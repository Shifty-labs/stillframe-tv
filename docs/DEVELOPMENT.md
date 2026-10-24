# Development

- `app/src/main`: shared launcher, local HTML UI, photo/icon features, network indicators and Proton widget integration.
- `app/src/full`: video player/controller, provider API, Internet permission, video trust configuration and notices.
- `app/src/lite`: photo-only controller and Lite label.

The bootstrap exposes the edition's video capability to the UI. Lite hides video controls and excludes the implementation from its APK.

Use JDK 21, Android SDK Platform 35 and the checked-in wrapper:

```sh
./gradlew :app:assembleFullDebug :app:assembleLiteDebug
python3 scripts/check-source.py
```

Keep SDK paths, local media, signing keys and build outputs out of Git. GitHub Actions builds both debug variants and uploads test APK artifacts. Public release APKs are built separately and signed locally with a private persistent release key; GitHub Actions does not have that key. Release assets include APK SHA-256 checksums and the public signing-certificate fingerprint. Never upload the keystore or its password.

Packaging validation compiles both variants and checks Lite's manifest/DEX/assets for absence of video and provider code. Device validation should cover remote navigation, Home/Back, persistence, photo imports, icon packs, optional VPN setup and Full's video lifecycle/failure fallback.
