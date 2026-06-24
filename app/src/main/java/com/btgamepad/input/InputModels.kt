package com.btgamepad.input

import android.view.KeyEvent
import android.view.MotionEvent

// ─────────────────────────────────────────────────────────────────────────────
//  Report byte-field definitions
// ─────────────────────────────────────────────────────────────────────────────

/** Byte indices inside the 9-byte HID report. */
object ReportIndex {
    const val BUTTONS_1 = 0   // A B X Y LB RB Start Select
    const val BUTTONS_2 = 1   // L3 R3 Home (reserved)
    const val DPAD_HAT  = 2   // hat switch 0-7, 8=centred
    const val AXIS_LX   = 3
    const val AXIS_LY   = 4
    const val AXIS_RX   = 5
    const val AXIS_RY   = 6
    const val AXIS_LT   = 7
    const val AXIS_RT   = 8
}

/** Bitmask for each button in BUTTONS_1. */
object ButtonMask1 {
    const val A      = 0x01
    const val B      = 0x02
    const val X      = 0x04
    const val Y      = 0x08
    const val LB     = 0x10
    const val RB     = 0x20
    const val START  = 0x40
    const val SELECT = 0x80.toByte()
}

/** Bitmask for each button in BUTTONS_2. */
object ButtonMask2 {
    const val L3   = 0x01
    const val R3   = 0x02
    const val HOME = 0x04
}

/** D-Pad HAT values (matches HID spec: 0=N, 1=NE, … 7=NW, 8=centred). */
object DPadHat {
    const val N        = 0
    const val NE       = 1
    const val E        = 2
    const val SE       = 3
    const val S        = 4
    const val SW       = 5
    const val W        = 6
    const val NW       = 7
    const val CENTERED = 8
}

// ─────────────────────────────────────────────────────────────────────────────
//  Source-input sealed class
// ─────────────────────────────────────────────────────────────────────────────

/** Represents the originating input event (physical key, axis, or virtual touch). */
sealed class SourceInput {
    /** A physical Android key code (e.g. KeyEvent.KEYCODE_BUTTON_A). */
    data class PhysicalKey(val keyCode: Int) : SourceInput()

    /** A physical joystick axis (e.g. MotionEvent.AXIS_X). */
    data class PhysicalAxis(val axis: Int) : SourceInput()

    /** A virtual on-screen button identified by a stable string tag. */
    data class VirtualButton(val tag: String) : SourceInput()

    /** A virtual on-screen joystick identified by tag + axis direction. */
    data class VirtualAxis(val tag: String) : SourceInput()
}

// ─────────────────────────────────────────────────────────────────────────────
//  Destination-output sealed class
// ─────────────────────────────────────────────────────────────────────────────

/** Describes which field in the HID report this input drives. */
sealed class ReportTarget {
    /**
     * A single button bit.
     * @param byteIndex  [ReportIndex.BUTTONS_1] or [ReportIndex.BUTTONS_2]
     * @param bitMask    The bit to set/clear (e.g. [ButtonMask1.A])
     */
    data class Button(val byteIndex: Int, val bitMask: Int) : ReportTarget()

    /**
     * The D-Pad HAT switch.
     * Handled via [DPadState] rather than a raw byte write.
     */
    object DPad : ReportTarget()

    /**
     * An 8-bit analog axis byte.
     * @param byteIndex  e.g. [ReportIndex.AXIS_LX]
     * @param inverted   Flip the axis (raw 0 → report 255, raw 255 → report 0)
     */
    data class Axis(val byteIndex: Int, val inverted: Boolean = false) : ReportTarget()
}

// ─────────────────────────────────────────────────────────────────────────────
//  Mapping entry
// ─────────────────────────────────────────────────────────────────────────────

data class InputMapping(
    val source: SourceInput,
    val target: ReportTarget
)

// ─────────────────────────────────────────────────────────────────────────────
//  D-Pad state helper (tracks Up/Down/Left/Right independently → HAT value)
// ─────────────────────────────────────────────────────────────────────────────

class DPadState {
    var up    = false
    var down  = false
    var left  = false
    var right = false

    fun toHat(): Byte {
        return when {
            up    && right -> DPadHat.NE
            down  && right -> DPadHat.SE
            down  && left  -> DPadHat.SW
            up    && left  -> DPadHat.NW
            up             -> DPadHat.N
            right          -> DPadHat.E
            down           -> DPadHat.S
            left           -> DPadHat.W
            else           -> DPadHat.CENTERED
        }.toByte()
    }
}
