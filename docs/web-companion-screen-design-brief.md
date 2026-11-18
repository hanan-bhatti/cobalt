# Cobalt companion: content screens

Design brief based on source inspection on 2026-11-11. This is a design specification, not a record of implemented screens.

Existing Figma: https://www.figma.com/design/dJOugcd2V82nQUA1MjyINI/

## Approved visual and interaction direction

Reuse the existing Figma screen's components, fonts, colors and dimensions. Muted sage and ivory, clean sidebar, global search, a pinned Library tab and closable content tabs. No sidebar footer, lock/key icons or prominent “Phone connected” label. Keep the quiet device menu. Preserve colored/custom folder icons where available.

Clicking an item opens a split viewer beside the library by default. A visible “Open in new tab” action promotes the item into a dedicated workspace tab. Closing the viewer restores the full library width and preserves scroll, filters and selection. A dedicated content tab uses the wider content area; its close action returns to the prior tab. These are proposed companion interactions.

## Required frames

| Frame | Composition and content | Actions and empty-data behavior |
| --- | --- | --- |
| Library / nothing open | Full-width grouped library; Recently saved mixed captures and Movies & Anime poster shelf. Search, type filters, sort, grid/list switch. Only Library tab on a fresh session; existing inactive tabs may remain after closing the split. | No giant empty reader. Filtered results use a coherent grid/list rather than advertising unrelated shelves. Empty archive and no search results are separate states. Counts must come from data. |
| Text / split reader | Existing approved reader: title, dates, folders, Markdown body with headings, lists, tables and code. | Edit, copy, open in new tab. Missing body gets a recovery state. Phone/other-browser editing conflict is a contextual message, never a persistent global lock icon. |
| Code / split viewer | Title, folders, dates; monospace code with syntax highlighting and horizontal scrolling inside code area. | Copy code, edit, open in new tab. Language detection is presentation-derived; do not imply a persisted language field. No run-code button. |
| Link / split viewer | Optional preview image, saved title/link title, source domain, description, full source URL and capture metadata. | Open original, copy URL, copy description, open in new tab. No Edit action under the current API. No invented full-article reader, author or reading time. Missing image collapses naturally; absent metadata retains title and URL. |
| Image / split viewer | Fit image without cropping the subject; title, date, folders, stored size; optional extracted text/description below. | Fit/zoom/fullscreen, copy extracted text, open in new tab. Dimensions may be derived after loading, not claimed as stored metadata. No fabricated camera/EXIF data. |
| Video / split viewer | Actual local video player with poster when available, seek bar, play/pause, sound and fullscreen; title, date, folders, size; optional extracted text/description. | Open in new tab. Duration comes from loaded media. Browser playback failure and missing file have useful distinct states. No movie catalogue poster presented as a playable file. |
| Audio / split viewer | Simple audio player, seek timeline and time, title, folders, date, size. Transcript appears only when extractedText exists. | Play/pause, volume, copy transcript, open in new tab. No invented waveform, speaker diarization, timestamped transcript or generated summary. |
| Movie / split detail | Poster, title, movie type, year, rating, runtime, watch-status badge, genres, synopsis, optional tagline, watch providers and trailer/source links. | Read-only under current web API. No Play movie action. Optional sections disappear when absent. |
| TV or anime / split detail | Same media family; episode count and catalogue source where present. TV progress and season detail can be designed as a separately marked API-extension variant. | No fake personal progress. Do not conflate total episodes with watched episodes. No embedded episode playback. |
| Manga / split detail | Cover, title, manga type, rating, genres, synopsis, chapter count and source. | No video player, runtime or Watch trailer control when inapplicable. Volume count/publication status require additional API fields. |
| Content / dedicated tab | Show at least one promoted content screen using the same viewer component, wider canvas and comfortable reading width. | Same data and actions as split mode; no duplicate functionality or different information model. |

## Supporting states

Include skeleton loading, missing preview image, unavailable local file, unsupported browser playback, unavailable/oversized note, expired session and disconnected device with recoverable actions. Preserve unsaved text/code when a save fails. Use inline explanations near affected content; avoid adding global visual clutter.

## Data evidence and API boundary

- `app/src/main/kotlin/com/example/data/model/SavedItem.kt`: exactly LINK, IMAGE, VIDEO, CODE, TEXT, AUDIO and MEDIA. Attachments is an image/video/audio grouping, not a document/PDF record type.
- `app/src/main/kotlin/com/example/data/local/SavedItemEntity.kt`: title, content, timestamps, folders, extracted text, thumbnail, link metadata, size, availability, media metadata, personal rating, watch progress and missing-body information. Legacy sync/backup flags are not evidence of an active cloud service.
- `app/src/main/kotlin/com/example/data/model/MediaFacts.kt`: catalogue source/source URL, episode/chapter/volume counts and publication status.
- `app/src/main/kotlin/com/example/data/model/TvShowProgress.kt`: watched/aired counts, next episode and season progress; richer optional provider facts.
- `app/src/main/kotlin/com/example/web/server/WebCompanionRouter.kt`, `noteResponse`: currently exposes common fields, link metadata, thumbnail, media type/status/year/rating/runtime/tagline/genres/providers/trailer, source, episode/chapter counts, size and local media URL. Only TEXT/CODE are editable. It does not expose every database field.
- `app/src/main/kotlin/com/example/ui/screens/DetailScreen.kt`: native image/video player, link preview and source, extracted image/video text, audio playback and transcript.
- `assets/screenshots/06-links.png` and `12-media-detail.png`: inspected native-screen references. `all-15-preview.png` inspected for library, code, folders and overall app context. These campaign panels contain native captures within marketing layouts; use the app content rather than their external headline/device framing.

Implementation gaps to track separately: watch-progress/personal-rating fields, backdrop, volume/publication fields, explicit unavailable-state flags and any proposed mutation actions outside text/code editing. A stored field does not automatically mean it is available or editable through the companion API.

## Figma construction and review

Inspect the existing file first. Reuse its sidebar, search, tabs, cards, colors and text styles. Create semantically editable frames and reusable viewer headers/metadata rows rather than importing complete-screen images. Add the new frames alongside the existing screen without replacing it. Use realistic synthetic examples; do not read private database contents. Label extension-only variants outside the product UI. Check each composition for clipped text, image fit, useful hierarchy and consistency with the approved screen.
