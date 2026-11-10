/* Zeus Client website: Knife preview. Every knife in every finish, as pictures drawn by the mod's own knife code
   (img/knives/knife-N.webp holds one strip of finishes per knife, knives.json lists the names). */
(function () {
  "use strict";
  var Z = window.Z;
  if (!Z) return;
  var h = Z.h, icon = Z.icon, clear = Z.clear;

  function src(t) {
    var p = "/img/knives/knife-" + t + ".webp";
    return (window.__zimg && window.__zimg[p]) || p;
  }
  // paints one finish of a knife into an element, cutting it out of the strip
  function paint(el, k, fin) {
    var n = k.finishes.length;
    el.style.backgroundImage = "url(" + src(k.id) + ")";
    el.style.backgroundSize = n * 100 + "% 100%";
    el.style.backgroundPosition = (n > 1 ? fin / (n - 1) * 100 : 0) + "% 0";
  }

  Z.pages.knives = function () {
    var list = [], sel = 0, fin = [], kind = "all";
    var big = h("div", { class: "kimg kbig", role: "img", "aria-label": "Knife preview" });
    var buildBar = function () {}, K3 = Z.knife3d, v3 = null, data3 = null, shown3 = -1, animBtns = {}, playBtn = null, scrub = null, stage = null, stageNote = null;
    // the clips each knife has (the order they are shown in) and what they are called; the hero knives have their own flourishes
    var ANIM_NAMES = { draw: "Draw", inspect: "Inspect", inspect2: "Inspect 2", slash: "Slash", slash2: "Slash 2", stab: "Stab", fan: "Fan", zen: "Zen twirl", aerial: "Aerial toss", spin: "Ring spin", pinwheel: "Pinwheel", toss: "Toss and catch" };
    var ANIM_ORDER = ["draw", "inspect", "inspect2", "fan", "zen", "aerial", "spin", "pinwheel", "toss", "slash", "slash2", "stab"];
    var ANIM_TEXT = {
      butterfly: { draw: "Pulled from the pocket closed: the safe handle whips out, the bite handle follows and the latch clicks home.", inspect: "A slow showing turn, then a fan: both handles close over the blade and open again.", inspect2: "The rollover: each handle goes right round the pivot while the blade stays put, then the knife turns to show its back.", fan: "Three close-and-open fans, each faster than the last.", zen: "A continuous twirl on its own axis while the handles are carried round.", aerial: "Tossed up end over end and caught with a small recoil.", slash: "A wind-up and a wrist cut across; the latch rattles on the follow-through.", slash2: "The same cut from the other side.", stab: "Pull back, thrust, recover." },
      karambit: { draw: "Swung up from below through one turn about the ring and caught on the finger.", inspect: "The ring spin: two turns, then it hangs on the finger and is turned to show the edge.", inspect2: "A back flip over the finger that also turns the blade on its axis so every face is seen.", spin: "Three quick turns around the ring.", pinwheel: "Forward, a pause at the top, then the reverse, like a propeller.", toss: "Thrown straight up with a lazy end-over-end and caught by the ring.", slash: "The hook sweeps across and rolls over at the end.", slash2: "The same sweep from the other side.", stab: "The ring hand pulls back, the hook is driven forward, a half-turn of the wrist at the end." }
    };
    function setup3d() {
      if (!K3 || !K3.supported() || stage) return;
      stage = h("div", { class: "k3d" });
      var vp = h("div", { class: "k3d-view" });
      stage.appendChild(vp);
      var bar = h("div", { class: "k3d-anims", role: "group", "aria-label": "Animation" });
      var loopOn = false;
      buildBar = function (k) {
        clear(bar); animBtns = {};
        var have = (data3 && data3[k.id] && data3[k.id].anims) || {};
        ANIM_ORDER.filter(function (key) { return have[key]; }).forEach(function (key) {
          var label = ANIM_NAMES[key];
          if (key === "inspect2" && k.kind === "butterfly") label = "Rollover";
          if (key === "inspect2" && k.kind === "karambit") label = "Back flip";
          var b = h("button", { type: "button", class: "k3d-btn", text: label });
          b.addEventListener("click", function () { if (v3) { v3.play(key, loopOn); mark(key); } });
          animBtns[key] = b; bar.appendChild(b);
        });
      };
      playBtn = h("button", { type: "button", class: "k3d-ic", "aria-label": "Pause", text: "Pause" });
      playBtn.addEventListener("click", function () {
        if (!v3) return;
        var st = v3.state();
        if (!st.anim) { v3.play("inspect", loopOn); mark("inspect"); return; }
        if (st.playing) v3.pause(); else v3.resume();
        sync();
      });
      scrub = h("input", { type: "range", min: "0", max: "1000", value: "0", class: "k3d-scrub", "aria-label": "Animation position" });
      scrub.addEventListener("input", function () { if (!v3) return; var st = v3.state(); if (!st.anim) { v3.play("inspect", false); mark("inspect"); st = v3.state(); } v3.seek(st.duration * scrub.value / 1000); sync(); });
      var loopBtn = h("button", { type: "button", class: "k3d-ic", "aria-pressed": "false", text: "Loop" });
      loopBtn.addEventListener("click", function () { loopOn = !loopOn; loopBtn.setAttribute("aria-pressed", loopOn ? "true" : "false"); loopBtn.classList.toggle("on", loopOn); if (v3) v3.setLoop(loopOn); });
      var spinBtn = h("button", { type: "button", class: "k3d-ic on", "aria-pressed": "true", text: "Turn" });
      spinBtn.addEventListener("click", function () { var on = spinBtn.getAttribute("aria-pressed") !== "true"; spinBtn.setAttribute("aria-pressed", on ? "true" : "false"); spinBtn.classList.toggle("on", on); if (v3) v3.setRotate(on); });
      var speed = h("select", { class: "k3d-sel", "aria-label": "Speed" }, h("option", { value: "0.4", text: "0.4x" }), h("option", { value: "0.7", text: "0.7x" }), h("option", { value: "1", selected: "", text: "1x" }), h("option", { value: "1.5", text: "1.5x" }));
      speed.value = "1";
      speed.addEventListener("change", function () { if (v3) v3.setSpeed(parseFloat(speed.value)); });
      var reset = h("button", { type: "button", class: "k3d-ic", text: "Reset view" });
      reset.addEventListener("click", function () { if (v3) v3.reset(); });
      stage.appendChild(bar);
      stage.appendChild(h("div", { class: "k3d-ctl" }, playBtn, scrub, loopBtn, spinBtn, speed, reset));
      stageNote = h("p", { class: "k3d-hint", text: "Drag to turn the knife, pinch or scroll to zoom, double-tap to reset." });
      stage.appendChild(stageNote);
      big.style.display = "none";
      big.parentNode && big.parentNode.insertBefore(stage, big);
      try {
        v3 = K3.create(vp, {});
        if (!v3) throw new Error("no context");
        Z.k3dView = v3;
        v3.onState(function (what) { sync(); });
        K3.load().then(function (m) { data3 = m; paintView(); }, function () { fallback3d(); });
      } catch (e) { fallback3d(); }
    }
    function fallback3d() { if (stage && stage.parentNode) stage.parentNode.removeChild(stage); stage = null; v3 = null; big.style.display = ""; paintView(); }
    function mark(key) {
      Object.keys(animBtns).forEach(function (k) { animBtns[k].classList.toggle("on", k === key); });
      var k = current(), tx = k && ANIM_TEXT[k.kind] && ANIM_TEXT[k.kind][key];
      if (stageNote) stageNote.textContent = tx ? tx : "Drag to turn the knife, pinch or scroll to zoom, double-tap to reset.";
      sync();
    }
    function sync() {
      if (!v3 || !scrub) return;
      var st = v3.state();
      scrub.value = st.duration ? Math.round(st.t / st.duration * 1000) : 0;
      playBtn.textContent = st.playing ? "Pause" : "Play";
      playBtn.setAttribute("aria-label", st.playing ? "Pause" : "Play");
    }

    var nameEl = h("h2", { class: "kname" });
    var finEl = h("div", { class: "kfinname" });
    var kindEl = h("span", { class: "tag" });
    var inspectEl = h("p", { class: "kinspect" });
    var finRow = h("div", { class: "kfins", role: "group", "aria-label": "Finish" });
    var grid = h("div", { class: "kgrid" });
    var filters = h("div", { class: "kfilters" });

    function current() { return list[sel]; }
    function paintView() {
      var k = current();
      if (!k) return;
      var f = fin[sel] || 0;
      if (v3 && data3 && data3[k.id]) {
        if (shown3 !== k.id) { v3.setKnife(data3[k.id]); shown3 = k.id; buildBar(k); sync(); v3.play("draw", false); mark("draw"); }
        v3.setFinish(k.finishes[f]);
      } else paint(big, k, f);
      big.setAttribute("aria-label", k.name + ", " + k.finishes[f]);
      nameEl.textContent = k.name;
      finEl.textContent = k.finishes[f];
      kindEl.textContent = k.kind === "butterfly" ? "Balisong" : k.kind === "karambit" ? "Karambit" : "Straight knife";
      inspectEl.textContent = k.inspect;
      clear(finRow);
      k.finishes.forEach(function (name, i) {
        var b = h("button", { type: "button", class: "kfin" + (i === f ? " on" : ""), title: name, "aria-label": name, "aria-pressed": i === f ? "true" : "false" });
        var t = h("span", { class: "kimg" });
        paint(t, k, i);
        b.appendChild(t);
        b.addEventListener("click", function () {
          if (k.kind === "straight") list.forEach(function (o, j) { if (o.kind === "straight") fin[j] = i; });   // the straight knives share one finish in the game
          else fin[sel] = i;
          paintView(); paintGrid();
        });
        finRow.appendChild(b);
      });
    }
    function paintGrid() {
      clear(grid);
      list.forEach(function (k, i) {
        if (kind !== "all" && (kind === "straight" ? k.kind !== "straight" : k.kind === "straight")) return;
        var c = h("button", { type: "button", class: "kcard" + (i === sel ? " on" : ""), "aria-pressed": i === sel ? "true" : "false" });
        var t = h("span", { class: "kimg" });
        paint(t, k, fin[i] || 0);
        c.appendChild(t);
        c.appendChild(h("span", { class: "kcn", text: k.name }));
        c.addEventListener("click", function () { sel = i; paintView(); paintGrid(); var top = big.getBoundingClientRect().top; if (top < 0) window.scrollBy({ top: top - 90, behavior: Z.reduced ? "auto" : "smooth" }); });
        grid.appendChild(c);
      });
    }
    function paintFilters() {
      clear(filters);
      [["all", "All knives"], ["folding", "Butterfly and karambit"], ["straight", "Straight knives"]].forEach(function (f) {
        var b = h("button", { type: "button", class: "fbtn" + (kind === f[0] ? " on" : ""), "aria-pressed": kind === f[0] ? "true" : "false", text: f[1] });
        b.addEventListener("click", function () { kind = f[0]; paintFilters(); paintGrid(); });
        filters.appendChild(b);
      });
    }

    var viewer = h("div", { class: "card kview" },
      big,
      h("div", { class: "kinfo" }, kindEl, nameEl, finEl,
        h("h3", { class: "kh", text: "Finish" }), finRow,
        h("h3", { class: "kh", text: "Inspect" }), inspectEl));

    var note = h("div", { class: "note-box" }, icon("info"), h("div", {},
      h("b", { text: "About these pictures" }),
      h("p", { text: "The 3D view shows an original model of each knife with its own animations; drag to turn it, pinch to zoom. Finishes are painted live. In the game the same models are drawn lighter and sit in your own hand. The same gallery is in the game: switch on Butterfly & Karambit and press Open the knife gallery." }),
      h("p", { class: "faint", text: "The straight knives share one finish setting, so choosing a finish for one changes all of them. The butterfly and the karambit each have their own colours." })));

    var page = h("div", { class: "wrap page" },
      Z.pageHead("Knives", "Knife preview", "Every knife in every finish, as the game draws it."),
      viewer, h("div", { style: "margin:26px 0 12px" }, filters), grid,
      h("div", { style: "margin-top:26px" }, note),
      h("div", { class: "cta-row", style: "margin-top:22px" },
        h("a", { class: "btn btn-primary", href: "/features?m=knife", "data-link": "" }, icon("cube"), h("span", { text: "See the knife module" })),
        h("a", { class: "btn btn-outline", href: "/download", "data-link": "" }, icon("download"), h("span", { text: "Download Zeus" }))));

    setTimeout(function () { setup3d(); }, 0);
    Z.getJson("/knives.json").then(function (d) {
      list = d || [];
      fin = list.map(function () { return 0; });
      var want = parseInt((/[?&]k=(\d+)/.exec(location.search) || [])[1], 10);
      if (want >= 0 && want < list.length) sel = want;
      paintFilters(); paintView(); paintGrid();
    }, function () { grid.appendChild(h("div", { class: "empty", text: "The knife list could not be loaded." })); });
    return page;
  };
})();
