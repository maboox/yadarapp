/* Yadimo studio: moods, accessories and scenes on top of the 2.5D vector rig (art.js + volume.js).
   Several Yadimos can live on one page; each one prefixes its SVG ids. */
function shade(hex, n) { return '#' + hex.slice(1).match(/../g).map(v => Math.max(0, Math.min(255, parseInt(v, 16) + n)).toString(16).padStart(2, '0')).join(''); }

const COLORS = {
  lilac: {n: 'یاسی', body: '#9976f5', accent: '#ffc746'},
  sky:   {n: 'آسمانی', body: '#4f9ef0', accent: '#ffc746'},
  mint:  {n: 'نعنایی', body: '#36c08f', accent: '#ffc746'},
  peach: {n: 'هلویی', body: '#ff8f5e', accent: '#ffd25a'},
  sun:   {n: 'آفتابی', body: '#f2b12a', accent: '#ff7b54'},
  rose:  {n: 'صورتی', body: '#f06aa3', accent: '#ffc746'},
  night: {n: 'شب', body: '#4b4f96', accent: '#ffc746'},
};
const FACES = {happy: 'خوشحال', wink: 'چشمک', love: 'عاشق', surprised: 'تعجب', focused: 'جدی', sleepy: 'خواب‌آلود'};
const VARIANTS = {bell: 'زنگوله', robot: 'ربات'};
/* Each mood sets a pose, a motion and sometimes a face, and adds its own little extras. */
const MOODS = {
  idle:      {n: 'ایستاده', pose: 'rest', motion: 'curious'},
  wave:      {n: 'سلام', pose: 'wave', motion: 'float', expr: 'happy', sparkles: true},
  talk:      {n: 'حرف زدن', pose: 'point', motion: 'float', expr: 'happy'},
  think:     {n: 'فکر', pose: 'rest', motion: 'still', expr: 'happy'},
  ring:      {n: 'زنگ', pose: 'yay', motion: 'ring', expr: 'surprised'},
  celebrate: {n: 'جشن', pose: 'yay', motion: 'bounce', expr: 'wink', sparkles: true},
  love:      {n: 'ذوق', pose: 'rest', motion: 'float', expr: 'love', sparkles: true},
  sleep:     {n: 'خواب', pose: 'rest', motion: 'float', expr: 'sleepy'},
  fly:       {n: 'پرواز به بالا', pose: 'yay', motion: 'float', expr: 'happy'},
};
const COVERS_ANTENNA = new Set(['party', 'cap', 'crown', 'beanie', 'wizard', 'grad', 'chef', 'night', 'flowers']);
const SVGNS = 'http://www.w3.org/2000/svg';
let YID = 0;

