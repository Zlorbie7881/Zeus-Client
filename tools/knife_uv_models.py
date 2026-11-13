#!/usr/bin/env python3
"""Author the eight Counter-Strike 2 knives as hand-drawn silhouettes with UV textures.

Why this is not a swept centreline any more: each part is defined by two *independent*
polylines, the spine and the cutting edge, sampled at the same station count.  Every
silhouette is therefore authored point by point - the karambit's claw hook, the M9's clip
point and saw teeth, the kukri's forward belly, the bowie's sweeping clip - instead of
being the symmetric taper a centreline sweep can produce.

The two polylines are zipped into one quad per station pair, so a part is a strip of
textured quads with UVs that follow the outline: u = 0 on the spine, u = 1 on the edge,
v = arc length along the part.  Nothing is stretched by station index, which is what made
the previous pass look like a cone with a rubber sheet pulled over it.

Model space: y up, x across, z thickness, grip base at y = 0.

Run: python3 tools/knife_uv_models.py
"""

import json
import math
import os

from PIL import Image, ImageChops, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_DIR = os.path.join(ROOT, "src/main/resources/assets/zeus/knives/models")
TEX_DIR = os.path.join(ROOT, "src/main/resources/assets/zeus/textures/knives")
TEX = 128

# atlas regions, uv in 0..16
FACE_UV = (0.0, 0.0, 12.0, 16.0)
RIM_UV = (12.0, 0.0, 16.0, 16.0)
BRASS_UV = (0.0, 0.0, 6.0, 6.0)
KNURL_UV = (6.0, 0.0, 10.0, 8.0)
WOOD_UV = (10.0, 0.0, 14.0, 8.0)
CORD_UV = (6.0, 8.0, 10.0, 16.0)
RIB_UV = (10.0, 8.0, 14.0, 16.0)

UV_BY_TEX = {"#steel": FACE_UV, "#brass": BRASS_UV, "#knurl": KNURL_UV,
             "#wood": WOOD_UV, "#cord": CORD_UV, "#rib": RIB_UV}


def tex_px(uv):
    k = TEX / 16.0
    return [int(round(v * k)) for v in uv]


# ------------------------------------------------------------------ texture sheets
def blade_sheet(pattern):
    """Blade art authored across u: dark spine, fuller, grind line, bright edge bevel."""
    img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    fx0, fy0, fx1, fy1 = tex_px(FACE_UV)
    w, h = fx1 - fx0, fy1 - fy0
    pix = img.load()
    # Broad, low frequency bands only: a blade is barely twenty pixels wide on screen, so
    # fine detail turns into moire rather than surface.  The shading is a smooth ramp with
    # one fuller channel and one bright bevel, exactly what reads as steel at this size.
    for row in range(h):
        v = row / (h - 1.0)
        for x in range(w):
            u = x / (w - 1.0)
            g = 150 + 40 * u
            g -= 34 * math.exp(-((u - 0.10) ** 2) / 0.006)
            g -= 30 * math.exp(-((u - 0.42) ** 2) / 0.020)
            g += 26 * math.exp(-((u - 0.58) ** 2) / 0.004)
            g += 46 * math.exp(-((u - 0.92) ** 2) / 0.010)
            if u < 0.035:
                g = 44 + 120 * u / 0.035
            if u > 0.968:
                g = 44 + 120 * (1.0 - u) / 0.032
            g += 3 * math.sin(v * 22.0 + u * 6.0)
            g = max(0, min(255, int(g)))
            pix[fx0 + x, fy0 + row] = (g, g + 3, g + 10, 255)

    if pattern == "damascus":
        d = ImageDraw.Draw(img)
        for i in range(9):
            pts = [(fx0 + k, fy0 + int(i * h / 9.0 + 5.0 * math.sin(k * 0.09 + i)))
                   for k in range(0, w, 3)]
            d.line(pts, fill=(96, 100, 112, 120), width=3)
            d.line([(x, y + 2) for x, y in pts], fill=(238, 244, 252, 90), width=2)
    elif pattern == "fade":
        d = ImageDraw.Draw(img)
        for row in range(h):
            a = int(150 * (1.0 - row / (h - 1.0)) ** 1.5)
            if a:
                d.line([(fx0, fy0 + row), (fx1, fy0 + row)], fill=(255, 255, 255, a))
    elif pattern == "case":
        d = ImageDraw.Draw(img)
        for i in range(18):
            x = fx0 + (i * 53) % w
            y = fy0 + (i * 37) % h
            d.ellipse([x - 11, y - 11, x + 11, y + 11], fill=(84, 100, 146, 110))
    elif pattern == "tiger":
        d = ImageDraw.Draw(img)
        for i in range(11):
            y = fy0 + int((i + 0.5) * h / 11.0)
            d.polygon([(fx0, y), (fx1, y + 18), (fx1, y + 30), (fx0, y + 12)], fill=(196, 150, 60, 120))
    elif pattern == "doppler":
        d = ImageDraw.Draw(img)
        for i in range(10):
            x = fx0 + i * w // 10
            d.line([(x, fy0), (x + 26, fy1)], fill=(60, 74, 150, 80), width=8)

    rx0, ry0, rx1, ry1 = tex_px(RIM_UV)
    for x in range(rx0, rx1):
        k = (x - rx0) / max(1, rx1 - rx0 - 1)
        g = 84 + int(104 * k)
        for y in range(ry0, ry1):
            pix[x, y] = (g, g + 3, g + 10, 255)
    return img


