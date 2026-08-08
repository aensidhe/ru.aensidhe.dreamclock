package ru.aensidhe.dreamclock.pairing

import java.net.NetworkInterface
import ru.aensidhe.dreamclock.core.pairing.NicAddress

object LanInterfaces {
    fun enumerate(): List<NicAddress> =
        NetworkInterface
            .getNetworkInterfaces()
            .toList()
            .flatMap { nic ->
                val up = runCatching { nic.isUp }.getOrDefault(false)
                val loopback = runCatching { nic.isLoopback }.getOrDefault(true)
                nic.inetAddresses.toList().map { addr ->
                    NicAddress(
                        interfaceName = nic.name,
                        address = addr.hostAddress?.substringBefore('%').orEmpty(),
                        isUp = up,
                        isLoopback = loopback,
                    )
                }
            }.filter { it.address.isNotBlank() }
}
