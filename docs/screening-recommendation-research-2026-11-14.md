# Screening recommendation research and optimization plan

Reviewed and updated 14 November 2026. Status: platform research, historical pre-implementation audit, and future evaluation plan. The `local-taste-v2` recommender and Your taste screen have since been implemented; see the [implementation and validation report](screening-algorithm-validation-2026-11-14.md) for current behavior and checks. The baseline findings below are preserved as the rationale for those changes.

## Recommendation

Build an explainable local recommender that helps someone choose a saved title they want to start or resume. Prioritize reliable feedback, useful variety, and clear user controls. Measure explicit recommendation usefulness and confirmed starts/resumes; treat completion as a separate outcome rather than proof of enjoyment.

The first implementation should connect the existing widget thumbs to learning and introduce a shared explanation record for the widget, recommendation preview, and a new **Your taste** screen. Improve ranking and selection against that same record. Add richer similarity and automatic parameter adaptation in later stages, after collecting usable evidence.

## Scope and evidence

This pass covers Netflix, YouTube, Instagram, Facebook, Threads, TikTok, X, LinkedIn, Pinterest, Snapchat, Reddit, Spotify, Amazon Prime Video, and Bluesky. Sources are official help pages, engineering publications, research papers, and published code. This is broad comparative research rather than a claim to reproduce every platform's complete live system. Public papers describe particular systems at particular dates; operational models and objectives can change.

Older architectural papers are identified as historical. Search-result dates are not publication dates: for example, Amazon's metadata paper is listed by its owning publication page as **2021**, despite a search result suggesting a recent date. TikTok's current help page did not expose readable article text, so the substantive review used its accessible official 2020 explainer.

## Platform comparison

The findings column summarizes published behavior. The Cobalt column is our proposed adaptation, not a claim that the platforms use Cobalt's formula.

