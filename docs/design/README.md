# Design — Phase 1 TV app

Reference screens for the Android TV app, 1920×1080 (what the stick outputs over HDMI). Each screen exists in
English (`en/`) and Spanish (`es/`). Behavior behind each screen is specified in [`CLAUDE.md`](../../CLAUDE.md)
and [`architecture.md`](../architecture.md); these images show how it looks.

| # | Screen | What it shows |
|---|---|---|
| 01 | [Live game](en/01-live-game.png) | The scoreboard: game clock on top, the two color teams and their scores, status bar with the game length and whether the clock is running |
| 02 | [Final minute](en/02-final-minute.png) | Below 1:00 the clock shows tenths (`38.4`) |
| 03 | [Time up](en/03-time-up.png) | Buzzer state: red frame, result, and the choice between *Next game* (default) and *Reset clock · keep score* |
| 04 | [Next game](en/04-next-game.png) | Team picker: the last game's teams pre-selected; ←/→ side, ↑/↓ team |
| 05 | [Action feedback](en/05-action-feedback.png) | Confirmation after each action (`YELLOW +3 · 48 → 51`) with the Undo hint |
| 06 | [Menu](en/06-menu.png) | Long-press Back: game clock, game length, scores, teams, new game, language, exit |
| 07 | [Quick clock adjust](en/07-quick-clock-adjust.png) | ↓ opens it: ↑/↓ ±1 min, ←/→ ±1 s, OK done, Back cancel; works while the clock runs |
| 08 | [Set game clock](en/08-set-game-clock.png) | Exact time entry from the menu, only while the clock is stopped |
| 09 | [Exit confirm](en/09-exit-confirm.png) | Guarded exit to Android TV: Cancel focused by default, exit needs OK held for 2 s |
| 10 | [Remote mapping](en/10-remote-mapping.png) | Key mapping for the Xiaomi Mi TV Stick remote |

Spanish versions use the same file names under [`es/`](es/).

## Visual language

- Black background; digits mimic LED scoreboards using **DSEG7 Classic** (seven-segment) with dim "unlit 8"
  segments behind them. Game clock LED red `#FF3B2F`, scores LED amber `#FFB21E`.
- Teams are jersey colors: a color swatch plus the color name in white. Palette: yellow `#FFD60A`,
  black `#111214` (swatch outlined so it shows on black), white `#F5F5F5`, blue `#2F6BFF`, red `#E5372B`,
  green `#1FB85A`, orange `#FF7A00`, purple `#8B5CF6`, light blue `#5BC8F5`.
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
