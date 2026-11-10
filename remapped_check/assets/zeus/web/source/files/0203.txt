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
      paint(big, k, f);
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
      h("p", { text: "Each picture is drawn by the same knife code the mod uses, with the knife at rest. In the game it moves, throws light and sits in your own hand. The same gallery is in the game: switch on Butterfly & Karambit and press Open the knife gallery." }),
      h("p", { class: "faint", text: "The straight knives share one finish setting, so choosing a finish for one changes all of them. The butterfly and the karambit each have their own colours." })));

    var page = h("div", { class: "wrap page" },
      Z.pageHead("Knives", "Knife preview", "Every knife in every finish, as the game draws it."),
      viewer, h("div", { style: "margin:26px 0 12px" }, filters), grid,
      h("div", { style: "margin-top:26px" }, note),
      h("div", { class: "cta-row", style: "margin-top:22px" },
        h("a", { class: "btn btn-primary", href: "/features?m=knife", "data-link": "" }, icon("cube"), h("span", { text: "See the knife module" })),
        h("a", { class: "btn btn-outline", href: "/download", "data-link": "" }, icon("download"), h("span", { text: "Download Zeus" }))));

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
