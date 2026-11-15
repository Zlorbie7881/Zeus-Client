/* Zeus Client website: the Source code page. Lists every bundled source file (see tools/make_source_manifest.py) and shows
   the file you pick with line numbers and light syntax colouring. No inline scripts (CSP). */
(function () {
  "use strict";
  var Z = window.Z;
  if (!Z) return;
  var h = Z.h, icon = Z.icon, clear = Z.clear;

  var KW = {
    Java: "abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for if implements import instanceof int interface long new null package private protected public return short static super switch synchronized this throw throws true false try var void volatile while record sealed permits yield",
    JavaScript: "break case catch class const continue default delete do else export extends false finally for function if import in instanceof let new null of return static super switch this throw true try typeof undefined var void while yield async await",
    Python: "and as assert break class continue def del elif else except False finally for from global if import in is lambda None nonlocal not or pass raise return True try while with yield",
    Gradle: "plugins id version repositories dependencies implementation include group def",
    Batch: "echo set if else for goto call exit pause",
  };
  var kwSet = {};
  Object.keys(KW).forEach(function (l) { var o = {}; KW[l].split(" ").forEach(function (w) { o[w] = 1; }); kwSet[l] = o; });

  function esc(t) { return t.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;"); }
  function fmtNum(n) { return Number(n).toLocaleString("en-US"); }
  function fmtSize(b) { return b < 1024 ? b + " B" : b < 1048576 ? (b / 1024).toFixed(b < 10240 ? 1 : 0) + " KB" : (b / 1048576).toFixed(1) + " MB"; }

  /* ----- a small tokenizer: comments, strings, numbers, keywords. Returns an HTML string per line. */
  function highlight(text, lang) {
    var lines = text.split("\n");
    if (lines.length && lines[lines.length - 1] === "") lines.pop();
    var kws = kwSet[lang] || null;
    var lineComment = { Java: "//", JavaScript: "//", Python: "#", Gradle: "//", Batch: "REM", Properties: "#" }[lang] || null;
    var hasBlock = lang === "Java" || lang === "JavaScript" || lang === "CSS" || lang === "Gradle";
    var jsonish = lang === "JSON";
    var inBlock = false;
    return lines.map(function (ln) {
      if (ln.length > 600) return esc(ln.slice(0, 600)) + '<span class="tk-c"> … (line shortened)</span>';
      if (lang === "Markdown" || lang === "Text" || lang === "Shell") {
        if (lang === "Markdown" && /^#{1,6}\s/.test(ln)) return '<span class="tk-k">' + esc(ln) + "</span>";
        if (lang === "Shell" && /^\s*#/.test(ln)) return '<span class="tk-c">' + esc(ln) + "</span>";
        return esc(ln);
      }
      var out = "", i = 0, n = ln.length;
      while (i < n) {
        if (inBlock) {
          var e = ln.indexOf("*/", i);
          if (e < 0) { out += '<span class="tk-c">' + esc(ln.slice(i)) + "</span>"; i = n; }
          else { out += '<span class="tk-c">' + esc(ln.slice(i, e + 2)) + "</span>"; i = e + 2; inBlock = false; }
          continue;
        }
        var c = ln.charAt(i);
        if (hasBlock && c === "/" && ln.charAt(i + 1) === "*") { inBlock = true; continue_block(); continue; }
        if (lineComment && ln.substr(i, lineComment.length) === lineComment && !jsonish) {
          out += '<span class="tk-c">' + esc(ln.slice(i)) + "</span>"; i = n; continue;
        }
        if (c === '"' || c === "'" || (c === "`" && lang === "JavaScript")) {
          var j = i + 1;
          while (j < n && ln.charAt(j) !== c) { if (ln.charAt(j) === "\\") j++; j++; }
          var str = ln.slice(i, Math.min(n, j + 1));
          var isKey = jsonish && /^\s*:/.test(ln.slice(j + 1));
          out += '<span class="' + (isKey ? "tk-p" : "tk-s") + '">' + esc(str) + "</span>";
          i = j + 1;
          continue;
        }
        if (/[0-9]/.test(c) && !/[A-Za-z_]/.test(ln.charAt(i - 1) || " ")) {
          var m = /^(0x[0-9a-fA-F_]+|[0-9][0-9_]*(\.[0-9]+)?([eE][+-]?[0-9]+)?[fFdDlL]?)/.exec(ln.slice(i));
          if (m) { out += '<span class="tk-n">' + esc(m[0]) + "</span>"; i += m[0].length; continue; }
        }
        if (/[A-Za-z_@]/.test(c)) {
          var w = /^[@A-Za-z_][A-Za-z0-9_]*/.exec(ln.slice(i))[0];
          if (kws && kws[w]) out += '<span class="tk-k">' + esc(w) + "</span>";
          else if (c === "@") out += '<span class="tk-a">' + esc(w) + "</span>";
          else if (lang === "Java" && /^[A-Z][A-Za-z0-9_]*$/.test(w) && w.length > 1 && !/^[A-Z0-9_]+$/.test(w)) out += '<span class="tk-t">' + esc(w) + "</span>";
          else out += esc(w);
          i += w.length;
          continue;
        }
        out += esc(c);
        i++;
      }
      return out;
      function continue_block() {
        var e2 = ln.indexOf("*/", i + 2);
        if (e2 < 0) { out += '<span class="tk-c">' + esc(ln.slice(i)) + "</span>"; i = n; }
        else { out += '<span class="tk-c">' + esc(ln.slice(i, e2 + 2)) + "</span>"; i = e2 + 2; inBlock = false; }
      }
    });
  }

  /* ----- the tree */
  function buildTree(files) {
    var root = { name: "", dirs: {}, files: [], count: 0, lines: 0 };
    files.forEach(function (f) {
      var parts = f.path.split("/"), node = root;
      node.count++; node.lines += f.lines;
      for (var i = 0; i < parts.length - 1; i++) {
        node = node.dirs[parts[i]] || (node.dirs[parts[i]] = { name: parts[i], dirs: {}, files: [], count: 0, lines: 0, path: parts.slice(0, i + 1).join("/") });
        node.count++; node.lines += f.lines;
      }
      node.files.push(f);
    });
    return root;
  }
  function collapseChains(node) {
    // a folder that holds only one folder (com/zlorbie788/zeus) is shown as a single row
    Object.keys(node.dirs).forEach(function (k) {
      var d = node.dirs[k];
      while (!d.files.length && Object.keys(d.dirs).length === 1) {
        var only = d.dirs[Object.keys(d.dirs)[0]];
        only.name = d.name + "/" + only.name;
        d = only;
      }
      node.dirs[k] = d;
      collapseChains(d);
    });
  }

  Z.pages.source = function () {
    var L = Z.site.links;
    var state = { index: null, current: null, q: "", lang: "", changed: false, open: {} };
    var cache = {};

    var statBox = h("div", { class: "fstats src-stats" });
    var treeBox = h("div", { class: "src-tree", role: "tree", "aria-label": "Source files" });
    var viewer = h("div", { class: "src-view" });
    var qIn = h("input", { type: "search", placeholder: "Filter files by name or folder…", "aria-label": "Filter source files", autocomplete: "off", spellcheck: "false" });
    var chipRow = h("div", { class: "src-langs", role: "group", "aria-label": "Languages" });
    var countEl = h("div", { class: "src-count faint" });

    var repo;
    if (L.github) {
      repo = h("a", { class: "btn btn-primary", href: L.github, target: "_blank", rel: "noopener noreferrer" }, icon("github"), h("span", { class: "btn-two" }, h("b", { text: "View on GitHub" }), h("small", { text: "Issues, history and downloads" })));
    } else {
      repo = h("div", { class: "src-repo soon", role: "group", "aria-label": "GitHub repository, not published yet" },
        h("span", { class: "btn btn-outline disabled", "aria-disabled": "true" }, icon("github"), h("span", { class: "btn-two" }, h("b", { text: "GitHub repository" }), h("small", { text: "Coming in a future update" }))));
    }
    var intro = h("div", { class: "src-head" },
      h("div", { class: "src-head-t" }, h("p", { class: "lead", text: "Every file of the mod, the website and the tools that build them. This copy is bundled automatically with each release, so it always matches the version you are looking at." }), statBox),
      h("div", { class: "src-head-a" }, repo,
        h("p", { class: "src-note" }, "Zeus is source-available under the licence in the ", h("a", { href: "#", class: "lnk", "data-open": "LICENSE", text: "LICENSE" }), " and ", h("a", { href: "#", class: "lnk", "data-open": "NOTICE", text: "NOTICE" }), " files. Read them before you reuse any of it.")));

    var layout = h("div", { class: "src-layout" },
      h("aside", { class: "src-side" },
        h("div", { class: "fsearch sm" }, icon("search"), qIn),
        chipRow, countEl, treeBox),
      viewer);

    var page = h("div", { class: "wrap page src" },
      Z.pageHead("Source code", "Browse the code", ""),
      intro, layout);

    function fileById(id) { return (state.index.files || []).filter(function (f) { return f.id === id; })[0]; }
    function fileByPath(p) { return (state.index.files || []).filter(function (f) { return f.path === p; })[0]; }
    function isChanged(f) {
      var c = state.index.changes;
      return !!(c && (c.added.indexOf(f.path) >= 0 || c.changed.indexOf(f.path) >= 0));
    }
    function matches(f) {
      if (state.lang && f.lang !== state.lang) return false;
      if (state.changed && !isChanged(f)) return false;
      var q = state.q.toLowerCase().trim();
      if (!q) return true;
      return q.split(/\s+/).every(function (t) { return f.path.toLowerCase().indexOf(t) >= 0; });
    }

    function paintStats() {
      var t = state.index.totals || {};
      clear(statBox);
      [["files", "files", t.files], ["lines", "lines of code", t.lines], ["langs", "languages", Object.keys(t.languages || {}).length], ["ver", "version", state.index.version || "–"]].forEach(function (s) {
        var b = h("b", { text: typeof s[2] === "number" ? "0" : String(s[2]) });
        statBox.appendChild(h("div", { class: "fstat" }, b, h("span", { text: s[1] })));
        if (typeof s[2] === "number") Z.countUp(b, s[2]);
      });
    }

    function paintChips() {
      clear(chipRow);
      var langs = (state.index.totals || {}).languages || {};
      var names = Object.keys(langs).sort(function (a, b) { return langs[b].lines - langs[a].lines; });
      function chip(label, value, n, extra) {
        var b = h("button", { type: "button", class: "faq-chip" + (state.lang === value && !state.changed && !extra ? " on" : "") + (extra && state.changed ? " on" : ""), "aria-pressed": "false" }, label, h("span", { class: "cnt", text: String(n) }));
        b.addEventListener("click", function () {
          if (extra) { state.changed = !state.changed; } else { state.lang = value; state.changed = false; }
          paintChips(); paintTree();
        });
        chipRow.appendChild(b);
      }
      chip("All", "", state.index.files.length);
      var c = state.index.changes;
      if (c) chip("Changed in " + (state.index.version || "this release"), "", c.added.length + c.changed.length, true);
      names.forEach(function (n) { chip(n, n, langs[n].files); });
    }

    function folderRow(node, depth, forceOpen) {
      var open = forceOpen || !!state.open[node.path];
      var row = h("button", { type: "button", class: "st-row dir" + (open ? " open" : ""), role: "treeitem", "aria-expanded": open ? "true" : "false", style: "--d:" + depth },
        icon("arrow", "small chev"), icon("folder", "small"), h("span", { class: "nm", text: node.name }), h("span", { class: "meta", text: String(node.count) }));
      row.addEventListener("click", function () { state.open[node.path] = !open; paintTree(); });
      return row;
    }
    function fileRow(f, depth) {
      var changed = isChanged(f);
      var row = h("button", { type: "button", class: "st-row file" + (state.current === f.id ? " on" : "") + (changed ? " chg" : ""), role: "treeitem", style: "--d:" + depth, title: f.path },
        icon("file", "small"), h("span", { class: "nm", text: f.path.split("/").pop() }), changed ? h("span", { class: "dot-chg", title: "Changed in this release" }) : null, h("span", { class: "meta", text: fmtNum(f.lines) }));
      row.addEventListener("click", function () { openFile(f, true); });
      return row;
    }
    function paintNode(node, depth, host, filtering, anim) {
      Object.keys(node.dirs).sort().forEach(function (k) {
        var d = node.dirs[k];
        var sub = filterNode(d);
        if (!sub) return;
        var open = filtering || !!state.open[d.path];
        host.appendChild(folderRow(sub, depth, filtering));
        if (open) paintNode(sub, depth + 1, host, filtering);
      });
      node.files.slice().sort(function (a, b) { return a.path < b.path ? -1 : 1; }).forEach(function (f) { if (matches(f)) host.appendChild(fileRow(f, depth)); });
    }
    function filterNode(node) {
      // a copy of the folder holding only matching files; null when nothing matches
      var copy = { name: node.name, path: node.path, dirs: {}, files: node.files.filter(matches), count: 0, lines: 0 };
      Object.keys(node.dirs).forEach(function (k) { var s = filterNode(node.dirs[k]); if (s) copy.dirs[k] = s; });
      copy.count = copy.files.length + Object.keys(copy.dirs).reduce(function (n, k) { return n + copy.dirs[k].count; }, 0);
      return copy.count ? copy : null;
    }
    var tree = null;
    function paintTree() {
      clear(treeBox);
      var filtering = !!(state.q.trim() || state.lang || state.changed);
      var shown = state.index.files.filter(matches).length;
      countEl.textContent = filtering ? shown + " of " + state.index.files.length + " files" : state.index.files.length + " files";
      var frag = document.createDocumentFragment();
      paintNode(tree, 0, frag, filtering);
      if (!frag.childNodes.length) frag.appendChild(h("div", { class: "empty", text: "No file matches that." }));
      treeBox.appendChild(frag);
    }
    qIn.addEventListener("input", Z.debounce(function () { state.q = qIn.value; paintTree(); }, 90));

    /* ----- the viewer */
    function viewerEmpty() {
      clear(viewer);
      var t = state.index.totals || {};
      var big = (state.index.files || []).slice().sort(function (a, b) { return b.lines - a.lines; }).slice(0, 5);
      var c = state.index.changes;
      viewer.appendChild(h("div", { class: "src-welcome" },
        h("div", { class: "sw-i" }, icon("code")),
        h("h2", { text: "Pick a file on the left" }),
        h("p", { class: "muted", text: "The tree shows every file in version " + (state.index.version || "") + ". Folders open on click, and the filter above the tree searches paths." }),
        c ? h("p", { class: "muted", text: c.added.length + c.changed.length + " files are new or changed since v" + c.since + ". Use the “Changed” chip to list them." })
          : h("p", { class: "muted", text: "From the next update on, the files that changed in each release are marked here." }),
        h("div", { class: "sw-big" }, h("span", { class: "tl-lab", text: "Biggest files" }), big.map(function (f) {
          var b = h("button", { type: "button", class: "tl-chip" }, f.path.split("/").pop(), h("small", { text: " " + fmtNum(f.lines) }));
          b.addEventListener("click", function () { openFile(f, true); });
          return b;
        })),
        h("p", { class: "faint", style: "margin-top:14px", text: "Generated " + (state.index.generated || "") + " · " + fmtNum(t.bytes || 0) + " bytes of text" })));
    }

    var loadToken = 0;
    function openFile(f, push) {
      state.current = f.id;
      var parts = f.path.split("/");
      for (var i = 1; i < parts.length; i++) state.open[parts.slice(0, i).join("/")] = true;
      // folders with collapsed single-child chains use the joined path as key, so also open those
      (function openChain(node, acc) {
        Object.keys(node.dirs).forEach(function (k) {
          var d = node.dirs[k];
          if (f.path.indexOf(d.path + "/") === 0) { state.open[d.path] = true; openChain(d); }
        });
      })(tree);
      paintTree();
      var token = ++loadToken;
      clear(viewer);
      var head = h("header", { class: "src-fh" },
        h("div", { class: "src-fh-t" }, h("span", { class: "src-lang", text: f.lang }), h("b", { text: f.path })),
        h("div", { class: "src-fh-m" }, h("span", { text: fmtNum(f.lines) + " lines" }), h("span", { text: fmtSize(f.size) }), h("span", { title: "First 16 characters of the file's SHA-256", text: "sha " + f.sha256 }), isChanged(f) ? h("span", { class: "tag hud", text: "Changed in this release" }) : null));
      var actions = h("div", { class: "src-fa" });
      var codeHost = h("div", { class: "src-code" }, h("div", { class: "src-skel" }, h("i"), h("i"), h("i"), h("i"), h("i"), h("i")));
      viewer.appendChild(head); viewer.appendChild(actions); viewer.appendChild(codeHost);
      if (push) { try { history.replaceState(null, "", "/source?f=" + encodeURIComponent(f.path)); } catch (e) { /* ignore */ } }
      if (window.innerWidth < 900) viewer.scrollIntoView({ behavior: Z.reduced ? "auto" : "smooth", block: "start" });
      load(f).then(function (text) {
        if (token !== loadToken) return;
        var copy = h("button", { type: "button", class: "btn btn-outline sm" }, icon("copy", "small"), "Copy");
        copy.addEventListener("click", function () {
          if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(text).then(function () { Z.toast("Copied " + f.path.split("/").pop()); }, function () { Z.toast("Could not copy", "bad"); });
        });
        var raw = h("a", { class: "btn btn-outline sm", href: "/codebundle/files/" + f.id + ".txt", target: "_blank", rel: "noopener noreferrer" }, icon("arrow", "small"), "Raw");
        var dl = h("a", { class: "btn btn-outline sm", href: "/codebundle/files/" + f.id + ".txt", download: f.path.split("/").pop() }, icon("download", "small"), "Download");
        var link = h("button", { type: "button", class: "btn btn-outline sm" }, icon("link", "small"), "Copy link");
        link.addEventListener("click", function () {
          var url = location.origin + "/source?f=" + encodeURIComponent(f.path);
          if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(url).then(function () { Z.toast("Link copied"); }, function () { Z.toast(url); }); else Z.toast(url);
        });
        actions.appendChild(copy); actions.appendChild(raw); actions.appendChild(dl); actions.appendChild(link);
        var lines = highlight(text, f.lang);
        clear(codeHost);
        var pre = h("pre", { class: "src-pre", tabindex: "0", "aria-label": "Source of " + f.path });
        var code = h("code", {});
        pre.appendChild(code);
        codeHost.appendChild(pre);
        // render in chunks so a 5000 line file never freezes the page
        var i = 0, CH = 400;
        (function chunk() {
          if (token !== loadToken) return;
          var frag = document.createDocumentFragment();
          for (var k = 0; k < CH && i < lines.length; k++, i++) {
            var row = document.createElement("span");
            row.className = "ln";
            row.setAttribute("data-n", String(i + 1));
            row.innerHTML = lines[i] || " ";
            frag.appendChild(row);
          }
          code.appendChild(frag);
          if (i < lines.length) requestAnimationFrame(chunk);
          else { var hash = (/#L(\d+)/.exec(location.hash) || [])[1]; if (hash) { var t = code.children[Number(hash) - 1]; if (t) { t.classList.add("hit"); t.scrollIntoView({ block: "center" }); } } }
        })();
      }, function () {
        if (token !== loadToken) return;
        clear(codeHost);
        codeHost.appendChild(h("div", { class: "empty", text: "Could not load this file." }));
      });
    }
    function load(f) {
      if (cache[f.id]) return Promise.resolve(cache[f.id]);
      return fetch("/codebundle/files/" + f.id + ".txt", { cache: "no-cache" }).then(function (r) {
        if (!r.ok) throw new Error(String(r.status));
        return r.text();
      }).then(function (t) { cache[f.id] = t; return t; });
    }

    page.addEventListener("click", function (e) {
      var a = e.target.closest && e.target.closest("a[data-open]");
      if (!a || !state.index) return;
      e.preventDefault();
      var f = fileByPath(a.getAttribute("data-open"));
      if (f) openFile(f, true);
    });

    Z.getJson("/codebundle/index.json").then(function (idx) {
      state.index = idx;
      tree = buildTree(idx.files || []);
      collapseChains(tree);
      paintStats(); paintChips();
      // folders that matter start open
      ["src", "tools"].forEach(function (p) { state.open[p] = true; });
      paintTree();
      var want = (/[?&]f=([^&#]+)/.exec(location.search) || [])[1];
      var f = want ? fileByPath(decodeURIComponent(want)) : null;
      if (f) openFile(f, false); else viewerEmpty();
    }, function () {
      clear(treeBox); clear(viewer);
      viewer.appendChild(h("div", { class: "empty", style: "padding:40px 20px" }, h("b", { text: "The source bundle is not part of this copy of the site." }),
        h("span", { text: "It is added by tools/make_source_manifest.py when a release is built." })));
    });
    return page;
  };
})();
