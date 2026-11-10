"""Draws the website's thunder cursor (img/cursor.png and img/cursor-link.png). Prints the hotspot of each."""
import math, os, sys
from PIL import Image, ImageDraw, ImageFilter

out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/zeus/web/img")
S = 8            # supersampling
N = 32
BOLT = [(14.5, 1.5), (4.5, 13.2), (10.6, 13.2), (8.6, 22.5), (19.8, 9.6), (13.4, 9.6), (17.6, 1.5)]

def poly(scale, rot, cx, cy):
    pts = []
    for x, y in BOLT:
        x -= 12; y -= 12
        a = math.radians(rot)
        rx = x * math.cos(a) - y * math.sin(a)
        ry = x * math.sin(a) + y * math.cos(a)
        pts.append((cx + rx * scale, cy + ry * scale))
    return pts

def make(name, scale, fill, edge, glow):
    big = N * S
    pts = poly(scale * S, 0, big / 2, big / 2)
    g = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    ImageDraw.Draw(g).polygon(pts, fill=glow)
    g = g.filter(ImageFilter.GaussianBlur(S * 1.6))
    b = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(b)
    d.polygon(pts, fill=edge)
    # inner highlight: shrink toward the centroid
    cx = sum(p[0] for p in pts) / len(pts); cy = sum(p[1] for p in pts) / len(pts)
    inner = [(cx + (x - cx) * 0.62, cy + (y - cy) * 0.62) for x, y in pts]
    d.polygon(inner, fill=fill)
    img = Image.alpha_composite(g, b).resize((N, N), Image.LANCZOS)
    img.save(os.path.join(out, name))
    tip = min(pts, key=lambda p: p[1] * 3 + p[0])
    print(name, "hotspot", round(tip[0] / S), round(tip[1] / S))

make("cursor.png", 1.2, (225, 242, 255, 255), (38, 126, 255, 255), (60, 140, 255, 150))
make("cursor-link.png", 1.28, (255, 247, 205, 255), (255, 176, 36, 255), (255, 190, 60, 170))