def trim_sheet():
    img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    bx0, by0, bx1, by1 = tex_px(BRASS_UV)
    for y in range(by0, by1):
        k = (y - by0) / max(1, by1 - by0 - 1)
        g = int(214 - 92 * k)
        d.line([(bx0, y), (bx1, y)], fill=(g, int(g * 0.74), int(g * 0.26), 255))
    d.line([(bx0, by0), (bx1, by0)], fill=(255, 242, 200, 255))
    d.line([(bx0, by1 - 2), (bx1, by1 - 2)], fill=(88, 58, 16, 255))

    kx0, ky0, kx1, ky1 = tex_px(KNURL_UV)
    d.rectangle([kx0, ky0, kx1 - 1, ky1 - 1], fill=(34, 35, 41, 255))
    for i in range(-32, 64, 4):
        d.line([(kx0 + i, ky0), (kx0 + i + 22, ky1)], fill=(58, 60, 68, 255), width=2)
        d.line([(kx0 + i, ky1), (kx0 + i + 22, ky0)], fill=(20, 21, 25, 255), width=2)

    wx0, wy0, wx1, wy1 = tex_px(WOOD_UV)
    d.rectangle([wx0, wy0, wx1 - 1, wy1 - 1], fill=(104, 66, 36, 255))
    for i in range(20):
        y = wy0 + int((wy1 - wy0) * i / 20.0)
        d.line([(wx0, y), (wx1, y + 2)], fill=(132, 86, 48, 255))
        d.line([(wx0, y + 2), (wx1, y + 4)], fill=(74, 46, 24, 255))

    cx0, cy0, cx1, cy1 = tex_px(CORD_UV)
    d.rectangle([cx0, cy0, cx1 - 1, cy1 - 1], fill=(40, 42, 48, 255))
    for i in range(-40, 80, 7):
        d.line([(cx0 + i, cy0), (cx0 + i + 26, cy1)], fill=(66, 68, 78, 255), width=4)
        d.line([(cx0 + i + 4, cy0), (cx0 + i + 30, cy1)], fill=(24, 25, 30, 255), width=1)

    rx0, ry0, rx1, ry1 = tex_px(RIB_UV)
    d.rectangle([rx0, ry0, rx1 - 1, ry1 - 1], fill=(30, 31, 37, 255))
    for y in range(ry0, ry1, 6):
        d.line([(rx0, y), (rx1, y)], fill=(56, 58, 66, 255), width=3)
        d.line([(rx0, y + 3), (rx1, y + 3)], fill=(18, 19, 23, 255), width=1)
    return img


