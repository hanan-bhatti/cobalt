# Screening artwork: research and design review

[Figma refinements and selected version A](https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI?node-id=224-2082) · [Preview](../design/widgets/validation/screening-artwork-feedback.png)

Research conducted November 12, 2026 for Android users who save movies, shows, anime, and manga in Cobalt. Public discussions were compared with official Android, Netflix, Google TV, and Trakt documentation. This is qualitative research, not a representative survey or proof of demand. Current native app behavior was checked in owning source; user feedback and ranking were not changed.

## Current edit and latest selection

Step 1: inspected the live edited artwork instance `217:1880`. Health: clearer visual hierarchy, missing feedback interaction. [Captured evidence](../design/widgets/validation/screening-user-edit-20261112.png). The user hid the header and change-pick control. The image, bottom gradient, title, and metadata remain. The live instance differs from its original master, so the live edit was used as the refinement source and preserved.

Keep the open artwork, direct title/image entry, and wide shape. Improve the artwork resolution: the current source is 210×147 and visibly pixelates when enlarged. Use a larger original backdrop in production, sized to actual widget pixels. Shorten metadata beside the feedback dock; category and runtime help more than the release year. Long titles need a two-line limit and a caption that grows upward rather than shrinking buttons.

Three editable refinements were delivered: A's compact floating thumbs dock, B's labelled caption rail, and C's conditional episode-progress state. Six feedback variants cover compact/labelled layouts and neutral/liked/disliked states. Twelve prototype transitions implement tap-to-select, tap-again-to-clear, and opposite-thumb-to-switch. All action targets are at least 48×48 dp. Typography is Source Serif 4 and Roboto, controls reuse canonical Cobalt thumbs, and raster fills contain only discrete media artwork.

The user then selected A and supplied a YouTube thumbnail showing a thin bottom-edge progress line. A now has an optional **3 dp accent-colored edge with no percentage, count, or progress label**. The Figma fill is an illustrative 46% style sample, not the user's movie playback data. A no-progress component is retained for unstarted or unknown progress. `Show progress#227:0` controls visibility. The original edited instance remains intact. A is selected; B/C remain earlier explorations.

## Findings ranked by value

| Priority | User goal / friction | Evidence and confidence | Cobalt response |
| --- | --- | --- | --- |
| 1 | Get useful information or act without opening the app. An app shortcut with decoration consumes space. | Android explicitly recommends one primary use case; broad Pixel discussion includes both frequent users and people who remove unused widgets. High confidence in the principle, unknown Cobalt adoption. | One title, direct entry, quick feedback. Avoid a settings dashboard. |
| 2 | Keep personal artwork that suits a home screen. Generic content and clutter weaken appeal. | AndroidApps participants described film/series/anime posters used as a photo-widget watchlist, with interested replies and discussion of syncing/styling. Specific anecdotes, medium confidence. | Artwork-first default, sharper images, compact controls, optional pinning later. |
| 3 | Trust the content to stay fresh and available. | Multiple participants in an older PocoPhones thread reported losing Netflix's widget; some valued latest-release updates. Android flags stale content and missing action updates. High confidence reliability matters; low confidence in current Netflix availability. | Stable daily pick; refresh after feedback/progress edits; retain useful cached content during temporary network failures. |
| 4 | Continue or finish something without deciding from scratch. | Trakt's current changelog confirms iOS widget progress bars and distinguishes caught-up shows from ended shows. High feasibility confidence, medium utility confidence. This does not establish Android feature parity or adoption. | Optional Continue state; subtle read-only progress from reliable data. |
| 5 | Teach preference quickly and recover from mistakes. | Netflix documents thumbs as recommendation inputs. Its 2017 testing announcement reported increased rating activity after moving from stars to thumbs. This self-report cannot predict Cobalt's uplift. | Reversible preference, separate from watched state, star ratings, favorites, or pinning. |

The opportunity is a personal media poster that saves a trip into the app. Artwork creates appeal; a useful pick, Continue state, and reliable feedback earn repeat use. Public opinions vary between dense utility and minimal layouts, so do not claim everyone wants the same home screen.

## Source map and limitations

