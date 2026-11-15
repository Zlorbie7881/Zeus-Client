#!/usr/bin/env python3
"""Build and validate the extra Zeus knife models.

The hand-made twenty knives live in knives_lo.json.  This script adds the generated
ones with the same schema: flat-shaded triangle soups, y along the blade, x across it,
z through it, materials blade / handle / guard / pin.

Design of every generated knife:
  * the blade is two solids, a body and a separate polished edge bevel, so the cutting
    edge catches a different shade than the flat - that bright line is what makes a
    flat-shaded knife read as ground steel
  * fuller creases down the flat, a ricasso step at the guard, a swedge near the point,
    spine jimping on the knives that have it, and a rounded or squared point
  * a sculpted grip: bevelled oval section, palm swell, butt flare, optional cord wrap
    bands and finger grooves, plus a separate pommel cap
  * a three ring shaped bolster, riveted pins through the grip, and a lanyard hole or
    the kunai's ring on the knives that want one

Every mesh is checked before it is written:
  * closed manifold: every edge is shared by exactly two triangles
  * outward winding: the signed volume must be positive
  * plausible solid: the volume must be a real fraction of the bounding box
  * budget: the whole knife stays under the 520 triangles KnifeMesh can paint

Run:  python3 tools/knife_models.py            (writes)
      python3 tools/knife_models.py --check    (validates only)
"""

import json
import math
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JSON_PATH = os.path.join(ROOT, "src/main/resources/assets/zeus/knives/knives_lo.json")
MAX_TRIS = 820


# ---------------------------------------------------------------- cross sections
def body_section(w, t, edge_w, fuller=0.0, swedge=0.0, belly=0.0, double=False):
    """The flat of the blade: a trapezoid with fuller creases, stopping at the bevel."""
    hw = w * 0.5
    ew = hw - edge_w
    spine = t * (0.5 - swedge) * (1.0 + belly)
    groove = t * (0.5 - swedge) * 0.70 - fuller
    groove = max(groove, t * 0.12)
    if double:
        # Symmetric around the centre line: a centre ridge with a bevel on both sides.
        return [
            (ew, t * 0.42), (0.0, groove), (-ew, t * 0.42),
            (-ew, -t * 0.42), (0.0, -groove), (ew, -t * 0.42),
        ]
    return [
        (ew, t * 0.42),
        (-hw * 0.2, t * 0.5 * (1.0 + belly)),
        (-hw * 0.55, groove),
        (-hw, spine),
        (-hw, -spine),
        (-hw * 0.55, -groove),
        (-hw * 0.2, -t * 0.5 * (1.0 + belly)),
        (ew, -t * 0.42),
    ]


def edge_section(w, t, edge_w, side=1.0):
    """The sharpened bevel: a thin wedge from the flat out to the cutting edge."""
    hw = w * 0.5
    base = hw - edge_w
    return [
        (hw * side, 0.0),
        (base * side, t * 0.42),
        (base * side, -t * 0.42),
    ]


def slab_body(w, t, edge_w, fuller=0.0, swedge=0.0, belly=0.0):
    """Cleaver / machete stock: flat, creased, and squared at the spine."""
    hw = w * 0.5
    ew = hw - edge_w
    face = t * 0.5
    spine = face * 0.72 * (1.0 - min(0.45, swedge))
    return [
        (ew, t * 0.40),
        (-hw * 0.25, face * (0.96 + belly)),
        (-hw * 0.6, face * (0.92 + belly) - fuller),
        (-hw, spine),
        (-hw, -spine),
        (-hw * 0.6, -(face * (0.92 + belly) - fuller)),
        (-hw * 0.25, -(face * (0.96 + belly))),
        (ew, -t * 0.40),
    ]


def sec_handle(hw, ht, cham=0.5, finger=0.0):
    """Bevelled oval grip; ``finger`` pinches the sides into finger grooves."""
    h = 1.0 + finger
    c = min(hw, ht) * cham
    return [
        (hw * h, ht * 0.52), (hw * 0.96, ht * 0.88), (c, ht),
        (-c, ht), (-hw * 0.96, ht * 0.88), (-hw * h, ht * 0.52),
        (-hw * h, -ht * 0.52), (-hw * 0.96, -ht * 0.88), (-c, -ht),
        (c, -ht), (hw * 0.96, -ht * 0.88), (hw * h, -ht * 0.52),
    ]


