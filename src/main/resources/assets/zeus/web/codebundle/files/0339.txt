#!/usr/bin/env python3
"""Build the HD knife collection: eight knife types beside the Tactical, each with its own skins.

Run:  python3 tools/knives_hd/make_knives.py            (everything new)
      python3 tools/knives_hd/make_knives.py karambit   (one type)

Uses the same sheet format and baking as make_tactical.py, so KnifeHD draws every knife the same
way.  The new knives are written at detail levels 1 and 2 only (512 and 256 wide); level 0 is only
ever chosen for knives much bigger than the hand shows them, and leaving it out keeps the jar small.
The Tactical entries already in tactical.json are kept as they are and listed first.
"""

import json
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_tactical as T  # noqa: E402
from hd_lib import *  # noqa: E402,F401


# ------------------------------------------------------------------ extra finishes
def fin_camo(kn, mask, sfrac, d_edge, d_spine, bev):
    """Forest camo: blotches of olive, moss, khaki and near-black."""
    n1 = fbm(1.3, 1501, 5)
    n2 = fbm(0.8, 1503, 4)
    alb = np.broadcast_to(hexc("6a6c45"), (GH, GW, 3)).copy()
    alb = lerp(alb, hexc("3e4a2a"), smoothstep(0.50, 0.53, n1))
    alb = lerp(alb, hexc("8f8a62"), smoothstep(0.60, 0.63, n2))
    alb = lerp(alb, hexc("1e2216"), smoothstep(0.62, 0.65, fbm(0.6, 1505, 4)))
    alb *= (0.95 + 0.06 * brushed(1506))[..., None]
    return alb, 0.35, 0.45


def fin_night(kn, mask, sfrac, d_edge, d_spine, bev):
    """Blackened steel: dark, satin, the edge ground back to bright metal."""
    n = fbm(1.0, 1601, 4)
    alb = ramp(n, [(0, hexc("0d0e10")), (0.5, hexc("1b1d21")), (1, hexc("2c2f35"))])
    alb *= (0.92 + 0.1 * brushed(1602))[..., None]
    return alb, 0.8, 0.55


def fin_rust(kn, mask, sfrac, d_edge, d_spine, bev):
    """Rust coat: orange-brown corrosion over dark steel, flaking in patches."""
    n = fbm(1.1, 1701, 5)
    rust = ramp(fbm(0.35, 1703, 4), [(0, hexc("4a1d08")), (0.5, hexc("8a3a12")), (1, hexc("c06a2c"))])
    steel = T.steel_base(1705, 0.55)
    alb = lerp(steel, rust, smoothstep(0.38, 0.5, n))
    return alb, 0.3, 0.3


def fin_gold(kn, mask, sfrac, d_edge, d_spine, bev):
    """Polished gold with a faint engraved scroll pattern."""
    n = fbm(1.4, 1801, 4)
    alb = ramp(0.55 + 0.3 * (n - 0.5) + 0.12 * sfrac, [(0, hexc("7a4e0e")), (0.45, hexc("c8901e")),
                                                       (0.75, hexc("f2c650")), (1, hexc("fff0b0"))])
    scroll = np.abs(np.sin(U * 3.1 + 2.2 * np.sin(V * 2.4 + U * 0.6)))
    alb = alb * (1 - 0.18 * smoothstep(0.12, 0.0, scroll)[..., None])
    alb *= (0.96 + 0.05 * brushed(1802))[..., None]
    return alb, 1.0, 1.0


def fin_bloodstone(kn, mask, sfrac, d_edge, d_spine, bev):
    """Dark red marble: deep crimson clouds over near-black, with hot pink veins where they meet."""
    n = fbm(0.9, 1901, 5)
    m = fbm(2.2, 1903, 4)
    tone = np.clip(n * 0.85 + 0.25 * (m - 0.5), 0, 1)
    alb = ramp(tone, [(0, hexc("140306")), (0.35, hexc("3c0610")), (0.55, hexc("8a0f22")),
                      (0.72, hexc("c8213e")), (1, hexc("ff6a86"))])
    vein = np.exp(-((n - 0.62) / 0.025) ** 2)
    alb = lerp(alb, np.minimum(1, alb * 1.5 + 0.10), vein * 0.5)
    alb *= (0.95 + 0.06 * brushed(1905))[..., None]
    return alb, 0.9, 0.85


def ridged(scale, seed, octaves=5):
    """Sharp branching ridges: 1 on the ridge lines, falling off either side."""
    return 1 - np.abs(2 * fbm(scale, seed, octaves) - 1)


def hsv(h, s_, v):
    h = (h % 1.0) * 6
    i = np.floor(h).astype(int) % 6
    f = h - np.floor(h)
    p, q, t = v * (1 - s_), v * (1 - s_ * f), v * (1 - s_ * (1 - f))
    r = np.choose(i, [v, q, p, p, t, v])
    g = np.choose(i, [t, v, v, q, p, p])
    b = np.choose(i, [p, p, t, v, v, q])
    return np.stack([r, g, b], -1).astype(np.float32)


def fin_stock(kn, mask, sfrac, d_edge, d_spine, bev):
    """Factory satin steel: the starter finish."""
    alb = T.steel_base(2001, 0.82)
    alb *= (0.95 + 0.07 * brushed(2002))[..., None]
    return alb, 1.0, 0.7


