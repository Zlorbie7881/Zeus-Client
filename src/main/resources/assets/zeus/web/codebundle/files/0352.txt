"""Splits the reference picture of the menu (tools/menu_reference.jpg, 1360 x 768) into layers:

  background.png      the whole scene without logo, buttons, icons or clouds, 1920 x 1080
  pillar_l.png        the two columns on the left, cut out, 1920 x 1080 with a clear background
  pillar_r.png        the same on the right
  plate.png           an empty button plate
  ink_<name>.png      the lettering of each button, cut out
  icon_<name>.png     the four round buttons in the bottom left, cut out
  logo.png            the logo with its lettering

Usage: python3 tools/menu_layers.py <reference jpg> <output folder>
All positions (in reference pixels) are the ones the menu code uses, see ZeusTitleScreen.java.
"""
import sys, os
import numpy as np, cv2

MW, MH = 1360, 768
CW, CH = 1920, 1080

PLATE_X0, PLATE_X1 = 490, 872
PLATE_Y = [351, 414, 476, 539, 602, 665]
PLATE_H = 54
ICON_C = [(74.5, 714.0), (167.5, 714.0), (256.5, 714.0), (348.5, 714.0)]
ICON_R = 34
NAMES = ["singleplayer", "multiplayer", "customs", "settings", "realms", "exit"]
ICONS = ["language", "accessibility", "settings", "credits"]
LOGO_BOX = (500, 14, 868, 346)          # x0, y0, x1, y1 of the medallion with its lettering and the line under it
MEDAL = (678.0, 145.0, 123.0)           # centre x, centre y, radius

# columns: for every row, where the stone starts and ends; (y, x) pairs, in between they are interpolated
COLS = {
    "l_near": ([(0, 0), (768, 0)], [(0, 148), (88, 148), (96, 143), (124, 128), (400, 130), (640, 135), (656, 146), (690, 149), (768, 151)]),
    "l_far": ([(0, 171), (140, 171), (150, 166), (175, 172), (400, 172), (640, 168), (670, 160), (700, 156), (768, 152)],
              [(0, 241), (140, 241), (152, 243), (176, 233), (400, 233), (640, 238), (660, 246), (700, 249), (768, 252)]),
    "r_near": ([(0, 1211), (84, 1211), (98, 1218), (124, 1235), (150, 1238), (400, 1233), (640, 1226), (656, 1216), (690, 1206), (720, 1196), (768, 1186)],
               [(0, 1360), (768, 1360)]),
    "r_far": ([(0, 1125), (140, 1125), (150, 1121), (175, 1131), (400, 1131), (640, 1125), (665, 1115), (690, 1107), (768, 1100)],
              [(0, 1190), (140, 1190), (152, 1196), (176, 1189), (400, 1189), (640, 1193), (660, 1199), (700, 1202), (768, 1204)]),
}


def column_mask(h, w, feather=1.2):
    m = np.zeros((h, w), np.float32)
    ys = np.arange(h)
    for left, right in COLS.values():
        ly = np.interp(ys, [p[0] for p in left], [p[1] for p in left])
        ry = np.interp(ys, [p[0] for p in right], [p[1] for p in right])
        xs = np.arange(w)[None, :]
        m = np.maximum(m, ((xs >= ly[:, None] - 0.5) & (xs <= ry[:, None] + 0.5)).astype(np.float32))
    return cv2.GaussianBlur(m, (0, 0), feather)


