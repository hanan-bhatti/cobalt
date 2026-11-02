#!/usr/bin/env python3
"""Explicit emulator/debug reset with local backup, then 60 legacy migration fixtures.

Uses the already compiled tiny fixture helper. Does not build/install an APK or
start another emulator. Leaves Cobalt stopped and the migration prompt unseen, so
the next launch of the user's updated APK shows it. Production is never touched.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import sys
import tarfile
import time
from emulator_note_stress import Baseline, PACKAGE, ADB, ROOT


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', default='emulator-5554')
    args = parser.parse_args()
    if not args.serial.startswith('emulator-') or PACKAGE != 'com.hanan_bhatti.cobalt.debug':
        raise ValueError('Only Cobalt Debug on an explicitly selected emulator may be reset')
    if not (ROOT / 'app/build/note-stress/seeder-dex/classes.dex').is_file():
        raise ValueError('Existing fixture DEX is missing. No reset was performed; this script never builds it.')
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    folder = ROOT / 'app/build/note-stress' / f'reset-{stamp}'
    folder.mkdir(parents=True, exist_ok=False)

    def adb(*parts):
        r = subprocess.run([ADB, '-s', args.serial, *parts], capture_output=True, timeout=60)
        if r.returncode:
            raise RuntimeError(r.stderr.decode(errors='replace')[:700] or r.stdout.decode(errors='replace')[:700])
        return r.stdout

    adb('shell', 'run-as', PACKAGE, 'id')
    adb('shell', 'am', 'force-stop', PACKAGE)
    backup = folder / 'before-reset.tar'
    fd = os.open(backup, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'wb') as output:
        r = subprocess.run([ADB, '-s', args.serial, 'exec-out', 'run-as', PACKAGE,
                            'tar', '-cf', '-', 'files', 'databases', 'shared_prefs'],
                           stdout=output, stderr=subprocess.PIPE, timeout=120)
    if r.returncode:
        raise RuntimeError('Backup incomplete; app data has NOT been cleared')
    with tarfile.open(backup) as archive:
        if 'databases/cobalt_db' not in archive.getnames():
            raise ValueError('Database missing from backup; app data has NOT been cleared')
    with backup.open('rb') as source:
        digest = hashlib.file_digest(source, 'sha256').hexdigest()
    cleared = adb('shell', 'pm', 'clear', PACKAGE).decode().strip()
    if cleared != 'Success':
        raise RuntimeError(f'Reset failed: {cleared}')
    adb('shell', 'am', 'start', '-W', '-n', f'{PACKAGE}/com.example.MainActivity')
    baseline = Baseline(args.serial)
    ready = False
    for _ in range(30):
        try:
            baseline.rows()  # Read-only DB/WAL copy, never database replacement or direct journal deletion.
            path = ROOT / 'app/build/note-stress/metadata-snapshot/cobalt_db'
            with sqlite3.connect(f'file:{path}?mode=ro', uri=True) as db:
                ready = db.execute('PRAGMA user_version').fetchone()[0] == 9
            if ready:
                break
        except Exception:
            pass
        time.sleep(1)
    if not ready:
        raise RuntimeError('Fresh debug database did not initialize; backup retained. No fixture build attempted.')
    seeded = subprocess.run([sys.executable, str(ROOT / 'scripts/seed_emulator_legacy_notes.py'),
                             '--serial', args.serial, '--no-launch'], capture_output=True, timeout=180)
    (folder / 'seed-result.txt').write_bytes(seeded.stdout + seeded.stderr)
    if seeded.returncode:
        raise RuntimeError('Fixture setup failed; inspect seed-result.txt. The original backup remains available.')
    result = dict(package=PACKAGE, serial=args.serial, cleared=True, backup=str(backup), backup_sha256=digest,
                  fixture_notes=60, app_left_stopped=True, prompt_unacknowledged=True, build_run=False)
    (folder / 'result.json').write_text(json.dumps(result, indent=2))
    print(json.dumps(result, indent=2))
    print(seeded.stdout.decode().strip())


if __name__ == '__main__':
    main()
