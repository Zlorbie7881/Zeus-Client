"""Detailed, realistic Butterfly (balisong) and Karambit for Zeus. Same conventions as geom.py: centimetres, the knife stands on +Y, the edge faces +X,
the flat sides face +-Z. Every part is built from closed solids (swept sections, rounded boxes, tori) so back faces can be culled in the game.
lod 1 = the website (thousands of triangles, smooth sections), lod 0 = the game (a few hundred, kept for the silhouette)."""
import math
import numpy as np
from scipy.interpolate import PchipInterpolator
from geom import Mesh, loft, cylinder, rotz, rrect

# --------------------------------------------------------------------------------------------- helpers


def pchip(points):
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    f = PchipInterpolator(xs, ys)
    return lambda t: float(f(min(max(t, xs[0]), xs[-1])))


def rr_asym(wl, wr, ht, r, n=2):
    """Rounded rectangle section, x from -wl to +wr, z from -ht to +ht, as (x, z) points."""
    r = max(0.01, min(r, wl * 0.95, wr * 0.95, ht * 0.95))
    out = []
    for cx, cz, a0 in ((wr - r, ht - r, 0), (-(wl - r), ht - r, 90), (-(wl - r), -(ht - r), 180), (wr - r, -(ht - r), 270)):
        for k in range(n + 1):
            a = math.radians(a0 + 90 * k / n)
            out.append((cx + r * math.cos(a), cz + r * math.sin(a)))
    return out


def path_arc(y0, L, deg, power=1.0, x0=0.0):
    """Centre line that bends by `deg` degrees in total (positive bends towards +X). power > 1 closes the curve towards the tip (a hawkbill)."""
    N = 400
    ss = np.linspace(0, L, N + 1)
    th = math.radians(deg) * (ss / L) ** power
    cx = np.concatenate([[0.0], np.cumsum(np.sin(0.5 * (th[1:] + th[:-1])) * np.diff(ss))])
    cy = np.concatenate([[0.0], np.cumsum(np.cos(0.5 * (th[1:] + th[:-1])) * np.diff(ss))])

    def f(s):
        s = min(max(s, 0.0), L)
        return (x0 + float(np.interp(s, ss, cx)), y0 + float(np.interp(s, ss, cy)), float(np.interp(s, ss, th)))
    return f


def sweep_path(path, L, section_fn, ts, mat, cap=True):
    """Sweeps (n, z) sections along a path. The normal N=(cos th, -sin th) is the 'edge' side."""
    stations, uvs = [], []
    for t in ts:
        cx, cy, th = path(t * L)
        pts, uv = section_fn(t)
        c, s = math.cos(th), math.sin(th)
        stations.append([(cx + c * n, cy - s * n, z) for n, z in pts])
        uvs.append(uv)
    return loft(stations, uvs, mat=mat, cap_start=cap, cap_end=cap)


def rbox(x, z, y0, y1, hx, hz, r, mat, n=2):
    """A rounded bar along Y: centre (x, z), half sizes hx, hz."""
    sec = rrect(hx, hz, r, n)
    return loft([[(x + a, y0, z + b) for a, b in sec], [(x + a, y1, z + b) for a, b in sec]], mat=mat)


def hexpin(cx, cy, cz, r, h, mat="pin", n=6):
    return cylinder((cx, cy, cz), r, h, "z", n, mat)


def disc_y(cx, cy, cz, r, h, mat, n):
    return cylinder((cx, cy, cz), r, h, "y", n, mat)


# --------------------------------------------------------------------------------------------- blades


