package com.example.notes

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Robolectric cannot open directory descriptors or execute Android Os.rename here.
 * Host tests use a real atomic move, but do NOT claim to test directory fsync.
 * Instrumented MarkdownStorageTest uses the unmodified Android syscall default.
 */
internal object HostTestNoteFileOperations : NoteFileOperations {
    override fun syncDirectory(directory: File) { check(directory.isDirectory) { "Storage directory unavailable" } }
    override fun renameAtomic(source: File, destination: File) {
        Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }
}
