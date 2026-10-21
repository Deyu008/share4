package com.deyu.share.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 发博草稿箱(本地持久化,仿原版 StatusDraftActivity 的草稿数据)。
 * 存纯文本(配图字节不入草稿,与原版行为一致)。
 */
object DraftStore {
    private const val FILE = "drafts"
    private const val KEY = "drafts"

    data class Draft(val id: Long, val text: String, val updatedAt: Long)

    fun list(ctx: Context): List<Draft> {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val arr = runCatching { JSONArray(sp.getString(KEY, "[]")) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                Draft(o.getLong("id"), o.getString("text"), o.getLong("updatedAt"))
            }.getOrNull()
        }.sortedByDescending { it.updatedAt }
    }

    fun save(ctx: Context, text: String): Draft {
        val d = Draft(System.currentTimeMillis(), text.trim(), System.currentTimeMillis())
        val all = list(ctx).toMutableList() + d
        persist(ctx, all)
        return d
    }

    fun remove(ctx: Context, id: Long) = persist(ctx, list(ctx).filter { it.id != id })

    private fun persist(ctx: Context, drafts: List<Draft>) {
        val arr = JSONArray()
        drafts.forEach { d ->
            arr.put(JSONObject().put("id", d.id).put("text", d.text).put("updatedAt", d.updatedAt))
        }
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