def fin_lightning(kn, mask, sfrac, d_edge, d_spine, bev):
    """Zeus' own: blued black steel with forked electric bolts that glow."""
    base = ramp(fbm(1.2, 2101, 4), [(0, hexc("05070d")), (0.5, hexc("0c1424")), (1, hexc("1a2a48"))])
    r = ridged(3.0, 2103, 6)
    keep = smoothstep(0.45, 0.6, fbm(2.2, 2109, 3))
    core = smoothstep(0.955, 0.985, r) * keep
    halo = smoothstep(0.82, 0.96, r) * keep
    fork = smoothstep(0.965, 0.99, ridged(1.1, 2105, 5)) * smoothstep(0.6, 0.75, fbm(1.0, 2107, 3)) * keep
    alb = base + halo[..., None] * hexc("2a6cff") * 0.55
    alb = lerp(alb, hexc("bfe6ff"), np.clip(core + fork, 0, 1))
    return np.clip(alb, 0, 1), 0.85, 0.9


def fin_olympus(kn, mask, sfrac, d_edge, d_spine, bev):
    """White Olympian marble with grey veins and veins of gold."""
    n = fbm(1.4, 2201, 5)
    alb = ramp(n, [(0, hexc("d8d4cc")), (0.5, hexc("efece6")), (1, hexc("ffffff"))])
    grey = smoothstep(0.93, 0.98, ridged(2.2, 2203, 6)) * 0.8
    gold = smoothstep(0.965, 0.99, ridged(3.2, 2205, 5))
    alb = lerp(alb, hexc("8a8680"), grey * 0.7)
    alb = lerp(alb, ramp(fbm(0.4, 2207, 3), [(0, hexc("b07a18")), (1, hexc("ffd86a"))]), gold)
    return alb, 0.35, 0.95


def fin_inferno(kn, mask, sfrac, d_edge, d_spine, bev):
    """Charred black steel split by glowing lava cracks, hottest towards the tip."""
    base = ramp(fbm(0.9, 2301, 4), [(0, hexc("070505")), (1, hexc("2a1a14"))])
    r = ridged(2.0, 2303, 6)
    heat = np.clip(0.35 + 0.65 * sfrac, 0, 1)
    crack = smoothstep(0.86, 0.96, r) * (0.6 + 0.4 * heat)
    glow_ = smoothstep(0.70, 0.90, r) * heat
    lava = ramp(r, [(0.8, hexc("7a1404")), (0.9, hexc("ff5a0a")), (1.0, hexc("ffe27a"))])
    alb = base + glow_[..., None] * hexc("ff3a00") * 0.35
    alb = lerp(alb, lava, crack)
    return np.clip(alb, 0, 1), 0.6, 0.6


def fin_nebula(kn, mask, sfrac, d_edge, d_spine, bev):
    """Deep space: violet and teal gas clouds over black, scattered with stars."""
    a = fbm(1.8, 2401, 6)
    b = fbm(1.2, 2403, 5)
    alb = hexc("04030a") + smoothstep(0.45, 0.85, a)[..., None] * hexc("7a2ad8") * 0.9
    alb = alb + smoothstep(0.5, 0.9, b)[..., None] * hexc("1aa6c8") * 0.6
    alb = alb + smoothstep(0.7, 0.95, a * b * 1.6)[..., None] * hexc("ff4ab0") * 0.5
    stars = (np.random.default_rng(2405).random((GH, GW)) > 0.9985).astype(np.float32)
    from scipy import ndimage
    stars = np.clip(ndimage.gaussian_filter(stars, 0.8) * 7, 0, 1)
    alb = lerp(alb, np.ones_like(alb), stars)
    return np.clip(alb, 0, 1), 0.8, 0.9


def fin_frost(kn, mask, sfrac, d_edge, d_spine, bev):
    """Ice: pale blue steel grown over with feathered frost crystals."""
    base = ramp(fbm(1.0, 2501, 4) * 0.6 + 0.4 * sfrac, [(0, hexc("5f8fb8")), (0.5, hexc("a8d4f0")), (1, hexc("e8f8ff"))])
    feath = 0
    for k, ang in enumerate((0.5, 1.6, 2.6)):
        d = U * math.cos(ang) + V * math.sin(ang)
        feath = feath + smoothstep(0.82, 0.98, np.abs(np.sin(d * 11 + 6 * fbm(0.8, 2503 + k, 3))))
    feath = np.clip(feath, 0, 1) * smoothstep(0.35, 0.6, fbm(1.3, 2507, 4))
    alb = lerp(base, hexc("ffffff"), feath * 0.75)
    return alb, 0.55, 0.95


def fin_phantom(kn, mask, sfrac, d_edge, d_spine, bev):
    """Holographic: iridescent foil that shifts through every colour."""
    n = fbm(1.5, 2601, 5)
    hue = 0.62 + 0.30 * (n - 0.5) * 2 + 0.012 * U
    alb = hsv(hue, 0.38 + 0.12 * fbm(0.6, 2603, 3), 0.93)
    sheen = fbm(2.5, 2605, 3, aniso=(3.0, 0.5))
    alb = lerp(alb, hexc("ffffff"), smoothstep(0.6, 0.85, sheen) * 0.35)
    alb = lerp(alb, alb * 0.75, smoothstep(0.45, 0.25, sheen) * 0.5)
    return alb, 1.0, 1.0