# ------------------------------------------------------------------ strip builder
def strip(spine, edge, tex, thick, uv=None, v_from=0.0, v_to=1.0, caps=True, rims=(True, True)):
    """One solid between two equally sized polylines: front, back, two rims, two caps."""
    if uv is None:
        uv = UV_BY_TEX.get(tex, FACE_UV)
    u0, v0, u1, v1 = uv
    rim = RIM_UV if tex == "#steel" else uv
    ru0, rv0, ru1, rv1 = rim
    n = len(spine)
    assert n == len(edge)
    # arc length along the part, so v never stretches with station spacing
    acc = [0.0]
    for i in range(1, n):
        ax, ay = edge[i - 1]
        bx, by = edge[i]
        sx, sy = spine[i - 1]
        tx, ty = spine[i]
        acc.append(acc[-1] + 0.5 * (math.hypot(bx - ax, by - ay) + math.hypot(tx - sx, ty - sy)))
    total = acc[-1] or 1.0
    vs = [v_from + (v_to - v_from) * (a / total) for a in acc]
    tz_s, tz_e = (thick, thick) if isinstance(thick, (int, float)) else thick
    quads = []
    for i in range(n - 1):
        sl0, el0 = spine[i], edge[i]
        sl1, el1 = spine[i + 1], edge[i + 1]
        va, vb = vs[i], vs[i + 1]
        quads.append({"t": tex, "v": [sl0[0], sl0[1], -tz_s, el0[0], el0[1], -tz_e,
                                      el1[0], el1[1], -tz_e, sl1[0], sl1[1], -tz_s],
                      "uv": [u0, v0 + (v1 - v0) * va, u1, v0 + (v1 - v0) * vb]})
        quads.append({"t": tex, "v": [el0[0], el0[1], tz_e, sl0[0], sl0[1], tz_s,
                                      sl1[0], sl1[1], tz_s, el1[0], el1[1], tz_e],
                      "uv": [u0, v0 + (v1 - v0) * va, u1, v0 + (v1 - v0) * vb]})
        if rims[0]:
            quads.append({"t": "#rim", "v": [sl0[0], sl0[1], -tz_s, sl1[0], sl1[1], -tz_s,
                                             sl1[0], sl1[1], tz_s, sl0[0], sl0[1], tz_s],
                          "uv": [ru0, rv0 + (rv1 - rv0) * va, ru1, rv0 + (rv1 - rv0) * vb]})
        if rims[1]:
            quads.append({"t": "#rim", "v": [el0[0], el0[1], -tz_e, el0[0], el0[1], tz_e,
                                             el1[0], el1[1], tz_e, el1[0], el1[1], -tz_e],
                          "uv": [ru0, rv0 + (rv1 - rv0) * va, ru1, rv0 + (rv1 - rv0) * vb]})
    if caps:
        s0, e0 = spine[0], edge[0]
        quads.append({"t": tex, "v": [s0[0], s0[1], -tz_s, e0[0], e0[1], -tz_e,
                                      e0[0], e0[1], tz_e, s0[0], s0[1], tz_s],
                      "uv": [u0, v0, u1, v0 + 0.03]})
        s1, e1 = spine[-1], edge[-1]
        quads.append({"t": tex, "v": [e1[0], e1[1], -tz_e, s1[0], s1[1], -tz_s,
                                      s1[0], s1[1], tz_s, e1[0], e1[1], tz_e],
                      "uv": [u0, v1 - 0.03, u1, v1]})
    return quads


def blade(spine, edge, thick, bevel=0.55, smooth=None):
    """Flat spine side plus an angled grind bevel down to the edge."""
    if len(edge) != len(spine):
        edge = resample(edge, len(spine))
    # the grind line follows the smooth spine, so saw teeth stay on the spine rim instead
    # of corrugating the whole face
    # collapse the last stations onto the tip so a mismatched pair cannot leave a spur
    tip = ((spine[-1][0] + edge[-1][0]) * 0.5, max(spine[-1][1], edge[-1][1]))
    spine = list(spine)
    edge = list(edge)
    for k in (-1, -2):
        spine[k] = tip
        edge[k] = tip
    if smooth is not None:
        smooth = list(smooth)
        smooth[-1] = tip
        smooth[-2] = ((smooth[-2][0] + tip[0]) * 0.5, (smooth[-2][1] + tip[1]) * 0.5)
    base = spine if smooth is None else smooth
    mid = [((base[i][0] + edge[i][0]) * 0.5, (base[i][1] + edge[i][1]) * 0.5)
           for i in range(len(spine))]
    # flat takes the spine half of the blade art, the bevel the polished edge half, so the
    # two faces read as one continuous sheet of steel with a grind line between them
    out = strip(spine, mid, "#steel", (thick, thick), uv=(0.0, 0.0, 7.5, 16.0), rims=(True, False))
    out += strip(mid, edge, "#steel", (thick, thick * bevel), uv=(7.5, 0.0, 12.0, 16.0),
                 rims=(False, True))
    return out


def rectangle(x0, x1, y0, y1, tex, thick, uv=None, v_from=0.0, v_to=1.0):
    """Axis aligned block: used for guards, bolsters and pommels."""
    spine = [(x0, y0), (x0, y1)]
    edge = [(x1, y0), (x1, y1)]
    return strip(spine, edge, tex, thick, uv=uv, v_from=v_from, v_to=v_to)


