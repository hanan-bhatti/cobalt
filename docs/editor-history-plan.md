# Editor history and large-file work

## What exists now

- Native Sora undo/redo retains edit operations in RAM. It does not save a complete
  Markdown file for every undo step. Large deleted/inserted strings still consume
  RAM; the current pressure guard can discard older undo history after 8 MiB of
  estimated changed text. Ten arbitrary large edits are therefore not guaranteed.
- Crash recovery writes checksummed edit transactions relative to a verified base.
  These transactions are not persistent undo history: a deletion records its
  coordinates, not an inverse operation containing the deleted text.
- Sora copy-on-write snapshots are temporary stable views for streaming. They share
  unchanged lines, but subsequent edits can retain extra line buffers until release.
- A changed explicit Save, preview checkpoint or dirty close writes a complete
  verified immutable Markdown body. Distinct saved bodies remain as older copies.
  Identical hashes reuse storage, but streaming an identical document still costs IO.
- Unchanged continuous saves now reuse the existing verified reference when the
  recovery writer is healthy. Failed writers still force a full checkpoint.
- Cleanup remains explicit. This change does not delete existing saved versions.

Saved-history follow-up: older complete generations can now be compacted into
verified copy/insert deltas, while retaining the current `.md` and its history
anchor. See [implementation and limits](note-delta-history-2026-11-03.md).
This replaces redundant storage bytes, without deleting a restorable version.
The live disk-backed editor and inverse-operation undo below remain planned.

## Planned replacement, not implemented by the unchanged-save fix

1. Keep the original file immutable during an editing session. Append inserted text
   to an app-private edit buffer. The live piece table references ranges in those
   files; it must not construct a whole-document String or resident Sora Content.
2. Store undo/redo entries as removed/inserted range descriptors, selection before
   and after, and an operation-group identity. A deleted 50 MB range stays in the
   immutable backing file instead of becoming a 50 MB String. New edits invalidate
   the redo branch, but referenced bytes must remain until no history uses them.
3. Keep crash recovery separate from optional saved-version history. Persist groups
   with checksums and an atomic commit marker, including enough information to
   rebuild both the current document and retained undo/redo after process death.
4. Bound RAM by a viewport text/layout cache and sparse background line index.
   IME, global selection, surrogate/grapheme movement, accessibility and long-line
   wrapping must work across cache boundaries without exposing block controls.
   The existing recovery piece table alone does not provide these UI behaviors.
5. Compact edits into one new verified `.md` checkpoint when publishing a changed
   saved body. Update the database only after verification. Keep the old base and
   edit buffer until active readers, recovery and undo no longer reference them.
   Full saved-version retention remains a separate, explicitly controlled policy.
6. Profile a 1 GB library with one active 50 MB document, including ten small edits,
   ten large replacements, undo/redo, reopening, interrupted writes and disk-full
   failures. Measure first content, editable latency, frame jank, app PSS/Java heap,
   CPU, journal growth, retained versions and peak temporary disk usage separately.

This does not promise ten whole-document replacements use negligible disk: new
inserted bytes have to exist somewhere. Ten different 50 MB replacements can add
roughly 500 MB of edit-buffer data even with descriptor-only undo. Compaction also
requires temporary free space. No device performance result is claimed here.