def fin_toxic(kn, mask, sfrac, d_edge, d_spine, bev):
    """Black steel splashed with glowing acid green that has eaten into the surface."""
    base = ramp(fbm(1.0, 2701, 4), [(0, hexc("060806")), (1, hexc("1a201a"))])
    n = fbm(1.1, 2703, 6)
    goo = smoothstep(0.56, 0.60, n)
    rim = smoothstep(0.50, 0.56, n) * (1 - goo)
    acid = ramp(fbm(0.4, 2705, 3), [(0, hexc("3ad80a")), (1, hexc("c8ff3a"))])
    alb = lerp(base, hexc("1e4a08"), rim)
    alb = lerp(alb, acid, goo)
    return alb, 0.5, 0.75


def fin_sunset(kn, mask, sfrac, d_edge, d_spine, bev):
    """A sunset fade: violet at the guard through pink and orange to gold at the tip."""
    t = np.clip(sfrac * 0.9 + 0.08 * (fbm(0.9, 2801, 3) - 0.5) + 0.05 * V / 3, 0, 1)
    alb = ramp(t, [(0, hexc("2a0f5a")), (0.3, hexc("a0207a")), (0.55, hexc("ff4a5a")), (0.78, hexc("ff9a2a")), (1, hexc("ffe07a"))])
    alb *= (0.95 + 0.07 * brushed(2802))[..., None]
    return alb, 1.0, 1.0


def fin_storm(kn, mask, sfrac, d_edge, d_spine, bev):
    """Thunderhead: rolling slate storm clouds with a single bright fork."""
    n = fbm(1.6, 2901, 6)
    alb = ramp(n, [(0, hexc("1a1e26")), (0.45, hexc("3a4250")), (0.7, hexc("6a7486")), (1, hexc("b8c2d0"))])
    bolt = smoothstep(0.965, 0.99, ridged(2.4, 2903, 6)) * smoothstep(0.5, 0.7, fbm(2.0, 2905, 2))
    alb = alb + smoothstep(0.85, 0.97, ridged(2.4, 2903, 6))[..., None] * hexc("6aa0ff") * 0.25 * smoothstep(0.5, 0.7, fbm(2.0, 2905, 2))[..., None]
    alb = lerp(alb, hexc("e8f2ff"), bolt)
    return np.clip(alb, 0, 1), 0.6, 0.75


def fin_abyss(kn, mask, sfrac, d_edge, d_spine, bev):
    """The deep sea: teal fading to black, lit by rippling caustics."""
    base = ramp(np.clip(1 - sfrac * 0.8 + 0.2 * (fbm(1.0, 3001, 4) - 0.5), 0, 1), [(0, hexc("01060a")), (0.5, hexc("06323e")), (1, hexc("0c6a78"))])
    c = np.abs(np.sin(fbm(2.4, 3003, 5) * 14.0))
    caus = smoothstep(0.955, 0.995, c) * smoothstep(0.2, 0.7, 1 - sfrac + 0.3)
    alb = lerp(base, hexc("7af0ff"), caus * 0.7)
    return alb, 0.8, 0.9


F = {
    "damascus": (T.fin_damascus, "Damascus", "#dfe6ef"),
    "slaughter": (T.fin_slaughter, "Slaughter", "#ff4a3a"),
    "case": (T.fin_case, "Case hardened", "#ffd166"),
    "fade": (T.fin_fade, "Fade", "#b04cff"),
    "crimson": (T.fin_crimson, "Crimson web", "#ff2a3c"),
    "tiger": (T.fin_tiger, "Tiger tooth", "#ffb030"),
    "sapphire": (T.fin_doppler, "Sapphire", "#4a7dff"),
    "urban": (T.fin_urban, "Urban masked", "#c8d0dc"),
    "emerald": (T.fin_emerald, "Emerald", "#34e07a"),
    "marble": (T.fin_marble, "Marble fade", "#ff8a2a"),
    "ruby": (T.fin_ruby, "Ruby", "#ff2a48"),
    "ultraviolet": (T.fin_ultraviolet, "Ultraviolet", "#9a6cff"),
    "camo": (fin_camo, "Forest camo", "#a3b56a"),
    "night": (fin_night, "Night", "#8c96a6"),
    "rust": (fin_rust, "Rust coat", "#d07030"),
    "gold": (fin_gold, "Gold leaf", "#ffcf4a"),
    "bloodstone": (fin_bloodstone, "Bloodstone", "#e0283c"),
    "stock": (fin_stock, "Stock", "#c8ccd4"),
    "lightning": (fin_lightning, "Lightning", "#6ab8ff"),
    "olympus": (fin_olympus, "Olympus", "#ffd86a"),
    "inferno": (fin_inferno, "Inferno", "#ff6a1a"),
    "nebula": (fin_nebula, "Nebula", "#b05aff"),
    "frost": (fin_frost, "Frost", "#a8e0ff"),
    "phantom": (fin_phantom, "Phantom", "#f0a8ff"),
    "toxic": (fin_toxic, "Toxic", "#8aff2a"),
    "sunset": (fin_sunset, "Sunset", "#ff7a4a"),
    "storm": (fin_storm, "Thunderhead", "#9ab0d0"),
    "abyss": (fin_abyss, "Abyss", "#2ad0e0"),
}


def accent(skin):
    """A handle accent colour that goes with the blade."""
    return hexc(F[skin][2].lstrip("#"))


def wood(seed, tone="5a3a24"):
    grain = np.sin(U * 1.5 + 6 * fbm(0.9, seed, 4, aniso=(4.0, 0.3))) * 0.5 + 0.5
    base = hexc(tone)
    return np.broadcast_to(base, (GH, GW, 3)) * (0.78 + 0.32 * grain[..., None]) * (0.92 + 0.12 * fbm(0.3, seed + 1, 3))[..., None]


