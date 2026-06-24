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
 *  1. Registers the device as a Bluetooth HID gamepad using [BluetoothHidDevice].
 *  2. Exposes a [RemappingManager] so the UI layer can push input changes.
 *  3. Sends HID reports to the connected host whenever the report changes.
 *
 * Lifecycle:
 *   Start  → startForeground → registerApp → wait for host to connect
 *   Input  → remappingManager.onXxx() → sendReport()
 *   Stop   → unregisterApp → stopSelf
 */
@SuppressLint("MissingPermission")   // Permissions checked in Activity before starting
class BluetoothHidService : Service() {

    companion object {
        private const val TAG = "BTHidService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "bt_hid_channel"

        const val ACTION_STATE_CHANGED  = "com.btgamepad.STATE_CHANGED"
        const val EXTRA_STATE           = "state"

        /** Service states broadcast via LocalBroadcastManager. */
        const val STATE_IDLE        = 0
        const val STATE_REGISTERED  = 1
        const val STATE_CONNECTED   = 2
        const val STATE_DISCONNECTED = 3
        const val STATE_ERROR       = 4
    }

    // ── Binder ────────────────────────────────────────────────────────────────

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothHidService = this@BluetoothHidService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    // ── Bluetooth objects ─────────────────────────────────────────────────────

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var hidDevice: BluetoothHidDevice? = null
    private var connectedHost: BluetoothDevice? = null

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    // ── Public input manager ──────────────────────────────────────────────────

    val remappingManager = RemappingManager()

    // ── Service lifecycle ─────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = btManager.adapter
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Initialising…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        registerHidApp()
        return START_STICKY
    }

    override fun onDestroy() {
        hidDevice?.unregisterApp()
        executor.shutdown()
        super.onDestroy()
    }

    // ── HID profile proxy ─────────────────────────────────────────────────────

    private val hidCallback = object : BluetoothHidDevice.Callback() {

        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            if (registered) {
                Log.i(TAG, "HID app registered")
                updateNotification("Registered — waiting for host…")
                broadcastState(STATE_REGISTERED)
            } else {
                Log.w(TAG, "HID app unregistered")
                broadcastState(STATE_IDLE)
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedHost = device
                    Log.i(TAG, "Host connected: ${device.address}")
                    updateNotification("Connected: ${device.name ?: device.address}")
                    broadcastState(STATE_CONNECTED)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (connectedHost?.address == device.address) connectedHost = null
                    Log.i(TAG, "Host disconnected: ${device.address}")
                    updateNotification("Disconnected — waiting for host…")
                    broadcastState(STATE_DISCONNECTED)
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            // Host is requesting the current report (e.g. after reconnect)
            hidDevice?.replyReport(device, type, id, remappingManager.currentReport())
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            hidDevice?.reportError(device, BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ)
        }

        override fun onSetProtocol(device: BluetoothDevice, protocol: Byte) {
            Log.d(TAG, "Set protocol: $protocol")
        }

        override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) {
            Log.d(TAG, "Interrupt data from host (len=${data.size})")
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as BluetoothHidDevice
                val sdp = BluetoothHidDeviceAppSdpSettings(
                    HidDescriptor.SDP_NAME,
                    HidDescriptor.SDP_DESCRIPTION,
                    HidDescriptor.SDP_PROVIDER,
                    BluetoothHidDevice.SUBCLASS1_GAMEPAD,
                    HidDescriptor.DESCRIPTOR
                )
                hidDevice?.registerApp(sdp, null, null, executor, hidCallback)
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                broadcastState(STATE_IDLE)
            }
        }
    }

    private fun registerHidApp() {
        val ok = bluetoothAdapter.getProfileProxy(this, profileListener, BluetoothProfile.HID_DEVICE)
        if (!ok) {
            Log.e(TAG, "Failed to get HID_DEVICE profile proxy")
            broadcastState(STATE_ERROR)
        }
    }

    // ── Report sending ────────────────────────────────────────────────────────

    /**
     * Send the current HID report to the connected host.
     * Call this from the UI thread after any input change.
     * No-op if there is no connected host.
     */
    fun sendReport() {
        val host   = connectedHost ?: return
        val device = hidDevice     ?: return
        val report = remappingManager.currentReport()
        val sent = device.sendReport(host, 0, report)
        if (!sent) Log.w(TAG, "sendReport returned false")
    }

    /**
     * Convenience: apply a remapping action then immediately send.
     * Runs on the calling thread (UI thread is fine).
     */
    fun onVirtualButton(tag: String, isDown: Boolean) {
        remappingManager.onVirtualButton(tag, isDown)
        sendReport()
    }

    fun onVirtualAxis(tag: String, value: Float) {
        remappingManager.onVirtualAxis(tag, value)
        sendReport()
    }

    fun onPhysicalKey(keyCode: Int, isDown: Boolean): Boolean {
        val consumed = remappingManager.onPhysicalKey(keyCode, isDown)
        if (consumed) sendReport()
        return consumed
    }

    fun onPhysicalAxis(event: android.view.MotionEvent) {
        remappingManager.onPhysicalAxis(event)
        sendReport()
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bluetooth Gamepad",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shows BT HID connection status" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
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
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    // ── State broadcast ───────────────────────────────────────────────────────

    private fun broadcastState(state: Int) {
        val intent = Intent(ACTION_STATE_CHANGED).putExtra(EXTRA_STATE, state)
        sendBroadcast(intent)
    }
}
