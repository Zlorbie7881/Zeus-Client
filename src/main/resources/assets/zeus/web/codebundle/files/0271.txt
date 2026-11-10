"""Authored animations for the two hero knives (butterfly and karambit). Every clip is built from tracks: moves with a chosen ease, holds, and damped
settles (the small bounce of a latch snapping home or a wrist catching a blade). Rotations are degrees about the knife's pivot (X first, then Y, then Z),
positions are centimetres. The butterfly's handles are separate parts: 0 degrees is open, +-180 is closed (handleA +, handleB -).
The clips are original; they follow how these knives are really flipped (balisong fans and rollovers, karambit ring spins and tosses)."""
import math
from anim import A


# ------------------------------------------------------------------------------------------------ easing
def e_io(u):
    return u * u * (3 - 2 * u)


def e_io5(u):
    return u * u * u * (u * (6 * u - 15) + 10)


def e_out(u):
    return 1 - (1 - u) ** 3


def e_in(u):
    return u ** 3


def e_out4(u):
    return 1 - (1 - u) ** 4


def e_lin(u):
    return u


EASE = {"io": e_io, "io5": e_io5, "out": e_out, "in": e_in, "out4": e_out4, "lin": e_lin}


class Tr:
    """A value over time, built from consecutive moves."""

    def __init__(self, v0=0.0):
        self.k = [(0.0, float(v0))]

    @property
    def t(self):
        return self.k[-1][0]

    @property
    def v(self):
        return self.k[-1][1]

    def hold(self, t1):
        if t1 > self.t + 1e-6:
            self.k.append((t1, self.v))
        return self

    def to(self, t1, v1, ease="io", n=None):
        t0, v0 = self.k[-1]
        if t1 <= t0 + 1e-6:
            self.k[-1] = (t0, float(v1))
            return self
        f = EASE[ease]
        n = n or max(3, int((t1 - t0) / 0.045))
        for i in range(1, n + 1):
            u = i / n
            self.k.append((t0 + (t1 - t0) * u, v0 + (v1 - v0) * f(u)))
        return self

    def settle(self, v1, freq=9.0, zeta=0.16, dur=0.5):
        """Damped oscillation from the current value to v1 (the bounce after a snap)."""
        t0, v0 = self.k[-1]
        w = 2 * math.pi * freq
        wd = w * math.sqrt(1 - zeta * zeta)
        n = max(6, int(dur * freq * 6))
        for i in range(1, n + 1):
            tau = dur * i / n
            v = v1 + (v0 - v1) * math.exp(-zeta * w * tau) * math.cos(wd * tau)
            self.k.append((t0 + tau, v))
        self.k.append((t0 + dur + 0.001, float(v1)))
        return self

    def wave(self, t1, amp, cycles, centre=None, decay=0.0):
        """A swing about `centre` (default: the current value) that returns to it."""
        t0, v0 = self.k[-1]
        c = v0 if centre is None else centre
        n = max(8, int((t1 - t0) / 0.04))
        for i in range(1, n + 1):
            u = i / n
            self.k.append((t0 + (t1 - t0) * u, c + amp * math.sin(2 * math.pi * cycles * u) * math.exp(-decay * u)))
        return self

    def keys(self, d=None):
        out, last = [], -1.0
        for t, v in self.k:
            if t > last + 1e-4:
                out.append((round(t, 4), round(v, 3)))
                last = t
        if d is not None and out[-1][0] < d:
            out.append((d, out[-1][1]))
        return out


def T(v0=0.0):
    return Tr(v0)


def ks(x):
    return x.keys() if isinstance(x, Tr) else x


