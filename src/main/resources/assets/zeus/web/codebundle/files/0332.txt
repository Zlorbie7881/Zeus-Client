"""The 20 Zeus knives as solid meshes. Shapes follow the blade tables of ui/KnifeArt.java (SMODELS) so the 3D knives keep the look of the
2D ones. Everything here is original geometry; nothing is taken from any game's files."""
import math, re, os
import numpy as np
from scipy.interpolate import PchipInterpolator
from geom import *

HERE = os.path.dirname(os.path.abspath(__file__))
JAVA = os.path.join(HERE, "..", "..", "src", "main", "java", "com", "zlorbie788", "zeus", "ui", "KnifeArt.java")

STYLES = {"H_RIBBED": 0, "H_CORD": 1, "H_SKEL": 2, "H_SCALES": 3, "H_PUSH": 4}


def read_straights():
    src = open(JAVA, encoding="utf-8").read()
    out = []
    for m in re.finditer(r'new Straight\("([^"]+)",\s*([\d.f]+),\s*([\d.f]+),\s*([\d.f]+),\s*w\(([^)]*)\),\s*w\(([^)]*)\),\s*(-?[\d.f]+),\s*(-?[\d.f]+),\s*(H_\w+),\s*"([a-z]*)"\)', src):
        f = lambda s: float(s.replace("f", ""))
        arr = lambda s: [f(x) for x in s.split(",")]
        out.append(dict(name=m.group(1), hl=f(m.group(2)), hhw=f(m.group(3)), bl=f(m.group(4)), ws=arr(m.group(5)), we=arr(m.group(6)),
                        bend=f(m.group(7)), sweep=f(m.group(8)), style=STYLES[m.group(9)], feats=m.group(10)))
    return out


# which straight knives are folders (the blade turns on a pin and tucks between two handle slabs)
FOLDERS = {"Flip knife", "Navaja", "Stiletto", "Talon", "Ursus"}


def blade_mesh(ws, we, bl, bend, sweep, y0, lod, fuller=False, thick=0.30, mat="blade", ricasso=0.10, grind=0.55):
    segs = 14 if lod else 7
    xs = np.linspace(0, 1, len(ws))
    pw0, pe0 = PchipInterpolator(xs, ws), PchipInterpolator(xs, we)
    # a fuller body and a shorter point (drop-point / clip-point feel): the profile is read a little slower, then the last part closes to the tip
    def shaped(f):
        def g(t):
            if t <= 0.84:
                return float(f(t * 0.93))
            base = float(f(0.84 * 0.93))
            return base * max(0.0, 1 - (t - 0.84) / 0.16) ** 0.85
        return g
    pw, pe = shaped(pw0), shaped(pe0)
    k = math.radians(sweep) / bl * (1 if bend > 0 else -1) if sweep else 0.0
    stations, uvs = [], []
    use_f = fuller and lod
    for i in range(segs + 1):
        t = i / segs
        s = t * bl
        th = k * s
        if abs(k) > 1e-6:
            cx, cy = (1 - math.cos(th)) / k, math.sin(th) / k
        else:
            cx, cy = 0.0, s
        N = (math.cos(th), -math.sin(th))
        if i == segs:
            stations.append([(cx, y0 + cy, 0.0)])
            uvs.append([(1.0, 0.5)])
            continue
        a, b = float(pw(t)), float(pe(t))
        a, b = max(a, 0.02), max(b, 0.02)
        tz = thick * (1.0 - 0.62 * t) * (0.55 + 0.45 * min(1.0, (1 - t) * 6))
        W = a + b
        p = lambda u: -a + u * W
        g = 0.97 if t < ricasso else (0.97 + (grind - 0.97) * min(1.0, (t - ricasso) / 0.08))
        half = []
        if use_f:
            fd = 0.5 * math.sin(math.pi * min(1.0, max(0.0, (t - 0.08) / 0.72)))   # how deep the fuller is
            fz = tz * (1 - fd)
            half = [(p(0.0), tz), (p(0.17), tz), (p(0.22), fz), (p(0.40), fz), (p(0.45), tz), (p(g), tz)]
        else:
            half = [(p(0.0), tz), (p(g), tz)]
        ring = half + [(p(1.0), 0.0)] + [(n, -z) for n, z in reversed(half)]
        pts, uv = [], []
        for n, z in ring:
            pts.append((cx + N[0] * n, y0 + cy + N[1] * n, z))
            uv.append((t, (n + a) / W))
        stations.append(pts)
        uvs.append(uv)
    return loft(stations, uvs, mat=mat)


