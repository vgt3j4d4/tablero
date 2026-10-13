# Roadmap

## Phase 1 — TV app + stick remote

Scope: two color teams, scores, game clock, buzzer, undo, rotation for pickup sessions (see `CLAUDE.md` →
Phase 1 scope). Shot clock, fouls, timeouts, periods and possession are deferred.

- [ ] Gradle multi-module scaffold (`shared`, `tv-app`), version catalog, CI-friendly `./gradlew test`
- [ ] `shared`: `TimeSource`, `GameEngine`, `Command`, `GameSnapshot`, `GameEvent`, undo history (+ undo semantics tests)
- [ ] `shared`: `AdjustGameClock`, `SetGameClock`, `ResetGameClock`, `NewGame`, display rounding + unit tests
- [ ] `shared`: `Rotation` (winner stays, rest after 2 wins, tie → both to the line) as data + unit tests
- [ ] `shared`: state serialization (JSON) for persistence
- [ ] `tv-app`: single activity, `HOME` intent filter, `FLAG_KEEP_SCREEN_ON`
- [ ] `tv-app`: string resources — English default + Spanish, `locales_config.xml`, in-app language setting,
      lint `MissingTranslation` as error
- [ ] `tv-app`: `ScoreboardView` (Canvas) — clock + scores in DSEG7 with ghost segments, team swatches, status bar
- [ ] `tv-app`: remote key adapter (OK on key-down, short/long press mapping, see CLAUDE.md)
- [ ] `tv-app`: quick clock adjust overlay (↓)
- [ ] `tv-app`: time-up bar + next-game picker
- [ ] `tv-app`: menu overlay (game clock, game length, scores, teams, new game, language, exit)
- [ ] `tv-app`: guarded exit to Android TV (stopped clock only, hold OK 2 s)
- [ ] `tv-app`: persistence (`AtomicFile`, restore on start, reboot detection)
- [ ] `tv-app`: buzzer sound via `SoundPool`
- [ ] Test on Android TV emulator, then on the Xiaomi stick
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