class Yadimo {
  constructor(svg, o = {}) {
    this.svg = svg; this.P = 'y' + (++YID) + '-';
    this.o = Object.assign({variant: 'bell', color: 'lilac', face: 'happy', hat: 'none', glass: 'none', neck: 'none', item: 'none', follow: false}, o);
    this.mood = 'idle'; this.t = 0; this.mt = 0; this.parts = []; this.pointer = {x: 0, y: 0, active: false};
    this.rig = new VectorRig(svg, this.P);
    this.posX = {x: 0, v: 0, t: 0}; this.posY = {x: 0, v: 0, t: 0};
    this.nextBlink = 2; this.blinkAt = -9; this.wasUp = false;
    this.render();
  }
  set(o) { Object.assign(this.o, o); this.render(); }
  setMood(m) {
    const before = MOODS[this.mood].expr || this.o.face;
    this.mood = m; this.mt = 0;
    if (m === 'fly') { this.posY.x = 560; this.posY.v = -2200; this.posY.t = 0; }
    if ((MOODS[m].expr || this.o.face) !== before || !!MOODS[m].sparkles !== this.sparkles) this.render();
  }
  prefix(s) { return s.replace(/id="([^"]+)"/g, `id="${this.P}$1"`).replace(/url\(#([^)]+)\)/g, `url(#${this.P}$1)`); }
  q(id) { return this.svg.querySelector('#' + this.P + id); }
  render() {
    const m = MOODS[this.mood], c = COLORS[this.o.color] || COLORS.lilac, robot = this.o.variant === 'robot';
    this.sparkles = !!m.sparkles;
    const s = {variant: this.o.variant, expression: m.expr || this.o.face, gaze: '0', bodyColor: c.body, accentColor: c.accent,
      cheekColor: '#f5a2c6', sparkles: this.sparkles, blush: true};
    this.svg.innerHTML = this.prefix(mascotArt(s));
    this.svg.querySelector('defs').insertAdjacentHTML('beforeend', this.prefix(ACC_DEFS));
    this.rig.bind(robot);
    const body = this.rig.body, face = this.q('face');
    const group = html => { const g = document.createElementNS(SVGNS, 'g'); g.innerHTML = this.prefix(html); return g; };
    // neck piece under the face (bell) or under the head (bot)
    this.gNeck = group((NECK[this.o.neck] || NECK.none).svg());
    if (robot) { this.gNeck.setAttribute('transform', 'translate(0 -16)'); body.insertBefore(this.gNeck, this.rig.head); }
    else body.insertBefore(this.gNeck, face);
    // glasses on the face, hat on top of everything on the head
    this.gGlass = group((GLASSES[this.o.glass] || GLASSES.none).svg()); face.append(this.gGlass);
    this.gHat = group((HATS[this.o.hat] || HATS.none).svg()); body.append(this.gHat);
    if (COVERS_ANTENNA.has(this.o.hat)) this.q('antenna').setAttribute('visibility', 'hidden');
    // the item sits in the right hand and turns with the arm
    const hand = robot ? [474, 386] : [500, 300], itemSvg = (ITEMS[this.o.item] || ITEMS.none).svg(hand[0], hand[1]);
    // the bell's right arm is behind its body; the bot's arms are behind its head, so its item gets its own top layer
    this.gItem = null;
    if (robot) { this.gItem = group(itemSvg); body.insertBefore(this.gItem, this.gHat); }
    else this.q('arm-right').insertAdjacentHTML('beforeend', this.prefix(itemSvg));
    this.fx = document.createElementNS(SVGNS, 'g'); this.svg.append(this.fx);
    this.rig.step(this.target(this.t), 0, true);
  }
  armAngles() {
    const robot = this.o.variant === 'robot', pose = MOODS[this.mood].pose;
    const poses = robot ? {rest: [0, 0], wave: [0, -65], yay: [65, -65], point: [0, -30]} : {rest: [-15, 25], wave: [0, -55], yay: [75, -55], point: [0, -15]};
    const a = poses[pose].slice();
    if (this.o.item !== 'none' && pose === 'rest') a[1] = robot ? -20 : 0;
    return a;
  }
  target(t) {
    const m = this.mood, mt = this.mt, tracking = this.o.follow && this.pointer.active;
    let yaw = tracking ? this.pointer.x * 35 : 0, pitch = tracking ? -this.pointer.y * 22 : 0, lean = 0;
    if (!tracking) {
      yaw += Math.sin(t * .8) * 4; pitch += Math.sin(t * .61) * 2; lean += Math.sin(t * .7) * 1.2;
      if (m === 'idle') { yaw += Math.sin(t * 1.05) * 26; pitch += Math.sin(t * .7) * 9; lean += Math.sin(t * .8) * 3; }
      if (m === 'think') { yaw = -16 + Math.sin(t * .9) * 3; pitch = 14; lean = -7; }
      if (m === 'sleep') { yaw = 6; pitch = -16 + Math.sin(t * 1.1) * 2; lean = 7; }
      if (m === 'talk') { yaw += Math.sin(mt * 2.3) * 10; pitch += Math.sin(mt * 4.6) * 5; }
      if (m === 'wave') { yaw += 8; lean -= 4; }
      if (m === 'love') { lean += Math.sin(mt * 2.4) * 7; }
      if (m === 'ring') { yaw += Math.sin(mt * 19) * 12; }
    }
    const k = {headYaw: yaw * .72, headPitch: pitch * .76, headRoll: lean - yaw * .09, bodyYaw: yaw * .38, bodyPitch: pitch * .3, bodyRoll: lean * .2 - yaw * .12};
    const max = {headYaw: 35, headPitch: 22, headRoll: 18, bodyYaw: 30, bodyPitch: 18, bodyRoll: 10};
    for (const key in k) k[key] = Math.max(-max[key], Math.min(max[key], k[key]));
    return k;
  }
  spring(s, dt, k = 120, d = 13) { s.v += (k * (s.t - s.x) - d * s.v) * dt; s.x += s.v * dt; }
  update(dt) {
    const m = this.mood, slow = m === 'sleep' ? .55 : 1;
    this.t += dt * slow; this.mt += dt;
    const t = this.t, mt = this.mt, motion = MOODS[m].motion;
    this.rig.step(this.target(t), dt);
    let y = 0, angle = this.rig.current.bodyRoll, sx = 1, sy = 1;
    if (motion === 'float') y = Math.sin(t * 2) * 9;
    if (motion === 'curious') y = Math.sin(t * 1.5) * 5;
    if (motion === 'ring') angle += Math.sin(mt * 26) * 8;
    if (motion === 'bounce') {
      const b = Math.abs(Math.sin(mt * 3.2)); y = -b * 46;
      const ground = b < .14; sy = ground ? 1 - .09 * (1 - b / .14) : 1 + .05 * b; sx = ground ? 1 + .07 * (1 - b / .14) : 1 - .03 * b;
      const up = Math.cos(mt * 3.2) * Math.sin(mt * 3.2) < 0 ? false : true;
      if (b > .2 && b < .35 && up && !this.wasUp) this.burst('conf', 16);
      this.wasUp = b > .2 && up;
    }
    sx *= 1 + Math.sin(t * 1.8) * .006; sy *= 1 - Math.sin(t * 1.8) * .006;
    // fly in from below: an underdamped spring overshoots and settles like a real jump
    if (m === 'fly') {
      this.spring(this.posY, dt, 70, 9);
      const v = this.posY.v; sy *= 1 + Math.max(-.08, Math.min(.14, -v / 3000)); sx *= 1 - Math.max(-.08, Math.min(.14, -v / 3000)) * .6;
      if (mt < .7 && Math.random() < .7) this.parts.push({type: 'spark', x: 300 + (Math.random() - .5) * 120, y: 480 + this.posY.x, vx: 0, vy: 80, life: .6, r: 0});
    } else { this.posY.t = 0; this.spring(this.posY, dt, 90, 14); }
    this.spring(this.posX, dt, 60, 12);
    y += this.posY.x;
    this.q('position').setAttribute('transform', `translate(${this.posX.x.toFixed(2)} ${y.toFixed(2)})`);
    this.q('character').setAttribute('transform', `translate(300 330) rotate(${angle.toFixed(2)}) scale(${sx.toFixed(4)} ${sy.toFixed(4)}) translate(-300 -330)`);
    const gs = this.q('ground-shadow'); gs.setAttribute('opacity', String(Math.max(.15, 1 + Math.min(0, y) / 140)));
    gs.setAttribute('cx', String(300 + this.posX.x)); gs.setAttribute('rx', String(177 * Math.max(.6, 1 + Math.min(0, y) / 400)));
    // eyes: random blinks
    if (t > this.nextBlink) { this.blinkAt = t; this.nextBlink = t + 2 + Math.random() * 3.5; }
    const blink = t - this.blinkAt < .13 && !['sleepy', 'love'].includes(MOODS[m].expr || this.o.face);
    const pose = this.armAngles();
    if (MOODS[m].pose === 'wave' || (m === 'fly' && mt > 1.2)) pose[1] += Math.sin(mt * 9) * 12;
    if (m === 'fly' && mt > 1.2) { pose[0] = this.o.variant === 'robot' ? 0 : -15; pose[1] -= 0; }
    if (m === 'talk') pose[1] += Math.sin(mt * 5) * 10;
    if (m === 'ring') { pose[0] += Math.sin(mt * 30) * 8; pose[1] += Math.sin(mt * 30 + 1) * 8; }
    if (m === 'celebrate') { pose[0] += Math.sin(mt * 12) * 8; pose[1] -= Math.sin(mt * 12) * 8; }
    const ex = {};
    if (m === 'talk') ex.mouth = .3 + .7 * Math.abs(Math.sin(mt * 11) * Math.sin(mt * 3.7 + 1));
    if (m === 'think') ex.eyeUp = -7;
    if (m === 'ring') ex.clap = Math.sin(mt * 26) * 26;
    const gaze = this.o.follow && this.pointer.active ? this.pointer.x * 4 : (m === 'think' ? -4 : 0);
    this.rig.apply({gaze: '0'}, motion === 'still' ? 0 : t, pose, blink, gaze, ex);
    // accessories follow the projected head and face
    const mp = this.rig.maps;
    if (mp) {
      this.gHat.setAttribute('transform', matrixFor(mp.shellMap, 300, 196));
      this.gGlass.setAttribute('transform', matrixFor(mp.faceMap, 300, 275));
      if (this.o.variant !== 'robot') this.gNeck.setAttribute('transform', matrixFor(mp.shellMap, 300, 362));
      if (this.gItem) this.gItem.setAttribute('transform', this.q('arm-right').getAttribute('transform') || '');
    }
    this.effects(dt, y);
  }
  burst(type, n) {
    const cols = ['#ffd24d', '#ff7fae', '#6fe0b2', '#7cc0ff', '#a993ff'];
    for (let i = 0; i < n; i++) { const a = Math.random() * Math.PI * 2, sp = 120 + Math.random() * 240;
      this.parts.push({type, x: 300 + this.posX.x, y: 230 + this.posY.x, vx: Math.cos(a) * sp, vy: Math.sin(a) * sp - 260, life: 1.3, r: Math.random() * 6, vr: (Math.random() - .5) * 12, c: cols[i % 5]}); }
  }
  effects(dt, y) {
    const m = this.mood, mt = this.mt, cx = 300 + this.posX.x;
    if (m === 'love' && Math.random() < dt * 3) this.parts.push({type: 'heart', x: cx + (Math.random() - .5) * 220, y: 200 + y, vx: (Math.random() - .5) * 30, vy: -90, life: 1.6, r: 0});
    if (m === 'sleep' && Math.random() < dt * .9) this.parts.push({type: 'z', x: cx + 120, y: 170 + y, vx: 34, vy: -50, life: 2.2, r: 0, size: 30 + Math.random() * 26});
    let h = '';
    for (const p of this.parts) {
      p.life -= dt; p.x += p.vx * dt; p.y += p.vy * dt; p.r += (p.vr || 0) * dt;
      if (p.type === 'conf' || p.type === 'spark') p.vy += 620 * dt;
      const o = Math.max(0, Math.min(1, p.life));
      if (p.type === 'conf') h += `<rect x="-7" y="-11" width="14" height="22" rx="3" fill="${p.c}" opacity="${o}" transform="translate(${p.x.toFixed(1)} ${p.y.toFixed(1)}) rotate(${(p.r * 57).toFixed(0)})"/>`;
      else if (p.type === 'spark') h += `<polygon points="${star5(p.x, p.y, 11, 4)}" fill="#fff" opacity="${o * .9}"/>`;
      else if (p.type === 'heart') h += `<path d="${heartPath(p.x, p.y, .7)}" fill="#ff5c93" opacity="${o}"/>`;
      else if (p.type === 'z') h += `<text x="${p.x.toFixed(1)}" y="${p.y.toFixed(1)}" font-size="${p.size}" font-weight="800" font-family="system-ui" fill="#7a62e0" opacity="${o}">z</text>`;
    }
    this.parts = this.parts.filter(p => p.life > 0);
    if (m === 'ring') for (let i = 0; i < 3; i++) {
      const ph = (mt * 1.4 + i / 3) % 1, r = 200 + ph * 110, o = (1 - ph) * .9;
      for (const sgn of [-1, 1]) { const a0 = sgn < 0 ? Math.PI - .5 : -.5, a1 = a0 + 1;
        h += `<path d="M${(cx + Math.cos(a0) * r).toFixed(1)} ${(300 + y + Math.sin(a0) * r).toFixed(1)} A${r} ${r} 0 0 1 ${(cx + Math.cos(a1) * r).toFixed(1)} ${(300 + y + Math.sin(a1) * r).toFixed(1)}" fill="none" stroke="#ffc746" stroke-width="${(12 * (1 - ph) + 3).toFixed(1)}" stroke-linecap="round" opacity="${o.toFixed(2)}"/>`; }
    }
    if (m === 'think') { const k = Math.max(0, Math.min(1, (mt - 1) * 3)), bx = cx + 170, by = 90 + y;
      if (k > 0) h += `<g transform="translate(${bx} ${by}) scale(${k.toFixed(3)})"><circle r="70" fill="url(#${this.P}acc-shine)" opacity=".7"/><circle cy="-6" r="34" fill="url(#${this.P}gold)"/>
        <rect x="-16" y="24" width="32" height="20" rx="7" fill="#a49ad0"/><path d="M-60 -6 H-78M60 -6 H78M-44 -50 L-56 -62M44 -50 L56 -62M0 -66 V-84" stroke="#ffc746" stroke-width="8" stroke-linecap="round"/></g>`; }
    this.fx.innerHTML = h;
  }
}

