# Note storage and rendering

Cobalt is a single Android application. Text and code note bodies, including
empty and one-line notes, are immutable Markdown generations in app-private
internal storage. `NoteBodyStore` is the only component that accesses body files.
Room v9 stores metadata, a 200-character preview, byte length, format version and
SHA-256 reference. Links/media keep their existing URL/path representation.

## Writes and recovery

Paste preparation and UTF-8 encoding run on background dispatchers. Large text
replacement uses non-copying character slices and a stream, avoiding another
whole-document String/byte array. SAF import streams from the chosen document.
Above 55 MB, new imports are refused with an explicit split option. Splits preserve
every byte and UTF-8 character but may divide Markdown structures; the UI warns
before proceeding. Existing oversized notes remain accessible and can shrink.

Writes create a same-directory temporary file, fsync it, rename it, verify its
length/hash and commit the Room reference transactionally. Legacy SQLite bodies
are read in 16 KiB blob slices and independently verified before clearing the
old value. Unsupported migrations fail without destructive database fallback.

Migration acknowledgement and completion are separate persisted states. A
versioned startup prompt precedes conversion; Settings → Data Migration exposes
retry/results. Quick drafts and section replacement operations have private
recovery files and small preference references. Pause/stop flushes a section
journal through the ViewModel. Conflicting drafts recover as separate notes.

Startup reconciliation checks references/lengths, marks missing bodies and keeps
interrupted copies. It does not guess which unreferenced generation is disposable.
Saved-generation undo/redo changes verified references without loading bodies.
Retained generations can consume disk space; automatic orphan deletion is not
enabled. Export is the preservation route for missing/conflicting data.

## Browsing and search

Room list queries substitute previews for note bodies. Home, folders, widgets,
heatmap, suggestions and reminders receive metadata. Side Dock asks Room for only
15 recent items and shows two preview lines; it never parses a complete note.

Global search combines metadata ranking with FTS4 over title and the first 32 KiB
of a file. This deliberately omits later content from global results. In-note find
streams the complete file; line lookup uses a sparse UTF-8 byte index with LF,
CRLF and CR handling. No additional SQLite distribution is required.

## Reader and editor

Markdown parsing stays off Main. The bounded reader carries context for long
fences, table headers, headings, lists/quotes and common inline delimiters across
source ranges; it no longer turns hard-split blocks or over-budget links into raw
Markdown. Excessive container nesting is flattened while retaining its children.
The 300-link cap controls interaction/styling, not paragraph formatting. This is
not a complete CommonMark implementation for every possible cross-range construct:
reference definitions across ranges and unusual/malformed delimiter cases remain
limitations. Stored/exported source bytes are unchanged by display.

Offset indexing runs in background batches after first content. Small paragraphs
are grouped within viewport limits; Markdown-only blank ranges need no layout.
Prepared blocks use an 8 MiB estimated-cost LRU plus a 64-document parse-reuse
cache. Nearby ranges (up to 256 blocks ahead) are warmed in batched reads through
a persistent random reader; no file open per paragraph and no second visible-row
parse. The offset index grows with the document and is not a constant-memory
structure. Scrolling has no per-row skeleton; a cold miss reserves bounded space
until prepared. This is not a zero-latency guarantee. Initial loading still has a
skeleton. These performance characteristics require device measurements.

The Markdown reader works at every file size; plain text is an explicit mode,
not a size-forced override. All note sizes use one original DetailScreen, with Find, Outline, export and saved-edit undo/redo in
its overflow menu. Capture retains the original title, folders and frameless body;
formatting controls appear only with the keyboard. Small in-memory notes retain
one bounded Compose field. File-note capture uses one fixed-height native Sora
editor with global selection, IME composition and operation-based undo. It does
not switch editable fields at paragraph/window boundaries. Formatting commands
modify selection endpoints; the whole body never passes through Compose state.
The formatted preview remains a separate mode inside the same Capture screen.

