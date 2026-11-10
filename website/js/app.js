/* Zeus Client website: shell, router, motion and the public pages. No inline scripts (the page's CSP forbids them). */
(function () {
  "use strict";

  var NS = "http://www.w3.org/2000/svg";
  var Z = (window.Z = {
    site: { version: "", minecraft: "1.21.11", local: false, moduleCount: 0, links: {}, url: "" },
    content: {},
    features: null,
    help: { sections: [], quick: [], faq: [] },
    timeline: [],
    pages: {},
    ready: [],
    token: "",
  });

  /* Motion: "full" (default) or "calm". Calm is chosen with the switch in the footer and is remembered. It is
     not tied to the system setting on purpose: a system that reports "reduce motion" for every page used to
     switch off the typing animation and every other effect without any way to turn it back on. */
  var REDUCED = false;
  try { REDUCED = localStorage.getItem("zeus.motion") === "calm"; } catch (e) { /* storage blocked */ }
  Z.reduced = REDUCED;
  document.documentElement.classList.add(REDUCED ? "calm" : "motion");
  Z.hooks = { data: [], page: [] };

  /* ---------------- tiny DOM helpers ---------------- */
  function h(tag, attrs) {
    var node = document.createElement(tag);
    if (attrs) {
      for (var k in attrs) {
        var v = attrs[k];
        if (v === undefined || v === null || v === false) continue;
        if (k === "class") node.className = v;
        else if (k === "text") node.textContent = v;
        else if (k.slice(0, 2) === "on" && typeof v === "function") node.addEventListener(k.slice(2), v);
        else node.setAttribute(k, v === true ? "" : v);
      }
    }
    for (var i = 2; i < arguments.length; i++) add(node, arguments[i]);
    return node;
  }
  function add(node, c) {
    if (c === undefined || c === null || c === false) return;
    if (Array.isArray(c)) { c.forEach(function (x) { add(node, x); }); return; }
    node.appendChild(c.nodeType ? c : document.createTextNode(String(c)));
  }
  function icon(name, cls) {
    if (!document.getElementById("i-" + name)) name = "info";
    var s = document.createElementNS(NS, "svg");
    s.setAttribute("class", "i" + (cls ? " " + cls : ""));
    s.setAttribute("aria-hidden", "true");
    var u = document.createElementNS(NS, "use");
    u.setAttribute("href", "#i-" + name);
    s.appendChild(u);
    return s;
  }
  function $(sel, root) { return (root || document).querySelector(sel); }
  function clear(node) { while (node.firstChild) node.removeChild(node.firstChild); }
  function safeUrl(u) { return typeof u === "string" && /^https?:\/\//i.test(u) ? u : ""; }
  function debounce(fn, ms) {
    var t;
    return function () { var a = arguments, s = this; clearTimeout(t); t = setTimeout(function () { fn.apply(s, a); }, ms); };
  }
  function store(key, value) {
    try {
      if (value === undefined) return localStorage.getItem(key);
      if (value === null) localStorage.removeItem(key); else localStorage.setItem(key, value);
    } catch (e) { /* storage can be blocked; the page works without it */ }
    return null;
  }
  function cap(s) { return s.charAt(0).toUpperCase() + s.slice(1); }
  function safeColor(c) { return /^#[0-9a-fA-F]{3,8}$/.test(c || "") ? c : "#4ea4ff"; }

  Z.setMotion = function (mode) {
    REDUCED = mode === "calm";
    Z.reduced = REDUCED;
    store("zeus.motion", REDUCED ? "calm" : "full");
    var c = document.documentElement.classList;
    c.toggle("calm", REDUCED); c.toggle("motion", !REDUCED);
    if (Z.fxSync) Z.fxSync();
    document.dispatchEvent(new CustomEvent("zeus:motion", { detail: { calm: REDUCED } }));
  };
  Z.h = h; Z.icon = icon; Z.$ = $; Z.clear = clear; Z.safeUrl = safeUrl; Z.debounce = debounce; Z.store = store;

  function toast(msg, kind) {
    var host = $("#toasts");
    var t = h("div", { class: "toast " + (kind || ""), text: msg });
    host.appendChild(t);
    setTimeout(function () {
      t.classList.add("out");
      setTimeout(function () { if (t.parentNode) t.parentNode.removeChild(t); }, 320);
    }, kind === "bad" ? 6500 : 3600);
  }
  Z.toast = toast;

  /* ---------------- data ---------------- */
  function getJson(url) {
    return fetch(url, { cache: "no-cache" }).then(function (r) {
      if (!r.ok) throw new Error(String(r.status));
      return r.json();
    });
  }

  Z.getJson = getJson;

  Z.api = function (method, path, body) {
    var headers = { Accept: "application/json" };
    if (Z.token) headers.Authorization = "Bearer " + Z.token;
    var opts = { method: method, headers: headers, cache: "no-store" };
    if (body !== undefined) { headers["Content-Type"] = "application/json"; opts.body = JSON.stringify(body); }
    return fetch(path, opts).then(function (r) {
      return r.text().then(function (text) {
        var data = {};
        try { data = text ? JSON.parse(text) : {}; } catch (e) { data = { error: "Unexpected reply" }; }
        if (!r.ok) {
          var err = new Error(data.error || ("Request failed (" + r.status + ")"));
          err.status = r.status;
          throw err;
        }
        return data;
      });
    });
  };

  function loadData() {
    var content = getJson("/site.json").catch(function () { return {}; });
    // A published build sets "hosted": true in site.json and never asks for the in-game API.
    var split = content.then(function (c) {
      var hosted = !!(c && c.hosted);
      var live = hosted ? Promise.resolve(null) : getJson("/api/site").catch(function () { return null; });
      var feats = (hosted ? getJson("/features.json") : getJson("/api/features").catch(function () { return getJson("/features.json"); })).catch(function () { return { categories: [], modules: [] }; });
      return Promise.all([live, feats]);
    });
    var help = getJson("/help.json").catch(function () { return { sections: [], quick: [], faq: [] }; });
    var tl = getJson("/timeline.json").catch(function () { return []; });
    var over = getJson("/overrides.json").catch(function () { return null; });
    getJson("/media.json").then(function (m) { Z.media = (m && m.modules) || {}; }, function () { Z.media = {}; });
    var live = split.then(function (x) { return x[0]; });
    var feats = split.then(function (x) { return x[1]; });
    return Promise.all([content, live, feats, help, tl, over]).then(function (r) {
      Z.content = r[0] || {};
      var base = Z.content.defaults || {};
      var live = r[1];
      Z.site.local = !!live;
      Z.site.version = (live && live.version) || base.version || "";
      Z.site.minecraft = (live && live.minecraft) || base.minecraft || "1.21.11";
      Z.site.moduleCount = (live && live.moduleCount) || (r[2].modules || []).length;
      Z.site.name = base.siteName || "Zeus Client";
      Z.site.url = (/^https?:\/\//i.test(base.siteUrl || "") ? base.siteUrl.replace(/\/+$/, "") : "") || location.origin;
      var L = Z.site.links;
      L.discord = safeUrl(live && live.discordUrl) || safeUrl(base.discordUrl);
      L.download = safeUrl(live && live.downloadUrl) || safeUrl(base.downloadUrl);
      L.youtube = safeUrl(live && live.youtubeUrl) || safeUrl(base.youtubeUrl);
      L.twitter = safeUrl(live && live.twitterUrl) || safeUrl(base.twitterUrl);
      L.tutorial = safeUrl(live && live.tutorialUrl) || safeUrl(base.tutorialUrl);
      L.github = safeUrl(base.githubUrl);
      L.contact = /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(base.contactEmail || "") ? base.contactEmail : "";
      Z.sha256 = /^[0-9a-fA-F]{64}$/.test(base.sha256 || "") ? base.sha256.toLowerCase() : "";
      Z.features = r[2];
      Z.help = r[3] || Z.help;
      Z.timeline = Array.isArray(r[4]) ? r[4] : [];
      Z.overrides = r[5] && typeof r[5] === "object" ? r[5] : null;
      if (Z.overrides) Z.applyOverrides(Z.overrides);
      Z.hooks.data.forEach(function (fn) { try { fn(); } catch (e) { if (window.console) console.error(e); } });
    });
  }

  /* ---------------- content overrides (overrides.json, written by the admin panel) ---------------- */
  function setPath(root, path, value) {
    var parts = String(path).split(".");
    for (var i = 0; i < parts.length; i++) if (parts[i] === "__proto__" || parts[i] === "constructor" || parts[i] === "prototype") return;
    var o = root;
    for (var j = 0; j < parts.length - 1; j++) {
      var k = parts[j];
      if (o[k] === undefined || o[k] === null || typeof o[k] !== "object") o[k] = /^\d+$/.test(parts[j + 1]) ? [] : {};
      o = o[k];
    }
    o[parts[parts.length - 1]] = value;
  }
  Z.setPath = setPath;
  Z.applyOverrides = function (ov) {
    if (!ov) return;
    var C = Z.content;
    var text = ov.content || {};
    Object.keys(text).forEach(function (k) { if (typeof text[k] === "string") setPath(C, k, text[k]); });
    var links = ov.links || {}, L = Z.site.links;
    Object.keys(links).forEach(function (k) { if (typeof links[k] === "string") L[k] = safeUrl(links[k]); });
    if (/^[0-9a-fA-F]{64}$/.test(ov.sha256 || "")) Z.sha256 = ov.sha256.toLowerCase();
    if (ov.banner && typeof ov.banner === "object") C.banner = ov.banner;
    var F = Z.help && Z.help.faq;
    if (F && ov.faq) {
      var hide = ov.faq.hide || [];
      if (hide.length) Z.help.faq = F = F.filter(function (f) { return hide.indexOf(f.orig || f.q) < 0; });
      Object.keys(ov.faq.edit || {}).forEach(function (q) {
        F.forEach(function (f) { if ((f.orig || f.q) === q) { var e = ov.faq.edit[q]; f.orig = f.orig || f.q; if (e.q) f.q = String(e.q); if (e.a) { f.a = String(e.a); f.alts = []; } } });
      });
      (ov.faq.add || []).forEach(function (n) {
        if (n && n.q && n.a) F.push({ q: String(n.q), a: String(n.a), cat: n.cat || "About Zeus", keys: n.keys || "", alts: [], links: [] });
      });
    }
    if (Array.isArray(ov.releases) && ov.releases.length) {
      ov.releases.slice().reverse().forEach(function (r) { if (r && r.version && r.title) Z.timeline.unshift({ version: String(r.version), title: String(r.title), text: String(r.text || ""), mods: r.mods || [] }); });
      if (Z.timeline[0] && /^\d+(\.\d+)*$/.test(Z.timeline[0].version)) Z.site.version = Z.timeline[0].version;
    }
  };

  /* ---------------- animated emblem ---------------- */
  /* Layers (back to front): halo, blurred glow copy, emblem, masked light sweep, one-shot shock ring.
     The emblem itself never changes shape; only light moves across it. */
  Z.logo = function (size) {
    var img = h("img", { class: "zl-img", src: "/img/emblem.png", alt: "", width: 250, height: 235, decoding: "async" });
    var float = h("span", { class: "zl-float" }, h("span", { class: "zl-halo" }), h("span", { class: "zl-glow" }), img, h("span", { class: "zl-sheen" }), h("span", { class: "zl-ring" }));
    var tilt = h("span", { class: "zl-tilt" }, float);
    var el = h("span", { class: "zlogo", style: typeof size === "number" ? "--zs:" + size + "px" : null, "aria-hidden": "true" }, tilt);
    el._tilt = tilt;
    return el;
  };

  /* ---------------- ambient embers + emitters ---------------- */
  var fx = { cv: null, ctx: null, w: 0, h: 0, dpr: 1, parts: [], emitters: [], running: false, last: 0 };
  Z.emit = function (el, on) {
    fx.emitters = fx.emitters.filter(function (e) { return e !== el; });
    if (on) fx.emitters.push(el);
  };
  function fxResize() {
    var cv = fx.cv;
    fx.dpr = Math.min(window.devicePixelRatio || 1, 1.5);
    fx.w = window.innerWidth; fx.h = window.innerHeight;
    cv.width = Math.round(fx.w * fx.dpr); cv.height = Math.round(fx.h * fx.dpr);
  }
  function spawn(x, y, strong) {
    fx.parts.push({
      x: x, y: y,
      vx: (Math.random() - 0.5) * (strong ? 22 : 10),
      vy: -(strong ? 20 + Math.random() * 34 : 6 + Math.random() * 16),
      r: strong ? 0.8 + Math.random() * 1.6 : 0.6 + Math.random() * 1.5,
      life: 0, max: strong ? 1.6 + Math.random() * 1.6 : 7 + Math.random() * 7,
      ph: Math.random() * 6.28,
    });
  }
  function fxFrame(t) {
    if (!fx.running) return;
    requestAnimationFrame(fxFrame);
    var dt = Math.min(0.05, (t - fx.last) / 1000 || 0.016);
    fx.last = t;
    var ctx = fx.ctx;
    ctx.setTransform(fx.dpr, 0, 0, fx.dpr, 0, 0);
    ctx.clearRect(0, 0, fx.w, fx.h);
    if (fx.parts.length < 46 && Math.random() < dt * 5) spawn(Math.random() * fx.w, fx.h + 8, false);
    fx.emitters.forEach(function (el) {
      if (!el.isConnected) return;
      var r = el.getBoundingClientRect();
      if (r.bottom < 0 || r.top > fx.h) return;
      if (Math.random() < dt * 14) spawn(r.left + r.width * (0.2 + Math.random() * 0.6), r.top + r.height * (0.25 + Math.random() * 0.6), true);
    });
    ctx.globalCompositeOperation = "lighter";
    for (var i = fx.parts.length - 1; i >= 0; i--) {
      var p = fx.parts[i];
      p.life += dt;
      if (p.life >= p.max || p.y < -20) { fx.parts.splice(i, 1); continue; }
      p.x += (p.vx + Math.sin(p.life * 1.3 + p.ph) * 6) * dt;
      p.y += p.vy * dt;
      var k = p.life / p.max;
      var a = Math.sin(Math.min(1, k) * Math.PI) * 0.8;
      var g = ctx.createRadialGradient(p.x, p.y, 0, p.x, p.y, p.r * 5);
      g.addColorStop(0, "rgba(150,205,255," + a.toFixed(3) + ")");
      g.addColorStop(0.35, "rgba(55,135,255," + (a * 0.45).toFixed(3) + ")");
      g.addColorStop(1, "rgba(30,90,255,0)");
      ctx.fillStyle = g;
      ctx.beginPath(); ctx.arc(p.x, p.y, p.r * 5, 0, 6.2832); ctx.fill();
    }
  }
  function fxStart() {
    fx.cv = $("#fx");
    if (!fx.cv) return;
    fx.ctx = fx.cv.getContext("2d");
    if (!fx.ctx) return;
    fxResize();
    window.addEventListener("resize", debounce(fxResize, 150));
    for (var i = 0; i < 22; i++) { spawn(Math.random() * fx.w, Math.random() * fx.h, false); fx.parts[i].life = Math.random() * 6; }
    function go() { if (fx.running || REDUCED) return; fx.running = true; fx.last = performance.now(); requestAnimationFrame(fxFrame); }
    document.addEventListener("visibilitychange", function () { if (document.hidden) fx.running = false; else go(); });
    Z.fxSync = function () { fx.cv.style.display = REDUCED ? "none" : ""; if (REDUCED) fx.running = false; else go(); };
    Z.fxSync();
  }

  /* ---------------- motion helpers ---------------- */
  var revealObs = null;
  Z.reveal = function (root) {
    var nodes = (root || document).querySelectorAll("[data-reveal]:not(.in)");
    if (!nodes.length) return;
    if (REDUCED || !("IntersectionObserver" in window)) { nodes.forEach(function (n) { n.classList.add("in"); }); return; }
    if (!revealObs) {
      revealObs = new IntersectionObserver(function (es) {
        es.forEach(function (e) {
          if (e.isIntersecting) { e.target.classList.add("in"); revealObs.unobserve(e.target); }
        });
      }, { rootMargin: "0px 0px -6% 0px", threshold: 0.01 });
    }
    nodes.forEach(function (n) { revealObs.observe(n); });
  };
  function stagger(nodes, step, base) {
    for (var i = 0; i < nodes.length; i++) {
      nodes[i].setAttribute("data-reveal", "");
      nodes[i].style.setProperty("--d", Math.min(i, 12) * (step || 60) + (base || 0) + "ms");
    }
  }
  Z.stagger = stagger;

  Z.countUp = function (el, to, suffix) {
    suffix = suffix || "";
    if (REDUCED || !to) { el.textContent = to + suffix; return; }
    var start = performance.now(), dur = 1100;
    (function step(t) {
      var k = Math.min(1, (t - start) / dur);
      var e = 1 - Math.pow(1 - k, 3);
      el.textContent = Math.round(to * e) + suffix;
      if (k < 1 && el.isConnected) requestAnimationFrame(step);
    })(start);
  };

  function wordmarkLetters(text, offset) {
    var out = document.createDocumentFragment();
    for (var i = 0; i < text.length; i++) {
      var s = h("span", { class: "wm-l", text: text[i] });
      s.style.setProperty("--i", String(i + (offset || 0)));
      out.appendChild(s);
    }
    return out;
  }

  /* spotlight on cards */
  document.addEventListener("pointermove", function (e) {
    var t = e.target && e.target.closest && e.target.closest(".card,.mod,.mc,.hq");
    if (!t) return;
    var r = t.getBoundingClientRect();
    t.style.setProperty("--mx", (e.clientX - r.left) + "px");
    t.style.setProperty("--my", (e.clientY - r.top) + "px");
  }, { passive: true });

  var progressTimer = 0;
  function progress(on) {
    var p = $("#progress");
    if (!p) return;
    clearTimeout(progressTimer);
    if (on) { p.style.transition = "none"; p.style.width = "0"; void p.offsetWidth; p.style.transition = ""; p.classList.add("on"); p.style.width = "72%"; }
    else { p.style.width = "100%"; progressTimer = setTimeout(function () { p.classList.remove("on"); setTimeout(function () { p.style.width = "0"; }, 320); }, 260); }
  }

  /* ---------------- shell ---------------- */
  /* The header shows five pages and a "More" menu. At narrow widths it collapses into a burger menu. */
  var NAV = [
    ["home", "Home", "home"],
    ["download", "Download", "download"],
    ["features", "Features", "cube"],
    ["tutorial", "Tutorial", "play"],
    ["information", "About", "info"],
  ];
  var MORE = [
    ["config", "Build a config", "sliders", "Answer a few questions"],
    ["knives", "Knife preview", "star", "Every knife and finish"],
    ["monitor", "Performance monitor", "bolt", "What your modules cost"],
    ["troubleshoot", "Troubleshooter", "wand", "Fix a problem"],
    ["source", "Source code", "code", "Read every file"],
    ["credits", "Credits", "users", "Who made Zeus"],
    ["settings", "Control center", "gear", "In-game only", true],
  ];

  function buildNav() {
    var box = $("#links");
    clear(box);
    NAV.forEach(function (n) {
      box.appendChild(h("a", { href: n[0] === "home" ? "/" : "/" + n[0], "data-page": n[0], "data-link": "" }, icon(n[2]), h("span", { text: n[1] })));
    });
    var menu = h("div", { class: "more-menu", id: "moreMenu", role: "menu" });
    MORE.forEach(function (n) {
      if (n[4] && !Z.site.local) return;
      menu.appendChild(h("a", { href: "/" + n[0], "data-page": n[0], "data-link": "", role: "menuitem" }, icon(n[2]), h("span", {}, h("b", { text: n[1] }), h("small", { text: n[3] }))));
    });
    var moreBtn = h("button", { class: "more-btn", type: "button", "aria-haspopup": "true", "aria-expanded": "false", "aria-controls": "moreMenu" }, icon("list"), h("span", { text: "More" }), icon("arrow", "small chev"));
    var more = h("div", { class: "more" }, moreBtn, menu);
    box.appendChild(more);
    box.appendChild(h("span", { class: "ind", "aria-hidden": "true" }));

    function setMore(open) {
      more.classList.toggle("open", open);
      moreBtn.setAttribute("aria-expanded", open ? "true" : "false");
    }
    moreBtn.addEventListener("click", function (e) { e.stopPropagation(); setMore(!more.classList.contains("open")); });
    document.addEventListener("click", function (e) { if (!more.contains(e.target)) setMore(false); });
    document.addEventListener("keydown", function (e) { if (e.key === "Escape") { setMore(false); document.documentElement.classList.remove("navopen"); } });
    document.addEventListener("zeus:page", function () { setMore(false); document.documentElement.classList.remove("navopen"); });

    // burger (shown by the stylesheet at narrow widths)
    var inner = $(".nav-inner");
    if (inner && !$("#burger")) {
      var burger = h("button", { class: "burger", id: "burger", type: "button", "aria-label": "Menu", "aria-expanded": "false" }, h("i"), h("i"), h("i"));
      burger.addEventListener("click", function (e) {
        e.stopPropagation();
        var on = document.documentElement.classList.toggle("navopen");
        burger.setAttribute("aria-expanded", on ? "true" : "false");
      });
      document.addEventListener("click", function (e) { if (!e.target.closest || !e.target.closest("#links, #burger")) document.documentElement.classList.remove("navopen"); });
      inner.appendChild(burger);
    }

    var brand = $(".brand");
    var old = brand && brand.querySelector("img");
    if (old) brand.replaceChild(Z.logo(), old);
    var bt = $(".brand-text b");
    if (bt) { bt.setAttribute("aria-label", "ZEUS"); }

    var d = $("#discordTop");
    if (Z.site.links.discord) { d.href = Z.site.links.discord; d.hidden = false; } else d.hidden = true;

    var foot = $("#footSocial");
    clear(foot);
    var year = new Date().getFullYear();
    $("#footText").textContent = "Zeus Client © " + year + ". All rights reserved.";
    foot.appendChild(h("a", { href: "/help", "data-link": "", "aria-label": "Help", title: "Help" }, icon("help")));
    [["github", "github", "GitHub"], ["discord", "discord", "Discord"], ["youtube", "youtube", "YouTube"], ["twitter", "twitter", "X / Twitter"]].forEach(function (s) {
      var url = Z.site.links[s[0]];
      if (!url) return;
      foot.appendChild(h("a", { href: url, target: "_blank", rel: "noopener noreferrer", "aria-label": s[2], title: s[2] }, icon(s[1])));
    });
    window.addEventListener("resize", debounce(moveIndicator, 100));
  }

  function moveIndicator() {
    var box = $("#links"), ind = box && $(".ind", box);
    var a = box && (box.querySelector(":scope > a.on") || box.querySelector(":scope > .more.on"));
    if (!ind) return;
    if (!a || !a.offsetWidth) { ind.classList.remove("on"); return; }
    ind.style.width = a.offsetWidth + "px";
    ind.style.transform = "translateX(" + a.offsetLeft + "px)";
    ind.classList.add("on");
  }

  function mark(page) {
    var inMore = false;
    document.querySelectorAll("#links a").forEach(function (a) {
      var on = a.getAttribute("data-page") === page;
      a.classList.toggle("on", on);
      if (on) { a.setAttribute("aria-current", "page"); if (a.closest(".more")) inMore = true; } else a.removeAttribute("aria-current");
    });
    var more = $("#links .more");
    if (more) more.classList.toggle("on", inMore);
    var hb = $("#helpTop");
    if (hb) hb.classList.toggle("on", page === "help");
    moveIndicator();
  }

  /* ---------------- router (History API, clean URLs) ---------------- */
  var cleanup = null;
  Z.onLeave = function (fn) { cleanup = fn; };

  var META = {
    home: ["Zeus Client – a cheat client for Minecraft 1.21.11", "Zeus Client is a Fabric cheat client for Minecraft 1.21.11 with combat, movement, ESP and base-finding modules and an in-game menu."],
    download: ["Download Zeus Client", "Download Zeus Client for Minecraft 1.21.11 on Fabric. What you need and how to install it."],
    features: ["Features – Zeus Client", "Every Zeus Client module and its settings. Search by name or filter by category."],
    tutorial: ["Tutorial – Zeus Client", "How to get started with Zeus Client: open the menu, switch modules on and place your HUD."],
    help: ["Help & FAQ – Zeus Client", "Guides, a searchable FAQ and a helper that answers questions about Zeus Client."],
    credits: ["Credits – Zeus Client", "Who made Zeus Client, and the projects it builds on."],
    information: ["Information – Zeus Client", "About Zeus Client, a few notes to read first, and every release from the first version to today."],
    source: ["Source code – Zeus Client", "Read the Zeus Client source code on this site, file by file."],
    config: ["Build a config – Zeus Client", "Answer a few yes or no questions, or pick a playstyle, and get a set of modules to start with."],
    knives: ["Knife preview – Zeus Client", "Every Zeus knife in every finish, drawn by the same code the game uses."],
    monitor: ["Performance monitor – Zeus Client", "A gauge of what a set of Zeus modules will probably cost you in FPS."],
    troubleshoot: ["Troubleshooter – Zeus Client", "Answer a few yes or no questions to find the fix for your Zeus problem."],
    account: ["Your profile – Zeus Client", "Your saved modules, configs and helper history."],
    settings: ["Control center – Zeus Client", "Control a running Zeus Client from your browser. It only works from the link the game prints."],
    admin: ["Administration – Zeus Client", "Site administration."],
  };

  function currentPage() {
    var seg = location.pathname.replace(/^\/+|\/+$/g, "").split("/")[0].toLowerCase().replace(/\.html$/, "");
    if (seg === "index" || seg === "") return "home";
    return Z.pages[seg] ? seg : "home";
  }

  function setMeta(page) {
    var m = META[page] || META.home;
    document.title = m[0];
    var d = $('meta[name="description"]');
    if (d) d.setAttribute("content", m[1]);
    var canon = $('link[rel="canonical"]');
    if (!canon) { canon = h("link", { rel: "canonical" }); document.head.appendChild(canon); }
    canon.setAttribute("href", Z.site.url + (page === "home" ? "/" : "/" + page));
  }

  var navToken = 0, lastPath = location.pathname;
  function render(page, opts) {
    opts = opts || {};
    lastPath = location.pathname;
    var token = ++navToken;
    var view = $("#view");
    var swap = function () {
      if (token !== navToken) return;
      if (cleanup) { try { cleanup(); } catch (e) { /* ignore */ } cleanup = null; }
      Z.emit(null, false);
      fx.emitters = [];
      clear(view);
      mark(page);
      setMeta(page);
      document.documentElement.setAttribute("data-pg", page);
      try {
        view.appendChild(Z.pages[page]());
      } catch (e) {
        view.appendChild(h("div", { class: "wrap page" }, h("div", { class: "card" }, h("h2", { class: "sec", text: "Something went wrong" }), h("p", { class: "muted", text: String(e && e.message || e) }))));
        if (window.console) console.error(e);
      }
      view.classList.remove("leaving");
      view.classList.remove("pgin");
      if (!REDUCED) {
        void view.offsetWidth;
        view.classList.add("pgin");
        clearTimeout(view._pgt);
        view._pgt = setTimeout(function () { view.classList.remove("pgin"); }, 1300);
      }
      Z.reveal(view);
      if (opts.hash) {
        var target = document.getElementById(opts.hash);
        if (target) setTimeout(function () { target.scrollIntoView({ behavior: REDUCED ? "auto" : "smooth", block: "start" }); }, 60);
        else window.scrollTo(0, 0);
      } else if (!opts.keepScroll) window.scrollTo(0, 0);
      progress(false);
      Z.hooks.page.forEach(function (fn) { try { fn(page, opts); } catch (e) { if (window.console) console.error(e); } });
      document.dispatchEvent(new CustomEvent("zeus:page", { detail: { page: page } }));
    };
    if (opts.first || REDUCED) { swap(); return; }
    progress(true);
    view.classList.add("leaving");
    setTimeout(swap, 150);
  }

  Z.rerender = function () { render(currentPage(), { first: true, keepScroll: true }); };

  Z.go = function (href, replace) {
    var u;
    try { u = new URL(href, location.origin); } catch (e) { return; }
    if (u.origin !== location.origin) { location.href = href; return; }
    var same = u.pathname === location.pathname;
    if (same && u.hash && u.search === location.search) {
      history.pushState(null, "", u.pathname + u.search + u.hash);
      var el = document.getElementById(u.hash.slice(1));
      if (el) el.scrollIntoView({ behavior: REDUCED ? "auto" : "smooth", block: "start" });
      return;
    }
    if (same && !u.hash && u.search !== location.search) {
      history.pushState(null, "", u.pathname + u.search);
      document.dispatchEvent(new CustomEvent("zeus:query"));
      return;
    }
    if (same && !u.hash) { window.scrollTo({ top: 0, behavior: REDUCED ? "auto" : "smooth" }); return; }
    history[replace ? "replaceState" : "pushState"](null, "", u.pathname + u.search + u.hash);
    render(currentPage(), { hash: u.hash.slice(1) });
  };

  function intercept(e) {
    if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
    var a = e.target.closest && e.target.closest("a[href]");
    if (!a || a.target === "_blank" || a.hasAttribute("download")) return;
    var href = a.getAttribute("href");
    if (!href || href.charAt(0) !== "/" || href.charAt(1) === "/") return;
    var seg = href.replace(/^\/+/, "").split(/[?#]/)[0].split("/")[0].toLowerCase();
    if (seg !== "" && !Z.pages[seg]) return;
    e.preventDefault();
    Z.go(href);
    if (Z.helper && Z.helper.isFloatingOpen && Z.helper.isFloatingOpen() && a.closest(".hlp")) { /* keep the chat open while navigating from an answer */ }
  }

  /* ---------------- shared pieces ---------------- */
  function extBtn(cls, url, children) {
    if (url) return h("a", { class: cls, href: url, target: "_blank", rel: "noopener noreferrer" }, children);
    return h("a", { class: cls, href: "/download" }, children);
  }
  Z.extBtn = extBtn;

  function pageHead(eyebrow, title, lead) {
    var items = [h("div", { class: "eyebrow", text: eyebrow }), h("h1", { class: "title", text: title }), lead ? h("p", { class: "lead", text: lead }) : null];
    stagger(items.filter(Boolean), 80);
    return items;
  }
  Z.pageHead = pageHead;

  function versionLabel() { return Z.site.version ? "v" + Z.site.version : ""; }

  function askLink(text, question, cls) {
    var b = h("button", { class: cls || "btn btn-outline sm", type: "button" }, icon("chat", "small"), text);
    b.addEventListener("click", function () { if (Z.helper) Z.helper.ask(question); });
    return b;
  }

  /* ---------------- Home ---------------- */
  Z.pages.home = function () {
    var C = Z.content;
    var L = Z.site.links;
    var tagline = C.tagline || "A cheat client for Minecraft 1.21.11.";
    var sub = C.subtitle || "Designed for power, customization and an unbeatable edge.";

    var logo = Z.logo();
    var art = h("div", { class: "hero-art" }, h("img", { src: "/img/hero.jpg", alt: "", decoding: "async" }));
    var wm = h("div", { class: "wordmark" }, h("b", { "aria-label": "ZEUS" }, wordmarkLetters("ZEUS", 0)), h("i", { "aria-label": "CLIENT" }, wordmarkLetters("CLIENT", 4)));
    var hero = h("section", { class: "hero" },
      art,
      h("div", { class: "wrap" },
        h("div", { class: "hero-in" },
          h("div", { class: "hero-id" }, logo, h("div", { class: "hero-rule" }), wm),
          h("div", { class: "tagline" }, tagline.replace(/\.$/, "").replace("cheat client", ""), h("em", { text: "cheat client" }), "."),
          h("p", { class: "lead", "data-edit": "subtitle", text: sub }),
          h("div", { class: "cta" },
            extBtn("btn btn-primary", L.download, [icon("download"), h("span", { class: "btn-two" }, h("b", { text: "Download Now" }), h("small", { text: "Minecraft " + Z.site.minecraft + " · Fabric" }))]),
            h("a", { class: "btn btn-outline", href: "/features" }, icon("cube"), h("span", { class: "btn-two" }, h("b", { text: "View Features" }), h("small", { text: (Z.site.moduleCount || "") + " modules" })))))));

    // light follows the pointer: parallax on the artwork, gentle 3D tilt on the emblem
    if (!REDUCED) {
      var raf = 0, px = 0, py = 0;
      hero.addEventListener("pointermove", function (e) {
        var r = hero.getBoundingClientRect();
        px = (e.clientX - r.left) / r.width - 0.5;
        py = (e.clientY - r.top) / r.height - 0.5;
        if (raf) return;
        raf = requestAnimationFrame(function () {
          raf = 0;
          hero.style.setProperty("--px", px.toFixed(3));
          hero.style.setProperty("--py", py.toFixed(3));
          var lr = logo.getBoundingClientRect();
          var dx = (e.clientX - (lr.left + lr.width / 2)) / Math.max(lr.width, 1);
          var dy = (e.clientY - (lr.top + lr.height / 2)) / Math.max(lr.height, 1);
          dx = Math.max(-1.2, Math.min(1.2, dx)); dy = Math.max(-1.2, Math.min(1.2, dy));
          logo._tilt.style.setProperty("--ry", (dx * 9).toFixed(2) + "deg");
          logo._tilt.style.setProperty("--rx", (-dy * 7).toFixed(2) + "deg");
        });
      });
      hero.addEventListener("pointerleave", function () {
        hero.style.setProperty("--px", "0"); hero.style.setProperty("--py", "0");
        logo._tilt.style.setProperty("--ry", "0deg"); logo._tilt.style.setProperty("--rx", "0deg");
      });
      Z.emit(logo, true);
    }

    var strip = h("nav", { class: "strip", "aria-label": "Highlights" });
    (C.strip || []).forEach(function (s) {
      strip.appendChild(h("a", { href: "/" + (s.page || "features") }, icon(s.icon || "bolt"), h("span", {}, h("b", { text: s.title }), h("small", { text: s.sub }))));
    });
    stagger(strip.children, 70);

    function ticks(list) {
      return h("ul", { class: "ticks" }, (list || []).map(function (t) { return h("li", {}, icon("check", "small"), h("span", { text: t })); }));
    }
    var HC = C.home || {};
    var about = h("div", { class: "card hc about" },
      h("div", { class: "card-title" }, icon("shield"), "About Zeus Client"),
      h("p", { "data-edit": "about", text: C.about || "Zeus is a Fabric cheat client for Minecraft 1.21.11." }),
      h("div", { class: "mini-stats" }, [
        [Z.site.moduleCount || "170+", "modules"],
        [((Z.features && Z.features.categories) || []).length || "9", "categories"],
        [String(((Z.features && Z.features.modules) || []).reduce(function (n, m) { return n + (m.settings || []).filter(function (x) { return x.type !== "info"; }).length; }, 0) || "700+"), "settings"],
      ].map(function (t) { return h("div", {}, h("b", { text: String(t[0]) }), h("small", { text: t[1] })); })),
      ticks(HC.about2),
      h("div", { class: "card-foot" }, h("a", { class: "btn btn-outline sm", href: "/information" }, "Learn more", icon("arrow", "small"))),
      h("img", { class: "bigz", src: "/img/emblem.png", alt: "" }));

    var upd = h("div", { class: "card hc upd-card" },
      h("div", { class: "upd-head" }, h("div", { class: "card-title" }, icon("calendar"), "Latest update"), h("a", { href: "/information#timeline" }, "Full timeline", icon("arrow", "small"))));
    var latest = Z.timeline[0];
    var modById = {};
    ((Z.features && Z.features.modules) || []).forEach(function (m) { modById[m.id] = m; });
    if (latest) {
      var chips = (latest.mods || []).filter(function (id) { return modById[id]; }).slice(0, 6);
      upd.appendChild(h("div", { class: "upd" },
        h("div", { class: "thumb" }, h("img", { src: "/img/emblem.png", alt: "" })),
        h("div", {}, h("h3", {}, latest.title || "Update", " ", h("span", { class: "pill", text: "v" + latest.version })), h("p", { class: "muted clamp", text: latest.text || "" }))));
      if (chips.length) upd.appendChild(h("div", { class: "tl-mods hc-chips" }, h("span", { class: "tl-lab", text: "New and changed" }), chips.map(function (id) {
        return h("a", { class: "tl-chip", href: "/features?m=" + encodeURIComponent(id) }, modById[id].name);
      })));
      upd.appendChild(h("div", { class: "card-foot" }, h("a", { class: "btn btn-outline sm", href: "/information#timeline" }, "Read the release notes", icon("arrow", "small"))));
    } else upd.appendChild(h("p", { class: "muted", text: "No updates yet." }));

    var disc = h("div", { class: "card hc disc" },
      h("div", { class: "row" }, icon(L.discord ? "discord" : "chat"), h("div", {}, h("h3", { text: L.discord ? "Join our Discord" : "Questions?" }), h("p", { text: L.discord ? "Ask for help, share configs and report bugs." : "Open the Help centre or ask the helper. It knows the modules, the install steps and the safety notes." }))),
      ticks(HC.disc),
      h("div", { class: "card-foot" }, L.discord ? h("a", { class: "btn btn-primary flat", href: L.discord, target: "_blank", rel: "noopener noreferrer" }, "Join Discord", icon("arrow", "small")) :
        h("a", { class: "btn btn-primary flat", href: "/help" }, "Open Help", icon("arrow", "small"))));

    var cards = h("div", { class: "cards3" }, about, upd, disc);
    stagger(cards.children, 110);

    // start-here tools
    var tools = h("div", { class: "tool-cards home-tools" },
      [["sliders", "Build a config", "Answer a few questions and get a set of modules for how you play.", "/config"],
       ["wand", "Troubleshooter", "Something not working? A few yes or no questions lead to the fix.", "/troubleshoot"],
       ["book", "Learn the basics", "Open the menu, switch modules on, place your HUD.", "/tutorial"],
       ["code", "Read the source", "Every file of Zeus is on this site, so you can read it before you run it.", "/source"]].map(function (t) {
        return h("a", { class: "tool-card", href: t[3] }, h("span", { class: "tc-i" }, icon(t[0])), h("span", { class: "tc-t" }, h("b", { text: t[1] }), h("small", { text: t[2] })), icon("arrow", "small"));
      }));
    stagger(tools.children, 80);

    // popular modules
    var popIds = ["killaura", "crystalaura", "elytrawaypoint", "mediabasefinder", "basefinder", "xray", "esp", "scaffold", "flight", "fullbright", "autototem", "freecam", "zoom", "boatfly", "schematicbuilder"];
    var pop = popIds.filter(function (id) { return modById[id]; }).map(function (id) {
      return h("a", { class: "pop", href: "/features?m=" + encodeURIComponent(id) }, icon("bolt", "small"), modById[id].name);
    });
    var popBox = pop.length ? h("div", { class: "pop-box", "data-reveal": "" },
      h("div", { class: "pop-head" }, h("h2", { class: "sec", text: "Popular modules" }), h("a", { href: "/features" }, "See all " + (Z.site.moduleCount || "") + " modules", icon("arrow", "small"))),
      h("div", { class: "pop-row" }, pop)) : null;

    var band = h("div", { class: "card band", "data-reveal": "" },
      h("div", { class: "band-t" }, icon("download", "big"), h("div", {}, h("h3", { text: "Want to try it?" }), h("p", { class: "muted", text: "Installing takes a few minutes. Get it from the Download page, or from GitHub if you prefer." }))),
      h("div", { class: "cta", style: "margin:0;animation:none" },
        extBtn("btn btn-primary flat", L.download, [icon("download"), "Download"]),
        h("button", { class: "btn btn-outline", type: "button", onclick: function () { if (Z.safe) Z.safe.open(); } }, icon("shield"), "Is it safe?")));

    return h("div", {}, hero, h("div", { class: "wrap" }, strip, cards, h("h2", { class: "sec home-sec", "data-reveal": "", text: "Start here" }), tools, popBox, band, h("div", { style: "height:36px" })));
  };

  /* ---------------- Download ---------------- */
  Z.pages.download = function () {
    var C = Z.content;
    var L = Z.site.links;
    var steps = C.install || [];
    var reqs = h("div", { class: "req", style: "margin-bottom:34px" }, (C.requirements || []).map(function (r) {
      return h("div", { class: "card" }, h("b", { text: r.title }), h("span", { text: r.text }));
    }));
    stagger(reqs.children, 80);
    var ol = h("ol", { class: "steps" }, steps.map(function (s) { return h("li", {}, h("div", {}, h("b", { text: s.title }), h("span", { text: s.text }))); }));
    stagger(ol.children, 90);
    var sha = Z.sha256 ? h("div", { class: "card", style: "margin-bottom:28px", "data-reveal": "" }, h("div", { class: "card-title" }, icon("shield"), "Check your download"),
      h("p", { class: "muted", style: "margin:10px 0 8px", text: "This is the SHA-256 of the official jar. Compare it with your download: certutil -hashfile zeus.jar SHA256 on Windows, sha256sum zeus.jar on Linux or macOS." }),
      h("code", { style: "word-break:break-all", text: Z.sha256 })) : null;

    var S = C.safe || {};
    var gh = L.github;
    var ghCard = h("div", { class: "card gh-card", id: "github", "data-reveal": "" },
      h("div", { class: "gh-ic" }, icon("github", "big")),
      h("div", { class: "gh-t" },
        h("h3", { text: "Prefer to download it from GitHub?" }),
        h("p", { class: "muted", text: S.github || "If you would rather not download from this site, get the same jar from the project's GitHub page, or build it yourself from the source." })),
      h("div", { class: "gh-b" },
        gh ? h("a", { class: "btn btn-primary flat", href: gh, target: "_blank", rel: "noopener noreferrer" }, icon("github"), "Open GitHub") : null,
        gh ? h("a", { class: "btn btn-outline", href: gh.replace(/\/+$/, "") + "/releases", target: "_blank", rel: "noopener noreferrer" }, icon("download"), "Releases") : null,
        h("a", { class: "btn btn-outline", href: "/source" }, icon("code"), "Read the source")));

    return h("div", { class: "wrap page" },
      pageHead("Download", "Get Zeus Client", "Zeus is a client-side Fabric mod for Minecraft " + Z.site.minecraft + "."),
      h("div", { class: "cta", style: "margin-bottom:34px" },
        extBtn("btn btn-primary", L.download, [icon("download"), h("span", { class: "btn-two" }, h("b", { text: "Download Zeus " + versionLabel() }), h("small", { text: L.download ? "Opens the download page" : "Link coming soon" }))]),
        gh ? h("a", { class: "btn btn-outline", href: gh, target: "_blank", rel: "noopener noreferrer" }, icon("github"), "GitHub") : h("a", { class: "btn btn-outline", href: "#github", onclick: function (e) { e.preventDefault(); var t = document.getElementById("github"); if (t) t.scrollIntoView({ behavior: REDUCED ? "auto" : "smooth", block: "center" }); } }, icon("github"), "From GitHub"),
        L.discord ? h("a", { class: "btn btn-outline", href: L.discord, target: "_blank", rel: "noopener noreferrer" }, icon("discord"), "Get help on Discord") : null,
        h("button", { class: "btn btn-outline", type: "button", onclick: function () { if (Z.safe) Z.safe.open(); } }, icon("shield"), "Is it safe?")),
      L.download ? null : h("div", { class: "note", style: "margin-bottom:28px", text: "There is no download link yet. Check back later or ask on the Discord." }),
      sha,
      ghCard,
      h("h2", { class: "sec", text: "Requirements" }), reqs,
      h("h2", { class: "sec", text: "Install" }), ol,
      h("div", { class: "note", style: "margin-top:30px", "data-edit": "warning", text: C.warning || "Many servers ban cheat clients. Only use Zeus where it is allowed, and read the server's rules first." }));
  };

  /* ---------------- Tutorial ---------------- */
  Z.pages.tutorial = function () {
    var C = Z.content;
    var L = Z.site.links;
    var ol = h("ol", { class: "steps" }, (C.tutorial || []).map(function (s) { return h("li", {}, h("div", {}, h("b", { text: s.title }), h("span", { text: s.text }))); }));
    stagger(ol.children, 90);
    return h("div", { class: "wrap page" },
      pageHead("Tutorial", "Getting started", "How to open the menu, switch modules on and find what you need."),
      h("div", { class: "cta", style: "margin-bottom:30px" },
        L.tutorial ? h("a", { class: "btn btn-primary flat", href: L.tutorial, target: "_blank", rel: "noopener noreferrer" }, icon("playfill"), "Watch the video tutorial") : null,
        h("a", { class: "btn btn-outline", href: "/help" }, icon("book"), "Guides for each part"),
        h("a", { class: "btn btn-outline", href: "/config" }, icon("sliders"), "Build a config")),
      ol,
      h("div", { class: "card tut-cfg", "data-reveal": "" },
        h("div", { class: "tc-i" }, icon("sliders")),
        h("div", {}, h("h3", { text: "Not sure what to switch on?" }), h("p", { class: "muted", text: "Answer a few yes or no questions, or pick a playstyle. You get a set of modules with a short reason for each." })),
        h("a", { class: "btn btn-primary flat", href: "/config" }, "Build a config", icon("arrow", "small"))),
      h("h2", { class: "sec", style: "margin-top:38px", text: "Useful commands" }),
      h("div", { class: "card", "data-reveal": "" }, h("table", { class: "tbl" }, h("tbody", {}, (C.commands || []).map(function (c) {
        return h("tr", {}, h("td", {}, h("code", { text: c.cmd })), h("td", { class: "muted", text: c.text }));
      })))));
  };

  /* ---------------- Help centre ---------------- */
  Z.pages.help = function () {
    var H = Z.help || { sections: [], faq: [] };
    var F = Z.features || { categories: [], modules: [] };
    var byCat = {};
    F.modules.forEach(function (m) { (byCat[m.category] = byCat[m.category] || []).push(m); });

    var side = h("nav", { class: "side", "aria-label": "Help sections" });
    var content = h("div", {});

    H.sections.forEach(function (s) {
      side.appendChild(h("a", { href: "#" + s.id, "data-sec": s.id }, icon(s.icon || "info"), h("span", { text: s.title })));
      var sec = h("section", { class: "hsec card", id: s.id, "data-reveal": "" },
        h("h2", {}, icon(s.icon || "info"), s.title),
        h("p", { class: "intro", text: s.intro || "" }));
      if (s.steps && s.steps.length) sec.appendChild(h("div", {}, h("h4", { text: "How to" }), h("ol", {}, s.steps.map(function (t) { return h("li", { text: t }); }))));
      if (s.tips && s.tips.length) sec.appendChild(h("div", {}, h("h4", { text: "Good to know" }), h("ul", { class: "tips" }, s.tips.map(function (t) { return h("li", { text: t }); }))));
      var mods = s.category && byCat[s.category];
      if (mods && mods.length) {
        var ml = h("div", { class: "modlist" }, mods.map(function (m) {
          var sp = h("span", { text: m.name, title: m.description || "", role: "button", tabindex: "0", style: "cursor:pointer" });
          var ask = function () { if (Z.helper) Z.helper.ask("What does " + m.name + " do?"); };
          sp.addEventListener("click", ask);
          sp.addEventListener("keydown", function (e) { if (e.key === "Enter") ask(); });
          return sp;
        }));
        sec.appendChild(h("div", {}, h("h4", { text: mods.length + " modules in this category · click one to ask about it" }), ml));
      }
      content.appendChild(sec);
    });

    // FAQ: grouped into categories, with chips to show one category and a search that covers all of them
    side.appendChild(h("a", { href: "#faq", "data-sec": "faq" }, icon("chat"), h("span", { text: "FAQ" })));
    var faqBox = h("div", { class: "faq-groups" });
    var faqCount = h("span", { class: "faint" });
    var q = h("input", { type: "search", placeholder: "Search the FAQ…", "aria-label": "Search the FAQ", autocomplete: "off", spellcheck: "false" });
    var catOrder = (H.cats && H.cats.length ? H.cats : []).slice();
    (H.faq || []).forEach(function (f) { if (f.cat && catOrder.indexOf(f.cat) < 0) catOrder.push(f.cat); });
    var curCat = "";
    var items = (H.faq || []).map(function (f) {
      var links = (f.links || []).filter(function (l) { return /^\/[a-z]*$/.test(l.href); });
      var d = h("details", { class: "faq" }, h("summary", { text: f.q }), h("p", { text: f.a }),
        links.length ? h("div", { class: "faq-links" }, links.map(function (l) { return h("a", { href: l.href }, l.label + " →"); })) : null);
      return { el: d, cat: f.cat || "", q: f.q, hay: (f.q + " " + f.a + " " + (f.keys || "")).toLowerCase() };
    });
    var groups = {};
    catOrder.forEach(function (c) {
      var count = h("span", { class: "cnt" });
      var body = h("div", { class: "faq-list" });
      var box = h("section", { class: "faq-group", "data-cat": c }, h("h3", {}, c, count), body);
      groups[c] = { box: box, body: body, count: count };
      faqBox.appendChild(box);
    });
    items.forEach(function (it) { var g = groups[it.cat]; if (g) g.body.appendChild(it.el); else faqBox.appendChild(it.el); });
    var chipRow = h("div", { class: "faq-chips", role: "group", "aria-label": "FAQ categories" });
    var chips = {};
    function chip(label, value, n) {
      var b = h("button", { type: "button", class: "faq-chip", "aria-pressed": "false" }, label, h("span", { class: "cnt", text: String(n) }));
      b.addEventListener("click", function () { curCat = value; filter(); });
      chips[value] = b; chipRow.appendChild(b);
    }
    chip("All", "", items.length);
    catOrder.forEach(function (c) { chip(c, c, items.filter(function (it) { return it.cat === c; }).length); });
    function filter() {
      var terms = q.value.trim().toLowerCase().split(/\s+/).filter(Boolean);
      var n = 0, per = {};
      items.forEach(function (it) {
        var ok = (!curCat || it.cat === curCat) && terms.every(function (t) { return it.hay.indexOf(t) >= 0; });
        it.el.hidden = !ok;
        if (ok) { n++; per[it.cat] = (per[it.cat] || 0) + 1; }
      });
      catOrder.forEach(function (c) {
        var g = groups[c];
        g.box.hidden = !per[c];
        g.count.textContent = per[c] ? String(per[c]) : "";
      });
      Object.keys(chips).forEach(function (k) { var on = k === curCat; chips[k].classList.toggle("on", on); chips[k].setAttribute("aria-pressed", on ? "true" : "false"); });
      faqCount.textContent = n + " of " + items.length + " questions" + (curCat ? " · " + curCat : "");
    }
    q.addEventListener("input", debounce(filter, 80));
    var pre = (/[?&]faq=([^&]+)/.exec(location.search) || [])[1];
    if (pre) { try { q.value = decodeURIComponent(pre.replace(/\+/g, " ")).slice(0, 120); } catch (e) { /* ignore */ } }
    filter();
    if (pre) setTimeout(function () { var first = items.filter(function (it) { return !it.el.hidden; })[0]; if (first) first.el.open = true; }, 40);
    content.appendChild(h("section", { class: "hsec card", id: "faq", "data-reveal": "" },
      h("h2", {}, icon("chat"), "Frequently asked questions"),
      h("p", { class: "intro", text: "Pick a category, search below, or ask the helper in your own words." }),
      h("div", { class: "faq-tools" }, h("div", { class: "search" }, icon("search"), q)),
      chipRow,
      h("div", { style: "margin:4px 0 12px" }, faqCount), faqBox));

    // the helper, embedded
    var helperHost = h("div", { id: "ask", class: "card", style: "margin-bottom:30px", "data-reveal": "" },
      h("div", { class: "card-title" }, icon("spark"), "Ask the helper"),
      h("p", { class: "muted", style: "margin:8px 0 14px", text: "Ask about Zeus or this website: how to install it, what a module does, whether it is safe, why something is not working." }));
    var mount = h("div", {});
    helperHost.appendChild(mount);
    if (Z.helper) Z.helper.mount(mount);

    var tools = h("div", { class: "tool-cards" },
      [["wand", "Troubleshooter", "A few yes or no questions lead to the fix.", "/troubleshoot"],
       ["sliders", "Build a config", "Answer a few questions and get a set of modules for how you play.", "/config"],
       ["code", "Source code", "Read every file of Zeus here on the site.", "/source"],
       ["user", "Your profile", "Your saved modules, configs and helper history.", "/account"]].map(function (t) {
        return h("a", { class: "tool-card", href: t[3] }, h("span", { class: "tc-i" }, icon(t[0])), h("span", { class: "tc-t" }, h("b", { text: t[1] }), h("small", { text: t[2] })), icon("arrow", "small"));
      }));
    stagger(tools.children, 70);
    var page = h("div", { class: "wrap page" },
      pageHead("Help", "Help centre", "Guides, a searchable FAQ and a helper that answers questions about Zeus and this site."),
      tools,
      helperHost,
      h("div", { class: "help-grid" }, side, content));

    // scroll spy
    var spy = function () {
      var best = null;
      content.querySelectorAll(".hsec").forEach(function (s) {
        if (s.getBoundingClientRect().top < 160) best = s.id;
      });
      side.querySelectorAll("a").forEach(function (a) { a.classList.toggle("on", a.getAttribute("data-sec") === best); });
    };
    var ticking = false;
    var onScroll = function () { if (ticking) return; ticking = true; requestAnimationFrame(function () { ticking = false; spy(); }); };
    window.addEventListener("scroll", onScroll, { passive: true });
    Z.onLeave(function () { window.removeEventListener("scroll", onScroll); });
    side.addEventListener("click", function (e) {
      var a = e.target.closest("a[data-sec]");
      if (!a) return;
      e.preventDefault();
      var t = document.getElementById(a.getAttribute("data-sec"));
      if (t) t.scrollIntoView({ behavior: REDUCED ? "auto" : "smooth", block: "start" });
      history.replaceState(null, "", "/help#" + a.getAttribute("data-sec"));
    });
    setTimeout(spy, 50);
    return page;
  };

  /* ---------------- Information (about + timeline) ---------------- */
  Z.pages.information = function () {
    var C = Z.content;
    var modCount = h("td", { text: String(Z.site.moduleCount || "–") });
    var glance = h("div", { class: "card" }, h("h2", { class: "sec", text: "At a glance" }), h("table", { class: "tbl" }, h("tbody", {}, [
      ["Version", h("td", { text: versionLabel() || "–" })],
      ["Minecraft", h("td", { text: Z.site.minecraft })],
      ["Loader", h("td", { text: "Fabric (client side only)" })],
      ["Modules", modCount],
      ["Settings", h("td", { text: String(((Z.features && Z.features.modules) || []).reduce(function (n, m) { return n + (m.settings || []).filter(function (x) { return x.type !== "info"; }).length; }, 0) || "–") })],
      ["Releases", h("td", { text: String(Z.timeline.length) })],
      ["Status", h("td", { text: "Actively updated" })],
    ].map(function (r) { return h("tr", {}, h("th", { text: r[0] }), r[1]); }))));
    if (Z.site.moduleCount) { var n = Z.site.moduleCount; modCount.textContent = "0"; setTimeout(function () { Z.countUp(modCount, n); }, 300); }

    var modById = {};
    ((Z.features && Z.features.modules) || []).forEach(function (m) { modById[m.id] = m; });
    var tl = h("div", { class: "tl", id: "tl" }, Z.timeline.map(function (u) {
      var mods = (u.mods || []).filter(function (id) { return modById[id]; });
      var card = h("div", { class: "tl-card" },
        h("span", { class: "pill", text: "v" + u.version }),
        h("h3", { text: u.title }),
        h("p", { text: u.text }));
      if (mods.length) {
        card.appendChild(h("div", { class: "tl-mods" }, h("span", { class: "tl-lab", text: "Modules" }), mods.map(function (id) {
          return h("a", { class: "tl-chip", href: "/features?m=" + encodeURIComponent(id) }, modById[id].name);
        })));
      }
      if (u.settings && u.settings.length) {
        card.appendChild(h("details", { class: "tl-sets" }, h("summary", { text: "Settings changed (" + u.settings.length + ")" }), h("ul", {}, u.settings.map(function (t) { return h("li", { text: t }); }))));
      }
      return h("div", { class: "tl-item", "data-reveal": "" }, h("span", { class: "tl-dot" }), card);
    }));
    var onScroll = function () {
      var r = tl.getBoundingClientRect();
      var k = (window.innerHeight * 0.6 - r.top) / Math.max(r.height, 1);
      tl.style.setProperty("--p", Math.max(0, Math.min(1, k)).toFixed(3));
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onScroll);
    Z.onLeave(function () { window.removeEventListener("scroll", onScroll); window.removeEventListener("resize", onScroll); });
    setTimeout(onScroll, 60);
    if (REDUCED) tl.style.setProperty("--p", "1");

    var top = h("div", { class: "grid g2", style: "margin-bottom:38px" }, glance,
      h("div", { class: "card" }, h("h2", { class: "sec", text: "Please read" }),
        h("p", { class: "muted", "data-edit": "warning", text: C.warning || "" }),
        h("p", { class: "muted", "data-edit": "disclaimer", text: C.disclaimer || "" }),
        h("div", { class: "cta", style: "margin:14px 0 0;animation:none" }, h("a", { class: "btn btn-outline sm", href: "/help#faq" }, "Read the FAQ"), h("button", { class: "btn btn-outline sm", type: "button", onclick: function () { if (Z.safe) Z.safe.open(); } }, icon("shield", "small"), "Is it safe?"))));
    stagger(top.children, 100);

    return h("div", { class: "wrap page" },
      pageHead("Information", "About and update timeline", "About the client, and every release from the first version to today."),
      top,
      h("h2", { class: "sec", id: "timeline", text: "Update timeline" }),
      h("p", { class: "muted", style: "margin:-6px 0 22px", text: "Newest first. One entry per release. The module chips open that module on the Features page." }),
      tl);
  };

  /* Settings is provided by dash.js; show a friendly page if it is missing. */
  if (!Z.pages.settings) Z.pages.settings = function () { return h("div", { class: "wrap page" }, h("div", { class: "card" }, "The control panel script did not load.")); };

  /* ---------------- start ---------------- */
  var started = false;
  function start() {
    if (started) return;
    started = true;

    // legacy "#/page" links and the one-time "#token=…" hand-over from the in-game link
    var tm = /[#&?]token=([A-Za-z0-9_-]{20,})/.exec(location.hash);
    var legacy = /^#\/([a-z]+)/i.exec(location.hash);
    if (legacy && !Z.pages[legacy[1].toLowerCase()] && legacy[1].toLowerCase() !== "home") legacy = null;
    if (tm) {
      Z.token = tm[1];
      store("zeus.token", tm[1]);
      history.replaceState(null, "", "/settings");
    } else {
      Z.token = store("zeus.token") || "";
      if (legacy && legacy[1] !== "token") {
        var seg = legacy[1].toLowerCase();
        if (seg === "home") history.replaceState(null, "", "/");
        else history.replaceState(null, "", "/" + seg);
      }
    }

    loadData().then(function () {
      buildNav();
      fxStart();
      document.addEventListener("click", intercept);
      window.addEventListener("popstate", function () {
        // the Features page opens and closes its detail drawer through the URL, so back/forward must not rebuild it
        if (location.pathname === lastPath && currentPage() === "features") { document.dispatchEvent(new CustomEvent("zeus:query")); return; }
        render(currentPage(), { hash: location.hash.slice(1), keepScroll: !location.hash });
      });
      if (!REDUCED) document.documentElement.classList.add("motion");
      render(currentPage(), { first: true, hash: location.hash.slice(1) });
      Z.ready.forEach(function (fn) { try { fn(); } catch (e) { if (window.console) console.error(e); } });
    }).catch(function (e) {
      $("#view").appendChild(h("div", { class: "wrap page" }, h("div", { class: "card" }, "Could not load the site data: " + (e && e.message))));
    });
  }
  window.addEventListener("DOMContentLoaded", start);
  if (document.readyState !== "loading") setTimeout(start, 0);
})();
