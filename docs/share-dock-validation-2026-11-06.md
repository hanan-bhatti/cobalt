# Link fetching and Side Dock validation — 6 November 2026

## Final behavior

One saved description: `saved_items.linkDescription`. The separate
`link_preview_queue` contains only item ID, URL, fallback eligibility, pending
status, attempt count and request generation; no description copies.

Saving a new LINK commits locally before scheduling background extraction.
Missing descriptions and untouched OCR fallbacks can receive page text. Capture
text, including an explicitly empty value, is protected during automatic fetching.
Capture Refresh keeps its explicit replacement interaction. URL changes, newer
edits and deleted rows reject stale background writes. Text notes containing URLs
are not expanded into thousands of independent bookmark jobs.

Latest user revision supersedes the earlier fixed-height row: Dock Note and Link
inputs start at one line, use 3 dp vertical padding and grow to three lines.
Send is hidden while empty. The light close glyph comes from the supplied linear
plus SVG, rotated 45 degrees; the global app close icon is unchanged.

The Dock note detail reads at most 4,096 characters / 120 lines off the main thread,
removing the old 200-character / two-line detail restriction. Recent cards retain
their short projections. Copy Preview copies the displayed bounded chunk. Header
and footer action layouts stay unchanged, per the user's clarification. Paste uses the existing
frameless icon and a bounded clipboard snippet in the placeholder, without a
second clipboard strip. Existing send/clear assets are retained. Large pasted
text waits for Send; a bounded Dock preview does not truncate the saved payload.

## Executed checks

- Bounded offline debug build + targeted host tests + AndroidTest packaging:
  **BUILD SUCCESSFUL in 5m 3s**, 149 tests, zero failures/errors/skips. Includes
  description policy (3), queue compare-and-set/deletion/URL guards (2), additive
  migration preservation (1), task marker edits (3), and Markdown read-session
  regressions (7). Remaining tests cover existing note/editor/import logic.
- Android `NoteMigrationTest`: **OK (2 tests), 0.403 s**, on emulator-5554, API 37.
  Empty and historical v8 fixtures (including 5,000 links) validate through v11.
- Updated the debug emulator without clearing app data. Live metadata snapshot:
  schema **11**, sole description column **linkDescription**, queue columns
  `itemId,url,replaceFallback,pending,attempts,generation`.
- Existing stress library retained **32 notes / 1,000,000,000 bytes** according to
  metadata. This check did not repeat all body hashes or scrolling benchmarks;
  the earlier stress import had verified body lengths and hashes.
- Dock Link UI save of `https://example.com`: saved original URL, background title
  became **Example Domain**, queue completed with **one attempt**. This page has
  no description, so the sole saved description remained null.
- Inspected the redesigned Dock on the emulator. The initial control row was too
  tall; corrected it to one line / 48 dp, removed vertical padding and the extra
  pending-paste/progress rows, and reduced the controls to 40 dp.
- Rebuilt that compact correction: **BUILD SUCCESSFUL in 3m 46s**, debug only.

## Limits and remaining device checks

Full device verification of clipboard placeholder/paste, Capture Refresh during
concurrent edits, offline/process-death queue retries, Cloudflare/redirect cases,
shared-file review, and the complete manual checklist remains open. Latest growing-input validation: **BUILD SUCCESSFUL in 4m 28s**, 143 tests,
zero failures/errors/skips, including all three new bounded-preview tests. The
build preceded removal of the now-unused old two-line constant (no behavior change).

Installed that build and checked the actual input container bounds on API 37:
90 px empty (30 dp at density 3), 168 px at three lines (56 dp). Send was absent
when empty, present after typing and absent after Clear. The first automation
probe incorrectly compared accessibility touch bounds, which remain 48 dp;
corrected the probe to measure the enclosing visible input frame. No application
change was needed for that probe correction.

The real 50 MB fixture's Dock detail exposed **4,096 characters**, and a screenshot
confirmed text fills the body area beyond two lines. The unsaved input probe was
cleared. Evidence: `app/build/device-tests-20261106-single-description/dock-growing/`
contains XML/screenshots and `input-check.json` / `preview-check.json`. No release
R8 build or new performance benchmark was run in this follow-up. Existing Gradle
and test deprecation warnings remain; this is not a warning-free release claim.

No dependencies added. No production app data cleared or modified. The rejected
three-description-column schema was removed before any emulator installation.


## Markdown preview and floating actions follow-up

- Replaced the wide labelled footer with a centered rounded pill: existing Copy,
  Share/Open link/Hub, and Open in Cobalt icons. Disabled Copy stays visible for
  consistent three-action layout. Tooltips and accessibility descriptions retain
  action names. No new icons or dependencies.
- TEXT preview uses the existing background-parsed MarkdownText renderer; CODE
  items retain monospace code text. The same 4,096-character / 120-line read limit
  remains. This is a preview, rather than loading an entire large note into Dock.
- Final bounded debug assembly: **BUILD SUCCESSFUL in 2m 49s**; no Kotlin compiler
  warnings/errors in that final log. Earlier tooltip overload deprecation warnings
  were fixed by using explicit Above positioning before the final build.
- Created one synthetic Markdown fixture through Dock Note and explicit Send.
  Emulator checks confirmed three icon action descriptions, rendered bold text
  without raw markers, table cells Alpha/42, and no displayed fence markers.
  Visual inspection also confirmed heading, italic text, bullet list and monospace
  code. Evidence: `app/build/device-tests-20261106-single-description/dock-markdown/`.
- No additional unit suite or performance benchmark was run for this UI-only
  follow-up; the prior 143-test preview run remains the unit baseline. Data was
  retained during debug APK updates; no production app was touched.
