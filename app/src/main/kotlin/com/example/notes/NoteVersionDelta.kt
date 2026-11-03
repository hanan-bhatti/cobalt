package com.example.notes

import java.io.*
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Versioned, bounded-memory binary copy/insert history. All calls belong on an IO worker. */
internal object NoteVersionDelta {
    private const val MAGIC = 0x43424431
    private const val VERSION = 1
    private const val MIN_CHUNK = 2048
    private const val MAX_CHUNK = 32768
    private const val COPY = 1
    private const val INSERT = 2
    private const val END = 0
    // A bounded anchor index; larger legacy files remain complete Markdown copies.
    private const val MAX_INDEX_ENTRIES = 65536
    private val gear = LongArray(256) { index ->
        var value = index.toLong() + 0x12345678L
        repeat(8) { value = (value xor (value ushr 13)) * 0x5deece66dL + 11 }
        value
    }
    data class Header(val baseHash: String, val targetHash: String, val baseBytes: Long, val targetBytes: Long)
    private data class Range(val offset: Long, val length: Int)

    private fun hash(bytes: ByteArray, size: Int, digest: MessageDigest): String {
        digest.update(bytes, 0, size)
        val sum = digest.digest()
        val digits = "0123456789abcdef"
        return buildString(64) { for (byte in sum) {
            val value = byte.toInt() and 255
            append(digits[value ushr 4]); append(digits[value and 15])
        } }
    }

    private inline fun chunks(input: InputStream, checkCancelled: () -> Unit, consume: (ByteArray, Int, Long) -> Unit) {
        val buffer = ByteArray(MAX_CHUNK)
        val source = BufferedInputStream(input, NoteSizePolicy.IO_CHUNK_BYTES)
        var offset = 0L
        while (true) {
            checkCancelled()
            var count = 0
            var rolling = 0L
            while (count < buffer.size) {
                val value = source.read()
                if (value < 0) break
                buffer[count++] = value.toByte()
                rolling = (rolling shl 1) + gear[value]
                if (count >= MIN_CHUNK && rolling and 8191L == 0L) break
            }
            if (count == 0) return
            consume(buffer, count, offset)
            offset = Math.addExact(offset, count.toLong())
        }
    }

    fun write(base: InputStream, target: InputStream, header: Header, output: OutputStream,
        checkCancelled: () -> Unit = {}) {
        validate(header)
        val digest = MessageDigest.getInstance("SHA-256")
        val index = HashMap<String, Range>()
        var baseLength = 0L
        chunks(base, checkCancelled) { bytes, count, offset ->
            check(index.size < MAX_INDEX_ENTRIES) { "History anchor index limit reached; full version retained" }
            index.putIfAbsent(hash(bytes, count, digest), Range(offset, count))
            baseLength = offset + count
        }
        check(baseLength == header.baseBytes) { "History anchor changed" }
        val raw = DataOutputStream(output)
        raw.writeInt(MAGIC); raw.writeInt(VERSION)
        raw.writeUTF(header.baseHash); raw.writeUTF(header.targetHash)
        raw.writeLong(header.baseBytes); raw.writeLong(header.targetBytes)
        val gzip = GZIPOutputStream(object : FilterOutputStream(raw) {
            override fun close() { flush() } // Caller owns the descriptor and its final fsync.
        }, NoteSizePolicy.IO_CHUNK_BYTES)
        try {
            val encoded = DataOutputStream(gzip)
            var targetLength = 0L
            chunks(target, checkCancelled) { bytes, count, offset ->
                val match = index[hash(bytes, count, digest)]?.takeIf { it.length == count }
                if (match != null) {
                    encoded.writeByte(COPY); encoded.writeLong(match.offset); encoded.writeInt(count)
                } else {
                    encoded.writeByte(INSERT); encoded.writeInt(count); encoded.write(bytes, 0, count)
                }
                targetLength = offset + count
            }
            check(targetLength == header.targetBytes) { "History target changed" }
            encoded.writeByte(END); encoded.flush(); gzip.finish(); raw.flush()
        } finally { gzip.close() }
    }

    private fun validate(header: Header) {
        require(header.baseHash.matches(Regex("[0-9a-f]{64}")) && header.targetHash.matches(Regex("[0-9a-f]{64}")))
        require(header.baseHash != header.targetHash && header.baseBytes >= 0 && header.targetBytes >= 0)
    }
    private fun header(input: DataInputStream): Header {
        check(input.readInt() == MAGIC && input.readInt() == VERSION) { "Unknown history format; retained" }
        return Header(input.readUTF(), input.readUTF(), input.readLong(), input.readLong()).also(::validate)
    }
    fun inspect(input: InputStream): Header = header(DataInputStream(input))

    /** Owns both inputs. Verifies the reconstructed hash at EOF, including empty documents. */
    fun open(input: InputStream, expectedHash: String, base: (Header) -> NoteRangeReader): InputStream {
        var reader: NoteRangeReader? = null
        try {
            val raw = DataInputStream(input)
            val info = header(raw)
            check(info.targetHash == expectedHash) { "History reference mismatch" }
            val source = base(info).also { reader = it }
            val encoded = DataInputStream(GZIPInputStream(raw, NoteSizePolicy.IO_CHUNK_BYTES))
            return object : InputStream() {
                val digest = MessageDigest.getInstance("SHA-256")
                var outputBytes = 0L
                var remaining = 0
                var copyOffset = 0L
                var copy = false
                var finished = false
                var closed = false
                override fun read(): Int {
                    val one = ByteArray(1)
                    return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 255
                }
                override fun read(bytes: ByteArray, off: Int, len: Int): Int {
                    if (off < 0 || len < 0 || off > bytes.size - len) throw IndexOutOfBoundsException()
                    check(!closed)
                    if (len == 0) return 0
                    if (finished) return -1
                    if (remaining == 0) {
                        when (encoded.readUnsignedByte()) {
                            END -> {
                                check(outputBytes == info.targetBytes && encoded.read() == -1) { "History length mismatch" }
                                val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
                                check(actual == expectedHash) { "History hash mismatch; retained" }
                                finished = true
                                return -1
                            }
                            COPY -> {
                                copy = true; copyOffset = encoded.readLong(); remaining = encoded.readInt()
                                check(copyOffset >= 0 && copyOffset <= info.baseBytes && remaining.toLong() <= info.baseBytes - copyOffset)
                            }
                            INSERT -> { copy = false; remaining = encoded.readInt() }
                            else -> error("Invalid history operation")
                        }
                        check(remaining in 1..MAX_CHUNK && remaining.toLong() <= info.targetBytes - outputBytes)
                    }
                    val count = minOf(len, remaining, NoteSizePolicy.IO_CHUNK_BYTES)
                    if (copy) {
                        val part = source.read(copyOffset, count)
                        check(part.size == count) { "History anchor is incomplete" }
                        part.copyInto(bytes, off); copyOffset += count
                    } else encoded.readFully(bytes, off, count)
                    remaining -= count; outputBytes += count; digest.update(bytes, off, count)
                    return count
                }
                override fun close() {
                    if (closed) return
                    closed = true
                    try { encoded.close() } finally { source.close() }
                }
            }
        } catch (error: Throwable) {
            try { input.close() } finally { reader?.close() }
            throw error
        }
    }
}
