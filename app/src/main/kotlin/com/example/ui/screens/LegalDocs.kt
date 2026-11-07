/*
 * Cobalt - A universal capture and personal knowledge archive
 * Copyright (C) 2026 Hanan Bhatti
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.example.ui.screens

object LegalDocs {
    val privacyPolicy = """
        **Privacy Policy**

        Last updated: 3 November 2026

        **1. Overview and Local Storage**

        Cobalt is a free, open-source personal capture and knowledge archive. Your notes, captured media, and organizational data are stored locally on your device. Cobalt does not operate a user account system, and there is no Cobalt cloud note synchronization service.

        Saved notes, Markdown files, media recordings, and database tables are stored in Cobalt's private application storage without separate app-level encryption. Optional service credentials—specifically your Gemini API key, TMDb API key, and Trakt OAuth tokens—are stored encrypted using Android Keystore (AES-256-GCM). Hardware backing for this storage depends on your device hardware and Android platform support.

        **2. Optional AI Features**

        When you use Gemini-powered OCR, Cobalt transmits the cropped screenshot region you selected. If you do not select a crop region, the entire captured screenshot is transmitted. When you use voice memo transcription, Cobalt sends the recorded audio file. When you format speech text, Cobalt sends the transcribed text and formatting instructions.

        These requests are sent to Google's Gemini API over HTTPS using the Gemini API key you provide. Cobalt tracks aggregate token counts and request counts locally on your device to show your usage; these counters are never uploaded or shared.

        **3. Media and Remote Content**

        When you search for movies and TV shows, Cobalt contacts The Movie Database (TMDb) using your configured API key. Anime and manga searches use your selected Jikan v4 server. A local server needs a connection to that server; saved catalogue details remain available offline. Search providers and source priority are controlled in Media & Providers. Uncached remote images will not load while offline.

        Posters, backdrops, cast photos, user avatars, and other remote images load automatically when media cards or details are displayed. When you save or share a URL, Cobalt automatically contacts the host website to fetch page titles and preview metadata. Opening saved items that reference remote media may download and cache those media files locally. Cobalt does not contact OMDb; that integration is unused.

        **4. Trakt Synchronization**

        Connecting Trakt is optional. Authentication primarily uses the OAuth 2.0 Authorization Code flow with Proof Key for Code Exchange (PKCE) via your web browser and a verified HTTPS App Link callback (`https://cobalt.hanan-bhatti.site/callback`), or alternatively the Device Code flow at `trakt.tv/activate`. Cobalt never handles, sees, or stores your Trakt password.

        When connected, Cobalt reads your Trakt profile, watched movie and TV history (including episode plays), ratings, and watchlist. Cobalt sends updates to Trakt when you mark movies or episodes as watched, rate items, or change watchlist status. Cobalt runs an initial sync upon connecting and updates when you tap "Sync Now". Cobalt does not run scheduled periodic background sync.

        Disconnecting Trakt clears your stored Trakt tokens and session details locally and attempts server-side revocation on Trakt's servers. If your device is offline or the revocation request fails, local disconnect still completes, and you can revoke Cobalt's access at any time through your Trakt account settings on trakt.tv. Previously synchronized items remain in your local Cobalt library. Disconnecting Trakt, uninstalling Cobalt, or deleting local items does not remove your watch history, ratings, or watchlist from your Trakt account.

        **5. YouTube Trailers**

        Trailer playback in media details uses an embedded Android WebView that loads the YouTube embed player. Network requests to YouTube and Google begin automatically whenever you open the detail screen of a movie or TV show that includes a trailer link.

        The embedded player runs with JavaScript, web storage, and third-party cookies enabled. YouTube and Google process these requests under their own terms and privacy practices. This external web playback is independent of Cobalt, which contains no analytics or tracking SDKs.

        **6. Crash Reports and Feedback**

        Cobalt does not include an analytics SDK and does not automatically upload crash reports.

        When an unexpected crash occurs, Cobalt writes a report locally. Crash reports include the app version, device manufacturer, device model, Android OS version, thread name, timestamp, exception message, and technical stack trace. Because exception messages and stack traces are not redacted, they may contain sensitive details such as file paths, identifiers, or URLs associated with the crash.

        Selecting "Send" opens an external email chooser. The report is sent only if you complete the action inside your email application. The developer receives the report and your email address. If you use in-app feedback export, a Markdown copy is saved to the app's cache directory and shared through your chosen application. Crash report and feedback files in cache are not automatically deleted.

        **7. Sharing, Retention, and Deletion**

        Exporting or sharing content is initiated by you. Note exports are generated as plaintext Markdown; app settings and API credentials are excluded, but any personal information or secrets written inside your notes will be included in the export. Shared copies are handled by the destination app you choose. Temporary share copies remain in the app cache until cleared.

        Deleted items move to Trash and are kept for 30 days before automatic removal, or you can delete them permanently at any time. Automatic Android backup and device-to-device transfers are disabled in Cobalt's configuration, though behavior can vary across customized operating system builds. Clearing app storage or uninstalling Cobalt removes your local notes, media, and credentials permanently. Externally saved exports and data stored on third-party services like Trakt remain unaffected.

        **8. App Updates**

        Cobalt can check GitHub for application releases. Automatic release checks are disabled by default. Manual checks in the Release Updates screen contact GitHub's API. Cobalt sends standard HTTP request headers without user identifiers or personal data. Cobalt does not automatically download or install app updates; update links open the GitHub releases page in your browser.

        **9. Third Parties and Contact**

        Third-party services—including Google Gemini, The Movie Database (TMDb), Jikan, Trakt, YouTube, and GitHub—operate independently of Cobalt. Their own terms and privacy policies govern how they handle connection metadata and request content.

        Cobalt uses Trakt but is not endorsed or certified by Trakt. Cobalt uses the TMDB API but is not endorsed or certified by TMDB.

        For privacy questions, contact hannanbhatti2006@gmail.com.

        Cobalt's source code is available at:
        [github.com/hanan-bhatti/cobalt](https://github.com/hanan-bhatti/cobalt)
    """.trimIndent()

    val termsOfConditions = """
        **Terms and Conditions**

        Last updated: 3 November 2026

        **1. Agreement and Open Source License**

        By using Cobalt, you agree to these terms. Cobalt is free and open-source software licensed under the GNU Affero General Public License (AGPL-3.0-or-later). Nothing in these terms limits your rights under that license. Source code and full license text are available at [github.com/hanan-bhatti/cobalt](https://github.com/hanan-bhatti/cobalt).

        **2. Your Content and Responsibility**

        You retain full ownership of the notes, links, images, media, and other content you store in Cobalt. Because Cobalt stores your information locally without a cloud note backup service, you are responsible for maintaining your own backups and exports of important content.

        **3. Third-Party Services and API Keys**

        Cobalt includes optional integrations with external services such as Google Gemini, The Movie Database (TMDb), Jikan, Trakt, and YouTube. You may choose to provide your own API keys or link third-party accounts.

        Cobalt uses Trakt but is not endorsed or certified by Trakt. Cobalt uses the TMDB API but is not endorsed or certified by TMDB.

        You are responsible for obtaining any required personal keys, keeping them secure, and complying with the terms of use established by each third-party provider. Cobalt is independent and is not affiliated with or endorsed by these third-party services.

        **4. Service Availability and Offline Use**

        Cobalt is designed as an offline-first capture tool for your locally saved data. However, external integrations—such as AI OCR, audio transcription, online media searches, remote image loading, link preview scraping, trailer playback, and Trakt synchronization—require an active internet connection and depend on the operational availability of third-party APIs and servers.

        **5. Disclaimer of Warranties**

        As provided in the GNU Affero General Public License, Cobalt is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for full details.

        **6. External Accounts and Service Terms**

        When you connect an external service or send data to an external provider through Cobalt, your relationship with that provider is governed by their respective terms of service and policies. You may disconnect integrations or revoke account authorizations at any time.
    """.trimIndent()

    val faq = """
        Q: How do I save a link or content quickly?
        A: Use the quick capture widget or share text, links, or media directly from any browser or app using Android's share menu.

        Q: Does sharing a link make network requests?
        A: Yes. When you save or share a URL, Cobalt automatically contacts the host website to fetch page titles and preview metadata.

        Q: How does movie and TV search work?
        A: Searching movies and TV shows requires your own free TMDb API key configured in Profile settings. Anime and manga search use Jikan without an API key. You can configure a private Jikan v4 server and read saved details offline.

        Q: Can I view anime and media offline?
        A: You can view previously saved media and anime details offline. However, new searches require an internet connection, and remote images that are not yet cached will not load while offline.

        Q: Does OCR or voice transcription work offline?
        A: No. OCR text recognition and audio transcription run through Google's Gemini API, so they require an internet connection and your configured Gemini API key.

        Q: Can I save media directly from the OCR overlay?
        A: Yes. When you capture text on your screen, the overlay panel offers a direct media search and a one-tap save action.

        Q: Is my data backed up to the cloud?
        A: Cobalt has no Cobalt user accounts and no cloud note sync. Your notes and media stay locally on your device. Optional Trakt sync only synchronizes your movie and TV watch tracking with Trakt.

        Q: What happens if I uninstall Cobalt or clear storage?
        A: Uninstalling Cobalt or clearing app storage deletes your local notes, media, and stored API keys. Android backup is disabled, so export any notes you wish to keep before uninstalling.

        Q: How does Trakt sign-in work?
        A: Cobalt signs in securely via your web browser using OAuth 2.0 with PKCE (Proof Key for Code Exchange) and redirects back to Cobalt through a verified HTTPS callback (cobalt.hanan-bhatti.site/callback). Alternatively, you can use the Device Code flow at trakt.tv/activate. Cobalt never asks for, sees, or stores your Trakt password.

        Q: Does disconnecting Trakt or deleting items in Cobalt remove them from Trakt?
        A: No. Disconnecting Trakt clears your local session and attempts server-side token revocation, but it does not delete your remote data on Trakt. If offline, local disconnect still finishes. Deleting local items in Cobalt also does not delete your watch history or ratings on Trakt.

        Q: Can I use Markdown in notes?
        A: Yes. Standard syntax such as bold, italics, headings, bullet lists, and links is supported.

        Q: Is Cobalt free and open source?
        A: Yes. Cobalt is completely free with no ads, subscriptions, or tracking SDKs. The full source code is on GitHub under the AGPL-3.0-or-later license.
    """.trimIndent()

    val about = """
        **About Cobalt**

        Cobalt is a free, open-source capture app for links, notes, images, and code snippets, built to get things out of your head and into a personal local archive with as little friction as possible.

        I built it because I wanted a fast, no-nonsense capture tool without ads, subscriptions, or tracking. Notes and media remain stored locally on your device, while optional online integrations provide AI transcription and media metadata when needed.

        Cobalt uses Trakt but is not endorsed or certified by Trakt. Cobalt uses the TMDB API but is not endorsed or certified by TMDB.

        The full source code is on GitHub at [github.com/hanan-bhatti/cobalt](https://github.com/hanan-bhatti/cobalt), licensed under AGPL-3.0-or-later. Read the code, file an issue, or fork it and make it your own.

        Built with Jetpack Compose.
    """.trimIndent()

    val openSourceLicenses = """
        **Open Source Licenses & Third-Party Assets**

        Cobalt is free and open-source software licensed under AGPL-3.0-or-later. It incorporates third-party open-source components and font software under their respective permissive and copyleft licenses.

        ---

        ### Bundled Local Fonts (SIL Open Font License 1.1)

        The following font software is bundled locally inside Cobalt. All font files are licensed under the SIL Open Font License, Version 1.1 (OFL-1.1).

        **1. Inter**
        - Copyright 2020 The Inter Project Authors (https://github.com/rsms/inter)
        - License: SIL Open Font License 1.1

        **2. Literata**
        - Copyright 2017 The Literata Project Authors (https://github.com/googlefonts/literata)
        - License: SIL Open Font License 1.1

        **3. Source Serif 4**
        - Copyright 2014 The Source Serif 4 Project Authors (https://github.com/adobe-fonts/source-serif)
        - License: SIL Open Font License 1.1

        **4. Atkinson Hyperlegible**
        - Copyright 2020 Braille Institute of America, Inc.
        - License: SIL Open Font License 1.1

        **5. Lexend**
        - Copyright 2018 The Lexend Project Authors (https://github.com/googlefonts/lexend), with Reserved Font Name "RevReading Lexend"
        - License: SIL Open Font License 1.1

        **6. JetBrains Mono**
        - Copyright 2020 The JetBrains Mono Project Authors (https://github.com/JetBrains/JetBrainsMono)
        - License: SIL Open Font License 1.1

        ---

        ### SIL OPEN FONT LICENSE (Version 1.1 - 26 February 2007)

        **PREAMBLE**
        The goals of the Open Font License (OFL) are to stimulate worldwide development of collaborative font projects, to support the font creation efforts of academic and linguistic communities, and to provide a free and open framework in which fonts may be shared and improved in partnership with others.

        **PERMISSION & CONDITIONS**
        Permission is hereby granted, free of charge, to any person obtaining a copy of the Font Software, to use, study, copy, merge, embed, modify, redistribute, and sell modified and unmodified copies of the Font Software, subject to the conditions stated in the full license text.

        ---

        ### Third-Party Libraries

        - **Sora Editor**: LGPL-2.1 (https://github.com/Rosemoe/sora-editor)
        - **AndroidX & Jetpack Compose**: Apache License 2.0
        - **Kotlin & Coroutines**: Apache License 2.0
        - **CommonMark**: BSD-2-Clause
        - **Coil**: Apache License 2.0
        - **OkHttp**: Apache License 2.0
        - **Retrofit & Moshi**: Apache License 2.0
        - **Jsoup**: MIT License
    """.trimIndent()
}
