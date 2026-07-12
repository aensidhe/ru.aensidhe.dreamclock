package ru.aensidhe.dreamclock.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow

private val Context.settingsStore: DataStore<Settings> by dataStore(
    fileName = "settings.pb",
    serializer = SettingsSerializer,
)

class SettingsRepository internal constructor(
    private val store: DataStore<Settings>,
) {
    val settings: Flow<Settings> = store.data

    suspend fun update(transform: (Settings) -> Settings) {
        store.updateData(transform)
    }

    companion object {
        fun from(context: Context): SettingsRepository = SettingsRepository(context.settingsStore)
    }
}
