/* Pixel-art idea for the home-screen widget: a side-view room drawn on a tiny pixel grid, and Yadimo turned into
   a pixel sprite automatically from the vector character (so every hat, colour and costume still works). */
const $ = id => document.getElementById(id);
let night = false;

// ---------- the room, drawn pixel by pixel ----------
const C = {
  wall: '#e9defc', wall2: '#ddd0f7', stripe: '#d4c5f3', floor: '#c98d5b', floor2: '#b47a4c', plank: '#9c6640', skirt: '#a68ad8',
  sky: '#8fd3ff', sky2: '#bfe7ff', frame: '#ffffff', wood: '#8a5a36', wood2: '#a8713f', dark: '#2a2140',
  bed: '#6f7fe8', bed2: '#5565d0', sheet: '#f4f1ff', pillow: '#ffd96a', lamp: '#ffcf5c', glow: 'rgba(255,230,140,.25)',
  leaf: '#3fbf7f', leaf2: '#2b9a63', pot: '#d9774f', rug: '#f6a8bf', rug2: '#ffd0dc', red: '#ff6b6b', white: '#ffffff',
  sofa: '#ef7aa8', sofa2: '#d65f8f', tv: '#2b2f4a', screen: '#7fd3ff', books: ['#ff8f5e', '#4f9ef0', '#ffd25a', '#36c08f', '#ef7aa8'],
  bowl: '#7a5cf0', food: '#c8a06a', gold: '#ffd25a', stair: '#b9875c', stair2: '#a27148',
};
function painter(ctx) {
  const r = (x, y, w, h, c) => { ctx.fillStyle = c; ctx.fillRect(x, y, w, h); };
  const it = {
    window(x, y, w = 16, h = 14) {
      r(x - 1, y - 1, w + 2, h + 2, C.frame); r(x, y, w, h, night ? '#1d2a5c' : C.sky); r(x, y, w, Math.ceil(h / 3), night ? '#26346d' : C.sky2);
      if (night) { r(x + 3, y + 3, 1, 1, C.white); r(x + w - 4, y + 6, 1, 1, C.white); r(x + w - 6, y + 2, 3, 3, '#fff6c8'); }
      else { r(x + 2, y + 4, 4, 2, C.white); r(x + 3, y + 3, 2, 1, C.white); }
      r(x + Math.floor(w / 2), y, 1, h, C.frame); r(x, y + Math.floor(h / 2), w, 1, C.frame);
      r(x - 2, y + h + 1, w + 4, 2, C.wood2);
    },
    bed(x, f) { r(x, f - 8, 24, 5, C.wood); r(x, f - 3, 2, 3, C.wood); r(x + 22, f - 3, 2, 3, C.wood); r(x + 22, f - 14, 2, 11, C.wood);
      r(x + 1, f - 11, 21, 3, C.sheet); r(x + 9, f - 12, 13, 4, C.bed); r(x + 9, f - 9, 13, 1, C.bed2); r(x + 2, f - 13, 6, 3, C.pillow); },
    lamp(x, f) { r(x + 2, f - 2, 4, 2, C.dark); r(x + 3, f - 16, 2, 14, C.dark); r(x, f - 21, 8, 5, C.lamp); r(x + 1, f - 22, 6, 1, C.lamp);
      if (night) { ctx.fillStyle = C.glow; ctx.beginPath(); ctx.arc(x + 4, f - 14, 16, 0, 7); ctx.fill(); } },
    plant(x, f) { r(x + 1, f - 6, 7, 6, C.pot); r(x, f - 7, 9, 2, '#e8885f'); r(x + 2, f - 13, 5, 6, C.leaf); r(x, f - 11, 3, 3, C.leaf2); r(x + 6, f - 12, 3, 4, C.leaf2); r(x + 3, f - 15, 3, 2, C.leaf); },
    shelf(x, y) { for (const dy of [0, 9]) { r(x, y + dy + 7, 20, 2, C.wood2); C.books.forEach((c, i) => r(x + 1 + i * 4, y + dy + (i % 2 ? 1 : 2), 3, 7 - (i % 2 ? 0 : 1), c)); } },
    picture(x, y) { r(x, y, 10, 8, C.gold); r(x + 1, y + 1, 8, 6, '#7a5cf0'); r(x + 3, y + 3, 3, 2, C.gold); },
    clock(x, y) { r(x + 1, y, 6, 8, C.white); r(x, y + 1, 8, 6, C.white); r(x + 3, y + 2, 1, 3, C.dark); r(x + 4, y + 4, 2, 1, C.dark); r(x + 1, y, 6, 1, C.dark); },
    rug(x, f, w = 26) { r(x, f, w, 2, C.rug); r(x + 2, f, w - 4, 1, C.rug2); },
    bowl(x, f) { r(x, f - 3, 8, 3, C.bowl); r(x + 1, f - 4, 6, 1, C.food); r(x + 2, f - 5, 1, 1, '#8a5a2a'); r(x + 5, f - 5, 1, 1, '#8a5a2a'); },
    ball(x, f) { r(x + 1, f - 7, 5, 7, C.red); r(x, f - 6, 7, 5, C.red); r(x, f - 4, 7, 1, C.white); r(x + 2, f - 6, 1, 1, C.white); },
    desk(x, f) { r(x, f - 11, 22, 2, C.wood2); r(x + 1, f - 9, 2, 9, C.wood); r(x + 19, f - 9, 2, 9, C.wood); r(x + 13, f - 9, 6, 7, C.wood);
      r(x + 4, f - 20, 11, 8, C.tv); r(x + 5, f - 19, 9, 6, C.screen); r(x + 9, f - 12, 2, 1, C.tv); r(x + 6, f - 16, 3, 1, C.white); },
    sofa(x, f) { r(x, f - 12, 28, 6, C.sofa2); r(x, f - 7, 28, 5, C.sofa); r(x - 2, f - 10, 4, 8, C.sofa2); r(x + 26, f - 10, 4, 8, C.sofa2);
      r(x + 1, f - 2, 2, 2, C.wood); r(x + 25, f - 2, 2, 2, C.wood); r(x + 5, f - 11, 7, 4, C.pillow); },
    tv(x, f) { r(x, f - 7, 22, 7, C.wood); r(x + 2, f - 6, 8, 5, C.wood2); r(x + 12, f - 6, 8, 5, C.wood2);
      r(x + 1, f - 22, 20, 14, C.tv); r(x + 2, f - 21, 18, 12, night ? '#3b3466' : C.screen); r(x + 4, f - 19, 5, 2, C.white); },
    toybox(x, f) { r(x, f - 8, 11, 8, C.gold); r(x, f - 9, 11, 2, '#ff8f5e'); r(x + 4, f - 6, 3, 2, '#ff8f5e'); },
  };
  return {r, it};
}

