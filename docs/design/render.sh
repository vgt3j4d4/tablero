#!/usr/bin/env bash
# Renders every board in boards.html to docs/design/<lang>/<NN>-<board>.png (1920×1080) with headless Chrome.
# Usage: ./render.sh            (set CHROME=/path/to/chrome if it is not in the default macOS location)
set -euo pipefail

CHROME="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
DIR="$(cd "$(dirname "$0")" && pwd)"
BOARDS=(
  live-game
  final-minute
  time-up
  next-game
  action-feedback
  menu
  quick-clock-adjust
  set-game-clock
  exit-confirm
  remote-mapping
)
LANGS=(en es)

for lang in "${LANGS[@]}"; do
  mkdir -p "$DIR/$lang"
  n=1
  for board in "${BOARDS[@]}"; do
    out="$DIR/$lang/$(printf '%02d' "$n")-$board.png"
    "$CHROME" --headless=new --disable-gpu --hide-scrollbars --allow-file-access-from-files \
      --force-device-scale-factor=1 --window-size=1920,1080 --virtual-time-budget=3000 \
      --screenshot="$out" "file://$DIR/boards.html?board=$board&lang=$lang" >/dev/null 2>&1
    echo "$out"
    n=$((n + 1))
  done
done
