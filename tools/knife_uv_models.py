#!/usr/bin/env python3
"""Author the UV mapped knife: a vanilla-schema item model plus real 64x64 textures.

The detail that used to be hundreds of tinted triangles now lives in the texture:
blade silhouette with alpha, grind line, fuller, spine serrations, etched grain,
brass guard, rubber grip panel, screws and cord.  Geometry is only a handful of
thin blocks, so the silhouette stays crisp at any size and the mesh never fights
the texture.

Model space is y up with the grip base at y = 0 (the same axis the old meshes used).
Everything else is vanilla item model schema: texture_size in texels, faces keyed
north/south/east/west/up/down, uv in 0..16 units, optional element rotation and
display transforms.

Run: python3 tools/knife_uv_models.py
"""

import json
import math
import os

from PIL import Image, ImageChops, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_DIR = os.path.join(ROOT, "src/main/resources/assets/zeus/knives/models")
TEX_DIR = os.path.join(ROOT, "src/main/resources/assets/zeus/textures/knives")
TEX = 128   # 128 is still a normal Minecraft texture size, and it gives the blade art
            # aspect correct resolution: 24x128 texels over a 2.64 x 13.6 model face

BLADE_UV = (0.0, 0.0, 3.0, 16.0)    # blade art with alpha, 24x128 texels, real aspect
STRIP_UV = (3.0, 0.0, 4.0, 16.0)    # plain steel strip for the thin faces
BRASS_UV = (4.0, 0.0, 8.0, 4.0)     # brass, 32x32
CORD_UV = (4.0, 4.0, 8.0, 8.0)      # grip panel, 32x32


def tex_px(uv):
    k = TEX / 16.0
    return [int(round(v * k)) for v in uv]


# ------------------------------------------------------------------ steel sheet
def steel_sheet():
    """Pixel by pixel blade art: outline, grind, fuller, serrations, etched grain.

    Row 0 is the point and the last row is the guard, which is exactly how the uv rect is
    mapped onto the blade face (v = 0 at the tip corner).
    """
    img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    bx0, by0, bx1, by1 = tex_px(BLADE_UV)
    w, h = bx1 - bx0, by1 - by0
    cx = (w - 1) / 2.0
    half = (w - 1) / 2.0

    def width(t):
        """Half width in texels, t = 0 at the guard, 1 at the point."""
        if t < 0.05:
            return 0.62 + 0.33 * (t / 0.05)
        if t < 0.58:
            return 0.95 + 0.05 * math.sin((t - 0.05) / 0.53 * math.pi)
        if t < 0.84:
            return 1.0 - 0.05 * ((t - 0.58) / 0.26)
        return 0.95 * (1.0 - ((t - 0.84) / 0.16) ** 1.15)

    pix = img.load()
    for row in range(h):
        t = 1.0 - row / (h - 1.0)
        hw = width(t) * half
        if hw < 0.6:
            continue
        for x in range(w):
            d = (x - cx) / hw          # -1 at the spine, +1 at the cutting edge
            if abs(d) > 1.0:
                continue
            # serrations: notch the spine, three texels deep, alternating rows
            if d < -0.80 and 0.30 < t < 0.62:
                if int((1.0 - t) * h * 0.55) % 3 == 0:
                    continue
            # base steel, brighter towards the point, with etched grain
            v = 178 + int(34 * t) + int(12 * math.sin(t * 34 + d * 5.5)) + int(7 * math.sin(t * 61))
            col = (v, v + 4, v + 12)
            # polished grind along the cutting edge, with a hard break line
            if d > 0.62:
                g = 218 + int(30 * (1.0 - abs(d - 0.80) / 0.20))
                col = (g, g + 2, min(255, g + 8))
            elif d > 0.55:
                col = (96, 100, 112)    # the grind line itself
            # fuller: recessed channel across the middle of the flat
            if -0.34 < d < 0.18:
                f = 120 + int(22 * (d + 0.34) / 0.52)
                col = (f, f + 4, f + 12)
                if d < -0.30:
                    col = (232, 238, 248)   # lit lower lip of the groove
            # edge outline so the silhouette stays crisp at any size
            if abs(d) > 0.965:
                col = (28, 32, 40)
            pix[bx0 + x, by0 + row] = col + (255,)

    # bright cutting edge: one texel column just inside the outline
    for row in range(h):
        t = 1.0 - row / (h - 1.0)
        hw = width(t) * half
        x = int(round(cx + hw * 0.92))
        if 0 <= x < w:
            pix[bx0 + x, by0 + row] = (250, 252, 255, 255)
        x2 = int(round(cx - hw * 0.92))
        if 0 <= x2 < w:
            pix[bx0 + x2, by0 + row] = (44, 48, 58, 255)

    # thin face strip: mid steel with a lit edge so the blade has thickness
    sx0, sy0, sx1, sy1 = tex_px(STRIP_UV)
    for y in range(sy0, sy1):
        for x in range(sx0, sx1):
            k = (x - sx0) / max(1, sx1 - sx0 - 1)
            v = 100 + int(66 * k)
            pix[x, y] = (v, v + 3, v + 10, 255)
    return img


