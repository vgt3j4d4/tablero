# Preparing the Xiaomi Mi TV Stick

Goal: the stick runs tablero with as little background work as possible. Everything here is
**root-free and reversible** (a factory reset restores the original state).

## 1. Enable ADB

Settings → Device Preferences → About → click **Build** 7 times → Developer options →
enable **USB debugging** / **Network debugging**.

```bash
adb connect <stick-ip>:5555
adb shell pm list packages -s      # system packages
adb shell dumpsys meminfo | head -60
```

## 2. Remove unused apps (per-user uninstall)

```bash
# Typical candidates on Android TV 9 — confirm names with `pm list packages` first.
for p in \
  com.google.android.youtube.tv \
  com.netflix.ninja \
  com.amazon.amazonvideo.livingroom \
  com.google.android.videos \
  com.google.android.music \
  com.google.android.play.games \
  com.google.android.tvrecommendations \
  com.google.android.backdrop \
  com.google.android.apps.mediashell \
  com.google.android.katniss \
  com.google.android.feedback
do adb shell pm uninstall -k --user 0 "$p"; done

# Undo one:
adb shell cmd package install-existing <package>
```

Do one batch, reboot, and confirm the remote still works before removing more.

**Never remove:** Google Play services (`com.google.android.gms`), TV Settings (`com.android.tv.settings`),
Bluetooth / remote-related packages, the package installer, the keyboard (`leanback.ime`).
Keep the Play Store (`com.android.vending`) but turn auto-updates off.

## 3. Opening tablero

**Setup in use:** the stock launcher (`com.google.android.tvlauncher`) stays the home screen, and tablero is an
ordinary app opened from its apps row. HOME on the remote goes to the launcher; the game keeps going meanwhile (the
engine and the buzzer live in the app process, and the state is saved on every change), and opening tablero again
shows it as it is. tablero's menu has **Exit to Android TV** (clock stopped, hold OK 2 s on EXIT), which opens the
launcher; its screen tells the scorer to open tablero from the apps row to come back.

### Optional: tablero as the home app (kiosk)

tv-app declares a `HOME` intent filter, so it can be the stick's home app instead: the stick boots into the
scoreboard and HOME always returns to it. We don't use this, because it leaves no way to reach other apps without
ADB, but it is what to try if accidental exits become a real problem.

**Making tablero the default doesn't work on this stick.** The stock launcher, a system app, declares its `HOME`
filter with priority 2, and Android only looks at the chosen default when the best candidates tie; an app that isn't
a system app can't go above priority 0. So `adb shell cmd package set-home-activity …` reports `Success`,
`dumpsys package preferred` lists tablero, and HOME still opens the stock launcher (checked on firmware
`Xiaomi/aquaman:10/QT/2514`, Android 10). The shell can't disable just the launcher's home activity either
(`SecurityException`), so the only way is **disabling the whole launcher** (not uninstalling it). Install tablero
**first**: the other way round leaves the stick with no home screen but Settings' boot placeholder.

```bash
adb shell pm disable-user --user 0 com.google.android.tvlauncher
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
#   → net.pladum.tablero.tv[.debug]/net.pladum.tablero.tv.MainActivity
# Rollback (the stock launcher is home again at once):
adb shell pm enable com.google.android.tvlauncher
```

Checked on the stick with the launcher disabled:

- A power cycle boots into tablero, and network debugging comes back (`persist.adb.tcp.port` is empty, yet
  `adb connect` worked right after it).
- HOME returns to tablero, from Settings too. Two crashes in a row (`adb shell am crash <package>`) just restarted
  tablero on HOME with the game restored.
- Exit to Android TV has no launcher to open, so it opens **Android TV Settings** (the side panel), which can't open
  other apps. The launcher's page (*Apps → See all apps → system apps → Pantalla principal de Android TV*) offers
  **Enable**, which turns the kiosk off; Settings offers no Disable for the home app, so turning it back on needs
  `pm disable-user` over ADB.
- With the launcher disabled tablero is the only home app, so if it ever failed on every start there would be
  nothing to fall back to: recovery is `pm enable com.google.android.tvlauncher` or uninstalling tablero over ADB.

A full lock-task kiosk (also blocks HOME) needs a device owner, which requires removing the Google account.

**Development:** while tablero is the home app, `adb shell am start -n …/MainActivity` starts a second, ordinary copy
of the activity next to the home one (Android keeps home activities in their own task); use
`adb shell input keyevent KEYCODE_HOME` instead.

Accidental exits to check on the stick:

- HOME → the stock launcher; the clock keeps running and the buzzer still sounds while Android keeps tablero in
  memory. Open tablero from the apps row.
- Back → handled by tablero (undo / menu); never finishes the activity.
- Assistant / Netflix / Prime buttons → should do nothing once their apps are removed in step 2 (verify).
- App crash or low-memory kill → tablero is gone until it is opened again, then restores the saved game state.

## 4. Settings

```bash
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
```

In the Settings UI: screensaver **off**, "put device to sleep" **Never**, Play Store auto-update **off**,
usage & diagnostics **off**. Pair/remember the referee phone's hotspot.

## 5. Measure

```bash
adb shell dumpsys meminfo
adb shell top -m 10
adb shell dumpsys gfxinfo <tv-app package>
```

## TODO

- [ ] `scripts/setup-stick.sh` and `scripts/restore-stick.sh` automating steps 2–4.
- [ ] Record the final package list for this stick's firmware here.
- [x] Confirm `cmd package set-home-activity` works on this firmware: it doesn't (see step 3).
- [x] Hold OK 2 s on EXIT with the remote (checked with the launcher disabled: opens Settings).
- [ ] Hold OK 2 s on EXIT with the launcher enabled: opens the launcher.
- [ ] Record what the Assistant / Netflix / Prime buttons do after debloat.
