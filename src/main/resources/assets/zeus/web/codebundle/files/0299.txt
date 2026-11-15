/* Zeus control center (the Settings page). Talks to the running game through the local API. */
(function () {
  "use strict";
  var Z = window.Z;
  var h = Z.h, icon = Z.icon, $ = Z.$, clear = Z.clear, toast = Z.toast, debounce = Z.debounce;

  var TABS = [
    ["overview", "Overview", "bolt"],
    ["modules", "Modules", "puzzle"],
    ["presets", "Presets", "sliders"],
    ["scheduler", "Scheduler", "clock"],
    ["market", "Market", "trophy"],
    ["activity", "Activity", "list"],
  ];

  var timers = [];
  function every(ms, fn) {
    var t = setInterval(function () { if (!document.hidden) fn(); }, ms);
    timers.push(t);
    return t;
  }
  function stopAll() { timers.forEach(clearInterval); timers = []; closeSheet(); }

  var state = {
    connected: false,
    tab: Z.store("zeus.tab") || "overview",
    modules: null,   // array from /api/modules
    status: null,
  };

  function failed(e) {
    if (e && e.status === 401) { setConnected(false); toast("The access token was not accepted. Copy it again from Minecraft.", "bad"); }
    else toast((e && e.message) || "Request failed", "bad");
  }

  /* ---------------- page ---------------- */
  Z.pages.settings = function () {
    var root = h("div", { class: "wrap page" }, Z.pageHead("Settings", "Control center", "Control the running game from your browser: switch modules, tune every setting, save presets and schedule changes."));
    var body = h("div", { id: "dashBody" });
    var conn = h("div", { class: "card", id: "connCard", style: "margin-bottom:24px" });
    root.appendChild(conn);
    root.appendChild(body);
    Z.onLeave(stopAll);
    drawConn(conn, body);
    if (Z.store("zeus.token")) connect(Z.token || Z.store("zeus.token"), conn, body, true);
    return root;
  };

  function setConnected(v) {
    state.connected = v;
    var conn = $("#connCard"), body = $("#dashBody");
    if (!conn || !body) return;
    drawConn(conn, body);
    if (!v) { stopAll(); clear(body); }
  }

  function drawConn(conn, body) {
    clear(conn);
    var local = Z.site.local;
    var input = h("input", { class: "field", type: "password", placeholder: "Paste the access token from Minecraft", style: "flex:1;min-width:240px", autocomplete: "off", spellcheck: "false", "aria-label": "Access token" });
    var go = h("button", { class: "btn btn-primary flat", type: "button" }, "Connect");
    var out = h("button", { class: "btn btn-outline sm", type: "button" }, "Disconnect");
    go.addEventListener("click", function () { connect(input.value.trim(), conn, body, false); });
    input.addEventListener("keydown", function (e) { if (e.key === "Enter") go.click(); });
    out.addEventListener("click", function () {
      Z.token = ""; Z.store("zeus.token", null);
      state.connected = false; stopAll(); clear(body); drawConn(conn, body);
    });
    var left;
    if (!local) {
      left = h("div", {},
        h("div", { class: "card-title" }, icon("gear"), "The control center runs inside your game"),
        h("p", { class: "muted", style: "margin:10px 0 0", text: "The control center talks to a running game, so it is only available when you open this site from the link Zeus prints inside Minecraft. In the game run /zeus website (or enable Interface → Web Dashboard) and open that link." }),
        h("div", { class: "row" }, h("a", { class: "btn btn-outline sm", href: "/help#website" }, "How the website works")));
    } else if (state.connected) {
      left = h("div", {},
        h("div", { class: "card-title" }, h("span", { class: "dot-live on" }), "Connected to your game"),
        h("p", { class: "muted", style: "margin:8px 0 0", text: "Changes apply instantly." }),
        h("div", { class: "row" }, out));
    } else {
      left = h("div", {},
        h("div", { class: "card-title" }, h("span", { class: "dot-live" }), "Connect to your game"),
        h("p", { class: "muted", style: "margin:8px 0 0", text: "In Minecraft press the Zeus menu → Interface → Web Dashboard → “Copy the access token”, or run /zeus website and click the token in chat. Paste it here." }),
        h("div", { class: "row" }, input, go));
    }
    conn.appendChild(left);
  }

  function connect(token, conn, body, silent) {
    if (!token) { if (!silent) toast("Paste the access token first", "bad"); return; }
    Z.token = token;
    Z.api("GET", "/api/status").then(function (st) {
      Z.store("zeus.token", token);
      state.status = st;
      state.connected = true;
      drawConn(conn, body);
      showTab(body);
    }).catch(function (e) {
      Z.token = "";
      if (silent) { Z.store("zeus.token", null); drawConn(conn, body); return; }
      failed(e);
    });
  }

  function showTab(body) {
    stopAll();
    clear(body);
    var tabs = h("div", { class: "tabs", role: "tablist" });
    TABS.forEach(function (t) {
      var b = h("button", { type: "button", role: "tab", class: state.tab === t[0] ? "on" : "", "data-tab": t[0] }, icon(t[2]), t[1]);
      b.addEventListener("click", function () { state.tab = t[0]; Z.store("zeus.tab", t[0]); showTab(body); });
      tabs.appendChild(b);
    });
    var pane = h("div", { id: "pane" });
    body.appendChild(tabs);
    body.appendChild(pane);
    (({ overview: overview, modules: modulesTab, presets: presetsTab, scheduler: schedulerTab, market: marketTab, activity: activityTab })[state.tab] || overview)(pane);
  }

  /* ---------------- overview ---------------- */
  function tile(label, value, bar, cls) {
    return h("div", { class: "tile" }, h("small", { text: label }), h("b", { text: value }), bar === undefined ? null : h("div", { class: "bar " + (cls || "") }, h("i", { style: "width:" + Math.max(0, Math.min(100, bar)) + "%" })));
  }

  function overview(pane) {
    var tiles = h("div", { class: "tiles" });
    var panic = h("button", { class: "btn btn-danger", type: "button" }, icon("power", "small"), "Panic: switch everything off");
    panic.addEventListener("click", function () {
      if (!confirm("Switch off every Zeus module now?")) return;
      Z.api("POST", "/api/panic", {}).then(function (r) { toast(r.message, "good"); setTimeout(refresh, 600); }).catch(failed);
    });
    pane.appendChild(tiles);
    pane.appendChild(h("div", { class: "toolbar" }, panic));
    pane.appendChild(h("h2", { class: "sec", text: "Switched on right now" }));
    var onList = h("div", { class: "mods" });
    pane.appendChild(onList);

    function refresh() {
      Promise.all([Z.api("GET", "/api/status"), Z.api("GET", "/api/modules")]).then(function (r) {
        var s = r[0]; state.status = s; state.modules = r[1].modules;
        clear(tiles);
        tiles.appendChild(tile("Zeus", "v" + s.version));
        tiles.appendChild(tile("Modules on", s.enabled + " / " + s.moduleCount, s.moduleCount ? s.enabled * 100 / s.moduleCount : 0));
        tiles.appendChild(tile("FPS", String(Math.round(s.fps || 0))));
        tiles.appendChild(tile("Game", s.inWorld ? "In a world" : "In menus"));
        if (s.inWorld) {
          tiles.appendChild(tile("Player", s.player));
          tiles.appendChild(tile("Server", s.server || "–"));
          if (s.ping !== undefined) tiles.appendChild(tile("Ping", s.ping + " ms"));
          tiles.appendChild(tile("Health", s.health + " / " + s.maxHealth, s.maxHealth ? s.health * 100 / s.maxHealth : 0, "hp"));
          tiles.appendChild(tile("Food", String(s.food), s.food * 5, "food"));
          tiles.appendChild(tile("Position", Math.round(s.x) + ", " + Math.round(s.y) + ", " + Math.round(s.z)));
          tiles.appendChild(tile("Dimension", String(s.dimension || "").replace(/^.*\//, "").replace(/\]$/, "")));
        }
        clear(onList);
        var on = state.modules.filter(function (m) { return m.enabled && m.toggleable; });
        if (!on.length) onList.appendChild(h("div", { class: "empty", style: "grid-column:1/-1", text: "No modules are switched on." }));
        on.forEach(function (m) {
          var card = h("div", { class: "mod" }, h("b", {}, m.name), h("p", { text: m.info || m.description }));
          onList.appendChild(card);
        });
      }).catch(failed);
    }
    refresh();
    every(2000, refresh);
  }

  /* ---------------- modules ---------------- */
  var CAT_COLORS = {};

  function favs() { try { return JSON.parse(Z.store("zeus.favs") || "[]"); } catch (e) { return []; } }
  function saveFavs(a) { Z.store("zeus.favs", JSON.stringify(a)); }

  function modulesTab(pane) {
    var f = { q: "", cat: "all", on: false, fav: false };
    var cards = {};      // id -> {el, sw, info, m}
    var order = [];
    var grid = h("div", { class: "mcards" });
    var count = h("span", { class: "faint" });
    var favSet = favs();
    var catSel = h("select", { class: "field", "aria-label": "Category" }, h("option", { value: "all", text: "All categories" }));
    var q = h("input", { type: "search", placeholder: "Search modules…", "aria-label": "Search modules", autocomplete: "off", spellcheck: "false" });
    var onBtn = h("button", { class: "chip", type: "button" }, "Enabled only");
    var favBtn = h("button", { class: "chip", type: "button" }, icon("heart", "small"), "Favorites");
    var allOff = h("button", { class: "btn btn-outline sm", type: "button" }, "Switch visible off");

    pane.appendChild(h("div", { class: "toolbar" }, h("div", { class: "search" }, icon("search"), q), catSel, onBtn, favBtn, allOff));
    pane.appendChild(h("div", { style: "margin-bottom:12px" }, count));
    pane.appendChild(grid);

    function matches(c) {
      var m = c.m;
      if (f.cat !== "all" && m.category !== f.cat) return false;
      if (f.on && !m.enabled) return false;
      if (f.fav && favSet.indexOf(m.id) < 0) return false;
      if (f.terms) for (var i = 0; i < f.terms.length; i++) if (c.hay.indexOf(f.terms[i]) < 0) return false;
      return true;
    }
    function applyFilter() {
      var qq = f.q.trim().toLowerCase();
      f.terms = qq ? qq.split(/\s+/) : null;
      var n = 0;
      order.forEach(function (id) {
        var c = cards[id];
        var show = matches(c);
        if (c.shown !== show) { c.el.hidden = !show; c.shown = show; }
        if (show) n++;
      });
      count.textContent = n + " module" + (n === 1 ? "" : "s") + " shown";
    }
    q.addEventListener("input", debounce(function () { f.q = q.value; applyFilter(); }, 90));
    catSel.addEventListener("change", function () { f.cat = catSel.value; applyFilter(); });
    onBtn.addEventListener("click", function () { f.on = !f.on; onBtn.classList.toggle("on", f.on); applyFilter(); });
    favBtn.addEventListener("click", function () { f.fav = !f.fav; favBtn.classList.toggle("on", f.fav); applyFilter(); });
    allOff.addEventListener("click", function () {
      var ids = order.filter(function (id) { var c = cards[id]; return c.shown && c.m.enabled && c.m.toggleable; });
      if (!ids.length) { toast("Nothing visible is switched on"); return; }
      Z.api("POST", "/api/modules/bulk", { ids: ids, enabled: false }).then(function (r) { toast(r.message, "good"); load(); }).catch(failed);
    });

    function build(m) {
      var sw = m.toggleable ? h("button", { class: "sw" + (m.enabled ? " on" : ""), type: "button", role: "switch", "aria-checked": String(m.enabled), "aria-label": "Toggle " + m.name }) : null;
      var fav = h("button", { class: "fav" + (favSet.indexOf(m.id) >= 0 ? " on" : ""), type: "button", "aria-label": "Favorite " + m.name, title: "Favorite" }, icon("heart"));
      var info = h("span", { class: "info", text: m.info || "" });
      var set = m.settings > 0 ? h("button", { class: "btn btn-outline sm", type: "button" }, icon("sliders", "small"), "Settings") : null;
      var el = h("div", { class: "mc" + (m.enabled ? " on" : "") + (m.error ? " err" : "") },
        h("div", { class: "mc-top" }, h("div", {}, h("small", { text: m.categoryLabel }), h("b", { text: m.name })), h("div", { style: "display:flex;gap:10px;align-items:center" }, fav, sw)),
        h("p", { text: m.error ? "Switched off after an error: " + m.error : m.description }),
        h("div", { class: "mc-foot" }, info, set));
      var c = { el: el, sw: sw, info: info, m: m, shown: true, hay: (m.name + " " + m.description + " " + m.categoryLabel).toLowerCase() };
      if (sw) sw.addEventListener("click", function () {
        var want = !c.m.enabled;
        Z.api("POST", "/api/module", { id: m.id, enabled: want }).then(function (r) { toast(r.message, "good"); load(); }).catch(failed);
      });
      fav.addEventListener("click", function () {
        var i = favSet.indexOf(m.id);
        if (i >= 0) favSet.splice(i, 1); else favSet.push(m.id);
        saveFavs(favSet);
        fav.classList.toggle("on", i < 0);
        if (f.fav) applyFilter();
      });
      if (set) set.addEventListener("click", function () { openSheet(m); });
      return c;
    }

    function sync(m) {
      var c = cards[m.id];
      if (!c) return;
      var was = c.m;
      c.m = m;
      if (was.enabled !== m.enabled) {
        c.el.classList.toggle("on", m.enabled);
        if (c.sw) { c.sw.classList.toggle("on", m.enabled); c.sw.setAttribute("aria-checked", String(m.enabled)); }
      }
      if (was.info !== m.info) c.info.textContent = m.info || "";
      if (f.on && was.enabled !== m.enabled) c.shown = null; // force re-evaluation
    }

    var first = true;
    function load() {
      Z.api("GET", "/api/modules").then(function (r) {
        state.modules = r.modules;
        if (first) {
          first = false;
          var cats = {};
          var frag = document.createDocumentFragment();
          r.modules.forEach(function (m) {
            cats[m.category] = m.categoryLabel;
            var c = build(m);
            cards[m.id] = c; order.push(m.id);
            frag.appendChild(c.el);
          });
          Object.keys(cats).forEach(function (k) { catSel.appendChild(h("option", { value: k, text: cats[k] })); });
          grid.appendChild(frag);
        } else r.modules.forEach(sync);
        applyFilter();
      }).catch(failed);
    }
    load();
    every(2500, load);
  }

  /* ---------------- settings sheet ---------------- */
  var sheetEl = null, sheetTimer = null;
  function closeSheet() {
    if (sheetTimer) { clearInterval(sheetTimer); sheetTimer = null; }
    if (sheetEl && sheetEl.parentNode) sheetEl.parentNode.removeChild(sheetEl);
    sheetEl = null;
    document.removeEventListener("keydown", escClose);
  }
  function escClose(e) { if (e.key === "Escape") closeSheet(); }

  function openSheet(m) {
    closeSheet();
    var rows = {};   // setting name -> {row, update}
    var list = h("div", { class: "sheet-b" }, h("div", { class: "empty", text: "Loading…" }));
    var sw = m.toggleable ? h("button", { class: "sw" + (m.enabled ? " on" : ""), type: "button", role: "switch", "aria-label": "Toggle " + m.name }) : null;
    var close = h("button", { class: "btn btn-outline sm", type: "button", "aria-label": "Close" }, icon("close", "small"));
    var sheet = h("div", { class: "sheet", role: "dialog", "aria-modal": "true", "aria-label": m.name + " settings" },
      h("div", { class: "sheet-h" }, h("div", { style: "flex:1;min-width:0" }, h("h3", { text: m.name }), h("p", { text: m.description })), sw, close), list);
    sheetEl = h("div", { class: "modal" }, sheet);
    sheetEl.addEventListener("mousedown", function (e) { if (e.target === sheetEl) closeSheet(); });
    close.addEventListener("click", closeSheet);
    document.addEventListener("keydown", escClose);
    document.body.appendChild(sheetEl);
    close.focus();

    function setEnabled(v) { if (sw) sw.classList.toggle("on", v); }
    if (sw) sw.addEventListener("click", function () {
      var want = !sw.classList.contains("on");
      Z.api("POST", "/api/module", { id: m.id, enabled: want }).then(function (r) { setEnabled(want); toast(r.message, "good"); }).catch(failed);
    });

    function push(s, value) {
      return Z.api("POST", "/api/setting", { id: m.id, name: s.name, value: value }).then(function (r) { applyAll(r.settings); setEnabled(r.enabled); }).catch(function (e) { failed(e); fetchAll(); });
    }

    function make(s) {
      var label = h("label", {}, s.name, s.desc ? h("small", { text: s.desc }) : null);
      var val = h("div", { class: "val" });
      var row = h("div", { class: "ctl" + (s.type === "info" ? " full" : "") }, label, val);
      var update = function () {};
      if (s.type === "bool") {
        var b = h("button", { class: "sw", type: "button", role: "switch", "aria-label": s.name });
        b.addEventListener("click", function () { push(s, !b.classList.contains("on")); });
        val.appendChild(b);
        update = function (n) { b.classList.toggle("on", !!n.value); };
      } else if (s.type === "num") {
        var range = h("input", { type: "range", min: s.min, max: s.max, step: s.step || "any" });
        var num = h("input", { class: "field num", type: "number", min: s.min, max: s.max, step: s.step || "any" });
        var unit = h("span", { class: "faint", text: s.unit || "" });
        var send = debounce(function (v) { push(s, v); }, 220);
        range.addEventListener("input", function () { num.value = range.value; send(parseFloat(range.value)); });
        num.addEventListener("change", function () { var v = parseFloat(num.value); if (isFinite(v)) { range.value = v; send(v); } });
        val.appendChild(range); val.appendChild(num); val.appendChild(unit);
        update = function (n) {
          range.min = n.min; range.max = n.max;
          if (document.activeElement !== num && document.activeElement !== range) { range.value = n.value; num.value = n.value; }
        };
      } else if (s.type === "choice" || s.type === "pick") {
        var sel = h("select", { class: "field", "aria-label": s.name });
        (s.options || []).forEach(function (o, i) { sel.appendChild(h("option", { value: String(i), text: o })); });
        sel.addEventListener("change", function () { push(s, parseInt(sel.value, 10)); });
        val.appendChild(sel);
        update = function (n) { if (document.activeElement !== sel) sel.value = String(n.value); };
      } else if (s.type === "text") {
        var t = h("input", { class: "field", type: "text", maxlength: s.max || 200, spellcheck: "false", "aria-label": s.name });
        t.addEventListener("change", function () { push(s, t.value); });
        val.appendChild(t);
        update = function (n) { if (document.activeElement !== t) t.value = n.value; };
      } else if (s.type === "action") {
        var a = h("button", { class: "btn btn-outline sm", type: "button" }, "Run");
        a.addEventListener("click", function () {
          Z.api("POST", "/api/action", { id: m.id, name: s.name }).then(function (r) { toast(r.message, "good"); applyAll(r.settings); }).catch(failed);
        });
        val.appendChild(a);
      } else if (s.type === "info") {
        var box = h("div", { class: "infobox" });
        val.style.justifyContent = "stretch"; val.style.width = "100%";
        val.appendChild(box);
        update = function (n) { box.textContent = (n.lines || []).join("\n"); };
        row.classList.add("full");
        row.replaceChild(h("label", {}, s.name), label);
        row.appendChild(box); row.removeChild(val);
      }
      update(s);
      return { row: row, update: update };
    }

    function applyAll(settings) {
      var first = Object.keys(rows).length === 0;
      if (first) {
        clear(list);
        if (!settings.length) list.appendChild(h("div", { class: "empty", text: "This module has no settings." }));
        settings.forEach(function (s) {
          var r = make(s);
          rows[s.name] = r;
          list.appendChild(r.row);
        });
      }
      settings.forEach(function (s) {
        var r = rows[s.name];
        if (!r) return;
        r.row.hidden = s.visible === false;
        r.update(s);
      });
    }
    function fetchAll() {
      Z.api("GET", "/api/module?id=" + encodeURIComponent(m.id)).then(function (d) { applyAll(d.settings || []); setEnabled(d.enabled); }).catch(function (e) { failed(e); closeSheet(); });
    }
    fetchAll();
    sheetTimer = setInterval(function () { if (!document.hidden) fetchAll(); }, 1500);
  }

  /* ---------------- presets ---------------- */
  function presets() { try { return JSON.parse(Z.store("zeus.presets") || "{}"); } catch (e) { return {}; } }
  function savePresets(p) { Z.store("zeus.presets", JSON.stringify(p)); }

  function presetsTab(pane) {
    var name = h("input", { class: "field", type: "text", maxlength: 40, placeholder: "Preset name (for example Legit, PvP, Mining)", style: "flex:1;min-width:220px", "aria-label": "Preset name" });
    var save = h("button", { class: "btn btn-primary flat", type: "button" }, "Save current modules");
    var list = h("div", {});
    pane.appendChild(h("p", { class: "muted", style: "margin-top:0", text: "A preset remembers which modules are switched on. Applying one switches those on and everything else off. Presets are stored in this browser." }));
    pane.appendChild(h("div", { class: "toolbar" }, name, save));
    pane.appendChild(list);

    function draw() {
      clear(list);
      var p = presets();
      var keys = Object.keys(p);
      if (!keys.length) list.appendChild(h("div", { class: "empty", text: "No presets yet. Switch on the modules you want, then save them here." }));
      keys.forEach(function (k) {
        var apply = h("button", { class: "btn btn-primary flat sm", type: "button" }, "Apply");
        var del = h("button", { class: "btn btn-outline sm", type: "button" }, "Delete");
        apply.addEventListener("click", function () { applyPreset(p[k]); });
        del.addEventListener("click", function () { var q = presets(); delete q[k]; savePresets(q); draw(); });
        list.appendChild(h("div", { class: "rowl" }, h("div", {}, h("b", { text: k }), h("br"), h("small", { text: p[k].length + " modules" })), h("div", { style: "display:flex;gap:8px" }, apply, del)));
      });
    }
    save.addEventListener("click", function () {
      var n = name.value.trim();
      if (!n) { toast("Give the preset a name", "bad"); return; }
      Z.api("GET", "/api/modules").then(function (r) {
        var ids = r.modules.filter(function (m) { return m.enabled && m.toggleable; }).map(function (m) { return m.id; });
        var p = presets(); p[n] = ids; savePresets(p);
        name.value = ""; draw(); toast("Saved “" + n + "” (" + ids.length + " modules)", "good");
      }).catch(failed);
    });
    function applyPreset(ids) {
      Z.api("GET", "/api/modules").then(function (r) {
        var want = {}; ids.forEach(function (id) { want[id] = true; });
        var off = r.modules.filter(function (m) { return m.toggleable && m.enabled && !want[m.id]; }).map(function (m) { return m.id; });
        var on = r.modules.filter(function (m) { return m.toggleable && !m.enabled && want[m.id]; }).map(function (m) { return m.id; });
        return Z.api("POST", "/api/modules/bulk", { ids: off, enabled: false }).then(function () {
          return Z.api("POST", "/api/modules/bulk", { ids: on, enabled: true });
        }).then(function () { toast("Preset applied", "good"); });
      }).catch(failed);
    }
    draw();
  }

  /* ---------------- scheduler ---------------- */
  function schedulerTab(pane) {
    var mod = h("select", { class: "field", "aria-label": "Module" });
    var act = h("select", { class: "field", "aria-label": "Action" }, h("option", { value: "enable", text: "Switch on" }), h("option", { value: "disable", text: "Switch off" }));
    var amount = h("input", { class: "field", type: "number", min: 1, value: 30, style: "width:100px", "aria-label": "Delay" });
    var unit = h("select", { class: "field", "aria-label": "Unit" }, h("option", { value: "1", text: "seconds" }), h("option", { value: "60", text: "minutes" }), h("option", { value: "3600", text: "hours" }));
    var go = h("button", { class: "btn btn-primary flat", type: "button" }, "Schedule");
    var list = h("div", {});
    pane.appendChild(h("p", { class: "muted", style: "margin-top:0", text: "Switch a module on or off after a delay, even if you tab back into the game. Tasks are forgotten when the game closes." }));
    pane.appendChild(h("div", { class: "toolbar" }, mod, act, amount, unit, go));
    pane.appendChild(h("h2", { class: "sec", text: "Waiting tasks" }));
    pane.appendChild(list);

    Z.api("GET", "/api/modules").then(function (r) {
      r.modules.filter(function (m) { return m.toggleable; }).sort(function (a, b) { return a.name.localeCompare(b.name); })
        .forEach(function (m) { mod.appendChild(h("option", { value: m.id, text: m.name })); });
    }).catch(failed);

    go.addEventListener("click", function () {
      var secs = Math.round(parseFloat(amount.value) * parseInt(unit.value, 10));
      if (!isFinite(secs) || secs < 1 || secs > 86400) { toast("Choose a delay between 1 second and 24 hours", "bad"); return; }
      Z.api("POST", "/api/task", { moduleId: mod.value, action: act.value, delaySeconds: secs }).then(function (r) { toast(r.message, "good"); load(); }).catch(failed);
    });
    function fmt(s) { var hh = Math.floor(s / 3600), mm = Math.floor(s % 3600 / 60), ss = s % 60; return (hh ? hh + "h " : "") + (mm || hh ? mm + "m " : "") + ss + "s"; }
    function load() {
      Z.api("GET", "/api/tasks").then(function (r) {
        clear(list);
        if (!r.tasks.length) { list.appendChild(h("div", { class: "empty", text: "Nothing is scheduled." })); return; }
        r.tasks.forEach(function (t) {
          var cancel = h("button", { class: "btn btn-outline sm", type: "button" }, "Cancel");
          cancel.addEventListener("click", function () { Z.api("POST", "/api/task/cancel", { id: t.id }).then(function () { load(); }).catch(failed); });
          list.appendChild(h("div", { class: "rowl" }, h("div", {}, h("b", { text: t.label }), h("br"), h("small", { text: "in " + fmt(t.remainingSeconds) })), cancel));
        });
      }).catch(failed);
    }
    load();
    every(1000, load);
  }

  /* ---------------- market alerts ---------------- */
  function marketTab(pane) {
    var watched = h("input", { class: "field", type: "text", value: "elytra,mace,netherite,diamond", maxlength: 180, style: "width:100%", "aria-label": "Items to watch" });
    var price = h("input", { class: "field", type: "text", value: "0", maxlength: 16, style: "width:100%", "aria-label": "Price ceiling" });
    var disc = h("input", { class: "field", type: "number", value: 20, min: 1, max: 90, style: "width:100%", "aria-label": "Discount" });
    var go = h("button", { class: "btn btn-primary flat", type: "button" }, "Apply and switch on");
    pane.appendChild(h("div", { class: "card", style: "max-width:760px" },
      h("div", { class: "card-title" }, icon("trophy"), "Auction market watch"),
      h("p", { class: "muted", text: "Configures Auction House Tweaks to alert you about listings. It only alerts; you review and buy listings yourself." }),
      h("div", { class: "ctl full" }, h("label", {}, "Items to watch", h("small", { text: "Comma separated item names" })), watched),
      h("div", { class: "ctl full" }, h("label", {}, "Price ceiling", h("small", { text: "Examples: 5000, 1.5k, 2m. Use 0 for no ceiling" })), price),
      h("div", { class: "ctl full" }, h("label", {}, "Alert when cheaper than market by (%)"), disc),
      h("div", { style: "margin-top:12px" }, go)));
    go.addEventListener("click", function () {
      Z.api("POST", "/api/auction", { watchedItems: watched.value, maxPrice: price.value, discount: parseFloat(disc.value) }).then(function (r) { toast(r.message, "good"); }).catch(failed);
    });
  }

  /* ---------------- activity ---------------- */
  function activityTab(pane) {
    var feed = h("div", { class: "feed" });
    pane.appendChild(h("p", { class: "muted", style: "margin-top:0", text: "Module switches and notifications from the game, newest first." }));
    pane.appendChild(feed);
    var last = 0, items = [];
    function fmtTime(ms) { var d = new Date(ms); return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" }); }
    function load() {
      Z.api("GET", "/api/events?after=" + last).then(function (r) {
        last = r.last || last;
        if (r.events.length) {
          items = r.events.concat(items).sort(function (a, b) { return b.id - a.id; }).slice(0, 200);
          clear(feed);
          items.forEach(function (e) {
            feed.appendChild(h("div", {}, h("time", { text: fmtTime(e.at) }), h("span", { class: e.good ? "good" : "bad", text: e.text })));
          });
        } else if (!items.length && !feed.firstChild) feed.appendChild(h("div", { class: "empty", text: "No activity yet. Switch a module on and it will show here." }));
      }).catch(failed);
    }
    load();
    every(1500, load);
  }
})();
