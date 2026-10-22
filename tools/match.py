"""Label sounds in a noisy recording by matching against clean reference sounds.

  python -I match.py clean.wav room.wav start end

1. Splits the clean recording into isolated sounds (templates, >=150 ms of silence between).
2. For every onset in the room recording, cross-correlates the band-passed (1-9 kHz, above
   room hum) waveform with each template and reports the best match and its score.
"""
import sys, wave, numpy as np
from numpy.fft import rfft, irfft

def load(p):
    w = wave.open(p); sr = w.getframerate()
    return np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(float) / 32768, sr

def band(x, sr, lo=1000, hi=9000):
    X = rfft(x); f = np.fft.rfftfreq(len(x), 1 / sr); X[(f < lo) | (f > hi)] = 0
    return irfft(X, len(x))

def envdb(x, hop):
    e = np.array([np.sqrt(np.mean(x[i:i + hop] ** 2) + 1e-12) for i in range(0, len(x) - hop, hop)])
    return 20 * np.log10(e / e.max())

clean, sr = load(sys.argv[1]); room, sr2 = load(sys.argv[2]); assert sr == sr2
hop = int(sr * 0.005)
e = envdb(clean, hop); on = e > -45
temps, i = [], 0
while i < len(on):
    if on[i]:
        j = i
        while j < len(on) and on[j:j + 30].any(): j += 1
        temps.append((i * hop, min(j, i + 160) * hop)); i = j
    else: i += 1
T = [band(clean[a:b], sr) for a, b in temps]
print("templates:", " ".join("T%02d@%.2fs/%dms" % (k + 1, a / sr, (b - a) * 1000 // sr) for k, (a, b) in enumerate(temps)))

s0, s1 = float(sys.argv[3]), float(sys.argv[4])
r = band(room[int(s0 * sr):int(s1 * sr)], sr)
er = envdb(r, hop)
floor = np.array([np.percentile(er[max(0, i - 400):i + 400], 30) for i in range(len(er))])
i = 0
while i < len(er):
    if er[i] - floor[i] > 12:
        a = max(0, i * hop - int(0.02 * sr)); seg = r[a:a + int(0.5 * sr)]
        best = []
        for k, t in enumerate(T):
            n = len(seg) + len(t)
            c = irfft(rfft(seg, n) * np.conj(rfft(t, n)), n)
            m = np.max(np.abs(c[:len(seg)])) / (np.linalg.norm(t) * np.linalg.norm(seg) + 1e-12)
            best.append((m, k))
        best.sort(reverse=True)
        print("t=%7.3f  best T%02d %.2f  next T%02d %.2f" % (s0 + i * hop / sr, best[0][1] + 1, best[0][0], best[1][1] + 1, best[1][0]))
        i += 40
    else: i += 1
