# Efficient and reliable note revisions for Cobalt

Research date: November 9, 2026. **This document records the original research and prototype. The subsequent application implementation and its separate validation are in [the implementation report](notes-storage-implementation-validation-2026-11-09.md).** Based on the [completed emulator audit](notes-storage-validation-2026-11-09.md), the owning source, primary technical documentation, and a standalone storage prototype. The prototype never opens emulator or app data.

## Answer to the storage question

Yes: **hundreds of small edits to a 50 MB note should usually add KB to a few MB of retained storage**, rather than another 50 MB for each version. The original 50 MB still has to exist. This expectation concerns incremental history overhead, not total note size. Large new pastes and unrelated full replacements necessarily retain their new information if history is kept.

The present implementation already achieves some delta savings, but starts from complete files. Its first small edit produced **50 MB current + 50 MB history**. After ten edits it used **100,575,231 B** combined; nine deltas were only **575,231 B**. Restoring one delta increased combined storage to **150,503,925 B**. These are measured Android file lengths, not estimates. Stable UI history later compacted from 100 MB to 50 MB, so the extra UI copy was temporary in that flow. The first history anchor remains a full-document cost.

Four source decisions cause this behavior:

1. `FileEditorSession.checkpointContinuous` exports the live disk editor through `disk.openUtf8()` and `store.writeAtomic()`. Every changed checkpoint streams a full body, even for a one-character edit.
2. `PrivateMarkdownStore.prepareArchivePrevious` excludes both the previous and current files when choosing a full `.md` anchor. With only two versions it has no third anchor, so cannot compact the first previous body. It also deliberately skips bodies below 32 KiB. Its delta writer indexes and reads whole bodies, compresses copy/insert instructions, and verifies reconstruction before publishing. Those checks are valuable, but make the maintenance work proportional to document size.
3. `NotePersistence.restoreGeneration` calls `store.materialize()` before pointing the database at an old version. It preserves the outgoing version and does not enqueue its compaction. A pointer change therefore turns back into a full file write.
4. Each `.delta` compares the target with one complete anchor. Inserted chunks are compressed inside that delta, but not shared as reusable changed-text objects between delta files. A later version can encode earlier changes again along with its latest change. History therefore need not grow in proportion to only the newest keystrokes; cumulative edits can repeat retained payload, depending on placement/chunk boundaries/compressibility. The ten-edit audit establishes the actual 575,231 B total, not a measured extrapolation to hundreds of edits.

There is an additional cost during typing: `DiskTextModel.publish` writes descriptors, undo/redo state and a checksum, then syncs and renames the state file after each edit group. This is **metadata write amplification**, rather than a full body per keystroke. The current `PieceTable` is a list of pieces; it caps undo at 100 operations and serialized descriptors at one million. Saving full descriptor lists indefinitely would eventually become inefficient too.

## Recommended architecture

**Use immutable UTF-8 text pieces and a persistent balanced piece tree, with revision roots and small content objects in the same Room/SQLite database as note metadata.** Keep Markdown as the streaming import/export format. Preserve the existing bounded editor and reader behavior while adapting their sources.

This extends an idea Cobalt already uses: original ranges are shared; added text lives separately. The proposed change is to keep sharing after Save, restart, Undo and Restore. A saved version owns a small root reference, not a complete Markdown copy or an unbounded chain of patches.

```text
Revision 0 ── root A ── original pieces
Revision 1 ── root B ── shared original pieces + new text
Revision 2 ── root C ── shared subtrees + another change
Restore    ── new revision record pointing to root A
```

Only the changed path through the tree is copied. A delete drops references from the new root; older roots still retain deleted text. A restore publishes an existing root and a new history event. Reading a saved version walks its own tree directly; it does not replay hundreds of earlier edits.

Microsoft's editor engineering describes original/added buffers, subtree length and line metadata, and balanced piece trees for efficient editing and lookup. **Persistence on disk and immutable revision roots are our extension; the source does not establish that VS Code uses this proposed saved-history format.** [Microsoft, Text Buffer Reimplementation](https://code.visualstudio.com/blogs/2018/03/23/text-buffer-reimplementation).

### Representation and transactions

