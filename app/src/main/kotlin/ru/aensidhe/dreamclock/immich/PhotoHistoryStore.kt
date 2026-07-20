package ru.aensidhe.dreamclock.immich

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.first

private val Context.photoHistoryStore: DataStore<PhotoHistoryProto> by dataStore(
    fileName = "photo_history.pb",
    serializer = PhotoHistorySerializer,
    corruptionHandler = ReplaceFileCorruptionHandler { PhotoHistoryProto.getDefaultInstance() },
)

class PhotoHistoryStore internal constructor(
    private val store: DataStore<PhotoHistoryProto>,
) {
    suspend fun current(): PhotoHistoryProto = store.data.first()

    suspend fun update(transform: (PhotoHistoryProto) -> PhotoHistoryProto) {
        store.updateData(transform)
    }

    companion object {
        fun from(context: Context): PhotoHistoryStore = PhotoHistoryStore(context.photoHistoryStore)
    }
}
