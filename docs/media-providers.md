# Media providers and local data

Profile → Media & Providers groups connections, search preferences, anime/manga
configuration and local archive controls. Pending Trakt changes has a dedicated
queue page with title/action rows, category pause states and retry controls. Search-source toggles and preferred
metadata source apply to Capture, Media Hub and Side Dock searches.

- Trakt supplies account history, personal ratings, watchlist, show/season
  metadata, episode screenshots and trailers when provided by the API.
- TMDB supplies catalogue metadata, artwork, trailers and watch-provider links
  using the user's key. It is not a second user-account synchronization target.
- Jikan supplies anime and manga catalogue data. Manga is a separate media type;
  its chapter/volume/publication details are cached locally. Jikan is not a
  MyAnimeList account-login or account-write integration.

Provider switches affect new searches. Already saved titles remain on device.
Search combines stable provider identifiers before using title/year matches;
conflicting IDs, remakes and different media types are kept separate. Existing
saved metadata and personal choices are not replaced when adding an already
saved identity. No automatic deletion of historical duplicate records occurs.

## Local Jikan

Enter a compatible v4 base URL, including its path, for example
`http://192.168.1.20:8080/v4/`. HTTPS is required for public hosts. Plain HTTP is
accepted for explicitly configured private/loopback addresses or `.local`
hostnames. Android cleartext transport is enabled to permit these destinations;
Jikan configuration validates the host and rejects URL credentials, query
parameters and fragments. Cross-host and protocol redirects are disabled for
this client. No Trakt bearer token or TMDB key is attached to Jikan requests.

A self-hosted server still needs a connection to that server; Cobalt does not
turn an uncached Jikan deployment into an offline MyAnimeList mirror. Saved
catalogue facts and cached artwork can be viewed offline. Missing or unknown
chapter/volume/episode counts are not invented.

The repository includes `tools/media-local-jikan.py start|status|stop` to run
the official Jikan v4 image with an isolated MongoDB container on
`http://127.0.0.1:8702/v4/`. For the emulator, run
`adb reverse tcp:8702 tcp:8702` and use that same URL in Media & Providers.
The validation catalogue contains real MAL entries for Cowboy Bebop and Berserk;
a full catalogue needs the upstream indexers. The containers retain this small
catalogue when stopped. No public port is exposed.

## Changes and synchronization

A planned movie/TV save and its watchlist intent commit together in SQLite.
TMDB-only identities omit unknown Trakt IDs instead of sending zero. The same
save path is used by Capture and Side Dock. Account-bound durable intents
coalesce per item/action. Requests contain at most 100 queued records; writes
are paced and retries respect server backoff. A newer local generation cannot
be acknowledged by an older in-flight response.

Rating gestures update the inline five-star control and commit at gesture end.
Half-stars map to Trakt's integer 1–10 ratings. Clear removes the personal rating.
The four local statuses remain available. Trakt receives supported watchlist
and watched-history operations; Watching and Dropped remain local status
choices. Moving a previously watched title back to Plan to Watch preserves its
existing watch-history timestamps. Episode progress is displayed without
editable watched checkboxes.

On connection, existing supported movie/TV choices are queued once per session
before imports can replace them. Legacy `tmdb_movie_…`, `tmdb_tv_…` and Trakt
record IDs recover dedicated IDs where old Capture builds dropped them. Seed
intents do not replace newer queued choices. History uses stored timestamps; a
TV completion label alone never fabricates watched episodes. Capturing a new
completed movie queues watched history and watchlist removal atomically.

Full snapshots reconcile remote rating/watchlist/history removals while
preserving pending local edits and saved notes. Failed snapshots never clear
missing rows; retry re-reads the IDs needed for complete reconciliation. Movie
and TV IDs remain separate. Eligible app starts refresh after 15 minutes and
Android schedules a daily sync; remote delivery depends on network/WorkManager.

Watched TV snapshots request `extended=progress`, as required by Trakt's June
2026 API change. Account pages continue until an empty result, because Trakt
may cap a page below the requested size. The first successful sync after this
contract upgrade refreshes unchanged accounts too. Missing seasons/episodes
fail the snapshot while preserving saved progress; missing catalogue totals
never imply that an entire show is completed.

Opening a movie or show displays cached details immediately and refreshes
catalogue details silently in the background. TMDB, Trakt summaries and OMDb
enrichment share one merge path, preserving personal ratings, status, annotations
and progress. Account imports handle account state and basic title identities;
opening a title fills its seasons, synopsis, cast, facts and available source
ratings. Episode catalogue requests are made for the selected season and keep
the cached season on failure. Home descriptions exclude provider URLs.

Small changes use an upload-only worker, not a full catalogue import. At most
one upload worker waits behind the active worker. Full imports drain new intents
between pages, and startup resumes an eligible committed outbox after a crash
between committing an intent and scheduling its worker. Safe mode and pending
note migration still pause background work.

Unknown provider IDs stay queued for a later retry rather than blocking matched
titles or being discarded. The Pending Trakt changes count includes retained
intents. HTTP 420 capacity errors keep the queue and stop automatic transient
retries; the queue page explains the account action needed before retrying. Queue regressions cover category pause/resume; the optimized APK also verifies
the separate delivered queue page. Live account delivery is recorded in the validation report.

## Seasons, playback and trailers

The season sheet uses a bounded horizontal pager and arrow navigation through
known seasons. Each season's cached metadata is replaced atomically; interrupted
refreshes keep the previous season. A late response cannot recreate episode
rows for a deleted item. Episode title, air date, runtime, screenshot and score
are small local rows rather than a single huge document field.

Playback percentages come from Trakt resume data, not an estimated halfway
position. The account playback cache is shared across season pages for five
minutes, paged in 100s and bounded to 2,000 entries. Responses above that bound
produce a refresh warning and retain saved data; they are not silently truncated.
Unknown playback positions have no partial bar. Watched history can show a
completed bar. This UI does not scrobble or change episode progress.

YouTube trailers initialize an in-app WebView only after Play is tapped. The
iframe uses the installed app ID as its HTTPS origin/referrer, following
YouTube’s documented WebView requirements. Controls reuse the existing play/
pause drawables; native controls sit below the unobscured YouTube player.
JavaScript/web storage are enabled, third-party cookies and file/content access
are disabled, and playback pauses offscreen or in the background. Failures
provide retry and an explicit provider link. A provider may still remove or
region-restrict a trailer; actual playback requires live APK validation. Watch-provider chips
use the supplied provider link/logo and price when available; prices are not
inferred. More Info groups catalogue facts, community facts, source ratings and
provider links. Trakt supplies its community score; configured OMDb enrichment
supplies IMDb, Rotten Tomatoes critics and Metacritic when available. The
configured APIs did not supply Popcornmeter during validation. Source logos
are used only with actual values, and unavailable ratings are not fabricated.

## Local deletion

Deleting local media cancels its pending intents. Minimal provider-ID tombstones
keep background imports from recreating removed titles, including legacy media
already in Trash at the upgrade. Tombstones contain no saved title, synopsis,
rating or note body. Restore or an explicit re-add clears the matching ignore
group. Media & Providers → Ignored deleted titles allows resetting these IDs.
Local deletion does not delete the user's Trakt watched history.

The metadata upgrade advances to Room v14, following the queued-sync v11 → v12
upgrade. Both early thumbnail-only v13 validation builds and the later v13 shape
are accepted by an additive, column-aware v13 → v14 migration. It adds nullable cached fields, episode thumbnails/playback and the
provider-ID tombstone table, preserving existing note bodies and files.
