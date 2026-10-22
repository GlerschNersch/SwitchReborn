"""Split a reference recording into sound events and describe each one.

  python -I sound_shape.py rec.wav [--thresh-db -45] [--gap-ms 120]

For every event it prints the start, the length to -40 dB, the attack time, the decay time
constant and the strongest partials (Hz, relative dB). Those numbers drive the synthesis in
Sfx.kt; no recorded audio is used by the app.
"""
import sys, wave, argparse
import numpy as np

ap = argparse.ArgumentParser()
ap.add_argument("wav"); ap.add_argument("--thresh-db", type=float, default=-45)
ap.add_argument("--gap-ms", type=float, default=120); ap.add_argument("--start", type=float, default=0)
ap.add_argument("--end", type=float, default=1e9); ap.add_argument("--partials", type=int, default=5)
a = ap.parse_args()
w = wave.open(a.wav); sr = w.getframerate(); n = w.getnframes()
x = np.frombuffer(w.readframes(n), dtype=np.int16).astype(np.float32) / 32768
if w.getnchannels() > 1: x = x.reshape(-1, w.getnchannels()).mean(1)
x = x[int(a.start * sr):int(min(a.end * sr, len(x)))]
hop = int(sr * 0.005)
env = np.array([np.sqrt(np.mean(x[i:i + hop] ** 2) + 1e-12) for i in range(0, len(x) - hop, hop)])
db = 20 * np.log10(env / env.max())
on = db > a.thresh_db
events, i, gap = [], 0, int(a.gap_ms / 5)
while i < len(on):
    if on[i]:
        j = i
        while j < len(on) and (on[j] or (j + gap < len(on) and on[j:j + gap].any())): j += 1
        events.append((i, j)); i = j
    else: i += 1
for k, (i, j) in enumerate(events):
    seg = x[i * hop:j * hop]; e = env[i:j]
    pk = int(np.argmax(e)); peak = e[pk]
    attack = pk * 5
    # decay: time from peak to -20 dB, expressed as an exponential rate (1/s)
    tail = np.where(20 * np.log10(e[pk:] / peak + 1e-9) < -20)[0]
    t20 = (tail[0] * 5) if len(tail) else (len(e) - pk) * 5
    rate = np.log(10) / (t20 / 1000) if t20 else 0
    win = seg * np.hanning(len(seg)); sp = np.abs(np.fft.rfft(win, n=max(8192, len(seg))))
    f = np.fft.rfftfreq(max(8192, len(seg)), 1 / sr)
    sp[f < 60] = 0
    idx = []
    s2 = sp.copy()
    for _ in range(a.partials):
        m = int(np.argmax(s2)); idx.append(m)
        lo, hi = max(0, m - 12), m + 12; s2[lo:hi] = 0
    ref = sp[idx[0]]
    parts = " ".join(f"{f[m]:.0f}Hz({20*np.log10(sp[m]/ref):.0f})" for m in sorted(idx, key=lambda m: f[m]))
    print(f"#{k+1:02d} t={a.start + i*hop/sr:7.3f}s len={(j-i)*5:5d}ms attack={attack:3d}ms decay={rate:5.1f}/s peak={20*np.log10(peak):5.1f}dBFS  {parts}")
