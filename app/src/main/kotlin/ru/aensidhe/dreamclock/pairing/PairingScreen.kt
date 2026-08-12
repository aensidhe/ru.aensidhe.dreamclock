package ru.aensidhe.dreamclock.pairing

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ru.aensidhe.dreamclock.R
import ru.aensidhe.dreamclock.core.pairing.PairingUrl

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PairingScreen(
    address: String,
    port: Int,
    keyBase64Url: String,
    lang: String,
    remainingSeconds: Int,
    status: String,
    onCancel: () -> Unit,
) {
    val url = PairingUrl.build(address, port, keyBase64Url, lang)
    val qr = remember(url) { PairingQr.bitmap(url).asImageBitmap() }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(qr, contentDescription = null, modifier = Modifier.size(360.dp))
            Text(stringResource(R.string.pairing_scan_hint))
            Text(stringResource(R.string.pairing_expires_in, PairingCountdown.format(remainingSeconds)))
            Text(status)
            Button(onClick = onCancel) { Text(stringResource(R.string.pairing_cancel)) }
        }
    }
}
