# CLAUDE.md — tablero

Project context for Claude Code. Read this first; deeper detail lives in `docs/`.

## What we are building

**tablero** is a low-cost DIY scoreboard for recreational basketball (and later other sports).
A cheap TV shows the scoreboard; an **Android TV app** running on a streaming stick (or a Google TV set)
is the display *and* the authoritative game clock. It is controlled by:

1. **Phase 1:** the stick's Bluetooth remote (D-pad + OK + Back) — scorer at the table.
2. **Phase 2+:** an **Android phone app** over local Wi-Fi (WebSocket), usable by a referee moving on court,
   including while the phone is **locked** (volume keys, Bluetooth clicker, lock-screen notification).

No internet is required at any point. Total hardware budget: < US$150 including the TV.

**Audience:** the repo is public and meant for anyone. The first users are players and referees in Lima, Peru,
so the app ships **English (default) + Spanish from day one**, and adding another language must only take a new
translation file (see `docs/architecture.md` → Localization).

**Usage pattern (drives Phase 1 design):** pickup sessions of ~2 hours, back-to-back **10-minute games** on a single
running clock. The board names its two teams the usual way, **HOME** (left) and **AWAY** (right) — *LOCAL* / *VISITA*
in Spanish — whoever is actually playing; it never tracks teams or who plays next.

## Current status

- Phase 1 (TV app + stick remote) — design agreed; Gradle scaffold and the `shared` engine (clock, scores, undo,
  display rounding, persisted state) are done with unit tests. `tv-app`: one activity, the final scoreboard look
  (`ScoreboardView`: LED clock and scores, HOME / AWAY labels, status bar, action confirmations), quick clock adjust,
  time-up bar, remote key adapter, persistence, fonts, localization (English + Spanish, in-app language via
  `AppLanguage`). Next: menu.
- Design reference: `docs/design/` (PNG screens in English and Spanish, regenerated from `boards.html`).
- See `docs/roadmap.md` for phases and checklists.

## Phase 1 scope

**In:** two teams, HOME on the left and AWAY on the right (fixed, no swap), with scores; game clock (tenths under
1:00), buzzer at 0, undo, quick clock adjust, time-up bar with a new game (0–0), menu, guarded exit to Android TV.

**Out for now (designed for, not built):** shot clock, team fouls, timeouts, periods, possession arrow,
FIBA/NBA/NCAA presets. Keep the engine open to them, but don't implement or render them.
## Repository layout (planned)

```
tablero/
├── shared/       # Pure Kotlin/JVM module: GameEngine, Command model, rules, protocol DTOs. No Android deps.
├── tv-app/       # Android TV app: scoreboard view, buzzer, input adapters (remote keys, later WebSocket server)
├── remote-app/   # Android phone controller (Phase 2+)
├── scripts/      # adb helpers: setup-stick.sh / restore-stick.sh (debloat + kiosk)
└── docs/         # architecture.md, hardware.md, stick-setup.md, roadmap.md, design/ (screens)
```

## Architecture invariants (do not break these)

1. **The display device is the single source of truth.** `GameEngine` on the TV owns the game clock, teams and
   scores. Controllers never own state.
2. **Inputs are commands, not values.** Every input source (remote keys, WebSocket, keyboard, clicker) is an
   adapter that maps to a `Command` (`ToggleClock`, `Score(side, delta)`, `AdjustGameClock(deltaMs)`, `Undo`, …)
   and calls `engine.dispatch(command)`. Absolute `Set…` commands exist only for corrections.
3. **Time comes from an injected monotonic clock** (`interface TimeSource { fun nowNanos(): Long }`;
   production = `SystemClock.elapsedRealtimeNanos()`). Never use wall-clock time. This makes the engine
   deterministic in unit tests (fake time source).
4. **The engine is pure Kotlin in `shared/`** with exhaustive unit tests for clock and undo rules.
   Android code only renders state and plays sounds.
5. **The engine emits state snapshots + events** (e.g. `GameClockExpired`); the UI observes them. Buzzer playback
   reacts to events, not to polling.
6. **A disconnected controller never affects the clock.** The game keeps running on the TV.
7. **Undo log:** engine keeps the last N (≈20) applied commands so the scorer can undo mistakes — including
   starting a new game by accident.
