#!/usr/bin/env python3
"""Render the HD knives the way KnifeHd.java draws them, straight from the generated atlas.

A software rasteriser rather than a quad blit: for every quad it inverse maps the covered
pixels into the sheet, so the cell rows, the alpha outline and the part transforms are checked
exactly as the game will use them.

Run: python3 tools/knife_hd_preview.py
"""

import json
import math
import os

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JSON = os.path.join(ROOT, "src/main/resources/assets/zeus/knives/hd/tactical.json")
TEX = os.path.join(ROOT, "src/main/resources/assets/zeus/textures/knives/hd")

SC = 1.2
U0 = 0.3
D = 90.0
CELLS = 7
FACE = 0
SIDE = 3
HALF = 3.55
LEN = 28.4


def rot(p, ax, ay, az):
    x, y, z = p
    cx, sx = math.cos(az), math.sin(az)
    x, y = x * cx - y * sx, x * sx + y * cx
    cy, sy = math.cos(ay), math.sin(ay)
    x, z = x * cy + z * sy, -x * sy + z * cy
    cc, sc = math.cos(ax), math.sin(ax)
    y, z = y * cc - z * sc, y * sc + z * cc
    return np.array([x, y, z])


def centre(model, u):
    arr = model["centre"]
    if len(arr) < 2:
        return 0.0
    x = max(0.0, min(len(arr) - 1.001, u / LEN * (len(arr) - 1)))
    i = int(x)
    t = x - i
    return arr[i] * (1 - t) + arr[i + 1] * t


def model_quads(model, order):
    quads = []
    step, nv = 0.55, 6
    for part in model["parts"]:
        t = part["t"]
        nu = max(1, int(math.ceil((part["u1"] - part["u0"]) / step)))
        for j in range(nv):
            va = -HALF + 2 * HALF * j / nv
            vb = -HALF + 2 * HALF * (j + 1) / nv
            for i in range(nu):
                ua = part["u0"] + (part["u1"] - part["u0"]) * i / nu
                ub = part["u0"] + (part["u1"] - part["u0"]) * (i + 1) / nu
                pts = np.array([rot(((vv + centre(model, uu)) * SC, (uu - U0) * SC, t * SC), *order)
                                for uu, vv in ((ua, va), (ua, vb), (ub, vb), (ub, va))])
                # face normal is the model +z, tipped by the same rotation
                n = rot((0.0, 0.0, 1.0), *order)
                shade = 0.56 + 0.44 * abs(n[2])
                quads.append((pts, (ua / LEN, ub / LEN, (FACE + j / nv) / CELLS,
                                    (FACE + (j + 1) / nv) / CELLS), shade,
                              float(np.mean(pts[:, 2]))))
        # side wall
        ns = max(1, int(math.ceil((part["u1"] - part["u0"]) / 1.2)))
        for i in range(ns):
            ua = part["u0"] + (part["u1"] - part["u0"]) * i / ns
            ub = part["u0"] + (part["u1"] - part["u0"]) * (i + 1) / ns
            pts = np.array([
                rot((centre(model, ua) * SC, (ua - U0) * SC, -t * SC), *order),
                rot((centre(model, ub) * SC, (ub - U0) * SC, -t * SC), *order),
                rot((centre(model, ub) * SC, (ub - U0) * SC, t * SC), *order),
                rot((centre(model, ua) * SC, (ua - U0) * SC, t * SC), *order),
            ])
            tv = (SIDE + 0.5) / CELLS
            quads.append((pts, (ua / LEN, ub / LEN, tv, tv), 0.62, float(np.mean(pts[:, 2]))))
    return quads


