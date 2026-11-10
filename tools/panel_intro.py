#!/usr/bin/env python3
"""
Renders the Zeus panel intro: a 5 second film that plays when the Zeus panel is opened (the Z key).

The scene (nothing from the game is used, only the Zeus logo):
   0.0 - 0.3 s   pure black
   0.3 - 1.1 s   a storm that is only seen by its lightning: sheet lightning in the clouds and two distant bolts that sit
                 behind the nearer clouds; the dark slowly turns blue
   1.1 - 1.6 s   a stepped leader grows down out of the cloud in steps, forking as it comes, and a few streamers rise to meet it
   1.62 s        the return stroke: one large bolt strikes the top of the medallion, with re-strikes; the flash lights the
                 clouds, the shock wave blows them open and the medallion appears in the light
   1.8 - 2.9 s   two arcs of electricity run round the rim from the point of impact and close the ring (it has tick marks)
   2.7 - 3.9 s   a streak of light opens and the lettering is revealed from the middle outwards
   3.8 - 4.4 s   a last shine runs over the logo
   4.3 - 5.0 s   the camera pushes in, a flash, black

  python3 tools/panel_intro.py OUT_DIR [--frames A:B]      writes PNG frames at 1920 x 1080 (30 per second)
  python3 tools/panel_intro.py --audio OUT.wav             writes the sound (44.1 kHz stereo)

The in-game copy is made from the frames (1024 x 576 jpg) and the sound (ogg); see the README.
"""
import math
import os
import sys

import cv2
import numpy as np

cv2.setNumThreads(1)

W, H = 1920, 1080
FPS = 30
DUR = 5.0
N = int(DUR * FPS)
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOGO = os.path.join(ROOT, "src/main/resources/assets/zeus/textures/gui/menu/logo.png")

# the logo picture is 920 x 830: the medallion (centre 445, 326, radius about 313) above the lettering that starts at row 650
MED_BOX = (123, 4, 767, 648)        # x0, y0, x1, y1 of the square round the medallion
MED_R = 313.0
LET_Y0 = 649

F32 = np.float32
BLUE = np.array([0.36, 0.67, 1.0], F32)         # RGB
ICE = np.array([0.76, 0.90, 1.0], F32)
WHITE = np.ones(3, F32)
DEEP = np.array([0.016, 0.026, 0.058], F32)
GLOW = np.array([0.40, 0.56, 1.0], F32)         # the wide glow of lightning: blue with a trace of violet

BURST = 1.62


def sm(a, b, x):
    x = np.clip((x - a) / (b - a), 0.0, 1.0)
    return x * x * (3 - 2 * x)


def ease(x):
    x = min(1.0, max(0.0, x))
    return x * x * (3 - 2 * x)


def ease_out(x):
    x = min(1.0, max(0.0, x))
    return 1 - (1 - x) ** 3


def ease_in(x):
    x = min(1.0, max(0.0, x))
    return x ** 3


def seg(t, a, b):
    return min(1.0, max(0.0, (t - a) / (b - a)))


def hsh(a, b=0):
    h = (int(a) * 374761393 + int(b) * 668265263) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    h ^= h >> 16
    return (h & 0xFFFFFF) / float(0x1000000)


def zoom_about(img, z, cx, cy, tx=0.0, ty=0.0, border=cv2.BORDER_REFLECT, flags=cv2.INTER_LINEAR):
    """out(p) = img(c + (p - c) / z + t): the picture magnified by z round (cx, cy)."""
    M = np.array([[1 / z, 0, cx - cx / z + tx], [0, 1 / z, cy - cy / z + ty]], np.float64)
    return cv2.warpAffine(img, M, (img.shape[1], img.shape[0]), flags=flags | cv2.WARP_INVERSE_MAP, borderMode=border)


def over(img, col, a, x, y):
    """Puts a premultiplied picture (col, a) over img at (x, y), clipped to the frame."""
    h, w = a.shape[:2]
    x0, y0 = max(0, x), max(0, y)
    x1, y1 = min(W, x + w), min(H, y + h)
    if x1 <= x0 or y1 <= y0:
        return
    sub = img[y0:y1, x0:x1]
    sub *= (1.0 - a[y0 - y:y1 - y, x0 - x:x1 - x])[..., None]
    sub += col[y0 - y:y1 - y, x0 - x:x1 - x]


def add(img, layer, x, y, colour, gain=1.0):
    """Adds a single channel layer (tinted) into img at (x, y), clipped to the frame."""
    h, w = layer.shape[:2]
    x0, y0 = max(0, x), max(0, y)
    x1, y1 = min(W, x + w), min(H, y + h)
    if x1 <= x0 or y1 <= y0:
        return
    img[y0:y1, x0:x1] += (layer[y0 - y:y1 - y, x0 - x:x1 - x] * gain)[..., None] * colour


# ------------------------------------------------------------------------------------------------------ static layers

def fbm(w, h, seed, cells, octaves=7, gain=0.56):
    rng = np.random.default_rng(seed)
    out = np.zeros((h, w), F32)
    amp, tot = 1.0, 0.0
    cw, ch = cells
    for o in range(octaves):
        n = rng.random((int(ch * 1.9 ** o) + 3, int(cw * 1.9 ** o) + 3)).astype(F32)
        out += amp * cv2.resize(n, (w, h), interpolation=cv2.INTER_CUBIC)
        tot += amp
        amp *= gain
    return out / tot


def cloud_texture(seed, cells, lo, hi, warp):
    tw, th = 2560, 1440
    f = fbm(tw, th, seed, cells)
    lo_, hi_ = np.percentile(f, [4, 96])
    f = np.clip((f - lo_) / (hi_ - lo_), 0, 1)
    # the swirl: shift every point by a slow noise so the edges curl instead of being blobs
    dx = (fbm(tw, th, seed + 50, (3, 2), 4) - 0.5) * warp
    dy = (fbm(tw, th, seed + 90, (3, 2), 4) - 0.5) * warp
    yy, xx = np.mgrid[0:th, 0:tw].astype(F32)
    f = cv2.remap(f, xx + dx, yy + dy, cv2.INTER_LINEAR, borderMode=cv2.BORDER_REFLECT)
    return sm(lo, hi, f).astype(F32)


def premult_resize(rgba, scale):
    a = rgba[..., 3:4]
    pm = np.concatenate([rgba[..., :3] * a, a], axis=2)
    h, w = pm.shape[:2]
    out = cv2.resize(pm, (max(1, int(round(w * scale))), max(1, int(round(h * scale)))), interpolation=cv2.INTER_AREA)
    return np.ascontiguousarray(out[..., :3]), np.ascontiguousarray(out[..., 3])


