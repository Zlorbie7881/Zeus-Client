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

from PIL import Image, ImageDraw

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
            if self.slot:
                s0, s1, sh = self.slot
                if s0 <= u <= s1 and abs(v + 0.85) <= sh:
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
def shade_grain(u, v, scale=1.0):
    return 4.0 * math.sin(u * 9.0 * scale + v * 0.7) + 2.5 * math.sin(u * 31.0 * scale)


def pattern(colour, kind, u, v, b0, tipu):
    """The skin: returns rgb for a face pixel."""
    r, g, b = colour
    t = clamp01((u - b0) / max(1e-6, tipu - b0))

    def mix(c, k):
        return (int(lerp(c[0], r, k)), int(lerp(c[1], g, k)), int(lerp(c[2], b, k)))

    if kind == "plain":
        return colour
    if kind == "case":
        # blue and gold patchwork with violet blotches
        m = (math.sin(u * 1.7 + v * 2.3) + math.sin(u * 0.9 - v * 3.1)
             + 0.7 * math.sin(u * 3.1 + v * 5.2))
        if m > 0.55:
            return mix((52, 78, 168), 0.35)
        if m > 0.0:
            return mix((176, 140, 66), 0.30)
        if m < -1.2:
            return mix((116, 74, 92), 0.30)
        return mix((206, 172, 92), 0.22)
    if kind == "crimson":
        web = (math.sin(u * 4.3 + v * 6.1) * math.sin(u * 2.7 - v * 4.4))
        if abs(web) < 0.12:
            return (198, 26, 34)
        if abs(web) < 0.22:
            return (120, 14, 20)
        return mix((16, 14, 18), 0.06)
    if kind == "damascus":
        w = math.sin(u * 3.4 + 2.2 * math.sin(v * 1.6 + u * 0.4))
        k = 0.5 + 0.5 * w
        return (int(120 + 70 * k), int(126 + 70 * k), int(136 + 68 * k))
    if kind == "doppler":
        cloud = (math.sin(u * 1.1 + v * 1.7) + math.sin(u * 2.7 - v * 2.2)
                 + 0.5 * math.sin(u * 5.1 + v * 4.3))
        if cloud > 1.0:
            return (26, 36, 96)
        if cloud > 0.55:
            return (40, 62, 170)
        if cloud < -1.25:
            return (196, 96, 150)
        return (58, 92, 214)
    if kind == "fade":
        k = smooth(t)
        stops = [(198, 26, 38), (170, 20, 70), (96, 16, 96), (44, 18, 120), (28, 40, 150)]
        idx = min(len(stops) - 2, int(k * (len(stops) - 1)))
        f = k * (len(stops) - 1) - idx
        a, bb = stops[idx], stops[idx + 1]
        return tuple(int(lerp(a[i], bb[i], f)) for i in range(3))
    if kind == "slaughter":
        m = math.sin(u * 2.2 + v * 3.4) + 0.6 * math.sin(u * 5.6 - v * 1.2)
        if m > 0.9:
            return (168, 44, 44)
        if m > 0.3:
            return (128, 34, 38)
        g0 = int(126 + 16 * math.sin(u * 7.0))
        return (g0, g0 + 2, g0 + 6)
    if kind == "tiger":
        if math.sin(u * 3.05 + 0.6 * v) > 0.15:
            return (208, 152, 42)
        return (26, 20, 12)
    if kind == "camo":
        m = math.sin(u * 1.4 + v * 2.1) + math.sin(u * 3.6 - v * 1.7)
        if m > 0.8:
            return (168, 172, 178)
        if m > 0.2:
            return (96, 100, 106)
        return (34, 36, 40)
    return colour


