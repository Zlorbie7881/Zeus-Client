"""Makes the pictures of the 3D news scroll in the bottom right of the Zeus menu.

  scroll_head.png   the top rod with its knobs and the title on the parchment under it
  scroll_body.png   the long parchment with the news written on it (the menu moves a window over it)
  scroll_foot.png   the bottom rod
  scroll_roll.png   the rolled-up scroll seen from the front (the scroll while it is closed)
  scroll_roll_0..6  the same roll turned 20, 13, 6, 0, -6, -13, -20 degrees, so the menu can turn it slowly

The rods and the roll are not drawn by hand: a small ray caster (numpy) shades real cylinders and cones with a moon light from the
upper right, a cool light from the left and a soft glow from below, so they have proper depth. The parchment is a noise texture
with stains, fibres, burnt edges and the curl where it runs onto a rod.

Run:  python3 tools/make_news_scroll.py <font folder> <output folder> <version>
The font folder is the Google Fonts "ofl" folder (Cinzel, under the SIL Open Font License). The body text uses Inter
(SIL Open Font License), which is looked for in the usual system folders.
Prints the sizes of the pictures; the same numbers are in MenuNews.java.
"""
import glob
import math
import sys

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont

FONTS, OUT, VERSION = (sys.argv + ["", "", ""])[1:4]
PW = 500                    # width of the parchment
CAP = 40                    # room at each side of the parchment inside the pictures (the knobs stick out into it)
RW = PW + 2 * CAP           # width of every picture
HEAD_H = 104
FOOT_H = 80
FADE = 16                   # head and foot melt into the body over this many pixels
MARGIN = 40                 # text margin inside the parchment
SS = 2                      # supersampling of the ray caster

PAPER = np.array([196, 186, 164], np.float32)     # parchment, a little cool from the moonlight
PAPER_DARK = np.array([128, 116, 96], np.float32)
INK = (34, 36, 52)
INK_SOFT = (74, 66, 58)
INK_ACCENT = (92, 58, 40)


# ------------------------------------------------------------------ fonts

def cinzel(size, weight=700):
    f = ImageFont.truetype(FONTS + "/cinzel/Cinzel[wght].ttf", size)
    try:
        f.set_variation_by_axes([weight])
    except Exception:
        pass
    return f


def inter(size, kind="Regular"):
    for pat in (f"/usr/share/fonts/**/Inter-{kind}.otf", f"/usr/share/fonts/**/Inter-{kind}.ttf", "/usr/share/fonts/**/Inter*.otf"):
        found = sorted(glob.glob(pat, recursive=True))
        if found:
            return ImageFont.truetype(found[0], size)
    return ImageFont.truetype(FONTS + "/gfsdidot/GFSDidot-Regular.ttf", size)


# ------------------------------------------------------------------ noise

def noise(h, w, cells, beta, seed):
    rng = np.random.default_rng(seed)
    f = np.fft.fft2(rng.normal(0, 1, (h, w)))
    fy = np.fft.fftfreq(h)[:, None] * w
    fx = np.fft.fftfreq(w)[None, :] * w
    k = np.sqrt((fx / cells) ** 2 + (fy / cells) ** 2) + 1.0
    f = f / k ** (beta / 2.0)
    f[0, 0] = 0
    n = np.real(np.fft.ifft2(f))
    return ((n - n.mean()) / (n.std() + 1e-9)).astype(np.float32)


def fbm(h, w, cells, octaves, seed, gain=0.5, beta=2.4):
    out = np.zeros((h, w), np.float32)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        out += amp * noise(h, w, cells * 2 ** o, beta, seed + 13 * o)
        tot += amp
        amp *= gain
    return out / tot


PAPER_TEX = None


def paper_texture(h, w, seed=4):
    """Parchment colour for a flat sheet h x w: base tone, stains, fibres, darker towards the edges."""
    n = fbm(h, w, 3.0, 5, seed, gain=0.55, beta=3.0)
    fib = fbm(h, w, 60.0, 3, seed + 5, gain=0.6, beta=1.4)
    fine = noise(h, w, 220.0, 0.6, seed + 9)
    t = np.clip(0.5 + 0.20 * n, 0, 1)
    col = PAPER[None, None, :] * (0.88 + 0.22 * t[..., None]) + PAPER_DARK * (0.0)
    col = col * (1.0 + 0.045 * fib[..., None] + 0.025 * fine[..., None])
    stain = np.clip((n - 0.55) * 1.4, 0, 1)                    # a few darker, browner patches
    col = col * (1 - 0.18 * stain[..., None]) + np.array([150, 120, 84], np.float32) * (0.18 * stain[..., None]) * (col.mean() / 160.0)
    return col


