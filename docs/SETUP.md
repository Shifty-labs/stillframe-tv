# Setup and recovery

Download the **Full** or **Lite** APK from [GitHub Releases](https://github.com/Shifty-labs/stillframe-tv/releases/latest). Both require Android TV / Google TV with Android 8.0 or newer. Do not download the Source code ZIP if you only want to install the app.

## Install with a file manager

1. Transfer the APK to the TV using a USB drive or your preferred file-transfer app.
2. Open the APK in the TV's file manager. If Android asks, allow **Install unknown apps** for that file manager.
3. Confirm Install, then open StillFrame. You can revoke the file manager's install permission afterwards.
4. Test navigation, app launching and returning Home. Then choose StillFrame as your Home app if the TV offers that setting.

## Install with ADB

Enable developer options and the TV's supported debugging mode. Enable network debugging only on your trusted network and accept the computer authorization on the TV. Some newer devices require Wireless debugging pairing; follow your TV's pairing screen rather than assuming port 5555.

For a TV exposing the traditional ADB port, replace DEVICE_ADDRESS below with its address and use the matching downloaded filename:

```sh
adb connect DEVICE_ADDRESS:5555
adb devices -l
adb -s DEVICE_ADDRESS:5555 install -r stillframe-lite-0.28.0.apk
# Or: adb -s DEVICE_ADDRESS:5555 install -r stillframe-full-0.28.0.apk
```

Always target the intended TV when multiple devices are connected. Disable debugging afterwards if you no longer need it.

## Home and recovery

Test the remote and app launching before selecting StillFrame as Home. Keep the stock launcher installed. Installation does not disable apps, grant widget-host permission, change system animations or alter network settings.

Some firmware prioritizes its stock launcher despite a saved Home preference. Record the original Home component before any device-specific changes. If the Google Android TV launcher was manually disabled on a Shield, the recovery used during development was:

```sh
adb shell pm enable --user 0 com.google.android.tvlauncher
adb shell cmd package set-home-activity --user 0 com.google.android.tvlauncher/.MainActivity
adb shell input keyevent KEYCODE_HOME
```

These package names do not apply to every TV. ADB-based recovery needs working ADB access.

Full and Lite have separate settings. An APK signed with a different key cannot update the existing app in place. Keep a working Home fallback before considering any uninstall, which also removes app settings.
