#!/usr/bin/env python3
"""Renders the lightning animations of the Zeus interface into sprite sheets, so the game only has to show a picture.

    python3 tools/anim/make_arcs.py src/main/resources/assets/zeus/textures/gui/anim

Every sheet is white with an alpha channel, so the game can tint it with the accent colour. Two layers per animation
(a wide glow and a thin white-hot core) are drawn one on top of the other with different tints.

  arc_glow.png / arc_core.png     16 frames of 512 x 64, 4 x 4: an energy arc that flows along a button edge (loops)
  burst_glow.png / burst_core.png 16 frames of 192 x 192, 4 x 4: bolts leaping out of a switch (plays once)
  ring_glow.png / ring_core.png   16 frames of 160 x 160, 4 x 4: an arc that circles a rounded square button (loops)
"""
import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

SS = 3  # supersampling


def smooth_noise(n, step, rng):
    """Band-limited random values in [-1, 1] for n samples, a new value every `step` samples."""
    pts = [rng.uniform(-1, 1) for _ in range(n // step + 3)]
    out = np.zeros(n)
    for i in range(n):
        a = i / step
        j = int(a)
        f = a - j
        f = f * f * (3 - 2 * f)
        out[i] = pts[j] * (1 - f) + pts[j + 1] * f
    return out


def arc_frame(f, frames, W, H):
    t = f / frames
    rng = random.Random(1000 + f * 7)
    w, h = W * SS, H * SS
    glow = Image.new("L", (w, h), 0)
    core = Image.new("L", (w, h), 0)
    gd = ImageDraw.Draw(glow)
    cd = ImageDraw.Draw(core)
    n = W
    xs = np.arange(n) / (n - 1)
    env = np.sin(np.pi * xs) ** 0.7
    strands = 3
    paths = []
    for s in range(strands):
        ph = s * 2.1
        base = (np.sin(2 * np.pi * (1.4 * xs + t * (1 + s)) + ph) * 0.62
                + np.sin(2 * np.pi * (3.3 * xs - t * 2 + ph * 1.7)) * 0.38)
        jag = smooth_noise(n, 9 + 3 * s, rng) * 0.35 + smooth_noise(n, 4, rng) * 0.18
        y = H / 2 + (base * (H * 0.22 - s * 1.2) + jag * (H * 0.13)) * env
        paths.append(y)
    # two bright pulses travel along the arc, so it reads as current flowing
    def pulse(u):
        v = 0.0
        for c in (t, (t + 0.5) % 1.0):
            d = abs(u - c)
            d = min(d, 1 - d)
            v += math.exp(-(d * d) / 0.012)
        return v

    for s, y in enumerate(paths):
        weight = 1.0 - s * 0.22
        for i in range(n - 1):
            u = xs[i]
            b = (0.35 + 0.65 * min(1.0, pulse(u))) * weight * (env[i] ** 0.8)
            v = int(255 * min(1.0, b))
            p0 = (xs[i] * W * SS, y[i] * SS)
            p1 = (xs[i + 1] * W * SS, y[i + 1] * SS)
            gd.line([p0, p1], fill=min(255, int(v * 0.9)), width=int(6.0 * SS))
            cd.line([p0, p1], fill=v, width=max(1, int(1.7 * SS * (1.0 if s == 0 else 0.75))))
    # little forks that jump off the arc and live for a frame or two
    for _ in range(rng.randint(2, 4)):
        i = rng.randint(int(n * 0.12), int(n * 0.88))
        s = rng.randint(0, strands - 1)
        x0, y0 = xs[i] * W, paths[s][i]
        ang = rng.choice([-1, 1]) * rng.uniform(0.7, 1.5)
        length = rng.uniform(5, 11)
        pts = [(x0, y0)]
        cx, cy = x0, y0
        for _k in range(3):
            ang += rng.uniform(-0.7, 0.7)
            cx += math.sin(ang) * length / 3 * 1.4
            cy += -math.cos(ang) * length / 3
            pts.append((cx, cy))
        sc = [(px * SS, py * SS) for px, py in pts]
        gd.line(sc, fill=150, width=int(4 * SS))
        cd.line(sc, fill=235, width=max(1, int(1.3 * SS)))
    g2 = glow.filter(ImageFilter.GaussianBlur(5.2 * SS))
    g1 = glow.filter(ImageFilter.GaussianBlur(1.8 * SS))
    ga = np.minimum(255, np.asarray(g2, dtype=np.float32) * 1.7 + np.asarray(g1, dtype=np.float32) * 0.45)
    c2 = np.asarray(core.filter(ImageFilter.GaussianBlur(0.6 * SS)), dtype=np.float32)
    glow_img = Image.fromarray(ga.astype(np.uint8), "L").resize((W, H), Image.LANCZOS)
    core_img = Image.fromarray(np.minimum(255, c2 * 1.15).astype(np.uint8), "L").resize((W, H), Image.LANCZOS)
    return glow_img, core_img


def bolt_path(rng, x0, y0, x1, y1, rough, levels=5):
    pts = [(x0, y0), (x1, y1)]
    disp = rough
    for _ in range(levels):
        out = [pts[0]]
        for a, b in zip(pts, pts[1:]):
            mx, my = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
            dx, dy = b[0] - a[0], b[1] - a[1]
            ln = math.hypot(dx, dy) or 1
            nx, ny = -dy / ln, dx / ln
            o = rng.uniform(-1, 1) * disp
            out.append((mx + nx * o, my + ny * o))
            out.append(b)
        pts = out
        disp *= 0.55
    return pts


def burst_frame(f, frames, S):
    p = f / (frames - 1)
    w = S * SS
    glow = Image.new("L", (w, w), 0)
    core = Image.new("L", (w, w), 0)
    gd = ImageDraw.Draw(glow)
    cd = ImageDraw.Draw(core)
    c = S / 2
    rng0 = random.Random(77)
    reveal = 1 - (1 - min(1.0, p * 2.4)) ** 3
    fade = 1.0 if p < 0.3 else max(0.0, 1 - (p - 0.3) / 0.7)
    bolts = 8
    for b in range(bolts):
        ang = b / bolts * 2 * math.pi + rng0.uniform(-0.25, 0.25)
        reach = S * rng0.uniform(0.34, 0.47)
        rng = random.Random(500 + b * 13 + f // 2)  # re-rolls every other frame: a crackle
        end = (c + math.cos(ang) * reach, c + math.sin(ang) * reach)
        pts = bolt_path(rng, c, c, end[0], end[1], reach * 0.16)
        cut = max(2, int(len(pts) * reveal))
        sc = [(x * SS, y * SS) for x, y in pts[:cut]]
        if len(sc) >= 2:
            gd.line(sc, fill=int(190 * fade), width=int(5.0 * SS))
            cd.line(sc, fill=int(255 * fade), width=max(1, int(1.6 * SS)))
            if b % 2 == 0 and cut > len(pts) // 2:
                m = pts[len(pts) // 2]
                a2 = ang + (0.7 if b % 4 == 0 else -0.7)
                e2 = (m[0] + math.cos(a2) * reach * 0.35, m[1] + math.sin(a2) * reach * 0.35)
                fk = bolt_path(rng, m[0], m[1], e2[0], e2[1], reach * 0.07, 3)
                fs = [(x * SS, y * SS) for x, y in fk]
                gd.line(fs, fill=int(120 * fade), width=int(3 * SS))
                cd.line(fs, fill=int(210 * fade), width=max(1, int(1.1 * SS)))
    # flash and shock ring
    fl = max(0.0, 1 - p / 0.35)
    r0 = S * 0.06 + S * 0.1 * min(1.0, p * 3)
    gd.ellipse([(c - r0) * SS, (c - r0) * SS, (c + r0) * SS, (c + r0) * SS], fill=int(255 * fl))
    cd.ellipse([(c - r0 * 0.5) * SS, (c - r0 * 0.5) * SS, (c + r0 * 0.5) * SS, (c + r0 * 0.5) * SS], fill=int(255 * fl))
    rr = S * (0.08 + 0.38 * (1 - (1 - min(1.0, p * 1.6)) ** 2))
    gd.ellipse([(c - rr) * SS, (c - rr) * SS, (c + rr) * SS, (c + rr) * SS], outline=int(150 * max(0.0, 1 - p * 1.3)), width=int(2.4 * SS))
    g2 = glow.filter(ImageFilter.GaussianBlur(4.5 * SS))
    g1 = glow.filter(ImageFilter.GaussianBlur(1.4 * SS))
    ga = np.minimum(255, np.asarray(g2, dtype=np.float32) * 1.8 + np.asarray(g1, dtype=np.float32) * 0.5)
    ca = np.asarray(core.filter(ImageFilter.GaussianBlur(0.5 * SS)), dtype=np.float32)
    return (Image.fromarray(ga.astype(np.uint8), "L").resize((S, S), Image.LANCZOS),
            Image.fromarray(np.minimum(255, ca * 1.1).astype(np.uint8), "L").resize((S, S), Image.LANCZOS))


def rounded_path(size, inset, radius, n=720):
    """Points along the outline of a rounded square, starting at the top middle, clockwise."""
    pts = []
    a = inset
    b = size - inset
    r = radius
    # build the outline as a list of segments, then sample it evenly
    segs = []
    segs.append(("L", (size / 2, a), (b - r, a)))
    segs.append(("A", (b - r, a + r), -90, 0))
    segs.append(("L", (b, a + r), (b, b - r)))
    segs.append(("A", (b - r, b - r), 0, 90))
    segs.append(("L", (b - r, b), (a + r, b)))
    segs.append(("A", (a + r, b - r), 90, 180))
    segs.append(("L", (a, b - r), (a, a + r)))
    segs.append(("A", (a + r, a + r), 180, 270))
    segs.append(("L", (a + r, a), (size / 2, a)))
    dense = []
    for sg in segs:
        if sg[0] == "L":
            (x0, y0), (x1, y1) = sg[1], sg[2]
            m = max(2, int(math.hypot(x1 - x0, y1 - y0)))
            for i in range(m):
                t = i / m
                dense.append((x0 + (x1 - x0) * t, y0 + (y1 - y0) * t))
        else:
            (cx, cy), a0, a1 = sg[1], sg[2], sg[3]
            m = max(2, int(r * math.radians(abs(a1 - a0))))
            for i in range(m):
                ang = math.radians(a0 + (a1 - a0) * i / m)
                dense.append((cx + math.cos(ang) * r, cy + math.sin(ang) * r))
    # resample to n points by index
    out = []
    for i in range(n):
        out.append(dense[int(i * len(dense) / n) % len(dense)])
    return out


def ring_frame(f, frames, S):
    t = f / frames
    rng = random.Random(4000 + f * 11)
    w = S * SS
    glow = Image.new("L", (w, w), 0)
    core = Image.new("L", (w, w), 0)
    gd = ImageDraw.Draw(glow)
    cd = ImageDraw.Draw(core)
    n = 720
    path = rounded_path(S, S * 0.16, S * 0.68 * 0.30, n)
    # normals for the little sideways jitter
    def normal(i):
        x0, y0 = path[(i - 2) % n]
        x1, y1 = path[(i + 2) % n]
        dx, dy = x1 - x0, y1 - y0
        ln = math.hypot(dx, dy) or 1
        return -dy / ln, dx / ln
    jit = smooth_noise(n, 7, rng) * 2.3 + smooth_noise(n, 3, rng) * 1.0
    comets = [(t, 0.34, 1.0), ((t + 0.5) % 1.0, 0.22, 0.75)]
    for head, length, power in comets:
        m = int(n * length)
        h0 = int(head * n)
        prev = None
        for j in range(m):
            i = (h0 - j) % n
            u = 1 - j / m  # 1 at the head, 0 at the tail
            nx, ny = normal(i)
            o = jit[i] * (0.5 + 0.5 * u)
            px, py = path[i][0] + nx * o, path[i][1] + ny * o
            v = int(255 * min(1.0, (u ** 1.4) * power + 0.0))
            pt = (px * SS, py * SS)
            if prev is not None:
                gd.line([prev, pt], fill=int(v * 0.85), width=int(10.0 * SS))
                cd.line([prev, pt], fill=v, width=max(1, int(3.4 * SS)))
            prev = pt
        # a little spark leaping off the head
        i = h0 % n
        nx, ny = normal(i)
        ang = math.atan2(ny, nx) + rng.uniform(-0.5, 0.5)
        x0, y0 = path[i]
        pts = [(x0, y0)]
        cx, cy = x0, y0
        for _k in range(3):
            ang += rng.uniform(-0.8, 0.8)
            cx += math.cos(ang) * 6.0
            cy += math.sin(ang) * 6.0
            pts.append((cx, cy))
        sc = [(px * SS, py * SS) for px, py in pts]
        gd.line(sc, fill=int(190 * power), width=int(7 * SS))
        cd.line(sc, fill=int(255 * power), width=max(1, int(2.6 * SS)))
    # a faint crackle all round, so the outline stays alive between the comets
    prev = None
    for i in range(0, n, 2):
        nx, ny = normal(i)
        o = jit[i] * 0.6
        pt = ((path[i][0] + nx * o) * SS, (path[i][1] + ny * o) * SS)
        if prev is not None and rng.random() > 0.35:
            cd.line([prev, pt], fill=90, width=max(1, int(2.0 * SS)))
        prev = pt
    g2 = glow.filter(ImageFilter.GaussianBlur(6.5 * SS))
    g1 = glow.filter(ImageFilter.GaussianBlur(2.4 * SS))
    ga = np.minimum(255, np.asarray(g2, dtype=np.float32) * 1.8 + np.asarray(g1, dtype=np.float32) * 0.5)
    ca = np.asarray(core.filter(ImageFilter.GaussianBlur(0.5 * SS)), dtype=np.float32)
    return (Image.fromarray(ga.astype(np.uint8), "L").resize((S, S), Image.LANCZOS),
            Image.fromarray(np.minimum(255, ca * 1.1).astype(np.uint8), "L").resize((S, S), Image.LANCZOS))


def sheet(frames_l, fw, fh, cols):
    rows = (len(frames_l) + cols - 1) // cols
    out = Image.new("RGBA", (fw * cols, fh * rows), (255, 255, 255, 0))
    for i, fr in enumerate(frames_l):
        white = Image.new("RGBA", (fw, fh), (255, 255, 255, 255))
        white.putalpha(fr)
        out.paste(white, ((i % cols) * fw, (i // cols) * fh))
    return out


def main():
    dest = sys.argv[1] if len(sys.argv) > 1 else "anim"
    os.makedirs(dest, exist_ok=True)
    n = 16
    glow, core = [], []
    for f in range(n):
        a, b = arc_frame(f, n, 512, 64)
        glow.append(a)
        core.append(b)
    sheet(glow, 512, 64, 4).save(os.path.join(dest, "arc_glow.png"), optimize=True)
    sheet(core, 512, 64, 4).save(os.path.join(dest, "arc_core.png"), optimize=True)
    glow, core = [], []
    for f in range(n):
        a, b = burst_frame(f, n, 192)
        glow.append(a)
        core.append(b)
    sheet(glow, 192, 192, 4).save(os.path.join(dest, "burst_glow.png"), optimize=True)
    sheet(core, 192, 192, 4).save(os.path.join(dest, "burst_core.png"), optimize=True)
    glow, core = [], []
    for f in range(n):
        a, b = ring_frame(f, n, 160)
        glow.append(a)
        core.append(b)
    sheet(glow, 160, 160, 4).save(os.path.join(dest, "ring_glow.png"), optimize=True)
    sheet(core, 160, 160, 4).save(os.path.join(dest, "ring_core.png"), optimize=True)
    print("done", dest)


if __name__ == "__main__":
    main()
