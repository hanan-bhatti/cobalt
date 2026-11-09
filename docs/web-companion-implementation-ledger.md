# Local web companion implementation and review

Branch: `feature/local-web-companion`. App version remains **2.2.3 / code 19**. This branch does not create or publish a 2.3.0 release.

## Gemini changes reviewed

The review covers the complete diff from `d488547` through `070b609`: 12 commits, 21 files, including the server, authentication, session registry, Android settings/lifecycle integration, web assets, tests, and this ledger. The later `b88bb69` commit contains the screenshot campaign rather than web-server behavior.

| Commit | Change |
| --- | --- |
| `c0be260` | Rotating PIN, sessions, lockout and request rate limiting. |
| `e6871b5` | Mobile/web editor session registry and ViewModel lock hook. |
| `f052272` | Release mobile edit lock on ViewModel teardown. |
| `48eaa6c` | Document back/cancel/save lock-release paths. |
| `aebc722` | Embedded Kotlin/Java socket HTTP server and models. |
| `2854d7e` | Auth, item listing/detail, media and static routes. |
| `fe41801` | HTML/CSS/JavaScript reader, search and theme export. |
| `c830662` | Android Web Companion settings and notification. |
| `5ab741c` | Exhaustive settings branch, repository visibility and suspend fixes. |
| `22ad05f` | Restore private repository and expose ViewModel lifecycle operations. |
| `906064a` | Annotation/clipboard/IP-resolution fixes; survey/share warning fixes. |
| `070b609` | Launch asynchronous clipboard operation in a coroutine. |

## Defects found in the original implementation

- There was no edit UI, save route, or route using the web edit-lock registry. Editing/saving was not implemented.
- `getItemForOpen()` intentionally loads bodies only below 200 KB. The web detail route treated the bounded metadata result as a complete body, returning empty or partial text instead of a complete larger note.
- Search passed raw text to SQLite FTS and swallowed failures as empty results. Folder filtering used substring matching.
- Status hardcoded version `2.3.0` although the build was `2.2.3`.
- Markdown inserted raw note HTML into `innerHTML`, and accepted unsafe link/image URLs.
- The client expected a different lockout JSON field and re-enabled its login button during lockout. It did not reliably report failed detail/list requests.
- OPTIONS bypassed Host checks; wildcard CORS was unnecessarily enabled. HTTP parsing accepted invalid/oversize/duplicate content lengths and truncated headers. Client sockets were unbounded.
- Logout/PIN changes did not release web locks; expired locks could be renewed. Automatic PIN rotation did not update the displayed PIN.
- Media read an arbitrary stored path, loaded the whole file without a bound, and advertised byte ranges without implementing them.
- The notification is an ordinary ongoing notification, **not an Android foreground service**. The previous statements about guaranteed background operation, exact zero CPU/battery use, full Markdown support, and F-Droid approval were unsupported.

## Corrections

- Repository-backed full note reads and durable TEXT/CODE edits, using native note generations, history, metadata and search indexing. Both timestamp and content hash identify the base revision. Save rechecks the active session and edit lock before persistence and before committing metadata.
- Edit/cancel/save controls, 20-second lock heartbeat, explicit save errors and unsaved-draft discard confirmation. Draft text remains visible after a failed write.
- Native search sanitization, exact folder membership, attachment filtering and actual build-version reporting.
- Escaped Markdown with allowed links/local images, fenced code and basic tables. Code and notes above 200,000 characters use a plain-text reader rather than building a large Markdown DOM.
- Same-origin requests, Host/Origin checks on OPTIONS, bounded HTTP parsing, eight concurrent client sockets, security response headers and socket cleanup on shutdown.
- Correct lockout countdown, locale-independent numeric PINs, reactive PIN state, bounded session/client tracking and web-lock cleanup without clearing mobile locks.
- Attachment type/deletion/private-root checks and bounded reads. Unsupported range claims removed. Missing static assets return 404.
- Android settings describe the actual server lifetime and HTTP connection rather than making unmeasured guarantees.

## Supported scope and limits

- Existing text/code notes can be read and edited. Links, images, audio/video and media-library entries can be browsed; only supported local attachments can be opened here. Creating/deleting items, editing movie metadata, backup/restore, sync, encryption and biometric locking are not part of these changes.
- Web note bodies are limited to **5,000,000 UTF-8 bytes**; attachments to **10 MiB**. Larger items stay intact and should be opened in the phone app. This is not a reduction in native note capacity.
- Search uses the native bounded FTS index (a 32 KiB body prefix, at most 200 body matches), plus item title/preview matching. It does not claim exhaustive full-body search across huge notes.
- The server is opt-in and tied to the app/ViewModel process. Android can terminate it in the background. There is no foreground service or wake lock.
- Traffic uses HTTP on the local network. No TLS or network encryption is implemented.
- Emulator NAT addresses are not directly reachable from the laptop. For this emulator use `adb -s emulator-5554 forward tcp:18080 tcp:8080`, then open `http://127.0.0.1:18080` and enter the PIN shown in Cobalt. On a physical phone, use its displayed local address from the same reachable network.
- Drafts are held in the browser page, not silently saved to a server or browser database. Closing/reloading a dirty editor requires confirmation; copying the draft is available when a save fails.

## Verification

The baseline installed debug app accepted PIN login and returned all 500 existing items using emulator forwarding. Browser editing was absent in the reviewed baseline source.

Verification is complete for the documented scope: 48 focused JVM tests, 9 client regressions, 2 Android instrumentation methods, and the real-server Chromium workflow passed. The original 500 rows were retained exactly; the installed APK matches the Gradle artifact. See [the runtime review](web-companion-runtime-review-2026-11-10.md) for evidence and limitations. These results do not replace broader release qualification or physical-device testing.
