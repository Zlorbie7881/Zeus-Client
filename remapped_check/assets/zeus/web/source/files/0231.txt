"""Writes help.json, timeline.json, trouble.json and presets.json for the Zeus website from the data files next to this script.
Run:  python3 tools/site/gen_site_data.py <web folder>     (features.json must already be in the web folder)"""
import json, os, re, sys
sys.path.insert(0, os.path.dirname(__file__))
from help_data import SECTIONS, FAQ, QUICK
from timeline_data import TIMELINE, EXTRA
from faq_cats import category, ORDER
from trouble_data import NODES, START
from presets_data import PRESETS
import help_950
import config_data as CD

out = sys.argv[1]
feat = json.load(open(os.path.join(out, "features.json"), encoding="utf-8"))
mods = feat["modules"]
ids = {m["id"] for m in mods}


def resolve(name):
    """A module id, a display name or an alias (case-insensitive) -> module id, or None."""
    n = name.lower().strip()
    if n in ids:
        return n
    for m in mods:
        if m["name"].lower() == n or n in [a.lower() for a in m.get("aliases", [])]:
            return m["id"]
    return None


def dump(name, data, **kw):
    with open(os.path.join(out, name), "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, **kw)


# ---- help.json (FAQ with categories)
faq = []
FAQ_ALL = [t for t in FAQ if t[0] not in help_950.REMOVE] + help_950.NEW
for q, a, k, links in FAQ_ALL:
    q = help_950.RENAME.get(q, q)
    a = help_950.PATCH.get(q, a)
    e = {"q": q, "a": a, "keys": k, "links": [{"label": l, "href": h} for l, h in links], "cat": category(q)}
    alts = help_950.ALTS.get(q)
    if alts:
        e["alts"] = [x if isinstance(x, dict) else {"p": [x]} for x in alts]
    faq.append(e)
missing = [q for q in help_950.ALTS if q not in {f["q"] for f in faq}]
if missing:
    print("warning: ALTS for unknown questions:", missing)
help_json = {"sections": SECTIONS, "quick": QUICK, "faq": faq, "cats": ORDER}
dump("help.json", help_json, separators=(",", ":"))

# ---- timeline.json: each release links to the modules it added or changed
tl = []
for v, t, x in TIMELINE:
    extra = EXTRA.get(v, {})
    found = []
    for name in extra.get("mods", []):
        i = resolve(name)
        if i is None:
            print("note: no module called", repr(name), "in", v)
        elif i not in found:
            found.append(i)
    # modules named in the release text count too (whole-word, capitalised as in the module list)
    for m in mods:
        if len(m["name"]) >= 5 and m["id"] not in found and re.search(r"(?<![A-Za-z])" + re.escape(m["name"]) + r"(?![A-Za-z])", x):
            found.append(m["id"])
    for m in mods:
        for al in m.get("aliases", []):
            if len(al) >= 5 and m["id"] not in found and re.search(r"(?<![A-Za-z])" + re.escape(al) + r"(?![A-Za-z])", x):
                found.append(m["id"])
    e = {"version": v, "title": t, "text": x}
    if found:
        e["mods"] = found
    if extra.get("settings"):
        e["settings"] = extra["settings"]
    tl.append(e)
dump("timeline.json", tl, indent=1)

# ---- trouble.json
faq_q = {q for q, *_ in FAQ}
for k, n in NODES.items():
    if "faq" in n and n["faq"] not in faq_q:
        sys.exit("trouble_data: unknown FAQ question " + repr(n["faq"]))
    for side in ("yes", "no"):
        if side in n and n[side] not in NODES:
            sys.exit("trouble_data: unknown node " + n[side])
dump("trouble.json", {"start": START, "nodes": NODES}, separators=(",", ":"))

# ---- presets.json
pr = []
for p in PRESETS:
    good = [m for m in p["mods"] if m in ids]
    if len(good) != len(p["mods"]):
        print("note: preset", p["id"], "dropped", [m for m in p["mods"] if m not in ids])
    pr.append(dict(p, mods=good))
dump("presets.json", pr, separators=(",", ":"))
# ---- configwiz.json (questions, buckets, playstyles)
for k, (_, _, _, ms) in CD.BUCKETS.items():
    miss = [m for m in ms if m not in ids]
    if miss:
        sys.exit("config_data: unknown module " + str(miss))
qids = {q["id"] for q in CD.QUESTIONS}
for q in CD.QUESTIONS:
    for r in q.get("if", []):
        if r not in qids:
            sys.exit("config_data: unknown question " + r)
for ps in CD.PLAYSTYLES:
    for a in ps["answers"]:
        if a not in qids:
            sys.exit("config_data: playstyle " + ps["id"] + " uses unknown question " + a)
dump("configwiz.json", {
    "q": CD.QUESTIONS,
    "buckets": {k: {"name": v[0], "why": v[1], "core": v[2], "mods": v[3]} for k, v in CD.BUCKETS.items()},
    "safe": CD.SAFE, "sponly": CD.SPONLY, "heavy": CD.HEAVY, "risky": CD.RISKY,
    "styles": CD.PLAYSTYLES}, separators=(",", ":"))
print("faq", len(FAQ), "sections", len(SECTIONS), "timeline", len(TIMELINE), "trouble nodes", len(NODES), "presets", len(PR) if False else len(pr))
