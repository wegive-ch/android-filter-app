# Android Filter App

Native Android kiosk/filter app for Android 13 and newer. The app lets the user set a first-run uninstall password, enables device-admin policies, starts lock-task mode, and provides a password-gated uninstall flow. A hard-coded administration password is embedded at build time from `password.secret`.

## Important Android Limits

Android does not allow a normal sideloaded APK to make itself impossible to delete, silently take administrator rights, block every settings screen, or prevent all app launches. The strong blocking behavior in `requirements.txt` requires provisioning this app as the device owner on a freshly reset device or through enterprise management.

Without device-owner provisioning, the app can request device-admin access and start lock-task mode, but Android still controls final uninstall and settings permissions.

## Requirements

- Android SDK with platform `android-35`
- Android build tools `35.0.0`
- Java 21 or newer
- Gradle available on `PATH`
- Node.js/npm for the requested `package.json` build wrapper

The devcontainer installs the Android command-line tools, SDK platform, build tools, Java, and Node.

If you are not using the devcontainer, install the SDK into `.android-sdk` or set `ANDROID_HOME` to an existing SDK before building.

## Configure Secrets

`password.secret` must contain one SHA-512 hex digest for the administration password. The checked-in value is a placeholder for:

```text
change-me-admin-password
```

Replace it before a real build:

```bash
printf '%s' 'your-real-admin-password' | sha512sum | awk '{print $1}' > password.secret
```

The first-run password is not embedded in the APK. It is set in the app UI and stored on the device as a salted SHA-512 hash.

## Build

```bash
npm run build
```

The release APK is produced at:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

Sign that APK with your release key before installing on a managed device.

## Release

The release signing keystore must be located at:

```text
my-release-key.jks
```

Set the keystore alias and passwords, then run:

```properties
RELEASE_KEY_ALIAS=your-key-alias
RELEASE_STORE_PASSWORD=your-keystore-password
RELEASE_KEY_PASSWORD=your-key-password
```

Save those values in `appsecrets.properties`, then run:

```bash
npm run release
```

Environment variables with the same names can also be used and will override `appsecrets.properties`:

```bash
export RELEASE_KEY_ALIAS='your-key-alias'
export RELEASE_STORE_PASSWORD='your-keystore-password'
export RELEASE_KEY_PASSWORD='your-key-password'
npm run release
```

If the key password is the same as the keystore password, `RELEASE_KEY_PASSWORD` can be omitted.

The same Gradle task can be run directly:

```bash
gradle :app:release
```

The signed APK is produced at:

```text
app/build/outputs/apk/release/app-release.apk
```

## Device-Owner Provisioning

Install on a freshly reset device before adding accounts, then run:

```bash
adb install app/build/outputs/apk/release/app-release-unsigned.apk
adb shell dpm set-device-owner ch.wegive.androidfilter/.WeGiveFilterDeviceAdminReceiver
```

After provisioning, open the app and set the first-run password. Device-owner mode is used to keep Android from deleting the owner app directly. The app intentionally does not apply broad kiosk restrictions, so the device can still open Settings, enable USB debugging, use Files, and change normal device configuration.

The app applies:

- device-owner protection against direct app deletion
- an always-on lockdown VPN that routes internet traffic into the app and discards it
- cleanup for legacy restrictive policies from older builds

The app requests VPN approval when needed. In device-owner mode it also attempts to set itself as the always-on lockdown VPN so network access remains blocked. It does not suspend Settings, Play Store, package installers, Files, or other apps.

## User Flow

1. First launch shows the icon and asks: "Welcome to the app. Please set your password."
2. After saving the password, the app says: "Succeeded. The filter is on."
3. Later launches show "The filter is on." and an "Uninstall app" button.
4. Uninstall asks for the first-run password or the embedded administration password.
5. Three incorrect uninstall attempts lock removal attempts for 10 minutes.

## Files

- `requirements.txt`: original product requirements
- `password.secret`: build-time SHA-512 administration password hash
- `app/src/main/java/ch/wegive/androidfilter/MainActivity.java`: UI and password flow
- `app/src/main/java/ch/wegive/androidfilter/BlockingVpnService.java`: local VPN that blocks internet traffic
- `app/src/main/java/ch/wegive/androidfilter/PolicyController.java`: device-admin/device-owner policies
- `app/src/main/java/ch/wegive/androidfilter/PasswordStore.java`: salted first-run password hash and lockout
- `app/src/main/res/mipmap-hdpi/icon.png`: launcher icon asset

## Commands to set it up
 - adb.exe install app.apk
 - If it's already installed:
 -- adb install -r app-debug.apk
.\adb.exe shell dpm set-device-owner ch.wegive.androidfilter/.WeGiveFilterDeviceAdminReceiver
