package com.rustpin.android.store

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Target lives in Target.kt (pure Kotlin + unit tests). This file is just persistence.
private val Context.ds by preferencesDataStore("rustpin")

data class AppSettings(
    val targetKey: String = "auto",
    val lastQuery: String = "",
    val hdPreview: Boolean = true,
    val accentKey: String = "red",
) {
    val target: Target get() = Target.parse(targetKey)
    val accent: Accent get() = Accent.parse(accentKey)
}

class SettingsStore(private val ctx: Context) {
    private val K_TARGET = stringPreferencesKey("target")
    private val K_QUERY = stringPreferencesKey("last_query")
    private val K_HD = booleanPreferencesKey("hd_preview")
    private val K_ACCENT = stringPreferencesKey("accent")

    val flow: Flow<AppSettings> = ctx.ds.data.map { p ->
        AppSettings(
            p[K_TARGET] ?: "auto",
            p[K_QUERY] ?: "",
            p[K_HD] ?: true,
            p[K_ACCENT] ?: "red",
        )
    }

    suspend fun setTarget(key: String) { ctx.ds.edit { it[K_TARGET] = key } }
    suspend fun setQuery(q: String) { ctx.ds.edit { it[K_QUERY] = q.take(120) } }
    suspend fun setHd(v: Boolean) { ctx.ds.edit { it[K_HD] = v } }
    suspend fun setAccent(key: String) { ctx.ds.edit { it[K_ACCENT] = key } }
}
