"""Makes the animation strips of the round buttons of the Zeus menu (shown while the pointer is on a button).

  icon_anim_language.png        24 frames, a wire globe turning (the meridians slide round it)
  icon_anim_accessibility.png   24 frames, the figure raises its right arm and lowers it again (one wave)
  icon_anim_credits.png         12 frames, the scroll unrolls (frame 0 is the closed scroll, frame 11 the opened one)

Every strip is one picture with the frames side by side, 222 x 222 each, made from the button pictures themselves
(icon_*.png in the same folder), so the metal looks the same as the still buttons.

Usage: python3 tools/menu_icon_anim.py <folder with icon_*.png> <output folder>
"""
import sys, os, math
import numpy as np, cv2

S = 222
SS = 4  # drawing is done at 4x and shrunk, for smooth lines


def load(folder, name):
    im = cv2.imread(os.path.join(folder, "icon_%s.png" % name), cv2.IMREAD_UNCHANGED)
    return im.astype(np.float32)


def light_mask(im, thr=104, rmax=84):
    g = cv2.cvtColor(im[..., :3].astype(np.uint8), cv2.COLOR_BGR2GRAY)
    yy, xx = np.mgrid[0:S, 0:S]
    r = np.hypot(xx - 111, yy - 111)
    return ((g > thr) & (r < rmax)).astype(np.uint8)


def clean(im, mask, grow=4):
    """The button with the symbol taken out: the dark woven disc is filled in again, with a little grain so it does not look smooth."""
    m = cv2.dilate(mask, np.ones((grow * 2 + 1, grow * 2 + 1), np.uint8))
    bgr = im[..., :3].astype(np.uint8)
    fill = cv2.inpaint(bgr, m * 255, 6, cv2.INPAINT_TELEA).astype(np.float32)
    rng = np.random.default_rng(5)
    grain = cv2.GaussianBlur(rng.normal(0, 5.5, (S, S)).astype(np.float32), (0, 0), 0.7)
    m3 = cv2.GaussianBlur(m.astype(np.float32), (0, 0), 1.2)[..., None]
    fill = fill + grain[..., None] * m3
    out = im.copy()
    out[..., :3] = im[..., :3] * (1 - m3) + fill * m3
    return out


def over(dst, src_bgr, alpha):
    """dst (S,S,4) float, src_bgr (S,S,3), alpha (S,S) 0..1: lays src on dst, keeping dst's own alpha."""
    a = alpha[..., None]
    dst[..., :3] = dst[..., :3] * (1 - a) + src_bgr * a


def save_strip(path, frames):
    strip = np.concatenate([np.clip(f, 0, 255).astype(np.uint8) for f in frames], axis=1)
    cv2.imwrite(path, strip)


# ------------------------------------------------------------------ the globe
def sphere_pts(lon_deg, lat_deg, tilt):
    lon = math.radians(lon_deg)
    lat = np.radians(lat_deg)
    x0 = np.cos(lat) * math.sin(lon)
    y0 = np.sin(lat)
    z0 = np.cos(lat) * math.cos(lon)
    y = y0 * math.cos(tilt) - z0 * math.sin(tilt)
    z = y0 * math.sin(tilt) + z0 * math.cos(tilt)
    return x0, y, z


def runs(x, y, vis):
    out, cur = [], []
    for xi, yi, v in zip(x, y, vis):
        if v:
            cur.append((xi, yi))
        elif cur:
            out.append(cur)
            cur = []
    if cur:
        out.append(cur)
    return out


