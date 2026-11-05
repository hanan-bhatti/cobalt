# Editor logic validation — 5 November 2026

Scope: editor correctness and recovery. Existing Capture layout, controls, theme,
icons, and font preferences are retained. No dependency, network, application ID,
signing, database-schema, or user-data reset changes were introduced in this pass.

## Changes

- The session drains accepted operations before closing, including when its queue
  is full. Native View disposal no longer determines the worker lifetime.
- Failed journal publication can be retried from retained piece descriptors and
  Undo/Redo state instead of reloading an older checkpoint. Save retries the journal
  before issuing ordinary selection commands.
- Cross-viewport IME deletion and horizontal hardware navigation use global positions.
  Code-point deletion respects emoji. Invalid selection/negative deletion requests
  and stale input connections are rejected. A failed viewport read keeps input disabled.
- Cancelled insertions roll back their append-file tail. Oversized streams are refused
  before their entire rejected body is written. Original bytes and prior Undo remain.
- Live draft recovery uses exclusive editor leases, reports current draft sizes,
  and can recover never-saved notes into a new note without overwriting the original.
  Permanent deletion and history retirement serialize through the same guard.
- Native typography respects API 24–27 compatibility and configuration/font-scale
  changes while using the merged local font system.
- Continued fenced code removes its original list/quote container from subsequent
  chunks and preserves literal indentation, blank lines and tabs.

## Completed validation

```sh
./tools/check-editor-isolated.sh --tests com.example.ui.theme.TypographyPrefsTest
./tools/check-editor-isolated.sh --tests com.example.ui.theme.TypographyPrefsTest :app:assembleDebug
```

Tests: `BUILD SUCCESSFUL in 3m 24s`; **144 tests, zero failures/errors,
zero skipped**, verified from JUnit XML. Debug APK packaging: `BUILD SUCCESSFUL
in 1m 19s` (tests up-to-date). One worker, 1536 MiB Gradle heap, 384 MiB metaspace;
no emulator or release/R8 task.

Includes seven native disk-viewport Robolectric tests, two draft recovery tests,
model/journal failure and cancellation tests, and six fenced-code/autolink regression
tests. Existing Compose test deprecation and Gradle/host-JVM dependency warnings remain.
Packaging could not strip libandroidx.graphics.path.so; Gradle packaged it unchanged.

Synthetic model fixture: a 50,000,000-byte ASCII source was deleted, cycled through
12 Undo/Redo pairs and restored/streamed. Append file: **0 bytes**; state file:
**256 bytes**; largest source read: **8192 bytes**. This is a host correctness result
for that fixture, not a device latency, heap, or typical history-size measurement.

## Device acceptance still required

- Real keyboard composition, selection handles, accessibility, long-press clipboard,
  rotation, process death, split-screen, and fast scroll across window boundaries.
- Markdown visual checks on normal device scale, including nested code continuation.
- 5/50/55 MB link-heavy and plain files, a roughly 1 GB collection, repeated Undo/Redo,
  storage-full recovery, startup and migration.
- Frame timings, typing p95, CPU, peak heap, disk use and thermal behavior on a physical
  mid-range device; emulator/API matrix and R8 release smoke test.

No claim of sub-50 ms loading, sub-second 55 MB opening, near-zero RAM/CPU,
or production acceptance is made from host tests.
