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

Steps 2–4 are scripted. Install tablero first, or pass its APK:

```bash
scripts/setup-stick.sh <stick-ip> [--apk <tablero.apk>] [--no-kiosk]   # apps, settings, kiosk; harmless to rerun
scripts/restore-stick.sh <stick-ip>                                    # undoes all of it; tablero stays
```

Both refuse any device but this stick (`ro.product.device` = `aquaman`). The setup removes these apps for user 0
(`pm uninstall -k --user 0`), the final list for firmware `Xiaomi/aquaman:10/QT/2514`: removed in two batches, each
followed by a reboot and a check of every remote key. All are system apps, so the APK stays on the stick:
`adb shell cmd package install-existing <package>` brings one back with its data and its Play Store updates, and
`restore-stick.sh` brings back all of them (checked: the same 97 packages as before).

| Package                                | What it is                                                                   |
|----------------------------------------|------------------------------------------------------------------------------|
| `com.google.android.youtube.tv`        | YouTube                                                                      |
| `com.google.android.youtube.tvmusic`   | YouTube Music                                                                |
| `com.netflix.ninja`                    | Netflix (~130 MB while it ran in the background)                             |
| `com.amazon.amazonvideo.livingroom`    | Prime Video                                                                  |
| `com.google.android.videos`            | Google Play Movies                                                           |
| `com.google.android.music`             | Google Play Music                                                            |
| `com.google.android.play.games`        | Google Play Games                                                            |
| `com.google.android.tvrecommendations` | The launcher's recommendations                                               |
| `com.google.android.backdrop`          | Ambient Mode screensaver                                                     |
| `com.google.android.apps.mediashell`   | Chromecast built-in                                                          |
| `com.google.android.katniss`           | Google app: Assistant and voice search (~40 MB)                              |
| `com.google.android.feedback`          | Google feedback                                                              |
| `com.mitv.tvhome.atv`                  | Xiaomi content service (starts on boot)                                      |
| `com.mitv.tvhome.michannel`            | Xiaomi launcher channels                                                     |
| `com.mitv.tvhome.mitvplus`             | Mi TV+ channels                                                              |
| `com.miui.tv.analytics`                | Xiaomi analytics                                                             |
| `com.xiaomi.statistic`                 | Xiaomi usage statistics                                                      |
| `com.xiaomi.mitv.updateservice`        | Xiaomi firmware updates (OTA): the firmware stays the one checked here       |

About 320 MB of the 1 GB were available two minutes after a reboot with tablero on screen (about 200 MB before,
with Netflix running in the background).

Left in place on purpose:

- **Apps that aren't system apps** (`pm list packages -3`): DirecTV, ESPN, Disney+ and a Play auto-install config
  (`android.autoinstalls.config.xiaomi.mibox3`), installed after the first boot. On this single-user stick, removing
  one for user 0 deletes it (only the Play Store brings it back), and none of them runs in the background.
- **Apps that never run** (gallery, camera, print spooler, TalkBack…): they only take storage.
- **Never remove:** Google Play services (`com.google.android.gms`), TV Settings (`com.android.tv.settings`), the
  Bluetooth and remote packages (`com.mi.bluetooth`, `com.google.android.tv.remote.service`, and
  `com.xiaomi.android.tvsetup.partnercustomizer`, which has the remote's pairing screens and handles its Netflix and
  Prime Video buttons), the package installer and the keyboard (`com.google.android.inputmethod.latin`). Keep the
  Play Store (`com.android.vending`) but turn auto-updates off (step 4).

To change the list, remove one batch by hand, reboot and check every remote key before adding it to
`scripts/stick-common.sh`.

## 3. Opening tablero

**Setup in use: tablero is the home app (kiosk).** tv-app declares a `HOME` intent filter, and `setup-stick.sh`
disables the stock launcher (`com.google.android.tvlauncher`), so the stick boots into the scoreboard and HOME always
returns to it. tablero's menu has **Exit** (clock stopped, hold OK 2 s on EXIT); its screen tells the scorer how to
come back.

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
- Back is handled by tablero (undo / menu) and never finishes the activity.
- The remote's other buttons do nothing (checked with a key logger after step 2):
  - **Assistant** sends `KEYCODE_ASSIST`, and with the Google app removed nothing handles it.
  - **Netflix** and **Prime Video** send `KEYCODE_BUTTON_6` and `KEYCODE_BUTTON_7`, global keys that the partner
    customizer's `GlobalKeyReceiver` turns into opening the app. With the apps removed nothing opens (on Netflix the
    receiver crashes in the background and Android restarts it).
  - **Apps** (the grid button) sends `KEYCODE_ALL_APPS`. Android opens the system home app's app list, and with the
    launcher disabled that is TV Settings, which has none.
- Exit has no launcher to open, so it opens **Android TV Settings** (the side panel). Settings can still open the
  apps left on the stick (*Apps → See all apps → app → Open*). The launcher's page (*Apps → See all apps → system
  apps → Pantalla principal de Android TV*) offers **Enable**, which turns the kiosk off; Settings offers no Disable
  for the home app, so turning it back on needs `pm disable-user` over ADB (or `setup-stick.sh`).
- With the launcher disabled tablero is the only home app, so if it ever failed on every start there would be
  nothing to fall back to: recovery is `pm enable com.google.android.tvlauncher` or uninstalling tablero over ADB.

