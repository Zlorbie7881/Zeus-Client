#!/usr/bin/env python3
"""Renders the menu animations (the stone plates, the round buttons, the click flash) into sprite sheets.

    python3 tools/anim/make_plate.py src/main/resources/assets/zeus/textures/gui/anim

All sheets are white with an alpha channel; the game tints them. The game only picks a frame and draws it.

  plate_glow.png / plate_core.png  24 frames of 512 x 96, 4 columns: two arcs that chase each other round the edge of a
                                   plate 480 x 64 (rounded corners, 10 px). Drawn in three pieces (left end, middle, right end)
                                   so any plate width works. Loops.
  circ_glow.png / circ_core.png    24 frames of 160 x 160, 4 columns: two arcs that circle a round button (radius 56). Loops.
  click.png                        16 frames of 512 x 256, 4 columns: shock waves and bolts from the middle of a click. Plays once.
"""
import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

SS = 3


def vnoise(n, step, rng):
    pts = [rng.uniform(-1, 1) for _ in range(n // step + 3)]
    out = np.zeros(n)
    for i in range(n):
        a = i / step
        j = int(a)
        f = a - j
        f = f * f * (3 - 2 * f)
        out[i] = pts[j] * (1 - f) + pts[j + 1] * f
    return out


# ---------------------------------------------------------------- paths

def plate_path(W, H, M, r):
    """Points along the outline of a rounded rectangle, clockwise from the top left end of the top edge.
    Returns (pts, normals, perimeter) sampled about every 1.5 px."""
    x0, y0, x1, y1 = M, M, W - M, H - M
    segs = []
    def line(ax, ay, bx, by, nx, ny):
        segs.append(("l", ax, ay, bx, by, nx, ny))
    def arc(cx, cy, a0):
        segs.append(("a", cx, cy, a0))
    line(x0 + r, y0, x1 - r, y0, 0, -1)
    arc(x1 - r, y0 + r, -90)
    line(x1, y0 + r, x1, y1 - r, 1, 0)
    arc(x1 - r, y1 - r, 0)
    line(x1 - r, y1, x0 + r, y1, 0, 1)
    arc(x0 + r, y1 - r, 90)
    line(x0, y1 - r, x0, y0 + r, -1, 0)
    arc(x0 + r, y0 + r, 180)
    pts, nrm = [], []
    for s in segs:
        if s[0] == "l":
            _, ax, ay, bx, by, nx, ny = s
            L = math.hypot(bx - ax, by - ay)
            n = max(1, int(L / 1.5))
            for i in range(n):
                f = i / n
                pts.append((ax + (bx - ax) * f, ay + (by - ay) * f))
                nrm.append((nx, ny))
        else:
            _, cx, cy, a0 = s
            L = math.pi / 2 * r
            n = max(2, int(L / 1.5))
            for i in range(n):
                a = math.radians(a0 + 90 * i / n)
                pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
                nrm.append((math.cos(a), math.sin(a)))
    return pts, nrm, len(pts)


def circle_path(cx, cy, r):
    n = int(2 * math.pi * r / 1.5)
    pts, nrm = [], []
    for i in range(n):
        a = 2 * math.pi * i / n - math.pi / 2
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
        nrm.append((math.cos(a), math.sin(a)))
    return pts, nrm, n


def bloom(shape, cx, cy, rad, power=1.0):
    h, w = shape
    yy, xx = np.mgrid[0:h, 0:w]
    d2 = ((xx + 0.5) / SS - cx) ** 2 + ((yy + 0.5) / SS - cy) ** 2
    return np.exp(-d2 / (rad * rad)) * power


def run_frame(pts, nrm, n, W, H, f, frames, jag_amp, width_core, width_glow, trail_frac, heads=2, seed=0):
    rng = random.Random(seed * 100003 + f * 7919)
    gl = Image.new("L", (W * SS, H * SS), 0)
    co = Image.new("L", (W * SS, H * SS), 0)
    gd, cd = ImageDraw.Draw(gl), ImageDraw.Draw(co)
    bl = np.zeros((H * SS, W * SS), dtype=np.float32)
    trail = int(n * trail_frac)
    noise = vnoise(n + 4, 7, rng) * 0.7 + vnoise(n + 4, 3, rng) * 0.45
    for hd in range(heads):
        head = int(((f / frames) + hd / heads) * n) % n
        prev = None
        for i in range(trail + 1):
            q = i / trail                      # 0 tail .. 1 head
            idx = (head - trail + i) % n
            taper = math.sin(min(1.0, q * 1.12) * math.pi / 2)
            off = noise[idx] * jag_amp * (0.35 + 0.65 * taper)
            px = pts[idx][0] + nrm[idx][0] * off
            py = pts[idx][1] + nrm[idx][1] * off
            if prev is not None:
                a = q ** 1.35
                p0 = (prev[0] * SS, prev[1] * SS)
                p1 = (px * SS, py * SS)
                gd.line([p0, p1], fill=int(255 * a * 0.85), width=max(1, int(width_glow * (0.4 + 0.6 * q) * SS)))
                cd.line([p0, p1], fill=int(255 * min(1.0, a * 1.15)), width=max(1, int(width_core * (0.45 + 0.55 * q) * SS)))
            prev = (px, py)
            # forks leaping outward off the channel
            if q > 0.35 and i % 9 == 0 and rng.random() < 0.28:
                reach = rng.uniform(7, 15)
                side = rng.uniform(-6, 6)
                fx = px + nrm[idx][0] * reach - nrm[idx][1] * side
                fy = py + nrm[idx][1] * reach + nrm[idx][0] * side
                mx, my = (px + fx) / 2 + rng.uniform(-2, 2), (py + fy) / 2 + rng.uniform(-2, 2)
                for (a0, a1) in (((px, py), (mx, my)), ((mx, my), (fx, fy))):
                    gd.line([(a0[0] * SS, a0[1] * SS), (a1[0] * SS, a1[1] * SS)], fill=int(255 * q * 0.7), width=int(3 * SS))
                    cd.line([(a0[0] * SS, a0[1] * SS), (a1[0] * SS, a1[1] * SS)], fill=int(255 * q * 0.9), width=max(1, int(0.9 * SS)))
        hx, hy = prev
        bl += bloom(bl.shape, hx, hy, 9.5, 0.95).astype(np.float32)
        # sparks around the head
        for _ in range(5):
            a = rng.uniform(0, 2 * math.pi)
            d = rng.uniform(3, 12)
            sx, sy = hx + math.cos(a) * d, hy + math.sin(a) * d
            r = rng.uniform(0.5, 1.1)
            cd.ellipse([(sx - r) * SS, (sy - r) * SS, (sx + r) * SS, (sy + r) * SS], fill=int(255 * rng.uniform(0.55, 1.0)))
    gl = gl.filter(ImageFilter.GaussianBlur(2.6 * SS))
    garr = np.asarray(gl, dtype=np.float32) / 255.0
    garr = np.clip(garr * 2.1 + bl * 0.9, 0, 1)
    carr = np.asarray(co, dtype=np.float32) / 255.0
    cblur = np.asarray(co.filter(ImageFilter.GaussianBlur(0.9 * SS)), dtype=np.float32) / 255.0
    carr = np.clip(np.maximum(carr, cblur * 0.9) + bloom(bl.shape, hx, hy, 3.2, 0.9) * 1.0, 0, 1)
    def down(a):
        a = a.reshape(H, SS, W, SS).mean(axis=(1, 3))
        return (np.clip(a, 0, 1) * 255 + 0.5).astype(np.uint8)
    return down(garr), down(carr)


def to_sheet(frames_list, W, H, cols):
    n = len(frames_list)
    rows = (n + cols - 1) // cols
    out = np.zeros((rows * H, cols * W, 4), dtype=np.uint8)
    out[..., :3] = 255
    for i, a in enumerate(frames_list):
        r, c = divmod(i, cols)
        out[r * H:(r + 1) * H, c * W:(c + 1) * W, 3] = a
    return Image.fromarray(out, "RGBA")


def make_plate(dest):
    W, H, M, R, FR = 512, 96, 16, 10, 24
    pts, nrm, n = plate_path(W, H, M, R)
    g, c = [], []
    for f in range(FR):
        a, b = run_frame(pts, nrm, n, W, H, f, FR, 2.4, 1.5, 6.0, 0.30, 2, 1)
        g.append(a)
        c.append(b)
    to_sheet(g, W, H, 4).save(os.path.join(dest, "plate_glow.png"), optimize=True)
    to_sheet(c, W, H, 4).save(os.path.join(dest, "plate_core.png"), optimize=True)


def make_circ(dest):
    W = H = 160
    FR = 24
    pts, nrm, n = circle_path(80, 80, 56)
    g, c = [], []
    for f in range(FR):
        a, b = run_frame(pts, nrm, n, W, H, f, FR, 2.0, 1.6, 6.0, 0.30, 2, 2)
        g.append(a)
        c.append(b)
    to_sheet(g, W, H, 4).save(os.path.join(dest, "circ_glow.png"), optimize=True)
    to_sheet(c, W, H, 4).save(os.path.join(dest, "circ_core.png"), optimize=True)


def bolt_path(x0, y0, x1, y1, rng, levels=5, amp=0.18):
    pts = [(x0, y0), (x1, y1)]
    a = math.hypot(x1 - x0, y1 - y0) * amp
    for _ in range(levels):
        nxt = [pts[0]]
        for (ax, ay), (bx, by) in zip(pts[:-1], pts[1:]):
            mx, my = (ax + bx) / 2, (ay + by) / 2
            dx, dy = bx - ax, by - ay
            L = math.hypot(dx, dy) or 1.0
            o = rng.uniform(-1, 1) * a
            nxt.append((mx - dy / L * o, my + dx / L * o))
            nxt.append((bx, by))
        pts = nxt
        a *= 0.55
    return pts


def make_click(dest):
    W, H, FR = 512, 256, 16
    cx, cy = W / 2, H / 2
    rng = random.Random(4242)
    # nine bolts, fixed for the whole animation, flickering by drawing different parts of them per frame
    bolts = []
    for i in range(9):
        ang = i / 9 * 2 * math.pi + rng.uniform(-0.25, 0.25)
        reach = rng.uniform(0.55, 0.95)
        ex = cx + math.cos(ang) * reach * W * 0.5
        ey = cy + math.sin(ang) * reach * H * 0.5
        sx = cx + math.cos(ang) * 30
        sy = cy + math.sin(ang) * 10
        bolts.append(bolt_path(sx, sy, ex, ey, rng))
    frames = []
    for f in range(FR):
        p = f / (FR - 1)
        im = Image.new("L", (W * SS, H * SS), 0)
        d = ImageDraw.Draw(im)
        fl = p / 0.16 if p < 0.16 else (1 - (p - 0.16) / 0.84) ** 2.2
        # shock waves
        for r in range(2):
            q = (p - r * 0.10) / 0.62
            if 0 < q < 1:
                e = 1 - (1 - q) ** 2
                rx = e * W * 0.47 * (1 - 0.18 * r)
                ry = rx * 0.55
                a = (1 - q) ** 2
                wd = max(1.0, 5.0 - 3.5 * q)
                d.ellipse([(cx - rx) * SS, (cy - ry) * SS, (cx + rx) * SS, (cy + ry) * SS], outline=int(255 * 0.8 * a), width=max(1, int(wd * SS)))
        # bolts grow quickly, then fade
        grow = min(1.0, p / 0.22)
        fade = max(0.0, 1 - p * 1.15)
        jit = random.Random(900 + f // 2)
        for pts in bolts:
            k = max(2, int(len(pts) * grow))
            seg = pts[:k]
            for a0, a1 in zip(seg[:-1], seg[1:]):
                jx, jy = jit.uniform(-0.8, 0.8), jit.uniform(-0.8, 0.8)
                d.line([((a0[0] + jx) * SS, (a0[1] + jy) * SS), ((a1[0] + jx) * SS, (a1[1] + jy) * SS)], fill=int(255 * fade), width=max(1, int((1.5 + 0.6 * (1 - p)) * SS)))
        arr = np.asarray(im, dtype=np.float32) / 255.0
        glow = np.asarray(im.filter(ImageFilter.GaussianBlur(3.2 * SS)), dtype=np.float32) / 255.0
        arr = np.clip(arr + glow * 1.5 + bloom(arr.shape, cx, cy, 70, 0.55 * fl) + bloom(arr.shape, cx, cy, 24, 0.9 * fl), 0, 1)
        a = arr.reshape(H, SS, W, SS).mean(axis=(1, 3))
        frames.append((np.clip(a, 0, 1) * 255 + 0.5).astype(np.uint8))
    to_sheet(frames, W, H, 4).save(os.path.join(dest, "click.png"), optimize=True)


if __name__ == "__main__":
    dest = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/zeus/textures/gui/anim"
    os.makedirs(dest, exist_ok=True)
    make_plate(dest)
    make_circ(dest)
    make_click(dest)
    print("ok")
