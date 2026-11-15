"""Makes the pictures for the storm on the Zeus main menu.

  storm_a.png, storm_b.png     2560 x 700 heavy dark rain clouds that tile sideways, thick at the top, ragged underside
  snow_far.png, snow_mid.png, snow_near.png   768 x 768 soft snowflakes that tile both ways (December on the menu)
  rain_far.png, rain_mid.png, rain_near.png   768 x 768 streaks of rain that tile both ways, all slanting the same way
                                (down and to the left, SLANT below; the menu moves them along that direction)

Usage: python3 tools/menu_storm.py <output folder>
"""
import sys, os, math
import numpy as np, cv2

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from menu_clouds import fbm, smoothstep, wrap_remap, shifted, save

W = 2560
SLANT_DEG = 11.0          # how far the rain leans from straight down; the menu code uses the same number


def storm(h, seed, cells, dark, lit, density_lo, density_hi, warp, thick_to):
    w = W
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    s = w / 2560.0
    wx = fbm(h, w, cells * 0.8, 2, seed + 100, gain=0.35, beta=3.8) * warp * s
    wy = fbm(h, w, cells * 0.8, 2, seed + 200, gain=0.35, beta=3.8) * warp * 0.8 * s
    base = fbm(h, w, cells, 5, seed, gain=0.45, beta=3.8)
    base = wrap_remap(base, xx + wx, yy + wy)
    # thick at the top, breaking up towards the bottom
    env = 1.0 - smoothstep(0.0, thick_to, yy / h)
    shape = smoothstep(density_lo, density_hi, base + (env - 0.40) * 2.2)
    turb = fbm(h, w, cells * 3.0, 5, seed + 300, gain=0.55, beta=2.9)
    dens = smoothstep(0.26, 0.70, shape + 0.36 * turb * (0.35 + shape * (1 - shape) * 2.6))
    dens = cv2.GaussianBlur(dens, (0, 0), 1.2 * s + 0.4)
    # hollows and puffs inside the bank, so it is not flat grey
    puff = fbm(h, w, cells * 2.4, 4, seed + 500, gain=0.5, beta=3.0)
    inner = dens * (0.45 + 0.55 * np.clip(0.5 + 0.6 * puff, 0, 1))
    soft = cv2.GaussianBlur(inner, (0, 0), 3.0 * s + 0.5)
    # a little light on the lower edges that face away from the top, so the bank has an underside
    under = np.clip(dens - shifted(soft, 0, -16 * s) * 0.9, 0, 1)
    texture = fbm(h, w, cells * 8, 4, seed + 400, gain=0.55, beta=2.6)
    t = np.clip(0.20 + 0.55 * (1.0 - cv2.GaussianBlur(dens, (0, 0), 18.0 * s)) + 0.22 * puff + texture * 0.16, 0, 1)
    col = np.zeros((h, w, 3), np.float32)
    for i in range(3):
        col[..., i] = dark[i] + (lit[i] - dark[i]) * t
        col[..., i] += lit[i] * 0.35 * np.clip(under * 1.6, 0, 1) ** 1.3
    alpha = np.clip(dens * (0.96 + 0.04 * texture), 0, 1)
    alpha *= 1.0 - smoothstep(0.86, 1.0, yy / h)
    return np.clip(col, 0, 255), np.clip(alpha, 0, 1)


def rain(size, seed, count, length, thick, alpha, jitter):
    """Rain streaks on a tile that wraps both ways. Every streak points the same way."""
    rng = np.random.default_rng(seed)
    canvas = np.zeros((size, size), np.uint8)
    ang = math.radians(SLANT_DEG)
    dx, dy = -math.sin(ang), math.cos(ang)       # down and to the left
    for _ in range(count):
        x = rng.uniform(0, size)
        y = rng.uniform(0, size)
        ln = length * rng.uniform(0.6, 1.25)
        a = alpha * rng.uniform(0.45, 1.0)
        th = max(1, int(round(thick * rng.uniform(0.8, 1.2))))
        n = 5
        for ox in (-size, 0, size):
            for oy in (-size, 0, size):
                if x + ox < -ln or x + ox > size + ln or y + oy < -ln or y + oy > size + ln:
                    continue
                for k in range(n):
                    f0, f1 = k / n, (k + 1) / n
                    fall = math.sin(math.pi * (f0 + f1) / 2) ** 0.7       # bright in the middle, thin at the ends
                    q0 = (x + ox + dx * ln * (f0 - 0.5), y + oy + dy * ln * (f0 - 0.5))
                    q1 = (x + ox + dx * ln * (f1 - 0.5), y + oy + dy * ln * (f1 - 0.5))
                    cv2.line(canvas, (int(round(q0[0] * 16)), int(round(q0[1] * 16))), (int(round(q1[0] * 16)), int(round(q1[1] * 16))),
                             int(255 * a * fall), th, cv2.LINE_AA, 4)
    tile = canvas.astype(np.float32) / 255.0
    # blur wrapping round the edges so the tile stays seamless
    big = np.tile(tile, (3, 3))
    big = cv2.GaussianBlur(big, (0, 0), jitter)
    tile = big[size:2 * size, size:2 * size]
    return tile