def blade_section(a, b, tz, g0, hollow, sw, nG, spine_r):
    """One cross-section of a blade: n from -a (spine) to +b (edge), z the half thickness. g0 = where the grind starts (0..1 from the spine),
    hollow = how concave the grind is, sw = depth of the swedge (a false edge bevel on the spine, 0..1)."""
    W = a + b
    half = []
    sp = tz * (0.50 - 0.38 * sw)
    half.append((-a, sp))
    if sw > 0.02:
        half.append((-a + W * 0.08 * sw + 0.02, tz * 0.82))
        half.append((-a + W * 0.16 * sw + 0.05, tz))
    else:
        half.append((-a + spine_r * 0.45, tz * 0.93))
        half.append((-a + spine_r * 1.0, tz))
    gs = -a + W * g0
    for k in range(nG + 1):
        s = k / nG
        x = gs + (b - gs) * s * 0.985
        z = tz * max(0.0, 1 - s) ** hollow
        z = max(z, 0.012)
        half.append((x, z))
    ring = half + [(b, 0.0)] + [(n, -z) for n, z in reversed(half)]
    # unique, in order
    return ring, W


def blade_mesh(path, L, a_fn, b_fn, tz_fn, sw_fn, ts, mat="blade", g0=0.34, hollow=1.5, nG=5, spine_r=0.10, tip_drop=0.0):
    stations, uvs = [], []
    last = len(ts) - 1
    for i, t in enumerate(ts):
        cx, cy, th = path(t * L)
        c, s = math.cos(th), math.sin(th)
        if i == last:
            # the point: on the edge side of the centre line, slightly forward
            a, b = a_fn(t), b_fn(t)
            cx2, cy2, th2 = path(min(L, t * L + tip_drop))
            stations.append([(cx2 + c * 0.0, cy2, 0.0)])
            uvs.append([(1.0, 0.5)])
            continue
        a, b, tz, sw = a_fn(t), b_fn(t), tz_fn(t), sw_fn(t)
        a, b = max(a, 0.03), max(b, 0.03)
        ring, W = blade_section(a, b, tz, g0, hollow, sw, nG, spine_r)
        pts = [(cx + c * n, cy - s * n, z) for n, z in ring]
        stations.append(pts)
        uvs.append([(t, (n + a) / W) for n, z in ring])
    return loft(stations, uvs, mat=mat)


def tsamples(n, tip_bias=1.25):
    return [1 - (1 - i / n) ** tip_bias if False else (i / n) ** (1.0 / tip_bias) if False else i / n for i in range(n + 1)]


def ts_dense(n):
    """Stations along the blade, a little denser towards the point."""
    u = np.linspace(0, 1, n + 1)
    return [float(1 - (1 - x) ** 1.35) for x in u]


# --------------------------------------------------------------------------------------------- butterfly


