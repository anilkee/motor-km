package com.seferdefteri.app.sync

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

sealed interface GoogleSonuc {
    data class Tamam(val idToken: String) : GoogleSonuc
    data object Vazgecildi : GoogleSonuc
    data class Hata(val mesaj: String) : GoogleSonuc
}

/**
 * Telefonda Google hesabi sectirir ve sunucuya gosterilecek kimlik belgesini
 * (id token) alir. Dogrulamayi sunucu yapar.
 *
 * Kullanilan kimlik SUNUCU (web) istemci kimligidir; Google belgeyi bunun icin
 * uretir, sunucu da ayni kimlige bakarak dogrular. Android istemci kimligi
 * projede tanimli olmali ama koda yazilmaz.
 */
object GoogleGiris {

    private const val SUNUCU_ISTEMCI_ID =
        "763613803259-hfj9qp0igga262prb62mtdedpo342ii1.apps.googleusercontent.com"

    /** Google hesap secme ekrani bu sureden uzun surerse takilmis sayilir. */
    private const val ZAMAN_ASIMI_MS = 90_000L

    suspend fun kimlikAl(context: Context): GoogleSonuc {
        // Credential Manager hesap secme ekranini gostermek icin ACTIVITY ister.
        // Compose'un verdigi context sarmalanmis olabiliyor; aciyoruz.
        val aktivite = context.aktiviteBul()
            ?: return GoogleSonuc.Hata("Google ekrani acilamadi (aktivite bulunamadi)")

        return try {
            withTimeout(ZAMAN_ASIMI_MS) {
                val secenek = GetGoogleIdOption.Builder()
                    // false: daha once bu uygulamaya baglanmamis hesaplar da
                    // listelensin, ilk giriste bos ekran cikmasin.
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(SUNUCU_ISTEMCI_ID)
                    .setAutoSelectEnabled(false)
                    .build()

                val istek = GetCredentialRequest.Builder()
                    .addCredentialOption(secenek)
                    .build()

                val sonuc = CredentialManager.create(aktivite).getCredential(aktivite, istek)
                val kimlik = sonuc.credential

                if (kimlik is CustomCredential &&
                    kimlik.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    GoogleSonuc.Tamam(GoogleIdTokenCredential.createFrom(kimlik.data).idToken)
                } else {
                    GoogleSonuc.Hata("Beklenmeyen kimlik turu: " + kimlik.type)
                }
            }
        } catch (e: TimeoutCancellationException) {
            GoogleSonuc.Hata(
                "Google yanit vermedi. Google Play Hizmetleri guncel mi diye bak, " +
                    "olmazsa kullanici adi ve sifreyle gir."
            )
        } catch (e: GetCredentialCancellationException) {
            GoogleSonuc.Vazgecildi
        } catch (e: NoCredentialException) {
            GoogleSonuc.Hata(
                "Google hesabi bulunamadi. Telefonun ayarlarindan bir Google hesabi " +
                    "ekli mi kontrol et."
            )
        } catch (e: GetCredentialException) {
            // Tur adini da yaziyoruz: sorun cikarsa neyi arayacagimizi bilelim.
            GoogleSonuc.Hata(
                "Google girisi olmadi (${e.javaClass.simpleName}): " +
                    (e.message ?: "ayrinti yok")
            )
        } catch (e: Exception) {
            GoogleSonuc.Hata(
                "Google girisi olmadi (${e.javaClass.simpleName}): " +
                    (e.message ?: "ayrinti yok")
            )
        }
    }

    /** Sarmalanmis context'in icinden Activity'yi cikarir. */
    private fun Context.aktiviteBul(): Activity? {
        var c: Context? = this
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}
