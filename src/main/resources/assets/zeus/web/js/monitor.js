/* Zeus Client website: Performance monitor.
   A monitor-style view of the per-module usage cost estimate in features.json. It is an estimate worked out from what each module does
   every frame (tick work, HUD drawing, world drawing, scanning), not a measurement. The real, measured numbers are in the game
   (Performance Guard, and "Show what each costs" in the module list). Nothing here is animated with fake jitter. */
(function () {
  "use strict";
  var Z = window.Z;
  if (!Z) return;
  var h = Z.h, icon = Z.icon, clear = Z.clear;

  var POINTS = [0, 1, 2, 4, 7, 11];                 // points for cost tier 1..5 (tier 0 unused)
  var HOOKS = [
    { id: "tick", label: "Game tick", note: "Logic that runs 20 times a second", w: 1, color: "#4ea4ff" },
    { id: "hud", label: "Screen drawing", note: "Things drawn on your HUD", w: 1, color: "#b58cff" },
    { id: "world", label: "World drawing", note: "Boxes and lines drawn in the world", w: 3, color: "#ffc857" },
    { id: "scan", label: "Scanning", note: "Looks through blocks or entities around you", w: 3, color: "#ff5d72" },
  ];
  var IDLE = { id: "idle", label: "Waiting", note: "Only reacts to events", color: "rgba(150,170,210,.45)" };
  var BANDS = [
    { max: 25, name: "Light", cls: "b1", tip: "Should be fine on almost any computer." },
    { max: 60, name: "Moderate", cls: "b2", tip: "Fine on a decent computer. Watch your FPS on an older one." },
    { max: 110, name: "Heavy", cls: "b3", tip: "Expect an FPS drop on a weaker computer. Consider switching a few of the red bars off." },
    { max: 1e9, name: "Very heavy", cls: "b4", tip: "That is a lot at once. Few people need this many modules on together." },
  ];
  var GAUGE_MAX = 130;

  function tierOf(m) { var t = typeof m.cost === "number" ? m.cost : 2; return t < 1 ? 1 : t > 5 ? 5 : t; }
  function pts(m) { return POINTS[tierOf(m)]; }
  function band(total) { for (var i = 0; i < BANDS.length; i++) if (total < BANDS[i].max) return BANDS[i]; return BANDS[BANDS.length - 1]; }
  function esc(s) { return String(s == null ? "" : s); }

  // split a module's points across the hooks it runs
  function split(m) {
    var p = pts(m), runs = (m.runs || []).filter(function (r) { return HOOKS.some(function (k) { return k.id === r; }); });
    if (!runs.length) return [{ hook: IDLE, v: p }];
    var sum = 0;
    runs.forEach(function (r) { sum += HOOKS.filter(function (k) { return k.id === r; })[0].w; });
    return runs.map(function (r) {
      var k = HOOKS.filter(function (x) { return x.id === r; })[0];
      return { hook: k, v: p * k.w / sum };
    });
  }

  var NS = "http://www.w3.org/2000/svg";
  function svg(tag, attrs) {
    var e = document.createElementNS(NS, tag);
    Object.keys(attrs || {}).forEach(function (k) { e.setAttribute(k, attrs[k]); });
    return e;
  }

  Z.pages.monitor = function () {
    var F = Z.features || { categories: [], modules: [] };
    var byId = {}, catOf = {};
    F.modules.forEach(function (m) { byId[m.id] = m; });
    F.categories.forEach(function (c) { catOf[c.id] = c; });
    var picked = {}, presetId = "", presets = [];

    /* ---------- monitor screen ---------- */
    var led = h("span", { class: "mon-led" });
    var statusTxt = h("span", { class: "mon-status", text: "" });
    var gaugeBox = h("div", { class: "mon-gauge" });
    var arc = svg("path", { d: "M 16 104 A 84 84 0 0 1 184 104", fill: "none", "stroke-width": "14", "stroke-linecap": "round", pathLength: "100", class: "g-fill", "stroke-dasharray": "100", "stroke-dashoffset": "100" });
    var track = svg("path", { d: "M 16 104 A 84 84 0 0 1 184 104", fill: "none", "stroke-width": "14", "stroke-linecap": "round", class: "g-track" });
    var gsvg = svg("svg", { viewBox: "0 0 200 120", role: "img", "aria-label": "Load gauge" });
    gsvg.appendChild(track); gsvg.appendChild(arc);
    [0, 25, 50, 75, 100].forEach(function (t) {
      var a = Math.PI * (1 - t / 100), cx = 100 + Math.cos(a) * 98, cy = 104 - Math.sin(a) * 98, cx2 = 100 + Math.cos(a) * 104, cy2 = 104 - Math.sin(a) * 104;
      gsvg.appendChild(svg("line", { x1: cx.toFixed(1), y1: cy.toFixed(1), x2: cx2.toFixed(1), y2: cy2.toFixed(1), class: "g-tick" }));
    });
    var gNum = h("div", { class: "g-num", text: "0" });
    var gBand = h("div", { class: "g-band", text: "" });
    gaugeBox.appendChild(gsvg); gaugeBox.appendChild(h("div", { class: "g-mid" }, gNum, h("div", { class: "g-unit", text: "load points" }), gBand));

    var hookBar = h("div", { class: "mstack", role: "img", "aria-label": "Where the load comes from" });
    var hookLegend = h("div", { class: "mon-legend" });
    var catBar = h("div", { class: "mstack", role: "img", "aria-label": "Load by category" });
    var catLegend = h("div", { class: "mon-legend" });
    var tipLine = h("p", { class: "mon-tip", text: "" });
    var countLine = h("div", { class: "mon-count", text: "" });
    var topBox = h("div", { class: "mon-top" });

    var screen = h("div", { class: "mon card" },
      h("div", { class: "mon-bar" }, led, h("b", { text: "Zeus load estimate" }), statusTxt, h("span", { class: "mon-est", text: "estimate, not measured" })),
      h("div", { class: "mon-body" },
        h("div", { class: "mon-left" }, gaugeBox, countLine, tipLine),
        h("div", { class: "mon-right" },
          h("h3", { class: "mon-h", text: "Where the load comes from" }), hookBar, hookLegend,
          h("h3", { class: "mon-h", text: "Load by category" }), catBar, catLegend)),
      h("div", { class: "mon-lower" },
        h("h3", { class: "mon-h", text: "Most expensive modules on your list" }), topBox));

    /* ---------- painting ---------- */
    function ids() { return Object.keys(picked).filter(function (k) { return picked[k] && byId[k]; }); }

    function stack(box, legend, parts, total) {
      clear(box); clear(legend);
      if (!total) { box.appendChild(h("span", { class: "seg empty", style: "flex:1 1 100%" })); box.classList.add("is-empty"); return; }
      box.classList.remove("is-empty");
      parts.forEach(function (p) {
        if (p.v <= 0) return;
        var pct = p.v / total * 100;
        var seg = h("span", { class: "seg", title: p.label + ": " + Math.round(pct) + "%" });
        seg.style.flexGrow = String(p.v); seg.style.background = p.color;
        box.appendChild(seg);
        legend.appendChild(h("span", { class: "lg", title: p.note || "" }, h("i", { style: "background:" + p.color }), p.label + " ", h("b", { text: Math.round(pct) + "%" })));
      });
    }

    function paint() {
      var list = ids().map(function (id) { return byId[id]; });
      var total = 0, byHook = {}, byCat = {};
      list.forEach(function (m) {
        total += pts(m);
        split(m).forEach(function (s) { byHook[s.hook.id] = (byHook[s.hook.id] || 0) + s.v; });
        byCat[m.category] = (byCat[m.category] || 0) + pts(m);
      });
      var b = band(total), fill = Math.min(total / GAUGE_MAX, 1) * 100;
      arc.setAttribute("stroke-dashoffset", String(100 - fill));
      gaugeBox.className = "mon-gauge " + b.cls;
      led.className = "mon-led " + b.cls;
      gNum.textContent = String(Math.round(total));
      gBand.textContent = list.length ? b.name : "Nothing selected";
      statusTxt.textContent = list.length ? b.name : "idle";
      countLine.textContent = list.length ? list.length + (list.length === 1 ? " module on" : " modules on") : "Pick a playstyle or some modules below";
      tipLine.textContent = list.length ? b.tip : "";

      stack(hookBar, hookLegend, HOOKS.concat([IDLE]).map(function (k) { return { label: k.label, note: k.note, color: k.color, v: byHook[k.id] || 0 }; }), total);
      stack(catBar, catLegend, F.categories.map(function (c) { return { label: c.label, color: /^#[0-9a-fA-F]{3,8}$/.test(c.color) ? c.color : "#4ea4ff", v: byCat[c.id] || 0 }; })
        .sort(function (a, c) { return c.v - a.v; }), total);

      // ranked bars
      clear(topBox);
      var ranked = list.slice().sort(function (a, c) { return pts(c) - pts(a) || a.name.localeCompare(c.name); });
      if (!ranked.length) topBox.appendChild(h("div", { class: "empty", text: "Nothing selected yet." }));
      var shown = ranked.slice(0, 12);
      shown.forEach(function (m) {
        var row = h("div", { class: "trow t" + tierOf(m) });
        var track = h("div", { class: "ttrack" });
        var wrap = h("div", { class: "tfill" });
        wrap.style.width = (pts(m) / POINTS[5] * 100) + "%";
        split(m).forEach(function (s) {
          var sg = h("span", { title: s.hook.label });
          sg.style.flexGrow = String(s.v); sg.style.background = s.hook.color;
          wrap.appendChild(sg);
        });
        track.appendChild(wrap);
        var rm = h("button", { type: "button", class: "trm", "aria-label": "Switch " + m.name + " off in this view", title: "Take it off this list" }, icon("power", "small"));
        rm.addEventListener("click", function () { picked[m.id] = false; presetId = ""; paintPresets(); paintMods(); paint(); });
        row.appendChild(h("span", { class: "tname", text: m.name }));
        row.appendChild(track);
        row.appendChild(h("span", { class: "tpts", text: pts(m) + " pt" + (pts(m) === 1 ? "" : "s") }));
        row.appendChild(rm);
        topBox.appendChild(row);
      });
      if (ranked.length > shown.length) {
        var rest = 0;
        ranked.slice(shown.length).forEach(function (m) { rest += pts(m); });
        topBox.appendChild(h("div", { class: "faint tmore", text: "and " + (ranked.length - shown.length) + " more, adding " + rest + " points together" }));
      }
    }

    /* ---------- picker ---------- */
    var grid = h("div", { class: "preset-grid" });
    var listBox = h("div", { class: "cfg-mods" });
    var filterIn = h("input", { type: "search", placeholder: "Filter modules…", "aria-label": "Filter modules", autocomplete: "off", spellcheck: "false" });

    function setAll(fn) { picked = {}; F.modules.forEach(function (m) { if (fn(m)) picked[m.id] = true; }); presetId = ""; paintPresets(); paintMods(); paint(); }
    function paintPresets() {
      [].forEach.call(grid.children, function (c) { var on = c.getAttribute("data-id") === presetId && presetId !== ""; c.classList.toggle("on", on); c.setAttribute("aria-pressed", on ? "true" : "false"); });
    }
    function paintMods() {
      var q = filterIn.value.trim().toLowerCase();
      clear(listBox);
      F.categories.forEach(function (c) {
        var ms = F.modules.filter(function (m) { return m.category === c.id && (!q || m.name.toLowerCase().indexOf(q) >= 0 || (m.aliases || []).some(function (a) { return a.toLowerCase().indexOf(q) >= 0; })); });
        if (!ms.length) return;
        var on = ms.filter(function (m) { return picked[m.id]; }).length;
        var det = h("details", { class: "cfg-cat", open: q || on ? "" : null, style: "--c:" + (/^#[0-9a-fA-F]{3,8}$/.test(c.color) ? c.color : "#4ea4ff") },
          h("summary", {}, h("span", { class: "dot" }), c.label, h("span", { class: "cnt", text: on + " / " + ms.length })),
          h("div", { class: "cfg-chips" }, ms.map(function (m) {
            var b = h("button", { type: "button", class: "cfg-chip" + (picked[m.id] ? " on" : ""), "aria-pressed": picked[m.id] ? "true" : "false", title: m.description || "" },
              h("span", { class: "tick" }, icon("check", "small")), h("span", { text: m.name }), h("span", { class: "chip-pts t" + tierOf(m), text: String(pts(m)) }));
            b.addEventListener("click", function () {
              picked[m.id] = !picked[m.id]; presetId = ""; paintPresets();
              b.classList.toggle("on", !!picked[m.id]); b.setAttribute("aria-pressed", picked[m.id] ? "true" : "false");
              paint();
            });
            return b;
          })));
        listBox.appendChild(det);
      });
      if (!listBox.firstChild) listBox.appendChild(h("div", { class: "empty", text: "No module matches that." }));
    }
    filterIn.addEventListener("input", Z.debounce(paintMods, 90));

    function choose(p) {
      presetId = p ? p.id : "";
      picked = {};
      if (p) p.mods.forEach(function (id) { if (byId[id]) picked[id] = true; });
      paintPresets(); paintMods(); paint();
    }

    var tools = h("div", { class: "mon-tools" });
    function tool(label, fn) {
      var b = h("button", { type: "button", class: "btn btn-outline sm", text: label });
      b.addEventListener("click", fn); tools.appendChild(b);
    }
    tool("Everything", function () { setAll(function () { return true; }); });
    tool("Only the heavy ones", function () { setAll(function (m) { return tierOf(m) >= 4; }); });
    tool("My saved modules", function () {
      var s = Z.saved ? Z.saved.list() : [];
      if (!s.length) { Z.toast("You have not saved any modules yet. Star some on the Features page.", "bad"); return; }
      setAll(function (m) { return s.indexOf(m.id) >= 0; });
    });
    tool("Clear", function () { setAll(function () { return false; }); });

    var about = h("div", { class: "note-box" }, icon("info"), h("div", {},
      h("b", { text: "How to read this" }),
      h("p", { text: "Every module gets points from 1 to 11 depending on what it does each frame. Logic on the game tick and drawing on the HUD are cheap. Drawing in the world and scanning blocks or entities around you cost the most. The points add up to the gauge." }),
      h("p", { text: "This is an estimate worked out from the code, not a stopwatch. A module that scans a big area can cost more on a busy server than in an empty world. For the real numbers, turn on Performance Guard in the game, or use Show what each costs in the module list." })));

    var page = h("div", { class: "wrap page" },
      Z.pageHead("Performance monitor", "Performance monitor", "See roughly what a set of modules will cost you in FPS before you turn them on."),
      screen,
      h("h2", { class: "sec", style: "margin-top:34px", text: "Start from a playstyle" }), grid,
      h("h2", { class: "sec", style: "margin-top:34px", text: "Or pick modules yourself" }),
      tools,
      h("div", { class: "fsearch sm", style: "margin:12px 0" }, icon("search"), filterIn), listBox,
      h("div", { style: "margin-top:26px" }, about),
      h("div", { class: "cta-row", style: "margin-top:22px" },
        h("a", { class: "btn btn-primary", href: "/config", "data-link": "" }, icon("sliders"), h("span", { text: "Turn this into a config" })),
        h("a", { class: "btn btn-outline", href: "/features", "data-link": "" }, icon("cube"), h("span", { text: "Browse all modules" }))));

    Z.getJson("/presets.json").then(function (list) {
      presets = list || [];
      presets.forEach(function (p, i) {
        var total = 0, n = 0;
        p.mods.forEach(function (id) { if (byId[id]) { total += pts(byId[id]); n++; } });
        var b = h("button", { type: "button", class: "preset", "data-id": p.id, "aria-pressed": "false" },
          h("span", { class: "pi" }, icon(p.icon || "bolt")), h("b", { text: p.name }), h("span", { class: "pb", text: p.blurb }),
          h("small", { text: n + " modules, about " + total + " points" }));
        b.addEventListener("click", function () { choose(p); });
        grid.appendChild(b);
      });
      Z.stagger(grid.children, 45);
      Z.reveal(grid);
      var want = (/[?&]p=([a-z]+)/.exec(location.search) || [])[1];
      var pre = presets.filter(function (p) { return p.id === want; })[0] || presets[0];
      if (pre && !ids().length) choose(pre);
    }, function () { grid.appendChild(h("div", { class: "empty", text: "The playstyles could not be loaded." })); });

    paintMods(); paint();
    return page;
  };
})();