def sample(tex, u, v):
    """Bilinear lookup in a tiling texture with u, v in 0..1 (any shape)."""
    h, w = tex.shape[:2]
    mx = (np.mod(u, 1.0) * w).astype(np.float32)
    my = (np.mod(v, 1.0) * h).astype(np.float32)
    return cv2.remap(tex, mx, my, cv2.INTER_LINEAR, borderMode=cv2.BORDER_WRAP)


# ------------------------------------------------------------------ the ray caster

class Part:
    """One piece of a solid of revolution around the x axis: radius r0 at x0 growing to r1 at x1."""

    def __init__(self, x0, x1, r0, r1, mat):
        self.x0, self.x1, self.r0, self.r1, self.mat = x0, x1, r0, r1, mat


MATS = {
    # name: (colour, specular strength, shininess)
    "paper": (PAPER, 0.04, 8),
    "bronze": (np.array([150, 118, 84], np.float32), 0.85, 28),
    "dark": (np.array([58, 50, 52], np.float32), 0.5, 18),
    "cloth": (np.array([26, 40, 82], np.float32), 0.20, 12),
    "silver": (np.array([196, 200, 210], np.float32), 0.9, 40),
}


def rot(yaw, pitch):
    cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]], np.float32)
    rx = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]], np.float32)
    return rx @ ry