# ------------------------------------------------------------------------ lofting
def loft(rings, tip=None, cap_start=True, cap_end=True, axis="y"):
    """Skin a list of rings.  ``axis`` names the coordinate the rings march along."""
    verts, faces = [], []
    n = len(rings[0][1])

    def place(p, a, b):
        return [a, p, b] if axis == "y" else [p, a, b]

    for p, pts in rings:
        for a, b in pts:
            verts.append(place(p, a, b))
    rows = len(rings)

    def vi(r, i):
        return r * n + (i % n)

    for r in range(rows - 1):
        for i in range(n):
            a, b = vi(r, i), vi(r, i + 1)
            c, d = vi(r + 1, i + 1), vi(r + 1, i)
            faces.append([a, b, c])
            faces.append([a, c, d])

    if cap_start:
        p0 = rings[0][0]
        ca = sum(q[0] for q in rings[0][1]) / n
        cb = sum(q[1] for q in rings[0][1]) / n
        c = len(verts)
        verts.append(place(p0, ca, cb))
        for i in range(n):
            faces.append([c, vi(0, i + 1), vi(0, i)])

    last = rows - 1
    if tip is not None:
        t = len(verts)
        verts.append(list(tip))
        for i in range(n):
            faces.append([vi(last, i), vi(last, i + 1), t])
    elif cap_end:
        p0 = rings[-1][0]
        ca = sum(q[0] for q in rings[-1][1]) / n
        cb = sum(q[1] for q in rings[-1][1]) / n
        c = len(verts)
        verts.append(place(p0, ca, cb))
        for i in range(n):
            faces.append([c, vi(last, i), vi(last, i + 1)])
    return verts, faces


def cylinder(cx, cy, r, half, sides=6):
    """Rivet / pin: a short closed cylinder through the grip."""
    verts, faces = [], []
    for k, d in enumerate((-half, half)):
        for i in range(sides):
            a = 2 * math.pi * i / sides
            verts.append([cx + r * math.cos(a), cy + r * math.sin(a), d])
    def vi(k, i):
        return k * sides + (i % sides)
    for i in range(sides):
        faces.append([vi(0, i), vi(0, i + 1), vi(1, i + 1)])
        faces.append([vi(0, i), vi(1, i + 1), vi(1, i)])
    c0 = len(verts)
    verts.append([cx, cy, -half])
    for i in range(sides):
        faces.append([c0, vi(0, i + 1), vi(0, i)])
    c1 = len(verts)
    verts.append([cx, cy, half])
    for i in range(sides):
        faces.append([c1, vi(1, i), vi(1, i + 1)])
    return outward(verts, faces)


def torus(centre, ring_r, tube_r, seg=10, side=3):
    """Closed ring for a kunai pommel or a lanyard hole."""
    cx, cy, cz = centre
    verts, faces = [], []
    for i in range(seg):
        a = 2 * math.pi * i / seg
        for j in range(side):
            b = 2 * math.pi * j / side
            rr = ring_r + tube_r * math.cos(b)
            verts.append([cx + rr * math.cos(a), cy + rr * math.sin(a), cz + tube_r * math.sin(b)])
    def vi(i, j):
        return (i % seg) * side + (j % side)
    for i in range(seg):
        for j in range(side):
            a, b = vi(i, j), vi(i + 1, j)
            c, d = vi(i + 1, j + 1), vi(i, j + 1)
            faces.append([a, b, c])
            faces.append([a, c, d])
    return outward(verts, faces)


def volume(verts, faces):
    v = 0.0
    for f in faces:
        a, b, c = verts[f[0]], verts[f[1]], verts[f[2]]
        v += (a[0] * (b[1] * c[2] - b[2] * c[1])
              - a[1] * (b[0] * c[2] - b[2] * c[0])
              + a[2] * (b[0] * c[1] - b[1] * c[0]))
    return v / 6.0


def flip(verts, faces):
    return verts, [[f[0], f[2], f[1]] for f in faces]


def outward(verts, faces):
    return (verts, faces) if volume(verts, faces) > 0 else flip(verts, faces)