def spine_pos(ws, bl, bend, sweep, y0, t):
    """Point on the spine (for teeth and the like)."""
    xs = np.linspace(0, 1, len(ws))
    a = float(PchipInterpolator(xs, ws)(t))
    k = math.radians(sweep) / bl * (1 if bend > 0 else -1) if sweep else 0.0
    s = t * bl
    th = k * s
    if abs(k) > 1e-6:
        cx, cy = (1 - math.cos(th)) / k, math.sin(th) / k
    else:
        cx, cy = 0.0, s
    return (cx - a * math.cos(th), y0 + cy + a * math.sin(th)), th


def handle_parts(m, lod, hl):
    """Handle meshes (and guard / pommel) for a straight knife. Returns the meshes and the y where the blade starts."""
    hw, ht = m["hhw"], m["hhw"] * 0.78
    st = m["style"]
    feats = m["feats"]
    meshes = []
    y_blade = hl
    if st == 0:     # ribbed
        steps = 14 if lod else 6
        ribs = 5
        def sec(t):
            w = hw * (0.90 + 0.07 * math.cos(2 * math.pi * ribs * t) + 0.10 * t)
            tt = ht * (0.90 + 0.07 * math.cos(2 * math.pi * ribs * t) + 0.08 * t)
            return rrect(w, tt, 0.55, 1)
        meshes.append(sweep_y(0, hl, sec, steps, "handle"))
    elif st == 3:   # scales: a swelling slab handle with pins and a bolster
        steps = 8 if lod else 4
        def sec(t):
            sw = 0.92 + 0.22 * math.sin(math.pi * min(1, t * 1.05)) - 0.05 * t
            return rrect(hw * sw, ht * (0.95 + 0.1 * math.sin(math.pi * t)), 0.5, 1)
        meshes.append(sweep_y(0, hl, sec, steps, "handle"))
        for yy in (hl * 0.22, hl * 0.52, hl * 0.80):
            meshes.append(cylinder((0, yy, 0), 0.26, ht * 2.35, "z", 8 if lod else 6, "pin"))
        meshes.append(box(0, hl - 0.15, 0, hw * 2.15, 0.55, ht * 2.15, "guard"))
    elif st == 1:   # cord wrap
        steps = 20 if lod else 8
        def sec(t):
            w = hw * (1.0 + 0.09 * math.cos(2 * math.pi * 9 * t) + 0.05 * t)
            return ellipse(w, w * 0.88, 8)
        meshes.append(sweep_y(0, hl, sec, steps, "cord"))
        meshes.append(box(0, hl - 0.1, 0, hw * 2.1, 0.5, ht * 2.1, "guard"))
    elif st == 2:   # skeleton frame
        rail = 0.42
        for sx in (-1, 1):
            meshes.append(box(sx * (hw * 0.78), hl / 2, 0, rail, hl, ht * 1.7, "handle"))
        meshes.append(box(0, hl, 0, hw * 2.0, 0.6, ht * 1.7, "guard"))
        for yy in ((0.3, hl * 0.33, hl * 0.66) if lod else (0.3, hl * 0.5)):
            meshes.append(box(0, yy, 0, hw * 1.7, 0.55, ht * 1.5, "handle"))
        for yy in (hl * 0.18, hl * 0.5, hl * 0.82):
            meshes.append(cylinder((0, yy, 0), 0.22, ht * 1.9, "z", 6, "pin"))
    elif st == 4:   # push dagger: a T handle
        meshes.append(box(0, 0.55, 0, 5.0, 1.2, ht * 1.8, "handle", taper=1.0))
        meshes.append(box(0, 2.3, 0, 1.6, 3.0, ht * 1.6, "handle"))
        for sx in (-1.5, 1.5):
            meshes.append(cylinder((sx, 0.55, 0), 0.28, ht * 2.0, "z", 8 if lod else 6, "pin"))
        meshes.append(box(0, 3.9, 0, 2.6, 0.5, ht * 1.9, "guard"))
        return meshes, 4.1
    if "g" in feats:
        meshes.append(box(0, hl + 0.30, 0, hw * 2 + 2.2, 0.60, ht * 2 + 0.55, "guard", taper=0.82))
        y_blade = hl + 0.55
        if "ribbed" and st == 0 and m["name"] in ("Bayonet", "M9 Bayonet"):
            meshes.append(torus((0, hl + 0.30, 0), hw * 1.6, 0.26, 12 if lod else 8, 5, "guard").transform(rotx(90), (0, 0, 0)).moved(0, 0, 0) if False else box(0, hl + 0.9, 0, hw * 2.3, 0.45, ht * 2.2, "guard"))
            y_blade = hl + 1.1
    if "p" in feats:
        meshes.append(sphere((0, -0.1, 0), hw * 1.08, 5 if lod else 4, "guard", 0.7))
    if "l" in feats:
        meshes.append(torus((0, -0.25, 0), 0.5, 0.14, 10 if lod else 8, 4, "accent"))
    if "w" in feats and st != 4:
        meshes.append(box(0, 0.3, 0, hw * 2.5, 0.9, ht * 2.4, "handle"))
    return meshes, y_blade