Start with 16–64 KiB immutable payload chunks; benchmark both on Android before choosing. Small additions should share packed pages/rows rather than creating one filesystem file per character. Leaves describe `(payloadId, UTF8 offset, byte length)` ranges. Small edits split descriptors, not the original payload. Internal nodes retain aggregate byte length, UTF-16 unit count and line information. A persistent AVL rope is a simple proof of concept; a tree with several descriptors/children per page may reduce node and query overhead in production. Do not add that optimization without measuring it.

Suggested entities:

| Entity | Role |
| --- | --- |
| `note_payload` | Immutable bounded bytes, format/codec, decoded length and SHA-256 |
| `note_tree_node` | Immutable children or piece descriptors, subtree sizes, checksum/root digest |
| `note_revision` | Note ID, revision ID, parent revision, root ID, time, origin; optional computed body SHA |
| `saved_items` | Current revision ID plus existing title/folders/Trash metadata and bounded preview |
| `note_draft` | Session ID, base saved revision, working root, durable sequence, selection |
| `note_undo_group` | Before/after roots, selection, grouping metadata and redo position |
| `note_staging` / `note_pin` | Incomplete imports, migration work, active reads/exports and other protected roots/objects |

Use the **same database** for authoritative payloads, nodes, revision roots and head updates. New immutable objects and the head/history/draft changes commit in one transaction. A failing stale-head compare-and-set rolls the entire publication back. This avoids adding another cross-file/DB commit protocol. It does increase database size, backup scope and migration cost; bounded rows and paged access are required, not a single 50 MB BLOB loaded through a DAO.

Metadata-only changes reuse the body root and do not create body history. Track a metadata version or apply field-specific patches with concurrency checks, so a body Save cannot overwrite a newer title/folder change from another screen. No-op saves should allocate neither text nor a fresh content tree. Revision IDs are monotonic event identities; timestamps alone are insufficient stale-write guards.

For large imports, stage bounded batches durably, pin every staged object, then publish the completed root/head in a final short transaction. Interrupted import has no visible partial note. Abandoned staging is reclaimable only after the owner is definitively inactive. Serialize imports, GC and normal publication through the repository's writer protocol; do not let unrelated media writes hold a long import transaction.

SHA-256 of the concatenated Markdown body cannot be obtained by merely combining child SHA-256 hashes. Keep `rootDigest`/revision identity separate from `bodySha256`. A root digest authenticates the encoded tree and referenced payloads; the same text can have different tree shapes. Compute full-body SHA on import/export, verification or background demand. **Do not keep a synchronous full-document hash scan on every Save**, or storage improves while Save remains O(document bytes). If equal-content detection matters, verify it deliberately; do not mistake tree identity for canonical text identity.

Add an explicit body location/format such as `CHUNKED`, plus a new revision-reference type. Existing `contentHash` and `NoteBodyRef.hash` mean a full-body SHA and currently drive paths, stale-save checks and Undo. Do not silently place a tree digest in those fields. Audit every caller, including share/export, task edits, deletion, migration, search and recovery, when introducing the adapter.

Adjacent compatible pieces can be coalesced. Indexes, previews, parser caches and formatted output are disposable derivatives. Retained history must never depend on them. Compression is optional per immutable chunk and must retain a verified decoded length/checksum; it is not the main source of small-edit savings.

### Alternatives considered

| Approach | Small-edit storage | Main drawback | Decision |
| --- | --- | --- | --- |
| Full Markdown snapshots + compression | Often another document's worth for changed high-entropy data | Duplicates unchanged content; whole-body Save | Keep as legacy/interchange only |
| Current anchor + deltas | Good later deltas; full first anchor and current | Full scans and restore materialization; maintenance backlog | Improve as an interim compatibility path |
| Original base + chronological edit journal | Excellent payload savings | Old-version random reads/replay; checkpoints, branches and pruning become complex | Useful recovery mechanism, not sole saved representation |
| Shared pieces + complete per-version descriptor arrays | Efficient initially | Descriptor snapshots grow with fragmentation; repeated lists can grow quadratically across edits | Avoid as the final long-lived format |
| Fixed-size whole-body chunk dedup | Good overwrites | Inserting near the beginning shifts downstream boundaries if rechunking by absolute offset | Reference/split existing pieces instead |
| Content-defined chunk dedup + snapshot trees | Good whole-file imports and backups | Scans the document to discover boundaries if used on every edit | Consider for migration/backup and external replacements |
| Persistent piece tree + immutable bounded chunks | Added text plus changed tree paths | More tree/GC engineering; DB overhead | Recommended, with measured validation gates |
| CRDT | Can support concurrent distributed edits | Additional identities, tombstones, merge and compaction complexity | Defer until actual multi-device concurrent editing is required |