def bbox(verts):
    xs = [v[0] for v in verts]
    ys = [v[1] for v in verts]
    zs = [v[2] for v in verts]
    return (min(xs), max(xs)), (min(ys), max(ys)), (min(zs), max(zs))


def check(name, verts, faces):
    bad = []
    edges = {}
    for f in faces:
        if len(f) != 3:
            bad.append("face is not a triangle")
            continue
        for i in range(3):
            a, b = f[i], f[(i + 1) % 3]
            key = (a, b) if a < b else (b, a)
            edges[key] = edges.get(key, 0) + 1
    open_edges = [e for e, c in edges.items() if c != 2]
    if open_edges:
        bad.append("%d open or non-manifold edges" % len(open_edges))
    vol = volume(verts, faces)
    if vol <= 0:
        bad.append("winding is inside out (volume %.4f)" % vol)
    bb = bbox(verts)
    ext = [bb[i][1] - bb[i][0] for i in range(3)]
    box = max(1e-6, ext[0] * ext[1] * ext[2])
    if not (0.005 < vol / box < 0.99):
        bad.append("fills only %.0f%% of its box, which is not a plausible solid" % (vol / box * 100))
    if len(faces) > MAX_TRIS:
        bad.append("%d triangles, over the %d budget" % (len(faces), MAX_TRIS))
    return bad


# --------------------------------------------------------------- knife profiles
def profile(u, table):
    u = max(0.0, min(1.0, u))
    x = u * (len(table) - 1)
    i = min(len(table) - 2, int(x))
    k = x - i
    return table[i] * (1 - k) + table[i + 1] * k


def blade_solids(y0, length, widths, thick, *, xoff=None, slices=12, round_tip=True,
                 edge_w=0.30, fuller=0.0, swedge=0.0, jimp=0.0, ricasso=0.0,
                 belly=0.0, slab=False, double=False, tip_shift=0.0):
    """Build the blade body and its edge bevels as separate closed solids."""
    body_rings, edge_rings, edge_rings_b = [], [], []
    reach = 0.94 if round_tip else 1.0
    for i in range(slices):
        u = (i / (slices - 1)) * reach
        w = max(0.06, profile(u, widths))
        if ricasso and u < 0.07:
            w *= 0.6
        t = thick * (1.0 - 0.5 * max(0.0, u - 0.7) / 0.3)
        f = fuller * (1.0 - abs(u - 0.42) / 0.42) if fuller else 0.0
        f = max(0.0, f)
        sw = swedge * max(0.0, (u - 0.7) / 0.3) if swedge else 0.0
        if jimp and 0.12 < u < 0.62 and i % 2 == 1:
            sw += jimp
        ew = w * edge_w * (1.0 + 0.6 * max(0.0, (u - 0.6) / 0.4))
        if round_tip:
            # Fatten the bevel towards the point, then run it out to nothing so the tip closes cleanly.
            ew *= max(0.0, 1.0 - max(0.0, (u - 0.86) / 0.14))
        else:
            # Squared tip: a small chamfer on the last ring instead of a bevel spike.
            ew *= 1.0 - 0.35 * max(0.0, (u - 0.9) / 0.1)
        xo = 0.0 if xoff is None else xoff(u)
        y = y0 + length * u
        if slab:
            pts = slab_body(w, t, ew, fuller=f, swedge=sw, belly=belly)
        else:
            pts = body_section(w, t, ew, fuller=f, swedge=sw, belly=belly, double=double)
        body_rings.append((y, [(x + xo, z) for x, z in pts]))
        edge_rings.append((y, [(x + xo, z) for x, z in edge_section(w, t, ew)]))
        if double:
            edge_rings_b.append((y, [(-x + xo, z) for x, z in edge_section(w, t, ew)]))

    tip_x = tip_shift + (0.0 if xoff is None else xoff(1.0))
    tip_y = y0 + length
    # Sink the body a hair so its end cap never sits on the bevel's cap on a squared tip.
    lift = -0.05 if not round_tip else 0.0
    body = outward(*loft([(y + lift, pts) for y, pts in body_rings],
                         tip=(tip_x, tip_y, 0.0) if round_tip else None,
                         cap_end=not round_tip))
    solids = [("blade", body)]
    tip_e = (tip_x, tip_y, 0.0) if round_tip else None
    solids.append(("edge", outward(*loft(edge_rings, tip=tip_e, cap_end=not round_tip))))
    if double:
        solids.append(("edge", outward(*loft(edge_rings_b, tip=tip_e, cap_end=not round_tip))))
    return solids