def grip_colour(kind, u, v, h1):
    t = clamp01((u - 1.4) / max(1e-6, h1 - 1.4))
    if kind == "ribbed":
        band = 0.5 + 0.5 * math.sin(t * math.pi * 2 * 26)
        g = int(20 + 16 * band)
        return (g, g, g + 2)
    if kind == "groove":
        band = 0.5 + 0.5 * math.sin(t * math.pi * 2 * 14 + v * 0.6)
        g = int(24 + 12 * band)
        return (g, g + 1, g + 3)
    if kind == "cord":
        band = 0.5 + 0.5 * math.sin((t * 18 + v * 1.1) * math.pi)
        g = int(28 + 14 * band)
        return (g, g, g + 2)
    if kind == "wood":
        w = math.sin(v * 6.0 + t * 4.0) * 0.5 + 0.5
        return (int(120 + 40 * w), int(28 + 12 * w), int(30 + 10 * w))
    if kind == "tiger":
        s = 0.5 + 0.5 * math.sin((t * 30 - v * 1.4) * math.pi)
        return (int(38 + 24 * s), int(66 + 44 * s), int(140 + 60 * s))
    if kind == "orange":
        n = 0.5 + 0.5 * math.sin(u * 22 + v * 6)
        return (int(176 + 30 * n), int(72 + 18 * n), int(24 + 10 * n))
    if kind == "camo":
        return (22, 23, 26)
    return (30, 30, 34)


BRASS = (201, 161, 86)
STEEL = (164, 170, 180)


def render_face(k):
    w, cellh = LEVELS[0]
    img = Image.new("RGBA", (w, cellh), (0, 0, 0, 0))
    px = img.load()
    b0, tipu, spine, edge = k.blade
    h0, h1, hw, style = k.handle
    for y in range(cellh):
        v = -HALF + (y + 0.5) / cellh * 2 * HALF
        for x in range(w):
            u = (x + 0.5) / w * LEN
            vv = v + k.centre_curve(u)
            if not k.inside(u, vv):
                continue
            if u < 1.5:
                c = BRASS
            elif k.ferrule and k.ferrule[0] <= u <= k.ferrule[1]:
                c = (216, 96, 40) if style != "wood" else BRASS
            elif k.guard and 12.55 <= u <= 13.35:
                c = BRASS
            elif h0 <= u <= h1:
                c = grip_colour(style, u, vv, h1)
                if style == "wood" and u < h0 + 0.9:
                    c = BRASS
                if style in ("groove", "camo"):
                    for ru in (3.2, 8.4):
                        if abs(u - ru) < 0.12 and abs(vv) < 0.9:
                            c = (188, 152, 78)
                if style == "skeleton":
                    c = (26, 27, 30)
            else:
                skin = k.skin
                c = pattern(STEEL, "plain", u, vv, b0, tipu) if skin == "plain" else pattern(
                    STEEL, skin, u, vv, b0, tipu)
                # grind: the edge band is brighter, the spine falls into shadow
                fs, fe = spine(u), edge(u)
                frac = (vv - fe) / (fs - fe) if fs > fe else 0.5
                if skin in ("plain", "damascus", "slaughter"):
                    lift = -34 + 46 * clamp01((frac - 0.62) / 0.38) - 18 * clamp01((0.30 - frac) / 0.30)
                    # the grind line: a bright bevel with a shadow under it, like a real flat grind
                    lift += 26 * math.exp(-((frac - 0.36) ** 2) / 0.0009)
                    lift -= 22 * math.exp(-((frac - 0.31) ** 2) / 0.0006)
                    c = (max(0, min(255, c[0] + int(lift))), max(0, min(255, c[1] + int(lift))),
                         max(0, min(255, c[2] + int(lift))))
            # a crisp dark line around the silhouette so the knife reads against the hand
            if x < 1 or y < 1 or x >= w - 1 or y >= cellh - 1:
                lit = 0.62
            else:
                lit = 1.0
                for dx, dy in ((2, 0), (-2, 0), (0, 2), (0, -2)):
                    uu = (x + dx + 0.5) / w * LEN
                    vyy = -HALF + (y + dy + 0.5) / cellh * 2 * HALF + k.centre_curve(uu)
                    if not k.inside(uu, vyy):
                        lit = 0.60
                        break
            if lit < 1.0:
                c = (int(c[0] * lit), int(c[1] * lit), int(c[2] * lit))
            g = int(shade_grain(u, vv))
            c = (max(0, min(255, c[0] + g)), max(0, min(255, c[1] + g)), max(0, min(255, c[2] + g)))
            px[x, y] = (c[0], c[1], c[2], 255)
    return img


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
