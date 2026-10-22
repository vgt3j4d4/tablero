# Roadmap

## Phase 1 — TV app + stick remote

Scope: two teams (HOME / AWAY), scores, game clock, buzzer, undo, new game, possession arrow (see `CLAUDE.md` →
Phase 1 scope), under Pickup rules or FIBA rules (team fouls, periods, breaks, overtime). Shot clock and timeouts
are deferred.

- [x] Gradle multi-module scaffold (`shared`, `tv-app`), version catalog, CI-friendly `./gradlew test`
- [x] `shared`: `TimeSource`, `GameEngine`, `Command`, `GameSnapshot`, `GameEvent`, undo history (+ undo semantics tests)
- [x] `shared`: `AdjustGameClock`, `SetGameClock`, `ResetGameClock`, `NewGame`, display rounding + unit tests
- [x] `shared`: state serialization (JSON) for persistence
- [x] `tv-app`: single activity, `FLAG_KEEP_SCREEN_ON`, launcher entry; first `ScoreboardView` (clock + scores,
      system font)
- [x] `tv-app`: string resources — English default + Spanish, `locales_config.xml`, in-app language setting,
      lint `MissingTranslation` as error
- [x] `tv-app`: bundle DSEG7 Classic (Bold) and Barlow Condensed (500–800) as **TTF** in `assets/fonts/` with their
      OFL license files (in `assets/licenses/`)
- [x] `tv-app`: `ScoreboardView` (Canvas) — clock + scores in DSEG7 with ghost segments, HOME / AWAY labels, status bar
- [x] `tv-app`: remote key adapter (OK on key-down, short/long press mapping, see CLAUDE.md)
- [x] `tv-app`: quick clock adjust overlay (↓)
- [x] `shared`: teams named by side (HOME / AWAY), no team colors
- [x] `tv-app`: time-up bar (new game / reset clock)
- [x] `tv-app`: menu overlay (game clock, game length, scores, new game, language; exit comes with the guarded exit)
- [x] `tv-app`: `HOME` intent filter and guarded exit to Android TV (stopped clock only, hold OK 2 s)
- [x] `tv-app`: persistence (`AtomicFile`, restore on start, reboot detection)
- [x] `tv-app`: buzzer sound via `SoundPool` (synthesized horn, `scripts/make-buzzer.py`)
- [x] `tv-app`: launcher banner and adaptive icon (vector drawables from `scripts/make-icons.py`, concepts in
      `docs/design/icons.html`)
- [x] `tv-app`: run on the Fire TV Stick 2nd gen (`minSdk 22`, fonts as assets, pre-24 boot detection)
- [x] `tv-app`: Large layout (bigger clock and scores, no clock box), chosen in the menu next to Classic
- [x] `tv-app`: Outdoor mode (a light board for daylight courts), chosen in the menu next to Indoor
- [x] `shared` + `tv-app`: possession arrow between the scores (↑ flips it, long ↑ hides it)
- [x] `tv-app`: How to use screen (a generic remote and what each key does), in the menu and on the first launch
- [x] Design: FIBA rules (team fouls, periods, breaks, overtime), `docs/design/` 24–33
- [x] `shared`: rules presets as data (Pickup, FIBA), periods, team fouls, breaks, overtime + unit tests
- [x] `tv-app`: FIBA board (fouls in the corners, period between the scores), team foul bar (hold ↓), end of period
      and break bars, menu rows (rules, period, period length, fouls) in a scrolling list, how to use
- [ ] Play a full FIBA game on the Xiaomi stick (periods, halftime, overtime)
- [ ] Test the Outdoor mode on a real outdoor court in daylight
- [ ] Test the full Phase 1 flow on the Xiaomi stick
- [x] `scripts/setup-stick.sh` / `restore-stick.sh`
- [ ] Readability test on a real TV from the far corner of a court

## Phase 2 — Phone controller over Wi-Fi

- [ ] WebSocket server in tv-app + NSD advertisement + QR fallback
- [ ] Protocol (commands, acks, snapshots, heartbeat, idempotency) in `shared`
- [ ] `remote-app`: discovery, connection, controller UI, mirrored state
- [ ] Latency check (slow-motion video of phone tap vs TV)

## Phase 3 — Control with the phone locked

- [ ] Foreground service (`connectedDevice`), wake/Wi-Fi locks
- [ ] MediaSession + VolumeProvider volume-key mapping, haptics
- [ ] Bluetooth clicker support
- [ ] MediaStyle lock-screen notification; optional show-when-locked UI
- [ ] Battery-optimization onboarding; test on Samsung and Xiaomi phones

## Phase 4 — Polish, full basketball & more sports

- [ ] Shot clock (24/14), timeouts, more presets (NBA, NCAA, FIBA 3x3)
      (see `docs/architecture.md` → Later)
- [ ] Multiple controllers with roles and conflict rules
- [ ] Custom team names, themes
- [ ] More languages (translation-only, see `docs/architecture.md` → Adding a language)
- [ ] Futsal, volleyball, generic timer modes
- [ ] Optional: ESP32 + HUB75 LED panel display for outdoor courts
