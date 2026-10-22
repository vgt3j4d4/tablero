#!/usr/bin/env bash
# Prepares the Xiaomi Mi TV Stick for tablero: docs/stick-setup.md steps 2–4.
#
#   scripts/setup-stick.sh <stick-ip>[:port] [--apk <tablero.apk>] [--no-kiosk]
#
# Installs the APK when given, removes the unused apps for user 0, turns off animations and the screensaver, and makes
# tablero the home app by disabling the stock launcher (the kiosk; --no-kiosk keeps the launcher). Running it again is
# harmless. scripts/restore-stick.sh undoes all of it but the install.

set -euo pipefail
source "$(dirname "$0")/stick-common.sh"

TABLERO_PACKAGE=net.pladum.tablero.tv   # also matches the .debug build

[[ $# -ge 1 ]] || usage
address=$1
shift
apk=
kiosk=true
while [[ $# -gt 0 ]]; do
  case $1 in
    --apk) [[ $# -ge 2 ]] || usage; apk=$2; shift 2 ;;
    --no-kiosk) kiosk=false; shift ;;
    *) usage ;;
  esac
done

connect_stick "$address"

if [[ -n $apk ]]; then
  echo "Installing $apk"
  adb -s "$STICK" install -r "$apk"
fi
# With the launcher disabled, tablero is the only home app: without it the stick would have no home screen.
if $kiosk; then
  home_candidates=$(stick cmd package query-activities --brief -a android.intent.action.MAIN \
    -c android.intent.category.HOME)
  if ! grep -qF "$TABLERO_PACKAGE" <<< "$home_candidates"; then
    echo "tablero isn't installed: pass --apk, or run ./gradlew :tv-app:installDebug first. Nothing changed." >&2
    exit 1
  fi
fi

echo "Removing apps for user 0:"
installed=$(stick pm list packages -s)   # -s: system apps only
for package in "${REMOVED_PACKAGES[@]}"; do
  if grep -qxF "package:$package" <<< "$installed"; then
    echo "  $package: $(stick pm uninstall -k --user 0 "$package")"
  else
    echo "  $package: already removed, or not a system app"
  fi
done

echo "Settings:"
while read -r namespace key value _; do
  [[ -n $namespace ]] || continue
  stick settings put "$namespace" "$key" "$value"
  echo "  $namespace $key = $value"
done <<< "$STICK_SETTINGS"

if $kiosk; then
  stick pm disable-user --user 0 "$STOCK_LAUNCHER" >/dev/null
  stick input keyevent KEYCODE_HOME
fi
echo "HOME opens: $(home_app)"

cat <<EOF

Still by hand (docs/stick-setup.md step 4): Play Store auto-update off, Usage & diagnostics off.
Then reboot the stick (adb -s $STICK reboot) and check that the remote works.
EOF
