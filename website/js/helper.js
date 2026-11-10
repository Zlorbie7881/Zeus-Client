/* Zeus Helper: an offline question-answering assistant for the Zeus website.
 *
 * It is NOT a language model. It is a small search engine over this site's own content (FAQ, guides, the module
 * list, commands and the update timeline) plus some intent rules. Everything runs in the browser; nothing you type
 * is sent anywhere. If the answer is not written down on this site it says so instead of guessing.
 */
(function () {
  "use strict";
  var Z = window.Z;
  if (!Z) return;
  var h = Z.h, icon = Z.icon;

  /* ======================================================================= text processing */
  var STOP = {};
  ("a an the is are was were be been am do does did can could should would will shall may might i me my you your yours we our us it its this that these those to of in on for with and or but if so at by from as about " +
   "how what when where why which who whom there their they them please tell give show want need get gets got let lets also just really very much many some any tell me know wanna gonna ok okay hey hi hello thanks thank use using used one make makes made today now best recommend recommended suggest suggested").split(" ").forEach(function (w) { STOP[w] = 1; });

  var GROUPS = [
    "safe safety secure security trust trusted legit legitimate harmless dangerous risk risky sketchy scam fake trustworthy",
    "virus malware trojan rat malicious infect infected keylogger spyware stealer harmful antivirus",
    "ban banned bans punish punished suspend suspension blacklist kicked kick detect detected detection anticheat",
    "install installation installing setup",
    "mine mining mined miner miners dig digging tunnel tunnelling excavate",
    "uninstall remove removing deinstall",
    "crash crashes freeze freezes frozen hang hangs error exception broken fail fails failed",
    "lag laggy fps stutter stuttering slow performance frame frames",
    "key keybind keybinds bind binding hotkey shortcut",
    "free cost price paid pay premium subscription",
    "download downloads jar",
    "website site webpage dashboard web browser localhost",
    "fly flight flying hover",
    "mobile phone android ios console tablet",
    "update updating upgrade upgrading newest",
    "hud overlay widget widgets",
    "password login credential credentials",
    "telemetry track tracking collect logging analytics spy privacy",
    "module modules feature features hack hacks cheat cheats mod",
    "disable off deactivate stop",
    "enable on activate start",
    "language languages translation translate",
    "domain hosting host publish deploy online",
    "setting settings option options config configuration preference preferences",
    "slider value number amount",
    "default defaults original standard factory",
    "credit credits credited thanks author authors developer dev creator maker",
    "reference references inspiration inspired based copy copied clone",
    "colour color colours colors",
    "armor armour",
    "move moving drag dragging reposition relocate",
    "resize scale scaling bigger smaller",
    "find finder locate locating scan scanner scanning search searching hunt hunting",
  ];
  /* nicknames people use for modules: [phrase, module id] */
  var ALIAS_LIST = [
    ["ka","killaura"],["kill aura","killaura"],["aura bot","killaura"],["multi aura","multiaura"],["crystal","crystalaura"],["ca","crystalaura"],["anchor","anchoraura"],
    ["aimbot","aimassist"],["aim assist","aimassist"],["aim help","aimassist"],["autoclick","autoclicker"],["clicker","autoclicker"],["cps","autoclicker"],
    ["kb","antiknockback"],["antikb","antiknockback"],["anti kb","antiknockback"],["velocity","antiknockback"],["no knockback","antiknockback"],["hitbox","hitboxes"],
    ["totem","autototem"],["offhand totem","autototem"],["armor","autoarmor"],["armour","autoarmor"],["trap","autotrap"],["cobweb","autoweb"],["web trap","autoweb"],
    ["trigger bot","triggerbot"],["w tap","wtap"],["jump reset","jumpreset"],["mace","maceswap"],["combo","autocombo"],["pvp coach","swordpvpcoach"],["coach","swordpvpcoach"],
    ["fly","flight"],["flying","flight"],["hover","flight"],["sprint","autosprint"],["water walk","jesus"],["waterwalk","jesus"],["spiderman","spider"],["wall climb","spider"],
    ["bridge","scaffold"],["bridging","scaffold"],["tower","scaffold"],["gravity","lowgravity"],["moon jump","lowgravity"],["void","antivoid"],["safe walk","safewalk"],
    ["elytra autopilot","elytrawaypoint"],["autopilot","elytrawaypoint"],["elytra waypoint","elytrawaypoint"],["elytra bot","elytrawaypoint"],["elytra navigation","elytrawaypoint"],
    ["glide","elytraboost"],["boat fly","boatfly"],["boat clip","boatnoclip"],
    ["xray","xray"],["ore finder","xray"],["ore esp","xray"],["nuker","nuker"],["area clearer","nuker"],["clear area","nuker"],["fast mine","fastbreak"],["speed mine","fastbreak"],["fast use","fastplace"],
    ["night vision","fullbright"],["nightvision","fullbright"],["gamma","fullbright"],["brightness","fullbright"],["free look","freelook"],["free cam","freecam"],
    ["butterfly","knife"],["karambit","knife"],["player esp","esp"],["entity esp","esp"],["villager","villageresp"],["villagers","villageresp"],["nametag","nametags"],["name tag","nametags"],
    ["crosshair","customcrosshair"],["low health","lowhealth"],["time changer","timechanger"],["chunk border","chunkborders"],["chunk borders","chunkborders"],
    ["afk","antiafk"],["anti afk","antiafk"],["fishing","autofish"],["respawn","autorespawn"],["xp thrower","expthrower"],["exp bottle","expthrower"],["elytra swap","chestswap"],
    ["sus","suspicious"],["sus chunks","suspicious"],["sus chunk","suspicious"],["susp chunks","suspicious"],["suspicious chunk","suspicious"],["suspicious chunks","suspicious"],
    ["abf","basefinder"],["base finder","basefinder"],["advanced base finder","basefinder"],["mbf","mediabasefinder"],["media finder","mediabasefinder"],["media base","mediabasefinder"],["media bases","mediabasefinder"],["media base finder","mediabasefinder"],
    ["tbf","tunnelbasefinder"],["tunnel base","tunnelbasefinder"],["tunnel finder","tunnelbasefinder"],["chest esp","storageesp"],["chestesp","storageesp"],["stash esp","storageesp"],["container esp","storageesp"],
    ["cave finder","caveesp"],["cave esp","caveesp"],["caves","caveesp"],["torch finder","lightdetector"],["light finder","lightdetector"],["clues","baseclues"],
    ["spawner","spawneresp"],["spawners","spawneresp"],["amethyst","amethystesp"],["geode","amethystesp"],["geodes","amethystesp"],["kelp","kelpesp"],["crop","cropfinder"],["crops","cropfinder"],
    ["ancient city","ancientcityfinder"],["deep dark","ancientcityfinder"],["sculk","ancientcityfinder"],["trial chamber","trialfinder"],["trial chambers","trialfinder"],
    ["portal","portalfinder"],["portals","portalfinder"],["farm","redstonefinder"],["farms","redstonefinder"],["bed","bedfinder"],["beds","bedfinder"],["bee","beefinder"],["bees","beefinder"],
    ["vanish","antivanish"],["vanished","antivanish"],["staff","staffhud"],["admins","staffhud"],["moderators","staffhud"],["zeus brain","brain"],
    ["ah","auction_house_tweaks"],["auction","auction_house_tweaks"],["auction house","auction_house_tweaks"],["auction bot","auction_house_tweaks"],["market hunter","auction_house_tweaks"],
    ["sell","autosell"],["rtp","goliathrtp"],["goliath","goliathrtp"],["schematic","schematiclibrary"],["schematics","schematiclibrary"],["litematic","schematiclibrary"],["litematica","schematiclibrary"],
    ["builder","schematicbuilder"],["discord webhook","webhook"],["discord alerts","webhook"],["screenshot","screenshottweaks"],["screenshots","screenshottweaks"],
    ["friend","friends"],["friends list","friends"],["friend panel","friendspanel"],["waypoint","waypoints"],["replenish","autoreplenish"],["durability","durabilityalert"],
    ["dashboard","webdashboard"],["control center","webdashboard"],["control centre","webdashboard"],["web dashboard","webdashboard"],["array list","modulelist"],["arraylist","modulelist"],
    ["scoreboard","fakescoreboard"],["info hud","infohud"],["coords","infohud"],["coordinates","infohud"],["fps","infohud"],["armor hud","armorhud"],["armour hud","armorhud"],
    ["potion","potionhud"],["effects","potionhud"],["keystroke","keystrokes"],["key strokes","keystrokes"],["ping","netinfo"],["latency","netinfo"],["pops","popcounter"],["totem pops","popcounter"],
    ["goliath map","regionmap"],["copy coords","coordsnapper"],["macro","chatmacro1"],["macros","chatmacro1"],["chat macro","chatmacro1"],
    ["streamer mode","streamerprivacy"],["stream mode","streamerprivacy"],["streamer","streamerprivacy"],["no bad effects","nostatus"],
  ];
  var GENERIC = { info: 1, session: 1, step: 1, speed: 1, sneak: 1, radar: 1, compass: 1, morph: 1, reach: 1, resize: 1, friends: 1, crop: 1, crops: 1, bed: 1, beds: 1, bee: 1, bees: 1, farm: 1, farms: 1, portal: 1, portals: 1, sell: 1, builder: 1, macro: 1, macros: 1, mace: 1, combo: 1, void: 1, staff: 1, ping: 1, effects: 1, potion: 1, coach: 1, trap: 1, tower: 1, gravity: 1, crystal: 1, anchor: 1, armor: 1, armour: 1, auction: 1, schematic: 1, screenshot: 1, screenshots: 1, respawn: 1, glide: 1, hover: 1, fishing: 1, brightness: 1, bridge: 1 };
  var CANON = {};
  GROUPS.forEach(function (g) { var w = g.split(" "); w.forEach(function (x) { CANON[x] = w[0]; }); });

  function words(s) { return String(s).toLowerCase().replace(/[’'`]/g, "").replace(/[^a-z0-9]+/g, " ").trim().split(" ").filter(Boolean); }
  function stem(w) {
    if (w.length > 5 && /ies$/.test(w)) w = w.slice(0, -3) + "y";
    else if (w.length > 5 && /ing$/.test(w)) w = w.slice(0, -3);
    else if (w.length > 4 && /ed$/.test(w)) w = w.slice(0, -2);
    else if (w.length > 3 && /s$/.test(w) && !/ss$/.test(w)) w = w.slice(0, -1);
    return w;
  }
  function norm(w) { var s = CANON[w] || stem(w); return CANON[s] || s; }
  function terms(s, keepStop) {
    var out = [];
    words(s).forEach(function (w) { if (!keepStop && STOP[w]) return; out.push(norm(w)); });
    return out;
  }
  function lev(a, b, max) {
    if (Math.abs(a.length - b.length) > max) return max + 1;
    var prev = [], cur = [], i, j;
    for (j = 0; j <= b.length; j++) prev[j] = j;
    for (i = 1; i <= a.length; i++) {
      cur[0] = i;
      var rowMin = i;
      for (j = 1; j <= b.length; j++) {
        cur[j] = Math.min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
        if (cur[j] < rowMin) rowMin = cur[j];
      }
      if (rowMin > max) return max + 1;
      var t = prev; prev = cur; cur = t;
    }
    return prev[b.length];
  }

  /* ======================================================================= knowledge index */
  var K = null;

  function catLabel(id) {
    var cs = (Z.features && Z.features.categories) || [];
    for (var i = 0; i < cs.length; i++) if (cs[i].id === id) return cs[i].label;
    return id || "";
  }
  function sectionForCat(id) {
    var ss = (Z.help && Z.help.sections) || [];
    for (var i = 0; i < ss.length; i++) if (ss[i].category === id) return ss[i];
    return null;
  }
  function sectionById(id) {
    var ss = (Z.help && Z.help.sections) || [];
    for (var i = 0; i < ss.length; i++) if (ss[i].id === id) return ss[i];
    return null;
  }

  function uniqTerms(text) {
    var out = [];
    terms(text).forEach(function (t) { if (out.indexOf(t) < 0) out.push(t); });
    return out;
  }

  function build() {
    var docs = [];
    function addDoc(d, fields) {
      var tf = {}, len = 0;
      fields.forEach(function (f) {
        terms(f[0]).forEach(function (t) { tf[t] = (tf[t] || 0) + f[1]; len += f[1]; });
      });
      d.tf = tf; d.len = len || 1;
      d.titleTerms = terms(d.title || "");
      docs.push(d);
    }

    ((Z.help && Z.help.faq) || []).forEach(function (f, i) {
      addDoc({ type: "faq", title: f.q, faq: f, id: "faq" + i }, [[f.q, 4], [f.keys || "", 2], [f.a, 1]]);
    });
    ((Z.help && Z.help.sections) || []).forEach(function (s) {
      addDoc({ type: "section", title: s.title, sec: s, id: "sec" + s.id },
        [[s.title, 4], [s.intro || "", 2], [(s.steps || []).join(" "), 1], [(s.tips || []).join(" "), 1]]);
    });
    var mods = ((Z.features && Z.features.modules) || []).map(function (m) {
      var nameWords = words(m.name);
      return { m: m, nameWords: nameWords, joined: nameWords.join(""), cat: catLabel(m.category) };
    });
    mods.forEach(function (mm) {
      addDoc({ type: "module", title: mm.m.name, mod: mm, id: "mod" + mm.m.id },
        [[mm.m.name, 5], [(mm.m.aliases || []).join(" "), 4], [mm.m.description || "", 2], [mm.cat, 1]]);
    });
    var nSettings = 0;
    mods.forEach(function (mm) {
      mm.sets = [];
      (mm.m.settings || []).forEach(function (s, i) {
        var st = { mm: mm, s: s, i: i, nameTerms: uniqTerms(s.name) };
        mm.sets.push(st);
        if (s.type !== "info") nSettings++;
        addDoc({ type: "setting", title: s.name + " (" + mm.m.name + ")", set: st, id: "set" + mm.m.id + "_" + i }, [[s.name, 5], [s.desc || "", 2], [mm.m.name, 2]]);
      });
    });
    ((Z.content && Z.content.commands) || []).forEach(function (c, i) {
      addDoc({ type: "command", title: c.cmd, cmd: c, id: "cmd" + i }, [[c.cmd, 4], [c.text, 2]]);
    });
    (Z.timeline || []).forEach(function (u, i) {
      addDoc({ type: "release", title: "Version " + u.version + " " + u.title, rel: u, id: "rel" + i }, [["version " + u.version, 3], [u.title, 3], [u.text, 1]]);
    });

    var df = {}, total = 0;
    docs.forEach(function (d) { total += d.len; for (var t in d.tf) df[t] = (df[t] || 0) + 1; });
    var byId = {};
    mods.forEach(function (mm) { byId[mm.m.id] = mm; mm.nset = mm.sets.filter(function (x) { return x.s.type !== "info"; }).length; });
    var aliases = [];
    ALIAS_LIST.forEach(function (a) {
      var mm = byId[a[1]];
      if (mm) aliases.push({ w: words(a[0]), key: a[0].replace(/ /g, ""), mm: mm });
    });
    mods.forEach(function (mm) {
      (mm.m.aliases || []).forEach(function (a) { aliases.push({ w: words(a), key: words(a).join(""), mm: mm }); });
    });
    K = { docs: docs, df: df, N: docs.length, avg: total / Math.max(docs.length, 1), vocab: Object.keys(df), mods: mods, byId: byId, aliases: aliases, nSettings: nSettings };
    K.vocab.sort();
  }

  function correct(t) {
    if (K.df[t] || t.length < 5) return t;
    var max = t.length >= 8 ? 2 : 1, best = null, bd = 9, bdf = 0;
    for (var i = 0; i < K.vocab.length; i++) {
      var v = K.vocab[i];
      if (Math.abs(v.length - t.length) > max) continue;
      var d = lev(t, v, max);
      if (d <= max && (d < bd || (d === bd && K.df[v] > bdf))) { best = v; bd = d; bdf = K.df[v]; }
    }
    return best || t;
  }

  function search(query) {
    var qn = words(query).join(" ");
    var qt = terms(query).map(correct);
    var uniq = [];
    qt.forEach(function (t) { if (uniq.indexOf(t) < 0) uniq.push(t); });
    if (!uniq.length) return [];
    var k1 = 1.4, b = 0.6, res = [];
    K.docs.forEach(function (d) {
      var s = 0, hit = 0, titleHit = 0;
      uniq.forEach(function (t) {
        var tf = d.tf[t];
        if (!tf) return;
        hit++;
        if (d.titleTerms.indexOf(t) >= 0) titleHit++;
        var idf = Math.log(1 + (K.N - K.df[t] + 0.5) / (K.df[t] + 0.5));
        s += idf * (tf * (k1 + 1)) / (tf + k1 * (1 - b + b * d.len / K.avg));
      });
      if (!hit) return;
      var cover = hit / uniq.length;
      s *= 0.45 + 0.55 * cover;
      s += titleHit * 0.9;
      if (d.type !== "module" && d.type !== "setting") {
        var tset = {}, inter = 0, tcount = 0;
        d.titleTerms.forEach(function (t) { if (!tset[t]) { tset[t] = 1; tcount++; } });
        uniq.forEach(function (t) { if (tset[t]) inter++; });
        var jac = inter / Math.max(1, uniq.length + tcount - inter);
        s += jac * 5;
        var dn = words(d.title).join(" ");
        if (dn === qn) s += 40;
        else if (qn.length > 6 && (dn.indexOf(qn) >= 0 || qn.indexOf(dn) >= 0)) s += 3 + 8 * Math.min(qn.length, dn.length) / Math.max(qn.length, dn.length);
      }
      if (d.type === "faq") s *= 1.12;
      if (d.type === "module") s *= 0.92;
      if (d.type === "setting") s *= 0.8;
      res.push({ d: d, s: s, cover: cover, titleHit: titleHit });
    });
    res.sort(function (a, c) { return c.s - a.s; });
    return res;
  }

  /* find the modules the user mentions (tolerates "killaura", "kill arua", "x ray", nicknames like "ka" or "sus chunks") */
  function scanModules(query) {
    var ws = words(query), hits = [];
    if (!ws.length) return hits;
    var wide = ws.length <= 4 || /\b(modules?|huds?|widgets?|features?|settings?|options?|panel|switch|toggle|enable|disable|turn on|turn off|bind|keybind|key|default|slider|does|work|works|use|range|delay|mean|means|between|vs|versus|compare|difference|differences)\b/.test(query.toLowerCase());
    K.mods.forEach(function (mm) {
      var n = mm.nameWords;
      if (!n.length || mm.joined.length < 3) return;
      var generic = GENERIC[mm.joined] && !wide;
      if (!generic) {
        for (var i = 0; i + n.length <= ws.length; i++) {
          var ok = true;
          for (var j = 0; j < n.length; j++) if (stem(ws[i + j]) !== stem(n[j])) { ok = false; break; }
          if (ok) hits.push({ mm: mm, a: i, b: i + n.length - 1, len: mm.joined.length, exact: 1 });
        }
        for (var a = 0; a < ws.length; a++) {
          var acc = "";
          for (var c = a; c < Math.min(ws.length, a + 3); c++) {
            acc += ws[c];
            if (acc.length < 4) continue;
            var tol = mm.joined.length >= 9 ? 2 : mm.joined.length >= 6 ? 1 : 0;
            if (acc === mm.joined) hits.push({ mm: mm, a: a, b: c, len: mm.joined.length, exact: 1 });
            else if (tol && !K.df[norm(acc)] && lev(acc, mm.joined, tol) <= tol) hits.push({ mm: mm, a: a, b: c, len: mm.joined.length - 0.2 });
          }
        }
      }
    });
    K.aliases.forEach(function (al) {
      if (GENERIC[al.key] && !wide) return;
      for (var i = 0; i + al.w.length <= ws.length; i++) {
        var ok = true;
        for (var j = 0; j < al.w.length; j++) if (stem(ws[i + j]) !== stem(al.w[j])) { ok = false; break; }
        if (ok) hits.push({ mm: al.mm, a: i, b: i + al.w.length - 1, len: al.key.length - 0.5, exact: 1 });
      }
    });
    return hits;
  }
  function isReco(s) { return /\b(which|what) (module|modules|feature|features|one)\b|\b(the|which) best\b|\bbest (module|modules|feature|features|way|one|hack|cheat|for|to)\b|\brecommend\w*|\bshould i use\b|\bwhat (can|do) i use\b|\bis there (a|an|any)\b|\bdoes zeus have\b/.test(s); }
  function isCompare(s) { return /\b(vs|versus|compare|compared|comparison|difference|differences|differ|different|better)\b|\bor\b/.test(s); }
  function findModules(query) {
    var hits = scanModules(query);
    hits.sort(function (x, y) { return (y.exact || 0) - (x.exact || 0) || y.len - x.len || x.a - y.a; });
    var taken = [], out = [];
    hits.forEach(function (h0) {
      for (var i = 0; i < taken.length; i++) if (!(h0.b < taken[i].a || h0.a > taken[i].b)) return;
      if (out.some(function (o) { return o.mm === h0.mm; })) return;
      taken.push(h0); out.push(h0);
    });
    out.sort(function (x, y) { return x.a - y.a; });
    return out.map(function (o) { return o.mm; });
  }
  function findModule(query) {
    var hits = scanModules(query), best = null;
    hits.forEach(function (h0) { if (!best || (h0.exact || 0) > (best.exact || 0) || ((h0.exact || 0) === (best.exact || 0) && h0.len > best.len)) best = h0; });
    return best ? best.mm : null;
  }

  /* ---- settings of a module ---- */
  var SET_NOISE = {};
  ("setting settings option options value values slider toggle switch button default change changes changing adjust set sets tune configure config does mean means work works do number amount").split(" ").forEach(function (w) { SET_NOISE[norm(w)] = 1; });

  function num(n) { return String(Math.round(Number(n) * 1000) / 1000); }
  function shortText(t, n) { t = String(t || ""); return t.length > n ? t.slice(0, n - 1).replace(/\s+\S*$/, "") + "…" : t; }
  function describeSetting(s) {
    switch (s.type) {
      case "switch": return "an on/off switch" + (typeof s.default === "boolean" ? ", " + (s.default ? "on" : "off") + " by default" : "");
      case "slider": return "a slider from " + num(s.min) + " to " + num(s.max) + (s.step ? " (in steps of " + num(s.step) + ")" : "") + (s.default != null ? ", " + num(s.default) + " by default" : "");
      case "choice": return "a choice between " + (s.options || []).join(", ") + (s["default"] != null && s["default"] !== "" ? "; the default is " + s["default"] : "");
      case "button": return "a button that runs an action when you click it";
      case "text": return "a text field" + (s["default"] ? ", " + s["default"] + " by default" : "");
      case "pick": return "a picker you click to choose from a list";
      default: return "an information line";
    }
  }
  function defaultLabel(s) {
    if (s.type === "switch") return typeof s.default === "boolean" ? (s.default ? "on" : "off") : "";
    if (s.type === "slider") return s.default != null ? num(s.default) : "";
    if (s.type === "choice") return s["default"] || "";
    return "";
  }
  /* which setting of mm does the question mean? returns { best, ties } or null */
  function matchSetting(mm, query) {
    if (!mm || !mm.sets || !mm.sets.length) return null;
    var modTerms = {};
    mm.nameWords.forEach(function (w) { modTerms[norm(w)] = 1; });
    // 1) the full setting name appears in the question, word for word
    var padded = " " + words(query).join(" ") + " ", phrase = null;
    var modPhrase = " " + mm.nameWords.join(" ") + " ";
    var ql = query.toLowerCase();
    var settingWord = /\b(setting|settings|option|options|slider|default|toggle|switch)\b/.test(ql) && !/\b(settings|options)\s+(of|for|in)\b|\b(all|list|show|every)\b.*\b(settings|options)\b|\bwhat are\b.*\b(settings|options)\b/.test(ql);
    function occurrences(hay, needle) { var c = 0, i = hay.indexOf(needle); while (i >= 0) { c++; i = hay.indexOf(needle, i + 1); } return c; }
    mm.sets.forEach(function (st) {
      var nm = words(st.s.name).join(" ");
      if (!nm || padded.indexOf(" " + nm + " ") < 0) return;
      // "settings of Speed" names the module, not its Speed setting
      if (!settingWord && modPhrase.indexOf(" " + nm + " ") >= 0 && occurrences(padded, " " + nm + " ") <= Math.max(1, occurrences(padded, modPhrase))) return;
      if (!phrase || nm.length > phrase.nm.length) phrase = { st: st, nm: nm };
    });
    if (phrase) {
      var sames = mm.sets.filter(function (st) { return st !== phrase.st && words(st.s.name).join(" ") === phrase.nm; });
      return { best: phrase.st, ties: sames.slice(0, 3), cov: 1, hit: phrase.st.nameTerms.length, exact: true };
    }
    // 2) most of the words of a setting name
    var qt = [];
    terms(query).forEach(function (t) { if (!modTerms[t] && !SET_NOISE[t] && qt.indexOf(t) < 0) qt.push(t); });
    if (!qt.length) return null;
    var res = [];
    mm.sets.forEach(function (st) {
      if (!st.nameTerms.length) return;
      var hit = 0;
      st.nameTerms.forEach(function (t) { if (qt.indexOf(t) >= 0 || qt.some(function (q) { return q.length > 4 && t.length > 4 && lev(q, t, 1) <= 1; })) hit++; });
      if (!hit) return;
      var cov = hit / st.nameTerms.length;
      var extra = qt.filter(function (q) { return st.nameTerms.indexOf(q) < 0; }).length;
      if (cov < 0.6 && hit < 2) return;
      res.push({ st: st, cov: cov, hit: hit, extra: extra, len: st.nameTerms.length });
    });
    if (!res.length) return null;
    res.sort(function (a, b) { return b.cov - a.cov || b.hit - a.hit || a.extra - b.extra || a.len - b.len; });
    var top = res[0];
    var ties = res.slice(1).filter(function (r) { return r.cov >= top.cov - 0.01 && r.hit >= top.hit; }).map(function (r) { return r.st; });
    // a lone generic word that matches many settings is too vague to pick one
    if (top.hit === 1 && top.cov < 1 && ties.length > 2) return null;
    return { best: top.st, ties: ties.slice(0, 3), cov: top.cov, hit: top.hit };
  }

  /* ======================================================================= answers */
  /* ---- variety: pick one of several phrasings, never the one used last time for the same key ---- */
  var varMem = {}, forceVar = -1;
  function pickIdx(key, n) {
    if (n <= 1) return 0;
    if (forceVar >= 0) return forceVar % n;
    var last = varMem[key], i, guard = 0;
    do { i = Math.floor(Math.random() * n); } while (i === last && guard++ < 12);
    varMem[key] = i;
    return i;
  }
  function oneOf(key, list) { return list[pickIdx(key, list.length)]; }

  function A(p, o) { o = o || {}; return { p: p || [], ul: o.ul || [], ol: o.ol || [], links: o.links || [], chips: o.chips || [], src: o.src || "", mod: o.mod || null, extra: o.extra || "" }; }

  function discordLink() { return Z.site.links.discord ? { label: "Ask on Discord", href: Z.site.links.discord, ext: true } : null; }

  function modLink(mm, label) { return { label: label || "Open on the Features page", href: "/features?m=" + encodeURIComponent(mm.m.id) }; }
  function setNames(mm, n) {
    return (mm.sets || []).filter(function (x) { return x.s.type !== "info" && x.s.type !== "button"; }).slice(0, n).map(function (x) { return x.s.name; });
  }
  function listNames(a) { return a.length < 2 ? a.join("") : a.slice(0, -1).join(", ") + " and " + a[a.length - 1]; }

  function moduleAnswer(mm, intent) {
    var a = moduleAnswer0(mm, intent);
    a.vkey = "mod:" + mm.m.id + ":" + (intent || "what");
    a.vintent = intent || "what";
    return a;
  }
  function moduleAnswer0(mm, intent) {
    var m = mm.m, cat = mm.cat;
    var where = "Press Z in a world, open the " + cat + " category and find " + m.name + ".";
    var sec = sectionForCat(m.category);
    var links = [modLink(mm)];
    if (sec) links.push({ label: "Guide: " + sec.title, href: "/help#" + sec.id });
    var sibs = K.mods.filter(function (x) { return x.m.category === m.category && x.m.id !== m.id; });
    var sn = setNames(mm, 3);
    var chips = [];
    if (mm.nset) chips.push("What are the settings of " + m.name + "?");
    chips.push("How do I bind " + m.name + " to a key?", "Can " + m.name + " get me banned?");
    if (sibs.length) { var r = sibs[(m.name.length * 7 + sibs.length) % sibs.length]; chips.push("What does " + r.m.name + " do?"); }
    var head = m.name + " (" + cat + "): " + (m.description || "");
    var o = { links: links, chips: chips, src: "Module list", mod: mm };
    if (intent === "bind") return A([head, "To put " + m.name + " on a key:"], { ol: [where, "Click the row (not the switch) to open its settings.", "Click the key chip, which says None, and press the key you want. Backspace clears it."], links: links, chips: chips, src: "Module list", mod: mm });
    if (intent === "settings") {
      var ss = mm.sets || [];
      if (!mm.nset) return A([head, m.name + " has no settings. You only switch it on or off."], o);
      var shown = ss.filter(function (x) { return x.s.type !== "info"; });
      var more = shown.length - 12;
      return A([head, m.name + " has " + mm.nset + " setting" + (mm.nset === 1 ? "" : "s") + (more > 0 ? ". These are the first 12, and the rest are on its Features page:" : ":")], {
        ul: shown.slice(0, 12).map(function (x) { var d = defaultLabel(x.s); return x.s.name + (d ? " (" + d + ")" : "") + ": " + shortText(x.s.desc, 110); }),
        links: [modLink(mm, "All " + mm.nset + " settings")].concat(sec ? [{ label: "Guide: " + sec.title, href: "/help#" + sec.id }] : []),
        chips: shown.slice(0, 2).map(function (x) { return "What does " + x.s.name + " do in " + m.name + "?"; }).concat(["How do I bind " + m.name + " to a key?"]), src: "Settings list", mod: mm });
    }
    if (intent === "use") return A([head], { ol: [where, "Click its switch to turn it on. Click the rest of the row to tune its settings first if you like.", "Switch everything off at once with the Panic button or /zeus panic."], links: links, chips: chips, src: "Module list", mod: mm });
    if (intent === "safe") return A([head, m.name + " is a client-side cheat feature. Whether it is allowed is decided by the server you play on, and many servers detect or ban cheats. Read the rules first and use it only where it is permitted. Nothing in Zeus logs you in anywhere or sends your account data out."], { links: links.concat([{ label: "Is Zeus safe?", href: "/help#faq" }]), chips: ["Is Zeus safe?", "Will I get banned?"], src: "Module list", mod: mm });
    if (intent === "where") return A([m.name + " lives in the " + cat + " category of the Zeus panel.", where], { links: links, chips: chips, src: "Module list", mod: mm });
    var p = [head, where + " Click its switch to turn it on; click the row to open its settings."];
    if (sn.length) p.push(m.name + " has " + mm.nset + " setting" + (mm.nset === 1 ? "" : "s") + ", for example " + listNames(sn) + ".");
    if (m.hud) p.push("It has a HUD card, so you can move and resize it in the HUD editor (/zeus hud).");
    return A(p, o);
  }

  function settingAnswer(hit) {
    var st = hit.best, mm = st.mm, m = mm.m, s = st.s;
    var p = ["“" + s.name + "” in " + m.name + " (" + mm.cat + "): " + (s.desc || "No description is written for this setting."),
             "It is " + describeSetting(s) + "."];
    var ul = [];
    if (hit.ties && hit.ties.length) {
      p.push("Other settings of " + m.name + " with a similar name:");
      ul = hit.ties.map(function (t) { return t.s.name + ": " + shortText(t.s.desc, 100); });
    }
    return A(p, { ul: ul, ol: [], links: [{ label: "All settings of " + m.name, href: "/features?m=" + encodeURIComponent(m.id) + "&s=" + encodeURIComponent(s.name) }],
      chips: ["What are the settings of " + m.name + "?", "What does " + m.name + " do?"], src: "Settings list", mod: mm });
  }

  function faqAnswer(f, extraChips) {
    var links = (f.links || []).filter(function (l) { return /^\/[a-z]*(#[a-z-]+)?$/.test(l.href); }).map(function (l) { return { label: l.label, href: l.href }; });
    var a = A([f.a], { links: links, chips: extraChips || [], src: "FAQ" });
    a.faq = f;
    return a;
  }
  function sectionAnswer(s) {
    return A([s.intro || ""], { ol: (s.steps || []).slice(0, 6), links: [{ label: "Open the full guide", href: "/help#" + s.id }], src: "Guide: " + s.title,
      chips: (s.tips || []).length ? [] : [] });
  }

  var FALLBACK_TOPICS = ["How do I install Zeus?", "Is Zeus safe?", "How do I open the HUD editor?", "What does Kill Aura do?", "How do I use the website?"];

  /* ======================================================================= intents */
  function intents(q, ctx) {
    var s = q.toLowerCase().trim();
    var ws = words(s);
    var joined = ws.join(" ");

    if (/\b(weather forecast|forecast|recipe|pizza|joke|bitcoin|stock market|capital of|president of|lyrics|homework|translate this)\b|(what is|what'?s|how is) the weather|weather (today|tomorrow|outside|tonight)|^\s*[\d\s.]+[+\-*\/x][\d\s.]+=?\s*$/.test(s) && !/zeus|minecraft|module/.test(s))
      return A([oneOf("off", ["That's outside what I know. I only answer questions about Zeus Client and this website.", "I can't help with that one, I only know Zeus Client and this website. Ask me about a module, installing or staying safe.", "That's not something I can answer. Ask me about Zeus or this site instead."])], { chips: (Z.help.quick || []).slice(0, 4), src: "Helper" });
    if (/^(what is|whats|about|tell me about|explain|describe|what exactly is)?\s*(zeus|zeus client|this client|this mod|this)$/.test(s.replace(/[?!.']/g, "").trim()) && ws.length <= 6) {
      var wf = ((Z.help && Z.help.faq) || []).filter(function (f) { return f.q === "What is Zeus Client?"; })[0];
      if (wf) return faqAnswer(wf, ["Is Zeus safe?", "How do I install Zeus?", "Is Zeus a cheat client?"]);
    }
    if (/^(hi|hello|hey|yo|sup|hola|howdy|good (morning|evening|afternoon))\b/.test(joined) && ws.length <= 4)
      return A([oneOf("hi", ["Hi, I'm the Zeus helper. Ask me about installing Zeus, any of its " + (Z.site.moduleCount || "170+") + " modules, the HUD editor, the website, or whether it is safe.", "Hey, what do you want to know? I can explain any of the " + (Z.site.moduleCount || "170+") + " modules, walk you through the install or tell you how to stay safe.", "Hello. Ask me about Zeus: modules, settings, installing, the HUD or the safety side."])], { chips: (Z.help.quick || []).slice(0, 4), src: "Helper" });
    if (/^(thanks|thank you|thx|ty|cheers|great|awesome|nice|cool|perfect)\b/.test(joined) && ws.length <= 5)
      return A([oneOf("ty", ["You're welcome! Ask me anything else about Zeus.", "Good. Anything else?", "Any time. I'm here if you get stuck on something else."])], { src: "Helper", chips: (Z.help.quick || []).slice(0, 3) });
    if (/^(bye|goodbye|cya|see you|later)\b/.test(joined) && ws.length <= 4) return A([oneOf("bye", ["See you! The Help centre is always at the top right of the page.", "Bye! If you get stuck later, the Help button is at the top of every page."])], { src: "Helper" });
    if (/\b(stupid|useless|dumb|idiot|trash|shut up)\b/.test(s)) return A(["Sorry I wasn't more useful. Try asking it in different words, or use the FAQ search on the Help page. I only know what is written on this site."], { links: [{ label: "Open the FAQ", href: "/help#faq" }].concat(discordLink() || []), src: "Helper" });

    if (/(who|what) (are|r) you|are you (an? )?(ai|bot|human|real|robot|chatgpt|gpt|claude|llm|person)|your name|how do you work|are you smart|do you use ai|what ai|what model|powered by/.test(s))
      return A(["I'm the Zeus helper. I am not a language model and nothing you type leaves your browser. I'm a search engine over this site's own guides, FAQ, module list, commands and update timeline, with some rules for common questions.",
        "That means I can answer anything that is written down here, and I will tell you when I can't find something instead of making it up."], { chips: (Z.help.quick || []).slice(0, 4), src: "Helper" });

    if (/^(help|help me|can you help( me)?|how (can|do) you help( me)?|what can (i|you) (ask|do|help with)( you)?|what do you (know|do)|what (can|should) i ask( you)?|topics)$/.test(s.replace(/[?!.']/g, "").trim()))
      return A(["I can answer questions about:"], { ul: ["Installing and updating Zeus, the mods folder, Fabric and Java", "Every module, for example “What does Auto Totem do?” or “How do I bind Fly to a key?”",
        "The HUD editor, profiles, friends, waypoints, schematics and the Panic button", "The website and control center, the access token and publishing the site",
        "Safety, bans, privacy and what Zeus can and cannot do", "Troubleshooting crashes, lag, keys that do nothing and modules that seem broken"], chips: (Z.help.quick || []).slice(0, 5), src: "Helper" });

    var found = findModules(q), one = found[0] || null;

    if (!one && (/\b(build|make|create|generate|get|need|want)\b.*\b(config|configuration|pack|loadout|setup|preset)\b|\b(config|loadout|setup) (builder|wizard|maker|generator)\b|\bset ?me ?up\b|\bwhich modules (should|do|would) i (use|turn on|enable|pick|choose)\b|\bwhat (should|do) i (turn on|enable|switch on)\b|\b(best|good|recommended) (config|setup|loadout)\b/.test(s)))
      return A([oneOf("cfg", ["The Build a config page makes a module pack for you. Answer a few yes or no questions (do you fight other players, find bases, mine, stream, play on a weak PC) or start from a playstyle, and it lists the modules with a reason for each one.", "Use Build a config. It asks a handful of yes or no questions about how you play and builds a pack to match. You can also start from a playstyle like Explorer or Crystal PvP and adjust it."]),
        "You get a zeus.json to import and the /zeus commands to switch the pack on, so nothing has to be clicked one by one."], { links: [{ label: "Build a config", href: "/config" }, { label: "Browse all modules", href: "/features" }], src: "Build a config", chips: ["What is a playstyle?", "How do I import a config?", "How do I open the Zeus menu?"] });

    if (/\b(credits?|credited|special thanks|references?|inspir\w*|based on|built on|made by|who (made|built|created|developed|wrote|did)|developers?|authors?|creators?)\b/.test(s) && !one && /\b(zeus|client|website|site|mod|this|credits?|references?|made|who|inspir\w*|based|built)\b/.test(s) && !/\bwho (is|are)\b.*\b(mitko|niki|zlorbie)/.test(s) && !/\b(mitko|niki|zlorbie|pandata|facter|meteor|litematica|fabric)\b/.test(s)) {
      var cr = (Z.content && Z.content.credits) || [], rf = (Z.content && Z.content.references) || [];
      if (rf.length && /\b(references?|inspir\w*|based on|built on|used|uses)\b/.test(s))
        return A(["Zeus credits the projects it used as references or builds on:"], { ul: rf.map(function (r) { return r.name + " – " + r.kind + ": " + r.text; }), links: [{ label: "Credits page", href: "/credits" }], src: "Credits", chips: ["Is Zeus a copy of Meteor Client?", "Who made Zeus?"] });
      if (cr.length) return A(["The people behind Zeus Client:" ], { ul: cr.filter(function (c) { return c.tag; }).map(function (c) { return c.name + " – " + c.tag; }), links: [{ label: "Credits page", href: "/credits" }], src: "Credits", chips: ["What did Zeus use as references?", "Is Zeus a copy of Meteor Client?"], extra: rf.length ? "References and inspiration: " + rf.map(function (r) { return r.name + " (" + r.kind.toLowerCase() + ")"; }).join(", ") + "." : "" });
    }

    var cm = /how many (settings|options|sliders|toggles|switches)/.exec(s);
    if (cm) {
      if (one) {
        var ns = one.nset;
        return A([one.m.name + " has " + ns + " setting" + (ns === 1 ? "" : "s") + (ns ? ", for example " + listNames(setNames(one, 3)) + "." : ". You only switch it on or off.")], { links: [modLink(one, "All settings of " + one.m.name)], src: "Settings list", mod: one, chips: ns ? ["What are the settings of " + one.m.name + "?"] : [] });
      }
      var withSet = K.mods.filter(function (mm) { return mm.nset; }).length;
      return A(["Zeus has " + K.nSettings + " settings across " + withSet + " modules. Every one has a short explanation, and you can read them all on the Features page."], { links: [{ label: "Browse modules and settings", href: "/features" }], src: "Settings list", chips: ["Which module has the most settings?"] });
    }
    if (/(which|what).*(module|feature).*(most|more) (settings|options)|(most|more) (settings|options)/.test(s) && !one) {
      var top5 = K.mods.slice().sort(function (a, b) { return b.nset - a.nset; }).slice(0, 5);
      return A(["The modules with the most settings are:"], { ul: top5.map(function (mm) { return mm.m.name + ": " + mm.nset + " settings"; }), links: [{ label: "Browse modules", href: "/features" }], src: "Settings list" });
    }

    /* HUD editor questions about one module or about all of them */
    if (/\b(hud editor|movable|move|moved|drag|reposition|resize|rescale|scale|widget|widgets|hud card|card)\b/.test(s) && /\b(hud|widget|widgets|editor|card|move|drag|movable|panel)\b/.test(s)) {
      if (one && /\b(move|moved|drag|reposition|resize|scale|position|place|hud|widget|card|editor|show|screen)\b/.test(s)) {
        var hl = [{ label: "HUD editor guide", href: "/help#hud" }, modLink(one)];
        if (one.m.hud) return A([one.m.name + " has a HUD card, so you can move and resize it. Switch it on first, then:"], { ol: ["Type /zeus hud to open the HUD editor.", "Click the outline of " + one.m.name + " and drag it where you want it. It snaps to edges, centre lines and other widgets.", "Use the mouse wheel to scale it, the arrow keys to nudge it, R to reset it and U to undo."], links: hl, src: "HUD editor", mod: one, chips: ["Which HUD widgets can I move?", "How do I use the HUD editor?"] });
        return A([one.m.name + " does not draw a panel on your screen, so there is nothing to move in the HUD editor. You can open its settings from the panel instead."], { links: hl, src: "HUD editor", mod: one, chips: ["Which HUD widgets can I move?"] });
      }
      if (!one && /\b(which|what|list|all|show|available|can i)\b/.test(s) && /\b(movable|move|moved|drag|editor|editable|appear|available|resize|reposition|scale)\b/.test(s)) {
        var hm = Z.features.modules.filter(function (m) { return m.hud; });
        return A(["These " + hm.length + " widgets can be moved and scaled in the HUD editor (/zeus hud). Each one only shows while its module is switched on:"], { ul: [hm.map(function (m) { return m.name; }).join(", ")], links: [{ label: "HUD editor guide", href: "/help#hud" }], src: "HUD editor", chips: ["How do I use the HUD editor?", "Can I move the Media Base Finder HUD?"] });
      }
    }

    /* two or more modules in one question: explain each, and compare when asked to */
    if (found.length >= 2 && words(s).length <= 18 && !/\bbind\b/.test(s)) {
      var cmp = isCompare(s) || /\b(best|which)\b/.test(s);
      var pick = found.slice(0, 3);
      var out = pick.map(function (mm) { return mm.m.name + " (" + mm.cat + "): " + (mm.m.description || "") + (mm.nset ? " It has " + mm.nset + " setting" + (mm.nset === 1 ? "" : "s") + "." : ""); });
      if (cmp) {
        var cats = pick.map(function (mm) { return mm.cat; });
        out.push(cats.every(function (c) { return c === cats[0]; }) ? "Both are in the " + cats[0] + " category, so they can run together; check each one's settings so they do not fight over the same action." : pick.map(function (mm) { return mm.m.name + " is in " + mm.cat; }).join(", ") + ".");
      }
      return A(out, { links: pick.map(function (mm) { return modLink(mm, mm.m.name + " settings"); }), src: "Module list", mod: pick[0], chips: pick.slice(0, 2).map(function (mm) { return "How do I bind " + mm.m.name + " to a key?"; }) });
    }

    /* "does Zeus have ...?" / "which module should I use for ...?" */
    if ((!one || isReco(s)) && (/\b(does|do|will|can)\b.*\b(zeus|it|this client|the client)\b.*\b(have|has|include|includes|support|offer|got|feature|come with)\b|^(can|could|does|do|will) (zeus|it|this client|the client|the mod) \w+|\bis there (a|an|any)\b|\b(best|recommend|which module|what module|what should i use|what can i use|which one|which feature)\b/.test(s))) {
      if (!K) build();
      var rank = search(s.replace(/\b(does|do|zeus|have|has|include|support|offer|there|module|modules|feature|features)\b/g, " "));
      var rawTop = search(s)[0];
      var faqWins = rawTop && rawTop.d.type === "faq" && rawTop.s > 6;
      var mods2 = rank.filter(function (r) { return r.d.type === "module" && r.s > 3.2; }).slice(0, 4);
      var sets2 = rank.filter(function (r) { return r.d.type === "setting" && r.s > 5; }).slice(0, 3);
      if (mods2.length && !faqWins) {
        var yes = /\b(does|do|will|can|is there)\b/.test(s);
        return A([yes ? "Yes, Zeus has " + (mods2.length > 1 ? "modules for that. The closest ones are:" : "a module for that:") : "I would look at:"], { ul: mods2.map(function (r) { return r.d.mod.m.name + " (" + r.d.mod.cat + "): " + r.d.mod.m.description; }).concat(sets2.length ? ["There are also settings for it: " + sets2.map(function (r) { return r.d.set.s.name + " in " + r.d.set.mm.m.name; }).join(", ")] : []),
          links: [modLink(mods2[0].d.mod, "Open " + mods2[0].d.mod.m.name)], src: "Module list", mod: mods2[0].d.mod, chips: ["What does " + mods2[0].d.mod.m.name + " do?", "How do I bind " + mods2[0].d.mod.m.name + " to a key?"] });
      }
      if (!mods2.length && !faqWins && !sets2.length && /\b(does|do|is there)\b/.test(s) && /\b(have|has|include|support|offer|there)\b/.test(s) && (!rank[0] || rank[0].s < 3)) {
        var unk = words(s).filter(function (w) { return !STOP[w] && !/^(zeus|module|modules|feature|features|have|has|include|support|offer|there|client)$/.test(w); }).slice(0, 3).join(" ");
        return A(["I could not find " + (unk ? "“" + unk + "”" : "that") + " among Zeus's " + (Z.site.moduleCount || K.mods.length) + " modules or their " + K.nSettings + " settings, so it probably does not exist (yet). If you have an idea for one, the Discord is the place to suggest it."], { links: [{ label: "Browse all modules", href: "/features" }].concat(discordLink() || []), chips: (Z.help.quick || []).slice(0, 3), src: "Module list" });
      }
    }

    if (/how many\b.*\b(hud|widget)s?\b|\b(hud|widget)s? (count|total)/.test(s)) {
      var hn = Z.features.modules.filter(function (m) { return m.hud; });
      return A(["There are " + hn.length + " HUD widgets you can move and scale in the HUD editor."], { links: [{ label: "HUD editor guide", href: "/help#hud" }], src: "HUD editor", chips: ["Which HUD widgets can I move?", "How do I use the HUD editor?"] });
    }
    var cc = /(how many|number of|count)/.test(s) ? (Z.features.categories || []).filter(function (c) { return new RegExp("\\b" + c.label.toLowerCase().replace(/[^a-z ]/g, "").replace(/ /g, "\\W+") + "\\b").test(s); }) : [];
    if (cc.length === 1 && /module|feature|hack|cheat|\bare there\b/.test(s) && !one) {
      var cn = Z.features.modules.filter(function (m) { return m.category === cc[0].id; }).length;
      return A(["The " + cc[0].label + " category has " + cn + " modules."], { links: [{ label: "Browse " + cc[0].label, href: "/features?c=" + encodeURIComponent(cc[0].id) }], src: "Module list", chips: ["List the " + cc[0].label + " modules"] });
    }
    if (/(how many|number of|count).*(module|feature)|(module|feature)s? (count|total)/.test(s))
      return A(["Zeus has " + (Z.site.moduleCount || "about 170") + " modules in " + ((Z.features.categories || []).length) + " categories. Every one is configurable in-game."], { links: [{ label: "Browse all modules", href: "/features" }], src: "Module list", chips: ["What are the categories?", "Which modules are for finding bases?"] });
    if (/(what|which|list).*(categor)|categories/.test(s)) {
      var cs = (Z.features.categories || []).map(function (c) { return c.label + " (" + Z.features.modules.filter(function (m) { return m.category === c.id; }).length + ")"; });
      return A(["The Zeus panel groups modules into these categories:"], { ul: cs, links: [{ label: "Browse by category", href: "/features" }], src: "Module list" });
    }
    var catHit = null;
    if (/\b(modules?|features?|hacks?|cheats?|everything|list|show|have|has|all)\b/.test(s) || (/\bbases?\b|stash/.test(s) && /(find|scan|search|locat|hunt|detect)/.test(s)) || /\bscanners?\b/.test(s)) {
      (Z.features.categories || []).forEach(function (c) {
        var l = c.label.toLowerCase();
        var re = new RegExp("\\b" + l.replace(/[^a-z ]/g, "").replace(/ /g, "\\W+") + "\\b");
        if (re.test(s) && (!catHit || l.length > catHit.label.length)) catHit = c;
      });
      if (!catHit && /\bscanners?\b/.test(s)) catHit = (Z.features.categories || []).filter(function (c) { return c.id === "SCANNER"; })[0] || null;
      if (!catHit && /\bbases?\b|stash/.test(s) && /(find|scan|search|locat|hunt|detect)/.test(s)) catHit = (Z.features.categories || []).filter(function (c) { return c.id === "SCANNER"; })[0] || null;
      if (/\bvisual|render|esp\b/.test(s) && !catHit) catHit = (Z.features.categories || []).filter(function (c) { return c.id === "RENDER"; })[0] || null;
    }
    if (catHit && /\b(modules?|features?|list|show|which|all|scanners?|hacks?|cheats?)\b/.test(s) && !findModule(s)) {
      var list = Z.features.modules.filter(function (m) { return m.category === catHit.id; });
      var sec = sectionForCat(catHit.id);
      return A([catHit.label + " has " + list.length + " modules:"], { ul: list.slice(0, 16).map(function (m) { return m.name + ": " + m.description; }).concat(list.length > 16 ? ["…and " + (list.length - 16) + " more on the Features page."] : []),
        links: (sec ? [{ label: "Guide: " + sec.title, href: "/help#" + sec.id }] : []).concat([{ label: "Browse all modules", href: "/features" }]), src: "Module list" });
    }

    if (/(helper|chat ?bot|assistant|bot|this chat|my (messages|questions))/.test(s) && /(send|store|log|save|record|see|privacy|private|data|track|share|leave|upload)/.test(s) || (/\b(site|website|page)\b/.test(s) && /(track|cookie|cookies|analytic|analytics|spy|spies|collect|privacy|telemetry)/.test(s)))
      return A(["This website runs entirely in your browser. It loads its fonts and images from itself and includes no analytics or advertising scripts.",
        "The helper does not call any server or AI service: your questions are answered by code in this page and are never sent anywhere. The conversation is kept in your browser tab only (it disappears when you close the tab or press the restart button).",
        "If you connect the control center, the access token is stored in your browser's local storage until you press Disconnect. Whoever hosts this site can still see ordinary web-server logs, as with any website."], { links: [{ label: "Is Zeus safe?", href: "/help#faq" }], src: "Site", chips: ["Does Zeus collect data or have telemetry?", "Is the control center safe?"] });
    if (/^(the |your )?(discord|youtube|twitter|x|socials?|community|invite|server invite|discord (link|server|invite)|join (the )?discord|youtube (channel|link))$/.test(joined) || /(discord|youtube|twitter).*(link|channel|server|invite|where)|(where|link).*(discord|youtube|twitter)/.test(s) && !/webhook/.test(s)) {
      var sl = Z.site.links, ln = [];
      if (sl.discord) ln.push({ label: "Discord", href: sl.discord, ext: true });
      if (sl.youtube) ln.push({ label: "YouTube", href: sl.youtube, ext: true });
      if (sl.twitter) ln.push({ label: "X / Twitter", href: sl.twitter, ext: true });
      return A([sl.discord && /discord/.test(s) ? "The Zeus Discord invite is " + sl.discord + "." : ln.length ? "Here are the official links this site has been configured with." : "No community links have been published on this site yet."], { links: ln.length ? ln : [{ label: "Help centre", href: "/help" }], src: "Site" });
    }
    if (/\b(modules?|features?)\b/.test(s) && /\b(for|help|helps|helping|with|that|which|related|about|good|best)\b/.test(s) && !findModule(s) && !/\bhow many\b/.test(s)) {
      if (!K) build();
      var mr = search(s.replace(/\b(modules?|features?)\b/g, "")).filter(function (r) { return r.d.type === "module" && r.s > 2; }).slice(0, 6);
      if (mr.length) return A(["These modules match:"], { ul: mr.map(function (r) { return r.d.mod.m.name + " (" + r.d.mod.cat + "): " + r.d.mod.m.description; }), links: [{ label: "Browse all modules", href: "/features" }], src: "Module list", chips: [mr[0].d.mod.m.name.length ? "What does " + mr[0].d.mod.m.name + " do?" : ""] });
    }
    if (/(current|latest|newest|which|what).{0,20}version|version number|what version are you/.test(s) && !/minecraft|java|fabric|new\b|changes?|update/.test(s))
      return A(["The current Zeus version is " + (Z.site.version ? "v" + Z.site.version : "shown on the Information page") + ", built for Minecraft " + Z.site.minecraft + " on Fabric."], { links: [{ label: "Update timeline", href: "/information#timeline" }], src: "Timeline", chips: ["How do I update Zeus?"] });
    if (/(what'?s|whats) new|latest update|newest update|changelog|change log|recent (update|change|release)|release notes|patch notes|new in/.test(s) && Z.timeline.length) {
      var u = Z.timeline[0];
      return A(["v" + u.version + " – " + u.title + ": " + u.text], { links: [{ label: "Full update timeline", href: "/information#timeline" }], src: "Timeline" });
    }
    var vm = /\b(\d+\.\d+(?:\.\d+)?)\b/.exec(s);
    if (vm && !/minecraft|java|fabric|^1\.2\d|1\.21/.test(s) && !/^(1\.2\d|21)/.test(vm[1]) && Z.timeline.length) {
      var want = vm[1], exact = [], near = [];
      Z.timeline.forEach(function (t) {
        if (t.version === want || t.version === want + ".0") exact.push(t);
        else if (t.version.indexOf(want + ".") === 0 || want.indexOf(t.version.split(".").slice(0, 2).join(".")) === 0) near.push(t);
      });
      var hit = exact.length ? exact : near;
      if (hit.length) return A(hit.slice(0, 3).map(function (t) { return "v" + t.version + " – " + t.title + ": " + t.text; }), { links: [{ label: "Update timeline", href: "/information#timeline" }], src: "Timeline" });
      return A(["There is no v" + want + " in the timeline. The versions listed run from v" + Z.timeline[Z.timeline.length - 1].version + " to v" + Z.timeline[0].version + "."], { links: [{ label: "Update timeline", href: "/information#timeline" }], src: "Timeline" });
    }
    if (/(first|oldest|earliest|began|started|beginning|history of).*(version|release|zeus)|when (was|did) zeus (made|start|begin|come out|release)/.test(s) && Z.timeline.length) {
      var f = Z.timeline[Z.timeline.length - 1];
      return A(["The first entry in the timeline is v" + f.version + " – " + f.title + ": " + f.text], { links: [{ label: "Update timeline", href: "/information#timeline" }], src: "Timeline" });
    }
    if (!one && /\b(turn|switch|toggle|shut|disable|stop)\b/.test(s) && /\boff\b/.test(s) && /\b(module|modules|hack|hacks|feature|features|it|zeus|everything|all)\b/.test(s) && !/\b(hud|widget|panel|website|site|control)\b/.test(s))
      return A([oneOf("off1", ["Click the module's switch again to turn it off. To stop everything at once press the Panic button or type /zeus panic.", "Flip its switch off in the panel (Z). One command does it too: /zeus off followed by the module id, and /zeus panic switches every module off together."])],
        { links: [{ label: "Commands", href: "/tutorial" }], src: "Guide", chips: ["What is the panic button?", "How do I turn a module on?", "How do I see which modules are on?"] });
    if (!one && /\b(hide|remove|disable|turn off|switch off|get rid of|stop showing|take away)\b/.test(s) && /\b(hud|widgets?|overlay)\b/.test(s)) {
      var hh = exactFaq("How do I hide a HUD widget?");
      if (hh) return faqAnswer(hh, ["How do I use the HUD editor?", "Why can't I see a widget in the HUD editor?"]);
    }
    if (!one && /\b(move|moving|drag|reposition|position|place|rearrange|arrange)\b/.test(s) && /\b(hud|widgets?|panels?|overlay)\b/.test(s) && !/\b(media|suspicious|freecam|vanish)\b/.test(s)) {
      var hf = exactFaq("How do I use the HUD editor?");
      if (hf) return faqAnswer(hf, ["How do I resize a HUD widget?", "Do my HUD positions save?", "How do I reset the HUD layout?"]);
    }
    if (!one && /\b(colou?rs?|theme|themes|skin|appearance|look|style|dark|light)\b/.test(s) && /\b(menu|panel|zeus|ui|interface|gui|client)\b/.test(s)) {
      var cf = exactFaq("How do I change the colour of the panel?");
      if (cf) return faqAnswer(cf, ["How do I open the HUD editor?", "What is the Interface category?"].slice(0, 1));
    }
    if (!one && /\bprofiles?\b/.test(s) && /\b(switch|change|load|save|create|make|new|use|swap|copy|duplicate|delete)\b/.test(s) && !/\b(server|website|site|share|code|account)\b/.test(s)) {
      var pf = exactFaq("What are profiles?");
      if (pf) return faqAnswer(pf, ["Can Zeus switch profile by itself on a server?", "How do I share my config?"]);
    }
    if (!one && /(what'?s|whats|what is|anything) new\b|\blatest (update|version|release|changes?)\b|\bnew (in|features?|modules?) (the )?(latest|newest|current|update)\b|\brecent (update|changes?)\b|\bwhat changed\b(?!.*\d)/.test(s) && Z.timeline.length) {
      var nw = Z.timeline[0];
      return A(["The latest release is v" + nw.version + " – " + nw.title + ". " + nw.text], { links: [{ label: "Full timeline", href: "/information#timeline" }].concat((nw.mods || []).slice(0, 2).map(function (id) { var m = ((Z.features && Z.features.modules) || []).filter(function (x) { return x.id === id; })[0]; return m ? { label: m.name, href: "/features?m=" + encodeURIComponent(id) } : null; }).filter(Boolean)), src: "Timeline", chips: ["What was added in each version?", "Is Zeus safe?"] });
    }
    if (/\b(verify|verif\w*|checksum|hash|genuine|authentic|tamper\w*|sha ?-?256)\b|\bcheck (the |my |that )?(download\w*|jar|file)\b/.test(s) && !one) {
      var vf = exactFaq("How do I check that my download is genuine?");
      if (vf) return faqAnswer(vf, ["Where can I download Zeus from?", "Can I download Zeus from GitHub instead?"]);
    }
    if (/\bgit ?hub\b/.test(s) && /\b(link|url|where|page|repo|repository|address|site)\b/.test(s) && !/\b(download\w*|safe|trust|install|from|release\w*)\b/.test(s)) {
      var gu = Z.site.links.github;
      return A([gu ? oneOf("ghl", ["The project's GitHub page is " + gu + ". The Releases tab has the same jar as the Download page, and the source is there too.", "Here is the GitHub page: " + gu + ". You can download the jar from its Releases tab or read and build the source yourself."]) : "The GitHub link has not been added to this site yet. Until it is, the Source page shows every file of this release so you can read it, and the Download page has the install steps."],
        { links: gu ? [{ label: "Open GitHub", href: gu, ext: true }, { label: "Download page", href: "/download#github" }] : [{ label: "Source code", href: "/source" }, { label: "Download page", href: "/download" }], src: "Site", chips: ["Can I download Zeus from GitHub instead?", "How do I check that my download is genuine?"] });
    }
    if (/\b(download\w*|link|button)\b/.test(s) && /(not work|does ?n.?t work|broken|dead|404|error|fail\w*|won.?t|can.?t|cannot|nothing happens|empty|blank)/.test(s) && !one && !/\b(menu|module|crash|game|minecraft)\b/.test(s)) {
      var gh2 = Z.site.links.github, dc2 = discordLink();
      return A([oneOf("dlx", ["If the download link does not work, try these in order:", "A download that fails usually has a simple cause. Try these:"])],
        { ol: ["Reload the Download page and click the button again, or try another browser.", gh2 ? "Get the same jar from the project's GitHub Releases page instead." : "Check the Download page again later, since the link may be updated.", "Check the SHA-256 on the Download page after you get the file, to be sure it is the real jar.", "Still stuck? Ask on Discord" + (dc2 ? "." : " when the invite is available.")],
          links: [{ label: "Download page", href: "/download" }].concat(gh2 ? [{ label: "GitHub", href: gh2, ext: true }] : []).concat(dc2 ? [dc2] : []), src: "Site", chips: ["Is Zeus safe to download?", "How do I install Zeus?"] });
    }
    if (/\b(is|are)\b.*\b(you|helper|bot|chat ?bot|assistant|this chat|this)\b.*\b(ai|artificial|human|real|robot|chatgpt|gpt|llm|person)\b/.test(s) && !/\b(zeus|module|mod|client|site|website)\b.*\b(ai)\b/.test(s) && ws.length <= 9)
      return A(["I'm the Zeus helper. I am not a language model and nothing you type leaves your browser. I'm a search engine over this site's own guides, FAQ, module list, commands and update timeline, with some rules for common questions.", "That means I can answer anything that is written down here, and I will tell you when I can't find something instead of making it up."], { chips: (Z.help.quick || []).slice(0, 4), src: "Helper" });
    if ((!one || /^(info|infohud|netinfo|session)$/.test(one.m.id)) && /\b(fps|framerate|frame rate|lag|laggy|stutter\w*|freez\w*|performance|slow(ing)? down)\b/.test(s) && !/\b(server|ping|tps|rubber|fake)\b/.test(s)) {
      var lf = exactFaq(/\b(fps|framerate|frame rate)\b/.test(s) && /\b(low|drop\w*|bad|poor|tank\w*|slow\w*|less|improve|boost|increase|better|fix)\b/.test(s) ? "My FPS dropped after enabling a module" : "Does Zeus lag my game?");
      if (lf) return faqAnswer(lf, ["Does Zeus lag my game?", "My FPS dropped after enabling a module", "What is TPS, ping and FPS?"].filter(function (c) { return c !== lf.q; }));
    }
    if (/^(what is|whats|what are|tell me about)\s+(the\s+)?(zeus\s+)?discord( server| link| invite)?$/.test(s.replace(/[?!.']/g, "").trim())) {
      var dsl = Z.site.links.discord;
      return A([dsl ? "The Zeus Discord is the place for support, sharing configs, bug reports and news about new releases. Invite: " + dsl : "The Zeus Discord invite has not been added to this site yet."], { links: dsl ? [{ label: "Join the Discord", href: dsl, ext: true }] : [], src: "Site", chips: ["How do I send a Discord webhook from Zeus?", "How do I report a bug?"] });
    }
    if (/\b(boat|raft)\b/.test(s) && /(not work|does ?n.?t work|broken|nothing|stuck|rubber|snap\w*|teleport\w*|back|suffocat\w*|inside|fix|why|fail\w*)/.test(s)) {
      var bf = exactFaq("Boat NoClip or Boat Fly does not work");
      if (bf) return faqAnswer(bf, ["What does Boat NoClip do?", "Why does the server rubber-band me?"]);
    }
    if (/(where|how).*(download|get zeus|get the mod|get the jar)|download (link|page|button)|^download$|^link$/.test(s)) {
      var dl = Z.site.links.download;
      var gh3 = Z.site.links.github;
      return A([dl ? "The download button on the Download page opens the official download page." + (gh3 ? " The same jar is also on the project's GitHub Releases page." : "") : "The download link has not been published on this site yet." + (gh3 ? " You can already get the jar from the project's GitHub Releases page." : " The Download page lists the requirements and install steps, and the Discord (if linked) will have the latest build.")],
        { links: [{ label: "Download page", href: "/download" }].concat(dl ? [{ label: "Open download link", href: dl, ext: true }] : []).concat(gh3 ? [{ label: "GitHub", href: gh3, ext: true }] : []), src: "Site", chips: ["How do I install Zeus?", "Is Zeus safe?", "How do I check that my download is genuine?"] });
    }
    if ((/\b(not|n.?t|never|dont|do not|cannot|can.?t|wont)\b.{0,12}\b(trust|sure|comfortable)\b|\b(distrust|untrusted|sketchy|suspicious|shady|scam\w*)\b/.test(s) || (/\b(download\w*|file|jar|site|website|link)\b/.test(s) && /\b(safe|trust\w*|legit|virus|malware|fake|genuine|secure)\b/.test(s))) && /\b(download\w*|file|jar|site|website|link|you|us|this|it)\b/.test(s) && !one && !/\b(module|webhook|server|bann?\w*|account)\b/.test(s)) {
      var gh4 = Z.site.links.github;
      return A([oneOf("trust", ["You do not have to trust this site. If you do not want to download the file from here, you can always get it from the project's GitHub page instead" + (gh4 ? ": " + gh4 : ", or build it yourself from the source on the Source page") + ". Compare the SHA-256 with the one on the Download page and you can be sure it is the same file.", "That is fair. You can skip this site's download entirely and get Zeus from GitHub" + (gh4 ? " (" + gh4 + ")" : "") + ", or read the source and build the jar yourself. Either way, check the file's SHA-256 against the one on the Download page."]),
        "The code is readable on the Source page, Zeus never asks for a password, and it has no server of its own, so there is nothing to log you in or send your data to."],
        { links: (gh4 ? [{ label: "Open GitHub", href: gh4, ext: true }] : [{ label: "GitHub download section", href: "/download#github" }]).concat([{ label: "Safety checklist", href: "#safe" }, { label: "Source code", href: "/source" }]), src: "Site", chips: ["How do I check that my download is genuine?", "Does Zeus collect data or have telemetry?", "Is Zeus a virus?"] });
    }
    if (/(this|the) (site|website|page).*(official|real|legit|fake|safe)|official (site|website|page)/.test(s))
      return A(["This is the official Zeus Client website. The download link on the Download page is the one the developer published" + (Z.site.links.github ? ", and the same file is on the project's GitHub page." : ", and the source code is on the Source page so you can check what you are running."), "Never download Zeus from a site that asks for your Minecraft or Microsoft password."], { links: [{ label: "Download page", href: "/download" }].concat(Z.site.links.github ? [{ label: "GitHub", href: Z.site.links.github, ext: true }] : [{ label: "Source code", href: "/source" }]), src: "Site", chips: ["Is Zeus safe?", "Is the control center safe?"] });
    return null;
  }

  function moduleIntent(s) {
    if (/\b(bind|keybind|hotkey|shortcut|assign)\b|\bkey\b|which key/.test(s)) return "bind";
    if (/\b(setting|settings|configure|config|option|options|sliders|adjust|tune|customi[sz]e|values|what can i (change|adjust|set))\b/.test(s)) return "settings";
    if (/\b(safe|ban|banned|detect|detected|detectable|allowed|legit|risk|punish|kick|flag|flagged|anticheat|anti cheat)\b/.test(s)) return "safe";
    if (/\b(where|find|located|category|which tab|which menu)\b/.test(s)) return "where";
    if (/\b(turn on|switch on|enable|activate|toggle|how (do|to|can)|use|start|setup|set up)\b/.test(s)) return "use";
    return "what";
  }

  /* ======================================================================= main answer function */
  var state = { last: null, lastTurn: false, history: [] };

  var DYNAMIC_FAQ = { "which hud widgets can i move in the hud editor": 1, "how many modules does zeus have": 1, "how many settings does zeus have": 1, "what are the categories in the panel": 1 };
  var FAQ_EXACT = null;
  function exactFaq(q) {
    if (!FAQ_EXACT) {
      FAQ_EXACT = {};
      ((Z.help && Z.help.faq) || []).forEach(function (f) { var k = words(f.q).join(" "); if (!DYNAMIC_FAQ[k]) FAQ_EXACT[k] = f; });
    }
    return FAQ_EXACT[words(q).join(" ")] || null;
  }

  function answerOne(q) {
    if (!K) build();
    var s = q.toLowerCase().trim();

    var ef = exactFaq(q);
    if (ef) return faqAnswer(ef, []);

    // a question that names a module and one of its settings
    var fm = findModules(q);
    if (fm.length && !isReco(s) && !(fm.length >= 2 && isCompare(s))) {
      var strictOnly = moduleIntent(s) === "bind" || moduleIntent(s) === "safe";
      var bestHit = null;
      fm.forEach(function (mm0) { // later mentions win ties: "what does Speed do in Flight" means Flight's Speed
        var hit = matchSetting(mm0, q);
        if (!hit || (strictOnly && !hit.exact)) return;
        if (!bestHit || (hit.cov + hit.hit * 0.01) >= (bestHit.cov + bestHit.hit * 0.01)) bestHit = hit;
      });
      if (bestHit && !(bestHit.best.nameTerms.length === 1 && bestHit.cov !== 1)) return settingAnswer(bestHit);
    }

    var it = intents(q, state);
    if (it) return it;

    var mm = findModule(q);
    var pron = /\b(it|its|this|that|them|those|the module|this one)\b/.test(s) && words(s).length <= 9;
    var ranked = search(q);
    var top = ranked[0];
    if (!mm && pron && state.last && state.lastTurn && (moduleIntent(s) !== "what" || !top || top.s < 5)) mm = state.last;

    // a specific setting of a module the question names (or of the module we were just talking about)
    var settingHit = null;
    if (mm && moduleIntent(s) !== "bind" && moduleIntent(s) !== "safe" && !isReco(s)) settingHit = matchSetting(mm, q);
    if (!settingHit && !mm && state.last && state.lastTurn && words(s).length <= 7 && !(top && top.d.type === "faq" && top.s > 8)) {
      settingHit = matchSetting(state.last, q);
      if (settingHit) mm = state.last;
    }
    if (settingHit && !(moduleIntent(s) === "settings" && /\b(settings|options)\b/.test(s) && settingHit.best.nameTerms.length === 1 && settingHit.best.cov !== 1)) return settingAnswer(settingHit);

    // an FAQ whose question already names the module wins over the generic module card
    if (mm) {
      if (top && top.d.type === "faq" && top.s > 4 && terms(top.d.title).join(" ").indexOf(terms(mm.m.name).join(" ")) >= 0 && moduleIntent(s) === "what" &&
          !(/^(what is|whats|what are|tell me about|explain)\b/.test(s) && !/^(what|how does)\b/i.test(top.d.title)))
        return finish(top, ranked);
      return moduleAnswer(mm, moduleIntent(s));
    }

    if (!top || top.s < 2.4 || (top.cover < 0.5 && top.s < 10)) return fallback(q, ranked);
    if (top.d.type !== "faq" && top.d.type !== "section" && !top.titleHit && top.cover < 0.75) return fallback(q, ranked);

    return finish(top, ranked);
  }

  function related(ranked, skip) {
    var out = [];
    for (var i = 0; i < ranked.length && out.length < 3; i++) {
      var d = ranked[i].d;
      if (d === skip || d.type !== "faq" || ranked[i].s < 1.4) continue;
      out.push(d.title);
    }
    return out;
  }

  function finish(top, ranked) {
    var d = top.d, a;
    if (d.type === "faq") a = faqAnswer(d.faq, related(ranked, d));
    else if (d.type === "module") {
      a = moduleAnswer(d.mod, "what");
      var also = ranked.filter(function (r) { return r.d.type === "module" && r.d !== d && r.s > top.s * 0.62; }).slice(0, 3);
      if (also.length) { a.related = true; a.p.push("Related modules:"); a.ul = also.map(function (r) { return r.d.mod.m.name + ": " + r.d.mod.m.description; }); }
    }
    else if (d.type === "setting") {
      a = settingAnswer({ best: d.set, ties: [] });
    }
    else if (d.type === "section") { a = sectionAnswer(d.sec); a.chips = related(ranked, d); }
    else if (d.type === "command") a = A([d.cmd.cmd + " – " + d.cmd.text], { links: [{ label: "All commands", href: "/tutorial" }], src: "Commands", chips: ["What commands does Zeus have?"] });
    else if (d.type === "release") a = A(["v" + d.rel.version + " – " + d.rel.title + ": " + d.rel.text], { links: [{ label: "Update timeline", href: "/information#timeline" }], src: "Timeline" });
    else a = fallback("", ranked);
    return a;
  }

  function fallback(q, ranked) {
    var chips = [];
    (ranked || []).slice(0, 8).forEach(function (r) {
      if (chips.length < 3 && r.s > 0.9 && (r.d.type === "faq" || r.d.type === "section") && chips.indexOf(r.d.title) < 0) chips.push(r.d.type === "faq" ? r.d.title : "Tell me about " + r.d.title);
    });
    if (!chips.length) chips = FALLBACK_TOPICS.slice(0, 4);
    var links = [{ label: "Open the FAQ", href: "/help#faq" }];
    var dc = discordLink();
    if (dc) links.push(dc);
    var q2 = String(q || "").toLowerCase();
    if (/\b(does|do)\b.*\b(zeus|it)\b.*\b(have|include|support|offer)\b|\bis there (a|an|any)\b/.test(q2))
      return A(["I could not find a module or setting for that among Zeus's " + (Z.site.moduleCount || K.mods.length) + " modules, so it probably does not exist (yet). If you have an idea for one, the Discord is the place to suggest it."], { chips: chips, links: [{ label: "Browse all modules", href: "/features" }].concat(dc ? [dc] : []), src: "Module list" });
    return A(["I couldn't find an answer to that in the Zeus guides, and I'd rather say so than guess. Try asking it with the module or feature name, or pick one of these:"], { chips: chips, links: links, src: "Helper" });
  }


  /* ======================================================================= answer variants */
  function dot(t) { t = String(t || "").trim(); return t && !/[.!?:…)]$/.test(t) ? t + "." : t; }
  function sentences(t) { return String(t).split(/(?<=[.!?])\s+(?=[A-Z0-9“"(])/).filter(Boolean); }

  /* same facts, different shape: a lead sentence with bullets, or two short paragraphs */
  function reshape(a, key) {
    if (a.p.length !== 1 || (a.ul && a.ul.length) || (a.ol && a.ol.length)) return a;
    var sn = sentences(a.p[0]);
    var kinds = ["plain", "plain"];
    if (sn.length >= 3 && sn.slice(1).every(function (x) { return x.length < 260; })) kinds.push("bullets");
    if (sn.length >= 2) kinds.push("split");
    var k = kinds[pickIdx(key + ":shape", kinds.length)];
    if (k === "bullets") { a.p = [sn[0]]; a.ul = sn.slice(1); }
    else if (k === "split") { var cut = Math.max(1, Math.round(sn.length / 2)); a.p = [sn.slice(0, cut).join(" "), sn.slice(cut).join(" ")]; }
    return a;
  }

  function modVary(a) {
    var mm = a.mod, m = mm.m, cat = mm.cat, intent = a.vintent, d = dot(m.description);
    var n = pickIdx(a.vkey, 3);
    if (n === 0) return a;
    var ns = mm.nset, sn = setNames(mm, 3);
    var setLine = ns ? ns + " setting" + (ns === 1 ? "" : "s") + ", for example " + listNames(sn) : "";
    if (intent === "what") {
      if (n === 1) {
        var ul = ["Where: press Z, open " + cat + " and find " + m.name + ".", "Use: click its switch to turn it on, or click the row to tune it first."];
        if (ns) ul.push("Settings: " + setLine + ".");
        if (m.hud) ul.push("HUD: it has a card you can place with /zeus hud.");
        a.p = [m.name + " is a " + cat + " module. " + d]; a.ul = ul;
      } else {
        a.p = [d + " That is " + m.name + ", in the " + cat + " category.", "Press Z, go to " + cat + " and flip its switch." + (ns ? " It has " + setLine + ", all on the same row." : " It has no settings, so that is all there is to it.") + (m.hud ? " Its HUD card can be moved in the HUD editor." : "")];
        a.ul = [];
      }
    } else if (intent === "use") {
      var head = a.p[0];
      if (n === 1) { a.p = [head, "Quick start: Z, then " + cat + ", then switch " + m.name + " on. Click its row first if you want to tune it. The Panic button or /zeus panic switches everything off again."]; a.ol = []; }
      else { a.p = [head]; a.ol = ["Press Z and open the " + cat + " category.", "Flip the switch on " + m.name + ". It is running as soon as the switch is lit.", "To adjust it, click its row. To stop it, flip the switch again or press Panic."]; }
    } else if (intent === "bind") {
      var head2 = a.p[0];
      if (n === 1) { a.p = [head2, "Press Z, open " + cat + ", click the " + m.name + " row (not its switch) and then the key chip that says None. Press the key you want. Backspace clears it."]; a.ol = []; }
      else { a.p = [head2, "Binding takes three clicks:"]; a.ol = ["Press Z and open " + cat + ".", "Click the " + m.name + " row to open its settings.", "Click the key chip and press a key. Backspace removes the bind."]; }
    } else if (intent === "where") {
      if (n === 1) a.p = [m.name + " is under " + cat + " in the Zeus panel (press Z).", d];
      else a.p = ["Press Z and look in " + cat + ". " + m.name + " is there. " + d];
    } else if (intent === "settings") {
      if (a.p.length > 1 && ns) a.p[1] = n === 1 ? "Here is what you can adjust in " + m.name + (a.ul.length < ns ? " (the first " + a.ul.length + ")" : "") + ":" : m.name + " exposes " + ns + " setting" + (ns === 1 ? "" : "s") + (a.ul.length < ns ? ", the first " + a.ul.length + " are:" : ":");
    } else if (intent === "safe") {
      if (n === 1) a.p = [a.p[0], "Short answer: " + m.name + " only changes what your own game does, and the server decides whether that is allowed. Many servers ban cheats like it, so read the rules and use it only where it is permitted. Zeus never sends your account details anywhere."];
      else a.p = [m.name + " is a cheat feature, so a server that forbids cheats can punish you for it. Use it in your own worlds or on servers whose rules allow it. Nothing in Zeus logs into accounts or sends your data out."];
    }
    return a;
  }

  function vary(a) {
    if (a.faq) {
      var alts = a.faq.alts || [];
      var key = "faq:" + a.faq.q;
      var i = pickIdx(key, alts.length + 1);
      if (i > 0) {
        var al = alts[i - 1];
        a.p = (al.p || []).slice(); a.ul = (al.ul || []).slice(); a.ol = (al.ol || []).slice();
        if (al.extra) a.extra = al.extra;
      }
      return reshape(a, key);
    }
    if (a.vkey && a.mod && !a.related) return modVary(a);
    return a;
  }

  function answer(q) {
    if (!K) build();
    var parts = q.split(/\?+\s+|;\s+|\n+/).map(function (x) { return x.trim(); }).filter(function (x) { return x.length > 2; });
    if (parts.length > 3) parts = parts.slice(0, 3);
    if (!parts.length) parts = [q];
    var out = parts.map(function (p) {
      var a = vary(answerOne(p));
      if (!a.mod && a.src !== "Helper") { var fm = findModule(p); if (fm && words(p).length <= 10) a.mod = fm; }
      if (a.mod) { state.last = a.mod; state.lastTurn = true; } else state.lastTurn = false;
      a.q = p;
      a.miss = a.src === "Helper" || /could not find a module or setting/.test(a.p[0] || "");
      return a;
    });
    return out;
  }

  /* ======================================================================= conversation + views */
  var views = [];
  var CMD_RE = null;
  function cmdRegex() {
    if (CMD_RE) return CMD_RE;
    var cmds = ((Z.content && Z.content.commands) || []).map(function (c) { return c.cmd; }).concat(["/zeus hud", "/zeus panic", "/zeus tutorial", "/zeus website", "/zeus website stop", "/zeus website status"]);
    cmds.sort(function (a, b) { return b.length - a.length; });
    CMD_RE = new RegExp("(" + cmds.map(function (c) { return c.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"); }).join("|") + "|config/zeus\\.json|\\.minecraft/mods)", "g");
    return CMD_RE;
  }
  function rich(text) {
    var frag = document.createDocumentFragment();
    var re = cmdRegex(), last = 0, m;
    re.lastIndex = 0;
    while ((m = re.exec(text))) {
      if (m.index > last) frag.appendChild(document.createTextNode(text.slice(last, m.index)));
      frag.appendChild(h("code", { text: m[0] }));
      last = m.index + m[0].length;
    }
    if (last < text.length) frag.appendChild(document.createTextNode(text.slice(last)));
    return frag;
  }

  /* ---- "Did that help?": everything stays in this browser (localStorage), nothing is sent anywhere ---- */
  var LOG_KEY = "zeus.helper.log";
  function readLog() {
    try { var l = JSON.parse(localStorage.getItem(LOG_KEY) || "[]"); return Array.isArray(l) ? l : []; } catch (e) { return []; }
  }
  function writeLog(l) { try { localStorage.setItem(LOG_KEY, JSON.stringify(l.slice(-150))); } catch (e) { /* storage may be blocked */ } }
  function logQuestion(q, kind) {
    var l = readLog();
    for (var i = l.length - 1; i >= 0; i--) if (l[i].q === q && (kind === "miss" || l[i].kind === "miss")) { l[i].kind = kind; l[i].t = Date.now(); writeLog(l); return; }
    l.push({ q: String(q).slice(0, 200), kind: kind, t: Date.now() });
    writeLog(l);
  }
  function feedbackRow(a) {
    var row = h("div", { class: "fb" });
    function thanks(text) { while (row.firstChild) row.removeChild(row.firstChild); row.appendChild(h("span", { class: "fb-done" }, icon("check", "small"), text)); }
    if (a.fb) { thanks(a.fb === "yes" ? "Glad it helped. Saved on this device only." : "Noted. Saved on this device only."); return row; }
    var yes = h("button", { type: "button", class: "fb-b", text: "Yes" });
    var no = h("button", { type: "button", class: "fb-b", text: "No" });
    yes.addEventListener("click", function () { a.fb = "yes"; logQuestion(a.q, "helped"); save(); thanks("Glad it helped. Saved on this device only."); });
    no.addEventListener("click", function () { a.fb = "no"; logQuestion(a.q, "unhelpful"); save(); thanks("Noted. Saved on this device only."); });
    row.appendChild(h("span", { class: "fb-q", text: "Did that help?" }));
    row.appendChild(yes); row.appendChild(no);
    return row;
  }
  Z.helperLog = { read: readLog, write: writeLog };

  function renderBot(a) {
    var box = h("div", { class: "msg bot" });
    a.p.forEach(function (t) { if (t) box.appendChild(h("p", {}, rich(t))); });
    if (a.ol && a.ol.length) box.appendChild(h("ol", { style: "margin:6px 0 0;padding-left:20px" }, a.ol.map(function (t) { return h("li", {}, rich(t)); })));
    if (a.ul && a.ul.length) box.appendChild(h("ul", {}, a.ul.map(function (t) { return h("li", {}, rich(t)); })));
    if (a.extra) box.appendChild(h("p", { style: "margin-top:8px" }, rich(a.extra)));
    if (a.links && a.links.length) {
      box.appendChild(h("div", { class: "acts" }, a.links.map(function (l) {
        if (l.href === "#safe") { var sb = h("a", { href: "#safe", role: "button" }, l.label, icon("arrow", "small")); sb.addEventListener("click", function (e) { e.preventDefault(); if (Z.safe) Z.safe.open(); }); return sb; }
        return l.ext ? h("a", { href: l.href, target: "_blank", rel: "noopener noreferrer" }, l.label, icon("arrow", "small")) : h("a", { href: l.href, "data-helper-link": "" }, l.label, icon("arrow", "small"));
      })));
    }
    if (a.src) box.appendChild(h("span", { class: "src", text: "Source: " + a.src }));
    if (a.q) box.appendChild(feedbackRow(a));
    return box;
  }

  /* ---- thinking, then typing: 1 to 3 seconds in total ---- */
  var TYPE_MIN_TOTAL = 1000, TYPE_MAX_TOTAL = 2800;
  var waiters = [], animators = [];
  function wait(ms) {
    return new Promise(function (res) {
      var t = setTimeout(done, ms), w = { done: done };
      waiters.push(w);
      function done() { clearTimeout(t); var i = waiters.indexOf(w); if (i >= 0) waiters.splice(i, 1); res(); }
    });
  }
  function skipAnimation() {
    waiters.slice().forEach(function (w) { w.done(); });
    animators.slice().forEach(function (f) { f(); });
  }
  function plan(a, first) {
    var chars = (a.p.join(" ") + a.ul.join(" ") + a.ol.join(" ") + (a.extra || "")).length;
    var think = Math.max(500, Math.min(950, 460 + chars * 0.9));
    var type = Math.max(450, Math.min(2000, chars * 11));
    if (!first) { think = Math.min(think, 520); }
    if (think + type < TYPE_MIN_TOTAL) type = TYPE_MIN_TOTAL - think;
    if (think + type > TYPE_MAX_TOTAL) type = TYPE_MAX_TOTAL - think;
    return { think: Math.round(think), type: Math.round(type) };
  }
  /* reveal a rendered answer character by character; blocks appear as the text reaches them */
  function typeBox(box, ms, onDone) {
    var tail = box.querySelectorAll(".acts, .src, .fb");
    var blocks = [].slice.call(box.querySelectorAll("p, li, ol, ul")).filter(function (b) { return !b.closest(".acts, .fb"); });
    var nodes = [], total = 0;
    var walker = document.createTreeWalker(box, NodeFilter.SHOW_TEXT, { acceptNode: function (n) { return n.parentNode.closest(".acts, .src, .fb") ? NodeFilter.FILTER_REJECT : (n.nodeValue.trim() ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_SKIP); } });
    var n;
    while ((n = walker.nextNode())) { nodes.push({ node: n, text: n.nodeValue, start: total }); total += n.nodeValue.length; n.nodeValue = ""; }
    if (!total) { onDone(); return; }
    blocks.forEach(function (b) { b.style.display = "none"; });
    [].forEach.call(tail, function (t) { t.classList.add("wait"); });
    var caret = h("span", { class: "caret", "aria-hidden": "true" });
    var t0 = performance.now(), finished = false, shown = -1;
    function reveal(count) {
      var last = null;
      for (var i = 0; i < nodes.length; i++) {
        var e = nodes[i], k = Math.max(0, Math.min(e.text.length, count - e.start));
        if (k !== e.node.nodeValue.length) e.node.nodeValue = e.text.slice(0, k);
        if (k > 0) {
          last = e.node;
          for (var p = e.node.parentNode; p && p !== box; p = p.parentNode) if (p.style && p.style.display === "none") p.style.display = "";
        }
      }
      if (last && last.parentNode) last.parentNode.insertBefore(caret, last.nextSibling);
    }
    function finish() {
      if (finished) return;
      finished = true;
      var i = animators.indexOf(finish); if (i >= 0) animators.splice(i, 1);
      reveal(total);
      blocks.forEach(function (b) { b.style.display = ""; });
      if (caret.parentNode) caret.parentNode.removeChild(caret);
      [].forEach.call(tail, function (t) { t.classList.remove("wait"); });
      onDone();
    }
    animators.push(finish);
    box.addEventListener("click", function once() { box.removeEventListener("click", once); finish(); });
    (function frame(t) {
      if (finished) return;
      var k = Math.min(1, (t - t0) / ms);
      var eased = k < 1 ? 1 - Math.pow(1 - k, 1.6) : 1;
      var c = Math.floor(total * eased);
      if (c !== shown) { shown = c; reveal(c); }
      if (k >= 1) finish(); else requestAnimationFrame(frame);
    })(t0);
  }

  function save() {
    try { sessionStorage.setItem("zeus.helper", JSON.stringify(state.history.slice(-40))); } catch (e) { /* storage may be blocked */ }
  }
  function restore() {
    try {
      var raw = sessionStorage.getItem("zeus.helper");
      if (raw) state.history = JSON.parse(raw).filter(function (m) { return m && (m.role === "me" || m.role === "bot"); });
    } catch (e) { state.history = []; }
  }

  function makeView(opts) {
    var msgs = h("div", { class: "hlp-m", role: "log", "aria-live": "polite" });
    var chips = h("div", { class: "hlp-c" });
    var input = h("input", { type: "text", placeholder: "Ask about Zeus or this site…", "aria-label": "Ask the helper", autocomplete: "off", maxlength: "300", spellcheck: "true" });
    var go = h("button", { class: "go", type: "submit", "aria-label": "Send" }, icon("send"));
    var form = h("form", { class: "hlp-f" }, input, go);
    var root = h("div", { class: "hlp" + (opts.embedded ? " embedded" : "") + (opts.floating ? "" : "") });
    var view = { root: root, msgs: msgs, input: input, chips: chips };

    var head = h("div", { class: "hlp-h" }, Z.logo(), h("div", {}, h("b", { text: "Zeus Helper" }), h("small", { text: "Answers from this site's own guides" })), h("span", { class: "sp" }));
    var clearBtn = h("button", { class: "ib", type: "button", title: "Start over", "aria-label": "Start over" }, icon("refresh"));
    clearBtn.addEventListener("click", function () { state.history = []; state.last = null; save(); views.forEach(function (v) { v.reset(); }); });
    head.appendChild(clearBtn);
    if (!opts.embedded) {
      var close = h("button", { class: "ib", type: "button", title: "Close", "aria-label": "Close helper" }, icon("close"));
      close.addEventListener("click", function () { Helper.close(); });
      head.appendChild(close);
    }
    root.appendChild(head);
    root.appendChild(msgs);
    root.appendChild(chips);
    root.appendChild(form);
    root.appendChild(h("div", { class: "hlp-note", text: "Offline helper: it searches this site's content and can't browse or run a language model. Nothing you type is sent anywhere." }));

    function scroll() { msgs.scrollTop = msgs.scrollHeight; }
    view.scroll = scroll;
    view.setChips = function (list) {
      clear(chips);
      (list || []).slice(0, 4).forEach(function (t) {
        var b = h("button", { type: "button", text: t });
        b.addEventListener("click", function () { Helper.submit(t); });
        chips.appendChild(b);
      });
      requestAnimationFrame(scroll);
    };
    function clear(n) { while (n.firstChild) n.removeChild(n.firstChild); }
    view.addMsg = function (m, typeMs, done) {
      var box = m.role === "me" ? h("div", { class: "msg me", text: m.text }) : renderBot(m.a);
      msgs.appendChild(box);
      scroll();
      if (m.role !== "me" && typeMs) {
        box.classList.add("typing-now");
        msgs.setAttribute("aria-busy", "true");
        var tick = setInterval(scroll, 90);
        typeBox(box, typeMs, function () { clearInterval(tick); box.classList.remove("typing-now"); msgs.removeAttribute("aria-busy"); scroll(); if (done) done(); });
      } else if (done) done();
    };
    view.typing = function (on, label) {
      var t = msgs.querySelector(".typing");
      if (on && !t) {
        t = h("div", { class: "typing", role: "status", "aria-label": "Helper is thinking" }, h("i"), h("i"), h("i"), h("span", { class: "lbl", text: label || "Thinking" }));
        msgs.appendChild(t); scroll();
      } else if (on && t) { var l = t.querySelector(".lbl"); if (l && label) l.textContent = label; }
      if (!on && t) t.parentNode.removeChild(t);
    };
    view.reset = function () {
      clear(msgs);
      welcome();
    };
    function welcome() {
      var w = { role: "bot", a: A(["Hi, I'm the Zeus helper. Ask me about installing Zeus, any module, the HUD editor, the website or safety. I answer from this site's own guides, so if something isn't written down here I'll tell you instead of guessing."], { src: "" }) };
      msgs.appendChild(renderBot(w.a));
      view.setChips((Z.help && Z.help.quick) || FALLBACK_TOPICS);
    }
    view.fill = function () {
      clear(msgs);
      if (!state.history.length) { welcome(); return; }
      state.history.forEach(function (m) { msgs.appendChild(m.role === "me" ? h("div", { class: "msg me", text: m.text }) : renderBot(m.a)); });
      var lastBot = null;
      state.history.forEach(function (m) { if (m.role === "bot") lastBot = m; });
      view.setChips(lastBot && lastBot.a.chips && lastBot.a.chips.length ? lastBot.a.chips : (Z.help.quick || []));
      scroll();
    };
    form.addEventListener("submit", function (e) {
      e.preventDefault();
      var v = input.value.trim();
      if (!v) return;
      input.value = "";
      Helper.submit(v);
    });
    root.addEventListener("click", function (e) {
      var a = e.target.closest && e.target.closest("a[data-helper-link]");
      if (a && !opts.embedded && window.innerWidth < 760) Helper.close();
    });
    view.fill();
    return view;
  }

  /* ======================================================================= public API */
  var floating = null, fab = null, queue = Promise.resolve();
  var LABELS = ["Thinking", "Searching the guides", "Writing the answer"];
  function respond(q) {
    var answers = answer(q);
    var chain = Promise.resolve();
    answers.forEach(function (a, idx) {
      chain = chain.then(function () {
        var pl = plan(a, idx === 0);
        views = views.filter(function (v) { return v.root.isConnected; });
        var labelAt = function (n) { return LABELS[Math.min(LABELS.length - 1, n)]; };
        var step = pl.think / 3;
        views.forEach(function (v) { v.typing(true, labelAt(0)); });
        return (pl.think ? wait(step).then(function () { views.forEach(function (v) { v.typing(true, labelAt(1)); }); return wait(step); }).then(function () { views.forEach(function (v) { v.typing(true, labelAt(2)); }); return wait(step); }) : Promise.resolve()).then(function () {
          var m = { role: "bot", a: a };
          if (a.miss) logQuestion(a.q, "miss");
          state.history.push(m);
          save();
          views.forEach(function (v) { v.typing(false); });
          return new Promise(function (res) {
            var left = views.length || 1;
            var one = function () { if (--left <= 0) res(); };
            if (!views.length) { res(); return; }
            views.forEach(function (v) { v.addMsg(m, pl.type, one); });
          });
        });
      });
    });
    return chain.then(function () {
      var last = answers[answers.length - 1];
      views.forEach(function (v) { v.setChips(last.chips && last.chips.length ? last.chips : (Z.help.quick || [])); });
    });
  }
  var Helper = (Z.helper = {
    mount: function (host) {
      views = views.filter(function (v) { return v.root.isConnected; });
      var v = makeView({ embedded: true });
      views.push(v);
      host.appendChild(v.root);
      v.embeddedHost = host;
      return v;
    },
    isFloatingOpen: function () { return !!floating; },
    reset: function () { K = null; FAQ_EXACT = null; },
    open: function () {
      if (floating) { floating.input.focus(); return; }
      floating = makeView({ floating: true });
      views.push(floating);
      document.body.appendChild(floating.root);
      if (fab) fab.classList.add("hide");
      setTimeout(function () { floating && floating.input.focus(); }, 60);
    },
    close: function () {
      if (!floating) return;
      var f = floating;
      floating = null;
      f.root.classList.add("out");
      views = views.filter(function (v) { return v !== f; });
      setTimeout(function () { if (f.root.parentNode) f.root.parentNode.removeChild(f.root); }, 240);
      if (fab) fab.classList.remove("hide");
    },
    submit: function (q) {
      q = String(q || "").trim().slice(0, 300);
      if (!q) return;
      views = views.filter(function (v) { return v.root.isConnected; });
      skipAnimation(); // finish whatever is still being typed
      var me = { role: "me", text: q };
      state.history.push(me);
      views.forEach(function (v) { v.addMsg(me); v.setChips([]); });
      queue = queue.then(function () { return respond(q); });
    },
    ask: function (q) {
      var host = views.filter(function (v) { return v.embeddedHost && v.embeddedHost.isConnected; })[0];
      if (host) {
        var el = document.getElementById("ask");
        if (el) el.scrollIntoView({ behavior: Z.reduced ? "auto" : "smooth", block: "center" });
      } else Helper.open();
      Helper.submit(q);
    },
    /* for tests: returns the answer objects without touching the UI */
    answer: function (q, v) { forceVar = v == null ? -1 : v; try { return answer(q); } finally { forceVar = -1; } },
    debug: function (q) { if (!K) build(); return search(q).slice(0, 4).map(function (r) { return r.d.type + ":" + r.d.title + " " + r.s.toFixed(2) + " c" + r.cover.toFixed(2); }); },
  });

  /* ======================================================================= Ctrl+K search */
  var palette = null;
  var PAGE_LIST = [
    ["Home", "/", "The start page", "home"], ["Download", "/download", "Requirements and install steps", "download"], ["Features", "/features", "Every module and setting", "cube"],
    ["Tutorial", "/tutorial", "Get started in six steps", "play"], ["Help centre", "/help", "Guides, FAQ and the helper", "help"], ["Credits", "/credits", "The people and references behind Zeus", "users"],
    ["Information", "/information", "About, notes and the update timeline", "info"], ["Control center", "/settings", "Control the running game", "gear"],
  ];
  var palItems = null;
  function palBuild() {
    if (!K) build();
    palItems = [];
    PAGE_LIST.forEach(function (p) { palItems.push({ kind: "page", label: p[0], sub: p[2], icon: p[3], hay: (p[0] + " " + p[2]).toLowerCase(), go: function () { Z.go(p[1]); } }); });
    K.mods.forEach(function (mm) {
      palItems.push({ kind: "module", label: mm.m.name, sub: mm.cat + " · " + shortText(mm.m.description, 90), icon: "cube", color: ((Z.features.categories || []).filter(function (c) { return c.id === mm.m.category; })[0] || {}).color, hay: (mm.m.name + " " + mm.joined + " " + mm.cat + " " + (mm.m.description || "")).toLowerCase(), go: function () { Z.go("/features?m=" + encodeURIComponent(mm.m.id)); } });
      mm.sets.forEach(function (st) {
        palItems.push({ kind: "setting", label: st.s.name, sub: mm.m.name + " · " + shortText(st.s.desc, 80), icon: "sliders", hay: (st.s.name + " " + mm.m.name + " " + (st.s.desc || "")).toLowerCase(), nameHay: st.s.name.toLowerCase(), go: function () { Z.go("/features?m=" + encodeURIComponent(mm.m.id) + "&s=" + encodeURIComponent(st.s.name)); } });
      });
    });
    ((Z.help && Z.help.faq) || []).forEach(function (f) {
      palItems.push({ kind: "answer", label: f.q, sub: shortText(f.a, 100), icon: "chat", hay: (f.q + " " + (f.keys || "")).toLowerCase(), go: function () { Helper.ask(f.q); } });
    });
  }
  var KIND_ORDER = ["page", "module", "setting", "answer"], KIND_TITLE = { page: "Pages", module: "Modules", setting: "Settings", answer: "Answers" }, KIND_MAX = { page: 3, module: 6, setting: 5, answer: 4 };
  function palSearch(q) {
    q = q.toLowerCase().trim();
    if (!q) return PAGE_LIST.map(function (p, i) { return palItems[i]; }).concat((Z.help.quick || []).slice(0, 4).map(function (t) { return { kind: "answer", label: t, sub: "Ask the helper", icon: "chat", go: function () { Helper.ask(t); } }; }));
    var qs = q.split(/\s+/).filter(Boolean), out = [];
    palItems.forEach(function (it) {
      var score = 0;
      for (var i = 0; i < qs.length; i++) {
        var t = qs[i], at = it.hay.indexOf(t);
        if (at < 0) {
          // forgive one typo in a word of the label
          var ok = false;
          if (t.length >= 4) it.label.toLowerCase().split(/[^a-z0-9]+/).forEach(function (w) { if (!ok && w.length >= 4 && lev(t, w, 1) <= 1) ok = true; });
          if (!ok) return;
          score += 1;
        } else {
          var ll = it.label.toLowerCase();
          score += ll.indexOf(t) === 0 ? 8 : ll.indexOf(t) > 0 ? 5 : 2;
        }
      }
      if (it.label.toLowerCase() === q) score += 20;
      if (it.kind === "module") score += 2;
      if (it.kind === "page") score += 3;
      out.push({ it: it, s: score - it.label.length * 0.01 });
    });
    out.sort(function (a, b) { return b.s - a.s; });
    var per = {}, res = [];
    out.forEach(function (o) { var k = o.it.kind; per[k] = (per[k] || 0) + 1; if (per[k] <= KIND_MAX[k]) res.push(o); });
    return res.map(function (o) { return o.it; });
  }
  function openPalette(initial) {
    if (palette) { palette.input.focus(); return; }
    if (!palItems) palBuild();
    var input = h("input", { type: "text", placeholder: "Search pages, modules, settings and answers…", "aria-label": "Search", autocomplete: "off", spellcheck: "false", role: "combobox", "aria-expanded": "true", "aria-controls": "pal-list" });
    var list = h("div", { class: "pal-list", id: "pal-list", role: "listbox" });
    var box = h("div", { class: "pal-box" },
      h("div", { class: "pal-in" }, icon("search"), input, h("kbd", { text: "Esc" })),
      list,
      h("div", { class: "pal-foot" }, h("span", {}, h("kbd", { text: "↑" }), h("kbd", { text: "↓" }), " move"), h("span", {}, h("kbd", { text: "Enter" }), " open"), h("span", {}, h("kbd", { text: "Tab" }), " ask the helper")));
    var root = h("div", { class: "pal", role: "dialog", "aria-modal": "true", "aria-label": "Search" }, h("div", { class: "pal-bg" }), box);
    var sel = 0, shown = [];
    function paint() {
      while (list.firstChild) list.removeChild(list.firstChild);
      shown = palSearch(input.value);
      if (!shown.length) { list.appendChild(h("div", { class: "pal-empty" }, "Nothing found. Press Tab to ask the helper: “" + shortText(input.value, 40) + "”")); return; }
      var lastKind = "";
      shown.forEach(function (it, i) {
        if (it.kind !== lastKind) { lastKind = it.kind; list.appendChild(h("div", { class: "pal-h", text: input.value.trim() ? KIND_TITLE[it.kind] : (it.kind === "page" ? "Go to" : "Ask the helper") })); }
        var row = h("div", { class: "pal-r" + (i === sel ? " on" : ""), role: "option", "aria-selected": i === sel ? "true" : "false", "data-i": String(i) },
          h("span", { class: "pal-i", style: it.color ? "color:" + it.color : "" }, icon(it.icon)),
          h("span", { class: "pal-t" }, h("b", { text: it.label }), h("small", { text: it.sub || "" })),
          h("span", { class: "pal-k", text: it.kind === "answer" ? "Ask" : it.kind === "page" ? "Go" : "Open" }));
        row.addEventListener("mousemove", function () { if (sel !== i) { sel = i; mark(); } });
        row.addEventListener("click", function () { choose(i); });
        list.appendChild(row);
      });
    }
    function mark() {
      [].forEach.call(list.querySelectorAll(".pal-r"), function (r) { var on = Number(r.getAttribute("data-i")) === sel; r.classList.toggle("on", on); r.setAttribute("aria-selected", on ? "true" : "false"); if (on) r.scrollIntoView({ block: "nearest" }); });
    }
    function choose(i) { var it = shown[i]; if (!it) return; closePalette(); setTimeout(it.go, 30); }
    input.addEventListener("input", function () { sel = 0; paint(); });
    input.addEventListener("keydown", function (e) {
      if (e.key === "ArrowDown") { e.preventDefault(); sel = Math.min(shown.length - 1, sel + 1); mark(); }
      else if (e.key === "ArrowUp") { e.preventDefault(); sel = Math.max(0, sel - 1); mark(); }
      else if (e.key === "Enter") { e.preventDefault(); choose(sel); }
      else if (e.key === "Tab") { e.preventDefault(); var v = input.value.trim(); closePalette(); if (v) setTimeout(function () { Helper.ask(v); }, 30); else Helper.open(); }
      else if (e.key === "Escape") { e.preventDefault(); closePalette(); }
    });
    root.querySelector(".pal-bg").addEventListener("click", closePalette);
    document.body.appendChild(root);
    palette = { root: root, input: input, prev: document.activeElement };
    input.value = initial || "";
    paint();
    requestAnimationFrame(function () { root.classList.add("in"); input.focus(); });
  }
  function closePalette() {
    if (!palette) return;
    var p = palette; palette = null;
    p.root.classList.remove("in");
    setTimeout(function () { if (p.root.parentNode) p.root.parentNode.removeChild(p.root); }, 180);
    try { if (p.prev && p.prev.focus) p.prev.focus(); } catch (e) { /* ignore */ }
  }
  Helper.openSearch = openPalette;

  /* ======================================================================= launcher */
  restore();
  function init() {
    build();
    fab = h("button", { class: "hlp-fab", type: "button", "aria-label": "Open the helper" }, icon("chat"), h("span", { text: "Ask the helper" }));
    fab.addEventListener("click", Helper.open);
    document.body.appendChild(fab);
    var sync = function () {
      var onHelp = location.pathname.replace(/\/+$/, "") === "/help";
      fab.style.display = onHelp ? "none" : "";
      if (onHelp && floating) Helper.close();
    };
    document.addEventListener("zeus:page", sync);
    document.addEventListener("keydown", function (e) {
      if ((e.ctrlKey || e.metaKey) && !e.altKey && !e.shiftKey && e.key.toLowerCase() === "k") { e.preventDefault(); if (palette) closePalette(); else openPalette(); return; }
      if (e.key === "Escape" && floating && !palette) Helper.close();
    });
    var nav = document.querySelector(".nav-inner"), helpTop = document.getElementById("helpTop");
    if (nav && helpTop) {
      var sb = h("button", { class: "btn btn-help srch-btn", id: "searchTop", type: "button", "aria-label": "Search the site (Ctrl+K)", title: "Search (Ctrl+K)" }, icon("search"), h("span", { text: "Search" }), h("kbd", { text: "Ctrl K" }));
      sb.addEventListener("click", function () { openPalette(); });
      nav.insertBefore(sb, helpTop);
    }
    sync();
  }
  Z.ready.push(init);
})();
