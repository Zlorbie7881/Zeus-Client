#!/usr/bin/env python3
"""Build the eight HD tactical knives (sheets + layout) used by KnifeHD.

Run:  python3 tools/knives_hd/make_tactical.py

Per knife this writes one sheet in two resolutions:
  textures/knives/hd/<id>_0.png   1024 x 1792   (level 0)
  textures/knives/hd/<id>_1.png    512 x  896   (level 1)
  textures/knives/hd/<id>_2.png    256 x  448   (level 2, the knife in hand at 1080p)
and knives/hd/tactical.json with the parts, their thickness and roundness.

The sheet is seven cells stacked vertically, each the whole knife seen from the side:
  0  FACE    lit colour of the top surface (rounded parts are inset by their radius)
  1  RIM50   the rounded shoulder half way down (inset by half the radius)
  2  RIM13   the shoulder near the side wall (inset by 13 % of the radius)
  3  SIDE    full outline in the colour of the side wall, used for the core slices
  4  GLOSS   specular colour of the flats (alpha 2, drawn as light)
  5  EDGE    specular colour of the bevels (alpha 2, drawn as light)
  6  PROFILE the knife seen from the spine: thickness per column, for edge-on views
"""

import json
import math
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from hd_lib import *  # noqa

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/zeus")
OUT_TEX = os.path.join(ASSETS, "textures/knives/hd")
OUT_JSON = os.path.join(ASSETS, "knives/hd/tactical.json")
FONT = os.path.join(ASSETS, "fonts/inter-extrabold.otf")

LIGHT = np.array([-0.30, -0.60, 0.74], np.float32)
LIGHT /= np.linalg.norm(LIGHT)

BRASS = [(0.0, hexc("2a1806")), (0.35, hexc("7a5218")), (0.62, hexc("b98a3e")),
         (0.84, hexc("e2bd6e")), (1.0, hexc("fff0c0"))]


# =================================================================== knife canvas
class Knife:
    def __init__(self, kid, name):
        self.id = kid
        self.name = name
        self.alb = np.zeros((GH, GW, 3), np.float32)
        self.mask = np.zeros((GH, GW), np.float32)
        self.height = np.zeros((GH, GW), np.float32)
        self.metal = np.zeros((GH, GW), np.float32)      # 0 dielectric .. 1 metal
        self.gloss = np.zeros((GH, GW), np.float32)      # specular strength of the flats
        self.bevel = np.zeros((GH, GW), np.float32)      # where the edge glint lives
        self.side = np.zeros((GH, GW, 3), np.float32)    # side wall colour
        self.round = np.zeros((GH, GW), np.float32)      # rounding radius per pixel (units)
        self.parts = []
        self.tip = (27.9, 0.0)
        self.grip = 6.0
        self.glow = "#ffffff"

    def paint(self, mask, alb, height=None, metal=0.0, gloss=0.0, bevel=None, side=None, rnd=0.0):
        m = np.clip(mask, 0, 1)
        k = m[..., None]
        self.alb = self.alb * (1 - k) + alb * k
        if height is not None:
            self.height = self.height * (1 - m) + height * m
        else:
            self.height = self.height * (1 - m)
        self.metal = self.metal * (1 - m) + np.asarray(metal, np.float32) * m
        self.gloss = self.gloss * (1 - m) + np.asarray(gloss, np.float32) * m
        bv = np.zeros_like(m) if bevel is None else bevel
        self.bevel = self.bevel * (1 - m) + bv * m
        sc = alb * 0.55 if side is None else side
        self.side = self.side * (1 - k) + sc * k
        self.round = self.round * (1 - m) + rnd * m
        self.mask = np.maximum(self.mask, m)

    def cut(self, mask):
        """Remove material (holes)."""
        keep = 1 - np.clip(mask, 0, 1)
        self.mask *= keep

    def part(self, name, u0, u1, thick, rnd):
        self.parts.append({"name": name, "u0": u0, "u1": u1, "t": thick, "r": rnd})


# =================================================================== blade
def profile_arrays(spine_fn, edge_fn, u0, u1, n=900):
    s = np.linspace(0, 1, n)
    us = u0 + s * (u1 - u0)
    return s, us, np.array([spine_fn(x) for x in s]), np.array([edge_fn(x) for x in s])


def blade(kn, u0, u1, spine_v, edge_v, tip_v, clip=0.70, belly=0.58, clip_pow=1.35,
          teeth=None, serr=None, fuller=None, choil=True, swedge=True, etch=None,
          bevel_w=1.05, finish=None, notch=None, tang=0.6):
    L = u1 - u0

    def spine(s):
        v = spine_v
        if s > clip:
            k = (s - clip) / (1 - clip)
            v = spine_v + (tip_v - spine_v) * (k ** clip_pow)
        if notch and notch[0] < s < notch[1]:
            k = (s - notch[0]) / (notch[1] - notch[0])
            v += notch[2] * math.sin(math.pi * k) ** 0.6
        return v

    def edge(s):
        v = edge_v
        if s > belly:
            k = (s - belly) / (1 - belly)
            v = tip_v + (edge_v - tip_v) * math.sqrt(max(0.0, 1 - k ** 2.15))
        if s < 0.02:
            v = edge_v - 0.25 * (1 - s / 0.02)
        return v

    s, us, sv, ev = profile_arrays(spine, edge, u0, u1)
    # tang continues under the guard so the joint never shows a gap
    pts = ([(u0 - tang, sv[0] + 0.2)] if tang else []) + list(zip(us, sv)) + list(zip(us[::-1], ev[::-1])) + ([(u0 - tang, ev[0] - 0.2)] if tang else [])
    mask = poly_mask(pts)

    # spine and edge lines sampled per pixel column
    col_u = U[0]
    sp = np.interp(col_u, us, sv)[None, :]
    ed = np.interp(col_u, us, ev)[None, :]
    sfrac = np.clip((U - u0) / L, 0, 1)
    width = np.maximum(ed - sp, 1e-3)

    if teeth:
        t0, t1, depth, pitch = teeth
        a, b = u0 + t0 * L, u0 + t1 * L
        ph = ((U - a) / pitch) % 1.0
        inside = (U > a) & (U < b)
        prof = np.where(ph < 0.72, ph / 0.72, (1 - ph) / 0.28)
        cut = inside & (V < sp + depth * (1 - prof))
        mask = mask * (1 - cut.astype(np.float32))
    if serr:
        s0, s1, depth, pitch = serr
        a, b = u0 + s0 * L, u0 + s1 * L
        ph = ((U - a) / pitch) % 1.0
        inside = (U > a) & (U < b)
        scallop = depth * np.sqrt(np.clip(1 - ((ph - 0.5) / 0.5) ** 2, 0, 1))
        cut = inside & (V > ed - scallop)
        mask = mask * (1 - cut.astype(np.float32))
    if choil:
        ce = float(np.interp(u0 + 0.35, us, ev))
        _, c = capsule_mask(u0 + 0.35, ce + 0.15, u0 + 0.35, ce + 0.15, 0.32)
        mask = mask * (1 - c)

    d_edge = ed - V
    d_spine = V - sp
    bw = np.minimum(bevel_w, 0.62 * width)
    bev = np.clip(1 - d_edge / bw, 0, 1) * (d_edge > -0.02)
    h = -0.42 * bev ** 1.15
    sw = np.zeros_like(h)
    if swedge:
        cl = smoothstep(u0 + (clip - 0.04) * L, u0 + (clip + 0.04) * L, U)
        sw = np.clip(1 - d_spine / 0.42, 0, 1) * cl
        h += -0.22 * sw
    # spine shoulders: a narrow polished chamfer
    sh = np.clip(1 - d_spine / 0.12, 0, 1) * (1 - sw)
    h += -0.10 * sh
    # saw teeth get their own bevels so they flash
    tooth_bev = np.zeros_like(h)
    if teeth:
        t0, t1, depth, pitch = teeth
        a, b = u0 + t0 * L, u0 + t1 * L
        ph = ((U - a) / pitch) % 1.0
        zone = ((U > a) & (U < b)).astype(np.float32) * np.clip(1 - d_spine / (depth + 0.30), 0, 1)
        tooth_bev = zone * np.where(ph < 0.72, 1.0, 0.4)
        h += -0.16 * zone * (0.6 + 0.4 * np.sin(ph * math.pi * 2))
    serr_bev = np.zeros_like(h)
    if serr:
        s0, s1, depth, pitch = serr
        a, b = u0 + s0 * L, u0 + s1 * L
        ph = ((U - a) / pitch) % 1.0
        zone = ((U > a) & (U < b)).astype(np.float32) * np.clip(1 - d_edge / (depth + 0.35), 0, 1)
        serr_bev = zone
        h += -0.18 * zone * np.cos((ph - 0.5) * math.pi)
    full = np.zeros_like(h)
    if fuller:
        f0, f1, fv, fw = fuller
        d, _ = capsule_mask(u0 + f0 * L, fv, u0 + f1 * L, fv, fw)
        full = np.clip(1 - d / fw, 0, 1)
        h += -0.38 * np.sin(full * math.pi / 2) ** 0.8
    etchm = np.zeros_like(h)
    if etch:
        text, es, ev_, size = etch
        etchm = text_mask(text, u0 + es * L, ev_, size, FONT)
        h += -0.03 * etchm

    # the tang never paints over a guard or bolster that is already there
    mask = mask * (1 - kn.mask * (U < u0))
    alb, metal, gloss = finish(kn, mask, sfrac, d_edge, d_spine, bev)
    # polished bevel is brighter and less patterned, the very edge is a bright line
    alb = alb * (1 + 0.20 * bev[..., None])
    edge_line = np.clip(1 - d_edge / 0.07, 0, 1) * (d_edge > -0.02)
    alb = lerp(alb, np.minimum(1, alb * 1.6 + 0.25), edge_line * 0.8)
    alb = lerp(alb, alb * 0.35, (full > 0.05) * 0.45 * np.sin(full * math.pi / 2))
    alb = lerp(alb, alb * 0.55, etchm * 0.75)
    # grind line: a fine dark seam where the flat meets the bevel
    seam = np.exp(-((d_edge - bw) / 0.035) ** 2) * (bw > 0.2)
    alb = alb * (1 - 0.25 * seam[..., None])
    # use and wear: hairline scratches that catch the light, a little grime at the guard and spine
    scr = scratches(kn.id, u0, u1, min(spine_v, edge_v), max(spine_v, edge_v)) * mask
    alb = lerp(alb, np.minimum(1, alb * 1.35 + 0.07), scr[..., None] * 0.45)
    grime = np.clip(1 - (U - u0) / 1.6, 0, 1) * 0.5 + np.clip(1 - d_spine / 0.10, 0, 1) * 0.25
    grime *= 0.6 + 0.4 * fbm(0.5, len(kn.id) * 7 + 3, 3)
    alb = alb * (1 - 0.18 * np.clip(grime, 0, 1)[..., None] * mask[..., None])
    bevel = np.clip(bev * 0.85 + sw * 0.6 + tooth_bev * 0.7 + serr_bev * 0.8 + edge_line, 0, 1)
    side = np.ones_like(alb) * hexc("8e949c")[None, None, :] * 0.85
    kn.paint(mask, alb, height=h, metal=metal, gloss=gloss, bevel=bevel, side=side)
    kn.tip = (u1, tip_v)
    return sp, ed


