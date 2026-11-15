#!/usr/bin/env python3
"""
The crate art: a hard equipment case for each crate, lit like a product shot.

Inspired by a black protective case (ribbed shell, draw latches, a medallion, a metal nameplate) and pushed further:
metal corner guards, a laurel and lightning medallion in the crate's own colour, a glowing accent line round the
front panel and along the lid seam, a carry handle on the lid.

Everything is built as maps (height, colour, metalness, gloss, glow) on a supersampled canvas and lit in one pass,
so ribs, bevels and engraving catch the light. Body and lid are saved apart so the lid can lift when a crate opens.

  python3 tools/crates/make_crates.py

writes textures/crates/<id>_body_<w>.png and <id>_lid_<w>.png for w in 512, 256, 128.
"""
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy import ndimage

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "assets", "zeus",
                   "textures", "crates")
CRATES = {
    "olympus": ("F5C04A", "OLYMPUS"),
    "tempest": ("5AA8FF", "TEMPEST"),
    "hades": ("FF4A4A", "HADES"),
    "nyx": ("B06AFF", "NYX"),
    "poseidon": ("2AD0D0", "POSEIDON"),
    "titan": ("FF8A2A", "TITAN"),
}
SS = 2
W, H = 512 * SS, 384 * SS
SEAM = int(0.27 * H)           # where the lid meets the body
TOP = int(0.055 * H)           # height of the lid's top face seen from slightly above
FONT_B = "/usr/share/fonts/opentype/inter/Inter-ExtraBold.otf"
FONT_M = "/usr/share/fonts/opentype/inter/Inter-SemiBold.otf"

YY, XX = np.mgrid[0:H, 0:W].astype(np.float32) + 0.5


def hexc(h):
    return np.array([int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4)], np.float32)


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def rrect_sdf(x0, y0, x1, y1, r):
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    hx, hy = (x1 - x0) / 2 - r, (y1 - y0) / 2 - r
    qx, qy = np.abs(XX - cx) - hx, np.abs(YY - cy) - hy
    return np.hypot(np.maximum(qx, 0), np.maximum(qy, 0)) + np.minimum(np.maximum(qx, qy), 0) - r


def circle_sdf(cx, cy, r):
    return np.hypot(XX - cx, YY - cy) - r


def cover(sdf):
    """Anti-aliased coverage of the inside of an sdf."""
    return np.clip(0.5 - sdf, 0, 1)


def poly_mask(pts):
    im = Image.new("L", (W, H), 0)
    ImageDraw.Draw(im).polygon([(float(x), float(y)) for x, y in pts], fill=255)
    return np.asarray(im, np.float32) / 255.0