def slot(kn, u0, u1, v, r):
    d, m = capsule_mask(u0, v, u1, v, r)
    kn.cut(m)


def finish_knife(kn, skin):
    kn.glow = F[skin][2]
    return kn


# ------------------------------------------------------------------ the types
# The karambit is traced from a reference picture: its outline (ring, finger grooves, guard and the
# claw) lives in ref/karambit_outline.json as a smoothed outline polygon, and is laid onto the sheet
# along the handle's axis. Points below are pixels of that picture.
KARAMBIT_REF = os.path.join(os.path.dirname(os.path.abspath(__file__)), "ref", "karambit_outline.json")
K_RING = (252.0, 43.0)                        # centre of the finger ring
K_AXIS = (-0.8805, 0.4747)                    # along the handle, ring towards blade
K_ACROSS = (0.4747, 0.8805)                   # across, towards the inside of the curve
K_SCALE = 0.0954                              # knife units per picture pixel
K_U0 = 1.6 + 31 * K_SCALE                     # sheet u of the ring centre
K_JUNCTION = ((112.0, 89.0), (163.0, 122.0))  # where the guard meets the blade
K_TIP = (100.0, 246.0)
K_CURL = (178.0, 196.0)                       # a point inside the curve, for telling edge from spine
KARAMBIT_CELL_H = 768                         # taller sheet: the claw hangs far below the handle


def _to_pic(u, v):
    du, dv = (u - K_U0) / K_SCALE, v / K_SCALE
    return (K_RING[0] + du * K_AXIS[0] + dv * K_ACROSS[0], K_RING[1] + du * K_AXIS[1] + dv * K_ACROSS[1])


def _to_sheet(x, y):
    dx, dy = x - K_RING[0], y - K_RING[1]
    return (K_U0 + K_SCALE * (dx * K_AXIS[0] + dy * K_AXIS[1]), K_SCALE * (dx * K_ACROSS[0] + dy * K_ACROSS[1]))


