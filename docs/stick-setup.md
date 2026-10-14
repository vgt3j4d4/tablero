# Preparing the Xiaomi Mi TV Stick

Goal: the stick boots straight into tablero, with as little background work as possible. Everything here is
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

## 3. Boot into tablero (kiosk)

tv-app declares a `HOME` intent filter, so it can be the stick's home app: the stick boots into the scoreboard and
HOME on the remote always returns to it.

**Making it the default doesn't work on this stick.** The stock launcher (`com.google.android.tvlauncher`, a system
app) declares its `HOME` filter with priority 2, and Android only looks at the chosen default when the best
candidates tie; an app that isn't a system app can't go above priority 0. So
`adb shell cmd package set-home-activity …` reports `Success`, `dumpsys package preferred` lists tablero, and HOME
still opens the stock launcher (checked on firmware `Xiaomi/aquaman:10/QT/2514`, Android 10).

What works is **disabling the stock launcher** (not uninstalling it). Install tablero **first**: the other way
round leaves the stick with no home screen but Settings' boot placeholder.

```bash
./gradlew :tv-app:installDebug                    # or the release APK
adb shell pm disable-user --user 0 com.google.android.tvlauncher
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
#   → net.pladum.tablero.tv[.debug]/net.pladum.tablero.tv.MainActivity
adb shell input keyevent KEYCODE_HOME            # should show the scoreboard

# Rollback (the stock launcher is home again at once):
adb shell pm enable com.google.android.tvlauncher
```

**Exit to Android TV** (tablero's menu, clock stopped, hold OK 2 s on EXIT) opens the stock launcher when it is
enabled. With it disabled there is no launcher to open, so it opens **Android TV Settings** instead (the side
panel); its *Apps* section is where installed apps, and the disabled launcher, can be reached (exact entries vary by
firmware: record them in the TODO below). Re-enabling the launcher there makes HOME open it again until it is
disabled over ADB. HOME from Settings returns to tablero. The exit screen's text follows what HOME will actually do: "Press HOME to come back" when tablero is the
home app, "Open tablero from the apps row" when it isn't.

**Recovery needs ADB.** With the launcher disabled, tablero is the only home app, so Android has nothing to fall
back to: if tablero ever failed on every start, the stick would keep showing it (or Settings, if it was open
underneath). Two crashes in a row (`adb shell am crash <package>`) simply restarted tablero on HOME with the game
restored. Before disabling the launcher, make sure network debugging comes back after a reboot
(`adb shell getprop persist.adb.tcp.port`; on this stick it was empty, so check by rebooting with the launcher still
enabled), or keep a way to plug in USB. The way out is then `pm enable com.google.android.tvlauncher` or
`pm uninstall <tablero package>`.

A full lock-task kiosk (also blocks HOME) needs a device owner, which requires removing the Google account — only
worth it if accidental exits turn out to be a real problem.

**Development:** `adb shell am start -n …/MainActivity` starts a second, ordinary copy of the activity next to the
home one (Android keeps home activities in their own task). Once tablero is the home app, open it with
`adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME` or `input keyevent KEYCODE_HOME`.

Accidental exits to check on the stick:

- HOME → returns to tablero (it is the default HOME).
- Back → handled by tablero (undo / menu); never finishes the activity.
- Assistant / Netflix / Prime buttons → should do nothing once their apps are removed in step 2 (verify).
- App crash or low-memory kill → Android relaunches the HOME app, and tablero restores the saved game state.

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
- [x] Confirm `cmd package set-home-activity` works on this firmware: it doesn't (see step 3); disable the launcher.
- [ ] Check that network debugging survives a reboot, then reboot with the launcher disabled: boots into tablero?
- [ ] With the remote: hold OK 2 s on EXIT, with the launcher enabled (opens it) and disabled (opens Settings);
  record how Settings → Apps opens an app and re-enables the launcher.
- [ ] Record what the Assistant / Netflix / Prime buttons do after debloat.
