#!/usr/bin/env python3
"""Adds a screenshot or clip to a module's drawer on the Features page.

    python3 tools/add_media.py <module id> <file> ["caption"] [--poster poster.jpg]

Copies the file into src/main/resources/assets/zeus/web/media/ under a safe name and lists it in media.json.
Remove an entry by deleting it from media.json (and the file from media/)."""
import json, os, re, shutil, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WEB = os.path.join(ROOT, "src", "main", "resources", "assets", "zeus", "web")
IMG = {".jpg", ".jpeg", ".png", ".webp", ".gif"}
VID = {".mp4", ".webm"}


def die(msg):
    print("error:", msg, file=sys.stderr)
    sys.exit(1)


def main(argv):
    poster = None
    if "--poster" in argv:
        i = argv.index("--poster")
        if i + 1 >= len(argv):
            die("--poster needs a file")
        poster = argv[i + 1]
        argv = argv[:i] + argv[i + 2:]
    if len(argv) < 2:
        die(__doc__)
    mod, path = argv[0].lower(), argv[1]
    caption = argv[2] if len(argv) > 2 else ""
    ids = {m["id"] for m in json.load(open(os.path.join(WEB, "features.json"), encoding="utf-8"))["modules"]}
    if mod not in ids:
        die("no module with id %r (ids are listed in features.json)" % mod)
    if not os.path.isfile(path):
        die("file not found: " + path)
    ext = os.path.splitext(path)[1].lower()
    kind = "image" if ext in IMG else "video" if ext in VID else None
    if not kind:
        die("unsupported file type %s (use %s)" % (ext, ", ".join(sorted(IMG | VID))))
    size = os.path.getsize(path)
    limit = 8_000_000 if kind == "video" else 1_500_000
    if size > limit:
        print("warning: %s is %.1f MB, which makes the site and the jar heavy. Consider shrinking it." % (os.path.basename(path), size / 1e6))
    mj_path = os.path.join(WEB, "media.json")
    mj = json.load(open(mj_path, encoding="utf-8")) if os.path.isfile(mj_path) else {"modules": {}}
    items = mj.setdefault("modules", {}).setdefault(mod, [])
    os.makedirs(os.path.join(WEB, "media"), exist_ok=True)
    n = 1
    while os.path.exists(os.path.join(WEB, "media", "%s-%d%s" % (mod, n, ext))):
        n += 1
    name = "%s-%d%s" % (mod, n, ext)
    shutil.copyfile(path, os.path.join(WEB, "media", name))
    entry = {"type": kind, "src": "/media/" + name}
    if caption:
        entry["caption"] = caption[:120]
    if poster and kind == "video":
        pext = os.path.splitext(poster)[1].lower()
        if pext not in IMG:
            die("the poster must be an image")
        pname = "%s-%d-poster%s" % (mod, n, pext)
        shutil.copyfile(poster, os.path.join(WEB, "media", pname))
        entry["poster"] = "/media/" + pname
    items.append(entry)
    with open(mj_path, "w", encoding="utf-8") as f:
        json.dump(mj, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print("added %s to %s (%d item%s)" % (name, mod, len(items), "" if len(items) == 1 else "s"))


if __name__ == "__main__":
    main(sys.argv[1:])
