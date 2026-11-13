#!/usr/bin/env python3
"""Preview the HD knives exactly as KnifeHD builds them and KnifeHDGui paints them.

Mirrors the Java: core slices, the profile strip through the thickness, the two inset rim
layers, the lit face, then the additive gloss and edge passes -- each quad painted with one
flat colour, the way the GUI draws it, over the sheet artwork.

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
FACE, RIM50, RIM13, SIDE, GLOSS, EDGE, PROFILE = range(7)
LX, LY, LZ = -0.40, -0.55, -0.73
FX, FY, FZ = 0.55, 0.35, -0.76
SPX, SPY, SPZ = -38.0, -62.0, -70.0


def clamp(v):
    return 0.0 if v < 0 else (1.0 if v > 1 else v)


def mat(ax, ay, az):
    ca, sa = math.cos(az), math.sin(az)
    cb, sb = math.cos(ay), math.sin(ay)
    cc, sc = math.cos(ax), math.sin(ax)
    # row major 3x3, built like Rig.T.rz().roty().rotx() in the module
    return np.array([
        [ca * cb, ca * sb * sc - sa * cc, ca * sb * cc + sa * sc],
        [sa * cb, sa * sb * sc + ca * cc, sa * sb * cc - ca * sc],
        [-sb, cb * sc, cb * cc],
    ])


def centre(model, u):
    arr = model["centre"]
    if len(arr) < 2:
        return 0.0
    x = max(0.0, min(len(arr) - 1.001, u / 28.4 * (len(arr) - 1)))
    i = int(x)
    t = x - i
    return arr[i] * (1 - t) + arr[i + 1] * t


def highlight(p, n, bevel):
    v = -p
    v /= max(1e-6, float(np.linalg.norm(v)))
    ndv = float(np.dot(n, v))
    if ndv <= 0:
        return 0.0
    r = 2 * ndv * n - v
    soft = max(0.0, float(np.dot(r, (-0.48, -0.58, -0.66)))) ** (22 if bevel else 16)
    across = r[0] - 0.62
    strip = math.exp(-(across ** 2) / (0.006 if bevel else 0.010)) * max(0.0, -r[2])
    l = np.array([SPX, SPY, SPZ]) - p
    ll = max(1e-6, float(np.linalg.norm(l)))
    point = max(0.0, float(np.dot(r, l)) / ll) ** (160 if bevel else 110)
    fres = (1 - ndv) ** 5
    if bevel:
        return 1.15 * soft + 0.75 * strip + 1.4 * point + 0.30 * fres
    return 0.80 * soft + 0.55 * strip + 1.1 * point + 0.05 + 0.22 * fres


def with_root(k, root):
    k = dict(k)
    k["len"] = root["len"]
    k["half"] = root["half"]
    k["zmax"] = root.get("zmax", 1.4)
    return k


class Quad:
    __slots__ = ("x", "y", "u", "v", "c", "light", "depth")


def build(model, order, unit=26.0, alpha=1.0, light=True):
    rot = mat(*order)
    n_world = rot[:, 2]
    e_world = rot[:, 0]
    quads = []

    def world(u, v, z):
        p = np.array([v * SC, (u - U0) * SC, z * SC])
        return rot @ p

    def add(pts, uv, col, light_q):
        q = Quad()
        q.x = [p[0] for p in pts]
        q.y = [p[1] for p in pts]
        q.u = [t[0] for t in uv]
        q.v = [t[1] for t in uv]
        q.c = col
        q.light = light_q
        q.depth = float(np.mean([p[2] for p in pts]))
        quads.append(q)

    half = model["half"]
    zmax = model.get("zmax", 1.4)

    for part in model["parts"]:
        t, r = part["t"], part["r"]
        cu = (part["u0"] + part["u1"]) / 2
        c = world(cu, 0.0, 0.0)
        tocam = float(np.dot(n_world, -c - np.array([0, 0, D])))
        side = 1.0 if tocam >= 0 else -1.0
        f = n_world * side
        key = max(0.0, float(np.dot(f, (LX, LY, LZ))))
        fill = max(0.0, float(np.dot(f, (FX, FY, FZ))))
        faceLum = clamp(0.40 + 0.58 * key + 0.16 * fill)
        core = max(0.0, t - r)
        slices = max(1, min(14, int(math.ceil(6 / 1.6))))
        if not light:
            slices = min(slices, 3)
        cellv = 1.0 / CELLS

        for s in range(slices):
            z = (0.0 if slices == 1 else -core + 2 * core * s / (slices - 1)) * side
            grid(model, part, z, SIDE, faceLum, faceLum, world, add, 1.6, 3, half)
        # side wall strip
        strip(model, part, world, add, cellv, zmax, alpha)
        if r > 0.02:
            grid(model, part, (t - 0.5 * r) * side, RIM13, faceLum * 0.92, faceLum * 0.92, world, add, 0.7, 6, half)
            grid(model, part, (t - 0.134 * r) * side, RIM50, faceLum * 0.97, faceLum * 0.97, world, add, 0.7, 6, half)
        grid(model, part, t * side, FACE, faceLum, -1.0, world, add, 0.7, 6, half)
        if light:
            light_layer(model, part, t * side, GLOSS, n_world, side, world, add, cellv, half, 22, 1.0)
            light_layer(model, part, t * side, EDGE, n_world, side, world, add, cellv, half, 38, 1.0)
    return quads


def grid(model, part, z, cell, lum, mode, world, add, step, nv, half):
    nu = max(1, int(math.ceil((part["u1"] - part["u0"]) / step)))
    v0, v1 = -half + 0.03, half - 0.03
    cellv = 1.0 / CELLS
    tv0, tv1 = cell * cellv, (cell + 1) * cellv
    for j in range(nv):
        for i in range(nu):
            ua = part["u0"] + (part["u1"] - part["u0"]) * i / nu
            ub = part["u0"] + (part["u1"] - part["u0"]) * (i + 1) / nu
            va = v0 + (v1 - v0) * j / nv
            vb = v0 + (v1 - v0) * (j + 1) / nv
            pts = [world(u, v, z) for u, v in ((ua, va), (ua, vb), (ub, vb), (ub, va))]
            uv = [(ua / model["len"], tv0 + (tv1 - tv0) * j / nv),
                  (ua / model["len"], tv0 + (tv1 - tv0) * (j + 1) / nv),
                  (ub / model["len"], tv0 + (tv1 - tv0) * (j + 1) / nv),
                  (ub / model["len"], tv0 + (tv1 - tv0) * j / nv)]
            add(pts, uv, [int(clamp(lum) * 255)] * 3, False)


def light_layer(model, part, z, cell, n_world, side, world, add, cellv, half, exponent, power):
    nu = max(1, int(math.ceil((part["u1"] - part["u0"]) / 0.7)))
    nv = 6
    v0, v1 = -half + 0.03, half - 0.03
    tv0, tv1 = cell * cellv, (cell + 1) * cellv
    bevel = cell == EDGE
    bc, bs = 0.788, 0.616
    e_world = np.cross(n_world, (0.0, 1.0, 0.0))
    if np.linalg.norm(e_world) < 1e-5:
        e_world = np.array([1.0, 0.0, 0.0])
    e_world /= np.linalg.norm(e_world)
    bn = n_world * bc * side + e_world * bs
    for j in range(nv):
        for i in range(nu):
            ua = part["u0"] + (part["u1"] - part["u0"]) * i / nu
            ub = part["u0"] + (part["u1"] - part["u0"]) * (i + 1) / nu
            va = v0 + (v1 - v0) * j / nv
            vb = v0 + (v1 - v0) * (j + 1) / nv
            pts = [world(u, v, z) for u, v in ((ua, va), (ua, vb), (ub, vb), (ub, va))]
            n = bn if bevel else n_world * side
            b = highlight(np.array(pts).mean(axis=0), n / np.linalg.norm(n), bevel)
            if b <= 0.01:
                continue
            uv = [(ua / model["len"], tv0 + (tv1 - tv0) * j / nv),
                  (ua / model["len"], tv0 + (tv1 - tv0) * (j + 1) / nv),
                  (ub / model["len"], tv0 + (tv1 - tv0) * (j + 1) / nv),
                  (ub / model["len"], tv0 + (tv1 - tv0) * j / nv)]
            g = int(clamp(b) * 255)
            add(pts, uv, [g] * 3, True)


def strip(model, part, world, add, cellv, zmax, alpha):
    nu = max(1, int(math.ceil((part["u1"] - part["u0"]) / 0.5)))
    zt = min(zmax, part["t"] + 0.05)
    tv0, tv1 = PROFILE * cellv, (PROFILE + 1) * cellv
    va = tv0 + (tv1 - tv0) * (0.5 - 0.5 * zt / zmax)
    vb = tv0 + (tv1 - tv0) * (0.5 + 0.5 * zt / zmax)
    for i in range(nu):
        ua = part["u0"] + (part["u1"] - part["u0"]) * i / nu
        ub = part["u0"] + (part["u1"] - part["u0"]) * (i + 1) / nu
        pts = [world(ua, centre(model, ua), -zt), world(ua, centre(model, ua), zt),
               world(ub, centre(model, ub), zt), world(ub, centre(model, ub), -zt)]
        uv = [(ua / model["len"], va), (ua / model["len"], vb),
              (ub / model["len"], vb), (ub / model["len"], va)]
        add(pts, uv, [135] * 3, False)


def raster(quads, sheet, size, pad=10, unit=26.0):
    tex = np.asarray(sheet.convert("RGBA"), dtype=np.float32)
    th, tw = tex.shape[:2]
    pts = np.array([[x, y] for q in quads for x, y in zip(q.x, q.y)])
    span = max(float(pts[:, 0].max() - pts[:, 0].min()), float(pts[:, 1].max() - pts[:, 1].min())) or 1.0
    S = (size - 2 * pad) / span
    cx = (pts[:, 0].min() + pts[:, 0].max()) / 2
    cy = (pts[:, 1].min() + pts[:, 1].max()) / 2
    out = np.zeros((size, size, 4), dtype=np.float32)
    # painted in build order, like KnifeHDGui: rims first, the lit face last on top
    for q in quads:
        scr = np.array([[size / 2 + (x - cx) * S, size / 2 - (y - cy) * S]
                        for x, y in zip(q.x, q.y)])
        x0, y0 = np.clip(np.floor(scr.min(axis=0)).astype(int), 0, size - 1)
        x1, y1 = np.clip(np.ceil(scr.max(axis=0)).astype(int), 0, size - 1)
        if x1 < x0 or y1 < y0:
            continue
        ex, ey = scr[1] - scr[0], scr[3] - scr[0]
        det = ex[0] * ey[1] - ex[1] * ey[0]
        if abs(det) < 1e-6:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        dx, dy = gx - scr[0][0], gy - scr[0][1]
        a = (dx * ey[1] - dy * ey[0]) / det
        b = (-dx * ex[1] + dy * ex[0]) / det
        inside = (a >= 0) & (a <= 1) & (b >= 0) & (b <= 1)
        if not inside.any():
            continue
        uu = q.u[0] + (q.u[3] - q.u[0]) * b
        vv = q.v[0] + (q.v[1] - q.v[0]) * a
        lo = min(q.v[0], q.v[1]) + 0.5 / th
        hi = max(q.v[0], q.v[1]) - 0.5 / th
        vv = np.clip(vv, lo, hi)
        tx = np.clip((uu * tw).astype(int), 0, tw - 1)
        ty = np.clip((vv * th).astype(int), 0, th - 1)
        samp = tex[ty, tx]
        lum = q.c[0] / 255.0
        keep = inside & (samp[..., 3] > 0.02)
        if not keep.any():
            continue
        sub = out[y0:y1 + 1, x0:x1 + 1]
        if q.light:
            add = samp[..., :3] * lum * 0.92 * (samp[..., 3:4] / 255.0)
            sub[np.broadcast_to(keep[..., None], sub.shape)] += 0
            region = sub[keep]
            region[:, :3] = np.minimum(255.0, region[:, :3] + add[keep])
            region[:, 3] = np.maximum(region[:, 3], 255.0)
            sub[keep] = region
        else:
            region = sub[keep]
            region[:, :3] = samp[keep][:, :3] * lum
            region[:, 3] = 255.0
            sub[keep] = region
    return out.astype(np.uint8)


def main():
    data = json.load(open(JSON))
    cell = 340
    out = Image.new("RGBA", (cell * 4, cell * 2), (12, 20, 38, 255))
    d = ImageDraw.Draw(out)
    order = (-0.12, -0.46, 0.36)
    for i, k in enumerate(data["knives"]):
        sheet = Image.open(os.path.join(TEX, k["id"] + "_0.png")).convert("RGBA")
        quads = build(with_root(k, data), order)
        img = Image.fromarray(raster(quads, sheet, cell), "RGBA")
        x, y = (i % 4) * cell, (i // 4) * cell
        out.alpha_composite(img, (x, y))
        d.text((x + 12, y + cell - 26), k["name"], fill=(206, 216, 234))
    os.makedirs(os.path.join(ROOT, "artifacts"), exist_ok=True)
    out.convert("RGB").save(os.path.join(ROOT, "artifacts", "hd_knives_3d.png"))
    print("preview -> artifacts/hd_knives_3d.png")


if __name__ == "__main__":
    main()
