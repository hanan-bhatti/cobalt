#!/usr/bin/env python3
"""Real process termination at deterministic note-storage frontiers; isolated debug fixture only."""
import argparse
from pathlib import Path
import subprocess
import time

parser = argparse.ArgumentParser()
parser.add_argument('--serial', default='emulator-5554')
parser.add_argument('--out', type=Path, required=True)
args = parser.parse_args()
args.out.mkdir(parents=True, exist_ok=True)
adb = ['adb', '-s', args.serial]
runner = 'com.hanan_bhatti.cobalt.debug.test/androidx.test.runner.AndroidJUnitRunner'
fixture = 'com.example.notes.NoteSharedStorageDeviceTest'
for phase in ('live', 'shared'):
    with (args.out / f'process-death-{phase}-stage.log').open('w') as log:
        process = subprocess.Popen(adb + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class', fixture + '#stageRealProcessDeath', '-e', 'crashPhase', phase, runner], stdout=log, stderr=subprocess.STDOUT)
        deadline = time.monotonic() + 120
        ready = False
        while time.monotonic() < deadline and process.poll() is None:
            result = subprocess.run(adb + ['shell', 'run-as', 'com.hanan_bhatti.cobalt.debug', 'test', '-f', 'files/notes-shared-process-death/ready-to-kill'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            if result.returncode == 0:
                ready = True
                break
            time.sleep(.3)
        if not ready:
            process.terminate()
            raise SystemExit(f'No deterministic kill marker for {phase}; inspect stage log')
        subprocess.run(adb + ['shell', 'am', 'force-stop', 'com.hanan_bhatti.cobalt.debug'], check=True)
        process.wait(timeout=30)
    result = subprocess.run(adb + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class', fixture + '#recoverAfterRealProcessDeath', '-e', 'crashPhase', phase, runner], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=120)
    (args.out / f'process-death-{phase}-recover.log').write_text(result.stdout)
    if 'OK (1 test)' not in result.stdout:
        raise SystemExit(f'Recovery verification failed for {phase}; inspect recovery log')
    print(f'{phase}: force-stop at interrupted write; saved head, durable unsaved writing, Undo, Redo and SQLite integrity passed', flush=True)
