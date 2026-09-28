package dev.smoreg.raa.ui.qr

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.smoreg.raa.R
import dev.smoreg.raa.RaaApp
import dev.smoreg.raa.data.QrCode
import dev.smoreg.raa.mission.QrScanner
import dev.smoreg.raa.ui.Hint
import dev.smoreg.raa.ui.PrimaryButton
import dev.smoreg.raa.ui.TopBar
import dev.smoreg.raa.ui.card
import dev.smoreg.raa.ui.theme.LocalPalette
import dev.smoreg.raa.ui.theme.Type
import kotlinx.coroutines.launch

private const val NAME_MAX = 40

private sealed interface Naming {
    data object Generated : Naming
    data class Scanned(val payload: String) : Naming
}

@Composable
fun QrScreen(onBack: () -> Unit) {
    val c = RaaApp.container
    val context = LocalContext.current
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val codes by remember { c.db.qrCodes().observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())
    var naming by remember { mutableStateOf<Naming?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Int?>(null) }
    val cameraAsk = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) scanning = true }

    fun startScan() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) scanning = true else cameraAsk.launch(Manifest.permission.CAMERA)
    }

    Column(Modifier.fillMaxSize().background(p.paper).statusBarsPadding()) {
        TopBar(stringResource(R.string.qr_codes), onBack = { if (scanning) scanning = false else onBack() })

        if (scanning) {
            Column(Modifier.padding(20.dp)) {
                Hint(stringResource(R.string.scan_own_hint))
                Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(top = 16.dp).clip(RoundedCornerShape(28.dp))) {
                    QrScanner(
                        onScan = { v ->
                            if (scanning) {
                                scanning = false
                                if (codes.any { it.payload == v }) message = R.string.code_duplicate else naming = Naming.Scanned(v)
                            }
                        },
                        torch = false,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            return@Column
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(stringResource(R.string.qr_intro_title), style = Type.headline, color = p.ink)
                Hint(stringResource(R.string.qr_intro), Modifier.padding(top = 8.dp, bottom = 12.dp))
            }
            items(codes, key = { it.id }) { code ->
                Column(Modifier.fillMaxWidth().card()) {
                    Text(code.name, style = Type.title, color = p.ink)
                    Hint(stringResource(if (code.generated) R.string.code_generated else R.string.code_registered))
                    Row(Modifier.padding(top = 8.dp)) {
                        if (code.generated) {
                            TextButton(onClick = { scope.launch { printCode(context, code) } }) { Text(stringResource(R.string.print)) }
                        }
                        TextButton(onClick = {
                            scope.launch {
                                // The last code cannot go while a QR alarm depends on it.
                                if (codes.size == 1 && c.db.alarms().enabledQrAlarms() > 0) message = R.string.last_code_blocked
                                else c.db.qrCodes().delete(code)
                            }
                        }) { Text(stringResource(R.string.delete), color = p.inkMuted) }
                    }
                }
            }
        }

        Column(Modifier.navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(stringResource(R.string.create_code), { naming = Naming.Generated })
            OutlinedButton(onClick = ::startScan, Modifier.fillMaxWidth().height(56.dp), shape = CircleShape) {
                Text(stringResource(R.string.register_barcode), color = p.ink)
            }
        }
    }

    naming?.let { n ->
        NameDialog(
            onDismiss = { naming = null },
            onSave = { name ->
                naming = null
                scope.launch {
                    val code = when (n) {
                        Naming.Generated -> QrCode(name = name, payload = QrCode.newPayload(), generated = true)
                        is Naming.Scanned -> QrCode(name = name, payload = n.payload, generated = false)
                    }
                    c.db.qrCodes().insert(code)
                    if (code.generated) printCode(context, code)
                }
            },
        )
    }
    message?.let { m ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(stringResource(m)) },
            confirmButton = { TextButton(onClick = { message = null }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun NameDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val fallback = stringResource(R.string.code_default_name)
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.code_name_title)) },
        text = {
            OutlinedTextField(name, { name = it.take(NAME_MAX) }, singleLine = true, placeholder = { Text(fallback) })
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim().ifEmpty { fallback }) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
