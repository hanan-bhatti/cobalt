# Markdown storage design — all-note files

Updated by the user’s instruction: all text/code note bodies use private Markdown files, including one-line notes. Implementation is in progress; no user database has been migrated by the development agent. Builds and device checks remain unverified.

## Schema and body migration

Use one Room v8 → v9 schema upgrade for preview (200 characters), bodyLocation,
byteLength, contentHash and formatVersion. This combines the originally separate
preview and hybrid-storage schema changes so both do not claim version 9.
Remove destructive migration fallback: an unsupported schema must fail without
wiping user data. Keep historical compatibility columns during this change.

Do not SELECT a full legacy body into a CursorWindow. Read existing bodies using
bounded `substr(CAST(content AS BLOB), start, length)` queries (16 KiB maximum),
with incremental UTF-8 decoding and SHA-256. Metadata queries exclude bodies.
A separate resumable body-conversion pass runs after the schema upgrade.

Every text and code note uses a file, including empty and one-line notes. Room
stores metadata, a short preview and the verified body reference, never an inline
copy after conversion. URLs and media paths retain their existing metadata role.
Private files are immutable generations at
`filesDir/notes/<sha256(note-id)>/<sha256(body)>.md`. Hashing the identifier also
prevents path traversal and filesystem-name limits without changing note IDs.
No NoteBodyStore consumer accesses these files directly. Export names end in `.md`.

Stream to a temporary file in the same directory, fsync, rename, verify length
and SHA-256, commit metadata and removal of the old inline copy transactionally,
then remove the superseded generation. Journal interrupted edits. Reconciliation
must check DB references before removing orphans; missing files mark the body
missing and never delete the note. No migration truncates or refuses an existing
note, even above 55 MB.

## First launch and manual recovery

Gate legacy database initialization before constructing the normal ViewModel or
starting workers. Show a single versioned consent dialog before conversion.
Persist that the dialog was shown; a failed/interrupted attempt goes to a resume
screen rather than repeating the dialog. Never mark migration complete until
verification passes for every migrated note. Persist progress and results.

Add a matching Settings category named **Data Migration**, using an existing
archive/organization-style icon. The page shows status, counts, failures and the
last completed version, with an explicit **Run migration again** action.
Reruns are idempotent: verify and repair state without duplicating notes. Keep
failed originals, expose errors, and allow retry. Do not present an incomplete
run as successful. The page includes: “This migration tool is temporary and is
planned for deprecation once the storage transition is complete.” No removal
date or automatic deletion of the recovery tool is implied.

## Limits, search and editor

Use decimal MB for the 200 KB, 5 MB, 50 MB and 55 MB policy boundaries. There is
no inline-body threshold: every text/code note uses a file. Accept new content through exactly 55,000,000
bytes, warning above 50,000,000. Refuse larger new imports with a split option.
Existing oversized content always remains accessible in plain large-file mode.

Use a streaming SAF import for multi-MB files. Clipboard limits need separate
verification; do not promise that Android can carry a 50 MB clipboard payload.

The planned piece table and incremental UTF-8 line index sit behind NoteBodyStore.
They are not implemented in this checkpoint. The current large-note fallback
uses lazy byte windows, a bounded page cache and streaming section replacement.
It displays plain text without link decoration. Small notes retain the existing
Markdown renderer. Editor choice and cross-block Markdown semantics still require
the Phase 4 options review.

The implemented search uses the existing Room/SQLite FTS4 support with the first
32 KiB of each file plus its title. This requires no new dependency. It does not
promise FTS5 availability on older supported Android versions. A match beyond the
indexed prefix requires in-note find, which remains to be implemented. Full-file
indexing is not enabled.

## Resource budget for development

No build, emulator, benchmark, dependency download or matrix run without separate
user approval. Current code changes and tests remain unverified until those
checks are explicitly authorized. Do not substitute static checks for compilation
or measured runtime performance.