Restic's published design uses content-identified immutable blobs, tree references and snapshots; its ordering rules publish content before a snapshot that exposes it. That supports the general shared-content/publication pattern, **not a claim that its backup chunker should run on each keystroke**. [Restic repository design](https://restic.readthedocs.io/en/v0.18.0/design.html).

## Prototype and measurements

Reproducible host prototype: [benchmark-note-revision-storage.py](../tools/benchmark-note-revision-storage.py). Run with a **new** output directory:

```bash
python3 tools/benchmark-note-revision-storage.py --out build/notes-storage-research-another-run
```

It stores real immutable payload/node rows and revision heads in SQLite WAL with `synchronous=FULL`, a 4 KiB default host page size and 64 KiB payload chunks. It does not use compression. It uses 50,000,000 B printable pseudo-random input, start/middle/end typing, scattered overwrites, deletion, Unicode, large pastes and unrelated replacement. Every saved version and the restore are reconstructed and compared with independent body SHA-256 after reopening the database. Tree balance, node hashes, payload hashes and SQLite integrity are checked. Abrupt subprocess exits before/after commit and stale compare-and-set rollback are also exercised.

Completed run: [raw results](../build/notes-storage-research-20261109/results.json), [verification summary](../build/notes-storage-research-20261109/verification-summary.json), [run log](../build/notes-storage-research-20261109.log). Host SQLite **3.54.0**, 4,096 B pages. MB/KB below are decimal. Settled figures include the open database and its SHM file after a successful WAL truncation; their allocated block totals matched their file-length totals at these boundaries.

| Workload | Added text submitted | Settled growth above baseline | Peak growth during edits, incl. WAL/SHM | Growth after restore/checkpoint | Verified versions incl. original/restore |
| --- | ---: | ---: | ---: | ---: | ---: |
| 100 scattered one-byte edits | 100 B | **229,376 B** | 2,723,352 B | **0 B** | 102 |
| 1,000 scattered one-byte save operations | 1,000 B | **2,568,192 B** | 6,684,248 B | **0 B** | 1,002 |
| 1,000 typing groups, 125 B each, start/middle/end | 125,000 B | **1,351,680 B** | 5,467,784 B | **0 B** | 1,002 |
| 100 deletions of 50 KB, 5 MB deleted total | 0 B | **143,360 B** | 1,643,912 B | **0 B** | 102 |
| 100 Unicode insertions | 2,600 B | **163,840 B** | 1,726,312 B | **0 B** | 102 |
| 10 new 1 MB pastes | 10,000,000 B | **10,117,120 B** | 12,419,728 B | **0 B** | 12 |
| Replace whole body with unrelated 50 MB | 50,000,000 B | **50,511,872 B** | 101,385,664 B | **0 B** | 3 |
| 1,000 randomized replacements/deletes from empty | 100,138 B | **1,175,552 B** | 5,115,456 B | **0 B** | 1,002 |

**All eight cases passed, with 3,327 saved-version reconstructions verified.** Both abrupt-exit cases passed: before commit preserved the old head; after commit preserved the new head. Stale compare-and-set rolled back objects and revision insertion in both fixtures. This is evidence for the proposed storage mechanism and SQLite transaction publication, not the unimplemented UI/GC/migration behavior.

The 50 MB baseline occupied **50,667,520 B** with its open DB/SHM; after 1,000 typing groups, it occupied **52,019,200 B**. Initial import temporarily occupied **101,537,192 B** before checkpoint. The replacement case similarly shows a large transient WAL, so bounded staged ingestion remains necessary.

The hypothetical flat-descriptor counter reached 42,336,000 B across the 1,000 scattered snapshots and 34,327,992 B across typing snapshots, compared with the measured 2.57 MB and 1.35 MB persistent-tree growth. Those calculations use this prototype's 763 initial chunk pieces and 24 B descriptors. They are **not measurements of Cobalt's existing editor state**, which starts with a different piece representation; they illustrate why blindly persisting every complete descriptor list is a poor long-lived design.