1. [Android widget design](https://developer.android.com/design/ui/mobile/guides/widgets): one primary use case, actionable content, sizing.
2. [Android widget quality](https://developer.android.com/docs/quality-guidelines/widget-quality): full host bounds, responsive reflow, 48 dp targets, updates after actions.
3. [AndroidApps: favorite useful widgets](https://www.reddit.com/r/androidapps/comments/1cmposs/what_are_your_favorite_useful_widgets/): personal poster watchlists, styling, note/task shortcuts.
4. [Pixel: do you actually use widgets?](https://www.reddit.com/r/GooglePixel/comments/1i4capu/do_you_actually_use_widgets_on_your_pixel/): space/usefulness tradeoffs, customization, reliability, differing preferences.
5. [PocoPhones: Netflix widget disappeared](https://www.reddit.com/r/PocoPhones/comments/1b7p2rq/widget_bug/): older firsthand disappearance reports and comments valuing release updates. Speculation about why it disappeared was not treated as fact.
6. [Google TV help](https://support.google.com/googleplay/answer/10066945?hl=en): officially documents a Google TV Android widget, a confirmed comparator.
7. [Trakt changelog](https://roadmap.trakt.tv/changelog): iOS widget progress bars, Android widgets, progress distinctions and quick-undo work. Platforms were kept distinct.
8. [Netflix rating help](https://help.netflix.com/en/node/9898): liking/loving/not-for-me inputs help suggestions.
9. [Netflix recommendation explanation](https://help.netflix.com/en/node/100639): feedback, viewing activity, title metadata and context inform its system. Cobalt does not observe Netflix playback.
10. [Netflix: Goodbye Stars, Hello Thumbs](https://about.netflix.com/en/news/goodbye-stars-hello-thumbs): historical rationale and self-reported test outcome.
11. [Android widget overview](https://developer.android.com/develop/ui/views/appwidgets/overview): control/hybrid widgets and restricted gestures.
12. [Current Netflix mobile layout help](https://help.netflix.com/en/node/575087423404644): current app navigation and discovery; not evidence of a home-screen widget.

Searches covered Netflix Android widget availability/removal/reviews, practical widget use, poster/watchlist widgets, Google TV, and Trakt continue/progress. Samsung community pages appeared in search but their full pages were inaccessible, so they were not principal evidence. The old Trakt forum now redirects; its current changelog was used. Streaming/Kodi results often mean in-app TV rows when saying “widget”; those were excluded from Android launcher conclusions. A Netflix iOS result was explicitly a hypothetical design concept, not a released feature. No current official Netflix home-screen-widget offering was verified. No private customer analytics were accessed.

## Progress feasibility

The visual treatment is feasible in Android. Whether it appears depends on real data:

| Available data | Honest behavior |
| --- | --- |
| Valid TV/anime watched and released counts | Thin accent fraction bar. For the selected design, counts stay out of the visual caption; accessibility can announce them. |
| Ongoing show caught up with currently released episodes | Distinguish caught up from finished. Never claim the entire series is complete. |
| Unknown/zero total or inconsistent watched count | Hide the bar. |
| Movie without playback-position data | Runtime/watch status only; runtime cannot produce playback percentage. |
| Manga without reading-progress fields | Hide the bar until actual chapter/volume progress and reliable totals exist. |

Inspected `ScreeningRecommendationEngine.progress`, `ScreeningWidgetModel.ScreeningPick`, `TvShowProgress`, and `SavedItem`. Episode progress exists. The current widget snapshot stores formatted metadata rather than numeric progress; implementation should add typed fields instead of parsing that text. SavedItem has no durable movie playback position or manga reading progress. The bar is a readout, not a draggable seek slider. For a movie/never-started pick, the default remains no bar.

### November 13 update: include movies using Trakt playback

The user requested movie progress as well as episode progress. Trakt's current official API contract confirms `GET /sync/playback/movies`, `GET /sync/playback/episodes`, and the combined `GET /sync/playback`. Playback records carry a floating-point `progress` value from 0 to 100 and a paused timestamp. These are partial playback positions, separate from watched history. [Official endpoint contract](https://github.com/trakt/trakt-api/blob/master/projects/api/src/contracts/sync/index.ts) · [Progress schema](https://github.com/trakt/trakt-api/blob/master/projects/api/src/contracts/sync/schema/response/playbackItemResponseSchema.ts).

Movies are included in the selected artwork design: use the same 3 dp accent edge for a real partial movie playback percentage, without added text. A connected player/scrobbler must report the position to Trakt; a watched checkbox, runtime, or Watching status cannot supply it. Current-episode playback should likewise use its own percentage; it should not silently be substituted with whole-series completion. Unknown or unstarted playback retains the no-bar state.

The current Cobalt code already fetches and caches episode playback through `TraktPlaybackCache` and `TraktEpisodeCatalog`. Its API service currently exposes only the episode playback endpoint. Movie fetching, persistence and widget consumption still need wiring when implementing this selected design. No account playback data was fetched or written during this verification.

C's earlier illustration uses discrete promotional art from [Sunrise's official Cowboy Bebop anniversary page](https://www.sunrise-inc.co.jp/work/news.php?id=20721). Its 12 of 26 watched count is mock viewing data. The source image and its crop are saved under design/widgets/assets/.

## Feedback contract for the later build

Keep one effective preference per stable item: neutral, like, dislike. Store the item key, new/prior preference, timestamp, and `screening_widget` action source; widget ID and pick generation validate stale actions. Update atomically and deduplicate action delivery. Ranking should consume the latest effective preference, so rapid toggle changes and Undo do not accumulate contradictory training weight.

Dislike suppresses the title from future recommendations without deleting it from the library. Leave the visible card until its next refresh/replacement so the vote can be cleared. Clearing/changing the vote must reverse the suppression. Like means recommendation interest; it does not automatically mark watched, assign a star rating, pin the poster, or advance the pick. Title opens and widget refreshes are separate weaker events, not proof of viewing.

`ScreeningStore.answer` currently locks the first answer and keeps notInterested sticky. A dedicated reversible preference mutation and revised effective ranking inputs are required. This design pass did not write feedback, change ranking, or implement the launcher UI.

## Creative opportunities

**First build:** selected A, sharper backdrops, short runtime/type metadata, wide adaptive layout, reversible thumbs and the optional accent edge. Test real touch errors, TalkBack labels, widget retention, and action refresh reliability. Static Figma screenshots cannot establish those properties.

**Next iteration:** a stable daily pick and conditional Continue state; “almost finished” from reliable counts; optional pinning from details to keep a favorite poster; available-new-episode context when release data is trustworthy. Keep extra information out of the card unless it helps a decision.

**Needs more research/data:** time-budget selection from known runtimes, manga reading progress, provider links that really reach the selected title, and calibrated recommendation confidence. Avoid invented Match percentages or viewing streaks added only as decoration.
