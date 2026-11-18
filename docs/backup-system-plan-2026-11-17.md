# Cobalt backup system — working plan

Date: 2026-11-17. Status: discussion draft; implementation has not started.

## Agreed direction and open decisions

The user wants automatic, incremental, reliable backups of user-selected content and preferences, with both device and cloud destinations. Recent changes should have a recovery copy even if the next scheduled full backup never runs. The interface should show one continuing backup, rather than a succession of large daily archives.

Agreed encryption recovery: a backup password plus a separate recovery key. The phone may remember access securely so unattended backups work.

Proposed decisions, still open:

- Start with a user-selected device folder and Nextcloud/WebDAV; evaluate optional Google Drive next.
- Present one repository per destination, retaining the current verified checkpoint and at least one previous verified checkpoint internally. These share unchanged content; they are not two full copies.
- Allow a device destination, a cloud destination, or both. Track protection separately for each.
- Keep recent-change recovery enabled whenever backup is enabled. It obeys the same content exclusions, with its own battery/network budget.
- Define retention for previous revisions, Trash, recovery drafts, and excluded content before implementation.
- Choose a proven backup engine or a narrowly specified application format after a compatibility and performance spike. Do not invent cryptography or claim a security audit has occurred.

## 1. Product behavior

There are three protection states, each with a distinct meaning:

| State | Meaning |
| --- | --- |
| Saved on this phone | The app's normal durable save succeeded. This alone is not an independent backup. |
| Recent recovery copy available | A complete, authenticated recovery record and its required content reached the named destination. Other library items may still be pending. |
| Verified backup available | A complete checkpoint of the selected scope has been published after its referenced content passed verification. |

An incomplete library backup may contain usable individual recovery records. A torn file, failed hash, or failed authentication is never accepted as a usable note. The partial state is about coverage, not permission to accept damaged content.

Example: the scheduled cloud backup finishes at 10:00. A user creates a note at 14:00. Its saved revision is recorded as pending, then copied as a small encrypted recovery record while the app can run and the cloud is reachable. At 14:01 the UI may show “Cloud recovery copy available; full backup last completed at 10:00.” If tomorrow's scheduled run never happens, the recovery record can still be restored. If the phone was offline, it must instead show that the change has not reached cloud storage.

Cloud copies cannot be guaranteed before connectivity and execution are available. An app-private pending queue does not survive uninstall or phone loss. A folder on the same phone can survive app uninstall when stored outside app-private storage, but does not protect against losing that phone. Setup and status must communicate these distinctions directly.

## 2. Storage choices and the OSS question

There is no representative Cobalt user survey establishing cloud-provider preference. Supported integrations in other FOSS projects are evidence of accepted design patterns, not a measurement of what most users use.

| Destination | Fit | Setup and limitations |
| --- | --- | --- |
| Device folder / removable storage | Baseline available without a cloud account or Google services | Choose a dedicated folder. A removed USB device, revoked permission, or unavailable document provider pauses that destination. Same-phone storage is not off-device protection. |
| Nextcloud | Strong fit for users seeking a FOSS service and control over hosting | Hosted accounts are available; self-hosting is optional. User supplies server address, then browser login using Nextcloud Login Flow v2. Storage capacity and reliability depend on the provider. |
| Generic WebDAV | Supports additional hosted services and personal servers through an open protocol | Usually requires a URL and credentials/app password. Server behavior and conditional writes must be tested. An open protocol does not imply every service using it is open source. |
| Google Drive | Convenient for users already using Google; optional support can broaden access | Proprietary service. Supported Android authorization, OAuth configuration, dependency licenses, and de-Googled/F-Droid behavior need a feasibility check. Do not assume desktop OAuth examples work on Android. |
| S3-compatible storage | Useful for advanced users and some hosted services | Endpoint/bucket/key setup is less approachable. Consider after the first providers are stable. |

