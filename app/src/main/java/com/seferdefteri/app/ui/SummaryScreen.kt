package com.seferdefteri.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seferdefteri.app.data.Repo

private enum class Aralik(val etiket: String) {
    BUGUN("Bugun"),
    HAFTA("Bu hafta"),
    AY("Bu ay"),
    TUMU("Tumu")
}

@Composable
fun SummaryScreen(
    repo: Repo,
    modifier: Modifier = Modifier
) {
    var aralik by remember { mutableStateOf(Aralik.AY) }

    val bas = when (aralik) {
        Aralik.BUGUN -> gunBasi()
        Aralik.HAFTA -> haftaBasi()
        Aralik.AY -> ayBasi()
        Aralik.TUMU -> 0L
    }
    val son = if (aralik == Aralik.TUMU) Long.MAX_VALUE else gunSonu()

    val ozet = remember(aralik) { repo.summary(bas, son) }
    val tuketim = remember(aralik) { repo.consumption(bas, son) }
    // Tarih araligindan bagimsiz: hic dolum girilmis mi?
    val hicDolumVar = remember(aralik) { repo.fuels(1).isNotEmpty() }
    val paketSayisi = remember(aralik) { repo.deliveryCountBetween(bas, son) }
    val vardiyalar = remember(aralik) { repo.shiftsBetween(bas, son) }

    // Gunluk kirilim
    val gunluk = remember(aralik) {
        vardiyalar.groupBy { gunBasi(it.startTime) }
            .map { (gun, liste) ->
                Triple(gun, liste.sumOf { it.km }, liste.sumOf { it.durationMs })
            }
            .sortedByDescending { it.first }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // ------------------------------------------------ aralik secici
        item {
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Aralik.entries.forEach { a ->
                    FilterChip(
                        selected = aralik == a,
                        onClick = { aralik = a },
                        label = { Text(a.etiket, fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = androidx.compose.ui.graphics.Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ------------------------------------------------ ana rakamlar
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        kmDeger(ozet.km),
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "kilometre  -  ${ozet.shiftCount} vardiya  -  ${sure(ozet.activeMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (paketSayisi > 0) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatBox(
                                "Paket", "$paketSayisi",
                                renk = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            StatBox(
                                "Paket basina", sayi(ozet.km / paketSayisi, 1) + " km",
                                renk = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            StatBox(
                                "Saatte",
                                if (ozet.activeMs > 0)
                                    sayi(paketSayisi / (ozet.activeMs / 3_600_000.0), 1) + " paket"
                                else "-",
                                renk = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // ------------------------------------------------ tuketim
        item {
            SectionCard("Yakit tuketimi") {
                if (tuketim.gecerli) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatBox(
                            "100 km'de",
                            sayi(tuketim.litersPer100Km, 2) + " L",
                            modifier = Modifier.weight(1f)
                        )
                        StatBox(
                            "1 litre ile",
                            sayi(tuketim.kmPerLiter, 2) + " km",
                            modifier = Modifier.weight(1f)
                        )
                        StatBox(
                            "1 km maliyet",
                            sayi(tuketim.tryPerKm, 2) + " TL",
                            renk = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))

                    SatirDeger("100 km maliyeti", lira(tuketim.tryPerKm * 100), vurgula = true)
                    SatirDeger("Olculen mesafe", kmDeger(tuketim.km) + " km")
                    SatirDeger("Bu mesafede yakilan", litre(tuketim.liters))
                    SatirDeger("Kac dolumdan hesaplandi", "${tuketim.olcumSayisi}")
                    if (paketSayisi > 0) {
                        SatirDeger(
                            "Paket basina yakit",
                            lira(ozet.km / paketSayisi * tuketim.tryPerKm)
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tuketim, ardisik iki dolum ARASINDA gidilen yola gore hesaplanir. " +
                            "Dogru cikmasi icin her seferinde depoyu agzina kadar doldur.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    Text(
                        if (!hicDolumVar) {
                            "Once Yakit sekmesinden ilk dolumunu gir."
                        } else {
                            "Ilk dolum sadece baslangic isareti olarak kullanilir; " +
                                "motorda o an zaten olan yakit hesaba katilmaz. " +
                                "Ikinci dolumundan sonra tuketim burada cikacak."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Nedeni: aldigin yakitin hepsini o gun yakmiyorsun, bir kismi " +
                            "depoda kaliyor. Dogru sonuc ancak iki dolum arasindaki " +
                            "mesafeye bakarak cikar.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        // ------------------------------------------------ donem harcamasi
        item {
            SectionCard("Bu donemde") {
                // Yakit satirlari ancak gercekten dolum girildiyse gosterilir;
                // motorda zaten olan yakit hicbir zaman hesaba katilmaz.
                if (ozet.liters > 0.0) {
                    SatirDeger("Alinan yakit", litre(ozet.liters))
                    SatirDeger("Yakit harcamasi", lira(ozet.costTry), vurgula = true)
                    SatirDeger(
                        "Ortalama litre fiyati",
                        sayi(ozet.costTry / ozet.liters, 2) + " TL"
                    )
                }
                SatirDeger("Gidilen yol", kmDeger(ozet.km) + " km")
                SatirDeger("Direksiyon basinda", sure(ozet.activeMs))
                SatirDeger("Ortalama hiz", sayi(ozet.avgKmh, 0) + " km/s")
            }
        }

        // ------------------------------------------------ gunluk kirilim
        item {
            Text(
                "Gun gun",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        if (gunluk.isEmpty()) {
            item {
                BosDurum(
                    Icons.Filled.QueryStats,
                    "Bu aralikta kayit yok",
                    "Baska bir zaman araligi sec."
                )
            }
        } else {
            items(gunluk, key = { it.first }) { (gun, kmToplam, sureToplam) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(tarihUzun(gun), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                sure(sureToplam),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                kmDeger(kmToplam) + " km",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (tuketim.gecerli) {
                                Text(
                                    "~" + lira(kmToplam * tuketim.tryPerKm) + " yakit",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