def scratches(seed_text, u0, u1, v0, v1, count=170):
    """Thin straight hairlines, mostly along the blade, rasterised only inside their own box."""
    seed = sum(ord(c) * (i + 1) for i, c in enumerate(seed_text))
    rng = np.random.default_rng(seed)
    out = np.zeros((GH, GW), np.float32)
    for _ in range(count):
        cu, cv = rng.uniform(u0 + 0.4, u1 - 0.8), rng.uniform(v0, v1)
        ln = rng.uniform(0.25, 1.6)
        ang = rng.normal(0.0, 0.22) + (math.pi / 2 if rng.random() < 0.12 else 0.0)
        du, dv = math.cos(ang) * ln / 2, math.sin(ang) * ln / 2
        a_u, a_v, b_u, b_v = cu - du, cv - dv, cu + du, cv + dv
        x0, y0 = to_px(min(a_u, b_u) - 0.05, min(a_v, b_v) - 0.05)
        x1, y1 = to_px(max(a_u, b_u) + 0.05, max(a_v, b_v) + 0.05)
        xa, xb = max(0, int(min(x0, x1))), min(GW, int(max(x0, x1)) + 1)
        ya, yb = max(0, int(min(y0, y1))), min(GH, int(max(y0, y1)) + 1)
        if xb <= xa or yb <= ya:
            continue
        uu, vv = U[ya:yb, xa:xb], V[ya:yb, xa:xb]
        pu, pv = uu - a_u, vv - a_v
        eu, ev = b_u - a_u, b_v - a_v
        t = np.clip((pu * eu + pv * ev) / (eu * eu + ev * ev + 1e-9), 0, 1)
        d = np.hypot(pu - t * eu, pv - t * ev)
        w = rng.uniform(0.008, 0.018)
        fade = np.clip(np.sin(t * math.pi), 0, 1) ** 0.5
        out[ya:yb, xa:xb] = np.maximum(out[ya:yb, xa:xb], np.clip(1 - d / w, 0, 1) * fade * rng.uniform(0.4, 1.0))
    return out


# ------------------------------------------------------------------ finishes
def steel_base(seed, tone=1.0):
    g = 0.62 + 0.10 * (fbm(3.0, seed, 4) - 0.5) + 0.05 * brushed(seed + 3)
    return np.stack([g * 0.97, g * 0.99, g * 1.03], -1) * tone


def fin_damascus(kn, mask, sfrac, d_edge, d_spine, bev):
    """Pattern-welded steel: many thin folded layers, flowing along the blade."""
    def f(u, v):
        return v * 4.2 + 1.1 * np.sin(u * 0.45 + v * 0.8) + 0.7 * np.sin(u * 1.3 - v * 1.9)
    field = warp(f, 2.6, 1.6, 11)
    field += 0.9 * (fbm(0.9, 13, 4) - 0.5)
    layer = 0.5 + 0.5 * np.sin(field * 7.5)
    fine = 0.5 + 0.5 * np.sin(field * 23.0)
    t = 0.30 + 0.45 * smoothstep(0.15, 0.85, layer) + 0.18 * fine
    alb = ramp(t, [(0.0, hexc("2c2f34")), (0.45, hexc("6d727a")), (0.75, hexc("b8bdc4")), (1.0, hexc("e6e9ee"))])
    alb *= (0.95 + 0.07 * brushed(12))[..., None]
    return alb, 1.0, 0.75


def fin_slaughter(kn, mask, sfrac, d_edge, d_spine, bev):
    alb = steel_base(21, 1.12)
    # splatter: many small irregular stains, denser toward the tip, with runs and droplets
    big = fbm(1.0, 23, 5)
    fine = fbm(0.35, 25, 4)
    stain = smoothstep(0.54, 0.60, big * 0.65 + fine * 0.45)
    drops = np.zeros_like(big)
    rng = np.random.default_rng(29)
    for _ in range(70):
        cu, cv = 14.5 + rng.random() * 13, rng.uniform(-1.4, 1.5)
        r = rng.uniform(0.03, 0.14)
        drops = np.maximum(drops, np.clip(1 - np.hypot(U - cu, (V - cv) * 1.3) / r, 0, 1))
    runs = smoothstep(0.62, 0.70, fbm(0.6, 27, 4, aniso=(3.5, 0.25)))
    amount = np.clip(stain + smoothstep(0.15, 0.45, drops) + runs * 0.8, 0, 1)
    amount *= (0.30 + 0.55 * smoothstep(15, 24, U)) * 0.85
    red = ramp(fbm(0.4, 24, 3), [(0, hexc("5c0505")), (0.45, hexc("a8100c")), (1, hexc("e0321c"))])
    alb = lerp(alb, red, amount * 0.95)
    for cu, cv in ((18.5, -0.2), (21.0, 0.35)):
        for sgn in (1, -1):
            d, _ = capsule_mask(cu - 0.5, cv - 0.45 * sgn, cu + 0.5, cv + 0.45 * sgn, 0.04)
            alb = lerp(alb, alb * 0.55, np.clip(1 - d / 0.04, 0, 1) * 0.7)
    gloss = 0.85 - 0.35 * amount
    return alb, 1.0 - 0.5 * amount, gloss


