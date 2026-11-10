#!/usr/bin/env python3
"""Renders the line sprites the ESP draws with (stroke.png) and the small marks (marks.png).

Everything an overlay line needs is baked here once, so the game draws one smooth, anti-aliased picture per line instead of
many flat rectangles: a soft line (core + halo), its dark keyline, a tapered tracer (thin and faint at the start, thick and
bright at the end), a travelling glint, a glow dot and a diamond. All of them are white with alpha; the game tints them.

stroke.png  256 x 168
    rows   0..33   soft line      (32 rows, core 4 rows thick, halo to the edge)
    rows  36..53   keyline        (16 rows, dark band 8 rows thick)
    rows  56..105  tracer taper   (48 rows, along x: alpha 0.03+0.97 f^3, half width 0.4..1.0)
    rows 108..141  glint          (32 rows, along x: tail 0 -> head 1)
    rows 144..159  hard line      (16 rows, crisp band 8 rows thick, no halo)
marks.png   256 x 64
    x   0..63   glow dot
    x  64..127  diamond outline
    x 128..191  diamond keyline
    x 192..255  filled triangle (pointing right, centroid in the middle of the cell)
"""
import numpy as np
from PIL import Image
import os, sys

SS = 4  # supersampling


def smooth(x):
    x = np.clip(x, 0.0, 1.0)
    return x * x * (3 - 2 * x)


def profile(d, core_r, halo_a, halo_s):
    """alpha across a stroke: crisp AA core of radius core_r rows, soft halo outside."""
    core = np.clip(core_r + 0.5 - d, 0.0, 1.0)
    halo = halo_a * np.exp(-(np.maximum(d - core_r, 0.0) / halo_s) ** 2)
    return np.maximum(core, halo)


def band(rows, w, fn):
    yy = (np.arange(rows * SS) + 0.5) / SS
    d = np.abs(yy - rows / 2.0)
    xx = (np.arange(w * SS) + 0.5) / SS
    a = fn(xx[None, :], d[:, None])
    a = a.reshape(rows, SS, w, SS).mean(axis=(1, 3))
    return a


def make_stroke():
    W, H = 256, 168
    img = np.zeros((H, W), dtype=np.float32)
    # soft line
    img[0:32, :] = band(32, W, lambda x, d: profile(d + 0 * x, 2.0, 0.20, 5.5) * (d < 15.9))
    # keyline
    img[36:52, :] = band(16, W, lambda x, d: 0.62 * (1 - smooth((d - 3.0) / 3.2)) + 0 * x)
    # tracer taper
    def taper(x, d):
        f = x / W
        r = 2.0 * (0.4 + 0.6 * f)
        a = profile(d, r, 0.22, 5.0 + 0 * f)
        return a * (0.03 + 0.97 * f ** 3)
    img[56:104, :] = band(48, W, taper)
    # glint: bright head on the right, fading to nothing on the left
    def glint(x, d):
        f = x / W
        r = 1.7 + 0.5 * f
        a = profile(d, r, 0.38, 5.0)
        return a * np.clip(f, 0, 1) ** 1.6
    img[108:140, :] = band(32, W, glint)
    # hard line: a crisp anti-aliased band with no halo, 8 rows thick in 16 rows (flat 1.5 px tracer look)
    img[144:160, :] = band(16, W, lambda x, d: np.clip(4.0 + 0.5 - d, 0.0, 1.0) + 0 * x)
    out = np.zeros((H, W, 4), dtype=np.uint8)
    out[..., :3] = 255
    out[..., 3] = np.clip(img * 255 + 0.5, 0, 255).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def make_marks():
    W, H = 256, 64
    img = np.zeros((H * SS, W * SS), dtype=np.float32)
    yy, xx = np.mgrid[0:H * SS, 0:W * SS]
    xx = (xx + 0.5) / SS
    yy = (yy + 0.5) / SS
    # glow dot
    cx, cy = 32, 32
    r = np.hypot(xx - cx, yy - cy)
    dot = np.clip(1.0 - smooth((r - 3.0) / 3.0), 0, 1) * 0.95 + 0.30 * np.exp(-(r / 10.5) ** 2)
    m = xx < 64
    img[m] = 0
    img = np.where(m, np.clip(dot, 0, 1), img)
    # diamond outline (L1 distance), radius 22, 3.6 thick
    def diamond(cx, thick, R=22.0):
        l1 = np.abs(xx - cx) + np.abs(yy - 32)
        # L1 distance to the outline, scaled to real distance
        dd = np.abs(l1 - R) / 1.4142
        return np.clip(thick / 2.0 + 0.5 - dd, 0, 1)
    m2 = (xx >= 64) & (xx < 128)
    img = np.where(m2, diamond(96, 3.6), img)
    # keyline: wider and soft
    m3 = xx >= 128
    l1 = np.abs(xx - 160) + np.abs(yy - 32)
    dd = np.abs(l1 - 22.0) / 1.4142
    key = 0.7 * (1 - smooth((dd - 3.2) / 3.0))
    img = np.where(m3, key, img)
    # filled triangle pointing right, centroid at the middle of its cell (the off-screen arrow)
    m4 = xx >= 192
    s_ = 22.0
    v = [(-0.57735 * s_, -s_), (1.1547 * s_, 0.0), (-0.57735 * s_, s_)]
    px, py = xx - 224, yy - 32

    def edge(ax, ay, bx, by):
        return (px - ax) * (by - ay) - (py - ay) * (bx - ax)
    e1 = edge(*v[0], *v[1])
    e2 = edge(*v[1], *v[2])
    e3 = edge(*v[2], *v[0])
    inside = ((e1 >= 0) & (e2 >= 0) & (e3 >= 0)) | ((e1 <= 0) & (e2 <= 0) & (e3 <= 0))
    img = np.where(m4, inside.astype(np.float32), img)
    a = img.reshape(H, SS, W, SS).mean(axis=(1, 3))
    out = np.zeros((H, W, 4), dtype=np.uint8)
    out[..., :3] = 255
    out[..., 3] = np.clip(a * 255 + 0.5, 0, 255).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def make_tri():
    """tri.png 128 x 128: a solid right triangle (1,1) (127,1) (1,127) with a one pixel soft edge. The game maps it onto any
    triangle, so a flat tile on the ground is two of them."""
    N = 128 * SS
    ys, xs = np.mgrid[0:N, 0:N]
    x = (xs + 0.5) / SS
    y = (ys + 0.5) / SS
    inside = (x >= 1) & (y >= 1) & (x + y <= 128)
    a = inside.astype(np.float32).reshape(128, SS, 128, SS).mean(axis=(1, 3))
    out = np.zeros((128, 128, 4), np.uint8)
    out[..., :3] = 255
    out[..., 3] = np.clip(a * 255 + 0.5, 0, 255).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


if __name__ == "__main__":
    dest = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/zeus/textures/gui/anim"
    os.makedirs(dest, exist_ok=True)
    make_stroke().save(os.path.join(dest, "stroke.png"), optimize=True)
    make_marks().save(os.path.join(dest, "marks.png"), optimize=True)
    make_tri().save(os.path.join(dest, "tri.png"), optimize=True)
    print("ok")
