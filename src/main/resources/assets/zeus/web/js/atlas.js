/* Zeus Client website: the Atlas page, a map of everything the client remembers about a server.
   Read-only, served by the local control server, drawn on a canvas so a base field stays sharp when zoomed in. */
(function () {
  "use strict";
  var Z = window.Z;
  if (!Z) return;
  var h = Z.h, icon = Z.icon;

  var LAYERS = [
    ["bases", "Bases", "#ff5c6c"],
    ["chests", "Chests", "#4ea4ff"],
    ["finds", "Scanner finds", "#35e0d0"],
    ["people", "People", "#ffc857"],
  ];

  function ago(ms) {
    if (!ms) return "never";
    var s = Math.max(0, (Date.now() - ms) / 1000);
    if (s < 60) return "now";
    if (s < 3600) return Math.round(s / 60) + "m";
    if (s < 172800) return Math.round(s / 3600) + "h";
    return Math.round(s / 86400) + "d";
  }

  function nice(step) {
    if (step >= 1000) return (step / 1000) + "k";
    return String(step);
  }

  Z.pages.atlas = function () {
    var st = {
      data: null,
      cx: 0,
      cz: 0,
      scale: 0.09,
      layers: { bases: true, chests: true, finds: true, people: true },
      hover: null,
      drag: null,
      fitted: false,
      timer: 0,
      error: "",
    };

    var canvas = h("canvas", { class: "atlas-canvas", width: 960, height: 520 });
    var tip = h("div", { class: "atlas-tip", hidden: true });
    var status = h("div", { class: "atlas-status", text: "Waiting for the game…" });
    var toggles = h("div", { class: "atlas-layers" });
    var notice = h("div", { class: "atlas-notice", hidden: true });

    LAYERS.forEach(function (l) {
      var box = h("input", { type: "checkbox", checked: true });
      box.addEventListener("change", function () {
        st.layers[l[0]] = box.checked;
        draw();
      });
      toggles.appendChild(h("label", { class: "atlas-chip" }, box, h("i", { style: "background:" + l[2] }), h("span", { text: l[1] })));
    });

    function sizeCanvas() {
      var box = canvas.parentElement;
      if (!box) return;
      var dpr = Math.min(2, window.devicePixelRatio || 1);
      var w = Math.max(320, box.clientWidth), hgt = Math.max(280, Math.round(w * 0.52));
      var old = canvas.width;
      canvas.width = Math.round(w * dpr);
      canvas.height = Math.round(hgt * dpr);
      canvas.style.width = w + "px";
      canvas.style.height = hgt + "px";
      // keep showing the same piece of the world when the window changes size
      if (old > 0) st.scale *= canvas.width / old;
      draw();
    }

    function fit() {
      var d = st.data;
      if (!d) return;
      var xs = [], zs = [];
      (d.bases || []).forEach(function (b) { xs.push(b.x); zs.push(b.z); });
      (d.chests || []).forEach(function (c) { xs.push(c.x); zs.push(c.z); });
      (d.finds || []).forEach(function (f) { xs.push(f.x); zs.push(f.z); });
      (d.people || []).forEach(function (p) { xs.push(p.x); zs.push(p.z); });
      if (d.inWorld) { xs.push(d.x); zs.push(d.z); }
      if (!xs.length) {
        st.cx = d.inWorld ? d.x : 0;
        st.cz = d.inWorld ? d.z : 0;
        st.scale = 0.09;
        return draw();
      }
      var minX = Math.min.apply(null, xs), maxX = Math.max.apply(null, xs);
      var minZ = Math.min.apply(null, zs), maxZ = Math.max.apply(null, zs);
      st.cx = (minX + maxX) / 2;
      st.cz = (minZ + maxZ) / 2;
      // fit both ways: a wide window must not leave the field floating in the middle
      var across = Math.max(512, maxX - minX);
      var down = Math.max(512, maxZ - minZ);
      var aspect = canvas.width / Math.max(1, canvas.height);
      var span = Math.max(across, down * aspect) * 1.1;
      st.scale = canvas.width / span;
      draw();
    }

    function toScreen(x, z) {
      var dpr = canvas.width / parseFloat(canvas.style.width || canvas.width);
      return [(x - st.cx) * st.scale + canvas.width / 2, (z - st.cz) * st.scale + canvas.height / 2, dpr];
    }

    function draw() {
      var g = canvas.getContext("2d");
      if (!g) return;
      var W = canvas.width, H = canvas.height;
      var dpr = W / parseFloat(canvas.style.width || W);
      g.clearRect(0, 0, W, H);
      g.fillStyle = "#080d16";
      g.fillRect(0, 0, W, H);
      var d = st.data;
      // the grid only ever shows steps a person can read
      var rawStep = 40 / st.scale;
      var pow = Math.pow(10, Math.floor(Math.log(Math.max(1, rawStep)) / Math.LN10));
      var step = Math.max(16, pow * (rawStep / pow > 5 ? 10 : rawStep / pow > 2 ? 5 : 2));
      g.strokeStyle = "rgba(120,160,255,0.10)";
      g.lineWidth = 1;
      g.fillStyle = "rgba(150,175,215,0.5)";
      g.font = (11 * dpr) + "px Inter, system-ui, sans-serif";
      var x0 = Math.floor((st.cx - W / 2 / st.scale) / step) * step;
      var x1 = st.cx + W / 2 / st.scale;
      var z0 = Math.floor((st.cz - H / 2 / st.scale) / step) * step;
      var z1 = st.cz + H / 2 / st.scale;
      for (var gx = x0; gx <= x1; gx += step) {
        var sx = (gx - st.cx) * st.scale + W / 2;
        g.beginPath(); g.moveTo(sx, 0); g.lineTo(sx, H); g.stroke();
        g.fillText(nice(gx), sx + 3 * dpr, 12 * dpr);
      }
      for (var gz = z0; gz <= z1; gz += step) {
        var sz = (gz - st.cz) * st.scale + H / 2;
        g.beginPath(); g.moveTo(0, sz); g.lineTo(W, sz); g.stroke();
        g.fillText(nice(gz), 4 * dpr, sz - 3 * dpr);
      }
      if (!d) return;

      if (st.layers.finds) {
        (d.finds || []).forEach(function (f) {
          var p = toScreen(f.x, f.z), r = Math.max(6 * dpr, (f.chunks || 1) * 6 * st.scale);
          g.fillStyle = "rgba(53,224,208,0.12)";
          g.strokeStyle = "rgba(53,224,208,0.55)";
          g.lineWidth = 1.2 * dpr;
          g.beginPath(); g.arc(p[0], p[1], r, 0, Math.PI * 2); g.fill(); g.stroke();
        });
      }
      if (st.layers.chests) {
        g.fillStyle = "rgba(78,164,255,0.85)";
        (d.chests || []).forEach(function (c) {
          var p = toScreen(c.x, c.z);
          g.beginPath(); g.arc(p[0], p[1], 2.4 * dpr, 0, Math.PI * 2); g.fill();
        });
      }
      if (st.layers.bases) {
        (d.bases || []).forEach(function (b) {
          var p = toScreen(b.x, b.z);
          var col = /raid/i.test(b.status) ? "#ff5c6c" : /intact/i.test(b.status) ? "#5ee6a0" : "#ffc857";
          g.save();
          g.translate(p[0], p[1]);
          g.rotate(Math.PI / 4);
          g.fillStyle = col;
          g.fillRect(-4 * dpr, -4 * dpr, 8 * dpr, 8 * dpr);
          g.restore();
          g.strokeStyle = "rgba(0,0,0,0.55)";
          g.lineWidth = 1 * dpr;
          g.strokeRect(p[0] - 5 * dpr, p[1] - 5 * dpr, 10 * dpr, 10 * dpr);
        });
      }
      if (st.layers.people) {
        g.font = "600 " + (11 * dpr) + "px Inter, system-ui, sans-serif";
        (d.people || []).forEach(function (p) {
          var q = toScreen(p.x, p.z);
          g.fillStyle = "#ffc857";
          g.beginPath();
          g.moveTo(q[0], q[1] - 5 * dpr);
          g.lineTo(q[0] + 4 * dpr, q[1] + 4 * dpr);
          g.lineTo(q[0] - 4 * dpr, q[1] + 4 * dpr);
          g.closePath();
          g.fill();
          g.fillStyle = "rgba(255,200,87,0.85)";
          g.fillText(p.name, q[0] + 7 * dpr, q[1] + 4 * dpr);
        });
      }
      if (d.inWorld) {
        var me = toScreen(d.x, d.z);
        g.strokeStyle = "#9fc4ff";
        g.lineWidth = 1.6 * dpr;
        g.beginPath(); g.arc(me[0], me[1], 7 * dpr, 0, Math.PI * 2); g.stroke();
        g.beginPath();
        g.moveTo(me[0] - 12 * dpr, me[1]); g.lineTo(me[0] - 7 * dpr, me[1]);
        g.moveTo(me[0] + 7 * dpr, me[1]); g.lineTo(me[0] + 12 * dpr, me[1]);
        g.moveTo(me[0], me[1] - 12 * dpr); g.lineTo(me[0], me[1] - 7 * dpr);
        g.moveTo(me[0], me[1] + 7 * dpr); g.lineTo(me[0], me[1] + 12 * dpr);
        g.stroke();
        g.fillStyle = "#9fc4ff";
        g.font = "600 " + (11 * dpr) + "px Inter, system-ui, sans-serif";
        g.fillText("you", me[0] + 10 * dpr, me[1] - 8 * dpr);
      }
      if (st.hover) {
        var hp = toScreen(st.hover.x, st.hover.z);
        g.strokeStyle = "rgba(255,255,255,0.75)";
        g.lineWidth = 1.4 * dpr;
        g.beginPath(); g.arc(hp[0], hp[1], 10 * dpr, 0, Math.PI * 2); g.stroke();
      }
    }

    function markers() {
      var d = st.data, out = [];
      if (!d) return out;
      (d.bases || []).forEach(function (b) {
        out.push({ x: b.x, z: b.z, kind: "Base", title: b.type, lines: [b.status, "confidence " + b.confidence, "seen " + ago(b.last)] });
      });
      (d.chests || []).forEach(function (c) {
        out.push({ x: c.x, z: c.z, kind: "Chest", title: c.kinds + " kinds of item", lines: ["y " + Math.round(c.y), "opened " + ago(c.at)] });
      });
      (d.finds || []).forEach(function (f) {
        out.push({ x: f.x, z: f.z, kind: "Find", title: "score " + f.score, lines: [f.chunks + " chunks"] });
      });
      (d.people || []).forEach(function (p) {
        out.push({ x: p.x, z: p.z, kind: "Player", title: p.name, lines: ["seen " + p.seen + "\u00D7", "last " + ago(p.last)] });
      });
      return out;
    }

    function nearest(mx, my) {
      var best = null, bd = 12 * (canvas.width / parseFloat(canvas.style.width || canvas.width));
      markers().forEach(function (m) {
        if (!st.layers[(m.kind === "Base" ? "bases" : m.kind === "Chest" ? "chests" : m.kind === "Find" ? "finds" : "people")]) return;
        var p = toScreen(m.x, m.z);
        var d = Math.hypot(p[0] - mx, p[1] - my);
        if (d < bd) { bd = d; best = m; }
      });
      return best;
    }

    function refresh() {
      if (!Z.token) {
        setStatus("Paste the access token from the game (Web Dashboard, or the chat line) in the Control center page first.");
        return;
      }
      Z.api("GET", "/api/atlas").then(function (d) {
        st.data = d;
        st.error = "";
        notice.hidden = true;
        setStatus(summary(d));
        if (!st.fitted && d) { st.fitted = true; fit(); } else draw();
      }).catch(function (e) {
        if (e && e.status === 401) {
          setStatus("This browser is not signed in to the game yet.");
          showToken();
        } else {
          setStatus("The game did not answer: " + (e && e.message ? e.message : "unknown"));
        }
      });
    }

    function summary(d) {
      var n = function (a) { return (a || []).length; };
      return (d.server || "singleplayer") + "  ·  " + (d.dimension || "?") + "  ·  " +
        n(d.bases) + " bases  ·  " + n(d.chests) + " chests  ·  " + n(d.finds) + " finds  ·  " + n(d.people) + " people";
    }

    function setStatus(text) {
      status.textContent = text;
    }

    function showToken() {
      if (notice.dataset.on) return;
      notice.dataset.on = "1";
      var field = h("input", { class: "atlas-input", type: "password", placeholder: "access token", spellcheck: "false" });
      var save = h("button", { class: "btn btn-outline sm", type: "button", text: "Use this token" });
      save.addEventListener("click", function () {
        var v = field.value.trim();
        if (v.length < 20) { field.focus(); return; }
        Z.token = v;
        Z.store("zeus.token", v);
        notice.hidden = true;
        notice.dataset.on = "";
        refresh();
      });
      notice.hidden = false;
      notice.replaceChildren(h("b", { text: "The map needs the game's token." }), field, save);
    }

    /* ---------------- interaction ---------------- */
    var down = null;
    canvas.addEventListener("mousedown", function (e) {
      down = { x: e.clientX, y: e.clientY, cx: st.cx, cz: st.cz };
      canvas.classList.add("grabbing");
    });
    var onMove = function (e) {
      if (down) {
        var dpr = canvas.width / parseFloat(canvas.style.width || canvas.width);
        st.cx = down.cx - (e.clientX - down.x) * dpr / st.scale;
        st.cz = down.cz - (e.clientY - down.y) * dpr / st.scale;
        draw();
        return;
      }
      var r = canvas.getBoundingClientRect();
      if (e.clientX < r.left || e.clientX > r.right || e.clientY < r.top || e.clientY > r.bottom) {
        if (st.hover) { st.hover = null; tip.hidden = true; draw(); }
        return;
      }
      var dpr2 = canvas.width / r.width;
      var m = nearest((e.clientX - r.left) * dpr2, (e.clientY - r.top) * dpr2);
      st.hover = m;
      draw();
      if (m) {
        tip.hidden = false;
        tip.replaceChildren(
          h("b", { text: m.title }),
          h("span", { class: "atlas-tip-kind", text: m.kind }),
          h("em", { text: m.lines.join("  ·  ") }),
          h("small", { text: Math.round(m.x) + " / " + Math.round(m.z) })
        );
        tip.style.left = Math.min(r.width - 190, e.clientX - r.left + 14) + "px";
        tip.style.top = Math.max(6, e.clientY - r.top - 10) + "px";
      } else tip.hidden = true;
    };
    window.addEventListener("mousemove", onMove);
    var onUp = function () {
      down = null;
      canvas.classList.remove("grabbing");
    };
    window.addEventListener("mouseup", onUp);
    canvas.addEventListener("wheel", function (e) {
      e.preventDefault();
      var r = canvas.getBoundingClientRect();
      var dpr = canvas.width / r.width;
      var mx = (e.clientX - r.left) * dpr, my = (e.clientY - r.top) * dpr;
      var wx = st.cx + (mx - canvas.width / 2) / st.scale;
      var wz = st.cz + (my - canvas.height / 2) / st.scale;
      var factor = e.deltaY < 0 ? 1.15 : 1 / 1.15;
      st.scale = Math.max(0.0006, Math.min(4, st.scale * factor));
      st.cx = wx - (mx - canvas.width / 2) / st.scale;
      st.cz = wz - (my - canvas.height / 2) / st.scale;
      draw();
    }, { passive: false });
    canvas.addEventListener("dblclick", function () {
      st.scale = Math.min(4, st.scale * 2);
      draw();
    });

    var fitBtn = h("button", { class: "btn btn-outline sm", type: "button" }, icon("grid", "small"), "Fit");
    fitBtn.addEventListener("click", fit);
    var meBtn = h("button", { class: "btn btn-outline sm", type: "button" }, icon("compass", "small"), "Go to me");
    meBtn.addEventListener("click", function () {
      if (!st.data || !st.data.inWorld) return;
      st.cx = st.data.x;
      st.cz = st.data.z;
      draw();
    });
    var againBtn = h("button", { class: "btn btn-outline sm", type: "button" }, icon("refresh", "small"), "Refresh");
    againBtn.addEventListener("click", refresh);

    var page = h("div", { class: "wrap page atlas" },
      Z.pageHead("Atlas", "Everything you know, on one map", "A test build of the map: the bases, chests, scanner finds and people Zeus remembers for this server. Read from your own game, drawn in your own browser, sent nowhere."),
      h("div", { class: "atlas-note" }, icon("info", "small"), h("span", { text: "Test build. It only knows what you have already seen in game: nothing is looked up, and nothing leaves this computer." })),
      h("div", { class: "card atlas-card" },
        h("div", { class: "atlas-bar" },
          h("div", { class: "atlas-btns" }, fitBtn, meBtn, againBtn),
          toggles,
          h("span", { class: "atlas-badge", text: "TEST" })),
        h("div", { class: "atlas-stage" }, canvas, tip, notice),
        status),
      h("p", { class: "faint", style: "margin-top:18px;font-size:13.5px", text: "Scroll to zoom, drag to pan, double click to zoom in. Markers are the places already in the client's own files." }));

    // the canvas needs its box in the layout before it can size itself
    setTimeout(function () {
      sizeCanvas();
      refresh();
    }, 0);
    var onResize = function () { sizeCanvas(); };
    window.addEventListener("resize", onResize);
    st.timer = window.setInterval(function () {
      if (document.hidden) return;
      refresh();
    }, 5000);
    Z.onLeave(function () {
      window.removeEventListener("resize", onResize);
      window.removeEventListener("mousemove", onMove);
      window.removeEventListener("mouseup", onUp);
      window.clearInterval(st.timer);
      st.timer = 0;
    });
    return page;
  };
})();
