# Markdown storage checkpoint — 2026-11-02

This is a development checkpoint, not a release approval or completed performance
gate. No real user database was migrated by the development agent.

## Implemented in source

- Every TEXT/CODE body, including empty and one-line notes, uses an immutable
  private `.md` generation. SQLite stores metadata, preview and hash/length.
- Streamed writes, fsync, rename, independent verification and transactional
  pointer switching. The legacy SQLite body is cleared only after verification.
  Interrupted writes are retained; automatic orphan deletion is not enabled.
- Additive v8→v9 schema migration without destructive fallback. Legacy note reads
  are clamped to 16 KiB SQLite blob slices. List queries return previews instead
  of full bodies. Saving a projection preserves the body reference.
- First-launch migration gate precedes the normal ViewModel. A versioned prompt
  and Settings → Data Migration page expose retry/results and the temporary-tool
  deprecation notice. Failed originals are retained.
- Startup recovery after two uncaught exceptions within the startup window.
  Normal process termination does not increment the counter. Native crashes,
  OS kills and ANRs are not detected by this Java exception handler.
- Large-note fallback uses lazy plain-text windows and streamed section edits.
  Section paste failures retain a retry buffer in the ViewModel across rotation;
  that unsaved buffer is not yet durable across process death.
- Note sharing streams complete Markdown with frontmatter instead of a preview.
  Quick/manual note input clears only after a successful save.
- OCR selected-link saving uses one lifecycle-bound sequential coroutine instead
  of one GlobalScope job per link; failed links remain in review.
- Debug application ID suffix/label and debug-only StrictMode. No new dependencies.

## Actual checks

`python3 scripts/check_note_recovery_sql.py` passed on the host:

```text
PASS: actual v8->v9 additive SQL preserves legacy source bodies
PASS: bounded slices and byte-exact 5k-link/UTF-8/NUL/CRLF/one-line/empty cases
PASS: body-reference CAS, preview-only rows and bounded-prefix FTS4 search
NOT RUN: Kotlin compilation, Android Room validation, filesystem/Compose tests or performance
```

The script's NOT RUN line describes its own scope. A separate approved Gradle
`:app:compileDebugKotlin` invocation used one worker and a 1536 MiB Java heap cap.
It **failed in 4m 22s**, reporting two errors: unavailable `OsConstants.O_DIRECTORY`
and a delegated-property smart cast. Both were corrected in source. Further source
fixes described above followed; **the corrected snapshot has not been compiled**.
The full output is in ignored `app/build/notes-debug-compile.log`.

`git diff --check` passed. Unit and instrumented tests were added but not run.
The filesystem tests cover one-line/empty Unicode bodies, saving projections,
conversion above CursorWindow size and simulated write failure. They do not yet
cover every atomic interruption point or Android's generated Room v8→v9 schema
validation. No APK install, emulator, release/R8 check or benchmark was run in
this checkpoint. Heap cap is a configured limit, not a measured peak-RAM number.

## Remaining release blockers / risks

- Recompile the corrected snapshot, then run approved targeted tests. A compiled
  app, successful Android migration and launch are not yet demonstrated.
- The old quick-note draft uses SharedPreferences and still needs conversion to
  streamed private storage before startup can be claimed body-free in all paths.
- Complete durable unsaved edit recovery and test process death/cancellation.
- Piece table, incremental line index, full Markdown block reader, in-note
  find/replace, outline, large-file undo/redo and streaming SAF import are pending.
  Current section editing is a conservative fallback, not the finished engine.
- FTS4 indexes title and first 32 KiB only. Content past that prefix is not found
  by global search; full in-note find remains pending.
- 50–55 MB warning/split workflow, clipboard limits and export/import round-trip
  validation are pending. The policy accepts through 55,000,000 bytes, rejects
  larger new content and retains oversized existing notes.
- Journal/orphan reconciliation and export/edit/delete races require additional
  verification. Retained orphan generations may consume space.
- Permission/FileProvider/logging/token-storage audits and the remainder of the
  security phase are unfinished. Shared export files use the app cache.
- No measured open latency, jank, typing latency or peak heap. No emulator/API/
  low-RAM/high-density matrix, coverage result or final release certification.

Further Gradle builds, tests, emulators and benchmarks require the user's
separate approval because the laptop has limited resources.

## Review commits

- `bd1499a` — isolate debug application data and label.
- `c975b37` — atomic private body store and size/window policies.
- `c8ea89f` — verified file persistence, body-free Room queries and test sources.
- `d731707` — resumable migration gate and startup recovery.
- `592de13` — sequential lifecycle-bound OCR selected-link saves.
- `8712e77` — bounded note UI, explicit loading and save/share integration.

Original unrelated work remains preserved in `bea90ab`; these commits are on
`cleanup/cobalt-single-app`. This report and the design/changelog are a separate
documentation commit. The checkpoint does not close the original phase gates.
