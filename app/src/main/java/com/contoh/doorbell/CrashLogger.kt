package com.contoh.doorbell

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

/** Menyimpan jejak error terakhir ke file, lalu menampilkannya saat aplikasi dibuka lagi. */
object CrashLogger {
    private const val NAMA_BERKAS = "crash_terakhir.txt"

    fun pasang(context: Context) {
        val app = context.applicationContext
        val handlerAsli = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                File(app.filesDir, NAMA_BERKAS).writeText("Waktu: ${Date()}\n\n$sw")
            } catch (e: Exception) {
                // Abaikan agar penanganan error bawaan tetap berjalan
            }
            handlerAsli?.uncaughtException(thread, error)
        }
    }

    fun ambil(context: Context): String? {
        val berkas = File(context.filesDir, NAMA_BERKAS)
        return if (berkas.exists()) berkas.readText() else null
    }

    fun hapus(context: Context) {
        File(context.filesDir, NAMA_BERKAS).delete()
    }
}