def grip_section(hw, ht, style="oval", finger=0.0):
    """One grip cross section.  Hex reads as a slab-sided handle, round as a turned one."""
    if style == "hex":
        return [(hw, ht * 0.26), (hw * 0.66, ht * 0.88), (-hw * 0.66, ht * 0.88),
                (-hw, ht * 0.26), (-hw, -ht * 0.26), (-hw * 0.66, -ht * 0.88),
                (hw * 0.66, -ht * 0.88), (hw, -ht * 0.26)]
    if style == "round":
        return [(hw * math.cos(2 * math.pi * i / 8), ht * math.sin(2 * math.pi * i / 8))
                for i in range(8)]
    return sec_handle(hw, ht, finger=finger)


def handle_rings(length, hw, ht, slices=8, swell=0.14, wrap=0, grooves=0,
                 style="oval", taper=0.88, swell_at=1.25, butt_flare=0.16):
    """Grip that is thin at the guard, full in the palm and flares at the butt."""
    rings = []
    for i in range(slices):
        u = i / (slices - 1)
        k = taper + swell * math.sin(min(1.0, u * swell_at) * math.pi * 0.5)
        # a coke bottle waist just above the guard, then the butt flare
        k *= 1.0 - 0.07 * math.sin(min(1.0, u * 2.2) * math.pi * 0.5) * (1.0 - u * 0.55)
        if u > 0.84:
            k *= 1.0 + butt_flare * ((u - 0.84) / 0.16)
        if wrap:
            k *= 1.0 + 0.05 * math.sin(u * math.pi * wrap * 2.0)
        finger = grooves * math.sin(u * math.pi * grooves) * 0.10 if grooves else 0.0
        rings.append((length * u, [(x * k, z * k) for x, z in grip_section(hw, ht, style, finger)]))
    return rings


def cross_guard(cy, half_len, depth, th):
    """A bar across the blade: the classic double edged dagger guard, lofted along x."""
    rings = []
    for x, s in ((-half_len, 0.55), (0.0, 1.0), (half_len, 0.55)):
        d, t = depth * s, th * s
        rings.append((x, [(cy - d, -t), (cy - d, t), (cy + d, t), (cy + d, -t)]))
    return outward(*loft(rings, axis="x"))


def bolster_rings(y, hw, ht, depth, flare=1.35):
    """Four ring bolster swept towards the blade: a shaped guard rather than a slab."""
    a = sec_handle(hw * 0.90, ht * 0.98, 0.42)
    b = sec_handle(hw * flare * 0.94, ht * 1.26, 0.3)
    c = sec_handle(hw * flare, ht * 1.16, 0.28)
    d = sec_handle(hw * 0.74, ht * 0.86, 0.42)
    return [(y, a), (y + depth * 0.32, b), (y + depth * 0.72, c), (y + depth, d)]