def R(d, rx=None, ry=None, rz=None, px=None, py=None, pz=None, pivot=None, parts=None):
    """Builds one clip. parts: {name: {'z': Tr, 'x': Tr, 'y': Tr}} rotations of a separate part."""
    rot = {k: ks(v) for k, v in (("x", rx), ("y", ry), ("z", rz)) if v is not None}
    pos = {k: ks(v) for k, v in (("x", px), ("y", py), ("z", pz)) if v is not None}
    pp = {}
    for name, ch in (parts or {}).items():
        pp[name] = {"rot": {k: ks(v) for k, v in ch.items()}}
    a = A(d, rot=rot or None, pos=pos or None, pivot=pivot, parts=pp or None)
    return a


# ================================================================================================ butterfly
def butterfly(grip_y):
    g = [0, grip_y, 0]
    N = {}
    CL_A, CL_B = 180.0, -180.0     # closed

    def latch(tr, target):        # the lock clicking home: a small damped rattle
        return tr.settle(target, freq=11, zeta=0.18, dur=0.45)

    # ---- draw: the closed knife comes up from the side, the safe handle whips out, the bite handle follows and locks
    d = 1.35
    hB = T(CL_B).hold(0.18).to(0.34, -62, "in").to(0.50, 14, "out").settle(0, freq=8, zeta=0.14, dur=0.5)
    hA = T(CL_A).hold(0.24).to(0.42, 60, "in").to(0.60, -12, "out").settle(0, freq=10, zeta=0.2, dur=0.55)
    rx = T(-70).to(0.40, -18, "out").to(0.62, 12, "io").to(0.9, -3, "io").settle(0, 7, 0.3, 0.4)
    rz = T(-24).to(0.35, 12, "out").to(0.62, -7, "io").settle(0, 7, 0.25, 0.45)
    ry = T(35).to(0.6, -8, "io").to(1.0, 0, "io")
    py = T(-15).to(0.38, 2.4, "out").to(0.62, -0.8, "io").settle(0, 8, 0.3, 0.4)
    px = T(7).to(0.45, -1.2, "out").to(0.9, 0, "io")
    N["draw"] = R(d, rx, ry, rz, px, py, None, g, {"handleA": {"z": hA}, "handleB": {"z": hB}})

    # ---- inspect: a slow showing turn, then a fan (close, reopen) so both handles are seen, ends on a clean lock
    d = 3.9
    ry = T(0).to(0.9, -38, "io").to(1.8, 30, "io").to(2.9, -14, "io").to(3.8, 0, "io")
    rx = T(0).to(0.8, -10, "io").to(2.0, 12, "io").to(3.9, 0, "io")
    rz = T(0).to(1.0, 8, "io").to(2.4, -10, "io").to(3.9, 0, "io")
    py = T(0).to(0.9, 1.2, "io").to(2.1, -0.6, "io").to(3.9, 0, "io")
    hA = T(0).hold(1.25).to(1.55, 95, "io").to(1.82, CL_A + 4, "out").hold(2.05).to(2.28, 80, "in").to(2.46, -9, "out").settle(0, 10, 0.2, 0.55)
    hB = T(0).hold(1.38).to(1.66, -95, "io").to(1.92, CL_B - 4, "out").hold(2.15).to(2.36, -80, "in").to(2.52, 11, "out").settle(0, 9, 0.2, 0.55)
    N["inspect"] = R(d, rx, ry, rz, None, py, None, g, {"handleA": {"z": hA}, "handleB": {"z": hB}})

    # ---- inspect 2: the rollover. The safe handle goes right round the pivot, then the bite handle follows it, the whole knife turns on its axis to show the back
    d = 3.4
    hB = T(0).hold(0.35).to(0.62, -40, "in").to(1.12, -352, "lin").to(1.28, -366, "out").settle(-360, 9, 0.2, 0.5)
    hA = T(0).hold(0.6).to(0.86, 40, "in").to(1.36, 352, "lin").to(1.52, 366, "out").settle(360, 9, 0.2, 0.5)
    ry = T(0).hold(0.9).to(2.5, 360, "io5").settle(360, 7, 0.3, 0.3)
    rx = T(0).to(0.5, -14, "io").to(1.5, 6, "io").to(2.4, -9, "io").to(3.3, 0, "io")
    py = T(0).to(0.7, -0.8, "io").to(1.5, 1.0, "io").to(3.3, 0, "io")
    N["inspect2"] = R(d, rx, ry, None, None, py, None, g, {"handleA": {"z": hA}, "handleB": {"z": hB}})

    # ---- fan: three close/open cycles, each faster, the handles flicking in turn
    d = 3.2
    hA, hB = T(0), T(0)
    t0 = 0.2
    for cyc, dur in enumerate((0.78, 0.62, 0.5)):
        s = t0
        hA.hold(s).to(s + dur * 0.34, CL_A + 3, "out").hold(s + dur * 0.40).to(s + dur * 0.62, 70, "in").to(s + dur * 0.78, -8, "out").to(s + dur * 0.9, 0, "io")
        hB.hold(s + 0.05).to(s + dur * 0.38, CL_B - 3, "out").hold(s + dur * 0.46).to(s + dur * 0.66, -70, "in").to(s + dur * 0.82, 9, "out").to(s + dur * 0.92, 0, "io")
        t0 += dur
    hA.settle(0, 10, 0.2, 0.4)
    hB.settle(0, 10, 0.2, 0.4)
    rz = T(0).wave(2.9, 9, 3, 0).to(3.2, 0, "io")
    rx = T(0).to(0.5, -8, "io").to(2.6, -4, "io").to(3.2, 0, "io")
    N["fan"] = R(d, rx, None, rz, None, None, None, g, {"handleA": {"z": hA}, "handleB": {"z": hB}})

    # ---- zen: the knife twirls on its axis while the handles are carried round (a continuous flourish)
    d = 3.0
    ry = T(0).to(0.35, -12, "io").to(2.2, 720, "io5").settle(720, 8, 0.3, 0.3)
    hA = T(0).hold(0.3).to(0.7, 120, "io").to(1.1, 360, "io").to(1.5, 372, "out").settle(360, 9, 0.2, 0.4)
    hB = T(0).hold(0.45).to(0.9, -110, "io").to(1.4, -350, "io").to(1.75, -366, "out").settle(-360, 9, 0.2, 0.4)
    py = T(0).to(0.6, 1.4, "out").to(1.8, 0.4, "io").to(2.6, 0, "io")
    N["zen"] = R(d, None, ry, None, None, py, None, g, {"handleA": {"z": hA}, "handleB": {"z": hB}})

    # ---- aerial: tossed up end over end, caught with a small recoil
    d = 2.6
    rx = T(0).to(0.12, -10, "io").hold(0.14).to(1.38, -720, "io5").settle(-720, 9, 0.28, 0.45)
    py = T(0).to(0.12, -2.2, "io").to(0.55, 9.5, "out").to(1.0, 8.6, "io").to(1.5, -1.6, "in").settle(0, 9, 0.22, 0.4)
    rz = T(0).to(0.5, 10, "io").to(1.4, -6, "io").to(2.4, 0, "io")
    hA = T(0).wave(0.25, 3, 1, 0).hold(1.4).wave(2.0, 3, 2, 0, 1.2)
    hB = T(0).wave(0.25, -3, 1, 0).hold(1.4).wave(2.0, -3, 2, 0, 1.2)
    N["aerial"] = R(d, rx, None, rz, None, py, None, g, {"handleA": {"z": hA}, "handleB": {"z": hB}})

    # ---- slash: wind up, cut across with the wrist, recover; the latch rattles on the follow-through
    def slash(sign, d=0.9):
        rz = T(0).to(0.17, 34 * sign, "io").to(0.40, -84 * sign, "in").to(0.5, -92 * sign, "out").settle(-88 * sign, 8, 0.3, 0.2).to(d, 0, "io")
        ry = T(0).to(0.17, -10 * sign, "io").to(0.42, 38 * sign, "in").to(d, 0, "io")
        rx = T(0).to(0.17, 8, "io").to(0.42, -12, "in").to(d, 0, "io")
        px = T(0).to(0.17, 4 * sign, "io").to(0.40, -10 * sign, "in").to(d, 0, "io")
        py = T(0).to(0.17, -1.2, "io").to(0.42, 3.4, "in").to(d, 0, "io")
        hA = T(0).hold(0.4).wave(0.9, 3.2, 3, 0, 2.6)
        hB = T(0).hold(0.4).wave(0.9, -3.2, 3, 0, 2.6)
        return R(d, rx, ry, rz, px, py, None, g, {"handleA": {"z": hA}, "handleB": {"z": hB}})
    N["slash"] = slash(1)
    N["slash2"] = slash(-1)
    # ---- stab: pull back, thrust, recover
    rx = T(0).to(0.2, 16, "io").to(0.34, -14, "in").to(0.46, -8, "out").to(0.9, 0, "io")
    py = T(0).to(0.2, -4.4, "io").to(0.34, 9.5, "in").to(0.46, 8.5, "out").to(0.9, 0, "io")
    pz = T(0).to(0.2, 1.8, "io").to(0.34, -3.6, "in").to(0.9, 0, "io")
    ry = T(0).to(0.2, 6, "io").to(0.36, 60, "in").to(0.9, 0, "io")
    hA = T(0).hold(0.34).wave(0.9, 3, 3, 0, 2.6)
    hB = T(0).hold(0.34).wave(0.9, -3, 3, 0, 2.6)
    N["stab"] = R(0.9, rx, ry, None, None, py, pz, g, {"handleA": {"z": hA}, "handleB": {"z": hB}})
    return N