def render(parts, w, h, yaw=0.0, pitch=0.0, cx=None, cy=None, scale=1.0, paper_len=None, seed=1):
    """Ray casts the parts (orthographic camera looking down -z), returns rgb (h, w, 3) float and alpha (h, w)."""
    W, H = w * SS, h * SS
    cx = w / 2 if cx is None else cx
    cy = h / 2 if cy is None else cy
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    px = (xx + 0.5) / SS - cx
    py = -((yy + 0.5) / SS - cy)
    px /= scale
    py /= scale
    R = rot(yaw, pitch)
    Rt = R.T
    # camera-space ray: origin (px, py, 500), direction (0, 0, -1); object space is R^T applied to both
    o = np.stack([px, py, np.full_like(px, 500.0)], -1) @ Rt.T
    d = (np.array([0, 0, -1.0], np.float32) @ Rt.T)
    d = np.broadcast_to(d, o.shape)
    ox, oy, oz = o[..., 0], o[..., 1], o[..., 2]
    dx, dy, dz = d[..., 0], d[..., 1], d[..., 2]
    best = np.full((H, W), 1e9, np.float32)
    nrm = np.zeros((H, W, 3), np.float32)
    pos = np.zeros((H, W, 3), np.float32)
    mat = np.full((H, W), -1, np.int32)
    kind = np.zeros((H, W), np.int32)      # 0 side, 1 end face
    names = list(MATS.keys())

    def commit(t, valid, n, p, m, k):
        upd = valid & (t > 0) & (t < best)
        best[upd] = t[upd]
        nrm[upd] = n[upd]
        pos[upd] = p[upd]
        mat[upd] = m
        kind[upd] = k

    for part in parts:
        mi = names.index(part.mat)
        k = (part.r1 - part.r0) / (part.x1 - part.x0)
        a0 = part.r0 - k * part.x0 + k * ox
        A = dy * dy + dz * dz - k * k * dx * dx
        B = 2 * (oy * dy + oz * dz - a0 * k * dx)
        C = oy * oy + oz * oz - a0 * a0
        disc = B * B - 4 * A * C
        ok = disc >= 0
        sq = np.sqrt(np.where(ok, disc, 0))
        Asafe = np.where(np.abs(A) < 1e-9, 1e-9, A)
        for sgn in (-1, 1):
            t = (-B + sgn * sq) / (2 * Asafe)
            p = o + d * t[..., None]
            inx = (p[..., 0] >= part.x0) & (p[..., 0] <= part.x1)
            rr = np.sqrt(p[..., 1] ** 2 + p[..., 2] ** 2)
            n = np.stack([-k * rr, p[..., 1], p[..., 2]], -1)
            n /= np.maximum(np.linalg.norm(n, axis=-1, keepdims=True), 1e-6)
            commit(t, ok & inx, n, p, mi, 0)
        # end faces: only where the radius is bigger than the neighbouring part (checked by the caller via a sorted list)
    # end discs between neighbouring parts (and at both ends of the whole thing)
    bounds = []
    for i, part in enumerate(parts):
        bounds.append((part.x0, 0.0, part.r0, i, -1))      # left face of part i: radius step from 0 (or the part before) to r0
        bounds.append((part.x1, part.r1, 0.0, i, +1))
    # a face is exposed where two parts meet with different radii (or where the part has nothing next to it): handle exact meetings
    faces = {}
    for (x, ra, rb, i, side) in bounds:
        key = round(x, 3)
        faces.setdefault(key, []).append((ra, rb, i, side))
    for x, items in faces.items():
        # radius on the left side of x (largest of parts ending here) and on the right side (largest of parts starting here)
        left = [(parts[i].r1, i) for (ra, rb, i, side) in items if side == +1]
        right = [(parts[i].r0, i) for (ra, rb, i, side) in items if side == -1]
        rl, il = max(left) if left else (0.0, None)
        rr_, ir = max(right) if right else (0.0, None)
        if abs(rl - rr_) < 1e-4:
            continue
        lo, hi = min(rl, rr_), max(rl, rr_)
        owner = il if rl > rr_ else ir
        sign = 1.0 if rl > rr_ else -1.0                     # which way the exposed face looks
        with np.errstate(divide="ignore", invalid="ignore"):
            t = (x - ox) / np.where(np.abs(dx) < 1e-9, 1e-9, dx)
        p = o + d * t[..., None]
        r = np.sqrt(p[..., 1] ** 2 + p[..., 2] ** 2)
        valid = (r >= lo) & (r <= hi)
        n = np.zeros_like(p)
        n[..., 0] = sign
        commit(t, valid, n, p, names.index(parts[owner].mat), 1)

    hit = mat >= 0
    # ---- shading
    base = np.zeros((H, W, 3), np.float32)
    spec_k = np.zeros((H, W), np.float32)
    shin = np.ones((H, W), np.float32)
    for mi, name in enumerate(names):
        m = mat == mi
        if not m.any():
            continue
        col, sk, sh = MATS[name]
        base[m] = col
        spec_k[m] = sk
        shin[m] = sh
    # paper: texture by (x along the roll, angle round it); end faces: a spiral of the rolled layers
    pm = (mat == names.index("paper"))
    if pm.any():
        global PAPER_TEX
        if PAPER_TEX is None:
            PAPER_TEX = paper_texture(512, 1024, seed=seed)
        ang = np.arctan2(pos[..., 1], pos[..., 2]) / (2 * math.pi)
        u = (pos[..., 0] / (paper_len or 500.0)) * 1.0
        side = pm & (kind == 0)
        tex = sample(PAPER_TEX, u + 0.0, ang * 1.0 + 0.5)
        base[side] = tex[side]
        face = pm & (kind == 1)
        if face.any():
            r = np.sqrt(pos[..., 1] ** 2 + pos[..., 2] ** 2)
            ang_f = np.arctan2(pos[..., 1], pos[..., 2])
            pitch_px = 3.4
            ph = ((r + pitch_px * ang_f / (2 * math.pi)) / pitch_px) % 1.0
            band = 0.5 - 0.5 * np.cos(2 * math.pi * ph)           # 0 at the cut edge of a layer, 1 mid-layer
            shade = 0.72 + 0.28 * band ** 0.6
            c = np.array([212, 200, 170], np.float32)
            base[face] = (c[None, :] * shade[face][..., None])
    nrm_c = nrm
    # lights, in object space
    def unit(v):
        v = np.array(v, np.float32)
        return v / np.linalg.norm(v)
    # the lights are fixed to the camera, so they are turned into object space with R^T
    Lk = unit([0.55, 0.65, 0.55]) @ Rt.T          # moon, upper right front
    Lf = unit([-0.7, 0.15, 0.5]) @ Rt.T           # cool fill from the left
    Lb = unit([0.0, -0.8, 0.4]) @ Rt.T            # glow from below (mist)
    V = np.array([0, 0, 1.0], np.float32) @ Rt.T
    nk = np.clip((nrm_c * Lk).sum(-1), 0, 1)
    nf = np.clip((nrm_c * Lf).sum(-1), 0, 1)
    nb = np.clip((nrm_c * Lb).sum(-1), 0, 1)
    amb = 0.22
    lit = amb + 0.95 * nk
    col = base * lit[..., None] * np.array([1.0, 0.97, 0.90], np.float32)
    col += base * (nf[..., None] * np.array([0.05, 0.08, 0.15], np.float32))
    col += base * (nb[..., None] * np.array([0.07, 0.09, 0.13], np.float32))
    hv = Lk + V
    hv = hv / np.maximum(np.linalg.norm(hv, axis=-1, keepdims=True) if hv.ndim > 1 else np.linalg.norm(hv), 1e-6)
    sp = np.clip((nrm_c * hv).sum(-1), 0, 1) ** shin
    col += (spec_k * sp)[..., None] * np.array([255, 246, 230], np.float32) * 0.9
    # soft self shadow: darker where the surface turns away from the camera at the silhouette
    facing = np.clip((nrm_c * V).sum(-1), 0, 1)
    col *= (0.55 + 0.45 * facing ** 0.5)[..., None]
    alpha = hit.astype(np.float32)
    col = np.where(hit[..., None], col, 0)
    # downsample with premultiplied colour
    pre = col * alpha[..., None]
    pre = cv2.resize(pre, (w, h), interpolation=cv2.INTER_AREA)
    a = cv2.resize(alpha, (w, h), interpolation=cv2.INTER_AREA)
    rgb = pre / np.maximum(a[..., None], 1e-4)
    return np.clip(rgb, 0, 255), a


