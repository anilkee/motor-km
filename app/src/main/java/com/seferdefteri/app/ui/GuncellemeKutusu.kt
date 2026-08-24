package com.seferdefteri.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.seferdefteri.app.update.UpdateInfo
import com.seferdefteri.app.update.Updater
import kotlinx.coroutines.launch

/**
 * "Yeni surum hazir" kutusu.
 *
 * Indirmeyi ve kurmayi kendi icinde yapar; Ayarlar ekranina gitmeye gerek yok.
 * Bu onemli: giris yapilamadiginda da guncelleme alinabilmeli, yoksa girisi
 * duzelten surume ulasmanin yolu kalmiyor.
 */
@Composable
fun GuncellemeKutusu(
    bilgi: UpdateInfo,
    onKapat: () -> Unit,
    onAtla: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var indiriliyor by remember { mutableStateOf(false) }
    var ilerleme by remember { mutableFloatStateOf(0f) }
    var hata by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!indiriliyor) onKapat() },
        title = { Text("Yeni surum hazir") },
        text = {
            Column {
                Text("Surum ${bilgi.versionName} yayinlandi.")
                if (bilgi.notes.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(bilgi.notes, style = MaterialTheme.typography.bodyMedium)
                }
                if (indiriliyor) {
                    Spacer(Modifier.height(16.dp))
                    if (ilerleme >= 0f) {
                        LinearProgressIndicator(
                            progress = { ilerleme },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Indiriliyor... %${(ilerleme * 100).toInt()}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                hata?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !indiriliyor,
                onClick = {
                    scope.launch {
                        indiriliyor = true
                        hata = null
                        ilerleme = 0f
                        val sonuc = Updater.download(ctx, bilgi) { y ->
                            ilerleme = if (y >= 0) y / 100f else -1f
                        }
                        indiriliyor = false
                        sonuc.onSuccess { dosya ->
                            if (!Updater.canInstall(ctx)) {
                                Updater.openInstallPermission(ctx)
                                hata = "Once \"bilinmeyen kaynaklardan kurulum\" iznini ver, " +
                                    "sonra tekrar dene."
                            } else {
                                Updater.install(ctx, dosya)
                            }
                        }.onFailure { e ->
                            hata = "Indirilemedi: ${e.message}"
                        }
                    }
                }
            ) { Text(if (indiriliyor) "Indiriliyor..." else "Indir ve kur") }
        },
        dismissButton = {
            TextButton(enabled = !indiriliyor, onClick = { (onAtla ?: onKapat)() }) {
                Text("Sonra")
            }
        }
    )
}