def butterfly(lod):
    hi = bool(lod)
    L = 11.6
    parts = []
    # ---- blade: profile of a clip-point balisong blade, with a swedge, a hollow grind and a thumb-ridge on the spine
    a_fn = pchip([(0, 1.10), (0.08, 1.10), (0.3, 1.08), (0.55, 1.05), (0.72, 0.94), (0.86, 0.66), (0.95, 0.30), (1.0, 0.0)])
    b_fn = pchip([(0, 1.25), (0.08, 1.35), (0.25, 1.52), (0.5, 1.62), (0.7, 1.52), (0.85, 1.10), (0.95, 0.55), (1.0, 0.0)])
    tz_fn = pchip([(0, 0.24), (0.1, 0.24), (0.5, 0.20), (0.85, 0.14), (1.0, 0.05)])
    sw_fn = pchip([(0, 0.0), (0.4, 0.0), (0.58, 0.55), (0.75, 0.95), (1.0, 1.0)])
    ts = ts_dense(46 if hi else 9)
    path = path_arc(0.0, L, 0.0)
    blade = blade_mesh(path, L, a_fn, b_fn, tz_fn, sw_fn, ts, "blade", g0=0.36, hollow=1.55, nG=5 if hi else 1, spine_r=0.09, tip_drop=0.0)
    # tang: a rounded plate between the handles, with the two pivot holes shown by brass bushings
    tang_sec = [(x, z) for x, z in rrect(1.62, 0.21, 0.19, 2 if hi else 1)]
    tang = loft([[(a, -1.9, b) for a, b in tang_sec], [(a, 1.1, b) for a, b in tang_sec]], mat="blade")
    ms = [blade, tang]
    # ricasso: a thicker, shorter block that carries the maker's line and the thumb jimping
    ms.append(rbox(0.1, 0, 0.9, 2.0, 1.28, 0.26, 0.10, "blade", 2 if hi else 1))
    if hi:
        for i in range(7):
            ms.append(rbox(-0.98, 0, 2.15 + i * 0.22, 2.15 + i * 0.22 + 0.10, 0.12, 0.30, 0.04, "guard", 1))
    # the kicker: a pin on the tang that rests against the handles when the knife is closed
    if hi:
        ms.append(hexpin(0.0, -1.35, 0.0, 0.24, 0.9, "pin", 10))
    parts.append(dict(n="blade", p=[0, 0, 0], par=None, meshes=ms))

    # ---- handles: each is a channel (a back plate with two rails). Skeletonised back plate, standoffs and a latch on the bite handle.
    H = 12.0
    for name, sx, sz, latch in (("handleA", 1, 1, False), ("handleB", -1, -1, True)):
        px, pz = 0.95 * sx, 0.78 * sz
        ms = []
        hw = 1.36          # half width of a handle
        z_in, z_out = 0.08, 1.36
        zc = sz * (z_in + z_out) / 2
        zh = (z_out - z_in) / 2
        st = 2 if hi else 1
        if not hi:
            ms.append(rbox(px, zc, -H, 0.55, hw, zh, 0.30, "handle", 1))
            ms.append(hexpin(px, 0.0, pz, 0.40, 2 * zh + 0.34, "pin", 6))
            parts.append(dict(n=name, p=[px, 0, pz], par=None, meshes=ms))
            continue
        # rails (full length, rounded at the pivot end by a disc)
        for rx in (-1, 1):
            ms.append(rbox(px + rx * (hw - 0.17), zc, -H, 0.55, 0.17, zh, 0.14, "handle", st))
        # back plate: bridges between the rails
        bridges = [(-H, -H + 1.0), (-H + 1.75, -H + 3.75), (-H + 4.5, -H + 6.5), (-H + 7.25, -H + 9.25), (-H + 10.0, 0.62)]
        for y0, y1 in bridges:
            ms.append(rbox(px, sz * (z_out - 0.13), y0, y1, hw - 0.12, 0.13, 0.10, "handle", st))
        # the end cap of the handle (the zipper tip) and the pivot boss
        ms.append(rbox(px, zc, -H - 0.05, -H + 0.6, hw, zh, 0.34, "handle", 3 if hi else 1))
        if hi:
            ms.append(disc_y(px, 0.35, zc, 1.1, 2 * zh, "handle", 28)) if False else None
        # pivot pin with a bushing, and two standoffs that hold the channel together
        ms.append(hexpin(px, 0.0, pz, 0.40, 2 * zh + 0.34, "pin", 16 if hi else 8))
        ms.append(hexpin(px, 0.0, sz * (z_out + 0.07), 0.55, 0.18, "guard", 16 if hi else 8))
        for y in (-4.1, -8.4):
            ms.append(hexpin(px, y, zc, 0.27, 2 * zh, "pin", 12 if hi else 6))
            ms.append(hexpin(px, y, sz * (z_out + 0.05), 0.40, 0.14, "guard", 12 if hi else 6))
        # the edge of the handle is chamfered with a steel inlay line on the back
        if hi:
            ms.append(rbox(px, sz * (z_out + 0.02), -H + 1.0, -0.5, 0.10, 0.05, 0.04, "guard", 1))
        if latch:
            # the latch: a small hook on the safe handle, with its pivot screw
            ms.append(rbox(px - 0.2, sz * (z_in - 0.06), -H + 0.8, -H + 3.4, 0.55, 0.10, 0.07, "guard", 1))
            ms.append(rbox(px + 0.5, sz * (z_in - 0.10), -H + 2.9, -H + 3.6, 0.38, 0.10, 0.06, "guard", 1))
            ms.append(hexpin(px - 0.2, -H + 1.5, sz * (z_in - 0.06), 0.16, 0.40, "pin", 8 if hi else 5))
        parts.append(dict(n=name, p=[px, 0, pz], par=None, meshes=ms))
    return parts, H, L


