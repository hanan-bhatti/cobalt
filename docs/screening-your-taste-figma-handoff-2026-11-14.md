# Your taste · current Android design · Figma handoff

Updated on 14 November 2026 in the existing Cobalt approved-library file, alongside the Android redesign.

[Current seven-view board](https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI?node-id=293-2528) · [Prototype](https://www.figma.com/proto/dJOugcd2V82nQUA1MjyINI?node-id=293-4386&starting-point-node-id=293%3A4386) · [Final composition](../design/screening-insights/redesign-20261114/figma-final.png)

## Views

| View | Presentation |
| --- | --- |
| Your taste | Five leading genres and an Others count, followed by a saved pick and recent compact feedback. |
| Why this pick? | A media hero, concise reasons and ranking points. Score contributions and the recorded suggestion open separately. |
| Your feedback | Active feedback first; title and reaction on each row. Source, dates, influence and corrections open in a sheet. |
| A genre's feedback | Contributing titles in a contextual sheet; numbers behind Learning details. |
| First feedback | One action to review a saved title, plus a pick explained by saved-title rules. |
| Other genres | All remaining individual genres, their tendencies and evidence. |
| Saved titles | Filled search field, simple filters, thumbnails and availability badges. |

The seven views reuse Cobalt's Noto Serif headings, Roboto text, dark surfaces, lavender accent, shared settings header, buttons and canonical icons. The search frame illustrates the default view; real text entry, filters and empty states run in Android.

[Correction outcomes](https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI?node-id=293-3523) and [editable components](https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI?node-id=293-2532) remain separate boards on the same page. Current, ignored-rating and reset prototype states have matching overview, Why, feedback, Others, search and detail destinations.

## Corrections and scoring examples

Ignoring Cold Case's rating changes its recommendation influence while preserving the saved 9/10 value. All feedback keeps the excluded rating inspectable. Use rating again returns to the current profile. Android offers Undo immediately after an exclusion; only reset requires confirmation.

| Illustrative state | Contributing titles | Ranking points |
| --- | ---: | ---: |
| Current | 7 | 47.79 |
| Cold Case rating ignored | 6 | 44.62 |
| Screening feedback reset | 5 | 47.24 |

The examples use the implemented genre formula and retain a 92-minute movie, 45 days since saving and the same previous-suggestion contribution to isolate the taste change. Actual Android reset also clears exposure state, so post-reset runtime totals can differ. Ranking points are relative quantities; they are not enjoyment probabilities. Undated ratings say Date not recorded. Titles, dates and feedback on the Figma board are illustrative.

## Organization and verification

The board is labelled **Current Android design** and contains seven 448 × 997 phones. The approved full-artwork Screening card remains the selected widget. Retired widget directions, dense insight components and superseded correction/modal concepts remain archived on the same page.

The final composition was inspected after correcting navigation sizing, icon-wrapper fill visibility and detail-overlay behavior. The work-in-progress placeholder is removed.

- 553 prototype actions checked across 121 scoped roots.
- Navigation, overlay and swap destinations resolve to valid top-level frames.
- No horizontal child overflow detected.
- Main board: 205 editable text layers, 142 component instances and 298 vectors.
- Zero image-filled UI nodes; the delivered UI remains editable.
- Product typography matches Noto Serif and Roboto.

[Final structural validation](../design/screening-insights/redesign-20261114/figma-final-validation.json) records the passing composition. The [UI validation report](screening-ui-redesign-validation-2026-11-14.md) records eight unattended Android UI checks and a separate full-window 320 dp / 1.6-font-scale check with 60 genres.

## Implementation and construction

Android: [ScreeningTasteScreen.kt](../app/src/main/kotlin/com/example/ui/screens/ScreeningTasteScreen.kt) and [ScreeningTasteComponents.kt](../app/src/main/kotlin/com/example/ui/screens/ScreeningTasteComponents.kt).

The current construction scripts and ledgers are in [redesign-20261114](../design/screening-insights/redesign-20261114/). Components and detail destinations were built before the main views; the finalization script preserves the existing board IDs and archives old concepts. Targeted visual fixes preserve component links.

The original [algorithm validation](screening-algorithm-validation-2026-11-14.md), [research](screening-recommendation-research-2026-11-14.md) and [screen contract](screening-transparency-screen-plan.md) retain the learning rules and rationale.
