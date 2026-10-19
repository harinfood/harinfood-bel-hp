package com.contoh.doorbell

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

/** Memutar mp3 pilihan, atau nada bel bawaan jika belum memilih. */
class PemutarBel(private val context: Context) {

    private var pemutar: MediaPlayer? = null

    fun mainkan() {
        pemutar?.release()
        pemutar = null

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

    fun lepas() {
        pemutar?.release()
        pemutar = null
    }
}
