# Multi TG — Multi-screen news app for Android TV

**Multi TG** turns an Android TV into a "newsroom wall": it plays **2 or 4 live TV channels (HLS `.m3u8` streams) side by side**, letting you pick which one you hear, jump to fullscreen, and set a sleep timer — all from the remote control.

## What it's for

- Watch several news channels at once (e.g. national + international + 24h news) and switch audio between them with a single key.
- Keep a TV running unattended as an always-on multi-channel monitor: streams auto-reconnect, the screen never sleeps, and the app can start by itself when the TV powers on.
- Works with any HLS live stream, so it is not tied to a specific broadcaster.

Built with Kotlin and [Media3 / ExoPlayer](https://developer.android.com/media/media3). Tested on a Xiaomi Fire TV (Android 9); minimum API 26.

## Features

- **2 or 4 tile grid**, each tile an independent player.
- **Audio selection**: only one tile plays sound; the others are muted (audio track disabled to save resources).
- **Fullscreen** on the selected tile; other players are paused to keep the TV responsive.
- **Quality cap** per tile (Low / Medium / High) for low-power TVs.
- **Sleep timer**: 15 / 30 / 60 / 90 / 120 minutes (countdown shown in the corner, app closes when it expires).
- **Editable channels**: name and URL of the 4 channels, saved permanently.
- **Auto-restart** of a stream every 4 seconds on error or interruption.
- **Auto-launch on boot / screen on** (foreground service, toggle in the menu).
- On-screen clock, keep-screen-on.

## Remote control

| Key | Action |
|---|---|
| Arrows | Move between tiles (yellow border = selected) |
| **OK** on a tile | Give the **audio** to that channel (🔊 icon) |
| **OK** on the tile that already has audio | Open it **fullscreen** |
| **BACK** | Fullscreen → back to grid; from the grid, asks to exit |
| **Long-press OK** (or MENU) | Open the **menu** |

## Menu (long-press OK)

- **Layout 2 / 4 tiles**
- **Tile quality** Low / Medium / High
- **Sleep timer**
- **Edit channels** — name and `.m3u8` URL of each channel
- **Auto-start on boot** on/off
- **Restart all streams**
- **Exit**

## Channels

Stream URLs are **not included** in this repository. The default entries in
[`MainActivity.kt`](MultiTG/app/src/main/java/com/donarosso/multitv/MainActivity.kt) are placeholders (`INSERISCI_URL_M3U8`): replace them with your own HLS URLs before building, or enter them on the TV via **Menu → Edit channels**. Typing long URLs with the remote is tedious: do it once (they are saved), or use a USB keyboard or ADB.

## Building

Requirements: JDK 17, Android SDK, Gradle 8.x.

```
export JAVA_HOME=/opt/homebrew/opt/openjdk@17   # or your JDK 17 path
cd MultiTG
gradle assembleDebug     # APK in app/build/outputs/apk/debug/
```

## Installing on the TV

**USB stick / "Send Files to TV" / Downloader app:** copy the APK to the TV, open it with a file manager and install (enable "Unknown sources" if asked).

**ADB** (TV and computer on the same network, network debugging enabled on the TV):
```
adb connect TV_IP
adb install app-debug.apk
```

## Notes

- Some broadcasters require a browser-like User-Agent or an explicit HLS MIME type; the player already sends a desktop Chrome User-Agent.
- Low-end TVs may not handle 4 HD streams: use the **Low** quality cap and/or the 2-tile layout.
