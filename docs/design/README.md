# Design — Phase 1 TV app

Reference screens for the Android TV app, 1920×1080 (what the stick outputs over HDMI). Each screen exists in
English (`en/`) and Spanish (`es/`). Behavior behind each screen is specified in [`CLAUDE.md`](../../CLAUDE.md)
and [`architecture.md`](../architecture.md); these images show how it looks.

| # | Screen | What it shows |
|---|---|---|
| 01 | [Live game](en/01-live-game.png) | The scoreboard: game clock on top, HOME and AWAY with their scores, status bar showing whether the clock is running |
| 02 | [Final minute](en/02-final-minute.png) | Below 1:00 the clock shows tenths (`38.4`) |
| 03 | [Time up](en/03-time-up.png) | Buzzer state: red frame, result, and the choice between *New game* (default: 0–0) and *Reset clock · keep score* |
| 05 | [Action feedback](en/05-action-feedback.png) | Confirmation after each action (`HOME +3 · 48 → 51`) with the Undo hint |
| 06 | [Menu](en/06-menu.png) | Long-press Back: game clock, game length, scores, new game, language, exit |
| 07 | [Quick clock adjust](en/07-quick-clock-adjust.png) | ↓ opens it: ↑/↓ ±1 min, ←/→ ±1 s, OK done, Back cancel; works while the clock runs |
| 08 | [Set game clock](en/08-set-game-clock.png) | Exact time entry from the menu, only while the clock is stopped |
| 09 | [Exit confirm](en/09-exit-confirm.png) | Guarded exit to Android TV: Cancel focused by default, exit needs OK held for 2 s |
| 10 | [Remote mapping](en/10-remote-mapping.png) | Key mapping for the Xiaomi Mi TV Stick remote |

Spanish versions use the same file names under [`es/`](es/). Numbers are fixed because issues link to the files;
04 (a next-game team picker) was dropped.

## Visual language

- Black background; digits mimic LED scoreboards using **DSEG7 Classic** (seven-segment) with dim "unlit 8"
  segments behind them. Game clock LED red `#FF3B2F`, scores LED amber `#FFB21E`.
- Teams are named by side, in white: `HOME` (left) and `AWAY` (right), `LOCAL` / `VISITA` in Spanish.
- UI text in **Barlow Condensed**; key hints drawn as outlined keycaps.
- Highlight / focus: inverted rows (light background, dark text) or a thick light outline; amber marks the
  clock-editing modes; red is reserved for time up and destructive actions.

## Regenerating the images

The screens are plain HTML in [`boards.html`](boards.html) (fonts bundled in [`fonts/`](fonts/), both under the
SIL Open Font License). Open `boards.html?board=<name>&lang=<en|es>` in a browser to view one, or regenerate all
PNGs with headless Chrome:

```bash
docs/design/render.sh                 # CHROME=/path/to/chrome to override the macOS default
```

Edit `boards.html` (layout or the `STRINGS` table) and re-run the script when the design changes.
