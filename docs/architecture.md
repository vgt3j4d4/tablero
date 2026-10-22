# Architecture

## Components

| Component       | Where                   | Responsibility                                                                       |
| --------------- | ----------------------- | ------------------------------------------------------------------------------------ |
| `GameEngine`    | `shared/` (pure Kotlin) | Owns all game state and the game clock; applies `Command`s; emits snapshots + events |
| Rules           | `shared/`               | Data: game length, tenths threshold, period rules (Pickup and FIBA presets)          |
| Protocol DTOs   | `shared/`               | Messages exchanged with controllers (Phase 2)                                        |
| Scoreboard view | `tv-app/`               | Renders the latest `GameSnapshot` and confirmations with `Canvas`                   |
| Overlays        | `tv-app/`               | Menu, quick clock adjust, set clock, time up, how to use, exit confirm               |
| Buzzer          | `tv-app/`               | Plays the time-up horn in response to engine events (`SoundPool`)                    |
| Persistence     | `tv-app/`               | Saves/restores engine state after every applied command                              |
| Input adapters  | `tv-app/`               | Remote keys → `Command`; WebSocket messages → `Command` (Phase 2)                    |
| Controller app  | `remote-app/`           | Sends commands, shows mirrored state, works while locked (Phase 2–3)                 |

```
 remote keys ─┐
 WebSocket  ──┼──▶ Command ──▶ GameEngine.dispatch() ──▶ GameSnapshot (StateFlow) ──▶ ScoreboardView
 keyboard   ──┘                     │                          │                 └──▶ WebSocket broadcast (Phase 2)
                                    │                          └──▶ StateStore (save after every command)
                                    └──▶ GameEvent (SharedFlow) ──▶ Buzzer, confirmations
```

## Phase 1 scope

Two teams, HOME (left) and AWAY (right), scores, one game clock, buzzer, undo, new game, possession arrow, under
Pickup rules or FIBA rules (team fouls, periods, breaks, overtime; see "Rules: Pickup and FIBA"). The shot clock,
timeouts and other presets are **out of scope for now** (see "Later" at the end); keep the model open to them but
don't implement them.

## Time model

- The engine reads time from an injected `TimeSource` (monotonic nanoseconds). Production:
  `SystemClock.elapsedRealtimeNanos()`. Tests: a fake clock advanced manually.
- A running clock is stored as `(remainingAtStartNanos, startedAtNanos)`; remaining time is computed on read,
  so there is no drift from tick timers. A UI ticker (~10 Hz) only triggers redraws.
- Adjusting a running clock (`AdjustGameClock`) shifts `remainingAtStartNanos`; the clock keeps running and no
  time is lost.
- Expiry detection: the engine checks the expiry instant and emits `GameClockExpired` exactly once; the clock
  stops at 0.

### Display rules

- `MM:SS` at ≥ 1:00, `SS.t` (tenths) below 1:00 (threshold is rules data).
- Round **up** to the displayed unit (seconds, then tenths) so the display reads 0 only at the moment of expiry.
- Digits are drawn in DSEG7 Classic over dim "8" ghost segments (`88:88` / `88.8`, `88` per score).

## State (sketch)

```kotlin
data class GameSnapshot(
    val version: Long,                 // increments on every applied command
    val rules: GameRules,              // gameLength, tenthsBelow, periods (null = Pickup)
    val gameClock: ClockState,         // remaining, running; also counts a break
    val left: TeamState,
    val right: TeamState,
    val timeUp: Boolean,               // clock reached 0; UI shows the time-up bar
    val gameStarted: Boolean,          // the clock ran since the last new game (see Game length)
    val possession: Side?,             // where the possession arrow points; null = hidden
    val period: Int,                   // period rules: 1…, past the last one = overtime
    val inBreak: Boolean,              // the game clock counts a break before `period`
)
data class TeamState(val score: Int, val fouls: Int)   // fouls in the current period (room for timeouts later)
enum class Side { LEFT, RIGHT }            // LEFT = home, RIGHT = away; names localized in tv-app
```

## Commands

