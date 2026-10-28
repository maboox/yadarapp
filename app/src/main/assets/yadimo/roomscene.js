/* Yadimo's room (soft 2.5D), shared by the in-app room screen (room.html, live) and the home-screen widget
   (roomframes.html, which turns short scenes into pictures for the widget). The room is laid out for the
   widget's size in cells: bigger widgets get more furniture, and six rows make a two-floor house. */
let NIGHT = false;
const CELL = 88;   // one home-screen cell in layout pixels
// ---------------- the room picture ----------------
function lin(g, y0, y1, a, b) { const gr = g.createLinearGradient(0, y0, 0, y1); gr.addColorStop(0, a); gr.addColorStop(1, b); return gr; }
function rr(g, x, y, w, h, r, fill) { g.beginPath(); g.roundRect(x, y, w, h, r); g.fillStyle = fill; g.fill(); }
const P = {
  window(g, x, y, w, h) { const n = NIGHT; rr(g, x - 6, y - 6, w + 12, h + 12, 10, '#fff');
    g.fillStyle = lin(g, y, y + h, n ? '#1a2257' : '#7cc8ff', n ? '#2c2f72' : '#cdeeff'); g.beginPath(); g.roundRect(x, y, w, h, 7); g.fill();
    if (n) { g.fillStyle = '#fff3b8'; g.beginPath(); g.arc(x + w * .72, y + h * .28, h * .13, 0, 7); g.fill(); g.fillStyle = '#1a2257'; g.beginPath(); g.arc(x + w * .67, y + h * .23, h * .12, 0, 7); g.fill();
      g.fillStyle = '#fff'; [[.2, .2], [.35, .6], [.85, .7], [.5, .15]].forEach(([a, b]) => g.fillRect(x + w * a, y + h * b, 1.5, 1.5)); }
    else { g.fillStyle = '#fff'; [[.22, .38, .12], [.34, .34, .09], [.7, .7, .1], [.8, .67, .07]].forEach(([a, b, r]) => { g.beginPath(); g.arc(x + w * a, y + h * b, h * r, 0, 7); g.fill(); }); }
    rr(g, x + w / 2 - 2, y, 4, h, 2, '#fff'); rr(g, x, y + h / 2 - 2, w, 4, 2, '#fff'); rr(g, x - 12, y + h + 4, w + 24, 7, 3, '#c88a52');
    rr(g, x - 16, y - 12, 9, h + 20, 4, '#ff9fbf'); rr(g, x + w + 7, y - 12, 9, h + 20, 4, '#ff9fbf'); rr(g, x - 18, y - 14, w + 36, 6, 3, '#f27aa5'); },
  bed(g, x, f, w) { g.fillStyle = 'rgba(0,0,0,.14)'; g.beginPath(); g.ellipse(x + w / 2, f + 3, w * .55, 6, 0, 0, 7); g.fill();
    rr(g, x, f - 26, w, 18, 6, '#8a5a36'); rr(g, x + 4, f - 10, 7, 10, 2, '#6e4428'); rr(g, x + w - 11, f - 10, 7, 10, 2, '#6e4428');
    rr(g, x + w - 9, f - 56, 9, 50, 5, '#9a6a40'); rr(g, x + 2, f - 36, w - 12, 13, 6, '#f4f1ff');
    g.fillStyle = lin(g, f - 37, f - 23, NIGHT ? '#5565d0' : '#8a97f5', NIGHT ? '#3f4cb0' : '#5f6fe0'); g.beginPath(); g.roundRect(x + 2, f - 37, w - 38, 15, 6); g.fill();
    rr(g, x + w - 34, f - 44, 23, 12, 6, '#ffd96a'); },
  lamp(g, x, f, h = 84) { rr(g, x - 10, f - 5, 20, 5, 3, '#3b2f63'); rr(g, x - 2, f - h, 4, h - 2, 2, '#3b2f63');
    g.fillStyle = lin(g, f - h - 24, f - h, '#ffe08a', '#f5b53a'); g.beginPath(); g.moveTo(x - 13, f - h); g.lineTo(x - 8, f - h - 24); g.lineTo(x + 8, f - h - 24); g.lineTo(x + 13, f - h); g.fill();
    if (NIGHT) { const rg = g.createRadialGradient(x, f - h, 4, x, f - h, 90); rg.addColorStop(0, 'rgba(255,220,120,.35)'); rg.addColorStop(1, 'rgba(255,220,120,0)'); g.fillStyle = rg; g.fillRect(x - 100, f - h - 100, 200, 200); } },
  plant(g, x, f) { g.fillStyle = lin(g, f - 22, f, '#e8885f', '#c4603a'); g.beginPath(); g.moveTo(x - 12, f - 22); g.lineTo(x + 12, f - 22); g.lineTo(x + 9, f); g.lineTo(x - 9, f); g.fill();
    [['#2fa86c', -10, -38, 10], ['#4fd08e', 10, -41, 11], ['#3fbf7f', 0, -50, 12]].forEach(([c, dx, dy, r]) => { g.fillStyle = c; g.beginPath(); g.ellipse(x + dx, f + dy, r * .7, r, dx / 25, 0, 7); g.fill(); }); },
  shelf(g, x, y, w = 66) { rr(g, x, y, w, 5, 3, '#a8713f'); ['#ff8f5e', '#4f9ef0', '#ffd25a', '#36c08f', '#ef7aa8', '#7a5cf0'].slice(0, Math.floor(w / 11)).forEach((c, i) => rr(g, x + 3 + i * 10, y - 20 + (i % 2) * 3, 8, 20 - (i % 2) * 3, 2, c)); },
  picture(g, x, y) { rr(g, x, y, 38, 29, 5, '#ffd25a'); g.fillStyle = lin(g, y, y + 25, NIGHT ? '#2c2f72' : '#9fd8ff', NIGHT ? '#3b3170' : '#d9f2ff'); g.fillRect(x + 4, y + 4, 30, 21);
    g.fillStyle = '#36c08f'; g.beginPath(); g.moveTo(x + 4, y + 25); g.lineTo(x + 18, y + 11); g.lineTo(x + 34, y + 25); g.fill(); },
  clock(g, x, y) { g.fillStyle = '#fff'; g.beginPath(); g.arc(x, y, 13, 0, 7); g.fill(); g.strokeStyle = '#5b4b7a'; g.lineWidth = 2.5; g.stroke();
    g.beginPath(); g.moveTo(x, y); g.lineTo(x, y - 8); g.moveTo(x, y); g.lineTo(x + 6, y + 3); g.stroke(); },
  rug(g, cx, f, w) { g.fillStyle = '#f6a8bf'; g.beginPath(); g.ellipse(cx, f + 12, w / 2, 9, 0, 0, 7); g.fill();
    g.strokeStyle = '#fff8'; g.lineWidth = 2; g.setLineDash([6, 4]); g.beginPath(); g.ellipse(cx, f + 12, w / 2 - 9, 6, 0, 0, 7); g.stroke(); g.setLineDash([]); },
  desk(g, x, f) { rr(g, x, f - 38, 78, 6, 3, '#c9a27a'); rr(g, x + 4, f - 32, 5, 32, 2, '#a8784f'); rr(g, x + 69, f - 32, 5, 32, 2, '#a8784f'); rr(g, x + 46, f - 32, 24, 22, 3, '#a8784f');
    rr(g, x + 14, f - 66, 32, 24, 4, '#2b2f4a'); rr(g, x + 17, f - 63, 26, 18, 3, NIGHT ? '#3b3466' : '#7fd3ff'); rr(g, x + 27, f - 42, 6, 4, 1, '#2b2f4a'); },
  toybox(g, x, f) { rr(g, x, f - 22, 32, 22, 4, '#ffd25a'); rr(g, x - 2, f - 25, 36, 6, 3, '#ff8f5e'); rr(g, x + 12, f - 15, 8, 5, 2, '#ff8f5e'); },
  sofa(g, x, f, w = 96) { rr(g, x, f - 42, w, 22, 9, '#e2689a'); rr(g, x, f - 26, w, 18, 7, '#ef7aa8'); rr(g, x - 6, f - 36, 14, 30, 6, '#e2689a'); rr(g, x + w - 8, f - 36, 14, 30, 6, '#e2689a');
    rr(g, x + 12, f - 40, 22, 14, 6, '#ffd96a'); rr(g, x + 6, f - 8, 5, 8, 2, '#8a5a36'); rr(g, x + w - 11, f - 8, 5, 8, 2, '#8a5a36'); },
  tv(g, x, f) { rr(g, x, f - 24, 80, 24, 4, '#a8784f'); rr(g, x + 6, f - 74, 68, 46, 5, '#2b2f4a'); rr(g, x + 10, f - 70, 60, 38, 3, NIGHT ? '#3b3466' : '#8ad8ff');
    g.fillStyle = '#fff'; g.fillRect(x + 16, f - 64, 14, 4); },
  bowl(g, x, f, food) { g.fillStyle = 'rgba(0,0,0,.15)'; g.beginPath(); g.ellipse(x, f + 3, 19, 4, 0, 0, 7); g.fill();
    g.fillStyle = lin(g, f - 14, f, '#9d86ff', '#6a4ee0'); g.beginPath(); g.moveTo(x - 18, f - 12); g.lineTo(x + 18, f - 12); g.quadraticCurveTo(x + 16, f, x, f); g.quadraticCurveTo(x - 16, f, x - 18, f - 12); g.fill();
    for (let i = 0; i < Math.ceil(food * 3); i++) cookie(g, x - 9 + i * 9, f - 15 + (i % 2) * 2, 6); },
  stairs(g, x, f, top, w) { const n = 9, sh = (f - top) / n, sw = w / n;
    for (let i = 0; i < n; i++) { rr(g, x + i * sw, f - (i + 1) * sh, sw + 2, (i + 1) * sh, 2, i % 2 ? '#b9875c' : '#a8784f'); rr(g, x + i * sw, f - (i + 1) * sh, sw + 2, 3, 1, '#d2a374'); }
    g.strokeStyle = '#7a4f2c'; g.lineWidth = 3; g.beginPath(); g.moveTo(x, f - 26); g.lineTo(x + w, top - 26); g.stroke(); },
};
function cookie(g, x, y, r) { g.fillStyle = '#d0904a'; g.beginPath(); g.arc(x, y, r, 0, 7); g.fill(); g.fillStyle = '#5a3412';
  [[-.4, -.3], [.3, -.1], [-.1, .4]].forEach(([a, b]) => { g.beginPath(); g.arc(x + a * r, y + b * r, r * .17, 0, 7); g.fill(); }); }