/* ======================= page ======================= */
const $ = id => document.getElementById(id);
const main = new Yadimo($('main-svg'), {follow: true});
const scenes = [];
document.addEventListener('pointermove', e => {
  const r = $('main-svg').getBoundingClientRect(), radius = Math.min(r.width, r.height) * .4;
  main.pointer.x = Math.max(-1, Math.min(1, (e.clientX - r.left - r.width / 2) / radius));
  main.pointer.y = Math.max(-1, Math.min(1, (e.clientY - r.top - r.height * .45) / radius)); main.pointer.active = true;
});
document.addEventListener('pointerleave', () => main.pointer.active = false);
$('main-svg').addEventListener('pointerdown', () => { const prev = main.mood; main.setMood('celebrate'); main.burst('conf', 10); setTimeout(() => main.mood === 'celebrate' && main.setMood(prev === 'celebrate' ? 'idle' : prev), 1600); });

function chips(id, items, current, pick) {
  const box = $(id); box.innerHTML = '';
  for (const [k, v] of Object.entries(items)) {
    const b = document.createElement('button'); b.className = 'chip' + (k === current() ? ' on' : ''); b.textContent = typeof v === 'string' ? v : v.n;
    b.onclick = () => { pick(k); [...box.children].forEach(x => x.classList.toggle('on', x === b)); }; box.append(b);
  }
}
chips('c-variant', VARIANTS, () => main.o.variant, k => { main.set({variant: k}); syncScenes(); });
chips('c-mood', Object.fromEntries(Object.entries(MOODS).map(([k, v]) => [k, v.n])), () => main.mood, k => main.setMood(k));
chips('c-face', FACES, () => main.o.face, k => { main.setMood('idle'); main.set({face: k}); markMood(); });
chips('c-hat', HATS, () => main.o.hat, k => { main.set({hat: k}); syncScenes(); });
chips('c-glass', GLASSES, () => main.o.glass, k => { main.set({glass: k}); syncScenes(); });
chips('c-neck', NECK, () => main.o.neck, k => { main.set({neck: k}); syncScenes(); });
chips('c-item', ITEMS, () => main.o.item, k => { main.set({item: k}); syncScenes(); });
{ const box = $('c-color');
  for (const [k, v] of Object.entries(COLORS)) { const b = document.createElement('button'); b.className = 'sw' + (k === main.o.color ? ' on' : ''); b.title = v.n;
    b.style.background = `radial-gradient(circle at 35% 30%, ${shade(v.body, 60)}, ${v.body} 55%, ${shade(v.body, -50)})`;
    b.onclick = () => { main.set({color: k}); syncScenes(); [...box.children].forEach(x => x.classList.toggle('on', x === b)); }; box.append(b); } }