| Command                                    | Notes                                                                      |
| ------------------------------------------ | -------------------------------------------------------------------------- |
| `ToggleClock` / `StartClock` / `StopClock` | Start rejected at time up or at 0; a game's first start sets its length    |
| `Score(side, delta)`                       | delta ±1; score never goes below 0                                         |
| `AdjustGameClock(deltaMs)`                 | Relative; allowed running or stopped; result clamped to 0–60:00            |
| `SetGameClock(ms)`                         | Absolute correction (0–60:00), only while stopped                          |
| `ResetGameClock`                           | Back to the period's (game) length, stopped, ending a break; scores kept   |
| `NewGame`                                  | 0–0, clock at game length, stopped, arrow hidden; period 1, no fouls       |
| `TogglePossession`                         | Flips the possession arrow; a hidden arrow shows, pointing to HOME         |
| `ClearPossession`                          | Hides the possession arrow                                                 |
| `SetGameLength(ms)`                        | 1–60 min; applies from the next reset / new game                           |
| `SetRules(preset)`                         | Pickup / FIBA, clock stopped; keeps scores and clock; period 1, no fouls   |
| `Foul(side, delta)`                        | Period rules; delta ±1; fouls 0–9                                          |
| `NextPeriod(withBreak)`                    | Period rules, at time up when there is a next period (see Rules)           |
| `EndBreak`                                 | Ends a break: clock at the period's length, stopped                        |
| `ChangePeriod(delta)`                      | Period rules; corrects the period by ±1 (1 to the 9th overtime)            |
| `Undo`                                     | Reverts the last applied command (history of 20)                           |

- Giving the clock time while `timeUp` (adjust or set to > 0) clears `timeUp`, so a scorer can add back seconds
  the clock ran over and restart it. A running clock adjusted down to 0 expires (buzzer).
- `dispatch` returns `Applied(snapshot)` or `Rejected(reason)`. A rejected command changes nothing, is not
  recorded for undo and does not bump the version; a command that would leave the state as it is is rejected
  as `NO_CHANGE`. Phase 2 acks reuse the reason.
- First launch: 0–0, 10:00 stopped.

### Game length

`ResetGameClock` and `NewGame` put the game length on the clock. Besides `SetGameLength` (menu), a game's **first
start** (the first `StartClock` since the last `NewGame`) makes the time on the clock the game length, when it is a
valid one (1–60 min). Players who want 7-minute games set 7:00 before the first game starts, and every reset and
new game after that is 7:00 too. Later starts in the same game don't count, so a short overtime set after a reset
leaves the length alone. Saved states without `gameStarted` read as started, so a restored game never takes its
length from a paused clock.

Each command carries metadata for Phase 2: `clientId`, `cmdId` (idempotency), `baseVersion` (for `Set…`).

### Undo semantics

Undo reverts the **effect** of the last command, not the passage of time:

- `Score` → only the score changes back.
- `StartClock` → stops the clock and restores the remaining time it had when started (an accidental start costs
  no time); undoing a game's first start also restores the game length it set. `StopClock` → resumes as if it had never stopped.
- `AdjustGameClock` → applies the opposite of the delta actually applied (after clamping); a running clock
  keeps running.
- `SetGameClock` / `ResetGameClock` → the previous clock (and time-up state) comes back.
- `NewGame` → restores the previous game's scores and clock (stopped), the time-up state, whether it had
  started, and its possession arrow.
- `TogglePossession` / `ClearPossession` → the arrow points where it did before (or hides again).
- `SetRules`, `Foul`, `ChangePeriod` → the rules, fouls or period come back.
- `NextPeriod` → the period's end comes back as it was (time up, its fouls), without the buzzer, even when the
  break that followed has run out since.
- `EndBreak` → the break resumes as if it had never ended; if it would have run out by now, it stays over (no
  second buzzer).

The quick-adjust overlay's Back ("cancel") undoes every adjustment made since the overlay opened. Each key is its own
`AdjustGameClock`, so Cancel sends one `Undo` per adjustment the session applied: they leave the history, and a later
Back undoes whatever came before the overlay. Adjustments older than the undo history (a session of more than 20)
are reverted with one opposite `AdjustGameClock` instead. Keys in the overlay act on key-down, ignore repeats, and
are swallowed until released so that a key-up never acts on the scoreboard; the overlay closes on its own, keeping
its changes, after 5 s without a key.

