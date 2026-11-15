#!/usr/bin/env python3
"""
The six Chroma knives, traced 1:1 from ref/chroma_six.png: the outline and every pixel of colour come from the
picture itself (cut out with GrabCut, holes placed by hand), laid onto the HD knife grid with Lanczos sampling,
then given the same 3D treatment as the other HD knives (rounded handles, bevelled blades, part thicknesses).

  python3 tools/knives_hd/make_chroma.py            bake all six, then cut the butterflies into pieces (make_pieces.py)
"""
import json
import os
import subprocess
import sys

import cv2
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
REF = os.path.join(HERE, "ref", "chroma_six.png")

# picture boxes (x0, y0, x1, y1), bottom row of the knife, and how tall the sheet must be for its width
KNIVES = {
    "chroma_karambit": dict(box=(58, 133, 216, 496), name="Karambit · Galaxy", glow="#b85cff", cell=448,
                            guard=257, ring=425, holes=[("ellipse", 120, 449, 20, 21)], spine_right=False),
    "chroma_m9": dict(box=(238, 68, 357, 498), name="M9 Bayonet · Molten Core", glow="#ff6a1c", cell=288,
                      guard=318, guard_end=327, holes=[("rect", 293, 145, 302, 162)], spine_right=True),
    "chroma_crystal": dict(box=(393, 48, 494, 496), name="Bowie · Prism Shard", glow="#8fe3ff", cell=256,
                           guard=300, holes=[], spine_right=True),
    "chroma_phoenix": dict(box=(548, 48, 650, 498), name="Bowie · Gilded Phoenix", glow="#ffc24d", cell=256,
                           guard=312, guard_end=322, pommel=470, holes=[], spine_right=True),
    "chroma_butterfly": dict(box=(698, 60, 792, 497), name="Butterfly · Stardust", glow="#e8d49a", cell=256,
                             guard=276, holes=[], spine_right=True, pins=((737.5, 277.0), (762.5, 277.0))),
    "chroma_kunai": dict(box=(858, 96, 932, 497), name="Kunai · Spectrum", glow="#7cf7d4", cell=256,
                         guard=312, guard_end=345, holes=[("ellipse", 896.5, 327.5, 15, 15), ("ellipse", 896.5, 246.5, 2.5, 2.5),
                                                          ("ellipse", 896.5, 261, 3, 3), ("ellipse", 896.5, 276.5, 3.5, 3.5),
                                                          ("ellipse", 896.5, 295, 4.5, 4.5), ("ellipse", 896.5, 355, 3, 3),
                                                          ("ellipse", 896.5, 462.5, 2.5, 2.5), ("ellipse", 896.5, 476, 4.5, 4.5)],
                         spine_right=True),
}
SCALE = 27.4 / 447.0          # knife units per picture pixel, the same for all six so their sizes match the picture
U_START = 0.3


def cut(name):
    """Foreground mask of one knife in full-picture coordinates (GrabCut inside its box, holes cut)."""
    im = cv2.imread(REF)
    k = KNIVES[name]
    x0, y0, x1, y1 = k["box"]
    pad = 10
    X0, Y0, X1, Y1 = x0 - pad, y0 - pad, x1 + pad, min(im.shape[0], y1 + 30)
    crop = im[Y0:Y1, X0:X1].copy()
    mask = np.zeros(crop.shape[:2], np.uint8)
    bg = np.zeros((1, 65), np.float64)
    fg = np.zeros((1, 65), np.float64)
    cv2.grabCut(crop, mask, (pad, pad, x1 - x0, y1 - y0), bg, fg, 8, cv2.GC_INIT_WITH_RECT)
    m = np.where((mask == 1) | (mask == 3), 255, 0).astype(np.uint8)
    n, lab, stats, _ = cv2.connectedComponentsWithStats(m)
    if n > 1:
        m = np.where(lab == 1 + np.argmax(stats[1:, cv2.CC_STAT_AREA]), 255, 0).astype(np.uint8)
    full = np.zeros(im.shape[:2], np.uint8)
    full[Y0:Y1, X0:X1] = m
    for h in k["holes"]:
        if h[0] == "ellipse":
            cv2.ellipse(full, (int(round(h[1] * 4)), int(round(h[2] * 4))), (int(round(h[3] * 4)), int(round(h[4] * 4))), 0, 0, 360, 0, -1, shift=2)
        else:
            cv2.rectangle(full, (h[1], h[2]), (h[3], h[4]), 0, -1)
    return full


