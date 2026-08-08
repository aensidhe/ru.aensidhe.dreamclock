package ru.aensidhe.dreamclock.core.pairing

object PairingUrl {
    fun build(
        address: String,
        port: Int,
        keyBase64Url: String,
    ): String {
        val host = if (address.contains(':')) "[$address]" else address
        return "http://$host:$port/#k=$keyBase64Url"
    }
}
