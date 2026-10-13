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

tv-app declares a `HOME` intent filter. Make it the **default** HOME but **keep the stock launcher installed**,
so the scorer can still reach Android TV through tablero's menu (*Exit to Android TV*, which launches the stock
launcher explicitly). Pressing HOME on the remote then always returns to tablero.

```bash
./gradlew :tv-app:installDebug
adb shell cmd package set-home-activity <tablero package>/<MainActivity class>   # verify on this firmware
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME  # should open tablero
# Rollback:
adb shell cmd package set-home-activity com.google.android.tvlauncher/<its HOME activity>
```

Stricter alternative (no way out without ADB): install tablero **first**, then
`adb shell pm disable-user --user 0 com.google.android.tvlauncher` (doing it the other way round leaves a black
screen; rollback with `pm enable`). A full lock-task kiosk (also blocks HOME) needs a device owner, which requires
removing the Google account — only worth it if accidental exits turn out to be a real problem.

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
- [ ] Confirm `cmd package set-home-activity` works on this firmware and record the exact command.
- [ ] Record what the Assistant / Netflix / Prime buttons do after debloat.