/** Lays out a room for a widget of [cols]×[rows] cells: bigger widgets get more furniture. Returns the spots
    Yadimo can stand at, the floor lines and a function that draws the still picture. */
function layout(cols, rows, W, H) {
  const two = rows >= 6, floors = two ? [H - 26, Math.round(H * .5) - 4] : [H - (rows === 1 ? 14 : 26)];
  const f = floors[0], spots = {}, draw = [];
  const wall = (y0, y1) => draw.push(g => { g.fillStyle = lin(g, y0, y1, NIGHT ? '#3b3372' : '#f3ecff', NIGHT ? '#2c2560' : '#ddd0fb'); g.fillRect(0, y0, W, y1 - y0);
    g.fillStyle = NIGHT ? '#ffffff08' : '#ffffff40'; for (let x = 8; x < W; x += 22) g.fillRect(x, y0, 9, y1 - y0); });
  const floor = (y, h) => draw.push(g => { g.fillStyle = lin(g, y, y + h, NIGHT ? '#6b4a33' : '#dba06c', NIGHT ? '#4f3624' : '#b97b47'); g.fillRect(0, y, W, h);
    g.strokeStyle = '#00000018'; g.lineWidth = 1; for (let i = -8; i <= 8; i++) { g.beginPath(); g.moveTo(W / 2 + i * 26, y); g.lineTo(W / 2 + i * 44, y + h); g.stroke(); }
    rr(g, 0, y - 6, W, 7, 0, NIGHT ? '#3a3070' : '#c4b0f2'); });
  if (two) { wall(0, floors[1]); floor(floors[1], 12); wall(floors[1] + 12, f); floor(f, H - f); }
  else { wall(0, f); floor(f, H - f); }
  const D = (fn) => draw.push(fn);

  if (rows === 1) {            // a thin strip: just Yadimo and one sentence
    spots.home = W * .16; return {floors, spots, draw, strip: true};
  }
  if (cols <= 2) {             // 2×2: a cosy corner, Yadimo up close
    D(g => P.window(g, W - 56, 18, 46, 40)); D(g => P.rug(g, W * .5, f, W * .8)); D(g => P.bowl(g, 26, f, food(g)));
    spots.home = W * .55; spots.bowl = 50; return {floors, spots, draw, close: true};
  }
  // four columns wide
  const lower = two ? f - floors[1] - 12 : f, tall = lower > 200;
  D(g => P.window(g, W * .42, (two ? floors[1] + 12 : 0) + Math.max(18, lower * .18), 70, 56));
  D(g => P.rug(g, W * .45, f, 150));
  if (!two) { D(g => P.bed(g, W - 112, f, 106)); spots.bed = W - 60; }
  D(g => P.lamp(g, 20, f)); D(g => P.bowl(g, 58, f, food(g))); spots.bowl = 84;
  spots.home = W * .5; spots.window = W * .45;
  if (rows >= 3) { D(g => P.shelf(g, 14, f - 120)); D(g => P.picture(g, W - 92, f - 132)); if (!two) D(g => P.plant(g, W - 128, f)); }
  if (rows >= 4 && !two) { D(g => P.clock(g, W * .27, 34)); D(g => P.shelf(g, W - 96, 70, 60)); D(g => P.toybox(g, 112, f)); }
  if (two) {                   // the house: bedroom upstairs, living room downstairs, stairs on the right
    const up = floors[1];
    D(g => P.bed(g, 10, up, 106)); D(g => P.desk(g, 146, up)); D(g => P.clock(g, W * .74, 30)); D(g => P.picture(g, W - 70, 22)); D(g => P.window(g, W * .38, 24, 56, 46));
    D(g => P.sofa(g, 92, f, 78)); D(g => P.tv(g, 182, f)); D(g => P.stairs(g, W - 84, f, up + 6, 80));
    spots.bed = 58; spots.bedFloor = 1; spots.sofa = 130;
  }
  return {floors, spots, draw, tall};
}
let currentFood = 0; const food = () => currentFood;


