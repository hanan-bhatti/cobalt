/*
 * Second Brain - A universal capture and personal knowledge archive
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

         Last updated: 18 July 2026

         **1. Data storage**
         Second Brain stores your data locally on your device by default. If you turn on cloud sync, your data is stored using Firebase. Nothing leaves your device unless you enable sync yourself.

         **2. AI features**
         The OCR and smart-organization features send your images or notes to the Gemini API for processing. Google does not use this data to train its models.

         **3. Analytics**
         Second Brain uses Firebase Analytics to understand how the app is used, things like which screens are opened, whether a note was created, edited, or deleted, and whether sign-in succeeded. This helps identify bugs and prioritize what to improve. No note content, search queries' full text is not stored beyond what's needed to log that a search happened, and no personally identifying information is sent as part of these events. Firebase Analytics also collects standard device and usage data (device model, OS version, country, session length) automatically, as governed by Google's privacy policy.

         **4. Third-party services**
         Firebase handles authentication, database storage, and analytics. Google's privacy policy covers whatever is processed or stored on their servers.

         **5. Open source**
         Second Brain is free, open-source software licensed under AGPL-3.0-or-later. You can read every line of code, verify these claims yourself, or fork it, at [github.com/hanan-bhatti/second-brain](https://github.com/hanan-bhatti/second-brain).

         **6. Deleting your data**
         Delete your account from the settings panel and your remote data is erased.
     """.trimIndent()

     val termsOfConditions = """
         **Terms and Conditions**

         Last updated: 18 July 2026

         **1. Agreement**
         Using Second Brain means you agree to these terms.

         **2. Your content**
         Whatever you save, notes, links, images, stays yours. You're responsible for what you store in the app.

         **3. Fair use**
         Don't use the app to store illegal content or abuse the AI/API integrations it relies on.

         **4. Uptime**
         Cloud sync, analytics, and AI features depend on third-party services (Firebase, Gemini), so they're provided as-is. Interruptions can happen and aren't guaranteed against.

         **5. License**
         The app is licensed under AGPL-3.0-or-later. Source code, issues, and license text are all at [github.com/hanan-bhatti/second-brain](https://github.com/hanan-bhatti/second-brain).

         **6. Termination**
         Accounts that abuse the service or violate these terms can be suspended.
     """.trimIndent()

    val faq = """
        Q: How do I save a link quickly?
        A: Use the home screen widget, or share directly from your browser using the "Share to" menu.

        Q: Does OCR work offline?
        A: No. OCR runs through the Gemini API, so it needs an internet connection.

        Q: Is my data backed up?
        A: Only if you sign in and enable sync. The app works fully offline too, and syncs once you're back online.

        Q: Can I use Markdown in notes?
        A: Yes, standard syntax works: bold, italics, and links.

        Q: Is this app free?
        A: Yes, completely. No subscriptions, no ads, no paywalled features.

        Q: Is it open source?
        A: Yes. The full source is on GitHub at github.com/hanan-bhatti/second-brain under the AGPL-3.0-or-later license. Read the code, report bugs, or contribute.

        Q: Why did you build this?
        A: I wanted a fast, no-nonsense capture tool without ads, subscriptions, or a bloated feature list I'd never touch.
    """.trimIndent()

    val about = """
        **About Second Brain**

        Second Brain is a free, open-source capture app for links, notes, images, and code snippets, built to get things out of your head and into one place with as little friction as possible.

        I built it because I wanted a fast, no-nonsense capture tool that didn't come with ads, subscriptions, or a bloated feature set I'd never touch. Share something from any app, and it lands here, organized automatically.

        The full source is on GitHub at [github.com/hanan-bhatti/second-brain](https://github.com/hanan-bhatti/second-brain), licensed under AGPL-3.0-or-later. Read the code, file an issue, or fork it and make it your own.

        Built with Jetpack Compose.
    """.trimIndent()
}