def karambit(skin):
    from scipy import ndimage
    kn = T.Knife("karambit_" + skin, "Karambit · " + F[skin][1])
    ref = json.load(open(KARAMBIT_REF))
    sil = poly_mask([_to_sheet(x, y) for x, y in ref["outline"]])
    hx, hy, hr = ref["ring_hole"]
    X, Y = _to_pic(U, V)
    ring_hole = (np.hypot(X - hx, Y - hy) < hr).astype(np.float32)
    sil = sil * (1 - ring_hole)

    # regions in picture space
    (jx1, jy1), (jx2, jy2) = K_JUNCTION
    nx, ny = -(jy2 - jy1), (jx2 - jx1)
    nl = math.hypot(nx, ny)
    nx, ny = nx / nl, ny / nl
    if (K_TIP[0] - jx1) * nx + (K_TIP[1] - jy1) * ny < 0:
        nx, ny = -nx, -ny
    dj = ((X - jx1) * nx + (Y - jy1) * ny) * K_SCALE                 # signed distance past the guard, units
    tip_d = ((K_TIP[0] - jx1) * nx + (K_TIP[1] - jy1) * ny) * K_SCALE
    ring_d = np.hypot(X - K_RING[0], Y - K_RING[1]) * K_SCALE
    blade_m = sil * smoothstep(-0.03, 0.03, dj)
    ring_m = sil * (1 - smoothstep(-0.03, 0.03, dj)) * (ring_d < 2.35)
    handle_m = np.clip(sil - blade_m - ring_m, 0, 1)

    # ---- handle: dark grey rubber, rounded body, knurled panels, finger grooves from the outline
    inside = sdf_inside((handle_m + ring_m > 0.5).astype(np.float32))
    body_h = np.clip(inside / 0.55, 0, 1) ** 0.6 * 0.30
    T.rubber(kn, handle_m, hexc("36383d"), 1901, rnd=0.6)
    kn.height += body_h * handle_m
    panel = handle_m * smoothstep(0.30, 0.42, inside) * smoothstep(K_U0 + 2.6, K_U0 + 3.2, U) * (dj < -0.9)
    kn_a = np.abs(np.sin((U + V) * 9.0)) * np.abs(np.sin((U - V) * 9.0))
    kn.alb = lerp(kn.alb, kn.alb * (0.55 + 0.6 * kn_a)[..., None], panel * 0.9)
    kn.height += -0.05 * panel * (1 - kn_a)
    # a moulded lip round each panel, a seam where the guard meets the blade
    lip = np.clip(1 - np.abs(inside - 0.30) / 0.05, 0, 1) * handle_m * smoothstep(K_U0 + 2.6, K_U0 + 3.2, U) * (dj < -0.9)
    kn.alb = lerp(kn.alb, np.minimum(1, kn.alb * 1.6 + 0.04), lip * 0.6)
    seam = np.exp(-((dj + 0.06) / 0.05) ** 2) * sil
    kn.alb = lerp(kn.alb, kn.alb * 0.3, seam * 0.8)
    # spine jimping: little notches along the top of the handle near the guard and near the ring
    top = np.where(handle_m > 0.5, V, 99).min(0)[None, :]
    for a, b in ((K_U0 + 8.8, K_U0 + 11.0), (K_U0 + 1.6, K_U0 + 3.6)):
        ph = ((U - a) / 0.26) % 1.0
        notch = (U > a) & (U < b) & (V < top + 0.12 * (1 - np.abs(ph - 0.5) * 2))
        kn.cut((notch & (handle_m > 0)).astype(np.float32))
    for cu in (K_U0 + 5.0, K_U0 + 9.0):
        T.screw(kn, cu, float(np.interp(cu, U[0], np.where(handle_m > 0.5, V, 0).sum(0) / np.maximum((handle_m > 0.5).sum(0), 1))) - 0.25,
                0.17, colour="4a4d55", metal=1.0)

    # ---- blade: spine on the outside of the curve, edge on the inside
    finish = F[skin][0]
    gx = ndimage.sobel(ndimage.gaussian_filter(blade_m, 2.0), 1)
    gy = ndimage.sobel(ndimage.gaussian_filter(blade_m, 2.0), 0)
    cu_, cv_ = _to_sheet(*K_CURL)
    toward = (cu_ - U) * (-gx) + (cv_ - V) * (-gy)                     # outward normal . direction to the curl
    bnd = (blade_m > 0.5) & (ndimage.binary_dilation(blade_m <= 0.5, iterations=1)) & (dj > 0.08)
    edge_px = bnd & (toward > 0)
    spine_px = bnd & (toward <= 0)
    d_edge = ndimage.distance_transform_edt(~edge_px) / GP
    d_spine = ndimage.distance_transform_edt(~spine_px) / GP
    sfrac = np.clip(dj / tip_d, 0, 1)
    width = d_edge + d_spine
    bw = np.minimum(0.85, 0.62 * width)
    bev = np.clip(1 - d_edge / np.maximum(bw, 1e-3), 0, 1)
    h = -0.42 * bev ** 1.15 - 0.10 * np.clip(1 - d_spine / 0.12, 0, 1)
    alb, metal, gloss = finish(kn, blade_m, sfrac, d_edge, d_spine, bev)
    alb = alb * (1 + 0.20 * bev[..., None])
    edge_line = np.clip(1 - d_edge / 0.08, 0, 1)
    alb = lerp(alb, np.minimum(1, alb * 1.7 + 0.35), edge_line * 0.85)
    seam_b = np.exp(-((d_edge - bw) / 0.035) ** 2) * (bw > 0.2)
    alb = alb * (1 - 0.25 * seam_b[..., None])
    scr = T.scratches(kn.id, K_U0 + 10, 27.5, -1.0, 10.0) * blade_m
    alb = lerp(alb, np.minimum(1, alb * 1.35 + 0.07), scr[..., None] * 0.45)
    side = np.ones_like(alb) * hexc("8e949c")[None, None, :] * 0.85
    kn.paint(blade_m, alb, height=h, metal=metal, gloss=gloss, bevel=np.clip(bev * 0.85 + edge_line, 0, 1), side=side)

    # ---- ring: the tang comes through the handle as a steel finger ring in the blade's finish
    ra, rm_, rg = finish(kn, ring_m, np.zeros_like(U), np.full_like(U, 1.0), np.full_like(U, 1.0), np.zeros_like(U))
    rin = sdf_inside((ring_m > 0.5).astype(np.float32))
    rh = np.clip(rin / 0.35, 0, 1) ** 0.5 * 0.2
    lipr = np.clip(1 - rin / 0.10, 0, 1)
    ra = lerp(ra, np.minimum(1, ra * 1.5 + 0.12), lipr * 0.6)
    kn.paint(ring_m, ra, height=rh, metal=rm_, gloss=rg, bevel=lipr * 0.7, rnd=0.2, side=hexc("3a3e46"))

    tip_u, tip_v = _to_sheet(*K_TIP)
    kn.tip = (tip_u, tip_v)
    ring_end = K_U0 + 2.3
    ju = min(_to_sheet(*K_JUNCTION[0])[0], _to_sheet(*K_JUNCTION[1])[0])
    kn.part("ring", 0.0, ring_end, 0.40, 0.30)
    kn.part("handle", ring_end, ju + 0.6, 0.95, 0.55)
    kn.part("blade", ju + 0.6, 28.2, 0.24, 0.0)
    return finish_knife(kn, skin)


def m9(skin):
    kn = T.Knife("m9_" + skin, "M9 Bayonet · " + F[skin][1])
    st = T.interp_stations([(0.6, 1.25, 1.25), (1.8, 1.35, 1.40), (6.0, 1.30, 1.35), (10.0, 1.32, 1.38), (11.9, 1.30, 1.35)])
    m, inside = T.handle_body(kn, st)
    T.rubber(kn, m, hexc("2c3324") if skin == "camo" else hexc("18191b"), 2001, rnd=0.6)
    rb, zone, ph = T.ribs(2.2, 11.5, 0.5, 0.12)
    kn.height += rb * m
    kn.alb = kn.alb * (1 - 0.35 * (zone * m * (1 - np.sin(ph * math.pi) ** 2))[..., None])
    cap = poly_mask(T.outline(T.interp_stations([(0.2, 1.1, 1.1), (0.7, 1.3, 1.3), (1.6, 1.35, 1.35)])))
    kn.paint(cap, T.steel_base(2003, 0.6), height=np.clip(sdf_inside(cap) / 0.3, 0, 1) * 0.15, metal=1.0, gloss=0.8,
             rnd=0.12, side=hexc("3a3e46"))
    T.hole(kn, 0.95, 0.0, 0.22, lip_colour=True)
    # guard with the muzzle ring above the spine
    g = poly_mask([(11.9, -2.6), (12.6, -2.6), (12.6, 2.0), (11.9, 2.0)])
    kn.paint(g, T.steel_base(2005, 0.5), height=np.clip(sdf_inside(g) / 0.15, 0, 1) * 0.1, metal=1.0, gloss=0.7,
             rnd=0.1, side=hexc("3a3e46"))
    d = np.hypot(U - 12.25, V + 2.6)
    ringm = ((d <= 0.75) & (d >= 0.42)).astype(np.float32)
    kn.paint(ringm, T.steel_base(2007, 0.5), metal=1.0, gloss=0.7, rnd=0.15, side=hexc("3a3e46"))
    T.blade(kn, 12.6, 28.0, -1.45, 1.6, -0.35, clip=0.72, belly=0.70, clip_pow=1.2, teeth=(0.08, 0.5, 0.42, 0.62),
            fuller=(0.06, 0.5, -0.6, 0.13), finish=F[skin][0], tang=0.5)
    kn.part("handle", 0.2, 11.9, 1.05, 0.6)
    kn.part("guard", 11.9, 12.6, 0.9, 0.1)
    kn.part("blade", 12.6, 28.2, 0.26, 0.0)
    return finish_knife(kn, skin)


