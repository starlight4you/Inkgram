# Inkgram ✈️

**Inkgram** is a customized, unofficial Telegram client specifically designed and optimized for **E-Ink Android devices** (such as e-readers and paper tablets). It aims to deliver a high-contrast, minimalist, and distraction-free messaging experience that matches the unique refresh rates and visual aesthetics of E-Ink screens.

---

## ✨ Features & Customizations

This repository is customized based on the official Telegram for Android source code, complying fully with all developer terms:

1. **Custom Brand Identity**: Fully rebranded from "Telegram" to **Inkgram** across all interface elements and 10+ localized string resources.
2. **Minimalist E-Ink Icon**: Replaced all 90 launcher assets with an elegant, high-contrast, monochrome stroke paper-plane logo designed to render beautifully on electronic paper displays.
3. **Dedicated Package Namespace**: Package ID changed to `com.inkgram.messenger` for all build variants, completely separating it from the official app.
4. **Official API ID Integration**: Pre-configured with official API credentials in the build variables.
5. **Robust Build Setup**: Restored NDK toolchain symlink references and configured dual-client Google Services supporting concurrent module compilations out-of-the-box.

---

## 🛠️ How to Build from Command Line

You can build the debug APK directly from your terminal:

```bash
# Compile and generate the debug APK
./gradlew :TMessagesProj_App:assembleDebug
```

Upon successful compilation, the generated APK will be located at:
`TMessagesProj_App/build/outputs/apk/afat/debug/app.apk`

---

## 📲 Installing via Wireless Debugging

If your Android E-Ink device is connected to your Mac/PC via ADB (wireless debugging supported):

```bash
adb install -r TMessagesProj_App/build/outputs/apk/afat/debug/app.apk
```

---

## ⚖️ License

This project is licensed under the **GNU General Public License v2** (GPLv2). All code and adaptations are fully open-source in compliance with the parent repository's licensing requirements.
