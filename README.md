# StillFrame TV

A calm Android TV Home launcher with a clock, favourite apps, configurable layouts and remote-first settings. Projectivy Launcher is not required.

![StillFrame TV — dock layout](docs/screenshots/full-dock.png)

![StillFrame TV — list left layout](docs/screenshots/list-left.png)

*Actual Android TV emulator screenshots, showing the dock and list-left layouts with an original StillFrame gradient.*

One project builds two editions:

| Feature | Full | Lite |
|---|:---:|:---:|
| Dock, list left and list right layouts | ✓ | ✓ |
| Clock, date, network and VPN indicators | ✓ | ✓ |
| Favourites, rearranging and app visibility | ✓ | ✓ |
| Android icon packs and per-app icon selection | ✓ | ✓ |
| Gradient backgrounds and imported photos | ✓ | ✓ |
| Selectable VPN widget controls (Proton tested) | ✓ | ✓ |
| Local video wallpapers | ✓ | — |
| Overflight / API-v1 video providers | ✓ | — |

Lite excludes the native video implementation, plugin API, Apple video trust configuration and Internet permission. Full also releases its video decoder when using photos; Lite should not be treated as a guaranteed large RAM saving.

## Status

Experimental development version **0.26.0**. The preceding Full implementation was tested on NVIDIA Shield with Android 11. These new Full/Lite packages need device validation. Sharp TV testing is pending.

The shareable source includes original CSS gradients instead of the personal installation's stock-photo collection. Import your own images through Appearance → Wallpaper gallery. Third-party icon packs, plugin APKs, aerial videos and account data are not bundled.

## Build

Use JDK 21 and Android SDK Platform 35. Set `ANDROID_HOME`, or create an untracked `local.properties` containing `sdk.dir=/path/to/sdk`.

```sh
./gradlew :app:assembleFullDebug :app:assembleLiteDebug
```

Outputs:

- `app/build/outputs/apk/full/debug/app-full-debug.apk`
- `app/build/outputs/apk/lite/debug/app-lite-debug.apk`

Full uses `dev.stillframe.tv`; Lite uses `dev.stillframe.tv.lite`. Both can be installed together, with independent settings. Updates require the same signing key. CI debug artifacts are for testing, not a stable release-signing identity.

## Install

```sh
adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk
# Or:
adb install -r app/build/outputs/apk/lite/debug/app-lite-debug.apk
```

Test app launching and remote controls before choosing StillFrame as Home. Keep a working stock launcher installed. StillFrame does not disable or uninstall system apps or other launchers.

## Controls

- D-pad moves selection; OK activates it.
- Hold OK on an app for Move icon / Change appearance. Back cancels moving.
- Home closes StillFrame menus.
- Play/Pause manages favourites where indicated.
- Settings → Appearance contains layout, wallpaper and icon controls.

See [setup and recovery](docs/SETUP.md), [wallpapers](docs/WALLPAPERS.md), [Proton VPN](docs/PROTON-VPN.md), [development](docs/DEVELOPMENT.md), [laptop preview](docs/LAPTOP-PREVIEW.md), and [known limitations](docs/KNOWN-ISSUES.md).

## Licensing

An open-source license for the original StillFrame code has not yet been selected. Repository visibility alone does not grant reuse rights. Third-party API notices and their Apache-2.0 license remain included in Full; see [notices](NOTICE.md).
