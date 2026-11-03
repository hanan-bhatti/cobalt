package com.example.notes

import org.junit.Assert.*
import org.junit.Test

class ContinuousTextSearchTest {
    @Test fun literalMatchesCrossFormerWindowBoundariesAndIncludeEmoji() {
        val text = "a".repeat(511) + "😀\r\nneedle" + "z".repeat(900) + "needle"
        assertEquals(listOf(515, 1421), ContinuousTextSearch.matches(text, "needle"))
        assertEquals(listOf(511), ContinuousTextSearch.matches(text, "😀\r\n"))
        assertEquals(listOf(1421), ContinuousTextSearch.matches(text, "needle", 516))
    }
    @Test fun searchNeverConvertsTheDocumentIntoAString() {
        val source = "before\nmatch\nafter"
        val guarded = object : CharSequence {
            override val length = source.length
            override fun get(index: Int) = source[index]
            override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = error("No document slicing")
            override fun toString(): String = error("No document String")
        }
        assertEquals(listOf(7), ContinuousTextSearch.matches(guarded, "match"))
    }
    @Test fun replacementMatchesAreNonOverlappingAndCapped() {
        assertEquals(listOf(0, 2), ContinuousTextSearch.matches("aaaaa", "aa"))
        assertEquals(listOf(0, 1, 2), ContinuousTextSearch.matches("a".repeat(1000), "a", limit = 3))
    }
    @Test fun byteRangesMapToWholeUtf16CodePoints() {
        val text = "a😀漢\r\nb"
        assertEquals(1 to 3, ContinuousTextSearch.utf16Range(text, 1, 5))
        assertEquals(3 to 6, ContinuousTextSearch.utf16Range(text, 5, 10))
        assertEquals(7 to 7, ContinuousTextSearch.utf16Range(text, 11, 11))
        assertThrows(IllegalArgumentException::class.java) { ContinuousTextSearch.utf16Range(text, 2, 5) }
    }
    @Test fun cancellationIsCheckedWhileScanningLargeContent() {
        var checks = 0
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            ContinuousTextSearch.matches("a".repeat(100_000), "missing") {
                if (++checks == 3) throw java.util.concurrent.CancellationException()
            }
        }
        assertEquals(3, checks)
    }
    @Test fun generatedCasesAgreeWithReferenceSearch() {
        val random = java.util.Random(41)
        repeat(200) {
            val text = buildString { repeat(1000) { append("ab漢\n"[random.nextInt(4)]) } }
            val query = text.substring(250, 250 + 1 + random.nextInt(5))
            val expected = mutableListOf<Int>()
            var from = 0
            while (true) {
                val found = text.indexOf(query, from)
                if (found < 0) break
                expected.add(found); from = found + query.length
            }
            assertEquals(expected, ContinuousTextSearch.matches(text, query))
        }
    }
}
