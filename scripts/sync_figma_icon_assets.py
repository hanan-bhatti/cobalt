"""Sync a fresh Figma SVG export bundle into Android without building the app.

Bundle rows: {id, nodeId, svg}. Only IDs in the canonical manifest are accepted.
Welcome artwork is excluded. Uses Android SDK's cached SVG converter; opaque
white Figma clipping masks become equivalent clip paths before conversion.
"""
from pathlib import Path
import argparse
import json
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import hashlib

ROOT = Path(__file__).resolve().parents[1]
A = '{http://schemas.android.com/apk/res/android}'
S = '{http://www.w3.org/2000/svg}'
ET.register_namespace('android', A[1:-1])
ET.register_namespace('', S[1:-1])


def prepare_svg(svg):
    root = ET.fromstring(svg)
    for mask in root.iter(S + 'mask'):
        if mask.get('opacity', '1') != '1':
            raise ValueError('Semitransparent masks require manual conversion')
        for shape in mask:
            if (shape.tag not in (S + 'path', S + 'rect') or
                    shape.get('fill') not in ('white', '#FFFFFF', '#ffffff') or
                    shape.get('fill-opacity', '1') != '1'):
                raise ValueError('Only opaque white clipping masks can be converted')
            shape.attrib.pop('fill', None)
        mask.tag = S + 'clipPath'
        mask.attrib = {k: v for k, v in mask.attrib.items() if k in ('id', 'transform')}
        mask.set('clipPathUnits', 'userSpaceOnUse')
    for node in root.iter():
        if 'mask' in node.attrib:
            node.set('clip-path', node.attrib.pop('mask'))
    return ET.tostring(root, encoding='unicode')


def classpath():
    cache = Path.home() / '.gradle/caches/modules-2/files-2.1'
    versions = sorted((cache / 'com.android.tools/sdk-common').iterdir(), reverse=True)
    version = versions[0].name
    packages = [f'com.android.tools/{p}/{version}' for p in ('sdk-common', 'common', 'annotations')]
    packages += ['com.google.guava/guava', 'com.google.guava/failureaccess',
                 'org.jetbrains.kotlin/kotlin-stdlib', 'org.jetbrains/annotations']
    jars = [str(p) for package in packages for p in (cache / package).rglob('*.jar')
            if not p.name.endswith(('-sources.jar', '-javadoc.jar'))]
    return ':'.join(jars)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('bundle', type=Path)
    parser.add_argument('--apply', action='store_true', help='Write validated icons; default is check only')
    args = parser.parse_args()
    manifest_path = ROOT / 'design/icons/manifest.json'
    manifest = json.loads(manifest_path.read_text())
    icons = {i['id']: i for i in manifest['icons'] if 'welcome' not in i['id']}
    rows = json.loads(args.bundle.read_text())
    rows = [r for r in rows if r['id'] in icons]
    if len({r['id'] for r in rows}) != len(rows):
        raise ValueError('Duplicate IDs in export bundle')
    missing = set(icons) - {r['id'] for r in rows}
    if missing:
        raise ValueError(f'Missing canonical exports: {sorted(missing)}')
    for r in rows:
        if r['nodeId'] != icons[r['id']]['figma']['nodeId']:
            raise ValueError(f'Export source does not match canonical Figma ID: {r["id"]}')
    with tempfile.TemporaryDirectory(prefix='cobalt-icon-sync-') as work:
        temp = Path(work)
        svgdir, xmldir = temp / 'svg', temp / 'xml'
        svgdir.mkdir()
        for r in rows:
            (svgdir / (r['id'] + '.svg')).write_text(prepare_svg(r['svg']))
        cp = classpath()
        subprocess.run(['javac', '-cp', cp, '-d', str(temp), str(ROOT / 'scripts/CobaltVectorSync.java')], check=True)
        subprocess.run(['java', '-Djava.awt.headless=true', '-cp', str(temp) + ':' + cp,
                        'CobaltVectorSync', 'convert', str(svgdir), str(xmldir)], check=True)
        pending = []
        for r in rows:
            icon = icons[r['id']]
            target = ROOT / icon['source']
            if icon['sourceKind'] == 'Android vector':
                vector = ET.parse(xmldir / (r['id'] + '.xml')).getroot()
                previous = ET.parse(target).getroot() if target.exists() else None
                # Keep resource sizing and RTL behavior; export viewport retains Figma proportions.
                for name in ('width', 'height', 'autoMirrored', 'alpha', 'tint', 'tintMode'):
                    if previous is not None and previous.get(A + name) is not None:
                        vector.set(A + name, previous.get(A + name))
                if icon['id'] in ('cobalt-external-link', 'cobalt-volume-muted', 'cobalt-volume-on'):
                    vector.set(A + 'autoMirrored', 'true')
                if icon['monochrome']:
                    for n in vector.iter('path'):
                        for name in ('fillColor', 'strokeColor'):
                            value = n.get(A + name)
                            if value and value not in ('#00000000', '@android:color/transparent'):
                                n.set(A + name, '#FFFFFF')
                ET.indent(vector, space='    ')
                pending.append((target, ET.tostring(vector, encoding='unicode') + '\n'))
            elif icon['sourceKind'] == 'Standalone SVG':
                pending.append((target, r['svg']))
            else:
                raise ValueError(f'Unsupported source kind: {icon["sourceKind"]}')
            pending.append((ROOT / icon['svgFile'], r['svg']))
            icon['svgSha256'] = hashlib.sha256(r['svg'].encode()).hexdigest()
            icon['lastSyncedFromFigma'] = r['nodeId']
        if args.apply:
            for path, contents in pending:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(contents)
            manifest_path.write_text(json.dumps(manifest, indent=2) + '\n')
        print(json.dumps({'canonicalExports': len(rows), 'androidVectors': sum(icons[r['id']]['sourceKind'] == 'Android vector' for r in rows),
                          'applied': args.apply, 'welcomeArtworkExcluded': True}))


if __name__ == '__main__':
    main()
