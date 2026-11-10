"""Repairs the menu background: crops the dark borders and paints a clean stormy sky and mist into the smeared middle.

Run:  python3 tools/make_menu_background.py <original background.png> <output background.png>
"""
import sys

import cv2
import numpy as np
from PIL import Image

src, dst = sys.argv[1], sys.argv[2]
im = np.asarray(Image.open(src).convert("RGB")).astype(np.float32)
im = im[10:896, 14:1546]            # drop the dark borders and the smeared strip at the bottom
H, W = im.shape[:2]
X0, X1 = 474, 1062                  # the smeared part of the picture
rng = np.random.default_rng(7)


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
    return (out - out.min()) / (out.max() - out.min())


# colours of the sky next to the smear, row by row
left = im[:, X0 - 80:X0 - 24].mean(axis=1)
right = im[:, X1 + 10:X1 + 64].mean(axis=1)
left = cv2.GaussianBlur(left, (1, 121), 0)
right = cv2.GaussianBlur(right, (1, 121), 0)
t = np.linspace(0, 1, X1 - X0, dtype=np.float32)[None, :, None]
t = t * t * (3 - 2 * t)
base = left[:, None, :] * (1 - t) + right[:, None, :] * t        # (H, w, 3)

# below the sky line the neighbours are the temple and the statue, so use the pale sky at the horizon instead
horizon = im[360:520, 250:330].reshape(-1, 3).mean(axis=0)
yv = np.arange(H, dtype=np.float32)[:, None, None]
wb = np.clip((yv - 120) / 330.0, 0, 1)
wb = wb * wb * (3 - 2 * wb)
base = base * (1 - wb) + horizon[None, None, :] * wb

# soft overcast clouds: stretched noise, as strong as the clouds on either side of the smear
w = X1 - X0
yy = np.arange(H, dtype=np.float32)[:, None] / H
ref = np.concatenate([im[:, X0 - 160:X0 - 24], im[:, X1 + 10:X1 + 60]], axis=1).mean(axis=2)
rowstd = cv2.GaussianBlur(ref.std(axis=1).astype(np.float32).reshape(H, 1), (1, 81), 0).reshape(H, 1)
n1 = fbm(H, w, 4, 6, 0.6, 1)
n2 = fbm(H, w, 7, 5, 0.5, 2)
n1 = cv2.resize(n1[::3], (w, H), interpolation=cv2.INTER_CUBIC) * 0.5 + n1 * 0.5      # a little stretched sideways
cl = 0.65 * n1 + 0.35 * n2
cl = cv2.GaussianBlur(cl, (0, 0), 1.6)
cl = (cl - cl.mean()) / (cl.std() + 1e-6)
sky_amt = np.clip(1.15 - yy * 2.0, 0, 1)
amp = (np.minimum(rowstd, 15.0) * 0.85 * (0.2 + 0.8 * sky_amt))[..., None]
fill = base + cl[..., None] * amp * np.array([1.0, 1.0, 1.04], np.float32)

# a pale glow towards the god, so the middle is not flat
glow = np.exp(-(((np.arange(w)[None, :] - w * 0.8) / (w * 0.45)) ** 2) - (((yy - 0.36) / 0.3) ** 2))[..., None]
fill += glow * np.array([9, 10, 13], np.float32)

# the bluff and the temple on the left: the temple is cut off by the smear, so complete it as a mirror image
M = 441                                   # the middle of the temple's front
xs = np.arange(X0, X1 + 1)
srcx = 2 * M - xs
ok = (srcx >= 200) & (srcx < X0)
mirror = np.zeros((H, w + 1, 3), np.float32)
mirror[:, ok] = im[:, srcx[ok]]
mx = np.clip(1 - (xs - (M + 70)) / 120.0, 0, 1)          # fades out to the right of the temple
mx = mx * mx * (3 - 2 * mx)
my = np.clip((np.arange(H, dtype=np.float32) - 330) / 40.0, 0, 1)       # only below the sky line
mm = (mx[None, :] * my[:, None] * ok[None, :].astype(np.float32))[..., None]
fill = fill * (1 - mm[:, :w]) + mirror[:, :w] * mm[:, :w]

# sea mist in the lower part: long soft streaks
mist = fbm(H, w, 2, 4, 0.6, 5)
mist = cv2.resize(cv2.resize(mist, (w, H // 16)), (w, H), interpolation=cv2.INTER_CUBIC)
mist = (mist - mist.min()) / (mist.max() - mist.min())
mist_amt = np.clip((yy - 0.62) * 2.6, 0, 1)[..., None] * (1 - mm[:, :w] * 0.9)
fog = np.array([70, 80, 94], np.float32)
fill = fill * (1 - 0.6 * mist_amt) + (fog + 26 * mist[..., None]) * 0.6 * mist_amt
# darken towards the bottom so the menu rests on a shadowed terrace
dk = np.clip((yy - 0.86) / 0.14, 0, 1)[..., None] * 0.55
fill = fill * (1 - dk)
fill = np.clip(fill, 0, 255)

# put it in with a soft seam on both sides
m0 = np.zeros((H, W), np.float32)
for y in range(H):
    # the right edge of the smear is further right in the sky than down at the little column
    x1 = int(X1 - 18 * min(1.0, max(0.0, (y - 400) / 60.0)))
    # the temple was cut off straight at x=497, so below the sky the picture is replaced from there
    x0 = int(474 + 23 * min(1.0, max(0.0, (y - 300) / 40.0)))
    m0[y, x0:x1] = 1
wide = np.clip(cv2.GaussianBlur(m0, (0, 0), 34) * 1.3 - 0.15, 0, 1)
narrow = np.clip(cv2.GaussianBlur(m0, (0, 0), 4) * 1.1 - 0.05, 0, 1)
narrow_r = np.clip(cv2.GaussianBlur(m0, (0, 0), 30) * 1.4 - 0.2, 0, 1)
xm = (X0 + X1) // 2
narrow[:, xm:] = narrow_r[:, xm:]
vw = np.clip((np.arange(H, dtype=np.float32) - 300) / 120.0, 0, 1)[:, None]
mask = wide * (1 - vw) + narrow * vw
out = im.copy()
PAD = 80
padded = np.pad(fill, ((0, 0), (PAD, PAD), (0, 0)), mode="edge")
canvas = np.zeros_like(im)
canvas[:, X0 - PAD:X1 + PAD] = padded
out = out * (1 - mask[..., None]) + canvas * mask[..., None]
# a little film grain over the new part so it matches the rest
grain = rng.normal(0, 2.2, (H, W, 1)).astype(np.float32)
out = np.clip(out + grain * mask[..., None], 0, 255)
Image.fromarray(out.astype(np.uint8)).save(dst, optimize=True)
print("ok", out.shape)