# ------------------------------------------------------------------ trim sheet
def trim_sheet():
    img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))

    # brass: warm bands with a top highlight and a dark lower lip, so a flat face still
    # reads as machined metal
    bx0, by0, bx1, by1 = tex_px(BRASS_UV)
    bw, bh = bx1 - bx0, by1 - by0
    brass = Image.new("RGBA", (bw, bh))
    bd = ImageDraw.Draw(brass)
    for y in range(bh):
        k = y / max(1, bh - 1)
        base = int(206 - 74 * k)
        bd.line([(0, y), (bw, y)], fill=(base, int(base * 0.76), int(base * 0.30), 255))
    bd.line([(0, 0), (bw, 0)], fill=(255, 240, 190, 255))
    bd.line([(0, 1), (bw, 1)], fill=(246, 224, 160, 255))
    bd.line([(0, bh - 1), (bw, bh - 1)], fill=(88, 58, 16, 255))
    img.paste(brass, (bx0, by0))

    # grip: black rubber panel, ribbed, with brass screws, so no geometry is needed
    cx0, cy0, cx1, cy1 = tex_px(CORD_UV)
    cw, ch = cx1 - cx0, cy1 - cy0
    grip = Image.new("RGBA", (cw, ch), (26, 27, 32, 255))
    gd = ImageDraw.Draw(grip)
    for y in range(0, ch, 3):
        gd.line([(0, y), (cw, y)], fill=(44, 46, 52, 255))
        gd.line([(0, y + 1), (cw, y + 1)], fill=(22, 23, 27, 255))
    for x in range(1, cw, 4):
        gd.line([(x, 0), (x, ch)], fill=(38, 39, 45, 70))
    # side shading so a flat panel looks rounded
    for x in range(cw):
        k = abs(x - cw / 2) / (cw / 2)
        if k < 0.98:
            gd.line([(x, 0), (x, ch)], fill=(0, 0, 0, int(120 * k ** 2)))
    # brass screws
    for sy in (int(ch * 0.16), int(ch * 0.84)):
        sx = cw // 2
        gd.ellipse([sx - 3, sy - 3, sx + 3, sy + 3], fill=(52, 38, 12, 255))
        gd.ellipse([sx - 2, sy - 2, sx + 2, sy + 2], fill=(206, 160, 72, 255))
        gd.point((sx - 1, sy - 1), fill=(240, 202, 122, 255))
        gd.line([(sx, sy - 2), (sx, sy + 2)], fill=(112, 80, 24, 255))
    img.paste(grip, (cx0, cy0))
    return img


# ------------------------------------------------------------------ model
def faces(uv_front, uv_side, tex_front="#steel", tex_side="#steel"):
    return {
        "north": {"uv": list(uv_front), "texture": tex_front},
        "south": {"uv": list(uv_front), "texture": tex_front},
        "east": {"uv": list(uv_side), "texture": tex_side},
        "west": {"uv": list(uv_side), "texture": tex_side},
        "up": {"uv": list(uv_side), "texture": tex_side},
        "down": {"uv": list(uv_side), "texture": tex_side},
    }


def el(name, frm, to, fc, rot=None):
    e = {"name": name, "from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": fc}
    if rot:
        e["rotation"] = rot
    return e


