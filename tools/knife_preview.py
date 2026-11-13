#!/usr/bin/env python3
"""Render every knife in knives_lo.json to one contact sheet PNG.

This is the offline check for the generated models: it uses the same painter's-algorithm
view as KnifeMesh (orthographic, flat shading per triangle, near triangles last), so a
knife that looks wrong here would look wrong in game.

Run:  python3 tools/knife_preview.py out.png
"""

import json
import math
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JSON_PATH = os.path.join(ROOT, "src/main/resources/assets/zeus/knives/knives_lo.json")

MATS = {
    "blade": ((214, 224, 236), (120, 132, 150), (46, 52, 64)),
    "handle": ((92, 98, 112), (54, 58, 70), (22, 24, 30)),
    "guard": ((150, 158, 172), (86, 92, 106), (34, 38, 46)),
    "pin": ((226, 206, 150), (150, 126, 74), (60, 48, 26)),
}


def rot(p, ax, ay):
    x, y, z = p
    cx, sx = math.cos(ax), math.sin(ax)
    cy, sy = math.cos(ay), math.sin(ay)
    x, z = x * cy + z * sy, -x * sy + z * cy
    y, z = y * cx - z * sx, y * sx + z * cx
    return (x, y, z)


def shade(mat, n, light):
    base, mid, dark = MATS.get(mat, MATS["blade"])
    lam = max(0.0, sum(a * b for a, b in zip(n, light)))
    k = 0.16 + 0.84 * lam
    if k < 0.5:
        t = k * 2
        c = tuple(int(dark[i] + (mid[i] - dark[i]) * t) for i in range(3))
    else:
        t = (k - 0.5) * 2
        c = tuple(int(mid[i] + (base[i] - mid[i]) * t) for i in range(3))
    return c


def draw_knife(dr, model, box, show_name):
    x0, y0, x1, y1 = box
    ax, ay = math.radians(-18), math.radians(28)
    tris = []
    for part in model["parts"]:
        for mesh in part["meshes"]:
            v = mesh["v"]
            pts = [(v[i], v[i + 1], v[i + 2]) for i in range(0, len(v), 3)]
            pts = [rot(p, ax, ay) for p in pts]
            if model.get("kind") == "butterfly":
                pts = [(p[0], -p[1], -p[2]) for p in pts]
            f = mesh["f"]
            for i in range(0, len(f), 3):
                a, b, c = pts[f[i]], pts[f[i + 1]], pts[f[i + 2]]
                u = [b[0] - a[0], b[1] - a[1], b[2] - a[2]]
                w = [c[0] - a[0], c[1] - a[1], c[2] - a[2]]
                n = (u[1] * w[2] - u[2] * w[1], u[2] * w[0] - u[0] * w[2], u[0] * w[1] - u[1] * w[0])
                ln = math.sqrt(sum(t * t for t in n)) or 1.0
                n = (n[0] / ln, n[1] / ln, n[2] / ln)
                depth = (a[2] + b[2] + c[2]) / 3
                col = shade(mesh["m"], n, (0.35, 0.45, -0.82))
                tris.append((depth, a, b, c, col))
    if not tris:
        return
    xs = [p[0] for t in tris for p in t[1:4]]
    ys = [p[1] for t in tris for p in t[1:4]]
    spanx = max(xs) - min(xs) or 1.0
    spany = max(ys) - min(ys) or 1.0
    pad = 12
    sc = min((x1 - x0 - pad * 2) / spanx, (y1 - y0 - pad * 2 - 12) / spany)
    midx = (min(xs) + max(xs)) / 2
    midy = (min(ys) + max(ys)) / 2
    cx = (x0 + x1) / 2
    cy = (y0 + y1) / 2 - 4

    def screen(p):
        return (cx + (p[0] - midx) * sc, cy - (p[1] - midy) * sc)

    tris.sort(key=lambda t: t[0])
    for _, a, b, c, col in tris:
        dr.polygon([screen(a), screen(b), screen(c)], fill=col)
    if show_name:
        dr.text((x0 + 6, y1 - 14), model["name"], fill=(200, 210, 230))


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "artifacts/knives.png")
    doc = json.load(open(JSON_PATH, encoding="utf-8"))
    knives = doc["knives"]
    cols = 7
    rows = (len(knives) + cols - 1) // cols
    tw, th = 168, 268
    img = Image.new("RGB", (cols * tw, rows * th), (10, 12, 18))
    dr = ImageDraw.Draw(img)
    for i, k in enumerate(knives):
        cx, cy = (i % cols) * tw, (i // cols) * th
        dr.rectangle([cx + 2, cy + 2, cx + tw - 2, cy + th - 2], outline=(26, 30, 40))
        draw_knife(dr, k, (cx, cy, cx + tw, cy + th), True)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out)
    print("wrote %s (%dx%d, %d knives)" % (out, img.width, img.height, len(knives)))


if __name__ == "__main__":
    main()
