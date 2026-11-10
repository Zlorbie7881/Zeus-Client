# Publishing the Zeus website

This folder is a complete static website for Zeus 0.9.9.0. Nothing runs on the server: no database, no PHP, no Node. Any host that can serve files works.
It must live at the **root of a domain or subdomain** (for example `https://zeus.example.com/`), not in a sub-folder.

## What visitors get
Home, Download, Features, Tutorial, Help (guides, FAQ and the helper), Credits and Information (with the update timeline).
The **Settings / control center page does not work on a public site**. It only controls a game running on the same computer, so the page tells visitors how to open it from inside Minecraft. That is intentional and safe: your public site never touches anyone's game.

## 1. Fill in your links
Run `python3 tools/build_website.py --url https://zeus.example.com --download <link to your jar or download page> --discord <invite> --jar <path to the release jar>` again whenever something changes.

> No download link was given. The Download button currently says the link is not published yet.

> No checksum was given. Pass `--jar path/to/zeus.jar` so visitors can verify their download.

You can also edit `site.json` in this folder by hand (the `defaults` block holds all links), then re-upload it.

## 2. Pick a host

### Cloudflare Pages (free, fast, recommended)
1. Create a Cloudflare account, open **Workers & Pages → Create → Pages → Upload assets**.
2. Drag this whole folder in and deploy.
3. Open the project's **Custom domains** tab, add `zeus.example.com` and follow the DNS prompts. HTTPS is automatic.
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
- a subdomain (`zeus`): a **CNAME** to the host's address;
- the bare domain: an **A/ALIAS/ANAME** record, as the host's instructions say.
DNS can take from a few minutes to a day. Always use HTTPS; every host above provides a free certificate.

## 4. Check it
- `https://zeus.example.com/` and `https://zeus.example.com/help` both open (refreshing a deep link must not give an error).
- `https://zeus.example.com/robots.txt` and `/sitemap.xml` open. Submit the sitemap in Google Search Console if you want to be found.
- Browsers' developer console shows no red errors. The page's Content-Security-Policy is strict (no inline scripts, nothing loaded from other websites), so if you add analytics or embeds you must extend it on purpose.

## Privacy and safety notes
- The site loads its fonts and images from itself and contains no trackers. If you add analytics, update your own privacy notice.
- The in-page helper runs entirely in the visitor's browser. It is a search over this site's own content, not an AI service, and sends nothing anywhere.
- Never put a download of anything other than your own, scanned release jar behind the Download button, and publish its SHA-256 (`--jar` does this) so people can verify it.
- Cheat clients are banned on many servers. Keep the warning on the Download and Information pages.

## Updating
Rebuild with the same command and upload again. File names inside `css/` and `js/` carry a version hash in the HTML, so visitors get the new files immediately.
