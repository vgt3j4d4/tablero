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
| 06 | [Menu](en/06-menu.png) | Long-press Back: game clock, game length, scores, new game, rules, layout, mode, language, how to use, exit |
| 07 | [Quick clock adjust](en/07-quick-clock-adjust.png) | ↓ opens it: ↑/↓ ±1 min, ←/→ ±1 s, OK done, Back cancel; works while the clock runs |
| 08 | [Set game clock](en/08-set-game-clock.png) | Exact time entry from the menu, only while the clock is stopped |
| 09 | [Exit confirm](en/09-exit-confirm.png) | Guarded exit to Android TV: Cancel focused by default, exit needs OK held for 2 s |
| 10 | [How to use](en/10-remote-mapping.png) | Menu → How to use, and on its own on the first launch: a generic remote (any Android TV remote has the D-pad, its center button and Back) and what each key does when pressed or held |
| 11 | [Large: live game](en/11-large-live-game.png) | The *Large* layout (menu → Layout): no clock box, clock and scores 410 px instead of 330, smaller team names |
| 12 | [Large: time up](en/12-large-time-up.png) | Time up in the Large layout; the bar clears the scores |
| 13 | [Large: quick clock adjust](en/13-large-quick-clock-adjust.png) | Quick clock adjust in the Large layout: the ring goes around the bare digits |
| 14 | [Outdoor: live game](en/14-outdoor-live-game.png) | The *Outdoor* mode (menu → Mode) for daylight courts: white board, black clock and team names, deep red scores, no clock box, faint unlit segments; shown at `9:21` and 3–0, where they keep the digits in place |
| 15 | [Outdoor: Large](en/15-outdoor-large-live-game.png) | Outdoor in the Large layout |
| 16 | [Outdoor: time up](en/16-outdoor-time-up.png) | Time up in Outdoor: the red frame and bar are unchanged |
| 17 | [Outdoor: action feedback](en/17-outdoor-action-feedback.png) | The confirmation turns dark so it stands out on the white board |
| 18 | [Outdoor: quick clock adjust](en/18-outdoor-quick-clock-adjust.png) | Quick clock adjust in Outdoor: amber ring and bar as indoors |
| 19 | [Outdoor: menu](en/19-outdoor-menu.png) | The menu over the Outdoor board, Mode row focused; the menu and dialogs stay dark in both modes |
| 20 | [Possession arrow](en/20-possession.png) | ↑ flips the arrow between the scores (the first press points to HOME), long ↑ hides it; hidden after a new game, as in 01–19 |
| 21 | [Large: possession arrow](en/21-large-possession.png) | The arrow in the Large layout, a little smaller so it clears the bigger scores |
| 22 | [Outdoor: possession arrow](en/22-outdoor-possession.png) | The arrow in Outdoor: deep red like the scores, faint when unlit |
| 23 | [Possession feedback](en/23-possession-feedback.png) | The confirmation after ↑ (`POS → AWAY`), undone with Back like any action |
| 24 | [FIBA: live game](en/24-fiba-live-game.png) | Menu → Rules → FIBA: team fouls in the corners beside the clock, the period between the scores with the possession arrows on either side; AWAY's 5th foul (the first with free throws) turns red |
| 25 | [FIBA: Large](en/25-fiba-large-live-game.png) | FIBA in the Large layout: a smaller period and arrows clear the bigger scores |
| 26 | [FIBA: Outdoor](en/26-outdoor-fiba-live-game.png) | FIBA in Outdoor: fouls deep red like the scores, the penalty and the period black like the clock |
| 27 | [FIBA: team foul](en/27-fiba-team-foul.png) | Hold ↓: rings on both foul corners and a bar; ← or → adds the foul and closes it, Back cancels |
| 28 | [FIBA: foul feedback](en/28-fiba-foul-feedback.png) | The confirmation after a foul (`AWAY FOUL · 4 → 5`), undone with Back |
| 29 | [FIBA: end of period](en/29-fiba-period-end.png) | The clock reached 0 in period 2: the time-up frame and a bar with the break (`HALFTIME · 15:00`, focused) or `SKIP BREAK` |
| 30 | [FIBA: halftime](en/30-fiba-halftime.png) | The break counts down on the game clock; the board is already on period 3 with no fouls. ↓ adjusts the break, OK ends it |
| 31 | [FIBA: overtime](en/31-fiba-overtime.png) | A tie after period 4: overtime 1 (`OT`), 5:00; overtime fouls count as period 4 fouls, so HOME's 5 carry over |
| 32 | [FIBA: menu](en/32-fiba-menu.png) | The menu under FIBA rules adds period, period length and fouls rows; the list scrolls with the focus and fades out where it is cut |
| 33 | [FIBA: how to use](en/33-fiba-remote-mapping.png) | How to use under FIBA rules: holding ↓ adds a team foul |