def globe_frame(base, phase_deg):
    cx, cy, R = 113.0, 115.5, 59.0
    tilt = math.radians(17)
    layer = np.zeros((S * SS, S * SS), np.float32)
    thick = int(round(3.4 * SS))

    def poly(pts, t=thick):
        if len(pts) < 2:
            return
        arr = np.array([(int(px * SS * 16), int(py * SS * 16)) for px, py in pts], np.int32)
        cv2.polylines(layer, [arr], False, 1.0, t, cv2.LINE_AA, 4)

    lat = np.arange(-90, 90.1, 2.0)
    for k in range(12):
        lon = k * 30.0 + phase_deg
        x0, y, z = sphere_pts(lon, lat, tilt)
        for run in runs(cx + R * x0, cy - R * y, z > 0.0):
            poly(run, int(round(thick * (0.8 + 0.2 * 1.0))))
    lon = np.arange(0, 360.1, 2.0)
    for la in (-60, -30, 0, 30, 60):
        xs, ys, zs = [], [], []
        for lo in lon:
            x0, y, z = sphere_pts(lo + phase_deg, np.array([la], np.float64), tilt)
            xs.append(cx + R * x0[0]); ys.append(cy - R * y[0]); zs.append(z[0])
        for run in runs(xs, ys, np.array(zs) > 0.0):
            poly(run)
    cv2.circle(layer, (int(cx * SS), int(cy * SS)), int(R * SS), 1.0, int(round(thick * 1.25)), cv2.LINE_AA)
    wire = cv2.resize(layer, (S, S), interpolation=cv2.INTER_AREA)
    # metal: a pale body with a dark edge below and a bright edge above (the buttons are embossed)
    shadow = np.roll(np.roll(wire, 2, axis=0), 2, axis=1)
    shadow = cv2.GaussianBlur(shadow, (0, 0), 1.0)
    spec = np.roll(wire, -1, axis=0)
    out = base.copy()
    over(out, np.full((S, S, 3), (14, 14, 16), np.float32), np.clip(shadow * 0.75, 0, 1))
    yy = np.linspace(0, 1, S)[:, None, None]
    body = np.array([186, 196, 202], np.float32) * (1.08 - 0.30 * yy) * np.ones((S, S, 3), np.float32)   # BGR, warm silver, lighter on top
    over(out, body, np.clip(wire * 0.96, 0, 1))
    hi = np.clip(wire - np.roll(wire, 1, axis=0), 0, 1)
    over(out, np.full((S, S, 3), (232, 236, 240), np.float32), hi * 0.45)
    return out


# ------------------------------------------------------------------ the figure
def accessibility_frames(im, n):
    mask = light_mask(im)
    base = clean(im, mask, grow=8)
    # the right arm: the part of the bar to the right of the shoulder (it reaches a little into the body, which is laid over it again)
    px, py = 124.0, 92.0
    region = np.zeros((S, S), np.uint8)
    region[76:112, int(px) - 10:166] = 1
    arm = (mask & region).astype(np.uint8)
    arm = cv2.dilate(arm, np.ones((3, 3), np.uint8))
    alpha = cv2.GaussianBlur(arm.astype(np.float32), (0, 0), 0.8)
    # everything of the symbol that stays where it is: all of it except the arm part right of the shoulder
    rest_m = mask.copy()
    rest_m[76:112, int(px):] = 0
    rest = cv2.GaussianBlur(cv2.dilate(rest_m, np.ones((3, 3), np.uint8)).astype(np.float32), (0, 0), 0.6)
    frames = []
    for k in range(n):
        s = 0.5 - 0.5 * math.cos(2 * math.pi * k / n)          # 0 .. 1 .. 0
        ang = -52.0 * s + 9.0 * math.sin(4 * math.pi * k / n) * s   # up, with a little wobble of the hand
        M = cv2.getRotationMatrix2D((px, py), -ang, 1.0)
        a = cv2.warpAffine(alpha, M, (S, S), flags=cv2.INTER_CUBIC)
        c = cv2.warpAffine(im[..., :3] * alpha[..., None], M, (S, S), flags=cv2.INTER_CUBIC)
        c = c / np.maximum(a[..., None], 1e-3)
        f = base.copy()
        over(f, c, np.clip(a, 0, 1))
        over(f, im[..., :3], np.clip(rest, 0, 1))
        frames.append(f)
    return frames


