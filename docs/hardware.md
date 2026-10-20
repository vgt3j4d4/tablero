# Hardware

Budget goal: **< US$150 total**, including the screen. Prices are snapshots from Lima retailers (Oct 2026) and
change often.

## Display device (runs tablero tv-app)

| Option | Notes |
|---|---|
| **Xiaomi Mi TV Stick, MDZ-24-AA** (owned) | Amlogic S805Y, 1 GB RAM, Android TV 9 (API 28) out of the box, 10 (API 29) after an OTA update, dual-band Wi-Fi, Bluetooth remote. **Primary target.** |
| Google TV set (e.g. Miray MS32-E2000GBT, Android 14) | The TV itself can run tablero; the stick becomes a spare. |
| Amazon Fire TV Stick 2nd gen, LY73PR (owned) | Model AFTT, Fire OS 5.2 = Android 5.1 (API 22), ~0.9 GB RAM, renders at 1920×1080. Supported (`minSdk 22`); ADB setup in `docs/stick-setup.md`. |

Power the stick from a wall adapter (5 V / 1 A), not the TV's USB port — some TVs cut USB power on standby.
Use the HDMI extender so the stick's antenna isn't flush behind the TV.

## Screen

- 32" is the comfortable size; 24" the minimum. On a 32" TV the game clock digits are ~18 cm tall,
  larger than the 3" (7.6 cm) LED digits of typical portable scoreboards.
- Mount at the centre of a sideline, high and tilted down; avoid facing windows. LCD TVs are fine indoors but
  wash out in direct sunlight.
- Any OS is fine when using the stick (Roku, Tizen, webOS…) — it is just an HDMI screen. Check that the TV
  powers on to the last-used HDMI input.

Candidates found (Oct 2026):

| TV | Store | Price | Speakers |
|---|---|---|---|
| Miray Google TV 32" MS32-E2000GBT | Hiraoka | S/ 399 | 16 W |
| Philips 32" 32PFD6910 | Hiraoka | S/ 399 | n/a |
| Caixun 32" Roku TV C32V1HR | Falabella | S/ 369 | n/a |
| JVC 32" LT-32KM1584 Google TV | Falabella / Sodimac | S/ 399 | n/a |
| TCL 32" QLED FHD Google TV | Hiraoka | S/ 499 | n/a |

Monitors with speakers were considered and rejected: 2×1–3 W speakers and a smaller screen for little savings.

## Audio (buzzer)

- TV speakers (10–16 W) are OK for a quiet gym, weak over a crowd.
- Louder: wired powered speaker on the TV's 3.5 mm / optical output. If the TV has no audio output, insert an
  HDMI audio extractor between stick and TV.
- Avoid Bluetooth speakers for the buzzer: 100–300 ms of added latency.
- The horn is synthesized, not recorded, so it ships under the repo's MIT license: `scripts/make-buzzer.py`
  (Python standard library only) writes `tv-app/src/main/res/raw/buzzer.wav`. Its pitch, length, rasp and level
  are constants at the top of the script; tune them by ear on the TV and run it again. To use a recorded horn
  instead, replace the WAV (keep it short, mono, 16-bit PCM) and check that its license allows redistribution.
- It plays on the media stream at full app volume. On the Xiaomi stick the media volume over HDMI sits at its
  maximum, so the TV's volume alone sets how loud the horn is.

## Network

- Default: the referee phone's **hotspot**; the stick remembers it and auto-joins. No internet required.
- Optional: a small travel router for fixed installations / multiple controllers.

## Controllers

- Phase 1: the stick's Bluetooth remote (~10 m range, no line of sight needed) — scorer at the table.
- Phase 2+: Android phone(s) running remote-app.
- Phase 3: optional Bluetooth selfie clicker (AB Shutter3 type, sends Volume Up) paired to the referee's phone.
