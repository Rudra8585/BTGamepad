package com.btgamepad.hid

/**
 * HID Report Descriptor for a standard Xbox 360-style gamepad.
 *
 * Report layout (8 bytes total):
 * ┌────────────────────────────────────────────────────────────────────────────────────┐
 * │ Byte 0  │ Buttons 1–8  : A, B, X, Y, LB, RB, Start, Select                       │
 * │ Byte 1  │ Buttons 9–12 : L3, R3, Home, (reserved)                                 │
 * │ Byte 2  │ D-Pad HAT switch (4-bit) + padding                                      │
 * │ Byte 3  │ Left Stick X  (0–255, centre = 128)                                     │
 * │ Byte 4  │ Left Stick Y  (0–255, centre = 128)                                     │
 * │ Byte 5  │ Right Stick X (0–255, centre = 128)                                     │
 * │ Byte 6  │ Right Stick Y (0–255, centre = 128)                                     │
 * │ Byte 7  │ Left Trigger (0–255) + Right Trigger (0–255) packed as nibbles? No —    │
 * │         │ LT and RT each get one byte. Expand to 9 bytes total.                   │
 * └────────────────────────────────────────────────────────────────────────────────────┘
 *
 * Final report: 9 bytes
 *   [0]  buttons_1  (bits 0-7: A B X Y LB RB Start Select)
 *   [1]  buttons_2  (bits 0-3: L3 R3 Home reserved)
 *   [2]  dpad_hat   (values 0-7 for directions, 8 = centred)
 *   [3]  lx         (left stick X,  0-255)
 *   [4]  ly         (left stick Y,  0-255)
 *   [5]  rx         (right stick X, 0-255)
 *   [6]  ry         (right stick Y, 0-255)
 *   [7]  lt         (left trigger,  0-255)
 *   [8]  rt         (right trigger, 0-255)
 */
object HidDescriptor {

    /** 9-byte zeroed report with sticks centred (128) and hat centred (8). */
    fun emptyReport(): ByteArray = byteArrayOf(
        0x00,  // [0] buttons_1
        0x00,  // [1] buttons_2
        0x08,  // [2] dpad hat = centred (8)
        128.toByte(),  // [3] LX
        128.toByte(),  // [4] LY
        128.toByte(),  // [5] RX
        128.toByte(),  // [6] RY
        0x00,  // [7] LT
        0x00   // [8] RT
    )