def smooth_fill(img, mask, scale=4, radius=6):
    """Fills the masked part from its surroundings (done small, so it is smooth), then adds grain like the rest of the picture."""
    h, w = mask.shape
    small = cv2.resize(img, (w // scale, h // scale), interpolation=cv2.INTER_AREA)
    m = cv2.resize((mask > 0.5).astype(np.uint8) * 255, (w // scale, h // scale), interpolation=cv2.INTER_NEAREST)
    m = cv2.dilate(m, np.ones((3, 3), np.uint8))
    filled = cv2.inpaint(small, m, radius, cv2.INPAINT_NS)
    filled = cv2.GaussianBlur(filled, (0, 0), 2.0)
    big = cv2.resize(filled, (w, h), interpolation=cv2.INTER_CUBIC).astype(np.float32)
    rng = np.random.default_rng(5)
    grain = cv2.GaussianBlur(rng.normal(0, 1, (h, w)).astype(np.float32), (0, 0), 0.8) * 3.2
    big += grain[..., None]
    a = cv2.GaussianBlur((mask > 0.5).astype(np.float32), (0, 0), 1.5)[..., None]
    return img.astype(np.float32) * (1 - a) + big * a


def stars(img, box, count, seed):
    """A sprinkling of small stars inside box (x0, y0, x1, y1) of the picture."""
    rng = np.random.default_rng(seed)
    x0, y0, x1, y1 = box
    layer = np.zeros(img.shape[:2], np.float32)
    for _ in range(count):
        x = rng.uniform(x0, x1)
        y = rng.uniform(y0, y1)
        b = rng.random() ** 3.0
        r = 0.55 + 0.5 * b
        xi, yi = int(x), int(y)
        for dy in range(-2, 3):
            for dx in range(-2, 3):
                px, py = xi + dx, yi + dy
                if 0 <= px < img.shape[1] and 0 <= py < img.shape[0]:
                    d2 = (px + 0.5 - x) ** 2 + (py + 0.5 - y) ** 2
                    layer[py, px] += (60 + 190 * b) * np.exp(-d2 / (2 * r * r))
    col = np.array([255, 236, 214], np.float32)       # BGR: slightly blue white
    return np.clip(img + layer[..., None] * (col / 255.0), 0, 255)


def up(img, interp=cv2.INTER_LANCZOS4):
    out = cv2.resize(img, (CW, CH), interpolation=interp)
    return out


def sharpen(img, amount=0.55, sigma=1.1):
    blur = cv2.GaussianBlur(img, (0, 0), sigma)
    return np.clip(img * (1 + amount) - blur * amount, 0, 255)


def main(ref, out):
    os.makedirs(out, exist_ok=True)
    src = cv2.imread(ref, cv2.IMREAD_COLOR)
    assert src.shape[1] == MW and src.shape[0] == MH, src.shape
    h, w = src.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)

    # ---- what the user interface covers: logo, plates, icons
    ui = np.zeros((h, w), np.float32)
    cx, cy, r = MEDAL
    ui[((xx - cx) ** 2 + (yy - cy) ** 2) < (r + 8) ** 2] = 1
    ui[264:346, 510:868] = 1
    ui[344:726, 484:878] = 1
    for (ix, iy) in ICON_C:
        ui[((xx - ix) ** 2 + (yy - iy) ** 2) < (ICON_R + 4) ** 2] = 1
    clean = smooth_fill(src, ui)
    clean = stars(clean, (500, 8, 860, 262), 150, 3)
    clean = stars(clean, (500, 262, 860, 345), 6, 4)
    clean = np.clip(clean, 0, 255)

    # ---- the pillars, cut from the picture with the user interface taken out
    cm = column_mask(h, w)
    # ---- the clouds are replaced by their own moving layers: here they become a calm haze
    sky = np.clip((yy - 292) / 46.0, 0, 1) * np.clip((708 - yy) / 40.0, 0, 1)
    sky = sky * np.clip((xx - 170) / 40.0, 0, 1) * np.clip((1190 - xx) / 40.0, 0, 1)
    # leave the moonlit part above and the columns alone
    low = cv2.GaussianBlur(clean, (0, 0), 38)
    haze = low * 0.62 + np.array([40, 24, 14], np.float32) * 0.38   # tends towards a dark navy (BGR)
    keep = (1 - sky)[..., None]
    base = clean * keep + haze * (1 - keep)
    base = base * (1 - cm[..., None]) + clean * cm[..., None]       # columns stay as they are
    cv2.imwrite(os.path.join(out, "_base_small.png"), np.clip(base, 0, 255).astype(np.uint8))

    big = sharpen(up(base.astype(np.float32)))
    rng = np.random.default_rng(9)
    big += cv2.GaussianBlur(rng.normal(0, 1, (CH, CW)).astype(np.float32), (0, 0), 0.7)[..., None] * 2.4
    cv2.imwrite(os.path.join(out, "background.png"), np.clip(big, 0, 255).astype(np.uint8))

    cm_big = cv2.resize(column_mask(h, w, 1.0), (CW, CH), interpolation=cv2.INTER_CUBIC)
    cm_big = np.clip(cm_big, 0, 1)
    colbig = sharpen(up(clean))
    colbig += cv2.GaussianBlur(rng.normal(0, 1, (CH, CW)).astype(np.float32), (0, 0), 0.7)[..., None] * 2.0
    colbig = np.clip(colbig, 0, 255)
    sx = CW / MW
    # the two strips are saved at their own size; the menu puts them at these columns of the 1920 x 1080 picture
    for name, x0, x1 in (("pillar_l", 0, 262), ("pillar_r", 1090, 1360)):
        c0, c1 = int(x0 * sx), int(round(x1 * sx))
        a = cm_big[:, c0:c1]
        rgba = np.dstack([np.clip(colbig[:, c0:c1], 0, 255), a * 255]).astype(np.uint8)
        cv2.imwrite(os.path.join(out, name + ".png"), rgba)
        print(name, "columns", c0, "to", c1, "of", CW)

    # ---- plates, lettering
    s2 = 2.0
    def crop2(x0, y0, x1, y1, scale=s2, img=src):
        c = img[int(y0):int(y1), int(x0):int(x1)]
        return cv2.resize(c, (int(round(c.shape[1] * scale)), int(round(c.shape[0] * scale))), interpolation=cv2.INTER_LANCZOS4)

    # an empty plate: the "Exit" plate with its lettering filled in from the dark plate around it
    pw = PLATE_X1 - PLATE_X0
    ex = src[PLATE_Y[5]:PLATE_Y[5] + PLATE_H, PLATE_X0:PLATE_X1].copy()
    lum = cv2.cvtColor(ex, cv2.COLOR_BGR2GRAY).astype(np.float32)
    txt = (cv2.GaussianBlur(lum, (0, 0), 1.0) > 62).astype(np.uint8)
    txt[:6, :] = 0
    txt[-6:, :] = 0
    txt[:, :8] = 0
    txt[:, -8:] = 0
    txt = cv2.dilate(txt, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (9, 9)))
    plate = cv2.inpaint(ex, txt * 255, 5, cv2.INPAINT_TELEA)
    # grain of the plate taken from a bare strip, so the filled part does not look smoother than the rest
    bare = plate[10:-10, 10:60].astype(np.float32)
    plate = plate.astype(np.float32)
    fillm = cv2.GaussianBlur(txt.astype(np.float32), (0, 0), 2.0)[..., None]
    noise = rng.normal(0, 1, plate.shape[:2]).astype(np.float32)
    noise = cv2.GaussianBlur(noise, (0, 0), 0.9)[..., None] * float(bare.std()) * 1.1
    plate = plate * (1 - fillm * 0.0) + noise * fillm
    plate = np.clip(plate, 0, 255)
    cv2.imwrite(os.path.join(out, "plate.png"), np.clip(sharpen(crop2(0, 0, pw, PLATE_H, 2.0, plate.astype(np.float32)), 0.4), 0, 255).astype(np.uint8))

    # lettering: alpha from how much brighter than the plate a pixel is, colour from the picture itself
    for i, name in enumerate(NAMES):
        y0 = PLATE_Y[i]
        c = src[y0 - 2:y0 + PLATE_H + 2, PLATE_X0 - 2:PLATE_X1 + 2]
        big_c = cv2.resize(c, None, fx=2, fy=2, interpolation=cv2.INTER_LANCZOS4).astype(np.float32)
        lum = cv2.cvtColor(np.clip(big_c, 0, 255).astype(np.uint8), cv2.COLOR_BGR2GRAY).astype(np.float32)
        a = np.clip((lum - 48) / 52.0, 0, 1)
        a[:16, :] = 0
        a[-16:, :] = 0
        a[:, :20] = 0
        a[:, -20:] = 0
        # un-premultiply: the colour of a letter edge is the letter colour, not the mix with the plate
        col = np.clip((big_c - (1 - a[..., None]) * np.array([6, 7, 9], np.float32)) / np.maximum(a[..., None], 0.35), 0, 255)
        col = sharpen(col, 0.5, 1.4)
        rgba = np.dstack([np.clip(col, 0, 255), a * 255]).astype(np.uint8)
        cv2.imwrite(os.path.join(out, "ink_%s.png" % name), rgba)

    # ---- round buttons
    k = 3.0
    for (ix, iy), name in zip(ICON_C, ICONS):
        R = ICON_R + 3
        x0, y0 = int(round(ix - R)), int(round(iy - R))
        c = src[y0:y0 + 2 * R, x0:x0 + 2 * R]
        big_c = cv2.resize(c, None, fx=k, fy=k, interpolation=cv2.INTER_LANCZOS4).astype(np.float32)
        n = big_c.shape[0]
        yy2, xx2 = np.mgrid[0:n, 0:n].astype(np.float32)
        d = np.sqrt((xx2 - (ix - x0) * k) ** 2 + (yy2 - (iy - y0) * k) ** 2)
        a = np.clip((ICON_R * k + 1.5 * k - d) / 2.5, 0, 1)
        rgba = np.dstack([sharpen(big_c, 0.5, 1.3), a * 255]).astype(np.uint8)
        cv2.imwrite(os.path.join(out, "icon_%s.png" % name), rgba)

    # ---- logo with its lettering (the Zeus Client lettering comes from the picture itself)
    x0, y0, x1, y1 = LOGO_BOX
    c = src[y0:y1, x0:x1]
    big_c = cv2.resize(c, None, fx=2.5, fy=2.5, interpolation=cv2.INTER_LANCZOS4).astype(np.float32)
    bg_c = clean[y0:y1, x0:x1]
    bg_big = cv2.resize(bg_c, None, fx=2.5, fy=2.5, interpolation=cv2.INTER_CUBIC).astype(np.float32)
    diff = np.abs(big_c - bg_big).max(2)
    n_h, n_w = diff.shape
    yy3, xx3 = np.mgrid[0:n_h, 0:n_w].astype(np.float32)
    medal = (((xx3 - (cx - x0) * 2.5) ** 2 + (yy3 - (cy - y0) * 2.5) ** 2) < ((r + 2.5) * 2.5) ** 2).astype(np.float32)
    medal = cv2.GaussianBlur(medal, (0, 0), 1.6)
    lum = cv2.cvtColor(np.clip(big_c, 0, 255).astype(np.uint8), cv2.COLOR_BGR2GRAY).astype(np.float32)
    lum_bg = cv2.cvtColor(np.clip(bg_big, 0, 255).astype(np.uint8), cv2.COLOR_BGR2GRAY).astype(np.float32)
    text_a = np.clip((lum - lum_bg - 26) / 60.0, 0, 1)
    text_a[:int((cy + r + 6 - y0) * 2.5), :] = 0
    a = np.maximum(medal, text_a)
    col = np.clip((big_c - (1 - a[..., None]) * bg_big) / np.maximum(a[..., None], 0.35), 0, 255)
    rgba = np.dstack([sharpen(col, 0.4, 1.4), a * 255]).astype(np.uint8)
    cv2.imwrite(os.path.join(out, "logo.png"), rgba)
    print("layers written to", out)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
