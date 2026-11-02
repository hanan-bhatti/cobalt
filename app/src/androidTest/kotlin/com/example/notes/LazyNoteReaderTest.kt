package com.example.notes

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.LazyMarkdownNote
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LazyNoteReaderTest {
    @get:Rule val compose = createComposeRule()
    @Test fun longNoteRendersOnlyVisibleBlocksAndCanScroll() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName.endsWith(".debug"))
        val store = PrivateMarkdownStore(context)
        val id = java.util.UUID.randomUUID().toString()
        val body = (1..3_000).joinToString("\n\n") { "Paragraph $it" }
        val ref = body.byteInputStream().use { store.writeAtomic(id, it) }
        try {
            compose.setContent { MaterialTheme { LazyMarkdownNote(id, ref.hash, ref.bytes, 0, Modifier.fillMaxSize()) } }
            compose.waitUntil(15_000) { compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty() }
            assertTrue(compose.onAllNodes(hasText("Paragraph", substring = true)).fetchSemanticsNodes().size < 100)
            compose.onNode(hasScrollAction()).performScrollToIndex(2_000)
            compose.waitForIdle()
            assertTrue(compose.onAllNodes(hasText("Paragraph", substring = true)).fetchSemanticsNodes().size < 100)
        } finally { store.deleteAll(id) }
    }
}
