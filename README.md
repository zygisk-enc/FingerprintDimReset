# FingerprintDimReset

A lightweight Xposed module that prevents your screen from timing out while your finger is on the fingerprint sensor.

## How it works

When Android dims the screen (before turning it off), this module monitors for any fingerprint activity. If a finger is detected on the sensor during the dimming phase, the module automatically sends a "User Activity" signal to the system, resetting the screen timeout and bringing the brightness back to normal.

This is particularly useful for users who read long articles or watch content where the screen might dim unexpectedly.

## Features

- **Seamless Integration:** Works with the native Android power management system.
- **Privacy Focused:** Works entirely offline. No internet permissions, no tracking.
- **Performance:** Minimal footprint, only active during the dimming phase.
- **Xposed/LSPosed Compatible:** Fully compatible with modern Xposed frameworks.

## F-Droid Checklist

- [x] **Fully Open Source:** Licensed under Apache 2.0.
- [x] **Privacy:** No internet permission, no tracking, no non-free dependencies.
- [x] **Standard Build System:** Uses standard Gradle build.
- [x] **Binary Clean:** No pre-compiled libraries included.

## Build Instructions

1. Clone the repository.
2. Open in Android Studio.
3. Ensure you have the Xposed API available (linked via `compileOnly`).
4. Run `./gradlew assembleDebug` to build the debug APK.
5. Run `./gradlew assembleRelease` to build the production APK.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
