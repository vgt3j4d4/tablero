#!/usr/bin/env python3
"""Synthesizes the time-up buzzer: tv-app/src/main/res/raw/buzzer.wav.

The horn is generated rather than recorded so it ships under the repo's own MIT license. It is two slightly
detuned, band-limited sawtooth oscillators (the detune gives the rasp of an arena horn), soft-clipped for
loudness on small TV speakers, with a short attack and release so it starts and ends without clicks.

Tune the constants below by ear on the TV and run the script again. Standard library only:

    python3 scripts/make-buzzer.py            # writes the file in the tv-app
    python3 scripts/make-buzzer.py out.wav    # writes somewhere else, to compare
"""

import math
import sys
import wave
from array import array
from pathlib import Path

SAMPLE_RATE_HZ = 48_000
DURATION_S = 1.5
ATTACK_S = 0.015
RELEASE_S = 0.06

# Two oscillators a few hertz apart; the beating between them makes the horn rough rather than a pure tone.
FREQUENCIES_HZ = (400.0, 404.0)
# Harmonics stop below this so nothing aliases, and small TV speakers can't play much above it anyway.
MAX_HARMONIC_HZ = 9_000.0
# How hard the soft clipper (tanh) is driven: higher is louder and harsher.
DRIVE = 2.5
PEAK_DBFS = -1.0

BITS_PER_SAMPLE = 16
FULL_SCALE = 2 ** (BITS_PER_SAMPLE - 1) - 1
DEFAULT_OUTPUT = Path(__file__).resolve().parent.parent / "tv-app/src/main/res/raw/buzzer.wav"


def sawtooth(frequency_hz: float, t: float) -> float:
    """A band-limited sawtooth at time [t]: its harmonics up to MAX_HARMONIC_HZ, each at 1/n."""
    harmonics = int(MAX_HARMONIC_HZ // frequency_hz)
    phase = 2 * math.pi * frequency_hz * t
    return sum(math.sin(n * phase) / n for n in range(1, harmonics + 1)) * (2 / math.pi)


def envelope(t: float) -> float:
    """Linear fade in over ATTACK_S and out over RELEASE_S; full level in between."""
    return max(0.0, min(1.0, t / ATTACK_S, (DURATION_S - t) / RELEASE_S))


def synthesize() -> list[float]:
    samples = []
    for i in range(int(SAMPLE_RATE_HZ * DURATION_S)):
        t = i / SAMPLE_RATE_HZ
        tone = sum(sawtooth(f, t) for f in FREQUENCIES_HZ) / len(FREQUENCIES_HZ)
        samples.append(math.tanh(DRIVE * tone) * envelope(t))
    peak = max(abs(s) for s in samples)
    gain = 10 ** (PEAK_DBFS / 20) / peak
    return [s * gain for s in samples]


def write_wav(path: Path, samples: list[float]) -> None:
    pcm = array("h", (round(s * FULL_SCALE) for s in samples))
    if sys.byteorder == "big":
        pcm.byteswap()  # WAV is little-endian.
    with wave.open(str(path), "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(BITS_PER_SAMPLE // 8)
        out.setframerate(SAMPLE_RATE_HZ)
        out.writeframes(pcm.tobytes())


def main() -> None:
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_OUTPUT
    write_wav(path, synthesize())
    print(f"Wrote {path} ({DURATION_S} s, {SAMPLE_RATE_HZ} Hz mono)")


if __name__ == "__main__":
    main()
