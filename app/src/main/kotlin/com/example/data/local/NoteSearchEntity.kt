package com.example.data.local

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

/** Derived, bounded search index. The authoritative body is always its Markdown file. */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "note_search")
data class NoteSearchEntity(val noteId: String, val title: String, val bodyPrefix: String)
