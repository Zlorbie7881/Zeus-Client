/* Zeus Client website 9.5: "Build a config". Answer yes or no questions, or pick a playstyle (a set of ready-made answers), and get a pack of
   modules with the reason for each. Cheats-off, single-player and low-end answers filter the pack. Output: zeus.json, /zeus on commands or a saved config. */
(function () {
  "use strict";
  var Z = window.Z, h = Z.h, icon = Z.icon, clear = Z.clear;

  // which answers switch a bucket of modules on ("basics" is always on)
  var BUCKET_Q = {
    defence: ["pvp"], melee: ["pvp"], crystal: ["crystal"], ranged: ["ranged"], pvpinfo: ["pvp"], bases: ["bases"], underground: ["underground"], donut: ["donut"],
    explore: ["explore"], mine: ["mine"], build: ["build"], stream: ["stream"], hud: ["hud"], auto: ["auto"], practice: ["sp"], fun: ["fun"], basics: [],
  };

  function copyText(text, ok) {
    function done() { Z.toast(ok || "Copied"); }
    if (navigator.clipboard && window.isSecureContext) { navigator.clipboard.writeText(text).then(done, fallback); return; }
    fallback();
    function fallback() {
      var t = h("textarea", { style: "position:fixed;left:-999px;top:0", "aria-hidden": "true" });
      t.value = text; document.body.appendChild(t); t.select();
      try { document.execCommand("copy"); done(); } catch (e) { Z.toast("Copy failed. Select the text yourself.", "bad"); }
      t.remove();
    }
  }
  function saveFile(name, text, type) {
    var a = h("a", { href: URL.createObjectURL(new Blob([text], { type: type })), download: name });
    document.body.appendChild(a); a.click();
    setTimeout(function () { URL.revokeObjectURL(a.href); a.remove(); }, 600);
  }

  Z.pages.config = function () {
    var F = Z.features || { categories: [], modules: [] };
    var byId = {};
    F.modules.forEach(function (m) { byId[m.id] = m; });
    var W = null;
    var S = { mode: "", ans: {}, style: "", ov: {}, custom: [], stack: [], q: "", name: "My Zeus config" };
    var host = h("div", { class: "cw" });
    host.appendChild(h("div", { class: "empty", text: "Loading…" }));
    var page = h("div", { class: "wrap page" },
      Z.pageHead("Build a config", "Build a config", "Answer a few yes or no questions, or pick a playstyle. You get a set of modules with a reason for each, and you can change any of it."),
      host);

    /* ----- model */
    function defaults() { var a = {}; W.q.forEach(function (q) { a[q.id] = !!q.default; }); return a; }
    function effective(ans) {
      var eff = {};
      W.q.forEach(function (q) { eff[q.id] = (q.if || []).every(function (r) { return eff[r]; }) ? !!ans[q.id] : false; });
      return eff;
    }
    function visible(ans) { var eff = effective(ans); return W.q.filter(function (q) { return (q.if || []).every(function (r) { return eff[r]; }); }); }

    function compute() {
      var eff = effective(S.ans), score = {}, info = {}, order = Object.keys(W.buckets);
      order.forEach(function (k) {
        var on = k === "basics" || (BUCKET_Q[k] || []).some(function (q) { return eff[q]; });
        if (!on) return;
        var b = W.buckets[k];
        b.mods.forEach(function (id, i) {
          if (!byId[id]) return;
          var s = (i < b.core ? 3 : 1) + (b.mods.length - i) * 0.01;
          score[id] = (score[id] || 0) + s;
          if (!info[id] || s > info[id].s) info[id] = { s: s, bucket: k };
        });
      });
      var left = { cheat: 0, sp: 0, heavy: 0 }, items = [];
      Object.keys(score).forEach(function (id) {
        if (!eff.cheats && W.safe.indexOf(id) < 0) { left.cheat++; return; }
        if (!eff.sp && W.sponly.indexOf(id) >= 0) { left.sp++; return; }
        if (eff.lowend && W.heavy.indexOf(id) >= 0) { left.heavy++; return; }
        items.push({ id: id, score: score[id], bucket: info[id].bucket, extra: score[id] < 2 });
      });
      items.sort(function (a, b) { return (a.extra - b.extra) || (b.score - a.score); });
      return { eff: eff, items: items, left: left, order: order };
    }
    function isOn(it) { return S.ov[it.id] !== undefined ? S.ov[it.id] : !it.extra; }
    function selected(res) {
      var out = [];
      res.items.forEach(function (it) { if (isOn(it)) out.push(it.id); });
      S.custom.forEach(function (id) { if (out.indexOf(id) < 0 && S.ov[id] !== false) out.push(id); });
      return out;
    }
    function enabledMap(ids) {
      var set = {}, mods = {};
      ids.forEach(function (id) { set[id] = 1; });
      F.modules.forEach(function (m) { var on = !!set[m.id], def = !!m.defaultOn; if (on !== def) mods[m.id] = { enabled: on }; });
      return mods;
    }
    function buildJson(ids) { return JSON.stringify({ schema: 2, clientVersion: Z.site.version || "", modules: enabledMap(ids) }, null, 2) + "\n"; }

    /* ----- screens */
    function modeScreen() {
      var a = h("button", { type: "button", class: "cw-mode" }, h("span", { class: "pi" }, icon("help")), h("b", { text: "Answer a few questions" }), h("span", { text: "Yes or no, one at a time. About a minute. The modules are chosen from your answers." }), h("small", { text: "Best if you are not sure what you need" }));
      a.addEventListener("click", function () { S.mode = "wizard"; S.ans = defaults(); S.stack = []; S.style = ""; S.ov = {}; S.custom = []; wizard(null); });
      var b = h("button", { type: "button", class: "cw-mode" }, h("span", { class: "pi" }, icon("bolt")), h("b", { text: "Pick a playstyle" }), h("span", { text: "Choose the way you play and get a ready-made pack. You can adjust the answers after." }), h("small", { text: W.styles.length + " playstyles" }));
      b.addEventListener("click", function () { S.mode = "style"; styles(); });
      return h("div", { class: "cw-modes" }, a, b);
    }
    function start() {
      S.mode = ""; clear(host);
      host.appendChild(h("h2", { class: "sec", text: "How do you want to start?" }));
      var m = modeScreen();
      host.appendChild(m);
      Z.stagger(m.children, 70);
    }
    function backBar(label, fn) {
      var b = h("button", { type: "button", class: "btn btn-outline sm" }, icon("arrow", "small flip"), label);
      b.addEventListener("click", fn);
      return b;
    }

    function wizard(prev) {
      // prev = question id we came from (to skip invisible ones), null = first
      var vis = visible(S.ans);
      var cur;
      if (prev === null) cur = vis[0];
      else {
        var idx = W.q.map(function (q) { return q.id; }).indexOf(prev);
        cur = null;
        for (var i = idx + 1; i < W.q.length; i++) { if (vis.indexOf(W.q[i]) >= 0) { cur = W.q[i]; break; } }
      }
      if (!cur) { results(); return; }
      clear(host);
      var n = vis.indexOf(cur) + 1;
      var card = h("div", { class: "card cw-q swap-in" },
        h("div", { class: "cw-prog" }, h("i", { style: "width:" + Math.round(((n - 1) / vis.length) * 100) + "%" })),
        h("small", { class: "cw-step", text: "Question " + n + " of " + vis.length }),
        h("h2", { text: cur.text }),
        h("p", { class: "muted", text: cur.hint }));
      function answer(v) { S.ans[cur.id] = v; S.stack.push(cur.id); wizard(cur.id); }
      var yes = h("button", { type: "button", class: "btn btn-primary" + (cur.default ? "" : " flat") }, icon("check"), "Yes");
      var no = h("button", { type: "button", class: "btn btn-outline" + (cur.default ? "" : " pick") }, icon("close", "small"), "No");
      yes.addEventListener("click", function () { answer(true); });
      no.addEventListener("click", function () { answer(false); });
      card.appendChild(h("div", { class: "cw-ans" }, yes, no));
      var nav = h("div", { class: "cw-nav" },
        S.stack.length ? backBar("Back", function () { S.stack.pop(); wizard(S.stack.length ? S.stack[S.stack.length - 1] : null); }) : backBar("Change how I start", start),
        h("button", { type: "button", class: "btn btn-outline sm", onclick: function () { results(); } }, "Skip to the result"));
      card.appendChild(nav);
      host.appendChild(card);
      yes.focus({ preventScroll: true });
    }

    function styles() {
      clear(host);
      host.appendChild(h("div", { class: "cw-nav", style: "margin-bottom:14px" }, backBar("Change how I start", start)));
      host.appendChild(h("h2", { class: "sec", text: "Pick a playstyle" }));
      var grid = h("div", { class: "preset-grid" });
      W.styles.forEach(function (p, i) {
        var b = h("button", { type: "button", class: "preset", "aria-pressed": "false" }, h("span", { class: "pi" }, icon(document.getElementById("i-" + p.icon) ? p.icon : "bolt")), h("b", { text: p.name }), h("span", { class: "pb", text: p.blurb }));
        b.addEventListener("click", function () { pickStyle(p); });
        b.style.setProperty("--d", i * 50 + "ms");
        grid.appendChild(b);
      });
      host.appendChild(grid);
      Z.stagger(grid.children, 45);
      Z.reveal(grid);
    }
    function pickStyle(p) {
      S.mode = "style"; S.style = p.id; S.ov = {}; S.custom = [];
      S.ans = defaults();
      Object.keys(p.answers).forEach(function (k) { S.ans[k] = !!p.answers[k]; });
      S.name = p.name + " pack";
      results();
    }

    /* ----- results */
    function results() {
      clear(host);
      var res = compute();
      var top = h("div", { class: "cw-nav", style: "margin-bottom:14px" },
        backBar(S.mode === "style" ? "Other playstyles" : "Change how I start", S.mode === "style" ? styles : start),
        S.style ? h("span", { class: "cw-chip" }, icon("bolt", "small"), (W.styles.filter(function (p) { return p.id === S.style; })[0] || {}).name || "") : null);
      var layout = h("div", { class: "cw-res" });
      var left = h("aside", { class: "card cw-left" });
      var right = h("div", { class: "cw-right" });
      layout.appendChild(left); layout.appendChild(right);
      host.appendChild(top);
      host.appendChild(layout);
      paint();

      function paint() {
        res = compute();
        paintAnswers();
        paintPack();
      }
      function paintAnswers() {
        clear(left);
        left.appendChild(h("h3", { text: "Your answers" }));
        left.appendChild(h("p", { class: "muted small", text: "Change one and the pack updates." }));
        visible(S.ans).forEach(function (q) {
          var cb = h("input", { type: "checkbox" });
          cb.checked = !!S.ans[q.id];
          cb.addEventListener("change", function () { S.ans[q.id] = cb.checked; paint(); });
          left.appendChild(h("label", { class: "cw-sw", title: q.hint }, cb, h("i", { class: "knob" }), h("span", { text: q.short || q.text })));
        });
      }
      function paintPack() {
        clear(right);
        var ids = selected(res);
        var risky = ids.filter(function (id) { return W.risky.indexOf(id) >= 0; });
        var heavy = ids.filter(function (id) { return W.heavy.indexOf(id) >= 0; });
        var level = !res.eff.cheats || risky.length === 0 ? 0 : risky.length <= 3 ? 1 : risky.length <= 8 ? 2 : 3;
        var labels = ["Low", "Medium", "High", "Very high"];
        var meter = h("div", { class: "cw-risk r" + level, role: "img", "aria-label": "Ban risk: " + labels[level] },
          h("div", { class: "bar" }, h("i", { style: "width:" + (level * 25 + (level ? 0 : 6)) + "%" })),
          h("span", {}, h("b", { text: "Ban risk: " + labels[level] }), h("small", { text: risky.length ? risky.length + " module" + (risky.length === 1 ? "" : "s") + " that servers often detect" : "Nothing here is a usual ban reason" })));
        var sum = h("div", { class: "card cw-sum" },
          h("div", {}, h("b", { class: "big", text: String(ids.length) }), h("span", { text: ids.length === 1 ? " module will be switched on" : " modules will be switched on" })),
          meter);
        right.appendChild(sum);

        var notes = [];
        if (!res.eff.cheats) notes.push(["shield", "Cheats are off in your answers, so only low-risk helpers are offered" + (res.left.cheat ? " (" + res.left.cheat + " cheat modules left out)." : ".")]);
        if (risky.length) notes.push(["info", "Many servers ban modules like " + risky.slice(0, 3).map(function (id) { return byId[id].name; }).join(", ") + ". Read the server rules before you use them."]);
        if (res.eff.lowend && res.left.heavy) notes.push(["gear", res.left.heavy + " heavy scanner and ESP modules were left out to keep your frame rate up."]);
        else if (heavy.length > 4) notes.push(["gear", heavy.length + " of these modules draw a lot (ESP and scanners). If your frame rate drops, answer yes to \"Keep it light\"."]);
        if (res.eff.sp) notes.push(["play", "Single-player modules (" + ids.filter(function (id) { return W.sponly.indexOf(id) >= 0; }).map(function (id) { return byId[id].name; }).join(", ") + ") switch themselves off on servers."]);
        if (notes.length) {
          var nb = h("div", { class: "cw-notes" });
          notes.forEach(function (n) { nb.appendChild(h("div", { class: "note-box" }, icon(n[0]), h("div", {}, h("p", { text: n[1] })))); });
          right.appendChild(nb);
        }

        // groups
        var groups = {};
        res.items.forEach(function (it) { (groups[it.bucket] = groups[it.bucket] || []).push(it); });
        res.order.forEach(function (k) {
          if (!groups[k]) return;
          var b = W.buckets[k];
          var on = groups[k].filter(isOn).length;
          var det = h("details", { class: "cfg-cat cw-grp", open: "" },
            h("summary", {}, h("span", { class: "dot" }), b.name, h("span", { class: "cnt", text: on + " / " + groups[k].length }), h("em", { text: b.why })),
            h("div", { class: "cw-mods" }, groups[k].map(function (it) { return modRow(it, b); })));
          right.appendChild(det);
        });
        if (!res.items.length) right.appendChild(h("div", { class: "empty", text: "Nothing is selected yet. Change an answer on the left." }));

        // anything else
        var more = h("div", { class: "card cw-add" }, h("h3", { text: "Add another module" }));
        var q = h("input", { type: "search", placeholder: "Search every module…", "aria-label": "Search modules", autocomplete: "off", spellcheck: "false", value: S.q });
        var hits = h("div", { class: "cw-hits" });
        function paintHits() {
          clear(hits);
          var s = q.value.trim().toLowerCase();
          if (s.length < 2) return;
          F.modules.filter(function (m) { return m.name.toLowerCase().indexOf(s) >= 0 || (m.aliases || []).some(function (a) { return a.toLowerCase().indexOf(s) >= 0; }); }).slice(0, 8).forEach(function (m) {
            var already = ids.indexOf(m.id) >= 0;
            var b = h("button", { type: "button", class: "cw-hit" + (already ? " on" : ""), disabled: already ? "" : null }, icon(already ? "check" : "sliders", "small"), m.name);
            b.addEventListener("click", function () {
              S.ov[m.id] = true;
              if (!res.items.some(function (it) { return it.id === m.id; }) && S.custom.indexOf(m.id) < 0) S.custom.push(m.id);
              S.q = q.value; paintPack();
            });
            hits.appendChild(b);
          });
        }
        q.addEventListener("input", Z.debounce(function () { S.q = q.value; paintHits(); }, 100));
        more.appendChild(h("div", { class: "fsearch sm" }, icon("search"), q));
        more.appendChild(hits);
        if (S.custom.length) {
          more.appendChild(h("div", { class: "cw-mods", style: "margin-top:12px" }, S.custom.map(function (id) {
            var on = S.ov[id] !== false;
            var b = h("button", { type: "button", class: "cw-mod" + (on ? " on" : ""), "aria-pressed": on ? "true" : "false" }, h("span", { class: "tick" }, icon("check", "small")), h("span", { class: "cw-n", text: byId[id].name }), h("span", { class: "cw-d", text: "Added by you" }));
            b.addEventListener("click", function () { S.ov[id] = !on; paintPack(); });
            return b;
          })));
        }
        right.appendChild(more);
        paintHits();

        // output
        var nameIn = h("input", { type: "text", maxlength: "60", "aria-label": "Config name", value: S.name });
        nameIn.addEventListener("input", function () { S.name = nameIn.value; });
        function need() { if (!ids.length) { Z.toast("Pick at least one module first", "bad"); return false; } return true; }
        var dl = h("button", { type: "button", class: "btn btn-primary" }, icon("download"), h("span", { class: "btn-two" }, h("b", { text: "Download zeus.json" }), h("small", { text: "Switches these modules on at launch" })));
        dl.addEventListener("click", function () { if (need()) saveFile("zeus.json", buildJson(ids), "application/json"); });
        var cmd = h("button", { type: "button", class: "btn btn-outline" }, icon("copy"), h("span", { class: "btn-two" }, h("b", { text: "Copy commands" }), h("small", { text: "One /zeus on line per module" })));
        cmd.addEventListener("click", function () { if (need()) copyText(ids.map(function (id) { return "/zeus on " + id; }).join("\n"), "Commands copied"); });
        var sv = h("button", { type: "button", class: "btn btn-outline" }, icon("star"), h("span", { class: "btn-two" }, h("b", { text: "Save to my profile" }), h("small", { text: "Kept in this browser" })));
        sv.addEventListener("click", function () {
          if (!need()) return;
          Z.configs.add({ name: nameIn.value.trim().slice(0, 60) || "My Zeus config", preset: S.style || "wizard", created: Date.now(), mods: ids });
          Z.toast("Saved. Find it on your profile page.");
        });
        right.appendChild(h("div", { class: "card cfg-out" },
          h("h2", { class: "sec", text: "Take it with you" }),
          h("label", { class: "adm-f" }, h("span", { text: "Name" }), nameIn),
          h("div", { class: "cfg-btns" }, dl, cmd, sv),
          h("div", { class: "note-box" }, icon("info"), h("div", {},
            h("b", { text: "How to use the file" }),
            h("ol", { class: "mini-steps" },
              h("li", { text: "Close Minecraft and open .minecraft/config." }),
              h("li", { text: "Back up your current zeus.json, then put the downloaded one in its place. It only holds which modules are on, so every other setting goes back to its default." }),
              h("li", { text: "Start the game. The modules switch on once you are in a world (Remember enabled modules, in the Interface settings, must stay on, which it is by default)." })),
            h("p", { class: "faint", text: "Rather not replace the file? Copy the commands and paste them into chat one at a time." })))));
      }
      function modRow(it, b) {
        var m = byId[it.id];
        var on = isOn(it);
        var flags = [];
        if (W.risky.indexOf(it.id) >= 0) flags.push(["bad", "Often banned"]);
        if (W.sponly.indexOf(it.id) >= 0) flags.push(["", "Single-player"]);
        if (W.heavy.indexOf(it.id) >= 0) flags.push(["", "Heavy"]);
        if (it.extra) flags.push(["opt", "Optional"]);
        var btn = h("button", { type: "button", class: "cw-mod" + (on ? " on" : "") + (it.extra ? " opt" : ""), "aria-pressed": on ? "true" : "false", title: m.description || "" },
          h("span", { class: "tick" }, icon("check", "small")),
          h("span", { class: "cw-n", text: m.name }),
          h("span", { class: "cw-d", text: (it.extra ? "Also useful: " : "Why: ") + b.why }),
          flags.length ? h("span", { class: "cw-f" }, flags.map(function (f) { return h("em", { class: f[0], text: f[1] }); })) : null);
        btn.addEventListener("click", function () {
          S.ov[it.id] = !isOn(it);
          var now = isOn(it);
          btn.classList.toggle("on", now); btn.setAttribute("aria-pressed", now ? "true" : "false");
          paintPack();
        });
        return btn;
      }
    }

    function init() {
      clear(host);
      var want = (/[?&]p=([a-z0-9_-]+)/.exec(location.search) || [])[1];
      var p = want && W.styles.filter(function (x) { return x.id === want; })[0];
      if (p) { pickStyle(p); return; }
      start();
    }
    Z.getJson("/configwiz.json").then(function (w) { W = w; init(); }, function () { clear(host); host.appendChild(h("div", { class: "empty", text: "The config builder could not load its data." })); });
    return page;
  };
})();
