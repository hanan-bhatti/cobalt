# Share review and background link previews

Requested 6 November 2026; implementation/testing is in progress.

## Required behavior

Incoming Android shares open a reviewable Capture draft. Save persists the item,
then closes the share capture and returns to the source app. Cancel never creates
an archive item. File import from Add remains a separate explicit import flow with
a persistent result bottom sheet. Large shared text files stage through private
streams rather than a giant resident String. Multiple shared documents require
review/explicit confirmation rather than silent background saves.

Every saved URL—from Side Dock, OCR, manual Capture, sharing or quick entry—uses
the same background enrichment path. Save does not wait on the network. Pending
work survives closing a Dock/share window or process death. No proprietary SDK,
third-party scraping proxy, Cloudflare-bypass service or Git dependency is needed.

## Problems verified in current source

- QuickShareActivity persists ordinary shares immediately and has its own Jsoup
  parser, while MainActivity's Capture flow has different behavior.
- buildItemWithFetchedMetadata can run before saving. A slow/blocked website can
  delay a user action that should be local and immediate.
- Active preview, OCR save, Side Dock save and quick-text save independently fetch
  and rewrite metadata. Several prefer fetched text over linkDescription without
  knowing whether it came from the user or AI.
- Fetch errors become three null fields. HTTP errors/challenge pages are not
  distinguished from real metadata, making failures hard to explain or retry.
- Whole-item background writes can overwrite newer edits, folder assignments or
  deletion state. Background results need targeted, conditional updates.

## Single saved description

Final user correction, 6 November: keep **one description column**, the existing
`saved_items.linkDescription`. Do not add separate user/page/AI description
columns or retain hidden alternative descriptions.

Normal background fetching fills a missing description. An untouched OCR AI
suggestion may be replaced by actual page text; if page text is unavailable, the
current AI fallback remains. A user-entered or explicitly cleared Capture value
is preserved. Queue state records only whether the current value is an eligible
fallback, not another copy of its text.

Explicit Refresh retains the existing Capture replacement interaction, including
replacement of a manually entered description. Edits made after Refresh starts
are protected from its late response. Preserve the existing Capture field layout
and visibility after extraction; do not add another description step.

Persist fetch-job state in a small separate Room queue (item ID, URL, generation,
fallback eligibility, pending flag, attempt count). The additive migration leaves
all existing descriptions and note bodies untouched, and does not fetch old
bookmarks automatically. Only apply targeted updates to a live, matching URL and
description; never reinsert a stale whole item.

## Fetch policy

- HTTP(S) only; normalise for requests without silently rewriting the saved URL.
- Resolve ordinary relative/absolute redirects with a small hop limit and a total
  request deadline. Reject malformed/unsupported destinations and loops.
- Bound response bytes and parser work. Parse Open Graph, Twitter and standard
  HTML description/title/image tags; resolve relative image paths against the
  final page URL. Add bounded structured-data fallback where useful.
- Never execute page scripts. Detect challenge/error pages before accepting their
  title or description; "Just a moment" is not a usable bookmark description.
- Record success/no-metadata/offline/retryable/blocked/permanent-error outcomes.
  Retry transient failures with bounded backoff and respect bounded Retry-After.
  Avoid retrying authentication/challenge blocks endlessly.
- Automatic failures must preserve existing usable descriptions/images. Keep the
  explicit Refresh replacement interaction distinct from automatic retries. A user
  can retry or open the website; never promise all protected pages can be read.

Use one durable pending queue and a bounded consumer, not one unbounded coroutine
or network worker per recognised link. Batch OCR enqueues records together. Work
must pause in startup safe mode and must not resurrect deleted/trashed items.

## Verification

Cover single-column automatic enrichment and explicit clearing; manual Refresh
replacement of a user description (without resurrecting the old override); edits
while a refresh is in flight; unchanged Capture field visibility after extraction;
legacy preservation; slow
responses; missing tags; relative images; redirect loops; 403/challenge HTML;
429/5xx/offline; malformed HTML/URLs; bounded body/deadline; cancellation; URL
changes, manual edits and deletion during fetch; process death/retry; rapid bulk
OCR; all save entry points. Test Android sharing for text, links, images, audio,
video, text-file URIs and multiple documents, with explicit Save/Cancel and source
app return. Never require a real personal account or mutate remote history for
metadata fixture tests.

## Primary references

- [Open Graph protocol](https://ogp.me/): canonical title/image/description metadata.
- [jsoup Connection documentation](https://jsoup.org/apidocs/org/jsoup/Connection):
  redirect, deadline and body-size controls; parser behavior alone is not a guarantee
  of access through browser challenges.
- [Cloudflare Challenge Pages](https://developers.cloudflare.com/cloudflare-challenges/challenge-types/challenge-pages/):
  challenge HTML is returned for a browser to render and solve. Ordinary redirects
  and interactive access challenges require different handling.
