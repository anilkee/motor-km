package com.kurye.takip.sync

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

sealed interface GoogleSonuc {
    data class Tamam(val idToken: String) : GoogleSonuc
    data object Vazgecildi : GoogleSonuc
    data class Hata(val mesaj: String) : GoogleSonuc
}

/**
 * Telefonda Google hesabi sectirir ve sunucuya gosterilecek kimlik belgesini
 * (id token) alir. Dogrulamayi sunucu yapar - burada kimseye guvenmiyoruz.
 *
 * Not: kullanilan kimlik SUNUCU (web) istemci kimligidir. Google, belgeyi
 * bunun icin uretir; sunucu da "bu belge benim uygulamama mi ait" diye ayni
 * kimlige bakarak dogrular. Android istemci kimligi ayrica projede tanimli
 * olmali ama koda yazilmaz.
 */
object GoogleGiris {

    private const val SUNUCU_ISTEMCI_ID =
        "763613803259-hfj9qp0igga262prb62mtdedpo342ii1.apps.googleusercontent.com"

    suspend fun kimlikAl(context: Context): GoogleSonuc {
        return try {
            val secenek = GetGoogleIdOption.Builder()
                // false: telefonda daha once bu uygulamaya baglanmamis
                // hesaplar da listelensin, ilk giriste bos ekran cikmasin.
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(SUNUCU_ISTEMCI_ID)
                .setAutoSelectEnabled(false)
                .build()

            val istek = GetCredentialRequest.Builder()
                .addCredentialOption(secenek)
                .build()

            val sonuc = CredentialManager.create(context).getCredential(context, istek)
            val kimlik = sonuc.credential

            if (kimlik is CustomCredential &&
                kimlik.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val google = GoogleIdTokenCredential.createFrom(kimlik.data)
                GoogleSonuc.Tamam(google.idToken)
            } else {
                GoogleSonuc.Hata("Beklenmeyen kimlik turu")
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleSonuc.Vazgecildi
        } catch (e: NoCredentialException) {
            GoogleSonuc.Hata("Telefonda Google hesabi bulunamadi. Once ayarlardan hesap ekle.")
        } catch (e: GetCredentialException) {
            GoogleSonuc.Hata(e.message ?: "Google girisi yapilamadi")
        } catch (e: Exception) {
            GoogleSonuc.Hata(e.message ?: "Google girisi yapilamadi")
        }
    }
}
