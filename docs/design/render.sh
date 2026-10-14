#!/usr/bin/env bash
# Renders every board in boards.html to docs/design/<lang>/<NN>-<board>.png (1920×1080) with headless Chrome.
# Usage: ./render.sh            (set CHROME=/path/to/chrome if it is not in the default macOS location)
set -euo pipefail

CHROME="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
DIR="$(cd "$(dirname "$0")" && pwd)"
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
)
LANGS=(en es)

for lang in "${LANGS[@]}"; do
  mkdir -p "$DIR/$lang"
  for entry in "${BOARDS[@]}"; do
    board="${entry#*-}"
    out="$DIR/$lang/$entry.png"
    "$CHROME" --headless=new --disable-gpu --hide-scrollbars --allow-file-access-from-files \
      --force-device-scale-factor=1 --window-size=1920,1080 --virtual-time-budget=3000 \
      --screenshot="$out" "file://$DIR/boards.html?board=$board&lang=$lang" >/dev/null 2>&1
    echo "$out"
  done
done