def fin_case(kn, mask, sfrac, d_edge, d_spine, bev):
    n1 = fbm(1.6, 31, 5)
    n2 = fbm(0.9, 37, 4)
    gold = ramp(n2, [(0, hexc("8a6214")), (0.5, hexc("d4a43a")), (1, hexc("f6dc8a"))])
    blue = ramp(n2, [(0, hexc("142a6a")), (0.5, hexc("2f5bc4")), (1, hexc("8fb6ff"))])
    purple = hexc("6a3c9a")
    t = smoothstep(0.45, 0.62, n1)
    alb = lerp(gold, blue, t)
    p = smoothstep(0.58, 0.7, fbm(1.1, 41, 4)) * (1 - t)
    alb = lerp(alb, np.broadcast_to(purple, alb.shape), p * 0.55)
    white = smoothstep(0.72, 0.85, fbm(0.7, 43, 4))
    alb = lerp(alb, np.ones_like(alb) * 0.92, white * 0.45)
    alb *= (0.96 + 0.06 * brushed(44))[..., None]
    return alb, 1.0, 0.85


def fin_fade(kn, mask, sfrac, d_edge, d_spine, bev):
    t = sfrac + 0.10 * (V / 1.6) + 0.04 * (fbm(1.0, 51, 3) - 0.5)
    alb = ramp(t, [(0.00, hexc("d0202e")), (0.28, hexc("cc2448")), (0.50, hexc("a2309c")),
                   (0.70, hexc("6a46d6")), (0.88, hexc("3f6cf0")), (1.0, hexc("5f93ff"))])
    alb *= (0.95 + 0.07 * brushed(52))[..., None]
    return alb, 1.0, 0.9


def carbon_weave(scale, seed):
    """Twill carbon fibre: alternating tows with a soft sheen."""
    a = (U + V) / scale
    b = (U - V) / scale
    ia = np.floor(a)
    ib = np.floor(b)
    over = ((ia + ib) % 2 == 0)
    fa = a - ia
    fb = b - ib
    tow = np.where(over, np.sin(fa * math.pi), np.sin(fb * math.pi))
    sheen = np.where(over, 0.5 + 0.5 * np.cos(fb * math.pi * 2), 0.5 + 0.5 * np.cos(fa * math.pi * 2))
    return tow, sheen, over


def fin_crimson(kn, mask, sfrac, d_edge, d_spine, bev):
    tow, sheen, over = carbon_weave(0.30, 61)
    base = 0.06 + 0.09 * tow * (0.45 + 0.55 * sheen)
    alb = np.stack([base, base * 1.02, base * 1.08], -1)
    cu, cv = 22.0, 0.1
    ang = np.arctan2(V - cv, (U - cu) * 0.5)
    rad = np.hypot((U - cu) * 0.5, V - cv)
    wob = (fbm(0.8, 63, 3) - 0.5)
    spokes = np.abs(np.sin(ang * 6 + wob * 1.2))
    rings = np.abs(np.sin((rad + 0.15 * wob) * 7.0))
    line = np.maximum(smoothstep(0.16, 0.0, spokes), smoothstep(0.16, 0.0, rings))
    d1 = np.abs(np.sin(U * 1.3 + V * 2.0 + wob)) * np.abs(np.sin(U * 1.3 - V * 2.0 - wob))
    line = np.maximum(line, smoothstep(0.08, 0.0, d1))
    red = ramp(fbm(0.6, 65, 3), [(0, hexc("8c0816")), (1, hexc("e2202e"))])
    alb = lerp(alb, red, np.clip(line, 0, 1) * 0.95)
    return alb, 0.6, 0.7


def fin_tiger(kn, mask, sfrac, d_edge, d_spine, bev):
    gold = ramp(fbm(1.2, 71, 4), [(0, hexc("c06c0c")), (0.5, hexc("f0a020")), (1, hexc("ffd058"))])
    def stripes(u, v):
        return np.sin(u * 4.6 + v * 1.9 + 0.9 * np.sin(v * 2.4 + u * 0.5))
    s = warp(stripes, 0.9, 1.2, 73)
    width = 0.42 + 0.30 * fbm(0.7, 75, 3)
    band = smoothstep(width, width + 0.10, s)
    torn = smoothstep(0.30, 0.55, fbm(0.25, 77, 4))
    fade_in = smoothstep(-1.6, 0.4, V)          # stripes reach in from the edge side
    k = np.clip(band * (0.55 + 0.6 * torn) * (0.55 + 0.45 * fade_in), 0, 1)
    alb = lerp(gold, hexc("1c0f04"), k)
    alb *= (0.95 + 0.07 * brushed(78))[..., None]
    return alb, 1.0, 0.85


def fin_doppler(kn, mask, sfrac, d_edge, d_spine, bev):
    n = fbm(1.6, 81, 5)
    def swirl(u, v):
        return np.sin(u * 0.9 + v * 1.7) + 0.6 * np.sin(u * 2.1 - v * 1.1)
    sw = warp(swirl, 3.2, 1.8, 83)
    t = 0.52 + 0.22 * sw + 0.35 * (n - 0.5)
    alb = ramp(t, [(0.0, hexc("02040e")), (0.22, hexc("0a1858")), (0.45, hexc("1636c0")),
                   (0.66, hexc("2f63ff")), (0.82, hexc("5c78ff")), (0.92, hexc("8a62e8")), (1.0, hexc("b8c8ff"))])
    sparkle = (np.random.default_rng(85).random((GH, GW)) > 0.9993).astype(np.float32)
    sparkle = ndimage.gaussian_filter(sparkle, 1.0) * 6
    alb = np.clip(alb + sparkle[..., None] * hexc("9cb4ff") * 0.6, 0, 1)
    return alb, 1.0, 1.0


def fin_urban(kn, mask, sfrac, d_edge, d_spine, bev):
    tow, sheen, over = carbon_weave(0.28, 91)
    carbon = 0.08 + 0.10 * tow * (0.45 + 0.55 * sheen)
    n = fbm(1.0, 93, 4, aniso=(1.5, 1.0))
    alb = ramp(n, [(0, hexc("0e0f11")), (0.42, hexc("1d2023")), (0.56, hexc("3c4047")),
                   (0.70, hexc("6c727b")), (1, hexc("9aa0a8"))])
    patch = smoothstep(0.46, 0.43, n)
    carbon_rgb = np.stack([carbon, carbon * 1.02, carbon * 1.06], -1)
    alb = lerp(alb, carbon_rgb, patch)
    alb *= (0.95 + 0.06 * brushed(94))[..., None]
    return alb, 0.85, 0.65


def doppler_like(seed, stops, sparkle_colour, sparkle=0.9993):
    """Swirled anodised gem finish, used by the Sapphire, Emerald and Ruby blades."""
    n = fbm(1.6, seed, 5)
    def swirl(u, v):
        return np.sin(u * 0.9 + v * 1.7) + 0.6 * np.sin(u * 2.1 - v * 1.1)
    sw = warp(swirl, 3.2, 1.8, seed + 2)
    t = 0.52 + 0.22 * sw + 0.35 * (n - 0.5)
    alb = ramp(t, stops)
    dots = (np.random.default_rng(seed + 4).random((GH, GW)) > sparkle).astype(np.float32)
    dots = ndimage.gaussian_filter(dots, 1.0) * 6
    return np.clip(alb + dots[..., None] * hexc(sparkle_colour) * 0.6, 0, 1)


def fin_emerald(kn, mask, sfrac, d_edge, d_spine, bev):
    alb = doppler_like(1101, [(0.0, hexc("010805")), (0.22, hexc("053a1c")), (0.45, hexc("0b7a36")),
                              (0.66, hexc("1cc05a")), (0.82, hexc("5cf09a")), (0.92, hexc("2fd0c0")), (1.0, hexc("c8ffe0"))], "9cffc8")
    return alb, 1.0, 1.0


def fin_ruby(kn, mask, sfrac, d_edge, d_spine, bev):
    alb = doppler_like(1201, [(0.0, hexc("0c0103")), (0.22, hexc("4a0310")), (0.45, hexc("9a0a22")),
                              (0.66, hexc("dc1834")), (0.82, hexc("ff4a5c")), (0.92, hexc("ff7a8a")), (1.0, hexc("ffd0d6"))], "ffb0ba")
    return alb, 1.0, 1.0