The code intentionally keeps reference bytes and verification caches in host RAM. It does not demonstrate Android memory bounds, IME correctness, real power-loss survival, production migration, GC, or phone Save speed. It retains intermediate tree nodes that never became reachable from a saved root, so results include avoidable metadata overhead. Its hypothetical flat-manifest counter is a comparison calculation, not a second implemented store. SQLite commit timings exclude full reference hashing and export work; they must not be compared directly to the audit's UI Save timings. The 1,000 scattered-save case includes eight equal-body writes; the prototype still allocates metadata for them, whereas production should suppress no-op revisions. The 10-paste case reaches 60 MB and deliberately exercises storage beyond Cobalt's current 55 MB admission limit; it does not test that app policy.

Measure settled database bytes, allocated filesystem blocks, WAL/SHM, peak bytes during editing and initial import separately. A WAL can temporarily duplicate pages even though retained logical history shares data. This is different from permanently retaining another note body. The prototype imports the initial body in one transaction and temporarily uses approximately twice the settled space. The staged-batch import described above is needed to bound that peak in production.

## Reliability rules

### Saved acknowledgment and draft durability

Use one ordered writer per editor session, with monotonically increasing sequence/revision IDs. Snapshot an immutable working root at Save. Continue accepting edits while that root commits. A completion for revision N marks only N durable/saved; it must not overwrite text or mark later revision N+1 clean. A bounded writer queue may coalesce cumulative roots for draft durability, but must preserve undo groups and explicitly requested saved versions. Backpressure must retain input and expose pending state, never drop edits.

Give Save an idempotency key such as `(noteId, sessionId, localRevision)`. If commit succeeds and the process dies before the UI acknowledgment, reopening/retrying must discover that exact committed event rather than invent another save or erase a newer draft. Handle coroutine cancellation at this boundary deliberately: an interrupted acknowledgment does not mean the durable transaction rolled back.

Explicit Save succeeds only after the head/revision transaction is durably committed. Start with SQLite WAL `FULL` for authoritative notes/drafts, batch small edit transactions off the UI thread, and measure battery/latency. A short draft batching window can lose the newest **unacknowledged** edits on abrupt death; show pending/durable state accurately, flush on normal exit/background when possible, and retain the last verified frontier. Target <=250 ms draft lag under light load initially; this is a target, not a guarantee that every just-rendered keystroke survives immediate process termination. Never erase recovery data until the matching saved revision has committed.

