package com.example.widget

import android.content.Context
import com.example.data.local.CustomFolderEntity
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.util.displayTitle
import org.json.JSONArray
import org.json.JSONObject

data class FolioSnapshot(val folder: CustomFolderEntity, val items: List<SavedItem>, val count: Int)

/** A bounded, durable preview: five rows with short excerpts and thumbnail pointers, never full note bodies. */
class FolioSnapshotStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("cobalt_folio_snapshots", Context.MODE_PRIVATE)
    fun save(id: Int, folder: CustomFolderEntity, items: List<SavedItem>) {
        val rows = JSONArray()
        items.take(5).forEach { item -> rows.put(JSONObject().put("id", item.id).put("title", item.displayTitle(context).take(180)).put("type", item.type.name)
            .put("excerpt", FolioRows.excerpt(item)).put("media", if (item.type in listOf(SavedItemType.IMAGE, SavedItemType.VIDEO)) item.content.take(2048) else "")
            .put("thumbnail", item.thumbnailPath?.take(2048) ?: "").put("linkImage", item.linkImage?.take(2048) ?: "").put("updated", item.updatedAt)) }
        val json = JSONObject().put("folder", folder.name).put("color", folder.colorHex ?: JSONObject.NULL)
            .put("icon", folder.iconName ?: JSONObject.NULL).put("count", items.size).put("items", rows)
        check(prefs.edit().putString(id.toString(), json.toString()).commit())
    }
    fun get(id: Int, config: FolioConfig): FolioSnapshot? = runCatching {
        val json = JSONObject(prefs.getString(id.toString(), null) ?: return null)
        if (config.folderName == null || json.getString("folder") != config.folderName) return null
        fun optional(key: String) = if (json.isNull(key)) null else json.getString(key)
        val folder = CustomFolderEntity(config.folderName, colorHex = optional("color"), iconName = optional("icon"))
        val rows = json.getJSONArray("items")
        val items = (0 until rows.length().coerceAtMost(5)).map {
            val row = rows.getJSONObject(it)
            SavedItem(id = row.getString("id"), title = row.getString("title"), type = SavedItemType.valueOf(row.getString("type")), content = row.optString("media").ifBlank { row.optString("excerpt") }, linkDescription = row.optString("excerpt"),
                thumbnailPath = row.optString("thumbnail").ifBlank { null }, linkImage = row.optString("linkImage").ifBlank { null },
                updatedAt = row.optLong("updated"), folders = listOf(folder.name))
        }
        FolioSnapshot(folder, items, json.getInt("count"))
    }.getOrNull()
    fun delete(id: Int) { prefs.edit().remove(id.toString()).apply() }
}
