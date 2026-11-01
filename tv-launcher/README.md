# Home Launcher for Google TV

A clean, remote-friendly home screen for Google TV / Android TV with Home Assistant built in.

```
┌──────────────────────────────────────────────────────────────────────────┐
│ Home  Cameras  Lights  Apps          (💡 TV Lights) (🎬 Movie)  ⚙  17:04 │
│ ┌──────────────────────────────────┐ ┌──────────────────────────────┐   │
│ │                                  │ │ ☀ 11°  Sunny  11°/4° · no rain│   │
│ │       live camera  ‹  ›          │ │ 17:00 18:00 19:00 20:00 …     │   │
│ │                                  │ ├──────────────────────────────┤   │
│ │                                  │ │ Outside                       │   │
│ │                                  │ │  Garden PM 10        18.0 ppb │   │
│ │                    Garden cam    │ │ Kitchen                       │   │
│ └──────────────────────────────────┘ │  Kitchen PM 10        5.0 ppb │   │
│                                      └──────────────────────────────┘   │
│ [YouTube] [Netflix] [Prime] [Jellyfin] [HBO Max] [Apple TV] …           │
└──────────────────────────────────────────────────────────────────────────┘
```

## Features

- **Tabs** – Home, Cameras, Lights and Apps along the top, with the clock top-right.
- **Quick buttons** – up to three scenes, scripts, lights or switches in the header
  (e.g. "TV Lights"). Select runs/toggles them; hold to reorder or remove.
- **Live camera** – the Home tab plays a Home Assistant camera live (HLS via HA's stream
  integration, muted). Left/right on the panel switches camera; select opens it full screen.
  If a camera can't stream, it falls back to refreshing snapshots. Video stops when another
  app is open.
- **Weather** – current conditions, today's high/low and rain chance, and the next 6 hours,
  from [Open-Meteo](https://open-meteo.com) (free, no API key). Uses your Home Assistant home
  location automatically, or any city you pick.
- **Sensors panel** – the sensors you choose, grouped by their Home Assistant areas.
- **Lights tab** – every light in Home Assistant, grouped by area and showing its current
  colour. Select toggles; **hold** opens a panel with on/off, a brightness bar (left/right in
  10% steps), white temperatures (candle → daylight) and colours, depending on what the bulb
  supports.
- **Cameras tab** – all cameras as snapshots; select for full-screen live view.
- **Apps** – your installed apps along the bottom of the Home tab using each app's TV banner,
  and the Apps tab shows them all in a grid. Hold select on an app to move it, hide it, or
  open App info. New installs/uninstalls appear automatically.
- **Hold-to-open menus** ignore the held button, so they stay open after you let go.
- **Phone setup** – while Settings is open the TV shows an address like
  `http://192.168.1.50:8099`; open it on your phone to paste your Home Assistant URL and token
  instead of typing a 180-character token with a remote.
- **Built for the Chromecast with Google TV** – precompiled UI code (baseline profile), live
  updates only for what's on screen, and lightweight rendering.

## Install

1. On the TV, install **Downloader** (by AFTVnews) from the Play Store.
2. In Downloader, enter this URL and install when prompted (allow Downloader to install
   unknown apps if asked):
   `https://github.com/LORDCB/LORDCB.github.io/releases/latest/download/home-launcher.apk`
   Every build publishes a new release, so this link always gives the newest version.
   (Alternatively: `adb install -r home-launcher.apk`.)
3. Open **Home Launcher** from your apps, then open **Settings** (gear icon, top-right).
4. Optional, for the smoothest experience straight away (needs adb):
   `adb shell cmd package compile -m speed-profile -f com.lordcb.homelauncher`
   Otherwise Android does this by itself the next time the TV is idle.

### Make it your home screen

Google TV doesn't offer a "choose launcher" option in its settings, so the Home button keeps
opening the stock launcher. Two options:

- **Recommended (reversible):** use a button-remapper app such as *Button Mapper* or *Projectivy
  Launcher's* override feature to send the Home button to Home Launcher.
- **Advanced:** disable the stock launcher with adb (re-enable any time with `pm enable`):
  ```sh
  adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
  adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
  ```
  Android will then use Home Launcher as the home app. To undo:
  ```sh
  adb shell pm enable com.google.android.apps.tv.launcherx
  adb shell pm enable com.google.android.tungsten.setupwraith
  ```

## Connect Home Assistant

1. In Home Assistant open your **Profile → Security → Long-lived access tokens → Create token**.
2. On the TV open Settings. Either use the phone-setup address shown on screen, or enter the
   *Server address* (e.g. `http://homeassistant.local:8123` or `http://192.168.1.10:8123`) and
   *Long-lived access token* with the remote.
3. Choose your **Quick buttons** and **Sensors panel** entries. Lights and cameras are picked
   up automatically.

## Build it yourself

Requires JDK 17 and the Android SDK (API 35).

```sh
cd tv-launcher
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
```

Release builds are signed with the debug key unless `LAUNCHER_KEYSTORE`,
`LAUNCHER_KEYSTORE_PASSWORD`, `LAUNCHER_KEY_ALIAS` and `LAUNCHER_KEY_PASSWORD` are set.
For CI, add the keystore as the base64 secret `LAUNCHER_KEYSTORE_BASE64` plus the three
password/alias secrets — then every build can be installed over the previous one.

## Project layout

```
app/src/main/java/com/lordcb/homelauncher/
├── MainActivity.kt            Screen navigation, Home/Back handling
├── LauncherViewModel.kt       Glue between data sources and UI
├── data/
│   ├── HomeAssistant.kt       WebSocket client (states, events, service calls)
│   ├── Weather.kt             Open-Meteo forecast + city search
│   ├── Apps.kt                Installed app discovery & banners
│   ├── Settings.kt            Persisted preferences
│   └── ConfigServer.kt        Tiny LAN page for phone-based setup
└── ui/
    ├── HomeScreen.kt          Clock, weather, HA tiles, app row
    ├── SettingsScreen.kt      Settings
    ├── EntityPickerScreen.kt  Choose Home Assistant entities
    ├── Components.kt, Icons.kt, Theme.kt
```
