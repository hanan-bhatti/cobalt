# Version comparison: published v2.2.2 to current development build

The published v2.2.2 APK was installed and used on Android 17 before upgrading. The current source comparison ends at `6669da6` (219 commits after `v2.2.2`); the requested changelog/metadata edits are additional uncommitted changes. Version remains **2.2.3 / code 19**. No version bump, survey revision, merge, tag or push is part of this review.

The [baseline report](v2.2.2-emulator-review-2026-11-09.md) documents actual screens, note integrity, telemetry and failures. The [commit ledger](version-comparison-commit-ledger-2026-11-09.md) indexes all 219 commit messages and changed-file counts, including historical test/documentation commits. Reading a commit or an older validation report is not a new device-test pass.

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
| Trakt | No Trakt configuration in baseline settings | Optional PKCE/App Links, two-way outbox sync, pending changes and session-safe revoke/disconnect | Website/OAuth callback is not a note-editing web companion. |
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

Pending completion of the installation/navigation checks in this session. Only measured results are appended here. The implementation, version and survey are not being revised as part of those checks.