def straight_knife(m, lod):
    name = m["name"]
    bl = m["bl"]
    WS = 0.80 if name != "Shadow Daggers" else 0.9
    m = dict(m, ws=[x * WS for x in m["ws"]], we=[x * WS for x in m["we"]])
    folder = name in FOLDERS
    if folder:
        bl *= 0.80
    hl = max(m["hl"], bl * 0.92 + 1.2) if folder else m["hl"]
    parts = []
    if folder:
        hw, ht = m["hhw"], m["hhw"] * 0.78
        steps = 8 if lod else 4
        pivot_y = hl - 1.1
        slabs = []
        for sz in (1, -1):
            def sec(t, sz=sz):
                w = hw * (0.95 + 0.15 * math.sin(math.pi * t) - 0.05 * t)
                return [(x, z) for x, z in rrect(w, 0.38, 0.2, 1)]
            slab = sweep_y(0, hl, sec, steps, "handle", z=sz * 0.78)
            slabs.append(slab)
        slabs.append(box(0, hl - 0.45, 0, hw * 2.0, 0.5, 2.0, "guard"))
        slabs.append(box(0, 0.35, 0, hw * 1.9, 0.6, 2.0, "guard"))
        for yy in (pivot_y, hl * 0.45, 0.8):
            slabs.append(cylinder((0, yy, 0), 0.30, 2.25, "z", 8 if lod else 6, "pin"))
        if "l" in m["feats"]:
            slabs.append(torus((0, -0.2, 0), 0.5, 0.14, 10 if lod else 8, 4, "accent"))
        parts.append(dict(n="handle", p=[0, hl * 0.5, 0], par=None, meshes=slabs))
        bm = blade_mesh(m["ws"], m["we"], bl, m["bend"], m["sweep"], pivot_y, lod, "f" in m["feats"] or name == "Stiletto")
        bm.merge(box(0, pivot_y, 0, 1.2, 0.8, 0.55, "blade")) if False else None
        blade_meshes = [bm]
        if "s" in m["feats"]:
            pass
        parts.append(dict(n="blade", p=[0, pivot_y, 0], par="handle", meshes=blade_meshes))
        tip = pivot_y + bl
        return parts, hl, tip
    hm, y_blade = handle_parts(m, lod, hl)
    parts.append(dict(n="handle", p=[0, hl * 0.5, 0], par=None, meshes=hm))
    bms = [blade_mesh(m["ws"], m["we"], bl, m["bend"], m["sweep"], y_blade, lod, "f" in m["feats"], thick=0.34 if m["style"] != 4 else 0.38)]
    if "s" in m["feats"]:
        for i in range(6 if lod else 3):
            t = 0.28 + 0.1 * i
            (px, py), th = spine_pos(m["ws"], bl, m["bend"], m["sweep"], y_blade, t)
            tooth = box(0, 0, 0, 0.42, 0.55, 0.5, "blade", taper=0.3).transform(rotz(-math.degrees(th)), (px - 0.12, py, 0))
            bms.append(tooth)
    if "h" in m["feats"]:
        (px, py), th = spine_pos(m["ws"], bl, m["bend"], m["sweep"], y_blade, 0.55)
        bms.append(box(0, 0, 0, 0.9, 1.1, 0.5, "blade", taper=0.2).transform(rotz(-math.degrees(th) + 18), (px - 0.25, py, 0)))
    parts.append(dict(n="blade", p=[0, y_blade, 0], par="handle", meshes=bms))
    return parts, hl, y_blade + bl