def fin_marble(kn, mask, sfrac, d_edge, d_spine, bev):
    """Marble fade: a red-yellow flame swirl that runs into a blue tip."""
    def flame(u, v):
        return np.sin(u * 0.7 + v * 2.3) + 0.8 * np.sin(u * 1.7 - v * 1.3 + 1.0)
    sw = warp(flame, 2.4, 1.5, 1301)
    t = 0.5 + 0.25 * sw + 0.25 * (fbm(1.1, 1303, 4) - 0.5)
    fire = ramp(t, [(0.0, hexc("5a0606")), (0.25, hexc("c41810")), (0.5, hexc("f05a10")),
                    (0.72, hexc("ffb020")), (0.9, hexc("ffe68a")), (1.0, hexc("fff8e0"))])
    ice = ramp(t, [(0.0, hexc("0a1440")), (0.5, hexc("2050e0")), (1.0, hexc("8ab4ff"))])
    blue = smoothstep(0.55, 0.85, sfrac + 0.12 * (fbm(0.8, 1305, 3) - 0.5))
    alb = lerp(fire, ice, blue)
    alb *= (0.95 + 0.07 * brushed(1306))[..., None]
    return alb, 1.0, 0.95


def fin_ultraviolet(kn, mask, sfrac, d_edge, d_spine, bev):
    """Ultraviolet: dark anodised steel with a violet cast, worn bright along scratches and the edge."""
    n = fbm(1.2, 1401, 5, aniso=(1.8, 1.0))
    alb = ramp(n, [(0, hexc("08060e")), (0.4, hexc("1c1430")), (0.65, hexc("3a2a66")), (1, hexc("7058b0"))])
    scr = smoothstep(0.80, 0.86, fbm(0.25, 1403, 4, aniso=(6.0, 0.18)))
    alb = lerp(alb, hexc("b8b2c8"), scr * 0.55)
    wear = smoothstep(0.62, 0.78, fbm(0.7, 1405, 4)) * smoothstep(0.35, 0.0, d_edge) if d_edge is not None else 0
    alb = lerp(alb, hexc("9a96a6"), np.clip(wear, 0, 1) * 0.6)
    alb *= (0.94 + 0.08 * brushed(1406))[..., None]
    return alb, 0.9, 0.8


def outline(stations, cv=0.0):
    """stations: (u, top_half, bottom_half) -> polygon around the centre line."""
    top = [(u, cv - a) for u, a, b in stations]
    bot = [(u, cv + b) for u, a, b in stations[::-1]]
    return top + bot


def interp_stations(stations, n=160):
    us = np.array([s[0] for s in stations])
    a = np.array([s[1] for s in stations])
    b = np.array([s[2] for s in stations])
    uu = np.linspace(us[0], us[-1], n)
    return list(zip(uu, np.interp(uu, us, a), np.interp(uu, us, b)))


def finger_grooves(stations, u0, u1, n, depth):
    out = []
    for u, a, b in stations:
        if u0 < u < u1:
            k = (u - u0) / (u1 - u0)
            b = b - depth * (0.5 - 0.5 * math.cos(k * n * 2 * math.pi))
        out.append((u, a, b))
    return out


def handle_body(kn, stations, cv=0.0):
    pts = outline(stations, cv)
    mask = poly_mask(pts)
    inside = sdf_inside(mask)
    return mask, inside


def stipple(seed, size=0.06):
    rng = np.random.default_rng(seed)
    n = rng.random((GH, GW)).astype(np.float32)
    return ndimage.gaussian_filter(n, size * GP) - ndimage.gaussian_filter(n, size * GP * 3)


def rubber(kn, mask, colour, rough_seed, panel=None, rnd=0.62, pt=0.0):
    st = stipple(rough_seed) * 6
    alb = np.broadcast_to(colour, (GH, GW, 3)) * (0.92 + 0.10 * st[..., None])
    h = 0.05 * st
    kn.paint(mask, alb, height=h, metal=0.0, gloss=0.18, rnd=rnd, side=colour * 0.5)


def dome(cu, cv, r):
    d = np.hypot(U - cu, V - cv)
    m = (d <= r).astype(np.float32)
    h = np.sqrt(np.clip(1 - (d / r) ** 2, 0, 1)) * r
    return m, h, d


def brass(kn, mask, h=None, seed=5, rnd=0.12, gloss=0.95):
    n = fbm(0.8, seed, 4)
    hh = np.zeros((GH, GW), np.float32) if h is None else h
    tone = 0.62 + 0.25 * (n - 0.5) + 0.06 * brushed(seed + 1)
    alb = ramp(tone, BRASS)
    kn.paint(mask, alb, height=hh, metal=1.0, gloss=gloss, bevel=np.clip(hh * 2.5, 0, 1) * 0.6,
             rnd=rnd, side=hexc("6e4a18"))


def screw(kn, cu, cv, r=0.22, kind="torx", colour="2a2c30", metal=0.8):
    m, h, d = dome(cu, cv, r)
    c = hexc(colour)
    alb = np.broadcast_to(c, (GH, GW, 3)) * (0.75 + 0.5 * (h / r))[..., None]
    if kind == "torx":
        ang = np.arctan2(V - cv, U - cu)
        star = (d < r * (0.42 + 0.10 * np.cos(ang * 6)))
        alb = np.where(star[..., None], alb * 0.25, alb)
        h = np.where(star, h - 0.08, h)
    ring = np.clip(1 - np.abs(d - r) / 0.04, 0, 1)
    alb = alb * (1 - 0.6 * ring[..., None])
    kn.paint(m, alb, height=h * 0.6, metal=metal, gloss=0.8, rnd=0.0, side=c * 0.6)


def hole(kn, cu, cv, r, lip=0.08, lip_colour=None):
    d = np.hypot(U - cu, V - cv)
    if lip_colour is not None:
        lipm = ((d > r) & (d < r + lip)).astype(np.float32)
        kn.alb = lerp(kn.alb, kn.alb * 0.45, lipm)
    kn.cut((d <= r).astype(np.float32))


def ring(kn, cu, cv, r_out, r_in):
    d = np.hypot(U - cu, V - cv)
    m = ((d <= r_out) & (d >= r_in)).astype(np.float32)
    mid = (r_out + r_in) / 2
    half = (r_out - r_in) / 2
    h = np.sqrt(np.clip(1 - ((d - mid) / half) ** 2, 0, 1)) * half
    brass(kn, m, h=h, seed=int(cu * 10), rnd=half * 0.9)


def paracord(kn, mask, u0, u1, colour, seed):
    """Diagonal cord wraps with a woven over/under pattern."""
    pitch = 0.42
    a = (U - u0) / pitch + V * 0.55
    fa = a % 1.0
    cord = np.sin(fa * math.pi)                       # each wrap is a rounded tube
    weave = 0.5 + 0.5 * np.sin(V * 9.0 + np.floor(a) * math.pi)
    strands = 0.5 + 0.5 * np.sin((U * 14 - V * 22) + np.floor(a) * 1.7)
    h = 0.18 * cord + 0.04 * weave * cord + 0.015 * strands
    shade = 0.45 + 0.55 * cord ** 0.7
    c_hi, c_lo = colour * 1.25, colour * 0.45
    alb = lerp(c_lo, np.minimum(1, c_hi), shade * (0.85 + 0.15 * strands))
    fleck = (np.random.default_rng(seed).random((GH, GW)) > 0.985).astype(np.float32)
    alb = lerp(alb, np.ones_like(alb) * 0.85, ndimage.gaussian_filter(fleck, 0.8) * 0.6)
    kn.paint(mask, alb, height=h, metal=0.0, gloss=0.12, rnd=0.55, side=colour * 0.45)


def ribs(stations_u0, stations_u1, pitch, depth):
    ph = ((U - stations_u0) / pitch) % 1.0
    zone = ((U > stations_u0) & (U < stations_u1)).astype(np.float32)
    rib = np.sin(ph * math.pi) ** 2
    return zone * depth * rib, zone, ph


def g10(colour, seed, layers=True):
    n = fbm(1.0, seed, 4, aniso=(3.0, 0.4))
    alb = np.broadcast_to(colour, (GH, GW, 3)) * (0.86 + 0.25 * (n - 0.5))[..., None]
    if layers:
        lam = 0.5 + 0.5 * np.sin(V * 34 + 3 * fbm(1.2, seed + 3, 3))
        alb = alb * (0.94 + 0.08 * lam[..., None])
    return alb


# =================================================================== the eight knives
GUARD_A = 12.7


