"""Adds "rank" (position in Usage.POPULAR, 0 = most used) and "aliases" to features.json, and applies renames.
Run after regenerating features.json:  python3 tools/site/features_extra.py <web folder>
(The in-game copy gets the same fields from FeatureCatalog.json(); this keeps the static website copy in step.)"""
import json, os, re, sys

web = sys.argv[1]
root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
src = open(os.path.join(root, "src/main/java/com/zlorbie788/zeus/core/Usage.java"), encoding="utf-8").read()
block = re.search(r"POPULAR\s*=\s*\{(.*?)\};", src, re.S).group(1)
popular = re.findall(r'"([a-z0-9_]+)"', block)
rank = {m: i for i, m in enumerate(popular)}

# id -> (display name, description, aliases). Keep in step with the module constructors / alias() calls.
CHANGES = {
    "jesus": ("Water Walk", "Walk on top of water as if it were ground. Also known as Jesus.", ["Jesus", "Jesus Walk", "Waterwalk", "Liquid Walk"]),
}

# modules that do real work every tick even though their code only has a tick method
COST_MIN = {"swordbot": 3, "crystalbot": 3, "killaura": 3, "multiaura": 3, "crystalaura": 3, "anchoraura": 3, "aimassist": 3, "triggerbot": 3,
            "scaffold": 3, "nuker": 3, "treebot": 3, "tunnelminer": 3, "crystaloptimizer": 3, "surround": 3, "autotrap": 3, "autoweb": 3, "holefiller": 3,
            "perfmonitor": 3, "atmosphere": 4}

path = os.path.join(web, "features.json")
data = json.load(open(path, encoding="utf-8"))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from config_data import HEAVY  # the scanners and ESPs: what costs the most frame time
for m in data["modules"]:
    m["rank"] = rank.get(m["id"], len(popular) + 1)
    m["weight"] = "heavy" if (m["id"] in HEAVY or m.get("weight") == "heavy") else "light"
    # the estimate from the code (FeatureCatalog) plus what is known by hand: the outline and scanner modules are never cheap,
    # and the fighting modules do real work (target search, line of sight) every tick
    c = m.get("cost", 2)
    if m["id"] in HEAVY:
        c = max(c, 4)
    c = max(c, COST_MIN.get(m["id"], 1))
    m["cost"] = min(5, c)
    m["weight"] = "heavy" if m["cost"] >= 4 else "light"
    if m["id"] in CHANGES:
        n, d, a = CHANGES[m["id"]]
        m["name"], m["description"], m["aliases"] = n, d, a
with open(path, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=1)
print("modules", len(data["modules"]), "ranked", sum(1 for m in data["modules"] if m["rank"] <= len(popular)))