def butterfly(skin):
    kn = T.Knife("butterfly_" + skin, "Butterfly · " + F[skin][1])
    col = accent(skin)
    for top, (a, b) in ((True, (-1.25, -0.06)), (False, (0.06, 1.25))):
        poly = [(1.0, a + 0.15), (1.4, a), (12.4, a), (12.9, a + 0.2), (12.9, b - 0.2), (12.4, b), (1.4, b), (1.0, b - 0.15)]
        hm = poly_mask(poly)
        tone = lerp(T.steel_base(2101 + top, 0.75), np.broadcast_to(col, (GH, GW, 3)), 0.55)
        kn.paint(hm, tone, height=np.clip(sdf_inside(hm) / 0.25, 0, 1) * 0.12, metal=1.0, gloss=0.85, rnd=0.15,
                 side=col * 0.5)
        mid = (a + b) / 2
        slot(kn, 2.6, 5.6, mid, 0.17)
        slot(kn, 6.6, 9.6, mid, 0.17)
    # latch and pivot pins
    lm = poly_mask([(0.3, -0.35), (1.2, -0.35), (1.2, 0.35), (0.3, 0.35)])
    kn.paint(lm, T.steel_base(2105, 0.45), metal=1.0, gloss=0.8, rnd=0.1, side=hexc("2a2c30"))
    for cv in (-0.7, 0.7):
        T.screw(kn, 12.2, cv, 0.2, kind="pin", colour="9aa0a8", metal=1.0)
    T.blade(kn, 12.9, 27.4, -1.0, 1.1, -0.25, clip=0.58, belly=0.64, clip_pow=1.3, teeth=None, fuller=None,
            finish=F[skin][0], tang=0.4, bevel_w=0.85)
    kn.part("handle", 0.3, 12.9, 0.7, 0.2)
    kn.part("blade", 12.9, 27.6, 0.22, 0.0)
    return finish_knife(kn, skin)


def huntsman(skin):
    kn = T.Knife("huntsman_" + skin, "Huntsman · " + F[skin][1])
    st = T.interp_stations([(0.5, 1.20, 1.30), (1.4, 1.55, 1.65), (4.0, 1.40, 1.55), (8.0, 1.40, 1.60), (11.4, 1.55, 1.75), (12.2, 1.45, 1.60)])
    st = T.finger_grooves(st, 5.5, 11.6, 3, 0.26)
    m, inside = T.handle_body(kn, st)
    T.rubber(kn, m, hexc("121214"), 2201, rnd=0.6)
    T.hole(kn, 1.1, 0.0, 0.26, lip_colour=True)
    for cu in (3.2, 9.4):
        T.screw(kn, cu, 0.0, 0.2, colour="3a3c42")
    bol = poly_mask([(12.2, -1.75), (12.8, -1.75), (12.8, 2.1), (12.2, 2.0)])
    kn.paint(bol, T.steel_base(2203, 0.45), height=np.clip(sdf_inside(bol) / 0.2, 0, 1) * 0.1, metal=1.0, gloss=0.7,
             rnd=0.1, side=hexc("2a2c30"))
    T.blade(kn, 12.8, 27.6, -1.65, 1.95, -1.35, clip=0.84, belly=0.80, clip_pow=0.7, teeth=(0.06, 0.48, 0.48, 0.66),
            fuller=None, finish=F[skin][0], tang=0.5)
    kn.part("handle", 0.3, 12.2, 1.05, 0.6)
    kn.part("guard", 12.2, 12.8, 1.0, 0.1)
    kn.part("blade", 12.8, 27.8, 0.28, 0.0)
    return finish_knife(kn, skin)


def bowie(skin):
    kn = T.Knife("bowie_" + skin, "Bowie · " + F[skin][1])
    st = T.interp_stations([(0.6, 1.10, 1.15), (1.4, 1.35, 1.40), (6.0, 1.25, 1.30), (11.0, 1.20, 1.25), (12.0, 1.15, 1.20)])
    m, inside = T.handle_body(kn, st)
    kn.paint(m, wood(2301), height=np.clip(inside / 0.4, 0, 1) * 0.1, metal=0.0, gloss=0.45, rnd=0.6, side=hexc("2e1c10"))
    cap = poly_mask(T.outline(T.interp_stations([(0.2, 1.0, 1.0), (0.6, 1.2, 1.2), (1.4, 1.38, 1.42)])))
    T.brass(kn, cap, h=np.clip(sdf_inside(cap) / 0.3, 0, 1) ** 0.5 * 0.2, seed=2303)
    for cu in (4.0, 8.5):
        T.screw(kn, cu, 0.0, 0.17, kind="pin", colour="c89a3c", metal=1.0)
    T.guard_cross(kn, 12.0, 12.8, 2.2, 2.3, knob=0.38, curve=0.18, seed=2305)
    T.blade(kn, 12.8, 28.0, -1.55, 1.85, -0.55, clip=0.56, belly=0.62, clip_pow=1.6, teeth=None,
            serr=(0.04, 0.22, 0.22, 0.38), fuller=None, finish=F[skin][0], tang=0.6)
    kn.part("pommel", 0.2, 1.4, 1.05, 0.3)
    kn.part("handle", 1.4, 12.0, 1.0, 0.55)
    kn.part("guard", 12.0, 12.8, 1.15, 0.22)
    kn.part("blade", 12.8, 28.2, 0.27, 0.0)
    return finish_knife(kn, skin)