def guard_cross(kn, u0, u1, top, bot, knob=0.0, curve=0.0, seed=3):
    """A brass crossguard; knob rounds the quillon ends."""
    pts = []
    n = 40
    for i in range(n + 1):
        k = i / n
        v = -top + (top + bot) * k
        du = curve * (2 * k - 1) ** 2
        pts.append((u1 + du, v))
    for i in range(n, -1, -1):
        k = i / n
        v = -top + (top + bot) * k
        du = curve * (2 * k - 1) ** 2
        pts.append((u0 + du, v))
    m = poly_mask(pts)
    if knob:
        for vv in (-top, bot):
            mm, hh, _ = dome((u0 + u1) / 2 + curve, vv, knob)
            m = np.maximum(m, mm)
    inside = sdf_inside(m)
    h = np.clip(inside / 0.22, 0, 1) ** 0.5 * 0.22
    brass(kn, m, h=h, seed=seed, rnd=0.16)
    return m


def k1():
    kn = Knife("tac_damascus", "Tactical · Damascus")
    st = interp_stations([(0.55, 1.40, 1.40), (1.6, 1.55, 1.62), (3.0, 1.62, 1.72), (5.5, 1.56, 1.66),
                          (8.0, 1.52, 1.70), (10.5, 1.55, 1.80), (12.2, 1.48, 1.72), (12.9, 1.40, 1.60)])
    st = finger_grooves(st, 6.2, 12.3, 3, 0.24)
    m, inside = handle_body(kn, st)
    rubber(kn, m, hexc("17181b"), 101)
    # stippled grip panel and orange accents
    panel = poly_mask(outline([(s[0], s[1] - 0.42, s[2] - 0.42) for s in st if 2.4 < s[0] < 11.6]))
    st2 = stipple(103, 0.035) * 9
    kn.alb = lerp(kn.alb, hexc("0b0b0d") * (0.9 + 0.2 * st2[..., None]), panel * 0.9)
    kn.height += panel * 0.06 * st2
    for a, b in ((1.65, 2.05), (11.75, 12.15)):
        band = ((U > a) & (U < b)).astype(np.float32) * m
        kn.alb = lerp(kn.alb, hexc("ee6a12"), band)
    edgeband = np.clip(1 - np.abs(inside - 0.22) / 0.05, 0, 1) * ((U > 2.0) & (U < 11.8)) * m
    kn.alb = lerp(kn.alb, hexc("e35f10"), edgeband * 0.9)
    # brass pommel cap with a lanyard hole
    cap = poly_mask(outline(interp_stations([(0.25, 1.15, 1.15), (0.6, 1.45, 1.45), (1.3, 1.55, 1.6), (1.6, 1.5, 1.55)])))
    ci = sdf_inside(cap)
    brass(kn, cap, h=np.clip(ci / 0.3, 0, 1) ** 0.5 * 0.2, seed=105)
    hole(kn, 0.95, 0.0, 0.28, lip_colour=True)
    guard_cross(kn, GUARD_A, GUARD_A + 1.0, 2.05, 2.20, knob=0.42, curve=0.10, seed=107)
    blade(kn, GUARD_A + 1.0, 28.0, -1.55, 1.78, 0.55, clip=0.60, belly=0.72, clip_pow=1.15,
          teeth=(0.05, 0.46, 0.44, 0.68), fuller=(0.09, 0.50, -0.62, 0.14),
          etch=("ZEUS", 0.64, 0.55, 0.42), finish=fin_damascus)
    kn.part("pommel", 0.2, 1.6, 1.05, 0.30)
    kn.part("handle", 1.6, GUARD_A, 1.08, 0.62)
    kn.part("guard", GUARD_A, GUARD_A + 1.0, 1.15, 0.22)
    kn.part("blade", GUARD_A + 1.0, 28.2, 0.26, 0.0)
    kn.glow = "#dfe6ef"
    return kn


def k2():
    kn = Knife("tac_slaughter", "Tactical · Slaughter")
    st = interp_stations([(0.45, 1.05, 1.05), (0.9, 1.5, 1.5), (2.0, 1.66, 1.70), (5.0, 1.58, 1.66),
                          (8.5, 1.56, 1.72), (11.0, 1.60, 1.84), (12.4, 1.55, 1.95), (12.9, 1.45, 1.85)])
    st = finger_grooves(st, 6.6, 12.4, 3, 0.22)
    m, inside = handle_body(kn, st)
    orange = hexc("e8661a")
    rubber(kn, m, orange, 201, rnd=0.6)
    panel_st = [(s[0], s[1] - 0.62, s[2] - 0.62) for s in st if 2.6 < s[0] < 11.0]
    panel = poly_mask(outline(panel_st))
    pi = sdf_inside(panel)
    grip = stipple(203, 0.04) * 8
    checker = (np.sin(U * 9.0) * np.sin(V * 9.0) > 0).astype(np.float32)
    kn.alb = lerp(kn.alb, hexc("121214") * (0.85 + 0.25 * grip[..., None]) * (0.9 + 0.12 * checker[..., None]), panel)
    kn.height += panel * (0.07 * grip + 0.05 * np.clip(pi / 0.08, 0, 1))
    hole(kn, 1.2, 0.0, 0.30, lip_colour=True)
    # steel bolster with a finger lip on the edge side
    bol = poly_mask([(GUARD_A, -1.55), (GUARD_A + 0.55, -1.52), (GUARD_A + 0.55, 2.25), (GUARD_A + 0.15, 2.45), (GUARD_A, 2.0)])
    bi = sdf_inside(bol)
    alb = steel_base(205, 0.62)
    kn.paint(bol, alb, height=np.clip(bi / 0.2, 0, 1) * 0.15, metal=1.0, gloss=0.8,
             bevel=np.clip(1 - bi / 0.1, 0, 1) * 0.5, rnd=0.12, side=hexc("4a4e55"))
    blade(kn, GUARD_A + 0.55, 28.0, -1.55, 1.75, 0.50, clip=0.60, belly=0.72, clip_pow=1.15,
          teeth=(0.06, 0.46, 0.42, 0.66), fuller=(0.10, 0.48, -0.62, 0.13), finish=fin_slaughter)
    kn.part("handle", 0.3, GUARD_A, 1.10, 0.62)
    kn.part("guard", GUARD_A, GUARD_A + 0.55, 1.0, 0.12)
    kn.part("blade", GUARD_A + 0.55, 28.2, 0.26, 0.0)
    kn.glow = "#ff4a3a"
    return kn


def k3():
    kn = Knife("tac_case", "Tactical · Case hardened")
    st = interp_stations([(0.55, 1.35, 1.35), (1.6, 1.50, 1.55), (3.5, 1.60, 1.62), (7.0, 1.52, 1.58),
                          (10.5, 1.58, 1.68), (12.9, 1.45, 1.55)])
    m, inside = handle_body(kn, st)
    rubber(kn, m, hexc("141416"), 301)
    rb, zone, ph = ribs(2.1, 12.3, 0.62, 0.10)
    kn.height += rb * m
    kn.alb = kn.alb * (1 - 0.35 * (zone * m * (1 - np.sin(ph * math.pi) ** 2))[..., None])
    cap = poly_mask(outline(interp_stations([(0.25, 1.2, 1.2), (0.7, 1.48, 1.48), (1.4, 1.55, 1.58), (1.75, 1.5, 1.52)])))
    ci = sdf_inside(cap)
    brass(kn, cap, h=np.clip(ci / 0.3, 0, 1) ** 0.5 * 0.2, seed=305)
    ferr = poly_mask(outline([(12.2, 1.55, 1.62), (12.75, 1.55, 1.62)]))
    brass(kn, ferr, h=np.clip(sdf_inside(ferr) / 0.2, 0, 1) * 0.12, seed=306)
    guard_cross(kn, GUARD_A, GUARD_A + 0.95, 2.0, 2.1, knob=0.40, curve=0.16, seed=307)
    blade(kn, GUARD_A + 0.95, 28.0, -1.50, 1.72, 0.50, clip=0.62, belly=0.72, clip_pow=1.15,
          teeth=(0.05, 0.44, 0.40, 0.64), fuller=(0.09, 0.50, -0.60, 0.13),
          etch=("ZEUS", 0.64, 0.55, 0.40), finish=fin_case)
    kn.part("pommel", 0.2, 1.8, 1.05, 0.30)
    kn.part("handle", 1.8, GUARD_A, 1.05, 0.60)
    kn.part("guard", GUARD_A, GUARD_A + 0.95, 1.15, 0.22)
    kn.part("blade", GUARD_A + 0.95, 28.2, 0.26, 0.0)
    kn.glow = "#ffd166"
    return kn


