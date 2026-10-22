"""Track the cyan selection outline and row offset per frame from raw RGB24 at 480x270 on stdin."""
import sys
W, H = 480, 270
data = sys.stdin.buffer.read(); n = len(data) // (W * H * 3)
t0 = float(sys.argv[1]); fps = 60
for i in range(n):
    f = data[i * W * H * 3:(i + 1) * W * H * 3]
    xs = []; ys = []
    for y in range(40, 250, 2):
        for x in range(0, W, 1):
            o = (y * W + x) * 3; r, g, b = f[o], f[o + 1], f[o + 2]
            if b > 160 and g > 120 and r < 90 and b - r > 100: xs.append(x); ys.append(y)
    if xs: print("%.3f cyan x %d..%d y %d..%d n=%d" % (t0 + i / fps, min(xs) * 4, max(xs) * 4, min(ys) * 4, max(ys) * 4, len(xs)))
    else: print("%.3f none" % (t0 + i / fps))