def ring(cx, cy, r_in, r_out, thick, tex, uv, seg=14):
    quads = []
    u0, v0, u1, v1 = uv
    for i in range(seg):
        a0 = 2 * math.pi * i / seg
        a1 = 2 * math.pi * (i + 1) / seg
        pi0 = (cx + r_in * math.cos(a0), cy + r_in * math.sin(a0))
        po0 = (cx + r_out * math.cos(a0), cy + r_out * math.sin(a0))
        pi1 = (cx + r_in * math.cos(a1), cy + r_in * math.sin(a1))
        po1 = (cx + r_out * math.cos(a1), cy + r_out * math.sin(a1))
        ui = u0 + (u1 - u0) * i / seg
        ui2 = u0 + (u1 - u0) * (i + 1) / seg
        for v in ((pi0, po0, po1, pi1), (po0, pi0, pi1, po1)):
            quads.append({"t": tex, "v": [v[0][0], v[0][1], -thick, v[1][0], v[1][1], -thick,
                                          v[2][0], v[2][1], -thick, v[3][0], v[3][1], -thick],
                          "uv": [ui, v0, ui2, v1]})
            quads.append({"t": tex, "v": [v[1][0], v[1][1], thick, v[0][0], v[0][1], thick,
                                          v[3][0], v[3][1], thick, v[2][0], v[2][1], thick],
                          "uv": [ui, v0, ui2, v1]})
        quads.append({"t": tex, "v": [po0[0], po0[1], -thick, po1[0], po1[1], -thick,
                                      po1[0], po1[1], thick, po0[0], po0[1], thick],
                      "uv": [ui, v0, ui2, v1]})
        quads.append({"t": tex, "v": [pi0[0], pi0[1], thick, pi1[0], pi1[1], thick,
                                      pi1[0], pi1[1], -thick, pi0[0], pi0[1], -thick],
                      "uv": [ui, v0, ui2, v1]})
    return quads


def grip(y0, y1, tex, thick, halfw=1.4, swell=0.22, crown=1.30, skew=0.0, grooves=3, rivets=2):
    """A contoured handle: pommel crown, mid swell, finger grooves on the front edge."""
    parts = []
    n = 11
    spine, edge = [], []
    for i in range(n):
        t = i / (n - 1.0)
        y = y0 + (y1 - y0) * t
        w = halfw + swell * math.sin(math.pi * t)
        if t < 0.10:
            w = crown * 0.92 + (halfw - crown * 0.92) * (t / 0.10)
        if t > 0.93:
            w = halfw * 0.90
        x = skew * t
        g = 0.11 * abs(math.sin(math.pi * grooves * (t - 0.12))) if 0.12 < t < 0.92 else 0.0
        spine.append((x - w, y))
        edge.append((x + w + g, y))
    parts.append(strip(spine, edge, tex, thick))
    for j in range(rivets):
        rx = skew * (0.5 + 0.28 * j) - 0.08
        ry = y0 + (y1 - y0) * (0.52 + 0.26 * j)
        parts.append(rectangle(rx, rx + 0.26, ry - 0.15, ry + 0.15, "#brass", thick * 0.82))
    return parts


def resample(points, n):
    """Even arc length resample, so control points can be placed where the shape needs them."""
    acc = [0.0]
    for i in range(1, len(points)):
        acc.append(acc[-1] + math.hypot(points[i][0] - points[i - 1][0],
                                        points[i][1] - points[i - 1][1]))
    total = acc[-1] or 1.0
    out = []
    for k in range(n):
        target = total * k / (n - 1.0)
        i = 1
        while i < len(acc) - 1 and acc[i] < target:
            i += 1
        span = acc[i] - acc[i - 1] or 1.0
        f = (target - acc[i - 1]) / span
        x = points[i - 1][0] + (points[i][0] - points[i - 1][0]) * f
        y = points[i - 1][1] + (points[i][1] - points[i - 1][1]) * f
        out.append((x, y))
    return out


def teeth(points, count, depth, start=0.0, end=1.0):
    """Saw teeth cut into the spine: a square notch per wavelength, never a noisy edge."""
    n = len(points)
    out = []
    for i, (x, y) in enumerate(points):
        t = i / (n - 1.0)
        if start <= t <= end:
            k = (t - start) / max(1e-6, end - start) * count
            if (k % 1.0) < 0.5:
                out.append((x - depth, y))
                continue
        out.append((x, y))
    return out


KNIVES = {}


def flatten(node):
    if isinstance(node, dict):
        return [node]
    if isinstance(node, (list, tuple)):
        out = []
        for child in node:
            out.extend(flatten(child))
        return out
    return []


