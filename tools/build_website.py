#!/usr/bin/env python3
"""Build the Zeus website into a folder you can upload to any static host and put behind your own domain.

    python3 tools/build_website.py --url https://zeus.example.com --out website \
        --download https://example.com/zeus-0.9.5.0.jar --discord https://discord.gg/xxxx --jar build/libs/zeus-0.9.5.0.jar

What it does
  * copies src/main/resources/assets/zeus/web (the same files the mod serves locally)
  * writes your links, domain and jar checksum into site.json and marks the copy as "hosted"
    (a hosted copy never calls the in-game API; the Settings page tells visitors it only works inside the game)
  * creates a real page for every route (/download/, /help/, ...) with its own <title>, description, canonical URL,
    Open Graph tags and a short text version for search engines and browsers without JavaScript
  * adds robots.txt, sitemap.xml, 404.html, security headers and ready-made configs for Netlify, Cloudflare Pages,
    Vercel, GitHub Pages, nginx, Caddy and Apache, plus DEPLOY.md
The site must be served from the root of a domain or subdomain (https://zeus.example.com/), not from a sub-folder.
"""
import argparse, datetime, hashlib, html, json, os, re, shutil, sys

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "src", "main", "resources", "assets", "zeus", "web")

ROUTES = {
    "": ("Zeus Client – a cheat client for Minecraft 1.21.11",
         "Zeus Client is a Fabric cheat client for Minecraft 1.21.11 with combat, movement, ESP and base-finding modules and an in-game menu.",
         "A cheat client for Minecraft 1.21.11", 1.0),
    "download": ("Download Zeus Client", "Download Zeus Client for Minecraft 1.21.11 on Fabric. What you need and how to install it.", "Download Zeus Client", 0.9),
    "features": ("Features – Zeus Client", "Every Zeus Client module and its settings. Search by name or filter by category.", "Everything Zeus can do", 0.9),
    "tutorial": ("Tutorial – Zeus Client", "How to get started with Zeus Client: open the menu, switch modules on and place your HUD.", "Getting started", 0.7),
    "help": ("Help & FAQ – Zeus Client", "Guides, a searchable FAQ and a helper that answers questions about Zeus Client.", "Help centre", 0.9),
    "source": ("Source code – Zeus Client", "Read the Zeus Client source code on this site, file by file.", "Source code", 0.6),
    "config": ("Build a config – Zeus Client", "Answer a few yes or no questions, or pick a playstyle, and get a set of modules to start with.", "Build a config", 0.6),
    "knives": ("Knife preview – Zeus Client", "Every Zeus knife in every finish, drawn by the same code the game uses, with what its Inspect trick does.", "Knife preview", 0.6),
    "monitor": ("Performance monitor – Zeus Client", "See roughly how much FPS a set of Zeus modules will cost you, with a gauge and a bar for every module.", "Performance monitor", 0.6),
    "troubleshoot": ("Troubleshooter – Zeus Client", "Answer a few yes or no questions to find the fix for your Zeus problem.", "Troubleshooter", 0.6),
    "account": ("Your profile – Zeus Client", "Your saved modules, configs and helper history.", "Your profile", 0.2),
    "credits": ("Credits – Zeus Client", "Who made Zeus Client, and the projects it builds on.", "Credits", 0.4),
    "information": ("Information – Zeus Client", "About Zeus Client, a few notes to read first, and every release from the first version to today.", "About and update timeline", 0.7),
    "settings": ("Control center – Zeus Client", "Control a running Zeus Client from your browser. It only works from the link the game prints.", "Control center", 0.2),
    "admin": ("Administration – Zeus Client", "Site administration.", "Administration", 0.0),
}
NOINDEX = {"settings", "account", "admin"}

CSP = ("default-src 'none'; img-src 'self' data:; media-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self'; font-src 'self'; "
       "connect-src 'self'; manifest-src 'self'; form-action 'none'; base-uri 'none'; frame-ancestors 'none'")


def die(msg):
    print("error:", msg, file=sys.stderr)
    sys.exit(1)


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def tree_hash(root):
    h = hashlib.sha256()
    for sub in ("css", "js"):
        for name in sorted(os.listdir(os.path.join(root, sub))):
            with open(os.path.join(root, sub, name), "rb") as f:
                h.update(f.read())
    return h.hexdigest()[:10]


