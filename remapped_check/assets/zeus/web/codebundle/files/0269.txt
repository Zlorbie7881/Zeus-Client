"""Draw, inspect, slash and stab animations for the 20 knives. The motions are original; they are modelled on how these knife types
are really handled and flipped (balisong fanning, karambit ring spins, folder flicks, toss and catch), not on any game's animation files."""
from anim import A, scale_anim

S = lambda *k: list(k)      # a curve: S((t, v), (t, v), ...)

# ------------------------------------------------------------------------------------------ shared animations
def draw(spin=0.0, roll=-10.0, drop=-13.0, k=1.0, pivot=None, extra_rot=None, parts=None):
    rot = {"x": S((0, -62), (.30, 14), (.52, -4), (.78, 0)),
           "y": S((0, 0), (.12, 0), (.62, spin)) if spin else None,
           "z": S((0, roll), (.4, -roll * .4), (.8, 0))}
    if extra_rot:
        for ax, c in extra_rot.items():
            rot[ax] = c
    pos = {"y": S((0, drop), (.30, 1.6), (.55, -.5), (.8, 0)), "x": S((0, 0), (.4, 0))}
    return scale_anim(A(.9, rot=rot, pos=pos, pivot=pivot, parts=parts), k)


def slash(sign=1, amp=1.0, k=1.0, twist=0.0, pivot=None, parts=None, rollx=0.0):
    a = 80 * amp
    rot = {"z": S((0, 0), (.16, 22 * sign), (.40, -a * sign), (.50, -(a + 6) * sign), (.72, 0)),
           "y": S((0, 0), (.16, -twist * .3), (.42, twist), (.72, 0)) if twist else None,
           "x": S((0, 0), (.16, 6), (.40, -8 + rollx), (.72, 0))}
    pos = {"x": S((0, 0), (.16, 4 * sign), (.40, -9 * sign * amp), (.72, 0)),
           "y": S((0, 0), (.16, -1), (.40, 3), (.72, 0))}
    return scale_anim(A(.72, rot=rot, pos=pos, pivot=pivot, parts=parts), k)


def stab(amp=1.0, k=1.0, twist=0.0, pivot=None, parts=None):
    rot = {"x": S((0, 0), (.20, 14), (.34, -10), (.50, -6), (.76, 0)),
           "y": S((0, 0), (.20, 0), (.36, twist), (.76, 0)) if twist else None}
    pos = {"y": S((0, 0), (.20, -5), (.34, 10 * amp), (.44, 9 * amp), (.76, 0)),
           "z": S((0, 0), (.20, 1.5), (.34, -3), (.76, 0))}
    return scale_anim(A(.76, rot=rot, pos=pos, pivot=pivot, parts=parts), k)


def folder_flick(t0, dur=.45, over=-14):
    """The blade comes out of the handle: 180 degrees to 0, with the overshoot of a pin snapping into its lock."""
    return S((0, 176), (t0, 176), (t0 + dur * .45, 60), (t0 + dur * .75, over), (t0 + dur * .9, 5), (t0 + dur, 0))


def fan_close_open(c0, c1, o0, o1):
    return S((0, 0), (c0, 0), ((c0 + c1) / 2, 95), (c1, 178), (o0, 178), ((o0 + o1) / 2, 40), (o1 - .08, -12), (o1, 0))