### Menu

A long press on Back opens the menu (design 06) unless another overlay owns the remote (quick adjust, or an open
time-up bar: there Back closes the bar first). It stays open until Back closes it, and the game clock keeps running
behind it. Its keys act on key-down like the other overlays: ↑/↓ move between rows (no wrap), ←/→ change the focused
value, OK opens the focused row.

- **Scores** change with every press, one `Score(side, ±1)` each, exactly as on the scoreboard.
- **Game length** (whole minutes, 1–60) and **language** stay pending while their row is focused and apply when
  focus leaves the row or the menu closes. Game length goes out as one `SetGameLength`, so stepping 10 → 7 takes
  one undo instead of three and can't push older actions (an accidental new game) out of the 20-entry history.
  Language is applied once because applying it recreates the activity; the menu reopens on the row it was on.
  The activity also applies a pending game length when it stops.
- **Rules** (*Pickup* / *FIBA*, design 06 / 32) stays pending like game length and goes out as one `SetRules` when
  focus leaves the row or the menu closes; it only changes while the clock is stopped, and is muted while it runs.
  Under FIBA the menu adds **Period** and **Home / Away fouls**, which change with every press (`ChangePeriod`,
  `Foul`) like scores, and Game length reads Period length. The rows then outgrow the panel: the list scrolls so that
  the focused row and the rows next to it show, and fades out where it is cut. Which rows show follows the snapshot's
  rules (`MenuRow.shownUnder`).
- **Layout** (*Classic* / *Large*, design 01 / 11) applies with every ←/→ press: the board behind the menu changes at
  once, without recreating the activity. The choice is saved in preferences (`LayoutSetting`, next to the language),
  so the board comes back the same after a restart or a reboot; a fresh install starts on Classic. Each layout is a
  `BoardLayout` value holding where the board's parts sit (clock box or none, LED size, team columns, name size,
  adjust ring); `ScoreboardView` reads those values instead of branching on the layout.
- **Mode** (*Indoor* / *Outdoor*, design 01 / 14) works the same way: every ←/→ press repaints the board behind the
  menu, and the choice is saved in preferences (`ModeSetting`); a fresh install starts on Indoor. Each mode is a
  `BoardMode` value holding the board's colors (a null clock box color leaves the box out, as Outdoor does) and the
  window brightness the activity asks for (`BRIGHTNESS_OVERRIDE_FULL` outdoors). The menu, dialogs, time-up and
  adjust bars keep their colors in both modes.
