# Commit ledger: v2.2.2 to current development source

Base: `v2.2.2`. End: `6669da6`. 219 commits, oldest first.

This indexes every commit message and changed-file list in the range. Commit titles are historical wording, not claims that a feature passed this emulator review. Earlier editor/storage/share designs were superseded; use the final-behavior comparison for the current architecture. Device testing in this session applies to the downloaded v2.2.2 APK.

| Commit | Recorded change | Changed files | Kind |
| --- | --- | ---: | --- |
| [6a4f1b0](https://github.com/hanan-bhatti/cobalt/commit/6a4f1b0613f8f163f9db190b332e2ecaee226da1) | fix(service): align foreground service type to SPECIAL_USE; side dock uses dynamic theme color and reacts to wallpaper changes | 4 | Implementation |
| [d705f86](https://github.com/hanan-bhatti/cobalt/commit/d705f869fbba8395fd005bbdfc106b9eb65c934b) | feat(ranking, search): add adaptive FolderRankingEngine and ElasticFuzzySearchEngine with multi-strategy scoring | 10 | Implementation |
| [4a496a8](https://github.com/hanan-bhatti/cobalt/commit/4a496a84e41b940ec0aebce602aea27be44ba9f4) | feat(gemini): per-day token tracker with auto-recovery; redesign usage screen with interactive 7-day bar chart | 4 | Implementation |
| [2e97335](https://github.com/hanan-bhatti/cobalt/commit/2e9733531c9c14cb56898225acbdaec8f3a93cfd) | feat(settings, ui): Archive & Organization screen, 52-week activity heatmap, redesigned MoveToFolder dialog with ranking, zero-lag share sheet | 11 | Implementation |
| [4c4352e](https://github.com/hanan-bhatti/cobalt/commit/4c4352eebc612cc4cde4eb7460b5e6938b1ba069) | feat(ux): replace badge with subtle accent border glow on suggested folders; folder empty-state icon fix; card expand animation; misc polish | 14 | Implementation |
| [074bb53](https://github.com/hanan-bhatti/cobalt/commit/074bb5393bb7d73f6651ee37637788ae50447fd3) | feat(media): replace TMDb spec badges with interactive Canvas line graph (24H/7D/30D scrub, bezier curve, gradient fill) | 1 | Implementation |
| [f986c9d](https://github.com/hanan-bhatti/cobalt/commit/f986c9dbbfbded4b3ffd1c5aa68c7db6abc83cb6) | fix(detail, metadata): live-refresh DetailScreen on edit save; prioritize real source description over AI fallback | 7 | Implementation |
| [59c25c8](https://github.com/hanan-bhatti/cobalt/commit/59c25c81fc8a64cf1e7df15b0cfb89a051140f6f) | feat(reminders): on-device adaptive knowledge resurfacing engine, settings subpage, and WorkManager scheduler | 11 | Implementation |
| [0cf2abb](https://github.com/hanan-bhatti/cobalt/commit/0cf2abbb08127845d50a8341d6090bc28970fe2a) | feat(reminders): add custom time picker for reminder delivery schedule | 3 | Implementation |
| [89d8d01](https://github.com/hanan-bhatti/cobalt/commit/89d8d0199c353fd4d64b09022c60ed75f3043a7f) | feat(survey): remove pre-selection bias, add 'why almost quit' question, fix submission flow (only mark submitted after actual send) | 5 | Implementation |
| [7876d64](https://github.com/hanan-bhatti/cobalt/commit/7876d648bd36676642b8644d2f3e20e19bc2b700) | style(settings): redesign archive folders and adaptive reminders to match expressive design system | 3 | Implementation |
| [671474b](https://github.com/hanan-bhatti/cobalt/commit/671474b393118c97a622031bcf58aa15f39acc4e) | feat(trakt): add trakt api models, retrofit service and device code auth manager | 4 | Implementation |
| [4ec50c6](https://github.com/hanan-bhatti/cobalt/commit/4ec50c62e66a0d5b5d2b9cacefac3fb5d6b731b6) | feat(trakt): extend database schema with trakt and series progress models (migration 7->8) | 5 | Implementation |
| [38f285b](https://github.com/hanan-bhatti/cobalt/commit/38f285bf81ccb11fe3734c8ca7173bf822ee1610) | feat(trakt): implement two-way sync engine, background worker with notifications, and settings UI with advanced self-hosted support | 11 | Implementation |
| [b5a9e81](https://github.com/hanan-bhatti/cobalt/commit/b5a9e812be4d9187724222713788dbd9cb3d14d8) | fix(trakt): correct device token parameter name and move polling to persistent supervisor scope | 3 | Implementation |
| [354ae8d](https://github.com/hanan-bhatti/cobalt/commit/354ae8de01370c0c77801801e1e1dd846e957d71) | fix(trakt,theme): resolve custom tab theme inversion, settings screen colors, trakt icon, and sync issues | 15 | Implementation |
| [3ce928e](https://github.com/hanan-bhatti/cobalt/commit/3ce928ef1d2cf4d966692fbc7e5beb9faaac8799) | fix(trakt): resolve device token polling, add HTTP telemetry, and handle non-blocking sync | 4 | Implementation |
| [c99cf95](https://github.com/hanan-bhatti/cobalt/commit/c99cf95d173c07971e16201b02f9cf9096f5a6d0) | feat(media): enrich trakt sync with tmdb posters, clickable watch providers, and tv show progress | 6 | Implementation |
| [f9c68a9](https://github.com/hanan-bhatti/cobalt/commit/f9c68a9b599b6f76504719e564d8e8e97d03aebb) | feat(media): add Trakt web parity metadata, official ratings, quality sparkline, next up card, and clean provider cards | 11 | Implementation |
| [bea90ab](https://github.com/hanan-bhatti/cobalt/commit/bea90ab3117f7a85bcc83396edd11c7161fffb08) | chore: snapshot existing work before single-app cleanup | 34 | Build / repository |
| [e8bc429](https://github.com/hanan-bhatti/cobalt/commit/e8bc4297cd1032687e866971b87cfcd62ecfd04e) | refactor(android): consolidate Cobalt into one local application | 55 | Implementation |
| [640e197](https://github.com/hanan-bhatti/cobalt/commit/640e197348931ac7fb4a80a4d3795739cdb35170) | chore(backend): remove retired cloud deployment sources | 8 | Build / repository |
| [adcb9f3](https://github.com/hanan-bhatti/cobalt/commit/adcb9f393de56ae514337fb5f869e422f1cee610) | ci: build one release APK and archive R8 mapping | 1 | Build / repository |
| [d9a9c9d](https://github.com/hanan-bhatti/cobalt/commit/d9a9c9d3a1b24c19b91940186b38eee8749d977b) | docs: describe single-app distribution and network inventory | 7 | Verification record |
| [a031f7c](https://github.com/hanan-bhatti/cobalt/commit/a031f7c980fa7d7fe2e3e6d0e034dd82360c6897) | refactor: remove remaining account and refresh surfaces | 36 | Implementation |
| [7486169](https://github.com/hanan-bhatti/cobalt/commit/74861690f01c693e8a50eb6d1b901aa15838d93c) | fix(privacy): disable automatic backup and device transfer | 6 | Implementation |
| [bd1499a](https://github.com/hanan-bhatti/cobalt/commit/bd1499a20d3a8142b735756c9cdc010ad81da297) | build: isolate debug application data and label | 1 | Build / repository |
| [c975b37](https://github.com/hanan-bhatti/cobalt/commit/c975b37211ad5cdbdd128d3748d7ec49c76bb4f0) | feat(notes): add atomic private Markdown body store and size policy | 5 | Implementation |
| [c8ea89f](https://github.com/hanan-bhatti/cobalt/commit/c8ea89fc0749db0c76b3846b3084b67ac6f31d11) | feat(notes): persist verified file bodies with body-free Room queries | 13 | Implementation |
| [d731707](https://github.com/hanan-bhatti/cobalt/commit/d731707cc72f2282e15e4ba82c17742bd95fd718) | feat(notes): gate startup on resumable migration and crash recovery | 16 | Implementation |
| [592de13](https://github.com/hanan-bhatti/cobalt/commit/592de13c8000fc45e9583c1c3a46a4a014b1ba74) | fix(ocr): serialize selected link saves within ViewModel lifecycle | 1 | Implementation |
| [8712e77](https://github.com/hanan-bhatti/cobalt/commit/8712e775595152280f559880e87379d070ae6368) | fix(notes): bound note layout and preserve drafts on save failure | 7 | Implementation |
| [b5e74b6](https://github.com/hanan-bhatti/cobalt/commit/b5e74b6c727e7f78cea42ea2d250e2434476ee17) | docs(notes): record storage checkpoint and remaining release blockers | 3 | Verification record |
| [7563cf9](https://github.com/hanan-bhatti/cobalt/commit/7563cf9ca4b8e5d4ff3753ccf93127f76050e91a) | feat(notes): add bounded piece-table and UTF-8 navigation primitives | 14 | Implementation |
| [e3260f8](https://github.com/hanan-bhatti/cobalt/commit/e3260f8c90aa015718b999704a4cff55fe5ad8f1) | feat(notes): persist recoverable drafts and stream imports and edits | 6 | Implementation |
| [4587328](https://github.com/hanan-bhatti/cobalt/commit/4587328d2be9f4ea7f60707a6cb5310838820aff) | fix(dock): limit recent queries and bound note previews and paste layout | 4 | Implementation |
| [4fe551f](https://github.com/hanan-bhatti/cobalt/commit/4fe551fa9a09388262d37ff5524044205c8d52b2) | feat(notes): wire durable capture and bounded file editing workflows | 10 | Implementation |
| [aa8f319](https://github.com/hanan-bhatti/cobalt/commit/aa8f3194c8d3ea3e50672483944ce733d1967d13) | fix(markdown): parse bounded documents off the main thread | 2 | Implementation |
| [db144dc](https://github.com/hanan-bhatti/cobalt/commit/db144dc236d1ba377b2ae9e3570dcf5637f2a794) | test(notes): add migration and recovery cases with checkpoint report | 5 | Verification record |
| [f33d491](https://github.com/hanan-bhatti/cobalt/commit/f33d4914736bdfabd8c7a774e44c8db4ea3048cc) | fix(privacy): redact application log messages and throwable details | 1 | Implementation |
| [e04b7d4](https://github.com/hanan-bhatti/cobalt/commit/e04b7d430ef8c14c6d558275cff56d859d1a12a3) | refactor(logging): route diagnostics through the redacted logger | 26 | Implementation |
| [0b0c358](https://github.com/hanan-bhatti/cobalt/commit/0b0c358ef92822035990c0116fc0db8ce4772fc7) | feat(security): protect credentials and restrict shared export paths | 8 | Implementation |
| [cf76ee7](https://github.com/hanan-bhatti/cobalt/commit/cf76ee759372765ec560dce7bcf0db9400da7688) | feat(notes): add bounded blocks, generation undo and reference reconciliation | 8 | Implementation |
| [71b7e7f](https://github.com/hanan-bhatti/cobalt/commit/71b7e7f544165b64ca1d9cb6f6ff95443b2496d4) | feat(editor): wire bounded reading, lifecycle journals and cross-section actions | 6 | Implementation |
| [4c7c7da](https://github.com/hanan-bhatti/cobalt/commit/4c7c7da835babe58f35d78f1f018a448316abbb4) | test(android): bound test resources and add debug verification workflow | 4 | Verification record |
| [85a8fd7](https://github.com/hanan-bhatti/cobalt/commit/85a8fd7ea1203020a0c620b90b52a1107e0f341b) | docs: describe note engine security and device-test requirements | 4 | Verification record |
| [d25a225](https://github.com/hanan-bhatti/cobalt/commit/d25a225a561d0227bd6b045bb2da9e7396a6c026) | test(notes): isolate stream round trips from Android filesystem shadows | 2 | Verification record |
| [9f06e1a](https://github.com/hanan-bhatti/cobalt/commit/9f06e1ab62fea1b4bb780e50be5e82531528e3e0) | docs: record verified debug APK and passing note tests | 3 | Verification record |
| [e62f326](https://github.com/hanan-bhatti/cobalt/commit/e62f326c465a7a3c39c1ad269224ecebe9213ab8) | feat(notes): receive and stream text documents safely | 6 | Implementation |
| [bb3f1d9](https://github.com/hanan-bhatti/cobalt/commit/bb3f1d9b2fbcb07e9707a64f76700d90c44802d9) | fix(notes): stage capture edits until explicit save | 3 | Implementation |
| [dee1dce](https://github.com/hanan-bhatti/cobalt/commit/dee1dced6dfbbafc7d7e387a7166524866e62fe9) | refactor(notes): integrate lazy notes into original screens | 11 | Implementation |
| [79db208](https://github.com/hanan-bhatti/cobalt/commit/79db2086269dd1d77286bb759d5d58baba24786a) | docs(notes): record redesign checks and editing limits | 4 | Verification record |
| [42b778e](https://github.com/hanan-bhatti/cobalt/commit/42b778ede5ba353a6c243411c8870a934c243dac) | test(notes): seed legacy emulator migration fixtures safely | 3 | Verification record |
| [76e960b](https://github.com/hanan-bhatti/cobalt/commit/76e960b8b1564041ad7928702e1a43e03024d7ef) | refactor(ui): match migration and recovery to settings | 6 | Implementation |
| [0609da1](https://github.com/hanan-bhatti/cobalt/commit/0609da1fff21177b315dee96d7804f978b801ad8) | test(notes): prepare repeatable debug migration resets | 3 | Verification record |
| [2895551](https://github.com/hanan-bhatti/cobalt/commit/2895551179b5b1a43df392612786b188f518b23f) | docs(ui): record migration redesign and reset checks | 2 | Verification record |
| [5ebf831](https://github.com/hanan-bhatti/cobalt/commit/5ebf831ff671d12c071db23525abdc773d69a671) | chore(ui): preserve shared export and overflow icons | 2 | Build / repository |
| [de1ebed](https://github.com/hanan-bhatti/cobalt/commit/de1ebedd2f2adab8a7e4e39faffe1e942b3a6cf4) | perf(notes): prepare reader content incrementally with bounded caches | 9 | Implementation |
| [5fedf31](https://github.com/hanan-bhatti/cobalt/commit/5fedf317a641d14d399331d93563b9ab6e931ead) | fix(ui): reuse shared note icons and settings navigation | 5 | Implementation |
| [fc1edc9](https://github.com/hanan-bhatti/cobalt/commit/fc1edc99d28887e126eac4ff194fcf98833a537c) | docs(notes): record reader changes and verification limits | 3 | Verification record |
| [de81efa](https://github.com/hanan-bhatti/cobalt/commit/de81efa1397e194ef8498e80944bf0374a8a45e4) | perf(notes): stream file pieces through persistent range handles | 2 | Implementation |
| [e238245](https://github.com/hanan-bhatti/cobalt/commit/e238245ad8edbd75a7c19b641a65507eee807b58) | build(editor): pin native core with license and logging safeguards | 7 | Build / repository |
| [58c2183](https://github.com/hanan-bhatti/cobalt/commit/58c218385e220e225525178df35cef7e239f4a76) | feat(notes): retain continuous documents with verified draft checkpoints | 10 | Implementation |
| [f9b7f3d](https://github.com/hanan-bhatti/cobalt/commit/f9b7f3d72ea84ad688ca7cac3f6cb3c52f3c2f72) | feat(editor): replace capture ranges with continuous native editing | 7 | Implementation |
| [e907264](https://github.com/hanan-bhatti/cobalt/commit/e907264dcdff1adc71ad38a45c3e12def7302184) | fix(reader): retain Markdown rendering and prefetch bounded ranges | 8 | Implementation |
| [ca83c3f](https://github.com/hanan-bhatti/cobalt/commit/ca83c3f9fa056e34a8f86d78dcf77cc57d90fd42) | fix(editor): preserve cursor handoff and recovery generations | 5 | Implementation |
| [926e10b](https://github.com/hanan-bhatti/cobalt/commit/926e10b782549cbaef1d8c50ab621264e5520a1c) | feat(notes): add checksummed edit journal and disk recovery | 5 | Implementation |
| [d659d34](https://github.com/hanan-bhatti/cobalt/commit/d659d3434f7bbb4c4fff7d74601bbc74d739929f) | feat(editor): journal native edits with bounded recovery writes | 6 | Implementation |
| [4ca72a6](https://github.com/hanan-bhatti/cobalt/commit/4ca72a6062fb8c38d3364ffa1f874343fa9f222f) | feat(settings): add storage recovery and explicit copy retention | 10 | Implementation |
| [0b4d5f9](https://github.com/hanan-bhatti/cobalt/commit/0b4d5f9c0268f38adeebf0fb2330e8c4b100011d) | test(editor): cover document release across view disposal | 1 | Verification record |
| [e979934](https://github.com/hanan-bhatti/cobalt/commit/e9799344d90668fe8be64159c2a4ff07a8f63162) | fix(editor): preserve and export drafts after storage failures | 5 | Implementation |
| [45a1256](https://github.com/hanan-bhatti/cobalt/commit/45a1256e7111665cc458ac25fd01695ea00a2f32) | test(editor): cover recovery writer transactions and failures | 1 | Verification record |
| [15f4ec8](https://github.com/hanan-bhatti/cobalt/commit/15f4ec839ac4e4e9c2644a8c8588ae239c75d016) | test(storage): exercise projections with real host atomic moves | 4 | Verification record |
| [958141a](https://github.com/hanan-bhatti/cobalt/commit/958141a7e5a5dccb47189a8cb2d0c87493989f82) | fix(editor): journal preview edits before full checkpoints | 2 | Implementation |
| [6f68c84](https://github.com/hanan-bhatti/cobalt/commit/6f68c845a20c79441b0f1536932b27cc1455a756) | test(editor): wait for layout before IME assertions | 1 | Verification record |
| [c77efcd](https://github.com/hanan-bhatti/cobalt/commit/c77efcdc6f383c8c8eb1928854919aa99dc06669) | test(notes): add resource-limited regression runner | 2 | Verification record |
| [a574aea](https://github.com/hanan-bhatti/cobalt/commit/a574aead3a55adeba9951285dca71e7e2f40ab30) | docs(notes): record recovery storage and regression gate | 5 | Verification record |
| [ad9cebf](https://github.com/hanan-bhatti/cobalt/commit/ad9cebf20442f738654a5bbeefd140950b7e19d7) | fix(editor): reuse verified bodies for unchanged saves | 3 | Implementation |
| [f336db9](https://github.com/hanan-bhatti/cobalt/commit/f336db9817373f076dd740c0872eab4d50ee7e80) | feat(notes): compact saved versions into verified deltas | 8 | Implementation |
| [59988c7](https://github.com/hanan-bhatti/cobalt/commit/59988c772de6116f157001c1a3670aac9d89f6e7) | perf(reader): prioritize visible Markdown over speculative work | 2 | Implementation |
| [c28df70](https://github.com/hanan-bhatti/cobalt/commit/c28df70cba7c1658544d67c198f073c434efc402) | fix(markdown): preserve source bytes when toggling tasks | 3 | Implementation |
| [c8b5e04](https://github.com/hanan-bhatti/cobalt/commit/c8b5e04ccd1db98db7706dd71e1bc62e1d385432) | test(android): support explicit physical debug-device profiling | 1 | Verification record |
| [33df31c](https://github.com/hanan-bhatti/cobalt/commit/33df31c54d02d348aece80b94810903afedad63f) | test(notes): cover delta restoration on Android filesystems | 1 | Verification record |
| [0b437b0](https://github.com/hanan-bhatti/cobalt/commit/0b437b001a0031e3b5feb2ac6bf888cb1dbed3a6) | test(android): reserve recovery space during stress imports | 1 | Verification record |
| [e7e9e5b](https://github.com/hanan-bhatti/cobalt/commit/e7e9e5b1100afcee6a6b77236483dc539dd33676) | fix(ui): resolve dock locale and Scaffold lint errors | 3 | Implementation |
| [fd8b588](https://github.com/hanan-bhatti/cobalt/commit/fd8b58813b388d14f84f9af686fa2b7b1354571b) | test(android): stream stress metadata snapshots with bounded memory | 1 | Verification record |
| [94654a1](https://github.com/hanan-bhatti/cobalt/commit/94654a1c89f4d26fc5947c82e0aff91ad49700e2) | fix(editor): lock visible styling bounds against concurrent edits | 3 | Implementation |
| [f81b5fe](https://github.com/hanan-bhatti/cobalt/commit/f81b5fe5316f4a264f12a30f7fddf442dfe37273) | feat(storage): add recoverable Trash and safe retained-file cleanup | 22 | Implementation |
| [7d2c1cf](https://github.com/hanan-bhatti/cobalt/commit/7d2c1cf96549bc7b9cd3c8d1ebe886015e2b80d0) | feat(ui): redesign Storage with usage breakdown and focused cleanup pages | 9 | Implementation |
| [21d91ee](https://github.com/hanan-bhatti/cobalt/commit/21d91eeaa4dcb960c611eeb39939ed743eedef92) | test(notes): cover saved-history compactor checkpoints and stale requests | 1 | Verification record |
| [66cab6f](https://github.com/hanan-bhatti/cobalt/commit/66cab6fce95043b303408d1380d318c84834cf3a) | chore(storage): document intentional durable-write lint exceptions | 3 | Build / repository |
| [a366e88](https://github.com/hanan-bhatti/cobalt/commit/a366e885fcae497c29bb6d0dfb0da0d09e93e0c2) | docs(storage): record Trash design verification and editor limitations | 5 | Verification record |
| [78875bf](https://github.com/hanan-bhatti/cobalt/commit/78875bf76e9f135f01bf75332ee13a8f23abffc4) | feat(trakt): safe session disconnect with server token revocation, race guards, and legal disclosures | 9 | Implementation |
| [a9e6c4f](https://github.com/hanan-bhatti/cobalt/commit/a9e6c4f295f1544918931950f40af6eab72f5829) | wip: preserve in-progress work from concurrent agent (live editor, markdown blocks, trakt, drawables) | 58 | Implementation |
| [7421f87](https://github.com/hanan-bhatti/cobalt/commit/7421f87e0d8665b8f131d5655ecedd91dfce1508) | feat(fonts): add local FOSS fonts, licenses, fetch script, and FONTS.md | 20 | Implementation |
| [a755e94](https://github.com/hanan-bhatti/cobalt/commit/a755e947600f7eb43ca2a50ccafc96e32a2a07b9) | feat(prefs): add TypographyPrefs model with validation, presets, and SettingsRepository storage | 3 | Implementation |
| [704aab2](https://github.com/hanan-bhatti/cobalt/commit/704aab2cecfaae60a1d0113876d6e951f5ac7a33) | feat(typography): add CobaltTypography provider with font cache, variable font support, and style scalers | 1 | Implementation |
| [7caedaa](https://github.com/hanan-bhatti/cobalt/commit/7caedaa5339521a377dfb98e844865b28ae177d3) | feat(ui): add ExpressiveTypographySliderItem with font endpoint hints, haptics, and TalkBack semantics | 1 | Implementation |
| [492f8f0](https://github.com/hanan-bhatti/cobalt/commit/492f8f0c33cb5145e8c595c04350f868cd63e690) | feat(ui): add Text & fonts subpage with live preview, bottom sheet selectors, and Appearance navigation | 5 | Implementation |
| [e6bfea2](https://github.com/hanan-bhatti/cobalt/commit/e6bfea27cb16217e315c4b7cf19105b91dd1fda0) | feat(notes): integrate LocalCobaltTypography across markdown renderer, code blocks, reader, and editor | 9 | Implementation |
| [4ac77fd](https://github.com/hanan-bhatti/cobalt/commit/4ac77fdab452efba26eafb626c7ae86f32e7138a) | feat(trakt): safe session disconnect credential store and race guards | 4 | Implementation |
| [1eebdf7](https://github.com/hanan-bhatti/cobalt/commit/1eebdf7a5f6a1a62abe42899fa29ffb35b79df0e) | docs(fonts): record verified APK size metrics and byte deltas | 1 | Verification record |
| [1c9dec9](https://github.com/hanan-bhatti/cobalt/commit/1c9dec97954a166539dd00285b78d72ff758a1c5) | feat(trakt, web): implement PKCE auth, Android App Links, public website, and legal sync | 28 | Implementation |
| [ec41050](https://github.com/hanan-bhatti/cobalt/commit/ec41050afcbde559d091f928c637ec0d3437c5d4) | feat(capture): introduce centralized title resolver and localized fallbacks (#21) | 3 | Implementation |
| [757ee85](https://github.com/hanan-bhatti/cobalt/commit/757ee85f3183108b154afafdbc03b4d344f3e7f7) | fix(capture): resolve original display name and guard title at save choke point (#21) | 4 | Implementation |
| [3300045](https://github.com/hanan-bhatti/cobalt/commit/3300045b7f5060265a3c843051acfcc638e5dd91) | fix(ui): apply display guards and prevent path leaks across UI, widgets, and notifications (#21) | 13 | Implementation |
| [b310aa5](https://github.com/hanan-bhatti/cobalt/commit/b310aa56ad5243eadde7333a8970540c840743b8) | feat(storage): add idempotent off-thread legacy title cleanup (#21) | 5 | Implementation |
| [7bdd78e](https://github.com/hanan-bhatti/cobalt/commit/7bdd78e187c1fe998d54f9cc69e53c1376843d6d) | test(capture): add table-driven path leak prevention suite and changelog entry (#21) | 2 | Verification record |
| [bc0719f](https://github.com/hanan-bhatti/cobalt/commit/bc0719f351f401da407d34462b81280ab9ab9623) | feat(editor): redesign markdown toolbar with minimal vector icons and fix keyboard gap | 17 | Implementation |
| [1c4873f](https://github.com/hanan-bhatti/cobalt/commit/1c4873f176c48cf0d18802906717f395469b7f27) | fix(editor): resolve LayoutNode owner crash on save and add blurred floating scrollbar with settings sheet | 13 | Implementation |
| [d195341](https://github.com/hanan-bhatti/cobalt/commit/d195341e0293660538708fe636ccedb500ddeefe) | fix(scrollbar): make floating pill super smooth with frame-paced scrubbing and fix blur on hub and detail screens | 3 | Implementation |
| [7d43358](https://github.com/hanan-bhatti/cobalt/commit/7d433580e33c964afff2ab8d040826aa92632fd4) | perf(home): eliminate per-item entry animations and zero-allocation staggered grid scroll progress | 2 | Implementation |
| [3c8fba1](https://github.com/hanan-bhatti/cobalt/commit/3c8fba11b00564b6d7b533dd70c71eedf6fdf16c) | feat(editor): modernize search & replace and outline bottom sheet in continuous editor | 2 | Implementation |
| [d3d082f](https://github.com/hanan-bhatti/cobalt/commit/d3d082f628b347a9fed5b8f56f4bb26a4fce83c0) | fix(search): allow outside interaction during note search and isolate highlight to matched text | 2 | Implementation |
| [30de809](https://github.com/hanan-bhatti/cobalt/commit/30de809ecd9269cc221a0cbd7a5e25d4d4aa1a4c) | fix(detail): extend note scroll underneath blurred bottom bar and align share button styling | 3 | Implementation |
| [a134fb5](https://github.com/hanan-bhatti/cobalt/commit/a134fb5beef29cafeeb6722a4f52d1bf6665363b) | fix(plain-note): stream 16KB natural paragraph chunks with fillMaxWidth to prevent half-screen wrapping | 2 | Implementation |
| [9aa8eac](https://github.com/hanan-bhatti/cobalt/commit/9aa8eac43fe0dafb5d2994c31c373efd5bee5eae) | fix(markdown): prevent mid-table splitting, expand block limits, and raise link decoration threshold | 3 | Implementation |
| [be1cd58](https://github.com/hanan-bhatti/cobalt/commit/be1cd58dff397fa4877ce8300c2f89a0d355d399) | perf(markdown): dynamic per-block link decoration with 300 link limit | 2 | Implementation |
| [4f0c100](https://github.com/hanan-bhatti/cobalt/commit/4f0c1007e1abd35e2a195efe9b7b687c5e3a6df2) | fix(editor): fix search bar haze, placement & focus, quick note draft flashing, import warning dismissal, and preview save crash | 7 | Implementation |
| [ee5f93f](https://github.com/hanan-bhatti/cobalt/commit/ee5f93f16d5de6cadc6efce05d72cf92f472e306) | fix(capture): show only Edit Note button in preview mode to eliminate button clutter | 2 | Implementation |
| [ad7af1f](https://github.com/hanan-bhatti/cobalt/commit/ad7af1f63f67a37003bb01ae3d14f71346d4a376) | fix(editor): attach hazeSource to editor viewport and enhance search bar frosted blur | 1 | Implementation |
| [75ef53a](https://github.com/hanan-bhatti/cobalt/commit/75ef53a939fd51022e94dbfe0cb37a5a3eb0b8e7) | fix(editor): prevent search bar from losing focus on typing by navigating on action only | 1 | Implementation |
| [8c05f45](https://github.com/hanan-bhatti/cobalt/commit/8c05f451402173a3015bc9a8f1e57164193ec1cf) | fix(editor): prevent validation exceptions from pausing disk editor and expand replace capacity | 2 | Implementation |
| [fe2036b](https://github.com/hanan-bhatti/cobalt/commit/fe2036b764cc44a44678b1e10adaf1303a271073) | fix(editor): eliminate typing flicker by retaining styled spans across text insertions | 1 | Implementation |
| [b7dad1d](https://github.com/hanan-bhatti/cobalt/commit/b7dad1d00d50364da666832aa0a7be00d292bd26) | fix(editor): ensure DiskEditorSession close directly releases file locks | 1 | Implementation |
| [b68f026](https://github.com/hanan-bhatti/cobalt/commit/b68f02645299fb50859981792e254777f7f5c821) | feat(search): chunk 10k replacements, expand search/outline caps, and dual-highlight matches | 13 | Implementation |
| [14ac733](https://github.com/hanan-bhatti/cobalt/commit/14ac733f64fa15711f48d030b4243f47c1827da1) | fix(search): restore 500/1000 limits with 500+/1000+ indicators, and batch replace all occurrences | 7 | Implementation |
| [7958a8d](https://github.com/hanan-bhatti/cobalt/commit/7958a8dbd68cfed858f4cf5426d3bf6b4e885ffb) | fix(editor): complete disk session persistence and recovery | 10 | Implementation |
| [af83e7d](https://github.com/hanan-bhatti/cobalt/commit/af83e7da64f6293ad21ec9d309063484789b2287) | fix(editor): handle input across disk viewport boundaries | 3 | Implementation |
| [724a478](https://github.com/hanan-bhatti/cobalt/commit/724a4782a451749154d30ac597dc6ff7594c773c) | fix(editor): preserve merged native font preferences | 1 | Implementation |
| [0df2b3e](https://github.com/hanan-bhatti/cobalt/commit/0df2b3e31ed50463f291bee3bbfc7a69ac2b474c) | fix(notes): recover retained live drafts without overwriting notes | 4 | Implementation |
| [74e8034](https://github.com/hanan-bhatti/cobalt/commit/74e803448e4ae79339d6dcefaad298744bdbd742) | fix(markdown): preserve nested fence continuation indentation | 2 | Implementation |
| [e95d125](https://github.com/hanan-bhatti/cobalt/commit/e95d125be8ed9c0324c3cab9911f3c42a88eaf0b) | docs(editor): record validation and device acceptance work | 3 | Verification record |
| [111b25d](https://github.com/hanan-bhatti/cobalt/commit/111b25d03f7d7804e93fb4e19b175f0545213c1f) | fix(ui): enable blur/haze settings on editor search bar and eliminate top gap in font settings | 9 | Implementation |
| [f3580af](https://github.com/hanan-bhatti/cobalt/commit/f3580aff832670de195810f6ac8a28ed96888c8f) | fix(notes): update checklist markers without reopening the reader | 5 | Implementation |
| [c66aeba](https://github.com/hanan-bhatti/cobalt/commit/c66aeba6c58013749d4a66047ecbc81f82687cdc) | perf(home): reduce card invalidation and stabilize scrollbar scrubbing | 3 | Implementation |
| [f0beddc](https://github.com/hanan-bhatti/cobalt/commit/f0beddc1c8d121fc52c31144864ad008375c3e2b) | docs(perf): record checklist and Home scroll validation | 2 | Verification record |
| [64551bb](https://github.com/hanan-bhatti/cobalt/commit/64551bb0f64500cc5e33d73ebb64212999c5f162) | fix(feedback,capture): allow submitting consecutive bug reports and prevent keyboard closing during folder creation | 2 | Implementation |
| [a62382c](https://github.com/hanan-bhatti/cobalt/commit/a62382c828c5a0f02ca309f090504b1adf3e5419) | docs(test): add comprehensive manual checklist and recent change index | 2 | Verification record |
| [2bd18d1](https://github.com/hanan-bhatti/cobalt/commit/2bd18d19ee81bb337f8b01217f99c232a0088f23) | fix(links): persist bounded metadata work with one description | 14 | Implementation |
| [b87ef0e](https://github.com/hanan-bhatti/cobalt/commit/b87ef0e18c4d1611dc0df3677f9ae8c19a1be9b5) | fix(dock): keep paste and send input compact and single line | 1 | Implementation |
| [4edc5ab](https://github.com/hanan-bhatti/cobalt/commit/4edc5ab4a6fb745e33704992f50b1b04e8278aff) | fix(dock): grow compact input and expand bounded note previews | 5 | Implementation |
| [df9c510](https://github.com/hanan-bhatti/cobalt/commit/df9c510ab26d739558b8a6dc147099f75be4ee2b) | docs(test): record link and Dock validation and acceptance checks | 4 | Verification record |
| [0145e61](https://github.com/hanan-bhatti/cobalt/commit/0145e618bcdc4e500202b8c1763ed358bbdc2230) | fix(dock): render Markdown previews with a compact action pill | 4 | Implementation |
| [d039c12](https://github.com/hanan-bhatti/cobalt/commit/d039c123ed9e30339056fa1c46da56512f2b10cb) | feat(capture): add gallery and camera source sheets | 2 | Implementation |
| [1913569](https://github.com/hanan-bhatti/cobalt/commit/191356989a0b4b56213af02e8c193ccb337b015d) | feat(fab): add six capture shortcuts with safe radial gestures | 8 | Implementation |
| [94763a5](https://github.com/hanan-bhatti/cobalt/commit/94763a56db674cf0f75d6f43a57a020ad2d6ac60) | feat(editor): add bounded Markdown table transactions | 6 | Implementation |
| [feadae5](https://github.com/hanan-bhatti/cobalt/commit/feadae515c20b8d32132db8a1b35f99f678b505f) | feat(selection): unify reader and editor context toolbars | 17 | Implementation |
| [e39296f](https://github.com/hanan-bhatti/cobalt/commit/e39296f64aaa67da762aac783378561988ff2c31) | fix(capture): keep folder controls above the keyboard | 3 | Implementation |
| [43a22d1](https://github.com/hanan-bhatti/cobalt/commit/43a22d167b5d78930e555ebcd8168e7fb4807450) | fix(editor): insert new tables after closed fences and existing tables | 2 | Implementation |
| [881d965](https://github.com/hanan-bhatti/cobalt/commit/881d965bd30cfa1062e79679a34c717a565d75a2) | fix(migration): skip conversion screen for fresh installs | 3 | Implementation |
| [3127cab](https://github.com/hanan-bhatti/cobalt/commit/3127cabdebf38c5d6e33ec52c5f92a616957b531) | fix(editor): support table header actions and preserve next-cell source | 2 | Implementation |
| [e91fde1](https://github.com/hanan-bhatti/cobalt/commit/e91fde18980fd8b3167d75d94ed0faf2c09c07b0) | feat(editor): keep plain source and continue Markdown lists | 11 | Implementation |
| [9445acd](https://github.com/hanan-bhatti/cobalt/commit/9445acd3996f1a42af361f34d14dfe4e256393a6) | fix(markdown): round inline code and honor table alignment | 2 | Implementation |
| [2bb6651](https://github.com/hanan-bhatti/cobalt/commit/2bb66513813571e3088128835310aa56deb9b5e2) | test(editor): exercise formatting tables and first-install migration | 3 | Verification record |
| [8b778d0](https://github.com/hanan-bhatti/cobalt/commit/8b778d0c42f13aa4ed0976add6af464305fdc4ea) | docs(editor): record validation results and remaining performance gaps | 2 | Verification record |
| [4a99182](https://github.com/hanan-bhatti/cobalt/commit/4a991821a4ccba9eb32b931b29724563b3f17158) | docs(test): record final host pass and emulator disconnect | 1 | Verification record |
| [7242e77](https://github.com/hanan-bhatti/cobalt/commit/7242e77da68a8f8f396d1ae16b7865939867b211) | fix(editor): focus source field from blank body taps | 1 | Implementation |
| [43ec6fe](https://github.com/hanan-bhatti/cobalt/commit/43ec6fe72a51d78b2fc8af85db0062afeb63975f) | style(ui): refresh shared icons and semantic action glyphs | 76 | Implementation |
| [60619a8](https://github.com/hanan-bhatti/cobalt/commit/60619a81216b0ad433ad84bdd79c7f2234eff59b) | fix(gemini): display the configured model name without hardcoded versions | 1 | Implementation |
| [fd2d72c](https://github.com/hanan-bhatti/cobalt/commit/fd2d72c26c362ccc8fda6576304f176b1e0bf33b) | fix(trakt): read TMDB credentials through secret preferences | 1 | Implementation |
| [0132d04](https://github.com/hanan-bhatti/cobalt/commit/0132d047f80435b0c8586e9d644211f01fd8a380) | fix(storage): classify media and distinguish protected recovery data | 2 | Implementation |
| [8ff564e](https://github.com/hanan-bhatti/cobalt/commit/8ff564e7e31bcff82c08550cc447f205bd96f9bd) | feat(home): persist archive filters and refine empty-state capture flows | 11 | Implementation |
| [156947e](https://github.com/hanan-bhatti/cobalt/commit/156947e2ae37ec61ab3456ade0a82c5813709afa) | feat(capture): review incoming shares and retain document import results | 7 | Implementation |
| [237aaf3](https://github.com/hanan-bhatti/cobalt/commit/237aaf3ce1412a3665fd08267d94e8783475796e) | fix(reader): remove the oversized-document warning banner | 1 | Implementation |
| [62cfb22](https://github.com/hanan-bhatti/cobalt/commit/62cfb22dcb18280a4970e0557e3790eea6fe44a5) | feat(share): checkpoint styled link cards and image export | 7 | Implementation |
| [4aac692](https://github.com/hanan-bhatti/cobalt/commit/4aac692d17bad5cf5f22bfddefd34d4a20cc9f10) | test(database): match migration callback parameter names | 1 | Verification record |
| [da09549](https://github.com/hanan-bhatti/cobalt/commit/da095497d5f15c579898a8ceb90ba4a307cbf387) | chore(ide): record the local emulator hardware input setting | 1 | Build / repository |
| [afab8cb](https://github.com/hanan-bhatti/cobalt/commit/afab8cbd23399001b83fdf799d6ed8f00647f2e6) | chore(git): ignore generated Python bytecode | 1 | Build / repository |
| [650c2cf](https://github.com/hanan-bhatti/cobalt/commit/650c2cf67e9231d121a9e7a459b25e57d648d0af) | fix(release): preserve WorkManager input merger constructors | 2 | Implementation |
| [40bd06a](https://github.com/hanan-bhatti/cobalt/commit/40bd06a655aa606fb16d7eae870d820ad62496c1) | test(release): record emulator stress results and release blockers | 1 | Verification record |
| [6ea424f](https://github.com/hanan-bhatti/cobalt/commit/6ea424f62c882341366c0d8640b70e930080825f) | fix(editor): retain table actions and serialize rapid undo redo | 6 | Implementation |
| [714c9cd](https://github.com/hanan-bhatti/cobalt/commit/714c9cd43f35022a5dadd5200eea6527b5c03c1a) | feat(media): add supplied rating logos and source-specific badges | 7 | Implementation |
| [b2ede7b](https://github.com/hanan-bhatti/cobalt/commit/b2ede7b61d9f95cd65fb25366088f7006f815fd7) | test(release): record Trakt round trips and current regression results | 1 | Verification record |
| [50287ee](https://github.com/hanan-bhatti/cobalt/commit/50287ee40b379ee0f280b42002a8051d5c886935) | feat(share): add Link share card and export system with verified themes and screenshot test | 5 | Implementation |
| [9eb06a5](https://github.com/hanan-bhatti/cobalt/commit/9eb06a5fc384082ea5803f09fe2de6ba4aa8ce86) | feat(share): add Image share sheet with dynamic aspect ratio and 100MP downsampling safety | 4 | Implementation |
| [7d4e131](https://github.com/hanan-bhatti/cobalt/commit/7d4e13171f98dc41d2a0f7319d3fb4b1f95e419a) | feat(share): add Movie Poster share with 2:3 theatrical and 16:9 backdrop styles | 6 | Implementation |
| [9c870aa](https://github.com/hanan-bhatti/cobalt/commit/9c870aa99506e37a259e716684437d61790e7442) | feat(media): prevent duplicate movie adds across external IDs and support trash recovery | 5 | Implementation |
| [e77a47e](https://github.com/hanan-bhatti/cobalt/commit/e77a47ef9eec0ef2d71c3c5a14a5ac80414cacd6) | feat(link): show inline notice for existing links and support trash recovery | 1 | Implementation |
| [636d9c7](https://github.com/hanan-bhatti/cobalt/commit/636d9c76c4c742fb5e0be9ca9b8aa0d5413b9d30) | feat(folders): add inline duplicate folder name validation and clean error pill | 6 | Implementation |
| [1ebcda0](https://github.com/hanan-bhatti/cobalt/commit/1ebcda062db3ac28a6706cf56c592b36c97e4041) | fix(share): randomize attribution tagline on sheet launch across all share components | 6 | Implementation |
| [3bf0d76](https://github.com/hanan-bhatti/cobalt/commit/3bf0d764a58ec2932fafc7a24749c2e5d91e663f) | fix(share): fix horizontal swipe gesture in modal bottom sheets by eliminating pre-scroll consumption | 3 | Implementation |
| [ad042d8](https://github.com/hanan-bhatti/cobalt/commit/ad042d8768932fd6dc0b572ed8f9c69caf9368f5) | feat(share): redesign movie share card with frameless dot-separated genres and tightened cinematic layout | 1 | Implementation |
| [1879897](https://github.com/hanan-bhatti/cobalt/commit/18798979519d98dabfd2585e09fbc53a85fcb35d) | feat(share): add safe clipboard copy limits and clean markdown file export | 1 | Implementation |
| [5c90e52](https://github.com/hanan-bhatti/cobalt/commit/5c90e52128f424675e96e4b09b52c97ce1cf8485) | feat(share): add Note share card with clean markdown, compact tables, and variable dimensions | 4 | Implementation |
| [f674046](https://github.com/hanan-bhatti/cobalt/commit/f67404671c79789e86441579525c523fb8515327) | feat(share): add Note share bottom sheet with 4k image export, md file export, and clipboard guard | 3 | Implementation |
| [312274a](https://github.com/hanan-bhatti/cobalt/commit/312274a273c483d373fe04246be6c889e846fbff) | test(share): add Roborazzi screenshot test suite for Note share cards | 1 | Verification record |
| [0741d97](https://github.com/hanan-bhatti/cobalt/commit/0741d97bb983da34461f4f0064a546f30c05d5dd) | feat(share): redesign Link share sheet with floating window and connected control toolbar | 2 | Implementation |
| [58da5a7](https://github.com/hanan-bhatti/cobalt/commit/58da5a7c744516e35d8490243d8cec697c31d14b) | feat(share): redesign Image share sheet with floating window and connected control toolbar | 1 | Implementation |
| [4df9473](https://github.com/hanan-bhatti/cobalt/commit/4df94737aed4933e4160d66e4a461a18fdde0647) | feat(share): redesign Movie share sheet with floating window and connected control toolbar | 1 | Implementation |
| [8c094f9](https://github.com/hanan-bhatti/cobalt/commit/8c094f98c71eb35725d7f85a3d82cff1f88ca977) | feat(share): redesign Note share sheet with floating window and connected control toolbar | 1 | Implementation |
| [9e4b66f](https://github.com/hanan-bhatti/cobalt/commit/9e4b66f43d530c3d57558077c8cb147173a5f79e) | feat(storage): add durable media metadata with safe legacy upgrades | 12 | Implementation |
| [84f609f](https://github.com/hanan-bhatti/cobalt/commit/84f609ff1728779fd72202850d6cabfc17d223db) | fix(trakt): persist account-bound sync intents and resume batches safely | 14 | Implementation |
| [0fa55ab](https://github.com/hanan-bhatti/cobalt/commit/0fa55ab8a3562df090b5f56b705d4c9fb0f0002c) | feat(media): unify provider settings and anime manga search identities | 15 | Implementation |
| [3e93268](https://github.com/hanan-bhatti/cobalt/commit/3e932687c91466acee53bda4fd89a872e33eded3) | feat(media): add inline ratings and cached season episode pager | 9 | Implementation |
| [b904ad3](https://github.com/hanan-bhatti/cobalt/commit/b904ad378d4578c0feaac174ff7c49cbaf2ce723) | fix(trash): cancel media uploads and retain deleted provider identities | 2 | Implementation |
| [3d332cb](https://github.com/hanan-bhatti/cobalt/commit/3d332cb3af725b6cb39eac65c5a18d451e035deb) | fix(storage): distinguish retained edit data from reclaimable cache | 5 | Implementation |
| [718903f](https://github.com/hanan-bhatti/cobalt/commit/718903f42e7b66b67e907baab8823d8b0f2110ee) | perf(ui): skip haze source capture when blur is disabled | 1 | Implementation |
| [378036f](https://github.com/hanan-bhatti/cobalt/commit/378036f0c6d290beb2e08f4b571fd12867cc65cb) | fix(ui): dismiss delete undo on navigation and show timed border | 4 | Implementation |
| [38ef4a5](https://github.com/hanan-bhatti/cobalt/commit/38ef4a59539bddf4cc4de847f29673f9c3660233) | feat(share): redesign link, image, and note share sheets with floating hero cards | 4 | Implementation |
| [98f6978](https://github.com/hanan-bhatti/cobalt/commit/98f69788988731b2530ac6f2ee231800a286932f) | feat(share): unify note sharing with Document, Minimal, and Compact styles powered by native Markdown engine | 6 | Implementation |
| [922177f](https://github.com/hanan-bhatti/cobalt/commit/922177f686b646f7065f649c0866bd1207f4c492) | feat(share): redesign movie share sheet with floating hero card and remove redundant note keyword | 2 | Implementation |
| [1667d6a](https://github.com/hanan-bhatti/cobalt/commit/1667d6a5c8b6bfb6ecf21cbdefe98d778d3e4ab6) | fix: harden media integrations and refine note rendering and sharing | 65 | Implementation |
| [00ff03e](https://github.com/hanan-bhatti/cobalt/commit/00ff03eaf7a805e81023f1ae28d34cc749ba1f90) | fix(links): fill missing social metadata and verify capture previews | 3 | Implementation |
| [dbeeb98](https://github.com/hanan-bhatti/cobalt/commit/dbeeb988b6827f0195e98a3c58e25d68506b4db8) | feat(notes): optimize note storage engine with shared trees and incremental disk persistence | 51 | Implementation |
| [13a2d72](https://github.com/hanan-bhatti/cobalt/commit/13a2d72ec40fd729435e95191b5785b0ec57aa5f) | fix(home): calculate media file sizes accurately for size-based sorting and filtering | 3 | Implementation |
| [2315788](https://github.com/hanan-bhatti/cobalt/commit/2315788602464ae8649df2c73b30e54850d042d5) | style(media-hub): align filter title, subtext, and progress status pill styling | 1 | Implementation |
| [3c64b54](https://github.com/hanan-bhatti/cobalt/commit/3c64b54b811e3202d5341dc0f74b9b8863ea1960) | feat(markdown): support ATX headings, horizontal rules, custom tab links, and inline code | 1 | Implementation |
| [2242e1e](https://github.com/hanan-bhatti/cobalt/commit/2242e1ed6611940624bf0484bf9d1066b64bf74d) | feat(licenses): add comprehensive open source libraries directory and fix markdown layout | 1 | Implementation |
| [e6c45da](https://github.com/hanan-bhatti/cobalt/commit/e6c45dabc13326af38032fe4cc02b59eef0e1049) | style(settings): simplify app version footer to clean minimal typography | 1 | Implementation |
| [52fb8bf](https://github.com/hanan-bhatti/cobalt/commit/52fb8bfff55f9cb922e09d4fea7626f75bef96e0) | feat(updates): redesign release updates with dynamic GitHub changelog and release notes | 8 | Implementation |
| [3e716d7](https://github.com/hanan-bhatti/cobalt/commit/3e716d7d0544b6ba80990ac6e582405167eba9c5) | feat(storage): implement skeleton shimmer loading and remove all linear progress indicators | 8 | Implementation |
| [55049aa](https://github.com/hanan-bhatti/cobalt/commit/55049aaf564ac51a9de92b85aac8902992877a6b) | feat(storage): redesign storage screens with Material 3 morphing pull-to-refresh, transparent copy, and unified visual hierarchy | 8 | Implementation |
| [1839528](https://github.com/hanan-bhatti/cobalt/commit/18395288f623d793d18fd5380f8a8598bca44d50) | fix(storage): add missing imports and resolve drawable references for release build | 7 | Implementation |
| [34fc4b8](https://github.com/hanan-bhatti/cobalt/commit/34fc4b8d9839427345b4e8cc5252def1ebd434a0) | feat(storage): redesign disposable confirmation into expressive bottom sheet and structure cache maintenance hierarchy | 3 | Implementation |
| [ac67eb8](https://github.com/hanan-bhatti/cobalt/commit/ac67eb81e0d235128c2770e4354bb2dbf7a91c11) | docs(storage): update shared note storage validation record | 1 | Verification record |
| [2e0aea4](https://github.com/hanan-bhatti/cobalt/commit/2e0aea48aa70111fca26543267dbe19947d4ee5b) | fix(storage): add ExperimentalMaterial3Api opt-in for storage bottom sheet | 2 | Implementation |
| [29d0e9b](https://github.com/hanan-bhatti/cobalt/commit/29d0e9b8d1306d9a1822216dc0637c5f78976802) | feat(feedback): redesign reporter header with minimal version tag and fix share icon mismatch across feedback and survey | 4 | Implementation |
| [6669da6](https://github.com/hanan-bhatti/cobalt/commit/6669da603c5775bd7ab51ecefb6934c21952d8ac) | fix: replace deprecated rememberModalBottomSheetState and Locale constructor | 2 | Implementation |

## Commit bodies

Commit bodies with additional explanation are retained below; blank bodies are represented by their table entry.

### 78875bf

- Add bounded server-side OAuth token revocation to POST /oauth/revoke on Trakt disconnect
- Ensure complete local credential and session clearing even if offline or server times out
- Cancel background WorkManager sync and active device auth polling upon disconnect
- Invalidate session epoch to prevent in-flight token refresh, poll, or sync responses from applying
- Guard outbound mutations against in-flight disconnect while preserving local SQLite updates
- Preserve custom Client ID and Base URL settings across disconnects
- Add SecretPreferences test fallback key for platform-independent JVM/Robolectric test execution
- Add comprehensive unit tests in TraktDisconnectTest covering all 8 disconnect and race scenarios
- Update in-app LegalDocs (Privacy Policy, Terms, FAQ) and NETWORK.md with verified implementation disclosures

### e6bfea2

- Bind MarkdownRenderer paragraphs, headings, lists, tables, and inline code to LocalCobaltTypography
- Update MarkdownCodeBlock to use LocalCobaltTypography.current.codeBlock
- Dynamic typeface and text size updates for ContinuousNoteEditor and RichTextEditor
- Key MarkdownReadSession in LazyMarkdownNote by typographyKey to invalidate layout on metric changes
- Add TypographyPrefsTest (clamping, presets, variable font detection, static weight snapping)
- Add TextAndFontsSettingsScreenTest for preview card and settings controls

### 64551bb

- FeedbackScreen.kt: provide 'Report Another' and 'Done' actions after submitting a bug report or feature request, and reset submitSuccess state on input edit (#23)
- CaptureScreen.kt: prevent inline folder creation from unmounting when soft keyboard opens by checking (!isKeyboardOpen || showNewFolderCreator) (#24)

### 1667d6a

Repair Trakt progress imports, durable account changes, and shared metadata hydration. Improve provider settings, source ratings, manga support, and inline trailer playback.

Include note and link rendering changes, regression coverage, live validation tools, and reports.