def knife(name, parts, ident=None):
    quads = flatten(parts)
    textures = {"steel": "zeus:knives/blade_plain", "trim": "zeus:knives/trim",
                "rim": "zeus:knives/blade_plain"}
    for key in ("#knurl", "#wood", "#cord", "#rib"):
        textures[key[1:]] = "zeus:knives/trim"
    KNIVES[ident or name.lower().replace(" ", "_")] = {
        "name": name, "textures": textures, "quads": quads}


# ------------------------------------------------------------------ the eight knives
def karambit():
    parts = []
    # contoured grip with three finger grooves on the front edge
    spine = [(-1.35, 0.6), (-1.5, 2.0), (-1.45, 3.6), (-1.55, 5.2), (-1.4, 6.8), (-1.5, 8.2), (-1.25, 9.4)]
    edge = [(1.30, 0.6), (1.15, 1.6), (1.45, 2.4), (1.05, 3.2), (1.40, 4.1), (1.00, 4.9),
            (1.35, 5.9), (1.05, 6.8), (1.35, 7.8), (1.15, 9.4)]
    parts.extend(grip(0.6, 9.4, "#knurl", 0.62, halfw=1.35, swell=0.20, skew=0.06))
    parts.append(rectangle(-1.45, 1.45, 9.4, 10.1, "#brass", 0.68))
    # claw: spine arcs up and forward, edge follows inside, tip hooks down
    spine = [(-1.30, 10.2), (0.40, 12.6), (2.60, 14.8), (4.90, 16.2), (6.90, 16.4), (8.30, 15.2), (9.00, 13.2)]
    edge = [(1.30, 10.2), (0.60, 11.4), (1.60, 13.4), (3.60, 15.0), (5.60, 15.6), (7.40, 14.8), (9.00, 13.2)]
    parts.extend(blade(resample(spine, 14), resample(edge, 14), 0.46))
    parts.append(ring(-0.05, -0.95, 1.00, 1.90, 0.52, "#brass", BRASS_UV))
    knife("Karambit", parts)


def m9():
    parts = []
    spine = [(-1.55, 0.7), (-1.45, 2.4), (-1.60, 4.2), (-1.45, 6.0), (-1.60, 7.8), (-1.45, 9.6), (-1.50, 11.3)]
    edge = [(1.45, 0.7), (1.30, 2.4), (1.45, 4.2), (1.30, 6.0), (1.45, 7.8), (1.30, 9.6), (1.35, 11.3)]
    parts.extend(grip(0.7, 11.3, "#rib", 0.58, halfw=1.50, skew=-0.06))
    parts.append(rectangle(-2.35, 2.35, 11.3, 12.3, "#brass", 0.75))
    parts.append(ring(0.85, 11.8, 0.5, 1.25, 0.75, "#brass", BRASS_UV, seg=10))
    # clip point blade with saw teeth on the spine
    spine = [(-1.15, 12.3), (-2.15, 14.4), (-2.25, 17.4), (-2.25, 21.2), (-2.05, 23.2),
             (-1.45, 25.4), (-0.55, 27.0), (0.45, 27.8)]
    edge = [(1.15, 12.3), (2.15, 14.4), (2.30, 17.4), (2.30, 21.4), (2.15, 23.6),
            (1.75, 25.6), (1.05, 27.2), (0.45, 27.8)]
    spine_all = resample(spine, 56)
    parts.extend(blade(teeth(spine_all, 6, 0.17, 0.30, 0.64), edge, 0.40, smooth=spine_all))
    knife("M9 Bayonet", parts, "m9_bayonet")


def huntsman():
    parts = []
    spine = [(-1.50, 0.7), (-1.40, 2.4), (-1.55, 4.1), (-1.40, 5.8), (-1.55, 7.5), (-1.45, 10.6)]
    edge = [(1.40, 0.7), (1.25, 2.4), (1.40, 4.1), (1.25, 5.8), (1.40, 7.5), (1.30, 10.6)]
    parts.extend(grip(0.7, 10.6, "#cord", 0.60, halfw=1.45, swell=0.26))
    parts.append(rectangle(-1.9, 1.9, 10.6, 11.6, "#brass", 0.72))
    spine = [(-1.15, 11.6), (-2.10, 13.6), (-2.20, 16.6), (-2.20, 19.6), (-2.10, 22.0),
             (-1.75, 25.0)]
    edge = [(1.15, 11.6), (2.10, 13.6), (2.25, 16.6), (2.30, 19.8), (2.10, 22.6),
            (1.55, 24.6), (0.75, 26.2)]
    spine_all = resample(spine, 48)
    spine = teeth(spine_all, 5, 0.22, 0.12, 0.42)
    edge = resample(edge, 34)
    # make the two polylines end at the same tip
    edge[-1] = spine[-1] = ((spine[-1][0] + edge[-1][0]) * 0.5, max(spine[-1][1], edge[-1][1]) + 0.25)
    parts.extend(blade(spine, edge, 0.38, smooth=spine_all))
    knife("Huntsman", parts)


