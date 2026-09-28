"""Synthesizes the klaxon, beeps and dawn loops, so these three need no audio licence.

    python3 tools/gen_sounds.py   # writes app/src/main/res/raw/*.ogg (needs ffmpeg)
"""
import math
import os
import struct
import subprocess
import tempfile
import wave

RATE = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")


def tone(freq, dur, vol=0.6, attack=0.01, release=0.08, harmonics=(1.0,)):
    n = int(RATE * dur)
    out = []
    for i in range(n):
        t = i / RATE
        env = min(1.0, t / attack) * min(1.0, (dur - t) / release)
        s = sum(a * math.sin(2 * math.pi * freq * (k + 1) * t) for k, a in enumerate(harmonics))
        out.append(vol * env * s / sum(harmonics))
    return out


def silence(dur):
    return [0.0] * int(RATE * dur)


def dawn():
    # Soft rising arpeggio, the gentle one.
    notes = [523.25, 659.25, 783.99, 1046.5, 783.99, 659.25]
    s = []
    for f in notes:
        s += tone(f, 0.42, vol=0.5, attack=0.02, release=0.35, harmonics=(1.0, 0.3, 0.1))
    return s + silence(0.6)


def beeps():
    # Classic digital alarm: four fast beeps, pause.
    s = []
    for _ in range(4):
        s += tone(2048, 0.09, vol=0.55, attack=0.002, release=0.01) + silence(0.07)
    return s + silence(0.5)


def klaxon():
    # The "Aaaaaaagh": a detuned sawtooth-ish wail sweeping up.
    dur = 1.1
    n = int(RATE * dur)
    s = []
    phase = 0.0
    for i in range(n):
        t = i / RATE
        f = 330 + 440 * (t / dur) ** 1.5
        phase += 2 * math.pi * f / RATE
        saw = sum(math.sin(k * phase) / k for k in range(1, 8))
        env = min(1.0, t / 0.02) * min(1.0, (dur - t) / 0.05)
        s.append(0.45 * env * saw)
    return s + silence(0.15)


def write(name, samples):
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
        path = f.name
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, x)) * 32767)) for x in samples))
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", path, "-c:a", "libopus", "-b:a", "96k", "-ar", "48000",
                    os.path.join(OUT, name + ".ogg")], check=True)
    os.remove(path)


if __name__ == "__main__":
    write("dawn", dawn())
    write("beeps", beeps())
    write("klaxon", klaxon())
