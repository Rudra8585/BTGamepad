package com.btgamepad.input

import android.view.KeyEvent
import android.view.MotionEvent
import com.btgamepad.hid.HidDescriptor
import kotlin.math.roundToInt

/**
 * RemappingManager
 *
 * Maintains the current 9-byte HID report and applies changes to it
 * as input events arrive. Thread-safe: all public mutations are
 * @Synchronized.
 *
 * Usage:
 *   val mgr = RemappingManager()
 *   mgr.onPhysicalKey(keyCode, isDown)   // from onKeyDown / onKeyUp
 *   mgr.onPhysicalAxis(event)            // from onGenericMotionEvent
 *   mgr.onVirtualButton(tag, isDown)     // from on-screen button touch
 *   mgr.onVirtualAxis(tag, value)        // from on-screen joystick (-1f … 1f)
 *   val bytes = mgr.currentReport()      // snapshot to send over BT
 */
class RemappingManager {

    // ── Current HID report (mutable, 9 bytes) ────────────────────────────────
    private val report = HidDescriptor.emptyReport()

    // ── D-Pad logical state ───────────────────────────────────────────────────
    private val dpad = DPadState()

    // ── Default mappings ─────────────────────────────────────────────────────

    /** Physical keycodes → report targets (Retroid Pocket 5 / standard HID gamepad). */
    private val physicalKeyMap: MutableMap<Int, ReportTarget> = mutableMapOf(
        // Face buttons
        KeyEvent.KEYCODE_BUTTON_A     to ReportTarget.Button(ReportIndex.BUTTONS_1, ButtonMask1.A),
        KeyEvent.KEYCODE_BUTTON_B     to ReportTarget.Button(ReportIndex.BUTTONS_1, ButtonMask1.B),
        KeyEvent.KEYCODE_BUTTON_X     to ReportTarget.Button(ReportIndex.BUTTONS_1, ButtonMask1.X),
        KeyEvent.KEYCODE_BUTTON_Y     to ReportTarget.Button(ReportIndex.BUTTONS_1, ButtonMask1.Y),
        // Shoulder buttons
        KeyEvent.KEYCODE_BUTTON_L1    to ReportTarget.Button(ReportIndex.BUTTONS_1, ButtonMask1.LB),
        KeyEvent.KEYCODE_BUTTON_R1    to ReportTarget.Button(ReportIndex.BUTTONS_1, ButtonMask1.RB),
        // Menu buttons
        KeyEvent.KEYCODE_BUTTON_START to ReportTarget.Button(ReportIndex.BUTTONS_1, ButtonMask1.START),
        KeyEvent.KEYCODE_BUTTON_SELECT to ReportTarget.Button(ReportIndex.BUTTONS_1, ButtonMask1.SELECT.toInt()),
        // Stick clicks
        KeyEvent.KEYCODE_BUTTON_THUMBL to ReportTarget.Button(ReportIndex.BUTTONS_2, ButtonMask2.L3),
        KeyEvent.KEYCODE_BUTTON_THUMBR to ReportTarget.Button(ReportIndex.BUTTONS_2, ButtonMask2.R3),
        // Home
        KeyEvent.KEYCODE_BUTTON_MODE  to ReportTarget.Button(ReportIndex.BUTTONS_2, ButtonMask2.HOME),
        // D-Pad keys
        KeyEvent.KEYCODE_DPAD_UP      to ReportTarget.DPad,
        KeyEvent.KEYCODE_DPAD_DOWN    to ReportTarget.DPad,
        KeyEvent.KEYCODE_DPAD_LEFT    to ReportTarget.DPad,
        KeyEvent.KEYCODE_DPAD_RIGHT   to ReportTarget.DPad,
    )

    /** Physical axis IDs → report byte indices. */
    private val physicalAxisMap: MutableMap<Int, ReportTarget.Axis> = mutableMapOf(
        MotionEvent.AXIS_X   to ReportTarget.Axis(ReportIndex.AXIS_LX),
        MotionEvent.AXIS_Y   to ReportTarget.Axis(ReportIndex.AXIS_LY, inverted = false),
        MotionEvent.AXIS_Z   to ReportTarget.Axis(ReportIndex.AXIS_RX),
        MotionEvent.AXIS_RZ  to ReportTarget.Axis(ReportIndex.AXIS_RY),
        MotionEvent.AXIS_LTRIGGER to ReportTarget.Axis(ReportIndex.AXIS_LT),
        MotionEvent.AXIS_RTRIGGER to ReportTarget.Axis(ReportIndex.AXIS_RT),
        // Some devices use BRAKE/GAS for triggers
        MotionEvent.AXIS_BRAKE to ReportTarget.Axis(ReportIndex.AXIS_LT),
        MotionEvent.AXIS_GAS   to ReportTarget.Axis(ReportIndex.AXIS_RT),
        // D-Pad as HAT axis
        MotionEvent.AXIS_HAT_X to ReportTarget.Axis(ReportIndex.DPAD_HAT),
        MotionEvent.AXIS_HAT_Y to ReportTarget.Axis(ReportIndex.DPAD_HAT),
    )