def mapping(name):
    """The knife's soft mask and where it sits in the picture: the bottom row (u = U_START) and the handle's centre (v = 0)."""
    full = cut(name).astype(np.float32) / 255.0
    full = cv2.GaussianBlur(full, (0, 0), 0.6)
    ys, xs = np.nonzero(full > 0.5)
    ybot = ys.max() + 0.5
    rows = full[int(ybot - (ybot - ys.min()) * 0.33):int(ybot), :] > 0.5
    xc = np.median(np.nonzero(rows)[1]) + 0.5
    return full, float(ybot), float(xc)


def build(name):
    """Bake one knife. Runs with KNIFE_CELL_H already set for its sheet height."""
    import make_tactical as T
    from hd_lib import U, V, GW, GH, GP, HALF, smoothstep, sdf_inside, lerp, hexc
    from scipy import ndimage
    k = KNIVES[name]
    pic = cv2.imread(REF, cv2.IMREAD_COLOR)[:, :, ::-1].astype(np.float32) / 255.0
    full, ybot, xc = mapping(name)
    # grid (u, v) -> picture (x, y): u runs up the knife, v across, spine (the picture's right) towards -v
    sgn = -1.0          # every knife in the picture has its spine on the right
    py = (ybot - (U - U_START) / SCALE).astype(np.float32)
    px = (xc + sgn * V / SCALE).astype(np.float32)
    alb = cv2.remap(pic, px, py, cv2.INTER_LANCZOS4, borderMode=cv2.BORDER_REPLICATE)
    alb = np.clip(alb, 0, 1)
    msk = np.clip(cv2.remap(full, px, py, cv2.INTER_LINEAR, borderValue=0), 0, 1)
    # a light unsharp mask brings back the crispness the upscale softens
    soft = cv2.GaussianBlur(alb, (0, 0), 1.2)
    alb = np.clip(alb + 0.45 * (alb - soft), 0, 1)

    def u_of(y):
        return U_START + (ybot - y) * SCALE

    def pic_to_grid(x, y):
        return u_of(y), (x - xc) / sgn * SCALE

    gu = u_of(k["guard"])
    gu_end = u_of(k.get("guard_end", k["guard"]))
    blade = msk * smoothstep(gu - 0.04, gu + 0.04, U)
    handle = np.clip(msk - blade, 0, 1)
    kn = T.Knife(name, k["name"])
    kn.glow = k["glow"]
    lum = alb.mean(-1)
    # ---- handle: rounded body, the photo's colour, a soft sheen
    if handle.max() > 0:
        inside = sdf_inside((handle > 0.5).astype(np.float32))
        dome = np.clip(inside / 0.5, 0, 1) ** 0.55 * 0.26
        is_brass = (alb[..., 0] > alb[..., 2] * 1.35) & (lum > 0.33)
        metal_h = np.where(is_brass, 0.9, 0.15).astype(np.float32)
        gloss_h = np.where(is_brass, 0.85, 0.22).astype(np.float32)
        # round each spot by its own thickness, so thin rings and rims keep their shape
        half_w = ndimage.maximum_filter(inside, size=int(GP * 0.9))
        rnd = np.clip(half_w * 0.35, 0.04, 0.32).astype(np.float32)
        kn.paint(handle, alb, height=dome, metal=metal_h, gloss=gloss_h, bevel=np.clip(1 - inside / 0.06, 0, 1) * 0.25,
                 side=np.clip(alb * 0.6, 0, 1), rnd=rnd)
    # ---- blade: flat with a ground bevel along the edge, metallic sheen from the photo's own light
    if blade.max() > 0:
        bm = (blade > 0.5)
        edge_side = (V > 0.0) if k["spine_right"] else (V < 0.0)     # the edge is the side away from the spine
        bnd = bm & ndimage.binary_dilation(~bm, iterations=1)
        d_edge = ndimage.distance_transform_edt(~(bnd & edge_side)) / GP
        bev = np.clip(1 - d_edge / 0.7, 0, 1) * bm
        h = -0.30 * bev ** 1.2
        kn.paint(blade, alb, height=h, metal=0.85, gloss=0.42 + 0.3 * np.clip(lum - 0.5, 0, 1),
                 bevel=np.clip(bev * 0.7 + np.clip(1 - d_edge / 0.07, 0, 1), 0, 1), side=np.clip(alb * 0.7, 0, 1), rnd=0.0)
    # tip: the highest u of the blade
    cols = np.nonzero((blade > 0.5).any(0))[0]
    tu = (cols.max() + 0.5) / GP if len(cols) else 27.9
    tcol = blade[:, min(GW - 1, cols.max())] if len(cols) else None
    tv = float((V[:, 0] * tcol).sum() / max(tcol.sum(), 1e-6)) if tcol is not None else 0.0
    kn.tip = (tu, tv)
    top = float(u_of(np.nonzero(full > 0.5)[0].min()))
    if name == "chroma_karambit":
        kn.part("ring", 0.0, u_of(k["ring"]), 0.42, 0.3)
        kn.part("handle", u_of(k["ring"]), gu, 0.95, 0.5)
    elif name == "chroma_phoenix":
        kn.part("pommel", 0.0, u_of(k["pommel"]), 1.0, 0.25)
        kn.part("handle", u_of(k["pommel"]), gu_end, 1.0, 0.5)
        kn.part("guard", gu_end, gu, 1.1, 0.2)
    elif name in ("chroma_m9", "chroma_kunai"):
        kn.part("handle", 0.0, gu_end, 0.95 if name == "chroma_m9" else 0.6, 0.5)
        kn.part("guard", gu_end, gu, 1.0 if name == "chroma_m9" else 0.3, 0.2)
    else:
        kn.part("handle", 0.0, gu, 0.9 if name != "chroma_crystal" else 0.55, 0.45)
    kn.part("blade", gu, max(top, gu + 1) + 0.2, 0.24, 0.0)
    entry = T.write(kn, min_level=1)
    if T.HALF != 3.55:
        entry["half"] = round(T.HALF, 4)
    if name == "chroma_butterfly":
        pl, pr = k["pins"]
        entry["pins"] = [list(map(lambda q: round(q, 3), pic_to_grid(*pl))), list(map(lambda q: round(q, 3), pic_to_grid(*pr)))]
    entry["rarity"] = "chroma"
    json.dump(entry, open(f"/tmp/chroma_{kn.id}.json", "w"))
    print("baked", kn.id, flush=True)


def main():
    if len(sys.argv) > 1 and sys.argv[1] == "--one":
        build(sys.argv[2])
        return
    jobs = [(n, None) for n in KNIVES]
    procs = []
    for n, _ in jobs:
        env = dict(os.environ, KNIFE_CELL_H=str(KNIVES[n]["cell"]))
        procs.append(subprocess.Popen([sys.executable, os.path.abspath(__file__), "--one", n], env=env))
    if any(pr.wait() != 0 for pr in procs):
        sys.exit("a knife failed to bake")
    import make_tactical as T
    meta = json.load(open(T.OUT_JSON))
    knives = [x for x in meta["knives"] if not x["id"].startswith("chroma_")]
    for n in KNIVES:
        knives.append(json.load(open(f"/tmp/chroma_{n}.json")))
    meta["knives"] = knives
    with open(T.OUT_JSON, "w") as f:
        json.dump(meta, f, indent=1)
    print("wrote", len(knives), "knives")
    subprocess.check_call([sys.executable, os.path.join(HERE, "make_pieces.py")])


if __name__ == "__main__":
    main()