/** A room of w×h pixels; [floors] are the y of each floor line (one, or two for the house). */
function drawRoom(ctx, w, h, kind) {
  const {r, it} = painter(ctx);
  const floors = kind === 'full' ? [h - 6, Math.round(h / 2) + 2] : [h - 6];
  // wallpaper
  r(0, 0, w, h, C.wall);
  for (let x = 2; x < w; x += 8) r(x, 0, 2, h, C.stripe);
  for (const f of floors) {
    r(0, f, w, 6, C.floor); for (let x = (f % 7); x < w; x += 12) r(x, f + 1, 1, 5, C.plank); r(0, f + 3, w, 1, C.floor2);
    r(0, f - 2, w, 2, C.skirt);
  }
  if (kind === 'full') { // ceiling of the lower room, and the stairs on the right
    const up = floors[1];
    const steps = 12, sh = (h - 6 - up) / steps, sw = 26 / steps;
    for (let i = 0; i < steps; i++) { const x = Math.round(w - 32 + i * sw), y = Math.round(h - 6 - (i + 1) * sh);
      r(x, y, Math.ceil(sw) + 3, 2, C.stair); r(x, y + 2, Math.ceil(sw) + 3, Math.ceil(sh) - 2, C.stair2); }
    for (let i = 0; i <= steps; i += 3) { const x = Math.round(w - 31 + i * sw), y = Math.round(h - 6 - i * sh); r(x, y - 9, 1, 9, C.wood); }
    for (let i = 0; i < steps; i++) r(Math.round(w - 31 + i * sw), Math.round(h - 15 - (i + 1) * sh), Math.ceil(sw) + 1, 1, C.wood);
  }
  const f = floors[0];
  if (kind === 's22') { it.window(14, 8, 18, 14); it.rug(8, f, 32); }
  if (kind === 's42') { it.window(40, 6); it.bed(w - 28, f); it.lamp(6, f); it.rug(30, f); it.bowl(18, f); }
  if (kind === 's43') { it.window(40, 10); it.bed(w - 28, f); it.lamp(4, f); it.rug(30, f); it.bowl(16, f); it.shelf(4, 14); it.picture(70, 12); it.plant(58, f); }
  if (kind === 's44') { it.window(42, 14, 16, 16); it.bed(w - 28, f); it.lamp(w - 40, f); it.rug(22, f, 30); it.bowl(56, f); it.shelf(4, 22); it.picture(72, 18); it.clock(26, 8);
    it.plant(2, f); it.desk(4, f - 30); it.ball(14, f); it.toybox(w - 46, f); }
  if (kind === 'full') { const up = floors[1];
    it.window(30, 18, 16, 14); it.window(58, 18, 14, 14); it.bed(w - 30, up); it.lamp(4, up); it.desk(12, up); it.picture(48, 8); it.clock(78, 6);
    it.sofa(6, f); it.tv(46, f); it.plant(36, f); it.rug(8, f, 30); it.ball(30, f); it.bowl(70, f); it.shelf(8, up + 12); it.toybox(4, up);
    it.window(40, up + 14, 14, 12); it.picture(64, up + 16); }
  if (night) { ctx.fillStyle = 'rgba(16,10,40,.45)'; ctx.fillRect(0, 0, w, h); if (kind !== 's22') { const lx = kind === 'full' ? 8 : kind === 's44' ? w - 36 : 6;
    ctx.fillStyle = 'rgba(255,220,120,.18)'; ctx.beginPath(); ctx.arc(lx, (kind === 'full' ? floors[1] : f) - 14, 22, 0, 7); ctx.fill(); } }
  return floors;
}

