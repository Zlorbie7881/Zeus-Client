"""Draws the Zeus lettering: an angular, carved-metal capital alphabet in the style of the menu's stone plates.

  greek.png    one row of every glyph (silver, with a bevel), cap height CAP pixels
  greek.json   where every glyph is in the row and how far to move on after it

Every letter is a skeleton of straight pieces and smooth curves, drawn as a stroke with flared ends, then lit from the top left
so it looks cut out of metal. A, E, G, I, L, M, N, O, P, R, S, T, U, X, Y and Z follow the lettering of the menu's plates;
the others (B, D, F, H, J, K, Q, V, W, the numbers and the marks) are drawn in the same hand.

Usage: python3 tools/greek_font.py <output folder>
"""
import sys, os, json, math
import numpy as np, cv2

CAP = 128          # output cap height in pixels
SS = 4             # supersampling
STROKE = 0.165     # stroke width as a part of the cap height
PAD = 14           # margin round every glyph in the atlas (room for the bevel and the shadow)
ROW = 190          # atlas height

# ---- skeletons. x 0..W (glyph width), y 0..100 (0 top, 100 baseline). 'S' marks a smooth curve through the points, 'L' straight pieces.
def G(w, *strokes):
    return {"w": w, "s": strokes}

def L(*pts):
    return ("L", pts)

def S(*pts):
    return ("S", pts)

def C(*pts):
    """closed straight polygon"""
    return ("L", tuple(pts) + (pts[0],))

