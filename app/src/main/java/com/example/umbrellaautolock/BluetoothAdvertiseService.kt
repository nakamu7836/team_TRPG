package com.example.umbrellaautolock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.os.ParcelUuid
import java.util.UUID

class BluetoothAdvertiseService : Service() {

    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // ESP32側と合わせた共通のサービスUUID
    private val TARGET_UUID = "19B10000-E8F2-537E-4F6C-D104768A1214"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // CPUを強制的に起こし続けるWakeLockを取得
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "UmbrellaAutoLock::BleWakeLock")
        wakeLock?.acquire()

        val channelId = "UmbrellaServiceChannel"
        val channel = NotificationChannel(
            channelId,
            "傘のオートロック発信サービス",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle("傘のオートロック")
            .setContentText("バックグラウンドで電波を強力に発信中...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()

        startForeground(1, notification)
        startAdvertising()

        return START_STICKY
    }

    private fun startAdvertising() {
        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter = bluetoothManager?.adapter
        bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser

        if (bluetoothLeAdvertiser == null) return

        val settings = AdvertiseSettings.Builder()
            // バックグラウンドでも検知されやすいようにモードを強化
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        val pUuid = ParcelUuid(UUID.fromString(TARGET_UUID))
        val data = AdvertiseData.Builder()
            .addServiceUuid(pUuid)
            .setIncludeDeviceName(false)
            .build()

        advertiseCallback = object : AdvertiseCallback() {}

        try {
            bluetoothLeAdvertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
        // サービス終了時にWakeLockを解放
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}