// ---------- Yadimo as a pixel sprite ----------
/** Turns the vector character into pixels: shrink, snap colours and transparency, then draw a dark outline. */
function pixelize(img, size) {
  const c = document.createElement('canvas'); c.width = c.height = size;
  const g = c.getContext('2d'); g.imageSmoothingEnabled = true; g.imageSmoothingQuality = 'high';
  g.drawImage(img, 0, 0, size, size);
  const d = g.getImageData(0, 0, size, size), p = d.data, solid = new Uint8Array(size * size);
  for (let i = 0; i < size * size; i++) {
    const a = p[i * 4 + 3];
    if (a < 110) { p[i * 4 + 3] = 0; continue; }
    solid[i] = 1; p[i * 4 + 3] = 255;
    for (let k = 0; k < 3; k++) p[i * 4 + k] = Math.min(255, Math.round(p[i * 4 + k] / 12) * 12);
  }
  const out = new Uint8ClampedArray(p);
  for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
    const i = y * size + x; if (solid[i]) continue;
    if ((x > 0 && solid[i - 1]) || (x < size - 1 && solid[i + 1]) || (y > 0 && solid[i - size]) || (y < size - 1 && solid[i + size])) {
      out[i * 4] = 42; out[i * 4 + 1] = 30; out[i * 4 + 2] = 70; out[i * 4 + 3] = 255;
    }
  }
  g.putImageData(new ImageData(out, size, size), 0, 0);
  return c;
}

