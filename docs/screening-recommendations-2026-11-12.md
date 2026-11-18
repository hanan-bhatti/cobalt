# Screening recommendations

Cobalt now has an explainable, local recommendation engine for saved movies and TV shows, with a preview/feedback screen and optional morning check-ins. The Screening Card launcher widget has per-card sources, independent pick cycles and reversible thumb preferences. Tap its artwork to open the saved detail screen. A partial saved Trakt position adds a thin accent edge; movie details also show playback progress. See [the latest validation and APKs](screening-widget-validation-2026-11-13.md).

## Research and approach

Reviewed these primary sources on 12 November 2026:

- [Google Research: Deep Neural Networks for YouTube Recommendations](https://research.google/pubs/deep-neural-networks-for-youtube-recommendations/) separates candidate generation from ranking.
- [Google ML: recommendation systems](https://developers.google.com/machine-learning/recommendation/overview/types) describes candidate generation, scoring and re-ranking. Cobalt uses eligibility filtering, explainable scoring and a diversity pass.
- [Netflix: how recommendations work](https://help.netflix.com/en/node/100639) describes viewing history, ratings, title metadata and context. Cobalt can use only progress and preferences actually available in its archive, not Netflix playback data or other users' histories.
- [YouTube: recommendation controls](https://support.google.com/youtube/answer/6342839?hl=en) distinguishes not interested, already watched and dislike. Cobalt likewise separates completion from preference and temporary snoozes.
- [Android: notifications](https://developer.android.com/develop/ui/views/notifications/build-notification) supports up to three actions. Background feedback uses explicit immutable receiver PendingIntents; notification taps open an Activity directly.
- [Android: WorkManager requests](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work) explains delayed, inexact execution and retries. Cobalt recalculates each day's local morning using one-time durable work, instead of adding a fixed 24-hour period across timezone/DST changes.

This is a small content/progress ranker, not a neural model trained on millions of viewers. It works offline with existing saved metadata. Provider discoveries, similarity embeddings and collaborative filtering are not enabled.

## Signals and rules

Eligible candidates must be saved MEDIA movie/TV records, active and available. Completed, dropped, paused/on-hold and explicitly uninterested titles are excluded. A caught-up ongoing show waits for more released episodes. Future-only episode data/premieres are withheld. Provider identities are namespaced by movie/TV, and overlapping TMDB/Trakt/IMDb aliases group duplicate copies.

Scores favor continuing something, finishing the last one or two released episodes, and resurfacing older untouched watchlist items. Runtime, recency of progress, ratings and explicit Useful/Not interested feedback adjust the score. Sparse genre evidence is smoothed; feedback influence halves over 60 days. Completing a title is weak positive evidence, not proof that it was liked. The diversity pass is bounded to the top 200 scored candidates to limit work for large libraries. Similar genres and purposes receive a diversity penalty before a deterministic weighted shuffle. Next excludes already-seen picks until the preview is restarted.

Missing totals stay unknown. Specials and duplicate episodes do not inflate normal-season progress. Counts that conflict with metadata lose the remaining-episode claim. Pace requires two positive intervals from recorded progress/episode timestamps; a single imported count does not establish a pace. General metadata updatedAt is not a viewing timestamp.

A five-episode show with three watched and no update for nine days can say “Only 2 released episodes left” and ask whether the user is still watching or has finished. It never marks the show complete because nine days elapsed.

## Feedback

| Response | Effect |
|---|---|
| Widget Like / Not for me | Reversible local thumb preference; keep the current card visible; further ranking use is planned |
| Useful | Positive preference evidence; short cooldown |
| Not interested | Hide this title; softly adjust genre preferences |
| Not now | Skip this title for seven days; no dislike |
| Still watching | Explicit Watching status; no fabricated episode records |
| Paused for now | Thirty-day local recommendation pause; archive status stays saved |
| Finished | User confirmation sets Completed; TV episode history remains unchanged |
| Next / silence | No dislike or completion; exposure fatigue only |

Provider-backed status changes reuse the existing durable Trakt outbox path. Records without provider IDs update locally without queuing invalid sync identities. Learning is stored separately in screening_learning.db and cannot rewrite note bodies. Histories are bounded and can be reset while keeping archive watch progress.

A delivery token is answered once, transactionally. The live saved item is revalidated. Deleted, archived, unavailable, replaced, already completed/dropped, answered or expired questions cannot change preferences through notification actions. Metadata enrichment preserves identity aliases and live questions. Reset invalidates existing questions while preserving daily reservations.

## Morning questions

Off by default; independent of archive reminders. Users choose 05:00–11:59 local time and whether weekends are included. At most one automatic question per local day, with minimum spacing; after three unanswered automatic questions, spacing increases to approximately three days. Normal candidates have a two-day exposure cooldown. Silence does not create a negative feedback event.

A question can arrive within three hours after its requested morning time. Later jobs skip delivery, avoiding a delayed evening notification. Notification permission and the Screening questions channel must both allow delivery. Rescheduling handles startup, reboot, clock and timezone changes. WorkManager timing remains approximate; Android force-stop suspends background work until the app is opened again.

The notification offers Useful, Not interested and Update progress. Tap the question for all response choices; Finished requires confirmation. Test/preview notifications are explicit user requests and do not consume the automatic daily reservation. Notifications use private lock-screen visibility.

Generic archive reminders now exclude MEDIA so they cannot bypass completed/dropped recommendation exclusions.

## Entry points

Settings → Home Screen Widgets → Screening Card → Screening picks, or Adaptive Reminders → Screening picks. Screening is no longer listed in Media & Providers. Uses the existing app theme, typography, settings groups, detail navigation and screen transition wrapper.

## Validation

The final feature run passed 63 targeted host tests and 5 Android device tests. Real launcher placement and RemoteViews inflation were checked. The subsequent settings simplification and shared Folio header changes were debug-built and installed. See [the validation report](screening-widget-validation-2026-11-12.md) for the distinction between current functionality and proposed design/category changes.
