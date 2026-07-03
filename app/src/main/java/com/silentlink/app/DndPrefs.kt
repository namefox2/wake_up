package com.silentlink.app

import android.content.Context
import com.google.gson.Gson
import com.silentlink.app.model.DndConfig

object DndPrefs {
    private const val PREFS_NAME = "silentlink_dnd"
    private const val KEY_JSON = "dnd_json"
    private val gson = Gson()

    fun save(context: Context, config: DndConfig) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_JSON, gson.toJson(config)).apply()
    }

    fun load(context: Context): DndConfig {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_JSON, null) ?: return DndConfig()
        return runCatching { gson.fromJson(json, DndConfig::class.java) }
            .getOrDefault(DndConfig())
    }
}
