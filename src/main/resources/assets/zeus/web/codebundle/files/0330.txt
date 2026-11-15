"""Tiny solid-mesh toolkit for the Zeus knives. Every part is a closed solid with outward winding, so the game can cull back faces.
Units are centimetres. A knife stands on +Y (the butt at y=0, the tip at +Y); the edge faces +X; the flat sides face +-Z."""
import math
import numpy as np


class Mesh:
    def __init__(self, mat="blade"):
        self.mat = mat
        self.v = []      # (x,y,z)
        self.uv = []     # (u,v)
        self.f = []      # (a,b,c)

    def add(self, p, uv=(0, 0)):
        self.v.append(tuple(float(c) for c in p))
        self.uv.append((float(uv[0]), float(uv[1])))
        return len(self.v) - 1

    def tri(self, a, b, c):
        if a != b and b != c and a != c:
            self.f.append((a, b, c))

    def quad(self, a, b, c, d):
        self.tri(a, b, c)
        self.tri(a, c, d)

    def merge(self, o):
        base = len(self.v)
        self.v += o.v
        self.uv += o.uv
        self.f += [(a + base, b + base, c + base) for a, b, c in o.f]
        return self

    def volume(self):
        v = np.array(self.v)
        s = 0.0
        for a, b, c in self.f:
            s += np.dot(v[a], np.cross(v[b], v[c]))
        return s / 6.0

    def orient(self):
        """Make the winding outward (positive volume)."""
        if self.volume() < 0:
            self.f = [(a, c, b) for a, b, c in self.f]
        return self

    def moved(self, dx=0, dy=0, dz=0):
        m = Mesh(self.mat)
        m.v = [(x + dx, y + dy, z + dz) for x, y, z in self.v]
        m.uv = list(self.uv)
        m.f = list(self.f)
        return m

    def transform(self, R=None, t=(0, 0, 0)):
        R = np.eye(3) if R is None else np.array(R)
        m = Mesh(self.mat)
        m.v = [tuple(R @ np.array(p) + np.array(t)) for p in self.v]
        m.uv = list(self.uv)
        m.f = list(self.f)
        return m

    def mirrored_z(self):
        m = Mesh(self.mat)
        m.v = [(x, y, -z) for x, y, z in self.v]
        m.uv = list(self.uv)
        m.f = [(a, c, b) for a, b, c in self.f]
        return m


