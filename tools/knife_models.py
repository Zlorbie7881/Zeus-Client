#!/usr/bin/env python3
"""Build and validate the extra Zeus knife models.

The hand-made twenty knives live in knives_lo.json.  This script adds the generated
ones (tantō, kris, cleaver, kunai, jambiya, khopesh, machete, dagger) with the same
schema: flat-shaded triangle soups, y along the blade, x across it, z through it,
material names blade / handle / guard / pin.

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
MAX_TRIS = 520


# ---------------------------------------------------------------- cross sections
def sec_blade(w, t, bevel=0.32):
    """Single edged blade: thick spine on -x, fine edge on +x."""
    hw = w * 0.5
    return [
        (hw, 0.0),
        (hw - w * bevel, t * 0.5),
        (-hw, t * 0.5),
        (-hw, -t * 0.5),
        (hw - w * bevel, -t * 0.5),
    ]


def sec_double(w, t):
    """Dagger: sharp on both sides, so a diamond section."""
    hw = w * 0.5
    return [(hw, 0.0), (0.0, t * 0.5), (-hw, 0.0), (0.0, -t * 0.5)]


def sec_slab(w, t):
    """Cleaver / machete: flat stock, but with a shallow central crease so the two big
    faces get different normals and stop fighting when painted back to front."""
    hw = w * 0.5
    return [
        (hw, 0.0),
        (hw * 0.74, t * 0.34),
        (hw * 0.12, t * 0.5),
        (-hw * 0.6, t * 0.5),
        (-hw, t * 0.33),
        (-hw, -t * 0.33),
        (-hw * 0.6, -t * 0.5),
        (hw * 0.12, -t * 0.5),
        (hw * 0.74, -t * 0.34),
    ]


def sec_handle(hw, ht, cham=0.42):
    """Roundish octagonal grip."""
    c = min(hw, ht) * cham
    return [
        (hw, ht - c), (hw - c, ht), (-hw + c, ht), (-hw, ht - c),
        (-hw, -ht + c), (-hw + c, -ht), (hw - c, -ht), (hw, -ht + c),
    ]


# ------------------------------------------------------------------------ lofting
def loft(rings, tip=None, cap_start=True, cap_end=True):
    """Skin a list of (y, [(x, z), ...]) rings.  All rings need the same point count."""
    verts, faces = [], []
    n = len(rings[0][1])
    for y, pts in rings:
        for x, z in pts:
            verts.append([x, y, z])
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
        y = rings[0][0]
        cx = sum(p[0] for p in rings[0][1]) / n
        cz = sum(p[1] for p in rings[0][1]) / n
        c = len(verts)
        verts.append([cx, y, cz])
        for i in range(n):
            faces.append([c, vi(0, i + 1), vi(0, i)])

    last = rows - 1
    if tip is not None:
        t = len(verts)
        verts.append(list(tip))
        for i in range(n):
            faces.append([vi(last, i), vi(last, i + 1), t])
    elif cap_end:
        y = rings[-1][0]
        cx = sum(p[0] for p in rings[-1][1]) / n
        cz = sum(p[1] for p in rings[-1][1]) / n
        c = len(verts)
        verts.append([cx, y, cz])
        for i in range(n):
            faces.append([c, vi(last, i), vi(last, i + 1)])
    return verts, faces


def torus(centre, ring_r, tube_r, seg=12, side=4, axis="y"):
    """Closed ring for a kunai pommel.  Both directions wrap, so it stays manifold."""
    cx, cy, cz = centre
    verts, faces = [], []
    for i in range(seg):
        a = 2 * math.pi * i / seg
        for j in range(side):
            b = 2 * math.pi * j / side
            rr = ring_r + tube_r * math.cos(b)
            if axis == "y":
                verts.append([cx + rr * math.cos(a), cy + tube_r * math.sin(b), cz + rr * math.sin(a)])
            else:
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
    """Return a list of problems with one mesh."""
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
    if not (0.05 < vol / box < 0.99):
        bad.append("fills only %.0f%% of its box, which is not a plausible solid" % (vol / box * 100))
    if len(faces) > MAX_TRIS:
        bad.append("%d triangles, over the %d budget" % (len(faces), MAX_TRIS))
    return bad


# --------------------------------------------------------------- knife profiles
def profile(u, table):
    """Piecewise linear sample of an 11 entry width table."""
    u = max(0.0, min(1.0, u))
    x = u * (len(table) - 1)
    i = min(len(table) - 2, int(x))
    k = x - i
    return table[i] * (1 - k) + table[i + 1] * k


def blade_rings(y0, length, widths, thickness, xoff=None, slices=11, section=sec_blade, round_tip=True):
    """Loft the blade.  The last ring stops short of the point so the tip is a real cone
    instead of a disc lying on top of the end cap (which z-fights when painted)."""
    rings = []
    reach = 0.94 if round_tip else 1.0
    for i in range(slices):
        u = (i / (slices - 1)) * reach
        w = max(0.05, profile(u, widths))
        t = thickness * (1.0 - 0.55 * max(0.0, u - 0.75) / 0.25)
        if len(section(1.0, 1.0)) == 4:
            pts = [(x * w, z * t) for x, z in section(1.0, 1.0)]
        else:
            pts = [(x * w, z * t) for x, z in section(1.0, 1.0)]
        shift = 0.0 if xoff is None else xoff(u)
        rings.append((y0 + length * u, [(x + shift, z) for x, z in pts]))
    return rings


def handle_rings(length, hw, ht, slices=7, swell=1.0):
    """Grip that is thin at the guard, full in the palm and flares at the butt."""
    rings = []
    for i in range(slices):
        u = i / (slices - 1)
        k = 0.84 + 0.22 * math.sin(min(1.0, u * 1.25) * math.pi * 0.5)
        if u > 0.86:
            k *= 1.0 + (swell - 1.0) * ((u - 0.86) / 0.14)
        rings.append((length * u, [(x * k, z * k) for x, z in sec_handle(hw, ht)]))
    return rings


def guard_rings(y, hw, ht, depth):
    pts = sec_handle(hw, ht, 0.28)
    return [(y, pts), (y + depth, pts)]


def build(name, hl, bl, widths, thick, *, section=sec_blade, guard=None,
          pommel=True, tip_shift=0.0, swell=1.10, hw=None, ht=1.15, xoff=None, point=True,
          ring=False):
    """Assemble one knife: grip from 0 up, guard, blade to hl + bl."""
    hw = hw if hw is not None else max(0.85, min(1.9, max(widths) * 0.62))
    widths = [w * 1.12 for w in widths]
    parts = []
    handle = outward(*loft(handle_rings(hl, hw, ht, swell=swell), cap_start=pommel))
    parts.append({"n": "handle", "p": [0.0, round(hl * 0.5, 3), 0.0],
                  "meshes": [geo("handle", handle)]})
    if pommel:
        butt = outward(*loft(handle_rings(0.0, hw * 1.10, ht * 1.10, slices=2)[:1]
                             + [(-0.55, [(x * 1.10, z * 1.10) for x, z in sec_handle(hw, ht)]),
                                (-0.55, [(x * 0.55, z * 0.55) for x, z in sec_handle(hw, ht)])]))
        parts.append({"n": "pommel", "p": [0.0, 0.0, 0.0], "meshes": [geo("guard", butt)]})
    if ring:
        parts.append({"n": "ring", "p": [0.0, 0.0, 0.0],
                      "meshes": [geo("guard", torus((0.0, -1.35, 0.0), 0.86, 0.22))]})
    base = hl
    if guard is not None:
        gw, gd = guard
        guard_geo = outward(*loft(guard_rings(base, hw * gw, ht * 1.22, gd)))
        parts.append({"n": "guard", "p": [0.0, base, 0.0],
                      "meshes": [geo("guard", guard_geo)]})
        base += gd
    rings = blade_rings(base, bl, widths, thick, xoff=xoff, section=section, round_tip=point)
    tip = (tip_shift + (0.0 if xoff is None else xoff(1.0)), base + bl, 0.0) if point else None
    blade = outward(*loft(rings, tip=tip))
    parts.append({"n": "blade", "p": [0.0, round(base, 3), 0.0],
                  "meshes": [geo("blade", blade)]})
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


def wave(x):
    return math.sin(x * math.pi * 3.0)


def build_tanto():
    return build("Tantō", 7.4, 13.6, [1.5, 1.7, 1.8, 1.8, 1.75, 1.7, 1.6, 1.45, 1.3, 1.0, 0.4],
                 0.62, guard=(1.45, 0.5), tip_shift=0.75)


def build_kris():
    return build("Kris", 8.0, 15.2, [1.2, 1.5, 1.2, 1.6, 1.2, 1.7, 1.2, 1.6, 1.1, 1.3, 0.35],
                 0.5, guard=(1.35, 0.42), xoff=lambda u: 0.55 * wave(u), tip_shift=0.2)


def build_cleaver():
    return build("Cleaver", 7.2, 12.8, [2.0, 2.6, 3.0, 3.2, 3.3, 3.3, 3.3, 3.2, 3.2, 3.1, 2.95],
                 0.46, section=sec_slab, hw=1.5, ht=1.05, swell=1.0, tip_shift=0.0, point=False)


def build_kunai():
    return build("Kunai", 6.4, 11.8, [1.35, 1.5, 1.55, 1.5, 1.35, 1.2, 1.0, 0.82, 0.6, 0.36, 0.1],
                 0.58, hw=0.95, ht=0.95, swell=1.22, ring=True)


def build_jambiya():
    return build("Jambiya", 7.0, 12.6, [2.2, 2.5, 2.6, 2.5, 2.3, 2.0, 1.7, 1.3, 0.9, 0.5, 0.15],
                 0.55, guard=(1.4, 0.55), xoff=lambda u: -1.35 * u * u)


def build_khopesh():
    return build("Khopesh", 7.8, 15.4, [1.3, 1.5, 1.6, 1.7, 1.8, 1.9, 2.0, 2.0, 1.7, 1.1, 0.3],
                 0.52, guard=(1.3, 0.45), xoff=lambda u: -2.9 * u * u)


def build_machete():
    return build("Machete", 8.6, 16.5, [2.0, 2.2, 2.3, 2.35, 2.4, 2.45, 2.5, 2.45, 2.2, 1.7, 0.8],
                 0.5, section=sec_slab, hw=1.35, ht=1.1, swell=1.0, xoff=lambda u: 0.55 * u * u)


def build_dagger():
    return build("Dagger", 5.8, 10.8, [1.5, 1.8, 1.9, 1.85, 1.7, 1.5, 1.25, 1.0, 0.7, 0.45, 0.12],
                 0.6, section=sec_double, guard=(1.5, 0.4), hw=1.0, ht=1.0)


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
    knives = [k for k in doc["knives"] if k["name"] not in
              {b()["name"] for b in BUILDERS}]
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
                    print("FAIL %s / %s: %s" % (model["name"], mesh["m"], "; ".join(bad)))
                else:
                    print("ok   %-9s %-6s %4d tris" % (model["name"], mesh["m"], len(faces)))
        if tris > MAX_TRIS:
            problems += 1
            print("FAIL %s: %d triangles in total" % (model["name"], tris))
        entry = {"id": len(knives) + len(built), "name": model["name"], "kind": "straight"}
        entry["grip"] = model["grip"]
        entry["tip"] = model["tip"]
        entry["parts"] = model["parts"]
        entry["anims"] = clips_for(model["hl"])
        built.append(entry)
        print("     %-9s total %d tris, tip %.2f" % (model["name"], tris, model["tip"]))
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