def knob_profile(x_start, direction, r_neck=4.5, r_bulb=15.0, neck_len=7.0, bulb_len=16.0, mat="bronze", steps=10):
    """The rounded end of a rod, going outwards from x_start (direction +1 or -1)."""
    parts = []
    x = x_start
    parts.append(Part(min(x, x + direction * neck_len), max(x, x + direction * neck_len), r_neck, r_neck, mat))
    x += direction * neck_len
    # a ring, then a ball made from short cones
    ring_len = 3.0
    parts.append(Part(min(x, x + direction * ring_len), max(x, x + direction * ring_len), r_bulb * 0.72, r_bulb * 0.72, mat))
    x += direction * ring_len
    for i in range(steps):
        a0 = math.pi * i / steps
        a1 = math.pi * (i + 1) / steps
        xa = x + direction * bulb_len * (1 - math.cos(a0)) / 2
        xb = x + direction * bulb_len * (1 - math.cos(a1)) / 2
        ra = r_bulb * math.sin(a0) * 0.9 + 1.0
        rb = r_bulb * math.sin(a1) * 0.9 + 1.0
        if direction > 0:
            parts.append(Part(xa, xb, ra, rb, mat))
        else:
            parts.append(Part(xb, xa, rb, ra, mat))
    return parts


def fix_order(parts):
    return sorted(parts, key=lambda p: p.x0)


def rod_parts(x0, x1, r_paper, r_rod=4.5, with_ribbon=False):
    """A rod with the parchment rolled round it between x0 and x1, and a knob at each end."""
    parts = [Part(x0, x1, r_paper, r_paper, "paper"), Part(x0 - 40, x1 + 40, r_rod, r_rod, "bronze")]
    parts += knob_profile(x1, +1, r_neck=r_rod, r_bulb=r_paper + 3.0)
    parts += knob_profile(x0, -1, r_neck=r_rod, r_bulb=r_paper + 3.0)
    if with_ribbon:
        xm = (x0 + x1) / 2
        parts.append(Part(xm - 22, xm + 22, r_paper + 1.6, r_paper + 1.6, "cloth"))
    return parts


# ------------------------------------------------------------------ parchment pictures