GLYPHS = {
    "A": G(100, L((0, 100), (50, 0), (100, 100)), L((22, 68), (78, 68))),
    "B": G(86, L((0, 0), (0, 100)), L((0, 0), (52, 0), (74, 12), (74, 36), (52, 50), (0, 50)), L((52, 50), (66, 50), (86, 64), (86, 86), (62, 100), (0, 100))),
    "C": G(92, S((88, 20), (66, 3), (40, 0), (15, 12), (4, 38), (4, 62), (15, 88), (40, 100), (66, 97), (88, 80))),
    "D": G(88, L((0, 0), (0, 100)), L((0, 0), (46, 0), (82, 24), (88, 50), (82, 76), (46, 100), (0, 100))),
    "E": G(78, L((78, 0), (0, 0), (0, 100), (78, 100)), L((0, 50), (60, 50))),
    "F": G(76, L((76, 0), (0, 0), (0, 100)), L((0, 50), (58, 50))),
    "G": G(98, S((90, 20), (68, 3), (42, 0), (16, 12), (4, 38), (4, 62), (16, 88), (42, 100), (70, 98), (92, 82)), L((92, 82), (92, 54), (56, 54))),
    "H": G(88, L((0, 0), (0, 100)), L((88, 0), (88, 100)), L((0, 50), (88, 50))),
    "I": G(14, L((0, 0), (0, 100))),
    "J": G(62, S((62, 0), (62, 66), (54, 90), (34, 100), (12, 96), (0, 82))),
    "K": G(90, L((0, 0), (0, 100)), L((88, 0), (6, 56), (92, 100))),
    "L": G(74, L((0, 0), (0, 100), (74, 100))),
    "M": G(112, L((0, 100), (0, 0), (56, 66), (112, 0), (112, 100))),
    "N": G(92, L((0, 100), (0, 0), (92, 100), (92, 0))),
    "O": G(96, C((48, 0), (96, 50), (48, 100), (0, 50))),
    "P": G(78, L((0, 0), (0, 100)), L((0, 0), (52, 0), (74, 14), (74, 38), (52, 52), (0, 52))),
    "Q": G(96, C((48, 0), (96, 50), (48, 100), (0, 50)), L((58, 72), (100, 112))),
    "R": G(86, L((0, 0), (0, 100)), L((0, 0), (52, 0), (74, 14), (74, 38), (52, 52), (0, 52)), L((40, 52), (86, 100))),
    "S": G(80, S((76, 14), (56, 2), (30, 2), (9, 16), (12, 36), (36, 48), (62, 58), (76, 74), (68, 92), (46, 99), (22, 98), (4, 84))),
    "T": G(92, L((0, 0), (92, 0)), L((46, 0), (46, 100))),
    "U": G(90, S((0, 0), (0, 66), (12, 90), (45, 100), (78, 90), (90, 66), (90, 0))),
    "V": G(96, L((0, 0), (48, 100), (96, 0))),
    "W": G(130, L((0, 0), (32, 100), (65, 26), (98, 100), (130, 0))),
    "X": G(92, L((0, 0), (92, 100)), L((92, 0), (0, 100))),
    "Y": G(92, L((0, 0), (46, 52), (92, 0)), L((46, 52), (46, 100))),
    "Z": G(84, L((0, 0), (84, 0), (0, 100), (84, 100))),
    "0": G(70, C((35, 0), (70, 50), (35, 100), (0, 50))),
    "1": G(40, L((4, 20), (30, 0), (30, 100))),
    "2": G(72, S((2, 22), (14, 4), (38, 0), (62, 8), (68, 28), (50, 54), (0, 100)), L((0, 100), (72, 100))),
    "3": G(70, S((4, 8), (30, 0), (56, 6), (64, 24), (44, 44), (24, 48)), S((44, 48), (68, 58), (70, 80), (50, 98), (24, 100), (2, 88))),
    "4": G(78, L((58, 100), (58, 0), (0, 70), (78, 70))),
    "5": G(70, L((66, 0), (8, 0), (4, 46)), S((4, 46), (28, 40), (52, 44), (68, 62), (66, 84), (44, 98), (20, 100), (2, 90))),
    "6": G(72, S((66, 10), (44, 0), (20, 10), (6, 40), (4, 68), (14, 92), (36, 100), (58, 94), (68, 74), (58, 54), (36, 48), (14, 56))),
    "7": G(72, L((0, 0), (72, 0), (26, 100))),
    "8": G(72, S((36, 50), (14, 40), (10, 20), (22, 4), (36, 0), (50, 4), (62, 20), (58, 40), (36, 50), (10, 62), (4, 82), (18, 98), (36, 100), (54, 98), (68, 82), (62, 62), (36, 50))),
    "9": G(72, S((6, 90), (28, 100), (52, 90), (66, 60), (68, 32), (58, 8), (36, 0), (14, 6), (4, 26), (14, 46), (36, 52), (58, 44))),
    ".": G(16, L((8, 94), (8, 100))),
    ",": G(18, L((9, 92), (3, 112))),
    ":": G(16, L((8, 30), (8, 36)), L((8, 94), (8, 100))),
    ";": G(18, L((9, 30), (9, 36)), L((9, 92), (3, 112))),
    "-": G(46, L((0, 52), (46, 52))),
    "'": G(14, L((7, 0), (7, 26))),
    "!": G(16, L((8, 0), (8, 68)), L((8, 94), (8, 100))),
    "?": G(66, S((2, 22), (14, 4), (36, 0), (58, 8), (64, 28), (46, 50), (32, 62), (32, 74)), L((32, 94), (32, 100))),
    "(": G(34, S((30, -6), (8, 28), (8, 72), (30, 106))),
    ")": G(34, S((4, -6), (26, 28), (26, 72), (4, 106))),
    "/": G(52, L((52, 0), (0, 100))),
    "+": G(64, L((32, 20), (32, 80)), L((2, 50), (62, 50))),
    "&": G(100, S((92, 100), (50, 46), (38, 20), (50, 2), (68, 12), (66, 32), (30, 62), (14, 82), (26, 98), (52, 98), (74, 76), (86, 50))),
    "%": G(100, L((100, 0), (0, 100)), C((14, 0), (30, 14), (14, 28), (0, 14)), C((86, 72), (100, 86), (86, 100), (72, 86))),
    "_": G(60, L((0, 100), (60, 100))),
    "#": G(80, L((24, 0), (18, 100)), L((62, 0), (56, 100)), L((4, 34), (78, 34)), L((2, 68), (76, 68))),
}
ADV_GAP = 21       # space between letters (in skeleton units)
SPACE = 46         # width of a space (in skeleton units)


def catmull(pts, n=14):
    p = [pts[0]] + list(pts) + [pts[-1]]
    out = []
    for i in range(1, len(p) - 2):
        p0, p1, p2, p3 = [np.array(q, np.float32) for q in p[i - 1:i + 3]]
        for k in range(n):
            t = k / n
            out.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (-p0 + 3 * p1 - 3 * p2 + p3) * t ** 3))
    out.append(np.array(pts[-1], np.float32))
    return out


