# Screening algorithm and Your taste · implementation and validation

Implemented on 14 November 2026 as `local-taste-v2`. The local recommender now learns from canonical widget thumbs, personal ratings and retained Screening responses. The native **Your taste** screen exposes the same evidence and scoring used by recommendations. Its overview shows **five leading genres + Others**, while every normalized genre remains in the model.

The current debug build is installed on `emulator-5554` as **Cobalt Debug** (`com.hanan_bhatti.cobalt.debug`). Entry: **Widgets → Screening Card → Your taste**, or **Why this pick?** from the in-app recommendation preview.

## What changed

- Resolve local, TMDB, Trakt and IMDb aliases before counting ratings, completions or reactions. Metadata enrichment preserves preferences and widget identity.
- Keep one reversible thumb per canonical title. Repeated taps clear it; switching replaces it. Thumbs now influence subsequent title and genre scores.
- Use the latest retained Useful/Not interested opinion per title, preventing repeated questions from adding unlimited preference votes.
- Store imported ratings without invented dates. A later observed rating change has an observation date, with that provenance identified.
- Select using explicit diversity-adjusted points and record the actual weighted shortlist. Same inputs and seed remain stable; Next does not become negative feedback.
- Count successful publications, rather than delivery reservations, for fatigue. Retained cards preserve their original short reason and score trace. Same-day reopening reuses an unanswered review question.
- Install learning schema version 3 additively, independently of the archive and note storage.

The approved full-artwork Screening card with floating thumbs is unchanged. Compared with the captured implementation baseline, its artwork cache and renderer have no changes in this pass.

## Learning and ranking

| Evidence | Reward and influence |
| --- | --- |
| Personal rating, 1–10 | Reward `(rating - 5.5) / 4.5`, evidence amount `1`; current saved ratings do not decay. |
| Finished title without a rating | Weak reward `.15`, evidence amount `.5`. |
| Active Like / Not for me thumb | Reward `+1 / -1`, evidence amount `1.25`, with a 60-day half-life where a date is known. |
| Latest retained Useful / Not interested opinion | Reward `+1 / -1`, evidence amount `.65`, with a 60-day half-life. |
| Other responses, opening details, unanswered questions | No taste reward. Explicit responses can still affect suppression or actual confirmed watch status. |

For each genre, `weight = sum(reward × evidence amount) / (3 + sum(evidence amount))`. The title's genre contribution is `18 × average genre weight`. An active thumb also contributes up to `+6` or `-10` direct title points, decayed using the same reaction age.

Existing watch-status/progress, saved-title age, runtime and eligibility rules remain separate from inferred taste. A thumb dislike is a soft ranking preference; **Not interested** from a question remains a reversible hard hide. **Maybe later** suppresses a title for seven days.

Candidates are reranked for genre/purpose variety and selected from a shortlist of up to eight using `max(1, diversity-adjusted points)` as the stable weighted selection utility. Ranking points are relative quantities, not enjoyment probabilities.

All genre labels are normalized and retained. Overview ordering uses absolute preference strength, then distinct contributing-title count and genre name. Others contains the remaining individual genres and a count; it has no combined preference score. Missing evidence is displayed separately from balanced positive and negative feedback.

## Transparency and corrections

The native screen provides genre evidence sheets, source/date labels, current influence, recent recorded changes, score details, the last published explanation and a saved-title eligibility search. Missing genre metadata is labelled explicitly, without a dangling influence label or an invented genre score. Profile, evidence and ranking use the shared `ScreeningService.insights()` snapshot. Reading/reopening insights adds no preference, reservation or publication/fatigue event.

Users can ignore/restore a saved rating's recommendation influence, clear a thumb, clear a title's recommendation filter, or reset Screening feedback. These controls preserve archive ratings, episode progress and notes. Reset clears reactions, question evidence, rating exclusions, suppressions and publication/fatigue state; saved ratings guide the rebuilt profile. Outstanding questions become answered so stale actions cannot restore cleared feedback; daily reservations remain.

Question evidence is retained for up to 180 days and 500 responses. The UI reads up to 200 recorded changes and publications. Active preferences, aliases and rating exclusions are stored independently of the bounded history.

## Algorithm baseline verification

These results were recorded before the subsequent UI redesign. The current presentation was checked separately with eight unattended Android UI tests and a full-window 320 dp / 1.6-font-scale test covering 60 genres. See the [UI redesign validation](screening-ui-redesign-validation-2026-11-14.md) for the current screenshots and installed APK hash.

