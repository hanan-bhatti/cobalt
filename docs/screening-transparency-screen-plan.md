# Your taste: Screening transparency screen

Updated 14 November 2026. Status: implemented in Android with the `local-taste-v2` recommender. This document records the product contract and the original design rationale. See the [implementation and validation report](screening-algorithm-validation-2026-11-14.md) for completed behavior and checks, and the [platform research and optimization plan](screening-recommendation-research-2026-11-14.md) for later evaluation work.

## Outcome

A Cobalt user should be able to answer three questions without knowing recommendation terminology:

1. What does Cobalt think I like, and what evidence supports that?
2. Why did it choose this title, and why might another title be absent?
3. How can I correct its understanding?

Use **Your taste** as the screen title, with the subtitle **How your feedback shapes your picks.** The screen describes the current local recommendation profile. It must distinguish saved facts, inferred preferences, and fixed recommendation rules.

## Recommended visual structure

Use horizontal preference bars, a per-title contribution breakdown, and a short learning history. A radar/star chart is optional for a later overview; it should not be the first version's main display.

This is a design recommendation: horizontal bars support direct comparisons, long genre labels, negative values, accessible text equivalents and visible evidence counts. A radar polygon depends on axis order and can imply a precise profile even where little evidence exists. Its filled area is not a meaningful total of liking.

| User question | Main display | Supporting detail |
| --- | --- | --- |
| What have you learned about me? | Horizontal genre preference bars with More often / Less often direction | Distinct contributing titles, source types, missing evidence and corrections |
| Why this title? | One short reason beside the current suggestion | Expandable signed contribution chart or rows, raw total and separate selection details |
| What changed? | Recent feedback list with source and available date | Recorded effect, original title, and correction/undo where supported |

Do not turn these into three dashboard metric cards. Use Cobalt's existing page surface, settings groups, serif heading, wallpaper-derived colors and typography settings.

## Entry and navigation

**Your taste** is available in Screening Card settings, beside the recommendation-review entry. **Why this pick?** is available in the in-app recommendation preview. Both lead to `screening_taste?itemId={itemId}&source={source}`; a supplied title opens its explanation.

Default to the current widget/recommendation source when entered from it. In the first version, label the profile as shared across saved media if that remains the engine's actual behavior. A Movies source filter must not silently imply that the profile was trained only on movie feedback.

Reuse [NoteSettingsPage and the current recommendation flow](../app/src/main/kotlin/com/example/ui/screens/ScreeningRecommendationsScreen.kt), [Screening settings components](../app/src/main/kotlin/com/example/ui/components/ScreeningSettingsComponents.kt), and the [existing typography](../app/src/main/kotlin/com/example/ui/theme/Type.kt). Preserve normal back behavior and the app's screen transitions.

## First screen

### Your taste

- Show up to five leading genre tendencies initially, ordered by absolute preference strength, then distinct contributing-title count. Put every remaining genre under **Others**, with its count and an expandable list. Do not add a combined Others score; every genre continues to participate separately in learning and ranking.
- Use one consistent signed scale. Visible labels emphasize More often / Less often; exact learned weights belong in optional detail.
- Put evidence beside the genre: **Based on 3 titles**, rather than a confidence percentage.
- A rating and a question response about the same movie are two observations about one title, not two independent titles.
- Show **Not enough information** when an estimate has little support, and **No feedback yet** when there is none. Missing evidence is distinct from balanced positive and negative evidence.
- Tap a genre to inspect the contributing title/source records. For example: **Cold Case · your rating 9/10** and **Late Shift · you said Good pick**.
- Keep preference inference language modest: **Your feedback suggests you prefer thrillers**, rather than **You love thrillers**.

Normal users should not need the formula. The detail view can expose the learned weight and evidence amount for someone who wants to inspect the numbers.

### Why this pick?

Show the actual current title as a compact row and one or two concrete reasons, such as **You often give crime films high ratings** and **You saved this 45 days ago**. Use a distinct reason for a deliberate variety choice, such as **A different genre from your recent picks**.

The **Score details** disclosure should contain:

