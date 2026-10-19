package com.contoh.doorbell

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

/** Pemutar tunggal, sehingga musik bisa dihentikan dari layanan maupun tampilan. */
object PemutarBel {

    private var pemutar: MediaPlayer? = null

    fun mainkan(context: Context) {
        hentikan()
        val p = MediaPlayer()
        try {
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )

            if (Pengaturan.adaSuaraKustom(context)) {
                p.setDataSource(Pengaturan.fileSuara(context).absolutePath)
            } else {
                context.resources.openRawResourceFd(R.raw.bel_default).use { fd ->
                    p.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                }
            }

            p.setOnCompletionListener {
                it.release()
                if (pemutar === it) pemutar = null
            }
            p.prepare()
            p.start()
            pemutar = p
        } catch (e: Exception) {
            p.release()
        }
    }

    /** Menghentikan musik yang sedang berbunyi, termasuk bel dari iTag. */
    fun hentikan() {
        val p = pemutar ?: return
        pemutar = null
        try {
            p.stop()
        } catch (e: IllegalStateException) {
            // Sudah berhenti, abaikan
        }
        p.release()
    }

    fun lepas() {
        hentikan()
    }
}
