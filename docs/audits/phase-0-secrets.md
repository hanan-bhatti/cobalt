# Phase 0 secret audit (2026-11-02)

No history was rewritten. Credential values are intentionally omitted.

A local pattern scan inspected reachable history blobs up to 3 MB for Google API
keys, GitHub tokens, AWS access keys and PEM private-key headers, and identified
sensitive filenames. This is a limited scan, not proof that no other secret exists.
Commit hashes below identify a change involving each matching blob (not necessarily
its first introduction).

| Path | Commit | Finding |
| --- | --- | --- |
| `app/src/play/google-services.json` | `4f7a0751abe021c54cb680b77283de4b4a993a25` | Google API key, sensitive configuration/signing file |
| `app/src/play/google-services.json` | `4f7a0751abe021c54cb680b77283de4b4a993a25` | Google API key, sensitive configuration/signing file |
| `app/src/play/google-services.json` | `f120a5c88cfcc53249c207fe44d6b3bc7d69dc71` | Google API key, sensitive configuration/signing file |
| `app/src/play/google-services.json` | `f59c01e97ccdaac6048b9f4e2d08c19d0c7e4466` | Google API key, sensitive configuration/signing file |
| `app/google-services.json` | `cabed7b0866a0f191f9ee41235e1dfa40c39cce0` | Google API key, sensitive configuration/signing file |

Ignored local signing material also exists: `debug.keystore`, `release-key.jks`
and `release-key-base64.txt`. These files were not printed, changed or committed.
The scan found no reachable tracked signing keystore under these filenames.
The public debug signing password in Gradle is not a release credential.

Rotate/restrict historical Google keys as appropriate. Remaining user-configured
API keys and OAuth storage need the separate Phase 3 review. Generic hardcoded
passwords, arbitrary token formats, large blobs and unreachable objects were not
exhaustively scanned.

A subsequent generic assignment check found a bundled TMDB API key in the
repository and Trakt sync engine. History reference:
`c99cf95d173c07971e16201b02f9cf9096f5a6d0`. The fallback value is removed from
current code; user-configured TMDB keys remain supported. Rotate the historical
key. The Trakt default client identifier is public OAuth application metadata;
no bundled client secret was identified by this check.
