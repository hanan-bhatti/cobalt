"""Versioned release notes, APK identity checks and verified static download links."""
from pathlib import Path
from datetime import datetime
import argparse
import hashlib
import html
import json
import os
import re
import shutil
import subprocess
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[2]
REPO = 'hanan-bhatti/cobalt'
DOWNLOAD_ICON = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>'

def app_version(root=ROOT):
    text = (root / 'app/build.gradle.kts').read_text()
    return (re.search(r'versionName\s*=\s*"([^"]+)"', text)[1],
            int(re.search(r'versionCode\s*=\s*(\d+)', text)[1]),
            re.search(r'applicationId\s*=\s*"([^"]+)"', text)[1])

def release_notes(text, version):
    sections = list(re.finditer(r'^## \[([^\]]+)\](?:[^\n]*)\n', text, re.M))
    matching = [(i, m) for i, m in enumerate(sections) if m[1] == version]
    if len(matching) != 1:
        raise ValueError('Expected exactly one changelog section for ' + version)
    i, match = matching[0]
    notes = text[match.end():sections[i+1].start() if i+1 < len(sections) else len(text)].strip()
    if not notes:
        raise ValueError('Release notes are empty')
    return notes + '\n'

def official_url(url, path):
    parsed = urlsplit(url)
    return (parsed.scheme == 'https' and parsed.netloc == 'github.com' and
            parsed.path == path and not parsed.query and not parsed.fragment)

def published_assets(release, allow_draft=False):
    tag = release.get('tag_name', '')
    if not re.fullmatch(r'v\d+\.\d+\.\d+', tag):
        raise ValueError('Expected a stable version tag')
    if type(release.get('draft')) is not bool or (release['draft'] and not allow_draft) or release.get('prerelease') is not False:
        raise ValueError('Release is not published and stable')
    if type(release.get('id')) is not int or release['id'] <= 0:
        raise ValueError('Release identity is missing')
    if not release['draft']:
        published = release.get('published_at', '')
        if not re.fullmatch(r'\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z', published):
            raise ValueError('Release publication timestamp is missing')
        datetime.strptime(published, '%Y-%m-%dT%H:%M:%SZ')
    version = tag[1:]
    page = release.get('html_url', '')
    if not official_url(page, f'/{REPO}/releases/tag/{tag}'):
        raise ValueError('Release page belongs to a different repository or tag')
    if tuple(map(int, version.split('.'))) >= (2, 3, 0):
        expected = [(f'cobalt-v{version}.apk', 'Download APK')]
    else:
        expected = [(f'cobalt-foss-v{version}.apk', 'Download FOSS APK'),
                    (f'cobalt-play-v{version}.apk', 'Download Play APK'),
                    (f'cobalt-v{version}.apk', 'Download APK')]
    result = []
    for name, label in expected:
        matches = [a for a in release.get('assets', []) if a.get('name') == name]
        if len(matches) != 1:
            continue
        asset = matches[0]
        url = asset.get('browser_download_url', '')
        if (asset.get('state') != 'uploaded' or type(asset.get('size')) is not int or asset['size'] <= 0 or
                type(asset.get('id')) is not int or asset['id'] <= 0 or
                not official_url(url, f'/{REPO}/releases/download/{tag}/{name}')):
            continue
        result.append({**asset, 'label': label})
    return version, page, result

def render_downloads(site, release):
    text = (site / 'index.html').read_text()
    pattern = r'<!-- release-download:start -->.*?<!-- release-download:end -->'
    if len(re.findall(pattern, text, re.S)) != 1:
        raise ValueError('Expected one download publication marker')
    version, page, assets = published_assets(release)
    links = []
    for asset in assets:
        links.append('<a href="' + html.escape(asset['browser_download_url'], quote=True) +
                     '" class="btn btn-primary" target="_blank" rel="noopener noreferrer">' +
                     DOWNLOAD_ICON +
                     html.escape(asset['label'] + ' (v' + version + ')') + '</a>')
    if not links:
        links = ['<a href="' + html.escape(page, quote=True) +
                 '" class="btn btn-primary" target="_blank" rel="noopener noreferrer">View release — APK unavailable</a>']
    replacement = '<!-- release-download:start -->\n' + '\n'.join(links) + '\n<!-- release-download:end -->'
    (site / 'index.html').write_text(re.sub(pattern, lambda _: replacement, text, count=1, flags=re.S))
    return assets

