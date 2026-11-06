package com.example.notes

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

class MarkdownListEditingTest {
    private fun enter(source: String): String {
        val previous = TextFieldValue(source, TextRange(source.length))
        return MarkdownListEditing.adapt(previous, TextFieldValue(source + "\n", TextRange(source.length + 1))).text
    }
    @Test fun continuesBulletsNumbersAlphabeticAndNestedLists() {
        for ((before, after) in listOf("- item" to "- item\n- ", "+ item" to "+ item\n+ ", "* item" to "* item\n* ",
            "9. item" to "9. item\n10. ", "3) item" to "3) item\n4) ", "a. item" to "a. item\nb. ",
            "z. item" to "z. item\naa. ", "Z) item" to "Z) item\nAA) ", "  - item" to "  - item\n  - ",
            "> - item" to "> - item\n> - ")) assertEquals(before, after, enter(before))
    }
    @Test fun newChecklistItemsAreUnchecked() {
        assertEquals("- [x] done\n- [ ] ", enter("- [x] done"))
        assertEquals("* [ ] open\n* [ ] ", enter("* [ ] open"))
    }
    @Test fun exitsEmptyItemsAndOutdentsNestedOnes() {
        assertEquals("", enter("- ")); assertEquals("", enter("3. ")); assertEquals("", enter("- [ ] "))
        assertEquals("- ", enter("  - ")); assertEquals("> ", enter("> - "))
    }
    @Test fun backspaceRemovesPrefixWithoutRecreatingIt() {
        val old = TextFieldValue("- [ ] task", TextRange(6))
        val deleted = TextFieldValue("- [ ]task", TextRange(5))
        assertEquals("task", MarkdownListEditing.adapt(old, deleted).text)
        assertEquals("hello", MarkdownListEditing.adapt(TextFieldValue("hello!", TextRange(6)), TextFieldValue("hello", TextRange(5))).text)
    }
    @Test fun ignoresFencesCompositionAndMultilinePastes() {
        assertEquals("```\n- code\n", enter("```\n- code"))
        assertEquals("~~~\n1. code\n", enter("~~~\n1. code"))
        assertEquals("```\n- code\n```\n- item\n- ", enter("```\n- code\n```\n- item"))
        val before = TextFieldValue("- item", TextRange(6), TextRange(2,6))
        val after = TextFieldValue("- item\n", TextRange(7))
        assertEquals(after, MarkdownListEditing.adapt(before, after))
        val paste = TextFieldValue("- item\nother\ntext", TextRange(17))
        assertEquals(paste, MarkdownListEditing.adapt(TextFieldValue("- item", TextRange(6)), paste))
    }
    @Test fun prefixCacheInvalidationAndReadsStayBounded() {
        val index = MarkdownFenceIndex()
        var text = "plain\n".repeat(2000) + "```\n- code"
        fun inside() = index.inside(text.length, { start, count -> assertTrue(count <= 8192); text.substring(start, start + count) })
        assertTrue(inside()); text = text.replace("```", "abc"); index.invalidate(12_000); assertFalse(inside())
        text = "```\n" + text; index.invalidate(0); assertTrue(inside())
    }
    @Test fun sourceTransformationNeverStylesOrChangesCharacters() {
        val source = androidx.compose.ui.text.AnnotatedString("**bold** `code` \\**literal\\** ```")
        val transformed = com.example.ui.screens.MarkdownVisualTransformation().filter(source)
        assertEquals(source, transformed.text)
        for (offset in 0..source.length) assertEquals(offset, transformed.offsetMapping.originalToTransformed(offset))
    }
}