$('follow').onchange = e => main.o.follow = e.target.checked;
function markMood() { [...$('c-mood').children].forEach((b, i) => b.classList.toggle('on', Object.keys(MOODS)[i] === main.mood)); }

// scenes share the look chosen above
const yw = new Yadimo($('w-svg')), ya = new Yadimo($('a-svg')), yp = new Yadimo($('p-svg'));
scenes.push(yw, ya, yp);
function syncScenes() { for (const y of scenes) y.set({variant: main.o.variant, color: main.o.color, hat: main.o.hat, glass: main.o.glass, neck: main.o.neck, item: 'none'}); }
let wT = [], pT = [];
function playWidget() { wT.forEach(clearTimeout); $('w-bubble').classList.remove('on'); yw.setMood('fly');
  wT = [setTimeout(() => { $('w-bubble').classList.add('on'); yw.setMood('wave'); }, 1250), setTimeout(() => yw.setMood('talk'), 3000), setTimeout(() => yw.setMood('idle'), 5200)]; }
function playAlarm() { ya.setMood('ring'); }
function playPeek() { pT.forEach(clearTimeout); $('p-bubble').classList.remove('on'); yp.posX.x = 420; yp.posX.t = 420; yp.setMood('idle');
  pT = [setTimeout(() => { yp.posX.t = 60; yp.setMood('wave'); }, 300), setTimeout(() => { $('p-bubble').classList.add('on'); yp.setMood('talk'); }, 1400),
        setTimeout(() => { $('p-bubble').classList.remove('on'); yp.setMood('idle'); }, 3800), setTimeout(() => yp.posX.t = 440, 4500)]; }
$('r-widget').onclick = playWidget; $('r-alarm').onclick = playAlarm; $('r-peek').onclick = playPeek;

let last = performance.now(), fn = 0, ft = 0;
function loop(now) {
  const dt = Math.min(.033, (now - last) / 1000); last = now;
  for (const y of [main, ...scenes]) y.update(dt);
  fn++; ft += dt; if (ft > .5) { $('fps').textContent = Math.round(fn / ft) + ' fps'; fn = 0; ft = 0; }
  requestAnimationFrame(loop);
}
requestAnimationFrame(loop);
setTimeout(playWidget, 500); setTimeout(playAlarm, 300); setTimeout(playPeek, 800);
