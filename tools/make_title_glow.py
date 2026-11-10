"""Bakes a soft blue glow around the lightning title, on extra transparent room, so the game needs no haze boxes.

Run:  python3 tools/make_title_glow.py tools/title_original.png <output title.png>
Prints the new size; the menu code uses the same numbers (PAD_X, PAD_Y).
"""
import sys

import cv2
import numpy as np
from PIL import Image

PAD_X, PAD_Y = 120, 100
src, dst = sys.argv[1], sys.argv[2]
im = np.asarray(Image.open(src).convert("RGBA")).astype(np.float32) / 255.0
h, w = im.shape[:2]
H, W = h + 2 * PAD_Y, w + 2 * PAD_X
canvas = np.zeros((H, W, 4), np.float32)
canvas[PAD_Y:PAD_Y + h, PAD_X:PAD_X + w] = im
alpha = canvas[..., 3]
glow = np.zeros((H, W), np.float32)
for sigma, gain in ((6, 0.9), (16, 0.7), (38, 0.5)):
    glow += cv2.GaussianBlur(alpha, (0, 0), sigma) * gain
glow = np.clip(glow, 0, 1) ** 1.15
glow_rgb = np.zeros((H, W, 3), np.float32)
glow_rgb[...] = np.array([0.20, 0.45, 1.0], np.float32)
ga = glow * 0.85
# letters over glow ("over" operator, premultiplied)
la = alpha
out_a = la + ga * (1 - la)
out_rgb = (canvas[..., :3] * la[..., None] + glow_rgb * (ga * (1 - la))[..., None]) / np.maximum(out_a, 1e-4)[..., None]
res = np.dstack([np.clip(out_rgb, 0, 1), np.clip(out_a, 0, 1)])
Image.fromarray((res * 255).astype(np.uint8), "RGBA").save(dst, optimize=True)
print(W, H)
