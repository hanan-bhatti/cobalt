# Version comparison: published v2.2.2 to current development build

The published v2.2.2 APK was installed and used on Android 17. A recreated, 500-item legacy library is used for the in-place update test described below. The current source comparison ends at `9f50ce3` (224 commits after `v2.2.2`); the changelog/metadata records below accompany that review. Version remains **2.2.3 / code 19**. No version bump, survey revision, merge, tag or push is part of this review.

The [baseline report](v2.2.2-emulator-review-2026-11-09.md) documents actual screens, note integrity, telemetry and failures. The [commit ledger](version-comparison-commit-ledger-2026-11-09.md) indexes all 232 commit messages and changed-file counts through `1b9b616`, including historical test/documentation commits. Reading a commit or an older validation report is not a new device-test pass.

The subsequent eight survey/polish commits are verified separately in the [10 November report](app-polish-verification-2026-11-10.md): 77 targeted tests passed, the signed in-place update retained all 500 items, and the email handoff/delivery limits are documented there. The comparison below preserves the initial review’s verification boundaries.

## Final implemented changes

| Area | Published v2.2.2 baseline | Current development source | Verification boundary |
| --- | --- | --- | --- |
| Distribution | Separate Foss/Play builds; tested Foss already worked as a local guest app | One local Cobalt app; retired Play/cloud/account/backend code removed; debug data isolated | Foss baseline observed. Removal also checked against source/build configuration. This is not a claim that Foss newly stopped requiring sign-in. |
| Home | Large tutorial card, type/folder chips, recent captures and navigation | Refined empty guidance, persistent archive filters, less card invalidation, revised scrollbar and actual media-size sorting | Baseline empty/populated/search/folder routes exercised. Performance acceptance requires controlled measurement. |
| Text storage | Inline note text is carried in saved-item data | Resumable conversion, file adapters and schema-15 shared immutable text chunks/range trees; body-free list metadata | Final architecture is shared storage, not the superseded early “every note is one Markdown file” design. |
| Save/history | Inline content/save flow | Atomic content-head/metadata/search/navigation publication; durable saved Undo/Redo; unchanged-text sharing; compatible delta/file history | Current storage reports contain historical host/device checks, linked below. Full-body SHA still makes Save proportional to note size. |
| Editor | Whole BasicTextField source; toolbar and preview | Continuous native/disk-backed input window; recoverable operation journals; global selection/Undo; bounded source styling and cancellable paste | A source change, not proof that every IME, size or performance budget passes. |
| Reader | Small Markdown rendered; 400k repeated plain note eventually opened with high memory/delayed hierarchy | Incremental bounded blocks/caches/prefetch; table/fence continuation; ATX/rules/code and local typography | Baseline exact copies and the stale-reader reproduction are recorded separately. |
| Edit refresh | Open reader copied old content immediately after Save; reopening exposed saved edit | Detail resolves current item metadata/hash instead of retaining a stale active item | Intended fix: `f986c9d` and later reader/storage reconciliation. Current runtime verification is recorded below when exercised. |
| Markdown tools | Existing heading/list/task/table rendering; basic formatting | Table row/column/alignment transactions, next-cell actions, list continuation, literal Find/Replace and heading navigation | Global archive search is distinct from in-note Find. Bounded search derivatives do not prove full-body or semantic search. |
| Recovery | No Storage/Data Migration category in tested preference index | Migration/recovery gates, recoverable drafts, direct failed-save export and protected cleanup | Actual upgrade/recovery behavior is recorded separately; no destructive reset is intended. |
| Storage UI | Five preference categories, no storage hub in tested baseline | Usage breakdown plus copies, Trash, unlinked files, recovery and cache/database pages; skeletons and refresh | Cleanup separates current, recovery, history, cache and physical overhead. Logical version size is not reclaimed bytes. |
| Trash | Delete/Undo flow existed | Thirty-day recoverable Trash, reviewed restore/permanent deletion, retained identities and active-reference protection | “Delete in edit mode” is already in v2.2.2 and must not be presented as newly added. |
| Links/capture | Share content opens Capture; note clipboard shortcut trims whitespace | Durable preview queue, manual-description protection, explicit Refresh races, reviewed text imports/capture and safe title resolution | Issue #21 path-title protection is implemented; no invented social metadata. |
| Duplication | Repeated-title share attempts were retained during testing, without an established deduplication claim | Folder/URL/media-identity checks and applicable Trash recovery notices | Source coverage exists; unrelated records with equal titles are not automatically duplicates. |
| Folder organization | Custom folder creation/assignment and category grid worked with hardware input | Local ranking, suggestions, organization settings and activity heatmap | Soft-keyboard #24 reproduction was not established by the hardware-keyboard baseline. |
| Media discovery | TMDB key page; movie/TV/anime search; `Naruto` returned no results without configured TMDB | Media & Providers, source enable/order, TMDB/Trakt/Jikan identities, custom Jikan v4 and manga facts | Baseline missing-key behavior is observed; API/network failure cause was not isolated. |
| Media controls | Watch-status filter and collection UI | Half-star local rating, watch/read status, bookmark, season/episode pager, progress/next-up and source badges | Live TMDB/Trakt tests require configured credentials; no successful account round trip is claimed here. |
| Trakt | No Trakt configuration in baseline settings | Optional PKCE/App Links, two-way outbox sync, pending changes, silent/background-only notifications and session-safe revoke/disconnect | Website/OAuth callback is not a note-editing web companion. |
| Sharing | Reader Share action exists | Styled Link/Image/Movie/Note cards, Document/Minimal/Compact styles, bounded copy, Markdown/image exports and floating hero sheets | A share preview is not proof of exported image/file integrity. |
| Fonts | Appearance/blur/haptics controls | Local licensed FOSS font families, live previews and per-role typography | No cloud font request is required. |
| Reminders/Gemini usage | Gemini key/model/prompt configuration | Adaptive local resurfacing/custom time, token/day tracking and usage graphs; actual configured model labels | No AI success is asserted with absent keys. |
| Side Dock | Overlay gate, geometry/motion pages; service ran as specialUse on API 37 | Compact paste/send input, bounded Markdown preview and floating action pill; additional service/theme fixes | API 37 success does not test the old DATA_SYNC mismatch on API 29–33. |
| Feedback/survey | Forms and survey visited; favorites copy advertises unverified biometrics; 10/10 preselected | Consecutive-report reset, frustration question, reaction/favorite choice without defaults; refreshed reporter/header/icons | Survey still needs the user's separate feature/copy task; recommendation score remains preselected. No synthetic report was published. |
| Updates/licenses | Local version-history/legal/about pages | Dynamic GitHub release/changelog caching/refresh and expanded open-source library directory | Remote release publication is separate from the local build's version number. |
| Credential/privacy controls | Fresh baseline keys absent; usable key extraction failed before uninstall | Keystore-backed API/OAuth secrets, redacted logs and narrowed share paths; Android backup/transfer disabled | Secret encryption does not encrypt note bodies or make an uninstall backup portable. |

