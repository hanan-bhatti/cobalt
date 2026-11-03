# Continuous capture editor — partial source gate, 2026-11-03

## Status and scope

**Historical source gate:** operation journaling, disk-backed recovery and explicit
storage management were subsequently added. See [the follow-up report](note-recovery-storage-2026-11-03.md).
The live editor still uses a resident model; this report is not final acceptance.

Implemented source replacement for the capture editor's independent text fields.
I did not run a Gradle task, Kotlin compilation, unit-test execution, APK build,
emulator or benchmark for this revision, following the user's no-build instruction. This
is not a release-readiness or performance pass. Existing unrelated folder, card,
OCR, Home and other UI/ViewModel changes are preserved outside these commits.
No database migration, app-data reset, signing change or application-ID change.

The original large-file editor mission is **not complete**. The remaining core
implementation is a truly disk-backed Tier C document plus a durable incremental
edit journal and safe compaction. Current full-body checkpoints may consume
substantial disk, and edits since the last checkpoint can be lost after a crash.
Build/device validation and the original performance budgets are also pending.

## Code commits in this source gate

- `de81efa` — file-piece range streams/handle lifetime.
- `e238245` — pinned native core, license notices, reused toolbar assets, R8 logging guard.
- `58c2183` — continuous model and verified draft/prepared-commit path.
- `f9b7f3d` — continuous native capture UI and its IME/selection regression source.
- `e907264` — Markdown mode/continuations, batched prefetch and cache improvements.
- `ca83c3f` — cursor handoff and conservative recovery-generation preservation.

## Choice and trade-off

The preceding editor used a LazyColumn of Text plus one focused Compose field.
Its visual boundaries also separated cursor, selection and composing state.
Removing its borders could not fix that design. The replacement uses one native
Sora CodeEditor, embedded in the original Capture screen at a fixed viewport
height. Small notes keep the existing bounded frameless Compose field. There is
no WebView, JavaScript engine or new editor screen. Markdown/MDX remains inert text.

A custom Compose editor over the existing byte piece table would need its own
text layout, bidi/grapheme movement, selection handles, accessibility and Android
IME protocol implementation. Sora supplies these behaviors rather than recreating
only their appearance. **Sora keeps the structured document in RAM. It is not the
fully disk-backed piece-table engine originally requested.** A conservative byte/
line/headroom admission scan may refuse even a 55 MB note, especially one with
many short lines. The same Capture screen offers complete plain-text reading and
explicit SAF export instead; notes are not deleted/truncated. This is a known
functional limit, not a measured success against the original acceptance criteria.

The configured model-estimate ceiling is 128 MiB. The heuristic uses twice UTF-8
bytes plus 160 bytes per logical line, bounded by estimated Java heap headroom.
It is not a real peak-heap measurement or a universal OOM guarantee. A single
large paste/deletion/undo may temporarily allocate much more. Clipboard IPC and
native document/layout allocations still require real device testing.

## Implemented behavior

- One logical document, global cursor/selection, native composing/keyboard input
  and undo/redo. The short-field-to-file transition passes its global UTF-16
  selection into the new document and recovery metadata instead of resetting it. No user-visible windows, block tapping or range navigation.
- Keep the original title/folders and frameless body; formatting controls remain
  keyboard-only. Reuse the existing undo, redo, outline and search icons unchanged.
- Formatting inserts delimiters at the actual selection endpoints instead of
  copying the complete selected body through a TextFieldValue.
- Prepare source styling off Main for a bounded nearby range (at most 161 lines,
  at most 4,096 characters each, at most 300 link decorations in that range).
  Version-check cached spans so an old analysis cannot style a shorter new line.
  This is source styling, not Obsidian's full Markdown Live Preview implementation.
- Paste size/capacity checks run in a worker; native insertions yield in chunks of
  2,048 UTF-16 units without splitting surrogate pairs. Show progress/cancel;
  finish older IME batches before the paste's independent undo transaction.
  Cancel/error rolls back that transaction. IME commits and API 33/34 overloads
  route through input guards. Composition regions/text are bounded to 4,096 units.
