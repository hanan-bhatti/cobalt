# Link metadata validation

Validated on November 8–9, 2026, using the running Android API 37 emulator (`emulator-5554`). The final release APK was installed with `adb install -r`; the production package's original installation date remained November 6, 2026. Existing app data was preserved.

The implementation changes metadata extraction in `CobaltRepository.fetchLinkMetadata` and `LinkMetadataExtractor`. Clipboard handling, sharing, capture UI, storage schemas, and dependencies were not changed by this task. The instrumented UI check uses the existing manual capture field and cancels every draft without saving archive items.

Extraction runs on the device using existing Jsoup and Android JSON support. It reads Open Graph, Twitter cards, microdata, JSON-LD, and public embedded YouTube, Instagram, and TikTok data. The standard fields follow the [Open Graph specification](https://ogp.me/) and [Schema.org VideoObject](https://schema.org/VideoObject). Platform-specific embedded data was checked against public pages and emulator results.

YouTube watch, short, embed, and live formats are normalized for the metadata fetch only. The entered and saved URL stays unchanged. Social pages with missing fields receive one link-preview user-agent fallback, which fills missing values while retaining existing metadata. Login, challenge, and generic platform shell metadata is filtered. HTTP 429 responses are not retried.

There is no backend, API integration, credential requirement, WebView, browser engine, or added dependency. Fetches use an eight-second timeout and a one-MiB HTML limit; Pinterest uses two MiB because its public metadata occurs after a large route manifest. There are at most two fetch attempts, with ordinary HTTP redirects handled by Jsoup.

The final extractor returned metadata for **20 of 20 public sample URLs**. All **18 provided thumbnail URLs** were successfully fetched as images. The Wikipedia article and Facebook video supplied text without an image.

| Link types | Samples | Metadata returned | Available thumbnails |
| --- | ---: | ---: | ---: |
| Website and article | 2 | 2 | 1 |
| YouTube watch, short URL, Shorts-format, real Short, embed | 5 | 5 | 5 |
| Instagram post, Reel, captioned embed | 3 | 3 | 3 |
| TikTok older and recent videos | 2 | 2 | 2 |
| X and Twitter posts | 2 | 2 | 2 |
| Facebook video and Reel | 2 | 2 | 1 |
| Reddit post | 1 | 1 | 1 |
| LinkedIn company page | 1 | 1 | 1 |
| Pinterest pin | 1 | 1 | 1 |
| Vimeo video | 1 | 1 | 1 |

`LinkMetadataDeviceTest` passed four tests in 64.515 seconds, covering parser formats, escaped data, embedded video identity checks, YouTube URL variants, real loopback redirects/404 responses through the repository, and the twenty live samples. `LinkMetadataUiTest` passed its live capture check in 27.508 seconds: Instagram, TikTok, and YouTube retained the entered URL and displayed captions and thumbnails. The test waits for the actual image to paint before saving screenshots.

The final debug and release builds passed; release shrinking and vital lint completed successfully. `git diff --check` passed.

The debug APK increased from **34,826,385** to **34,934,767 bytes**, a difference of **108,382 bytes (0.103 MiB)**. Only `classes9.dex` and `classes10.dex` changed; no assets or resources were added. This is a debug before/after comparison. The final release APK is **8,429,824 bytes (8.039 MiB)**.

Evidence is available locally:

- [Twenty live URLs and returned metadata](../build/link-metadata-validation/live-links.json)
- [UI metadata and unchanged URLs](../build/link-metadata-validation/ui-results.json)
- [Device test result](../build/link-metadata-validation/final-device-test.txt)
- [UI test result](../build/link-metadata-validation/final-ui-test.txt)
- [APK size comparison and hashes](../build/link-metadata-validation/apk-size.json)
- [Instagram screenshot](../build/link-metadata-validation/metadata-ui-instagram.png)
- [TikTok screenshot](../build/link-metadata-validation/metadata-ui-tiktok.png)
- [YouTube screenshot](../build/link-metadata-validation/metadata-ui-youtube.png)
- [Final release APK](../build/editor-validation/_app/outputs/apk/release/app-release.apk)

These results cover the listed public samples. Private, deleted, restricted, or blocked pages can still expose no metadata. The extractor retains the source URL and returns only fields available in the site's public response.
