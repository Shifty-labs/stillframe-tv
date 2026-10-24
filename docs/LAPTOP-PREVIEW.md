# Laptop preview and screenshots

StillFrame is an Android TV app. Run it in an Android TV emulator on Linux; it is not a native Linux launcher.

This laptop has an Android 16 TV emulator named `StillFrame_TV`. Launch it with:

```sh
ANDROID_AVD_HOME="$HOME/.config/.android/avd" "$HOME/Android/Sdk/emulator/emulator" -avd StillFrame_TV -no-audio -gpu auto
```

Use arrow keys and Enter as a TV remote; Escape is Back. Install a built APK with `adb -s emulator-5554 install -r PATH_TO_APK`, then launch with `adb -s emulator-5554 shell am start -n dev.stillframe.tv/.MainActivity`. For Lite, use `dev.stillframe.tv.lite/.MainActivity`. Check `adb devices` first because emulator serials can differ. These commands must target the emulator, not a physical TV.

Capture the Android screen using Android Studio's emulator Screenshot button, or `adb -s emulator-5554 exec-out screencap -p > stillframe.png`. Emulator screenshots show the emulator's installed apps and may differ from a Shield. Do not include account details, notifications or personal photos in public screenshots.
