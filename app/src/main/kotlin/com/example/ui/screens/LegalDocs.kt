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

        Cobalt stores notes locally. It has no analytics SDK or automatic crash upload.
        Crash reports are stored locally. Selecting Send opens an email chooser;
        your email application sends the report only when you choose to send it.
        Reports contain exception details and may contain sensitive values in exception messages.

        Optional Gemini requests send the selected input to Google using your API key.
        Media features contact TMDB, Jikan and OMDb; Trakt connects only when configured.
        Saved links and remote media can contact the websites that host them.
        GitHub release checks are controlled in Release & Updates settings.
        See NETWORK.md in the source repository for the connection inventory and limitations.

        Local backups and sharing are explicit user actions. Android system backup
        currently remains governed by the application backup settings.
        Uninstalling the app can remove local data. Export anything you need to keep.
    """.trimIndent()

    val termsOfConditions = """
        **Terms and Conditions**

        Last updated: 22 July 2026

        **1. Agreement**
        Using Cobalt means you agree to these terms.

        **2. Your content**
        Whatever you save, notes, links, images, stays yours. You're responsible for what you store in the app.

        **3. Third-party integrations & keys**
        To search movies and TV shows, you may choose to supply your own TMDb API key. You are solely responsible for obtaining your key and complying with TMDb's terms of service. Cobalt provides this integration as a convenience and is not affiliated with TMDb.

        **4. Fair use**
        Don't use the app to store illegal content or abuse the AI/API integrations it relies on.

        **5. Uptime**
        Optional AI and media integrations depend on third-party services (Gemini, TMDb, Jikan, Trakt), so they're provided as-is. Interruptions can happen and aren't guaranteed against.

        **6. License**
        The app is licensed under AGPL-3.0-or-later. Source code, issues, and license text are all at [github.com/hanan-bhatti/cobalt](https://github.com/hanan-bhatti/cobalt).

        **7. Termination**
        You are responsible for complying with the terms of services you choose to contact.
    """.trimIndent()

    val faq = """
        Q: How do I save a link quickly?
        A: Use the home screen widget, or share directly from your browser using the "Share to" menu.

        Q: How does Movie & TV (Media) search work?
        A: To search movies and TV shows, you need to set your free TMDb API key in the 'Movie & TV Integration' profile settings. Anime search is powered by Jikan and works offline or online without any API key.

        Q: Can I save media directly from the OCR overlay?
        A: Yes! When you capture text on your screen, the overlay panel offers a direct media search and a one-tap save action.

        Q: Does OCR work offline?
        A: No. OCR runs through the Gemini API, so it needs an internet connection.

        Q: Is my data backed up?
        A: Only if you sign in and enable sync. The app works fully offline too, and syncs once you're back online.

        Q: Can I use Markdown in notes?
        A: Yes, standard syntax works: bold, italics, and links.

        Q: Is this app free?
        A: Yes, completely. No subscriptions, no ads, no paywalled features.

        Q: Is it open source?
        A: Yes. The full source is on GitHub at github.com/hanan-bhatti/cobalt under the AGPL-3.0-or-later license. Read the code, report bugs, or contribute.

        Q: Why did you build this?
        A: I wanted a fast, no-nonsense capture tool without ads, subscriptions, or a bloated feature list I'd never touch.
    """.trimIndent()

    val about = """
        **About Cobalt**

        Cobalt is a free, open-source capture app for links, notes, images, and code snippets, built to get things out of your head and into one place with as little friction as possible.

        I built it because I wanted a fast, no-nonsense capture tool that didn't come with ads, subscriptions, or a bloated feature set I'd never touch. Share something from any app, and it lands here, organized automatically.

        The full source is on GitHub at [github.com/hanan-bhatti/cobalt](https://github.com/hanan-bhatti/cobalt), licensed under AGPL-3.0-or-later. Read the code, file an issue, or fork it and make it your own.

        Built with Jetpack Compose.
    """.trimIndent()
}