def butterfly(lod):
    hl, hw, ht = 14.0, 1.35, 0.95
    bl = 12.5
    ws = [1.0, 1.15, 1.2, 1.2, 1.2, 1.15, 1.0, 0.8, 0.55, 0.25, 0.0]
    we = [1.2, 1.5, 1.7, 1.75, 1.75, 1.7, 1.55, 1.25, 0.85, 0.4, 0.0]
    steps = 6 if lod else 3
    parts = []
    # blade: pivot at the base, between the two handles, standing on +Y
    bl_mesh = blade_mesh(ws, we, bl, 1, 0, 0.0, lod, True, thick=0.32)
    bl_mesh.merge(box(0, -0.1, 0, 2.2, 1.2, 0.8, "guard"))
    parts.append(dict(n="blade", p=[0, 0, 0], par=None, meshes=[bl_mesh]))
    for name, sx, sz in (("handleA", 1, 1), ("handleB", -1, -1)):
        px, pz = 0.95 * sx, 0.78 * sz
        ms = []
        def sec(t):
            return rrect(hw * (1.0 - 0.1 * t), ht * 0.55, 0.3, 1)
        h = sweep_y(-hl, 0.8, sec, steps + 2, "handle", x=px, z=pz)
        ms.append(h)
        ms.append(cylinder((px, 0.0, pz), 0.34, ht * 1.35, "z", 8 if lod else 6, "pin"))
        ms.append(cylinder((px, -hl * 0.55, pz), 0.28, ht * 1.2, "z", 8 if lod else 6, "pin"))
        ms.append(cylinder((px, -hl + 0.5, pz), 0.28, ht * 1.2, "z", 8 if lod else 6, "pin"))
        parts.append(dict(n=name, p=[px, 0, pz], par=None, meshes=ms))
    return parts, hl, bl


def karambit(lod):
    # a short hooked blade on a short handle with a finger ring at the butt; the ring's centre is the origin
    parts = []
    hl, bl = 9.5, 8.6
    ring_c = (0.0, 0.0, 0.0)
    ws = [1.0, 1.2, 1.3, 1.3, 1.2, 1.0, 0.8, 0.6, 0.4, 0.2, 0.0]
    we = [1.0, 1.3, 1.5, 1.6, 1.55, 1.4, 1.15, 0.85, 0.55, 0.25, 0.0]
    steps = 12 if lod else 6
    def sec(t):
        return rrect(1.15 + 0.1 * math.sin(math.pi * t), 0.85, 0.5, 1)
    ring = torus((0, 0, 0), 2.1, 0.5, 18 if lod else 12, 5, "handle")
    def hsec(t):
        return rrect(1.0 + 0.55 * math.sin(math.pi * min(1.0, t * 1.1)) , 0.8, 0.5, 1)
    hdl = arc_sweep(1.5, hl, -16, hsec, steps, "handle")
    ms = [ring, hdl]
    k = math.radians(-16) / hl
    def hpos(sy):
        th = k * sy
        return ((1 - math.cos(th)) / k, 1.5 + math.sin(th) / k)
    for sy in (hl * 0.3, hl * 0.62):
        px, py = hpos(sy)
        ms.append(cylinder((px, py, 0), 0.26, 2.2, "z", 8 if lod else 6, "pin"))
    ex, ey = hpos(hl)
    ms.append(box(ex, ey + 0.1, 0, 2.8, 0.6, 1.9, "guard").transform(rotz(math.degrees(k * hl) * -1), (0, 0, 0)) if False else box(ex, ey + 0.1, 0, 2.8, 0.6, 1.9, "guard"))
    y0 = ey + 0.4
    blade = blade_mesh(ws, we, bl, 1, 125, y0, lod, False, thick=0.30).moved(ex, 0, 0)
    parts.append(dict(n="handle", p=[0, 5.5, 0], par=None, meshes=ms))
    parts.append(dict(n="blade", p=[0, y0, 0], par="handle", meshes=[blade]))
    return parts, 1.5 + hl, y0 + bl * 0.7


def shadow_daggers(m, lod):
    parts = []
    tip = 0
    for name, sx, rot in (("daggerA", -3.4, 7), ("daggerB", 3.4, -7)):
        ps, hl, tip = straight_knife(m, lod)
        meshes = []
        for p in ps:
            meshes += p["meshes"]
        R = rotz(rot)
        mm = [mesh.transform(R, (sx, 0, 0)) for mesh in meshes]
        parts.append(dict(n=name, p=[sx, 3.0, 0], par=None, meshes=mm))
    return parts, 8.0, tip


def build(lod):
    knives = []
    import pro
    knives.append(dict(id=0, name="Butterfly knife", build=pro.butterfly(lod)))
    knives.append(dict(id=1, name="Karambit", build=pro.karambit(lod)))
    for i, m in enumerate(read_straights()):
        if m["name"] == "Shadow Daggers":
            b = shadow_daggers(m, lod)
        else:
            b = straight_knife(m, lod)
        knives.append(dict(id=2 + i, name=m["name"], build=b, shape=m))
    return knives
