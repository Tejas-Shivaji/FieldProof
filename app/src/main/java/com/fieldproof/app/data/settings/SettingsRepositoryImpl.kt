package com.fieldproof.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fieldproof.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "fieldproof_settings")

class SettingsRepositoryImpl(
    context: Context,
) : SettingsRepository {
    private val dataStore = context.applicationContext.settingsDataStore

    override fun observeApiBaseUrl(): Flow<String> =
        dataStore.data.map { it[API_BASE_URL] ?: DEFAULT_API_BASE_URL }

    override suspend fun apiBaseUrl(): String = observeApiBaseUrl().first()

    override suspend fun setApiBaseUrl(url: String) {
        val trimmed = url.trim().trimEnd('/')
        dataStore.edit { it[API_BASE_URL] = trimmed }
    }

    companion object {
        private val API_BASE_URL = stringPreferencesKey("api_base_url")
        const val DEFAULT_API_BASE_URL = "http://10.0.2.2:8000"
    }
}
