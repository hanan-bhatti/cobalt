package com.example.notes

import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class Utf8NotePartsTest {
    @Test fun allSmallBoundariesPreserveUnicodeAndByteExactContent() {
        val original = ("a😀漢字\u0000\r\n".repeat(150)).toByteArray(Charsets.UTF_8)
        for (limit in 4L..40L) {
            val parts = Utf8NoteParts(original.inputStream(), limit)
            val combined = ByteArrayOutputStream()
            while (true) {
                val part = parts.next() ?: break
                val bytes = part.use { it.readBytes() }
                assertTrue(bytes.isNotEmpty())
                assertTrue(bytes.size <= limit)
                assertArrayEquals(bytes, bytes.toString(Charsets.UTF_8).toByteArray(Charsets.UTF_8))
                combined.write(bytes)
            }
            assertArrayEquals(original, combined.toByteArray())
        }
    }

    @Test fun emptyAndMalformedInputsAreNeverTruncated() {
        for (original in listOf(byteArrayOf(), byteArrayOf(0x61, 0xF0.toByte(), 0x9F.toByte()))) {
            val parts = Utf8NoteParts(original.inputStream(), 4)
            val output = ByteArrayOutputStream()
            while (true) { val part = parts.next() ?: break; output.write(part.readBytes()) }
            assertArrayEquals(original, output.toByteArray())
        }
    }
}
