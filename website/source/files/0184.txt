/* Zeus Client website 9.5: administration. One account, no email. The sign-in is checked in the browser against a salted PBKDF2-SHA256 hash;
   neither the name nor the password exists anywhere in the site. Edits are kept as a draft in this browser and exported as overrides.json,
   which the site applies for every visitor once it is published with the site (tools/build_website.py copies it). */
(function () {
  "use strict";
  var Z = window.Z, h = Z.h, icon = Z.icon, $ = Z.$, clear = Z.clear;
  var SALT = "de9f3f2db188e1d02efa59184805d8f6", HASH = "88f1f3398844b00d3efde55fce318704b69f5ad033905b61ff10008d9ed65678", ITER = 310000;
  var SESSION = "zeus.adm.s", DRAFT = "zeus.adm.draft", FAILS = "zeus.adm.f", TTL = 2 * 3600 * 1000;

  /* ---------------------------------------------------------------- sign-in */
  function hexBytes(s) { var a = new Uint8Array(s.length / 2); for (var i = 0; i < a.length; i++) a[i] = parseInt(s.substr(i * 2, 2), 16); return a; }
  function toHex(buf) { var a = new Uint8Array(buf), o = ""; for (var i = 0; i < a.length; i++) o += (a[i] < 16 ? "0" : "") + a[i].toString(16); return o; }
  function derive(user, pw) {
    var c = window.crypto && window.crypto.subtle;
    if (!c || !window.TextEncoder) return Promise.reject(new Error("Sign-in needs a secure connection (https)."));
    return c.importKey("raw", new TextEncoder().encode(user + "\0" + pw), "PBKDF2", false, ["deriveBits"]).then(function (k) {
      return c.deriveBits({ name: "PBKDF2", hash: "SHA-256", salt: hexBytes(SALT), iterations: ITER }, k, 256);
    }).then(toHex);
  }
  function same(a, b) { if (a.length !== b.length) return false; var d = 0; for (var i = 0; i < a.length; i++) d |= a.charCodeAt(i) ^ b.charCodeAt(i); return d === 0; }
  function readFails() { try { var f = JSON.parse(localStorage.getItem(FAILS) || "{}"); return { n: +f.n || 0, until: +f.until || 0 }; } catch (e) { return { n: 0, until: 0 }; } }
  function writeFails(f) { try { localStorage.setItem(FAILS, JSON.stringify(f)); } catch (e) { /* storage blocked */ } }
  function bad() { return new Error("Wrong email or password."); }

  function login(user, pw) {
    var f = readFails(), now = Date.now();
    if (f.until > now) return Promise.reject(new Error("Too many attempts. Try again in " + Math.ceil((f.until - now) / 1000) + " seconds."));
    if (!user || !pw || user.length > 80 || pw.length > 200) return Promise.reject(bad());
    return derive(user.trim(), pw).then(function (d) {
      if (!same(d, HASH)) {
        f.n++;
        if (f.n >= 5) f.until = Date.now() + Math.min(3600, 30 * Math.pow(2, f.n - 5)) * 1000;
        writeFails(f);
        throw bad();
      }
      writeFails({ n: 0, until: 0 });
      try { sessionStorage.setItem(SESSION, String(Date.now() + TTL)); } catch (e) { throw new Error("Your browser blocks session storage, so the sign-in cannot be kept."); }
      boot();
    });
  }
  function active() {
    try {
      var t = +sessionStorage.getItem(SESSION);
      if (t && t > Date.now()) return true;
      if (t) sessionStorage.removeItem(SESSION);
    } catch (e) { /* storage blocked */ }
    return false;
  }
  function touch() { try { sessionStorage.setItem(SESSION, String(Date.now() + TTL)); } catch (e) { /* ignore */ } }
  function logout() {
    try { sessionStorage.removeItem(SESSION); } catch (e) { /* ignore */ }
    location.href = "/";
  }

  /* ---------------------------------------------------------------- draft */
  var draft = null, orig = {}, faqAll = null, editing = false;
  function emptyDraft() { return { content: {}, links: {}, banner: null, faq: { hide: [], edit: {}, add: [] }, releases: [], sha256: "" }; }
  function clone(o) { return JSON.parse(JSON.stringify(o)); }
  function normalise(d) {
    var o = emptyDraft();
    if (!d || typeof d !== "object") return o;
    if (d.content && typeof d.content === "object") Object.keys(d.content).forEach(function (k) { if (typeof d.content[k] === "string" && d.content[k].length < 6000) o.content[k] = d.content[k]; });
    if (d.links && typeof d.links === "object") Object.keys(d.links).forEach(function (k) { if (typeof d.links[k] === "string" && (d.links[k] === "" || Z.safeUrl(d.links[k]))) o.links[k] = d.links[k]; });
    if (d.banner && typeof d.banner === "object") o.banner = { on: !!d.banner.on, kind: /^(warn|ok)$/.test(d.banner.kind) ? d.banner.kind : "info", text: String(d.banner.text || "").slice(0, 300), label: String(d.banner.label || "").slice(0, 40), href: String(d.banner.href || "").slice(0, 300) };
    if (d.faq && typeof d.faq === "object") {
      if (Array.isArray(d.faq.hide)) o.faq.hide = d.faq.hide.filter(function (x) { return typeof x === "string"; });
      if (d.faq.edit && typeof d.faq.edit === "object") Object.keys(d.faq.edit).forEach(function (k) { var e = d.faq.edit[k]; if (e && typeof e === "object") o.faq.edit[k] = { q: String(e.q || ""), a: String(e.a || "") }; });
      if (Array.isArray(d.faq.add)) o.faq.add = d.faq.add.filter(function (e) { return e && e.q && e.a; }).map(function (e) { return { q: String(e.q), a: String(e.a), cat: String(e.cat || "About Zeus"), keys: String(e.keys || "") }; });
    }
    if (Array.isArray(d.releases)) o.releases = d.releases.filter(function (r) { return r && r.version && r.title; }).map(function (r) { return { version: String(r.version).slice(0, 20), title: String(r.title).slice(0, 120), text: String(r.text || "").slice(0, 2000), mods: Array.isArray(r.mods) ? r.mods.map(String) : [] }; });
    if (/^[0-9a-fA-F]{64}$/.test(d.sha256 || "")) o.sha256 = d.sha256.toLowerCase();
    return o;
  }
  function loadDraft() {
    var d = null;
    try { d = JSON.parse(localStorage.getItem(DRAFT) || "null"); } catch (e) { d = null; }
    draft = normalise(d || Z.overrides);
  }
  function saveDraft() { try { localStorage.setItem(DRAFT, JSON.stringify(draft)); } catch (e) { Z.toast("Your browser blocked saving the draft.", "bad"); } touch(); }
  function getPath(o, p) { var parts = p.split("."); for (var i = 0; i < parts.length; i++) { if (o === undefined || o === null) return undefined; o = o[parts[i]]; } return o; }
  function setContent(key, val) {
    if (!(key in orig)) { var cur = getPath(Z.content, key); orig[key] = cur === undefined ? "" : cur; }
    Z.setPath(Z.content, key, val);
    draft.content[key] = val;
    saveDraft();
  }
  function revertContent(key) {
    if (key in orig) Z.setPath(Z.content, key, orig[key]);
    delete draft.content[key];
    saveDraft();
  }
  function changeCount() {
    return Object.keys(draft.content).length + Object.keys(draft.links).length + (draft.banner ? 1 : 0) + draft.faq.hide.length + Object.keys(draft.faq.edit).length + draft.faq.add.length + draft.releases.length + (draft.sha256 ? 1 : 0);
  }

  /* apply the draft to the live page (admins only) */
  function applyDraft() {
    faqAll = (Z.help.faq || []).slice();
    var d = clone(draft);
    var have = {};
    Z.timeline.forEach(function (r) { have[r.version + "|" + r.title] = 1; });
    d.releases = d.releases.filter(function (r) { return !have[r.version + "|" + r.title]; });
    if (!d.banner) delete d.banner;
    if (!d.sha256) delete d.sha256;
    var linksOk = {};
    Object.keys(d.links).forEach(function (k) { if (d.links[k]) linksOk[k] = d.links[k]; });
    d.links = linksOk;
    Z.applyOverrides(d);
  }

  /* ---------------------------------------------------------------- small UI pieces */
  function field(label, input, hint) {
    return h("label", { class: "adm-f" }, h("span", { text: label }), input, hint ? h("small", { text: hint }) : null);
  }
  function inputText(value, ph) { var i = h("input", { type: "text", maxlength: 600, placeholder: ph || "", autocomplete: "off", spellcheck: "false" }); i.value = value || ""; return i; }
  function area(value, rows) { var t = h("textarea", { rows: rows || 3, maxlength: 4000, spellcheck: "true" }); t.value = value || ""; return t; }
  function btn(text, ic, cls, fn) { var b = h("button", { class: "btn sm " + (cls || "btn-outline"), type: "button" }, ic ? icon(ic, "small") : null, text); if (fn) b.addEventListener("click", fn); return b; }

  /* ---------------------------------------------------------------- tabs */
  var TABS = [["overview", "Overview", "gear"], ["text", "Page text", "file"], ["links", "Links", "link"], ["banner", "Banner", "bolt"], ["faq", "FAQ & helper", "chat"], ["releases", "Releases", "calendar"], ["log", "Helper log", "list"]];
  var tab = "overview", faqPrefill = null;

  function tabOverview() {
    var n = changeCount();
    var file = $("#admImport");
    return h("div", { class: "adm-sec" },
      h("div", { class: "adm-stats" },
        h("div", {}, h("b", { text: String(n) }), h("span", { text: "changes in your draft" })),
        h("div", {}, h("b", { text: String(draft.faq.add.length) }), h("span", { text: "added questions" })),
        h("div", {}, h("b", { text: String(draft.releases.length) }), h("span", { text: "added releases" })),
        h("div", {}, h("b", { text: draft.banner && draft.banner.on ? "On" : "Off" }), h("span", { text: "banner" }))),
      h("div", { class: "card adm-card" },
        h("h3", { text: "How publishing works" }),
        h("p", { class: "muted", text: "Everything you change here is saved as a draft in this browser and you see it on the site straight away. Other visitors see it after you publish: export the draft as overrides.json and put it next to the site's index.html (or save it as tools/site/overrides.json and run the build script again). Nothing else on the site has to change." }),
        h("div", { class: "acts" },
          btn("Export overrides.json", "download", "btn-primary flat", exportDraft),
          btn("Import a file", "folder", "btn-outline", function () { importPick(); }),
          btn("Edit text on the page", "wand", "btn-outline", function () { setEdit(true); Z.go("/"); }),
          btn("Discard the draft", "close", "btn-danger", function () {
            if (!window.confirm("Throw away every unpublished change in this browser?")) return;
            try { localStorage.removeItem(DRAFT); } catch (e) { /* ignore */ }
            location.reload();
          }))),
      h("div", { class: "card adm-card" },
        h("h3", { text: "What you can change" }),
        h("ul", { class: "adm-ul" },
          h("li", { text: "Page text: the tagline, the about text, the safety dialog, install steps, the tutorial, credits and more." }),
          h("li", { text: "Links: Discord, download, GitHub, YouTube, X / Twitter, tutorial video and the SHA-256 shown on the Download page." }),
          h("li", { text: "Banner: a notice across the top of every page." }),
          h("li", { text: "FAQ: reword, hide or add questions. The helper uses the same entries, so its answers change too." }),
          h("li", { text: "Releases: add a new entry to the update timeline." }),
          h("li", { text: "Helper log: the questions visitors asked in this browser that the helper could not answer well." }))),
      h("div", { class: "card adm-card" },
        h("h3", { text: "Good to know" }),
        h("p", { class: "muted", text: "The sign-in check runs in your browser, so it keeps the panel away from casual visitors but it is not server security: anyone could still change the files of a site they host themselves. Do not reuse this password anywhere else. Sign in only on your own device and sign out when you are done; the session also ends by itself after two hours." })));
  }

  var EXCL = [/^defaults\./, /\.icon$/, /\.page$/, /\.url$/, /^banner\./];
  var GROUPS = { _general: "General", home: "Home page", strip: "Home highlights", requirements: "Requirements", install: "Install steps", tutorial: "Tutorial", commands: "Commands", credits: "Credits", references: "References", safe: "Is Zeus safe? dialog" };
  function flat(o, p, out) {
    if (typeof o === "string") { if (!EXCL.some(function (r) { return r.test(p); })) out.push(p); return; }
    if (Array.isArray(o)) o.forEach(function (v, i) { flat(v, p ? p + "." + i : String(i), out); });
    else if (o && typeof o === "object") Object.keys(o).forEach(function (k) { flat(o[k], p ? p + "." + k : k, out); });
  }
  function tabText() {
    var keys = [];
    flat(Z.content, "", keys);
    var groups = {};
    keys.forEach(function (k) {
      var g = k.indexOf(".") < 0 ? "_general" : k.split(".")[0];
      (groups[g] = groups[g] || []).push(k);
    });
    var order = Object.keys(GROUPS).filter(function (g) { return groups[g]; }).concat(Object.keys(groups).filter(function (g) { return !GROUPS[g]; }));
    var q = h("input", { type: "search", placeholder: "Search the text…", "aria-label": "Search the text" });
    var host = h("div", {});
    function rows() {
      clear(host);
      var needle = q.value.trim().toLowerCase();
      order.forEach(function (g) {
        var list = groups[g].filter(function (k) { return !needle || k.toLowerCase().indexOf(needle) >= 0 || String(getPath(Z.content, k)).toLowerCase().indexOf(needle) >= 0; });
        if (!list.length) return;
        var det = h("details", { class: "adm-grp" }, h("summary", {}, h("b", { text: GROUPS[g] || g }), h("em", { text: list.length + " fields" })));
        if (needle || g === "_general") det.open = true;
        list.forEach(function (k) { det.appendChild(textRow(k)); });
        host.appendChild(det);
      });
      if (!host.firstChild) host.appendChild(h("p", { class: "muted", text: "Nothing matches." }));
    }
    q.addEventListener("input", Z.debounce(rows, 150));
    rows();
    return h("div", { class: "adm-sec" }, h("p", { class: "muted", text: "Type in a field and it changes on the site right away (in your browser). The small key under each field is its place in the page data." }), q, host);
  }
  function textRow(key) {
    var v = String(getPath(Z.content, key) || "");
    var multi = v.length > 64 || v.indexOf("\n") >= 0;
    var inp = multi ? area(v, Math.min(7, Math.ceil(v.length / 70) + 1)) : inputText(v);
    var row = h("div", { class: "adm-row" });
    var rev = btn("Undo", "refresh", "btn-outline", function () { revertContent(key); inp.value = String(getPath(Z.content, key) || ""); row.classList.remove("chg"); });
    inp.addEventListener("input", Z.debounce(function () { setContent(key, inp.value); row.classList.add("chg"); }, 300));
    if (key in draft.content) row.classList.add("chg");
    row.appendChild(h("label", {}, h("code", { text: key }), inp));
    row.appendChild(rev);
    return row;
  }

  var LINKS = [["discord", "Discord invite"], ["download", "Download link (the jar)"], ["github", "GitHub page"], ["youtube", "YouTube"], ["twitter", "X / Twitter"], ["tutorial", "Tutorial video"]];
  function tabLinks() {
    var box = h("div", { class: "adm-sec" }, h("p", { class: "muted", text: "Only https:// links are accepted. Leave a field empty to remove that link from the site." }));
    var card = h("div", { class: "card adm-card" });
    LINKS.forEach(function (l) {
      var i = inputText(draft.links[l[0]] !== undefined ? draft.links[l[0]] : (Z.site.links[l[0]] || ""), "https://…");
      var note = h("small", { class: "adm-note" });
      i.addEventListener("input", Z.debounce(function () {
        var v = i.value.trim();
        if (v && !Z.safeUrl(v)) { note.textContent = "Not saved: this must start with https://"; return; }
        note.textContent = "";
        draft.links[l[0]] = v; Z.site.links[l[0]] = v; saveDraft();
      }, 350));
      card.appendChild(h("label", { class: "adm-f" }, h("span", { text: l[1] }), i, note));
    });
    var sh = inputText(draft.sha256 || Z.sha256 || "", "64 hexadecimal characters");
    var shn = h("small", { class: "adm-note" });
    sh.addEventListener("input", Z.debounce(function () {
      var v = sh.value.trim().toLowerCase();
      if (v && !/^[0-9a-f]{64}$/.test(v)) { shn.textContent = "Not saved: a SHA-256 is 64 characters of 0-9 and a-f."; return; }
      shn.textContent = ""; draft.sha256 = v; Z.sha256 = v; saveDraft();
    }, 350));
    card.appendChild(h("label", { class: "adm-f" }, h("span", { text: "SHA-256 of the official jar" }), sh, shn));
    box.appendChild(card);
    return box;
  }

  function tabBanner() {
    var b = draft.banner || (Z.content.banner && typeof Z.content.banner === "object" ? clone(Z.content.banner) : { on: false, kind: "info", text: "", label: "", href: "" });
    var on = h("input", { type: "checkbox" }); on.checked = !!b.on;
    var kind = h("select", {}, [["info", "Info (blue)"], ["warn", "Warning (amber)"], ["ok", "Good news (green)"]].map(function (k) { return h("option", { value: k[0], text: k[1] }); }));
    kind.value = b.kind || "info";
    var text = inputText(b.text, "What visitors should know"), label = inputText(b.label, "Button text, e.g. Learn more"), href = inputText(b.href, "https://… or /download");
    var err = h("small", { class: "adm-note" });
    function save() {
      var hv = href.value.trim();
      if (hv && !/^(https:\/\/|http:\/\/|\/)/.test(hv)) { err.textContent = "The link must start with https:// or /"; return; }
      err.textContent = "";
      draft.banner = { on: on.checked, kind: kind.value, text: text.value.trim(), label: label.value.trim(), href: hv };
      Z.content.banner = clone(draft.banner);
      saveDraft(); Z.renderBanner();
    }
    [on, kind].forEach(function (e) { e.addEventListener("change", save); });
    [text, label, href].forEach(function (e) { e.addEventListener("input", Z.debounce(save, 300)); });
    return h("div", { class: "adm-sec" },
      h("div", { class: "card adm-card" },
        h("label", { class: "adm-sw" }, on, h("span", { text: "Show the banner on every page" })),
        field("Style", kind), field("Message", text), field("Button text (optional)", label), field("Button link (optional)", href), err));
  }

  function faqList() { return Z.help.faq || []; }
  function tabFaq() {
    var cats = (Z.help && Z.help.cats) || ["About Zeus"];
    var box = h("div", { class: "adm-sec" });
    var addQ = inputText(faqPrefill || "", "Question, e.g. How do I reset my keybinds?");
    var addA = area("", 4), addK = inputText("", "Extra search words (optional)");
    var addC = h("select", {}, cats.map(function (c) { return h("option", { value: c, text: c }); }));
    var addBtn = btn("Add question", "check", "btn-primary flat", function () {
      var q = addQ.value.trim(), a = addA.value.trim();
      if (q.length < 6 || a.length < 6) { Z.toast("Write a question and an answer first.", "bad"); return; }
      var e = { q: q, a: a, cat: addC.value, keys: addK.value.trim() };
      draft.faq.add.push(e); saveDraft();
      Z.help.faq.push({ q: q, a: a, cat: e.cat, keys: e.keys, alts: [], links: [] });
      if (Z.helper && Z.helper.reset) Z.helper.reset();
      faqPrefill = null; Z.toast("Question added. Try it in the helper."); draw();
    });
    box.appendChild(h("div", { class: "card adm-card" }, h("h3", { text: "Add a question" }), field("Question", addQ), field("Answer", addA), h("div", { class: "adm-two" }, field("Category", addC), field("Search words", addK)), h("div", { class: "acts" }, addBtn)));
    if (draft.faq.add.length) {
      var mine = h("div", { class: "card adm-card" }, h("h3", { text: "Questions you added" }));
      draft.faq.add.forEach(function (e, i) {
        mine.appendChild(h("div", { class: "adm-mini" }, h("b", { text: e.q }), btn("Remove", "close", "btn-danger", function () {
          draft.faq.add.splice(i, 1); saveDraft();
          Z.help.faq = faqList().filter(function (f) { return !(f.q === e.q && f.a === e.a); });
          if (Z.helper && Z.helper.reset) Z.helper.reset();
          draw();
        })));
      });
      box.appendChild(mine);
    }
    var q = h("input", { type: "search", placeholder: "Search the existing questions…", "aria-label": "Search the questions" });
    var host = h("div", {});
    function rows() {
      clear(host);
      var needle = q.value.trim().toLowerCase();
      var list = faqList().filter(function (f) { return !needle || (f.q + " " + f.a).toLowerCase().indexOf(needle) >= 0; });
      list.slice(0, 40).forEach(function (f) {
        var qi = inputText(f.q), ai = area(f.a, 4);
        var det = h("details", { class: "adm-grp" }, h("summary", {}, h("b", { text: f.q }), h("em", { text: f.cat || "" })));
        var save = btn("Save change", "check", "btn-primary flat", function () {
          var nq = qi.value.trim(), na = ai.value.trim();
          if (nq.length < 4 || na.length < 4) { Z.toast("The question and the answer cannot be empty.", "bad"); return; }
          var o = f.orig || f.q;
          draft.faq.edit[o] = { q: nq, a: na }; saveDraft();
          f.orig = o; f.q = nq; f.a = na; f.alts = [];
          if (Z.helper && Z.helper.reset) Z.helper.reset();
          Z.toast("Saved. The helper uses the new answer.");
        });
        var hide = btn("Hide this question", "eye", "btn-danger", function () {
          var o = f.orig || f.q;
          if (draft.faq.hide.indexOf(o) < 0) draft.faq.hide.push(o);
          saveDraft();
          Z.help.faq = faqList().filter(function (x) { return x !== f; });
          if (Z.helper && Z.helper.reset) Z.helper.reset();
          draw();
        });
        det.appendChild(h("div", { class: "adm-det" }, field("Question", qi), field("Answer", ai), h("div", { class: "acts" }, save, hide)));
        host.appendChild(det);
      });
      if (list.length > 40) host.appendChild(h("p", { class: "muted", text: "Showing 40 of " + list.length + ". Search to narrow it down." }));
      if (!list.length) host.appendChild(h("p", { class: "muted", text: "Nothing matches." }));
    }
    q.addEventListener("input", Z.debounce(rows, 150));
    rows();
    box.appendChild(h("div", { class: "card adm-card" }, h("h3", { text: "Existing questions" }), q, host));
    if (draft.faq.hide.length) {
      var hid = h("div", { class: "card adm-card" }, h("h3", { text: "Hidden questions" }));
      draft.faq.hide.forEach(function (o) {
        hid.appendChild(h("div", { class: "adm-mini" }, h("b", { text: o }), btn("Show again", "refresh", "btn-outline", function () {
          draft.faq.hide = draft.faq.hide.filter(function (x) { return x !== o; }); saveDraft();
          (faqAll || []).forEach(function (f) { if ((f.orig || f.q) === o && faqList().indexOf(f) < 0) Z.help.faq.push(f); });
          if (Z.helper && Z.helper.reset) Z.helper.reset();
          draw();
        })));
      });
      box.appendChild(hid);
    }
    return box;
  }

  function tabReleases() {
    var v = inputText("", "e.g. 0.9.5.1"), t = inputText("", "Short title"), x = area("", 4), m = inputText("", "Module ids, comma separated (optional)");
    var box = h("div", { class: "adm-sec" });
    box.appendChild(h("div", { class: "card adm-card" }, h("h3", { text: "Add a release to the timeline" }), h("div", { class: "adm-two" }, field("Version", v), field("Title", t)), field("What changed", x), field("Modules it touches", m),
      h("div", { class: "acts" }, btn("Add release", "check", "btn-primary flat", function () {
        var ver = v.value.trim(), ti = t.value.trim();
        if (!/^\d+(\.\d+){1,3}$/.test(ver) || ti.length < 3) { Z.toast("Give a version like 0.9.5.1 and a title.", "bad"); return; }
        var r = { version: ver, title: ti, text: x.value.trim(), mods: m.value.split(",").map(function (s) { return s.trim(); }).filter(Boolean) };
        draft.releases.unshift(r); saveDraft();
        Z.timeline.unshift({ version: r.version, title: r.title, text: r.text, mods: r.mods });
        Z.toast("Release added to the timeline."); draw();
      }))));
    if (draft.releases.length) {
      var mine = h("div", { class: "card adm-card" }, h("h3", { text: "Releases you added" }));
      draft.releases.forEach(function (r, i) {
        mine.appendChild(h("div", { class: "adm-mini" }, h("b", { text: r.version + " – " + r.title }), btn("Remove", "close", "btn-danger", function () {
          draft.releases.splice(i, 1); saveDraft();
          Z.timeline = Z.timeline.filter(function (e) { return !(e.version === r.version && e.title === r.title); });
          draw();
        })));
      });
      box.appendChild(mine);
    }
    return box;
  }

  function tabLog() {
    var log = (Z.helperLog && Z.helperLog.read()) || [];
    var box = h("div", { class: "adm-sec" });
    var misses = log.filter(function (e) { return e.kind === "miss"; }).length;
    box.appendChild(h("p", { class: "muted", text: "Questions asked in the helper on this browser. \"miss\" means it had no good answer. Use one as the start of a new FAQ entry. " + log.length + " saved, " + misses + " unanswered." }));
    var card = h("div", { class: "card adm-card" });
    log.slice().reverse().slice(0, 80).forEach(function (e) {
      card.appendChild(h("div", { class: "adm-mini" },
        h("span", { class: "adm-tag " + (e.kind === "miss" ? "bad" : "") , text: e.kind || "ask" }),
        h("b", { text: e.q }),
        btn("Make FAQ", "send", "btn-outline", function () { faqPrefill = e.q; tab = "faq"; draw(); })));
    });
    if (!log.length) card.appendChild(h("p", { class: "muted", text: "Nothing yet. Ask the helper something and it shows up here." }));
    box.appendChild(card);
    if (log.length) box.appendChild(h("div", { class: "acts" }, btn("Clear the log", "close", "btn-danger", function () { if (Z.helperLog) Z.helperLog.write([]); draw(); })));
    return box;
  }

  /* ---------------------------------------------------------------- export / import */
  function exportDraft() {
    var d = clone(draft);
    if (!d.banner) delete d.banner;
    if (!d.sha256) delete d.sha256;
    var json = JSON.stringify(d, null, 2) + "\n";
    var a = h("a", { href: URL.createObjectURL(new Blob([json], { type: "application/json" })), download: "overrides.json" });
    document.body.appendChild(a); a.click();
    setTimeout(function () { URL.revokeObjectURL(a.href); a.remove(); }, 500);
    Z.toast("overrides.json saved. Publish it next to index.html.");
  }
  function importPick() {
    var f = h("input", { type: "file", accept: ".json,application/json", style: "display:none" });
    f.addEventListener("change", function () {
      var file = f.files && f.files[0];
      if (!file || file.size > 600000) { Z.toast("Choose an overrides.json file under 600 KB.", "bad"); return; }
      var r = new FileReader();
      r.onload = function () {
        try { draft = normalise(JSON.parse(String(r.result))); saveDraft(); Z.toast("Imported. Reloading…"); setTimeout(function () { location.reload(); }, 500); }
        catch (e) { Z.toast("That file is not valid JSON.", "bad"); }
      };
      r.readAsText(file);
    });
    document.body.appendChild(f); f.click(); setTimeout(function () { f.remove(); }, 1000);
  }

  /* ---------------------------------------------------------------- the page */
  var view = null;
  function draw() {
    if (!view) return;
    clear(view);
    var tabs = h("div", { class: "adm-tabs", role: "tablist" }, TABS.map(function (t) {
      var b = h("button", { class: "adm-tab" + (tab === t[0] ? " on" : ""), type: "button", role: "tab", "aria-selected": tab === t[0] ? "true" : "false" }, icon(t[2], "small"), t[1]);
      b.addEventListener("click", function () { tab = t[0]; draw(); });
      return b;
    }));
    view.appendChild(tabs);
    var body = tab === "text" ? tabText() : tab === "links" ? tabLinks() : tab === "banner" ? tabBanner() : tab === "faq" ? tabFaq() : tab === "releases" ? tabReleases() : tab === "log" ? tabLog() : tabOverview();
    view.appendChild(body);
    Z.reveal && Z.reveal(view);
  }

  Z.pages.admin = function () {
    if (!active()) {
      return h("div", { class: "wrap page" },
        h("div", { class: "card adm-gate" },
          h("h1", { class: "sec", text: "Sign in required" }),
          h("p", { class: "muted", text: "This area is only for the site administrator." }),
          h("div", { class: "acts" }, h("a", { class: "btn btn-primary flat", href: "/account" }, icon("user", "small"), "Go to sign in"), h("a", { class: "btn btn-outline", href: "/" }, "Back to the home page"))));
    }
    touch();
    view = h("div", { class: "adm-body" });
    var wrap = h("div", { class: "wrap page adm" },
      h("div", { class: "eyebrow", text: "Administration" }),
      h("h1", { class: "title", text: "Site administration" }),
      h("p", { class: "lead", text: "Edit the site, the FAQ and the helper. Changes are a draft until you publish them." }),
      h("div", { class: "acts" },
        btn("Export overrides.json", "download", "btn-primary flat", exportDraft),
        btn("Edit text on the page", "wand", "btn-outline", function () { setEdit(true); Z.go("/"); }),
        btn("Sign out", "logout", "btn-outline", logout)),
      view);
    draw();
    return wrap;
  };

  /* ---------------------------------------------------------------- on-page editing and the admin pill */
  function applyEditable() {
    var els = document.querySelectorAll("[data-edit]");
    for (var i = 0; i < els.length; i++) {
      if (editing) { els[i].setAttribute("contenteditable", "true"); els[i].setAttribute("spellcheck", "true"); }
      else { els[i].removeAttribute("contenteditable"); els[i].removeAttribute("spellcheck"); }
    }
  }
  function setEdit(on) {
    editing = !!on;
    document.documentElement.classList.toggle("adm-edit", editing);
    applyEditable();
    paintPill();
    if (editing) Z.toast("Edit mode: click any glowing text and type. It saves to your draft.");
  }
  document.addEventListener("focusout", function (e) {
    var el = e.target;
    if (!editing || !el || !el.getAttribute || !el.getAttribute("data-edit")) return;
    var key = el.getAttribute("data-edit");
    var v = el.textContent.replace(/\s+/g, " ").trim();
    if (v !== String(getPath(Z.content, key) || "")) setContent(key, v);
  });
  document.addEventListener("paste", function (e) {
    var el = e.target;
    if (!editing || !el || !el.closest || !el.closest("[data-edit]")) return;
    e.preventDefault();
    var t = (e.clipboardData && e.clipboardData.getData("text/plain")) || "";
    document.execCommand("insertText", false, t.replace(/\s+/g, " "));
  });
  document.addEventListener("keydown", function (e) {
    if (editing && e.key === "Enter" && e.target.getAttribute && e.target.getAttribute("data-edit")) { e.preventDefault(); e.target.blur(); }
  });

  var pill = null;
  function paintPill() {
    if (!active()) { if (pill) { pill.remove(); pill = null; } return; }
    if (!pill) { pill = h("div", { class: "adm-pill", id: "admPill", role: "region", "aria-label": "Administrator tools" }); document.body.appendChild(pill); }
    clear(pill);
    pill.appendChild(h("span", { class: "adm-dot", title: "Signed in as administrator" }));
    pill.appendChild(h("a", { href: "/admin" }, icon("gear", "small"), "Panel"));
    var e = h("button", { type: "button", class: editing ? "on" : "" }, icon("wand", "small"), editing ? "Done editing" : "Edit page");
    e.addEventListener("click", function () { setEdit(!editing); });
    pill.appendChild(e);
    var x = h("button", { type: "button", title: "Sign out" }, icon("logout", "small"));
    x.addEventListener("click", logout);
    pill.appendChild(x);
  }
  function syncNav() {
    var menu = $("#moreMenu");
    if (!menu) return;
    var have = $("#moreAdmin");
    if (active() && !have) menu.appendChild(h("a", { href: "/admin", id: "moreAdmin", "data-page": "admin", "data-link": "", role: "menuitem" }, icon("gear"), h("span", {}, h("b", { text: "Administration" }), h("small", { text: "Edit the site" }))));
    if (!active() && have) have.remove();
  }

  function boot() {
    if (!active()) return;
    if (!draft) { loadDraft(); applyDraft(); }
    Z.renderBanner();
    paintPill(); syncNav();
  }

  Z.adminActive = active;
  Z.admin = { login: login, logout: logout, active: active };
  Z.hooks.data.push(function () { if (active()) { loadDraft(); applyDraft(); Z.renderBanner(); } });
  Z.hooks.page.push(function () { if (active()) { touch(); paintPill(); syncNav(); applyEditable(); } else if (editing) { editing = false; } });
  Z.ready.push(function () { if (active()) { paintPill(); syncNav(); } });
})();
