package ru.aensidhe.dreamclock.settings

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

// Backs SettingsRepository.inMemory() so its test can run on the JVM without Android.
class InMemorySettingsDataStore : DataStore<Settings> {
    private val state = MutableStateFlow(SettingsSerializer.defaultValue)

    override val data: Flow<Settings> = state

    override suspend fun updateData(transform: suspend (t: Settings) -> Settings): Settings {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

// Backed by an in-memory DataStore so SettingsRepository tests run on the JVM without Android.
fun SettingsRepository.Companion.inMemory(): SettingsRepository = SettingsRepository(InMemorySettingsDataStore())