The editor core holds a structured resident document (compact per-line buffers),
not a disk-backed piece table. Loading streams through ContentIO off Main. A
conservative capacity scan considers note bytes, line count and heap headroom
before allocating the model. Its 128 MiB model-estimate ceiling is a configured
guard, not a measured heap guarantee. Dense 50–55 MB notes may be refused for
editing; Capture then offers bounded plain-text reading/export of the complete
body. This does **not** fulfill the original requirement for all 50–55 MB notes to
edit within the specified memory budget. A fully file-backed text model remains
unimplemented. Undo pressure trims older history after an 8 MiB change threshold;
the active transaction can exceed it and is not a hard memory bound.

Visible/nearby source lines receive bounded lexical Markdown styling. Find uses
literal UTF-16 search over copy-on-write snapshots; it avoids Sora's stock search,
which copies the complete document. Replace-all refuses more than 10,000 matches
before making changes. Paste preflight runs off Main, inserts bounded native
chunks with yields, offers progress/cancel and groups the edit for undo. Normal
IME commits, new Android input APIs and clipboard cuts have size/failure guards.
The library's optional text diagnostic flag stays disabled, with a release R8
assumption reinforcing it. No runtime network service/language pack is included.

Native content events enter a bounded operation journal. Mutations within a UI turn
share a checksummed UTF-16 transaction; an IO consumer coalesces fsync calls and
paste/replace-all await bounded backpressure. Idle and pause/stop flush operations
instead of retaining full body copies. Explicit Save, formatted preview and dirty
close stream a COW snapshot into an immutable verified generation. Recovery
references advance before old logs are retired. Saved originals and older full
copies remain until explicit retention cleanup; there is no automatic pruning.
Only synced frames/full copies are durable across process death. Complete corrupt
frames fail closed; torn tail frames are excluded while originals remain retained.
Preview edits use the journal even when no native editor View is attached.

Recovery verifies the base and replays operations over UTF-16 scratch files through
a disk piece table, then streams a verified UTF-8 generation. This happens before
resident-editor admission, so recovery and export need no full Sora document.
It does not make the live editor disk-backed. Save verifies/commits a prepared
generation without writing it twice. Conflicts retain original/recovery references;
explicit emergency export can stream a live snapshot directly to SAF. Legacy
focused-window drafts are folded into a verified generation before model loading.

Import is available in Add, not Capture. SEND, SEND_MULTIPLE and content-provider
VIEW intents can receive Markdown, MDX, plain text, common structured text and
source files. The importer checks MIME/name, rejects known binary extensions,
strictly decodes UTF-8 or BOM-labelled UTF-16 and streams normalized UTF-8 into
private storage. MDX/HTML/JavaScript are inert document text. Multi-file shares are
processed sequentially (at most 100 documents per action); source files are never
modified. No network request or dependency was added for these paths.

## Validation

See `docs/notes-device-test-report.md` for actual command outcomes and remaining
device checks. A debug build is isolated with `.debug`; release ID/signing stay
unchanged. Performance targets are not verified until measured on devices.

## Recovery and retention follow-up (2026-11-03)

Native edits now feed a bounded, checksummed operation journal. Full Markdown
generations are still materialized for explicit Save, formatted preview and dirty
close. Recovery can replay UTF-16 coordinates through a temporary disk piece table
and stream a verified UTF-8 generation before native-editor admission. The live
Sora model itself is still resident in RAM; the recovery adapter is not a live
large-file editor. Emergency export can stream an unsaved COW snapshot directly
to a user-chosen SAF target when internal storage cannot accept another copy.

Storage & Recovery performs metadata/file-size accounting and user-confirmed
retention cleanup. It protects DB references, drafts, interrupted writes, open
readers and undo histories. It retains unknown files; there is no automatic
pruning or compression. Older-copy restore changes the body with a saved-version
guard and never silently overwrites a recovery draft. Detailed current limitations
and actual test output are in [the recovery/storage report](docs/note-recovery-storage-2026-11-03.md).