    /** Virtual button tags → report targets. */
    private val virtualButtonMap: MutableMap<String, ReportTarget> = mutableMapOf(
        "btn_a"      to ReportTarget.Button(ReportIndex.BUTTONS_1, ButtonMask1.A),
        "btn_b"      to ReportTarget.Button(ReportIndex.BUTTONS_1, ButtonMask1.B),
        "btn_x"      to ReportTarget.Button(ReportIndex.BUTTONS_1, ButtonMask1.X),
        "btn_y"      to ReportTarget.Button(ReportIndex.BUTTONS_1, ButtonMask1.Y),
        "btn_lb"     to ReportTarget.Button(ReportIndex.BUTTONS_1, ButtonMask1.LB),
        "btn_rb"     to ReportTarget.Button(ReportIndex.BUTTONS_1, ButtonMask1.RB),
        "btn_lt"     to ReportTarget.Axis(ReportIndex.AXIS_LT),
        "btn_rt"     to ReportTarget.Axis(ReportIndex.AXIS_RT),
        "btn_start"  to ReportTarget.Button(ReportIndex.BUTTONS_1, ButtonMask1.START),
        "btn_select" to ReportTarget.Button(ReportIndex.BUTTONS_1, ButtonMask1.SELECT.toInt()),
        "btn_l3"     to ReportTarget.Button(ReportIndex.BUTTONS_2, ButtonMask2.L3),
        "btn_r3"     to ReportTarget.Button(ReportIndex.BUTTONS_2, ButtonMask2.R3),
        "dpad_up"    to ReportTarget.DPad,
        "dpad_down"  to ReportTarget.DPad,
        "dpad_left"  to ReportTarget.DPad,
        "dpad_right" to ReportTarget.DPad,
    )

    /** Virtual axis tags → report byte indices. */
    private val virtualAxisMap: MutableMap<String, ReportTarget.Axis> = mutableMapOf(
        "ls_x" to ReportTarget.Axis(ReportIndex.AXIS_LX),
        "ls_y" to ReportTarget.Axis(ReportIndex.AXIS_LY),
        "rs_x" to ReportTarget.Axis(ReportIndex.AXIS_RX),
        "rs_y" to ReportTarget.Axis(ReportIndex.AXIS_RY),
        "lt"   to ReportTarget.Axis(ReportIndex.AXIS_LT),
        "rt"   to ReportTarget.Axis(ReportIndex.AXIS_RT),
    )

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Handle a physical key event.
     * @param keyCode  e.g. [KeyEvent.KEYCODE_BUTTON_A]
     * @param isDown   true = pressed, false = released
     * @return true if the key was consumed by a mapping
     */
    @Synchronized
    fun onPhysicalKey(keyCode: Int, isDown: Boolean): Boolean {
        val target = physicalKeyMap[keyCode] ?: return false
        applyButtonOrDPad(keyCode, target, isDown)
        return true
    }

    /**
     * Handle a generic motion event from a physical controller.
     * Call from [android.app.Activity.onGenericMotionEvent].
     */
    @Synchronized
    fun onPhysicalAxis(event: MotionEvent) {
        // Check HAT axes separately (they drive the D-Pad)
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        if (hatX != 0f || hatY != 0f || isHatAxesPresent(event)) {
            dpad.right = hatX >  0.5f
            dpad.left  = hatX < -0.5f
            dpad.down  = hatY >  0.5f
            dpad.up    = hatY < -0.5f
            report[ReportIndex.DPAD_HAT] = dpad.toHat()
        }

        // Remaining axes
        for ((axis, target) in physicalAxisMap) {
            if (axis == MotionEvent.AXIS_HAT_X || axis == MotionEvent.AXIS_HAT_Y) continue
            val raw = event.getAxisValue(axis) // -1f … 1f for sticks; 0…1 for triggers
            val byte = axisFloatToByte(raw, target.byteIndex == ReportIndex.AXIS_LT
                    || target.byteIndex == ReportIndex.AXIS_RT, target.inverted)
            report[target.byteIndex] = byte
        }
    }

