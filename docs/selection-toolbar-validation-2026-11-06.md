# Editor, Markdown and migration validation — 6 November 2026

## Scope and changes

Edit remains plain Markdown source; Preview renders Markdown. Resident and
file-backed editors share formatting commands, list continuation, clipboard
selection tools and table operations. No dependency was added for this work.

- The shared rounded selection toolbar uses existing icons and the supplied Cut
  SVG. Its single row ends in More; long presses show action labels. Readonly
  fields offer supported selection actions; editable notes add formatting.
- Enter continues bullets, numbered/alphabetic lists and checklists. Empty items
  exit or outdent. Backspace removes a list prefix. Code fences, composition and
  bulk paste are excluded from automatic list continuation.
- Table creation, rows, columns, alignment, navigation and cancellation use bounded
  background transactions. Existing-cell navigation only moves the cursor and
  does not rewrite source or add undo history. Table tools have explicit limits;
  they refuse oversized transactions without truncating the source.
- Inline code uses monospace text with a softly rounded padded background.
  Preview now applies Markdown column alignment.
- Capture folder controls account for the keyboard once and request visibility
  when the new-folder input gains focus. The duplicate footer gap is removed.
- Startup migration uses scalar metadata checks. Empty new installations and
  verified file-backed libraries skip it; legacy bodies/drafts and interrupted
  or failed migrations still require attention. No full body is needed for this
  startup decision.

## Completed checks

The bounded command runs with one Gradle worker, no parallel builds and a
1536 MiB Java heap cap:

```sh
./tools/check-editor-isolated.sh \
  --tests 'com.example.ui.components.CobaltSelectionMenusTest' \
  --tests 'com.example.ui.dock.DockSelectionContextMenuTest' \
  :app:assembleDebug :app:assembleDebugAndroidTest
```

The final bounded host run passed **185 tests, zero failures, errors or skips**
and built both debug APKs in **8m 17s**. Evidence:
`/tmp/cobalt-visual-alignment-retry.log` and the isolated build's JUnit XML.
This is a targeted suite, not the whole app test suite.

On emulator-5554 (API 37), the installed debug build passed **5 instrumented
tests, zero failures, 105.254 seconds**:

- Shared menu More keeps focus/selection; plain source typing transitions to
  rendered Preview.
- All 13 formatting commands change source correctly and render in Preview:
  bold, italic, strikethrough, three headings, bullets, numbering, checklist,
  quote, inline code, fenced code and link.
- Table creation, add/remove row/column, alignment source, next-cell navigation,
  Preview and cancelled creation.
- Fresh-install/empty opened database migration gating and a retained 2.5 MB
  legacy body, using isolated test preferences/database paths.

Evidence: `/tmp/cobalt-all-markdown-tools-final-device.log`. The debug library
was preserved; the migration test never clears it.

The first table UI run failed on Add column immediately after creation. The
parser rejected the header before reading its following separator/body rows.
That defect was fixed and the five-test suite above reran successfully.

Captured formatting previews were inspected as a contact sheet. Inline-code
padding/monospace, heading hierarchy, lists, checklist, quote, link and
strikethrough were visible. Images are ignored build artifacts under
`app/build/device-tests-20261106-editor-tools/`.

Earlier direct app checks also verified folder input above the keyboard,
folder-help text, readonly Dock selection and copying exactly the selected word.
Keyboard preferences were restored and unsaved test edits discarded.

## Final alignment refinement

Visual/source review found Preview ignored column alignment despite correctly
writing the Markdown delimiters. The renderer now applies it. The instrumented
table test additionally measures the first glyph's horizontal position for
left, center and right alignment and captures each result. A host regression
also checks disk-backed Next cell preserves the native content buffer/history.

The initial compile exposed a static Node/TableCell type mismatch, corrected by
checking the cell type. A subsequent verification was interrupted without a
final result. The bounded retry passed all 185 host tests and produced both
APKs. Both installed successfully without clearing the library. The final
instrumented rerun was interrupted when emulator-5554 disconnected; ADB listed
no devices afterward. Its log (`/tmp/cobalt-editor-committed-device.log`) has no
final summary, so the new glyph-position assertions remain unverified on device.
This incomplete rerun is not counted as a pass or an assertion failure.

## Limits and remaining validation

- Readonly selection is scoped to a rendered block. Selection across unloaded
  lazy-list blocks is not whole-document selection.
- Cold list/fence indexing is background work; worst-case typing latency at the
  end of a huge document has not been measured.
- These tests exercise real controls/rendering in a focused test activity;
  they do not prove every Capture, share, Dock and navigation lifecycle path.
- Full 1 GB library stress with repeated undo/redo, process-death tests, storage
  accounting/cleanup, Trakt and all integrations have not been rerun on this APK.
- API 29, 31/32, 34, 36, low-RAM profiles, a physical phone and release/R8 smoke
  testing remain outside these results. No release-readiness claim is made.
- Historical 50 MB software-emulator scrolling missed the performance budget
  (112/116 janky frames, 96.55%; PSS 208061 KiB). These editor checks do not
  resolve or supersede that measurement. No sub-second open or 60 fps claim.

## Interaction references

- [Android tooltip guidance](https://developer.android.com/develop/ui/compose/components/tooltip)
- [Android accessible touch targets](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
- [Obsidian edit/read modes](https://obsidian.md/help/edit-and-read)
- [Typora table editing](https://support.typora.io/Table-Editing/)
