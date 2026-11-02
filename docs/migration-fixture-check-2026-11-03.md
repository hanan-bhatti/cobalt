# Migration fixture checkpoint — 2026-11-03

The existing API 37 emulator (`emulator-5554`) is left displaying **Move your notes
to Markdown storage** in `com.hanan_bhatti.cobalt.debug`. Migration was not started
by the harness. The 1 GB stress test is deferred for this requested migration check.

## Data added

- 60 TEXT notes using legacy INLINE bodies, null body hashes and format version 0.
- Five styles: Markdown, links, tables, tasks/fenced code and clean prose.
- Each style has sizes of 1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1,000 and 2,000 KB
  (decimal KB). Total body size: **20,115,000 bytes**.
- The existing one-line FILE note is retained with its original ID/hash/length.
- Schema stays at Room v9. This setup exercises body migration, **not** a Room
  schema downgrade or the separate v8→v9 schema-upgrade test.

## Verification actually run

- Compiled the standalone Android fixture helper with 128 MiB compiler heap caps.
  No Cobalt APK was built or replaced and no second emulator was started.
- The helper inserts notes using Android SQLiteDatabase in one transaction. It
  never replaces the database or manually removes WAL/SHM. A database/preferences
  archive was captured while the debug app was stopped, before any inserts.
- Host-side read-only verification found all **60** notes still INLINE, with every
  body length and SHA-256 matching the generated manifest.
- `PRAGMA quick_check`: **ok**.
- Existing FILE-note metadata retained: 27 bytes, hash
  `dd6c3e5f94421772cea83322eab1c519a9736558285f3822af8146140b997a78`.
- Screenshot and UI hierarchy confirm the migration dialog, with **Migrate notes**
  and **Close for now**. The user can initiate conversion in the emulator.
- Conversion correctness, retry behavior and performance have **not** been tested
  with this fixture set yet. No app frame-time/peak-heap claims are made here.

The initial database-replacement proposal was rejected by automatic approval
review over WAL/data-loss risk and was not run. The replacement implementation
uses Android's native in-place SQLite transaction and was approved and executed.

Local evidence (ignored build artifacts):

- `app/build/note-stress/migration-seed-20261102T190617Z/before.tar`
- `app/build/note-stress/migration-seed-20261102T190617Z/manifest.json`
- `app/build/note-stress/migration-seed-20261102T190617Z/result.txt`
- `app/build/note-stress/migration-ready.xml` and `migration-ready.png`

The archive may contain private settings; keep it local. It is not committed.
The screenshot currently represents the migration prompt; it is not a live view.

## Reproducing fixture setup

The script is deliberately restricted to an emulator and the debuggable package.
Compile the tiny helper before running `scripts/seed_emulator_legacy_notes.py`:

```sh
mkdir -p app/build/note-stress/seeder-classes app/build/note-stress/seeder-dex
javac -J-Xmx128m -source 8 -target 8 \
  -classpath "$ANDROID_HOME/platforms/android-37.0/android.jar" \
  -d app/build/note-stress/seeder-classes scripts/android/CobaltLegacyNoteSeeder.java
"$ANDROID_HOME/build-tools/36.0.0/d8" -JXmx128m \
  --lib "$ANDROID_HOME/platforms/android-37.0/android.jar" \
  --output app/build/note-stress/seeder-dex \
  app/build/note-stress/seeder-classes/CobaltLegacyNoteSeeder.class
python3 scripts/seed_emulator_legacy_notes.py --serial emulator-5554
```

A rerun intentionally adds a new timestamped batch and resets the debug migration
prompt for that test. Existing notes are preserved. The script currently uses the
SDK platform-tools path on this workstation; adjust it for another machine.
