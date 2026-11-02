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

Small documents retain the existing Markdown renderer, now parsed off the main
thread with size/depth/link guards. Larger documents below 5 MB use lazy bounded
blocks and a 32-block text cache. Complete blocks end at blank lines outside code
fences. A structural block that exceeds the viewport budget is displayed literally
in bounded fragments; this avoids incorrect partial Markdown parsing. Complete
tables/lists/fences within the budget retain normal rendering. Cross-block
reference-link definitions and full formatting of oversized structures are not
implemented; plain-text mode remains available.

At 5 MB and above, the reader uses bounded plain-text ranges. All note sizes use
one original DetailScreen, with Find, Outline, export and saved-edit undo/redo in
its overflow menu. Capture retains the original title, folders and frameless body;
formatting controls appear only with the keyboard. Its file-backed body is one
LazyColumn of bounded ranges, with a native Compose field in the focused range.
There are no page/section navigation controls. Tapping another visible range
stages the previous edit privately, then focuses the tapped range. This is a
scrolling block editor, not a complete custom virtual text layout: drag selection,
IME composition and cursor navigation across range boundaries remain unsupported.
Text may wrap at a hard range boundary inside an unusually long logical line.

The file editor journals its focused range and immutable staged generation.
Switching focus does not change the database body reference. Save streams a piece
table result through the verified writer with a concurrent-version check; an error
retains the original and staged files. Pause/stop flush the range journal. Cancel
closes the surface but retains the draft for the next edit of the same generation.
A conflicting saved version prevents draft application; automatic reconciliation
never promotes or deletes that conflicting generation.

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