def skeleton(skin):
    kn = T.Knife("skeleton_" + skin, "Skeleton · " + F[skin][1])
    st = T.interp_stations([(0.4, 0.25, 0.25), (1.3, 0.70, 0.70), (3.0, 0.82, 0.82), (10.0, 0.78, 0.78), (11.4, 0.85, 0.85)])
    m, inside = T.handle_body(kn, st)
    T.paracord(kn, m, 2.2, 10.8, hexc("1a1c1f"), 2401)
    tipm = poly_mask(T.outline(T.interp_stations([(0.1, 0.05, 0.05), (1.0, 0.55, 0.55), (2.2, 0.8, 0.8)])))
    kn.paint(tipm, T.steel_base(2403, 0.6), metal=1.0, gloss=0.8, rnd=0.1, side=hexc("3a3e46"))
    T.blade(kn, 11.4, 27.6, -1.35, 1.35, 0.0, clip=0.55, belly=0.55, clip_pow=1.25, teeth=None, fuller=None,
            choil=False, finish=F[skin][0], tang=0.5)
    # the finger ring cut through the base of the blade
    d = np.hypot(U - 13.2, V - 0.0)
    kn.cut((d <= 0.62).astype(np.float32))
    ringlip = ((d > 0.62) & (d < 0.78)).astype(np.float32) * kn.mask
    kn.alb = lerp(kn.alb, np.minimum(1, kn.alb * 1.35 + 0.08), ringlip * 0.7)
    kn.part("handle", 0.1, 11.4, 0.85, 0.55)
    kn.part("blade", 11.4, 27.8, 0.26, 0.0)
    return finish_knife(kn, skin)


def ursus(skin):
    kn = T.Knife("ursus_" + skin, "Ursus · " + F[skin][1])
    st = T.interp_stations([(0.5, 1.10, 1.25), (1.2, 1.50, 1.60), (4.0, 1.40, 1.55), (8.0, 1.38, 1.60), (11.2, 1.50, 1.80), (12.0, 1.40, 1.65)])
    st = T.finger_grooves(st, 5.0, 11.4, 3, 0.22)
    m, inside = T.handle_body(kn, st)
    kn.paint(m, T.g10(hexc("15161a"), 2501), height=np.zeros((GH, GW), np.float32), metal=0.0, gloss=0.5, rnd=0.55,
             side=hexc("0a0a0c"))
    for cu in (3.0, 7.0, 10.6):
        T.screw(kn, cu, 0.05, 0.2)
    bol = poly_mask([(12.0, -1.6), (12.7, -1.6), (12.7, 2.3), (12.3, 2.5), (12.0, 2.0)])
    T.brass(kn, bol, h=np.clip(sdf_inside(bol) / 0.2, 0, 1) * 0.12, seed=2503) if skin == "gold" else \
        kn.paint(bol, T.steel_base(2505, 0.6), height=np.clip(sdf_inside(bol) / 0.2, 0, 1) * 0.12, metal=1.0, gloss=0.8,
                 rnd=0.12, side=hexc("3a3e46"))
    T.blade(kn, 12.7, 27.8, -1.6, 2.15, -0.75, clip=0.70, belly=0.60, clip_pow=1.2, teeth=None,
            fuller=(0.06, 0.62, -0.55, 0.16), finish=F[skin][0], tang=0.5)
    kn.part("handle", 0.3, 12.0, 1.05, 0.55)
    kn.part("guard", 12.0, 12.7, 1.0, 0.12)
    kn.part("blade", 12.7, 28.0, 0.28, 0.0)
    return finish_knife(kn, skin)


