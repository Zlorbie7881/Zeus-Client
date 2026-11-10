#!/usr/bin/env python3
"""Builds the 3D knives: meshes and animations for all 20 knives.
    python3 tools/knives/make_knives.py
Writes  src/main/resources/assets/zeus/web/knives3d.json        (detailed, for the website)
        src/main/resources/assets/zeus/knives/knives_lo.json    (light, for the game: fewer segments, no fuller)"""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import models, moves
from geom import export

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
RES = os.path.join(ROOT, "src", "main", "resources", "assets", "zeus")


def make(lod):
    out = []
    for k in models.build(lod):
        parts, hl, tip = k["build"]
        kind = "butterfly" if k["id"] == 0 else "karambit" if k["id"] == 1 else "straight"
        grip_y = {"Butterfly knife": -5.0, "Karambit": 5.5}.get(k["name"], round(hl * 0.5, 2))
        anims = moves.build(k["name"], grip_y, tip, hl)
        ps = []
        for p in parts:
            ps.append({"n": p["n"], "p": [round(c, 2) for c in p["p"]], "par": p["par"], "meshes": [export(m) for m in p["meshes"]]})
        hero = k["id"] < 2
        out.append({"id": k["id"], "name": k["name"], "kind": kind, "grip": [0, grip_y, 0], "tip": round(tip, 2), "parts": ps, "anims": anims if (lod or hero) else {}})
    return {"v": 1, "knives": out}


def main():
    for lod, path in ((1, os.path.join(RES, "web", "knives3d.json")), (0, os.path.join(RES, "knives", "knives_lo.json"))):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        d = make(lod)
        s = json.dumps(d, separators=(",", ":"))
        open(path, "w").write(s)
        tris = [sum(len(m["f"]) // 3 for p in k["parts"] for m in p["meshes"]) for k in d["knives"]]
        print(os.path.relpath(path, ROOT), "%.0f KB" % (len(s) / 1024), "triangles min %d max %d total %d" % (min(tris), max(tris), sum(tris)))


if __name__ == "__main__":
    main()
