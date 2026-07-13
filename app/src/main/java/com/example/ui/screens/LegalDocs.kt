package com.example.ui.screens

object LegalDocs {
    val privacyPolicy = """
        **Privacy Policy**
        
        Last updated: Today
        
        **1. Data Collection**
        Second Brain is designed as a personal knowledge archive. By default, data is stored locally on your device. If you enable cloud sync, your data is securely stored using Google Firebase.
        
        **2. Use of AI**
        When using the OCR or "Smart Organization" features, your images or notes may be processed by the Gemini API. This data is not used to train Google's foundation models.
        
        **3. Analytics**
        We do not collect identifiable telemetry or usage analytics without explicit consent.
        
        **4. Third-Party Services**
        We use Firebase for authentication and database services. Their respective privacy policies apply to the data stored on their servers.
        
        **5. Contact**
        For data deletion requests, simply delete your account from the settings panel, which will erase your remote data.
    """.trimIndent()

    val termsOfConditions = """
        **Terms and Conditions**
        
        Last updated: Today
        
        **1. Acceptance of Terms**
        By creating an account or using Second Brain, you agree to these terms.
        
        **2. User Content**
        You retain all rights to the notes, links, and images you capture. You are solely responsible for the content you store.
        
        **3. Acceptable Use**
        You agree not to use the app for illegal purposes, to store illicit materials, or to abuse the provided API integrations.
        
        **4. Service Availability**
        While we strive for 100% uptime, the cloud-sync and AI features are provided "as is" and may be subject to interruptions.
        
        **5. Termination**
        We reserve the right to suspend accounts that violate these terms or abuse system resources.
    """.trimIndent()

    val faq = """
        **Frequently Asked Questions**
        
        **Q: How do I capture a link quickly?**
        A: Use the Home Screen Widget or the "Share to" menu from your browser to save links directly into Second Brain.
        
        **Q: Does OCR work offline?**
        A: Currently, OCR text extraction relies on the Gemini AI model and requires an active internet connection.
        
        **Q: Are my notes backed up?**
        A: Yes, if you are signed in, your notes are automatically synchronized to the cloud. You can use the app offline and it will sync when you reconnect.
        
        **Q: Can I use Markdown?**
        A: Yes! You can format your text notes with **bold**, *italics*, and [links](https://example.com) using standard Markdown syntax.
    """.trimIndent()

    val about = """
        **About Second Brain**
        
        Version 1.0.0
        
        Second Brain is a minimalist, unified inbox for your mind. Designed to reduce friction when capturing ideas, links, and text, it provides a quiet, offline-first environment for your thoughts.
        
        **Design Philosophy**
        - Speed of capture
        - Minimalist editorial aesthetics
        - Quiet typography
        
        Crafted with Jetpack Compose.
    """.trimIndent()
}
