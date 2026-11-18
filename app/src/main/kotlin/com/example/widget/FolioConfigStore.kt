package com.example.widget

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.util.UUID

class FolioConfigStore(context: Context) {
    val preferences: SharedPreferences = context.applicationContext.getSharedPreferences("cobalt_folios", Context.MODE_PRIVATE)
    private fun key(id: Int) = if (id == -1) "draft" else "widget_$id"
    fun get(id: Int): FolioConfig = decode(preferences.getString(key(id), null))
    fun isConfigured(id: Int): Boolean = id > 0 && preferences.contains(key(id))
    fun save(id: Int, config: FolioConfig) = synchronized(lock) { check(preferences.edit().putString(key(id), encode(config)).commit()) }
    fun saveConfigured(id: Int, config: FolioConfig): Boolean = synchronized(lock) {
        if (!isConfigured(id)) false else { save(id, config); true }
    }
    fun delete(id: Int) = synchronized(lock) { preferences.edit().remove(key(id)).apply() }
    fun stagePin(config: FolioConfig): String {
        val token = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val edit = preferences.edit()
        preferences.all.keys.filter { it.startsWith("pin_") && it.endsWith("_time") }.forEach {
            if (now - preferences.getLong(it, 0) > 86_400_000L) edit.remove(it).remove(it.removeSuffix("_time"))
        }
        check(edit.putString("pin_$token", encode(config)).putLong("pin_${token}_time", now).commit())
        return token
    }
    fun finishPin(token: String, id: Int): Boolean {
        if (id <= 0) return false
        val raw = preferences.getString("pin_$token", null) ?: return false
        if (System.currentTimeMillis() - preferences.getLong("pin_${token}_time", 0) > 86_400_000L) return false
        return preferences.edit().putString(key(id), raw).remove("pin_$token").remove("pin_${token}_time").commit()
    }
    fun renameFolder(old: String, new: String) = transform {
        if (it.folderName == old) it.copy(folderName = new) else it
    }
    fun removeFolder(name: String) = transform {
        if (it.folderName == name) it.copy(folderName = null, removedFolder = name) else it
    }
    private fun transform(change: (FolioConfig) -> FolioConfig) {
        val edit = preferences.edit()
        preferences.all.forEach { (key, value) ->
            if (value is String && (key == "draft" || key.startsWith("widget_") || key.startsWith("pin_"))) {
                edit.putString(key, encode(change(decode(value))))
            }
        }
        check(edit.commit())
    }
    private fun encode(c: FolioConfig) = JSONObject().apply {
        put("folder", c.folderName ?: JSONObject.NULL); put("color", c.colorHex ?: JSONObject.NULL)
        put("order", c.order.name); put("removed", c.removedFolder ?: JSONObject.NULL)
    }.toString()
    private fun decode(raw: String?): FolioConfig = runCatching {
        val j = JSONObject(raw ?: "{}")
        fun optional(k: String) = if (j.isNull(k)) null else j.optString(k).takeIf { it.isNotBlank() }
        FolioConfig(optional("folder"), optional("color"),
            runCatching { FolioOrder.valueOf(j.optString("order")) }.getOrDefault(FolioOrder.UPDATED), optional("removed"))
    }.getOrDefault(FolioConfig())
    companion object { private val lock = Any() }
}
