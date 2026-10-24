# Wallpapers

Both editions support gradients, imported photos, favourites and rotation. Appearance → Wallpaper gallery → Import wallpaper opens Android's file picker. Limits are currently 20 imports and 25 MB per source image. Images are copied into app-private storage at bounded resolution.

Full adds Appearance → Wallpaper source:

- Photos displays the selected gradient/image.
- Local video retains read access to one chosen video on storage or USB. Moving or deleting the file can break playback.
- Stillframe: Overflight appears when the separate Overflight plugin is installed.

Videos are muted and centre-cropped behind the clock/icons. The decoder is released on pause and recreated on resume. A failed startup reveals the selected photo. Provider playlists shuffle when loaded and advance at clip completion; Next video wallpaper skips manually. Selecting a gallery image returns to photos.

Provider settings opens the provider's quality/filter controls. Streaming uses internet data; no offline video-download cache is implemented.

Supported providers advertise `tv.projectivy.plugin.WALLPAPER_PROVIDER`, export their service, and declare API version 1. Current support covers time-elapsed requests and video entries, with up to 500 entries retained. Other wallpaper types and dynamic events are not supported. HTTPS and provider content URIs are accepted. Legacy Apple aerial HTTP entries are upgraded to HTTPS using the host-scoped Apple trust anchor.

Overflight: https://github.com/spocky/projectivy-plugin-wallpaper-overflight. Projectivy Launcher is not required. Enabling streaming is explicit.
