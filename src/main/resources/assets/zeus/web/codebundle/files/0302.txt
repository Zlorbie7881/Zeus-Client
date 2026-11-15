/* Zeus Client website: the 3D knife viewer. A small WebGL renderer (no library): the knife meshes come from knives3d.json, which tools/knives/make_knives.py
   builds (the game loads a lighter copy of the same knives). Finishes are drawn on a canvas once and cached. It renders only while something moves or is
   being dragged, stops when the tab is hidden or the viewer scrolls out of sight, and caps the pixel ratio so a phone does not work harder than it must. */
(function () {
  "use strict";
  var Z = window.Z;
  if (!Z) return;

  // ------------------------------------------------------------------------------------------ matrices (column-major 4x4)
  function ident() { return [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]; }
  function mul(a, b) {
    var o = new Array(16);
    for (var c = 0; c < 4; c++) for (var r = 0; r < 4; r++) {
      var s = 0;
      for (var k = 0; k < 4; k++) s += a[k * 4 + r] * b[c * 4 + k];
      o[c * 4 + r] = s;
    }
    return o;
  }
  function trans(x, y, z) { var m = ident(); m[12] = x; m[13] = y; m[14] = z; return m; }
  function rotXYZ(rx, ry, rz) {   // X first, then Y, then Z
    var d = Math.PI / 180, cx = Math.cos(rx * d), sx = Math.sin(rx * d), cy = Math.cos(ry * d), sy = Math.sin(ry * d), cz = Math.cos(rz * d), sz = Math.sin(rz * d);
    var X = [1, 0, 0, 0, 0, cx, sx, 0, 0, -sx, cx, 0, 0, 0, 0, 1];
    var Y = [cy, 0, -sy, 0, 0, 1, 0, 0, sy, 0, cy, 0, 0, 0, 0, 1];
    var Zm = [cz, sz, 0, 0, -sz, cz, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];
    return mul(Zm, mul(Y, X));
  }
  function persp(fov, asp, n, f) {
    var t = 1 / Math.tan(fov / 2), m = new Array(16).fill(0);
    m[0] = t / asp; m[5] = t; m[10] = (f + n) / (n - f); m[11] = -1; m[14] = 2 * f * n / (n - f);
    return m;
  }
  function lookAt(e, c, u) {
    var z = norm([e[0] - c[0], e[1] - c[1], e[2] - c[2]]), x = norm(cross(u, z)), y = cross(z, x);
    return [x[0], y[0], z[0], 0, x[1], y[1], z[1], 0, x[2], y[2], z[2], 0, -dot(x, e), -dot(y, e), -dot(z, e), 1];
  }
  function cross(a, b) { return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]; }
  function dot(a, b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
  function norm(a) { var l = Math.sqrt(dot(a, a)) || 1; return [a[0] / l, a[1] / l, a[2] / l]; }

  // ------------------------------------------------------------------------------------------ animation sampling (same maths as the game)
  function cardinal(rows, col, t) {
    var n = rows.length;
    if (!n) return 0;
    if (t <= rows[0][0]) return rows[0][col];
    if (t >= rows[n - 1][0]) return rows[n - 1][col];
    var i = 0;
    while (rows[i + 1][0] < t) i++;
    function tan(j) { return (j <= 0 || j >= n - 1) ? 0 : (rows[j + 1][col] - rows[j - 1][col]) / (rows[j + 1][0] - rows[j - 1][0]); }
    var t0 = rows[i][0], t1 = rows[i + 1][0], h = t1 - t0, u = (t - t0) / h, u2 = u * u, u3 = u2 * u;
    var m0 = tan(i) * h, m1 = tan(i + 1) * h, v0 = rows[i][col], v1 = rows[i + 1][col];
    return (2 * u3 - 3 * u2 + 1) * v0 + (u3 - 2 * u2 + u) * m0 + (-2 * u3 + 3 * u2) * v1 + (u3 - u2) * m1;
  }
  function sample3(rows, t) {
    if (!rows || !rows.length) return [0, 0, 0];
    return [cardinal(rows, 1, t), cardinal(rows, 2, t), cardinal(rows, 3, t)];
  }
  function localM(pivot, rot, pos) {
    var m = trans(pivot[0] + pos[0], pivot[1] + pos[1], pivot[2] + pos[2]);
    m = mul(m, rotXYZ(rot[0], rot[1], rot[2]));
    return mul(m, trans(-pivot[0], -pivot[1], -pivot[2]));
  }

  // ------------------------------------------------------------------------------------------ noise and finish textures
  function hash(x, y, s) {
    var h = (x * 374761393 + y * 668265263 + s * 1442695041) | 0;
    h = (h ^ (h >>> 13)) * 1274126177 | 0;
    return ((h ^ (h >>> 16)) >>> 0) / 4294967295;
  }
  function vnoise(x, y, s) {
    var xi = Math.floor(x), yi = Math.floor(y), xf = x - xi, yf = y - yi;
    var u = xf * xf * (3 - 2 * xf), v = yf * yf * (3 - 2 * yf);
    var a = hash(xi, yi, s), b = hash(xi + 1, yi, s), c = hash(xi, yi + 1, s), d = hash(xi + 1, yi + 1, s);
    return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
  }
  function fbm(x, y, s, oct) {
    var v = 0, a = 0.5, f = 1;
    for (var i = 0; i < oct; i++) { v += a * vnoise(x * f, y * f, s + i * 17); f *= 2.03; a *= 0.5; }
    return v;
  }
  function pal(stops, t) {    // stops: [[pos, r, g, b], ...] with r,g,b in 0..255
    t = Math.min(1, Math.max(0, t));
    for (var i = 0; i < stops.length - 1; i++) {
      var a = stops[i], b = stops[i + 1];
      if (t <= b[0]) { var k = (t - a[0]) / (b[0] - a[0] || 1); return [a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k, a[3] + (b[3] - a[3]) * k]; }
    }
    var l = stops[stops.length - 1]; return [l[1], l[2], l[3]];
  }
  function hsv(h, s, v) {
    h = ((h % 1) + 1) % 1; var i = Math.floor(h * 6), f = h * 6 - i, p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s), r, g, b;
    switch (i % 6) { case 0: r = v; g = t; b = p; break; case 1: r = q; g = v; b = p; break; case 2: r = p; g = v; b = t; break; case 3: r = p; g = q; b = v; break; case 4: r = t; g = p; b = v; break; default: r = v; g = p; b = q; }
    return [r * 255, g * 255, b * 255];
  }

  var STEEL = [190, 198, 210];
  // pattern(u, v) -> [r, g, b]; u runs along the blade (0 handle .. 1 tip), v across it (0 spine .. 1 edge)
  var FIN = {
    "Stainless":      { metal: 1.0, rough: 0.30, handle: [58, 62, 72], guard: "steel", pat: function (u, v) { return STEEL; } },
    "Steel":          { metal: 1.0, rough: 0.30, handle: [150, 158, 172], guard: "steel", pat: function () { return STEEL; }, anod: [170, 178, 192] },
    "Blue steel":     { metal: 1.0, rough: 0.34, handle: [30, 52, 96], guard: "dark", pat: function (u, v) { var n = fbm(u * 6, v * 2, 3, 3); return [40 + n * 40, 70 + n * 50, 130 + n * 60]; } },
    "Fade":           { metal: 0.95, rough: 0.30, handle: [40, 40, 48], guard: "steel", pat: function (u, v) {
      var t = u * 0.9 + (v - 0.5) * 0.35 + (fbm(u * 5, v * 3, 5, 3) - 0.5) * 0.25;
      return pal([[0, 120, 40, 200], [0.30, 205, 60, 190], [0.58, 255, 110, 70], [0.82, 255, 190, 60], [1, 255, 225, 90]], t); } },
    "Slaughter":      { metal: 0.9, rough: 0.34, handle: [60, 22, 18], guard: "dark", pat: function (u, v) {
      var w = fbm(u * 7 + fbm(u * 3, v * 3, 9, 3) * 2.2, v * 3, 11, 4);
      return pal([[0, 35, 8, 10], [0.35, 110, 20, 18], [0.55, 215, 70, 30], [0.75, 245, 140, 50], [1, 60, 14, 12]], w); } },
    "Crimson web":    { metal: 0.9, rough: 0.36, handle: [62, 14, 18], guard: "dark", pat: function (u, v) {
      var x = u * 9, y = v * 3.5, best = 9, second = 9;
      for (var i = -1; i <= 1; i++) for (var j = -1; j <= 1; j++) {
        var cx = Math.floor(x) + i, cy = Math.floor(y) + j, px = cx + hash(cx, cy, 21), py = cy + hash(cx, cy, 22), d = Math.hypot(x - px, y - py);
        if (d < best) { second = best; best = d; } else if (d < second) second = d;
      }
      var e = Math.min(1, (second - best) * 5.5), n = fbm(u * 8, v * 4, 4, 3);
      return [(110 + n * 80) * e + 12, (14 + n * 12) * e + 8, (18 + n * 12) * e + 8]; } },
    "Doppler":        { metal: 0.9, rough: 0.30, handle: [24, 24, 30], guard: "steel", pat: function (u, v) {
      var n = fbm(u * 5 + fbm(u * 2, v * 2, 7, 3) * 2.5, v * 2.5, 13, 4);
      return pal([[0, 16, 16, 28], [0.30, 40, 70, 190], [0.48, 40, 120, 235], [0.62, 220, 60, 170], [0.80, 250, 110, 190], [1, 20, 20, 36]], n); } },
    "Case hardened":  { metal: 1.0, rough: 0.36, handle: [44, 52, 80], guard: "steel", pat: function (u, v) {
      var n = fbm(u * 9 + fbm(u * 4, v * 4, 3, 3) * 2.4, v * 4, 31, 4);
      return pal([[0, 36, 52, 112], [0.34, 70, 100, 170], [0.52, 168, 150, 96], [0.7, 205, 168, 82], [0.86, 90, 96, 150], [1, 30, 40, 96]], n); } },
    "Night":          { metal: 0.8, rough: 0.42, handle: [16, 18, 24], guard: "dark", pat: function (u, v) { var n = fbm(u * 18, v * 7, 5, 3); return [18 + n * 22, 20 + n * 24, 30 + n * 34]; } },
    "Forest":         { metal: 0.6, rough: 0.55, handle: [40, 56, 30], guard: "dark", pat: function (u, v) {
      var n = fbm(u * 7, v * 3.2, 41, 3), m = fbm(u * 10 + 5, v * 4 + 2, 43, 3);
      return n > 0.56 ? [58, 78, 40] : (m > 0.52 ? [34, 46, 28] : [92, 98, 58]); } },
    "Gamma Doppler":  { metal: 0.9, rough: 0.30, handle: [16, 34, 28], guard: "steel", pat: function (u, v) {
      var n = fbm(u * 5 + fbm(u * 2, v * 2, 8, 3) * 2.5, v * 2.5, 51, 4);
      return pal([[0, 10, 30, 28], [0.32, 20, 120, 90], [0.5, 40, 200, 140], [0.7, 120, 240, 150], [0.86, 30, 160, 170], [1, 10, 30, 36]], n); } },
    "Gold":           { metal: 1.0, rough: 0.26, handle: [150, 112, 40], guard: "gold", pat: function (u, v) { var n = fbm(u * 14, v * 2, 2, 2); return [226 + n * 20, 178 + n * 20, 70 + n * 14]; } },
    "Damascus":       { metal: 1.0, rough: 0.38, handle: [48, 44, 40], guard: "steel", pat: function (u, v) {
      var w = Math.sin((v * 22 + fbm(u * 5, v * 3, 61, 3) * 7 + u * 3) * 1.6) * 0.5 + 0.5;
      var c = 70 + w * 120; return [c, c + 4, c + 12]; } },
    "Rainbow":        { metal: 1.0, rough: 0.28, handle: [40, 40, 50], guard: "steel", pat: function (u, v) { return hsv(u * 1.3 + v * 0.25, 0.75, 0.95); } },
    // butterfly: anodised handles, steel blade
    "Crimson anodized": { metal: 1.0, rough: 0.30, handle: [200, 36, 48], guard: "steel", pat: function () { return STEEL; }, anod: [210, 36, 52] },
    "Zeus blue":      { metal: 1.0, rough: 0.28, handle: [44, 110, 230], guard: "steel", pat: function () { return STEEL; }, anod: [46, 112, 232] },
    "Black ops":      { metal: 0.9, rough: 0.40, handle: [34, 37, 44], guard: "dark", pat: function () { return [44, 48, 58]; }, anod: [38, 41, 48] },
    "Neon green":     { metal: 1.0, rough: 0.28, handle: [44, 220, 90], guard: "steel", pat: function () { return STEEL; }, anod: [48, 224, 96] },
    "Violet":         { metal: 1.0, rough: 0.28, handle: [140, 70, 232], guard: "steel", pat: function () { return STEEL; }, anod: [146, 74, 236] },
    "Rose gold":      { metal: 1.0, rough: 0.26, handle: [232, 160, 144], guard: "gold", pat: function () { return [236, 176, 160]; }, anod: [234, 162, 146] },
    "Ice":            { metal: 1.0, rough: 0.22, handle: [160, 228, 248], guard: "steel", pat: function () { return [210, 240, 252]; }, anod: [160, 228, 248] },
    // karambit
    "Voidbloom":      { metal: 0.95, rough: 0.30, handle: [28, 14, 44], guard: "dark", pat: function (u, v) {
      var n = fbm(u * 6 + fbm(u * 3, v * 3, 71, 3) * 2, v * 3, 73, 4);
      return pal([[0, 14, 6, 26], [0.45, 70, 24, 130], [0.7, 40, 190, 190], [1, 12, 6, 24]], n); } },
    "Crimson claw":   { metal: 0.95, rough: 0.32, handle: [96, 14, 20], guard: "dark", pat: function (u, v) {
      var n = fbm(u * 5, v * 3, 75, 4), t = u * 0.8 + n * 0.4;
      return pal([[0, 24, 4, 8], [0.4, 140, 18, 28], [0.75, 230, 60, 44], [1, 40, 6, 10]], t); } },
    "Frost":          { metal: 0.95, rough: 0.26, handle: [150, 196, 230], guard: "steel", pat: function (u, v) {
      var n = fbm(u * 9, v * 4, 77, 4); return pal([[0, 120, 170, 220], [0.55, 200, 232, 250], [1, 250, 254, 255]], n); } },
    "Toxic":          { metal: 0.9, rough: 0.34, handle: [36, 70, 20], guard: "dark", pat: function (u, v) {
      var n = fbm(u * 8 + fbm(u * 3, v * 3, 79, 3) * 2, v * 3, 81, 4);
      return pal([[0, 16, 24, 10], [0.5, 80, 200, 40], [0.8, 190, 255, 60], [1, 14, 20, 8]], n); } },
    "Gold rush":      { metal: 1.0, rough: 0.28, handle: [120, 84, 28], guard: "gold", pat: function (u, v) {
      var n = fbm(u * 12, v * 2.5, 83, 3); return pal([[0, 150, 104, 36], [0.5, 232, 184, 70], [1, 252, 226, 130]], n); } }
  };
  var texCache = {};
  function finishSpec(name) { return FIN[name] || FIN["Stainless"]; }

  function bladeCanvas(name) {
    if (texCache[name]) return texCache[name];
    var W = 512, H = 128, cv = document.createElement("canvas");
    cv.width = W; cv.height = H;
    var ctx = cv.getContext("2d"), img = ctx.createImageData(W, H), d = img.data, spec = finishSpec(name);
    for (var y = 0; y < H; y++) {
      var v = y / (H - 1);
      var brush = 0.94 + 0.10 * vnoise(0, y * 0.9, 7) + 0.04 * vnoise(0, y * 3.1, 8);   // brushed along the blade
      for (var x = 0; x < W; x++) {
        var u = x / (W - 1), c = spec.pat(u, v);
        var g = brush + 0.05 * vnoise(x * 0.35, y * 2.2, 9) - 0.025;
        var bevel = v > 0.93 ? 1.18 : (v < 0.04 ? 0.86 : 1);      // the sharpened edge catches light, the spine is a little darker
        var i = (y * W + x) * 4;
        d[i] = Math.min(255, c[0] * g * bevel); d[i + 1] = Math.min(255, c[1] * g * bevel); d[i + 2] = Math.min(255, c[2] * g * bevel); d[i + 3] = 255;
      }
    }
    ctx.putImageData(img, 0, 0);
    texCache[name] = cv;
    return cv;
  }
  function plainCanvas(rgb, grain, key) {
    var k = "p" + rgb.join(",") + grain + key;
    if (texCache[k]) return texCache[k];
    var S = 64, cv = document.createElement("canvas");
    cv.width = S; cv.height = S;
    var ctx = cv.getContext("2d"), img = ctx.createImageData(S, S), d = img.data;
    for (var y = 0; y < S; y++) for (var x = 0; x < S; x++) {
      var n = 1 + (vnoise(x * 0.5, y * 0.5, 3) - 0.5) * grain + (hash(x, y, 4) - 0.5) * grain * 0.5, i = (y * S + x) * 4;
      d[i] = Math.min(255, rgb[0] * n); d[i + 1] = Math.min(255, rgb[1] * n); d[i + 2] = Math.min(255, rgb[2] * n); d[i + 3] = 255;
    }
    ctx.putImageData(img, 0, 0);
    texCache[k] = cv;
    return cv;
  }
  function cordCanvas(rgb) {
    var k = "c" + rgb.join(",");
    if (texCache[k]) return texCache[k];
    var W = 128, H = 128, cv = document.createElement("canvas");
    cv.width = W; cv.height = H;
    var ctx = cv.getContext("2d"), img = ctx.createImageData(W, H), d = img.data;
    for (var y = 0; y < H; y++) for (var x = 0; x < W; x++) {
      var band = 0.78 + 0.22 * Math.abs(Math.sin((x * 9 + y * 0.5) / W * Math.PI * 9)), n = 0.9 + 0.2 * hash(x, y, 6), i = (y * W + x) * 4;
      d[i] = rgb[0] * band * n; d[i + 1] = rgb[1] * band * n; d[i + 2] = rgb[2] * band * n; d[i + 3] = 255;
    }
    ctx.putImageData(img, 0, 0);
    texCache[k] = cv;
    return cv;
  }

  // ------------------------------------------------------------------------------------------ shaders
  var VS = "attribute vec3 aP;attribute vec3 aN;attribute vec2 aUV;uniform mat4 uM;uniform mat4 uVP;varying vec3 vW;varying vec3 vN;varying vec2 vUV;" +
    "void main(){vec4 w=uM*vec4(aP,1.0);vW=w.xyz;vN=mat3(uM)*aN;vUV=aUV;gl_Position=uVP*w;}";
  var FS = "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n" +
    "varying vec3 vW;varying vec3 vN;varying vec2 vUV;uniform sampler2D uT;uniform vec3 uCam;uniform vec3 uTint;uniform float uMetal;uniform float uRough;uniform float uEdge;" +
    "float lamp(vec3 r,vec3 d,float w,float blur){return smoothstep(w+blur,w,acos(clamp(dot(r,normalize(d)),-1.0,1.0)));}" +
    "float strip(vec3 r,vec3 d,vec3 up,float len,float wid,float blur){vec3 dn=normalize(d);vec3 a=normalize(cross(up,dn));vec3 b=cross(dn,a);" +
    "float f=dot(r,dn);if(f<0.2)return 0.0;vec2 p=vec2(dot(r,a),dot(r,b))/f;return smoothstep(len+blur,len,abs(p.x))*smoothstep(wid+blur,wid,abs(p.y));}" +
    "vec3 env(vec3 r,float rough){float bl=0.03+rough*0.5;" +
    "vec3 sky=mix(vec3(0.020,0.026,0.045),vec3(0.30,0.38,0.58),smoothstep(-0.25,0.95,r.y));" +
    "if(r.y<0.0)sky=mix(vec3(0.020,0.024,0.036),vec3(0.07,0.09,0.14),smoothstep(0.0,-0.8,r.y));" +
    "sky+=vec3(1.6,1.55,1.5)*strip(r,vec3(0.15,0.55,0.8),vec3(1.0,0.0,0.0),0.9,0.10,bl*1.6);" +
    "sky+=vec3(0.9,1.1,1.7)*strip(r,vec3(-0.85,0.15,0.5),vec3(0.0,1.0,0.0),0.07,0.7,bl*1.4);" +
    "sky+=vec3(1.5,1.2,0.9)*strip(r,vec3(0.9,0.1,0.4),vec3(0.0,1.0,0.0),0.06,0.55,bl*1.4);" +
    "sky+=vec3(0.7,0.8,1.0)*lamp(r,vec3(0.0,0.2,-1.0),0.5,bl*2.0)*0.6;return sky;}" +
    "void main(){vec3 n=normalize(vN);vec3 v=normalize(uCam-vW);if(dot(n,v)<0.0)n=-n;vec3 t=texture2D(uT,vUV).rgb;vec3 alb=pow(t,vec3(2.2))*uTint;" +
    "float edge=smoothstep(0.90,1.0,vUV.y)*uEdge;alb*=1.0+edge*0.35;" +
    "vec3 r=reflect(-v,n);vec3 L=normalize(vec3(-0.45,0.75,0.6));float nl=max(dot(n,L),0.0);" +
    "float fr=pow(1.0-max(dot(n,v),0.0),5.0);vec3 f0=mix(vec3(0.04),alb,uMetal);vec3 F=f0+(vec3(1.0)-f0)*fr;" +
    "vec3 diff=alb*(1.0-uMetal)*(0.16+0.95*nl+0.18*max(n.y,0.0));" +
    "vec3 spec=env(r,uRough)*F*mix(1.0,0.85+0.3*(t.g),uMetal);" +
    "vec3 c=diff+spec;c=c/(1.0+c*0.55);c=pow(c,vec3(1.0/2.2));gl_FragColor=vec4(c,1.0);}";
  var SVS = "attribute vec2 aP;uniform mat4 uVP;uniform vec3 uC;uniform float uR;varying vec2 vP;void main(){vP=aP;gl_Position=uVP*vec4(uC.x+aP.x*uR,uC.y,uC.z+aP.y*uR,1.0);}";
  var SFS = "precision mediump float;varying vec2 vP;uniform float uA;void main(){float d=length(vP);gl_FragColor=vec4(0.0,0.0,0.0,uA*smoothstep(1.0,0.15,d));}";

  // ------------------------------------------------------------------------------------------ the viewer
  function supported() {
    try { var c = document.createElement("canvas"); return !!(window.WebGLRenderingContext && (c.getContext("webgl") || c.getContext("experimental-webgl"))); } catch (e) { return false; }
  }

  function create(host, opts) {
    opts = opts || {};
    var canvas = document.createElement("canvas");
    canvas.className = "k3d-canvas";
    canvas.setAttribute("role", "img");
    canvas.style.touchAction = "pan-y";
    host.appendChild(canvas);
    var gl = canvas.getContext("webgl", { antialias: true, alpha: true, powerPreference: "low-power", preserveDrawingBuffer: !!opts.preserve }) || canvas.getContext("experimental-webgl");
    if (!gl) return null;

    function shader(type, src) { var s = gl.createShader(type); gl.shaderSource(s, src); gl.compileShader(s); if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s)); return s; }
    function program(vs, fs) { var p = gl.createProgram(); gl.attachShader(p, shader(gl.VERTEX_SHADER, vs)); gl.attachShader(p, shader(gl.FRAGMENT_SHADER, fs)); gl.linkProgram(p); if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(p)); return p; }
    var prog = program(VS, FS), sprog = program(SVS, SFS);
    var loc = {
      aP: gl.getAttribLocation(prog, "aP"), aN: gl.getAttribLocation(prog, "aN"), aUV: gl.getAttribLocation(prog, "aUV"),
      uM: gl.getUniformLocation(prog, "uM"), uVP: gl.getUniformLocation(prog, "uVP"), uT: gl.getUniformLocation(prog, "uT"), uCam: gl.getUniformLocation(prog, "uCam"),
      uTint: gl.getUniformLocation(prog, "uTint"), uMetal: gl.getUniformLocation(prog, "uMetal"), uRough: gl.getUniformLocation(prog, "uRough"), uEdge: gl.getUniformLocation(prog, "uEdge")
    };
    var sloc = { aP: gl.getAttribLocation(sprog, "aP"), uVP: gl.getUniformLocation(sprog, "uVP"), uC: gl.getUniformLocation(sprog, "uC"), uR: gl.getUniformLocation(sprog, "uR"), uA: gl.getUniformLocation(sprog, "uA") };
    var quad = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, quad);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, 1, 1, -1, -1, 1, 1, -1, 1]), gl.STATIC_DRAW);

    var S = { knife: null, finish: "Stainless", anim: null, t: 0, playing: false, loop: false, speed: 1, rot: true, az: 0.55, el: 0.18, dist: 1, dirty: true, visible: true, drag: false, last: 0, idle: 0, raf: 0 };
    var gpu = null;          // the built knife: parts with buffers

    function texFrom(cv) {
      var t = gl.createTexture();
      gl.bindTexture(gl.TEXTURE_2D, t);
      gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, false);
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, cv);
      var pot = (cv.width & (cv.width - 1)) === 0 && (cv.height & (cv.height - 1)) === 0;
      if (pot) { gl.generateMipmap(gl.TEXTURE_2D); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR_MIPMAP_LINEAR); } else gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
      return t;
    }

    // vertex normals with a crease angle: smooth round handles, crisp blade edges
    function buildMesh(m) {
      var v = m.v, uv = m.u, f = m.f, nt = f.length / 3, fn = new Float32Array(nt * 3), vf = {}, i, k;
      for (i = 0; i < nt; i++) {
        var a = f[i * 3] * 3, b = f[i * 3 + 1] * 3, c = f[i * 3 + 2] * 3;
        var ux = v[b] - v[a], uy = v[b + 1] - v[a + 1], uz = v[b + 2] - v[a + 2], wx = v[c] - v[a], wy = v[c + 1] - v[a + 1], wz = v[c + 2] - v[a + 2];
        var nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx, l = Math.sqrt(nx * nx + ny * ny + nz * nz) || 1;
        fn[i * 3] = nx / l; fn[i * 3 + 1] = ny / l; fn[i * 3 + 2] = nz / l;
        for (k = 0; k < 3; k++) { var id = f[i * 3 + k]; (vf[id] || (vf[id] = [])).push(i); }
      }
      var P = new Float32Array(nt * 9), N = new Float32Array(nt * 9), T = new Float32Array(nt * 6), cosT = Math.cos(34 * Math.PI / 180);
      for (i = 0; i < nt; i++) for (k = 0; k < 3; k++) {
        var id2 = f[i * 3 + k], sx = 0, sy = 0, sz = 0, list = vf[id2];
        for (var j = 0; j < list.length; j++) {
          var q = list[j];
          if (fn[q * 3] * fn[i * 3] + fn[q * 3 + 1] * fn[i * 3 + 1] + fn[q * 3 + 2] * fn[i * 3 + 2] > cosT) { sx += fn[q * 3]; sy += fn[q * 3 + 1]; sz += fn[q * 3 + 2]; }
        }
        var ln = Math.sqrt(sx * sx + sy * sy + sz * sz) || 1, o = (i * 3 + k);
        P[o * 3] = v[id2 * 3]; P[o * 3 + 1] = v[id2 * 3 + 1]; P[o * 3 + 2] = v[id2 * 3 + 2];
        N[o * 3] = sx / ln; N[o * 3 + 1] = sy / ln; N[o * 3 + 2] = sz / ln;
        T[o * 2] = uv[id2 * 2]; T[o * 2 + 1] = uv[id2 * 2 + 1];
      }
      var buf = function (arr) { var b = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, b); gl.bufferData(gl.ARRAY_BUFFER, arr, gl.STATIC_DRAW); return b; };
      return { mat: m.m, p: buf(P), n: buf(N), t: buf(T), count: nt * 3 };
    }

    function disposeGpu() {
      if (!gpu) return;
      gpu.parts.forEach(function (p) { p.meshes.forEach(function (m) { gl.deleteBuffer(m.p); gl.deleteBuffer(m.n); gl.deleteBuffer(m.t); }); });
      Object.keys(gpu.tex).forEach(function (k) { gl.deleteTexture(gpu.tex[k]); });
      gpu = null;
    }

    function buildGpu() {
      disposeGpu();
      if (!S.knife) return;
      var spec = finishSpec(S.finish), tex = {};
      var steel = [200, 206, 216], gold = [226, 182, 80], dark = [52, 56, 66], brass = [190, 150, 70];
      var guardC = spec.guard === "gold" ? gold : spec.guard === "dark" ? dark : steel;
      var handleC = spec.handle;
      var isBF = S.knife.kind === "butterfly";
      var mats = {
        blade: { tex: texFrom(bladeCanvas(S.finish)), tint: [1, 1, 1], metal: spec.metal, rough: spec.rough, edge: 1 },
        guard: { tex: texFrom(plainCanvas(guardC, 0.18, "g")), tint: [1, 1, 1], metal: spec.guard === "dark" ? 0.7 : 1, rough: 0.30, edge: 0 },
        handle: { tex: texFrom(plainCanvas(handleC, isBF || S.knife.kind === "karambit" ? 0.10 : 0.30, "h")), tint: [1, 1, 1], metal: isBF ? 0.95 : 0.15, rough: isBF ? 0.30 : 0.62, edge: 0 },
        pin: { tex: texFrom(plainCanvas(brass, 0.2, "p")), tint: [1, 1, 1], metal: 1, rough: 0.28, edge: 0 },
        cord: { tex: texFrom(cordCanvas(handleC)), tint: [1, 1, 1], metal: 0.0, rough: 0.85, edge: 0 },
        accent: { tex: texFrom(plainCanvas([190, 40, 48], 0.2, "a")), tint: [1, 1, 1], metal: 0.0, rough: 0.8, edge: 0 }
      };
      Object.keys(mats).forEach(function (k) { tex[k] = mats[k].tex; });
      var parts = S.knife.parts.map(function (p) {
        return { n: p.n, p: p.p, par: p.par, meshes: p.meshes.map(function (m) { var g = buildMesh(m); g.mat = mats[m.m] || mats.handle; return g; }) };
      });
      gpu = { parts: parts, tex: tex };
      // framing: a sphere around the knife at rest
      var mn = [1e9, 1e9, 1e9], mx = [-1e9, -1e9, -1e9];
      S.knife.parts.forEach(function (p) { p.meshes.forEach(function (m) { for (var i = 0; i < m.v.length; i += 3) for (var a = 0; a < 3; a++) { mn[a] = Math.min(mn[a], m.v[i + a]); mx[a] = Math.max(mx[a], m.v[i + a]); } }); });
      gpu.center = [(mn[0] + mx[0]) / 2, (mn[1] + mx[1]) / 2, (mn[2] + mx[2]) / 2];
      gpu.radius = Math.max(Math.hypot(mx[0] - mn[0], mx[1] - mn[1], mx[2] - mn[2]) / 2, 6);
      gpu.floor = mn[1];
      S.dirty = true;
    }

    function resize() {
      var r = canvas.getBoundingClientRect(), dpr = Math.min(window.devicePixelRatio || 1, window.innerWidth < 760 ? 1.5 : 2);
      var w = Math.max(2, Math.round(r.width * dpr)), h = Math.max(2, Math.round(r.height * dpr));
      if (canvas.width !== w || canvas.height !== h) { canvas.width = w; canvas.height = h; S.dirty = true; }
    }

    function partMatrices(t) {
      var an = S.anim && S.knife.anims[S.anim], mats = {}, root = ident();
      if (an) {
        var rr = sample3(an.root.rot, t), rp = sample3(an.root.pos, t), pv = an.root.pivot || S.knife.grip;
        root = localM(pv, rr, rp);
      }
      gpu.parts.forEach(function (p) {
        var rot = [0, 0, 0], pos = [0, 0, 0];
        if (an && an.parts[p.n]) { rot = sample3(an.parts[p.n].rot, t); pos = sample3(an.parts[p.n].pos, t); }
        var m = localM(p.p, rot, pos);
        if (p.par && mats[p.par]) m = mul(mats[p.par], m);
        mats[p.n] = m;
      });
      var out = {};
      gpu.parts.forEach(function (p) { out[p.n] = mul(root, mats[p.n]); });
      return out;
    }

    function draw() {
      resize();
      var w = canvas.width, h = canvas.height;
      gl.viewport(0, 0, w, h);
      gl.clearColor(0, 0, 0, 0);
      gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
      if (!gpu) return;
      var R = gpu.radius, c = gpu.center, dist = R * 4.4 * S.dist, ce = Math.cos(S.el), se = Math.sin(S.el);
      var eye = [c[0] + dist * ce * Math.sin(S.az), c[1] + dist * se, c[2] + dist * ce * Math.cos(S.az)];
      var asp = w / h, fov = asp < 0.9 ? 0.62 : 0.5;
      var VP = mul(persp(fov, asp, dist * 0.2, dist * 4), lookAt(eye, c, [0, 1, 0]));
      gl.enable(gl.DEPTH_TEST);
      // soft contact shadow on the floor under the knife
      gl.useProgram(sprog);
      gl.enable(gl.BLEND);
      gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
      gl.disable(gl.CULL_FACE);
      gl.depthMask(false);
      gl.bindBuffer(gl.ARRAY_BUFFER, quad);
      gl.enableVertexAttribArray(sloc.aP);
      gl.vertexAttribPointer(sloc.aP, 2, gl.FLOAT, false, 0, 0);
      gl.uniformMatrix4fv(sloc.uVP, false, VP);
      gl.uniform3f(sloc.uC, c[0], gpu.floor - R * 0.22, c[2]);
      gl.uniform1f(sloc.uR, R * 0.9);
      gl.uniform1f(sloc.uA, 0.5);
      gl.drawArrays(gl.TRIANGLES, 0, 6);
      gl.depthMask(true);
      gl.disable(gl.BLEND);
      gl.disableVertexAttribArray(sloc.aP);
      // the knife
      gl.useProgram(prog);
      gl.enable(gl.CULL_FACE);
      gl.cullFace(gl.BACK);
      gl.uniformMatrix4fv(loc.uVP, false, VP);
      gl.uniform3f(loc.uCam, eye[0], eye[1], eye[2]);
      gl.uniform1i(loc.uT, 0);
      gl.activeTexture(gl.TEXTURE0);
      var M = partMatrices(S.t);
      gl.enableVertexAttribArray(loc.aP); gl.enableVertexAttribArray(loc.aN); gl.enableVertexAttribArray(loc.aUV);
      gpu.parts.forEach(function (p) {
        gl.uniformMatrix4fv(loc.uM, false, M[p.n]);
        p.meshes.forEach(function (m) {
          gl.bindTexture(gl.TEXTURE_2D, m.mat.tex);
          gl.uniform3fv(loc.uTint, m.mat.tint);
          gl.uniform1f(loc.uMetal, m.mat.metal); gl.uniform1f(loc.uRough, m.mat.rough); gl.uniform1f(loc.uEdge, m.mat.edge);
          gl.bindBuffer(gl.ARRAY_BUFFER, m.p); gl.vertexAttribPointer(loc.aP, 3, gl.FLOAT, false, 0, 0);
          gl.bindBuffer(gl.ARRAY_BUFFER, m.n); gl.vertexAttribPointer(loc.aN, 3, gl.FLOAT, false, 0, 0);
          gl.bindBuffer(gl.ARRAY_BUFFER, m.t); gl.vertexAttribPointer(loc.aUV, 2, gl.FLOAT, false, 0, 0);
          gl.drawArrays(gl.TRIANGLES, 0, m.count);
        });
      });
      gl.disableVertexAttribArray(loc.aP); gl.disableVertexAttribArray(loc.aN); gl.disableVertexAttribArray(loc.aUV);
    }

    var onState = null;
    function frame(now) {
      S.raf = 0;
      var dt = Math.min(0.05, (now - (S.last || now)) / 1000);
      S.last = now;
      var animate = false;
      if (S.playing && S.anim) {
        var d = S.knife.anims[S.anim].d;
        S.t += dt * S.speed;
        if (S.t >= d) {
          if (S.loop) S.t -= d; else { S.t = d; S.playing = false; if (onState) onState("end"); }
        }
        animate = true;
        if (onState) onState("tick");
      }
      if (S.rot && !S.drag && !S.playing && !opts.noSpin) { S.az += dt * 0.35; animate = true; }
      if (animate || S.dirty) { draw(); S.dirty = false; }
      if (animate && S.visible && !document.hidden) schedule();
    }
    function schedule() {
      if (S.raf || !S.visible || document.hidden) return;
      S.last = 0;
      S.raf = requestAnimationFrame(frame);
    }
    function kick() { S.dirty = true; if (!S.raf) { S.last = 0; S.raf = requestAnimationFrame(function (t) { S.last = t; frame(t); }); } }

    // input: drag to turn, wheel or pinch to zoom
    var pts = {}, pinch = 0;
    canvas.addEventListener("pointerdown", function (e) { pts[e.pointerId] = [e.clientX, e.clientY]; S.drag = true; try { canvas.setPointerCapture(e.pointerId); } catch (x) { /* ignore */ } });
    canvas.addEventListener("pointermove", function (e) {
      var p = pts[e.pointerId];
      if (!p) return;
      var ids = Object.keys(pts);
      if (ids.length === 2) {
        var o = pts[ids[0] == e.pointerId ? ids[1] : ids[0]], nd = Math.hypot(e.clientX - o[0], e.clientY - o[1]);
        if (pinch) S.dist = Math.min(2.2, Math.max(0.45, S.dist * pinch / nd));
        pinch = nd;
      } else {
        S.az -= (e.clientX - p[0]) * 0.008;
        S.el = Math.min(1.3, Math.max(-0.5, S.el + (e.clientY - p[1]) * 0.006));
      }
      pts[e.pointerId] = [e.clientX, e.clientY];
      kick();
    });
    function up(e) { delete pts[e.pointerId]; pinch = 0; if (!Object.keys(pts).length) { S.drag = false; kick(); } }
    canvas.addEventListener("pointerup", up);
    canvas.addEventListener("pointercancel", up);
    canvas.addEventListener("wheel", function (e) { if (!e.ctrlKey && !e.shiftKey && Math.abs(e.deltaY) < 1) return; e.preventDefault(); S.dist = Math.min(2.2, Math.max(0.45, S.dist * (1 + e.deltaY * 0.001))); kick(); }, { passive: false });
    canvas.addEventListener("dblclick", function () { S.az = 0.55; S.el = 0.18; S.dist = 1; kick(); });
    window.addEventListener("resize", function () { kick(); });
    if (window.IntersectionObserver) {
      new IntersectionObserver(function (es) { S.visible = es[0].isIntersecting; if (S.visible) kick(); }, { threshold: 0.05 }).observe(canvas);
    }
    document.addEventListener("visibilitychange", function () { if (!document.hidden) kick(); });
    canvas.addEventListener("webglcontextlost", function (e) { e.preventDefault(); });

    return {
      canvas: canvas,
      setKnife: function (k) { S.knife = k; S.anim = null; S.t = 0; S.playing = false; buildGpu(); kick(); },
      setFinish: function (name) { if (S.finish === name) return; S.finish = name; buildGpu(); kick(); },
      play: function (name, loop) { if (!S.knife || !S.knife.anims[name]) return; S.anim = name; S.t = 0; S.playing = true; S.loop = !!loop; kick(); },
      pause: function () { S.playing = false; kick(); },
      resume: function () { if (S.anim) { if (S.t >= S.knife.anims[S.anim].d) S.t = 0; S.playing = true; kick(); } },
      seek: function (t) { S.t = t; S.playing = false; kick(); },
      stop: function () { S.anim = null; S.t = 0; S.playing = false; kick(); },
      setLoop: function (b) { S.loop = !!b; },
      setSpeed: function (s) { S.speed = s; },
      setRotate: function (b) { S.rot = !!b; kick(); },
      setView: function (az, el, dist) { S.az = az; S.el = el; if (dist) S.dist = dist; kick(); },
      reset: function () { S.az = 0.55; S.el = 0.18; S.dist = 1; kick(); },
      state: function () { return { anim: S.anim, t: S.t, playing: S.playing, duration: S.anim && S.knife ? S.knife.anims[S.anim].d : 0, loop: S.loop }; },
      onState: function (fn) { onState = fn; },
      redraw: function () { draw(); },
      dispose: function () { disposeGpu(); try { gl.getExtension("WEBGL_lose_context") && gl.getExtension("WEBGL_lose_context").loseContext(); } catch (e) { /* ignore */ } if (canvas.parentNode) canvas.parentNode.removeChild(canvas); }
    };
  }

  var dataPromise = null;
  function load() {
    if (!dataPromise) dataPromise = Z.getJson("/knives3d.json").then(function (d) { var m = {}; d.knives.forEach(function (k) { m[k.id] = k; }); return m; });
    return dataPromise;
  }

  Z.knife3d = { supported: supported, create: create, load: load, finishes: FIN, cardinal: cardinal };
})();
