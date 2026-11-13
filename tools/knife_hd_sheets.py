#!/usr/bin/env python3
"""Generate the HD knife atlas sheets and tactical.json.

The sheet is a 7 cell strip per knife, one cell per material layer, exactly as KnifeHd.java
reads it:

    0 FACE   the artwork: alpha is the outline, colour is the skin
    1 RIM50  the same art, 50% inset shading (the shoulder of a rounded handle)
    2 RIM13  the same art, 13% inset shading
    3 SIDE   flat colour of the side wall
    4 GLOSS  white highlight mask (very low alpha), added on top
    5 EDGE   bevel glint mask, added on top
    6 PROFILE the strip through the centre line and the thickness axis

u runs along the knife in sheet units (0..len), v across it (-half..+half).  Everything is
drawn from the outline functions below, so a knife's silhouette - its saw teeth, fuller slot,
lanyard ring, grip grooves - is in the alpha channel at full resolution.

Run: python3 tools/knife_hd_sheets.py
"""

import json
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(ROOT, "src/main/resources/assets/zeus/textures/knives/hd")
JSON = os.path.join(ROOT, "src/main/resources/assets/zeus/knives/hd")

LEN = 28.4
HALF = 3.55
ZMAX = 1.4
CELLS = 7
# (cell width, cell height) per detail level: the cell is square-ish in world units,
# 28.4 long by 7.1 wide, so 1024x256 keeps the blade's proportions at full detail.
LEVELS = [(1024, 256), (512, 128), (256, 64)]


def lerp(a, b, t):
    return a + (b - a) * t


def clamp01(x):
    return max(0.0, min(1.0, x))


def smooth(x):
    x = clamp01(x)
    return x * x * (3.0 - 2.0 * x)


# ----------------------------------------------------------------- geometry
class Knife:
    """Outline in (u, v) sheet units: u along, v across, 0 in the middle of the grip."""

    def __init__(self, ident, name, glow, parts, handle, blade, tip, teeth=None,
                 fuller=True, ring=None, ferrule=None, guard=True, slot=None):
        self.id = ident
        self.name = name
        self.glow = glow
        self.parts = parts          # [(name, u0, u1, t, r)]
        self.handle = handle        # (u0, u1, halfw, style)
        self.blade = blade          # (u0, tip_u, spine_fn, edge_fn)
        self.tip = tip              # [u, v]
        self.teeth = teeth or []
        self.fuller = fuller
        self.ring = ring            # (u_centre, r_out, r_in) lanyard ring at the pommel
        self.ferrule = ferrule      # (u0, u1) brass band
        self.guard = guard
        self.slot = slot            # (u0, u1, halfh) fuller slot

    # -- silhouette ---------------------------------------------------------
    def inside(self, u, v):
        if u < 0.15 or u > LEN - 0.15:
            return False
        if self.ring is not None:
            cu, ro, ri = self.ring
            d = math.hypot(u - cu, v)
            if d <= ro:
                return d >= ri
        if u >= 0 and u < 1.5:
            # pommel cap
            if abs(v) <= 1.30:
                return True
        h0, h1, hw, style = self.handle
        if h0 <= u <= h1:
            w = self.grip_half(u, hw, style)
            if abs(v) > w:
                return False
            if style == "skeleton":
                for cu in (3.4, 6.6, 9.8):
                    if (u - cu) ** 2 / 0.55 ** 2 + (v / 0.62) ** 2 < 1.0:
                        return False
            return True
        if self.ferrule and self.ferrule[0] <= u <= self.ferrule[1]:
            return abs(v) <= 1.34
        if self.guard and 12.55 <= u <= 13.35:
            return abs(v) <= 1.62
        b0, tipu, spine, edge = self.blade
        if b0 <= u <= tipu:
            fs, fe = spine(u), edge(u)   # spine is the +v side, edge the -v side
            if fs <= fe:
                return False
            if not (fe <= v <= fs):
                return False
            for t0, t1, depth, count in self.teeth:
                if t0 <= u <= t1:
                    k = (u - t0) / (t1 - t0) * count
                    if (k % 1.0) < 0.45 and v > fs - depth:
                        return False
            return True
        return False

    def grip_half(self, u, hw, style):
        h0, h1, _, _ = self.handle
        t = (u - h0) / max(1e-6, h1 - h0)
        w = hw * (1.0 - 0.16 * clamp01((t - 0.75) / 0.25))
        if style in ("groove", "camo", "wood", "tiger"):
            w += 0.10 * abs(math.sin(math.pi * 3.0 * t))
        if style == "curve":
            w *= 1.0 + 0.10 * math.sin(math.pi * t)
        return w

    def centre_curve(self, u):
        """How far the knife bows away from v=0 (curved grips sit off centre)."""
        if self.handle[3] in ("curve", "camo"):
            t = clamp01((u - 0.5) / 13.0)
            return -1.15 * math.sin(math.pi * t) * (1.0 if u < 13.0 else 0.0)
        return 0.0


