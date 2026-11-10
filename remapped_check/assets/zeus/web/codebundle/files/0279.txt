"""Makes the cloud and mist pictures of the main menu. They tile sideways, so the menu can slide them to the left for ever.

  cloud_far.png, cloud_mid.png, cloud_near.png   2560 x 890 dark storm clouds lit from the moon (upper right)
  mist_a.png, mist_b.png                          2560 x 420 low drifting fog

Usage: python3 tools/menu_clouds.py <output folder>
"""
import sys, os
import numpy as np, cv2

W = 2560


# PHASE (degrees) moves every noise field along a second, independent field: 0 gives the original pictures, and a few tens of
# degrees give a related picture (same big shapes, changed details) that the menu fades to and from, so the clouds change shape.
PHASE = 0.0


def noise(h, w, cells_x, beta, seed, stretch=1.7):
    n = _noise(h, w, cells_x, beta, seed, stretch)
    if PHASE:
        n2 = _noise(h, w, cells_x, beta, seed + 7777, stretch)
        a = np.radians(PHASE)
        n = np.cos(a) * n + np.sin(a) * n2
    return n


def _noise(h, w, cells_x, beta, seed, stretch=1.7):
    """Smooth noise that wraps both ways. cells_x: roughly how many features fit across the width; beta: higher = smoother."""
    rng = np.random.default_rng(seed)
    f = np.fft.fft2(rng.normal(0, 1, (h, w)))
    fy = np.fft.fftfreq(h)[:, None] * w          # per picture width, so a feature is as tall as it is wide in pixels, then stretched
    fx = np.fft.fftfreq(w)[None, :] * w
    k = np.sqrt((fx * stretch / cells_x) ** 2 + (fy / cells_x) ** 2) + 1.0
    f = f / (k ** (beta / 2.0))
    f[0, 0] = 0
    n = np.real(np.fft.ifft2(f))
    n = (n - n.mean()) / (n.std() + 1e-9)
    return n.astype(np.float32)


def fbm(h, w, cells, octaves, seed, gain=0.52, beta=2.2, lac=2.0):
    out = np.zeros((h, w), np.float32)
    amp = 1.0
    tot = 0.0
    for o in range(octaves):
        out += amp * noise(h, w, cells * (lac ** o), beta, seed + o * 11)
        tot += amp
        amp *= gain
    return out / tot


def wrap_remap(img, mx, my):
    """cv2.remap with wrap-around on both axes (so the picture still tiles)."""
    h, w = img.shape[:2]
    return cv2.remap(img, np.mod(mx, w).astype(np.float32), np.mod(my, h).astype(np.float32), cv2.INTER_LINEAR, borderMode=cv2.BORDER_WRAP)


def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def shifted(img, dx, dy):
    """The picture moved by (dx, dy) pixels, wrapping round."""
    return np.roll(np.roll(img, int(round(dy)), axis=0), int(round(dx)), axis=1)


def cloud(h, seed, cells, dark, lit, rim, density_lo, density_hi, warp, top_fade, rim_gain, fine, fade_top=0.16, fade_bot=0.90, lift_amount=0.55, stretch=1.7):
    w = W
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    s = w / 2560.0
    wx = fbm(h, w, cells * 0.8, 2, seed + 100, gain=0.35, beta=3.8) * warp * s
    wy = fbm(h, w, cells * 0.8, 2, seed + 200, gain=0.35, beta=3.8) * warp * 0.8 * s
    base = fbm(h, w, cells, 5, seed, gain=0.45, beta=3.8)
    base = wrap_remap(base, xx + wx, yy + wy)
    env = smoothstep(0.0, top_fade, yy / h)                         # clear sky at the top, thick below
    shape = smoothstep(density_lo, density_hi, base + (env - 0.5) * 0.6)
    # cauliflower edges: turbulence eats into the broad shape where it is neither thin nor thick
    turb = fbm(h, w, cells * 3.0, 5, seed + 300, gain=0.55, beta=2.9)
    dens = smoothstep(0.28, 0.72, shape + 0.34 * turb * (0.35 + shape * (1 - shape) * 2.6))
    dens = cv2.GaussianBlur(dens, (0, 0), 1.0 * s + 0.4)
    # light: how much cloud lies between a point and the moon (up and to the right)
    puff = fbm(h, w, cells * 2.6, 4, seed + 500, gain=0.5, beta=3.0)
    inner = dens * (0.50 + 0.50 * np.clip(0.5 + 0.55 * puff, 0, 1))      # thick and thin parts inside the cloud, so it has puffs and hollows
    soft = cv2.GaussianBlur(inner, (0, 0), 3.0 * s + 0.5)
    r1, r2, r3 = 14 * s, 34 * s, 70 * s
    block = 0.45 * shifted(soft, -r1 * 0.75, r1 * 0.66) + 0.35 * shifted(soft, -r2 * 0.75, r2 * 0.66) + 0.25 * shifted(soft, -r3 * 0.75, r3 * 0.66)
    light = np.clip(1.0 - block * 1.0, 0, 1)
    edge = np.clip(dens - shifted(soft, -r1 * 0.5, r1 * 0.45) * 0.9, 0, 1)      # the rim that faces the moon
    texture = fbm(h, w, cells * 8, 4, seed + 400, gain=0.55, beta=2.6)
    t = np.clip(0.10 + 0.85 * light ** 1.25 + texture * fine * 0.6, 0, 1)
    thick = cv2.GaussianBlur(dens, (0, 0), 16.0 * s)
    t = t * (1.0 - 0.25 * thick)
    col = np.zeros((h, w, 3), np.float32)
    for i in range(3):
        col[..., i] = dark[i] + (lit[i] - dark[i]) * t
        col[..., i] += rim[i] * rim_gain * np.clip(edge * 1.7, 0, 1) ** 1.4 * light
    # the underside of the bank is hazier and lighter, as in the picture
    lift = smoothstep(0.58, 1.0, yy / h) * lift_amount
    for i in range(3):
        col[..., i] = col[..., i] * (1 - lift) + lit[i] * 1.05 * lift
    alpha = np.clip(dens * (0.94 + 0.06 * texture), 0, 1)
    if fade_top > 0:
        alpha *= smoothstep(0.0, fade_top, yy / h)
    if fade_bot < 1.0:
        alpha *= 1.0 - smoothstep(fade_bot, 1.0, yy / h)
    return np.clip(col, 0, 255), np.clip(alpha, 0, 1)