Recommendation: keep the backup engine independent of storage providers, with no Cobalt account or Cobalt-hosted data service required. First implement local storage and Nextcloud/WebDAV; leave a clear path for optional Drive. Do not present Google as mandatory or assert that OSS users universally reject it.

Nextcloud has a hosted-provider signup directory. We can offer a neutral “Need a Nextcloud account?” link without promising third-party availability, capacity, or privacy terms.

Joplin demonstrates separate drivers for local filesystem, Nextcloud, WebDAV and other services. rclone explicitly supports Google Drive and WebDAV. Neo Backup recommends combining encrypted backups with tools such as Nextcloud or Syncthing. These support offering choice.

F-Droid's distinction matters: proprietary libraries bundled into an app and dependence on a proprietary network service are separate concerns. Optional Drive does not automatically guarantee the same F-Droid metadata or eligibility as a service-independent build; inspect the final dependencies and distribution policy before choosing the integration.

Android's file picker is useful for local/document-provider access, but a cloud provider exposed through that picker must not automatically be advertised as a reliable unattended cloud backend. Probe provider capabilities and test its durability and background behavior first.

## 3. Incremental repository rather than overwriting one archive

The first backup must transfer all selected data. Later runs transfer new or changed content, metadata and required index updates. A large new video is still a large transfer; incremental backup cannot eliminate that cost.

Use the established immutable-object and checkpoint pattern:

1. Divide large payloads into bounded reusable pieces; choose chunking based on measured edit and attachment workloads.
2. Compress suitable data before encryption; avoid wasting work recompressing already compressed media.
3. Reuse objects that already exist and have been verified in the same repository.
4. Write new objects without modifying the old verified objects.
5. Verify newly written content and completeness of the candidate checkpoint.
6. Publish an authenticated checkpoint only when everything it references is present.
7. Retire obsolete data later, after the new checkpoint and retention policy allow it.

“One backup” should mean one managed repository in the interface, not a single file continuously overwritten in place. A safe update necessarily needs some temporary space, small checkpoint metadata and, when content changes, storage for the old and new changed pieces until publication succeeds.

If the current library is 2 GB and one short note changes, a subsequent transfer can be small. The exact savings depend on chunking, metadata and encryption overhead; do not promise a fixed size before measurement.

Keep at least the previous verified checkpoint during every update. Longer revision history is optional. Strict latest-only retention loses protection against delayed corruption or accidental edits/deletions, so it needs an explicit product decision. Deduplication reduces duplication but does not make revision retention free.

Restic documents a mature example of this general design. That is a reference for architecture, not a claim that Cobalt will be restic-compatible or should reimplement restic's cryptographic format.

## 4. Closing the scheduled-backup gap

### Durable change tracking

Every relevant committed mutation must be represented in a durable backup journal: note saves, imports, metadata changes, moves, folder changes, attachments, archived state, deletions and selected preferences. This includes background changes and web-companion edits if that feature is enabled.

For database content, save the revision and its backup change record within the same database transaction. Protect immutable note revisions required by pending work from storage collection. A record containing only an item ID pointing to mutable/deletable current content is insufficient.

Use sequence numbers and stable revision identities, not a timestamp-only scan. A backup captures a boundary: changes after that boundary remain pending even when the current run completes. Completion acknowledgement is per revision, destination and selection-policy version.

Preferences currently live outside Room. Explicitly solve that boundary: record typed preference revisions in a durable export catalog, make writes recoverable, and reconcile on startup. Do not claim one transaction spans SQLite and SharedPreferences. Relevant writers and preference listeners must be audited before choosing the implementation.

### Early recovery copying

After a save, request a short resumable copy while the app is active, then persist background work for remaining items. Coalesce rapid edits and limit foreground work; do not hash and upload an entire large note for each keystroke.

Each recovery record contains enough authenticated metadata and verified payload references to restore the individual revision. Copy records to configured destinations as conditions allow. During the next complete backup, reuse those already copied objects and incorporate the eligible records into a verified checkpoint.