def blade_profile(b0, tipu, wid, clip=0.0, tipv=0.0, teeth_side=0):
    """Spine and edge as functions of u.

    The spine runs straight and then chamfers down into the tip (a clip point); the edge
    sweeps up to meet it over the last quarter, so the knife ends in a point of the outline
    rather than a needle stuck on a wide blade.
    """

    def spine(u):
        t = clamp01((u - b0) / (tipu - b0))
        if t > 0.58:
            return lerp(wid, 0.10 * wid, (t - 0.58) / 0.42)
        return wid

    def edge(u):
        t = clamp01((u - b0) / (tipu - b0))
        e = -wid * (1.0 + 0.05 * math.sin(math.pi * t))
        if t > 0.74:
            e = lerp(e, -0.10 * wid, ((t - 0.74) / 0.26) ** 1.35)
        return e

    return spine, edge


# ----------------------------------------------------------------- art
# The face cell is painted, not sampled: the alpha comes from the analytic outline drawn at
# 4x and filtered down, and the colour from a height-field style shading pass, so edges are
# smooth, the grind is a real bevel with a specular band, the fuller is a groove and the brass
# parts have a round, lit cross section.

ART_SCALE = 4


def sstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def mix(c0, c1, t):
    t = np.asarray(t, dtype=np.float64)
    if t.ndim == 2:
        t = t[..., None]
    return np.asarray(c0, dtype=np.float64) * (1.0 - t) + np.asarray(c1, dtype=np.float64) * t


def blend(c, colour, w):
    w = np.asarray(w, dtype=np.float64)
    if w.ndim == 2:
        w = w[..., None]
    return c * (1.0 - w) + np.asarray(colour, dtype=np.float64) * w


def noise(u, v, seed):
    a = np.sin(u * 1.7 + v * 2.3 + seed) * np.sin(u * 0.63 - v * 1.9 + seed * 1.7)
    b = np.sin(u * 4.3 - v * 3.1 + seed * 0.4) * np.sin(u * 2.1 + v * 1.1)
    c = np.sin(u * 9.0 + v * 7.3 + seed * 2.2) * np.sin(u * 5.7 - v * 3.3)
    return 0.55 * a + 0.32 * b + 0.13 * c


