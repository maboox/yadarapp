/* Widget idea demo: Yadimo living in an isometric room, at different widget sizes and frame rates. */
const $ = id => document.getElementById(id);
const LINES = {
  s22: ['اتاقم یه کم کوچیک نیست؟ 😅', 'یه ذره جا بده دیگه! 🙏', 'اینجا حتی تخت هم جا نمی‌شه 😂'],
  any: ['سیرم، مرسی! 😋', 'امروز ۳ تا کار داری 💪', 'بیا بازی! ⚽', 'دلم برات تنگ شده بود 🥺', 'چه اتاق قشنگی دارم! ✨'],
};
let night = false;
const actors = [];

// A small seeded random, so every panel makes the same choices in the same order.
function rng(seed) { let s = seed; return () => (s = (s * 16807) % 2147483647) / 2147483647; }

class Actor {
  constructor(host, sizeKey, fps, look, opt = {}) {
    this.host = host; this.key = sizeKey; this.fps = fps; this.look = look; this.opt = opt;
    this.rand = rng(opt.seed || 7); this.acc = 0; this.t = 0;
    this.draw();
  }
  draw() {
    const cfg = Room.SIZES[this.key];
    const r = Room.build(cfg.n, cfg.items, {night, walls: cfg.walls});
    this.room = r; this.n = cfg.n;
    const [x, y, w, h] = r.viewBox;
    this.host.innerHTML = `<svg class="room" viewBox="${x} ${y} ${w} ${h}" preserveAspectRatio="xMidYMid meet">${r.svg}<svg class="yad" viewBox="0 0 600 600"></svg></svg>
      <div class="bubble"></div>`;
    this.svg = this.host.querySelector('svg.yad');
    this.bubble = this.host.querySelector('.bubble');
    this.size = this.key === 's22' ? 190 : 150;
    this.svg.setAttribute('width', this.size); this.svg.setAttribute('height', this.size);
    const old = this.y;
    this.y = new Yadimo(this.svg, this.look);
    if (old) this.y.setMood(old.mood);
    this.free = this.freeTiles();
    if (!this.pos) { this.pos = this.key === 's22' ? [1.5, 1.5] : [this.n * .55, this.n * .6]; this.target = this.pos.slice(); }
    this.wait = 1 + this.rand() * 2; this.place();
  }
  freeTiles() {
    const n = this.n, used = new Set();
    for (const [, x, y] of Room.SIZES[this.key].items) for (let i = Math.floor(x); i < x + 2 && i < n; i++) for (let j = Math.floor(y); j < y + 1.3 && j < n; j++) used.add(i + ',' + j);
    const out = [];
    for (let i = 1; i < n; i++) for (let j = 1; j < n; j++) if (!used.has(i + ',' + j)) out.push([i + .5, j + .5]);
    return out.length ? out : [[n / 2, n / 2]];
  }
  place() {
    const [sx, sy] = this.room.toScreen(this.pos[0], this.pos[1]);
    // the character's feet are near the bottom of its 600×600 box
    this.svg.setAttribute('x', (sx - this.size / 2).toFixed(1));
    this.svg.setAttribute('y', (sy - this.size * .9).toFixed(1));
  }
  say(text, secs = 3) {
    this.bubble.textContent = text; this.bubble.classList.add('on');
    clearTimeout(this.sayT); this.sayT = setTimeout(() => this.bubble.classList.remove('on'), secs * 1000);
  }
  act(kind) {
    const y = this.y;
    if (kind === 'feed') { y.setMood('love'); this.say('ممم! خوشمزه بود 😋'); this.goTo(this.near('bowl')); }
    if (kind === 'charge') { y.setMood('celebrate'); y.burst('spark', 12); this.say('شارژ شدم! ⚡'); }
    if (kind === 'pet') { y.setMood('love'); this.say('آخ جون! 🥰'); }
    if (kind === 'play') { y.setMood('celebrate'); y.burst('conf', 14); this.say('بزن بریم! ⚽'); this.goTo(this.near('ball')); }
    clearTimeout(this.moodT); this.moodT = setTimeout(() => y.setMood(night ? 'sleep' : 'idle'), 3200);
  }
  near(item) {
    const it = Room.SIZES[this.key].items.find(i => i[0] === item);
    return it ? [Math.min(this.n - .5, it[1] + .5), Math.min(this.n - .5, it[2] + 1.4)] : null;
  }
  goTo(p) { if (p && this.key !== 's22') { this.target = p; this.wait = 2.5; } }
  step(dt) {
    this.t += dt;
    if (night) { this.y.update(dt); return; }
    const dx = this.target[0] - this.pos[0], dy = this.target[1] - this.pos[1], d = Math.hypot(dx, dy);
    if (d > .02) {
      const v = Math.min(d, 1.4 * dt);
      this.pos[0] += dx / d * v; this.pos[1] += dy / d * v;
      // a small hop while walking
      this.y.posY.t = -Math.abs(Math.sin(this.t * 9)) * 26;
      this.y.pointer.x = Math.max(-1, Math.min(1, (dx - dy) * 3)); this.y.pointer.y = .2; this.y.pointer.active = true; this.y.o.follow = true;
    } else {
      this.y.posY.t = 0; this.y.o.follow = false;
      this.wait -= dt;
      if (this.wait <= 0) {
        this.wait = 1.5 + this.rand() * 3;
        if (this.key === 's22') { if (this.rand() < .5) this.say(LINES.s22[Math.floor(this.rand() * 3)]); else this.y.setMood(this.rand() < .5 ? 'wave' : 'idle'); }
        else {
          this.target = this.free[Math.floor(this.rand() * this.free.length)].slice();
          if (this.rand() < .25) this.say(LINES.any[Math.floor(this.rand() * LINES.any.length)]);
        }
      }
    }
    this.place();
    this.y.update(dt);
  }
  tick(dt) {
    if (!this.fps) { this.step(dt); return; }
    // Like a widget flipping between ready-made pictures: nothing moves between two frames.
    this.acc += dt;
    if (this.acc >= 1 / this.fps) { this.step(this.acc); this.acc = 0; }
  }
}

