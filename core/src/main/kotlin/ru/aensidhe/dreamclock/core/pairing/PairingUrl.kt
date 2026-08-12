package ru.aensidhe.dreamclock.core.pairing

object PairingUrl {
    fun build(
        address: String,
        port: Int,
        keyBase64Url: String,
        lang: String = "",
    ): String {
        val host = if (address.contains(':')) "[$address]" else address
        val langSuffix = if (lang.isBlank()) "" else "&lang=$lang"
        return "http://$host:$port/#k=$keyBase64Url$langSuffix"
    }
}