def stiletto(skin):
    kn = T.Knife("stiletto_" + skin, "Stiletto · " + F[skin][1])
    st = T.interp_stations([(1.2, 0.75, 0.75), (2.0, 0.85, 0.85), (8.0, 0.80, 0.80), (11.4, 0.85, 0.85)])
    m, inside = T.handle_body(kn, st)
    kn.paint(m, wood(2601, "1a1614"), height=np.clip(inside / 0.4, 0, 1) * 0.08, metal=0.0, gloss=0.7, rnd=0.6,
             side=hexc("0c0a0a"))
    for a, b in ((0.4, 2.0), (11.2, 12.4)):
        bm = poly_mask(T.outline(T.interp_stations([(a, 0.72, 0.72), (a + 0.3, 0.9, 0.9), (b - 0.3, 0.9, 0.9), (b, 0.8, 0.8)])))
        kn.paint(bm, T.steel_base(2603 + int(a), 0.75), height=np.clip(sdf_inside(bm) / 0.2, 0, 1) * 0.12, metal=1.0,
                 gloss=0.9, rnd=0.2, side=hexc("4a4e55"))
    T.screw(kn, 6.0, -0.55, 0.16, kind="pin", colour="b8bcc4", metal=1.0)
    # thin crossbar with ball ends
    g = poly_mask([(12.4, -1.2), (12.75, -1.2), (12.75, 1.2), (12.4, 1.2)])
    kn.paint(g, T.steel_base(2605, 0.7), metal=1.0, gloss=0.9, rnd=0.12, side=hexc("4a4e55"))
    for cv in (-1.25, 1.25):
        mm, hh, _ = T.dome(12.58, cv, 0.26)
        kn.paint(mm, T.steel_base(2607, 0.8), height=hh, metal=1.0, gloss=0.9, rnd=0.2, side=hexc("4a4e55"))
    T.blade(kn, 12.75, 28.0, -0.62, 0.66, 0.0, clip=0.42, belly=0.42, clip_pow=1.15, teeth=None, fuller=None,
            choil=False, bevel_w=0.5, finish=F[skin][0], tang=0.4)
    kn.part("handle", 0.4, 12.4, 0.8, 0.5)
    kn.part("guard", 12.4, 12.75, 0.6, 0.12)
    kn.part("blade", 12.75, 28.2, 0.2, 0.0)
    return finish_knife(kn, skin)


TYPES = [
    ("karambit", karambit, ["bloodstone", "crimson", "sapphire", "fade", "tiger", "slaughter"]),
    ("m9", m9, ["camo", "damascus", "case", "ruby", "night"]),
    ("butterfly", butterfly, ["fade", "marble", "emerald", "slaughter", "ultraviolet"]),
    ("huntsman", huntsman, ["night", "urban", "tiger", "case", "crimson"]),
    ("bowie", bowie, ["damascus", "slaughter", "fade", "sapphire", "rust"]),
    ("skeleton", skeleton, ["case", "fade", "marble", "emerald", "night"]),
    ("ursus", ursus, ["gold", "sapphire", "ruby", "tiger", "urban"]),
    ("stiletto", stiletto, ["fade", "damascus", "case", "night", "marble"]),
]


# New skins, appended after everything above so the index of every older knife stays the same.
EXTRA = [("karambit", "stock"),
         ("tactical", "lightning"), ("tactical", "olympus"),
         ("karambit", "inferno"), ("karambit", "nebula"),
         ("m9", "frost"), ("m9", "phantom"),
         ("butterfly", "toxic"), ("butterfly", "sunset"),
         ("huntsman", "storm"), ("huntsman", "toxic"),
         ("bowie", "inferno"), ("bowie", "olympus"),
         ("skeleton", "abyss"), ("skeleton", "phantom"),
         ("ursus", "frost"), ("ursus", "nebula"),
         ("stiletto", "sunset"), ("stiletto", "abyss")]


def tactical(skin):
    """New Tactical skins reuse a Tactical body (handle, guard, etching) with the new finish."""
    body, fin = {"lightning": ("k7", "fin_doppler"), "olympus": ("k3", "fin_case")}[skin]
    old = getattr(T, fin)
    setattr(T, fin, F[skin][0])
    try:
        kn = getattr(T, body)()
    finally:
        setattr(T, fin, old)
    kn.id = "tac_" + skin
    kn.name = "Tactical · " + F[skin][1]
    return finish_knife(kn, skin)


BUILDERS = {kind: build for kind, build, _ in TYPES}
BUILDERS["tactical"] = tactical


def main():
    only = sys.argv[1:]
    meta = json.load(open(T.OUT_JSON))
    have = {k["id"]: k for k in meta["knives"]}
    tactical_old = [k["id"] for k in meta["knives"] if k["id"].startswith("tac_") and k["id"] not in
                    ["tac_" + sk for kd, sk in EXTRA if kd == "tactical"]]
    tall = {"karambit": KARAMBIT_CELL_H}
    kinds = [kind for kind, _, _ in TYPES] + ["tactical"]
    for kind in kinds:
        if only and kind not in only:
            continue
        skins = [sk for kd, b, sks in TYPES if kd == kind for sk in sks] + [sk for kd, sk in EXTRA if kd == kind]
        if kind in tall and os.environ.get("KNIFE_CELL_H") != str(tall[kind]):
            import subprocess
            env = dict(os.environ, KNIFE_CELL_H=str(tall[kind]))
            subprocess.run([sys.executable, os.path.abspath(__file__), kind], env=env, check=True)
            meta = json.load(open(T.OUT_JSON))
            have = {k["id"]: k for k in meta["knives"]}
            continue
        for skin in skins:
            kid = ("tac_" if kind == "tactical" else kind + "_") + skin
            if os.environ.get("NEW_ONLY") and kid in have:
                continue
            kn = BUILDERS[kind](skin)
            print("baking", kn.id, flush=True)
            entry = T.write(kn, min_level=1)
            if T.HALF != 3.55:
                entry["half"] = round(T.HALF, 4)
            have[kn.id] = entry
    order = list(tactical_old)
    for kind, build, skins in TYPES:
        order += [kind + "_" + s for s in skins]
    order += [("tac_" if kd == "tactical" else kd + "_") + sk for kd, sk in EXTRA]
    meta["knives"] = [have[i] for i in order if i in have]
    with open(T.OUT_JSON, "w") as f:
        json.dump(meta, f, indent=1)
    print("wrote", len(meta["knives"]), "knives to", T.OUT_JSON)


if __name__ == "__main__":
    main()
