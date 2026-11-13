#!/usr/bin/env python3
"""Author the eight Counter-Strike 2 knives as UV mapped quads plus their textures.

Format produced (per knife), ``assets/zeus/knives/models/<id>.json``::

    {
      "name": "M9 Bayonet",
      "textures": {"steel": "zeus:knives/blade_plain", "trim": "zeus:knives/trim"},
      "quads": [
        {"t": "#steel", "v": [x,y,z, x,y,z, x,y,z, x,y,z], "uv": [u0,v0,u1,v1]},
        ...
      ]
    }

Every knife is built from *lofts*: a centre line with a spine offset and an edge offset.
That gives asymmetric blades (one cutting edge, one spine), curved silhouettes (karambit,
kukri) and saw teeth (spine serrations) directly in geometry, and every quad carries a uv
rectangle into a shared 128x128 texture sheet, so the surface is real texture, not a tint.

Sheets (shared by all knives, same layout):
    blade_plain/damascus/fade/case/tiger/doppler.png   128x128, greyscale art + rim strip
    trim.png                                           128x128, brass + grip patterns

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

# atlas regions in uv units (0..16 over the sheet)
FACE_UV = (0.0, 0.0, 12.0, 16.0)      # blade / handle face art, 96x128 texels
RIM_UV = (12.0, 0.0, 16.0, 16.0)      # thickness strip, 32x128
BRASS_UV = (0.0, 0.0, 8.0, 8.0)       # trim sheet, top left 64x64
KNURL_UV = (8.0, 0.0, 12.0, 6.0)
WOOD_UV = (8.0, 6.0, 12.0, 12.0)
CORD_UV = (12.0, 0.0, 16.0, 6.0)
RIB_UV = (12.0, 6.0, 16.0, 12.0)


def tex_px(uv):
    k = TEX / 16.0
    return [int(round(v * k)) for v in uv]


# ------------------------------------------------------------------ texture sheets
PATTERNS = {
    "plain": None,
    "damascus": "swirl",
    "fade": "fade",
    "case": "case",
    "tiger": "tiger",
    "doppler": "doppler",
}


def blade_sheet(pattern):
    """Greyscale blade art: brushed grain, spine shade, fuller, bright edge bevel."""
    img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    fx0, fy0, fx1, fy1 = tex_px(FACE_UV)
    w, h = fx1 - fx0, fy1 - fy0
    pix = img.load()
    for row in range(h):
        v = row / (h - 1.0)
        for x in range(w):
            u = x / (w - 1.0)
            base = 168 + int(26 * math.sin(v * 41.0 + u * 7.0)) + int(8 * math.sin(v * 113.0))
            # spine side darker, cutting edge brighter
            base += int(26 * (u - 0.5))
            # ground bevel along the cutting edge
            if u > 0.80:
                base = 224 + int(26 * (u - 0.80) / 0.20)
            elif u > 0.74:
                base = 118                      # the grind line
            # fuller: recessed channel with a lit lower lip
            if 0.30 < u < 0.58:
                base = 126 + int(14 * (u - 0.30) / 0.28)
                if 0.545 < u < 0.58:
                    base = 226
                elif 0.30 < u < 0.325:
                    base = 84
            # brushed grain
            base += int(6 * math.sin(u * 96 + v * 3)) + int(4 * math.sin(v * 220))
            # silhouette outline, so quads read as separate parts
            if u < 0.012 or u > 0.988:
                base = 44
            base = max(0, min(255, base))
            pix[fx0 + x, fy0 + row] = (base, base + 3, base + 10, 255)

    pat = PATTERNS.get(pattern)
    if pat == "swirl":
        d = ImageDraw.Draw(img)
        for i in range(22):
            pts = []
            for k in range(0, w + 1, 2):
                y = int(8 + i * (h - 16) / 22.0 + 3.2 * math.sin(k * 0.16 + i * 0.9))
                pts.append((fx0 + k, fy0 + y))
            d.line(pts, fill=(96, 100, 108, 60))
            d.line([(x, y + 1) for x, y in pts], fill=(232, 238, 248, 46))
    elif pat == "fade":
        d = ImageDraw.Draw(img)
        for row in range(h):
            v = row / (h - 1.0)
            a = int(120 * (1.0 - v) ** 1.4)
            if a > 0:
                d.line([(fx0, fy0 + row), (fx1, fy0 + row)], fill=(255, 255, 255, a))
    elif pat == "case":
        d = ImageDraw.Draw(img)
        for i in range(26):
            x0 = (i * 37) % w
            d.ellipse([fx0 + x0 - 9, fy0 + (i * 23) % h - 9, fx0 + x0 + 9, fy0 + (i * 23) % h + 9],
                      fill=(84, 96, 128, 70))
    elif pat == "tiger":
        d = ImageDraw.Draw(img)
        for i in range(12):
            y0 = fx0 + 0
            d.polygon([(fx0 + w * 0.05, fy0 + (i * 11) % h),
                       (fx0 + w * 0.95, fy0 + (i * 11 + 24) % h),
                       (fx0 + w * 0.95, fy0 + (i * 11 + 34) % h),
                       (fx0 + w * 0.05, fy0 + (i * 11 + 10) % h)], fill=(58, 40, 12, 210))
    elif pat == "doppler":
        d = ImageDraw.Draw(img)
        for i in range(9):
            x = fx0 + int(w * i / 9.0)
            d.line([(x, fy0), (x + 20, fy1)], fill=(70, 90, 160, 90), width=6)

    # rim strip: thickness, lit on the edge side
    rx0, ry0, rx1, ry1 = tex_px(RIM_UV)
    for x in range(rx0, rx1):
        k = (x - rx0) / max(1, rx1 - rx0 - 1)
        v = 96 + int(92 * k)
        for y in range(ry0, ry1):
            pix[x, y] = (v, v + 3, v + 10, 255)
    return img


def trim_sheet():
    img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # brass
    bx0, by0, bx1, by1 = tex_px(BRASS_UV)
    for y in range(by0, by1):
        k = (y - by0) / max(1, by1 - by0 - 1)
        v = int(212 - 86 * k)
        d.line([(bx0, y), (bx1, y)], fill=(v, int(v * 0.75), int(v * 0.28), 255))
    d.line([(bx0, by0), (bx1, by0)], fill=(255, 242, 198, 255))
    d.line([(bx0, by1 - 2), (bx1, by1 - 2)], fill=(92, 62, 18, 255))

    # knurled rubber: diamond checkering
    kx0, ky0, kx1, ky1 = tex_px(KNURL_UV)
    d.rectangle([kx0, ky0, kx1, ky1], fill=(32, 33, 38, 255))
    for i in range(-40, 80, 4):
        d.line([(kx0 + i, ky0), (kx0 + i + 24, ky1)], fill=(52, 54, 62, 255), width=2)
        d.line([(kx0 + i, ky1), (kx0 + i + 24, ky0)], fill=(22, 23, 27, 255), width=2)

    # wood grain
    wx0, wy0, wx1, wy1 = tex_px(WOOD_UV)
    d.rectangle([wx0, wy0, wx1, wy1], fill=(96, 62, 34, 255))
    for i in range(18):
        y = wy0 + int((wy1 - wy0) * i / 18.0)
        d.line([(wx0, y), (wx1, y + 2)], fill=(124, 82, 46, 255))
        d.line([(wx0, y + 1), (wx1, y + 3)], fill=(70, 44, 24, 255))

    # paracord braid
    cx0, cy0, cx1, cy1 = tex_px(CORD_UV)
    d.rectangle([cx0, cy0, cx1, cy1], fill=(38, 40, 46, 255))
    for i in range(-30, 60, 6):
        d.line([(cx0 + i, cy0), (cx0 + i + 20, cy1)], fill=(64, 66, 76, 255), width=3)
        d.line([(cx0 + i + 3, cy0), (cx0 + i + 23, cy1)], fill=(24, 25, 30, 255), width=1)

    # ribbed tactical grip
    rx0, ry0, rx1, ry1 = tex_px(RIB_UV)
    d.rectangle([rx0, ry0, rx1, ry1], fill=(30, 31, 36, 255))
    for y in range(ry0, ry1, 5):
        d.line([(rx0, y), (rx1, y)], fill=(54, 56, 64, 255), width=2)
        d.line([(rx0, y + 2), (rx1, y + 2)], fill=(20, 21, 25, 255), width=1)
    return img


# ------------------------------------------------------------------ lofting
def stations(centre, widths, thickness=0.0, skew=0.0):
    """Turn a centre line plus (spine width, edge width) into left/right points."""
    out = []
    n = len(centre)
    for i, (x, y) in enumerate(centre):
        px, py = centre[max(0, i - 1)]
        nx, ny = centre[min(n - 1, i + 1)]
        dx, dy = nx - px, ny - py
        ln = math.hypot(dx, dy) or 1.0
        # normal pointing to the edge side
        ox, oy = dy / ln, -dx / ln
        wl, wr = widths[i]
        out.append(((x - ox * wl + skew, y - oy * wl), (x + ox * wr + skew, y + oy * wr)))
    return out


UV_BY_TEX = {"#steel": FACE_UV, "#brass": BRASS_UV, "#knurl": KNURL_UV,
             "#wood": WOOD_UV, "#cord": CORD_UV, "#rib": RIB_UV}


def loft(st, tex, hz, uv=None, rim=None, uv_v=(0.0, 1.0)):
    if uv is None:
        uv = UV_BY_TEX.get(tex, FACE_UV)
    if rim is None:
        rim = RIM_UV if tex == "#steel" else uv
    """Quads for one solid part: front, back, two rims, and end caps."""
    quads = []
    u0, v0, u1, v1 = uv
    ru0, rv0, ru1, rv1 = rim
    n = len(st)
    for i in range(n - 1):
        (la, ra), (lb, rb) = st[i], st[i + 1]
        va = uv_v[0] + (uv_v[1] - uv_v[0]) * (i / (n - 1.0))
        vb = uv_v[0] + (uv_v[1] - uv_v[0]) * ((i + 1) / (n - 1.0))
        t = hz if isinstance(hz, float) else hz[i]
        t2 = hz if isinstance(hz, float) else hz[i + 1]
        # front (-z) and back (+z): u across the part, v along it
        quads.append({"t": tex, "v": [la[0], la[1], -t, ra[0], ra[1], -t, rb[0], rb[1], -t2, lb[0], lb[1], -t2],
                      "uv": [u0, va, u1, vb]})
        quads.append({"t": tex, "v": [ra[0], ra[1], t, la[0], la[1], t, lb[0], lb[1], t2, rb[0], rb[1], t2],
                      "uv": [u0, va, u1, vb]})
        # rims: spine side and edge side
        quads.append({"t": rim_tex(tex), "v": [la[0], la[1], -t, lb[0], lb[1], -t2, lb[0], lb[1], t2, la[0], la[1], t],
                      "uv": [ru0, va, ru1, vb]})
        quads.append({"t": rim_tex(tex), "v": [ra[0], ra[1], -t, ra[0], ra[1], t, rb[0], rb[1], t2, rb[0], rb[1], -t2],
                      "uv": [ru0, va, ru1, vb]})
    # caps
    (la, ra) = st[0]
    quads.append({"t": tex, "v": [la[0], la[1], -hz0(hz), ra[0], ra[1], -hz0(hz), ra[0], ra[1], hz0(hz), la[0], la[1], hz0(hz)],
                  "uv": [u0, v0, u1, v0 + 0.05]})
    (lb, rb) = st[-1]
    quads.append({"t": tex, "v": [rb[0], rb[1], -hz1(hz), lb[0], lb[1], -hz1(hz), lb[0], lb[1], hz1(hz), rb[0], rb[1], hz1(hz)],
                  "uv": [u0, v1 - 0.05, u1, v1]})
    return quads


def rim_tex(tex):
    return "#rim" if tex == "#steel" else tex


def hz0(hz):
    return hz if isinstance(hz, float) else hz[0]


def hz1(hz):
    return hz if isinstance(hz, float) else hz[-1]


def ring_quads(cx, cy, r_in, r_out, hz, tex, uv, seg=12):
    """Annulus extruded into a finger ring: front, back and inner/outer rims."""
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
        quads.append({"t": tex, "v": [pi0[0], pi0[1], -hz, po0[0], po0[1], -hz, po1[0], po1[1], -hz, pi1[0], pi1[1], -hz],
                      "uv": [ui, v0, ui2, v1]})
        quads.append({"t": tex, "v": [po0[0], po0[1], hz, pi0[0], pi0[1], hz, pi1[0], pi1[1], hz, po1[0], po1[1], hz],
                      "uv": [ui, v0, ui2, v1]})
        quads.append({"t": tex, "v": [po0[0], po0[1], -hz, po1[0], po1[1], -hz, po1[0], po1[1], hz, po0[0], po0[1], hz],
                      "uv": [ui, v0, ui2, v1]})
        quads.append({"t": tex, "v": [pi0[0], pi0[1], hz, pi1[0], pi1[1], hz, pi1[0], pi1[1], -hz, pi0[0], pi0[1], -hz],
                      "uv": [ui, v0, ui2, v1]})
    return quads


def handle(cx_line, widths, hz, tex="#knurl"):
    return loft(stations(cx_line, widths, hz), tex, hz)


def serrate(y0, y1, teeth, half):
    """Saw teeth on the spine: width spikes that cut into the silhouette."""
    pts = []
    span = (y1 - y0) / teeth
    for i in range(teeth * 2 + 1):
        y = y0 + span * i * 0.5
        pts.append((0.0, y, half * (0.42 if i % 2 else 1.0)))
    return pts


def blade_line(y0, y1, n, w0, w1, curve=0.0, tip=0.0, teeth=None, tooth_from=0.0, tooth_to=0.0, clip=0.0):
    """Centre line with (spine width, edge width) pairs: asymmetric, one cutting edge."""
    centre, widths = [], []
    for i in range(n):
        t = i / (n - 1.0)
        y = y0 + (y1 - y0) * t
        x = curve * (t ** 2)
        front = min(1.0, max(0.0, (t - tooth_from) / max(1e-6, (tooth_to - tooth_from))))
        taper = 1.0 - tip * max(0.0, (t - 0.72) / 0.28) ** 1.6
        half = (w0 + (w1 - w0) * t) * taper
        spine = half * 0.62
        if clip:
            spine *= 1.0 - clip * max(0.0, (t - 0.55) / 0.45) ** 0.9
        if teeth and tooth_from <= t <= tooth_to:
            k = (t - tooth_from) / (tooth_to - tooth_from) * teeth
            spine *= 0.55 if int(k * 2) % 2 == 0 else 1.0
        centre.append((x, y))
        widths.append((spine, half))
    return centre, widths


KNIVES = {}


def knife(name, parts, tex_extra=None):
    quads = []
    for p in parts:
        quads.extend(p)
    textures = {"steel": "zeus:knives/blade_plain", "trim": "zeus:knives/trim",
                "rim": "zeus:knives/blade_plain"}
    for key in ("#knurl", "#wood", "#cord", "#rib"):
        textures[key[1:]] = "zeus:knives/trim"
    model = {"name": name, "textures": textures, "quads": quads}
    KNIVES[slug(name)] = model


def slug(name):
    return name.lower().replace(" ", "_").replace("/", "_")


def build_karambit():
    # claw: handle angled back, blade sweeping forward and down, ring at the pommel
    hc = [(-0.6 + 0.10 * i, 1.2 * i) for i in range(9)]
    hw = [(1.55, 1.55) if i not in (2, 5) else (1.35, 1.35) for i in range(9)]
    parts = [loft(stations(hc, hw, 0.62), "#knurl", 0.62)]
    # brass collar
    parts.append(loft(stations([(-0.3, 9.2), (-0.1, 10.1)], [(1.05, 1.05)] * 2), "#brass", 0.7))
    # claw blade: leaves the handle top, sweeps forward and hooks up to a point
    centre, widths = [], []
    for i in range(20):
        t = i / 19.0
        a = math.radians(4 + 86 * t)
        centre.append((0.5 + 6.9 * math.sin(a), 9.7 + 6.9 * (1.0 - math.cos(a))))
        widths.append((2.2 * (1 - 0.78 * t), 0.9 * (1 - 0.75 * t)))
    parts.append(loft(stations(centre, widths, 0.42), "#steel", 0.42, uv_v=(0.0, 1.0)))
    parts.append(ring_quads(-2.0, 0.2, 1.15, 1.95, 0.6, "#brass", BRASS_UV, seg=14))
    knife("Karambit", parts)


def build_m9():
    # clip point bayonet, spine saw teeth, muzzle ring on the guard
    hc = [(0.0, 0.9 * i) for i in range(14)]
    hw = [(1.45, 1.45)] * 14
    parts = [loft(stations(hc, hw, 0.55), "#rib", 0.55)]
    parts.append(loft(stations([(-1.7, 11.7), (0.0, 12.0), (1.7, 11.7)], [(0.9, 0.9)] * 3), "#brass", 0.8))
    parts.append(ring_quads(0.0, 12.1, 0.6, 1.5, 0.72, "#brass", BRASS_UV, seg=10))
    centre, widths = blade_line(12.6, 28.6, 46, 2.35, 1.85, tip=0.30, teeth=7, tooth_from=0.26, tooth_to=0.66, clip=0.55)
    parts.append(loft(stations(centre, widths, 0.36), "#steel", 0.36))
    knife("M9 Bayonet", parts)


def build_huntsman():
    hc = [(0.0, 0.85 * i) for i in range(13)]
    hw = [(1.4, 1.4)] * 13
    parts = [loft(stations(hc, hw, 0.55), "#cord", 0.55)]
    parts.append(loft(stations([(-1.5, 10.7), (0.0, 11.0), (1.5, 10.7)], [(0.85, 0.85)] * 3), "#brass", 0.76))
    centre, widths = blade_line(11.5, 27.0, 40, 2.75, 2.55, tip=0.45, teeth=5, tooth_from=0.12, tooth_to=0.48, clip=0.5)
    parts.append(loft(stations(centre, widths, 0.34), "#steel", 0.34))
    knife("Huntsman", parts)


def build_bowie():
    hc = [(0.0, 0.95 * i) for i in range(13)]
    hw = [(1.6, 1.6)] * 13
    parts = [loft(stations(hc, hw, 0.62), "#wood", 0.62)]
    # curved crossguard
    parts.append(loft(stations([(-2.1, 12.0), (0.0, 12.3), (2.1, 12.0)],
                               [(0.55, 0.55), (0.75, 0.75), (0.55, 0.55)], 0.7), "#brass", 0.7))
    centre, widths = blade_line(12.3, 30.0, 22, 3.0, 2.8, tip=0.4, clip=0.6)
    parts.append(loft(stations(centre, widths, 0.38), "#steel", 0.38))
    knife("Bowie", parts)


def build_butterfly():
    # two separated vented handles either side of the tang, slim clip point blade
    parts = []
    for side in (-1, 1):
        rail = [(side * 1.25, 0.6 + 0.9 * i) for i in range(11)]
        parts.append(loft(stations(rail, [(0.42, 0.42)] * 11, 0.5), "#knurl", 0.5))
        rail2 = [(side * 2.15, 0.6 + 0.9 * i) for i in range(11)]
        parts.append(loft(stations(rail2, [(0.30, 0.30)] * 11, 0.36), "#brass", 0.36))
    centre, widths = blade_line(10.4, 25.0, 16, 1.25, 0.95, tip=0.6, clip=0.45)
    parts.append(loft(stations(centre, widths, 0.26), "#steel", 0.26))
    knife("Butterfly knife", parts)


def build_skeleton():
    # double edged dagger, skeletonised handle with a large finger hole
    parts = []
    for side in (-1, 1):
        rail = [(side * 1.15, 0.5 + 0.8 * i) for i in range(9)]
        parts.append(loft(stations(rail, [(0.34, 0.34)] * 9, 0.34), "#brass", 0.34))
    parts.append(loft(stations([(0.0, 0.4), (0.0, 0.9)], [(1.25, 1.25)] * 2), "#brass", 0.4))
    parts.append(ring_quads(0.0, 3.6, 0.95, 1.55, 0.34, "#brass", BRASS_UV, seg=12))
    centre, widths = [], []
    for i in range(20):
        t = i / 19.0
        y = 7.6 + 15.4 * t
        half = 1.5 * (1.0 - 0.92 * max(0.0, (t - 0.35) / 0.65) ** 1.5)
        centre.append((0.0, y))
        widths.append((half, half))      # symmetric: this one is double edged
    parts.append(loft(stations(centre, widths, 0.3), "#steel", 0.3))
    knife("Skeleton", parts)


def build_kukri():
    # forward curving kukri belly with a contoured handle
    hc = [(0.0, 0.85 * i) for i in range(13)]
    hw = [(1.45, 1.45) if i % 3 else (1.28, 1.28) for i in range(13)]
    parts = [loft(stations(hc, hw, 0.56), "#wood", 0.56)]
    parts.append(loft(stations([(-1.4, 10.5), (0.0, 10.8), (1.4, 10.5)], [(0.8, 0.8)] * 3), "#brass", 0.72))
    centre, widths = [], []
    for i in range(20):
        t = i / 19.0
        y = 11.2 + 15.6 * t
        x = 1.5 * (t ** 1.7) - 0.6 * t
        half = 1.25 + 1.25 * math.sin(min(1.0, t * 1.15) * math.pi * 0.9) - 0.9 * max(0.0, (t - 0.8) / 0.2) ** 1.4
        centre.append((x, y))
        widths.append((half * 0.55, half))
    parts.append(loft(stations(centre, widths, 0.34), "#steel", 0.34))
    knife("Kukri", parts)


def build_stiletto():
    # narrow needle blade with a curved bolster and slim handle
    hc = [(0.0, 0.95 * i) for i in range(14)]
    hw = [(1.05, 1.05)] * 14
    parts = [loft(stations(hc, hw, 0.42), "#knurl", 0.42)]
    parts.append(loft(stations([(0.0, 12.3), (0.0, 13.0), (0.0, 13.6)],
                               [(1.0, 1.0), (1.15, 1.15), (0.7, 0.7)], 0.5), "#brass", 0.5))
    centre, widths = blade_line(13.6, 29.4, 20, 0.95, 0.7, tip=0.82)
    parts.append(loft(stations(centre, widths, 0.22), "#steel", 0.22))
    knife("Stiletto", parts)


BUILDERS = [build_karambit, build_m9, build_huntsman, build_bowie,
            build_butterfly, build_skeleton, build_kukri, build_stiletto]


# ------------------------------------------------------------------ preview
def shade_for(v):
    """Minecraft style fixed face shading from the quad normal."""
    a = v[0:3]
    b = v[3:6]
    c = v[6:9]
    u = [b[i] - a[i] for i in range(3)]
    w = [c[i] - a[i] for i in range(3)]
    n = [u[1] * w[2] - u[2] * w[1], u[2] * w[0] - u[0] * w[2], u[0] * w[1] - u[1] * w[0]]
    ln = math.sqrt(sum(t * t for t in n)) or 1.0
    n = [t / ln for t in n]
    if abs(n[2]) > 0.7:
        return 1.0 if n[2] < 0 else 0.72
    if abs(n[1]) > 0.7:
        return 0.92 if n[1] > 0 else 0.6
    return 0.84


def render(model, sheets, tint, size, ax=-10.0, ay=-20.0):
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
        quads.append((sum(p[2] for p in pts) / 4.0, pts, q["uv"], q["t"], shade_for(v)))
    allp = [p for _, pts, _, _, _ in quads for p in pts]
    xs = [p[0] for p in allp]
    ys = [p[1] for p in allp]
    span = max(max(xs) - min(xs), max(ys) - min(ys)) or 1.0
    S = size * 0.92 / span
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
        warped = sheet.transform((size, size), Image.AFFINE, (A, B, C, D, E, F), resample=Image.NEAREST)
        if tex == "#steel":
            r, g, b, a = warped.split()
            warped = Image.merge("RGBA", (r.point(lambda q: q * tint[0] // 255),
                                          g.point(lambda q: q * tint[1] // 255),
                                          b.point(lambda q: q * tint[2] // 255), a))
        if sh < 1.0:
            dark = Image.new("RGBA", (size, size), (0, 0, 0, int(255 * (1 - sh))))
            mask = ImageChops.multiply(warped.split()[3], Image.new("L", (size, size), 255))
            warped = Image.composite(dark, warped, Image.composite(Image.new("L", (size, size), 255),
                                                                   Image.new("L", (size, size), 0),
                                                                   warped.split()[3]))
        clip = Image.new("L", (size, size), 0)
        ImageDraw.Draw(clip).polygon(scr, fill=255)
        warped.putalpha(ImageChops.multiply(warped.split()[3], clip))
        canvas.alpha_composite(warped)
    return canvas


FINISH_TINTS = {
    "Stainless": (206, 212, 222),
    "Blue steel": (150, 180, 235),
    "Fade": (240, 176, 150),
    "Slaughter": (226, 140, 140),
    "Crimson web": (214, 120, 120),
    "Doppler": (156, 132, 232),
    "Case hardened": (214, 196, 156),
    "Night": (150, 154, 166),
    "Forest": (150, 186, 150),
    "Gamma Doppler": (150, 226, 190),
    "Gold": (236, 209, 108),
    "Damascus": (198, 204, 214),
    "Emerald": (140, 220, 170),
    "Obsidian": (150, 148, 168),
    "Copper": (224, 170, 128),
    "Ruby": (228, 130, 150),
    "Tiger": (236, 190, 110),
    "Rainbow": (200, 190, 230),
}


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
    print("wrote %d models and %d texture sheets" % (len(KNIVES), len(PATTERNS) + 1))

    # contact sheet: 2 rows of 4, one finish per knife, like the reference photo
    os.makedirs(os.path.join(ROOT, "artifacts"), exist_ok=True)
    order = list(KNIVES.items())
    cell = 300
    sheet = Image.new("RGBA", (cell * 4, cell * 2), (18, 20, 26, 255))
    d = ImageDraw.Draw(sheet)
    finishes = list(FINISH_TINTS.items())
    for i, (ident, model) in enumerate(order):
        pat = ["plain", "damascus", "fade", "tiger", "doppler", "plain", "case", "fade"][i % 8]
        sheets = {"#steel": Image.open(os.path.join(TEX_DIR, "blade_%s.png" % pat)).convert("RGBA"),
                  "#trim": Image.open(os.path.join(TEX_DIR, "trim.png")).convert("RGBA"),
                  "#knurl": Image.open(os.path.join(TEX_DIR, "trim.png")).convert("RGBA"),
                  "#cord": Image.open(os.path.join(TEX_DIR, "trim.png")).convert("RGBA"),
                  "#rib": Image.open(os.path.join(TEX_DIR, "trim.png")).convert("RGBA"),
                  "#wood": Image.open(os.path.join(TEX_DIR, "trim.png")).convert("RGBA"),
                  "#brass": Image.open(os.path.join(TEX_DIR, "trim.png")).convert("RGBA")}
        tint = FINISH_TINTS[finishes[i * 2][0]]
        img = render(model, sheets, tint, cell)
        x, y = (i % 4) * cell, (i // 4) * cell
        sheet.alpha_composite(img, (x, y))
        d.text((x + 10, y + cell - 18), model["name"], fill=(214, 222, 236))
        d.rectangle([x + 2, y + 2, x + cell - 3, y + cell - 3], outline=(34, 40, 52))
    sheet.convert("RGB").save(os.path.join(ROOT, "artifacts", "cs2_knives.png"))
    print("preview -> artifacts/cs2_knives.png")


if __name__ == "__main__":
    main()
