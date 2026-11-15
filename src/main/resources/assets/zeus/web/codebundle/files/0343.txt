"""Draws the per-page background artwork (img/bg/*.svg). Deterministic: the same run gives the same files."""
import math, os, random, sys

out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/zeus/web/img/bg")
os.makedirs(out, exist_ok=True)
B = "#3a86ff"; B2 = "#8cc4ff"

def write(name, w, h, body, defs="", slice_=True):
    par = ' preserveAspectRatio="xMidYMid slice"' if slice_ else ""
    s = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d"%s>%s%s</svg>' % (w, h, par, ("<defs>%s</defs>" % defs) if defs else "", body)
    open(os.path.join(out, name), "w").write(s)
    print(name, len(s))

def bolt(rnd, x, y, ang, length, depth, width, op, acc):
    pts = [(x, y)]
    segs = max(4, int(length / 38))
    for _ in range(segs):
        ang += rnd.uniform(-0.55, 0.55)
        x += math.cos(ang) * length / segs; y += math.sin(ang) * length / segs
        pts.append((x, y))
        if depth > 0 and rnd.random() < 0.32:
            bolt(rnd, x, y, ang + rnd.choice([-1, 1]) * rnd.uniform(0.5, 1.0), length * 0.45, depth - 1, width * 0.6, op * 0.8, acc)
    d = "M" + " L".join("%.0f %.0f" % p for p in pts)
    acc.append('<path d="%s" stroke="%s" stroke-width="%.1f" opacity="%.2f"/>' % (d, B2, width, op))

# ---- home: a storm front with thin branched lightning
rnd = random.Random(7)
acc = []
for i in range(5):
    bolt(rnd, rnd.uniform(1000, 1900), rnd.uniform(-40, 40), math.radians(rnd.uniform(95, 130)), rnd.uniform(380, 640), 2, 1.6, rnd.uniform(.16, .30), acc)
for i in range(3):
    bolt(rnd, rnd.uniform(0, 500), rnd.uniform(-40, 40), math.radians(rnd.uniform(60, 85)), rnd.uniform(260, 420), 1, 1.2, rnd.uniform(.08, .16), acc)
write("home.svg", 1920, 1080,
      '<rect width="1920" height="1080" fill="url(#g)"/><ellipse cx="1500" cy="-40" rx="900" ry="300" fill="url(#c)"/><ellipse cx="260" cy="40" rx="520" ry="180" fill="url(#c)" opacity=".6"/>'
      '<g fill="none" stroke-linecap="round" stroke-linejoin="round">%s</g>' % "".join(acc),
      '<linearGradient id="g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#0a1a3d" stop-opacity=".55"/><stop offset=".6" stop-color="#050c1e" stop-opacity="0"/></linearGradient>'
      '<radialGradient id="c"><stop offset="0" stop-color="#2f7dff" stop-opacity=".30"/><stop offset="1" stop-color="#2f7dff" stop-opacity="0"/></radialGradient>')

# ---- download: data falling into a tray
rnd = random.Random(11)
acc = []
for i in range(46):
    x = rnd.uniform(20, 1900); y = rnd.uniform(0, 760); l = rnd.uniform(60, 320)
    acc.append('<rect x="%.0f" y="%.0f" width="2" height="%.0f" rx="1" fill="url(#l)" opacity="%.2f"/>' % (x, y, l, rnd.uniform(.25, .8)))
for i in range(14):
    x = rnd.uniform(60, 1860); y = rnd.uniform(820, 1040)
    acc.append('<path d="M%.0f %.0f l10 12 l10 -12" fill="none" stroke="%s" stroke-width="2" opacity="%.2f"/>' % (x, y, B2, rnd.uniform(.10, .30)))
write("download.svg", 1920, 1080, '<rect y="760" width="1920" height="320" fill="url(#t)"/>%s' % "".join(acc),
      '<linearGradient id="l" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="%s" stop-opacity="0"/><stop offset="1" stop-color="%s" stop-opacity=".9"/></linearGradient>'
      '<linearGradient id="t" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2f7dff" stop-opacity="0"/><stop offset="1" stop-color="#2f7dff" stop-opacity=".16"/></linearGradient>' % (B, B2))

# ---- features: hexagon grid (tile)
def hexpath(cx, cy, r):
    return "M" + " L".join("%.1f %.1f" % (cx + r * math.cos(math.radians(60 * k + 30)), cy + r * math.sin(math.radians(60 * k + 30))) for k in range(6)) + "Z"
r = 36; w = r * math.sqrt(3); hh = r * 3
body = '<g fill="none" stroke="%s" stroke-width="1.2" opacity=".22">' % B
for (cx, cy) in [(w / 2, r), (w, r + r * 1.5), (0, r + r * 1.5), (w / 2, r + r * 3)]:
    body += '<path d="%s"/>' % hexpath(cx, cy, r - 2)
body += '</g><path d="%s" fill="%s" opacity=".05"/>' % (hexpath(w, r + r * 1.5, r - 2), B)
write("features.svg", int(round(w)), int(round(hh)), body, slice_=False)