const SIZES = {s22: [48, 48], s42: [96, 48], s43: [96, 72], s44: [96, 96], full: [96, 176]};
const LINES = {s22: ['اتاقم کوچیکه! 😅', 'یه کم جا بده! 🙏'], any: ['سیرم، مرسی! 😋', 'امروز ۳ تا کار داری 💪', 'بیا بازی! ⚽', 'دلم برات تنگ شده بود 🥺']};
function rng(seed) { let s = seed; return () => (s = (s * 16807) % 2147483647) / 2147483647; }
const serializer = new XMLSerializer();

class Pet {
  constructor(host, kind, fps, look, seed, sprite) {
    this.host = host; this.kind = kind; this.fps = fps; this.rand = rng(seed); this.acc = 1; this.t = 0;
    const [w, h] = SIZES[kind]; this.w = w; this.h = h;
    host.innerHTML = `<canvas width="${w}" height="${h}"></canvas><div class="bubble"></div>`;
    this.ctx = host.querySelector('canvas').getContext('2d'); this.bubble = host.querySelector('.bubble');
    this.bg = document.createElement('canvas'); this.bg.width = w; this.bg.height = h; this.redraw();
    this.size = sprite || 34;
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('viewBox', '0 0 600 600'); svg.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
    svg.style.cssText = 'position:absolute;width:10px;height:10px;left:-99px;top:0'; document.body.append(svg);
    this.svg = svg; this.y = new Yadimo(svg, look);
    this.floor = 0; this.x = w * .5; this.tx = this.x; this.climb = null; this.wait = 1.5; this.face = 1; this.busy = false;
  }
  redraw() { this.floors = drawRoom(this.bg.getContext('2d'), this.w, this.h, this.kind); }
  say(t, s = 2.6) { this.bubble.textContent = t; this.bubble.classList.add('on'); clearTimeout(this.st); this.st = setTimeout(() => this.bubble.classList.remove('on'), s * 1000); }
  act(k) {
    const y = this.y;
    if (k === 'feed') { y.setMood('love'); this.say('ممم! خوشمزه بود 😋'); }
    if (k === 'charge') { y.setMood('celebrate'); y.burst('spark', 10); this.say('شارژ شدم! ⚡'); }
    if (k === 'pet') { y.setMood('love'); this.say('آخ جون! 🥰'); }
    if (k === 'play') { y.setMood('celebrate'); y.burst('conf', 12); this.say('بزن بریم! ⚽'); }
    clearTimeout(this.mt); this.mt = setTimeout(() => y.setMood(night ? 'sleep' : 'idle'), 3000);
  }
  move(dt) {
    this.t += dt;
    if (night) return;
    if (this.climb) { // up or down the stairs of the house
      const c = this.climb; c.p = Math.min(1, c.p + dt / 1.6);
      this.x = c.x0 + (c.x1 - c.x0) * c.p; this.yOff = (this.floors[c.from] - this.floors[c.to]) * c.p;
      if (c.p >= 1) { this.floor = c.to; this.yOff = 0; this.climb = null; }
      return;
    }
    const d = this.tx - this.x;
    if (Math.abs(d) > .5) { this.x += Math.sign(d) * Math.min(Math.abs(d), 16 * dt); this.face = Math.sign(d); this.walking = true; return; }
    this.walking = false; this.wait -= dt;
    if (this.wait > 0) return;
    this.wait = 1.5 + this.rand() * 3;
    if (this.kind === 's22') { if (this.rand() < .6) this.say(LINES.s22[Math.floor(this.rand() * 2)]); else this.y.setMood('wave'); return; }
    if (this.kind === 'full' && this.rand() < .3) { // go to the other floor by the stairs
      const up = this.floor === 0, bottom = this.w - 32, top = this.w - 6;
      if (Math.abs(this.x - (up ? bottom : top)) > 1) { this.tx = up ? bottom : top; this.wait = 0; this.pendingClimb = true; return; }
    }
    if (this.pendingClimb) { const up = this.floor === 0; this.pendingClimb = false;
      this.climb = {p: 0, from: this.floor, to: up ? 1 : 0, x0: this.x, x1: up ? this.w - 6 : this.w - 32}; return; }
    const margin = this.size * .35;
    this.tx = margin + this.rand() * (this.w - margin * 2 - (this.kind === 'full' ? 34 : 0));
    if (this.rand() < .25) this.say(LINES.any[Math.floor(this.rand() * LINES.any.length)]);
  }
  frame(dt) {
    this.move(dt);
    this.y.posY.t = this.walking || this.climb ? -Math.abs(Math.sin(this.t * 10)) * 40 : 0;
    this.y.pointer.x = this.walking ? this.face * .8 : 0; this.y.pointer.y = 0; this.y.pointer.active = this.walking; this.y.o.follow = this.walking;
    this.y.update(dt);
    // render the vector character to a picture, then to pixels
    // the hidden on-page copy is tiny; the picture is drawn at full size
    const markup = serializer.serializeToString(this.svg).replace(/^<svg[^>]*?style="[^"]*"/, m => m.replace(/style="[^"]*"/, 'width="600" height="600"'));
    const src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(markup);
    const img = new Image();
    img.onload = () => {
      this.sprite = pixelize(img, this.size);
      const g = this.ctx; g.clearRect(0, 0, this.w, this.h); g.drawImage(this.bg, 0, 0);
      const fy = this.floors[this.floor] - (this.yOff || 0);
      g.drawImage(this.sprite, Math.round(this.x - this.size / 2), Math.round(fy - this.size * .88));
    };
    img.src = src;
  }
  tick(dt) { this.acc += dt; if (this.acc >= 1 / this.fps) { this.frame(this.acc); this.acc = 0; } }
}

