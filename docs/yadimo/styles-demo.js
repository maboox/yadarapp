/* Three art styles for Yadimo's room widget, side by side. One little world (Yadimo, ball, food, bed…) is simulated
   once; three renderers draw the same moment: detailed pixel art, blocky "Minecraft-like", and soft 2.5D.
   Yadimo itself comes from the real vector character, so hats, colours and costumes always match the app. */
const $ = id => document.getElementById(id);
const rand = (a, b) => a + Math.random() * (b - a);

// ---------------- the world (x from 0 to 1 across the room, y = height above the floor in room heights) -------
const BED = {x0: .72, x1: .96}, BOWL = .2, LAMP = .06, PLANT = .62;
const W = {
  night: false, t: 0,
  yad: {x: .45, tx: .45, face: 1, mode: 'idle', timer: 2, lift: 0, lying: 0},
  ball: null,                 // {x, y, vx, vy}
  cookie: null,               // {y, vy} falling into the bowl
  food: 0,                    // 0..1 in the bowl
  charge: 0,                  // effect time left
  pet: 0, bolt: 0, crumbs: [], sparks: [],
  stats: {hunger: 70, energy: 60, joy: 75},
  line: '', lineT: 0,
};
const Y = () => W.yad;
function say(t, s = 2.6) { W.line = t; W.lineT = s; }
function walkTo(x, then) { const y = Y(); y.tx = clamp(x, .08, .92); y.mode = 'walk'; y.then = then; }

const ACT = {
  feed() { if (W.food > 0 || W.cookie) return; W.cookie = {y: 1.1, vy: 0}; say('غذا! 🍪'); },
  play() { W.ball = {x: rand(.25, .65), y: 1.05, vx: rand(-.15, .15), vy: 0, kicks: 0}; say('توپ! ⚽'); Y().mode = 'chase'; },
  charge() { W.charge = 2.4; W.bolt = .25; Y().mode = 'charge'; Y().timer = 2.4; say('شارژ شدم! ⚡'); W.stats.energy = 100; },
  pet() { W.pet = 2.2; Y().mode = 'pet'; Y().timer = 2.2; say('آخ جون! 🥰'); W.stats.joy = Math.min(100, W.stats.joy + 15); },
  sleep() { walkTo((BED.x0 + BED.x1) / 2, () => { Y().mode = 'sleep'; Y().timer = W.night ? 1e9 : 9; say('شب بخیر… 😴', 2); }); },
};

function updateWorld(dt) {
  W.t += dt; const y = Y();
  for (const k of ['hunger', 'energy', 'joy']) W.stats[k] = Math.max(0, W.stats[k] - dt * .35);
  if (W.lineT > 0) W.lineT -= dt;
  W.charge = Math.max(0, W.charge - dt); W.pet = Math.max(0, W.pet - dt); W.bolt = Math.max(0, W.bolt - dt);
  // cookie falls into the bowl, then Yadimo goes to eat
  if (W.cookie) { const c = W.cookie; c.vy -= 3 * dt; c.y += c.vy * dt;
    if (c.y <= .06) { W.cookie = null; W.food = 1; for (let i = 0; i < 6; i++) W.crumbs.push({x: BOWL, y: .07, vx: rand(-.15, .15), vy: rand(.3, .7), life: .6});
      walkTo(BOWL + .07, () => { y.mode = 'eat'; y.timer = 2.6; }); } }
  // ball physics
  if (W.ball) { const b = W.ball; b.vy -= 3.2 * dt; b.x += b.vx * dt; b.y += b.vy * dt;
    if (b.y < .05) { b.y = .05; b.vy = Math.abs(b.vy) > .25 ? -b.vy * .55 : 0; b.vx *= .9; }
    if (b.x < .05 || b.x > .95) { b.vx = -b.vx; b.x = clamp(b.x, .05, .95); }
    b.vx *= 1 - dt * .4; }
  for (const c of W.crumbs) { c.vy -= 3 * dt; c.x += c.vx * dt; c.y += c.vy * dt; c.life -= dt; }
  W.crumbs = W.crumbs.filter(c => c.life > 0 && c.y > 0);
  if (W.charge > 0 && Math.random() < dt * 18) W.sparks.push({x: y.x + rand(-.08, .08), y: rand(.1, .5), life: .5});
  for (const s of W.sparks) { s.y += dt * .3; s.life -= dt; } W.sparks = W.sparks.filter(s => s.life > 0);

  // Yadimo's behaviour
  y.lift += ((y.mode === 'sleep' ? .16 : 0) - y.lift) * Math.min(1, dt * 6);
  y.lying += ((y.mode === 'sleep' ? 1 : 0) - y.lying) * Math.min(1, dt * 5);
  if (y.mode === 'walk' || y.mode === 'chase') {
    const target = y.mode === 'chase' && W.ball ? W.ball.x - .06 * Math.sign(W.ball.x - y.x || 1) : y.tx;
    const d = target - y.x;
    if (Math.abs(d) > .01) { y.x += Math.sign(d) * Math.min(Math.abs(d), .22 * dt); y.face = Math.sign(d); }
    else if (y.mode === 'walk') { y.mode = 'idle'; y.timer = rand(1.5, 4); const f = y.then; y.then = null; if (f) f(); }
    if (y.mode === 'chase' && W.ball && Math.abs(W.ball.x - y.x) < .09 && W.ball.y < .2 && W.t - (W.ball.kickedAt || -9) > 1.1) {     // kick!
      const b = W.ball; b.vx = (b.x > y.x ? 1 : -1) * rand(.35, .55); b.vy = rand(.8, 1.3); b.kicks++; b.kickedAt = W.t;
      W.stats.joy = Math.min(100, W.stats.joy + 6);
      if (b.kicks >= 5) { y.mode = 'idle'; y.timer = 2; say('خیلی خوش گذشت! 😄'); setTimeout(() => W.ball = null, 2500); }
    }
  } else if (y.mode === 'eat') {
    W.food = Math.max(0, W.food - dt / 2.6);
    if (Math.random() < dt * 8) W.crumbs.push({x: y.x - .04 * y.face, y: .12, vx: rand(-.1, .1), vy: rand(.2, .4), life: .5});
    if ((y.timer -= dt) <= 0) { y.mode = 'idle'; y.timer = 2; W.stats.hunger = 100; say('سیرم، مرسی! 😋'); }
  } else if (y.mode === 'sleep') {
    if (!W.night && (y.timer -= dt) <= 0) { y.mode = 'idle'; y.timer = 1.5; W.stats.energy = 100; say('صبح بخیر! ☀️'); }
  } else if (y.mode === 'charge' || y.mode === 'pet') {
    if ((y.timer -= dt) <= 0) { y.mode = 'idle'; y.timer = 2; }
  } else if ((y.timer -= dt) <= 0) {       // idle: wander, and now and then take a nap
    if (W.night) { ACT.sleep(); return; }
    if (W.stats.energy < 25 || Math.random() < .12) ACT.sleep();
    else { walkTo(rand(.12, .66)); if (Math.random() < .2) say(['امروز ۳ تا کار داری 💪', 'چه اتاق قشنگی! ✨', 'حوصله‌م سر رفت، بازی؟ ⚽'][Math.floor(rand(0, 3))]); }
  }
}

