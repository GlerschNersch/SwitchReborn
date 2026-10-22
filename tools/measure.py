"""Pixel probes on a reference frame: python -I measure.py frame.png"""
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert("RGB"); W, H = im.size
px = im.load()
hx = lambda c: "#%02x%02x%02x" % c
def run(y, x0, x1, pred):
    out, inside, start = [], False, 0
    for x in range(x0, x1):
        p = pred(px[x, y])
        if p and not inside: inside, start = True, x
        if not p and inside: inside = False; out.append((start, x - 1))
    if inside: out.append((start, x1 - 1))
    return out
def col(x, y0, y1, pred):
    out, inside, start = [], False, 0
    for y in range(y0, y1):
        p = pred(px[x, y])
        if p and not inside: inside, start = True, y
        if not p and inside: inside = False; out.append((start, y - 1))
    if inside: out.append((start, y1 - 1))
    return out
bg = px[960, 160]; print("bg", hx(bg), "bg@900", hx(px[960, 920]), "bg@1060", hx(px[300, 1060]))
diff = lambda c: sum(abs(a - b) for a, b in zip(c, bg)) > 30
print("row y=480 runs", run(480, 0, W, diff))
print("col x=350 runs", col(350, 150, H, diff))
print("col x=760 runs", col(760, 150, H, diff))
print("border color", hx(px[150, 480]), hx(px[152, 480]), hx(px[155, 480]), hx(px[350, 281]), hx(px[350, 284]))
print("buttons y=818 runs", run(818, 0, W, diff))
print("col x=1120 buttons", col(1120, 700, 960, diff))
for x in (474, 635, 797, 959, 1120, 1282, 1444): print(" btn", x, hx(px[x - 50, 818]), "icon?", hx(px[x, 790]))
print("rule y", col(960, 940, 1000, diff), hx(px[960, 970]))
print("rule x", run(970, 0, W, diff))
print("title runs y=240", run(240, 100, 700, diff)[:3], "title col", col(250, 180, 280, diff), hx(px[165, 230]))
print("avatar row y=97", run(97, 0, 700, diff)); print("avatar col x=135", col(135, 20, 160, diff))
print("clock row y=98", run(98, 1300, W, diff)); print("clock col x=1420", col(1420, 60, 130, diff))
print("bottom row y=1028", run(1028, 0, W, diff)); print("col x=1465", col(1465, 980, 1075, diff), "col x=135", col(135, 980, 1075, diff))
print("dot", col(692, 740, 780, diff), run(762, 660, 720, diff), hx(px[692, 762]))