const look = {variant: 'robot', color: 'lilac', hat: 'party', glass: 'sun'};
const look2 = {variant: 'bell', color: 'peach', hat: 'crown'};
const pets = [];
[[8, '۸ فریم'], [12, '۱۲ فریم'], [24, '۲۴ فریم']].forEach(([fps, label]) => {
  const card = document.createElement('div'); card.className = 'card';
  card.innerHTML = `<div class="tag">${label} در ثانیه</div><div class="w px"></div>`;
  $('fps-grid').append(card); pets.push(new Pet(card.querySelector('.w'), 's42', fps, look, 5));
});
const sized = [];
[['s22', '۲×۲ — فقط خودش'], ['s42', '۴×۲ — تخت، چراغ، ظرف غذا'], ['s43', '۴×۳ — قفسه، تابلو، گلدان'], ['s44', '۴×۴ — میز و کامپیوتر، توپ، ساعت'],
 ['full', 'تمام صفحه — خانهٔ دوطبقه']].forEach(([k, label], i) => {
  const card = document.createElement('div'); card.className = 'card size ' + k;
  card.innerHTML = `<div class="tag">${label}</div><div class="w px ${k}"></div>` + (k === 's22' ? '' :
    `<div class="btns"><button data-a="feed">🍪 غذا</button><button data-a="charge">⚡ شارژ</button><button data-a="pet">🤚 ناز</button><button data-a="play">⚽ بازی</button></div>`);
  $('size-grid').append(card);
  const p = new Pet(card.querySelector('.w'), k, 10, i % 2 ? look2 : look, 3 + i, k === 's22' ? 40 : 34);
  card.querySelectorAll('button').forEach(b => b.onclick = () => p.act(b.dataset.a));
  card.querySelector('.w').onclick = () => p.act('pet');
  pets.push(p); sized.push(p);
});
document.querySelectorAll('#size-fps button').forEach(b => b.onclick = () => {
  sized.forEach(p => p.fps = +b.dataset.f);
  document.querySelectorAll('#size-fps button').forEach(x => x.classList.toggle('on', x === b));
});
$('night').onclick = () => { night = !night; $('night').textContent = night ? '☀️ روز' : '🌙 شب';
  pets.forEach(p => { p.redraw(); p.y.setMood(night ? 'sleep' : 'idle'); }); };
let last = performance.now();
(function loop(now) { const dt = Math.max(0, Math.min(.2, (now - last) / 1000)); last = now; pets.forEach(p => p.tick(dt)); requestAnimationFrame(loop); })(last);