def build_static():
    S = {}
    bgra = cv2.imread(LOGO, cv2.IMREAD_UNCHANGED).astype(F32) / 255.0
    rgba = np.dstack([bgra[..., 2], bgra[..., 1], bgra[..., 0], bgra[..., 3]])
    R_px = 0.205 * H
    scale = R_px / MED_R
    S["scale"] = scale
    # --- the medallion crop
    x0, y0, x1, y1 = MED_BOX
    mcol, ma = premult_resize(rgba[y0:y1, x0:x1], scale)
    mh, mw = ma.shape
    S["mcol"], S["ma"] = mcol, ma
    S["mx"] = int(round(W / 2.0 - mw / 2.0))
    S["my"] = int(round(0.50 * H - mh / 2.0))
    S["cx"] = S["mx"] + mw / 2.0
    S["cy"] = S["my"] + mh / 2.0
    S["R"] = R_px
    yy, xx = np.mgrid[0:mh, 0:mw].astype(F32)
    S["mdx"], S["mdy"] = xx - mw / 2.0 + 0.5, yy - mh / 2.0 + 0.5
    S["mrad"] = np.sqrt(S["mdx"] ** 2 + S["mdy"] ** 2)
    # --- the ring (its crop). The lettering is put below the ring, so the ring never touches it
    rr = 1.17 * R_px
    S["rr"] = rr
    half = int(rr + 0.36 * R_px)
    S["rhalf"] = half
    S["rx"], S["ry"] = int(round(S["cx"] - half)), int(round(S["cy"] - half))
    yy, xx = np.mgrid[0:2 * half, 0:2 * half].astype(F32)
    dx, dy = xx - (S["cx"] - S["rx"]), yy - (S["cy"] - S["ry"])
    S["rxx"], S["ryy"] = xx, yy
    S["rrad"] = np.sqrt(dx ** 2 + dy ** 2)
    S["ru"] = ((np.arctan2(dy, dx) + math.pi / 2) % (2 * math.pi) / (2 * math.pi)).astype(F32)
    # --- the lettering
    lcol, la = premult_resize(rgba[LET_Y0:], scale)
    lh, lw = la.shape
    S["lcol"], S["la"] = lcol, la
    S["lx"] = int(round(W / 2.0 - lw / 2.0))
    S["ly"] = int(round(S["cy"] + rr + 0.16 * R_px))          # the top of the lettering is below the ring and its glow
    cols = np.where(la.max(axis=0) > 0.2)[0]
    S["lx0"], S["lx1"] = int(cols.min()), int(cols.max())
    S["lh"], S["lw"] = lh, lw
    yy, xx = np.mgrid[0:lh, 0:lw].astype(F32)
    S["ldx"] = xx - (S["lx0"] + S["lx1"]) / 2.0
    S["ldy"] = yy - lh / 2.0
    S["lhalf"] = (S["lx1"] - S["lx0"]) / 2.0
    # --- the screen
    yy, xx = np.mgrid[0:H, 0:W].astype(F32)
    S["dx"], S["dy"] = xx - S["cx"], yy - S["cy"]
    S["rad"] = np.sqrt(S["dx"] ** 2 + S["dy"] ** 2)
    S["ang"] = np.arctan2(S["dy"], S["dx"])
    nx, ny = (xx - W / 2) / (W / 2), (yy - H / 2) / (H / 2)
    S["yn"] = (yy / H).astype(F32)
    S["vig"] = np.clip(1.0 - 0.40 * (nx ** 2 * 0.75 + ny ** 2 * 0.95), 0.2, 1.0)[..., None].astype(F32)
    # --- the clouds: three layers at different depths
    S["clouds"] = [
        dict(tex=cloud_texture(3, (5, 3), 0.30, 0.85, 220), z0=1.00, rate=0.05, scroll=(10, 4), tint=1.30, alpha=0.95),
        dict(tex=cloud_texture(8, (4, 3), 0.40, 0.88, 300), z0=1.05, rate=0.15, scroll=(-26, 6), tint=1.00, alpha=0.95),
        dict(tex=cloud_texture(14, (3, 2), 0.46, 0.90, 380), z0=1.15, rate=0.29, scroll=(34, -8), tint=0.75, alpha=0.90),
    ]
    # --- dust
    cx, cy, R = S["cx"], S["cy"], S["R"]
    S["main_bolt"] = Bolt(41, (cx + 330, -280), (cx + 2, cy - R * 0.99), forks=30, spread=0.15, long_forks=True)
    S["far_bolts"] = [Bolt(7, (0.22 * W, -60), (0.30 * W, 0.40 * H), forks=7, spread=0.16),
                      Bolt(19, (0.82 * W, -80), (0.73 * W, 0.46 * H), forks=7, spread=0.16)]
    S["streamers"] = [Bolt(60 + k, (cx + dx0, cy - R * 0.99), (cx + dx1, cy - R - ln), forks=3, spread=0.10, levels=8, spread_top=False)
                      for k, (dx0, dx1, ln) in enumerate(((-40, -95, 190), (10, 25, 240), (55, 120, 170)))]
    S["cache"] = {}
    S["dust"] = [(hsh(i, 1) * W, hsh(i, 2) * H, 2 + 8 * hsh(i, 3), hsh(i, 4), 0.5 + 1.5 * hsh(i, 5)) for i in range(70)]
    return S


# ------------------------------------------------------------------------------------------------------ lightning