// ---------------- Yadimo: one vector character, snapshotted for the pixel views ----------------
const NS = 'http://www.w3.org/2000/svg';
const hidden = document.createElementNS(NS, 'svg');
hidden.setAttribute('viewBox', '0 0 600 600'); hidden.setAttribute('xmlns', NS);
hidden.style.cssText = 'position:fixed;left:0;top:0;width:600px;height:600px;opacity:0;pointer-events:none;z-index:-1';
document.body.append(hidden);
let look = {variant: 'robot', color: 'lilac', hat: 'party', glass: 'sun'};
const yad = new Yadimo(hidden, look);
let lastMood = '';
function driveCharacter(dt) {
  const y = Y();
  const mood = {walk: 'idle', chase: 'idle', eat: 'love', sleep: 'sleep', charge: 'celebrate', pet: 'love'}[y.mode] || (W.lineT > 0 ? 'talk' : 'idle');
  if (mood !== lastMood) { yad.setMood(mood); lastMood = mood; }
  const moving = y.mode === 'walk' || y.mode === 'chase';
  yad.posY.t = moving ? -Math.abs(Math.sin(W.t * 10)) * 45 : 0;
  yad.o.follow = moving; yad.pointer.active = moving; yad.pointer.x = y.face * .9; yad.pointer.y = .1;
  yad.update(dt);
}
const ser = new XMLSerializer();
let charImg = null, charBusy = false;
function snapshot(done) {
  if (charBusy) return; charBusy = true;
  const m = ser.serializeToString(hidden).replace(/^<svg[^>]*?style="[^"]*"/, s => s.replace(/style="[^"]*"/, 'width="600" height="600"'));
  const img = new Image(); img.onload = () => { charImg = img; charBusy = false; done(); }; img.onerror = () => { charBusy = false; };
  img.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(m);
}
/** Shrinks the character to [size] pixels: clean edges, full colour, and a dark outline around the outside. */
function pixelSprite(size) {
  const c = document.createElement('canvas'); c.width = c.height = size;
  const g = c.getContext('2d'); g.imageSmoothingQuality = 'high';
  // two steps down keeps small features (eyes, glasses, hat stripes) from disappearing
  const mid = document.createElement('canvas'); mid.width = mid.height = size * 2; mid.getContext('2d').drawImage(charImg, 0, 0, size * 2, size * 2);
  g.drawImage(mid, 0, 0, size, size);
  const d = g.getImageData(0, 0, size, size), p = d.data, solid = new Uint8Array(size * size);
  for (let i = 0; i < size * size; i++) { if (p[i * 4 + 3] < 120) { p[i * 4 + 3] = 0; continue; } solid[i] = 1; p[i * 4 + 3] = 255; }
  // drop the soft floor shadow (very dark, very transparent in the original), keep everything else
  const out = new Uint8ClampedArray(p);
  for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
    const i = y * size + x; if (solid[i]) continue;
    if ((x && solid[i - 1]) || (x < size - 1 && solid[i + 1]) || (y && solid[i - size]) || (y < size - 1 && solid[i + size])) { out.set([38, 26, 66, 255], i * 4); }
  }
  g.putImageData(new ImageData(out, size, size), 0, 0);
  return c;
}