def stroke_mask(g, scale, sw):
    """The glyph's strokes, drawn at the working size; returns (mask, width in pixels)."""
    w = int(g["w"] * scale) + 2 * PAD * SS
    h = ROW * SS
    m = np.zeros((h, w), np.uint8)
    ox, oy = PAD * SS, int(30 * SS)
    for kind, pts in g["s"]:
        pp = catmull(pts) if kind == "S" else [np.array(q, np.float32) for q in pts]
        arr = np.array([[ox + q[0] * scale, oy + q[1] * scale] for q in pp], np.float32)
        arr16 = np.round(arr * 16).astype(np.int32)
        cv2.polylines(m, [arr16], False, 255, sw, cv2.LINE_AA, 4)
    return m


def shade(m):
    """Silver bevel lit from the top left."""
    mf = m.astype(np.float32) / 255.0
    sw = STROKE * CAP * SS
    h = cv2.GaussianBlur(mf, (0, 0), 0.30 * sw)
    h2 = cv2.GaussianBlur(mf, (0, 0), 0.12 * sw)
    height = 0.65 * h + 0.35 * h2
    gx = cv2.Sobel(height, cv2.CV_32F, 1, 0, ksize=3)
    gy = cv2.Sobel(height, cv2.CV_32F, 0, 1, ksize=3)
    k = 16.0 * SS / 4
    nx, ny, nz = -gx * k, -gy * k, np.ones_like(gx)
    nl = np.sqrt(nx * nx + ny * ny + nz * nz)
    nx, ny, nz = nx / nl, ny / nl, nz / nl
    lx, ly, lz = -0.55, -0.62, 0.56
    ln = math.sqrt(lx * lx + ly * ly + lz * lz)
    diff = np.clip((nx * lx + ny * ly + nz * lz) / ln, 0, 1)
    spec = np.clip((nx * lx + ny * ly + nz * lz) / ln, 0, 1) ** 14
    yy = np.linspace(0, 1, m.shape[0])[:, None]
    base = 0.58 + 0.30 * (1 - np.abs(yy - 0.36) * 1.9)      # a lighter band across the letters, like brushed metal
    # fine grain
    rng = np.random.default_rng(3)
    grain = cv2.GaussianBlur(rng.standard_normal(m.shape).astype(np.float32), (0, 0), 0.8 * SS / 4) * 0.045
    lum = np.clip(base * (0.42 + 0.78 * diff) + 0.55 * spec + grain, 0, 1)
    col = np.zeros(m.shape + (3,), np.float32)
    col[..., 0] = lum * 0.93 * 255      # B
    col[..., 1] = lum * 0.96 * 255      # G
    col[..., 2] = lum * 1.00 * 255      # R   (slightly cool silver)
    # dark rim where the metal meets the stone
    edge = np.clip(mf - cv2.erode(mf, np.ones((3, 3), np.uint8), iterations=max(1, SS // 2)), 0, 1)
    col *= (1 - 0.45 * edge[..., None])
    alpha = (cv2.GaussianBlur(mf, (0, 0), 0.5) * 255)
    return col, alpha


def render_glyph(ch):
    g = GLYPHS[ch]
    scale = SS * CAP / 100.0
    sw = max(2, int(round(STROKE * CAP * SS)))
    m = stroke_mask(g, scale, sw)
    col, a = shade(m)
    img = np.dstack([col, a]).astype(np.float32)
    out = cv2.resize(img, (img.shape[1] // SS, img.shape[0] // SS), interpolation=cv2.INTER_AREA)
    return np.clip(out, 0, 255).astype(np.uint8)


def build(out):
    os.makedirs(out, exist_ok=True)
    chars = list(GLYPHS.keys())
    parts = []
    meta = {"cap": CAP, "row": ROW, "base": 30 + CAP, "pad": PAD, "space": round(SPACE * CAP / 100.0), "gap": round(ADV_GAP * CAP / 100.0), "g": {}}
    x = 0
    for ch in chars:
        im = render_glyph(ch)
        parts.append(im)
        wpx = round(GLYPHS[ch]["w"] * CAP / 100.0)
        meta["g"][ch] = [x, im.shape[1], wpx]        # start in the atlas, width of the picture, width of the letter
        x += im.shape[1]
    atlas = np.concatenate(parts, axis=1)
    cv2.imwrite(os.path.join(out, "greek.png"), atlas)
    json.dump(meta, open(os.path.join(out, "greek.json"), "w"), separators=(",", ":"))
    print("greek font:", atlas.shape, len(chars), "glyphs")
    return atlas, meta


if __name__ == "__main__":
    build(sys.argv[1])
