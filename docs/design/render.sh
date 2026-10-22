#!/usr/bin/env bash
# Renders the boards in boards.html to docs/design/<lang>/<NN>-<board>.png (1920×1080) with headless Chrome, several
# at once.
# Usage: ./render.sh                 every board, in every language
#        ./render.sh 06 fiba-menu    only these boards, by number or by name
# Set CHROME=/path/to/chrome if it is not in the default macOS location, JOBS=n to change how many render at once.
set -euo pipefail

CHROME="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
DIR="$(cd "$(dirname "$0")" && pwd)"
JOBS="${JOBS:-$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4)}"
# Numbers are fixed: issues and docs link to the files, so a removed board leaves a gap (04 was the next-game picker).
BOARDS=(
  01-live-game
  02-final-minute
  03-time-up
  05-action-feedback
  06-menu
  07-quick-clock-adjust
  08-set-game-clock
  09-exit-confirm
  10-remote-mapping
  11-large-live-game
  12-large-time-up
  13-large-quick-clock-adjust
  14-outdoor-live-game
  15-outdoor-large-live-game
  16-outdoor-time-up
  17-outdoor-action-feedback
  18-outdoor-quick-clock-adjust
  19-outdoor-menu
  20-possession
  21-large-possession
  22-outdoor-possession
  23-possession-feedback
  24-fiba-live-game
  25-fiba-large-live-game
  26-outdoor-fiba-live-game
  27-fiba-team-foul
  28-fiba-foul-feedback
  29-fiba-period-end
  30-fiba-halftime
  31-fiba-overtime
  32-fiba-menu
  33-fiba-remote-mapping
)
LANGS=(en es)

# The boards to render: those named as arguments (06 or menu for 06-menu), or all of them.
selected=()
if (($#)); then
  for wanted in "$@"; do
    found=""
    for entry in "${BOARDS[@]}"; do
      if [[ "$wanted" == "${entry%%-*}" || "$wanted" == "${entry#*-}" || "$wanted" == "$entry" ]]; then
        found="$entry"
        break
      fi
    done
    if [[ -z "$found" ]]; then
      echo "No board $wanted" >&2
      exit 1
    fi
    selected+=("$found")
  done
else
  selected=("${BOARDS[@]}")
fi

# One Chrome per board and language; each needs its own profile directory to run next to the others.
render() {
  local lang="$1" entry="$2" profile
  profile="$(mktemp -d)"
  "$CHROME" --headless=new --disable-gpu --hide-scrollbars --allow-file-access-from-files --user-data-dir="$profile" \
    --force-device-scale-factor=1 --window-size=1920,1080 --virtual-time-budget=3000 \
    --screenshot="$DIR/$lang/$entry.png" "file://$DIR/boards.html?board=${entry#*-}&lang=$lang" >/dev/null 2>&1
  rm -rf "$profile"
  echo "$DIR/$lang/$entry.png"
}
export -f render
export CHROME DIR

for lang in "${LANGS[@]}"; do mkdir -p "$DIR/$lang"; done
for lang in "${LANGS[@]}"; do
  for entry in "${selected[@]}"; do echo "$lang $entry"; done
done | xargs -n 2 -P "$JOBS" bash -c 'render "$@"' _
