# Known limitations

- Two activities can remain in separate Home and ordinary-app tasks after explicit launches. Consolidation is a follow-up; a memory leak has not been established.
- Short development samples measured approximately 12–13% total four-core Shield CPU usage with video and 1–2% with a static background. These are not Lite benchmarks or guarantees for other devices. Cached textures and WebView processes affect memory measurements.
- The new two-edition packages need device validation. Sharp TV testing is pending.
- Photo import is one image at a time, limited to 20 images.
- Icon packs use standard mappings/catalogues. Generated masks, dynamic calendars and all launcher-specific extensions are not implemented.
- Full supports API-v1 video entries, not every Projectivy plugin type. There is no offline video cache.
- Proton integration depends on its widget version, sign-in, Android VPN consent and widget-host permission.
- Stable release signing, update distribution and an original-source license remain to be selected.
