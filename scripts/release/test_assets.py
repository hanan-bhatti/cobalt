"""Release publication regressions; fixtures are synthetic, no APK is built or published."""
from copy import deepcopy
from pathlib import Path
from types import SimpleNamespace
import hashlib
import tempfile
import unittest

import assets


def release(version='2.3.0'):
    name = f'cobalt-v{version}.apk'
    return {
        'id': 12, 'tag_name': 'v' + version, 'draft': False, 'prerelease': False,
        'published_at': '2026-11-11T12:00:00Z',
        'html_url': f'https://github.com/hanan-bhatti/cobalt/releases/tag/v{version}',
        'assets': [{'id': 42, 'name': name, 'state': 'uploaded', 'size': 7,
                    'browser_download_url': f'https://github.com/hanan-bhatti/cobalt/releases/download/v{version}/{name}'}],
    }


class ReleaseAssetsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='cobalt-release-publication-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / 'app').mkdir()
        (self.root / 'app/build.gradle.kts').write_text(
            'applicationId = "com.hanan_bhatti.cobalt"\nversionName = "2.3.0"\nversionCode = 20\n')
        (self.root / 'fdroid').mkdir()
        self.certificate = 'a' * 64
        (self.root / 'fdroid/com.hanan_bhatti.cobalt.yml').write_text(
            'AllowedAPKSigningKeys: ' + self.certificate + '\n\nCurrentVersion: 2.3.0\n')
        self.apk = self.root / 'cobalt-v2.3.0.apk'
        self.apk.write_bytes(b'fixture')
        (self.root / 'index.html').write_text(
            '<main><!-- release-download:start -->old<!-- release-download:end --></main>')

    def sdk(self, argv, **kwargs):
        fields = {'version-name': '2.3.0', 'version-code': '20',
                  'application-id': 'com.hanan_bhatti.cobalt', 'debuggable': 'false'}
        output = fields[argv[2]] if argv[0] == 'apkanalyzer' else 'Signer #1 certificate SHA-256 digest: ' + self.certificate
        return SimpleNamespace(stdout=output)

    def test_notes_only_include_requested_unique_version(self):
        text = '## [2.3.0] - 2026-11-11\n\n### Fixed\n- Correct downloads\n\n## [2.2.2] - date\nOld notes\n'
        self.assertEqual('### Fixed\n- Correct downloads\n', assets.release_notes(text, '2.3.0'))
        for invalid in [text + '\n## [2.3.0]\nDuplicate\n', '## [2.3.0]\n\n', text.replace('2.3.0', '2.4.0')]:
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                assets.release_notes(invalid, '2.3.0')

    def test_only_exact_uploaded_official_asset_is_selected(self):
        item = release()
        unrelated = deepcopy(item['assets'][0])
        unrelated['name'] = 'debug.apk'
        item['assets'].insert(0, unrelated)
        self.assertEqual(['cobalt-v2.3.0.apk'], [a['name'] for a in assets.published_assets(item)[2]])

    def test_invalid_asset_identity_is_never_a_download(self):
        for field, value in [('id', 0), ('id', True), ('size', 0), ('size', True), ('state', 'new'),
                             ('name', 'cobalt-foss-v2.3.0.apk'),
                             ('browser_download_url', 'https://example.com/cobalt-v2.3.0.apk'),
                             ('browser_download_url', 'https://github.com/hanan-bhatti/cobalt/releases/download/v2.2.2/cobalt-v2.3.0.apk')]:
            with self.subTest(field=field, value=value):
                item = release(); item['assets'][0][field] = value
                self.assertEqual([], assets.published_assets(item)[2])

    def test_duplicate_assets_are_not_chosen_arbitrarily(self):
        item = release(); item['assets'].append(deepcopy(item['assets'][0]))
        self.assertEqual([], assets.published_assets(item)[2])

    def test_draft_prerelease_or_invalid_publication_is_rejected(self):
        for field, value in [('draft', True), ('draft', None), ('prerelease', True), ('id', 0),
                             ('published_at', None), ('published_at', '2026-02-30T12:00:00Z'),
                             ('tag_name', 'v2.3.0-beta'), ('html_url', 'https://example.com/v2.3.0')]:
            with self.subTest(field=field, value=value), self.assertRaises((ValueError, TypeError)):
                item = release(); item[field] = value
                assets.published_assets(item)

    def test_legacy_flavors_keep_explicit_labels(self):
        item = release('2.2.2')
        original = item['assets'][0]
        item['assets'] = []
        for flavor in ['play', 'foss']:
            name = f'cobalt-{flavor}-v2.2.2.apk'
            item['assets'].append({**original, 'name': name, 'browser_download_url':
                                  'https://github.com/hanan-bhatti/cobalt/releases/download/v2.2.2/' + name})
        self.assertEqual(['Download FOSS APK', 'Download Play APK'], [a['label'] for a in assets.published_assets(item)[2]])

    def test_website_uses_metadata_links_and_is_idempotent(self):
        assets.render_downloads(self.root, release())
        first = (self.root / 'index.html').read_text()
        self.assertIn(release()['assets'][0]['browser_download_url'], first)
        self.assertIn('Download APK (v2.3.0)', first)
        assets.render_downloads(self.root, release())
        self.assertEqual(first, (self.root / 'index.html').read_text())

    def test_no_apk_is_labelled_as_unavailable_with_release_page(self):
        item = release(); item['assets'] = []
        assets.render_downloads(self.root, item)
        text = (self.root / 'index.html').read_text()
        self.assertIn('View release — APK unavailable', text)
        self.assertNotIn('Download APK', text)
        self.assertIn(item['html_url'], text)

    def test_missing_or_duplicate_website_marker_fails_without_write(self):
        for text in ['no markers', (self.root / 'index.html').read_text() * 2]:
            (self.root / 'index.html').write_text(text)
            with self.assertRaises(ValueError): assets.render_downloads(self.root, release())
            self.assertEqual(text, (self.root / 'index.html').read_text())

    def test_apk_manifest_signer_and_checksum_are_verified(self):
        digest = assets.verify_apk(self.apk, self.root, self.sdk, lambda tool: tool)
        self.assertEqual(hashlib.sha256(b'fixture').hexdigest(), digest)
        self.assertEqual(digest + '  cobalt-v2.3.0.apk\n', self.apk.with_suffix('.apk.sha256').read_text())

    def test_wrong_manifest_field_is_rejected(self):
        for field in ['version-name', 'version-code', 'application-id', 'debuggable']:
            def wrong(argv, **kwargs):
                return SimpleNamespace(stdout='wrong') if argv[0] == 'apkanalyzer' and argv[2] == field else self.sdk(argv, **kwargs)
            with self.subTest(field=field), self.assertRaises(ValueError):
                assets.verify_apk(self.apk, self.root, wrong, lambda tool: tool)

    def test_wrong_signer_is_rejected(self):
        self.certificate = 'b' * 64
        with self.assertRaises(ValueError): assets.verify_apk(self.apk, self.root, self.sdk, lambda tool: tool)

    def test_wrong_filename_or_empty_apk_is_rejected(self):
        with self.assertRaises(ValueError): assets.verify_apk(self.root / 'random.apk', self.root, self.sdk, lambda tool: tool)
        self.apk.write_bytes(b'')
        with self.assertRaises(ValueError): assets.verify_apk(self.apk, self.root, self.sdk, lambda tool: tool)

    def test_upload_size_and_digest_match_the_verified_local_artifact(self):
        item = release()
        item['assets'][0]['digest'] = 'sha256:' + hashlib.sha256(b'fixture').hexdigest()
        self.assertEqual(item['assets'][0]['browser_download_url'], assets.confirm_upload(item, self.apk))
        for field, value in [('size', 8), ('digest', 'sha256:' + 'b' * 64), ('name', 'random.apk')]:
            with self.subTest(field=field), self.assertRaises(ValueError):
                invalid = deepcopy(item); invalid['assets'][0][field] = value
                assets.confirm_upload(invalid, self.apk)

    def test_draft_can_only_be_confirmed_before_explicit_publication(self):
        item = release(); item['draft'] = True; item['published_at'] = None
        self.assertEqual(item['assets'][0]['browser_download_url'], assets.confirm_upload(item, self.apk, allow_draft=True))
        with self.assertRaises(ValueError): assets.confirm_upload(item, self.apk)


if __name__ == '__main__':
    unittest.main()