def parchment_sheet(h, rows_curl_top=0, rows_curl_bottom=0, seed=4, rough_top=False, rough_bottom=False):
    """The flat sheet (RW x h): colour and alpha. The rows next to a rod darken as the paper turns away round it."""
    tex = paper_texture(h, RW, seed)
    ys = np.arange(h, dtype=np.float32)[:, None]
    xs = np.arange(RW, dtype=np.float32)[None, :]
    # wavy, slightly burnt side edges
    wob_l = 1.6 * np.sin(ys * 0.05 + 1.0) + 1.1 * np.sin(ys * 0.171 + 2.0) + 0.7 * np.sin(ys * 0.43)
    wob_r = 1.6 * np.sin(ys * 0.043 + 4.0) + 1.1 * np.sin(ys * 0.19 + 0.7) + 0.7 * np.sin(ys * 0.39 + 1.0)
    left = CAP + 3 + wob_l
    right = CAP + PW - 3 + wob_r
    m = np.clip(np.minimum(xs - left, right - xs) / 1.4 + 0.5, 0, 1)
    dist = np.minimum(xs - left, right - xs)
    burn = np.clip(1.0 - dist / 22.0, 0, 1) ** 1.8                # brown, scorched border
    col = tex * (1 - 0.34 * burn[..., None]) + np.array([96, 66, 40], np.float32) * (0.12 * burn[..., None])
    # light falls off a little towards the sides of the long sheet
    side = np.clip(np.abs(xs - RW / 2) / (PW / 2), 0, 1) ** 2
    col *= (1.0 - 0.12 * side)[..., None]
    # curl where the sheet runs onto the rods
    if rows_curl_top:
        c = np.clip(1 - ys / rows_curl_top, 0, 1) ** 1.6
        col *= (1.0 - 0.52 * c)[..., None]
    if rows_curl_bottom:
        c = np.clip(1 - (h - 1 - ys) / rows_curl_bottom, 0, 1) ** 1.6
        col *= (1.0 - 0.52 * c)[..., None]
    return np.clip(col, 0, 255), m


def finish(rgb, alpha):
    return Image.fromarray(np.dstack([np.clip(rgb, 0, 255), np.clip(alpha, 0, 1) * 255]).astype(np.uint8), "RGBA")


def over(dst_rgb, dst_a, src_rgb, src_a):
    a = src_a + dst_a * (1 - src_a)
    rgb = (src_rgb * src_a[..., None] + dst_rgb * dst_a[..., None] * (1 - src_a[..., None])) / np.maximum(a[..., None], 1e-4)
    return rgb, a


def drop_shadow(alpha, dy, blur, strength):
    sh = np.roll(alpha, dy, axis=0)
    sh = cv2.GaussianBlur(sh, (0, 0), blur) * strength
    return sh


def paper_roll_alpha_and_rgb(w, h, parts, **kw):
    return render(parts, w, h, **kw)


def title_layer(h, cy):
    layer = Image.new("L", (RW * 3, h * 3), 0)
    d = ImageDraw.Draw(layer)
    f = cinzel(22 * 3, 700)
    text = "NEWS OF ZEUS"
    gap = 4 * 3
    widths = [d.textlength(c, font=f) for c in text]
    total = sum(widths) + gap * (len(text) - 1)
    x = RW * 3 / 2 - total / 2
    asc, desc = f.getmetrics()
    y = cy * 3 - (asc + desc) / 2 + 2 * 3
    for c, wd in zip(text, widths):
        d.text((x, y), c, font=f, fill=255)
        x += wd + gap
    for sx in (-1, 1):
        px = RW * 3 / 2 + sx * (total / 2 + 20 * 3)
        py = cy * 3
        s = 4 * 3
        d.polygon([(px, py - s), (px + s, py), (px, py + s), (px - s, py)], fill=255)
    return np.asarray(layer.resize((RW, h), Image.LANCZOS)).astype(np.float32) / 255.0


def ink(rgb, mask, color, strength=0.92):
    m = cv2.GaussianBlur(mask, (0, 0), 0.4) * strength
    return rgb * (1 - m[..., None]) + np.array(color, np.float32) * m[..., None]


def rule(rgb, y, x0, x1, color, strength=0.55):
    layer = np.zeros(rgb.shape[:2], np.uint8)
    cv2.line(layer, (x0, y), (x1, y), 255, 1, cv2.LINE_AA)
    m = layer.astype(np.float32) / 255.0 * strength
    xs = np.arange(rgb.shape[1], dtype=np.float32)
    span = np.clip(np.minimum(xs - x0, x1 - xs) / 110.0, 0, 1)
    m = m * span[None, :]
    return rgb * (1 - m[..., None]) + np.array(color, np.float32) * m[..., None]


ROD_Y_HEAD = 24
ROD_R = 13.0


