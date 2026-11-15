"""Replaces the soft mist in the lower middle of the repaired menu background with sharp scenery: a stretch of sea under the
storm and a wet stone terrace in perspective, with real grout lines, slab-to-slab differences and stone grain, so nothing
in the middle of the picture is blurred. Grain is added to the whole middle so it matches the sharp photo-like sides.

Run:  python3 tools/sharpen_menu_middle.py <repaired background.png> <output background.png>
"""
import sys

import cv2
import numpy as np
from PIL import Image

src, dst = sys.argv[1], sys.argv[2]
im = np.asarray(Image.open(src).convert("RGB")).astype(np.float32)
H, W = im.shape[:2]
X0, X1 = 474, 1062          # the middle that was smeared in the original picture
rng = np.random.default_rng(5)


def fbm(h, w, base, octaves, persist=0.55, seed=0):
    r = np.random.default_rng(seed)
    out = np.zeros((h, w), np.float32)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        gh, gw = max(2, int(base * (2 ** o) * h / w)), max(2, int(base * (2 ** o)))
        g = r.random((gh, gw)).astype(np.float32)
        out += amp * cv2.resize(g, (w, h), interpolation=cv2.INTER_CUBIC)
        tot += amp
        amp *= persist
    out /= tot
    return (out - out.min()) / (out.max() - out.min() + 1e-6)


PAD = 70
x0, x1 = 548, X1 + PAD
w = x1 - x0
ys = np.arange(H, dtype=np.float32)[:, None]
xs = np.arange(x0, x1, dtype=np.float32)[None, :]

# ---- the sea: from the horizon down to the terrace wall
HOR, WALL = 338, 704
sea_t = np.clip((ys - HOR) / (WALL - HOR), 0, 1)
sea = np.zeros((H, w, 3), np.float32)
hazec = np.array([168, 180, 193], np.float32)
deep = np.array([74, 96, 116], np.float32)
sea[:] = hazec * (1 - sea_t[..., None]) ** 1.5 + deep * (1 - (1 - sea_t[..., None]) ** 1.5)
wave = cv2.GaussianBlur(rng.normal(0, 1, (H, w)).astype(np.float32), (0, 0), sigmaX=26, sigmaY=1.1)
sea += (wave * (10 + 34 * sea_t))[..., None]
swell = fbm(H, w, 14, 4, 0.55, 3)
sea *= (0.92 + 0.16 * swell)[..., None]
# wave crests: bands that get wider towards the viewer, broken up by noise, with white caps on the biggest
depth = np.maximum(ys - HOR + 6.0, 1.0)
bands = np.sin(260.0 / np.sqrt(depth) * 3.1 + swell * 5.0) * 0.5 + 0.5
crest = np.clip((bands - 0.72) / 0.28, 0, 1) * np.clip(swell * 1.6 - 0.35, 0, 1) * np.clip(sea_t * 2.0, 0.05, 1)
sea += crest[..., None] * np.array([70, 76, 82], np.float32)
trough = np.clip((0.30 - bands) / 0.30, 0, 1) * sea_t
sea -= trough[..., None] * 18.0
# a crisp horizon line
hl = np.exp(-((ys - HOR) / 1.6) ** 2)
sea += hl[..., None] * 14.0

# ---- the terrace: slabs in perspective, vanishing point above the wall
VY = 600.0
vx = (X0 + X1) / 2.0
t = np.clip((ys - VY) / (H - VY), 0.02, 1.0)               # 0 far, 1 near
Xw = (xs - vx) / t                                          # world x
Zw = 1.0 / t                                                # world depth
SX, SZ = 300.0, 0.50
fx = (Xw / SX) % 1.0
fz = (Zw / SZ) % 1.0
ix = np.floor(Xw / SX)
iz = np.floor(Zw / SZ)
h = np.sin(ix * 12.9898 + iz * 78.233) * 43758.5453
slab = (h - np.floor(h)).astype(np.float32)                  # a number per slab
gx = np.minimum(fx, 1 - fx) * SX * t                         # distance to the grout in screen pixels
gz = np.minimum(fz, 1 - fz) * SZ * (t * t) * (H - VY) * 0.9
g = np.minimum(gx, gz)
grout = np.clip(1 - g / 2.2, 0, 1)
bevel = np.clip(1 - g / 7.0, 0, 1) * 0.25
base = np.array([128, 133, 140], np.float32)
stone = np.zeros((H, w, 3), np.float32)
stone[:] = base
stone *= (0.82 + 0.30 * slab)[..., None]
grain = fbm(H, w, 60, 5, 0.6, 9)
stone *= (0.80 + 0.40 * grain)[..., None]
veins = cv2.GaussianBlur(rng.normal(0, 1, (H, w)).astype(np.float32), (0, 0), 0.7)
stone += veins[..., None] * 5.0
stone *= (1 + bevel[..., None] * 0.9)
stone = stone * (1 - grout[..., None] * 0.85) + np.array([30, 33, 38], np.float32) * (grout[..., None] * 0.85)
# wet stone: it mirrors the pale sky near the wall and gets darker and coarser close to the viewer
wet = np.clip(1 - (ys - WALL) / 120.0, 0, 1) ** 1.6
stone = stone * (1 - 0.30 * wet[..., None]) + hazec * (0.30 * wet[..., None])
stone *= (1.0 - 0.18 * np.clip((ys - 700) / 186.0, 0, 1))[..., None]

# the wall at the far edge of the terrace: a dark strip with a lit top edge
wall = np.zeros((H, w), np.float32)
wall[WALL - 7:WALL + 3, :] = 1
wall = cv2.GaussianBlur(wall, (0, 0), 0.8)
top = np.zeros((H, w), np.float32)
top[WALL - 9:WALL - 6, :] = 1
scene = np.where(ys[..., None] < WALL - 4, sea, stone)
scene = scene * (1 - wall[..., None] * 0.7) + np.array([40, 44, 52], np.float32) * (wall[..., None] * 0.7)
scene += top[..., None] * 40

# ---- put it into the picture
ramp = np.clip((ys - (HOR - 30)) / 40.0, 0, 1)               # fade in from the sky above the horizon
ramp = ramp * ramp * (3 - 2 * ramp)
edge = np.ones((1, w), np.float32)
f = 84
edge[0, :f] = np.linspace(0, 1, f)
edge[0, -f:] = np.linspace(1, 0, f)
edge = edge * edge * (3 - 2 * edge)
m = (ramp * edge)[..., None]
out = im.copy()
out[:, x0:x1] = im[:, x0:x1] * (1 - m) + scene * m
# grain over the middle so it carries the same fine detail as the sides
gm = np.zeros((H, W, 1), np.float32)
gm[:, X0 - 20:X1 + 20] = 1
out += rng.normal(0, 2.6, (H, W, 1)).astype(np.float32) * gm
# a gentle sharpening of the middle only
blur = cv2.GaussianBlur(out, (0, 0), 1.6)
amt = 0.7 * gm
out = out + (out - blur) * amt
Image.fromarray(np.clip(out, 0, 255).astype(np.uint8)).save(dst, optimize=True)
print("ok")