# ------------------------------------------------------------------------------------------ per knife
def build(name, grip_y, tip_y, hl):
    import pro_moves
    if name == "Butterfly knife":
        return pro_moves.butterfly(grip_y)
    if name == "Karambit":
        return pro_moves.karambit()
    g = [0, grip_y, 0]
    ring = [0, 0, 0]
    N = {}

    if name == "Butterfly knife":
        N["draw"] = draw(roll=-14, k=1.25, parts={
            "handleA": {"rot": {"z": S((0, 180), (.1, 180), (.28, 35), (.5, -210), (.72, -352), (.88, -364), (1.0, -360))}},
            "handleB": {"rot": {"z": S((0, -180), (.1, -180), (.35, -70), (.58, 22), (.78, 7), (.95, -2), (1.05, 0))}}})
        N["inspect"] = A(3.0, rot={"y": S((0, 0), (.7, 20), (2.2, -10), (3, 0)), "z": S((0, 0), (1.0, -20), (2.4, 10), (3, 0))}, parts={
            "handleA": {"rot": {"z": S((0, 0), (.3, 0), (.7, -180), (1.0, -380), (1.35, -540), (1.7, -720), (2.1, -722), (2.4, -720))}},
            "handleB": {"rot": {"z": S((0, 0), (.9, 0), (1.3, 180), (1.7, 372), (2.05, 540), (2.4, 722), (2.9, 720))}}})
        N["inspect2"] = A(2.6, rot={"z": S((0, 0), (.3, -30), (1.0, -360), (1.7, -720), (2.1, -745), (2.4, -720)),
                                    "x": S((0, 0), (.6, -25), (1.6, 15), (2.6, 0))}, pivot=[0, 0, 0], parts={
            "handleA": {"rot": {"z": S((0, 0), (.5, 70), (1.2, 160), (1.7, 70), (2.2, 5), (2.6, 0))}},
            "handleB": {"rot": {"z": S((0, 0), (.5, -70), (1.2, -160), (1.7, -70), (2.2, -5), (2.6, 0))}}})
        N["slash"] = slash(1, 1.0, .95, 40, parts={"handleA": {"rot": {"z": S((0, 0), (.2, 6), (.42, -10), (.6, 3), (.72, 0))}}})
        N["slash2"] = slash(-1, 1.0, .95, -40)
        N["stab"] = stab(1.0, .95, 90)
    elif name == "Karambit":
        N["draw"] = draw(roll=-8, k=1.2, pivot=ring, extra_rot={"z": S((0, -10), (.2, -60), (.5, -330), (.72, -372), (.9, -360)), "y": S((0, 0), (.9, 0))})
        N["inspect"] = A(2.6, rot={"z": S((0, 0), (.35, -100), (1.0, -480), (1.55, -705), (1.85, -732), (2.1, -720)), "x": S((0, 0), (.8, 18), (1.7, -12), (2.6, 0)),
                                   "y": S((0, 0), (.9, 25), (1.8, -15), (2.6, 0))}, pos={"y": S((0, 0), (.9, 1.5), (2.6, 0))}, pivot=ring)
        N["inspect2"] = A(2.4, rot={"x": S((0, 0), (.4, -30), (.95, 180), (1.5, 175), (2.0, 5), (2.4, 0)), "y": S((0, 0), (.5, 0), (1.2, 140), (1.7, 220), (2.4, 360)),
                                    "z": S((0, 0), (.9, 20), (1.7, -25), (2.4, 0))}, pivot=ring)
        N["slash"] = slash(1, 1.1, .85, 120, pivot=ring)
        N["slash2"] = slash(-1, 1.1, .85, -120, pivot=ring)
        N["stab"] = stab(.9, .85, 360, pivot=ring)
    elif name in ("Flip knife", "Navaja", "Stiletto", "Talon", "Ursus"):
        fast = {"Flip knife": .45, "Navaja": .32, "Stiletto": .2, "Talon": .38, "Ursus": .5}[name]
        N["draw"] = draw(roll=-12, k=1.1, parts={"blade": {"rot": {"z": folder_flick(.25, fast, -16)}}})
        if name == "Flip knife":
            N["inspect"] = A(2.9, rot={"y": S((0, 0), (1.0, 90), (1.6, 60), (2.2, 0), (2.9, 0)), "z": S((0, 0), (1.5, -15), (2.9, 0))},
                             parts={"blade": {"rot": {"z": fan_close_open(.25, .75, 1.1, 1.6)}}})
            N["inspect2"] = A(2.7, rot={"x": S((0, 0), (.4, -20), (1.6, 340), (2.2, 372), (2.5, 360), (2.7, 360))}, pos={"y": S((0, 0), (.9, 2.5), (1.8, 0))},
                              parts={"blade": {"rot": {"z": S((0, 0), (.3, 40), (.7, 130), (1.0, 100), (1.5, 20), (1.9, -8), (2.1, 0))}}})
        elif name == "Navaja":
            N["inspect"] = A(2.6, rot={"x": S((0, 0), (.4, 0), (1.3, 360), (1.55, 372), (1.8, 360)), "z": S((0, 0), (1.0, 12), (2.6, 0))},
                             parts={"blade": {"rot": {"z": S((0, 0), (.15, 40), (.4, 178), (1.45, 178), (1.7, 40), (1.9, -14), (2.05, 3), (2.2, 0))}}})
            N["inspect2"] = A(2.2, rot={"z": S((0, 0), (.3, -20), (1.0, -380), (1.3, -366), (1.5, -360)), "y": S((0, 0), (.8, 40), (2.2, 0))}, pivot=g)
        elif name == "Stiletto":
            N["inspect"] = A(2.4, rot={"x": S((0, 0), (.4, -20), (1.5, 560), (1.8, 545), (2.0, 540)), "y": S((0, 0), (1.0, 25), (2.4, 0))},
                             parts={"blade": {"rot": {"z": S((0, 0), (.2, 178), (.3, 178), (1.5, 178), (1.58, 0), (1.66, -9), (1.75, 0))}}})
            N["inspect2"] = A(2.5, rot={"x": S((0, 0), (.3, -30), (1.1, 700), (1.4, 724), (1.6, 720)), "z": S((0, 0), (1.0, -30), (2.5, 0))},
                              pos={"y": S((0, 0), (.4, 1), (.9, 13), (1.3, 3), (1.6, 0)), "z": S((0, 0), (.9, 4), (1.6, 0))})
        elif name == "Talon":
            N["inspect"] = A(3.0, rot={"y": S((0, 0), (.8, 100), (1.6, 180), (2.4, 90), (3.0, 0)), "z": S((0, 0), (.7, 18), (1.5, -22), (2.3, 12), (3.0, 0)),
                                       "x": S((0, 0), (1.0, -18), (2.0, 14), (3.0, 0))})
            N["inspect2"] = A(2.8, rot={"y": S((0, 0), (.6, 25), (2.0, 360), (2.8, 360)), "z": S((0, 0), (1.2, -12), (2.8, 0))}, pos={"y": S((0, 0), (1.0, 1.0), (2.8, 0))})
        else:
            N["inspect"] = A(2.4, rot={"z": S((0, 0), (.4, -30), (1.1, 180), (1.5, 190), (1.9, 186)), "x": S((0, 0), (1.0, 20), (2.4, 0))}, pos={"y": S((0, 0), (.5, 6), (1.2, 0))})
            N["inspect2"] = A(2.0, rot={"y": S((0, 0), (.2, 0), (1.3, 720), (1.7, 735), (2.0, 720))}, pos={"y": S((0, 0), (.5, 2), (1.5, 0))})
        k = {"Stiletto": .75, "Navaja": .8, "Ursus": 1.15}.get(name, 1.0)
        N["slash"] = slash(1, 1.0, k, 20, parts={"blade": {"rot": {"z": S((0, 0), (.3, 0))}}})
        N["slash2"] = slash(-1, 1.0, k, -20)
        N["stab"] = stab(1.0, k, 0)
    elif name == "Skeleton":
        N["draw"] = draw(roll=-20, k=1.2, spin=720, pivot=g)
        N["inspect"] = A(2.8, rot={"z": S((0, 0), (.5, -180), (1.2, -540), (1.9, -720), (2.2, -735), (2.5, -720)), "y": S((0, 0), (.8, 50), (1.7, -40), (2.8, 0)),
                                   "x": S((0, 0), (1.2, -20), (2.8, 0))}, pivot=g)
        N["inspect2"] = A(2.6, rot={"x": S((0, 0), (.4, 40), (1.0, 180), (1.5, 180), (2.0, 360), (2.6, 360)), "z": S((0, 0), (1.0, 90), (1.7, 270), (2.6, 360))}, pivot=g)
        N["slash"] = slash(1, 1.1, .85, 60, pivot=g)
        N["slash2"] = slash(-1, 1.1, .85, -60, pivot=g)
        N["stab"] = stab(1.0, .85, 180, pivot=g)
    elif name == "Shadow Daggers":
        pa = {"daggerA": {"rot": {"z": S((0, 0), (.4, 0))}}}
        N["draw"] = draw(roll=-6, k=1.15, parts={"daggerA": {"rot": {"z": S((0, 80), (.3, -30), (.55, 18), (.8, 10))}}, "daggerB": {"rot": {"z": S((0, -80), (.3, 30), (.55, -18), (.8, -10))}}})
        N["inspect"] = A(2.6, parts={"daggerA": {"rot": {"z": S((0, 10), (.6, -50), (1.4, 70), (2.1, -20), (2.6, 10)), "y": S((0, 0), (1.0, 0), (2.0, 360))}},
                                     "daggerB": {"rot": {"z": S((0, -10), (.6, 50), (1.4, -70), (2.1, 20), (2.6, -10)), "y": S((0, 0), (1.0, 0), (2.0, -360))}}})
        N["inspect2"] = A(2.4, rot={"y": S((0, 0), (1.2, 180), (2.4, 360))}, parts={"daggerA": {"rot": {"z": S((0, 10), (.8, 100), (1.5, 100), (2.4, 10))}},
                                                                                  "daggerB": {"rot": {"z": S((0, -10), (.8, -100), (1.5, -100), (2.4, -10))}}})
        N["slash"] = slash(1, .9, .8, 30, parts={"daggerB": {"rot": {"z": S((0, 0), (.2, -18), (.42, 24), (.72, 0))}}})
        N["slash2"] = slash(-1, .9, .8, -30, parts={"daggerA": {"rot": {"z": S((0, 0), (.2, 18), (.42, -24), (.72, 0))}}})
        N["stab"] = stab(.85, .8, 0, parts={"daggerA": {"rot": {"z": S((0, 0), (.3, -14), (.6, 0))}}, "daggerB": {"rot": {"z": S((0, 0), (.3, 14), (.6, 0))}}})
    else:
        heavy = {"Falchion": 1.2, "Bowie": 1.28, "Kukri": 1.3}.get(name, 1.0)
        lively = {"Gut knife": .88, "Huntsman": .95}.get(name, 1.0)
        k = heavy * lively
        spin = {"M9 Bayonet": 360, "Classic": 360, "Bayonet": 360, "Survival": 0}.get(name, 0)
        N["draw"] = draw(roll=-10 * heavy, k=1.0 * k, spin=spin, drop=-14 if heavy > 1 else -13)
        if name == "Bayonet":
            N["inspect"] = A(2.2, rot={"x": S((0, 0), (.3, -20), (.7, 200), (1.05, 360), (1.3, 372), (1.55, 355), (1.9, 360))}, pos={"y": S((0, 0), (.35, -2), (.7, 9), (1.0, 5), (1.25, -1.5), (1.45, .6), (1.8, 0))})
            N["inspect2"] = A(2.7, rot={"y": S((0, 0), (.5, -35), (1.2, 150), (1.9, 215), (2.7, 360)), "z": S((0, 0), (.6, 18), (1.4, -8), (2.7, 0))})
        elif name == "M9 Bayonet":
            gz = [0, hl + 1, 0]
            N["inspect"] = A(1.9, rot={"z": S((0, 0), (.3, 30), (.8, -150), (1.2, -340), (1.5, -366), (1.7, -360)), "y": S((0, 0), (.9, 40), (1.7, 0))}, pivot=gz)
            N["inspect2"] = A(2.3, rot={"y": S((0, 0), (.7, 90), (1.5, 90), (2.2, 360)), "x": S((0, 0), (.7, -35), (1.5, -35), (2.2, 0))})
        elif name == "Huntsman":
            N["inspect"] = A(3.2, rot={"y": S((0, 0), (3.2, 360)), "x": S((0, 0), (.6, -18), (2.6, -18), (3.2, 0))})
            N["inspect2"] = A(2.6, rot={"x": S((0, 0), (.6, -80), (1.6, -80), (2.4, 0))}, pos={"z": S((0, 0), (.6, -3), (1.6, -3), (2.4, 0))})
        elif name == "Gut knife":
            N["inspect"] = A(2.5, rot={"x": S((0, 0), (.5, -40), (1.2, -40), (1.7, 0)), "y": S((0, 0), (.5, 50), (1.2, 50), (1.7, 0)), "z": S((0, 0), (1.7, 0), (2.3, 360))})
            N["inspect2"] = A(2.4, rot={"y": S((0, 0), (.8, 180), (1.5, 180), (2.2, 360)), "z": S((0, 0), (.2, 6), (.3, -6), (.4, 5), (.5, 0), (2.4, 0))})
        elif name == "Falchion":
            N["inspect"] = A(2.3, rot={"z": S((0, 0), (.5, -40), (1.0, 40), (1.6, -200), (2.1, -360)), "y": S((0, 0), (.5, 30), (1.0, -30), (1.6, 25), (2.1, 0))}, pos={"x": S((0, 0), (.5, -3), (1.0, 3), (1.6, 0))}, pivot=g)
            N["inspect2"] = A(2.6, rot={"x": S((0, 0), (.7, -70), (1.9, -70), (2.5, 0))}, parts={})
        elif name == "Bowie":
            N["inspect"] = A(2.7, rot={"x": S((0, 0), (.7, -85), (1.8, -85), (2.5, 0))}, pos={"z": S((0, 0), (.7, -5), (1.8, -5), (2.5, 0))})
            N["inspect2"] = A(2.1, rot={"z": S((0, 0), (.6, 35), (1.0, -60), (1.4, -65), (2.0, 0))})
        elif name == "Classic":
            N["inspect"] = A(3.0, rot={"y": S((0, 0), (3.0, 360))})
            N["inspect2"] = A(2.2, rot={"x": S((0, 0), (.6, -20), (1.5, -20), (2.2, 0)), "y": S((0, 0), (.6, 120), (1.5, 120), (2.2, 0))})
        elif name == "Paracord":
            N["inspect"] = A(2.8, rot={"z": S((0, 0), (.8, 35), (2.0, 35), (2.8, 0)), "y": S((0, 0), (.8, 60), (1.9, 160), (2.8, 0))})
            N["inspect2"] = A(2.3, rot={"x": S((0, 0), (1.2, 360)), "z": S((0, 0), (.4, 12), (.9, -12), (1.4, 8), (2.3, 0))})
        elif name == "Survival":
            N["inspect"] = A(2.6, rot={"x": S((0, 0), (1.5, 360), (1.8, 366), (2.0, 360))}, pos={"y": S((0, 0), (.6, 3), (1.3, 0))})
            N["inspect2"] = A(2.2, rot={"y": S((0, 0), (.6, 90), (1.6, 90), (2.2, 0)), "x": S((0, 0), (.6, -30), (1.6, -30), (2.2, 0))})
        elif name == "Nomad":
            N["inspect"] = A(2.4, rot={"z": S((0, 0), (.15, 4), (.3, -4), (.45, 5), (.6, -3), (.75, 3), (.9, 0), (1.3, 0), (1.9, 360)), "x": S((0, 0), (1.3, 0), (1.6, 20), (1.9, 0))})
            N["inspect2"] = A(2.0, rot={"y": S((0, 0), (.7, 180), (1.0, 190), (1.4, 180), (2.0, 360))}, pos={"y": S((0, 0), (.4, 1.5), (.8, 0))})
        elif name == "Kukri":
            N["inspect"] = A(2.6, rot={"z": S((0, 0), (.5, 40), (.9, -70), (1.3, -75), (2.0, -360))}, pivot=g)
            N["inspect2"] = A(2.6, rot={"y": S((0, 0), (.8, 180), (1.9, 180), (2.6, 360)), "x": S((0, 0), (.8, -25), (1.9, -25), (2.6, 0))})
        N.setdefault("inspect", A(2.4, rot={"y": S((0, 0), (1.2, 360))}))
        N.setdefault("inspect2", A(2.4, rot={"y": S((0, 0), (1.2, -360))}))
        sk = k
        N["slash"] = slash(1, 1.0 * (1.1 if heavy > 1 else 1), sk, 20 * heavy)
        N["slash2"] = slash(-1, 1.0 * (1.1 if heavy > 1 else 1), sk, -20 * heavy)
        N["stab"] = stab(1.0, sk, 0)
    for key, a in N.items():
        if a["root"].get("pivot") is None and key != "draw" or (key == "draw" and a["root"].get("pivot") is None):
            a["root"]["pivot"] = g
    return N
