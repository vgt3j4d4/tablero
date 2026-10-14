# Roadmap

## Phase 1 — TV app + stick remote

Scope: two teams (HOME / AWAY), scores, game clock, buzzer, undo, new game (see `CLAUDE.md` →
Phase 1 scope). Shot clock, fouls, timeouts, periods and possession are deferred.

- [x] Gradle multi-module scaffold (`shared`, `tv-app`), version catalog, CI-friendly `./gradlew test`
- [x] `shared`: `TimeSource`, `GameEngine`, `Command`, `GameSnapshot`, `GameEvent`, undo history (+ undo semantics tests)
- [x] `shared`: `AdjustGameClock`, `SetGameClock`, `ResetGameClock`, `NewGame`, display rounding + unit tests
- [x] `shared`: state serialization (JSON) for persistence
- [x] `tv-app`: single activity, `FLAG_KEEP_SCREEN_ON`, launcher entry; first `ScoreboardView` (clock + scores,
      system font)
- [x] `tv-app`: string resources — English default + Spanish, `locales_config.xml`, in-app language setting,
      lint `MissingTranslation` as error
- [x] `tv-app`: bundle DSEG7 Classic (Bold) and Barlow Condensed (500–800) as **TTF** in `res/font/` with their
      OFL license files (in `assets/licenses/`)
- [x] `tv-app`: `ScoreboardView` (Canvas) — clock + scores in DSEG7 with ghost segments, HOME / AWAY labels, status bar
- [x] `tv-app`: remote key adapter (OK on key-down, short/long press mapping, see CLAUDE.md)
- [x] `tv-app`: quick clock adjust overlay (↓)
- [x] `shared`: teams named by side (HOME / AWAY), no team colors
- [ ] `tv-app`: time-up bar (new game / reset clock)
- [ ] `tv-app`: menu overlay (game clock, game length, scores, new game, language, exit)
- [ ] `tv-app`: `HOME` intent filter and guarded exit to Android TV (stopped clock only, hold OK 2 s)
- [x] `tv-app`: persistence (`AtomicFile`, restore on start, reboot detection)
- [ ] `tv-app`: buzzer sound via `SoundPool`
- [ ] Test the full Phase 1 flow on the Xiaomi stick
- [ ] `scripts/setup-stick.sh` / `restore-stick.sh`
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

- [ ] Shot clock (24/14), team fouls / bonus, timeouts, periods, possession arrow, league presets
      (see `docs/architecture.md` → Later)
- [ ] Multiple controllers with roles and conflict rules
- [ ] Custom team names, themes
- [ ] More languages (translation-only, see `docs/architecture.md` → Adding a language)
- [ ] Futsal, volleyball, generic timer modes
- [ ] Optional: ESP32 + HUB75 LED panel display for outdoor courts