def rotz(deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def rotx(deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def roty(deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def loft(stations, uvs=None, mat="blade", cap_start=True, cap_end=True):
    """stations: list of rings (lists of 3D points, all rings the same length) or a single point (a list of one point).
    Rings are joined with quads. The ring order decides the winding; orient() fixes it afterwards."""
    m = Mesh(mat)
    idx = []
    for i, ring in enumerate(stations):
        row = []
        for j, p in enumerate(ring):
            uv = uvs[i][j] if uvs else (i / max(1, len(stations) - 1), j / max(1, len(ring)))
            row.append(m.add(p, uv))
        idx.append(row)
    for i in range(len(idx) - 1):
        a, b = idx[i], idx[i + 1]
        if len(a) == 1 and len(b) == 1:
            continue
        if len(b) == 1:
            for j in range(len(a)):
                m.tri(a[j], a[(j + 1) % len(a)], b[0])
        elif len(a) == 1:
            for j in range(len(b)):
                m.tri(a[0], b[(j + 1) % len(b)], b[j])
        else:
            n = len(a)
            for j in range(n):
                k = (j + 1) % n
                m.quad(a[j], a[k], b[k], b[j])
    for ring_idx, cap in ((idx[0], cap_start), (idx[-1], cap_end)):
        if cap and len(ring_idx) > 2:
            pts = np.array([m.v[i] for i in ring_idx])
            c = m.add(pts.mean(axis=0), (0.5, 0.5))
            for j in range(len(ring_idx)):
                m.tri(ring_idx[j], ring_idx[(j + 1) % len(ring_idx)], c)
    return m.orient()


def box(cx, cy, cz, sx, sy, sz, mat="guard", taper=1.0):
    """Axis-aligned box centred at (cx, cy, cz). taper scales the +y end in x and z."""
    hx, hy, hz = sx / 2, sy / 2, sz / 2
    lo = [(-hx, -hy, -hz), (hx, -hy, -hz), (hx, -hy, hz), (-hx, -hy, hz)]
    hi = [(-hx * taper, hy, -hz * taper), (hx * taper, hy, -hz * taper), (hx * taper, hy, hz * taper), (-hx * taper, hy, hz * taper)]
    pts = [[(cx + x, cy + y, cz + z) for x, y, z in lo], [(cx + x, cy + y, cz + z) for x, y, z in hi]]
    return loft(pts, mat=mat)


def rrect(w, t, r, n=2):
    """A rounded rectangle section (half width w, half thickness t, corner radius r) as (x, z) points."""
    r = min(r, w * 0.95, t * 0.95)
    pts = []
    corners = [(w - r, t - r, 0), (-(w - r), t - r, 90), (-(w - r), -(t - r), 180), (w - r, -(t - r), 270)]
    for cx, cz, a0 in corners:
        for k in range(n + 1):
            a = math.radians(a0 + 90 * k / n)
            pts.append((cx + r * math.cos(a), cz + r * math.sin(a)))
    return pts


def ellipse(rx, rz, n=8):
    return [(rx * math.cos(2 * math.pi * k / n), rz * math.sin(2 * math.pi * k / n)) for k in range(n)]


def sweep_y(y0, y1, section_fn, steps, mat="handle", x=0.0, z=0.0, cap=True):
    """Sweep a section along +Y. section_fn(t) gives the (x, z) points for t in 0..1."""
    stations, uvs = [], []
    for i in range(steps + 1):
        t = i / steps
        y = y0 + (y1 - y0) * t
        pts = section_fn(t)
        stations.append([(x + px, y, z + pz) for px, pz in pts])
        uvs.append([(t, j / len(pts)) for j in range(len(pts))])
    return loft(stations, uvs, mat=mat, cap_start=cap, cap_end=cap)


def cylinder(c, r, h, axis="z", n=10, mat="pin"):
    cx, cy, cz = c
    ring = [(r * math.cos(2 * math.pi * k / n), r * math.sin(2 * math.pi * k / n)) for k in range(n)]
    if axis == "z":
        s = [[(cx + a, cy + b, cz - h / 2) for a, b in ring], [(cx + a, cy + b, cz + h / 2) for a, b in ring]]
    elif axis == "x":
        s = [[(cx - h / 2, cy + a, cz + b) for a, b in ring], [(cx + h / 2, cy + a, cz + b) for a, b in ring]]
    else:
        s = [[(cx + a, cy - h / 2, cz + b) for a, b in ring], [(cx + a, cy + h / 2, cz + b) for a, b in ring]]
    return loft(s, mat=mat)


def torus(c, R, r, n=14, m=6, mat="handle"):
    """A ring in the XY plane."""
    cx, cy, cz = c
    st = []
    for i in range(n):
        a = 2 * math.pi * i / n
        ca, sa = math.cos(a), math.sin(a)
        ring = []
        for j in range(m):
            b = 2 * math.pi * j / m
            rr = R + r * math.cos(b)
            ring.append((cx + rr * ca, cy + rr * sa, cz + r * math.sin(b)))
        st.append(ring)
    # close the loop: join last to first
    msh = Mesh(mat)
    idx = []
    for i, ring in enumerate(st):
        idx.append([msh.add(p, (i / n, j / m)) for j, p in enumerate(ring)])
    for i in range(n):
        a, b = idx[i], idx[(i + 1) % n]
        for j in range(m):
            k = (j + 1) % m
            msh.quad(a[j], a[k], b[k], b[j])
    return msh.orient()


def sphere(c, r, n=8, mat="guard", sy=1.0):
    cx, cy, cz = c
    st = [[(cx, cy - r * sy, cz)]]
    for i in range(1, n):
        a = math.pi * i / n
        rr = r * math.sin(a)
        st.append([(cx + rr * math.cos(2 * math.pi * k / (n * 2)), cy - r * sy * math.cos(a), cz + rr * math.sin(2 * math.pi * k / (n * 2))) for k in range(n * 2)])
    st.append([(cx, cy + r * sy, cz)])
    return loft(st, mat=mat, cap_start=False, cap_end=False)


def round_to(v, d=2):
    return round(float(v), d)


def export(m):
    """Compact form for JSON: flat vertex list, flat uv list, flat index list."""
    out = {"m": m.mat,
           "v": [round_to(c) for p in m.v for c in p],
           "u": [round_to(c, 3) for p in m.uv for c in p],
           "f": [i for t in m.f for i in t]}
    return out


def arc_sweep(y0, length, deg, section_fn, steps, mat="handle", cap=True):
    """Sweep a section along a gentle arc starting at (0, y0) going up (+Y) and bending towards +X when deg > 0."""
    k = math.radians(deg) / length if length else 0.0
    stations, uvs = [], []
    for i in range(steps + 1):
        t = i / steps
        sarc = t * length
        th = k * sarc
        if abs(k) > 1e-6:
            cx, cy = (1 - math.cos(th)) / k, math.sin(th) / k
        else:
            cx, cy = 0.0, sarc
        N = (math.cos(th), -math.sin(th))
        pts = section_fn(t)
        stations.append([(cx + N[0] * px, y0 + cy + N[1] * px, pz) for px, pz in pts])
        uvs.append([(t, j / len(pts)) for j in range(len(pts))])
    return loft(stations, uvs, mat=mat, cap_start=cap, cap_end=cap)
