/* Puts accessories in 2.5D: every shape of a layer is turned into an absolute path once ("baked", including any
   transforms), then every frame each point goes through the same projection as the head or face it sits on.
   So a mask, a hat or a beard narrows, shifts and leans with the head instead of staying a flat sticker. */
const Warp = (() => {
  const NS = 'http://www.w3.org/2000/svg', K = 0.5522847498;
  const num = (el, a) => parseFloat(el.getAttribute(a) || '0');

  // SVG path data -> absolute segments: M, L, C, Q, Z (arcs become cubic curves)
  function parse(d) {
    const tk = d.match(/[MmLlHhVvCcSsQqTtAaZz]|[-+]?(?:\d*\.\d+|\d+\.?)(?:e[-+]?\d+)?/g) || [];
    const out = []; let i = 0, cmd = '', x = 0, y = 0, sx = 0, sy = 0, lc = null, lq = null;
    const n = () => parseFloat(tk[i++]);
    while (i < tk.length) {
      if (/[a-z]/i.test(tk[i])) cmd = tk[i++];
      const rel = cmd === cmd.toLowerCase(), C = cmd.toUpperCase(), ox = rel ? x : 0, oy = rel ? y : 0;
      if (C === 'Z') { out.push({c: 'Z'}); x = sx; y = sy; lc = lq = null; continue; }
      if (C === 'M') { x = ox + n(); y = oy + n(); sx = x; sy = y; out.push({c: 'M', p: [x, y]}); cmd = rel ? 'l' : 'L'; lc = lq = null; continue; }
      if (C === 'L') { x = ox + n(); y = oy + n(); out.push({c: 'L', p: [x, y]}); lc = lq = null; continue; }
      if (C === 'H') { x = ox + n(); out.push({c: 'L', p: [x, y]}); lc = lq = null; continue; }
      if (C === 'V') { y = oy + n(); out.push({c: 'L', p: [x, y]}); lc = lq = null; continue; }
      if (C === 'C' || C === 'S') {
        let x1, y1;
        if (C === 'C') { x1 = ox + n(); y1 = oy + n(); } else { x1 = lc ? 2 * x - lc[0] : x; y1 = lc ? 2 * y - lc[1] : y; }
        const x2 = ox + n(), y2 = oy + n(), ex = ox + n(), ey = oy + n();
        out.push({c: 'C', p: [x1, y1, x2, y2, ex, ey]}); lc = [x2, y2]; lq = null; x = ex; y = ey; continue;
      }
      if (C === 'Q' || C === 'T') {
        let qx, qy;
        if (C === 'Q') { qx = ox + n(); qy = oy + n(); } else { qx = lq ? 2 * x - lq[0] : x; qy = lq ? 2 * y - lq[1] : y; }
        const ex = ox + n(), ey = oy + n();
        out.push({c: 'Q', p: [qx, qy, ex, ey]}); lq = [qx, qy]; lc = null; x = ex; y = ey; continue;
      }
      if (C === 'A') {
        const rx = n(), ry = n(), rot = n(), large = n(), sweep = n(), ex = ox + n(), ey = oy + n();
        for (const s of arc(x, y, rx, ry, rot, large, sweep, ex, ey)) out.push({c: 'C', p: s});
        x = ex; y = ey; lc = lq = null; continue;
      }
      i++;   // unknown token
    }
    return out;
  }
  // endpoint arc -> cubic curves (standard conversion)
  function arc(x1, y1, rx, ry, phi, fa, fs, x2, y2) {
    if (!rx || !ry) return [[x1, y1, x2, y2, x2, y2]];
    const p = phi * Math.PI / 180, cp = Math.cos(p), sp = Math.sin(p);
    const dx = (x1 - x2) / 2, dy = (y1 - y2) / 2, x1p = cp * dx + sp * dy, y1p = -sp * dx + cp * dy;
    rx = Math.abs(rx); ry = Math.abs(ry);
    const lam = x1p * x1p / (rx * rx) + y1p * y1p / (ry * ry); if (lam > 1) { rx *= Math.sqrt(lam); ry *= Math.sqrt(lam); }
    let co = Math.sqrt(Math.max(0, (rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p) / (rx * rx * y1p * y1p + ry * ry * x1p * x1p)));
    if (fa == fs) co = -co;
    const cxp = co * rx * y1p / ry, cyp = -co * ry * x1p / rx, cx = cp * cxp - sp * cyp + (x1 + x2) / 2, cy = sp * cxp + cp * cyp + (y1 + y2) / 2;
    const ang = (ux, uy, vx, vy) => { const a = Math.atan2(ux * vy - uy * vx, ux * vx + uy * vy); return a; };
    let t1 = ang(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry), dt = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry);
    if (!fs && dt > 0) dt -= 2 * Math.PI; if (fs && dt < 0) dt += 2 * Math.PI;
    const segs = Math.ceil(Math.abs(dt) / (Math.PI / 2)), d = dt / segs, k = 4 / 3 * Math.tan(d / 4), out = [];
    const pt = (t) => [cx + rx * Math.cos(t) * cp - ry * Math.sin(t) * sp, cy + rx * Math.cos(t) * sp + ry * Math.sin(t) * cp];
    const dv = (t) => [-rx * Math.sin(t) * cp - ry * Math.cos(t) * sp, -rx * Math.sin(t) * sp + ry * Math.cos(t) * cp];
    for (let s = 0; s < segs; s++) {
      const a = t1 + s * d, b = a + d, p0 = pt(a), p3 = pt(b), d0 = dv(a), d3 = dv(b);
      out.push([p0[0] + k * d0[0], p0[1] + k * d0[1], p3[0] - k * d3[0], p3[1] - k * d3[1], p3[0], p3[1]]);
    }
    return out;
  }
  function ellipse(cx, cy, rx, ry) {
    const a = rx * K, b = ry * K;
    return [{c: 'M', p: [cx + rx, cy]}, {c: 'C', p: [cx + rx, cy + b, cx + a, cy + ry, cx, cy + ry]}, {c: 'C', p: [cx - a, cy + ry, cx - rx, cy + b, cx - rx, cy]},
      {c: 'C', p: [cx - rx, cy - b, cx - a, cy - ry, cx, cy - ry]}, {c: 'C', p: [cx + a, cy - ry, cx + rx, cy - b, cx + rx, cy]}, {c: 'Z'}];
  }
  function shape(el) {
    switch (el.tagName.toLowerCase()) {
      case 'path': return parse(el.getAttribute('d') || '');
      case 'circle': return ellipse(num(el, 'cx'), num(el, 'cy'), num(el, 'r'), num(el, 'r'));
      case 'ellipse': return ellipse(num(el, 'cx'), num(el, 'cy'), num(el, 'rx'), num(el, 'ry'));
      case 'line': return [{c: 'M', p: [num(el, 'x1'), num(el, 'y1')]}, {c: 'L', p: [num(el, 'x2'), num(el, 'y2')]}];
      case 'polygon': case 'polyline': {
        const v = (el.getAttribute('points') || '').trim().split(/[\s,]+/).map(Number), o = [];
        for (let i = 0; i + 1 < v.length; i += 2) o.push({c: i ? 'L' : 'M', p: [v[i], v[i + 1]]});
        if (el.tagName.toLowerCase() === 'polygon') o.push({c: 'Z'}); return o;
      }
      case 'rect': {
        const x = num(el, 'x'), y = num(el, 'y'), w = num(el, 'width'), h = num(el, 'height');
        let r = Math.min(num(el, 'rx') || num(el, 'ry'), w / 2, h / 2); const k = r * (1 - K);
        if (!r) return [{c: 'M', p: [x, y]}, {c: 'L', p: [x + w, y]}, {c: 'L', p: [x + w, y + h]}, {c: 'L', p: [x, y + h]}, {c: 'Z'}];
        return [{c: 'M', p: [x + r, y]}, {c: 'L', p: [x + w - r, y]}, {c: 'C', p: [x + w - k, y, x + w, y + k, x + w, y + r]},
          {c: 'L', p: [x + w, y + h - r]}, {c: 'C', p: [x + w, y + h - k, x + w - k, y + h, x + w - r, y + h]},
          {c: 'L', p: [x + r, y + h]}, {c: 'C', p: [x + k, y + h, x, y + h - k, x, y + h - r]},
          {c: 'L', p: [x, y + r]}, {c: 'C', p: [x, y + k, x + k, y, x + r, y]}, {c: 'Z'}];
      }
    }
    return null;
  }
  function matrixTo(el, root) {
    let m = new DOMMatrix();
    for (let n = el; n && n !== root; n = n.parentNode) {
      const t = n.transform && n.transform.baseVal && n.transform.baseVal.consolidate();
      if (t) { const s = t.matrix; m = new DOMMatrix([s.a, s.b, s.c, s.d, s.e, s.f]).multiply(m); }
    }
    return m;
  }
  const GEOM = new Set(['d', 'cx', 'cy', 'r', 'rx', 'ry', 'x', 'y', 'width', 'height', 'points', 'x1', 'y1', 'x2', 'y2', 'transform']);
  /* Turn every shape under root into an absolute path in root's coordinates. Returns what to warp each frame. */
  function bake(root) {
    const items = [];
    for (const el of [...root.querySelectorAll('path,circle,ellipse,rect,polygon,polyline,line')]) {
      const segs = shape(el); if (!segs) continue;
      const m = matrixTo(el, root);
      for (const s of segs) if (s.p) for (let i = 0; i < s.p.length; i += 2) { const q = m.transformPoint({x: s.p[i], y: s.p[i + 1]}); s.p[i] = q.x; s.p[i + 1] = q.y; }
      const p = document.createElementNS(NS, 'path');
      for (const a of [...el.attributes]) if (!GEOM.has(a.name)) p.setAttribute(a.name, a.value);
      // strokes keep their thickness after baking a scale
      const sc = Math.sqrt(Math.abs(m.a * m.d - m.b * m.c));
      if (p.hasAttribute('stroke-width') && Math.abs(sc - 1) > .01) p.setAttribute('stroke-width', String(parseFloat(p.getAttribute('stroke-width')) * sc));
      p.setAttribute('d', toD(segs)); el.replaceWith(p); items.push({el: p, segs});
    }
    for (const g of root.querySelectorAll('[transform]')) g.removeAttribute('transform');
    return items;
  }
  const f = v => Math.round(v * 10) / 10;
  function toD(segs, map) {
    let s = '';
    for (const g of segs) {
      if (g.c === 'Z') { s += 'Z'; continue; }
      s += g.c;
      for (let i = 0; i < g.p.length; i += 2) { const q = map ? map(g.p[i], g.p[i + 1]) : [g.p[i], g.p[i + 1]]; s += f(q[0]) + ' ' + f(q[1]) + ' '; }
    }
    return s;
  }
  function apply(items, map) { for (const it of items) it.el.setAttribute('d', toD(it.segs, map)); }
  return {bake, apply};
})();
