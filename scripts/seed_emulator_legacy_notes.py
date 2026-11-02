#!/usr/bin/env python3
"""Create 60 legacy body fixtures using Android's SQLite API, preserving DB/WAL.

Requires a tiny precompiled test helper DEX; does not rebuild or replace Cobalt.
Stops only Cobalt Debug on the selected emulator, backs up its database/preferences,
then inserts synthetic records in one SQLite transaction. Never replaces the DB,
manually deletes journals, or touches production data. Leaves migration unstarted.
"""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
ADB = '/home/hanan-bhatti/Android/Sdk/platform-tools/adb'
PACKAGE = 'com.hanan_bhatti.cobalt.debug'


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--serial', default='emulator-5554')
    p.add_argument('--no-launch', action='store_true', help='Keep the one-time prompt unacknowledged for the next APK launch')
    args = p.parse_args()
    if not args.serial.startswith('emulator-'):
        raise ValueError('Only the explicitly selected emulator may be seeded')
    dex = ROOT / 'app/build/note-stress/seeder-dex/classes.dex'
    if not dex.is_file():
        raise ValueError('Compile scripts/android/CobaltLegacyNoteSeeder.java to the test-helper DEX first')
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    batch = f'MigrationDemo-{stamp}'
    folder = ROOT / 'app/build/note-stress' / f'migration-seed-{stamp}'
    folder.mkdir(parents=True, exist_ok=False)

    def adb(*parts, timeout=60):
        r = subprocess.run([ADB, '-s', args.serial, *parts], capture_output=True, timeout=timeout)
        if r.returncode:
            raise RuntimeError(r.stderr.decode(errors='replace')[:700] or r.stdout.decode(errors='replace')[:700])
        return r.stdout

    adb('shell', 'run-as', PACKAGE, 'id')
    adb('shell', 'am', 'force-stop', PACKAGE)
    backup = folder / 'before.tar'
    backup.write_bytes(adb('exec-out', 'run-as', PACKAGE, 'tar', '-cf', '-', 'databases', 'shared_prefs'))
    os.chmod(backup, 0o600)
    remote = '/data/local/tmp/cobalt-legacy-note-seeder.dex'
    adb('shell', 'rm', '-f', remote)  # Remove only this harness's previous read-only DEX.
    adb('push', str(dex), remote)
    adb('shell', 'chmod', '444', remote)
    result = adb('shell', 'run-as', PACKAGE, 'env', f'CLASSPATH={remote}',
                 'app_process', '/system/bin', 'CobaltLegacyNoteSeeder', batch, timeout=120)
    (folder / 'result.txt').write_bytes(result)
    manifest = adb('exec-out', 'run-as', PACKAGE, 'cat', f'files/{batch}.json')
    (folder / 'manifest.json').write_bytes(manifest)
    data = json.loads(manifest)
    assert data['count'] == 60
    assert min(n['bytes'] for n in data['notes']) == 1000
    assert max(n['bytes'] for n in data['notes']) == 2000000
    launch = b'' if args.no_launch else adb('shell', 'am', 'start', '-W', '-n', f'{PACKAGE}/com.example.MainActivity')
    print(result.decode().strip())
    print(json.dumps(dict(backup=str(backup), manifest=str(folder/'manifest.json'),
                         launch=launch.decode().strip(), schema_upgrade_test=False)))


if __name__ == '__main__':
    main()
