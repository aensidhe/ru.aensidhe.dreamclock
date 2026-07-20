package ru.aensidhe.dreamclock.immich

import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream

object PhotoHistorySerializer : Serializer<PhotoHistoryProto> {
    override val defaultValue: PhotoHistoryProto = PhotoHistoryProto.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): PhotoHistoryProto = PhotoHistoryProto.parseFrom(input)

    override suspend fun writeTo(
        t: PhotoHistoryProto,
        output: OutputStream,
    ) = t.writeTo(output)
}
