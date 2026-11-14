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
def karambit(skin):
    """A crescent: ring, curved handle and hooked claw blade all bend round the same side, with the
    edge on the inside of the curve."""
    kn = T.Knife("karambit_" + skin, "Karambit · " + F[skin][1])
    u_b = 10.8                                          # where the handle meets the blade

    def c(u):                                           # centre line of the handle
        return -0.80 + 1.75 * max(0.0, (u_b - u) / 8.2) ** 1.7

    # handle: (u, spine-side half, edge-side half); a finger guard bump where it meets the blade
    raw = [(2.5, 0.95, 0.95), (3.4, 1.02, 1.00), (6.0, 1.00, 1.02), (9.0, 1.00, 1.02), (10.0, 1.02, 1.30),
           (10.45, 1.04, 1.55), (10.8, 1.02, 1.20)]
    st = T.interp_stations(raw)
    st = T.finger_grooves(st, 3.6, 9.7, 3, 0.30)
    pts = [(u, c(u) - a) for u, a, b in st] + [(u, c(u) + b) for u, a, b in st[::-1]]
    m = poly_mask(pts)
    inside = sdf_inside(m)
    T.rubber(kn, m, hexc("121214"), 1901, rnd=0.55)
    # a raised rim round the grip panel and fine grip grooves across it
    rim = np.clip(1 - np.abs(inside - 0.14) / 0.05, 0, 1) * m
    kn.alb = lerp(kn.alb, hexc("2c2e33"), rim * 0.7)
    for cu in (4.6, 8.4):
        T.screw(kn, cu, c(cu) - 0.05, 0.2, colour="4a4d55", metal=1.0)
    # the ring: steel, wide, with a polished inner lip
    rc_u, rc_v = 1.45, c(1.45) + 0.05
    d = np.hypot(U - rc_u, V - rc_v)
    rm = ((d <= 1.42) & (d >= 0.90)).astype(np.float32)
    mid, half = 1.16, 0.26
    rh = np.sqrt(np.clip(1 - ((d - mid) / half) ** 2, 0, 1)) * half
    steel = T.steel_base(1903, 0.55)
    lip = np.clip(1 - (d - 0.90) / 0.10, 0, 1)
    steel = lerp(steel, np.minimum(1, steel * 1.6 + 0.15), lip * 0.8)
    kn.paint(rm, steel, height=rh, metal=1.0, gloss=0.85, rnd=0.2, side=hexc("3a3e46"))
    kn.cut((d < 0.90).astype(np.float32))
    # the claw: wide at the base, sweeping round to a needle point
    T.blade(kn, u_b, 22.6, -1.85, 0.22, -0.35, clip=0.0, belly=0.0, clip_pow=1.25,
            teeth=(0.04, 0.17, 0.10, 0.22), fuller=None, choil=False, swedge=False, bevel_w=0.70,
            finish=F[skin][0], tang=0.35, bend=3.35, bend_pow=2.15)
    kn.part("ring", 0.0, 2.9, 0.40, 0.30)
    kn.part("handle", 2.9, u_b, 0.95, 0.55)
    kn.part("blade", u_b, 22.8, 0.24, 0.0)
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
    ("karambit", karambit, ["crimson", "sapphire", "fade", "tiger", "slaughter"]),
    ("m9", m9, ["camo", "damascus", "case", "ruby", "night"]),
    ("butterfly", butterfly, ["fade", "marble", "emerald", "slaughter", "ultraviolet"]),
    ("huntsman", huntsman, ["night", "urban", "tiger", "case", "crimson"]),
    ("bowie", bowie, ["damascus", "slaughter", "fade", "sapphire", "rust"]),
    ("skeleton", skeleton, ["case", "fade", "marble", "emerald", "night"]),
    ("ursus", ursus, ["gold", "sapphire", "ruby", "tiger", "urban"]),
    ("stiletto", stiletto, ["fade", "damascus", "case", "night", "marble"]),
]


def main():
    only = sys.argv[1:]
    meta = json.load(open(T.OUT_JSON))
    have = {k["id"]: k for k in meta["knives"]}
    tactical = [k for k in meta["knives"] if k["id"].startswith("tac_")]
    for kind, build, skins in TYPES:
        if only and kind not in only:
            continue
        for skin in skins:
            kn = build(skin)
            print("baking", kn.id, flush=True)
            have[kn.id] = T.write(kn, min_level=1)
    order = [k["id"] for k in tactical]
    for kind, build, skins in TYPES:
        order += [kind + "_" + s for s in skins]
    meta["knives"] = [have[i] for i in order if i in have]
    with open(T.OUT_JSON, "w") as f:
        json.dump(meta, f, indent=1)
    print("wrote", len(meta["knives"]), "knives to", T.OUT_JSON)


if __name__ == "__main__":
    main()