def page_html(template, route, base, noscript_extra, ver):
    title, desc, h1, _ = ROUTES[route]
    url = base + ("/" + route if route else "/")
    t = template
    t = re.sub(r"<title>.*?</title>", "<title>%s</title>" % html.escape(title), t, count=1)
    t = re.sub(r'(<meta name="description" content=")[^"]*(")', lambda m: m.group(1) + html.escape(desc, quote=True) + m.group(2), t, count=1)
    t = re.sub(r'(<meta property="og:title" content=")[^"]*(")', lambda m: m.group(1) + html.escape(title, quote=True) + m.group(2), t, count=1)
    t = re.sub(r'(<meta property="og:description" content=")[^"]*(")', lambda m: m.group(1) + html.escape(desc, quote=True) + m.group(2), t, count=1)
    t = t.replace('content="/img/og.jpg"', 'content="%s/img/og.jpg"' % base)
    extra = '<link rel="canonical" href="%s">\n<meta property="og:url" content="%s">\n<meta name="twitter:image" content="%s/img/og.jpg">\n' % (url, url, base)
    if route in NOINDEX:
        extra += '<meta name="robots" content="noindex">\n'
    t = t.replace("</head>", extra + "</head>", 1)
    t = re.sub(r'<script src="(/js/[a-z0-9]+\.js)"></script>', lambda m: '<script src="%s?v=%s"></script>' % (m.group(1), ver), t)
    t = re.sub(r'href="(/css/[a-z0-9]+\.css)"', lambda m: 'href="%s?v=%s"' % (m.group(1), ver), t)
    links = "".join('<li><a href="/%s">%s</a></li>' % (r, html.escape(ROUTES[r][2])) for r in ROUTES if r and r not in NOINDEX)
    body = ('<main id="view" tabindex="-1"><div class="wrap page"><h1 class="title">%s</h1><p class="lead">%s</p>%s<ul>%s</ul></div></main>'
            % (html.escape(h1), html.escape(desc), noscript_extra, links))
    t = t.replace('<main id="view" tabindex="-1"></main>', body, 1)
    return t


