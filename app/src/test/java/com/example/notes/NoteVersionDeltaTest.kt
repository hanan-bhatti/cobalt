package com.example.notes

import java.io.*
import java.security.MessageDigest
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class NoteVersionDeltaTest {
    @Test fun fiftyMegabyteVersionReconstructsByStreamingBoundedRanges() {
        val length = 50_000_000L
        fun value(offset: Long): Byte = ((offset * 1103515245L + 12345L) ushr 16).toByte()
        fun source(changed: Boolean): InputStream = object : InputStream() {
            var position = 0L
            override fun read(): Int {
                if (position >= length) return -1
                val offset = position++
                return (if (changed && offset == 25_000_000L) (value(offset).toInt() xor 1).toByte() else value(offset)).toInt() and 255
            }
            override fun read(bytes: ByteArray, off: Int, len: Int): Int {
                if (len == 0) return 0
                if (position == length) return -1
                val count = minOf(len.toLong(), length - position).toInt()
                repeat(count) { bytes[off + it] = read().toByte() }
                return count
            }
        }
        fun digest(input: InputStream): String = input.use {
            val md = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(16384)
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                md.update(buffer, 0, count)
            }
            md.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 255) }
        }
        val baseHash = digest(source(false))
        val targetHash = digest(source(true))
        val encoded = ByteArrayOutputStream()
        source(false).use { base -> source(true).use { target ->
            NoteVersionDelta.write(base, target, NoteVersionDelta.Header(baseHash, targetHash, length, length), encoded)
        } }
        val restored = NoteVersionDelta.open(encoded.toByteArray().inputStream(), targetHash) {
            object : NoteRangeReader {
                override fun read(offset: Long, count: Int): ByteArray {
                    assertTrue("Unbounded source read", count <= 16384)
                    return ByteArray(count) { value(offset + it) }
                }
                override fun close() = Unit
            }
        }
        assertEquals(targetHash, digest(restored))
        assertTrue(encoded.size() < 1_000_000)
        println("HOST_DELTA_FIXTURE: 50000000-byte base and target, one changed byte, delta bytes=${encoded.size()}")
    }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun encode(base: ByteArray, target: ByteArray): ByteArray = ByteArrayOutputStream().also {
        NoteVersionDelta.write(base.inputStream(), target.inputStream(),
            NoteVersionDelta.Header(hash(base), hash(target), base.size.toLong(), target.size.toLong()), it)
    }.toByteArray()
    private fun open(delta: ByteArray, base: ByteArray, targetHash: String) =
        NoteVersionDelta.open(delta.inputStream(), targetHash) { header ->
            check(hash(base) == header.baseHash) { "Anchor hash mismatch" }
            object : NoteRangeReader {
                override fun read(offset: Long, count: Int) = base.copyOfRange(offset.toInt(), minOf(base.size, offset.toInt() + count))
                override fun close() = Unit
            }
        }
    @Test fun smallScatteredEditsAndShiftedContentReuseRandomBaseChunks() {
        val base = Random(7).nextBytes(2_000_000)
        val target = base.copyOf().also { it[50000] = (it[50000].toInt() xor 17).toByte(); it[1_800_000] = 3 }
        val shifted = "Inserted😀".toByteArray() + target
        val delta = encode(base, shifted)
        assertTrue("Small edits should not retain another full random body: ${delta.size}", delta.size < 200_000)
        assertArrayEquals(shifted, open(delta, base, hash(shifted)).use { it.readBytes() })
    }
    @Test fun deterministicRandomEditsRestoreEveryByte() {
        val random = Random(91)
        val base = random.nextBytes(100_000)
        repeat(30) {
            val from = random.nextInt(base.size)
            val until = random.nextInt(from, base.size + 1)
            val target = base.copyOfRange(0, from) + random.nextBytes(random.nextInt(5000)) + base.copyOfRange(until, base.size)
            assertArrayEquals(target, open(encode(base, target), base, hash(target)).use { it.readBytes() })
        }
    }
    @Test fun emptyBodiesAndUnicodeRoundTrip() {
        for ((base, target) in listOf(ByteArray(0) to "😀\r\n# Title\n".toByteArray(), "remove all".toByteArray() to ByteArray(0))) {
            assertArrayEquals(target, open(encode(base, target), base, hash(target)).use { it.readBytes() })
        }
    }
    @Test fun corruptTruncatedAndWrongBaseVersionsAreRejected() {
        val base = Random(3).nextBytes(20000)
        val target = base + "end".toByteArray()
        val delta = encode(base, target)
        for (bad in listOf(delta.copyOf(delta.size - 4), delta.copyOf().also { it[it.lastIndex - 8] = 99 })) {
            try { open(bad, base, hash(target)).use { it.readBytes() }; fail("Corrupt history was accepted") }
            catch (_: Exception) { }
        }
        val changed = base.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        try { open(delta, changed, hash(target)).use { it.readBytes() }; fail("Wrong anchor was accepted") }
        catch (_: IllegalStateException) { /* The provider rejects the mismatched anchor identity. */ }
    }
}