- Positive and negative point contributions from the same calculation that ranked this title.
- The raw ranking total, labelled **Ranking points**.
- A separate variety/intent adjustment, adjusted utility, and shortlist/selection explanation.
- Whether today's valid pick was retained for stability rather than chosen again.
- Missing signals, such as **Runtime unknown** or **No saved playback position**.

For the current ranker, a raw breakdown can start with 20 base points and then show status/watchlist, taste, age, fatigue and runtime contributions. The total must reconcile exactly before display rounding. If the new selector samples among a shortlist, explain that a high score gives better odds and does not guarantee selection.

Never present a value such as 52 ranking points as **52% likely to enjoy**. Ranking points, a relative preference weight, selection probability and calibrated enjoyment probability are different quantities. Only display a probability where the implementation actually computes that probability and its meaning is clear.

Expose **Find a saved title** within this explanation area to answer why another title is absent. Use its real eligibility reason: completed, on hold, hidden, snoozed until a date, caught up, unavailable, outside the selected source, or unsupported format. Where release information is missing, do not invent an unreleased date.

### Recent learning

Show the latest few recorded changes, with **See all feedback** for the full retained history:

- The title and the user's action.
- Source where recorded: widget, review screen, morning question, or imported rating.
- Event date where recorded. Use **Date not recorded** for migrated ratings without a timestamp; `SavedItem.updatedAt` is not a rating/viewing event.
- A plain-language effect: **More influence for Thriller**, **Hidden from future picks**, or **Skipped until 21 Nov**.
- A labelled correction action.

Exact historical before/after weights require a stored event snapshot. If only today's recomputation is available, label it **Current influence** rather than inventing a past score change. If old history was pruned, explain that older activity is no longer retained.

## Corrections and controls

| Control | Behavior |
| --- | --- |
| Clear or switch a widget reaction | Replace the one active reaction; do not append extra preference votes for repeated taps. |
| Ignore this rating for recommendations | Exclude that source's influence locally while retaining the saved rating. This requires an explicit evidence-exclusion record. |
| Recommend again | Clear the selected title's explicit recommendation exclusion/suppression. A positive rating should not silently override an existing hide. |
| Maybe later | Keep the existing seven-day temporary suppression; show the end date. |
| Reset recommendation feedback | Clear the specified Screening reactions/question feedback and related model state. Keep saved ratings and actual watch progress; make their continuing influence explicit. |

Use **Reset recommendation feedback**, with confirmation: **Clear your Screening likes, dislikes and question responses? Your saved ratings and watch progress will stay. Saved ratings will still guide your picks.** The implemented reset covers canonical and legacy widget thumbs, question feedback, rating exclusions, publication/fatigue state and recommendation suppressions. It invalidates outstanding questions, keeps daily reservations, and preserves archive ratings, watch progress and notes. Saved ratings guide the rebuilt profile.

A separate future **Start a fresh taste profile** control can exclude older evidence while preserving archive facts, but it needs a reset epoch/evidence policy. Do not promise that behavior by merely deleting the current feedback table and then immediately rebuilding the profile from old ratings.

Changing watch status or a provider-backed personal rating should open the existing title controls. A recommendation correction should not unexpectedly edit Trakt watch history, episode records or the archive rating.

Temporary intent controls, such as **A shorter movie tonight**, belong in a later iteration. Their expiry must be visible and they must remain distinct from durable learned taste.

## Shared data contract

The native screen reads `ScreeningService.insights()`, using `ScreeningTasteProfile`, evidence and history records, `ScreeningScoreTrace`, retained `ScreeningDecisionSnapshot` publications and engine eligibility explanations. The following requirements describe that shared contract; temporary intent, an evidence reset epoch and an actual-opened event remain future capabilities.

| Structure | Required information |
| --- | --- |
| Profile snapshot | Profile/model version, generated time, active media scope, evidence cutoff/reset policy |
| Genre entry | Canonical genre, learned weight or missing value, distinct evidence-title count, source counts and contributing source IDs |
| Evidence record | Canonical title aliases, signal type/value, origin if known, event timestamp if known, current inclusion/exclusion and decay policy |
| Decision snapshot | Decision ID, versions, time, surface/widget identity, source filter, eligible pool/shortlist, selected title and retention/selection mode |
| Contribution trace | Raw feature values/points, raw total, separate diversity/intent adjustment and adjusted selection utility |
| History change | Previous/new active state, origin/time, recorded effect or explicitly labelled current effect, valid correction capability |
| Eligibility explanation | Actual exclusion code and supporting facts, including suppression expiry or unavailable data |