- Bound clipboard IPC. Cut deletes only after Android successfully accepts the
  copied selection, so an IPC failure cannot silently remove its source text.
- Undo has a 100-operation maximum. After an estimated 8 MiB of changed-character
  pressure, older entries are trimmed while keeping the active transaction; a
  visible message explains that history reduction. This is not a hard byte cap.
- Literal, case-sensitive in-note find runs on a COW snapshot, with next/previous
  wrap. Replace acts on the current matching selection. Replace-all refuses more
  than 10,000 matches before mutation and is cancellable/grouped. Queries are
  limited to 256 characters, replacements to 4,096; over-limit input is refused,
  never silently cut. Outline scans bounded line prefixes outside fenced code,
  exposes up to 500 headings and jumps within the same document.
- Formatted preview stays inside Capture. Checkbox edits map verified UTF-8 block
  offsets to global UTF-16 positions and use the same document/undo history. Link
  taps use the existing reader path; the source editor treats normal taps as
  caret placement. Bounded continuation rendering keeps common long structures in Markdown;
  cross-block reference-link and unusual delimiter limitations remain.
- Stream load/save without constructing a whole-document String. File-backed
  piece-table ranges now retain one input handle per piece, closing on completion
  or cancellation; they no longer open a RandomAccessFile for every 16 KiB read.
- Debounce full draft checkpoints (1 s; 3 s for large notes), plus pause/stop.
  Stream a copy-on-write snapshot to the verified atomic writer, persist the
  recovery reference and retain original/older checkpoints. No automatic deletion
  guesses whether a hash also belongs to saved history; repeated full checkpoints
  can consume substantial disk until operation journaling/compaction is added. Explicit Save commits the prepared generation with
  verification and body-version checks, avoiding another complete write/fsync.
- Legacy recovered window edits are staged before capacity admission; fallback
  reading/export includes those edits. Normal process loss restores the most
  recent completed checkpoint. **A per-operation persistent edit journal is not
  implemented; edits after that checkpoint can be lost on abrupt process death.**

## Dependency review

New dependency: `io.github.rosemoe:editor:0.24.6`, core only, pinned on Maven Central.
License: LGPL-2.1-or-later, compatible with a FOSS/F-Droid distribution when its
license/source/rebuild obligations are retained. Include the full license and
source notice in `app/src/main/assets/licenses/`. Cobalt remains AGPL-3.0.

Primary upstream sources:
- https://github.com/Rosemoe/sora-editor/tree/0.24.6
- https://github.com/Rosemoe/sora-editor/blob/0.24.6/LICENSE
- https://project-sora.github.io/sora-editor-docs/guide/getting-started
- https://repo.maven.apache.org/maven2/io/github/rosemoe/editor/0.24.6/

Maintenance: upstream describes ongoing, slow development; this is a pre-1.0
library with API-change risk. The exact published AAR and tagged source were
inspected rather than assuming the website examples match. Upgrades require a
new compatibility/device check. The POM lists AndroidX annotation 1.10.0,
collection 1.5.0 and Kotlin stdlib 2.3.10. This integration excludes the transitive
stdlib to retain the app's existing 2.2.10 toolchain/runtime. That compatibility
has **not** been compiled or tested; Gradle dependency resolution is unverified.

Measured downloaded archive sizes (not APK contribution or heap usage):
- Core AAR: **790,650 bytes**; contained classes.jar: **833,656 bytes** uncompressed.
- Published source JAR: **562,640 bytes**.
- AAR SHA-256: `fb76ae4db31d94d9fee7f97d9b8ec1c9659c54b5ee1ded7f9f95f7039646243e`.
- No `jni/` entries; its manifest adds no permissions/components. No language
  packs, LSP, TextMate/TreeSitter or regex-native modules are requested.

The source audit found optional IME text diagnostics behind the package-private
`EditorInputConnection.DEBUG = false`. The app never enables them, and a deliberate
R8 `-assumevalues` rule reinforces false in release. No reflection name-keep rule
is added. Other inspected logs concern failures/positions, not explicit note-body
logging; exceptions remain a dependency-review consideration. No runtime outbound
connection was added. APK/DEX size, R8 behavior and final dependency graph remain
unverified until a build is authorized.