def main():
    ap = argparse.ArgumentParser(description="Build the Zeus website for hosting.")
    ap.add_argument("--url", default="", help="public address, e.g. https://zeus.example.com (needed for canonical links and the sitemap)")
    ap.add_argument("--out", default="website", help="output folder (default: website)")
    ap.add_argument("--download", default="", help="https link behind the Download button")
    ap.add_argument("--discord", default="")
    ap.add_argument("--youtube", default="")
    ap.add_argument("--twitter", default="")
    ap.add_argument("--github", default="", help="link to the GitHub repository (shown on the Source code page)")
    ap.add_argument("--tutorial", default="", help="link to a video tutorial")
    ap.add_argument("--email", default="", help="contact e-mail (optional)")
    ap.add_argument("--jar", default="", help="path to the released jar; its SHA-256 is shown on the Download page")
    ap.add_argument("--sha256", default="", help="SHA-256 of the jar if you do not have the file here")
    ap.add_argument("--overrides", default="", help="overrides.json exported from the site's admin panel (default: tools/site/overrides.json if it exists)")
    ap.add_argument("--version", default="", help="override the version shown (default: taken from site.json)")
    a = ap.parse_args()

    src = os.path.normpath(SRC)
    if not os.path.isdir(src):
        die("web sources not found at " + src)
    si = os.path.join(src, "codebundle", "index.json")
    if not os.path.isfile(si):
        die("the source bundle is missing. Run  python3 tools/make_source_manifest.py  first (it is part of every release).")
    try:
        bundled = json.load(open(si, encoding="utf-8")).get("version", "")
        shown = json.load(open(os.path.join(src, "site.json"), encoding="utf-8")).get("defaults", {}).get("version", "")
        if bundled and shown and bundled != shown and not a.version:
            die("the source bundle is for version %s but site.json says %s. Re-run tools/make_source_manifest.py after the version bump." % (bundled, shown))
    except (OSError, ValueError):
        pass
    base = a.url.rstrip("/")
    if base and not re.match(r"^https://[A-Za-z0-9.-]+(:\d+)?$", base) and not re.match(r"^http://(localhost|127\.0\.0\.1)(:\d+)?$", base):
        die("--url must look like https://zeus.example.com (no path)")
    for name in ("download", "discord", "youtube", "twitter", "tutorial", "github"):
        v = getattr(a, name)
        if v and not re.match(r"^https?://", v):
            die("--%s must start with https://" % name)
    if a.email and not re.match(r"^[^@\s]+@[^@\s]+\.[^@\s]+$", a.email):
        die("--email is not a valid address")
    sha = a.sha256.strip().lower()
    if a.jar:
        if not os.path.isfile(a.jar):
            die("jar not found: " + a.jar)
        sha = sha256_file(a.jar)
    if sha and not re.match(r"^[0-9a-f]{64}$", sha):
        die("--sha256 must be 64 hex characters")

    out = os.path.abspath(a.out)
    if os.path.exists(out):
        # only wipe folders this script made (or empty ones) so a typo in --out cannot delete something important
        marker = os.path.join(out, ".zeus-site")
        if os.listdir(out) and not os.path.exists(marker):
            die("%s exists and was not created by this script. Choose another --out or empty it first." % out)
        shutil.rmtree(out)
    shutil.copytree(src, out, ignore=shutil.ignore_patterns("*.map", "__pycache__"))
    open(os.path.join(out, ".zeus-site"), "w").write("generated by tools/build_website.py\n")

    # overrides.json: content edits made in the admin panel, applied for every visitor
    ov = a.overrides or os.path.join(HERE, "site", "overrides.json")
    if os.path.isfile(ov):
        try:
            data = json.load(open(ov, encoding="utf-8"))
            if not isinstance(data, dict):
                raise ValueError("not an object")
        except (OSError, ValueError) as e:
            die("overrides file is not valid JSON: %s" % e)
        shutil.copyfile(ov, os.path.join(out, "overrides.json"))
        print("applied", ov)

    # site.json
    sj_path = os.path.join(out, "site.json")
    with open(sj_path, encoding="utf-8") as f:
        sj = json.load(f)
    d = sj.setdefault("defaults", {})
    d.update({k: v for k, v in {"siteUrl": base, "downloadUrl": a.download, "discordUrl": a.discord, "youtubeUrl": a.youtube,
                                "twitterUrl": a.twitter, "githubUrl": a.github, "tutorialUrl": a.tutorial, "contactEmail": a.email, "sha256": sha,
                                "version": a.version}.items() if v})
    sj["hosted"] = True
    with open(sj_path, "w", encoding="utf-8") as f:
        json.dump(sj, f, ensure_ascii=False, indent=2)
    version = d.get("version", "")

    # version.json: what the in-game Update Checker reads (Update Checker > Version address = <your site>/version.json)
    try:
        tl = json.load(open(os.path.join(out, "timeline.json"), encoding="utf-8"))
        note = tl[0].get("title", "") if isinstance(tl, list) and tl else ""
    except Exception:
        note = ""
    with open(os.path.join(out, "version.json"), "w", encoding="utf-8") as f:
        json.dump({"version": version, "url": (a.download or (base + "/download" if base else "")), "notes": note}, f, ensure_ascii=False, indent=1)

    ver = tree_hash(out)
    with open(os.path.join(out, "index.html"), encoding="utf-8") as f:
        template = f.read()
    site_base = base  # may be empty: then canonical/og urls are skipped by the app and left relative
    noscript = '<p>This site needs JavaScript to show its pages. Zeus Client is a client-side Fabric mod for Minecraft 1.21.11.</p>'
    for route in ROUTES:
        page = page_html(template, route, site_base, noscript, ver)
        if not site_base:
            page = re.sub(r'<link rel="canonical"[^>]*>\n|<meta property="og:url"[^>]*>\n', "", page)
        if route == "":
            target = os.path.join(out, "index.html")
        else:
            os.makedirs(os.path.join(out, route), exist_ok=True)
            target = os.path.join(out, route, "index.html")
        with open(target, "w", encoding="utf-8") as f:
            f.write(page)
    # 404: the same shell, so unknown addresses fall back to the home page instead of an empty screen
    with open(os.path.join(out, "404.html"), "w", encoding="utf-8") as f:
        f.write(page_html(template, "", site_base, noscript, ver).replace("<title>", "<title>Page not found – ", 1))

    # robots + sitemap
    today = datetime.date.today().isoformat()
    with open(os.path.join(out, "robots.txt"), "w") as f:
        f.write("User-agent: *\nAllow: /\nDisallow: /settings\n")
        if base:
            f.write("Sitemap: %s/sitemap.xml\n" % base)
    if base:
        urls = "".join("  <url><loc>%s</loc><lastmod>%s</lastmod><priority>%.1f</priority></url>\n" % (base + ("/" + r if r else "/"), today, ROUTES[r][3]) for r in ROUTES if r not in NOINDEX)
        with open(os.path.join(out, "sitemap.xml"), "w") as f:
            f.write('<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n' + urls + "</urlset>\n")
    open(os.path.join(out, ".nojekyll"), "w").close()
    if base.startswith("https://"):
        with open(os.path.join(out, "CNAME"), "w") as f:
            f.write(base[len("https://"):].split(":")[0] + "\n")

    # headers / redirects
    sec = [("X-Content-Type-Options", "nosniff"), ("Referrer-Policy", "strict-origin-when-cross-origin"), ("X-Frame-Options", "DENY"),
           ("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()"), ("Content-Security-Policy", CSP),
           ("Strict-Transport-Security", "max-age=31536000; includeSubDomains")]
    hdr = "/*\n" + "".join("  %s: %s\n" % kv for kv in sec)
    hdr += "\n/*.html\n  Cache-Control: no-cache\n\n/\n  Cache-Control: no-cache\n"
    hdr += "\n/css/*\n  Cache-Control: public, max-age=3600\n\n/js/*\n  Cache-Control: public, max-age=3600\n\n/*.json\n  Cache-Control: no-cache\n"
    hdr += "\n/fonts/*\n  Cache-Control: public, max-age=31536000, immutable\n\n/img/*\n  Cache-Control: public, max-age=604800\n"
    with open(os.path.join(out, "_headers"), "w") as f:
        f.write(hdr)
    with open(os.path.join(out, "_redirects"), "w") as f:
        f.write("/index.html / 301\n")
    vercel = {"cleanUrls": True, "trailingSlash": False,
              "headers": [{"source": "/(.*)", "headers": [{"key": k, "value": v} for k, v in sec]},
                          {"source": "/fonts/(.*)", "headers": [{"key": "Cache-Control", "value": "public, max-age=31536000, immutable"}]}]}
    with open(os.path.join(out, "vercel.json"), "w") as f:
        json.dump(vercel, f, indent=2)

    os.makedirs(os.path.join(out, "deploy"), exist_ok=True)
    host = base[len("https://"):] if base.startswith("https://") else "zeus.example.com"
    with open(os.path.join(out, "deploy", "nginx.conf.example"), "w") as f:
        f.write("""# /etc/nginx/sites-available/%(h)s   (then: ln -s to sites-enabled, nginx -t, systemctl reload nginx)
server {
    listen 80;
    server_name %(h)s;
    root /var/www/zeus;
    index index.html;

    add_header X-Content-Type-Options nosniff always;
    add_header Referrer-Policy strict-origin-when-cross-origin always;
    add_header X-Frame-Options DENY always;
    add_header Content-Security-Policy "%(csp)s" always;

    location / { try_files $uri $uri/index.html /404.html; }
    location /fonts/ { expires 1y; add_header Cache-Control "public, immutable"; }
    location /img/ { expires 7d; }
    location ~* \\.(css|js)$ { expires 1h; }
    error_page 404 /404.html;
}
# Get HTTPS with:  sudo certbot --nginx -d %(h)s
""" % {"h": host, "csp": CSP})
    with open(os.path.join(out, "deploy", "Caddyfile.example"), "w") as f:
        f.write("""%(h)s {
    root * /var/www/zeus
    encode zstd gzip
    header {
        X-Content-Type-Options nosniff
        Referrer-Policy strict-origin-when-cross-origin
        X-Frame-Options DENY
        Content-Security-Policy "%(csp)s"
        Strict-Transport-Security "max-age=31536000"
    }
    handle_errors {
        rewrite * /404.html
        file_server
    }
    file_server
}
# Caddy gets and renews the HTTPS certificate for you.
""" % {"h": host, "csp": CSP})
    with open(os.path.join(out, "deploy", "htaccess.example"), "w") as f:
        f.write("""# Apache: copy to .htaccess in the site folder (needs mod_headers and mod_rewrite)
DirectoryIndex index.html
ErrorDocument 404 /404.html
Header always set X-Content-Type-Options "nosniff"
Header always set Referrer-Policy "strict-origin-when-cross-origin"
Header always set X-Frame-Options "DENY"
Header always set Content-Security-Policy "%s"
""" % CSP)

    with open(os.path.join(out, "DEPLOY.md"), "w", encoding="utf-8") as f:
        f.write(deploy_guide(base, host, version, bool(a.download), bool(sha)))

    files = sum(len(fs) for _, _, fs in os.walk(out))
    size = sum(os.path.getsize(os.path.join(r, n)) for r, _, fs in os.walk(out) for n in fs)
    print("Built %s (%d files, %.1f MB)" % (out, files, size / 1e6))
    if not base:
        print("note: no --url given, so canonical links, sitemap.xml and CNAME were skipped. Re-run with --url https://your-domain once you know it.")
    if not a.download:
        print("note: no --download link; the Download button will say the link is not published yet.")
    print("Preview locally:  python3 -m http.server --directory %s 8080   (clean URLs like /help need a host with the rewrite rules; the home page always works)" % a.out)