def noise(seed, scale, octaves=3):
    rng = np.random.default_rng(seed)
    out = np.zeros((H, W), np.float32)
    amp = 1.0
    for o in range(octaves):
        s = max(1, int(scale / (2 ** o)))
        n = rng.standard_normal((H // s + 2, W // s + 2)).astype(np.float32)
        n = ndimage.zoom(n, s, order=3)[:H, :W]
        out += amp * n
        amp *= 0.5
    return out / np.abs(out).max()


class Case:
    def __init__(self):
        self.h = np.zeros((H, W), np.float32)
        self.alb = np.zeros((H, W, 3), np.float32)
        self.metal = np.zeros((H, W), np.float32)
        self.gloss = np.zeros((H, W), np.float32)
        self.glow = np.zeros((H, W, 3), np.float32)
        self.alpha = np.zeros((H, W), np.float32)

    def paint(self, m, alb=None, h=None, add=None, metal=None, gloss=None):
        m = np.clip(m, 0, 1)
        mm = m[..., None]
        if alb is not None:
            self.alb = self.alb * (1 - mm) + np.broadcast_to(alb, self.alb.shape) * mm
        if h is not None:
            self.h = self.h * (1 - m) + h * m
        if add is not None:
            self.h += add * m
        if metal is not None:
            self.metal = self.metal * (1 - m) + metal * m
        if gloss is not None:
            self.gloss = self.gloss * (1 - m) + gloss * m

    def emit(self, m, col, k=1.0):
        self.glow += np.clip(m, 0, 1)[..., None] * col * k


def bevel(sdf, width):
    """0 at the outline rising to 1 a width inside: a rounded edge profile."""
    return np.sqrt(smooth(0, 1, -sdf / width))


def engrave_text(case, text, font, size, cx, cy, depth, spacing=0, max_w=None):
    im = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(im)
    f = ImageFont.truetype(font, size)
    while max_w and size > 6:
        tw = sum(d.textlength(ch, font=f) for ch in text) + spacing * (len(text) - 1)
        if tw <= max_w:
            break
        size -= 1
        f = ImageFont.truetype(font, size)
    if spacing:
        widths = [d.textlength(ch, font=f) for ch in text]
        total = sum(widths) + spacing * (len(text) - 1)
        x = cx - total / 2
        for ch, w in zip(text, widths):
            d.text((x, cy), ch, font=f, fill=255, anchor="lm")
            x += w + spacing
    else:
        d.text((cx, cy), text, font=f, fill=255, anchor="mm")
    m = np.asarray(im, np.float32) / 255.0
    m = ndimage.gaussian_filter(m, 0.6)
    case.paint(m, add=-depth)
    return m


def build(crate_id):
    accent_hex, title = CRATES[crate_id]
    acc = hexc(accent_hex)
    c = Case()
    polymer = np.array([0.075, 0.08, 0.092], np.float32) * 0.9 + acc * 0.03
    steel = np.array([0.66, 0.68, 0.72], np.float32) * 0.92 + acc * 0.05
    dark_steel = np.array([0.30, 0.31, 0.34], np.float32)

    # ---------------- silhouette: the lid's top face (seen from a little above) and the front of the case
    front = rrect_sdf(0, TOP, W, H - 22 * SS, 30 * SS)
    top = poly_mask([(22 * SS, 0), (W - 22 * SS, 0), (W, TOP + 18 * SS), (0, TOP + 18 * SS)])
    top = np.maximum(top * (YY < TOP + 30 * SS), 0)
    feet = np.zeros((H, W), np.float32)
    for fx in (0.13, 0.87):
        feet = np.maximum(feet, cover(rrect_sdf(W * fx - 46 * SS, H - 34 * SS, W * fx + 46 * SS, H - 2 * SS, 10 * SS)))
    shell = np.maximum(cover(front), top)
    c.alpha = np.maximum(shell, feet)

    # polymer shell: a soft stipple and a rounded outer edge
    stip = noise(11, 3 * SS, 2)
    c.paint(shell, alb=polymer, h=0, metal=0.0, gloss=0.30)
    c.h += shell * (bevel(front, 16 * SS) * 10 * SS + stip * 0.35 * SS)
    c.paint(top, alb=polymer * 1.25, h=8 * SS, gloss=0.35)
    c.paint(feet * (1 - cover(front)), alb=np.array([0.035, 0.035, 0.04], np.float32), h=4 * SS, gloss=0.1)

    # a faint rim of the crate's colour along the outer edge, as if the case sat in its own light
    rim = np.exp(-((front + 2.5 * SS) / (1.6 * SS)) ** 2) * (YY > TOP + 20 * SS)
    c.emit(rim, acc, 0.22)
    # the seam between lid and body: a deep groove and a thin light in the crate's colour
    seam = np.exp(-((YY - SEAM) / (3.0 * SS)) ** 2) * cover(front + 12 * SS)
    c.h -= seam * 7 * SS
    seam_line = np.exp(-((YY - SEAM) / (0.9 * SS)) ** 2) * cover(front + 40 * SS)
    c.emit(seam_line, acc, 0.9)

    # ---------------- ribbed side bands on the body
    for x0, x1 in ((40 * SS, 150 * SS), (W - 150 * SS, W - 40 * SS)):
        band = rrect_sdf(x0, SEAM + 52 * SS, x1, H - 56 * SS, 14 * SS)
        rec = cover(band)
        c.h -= rec * 3 * SS
        ribs = 0.5 + 0.5 * np.cos((XX - x0) / (11 * SS) * 2 * math.pi)
        c.h += rec * smooth(0, 1, -band / (6 * SS)) * ribs ** 3 * 4 * SS
    # lid ribs across the front band, between the latches
    lband = rrect_sdf(150 * SS, TOP + 26 * SS, W - 150 * SS, SEAM - 18 * SS, 10 * SS)
    lrec = cover(lband)
    c.h -= lrec * 2 * SS
    lribs = 0.5 + 0.5 * np.cos((YY - TOP) / (9 * SS) * 2 * math.pi)
    c.h += lrec * smooth(0, 1, -lband / (5 * SS)) * lribs ** 3 * 3 * SS

    # ---------------- centre panel with a glowing border
    panel = rrect_sdf(165 * SS, SEAM + 34 * SS, W - 165 * SS, H - 52 * SS, 18 * SS)
    pc = cover(panel)
    c.h -= pc * 5 * SS * smooth(0, 1, -panel / (5 * SS))
    c.paint(pc, alb=polymer * 0.85, gloss=0.42)
    border = np.exp(-((panel + 7 * SS) / (1.0 * SS)) ** 2)
    c.emit(border, acc, 0.75)
    c.h += border * 1.0 * SS

    # ---------------- the medallion: knurled steel ring, enamel disc, laurel wreath and a lightning bolt
    mx, my, R = W / 2, SEAM + 96 * SS, 70 * SS
    em = R / (92 * SS)           # the emblem was drawn for a 92 px medallion
    ring = circle_sdf(mx, my, R)
    inner = circle_sdf(mx, my, R - 13 * SS)
    ring_m = cover(ring) * (1 - cover(inner))
    ang = np.arctan2(YY - my, XX - mx)
    knurl = 0.5 + 0.5 * np.cos(ang * 72)
    c.paint(cover(ring), alb=steel, h=10 * SS, metal=1.0, gloss=0.8)
    c.h += ring_m * (bevel(ring, 5 * SS) * 4 * SS + knurl * 1.2 * SS * smooth(0, 1, (-ring - 4 * SS) / (3 * SS)))
    enamel = acc * 0.16 + np.array([0.02, 0.025, 0.04], np.float32)
    c.paint(cover(inner), alb=enamel, h=7 * SS, metal=0.2, gloss=0.85)
    c.h -= cover(inner) * smooth(0, 1, -inner / (4 * SS)) * 2 * SS
    # laurel: leaves along two arcs, rising from the bottom on both sides
    leaves = Image.new("L", (W, H), 0)
    ld = ImageDraw.Draw(leaves)
    lr = R - 26 * SS
    for side in (-1, 1):
        for i in range(10):
            a = math.radians(100 + side * (12 + i * 14.5)) if side > 0 else math.radians(80 - (12 + i * 14.5))
            a = math.radians(90) + side * math.radians(14 + i * 14.5)
            px, py = mx + lr * math.cos(a), my + lr * math.sin(a)
            tang = a + side * math.pi / 2
            size = (11 - i * 0.45) * SS * em
            for off in (-1, 1):
                la = tang + off * 0.75 * side
                cx2 = px + math.cos(a) * off * 5 * SS * em + math.cos(tang) * 3 * SS * em
                cy2 = py + math.sin(a) * off * 5 * SS * em + math.sin(tang) * 3 * SS * em
                pts = []
                for k in range(16):
                    t = k / 15 * 2 * math.pi
                    ex, ey = math.cos(t) * size, math.sin(t) * size * 0.38
                    pts.append((cx2 + ex * math.cos(la) - ey * math.sin(la), cy2 + ex * math.sin(la) + ey * math.cos(la)))
                ld.polygon(pts, fill=255)
    stem = ImageDraw.Draw(leaves)
    stem.arc([mx - lr, my - lr, mx + lr, my + lr], 90 + 12, 90 + 12 + 140, fill=255, width=int(2.2 * SS))
    stem.arc([mx - lr, my - lr, mx + lr, my + lr], 90 - 12 - 140, 90 - 12, fill=255, width=int(2.2 * SS))
    lm = ndimage.gaussian_filter(np.asarray(leaves, np.float32) / 255.0, 0.7)
    c.paint(lm, alb=acc * 0.9 + 0.1, add=2.2 * SS, metal=0.6, gloss=0.9)
    c.emit(lm, acc, 0.55)
    # the bolt
    s = SS * em
    bolt = [(mx + 8 * s, my - 52 * s), (mx - 22 * s, my + 6 * s), (mx - 3 * s, my + 6 * s), (mx - 13 * s, my + 50 * s),
            (mx + 23 * s, my - 10 * s), (mx + 3 * s, my - 10 * s), (mx + 15 * s, my - 52 * s)]
    bm = ndimage.gaussian_filter(poly_mask(bolt), 0.7)
    bd = ndimage.distance_transform_edt(bm > 0.5) / SS
    gold = np.array([1.0, 0.86, 0.45], np.float32) * 0.55 + acc * 0.45
    c.paint(bm, alb=gold, add=np.clip(bd / 3.0, 0, 1) * 4 * SS, metal=1.0, gloss=0.95)
    c.emit(bm, gold * 0.5 + 0.5, 0.35)
    # small stars over the wreath
    for _, (dx, dy) in enumerate(((-34, -44), (34, -44), (0, -64))):
        st = cover(circle_sdf(mx + dx * SS * em, my + dy * SS * em, 2.6 * SS))
        c.paint(st, alb=np.ones(3, np.float32), add=1.5 * SS, metal=0.5, gloss=0.9)
        c.emit(st, acc * 0.5 + 0.5, 0.8)

    # ---------------- nameplate: brushed steel, four screws, engraved lettering
    py0, py1 = my + R + 9 * SS, H - 58 * SS
    plate = rrect_sdf(W / 2 - 80 * SS, py0, W / 2 + 80 * SS, py1, 5 * SS)
    brush = noise(23, 1, 1)
    brush = ndimage.gaussian_filter1d(np.random.default_rng(5).standard_normal((H, W)).astype(np.float32), 18 * SS, axis=1)
    brush /= np.abs(brush).max()
    c.paint(cover(plate), alb=steel * (0.93 + 0.07 * brush[..., None]), h=7 * SS, metal=1.0, gloss=0.55)
    c.h += cover(plate) * bevel(plate, 2.5 * SS) * 2 * SS
    for sx in (-71, 71):
        for sy in (0.28, 0.72):
            scr = circle_sdf(W / 2 + sx * SS, py0 + (py1 - py0) * sy, 3.2 * SS)
            c.paint(cover(scr), alb=dark_steel, add=1.5 * SS, metal=1.0, gloss=0.7)
            slot = cover(rrect_sdf(W / 2 + sx * SS - 2.6 * SS, py0 + (py1 - py0) * sy - 0.5 * SS,
                                   W / 2 + sx * SS + 2.6 * SS, py0 + (py1 - py0) * sy + 0.5 * SS, 0.4 * SS)) * cover(scr)
            c.h -= slot * 1.5 * SS
    ph = py1 - py0
    engrave_text(c, title + " CRATE", FONT_B, int(ph * 0.40), W / 2, py0 + ph * 0.40, 1.6 * SS, spacing=int(1.5 * SS), max_w=136 * SS)
    engrave_text(c, "ZEUS ARMORY", FONT_M, int(ph * 0.22), W / 2, py0 + ph * 0.77, 1.0 * SS, spacing=int(2.2 * SS), max_w=120 * SS)

    # ---------------- corner guards: brushed steel brackets with rivets
    g = 46 * SS
    for (cx0, cy0, sx, sy) in ((0, TOP, 1, 1), (W, TOP, -1, 1), (0, H - 22 * SS, 1, -1), (W, H - 22 * SS, -1, -1)):
        arm1 = rrect_sdf(min(cx0, cx0 + sx * g), min(cy0, cy0 + sy * 13 * SS), max(cx0, cx0 + sx * g), max(cy0, cy0 + sy * 13 * SS), 4 * SS)
        arm2 = rrect_sdf(min(cx0, cx0 + sx * 13 * SS), min(cy0, cy0 + sy * g), max(cx0, cx0 + sx * 13 * SS), max(cy0, cy0 + sy * g), 4 * SS)
        corner = np.minimum(arm1, arm2)
        cm = cover(corner) * cover(front)
        c.paint(cm, alb=steel * 0.95, h=c.h * 0 + 13 * SS, metal=1.0, gloss=0.75)
        c.h += cm * bevel(corner, 3 * SS) * 2.5 * SS
        for (rx, ry) in ((cx0 + sx * 33 * SS, cy0 + sy * 6.5 * SS), (cx0 + sx * 6.5 * SS, cy0 + sy * 33 * SS)):
            rv = circle_sdf(rx, ry, 3.6 * SS)
            c.paint(cover(rv) * cm, alb=steel * 1.05, add=bevel(rv, 3.6 * SS) * 2.5 * SS, metal=1.0, gloss=0.9)

    # ---------------- draw latches straddling the seam: catch on the lid, lever and base on the body
    for lx in (95 * SS, W - 95 * SS):
        catch = rrect_sdf(lx - 30 * SS, SEAM - 26 * SS, lx + 30 * SS, SEAM - 2 * SS, 4 * SS)
        c.paint(cover(catch), alb=dark_steel, h=12 * SS, metal=1.0, gloss=0.6)
        c.h += cover(catch) * bevel(catch, 2 * SS) * 2 * SS
        base = rrect_sdf(lx - 40 * SS, SEAM + 4 * SS, lx + 40 * SS, SEAM + 62 * SS, 7 * SS)
        c.paint(cover(base), alb=dark_steel * 1.1, h=11 * SS, metal=1.0, gloss=0.55)
        c.h += cover(base) * bevel(base, 3 * SS) * 2 * SS
        lever = rrect_sdf(lx - 27 * SS, SEAM - 16 * SS, lx + 27 * SS, SEAM + 56 * SS, 9 * SS)
        lc = cover(lever)
        c.paint(lc, alb=steel * 1.05, h=18 * SS, metal=1.0, gloss=0.92)
        c.h += lc * bevel(lever, 6 * SS) * 5 * SS
        # a raised spine down the lever and a grip notch
        spine = cover(rrect_sdf(lx - 3 * SS, SEAM - 8 * SS, lx + 3 * SS, SEAM + 34 * SS, 3 * SS))
        c.h += spine * 2.5 * SS
        notch = cover(rrect_sdf(lx - 18 * SS, SEAM + 42 * SS, lx + 18 * SS, SEAM + 48 * SS, 3 * SS))
        c.h -= notch * 3 * SS
        led = circle_sdf(lx, SEAM - 7 * SS, 3.6 * SS)
        c.paint(cover(led), alb=acc, add=1 * SS, gloss=0.9)
        c.emit(cover(led), acc * 0.4 + 0.6 * acc / max(acc.max(), 1e-3), 1.2)
        for sxx in (-31, 31):
            scr = circle_sdf(lx + sxx * SS, SEAM + 50 * SS, 3 * SS)
            c.paint(cover(scr), alb=steel, add=bevel(scr, 3 * SS) * 1.6 * SS, metal=1.0, gloss=0.8)

    # ---------------- carry handle on the lid's top face
    hnd = rrect_sdf(W / 2 - 100 * SS, 4 * SS, W / 2 + 100 * SS, TOP + 4 * SS, 9 * SS)
    c.paint(cover(hnd) * top, alb=polymer * 0.9, h=16 * SS, gloss=0.4)
    c.h += cover(hnd) * bevel(hnd, 6 * SS) * 5 * SS
    for hx in (-100, 100):
        mount = rrect_sdf(W / 2 + hx * SS - 16 * SS, 0, W / 2 + hx * SS + 16 * SS, TOP + 12 * SS, 5 * SS)
        c.paint(cover(mount) * np.maximum(top, cover(front)), alb=steel, h=17 * SS, metal=1.0, gloss=0.8)
        c.h += cover(mount) * bevel(mount, 3 * SS) * 2 * SS
    return c


def light(c):
    """Key light from the upper left, a soft studio environment for the metal, cavities darkened."""
    gy, gx = np.gradient(c.h)
    n = np.stack([-gx, -gy, np.ones_like(gx) * 1.0], -1)
    n /= np.linalg.norm(n, axis=-1, keepdims=True)
    L = np.array([-0.45, -0.62, 0.64], np.float32)
    L /= np.linalg.norm(L)
    V = np.array([0, 0, 1], np.float32)
    Hv = (L + V) / np.linalg.norm(L + V)
    ndl = np.clip((n * L).sum(-1), 0, 1)
    ndh = np.clip((n * Hv).sum(-1), 0, 1)
    up = -n[..., 1]
    cav = c.h - ndimage.gaussian_filter(c.h, 10 * SS)
    ao = np.clip(1 + cav / (14.0 * SS), 0.55, 1.12)
    # a reflection of the studio: a bright softbox above, a dark floor below, a rim strip on the right
    R = 2 * n[..., 2:3] * n - V
    env = 0.34 + 0.80 * smooth(-0.1, 0.65, -R[..., 1]) + 0.45 * np.exp(-((R[..., 0] - 0.55) / 0.12) ** 2)
    env *= 0.55 + 0.45 * (0.5 + 0.5 * np.sin(XX / W * 6.0 + YY / H * 2.0))   # a little variation along the surface
    shin = 20 + 220 * c.gloss
    spec = ndh ** shin[...]
    diel = c.alb * (0.30 + 0.12 * up[..., None] + 0.95 * ndl[..., None]) * ao[..., None] + (spec * c.gloss * 0.55)[..., None]
    metal = c.alb * (env[..., None] * 0.85 + 0.25 * ndl[..., None]) * ao[..., None] + (spec * (0.4 + c.gloss))[..., None] * (0.6 + 0.4 * c.alb)
    col = diel * (1 - c.metal[..., None]) + metal * c.metal[..., None]
    # glow: the lit parts themselves plus a soft bloom that stays on the case
    bloom = ndimage.gaussian_filter(c.glow, (6 * SS, 6 * SS, 0)) * 0.9 + ndimage.gaussian_filter(c.glow, (18 * SS, 18 * SS, 0)) * 0.5
    col = col + c.glow + bloom
    # a gentle top-to-bottom fall-off, like a light above the case
    col *= (1.08 - 0.22 * (YY / H))[..., None]
    col = col / (1 + 0.35 * np.clip(col - 0.8, 0, None))       # soft shoulder instead of hard clipping
    return np.clip(col, 0, 1)


def save(crate_id, col, alpha):
    os.makedirs(OUT, exist_ok=True)
    rgba = np.concatenate([col, alpha[..., None]], -1)
    # premultiply for resampling, then un-premultiply
    pm = rgba.copy()
    pm[..., :3] *= pm[..., 3:4]
    big = (pm * 255).round().astype(np.uint8)
    full = Image.fromarray(big, "RGBA")
    cut = SEAM
    parts = {"lid": full.crop((0, 0, W, cut)), "body": full.crop((0, cut, W, H))}
    for name, im in parts.items():
        for w in (512, 256, 128):
            h = round(im.height * w / W)
            small = np.asarray(im.resize((w, h), Image.LANCZOS)).astype(np.float32) / 255.0
            a = small[..., 3:4]
            rgb = np.where(a > 1e-3, small[..., :3] / np.maximum(a, 1e-3), 0)
            out = np.concatenate([np.clip(rgb, 0, 1), a], -1)
            # bleed colour into transparent edge texels so linear filtering stays clean
            m = out[..., 3] > 0.02
            idx = ndimage.distance_transform_edt(~m, return_distances=False, return_indices=True)
            out[..., :3] = out[idx[0], idx[1], :3]
            Image.fromarray((out * 255).round().astype(np.uint8), "RGBA").save(os.path.join(OUT, f"{crate_id}_{name}_{w}.png"), optimize=True)


def main():
    import sys
    ids = sys.argv[1:] or list(CRATES)
    for cid in ids:
        c = build(cid)
        col = light(c)
        save(cid, col, np.clip(c.alpha, 0, 1))
        print("crate", cid, flush=True)
    print("lid/body split at", SEAM / H, "of the height; aspect", H / W)


if __name__ == "__main__":
    main()
