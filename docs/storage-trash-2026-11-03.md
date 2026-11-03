# Storage and Trash implementation — 2026-11-03

## Behaviour

Storage uses the existing native settings headers, connected cards, dynamic
Material palette and navigation transitions. The usage bar describes Cobalt app
data, not the phone's free space. A compact legend gives labelled sizes alongside
colours. Focused pages expose Trash, saved versions, retained unlinked files, and
cache/database maintenance. Titles are single-line/ellipsized; sizes use the shared
rounded formatter. Storage uses the supplied SVG; actions reuse existing delete,
undo, export and menu assets. Ordinary note recovery is linked only from Storage.

Trash defaults to 30 days. Disabling it keeps a 30-second Undo window for future
deletions and does not change existing deadlines. Restore updates metadata only,
never writes a projected preview into a body. Explicit permanent deletion persists
its intent first, removes owned versions/drafts/media, then deletes the metadata.
An interrupted purge retries and cannot offer a half-deleted item for restoration.
A persistent identity fence rejects late editor/autosave writes after purge.
Shared media is kept until the last owner is deleted. Cache-only original media
is verified into durable private storage before an item enters Trash.

Room v9→v10 adds the Trash table without changing body data. Existing soft deletes
receive a fresh 30-day window. Earlier Markdown migration remains separately
versioned; this upgrade does not repeat its prompt.

Unlinked files can be reviewed, exported, recovered or explicitly removed. Completed
unowned media is eligible after a short grace period; temporary media requires
24 hours. Active drafts, readers and required delta anchors stay protected. Bulk
cleanup rechecks the exact reviewed identities and never includes a new file just
because it appeared after confirmation. Cache cleanup targets rebuildable image
cache, registered idle workspace and export copies older than 24 hours. It never
blanket-deletes cacheDir, recordings, current notes or edit recovery journals.
Database compaction is explicit and checks temporary free space first.

## Research and trade-offs

The 30-day Trash window follows a familiar [Files by Google retention model](https://support.google.com/files/answer/10607740?hl=en-AU).
Android distinguishes [temporary cache from app-private durable files](https://developer.android.com/training/data-storage/app-specific);
cache is unsuitable as the only retained original. [SQLite VACUUM](https://www.sqlite.org/lang_vacuum.html)
can reclaim free pages but needs temporary disk space, so it is an explicit action.
These are design references only: no Google integration or SDK was added.

Expiry uses WorkManager; Android can defer it. Empty Trash is the explicit immediate
cleanup action. Deletion fences retain small identity tombstones so stale async
saves cannot silently resurrect removed data. Physical deletion is ordinary file
unlinking, not a promise of forensic secure erasure. Unknown unfinished files remain
protected rather than guessing that they are safe to remove.

## Validation

The targeted Robolectric/JUnit run passed **114 tests in 32 classes**, with zero
failures, errors or skipped tests. It includes 9 Trash lifecycle/failure tests,
3 unlinked-file recovery/cleanup tests, v9→v10 body-preservation/retention coverage,
Trash policy boundaries, active-cache protection and the existing editor, delta,
policy, store and rendering regressions. The added orphan-media test verifies
recovery of audio stored with an MP4 extension and complete removal after purge.

Both `assembleDebug` and `assembleDebugAndroidTest` completed. Instrumented test
compilation/package creation is not instrumented test execution. ADB reported no
connected devices. No install, app-data reset, screenshot review or stress test
was performed in this run. No device performance numbers or visual-verification
results are implied by compilation/host tests. No new
dependency, permission, network call, application ID or signing change was added.

## Remaining editor and performance work

Saved-version compression uses deltas; the live Sora editor model is still resident
in RAM. The disk-backed recovery piece table is not a complete disk-backed live
editor. Full 55 MB editing, 1 GB libraries with large Undo histories, frame timing,
peak memory and CPU budgets have not been validated. No claim of production
readiness or constant sub-50 ms loading is made by this Storage change.


## Debug artifact

- Package: `com.hanan_bhatti.cobalt.debug` (confirmed with apkanalyzer).
- Path: `app/build/outputs/apk/debug/app-debug.apk`.
- Size: 33,591,331 bytes.
- SHA-256: `d5702c6100d088553b0b99ac9bdd79e002539122fa956bf699ada46676bbd011`.
- This APK contains the current working tree, including the user's concurrent
  uncommitted work. Task commits deliberately exclude those unrelated edits.

## Review commits and working tree

- `94654a1` — protect visible editor styling from concurrent deletion/disposal.
- `f81b5fe` — Room Trash metadata, resumable purge, late-save fences and safe maintenance.
- `7d2c1cf` — Storage usage bar, focused pages and supplied/reused icons.
- `21d91ee` — intermediate history compaction and stale-request regressions.
- `66cab6f` — explain narrowly suppressed durability/free-space lint suggestions.

Work continues on `cleanup/cobalt-single-app`. No reset, stash or wholesale staging
was performed in this continuation. Existing concurrent Trakt/UI/capture/renderer
changes remain in the working tree. Repository/ViewModel task commits were staged
from reviewed HEAD-derived copies to exclude unrelated concurrent changes.

## Check details and remaining risks

The complete targeted test + debug APK + Android-test APK + lint command completed
successfully in **11m 5s** with one worker, 1,536 MiB Java heap and 384 MiB metaspace.
That pass reported no lint errors, 194 warnings and 11 hints. Six storage lint
suggestions were then documented narrowly: checked synchronous preference commits
are intentional, and database compaction requires physically free space rather
than assuming recovery cache can be evicted. Repackaging/lint after those annotation
changes is recorded below when complete. Unit-test behavior is unchanged.

The v9→v10 migration test is a host SQLite/Room test; Android upgrade fixtures now
include the new migration, but their APK has only been built, not executed. Device
filesystem failures, interrupted process restoration, predictive-back visuals,
large-library latency and release/R8 verification remain pending. The debug app
and test APKs are not a release-readiness certification.


Final annotation-only repackaging and lint passed in **8m 45s**. Final lint reports
**0 errors, 188 warnings and 11 hints**; no lint findings remain in the new/changed
Storage/Trash implementation files. Existing project warnings remain. No warning
baseline was generated or expanded. The 114 behavior regressions passed before
these annotation/comment-only changes; they were not needlessly rerun afterward.

Final gate for this scope: code, debug packaging and host regressions complete;
device visual/lifecycle/storage-reclamation verification and release/performance
acceptance remain open. No device data was modified.
