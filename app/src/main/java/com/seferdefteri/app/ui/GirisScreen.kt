package com.seferdefteri.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.drawscope.scale
import com.seferdefteri.app.sync.GoogleGiris
import com.seferdefteri.app.sync.GoogleTarayici
import com.seferdefteri.app.sync.GoogleSonuc
import com.seferdefteri.app.sync.Hesap
import com.seferdefteri.app.sync.HesapSonuc
import kotlinx.coroutines.launch

/**
 * Giris / kayit ekrani.
 *
 * Giris yapinca sunucudan alinan cihaz anahtari telefonda kalir; kullanici
 * "Cikis yap" demedikce bir daha sifre sorulmaz.
 */
@Composable
fun GirisScreen(
    onGirisTamam: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var kayitModu by remember { mutableStateOf(false) }
    var kimlik by remember { mutableStateOf("") }
    var eposta by remember { mutableStateOf("") }
    var sifre by remember { mutableStateOf("") }
    var sifreGorunsun by remember { mutableStateOf(false) }
    var calisiyor by remember { mutableStateOf(false) }
    var hata by remember { mutableStateOf<String?>(null) }
    var bilgi by remember { mutableStateOf<String?>(null) }

    /**
     * Once telefondaki hesap secici (Credential Manager) denenir - daha hizli.
     * O calismazsa tarayiciya dusulur: tarayici yolu Google Clouddaki Android
     * istemcisine hic bakmaz, sadece web istemcisini kullanir, bu yuzden
     * Credential Managerin takildigi durumlarda da calisir.
     */
    fun googleIleGir() {
        hata = null
        bilgi = null
        scope.launch {
            calisiyor = true
            when (val g = GoogleGiris.kimlikAl(ctx)) {
                is GoogleSonuc.Tamam -> {
                    when (val s = Hesap.googleIleGir(ctx, g.idToken)) {
                        is HesapSonuc.Tamam -> onGirisTamam()
                        is HesapSonuc.Hata -> hata = s.mesaj
                    }
                    calisiyor = false
                }
                GoogleSonuc.Vazgecildi -> calisiyor = false
                is GoogleSonuc.Hata -> {
                    // Telefondaki secici olmadi; tarayicidan devam et.
                    bilgi = "Tarayici aciliyor..."
                    if (!GoogleTarayici.baslat(ctx)) {
                        bilgi = null
                        hata = g.mesaj
                    }
                    calisiyor = false
                }
            }
        }
    }

    fun gonder() {
        hata = null
        bilgi = null
        scope.launch {
            calisiyor = true
            val sonuc = if (kayitModu) {
                Hesap.kayitOl(ctx, kimlik, eposta, sifre)
            } else {
                Hesap.girisYap(ctx, kimlik, sifre)
            }
            calisiyor = false
            when (sonuc) {
                is HesapSonuc.Tamam -> {
                    if (sonuc.sifreDegistir) {
                        bilgi = "Gecici sifreyle girdin. Panelden kendi sifreni belirle."
                    }
                    onGirisTamam()
                }
                is HesapSonuc.Hata -> hata = sonuc.mesaj
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0E7C43), Color(0xFF06301A)))
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Sefer Defteri",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Kilometren, yakitin, paketlerin",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.75f)
            )

            Spacer(Modifier.height(28.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(22.dp)) {
                    Text(
                        if (kayitModu) "Hesap ac" else "Giris yap",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = kimlik,
                        onValueChange = { kimlik = it; hata = null },
                        label = { Text(if (kayitModu) "Kullanici adi" else "Kullanici adi veya e-posta") },
                        singleLine = true,
                        enabled = !calisiyor,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (kayitModu) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = eposta,
                            onValueChange = { eposta = it; hata = null },
                            label = { Text("E-posta") },
                            singleLine = true,
                            enabled = !calisiyor,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Sifreni unutursan ulasabilmemiz icin.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = 4.dp, top = 3.dp)
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = sifre,
                        onValueChange = { sifre = it; hata = null },
                        label = { Text("Sifre") },
                        singleLine = true,
                        enabled = !calisiyor,
                        visualTransformation = if (sifreGorunsun) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        trailingIcon = {
                            IconButton(onClick = { sifreGorunsun = !sifreGorunsun }) {
                                Icon(
                                    if (sifreGorunsun) Icons.Filled.VisibilityOff
                                    else Icons.Filled.Visibility,
                                    contentDescription = if (sifreGorunsun) "Gizle" else "Goster"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (kayitModu) {
                        Text(
                            "En az 8 karakter.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = 4.dp, top = 3.dp)
                        )
                    }

                    hata?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    bilgi?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { gonder() },
                        enabled = !calisiyor && kimlik.isNotBlank() && sifre.isNotBlank() &&
                            (!kayitModu || eposta.isNotBlank()),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        if (calisiyor) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Text(
                                if (kayitModu) "Hesabi ac" else "Gir",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // ---------------------------------------- Google ile giris
                    Spacer(Modifier.height(18.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(Modifier.weight(1f))
                        Text(
                            "veya",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        HorizontalDivider(Modifier.weight(1f))
                    }

                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = { googleIleGir() },
                        enabled = !calisiyor,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        GoogleLogosu()
                        Spacer(Modifier.size(12.dp))
                        Text(
                            "Google ile devam et",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (kayitModu) "Zaten hesabin var mi?" else "Hesabin yok mu?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { kayitModu = !kayitModu; hata = null },
                            enabled = !calisiyor
                        ) {
                            Text(if (kayitModu) "Giris yap" else "Kayit ol")
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Devam ederek kullanim kosullarini ve gizlilik metnini kabul etmis olursun.\n" +
                    "Konumun sadece vardiya actiginda kaydedilir.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.65f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** Google'in dort renkli "G" harfi. */
@Composable
private fun GoogleLogosu() {
    Canvas(modifier = Modifier.size(20.dp)) {
        val b = size.width
        val yol = { d: String -> androidx.compose.ui.graphics.vector.PathParser().parsePathString(d).toPath() }
        val olcek = b / 48f
        scale(olcek, olcek, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(yol("M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"), Color(0xFFEA4335))
            drawPath(yol("M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"), Color(0xFF4285F4))
            drawPath(yol("M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"), Color(0xFFFBBC05))
            drawPath(yol("M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"), Color(0xFF34A853))
        }
    }
}