def knife_model():
    els = []
    # blade: thin block, the silhouette is the alpha in the steel sheet
    els.append(el("blade", (-1.58, 5.6, -0.16), (1.58, 20.4, 0.16), faces(BLADE_UV, STRIP_UV)))
    # brass guard: centre block plus two swept wings
    els.append(el("guard", (-0.74, 4.72, -0.48), (0.74, 5.66, 0.48), faces(BRASS_UV, BRASS_UV, "#trim", "#trim")))
    for s in (-1, 1):
        els.append(el("wing%d" % s, (s * 0.70 - 0.28, 4.50, -0.40), (s * 0.70 + 0.86, 5.12, 0.40),
                      faces(BRASS_UV, BRASS_UV, "#trim", "#trim"),
                      rot={"origin": [s * 0.70, 4.80, 0.0], "axis": "z", "angle": s * -16, "rescale": False}))
    # upper and lower brass bolsters
    els.append(el("bolster_hi", (-1.14, 4.22, -0.62), (1.14, 4.74, 0.62), faces(BRASS_UV, BRASS_UV, "#trim", "#trim")))
    els.append(el("bolster_lo", (-1.14, 0.54, -0.62), (1.14, 1.06, 0.62), faces(BRASS_UV, BRASS_UV, "#trim", "#trim")))
    # grip panel
    els.append(el("grip", (-1.06, 1.06, -0.58), (1.06, 4.22, 0.58), faces(CORD_UV, CORD_UV, "#trim", "#trim")))
    return {
        "texture_size": [TEX, TEX],
        "name": "Kunai",
        "textures": {"steel": "zeus:knives/tactical_steel", "trim": "zeus:knives/tactical_trim"},
        "elements": els,
        "display": {
            "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
            "firstperson_righthand": {"rotation": [0, -90, 20], "translation": [1.13, 3.2, 1.13], "scale": [0.62, 0.62, 0.62]},
            "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.52, 0.52, 0.52]},
        },
        "ambientocclusion": False,
    }


# ------------------------------------------------------------------ preview
SHADE = {"north": 1.0, "south": 0.70, "east": 0.84, "west": 0.84, "up": 0.92, "down": 0.60}


def corners_of(e):
    x0, y0, z0 = e["from"]
    x1, y1, z1 = e["to"]
    c = {
        "north": [(x0, y1, z0), (x1, y1, z0), (x1, y0, z0), (x0, y0, z0)],
        "south": [(x1, y1, z1), (x0, y1, z1), (x0, y0, z1), (x1, y0, z1)],
        "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "up": [(x0, y1, z1), (x1, y1, z1), (x1, y1, z0), (x0, y1, z0)],
        "down": [(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)],
    }
    out = {}
    rot = e.get("rotation")
    for k, pts in c.items():
        if rot and rot.get("axis") == "z":
            ox, oy, _ = rot["origin"]
            a = math.radians(rot["angle"])
            ca, sa = math.cos(a), math.sin(a)
            pts = [(ox + (x - ox) * ca - (y - oy) * sa, oy + (x - ox) * sa + (y - oy) * ca, z) for x, y, z in pts]
        out[k] = pts
    return out