def rain_tile(path, size, seed, count, length, thick, alpha, jitter, tint):
    a = rain(size, seed, count, length, thick, alpha, jitter)
    col = np.zeros((size, size, 3), np.float32)
    for i in range(3):
        col[..., i] = tint[i]
    save(path, col, np.clip(a, 0, 1))


def snow_tile(path, size, seed, count, rmin, rmax, alpha, soft, star=0.0):
    """Snowflakes: soft round flakes (a few of them with six little arms) on a tile that wraps both ways."""
    rng = np.random.default_rng(seed)
    layer = np.zeros((size, size), np.float32)
    for _ in range(count):
        x, y = rng.uniform(0, size), rng.uniform(0, size)
        r = rng.uniform(rmin, rmax)
        a = alpha * rng.uniform(0.5, 1.0)
        arms = rng.random() < star
        for ox in (-size, 0, size):
            for oy in (-size, 0, size):
                cx, cy = x + ox, y + oy
                if cx < -r * 3 or cx > size + r * 3 or cy < -r * 3 or cy > size + r * 3:
                    continue
                c = np.zeros((size, size), np.float32)
                cv2.circle(c, (int(round(cx * 16)), int(round(cy * 16))), int(round(r * 16)), 1.0, -1, cv2.LINE_AA, 4)
                if arms:
                    ang0 = rng.uniform(0, math.pi)
                    for k in range(3):
                        ang = ang0 + k * math.pi / 3
                        p0 = (cx - math.cos(ang) * r * 2.4, cy - math.sin(ang) * r * 2.4)
                        p1 = (cx + math.cos(ang) * r * 2.4, cy + math.sin(ang) * r * 2.4)
                        cv2.line(c, (int(p0[0] * 16), int(p0[1] * 16)), (int(p1[0] * 16), int(p1[1] * 16)), 0.7, 1, cv2.LINE_AA, 4)
                layer = np.maximum(layer, c * a)
    big = np.tile(layer, (3, 3))
    big = cv2.GaussianBlur(big, (0, 0), soft)
    tile = np.clip(big[size:2 * size, size:2 * size] * (1.0 + 0.35 * soft / 3.0), 0, 1)
    col = np.zeros((size, size, 3), np.float32)
    for i, v in enumerate((236, 244, 252)):
        col[..., i] = v
    save(path, col, tile)


def main(out):
    os.makedirs(out, exist_ok=True)
    c, a = storm(700, 61, 3.4, (10, 15, 30), (70, 86, 120), -0.55, 0.35, 95.0, 0.95)
    save(os.path.join(out, "storm_a.png"), c, a * 0.96)
    c, a = storm(700, 77, 2.6, (7, 10, 22), (60, 74, 106), -0.60, 0.30, 110.0, 0.85)
    save(os.path.join(out, "storm_b.png"), c, a * 0.92)
    # BGR note: save() takes (R, G, B) colours
    rain_tile(os.path.join(out, "rain_far.png"), 768, 5, 420, 26, 1, 0.30, 0.45, (170, 196, 240))
    rain_tile(os.path.join(out, "rain_mid.png"), 768, 9, 170, 54, 2, 0.40, 0.55, (186, 208, 246))
    rain_tile(os.path.join(out, "rain_near.png"), 768, 13, 42, 120, 3, 0.46, 0.8, (206, 224, 252))
    snow_tile(os.path.join(out, "snow_far.png"), 768, 21, 420, 1.3, 2.1, 0.8, 0.7)
    snow_tile(os.path.join(out, "snow_mid.png"), 768, 22, 150, 2.6, 4.2, 0.85, 1.0, 0.18)
    snow_tile(os.path.join(out, "snow_near.png"), 768, 23, 38, 6.0, 11.0, 0.66, 2.8, 0.25)
    print("storm pictures written to", out)


if __name__ == "__main__":
    main(sys.argv[1])