def deploy_guide(base, host, version, has_dl, has_sha):
    return """# Publishing the Zeus website

This folder is a complete static website%(ver)s. Nothing runs on the server: no database, no PHP, no Node. Any host that can serve files works.
It must live at the **root of a domain or subdomain** (for example `https://%(h)s/`), not in a sub-folder.

## What visitors get
Home, Download, Features, Tutorial, Help (guides, FAQ and the helper), Credits and Information (with the update timeline).
The **Settings / control center page does not work on a public site**. It only controls a game running on the same computer, so the page tells visitors how to open it from inside Minecraft. That is intentional and safe: your public site never touches anyone's game.

## 1. Fill in your links
Run `python3 tools/build_website.py --url https://%(h)s --download <link to your jar or download page> --discord <invite> --jar <path to the release jar>` again whenever something changes.
%(dl)s%(sha)s
You can also edit `site.json` in this folder by hand (the `defaults` block holds all links), then re-upload it.

## 2. Pick a host

### Cloudflare Pages (free, fast, recommended)
1. Create a Cloudflare account, open **Workers & Pages → Create → Pages → Upload assets**.
2. Drag this whole folder in and deploy.
3. Open the project's **Custom domains** tab, add `%(h)s` and follow the DNS prompts. HTTPS is automatic.
The `_headers` file adds the security headers. `_redirects` redirects `/index.html` to `/`; unknown addresses get `404.html` automatically.

### Netlify
Drag this folder onto https://app.netlify.com/drop, then **Domain settings → Add a custom domain**. `_headers` and `_redirects` are used automatically.

### GitHub Pages
1. Push the contents of this folder to a repository (the branch root, or a `/docs` folder).
2. **Settings → Pages**: choose the branch. The `CNAME` file in this folder already contains your domain.
3. Add the DNS records GitHub lists and tick **Enforce HTTPS**.
GitHub Pages ignores `_headers`, so the security headers are not sent there. The site still works.

### Vercel
`npx vercel --prod` inside this folder (or import it in the dashboard). `vercel.json` sets clean URLs and the headers. Add the domain in **Settings → Domains**.

### Your own server (VPS)
Copy the folder to `/var/www/zeus`, then use `deploy/nginx.conf.example` or `deploy/Caddyfile.example` (Caddy also gets the HTTPS certificate for you). For Apache see `deploy/htaccess.example`.

## 3. Point the domain
At your domain registrar add the records your host asks for. Typically:
- a subdomain (`%(sub)s`): a **CNAME** to the host's address;
- the bare domain: an **A/ALIAS/ANAME** record, as the host's instructions say.
DNS can take from a few minutes to a day. Always use HTTPS; every host above provides a free certificate.

## 4. Check it
- `https://%(h)s/` and `https://%(h)s/help` both open (refreshing a deep link must not give an error).
- `https://%(h)s/robots.txt` and `/sitemap.xml` open. Submit the sitemap in Google Search Console if you want to be found.
- Browsers' developer console shows no red errors. The page's Content-Security-Policy is strict (no inline scripts, nothing loaded from other websites), so if you add analytics or embeds you must extend it on purpose.

## Privacy and safety notes
- The site loads its fonts and images from itself and contains no trackers. If you add analytics, update your own privacy notice.
- The in-page helper runs entirely in the visitor's browser. It is a search over this site's own content, not an AI service, and sends nothing anywhere.
- Never put a download of anything other than your own, scanned release jar behind the Download button, and publish its SHA-256 (`--jar` does this) so people can verify it.
- Cheat clients are banned on many servers. Keep the warning on the Download and Information pages.

## Updating
Rebuild with the same command and upload again. File names inside `css/` and `js/` carry a version hash in the HTML, so visitors get the new files immediately.
""" % {"ver": (" for Zeus " + version) if version else "", "h": host, "sub": host.split(".")[0] if "." in host else host,
       "dl": "" if has_dl else "\n> No download link was given. The Download button currently says the link is not published yet.\n",
       "sha": "" if has_sha else "\n> No checksum was given. Pass `--jar path/to/zeus.jar` so visitors can verify their download.\n"}


if __name__ == "__main__":
    main()
