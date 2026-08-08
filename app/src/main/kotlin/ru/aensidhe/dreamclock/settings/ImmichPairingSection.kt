package ru.aensidhe.dreamclock.settings

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.google.protobuf.ByteString
import java.security.SecureRandom
import java.time.LocalDate
import java.time.ZoneId
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.aensidhe.dreamclock.R
import ru.aensidhe.dreamclock.core.pairing.AddressFamily
import ru.aensidhe.dreamclock.core.pairing.InterfaceSelection
import ru.aensidhe.dreamclock.core.pairing.PairingAddress
import ru.aensidhe.dreamclock.immich.ImmichClient
import ru.aensidhe.dreamclock.immich.KeyCipher
import ru.aensidhe.dreamclock.pairing.LanInterfaces
import ru.aensidhe.dreamclock.pairing.PairingAssets
import ru.aensidhe.dreamclock.pairing.PairingController
import ru.aensidhe.dreamclock.pairing.PairingOutcome
import ru.aensidhe.dreamclock.pairing.PairingScreen
import ru.aensidhe.dreamclock.pairing.PairingServer

private const val PAIRING_WINDOW_SECONDS = 300
private const val PAIRING_KEY_BYTES = 32
private const val PAIRING_TICK_MS = 1000L
private const val PAIRING_SAVED_STOP_DELAY_MS = 500L

private fun pairingFamily(proto: AddressFamilyProto): AddressFamily =
    if (proto == AddressFamilyProto.IPV6) AddressFamily.IPV6 else AddressFamily.IPV4

private fun pairingFamilyProto(family: AddressFamily): AddressFamilyProto =
    if (family == AddressFamily.IPV6) AddressFamilyProto.IPV6 else AddressFamilyProto.IPV4

private class PairingSession(
    val server: PairingServer,
    val address: PairingAddress,
    val port: Int,
    val keyBase64Url: String,
)

/** Bundles what [startPairing] needs to resolve an interface, persist credentials, and serve. */
private data class PairingRequest(
    val settings: Settings,
    val cipher: KeyCipher,
    val repository: SettingsRepository,
    val scope: CoroutineScope,
    val context: Context,
)

/**
 * Resolves the configured interface, generates a fresh pairing key, wires a [PairingController]
 * to persist the credentials it receives, and starts a [PairingServer] listening for the phone.
 */
private fun startPairing(
    request: PairingRequest,
    onNoInterface: () -> Unit,
    onSaved: () -> Unit,
): PairingSession? {
    val chosen =
        InterfaceSelection.resolve(
            LanInterfaces.enumerate(),
            request.settings.pairingInterfaceName,
            pairingFamily(request.settings.pairingAddressFamily),
        ) ?: run {
            onNoInterface()
            return null
        }
    val key = ByteArray(PAIRING_KEY_BYTES).also { SecureRandom().nextBytes(it) }
    val keyString = Base64.getUrlEncoder().withoutPadding().encodeToString(key)
    val controller =
        PairingController(key, { host -> ImmichClient.api(host) }, ZoneId.systemDefault()) { creds ->
            request.repository.update {
                it
                    .toBuilder()
                    .setImmichHost(creds.host)
                    .setImmichKeyCiphertext(ByteString.copyFrom(request.cipher.encrypt(creds.apiKey)))
                    .build()
            }
        }
    val server =
        PairingServer(chosen.address, { name -> PairingAssets.read(request.context, name) }) { envelope ->
            val outcome = controller.receive(envelope, LocalDate.now(), request.settings.daysEitherSide)
            if (outcome == PairingOutcome.Saved) {
                // Stopping the embedded server from inside its own request handler would
                // cut the "ok" response off before it reaches the phone, so the shutdown
                // is deferred long enough for the response to flush.
                request.scope.launch {
                    delay(PAIRING_SAVED_STOP_DELAY_MS)
                    withContext(Dispatchers.Main) { onSaved() }
                }
            }
            outcome == PairingOutcome.Saved
        }
    val port = server.start()
    return PairingSession(server, chosen, port, keyString)
}

/**
 * Owns the "pair with phone" flow end to end: the interface picker, the start button, the
 * full-screen QR/countdown overlay while a pairing session is live, and the server lifecycle.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ImmichPairingSection(
    settings: Settings,
    cipher: KeyCipher,
    repository: SettingsRepository,
    scope: CoroutineScope,
) {
    val context = LocalContext.current

    var pairing by remember { mutableStateOf(false) }
    var pairingAddress by remember { mutableStateOf<PairingAddress?>(null) }
    var pairingPort by remember { mutableStateOf(0) }
    var pairingKey by remember { mutableStateOf("") }
    var pairingRemaining by remember { mutableStateOf(PAIRING_WINDOW_SECONDS) }
    var pairingStatusRes by remember { mutableStateOf(R.string.pairing_waiting) }
    var pairingServer by remember { mutableStateOf<PairingServer?>(null) }

    fun stopPairing() {
        pairingServer?.stop()
        pairingServer = null
        pairing = false
    }

    val activeAddress = pairingAddress
    if (pairing && activeAddress != null) {
        LaunchedEffect(activeAddress, pairingPort, pairingKey) {
            pairingRemaining = PAIRING_WINDOW_SECONDS
            while (pairingRemaining > 0) {
                delay(PAIRING_TICK_MS)
                pairingRemaining -= 1
            }
            stopPairing()
        }
        Box(Modifier.fillMaxWidth().height(LocalConfiguration.current.screenHeightDp.dp)) {
            PairingScreen(
                address = activeAddress.address,
                port = pairingPort,
                keyBase64Url = pairingKey,
                remainingSeconds = pairingRemaining,
                status = stringResource(pairingStatusRes),
                onCancel = { stopPairing() },
            )
        }
        return
    }

    PairingInterfaceSection(settings) { candidate ->
        scope.launch {
            repository.update {
                it
                    .toBuilder()
                    .setPairingInterfaceName(candidate.interfaceName)
                    .setPairingAddressFamily(pairingFamilyProto(candidate.family))
                    .build()
            }
        }
    }

    Button(
        onClick = {
            val session =
                startPairing(
                    request = PairingRequest(settings, cipher, repository, scope, context),
                    onNoInterface = { pairingStatusRes = R.string.pairing_no_interface },
                    onSaved = {
                        pairingStatusRes = R.string.pairing_saved
                        stopPairing()
                    },
                )
            if (session != null) {
                pairingPort = session.port
                pairingServer = session.server
                pairingKey = session.keyBase64Url
                pairingStatusRes = R.string.pairing_waiting
                pairingAddress = session.address
                pairing = true
            }
        },
    ) { Text(stringResource(R.string.settings_pair_action)) }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PairingInterfaceSection(
    settings: Settings,
    onSelect: (PairingAddress) -> Unit,
) {
    val candidates = remember { InterfaceSelection.candidates(LanInterfaces.enumerate()) }
    if (candidates.isEmpty()) return
    SectionHeader(stringResource(R.string.settings_pairing_interface))
    candidates.forEach { candidate ->
        val selected =
            candidate.interfaceName == settings.pairingInterfaceName &&
                candidate.family == pairingFamily(settings.pairingAddressFamily)
        SelectableRow(
            label = "${candidate.interfaceName} — ${candidate.address}",
            description = null,
            selected = selected,
        ) { onSelect(candidate) }
    }
}
