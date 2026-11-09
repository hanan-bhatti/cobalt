package com.example.notes

/** Bounded metadata must never contain half a UTF-16 scalar when bound through SQLite/JNI. */
internal object NoteTextMetadata {
    fun preview(text: String, limit: Int = NoteSizePolicy.PREVIEW_CHARACTERS): String = buildString {
        var i = 0
        while (i < text.length && length < limit) {
            val char = text[i++]
            if (Character.isHighSurrogate(char)) {
                if (i < text.length && Character.isLowSurrogate(text[i])) {
                    if (length + 2 > limit) break
                    append(char); append(text[i++])
                } else append('\uFFFD')
            } else if (Character.isLowSurrogate(char)) append('\uFFFD')
            else append(char)
        }
    }
}
