package com.example.notes

import org.junit.Assert.*
import org.junit.Test

class MarkdownTableEditsTest {
    private val table = "| Name | Value |\n| :--- | ---: |\n| Alpha | 42 |\n| Beta | 7 |"
    private fun plan(source: String, cursor: Int, command: String) = MarkdownTableEdits.plan(source.length, cursor, command,
        { start, count -> assertTrue(count <= 8192); source.substring(start, start + count) })
    private fun apply(source: String, cursor: Int, command: String): String {
        val edit = plan(source, cursor, command)
        val result = source.replaceRange(edit.start, edit.end, edit.text)
        assertTrue(edit.caret in 0..result.length)
        return result
    }
    @Test fun insertsDimensionsWithoutRemovingSurroundingText() {
        val text = apply("before\nafter", 7, "table:new:2:3")
        assertTrue(text.startsWith("before\n| Column 1 | Column 2 | Column 3 |"))
        assertTrue(text.endsWith("\n\nafter"))
        assertEquals(4, text.lines().count { it.startsWith('|') })
    }
    @Test fun addsRowBelowActiveRowAndPreservesAlignment() {
        val updated = apply(table, table.indexOf("Alpha"), "table:row")
        assertTrue(updated.contains("| Alpha | 42 |\n|  |  |\n| Beta | 7 |"))
        assertTrue(updated.contains("| :--- | ---: |"))
    }
    @Test fun addsColumnToEveryRow() {
        val updated = apply(table, table.indexOf("Alpha"), "table:column")
        assertTrue(updated.contains("| Name |  | Value |"))
        assertTrue(updated.contains("| :--- | --- | ---: |"))
        assertTrue(updated.contains("| Alpha |  | 42 |"))
    }
    @Test fun alignsOnlyTheActiveColumn() {
        val updated = apply(table, table.indexOf("42"), "table:center")
        assertTrue(updated.contains("| :--- | :---: |"))
        assertTrue(updated.contains("| Alpha | 42 |"))
    }
    @Test fun removesExplicitBodyRowOrColumnWithoutTouchingOtherCells() {
        assertFalse(apply(table, table.indexOf("Beta"), "table:remove-row").contains("Beta"))
        val updated = apply(table, table.indexOf("42"), "table:remove-column")
        assertTrue(updated.contains("| Name |\n| :--- |\n| Alpha |\n| Beta |"))
    }
    @Test fun preservesEscapedPipesAndCodeAndUnicode() {
        val source = "| A | B |\n| --- | --- |\n| `x\\|y` | 😀مرحبا |"
        val updated = apply(source, source.indexOf("😀"), "table:column")
        assertTrue(updated.contains("`x\\|y`")); assertTrue(updated.contains("😀مرحبا"))
    }
    @Test fun retainsCrlfAndProseAroundTable() {
        val source = "Intro\r\n\r\n" + table.replace("\n", "\r\n") + "\r\n\r\nEnd"
        val updated = apply(source, source.indexOf("Alpha"), "table:row")
        assertTrue(updated.startsWith("Intro\r\n\r\n")); assertTrue(updated.endsWith("\r\n\r\nEnd"))
        assertFalse(updated.replace("\r\n", "").contains('\n'))
    }
    @Test fun refusesFencedTablesAndMalformedTablesWithoutMutation() {
        for (source in listOf("```md\n$table\n```", "~~~\n$table\n~~~", "1. ```md\n$table\n   ```")) {
            assertThrows(IllegalArgumentException::class.java) { plan(source, source.indexOf("Alpha"), "table:row") }
        }
        assertThrows(IllegalArgumentException::class.java) { plan("| A |\n| --- |\n| x | extra |", 20, "table:column") }
    }
    @Test fun nextCellAppendsBodyRowAndCursorTargetsIt() {
        val edit = plan(table, table.indexOf("7"), "table:next-cell")
        assertTrue(edit.text.endsWith("\n|  |  |"))
        assertEquals(' ', edit.text[edit.caret - edit.start])
    }
    @Test fun retainsHeaderAndFinalColumnAndRejectsOversizeTable() {
        assertThrows(IllegalArgumentException::class.java) { plan(table, 3, "table:remove-row") }
        val one = "| A |\n| --- |\n| B |"
        assertThrows(IllegalArgumentException::class.java) { plan(one, one.indexOf('B'), "table:remove-column") }
        val huge = "| A |\n| --- |\n" + "| x |\n".repeat(MarkdownTableEdits.MAX_ROWS)
        assertThrows(IllegalArgumentException::class.java) { plan(huge, huge.indexOf('x'), "table:row") }
    }    @Test fun earlierLargeTablesDoNotBlockASeparateSmallTable() {
        val source = "| A |\n| --- |\n" + "| x |\n".repeat(MarkdownTableEdits.MAX_ROWS) + "\n" + table
        val result = apply(source, source.lastIndexOf("Alpha"), "table:row")
        assertTrue(result.contains("| Alpha | 42 |\n|  |  |\n| Beta | 7 |"))
    }
    @Test fun createsTableAtEndOfAClosedFenceWithoutAnExtraEnter() {
        val source = "```kotlin\nval a = 1\n```"
        val result = apply(source, source.length, "table:new:2:2")
        assertTrue(result.startsWith(source + "\n\n| Column 1 | Column 2 |"))
    }
    @Test fun insertingFromAnExistingTableKeepsItAndCreatesAnotherBelow() {
        val result = apply(table, table.indexOf("Alpha"), "table:new:1:2")
        assertTrue(result.startsWith(table + "\n\n| Column 1 | Column 2 |"))
    }
    @Test fun everyHeaderActionReadsTheFollowingSeparatorAndRows() {
        for (command in listOf("table:row", "table:column", "table:left", "table:center", "table:right", "table:remove-column", "table:next-cell")) {
            val edit = plan(table, table.indexOf("Name"), command)
            assertTrue(command, edit.caret >= 0)
        }
    }
    @Test fun nextCellDoesNotReformatOrRewriteExistingText() {
        val source = "|A|B|\n|--|--|\n|x|y|"
        val edit = plan(source, 1, "table:next-cell")
        assertTrue(edit.selectionOnly)
        assertEquals(edit.start, edit.end); assertEquals("", edit.text)
        assertEquals(source.indexOf('B'), edit.caret)
    }

}