def build(name, hl, bl, widths, thick, *, guard=None, tip_shift=0.0, hw=None, ht=1.15,
          xoff=None, point=True, fuller=0.0, swedge=0.0, jimp=0.0, ricasso=0.0,
          wrap=0, grooves=0, pins=2, ring=False, lanyard=False, butt=True,
          width_scale=1.14, edge_w=0.30, slab=False, double=False, belly=0.0, slices=12,
          grip="oval", taper=0.88, swell=0.14, swell_at=1.25, butt_flare=0.16,
          guard_style="winged", pommel="cap"):
    """Assemble one knife: grip from 0 up, bolster, blade to hl + bl."""
    hw = hw if hw is not None else max(0.85, min(1.9, max(widths) * 0.62))
    widths = [w * width_scale for w in widths]
    parts = []

    handle = outward(*loft(handle_rings(hl, hw, ht, swell=swell, wrap=wrap, grooves=grooves,
                                        style=grip, taper=taper, swell_at=swell_at,
                                        butt_flare=butt_flare),
                           cap_start=butt))
    parts.append({"n": "handle", "p": [0.0, round(hl * 0.5, 3), 0.0],
                  "meshes": [geo("handle", handle)]})

    if butt and pommel != "none":
        gs = lambda f: [(x * f, z * f) for x, z in grip_section(hw * 1.02, ht * 1.02, grip)]
        if pommel == "ball":
            rings = [(0.0, gs(1.05)), (-0.28, gs(1.20)), (-0.55, gs(0.4))]
        elif pommel == "flat":
            rings = [(0.0, gs(1.0)), (-0.34, gs(0.96))]
        else:
            rings = [(0.0, gs(1.05)), (-0.3, gs(1.1)), (-0.5, gs(0.84))]
        parts.append({"n": "pommel", "p": [0.0, 0.0, 0.0],
                      "meshes": [geo("brass", outward(*loft(rings)))]})

    if pins:
        meshes = []
        for i in range(pins):
            u = 0.26 + 0.5 * (i / max(1, pins - 1)) if pins > 1 else 0.5
            meshes.append(geo("brass", cylinder(0.0, hl * u, 0.22, ht * 1.02, sides=6)))
        parts.append({"n": "pins", "p": [0.0, 0.0, 0.0], "meshes": meshes})

    base = hl
    if guard is not None:
        gw, gd = guard
        if guard_style == "cross":
            gpair = cross_guard(base + gd * 0.5, hw * gw * 1.55, gd * 0.55, ht * 1.06)
        elif guard_style == "block":
            a = [(x, z) for x, z in grip_section(hw * gw, ht * 1.1, "oval")]
            b = [(x * 1.14, z * 1.2) for x, z in grip_section(hw * gw, ht * 1.1, "oval")]
            gpair = outward(*loft([(base, a), (base + gd, b)]))
        elif guard_style == "collar":
            a = [(x * 0.82, z * 1.0) for x, z in grip_section(hw * gw, ht, "oval")]
            b = [(x * 1.02, z * 1.06) for x, z in grip_section(hw * gw, ht, "oval")]
            gpair = outward(*loft([(base, a), (base + gd * 0.8, b)]))
        else:
            gpair = outward(*loft(bolster_rings(base, hw * gw, ht, gd)))
        parts.append({"n": "guard", "p": [0.0, base, 0.0], "meshes": [geo("brass", gpair)]})
        base += gd

    solids = blade_solids(base, bl, widths, thick, xoff=xoff, round_tip=point, slices=slices,
                          edge_w=edge_w, fuller=fuller, swedge=swedge, jimp=jimp,
                          ricasso=ricasso, belly=belly, slab=slab, double=double,
                          tip_shift=tip_shift)
    parts.append({"n": "blade", "p": [0.0, round(base, 3), 0.0],
                  "meshes": [geo(mat, geo_pair) for mat, geo_pair in solids]})

    if ring:
        parts.append({"n": "ring", "p": [0.0, 0.0, 0.0],
                      "meshes": [geo("brass", torus((0.0, -0.5, 0.0), 0.62, 0.17, seg=10, side=3))]})
    if lanyard:
        parts.append({"n": "lanyard", "p": [0.0, 0.0, 0.0],
                      "meshes": [geo("brass", torus((0.0, -0.72, 0.0), 0.3, 0.1, seg=6, side=3))]})

    return {"name": name, "hl": hl, "bl": bl, "tip": round(base + bl, 3),
            "grip": [0.0, round(hl * 0.5, 3), 0.0], "parts": parts}


def geo(mat, pair):
    verts, faces = pair
    flat = []
    for v in verts:
        flat.extend([round(c, 3) for c in v])
    idx = []
    for f in faces:
        idx.extend(f)
    return {"m": mat, "v": flat, "f": idx}


def build_tanto():
    # Flat ground tantō: jimping, a step at the ricasso, an angled point.
    return build("Tantō", 7.4, 13.6, [1.5, 1.7, 1.8, 1.8, 1.75, 1.7, 1.6, 1.45, 1.3, 1.0, 0.35],
                 0.62, guard=(1.35, 0.5), tip_shift=0.7, jimp=0.14, ricasso=1.0,
                 swedge=0.18, pins=2, grooves=2, edge_w=0.16, taper=0.92,
                 guard_style="winged", pommel="cap")