def build_head():
    h = HEAD_H
    rgb, a = parchment_sheet(h, rows_curl_top=0, seed=4)
    # paper starts under the rod: nothing above its centre line
    ys = np.arange(h, dtype=np.float32)[:, None]
    a = a * np.clip((ys - (ROD_Y_HEAD - 2)) / 3.0, 0, 1)
    # shadow of the rod on the paper
    sh = np.clip(1 - (ys - ROD_Y_HEAD) / 26.0, 0, 1) ** 1.5
    rgb = rgb * (1 - 0.55 * sh[..., None])
    rgb = ink(rgb, title_layer(h, 64), INK)
    rgb = rule(rgb, 88, CAP + 40, CAP + PW - 40, INK_ACCENT, 0.6)
    a = a * np.where(ys > h - FADE, np.clip((h - 1 - ys) / FADE, 0, 1), 1.0)       # melts into the body below
    # the rod
    parts = fix_order(rod_parts(-PW / 2, PW / 2, ROD_R, 4.5))
    rr, ra = render(parts, RW, h, yaw=0.0, pitch=-0.20, cx=RW / 2, cy=ROD_Y_HEAD, scale=1.0, paper_len=PW, seed=2)
    shad = drop_shadow(ra, 5, 4.0, 0.55)
    rgb, a = over(rgb, a, np.zeros_like(rgb), shad * (1 - ra))
    rgb, a = over(rgb, a, rr, ra)
    return finish(rgb, a)


def build_foot():
    h = FOOT_H
    rgb, a = parchment_sheet(h, rows_curl_bottom=0, seed=6)
    ys = np.arange(h, dtype=np.float32)[:, None]
    rod_y = 52
    a = a * np.clip((rod_y + 2 - ys) / 3.0, 0, 1)
    sh = np.clip(1 - (rod_y - ys) / 24.0, 0, 1) ** 1.5
    rgb = rgb * (1 - 0.55 * sh[..., None])
    a = a * np.clip(ys / FADE, 0, 1)                                     # melts into the body above
    parts = fix_order(rod_parts(-PW / 2, PW / 2, ROD_R, 4.5))
    rr, ra = render(parts, RW, h, yaw=0.0, pitch=0.14, cx=RW / 2, cy=rod_y, scale=1.0, paper_len=PW, seed=3)
    shad = drop_shadow(ra, 6, 4.5, 0.5)
    rgb, a = over(rgb, a, np.zeros_like(rgb), shad * (1 - ra))
    rgb, a = over(rgb, a, rr, ra)
    return finish(rgb, a)


def roll_parts():
    r = 31.0
    hw = PW / 2 - 12
    parts = [Part(-hw + 6, hw - 6, r, r, "paper")]
    parts += [Part(-hw - 16, hw + 16, 5.0, 5.0, "bronze")]
    parts += knob_profile(hw + 16, +1, r_neck=5.0, r_bulb=r * 0.5 + 2, neck_len=6, bulb_len=22)
    parts += knob_profile(-hw - 16, -1, r_neck=5.0, r_bulb=r * 0.5 + 2, neck_len=6, bulb_len=22)
    # two cloth bands and a bronze seal in the middle
    parts += [Part(-hw + 90, -hw + 118, r + 1.8, r + 1.8, "cloth"), Part(hw - 118, hw - 90, r + 1.8, r + 1.8, "cloth")]
    parts += [Part(-9, 9, r + 3.2, r + 3.2, "bronze")]
    return fix_order(parts)


def build_roll(yaw_deg=0.0):
    h = HEAD_H + FOOT_H
    rr, ra = render(roll_parts(), RW, h, yaw=math.radians(yaw_deg), pitch=-0.38, cx=RW / 2, cy=h / 2 + 4, scale=1.0, paper_len=PW, seed=5)
    rgb = np.zeros_like(rr)
    a = np.zeros_like(ra)
    # a soft pool of shadow under the roll
    sh = drop_shadow(ra, 18, 9.0, 0.55)
    rgb, a = over(rgb, a, np.zeros_like(rr), sh * (1 - ra))
    rgb, a = over(rgb, a, rr, ra)
    return finish(rgb, a)


# ------------------------------------------------------------------ the news

def wrap(d, text, fnt, width):
    words, out, cur = text.split(" "), [], ""
    for w_ in words:
        t = (cur + " " + w_).strip()
        if d.textlength(t, font=fnt) > width and cur:
            out.append(cur)
            cur = w_
        else:
            cur = t
    if cur:
        out.append(cur)
    return out


