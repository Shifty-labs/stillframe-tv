# Setup and recovery

Connect ADB to your own device and install one edition:

```sh
adb connect DEVICE_ADDRESS:5555
adb devices -l
adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk
```

Test the remote and app launching before selecting StillFrame as Home. Keep the stock launcher installed. Installation does not disable apps, grant widget-host permission, change system animations or alter network settings.

Some firmware prioritizes its stock launcher despite a saved Home preference. Record the original Home component before any device-specific changes. If the Google Android TV launcher was manually disabled on a Shield, the recovery used during development was:

```sh
adb shell pm enable --user 0 com.google.android.tvlauncher
adb shell cmd package set-home-activity --user 0 com.google.android.tvlauncher/.MainActivity
adb shell input keyevent KEYCODE_HOME
```

These package names do not apply to every TV. ADB-based recovery needs working ADB access.

Full and Lite have separate settings. An APK signed with a different key cannot update the existing app in place. Keep a working Home fallback before considering any uninstall, which also removes app settings.