The profile, chart, reasons and ranking must consume the same calculations. Do not reimplement the score separately in Compose. Generate short explanations from structured evidence and contributions; an LLM is unnecessary for deterministic explanations.

Capture a decision snapshot when a new suggestion is published, not on every rerender. Distinguish a reserved notification, a published notification/widget, and an explicitly opened title. Cobalt cannot know that a launcher card was actually seen just because it published RemoteViews.

Opening this screen must not call `createDelivery`, increase shown count, consume a daily question reservation, change watch status, or add taste evidence. Refresh can show updated archive facts and a current profile without implying a new recommendation exposure. Preserve the explanation associated with a retained card; label a fresh recomputation clearly if it differs from the original decision.

Keep learning storage independent of notes. Use bounded event history and durable active preferences/exclusions, with clear retention semantics. Handle process recreation, metadata enrichment, identity aliases and account-scoped provider progress. Show provider provenance only where recorded; having a Trakt ID does not prove that a specific rating originated there.

## States and accessibility

- **No saved titles:** clear empty state with an action to open Media.
- **Saved titles, no taste evidence:** show that picks currently rely on watchlist/status and variety. Invite feedback on a saved title without inventing a neutral profile.
- **Sparse or conflicting evidence:** show counts and contributions; do not display a certainty meter.
- **All titles excluded:** explain the exclusions and offer review of hidden/snoozed titles.
- **Unsupported Manga source:** show the actual limitation until the reading adapter is implemented.
- **Loading/error/offline:** preserve available saved explanations with their timestamp; offer retry where useful. Never fabricate a fresh profile.
- **Different account/progress session:** clear stale playback influence and identify unavailable provider facts.
- **Large text and narrow screens:** labels and point values wrap; disclosures and actions retain at least 48 dp targets.
- **Light/dark/dynamic palettes:** use the app's tokens and adequate contrast. Pair negative/positive marks with words and signs; color alone is insufficient.
- **Screen readers:** every genre and contribution has a textual label/value/source summary. Reading order follows the visible screen.

## Acceptance contract

1. Displayed contributions and totals match the live engine and the relevant retained decision snapshot.
2. Profile values, counts and evidence records agree, including duplicate aliases, clears and contradictory signals.
3. No observation is invented from missing timestamps, global title ratings, background refreshes or elapsed time.
4. Corrections change only their documented recommendation influence, survive process death, and cannot be undone by a stale notification.
5. Reset behavior is explicit, works across intended feedback stores, and preserves archive ratings/progress as promised.
6. Opening/reopening the screen is free of learning and exposure side effects.
7. Existing widget placement, independent picks, cold launch and note-recovery gates continue to work.
8. The screen works with real sparse data, offline state, both themes, large text and accessibility services.

## Implementation and later work

Canonical feedback, provenance, shared explanations, diversity-adjusted selection and the native Your taste screen are implemented. Richer similarity and automatic tuning follow the measurement plan in the companion research document; this pass does not claim measured recommendation-quality improvement.

The earlier conversational example uses fictional titles and baseline arithmetic to demonstrate linked profile bars and a raw score breakdown. It is distinct from the implemented native screen and from the user's actual profile.

## Earlier conversational-example verification

Offline checks passed for all 30 rating/title combinations, default values, score reconciliation, missing evidence, interaction wiring, saved-state restoration and invalid-state fallback. Direct contract checks covered two static chart configurations and 18 generated configurations; module wiring was checked with a chart mount stub, including a state change before mounting.

The bundled chart checker timed out on both this example and a minimal one-row diagnostic. Its validation did not complete. Browser rendering was not inspected, and no Android or Gradle tests were run for this documentation and example pass.

## Editable Figma concept

The six-view Your taste concept and linked correction prototype are in the existing Cobalt file. All eight overview previews now use five genres plus Others, with separate current, ignored-rating and reset outcomes. See the [Figma handoff](screening-your-taste-figma-handoff-2026-11-14.md) for the boards, flows, illustrative data and visual verification.