def skin_colour(kind, U, V, b0, tipu):
    """The blade finish, as smooth fields rather than hard thresholds."""
    t = np.clip((U - b0) / max(1e-6, tipu - b0), 0.0, 1.0)
    n = noise(U * 0.9, V * 1.6, 1.0)
    if kind == "case":
        p = noise(U * 0.55, V * 0.9, 2.0)
        q = noise(U * 1.6 + 3.0, V * 2.2, 5.0)
        c = mix((50, 70, 150), (198, 160, 76), sstep(-0.50, 0.30, p))
        c = blend(c, (118, 74, 106), 0.50 * sstep(0.10, 0.70, q))
        c = blend(c, (228, 198, 124), 0.35 * sstep(-0.25, 0.20, p) * sstep(-0.2, 0.4, q))
    elif kind == "crimson":
        web = noise(U * 1.15, V * 1.45, 4.0)
        line = 1.0 - sstep(0.05, 0.20, np.abs(web))
        c = mix((20, 18, 22), (150, 16, 24), 0.85 * line)
        c = blend(c, (216, 40, 46), 0.75 * line * sstep(0.45, 0.95, np.abs(np.sin(U * 1.4 + V * 1.1))))
        c = blend(c, (60, 10, 14), 0.35 * sstep(0.2, 0.8, noise(U * 1.2, V * 1.4, 7.0)))
    elif kind == "damascus":
        w = np.sin(U * 1.35 + 2.8 * np.sin(V * 1.1 + U * 0.35) + n * 0.9)
        c = mix((104, 110, 120), (198, 203, 212), 0.5 + 0.5 * w)
        c = blend(c, (146, 152, 164), 0.22 * (0.5 + 0.5 * np.sin(U * 4.6 + V * 1.6)))
        c = blend(c, (62, 66, 74), 0.28 * sstep(0.15, 0.85, noise(U * 0.9, V * 1.3, 11.0)))
    elif kind == "doppler":
        p = noise(U * 0.7, V * 1.2, 2.0) + 0.30 * np.sin(U * 0.9 - V * 1.4)
        c = mix((20, 32, 92), (62, 108, 226), sstep(-0.45, 0.42, p))
        c = blend(c, (22, 24, 62), 0.55 * sstep(0.42, 0.85, noise(U * 2.4, V * 3.0, 9.0)))
        c = blend(c, (156, 196, 255), 0.28 * sstep(0.25, 0.75, p))
    elif kind == "fade":
        stops = np.array([(208, 32, 42), (190, 20, 62), (142, 20, 96),
                          (88, 26, 132), (54, 44, 172), (40, 86, 198)], dtype=np.float64)
        x = np.clip(t * (len(stops) - 1), 0, len(stops) - 1.001)
        i0 = x.astype(int)
        f = (x - i0)[..., None]
        c = stops[i0] * (1 - f) + stops[i0 + 1] * f
        c = blend(c, (255, 255, 255), 0.06 * sstep(0.6, 1.0, t))
    elif kind == "slaughter":
        p = noise(U * 0.55, V * 0.95, 3.0)
        c = mix((176, 178, 186), (92, 96, 104), sstep(-0.35, 0.45, p))
        red = sstep(0.05, 0.55, noise(U * 0.85, V * 1.15, 11.0))
        c = blend(c, (168, 34, 38), 0.85 * red)
        c = blend(c, (226, 218, 210), 0.35 * sstep(0.35, 0.85, noise(U * 1.5 + 1.0, V * 1.9, 5.0)))
    elif kind == "tiger":
        band = np.sin(U * 2.5 + 0.8 * np.sin(V * 0.9) + n * 0.7)
        k = sstep(-0.32, 0.32, band)
        c = mix((28, 22, 14), (226, 172, 48), k)
        c = blend(c, (252, 216, 122), 0.30 * sstep(0.35, 0.9, k))
    elif kind == "camo":
        p = noise(U * 0.50, V * 0.85, 6.0)
        c = mix((26, 28, 32), (176, 180, 186), sstep(0.05, 0.55, p))
        c = blend(c, (74, 78, 86), 0.70 * sstep(-0.50, 0.10, noise(U * 1.15, V * 1.55, 13.0)))
    else:
        c = np.zeros(U.shape + (3,), dtype=np.float64) + np.array((158, 164, 174), dtype=np.float64)
    return np.clip(c, 0, 255)


def grip_colour_v(kind, U, V, t):
    """Grip skin, smooth across the handle."""
    if kind == "ribbed":
        band = 0.5 + 0.5 * np.sin(t * np.pi * 2 * 26)
        g = 34 + 26 * band
        return np.stack([g, g, g + 2], axis=-1)
    if kind == "groove":
        band = 0.5 + 0.5 * np.sin(t * np.pi * 2 * 14 + V * 0.6)
        g = 36 + 20 * band
        return np.stack([g, g + 1, g + 3], axis=-1)
    if kind == "cord":
        band = 0.5 + 0.5 * np.sin((t * 18 + V * 1.1) * np.pi)
        g = 46 + 24 * band
        return np.stack([g, g, g + 4], axis=-1)
    if kind == "wood":
        w = 0.5 + 0.5 * np.sin(V * 6.0 + t * 4.0 + noise(U * 6.0, V * 3.0, 2.0))
        return np.stack([118 + 46 * w, 28 + 14 * w, 30 + 12 * w], axis=-1)
    if kind == "tiger":
        s = 0.5 + 0.5 * np.sin((t * 30 - V * 1.4) * np.pi)
        return np.stack([36 + 26 * s, 66 + 46 * s, 140 + 62 * s], axis=-1)
    if kind == "orange":
        n = 0.5 + 0.5 * np.sin(U * 22 + V * 6 + noise(U * 9.0, V * 5.0, 4.0))
        return np.stack([180 + 32 * n, 74 + 20 * n, 24 + 12 * n], axis=-1)
    if kind == "camo":
        p = noise(U * 1.4, V * 2.2, 8.0)
        return mix((20, 22, 25), (44, 46, 50), 0.5 + 0.5 * p)
    return np.zeros(U.shape + (3,)) + np.array((30, 31, 34))


