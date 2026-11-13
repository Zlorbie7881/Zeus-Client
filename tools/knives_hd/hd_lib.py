"""Shared helpers for the HD tactical knives.

Everything is authored on a supersampled grid in knife units:
  u = distance along the knife from the pommel end (0 .. LEN)
  v = distance across the knife, negative on the spine side, positive on the edge side

The finished sheets are downsampled from that grid, so every outline is anti-aliased.
"""

import math

import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy import ndimage

LEN = 28.4          # knife units covered by the sheet width
HALF = 3.55         # knife units from the centre line to the sheet's top and bottom
W, H = 1024, 256    # one cell of the finished sheet (level 0)
SS = 2              # supersampling factor
PX = W / LEN        # pixels per knife unit at level 0
GW, GH = W * SS, H * SS
GP = PX * SS        # pixels per unit on the working grid

_y, _x = np.mgrid[0:GH, 0:GW].astype(np.float32)
U = (_x + 0.5) / GP
V = (_y + 0.5) / GP - HALF
del _x, _y


def to_px(u, v):
    return (u * GP, (v + HALF) * GP)


# ------------------------------------------------------------------ shapes
def poly_mask(points):
    """points in (u, v) knife units -> float mask on the working grid."""
    img = Image.new("L", (GW, GH), 0)
    ImageDraw.Draw(img).polygon([to_px(u, v) for u, v in points], fill=255)
    return np.asarray(img, np.float32) / 255.0


def ellipse_mask(cu, cv, ru, rv):
    return (((U - cu) / ru) ** 2 + ((V - cv) / rv) ** 2 <= 1.0).astype(np.float32)


def capsule_mask(u0, v0, u1, v1, r):
    """Distance to a segment <= r."""
    du, dv = u1 - u0, v1 - v0
    L2 = du * du + dv * dv
    t = np.clip(((U - u0) * du + (V - v0) * dv) / max(L2, 1e-9), 0, 1)
    d = np.hypot(U - (u0 + t * du), V - (v0 + t * dv))
    return d, (d <= r).astype(np.float32)


def sdf_inside(mask):
    """Distance in knife units from each inside pixel to the outline (0 outside)."""
    m = mask > 0.5
    return ndimage.distance_transform_edt(m).astype(np.float32) / GP


def sdf_outside(mask):
    m = mask > 0.5
    return ndimage.distance_transform_edt(~m).astype(np.float32) / GP


def smooth(x, sigma_units):
    return ndimage.gaussian_filter(x, sigma_units * GP)


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


# ------------------------------------------------------------------ noise
_rng = np.random.default_rng(1234)


def value_noise(scale_units, seed, aniso=(1.0, 1.0)):
    """Smooth noise with features about scale_units across; aniso stretches u and v."""
    rng = np.random.default_rng(seed)
    gu = max(2, int(LEN / (scale_units * aniso[0])) + 3)
    gv = max(2, int(2 * HALF / (scale_units * aniso[1])) + 3)
    grid = rng.random((gv, gu)).astype(np.float32)
    zy = GH / (gv - 2)
    zx = GW / (gu - 2)
    big = ndimage.zoom(grid, (zy, zx), order=3)
    return np.clip(big[:GH, :GW], 0, 1)


def fbm(scale_units, seed, octaves=5, aniso=(1.0, 1.0), gain=0.5):
    out = np.zeros((GH, GW), np.float32)
    amp, tot, s = 1.0, 0.0, scale_units
    for o in range(octaves):
        out += amp * value_noise(s, seed + o * 101, aniso)
        tot += amp
        amp *= gain
        s *= 0.5
        if s * GP < 1.5:
            break
    return out / tot


def warp(field_fn, strength, scale, seed):
    """Domain warp: sample field_fn at displaced coordinates."""
    du = (fbm(scale, seed, 4) - 0.5) * strength
    dv = (fbm(scale, seed + 7, 4) - 0.5) * strength
    return field_fn(U + du, V + dv)


def brushed(seed, strength=1.0):
    """Fine lengthwise grain of ground steel."""
    rng = np.random.default_rng(seed)
    n = rng.normal(0, 1, (GH, GW)).astype(np.float32)
    n = ndimage.gaussian_filter(n, (0.6, 9.0))
    n /= (np.abs(n).max() + 1e-6)
    return n * strength


# ------------------------------------------------------------------ colour
def hexc(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], np.float32) / 255.0


def lerp(a, b, t):
    t = np.asarray(t, np.float32)
    if t.ndim == 2:
        t = t[..., None]
    return a * (1 - t) + b * t


def ramp(t, stops):
    """stops: list of (pos, colour)."""
    t = np.clip(t, 0, 1)
    out = np.zeros(t.shape + (3,), np.float32)
    out[...] = stops[0][1]
    for (p0, c0), (p1, c1) in zip(stops, stops[1:]):
        k = np.clip((t - p0) / max(p1 - p0, 1e-6), 0, 1)
        sel = t >= p0
        mix = c0 * (1 - k[..., None]) + c1 * k[..., None]
        out = np.where(sel[..., None], mix, out)
    return out


# ------------------------------------------------------------------ text
FONT_DIR = None


def text_mask(text, u, v, size_units, font_path, angle=0.0):
    """Etched lettering as a mask, centred at (u, v), reading along the knife."""
    size = max(8, int(size_units * GP))
    font = ImageFont.truetype(font_path, size)
    box = font.getbbox(text)
    tw, th = box[2] - box[0] + 8, box[3] - box[1] + 8
    im = Image.new("L", (tw, th), 0)
    ImageDraw.Draw(im).text((4 - box[0], 4 - box[1]), text, font=font, fill=255)
    if angle:
        im = im.rotate(angle, expand=True, resample=Image.BICUBIC)
    out = Image.new("L", (GW, GH), 0)
    cx, cy = to_px(u, v)
    out.paste(im, (int(cx - im.width / 2), int(cy - im.height / 2)))
    return np.asarray(out, np.float32) / 255.0


# ------------------------------------------------------------------ lighting
def normals(height, strength=1.0):
    gy, gx = np.gradient(height * strength, 1.0 / GP)
    n = np.stack([-gx, -gy, np.ones_like(height)], -1)
    n /= np.linalg.norm(n, axis=-1, keepdims=True)
    return n


def downsample(arr, f):
    if f == 1:
        return arr
    h, w = arr.shape[:2]
    if arr.ndim == 2:
        return arr.reshape(h // f, f, w // f, f).mean((1, 3))
    return arr.reshape(h // f, f, w // f, f, arr.shape[2]).mean((1, 3))