// ---------------- shared drawing helpers ----------------
/** Where Yadimo's middle is: standing on the floor, or lying on the mattress while asleep. */
const mixY = (stand, lie) => stand + (lie - stand) * Y().lying;
function canvasIn(host, w, h) {
  host.innerHTML = `<canvas width="${w}" height="${h}"></canvas><div class="bubble"></div><div class="hud"></div>`;
  const cv = host.querySelector('canvas'); const g = cv.getContext('2d'); g.imageSmoothingEnabled = false;
  return {g, bubble: host.querySelector('.bubble'), hud: host.querySelector('.hud')};
}
function hud(v) {
  v.bubble.textContent = W.line; v.bubble.classList.toggle('on', W.lineT > 0);
  const s = W.stats, bar = (e, n) => `<span>${e}<i><b style="width:${n.toFixed(0)}%"></b></i></span>`;
  v.hud.innerHTML = bar('🍪', s.hunger) + bar('⚡', s.energy) + bar('😊', s.joy);
}

// ======================= 1) detailed pixel art (240 × 120) =======================
class PixelView {
  constructor(host) { this.w = 240; this.h = 120; this.floor = 104; Object.assign(this, canvasIn(host, 240, 120)); }
  room(g) {
    const r = (x, y, w, h, c) => { g.fillStyle = c; g.fillRect(x, y, w, h); }, n = W.night, w = this.w, f = this.floor;
    r(0, 0, w, f, n ? '#4a3f86' : '#ece2ff');
    for (let x = 0; x < w; x += 12) { r(x, 0, 6, f, n ? '#463b80' : '#e3d6fd'); r(x + 2, 6, 2, 2, n ? '#544a92' : '#d6c6fb'); r(x + 8, 18, 2, 2, n ? '#544a92' : '#d6c6fb'); }
    r(0, f - 5, w, 5, n ? '#3c3270' : '#b9a3ec'); r(0, f - 5, w, 1, n ? '#5b4f99' : '#d3c3f7');
    r(0, f, w, 16, n ? '#6b4a33' : '#c98d5b');
    for (let x = 0; x < w; x += 20) { r(x + (x / 20 % 2) * 10, f + 1, 1, 7, n ? '#5a3d2a' : '#a8703f'); r(x, f + 8, 1, 8, n ? '#5a3d2a' : '#a8703f'); }
    r(0, f + 7, w, 1, n ? '#5a3d2a' : '#b07849'); r(0, f, w, 1, n ? '#7d5a40' : '#dda26e');
    // window
    const wx = 98, wy = 16; r(wx - 3, wy - 3, 46, 38, '#fff'); r(wx, wy, 40, 32, n ? '#1b2456' : '#8fd3ff'); r(wx, wy, 40, 10, n ? '#25307a' : '#b9e6ff');
    if (n) { r(wx + 28, wy + 5, 6, 6, '#fff3b8'); r(wx + 30, wy + 5, 4, 2, '#1b2456'); [[6, 6], [16, 18], [33, 22], [10, 26]].forEach(([a, b]) => r(wx + a, wy + b, 1, 1, '#fff')); }
    else { r(wx + 5, wy + 12, 10, 3, '#fff'); r(wx + 7, wy + 10, 6, 2, '#fff'); r(wx + 24, wy + 20, 9, 3, '#fff'); r(wx + 26, wy + 18, 4, 2, '#fff'); }
    r(wx + 19, wy, 2, 32, '#fff'); r(wx, wy + 15, 40, 2, '#fff'); r(wx - 5, wy + 33, 50, 3, '#a8713f'); r(wx - 5, wy + 33, 50, 1, '#c88a52');
    r(wx - 9, wy - 6, 6, 44, '#ff9fbf'); r(wx + 43, wy - 6, 6, 44, '#ff9fbf'); r(wx - 9, wy - 6, 58, 3, '#f27aa5');
    // shelf with books
    r(40, 34, 40, 3, '#8a5a36'); ['#ff8f5e', '#4f9ef0', '#ffd25a', '#36c08f', '#ef7aa8', '#7a5cf0', '#ff6b6b'].forEach((c, i) => { r(42 + i * 5, 23 + (i % 3), 4, 11 - (i % 3), c); r(42 + i * 5, 25, 4, 1, '#0002'); });
    r(70, 26, 6, 8, '#3fbf7f'); r(71, 30, 4, 4, '#d9774f');
    // picture and clock
    r(160, 22, 22, 16, '#ffd25a'); r(162, 24, 18, 12, n ? '#3b3170' : '#9fd8ff'); r(165, 30, 6, 6, '#36c08f'); r(170, 28, 8, 8, '#2fa86c'); r(175, 26, 3, 3, '#fff3b8');
    r(200, 14, 12, 12, '#fff'); r(201, 13, 10, 14, '#fff'); r(205, 16, 2, 5, '#3b2f63'); r(206, 20, 4, 1, '#3b2f63'); r(200, 14, 12, 1, '#7a5cf0');
    // lamp
    const lx = Math.round(LAMP * w); r(lx - 4, f - 3, 10, 3, '#3b2f63'); r(lx, f - 40, 2, 37, '#3b2f63'); r(lx - 8, f - 52, 18, 12, '#ffcf5c'); r(lx - 6, f - 54, 14, 2, '#ffe08a'); r(lx - 8, f - 41, 18, 1, '#e8a93a');
    if (n) { g.fillStyle = 'rgba(255,220,120,.16)'; g.beginPath(); g.arc(lx + 1, f - 40, 34, 0, 7); g.fill(); }
    // plant
    const px = Math.round(PLANT * w); r(px - 6, f - 12, 13, 12, '#d9774f'); r(px - 7, f - 13, 15, 3, '#e8885f'); r(px - 3, f - 26, 7, 14, '#3fbf7f'); r(px - 9, f - 22, 6, 7, '#2fa86c'); r(px + 4, f - 24, 6, 8, '#4fd08e'); r(px - 1, f - 30, 4, 5, '#3fbf7f');
    // rug
    r(70, f + 2, 70, 4, '#f6a8bf'); r(74, f + 3, 62, 1, '#ffd0dc');
    // bed
    const bx = Math.round(BED.x0 * w), bw = Math.round((BED.x1 - BED.x0) * w);
    r(bx, f - 16, bw, 10, '#8a5a36'); r(bx, f - 6, 4, 6, '#6e4428'); r(bx + bw - 4, f - 6, 4, 6, '#6e4428'); r(bx + bw - 5, f - 34, 5, 28, '#8a5a36'); r(bx + bw - 5, f - 34, 5, 2, '#a8713f');
    r(bx + 2, f - 22, bw - 7, 6, '#f4f1ff'); r(bx + 2, f - 16, bw - 7, 1, '#d6d0f0');
    r(bx + 2, f - 22, bw - 26, 7, n ? '#5565d0' : '#6f7fe8'); r(bx + 2, f - 22, bw - 26, 1, '#8f9cf5');
    r(bx + bw - 22, f - 27, 15, 6, '#ffd96a'); r(bx + bw - 22, f - 27, 15, 1, '#fff0a8');
    // bowl
    const ox = Math.round(BOWL * w); r(ox - 9, f - 7, 18, 7, '#7a5cf0'); r(ox - 10, f - 8, 20, 2, '#9d86ff');
    if (W.food > 0) { const k = Math.ceil(W.food * 3); for (let i = 0; i < k; i++) { r(ox - 7 + i * 5, f - 11, 5, 4, '#c8873f'); r(ox - 6 + i * 5, f - 10, 1, 1, '#5a3412'); r(ox - 4 + i * 5, f - 9, 1, 1, '#5a3412'); } }
  }
  draw() {
    const g = this.g, w = this.w, f = this.floor; g.clearRect(0, 0, w, this.h); this.room(g);
    const r = (x, y, ww, hh, c) => { g.fillStyle = c; g.fillRect(Math.round(x), Math.round(y), ww, hh); };
    if (W.cookie) { const x = BOWL * w, y = f - W.cookie.y * 100; r(x - 4, y - 4, 8, 8, '#c8873f'); r(x - 2, y - 2, 1, 1, '#5a3412'); r(x + 1, y, 1, 1, '#5a3412'); }
    for (const c of W.crumbs) r(c.x * w, f - c.y * 100, 2, 2, '#c8873f');
    if (W.ball) { const x = W.ball.x * w, y = f - W.ball.y * 100; r(x - 5, f - 1, 10, 2, '#0003'); r(x - 4, y - 9, 8, 10, '#ff6b6b'); r(x - 5, y - 8, 10, 8, '#ff6b6b'); r(x - 5, y - 5, 10, 2, '#fff'); r(x - 2, y - 8, 2, 2, '#ffd0d0'); }
    if (charImg) {
      const s = 64, y = Y(), sp = pixelSprite(s), cx = y.x * w, cy = mixY(f - s * .42, f - 22 - s * .2);
      g.save(); g.translate(Math.round(cx), Math.round(cy)); if (y.lying > .02) g.rotate(-y.lying * 1.35);
      g.drawImage(sp, -s / 2, -s * .46); g.restore();
    }
    for (const s of W.sparks) r(s.x * w, f - s.y * 100 - 30, 2, 2, '#ffe14a');
    if (W.charge > 0) { const x = Y().x * w, y = f - 80; r(x - 7, y, 14, 8, '#fff'); r(x - 6, y + 1, Math.round(12 * (1 - W.charge / 2.4)), 6, '#36c08f'); r(x + 7, y + 2, 2, 4, '#fff'); }
    if (W.pet > 0) { const x = Y().x * w, y = f - 78 + Math.abs(Math.sin(W.t * 10)) * 5; r(x - 6, y, 12, 8, '#ffd0b0'); r(x - 6, y - 4, 3, 5, '#ffd0b0'); r(x - 2, y - 5, 3, 6, '#ffd0b0'); r(x + 2, y - 4, 3, 5, '#ffd0b0'); }
    if (W.bolt > 0) { g.fillStyle = 'rgba(255,240,120,.35)'; g.fillRect(0, 0, w, this.h); }
    if (Y().mode === 'sleep') { const x = Y().x * w + 14, y = f - 50; g.fillStyle = '#7a62e0'; g.font = 'bold 9px monospace'; g.fillText('z', x, y - (W.t * 8 % 10)); g.fillText('Z', x + 6, y - 8 - (W.t * 8 % 10)); }
    if (W.night) { g.fillStyle = 'rgba(14,8,40,.28)'; g.fillRect(0, 0, w, this.h); }
    hud(this);
  }
}

