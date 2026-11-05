# Checklist and Home scroll fixes — 5 November 2026

## Checklist cause and fix

The detail body was keyed by content hash. Every checkbox save produced a new hash,
destroying the old reader, cached blocks and composition. The lazy list briefly had
too few items to retain its current position. A separate busy flag displayed a progress
bar for every save. Capture preview also reset readiness on every busy-state change.

Checklist updates now prepare the affected block optimistically, retain the reader's
offsets, other cached blocks and lazy-list state, and adopt the verified file generation.
Only a single ASCII task-marker change qualifies for this path: UTF-8 offsets and
Markdown block boundaries remain unchanged. Other document/version changes still create
a fresh reader. Saves remain serialized; a failed save restores the previous displayed
block and preserves the existing recovery/error path. No busy progress bar is inserted.
Verified task saves update detail metadata without reopening the note. Late initial-load
results cannot replace a newer saved generation. Capture preview remains visible while
a checkbox save runs, but still waits for an initially busy document before entering preview.

## Home findings and fixes

- Cards read the entire shared-transition visible-ID set. Each changed set could
  invalidate every visible card. Each card now observes a derived eligibility Boolean.
- Scrollbar scrubbing requested a lazy-list jump on every display frame, even when
  its target did not change. Requests now run only for a changed target, coalesced
  to display frames; the last drag position is applied at release.
- Drag mapping remeasured newly visible card heights on every request. A drag now
  uses one sampled geometry, preventing feedback from moving the target.
- A spring followed scroll position while scroll state was read in composition.
  Thumb positioning now reads progress in placement and follows it directly.
- Pointer input and scrub effects now use the adapter as a key, avoiding stale
  list/grid adapters after switching layouts. List averages avoid temporary collections.

Existing layout, colors, icons, blur settings and navigation animations are retained.
GPU blur cost, cold card/image layout and actual frame timings remain device-profile
questions. These code findings are not measurements of their share of observed jank.

## Reproduce validation

```sh
./tools/check-editor-isolated.sh --tests com.example.ui.theme.TypographyPrefsTest --tests com.example.ui.components.LazyScrubGeometryTest :app:assembleDebug
```

New host coverage verifies marker-only eligibility, retained block/cache identity,
switching subsequent reads to the saved generation, rollback preparation, and bounded,
monotonic scrollbar targets for 1,000 items in one and two lanes.

Result: **148 tests, zero failures/errors** (JUnit XML), and `BUILD SUCCESSFUL in
4m 31s` including debug APK packaging. One worker, 1536 MiB Gradle heap; no emulator,
release/R8 task or data reset. Existing Compose-test/Gradle/JVM deprecation warnings remain.

Phone verification remains necessary: toggle tasks well below the top, test save
failure and rapid taps, scroll and drag in both Home layouts with 1,000 notes, and
record frame times/CPU/heap. No device smoothness or frame-rate result is claimed.
