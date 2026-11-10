/* Zeus Client website: profile (kept in this browser only), saved modules, Build a config, Troubleshooter, module media.
   No inline scripts (CSP). Nothing here talks to a server: accounts, saved modules and configs live in this browser's storage. */
(function () {
  "use strict";
  var Z = window.Z;
  if (!Z) return;
  var h = Z.h, icon = Z.icon, clear = Z.clear, store = Z.store;
  var REDUCED = Z.reduced;

  function ev(name, detail) { document.dispatchEvent(new CustomEvent(name, { detail: detail || {} })); }
  function jget(key, fallback) { try { var v = JSON.parse(localStorage.getItem(key) || "null"); return v === null ? fallback : v; } catch (e) { return fallback; } }
  function jset(key, value) { try { localStorage.setItem(key, JSON.stringify(value)); return true; } catch (e) { return false; } }
  function when(t) { try { return new Date(t).toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" }); } catch (e) { return ""; } }
  function copyText(text, ok) {
    var done = function () { Z.toast(ok || "Copied"); };
    if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(text).then(done, function () { Z.toast("Could not copy", "bad"); });
    else Z.toast("Copy is not available here", "bad");
  }
  function saveFile(name, text, type) {
    var url = URL.createObjectURL(new Blob([text], { type: type || "application/json" }));
    var a = h("a", { href: url, download: name });
    document.body.appendChild(a); a.click(); document.body.removeChild(a);
    setTimeout(function () { URL.revokeObjectURL(url); }, 4000);
  }
  function modName(id) {
    var m = ((Z.features && Z.features.modules) || []).filter(function (x) { return x.id === id; })[0];
    return m ? m.name : id;
  }

  /* ======================================================================= accounts (this browser only)
     There is no server behind this site. A "profile" is a record in this browser's localStorage; the password is never stored,
     only a salted PBKDF2 hash made with the browser's own crypto. Clearing the site's data deletes the profile, and a forgotten
     password cannot be recovered (nobody else has a copy). */
  var USERS = "zeus.users", SESSION = "zeus.session";
  var subtle = window.crypto && window.crypto.subtle;
  function b64(bytes) { var s = ""; bytes = new Uint8Array(bytes); for (var i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]); return btoa(s); }
  function unb64(t) { var s = atob(t), a = new Uint8Array(s.length); for (var i = 0; i < s.length; i++) a[i] = s.charCodeAt(i); return a; }
  function derive(password, salt, iters) {
    var enc = new TextEncoder();
    return subtle.importKey("raw", enc.encode(password), "PBKDF2", false, ["deriveBits"]).then(function (k) {
      return subtle.deriveBits({ name: "PBKDF2", salt: salt, iterations: iters, hash: "SHA-256" }, k, 256);
    }).then(b64);
  }
  function users() { var u = jget(USERS, {}); return u && typeof u === "object" ? u : {}; }
  function emailKey(e) { return String(e || "").trim().toLowerCase(); }
  function hue(s) { var n = 0; for (var i = 0; i < s.length; i++) n = (n * 31 + s.charCodeAt(i)) % 360; return n; }

  var current = null;
  function loadSession() {
    var id = store(SESSION);
    var sess = null;
    try { sess = sessionStorage.getItem(SESSION); } catch (e) { /* ignore */ }
    id = id || sess;
    var u = users()[id];
    current = u ? u : null;
  }
  var auth = (Z.auth = {
    available: !!subtle,
    user: function () { return current; },
    register: function (name, email, password, stay) {
      if (!subtle) return Promise.reject(new Error("This browser cannot hash passwords here. Open the site over HTTPS (or from inside the game) and try again."));
      name = String(name || "").trim().replace(/\s+/g, " ");
      var key = emailKey(email);
      if (name.length < 2 || name.length > 32) return Promise.reject(new Error("Choose a display name between 2 and 32 characters."));
      if (!/^[^@\s]+@[^@\s]+\.[^@\s]{2,}$/.test(key) || key.length > 120) return Promise.reject(new Error("That does not look like an email address."));
      if (String(password).length < 8) return Promise.reject(new Error("Use a password of at least 8 characters."));
      if (String(password).length > 200) return Promise.reject(new Error("That password is too long."));
      var all = users();
      for (var k in all) if (all[k].email === key) return Promise.reject(new Error("A profile with that email already exists in this browser. Sign in instead."));
      var salt = crypto.getRandomValues(new Uint8Array(16)), iters = 150000;
      return derive(password, salt, iters).then(function (hash) {
        var id = "u" + b64(crypto.getRandomValues(new Uint8Array(9))).replace(/[^A-Za-z0-9]/g, "x");
        var rec = { id: id, name: name, email: key, salt: b64(salt), iters: iters, hash: hash, created: Date.now() };
        all[id] = rec;
        if (!jset(USERS, all)) throw new Error("This browser would not let the site save your profile (private mode or blocked storage).");
        auth.adopt(rec, stay);
        return rec;
      });
    },
    login: function (email, password, stay) {
      if (!subtle) return Promise.reject(new Error("This browser cannot check passwords here. Open the site over HTTPS (or from inside the game)."));
      var key = emailKey(email), all = users(), rec = null;
      for (var k in all) if (all[k].email === key) rec = all[k];
      var fail = new Error("Wrong email or password.");
      var wait = Math.min(4000, (auth._fails || 0) * 700);
      var later = function (fn) { return new Promise(function (res) { setTimeout(res, wait); }).then(fn); };
      if (!rec) { auth._fails = (auth._fails || 0) + 1; return later(function () { throw fail; }); }
      return derive(password, unb64(rec.salt), rec.iters).then(function (hash) {
        if (hash !== rec.hash) { auth._fails = (auth._fails || 0) + 1; return later(function () { throw fail; }); }
        auth._fails = 0;
        auth.adopt(rec, stay);
        return rec;
      });
    },
    adopt: function (rec, stay) {
      current = rec;
      store(SESSION, null);
      try { sessionStorage.removeItem(SESSION); } catch (e) { /* ignore */ }
      if (stay) store(SESSION, rec.id); else { try { sessionStorage.setItem(SESSION, rec.id); } catch (e) { store(SESSION, rec.id); } }
      mergeGuest(rec.id);
      ev("zeus:auth");
    },
    logout: function () {
      current = null;
      store(SESSION, null);
      try { sessionStorage.removeItem(SESSION); } catch (e) { /* ignore */ }
      ev("zeus:auth");
    },
    update: function (patch) {
      if (!current) return;
      var all = users();
      if (!all[current.id]) return;
      for (var k in patch) all[current.id][k] = patch[k];
      jset(USERS, all);
      current = all[current.id];
      ev("zeus:auth");
    },
    changePassword: function (oldPw, newPw) {
      if (!current) return Promise.reject(new Error("Not signed in."));
      if (String(newPw).length < 8) return Promise.reject(new Error("Use a new password of at least 8 characters."));
      var rec = current;
      return derive(oldPw, unb64(rec.salt), rec.iters).then(function (hash) {
        if (hash !== rec.hash) throw new Error("The current password is wrong.");
        var salt = crypto.getRandomValues(new Uint8Array(16));
        return derive(newPw, salt, 150000).then(function (nh) { auth.update({ salt: b64(salt), iters: 150000, hash: nh }); });
      });
    },
    remove: function () {
      if (!current) return;
      var all = users(), id = current.id;
      delete all[id];
      jset(USERS, all);
      store("zeus.data." + id, null);
      auth.logout();
    },
  });

  /* ======================================================================= per-profile data: saved modules and configs
     Stored under "zeus.data.<profile id>", or "zeus.data.guest" when nobody is signed in. Signing in adds the guest's items to the profile. */
  function dataKey() { return "zeus.data." + (current ? current.id : "guest"); }
  function readData(key) {
    var d = jget(key || dataKey(), null);
    if (!d || typeof d !== "object") d = {};
    if (!Array.isArray(d.saved)) d.saved = [];
    if (!Array.isArray(d.configs)) d.configs = [];
    return d;
  }
  function writeData(d, key) { jset(key || dataKey(), d); ev("zeus:data"); }
  function mergeGuest(id) {
    var g = readData("zeus.data.guest");
    if (!g.saved.length && !g.configs.length) return;
    var d = readData("zeus.data." + id);
    g.saved.forEach(function (m) { if (d.saved.indexOf(m) < 0) d.saved.push(m); });
    g.configs.forEach(function (c) { d.configs.push(c); });
    jset("zeus.data." + id, d);
    store("zeus.data.guest", null);
  }
  Z.saved = {
    has: function (id) { return readData().saved.indexOf(id) >= 0; },
    toggle: function (id) {
      var d = readData(), i = d.saved.indexOf(id);
      if (i >= 0) d.saved.splice(i, 1); else d.saved.push(id);
      writeData(d);
      return i < 0;
    },
    list: function () { return readData().saved.slice(); },
  };
  Z.configs = {
    list: function () { return readData().configs.slice(); },
    add: function (c) { var d = readData(); d.configs.unshift(c); d.configs = d.configs.slice(0, 30); writeData(d); },
    remove: function (i) { var d = readData(); d.configs.splice(i, 1); writeData(d); },
  };

  /* ----- the avatar button in the header */
  function headerButton() {
    var help = document.getElementById("helpTop");
    if (!help || document.getElementById("acctTop")) return;
    var b = h("a", { class: "btn btn-acct", id: "acctTop", href: "/account", "aria-label": "Your profile" });
    help.parentNode.insertBefore(b, help.nextSibling);
    function paint() {
      clear(b);
      if (current) {
        b.title = "Signed in as " + current.name;
        b.appendChild(h("span", { class: "av", style: "--h:" + hue(current.id) }, current.name.charAt(0).toUpperCase()));
        b.appendChild(h("span", { class: "acct-n", text: current.name }));
      } else {
        b.title = "Sign in or create a profile";
        b.appendChild(icon("user"));
        b.appendChild(h("span", { class: "acct-n", text: "Sign in" }));
      }
    }
    paint();
    document.addEventListener("zeus:auth", paint);
    document.addEventListener("zeus:page", function (e) { b.classList.toggle("on", e.detail && e.detail.page === "account"); });
  }
  loadSession();
  Z.ready.push(headerButton);

  /* ======================================================================= the profile page */
  function field(label, input, hint) {
    return h("label", { class: "fld" }, h("span", { class: "fld-l", text: label }), input, hint ? h("small", { class: "fld-h", text: hint }) : null);
  }
  function input(type, name, ph, attrs) {
    var o = { type: type, name: name, placeholder: ph || "", autocomplete: type === "password" ? (attrs && attrs.ac) || "current-password" : (attrs && attrs.ac) || "off", spellcheck: "false" };
    if (attrs) for (var k in attrs) if (k !== "ac") o[k] = attrs[k];
    return h("input", o);
  }
  function errBox() { return h("div", { class: "form-err", role: "alert", hidden: true }); }
  function showErr(box, msg) { box.textContent = msg; box.hidden = !msg; }
  function busy(btn, on, label) { btn.disabled = !!on; btn.classList.toggle("busy", !!on); if (label) btn.lastChild.textContent = label; }

  function signedOutView() {
    var mode = /[?&]mode=register/.test(location.search) ? "register" : "login";
    var tabs = h("div", { class: "tabs2", role: "tablist" });
    var tLogin = h("button", { type: "button", role: "tab", class: "tab2", text: "Sign in" });
    var tReg = h("button", { type: "button", role: "tab", class: "tab2", text: "Create profile" });
    tabs.appendChild(tLogin); tabs.appendChild(tReg);
    var host = h("div", { class: "auth-form" });

    function loginForm() {
      var email = input("email", "email", "you@example.com", { ac: "email", maxlength: "120", required: "" });
      var pw = input("password", "password", "Your password", { ac: "current-password", maxlength: "200", required: "" });
      var stay = h("input", { type: "checkbox", id: "stay1" });
      var err = errBox();
      var go = h("button", { type: "submit", class: "btn btn-primary" }, icon("lock", "small"), h("span", { text: "Sign in" }));
      var f = h("form", { class: "form", novalidate: "" }, field("Email", email), field("Password", pw),
        h("label", { class: "chk" }, stay, h("span", { text: "Keep me signed in on this device" })), err, go);
      f.addEventListener("submit", function (e) {
        e.preventDefault();
        showErr(err, "");
        busy(go, true);
        var who = email.value.trim();
        if (who && who.indexOf("@") < 0 && Z.admin) {
          Z.admin.login(who, pw.value).then(function () { Z.toast("Signed in"); Z.go("/admin"); }, function (x) { busy(go, false); showErr(err, x.message); });
          return;
        }
        auth.login(email.value, pw.value, stay.checked).then(function () { Z.toast("Signed in"); }, function (x) { busy(go, false); showErr(err, x.message); });
      });
      return f;
    }
    function registerForm() {
      var name = input("text", "name", "How you want to be called", { ac: "nickname", maxlength: "32", required: "" });
      var email = input("email", "email", "you@example.com", { ac: "email", maxlength: "120", required: "" });
      var pw = input("password", "password", "At least 8 characters", { ac: "new-password", maxlength: "200", required: "" });
      var pw2 = input("password", "password2", "Type it again", { ac: "new-password", maxlength: "200", required: "" });
      var meter = h("div", { class: "pw-meter", "aria-hidden": "true" }, h("i"), h("i"), h("i"), h("i"));
      pw.addEventListener("input", function () {
        var v = pw.value, sc = 0;
        if (v.length >= 8) sc++; if (v.length >= 12) sc++; if (/[A-Z]/.test(v) && /[a-z]/.test(v)) sc++; if (/[0-9]/.test(v) && /[^A-Za-z0-9]/.test(v)) sc++;
        meter.setAttribute("data-s", String(sc));
      });
      var stay = h("input", { type: "checkbox", id: "stay2", checked: "" });
      var err = errBox();
      var go = h("button", { type: "submit", class: "btn btn-primary" }, icon("user", "small"), h("span", { text: "Create profile" }));
      var f = h("form", { class: "form", novalidate: "" }, field("Display name", name), field("Email", email, "Used only as your sign-in name on this device. No email is ever sent."),
        field("Password", pw), meter, field("Repeat password", pw2),
        h("label", { class: "chk" }, stay, h("span", { text: "Keep me signed in on this device" })), err, go);
      f.addEventListener("submit", function (e) {
        e.preventDefault();
        showErr(err, "");
        if (pw.value !== pw2.value) { showErr(err, "The two passwords are not the same."); return; }
        busy(go, true);
        auth.register(name.value, email.value, pw.value, stay.checked).then(function () { Z.toast("Profile created"); }, function (x) { busy(go, false); showErr(err, x.message); });
      });
      return f;
    }
    function show(m) {
      mode = m;
      tLogin.classList.toggle("on", m === "login"); tReg.classList.toggle("on", m === "register");
      tLogin.setAttribute("aria-selected", m === "login" ? "true" : "false"); tReg.setAttribute("aria-selected", m === "register" ? "true" : "false");
      clear(host);
      var f = m === "login" ? loginForm() : registerForm();
      f.classList.add("swap-in");
      host.appendChild(f);
    }
    tLogin.addEventListener("click", function () { show("login"); });
    tReg.addEventListener("click", function () { show("register"); });
    show(mode);

    function social(name, ic) {
      return h("button", { type: "button", class: "btn btn-outline social", disabled: "", "aria-disabled": "true", title: name + " sign-in is not available yet" },
        icon(ic), h("span", { text: "Continue with " + name }), h("em", { class: "soon-tag", text: "Coming soon" }));
    }
    var notice = h("div", { class: "note-box" }, icon("info"),
      h("div", {}, h("b", { text: "How profiles work here" }),
        h("p", { text: "There is no Zeus account server. Your profile is saved inside this browser only: nothing is uploaded, no email is sent and nobody can reset a password for you. If you clear this site's data or switch browser, the profile is gone, so use “Export my data” on the profile page to keep a copy." })));
    var side = h("div", { class: "card auth-side" },
      h("h2", { class: "sec", text: "What a profile gives you" }),
      h("ul", { class: "ticks" },
        ["Save modules with the star on the Features page", "Keep the configs you build on the Build a config page", "See the questions the helper could not answer", "Export everything as one file whenever you like"].map(function (t) { return h("li", {}, icon("check", "small"), t); })),
      notice);
    var card = h("div", { class: "card auth-card" },
      h("div", { class: "auth-brand" }, Z.logo(), h("div", {}, h("h2", { text: "Your Zeus profile" }), h("p", { class: "muted", text: "Sign in or create a profile to keep your saved modules and configs together." }))),
      tabs, host,
      h("div", { class: "or" }, h("span", { text: "or" })),
      h("div", { class: "socials" }, social("Google", "mail"), social("Discord", "discord"), social("GitHub", "github")),
      h("p", { class: "faint", style: "margin-top:10px", text: "Single sign-on needs a Zeus server, which does not exist yet." }));
    var grid = h("div", { class: "auth-grid" }, card, side);
    Z.stagger(grid.children, 110);
    return grid;
  }

  function stat(label, value, href) {
    var b = h("b", { text: "0" });
    var el = h(href ? "a" : "div", { class: "fstat", href: href || null }, b, h("span", { text: label }));
    Z.countUp(b, value);
    return el;
  }

  function signedInView() {
    var u = current;
    var tab = "overview";
    var body = h("div", { class: "acct-body" });
    var tabs = h("div", { class: "tabs2 big", role: "tablist" });
    var defs = [["overview", "Overview"], ["saved", "Saved modules"], ["configs", "My configs"], ["log", "Helper history"], ["settings", "Settings"]];
    var btns = {};
    defs.forEach(function (d) {
      var b = h("button", { type: "button", role: "tab", class: "tab2", text: d[1] });
      b.addEventListener("click", function () { tab = d[0]; paint(); });
      btns[d[0]] = b; tabs.appendChild(b);
    });

    function sectionSaved() {
      var list = Z.saved.list();
      var box = h("div", { class: "card" }, h("h2", { class: "sec", text: "Saved modules" }));
      if (!list.length) {
        box.appendChild(h("p", { class: "muted", text: "Nothing saved yet. Open the Features page and press the star on a module." }));
        box.appendChild(h("a", { class: "btn btn-outline sm", href: "/features" }, icon("cube", "small"), "Browse modules"));
        return box;
      }
      box.appendChild(h("div", { class: "saved-grid" }, list.map(function (id) {
        var m = ((Z.features && Z.features.modules) || []).filter(function (x) { return x.id === id; })[0];
        if (!m) return null;
        var rm = h("button", { type: "button", class: "dbtn", "aria-label": "Remove " + m.name, title: "Remove" }, icon("close"));
        rm.addEventListener("click", function () { Z.saved.toggle(id); paint(); });
        return h("div", { class: "saved-item" }, h("a", { href: "/features?m=" + encodeURIComponent(id) }, h("b", { text: m.name }), h("small", { text: (m.description || "").slice(0, 90) })), rm);
      })));
      box.appendChild(h("div", { class: "row-actions" },
        h("button", { type: "button", class: "btn btn-outline sm", onclick: function () { copyText(list.map(function (id) { return "/zeus on " + id; }).join("\n"), "Commands copied"); } }, icon("copy", "small"), "Copy as /zeus on commands")));
      return box;
    }
    function sectionConfigs() {
      var list = Z.configs.list();
      var box = h("div", { class: "card" }, h("h2", { class: "sec", text: "My configs" }));
      if (!list.length) {
        box.appendChild(h("p", { class: "muted", text: "You have not saved a config yet." }));
        box.appendChild(h("a", { class: "btn btn-outline sm", href: "/config" }, icon("sliders", "small"), "Build a config"));
        return box;
      }
      list.forEach(function (c, i) {
        var dl = h("button", { type: "button", class: "btn btn-outline sm" }, icon("download", "small"), "zeus.json");
        dl.addEventListener("click", function () { saveFile("zeus.json", buildJson(c.mods), "application/json"); });
        var cp = h("button", { type: "button", class: "btn btn-outline sm" }, icon("copy", "small"), "Commands");
        cp.addEventListener("click", function () { copyText(c.mods.map(function (id) { return "/zeus on " + id; }).join("\n"), "Commands copied"); });
        var rm = h("button", { type: "button", class: "dbtn", "aria-label": "Delete this config", title: "Delete" }, icon("close"));
        rm.addEventListener("click", function () { Z.configs.remove(i); paint(); });
        box.appendChild(h("div", { class: "cfg-item" }, h("div", { class: "cfg-t" }, h("b", { text: c.name }), h("small", { text: c.mods.length + " modules · " + (c.preset || "custom") + " · " + when(c.created) })), h("div", { class: "cfg-a" }, dl, cp, rm)));
      });
      return box;
    }
    function sectionLog() {
      var log = Z.helperLog ? Z.helperLog.read() : [];
      var box = h("div", { class: "card" }, h("h2", { class: "sec", text: "Helper history" }),
        h("p", { class: "muted", text: "Questions the helper could not answer, and your Yes/No answers to “Did that help?”. This list is stored in this browser only; nothing is sent anywhere. Use it to ask on Discord." }));
      if (!log.length) { box.appendChild(h("p", { class: "faint", text: "Nothing logged yet." })); return box; }
      var KIND = { miss: ["No answer found", "bad"], unhelpful: ["Marked not helpful", "warn"], helped: ["Helped", "ok"] };
      var rows = log.slice().reverse();
      var list = h("div", { class: "log-list" }, rows.map(function (r) {
        var k = KIND[r.kind] || ["", ""];
        return h("div", { class: "log-item" }, h("span", { class: "pill " + k[1], text: k[0] }), h("span", { class: "log-q", text: r.q }), h("small", { text: when(r.t) }));
      }));
      var open = rows.filter(function (r) { return r.kind === "miss" || r.kind === "unhelpful"; });
      box.appendChild(list);
      box.appendChild(h("div", { class: "row-actions" },
        h("button", { type: "button", class: "btn btn-outline sm", onclick: function () { copyText(open.map(function (r) { return "- " + r.q; }).join("\n") || "(none)", "Copied the unanswered questions"); } }, icon("copy", "small"), "Copy unanswered (" + open.length + ")"),
        h("button", { type: "button", class: "btn btn-outline sm", onclick: function () { Z.helperLog.write([]); paint(); Z.toast("History cleared"); } }, icon("close", "small"), "Clear history")));
      return box;
    }
    function sectionSettings() {
      var wrap = h("div", { class: "stack" });
      // name
      var nm = input("text", "name", "", { maxlength: "32", ac: "nickname" }); nm.value = u.name;
      var nerr = errBox();
      var nform = h("form", { class: "form", novalidate: "" }, field("Display name", nm), nerr, h("button", { type: "submit", class: "btn btn-primary sm" }, icon("check", "small"), h("span", { text: "Save name" })));
      nform.addEventListener("submit", function (e) {
        e.preventDefault();
        var v = nm.value.trim().replace(/\s+/g, " ");
        if (v.length < 2 || v.length > 32) { showErr(nerr, "Choose a display name between 2 and 32 characters."); return; }
        auth.update({ name: v }); Z.toast("Name saved");
      });
      // password
      var o = input("password", "old", "Current password", { ac: "current-password", maxlength: "200" });
      var n1 = input("password", "new", "New password (8+ characters)", { ac: "new-password", maxlength: "200" });
      var perr = errBox();
      var pbtn = h("button", { type: "submit", class: "btn btn-primary sm" }, icon("lock", "small"), h("span", { text: "Change password" }));
      var pform = h("form", { class: "form", novalidate: "" }, field("Current password", o), field("New password", n1), perr, pbtn);
      pform.addEventListener("submit", function (e) {
        e.preventDefault(); showErr(perr, ""); busy(pbtn, true);
        auth.changePassword(o.value, n1.value).then(function () { busy(pbtn, false); o.value = ""; n1.value = ""; Z.toast("Password changed"); }, function (x) { busy(pbtn, false); showErr(perr, x.message); });
      });
      // data
      var exp = h("button", { type: "button", class: "btn btn-outline sm" }, icon("download", "small"), "Export my data");
      exp.addEventListener("click", function () {
        var d = readData();
        saveFile("zeus-profile.json", JSON.stringify({ app: "zeus-site-profile", exported: new Date().toISOString(), name: u.name, email: u.email, saved: d.saved, configs: d.configs, helperLog: Z.helperLog ? Z.helperLog.read() : [] }, null, 1));
      });
      var imp = h("input", { type: "file", accept: "application/json,.json", hidden: true });
      var impBtn = h("button", { type: "button", class: "btn btn-outline sm" }, icon("refresh", "small"), "Import a backup");
      impBtn.addEventListener("click", function () { imp.click(); });
      imp.addEventListener("change", function () {
        var f = imp.files && imp.files[0];
        if (!f || f.size > 400000) { Z.toast("Choose a zeus-profile.json file under 400 KB", "bad"); return; }
        f.text().then(function (t) {
          var j = JSON.parse(t);
          if (!j || j.app !== "zeus-site-profile") throw new Error("not a profile backup");
          var d = readData();
          (Array.isArray(j.saved) ? j.saved : []).forEach(function (id) { if (typeof id === "string" && /^[a-z0-9_]{1,40}$/.test(id) && d.saved.indexOf(id) < 0) d.saved.push(id); });
          (Array.isArray(j.configs) ? j.configs : []).slice(0, 30).forEach(function (c) {
            if (c && typeof c.name === "string" && Array.isArray(c.mods)) d.configs.push({ name: c.name.slice(0, 60), preset: String(c.preset || "").slice(0, 40), created: Number(c.created) || Date.now(), mods: c.mods.filter(function (x) { return typeof x === "string" && /^[a-z0-9_]{1,40}$/.test(x); }).slice(0, 400) });
          });
          writeData(d); Z.toast("Backup imported"); paint();
        }).catch(function () { Z.toast("That file is not a Zeus profile backup", "bad"); });
      });
      var out = h("button", { type: "button", class: "btn btn-outline sm" }, icon("logout", "small"), "Sign out");
      out.addEventListener("click", function () { auth.logout(); Z.toast("Signed out"); });
      var del = h("button", { type: "button", class: "btn btn-danger sm" }, icon("close", "small"), "Delete this profile");
      var armed = false;
      del.addEventListener("click", function () {
        if (!armed) { armed = true; del.lastChild.textContent = "Press again to delete"; setTimeout(function () { armed = false; del.lastChild.textContent = "Delete this profile"; }, 4000); return; }
        auth.remove(); Z.toast("Profile deleted from this browser");
      });
      wrap.appendChild(h("div", { class: "grid g2" },
        h("div", { class: "card" }, h("h2", { class: "sec", text: "Display name" }), nform),
        h("div", { class: "card" }, h("h2", { class: "sec", text: "Password" }), pform)));
      wrap.appendChild(h("div", { class: "card" }, h("h2", { class: "sec", text: "Your data" }),
        h("p", { class: "muted", text: "Everything lives in this browser. Export it to keep a copy or to move to another browser (then create a profile there and import the file)." }),
        h("div", { class: "row-actions" }, exp, impBtn, imp, out, del)));
      return wrap;
    }
    function sectionOverview() {
      var d = readData(), log = Z.helperLog ? Z.helperLog.read() : [];
      var stats = h("div", { class: "fstats" },
        stat("saved modules", d.saved.length), stat("configs", d.configs.length), stat("helper questions", log.length));
      var quick = h("div", { class: "tool-cards" },
        [["cube", "Browse modules", "Star the ones you use.", "/features"], ["sliders", "Build a config", "Pick a playstyle and download it.", "/config"], ["wand", "Troubleshooter", "Fix a problem step by step.", "/troubleshoot"], ["chat", "Ask the helper", "Questions about Zeus.", "/help#ask"]].map(function (t) {
          return h("a", { class: "tool-card", href: t[3] }, h("span", { class: "tc-i" }, icon(t[0])), h("span", { class: "tc-t" }, h("b", { text: t[1] }), h("small", { text: t[2] })), icon("arrow", "small"));
        }));
      Z.stagger(quick.children, 60);
      return h("div", { class: "stack" }, stats, quick, h("div", { class: "note-box" }, icon("info"), h("div", {}, h("b", { text: "Stored on this device only" }), h("p", { text: "This profile exists only in this browser. It is not an online account." }))));
    }

    function paint() {
      defs.forEach(function (d) { btns[d[0]].classList.toggle("on", d[0] === tab); btns[d[0]].setAttribute("aria-selected", d[0] === tab ? "true" : "false"); });
      clear(body);
      var v = { overview: sectionOverview, saved: sectionSaved, configs: sectionConfigs, log: sectionLog, settings: sectionSettings }[tab]();
      v.classList.add("swap-in");
      body.appendChild(v);
    }
    paint();
    var head = h("div", { class: "card prof-head" },
      h("span", { class: "av big", style: "--h:" + hue(u.id) }, u.name.charAt(0).toUpperCase()),
      h("div", { class: "prof-t" }, h("h2", { text: u.name }), h("p", { class: "muted", text: u.email }), h("p", { class: "faint", text: "Profile created " + when(u.created) + " · stored on this device only" })));
    return h("div", {}, head, tabs, body);
  }

  Z.pages.account = function () {
    var host = h("div", {});
    function paint() { clear(host); host.appendChild(current ? signedInView() : signedOutView()); }
    paint();
    var onAuth = function () { paint(); };
    document.addEventListener("zeus:auth", onAuth);
    Z.onLeave(function () { document.removeEventListener("zeus:auth", onAuth); });
    return h("div", { class: "wrap page" },
      Z.pageHead("Profile", current ? "Your profile" : "Sign in", current ? "Saved modules, your configs and your helper history. Everything stays in this browser." : "A profile keeps your saved modules and configs together. It lives only in this browser."),
      host);
  };

  /* ======================================================================= Build a config */
  function enabledMap(ids) {
    // zeus.json holds an "enabled" flag only where it differs from the module's default
    var set = {}, mods = {};
    ids.forEach(function (id) { set[id] = 1; });
    ((Z.features && Z.features.modules) || []).forEach(function (m) {
      var on = !!set[m.id], def = !!m.defaultOn;
      if (on !== def) mods[m.id] = { enabled: on };
    });
    return mods;
  }
  function buildJson(ids) {
    return JSON.stringify({ schema: 2, clientVersion: Z.site.version || "", modules: enabledMap(ids) }, null, 2) + "\n";
  }

  Z.pages.config = function () {
    var F = Z.features || { categories: [], modules: [] };
    var byId = {};
    F.modules.forEach(function (m) { byId[m.id] = m; });
    var picked = {}, presetId = "", presets = [];
    var grid = h("div", { class: "preset-grid" });
    var listBox = h("div", { class: "cfg-mods" });
    var summary = h("div", { class: "cfg-sum" });
    var nameIn = h("input", { type: "text", maxlength: "60", placeholder: "Name your config", "aria-label": "Config name", value: "My Zeus config" });
    var filterIn = h("input", { type: "search", placeholder: "Filter modules…", "aria-label": "Filter modules", autocomplete: "off", spellcheck: "false" });

    function ids() { return Object.keys(picked).filter(function (k) { return picked[k]; }); }
    function paintSummary() {
      var n = ids().length;
      summary.textContent = "";
      summary.appendChild(h("b", { text: String(n) }));
      summary.appendChild(document.createTextNode(n === 1 ? " module will be switched on" : " modules will be switched on"));
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
            var b = h("button", { type: "button", class: "cfg-chip" + (picked[m.id] ? " on" : ""), "aria-pressed": picked[m.id] ? "true" : "false", title: m.description || "" }, h("span", { class: "tick" }, icon("check", "small")), m.name);
            b.addEventListener("click", function () { picked[m.id] = !picked[m.id]; presetId = ""; paintPresets(); b.classList.toggle("on", !!picked[m.id]); b.setAttribute("aria-pressed", picked[m.id] ? "true" : "false"); paintSummary(); var cnt = det.querySelector(".cnt"); if (cnt) cnt.textContent = ms.filter(function (x) { return picked[x.id]; }).length + " / " + ms.length; });
            return b;
          })));
        listBox.appendChild(det);
      });
      if (!listBox.firstChild) listBox.appendChild(h("div", { class: "empty", text: "No module matches that." }));
    }
    function choose(p) {
      presetId = p ? p.id : "";
      picked = {};
      if (p) p.mods.forEach(function (id) { if (byId[id]) picked[id] = true; });
      if (p) nameIn.value = p.name;
      paintPresets(); paintMods(); paintSummary();
    }
    function paintPresets() {
      [].forEach.call(grid.children, function (c) { c.classList.toggle("on", c.getAttribute("data-id") === presetId); c.setAttribute("aria-pressed", c.getAttribute("data-id") === presetId ? "true" : "false"); });
    }
    filterIn.addEventListener("input", Z.debounce(paintMods, 90));

    var dlBtn = h("button", { type: "button", class: "btn btn-primary" }, icon("download"), h("span", { class: "btn-two" }, h("b", { text: "Download zeus.json" }), h("small", { text: "Switches these modules on at launch" })));
    dlBtn.addEventListener("click", function () {
      if (!ids().length) { Z.toast("Pick at least one module first", "bad"); return; }
      saveFile("zeus.json", buildJson(ids()), "application/json");
    });
    var cmdBtn = h("button", { type: "button", class: "btn btn-outline" }, icon("copy"), h("span", { class: "btn-two" }, h("b", { text: "Copy commands" }), h("small", { text: "One /zeus on line per module" })));
    cmdBtn.addEventListener("click", function () {
      if (!ids().length) { Z.toast("Pick at least one module first", "bad"); return; }
      copyText(ids().map(function (id) { return "/zeus on " + id; }).join("\n"), "Commands copied");
    });
    var saveBtn = h("button", { type: "button", class: "btn btn-outline" }, icon("star"), h("span", { class: "btn-two" }, h("b", { text: "Save to my profile" }), h("small", { text: current ? "Kept in this browser" : "Kept in this browser, sign in to tie it to a profile" })));
    saveBtn.addEventListener("click", function () {
      if (!ids().length) { Z.toast("Pick at least one module first", "bad"); return; }
      Z.configs.add({ name: nameIn.value.trim().slice(0, 60) || "My Zeus config", preset: presetId, created: Date.now(), mods: ids() });
      Z.toast("Saved. Find it on your profile page.");
    });

    var how = h("div", { class: "note-box" }, icon("info"), h("div", {},
      h("b", { text: "How to use the file" }),
      h("ol", { class: "mini-steps" },
        h("li", { text: "Close Minecraft and open .minecraft/config." }),
        h("li", { text: "Back up your current zeus.json, then put the downloaded one in its place. It only holds which modules are on, so every other setting goes back to its default." }),
        h("li", { text: "Start the game. The modules switch on once you are in a world (Remember enabled modules, in the Interface settings, must stay on, which it is by default)." })),
      h("p", { class: "faint", text: "Rather not replace the file? Copy the commands and paste them into chat one at a time. Profile share codes are not offered here because Zeus ignores on/off states inside share codes on purpose." })));

    var out = h("div", { class: "card cfg-out" }, h("h2", { class: "sec", text: "Your config" }), field("Name", nameIn), summary, h("div", { class: "cfg-btns" }, dlBtn, cmdBtn, saveBtn), how);

    var page = h("div", { class: "wrap page" },
      Z.pageHead("Build a config", "Build a config", "Pick a playstyle, adjust the module list and download a zeus.json that switches those modules on."),
      h("h2", { class: "sec", text: "1. Pick a playstyle" }), grid,
      h("h2", { class: "sec", style: "margin-top:34px", text: "2. Adjust the modules" }),
      h("div", { class: "fsearch sm", style: "margin-bottom:12px" }, icon("search"), filterIn), listBox,
      h("h2", { class: "sec", style: "margin-top:34px", text: "3. Take it with you" }), out);

    Z.getJson("/presets.json").then(function (list) {
      presets = list || [];
      presets.forEach(function (p, i) {
        var valid = p.mods.filter(function (id) { return byId[id]; }).length;
        var b = h("button", { type: "button", class: "preset", "data-id": p.id, "aria-pressed": "false" },
          h("span", { class: "pi" }, icon(p.icon || "bolt")), h("b", { text: p.name }), h("span", { class: "pb", text: p.blurb }), h("small", { text: valid + " modules" }));
        b.addEventListener("click", function () { choose(p); });
        b.style.setProperty("--d", i * 50 + "ms");
        grid.appendChild(b);
      });
      var blank = h("button", { type: "button", class: "preset", "data-id": "", "aria-pressed": "false" }, h("span", { class: "pi" }, icon("wand")), h("b", { text: "Start empty" }), h("span", { class: "pb", text: "Pick every module yourself." }), h("small", { text: "0 modules" }));
      blank.addEventListener("click", function () { choose(null); nameIn.value = "My Zeus config"; });
      grid.appendChild(blank);
      Z.stagger(grid.children, 45);
      Z.reveal(grid);
      var want = (/[?&]p=([a-z]+)/.exec(location.search) || [])[1];
      var pre = presets.filter(function (p) { return p.id === want; })[0];
      if (pre) choose(pre);
    }, function () { grid.appendChild(h("div", { class: "empty", text: "The presets could not be loaded." })); });
    paintMods(); paintSummary();
    return page;
  };

  /* ======================================================================= Troubleshooter */
  Z.pages.troubleshoot = function () {
    var host = h("div", { class: "ts" });
    var data = null, path = [], node = null;

    function report(res) {
      var lines = ["Zeus " + (Z.site.version || "?") + " on Minecraft " + Z.site.minecraft + " (Fabric)", "Problem: " + res.result, "Steps I went through:"];
      path.forEach(function (p) { lines.push("- " + p.q + " " + (p.yes ? "Yes" : "No")); });
      lines.push("", "What I already tried:", "", "latest.log attached: yes / no");
      return lines.join("\n");
    }
    function ask(id) {
      node = data.nodes[id];
      clear(host);
      var done = path.length;
      var dots = h("div", { class: "ts-dots", "aria-hidden": "true" });
      for (var i = 0; i < Math.max(4, done + 1); i++) dots.appendChild(h("i", { class: i < done ? "d" : i === done ? "n" : "" }));
      if (node.result) return result(node, dots);
      var yes = h("button", { type: "button", class: "btn btn-primary" }, icon("check"), "Yes");
      var no = h("button", { type: "button", class: "btn btn-outline" }, icon("close"), "No");
      yes.addEventListener("click", function () { path.push({ q: node.q, yes: true }); ask(node.yes); });
      no.addEventListener("click", function () { path.push({ q: node.q, yes: false }); ask(node.no); });
      var back = h("button", { type: "button", class: "btn btn-ghost sm", disabled: path.length ? null : "" }, icon("arrow", "small flip"), "Back");
      back.addEventListener("click", function () { var last = path.pop(); if (!last) return; for (var k in data.nodes) if (data.nodes[k].q === last.q) { ask(k); break; } });
      var card = h("div", { class: "card ts-card swap-in" }, h("span", { class: "pill", text: "Question " + (done + 1) }), h("h2", { text: node.q }), h("div", { class: "ts-a" }, yes, no), h("div", { class: "ts-f" }, back));
      host.appendChild(dots); host.appendChild(card);
      yes.focus({ preventScroll: true });
    }
    function result(res, dots) {
      var faqHref = "/help?faq=" + encodeURIComponent(res.faq) + "#faq";
      var logs = (res.log || []).length ? h("div", { class: "ts-log" }, h("span", { class: "tl-lab", text: "Search the log for" }), res.log.map(function (l) { return h("code", { text: l }); }),
        h("p", { class: "faint", text: "The log is the file latest.log in .minecraft/logs. Open it in a text editor and use Find." })) : null;
      var again = h("button", { type: "button", class: "btn btn-outline sm" }, icon("refresh", "small"), "Start again");
      again.addEventListener("click", function () { path = []; ask(data.start); });
      var rep = h("button", { type: "button", class: "btn btn-outline sm" }, icon("copy", "small"), "Copy a bug report");
      rep.addEventListener("click", function () { copyText(report(res), "Report copied. Paste it on Discord with your latest.log."); });
      var helper = h("button", { type: "button", class: "btn btn-outline sm" }, icon("chat", "small"), "Ask the helper");
      helper.addEventListener("click", function () { if (Z.helper) Z.helper.ask(res.ask || res.result); });
      var card = h("div", { class: "card ts-card ts-res swap-in" },
        h("span", { class: "pill ok", text: "Most likely cause" }), h("h2", { text: res.result }), h("p", { text: res.text }), logs,
        h("div", { class: "ts-a" }, h("a", { class: "btn btn-primary", href: faqHref }, icon("book"), "Read the FAQ answer"), helper),
        h("div", { class: "ts-f" }, again, rep, Z.site.links.discord ? h("a", { class: "btn btn-ghost sm", href: Z.site.links.discord, target: "_blank", rel: "noopener noreferrer" }, icon("discord", "small"), "Ask on Discord") : null));
      host.appendChild(dots); host.appendChild(card);
    }
    Z.getJson("/trouble.json").then(function (d) { data = d; ask(d.start); }, function () { host.appendChild(h("div", { class: "card" }, "The troubleshooter data could not be loaded.")); });
    return h("div", { class: "wrap page" }, Z.pageHead("Troubleshooter", "Fix a problem", "Answer a few yes or no questions. You end up at the most likely cause, the matching FAQ answer and the line to look for in your game log."), host);
  };

  /* ======================================================================= screenshots and clips on a module's drawer
     Files are listed in media.json:  { "modules": { "killaura": [ { "type": "image", "src": "/media/killaura-1.jpg", "caption": "…" },
     { "type": "video", "src": "/media/killaura.mp4", "poster": "/media/killaura.jpg", "caption": "…" } ] } }.  See media/README.txt. */
  function lightbox(item) {
    var root = h("div", { class: "lightbox", role: "dialog", "aria-modal": "true", "aria-label": item.caption || "Media" });
    var closeB = h("button", { type: "button", class: "dbtn", "aria-label": "Close" }, icon("close"));
    var node = item.type === "video" ?
      h("video", { src: item.src, controls: "", autoplay: "", loop: "", playsinline: "", poster: item.poster || null }) :
      h("img", { src: item.src, alt: item.caption || "" });
    function close() { document.removeEventListener("keydown", key, true); if (root.parentNode) root.parentNode.removeChild(root); }
    function key(e) { if (e.key === "Escape") { e.stopPropagation(); close(); } }
    root.addEventListener("click", function (e) { if (e.target === root || e.target === closeB) close(); });
    closeB.addEventListener("click", close);
    root.appendChild(h("figure", {}, node, item.caption ? h("figcaption", { text: item.caption }) : null));
    root.appendChild(closeB);
    document.body.appendChild(root);
    document.addEventListener("keydown", key, true);
    requestAnimationFrame(function () { root.classList.add("in"); });
  }
  function safePath(p) { return typeof p === "string" && /^\/media\/[A-Za-z0-9_./-]{1,100}$/.test(p) && p.indexOf("..") < 0; }
  Z.mediaBlock = function (m) {
    var list = ((Z.media || {})[m.id] || []).filter(function (x) { return x && (x.type === "image" || x.type === "video") && safePath(x.src) && (!x.poster || safePath(x.poster)); }).slice(0, 12);
    var box = h("div", { class: "dmedia" }, h("div", { class: "dsets-h" }, h("h3", { text: "Screenshots and clips" })));
    if (!list.length) {
      box.appendChild(h("div", { class: "mslots" }, [0, 1, 2].map(function (i) {
        return h("div", { class: "mslot" }, icon(i === 1 ? "play" : "image"), h("span", { text: i === 1 ? "Clip coming soon" : "Screenshot coming soon" }));
      })));
      return box;
    }
    box.appendChild(h("div", { class: "mgrid" }, list.map(function (x) {
      var thumb = x.type === "video" ?
        h("video", { src: x.src, muted: "", loop: "", playsinline: "", preload: "metadata", poster: x.poster || null, tabindex: "-1" }) :
        h("img", { src: x.src, alt: x.caption || "", loading: "lazy", decoding: "async" });
      var b = h("button", { type: "button", class: "mtile", "aria-label": (x.type === "video" ? "Play clip" : "Open screenshot") + (x.caption ? ": " + x.caption : "") }, thumb, x.type === "video" ? h("span", { class: "mplay" }, icon("playfill")) : null, x.caption ? h("small", { text: x.caption }) : null);
      if (x.type === "video" && !REDUCED) {
        b.addEventListener("pointerenter", function () { try { thumb.play(); } catch (e) { /* ignore */ } });
        b.addEventListener("pointerleave", function () { try { thumb.pause(); } catch (e) { /* ignore */ } });
      }
      b.addEventListener("click", function () { lightbox(x); });
      return b;
    })));
    return box;
  };

  /* ======================================================================= small motion helpers for the whole site */
  Z.ready.push(function () {
    // press ripple on buttons
    if (!REDUCED) {
      document.addEventListener("pointerdown", function (e) {
        var b = e.target.closest && e.target.closest(".btn, .preset, .tool-card, .faq-chip, .tab2");
        if (!b || b.disabled || b.getAttribute("aria-disabled") === "true") return;
        var r = b.getBoundingClientRect(), s = Math.max(r.width, r.height) * 1.6;
        var rip = h("span", { class: "ripple", style: "width:" + s + "px;height:" + s + "px;left:" + (e.clientX - r.left - s / 2) + "px;top:" + (e.clientY - r.top - s / 2) + "px" });
        b.classList.add("has-ripple");
        b.appendChild(rip);
        setTimeout(function () { if (rip.parentNode) rip.parentNode.removeChild(rip); }, 650);
      }, { passive: true });
      // gentle tilt on tool cards and presets
      document.addEventListener("pointermove", function (e) {
        var t = e.target.closest && e.target.closest(".preset, .tool-card");
        if (!t) return;
        var r = t.getBoundingClientRect();
        t.style.setProperty("--rx", (((e.clientY - r.top) / r.height - 0.5) * -5).toFixed(2) + "deg");
        t.style.setProperty("--ry", (((e.clientX - r.left) / r.width - 0.5) * 6).toFixed(2) + "deg");
      }, { passive: true });
      document.addEventListener("pointerout", function (e) {
        var t = e.target.closest && e.target.closest(".preset, .tool-card");
        if (t && !t.contains(e.relatedTarget)) { t.style.setProperty("--rx", "0deg"); t.style.setProperty("--ry", "0deg"); }
      }, { passive: true });
    }
  });
})();
