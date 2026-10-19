package com.contoh.doorbell

import android.content.Context
import android.net.Uri
import java.io.File

object Pengaturan {
    private const val NAMA_FILE = "doorbell_pengaturan"
    private const val KUNCI_MAC = "mac_itag"
    private const val KUNCI_NAMA_ITAG = "nama_itag"
    private const val KUNCI_INGIN_TERSAMBUNG = "ingin_tersambung"
    private const val KUNCI_NAMA_SUARA = "nama_suara"
    private const val NAMA_SALINAN = "bel_kustom.mp3"

    data class Itag(val mac: String, val nama: String)

    private fun prefs(context: Context) =
        context.getSharedPreferences(NAMA_FILE, Context.MODE_PRIVATE)

    fun itagTersimpan(context: Context): Itag? {
        val p = prefs(context)
        val mac = p.getString(KUNCI_MAC, null) ?: return null
        return Itag(mac, p.getString(KUNCI_NAMA_ITAG, "iTag") ?: "iTag")
    }

    fun simpanItag(context: Context, mac: String, nama: String) {
        prefs(context).edit()
            .putString(KUNCI_MAC, mac)
            .putString(KUNCI_NAMA_ITAG, nama)
            .apply()
    }

    fun hapusItag(context: Context) {
        prefs(context).edit()
            .remove(KUNCI_MAC)
            .remove(KUNCI_NAMA_ITAG)
            .apply()
    }

    /** False jika pengguna menekan Putuskan, agar aplikasi tidak menyambung otomatis. */
    fun inginTersambung(context: Context): Boolean =
        prefs(context).getBoolean(KUNCI_INGIN_TERSAMBUNG, false)

    fun setInginTersambung(context: Context, nilai: Boolean) {
        prefs(context).edit().putBoolean(KUNCI_INGIN_TERSAMBUNG, nilai).apply()
    }

    fun fileSuara(context: Context): File = File(context.filesDir, NAMA_SALINAN)

    fun adaSuaraKustom(context: Context): Boolean {
        val berkas = fileSuara(context)
        return berkas.exists() && berkas.length() > 0
    }

    fun namaSuara(context: Context): String? =
        prefs(context).getString(KUNCI_NAMA_SUARA, null)

    /** Menyalin mp3 pilihan ke penyimpanan internal agar tetap bisa diputar. */
    fun salinSuara(context: Context, uri: Uri, nama: String?): Boolean {
        return try {
            val masuk = context.contentResolver.openInputStream(uri) ?: return false
            masuk.use { input ->
                fileSuara(context).outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            prefs(context).edit().putString(KUNCI_NAMA_SUARA, nama ?: "file mp3").apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun hapusSuara(context: Context) {
        fileSuara(context).delete()
        prefs(context).edit().remove(KUNCI_NAMA_SUARA).apply()
    }
}
