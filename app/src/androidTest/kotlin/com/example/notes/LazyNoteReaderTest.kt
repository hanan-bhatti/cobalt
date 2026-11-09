package com.example.notes

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
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
        val jump = mutableLongStateOf(0)
        val visible = androidx.compose.runtime.mutableStateOf(true)
        fun paragraph(number: Int) = SemanticsMatcher("Contains complete paragraph $number") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { value ->
                value.text.lineSequence().any { it.trim() == "Paragraph $number" }
            } == true
        }
        fun capture(name: String) {
            val folder = File(context.cacheDir, "note-reader-validation").apply { mkdirs() }
            File(folder, "$name.png").outputStream().use { output ->
                compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
            }
        }
        try {
            compose.setContent { MaterialTheme { if (visible.value) LazyMarkdownNote(id, ref.hash, ref.bytes, jump.longValue, Modifier.fillMaxSize()) } }
            compose.waitUntil(15_000) { compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty() }
            // Adaptive blocks can contain several paragraphs in one Text node.
            compose.waitUntil(15_000) { compose.onAllNodes(paragraph(1)).fetchSemanticsNodes().isNotEmpty() }
            assertTrue(compose.onAllNodes(hasText("Paragraph", substring = true)).fetchSemanticsNodes().size < 100)
            capture("first-paragraph")
            // Paragraph numbers are not LazyColumn item indices; use the actual byte navigation.
            compose.runOnIdle { jump.longValue = body.substring(0, body.indexOf("Paragraph 2001")).toByteArray().size.toLong() }
            compose.waitUntil(15_000) { compose.onAllNodes(paragraph(2001)).fetchSemanticsNodes().isNotEmpty() }
            assertTrue(compose.onAllNodes(hasText("Paragraph", substring = true)).fetchSemanticsNodes().size < 100)
            capture("distant-paragraph")
        } catch (error: Throwable) {
            File(context.cacheDir, "note-reader-failure-semantics.txt").writeText(compose.onRoot().printToString())
            throw error
        } finally {
            compose.runOnIdle { visible.value = false }
            compose.waitUntil(5_000) { !NoteBodyLeases.activeDirectory(PrivateMarkdownStore.storageKey(id)) }
            store.deleteAll(id)
        }
    }
}