// ======================= 2) blocky, Minecraft-like (10 × 5 blocks of 16 px) =======================
class BlockView {
  constructor(host) { this.w = 160; this.h = 80; this.floor = 64; Object.assign(this, canvasIn(host, 160, 80)); this.tex = this.makeTextures(); }
  makeTextures() {
    const seed = (s => () => (s = (s * 9301 + 49297) % 233280) / 233280)(42);
    const T = (draw) => { const c = document.createElement('canvas'); c.width = c.height = 16; const g = c.getContext('2d'); draw(g, (x, y, w, h, col) => { g.fillStyle = col; g.fillRect(x, y, w, h); }); return c; };
    const noise = (r, base, cols, n = 60) => { r(0, 0, 16, 16, base); for (let i = 0; i < n; i++) r(Math.floor(seed() * 16), Math.floor(seed() * 16), 1, 1, cols[Math.floor(seed() * cols.length)]); };
    return {
      plank: T((g, r) => { noise(r, '#b8945f', ['#a8844f', '#c4a06a', '#9c7a48']); [3, 7, 11, 15].forEach(y => r(0, y, 16, 1, '#8a6a3c')); r(5, 0, 1, 3, '#8a6a3c'); r(12, 4, 1, 3, '#8a6a3c'); r(3, 8, 1, 3, '#8a6a3c'); r(10, 12, 1, 3, '#8a6a3c'); }),
      dark: T((g, r) => { noise(r, '#6b4a2b', ['#5e4026', '#7a5634', '#4f3520']); [3, 7, 11, 15].forEach(y => r(0, y, 16, 1, '#3f2a17')); r(8, 0, 1, 3, '#3f2a17'); r(2, 4, 1, 3, '#3f2a17'); r(12, 8, 1, 3, '#3f2a17'); r(6, 12, 1, 3, '#3f2a17'); }),
      glass: T((g, r) => { r(0, 0, 16, 16, '#cfefff'); r(0, 0, 16, 1, '#fff'); r(0, 0, 1, 16, '#fff'); r(15, 0, 1, 16, '#9cc8e0'); r(0, 15, 16, 1, '#9cc8e0'); r(3, 3, 2, 2, '#fff'); r(5, 5, 2, 2, '#fff'); }),
      books: T((g, r) => { r(0, 0, 16, 16, '#b8945f'); r(0, 0, 16, 2, '#8a6a3c'); r(0, 7, 16, 2, '#8a6a3c'); r(0, 14, 16, 2, '#8a6a3c');
        ['#c0392b', '#2e86de', '#27ae60', '#f1c40f', '#8e44ad', '#e67e22'].forEach((c, i) => { r(1 + i * 2.5, 2, 2, 5, c); r(1 + i * 2.5, 9, 2, 5, ['#2e86de', '#c0392b', '#f1c40f', '#27ae60', '#e67e22', '#8e44ad'][i]); }); }),
      wool: T((g, r) => noise(r, '#c0392b', ['#a93226', '#d14836'], 40)),
      white: T((g, r) => noise(r, '#eeeeee', ['#dddddd', '#ffffff'], 40)),
      lantern: T((g, r) => { r(5, 3, 6, 11, '#3a3a3a'); r(6, 5, 4, 7, '#ffcf5c'); r(7, 6, 2, 5, '#fff3b0'); r(7, 0, 2, 3, '#3a3a3a'); r(4, 13, 8, 2, '#3a3a3a'); }),
      pot: T((g, r) => { r(4, 9, 8, 7, '#a0522d'); r(3, 9, 10, 2, '#b5653a'); r(7, 2, 2, 7, '#2e7d32'); r(4, 3, 3, 3, '#43a047'); r(9, 4, 3, 3, '#43a047'); r(6, 0, 4, 3, '#e91e63'); }),
      craft: T((g, r) => { noise(r, '#9c7a48', ['#8a6a3c', '#b8945f']); r(0, 0, 16, 4, '#7a5a30'); r(2, 6, 5, 4, '#555'); r(9, 6, 5, 6, '#8a6a3c'); r(10, 7, 3, 1, '#ccc'); }),
    };
  }
  draw() {
    const g = this.g, t = this.tex, n = W.night, w = this.w, f = this.floor; g.clearRect(0, 0, w, this.h);
    const B = (tex, bx, by) => g.drawImage(tex, bx * 16, by * 16);
    for (let x = 0; x < 10; x++) { for (let y = 0; y < 4; y++) B(t.plank, x, y); B(t.dark, x, 4); }
    // window 2×2 with sky behind
    g.fillStyle = n ? '#14204e' : '#7cc8ff'; g.fillRect(64, 8, 32, 24); if (n) { g.fillStyle = '#fff6c8'; g.fillRect(84, 12, 6, 6); }
    g.globalAlpha = .55; for (let x = 4; x < 6; x++) for (let y = 0; y < 2; y++) g.drawImage(t.glass, x * 16, 8 + y * 12, 16, 12); g.globalAlpha = 1;
    g.fillStyle = '#5e4026'; g.fillRect(62, 6, 36, 2); g.fillRect(62, 32, 36, 2); g.fillRect(62, 6, 2, 28); g.fillRect(96, 6, 2, 28); g.fillRect(79, 6, 2, 28);
    B(t.books, 2, 1); B(t.books, 2, 2); B(t.books, 3, 2);
    B(t.lantern, Math.round(LAMP * 10), 3); B(t.pot, Math.round(PLANT * 10), 3); B(t.craft, 1, 3);
    // bed: two blocks wide, red wool with a white pillow
    const bx = Math.round(BED.x0 * w), bw = Math.round((BED.x1 - BED.x0) * w); g.drawImage(t.wool, bx, f - 9, bw - 8, 9); g.drawImage(t.white, bx + bw - 10, f - 9, 8, 9);
    g.fillStyle = '#5e4026'; g.fillRect(bx, f - 2, bw, 2); g.fillRect(bx + bw - 2, f - 16, 2, 16);
    // bowl (a "cauldron")
    const ox = Math.round(BOWL * w); g.fillStyle = '#3a3a3a'; g.fillRect(ox - 6, f - 6, 12, 6); g.fillStyle = '#555'; g.fillRect(ox - 7, f - 7, 14, 2);
    if (W.food > 0) { g.fillStyle = '#c8873f'; g.fillRect(ox - 5, f - 9, Math.ceil(W.food * 10), 3); g.fillStyle = '#5a3412'; g.fillRect(ox - 3, f - 8, 1, 1); }
    if (W.cookie) { const y = f - W.cookie.y * 66; g.fillStyle = '#c8873f'; g.fillRect(ox - 3, y - 3, 6, 6); g.fillStyle = '#5a3412'; g.fillRect(ox - 1, y - 1, 1, 1); }
    if (W.ball) { const x = Math.round(W.ball.x * w), y = Math.round(f - W.ball.y * 66); g.fillStyle = '#6fdc5a'; g.fillRect(x - 4, y - 8, 8, 8); g.fillStyle = '#4caf3a'; g.fillRect(x - 2, y - 6, 2, 2); g.fillRect(x + 1, y - 4, 2, 2); }
    if (charImg) {        // the character as chunky blocks with a lit top edge
      const s = 32, y = Y(), sp = pixelSprite(s), px = sp.getContext('2d').getImageData(0, 0, s, s).data, k = 1.4;
      const cx = y.x * w, cy = mixY(f - s * k * .42, f - 9 - s * k * .2);
      g.save(); g.translate(Math.round(cx), Math.round(cy)); if (y.lying > .02) g.rotate(-y.lying * 1.35);
      for (let j = 0; j < s; j++) for (let i = 0; i < s; i++) { const o = (j * s + i) * 4; if (!px[o + 3]) continue;
        g.fillStyle = `rgb(${px[o]},${px[o + 1]},${px[o + 2]})`; g.fillRect((i - s / 2) * k, (j - s * .46) * k, k, k); }
      g.restore();
    }
    for (const s of W.sparks) { g.fillStyle = '#ffe14a'; g.fillRect(Math.round(s.x * w), Math.round(f - s.y * 66 - 20), 2, 2); }
    for (const c of W.crumbs) { g.fillStyle = '#c8873f'; g.fillRect(Math.round(c.x * w), Math.round(f - c.y * 66), 1, 1); }
    if (W.bolt > 0) { g.fillStyle = 'rgba(255,240,120,.35)'; g.fillRect(0, 0, w, this.h); }
    if (Y().mode === 'sleep') { g.fillStyle = '#fff'; g.font = 'bold 7px monospace'; g.fillText('z', Y().x * w + 10, f - 30 - (W.t * 6 % 8)); }
    if (n) { g.fillStyle = 'rgba(10,6,30,.4)'; g.fillRect(0, 0, w, this.h); g.fillStyle = 'rgba(255,200,90,.18)'; g.beginPath(); g.arc(LAMP * w + 8, 56, 26, 0, 7); g.fill(); }
    hud(this);
  }
}

