# AGENTS.md

## Cursor Cloud specific instructions

### Product overview

Inkgram is a Gradle Android monorepo (Telegram Android fork for e-ink devices). There is no web server or Docker Compose stack. Development is **on-device / emulator** plus **Gradle builds** on the host.

### System dependencies (VM image)

The cloud VM should provide:

- **JDK 17+** (JDK 21 works with Gradle 8.7)
- **Android SDK** at `/opt/android-sdk` with:
  - `platforms;android-35`
  - `build-tools;35.0.0` and `build-tools;30.0.3` (30.0.3 `dx`/`dx.jar` copied into 35.0.0 per repo `Dockerfile`)
  - `ndk;27.2.12479018`
  - `platform-tools`, `emulator` (optional, for instrumented tests)

Set `ANDROID_HOME=/opt/android-sdk` and extend `PATH` with `cmdline-tools/latest/bin`, `platform-tools`, and `emulator`.

### `local.properties`

`local.properties` is gitignored. Create it at the repo root if missing:

```properties
sdk.dir=/opt/android-sdk
```

Optional Telegram API credentials for real login flows (from https://my.telegram.org/apps):

```properties
APP_ID=<your_id>
APP_HASH=<your_hash>
```

### Build (primary dev workflow)

Main debug APK (fastest single-ABI build):

```bash
./gradlew :TMessagesProj_App:assembleAfatDebug -Pandroid.injected.build.abi=x86_64
```

Output APK: `TMessagesProj_App/build/intermediates/apk/afat/debug/app.apk` (`com.inkgram.messenger.beta`).

Release/bundle variants and other app modules (`TMessagesProj_AppHuawei`, `TMessagesProj_AppStandalone`, etc.) are documented in the root `Dockerfile` CMD.

### Lint

```bash
./gradlew :TMessagesProj:lintDebug :TMessagesProj_App:lintAfatDebug
```

The app lint task may fail on pre-existing manifest `MissingClass` entries (Google Play Services measurement classes). That reflects project lint configuration, not a broken SDK install.

### Instrumented tests

Module: `TMessagesProj_AppTests`. Requires a running emulator or USB device:

```bash
./gradlew :TMessagesProj_AppTests:connectedAfatDebugAndroidTest -Pandroid.injected.build.abi=x86_64
```

**Emulator caveat:** x86_64 AVDs need `/dev/kvm` for reliable boot. Without KVM, use `emulator -accel off` (very slow; boot/install may be flaky). Prefer a physical device or a KVM-enabled VM for connected tests.

### Running the app

Install debug APK:

```bash
adb install -r TMessagesProj_App/build/intermediates/apk/afat/debug/app.apk
```

Launch: `adb shell am start -n com.inkgram.messenger.beta/org.telegram.ui.LaunchActivity` (exact activity may vary by build).

Full messaging E2E requires Telegram cloud + valid `APP_ID`/`APP_HASH`.

### Gotchas

- First native build is long (~8+ minutes); subsequent builds are incremental.
- Do not run `pnpm`/`npm` — this is not a Node project.
- `connectedAfatDebugAndroidTest` can hit Kotlin stdlib version conflicts in generated androidTest sources; `assembleAfatDebug` for the main app is the reliable compile check.