The normal save must not depend on an available cloud connection. A crash before the independent copy leaves a clearly pending revision, not a falsely reported backup. A stricter “do not acknowledge save until a second destination has it” mode would be a different, costly product promise.

Keep recovery records until a verified checkpoint protects them at the required destinations or the user explicitly discards them. Define storage limits, warnings and a policy for coalescing superseded revisions. Never silently delete the only available new-item recovery copy to meet a cache limit.

Unsaved editor drafts are a separate category. If included, capture stable recoverable draft states through the editor's existing recovery boundary, then restore them as drafts for review. Do not label live unsaved typing as a saved-note backup.

## 5. Scheduling and resource controls

Use WorkManager for persisted, resumable background requests. Android does not guarantee an exact execution time; a periodic interval is a preference and eligibility window. WorkManager's minimum periodic interval is 15 minutes, and constraints may delay or skip a run.

Proposed controls:

- Automatic backup enabled/disabled; disabling it pauses copying without deleting existing backups.
- Charging only, or charging OR battery above the user's threshold.
- Frequency/desired window for full checkpoint publication.
- Wi-Fi/unmetered only by default for cloud; optional mobile-data permission.
- Optional roaming restrictions, data budget and separate treatment for large attachments.
- Device and cloud destinations with independent progress and health.
- Backup now, pause/cancel, retry and check backup.

WorkManager constraints are combined as requirements. “Charging OR battery above X” needs explicit scheduling and a fresh battery check, rather than setting two constraints and assuming an OR. BatteryNotLow is a system condition, not a customizable percentage. Coordinate all triggers with one backup coordinator so they cannot publish conflicting runs.

Process work in bounded units, checkpoint progress and retry with backoff. Resume after process death, reboot, loss of charging, connection changes and temporary authentication failure. Backup now is user initiated, with honest progress and cancellation; verify current foreground-service and job restrictions for the app's target SDK before implementing long transfers.

A missed run should produce catch-up attempts and an overdue status. It should not erase the queue. Use configured preferences and meaningful staleness to determine when a notification is useful, avoiding a notification for every normal Android scheduling delay.

## 6. Encryption, recovery and verification

Generate a random repository encryption key. Use an established authenticated-encryption implementation for both payloads and private metadata. A candidate is AES-256-GCM with independently safe nonces per object; finalize the format and crypto library after review, not from a hand-written cipher scheme.

Wrap the repository key under two independent recovery methods: the user's backup password through a vetted memory-hard password KDF, and a separately generated high-entropy recovery key. Select and benchmark the KDF/library and parameter bounds on supported Android devices. Store salts and format parameters, never the password or recovery key in plaintext in the cloud.

Remember a device-wrapped copy using Android Keystore to enable automatic operation. The Keystore key itself is not the portable backup key: a replacement phone must recover access with the password or recovery key. Test that path on an installation without the original Keystore.

Changing the password should rewrap the repository key without uploading all data again. If an attacker previously obtained both a usable wrapper and the corresponding password/key, merely deleting that wrapper or changing the password does not revoke their access to old ciphertext. Compromise recovery requires key rotation and a separate plan.

Encrypt titles, folder names, note text, OCR/transcripts, selected settings and manifests. Remote storage can still observe account identity, object sizes/counts, timing and access patterns. Keep readable user names out of filenames and logs.

Use repository-scoped keyed identities for plaintext deduplication where appropriate so exposed filenames do not disclose a guessable plaintext SHA-256. Use SHA-256 for explicit digest checks and authenticated encryption for cryptographic integrity. SHA alone is not protection against an attacker replacing data and its hash.

Verification has levels:

- Verify source content and bounds, authenticated encoding, and every newly transferred object's stored bytes before publication.
- Validate checkpoint structure and every referenced object's availability; reuse previously verified objects only under a stated verification policy.
- Periodically scrub older stored content with bounded full reads, authentication and hashes.
- Verify restore inputs before changing live data, and prove representative restores end to end.