8. **State survives the app being killed.** The engine state is persisted after every applied command and
   restored on start (see `docs/architecture.md` → Persistence).
9. **The engine never produces display text.** It emits values, enums and events; `tv-app` maps them to
   localized strings. No user-visible string is hardcoded in Kotlin or layouts.

## Target devices & constraints

- **Primary display device:** Xiaomi Mi TV Stick (model MDZ-24-AA), Amlogic S805Y, **1 GB RAM**,
  **Android TV 9 (API 28)**, 1080p over HDMI. → `tv-app` **minSdk 28**.
- Should also run on cheap Google TV sets (e.g. Miray MS32-E2000GBT, Android 14).
- Amazon Fire TV Stick 2nd gen (Fire OS 5 / API 22) is **not** a target.
- **Performance budget:** 1 GB RAM device. For the scoreboard surface use a single custom `View` drawn with
  `Canvas` (hardware accelerated). **No WebView.** No object allocation inside `onDraw`. Redraw at ~10 Hz while
  a clock runs, only on change otherwise. Everything but the lit clock digits is cached in a bitmap layer redrawn
  on change: the stick's CPU can't re-record the whole scene at 10 Hz without missing frames. Preload buzzer
  sounds with `SoundPool`.
- Keep screen awake with `FLAG_KEEP_SCREEN_ON`. The app is the **default HOME** on the stick (activity has a
  `HOME` intent filter; the stock launcher stays installed so admins can exit — see `docs/stick-setup.md`).

## Scoreboard look

- Black background; digits mimic LED scoreboards: **DSEG7 Classic** (seven-segment, SIL OFL) with dim "unlit 8"
  ghost segments drawn behind the digits. All digits share one width, and `.` is zero-width (it lights the
  decimal point of the digit before it), so `88.8` lines up under any reading.
- Fonts ship unmodified as TTF in `tv-app/src/main/res/font/` (`dseg7_classic_bold`, `barlow_condensed` family
  500–800); their OFL license files live in `tv-app/src/main/assets/licenses/` (`res/font` only accepts font and
  XML files). DSEG has the Reserved Font Name "DSEG": never subset or modify that file.
- Game clock LED red, scores LED amber. Each team column is labelled `HOME` / `AWAY` (white text). UI text in
  Barlow Condensed (OFL).
- Layout (1920×1080): clock across the top; left/right team columns below; a thin status bar at the bottom
  (clock running/stopped, action confirmations like `HOME +3 · 48 → 51`).

## Remote control mapping (Phase 1, Xiaomi Bluetooth remote)

Usable keys: D-pad ↑↓←→, OK (`DPAD_CENTER`), Back — with short vs long press (`event.startTracking()` +
`onKeyLongPress`). Home / Assistant / Netflix / Prime buttons are system-handled; volume keys likely go to the TV
via HDMI-CEC (verify with a key logger).

| Key | Short press | Long press |
|---|---|---|
| OK | Start/stop game clock — **fires on key-down** (no long press on OK, so no delay) | — |
| ← | Home +1 | Home −1 |
| → | Away +1 | Away −1 |
| ↑ | — (unused) | — |
| ↓ | Quick clock adjust mode | — |
| Back | Undo last action | Open menu |

- Ignore key repeats (`repeatCount > 0`) for OK so holding it never toggles twice.
- Consecutive +1 presses on the same side within ~1.5 s are shown as one confirmation (`HOME +3`), but are
  still separate `Score` commands.
- **Quick clock adjust (↓):** clock gets highlighted; ↑/↓ = ±1 min, ←/→ = ±1 s, OK = done, Back = cancel all
  changes made in this session. Auto-exits after ~5 s idle. Works while the clock runs (relative command).
- **Game length:** the time on the clock at a game's first start (after `NEW GAME`) becomes the game length that
  resets and new games use; set 7:00 before the game starts to play 7-minute games. The menu can set it too.
- **Time up:** red frame + bar with `NEW GAME` (default: 0–0, clock at game length, stopped) /
  `RESET CLOCK · KEEP SCORE` (←/→ to choose, OK to apply). Back closes the bar instead of undoing (the frame stays,
  so a score can still be fixed); OK opens it again.

