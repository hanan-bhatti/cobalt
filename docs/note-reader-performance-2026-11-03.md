# Reader hot-path changes — 2026-11-03

Source implementation only. No Gradle build, Kotlin compilation, unit-test run,
APK installation, emulator benchmark or release/R8 check was performed for these
changes, following the user's no-build instruction. Earlier successful tests/APKs
do not verify this revision. There are no new dependencies or network calls.

## Why a 2 MB note waited

The previous LazyMarkdownNote scanned the whole file, then traversed every block,
opened a range and parsed Markdown before publishing the block list. Plain blocks
with zero links never exhausted the 300-link allowance, so the upfront parse still
visited the entire plain-text document. Visible MarkdownText components then
parsed those blocks again. Its index-keyed scroll effect also reset the position
when a newly published index changed. This is a source-level diagnosis; no elapsed
time or CPU profile has been measured for that path in this pass.

The plain reader dispatched IO and queried metadata for each tiny display chunk,
despite caching underlying pages. That added avoidable per-row work on scrolling.

## Implementation

- MarkdownBlocks.Cursor yields bounded ranges incrementally. Startup no longer
  needs an eager list of the entire note. Existing scan callers retain their API.
- Read/prepare up to 64 blocks and at most 16 KiB of body data per batch; parse
  only visible/nearby ranges. A distant find/heading jump indexes skipped offsets
  without parsing them, then warms its destination.
- Keep a 96-block prepared-content LRU and a 32-text parsed-document cache per
  reader. Reuse parser instances per worker thread. Feed prepared ASTs directly
  to MarkdownText instead of parsing twice. The per-note decorated-link cap stays
  at 300, with no jobs or network requests spawned per link.
- One serialized reader controls scanning/preparation; nearby work observes cancellation
  between bounded batches on close. A scanner batch already consumed is published even if its consumer is
  cancelled, so ranges cannot silently disappear. Cursor cleanup is guarded.
- Plain reading binds a metadata/generation snapshot once. Cache eight immutable
  16 KiB pages, prefetch the current page plus two nearby pages and decode cached
  slices without IO/worker locks during first composition.
- Replace the spinner and “Rendering Markdown…” text with a static skeleton.
  It deliberately has no continuous shimmer animation or idle animation CPU cost.
- Keep the existing large-note plain-text safety tier (5 MB and above). This work
  does not claim full-document Markdown parsing of 55 MB in under one second.

Caches are bounded; a Markdown offset index grows as ranges are visited or an
explicit distant jump indexes them. Capture editing/selection is not replaced by
this reader work. Structural Markdown and reference-link limitations from the
existing block renderer remain. Note bodies/storage are never changed by reading.

## Navigation and icons

Recovery/list/plain-text pages use the same NavHost transitions and ScreenWrapper
as the other settings pages, replacing the local 240 ms slide/fade for those
routes. This includes native predictive-back seeking and existing depth/dim/corner
effects. Standalone safe-mode exit still has its confirmation behavior.

Use the existing `ic_custom_export`, `ic_custom_delete`, `ic_custom_more_vert`,
`ic_custom_text` and `ic_custom_sync` resources unchanged. Duplicate custom action
icons were removed. The export/more resources were already present as shared
uncommitted assets and are preserved unchanged in a separate resource commit.
Other concurrent editor, Home, Folders, OCR and ViewModel work remains untouched.

## Checks and limits

- Ran whitespace checks, existing resource XML parsing and source-reference review.
- Added regression tests for yielding the first Markdown block from a 55 MB virtual
  stream without consuming the document, and warmed UTF-8 plain-text slice lookup.
  These tests are **not run**. Existing byte-boundary/fence tests must also be rerun.
- No first-content latency, typing latency, frame rate, jank, peak heap or CPU usage
  was measured. Universal 50 ms loading, near-zero RAM/CPU and 55 MB fully parsed
  under one second are not guarantees made by this change.
- After a user-built APK is available, measure first readable content (separately
  from full indexing/parsing), scroll both directions and jump far; test close/
  cancellation, checkboxes, link limits, Unicode, rotation, plain recovery and
  predictive-back commit/cancellation. Then resume the deferred 1 GB library test.
