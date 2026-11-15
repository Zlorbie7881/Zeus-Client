#!/usr/bin/env python3
"""Render every knife in knives_lo.json to one contact sheet PNG.

This is the offline check for the generated models.  It mirrors KnifeMesh exactly: the
same orthographic projection, the same painter's ordering, the same five stop shade ramps,
the same lengthwise polish gradient and the same per face variance.  The only additions are
a 3x supersample (so edges are anti aliased like Minecraft's filtered texture blits) and a
soft drop shadow.

Run:  python3 tools/knife_preview.py out.png
"""

import json
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JSON_PATH = os.path.join(ROOT, "src/main/resources/assets/zeus/knives/knives_lo.json")
SS = 3  # supersample factor

# KnifeArt.sLook(finish=0, "Stainless"): handle = SFIN[0..3], blade = SFIN[4..6].
HANDLE = ((138, 143, 152), (74, 79, 88), (36, 39, 46), (14, 16, 20))
BLADE = ((244, 247, 251), (183, 192, 204), (107, 118, 132))
BRASS = ((58, 36, 8), (94, 58, 14), (154, 100, 32), (200, 147, 62), (239, 201, 122))

LIGHT = (-0.40, -0.55, -0.73)


def mix(c1, c2, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(c1[i] * (1 - t) + c2[i] * t)) for i in range(3))


def lighten(c, t):
    return mix(c, (255, 255, 255), t)


def darken(c, t):
    return mix(c, (0, 0, 0), t)


def ramp(mat):
    """KnifeMesh.shade(): deep, shadow, body, lit and highlight for one material."""
    grip = mat in ("handle", "cord")
    if grip:
        base = darken(HANDLE[1], 0.35) if mat == "cord" else HANDLE[0]
        return (darken(HANDLE[3], 0.35), HANDLE[2], HANDLE[1], base, lighten(base, 0.30))
    if mat in ("brass", "accent"):
        return BRASS
    hi, mid, dark = BLADE
    if mat in ("edge", "guard", "pin"):
        hi = lighten(BLADE[0], 0.30)
        mid = mix(BLADE[0], BLADE[1], 0.45)
    elif mat == "dark":
        hi, mid, dark = mid, dark, darken(dark, 0.4)
    return (darken(dark, 0.55), dark, mid, hi, lighten(hi, 0.45))


def jitter(seed):
    h = (seed * 374761393 + 668265263) & 0xFFFFFFFF
    h = (h ^ (h >> 13)) & 0xFFFFFFFF
    h = (h * 1274126177) & 0xFFFFFFFF
    h = (h ^ (h >> 16)) & 0xFFFFFFFF
    return (h & 255) / 255.0 - 0.5


def shade(mat, n, lx, ly, seed):
    grip = mat in ("handle", "cord")
    lam = max(0.0, sum(a * b for a, b in zip(n, LIGHT)))
    b = (0.08 if grip else -0.02) + (0.95 if grip else 0.90) * lam
    b -= 0.10 * max(0.0, n[1])
    if not grip:
        b += 0.13 * abs(n[0])
        b += 0.10 * (max(0.0, min(1.0, ly / 26.0)) - 0.45)
        if mat not in ("handle", "cord"):
            b += 0.045 * math.sin(lx * 6.3 + ly * 0.35)
        if mat == "edge":
            b += 0.12
        else:
            b -= 0.18 * (1.0 - min(1.0, abs(n[2])))
    b += jitter(seed) * (0.045 if grip else 0.075)
    b = max(0.0, min(1.0, b))
    a0, a1, a2, a3, a4 = ramp(mat)
    if b < 0.25:
        return mix(a0, a1, b * 4)
    if b < 0.5:
        return mix(a1, a2, (b - 0.25) * 4)
    if b < 0.75:
        return mix(a2, a3, (b - 0.5) * 4)
    return mix(a3, a4, (b - 0.75) * 4)


def rot(p, ax, ay):
    x, y, z = p
    cx, sx = math.cos(ax), math.sin(ax)
    cy, sy = math.cos(ay), math.sin(ay)
    x, z = x * cy + z * sy, -x * sy + z * cy
    y, z = y * cx - z * sx, y * sx + z * cx
    return (x, y, z)