# ---- tutorial: a path with numbered stops
rnd = random.Random(5)
pts = [(160 + i * 300, 180 + (i % 2) * 520 + rnd.uniform(-60, 60)) for i in range(6)]
d = "M%.0f %.0f" % pts[0]
for i in range(1, len(pts)):
    px, py = pts[i - 1]; x, y = pts[i]
    d += " C%.0f %.0f %.0f %.0f %.0f %.0f" % (px + 160, py, x - 160, y, x, y)
dots = "".join('<circle cx="%.0f" cy="%.0f" r="14" fill="#06101f" stroke="%s" stroke-width="2"/><circle cx="%.0f" cy="%.0f" r="4" fill="%s"/>' % (x, y, B2, x, y, B2) for x, y in pts)
write("tutorial.svg", 1920, 1080, '<path d="%s" fill="none" stroke="%s" stroke-width="2.2" stroke-dasharray="3 12" stroke-linecap="round" opacity=".42"/><g opacity=".5">%s</g>' % (d, B, dots))

# ---- information: timeline spine with dots and stars
rnd = random.Random(9)
stars = "".join('<circle cx="%.0f" cy="%.0f" r="%.1f" fill="%s" opacity="%.2f"/>' % (rnd.uniform(0, 1920), rnd.uniform(0, 1080), rnd.uniform(.8, 2.0), B2, rnd.uniform(.15, .55)) for _ in range(90))
spine = '<path d="M380 0 V1080" stroke="url(#s)" stroke-width="2"/>' + "".join('<circle cx="380" cy="%d" r="6" fill="#06101f" stroke="%s" stroke-width="2" opacity=".6"/>' % (y, B2) for y in range(80, 1080, 170))
write("information.svg", 1920, 1080, stars + spine, '<linearGradient id="s" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="%s" stop-opacity="0"/><stop offset=".5" stop-color="%s" stop-opacity=".45"/><stop offset="1" stop-color="%s" stop-opacity="0"/></linearGradient>' % (B, B, B))

# ---- help: radar rings
rings = "".join('<circle cx="1500" cy="520" r="%d" fill="none" stroke="%s" stroke-width="1.4" opacity="%.2f"/>' % (r_, B, .30 - i * .04) for i, r_ in enumerate(range(120, 780, 110)))
ticks = "".join('<line x1="%.0f" y1="%.0f" x2="%.0f" y2="%.0f" stroke="%s" stroke-width="1.4" opacity=".2"/>' % (1500 + 90 * math.cos(math.radians(a)), 520 + 90 * math.sin(math.radians(a)), 1500 + 780 * math.cos(math.radians(a)), 520 + 780 * math.sin(math.radians(a)), B) for a in range(0, 360, 30))
write("help.svg", 1920, 1080, rings + ticks + '<circle cx="1500" cy="520" r="6" fill="%s" opacity=".6"/>' % B2)

# ---- source: faint lines of code
rnd = random.Random(3)
acc = []
y = 36
while y < 1060:
    x = 120 + rnd.choice([0, 0, 28, 56, 84]) * 1.0
    n = rnd.randint(1, 4)
    for _ in range(n):
        wd = rnd.choice([40, 64, 96, 140, 200, 260]); acc.append('<rect x="%.0f" y="%d" width="%d" height="8" rx="4" opacity="%.2f"/>' % (x, y, wd, rnd.uniform(.05, .17))); x += wd + 14
    y += 30
write("source.svg", 1920, 1080, '<g fill="%s">%s</g>' % (B2, "".join(acc)))

# ---- credits: constellation
rnd = random.Random(21)
P = [(rnd.uniform(80, 1840), rnd.uniform(60, 1020)) for _ in range(36)]
lines = []
for i, (x, y) in enumerate(P):
    near = sorted(P, key=lambda q: (q[0] - x) ** 2 + (q[1] - y) ** 2)[1:3]
    for q in near:
        if (q[0] - x) ** 2 + (q[1] - y) ** 2 < 330 ** 2:
            lines.append('<line x1="%.0f" y1="%.0f" x2="%.0f" y2="%.0f"/>' % (x, y, q[0], q[1]))
write("credits.svg", 1920, 1080, '<g stroke="%s" stroke-width="1.1" opacity=".22">%s</g>' % (B, "".join(lines)) + "".join('<circle cx="%.0f" cy="%.0f" r="%.1f" fill="%s" opacity=".6"/>' % (x, y, rnd.uniform(2, 4.4), B2) for x, y in P))

# ---- config: circuit traces
rnd = random.Random(14)
acc = []
for _ in range(34):
    x = rnd.randrange(0, 1920, 40); y = rnd.randrange(0, 1080, 40); d = "M%d %d" % (x, y)
    for _ in range(rnd.randint(2, 4)):
        if rnd.random() < .5: x += rnd.choice([-1, 1]) * rnd.randrange(80, 320, 40)
        else: y += rnd.choice([-1, 1]) * rnd.randrange(80, 240, 40)
        d += " L%d %d" % (x, y)
    acc.append('<path d="%s"/><circle cx="%d" cy="%d" r="5" fill="#06101f"/>' % (d, x, y))
write("config.svg", 1920, 1080, '<g fill="none" stroke="%s" stroke-width="1.6" opacity=".2" stroke-linejoin="round">%s</g>' % (B, "".join(acc)))

# ---- everything else: a fine grid (tile)
write("grid.svg", 56, 56, '<path d="M56 0H0V56" fill="none" stroke="%s" stroke-width="1" opacity=".16"/>' % B, slice_=False)