## User-reported requests and bugs

Issue state is what GitHub returned during this review; a closed issue is not itself evidence that a fix passed.

| Issue / request | Actual position |
| --- | --- |
| [#20 improved editing](https://github.com/hanan-bhatti/cobalt/issues/20) | Closed. Baseline already has Delete item in editing. Link-description editing/refresh protections are in current source; do not invent a new delete feature. |
| [#21 cache path in image title](https://github.com/hanan-bhatti/cobalt/issues/21) | Closed. Central title resolution, save/display guards and legacy cleanup are implemented across capture/cards/widgets/notifications. No new image-attachment UI regression test is credited here. |
| [#22 stale note titles](https://github.com/hanan-bhatti/cobalt/issues/22) | Closed. A related stale-content reader behavior was reproduced in the baseline. Current detail-state changes target this family of behavior. |
| [#23 consecutive reports](https://github.com/hanan-bhatti/cobalt/issues/23) | Closed. Report Another and input reset are implemented. No actual GitHub/email submission was sent during this review. |
| [#24 in-note folder keyboard](https://github.com/hanan-bhatti/cobalt/issues/24) | Closed. Current source keeps inline folder creation mounted when keyboard opens. Baseline folder creation worked with hardware input; that does not invalidate the original soft-keyboard report. |
| [#25 galleries inside a note](https://github.com/hanan-bhatti/cobalt/issues/25) | Open. Camera/gallery capture of an item is not an ordered multi-image gallery inside a note. This remains future work. |
| [#26 local Jikan](https://github.com/hanan-bhatti/cobalt/issues/26) | Open. Custom Jikan v4 endpoints are implemented in current source. Do not continue listing this as wholly unimplemented; live/server-fixture evidence must be identified separately. |
| Web companion | Public product/legal pages and Trakt callback exist. Cross-device note editing/sync companion is not implemented. |
| Backup/restore | Per-note export, saved-version restoration and Trash exist. Complete user-facing app backup/restore is not implemented; Android automatic backup is disabled. |
| Biometric lock / encrypted notes | Not implemented. Keystore-backed credential protection is a different feature. The survey needs correction in the separate requested task. |

## Historical validation that is relevant, but not rerun evidence

- [Shared storage implementation](notes-storage-implementation-validation-2026-11-09.md): documented schema-15 storage/recovery/cleanup tests and a 50 MB/100-edit Android fixture. That report identifies its own APKs and reports a loaded-environment save median of 7,144 ms and maximum of 65,046 ms. Do not substitute prototype/tree-only timing or call this instant saving.
- [Media/provider validation](media-validation-2026-11-08.md): earlier media/Trakt/Jikan runtime and host coverage, with its own limits and build attribution.
- [Link metadata validation](link-metadata-validation-2026-11-09.md): earlier extraction/preview checks.
- [Manual acceptance checklist](MANUAL_TEST_CHECKLIST.md): unchecked items are work to perform, not passed checks.

No physical-phone, API-24–37 matrix, TalkBack, 55 MB editor budget, disk corruption, hardware power loss, full library backup or successful live-account authentication is inferred from the baseline review.

## Release documentation synchronization

The root and bundled Markdown changelogs describe the final source rather than the superseded intermediate editor/share/storage designs. Fastlane/F-Droid code-19 notes fit the 500-character limit; the full store description distinguishes optional network integrations and future requests. The in-app release-summary highlights are synchronized and dated “Unreleased.” Existing version name/code are unchanged. The F-Droid build recipe still expects the future `v2.2.3` tag; no tag/release or merge/push was created by this review.

## Current build runtime comparison

The [retained-data migration report](migration-v2.2.2-to-current-2026-11-09.md)
and its [computed evidence](evidence/version-comparison-2026-11-09/migration-summary.json)
record the actual in-place upgrade on the running emulator. The startup prompt
appeared. Interruption/resume worked. Schema 6 upgraded to 15 with all **500
records**, **320 exact note bodies**, **70 exact local media files**, both folders,
17 archive flags, every captured legacy field and the supplied TMDB key retained.
There were zero missing IDs, extra IDs, body/file errors or metadata differences.
The completed migration verified 320 notes and reported zero failures. The key
is now Keystore-encrypted. No reinstall/data clear occurred during this upgrade.

The report explains why Not now closes the app, why a resumed run displays fewer
new conversions than total verified notes, and why the UI's “private Markdown
files” wording is inaccurate for the final shared-chunk implementation. It also
records a Binder overflow caused by the private inspection helper, its correction,
and the resulting crash-report dialog; normal migration was not blamed for that
helper failure.

The current Settings dashboard independently displays all 500 records with the
expected type counts; Home displays 483 because 17 remain archived. Archive &
Organization, Adaptive Reminders, Intelligence, Dock, Widgets, Storage and its
Manage notes/Draft recovery/Trash/Version history/Cache & database/Unlinked storage
pages were visited. Those visits validate navigation and available controls;
account authentication, actual reminder delivery, OCR/transcription and destructive
cleanup are not inferred from opening a page.


## Current UI checks and observed behavior

| Surface | Observation in the migrated build | Limit |
| --- | --- | --- |
| Populated Home / Settings | Home 483; Settings 500 and exact per-type counts; Home adds Filter and Sort and distinct MOVIE/TV SHOW badges | No phone FPS claim |
| Migration prompt / ready / running / resume / complete | Prompt captured; decline closes to launcher; interruption/resume verified; all 320 notes verified | Controlled force-stop, not power loss |
| Markdown / large reader | Mixed tasks/table/code/Urdu/emoji rendered; 399,995-byte reader displayed its START marker; all 320 full body hashes checked independently | Initial 100k hierarchy was captured before body display; no full-render latency inferred |
| Editing / Save refresh | Inserted a marker into the small note; Update Archive immediately rendered the changed content | Small-note edit, not a 55 MB editor acceptance test |
| Saved Undo / Redo | Undo restored original rendering; Redo restored marker; after process restart saved Undo still worked and restored the original fixture | Final body hash recheck recorded below |
| Note actions | Find, Outline, Markdown Mode, saved Undo/Redo, Markdown export, Share and Delete controls inspected | Export/search controls alone are not export integrity or exhaustive Find tests |
| Note share | Document preview rendered the complete small fixture; Minimal/Compact previews were opened | No image export delivery or font-family matrix certified |
| Appearance / fonts | Light/dynamic-color/blur/recents controls; live typography preview with Urdu, Inter/JetBrains Mono, presets and metric controls | Controls visited; every setting combination not tested |
| Organization / reminders | Ranking, suggestions and delivery controls inspected | Actual reminder delivery not tested |
| Intelligence / Dock / widgets | Missing Gemini key, overlay permission gate and widget setup states inspected | No AI request, OCR, current Dock capture or launcher widget pass |
| Manage notes / recovery | 320 saved notes; Large filter finds one; no drafts | Recovery of a deliberately failed Save not tested |
| Trash / history | 30-day retention controls; empty Trash and initial history 0 B | Restore/permanent-delete/cleanup not performed |
| Cache / database / unlinked files | Usage/optimization pages opened; unlinked storage 0 B | No destructive cleanup pass inferred |
| Crash report | Inspector's Binder overflow surfaced a report; Dismiss returned to the retained library | No email/report sent; helper failure explained in migration report |

Current memory snapshots were PSS 86,066 KiB at the early 100k checkpoint,
86,549 KiB in the 400k reader and 72,919 KiB in the mixed reader. The recreated
baseline Home snapshot was 59,602 KiB. These are different screens and loaded
emulator samples, not a controlled before/after speed or peak-RAM comparison.
The current 400k activity launch reported TotalTime 1,417 ms / WaitTime 1,531 ms;
those activity timings do not measure complete note rendering.

The original small fixture was restored after the edit/history tests. Migration
success does not establish live Gemini/Trakt account flows, every Android version,
physical-phone IMEs, accessibility, disk-full recovery or complete backup/restore.


The final [retention recheck](evidence/version-comparison-2026-11-09/final-retention-summary.json)
again passed all 500 IDs, 320 restored original bodies and 70 media files after
editing/history checks and the bundled-changelog packaging update. Executable
DEX bytes were unchanged by that update. Breaking Bad detail exposed local
rating/watchlist/progress and source-rating controls and refreshed cached media
metadata; those changes are listed in the final evidence. No successful current
Arrival search is credited: the attempted input encountered a different active
screen. The independently verified retained TMDB credential and successful
baseline Matrix search are the API evidence, with that boundary stated explicitly.
