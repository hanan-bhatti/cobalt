# Saved-version deltas and reader scheduling

## Storage implementation

The current body remains a complete, app-private `.md` file. After a successful
database commit, one bounded background consumer attempts to compact eligible older
versions, including intermediate preview checkpoints. Queue saturation, low heap headroom, cancellation or IO failure retain
complete versions. A failed optional compaction does not roll back a successful Save.

History uses a versioned binary format with a base hash/length, target hash/length,
and gzip-compressed copy/insert commands. Content-defined chunks range from 2 to
32 KiB; matching SHA-256 chunks reference anchor offsets. Small scattered edits and
insertions can reuse unchanged bytes without diffing two resident document Strings.
The anchor index has a fixed maximum entry count. No new library, Git repository,
network request or database migration is involved.

An older complete generation serves as the immutable anchor. Deltas never depend
on other deltas, preventing arbitrarily long replay chains. The first replacement
may still retain two complete files. A typical long history retains the current
complete body, an older complete anchor and smaller deltas. Unrelated versions or
changes touching most chunks may remain full files: conversion requires at least
10% size reduction. This is not a promise of an optimal/minimal binary diff.

Publication writes and fsyncs a temporary delta, reconstructs it through bounded
buffers, verifies the complete SHA-256, atomically renames it, syncs the directory,
then unlinks the redundant old `.md` and syncs again. An interruption can leave
extra complete/delta/temp copies. Existing open file descriptors remain readable;
new readers find the complete copy or verified delta. Unknown/temp files are retained.
Anchors are protected against explicit cleanup while any delta references them.
Reviewed bulk cleanup removes dependent deltas before considering their anchors.

History export streams reconstruction. History random-range browsing currently
materializes a temporary verified copy in app cache, removed on close. Restoring a
delta to be the active version first writes a verified complete Markdown file, then
switches the Room reference. Temporary space and IO are required for these actions.
Compression, indexing and reconstruction verification run outside the database
mutex while leases protect both source generations. Only final publication serializes
with note writes, rechecking that the requested current hash still matches Room.
Actual device save/open latency still needs profiling. Rapid saves can outpace the
bounded queue and leave full history copies; no storage quota is guaranteed.

## Editor and renderer scope

Live native undo is still Sora's operation history in RAM, with its existing pressure
guard. Saved-version deltas do not turn that into disk-backed undo, nor replace the
resident text model. The original disk-backed 50–55 MB continuous-editor requirement
is **not complete**. Do not report this archive as meeting it.

Reader prefetch now prepares visible blocks first, then ahead, then behind. It yields
the mutex every eight speculative blocks. Offset scanning publishes smaller batches;
heading jumps prepare a small target prefix instead of hundreds of surrounding blocks.
Changes in jump state restart prefetch even when the scroll position is unchanged.
Cold misses remain possible; no guaranteed 50 ms/1 s opening or frame budget is claimed.

Checkbox edits use CommonMark source spans and replace one status character, keeping
CR, LF, CRLF and Unicode source intact. Fenced/indented code is excluded by parsing.

## Verification

The earlier delta-only baseline passed **95 host tests, zero failures/errors/skips,
25 classes**, in **3m 27s**. The subsequent Storage/Trash run passed **114 host tests
in 32 classes**, with zero failures/errors/skips. Coverage includes delta round trips,
random scattered edits, corruption/truncation, archive materialization, anchor
protection, cancellation, interrupted publication, checkbox edits and visible-read
priority. Measured synthetic fixture outputs from the Gradle XML:

```
HOST_HISTORY_FIXTURE: 11 versions, 1000000 bytes each, physical body/delta bytes=2518574
HOST_DELTA_FIXTURE: 50000000-byte base and target, one changed byte, delta bytes=6554
```

The first fixture uses a random 1 MB base and ten scattered single-byte edits;
every version is reconstructed and compared byte-for-byte. The second uses a
generated deterministic byte pattern, streams all 50 MB and verifies its SHA-256.
These are file-content lengths, excluding allocation rounding, filesystem metadata,
journals, scratch files and app database. They are not app latency/RAM measurements
or guaranteed compression ratios for arbitrary documents. The test JVM cap is
512 MiB, not a measured peak heap. Host filesystem seams do not prove
Android fsync/power-loss behavior. Device stress, R8 release, IME/rotation testing,
1 GB library performance, CPU/PSS/jank and ten large live undo/redo steps remain
unverified until explicitly measured.