def android_tool(name):
    executable = shutil.which(name)
    if executable:
        return executable
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if sdk:
        locations = list((Path(sdk) / 'cmdline-tools').glob('*/bin/' + name)) + list((Path(sdk) / 'build-tools').glob('*/' + name))
        if locations:
            return str(sorted(locations, key=lambda p: tuple(int(n) for n in re.findall(r'\d+', str(p))), reverse=True)[0])
    raise ValueError('Android SDK tool is unavailable: ' + name)

def verify_apk(apk, root=ROOT, run=subprocess.run, tool=android_tool):
    version, code, package = app_version(root)
    if apk.name != f'cobalt-v{version}.apk' or not apk.is_file() or apk.stat().st_size <= 0:
        raise ValueError('Expected the nonempty versioned APK from this build')
    analyzer = tool('apkanalyzer')
    for field, expected in [('version-name', version), ('version-code', str(code)), ('application-id', package), ('debuggable', 'false')]:
        actual = run([analyzer, 'manifest', field, str(apk)], check=True, text=True, capture_output=True).stdout.strip()
        if actual != expected:
            raise ValueError('APK manifest ' + field + ' does not match this release')
    result = run([tool('apksigner'), 'verify', '--verbose', '--print-certs', str(apk)], check=True, text=True, capture_output=True)
    certificates = set(re.findall(r'certificate SHA-256 digest:\s*([0-9a-fA-F:]+)', result.stdout))
    certificates = {c.replace(':', '').lower() for c in certificates}
    metadata = (root / 'fdroid/com.hanan_bhatti.cobalt.yml').read_text()
    allowed = set(re.findall(r'[0-9a-fA-F]{64}', metadata.split('AllowedAPKSigningKeys:', 1)[1].split('\n\n', 1)[0]))
    if not certificates or not certificates <= {c.lower() for c in allowed}:
        raise ValueError('APK signer does not match the official distribution certificate')
    with apk.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    apk.with_suffix('.apk.sha256').write_text(digest + '  ' + apk.name + '\n')
    return digest

def confirm_upload(release, apk, allow_draft=False):
    version, _, assets = published_assets(release, allow_draft)
    asset = next((a for a in assets if a['name'] == apk.name), None)
    if asset is None or apk.name != f'cobalt-v{version}.apk' or asset['size'] != apk.stat().st_size:
        raise ValueError('Uploaded release asset does not match the local APK')
    with apk.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    if asset.get('digest') and asset['digest'] != 'sha256:' + digest:
        raise ValueError('GitHub APK digest differs from the built artifact')
    return asset['browser_download_url']

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    notes = sub.add_parser('notes'); notes.add_argument('--version', required=True); notes.add_argument('--output', type=Path, required=True)
    verify = sub.add_parser('verify-apk'); verify.add_argument('--apk', type=Path, required=True)
    site = sub.add_parser('website'); site.add_argument('--site', type=Path, default=ROOT/'docs'); site.add_argument('--release-json', type=Path, required=True)
    confirm = sub.add_parser('confirm-upload'); confirm.add_argument('--apk', type=Path, required=True); confirm.add_argument('--release-json', type=Path, required=True); confirm.add_argument('--allow-draft', action='store_true')
    args = parser.parse_args()
    if args.command == 'notes':
        version, _, _ = app_version()
        if version != args.version or (ROOT/'CHANGELOG.md').read_bytes() != (ROOT/'app/src/main/assets/CHANGELOG.md').read_bytes():
            raise ValueError('Version or bundled changelog differs from release source')
        args.output.write_text(release_notes((ROOT/'CHANGELOG.md').read_text(), version))
    elif args.command == 'verify-apk':
        print('Verified APK manifest and official signer. SHA-256:', verify_apk(args.apk))
    elif args.command == 'website':
        assets = render_downloads(args.site, json.loads(args.release_json.read_text()))
        print('Website download links use', len(assets), 'confirmed uploaded assets.')
    elif args.command == 'confirm-upload':
        print(confirm_upload(json.loads(args.release_json.read_text()), args.apk, args.allow_draft))

if __name__ == '__main__':
    main()
