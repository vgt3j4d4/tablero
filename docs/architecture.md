# Architecture

## Components

| Component       | Where                   | Responsibility                                                                       |
| --------------- | ----------------------- | ------------------------------------------------------------------------------------ |
| `GameEngine`    | `shared/` (pure Kotlin) | Owns all game state and the game clock; applies `Command`s; emits snapshots + events |
| Rules           | `shared/`               | Data: game length, tenths threshold                                                  |
| Protocol DTOs   | `shared/`               | Messages exchanged with controllers (Phase 2)                                        |
| Scoreboard view | `tv-app/`               | Renders the latest `GameSnapshot` with `Canvas`                                      |
| Overlays        | `tv-app/`               | Menu, quick clock adjust, set clock, time up, next game, exit confirm                |
| Sound           | `tv-app/`               | Plays the buzzer in response to engine events (`SoundPool`)                          |
| Persistence     | `tv-app/`               | Saves/restores engine state after every applied command                              |
| Input adapters  | `tv-app/`               | Remote keys → `Command`; WebSocket messages → `Command` (Phase 2)                    |
| Controller app  | `remote-app/`           | Sends commands, shows mirrored state, works while locked (Phase 2–3)                 |

```
 remote keys ─┐
 WebSocket  ──┼──▶ Command ──▶ GameEngine.dispatch() ──▶ GameSnapshot (StateFlow) ──▶ ScoreboardView
 keyboard   ──┘                     │                          │                 └──▶ WebSocket broadcast (Phase 2)
                                    │                          └──▶ StateStore (save after every command)
                                    └──▶ GameEvent (SharedFlow) ──▶ SoundPlayer (buzzer), overlays
```

## Phase 1 scope

Two teams (left/right) identified by color, scores, one game clock, buzzer, undo, next-game picker. Shot clock, fouls,
timeouts, periods and the possession arrow are **out of scope for now** (see "Later" at the end); keep the model
open to them but don't implement them.

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
    val rules: GameRules,              // gameLength, tenthsBelow
    val gameClock: ClockState,         // remaining, running
    val left: TeamState,
    val right: TeamState,
    val timeUp: Boolean,               // clock reached 0; UI shows the time-up bar
    val teams: List<TeamColor>,        // the session's team colors, offered in the next-game picker
)
data class TeamState(val color: TeamColor, val score: Int)
enum class TeamColor { YELLOW, BLACK, WHITE, BLUE, RED, GREEN, ORANGE, PURPLE, LIGHT_BLUE } // names localized in tv-app
```

## Commands

| Command                                    | Notes                                                                      |
| ------------------------------------------ | -------------------------------------------------------------------------- |
| `ToggleClock` / `StartClock` / `StopClock` | Start is rejected while `timeUp` or with 0 on the clock                    |
| `Score(side, delta)`                       | delta ±1; score never goes below 0                                         |
| `AdjustGameClock(deltaMs)`                 | Relative; allowed running or stopped; result clamped to 0–60:00            |
| `SetGameClock(ms)`                         | Absolute correction (0–60:00), only while stopped                          |
| `ResetGameClock`                           | Back to the game length, stopped, scores kept                              |
| `NewGame(left, right)`                     | The two teams for the next game; scores 0–0; clock at game length, stopped |
| `SetGameLength(ms)`                        | 1–60 min; applies from the next reset / new game                           |
| `SetTeams(colors)`                         | Session team colors offered in the next-game picker (≥ 2, distinct)        |
| `Undo`                                     | Reverts the last applied command (history of 20)                           |

- Giving the clock time while `timeUp` (adjust or set to > 0) clears `timeUp`, so a scorer can add back seconds
  the clock ran over and restart it. A running clock adjusted down to 0 expires (buzzer).
- `dispatch` returns `Applied(snapshot)` or `Rejected(reason)`. A rejected command changes nothing, is not
  recorded for undo and does not bump the version; a command that would leave the state as it is is rejected
  as `NO_CHANGE`. Phase 2 acks reuse the reason.
- First launch: every color in the roster, Yellow (left) vs Black (right), 0–0, 10:00 stopped.

Each command carries metadata for Phase 2: `clientId`, `cmdId` (idempotency), `baseVersion` (for `Set…`).

### Undo semantics

Undo reverts the **effect** of the last command, not the passage of time:

- `Score` → only the score changes back.
- `StartClock` → stops the clock and restores the remaining time it had when started (an accidental start costs
  no time). `StopClock` → resumes as if it had never stopped.
- `AdjustGameClock` → applies the opposite of the delta actually applied (after clamping); a running clock
  keeps running.
- `SetGameClock` / `ResetGameClock` → the previous clock (and time-up state) comes back.
- `NewGame` → restores the previous game (teams and scores), clock stopped.

The quick-adjust overlay's Back ("cancel") undoes every adjustment made since the overlay opened.

## Events

`GameClockExpired`, `ClockStarted`, `ClockStopped`. The sound player maps `GameClockExpired` to the buzzer; the UI
shows the time-up bar.

## Next game

The next-game picker pre-selects the last game's two teams; the scorer changes either side (a team can't be on
both sides) and confirms, which dispatches `NewGame(left, right)`. The app does not decide who plays next.

## Persistence

- After every applied command `tv-app` writes the engine state (snapshot and the undo history)
  as JSON with `AtomicFile` in the app's files dir; on start it restores it. While the clock runs it also saves
  every ~5 s.
- Running-clock timestamps are `elapsedRealtime` based: valid across process death, not across a reboot. After
  a reboot (detected via `Settings.Global.BOOT_COUNT`), restore the clock **stopped** at the last saved remaining
  time and drop the undo history (its timestamps belong to the previous boot).
- The boot count each save belongs to is kept in preferences and written **after** the state file, so a crash
  between the two writes makes the next start assume a reboot (clock stopped, at most ~5 s lost) rather than
  apply the previous boot's timestamps. The app also saves once at start, which records the current boot.
  An unreadable state file, or one from a newer schema, is ignored and replaced by the next save.
- The engine side lives in `shared`: `GameEngine.exportState()` → `EngineState.toJson()`, and
  `GameEngine(…, restored = EngineState.fromJson(json), rebooted = …)`. The JSON carries a `schemaVersion`;
  unknown keys are ignored, state from a newer schema is refused.

## Localization

Goal: the app ships English + Spanish, and adding a language is a translation-only change.

- The engine emits values, enums and events only — never display text.
- `tv-app` string resources: English is the default (`res/values/strings.xml`), Spanish in
  `res/values-es/strings.xml` (generic Spanish; region overrides such as `values-es-rPE` only if ever needed).
  Team color names come from resources (`TeamColor.YELLOW` → "Yellow" / "Amarillo").
- `res/xml/locales_config.xml` is the single list of bundled languages. The menu's _Language_ option is built
  from it (each language shown by its own name, e.g. "English", "Español"). Android 13+ also reads this file
  for the system per-app language setting.
- API 28 has no per-app language setting, so the language chosen in the app's menu is stored in preferences
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

## Later (out of Phase 1 scope)

Kept for when the scope grows again: shot clock, team fouls / bonus, timeouts, periods, possession arrow and
league presets.

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
