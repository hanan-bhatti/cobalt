package com.example.widget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import com.example.media.recommendation.ScreeningDecisionCodec
import com.example.media.recommendation.ScreeningPreference
import com.example.media.recommendation.ScreeningStore

/** Config, current pick and shuffle cycle commit together and survive process death. */
class ScreeningWidgetStore(context: Context) {
    private val appContext = context.applicationContext
    val preferences = context.applicationContext.getSharedPreferences("cobalt_screening_widgets", Context.MODE_PRIVATE)
    private fun key(id: Int) = if (id == -1) "draft" else "widget_$id"
    /** Config/display metadata only; suitable for background preparation without opening the learning DB. */
    fun getSaved(id: Int): ScreeningWidgetEntry {
        val entry = decode(preferences.getString(key(id), null))
        val session = com.example.data.remote.trakt.TraktAuthManager.getInstance(appContext).playbackSession.value
        val visible = if (session != null && entry.pick?.playbackSession == session) entry
            else entry.copy(pick = entry.pick?.copy(playbackProgress = null, playbackSession = null))
        return visible
    }
    fun get(id: Int): ScreeningWidgetEntry = getSaved(id).let { it.copy(thumb = it.pick?.itemId?.let(::thumb)) }
    fun thumb(itemId: String): ScreeningThumb? = ScreeningStore(appContext).reaction(itemId)?.value?.let { ScreeningThumb.valueOf(it.name) }
    /** Local recommendation preference, reversible across cards; never a watch event or Trakt rating. */
    fun toggleThumb(id: Int, generation: Long, itemId: String, choice: ScreeningThumb): Boolean = synchronized(lock) {
        if (!isConfigured(id)) return@synchronized false
        val entry = get(id)
        if (entry.generation != generation || entry.pick?.itemId != itemId) return@synchronized false
        togglePreference(itemId, entry.pick.key, choice)
    }
    fun togglePreference(itemId: String, pickKey: String, choice: ScreeningThumb, origin: String = "Widget"): Boolean = synchronized(lock) {
        ScreeningStore(appContext).toggleReaction(itemId, pickKey, ScreeningPreference.valueOf(choice.name), origin)
    }
    fun isConfigured(id: Int) = id > 0 && preferences.contains(key(id))
    fun save(id: Int, entry: ScreeningWidgetEntry) = synchronized(lock) {
        check(preferences.edit().putString(key(id), encode(entry)).commit())
    }
    fun saveConfig(id: Int, config: ScreeningWidgetConfig) = synchronized(lock) {
        val current = get(id)
        save(id, current.copy(config = config, generation = current.generation + 1))
    }
    fun compareAndSave(id: Int, expected: ScreeningWidgetEntry, updated: ScreeningWidgetEntry): Boolean = synchronized(lock) {
        if (!isConfigured(id) || get(id) != expected) false else { save(id, updated); true }
    }
    fun saveConfigured(id: Int, config: ScreeningWidgetConfig): Boolean = synchronized(lock) {
        if (!isConfigured(id)) false else { saveConfig(id, config); true }
    }
    fun delete(id: Int) = synchronized(lock) { preferences.edit().remove(key(id)).apply() }
    fun stagePin(entry: ScreeningWidgetEntry): String = synchronized(lock) {
        val now = System.currentTimeMillis()
        val tokens = preferences.all.keys.filter { it.startsWith("pin_") && it.endsWith("_at") }
            .sortedByDescending { preferences.getLong(it, 0) }
        val edit = preferences.edit()
        tokens.forEachIndexed { index, at ->
            if (index >= 15 || now - preferences.getLong(at, 0) > 86_400_000L) edit.remove(at).remove(at.removeSuffix("_at"))
        }
        val token = UUID.randomUUID().toString()
        check(edit.putString("pin_$token", encode(entry)).putLong("pin_${token}_at", now).commit())
        token
    }
    fun finishPin(token: String, id: Int): Boolean = synchronized(lock) {
        val at = preferences.getLong("pin_${token}_at", 0)
        val now = System.currentTimeMillis()
        val raw = preferences.getString("pin_$token", null)
        if (id <= 0 || raw == null || at <= 0 || at > now || now - at > 86_400_000L) return@synchronized false
        preferences.edit().putString(key(id), raw).remove("pin_$token").remove("pin_${token}_at").commit()
    }
    private fun encode(entry: ScreeningWidgetEntry): String = JSONObject().apply {
        put("source", entry.config.source.name); put("day", entry.day); put("generation", entry.generation)
        put("count", entry.poolSize); put("seen", JSONArray(entry.seen.takeLast(200)))
        put("pick", entry.pick?.let { p -> JSONObject().apply {
            put("id", p.itemId); put("key", p.key); put("title", p.title.take(180)); put("metadata", p.metadata.take(180))
            put("reason", p.reason.take(240)); put("artwork", p.artwork?.take(2048)); put("kind", p.kind)
            put("playback", p.playbackProgress?.takeIf { it.isFinite() && it > 0.0 && it < 100.0 })
            put("playbackSession", p.playbackSession)
            put("trace", p.trace?.let(ScreeningDecisionCodec::encode))
        } })
    }.toString()
    private fun decode(raw: String?): ScreeningWidgetEntry = runCatching {
        val json = JSONObject(raw ?: "{}")
        val p = json.optJSONObject("pick")
        val seen = json.optJSONArray("seen") ?: JSONArray()
        ScreeningWidgetEntry(
            ScreeningWidgetConfig(runCatching { ScreeningSource.valueOf(json.optString("source")) }.getOrDefault(ScreeningSource.ALL)),
            p?.let { ScreeningPick(it.getString("id"), it.getString("key"), it.getString("title").take(180),
                it.optString("metadata").take(180), it.optString("reason").take(240),
                if (it.isNull("artwork")) null else it.optString("artwork").takeIf(String::isNotBlank)?.take(2048), it.getString("kind"),
                it.optDouble("playback", Double.NaN).takeIf { value -> value.isFinite() && value > 0.0 && value < 100.0 },
                it.optString("playbackSession").takeIf(String::isNotBlank), ScreeningDecisionCodec.decode(it.optJSONObject("trace"))) },
            json.optInt("count").coerceIn(0, 200), (0 until seen.length().coerceAtMost(200)).map { seen.getString(it) },
            json.optString("day"), json.optLong("generation").coerceAtLeast(0)
        )
    }.getOrDefault(ScreeningWidgetEntry())
    companion object { private val lock = Any() }
}
