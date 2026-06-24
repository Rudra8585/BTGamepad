package com.btgamepad

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.btgamepad.service.BluetoothHidService
import com.btgamepad.ui.VirtualJoystickView

class MainActivity : AppCompatActivity() {

    // ── Service binding ───────────────────────────────────────────────────────

    private var hidService: BluetoothHidService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            hidService = (binder as BluetoothHidService.LocalBinder).getService()
            serviceBound = true
            statusText.text = "BT HID service connected — waiting for host…"
        }
        override fun onServiceDisconnected(name: ComponentName) {
            hidService = null
            serviceBound = false
        }
    }

    // ── Views ─────────────────────────────────────────────────────────────────

    private lateinit var statusText: TextView
    private lateinit var leftStick: VirtualJoystickView
    private lateinit var rightStick: VirtualJoystickView

    // ── Permission launcher ───────────────────────────────────────────────────

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) startHidService()
        else Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_LONG).show()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.tv_status)
        leftStick  = findViewById(R.id.joystick_left)
        rightStick = findViewById(R.id.joystick_right)

        leftStick.tag  = "ls"
        rightStick.tag = "rs"

        // Wire virtual joysticks
        val stickListener = { axisX: String, axisY: String, x: Float, y: Float ->
            hidService?.onVirtualAxis(axisX, x)
            hidService?.onVirtualAxis(axisY, y)
            Unit
        }
        leftStick.onMoveListener  = stickListener
        rightStick.onMoveListener = stickListener

        // Wire virtual face buttons (A B X Y etc.)
        wireFaceButton(R.id.btn_a,      "btn_a")
        wireFaceButton(R.id.btn_b,      "btn_b")
        wireFaceButton(R.id.btn_x,      "btn_x")
        wireFaceButton(R.id.btn_y,      "btn_y")
        wireFaceButton(R.id.btn_lb,     "btn_lb")
        wireFaceButton(R.id.btn_rb,     "btn_rb")
        wireFaceButton(R.id.btn_lt,     "btn_lt")
        wireFaceButton(R.id.btn_rt,     "btn_rt")
        wireFaceButton(R.id.btn_start,  "btn_start")
        wireFaceButton(R.id.btn_select, "btn_select")
        wireDPad(R.id.btn_dpad_up,    "dpad_up")
        wireDPad(R.id.btn_dpad_down,  "dpad_down")
        wireDPad(R.id.btn_dpad_left,  "dpad_left")
        wireDPad(R.id.btn_dpad_right, "dpad_right")

        // Remap settings shortcut
        findViewById<Button>(R.id.btn_remap)?.setOnClickListener {
            startActivity(Intent(this, RemapActivity::class.java))
        }

        requestBtPermissionsAndStart()
        registerStateReceiver()
    }

    override fun onDestroy() {
        if (serviceBound) unbindService(serviceConnection)
        unregisterReceiver(stateReceiver)
        super.onDestroy()
    }

    // ── Physical controller input ─────────────────────────────────────────────

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
            || event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) {
            if (hidService?.onPhysicalKey(keyCode, true) == true) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
            || event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) {
            if (hidService?.onPhysicalKey(keyCode, false) == true) return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
            && event.action == MotionEvent.ACTION_MOVE) {
            hidService?.onPhysicalAxis(event)
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    // ── State receiver ────────────────────────────────────────────────────────

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getIntExtra(BluetoothHidService.EXTRA_STATE, -1)
            statusText.text = when (state) {
                BluetoothHidService.STATE_IDLE         -> "Idle"
                BluetoothHidService.STATE_REGISTERED   -> "Registered — waiting for host"
                BluetoothHidService.STATE_CONNECTED    -> "✓ Connected to host"
                BluetoothHidService.STATE_DISCONNECTED -> "Disconnected"
                BluetoothHidService.STATE_ERROR        -> "⚠ Error — check BT permissions"
                else -> "Unknown state"
            }
        }
    }

    private fun registerStateReceiver() {
        registerReceiver(stateReceiver,
            IntentFilter(BluetoothHidService.ACTION_STATE_CHANGED))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun wireFaceButton(viewId: Int, tag: String) {
        val view = findViewById<View>(viewId) ?: return
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { hidService?.onVirtualButton(tag, true);  view.isPressed = true  }
                MotionEvent.ACTION_UP   -> { hidService?.onVirtualButton(tag, false); view.isPressed = false }
            }
            true
        }
    }

    private fun wireDPad(viewId: Int, tag: String) = wireFaceButton(viewId, tag)

    private fun startHidService() {
        val intent = Intent(this, BluetoothHidService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun requestBtPermissionsAndStart() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelf(Manifest.permission.BLUETOOTH_CONNECT))  needed += Manifest.permission.BLUETOOTH_CONNECT
            if (checkSelf(Manifest.permission.BLUETOOTH_ADVERTISE)) needed += Manifest.permission.BLUETOOTH_ADVERTISE
        } else {
            if (checkSelf(Manifest.permission.BLUETOOTH))       needed += Manifest.permission.BLUETOOTH
            if (checkSelf(Manifest.permission.BLUETOOTH_ADMIN)) needed += Manifest.permission.BLUETOOTH_ADMIN
        }
        if (checkSelf(Manifest.permission.FOREGROUND_SERVICE)) needed += Manifest.permission.FOREGROUND_SERVICE

        if (needed.isEmpty()) startHidService()
        else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun checkSelf(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
}
