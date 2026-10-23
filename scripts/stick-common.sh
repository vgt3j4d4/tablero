# Shared by setup-stick.sh and restore-stick.sh (sourced, not run): what docs/stick-setup.md changes on the Xiaomi
# Mi TV Stick (MDZ-24-AA), and how to reach it. Bash 3.2 (macOS) compatible.

# ro.product.device of the Xiaomi stick. The scripts refuse any other device: the lists below were checked on its
# firmware (Xiaomi/aquaman:10/QT/2514), and Fire OS doesn't allow most of it (docs/stick-setup.md → Fire TV).
STICK_DEVICE=aquaman

STOCK_LAUNCHER=com.google.android.tvlauncher

# Removed for user 0 with `pm uninstall -k --user 0`; docs/stick-setup.md step 2 says what each one is. Only system
# apps: the APK stays on the stick and `cmd package install-existing` brings each one back with its data.
# setup-stick.sh skips any other app, since on this single-user stick removing it for user 0 deletes it.
REMOVED_PACKAGES=(
  com.google.android.youtube.tv
  com.google.android.youtube.tvmusic
  com.netflix.ninja
  com.amazon.amazonvideo.livingroom
  com.google.android.videos
  com.google.android.music
  com.google.android.play.games
  com.google.android.tvrecommendations
  com.google.android.backdrop
  com.google.android.apps.mediashell
  com.google.android.katniss
  com.google.android.feedback
  com.mitv.tvhome.atv
  com.mitv.tvhome.michannel
  com.mitv.tvhome.mitvplus
  com.miui.tv.analytics
  com.xiaomi.statistic
  com.xiaomi.mitv.updateservice
)

# Settings the setup changes, one per line: namespace, key, the setup's value, and the value the stick had before
# ("-" when unset), which restore-stick.sh puts back.
STICK_SETTINGS='
global window_animation_scale 0 1.0
global transition_animation_scale 0 1.0
global animator_duration_scale 0 -
secure screensaver_enabled 0 1
'

# Prints the usage: the script's header lines that start with "#   ".
usage() {
  sed -n 's/^#   //p' "$0" >&2
  exit 2
}

# Runs a shell command on the stick. adb shell would otherwise read stdin, eating the rest of a `while read` list.
stick() {
  adb -s "$STICK" shell "$@" </dev/null | tr -d '\r'
}

# Connects to the stick at $1 (<ip>[:port], port 5555 by default), sets STICK to its adb serial and checks that it is
# the Xiaomi.
connect_stick() {
  STICK=$1
  [[ $STICK == *:* ]] || STICK=$STICK:5555
  adb connect "$STICK" >/dev/null || true
  if [[ $(adb -s "$STICK" get-state 2>/dev/null) != device ]]; then
    echo "Can't reach $STICK over adb: is network debugging on, and was this computer allowed on the TV?" >&2
    exit 1
  fi
  local device
  device=$(stick getprop ro.product.device)
  if [[ $device != "$STICK_DEVICE" ]]; then
    echo "$STICK is '$device', not the Xiaomi Mi TV Stick ($STICK_DEVICE). Nothing changed." >&2
    exit 1
  fi
}

# Prints the activity HOME opens.
home_app() {
  stick cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1
}
