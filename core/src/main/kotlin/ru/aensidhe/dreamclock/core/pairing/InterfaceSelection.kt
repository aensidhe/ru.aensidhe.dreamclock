package ru.aensidhe.dreamclock.core.pairing

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

enum class AddressFamily { IPV4, IPV6 }

data class NicAddress(
    val interfaceName: String,
    val address: String,
    val isUp: Boolean,
    val isLoopback: Boolean,
)

data class PairingAddress(
    val interfaceName: String,
    val address: String,
    val family: AddressFamily,
)

object InterfaceSelection {
    /**
     * Addresses are classified via [InetAddress.getByName], which is DNS-capable: given a
     * hostname it will resolve over the network. Callers MUST pass literal IP strings in
     * [records] — enumeration (LanInterfaces) already does — since the hermeticity of this
     * otherwise-pure function depends on the input being literal, not a name to resolve.
     */
    fun candidates(records: List<NicAddress>): List<PairingAddress> =
        records
            .mapNotNull { record ->
                if (!record.isUp || record.isLoopback) return@mapNotNull null
                val inet =
                    runCatching { InetAddress.getByName(record.address) }.getOrNull()
                        ?: return@mapNotNull null
                if (inet.isLinkLocalAddress) return@mapNotNull null
                val rank = rank(inet) ?: return@mapNotNull null
                val family = if (inet is Inet4Address) AddressFamily.IPV4 else AddressFamily.IPV6
                rank to PairingAddress(record.interfaceName, record.address, family)
            }.sortedBy { it.first }
            .map { it.second }

    fun resolve(
        records: List<NicAddress>,
        interfaceName: String?,
        family: AddressFamily?,
    ): PairingAddress? {
        val ordered = candidates(records)
        val saved =
            ordered.firstOrNull { it.interfaceName == interfaceName && it.family == family }
        return saved ?: ordered.firstOrNull()
    }

    private fun rank(inet: InetAddress): Int? =
        when {
            inet is Inet4Address && inet.isSiteLocalAddress -> 0
            inet is Inet4Address -> 1
            inet is Inet6Address && isUniqueLocalAddress(inet) -> 2
            inet is Inet6Address -> 3
            else -> null
        }

    private fun isUniqueLocalAddress(inet: Inet6Address): Boolean {
        val first = inet.address.first().toInt() and 0xFE
        return first == 0xFC
    }
}