NEWS = [
    ("head", "Zeus " + VERSION),
    ("sub", "Hits that are confirmed, a camera-true view and a rebuilt auction sniper"),
    ("gap", 6),
    ("body", "Every swing is matched with the server's answer, so Kill Aura stops hitting a target that is not taking damage and the attack modules show how many hits landed."),
    ("body", "Every overlay comes from the game's own camera and field of view, so ESP boxes, name tags and tracers are right in third person, in freecam and while zooming."),
    ("body", "The AH Sniper reads every listing, pages through the auction house, refreshes, buys the biggest discount and checks the item is really in your inventory."),
    ("body", "Suspicious Chunks also counts plants that should have grown, the Discoveries card waits for a real base, Goliath RTP reports the area and its edges, and /zeus find and /zeus settings read modules back to you."),
    ("gap", 8),
    ("tip", "Open Zeus Customs to turn every module on or off and change each setting."),
    ("gap", 40),
]


def paint_text(w_img, h_img, lines, y_start):
    sc = 2
    heads = Image.new("L", (w_img * sc, h_img * sc), 0)
    subs = Image.new("L", heads.size, 0)
    bodies = Image.new("L", heads.size, 0)
    tips = Image.new("L", heads.size, 0)
    marks = Image.new("L", heads.size, 0)
    dh, ds, db, dt, dm = (ImageDraw.Draw(x) for x in (heads, subs, bodies, tips, marks))
    fh, fs, fb, ft = cinzel(25 * sc, 700), inter(16 * sc, "Medium"), inter(17 * sc, "Medium"), inter(17 * sc, "SemiBold")
    inner = PW - 2 * MARGIN
    x0 = CAP + MARGIN
    y = y_start
    for kind, text in lines:
        if kind == "gap":
            y += int(text)
        elif kind == "head":
            dh.text((x0 * sc, y * sc), text, font=fh, fill=255)
            y += 38
        elif kind == "sub":
            for ln in wrap(ds, text, fs, inner * sc):
                ds.text((x0 * sc, y * sc), ln, font=fs, fill=255)
                y += 23
        elif kind == "body":
            lines_ = wrap(db, text, fb, (inner - 16) * sc)
            y += 8
            # a small diamond as the bullet
            cxm, cym = x0 + 3, y + 10
            dm.polygon([((cxm) * sc, (cym - 4) * sc), ((cxm + 4) * sc, cym * sc), ((cxm) * sc, (cym + 4) * sc), ((cxm - 4) * sc, cym * sc)], fill=255)
            for ln in lines_:
                db.text(((x0 + 16) * sc, y * sc), ln, font=fb, fill=255)
                y += 25
        elif kind == "tip":
            y += 6
            for ln in wrap(dt, text, ft, inner * sc):
                w_ = dt.textlength(ln, font=ft) / sc
                dt.text(((CAP + PW / 2 - w_ / 2) * sc, y * sc), ln, font=ft, fill=255)
                y += 25
    down = lambda im: np.asarray(im.resize((w_img, h_img), Image.LANCZOS)).astype(np.float32) / 255.0
    return [down(x) for x in (heads, subs, bodies, tips, marks)], y


def build_body():
    _, end = paint_text(RW, 4000, NEWS, 14)
    h = end + 26
    rgb, a = parchment_sheet(h, seed=8)
    masks, _ = paint_text(RW, h, NEWS, 14)
    heads, subs, bodies, tips, marks = masks
    rgb = ink(rgb, heads, INK)
    rgb = ink(rgb, subs, INK_ACCENT, 0.88)
    rgb = ink(rgb, bodies, INK, 0.88)
    rgb = ink(rgb, tips, INK_ACCENT, 0.9)
    rgb = ink(rgb, marks, INK_ACCENT, 0.95)
    return finish(rgb, a), h


def main():
    head = build_head()
    head.save(OUT + "/scroll_head.png", optimize=True)
    foot = build_foot()
    foot.save(OUT + "/scroll_foot.png", optimize=True)
    body, body_h = build_body()
    body.save(OUT + "/scroll_body.png", optimize=True)
    roll = build_roll(0.0)
    roll.save(OUT + "/scroll_roll.png", optimize=True)
    for i, yaw in enumerate((20, 13, 6, 0, -6, -13, -20)):
        build_roll(yaw).save(OUT + "/scroll_roll_%d.png" % i, optimize=True)
    print("roll", roll.size, "RW", RW, "PW", PW, "head", head.size, "body", RW, body_h, "foot", foot.size)


if __name__ == "__main__":
    main()
