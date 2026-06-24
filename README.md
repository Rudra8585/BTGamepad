# BT Virtual Gamepad

An Android app that registers itself as a **standard Bluetooth HID gamepad** using `BluetoothHidDevice` (Android 9+).  
Pair it with any PC, console, or Android device and it appears as a native wireless controller — no receiver app needed.

---

## HID Report Layout

The descriptor defines a **9-byte report** sent on every input change:

```
Byte  Field         Values
────  ────────────  ──────────────────────────────────────────────────
[0]   buttons_1     Bit 0=A  1=B  2=X  3=Y  4=LB  5=RB  6=Start  7=Select
[1]   buttons_2     Bit 0=L3  1=R3  2=Home  3=(reserved)
                    + 4 padding bits → 2 full bytes for 12 buttons
[2]   dpad_hat      0=N 1=NE 2=E 3=SE 4=S 5=SW 6=W 7=NW 8=Centred
[3]   axis_lx       Left stick X   (0=full-left, 128=centre, 255=full-right)
[4]   axis_ly       Left stick Y   (0=full-up,   128=centre, 255=full-down)
[5]   axis_rx       Right stick X  (same scale)
[6]   axis_ry       Right stick Y  (same scale)
[7]   axis_lt       Left trigger   (0=released, 255=full-press)
[8]   axis_rt       Right trigger  (0=released, 255=full-press)
```

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│  MainActivity                                                       │
│  ┌──────────────┐  ┌──────────────┐  onKeyDown / onGenericMotion   │
│  │ VirtualJoystick│  │ Face Buttons │  ──────────────────────────── │
│  │ View (×2)    │  │ (touch)      │  Physical Controller Events     │
│  └──────┬───────┘  └──────┬───────┘             │                  │
│         │ onMoveListener  │ setOnTouchListener   │                  │
└─────────┼─────────────────┼──────────────────────┼──────────────────┘
          │                 │                      │
          ▼                 ▼                      ▼
┌─────────────────────────────────────────────────────────────────────┐
│  BluetoothHidService  (Foreground Service)                          │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  RemappingManager                                             │  │
│  │  ┌─────────────┐   ┌─────────────┐   ┌────────────────────┐  │  │
│  │  │ physicalKey │   │ virtualBtn  │   │ physicalAxis /     │  │  │
│  │  │ Map         │   │ Map         │   │ virtualAxis Map    │  │  │
│  │  └──────┬──────┘   └──────┬──────┘   └─────────┬──────────┘  │  │
│  │         └──────────────────┴──────────────────── ┘            │  │
│  │                         ▼                                     │  │
│  │                   report[9] byte array (mutable)              │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                              │ sendReport()                          │
│                              ▼                                       │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │  BluetoothHidDevice  (Android OS HID profile proxy)           │  │
│  │  ┌──────────────────────────────────────────────────────────┐  │  │
│  │  │  SDP Record: SUBCLASS1_GAMEPAD + HID Descriptor          │  │  │
│  │  └──────────────────────────────────────────────────────────┘  │  │
│  └──────────────────────────────┬─────────────────────────────────┘  │
└─────────────────────────────────┼───────────────────────────────────┘
                                  │ Bluetooth HID (HOGP / Classic BT)
                                  ▼
                      PC / Console / Android host
```

---

## File Map

```
app/src/main/
├── kotlin/com/btgamepad/
│   ├── hid/
│   │   └── HidDescriptor.kt        ← Raw descriptor bytes + SDP metadata
│   ├── input/
│   │   ├── InputModels.kt          ← SourceInput, ReportTarget, DPadState
│   │   └── RemappingManager.kt     ← Translates events → 9-byte report
│   ├── service/
│   │   └── BluetoothHidService.kt  ← Foreground service, BT HID profile
│   ├── ui/
│   │   └── VirtualJoystickView.kt  ← Custom touch joystick widget
│   ├── MainActivity.kt             ← UI wiring, physical key events
│   └── RemapActivity.kt            ← Interactive button remapping UI
├── res/
│   ├── layout/activity_main.xml    ← Gamepad layout
│   ├── layout/activity_remap.xml   ← Remap screen
│   └── values/strings_and_styles.xml
└── AndroidManifest.xml
.github/workflows/android.yml       ← GitHub Actions APK build
```

---

## Getting Started

### Requirements
- Android 9.0+ (API 28) — `BluetoothHidDevice` requires it
- Bluetooth must be enabled on the Android device
- The host device (PC etc.) must support Bluetooth Classic HID

### Building
```bash
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

### GitHub Actions
Push to `main` → the `android.yml` workflow builds the debug APK automatically.  
Download from **Actions → latest run → Artifacts → BTGamepad-debug-N**.

### Pairing
1. On the Android device: open the app → it registers as a HID gamepad.
2. On the host PC: **Add a Bluetooth device** → select **BTGamepad** from the list.
3. The app status bar updates to **"✓ Connected"** once the host connects.

---

## Remapping

Tap **Remap** in the app to open the remapping screen. Tap any row, then press a physical button — it's instantly rebound.

To remap programmatically:
```kotlin
// Change physical A button to KeyEvent.KEYCODE_BUTTON_B
hidService.remappingManager.remapPhysicalKey(
    KeyEvent.KEYCODE_BUTTON_B,
    ReportTarget.Button(ReportIndex.BUTTONS_1, ButtonMask1.A)
)
```

---

## Permissions (AndroidManifest)

| Permission | Why |
|---|---|
| `BLUETOOTH_CONNECT` (API 31+) | Connect to host, register HID app |
| `BLUETOOTH_ADVERTISE` (API 31+) | Make device discoverable |
| `BLUETOOTH` / `BLUETOOTH_ADMIN` (API ≤30) | Legacy BT access |
| `FOREGROUND_SERVICE` | Keep HID service alive in background |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` (API 34+) | Correct service type |
