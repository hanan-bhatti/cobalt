package com.example.notes

/** Metadata only: never contains a note body or an unbounded title. */
data class RecoveryNote(val id: String, val title: String, val byteLength: Long)
