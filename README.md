# tablero

DIY scoreboard for pickup basketball: an Android TV app on a cheap TV + streaming stick, controlled by the
stick's remote — no internet required. Later: a full 24/14 shot clock, other sports, and control from an Android
phone over local Wi-Fi.

```
 ┌────────────────────────────────┐        HDMI        ┌──────────────────────────────┐
 │  Android TV stick / Google TV  │ ─────────────────▶ │  TV (24–32", built-in audio) │
 │  tablero tv-app                │                    │  scoreboard + buzzer         │
 │  • GameEngine (owns the clock) │                    └──────────────────────────────┘
 │  • WebSocket server (Phase 2)  │
 └────────────────▲───────────────┘
        Bluetooth │ remote (Phase 1)        local Wi-Fi (phone hotspot) │ WebSocket (Phase 2)
                  │                                                     │
          ┌───────┴──────┐                                    ┌─────────┴──────────┐
          │ Stick remote │                                    │ tablero remote-app │
          │ (scorer)     │                                    │ (referee / table)  │
          └──────────────┘                                    └────────────────────┘
```

## Why

Portable LED scoreboards cost ~US$120 (~US$250 with a shot clock) and more once shipped to Peru, and they are
awkward to correct mid-game. A cheap TV plus an Android TV stick gives bigger digits, a clock you can fix in a
couple of key presses, undo, and a quick way to start the next game — for under US$150 total. The same
setup can grow into a proper 24/14 shot clock and phone control.

## Features (planned)

- **Phase 1 — pickup sessions:** two teams identified by jersey color, scores, game clock (tenths under 1:00)
  with LED-style digits, buzzer, undo, quick clock adjust, and a next-game team picker.
- Control from the stick's Bluetooth remote (Phase 1).
- English and Spanish built in; adding a language is a translation-only change.
- Later: 24/14 shot clock, periods, team fouls / bonus, timeouts, possession arrow and presets (FIBA, NBA, NCAA,
  FIBA 3x3); futsal, volleyball.
- Control from an Android phone over local Wi-Fi, also with the phone locked via volume keys or a
  Bluetooth clicker (Phase 2–3).
- Multiple controllers with roles (referee / table) and undo.

## Hardware

| Part           | Example                                                                   | Approx. cost (Lima, Oct 2026) |
| -------------- | ------------------------------------------------------------------------- | ----------------------------- |
| Display device | Xiaomi Mi TV Stick (MDZ-24-AA, Android TV 9) — or any Google TV set       | already owned                 |
| TV             | 32" HD, e.g. Miray MS32-E2000GBT (Google TV, 16 W speakers)               | ~S/ 399                       |
| Optional       | Wired powered speaker (+ HDMI audio extractor if the TV has no audio out) | ~S/ 100–170                   |
| Controller     | Android phone (hotspot + remote app)                                      | owned                         |

Details: [`docs/hardware.md`](docs/hardware.md).

## Status

Early development — the Phase 1 design is agreed and the game engine is in place; the TV UI is next. See [`docs/roadmap.md`](docs/roadmap.md).

| Phase | Scope                                                             | Status                        |
| ----- | ----------------------------------------------------------------- | ----------------------------- |
| 1     | TV app + stick remote                                             | engine done, TV UI next       |
| 2     | Phone controller over Wi-Fi                                       | —                             |
| 3     | Locked-phone control (volume keys, clicker, notification)         | —                             |
| 4     | Full basketball rules, roles, branding, more languages and sports | —                             |

## Getting started (developers)

Requires JDK 17+ and the Android SDK (Android Studio recent enough for AGP 9.4).

```bash
./gradlew :shared:test                 # engine unit tests
./gradlew :tv-app:assembleDebug
adb connect <stick-ip>:5555
./gradlew :tv-app:installDebug
```

Preparing the stick (developer mode, debloat, boot into tablero): [`docs/stick-setup.md`](docs/stick-setup.md).

## Docs

- [Design (TV screens)](docs/design/README.md)
- [Architecture](docs/architecture.md)
- [Hardware](docs/hardware.md)
- [Stick setup](docs/stick-setup.md)
- [Roadmap](docs/roadmap.md)

## License

[MIT](LICENSE). Bundled fonts keep their own license (SIL Open Font License 1.1) and ship with their license
files.
