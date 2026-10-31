# Home Launcher for Google TV

A clean, remote-friendly home screen for Google TV / Android TV with Home Assistant built in.

```
┌──────────────────────────────────────────────────────────────────────────┐
│ Good evening                                ☀ 18°  │ Sat Sun Mon Tue Wed  ⚙ │
│ 19:42                                    Clear     │  ☁   ☂   ☀   ☀   ☁     │
│ Saturday 31 October                      Home      │ 17° 15° 19° 20° 16°    │
│                                                                          │
│ Home                                                                     │
│ [💡 Living room] [💡 Kitchen] [🔌 Lamp] [🎨 Movie night] [🌡 21° → 22°] [+] │
│                                                                          │
│ Apps                                                                     │
│ [ Netflix ] [ YouTube ] [ Plex ] [ Disney+ ] [ Spotify ] [ Settings ] …   │
└──────────────────────────────────────────────────────────────────────────┘
```

## Features

- **Clock, date and greeting** in large, easy-to-read type.
- **Weather** – current conditions plus a 5-day forecast from [Open-Meteo](https://open-meteo.com)
  (free, no API key). Uses your Home Assistant home location automatically, or any city you pick.
  The background glow subtly follows the weather and time of day.
- **Home Assistant** – live tiles over the WebSocket API (instant updates, auto-reconnect).
  - Press select to toggle lights, switches, fans, covers, input booleans, run scenes/scripts,
    press buttons, play/pause media, start a vacuum.
  - Hold select for more: light brightness presets, thermostat up/down, open/close covers,
    lock/unlock (locks never toggle on a single press), reorder or remove a tile.
  - Sensors show their value and unit.
- **Your installed apps** along the bottom using each app's TV banner. Hold select on an app to
  move it, hide it, or open App info. New installs/uninstalls appear automatically.
- **Phone setup** – while Settings is open the TV shows an address like
  `http://192.168.1.50:8099`; open it on your phone to paste your Home Assistant URL and token
  instead of typing a 180-character token with a remote.

## Install

1. Get the APK:
   - From the **Releases** page of this repo (built automatically on every push to `main`), or
   - From the latest **Actions → Build TV launcher** run (download the `home-launcher-apk` artifact).
2. Sideload it, e.g. with the *Downloader* app on the TV, *Send Files to TV*, or adb:
   `adb install -r home-launcher.apk`
3. Open **Home Launcher** from your apps, then go to Settings (gear icon, top-right).

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
3. Choose **Devices on the home screen** and tick the entities you want.

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
