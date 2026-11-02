# Device-test build report — 2026-11-02

This checkpoint covers the original-screen note redesign and text-file receiving.
It is a debug device-test artifact, not a production-release certification.

## Changes and commits

- `e62f326`: strict streaming text-file import/receiving. MD, MDX, TXT, common
  structured-text/source extensions, text MIME types, content-provider VIEW and
  sequential SEND_MULTIPLE handling. UTF-8 and BOM-labelled UTF-16 are decoded
  strictly; malformed input fails without modifying the source. MDX is inert.
- `bb3f1d9`: private capture journals and explicit Save; edits stage immutable
  generations without changing the saved body. Restore preserves the exact edited
  byte range. Existing oversized bodies can be saved within their size allowance.
  Closing during a file flush cannot reopen cancelled capture state.
- `dee1dce`: original DetailScreen for all notes, frameless Capture body, formatting
  toolbar only while the keyboard is open, scrolling file-backed editing without
  section buttons, and Add-menu import instead of an editor import button.
- Dependencies added: none. Application ID/signing configuration unchanged.

## Actual verification

All Gradle commands used one worker, no parallel tasks and a 1536 MiB Gradle Java
heap cap. Kotlin compilation runs in-process; unit-test forks have a 512 MiB cap.
These are configured limits, not measured app memory or total host RAM.

- Initial redesign build: failed in **7m 6s** on an audio-preview label referencing
  note-only state. Corrected.
- Debug build plus targeted note tests: **passed in 9m 49s**.
- New instrumented test source compilation: **passed in 3m 35s**. Added two isolated
  filesystem/database cases for capture-journal restore and explicit-save behavior.
- Refreshed debug build plus targeted note tests after the cancellation fix:
  **passed in 13m 47s**. XML totals: **36 tests, 0 failures, 0 errors, 0 skipped**.
- `git diff --check`: passed.
- Instrumented/Compose tests: **not executed**. No emulator was started.
- Release/R8, lint, benchmarks and API/device matrix: **not run in this pass**.
- Existing unrelated screenshot-test deprecation, Gradle and native-access warnings
  remain. No performance acceptance target is claimed from compilation/unit tests.

Verification command:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest --tests 'com.example.notes.*' \
  --max-workers=1 --no-parallel --console=plain \
  '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m -XX:TieredStopAtLevel=1 -Dfile.encoding=UTF-8'
```

Local logs: `app/build/notes-redesign-build.log`,
`app/build/notes-redesign-verification.log`,
`app/build/notes-redesign-device-test-compile.log`,
`app/build/notes-redesign-final-verification.log`.

## Current artifact

- `app/build/outputs/apk/debug/app-debug.apk`: **30,814,238 bytes**.
- Package `com.hanan_bhatti.cobalt.debug`, label **Cobalt Debug**, version **2.2.3 (19)**.
  This installs alongside production and does not access its private notes.
- SHA-256: `b471b36312ca5a3a313c628c3b3f6abed8c493a14b83ae20ca30b17e25758877`.
- `apksigner verify --print-certs`: passed with the debug certificate.
- All **14 DEX files** scanned: no Firebase, Google Play Services or BillingClient
  class-descriptor prefixes. This is a binary signature scan, not a complete
  transitive-license audit.

## Known limits and device checks

The body looks like one frameless scrolling surface. Internally it is a lazy list
of bounded ranges with a native input in the focused range. Tap another part to
focus it. Native selection, IME composition and cursor movement currently stay
within that range; cross-range editing is not fully seamless. A hard boundary
inside a very long logical line may affect wrapping. This is not the finished
custom large-file editor. The former separate-screen line-copy/jump/replace UI
has not been carried into the simplified interface; Find, Outline, plain-text
reading, export and saved-edit undo/redo are in Detail's overflow menu.

Large Markdown structures fall back to literal bounded fragments; cross-block
reference definitions remain unsupported. Pause/stop journals a file edit; Cancel
retains its draft. A conflicting saved version prevents automatic draft application;
conflicting generations are retained, and a dedicated recovery action for those
capture drafts is still outstanding.

Before release, exercise:

1. Import/open/share MD, MDX, TXT, UTF-16 and malformed/binary documents from real
   file providers, including multiple shares and a share during first migration.
2. Import 1k/3k/5k/10k links, 100k characters, 50k lines and 5/50/54/56 MB fixtures
   using `scripts/generate_note_fixture.py`; verify full content after save/reopen.
3. Paste, scroll, focus a different range, format, save, cancel/reopen, rotate,
   background/kill/reopen and use split-screen with the keyboard visible.
4. Validate v8 migration, missing files, disk-full/write faults, cancellation,
   concurrent edits/deletes, export/import round trips and startup safe mode.
5. Confirm Home, folders, Side Dock's 15 items/two-line previews, widget, reminders,
   suggestions and heatmap use bounded metadata and retain their existing behavior.
6. Run instrumented/Compose tests, release/R8 smoke tests, lint and the API 29,
   31/32, 34, 36, low-RAM and high-density matrix. Measure typing latency, scrolling
   jank, open times and heap before claiming the original performance budgets.
