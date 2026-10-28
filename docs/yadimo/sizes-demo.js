/* Yadimo's room widget in the soft 2.5D style, at every widget size, the way the real widget would work:
   1) a still picture of the room (redrawn only when something in it changes),
   2) Yadimo on top, as a short loop at the chosen frame rate,
   3) a short animation for each button (food, ball, charge, pat, sleep), then back to the loop.
   Yadimo stays at a few spots (window, bowl, bed…) and moves between them only inside those short animations. */
const $ = id => document.getElementById(id);
const rnd = (a, b) => a + Math.random() * (b - a);
const NS = 'http://www.w3.org/2000/svg';
let FPS = 30, NIGHT = false, LOOK = {variant: 'robot', color: 'lilac', hat: 'party', glass: 'sun'};
const CELL = 88;   // one home-screen cell in CSS pixels

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

// ---------------- one widget ----------------
const LINES2 = ['اتاقم کوچیکه! 😅', 'یه کم جا بده! 🙏', 'منم خونهٔ بزرگ‌تر می‌خوام 🏠'];
const LINES = ['امروز ۳ تا کار داری 💪', 'سیرم، مرسی! 😋', 'حوصله‌م سر رفت، بازی؟ ⚽', 'چه روز خوبیه ☀️'];
class Widget {
  constructor(host, cols, rows) {
    this.cols = cols; this.rows = rows; this.W = cols * CELL - 8; this.H = rows * CELL - 8;
    const dpr = Math.min(2, devicePixelRatio || 1);
    host.style.width = this.W + 'px'; host.style.height = this.H + 'px';
    host.innerHTML = `<canvas class="bg"></canvas><canvas class="fx"></canvas><svg class="yad" viewBox="0 0 600 600"></svg><div class="bubble"></div>` +
      (rows === 1 ? '' : `<div class="wbtns"><button data-a="feed">🍪</button><button data-a="play">⚽</button><button data-a="charge">⚡</button><button data-a="pet">🤚</button><button data-a="sleep">😴</button></div>`);
    [this.bg, this.fx] = host.querySelectorAll('canvas');
    for (const c of [this.bg, this.fx]) { c.width = this.W * dpr; c.height = this.H * dpr; c.getContext('2d').setTransform(dpr, 0, 0, dpr, 0, 0); }
    this.svgEl = host.querySelector('svg.yad'); this.bubble = host.querySelector('.bubble');
    this.y = new Yadimo(this.svgEl, LOOK);
    this.L = layout(cols, rows, this.W, this.H);
    this.size = rows === 1 ? 70 : this.L.close ? 150 : 108;
    this.svgEl.style.width = this.svgEl.style.height = this.size + 'px';
    this.s = {x: this.L.spots.home, floor: 0, from: null, to: null, p: 1, mode: 'idle', lying: 0, t: 0, wait: rnd(4, 8)};
    this.food = 0; this.ball = null; this.cookie = null; this.charge = 0; this.pat = 0; this.acc = 1; this.dirty = true; this.lineT = 0;
    host.querySelectorAll('[data-a]').forEach(b => b.onclick = e => { e.stopPropagation(); this.act(b.dataset.a); });
    host.onclick = () => this.act('pet');
    if (this.L.strip) this.say('امروز ۳ تا کار داری؛ اولی ساعت ۱۰ 💪', 1e9);
  }
  say(t, s = 2.6) { this.bubble.textContent = t; this.bubble.classList.add('on'); this.lineT = s; }
  moveTo(x, floor = this.s.floor, then) { const s = this.s; s.from = {x: s.x, floor: s.floor}; s.to = {x, floor}; s.p = 0; s.mode = 'walk'; s.then = then; }
  act(k) {
    const s = this.s;
    if (k === 'pet') { this.pat = 2; this.y.setMood('love'); this.say('آخ جون! 🥰'); return; }
    if (this.L.strip) { this.y.setMood('wave'); return; }
    if (s.mode === 'sleep') { s.mode = 'idle'; }
    if (k === 'feed') { this.cookie = {y: 1}; this.say('غذا! 🍪'); }
    if (k === 'play') { this.ball = {x: s.x + (Math.random() < .5 ? -50 : 50), y: 1, vx: 0, vy: 0, kicks: 0, t: 0}; this.say('توپ! ⚽'); }
    if (k === 'charge') { this.charge = 2.2; this.flash = .25; this.y.setMood('celebrate'); this.say('شارژ شدم! ⚡'); }
    if (k === 'sleep') { this.ball = null; this.cookie = null; this.goSleep(); }
  }
  goSleep() { const sp = this.L.spots; if (sp.bed === undefined) { this.s.mode = 'nap'; this.y.setMood('sleep'); this.say('یه چرت کوچیک… 😴', 2); return; }
    this.moveTo(sp.bed, sp.bedFloor || 0, () => { this.s.mode = 'sleep'; this.y.setMood('sleep'); this.say('شب بخیر… 😴', 2); }); }
  floorY(fl) { return this.L.floors[fl] ?? this.L.floors[0]; }
  /** The world moves on every screen frame… */
  world(dt) {
    const s = this.s; s.t += dt;
    if (this.lineT > 0 && (this.lineT -= dt) <= 0) this.bubble.classList.remove('on');
    if (this.cookie) { this.cookie.y -= dt * 1.6; if (this.cookie.y <= 0) { this.cookie = null; this.food = 1; this.dirty = true;
      if (this.L.spots.bowl !== undefined) this.moveTo(this.L.spots.bowl + 26, 0, () => { s.mode = 'eat'; s.eat = 2.4; this.y.setMood('love'); }); } }
    if (s.mode === 'eat') { this.food = Math.max(0, this.food - dt / 2.4); this.dirty = true; if ((s.eat -= dt) <= 0) { s.mode = 'idle'; s.wait = rnd(4, 8); this.say('سیرم، مرسی! 😋'); this.y.setMood('idle'); } }
    if (this.ball) { const b = this.ball, f = this.floorY(s.floor); b.t += dt; b.vy -= 900 * dt; b.y = Math.max(0, b.y === 1 ? 140 : b.y) ; b.y += b.vy * dt;
      if (b.y <= 0) { b.y = 0; b.vy = Math.abs(b.vy) > 60 ? -b.vy * .5 : 0; } b.x += b.vx * dt; b.vx *= 1 - dt * 1.2;
      b.x = Math.max(16, Math.min(this.W - 16, b.x));
      if (s.mode !== 'walk' && Math.abs(b.x - s.x) > 34 && b.y < 30) this.moveTo(b.x + (b.x > s.x ? -28 : 28));
      if (Math.abs(b.x - s.x) < 34 && b.y < 20 && b.t > .6 && s.mode !== 'walk') { b.vx = (b.x > s.x ? 1 : -1) * rnd(140, 220); b.vy = rnd(260, 360); b.t = 0; b.kicks++; this.y.setMood('celebrate');
        if (b.kicks >= 4) { setTimeout(() => { this.ball = null; this.say('خیلی خوش گذشت! 😄'); this.y.setMood('idle'); }, 1400); b.kicks = -99; } } }
    this.charge = Math.max(0, this.charge - dt); this.pat = Math.max(0, this.pat - dt); this.flash = Math.max(0, (this.flash || 0) - dt);
    if (this.pat <= 0 && this.charge <= 0 && !this.ball && s.mode === 'idle' && this.y.mood !== 'idle' && this.y.mood !== 'talk') this.y.setMood(NIGHT ? 'sleep' : 'idle');
    if (s.mode === 'walk') { s.p = Math.min(1, s.p + dt * (s.from.floor !== s.to.floor ? .45 : 1.1));
      if (s.from.floor !== s.to.floor) {   // through the stairs: across, then up
        const stairX = this.W - 84, k = s.p; s.x = k < .4 ? s.from.x + (stairX - s.from.x) * (k / .4) : k < .8 ? stairX + 80 * ((k - .4) / .4) : stairX + 80 + (s.to.x - stairX - 80) * ((k - .8) / .2);
        s.climb = k < .4 ? 0 : k < .8 ? (k - .4) / .4 : 1; }
      else s.x = s.from.x + (s.to.x - s.from.x) * (1 - Math.pow(1 - s.p, 2));
      if (s.p >= 1) { s.floor = s.to.floor; s.climb = 0; s.mode = 'idle'; s.wait = rnd(5, 9); const t = s.then; s.then = null; if (t) t(); } }
    s.lying += ((s.mode === 'sleep' ? 1 : 0) - s.lying) * Math.min(1, dt * 5);
    if (s.mode === 'idle' && !this.ball && !this.cookie && (s.wait -= dt) <= 0) {   // now and then: another spot, a line, or a nap
      s.wait = rnd(6, 11);
      if (NIGHT) { this.goSleep(); return; }
      const r = Math.random();
      if (this.L.close) { if (r < .5) this.say(LINES2[Math.floor(rnd(0, 3))]); else this.y.setMood('wave'); }
      else if (r < .15) this.goSleep();
      else { const keys = Object.keys(this.L.spots).filter(k => !['bed', 'bedFloor'].includes(k)); const k = keys[Math.floor(rnd(0, keys.length))];
        this.moveTo(this.L.spots[k] + rnd(-10, 10), 0); if (Math.random() < .3) this.say(LINES[Math.floor(rnd(0, LINES.length))]); }
    }
    if (s.mode === 'sleep' && !NIGHT && s.t % 1 < dt && Math.random() < .08) { s.mode = 'idle'; this.y.setMood('idle'); this.say('صبح بخیر! ☀️'); }
    if (s.mode === 'nap' && Math.random() < dt / 5) { s.mode = 'idle'; this.y.setMood('idle'); }
  }
  /** …but the widget only shows a new picture [FPS] times a second. */
  frame(dt) {
    const s = this.s, y = this.y;
    const walking = s.mode === 'walk';
    y.posY.t = walking ? -Math.abs(Math.sin(s.t * 11)) * 42 : 0; y.o.follow = walking; y.pointer.active = walking;
    y.pointer.x = walking ? Math.sign(s.to.x - s.from.x) * .9 : 0; y.pointer.y = .1;
    y.update(dt);
    // where the character stands (or lies) in the widget
    const f = this.floorY(s.floor), fl = s.climb ? f - (f - this.L.floors[1]) * s.climb : f;
    const lieY = (this.L.spots.bedFloor ? this.L.floors[1] : f) - 40;
    const cy = this.L.strip ? this.H - this.size * .5 : fl - this.size * .42 + (lieY - (fl - this.size * .42) + 12) * s.lying;
    this.svgEl.style.transform = `translate(${(s.x - this.size / 2).toFixed(1)}px, ${(cy - this.size * .5).toFixed(1)}px) rotate(${(s.lying * 77).toFixed(1)}deg)`;
    if (this.dirty) { currentFood = this.food; const g = this.bg.getContext('2d'); g.clearRect(0, 0, this.W, this.H); this.L.draw.forEach(d => d(g));
      if (NIGHT) { g.fillStyle = 'rgba(10,6,30,.25)'; g.fillRect(0, 0, this.W, this.H); } this.dirty = false; }
    const g = this.fx.getContext('2d'); g.clearRect(0, 0, this.W, this.H);
    if (this.cookie) cookie(g, (this.L.spots.bowl ?? 50), this.floorY(0) - 14 - this.cookie.y * (this.H * .7), 8);
    if (this.ball) { const b = this.ball, by = this.floorY(s.floor) - b.y - 11; g.fillStyle = 'rgba(0,0,0,.18)'; g.beginPath(); g.ellipse(b.x, this.floorY(s.floor) + 2, 11, 3, 0, 0, 7); g.fill();
      const rg = g.createRadialGradient(b.x - 3, by - 4, 2, b.x, by, 12); rg.addColorStop(0, '#ff9a9a'); rg.addColorStop(1, '#e24848'); g.fillStyle = rg; g.beginPath(); g.arc(b.x, by, 11, 0, 7); g.fill();
      g.strokeStyle = '#fff'; g.lineWidth = 2.5; g.beginPath(); g.moveTo(b.x - 11, by); g.quadraticCurveTo(b.x, by + 6, b.x + 11, by); g.stroke(); }
    if (this.charge > 0) { const x = s.x, top = cy - this.size * .45; rr(g, x - 16, top, 32, 15, 4, '#fff'); rr(g, x - 13, top + 3, 26 * (1 - this.charge / 2.2), 9, 3, '#36c08f'); rr(g, x + 16, top + 4, 3, 7, 1, '#fff');
      for (let i = 0; i < 5; i++) { g.fillStyle = '#ffe14a'; g.beginPath(); g.arc(x + rnd(-40, 40), cy + rnd(-30, 30), rnd(1.5, 3.5), 0, 7); g.fill(); } }
    if (this.pat > 0) { g.font = '26px serif'; g.fillText('🤚', s.x - 13, cy - this.size * .32 + Math.abs(Math.sin(s.t * 10)) * 8); }
    if (this.flash > 0) { g.fillStyle = 'rgba(255,240,120,.3)'; g.fillRect(0, 0, this.W, this.H); }
  }
  tick(dt) { this.world(dt); this.acc += dt; if (this.acc >= 1 / FPS) { this.frame(this.acc); this.acc = 0; } }
}