# ------------------------------------------------------------------ the scroll
def scroll_frames(im, n):
    mask = light_mask(im, thr=96, rmax=88)
    closed = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, np.ones((9, 9), np.uint8))
    # fill the holes (the dark text lines inside the paper)
    cnts, _ = cv2.findContours(closed, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    obj = np.zeros((S, S), np.uint8)
    cv2.drawContours(obj, cnts, -1, 1, -1)
    base = clean(im, obj, grow=5)
    ys = np.nonzero(obj.any(axis=1))[0]
    top, bottom = ys.min(), ys.max() + 1
    roll = max(18, int(round((bottom - top) * 0.22)))
    t_end = top + roll                       # the top roll
    b_start = bottom - roll                  # the bottom roll
    alpha_full = cv2.GaussianBlur(obj.astype(np.float32), (0, 0), 0.7)
    frames = []
    for k in range(n):
        p = k / (n - 1)
        e = p * p * (3 - 2 * p)
        d = 15.0 * e
        shift_up = d / 2.0
        f = base.copy()
        canvas_c = np.zeros((S, S, 3), np.float32)
        canvas_a = np.zeros((S, S), np.float32)

        def put(rows0, rows1, dy, stretch_to=None):
            piece_c = (im[rows0:rows1, :, :3] * alpha_full[rows0:rows1, :, None])
            piece_a = alpha_full[rows0:rows1, :].copy()
            h = rows1 - rows0
            if stretch_to is not None and stretch_to != h:
                piece_c = cv2.resize(piece_c, (S, stretch_to), interpolation=cv2.INTER_LINEAR)
                piece_a = cv2.resize(piece_a, (S, stretch_to), interpolation=cv2.INTER_LINEAR)
                h = stretch_to
            y0 = int(round(rows0 + dy)) if stretch_to is None else int(round(dy))
            y0c, y1c = max(0, y0), min(S, y0 + h)
            if y1c <= y0c:
                return
            pc = piece_c[y0c - y0:y1c - y0]
            pa = piece_a[y0c - y0:y1c - y0]
            # premultiplied "over"
            canvas_c[y0c:y1c] = pc + canvas_c[y0c:y1c] * (1 - pa[..., None])
            canvas_a[y0c:y1c] = pa + canvas_a[y0c:y1c] * (1 - pa)

        mid_h = b_start - t_end
        new_mid = int(round(mid_h + d))
        put(top, t_end, -shift_up)                                   # top roll moves up a little
        put(t_end, b_start, t_end - shift_up, stretch_to=new_mid) if False else None
        # the paper, stretched
        piece_c = im[t_end:b_start, :, :3] * alpha_full[t_end:b_start, :, None]
        piece_a = alpha_full[t_end:b_start, :]
        pc = cv2.resize(piece_c, (S, new_mid), interpolation=cv2.INTER_LINEAR)
        pa = cv2.resize(piece_a, (S, new_mid), interpolation=cv2.INTER_LINEAR)
        y0 = int(round(t_end - shift_up))
        canvas_c[y0:y0 + new_mid] = pc + canvas_c[y0:y0 + new_mid] * (1 - pa[..., None])
        canvas_a[y0:y0 + new_mid] = pa + canvas_a[y0:y0 + new_mid] * (1 - pa)
        # the bottom roll moves down
        put(b_start, bottom, d - shift_up)
        a = np.clip(canvas_a, 0, 1)
        c = canvas_c / np.maximum(a[..., None], 1e-3)
        over(f, c, a)
        # a soft shadow under the roll that has dropped
        frames.append(f)
    return frames


def main(folder, out):
    os.makedirs(out, exist_ok=True)
    lang = load(folder, "language")
    base = clean(lang, light_mask(lang, thr=100, rmax=80), grow=5)
    frames = [globe_frame(base, 30.0 * k / 24.0) for k in range(24)]
    save_strip(os.path.join(out, "icon_anim_language.png"), frames)
    acc = load(folder, "accessibility")
    save_strip(os.path.join(out, "icon_anim_accessibility.png"), accessibility_frames(acc, 24))
    cr = load(folder, "credits")
    save_strip(os.path.join(out, "icon_anim_credits.png"), scroll_frames(cr, 12))
    print("strips written to", out)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
