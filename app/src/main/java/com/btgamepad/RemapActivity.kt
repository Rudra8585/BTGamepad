package com.btgamepad

import android.os.Bundle
import android.view.KeyEvent
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.btgamepad.input.ButtonMask1
import com.btgamepad.input.ButtonMask2
import com.btgamepad.input.ReportIndex
import com.btgamepad.input.ReportTarget

/**
 * RemapActivity
 *
 * Allows the user to re-bind physical controller keys to different gamepad
 * buttons. Tap a row to begin "listening" for a key press, then the next key
 * pressed will be assigned to that slot.
 *
 * In a production app this would persist mappings to SharedPreferences or
 * a Room database. Here we demonstrate the interactive remapping UX pattern.
 */
class RemapActivity : AppCompatActivity() {

    // ── Slots available for remapping ─────────────────────────────────────────

    data class Slot(
        val label: String,
        val target: ReportTarget,
        var boundKeyCode: Int?
    )

    private val slots = mutableListOf(
        Slot("A Button",       ReportTarget.Button(ReportIndex.BUTTONS_1, ButtonMask1.A),      KeyEvent.KEYCODE_BUTTON_A),
        Slot("B Button",       ReportTarget.Button(ReportIndex.BUTTONS_1, ButtonMask1.B),      KeyEvent.KEYCODE_BUTTON_B),
        Slot("X Button",       ReportTarget.Button(ReportIndex.BUTTONS_1, ButtonMask1.X),      KeyEvent.KEYCODE_BUTTON_X),
        Slot("Y Button",       ReportTarget.Button(ReportIndex.BUTTONS_1, ButtonMask1.Y),      KeyEvent.KEYCODE_BUTTON_Y),
        Slot("LB",             ReportTarget.Button(ReportIndex.BUTTONS_1, ButtonMask1.LB),     KeyEvent.KEYCODE_BUTTON_L1),
        Slot("RB",             ReportTarget.Button(ReportIndex.BUTTONS_1, ButtonMask1.RB),     KeyEvent.KEYCODE_BUTTON_R1),
        Slot("Start",          ReportTarget.Button(ReportIndex.BUTTONS_1, ButtonMask1.START),  KeyEvent.KEYCODE_BUTTON_START),
        Slot("Select",         ReportTarget.Button(ReportIndex.BUTTONS_1, ButtonMask1.SELECT.toInt()), KeyEvent.KEYCODE_BUTTON_SELECT),
        Slot("L3 (Left Click)",ReportTarget.Button(ReportIndex.BUTTONS_2, ButtonMask2.L3),     KeyEvent.KEYCODE_BUTTON_THUMBL),
        Slot("R3 (Right Click)",ReportTarget.Button(ReportIndex.BUTTONS_2, ButtonMask2.R3),    KeyEvent.KEYCODE_BUTTON_THUMBR),
    )

    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private var listeningSlotIndex: Int = -1

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remap)

        listView = findViewById(R.id.lv_mappings)
        adapter  = ArrayAdapter(this, android.R.layout.simple_list_item_1, buildDisplayList())
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            beginListening(position)
        }

        findViewById<Button>(R.id.btn_reset)?.setOnClickListener { resetDefaults() }
        findViewById<Button>(R.id.btn_apply)?.setOnClickListener { applyAndFinish() }
    }

    // ── Key listening ─────────────────────────────────────────────────────────

    private fun beginListening(slotIndex: Int) {
        listeningSlotIndex = slotIndex
        AlertDialog.Builder(this)
            .setTitle("Press a button…")
            .setMessage("Press any button on your physical controller to assign it to: ${slots[slotIndex].label}")
            .setNegativeButton("Cancel") { _, _ -> listeningSlotIndex = -1 }
            .show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (listeningSlotIndex >= 0) {
            val idx = listeningSlotIndex
            listeningSlotIndex = -1
            slots[idx].boundKeyCode = keyCode
            adapter.clear()
            adapter.addAll(buildDisplayList())
            adapter.notifyDataSetChanged()
            Toast.makeText(this, "Assigned ${KeyEvent.keyCodeToString(keyCode)} → ${slots[idx].label}", Toast.LENGTH_SHORT).show()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildDisplayList(): List<String> = slots.map { slot ->
        val key = slot.boundKeyCode?.let { KeyEvent.keyCodeToString(it) } ?: "(unbound)"
        "${slot.label}  →  $key"
    }

    private fun resetDefaults() {
        slots.forEach { it.boundKeyCode = null }
        adapter.clear()
        adapter.addAll(buildDisplayList())
        adapter.notifyDataSetChanged()
        Toast.makeText(this, "Reset to defaults", Toast.LENGTH_SHORT).show()
    }

    private fun applyAndFinish() {
        // In a real app: persist slots, then push new mapping to BluetoothHidService
        // via a bound-service call or a SharedPreferences-based config reload.
        Toast.makeText(this, "Mappings applied!", Toast.LENGTH_SHORT).show()
        finish()
    }
}
