package com.btgamepad.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.btgamepad.hid.HidDescriptor
import com.btgamepad.input.RemappingManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * BluetoothHidService
 *
 * A foreground [Service] that:
 * 1. Registers the device as a Bluetooth HID gamepad using [BluetoothHidDevice].
 * 2. Exposes a [RemappingManager] so the UI layer can push input changes.
 * 3. Sends HID reports to the connected host whenever the report changes.
 */
@SuppressLint("MissingPermission")
class BluetoothHidService : Service() {

    companion object {
        private const val CHANNEL_ID = "bt_hid_service"
        private const val NOTIFICATION_ID = 1
        const val ACTION_STATE_CHANGED = "com.btgamepad.ACTION_STATE_CHANGED"
        const val EXTRA_STATE = "EXTRA_STATE"
    }

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    val remappingManager = RemappingManager()

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothHidService = this@BluetoothHidService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Waiting for host..."))
        
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        adapter?.getProfileProxy(this, profileListener, BluetoothProfile.HID_DEVICE)
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as BluetoothHidDevice
                val sdp = BluetoothHidDeviceAppSdpSettings(
                    HidDescriptor.SDP_NAME,
                    HidDescriptor.SDP_DESCRIPTION,
                    HidDescriptor.SDP_PROVIDER,
                    BluetoothHidDevice.SUBCLASS2_GAMEPAD,
                    HidDescriptor.DESCRIPTOR
                )
                hidDevice?.registerApp(sdp, null, null, executor, hidCallback)
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            hidDevice = null
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                connectedDevice = device
                updateNotification("Connected: ${device.name}")
                broadcastState(state)
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                connectedDevice = null
                updateNotification("Waiting for host...")
                broadcastState(state)
            }
        }
    }

    fun onVirtualButton(tag: String, isDown: Boolean) {
        remappingManager.onVirtualButton(tag, isDown)
        sendReport()
    }

    private fun sendReport() {
        val device = connectedDevice ?: return
        hidDevice?.sendReport(device, 1, remappingManager.currentReport())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bluetooth Gamepad",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shows BT HID connection status" }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = Intent(this, BluetoothHidService::class.java).apply {
            action = "STOP"
        }
        val stopPi = PendingIntent.getService(this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BT Virtual Gamepad")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_preferences)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPi)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    // ── State broadcast ───────────────────────────────────────────────────────

    private fun broadcastState(state: Int) {
        val intent = Intent(ACTION_STATE_CHANGED).putExtra(EXTRA_STATE, state)
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        hidDevice?.unregisterApp()
        super.onDestroy()
    }
}
