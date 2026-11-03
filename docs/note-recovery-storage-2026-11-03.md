# Note recovery and storage management — 2026-11-03

## Status

This is a source implementation and partial verification report, **not** the final
large-file acceptance gate. The live Sora text model is still resident in RAM. The
new disk piece-table adapter is used for journal recovery, not live editing. Dense
large notes may still fail the live editor's capacity admission check. The original
50/55 MB live-editing, latency, jank and heap requirements remain unverified and
are not claimed complete.

Tests ran on the shared working tree, including preserved concurrent changes;
this is not a clean-checkout or release-artifact claim.

No app data was cleared, no database schema/migration version was changed, no
network connection was added, and no application ID or signing change was made.
Unrelated concurrent repository, OCR, folder, search, card and icon changes were
left outside these commits. Shared ViewModel staging used a HEAD-derived snapshot
containing only the lifecycle journal-flush change.

## Storage question

Five distinct full saves of a 50 MB body can retain five different 50 MB files.
Including the original, that is roughly 300 MB before journals, metadata, scratch
space or filesystem overhead. Hash naming deduplicates identical bytes only.

Idle native editing now writes UTF-16 operation transactions instead of a new
full body after each idle pause. Full verified copies are still made for Save,
formatted preview and closing a dirty editor. Retention is **manual**: choosing a
copy count does not silently delete files or impose an automatic storage quota.
The Settings cleanup action applies it only after explicit confirmation.

There is no snapshot compression in this revision. Large pastes produce large
operation payloads, and a deletion/paste/undo sequence can grow the journal.
Recovery temporarily needs disk space for UTF-16 scratch files and a verified
UTF-8 replacement. This is not a zero-disk-overhead scheme.

## Journal and recovery

- Versioned header binds a journal to its immutable base hash and byte length.
- A transaction contains UTF-16 coordinates, removed counts and inserted code
  units, protected by a CRC32 over both coordinates and payload. Native mutations
  within one UI turn are grouped; a delete+insert replacement is not replayed as
  a deletion alone when its frame is torn.
- One bounded IO consumer owns writes. Nearby transactions share a flush/fsync;
  paste and replace-all suspend for backpressure while keeping Main available.
- A complete transaction is validated before replay. A torn last transaction is
  excluded. Corrupt complete frames fail closed and remain on disk. An unfinished
  multi-turn paste can recover only its completed transaction prefix; the saved
  original is retained. CRC detects accidental corruption, not malicious changes.
- Recovery verifies the original base, converts it to temporary UTF-16 scratch,
  applies the log through a piece table without undo retention, and streams a
  UTF-8 result into the existing atomic verified writer. Adjacent additions share
  a byte source and coalesce. No whole-note String or Sora document is required.
- A durable recovery reference is published before the old log is retired. Failed
  verification does not authorize replacing the original or deleting a log.
- Recovered text is materialized before live-editor capacity admission, so a
  capacity refusal still exposes the recovered generation for reading/export.
- A failed writer pauses editing and leaves Save available. A failed close keeps
  its model in the ViewModel for reopening/retrying. That RAM-only fallback cannot
  survive process death: only synced transactions/full copies are durable.
- A failed Save or full disk also exposes explicit **Export unsaved edits**. It
  streams a retained COW snapshot to the user-chosen SAF destination, without an
  internal full-copy prerequisite. Failed/cancelled exports may leave an incomplete
  destination. Original note files remain untouched.
- Model ownership waits for both the session and native view to close before
  releasing the model. Rotation alone does not release the retained document.

## Storage & Recovery

A new Settings category uses the existing settings header, palette, surfaces,
context menus and navigation transition helpers. Existing icons are reused.

- Breakdown: current Markdown bodies, older full copies, drafts/recovery,
  database, other files under filesDir, and cache. This is not a complete Android
  package-size report: APK/native libraries and shared-preference overhead are
  not included. Sizes are formatted with Android's human-readable formatter.
- Retain 0, 1, 5, 10, 20 or all extra full copies per note when explicitly cleaning.
  Default is five; active undo references may keep more.
- Review an estimate and confirm before removing older copies/completed logs.
- Recompute candidates under the note-write lock. Verify current bodies before
  removing fallback copies; check file identity/size/modification time again.
- Protect current references, capture/section/quick drafts, active file readers,
  live undo histories and commit-to-history handoffs. Interrupted/unknown writes
  and directories without known note metadata are retained. A malformed recovery
  preference disables cleanup instead of authorizing deletion.
- Browse up to 200 recent older full copies; titles are one line. Export streams
  to SAF with frontmatter and hash/length checks. Restore verifies the body and
  current-version guard; a recovery draft blocks restoration. The previous
  current body remains as an older copy. Copies do not contain historical title,
  tag or folder metadata; restore changes the body only.

## Verification actually performed