    /** Handle a virtual on-screen button press/release. */
    @Synchronized
    fun onVirtualButton(tag: String, isDown: Boolean) {
        val target = virtualButtonMap[tag] ?: return
        applyButtonOrDPad(tag, target, isDown)
    }

    /**
     * Handle a virtual joystick axis value.
     * @param tag    e.g. "ls_x"
     * @param value  normalised -1f … 1f (0f = centre)
     */
    @Synchronized
    fun onVirtualAxis(tag: String, value: Float) {
        val target = virtualAxisMap[tag] ?: return
        val isTrigger = target.byteIndex == ReportIndex.AXIS_LT
                || target.byteIndex == ReportIndex.AXIS_RT
        report[target.byteIndex] = axisFloatToByte(value, isTrigger, target.inverted)
    }

    /** Returns a snapshot copy of the current 9-byte HID report. */
    @Synchronized
    fun currentReport(): ByteArray = report.copyOf()

    // ── Remapping API ─────────────────────────────────────────────────────────

    /** Replace the entire physical key → report mapping. */
    fun setPhysicalKeyMappings(map: Map<Int, ReportTarget>) {
        physicalKeyMap.clear()
        physicalKeyMap.putAll(map)
    }

    /** Replace the entire virtual button → report mapping. */
    fun setVirtualButtonMappings(map: Map<String, ReportTarget>) {
        virtualButtonMap.clear()
        virtualButtonMap.putAll(map)
    }

    /** Add or update a single physical key binding. */
    fun remapPhysicalKey(keyCode: Int, target: ReportTarget) {
        physicalKeyMap[keyCode] = target
    }

    /** Add or update a single virtual button binding. */
    fun remapVirtualButton(tag: String, target: ReportTarget) {
        virtualButtonMap[tag] = target
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun applyButtonOrDPad(key: Any, target: ReportTarget, isDown: Boolean) {
        when (target) {
            is ReportTarget.Button -> {
                if (isDown) report[target.byteIndex] = (report[target.byteIndex].toInt() or target.bitMask).toByte()
                else        report[target.byteIndex] = (report[target.byteIndex].toInt() and target.bitMask.inv()).toByte()
            }
            is ReportTarget.DPad -> {
                // Determine which D-Pad direction from key identity
                val tag = key.toString()
                val keyCode = key as? Int
                when {
                    keyCode == KeyEvent.KEYCODE_DPAD_UP    || tag == "dpad_up"    -> dpad.up    = isDown
                    keyCode == KeyEvent.KEYCODE_DPAD_DOWN  || tag == "dpad_down"  -> dpad.down  = isDown
                    keyCode == KeyEvent.KEYCODE_DPAD_LEFT  || tag == "dpad_left"  -> dpad.left  = isDown
                    keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || tag == "dpad_right" -> dpad.right = isDown
                }
                report[ReportIndex.DPAD_HAT] = dpad.toHat()
            }
            is ReportTarget.Axis -> {
                // Treat digital button as full press / release on an axis
                report[target.byteIndex] = if (isDown) 0xFF.toByte() else 0x00
            }
        }
    }

    /**
     * Convert a floating-point axis value to a report byte.
     * @param raw        axis value from Android (-1…1 for sticks, 0…1 for triggers)
     * @param isTrigger  if true, treats input as 0…1 range
     * @param inverted   if true, flips the axis
     */
    private fun axisFloatToByte(raw: Float, isTrigger: Boolean, inverted: Boolean): Byte {
        val normalised = if (isTrigger) {
            // Triggers: 0.0 → 0, 1.0 → 255
            raw.coerceIn(0f, 1f)
        } else {
            // Sticks: -1.0 → 0, 0.0 → 128, 1.0 → 255
            (raw.coerceIn(-1f, 1f) + 1f) / 2f
        }
        val result = (normalised * 255f).roundToInt().coerceIn(0, 255)
        return if (inverted) (255 - result).toByte() else result.toByte()
    }

    private fun isHatAxesPresent(event: MotionEvent): Boolean =
        event.getAxisValue(MotionEvent.AXIS_HAT_X) == 0f &&
        event.getAxisValue(MotionEvent.AXIS_HAT_Y) == 0f &&
        (dpad.up || dpad.down || dpad.left || dpad.right)
}