def brass_shade(colour, frac):
    """A round brass bar: darker at the rims, a specular band above the centre."""
    f = np.clip(frac, -1, 1)
    round_ = 0.55 + 0.55 * np.sqrt(np.clip(1.0 - f * f, 0, 1))
    spec = 0.55 * np.exp(-((f - 0.32) / 0.20) ** 2)
    c = np.asarray(colour, dtype=np.float64)[None, None, :] * round_[..., None]
    return np.clip(c + 210.0 * spec[..., None], 0, 255)


def knife_mask(k):
    """The outline at 4x, filtered down: a smooth alpha edge at any zoom."""
    w, cellh = LEVELS[0]
    W, H = w * ART_SCALE, cellh * ART_SCALE
    img = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(img)

    def P(uu, vv):
        return (uu / LEN * W, ((vv + HALF) / (2 * HALF)) * H)

    n = 900
    b0, tipu, spine, edge = k.blade
    us = [b0 + (tipu - b0) * i / n for i in range(n + 1)]
    top = [P(x, spine(x)) for x in us]
    bot = [P(x, edge(x)) for x in us]
    d.polygon(top + bot[::-1], fill=255)

    h0, h1, hw, style = k.handle
    hu = [h0 + (h1 - h0) * i / n for i in range(n + 1)]
    ht = [P(x, k.grip_half(x, hw, style)) for x in hu]
    hb = [P(x, -k.grip_half(x, hw, style)) for x in hu]
    d.polygon(ht + hb[::-1], fill=255)

    if 0 <= 1.6:
        d.rectangle([P(0.15, -1.30), P(1.6, 1.30)], fill=255)
    if k.ferrule:
        d.rectangle([P(k.ferrule[0], -1.34), P(k.ferrule[1], 1.34)], fill=255)
    if k.guard:
        d.rectangle([P(12.55, -1.62), P(13.35, 1.62)], fill=255)

    if k.ring is not None:
        cu, ro, ri = k.ring
        d.ellipse([P(cu - ro, -ro), P(cu + ro, ro)], fill=255)
        d.ellipse([P(cu - ri, -ri), P(cu + ri, ri)], fill=0)

    if style == "skeleton":
        for cu in (3.4, 6.6, 9.8):
            d.ellipse([P(cu - 0.55, -0.62), P(cu + 0.55, 0.62)], fill=0)

    for t0, t1, depth, count in k.teeth:
        steps = 400
        for i in range(steps):
            uu = t0 + (t1 - t0) * i / steps
            ph = ((uu - t0) / (t1 - t0) * count) % 1.0
            if ph < 0.45:
                fs = spine(uu)
                du = (t1 - t0) / steps
                d.polygon([P(uu, fs - depth), P(uu + du, fs - depth),
                           P(uu + du, fs + 0.4), P(uu, fs + 0.4)], fill=0)
    return img.resize((w, cellh), Image.LANCZOS)