Provider acknowledgement, file size, ETag, or a checksum advertised by a provider is not automatically the same as Cobalt reading back and authenticating its stored content. Display the difference between last completed backup and last full integrity check.

No Cobalt operator can recover data if both password and recovery key are lost. Setup should ask the user to save the recovery key outside this phone and verify that they recorded it. Biometric access on the original phone is convenience, not portable recovery.

Credentials should be excluded by default even when preferences are selected. Current API/OAuth secrets are encrypted with a device Keystore key; copying their encrypted preference values does not make them portable. Optional API-key backup needs explicit consent and encryption under the portable backup key. Trakt/cloud session tokens should require reconnecting rather than silently reviving an old session.

## 7. Selection, deletion and retention

Give users a preview of the exact selected content and estimated initial size before starting. Selection belongs to each destination so a user can keep everything locally but send a smaller scope to cloud storage.

Controls should include content types/categories, selected folders or all folders, excluded folders, unfiled items, archived content, attachment inclusion, app-preference groups and optional recovery/history/Trash categories. Individual item exclusions can be an advanced control.

Proposed deterministic rule: a content type must be enabled, the item must satisfy the included-folder/unfiled rule, and any explicit exclusion wins. An item in both an included folder and an excluded private folder is excluded. Display examples and counts so this is understandable.

Export only metadata required for the selected content and chosen settings. Do not leak an excluded folder name through an included item's folder memberships or a full database copy.

Changing selection increments a policy version. Newly eligible content becomes pending even if it has not been edited. A checkpoint keeps its actual scope so restore can explain what was included. An in-flight copy must re-check consent before publishing content excluded by a newer policy.

Unchecking a folder stops future copying but does not physically erase previous checkpoints or recovery records by itself. Offer a separate, explicit “Remove this content from existing backups” operation that removes references and then reclaims unreferenced objects safely. Explain when cleanup is pending and when the provider may retain its own revisions.

Deleting a note locally should remove it from the next current checkpoint while respecting chosen recovery retention. A pending delete must not destructively edit the last verified backup. Permanent backup deletion is distinct from local Trash, disabling backup and removing a destination.

Default and maximum revision retention remain open. At minimum protect current, previous-until-verified, pending recovery records and active restore/read operations from pruning.

## 8. Backup detection and multiple devices

Repository headers should identify the format, version, random repository ID and encryption/recovery parameters. Private checkpoint metadata identifies the device, sequence, scope and parent checkpoint. Connecting to a folder should detect an existing repository and offer unlock/restore rather than overwrite it.

Matching digests help reuse objects and detect unchanged revisions. They do not decide which of two independently edited notes is correct. Backup detection, deduplication and multi-device synchronization are separate behaviors.

First release recommendation: one publishing owner per repository; other devices can discover and restore it. Continued backup from a replacement phone needs an explicit ownership/handoff step. Storage providers without enforceable conditional writes/leases must not be claimed to support safe concurrent ownership. Use isolated device branches if robust exclusive publication cannot be enforced.

A normal synced folder does not make conflict handling automatic. If another tool transfers the repository, it must preserve immutable objects and commit ordering, and restore must reject incomplete manifests. Such external syncing is an optional advanced workflow to test, not a prerequisite for ordinary cloud backup.

Two-way note synchronization would require stable identities, concurrent-edit conflict resolution, tombstones, deletion semantics and multi-device authentication. Keep it as a separately planned feature.

## 9. Restore is a release requirement

Flow: select/detect repository → unlock with password or recovery key → validate format and checkpoints → show coverage/date/size → select what to restore → inspect conflicts → stage and verify → apply → report outcome.

Offer the newest verified checkpoint plus a separately described option to recover newer complete pending records. Only combine matching repository/owner/scope records with well-defined ordering; do not blindly overlay deletions or damaged fragments. Let users choose older verified checkpoints when available.

Restoring onto a new installation should rebuild the app's current schema from a versioned logical backup format, rewrite local attachment paths and rebuild search/derived indexes. Reject unsupported versions without modifying existing data. Keep migration compatibility separate from the Room schema number.