def k4():
    kn = Knife("tac_fade", "Tactical · Fade")
    st = interp_stations([(0.45, 1.25, 1.55), (1.2, 1.62, 1.85), (2.5, 1.55, 1.70), (5.5, 1.42, 1.55),
                          (9.0, 1.45, 1.62), (11.5, 1.55, 1.75), (12.9, 1.45, 1.60)])
    st = finger_grooves(st, 7.0, 12.4, 3, 0.24)
    m, inside = handle_body(kn, st)
    alb = g10(hexc("b81e26"), 401)
    kn.paint(m, alb, height=np.zeros((GH, GW), np.float32), metal=0.0, gloss=0.55, rnd=0.55, side=hexc("5a0c10"))
    for cu in (3.0, 7.0, 11.0):
        screw(kn, cu, 0.05, 0.22)
    hole(kn, 1.05, 0.15, 0.30, lip_colour=True)
    guard_cross(kn, GUARD_A, GUARD_A + 0.55, 2.55, 2.55, knob=0.0, curve=-0.18, seed=407)
    blade(kn, GUARD_A + 0.55, 28.0, -1.55, 1.78, 0.55, clip=0.58, belly=0.72, clip_pow=1.15,
          teeth=(0.05, 0.46, 0.44, 0.68), fuller=(0.09, 0.52, -0.62, 0.14), finish=fin_fade)
    kn.part("handle", 0.3, GUARD_A, 1.0, 0.50)
    kn.part("guard", GUARD_A, GUARD_A + 0.55, 1.0, 0.14)
    kn.part("blade", GUARD_A + 0.55, 28.2, 0.26, 0.0)
    kn.glow = "#b04cff"
    return kn


def k5():
    kn = Knife("tac_crimson", "Tactical · Crimson web")
    st = interp_stations([(1.4, 1.25, 1.30), (2.2, 1.55, 1.60), (5.0, 1.50, 1.60), (9.0, 1.50, 1.65),
                          (11.6, 1.55, 1.95), (12.6, 1.50, 2.35), (13.3, 1.45, 2.15)])
    m, inside = handle_body(kn, st)
    rubber(kn, m, hexc("121315"), 501, rnd=0.55)
    rb, zone, ph = ribs(2.4, 11.3, 0.75, 0.16)
    groove = zone * m * smoothstep(0.85, 0.98, np.cos((ph - 0.5) * math.pi * 2) * 0.5 + 0.5)
    kn.alb = lerp(kn.alb, kn.alb * 0.25, groove * 0.9)
    kn.height -= groove * 0.14
    for cu in (4.0, 9.5):
        screw(kn, cu, 0.0, 0.20, colour="3a3c42")
    ring(kn, 0.75, 0.0, 0.70, 0.42)
    blade(kn, 13.3, 28.0, -1.40, 1.55, -0.55, clip=0.62, belly=0.62, clip_pow=1.0,
          teeth=(0.04, 0.22, 0.18, 0.32), notch=(0.70, 0.80, 0.18), fuller=None,
          bevel_w=0.95, finish=fin_crimson, tang=0)
    kn.part("ring", 0.0, 1.45, 0.42, 0.28)
    kn.part("handle", 1.45, 13.3, 1.05, 0.55)
    kn.part("blade", 13.3, 28.2, 0.27, 0.0)
    kn.glow = "#ff2a3c"
    return kn


def k6():
    kn = Knife("tac_tiger", "Tactical · Tiger tooth")
    st = interp_stations([(2.6, 1.20, 1.20), (3.2, 1.45, 1.45), (6.5, 1.40, 1.45), (10.0, 1.42, 1.50),
                          (12.2, 1.48, 1.55), (12.75, 1.42, 1.48)])
    m, inside = handle_body(kn, st)
    paracord(kn, m, 3.2, 12.2, hexc("2f63d8"), 601)
    for a, b in ((2.55, 3.25), (12.15, 12.75)):
        f = poly_mask(outline([(a, 1.48, 1.50), (b, 1.48, 1.50)]))
        brass(kn, f, h=np.clip(sdf_inside(f) / 0.2, 0, 1) * 0.14, seed=int(a * 7))
    ring(kn, 1.45, 0.0, 1.25, 0.78)
    guard_cross(kn, GUARD_A, GUARD_A + 0.62, 1.95, 2.05, knob=0.30, seed=607)
    blade(kn, GUARD_A + 0.62, 28.0, -1.45, 1.65, 0.40, clip=0.62, belly=0.70, clip_pow=1.15,
          teeth=(0.05, 0.50, 0.36, 0.60), fuller=None, finish=fin_tiger)
    kn.part("ring", 0.1, 2.7, 0.40, 0.30)
    kn.part("handle", 2.7, GUARD_A, 0.98, 0.55)
    kn.part("guard", GUARD_A, GUARD_A + 0.62, 1.1, 0.16)
    kn.part("blade", GUARD_A + 0.62, 28.2, 0.26, 0.0)
    kn.glow = "#ffb030"
    return kn


def k7():
    kn = Knife("tac_doppler", "Tactical · Sapphire")
    st = interp_stations([(0.35, 0.60, 0.60), (0.9, 1.20, 1.25), (2.0, 1.42, 1.48), (5.5, 1.30, 1.40),
                          (9.0, 1.30, 1.45), (11.6, 1.40, 1.55), (12.9, 1.35, 1.45)])
    st = finger_grooves(st, 6.8, 12.4, 3, 0.14)
    m, inside = handle_body(kn, st)
    alb, _, _ = fin_doppler(kn, m, None, None, None, None)
    alb = alb * 0.85
    kn.paint(m, alb, height=np.zeros((GH, GW), np.float32), metal=1.0, gloss=1.0, rnd=0.55, side=hexc("0a1440"))
    guard_cross(kn, GUARD_A, GUARD_A + 0.55, 2.35, 2.35, knob=0.30, curve=0.0, seed=707)
    blade(kn, GUARD_A + 0.55, 28.0, -1.35, 1.55, 0.05, clip=0.66, belly=0.55, clip_pow=1.6,
          teeth=None, serr=(0.05, 0.36, 0.26, 0.45), fuller=None,
          etch=("ZEUS", 0.30, -0.65, 0.40), finish=fin_doppler)
    kn.part("handle", 0.2, GUARD_A, 0.95, 0.50)
    kn.part("guard", GUARD_A, GUARD_A + 0.55, 1.05, 0.15)
    kn.part("blade", GUARD_A + 0.55, 28.2, 0.26, 0.0)
    kn.glow = "#4a7dff"
    return kn


def k8():
    kn = Knife("tac_urban", "Tactical · Urban masked")
    # the handle sweeps down toward the edge side into a hook, then a big ring
    # (u, top, bottom) around a centre line that bends towards the edge side at the pommel
    raw = [(2.3, 1.00, 1.00, 1.15), (3.0, 1.05, 1.25, 1.0), (4.5, 1.20, 1.35, 0.62), (7.0, 1.38, 1.48, 0.22),
           (10.0, 1.45, 1.55, 0.0), (12.0, 1.55, 1.70, 0.0), (12.9, 1.45, 1.60, 0.0)]
    st = interp_stations([(u, a - d, b + d) for u, a, b, d in raw])
    st = finger_grooves(st, 6.0, 12.4, 3, 0.20)
    m, inside = handle_body(kn, st, cv=0.0)
    alb = g10(hexc("17181a"), 801)
    kn.paint(m, alb, height=np.zeros((GH, GW), np.float32), metal=0.0, gloss=0.5, rnd=0.55, side=hexc("0b0b0c"))
    rim = np.clip(1 - np.abs(inside - 0.16) / 0.05, 0, 1) * m
    kn.alb = lerp(kn.alb, hexc("3a3c40"), rim * 0.6)
    for cu, cv in ((5.5, 0.15), (10.5, 0.0)):
        mm, hh, d = dome(cu, cv, 0.22)
        brass(kn, mm, h=hh * 0.7, seed=int(cu * 10), rnd=0.0)
    ring(kn, 1.40, 1.05, 1.25, 0.80)
    blade(kn, 12.9, 28.0, -1.50, 1.70, 0.50, clip=0.60, belly=0.72, clip_pow=1.15,
          teeth=(0.66, 0.80, 0.18, 0.30), fuller=None, finish=fin_urban, tang=0)
    kn.part("ring", 0.0, 2.6, 0.38, 0.30)
    kn.part("handle", 2.6, 12.9, 1.0, 0.50)
    kn.part("blade", 12.9, 28.2, 0.26, 0.0)
    kn.glow = "#c8d0dc"
    return kn