def collect(model, ax, ay):
    tris = []
    for part in model["parts"]:
        for mesh in part["meshes"]:
            v = mesh["v"]
            pts = [rot((v[i], v[i + 1], v[i + 2]), ax, ay) for i in range(0, len(v), 3)]
            if model.get("kind") == "butterfly":
                pts = [(p[0], -p[1], -p[2]) for p in pts]
            f = mesh["f"]
            for i in range(0, len(f), 3):
                ia, ib, ic = f[i], f[i + 1], f[i + 2]
                a, b, c = pts[ia], pts[ib], pts[ic]
                u = (b[0] - a[0], b[1] - a[1], b[2] - a[2])
                w = (c[0] - a[0], c[1] - a[1], c[2] - a[2])
                n = (u[1] * w[2] - u[2] * w[1], u[2] * w[0] - u[0] * w[2], u[0] * w[1] - u[1] * w[0])
                ln = math.sqrt(sum(t * t for t in n))
                if ln < 1e-6:
                    continue
                n = (n[0] / ln, n[1] / ln, n[2] / ln)
                ly = (v[ia * 3 + 1] + v[ib * 3 + 1] + v[ic * 3 + 1]) / 3.0
                lx = (v[ia * 3] + v[ib * 3] + v[ic * 3]) / 3.0
                col = shade(mesh["m"], n, lx, ly, i)
                tris.append(((a[2] + b[2] + c[2]) / 3.0, a, b, c, col))
    return tris


def draw_knife(dr, shadow, model, box, show_name, tw):
    x0, y0, x1, y1 = box
    ax, ay = math.radians(-14), math.radians(26)
    tris = collect(model, ax, ay)
    if not tris:
        return
    xs = [p[0] for t in tris for p in t[1:4]]
    ys = [p[1] for t in tris for p in t[1:4]]
    spanx = max(xs) - min(xs) or 1.0
    spany = max(ys) - min(ys) or 1.0
    pad = 16
    sc = min((x1 - x0 - pad * 2) / spanx, (y1 - y0 - pad * 2 - 14) / spany)
    midx = (min(xs) + max(xs)) / 2
    midy = (min(ys) + max(ys)) / 2
    cx = (x0 + x1) / 2
    cy = (y0 + y1) / 2 - 4

    def screen(p, dx=0.0, dy=0.0):
        return (cx + (p[0] - midx) * sc + dx, cy - (p[1] - midy) * sc + dy)

    tris.sort(key=lambda t: t[0], reverse=True)  # KnifeMesh paints the far faces first
    for _, a, b, c, _ in tris:
        shadow.polygon([screen(a, 7, 10), screen(b, 7, 10), screen(c, 7, 10)], fill=(0, 0, 0, 150))
    for _, a, b, c, col in tris:
        dr.polygon([screen(a), screen(b), screen(c)], fill=col)


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "artifacts/knives.png")
    doc = json.load(open(JSON_PATH, encoding="utf-8"))
    knives = doc["knives"]
    cols = 7
    rows = (len(knives) + cols - 1) // cols
    tw, th = 186, 292
    W, H = cols * tw, rows * th

    img = Image.new("RGB", (W * SS, H * SS), (13, 15, 21))
    dr = ImageDraw.Draw(img)
    # Backdrop: a soft vertical wash and hairline cards, so the sheet does not look like a dump.
    for y in range(H * SS):
        k = y / (H * SS)
        dr.line([(0, y), (W * SS, y)], fill=(int(14 + 9 * k), int(16 + 9 * k), int(23 + 11 * k)))
    for i in range(len(knives)):
        cx, cy = (i % cols) * tw * SS, (i // cols) * th * SS
        dr.rectangle([cx + 3, cy + 3, cx + tw * SS - 4, cy + th * SS - 4], outline=(30, 35, 47))

    shadow = Image.new("RGBA", (W * SS, H * SS), (0, 0, 0, 0))
    sh = ImageDraw.Draw(shadow)
    for i, k in enumerate(knives):
        cx, cy = (i % cols) * tw * SS, (i // cols) * th * SS
        draw_knife(dr, sh, k, (cx, cy, cx + tw * SS, cy + th * SS), False, tw * SS)
    shadow = shadow.filter(ImageFilter.GaussianBlur(5 * SS / 3))
    img = Image.alpha_composite(img.convert("RGBA"), shadow).convert("RGB")

    small = img.resize((W, H), Image.LANCZOS)
    dr2 = ImageDraw.Draw(small)
    for i, k in enumerate(knives):
        cx, cy = (i % cols) * tw, (i // cols) * th
        dr2.text((cx + 9, cy + th - 17), k["name"], fill=(196, 206, 226))
    os.makedirs(os.path.dirname(out), exist_ok=True)
    small.save(out)
    print("wrote %s (%dx%d, %d knives)" % (out, small.width, small.height, len(knives)))


if __name__ == "__main__":
    main()
