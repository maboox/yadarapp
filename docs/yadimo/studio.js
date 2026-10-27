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
chips('c-costume', TEST.COSTUMES, () => main.o.costume, k => { main.set({costume: k, hat: 'none', glass: 'none', neck: 'none'}); for (const id of ['c-hat', 'c-glass', 'c-neck']) [...$(id).children].forEach((b, i) => b.classList.toggle('on', i === 0)); syncScenes(); });
chips('c-titem', TEST.TITEMS, () => main.o.item, k => { main.set({item: k}); [...$('c-item').children].forEach(b => b.classList.remove('on')); syncScenes(); });
{ const box = $('c-color');
  for (const [k, v] of Object.entries(COLORS)) { const b = document.createElement('button'); b.className = 'sw' + (k === main.o.color ? ' on' : ''); b.title = v.n;
    b.style.background = `radial-gradient(circle at 35% 30%, ${shade(v.body, 60)}, ${v.body} 55%, ${shade(v.body, -50)})`;
    b.onclick = () => { main.set({color: k}); syncScenes(); [...box.children].forEach(x => x.classList.toggle('on', x === b)); }; box.append(b); } }
$('follow').onchange = e => main.o.follow = e.target.checked;
function markMood() { [...$('c-mood').children].forEach((b, i) => b.classList.toggle('on', Object.keys(MOODS)[i] === main.mood)); }

// scenes share the look chosen above
const yw = new Yadimo($('w-svg')), ya = new Yadimo($('a-svg')), yp = new Yadimo($('p-svg'));
scenes.push(yw, ya, yp);
function syncScenes() { for (const y of scenes) y.set({variant: main.o.variant, color: main.o.color, hat: main.o.hat, glass: main.o.glass, neck: main.o.neck, costume: main.o.costume, item: 'none'}); }
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
  const dt = Math.max(0, Math.min(.033, (now - last) / 1000)); last = now;
  for (const y of [main, ...scenes]) y.update(dt);
  fn++; ft += dt; if (ft > .5) { $('fps').textContent = Math.round(fn / ft) + ' fps'; fn = 0; ft = 0; }
  requestAnimationFrame(loop);
}
requestAnimationFrame(loop);
setTimeout(playWidget, 500); setTimeout(playAlarm, 300); setTimeout(playPeek, 800);
