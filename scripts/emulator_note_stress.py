#!/usr/bin/env python3
"""Baseline the INSTALLED Cobalt Debug APK. No builds, APK replacement, root or DB writes.

Synthetic imports use the exported file-sharing entry point. SQLite copies are
read-only observations (DB/WAL copies are not an atomic backup). Body SHA-256 is
verified separately on the emulator. Fixture files are streamed one at a time.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import sqlite3
import subprocess
import tarfile
import time
from urllib.parse import quote

from generate_note_fixture import generate

PACKAGE = 'com.hanan_bhatti.cobalt.debug'
ADB = '/home/hanan-bhatti/Android/Sdk/platform-tools/adb'
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'app/build/note-stress'
PREFIX = 'CobaltStress-'


class Baseline:
    def __init__(self, serial, allow_physical=False):
        if not serial.startswith('emulator-') and not allow_physical:
            raise ValueError('Physical devices require an explicit --serial and --allow-physical-device')
        self.serial = serial
        OUT.mkdir(parents=True, exist_ok=True)
        self.events = OUT / 'events.jsonl'

    def event(self, kind, **values):
        entry = dict(event=kind, utc=time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()), **values)
        line = json.dumps(entry, ensure_ascii=False)
        with self.events.open('a') as f:
            f.write(line + '\n')
        print(line, flush=True)
        return entry

    def adb(self, *args, timeout=40, check=True, binary=False):
        r = subprocess.run([ADB, '-s', self.serial, *args], capture_output=True, timeout=timeout)
        if check and r.returncode:
            raise RuntimeError(r.stderr.decode(errors='replace')[:500] or r.stdout.decode(errors='replace')[:500])
        return r.stdout if binary else r.stdout.decode(errors='replace')

    def rows(self):
        archive = self.adb('exec-out', 'run-as', PACKAGE, 'tar', '-cf', '-',
                           'databases/cobalt_db', 'databases/cobalt_db-wal', binary=True, check=False)
        folder = OUT / 'metadata-snapshot'
        folder.mkdir(exist_ok=True)
        for name in ['cobalt_db', 'cobalt_db-wal', 'cobalt_db-shm']:
            (folder / name).unlink(missing_ok=True)
        with tarfile.open(fileobj=io.BytesIO(archive)) as tar:
            for member in tar.getmembers():
                if member.name in ['databases/cobalt_db', 'databases/cobalt_db-wal']:
                    source = tar.extractfile(member)
                    if source is not None:
                        (folder / Path(member.name).name).write_bytes(source.read())
        with sqlite3.connect(f'file:{folder / "cobalt_db"}?mode=ro', uri=True) as db:
            db.row_factory = sqlite3.Row
            return [dict(row) for row in db.execute(
                "SELECT id,title,byteLength,contentHash,bodyLocation,bodyMissing,length(content) AS inlineCharacters "
                "FROM saved_items WHERE title LIKE 'CobaltStress-%' ORDER BY title")]

    def metrics(self, label):
        mem = self.adb('shell', 'dumpsys', 'meminfo', PACKAGE)
        gfx = self.adb('shell', 'dumpsys', 'gfxinfo', PACKAGE)
        cpu = self.adb('shell', 'dumpsys', 'cpuinfo')
        (OUT / f'{label}-meminfo.txt').write_text(mem)
        (OUT / f'{label}-gfxinfo.txt').write_text(gfx)
        (OUT / f'{label}-cpuinfo.txt').write_text(cpu)
        pss = re.search(r'TOTAL PSS:\s*(\d+)', mem)
        if not pss:
            pss = re.search(r'^\s*TOTAL\s+(\d+)', mem, re.M)
        java = re.search(r'^\s*Java Heap:\s*(\d+)', mem, re.M)
        total = re.search(r'Total frames rendered:\s*(\d+)', gfx)
        jank = re.search(r'Janky frames:\s*(\d+)\s*\(([^)]+)\)', gfx)
        process_cpu = re.search(r'^\s*([\d.]+)%\s+\d+/' + re.escape(PACKAGE) + r':', cpu, re.M)
        return self.event('metrics', label=label, pss_kib=int(pss[1]) if pss else None,
                          java_heap_pss_kib=int(java[1]) if java else None,
                          frames=int(total[1]) if total else None,
                          janky_frames=int(jank[1]) if jank else None,
                          jank_percent=jank[2] if jank else None,
                          cpu_percent=float(process_cpu[1]) if process_cpu else None,
                          pid=self.adb('shell', 'pidof', PACKAGE, check=False).strip())

    def ui(self, label):
        self.adb('shell', 'uiautomator', 'dump', '/sdcard/cobalt-stress-window.xml', timeout=20)
        self.adb('pull', '/sdcard/cobalt-stress-window.xml', str(OUT / f'{label}.xml'))
        screenshot = self.adb('exec-out', 'screencap', '-p', binary=True)
        (OUT / f'{label}.png').write_bytes(screenshot)

    def make(self, name, kind, size):
        path = OUT / 'fixture.md'
        with path.open('wb') as f:
            if kind == 'one-line':
                f.write(b'Cobalt synthetic one line.\n')
            else:
                generate(kind, size, f)
        with path.open('rb') as f:
            digest = hashlib.file_digest(f, 'sha256').hexdigest()
        return path, digest

    def verify(self, row, length, digest):
        assert row['byteLength'] == length, row
        assert row['contentHash'] == digest, row
        assert row['bodyLocation'] == 'FILE' and not row['bodyMissing'], row
        assert row['inlineCharacters'] == 0, row
        directory = hashlib.sha256(row['id'].encode()).hexdigest()
        actual = self.adb('shell', 'run-as', PACKAGE, 'sha256sum',
                          f'files/notes/{directory}/{digest}.md', timeout=60).split()[0]
        assert actual == digest, (actual, digest)

    def import_one(self, name, kind, amount, refused=False):
        path, digest = self.make(name, kind, amount)
        length = path.stat().st_size
        prior = [row for row in self.rows() if row['title'] == name]
        if prior:
            if refused or len(prior) != 1:
                raise RuntimeError(f'Fixture title already used: {name}')
            self.verify(prior[0], length, digest)
            self.event('existing_verified', title=name, bytes=length, sha256=digest)
            return
        available = int(re.search(r'MemAvailable:\s*(\d+)', Path('/proc/meminfo').read_text())[1])
        if available < 700 * 1024:
            raise RuntimeError('Host available memory below 700 MiB; pause rather than load it further')
        remote = f'/sdcard/Download/{name}.md'
        uri = 'content://com.android.externalstorage.documents/document/' + quote(f'primary:Download/{name}.md', safe='')
        self.event('import_start', title=name, bytes=length, host_available_kib=available)
        self.adb('push', str(path), remote, timeout=90)
        start = time.perf_counter()
        launch = self.adb('shell', 'am', 'start', '-W', '-a', 'android.intent.action.VIEW',
                          '-t', 'text/markdown', '-d', uri, '-f', '1', '-n', f'{PACKAGE}/com.example.QuickShareActivity')
        launch_time = re.search(r'TotalTime:\s*(\d+)', launch)
        found = None
        while time.perf_counter() - start < (12 if refused else 120):
            try:
                found = next((row for row in self.rows() if row['title'] == name), None)
            except (sqlite3.Error, tarfile.TarError):
                found = None
            if found:
                break
            time.sleep(0.35)
        elapsed = round((time.perf_counter() - start) * 1000)
        if refused:
            assert found is None, 'Above-limit note was accepted unexpectedly'
            self.event('not_created_above_limit', title=name, bytes=length, observation_ms=elapsed)
        else:
            assert found is not None, f'Import did not complete within 120s: {name}'
            self.verify(found, length, digest)
            self.event('import_verified', title=name, bytes=length, sha256=digest, id=found['id'],
                       observed_completion_upper_bound_ms=elapsed,
                       quickshare_activity_start_ms=int(launch_time[1]) if launch_time else None)
        self.metrics(name)
        # Only remove the synthetic external source created by this method, after verification.
        self.adb('shell', 'rm', remote)

    def matrix(self, fill_bytes):
        cases = [('one-line', 'one-line', 0), ('one-kb', 'plain', 1_000),
                 ('characters-100000', 'plain', 100_000)]
        cases += [(f'links-{n}', 'links', n) for n in [1_000, 3_000, 5_000, 10_000]]
        cases += [('lines-50000', 'lines', 50_000), ('plain-1mb', 'plain', 1_000_000),
                  ('plain-3mb', 'plain', 3_000_000), ('plain-5mb', 'plain', 5_000_000),
                  ('links-5mb', 'link-bytes', 5_000_000), ('plain-50mb', 'plain', 50_000_000),
                  ('links-50mb', 'link-bytes', 50_000_000), ('plain-54mb', 'plain', 54_000_000),
                  ('plain-55mb', 'plain', 55_000_000)]
        for name, kind, size in cases:
            self.import_one(PREFIX + name, kind, size)
        self.import_one(PREFIX + 'refused-56mb', 'plain', 56_000_000, refused=True)
        number = 1
        while True:
            rows = self.rows()
            total = sum(row['byteLength'] for row in rows)
            if total >= fill_bytes:
                self.event('dataset_verified', count=len(rows), bytes=total, max_bytes=max(row['byteLength'] for row in rows))
                return
            name = PREFIX + f'volume-{number:02}'
            number += 1
            if any(row['title'] == name for row in rows):
                continue
            self.import_one(name, 'plain', min(50_000_000, fill_bytes - total))


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--serial', default='emulator-5554')
    p.add_argument('action', choices=['snapshot', 'matrix', 'metrics', 'ui', 'reset-metrics'])
    p.add_argument('--allow-physical-device', action='store_true',
                   help='Opt into testing the debug package on the explicitly selected physical device')
    p.add_argument('--fill-bytes', type=int, default=1_000_000_000)
    p.add_argument('--label', default='baseline')
    args = p.parse_args()
    if args.allow_physical_device and args.serial == 'emulator-5554':
        p.error('--allow-physical-device requires an explicit physical-device --serial')
    b = Baseline(args.serial, args.allow_physical_device)
    # Never fall back to the production package or root when debug access is unavailable.
    b.adb('shell', 'run-as', PACKAGE, 'id')
    if args.action == 'snapshot':
        print(json.dumps(b.rows(), indent=2))
    elif args.action == 'matrix':
        b.matrix(args.fill_bytes)
    elif args.action == 'metrics':
        b.metrics(args.label)
    elif args.action == 'reset-metrics':
        b.adb('shell', 'dumpsys', 'gfxinfo', PACKAGE, 'reset')
        b.event('frame_counters_reset')
    else:
        b.ui(args.label)


if __name__ == '__main__':
    main()
