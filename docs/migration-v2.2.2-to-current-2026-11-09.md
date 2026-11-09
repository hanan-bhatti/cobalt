# Published v2.2.2 → current release: retained-data migration

The in-place update on `emulator-5554` passed. The published v2.2.2 Foss APK
(schema 6, version code 18) was updated to the user-compiled current release
(schema 15, version 2.2.3/code 19, source `9f50ce3`). **No uninstall or data clear
occurred between populating this baseline and updating it.**

## Dataset and evidence

The running emulator was repopulated after the user removed its installed Cobalt
packages. This is recreated test data, not a recovery of the earlier deleted
baseline or original Gemini key. Five records were captured through the actual
v2.2.2 UI. At the user's request for faster population, a certificate-matched
instrumentation helper inserted 495 fixtures transactionally into the existing
legacy schema and copied the media into private app files. The APK was unmodified.
Bulk seeding does not establish 495 individual Capture-screen passes.

| Type | Before | After |
| --- | ---: | ---: |
| Text | 300 | 300 |
| Code | 20 | 20 |
| Links | 100 | 100 |
| Images | 50 | 50 |
| Videos | 10 | 10 |
| Audio | 10 | 10 |
| Movies | 5 | 5 |
| TV shows | 5 | 5 |
| Total | **500** | **500** |

Seventeen records are archived: Home shows **483**, while Settings shows **500**.
Both custom folders and their assignments survived. The ten media records use
real TMDB responses fetched by the preparation script. Baseline app search for
`Matrix` also returned live results with the supplied test key.

The [machine-readable evidence](evidence/version-comparison-2026-11-09/migration-summary.json)
records APK/source identities and the computed outcome. Raw manifests, private
credentials, test-helper source, logs and XML hierarchies remain outside Git in
`~/.local/state/cobalt-review-2026-11-09/`.

## Installation and signing

Both APKs used production certificate SHA-256:
`9af192bc1865c315591539bd4022e69e6a35e597384b846d3b3f272e74c6e2cb`.

The earlier failed update used Gradle's Android Debug signing fallback. Loading
`KEYSTORE_PATH`, `KEY_ALIAS`, `KEYSTORE_PASSWORD` and `KEY_PASSWORD` privately
selected the matching release key. No credential was added to the repository.

The user's current Gradle compilation/R8 output was reused. `installRelease`
ran packaging/signing/installation with Kotlin/KSP/R8 task exclusions, avoiding
another duplicate compilation; an init hook verified the certificate before
installation. Gradle reported `BUILD SUCCESSFUL in 46s`, installed on one device.
Android retained `firstInstallTime=2026-11-09 21:02:07` and recorded
`lastUpdateTime=2026-11-09 22:00:26`.

The version number already was 2.2.3/code 19. This review did not bump it, merge,
tag, push, revise the survey or generate promotional screenshots.

## Observed migration sequence

1. The first updated launch displayed **“Move your notes?”**, “Move notes” and
   “Not now.” The XML hierarchy was captured before confirmation.
2. Choosing **Not now closed the app to the launcher**. Relaunch showed the
   Data Migration page with **Ready to move**; the initial modal did not repeat.
   This is a startup gate, not an in-app postponement that opens Home.
3. Migration was started, then deliberately force-stopped after 0.7 seconds.
   Relaunch showed **Ready to resume**, **320 notes**, **6 moved to Markdown**
   and **Resume migration**.
4. Resume progressed through the retained originals. The completed screen showed
   **Your notes are ready**, **320 notes verified** and **Open Cobalt**.
5. Persisted status was `COMPLETE`, `failed=0`, `errors=[]`,
   `migration_required=false`, `completed_format=1`.

The resumed run reported 313 newly converted notes because seven content heads
had already committed before it restarted. Six had reached the previous
progress-counter checkpoint. All 320 were independently verified at completion;
313 is the resumed-run conversion count, not the retained-note count.

## Independent integrity check

The inspection opened SQLite read-only. For each migrated note it traversed the
shared range tree in order, validated each immutable chunk checksum and computed
SHA-256 over the actual reconstructed bytes. This was compared with a separate
pre-update SHA of the legacy inline body, rather than trusting `contentHash`.
Media files were opened and fully hashed on both sides.

- **500 IDs present; no missing or extra records.**
- **320/320 text/code bodies: exact bytes and SHA-256 preserved.**
- **70/70 local image/video/audio files: exact bytes and SHA-256 preserved.**
- **All 100 URLs and descriptions, ten movie/show contents and legacy metadata
  unchanged.** Zero differences in the captured pre-existing fields.
- Both folder records, assignments, timestamps, ordering, archive/delete flags
  and titles unchanged.
- The 100,000-byte and 399,995-byte large bodies matched exactly. The mixed
  Markdown/Unicode fixture matched all 198 UTF-8 bytes.
- The TMDB credential matched the supplied key before and after. It changed from
  plaintext legacy storage to authenticated Keystore encryption, verified by
  decrypting and comparing a private digest. No Gemini/Trakt success is claimed.

## UI copy and test-tool caveats

The migration UI still describes “private Markdown files.” Actual completed
storage in this build is **shared immutable SQLite chunks/range trees** for all
320 migrated notes. “FILE” is the compatibility body-location label, not evidence
that each note has its own physical `.md` file. This wording needs a separate
implementation/copy task; it was not silently changed during the review.

An early inspection attempt returned a report larger than Android's Binder
transaction limit (1,122,668 bytes as a UTF-16 parcel). The stack identifies
`Instr: com.cobalt.review.inspect.Inspector` / `finishInstrumentation`; this was
our test helper's transport failure. It generated a Cobalt crash-report dialog,
which was dismissed without sending a report. Compressing the helper output
fixed the transport; the subsequent full inspection passed. This is not a
normal-user migration crash and is not omitted from the evidence.

A pre-update emulator snapshot save returned OK and had nonempty RAM, texture
and snapshot metadata files. The snapshot was not restore-tested. The controlled
force-stop tests process interruption; it does not establish power-loss,
disk-full, physical-phone or every-Android-version guarantees.


## Additional current-build checks

The small Markdown note was edited through the UI. The reader reflected the
marker immediately after Update Archive. Saved Undo restored the original,
Redo restored the edit, and Undo remained usable after a process restart. The
original content was restored before the final retention check. Current Settings,
storage/recovery pages, typography and note-share previews were inspected; see
the [screen comparison](version-comparison-v2.2.2-to-current-2026-11-09.md).


## Final installed package and retention recheck

The final packaging-only update installed the synchronized bundled changelog.
Its executable DEX files are byte-for-byte identical to the migration-tested
build. The APK's `assets/CHANGELOG.md` matches the root changelog exactly.

The [final computed recheck](evidence/version-comparison-2026-11-09/final-retention-summary.json)
confirmed all **500 IDs**, **320 original note bodies**, **70 original local media
files**, folders and archive/delete flags still retained, and the TMDB key still
correct and encrypted. The temporarily edited Markdown fixture is restored to
its original hash; only its edit timestamp changed. Opening Breaking Bad detail
also refreshed its genre/provider/trailer/backdrop/company/extracted metadata and
timestamp. Those are post-migration enrichment changes, recorded separately from
the zero-difference initial migration comparison.

A final cold launch opened Home with 483 active items and no migration prompt.
Android activity timing was TotalTime 1,111 ms / WaitTime 1,127 ms; it is not a
complete-render or hardware performance guarantee. Current executable version
remains 2.2.3/code 19. The F-Droid release notes are 443 characters and the build
recipe's future tag was not published by this review.