SQLite distinguishes commit durability from database consistency: WAL `NORMAL` can lose recent commits after power loss; `FULL` adds a commit sync. Android's framework WAL commonly defaults to `NORMAL`, and OEMs may differ. Cobalt's current Room builder does not explicitly choose a sync mode. **Do not infer actual device settings from defaults.** Configure through the supported Room/framework/driver path and read back the mode on every writer connection; verify the connection-pool behavior for the chosen versions. [SQLite synchronization semantics](https://www.sqlite.org/pragma.html#pragma_synchronous), [Android SQLite configuration](https://developer.android.com/topic/performance/sqlite-performance-best-practices).

The current SQLite documentation also records a WAL reset race fixed in 3.51.3, with named 3.44.6/3.50.7 backports. Record runtime `sqlite_version()` and `sqlite_source_id()` on supported Android versions before committing to a driver. Use a verified patched engine/vendor build for concurrent WAL writers/checkpointers. This is a dependency validation item, **not evidence that Cobalt currently suffered corruption**. [SQLite WAL documentation, section 11](https://www.sqlite.org/wal.html#walreset).

### Recovery and corruption

On open, validate format versions, bounds, available roots, sequence and checksums before showing a generation. Verify bounded payloads when read, with a bounded verified-object cache; separately schedule an integrity scan. A shared corrupted chunk can affect several versions, so retain independent backups and avoid promising that history alone is a backup. Cobalt's manifest currently disables Android backup and its extraction rules exclude cloud backup/device transfer; do not assume the operating system supplies those independent copies. Define an explicit, user-controlled verified archive/restore path. If the newest draft fails validation, offer the last verified frontier and preserve the damaged material for recovery/export. Never reinterpret an unknown format as empty text.

Power-loss assurances depend on the filesystem, SQLite VFS and hardware honoring sync. A process-kill test is useful but does not establish real power-loss survival. SQLite's atomic-commit documentation explains these assumptions. [SQLite atomic commit](https://www.sqlite.org/atomiccommit.html).

Disk-full/I/O failures roll back publication, retain the old head and keep unsaved editor input available for retry/export. Imports must check space conservatively; error handling still matters because space can change after the check. Saving must not require temporary space for another complete body after a small edit. Full exports, initial imports, backup and database rebuilding do require significant temporary space; report that space in their own operations.

### Undo, redo and saved versions

Keep three concepts distinct in behavior: native editing undo groups, committed saved-version history and crash-recovery checkpoints. They can share roots but have different retention and navigation rules.

Group contiguous typing/deletion by edit type, adjacent location, selection movement and a short time window; IME composition commit, paste, format command and table operation are explicit boundaries. One format/table action must undo atomically. Store selection with the before/after roots. Test surrogate pairs, combining marks, RTL and CRLF; storage uses UTF-8 offsets while Android IME uses UTF-16 positions. A partially composed surrogate must stay in validated editor/journal state until it can be encoded under a defined policy; do not silently drop or replace it on Save.

Undo/Redo must have one owner. For state-based short fields, integrate the native field's undo behavior; do not layer a second independent stack over it. Large windowed editing requires mapping native window operations into global document groups. A new edit after Undo changes the redo navigation path; older saved revisions may remain in history according to retention. Restoring history creates a new event with a previous head, so a subsequent Undo can return to the outgoing revision.

Saved revision registration belongs in repository commit logic for **every note save path**, including short notes, task toggles and imports edited later. The observed short-note Undo bug is explained by `CobaltViewModel.saveActiveItem` recording `NoteGenerationHistory` only in the file-editor branch. That class currently keeps at most ten in-memory saved changes; durable history navigation needs a deliberately defined repository model if it is to survive restart.

### Garbage collection, Trash and retention

Root reachability must include current heads, all retained history roots, Trash, durable drafts, undo/redo roots, staging/migration roots and active reader/export/backup pins. Serialize collection with publication and pin registration. Mark from that complete root set, then remove only unmarked objects in bounded transactions. For a large resumable sweep, use a generation/write barrier and protect new objects until the next sweep. A snapshot-only scan with concurrent publication is insufficient.

Do not expire active pins solely because an export took a long time. Release on completion, or recover pins only after proving their session/owner is inactive. Persistent drafts are never ordinary cache. Refcounts can accelerate reporting but should not be the only correctness mechanism; audit/rebuild them from roots.

Pruning a revision must not delete text still referenced by another revision. Trash deletion moves the note's roots into Trash; restore moves the head back. Permanent purge drops only that note's eligible roots/pins and then runs GC. SQLite free pages can be reused without shrinking the main file. Space becomes physically smaller after appropriate checkpoint/reclamation; full `VACUUM` is an explicit maintenance operation needing temporary capacity, not a per-Save operation.

Suggested initial retention product decision: support at least 1,000 small saved edits for the target fixture; use configurable age/count/new-payload budgets for real libraries. Protected roots and current data always win. Communicate older-version removal before making a tight budget silently truncate history. Hundreds of 1 MB pastes cannot remain a few-MB history if all unique content is retained.

### Storage-screen accounting

Count each immutable object once. Recommended deterministic ownership order is live current data, history-only data, Trash-only data, then recovery-only data. Shared objects are attributed to the first applicable category; do not add their complete logical sizes again per version. Tree metadata can follow the same rule. Display shared/referenced logical version size separately from actual bytes that the version uniquely retains.

Keep DB page/index overhead and reusable free pages visible separately from payload/tree bytes; include WAL/SHM and physical allocated bytes in total-on-device reporting. Logical row byte sums will not equal filesystem usage. Existing media, settings, exports and parser caches remain in their appropriate categories. “Unlinked” means unreachable staged/orphan data after accounting for every protected root, never merely data absent from `saved_items`.

Clearing history should estimate unique reclaimable objects. It can reclaim almost nothing when the old versions share all chunks with current content. Checkpoint or page reuse may also delay a physical file-size reduction. Display that distinction honestly rather than promising the sum of every document's logical size will be freed.

## Every audit finding: proposed action and verification

| Audit finding | Action | Required evidence before calling it fixed |
| --- | --- | --- |
| Rapid Quick Note retained 104/125 injected chars; slow control passed | One synchronous text owner; draft completions update durability/error only, fenced by session+revision | Repeat exact rapid hardware test many times; software IME fast typing, composition, title edits, focus/recomposition and delayed/out-of-order storage completions; exact saved bytes |
| First edit retains full 50 MB history | Shared immutable roots/pieces | 50 MB random fixture + 100/1,000 small saves, all versions exact, physical growth and peak measured |
| Restore adds ~50 MB | Publish existing root; read it directly | Restore old/new repeatedly, Undo/Redo, restart; no body-sized materialization or retained growth |
| Short saved Undo disabled | Common repository revision registration | Short/long notes share save/Undo/Redo behavior, no-op and rename policy, restart behavior defined |
| Large Save latency | Commit changed paths; avoid synchronous body hash/diff; save snapshot without freezing later typing | Phone and quiet-emulator p50/p95, tracing, bytes written, rapid typing while Save pending, revision consistency |
| Reader starts at Paragraph 1057 in isolated test | Investigate lazy-list state, placeholder sizing/anchoring and indexing startup separately | New note begins at byte zero, restored positions scoped to note/root, paragraphs 1/middle/end exact; with/without header, font changes, replacement root and repeated navigation |
| Selection-menu flake | Diagnose input/focus/menu lifecycle | Repeated API 37 and supported-device tests; count flakes; preserve failures |

Quick Note's suspected race is source-supported but not fully isolated: `GlobalExpandingFab` owns local `TextFieldValue`, then `LaunchedEffect(captureContent)` replaces it from external state and moves selection to the end. `updateQuickDraft` also writes a captured string after a debounce and replaces its state following IO. Cancellation alone does not establish ordering for every file write, clear, completion or session change. Introduce revision-fenced completions and a serialized draft writer, including clear/save operations. Add a deterministic delayed-write reproduction before asserting the exact cause.

Android recommends state-based `TextFieldState` and synchronous input transformations, avoiding async feedback into the editing state. Use that for the bounded Quick Note field, hydrate it once per draft/session and use explicit replace/restore commands. Do not put a 50 MB document into a Compose field or Android saved-state Bundle. Cobalt uses Compose BOM 2024.09.00 and an explicit newer Material3 alpha; verify resolved foundation/material versions and overload compatibility before migrating. A local synchronous `TextFieldValue` with one-way persistence is a valid immediate path if a coordinated dependency upgrade is unsuitable. [Android state-based text migration](https://developer.android.com/develop/ui/compose/text/migrate-state-based).

The reader issue is **not explained by history storage**. `LazyMarkdownNote` receives/defaults a remembered list state, ignores nonpositive jump requests, indexes additional blocks in a startup loop, and appends a viewport-sized loading row. A concrete hypothesis is that the initially visible `note_loading` row keeps its stable key while real blocks are inserted before it. AndroidX explicitly maintains the first visible keyed item when preceding items are inserted; this could move the viewport down as indexing progresses. Prepare the first content batch before exposing the scrollable list, or give initial-load/append-load states deliberately different behavior, then test this hypothesis. It is not yet a proven diagnosis of the failed fixture. [AndroidX LazyListScope key contract](https://github.com/androidx/androidx/blob/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/lazy/LazyDsl.kt).

Instrument first-visible item/offset, first-visible key and scroll requests during first layout and each indexing batch. Use note/root-scoped keys; preserve a byte anchor deliberately when restoring position or changing layout. Do not force scroll-to-zero on every recomposition.

Reader, exporter and search should open a pinned root and page bounded ranges. Reuse immutable payload indexes for UTF-8/UTF-16 translation; count line boundaries carefully across chunks. A single huge line, giant table, deeply nested Markdown or many links must not turn into an unbounded layout/parser allocation. Existing limits and streaming exports are worth preserving. Current global FTS4 indexes only a 32 KiB body prefix; a storage rewrite does not automatically provide full-note search. Either label that scope or add a separately budgeted background index with a root watermark and stale-result checks. Avoid full-note tokenization during Save.

## Migration and implementation order

1. **Fix input ownership and common short-note history registration first.** Add deterministic race regressions and preserve all existing recovery behavior. Investigate the reader test independently. These can ship without a storage-format migration.
2. **Improve legacy behavior while the replacement is developed.** Schedule compaction after restore; expose pending compaction. Consider representing the first archived version against the current body only with a safe anchor dependency protocol. Do not make a current anchor reclaimable while older deltas depend on it, and do not introduce patch cycles. These changes reduce symptoms but still retain whole-body Save work; measure before selecting the interim change.
3. **Build a Kotlin storage backend behind the existing range/stream interface.** Schema version, checksummed objects, persistent tree, expected-head commits, draft roots, leases and repository history first. Preserve metadata-only saves and streaming exports. Prove randomized range reads and replacements against a plain reference model before routing the UI through it.
4. **Migrate one note at a time using dual readers and an explicit format marker.** Keep old `.md`, delta dependencies and journals until the new root is committed and verified. Decode legacy generations as streams in dependency order. Use known anchors/copy ranges and content-defined chunking for legacy ingestion to avoid storing each reconstructed generation as unrelated chunks. External replacements may need whole-body scanning; normal edits do not.
5. **Validate every retained migrated version against its existing full-body SHA.** Maintain an old-generation→new-revision map, including Undo/Redo, Trash and recovery references. A corrupted/missing legacy version must be reported and retained for investigation; never silently “migrate” it to empty. Check available temporary capacity, cancel/resume by staging ID, and allow reads from legacy when migration cannot complete.
6. **Enable new writes by measured cohorts after integrity gates pass.** Once a migrated note accepts new edits, an old APK cannot understand its new roots. Rollback needs the compatible reader or an explicit verified Markdown/history export; simply flipping a feature flag is insufficient. Keep old files read-only during a validation window, without maintaining full duplicate files on every new Save.
7. **Reclaim legacy files only after all heads/history/drafts/pins moved.** Validate storage-screen accounting and backup/restore for both formats. Use a consistent database backup/export API or a coordinated quiescent snapshot; never copy only the main SQLite file while committed data exists in WAL. Backups must include payloads and all roots, not just note metadata. [SQLite WAL file lifecycle](https://www.sqlite.org/wal.html), [SQLite online backup API](https://www.sqlite.org/backup.html).

## Acceptance gates for a production implementation

These are proposed targets, not results of the current app audit or host prototype:

- **Storage:** 50 MB pseudo-random base + 100 single-character saved edits adds <1 MB settled payload/tree/revision overhead; 1,000 small edits or 125 B typing groups adds <5 MB initially. Include database overhead and report WAL/allocated peaks separately. Restore adds no document-sized payload. Test no-op edits, rename, scattered and adjacent typing, deletion, large paste and full replacement.
- **Input:** zero byte/character loss across repeated rapid hardware and software-IME trials; delayed draft saves cannot change live text/selection/composition; Save N followed by typing N+1 preserves both revisions correctly.
- **Integrity:** every generation's exported UTF-8 body matches an independent oracle; root/node/payload corruption is detected and does not cause empty-save overwrite. Random sequences include restore, undo, redo, stale writers, deletion and purge.
- **Crash:** interrupt before object insertion, after nodes, before/after head commit, during checkpoint, draft flush, migration and GC. Old or new complete head only; no acknowledged edit loss under the tested durability model. Distinguish process kill, filesystem fault injection and real device power loss.
- **Pressure:** disk-full at each stage, low-memory devices, large paste rejection, cancellation, 10,000+ fragmented operations, many tiny notes and multiple active readers. All failures keep a recoverable head/draft and leave GC safe.
- **Performance:** initial goal p95 <250 ms for small changed-root Save on a representative midrange phone, with no storage IO on the main thread. Trace draft/Save queue lag and frame time. Measure 50 MB start/middle/end reads, export throughput and long sessions. Set the final targets from device results.
- **Reader:** the currently failing Paragraph 1 test passes repeatedly without weakening its assertion; distant byte jumps, headers, font changes, stale roots, selection and task toggles work. Count and investigate the selection-menu flake.
- **Maintenance:** GC cannot collect any protected content; history clear/Trash purge reports unique reclaimable bytes; measured file totals reconcile with Storage; checkpoint/VACUUM/backup interruptions preserve notes. Version migration covers current, history, Trash and recovery together.

The current audit's strong passing areas—streamed Unicode export, all eleven large-version reconstructions, atomic write fault tests, stale-edit protection, recovery, protected cache and migrations—remain regression requirements. The replacement is justified by storage and Save behavior, not by a claim that the present checks proved every reliability property.