def render_face(k):
    from PIL import ImageFilter

    w, cellh = LEVELS[0]
    U1 = (np.arange(w) + 0.5) / w * LEN
    V1 = -HALF + (np.arange(cellh) + 0.5) / cellh * 2 * HALF
    UU = np.broadcast_to(U1[None, :], (cellh, w)).astype(np.float64).copy()
    cen = np.array([k.centre_curve(float(x)) for x in U1])[None, :]
    V = np.broadcast_to(V1[:, None], (cellh, w)).astype(np.float64) + cen

    b0, tipu, spine, edge = k.blade
    fs = np.array([spine(float(x)) for x in U1])[None, :]
    fe = np.array([edge(float(x)) for x in U1])[None, :]
    height = np.maximum(1e-4, fs - fe)
    frac = (V - fe) / height                       # 0 at the edge, 1 at the spine

    # ---- blade
    col = skin_colour(k.skin, UU, V, b0, tipu)
    shade = 0.76 + 0.26 * sstep(0.00, 0.22, frac) + 0.16 * sstep(0.62, 0.26, frac)
    tipfade = np.clip(height / 1.8, 0.0, 1.0)
    spec = (0.30 * np.exp(-((frac - 0.44) / 0.075) ** 2)
            + 0.12 * np.exp(-((frac - 0.29) / 0.045) ** 2)) * tipfade
    hot = 1.0 + 0.16 * sstep(0.12, 0.0, frac)
    col = col * (shade * hot)[..., None] + 235.0 * spec[..., None]
    # the plunge cut: the blade darkens where it leaves the guard
    col = col * (1.0 - 0.18 * np.exp(-((UU - b0 - 0.45) / 0.45) ** 2))[..., None]
    if k.slot and k.slot[2] > 0:
        s0, s1, sh = k.slot
        inr = (UU >= s0) & (UU <= s1)
        d = np.abs(V + 0.85) / max(1e-4, sh)
        groove = inr & (d <= 1.0)
        col = np.where(groove[..., None], col * (1.0 - 0.40 * (1.0 - d * d))[..., None], col)
        lip = inr & (d > 0.84) & (d <= 1.14)
        col = np.where(lip[..., None], np.minimum(255.0, col * 1.09), col)
    col = np.clip(col, 0, 255)

    # ---- handle
    h0, h1, hw, style = k.handle
    t = np.clip((UU - h0) / max(1e-6, h1 - h0), 0.0, 1.0)
    g = hw * (1.0 - 0.16 * sstep(0.75, 1.0, t))
    if style in ("groove", "camo", "wood", "tiger"):
        g = g + 0.10 * np.abs(np.sin(np.pi * 3.0 * t))
    if style == "curve":
        g = g * (1.0 + 0.10 * np.sin(np.pi * t))
    gc = np.maximum(0.2, g)
    across = np.clip(np.abs(V - cen) / gc, 0, 1)
    round_ = 0.72 + 0.40 * np.sqrt(np.clip(1.0 - across * across, 0, 1))
    hspec = 0.45 * np.exp(-((across - 0.42) / 0.22) ** 2)
    hc = grip_colour_v(style, UU, V, t) * round_[..., None] + 190.0 * hspec[..., None]
    if style == "wood":
        for ru in (3.0, 7.0, 11.0):
            for rv in (-0.55, 0.55):
                m = ((UU - ru) ** 2 + (V - rv) ** 2) < 0.10
                hc = np.where(m[..., None], np.array((206, 172, 96), dtype=np.float64), hc)
    if style in ("ribbed", "groove"):
        for ru in (3.2, 8.4):
            m = (np.abs(UU - ru) < 0.11) & (np.abs(V) < 0.95)
            hc = np.where(m[..., None], np.array((198, 160, 86), dtype=np.float64), hc)
    hmask = (UU >= h0) & (UU <= h1) & (np.abs(V - cen) <= gc)
    col = np.where(hmask[..., None], hc, col)

    # ---- brass: pommel, ferrule, guard
    def bar(u0, u1, halfw, colour):
        nonlocal col
        m = (UU >= u0) & (UU <= u1) & (np.abs(V - cen) <= halfw)
        b = brass_shade(colour, (V - cen) / max(1e-4, halfw))
        # rounded ends
        e0 = np.clip((UU - u0) / 0.20, 0, 1)
        e1 = np.clip((u1 - UU) / 0.20, 0, 1)
        edge = np.minimum(e0, e1)
        b = b * (0.72 + 0.28 * np.sqrt(np.clip(edge, 0, 1)))[..., None]
        col = np.where(m[..., None], b, col)

    bar(0.15, 1.6, 1.30, (198, 160, 88))
    if k.ferrule:
        bar(k.ferrule[0], k.ferrule[1], 1.34, (214, 126, 56) if style != "wood" else (200, 162, 90))
    if k.guard:
        bar(12.55, 13.35, 1.62, (200, 162, 90))
        # the two rings of a guard
        for ru in (12.62, 13.28):
            m = (np.abs(UU - ru) < 0.07) & (np.abs(V - cen) <= 1.70)
            col = np.where(m[..., None], np.array((228, 196, 128), dtype=np.float64), col)
    if k.ring is not None:
        cu, ro, ri = k.ring
        d = np.sqrt((UU - cu) ** 2 + V ** 2)
        m = (d <= ro) & (d >= ri)
        ang = np.arctan2(V, UU - cu)
        b = brass_shade((200, 162, 90), np.cos(ang) * 0.9)
        col = np.where(m[..., None], b, col)

    # ---- alpha and the silhouette line
    mask = knife_mask(k)
    a = np.asarray(mask, dtype=np.float64) / 255.0
    inner = np.asarray(mask.filter(ImageFilter.MinFilter(7)), dtype=np.float64) / 255.0
    border = np.clip(a - inner, 0, 1)
    col = col * (1.0 - 0.45 * border)[..., None]
    # a hint of grain so the flats are not perfectly clean
    grain = 3.0 * np.sin(UU * 61.0 + V * 17.0) + 2.0 * np.sin(UU * 133.0 - V * 41.0)
    col = np.clip(col + grain[..., None], 0, 255)

    out = np.zeros((cellh, w, 4), dtype=np.uint8)
    out[..., :3] = col.astype(np.uint8)
    out[..., 3] = np.clip(a * 255.0, 0, 255).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def offset_cell(face, dx, dy, tint):
    """A rim cell: the same art, brightened or dimmed by the inset shoulder."""
    out = face.copy()
    px = face.load()
    opx = out.load()
    w, h = face.size
    for y in range(h):
        for x in range(w):
            a = px[x, y][3]
            if not a:
                continue
            sx, sy = min(w - 1, max(0, x + dx)), min(h - 1, max(0, y + dy))
            o = px[sx, sy]
            if not o[3]:
                opx[x, y] = (0, 0, 0, 0)
            else:
                opx[x, y] = (int(o[0] * tint), int(o[1] * tint), int(o[2] * tint), 255)
    return out