def k9():
    kn = Knife("tac_emerald", "Tactical · Emerald")
    st = interp_stations([(0.45, 1.20, 1.20), (1.2, 1.58, 1.62), (3.0, 1.60, 1.70), (6.0, 1.50, 1.60),
                          (9.5, 1.50, 1.70), (12.0, 1.55, 1.80), (12.9, 1.45, 1.62)])
    st = finger_grooves(st, 6.4, 12.3, 3, 0.22)
    m, inside = handle_body(kn, st)
    alb = g10(hexc("1a2c20"), 901)
    kn.paint(m, alb, height=np.zeros((GH, GW), np.float32), metal=0.0, gloss=0.55, rnd=0.58, side=hexc("070b08"))
    inlay = np.clip(1 - np.abs(inside - 0.20) / 0.045, 0, 1) * ((U > 2.0) & (U < 12.0)) * m
    kn.alb = lerp(kn.alb, hexc("18b05a"), inlay * 0.85)
    for cu in (3.2, 7.4, 11.2):
        screw(kn, cu, 0.05, 0.21, colour="1c2a22")
    cap = poly_mask(outline(interp_stations([(0.2, 1.05, 1.05), (0.55, 1.42, 1.42), (1.2, 1.55, 1.58), (1.5, 1.5, 1.52)])))
    brass(kn, cap, h=np.clip(sdf_inside(cap) / 0.3, 0, 1) ** 0.5 * 0.2, seed=905)
    hole(kn, 0.85, 0.0, 0.26, lip_colour=True)
    guard_cross(kn, GUARD_A, GUARD_A + 0.8, 2.15, 2.25, knob=0.38, curve=0.08, seed=907)
    blade(kn, GUARD_A + 0.8, 28.0, -1.55, 1.78, 0.55, clip=0.60, belly=0.72, clip_pow=1.15,
          teeth=(0.05, 0.46, 0.44, 0.68), fuller=(0.09, 0.50, -0.62, 0.14),
          etch=("ZEUS", 0.64, 0.55, 0.42), finish=fin_emerald)
    kn.part("pommel", 0.2, 1.5, 1.05, 0.30)
    kn.part("handle", 1.5, GUARD_A, 1.06, 0.58)
    kn.part("guard", GUARD_A, GUARD_A + 0.8, 1.15, 0.22)
    kn.part("blade", GUARD_A + 0.8, 28.2, 0.26, 0.0)
    kn.glow = "#34e07a"
    return kn


def k10():
    kn = Knife("tac_marble", "Tactical · Marble fade")
    st = interp_stations([(2.6, 1.20, 1.20), (3.2, 1.45, 1.45), (6.5, 1.40, 1.45), (10.0, 1.42, 1.50),
                          (12.2, 1.48, 1.55), (12.75, 1.42, 1.48)])
    m, inside = handle_body(kn, st)
    paracord(kn, m, 3.2, 12.2, hexc("c42a18"), 1001)
    for a, b in ((2.55, 3.25), (12.15, 12.75)):
        f = poly_mask(outline([(a, 1.48, 1.50), (b, 1.48, 1.50)]))
        brass(kn, f, h=np.clip(sdf_inside(f) / 0.2, 0, 1) * 0.14, seed=int(a * 11))
    ring(kn, 1.45, 0.0, 1.25, 0.78)
    guard_cross(kn, GUARD_A, GUARD_A + 0.62, 2.05, 2.15, knob=0.32, curve=0.10, seed=1007)
    blade(kn, GUARD_A + 0.62, 28.0, -1.50, 1.70, 0.45, clip=0.62, belly=0.70, clip_pow=1.15,
          teeth=(0.05, 0.48, 0.38, 0.62), fuller=(0.10, 0.48, -0.60, 0.13), finish=fin_marble)
    kn.part("ring", 0.1, 2.7, 0.40, 0.30)
    kn.part("handle", 2.7, GUARD_A, 0.98, 0.55)
    kn.part("guard", GUARD_A, GUARD_A + 0.62, 1.1, 0.16)
    kn.part("blade", GUARD_A + 0.62, 28.2, 0.26, 0.0)
    kn.glow = "#ff8a2a"
    return kn


def k11():
    kn = Knife("tac_ruby", "Tactical · Ruby")
    st = interp_stations([(0.35, 0.60, 0.60), (0.9, 1.20, 1.25), (2.0, 1.42, 1.48), (5.5, 1.30, 1.40),
                          (9.0, 1.30, 1.45), (11.6, 1.40, 1.55), (12.9, 1.35, 1.45)])
    st = finger_grooves(st, 6.8, 12.4, 3, 0.14)
    m, inside = handle_body(kn, st)
    alb = g10(hexc("40101a"), 1101)
    kn.paint(m, alb, height=np.zeros((GH, GW), np.float32), metal=0.0, gloss=0.6, rnd=0.55, side=hexc("0c0406"))
    for cu in (3.0, 7.0, 11.0):
        screw(kn, cu, 0.05, 0.20, colour="3a1018")
    guard_cross(kn, GUARD_A, GUARD_A + 0.55, 2.35, 2.35, knob=0.30, curve=0.0, seed=1107)
    blade(kn, GUARD_A + 0.55, 28.0, -1.35, 1.55, 0.05, clip=0.66, belly=0.55, clip_pow=1.6,
          teeth=None, serr=(0.05, 0.36, 0.26, 0.45), fuller=None,
          etch=("ZEUS", 0.30, -0.65, 0.40), finish=fin_ruby)
    kn.part("handle", 0.2, GUARD_A, 0.95, 0.50)
    kn.part("guard", GUARD_A, GUARD_A + 0.55, 1.05, 0.15)
    kn.part("blade", GUARD_A + 0.55, 28.2, 0.26, 0.0)
    kn.glow = "#ff2a48"
    return kn


def k12():
    kn = Knife("tac_ultraviolet", "Tactical · Ultraviolet")
    st = interp_stations([(0.55, 1.35, 1.35), (1.6, 1.50, 1.55), (3.5, 1.60, 1.62), (7.0, 1.52, 1.58),
                          (10.5, 1.58, 1.68), (12.9, 1.45, 1.55)])
    m, inside = handle_body(kn, st)
    rubber(kn, m, hexc("15111c"), 1201)
    rb, zone, ph = ribs(2.1, 12.3, 0.62, 0.10)
    kn.height += rb * m
    kn.alb = kn.alb * (1 - 0.35 * (zone * m * (1 - np.sin(ph * math.pi) ** 2))[..., None])
    cap = poly_mask(outline(interp_stations([(0.25, 1.2, 1.2), (0.7, 1.48, 1.48), (1.4, 1.55, 1.58), (1.75, 1.5, 1.52)])))
    ci = sdf_inside(cap)
    kn.paint(cap, steel_base(1205, 0.55), height=np.clip(ci / 0.3, 0, 1) ** 0.5 * 0.2, metal=1.0, gloss=0.8,
             bevel=np.clip(1 - ci / 0.1, 0, 1) * 0.5, rnd=0.12, side=hexc("3a3e46"))
    guard_cross(kn, GUARD_A, GUARD_A + 0.95, 2.0, 2.1, knob=0.40, curve=0.16, seed=1207)
    blade(kn, GUARD_A + 0.95, 28.0, -1.50, 1.72, 0.50, clip=0.62, belly=0.72, clip_pow=1.15,
          teeth=(0.05, 0.44, 0.40, 0.64), fuller=(0.09, 0.50, -0.60, 0.13),
          etch=("ZEUS", 0.64, 0.55, 0.40), finish=fin_ultraviolet)
    kn.part("pommel", 0.2, 1.8, 1.05, 0.30)
    kn.part("handle", 1.8, GUARD_A, 1.05, 0.60)
    kn.part("guard", GUARD_A, GUARD_A + 0.95, 1.15, 0.22)
    kn.part("blade", GUARD_A + 0.95, 28.2, 0.26, 0.0)
    kn.glow = "#9a6cff"
    return kn