For an existing library, identical revisions can be skipped; differing items need a clear conflict policy, with keep-both as the safe proposal. Full replacement requires review and an independent rollback checkpoint. Stage and verify input before live publication. Define crash-recovery and cancellation boundaries before designing a replace operation.

Do not blindly copy caches, external URI permissions, widget IDs, migration flags, crash state or remote work queues. Preserve user-authored media preferences and tracking, but reconnect integrations and avoid sending restored historical state to Trakt as new user actions. Remap folder/item references where keep-both creates new IDs.

Whole-library undo history and unsaved drafts need explicit versioned export/restore semantics if enabled. Do not silently omit them while claiming a complete backup.

## 10. App integration constraints found in current source

- `AppDatabase` is currently schema 16, using Room and `JournalMode.TRUNCATE` with `synchronous=EXTRA`.
- Shared note chunks/nodes/roots and note history are authoritative database records. Legacy Markdown body files, recovery journals and local attachments also exist. Backing up note metadata alone would lose content.
- `SavedItemDao.getAllItems()` intentionally returns previews for text/code notes. A backup exporter must read full bodies through `NoteBodyStore`, not treat list/search projections as full notes.
- `NotePersistence` serializes writes and commits verified note metadata/history. Extend the durable publication boundary for backup journaling and generation protection.
- Existing body leases protect active readers; backup pins must survive interrupted/background operations and be included in shared-storage collection, history cleanup and Trash purge.
- Attachments are currently persisted through files and metadata updates. Audit mutation/deletion paths and make exported revisions stable; a note write lock alone is not a complete attachment snapshot protocol.
- `SettingsRepository` and other components use separate SharedPreferences. Export an explicit typed settings schema, including personal Screening preferences where selected, rather than copying every preference file.
- Existing `isBackedUp`, `isPendingBackup` and `isSynced` fields are documented legacy metadata. They are not a working durable backup journal and are insufficient for multiple destinations/revisions.
- Existing startup recovery/migration guards must also gate backup reads. Use a consistent logical export boundary and avoid a long database lock during cloud transfer.
- Android automatic backup is currently disabled. This feature needs its own user-authorized destinations and scheduler; merely enabling the manifest flag does not implement the proposal.

Do not copy a live SQLite database file as a substitute for a coordinated snapshot. SQLite's online backup API and `VACUUM INTO` are examples of consistent database-copy approaches, but availability through Android's current driver and API range must be checked. A selective logical exporter is the preferred proposal here because a full database contains excluded content and device/session state.

## 11. Settings and status

Proposed Backup & restore screen:

- Protection summary: verified checkpoint date and number of selected changes still pending.
- Device destination: location, last successful checkpoint, recent recovery coverage, stored size and health.
- Cloud destination: provider/account display, equivalent status, connection/authentication state.
- Progress while running: objects/bytes copied, verification and publication stage; do not use a fake precise completion estimate.
- Backup now, pause, retry, check backup and restore actions.
- What to back up: categories, folders/exclusions, preferences and advanced recovery/history controls, with counts and estimated size.
- When to back up: charging/battery rules, desired frequency and network permissions.
- Security: password change, recovery-key management and credential-backup consent.
- Retention and space: retained revision bytes, pending bytes, cleanup status and storage limits.
- Recent activity: successes, interruptions and actionable errors without private content in logs.

Use per-destination copy/verification times. A new recovery record does not update the “last full backup” time. Show waiting reasons such as charging, battery, Wi-Fi, unavailable folder, expired sign-in, quota or insufficient space.

Distinguish logical selected-library size, physical repository size, bytes changed/transferred this run and reclaimable history. Size estimates before the first encrypted transfer are estimates.

## 12. Delivery sequence and acceptance criteria

### Phase A — settle behavior and prove feasibility

Finalize providers, selection rules, previous-checkpoint/retention policy, deletion semantics, recovery-copy scheduling, key management and restore conflict policy. Specify the threat model and format/version boundaries. Benchmark a mature-engine option against a narrowly scoped exporter using established crypto; examine license and Android packaging constraints.