// ======================= 3) soft 2.5D (smooth shapes, light and shadow, like the character) =======================
class SoftView {
  constructor(host) { this.w = 960; this.h = 480; this.floor = 400; Object.assign(this, canvasIn(host, 960, 480)); this.g.imageSmoothingEnabled = true; }
  rr(x, y, w, h, r, fill) { const g = this.g; g.beginPath(); g.roundRect(x, y, w, h, r); g.fillStyle = fill; g.fill(); }
  lin(y0, y1, a, b) { const gr = this.g.createLinearGradient(0, y0, 0, y1); gr.addColorStop(0, a); gr.addColorStop(1, b); return gr; }
  draw() {
    const g = this.g, n = W.night, w = this.w, f = this.floor; g.clearRect(0, 0, w, this.h);
    g.fillStyle = this.lin(0, f, n ? '#3b3372' : '#f3ecff', n ? '#2c2560' : '#ddd0fb'); g.fillRect(0, 0, w, f);
    // floor with perspective boards
    g.fillStyle = this.lin(f, this.h, n ? '#6b4a33' : '#dba06c', n ? '#4f3624' : '#b97b47'); g.fillRect(0, f, w, this.h - f);
    g.strokeStyle = n ? '#00000030' : '#00000018'; g.lineWidth = 2; for (let i = -6; i <= 6; i++) { g.beginPath(); g.moveTo(w / 2 + i * 60, f); g.lineTo(w / 2 + i * 130, this.h); g.stroke(); }
    this.rr(0, f - 18, w, 20, 0, n ? '#3a3070' : '#c4b0f2');
    // window with light falling in
    const wx = 380, wy = 60; this.rr(wx - 14, wy - 14, 200, 170, 22, '#fff');
    g.fillStyle = this.lin(wy, wy + 142, n ? '#1a2257' : '#7cc8ff', n ? '#2c2f72' : '#cdeeff'); g.beginPath(); g.roundRect(wx, wy, 172, 142, 14); g.fill();
    if (n) { g.fillStyle = '#fff3b8'; g.beginPath(); g.arc(wx + 128, wy + 36, 18, 0, 7); g.fill(); g.fillStyle = '#1a2257'; g.beginPath(); g.arc(wx + 120, wy + 30, 16, 0, 7); g.fill(); }
    else { g.fillStyle = '#fff'; [[40, 50, 26], [64, 44, 20], [120, 96, 22], [140, 92, 16]].forEach(([x, y, r]) => { g.beginPath(); g.arc(wx + x, wy + y, r, 0, 7); g.fill(); }); }
    this.rr(wx + 82, wy, 8, 142, 3, '#fff'); this.rr(wx, wy + 66, 172, 8, 3, '#fff'); this.rr(wx - 24, wy + 150, 220, 14, 6, '#c88a52');
    if (!n) { g.fillStyle = 'rgba(255,255,220,.18)'; g.beginPath(); g.moveTo(wx, wy + 142); g.lineTo(wx + 172, wy + 142); g.lineTo(wx + 260, f + 70); g.lineTo(wx - 40, f + 70); g.fill(); }
    // shelf, picture, clock
    this.rr(150, 140, 170, 12, 6, '#a8713f'); ['#ff8f5e', '#4f9ef0', '#ffd25a', '#36c08f', '#ef7aa8', '#7a5cf0'].forEach((c, i) => this.rr(160 + i * 22, 92 + (i % 2) * 8, 18, 48 - (i % 2) * 8, 4, c));
    this.rr(640, 90, 96, 72, 10, '#ffd25a'); g.fillStyle = this.lin(98, 154, '#9fd8ff', '#d9f2ff'); g.fillRect(650, 100, 76, 52); g.fillStyle = '#36c08f'; g.beginPath(); g.moveTo(650, 152); g.lineTo(690, 115); g.lineTo(726, 152); g.fill();
    g.fillStyle = '#fff'; g.beginPath(); g.arc(820, 90, 30, 0, 7); g.fill(); g.strokeStyle = '#5b4b7a'; g.lineWidth = 5; g.stroke(); g.beginPath(); g.moveTo(820, 90); g.lineTo(820, 70); g.moveTo(820, 90); g.lineTo(834, 96); g.stroke();
    // lamp and plant
    const lx = LAMP * w; this.rr(lx - 24, f - 12, 48, 12, 6, '#3b2f63'); this.rr(lx - 4, f - 170, 8, 160, 4, '#3b2f63');
    g.fillStyle = this.lin(f - 230, f - 170, '#ffe08a', '#f5b53a'); g.beginPath(); g.moveTo(lx - 30, f - 170); g.lineTo(lx - 18, f - 228); g.lineTo(lx + 18, f - 228); g.lineTo(lx + 30, f - 170); g.fill();
    if (n) { const rg = g.createRadialGradient(lx, f - 170, 10, lx, f - 170, 200); rg.addColorStop(0, 'rgba(255,220,120,.35)'); rg.addColorStop(1, 'rgba(255,220,120,0)'); g.fillStyle = rg; g.fillRect(lx - 220, f - 380, 440, 420); }
    const px = PLANT * w; g.fillStyle = this.lin(f - 60, f, '#e8885f', '#c4603a'); g.beginPath(); g.moveTo(px - 30, f - 56); g.lineTo(px + 30, f - 56); g.lineTo(px + 22, f); g.lineTo(px - 22, f); g.fill();
    [['#2fa86c', -26, -96, 26], ['#4fd08e', 24, -104, 28], ['#3fbf7f', 0, -126, 30]].forEach(([c, dx, dy, r]) => { g.fillStyle = c; g.beginPath(); g.ellipse(px + dx, f + dy, r * .7, r, dx / 60, 0, 7); g.fill(); });
    // rug
    g.fillStyle = '#f6a8bf'; g.beginPath(); g.ellipse(430, f + 34, 200, 26, 0, 0, 7); g.fill(); g.strokeStyle = '#fff8'; g.lineWidth = 4; g.setLineDash([14, 10]); g.beginPath(); g.ellipse(430, f + 34, 176, 18, 0, 0, 7); g.stroke(); g.setLineDash([]);
    // bed
    const bx = BED.x0 * w, bw = (BED.x1 - BED.x0) * w;
    g.fillStyle = 'rgba(0,0,0,.15)'; g.beginPath(); g.ellipse(bx + bw / 2, f + 6, bw * .55, 14, 0, 0, 7); g.fill();
    this.rr(bx, f - 70, bw, 46, 12, '#8a5a36'); this.rr(bx + 8, f - 26, 16, 26, 4, '#6e4428'); this.rr(bx + bw - 24, f - 26, 16, 26, 4, '#6e4428');
    this.rr(bx + bw - 22, f - 150, 22, 130, 10, '#9a6a40');
    this.rr(bx + 6, f - 96, bw - 34, 32, 14, '#f4f1ff');
    g.fillStyle = this.lin(f - 98, f - 64, n ? '#5565d0' : '#8a97f5', n ? '#3f4cb0' : '#5f6fe0'); g.beginPath(); g.roundRect(bx + 6, f - 98, bw - 100, 36, 14); g.fill();
    this.rr(bx + bw - 96, f - 118, 62, 30, 14, '#ffd96a');
    // bowl and food
    const ox = BOWL * w; g.fillStyle = 'rgba(0,0,0,.15)'; g.beginPath(); g.ellipse(ox, f + 4, 46, 9, 0, 0, 7); g.fill();
    g.fillStyle = this.lin(f - 34, f, '#9d86ff', '#6a4ee0'); g.beginPath(); g.moveTo(ox - 44, f - 30); g.lineTo(ox + 44, f - 30); g.quadraticCurveTo(ox + 40, f, ox, f); g.quadraticCurveTo(ox - 40, f, ox - 44, f - 30); g.fill();
    if (W.food > 0) for (let i = 0; i < Math.ceil(W.food * 4); i++) this.cookie(ox - 24 + i * 16, f - 38 + (i % 2) * 4, 14);
    if (W.cookie) this.cookie(ox, f - W.cookie.y * 330, 18);
    for (const c of W.crumbs) { g.fillStyle = '#c8873f'; g.beginPath(); g.arc(c.x * w, f - c.y * 330, 5, 0, 7); g.fill(); }
    if (W.ball) { const x = W.ball.x * w, y = f - W.ball.y * 330 - 26; g.fillStyle = 'rgba(0,0,0,.18)'; g.beginPath(); g.ellipse(x, f + 4, 26, 6, 0, 0, 7); g.fill();
      const rg = g.createRadialGradient(x - 8, y - 10, 4, x, y, 28); rg.addColorStop(0, '#ff9a9a'); rg.addColorStop(1, '#e24848'); g.fillStyle = rg; g.beginPath(); g.arc(x, y, 26, 0, 7); g.fill();
      g.strokeStyle = '#fff'; g.lineWidth = 6; g.beginPath(); g.moveTo(x - 26, y); g.quadraticCurveTo(x, y + 14, x + 26, y); g.stroke(); }
    if (charImg) { const s = 300, y = Y(), cx = y.x * w, cy = mixY(f - s * .42, f - 96 - s * .2);
      g.save(); g.translate(cx, cy); if (y.lying > .02) g.rotate(-y.lying * 1.35); g.drawImage(charImg, -s / 2, -s * .46, s, s); g.restore(); }
    for (const s of W.sparks) { g.fillStyle = '#ffe14a'; g.beginPath(); g.arc(s.x * w, f - s.y * 330 - 120, 6 * s.life * 2, 0, 7); g.fill(); }
    if (W.charge > 0) { const x = Y().x * w, y = f - 330; this.rr(x - 40, y, 80, 36, 10, '#fff'); this.rr(x - 34, y + 6, 68 * (1 - W.charge / 2.4), 24, 6, '#36c08f'); this.rr(x + 40, y + 10, 8, 16, 3, '#fff'); }
    if (W.pet > 0) { g.font = '60px serif'; g.fillText('🤚', Y().x * w - 30, f - 300 + Math.abs(Math.sin(W.t * 10)) * 18); }
    if (W.bolt > 0) { g.fillStyle = 'rgba(255,240,120,.3)'; g.fillRect(0, 0, w, this.h); }
    if (n) { g.fillStyle = 'rgba(10,6,30,.28)'; g.fillRect(0, 0, w, this.h); }
    hud(this);
  }
  cookie(x, y, r) { const g = this.g; g.fillStyle = '#d0904a'; g.beginPath(); g.arc(x, y, r, 0, 7); g.fill(); g.fillStyle = '#5a3412'; [[-.4, -.3], [.3, -.1], [-.1, .4]].forEach(([a, b]) => { g.beginPath(); g.arc(x + a * r, y + b * r, r * .16, 0, 7); g.fill(); }); }
}

