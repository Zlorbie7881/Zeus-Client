/* Zeus Client website: the Features page (browse every module and setting) and the Credits page. No inline scripts (CSP). */
(function () {
  "use strict";
  var Z = window.Z;
  if (!Z) return;
  var h = Z.h, icon = Z.icon, $ = Z.$, clear = Z.clear, store = Z.store;

  function safeColor(c) { return /^#[0-9a-fA-F]{3,8}$/.test(c || "") ? c : "#4ea4ff"; }
  function num(n) { return String(Math.round(Number(n) * 1000) / 1000); }
  function plural(n, one, many) { return n + " " + (n === 1 ? one : (many || one + "s")); }
  function params() {
    var out = {};
    try { new URLSearchParams(location.search).forEach(function (v, k) { out[k] = v; }); } catch (e) { /* old browser */ }
    return out;
  }
  function setParams(patch, push) {
    var p = params();
    Object.keys(patch).forEach(function (k) { if (patch[k] === "" || patch[k] === null || patch[k] === undefined) delete p[k]; else p[k] = patch[k]; });
    var qs = Object.keys(p).map(function (k) { return encodeURIComponent(k) + "=" + encodeURIComponent(p[k]); }).join("&");
    var url = location.pathname + (qs ? "?" + qs : "");
    if (url === location.pathname + location.search) return;
    try { history[push ? "pushState" : "replaceState"](null, "", url); } catch (e) { /* ignore */ }
  }

  /* wrap the parts of `text` that match any search term in <mark> */
  function hl(text, terms) {
    text = String(text || "");
    if (!terms || !terms.length) return document.createTextNode(text);
    var low = text.toLowerCase(), spans = [];
    terms.forEach(function (t) {
      if (!t) return;
      var i = low.indexOf(t);
      while (i >= 0) { spans.push([i, i + t.length]); i = low.indexOf(t, i + t.length); }
    });
    if (!spans.length) return document.createTextNode(text);
    spans.sort(function (a, b) { return a[0] - b[0]; });
    var merged = [spans[0]];
    for (var k = 1; k < spans.length; k++) {
      var last = merged[merged.length - 1];
      if (spans[k][0] <= last[1]) last[1] = Math.max(last[1], spans[k][1]); else merged.push(spans[k]);
    }
    var frag = document.createDocumentFragment(), pos = 0;
    merged.forEach(function (s) {
      if (s[0] > pos) frag.appendChild(document.createTextNode(text.slice(pos, s[0])));
      frag.appendChild(h("mark", { text: text.slice(s[0], s[1]) }));
      pos = s[1];
    });
    if (pos < text.length) frag.appendChild(document.createTextNode(text.slice(pos)));
    return frag;
  }

  function valueLabel(s) {
    switch (s.type) {
      case "switch": return typeof s.default === "boolean" ? (s.default ? "On" : "Off") : "";
      case "slider": return s.default != null ? num(s.default) : "";
      case "choice": return s["default"] || "";
      case "text": return s["default"] || "";
      default: return "";
    }
  }
  var TYPE_LABEL = { "switch": "Switch", slider: "Slider", choice: "Choice", button: "Button", text: "Text", info: "Note", pick: "Picker" };

  /* ====================================================================== Features */
  Z.pages.features = function () {
    var F = Z.features || { categories: [], modules: [] };
    var byCat = {};
    F.categories.forEach(function (c) { byCat[c.id] = c; });
    var cats = F.categories.filter(function (c) { return F.modules.some(function (m) { return m.category === c.id; }); });
    var items = F.modules.map(function (m, i) {
      var sets = m.settings || [];
      var cat = byCat[m.category] || { id: m.category, label: "", color: "#4ea4ff" };
      return {
        m: m, i: i, cat: cat, sets: sets, nSet: sets.filter(function (s) { return s.type !== "info"; }).length,
        name: m.name.toLowerCase(), joined: m.name.toLowerCase().replace(/[^a-z0-9]/g, ""), desc: (m.description || "").toLowerCase(), catl: cat.label.toLowerCase(),
        alias: (m.aliases || []).map(function (a) { return a.toLowerCase(); }), rank: typeof m.rank === "number" ? m.rank : 999,
        sn: sets.map(function (s) { return s.name.toLowerCase(); }), sd: sets.map(function (s) { return (s.desc || "").toLowerCase(); }),
      };
    });
    var byId = {};
    items.forEach(function (it) { byId[it.m.id] = it; });
    var totalSettings = items.reduce(function (n, it) { return n + it.nSet; }, 0);
    var hudCount = items.filter(function (it) { return it.m.hud; }).length;

    var P = params();
    var state = {
      cat: byCat[P.c] ? P.c : "all", q: (P.q || "").slice(0, 80), hud: P.hud === "1",
      view: store("zeus.fview") === "list" ? "list" : "grid",
      sort: ["category", "popular", "az", "settings"].indexOf(store("zeus.fsort")) >= 0 ? store("zeus.fsort") : "category",
    };
    var visible = [];
    var drawer = null;

    /* ---------- header and tools ---------- */
    var statEls = {};
    function stat(key, label, value) {
      var n = h("b", { text: String(value) });
      statEls[key] = [n, value];
      return h("div", { class: "fstat" }, n, h("span", { text: label }));
    }
    var stats = h("div", { class: "fstats" },
      stat("m", "modules", F.modules.length), stat("s", "settings", totalSettings), stat("c", "categories", cats.length), stat("h", "HUD widgets", hudCount));

    var input = h("input", { type: "search", placeholder: "Search modules, settings and descriptions…", "aria-label": "Search modules and settings", autocomplete: "off", spellcheck: "false" });
    input.value = state.q;
    var clearBtn = h("button", { class: "clr", type: "button", "aria-label": "Clear search", title: "Clear" }, icon("close"));
    clearBtn.addEventListener("click", function () { input.value = ""; state.q = ""; sync(); input.focus(); });
    var searchBox = h("div", { class: "fsearch" }, icon("search"), input, clearBtn, h("kbd", { class: "fkey", text: "/" }));

    var COST = ["", "Minimal", "Low", "Medium", "High", "Very high"];
    function costTier(m) { return typeof m.cost === "number" ? m.cost : 2; }
    function costMeter(m, withText) {
      var t = costTier(m), el = h("span", { class: "cost c" + t, title: "Estimated cost: " + COST[t] + ". Worked out from what the module does each frame. In the game the real number is shown next to every module." });
      for (var i = 1; i <= 5; i++) el.appendChild(h("i", { class: i <= t ? "on" : "" }));
      if (withText) el.appendChild(h("em", { text: COST[t] }));
      return el;
    }
    function costWhy(m) {
      var r = m.runs || [], out = [];
      if (r.indexOf("tick") >= 0) out.push("does a little work every game tick (20 times a second)");
      if (r.indexOf("hud") >= 0) out.push("draws something on your screen every frame");
      if (r.indexOf("world") >= 0) out.push("draws in the world every frame");
      if (r.indexOf("scan") >= 0) out.push("scans the loaded chunks, which is the expensive part");
      return out;
    }
    var sortSel = h("select", { "aria-label": "Sort modules" },
      h("option", { value: "category", text: "Grouped by category" }), h("option", { value: "popular", text: "Most used first" }), h("option", { value: "az", text: "A to Z" }), h("option", { value: "settings", text: "Most settings first" }), h("option", { value: "costlow", text: "Cheapest first" }), h("option", { value: "costhigh", text: "Most expensive first" }));
    sortSel.value = state.sort;
    sortSel.addEventListener("change", function () { state.sort = sortSel.value; store("zeus.fsort", state.sort); render(); });

    var hudBtn = h("button", { class: "fbtn" + (state.hud ? " on" : ""), type: "button", title: "Only modules with a movable HUD widget", "aria-pressed": state.hud ? "true" : "false" }, icon("sliders"), h("span", { text: "HUD widgets" }));
    hudBtn.addEventListener("click", function () { state.hud = !state.hud; hudBtn.classList.toggle("on", state.hud); hudBtn.setAttribute("aria-pressed", state.hud ? "true" : "false"); sync(); });

    var heavyBtn = h("button", { class: "fbtn", type: "button", title: "Only the modules that cost the most frame rate", "aria-pressed": "false" }, icon("info"), h("span", { text: "Heavy on FPS" }));
    heavyBtn.addEventListener("click", function () { state.heavy = !state.heavy; heavyBtn.classList.toggle("on", state.heavy); heavyBtn.setAttribute("aria-pressed", state.heavy ? "true" : "false"); sync(); });

    var gridBtn = h("button", { type: "button", "aria-label": "Grid view", title: "Grid" }, icon("dots"));
    var listBtn = h("button", { type: "button", "aria-label": "List view", title: "List" }, icon("list"));
    var seg = h("div", { class: "fseg" }, gridBtn, listBtn);
    function paintSeg() {
      gridBtn.classList.toggle("on", state.view === "grid"); listBtn.classList.toggle("on", state.view === "list");
      gridBtn.setAttribute("aria-pressed", state.view === "grid" ? "true" : "false"); listBtn.setAttribute("aria-pressed", state.view === "list" ? "true" : "false");
    }
    gridBtn.addEventListener("click", function () { state.view = "grid"; store("zeus.fview", "grid"); paintSeg(); render(); });
    listBtn.addEventListener("click", function () { state.view = "list"; store("zeus.fview", "list"); paintSeg(); render(); });
    paintSeg();

    var bar = h("div", { class: "fbar" }, searchBox, h("div", { class: "fctl" }, sortSel, hudBtn, heavyBtn, seg));

    /* ---------- category rail ---------- */
    var rail = h("nav", { class: "frail", "aria-label": "Categories" });
    var railBtns = {};
    function railBtn(id, label, color, count) {
      var b = h("button", { type: "button", class: "frail-b", "data-cat": id },
        color ? h("span", { class: "dot", style: "background:" + safeColor(color) }) : icon("cube"),
        h("span", { class: "lbl", text: label }), h("span", { class: "cnt", text: String(count) }));
      b.addEventListener("click", function () { state.cat = id; sync(); var top = $(".fmain"); if (top && window.innerWidth < 900) top.scrollIntoView({ behavior: Z.reduced ? "auto" : "smooth", block: "start" }); });
      railBtns[id] = b;
      return b;
    }
    rail.appendChild(railBtn("all", "All modules", null, items.length));
    cats.forEach(function (c) { rail.appendChild(railBtn(c.id, c.label, c.color, items.filter(function (it) { return it.m.category === c.id; }).length)); });

    var info = h("div", { class: "finfo" });
    var list = h("div", { class: "flist" });
    var main = h("div", { class: "fmain" }, info, list);

    /* ---------- filtering ---------- */
    function terms() { return state.q.toLowerCase().split(/\s+/).filter(Boolean); }
    function match(it, ts) {
      if (state.cat !== "all" && it.m.category !== state.cat) return null;
      if (state.hud && !it.m.hud) return null;
      if (state.heavy && it.m.weight !== "heavy") return null;
      if (!ts.length) return { score: 0, via: null };
      var score = 0, via = null;
      for (var i = 0; i < ts.length; i++) {
        var t = ts[i], s = 0;
        if (it.name === t || it.joined === t) s = 30;
        else if (it.name.indexOf(t) === 0 || it.joined.indexOf(t) === 0) s = 14;
        else if (it.name.indexOf(t) > 0 || it.joined.indexOf(t) >= 0) s = 9;
        else if (it.alias.some(function (a) { return a === t || a.replace(/[^a-z0-9]/g, "") === t; })) { s = 28; via = via || null; }
        else if (it.alias.some(function (a) { return a.indexOf(t) === 0 || a.replace(/[^a-z0-9]/g, "").indexOf(t) === 0; })) s = 13;
        else if (it.catl.indexOf(t) >= 0) s = 4;
        else if (it.desc.indexOf(t) >= 0) s = 3;
        else {
          for (var k = 0; k < it.sn.length; k++) if (it.sn[k].indexOf(t) >= 0) { s = 2.5; if (!via) via = it.sets[k].name; break; }
          if (!s) for (var d = 0; d < it.sd.length; d++) if (it.sd[d].indexOf(t) >= 0) { s = 1; if (!via) via = it.sets[d].name; break; }
        }
        if (!s) return null;
        score += s;
      }
      return { score: score, via: via };
    }

    /* ---------- saved modules (kept in this browser; shown on the profile page) ---------- */
    function savedButton(id, cls) {
      var b = h("button", { type: "button", class: "star " + (cls || ""), "aria-label": "Save this module", title: "Save this module" }, icon("star"), icon("starfill"));
      function paint() {
        var on = Z.saved.has(id);
        b.classList.toggle("on", on);
        b.setAttribute("aria-pressed", on ? "true" : "false");
        b.title = on ? "Remove from saved modules" : "Save this module";
        b.setAttribute("aria-label", b.title);
      }
      b.addEventListener("click", function (e) { e.stopPropagation(); e.preventDefault(); var now = Z.saved.toggle(id); Z.toast(now ? "Saved. Find it on your profile page." : "Removed from your saved modules."); paint(); });
      b.addEventListener("keydown", function (e) { e.stopPropagation(); });
      paint();
      return b;
    }

    /* ---------- cards ---------- */
    function openCard(it) { openDrawer(it.m.id, null, true); }
    function card(it, ts, via, n) {
      var c = it.cat;
      var meta = h("div", { class: "fm-meta" });
      if (it.m.hud) meta.appendChild(h("span", { class: "tag hud", title: "Has a HUD widget you can move in /zeus hud" }, icon("sliders", "small"), "HUD"));
      meta.appendChild(costMeter(it.m, true));
      meta.appendChild(h("span", { class: "tag" }, it.nSet ? plural(it.nSet, "setting") : "No settings"));
      if (state.cat === "all" && state.sort !== "category") meta.appendChild(h("span", { class: "tag cat", style: "--c:" + safeColor(c.color) }, c.label));
      var aliasHit = ts.length && it.alias.length && ts.some(function (t) { return it.name.indexOf(t) < 0 && it.alias.some(function (a) { return a.indexOf(t) >= 0; }); }) ? it.m.aliases.join(", ") : "";
      var star = Z.saved ? savedButton(it.m.id, "fm-star") : null;
      var body = h("div", { class: "fm" + (state.view === "list" ? " row" : ""), tabindex: "0", role: "button", "aria-label": it.m.name + ", open details", style: "--c:" + safeColor(c.color) },
        h("div", { class: "fm-top" }, h("b", {}, h("span", { class: "dot" }), hl(it.m.name, ts)), meta, star),
        aliasHit ? h("div", { class: "fm-alias" }, "Also known as ", h("b", { text: aliasHit })) : null,
        h("p", {}, hl(it.m.description || "", ts)),
        via ? h("div", { class: "fm-via" }, icon("sliders", "small"), h("span", {}, "Setting: ", h("mark", { text: via }))) : null);
      if (!Z.reduced) { body.style.animation = "rise .42s cubic-bezier(.2,.7,.2,1) both"; body.style.animationDelay = Math.min(n, 16) * 20 + "ms"; }
      body.addEventListener("click", function () { openCard(it); });
      body.addEventListener("keydown", function (e) { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); openCard(it); } });
      return body;
    }

    function render() {
      var ts = terms();
      var res = [];
      items.forEach(function (it) { var m = match(it, ts); if (m) res.push({ it: it, score: m.score, via: m.via }); });
      if (ts.length && state.sort === "category") res.sort(function (a, b) { return b.score - a.score || a.it.i - b.it.i; });
      else if (state.sort === "popular") res.sort(function (a, b) { return a.it.rank - b.it.rank || a.it.i - b.it.i; });
      else if (state.sort === "az") res.sort(function (a, b) { return a.it.name < b.it.name ? -1 : a.it.name > b.it.name ? 1 : 0; });
      else if (state.sort === "costlow") res.sort(function (a, b) { return costTier(a.it.m) - costTier(b.it.m) || a.it.i - b.it.i; });
      else if (state.sort === "costhigh") res.sort(function (a, b) { return costTier(b.it.m) - costTier(a.it.m) || a.it.i - b.it.i; });
      else if (state.sort === "settings") res.sort(function (a, b) { return b.it.nSet - a.it.nSet || a.it.i - b.it.i; });
      visible = res.map(function (r) { return r.it; });

      clear(list);
      list.className = "flist " + state.view;
      var grouped = state.sort === "category" && !ts.length && state.cat === "all";
      var n = 0;
      if (!res.length) {
        list.appendChild(h("div", { class: "empty fempty" }, h("b", { text: "Nothing matches that." }),
          h("span", { text: "Try a shorter word, another category, or ask the helper." }),
          h("button", { class: "btn btn-outline sm", type: "button", onclick: function () { if (Z.helper) Z.helper.ask(state.q || "What can Zeus do?"); } }, icon("chat", "small"), "Ask the helper")));
      } else if (grouped) {
        cats.forEach(function (c) {
          var group = res.filter(function (r) { return r.it.m.category === c.id; });
          if (!group.length) return;
          var sec = h("section", { class: "fgroup", id: "cat-" + c.id, style: "--c:" + safeColor(c.color) },
            h("header", {}, h("span", { class: "bar" }), h("h2", { text: c.label }), h("span", { class: "cnt", text: plural(group.length, "module") }),
              h("button", { class: "only", type: "button", onclick: function () { state.cat = c.id; sync(); } }, "Show only")),
            h("div", { class: "fgrid " + state.view }, group.map(function (r) { return card(r.it, ts, r.via, n++); })));
          list.appendChild(sec);
        });
      } else {
        list.appendChild(h("div", { class: "fgrid " + state.view }, res.map(function (r) { return card(r.it, ts, r.via, n++); })));
      }

      var label = state.cat === "all" ? "All modules" : (byCat[state.cat] || {}).label;
      info.textContent = "";
      info.appendChild(h("span", {}, h("b", { text: String(res.length) }), " " + (res.length === 1 ? "module" : "modules") + " · " + label + (ts.length ? " · “" + state.q.trim() + "”" : "")));
      if (ts.length || state.cat !== "all" || state.hud || state.heavy) info.appendChild(h("button", { class: "reset", type: "button", onclick: function () { state.q = ""; state.cat = "all"; state.hud = false; state.heavy = false; heavyBtn.classList.remove("on"); input.value = ""; hudBtn.classList.remove("on"); sync(); } }, "Reset filters"));
      Object.keys(railBtns).forEach(function (id) { railBtns[id].classList.toggle("on", id === state.cat); });
    }
    function sync() {
      setParams({ c: state.cat === "all" ? "" : state.cat, q: state.q.trim(), hud: state.hud ? "1" : "" });
      render();
    }
    input.addEventListener("input", Z.debounce(function () { state.q = input.value; sync(); }, 110));
    input.addEventListener("keydown", function (e) { if (e.key === "Escape" && input.value) { input.value = ""; state.q = ""; sync(); e.stopPropagation(); } });

    /* ---------- which releases touched this module (from the update timeline) ---------- */
    function releasesBlock(id) {
      var rel = (Z.timeline || []).filter(function (u) { return (u.mods || []).indexOf(id) >= 0; });
      if (!rel.length) return null;
      return h("div", { class: "drel" }, h("span", { class: "tl-lab", text: "In releases" }),
        rel.slice(0, 8).map(function (u) { return h("a", { class: "tl-chip", href: "/information#timeline", title: u.title }, "v" + u.version); }));
    }

    /* ---------- detail drawer ---------- */
    function closeDrawer(skipUrl) {
      if (!drawer) return;
      var d = drawer; drawer = null;
      d.root.classList.remove("in");
      document.body.classList.remove("no-scroll");
      setTimeout(function () { if (d.root.parentNode) d.root.parentNode.removeChild(d.root); }, 240);
      if (!skipUrl) setParams({ m: "", s: "" });
      try { if (d.opener && d.opener.isConnected) d.opener.focus({ preventScroll: true }); } catch (e) { /* ignore */ }
    }
    function openDrawer(id, focusSetting, push) {
      var it = byId[id];
      if (!it) return;
      var terms0 = terms();
      var opener = document.activeElement;
      if (drawer) { opener = drawer.opener; drawer.root.parentNode && drawer.root.parentNode.removeChild(drawer.root); drawer = null; }
      var c = it.cat;
      var pos = visible.indexOf(it);

      var filter = h("input", { type: "search", placeholder: "Filter " + plural(it.nSet, "setting") + "…", "aria-label": "Filter settings", autocomplete: "off", spellcheck: "false" });
      var rows = h("div", { class: "srows" });
      var count = h("span", { class: "scount" });
      function paintRows() {
        var q = filter.value.toLowerCase().trim(), qs = q.split(/\s+/).filter(Boolean), shown = 0;
        clear(rows);
        it.sets.forEach(function (s, k) {
          var hay = it.sn[k] + " " + it.sd[k];
          for (var i = 0; i < qs.length; i++) if (hay.indexOf(qs[i]) < 0) return;
          shown++;
          rows.appendChild(settingRow(s, qs));
        });
        if (!shown) rows.appendChild(h("div", { class: "empty", text: "No setting matches that." }));
        count.textContent = q ? shown + " of " + it.sets.length : plural(it.sets.length, "setting");
      }
      function settingRow(s, qs) {
        var detail = [];
        if (s.type === "slider") detail.push(h("span", { class: "chip2" }, num(s.min) + " to " + num(s.max) + (s.step ? ", step " + num(s.step) : "")));
        if (s.type === "choice") (s.options || []).forEach(function (o) { detail.push(h("span", { class: "chip2" + (o === s["default"] ? " def" : "") , text: o })); });
        var dv = valueLabel(s);
        var right = h("div", { class: "srow-r" }, h("span", { class: "ty " + s.type, text: TYPE_LABEL[s.type] || s.type }), dv && s.type !== "choice" ? h("span", { class: "dv" }, h("small", { text: "default" }), dv) : null);
        var row = h("div", { class: "srow", "data-name": s.name }, h("div", { class: "srow-l" }, h("b", {}, hl(s.name, qs)), h("p", {}, hl(s.desc || "", qs)), detail.length ? h("div", { class: "sdet" }, detail) : null), right);
        return row;
      }
      filter.addEventListener("input", Z.debounce(paintRows, 80));

      var prev = h("button", { class: "dbtn", type: "button", "aria-label": "Previous module", title: "Previous (←)" }, icon("arrow", "flip"));
      var next = h("button", { class: "dbtn", type: "button", "aria-label": "Next module", title: "Next (→)" }, icon("arrow"));
      var share = h("button", { class: "dbtn", type: "button", "aria-label": "Copy link to this module", title: "Copy link" }, icon("copy"));
      var closeB = h("button", { class: "dbtn", type: "button", "aria-label": "Close", title: "Close (Esc)" }, icon("close"));
      function go(delta) {
        if (!visible.length) return;
        var i = visible.indexOf(it);
        var t = visible[(i < 0 ? 0 : i + delta + visible.length) % visible.length];
        if (t) openDrawer(t.m.id, null, false);
      }
      prev.addEventListener("click", function () { go(-1); });
      next.addEventListener("click", function () { go(1); });
      closeB.addEventListener("click", function () { closeDrawer(); });
      share.addEventListener("click", function () {
        var url = location.origin + "/features?m=" + encodeURIComponent(it.m.id);
        var done = function () { Z.toast("Link copied"); };
        if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(url).then(done, function () { Z.toast(url); }); else Z.toast(url);
      });
      var saveBtn = Z.saved ? savedButton(it.m.id, "dbtn") : null;
      var ask = h("button", { class: "btn btn-primary sm", type: "button" }, icon("chat", "small"), "Ask the helper");
      ask.addEventListener("click", function () { var q = "What does " + it.m.name + " do?"; closeDrawer(); if (Z.helper) Z.helper.ask(q); });

      var where = "Press Z in a world, open " + c.label + " and click the " + it.m.name + " row.";
      var panel = h("aside", { class: "drawer", role: "dialog", "aria-modal": "true", "aria-label": it.m.name, style: "--c:" + safeColor(c.color) },
        h("header", { class: "dh" },
          h("div", { class: "dh-t" }, h("span", { class: "dcat" }, h("span", { class: "dot" }), c.label), h("h2", {}, hl(it.m.name, terms0))),
          h("div", { class: "dh-b" }, pos >= 0 && visible.length > 1 ? [prev, next] : null, saveBtn, share, closeB)),
        h("div", { class: "db" },
          h("p", { class: "ddesc" }, hl(it.m.description || "", terms0)),
          it.m.aliases && it.m.aliases.length ? h("p", { class: "dalias" }, "Also known as ", h("b", { text: it.m.aliases.join(", ") })) : null,
          h("div", { class: "dchips" },
            h("span", { class: "tag" }, plural(it.nSet, "setting")),
            costMeter(it.m, true),
            it.m.hud ? h("span", { class: "tag hud" }, icon("sliders", "small"), "Movable HUD widget") : null),
          h("div", { class: "dact" }, ask),
          h("p", { class: "dhint" }, icon("info", "small"), where + (it.m.hud ? " Move it with /zeus hud." : "")),
          h("div", { class: "dcost" }, h("h3", { text: "Estimated cost" }),
            h("p", {}, costMeter(it.m, true), h("span", { text: " " + (costWhy(it.m).length ? "It " + costWhy(it.m).join(", and ") + "." : "It only reacts to something happening, so it costs almost nothing while it waits.") })),
            h("p", { class: "muted", text: "This is an estimate from what the module does, not a measurement. Switch on Show what each costs in the Zeus menu to see the real figure on your computer." }),
            h("p", {}, h("a", { href: "/monitor", "data-link": "" }, "Open the performance monitor"), h("span", { class: "muted", text: " to see what a whole set of modules adds up to." }))),
          releasesBlock(it.m.id),
          Z.mediaBlock ? Z.mediaBlock(it.m) : null,
          it.sets.length ? h("div", { class: "dsets" },
            h("div", { class: "dsets-h" }, h("h3", { text: "Settings" }), count),
            it.sets.length > 6 ? h("div", { class: "fsearch sm" }, icon("search"), filter) : null,
            rows) : h("p", { class: "muted", text: "This module has no settings. You switch it on or off." })));
      var bg = h("div", { class: "drawer-bg" });
      bg.addEventListener("click", function () { closeDrawer(); });
      var root = h("div", { class: "drawer-root" }, bg, panel);
      document.body.appendChild(root);
      document.body.classList.add("no-scroll");
      drawer = { root: root, opener: opener, id: id, panel: panel };
      paintRows();
      setParams({ m: id, s: focusSetting || "" }, push);
      requestAnimationFrame(function () { root.classList.add("in"); closeB.focus({ preventScroll: true }); });
      if (focusSetting) {
        setTimeout(function () {
          var row = [].filter.call(rows.querySelectorAll(".srow"), function (r) { return r.getAttribute("data-name") === focusSetting; })[0];
          if (row) { row.classList.add("flash"); row.scrollIntoView({ block: "center", behavior: Z.reduced ? "auto" : "smooth" }); }
        }, 260);
      }
    }

    /* open from the URL, and follow back/forward and in-site links */
    function fromUrl() {
      var p = params();
      if (p.m && byId[p.m]) { if (!drawer || drawer.id !== p.m) openDrawer(p.m, p.s || null, false); }
      else if (drawer) closeDrawer(true);
    }
    function onQuery() {
      var p = params();
      var c = byCat[p.c] ? p.c : "all";
      if (c !== state.cat || (p.q || "") !== state.q.trim() || (p.hud === "1") !== state.hud) {
        state.cat = c; state.q = p.q || ""; state.hud = p.hud === "1"; input.value = state.q; hudBtn.classList.toggle("on", state.hud); render();
      }
      fromUrl();
    }
    function onKey(e) {
      var tag = (e.target && e.target.tagName) || "";
      var typing = tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT";
      if (e.key === "Escape" && drawer) { e.preventDefault(); closeDrawer(); return; }
      if (drawer && !typing && (e.key === "ArrowRight" || e.key === "ArrowLeft")) {
        e.preventDefault();
        var i = visible.indexOf(byId[drawer.id]);
        if (visible.length > 1 && i >= 0) openDrawer(visible[(i + (e.key === "ArrowRight" ? 1 : -1) + visible.length) % visible.length].m.id, null, false);
        return;
      }
      if (e.key === "/" && !typing && !e.ctrlKey && !e.metaKey && !drawer) { e.preventDefault(); input.focus(); input.select(); }
    }
    document.addEventListener("zeus:query", onQuery);
    document.addEventListener("keydown", onKey);
    Z.onLeave(function () {
      document.removeEventListener("zeus:query", onQuery);
      document.removeEventListener("keydown", onKey);
      closeDrawer(true);
    });

    var page = h("div", { class: "wrap page feat" },
      Z.pageHead("Features", "Everything Zeus can do", "Browse all " + F.modules.length + " modules and their " + totalSettings + " settings. Click a module to see every setting, its range and its default."),
      stats, bar,
      h("div", { class: "fbody" }, rail, main));
    render();
    // count the numbers up once the page is really on screen
    var tries = 0;
    (function startCounts() {
      if (!page.isConnected) { if (tries++ < 40) setTimeout(startCounts, 50); return; }
      Object.keys(statEls).forEach(function (k) { Z.countUp(statEls[k][0], statEls[k][1]); });
    })();
    setTimeout(fromUrl, 0);
    return page;
  };

  /* ====================================================================== Credits */
  function hue(name) { var n = 0; for (var i = 0; i < name.length; i++) n = (n * 31 + name.charCodeAt(i)) % 360; return n; }

  Z.pages.credits = function () {
    var C = Z.content || {};
    var people = (C.credits || []);
    var refs = (C.references || []);

    var cards = people.map(function (p, i) {
      var lead = i === 0;
      var avatar = lead ? h("div", { class: "avatar logo" }, Z.logo(54))
        : h("div", { class: "avatar", style: "--h:" + hue(p.name) }, h("span", { text: p.name.replace(/[^A-Za-z0-9]/g, "").charAt(0).toUpperCase() }));
      return h("article", { class: "person" + (lead ? " lead" : "") },
        avatar,
        h("div", { class: "pbody" },
          h("div", { class: "prole", text: p.role || "" }),
          h("h3", { text: p.name }),
          p.tag ? h("div", { class: "ptag" }, icon("check", "small"), p.tag) : null,
          h("p", { text: p.text || "" })));
    });
    var pg = h("div", { class: "people" }, cards);
    Z.stagger(pg.children, 90);

    var rg = h("div", { class: "refs" }, refs.map(function (r) {
      var url = Z.safeUrl(r.url);
      var body = [
        h("div", { class: "rtop" }, h("span", { class: "ri" }, icon(r.icon || "puzzle")), h("span", { class: "rkind", text: r.kind || "" })),
        h("h3", { text: r.name }),
        h("p", { text: r.text || "" }),
        url ? h("span", { class: "rlink" }, "Visit", icon("arrow", "small")) : null,
      ];
      return url ? h("a", { class: "ref", href: url, target: "_blank", rel: "noopener noreferrer" }, body) : h("div", { class: "ref" }, body);
    }));
    Z.stagger(rg.children, 70);

    var L = Z.site.links;
    return h("div", { class: "wrap page" },
      Z.pageHead("Credits", "Made by people, for people", "Who built Zeus Client, who shaped it, and the projects it learned from."),
      pg,
      h("h2", { class: "sec", style: "margin-top:46px", text: "References and inspiration" }),
      h("p", { class: "muted", style: "max-width:720px;margin:0 0 22px", text: "Zeus is its own client, but no project starts from nothing. These are the things it was shaped by or builds on. Having a reference does not mean the project is affiliated with Zeus." }),
      rg,
      h("div", { class: "card cr-foot", "data-reveal": "" },
        h("div", {}, h("b", { text: "Missing someone?" }), h("p", { class: "muted", text: "If your work helped Zeus and you are not listed, tell us and we will add you." })),
        L.discord ? h("a", { class: "btn btn-outline", href: L.discord, target: "_blank", rel: "noopener noreferrer" }, icon("discord"), "Join the Discord") : null),
      h("p", { class: "faint", style: "margin-top:26px;font-size:13.5px", text: "Third-party licence notices ship with the mod in the NOTICE file. " + (C.disclaimer || "") }));
  };
})();