A full lock-task kiosk (also blocks HOME) needs a device owner, which requires removing the Google account.

**Development:** while tablero is the home app, `adb shell am start -n …/MainActivity` starts a second, ordinary copy
of the activity next to the home one (Android keeps home activities in their own task); use
`adb shell input keyevent KEYCODE_HOME` instead.

### Alternative: the stock launcher as home

`setup-stick.sh --no-kiosk` leaves the launcher enabled, and the rollback above turns an existing kiosk off. tablero is
then an ordinary app opened from the launcher's apps row, for a stick that also runs other apps:

- HOME goes to the launcher. The game keeps going meanwhile (the engine and the buzzer live in the app process, and
  the state is saved on every change), and opening tablero again shows it as it is.
- Exit opens the launcher (checked with the remote), and its screen tells the scorer to open tablero from the apps
  row.
- An app crash or a low-memory kill closes tablero until it is opened again, with the game restored.

## 4. Settings

`setup-stick.sh` turns off the animations and the screensaver, and `restore-stick.sh` puts back the stick's own
values (both from `STICK_SETTINGS` in `scripts/stick-common.sh`).

The sleep timers stay as they are. This firmware's TV Settings has no **Never** for *Put device to sleep* (5 minutes
to 2 hours), and none is needed: while tablero is on screen its `FLAG_KEEP_SCREEN_ON` holds a screen wake lock
(`dumpsys power` lists a `SCREEN_BRIGHT_WAKE_LOCK` from `WindowManager` for tablero's uid), so the stick never sleeps
during a game. Anywhere else, such as Settings after Exit, the screen turns off after 15 idle minutes.

By hand, in the Settings UI: Play Store auto-update **off** (with the kiosk on, open the Play Store from *Settings →
Apps → See all apps → Google Play Store → Open*) and usage & diagnostics **off**. Pair/remember the referee phone's
hotspot.

## 5. Measure

```bash
adb shell dumpsys meminfo
adb shell top -m 10
adb shell dumpsys gfxinfo <tv-app package>
```

## Fire TV Stick 2nd gen

The spare Amazon stick (AFTT, Fire OS 5.2 = Android 5.1) runs tablero too. Debloating and the kiosk above are only
written for the Xiaomi; on the Fire TV, open tablero from **Your Apps & Games** (see *Debloat and kiosk* below for
what Fire OS allows).

1. Settings → **My Fire TV** (called **Device** on older software) → **Developer options**. If it isn't listed,
   open **About** and select the stick's name 7 times.
2. Turn on **ADB debugging** and **Apps from Unknown Sources**.
3. Settings → My Fire TV → About → **Network** shows the stick's IP. It is not the router's address (often `.1`);
   if unsure, look for the device with port 5555 open.
4. `adb connect <fire-ip>:5555` and accept **Allow USB debugging?** on the TV ("Always allow from this computer").

Checked on Fire OS 5.2.9.5: install and launch, fonts, 10 Hz clock (~3 ms per frame), restore after a kill with
the clock running, buzzer, menu on Back long press (via `input keyevent --longpress`), and HOME going straight to
the Fire launcher (no home-app chooser).

### Debloat and kiosk

Fire OS doesn't let the shell touch Amazon's apps: `pm disable-user` fails with `SecurityException: Permission
Denial: attempt to change component state`, and `pm hide` answers `new hidden state: false` and changes nothing
(checked on Fire OS 5.2.9.5 with the launcher, the screensaver and Live TV). So:

- **tablero can't be the home app.** HOME always opens the Amazon launcher (`com.amazon.tv.launcher`), the only way
  to beat it would be disabling it, and the shell can't. Avoid `pm uninstall -k --user 0` on Amazon apps: Android 5.1
  has no `install-existing`, so only a factory reset brings them back.
- **Only apps installed from the Appstore can be removed**, with a plain uninstall (reinstall them from the Appstore):

  ```bash
  adb shell pm list packages -3        # tablero plus whatever was installed from the Appstore
  adb shell pm uninstall <package>     # e.g. com.netflix.ninja, com.amazon.firetv.youtube
  ```

  Apps that aren't running only take storage, so this frees little memory.
- **Settings the shell can change:**

  ```bash
  adb shell settings put global window_animation_scale 0
  adb shell settings put global transition_animation_scale 0
  adb shell settings put global animator_duration_scale 0
  adb shell settings put secure screensaver_enabled 0   # the screensaver process alone took ~100 MB
  # Undo: put 1 instead of 0.
  ```

Most of the RAM stays with Amazon's services (launcher ~100 MB, Alexa, metrics, Live TV…): on the stick, with
tablero in front, about 200 MB of the 0.9 GB were available after the steps above.

## TODO

- [x] `scripts/setup-stick.sh` and `scripts/restore-stick.sh` automating steps 2–4.
- [x] Record the final package list for this stick's firmware (step 2).
- [x] Confirm `cmd package set-home-activity` works on this firmware: it doesn't (see step 3).
- [x] Hold OK 2 s on EXIT with the remote (checked with the launcher disabled: opens Settings).
- [x] Hold OK 2 s on EXIT with the launcher enabled: opens the launcher.
- [x] Record what the Assistant / Netflix / Prime buttons do after debloat (step 3: nothing).
- [ ] Fire TV: Back long press and hold OK 2 s on EXIT with the Fire TV remote; EXIT should open the Fire launcher.
