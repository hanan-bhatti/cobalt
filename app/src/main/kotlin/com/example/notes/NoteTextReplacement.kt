package com.example.notes

/** Plans an insertion without building another whole-document String or UTF-8 array. */
class NoteTextReplacement(source: String, start: Int, end: Int, replacement: String, checkCancelled: () -> Unit = {}) {
    private val prefix: CharSequence
    private val inserted: CharSequence = replacement
    private val suffix: CharSequence
    val byteLength: Long

    init {
        require(start in 0..end && end <= source.length)
        fun boundary(offset: Int) = offset == 0 || offset == source.length ||
            !(Character.isHighSurrogate(source[offset - 1]) && Character.isLowSurrogate(source[offset]))
        require(boundary(start) && boundary(end)) { "Edit splits a Unicode character; original retained" }
        prefix = NoteTextSlice(source, 0, start)
        suffix = NoteTextSlice(source, end, source.length)
        byteLength = NoteEditorWindows.utf8Bytes(prefix, checkCancelled) +
            NoteEditorWindows.utf8Bytes(inserted, checkCancelled) + NoteEditorWindows.utf8Bytes(suffix, checkCancelled)
    }

    fun openStream(): java.io.InputStream = java.io.SequenceInputStream(java.util.Collections.enumeration(
        listOf(TextUtf8InputStream(prefix), TextUtf8InputStream(inserted), TextUtf8InputStream(suffix))))
}
