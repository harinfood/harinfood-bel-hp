package com.contoh.doorbell

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/** Layanan latar depan: menjaga koneksi iTag tetap hidup dan memutar bel saat tombol ditekan. */
class ItagService : Service() {

    interface Pendengar {
        fun onStatus(status: Int, pesan: String)
        fun onBelDitekan()
    }

    companion object {
        const val AKSI_SAMBUNG = "com.contoh.doorbell.SAMBUNG"
        const val AKSI_BUNYIKAN = "com.contoh.doorbell.BUNYIKAN"
        const val AKSI_PUTUS = "com.contoh.doorbell.PUTUS"
        const val EXTRA_MAC = "mac"

        private const val KANAL = "doorbell_itag"
        private const val ID_NOTIFIKASI = 1

        var pendengar: Pendengar? = null
        var infoTerakhir: String = "Belum ada info. Hubungkan iTag dahulu."
    }

    private lateinit var koneksi: ItagConnection
    private lateinit var pemutar: PemutarBel
    private var terakhirTekan = 0L
    private var pesanTerakhir = "Menyiapkan..."
    private var statusTerakhir = ItagConnection.TERPUTUS

    override fun onCreate() {
        super.onCreate()
        buatKanal()
        pemutar = PemutarBel(this)
        koneksi = ItagConnection(this, object : ItagConnection.Listener {
            override fun onStatus(status: Int, pesan: String) {
                statusTerakhir = status
                pesanTerakhir = pesan
                perbaruiNotifikasi(pesan)
                pendengar?.onStatus(status, pesan)
            }

            override fun onTombolDitekan() {
                // Tombol kadang memicu dua notifikasi; abaikan yang datang terlalu dekat
                val sekarang = System.currentTimeMillis()
                if (sekarang - terakhirTekan < 2500L) return
                terakhirTekan = sekarang
                pemutar.mainkan()
                pendengar?.onBelDitekan()
            }

            override fun onInfo(info: String) {
                infoTerakhir = info
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat(buatNotifikasi(pesanTerakhir))

        when (intent?.action) {
            AKSI_SAMBUNG -> {
                val mac = intent?.getStringExtra(EXTRA_MAC) ?: Pengaturan.itagTersimpan(this)?.mac
                if (mac != null) koneksi.sambung(mac)
            }
            AKSI_BUNYIKAN -> {
                if (!koneksi.bunyikan()) {
                    pendengar?.onStatus(statusTerakhir, "iTag belum siap, tunggu sebentar")
                }
            }
            AKSI_PUTUS -> {
                koneksi.tutup()
                Pengaturan.hapusItag(this)
                statusTerakhir = ItagConnection.TERPUTUS
                pendengar?.onStatus(ItagConnection.TERPUTUS, "iTag diputuskan")
                stopForeground(true)
                stopSelf()
            }
            else -> {
                // Dipanggil ulang oleh sistem setelah aplikasi dihentikan: sambung kembali
                Pengaturan.itagTersimpan(this)?.let { koneksi.sambung(it.mac) }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        koneksi.tutup()
        pemutar.lepas()
        super.onDestroy()
    }

    private fun startForegroundCompat(notifikasi: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(ID_NOTIFIKASI, notifikasi, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(ID_NOTIFIKASI, notifikasi)
        }
    }

    private fun buatKanal() {
        if (Build.VERSION.SDK_INT >= 26) {
            val kanal = NotificationChannel(KANAL, "Harinfood Bel HP", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(kanal)
        }
    }

    private fun perbaruiNotifikasi(teks: String) {
        getSystemService(NotificationManager::class.java).notify(ID_NOTIFIKASI, buatNotifikasi(teks))
    }

    private fun buatNotifikasi(teks: String): Notification {
        val buka = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, KANAL)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Harinfood Bel HP")
            .setContentText(teks)
            .setSmallIcon(R.drawable.ic_lonceng)
            .setContentIntent(buka)
            .setOngoing(true)
            .build()
    }
}