## Reader-mode correction found during the status review

UnifiedNoteBody previously required both Markdown mode and byteLength < 5 MB to
use LazyMarkdownNote, while DetailScreen also defaulted large text notes to plain
text. Thus choosing Markdown could not actually render a file of 5 MB or larger.
Remove the hard size override and default text notes to the bounded lazy reader;
code notes keep plain text by default. All content remains bounded/on-demand.
The user clarified that content was visible as plain text, not blank. This
correction addresses that forced-mode behavior; runtime verification is pending.

## Markdown and fast-scroll follow-up

The size-forced mode override is removed. The reader also stops downgrading an
entire block when the link cap is exhausted: it renders Markdown normally while
leaving additional link labels non-interactive. Bare URL decoration participates
in that cap too. Parser safety no longer discards the whole AST for nesting/link
count; excessive container nesting is flattened while preserving child content.

Source ranges carry bounded context for fenced/indented code, table continuation
headers (including Unicode), headings, list/quote prefixes and common inline
markers. Each range is parsed as Markdown. LF, CRLF and CR are recognized. Source
byte boundaries and checkbox replacement ranges remain the original file ranges;
synthetic context is rendering-only. Short paragraphs are grouped instead of
creating one lazy row per link. Whitespace-only Markdown ranges need no layout;
whitespace inside code fences remains eligible for code rendering. Code display
removes one parser-added newline rather than trimming meaningful whitespace.

Initial prepared content is published before full offset indexing. Metadata
indexing proceeds in background batches, without parsing offscreen content. The
prepared cache has an **estimated** 8 MiB cost ceiling and a 16,384-entry ceiling;
there is also a separate 64-document parse-reuse cache. Actual heap is unmeasured.
The index is proportional to source ranges, not constant memory. Use one persistent
range handle; warm up to 256 nearby blocks ahead with read batches of at most
16 KiB. Index growth outside that range does not repeatedly cancel prefetch.

Remove per-row/tail scrolling skeletons after initial content. Cold ranges reserve
bounded space while work finishes, rather than display raw Markdown. **This does
not prove invisible loading or zero blank space on every cold jump/device.**
Initial loading skeletons remain. Common long fences/tables are covered by added
regression cases; arbitrary malformed/cross-range Markdown is not proven fully
CommonMark-compatible. No speed, heap or jank result was measured or claimed.

## Checks actually performed

- `git diff --check` (whitespace).
- Parsed the reused icon XML and published library manifest; inspected AAR/source
  entries, POM, license, source logging, undo grouping and stream/IME APIs.
- Inspected published class-file method descriptors for the used content, editor,
  event and line-separator APIs. This is not Kotlin or Android compilation.
- Added targeted unit/Robolectric cases for UTF-8 streaming/line endings, stable
  COW snapshots, cross-boundary undo, global selection/IME commits, frozen input,
  literal search/fuzz/cancellation/range mapping, model-capacity guards, prepared
  commit verification/projection safety and streamed file-piece handle lifetime.
  Added reader cases for preserving formatting after the link cap, batched range
  handle reuse, long fenced code, Unicode tables and CR/CRLF continuation.
  **None of these tests were run.** Earlier passing builds/tests do not validate
  this source revision.

## Required verification and unresolved limits

Run a user-approved limited compile/test pass first. Then test native IME
composition (Gboard/AOSP, emoji, RTL), drag selection across old boundaries,
undo/redo, cut IPC failure, paste cancel/close/rotation, preview checkbox changes,
find/replace cancellation, draft recovery, disk failure and conflicting saves.
The native view has Robolectric tests; Compose semantics alone cannot exercise
its real IME/selection behavior. Device/instrumented checks are still required.

Use the 1k/3k/5k/10k-link, 50k-line, 100k-character, 5/50/54/56 MB fixtures. Measure
first content, time until editable, scrolling/jank, typing p95, heap and CPU on the
requested API/device matrix. No 50 ms, 1 s, <150 MB or 60 fps result is claimed.
The fully file-backed Tier C editor, per-operation durable journal and the
original universal 50–55 MB editing budget are **not complete** in this revision.