def flat_cell(face, colour):
    out = Image.new("RGBA", face.size, (0, 0, 0, 0))
    opx = out.load()
    px = face.load()
    w, h = face.size
    for y in range(h):
        for x in range(w):
            if px[x, y][3]:
                opx[x, y] = (colour[0], colour[1], colour[2], 255)
    return out


def light_cell(face, mask_fn, strength):
    out = Image.new("RGBA", face.size, (0, 0, 0, 0))
    px = face.load()
    opx = out.load()
    w, h = face.size
    for y in range(h):
        for x in range(w):
            if not px[x, y][3]:
                continue
            a = mask_fn(x / w)
            if a <= 0:
                continue
            opx[x, y] = (255, 255, 255, int(255 * a * strength))
    return out


def profile_cell(face):
    out = Image.new("RGBA", face.size, (0, 0, 0, 0))
    px = face.load()
    opx = out.load()
    w, h = face.size
    for y in range(h):
        for x in range(w):
            if not px[x, y][3]:
                continue
            t = y / max(1, h - 1)
            g = int(90 + 96 * (1.0 - abs(t - 0.5) * 2))
            opx[x, y] = (g, g + 2, g + 6, 255)
    return out


def build_sheet(k):
    face = render_face(k)
    gloss = light_cell(face, lambda t: 0.35 + 0.65 * max(0.0, math.sin(t * math.pi * 3.0)), 0.35)
    edge = light_cell(face, lambda t: max(0.0, min(1.0, (t - 0.55) / 0.45)), 0.5)
    cells = [face, offset_cell(face, 1, 1, 0.88), offset_cell(face, 2, 2, 0.74),
             flat_cell(face, (58, 58, 64)), gloss, edge, profile_cell(face)]
    w, cellh = LEVELS[0]
    sheet = Image.new("RGBA", (w, cellh * CELLS), (0, 0, 0, 0))
    for i, c in enumerate(cells):
        sheet.alpha_composite(c, (0, i * cellh))
    return sheet


