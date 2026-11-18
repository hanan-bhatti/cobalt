package com.example.media.recommendation

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.data.model.SavedItem
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Small separate database: learning never rewrites the archive or stores note bodies. IO callers only. */
class ScreeningStore(context: Context) {
    private val helper = database(context)
    private val prefs = context.applicationContext.getSharedPreferences("cobalt_screening", Context.MODE_PRIVATE)
    private val widgetPrefs = context.applicationContext.getSharedPreferences("cobalt_screening_widgets", Context.MODE_PRIVATE)
    private val revision = revisions(context)
    /** Only committed learning changes invalidate the app's shared read model. */
    val changes: StateFlow<Long> = revision.asStateFlow()
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) { prefs.edit().putBoolean("enabled", value).commit() }
    var hour: Int
        get() = prefs.getInt("hour", 9).coerceIn(5, 11)
        set(value) { prefs.edit().putInt("hour", value.coerceIn(5, 11)).commit() }
    var minute: Int
        get() = prefs.getInt("minute", 0).coerceIn(0, 59)
        set(value) { prefs.edit().putInt("minute", value.coerceIn(0, 59)).commit() }
    var weekends: Boolean
        get() = prefs.getBoolean("weekends", true)
        set(value) { prefs.edit().putBoolean("weekends", value).commit() }

    data class Delivery(val token: String, val key: String, val itemId: String, val at: Long, val answered: Boolean,
        val posted: Boolean = false, val publishedAt: Long? = null)
    data class LearningSnapshot(val signals: ScreeningSignals, val history: List<ScreeningHistoryChange>,
        val publications: List<ScreeningDecisionSnapshot>, val deliveries: Map<String, Delivery> = emptyMap())
    private fun signal(db: SQLiteDatabase, key: String): ScreeningItemSignal = db.rawQuery(
        "SELECT data FROM signals WHERE k = ?", arrayOf(key)).use {
        if (it.moveToFirst()) decode(JSONObject(it.getString(0))) else ScreeningItemSignal()
    }
    private fun put(db: SQLiteDatabase, key: String, itemId: String, value: ScreeningItemSignal, now: Long) {
        db.insertWithOnConflict("signals", null, ContentValues().apply {
            put("k", key); put("item_id", itemId); put("data", encode(value).toString()); put("at", now)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    private fun <T> transaction(notify: Boolean = true, block: (SQLiteDatabase) -> T): T {
        val db = helper.writableDatabase
        db.beginTransactionNonExclusive()
        var changed = false
        try {
            val before = if (notify) totalChanges(db) else 0L
            val result = block(db)
            changed = notify && totalChanges(db) != before
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
            if (changed) revision.update { it + 1 }
        }
    }
    private fun totalChanges(db: SQLiteDatabase): Long = db.rawQuery("SELECT total_changes()", null).use { it.moveToFirst(); it.getLong(0) }
    fun observe(items: List<SavedItem>, now: Long = System.currentTimeMillis()): ScreeningSignals = transaction { db ->
        val records = ScreeningLearningRecords(db)
        ScreeningIdentity.resolve(items, records.aliases()).forEach { title ->
            val item = title.item
            val key = title.key
            val oldKeys = (title.aliases + title.aliases.map(records::resolve) + key).distinct()
            // Local and provider aliases share learning when metadata enriches a record.
            for (oldKey in oldKeys.filter { it != key }) {
                db.rawQuery("SELECT data FROM signals WHERE k = ?", arrayOf(oldKey)).use { old ->
                    if (old.moveToFirst()) {
                        val merged = ScreeningRecommendationEngine.mergeSignals(listOf(signal(db, key), decode(JSONObject(old.getString(0)))))
                        put(db, key, item.id, merged, now)
                        db.delete("signals", "k = ?", arrayOf(oldKey))
                    }
                }
                db.execSQL("UPDATE deliveries SET k = ? WHERE k = ?", arrayOf(key, oldKey))
                db.execSQL("UPDATE feedback SET k = ? WHERE k = ?", arrayOf(key, oldKey))
            }
            records.recordTitle(title, oldKeys, now)
            if (ScreeningRecommendationEngine.kind(item) == null) return@forEach
            val prior = signal(db, key)
            val progress = ScreeningRecommendationEngine.progress(item, prior, now)
            val viewing = ScreeningRecommendationEngine.watchStatus(item) == "watching" || progress.watched > 0
            val first = db.rawQuery("SELECT 1 FROM signals WHERE k = ?", arrayOf(key)).use { !it.moveToFirst() }
            val increased = !first && progress.watched > prior.watched
            // Enrichment and generic updatedAt aren't viewing events. First import provides a baseline only.
            val realAt = item.lastWatchedAt?.takeIf { it in 1..now } ?: progress.lastWatchedAt
            val lastProgress = if (increased) realAt?.takeIf { it > (prior.lastProgressAt ?: 0) } ?: now
                else listOfNotNull(prior.lastProgressAt, realAt).maxOrNull()
            val regression = progress.watched < prior.watched
            val datedIncrease = increased && realAt != null && realAt > (prior.samples.lastOrNull()?.at ?: 0)
            val sample = when {
                first || regression -> listOf(ProgressSample(now, progress.watched))
                datedIncrease -> (prior.samples + ProgressSample(realAt!!, progress.watched)).takeLast(12)
                else -> prior.samples
            }
            put(db, key, item.id, prior.copy(watchingSince = prior.watchingSince ?: if (viewing) realAt ?: now else null,
                lastProgressAt = lastProgress, watched = progress.watched, samples = sample), now)
        }
        records.importLegacy(widgetPrefs.all, now)
        prune(db, now)
        snapshot(db, now)
    }
    fun snapshot(now: Long = System.currentTimeMillis()): ScreeningSignals = snapshot(helper.readableDatabase, now)
    /** One consistent read across evidence, corrections and publications, with no learning writes. */
    fun learningSnapshot(now: Long = System.currentTimeMillis()): LearningSnapshot = transaction(notify = false) { db ->
        val records = ScreeningLearningRecords(db)
        val deliveries = mutableMapOf<String, Delivery>()
        db.rawQuery("SELECT token, k, item_id, at, answered, posted, published_at FROM deliveries WHERE at >= ? AND at <= ? ORDER BY at DESC LIMIT 100",
            arrayOf((now - 14 * SCREENING_DAY).toString(), now.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                val value = Delivery(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getLong(3),
                    cursor.getInt(4) != 0, cursor.getInt(5) != 0, if (cursor.isNull(6)) null else cursor.getLong(6))
                deliveries[value.token] = value
            }
        }
        LearningSnapshot(snapshot(db, now), records.history(), records.publications(), deliveries)
    }
    private fun snapshot(db: SQLiteDatabase, now: Long): ScreeningSignals {
        val items = mutableMapOf<String, ScreeningItemSignal>()
        db.rawQuery("SELECT k, data FROM signals", null).use { while (it.moveToNext()) items[it.getString(0)] = decode(JSONObject(it.getString(1))) }
        val events = mutableListOf<ScreeningFeedbackEvent>()
        db.rawQuery("SELECT k, kind, at, genres, rowid, origin, title, item_id FROM feedback WHERE at >= ? ORDER BY at DESC, rowid DESC LIMIT 500",
            arrayOf((now - 180 * SCREENING_DAY).toString())).use {
            while (it.moveToNext()) events += ScreeningFeedbackEvent(it.getString(0), ScreeningFeedback.valueOf(it.getString(1)),
                it.getLong(2), strings(JSONArray(it.getString(3))), "feedback:${it.getLong(4)}",
                if (it.isNull(5)) null else it.getString(5), if (it.isNull(6)) null else it.getString(6), if (it.isNull(7)) null else it.getString(7))
        }
        return ScreeningLearningRecords(db).augment(ScreeningSignals(items, events), widgetPrefs.all, now)
    }
    fun delivery(token: String, now: Long = System.currentTimeMillis()): Delivery? = helper.readableDatabase.rawQuery(
        "SELECT token, k, item_id, at, answered, posted, published_at FROM deliveries WHERE token = ?", arrayOf(token)).use {
        if (!it.moveToFirst() || now - it.getLong(3) > 14 * SCREENING_DAY || it.getLong(3) > now) null
        else Delivery(it.getString(0), it.getString(1), it.getString(2), it.getLong(3), it.getInt(4) != 0,
            it.getInt(5) != 0, if (it.isNull(6)) null else it.getLong(6))
    }
    fun pendingMorning(day: String, now: Long): Delivery? = helper.readableDatabase.rawQuery(
        "SELECT token FROM deliveries WHERE day = ? AND daily = 1 AND posted = 0 AND answered = 0", arrayOf(day)).use {
        if (it.moveToFirst()) delivery(it.getString(0), now) else null
    }
    fun markPosted(token: String, now: Long = System.currentTimeMillis(), pick: ScreeningRecommendation? = null,
                   source: String = "ALL", mode: String = "Selected") = transaction { db ->
        val value = db.rawQuery("SELECT k, item_id, daily, trace, posted FROM deliveries WHERE token = ?", arrayOf(token)).use {
            if (!it.moveToFirst() || it.getInt(4) != 0) null else {
                val trace = pick?.let(ScreeningScoreTrace::from) ?: if (it.isNull(3)) null else
                    runCatching { ScreeningDecisionCodec.decode(JSONObject(it.getString(3))) }.getOrNull()
                val key = it.getString(0)
                val title = pick?.item?.title ?: db.rawQuery("SELECT title FROM taste_titles WHERE k = ?", arrayOf(key)).use { facts ->
                    if (facts.moveToFirst()) facts.getString(0) else "Saved title"
                }
                ScreeningDecisionSnapshot("delivery:$token", if (it.getInt(2) == 1) "Morning question" else "Review screen", source,
                    key, it.getString(1), title, now, trace, mode, pick?.reason)
            }
        } ?: return@transaction
        publish(db, value)
        db.execSQL("UPDATE deliveries SET posted = 1, published_at = ? WHERE token = ?", arrayOf<Any>(now, token))
    }
    fun createDelivery(pick: ScreeningRecommendation, now: Long, day: String, notification: Boolean): Delivery? = transaction { db ->
        if (!notification) {
            val reusable = db.rawQuery("SELECT token FROM deliveries WHERE k = ? AND item_id = ? AND day = ? AND daily = 0 AND answered = 0 ORDER BY at DESC LIMIT 1",
                arrayOf(pick.key, pick.item.id, day)).use { if (it.moveToFirst()) it.getString(0) else null }
            if (reusable != null) delivery(reusable, now)?.let { return@transaction it }
        }
        if (notification) {
            val previous = db.rawQuery("SELECT at FROM deliveries WHERE daily = 1 ORDER BY at DESC LIMIT 1", null).use { if (it.moveToFirst()) it.getLong(0) else null }
            val recentUnanswered = db.rawQuery("SELECT answered FROM deliveries WHERE daily = 1 ORDER BY at DESC LIMIT 3", null).use {
                var count = 0; while (it.moveToNext()) if (it.getInt(0) == 0) count++; count
            }
            if (previous != null && now - previous < (if (recentUnanswered >= 3) 3 * SCREENING_DAY - 4 * 3_600_000L else 20 * 3_600_000L)) return@transaction null
            if (db.rawQuery("SELECT 1 FROM deliveries WHERE day = ? AND daily = 1", arrayOf(day)).use { it.moveToFirst() }) return@transaction null
        }
        val result = Delivery(UUID.randomUUID().toString(), pick.key, pick.item.id, now, false)
        db.insertOrThrow("deliveries", null, ContentValues().apply {
            put("token", result.token); put("k", result.key); put("item_id", result.itemId); put("at", now)
            put("answered", 0); put("posted", 0); put("day", day); put("daily", if (notification) 1 else 0)
            put("trace", ScreeningDecisionCodec.encode(ScreeningScoreTrace.from(pick)).toString())
        })
        prune(db, now); result
    }
    /** Transactional first-answer-wins. Stale or replaced titles cannot train on a notification. */
    fun answer(token: String, item: SavedItem, kind: ScreeningFeedback, now: Long = System.currentTimeMillis()): Boolean = transaction { db ->
        val delivery = delivery(token, now) ?: return@transaction false
        if (delivery.answered || item.isDeleted || item.isArchived || item.isUnavailable ||
            item.id != delivery.itemId || ScreeningLearningRecords(db).resolve(ScreeningRecommendationEngine.key(item)) != delivery.key) return@transaction false
        val prior = signal(db, delivery.key)
        val suppress = when (kind) {
            ScreeningFeedback.NOT_NOW -> now + 7 * SCREENING_DAY
            ScreeningFeedback.PAUSED -> now + 30 * SCREENING_DAY
            ScreeningFeedback.WATCHED -> now + 2 * SCREENING_DAY
            else -> now + 2 * SCREENING_DAY
        }
        put(db, delivery.key, item.id, prior.copy(notInterested = kind == ScreeningFeedback.NOT_INTERESTED || prior.notInterested,
            suppressedUntil = suppress, lastFeedbackAt = now), now)
        val origin = if (delivery.posted && db.rawQuery("SELECT daily FROM deliveries WHERE token = ?", arrayOf(token)).use {
            it.moveToFirst() && it.getInt(0) == 1
        }) "Morning question" else "Review screen"
        db.insertOrThrow("feedback", null, ContentValues().apply {
            put("k", delivery.key); put("kind", kind.name); put("at", now); put("genres", JSONArray(ScreeningGenres.normalize(item.genres)).toString())
            put("origin", origin)
            put("title", item.title.take(180)); put("item_id", item.id)
        })
        db.execSQL("UPDATE deliveries SET answered = 1 WHERE token = ?", arrayOf(token))
        ScreeningLearningRecords(db).history(delivery.key, kind.name, kind.label, null, origin, now)
        prune(db, now); true
    }
    fun canonicalKey(item: SavedItem): String = canonicalKey(ScreeningRecommendationEngine.key(item))
    fun canonicalKey(key: String): String = ScreeningLearningRecords(helper.readableDatabase).resolve(key)
    fun reaction(itemId: String): ScreeningReaction? {
        val records = ScreeningLearningRecords(helper.readableDatabase)
        val keys = listOf("movie:local:$itemId", "tv:local:$itemId", "media:local:$itemId")
        val existing = keys.mapNotNull(records::reaction).maxWithOrNull(compareBy<ScreeningReaction> { it.at ?: 0L }.thenBy { it.revision })
        return existing ?: records.legacyProjection(widgetPrefs.all.filterKeys { it == "thumb_$itemId" }, System.currentTimeMillis()).lastOrNull()
    }
    fun toggleReaction(itemId: String, key: String, choice: ScreeningPreference, origin: String = "Widget", now: Long = System.currentTimeMillis()): Boolean {
        transaction { db ->
            val records = ScreeningLearningRecords(db); records.importLegacy(widgetPrefs.all, now)
            val canonical = records.resolve(key)
            // A title can be voted on before its next background observation.
            db.insertWithOnConflict("taste_aliases", null, ContentValues().apply {
                put("alias", "${key.substringBefore(':')}:local:$itemId"); put("k", canonical)
            }, SQLiteDatabase.CONFLICT_IGNORE)
            records.toggle(canonical, choice, origin, now); records.prune()
        }
        touchWidgets(); return true
    }
    fun clearReaction(key: String, now: Long = System.currentTimeMillis()) {
        transaction { db ->
            val records = ScreeningLearningRecords(db)
            records.importLegacy(widgetPrefs.all, now)
            records.clearReaction(key, now); records.prune()
        }
        touchWidgets()
    }
    fun ignoreEvidence(id: String, ignored: Boolean, now: Long = System.currentTimeMillis()) = transaction { db ->
        require(id.startsWith("rating:") || id.matches(Regex("feedback:[0-9]+")))
        val records = ScreeningLearningRecords(db)
        val canonical = if (id.startsWith("rating:")) "rating:${records.resolve(id.removePrefix("rating:"))}" else id
        records.ignore(canonical, ignored, now)
        if (id.startsWith("feedback:")) {
            val key = db.rawQuery("SELECT k FROM feedback WHERE rowid = ?", arrayOf(id.removePrefix("feedback:"))).use { if (it.moveToFirst()) it.getString(0) else null }
            if (key != null) recomputeFeedback(db, key, now)
        }
        records.prune()
    }
    private fun recomputeFeedback(db: SQLiteDatabase, key: String, now: Long) {
        val ignored = ScreeningLearningRecords(db).ignored()
        val events = snapshot(db, now).feedback.filter { it.key == key }
        val active = events.filter { it.id !in ignored }
        val prior = signal(db, key)
        val hidden = active.any { it.kind == ScreeningFeedback.NOT_INTERESTED } ||
            (prior.notInterested && events.none { it.kind == ScreeningFeedback.NOT_INTERESTED })
        val until = active.maxOfOrNull { it.at + when (it.kind) {
            ScreeningFeedback.NOT_NOW -> 7 * SCREENING_DAY; ScreeningFeedback.PAUSED -> 30 * SCREENING_DAY; else -> 2 * SCREENING_DAY
        } } ?: if (events.isEmpty()) prior.suppressedUntil else 0
        put(db, key, "", prior.copy(notInterested = hidden, suppressedUntil = until,
            lastFeedbackAt = active.maxOfOrNull { it.at }), now)
    }
    fun recommendAgain(key: String, now: Long = System.currentTimeMillis()) = transaction { db ->
        val records = ScreeningLearningRecords(db); val canonical = records.resolve(key)
        val prior = signal(db, canonical)
        db.rawQuery("SELECT rowid FROM feedback WHERE k = ? AND kind IN ('NOT_INTERESTED', 'NOT_NOW', 'PAUSED')", arrayOf(canonical)).use {
            while (it.moveToNext()) db.insertWithOnConflict("taste_ignored", null, ContentValues().apply { put("id", "feedback:${it.getLong(0)}") }, SQLiteDatabase.CONFLICT_IGNORE)
        }
        put(db, canonical, "", prior.copy(notInterested = false, suppressedUntil = 0), now)
        db.execSQL("UPDATE deliveries SET answered = 1 WHERE k = ?", arrayOf(canonical))
        records.history(canonical, "ALLOW", null, null, "Your taste", now); records.prune()
    }
    fun history(): List<ScreeningHistoryChange> = ScreeningLearningRecords(helper.readableDatabase).history()
    fun publications(): List<ScreeningDecisionSnapshot> = ScreeningLearningRecords(helper.readableDatabase).publications()
    fun publish(value: ScreeningDecisionSnapshot): Boolean = transaction { db -> publish(db, value) }
    private fun publish(db: SQLiteDatabase, value: ScreeningDecisionSnapshot): Boolean {
        val records = ScreeningLearningRecords(db)
        if (!records.publish(value)) return false
        val key = records.resolve(value.key); val prior = signal(db, key)
        put(db, key, value.itemId, prior.copy(lastShownAt = maxOf(prior.lastShownAt ?: 0, value.at), shownCount = prior.shownCount + 1), value.at)
        records.prune(); return true
    }
    private fun touchWidgets() { widgetPrefs.edit().putLong("taste_revision", widgetPrefs.getLong("taste_revision", 0) + 1).commit() }
    fun resetLearning() {
        transaction { db ->
            val records = ScreeningLearningRecords(db); records.importLegacy(widgetPrefs.all, System.currentTimeMillis())
            db.delete("signals", null, null); db.delete("feedback", null, null); records.reset()
            // Keep daily reservations, and invalidate live questions so stale notifications cannot relearn cleared feedback.
            db.execSQL("UPDATE deliveries SET answered = 1")
        }
        val edit = widgetPrefs.edit(); widgetPrefs.all.keys.filter { it.startsWith("thumb_") }.forEach(edit::remove)
        edit.putLong("taste_revision", widgetPrefs.getLong("taste_revision", 0) + 1).commit()
    }
    private fun prune(db: SQLiteDatabase, now: Long) {
        db.execSQL("DELETE FROM feedback WHERE at < ? OR rowid NOT IN (SELECT rowid FROM feedback ORDER BY at DESC LIMIT 500)", arrayOf(now - 180 * SCREENING_DAY))
        // SQLite may reuse feedback rowids after the retained history becomes empty.
        db.execSQL("DELETE FROM taste_ignored WHERE id LIKE 'feedback:%' AND CAST(substr(id, 10) AS INTEGER) NOT IN (SELECT rowid FROM feedback)")
        db.execSQL("DELETE FROM deliveries WHERE at < ? OR token NOT IN (SELECT token FROM deliveries ORDER BY at DESC LIMIT 100)", arrayOf(now - 30 * SCREENING_DAY))
        db.execSQL("DELETE FROM signals WHERE k NOT IN (SELECT k FROM signals ORDER BY at DESC LIMIT 5000)")
        ScreeningLearningRecords(db).prune()
    }
    companion object {
        private val instances = java.util.WeakHashMap<Context, SQLiteOpenHelper>()
        private val changeStreams = java.util.WeakHashMap<Context, MutableStateFlow<Long>>()
        private fun revisions(context: Context): MutableStateFlow<Long> = synchronized(changeStreams) {
            changeStreams.getOrPut(context.applicationContext) { MutableStateFlow(0L) }
        }
        private fun database(context: Context): SQLiteOpenHelper = synchronized(instances) {
            val owner = context.applicationContext
            instances[owner] ?: object : SQLiteOpenHelper(owner, "screening_learning.db", null, 3) {
                override fun onCreate(db: SQLiteDatabase) {
                    db.execSQL("CREATE TABLE signals (k TEXT PRIMARY KEY, item_id TEXT NOT NULL, data TEXT NOT NULL, at INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE feedback (k TEXT NOT NULL, kind TEXT NOT NULL, at INTEGER NOT NULL, genres TEXT NOT NULL)")
                    db.execSQL("CREATE TABLE deliveries (token TEXT PRIMARY KEY, k TEXT NOT NULL, item_id TEXT NOT NULL, at INTEGER NOT NULL, answered INTEGER NOT NULL, posted INTEGER NOT NULL DEFAULT 0, day TEXT NOT NULL, daily INTEGER NOT NULL)")
                    db.execSQL("CREATE UNIQUE INDEX screening_daily ON deliveries(day) WHERE daily = 1")
                    ScreeningLearningRecords.install(db)
                }
                override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    if (oldVersion < 2) {
                        val hasPosted = db.rawQuery("PRAGMA table_info(deliveries)", null).use { c ->
                            var found = false
                            while (c.moveToNext()) if (c.getString(c.getColumnIndexOrThrow("name")) == "posted") found = true
                            found
                        }
                        if (!hasPosted) db.execSQL("ALTER TABLE deliveries ADD COLUMN posted INTEGER NOT NULL DEFAULT 0")
                    }
                    if (oldVersion < 3) ScreeningLearningRecords.install(db)
                }
            }.also { it.setWriteAheadLoggingEnabled(true); instances[owner] = it }
        }
        private fun strings(array: JSONArray): List<String> = (0 until array.length()).map { array.getString(it) }
        private fun encode(s: ScreeningItemSignal) = JSONObject().apply {
            put("since", s.watchingSince); put("progress", s.lastProgressAt); put("watched", s.watched)
            put("shown", s.lastShownAt); put("count", s.shownCount); put("until", s.suppressedUntil)
            put("no", s.notInterested); put("feedback", s.lastFeedbackAt)
            put("samples", JSONArray(s.samples.map { JSONObject().put("at", it.at).put("watched", it.watched) }))
        }
        private fun decode(j: JSONObject): ScreeningItemSignal {
            fun time(key: String) = if (j.has(key) && !j.isNull(key)) j.getLong(key) else null
            val a = j.optJSONArray("samples") ?: JSONArray()
            return ScreeningItemSignal(time("since"), time("progress"), j.optInt("watched"),
                (0 until a.length()).map { a.getJSONObject(it).let { o -> ProgressSample(o.getLong("at"), o.getInt("watched")) } },
                time("shown"), j.optInt("count"), j.optLong("until"), j.optBoolean("no"), time("feedback"))
        }
    }
}