def bowie():
    parts = []
    spine = [(-1.70, 0.8), (-1.55, 2.6), (-1.75, 4.6), (-1.55, 6.6), (-1.75, 8.6), (-1.60, 12.2)]
    edge = [(1.55, 0.8), (1.40, 2.6), (1.60, 4.6), (1.40, 6.6), (1.60, 8.6), (1.45, 12.2)]
    parts.extend(grip(0.8, 12.2, "#wood", 0.66, halfw=1.65, swell=0.30))
    # curved crossguard: three blocks stepping outwards
    parts.append(rectangle(-2.55, 2.55, 12.2, 13.0, "#brass", 0.55))
    parts.append(rectangle(-1.85, 1.85, 12.9, 13.5, "#brass", 0.72))
    spine = [(-1.20, 13.5), (-2.10, 15.8), (-2.20, 19.0), (-2.20, 22.4), (-1.90, 25.6)]
    edge = [(1.20, 13.5), (2.35, 15.8), (2.55, 19.0), (2.55, 22.6), (1.95, 25.8), (0.90, 28.4)]
    spine = resample(spine, 26)
    edge = resample(edge, 26)
    edge[-1] = spine[-1] = (0.35, 29.0)
    parts.extend(blade(spine, edge, 0.42))
    knife("Bowie", parts)


def butterfly():
    parts = []
    for side in (-1, 1):
        # two handle channels, split apart, with vent slots between them
        spine = [(side * 0.75, 0.4), (side * 0.85, 2.4), (side * 0.75, 4.4), (side * 0.85, 6.4), (side * 0.75, 8.6)]
        edge = [(side * 1.95, 0.4), (side * 2.05, 2.4), (side * 1.95, 4.4), (side * 2.05, 6.4), (side * 1.95, 8.6)]
        parts.append(strip(resample(spine, 10), resample(edge, 10), "#knurl", 0.44))
        parts.append(rectangle(side * 0.6, side * 2.1, 8.6, 9.5, "#brass", 0.5))
    spine = [(-0.95, 9.5), (-1.35, 12.0), (-1.30, 15.0), (-1.10, 18.0), (-0.75, 20.6), (-0.10, 22.6)]
    edge = [(0.95, 9.5), (1.35, 12.0), (1.45, 15.0), (1.40, 18.0), (1.25, 20.4), (0.85, 22.6)]
    spine = resample(spine, 16)
    edge = resample(edge, 16)
    edge[-1] = spine[-1] = (0.30, 23.6)
    parts.extend(blade(spine, edge, 0.26))
    knife("Butterfly knife", parts, "butterfly_knife")


def skeleton():
    parts = []
    for side in (-1, 1):
        parts.append(rectangle(side * 0.65, side * 1.45, 0.5, 5.4, "#cord", 0.34))
    parts.append(rectangle(-1.55, 1.55, 0.2, 0.95, "#brass", 0.42))
    parts.append(ring(0.0, 3.2, 1.0, 1.7, 0.30, "#brass", BRASS_UV))
    # double edged dagger: symmetric by design, this is the throwing knife
    spine = [(-0.55, 5.8), (-1.10, 8.0), (-1.25, 11.0), (-1.15, 14.0), (-0.85, 17.0), (-0.40, 19.6)]
    edge = [(0.55, 5.8), (1.10, 8.0), (1.25, 11.0), (1.15, 14.0), (0.85, 17.0), (0.40, 19.6)]
    spine = resample(spine, 18)
    edge = resample(edge, 18)
    edge[-1] = spine[-1] = (0.0, 21.6)
    parts.extend(blade(spine, edge, 0.34))
    knife("Skeleton", parts)


def kukri():
    parts = []
    spine = [(-1.45, 0.7), (-1.35, 2.4), (-1.50, 4.1), (-1.35, 5.8), (-1.50, 7.4), (-1.40, 10.4)]
    edge = [(1.35, 0.7), (1.20, 2.4), (1.35, 4.1), (1.20, 5.8), (1.35, 7.4), (1.25, 10.4)]
    parts.extend(grip(0.7, 10.4, "#wood", 0.60, halfw=1.45, swell=0.26, skew=0.12))
    parts.append(rectangle(-1.55, 1.55, 10.4, 11.2, "#brass", 0.66))
    # forward swept kukri belly: spine stays near vertical, edge bulges far forward
    spine = [(-1.15, 11.2), (-1.35, 13.6), (-1.20, 16.2), (-0.70, 18.8), (0.30, 21.2), (1.70, 23.2), (3.05, 24.6)]
    edge = [(1.15, 11.2), (2.15, 13.4), (3.40, 15.8), (4.45, 18.4), (4.85, 21.0), (4.55, 23.2), (3.60, 25.0)]
    spine = resample(spine, 22)
    edge = resample(edge, 22)
    edge[-1] = spine[-1] = (3.30, 26.4)
    parts.extend(blade(spine, edge, 0.40))
    knife("Kukri", parts)