// ---------------- scenes: where everything is at each moment of a short scene ----------------
const ease = t => t < .5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
const clampX = (s, x) => Math.max(26, Math.min(s.W - 26, x));
const SPEED = 120;   // walking speed, layout pixels per second

/** A walk through [stops] ({x, floor}); stairs between floors take a fixed time. Returns {T, at(t)}. */
function route(stops) {
  const legs = [];
  for (let i = 1; i < stops.length; i++) { const a = stops[i - 1], b = stops[i];
    legs.push({a, b, T: a.floor !== b.floor ? 1.3 : Math.max(.15, Math.abs(b.x - a.x) / SPEED)}); }
  const T = legs.reduce((s, l) => s + l.T, 0);
  return {T, at(t) {
    let u = Math.max(0, t);
    for (const l of legs) { if (u <= l.T) { const k = l.T ? u / l.T : 1, e = l.a.floor !== l.b.floor ? k : ease(k);
      return {x: l.a.x + (l.b.x - l.a.x) * e, floor: l.a.floor, climb: l.a.floor !== l.b.floor ? (l.b.floor > l.a.floor ? k : 1 - k) : (l.a.floor ? 1 : 0),
        walking: k > 0 && k < 1, dir: Math.sign(l.b.x - l.a.x) || 1}; } u -= l.T; }
    const z = stops[stops.length - 1]; return {x: z.x, floor: z.floor, climb: z.floor ? 1 : 0, walking: false, dir: 1}; }};
}