| Platform | Published approach and learning | Adaptation for Cobalt |
| --- | --- | --- |
| Netflix | Viewing history, ratings, title metadata, similar viewers and session context influence recommendations. More recent interactions can outweigh older ones. Its March 2025 foundation-model work centralizes preference representations; July 2026 GenRec work combines catalog-constrained scoring with ranking-specific training and offline/online evaluation. [Help](https://help.netflix.com/en/node/100639), [2025 model](https://netflixtechblog.com/foundation-model-for-personalized-recommendation-1a0bd8e02d39), [2026 GenRec](https://netflixtechblog.com/genrec-towards-llm-native-recommendation-at-netflix-f20be6f643e3). | Separate durable taste from temporary intent. Weight meaningful actions carefully. Keep recommendations inside the eligible library. Evaluate changes rather than assuming a larger model helps. |
| YouTube | Current documentation distinguishes watch history, subscriptions, likes, dislikes, explicit dismissal, and satisfaction surveys. Signals differ by surface. The 2016 paper describes separate candidate generation and ranking models; it is an architectural reference, not a specification of today's full system. [Current explanation](https://support.google.com/youtube/answer/16089387?hl=en), [2016 paper](https://research.google/pubs/deep-neural-networks-for-youtube-recommendations/). | Separate preference, recommendation usefulness, and consumption status. Use explicit feedback as a quality measure. A widget tap that opens details is not evidence that the movie was watched. |
| Instagram | The 2023 Explore publication describes retrieval, lightweight ranking, heavier ranking, and final reranking. Multiple predicted actions contribute to value. Its 2025 notification framework penalizes similarity to recently delivered notifications across content, author and surface. [Explore](https://engineering.fb.com/2023/08/09/ml-applications/scaling-instagram-explore-recommendations-system/), [notifications](https://engineering.fb.com/2025/09/02/ml-applications/a-new-ranking-framework-for-better-notification-quality-on-instagram/). | Keep eligibility, ranking, diversity and delivery separate. Variety should consider recent suggestions over time, not only neighboring positions in a freshly computed list. |
| Facebook | Meta describes combining behavioral predictions and survey feedback, and publishes system cards explaining ranking stages and user controls across Facebook and Instagram surfaces. [Official explanation](https://about.fb.com/news/2023/06/how-ai-ranks-content-on-facebook-and-instagram/). | Pair an understandable overview with an explanation attached to the actual recommendation and controls that change it. |
| Threads | The February 2026 Dear Algo feature lets users temporarily request more or less of a topic; the documented request lasts three days. It is a user-control example rather than a disclosure of the complete ranker. [Official feature](https://about.fb.com/news/2026/02/threads-dear-algo/amp/). | Allow a temporary request such as wanting a shorter film tonight without rewriting long-term preferences. Use simple local controls before considering natural-language input. |
| TikTok | Its official explainer describes interaction signals, video metadata, differently weighted signals, initial interests, and explicit Not Interested controls. Completion of a longer video is presented as stronger evidence than shared country. [Official explainer, 2020](https://newsroom.tiktok.com/how-tiktok-recommends-videos-for-you?lang=en). | Distinguish strong and weak observations. Cobalt cannot observe Netflix playback or continuous social-feed scrolling, so do not import those assumptions into its feedback model. |
| X | The reviewed public repository combines multiple retrieval sources, transformer predictions for several actions, weighted scoring, separate filters and diversity adjustments. Its README explicitly distinguishes weights on predicted actions from weights on raw engagement counts. [Published implementation](https://github.com/xai-org/x-algorithm). | Preserve the distinction between eligibility, raw score, adjustments and final selection. Explain exactly which quantity a number represents. |
| LinkedIn | The reviewed engineering article describes semantic retrieval and sequence-based ranking. It emphasizes curated engagement history, feature representation, freshness, and reducing infrastructure complexity. [Engineering publication](https://www.linkedin.com/blog/engineering/feed/engineering-the-next-generation-of-linkedins-feed). | Better signal quality comes before more data or a larger model. Use richer title representations only when available and demonstrably helpful. |
| Pinterest | Its April 2026 publication separates per-item ranking from feed composition. It reports that removing diversity can improve immediate actions but harm subsequent engagement, and describes multiple diversity techniques. [Engineering publication](https://medium.com/pinterest-engineering/evolution-of-multi-objective-optimization-at-pinterest-home-feed-06657e33cd10). | Protect variety as its own objective. Test repeated recommendations and genre coverage over a sequence, alongside usefulness. |
| Snapchat | Spotlight documentation describes content labels, favorites, subscriptions, sharing, hides, viewing signals, predicted actions, and adjustments for a diverse result. [Official explanation](https://help.snapchat.com/hc/en-us/articles/8961653169940-How-We-Rank-Content-on-Spotlight). | Combine title understanding with explicit preferences. Preserve clear distinctions between hiding a title and lowering affinity for similar titles. |
| Reddit | Its October 2026 help article describes candidate generation, filtering, predictive ranking, diversity, and controls such as fewer similar posts, community muting and personalization settings. [Official explanation](https://support.reddithelp.com/hc/en-us/articles/23511859482388-Reddit-s-Approach-to-Content-Recommendations). | Make exclusions inspectable and reversible. Explain why a saved title is absent as well as why a title was selected. |
| Spotify | October 2026 documentation describes a taste profile, content characteristics, trends and feedback. Available controls include excluding content from taste and, in a beta with limited availability, inspecting a taste snapshot and adding notes. [Official explanation](https://www.spotify.com/us/safetyandprivacy/understanding-recommendations/plain). | Show the learned profile and its evidence. Let users remove an influence rather than requiring a complete reset. |
| Amazon Prime Video | The 2021 paper studies two-tower recommendation using categorical metadata, synopsis and artwork to improve recommendations for titles with little or no viewing history. Its experiments use proprietary data. [Publication](https://www.amazon.science/publications/exploring-heterogeneous-metadata-for-video-recommendation-with-two-tower-model), [paper](https://cdn.amazon.science/1e/e6/4d7f8a2741a4a3b148e20a953946/exploring-heterogeneous-metadata-for-video-recommendation-with-two-tower-model.pdf). | Start with metadata available locally for new titles. Actors, directors, themes or embeddings require additional structured metadata; do not invent them from current genre fields. |
| Bluesky | Custom feeds expose algorithmic choice. Users can replace a default feed with feeds suited to their intentions; the 2023 publication describes its initial implementation. [Official explanation](https://bsky.social/about/blog/7-27-2023-custom-feeds). | Offer understandable intents such as Balanced, Continue, or Rediscover once the base ranker is reliable. Users should have a useful default and a clear way to steer it. |

## What transfers to a small local app

1. A pipeline with explicit stages makes both optimization and explanations easier.
2. Explicit liking, usefulness feedback, temporary snoozes and confirmed progress have different meanings.
3. Taste changes; recent evidence needs appropriate influence without letting one action erase a stable preference.
4. Repetition and narrow coverage are quality problems even when an individual title has a high score.
5. Learning needs both reliable observations and a testable objective. Counting clicks alone does not establish usefulness.
6. Explanations and corrections belong in the same product flow. A chart without evidence or controls provides limited transparency.

These are our synthesis of the sources. Large-scale collaborative filtering, distributed retrieval services, and foundation-model training are not the first optimization targets for Cobalt's current local library.

## Baseline behavior reviewed before local-taste-v2

This section records the behavior at the initial audit, before implementation. The [engine](../app/src/main/kotlin/com/example/media/recommendation/ScreeningRecommendationEngine.kt) continues to rank titles already saved in the archive, without searching Netflix's catalog or learning from other users. Widget feedback and the diversity/selection mismatch described here are now addressed in v2.

1. **Build a genre profile.** Personal ratings from 1 to 10 become signed evidence: `(rating - 5.5) / 4.5`. A completed title without a rating supplies much weaker positive evidence. Useful and Not interested question responses add positive or negative evidence whose influence halves every 60 days; saved ratings currently have no such decay.
2. **Filter eligible titles.** Remove unavailable, archived, deleted, completed, paused, explicitly rejected, temporarily suppressed and unsupported titles. Exclude TV shows that are caught up or unreleased. A two-day suggestion cooldown applies where enabled; widgets use their own cycle.
3. **Score each title.** Start with 20 points. Add one purpose bonus: 25 for the last one or two released episodes, 20 for continuing, 18 for an older untouched title, or 12 for the rest of the watchlist. Add genre preference, saved-title age and the applicable progress-recency bonus; subtract repetition fatigue. An unwatched movie of at most 100 minutes gets 3 extra points.
4. **Arrange variety and select.** Rerank up to 200 candidates for genre/purpose variety, then use a stable weighted selection among the first eight eligible choices. Refreshing with the same inputs and seed is stable; Next changes the selection without recording a dislike. The diversity/weight mismatch described below needs correction.

For a genre, the learned weight is `sum(reward × evidence weight) / (3 + sum(evidence weight))`. The added 3 shrinks sparse evidence toward neutral. A title's taste contribution is 18 times its average genre weight. These are relative ranking quantities, not probabilities of enjoyment.

**Baseline adaptation:** new ratings, question responses and actual status/progress updates changed the evidence and therefore future recommendations. The separate widget thumbs did not influence that calculation; v2 now connects them. Scoring constants, signal weights and the decay period still do not automatically tune themselves. Opening details, unanswered questions and elapsed time do not establish that a title was watched or liked.

## Historical baseline audit

Owning sources: [ranker](../app/src/main/kotlin/com/example/media/recommendation/ScreeningRecommendationEngine.kt), [service](../app/src/main/kotlin/com/example/media/recommendation/ScreeningService.kt), [learning store](../app/src/main/kotlin/com/example/media/recommendation/ScreeningStore.kt), [widget selection](../app/src/main/kotlin/com/example/widget/ScreeningWidgetModel.kt), [thumb store](../app/src/main/kotlin/com/example/widget/ScreeningWidgetStore.kt), [provider](../app/src/main/kotlin/com/example/widget/ScreeningWidgetProvider.kt), [saved metadata](../app/src/main/kotlin/com/example/data/model/SavedItem.kt).

| Finding | Consequence | Proposed correction |
| --- | --- | --- |
| Widget thumbs are stored separately and are not consumed by `ScreeningService.ranked`. | Visible feedback does not yet personalize future scores. | Migrate to a canonical, reversible reaction model that ranking and the screen both read. |
| Genre evidence is accumulated before duplicate candidates are grouped. | Duplicate copies can contribute a rating or completion more than once. | Resolve identities and the authoritative rating before accumulating evidence. |
| Feedback events have time decay; current saved ratings do not have rating timestamps in `SavedItem`. | We cannot truthfully display when an old rating happened or perform a full historical replay from current values. | Add provenance and event timestamps prospectively; mark migrated history as undated instead of assigning the migration date. |
| Multiple Useful responses for one title can accumulate as separate genre evidence. | Repeated questions may overpower diverse evidence. | Cap repeated usefulness influence per title/time window and keep it distinct from movie enjoyment. |
| `features` reconcile the raw score, but the diversity pass returns recommendations with their original scores. `choose` samples the top eight using those original scores. | Diversity affects shortlist membership, but does not change final sampling weights. For a pool of eight or fewer, a reorder alone cannot change those weights. | Keep an explicit diversity-adjusted utility and consume it during selection; trace both stages. |
| Widget refresh bypasses the two-day exposure cooldown and tracks its own per-day seen cycle; widget publication does not use `createDelivery`. | Exposure behavior differs by surface. Existing global fatigue is not a complete widget-delivery history. | Record unique published suggestions per surface while retaining same-day stability. A background rerender is not a new suggestion. |
| `createDelivery` increments shown counters before notification posting is confirmed. | A reservation can be mistaken for successful delivery. | Separate reserved, published and opened events. Label published widgets as suggested, not necessarily seen. |
| The explanatory `reason` is mostly chosen from a status/age bucket. | Strong genre evidence may affect a score without appearing in the reason. | Derive short reasons from the same structured contribution record used by the score breakdown. |
| Fractional movie playback is attached after ranking for display. | The progress line is not a ranking feature. | If a resume feature is added, use an actual, account-scoped saved position with freshness; do not infer completion from it. |
| Manga is not an eligible ranking kind yet. | A selectable source can have no ranked results. | Add a reading-specific adapter later; do not treat chapters as TV episodes. |
| The engine ranks saved records and has no public-metadata fields for cast/director embeddings. | A plan cannot promise catalog discovery or creator-level learning using current data alone. | Preserve saved-title scope first; add metadata capability explicitly before richer similarity. |

## Implemented pipeline and next stages

`Archive + explicit feedback -> identity resolution -> eligibility -> preference evidence -> title scoring -> diversity/intent adjustments -> selection -> explanation + publication record`

The same decision object must power every surface. Reading Your taste must not create a delivery, add fatigue, or invent a viewing event.

### Stage 1: implemented feedback and explanations

The following base capabilities are implemented and behavior-tested. Canonical thumbs, deduplicated ratings, timestamp provenance, reversible corrections, structured scoring, publication records and five-genres-plus-Others all share the same calculations. Recommendation-quality evaluation and automatic weight tuning are separate future work.

- Define separate records for title preference, recommendation usefulness, temporary suppression, and actual consumption status.
- Canonicalize movie/TV/provider identities, normalize genre labels, and count each authoritative title rating once.
- Store one active Like/Not for me state per canonical title. Repeating the same reaction clears it; switching replaces its influence. Keep an audit trail without counting repeated taps as extra votes.
- Retain Useful/Not interested semantics for existing questions, but expose their different effects and provide a correction path for old permanent exclusions.
- Preserve legacy values and aliases. An undated rating or a pruned event remains undated or unavailable in the screen.
- Extract a versioned profile and explanation snapshot from the actual score calculations. Separate raw ranking points, diversity adjustment, shortlist membership, and final choice.
- Repair diversity/selection semantics. Maintain deterministic refresh behavior and independent widget cycles.
- Build the [Your taste screen](screening-transparency-screen-plan.md) from that snapshot.

### Stage 2: improve personalization and useful variety

- Track an evidence amount alongside each learned preference. Keep shrinkage toward neutral for sparse evidence; retain an explicit no-evidence state.
- Compare the existing 60-day feedback decay with candidate alternatives. Treat the choice as a parameter to evaluate, not a proven universal constant.
- Compare capped ratings/reactions/usefulness weighting. Finishing and progress remain weak taste evidence; a confirmed dislike is stronger.
- Blend global and media-kind-specific taste only when enough evidence exists; movies and long-running series can express different preferences.
- Diversify across recent suggestions, genres and recommendation purposes. Hard title exclusions stay hard; exploration cannot bypass them.
- Add user-directed temporary intent and time budget. Leave inferred mood and ambient device surveillance out of the first version.
- Prefer similarity from available genres and metadata initially. Add cached themes/cast/directors or compact text embeddings only after implementing their data sources and validating their value.
- Compare resume behavior with and without reliable partial movie playback. Missing or stale positions add no fabricated progress.

### Stage 3: bounded adaptation, contingent on sufficient evidence

- Collect versioned local decisions and explicitly labelled outcomes. Keep a bounded store independent of notes.
- Start with offline tuning of a small set of weights against a frozen baseline. Temporal evaluation is possible only where historical timestamps/snapshots actually exist.
- Consider a small local contextual bandit or regularized model later. Any exploration must respect eligibility, remain understandable, and have a stable fallback.
- If randomized exploration is introduced, log the actual eligible pool and selection policy/propensity. Stable seeded shuffling and unobserved outcomes alone do not justify causal improvement claims.
- Keep a model version and rollback path. A profile update, a code/weight update and a measured quality improvement are three different things.

New-title discovery, collaborative filtering and cloud model training need a separate product/data decision. They are not implied by optimizing this saved-title recommender.

## Measurement and acceptance

The implementation should preserve the existing progress and storage invariants while proving the new behavior:

- Like/clear/switch changes influence once, across duplicate aliases and multiple widgets, including after process death.
- Explicit dislikes and temporary snoozes have their documented effects. Silence, opening details and time passing cannot mark something watched or disliked.
- Every raw contribution sums to the displayed raw total. Every diversity adjustment and shortlist decision matches selection.
- Opening the insights page repeatedly does not change preference, fatigue or the current suggestion.
- An unrated title or unknown genre is displayed as missing evidence. Contradictory evidence and imported undated data remain inspectable.
- Reset/correction behavior covers the intended feedback stores, invalidates stale questions where necessary, and preserves saved items and actual watch progress.
- Same-day widget stability, independent source filters, narrow/large-text layouts, cold launch, and offline operation continue to work.

Use curated cases and existing meaningful engine/store/device tests to establish behavior first. Then evaluate sequences of suggestions with explicit usefulness, rejection rate, duplicate/repeat rate, genre/purpose coverage and confirmed starts/resumes. Record latency, battery/network work and storage growth as constraints.

Compare ranking metrics only on data with defensible labels. Do not label unshown titles or unanswered long-form recommendations as disliked. A small personal history supports modest descriptive results, not a Netflix-scale accuracy claim. Keep the baseline until a candidate provides a measurable benefit without breaking exclusions, diversity, latency or explanation fidelity.

## Implementation order

1. Canonical feedback and provenance; connect existing thumbs.
2. Shared profile/decision explanations and corrected diversity selection.
3. Your taste screen with evidence, per-title breakdown, history and corrections.
4. Measured ranking improvements, temporary intent and richer local similarity.
5. Bounded adaptive tuning after the evidence supports it.

The earlier conversational example uses fictional data and baseline arithmetic. The updated Figma example uses the v2 response weight and describes its fixed comparison assumptions in the [handoff](screening-your-taste-figma-handoff-2026-11-14.md). Neither example is the user's personal profile or evidence of measured quality improvement; the native implementation is documented separately.