def stiletto():
    parts = []
    spine = [(-1.05, 0.6), (-0.95, 2.4), (-1.05, 4.2), (-0.95, 6.0), (-1.05, 7.8), (-0.95, 10.0), (-1.00, 11.6)]
    edge = [(1.00, 0.6), (0.90, 2.4), (1.00, 4.2), (0.90, 6.0), (1.00, 7.8), (0.90, 10.0), (0.95, 11.6)]
    parts.extend(grip(0.6, 11.6, "#knurl", 0.44, halfw=0.95, crown=1.05, swell=0.14))
    # swing guard / bolster
    parts.append(rectangle(-1.65, 1.65, 11.6, 12.3, "#brass", 0.52))
    parts.append(rectangle(-0.95, 0.95, 12.2, 13.1, "#brass", 0.60))
    spine = [(-0.75, 13.1), (-0.80, 16.0), (-0.72, 19.0), (-0.55, 22.0), (-0.35, 24.8), (-0.12, 27.0)]
    edge = [(0.75, 13.1), (0.80, 16.0), (0.72, 19.0), (0.55, 22.0), (0.35, 24.8), (0.12, 27.0)]
    spine = resample(spine, 20)
    edge = resample(edge, 20)
    edge[-1] = spine[-1] = (0.0, 28.8)
    parts.extend(blade(spine, edge, 0.20))
    knife("Stiletto", parts)


BUILDERS = [karambit, m9, huntsman, bowie, butterfly, skeleton, kukri, stiletto]

PATTERNS = {"plain": None, "damascus": "damascus", "fade": "fade",
            "case": "case", "tiger": "tiger", "doppler": "doppler"}

FINISH_TINTS = {
    "Stainless": (208, 214, 224),
    "Damascus": (198, 204, 214),
    "Fade": (240, 176, 150),
    "Case hardened": (214, 196, 156),
    "Tiger Tooth": (236, 190, 110),
    "Doppler": (150, 170, 236),
    "Crimson": (226, 140, 140),
    "Night": (150, 154, 166),
}


# ------------------------------------------------------------------ preview
def face_shade(v):
    a, b, c = v[0:3], v[3:6], v[6:9]
    u = [b[i] - a[i] for i in range(3)]
    w = [c[i] - a[i] for i in range(3)]
    n = [u[1] * w[2] - u[2] * w[1], u[2] * w[0] - u[0] * w[2], u[0] * w[1] - u[1] * w[0]]
    ln = math.sqrt(sum(t * t for t in n)) or 1.0
    n = [t / ln for t in n]
    if abs(n[2]) > 0.7:
        return 1.0 if n[2] < 0 else 0.74
    if abs(n[1]) > 0.7:
        return 0.92 if n[1] > 0 else 0.62
    return 0.84