- **Game clock** opens the set game clock dialog (design 08) only while the clock is stopped: ↑/↓ change the focused
  field (minutes stop at 0 and 60, seconds wrap without carrying), ←/→ switch field, OK sends one `SetGameClock`
  (nothing when the time wasn't changed), Back cancels; both return to the menu.
- **New game** sends `NewGame` and closes the menu; Back on the scoreboard undoes it.
- **How to use** (design 10) opens only while the clock is stopped, since it covers the whole board: a generic remote
  (every Android TV remote has the D-pad, its center button and Back, whatever their labels) next to what each key
  does when pressed or held. Only OK and Back act there, both returning to the menu. The first time tablero starts
  with the clock stopped it opens the screen by itself, without the menu, so that a new scorer learns that holding
  Back opens the menu; closing it then goes straight to the board. Preferences remember that it was shown
  (`HelpShown`).
- **Exit** (design 09) opens only while the clock is stopped (muted while it runs, like Game clock):
  a confirm screen with CANCEL focused. ←/→ move between CANCEL and EXIT, OK on CANCEL or Back returns to the menu.
  OK held on EXIT for 2 s fills the button in red and then closes the menu and opens the stock launcher (Android TV
  Settings if it was disabled for the optional kiosk, see `stick-setup.md`); releasing OK sooner, moving the focus or the
  activity pausing cancels the hold. The remote key adapter passes OK's release to the menu for this, the only
  key-up an overlay gets. The screen says how to come back depending on whether HOME opens tablero.

A language change recreates the activity, and the new one may receive the key-up of the key that applied it, so the
remote key adapter ignores key-ups whose key-down it never saw (a stray Back release would otherwise undo).

## Events

`GameClockExpired`, `BreakEnded`, `ClockStarted`, `ClockStopped`. The buzzer (`Buzzer` in `tv-app`) plays its horn
on `GameClockExpired` and nothing else (`BreakEnded` joins it with the tv-app's FIBA screens). The engine emits it once, at the instant a running clock reaches 0 (run out or
adjusted down to 0), so an undo that brings a finished game back shows the time-up bar again but stays silent. The
buzzer belongs to the application, not the activity: it loads once, survives a language recreate, and sounds even
when another app is in front, because the clock keeps running there. An expiry found while restoring after a
restart is not replayed: the bar shows, the horn doesn't.

The horn is `res/raw/buzzer.wav`, synthesized by `scripts/make-buzzer.py` (see `docs/hardware.md` → Audio). It
plays as media, which is the stream that goes out over HDMI and follows the TV's volume.

The time-up bar follows the snapshot's `timeUp`, not the event: it opens (NEW GAME focused) each time `timeUp`
turns true, whether the clock ran out or an undo brought a finished game back, and goes away when it turns false.
Whether Back has closed it is UI state only (`TimeUpBar` in `tv-app`), so after a restart the bar shows again.

`CommandApplied(command, before, after)` and `CommandUndone(command, before, after)` report every change with the
snapshots around it, whichever input sent the command (a toggle is reported as the start or stop it resolved to).
`tv-app` turns them into the confirmation over the status bar (`HOME +3 · 48 → 51`, `POS → AWAY`,
`UNDONE · START CLOCK`):
consecutive score presses on one side and in one direction, each within 1.5 s of the previous one, add up into one
confirmation, while staying separate commands. A command event comes before the clock events it causes.

## Teams and new games

The two teams are named by side, the common scoreboard pattern: HOME on the left, AWAY on the right (LOCAL /
VISITA in Spanish), whoever is actually playing. Sides are fixed. `NewGame` (from the time-up bar or the menu)
starts again at 0–0. The app does not track teams and never decides who plays next.

## Persistence

- After every applied command `tv-app` writes the engine state (snapshot and the undo history)
  as JSON with `AtomicFile` in the app's files dir; on start it restores it. While the clock runs it also saves
  every ~5 s.
- Running-clock timestamps are `elapsedRealtime` based: valid across process death, not across a reboot. After
  a reboot (detected via `Settings.Global.BOOT_COUNT`, or before API 24 the kernel's
  `/proc/sys/kernel/random/boot_id`), restore the clock **stopped** at the last saved remaining
  time and drop the undo history (its timestamps belong to the previous boot).
- The boot each save belongs to is kept in preferences and written **after** the state file, so a crash
  between the two writes makes the next start assume a reboot (clock stopped, at most ~5 s lost) rather than
  apply the previous boot's timestamps. The app also saves once at start, which records the current boot.
  An unreadable state file, or one from a newer schema, is ignored and replaced by the next save.
- The engine side lives in `shared`: `GameEngine.exportState()` → `EngineState.toJson()`, and
  `GameEngine(…, restored = EngineState.fromJson(json), rebooted = …)`. The JSON carries a `schemaVersion`;
  unknown keys are ignored, state from a newer schema is refused.
- Nothing is backed up or moved to a new device: `allowBackup="false"` plus exclude-all rules
  (`xml/backup_rules.xml` for Android 11 and lower, `xml/data_extraction_rules.xml` for 12+, which also covers
  device-to-device transfer). A saved game belongs to the TV it was played on.

## Localization

Goal: the app ships English + Spanish, and adding a language is a translation-only change.

- The engine emits values, enums and events only — never display text.
- `tv-app` string resources: English is the default (`res/values/strings.xml`), Spanish in
  `res/values-es/strings.xml` (generic Spanish; region overrides such as `values-es-rPE` only if ever needed).
  Team names come from resources (`Side.LEFT` → "Home" / "Local", `Side.RIGHT` → "Away" / "Visita").
- `res/xml/locales_config.xml` is the single list of bundled languages. The menu's _Language_ option is built
  from it (each language shown by its own name, e.g. "English", "Español"). Android 13+ also reads this file
  for the system per-app language setting.
- Android before 13 has no per-app language setting, so the language chosen in the app's menu is stored in preferences
  (`AppLanguage`) and applied to the activity as an override configuration (`applyOverrideConfiguration` in
  `attachBaseContext`). With nothing chosen, the app follows the device language (on Android 13+ also the system
  per-app setting, which `locales_config.xml` enables) and falls back to English. When both exist, the in-app choice
  wins. Only activity contexts carry the choice, so user-visible strings are resolved from the activity.
- The language split of App Bundles is disabled (`bundle.language.enableSplit = false`), so every device gets every
  language and the in-app switch always works.
- Rules for translatable strings: format arguments (`%1$s +%2$d`) instead of concatenation, `<plurals>` for
  counts, no text in images, and layouts that tolerate ~30% longer text. Lint `MissingTranslation` is an error so
  every bundled language stays complete.
- Fonts: digits use DSEG7 (language-independent). The UI font (Barlow Condensed) covers Latin scripts; a
  language in another script needs a fallback font added to the font family.

### Adding a language

1. Copy `res/values/strings.xml` to `res/values-<code>/strings.xml` (e.g. `values-pt`) and translate it.
2. Add `<locale android:name="<code>"/>` to `res/xml/locales_config.xml`.
3. Run `./gradlew :tv-app:lint` (catches missing strings) and `./gradlew :tv-app:testDebugUnitTest`
   (`LocalesConfigTest` catches a `values-*` folder missing from `locales_config.xml`), then check the screens on
   the stick.

No Kotlin changes are needed.

## Phase 2 protocol (draft)

- Network: TV joins the referee phone's hotspot (or a travel router). No internet needed.
- Discovery: Android NSD service `_tablero._tcp`; fallback QR code on the TV with `ws://<ip>:<port>`.
- Transport: WebSocket, JSON text frames.
- Client → TV: `{ "type": "command", "clientId", "cmdId", "baseVersion", "command": {…} }`
- TV → clients: `{ "type": "snapshot", "version", "state": {…} }` on every change and at 4–10 Hz while running;
  `{ "type": "ack", "cmdId", "applied": true|false, "reason"? }`.
- Heartbeat: client ping every 1 s; no pong for 2.5 s → controller shows LINK LOST + vibrates, reconnects with
  backoff (≤ 1 s) and requests a snapshot. The TV never stops the clock because a client disconnected.
- Idempotency: TV de-duplicates by `(clientId, cmdId)` so retries are safe; stale `Set…` (old `baseVersion`)
  is rejected.
- Roles: `REFEREE` (clock), `TABLE` (scores, teams), `ADMIN` (all + rules).
  Any role may STOP the clock; only clock owners may START it.

## Locked-phone control (Phase 3, remote-app)

- Foreground service, `foregroundServiceType="connectedDevice"` (Android 14+ requirements), partial wake lock
  and Wi-Fi lock while a game is active.
- `MediaSessionCompat` + `VolumeProviderCompat` (remote playback) to receive volume keys with the screen off:
  Vol+ = toggle clock; Vol− mapping TBD (shot reset once the shot clock exists). Haptic confirmation on every
  accepted action.
- Bluetooth selfie clicker (sends Volume Up) paired to the phone rides the same path.
- MediaStyle notification with up to 3 lock-screen actions; optional `setShowWhenLocked` full UI for the table.
- OEM battery killers (Xiaomi, Huawei, Samsung): onboarding screen for battery-optimization exemptions; the
  system must tolerate the controller being killed (see invariant: TV owns the clock).

## Rules: Pickup and FIBA (end of period and breaks not on screen yet)

The menu's *Rules* row chooses how a game is played (design 24–33). **Pickup** is everything above: one clock per
game, back-to-back games. **FIBA** adds team fouls, periods, breaks and overtime. The rules are data (`GameRules`)
and part of the game state, not a preference: changing them is one undoable command, applied when focus leaves the
row, that keeps the scores and the clock and starts FIBA at period 1 with no fouls.

FIBA takes its periods, lengths and bonus from the FIBA row of the presets table (see "Later"). On top of that:
the period length is set like the Pickup game length (menu, or a game's first start); breaks last 2:00 after
periods 1 and 3 and before each overtime, and 15:00 (halftime) after period 2; overtimes follow one another while the
score is tied; and overtime fouls count as period 4 fouls.

- **Team fouls:** holding ↓ opens the foul bar (design 27); ← or → adds a foul to that side and closes it, Back
  cancels, and it closes by itself after 5 s without a key. So that ↓ can be held, its press opens quick clock adjust
  on release, like ←/→ score on release; under Pickup a held ↓ does the same. The menu corrects fouls ±1 like scores.
  A foul is confirmed like a score (`AWAY FOUL · 4 → 5`).
- **End of a period:** the clock reaching 0 emits `GameClockExpired` (buzzer) as it does now. After periods 1–3, or
  after period 4 or an overtime with the score tied, the time-up bar offers the break (focused) or skipping it
  (design 29). Both move to the next period: period + 1 (or the next overtime), fouls back to 0 (kept going into
  overtime), clock at the period length (5:00 in overtime). With the break, the game clock first counts the break
  down (design 30): ↓ adjusts it like any clock and OK ends it early; at 0 the buzzer sounds and the clock shows the
  period's time, stopped, until OK starts it. After period 4 or an overtime with a winner, the Pickup time-up bar
  shows (design 03).
- **Possession arrow:** unchanged; ↑ flips it, and it carries across periods.
- **New game** under FIBA: 0–0, period 1, no fouls, clock at the period length, arrow hidden.
- Undo reverts each of these like any command: a foul, moving to the next period, starting or ending a break.

In the engine, `GameRules.periods` holds a `PeriodRules` (periods, overtime, break and halftime lengths, the
penalty foul, whether overtime keeps fouls), null under Pickup; `RulesPreset` names the sets the menu offers
(`PICKUP`, `FIBA`). The snapshot answers what the UI needs from that data: `nextPeriod` (null when the game is
over), `breakBeforeMs(next)`, `lengthMs(period)`, `overtime` and `inPenalty(side)`. A break runs on the game clock
(`inBreak`, with `period` already the next one); when it runs out the engine emits `BreakEnded` and leaves the
period's length on the clock, stopped. NBA, NCAA and FIBA 3x3 (see "Later") are new `PeriodRules` values, not new
branches.

## Later (out of Phase 1 scope)

Kept for when the scope grows again: shot clock, timeouts and more league presets. (The possession arrow, and team
fouls and periods as the FIBA rules, moved into Phase 1. The arrow never flips by itself: the scorer flips it with ↑
after each alternating possession.)

- Shot clock display: whole seconds, tenths below 5.0 s; hidden when the game clock has less time remaining than
  the shot clock (FIBA behavior). Events `ShotClockExpired`, `PeriodEnded`.
- FIBA 14-second logic (for a future "smart reset" helper): after a stoppage for a defensive foul/violation in the
  frontcourt, if the shot clock shows ≥ 14 it continues, otherwise it resets to 14. Backcourt throw-ins reset
  to 24.

Rules presets (initial values — verify against current rulebooks):

| Preset     | Periods | Period length            | OT   | Shot clock | Short reset            | Bonus                                         |
| ---------- | ------- | ------------------------ | ---- | ---------- | ---------------------- | --------------------------------------------- |
| FIBA       | 4       | 10:00                    | 5:00 | 24         | 14                     | from 5th team foul per quarter                |
| NBA        | 4       | 12:00                    | 5:00 | 24         | 14 (offensive rebound) | from 5th team foul per quarter                |
| NCAA (men) | 2       | 20:00                    | 5:00 | 30         | 20 (offensive rebound) | per-half thresholds (verify)                  |
| FIBA 3x3   | 1       | 10:00                    | —    | 12         | —                      | from 7th team foul; first to 21 wins (verify) |
| Timer      | 1       | configurable, up or down | —    | off        | —                      | —                                             |