# ================================================================================================ karambit
def karambit():
    ring = [0, 0, 0]
    N = {}

    # ---- draw: swung up from below through one turn about the ring, catching on the finger with a small recoil
    d = 1.3
    rz = T(-8).to(0.12, 6, "io").to(0.68, -374, "out4").settle(-360, 8, 0.24, 0.5)
    rx = T(-34).to(0.5, 8, "out").to(0.8, -3, "io").settle(0, 8, 0.3, 0.4)
    ry = T(24).to(0.7, -6, "io").to(1.2, 0, "io")
    py = T(-14).to(0.4, 1.8, "out").to(0.7, -0.6, "io").settle(0, 8, 0.3, 0.4)
    px = T(6).to(0.6, 0, "io")
    N["draw"] = R(d, rx, ry, rz, px, py, None, ring)

    # ---- inspect: the ring spin; two turns, then it hangs on the finger and is turned to show the edge
    d = 3.5
    rz = T(0).to(0.18, 14, "io").to(0.55, -80, "in").to(1.3, -735, "lin").to(1.75, -722, "out").settle(-720, 7, 0.3, 0.4)
    ry = T(0).hold(1.7).to(2.4, 55, "io").to(3.0, -18, "io").to(3.5, 0, "io")
    rx = T(0).to(0.9, 16, "io").to(1.7, -10, "io").to(2.5, 8, "io").to(3.5, 0, "io")
    py = T(0).to(0.5, 1.8, "out").to(1.4, 0.8, "io").to(1.9, -0.5, "io").settle(0, 8, 0.3, 0.4)
    N["inspect"] = R(d, rx, ry, rz, None, py, None, ring)

    # ---- inspect 2: a back flip: the knife somersaults over the finger (about X), turns on its axis once so every face is seen
    d = 3.0
    rx = T(0).to(0.22, 22, "io").to(0.5, 12, "io").to(1.35, -372, "io5").settle(-360, 8, 0.28, 0.45)
    ry = T(0).hold(0.3).to(1.7, 360, "io5").settle(360, 8, 0.3, 0.3)
    py = T(0).to(0.28, -1.2, "io").to(0.62, 3.6, "out").to(1.0, 2.4, "io").to(1.5, -0.7, "in").settle(0, 9, 0.22, 0.4)
    rz = T(0).to(0.8, 14, "io").to(1.8, -12, "io").to(2.9, 0, "io")
    N["inspect2"] = R(d, rx, ry, rz, None, py, None, ring)

    # ---- ring spin: three turns, quick, as flippers do it
    d = 2.2
    rz = T(0).to(0.14, 12, "io").to(0.42, -90, "in").to(1.45, -1090, "lin").to(1.85, -1088 + 0 * 0, "out").settle(-1080, 8, 0.25, 0.4)
    rx = T(0).to(0.4, 10, "io").to(1.4, -6, "io").to(2.1, 0, "io")
    N["spin"] = R(d, rx, None, rz, None, None, None, ring)

    # ---- pinwheel: forward then reverse, like a propeller, with the blade changing direction at the top
    d = 3.0
    rz = T(0).to(0.12, 10, "io").to(0.35, -60, "in").to(0.9, -366, "out").settle(-360, 8, 0.26, 0.3).hold(1.35).to(1.55, -300, "in").to(2.1, 6, "out").settle(0, 8, 0.26, 0.4)
    rx = T(0).to(0.5, 14, "io").to(1.4, -8, "io").to(2.6, 0, "io")
    ry = T(0).hold(0.4).to(1.1, 40, "io").to(2.0, -40, "io").to(3.0, 0, "io")
    N["pinwheel"] = R(d, rx, ry, rz, None, None, None, ring)

    # ---- toss: thrown straight up with a lazy end-over-end, caught by the ring
    d = 2.8
    rz = T(0).to(0.12, 8, "io").hold(0.14).to(1.5, -540, "io5").settle(-540, 8, 0.25, 0.5)
    py = T(0).to(0.12, -2.0, "io").to(0.62, 10.0, "out").to(1.05, 9.2, "io").to(1.6, -1.4, "in").settle(0, 9, 0.22, 0.45)
    rx = T(0).to(0.6, 14, "io").to(1.5, -12, "io").to(2.7, 0, "io")
    N["toss"] = R(d, rx, None, rz, None, py, None, ring)

    # ---- slash: the hook sweeps across and rolls over at the end (the flip that gives a karambit its cut)
    def slash(sign, d=0.95):
        rz = T(0).to(0.16, 28 * sign, "io").to(0.40, -150 * sign, "in").to(0.54, -176 * sign, "out").settle(-170 * sign, 8, 0.3, 0.25).to(d, 0, "io5")
        ry = T(0).to(0.16, -12 * sign, "io").to(0.42, 70 * sign, "in").to(d, 0, "io")
        rx = T(0).to(0.16, 8, "io").to(0.42, -16, "in").to(d, 0, "io")
        px = T(0).to(0.16, 4 * sign, "io").to(0.40, -9 * sign, "in").to(d, 0, "io")
        py = T(0).to(0.16, -1.2, "io").to(0.42, 3.0, "in").to(d, 0, "io")
        return R(d, rx, ry, rz, px, py, None, ring)
    N["slash"] = slash(1)
    N["slash2"] = slash(-1)
    # ---- stab: pull the ring hand back, drive the hook forward, a quick half-turn of the wrist at the end
    rx = T(0).to(0.2, 18, "io").to(0.34, -16, "in").to(0.46, -8, "out").to(0.92, 0, "io")
    py = T(0).to(0.2, -4.0, "io").to(0.34, 9.0, "in").to(0.46, 8.0, "out").to(0.92, 0, "io")
    pz = T(0).to(0.2, 1.6, "io").to(0.34, -3.4, "in").to(0.92, 0, "io")
    ry = T(0).to(0.2, 10, "io").to(0.36, 180, "in").to(0.92, 360, "io")
    N["stab"] = R(0.92, rx, ry, None, None, py, pz, ring)
    return N
