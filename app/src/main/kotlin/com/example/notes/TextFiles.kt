package com.example.notes

import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.util.Locale

/** Text is imported as inert content; Markdown/MDX/HTML never execute scripts. */
object TextFiles {
    private val extensions = setOf("md", "mdx", "markdown", "mdown", "mkd", "mkdn", "txt", "text", "rst", "org",
        "adoc", "asciidoc", "log", "csv", "tsv", "json", "jsonl", "ndjson", "yaml", "yml", "toml", "ini", "cfg",
        "conf", "properties", "xml", "html", "htm", "css", "scss", "js", "jsx", "ts", "tsx", "kt", "kts", "java",
        "py", "rb", "rs", "go", "c", "h", "cpp", "hpp", "sh", "bash", "zsh", "sql", "tex", "srt", "vtt", "diff", "patch")
    private val applicationTypes = setOf("application/json", "application/ld+json", "application/x-ndjson", "application/xml",
        "application/xhtml+xml", "application/yaml", "application/x-yaml", "application/toml", "application/javascript", "application/sql", "application/mdx")
    private val binaryExtensions = setOf("pdf", "zip", "gz", "7z", "rar", "apk", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "png", "jpg", "jpeg", "gif", "webp", "heic", "mp4", "mp3", "wav", "ogg", "woff", "ttf", "exe", "bin")
    fun accepts(name: String?, mimeType: String?): Boolean {
        val extension = name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty()
        if (extension in binaryExtensions) return false
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return extension in extensions || mime.startsWith("text/") || mime in applicationTypes
    }
    fun title(name: String): String = name.substringBeforeLast('.', name).ifBlank { name }.take(160)

    /** Decode UTF-8 or BOM-labelled UTF-16 strictly, then stream normalized UTF-8. */
    fun openUtf8(source: InputStream): InputStream {
        val input = source.buffered(NoteSizePolicy.IO_CHUNK_BYTES)
        input.mark(3)
        val first = input.read(); val second = input.read()
        input.reset()
        val charset = when {
            first == 0xff && second == 0xfe -> Charsets.UTF_16LE
            first == 0xfe && second == 0xff -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        if (charset != Charsets.UTF_8) { input.read(); input.read() }
        val reader = InputStreamReader(input, charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))
        return object : InputStream() {
            var buffer = ByteArray(0)
            var position = 0
            fun fill(): Boolean {
                if (position < buffer.size) return true
                val chars = CharArray(2049)
                var count = reader.read(chars, 0, 2048)
                if (count < 0) return false
                if (count == 0) return fill()
                if (Character.isHighSurrogate(chars[count - 1])) {
                    val next = reader.read()
                    if (next < 0 || !Character.isLowSurrogate(next.toChar())) throw IOException("Malformed text; original file retained")
                    chars[count++] = next.toChar()
                }
                buffer = String(chars, 0, count).toByteArray(Charsets.UTF_8); position = 0
                return true
            }
            override fun read(): Int = if (fill()) buffer[position++].toInt() and 255 else -1
            override fun read(bytes: ByteArray, off: Int, len: Int): Int {
                if (off < 0 || len < 0 || off > bytes.size - len) throw IndexOutOfBoundsException()
                if (len == 0) return 0
                if (!fill()) return -1
                val count = minOf(len, buffer.size - position)
                buffer.copyInto(bytes, off, position, position + count); position += count
                return count
            }
            override fun close() = reader.close()
        }
    }
}