def mist(h, seed, cells, tint, strength):
    w = W
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    wx = fbm(h, w, cells * 0.7, 3, seed + 50) * 120.0
    wy = fbm(h, w, cells * 0.7, 3, seed + 60) * 40.0
    # long, low bands: stretch the noise sideways by sampling it at a quarter of the horizontal rate
    n = fbm(h, w, cells, 6, seed, gain=0.55, beta=2.4)
    n2 = wrap_remap(n, (xx * 0.28 + wx), (yy + wy))
    n3 = fbm(h, w, cells * 4, 4, seed + 7, gain=0.5, beta=1.8)
    d = smoothstep(-0.65, 1.0, n2 * 0.95 + n3 * 0.16)
    env = smoothstep(0.0, 0.55, yy / h) * (1 - 0.35 * smoothstep(0.8, 1.0, yy / h))
    a = cv2.GaussianBlur(np.clip(d * env * strength, 0, 1), (0, 0), 1.6)
    lit = np.clip(0.50 + 0.18 * n3 + 0.25 * d + 0.15 * (1 - yy / h), 0, 1)
    col = np.zeros((h, w, 3), np.float32)
    for i in range(3):
        col[..., i] = tint[0][i] + (tint[1][i] - tint[0][i]) * lit
    return col, a


def save(path, col, a):
    # PNG is BGR for cv2; the tints below are given as (R, G, B)
    rgba = np.dstack([col[..., 2], col[..., 1], col[..., 0], a * 255]).clip(0, 255).astype(np.uint8)
    cv2.imwrite(path, rgba)


def main(out):
    os.makedirs(out, exist_ok=True)
    H = 890
    # far: smaller, hazier, bluer; mid: the main bank; near: big and dark with bright moonlit rims
    c, a = cloud(H, 11, 4.6, (12, 20, 42), (40, 56, 90), (100, 126, 172), -0.15, 0.70, 80.0, 0.42, 0.40, 0.16)
    save(os.path.join(out, "cloud_far.png"), c, a * 0.78)
    c, a = cloud(H, 23, 3.3, (7, 12, 29), (34, 50, 82), (116, 144, 190), -0.22, 0.68, 95.0, 0.38, 0.55, 0.18)
    save(os.path.join(out, "cloud_mid.png"), c, a * 0.92)
    c, a = cloud(H, 37, 2.3, (5, 9, 23), (30, 44, 74), (130, 158, 204), -0.28, 0.66, 110.0, 0.34, 0.70, 0.20)
    save(os.path.join(out, "cloud_near.png"), c, a * 0.96)
    c, a = mist(420, 71, 3.0, ((34, 46, 76), (92, 118, 164)), 0.62)
    save(os.path.join(out, "mist_a.png"), c, a)
    c, a = mist(420, 83, 4.0, ((26, 36, 62), (74, 98, 142)), 0.52)
    save(os.path.join(out, "mist_b.png"), c, a)
    print("clouds written to", out)


if __name__ == "__main__":
    main(sys.argv[1])
