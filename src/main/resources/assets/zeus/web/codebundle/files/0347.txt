"""Makes the frames of the news scroll unrolling (scroll_unroll.png) for the Zeus menu.

The scroll is a real object: a roll of parchment on a rod with a bronze knob at each end. While it opens, the paper leaves the roll,
so the roll gets thinner, the rod it is wound on shows more and more, the bands round it fall away, the roll turns a little towards
the viewer and the paper hangs from it with the shadow of the roll on it. Every frame is ray cast like the rest of the scroll
(tools/make_news_scroll.py), so the shading is real depth. The first frame matches the closed roll, the last one the top rod of the
open scroll, so the game can change from one picture to the next without a seam.

scroll_unroll.png   580 x (104 * FRAMES): the frames one under the other, each the same size as scroll_head.png

Usage: python3 tools/make_scroll_unroll.py <output folder>
"""
import math
import os
import sys

import numpy as np
from PIL import Image

OUT = sys.argv[1]
sys.argv = [sys.argv[0], "", "", ""]
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_news_scroll as S  # noqa: E402

FRAMES = 16
H = S.HEAD_H
R0, R1 = 31.0, S.ROD_R
CY0, CY1 = 36.0, float(S.ROD_Y_HEAD)


def lerp(a, b, u):
    return a + (b - a) * u


def frame(k):
    u = k / (FRAMES - 1.0)
    # the paper leaves the roll: the area of the roll goes down evenly
    r = math.sqrt(lerp(R0 * R0, R1 * R1, u))
    cy = lerp(CY0, CY1, u)
    PW = S.PW
    half = lerp(PW / 2 - 18, PW / 2, u)
    rod_half = lerp(PW / 2 + 4, PW / 2 + 40, u)
    parts = [S.Part(-half, half, r, r, "paper"), S.Part(-rod_half, rod_half, lerp(5.0, 4.5, u), lerp(5.0, 4.5, u), "bronze")]
    knob_x = lerp(PW / 2 + 4, PW / 2, u)
    neck = lerp(6, 7, u)
    bulb = lerp(22, 16, u)
    rb = lerp(R0 * 0.5 + 2, R1 + 3, u) * (r / lerp(R0, R1, u)) if False else lerp(R0 * 0.5 + 2, R1 + 3, u)
    parts += S.knob_profile(knob_x, +1, r_neck=lerp(5.0, 4.5, u), r_bulb=rb, neck_len=neck, bulb_len=bulb)
    parts += S.knob_profile(-knob_x, -1, r_neck=lerp(5.0, 4.5, u), r_bulb=rb, neck_len=neck, bulb_len=bulb)
    if u < 0.24:
        # the bands loosen and slide to the ends as the roll opens; the seal goes with the first frames
        f = u / 0.24
        slide = f * 120
        fade_r = 1.8 * (1 - f)
        if fade_r > 0.2:
            parts += [S.Part(-half + 72 - slide, -half + 100 - slide, r + fade_r, r + fade_r, "cloth"),
                      S.Part(half - 100 + slide, half - 72 + slide, r + fade_r, r + fade_r, "cloth")]
        if u < 0.12:
            parts += [S.Part(-9, 9, r + 3.2 * (1 - u / 0.12), r + 3.2 * (1 - u / 0.12), "bronze")]
    parts = S.fix_order(parts)

    # the paper that hangs from the roll, with the roll's shadow on it, melting into the body below
    rgb, a = S.parchment_sheet(H, seed=4)
    ys = np.arange(H, dtype=np.float32)[:, None]
    a = a * np.clip((ys - (cy + r * 0.2)) / 3.0, 0, 1)
    span = 26.0 + (r - R1) * 0.9
    sh = np.clip(1 - (ys - cy) / span, 0, 1) ** 1.5
    rgb = rgb * (1 - 0.55 * sh[..., None])
    a = a * np.where(ys > H - S.FADE, np.clip((H - 1 - ys) / S.FADE, 0, 1), 1.0)

    pitch = lerp(-0.38, -0.20, u)
    rr, ra = S.render(parts, S.RW, H, yaw=0.0, pitch=pitch, cx=S.RW / 2, cy=cy, scale=1.0, paper_len=PW, seed=5 if k == 0 else 2)
    shad = S.drop_shadow(ra, int(round(lerp(10, 5, u))), lerp(6.0, 4.0, u), lerp(0.62, 0.55, u))
    rgb, a = S.over(rgb, a, np.zeros_like(rgb), shad * (1 - ra))
    rgb, a = S.over(rgb, a, rr, ra)
    return S.finish(rgb, a)


def main():
    os.makedirs(OUT, exist_ok=True)
    sheet = Image.new("RGBA", (S.RW, H * FRAMES))
    for k in range(FRAMES):
        sheet.paste(frame(k), (0, H * k))
        print("frame", k, flush=True)
    sheet.save(os.path.join(OUT, "scroll_unroll.png"), optimize=True)
    print("sheet", sheet.size, "frames", FRAMES, "frame height", H)


if __name__ == "__main__":
    main()
