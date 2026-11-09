package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Immutable bounded payloads. Bodies and metadata live in the same durable database. */
@Entity(tableName = "note_chunks", indices = [Index(value = ["digest"], unique = true)])
data class NoteChunkEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0,
    val digest: ByteArray, val data: ByteArray)

@Entity(tableName = "note_nodes", indices = [Index(value = ["digest"], unique = true)])
data class NoteNodeEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0,
    val left: Long, val right: Long, val chunk: Long, val offset: Long, val length: Long,
    val bytes: Long, val height: Int, val digest: ByteArray)

/** Full-body SHA remains the public identity; the tree is a separately identified representation. */
@Entity(tableName = "note_roots", primaryKeys = ["owner", "hash"])
data class NoteRootEntity(val owner: String, val hash: String, val root: Long,
    val bytes: Long, val preview: String, val created: Long, val staged: Boolean)

@Entity(tableName = "note_history_state")
data class NoteHistoryStateEntity(@PrimaryKey val noteId: String, val state: String)
