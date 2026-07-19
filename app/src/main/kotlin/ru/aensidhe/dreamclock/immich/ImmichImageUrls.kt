package ru.aensidhe.dreamclock.immich

object ImmichImageUrls {
    fun preview(
        host: String,
        id: String,
    ): String = "${asset(host, id)}/thumbnail?size=preview"

    fun placeholder(
        host: String,
        id: String,
    ): String = "${asset(host, id)}/thumbnail?size=thumbnail"

    private fun asset(
        host: String,
        id: String,
    ): String = "${host.trimEnd('/')}/api/assets/$id"
}
