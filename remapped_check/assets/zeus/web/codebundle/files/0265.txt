"""Keyframe animation for the Zeus knives. A curve is a list of (time, value); it is read with a cardinal spline (the same maths is in
js/knife3d.js and KnifeMesh.java), so motion is smooth with natural overshoot and holds still at the ends.
Rotations are degrees, applied X first, then Y, then Z, about the part's pivot. Positions are centimetres."""
import math


def cardinal(keys, t):
    n = len(keys)
    if n == 0:
        return 0.0
    if t <= keys[0][0]:
        return keys[0][1]
    if t >= keys[-1][0]:
        return keys[-1][1]
    i = 0
    while keys[i + 1][0] < t:
        i += 1
    t0, v0 = keys[i]
    t1, v1 = keys[i + 1]
    def tan(j):
        if j <= 0 or j >= n - 1:
            return 0.0
        return (keys[j + 1][1] - keys[j - 1][1]) / (keys[j + 1][0] - keys[j - 1][0])
    h = t1 - t0
    u = (t - t0) / h
    m0, m1 = tan(i) * h, tan(i + 1) * h
    u2, u3 = u * u, u * u * u
    return (2 * u3 - 3 * u2 + 1) * v0 + (u3 - 2 * u2 + u) * m0 + (-2 * u3 + 3 * u2) * v1 + (u3 - u2) * m1


def chan(x=None, y=None, z=None):
    """Three scalar curves -> one channel of [t, x, y, z] rows (times are the union of the keys)."""
    cs = [c or [(0, 0.0)] for c in (x, y, z)]
    times = sorted({round(t, 4) for c in cs for t, _ in c})
    rows = []
    for t in times:
        rows.append([t] + [round(cardinal(c, t), 2) for c in cs])
    return rows


def A(d, rot=None, pos=None, pivot=None, parts=None):
    """rot / pos: dict with x, y, z curves (lists of (t, v)). parts: {name: {'rot': {...}, 'pos': {...}}}."""
    out = {"d": round(d, 3)}
    root = {}
    if rot:
        root["rot"] = chan(rot.get("x"), rot.get("y"), rot.get("z"))
    if pos:
        root["pos"] = chan(pos.get("x"), pos.get("y"), pos.get("z"))
    if pivot:
        root["pivot"] = list(pivot)
    out["root"] = root
    tr = {}
    for name, ch in (parts or {}).items():
        t = {}
        if "rot" in ch:
            t["rot"] = chan(ch["rot"].get("x"), ch["rot"].get("y"), ch["rot"].get("z"))
        if "pos" in ch:
            t["pos"] = chan(ch["pos"].get("x"), ch["pos"].get("y"), ch["pos"].get("z"))
        tr[name] = t
    out["parts"] = tr
    return out


def stretch(keys, k):
    return [(t * k, v) for t, v in keys]


def scale_anim(a, k):
    """Slow down or speed up a finished animation (k > 1 is slower)."""
    a = dict(a)
    a["d"] = round(a["d"] * k, 3)
    def sc(ch):
        return [[round(r[0] * k, 4)] + r[1:] for r in ch]
    root = {}
    for key, val in a["root"].items():
        root[key] = sc(val) if key in ("rot", "pos") else val
    a["root"] = root
    a["parts"] = {n: {kk: sc(vv) for kk, vv in t.items()} for n, t in a["parts"].items()}
    return a
