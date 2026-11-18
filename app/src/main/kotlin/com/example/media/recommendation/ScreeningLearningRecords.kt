package com.example.media.recommendation

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

data class ScreeningHistoryChange(val id: Long, val key: String, val title: String?, val action: String,
    val value: String?, val previous: String?, val origin: String, val at: Long?)

/** Canonical active state is durable; only the audit trail and decision history are bounded. */
internal class ScreeningLearningRecords(private val db: SQLiteDatabase) {
    fun resolve(key: String): String = db.rawQuery("SELECT k FROM taste_aliases WHERE alias = ?", arrayOf(key)).use {
        if (it.moveToFirst()) it.getString(0) else key
    }
    fun aliases(): Map<String, String> = buildMap {
        db.rawQuery("SELECT alias, k FROM taste_aliases", null).use { while (it.moveToNext()) put(it.getString(0), it.getString(1)) }
    }
    fun reaction(key: String): ScreeningReaction? = db.rawQuery(
        "SELECT k, value, at, origin, revision FROM taste_reactions WHERE k = ?", arrayOf(resolve(key))).use {
        if (!it.moveToFirst()) null else ScreeningReaction(it.getString(0),
            if (it.isNull(1)) null else ScreeningPreference.valueOf(it.getString(1)),
            if (it.isNull(2)) null else it.getLong(2), it.getString(3), it.getLong(4))
    }
    private fun putReaction(value: ScreeningReaction) {
        db.insertWithOnConflict("taste_reactions", null, ContentValues().apply {
            put("k", value.key); put("value", value.value?.name); put("at", value.at)
            put("origin", value.origin); put("revision", value.revision)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun imported(id: String): Boolean = db.rawQuery("SELECT 1 FROM taste_imports WHERE id = ?", arrayOf(id)).use { it.moveToFirst() }
    fun importLegacy(values: Map<String, *>, now: Long) {
        val records = values.filterKeys { it.startsWith("thumb_") }.mapNotNull { (id, raw) ->
            if (imported(id) || raw !is String) null else runCatching { id to JSONObject(raw) }.getOrNull()
        }.sortedWith(compareBy<Pair<String, JSONObject>> { it.second.optLong("at") }.thenBy { it.first })
        for ((id, json) in records) {
            val rawKey = json.optString("key").takeIf { it.isNotBlank() } ?: continue
            val key = resolve(rawKey)
            val at = json.optLong("at").takeIf { it in 1..now }
            val value = json.optString("value").takeIf { it.isNotBlank() && it != "null" }?.let {
                runCatching { ScreeningPreference.valueOf(it) }.getOrNull()
            }
            val prior = reaction(key)
            if (prior == null || (prior.origin == "Widget · imported" && (at ?: 0) >= (prior.at ?: 0))) {
                putReaction(ScreeningReaction(key, value, at, "Widget · imported", prior?.revision ?: 0))
            }
            db.insertWithOnConflict("taste_imports", null, ContentValues().apply { put("id", id) }, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }
    fun legacyProjection(values: Map<String, *>, now: Long): List<ScreeningReaction> = values.mapNotNull { (id, raw) ->
        if (!id.startsWith("thumb_") || imported(id) || raw !is String) null else runCatching {
            val json = JSONObject(raw)
            val key = resolve(json.getString("key"))
            val value = json.optString("value").takeIf { it.isNotBlank() && it != "null" }?.let { ScreeningPreference.valueOf(it) }
            ScreeningReaction(key, value, json.optLong("at").takeIf { it in 1..now }, "Widget · imported")
        }.getOrNull()
    }
    fun recordTitle(title: ScreeningCanonicalTitle, oldKeys: List<String>, now: Long) {
        val ratings = oldKeys.mapNotNull { key -> db.rawQuery("SELECT rating, rating_at FROM taste_titles WHERE k = ?", arrayOf(key)).use {
            if (it.moveToFirst()) ScreeningRatingObservation(if (it.isNull(0)) null else it.getInt(0), if (it.isNull(1)) null else it.getLong(1)) else null
        } }
        val rating = title.ratingItem?.userRating
        val matching = ratings.filter { it.value == rating }
        val ratingAt = if (matching.isNotEmpty()) matching.mapNotNull { it.at }.maxOrNull()
            else if (ratings.isNotEmpty()) now else null
        val reactions = oldKeys.mapNotNull(::reaction)
        val latest = reactions.maxWithOrNull(compareBy<ScreeningReaction> { it.at ?: 0L }.thenBy { it.revision }.thenBy { it.key })
        for (old in oldKeys.filter { it != title.key }) {
            db.execSQL("UPDATE taste_aliases SET k = ? WHERE k = ?", arrayOf(title.key, old))
            db.execSQL("UPDATE taste_history SET k = ? WHERE k = ?", arrayOf(title.key, old))
            for (prefix in listOf("rating:", "reaction:")) {
                db.execSQL("INSERT OR IGNORE INTO taste_ignored(id) SELECT ? FROM taste_ignored WHERE id = ?",
                    arrayOf(prefix + title.key, prefix + old))
                db.delete("taste_ignored", "id = ?", arrayOf(prefix + old))
            }
            db.delete("taste_titles", "k = ?", arrayOf(old))
            db.delete("taste_reactions", "k = ?", arrayOf(old))
        }
        if (latest != null) putReaction(latest.copy(key = title.key))
        for (alias in (title.aliases + oldKeys).distinct()) db.insertWithOnConflict("taste_aliases", null,
            ContentValues().apply { put("alias", alias); put("k", title.key) }, SQLiteDatabase.CONFLICT_REPLACE)
        db.insertWithOnConflict("taste_titles", null, ContentValues().apply {
            put("k", title.key); put("item_id", title.item.id); put("title", title.item.title.take(180))
            put("rating", rating); put("rating_at", ratingAt)
        }, SQLiteDatabase.CONFLICT_REPLACE)
        if (ratings.isNotEmpty() && matching.isEmpty()) history(title.key, "RATING_OBSERVED", rating?.toString(),
            ratings.first().value?.toString(), "Saved rating · change observed", now)
    }
    fun toggle(key: String, choice: ScreeningPreference, origin: String, now: Long): ScreeningReaction {
        val canonical = resolve(key)
        val prior = reaction(canonical)
        val next = ScreeningReaction(canonical, if (prior?.value == choice) null else choice, now, origin, (prior?.revision ?: 0) + 1)
        putReaction(next)
        history(canonical, next.value?.name ?: "CLEAR", next.value?.name, prior?.value?.name, origin, now)
        return next
    }
    fun clearReaction(key: String, now: Long) {
        val prior = reaction(key) ?: return
        putReaction(prior.copy(value = null, at = now, revision = prior.revision + 1, origin = "Your taste"))
        history(prior.key, "CLEAR", null, prior.value?.name, "Your taste", now)
    }
    fun ignore(id: String, ignored: Boolean, now: Long) {
        if ((id in ignored()) == ignored) return
        if (ignored) db.insertWithOnConflict("taste_ignored", null, ContentValues().apply { put("id", id) }, SQLiteDatabase.CONFLICT_IGNORE)
        else db.delete("taste_ignored", "id = ?", arrayOf(id))
        val key = if (id.startsWith("rating:")) resolve(id.removePrefix("rating:")) else
            db.rawQuery("SELECT k FROM feedback WHERE rowid = ?", arrayOf(id.removePrefix("feedback:"))).use {
                if (it.moveToFirst()) it.getString(0) else "profile"
            }
        history(key, if (ignored) "IGNORE" else "RESTORE", id, null, "Your taste", now)
    }
    fun ignored(): Set<String> = buildSet {
        db.rawQuery("SELECT id FROM taste_ignored", null).use { while (it.moveToNext()) add(it.getString(0)) }
    }
    fun augment(signals: ScreeningSignals, legacy: Map<String, *>, now: Long): ScreeningSignals {
        val reactions = mutableMapOf<String, ScreeningReaction>()
        db.rawQuery("SELECT k FROM taste_reactions", null).use { while (it.moveToNext()) reaction(it.getString(0))?.let { r -> reactions[r.key] = r } }
        for (value in legacyProjection(legacy, now).groupBy { it.key }.values.map { values -> values.maxBy { it.at ?: 0L } })
            if (value.key !in reactions) reactions[value.key] = value
        val ratings = mutableMapOf<String, ScreeningRatingObservation>()
        db.rawQuery("SELECT k, rating, rating_at FROM taste_titles", null).use { while (it.moveToNext()) {
            ratings[it.getString(0)] = ScreeningRatingObservation(if (it.isNull(1)) null else it.getInt(1), if (it.isNull(2)) null else it.getLong(2))
        } }
        return signals.copy(reactions = reactions, ignoredEvidence = ignored(), ratings = ratings, aliases = aliases())
    }
    fun history(key: String, action: String, value: String?, previous: String?, origin: String, now: Long?) {
        val title = db.rawQuery("SELECT title FROM taste_titles WHERE k = ?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else null }
        db.insertOrThrow("taste_history", null, ContentValues().apply {
            put("k", key); put("title", title); put("action", action); put("value", value)
            put("previous", previous); put("origin", origin); put("at", now)
        })
    }
    fun history(): List<ScreeningHistoryChange> = buildList {
        db.rawQuery("SELECT id, k, title, action, value, previous, origin, at FROM taste_history ORDER BY id DESC LIMIT 200", null).use {
            while (it.moveToNext()) add(ScreeningHistoryChange(it.getLong(0), it.getString(1), if (it.isNull(2)) null else it.getString(2),
                it.getString(3), if (it.isNull(4)) null else it.getString(4), if (it.isNull(5)) null else it.getString(5),
                it.getString(6), if (it.isNull(7)) null else it.getLong(7)))
        }
    }
    fun publish(value: ScreeningDecisionSnapshot): Boolean {
        val previous = db.rawQuery("SELECT id FROM taste_surface_heads WHERE surface = ?", arrayOf(value.surface)).use {
            if (it.moveToFirst()) it.getString(0) else null
        }
        if (previous == value.publicationId) return false
        db.insertWithOnConflict("taste_publications", null, ContentValues().apply {
            put("id", value.publicationId); put("surface", value.surface); put("source", value.source); put("k", value.key)
            put("item_id", value.itemId); put("title", value.title.take(180)); put("at", value.at); put("mode", value.mode)
            put("data", value.trace?.let(ScreeningDecisionCodec::encode)?.toString()); put("reason", value.reason?.take(240))
        }, SQLiteDatabase.CONFLICT_IGNORE)
        db.insertWithOnConflict("taste_surface_heads", null, ContentValues().apply {
            put("surface", value.surface); put("id", value.publicationId)
        }, SQLiteDatabase.CONFLICT_REPLACE)
        return true
    }
    fun publications(): List<ScreeningDecisionSnapshot> = buildList {
        db.rawQuery("SELECT id, surface, source, k, item_id, title, at, data, mode, reason FROM taste_publications ORDER BY at DESC LIMIT 200", null).use {
            while (it.moveToNext()) add(ScreeningDecisionSnapshot(it.getString(0), it.getString(1), it.getString(2), it.getString(3),
                it.getString(4), it.getString(5), it.getLong(6), if (it.isNull(7)) null else
                    runCatching { ScreeningDecisionCodec.decode(JSONObject(it.getString(7))) }.getOrNull(), it.getString(8), if (it.isNull(9)) null else it.getString(9)))
        }
    }
    fun reset() {
        for (table in listOf("taste_reactions", "taste_ignored", "taste_history", "taste_publications", "taste_surface_heads")) db.delete(table, null, null)
        history("profile", "RESET", null, null, "Your taste", System.currentTimeMillis())
    }
    fun prune() {
        db.execSQL("DELETE FROM taste_history WHERE id NOT IN (SELECT id FROM taste_history ORDER BY id DESC LIMIT 500)")
        db.execSQL("DELETE FROM taste_publications WHERE id NOT IN (SELECT id FROM taste_publications ORDER BY at DESC LIMIT 200)")
    }
    companion object {
        fun install(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS taste_aliases (alias TEXT PRIMARY KEY, k TEXT NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS taste_alias_owner ON taste_aliases(k)")
            db.execSQL("CREATE TABLE IF NOT EXISTS taste_titles (k TEXT PRIMARY KEY, item_id TEXT NOT NULL, title TEXT NOT NULL, rating INTEGER, rating_at INTEGER)")
            db.execSQL("CREATE TABLE IF NOT EXISTS taste_reactions (k TEXT PRIMARY KEY, value TEXT, at INTEGER, origin TEXT NOT NULL, revision INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS taste_imports (id TEXT PRIMARY KEY)")
            db.execSQL("CREATE TABLE IF NOT EXISTS taste_ignored (id TEXT PRIMARY KEY)")
            db.execSQL("CREATE TABLE IF NOT EXISTS taste_history (id INTEGER PRIMARY KEY AUTOINCREMENT, k TEXT NOT NULL, title TEXT, action TEXT NOT NULL, value TEXT, previous TEXT, origin TEXT NOT NULL, at INTEGER)")
            db.execSQL("CREATE TABLE IF NOT EXISTS taste_publications (id TEXT PRIMARY KEY, surface TEXT NOT NULL, source TEXT NOT NULL, k TEXT NOT NULL, item_id TEXT NOT NULL, title TEXT NOT NULL, at INTEGER NOT NULL, data TEXT, mode TEXT NOT NULL, reason TEXT)")
            db.execSQL("CREATE TABLE IF NOT EXISTS taste_surface_heads (surface TEXT PRIMARY KEY, id TEXT NOT NULL)")
            fun column(table: String, name: String, type: String) {
                val exists = db.rawQuery("PRAGMA table_info($table)", null).use { c ->
                    var found = false; while (c.moveToNext()) if (c.getString(c.getColumnIndexOrThrow("name")) == name) found = true; found
                }
                if (!exists) db.execSQL("ALTER TABLE $table ADD COLUMN $name $type")
            }
            for (name in listOf("origin", "title", "item_id")) column("feedback", name, "TEXT")
            column("deliveries", "trace", "TEXT"); column("deliveries", "published_at", "INTEGER")
            column("taste_publications", "reason", "TEXT")
        }
    }
}