def write_levels(k, sheet):
    for wi, chi in LEVELS:
        out = Image.new("RGBA", (wi, chi * CELLS), (0, 0, 0, 0))
        for i in range(CELLS):
            band = sheet.crop((0, i * LEVELS[0][1], LEVELS[0][0], (i + 1) * LEVELS[0][1]))
            if band.size != (wi, chi):
                band = band.resize((wi, chi), Image.LANCZOS)
            out.alpha_composite(band, (0, i * chi))
        level = LEVELS.index((wi, chi))
        out.save(os.path.join(TEX, "%s_%d.png" % (k.id, level)))


def centre_samples(k, n=130):
    out = []
    for i in range(n):
        u = i / (n - 1.0) * LEN
        out.append(round(k.centre_curve(u) * 0.25, 3))
    return out


def main():
    os.makedirs(TEX, exist_ok=True)
    os.makedirs(JSON, exist_ok=True)
    knives = []

    sp, ed = blade_profile(13.35, 28.0, 2.55, clip=0.55, tipv=0.0)
    knives.append(Knife("tac_case", "Tactical · Case hardened", "#ffd166",
                        [("pommel", 0.2, 1.6, 1.05, 0.3), ("handle", 1.6, 12.7, 1.08, 0.62),
                         ("guard", 12.7, 13.7, 1.15, 0.22), ("blade", 13.7, 28.2, 0.26, 0.0)],
                        (1.5, 12.7, 1.28, "ribbed"), (13.35, 28.0, sp, ed), [28.0, 0.0],
                        teeth=[(13.6, 17.4, 0.55, 7)], slot=(14.6, 21.5, 0.34)))
    knives[-1].skin = "case"

    sp, ed = blade_profile(13.2, 28.0, 2.45, clip=0.5, tipv=0.0)
    knives.append(Knife("tac_crimson", "Tactical · Crimson web", "#ff2a3c",
                        [("ring", 0.0, 1.45, 0.42, 0.28), ("handle", 1.45, 13.3, 1.05, 0.55),
                         ("blade", 13.3, 28.2, 0.27, 0.0)],
                        (1.6, 13.1, 1.22, "skeleton"), (13.2, 28.0, sp, ed), [28.0, 0.0],
                        teeth=[(13.5, 16.0, 0.42, 5)], ring=(0.62, 0.92, 0.58),
                        fuller=False, slot=(14.4, 20.4, 0.30)))
    knives[-1].skin = "crimson"

    sp, ed = blade_profile(13.3, 28.0, 2.50, clip=0.45, tipv=0.0)
    knives.append(Knife("tac_damascus", "Tactical · Damascus", "#dfe6ef",
                        [("pommel", 0.2, 1.6, 1.05, 0.3), ("handle", 1.6, 12.7, 1.08, 0.62),
                         ("guard", 12.7, 13.7, 1.15, 0.22), ("blade", 13.7, 28.2, 0.26, 0.0)],
                        (1.5, 12.7, 1.26, "cord"), (13.3, 28.0, sp, ed), [28.0, 0.0],
                        teeth=[(13.6, 18.0, 0.52, 8)], ferrule=(12.05, 12.5),
                        slot=(14.8, 21.6, 0.32)))
    knives[-1].skin = "damascus"

    sp, ed = blade_profile(13.2, 28.0, 2.40, clip=0.2, tipv=0.0)
    knives.append(Knife("tac_doppler", "Tactical · Sapphire", "#4a7dff",
                        [("handle", 0.2, 12.7, 0.95, 0.5), ("guard", 12.7, 13.25, 1.05, 0.15),
                         ("blade", 13.25, 28.2, 0.26, 0.0)],
                        (1.2, 12.7, 1.24, "plain"), (13.2, 28.0, sp, ed), [28.0, 0.0],
                        teeth=[(13.4, 16.6, 0.34, 6)], guard=True, fuller=False,
                        slot=(14.2, 20.0, 0.26)))
    knives[-1].skin = "doppler"

    sp, ed = blade_profile(13.25, 28.0, 2.42, clip=0.35, tipv=0.0)
    knives.append(Knife("tac_fade", "Tactical · Fade", "#b04cff",
                        [("handle", 0.3, 12.7, 1.00, 0.5), ("guard", 12.7, 13.25, 1.00, 0.14),
                         ("blade", 13.25, 28.2, 0.26, 0.0)],
                        (1.4, 12.7, 1.22, "wood"), (13.25, 28.0, sp, ed), [28.0, 0.0],
                        teeth=[(13.5, 16.8, 0.36, 6)], slot=(14.3, 20.6, 0.28)))
    knives[-1].skin = "fade"
    knives[-1].ring = (0.55, 0.86, 0.52)

    sp, ed = blade_profile(13.25, 28.0, 2.48, clip=0.4, tipv=0.0)
    knives.append(Knife("tac_slaughter", "Tactical · Slaughter", "#ff4a3a",
                        [("handle", 0.3, 12.7, 1.10, 0.62), ("guard", 12.7, 13.25, 1.00, 0.12),
                         ("blade", 13.25, 28.2, 0.26, 0.0)],
                        (1.2, 12.7, 1.30, "orange"), (13.25, 28.0, sp, ed), [28.0, 0.0],
                        teeth=[(13.5, 17.2, 0.44, 7)], slot=(14.2, 20.8, 0.30)))
    knives[-1].skin = "slaughter"
    knives[-1].ring = (0.55, 0.95, 0.60)

    sp, ed = blade_profile(13.32, 28.0, 2.52, clip=0.5, tipv=0.0)
    knives.append(Knife("tac_tiger", "Tactical · Tiger tooth", "#ffb030",
                        [("ring", 0.1, 2.7, 0.4, 0.3), ("handle", 2.7, 12.7, 0.98, 0.55),
                         ("guard", 12.7, 13.32, 1.10, 0.16), ("blade", 13.32, 28.2, 0.26, 0.0)],
                        (2.9, 12.7, 1.20, "tiger"), (13.32, 28.0, sp, ed), [28.0, 0.0],
                        teeth=[(13.5, 20.5, 0.85, 10)], ring=(0.72, 1.10, 0.72),
                        fuller=False, slot=(14.0, 19.4, 0.0)))
    knives[-1].skin = "tiger"

    sp, ed = blade_profile(12.9, 28.0, 2.42, clip=0.3, tipv=0.5)
    knives.append(Knife("tac_urban", "Tactical · Urban masked", "#c8d0dc",
                        [("ring", 0.0, 2.6, 0.38, 0.3), ("handle", 2.6, 12.9, 1.00, 0.5),
                         ("blade", 12.9, 28.2, 0.26, 0.0)],
                        (2.8, 12.8, 1.16, "camo"), (12.9, 28.0, sp, ed), [28.0, 0.5],
                        teeth=[(25.6, 27.4, 0.30, 6)], ring=(0.66, 1.00, 0.66),
                        fuller=False, slot=(14.0, 19.6, 0.0)))
    knives[-1].skin = "camo"

    data = {"len": LEN, "half": HALF, "cells": CELLS, "zmax": ZMAX,
            "levels": [[w, h] for w, h in LEVELS], "knives": []}
    for k in knives:
        sheet = build_sheet(k)
        write_levels(k, sheet)
        data["knives"].append({
            "id": k.id, "name": k.name, "glow": k.glow,
            "tip": k.tip,
            "centre": centre_samples(k),
            "parts": [{"name": n, "u0": a, "u1": b, "t": t, "r": r} for n, a, b, t, r in k.parts],
        })
        print("sheet", k.id)
    with open(os.path.join(JSON, "tactical.json"), "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=1)

    # contact sheet: the FACE cell of every knife, on dark blue
    cellw, cellh = 470, 116
    prev = Image.new("RGBA", (cellw * 2, cellh * 4), (12, 20, 38, 255))
    for i, k in enumerate(knives):
        face = render_face(k)
        # the cell is a strip; stretch it back to the knife's real proportions for the preview
        vh = int((cellw - 24) * (2 * HALF / LEN))
        face = face.resize((cellw - 24, vh), Image.LANCZOS)
        prev.alpha_composite(face, ((i % 2) * cellw + 12, (i // 2) * cellh + 22))
        ImageDraw.Draw(prev).text(((i % 2) * cellw + 10, (i // 2) * cellh + 4), k.name,
                                  fill=(206, 216, 234))
    os.makedirs(os.path.join(ROOT, "artifacts"), exist_ok=True)
    prev.convert("RGB").save(os.path.join(ROOT, "artifacts", "hd_knives.png"))
    print("preview -> artifacts/hd_knives.png")


if __name__ == "__main__":
    main()
