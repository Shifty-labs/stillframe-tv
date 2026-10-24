# Optional VPN controls

The indicator reports Android's VPN transport. The button delegates to Proton VPN's own widget; StillFrame does not store Proton credentials or implement a VPN.

Install Proton, sign in there, connect once to complete Android VPN consent, and set Quick Connect to Fastest. On Shield, the one-time widget-host setup used was:

```sh
adb shell appwidget grantbind --package dev.stillframe.tv --user 0
# For Lite, use dev.stillframe.tv.lite.
```

Off requests Fastest; connected requests Proton's Disconnect action. Repeated requests are guarded while pending. Proton updates may require compatibility changes.

Choose **Settings → VPN controls** to select an installed VPN app's Android widget. Proton remains the tested default; other providers are experimental. They must expose one clear Connect or Disconnect control through their widget. Unsupported or ambiguous widgets are refused. Other providers use their app's default connection, rather than Proton's Fastest setting. Selecting a provider does not change the current connection. The indicator reports any active Android VPN, while the button controls the selected provider; if another VPN is active, open its own app to manage it. It is not a kill switch or a guarantee of protected traffic, and cannot bypass sign-in or VPN consent.

To revoke permission to create new widget bindings:

```sh
adb shell appwidget revokebind --package dev.stillframe.tv --user 0
```

Android can preserve existing widget bindings after revocation. Removing those requires host cleanup or app-data removal; the latter also affects saved settings.

Version 0.26 adds provider selection. Both editions compile; end-to-end third-party widget compatibility is not yet validated. TV-only VPN apps without Android widgets are not supported by this integration.