Probe local document-provider durability, Nextcloud/WebDAV conditional publication, chunking efficiency and Google authorization feasibility before promising support. Record provider capabilities explicitly. Do not expand the release to many providers before one complete path works.

### Phase B — independent local backup and complete restore

Implement coherent export, journal/pins, encrypted incremental repository, publication/pruning, local destination and complete restore. Prove password and recovery-key restoration on a fresh installation, not only decrypting an object in the original process.

### Phase C — recent-change protection and Android scheduling

Implement early recovery records, persisted catch-up, per-destination state, resource preferences and the full status screen. Audit every relevant mutation path. Test scheduling behavior on physical phones and supported API boundaries.

### Phase D — first direct cloud provider

Use the same engine with Nextcloud/WebDAV, safe authentication, resumable transfer and provider-specific failure handling. Test real remote storage including read-back and restore. Consider optional Drive after its feasibility requirements are resolved.

### Phase E — release hardening

Required failure scenarios:

- Kill/restart during export, encryption, upload, object verification, checkpoint publication, restore and pruning.
- Save, edit, delete, move or exclude content while a run is active; new changes must remain pending and pinned.
- Create a note, copy only its recovery record, then restore it when no later full scheduled checkpoint occurred.
- No connectivity, force-stop, reboot, delayed/skipped work, removed charging and battery crossing the threshold.
- Revoked folder access, removed USB, cloud quota, expired credentials, partial writes and full local disk.
- Damaged payload, damaged manifest, missing object, wrong password/recovery key and unsupported version.
- Restore fresh install without original Keystore; restore keep-both conflicts and verify rewritten references/attachments.
- Never upload excluded text or names through metadata, logs, settings or an obsolete in-flight policy.
- Large note/video and many-small-note workloads: bounded memory, responsive saving, cancellation and storage accounting.
- Owner collision or replacement-device handoff without destroying another device's checkpoint.

Measure how long recent changes take to reach each destination while the app is active and while it is in background, rather than claiming a universal recovery-time guarantee. Publish tested limits for API range, device/OEM behavior, provider versions and representative library sizes.

Release gate: the newest incomplete update cannot invalidate the previous verified backup; a newly copied recovery record can be recovered without waiting for a later full run; exclusions and encryption remain correct; and fresh-install restore reproduces the selected content and attachments. No production-ready claim before these are evidenced.

## Research sources

Read on 2026-11-17. These describe capabilities and constraints, not a community preference survey.

- Android WorkManager constraints and periodic timing: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
- Android Storage Access Framework and persistent URI grants: https://developer.android.com/training/data-storage/shared/documents-files
- Android Keystore: https://developer.android.com/privacy-and-security/keystore
- SQLite online backup and alternatives: https://www.sqlite.org/backup.html
- Restic repository design: https://restic.readthedocs.io/en/v0.18.0/design.html
- Joplin service-independent synchronization drivers: https://joplinapp.org/help/apps/sync/
- rclone Google Drive: https://rclone.org/drive/
- rclone WebDAV: https://rclone.org/webdav/
- Neo Backup encrypted-backup and external-sync examples: https://github.com/NeoApplications/Neo-Backup/blob/master/README.md
- Nextcloud Login Flow v2: https://docs.nextcloud.com/server/latest/developer_manual/client_apis/LoginFlow/index.html#login-flow-v2
- Nextcloud hosted account directory: https://nextcloud.com/sign-up/
- Nextcloud Android app on F-Droid: https://f-droid.org/en/packages/com.nextcloud.client/
- F-Droid anti-feature distinctions: https://f-droid.org/docs/Anti-Features/
- Google installed-app OAuth and Android redirect restrictions: https://developers.google.com/identity/protocols/oauth2/native-app
- Google Drive hidden app-data scope and lifecycle: https://developers.google.com/workspace/drive/api/guides/appdata