// ---------------- the phones ----------------
const widgets = [];
function phone(title, rows) {
  const card = document.createElement('div'); card.className = 'phone-wrap';
  card.innerHTML = `<div class="ptitle">${title}</div><div class="phone"><div class="status">۱۰:۰۸</div><div class="home"></div><div class="dock">${'<i></i>'.repeat(4)}</div></div>`;
  const home = card.querySelector('.home');
  for (const row of rows) {
    const r = document.createElement('div'); r.className = 'row';
    for (const item of row) {
      if (item === 'app') { r.insertAdjacentHTML('beforeend', `<div class="app"><i></i><span>برنامه</span></div>`); continue; }
      const [c, h, label] = item; const w = document.createElement('div'); w.className = 'widget'; w.title = label;
      r.append(w); const tag = document.createElement('div'); tag.className = 'wtag'; tag.textContent = label;
      widgets.push(new Widget(w, c, h)); w.append(tag);
    }
    home.append(r);
  }
  $('phones').append(card);
}
phone('۴×۱ و ۲×۲ و ۴×۲', [[[4, 1, '۴×۱']], [[2, 2, '۲×۲'], 'app', 'app'], [[4, 2, '۴×۲']]]);
phone('۴×۳', [[[4, 3, '۴×۳']], ['app', 'app', 'app', 'app']]);
phone('۴×۴', [[[4, 4, '۴×۴']]]);
phone('تمام صفحه: خانهٔ دوطبقه', [[[4, 6, '۴×۶']]]);

document.querySelectorAll('#fps button').forEach(b => b.onclick = () => { FPS = +b.dataset.f; document.querySelectorAll('#fps button').forEach(x => x.classList.toggle('on', x === b)); });
document.querySelectorAll('#look button').forEach(b => b.onclick = () => { LOOK = JSON.parse(b.dataset.l); widgets.forEach(w => w.y.set(LOOK)); document.querySelectorAll('#look button').forEach(x => x.classList.toggle('on', x === b)); });
document.querySelectorAll('#all [data-a]').forEach(b => b.onclick = () => widgets.forEach(w => w.act(b.dataset.a)));
$('night').onclick = () => { NIGHT = !NIGHT; $('night').textContent = NIGHT ? '☀️ روز' : '🌙 شب'; document.body.classList.toggle('night', NIGHT);
  widgets.forEach(w => { w.dirty = true; if (NIGHT) w.goSleep(); else { w.s.mode = 'idle'; w.y.setMood('idle'); } }); };
let last = performance.now();
(function loop(now) { const dt = Math.max(0, Math.min(.05, (now - last) / 1000)); last = now; widgets.forEach(w => w.tick(dt)); requestAnimationFrame(loop); })(last);
