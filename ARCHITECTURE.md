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

At 5 MB and above, the reader uses bounded plain-text windows. Section editing
uses native Compose inputs for IME/selection inside the active window. A piece
table streams original/append fragments on save; operation history and generation
references support undo. Find/current-match replacement, outline, jump-to-line and
copying an explicit line range work without reading the whole body into a String.
Clipboard copies are limited to 100,000 characters; export handles larger ranges.
Drag selection across virtualized blocks is not supported.

## Validation

See `docs/notes-device-test-report.md` for actual command outcomes and remaining
device checks. A debug build is isolated with `.debug`; release ID/signing stay
unchanged. Performance targets are not verified until measured on devices.