def raster(quads, sheet, size, pad=8):
    """Software rasteriser with a depth buffer; returns an RGBA array."""
    tex = np.asarray(sheet.convert("RGBA"), dtype=np.float32)
    th, tw = tex.shape[:2]
    cellh = th / CELLS
    pts = np.vstack([q[0] for q in quads])
    span = max(float(pts[:, 0].max() - pts[:, 0].min()),
               float(pts[:, 1].max() - pts[:, 1].min())) or 1.0
    S = (size - 2 * pad) / span
    cx = (pts[:, 0].min() + pts[:, 0].max()) / 2
    cy = (pts[:, 1].min() + pts[:, 1].max()) / 2

    out = np.zeros((size, size, 4), dtype=np.float32)
    zbuf = np.full((size, size), 1e9, dtype=np.float32)
    order = sorted(range(len(quads)), key=lambda i: -quads[i][3])

    for qi in order:
        qpts, (u0, u1, v0, v1), shade, z = quads[qi]
        scr = np.zeros((4, 2))
        for i in range(4):
            p = qpts[i]
            s = S * D / (D + p[2])
            scr[i] = (size / 2 + (p[0] - cx) * s, size / 2 - (p[1] - cy) * s)
        x0, y0 = np.floor(scr.min(axis=0)).astype(int)
        x1, y1 = np.ceil(scr.max(axis=0)).astype(int)
        x0, y0 = max(0, x0), max(0, y0)
        x1, y1 = min(size - 1, x1), min(size - 1, y1)
        if x1 < x0 or y1 < y0:
            continue
        # affine dest -> uv over the quad's parallelogram span
        ex = scr[1] - scr[0]
        ey = scr[3] - scr[0]
        det = ex[0] * ey[1] - ex[1] * ey[0]
        if abs(det) < 1e-6:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        dx, dy = gx - scr[0][0], gy - scr[0][1]
        a = (dx * ey[1] - dy * ey[0]) / det
        b = (-dx * ex[1] + dy * ex[0]) / det
        inside = (a >= 0.0) & (a <= 1.0) & (b >= 0.0) & (b <= 1.0)
        if not inside.any():
            continue
        # a runs across the quad (texture v), b along it (texture u)
        uu = u0 + (u1 - u0) * b
        vv = v0 + (v1 - v0) * a
        # stay inside this quad's own cell: a row of the neighbouring layer is opaque art
        lo, hi = v0 + 0.5 / th, v1 - 0.5 / th
        vv = np.clip(vv, lo, hi)
        tx = np.clip((uu * tw).astype(int), 0, tw - 1)
        ty = np.clip((vv * th).astype(int), 0, th - 1)
        sample = tex[ty, tx]
        alpha = sample[..., 3] / 255.0
        keep = inside & (alpha > 0.02)
        if not keep.any():
            continue
        sub = out[y0:y1 + 1, x0:x1 + 1]
        zsub = zbuf[y0:y1 + 1, x0:x1 + 1]
        nearer = keep & (z < zsub)
        if not nearer.any():
            continue
        rgb = sample[..., :3] * shade
        sub[nearer, :3] = rgb[nearer]
        sub[nearer, 3] = 255
        zsub[nearer] = z
    return out.astype(np.uint8)


def main():
    data = json.load(open(JSON))
    cell = 320
    out = Image.new("RGBA", (cell * 4, cell * 2), (12, 20, 38, 255))
    d = ImageDraw.Draw(out)
    order = (-0.12, -0.46, 0.36)
    for i, k in enumerate(data["knives"]):
        sheet = Image.open(os.path.join(TEX, k["id"] + "_0.png")).convert("RGBA")
        img = Image.fromarray(raster(model_quads(k, order), sheet, cell), "RGBA")
        x, y = (i % 4) * cell, (i // 4) * cell
        out.alpha_composite(img, (x, y))
        d.text((x + 12, y + cell - 26), k["name"], fill=(206, 216, 234))
    os.makedirs(os.path.join(ROOT, "artifacts"), exist_ok=True)
    out.convert("RGB").save(os.path.join(ROOT, "artifacts", "hd_knives_3d.png"))
    print("preview -> artifacts/hd_knives_3d.png")


if __name__ == "__main__":
    main()