KNIVES = [k1, k2, k3, k4, k5, k6, k7, k8, k9, k10, k11, k12]


# =================================================================== bake
def erode(mask, r_units):
    """Shrink a mask by a per-pixel radius in units (array)."""
    inside = sdf_inside(mask)
    return np.clip((inside - r_units) * GP * 0.5 + 0.5, 0, 1) * (mask > 0.5)


def bake(kn):
    mask = np.clip(kn.mask, 0, 1)
    h = kn.height
    n = normals(h, 1.6)
    # relief light: only the difference from a flat face, so the dynamic light owns the rest
    ndl = np.clip((n * LIGHT).sum(-1), 0, 1)
    rel = ndl - LIGHT[2]
    cav = h - smooth(h, 0.18)
    ao = np.clip(1 + 2.4 * np.minimum(cav, 0), 0.35, 1)
    base = kn.alb * ao[..., None]
    lit = base * (0.92 + 0.95 * rel)[..., None]
    # polished metals mirror a soft studio: brighter where the relief tips toward the key light
    env = np.clip(0.5 + 0.9 * rel + 0.25 * n[..., 1] * -1, 0, 1.4)
    lit = lerp(lit, np.minimum(1, lit * (0.7 + 0.6 * env[..., None])), kn.metal * 0.5)
    lit = np.clip(lit, 0, 1)

    # outline contact shadow to separate parts and to give the silhouette a crisp edge
    edge_dist = sdf_inside(mask)
    rim_dark = np.clip(1 - edge_dist / 0.05, 0, 1) * 0.35
    lit = lit * (1 - rim_dark[..., None])

    r = kn.round
    face_a = erode(mask, r)
    rim50_a = erode(mask, r * 0.5)
    rim13_a = erode(mask, r * 0.134)

    # the shoulders turn away from the face, so they get progressively darker
    rim50 = lit * 0.86
    rim13 = lit * 0.70

    side = np.clip(kn.side, 0, 1)

    # metals reflect in their own colour; plastics, rubber and cord give a faint white sheen
    spec_tint = lerp(np.ones_like(kn.alb) * 0.32, np.clip(kn.alb * 1.3 + 0.1, 0, 1), kn.metal)
    gloss = np.clip(kn.gloss * ao, 0, 1)
    gl = spec_tint * gloss[..., None]
    ev = np.clip(kn.bevel, 0, 1)
    edge_spec = lerp(np.ones_like(kn.alb), spec_tint, 0.4) * ev[..., None]

    profile_rgb, profile_a = profile_cell(kn, mask, side)
    cells = [
        (lit, face_a),
        (rim50, rim50_a),
        (rim13, rim13_a),
        (side, mask),
        (gl, None),
        (edge_spec, None),
        (profile_rgb, profile_a),
    ]
    return cells, mask


ZMAX = 1.4   # the profile cell covers z = -ZMAX .. ZMAX top to bottom


def profile_cell(kn, mask, side):
    """The knife seen from its spine: per column, the part's thickness, in its side colour."""
    cover = mask.max(0)                                   # does this column hold material
    wsum = mask.sum(0) + 1e-6
    col = (side * mask[..., None]).sum(0) / wsum[:, None]  # mean side colour of the column
    t = np.zeros(GW, np.float32)
    r = np.zeros(GW, np.float32)
    uu = U[0]
    for p in kn.parts:
        sel = (uu >= p["u0"]) & (uu < p["u1"])
        t[sel] = p["t"]
        r[sel] = p["r"]
    z = (np.arange(GH, dtype=np.float32) + 0.5) / GH * 2 * ZMAX - ZMAX
    zz = z[:, None]
    tt = t[None, :]
    inside = np.clip((tt - np.abs(zz)) * GP * 0.5 + 0.5, 0, 1) * cover[None, :]
    rr = np.maximum(r[None, :], 1e-3)
    across = np.clip(np.abs(zz) / np.maximum(tt, 1e-3), 0, 1)
    shade = np.where(r[None, :] > 0.05, 0.62 + 0.42 * np.sqrt(np.clip(1 - across ** 2, 0, 1)), 0.95 - 0.25 * across)
    rgb = np.clip(col[None, :, :] * shade[..., None] * 1.25, 0, 1)
    # a crisp lit line down the middle of a blade spine
    rgb = np.where((r[None, :] <= 0.05)[..., None], np.clip(rgb + 0.10 * (1 - across)[..., None], 0, 1), rgb)
    return rgb.astype(np.float32), inside.astype(np.float32)


def to_rgba(rgb, alpha, f, light_mask=None):
    rgb = downsample(rgb, f)
    if alpha is None:
        lm = downsample(light_mask, f)
        rgb = rgb * lm[..., None]
        # a whisper of alpha only where there is knife: the shader keeps those texels and they
        # add light; everywhere else alpha is 0 and the shader discards them
        a = np.where(lm > 0.002, 2.0 / 255.0, 0.0).astype(np.float32)
    else:
        a = downsample(alpha, f)
        # un-premultiply what the averaging premultiplied, so edges keep their colour
        pass
    if alpha is None:
        # the outermost rows are fully clear, so filtering never bleeds light into a neighbour cell
        a[:2] = 0
        a[-2:] = 0
    out = np.dstack([np.clip(rgb, 0, 1), np.clip(a, 0, 1)])
    return (out * 255 + 0.5).astype(np.uint8)


def write(kn):
    cells, mask = bake(kn)
    os.makedirs(OUT_TEX, exist_ok=True)
    prepared = []
    for rgb, alpha in cells:
        if alpha is not None:
            # colour bleed into transparent texels keeps linear filtering clean
            m = alpha > 0.02
            if m.any():
                idx = ndimage.distance_transform_edt(~m, return_distances=False, return_indices=True)
                rgb = rgb[idx[0], idx[1]]
        prepared.append((rgb, alpha))
    for level, f in ((0, SS), (1, SS * 2), (2, SS * 4)):
        rows = []
        for rgb, alpha in prepared:
            if alpha is not None:
                rows.append(to_rgba(rgb, alpha, f))
            else:
                rows.append(to_rgba(rgb, None, f, light_mask=mask))
        sheet = np.concatenate(rows, 0)
        Image.fromarray(sheet, "RGBA").save(os.path.join(OUT_TEX, f"{kn.id}_{level}.png"), optimize=True)
    # centre of material per column: the side wall strip runs along this line
    m = np.clip(kn.mask, 0, 1)
    wsum = m.sum(0)
    cv = (V * m).sum(0) / np.maximum(wsum, 1e-6)
    n = 142
    line = []
    for i in range(n + 1):
        u = LEN * i / n
        col = min(GW - 1, int(u * GP))
        lo, hi = max(0, col - 3), min(GW, col + 4)
        w = wsum[lo:hi]
        line.append(round(float((cv[lo:hi] * w).sum() / w.sum()), 3) if w.sum() > 1e-3 else 0.0)
    return {
        "id": kn.id,
        "centre": line,
        "name": kn.name,
        "glow": kn.glow,
        "tip": [round(kn.tip[0], 3), round(kn.tip[1], 3)],
        "parts": [{k: (round(v, 3) if isinstance(v, float) else v) for k, v in p.items()} for p in kn.parts],
    }


def main():
    only = sys.argv[1:]
    meta = {"len": LEN, "half": HALF, "cells": 7, "zmax": ZMAX, "levels": [[W, H], [W // 2, H // 2], [W // 4, H // 4]], "knives": []}
    old = {}
    if only and os.path.exists(OUT_JSON):
        for k in json.load(open(OUT_JSON))["knives"]:
            old[k["id"]] = k
    for fn in KNIVES:
        kid = fn.__name__
        if only and kid not in only:
            continue
        kn = fn()
        print("baking", kn.id, flush=True)
        old[kn.id] = write(kn)
    order = ["tac_damascus", "tac_slaughter", "tac_case", "tac_fade", "tac_crimson", "tac_tiger", "tac_doppler", "tac_urban",
             "tac_emerald", "tac_marble", "tac_ruby", "tac_ultraviolet"]
    meta["knives"] = [old[k] for k in order if k in old]
    os.makedirs(os.path.dirname(OUT_JSON), exist_ok=True)
    with open(OUT_JSON, "w") as f:
        json.dump(meta, f, indent=1)
    print("wrote", OUT_JSON)


if __name__ == "__main__":
    main()
