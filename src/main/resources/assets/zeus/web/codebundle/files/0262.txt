"""Gives the menu background its final look: a smooth dark pool behind the buttons, softly darkened sides, and a gentle blue
tone, all baked into the picture so the game draws no bands or boxes over it.

Run:  python3 tools/grade_menu_background.py <repaired background.png> <output background.png>
"""
import sys

import cv2
import numpy as np
from PIL import Image

src, dst = sys.argv[1], sys.argv[2]
im = np.asarray(Image.open(src).convert("RGB")).astype(np.float32)
H, W = im.shape[:2]
xs = (np.arange(W, dtype=np.float32) / (W - 1) - 0.5)[None, :]
ys = (np.arange(H, dtype=np.float32) / (H - 1) - 0.5)[:, None]

# the dark pool behind the tablets: a wide soft plateau (a flattened bell), a little stronger low down where the buttons are
pool = np.exp(-np.power(np.abs(xs) / 0.19, 3.2))
pool = pool * (0.26 + 0.10 * np.clip(ys + 0.5, 0, 1))
# the sides fall gently into shadow
side = np.clip((np.abs(xs) - 0.30) / 0.20, 0, 1)
side = side * side * (3 - 2 * side) * 0.34
# top and bottom
top = np.clip(1 - (ys + 0.5) / 0.22, 0, 1) ** 2 * 0.30
bot = np.clip(((ys + 0.5) - 0.74) / 0.26, 0, 1) ** 2 * 0.46
a = np.clip(1 - (1 - pool) * (1 - side) * (1 - top) * (1 - bot), 0, 0.82)
a = cv2.GaussianBlur(a, (0, 0), 4)
shade = np.array([7, 12, 24], np.float32)
out = im * (1 - a[..., None]) + shade * a[..., None]
# a faint cool lift in the middle so the dark pool does not look flat
lift = np.exp(-(np.power(xs / 0.16, 2) + np.power((ys + 0.12) / 0.30, 2)))
out += lift[..., None] * np.array([6, 12, 26], np.float32) * 0.9
Image.fromarray(np.clip(out, 0, 255).astype(np.uint8)).save(dst, optimize=True)
print("ok", out.shape)