def build_kris():
    # Serpentine blade with a full-length fuller and a waved edge.
    return build("Kris", 8.0, 15.2, [1.2, 1.5, 1.2, 1.6, 1.2, 1.7, 1.2, 1.6, 1.1, 1.3, 0.32],
                 0.52, guard=(1.25, 0.44), hw=1.05, xoff=lambda u: 0.6 * math.sin(u * math.pi * 3.0),
                 fuller=0.1, swedge=0.14, pins=2, wrap=3, edge_w=0.18, grip="round",
                 taper=0.84, swell=0.2, butt_flare=0.1, guard_style="collar", pommel="ball")


def build_cleaver():
    # Wide flat stock, squared off, stout bolster, three rivets.
    return build("Cleaver", 7.2, 12.8, [2.0, 2.6, 3.0, 3.2, 3.3, 3.3, 3.3, 3.2, 3.2, 3.05, 2.95],
                 0.48, guard=(1.35, 0.42), hw=1.5, ht=1.08, point=False, belly=0.1,
                 slab=True, pins=3, edge_w=0.14, grip="hex", taper=0.95, swell=0.1,
                 butt_flare=0.05, grooves=2, guard_style="block", pommel="flat")


def build_kunai():
    # Leaf blade, no guard, cord wrap and a pommel ring.
    return build("Kunai", 6.4, 11.8, [1.35, 1.5, 1.55, 1.5, 1.35, 1.2, 1.0, 0.82, 0.6, 0.36, 0.1],
                 0.6, hw=0.95, ht=0.95, fuller=0.08, pins=1, ring=True, wrap=4, grooves=1,
                 edge_w=0.24, grip="round", taper=0.8, swell=0.08, pommel="none")


def build_jambiya():
    # Wide sweeping blade, deep fuller, long bolster, lanyard hole.
    return build("Jambiya", 7.0, 12.6, [2.2, 2.5, 2.6, 2.5, 2.3, 2.0, 1.7, 1.3, 0.9, 0.5, 0.15],
                 0.56, guard=(1.45, 0.6), xoff=lambda u: -1.4 * u * u, fuller=0.12,
                 swedge=0.1, pins=1, lanyard=True, edge_w=0.2, slices=7, hw=1.2,
                 taper=0.9, swell=0.22, guard_style="winged", pommel="ball")


def build_khopesh():
    # Hooked blade: strong sweep, thinned spine, wrapped grip.
    return build("Khopesh", 7.8, 15.4, [1.3, 1.5, 1.6, 1.7, 1.8, 1.9, 2.0, 2.0, 1.7, 1.1, 0.28],
                 0.54, guard=(1.2, 0.4), xoff=lambda u: -2.95 * u * u, swedge=0.16,
                 fuller=0.08, pins=2, wrap=3, edge_w=0.18, grip="round", taper=0.86,
                 swell=0.18, guard_style="winged", pommel="cap")


def build_machete():
    # Long slab blade with a fuller, no guard, wrapped grip and a lanyard hole.
    return build("Machete", 8.6, 16.5, [2.0, 2.2, 2.3, 2.35, 2.4, 2.45, 2.5, 2.45, 2.2, 1.7, 0.75],
                 0.52, hw=1.35, ht=1.1, xoff=lambda u: 0.55 * u * u, fuller=0.07, belly=0.08,
                 slab=True, pins=2, wrap=3, lanyard=True, edge_w=0.16, grip="hex",
                 taper=0.9, swell=0.12, guard_style="collar", pommel="flat")


def build_dagger():
    # Double edged diamond section with fullers and a crossguard.
    return build("Dagger", 5.8, 10.8, [1.5, 1.8, 1.9, 1.85, 1.7, 1.5, 1.25, 1.0, 0.7, 0.45, 0.12],
                 0.62, guard=(1.5, 0.42), hw=1.0, ht=1.0, fuller=0.07, double=True,
                 pins=1, edge_w=0.2, taper=0.84, swell=0.16, guard_style="cross",
                 pommel="ball")


BUILDERS = [build_tanto, build_kris, build_cleaver, build_kunai,
            build_jambiya, build_khopesh, build_machete, build_dagger]


# ---------------------------------------------------------------------- clips
def clip(duration, rot_rows, pos_rows=((0.0, 0.0, 0.0, 0.0),), pivot=(0.0, 0.0, 0.0)):
    return {"d": duration,
            "root": {"pivot": [round(c, 3) for c in pivot],
                     "rot": [list(r) for r in rot_rows],
                     "pos": [list(r) for r in pos_rows]},
            "parts": {}}