def small_glow(mask, sigmas, weights):
    """The soft glow of a mask: a few blurs at quarter size, so it stays cheap."""
    sm_ = cv2.resize(mask, (W // 4, H // 4), interpolation=cv2.INTER_AREA)
    out = np.zeros_like(sm_)
    for s, w in zip(sigmas, weights):
        out += cv2.GaussianBlur(sm_, (0, 0), s / 4.0) * w
    return cv2.resize(out, (W, H), interpolation=cv2.INTER_CUBIC)


# the flashes inside the clouds: (time, x, y as a fraction of the screen, strength, decay in seconds)
FLASHES = [
    (0.32, 0.30, 0.34, 0.55, 0.10), (0.62, 0.30, 0.22, 1.00, 0.12), (0.80, 0.55, 0.50, 0.50, 0.09),
    (1.00, 0.74, 0.26, 1.00, 0.12), (1.20, 0.40, 0.55, 0.45, 0.08),
]
FAR = [(0.62, 0), (1.00, 1)]       # the distant bolts, and which one they are


def _path(rng, a, b, levels, amp):
    """A crooked line from a to b: the middle of every piece is pushed sideways by a random amount that gets smaller
    with every level, so the large bends stay large and the small kinks stay small."""
    pts = np.array([a, b], np.float64)
    keep = [0.92, 0.90, 0.88, 0.82, 0.78, 0.74, 0.72, 0.70, 0.68, 0.66, 0.65]
    for lv in range(levels):
        seg_ = pts[1:] - pts[:-1]
        L = np.hypot(seg_[:, 0], seg_[:, 1]) + 1e-6
        nrm = np.stack([-seg_[:, 1], seg_[:, 0]], 1) / L[:, None]
        mid = (pts[:-1] + pts[1:]) / 2 + nrm * (rng.normal(0, 1, len(L)) * L * amp * 0.5)[:, None]
        new = np.empty((len(pts) + len(mid), 2))
        new[0::2], new[1::2] = pts, mid
        pts = new
        amp *= keep[min(lv, len(keep) - 1)]
    return pts


def _dist(pts):
    d = np.hypot(*(pts[1:] - pts[:-1]).T)
    return np.concatenate([[0.0], np.cumsum(d)])


class Bolt:
    """A bolt as a tree of channels: a crooked main channel from the cloud to the target, forks that keep heading
    downward, forks of forks, and a few channels that spread sideways under the cloud. Every point knows how far along
    the channel it is from the start, so the bolt can grow outwards the way a real leader does."""

    def __init__(self, seed, p0, p1, forks=14, spread=0.17, levels=10, spread_top=True, long_forks=False):
        self.fk = 1.7 if long_forks else 1.0
        rng = np.random.default_rng(seed)
        main = _path(rng, p0, p1, levels, spread)
        d = _dist(main)
        self.total = d[-1]
        self.segs = [(main, d, 0)]
        self._fork(rng, main, d, 0, forks)
        if spread_top:
            ys = main[:, 1]
            cand = np.where((ys > 6) & (ys < 0.22 * H))[0]
            for k in range(4):
                if len(cand) < 4:
                    break
                idx = int(rng.choice(cand))
                side = rng.choice([-1, 1])
                ang = math.radians(side * rng.uniform(64, 86))
                dv = np.array([math.sin(ang), math.cos(ang)])
                ln = self.total * rng.uniform(0.10, 0.22)
                sub = _path(rng, main[idx], main[idx] + dv * ln, 7, 0.24)
                self.segs.append((sub, _dist(sub) + d[idx], 1))
        # points along the main channel that are on the screen, for the light the bolt throws on the clouds
        on = main[(main[:, 1] > 0) & (main[:, 1] < H) & (main[:, 0] > 0) & (main[:, 0] < W)]
        self.lights = [tuple(on[int(f * (len(on) - 1))]) for f in np.linspace(0.05, 0.95, 7)] if len(on) > 8 else []

    def _fork(self, rng, path, dist, level, count):
        for k in range(count):
            idx = int(rng.uniform(0.05, 0.93 if level else 0.78) * (len(path) - 2)) + 1
            dv = path[idx + 1] - path[idx - 1]
            dv = dv / (np.hypot(*dv) + 1e-9)
            ang = math.radians(rng.choice([-1, 1]) * rng.uniform(12, 40))
            c, sn = math.cos(ang), math.sin(ang)
            nd = np.array([dv[0] * c - dv[1] * sn, dv[0] * sn + dv[1] * c]) + np.array([0.0, 0.38])
            nd = nd / np.hypot(*nd)
            if nd[1] < 0.12:
                continue
            ln = self.total * rng.uniform(0.07, 0.24) * self.fk * (0.62 ** level)
            sub = _path(rng, path[idx], path[idx] + nd * ln, 6, 0.22)
            sd = _dist(sub) + dist[idx]
            self.segs.append((sub, sd, level + 1))
            if level < 3:
                self._fork(rng, sub, sd, level + 1, int(rng.integers(1, 4)) if level < 2 else int(rng.integers(0, 3)))


def bolt_layers(bolt, front=1e9, width=1.0, glow=(6, 22, 70, 230), gw=(1.3, 0.75, 0.38, 0.2)):
    """Draws the channels up to `front` (the distance from the start). The main channel and the forks come out as separate
    layers, so each can have its own brightness: (core, halo, glow) for each."""
    out = {}
    for name, levels in (("m", (0,)), ("b", (1, 2, 3, 4))):
        core = np.zeros((H, W), np.uint8)
        halo = np.zeros((H, W), np.uint8)
        for pts, dist, lvl in sorted(bolt.segs, key=lambda q: -q[2]):
            if lvl not in levels:
                continue
            n = int(np.searchsorted(dist, front))
            if n < 2:
                continue
            P = pts[:n]
            wc = max(1, int(round((3.0 if lvl == 0 else 2.0 if lvl == 1 else 1.2 if lvl == 2 else 1.0) * width)))
            parts = 4 if lvl > 0 else 28
            idx = np.linspace(0, n - 1, parts + 1).astype(int)
            for c in range(parts):
                a, b = idx[c], idx[c + 1] + 1
                if b - a < 2:
                    continue
                f = 1.0 - 0.55 * c / max(1, parts - 1) if lvl > 0 else 0.80 + 0.20 * hsh(c, int(P[0][0]) % 97 + 5)
                pl = np.round(P[a:b] * 8).astype(np.int32)
                cv2.polylines(core, [pl], False, int(255 * f), wc, cv2.LINE_AA, 3)
                cv2.polylines(halo, [pl], False, int(255 * f * 0.6), wc * 3 + 1, cv2.LINE_AA, 3)
        c = core.astype(F32) / 255
        hh = cv2.GaussianBlur(halo.astype(F32) / 255, (0, 0), 2.4)
        out[name] = (c, hh, small_glow(c * 0.9 + hh * 0.5, glow, gw))
    return out


def paint_bolt(img, L, Im, Ib, glow_k=1.0):
    for name, I in (("m", Im), ("b", Ib)):
        if I <= 0.003:
            continue
        c, hh, g = L[name]
        img += c[..., None] * (np.array([1.0, 0.98, 1.0], F32) * (1.55 * I))
        img += hh[..., None] * (np.array([0.80, 0.84, 1.0], F32) * (0.75 * I))
        img += g[..., None] * (GLOW * (0.55 * I * glow_k))


def blob(img, x, y, sigma, amp, colour):
    r = int(sigma * 4)
    x0, y0, x1, y1 = max(0, int(x) - r), max(0, int(y) - r), min(W, int(x) + r), min(H, int(y) + r)
    if x1 <= x0 or y1 <= y0:
        return
    yy, xx = np.mgrid[y0:y1, x0:x1].astype(F32)
    img[y0:y1, x0:x1] += (np.exp(-((xx - x) ** 2 + (yy - y) ** 2) / (2 * sigma ** 2)) * amp)[..., None] * colour


def strike_I(d):
    """The brightness of the main channel d seconds after the return stroke began: a hard peak, then re-strikes."""
    if d < 0:
        return 0.0
    v = math.exp(-d / 0.05) + 0.30 * math.exp(-d / 0.32)
    for s_, a in ((0.075, 0.80), (0.150, 0.60), (0.262, 0.42)):
        if d >= s_:
            v += a * math.exp(-(d - s_) / 0.035)
    return v


def far_I(d):
    if d < 0:
        return 0.0
    v = math.exp(-d / 0.05)
    if d >= 0.10:
        v += 0.6 * math.exp(-(d - 0.10) / 0.05)
    return v


def zig(rng_seed, x0, y0, x1, y1):
    rng = np.random.default_rng(rng_seed)
    return _path(rng, (x0, y0), (x1, y1), 4, 0.35)


def burst_flash(t):
    d = t - BURST
    if d < 0:
        return 0.0
    v = math.exp(-d * 7.5) + 0.55 * math.exp(-d * 2.4) * ease(d / 0.05)
    for s_, a in ((0.075, 0.5), (0.150, 0.35), (0.262, 0.25)):
        if d >= s_:
            v += a * math.exp(-(d - s_) / 0.04)
    return v


# ------------------------------------------------------------------------------------------------------ the frame

def render(S, i):
    t = i / FPS
    cx, cy, R = S["cx"], S["cy"], S["R"]
    rad = S["rad"]
    fl = burst_flash(t)

    # ---------- the energy that gathers in the middle, and how open the clouds are
    charge = ease(seg(t, 1.10, BURST))
    amb = ease(seg(t, 0.35, 1.7))                 # the dark turns blue slowly: at the start there is only black
    clear_r = 90 * ease(seg(t, 1.30, BURST)) + 470 * ease_out(seg(t, BURST, BURST + 1.2))

    # ---------- the sky behind
    glow_k = (0.25 + 0.75 * ease(seg(t, 1.1, 2.3))) * amb
    img = DEEP * amb + (np.array([0.040, 0.105, 0.26], F32) * glow_k) * np.exp(-(rad / (W * 0.55)) ** 2)[..., None]
    img = np.ascontiguousarray(img.astype(F32))
    g_t = (0.10 * charge + 0.40 * ease(seg(t, BURST, 2.6))) * (1 - 0.35 * seg(t, 4.4, 5.0))
    img += (np.exp(-(rad / (R * 2.3)) ** 2) * g_t)[..., None] * BLUE * 0.9

    # ---------- the light that comes from the flashes: a map, soft, made at low resolution
    lw_, lh_ = W // 6, H // 6
    gx, gy = np.meshgrid(np.arange(lw_, dtype=F32) * 6, np.arange(lh_, dtype=F32) * 6)
    illum = np.zeros((lh_, lw_), F32)
    glob = 0.0
    for (ft, fx, fy, fi, tau) in FLASHES:
        d = t - ft
        if d >= 0:
            a = fi * math.exp(-d / tau) * ease(d / 0.025)
            illum += a * np.exp(-(((gx - fx * W) ** 2 + (gy - fy * H) ** 2) / (2 * 420.0 ** 2)))
            glob += a * 0.10
    mb = S["main_bolt"]
    il = 0.30 * charge if 1.10 <= t < BURST else 0.62 * strike_I(t - BURST)
    if il > 0.01:
        # the bolt lights the clouds along its whole length
        for (px_, py_) in mb.lights:
            illum += il * 0.55 * np.exp(-(((gx - px_) ** 2 + (gy - py_) ** 2) / (2 * 330.0 ** 2)))
    if fl > 0:
        illum += fl * 0.45 * np.exp(-(((gx - cx) ** 2 + (gy - cy) ** 2) / (2 * 760.0 ** 2)))
        glob += fl * 0.12
    illum = cv2.resize(illum, (W, H), interpolation=cv2.INTER_CUBIC)

    # ---------- clouds, back to front
    hole = sm(clear_r, clear_r + 760.0, rad) if clear_r > 1 else np.ones_like(rad)
    keep = 0.55 + 0.45 * (1 - ease(seg(t, BURST, 3.0)) * 0.35)
    trans = np.ones((H, W), F32)
    layers = []
    warm_amb = (0.018 + 0.040 * charge + 0.10 * ease(seg(t, BURST, 2.4))) * np.exp(-(rad / (W * 0.8)) ** 2)
    top_lit = (0.35 + 0.65 * (1 - S["yn"])) * amb
    base = np.array([0.030, 0.046, 0.080], F32) * amb
    for L in S["clouds"]:
        z = L["z0"] * math.exp(L["rate"] * t)
        sx, sy = L["scroll"][0] * t, L["scroll"][1] * t
        tcx, tcy = 1280.0, 720.0
        M = np.array([[1 / z, 0, tcx - cx / z + sx], [0, 1 / z, tcy - cy / z + sy]], np.float64)
        d = cv2.warpAffine(L["tex"], M, (W, H), flags=cv2.INTER_LINEAR | cv2.WARP_INVERSE_MAP, borderMode=cv2.BORDER_REFLECT)
        if L["rate"] > 0.2:
            fade = 1 - ease(seg(t, 1.7, 2.9))               # the nearest layer is blown away
        elif L["rate"] > 0.1:
            fade = 1 - 0.30 * ease(seg(t, 2.0, 3.4))
        else:
            fade = 1 - 0.15 * ease(seg(t, 2.0, 3.4))
        a = np.clip(d * 1.2, 0, 1) * hole * (L["alpha"] * fade * min(1.0, keep + 0.1))
        # light on the faces turned towards the middle: the density minus the density a little nearer the middle
        ds = zoom_about(d, 1 / 0.965, cx, cy, border=cv2.BORDER_REPLICATE)
        rim = np.clip(d - ds, 0, 1) * 3.2
        lit = illum * (0.22 + 0.85 * d) * (0.8 * L["tint"])
        c = base[None, None] * (L["tint"] * (0.6 + 0.9 * d))[..., None]
        c = c + lit[..., None] * ICE * 0.62 + (lit * 0.8)[..., None] * BLUE * 0.25
        c = c + (top_lit * d * L["tint"])[..., None] * np.array([0.10, 0.20, 0.38], F32)
        c = c + (rim * warm_amb * 4.5)[..., None] * (BLUE * 0.8 + ICE * 0.2)
        c = c + (rim * charge * 0.10)[..., None] * ICE
        layers.append((c, a))
        trans *= (1 - a)
    for li, (c, a) in enumerate(layers):
        img = img * (1 - a)[..., None] + c * a[..., None]
        if li == 0:
            # the distant bolts are inside the clouds: the nearer layers go over them
            for (ft, which) in FAR:
                fi = far_I(t - ft)
                if fi > 0.01:
                    key = "far%d" % which
                    if key not in S["cache"]:
                        S["cache"][key] = bolt_layers(S["far_bolts"][which], width=0.7, glow=(5, 18, 60, 200), gw=(1.1, 0.7, 0.4, 0.25))
                    paint_bolt(img, S["cache"][key], 0.85 * fi, 0.60 * fi * (0.8 + 0.4 * hsh(i, which)), 0.9)

    # ---------- the rays: light from the middle that only gets out through the gaps in the clouds
    ray_k = 0.85 * ease(seg(t, BURST, BURST + 0.8)) * (1 - 0.7 * ease(seg(t, 4.3, 4.9)))
    if ray_k > 0.01:
        hw, hh = W // 2, H // 2
        rad_h = cv2.resize(rad, (hw, hh), interpolation=cv2.INTER_AREA)
        src = np.exp(-(rad_h / (R * 1.5)) ** 2) * (0.45 + 0.55 * cv2.resize(trans, (hw, hh), interpolation=cv2.INTER_AREA) ** 1.5)
        src = src.astype(F32) * (1.0 + 0.8 * fl)
        acc = np.zeros_like(src)
        wsum = 0.0
        for k in range(18):
            s = 1 - 0.028 * k
            acc += zoom_about(src, 1 / s, cx / 2, cy / 2, border=cv2.BORDER_CONSTANT) * (0.90 ** k)
            wsum += 0.90 ** k
        rays = cv2.resize(acc / wsum, (W, H), interpolation=cv2.INTER_CUBIC)
        mod = 0.75 + 0.25 * np.sin(S["ang"] * 9 + t * 0.7 + 2.0 * np.sin(S["ang"] * 4 - t * 0.4))
        img += (rays * mod * ray_k * 0.55)[..., None] * (BLUE * 0.75 + ICE * 0.25)

    # ---------- the burst: the flash, and shock waves that leave the middle
    if fl > 0.002:
        img += (np.exp(-(rad / (W * 0.40)) ** 2) * fl * 0.26)[..., None] * (ICE * 0.8 + WHITE * 0.2)
        img += fl * 0.03
    for off, amp, wid in ((0.0, 1.0, 1.0), (0.10, 0.5, 1.6)):
        qq = seg(t, BURST + off, 3.0)
        if 0 < qq < 1:
            wr = 30 + 1500 * ease_out(qq)
            wave = np.exp(-((rad - wr) / (14 + 70 * qq * wid)) ** 2)
            img += (wave * ((1 - qq) ** 2.2 * amp * 0.42))[..., None] * (ICE * 0.7 + BLUE * 0.3)

    # ---------- the medallion: it is there in the dark, and the strike lights it up
    if t >= BURST:
        sc = 1.0 + 0.10 * (1 - ease_out(seg(t, BURST, BURST + 0.9)))
        appear = ease(seg(t, BURST, BURST + 0.04))
        v = 0.10 + 0.90 * ease(seg(t, BURST, BURST + 1.5)) + 0.9 * fl
        mcol, ma = S["mcol"], S["ma"]
        if sc > 1.0008:
            mh, mw = ma.shape
            mcol = zoom_about(mcol, sc, mw / 2, mh / 2, border=cv2.BORDER_CONSTANT, flags=cv2.INTER_CUBIC)
            ma = zoom_about(ma, sc, mw / 2, mh / 2, border=cv2.BORDER_CONSTANT, flags=cv2.INTER_CUBIC)
        col = mcol * v
        for (a0, a1) in ((1.90, 2.80), (3.70, 4.30)):
            sw_ = seg(t, a0, a1)
            if 0 < sw_ < 1:
                pos = (-1.25 + 2.5 * ease(sw_)) * R
                dd = S["mdx"] * 0.80 + S["mdy"] * 0.60 - pos
                band = np.exp(-(dd / (R * 0.15)) ** 2) * (1 - abs(2 * sw_ - 1) ** 3)
                col = col + (band * ma * 0.60)[..., None] * ICE
        over(img, col * appear, ma * appear, S["mx"], S["my"])
        rim = np.exp(-((S["mrad"] - R * 1.0) / (R * 0.022)) ** 2)
        add(img, rim, S["mx"], S["my"], BLUE, 0.30 * ease(seg(t, BURST, BURST + 1.3)) + 0.9 * fl)

    # ---------- the main bolt: a leader that grows down in steps, streamers that rise to meet it, then the return stroke
    if 1.10 <= t < BURST:
        lead = seg(t, 1.10, BURST)
        x = lead * 24
        k_ = math.floor(x)
        ps = (k_ + ease(min(1.0, (x - k_) * 2.5))) / 24.0
        front = ps * mb.total * 1.15
        L = bolt_layers(mb, front=front)
        fk = 0.8 + 0.4 * hsh(i, 3)
        paint_bolt(img, L, 0.20 * fk, 0.13 * fk, 0.5)
        tip = mb.segs[0][0][max(0, min(len(mb.segs[0][0]) - 1, int(np.searchsorted(mb.segs[0][1], front)) - 1))]
        blob(img, tip[0], tip[1], 8, 1.6 * fk, ICE)
        blob(img, tip[0], tip[1], 40, 0.30 * fk, GLOW)
        for k, st in enumerate(S["streamers"]):
            sp_ = ease(seg(t, 1.36 + 0.05 * k, BURST - 0.015))
            if sp_ > 0.01:
                Ls = bolt_layers(st, front=sp_ * st.total * 1.1, width=0.6, glow=(5, 18, 60), gw=(1.0, 0.6, 0.3))
                paint_bolt(img, Ls, 0.42 * fk, 0.28 * fk, 0.35)
    elif t >= BURST:
        d = t - BURST
        Im = strike_I(d)
        if Im > 0.01:
            if "main" not in S["cache"]:
                S["cache"]["main"] = bolt_layers(mb)
            fk = 0.75 + 0.5 * hsh(i, 5)
            paint_bolt(img, S["cache"]["main"], min(1.35, Im), min(1.2, Im) * 0.70 * fk, 1.0)
            ix, iy = cx + 2, cy - R * 0.99
            blob(img, ix, iy, 9, 2.2 * math.exp(-d / 0.06), WHITE)
            blob(img, ix, iy, 80, 0.55 * math.exp(-d / 0.13), GLOW)

    # ---------- the ring: two arcs of electricity run round the rim from the point of impact and meet at the bottom
    p = ease(seg(t, 1.80, 2.85))
    if p > 0.002:
        rr = S["rr"]
        rad_c, u = S["rrad"], S["ru"]
        ud = np.minimum(u, 1 - u)                   # how far round from the top, in turns: 0 at the top, 0.5 at the bottom
        pe = 0.5 * p
        drawn = (ud <= pe).astype(F32)
        line = np.exp(-((rad_c - rr) / 1.9) ** 2)
        if p < 0.999:
            trail = 0.55 + 3.2 * np.exp(-np.clip(pe - ud, 0, None) * 24.0)
        else:
            trail = np.full_like(u, 0.62)
        I = line * drawn * trail
        # ticks
        nt = 120
        fr = (u * nt) % 1.0
        r0 = rr + 0.038 * R
        idx = np.floor(u * nt).astype(np.int32)
        ln = np.where(idx % 10 == 0, 0.085 * R, np.where(idx % 5 == 0, 0.055 * R, 0.030 * R)).astype(F32)
        hwid = 0.9 / (2 * math.pi * r0 / nt)
        tick = np.exp(-((fr - 0.5) / hwid) ** 2) * sm(r0 - 1, r0 + 1, rad_c) * (1 - sm(r0 + ln - 1, r0 + ln + 1, rad_c))
        I += tick * drawn * (0.35 + 0.9 * np.exp(-np.clip(pe - ud, 0, None) * 10.0)) * 0.8
        if p >= 0.999:
            # a comet that keeps running once the ring is closed
            uh = ((t - 2.85) * 0.30) % 1.0
            dist = ((u - uh + 0.5) % 1.0) - 0.5
            I += line * np.exp(-np.clip(-dist, 0, None) * 18.0) * (dist <= 0) * (1.3 * ease(seg(t, 2.85, 3.4)) * (1 - 0.6 * seg(t, 4.3, 4.9)))
        else:
            # the two heads: a bright point, and short arcs of electricity that crackle ahead of it
            arc8 = np.zeros(I.shape, np.uint8)
            for sgn in (1, -1):
                ha = -math.pi / 2 + sgn * 2 * math.pi * pe
                hx = cx + math.cos(ha) * rr - S["rx"]
                hy = cy + math.sin(ha) * rr - S["ry"]
                hd = (S["rxx"] - hx) ** 2 + (S["ryy"] - hy) ** 2
                I += np.exp(-hd / 9.0) * 4.0 + np.exp(-hd / 160.0) * 0.8
                tx_, ty_ = -math.sin(ha) * sgn, math.cos(ha) * sgn
                for k in range(5):
                    a_ = math.radians((hsh(i * 11 + k, sgn + 3) - 0.5) * 110)
                    dxr, dyr = tx_ * math.cos(a_) - ty_ * math.sin(a_), tx_ * math.sin(a_) + ty_ * math.cos(a_)
                    ln_ = 36 + 90 * hsh(i * 13 + k, sgn + 7)
                    pts = zig(i * 101 + k * 7 + (sgn + 2), hx, hy, hx + dxr * ln_, hy + dyr * ln_)
                    cv2.polylines(arc8, [np.round(pts * 8).astype(np.int32)], False, int(255 * (0.55 + 0.45 * hsh(i, k + 40))), 1 if k else 2, cv2.LINE_AA, 3)
            I += arc8.astype(F32) / 255 * 1.7
        gl = cv2.GaussianBlur(I, (0, 0), 7) * 0.9 + cv2.GaussianBlur(I, (0, 0), 20) * 0.7
        breath = 0.9 + 0.1 * math.sin(t * 2.4)
        add(img, (I * 0.95 + gl) * breath, S["rx"], S["ry"], BLUE * 0.85 + ICE * 0.15, 1.0 + 0.5 * fl)
        # a second ring of slow dashes outside it
        q2 = ease(seg(t, 2.3, 3.0))
        if q2 > 0.01:
            r2 = rr + 0.20 * R
            dash = (((u + t * 0.015) * 36) % 1.0 < 0.56).astype(F32)
            ring2 = np.exp(-((rad_c - r2) / 1.3) ** 2) * dash * (ud <= (0.1 + 0.4 * q2))
            add(img, ring2 + cv2.GaussianBlur(ring2, (0, 0), 5) * 0.8, S["rx"], S["ry"], BLUE, 0.7 * q2)

    # ---------- the lettering: a streak of light, then a reveal from the middle outwards
    ly, lx = S["ly"], S["lx"]
    lt = seg(t, 2.85, 3.75)
    if lt > 0:
        soft = 70.0
        reach = ease(lt) * (S["lhalf"] + soft)
        ldx = np.abs(S["ldx"])
        m = np.clip((reach - ldx) / soft, 0, 1)
        sharp = ease(seg(lt, 0.0, 0.8))
        lcol, la = S["lcol"], S["la"]
        sc_l = 1.0 + 0.07 * (1 - ease_out(lt))
        if sc_l > 1.0008:
            lcol = zoom_about(lcol, sc_l, S["lw"] / 2, S["lh"] / 2, border=cv2.BORDER_CONSTANT, flags=cv2.INTER_CUBIC)
            la = zoom_about(la, sc_l, S["lw"] / 2, S["lh"] / 2, border=cv2.BORDER_CONSTANT, flags=cv2.INTER_CUBIC)
        if sharp < 0.995:
            s_ = 9.0 * (1 - sharp)
            lcol = cv2.GaussianBlur(lcol, (0, 0), s_)
            la = cv2.GaussianBlur(la, (0, 0), s_)
        v = 0.92 + 0.5 * math.exp(-lt * 2.2)
        over(img, lcol * (m * v)[..., None], la * m, lx, ly)
        if lt < 0.999:
            edge = np.exp(-((ldx - reach + soft * 0.35) / (soft * 0.30)) ** 2) * (S["la"] > 0.05)
            edge = cv2.GaussianBlur(edge.astype(F32), (0, 0), 2.5)
            add(img, edge * S["la"], lx, ly, ICE, 1.0)
    sp = seg(t, 3.75, 4.35)
    if 0 < sp < 1:
        ex = S["lx0"] + (S["lx1"] - S["lx0"]) * (-0.15 + 1.3 * ease(sp))
        xs = np.arange(S["lw"], dtype=F32)[None, :]
        band = np.exp(-((xs + S["ldy"] * 0.5 - ex) / (S["lw"] * 0.035)) ** 2) * S["la"] * (1 - abs(2 * sp - 1) ** 2)
        add(img, band, lx, ly, ICE, 0.9)

    # ---------- sparks from the burst and dust in the air
    st = t - BURST
    if 0 <= st < 2.6:
        sp_layer = np.zeros((H, W), F32)
        for k in range(120):
            ang = hsh(k, 7) * 2 * math.pi
            spd = R * (0.8 + 3.4 * hsh(k, 8))
            life = 0.8 + 1.7 * hsh(k, 9)
            if st > life:
                continue
            pp = st / life
            gk = 120 * (0.5 + hsh(k, 11))
            dist = R * 1.08 + spd * (1 - (1 - pp) ** 2.4) * 0.9
            x = cx + math.cos(ang) * dist
            y = cy + math.sin(ang) * dist * 0.85 + gk * pp * pp
            pp2 = max(0.0, pp - 0.012)
            dist2 = R * 1.08 + spd * (1 - (1 - pp2) ** 2.4) * 0.9
            x2 = cx + math.cos(ang) * dist2
            y2 = cy + math.sin(ang) * dist2 * 0.85 + gk * pp2 * pp2
            cv2.line(sp_layer, (int(x2 * 8), int(y2 * 8)), (int(x * 8), int(y * 8)), (1 - pp) ** 1.3 * (1.2 + 1.6 * hsh(k, 13)), 1 if hsh(k, 14) < 0.7 else 2, cv2.LINE_AA, 3)
        img += sp_layer[..., None] * ICE
        img += (small_glow(sp_layer, (3, 9), (1.0, 0.5)) * 0.8)[..., None] * BLUE
    da = ease(seg(t, 1.9, 2.9)) * (1 - ease(seg(t, 4.4, 4.9)))
    if da > 0.01:
        dl = np.zeros((H, W), F32)
        for (x, y, rd, ph, spd) in S["dust"]:
            yy_ = (y - t * 14.0 * spd) % H
            xx_ = (x + 18 * math.sin(t * 0.35 * spd + ph * 6.28)) % W
            tw = 0.4 + 0.6 * (0.5 + 0.5 * math.sin(t * 1.5 + ph * 20))
            cv2.circle(dl, (int(xx_), int(yy_)), max(1, int(rd)), 0.22 * tw * (0.5 + math.exp(-((xx_ - cx) ** 2 + (yy_ - cy) ** 2) / (R * 3.0) ** 2)), -1, cv2.LINE_AA)
        dl = cv2.GaussianBlur(dl, (0, 0), 2.2)
        img += (dl * (da * 1.5))[..., None] * ICE
    if glob > 0.001:
        img += glob * ICE * 0.55

    # ---------- the camera: a slow push, a punch on the burst, a shake, a slight drift, and the long push at the end
    push = 1.0 + 0.035 * (t / DUR) + (0.03 * math.exp(-(t - BURST) * 6.0) if t >= BURST else 0.0) + 0.55 * ease_in(seg(t, 4.20, 5.0))
    shake = 12.0 * math.exp(-(t - BURST) * 10.0) if t >= BURST else 0.0
    sx, sy = shake * math.sin(t * 83.0), shake * math.cos(t * 71.0)
    roll = 0.5 * math.sin(t * 0.9) - 0.8 * ease_in(seg(t, 4.2, 5.0))
    zb = 0.05 * ease_in(seg(t, 4.30, 5.0)) + (0.022 * math.exp(-(t - BURST) * 9.0) if t >= BURST else 0.0)
    c0 = (W / 2.0, H * 0.47)
    n_s = 9 if zb > 0.004 else 1
    acc = None
    for k in range(n_s):
        pk = push * (1 + zb * (k / (n_s - 1) - 0.5)) if n_s > 1 else push
        Mx = cv2.getRotationMatrix2D(c0, roll, pk)
        Mx[0, 2] += sx
        Mx[1, 2] += sy
        wimg = cv2.warpAffine(img, Mx, (W, H), flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_REFLECT)
        acc = wimg if acc is None else acc + wimg
    img = acc / n_s

    # ---------- the end: a flash of light and the cut to black
    out_f = ease(seg(t, 4.35, 4.66)) * (1 - ease(seg(t, 4.66, 4.98)))
    if out_f > 0.001:
        img += (out_f * 1.2) * (BLUE * 0.35 + ICE * 0.5 + WHITE * 0.15)[None, None] * (0.30 + 0.70 * np.exp(-(rad / (W * 0.5)) ** 2))[..., None]

    # ---------- the lens: bloom, a streak, colour fringes, vignette
    hq = cv2.resize(img, (W // 4, H // 4), interpolation=cv2.INTER_AREA)
    bright = np.clip(hq - 0.80, 0, None)
    bl = cv2.GaussianBlur(bright, (0, 0), 3) * 0.9 + cv2.GaussianBlur(bright, (0, 0), 11) * 0.8 + cv2.GaussianBlur(bright, (0, 0), 34) * 0.7
    streak = cv2.GaussianBlur(np.clip(hq - 1.4, 0, None), (0, 0), sigmaX=70, sigmaY=1.6)
    bloom = cv2.resize(bl * 0.42 + streak * 0.30 * np.array([0.7, 0.9, 1.0], F32), (W, H), interpolation=cv2.INTER_CUBIC)
    img = img + bloom
    ab = 0.0010 + 0.0030 * fl + 0.004 * out_f
    img[..., 0] = zoom_about(np.ascontiguousarray(img[..., 0]), 1 + ab, W / 2, H / 2, border=cv2.BORDER_REPLICATE)
    img[..., 2] = zoom_about(np.ascontiguousarray(img[..., 2]), 1 - ab, W / 2, H / 2, border=cv2.BORDER_REPLICATE)
    img = img * S["vig"]

    # ---------- the fades to and from black, a soft shoulder for the highlights, grain
    fade = 1 - ease(seg(t, 4.80, 5.0 - 1.0 / FPS))
    img = np.clip(img * fade, 0, None)
    knee = 0.80
    img = np.where(img > knee, knee + (1 - knee) * (1 - np.exp(-(img - knee) / (1 - knee))), img)
    img = np.clip(img, 0, 1) ** 0.97
    rng = np.random.default_rng(1000 + i)
    grain = rng.normal(0, 0.009, (H, W, 1)).astype(F32) + rng.normal(0, 0.003, (H, W, 3)).astype(F32)
    img = np.clip(img + grain * (0.4 + 0.6 * (1 - np.abs(img - 0.35))) * ease(seg(t, 0.05, 0.40)), 0, 1)
    out = (img * 255.0 + 0.5).astype(np.uint8)
    return np.ascontiguousarray(out[..., ::-1])       # RGB to BGR for OpenCV


# ------------------------------------------------------------------------------------------------------ the sound

def audio(path):
    """A restrained score: deep thunder, a soft riser and a quiet moment before the strike, a warm pad and clean chimes.
    Everything is filtered with steep filters; there is no hiss, buzz or ringing."""
    from scipy.signal import butter, sosfilt
    sr = 44100
    n = int(sr * DUR)
    t = np.arange(n) / sr
    rng = np.random.default_rng(11)

    def lpf(x, fc, o=4):
        return sosfilt(butter(o, fc, "low", fs=sr, output="sos"), x)

    def hpf(x, fc, o=4):
        return sosfilt(butter(o, fc, "high", fs=sr, output="sos"), x)

    def bpf(x, lo, hi, o=3):
        return sosfilt(butter(o, [lo, hi], "band", fs=sr, output="sos"), x)

    def white():
        x = rng.normal(0, 1, n)
        return x / np.std(x)

    def brown():
        x = hpf(np.cumsum(rng.normal(0, 1, n)), 25.0)
        return x / np.std(x)

    def swell(a, b, c, shape=1.0):
        e = np.zeros(n)
        i0, i1, i2 = int(a * sr), int(b * sr), int(c * sr)
        e[i0:i1] = np.linspace(0, 1, i1 - i0) ** shape
        e[i1:i2] = np.linspace(1, 0, max(1, i2 - i1)) ** 1.5
        return e

    def hit(at, atk, dec):
        """A soft attack followed by an exponential decay."""
        e = np.zeros(n)
        i0 = int(at * sr)
        tt = np.arange(n - i0) / sr
        e[i0:] = (1 - np.exp(-tt / atk)) * np.exp(-tt / dec)
        return e

    def tone(f, ph=0.0):
        return np.sin(2 * np.pi * f * t + ph)

    # the quiet before the strike: everything low is ducked for a moment, so the hit lands
    duck = 1 - 0.85 * np.clip(np.minimum((t - (BURST - 0.07)) / 0.02, (BURST - t) / 0.01), 0, 1)

    mix = np.zeros(n)
    # --- a low, slowly swelling drone
    drone = (tone(55.0) * 0.5 + tone(82.41) * 0.3 + tone(110.0) * 0.14) * (1 + 0.04 * np.sin(2 * np.pi * 0.3 * t))
    mix += drone * swell(0.0, 1.55, 1.9, 1.3) * 0.26 * duck
    # --- distant thunder behind the two far bolts: a soft low rumble and the faintest crack
    for at, amp in ((0.62, 0.55), (1.00, 0.80)):
        mix += lpf(brown(), 150.0) * hit(at + 0.03, 0.14, 0.55) * amp * 0.30
        mix += bpf(white(), 500, 2200) * hit(at, 0.002, 0.04) * amp * 0.035
    # --- the leader: a soft riser that opens up and stops just before the strike
    r = np.clip((t - 1.05) / 0.5, 0, 1) ** 1.5
    riser = lpf(white(), 450.0) * (1 - r) + lpf(white(), 3200.0) * r
    mix += riser * swell(1.05, 1.57, 1.575, 2.2) * 0.065
    # --- the strike: a deep boom, a thump, a short clean crack, re-strikes, and thunder that rolls away
    bf = 30 + 70 * np.exp(-np.clip(t - BURST, 0, None) / 0.22)
    boom = np.sin(2 * np.pi * np.cumsum(bf) / sr)
    mix += boom * hit(BURST, 0.004, 1.0) * 0.95
    mix += lpf(white(), 320.0) * hit(BURST, 0.003, 0.20) * 0.65
    mix += bpf(white(), 700, 5500) * hit(BURST, 0.0008, 0.030) * 0.34
    mix += lpf(white(), 2400.0) * hit(BURST, 0.001, 0.14) * 0.12
    for at, amp in ((BURST + 0.075, 0.45), (BURST + 0.15, 0.32), (BURST + 0.262, 0.22)):
        mix += bpf(white(), 250, 2800) * hit(at, 0.001, 0.05) * amp * 0.30
        mix += tone(62.0) * hit(at, 0.004, 0.14) * amp * 0.55
    roll = 0.72 + 0.28 * np.sin(2 * np.pi * 2.4 * t + 1.0) * np.exp(-np.clip(t - BURST, 0, None) / 1.5)
    mix += lpf(brown(), 210.0) * hit(BURST + 0.04, 0.16, 1.7) * roll * 0.52
    mix += lpf(white(), 1700.0) * hit(BURST, 0.03, 0.40) * 0.07
    # --- the warm pad that opens after the strike (A major), with a slow vibrato and a little width
    pad = swell(BURST + 0.05, 2.75, 4.55, 1.2)
    vib = 0.6 * np.sin(2 * np.pi * 0.23 * t)
    for f, a in ((110.0, 0.12), (164.81, 0.10), (220.0, 0.09), (277.18, 0.05), (329.63, 0.045), (440.0, 0.02)):
        mix += (tone(f, vib) + tone(f * 1.003, 1.3 + vib)) * 0.5 * pad * a
    # --- clean chimes: the ring closes, the lettering finishes
    for at, notes, dec, amp in ((2.85, ((880.0, 1.0), (1318.5, 0.5), (1760.0, 0.32), (2637.0, 0.12)), 1.5, 0.050),
                                (3.75, ((1174.7, 1.0), (1760.0, 0.45), (2349.3, 0.2)), 1.3, 0.036)):
        for f, a in notes:
            mix += tone(f) * hit(at, 0.004, dec) * a * amp
    # --- a breath of air under the lettering
    mix += lpf(white(), 1900.0) * swell(2.70, 3.20, 3.85, 1.3) * 0.045
    # --- the close: a soft reverse swell, one deep note, then silence
    mix += lpf(white(), 1400.0) * swell(4.20, 4.62, 4.64, 2.2) * 0.07
    hf = 34 + 36 * np.exp(-np.clip(t - 4.62, 0, None) / 0.14)
    mix += np.sin(2 * np.pi * np.cumsum(hf) / sr) * hit(4.62, 0.004, 0.38) * 0.60
    mix += lpf(white(), 450.0) * hit(4.62, 0.002, 0.10) * 0.22
    mix *= np.clip((DUR - t) / 0.22, 0, 1)
    mix *= np.clip(t / 0.04, 0, 1)
    # --- a dark room: a short, soft reverb
    ir_n = int(sr * 1.5)
    ir = rng.normal(0, 1, ir_n) * np.exp(-np.arange(ir_n) / (sr * 0.42))
    ir[: int(0.018 * sr)] = 0
    ir = lpf(ir, 2800.0, 2)
    ir /= np.sqrt(np.sum(ir ** 2))
    from numpy.fft import rfft, irfft
    Ln = n + ir_n
    wet = irfft(rfft(mix, Ln) * rfft(ir, Ln), Ln)[:n]
    wet2 = irfft(rfft(mix, Ln) * rfft(np.roll(ir, 53), Ln), Ln)[:n]
    L = lpf(mix * 0.86 + wet * 0.34, 11000.0, 2)
    Rr = lpf(mix * 0.86 + wet2 * 0.34, 11000.0, 2)
    st = np.stack([L, Rr], axis=1)
    st = np.tanh(st * 0.85) / np.tanh(0.85)
    st = st / np.max(np.abs(st)) * 0.80
    import wave
    pcm = (np.clip(st, -1, 1) * 32767).astype(np.int16)
    with wave.open(path, "wb") as wf:
        wf.setnchannels(2)
        wf.setsampwidth(2)
        wf.setframerate(sr)
        wf.writeframes(pcm.tobytes())


def main():
    args = sys.argv[1:]
    if args and args[0] == "--audio":
        audio(args[1])
        return
    out = args[0]
    os.makedirs(out, exist_ok=True)
    a, b = 0, N
    if "--frames" in args:
        a, b = [int(x) for x in args[args.index("--frames") + 1].split(":")]
    S = build_static()
    for i in range(a, b):
        cv2.imwrite(os.path.join(out, "p%03d.png" % (i + 1)), render(S, i))
        if i % 10 == 0:
            print(i, "/", N, flush=True)


if __name__ == "__main__":
    main()
