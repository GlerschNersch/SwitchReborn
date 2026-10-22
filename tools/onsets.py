"""Find short UI sounds in a noisy recording and group look-alikes.

  python -I onsets.py rec.wav start end

Onsets are frames rising >14 dB above the local noise floor. Each sound is described by its
two strongest spectral peaks and its length above the floor, then grouped by those peaks.
"""
import sys, wave, numpy as np
w = wave.open(sys.argv[1]); sr = w.getframerate()
x = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(float) / 32768
s0, s1 = float(sys.argv[2]), float(sys.argv[3]); x = x[int(s0 * sr):int(s1 * sr)]
# High-pass above room rumble so clicks stand out
X = np.fft.rfft(x); f = np.fft.rfftfreq(len(x), 1 / sr); X[f < 300] = 0; y = np.fft.irfft(X, len(x))
hop = int(sr * 0.005)
env = 20 * np.log10(np.array([np.sqrt(np.mean(y[i:i + hop] ** 2) + 1e-12) for i in range(0, len(y) - hop, hop)]))
floor = np.array([np.percentile(env[max(0, i - 400):i + 400], 30) for i in range(0, len(env))])
ev, i = [], 0
while i < len(env):
    if env[i] - floor[i] > 14:
        j = i
        while j < len(env) and env[j] - floor[i] > 8: j += 1
        if j - i >= 2: ev.append((i, j))
        i = j + 20
    else: i += 1
rows = []
for i, j in ev:
    seg = y[i * hop:min(len(y), (i + 60) * hop)]
    sp = np.abs(np.fft.rfft(seg * np.hanning(len(seg)), 16384)); ff = np.fft.rfftfreq(16384, 1 / sr)
    sp[(ff < 300) | (ff > 12000)] = 0
    a = int(np.argmax(sp)); sp2 = sp.copy(); sp2[max(0, a - 40):a + 40] = 0; b = int(np.argmax(sp2))
    rows.append((s0 + i * hop / sr, (j - i) * 5, ff[a], ff[b], env[i:j].max() - floor[i]))
for r in rows: print("t=%7.3f len=%4dms f1=%6.0f f2=%6.0f snr=%4.1fdB" % r)