def clips_for(hl):
    mid = round(hl * 0.5, 3)
    pivot = (0.0, mid, 0.0)
    draw = clip(1.3, [(0.0, -92.0, 0.0, 24.0), (0.35, -40.0, 0.0, 8.0),
                      (0.72, -6.0, 0.0, -2.0), (1.0, 0.0, 0.0, 0.0)],
                [(0.0, 0.0, -9.0, 0.0), (0.55, 0.0, 0.6, 0.0), (1.0, 0.0, 0.0, 0.0)], pivot)
    inspect = clip(3.0, [(0.0, 0.0, 0.0, 0.0), (0.9, -14.0, 0.0, 12.0),
                         (1.8, 12.0, 0.0, -10.0), (2.4, 0.0, 0.0, 26.0),
                         (3.0, 0.0, 0.0, 0.0)], pivot=pivot)
    spin = clip(2.2, [(0.0, 0.0, 0.0, 0.0), (0.25, 0.0, -40.0, 0.0),
                      (0.8, 0.0, 320.0, 0.0), (1.0, 0.0, 360.0, 0.0)], pivot=pivot)
    fan = clip(2.0, [(0.0, 0.0, 0.0, 0.0), (0.3, 0.0, 0.0, 46.0),
                     (0.55, 0.0, 0.0, -34.0), (0.8, 0.0, 0.0, 22.0),
                     (1.0, 0.0, 0.0, 0.0)], pivot=pivot)
    aerial = clip(2.6, [(0.0, 0.0, 0.0, 0.0), (0.35, -210.0, 0.0, 0.0),
                        (0.7, -390.0, 0.0, 0.0), (1.0, -360.0, 0.0, 0.0)],
                  [(0.0, 0.0, 0.0, 0.0), (0.35, 0.0, 7.5, 0.0),
                   (0.7, 0.0, 1.2, 0.0), (1.0, 0.0, 0.0, 0.0)], pivot)
    zen = clip(3.0, [(0.0, 0.0, 0.0, 0.0), (1.2, 8.0, 0.0, 8.0),
                     (2.2, -6.0, 0.0, -6.0), (3.0, 0.0, 0.0, 0.0)], pivot=pivot)
    return {"draw": draw, "inspect": inspect, "inspect2": inspect,
            "spin": spin, "fan": fan, "aerial": aerial, "zen": zen}


def main():
    check_only = "--check" in sys.argv
    with open(JSON_PATH, encoding="utf-8") as fh:
        doc = json.load(fh)
    names = {b()["name"] for b in BUILDERS}
    knives = [k for k in doc["knives"] if k["name"] not in names]
    built = []
    problems = 0
    for make in BUILDERS:
        model = make()
        tris = 0
        for part in model["parts"]:
            for mesh in part["meshes"]:
                verts = [mesh["v"][i:i + 3] for i in range(0, len(mesh["v"]), 3)]
                faces = [mesh["f"][i:i + 3] for i in range(0, len(mesh["f"]), 3)]
                bad = check(model["name"], verts, faces)
                tris += len(faces)
                if bad:
                    problems += 1
                    print("FAIL %s / %s: %s" % (model["name"], part["n"], "; ".join(bad)))
        if tris > MAX_TRIS:
            problems += 1
            print("FAIL %s: %d triangles in total" % (model["name"], tris))
        entry = {"id": len(knives) + len(built), "name": model["name"], "kind": "straight"}
        entry["grip"] = model["grip"]
        entry["tip"] = model["tip"]
        entry["parts"] = model["parts"]
        entry["anims"] = clips_for(model["hl"])
        built.append(entry)
        print("ok   %-9s %3d tris  %d parts  tip %.2f" % (model["name"], tris, len(model["parts"]), model["tip"]))
    if problems:
        print("\n%d problem(s); nothing written" % problems)
        return 1
    if check_only:
        print("\nall models valid")
        return 0
    doc["knives"] = knives + built
    with open(JSON_PATH, "w", encoding="utf-8") as fh:
        json.dump(doc, fh, separators=(",", ":"))
    print("\nwrote %d knives (%d generated)" % (len(doc["knives"]), len(built)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
