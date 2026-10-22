"""Envelope + spectrogram of a reference recording: python -I spectro.py in.wav out.png [start end]"""
import sys, wave, numpy as np
import matplotlib; matplotlib.use("Agg"); import matplotlib.pyplot as plt
w = wave.open(sys.argv[1]); sr = w.getframerate()
x = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(float) / 32768
s0 = float(sys.argv[3]) if len(sys.argv) > 3 else 0; s1 = float(sys.argv[4]) if len(sys.argv) > 4 else len(x) / sr
x = x[int(s0 * sr):int(s1 * sr)]
hop = int(sr * 0.005)
env = np.array([np.sqrt(np.mean(x[i:i + hop] ** 2) + 1e-12) for i in range(0, len(x) - hop, hop)])
fig, ax = plt.subplots(2, 1, figsize=(28, 7), sharex=True)
ax[0].plot(s0 + np.arange(len(env)) * 0.005, 20 * np.log10(env / env.max()), lw=0.6); ax[0].set_ylim(-70, 0)
ax[0].set_xticks(np.arange(int(s0), s1 + 1, max(1, int((s1 - s0) / 40)))); ax[0].grid(alpha=.3)
ax[1].specgram(x, NFFT=1024, Fs=sr, noverlap=896, cmap="magma", vmin=-120, xextent=(s0, s1)); ax[1].set_ylim(0, 10000)
plt.tight_layout(); plt.savefig(sys.argv[2], dpi=70)
