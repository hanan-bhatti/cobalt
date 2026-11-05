# Reviewed recent commit index

Companion to MANUAL_TEST_CHECKLIST.md, prepared 5 November 2026.

Newest first. This is a historical index, not a declaration that every historical feature remains enabled. In particular, old Play/Firebase/cloud commits predate their removal. There are 117 commits after the pre-cleanup snapshot bea90ab at checklist baseline f0beddc. Uncommitted work is not listed here.

| Commit | Change |
|---|---|
| `64551bb` | fix(feedback,capture): allow submitting consecutive bug reports and prevent keyboard closing during folder creation |
| `f0beddc` | docs(perf): record checklist and Home scroll validation |
| `c66aeba` | perf(home): reduce card invalidation and stabilize scrollbar scrubbing |
| `f3580af` | fix(notes): update checklist markers without reopening the reader |
| `111b25d` | fix(ui): enable blur/haze settings on editor search bar and eliminate top gap in font settings |
| `e95d125` | docs(editor): record validation and device acceptance work |
| `74e8034` | fix(markdown): preserve nested fence continuation indentation |
| `0df2b3e` | fix(notes): recover retained live drafts without overwriting notes |
| `724a478` | fix(editor): preserve merged native font preferences |
| `af83e7d` | fix(editor): handle input across disk viewport boundaries |
| `7958a8d` | fix(editor): complete disk session persistence and recovery |
| `14ac733` | fix(search): restore 500/1000 limits with 500+/1000+ indicators, and batch replace all occurrences |
| `b68f026` | feat(search): chunk 10k replacements, expand search/outline caps, and dual-highlight matches |
| `b7dad1d` | fix(editor): ensure DiskEditorSession close directly releases file locks |
| `fe2036b` | fix(editor): eliminate typing flicker by retaining styled spans across text insertions |
| `8c05f45` | fix(editor): prevent validation exceptions from pausing disk editor and expand replace capacity |
| `75ef53a` | fix(editor): prevent search bar from losing focus on typing by navigating on action only |
| `ad7af1f` | fix(editor): attach hazeSource to editor viewport and enhance search bar frosted blur |
| `ee5f93f` | fix(capture): show only Edit Note button in preview mode to eliminate button clutter |
| `4f0c100` | fix(editor): fix search bar haze, placement & focus, quick note draft flashing, import warning dismissal, and preview save crash |
| `be1cd58` | perf(markdown): dynamic per-block link decoration with 300 link limit |
| `9aa8eac` | fix(markdown): prevent mid-table splitting, expand block limits, and raise link decoration threshold |
| `a134fb5` | fix(plain-note): stream 16KB natural paragraph chunks with fillMaxWidth to prevent half-screen wrapping |
| `30de809` | fix(detail): extend note scroll underneath blurred bottom bar and align share button styling |
| `d3d082f` | fix(search): allow outside interaction during note search and isolate highlight to matched text |
| `3c8fba1` | feat(editor): modernize search & replace and outline bottom sheet in continuous editor |
| `7d43358` | perf(home): eliminate per-item entry animations and zero-allocation staggered grid scroll progress |
| `d195341` | fix(scrollbar): make floating pill super smooth with frame-paced scrubbing and fix blur on hub and detail screens |
| `1c4873f` | fix(editor): resolve LayoutNode owner crash on save and add blurred floating scrollbar with settings sheet |
| `bc0719f` | feat(editor): redesign markdown toolbar with minimal vector icons and fix keyboard gap |
| `7bdd78e` | test(capture): add table-driven path leak prevention suite and changelog entry (#21) |
| `b310aa5` | feat(storage): add idempotent off-thread legacy title cleanup (#21) |
| `3300045` | fix(ui): apply display guards and prevent path leaks across UI, widgets, and notifications (#21) |
| `757ee85` | fix(capture): resolve original display name and guard title at save choke point (#21) |
| `ec41050` | feat(capture): introduce centralized title resolver and localized fallbacks (#21) |
| `1c9dec9` | feat(trakt, web): implement PKCE auth, Android App Links, public website, and legal sync |
| `1eebdf7` | docs(fonts): record verified APK size metrics and byte deltas |
| `4ac77fd` | feat(trakt): safe session disconnect credential store and race guards |
| `e6bfea2` | feat(notes): integrate LocalCobaltTypography across markdown renderer, code blocks, reader, and editor |
| `492f8f0` | feat(ui): add Text & fonts subpage with live preview, bottom sheet selectors, and Appearance navigation |
| `7caedaa` | feat(ui): add ExpressiveTypographySliderItem with font endpoint hints, haptics, and TalkBack semantics |
| `704aab2` | feat(typography): add CobaltTypography provider with font cache, variable font support, and style scalers |
| `a755e94` | feat(prefs): add TypographyPrefs model with validation, presets, and SettingsRepository storage |
| `7421f87` | feat(fonts): add local FOSS fonts, licenses, fetch script, and FONTS.md |
| `a9e6c4f` | wip: preserve in-progress work from concurrent agent (live editor, markdown blocks, trakt, drawables) |
| `78875bf` | feat(trakt): safe session disconnect with server token revocation, race guards, and legal disclosures |
| `a366e88` | docs(storage): record Trash design verification and editor limitations |
| `66cab6f` | chore(storage): document intentional durable-write lint exceptions |
| `21d91ee` | test(notes): cover saved-history compactor checkpoints and stale requests |
| `7d2c1cf` | feat(ui): redesign Storage with usage breakdown and focused cleanup pages |
| `f81b5fe` | feat(storage): add recoverable Trash and safe retained-file cleanup |
| `94654a1` | fix(editor): lock visible styling bounds against concurrent edits |
| `fd8b588` | test(android): stream stress metadata snapshots with bounded memory |
| `e7e9e5b` | fix(ui): resolve dock locale and Scaffold lint errors |
| `0b437b0` | test(android): reserve recovery space during stress imports |
| `33df31c` | test(notes): cover delta restoration on Android filesystems |
| `c8b5e04` | test(android): support explicit physical debug-device profiling |
| `c28df70` | fix(markdown): preserve source bytes when toggling tasks |
| `59988c7` | perf(reader): prioritize visible Markdown over speculative work |
| `f336db9` | feat(notes): compact saved versions into verified deltas |
| `ad9cebf` | fix(editor): reuse verified bodies for unchanged saves |
| `a574aea` | docs(notes): record recovery storage and regression gate |
| `c77efcd` | test(notes): add resource-limited regression runner |
| `6f68c84` | test(editor): wait for layout before IME assertions |
| `958141a` | fix(editor): journal preview edits before full checkpoints |
| `15f4ec8` | test(storage): exercise projections with real host atomic moves |
| `45a1256` | test(editor): cover recovery writer transactions and failures |
| `e979934` | fix(editor): preserve and export drafts after storage failures |
| `0b4d5f9` | test(editor): cover document release across view disposal |
| `4ca72a6` | feat(settings): add storage recovery and explicit copy retention |
| `d659d34` | feat(editor): journal native edits with bounded recovery writes |
| `926e10b` | feat(notes): add checksummed edit journal and disk recovery |
| `ca83c3f` | fix(editor): preserve cursor handoff and recovery generations |
| `e907264` | fix(reader): retain Markdown rendering and prefetch bounded ranges |
| `f9b7f3d` | feat(editor): replace capture ranges with continuous native editing |
| `58c2183` | feat(notes): retain continuous documents with verified draft checkpoints |
| `e238245` | build(editor): pin native core with license and logging safeguards |
| `de81efa` | perf(notes): stream file pieces through persistent range handles |
| `fc1edc9` | docs(notes): record reader changes and verification limits |
| `5fedf31` | fix(ui): reuse shared note icons and settings navigation |
| `de1ebed` | perf(notes): prepare reader content incrementally with bounded caches |
| `5ebf831` | chore(ui): preserve shared export and overflow icons |
| `2895551` | docs(ui): record migration redesign and reset checks |
| `0609da1` | test(notes): prepare repeatable debug migration resets |
| `76e960b` | refactor(ui): match migration and recovery to settings |
| `42b778e` | test(notes): seed legacy emulator migration fixtures safely |
| `79db208` | docs(notes): record redesign checks and editing limits |
| `dee1dce` | refactor(notes): integrate lazy notes into original screens |
| `bb3f1d9` | fix(notes): stage capture edits until explicit save |
| `e62f326` | feat(notes): receive and stream text documents safely |
| `9f06e1a` | docs: record verified debug APK and passing note tests |
| `d25a225` | test(notes): isolate stream round trips from Android filesystem shadows |
| `85a8fd7` | docs: describe note engine security and device-test requirements |
| `4c7c7da` | test(android): bound test resources and add debug verification workflow |
| `71b7e7f` | feat(editor): wire bounded reading, lifecycle journals and cross-section actions |
| `cf76ee7` | feat(notes): add bounded blocks, generation undo and reference reconciliation |
| `0b0c358` | feat(security): protect credentials and restrict shared export paths |
| `e04b7d4` | refactor(logging): route diagnostics through the redacted logger |
| `f33d491` | fix(privacy): redact application log messages and throwable details |
| `db144dc` | test(notes): add migration and recovery cases with checkpoint report |
| `aa8f319` | fix(markdown): parse bounded documents off the main thread |
| `4fe551f` | feat(notes): wire durable capture and bounded file editing workflows |
| `4587328` | fix(dock): limit recent queries and bound note previews and paste layout |
| `e3260f8` | feat(notes): persist recoverable drafts and stream imports and edits |
| `7563cf9` | feat(notes): add bounded piece-table and UTF-8 navigation primitives |
| `b5e74b6` | docs(notes): record storage checkpoint and remaining release blockers |
| `8712e77` | fix(notes): bound note layout and preserve drafts on save failure |
| `592de13` | fix(ocr): serialize selected link saves within ViewModel lifecycle |
| `d731707` | feat(notes): gate startup on resumable migration and crash recovery |
| `c8ea89f` | feat(notes): persist verified file bodies with body-free Room queries |
| `c975b37` | feat(notes): add atomic private Markdown body store and size policy |
| `bd1499a` | build: isolate debug application data and label |
| `7486169` | fix(privacy): disable automatic backup and device transfer |
| `a031f7c` | refactor: remove remaining account and refresh surfaces |
| `d9a9c9d` | docs: describe single-app distribution and network inventory |
| `adcb9f3` | ci: build one release APK and archive R8 mapping |
| `640e197` | chore(backend): remove retired cloud deployment sources |
| `e8bc429` | refactor(android): consolidate Cobalt into one local application |
| `bea90ab` | chore: snapshot existing work before single-app cleanup |
| `f9c68a9` | feat(media): add Trakt web parity metadata, official ratings, quality sparkline, next up card, and clean provider cards |
| `c99cf95` | feat(media): enrich trakt sync with tmdb posters, clickable watch providers, and tv show progress |
| `3ce928e` | fix(trakt): resolve device token polling, add HTTP telemetry, and handle non-blocking sync |
| `354ae8d` | fix(trakt,theme): resolve custom tab theme inversion, settings screen colors, trakt icon, and sync issues |
| `b5a9e81` | fix(trakt): correct device token parameter name and move polling to persistent supervisor scope |
| `38f285b` | feat(trakt): implement two-way sync engine, background worker with notifications, and settings UI with advanced self-hosted support |
| `4ec50c6` | feat(trakt): extend database schema with trakt and series progress models (migration 7->8) |
| `671474b` | feat(trakt): add trakt api models, retrofit service and device code auth manager |
| `7876d64` | style(settings): redesign archive folders and adaptive reminders to match expressive design system |
| `89d8d01` | feat(survey): remove pre-selection bias, add 'why almost quit' question, fix submission flow (only mark submitted after actual send) |
| `0cf2abb` | feat(reminders): add custom time picker for reminder delivery schedule |
| `59c25c8` | feat(reminders): on-device adaptive knowledge resurfacing engine, settings subpage, and WorkManager scheduler |
| `f986c9d` | fix(detail, metadata): live-refresh DetailScreen on edit save; prioritize real source description over AI fallback |
| `074bb53` | feat(media): replace TMDb spec badges with interactive Canvas line graph (24H/7D/30D scrub, bezier curve, gradient fill) |
| `4c4352e` | feat(ux): replace badge with subtle accent border glow on suggested folders; folder empty-state icon fix; card expand animation; misc polish |
| `2e97335` | feat(settings, ui): Archive & Organization screen, 52-week activity heatmap, redesigned MoveToFolder dialog with ranking, zero-lag share sheet |
| `4a496a8` | feat(gemini): per-day token tracker with auto-recovery; redesign usage screen with interactive 7-day bar chart |
| `d705f86` | feat(ranking, search): add adaptive FolderRankingEngine and ElasticFuzzySearchEngine with multi-strategy scoring |
| `6a4f1b0` | fix(service): align foreground service type to SPECIAL_USE; side dock uses dynamic theme color and reacts to wallpaper changes |
| `813ee50` | chore(release): bump version to 2.2.2 (versionCode 18) |
| `f1a769e` | feat(media, ui): parallelize media search and polish undo deletion capsule styling |
| `e0c8463` | fix(media): render feedback banner inside MediaSearchBottomSheet for proper z-index and retry |
| `379cb5a` | fix(capture): position confirmation dialog correctly inside CaptureScreen |
| `44461ca` | feat(capture): add delete button with confirmation dialog in edit mode |
| `19a5a8d` | feat(ui): complete capture screen overhaul, audio redesign, side dock, feedback export, and dynamic issue sync |
| `e9ac518` | chore(release): bump version to 2.2.1 (versionCode 17) |
| `a9e493f` | fix(animation): choreograph detail bars entry, remove legacy skeleton loaders, and add animation telemetry |
| `e7fbfbf` | fix(media): resolve YouTube trailer playback configuration error and support YouTube video URLs |
| `704f38f` | fix(audio): support Gemini 3.5 Transcribe response schema (audioTranscription.text) |
| `3e67459` | fix(media): render video preview in capture screen, harden AudioPlayer lifecycle, remove duplicate OCR/notes fields, and default to Gemini Flash Lite |
| `ae99da8` | fix(audio): keep screen on during recording, fix Gemini finishReason STOP parsing, and preserve recorded memos |
| `4b439a6` | fix(ui): improve card morph animation, detail RTL rendering, dock auto-collapse, and in-app browser routing |
| `49e6801` | Recreate smooth card-to-detail morph animation and fix audio transcription |
| `757ffa1` | fix(ui,player): resolve SupportScreen annotation target warning and fix YouTube trailer WebView configuration error |
| `12c8dff` | fix(backend,cloud): deploy pure 2nd gen functions, fix quota triggers, and support screen drawable annotation |
| `741dd21` | feat(release): bump to v2.2.0, expressive settings, custom header icons, and clean UI improvements |
| `e48faa5` | feat(dock,ocr): polish SideDock content cards, sharp background scrim, humorous loading states, and error code mapping |
| `e63b01d` | feat(dock): fluid Oppo-style water-drop morph animation between handle and dock |
| `5cab110` | feat(dock): anchor to handle position, 100% transparent backdrop, 3-line input, and quick link mode |
| `a1c82f2` | docs(readme): add high-res hero banner and unify developer badges |
| `8e43b09` | docs(readme): add app logo, Material 3 badges, and remove misleading play badge |
| `43fe270` | feat(dock): modernize SideDock with Compose GPU animations, Chrome Custom Tabs, and revamped README |
| `ecf55d4` | chore(fdroid): update recipe commit to d1e0629 |
| `d1e0629` | chore(fdroid): update recipe commit hash |
| `9d20bf0` | feat(play): add 'Don't ask again' option to notification permission prompt |
| `3634912` | chore(fdroid): update recipe to single 2.1.2 build with full SHA |
| `888cba7` | fix(foss): address F-Droid tester feedback, decouple Home search, and bump to 2.1.2 |
| `6395a70` | feat(firebase): properly configure Crashlytics and Performance Gradle plugins for Play build |
| `642c061` | fix(play): comment out unused crashlytics dependency to prevent startup crash and format fdroid metadata |
| `7242d65` | fix(play): fix dataSync foreground service crash and premature cancellation notifications |
| `85f432d` | chore(fdroid): set recipe commit to full SHA f84079e |
| `f84079e` | fix(build): remove secrets plugin and rely exclusively on user-provided api keys |
| `35dc2e5` | chore(fdroid): update metadata recipe for version 2.1.1 (commit 3300bc4) |
| `3300bc4` | chore(release): bump version to 2.1.1 (14) and address F-Droid review findings |
| `6210792` | revert(overlay): keep in-development overlay changes on feat/overlay-unified-animation |
| `8b86f8f` | fix(fdroid): disable dependency metadata signing block and vcs info |
| `a514bf5` | ci: update metadata commit hash |
| `9b39984` | build: remove applicationIdSuffix from foss flavor to match fdroid package ID |
| `0fa6e28` | ci: add local fdroid test suite and update metadata |
| `93d8204` | build(fdroid): make signing config lookups resilient to fdroid stripping |
| `e20c277` | chore(fdroid): format metadata for rewritemeta and fix prebuild path |