class Scene {
  constructor(svg, cols, rows, W, H, look) {
    Object.assign(this, {cols, rows, W, H});
    this.L = layout(cols, rows, W, H);
    this.y = new Yadimo(svg, look); this.y.nextAct = 1e9;   // calm: no random hops in the pictures
    this.size = rows === 1 ? Math.min(70, H - 6) : this.L.close ? Math.min(150, H * .78) : 108;
    this.home = {x: this.L.spots.home, floor: 0};
    this.hasBed = this.L.spots.bed !== undefined;
    this.bed = this.hasBed ? {x: this.L.spots.bed, floor: this.L.spots.bedFloor || 0} : this.home;
  }
  floorY(fl) { return this.L.floors[fl] ?? this.L.floors[0]; }
  path(a, b) {
    if (a.floor === b.floor) return route([a, b]);
    const s = {x: this.W - 84, floor: 0}, top = {x: this.W - 4, floor: 1};
    return a.floor === 0 ? route([a, s, top, b]) : route([a, top, s, b]);
  }
  /** Length of a scene in seconds. */
  duration(kind) { return this.script(kind).T; }
  /** The scene [kind] as {T, at(t)} → state of the room at time t. */
  script(kind) {
    const H = this.home, base = {x: H.x, floor: 0, climb: 0, walking: false, dir: 1, lying: 0, mood: NIGHT ? 'sleep' : 'idle'};
    const at = (st, extra) => Object.assign({}, base, st, extra);
    if (this.L.strip) kind = kind === 'pet' ? 'wave' : 'idle';
    switch (kind) {
      case 'idle': case 'wave': return {T: 2, at: t => at({}, {mood: kind === 'wave' ? 'wave' : 'idle'})};
      case 'sleep': return {T: 2, at: t => this.hasBed ? at(this.bed, {climb: this.bed.floor ? 1 : 0, lying: 1, mood: 'sleep', z: true}) : at({}, {mood: 'sleep', z: true})};
      case 'charge': return {T: 2.2, at: t => at({}, {mood: 'celebrate', charge: t / 2.2, flash: t < .22, sparks: true})};
      case 'pet': return {T: 2, at: t => at({}, {mood: 'love', pat: true})};
      case 'feed': {
        const B = {x: (this.L.spots.bowl ?? 50) + 26, floor: 0}, go = this.path(H, B), back = this.path(B, H), eat = 1.4, T = go.T + eat + back.T;
        return {T, at: t => {
          const cookieY = t < .6 ? (1 - t / .6) : null, food = t < .6 ? 0 : t < go.T + eat ? Math.max(0, 1 - Math.max(0, t - go.T) / eat) : 0;
          if (t < go.T) return at(go.at(t), {cookieY, food, mood: 'idle'});
          if (t < go.T + eat) return at(B, {food, mood: 'love', crumbs: true, dir: -1});
          return at(back.at(t - go.T - eat), {mood: 'idle'});
        }};
      }
      case 'play': {
        const side = H.x + 160 < this.W ? 1 : -1, b1 = clampX(this, H.x + side * 60), b2 = clampX(this, H.x + side * 170), b3 = clampX(this, H.x - side * 30);
        const k1 = this.path(H, {x: b1 - side * 26, floor: 0}), k2 = this.path({x: b1 - side * 26, floor: 0}, {x: b2 - side * 26, floor: 0}), k3 = this.path({x: b2 - side * 26, floor: 0}, H);
        const t1 = Math.max(.7, k1.T), t2 = t1 + .6 + k2.T, T = t2 + .7 + Math.max(.6, k3.T);
        const arc = (x0, x1, h, u) => ({x: x0 + (x1 - x0) * u, y: 4 * h * u * (1 - u)});
        return {T, at: t => {
          let ball;
          if (t < .6) ball = {x: b1, y: 150 * (1 - t / .6) * (1 - t / .6)}; else if (t < t1) ball = {x: b1, y: 0};
          else if (t < t1 + .6) ball = arc(b1, b2, 60, (t - t1) / .6); else if (t < t2) ball = {x: b2, y: 0};
          else if (t < t2 + .7) ball = arc(b2, b3, 70, (t - t2) / .7); else ball = {x: b3, y: 0, fade: Math.min(1, (t - t2 - .7) / .5)};
          const kick = Math.abs(t - t1) < .25 || Math.abs(t - t2) < .25;
          const pos = t < t1 ? k1.at(t) : t < t2 ? k2.at(t - t1 - .2) : k3.at(t - t2 - .2);
          return at(pos, {ball, mood: kick || t > T - .5 ? 'celebrate' : 'idle'});
        }};
      }
      case 'bed': {
        if (!this.hasBed) return {T: .6, at: t => at({}, {mood: 'sleep', z: t > .3})};
        const go = this.path(H, this.bed), T = go.T + .7;
        return {T, at: t => t < go.T ? at(go.at(t), {mood: 'idle'}) : at(this.bed, {climb: this.bed.floor ? 1 : 0, lying: Math.min(1, (t - go.T) / .6), mood: 'sleep'})};
      }
      case 'stroll': {   // to one of the room's spots, a look around, and back
        const keys = Object.keys(this.L.spots).filter(k => !['home', 'bed', 'bedFloor'].includes(k));
        if (!keys.length) return {T: 2, at: () => at({}, {mood: 'wave'})};
        const to = {x: this.L.spots[keys[Math.floor(Math.random() * keys.length)]] + (Math.random() - .5) * 20, floor: 0};
        const go = this.path(H, to), back = this.path(to, H), T = go.T + 1.6 + back.T;
        return {T, at: t => t < go.T ? at(go.at(t)) : t < go.T + 1.6 ? at(to) : at(back.at(t - go.T - 1.6))};
      }
      case 'wake': {
        if (!this.hasBed) return {T: .6, at: t => at({}, {mood: 'wave'})};
        const back = this.path(this.bed, H), T = back.T + .7;
        return {T, at: t => t < .7 ? at(this.bed, {climb: this.bed.floor ? 1 : 0, lying: 1 - t / .7, mood: 'wave'}) : at(back.at(t - .7), {mood: 'idle'})};
      }
    }
    return {T: 2, at: () => at({})};
  }
  /** Moves the character along to state [st]; call often (about 60 times a second). */
  drive(st, t, dt) {
    const y = this.y;
    if (y.mood !== st.mood) y.setMood(st.mood);
    y.posY.t = st.walking ? -Math.abs(Math.sin(t * 11)) * 42 : 0;
    y.o.follow = st.walking; y.pointer.active = st.walking; y.pointer.x = st.walking ? st.dir * .9 : 0; y.pointer.y = .1;
    y.update(dt);
  }
  /** Where the character's middle is, and its tilt in degrees (lying on the bed: head on the pillow). */
  place(st) {
    const f = this.floorY(st.floor), fl = st.climb && this.L.floors[1] !== undefined ? this.floorY(0) - (this.floorY(0) - this.L.floors[1]) * st.climb : f;
    const stand = this.L.strip ? this.H - this.size * .5 : fl - this.size * .42;
    const lie = this.floorY(this.bed.floor) - 28;
    // lying down: turned so the head is on the pillow (the right end of the bed), body over the mattress
    return {x: st.x - this.size * .12 * (st.lying || 0), y: stand + (lie - stand) * (st.lying || 0), angle: (st.lying || 0) * 77};
  }
  /** Things around the character: cookie, ball, battery, hand, sparks, z's. */
  drawProps(g, st, t, p) {
    const f0 = this.floorY(0);
    if (st.cookieY != null) cookie(g, (this.L.spots.bowl ?? 50), f0 - 14 - st.cookieY * this.H * .6, 8);
    if (st.food) { currentFood = st.food; P.bowl(g, (this.L.spots.bowl ?? 50) - (this.L.close ? 24 : 26), f0, st.food); }
    if (st.ball) { const b = st.ball, by = f0 - b.y - 11; g.globalAlpha = 1 - (b.fade || 0);
      g.fillStyle = 'rgba(0,0,0,.18)'; g.beginPath(); g.ellipse(b.x, f0 + 2, 11, 3, 0, 0, 7); g.fill();
      const rg = g.createRadialGradient(b.x - 3, by - 4, 2, b.x, by, 12); rg.addColorStop(0, '#ff9a9a'); rg.addColorStop(1, '#e24848'); g.fillStyle = rg; g.beginPath(); g.arc(b.x, by, 11, 0, 7); g.fill();
      g.strokeStyle = '#fff'; g.lineWidth = 2.5; g.beginPath(); g.moveTo(b.x - 11, by); g.quadraticCurveTo(b.x, by + 6, b.x + 11, by); g.stroke(); g.globalAlpha = 1; }
    if (st.charge != null) { const x = p.x, top = p.y - this.size * .55; rr(g, x - 16, top, 32, 15, 4, '#fff'); rr(g, x - 13, top + 3, 26 * st.charge, 9, 3, '#36c08f'); rr(g, x + 16, top + 4, 3, 7, 1, '#fff'); }
    if (st.sparks) for (let i = 0; i < 6; i++) { const a = i * 1.7 + t * 5, r = 30 + 14 * Math.sin(t * 7 + i); g.fillStyle = '#ffe14a';
      g.beginPath(); g.arc(p.x + Math.cos(a) * r, p.y + Math.sin(a) * r * .8, 2.5, 0, 7); g.fill(); }
    if (st.crumbs) for (let i = 0; i < 3; i++) { g.fillStyle = '#c8873f'; g.beginPath(); g.arc(p.x - 24 + i * 6, f0 - 6 - ((t * 60 + i * 13) % 14), 2, 0, 7); g.fill(); }
    if (st.pat) { g.font = '26px serif'; g.textAlign = 'center'; g.fillText('🤚', p.x, p.y - this.size * .3 + Math.abs(Math.sin(t * 10)) * 8); g.textAlign = 'start'; }
    if (st.z) { g.fillStyle = '#7a62e0'; g.font = 'bold 14px system-ui'; const k = (t * .8) % 1; g.globalAlpha = 1 - k;
      g.fillText('z', p.x + 18, p.y - 20 - k * 22); g.font = 'bold 10px system-ui'; g.fillText('z', p.x + 30, p.y - 34 - k * 18); g.globalAlpha = 1; }
    if (st.flash) { g.fillStyle = 'rgba(255,240,120,.3)'; g.fillRect(0, 0, this.W, this.H); }
  }
  drawRoom(g) { currentFood = 0; this.L.draw.forEach(d => d(g)); if (NIGHT) { g.fillStyle = 'rgba(10,6,30,.25)'; g.fillRect(0, 0, this.W, this.H); } }
}