// ---------------- page wiring ----------------
const views = [new PixelView($('v-pixel')), new BlockView($('v-block')), new SoftView($('v-soft'))];
let fps = 10, acc = 1;
document.querySelectorAll('[data-a]').forEach(b => b.onclick = () => ACT[b.dataset.a]());
document.querySelectorAll('#fps button').forEach(b => b.onclick = () => { fps = +b.dataset.f; document.querySelectorAll('#fps button').forEach(x => x.classList.toggle('on', x === b)); });
document.querySelectorAll('#look button').forEach(b => b.onclick = () => {
  look = JSON.parse(b.dataset.l); yad.set(look); document.querySelectorAll('#look button').forEach(x => x.classList.toggle('on', x === b)); });
$('night').onclick = () => { W.night = !W.night; $('night').textContent = W.night ? '☀️ روز' : '🌙 شب';
  if (W.night) ACT.sleep(); else { Y().mode = 'idle'; Y().timer = 1; } };
document.querySelectorAll('.view').forEach(v => v.onclick = e => { if (e.target.tagName === 'CANVAS') ACT.pet(); });

let last = performance.now();
(function loop(now) {
  const dt = Math.max(0, Math.min(.05, (now - last) / 1000)); last = now;
  updateWorld(dt); driveCharacter(dt);
  acc += dt;
  if (acc >= 1 / fps) { acc = 0; snapshot(() => views.forEach(v => v.draw())); }
  requestAnimationFrame(loop);
})(last);
