# Rating artwork

The IMDb, Rotten Tomatoes tomato, and Popcornmeter bucket vectors were supplied
by the project owner on 7 November 2026, copied from Trakt's displayed artwork.
They are third-party brand artwork, not original Cobalt icons. No trademark
ownership or open-source license for these logos is asserted here.

Android resources:

- `ic_rating_imdb.xml`
- `ic_rating_rotten_tomatoes.xml`
- `ic_rating_popcornmeter.xml`

Conversion expands SVG definitions/references, removes transparent duplicate
paths, CSS, comments and the redundant rectangular mask, and preserves the
Popcornmeter translation. Original fills and viewport geometry are retained.
Compose uses `Image` rather than a theme-tinted `Icon`, with an 18 dp height and
proportional widths. Accessibility labels identify the source and score.

The media detail badges consume only existing source-specific cached fields.
A generic Trakt/TMDB community rating must not be labeled IMDb or Rotten
Tomatoes. Missing, invalid and out-of-range scores are hidden. This change does
not add a ratings scraper, API integration, dependency or network request.