def render(model, sheets, tint, size, ax=-8.0, ay=-18.0):
    def proj(p):
        x, y, z = p
        cx, sx = math.cos(math.radians(ax)), math.sin(math.radians(ax))
        cy, sy = math.cos(math.radians(ay)), math.sin(math.radians(ay))
        x, z = x * cy + z * sy, -x * sy + z * cy
        y, z = y * cx - z * sx, y * sx + z * cx
        return x, y, z

    quads = []
    for q in model["quads"]:
        v = q["v"]
        pts = [proj((v[i], v[i + 1], v[i + 2])) for i in range(0, 12, 3)]
        quads.append((sum(p[2] for p in pts) / 4.0, pts, q["uv"], q["t"], face_shade(v)))
    allp = [p for _, pts, _, _, _ in quads for p in pts]
    xs = [p[0] for p in allp]
    ys = [p[1] for p in allp]
    span = max(max(xs) - min(xs), max(ys) - min(ys)) or 1.0
    S = size * 0.86 / span
    ox = size / 2 - (min(xs) + max(xs)) / 2 * S
    oy = size / 2 + (min(ys) + max(ys)) / 2 * S

    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    quads.sort(key=lambda q: q[0])
    for _, pts, uv, tex, sh in quads:
        sheet = sheets.get(tex) or sheets["#steel"]
        tw, th = sheet.size
        u0, v0, u1, v1 = [k * tw / 16.0 for k in uv]
        scr = [(ox + p[0] * S, oy - p[1] * S) for p in pts]
        c0, c1, c3 = scr[0], scr[1], scr[3]
        ex = (c1[0] - c0[0], c1[1] - c0[1])
        ey = (c3[0] - c0[0], c3[1] - c0[1])
        det = ex[0] * ey[1] - ex[1] * ey[0]
        if abs(det) < 1e-7:
            continue
        ia = (ey[1] / det, -ey[0] / det)
        ib = (-ex[1] / det, ex[0] / det)
        su, sv = (u1 - u0), (v1 - v0)
        A, B = ia[0] * su, ia[1] * su
        C = u0 - c0[0] * A - c0[1] * B
        D, E = ib[0] * sv, ib[1] * sv
        F = v0 - c0[0] * D - c0[1] * E
        warped = sheet.transform((size, size), Image.AFFINE, (A, B, C, D, E, F),         resample=Image.BILINEAR)
        if tex == "#steel":
            r, g, b, a = warped.split()
            warped = Image.merge("RGBA", (r.point(lambda q: q * tint[0] // 255),
                                          g.point(lambda q: q * tint[1] // 255),
                                          b.point(lambda q: q * tint[2] // 255), a))
        if sh < 1.0:
            dark = Image.new("RGBA", (size, size), (0, 0, 0, int(255 * (1 - sh))))
            warped = Image.composite(dark, warped, warped.split()[3])
        clip = Image.new("L", (size, size), 0)
        ImageDraw.Draw(clip).polygon(scr, fill=255)
        warped.putalpha(ImageChops.multiply(warped.split()[3], clip))
        canvas.alpha_composite(warped)
    return canvas


def main():
    os.makedirs(MODEL_DIR, exist_ok=True)
    os.makedirs(TEX_DIR, exist_ok=True)
    for build in BUILDERS:
        build()
    for ident, model in KNIVES.items():
        with open(os.path.join(MODEL_DIR, ident + ".json"), "w", encoding="utf-8") as fh:
            json.dump(model, fh, separators=(",", ":"))
    with open(os.path.join(MODEL_DIR, "index.txt"), "w", encoding="utf-8") as fh:
        fh.write("# UV mapped knife models, one id per line\n")
        for ident in KNIVES:
            fh.write(ident + "\n")
    for name in PATTERNS:
        blade_sheet(name).save(os.path.join(TEX_DIR, "blade_%s.png" % name))
    trim_sheet().save(os.path.join(TEX_DIR, "trim.png"))
    print("wrote %d models, %d sheets" % (len(KNIVES), len(PATTERNS) + 1))

    cell = 320
    sheet = Image.new("RGBA", (cell * 4, cell * 2), (12, 20, 38, 255))
    d = ImageDraw.Draw(sheet)
    picks = [("plain", "Stainless"), ("damascus", "Damascus"), ("fade", "Fade"),
             ("case", "Case hardened"), ("tiger", "Tiger Tooth"), ("plain", "Night"),
             ("doppler", "Doppler"), ("fade", "Crimson")]
    cache = {}
    for i, (ident, model) in enumerate(KNIVES.items()):
        pat, fin = picks[i % len(picks)]
        sheet_img = cache.get(pat) or Image.open(os.path.join(TEX_DIR, "blade_%s.png" % pat)).convert("RGBA")
        cache[pat] = sheet_img
        trim = cache.get("trim") or Image.open(os.path.join(TEX_DIR, "trim.png")).convert("RGBA")
        cache["trim"] = trim
        sheets = {"#steel": sheet_img, "#rim": sheet_img, "#trim": trim, "#brass": trim,
                  "#knurl": trim, "#wood": trim, "#cord": trim, "#rib": trim}
        img = render(model, sheets, FINISH_TINTS[fin], cell)
        x, y = (i % 4) * cell, (i // 4) * cell
        sheet.alpha_composite(img, (x, y))
        d.text((x + 10, y + cell - 20), model["name"], fill=(206, 216, 234))
        d.text((x + 10, y + cell - 32), fin, fill=(120, 134, 158))
    os.makedirs(os.path.join(ROOT, "artifacts"), exist_ok=True)
    sheet.convert("RGB").save(os.path.join(ROOT, "artifacts", "cs2_knives.png"))
    print("preview -> artifacts/cs2_knives.png")


if __name__ == "__main__":
    main()
