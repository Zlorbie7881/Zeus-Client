#!/usr/bin/env python3
"""
Cuts every butterfly knife into its three moving pieces, straight from the finished sheets, so the hand can play
real balisong tricks with them: the blade turns round the held handle's pin, and the swinging handle turns round
its own pin, which rides on the blade.

  python3 tools/knives_hd/make_pieces.py

For each butterfly knife <id> it writes <id>__blade / __held / __swing sheets (every detail level the knife has)
and a "pieces" entry in knives/hd/tactical.json:

  {"of": "<id>", "held_pin": [u, v], "swing_pin": [u, v], "blade": {...model}, "held": {...}, "swing": {...}}

The held handle is the one on the spine side (v < 0), the swinging handle the one on the edge side.
"""
import json
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
RES = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "zeus")
JSON = os.path.join(RES, "knives", "hd", "tactical.json")
TEX = os.path.join(RES, "textures", "knives", "hd")
CELLS = 7
GLOSS, EDGE, PROFILE = 4, 5, 6


def generic_split(u, v):
    """The drawn butterflies (make_knives.butterfly): two handles either side of v = 0 up to u 12.9, the latch on the edge handle."""
    hand = u < 12.9
    latch = u < 1.25
    swing = hand & ((v > 0.0) | latch)
    held = hand & ~swing
    return held, swing


def chroma_split(entry):
    """The Stardust butterfly traced from the picture: each handle's round head wraps its pin; the kicker belongs to the blade."""
    import make_chroma as C
    _, ybot, xc = C.mapping("chroma_butterfly")

    def split(u, v):
        y = ybot - (u - C.U_START) / C.SCALE
        x = xc - v / C.SCALE
        hand = y > 266.0
        kicker = (x < 726.0) & (y < 292.0)
        swing = hand & ~kicker & (x < 750.0)       # the picture's left handle: the edge side
        held = hand & (x >= 750.0)
        return held, swing
    return split


def centre_line(alpha, length, half):
    """Centre of material across the knife, sampled like make_tactical.write (143 points along u)."""
    h, w = alpha.shape
    vs = -half + (np.arange(h) + 0.5) / h * 2 * half
    wsum = alpha.sum(0)
    cv = (vs[:, None] * alpha).sum(0) / np.maximum(wsum, 1e-6)
    out = []
    n = 142
    for i in range(n + 1):
        col = min(w - 1, int(length * i / n / length * w))
        lo, hi = max(0, col - 3), min(w, col + 4)
        ww = wsum[lo:hi]
        out.append(round(float((cv[lo:hi] * ww).sum() / ww.sum()), 3) if ww.sum() > 1e-3 else None)
    last = next((x for x in out if x is not None), 0.0)
    for i, x in enumerate(out):
        if x is None:
            out[i] = last
        else:
            last = x
    return out


def cut(entry, length, split, held_pin, swing_pin):
    kid = entry["id"]
    half = entry.get("half", 3.55)
    pieces = {}
    l2 = os.path.join(os.path.dirname(os.path.abspath(__file__)), "level2")
    src = {lv: os.path.join(TEX, f"{kid}_{lv}.png") for lv in (0, 1)}
    src[2] = os.path.join(l2, f"{kid}_2.png")   # level 2 base sheets are not shipped (see make_tactical.py)
    levels = [lv for lv in (0, 1, 2) if os.path.exists(src[lv])]
    masks_l1 = None
    for lv in levels:
        sheet = np.asarray(Image.open(src[lv]).convert("RGBA")).astype(np.float32)
        H, W = sheet.shape[:2]
        ch = H // CELLS
        u = (np.arange(W) + 0.5) / W * length
        v = -half + (np.arange(ch) + 0.5) / ch * 2 * half
        U, V = np.meshgrid(u, v)
        held, swing = split(U, V)
        blade = ~(held | swing)
        hand_u = (held | swing).any(0)               # columns the handles reach, for the profile strip
        cut_u = u[hand_u].max() if hand_u.any() else 0.0
        for name, m in (("blade", blade), ("held", held), ("swing", swing)):
            out = sheet.copy()
            for c in range(CELLS):
                rows = slice(c * ch, (c + 1) * ch)
                if c == PROFILE:
                    keep = np.broadcast_to((u >= cut_u) if name == "blade" else (u < cut_u), (ch, W)).astype(np.float32)
                else:
                    keep = m.astype(np.float32)
                if c in (GLOSS, EDGE):
                    out[rows, :, :4] *= keep[..., None]      # light cells add their colour: dim it away too
                else:
                    out[rows, :, 3] *= keep
            Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGBA").save(
                os.path.join(TEX, f"{kid}__{name}_{lv}.png"), optimize=True)
            if lv == levels[0]:
                pieces[name] = (out[0:ch, :, 3] / 255.0, u, cut_u)
    models = {}
    for name, (alpha, u, cut_u) in pieces.items():
        cols = np.nonzero(alpha.max(0) > 0.05)[0]
        u0 = float(u[cols.min()]) if len(cols) else 0.0
        u1 = float(u[cols.max()]) if len(cols) else length
        m = {"id": f"{kid}__{name}", "min_level": entry.get("min_level", 0), "name": entry["name"],
             "glow": entry["glow"], "centre": centre_line(alpha, length, half)}
        if "half" in entry:
            m["half"] = entry["half"]
        if name == "blade":
            m["tip"] = entry["tip"]
            parts = [p for p in entry["parts"] if p["name"] == "blade"]
            bt = parts[0]["t"] if parts else 0.22
            m["parts"] = [{"name": "blade", "u0": round(min(u0, cut_u), 3), "u1": round(u1 + 0.1, 3), "t": bt, "r": 0.0}]
        else:
            hp = [p for p in entry["parts"] if p["name"] == "handle"]
            ht, hr = (hp[0]["t"], hp[0]["r"]) if hp else (0.7, 0.2)
            pin = held_pin if name == "held" else swing_pin
            m["tip"] = [pin[0], pin[1]]
            # the two handles sit side by side in the picture; each is a slab of its own, a little thinner
            m["parts"] = [{"name": "handle", "u0": round(u0, 3), "u1": round(u1 + 0.05, 3), "t": round(ht * 0.8, 3), "r": round(min(hr, ht * 0.6), 3)}]
        models[name] = m
    return {"of": kid, "held_pin": [round(held_pin[0], 3), round(held_pin[1], 3)],
            "swing_pin": [round(swing_pin[0], 3), round(swing_pin[1], 3)],
            "blade": models["blade"], "held": models["held"], "swing": models["swing"]}


def main():
    meta = json.load(open(JSON))
    length = meta["len"]
    out = []
    for e in meta["knives"]:
        kid = e["id"]
        if kid.startswith("butterfly_"):
            out.append(cut(e, length, generic_split, (12.2, -0.7), (12.2, 0.7)))
        elif kid == "chroma_butterfly":
            pins = sorted(e["pins"], key=lambda p: p[1])        # spine side (v < 0) first: the held handle
            out.append(cut(e, length, chroma_split(e), pins[0], pins[1]))
        else:
            continue
        print("cut", kid, flush=True)
    meta["pieces"] = out
    with open(JSON, "w") as f:
        json.dump(meta, f, indent=1)
    print("wrote", len(out), "butterflies in pieces")


if __name__ == "__main__":
    main()