    /**
     * Raw HID Report Descriptor bytes.
     *
     * Parsed structure:
     *   Usage Page (Generic Desktop)
     *   Usage (Gamepad)
     *   Collection (Application)
     *     Collection (Physical)
     *       -- 12 buttons (bits) --
     *       Usage Page (Button)
     *       Usage Minimum (1)
     *       Usage Maximum (12)
     *       Logical Minimum (0)
     *       Logical Maximum (1)
     *       Report Size (1)
     *       Report Count (12)
     *       Input (Data, Variable, Absolute)
     *       -- 4-bit padding --
     *       Report Size (1)
     *       Report Count (4)
     *       Input (Constant)
     *       -- HAT switch (D-Pad) --
     *       Usage Page (Generic Desktop)
     *       Usage (Hat switch)
     *       Logical Minimum (0)
     *       Logical Maximum (7)
     *       Physical Minimum (0)
     *       Physical Maximum (315)
     *       Unit (English Rotation: Angular Position)
     *       Report Size (4)
     *       Report Count (1)
     *       Input (Data, Variable, Absolute, Null State)
     *       -- 4-bit padding --
     *       Report Size (4)
     *       Report Count (1)
     *       Input (Constant)
     *       -- 6 analog axes: LX, LY, RX, RY, LT, RT --
     *       Usage (X)  Usage (Y)  Usage (Z)  Usage (Rz) Usage (Rx) Usage (Ry)
     *       Logical Minimum (0)
     *       Logical Maximum (255)
     *       Report Size (8)
     *       Report Count (6)
     *       Input (Data, Variable, Absolute)
     *     End Collection
     *   End Collection
     */
    val DESCRIPTOR: ByteArray = byteArrayOf(
        0x05.toByte(), 0x01,        // Usage Page (Generic Desktop Controls)
        0x09.toByte(), 0x05,        // Usage (Gamepad)
        0xA1.toByte(), 0x01,        // Collection (Application)
        0xA1.toByte(), 0x00,        //   Collection (Physical)

        // ── 12 Buttons ──────────────────────────────────────────────────────
        0x05.toByte(), 0x09,        //   Usage Page (Button)
        0x19.toByte(), 0x01,        //   Usage Minimum (Button 1)
        0x29.toByte(), 0x0C,        //   Usage Maximum (Button 12)
        0x15.toByte(), 0x00,        //   Logical Minimum (0)
        0x25.toByte(), 0x01,        //   Logical Maximum (1)
        0x75.toByte(), 0x01,        //   Report Size (1)
        0x95.toByte(), 0x0C,        //   Report Count (12)
        0x81.toByte(), 0x02,        //   Input (Data, Variable, Absolute)

        // ── 4-bit padding (buttons → 2 bytes total) ──────────────────────
        0x75.toByte(), 0x01,        //   Report Size (1)
        0x95.toByte(), 0x04,        //   Report Count (4)
        0x81.toByte(), 0x03,        //   Input (Constant, Variable, Absolute)  [padding]

        // ── HAT Switch (D-Pad) ────────────────────────────────────────────
        0x05.toByte(), 0x01,        //   Usage Page (Generic Desktop Controls)
        0x09.toByte(), 0x39,        //   Usage (Hat switch)
        0x15.toByte(), 0x00,        //   Logical Minimum (0)
        0x25.toByte(), 0x07,        //   Logical Maximum (7)
        0x35.toByte(), 0x00,        //   Physical Minimum (0)
        0x46.toByte(), 0x3B.toByte(), 0x01, // Physical Maximum (315)
        0x65.toByte(), 0x14,        //   Unit (Eng Rot: Angular Position)
        0x75.toByte(), 0x04,        //   Report Size (4)
        0x95.toByte(), 0x01,        //   Report Count (1)
        0x81.toByte(), 0x42,        //   Input (Data, Var, Abs, Null State)

        // ── 4-bit padding after HAT (HAT → 1 full byte) ──────────────────
        0x75.toByte(), 0x04,        //   Report Size (4)
        0x95.toByte(), 0x01,        //   Report Count (1)
        0x81.toByte(), 0x03,        //   Input (Constant) [padding]

        // ── 6 Analog Axes: LX, LY, RX, RY, LT, RT ───────────────────────
        0x09.toByte(), 0x30,        //   Usage (X)
        0x09.toByte(), 0x31,        //   Usage (Y)
        0x09.toByte(), 0x32,        //   Usage (Z)
        0x09.toByte(), 0x35,        //   Usage (Rz)
        0x09.toByte(), 0x33,        //   Usage (Rx)  → LT
        0x09.toByte(), 0x34,        //   Usage (Ry)  → RT
        0x15.toByte(), 0x00,        //   Logical Minimum (0)
        0x26.toByte(), 0xFF.toByte(), 0x00, // Logical Maximum (255)
        0x75.toByte(), 0x08,        //   Report Size (8)
        0x95.toByte(), 0x06,        //   Report Count (6)
        0x81.toByte(), 0x02,        //   Input (Data, Variable, Absolute)

        0xC0.toByte(),              //   End Collection (Physical)
        0xC0.toByte()               // End Collection (Application)
    )

    // ── SDP / HID metadata ──────────────────────────────────────────────────

    val SDP_NAME        = "BT Virtual Gamepad"
    val SDP_DESCRIPTION = "Android Bluetooth HID Gamepad"
    val SDP_PROVIDER    = "BTGamepad"
}