const look = {variant: 'robot', color: 'lilac', hat: 'party', glass: 'sun'};
const look2 = {variant: 'bell', color: 'peach', hat: 'crown'};

// 1) the same room at four frame rates
[[0, 'روان (مثل داخل برنامه)'], [15, '۱۵ فریم در ثانیه'], [8, '۸ فریم در ثانیه'], [4, '۴ فریم در ثانیه']].forEach(([fps, label], i) => {
  const card = document.createElement('div'); card.className = 'card';
  card.innerHTML = `<div class="tag">${label}</div><div class="w w42"></div>`;
  $('fps-grid').append(card);
  actors.push(new Actor(card.querySelector('.w'), 's42', fps, look, {seed: 11}));
});

// 2) rooms by widget size
let sizeFps = 0;
const sized = [];
[['s22', '۲×۲ — فقط خودش', 'w22'], ['s42', '۴×۲ — تخت و چراغ', 'w42'], ['s43', '۴×۳ — قفسه، گلدان، ظرف غذا', 'w43'],
 ['s44', '۴×۴ — میز، توپ، جعبهٔ اسباب‌بازی', 'w44'], ['full', 'تمام صفحه — خانهٔ یادیمو', 'wfull']].forEach(([key, label, cls], i) => {
  const card = document.createElement('div'); card.className = 'card size ' + cls;
  card.innerHTML = `<div class="tag">${label}</div><div class="w ${cls}"></div>` + (key === 's22' ? '' :
    `<div class="btns"><button data-a="feed">🍪 غذا</button><button data-a="charge">⚡ شارژ</button><button data-a="pet">🤚 ناز</button><button data-a="play">⚽ بازی</button></div>`);
  $('size-grid').append(card);
  const a = new Actor(card.querySelector('.w'), key, sizeFps, i % 2 ? look2 : look, {seed: 3 + i});
  card.querySelectorAll('button').forEach(b => b.onclick = () => a.act(b.dataset.a));
  card.querySelector('.w').onclick = () => a.act('pet');
  actors.push(a); sized.push(a);
});
document.querySelectorAll('#size-fps button').forEach(b => b.onclick = () => {
  sizeFps = +b.dataset.f; sized.forEach(a => { a.fps = sizeFps; a.acc = 0; });
  document.querySelectorAll('#size-fps button').forEach(x => x.classList.toggle('on', x === b));
});
$('night').onclick = () => {
  night = !night; $('night').textContent = night ? '☀️ روز' : '🌙 شب';
  actors.forEach(a => { a.draw(); a.y.setMood(night ? 'sleep' : 'idle'); });
};

let last = performance.now();
function loop(now) {
  const dt = Math.max(0, Math.min(.05, (now - last) / 1000)); last = now;
  for (const a of actors) a.tick(dt);
  requestAnimationFrame(loop);
}
requestAnimationFrame(loop);