Debug Kotlin compilation and the targeted Gradle host regression suite were run.
No APK packaging, emulator test, R8/release build or benchmark was run here.

A standalone Kotlin/JVM harness compiled the actual journal, piece-table and disk
recovery sources with test-only adapters for unrelated Android store references.
Compiler options were `-Xmx256m -XX:ActiveProcessorCount=1`; harness execution used
`-Xmx128m -XX:ActiveProcessorCount=1`.

Actual output:

```text
PASS: 68 assertions; Unicode/CRLF/NUL/unpaired-surrogate round trip; every torn replacement boundary; corrupt checksum rejected
Five single-character operation frames: 270 bytes including journal header (not an Android storage/performance measurement)
PASS: 500 deterministic insert/delete/replace operations against StringBuilder, including split surrogate boundaries; scratch cleanup
PASS: 50,000,000-byte streamed base with a one-character replacement; output length/content and scratch cleanup checked under a 128 MiB JVM heap cap
```

For the 50 MB harness, length and the replacement at byte offset 1 were checked;
this was not a full-byte comparison or a timing/peak-RSS measurement. The committed
JUnit version checks every output byte and now passes in the Gradle regression
suite. The fuzz harness compared the entire result against StringBuilder.

An intermediate scoped cached-API type-check passed. A later scoped check was
terminated with SIGKILL; no larger scoped compiler was started. After the user
freed RAM and authorized resuming tests, Gradle ran sequentially with one worker,
`-Xmx1536m -XX:MaxMetaspaceSize=384m -XX:ActiveProcessorCount=1`, no parallel build
and the in-process Kotlin compiler. Host tests used one 512 MiB test JVM.

Actual final commands/results:

```text
./gradlew :app:compileDebugKotlin [bounded/offline flags]
BUILD SUCCESSFUL in 3m 10s

./tools/check-notes-low-memory.sh
BUILD SUCCESSFUL in 3m 38s
81 tests, 0 failures, 0 errors, 0 skipped; 22 test classes (from Gradle XML)
```

The first 37-case run had 8 failures in Robolectric's directory-open shadow. An
explicit filesystem boundary exposed a second unsupported Android rename stub.
Host fixtures now use a real `Files.move(..., ATOMIC_MOVE)` and a directory-existence
check. Production defaults remain Android `Os.rename` and `Os.open/fsync/close`.
**Host tests do not validate directory fsync or Android power-loss durability.**
Existing instrumented storage tests retain the real production default and still
need a device/emulator run. No production exception is suppressed for test success.

A projection test incorrectly asserted that a projection's preview field was an
empty raw database column. It now checks raw column byte length separately, keeps
the full-file assertion, and tests an actual metadata save from a list projection.
The broader run then found two native IME fixtures requesting a connection before
asynchronous layout finished. They now wait on real public editor readiness.
Finally, a preview-edit failure test verifies that a failed full checkpoint can be
recovered in a fresh session from the operation log without changing the saved
original. Final 81-case results include these fixes.

Coverage includes journal integrity/torn frames, a byte-for-byte 50 MB streamed
recovery, native selection/IME across former block boundaries, editor freeze,
capacity/size policies, Markdown block/read-session behavior, search/replacement,
cleanup protections/restore, projection-body preservation, export/import, startup
crash policy and model lifetime. This is not a coverage percentage or device test.

The reproducible resource-limited command is `./tools/check-notes-low-memory.sh`.
It builds source/classes needed by tests, not an APK, and does not launch an
emulator or modify persistent Gradle properties. `git diff --check` also passed.
Pre-existing Gradle deprecation and screenshot-test Compose API warnings remain;
no release lint/R8/benchmark claim is made. Timing/peak heap of the actual app
remains unmeasured; Java heap caps are not peak-RSS measurements.

## Commits

- `926e10b` — checksummed operation journal and disk recovery core/tests.
- `d659d34` — native edit journal integration and backpressure.
- `4ca72a6` — storage, explicit cleanup, older-copy export/restore and tests.
- `0b4d5f9` — document-release regression test.
- `e979934` — emergency draft export and recovery-ready guards.
- `45a1256` — recovery writer transaction/failure tests.
- `15f4ec8` — host filesystem seam and stronger projection-save tests.
- `958141a` — preview edit journaling and failed-checkpoint recovery regression.
- `6f68c84` — native IME fixture layout readiness.
- `c77efcd` — resource-limited regression runner.

## Before emulator stress testing

The live disk-backed editing requirement remains open; recovery alone does not
satisfy it. Debug compilation and the host regression gate now pass. Android
filesystem durability, process death at publish/retirement, IME variants, rotation,
multi-window, native wrapped layout and emulator/device performance still require
validation. The remaining live-editor architecture gap must not be described as
complete or hidden by the passing recovery tests. No final release gate is claimed.