Menu overlay (Back long press; D-pad navigable): game clock (set exact time, stopped only), game length, left/right
score, new game, language, exit to Android TV (only while stopped; confirm screen with Cancel focused; exit
requires holding OK 2 s).

## Tech stack

- Kotlin, Gradle Kotlin DSL, version catalog (`gradle/libs.versions.toml`).
- Package / application IDs: `tv-app` = `net.pladum.tablero.tv`, `remote-app` = `net.pladum.tablero.remote`,
  `shared` = `net.pladum.tablero.shared` (Kotlin package). Debug builds add `.debug`. Application IDs are
  permanent once published — never rename them.
- `shared`: Kotlin/JVM library. Tests: JUnit Jupiter (JUnit 6, the successor of JUnit 5; `@ParameterizedTest`,
  `@Nested`) with kotlin.test assertions.
- JSON: kotlinx.serialization (no reflection) for persisted state and, later, the Phase 2 protocol.
- `tv-app`: Android Views + Leanback-free custom UI; Kotlin coroutines/Flow for state.
- Localization: Android string resources. **English is the default** (`res/values/`), Spanish in
  `res/values-es/`. The list of languages lives in one place (`res/xml/locales_config.xml`). API 28 has no
  per-app language setting, so the app applies the language chosen in its own menu (stored in preferences) via
  an override configuration on the activity; with nothing chosen it follows the device language.
- Phase 2 networking: WebSocket server on the TV (Ktor server or Java-WebSocket — decide when Phase 2 starts),
  service discovery via Android NSD (`_tablero._tcp`), QR-code fallback with `ws://ip:port`. JSON messages.
- `remote-app`: modern Android (Compose OK here), foreground service type `connectedDevice`, `MediaSession` +
  `VolumeProviderCompat` for volume keys while locked.

## Common commands (once scaffolded)

```bash
./gradlew :shared:test                     # engine/rules unit tests
./gradlew :tv-app:assembleDebug
adb connect <stick-ip>:5555                # stick: Developer options → network debugging
./gradlew :tv-app:installDebug
adb shell input keyevent KEYCODE_DPAD_CENTER   # simulate remote keys
adb shell input keyevent --longpress KEYCODE_DPAD_LEFT
adb exec-out screencap -p > screen.png     # screenshot (the stick renders at 1280×720)
adb logcat -s tablero
```

On-device testing happens **on the stick only**, installed over adb; there is no emulator setup. Tips:

- `adb shell input keyevent` can take several seconds on the stick, so judge clock timing from screenshots and
  `logcat` timestamps, not from host-side timing.
- Right after a fresh install the stock launcher may jump back in front; launch the app again with
  `adb shell am start -n net.pladum.tablero.tv.debug/net.pladum.tablero.tv.MainActivity`.

## Conventions

- Code, comments, docs and commit messages in **English**.
- Every user-visible string is a resource with an English default and a translation in **every** bundled
  language (lint `MissingTranslation` is an error). Use format arguments (`%1$s`) and `<plurals>`, never string
  concatenation; never bake text into images.
- Keep on-screen labels short and uppercase where they sit on the scoreboard, and leave room for ~30% longer
  translations. Spanish copy uses *tú* ("Presiona INICIO…").
- Every rules change in `shared/` comes with unit tests using a fake `TimeSource`.
- Rules are data: game length and the tenths threshold (and later presets) are defined as data objects, not
  `if (mode == …)` branches scattered in the code.
- Prefer small, reviewable commits; keep `docs/` updated when behavior or decisions change.
- **Branches:** `develop` is the integration branch. Create feature branches from `develop` and open PRs against
  it. `main` only holds released code: merge `develop` → `main` when a release is ready.

## Further reading

- `docs/architecture.md` — engine model, commands, clock, persistence, Phase 2 protocol.
- `docs/hardware.md` — hardware choices, budget, audio, placement.
- `docs/stick-setup.md` — preparing the Xiaomi stick (ADB, debloat, default launcher, settings).
- `docs/roadmap.md` — phases and task checklists.
- `docs/design/` — reference screens for the TV app.
