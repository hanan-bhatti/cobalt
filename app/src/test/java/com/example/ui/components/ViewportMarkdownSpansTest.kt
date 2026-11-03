package com.example.ui.components

import io.github.rosemoe.sora.text.Content
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ViewportMarkdownSpansTest {
    @Test fun deletionBeforeTheReadLockCannotInvalidateVisibleBounds() {
        val content = object : Content("# Heading\n".repeat(300)) {
            var removeBeforeLock = true
            override fun runReadActionsOnLines(startLine: Int, endLine: Int, consumer: ContentLineConsumer2) {
                if (removeBeforeLock) {
                    removeBeforeLock = false
                    delete(0, length)
                }
                super.runReadActionsOnLines(startLine, endLine, consumer)
            }
        }
        try {
            val spans = ViewportMarkdownSpans(content)
            spans.prepare(100, 200)
            assertEquals(0, content.length)
            assertEquals(1, spans.lineCount)
            assertTrue(spans.read().getSpansOnLine(0).isNotEmpty())
        } finally { content.release() }
    }
    @Test fun cancelledStylingAfterDocumentReleaseIsHarmless() {
        val content = Content("**before close**")
        val spans = ViewportMarkdownSpans(content)
        spans.prepare(0, 0)
        content.release()
        spans.prepare(100, 200)
        assertTrue(spans.read().getSpansOnLine(0).isNotEmpty())
    }
}