def render(model, sheets, tint, out_path, size=560, ax=math.radians(-6), ay=math.radians(-22)):
    def proj(p):
        x, y, z = p
        cy, sy = math.cos(ay), math.sin(ay)
        cx, sx = math.cos(ax), math.sin(ax)
        x, z = x * cy + z * sy, -x * sy + z * cy
        y, z = y * cx - z * sx, y * sx + z * cx
        return x, y, z

    quads = []
    for e in model["elements"]:
        cs = corners_of(e)
        for dirn, face in e["faces"].items():
            pr = [proj(p) for p in cs[dirn]]
            quads.append((sum(p[2] for p in pr) / 4.0, pr, face["uv"], face["texture"], SHADE[dirn]))

    pts = [p for q in quads for p in q[1]]
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    span = max(max(xs) - min(xs), max(ys) - min(ys))
    S = size * 0.88 / span
    ox = size / 2 - (min(xs) + max(xs)) / 2 * S
    oy = size / 2 + (min(ys) + max(ys)) / 2 * S

    canvas = Image.new("RGBA", (size, size), (16, 18, 24, 255))
    quads.sort(key=lambda q: q[0])  # far faces first: larger z is nearer the viewer
    for _, pr, uv, texkey, shade in quads:
        sheet = sheets[texkey]
        tw, th = sheet.size
        u0, v0, u1, v1 = [v * tw / 16.0 for v in uv]
        scr = [(ox + p[0] * S, oy - p[1] * S) for p in pr]
        # affine source->dest for a planar quad: dest = c0 + a*(c1-c0) + b*(c3-c0)
        c0, c1, c3 = scr[0], scr[1], scr[3]
        ex = (c1[0] - c0[0], c1[1] - c0[1])
        ey = (c3[0] - c0[0], c3[1] - c0[1])
        det = ex[0] * ey[1] - ex[1] * ey[0]
        if abs(det) < 1e-6:
            continue
        # dest->source: solve [ex ey] * [a b] = p - c0
        ia = (ey[1] / det, -ey[0] / det)
        ib = (-ex[1] / det, ex[0] / det)
        # source pixel = (u0 + a*(u1-u0), v0 + b*(v1-v0))
        su, sv = (u1 - u0), (v1 - v0)
        A = ia[0] * su
        B = ib[0] * su
        C = u0 - (c0[0]) * A - (c0[1]) * B
        D = ia[1] * sv
        E = ib[1] * sv
        F = v0 - (c0[0]) * D - (c0[1]) * E
        warped = sheet.transform((size, size), Image.AFFINE, (A, B, C, D, E, F), resample=Image.NEAREST)
        if shade < 1.0:
            dark = Image.new("RGBA", (size, size), (0, 0, 0, int(255 * (1 - shade))))
            warped = Image.composite(dark, warped, Image.composite(
                Image.new("L", (size, size), 255), Image.new("L", (size, size), 0), warped.split()[3]))
        if texkey == "#steel":
            # tint layer: multiply the finish colour over the neutral steel art so the
            # etched grain and the grind line survive
            r, g, b, a = warped.split()
            mul = Image.merge("RGB", (
                r.point(lambda v: v * tint[0] // 255),
                g.point(lambda v: v * tint[1] // 255),
                b.point(lambda v: v * tint[2] // 255)))
            warped = Image.merge("RGBA", (*mul.split(), a))
        # clip to the quad: the affine maps the whole canvas, only the quad is this face
        clip = Image.new("L", (size, size), 0)
        ImageDraw.Draw(clip).polygon(scr, fill=255)
        warped.putalpha(ImageChops.multiply(warped.split()[3], clip))
        canvas.alpha_composite(warped)
    canvas.convert("RGB").save(out_path)
    print("preview ->", out_path)


def main():
    os.makedirs(MODEL_DIR, exist_ok=True)
    os.makedirs(TEX_DIR, exist_ok=True)
    model = knife_model()
    with open(os.path.join(MODEL_DIR, "tactical.json"), "w", encoding="utf-8") as fh:
        json.dump(model, fh, indent=2)
    steel_sheet().save(os.path.join(TEX_DIR, "tactical_steel.png"))
    trim_sheet().save(os.path.join(TEX_DIR, "tactical_trim.png"))
    with open(os.path.join(MODEL_DIR, "index.txt"), "w", encoding="utf-8") as fh:
        fh.write("# UV mapped knife models, one id per line\ntactical\n")
    print("wrote tactical.json + 2 textures,", len(model["elements"]), "elements")

    sheets = {
        "#steel": Image.open(os.path.join(TEX_DIR, "tactical_steel.png")).convert("RGBA"),
        "#trim": Image.open(os.path.join(TEX_DIR, "tactical_trim.png")).convert("RGBA"),
    }
    os.makedirs(os.path.join(ROOT, "artifacts"), exist_ok=True)
    # a few finishes, to check the tint layer reads as the palette without killing detail
    for i, (nm, tint) in enumerate([("stainless", (201, 208, 217)),
                                    ("blue", (150, 180, 235)),
                                    ("gold", (236, 209, 108)),
                                    ("crimson", (235, 150, 150))]):
        render(model, sheets, tint, os.path.join(ROOT, "artifacts", "uv_%s.png" % nm))


if __name__ == "__main__":
    main()
