package io.lightprofiles.karoo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "light_profiles")

class SettingsStore(private val context: Context) {

    companion object {
        private val KEY = stringPreferencesKey("settings")
        private val json = Json { ignoreUnknownKeys = true }
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        prefs[KEY]?.let {
            try {
                json.decodeFromString<AppSettings>(it)
            } catch (e: Exception) {
                Timber.e(e, "Stored settings unreadable, starting fresh")
                null
            }
        } ?: AppSettings()
    }

    suspend fun update(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY] = json.encodeToString(settings)
        }
    }
}