# --------------------------------------------------------------------------------------------- karambit


def karambit(lod):
    hi = bool(lod)
    parts = []
    # ---- finger ring: an oval, thicker at the bottom, centred on the knife's origin
    def ring_mesh():
        n = 40 if hi else 14
        m = 10 if hi else 4
        Rx, Ry = 1.62, 1.95
        stn = []
        for i in range(n):
            a = 2 * math.pi * i / n
            ca, sa = math.cos(a), math.sin(a)
            tube_r = 0.37 + 0.12 * max(0.0, -sa) ** 1.4          # a little thicker at the bottom of the ring
            tube_z = 0.50 + 0.06 * max(0.0, -sa)
            row = []
            for j in range(m):
                b = 2 * math.pi * j / m
                rr = 1.0 + tube_r * math.cos(b) / ((Rx + Ry) / 2)
                row.append(((Rx + tube_r * math.cos(b)) * ca, (Ry + tube_r * math.cos(b)) * sa, tube_z * math.sin(b)))
            stn.append(row)
        mm = Mesh("handle")
        idx = [[mm.add(p, (i / n * 4, j / m)) for j, p in enumerate(r)] for i, r in enumerate(stn)]
        for i in range(n):
            a_, b_ = idx[i], idx[(i + 1) % n]
            for j in range(m):
                k = (j + 1) % m
                mm.quad(a_[j], a_[k], b_[k], b_[j])
        return mm.orient()
    ms = [ring_mesh()]

    # ---- handle: two contoured G10-style scales over a steel liner, curved gently away from the blade's hook
    HY0, HL, HDEG = 1.85, 8.2, -12.0
    hpath = path_arc(HY0, HL, HDEG)
    n_st = 26 if hi else 5
    ts = [i / n_st for i in range(n_st + 1)]
    ns = 3 if hi else 1

    def width_l(t):
        return 1.18 + 0.30 * math.sin(math.pi * min(1.0, t * 1.05)) ** 0.8

    def width_r(t):
        groove = 0.0
        if hi:
            groove = 0.17 * max(0.0, math.cos(2 * math.pi * (t * 3.3 - 0.15))) ** 2 * math.sin(math.pi * t) ** 0.7
        else:
            groove = 0.12 * math.sin(math.pi * t * 3.3) ** 2 * 0.5
        return 1.08 + 0.34 * math.sin(math.pi * min(1.0, t * 1.05)) ** 0.8 - groove

    def scale_sec(sz):
        def f(t):
            wl, wr = width_l(t) - 0.02, width_r(t) - 0.02
            ht = 0.32 + 0.05 * math.sin(math.pi * t) + (0.022 * math.cos(2 * math.pi * 13 * t) if hi else 0.0)
            pts = rr_asym(wl, wr, ht, 0.30, ns)
            pts = [(x, sz * (0.50 + 0.30) + z) for x, z in pts]
            return pts, [(t, j / len(pts)) for j in range(len(pts))]
        return f
    if hi:
        for sz in (1, -1):
            ms.append(sweep_path(hpath, HL, scale_sec(sz), ts, "handle"))
    else:
        def body(t):
            pts = rr_asym(width_l(t), width_r(t), 0.90, 0.40, 1)
            return pts, [(t, j / len(pts)) for j in range(len(pts))]
        ms.append(sweep_path(hpath, HL, body, ts, "handle"))

    def liner(t):
        wl, wr = width_l(t) + 0.04, width_r(t) + 0.04
        pts = rr_asym(wl, wr, 0.24, 0.12, 1)
        return pts, [(t, j / len(pts)) for j in range(len(pts))]
    if hi:
        ms.append(sweep_path(hpath, HL, liner, ts, "guard"))

    # screws: hex heads on both scales
    def hp(s):
        cx, cy, th = hpath(s)
        return cx, cy, th
    for s in ((HL * 0.20, HL * 0.52, HL * 0.82) if hi else ()):
        cx, cy, th = hp(s)
        ms.append(hexpin(cx - 0.05, cy, 0.0, 0.26, 1.86, "pin", 12 if hi else 6))
        for sz in (1, -1):
            ms.append(hexpin(cx - 0.05, cy, sz * 0.97, 0.36, 0.12, "guard", 12 if hi else 6))
    # butt: where the handle joins the ring, a steel block
    cx, cy, th = hp(0.35)
    if hi:
        ms.append(rbox(cx, 0.0, HY0 - 0.2, HY0 + 0.9, 1.0, 0.58, 0.30, "guard", ns))
    # bolster / guard at the top, where the blade rises out of the handle
    ex, ey, eth = hp(HL)
    c, s_ = math.cos(eth), math.sin(eth)
    bol = rbox(0.0, 0.0, -0.35, 0.35, 1.45, 0.62, 0.25, "guard", ns)
    bol = bol.transform(rotz(-math.degrees(eth)), (ex, ey + 0.15, 0.0))
    ms.append(bol)
    parts.append(dict(n="handle", p=[0, 5.5, 0], par=None, meshes=ms))

    # ---- blade: a hawkbill whose curve tightens towards the point; the edge is on the inside of the hook
    BL = 10.6
    y0 = ey + 0.25
    bpath = path_arc(y0, BL, 134, power=1.25, x0=ex)
    a_fn = pchip([(0, 1.10), (0.10, 1.18), (0.4, 1.20), (0.7, 1.02), (0.88, 0.64), (0.96, 0.26), (1.0, 0.0)])
    b_fn = pchip([(0, 1.00), (0.06, 1.10), (0.12, 1.45), (0.35, 1.95), (0.6, 1.70), (0.8, 1.15), (0.94, 0.50), (1.0, 0.0)])
    tz_fn = pchip([(0, 0.27), (0.1, 0.27), (0.6, 0.20), (0.9, 0.12), (1.0, 0.05)])
    sw_fn = pchip([(0, 0.0), (0.5, 0.0), (0.72, 0.6), (1.0, 1.0)])
    bts = ts_dense(52 if hi else 9)
    blade = blade_mesh(bpath, BL, a_fn, b_fn, tz_fn, sw_fn, bts, "blade", g0=0.36, hollow=1.5, nG=5 if hi else 1, spine_r=0.10)
    bm = [blade]
    # the blade's tang block and a small relief notch at its heel
    bm.append(rbox(ex, 0.0, ey - 0.2, y0 + 0.9, 1.18, 0.28, 0.10, "blade", 2 if hi else 1).transform(rotz(-math.degrees(eth)), (0, 0, 0)) if False else rbox(ex, 0.0, ey - 0.1, y0 + 0.9, 1.15, 0.27, 0.10, "blade", 2 if hi else 1))
    if hi:
        # jimping on the spine just above the handle
        for i in range(6):
            cx2, cy2, th2 = bpath(0.7 + i * 0.28)
            c2, s2 = math.cos(th2), math.sin(th2)
            px_, py_ = cx2 - c2 * 1.05, cy2 + s2 * 1.05
            bm.append(rbox(0, 0, -0.07, 0.07, 0.10, 0.31, 0.04, "guard", 1).transform(rotz(-math.degrees(th2)), (px_, py_, 0)))
    parts.append(dict(n="blade", p=[0, y0, 0], par="handle", meshes=bm))
    return parts, HY0 + HL, y0 + BL * 0.7