**88 host tests passed**, with zero failures, errors or skipped cases. Coverage includes engine/store behavior, 60 genres, identity aliases, migration, reversible feedback, sparse/conflicting evidence, dates, reset, trace reconciliation, read-only insights, actual Room/service archive preservation and six Compose UI checks.

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'com.example.media.recommendation.*' \
  --tests 'com.example.widget.ScreeningWidgetTest' \
  --tests 'com.example.ui.screens.ScreeningTasteUiTest' \
  --offline --console=plain
```

**Eight distinct Android tests passed on the user's emulator**; the persistence test ran in two separate instrumentation processes with a force-stop between them:

- Three notification/confirmed-progress checks: idempotent real notification action, preserved episode history, and rejected actions for removed titles.
- Two widget checks: real Like/switch/clear PendingIntents and Android wallpaper palettes in both app modes.
- One fractional movie-progress rendering check.
- One 60-genre Your taste flow at 320 dp and `1.6` font scale, including the last genre's evidence and undated rating label.
- One persistence check: canonical reaction, rating exclusion, publication reason/trace and publication count survived a new process. Clearing/restoring and reset worked afterward; a stale question was rejected, and the fixture's actual Room rating, progress and note body stayed identical.

Persistence fixtures use their own database/preferences and archive file. Interaction-test cleanup removes only generated Screening fixture IDs, including their v2 learning/history rows.

The application and test APKs assembled successfully. The six host Compose checks passed again after the final missing-genre display fix. Cold-launch navigation to the native Your taste route was inspected, and the large-text evidence screenshot was visually reviewed. The shared settings header can ellipsize long subtitles at enlarged text; body descriptions and actions wrap.

| Evidence | Location |
| --- | --- |
| Full 88-test host results | `app/build/reports/screening-algorithm/host-tests-88/` |
| Latest targeted UI recheck | `app/build/test-results/testDebugUnitTest/` |
| Light/dark, Others and score screenshots | `app/build/reports/screening-taste/` |
| Native 320 dp / large-text evidence | [Screenshot](../design/screening-insights/validation/screening-taste-device-evidence.png) |
| Separate-process persistence checks | [Seed log](../design/screening-insights/validation/persistence-seed.txt), [verify log](../design/screening-insights/validation/persistence-verify.txt) |
| Android interaction regression checks | [Log](../design/screening-insights/validation/android-regression.txt) |
| Debug application APK | `app/build/outputs/apk/debug/app-debug.apk` |
| Android test APK | `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` |

Algorithm-baseline debug APK SHA-256: `c65cccb88afb578e0a88cd667bb7a38ae31b1eddf78b739531af2f60ee5d4201`.

To rerun the persistence check, invoke `ScreeningLearningDeviceTest` with instrumentation argument `screeningTastePhase=seed`, force-stop `com.hanan_bhatti.cobalt.debug`, then invoke it with `screeningTastePhase=verify`. The test asserts that the process ID changed. Without these explicit phases it is skipped by a normal test-suite run.

## Figma

[Current seven-view Android design](https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI?node-id=293-2528) · [Playable prototype](https://www.figma.com/proto/dJOugcd2V82nQUA1MjyINI?node-id=293-4386&starting-point-node-id=293%3A4386).

The subsequent redesign keeps five genres plus Others and updates Current, ignored-rating and reset states. Source dates, learning numbers and score contributions open in separate disclosures. The final review checked 553 prototype actions with valid destinations and no horizontal child overflow. The main board contains 205 editable text layers, 142 instances and 298 vectors, with zero image-filled UI nodes. Product typography remains Noto Serif/Roboto. Superseded dense components and correction concepts are archived on the same page.

See the [current handoff](screening-your-taste-figma-handoff-2026-11-14.md), [final composition](../design/screening-insights/redesign-20261114/figma-final.png), and [structural validation](../design/screening-insights/redesign-20261114/figma-final-validation.json). The example holds age, runtime and the previous-suggestion contribution constant to illustrate taste changes. Actual runtime reset also clears exposure state, so example reset totals are not guaranteed post-reset scores.

## Remaining evaluation work

The profile adapts to new feedback; weights and decay constants are fixed in this version. These checks establish behavior and explanation fidelity, without claiming a measured improvement in recommendation usefulness or enjoyment. The [research plan](screening-recommendation-research-2026-11-14.md) covers later quality measurement, temporary user intent, richer metadata/similarity and bounded automatic tuning. Manga and new-catalog discovery remain outside this saved-movie/TV recommender.
