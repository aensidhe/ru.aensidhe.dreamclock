package ru.aensidhe.dreamclock.core.pairing

object PairingUrl {
    /**
     * Builds the pairing URL for [address].
     *
     * [address] is expected UNBRACKETED — a bare IPv4/IPv6 literal or a hostname. IPv6 literals
     * are auto-bracketed (detected by the presence of a ':'), so callers must not pass an
     * already-bracketed address.
     */
    fun build(
        address: String,
        port: Int,
        keyBase64Url: String,
    ): String {
        val host = if (address.contains(':')) "[$address]" else address
        return "http://$host:$port/#k=$keyBase64Url"
    }
}
