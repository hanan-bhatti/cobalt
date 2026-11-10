# Security Policy

## Local-data threat model

Text/code note bodies, recovery drafts and journals live in app-private internal
storage. Room retains metadata, short previews and file references. Android
sandboxing is not encryption: root, a compromised OS, a debuggable build or access
to an unlocked device may expose this data. Notes are currently plaintext. An
encrypted vault is future work and should implement the `NoteBodyStore` interface,
including streaming/range semantics, rather than bypassing it.

Android automatic backup and device transfer are disabled. Reinstallation/device
loss will lose app-private data unless the user explicitly exports it. Markdown
export/share is an explicit action. FileProvider grants are restricted to generated
share, feedback and crash-report cache directories; the notes directory is not
exposed. Crash reports are stored locally and sent only after the user launches
the email chooser. No automatic crash upload or analytics SDK is installed.

Gemini/TMDB keys and Trakt OAuth tokens use AES-256-GCM with an Android Keystore
key and per-write random IVs. The preference namespace/key is authenticated as
associated data. Existing plaintext credentials are replaced only after an
encrypted/decrypted round-trip succeeds. On key loss or authentication failure,
ciphertext is retained and integrations require credential re-entry. This does
not protect against a compromised running app and is not note-body encryption.
See [Android cryptography guidance](https://developer.android.com/privacy-and-security/cryptography)
and [Android Keystore](https://developer.android.com/privacy-and-security/keystore).
The deprecated AndroidX security-crypto API is not introduced; no dependency was
added for this implementation.

The application logger discards supplied log messages and throwable details,
preventing note/URL/token interpolation from reaching logcat. Debug informational
events are disabled in release. This reduces log detail; local user-reviewed
crash reports remain the explicit diagnostic channel.

Settings → Appearance → Hide content in Recents enables `FLAG_SECURE`. It also
blocks screenshots/recording of the app. It is off by default. This is a platform
privacy control, not a defence against root or a compromised OS.

Device verification, release/R8 smoke testing and the API/low-RAM matrix remain
required before release certification. Do not treat a debug APK as a security
audit or performance certification.

## Supported Versions

Since this project is in an early public beta, only the latest published release receives security updates. There is no long-term support (LTS) policy at this stage.

| Version | Supported |
|---|---|
| >= 2.3.0 | :white_check_mark: |
| < 2.3.0 | :x: |

---

## Reporting a Vulnerability

If you discover a security vulnerability in Cobalt, please report it immediately using one of the following channels to keep the report confidential until a fix is ready:

*   **GitHub Security Advisories (Preferred):** Go to the "Security" tab of this repository on GitHub and select "Report a vulnerability" to open a private draft advisory.
*   **Direct Email:** Alternatively, you can contact the maintainer directly at hannanbhatti2006@gmail.com.

When reporting a vulnerability, please include the following details to help us triage the issue:
*   **Affected Version:** The version of the app where you observed the issue (e.g., `v2.3.0`).
*   **Description:** A detailed explanation of the vulnerability and its potential impact.
*   **Steps to Reproduce:** Clear, step-by-step instructions (and proof-of-concept code, if applicable) showing how to trigger the vulnerability.
*   **Potential Impact:** Who is affected and what an attacker could achieve by exploiting the vulnerability.

---

## Response Expectations

Cobalt is currently a solo-developer project maintained in the author's spare time. While we take security reports seriously, we do not have enterprise-grade SLA response guarantees. 

You can expect:
*   An initial acknowledgment of your report within 3–5 business days.
*   An estimate of the fix timeline once the vulnerability is successfully triaged and verified.

---

## Disclosure Policy

We appreciate your cooperation in practicing responsible disclosure. Please:
*   Give us reasonable time to investigate, address, and deploy a patch for the reported issue before disclosing details publicly or sharing them with third parties.
*   Avoid exploiting the vulnerability to access, modify, or destroy user data.

Unless you request to remain anonymous, we will gladly credit you for your help in securing the Cobalt community when the security advisory is published and the patch is released.
