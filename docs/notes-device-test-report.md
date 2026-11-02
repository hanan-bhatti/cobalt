# Device-test build report — 2026-11-02

This report distinguishes source implementation, build results and device results.
It does not certify a release before the device checks pass.

## Current validation

- Host SQL recovery/migration checks: passed in this session.
- `git diff --check`: passed before the debug build.
- Debug APK: **passed**, 18m 57s, one worker and a 1536 MiB Gradle Java heap cap.
- Targeted note unit tests: **32 passed, 0 failures, 0 errors**, final run 1m 38s.
- Instrumented/Compose/security test source compilation: **passed**, 3m 4s.
- Instrumented/Compose/security test execution: not run on a device.
- Release/R8, lint, benchmarks and emulator matrix: not run in this session.
- New dependencies: none.

An initial debug-build attempt failed after 4m 9s on two Compose-context errors
in a size-warning label. These were corrected. The following attempt passed
Kotlin compilation and progressed to D8 dependency processing; final outcome is
recorded below when complete. A thread inspection showed active D8 work rather
than a blocked compiler. An older verified idle Gradle daemon was stopped to
reduce memory use; the active daemon and heap cap were left unchanged.

Clipboard constraints were checked against Android's documentation: Binder uses
a shared, limited transaction buffer, so no universal multi-MB text clipboard
capacity is promised. Large documents use SAF streams instead. See
[TransactionTooLargeException](https://developer.android.com/reference/android/os/TransactionTooLargeException)
and [copy/paste with content URIs](https://developer.android.com/develop/ui/views/touch-and-input/copy-paste).

The configured heap cap is not a measured peak heap or total system RAM figure.
Unit-test forks are restricted to one with a 512 MiB heap cap. No emulator runs
alongside these commands.

## Installable artifact and checks

- APK: `app/build/outputs/apk/debug/app-debug.apk`, 30,830,066 bytes.
- Package: `com.hanan_bhatti.cobalt.debug`; label: **Cobalt Debug**.
- Version: 2.2.3 (19). Installs separately from the production app/data.
- SHA-256: `45c41f7106527ce30900bdef3b55d5284128263d8663f355e96d7579e00f5d13`.
- `apksigner verify --print-certs`: passed (debug signing certificate).
- Compiled manifest: `allowBackup=false`; private FileProvider authority uses the
  debug package. `debuggable=true` is expected for this test artifact. Release
  build configuration explicitly sets `isDebuggable=false`; no release APK was
  built/validated in this run.
- Scanned all 14 DEX files for Firebase, Google Play Services and BillingClient
  class-descriptor prefixes: none found. This is a binary signature scan, not an
  independent review of every transitive dependency's licence.

The first targeted test run had one failure caused by Robolectric's unsupported
directory-fsync shadow. The export/import unit test now uses a stream fixture;
real filesystem fsync/write-fault tests remain instrumented tests. The entire
32-test note suite then passed. Existing unrelated screenshot-test deprecation
and native-library/Gradle warnings remain; they are not recorded as new test
passes or resolved quality checks.

This artifact is ready for **device testing**, not production-release certification.
The device checklist, release/R8 validation, lint and measured performance remain
outstanding. Oversized Markdown structures deliberately fall back to literal
text; cross-block reference definitions and drag selection across virtualized
blocks are not claimed. Use explicit line-range copy and plain-text/export modes.

## Device acceptance checklist

Use the debug package for the first test; it cannot access the release package's
notes. Preserve the release data until an update/migration test is explicitly
planned. Fixtures are generated individually by `scripts/generate_note_fixture.py`.

1. Import 1k/3k/5k/10k link fixtures, the 100k-character and 50k-line fixtures.
2. Paste into normal capture, Quick Note and Side Dock; verify progress and body
   equality after save/reopen. Check that multi-line share text remains complete.
3. Test ordinary Markdown, long fenced code, tables/lists, checkboxes, link taps,
   plain-text mode, find/replace, heading/line jumps and cross-section copy.
4. Edit, undo/redo, rotate and use split-screen with the keyboard visible. Background
   the editor and kill/reopen the debug process; recover the journaled draft.
5. On a disposable v8 fixture database, test migration, cancellation/retry and
   missing-body handling. Confirm the original survives every failed conversion.
6. Test disk-full/write faults, export cancellation and concurrent edit/delete.
7. Import 5/50/54/56 MB fixtures one at a time. Verify tolerance warnings, refusal
   above 55 MB, explicit splitting and preservation of the original source.
8. Simulate two uncaught startup exceptions; verify safe mode skips normal workers
   and opens recovery. Normal process termination must not increment the counter.
9. Confirm Side Dock shows two lines and only 15 recent items; compare Home/folder
   titles, counts, tags, reminders, suggestions and heatmap against existing data.
10. Verify explicit share/email grants expose only export cache paths. Exercise
    credential migration, re-authentication after key loss and Recents privacy.

API 29, 31/32, 34, 36, low-RAM and high-density profiles, an R8 APK smoke test,
Macrobenchmark timings/jank, typing latency and heap measurements remain required
before the original release/performance acceptance criteria can be claimed.
