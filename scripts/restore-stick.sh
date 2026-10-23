#!/usr/bin/env bash
# Undoes scripts/setup-stick.sh on the Xiaomi Mi TV Stick: brings back the removed apps, puts back the settings the
# stick had, and enables the stock launcher again (it is the home app at once). tablero stays installed.
#
#   scripts/restore-stick.sh <stick-ip>[:port]

set -euo pipefail
source "$(dirname "$0")/stick-common.sh"

[[ $# -eq 1 ]] || usage
connect_stick "$1"

echo "Bringing back apps:"
known=$(stick pm list packages -u)   # -u: with the ones removed for user 0
for package in "${REMOVED_PACKAGES[@]}"; do
  if grep -qxF "package:$package" <<< "$known"; then
    echo "  $package: $(stick cmd package install-existing "$package")"
  else
    echo "  $package: not on this stick"
  fi
done

echo "Settings:"
while read -r namespace key _ stock; do
  [[ -n $namespace ]] || continue
  if [[ $stock == - ]]; then
    stick settings delete "$namespace" "$key" >/dev/null
    echo "  $namespace $key unset"
  else
    stick settings put "$namespace" "$key" "$stock"
    echo "  $namespace $key = $stock"
  fi
done <<< "$STICK_SETTINGS"

stick pm enable "$STOCK_LAUNCHER" >/dev/null
echo "HOME opens: $(home_app)"