Screens 01–23 show the *Pickup* rules, 01–13 the *Indoor* mode and 01–10 the *Classic* layout, the defaults; the other
screens look the same in every layout and mode (the menu, dialogs and bars only differ in the board behind them). Spanish versions use the same file names under [`es/`](es/).
Numbers are fixed because issues link to the files; 04 (a next-game team picker) was dropped.

## Visual language

- Black background; digits mimic LED scoreboards using **DSEG7 Classic** (seven-segment) with dim "unlit 8"
  segments behind them. Game clock LED red `#FF3B2F`, scores LED amber `#FFB21E`.
- **Outdoor** mode: in daylight, reflections wash out a dark screen and dim reds, so the board turns light, like the
  day mode of maps apps: white background, clock and team names `#0A0A0A`, scores deep red `#B0121B`, no clock box
  (its grey would take contrast from the clock; the digits stay where Classic puts them), and faint unlit segments
  (`#EEEFF1` clock, `#F6E7E8` scores). Without them a lit 1 drifts right of its cell and a short reading (`9:21`,
  `3`) looks off-centre. The confirmation becomes a dark panel; menu, dialogs, time-up and adjust bars keep
  their colors.
- **Possession arrow:** a `POS` label in line with the team names (`POS` in Spanish too) over a pair of arrows
  between the scores. The lit one points to the team with the next alternating possession, in the score color; the
  other is drawn like an unlit segment. Hidden until the first ↑ of a game.
- **FIBA rules** (menu → Rules): each team's fouls sit in the corner beside the clock on its side, which is empty
  under Pickup rules, with a `FOULS` label over one LED digit (250 px; 230 in Large). They use the score color and
  switch to the clock color from the first foul with free throws (the 5th of a period). The period is one digit in
  the clock color between the scores, with a `PERIOD` label (`OT` in overtime) in line with the team names; the
  possession arrows sit on either side of it and stay drawn, unlit, while the arrow is hidden, so `POS` is not
  shown. The foul bar uses the confirmation's colors, the break bar the menu's panel.
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

## Launcher banner and icon

[`icons.html`](icons.html) holds the concepts that were compared ([`icons.png`](icons.png)); **A** won, with the
**A1** icon. The Android TV banner (320×180) is the red LED clock in its box over the `TABLERO` wordmark, on the
panel grey so the tile keeps an edge against the launcher's black background. The adaptive icon is a seven-segment
`t` over its unlit `8` on the board's black. Unlit segments use `led_red_unlit_icon`, a touch brighter than on the
board, so they still show at launcher sizes.

The shipped drawables (`drawable/banner.xml`, `ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`) are
generated from the app's fonts, so change the constants in the script rather than the XML:

```bash
pip install fonttools uharfbuzz
python3 scripts/make-icons.py
```

The stock launcher caches banners: after installing a new one, `adb shell am force-stop com.google.android.tvlauncher`
and press Home to see it.
