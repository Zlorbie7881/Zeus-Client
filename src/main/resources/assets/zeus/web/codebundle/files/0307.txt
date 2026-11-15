/* Zeus Client website 9.5: page backgrounds, opening animations, thunder cursor, electric arcs on the emblem, the floating helper button,
   the "Is it safe?" dialog, the site banner and the Full/Calm motion switch. No inline scripts (the page's CSP forbids them). */
(function () {
  "use strict";
  var Z = window.Z, h = Z.h, icon = Z.icon, $ = Z.$;
  var NS = "http://www.w3.org/2000/svg";

  function rnd(a, b) { return a + Math.random() * (b - a); }
  function svgEl(tag, attrs) {
    var e = document.createElementNS(NS, tag);
    for (var k in attrs) e.setAttribute(k, attrs[k]);
    return e;
  }

  /* ---------------------------------------------------------------- per-page background */
  var BG_KEYS = { home: 1, download: 1, features: 1, tutorial: 1, information: 1, help: 1, source: 1, credits: 1, config: 1 };
  var bg = null, layers = {};
  function setBackground(page) {
    if (!bg) {
      bg = h("div", { id: "bg", "aria-hidden": "true" });
      var fx = $("#fx");
      document.body.insertBefore(bg, fx || document.body.firstChild);
    }
    var key = BG_KEYS[page] ? page : "grid";
    if (!layers[key]) { layers[key] = h("div", { class: "bl", "data-k": key }); bg.appendChild(layers[key]); }
    Object.keys(layers).forEach(function (k) { layers[k].classList.toggle("on", k === key); });
  }

  /* ---------------------------------------------------------------- bolt that runs across the header line on a page change */
  function boltFlash() {
    if (Z.reduced) return;
    var old = $(".bolt-flash");
    if (old) old.remove();
    var w = 1000, d = "M" + rnd(w * 0.15, w * 0.85) + " 0", x = parseFloat(d.slice(1)), y = 0;
    var branches = "";
    for (var i = 0; i < 7; i++) {
      x += rnd(-70, 70); y += rnd(14, 24);
      d += " L" + x.toFixed(0) + " " + y.toFixed(0);
      if (Math.random() < 0.4) branches += "M" + x.toFixed(0) + " " + y.toFixed(0) + " l" + rnd(-60, 60).toFixed(0) + " " + rnd(14, 36).toFixed(0);
    }
    var s = svgEl("svg", { viewBox: "0 0 " + w + " 150", preserveAspectRatio: "none", "aria-hidden": "true" });
    s.appendChild(svgEl("path", { d: d + branches.replace(/M/g, " M"), fill: "none", stroke: "#cfe6ff", "stroke-width": "2", "stroke-linejoin": "round", "stroke-linecap": "round" }));
    var el = h("div", { class: "bolt-flash", "aria-hidden": "true" });
    el.appendChild(s);
    document.body.appendChild(el);
    setTimeout(function () { if (el.parentNode) el.parentNode.removeChild(el); }, 700);
  }

  /* ---------------------------------------------------------------- electric arcs around every emblem */
  function makeArc(angle) {
    var cx = 50, cy = 50, r = rnd(37, 41), step = 4 + Math.floor(rnd(0, 2)), a = angle;
    var d = "M" + (cx + Math.cos(a) * r).toFixed(1) + " " + (cy + Math.sin(a) * r).toFixed(1);
    for (var i = 0; i < step; i++) {
      r += rnd(3, 6); a += rnd(-0.16, 0.16);
      var j = rnd(-2.6, 2.6);
      d += " L" + (cx + Math.cos(a) * r + j).toFixed(1) + " " + (cy + Math.sin(a) * r + j).toFixed(1);
    }
    return d;
  }
  var origLogo = Z.logo;
  Z.logo = function (size) {
    var el = origLogo(size);
    var float = el.querySelector(".zl-float");
    if (float) {
      var s = svgEl("svg", { class: "zl-arcs", viewBox: "0 0 100 100", "aria-hidden": "true" });
      [-0.6, 0.9, 2.5, 4.1].forEach(function (a) { s.appendChild(svgEl("path", { d: makeArc(a) })); });
      float.appendChild(s);
    }
    return el;
  };

  /* ---------------------------------------------------------------- thunder cursor: a short spark trail */
  var cur = { cv: null, ctx: null, parts: [], running: false, lx: -99, ly: -99, dpr: 1 };
  function curInit() {
    if (window.matchMedia && matchMedia("(hover: none)").matches) return;
    cur.cv = h("canvas", { id: "cur", "aria-hidden": "true" });
    document.body.appendChild(cur.cv);
    cur.ctx = cur.cv.getContext("2d");
    function size() { cur.dpr = Math.min(window.devicePixelRatio || 1, 1.5); cur.cv.width = innerWidth * cur.dpr; cur.cv.height = innerHeight * cur.dpr; }
    size();
    window.addEventListener("resize", Z.debounce(size, 150));
    document.addEventListener("pointermove", function (e) {
      if (Z.reduced || e.pointerType === "touch") return;
      var dx = e.clientX - cur.lx, dy = e.clientY - cur.ly;
      if (dx * dx + dy * dy < 110) return;
      cur.lx = e.clientX; cur.ly = e.clientY;
      var n = 1 + (Math.random() < 0.35 ? 1 : 0);
      for (var i = 0; i < n; i++) cur.parts.push({ x: e.clientX + 19 - 19 + rnd(-3, 3), y: e.clientY + rnd(0, 8), vx: rnd(-18, 18), vy: rnd(8, 34), life: 0, max: rnd(0.35, 0.7), bolt: Math.random() < 0.25, r: rnd(1, 2.3) });
      if (cur.parts.length > 60) cur.parts.splice(0, cur.parts.length - 60);
      if (!cur.running) { cur.running = true; cur.last = performance.now(); requestAnimationFrame(curFrame); }
    }, { passive: true });
    document.addEventListener("pointerdown", function (e) {
      if (Z.reduced || e.pointerType === "touch") return;
      for (var i = 0; i < 9; i++) { var a = Math.random() * 6.283, v = rnd(40, 120); cur.parts.push({ x: e.clientX, y: e.clientY, vx: Math.cos(a) * v, vy: Math.sin(a) * v, life: 0, max: rnd(0.25, 0.5), bolt: i % 3 === 0, r: rnd(1, 2) }); }
      if (!cur.running) { cur.running = true; cur.last = performance.now(); requestAnimationFrame(curFrame); }
    }, { passive: true });
  }
  function curFrame(t) {
    var dt = Math.min(0.05, (t - cur.last) / 1000 || 0.016);
    cur.last = t;
    var c = cur.ctx;
    c.setTransform(cur.dpr, 0, 0, cur.dpr, 0, 0);
    c.clearRect(0, 0, innerWidth, innerHeight);
    c.globalCompositeOperation = "lighter";
    for (var i = cur.parts.length - 1; i >= 0; i--) {
      var p = cur.parts[i];
      p.life += dt;
      if (p.life >= p.max) { cur.parts.splice(i, 1); continue; }
      p.x += p.vx * dt; p.y += p.vy * dt; p.vx *= 0.96; p.vy *= 0.96;
      var a = 1 - p.life / p.max;
      if (p.bolt) {
        c.strokeStyle = "rgba(190,225,255," + (a * 0.9).toFixed(2) + ")"; c.lineWidth = 1.4;
        c.beginPath(); c.moveTo(p.x, p.y); c.lineTo(p.x + 4, p.y + 5); c.lineTo(p.x - 1, p.y + 7); c.lineTo(p.x + 3, p.y + 13); c.stroke();
      } else {
        var g = c.createRadialGradient(p.x, p.y, 0, p.x, p.y, p.r * 4);
        g.addColorStop(0, "rgba(200,230,255," + (a * 0.9).toFixed(2) + ")");
        g.addColorStop(1, "rgba(40,120,255,0)");
        c.fillStyle = g; c.beginPath(); c.arc(p.x, p.y, p.r * 4, 0, 6.283); c.fill();
      }
    }
    if (cur.parts.length && !Z.reduced) requestAnimationFrame(curFrame); else { cur.running = false; c.clearRect(0, 0, innerWidth, innerHeight); }
  }

  /* ---------------------------------------------------------------- dialogs */
  Z.modal = function (o) {
    var prev = document.activeElement;
    var back = h("div", { class: "modal-back", role: "presentation" });
    var x = h("button", { class: "modal-x", type: "button", "aria-label": "Close" }, icon("close"));
    var box = h("div", { class: "modal", role: "dialog", "aria-modal": "true", "aria-label": o.title || "Dialog", style: o.width ? "width:min(" + o.width + "px,100%)" : null },
      x, o.title ? h("h2", {}, o.icon ? icon(o.icon) : null, o.title) : null, o.lead ? h("p", { class: "lead", text: o.lead }) : null, o.body);
    back.appendChild(box);
    document.body.appendChild(back);
    var closed = false;
    function close() {
      if (closed) return; closed = true;
      document.removeEventListener("keydown", onKey, true);
      back.classList.add("out");
      setTimeout(function () { if (back.parentNode) back.parentNode.removeChild(back); }, Z.reduced ? 0 : 200);
      if (prev && prev.focus) try { prev.focus(); } catch (e) { /* ignore */ }
      if (o.onClose) o.onClose();
    }
    function onKey(e) {
      if (e.key === "Escape") { e.stopPropagation(); close(); }
      if (e.key === "Tab") {
        var f = box.querySelectorAll("a[href],button:not([disabled]),input,textarea,select,[tabindex]:not([tabindex='-1'])");
        if (!f.length) return;
        var first = f[0], last = f[f.length - 1];
        if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
        else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
      }
    }
    document.addEventListener("keydown", onKey, true);
    x.addEventListener("click", close);
    back.addEventListener("mousedown", function (e) { if (e.target === back) close(); });
    box.addEventListener("click", function (e) { var a = e.target.closest && e.target.closest("a[href]"); if (a && a.getAttribute("href").charAt(0) === "/" && !a.target) close(); });
    setTimeout(function () { x.focus(); }, 30);
    return { close: close, box: box };
  };

  /* ---------------------------------------------------------------- Is it safe? */
  Z.safe = {
    open: function () {
      var S = Z.content.safe || {};
      var L = Z.site.links;
      var checks = (S.checks || []).map(function (c, i) {
        var r = h("div", { class: "safe-row" }, icon(c.icon && document.getElementById("i-" + c.icon) ? c.icon : "check"), h("div", {}, h("b", { text: c.title }), h("span", { text: c.text })));
        r.style.animationDelay = i * 70 + "ms";
        return r;
      });
      var gh = L.github;
      var ghBox = h("div", { class: "safe-gh" }, icon("github"),
        h("div", {}, h("b", { text: "Do not trust the download on this site?" }),
          h("p", { text: S.github || "You do not have to. The same jar is on the project's GitHub page, and you can build it yourself from the source." })),
        gh ? h("div", { class: "cta", style: "margin:0;gap:10px" },
          h("a", { class: "btn btn-primary flat", href: gh, target: "_blank", rel: "noopener noreferrer" }, icon("github"), "Open GitHub"),
          h("a", { class: "btn btn-outline", href: gh.replace(/\/+$/, "") + "/releases", target: "_blank", rel: "noopener noreferrer" }, "Releases"))
        : h("a", { class: "btn btn-outline", href: "/download#github" }, icon("github"), "GitHub download"));
      var body = h("div", {}, h("div", { class: "safe-list" }, checks), ghBox,
        S.risk ? h("p", { class: "safe-risk", text: S.risk }) : null,
        h("div", { class: "acts" },
          h("a", { class: "btn btn-outline sm", href: "/source" }, icon("code", "small"), "Read the source"),
          h("a", { class: "btn btn-outline sm", href: "/download" }, icon("download", "small"), "Download page"),
          h("button", { class: "btn btn-outline sm", type: "button", onclick: function () { m.close(); if (Z.helper) Z.helper.ask("Is Zeus safe to download?"); } }, icon("chat", "small"), "Ask the helper")));
      var m = Z.modal({ title: S.title || "Is Zeus safe?", icon: "shield", lead: S.lead, body: body });
      return m;
    },
  };

  /* ---------------------------------------------------------------- banner */
  Z.renderBanner = function () {
    var old = $("#siteBanner");
    if (old) old.remove();
    var b = Z.content.banner;
    if (!b || !b.on || !b.text) return;
    var key = "zeus.banner." + String(b.text).length + String(b.text).charCodeAt(0);
    if (Z.store(key) === "x" && !(Z.adminActive && Z.adminActive())) return;
    var kind = /^(warn|ok)$/.test(b.kind) ? b.kind : "info";
    var el = h("div", { class: "site-banner " + kind, id: "siteBanner", role: "status" }, h("span", { text: b.text }),
      b.href && /^(https?:\/\/|\/)/.test(b.href) ? h("a", { href: b.href, target: /^https?:/.test(b.href) ? "_blank" : null, rel: "noopener noreferrer", text: b.label || "Learn more" }) : null);
    var x = h("button", { class: "bx", type: "button", "aria-label": "Dismiss" }, icon("close"));
    x.addEventListener("click", function () { Z.store(key, "x"); el.remove(); });
    el.appendChild(x);
    document.body.insertBefore(el, $("#nav"));
  };

  /* ---------------------------------------------------------------- footer motion switch */
  function motionSwitch() {
    var left = $(".foot-left");
    if (!left || $("#motionSw")) return;
    var b = h("button", { class: "mt", id: "motionSw", type: "button", "aria-pressed": "false", title: "Switch animations between Full and Calm" });
    function paint() {
      while (b.firstChild) b.removeChild(b.firstChild);
      b.appendChild(icon(Z.reduced ? "eye" : "spark"));
      b.appendChild(document.createTextNode("Motion: " + (Z.reduced ? "Calm" : "Full")));
      b.setAttribute("aria-pressed", Z.reduced ? "true" : "false");
    }
    b.addEventListener("click", function () { Z.setMotion(Z.reduced ? "full" : "calm"); paint(); if (!Z.reduced) boltFlash(); });
    paint();
    left.appendChild(b);
  }

  /* ---------------------------------------------------------------- floating helper button */
  function fabInit() {
    var fab = $(".hlp-fab");
    if (!fab || fab.getAttribute("data-v95")) return;
    fab.setAttribute("data-v95", "1");
    while (fab.firstChild) fab.removeChild(fab.firstChild);
    fab.appendChild(h("span", { class: "fab-ic" }, icon("chat")));
    fab.appendChild(h("span", { text: "Ask the helper" }));
    var hint = null, seen = false;
    try { seen = sessionStorage.getItem("zeus.fabhint") === "1"; } catch (e) { /* storage blocked */ }
    function hide() { if (hint) { hint.classList.remove("on"); var hh = hint; hint = null; setTimeout(function () { if (hh.parentNode) hh.parentNode.removeChild(hh); }, 400); } }
    function show() {
      if (seen || hint || fab.classList.contains("hide") || fab.style.display === "none" || Z.reduced) return;
      seen = true;
      try { sessionStorage.setItem("zeus.fabhint", "1"); } catch (e) { /* storage blocked */ }
      var x = h("button", { type: "button", "aria-label": "Dismiss" }, icon("close"));
      x.addEventListener("click", function (e) { e.stopPropagation(); hide(); });
      hint = h("div", { class: "fab-hint", role: "status" }, h("span", { class: "dots" }, h("i"), h("i"), h("i")), h("span", { text: "Stuck? I can answer questions about Zeus." }), x);
      hint.addEventListener("click", function () { hide(); fab.click(); });
      document.body.appendChild(hint);
      requestAnimationFrame(function () { requestAnimationFrame(function () { if (hint) hint.classList.add("on"); }); });
      setTimeout(hide, 9000);
    }
    fab.addEventListener("click", hide);
    setTimeout(show, 6500);
    document.addEventListener("zeus:page", function (e) { if (e.detail && e.detail.page === "help") hide(); });
  }

  /* ---------------------------------------------------------------- wiring */
  Z.hooks.page.push(function (page) { setBackground(page); boltFlash(); });
  Z.hooks.data.push(function () { Z.renderBanner(); });
  Z.ready.push(function () { curInit(); motionSwitch(); fabInit(); Z.renderBanner(); });
})();
