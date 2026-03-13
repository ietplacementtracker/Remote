# Daikin IR Remote — Android App

A fully offline Android remote control for Daikin air conditioners, written in Kotlin.
Uses the Android `ConsumerIrManager` API to transmit IR signals from the phone's built-in IR blaster.

---

## Features

| Feature | Detail |
|---|---|
| Power on/off | Toggles AC power |
| Temperature | Adjustable 16 °C – 30 °C via ▲/▼ buttons |
| Modes | Cool, Dry, Fan |
| Fan speeds | Auto, Low, Medium, High |
| No internet | 100% offline, no login or signup |
| Full state IR | Every button press sends the complete AC state |

---

## Project structure

```
DaikinIRRemote/
├── app/
│   └── src/main/
│       ├── kotlin/com/daikin/irremote/
│       │   ├── ACState.kt          ← AC state model (power, temp, mode, fan)
│       │   ├── IRController.kt     ← IR signal builder + ConsumerIrManager transmitter
│       │   └── MainActivity.kt     ← UI controller
│       ├── res/
│       │   ├── layout/
│       │   │   └── activity_main.xml   ← Remote UI layout
│       │   └── values/
│       │       ├── colors.xml          ← Colour palette
│       │       ├── strings.xml
│       │       └── themes.xml
│       └── AndroidManifest.xml     ← TRANSMIT_IR permission declared here
├── build.gradle                    ← Project-level Gradle
├── settings.gradle
└── gradle/wrapper/
    └── gradle-wrapper.properties   ← Gradle 8.6
```

---

## How to run in Android Studio

### Prerequisites

- **Android Studio Hedgehog (2023.1.1)** or newer
- **Android SDK** with API 34 (Android 14) installed
- A **physical Android device with an IR blaster** (e.g. Xiaomi, Samsung Galaxy S series,
  HTC One, LG G series, Huawei flagship). IR blasters are not available on emulators.

### Steps

1. **Open the project**
   - Launch Android Studio.
   - Choose **File → Open…** and select the `DaikinIRRemote` folder (the one containing `settings.gradle`).
   - Android Studio will sync Gradle automatically. Wait for the sync to complete.

2. **Trust the project** (if prompted)
   - Click **Trust Project** in the dialog that appears.

3. **Connect your device**
   - Enable **Developer Options** on your phone (Settings → About Phone → tap Build Number 7×).
   - Enable **USB Debugging** (Developer Options → USB Debugging).
   - Connect the phone via USB.
   - Android Studio should detect it in the device dropdown (top toolbar).

4. **Run the app**
   - Click the green **▶ Run** button (or press `Shift+F10`).
   - Select your physical device.
   - The app will build and install automatically.

5. **Use the remote**
   - Press **⏻ Power** to turn the AC on.
   - Use **▲/▼** to set the temperature.
   - Tap a **Mode** button (Cool / Dry / Fan).
   - Tap a **Fan Speed** button (Auto / Low / Medium / High).
   - Point the phone's IR blaster at the AC unit (usually the top edge of the phone).

---

## IR protocol notes

- **Carrier frequency:** 38 000 Hz (38 kHz)
- **Protocol:** Daikin ARC433** format (two-frame transmission)
- **Frame 1:** Fixed 8-byte header — same for every command
- **Frame 2:** 19-byte payload — encodes power, mode, temperature, fan speed
- **Bit order:** LSB first (least significant bit transmitted first)
- **Checksum:** Sum of bytes 0–17 in Frame 2, modulo 256

### Adding more commands

Open `IRController.kt` and see the section **"EXTENDING WITH ADDITIONAL CODES"** at the bottom.
Key extension points:

1. **New modes/fan speeds** — add enum values to `ACMode` or `FanSpeed` in `ACState.kt`.
2. **Special flags** (e.g. Powerful, Econo, Sleep, Swing) — set the corresponding bits in
   the Frame 2 payload inside `IRController.buildFrame2()`.
3. **Other Daikin model variants** — create a parallel `buildFrame2ForModelX()` method with
   the byte offsets documented for that model's protocol.
4. **Raw IR hex codes** from LIRC / irdb databases can be converted to mark/space arrays
   and transmitted directly with `irManager.transmit()`.

---

## Troubleshooting

| Problem | Solution |
|---|---|
| "No IR blaster available" toast | Your device doesn't have an IR blaster. Use a device that does. |
| App installs but AC doesn't respond | Make sure the IR blaster is pointing at the AC; try within 5 m. |
| Gradle sync fails | Check that Android SDK API 34 is installed via SDK Manager. |
| `local.properties` warning | Android Studio regenerates this automatically — ignore. |

---

## Permissions

The app declares only one permission:

```xml
<uses-permission android:name="android.permission.TRANSMIT_IR" />
```

No internet, no camera, no location, no storage — completely offline.
