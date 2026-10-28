/* Yadimo's room as a little game: a detailed 2.5D room drawn in the same style as the character (soft gradients,
   highlights, soft shadows), with depth (Yadimo walks left/right and towards/away from you), objects Yadimo really
   uses, and a character that is always somewhere: every action starts from where it stands, nothing jumps.
   Tap the floor to send it somewhere, tap it to pat it, drag it to pick it up, tap things in the room to use them. */
(() => {
const NS = 'http://www.w3.org/2000/svg';
// 'demo' (design page), 'app' (the room screen in the app) or 'widget' (makes the home-screen widget's pictures)
const MODE = window.GAME_MODE || 'demo', RATE = window.GAME_RATE || 1;
const $ = id => document.getElementById(id);
const rnd = (a, b) => a + Math.random() * (b - a);
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const lerp = (a, b, k) => a + (b - a) * k;
const el = (tag, attrs = {}, parent) => { const e = document.createElementNS(NS, tag); for (const k in attrs) e.setAttribute(k, attrs[k]); if (parent) parent.appendChild(e); return e; };

// ---------------- the room's geometry: a back wall and a floor with depth ----------------
const W = 400, H = 720, WALL = 430, FLOOR_END = 700;
const yOf = z => WALL + (FLOOR_END - WALL) * z;           // z: 0 at the wall … 1 at the front
const sOf = z => .66 + .44 * z;                           // things nearer are bigger
const BASE = 175;                                         // Yadimo's size at the front
const SPOTS = { bowl: {x: 74, z: .8}, pad: {x: 158, z: .42}, bed: {x: 300, z: .14}, bedSide: {x: 228, z: .3}, plant: {x: 58, z: .2},
  basket: {x: 340, z: .84}, window: {x: 200, z: .22} };

const svg = $('scene');
svg.setAttribute('viewBox', `0 0 ${W} ${H}`);
svg.innerHTML = `<defs>
  <linearGradient id="g-wall" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#f6efff"/><stop offset="1" stop-color="#e6d9fb"/></linearGradient>
  <linearGradient id="g-wall-n" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#3a3170"/><stop offset="1" stop-color="#2b2458"/></linearGradient>
  <pattern id="p-dots" width="26" height="26" patternUnits="userSpaceOnUse"><circle cx="6" cy="6" r="1.6" fill="#d9c9f7"/><circle cx="19" cy="19" r="1.6" fill="#e3d6fa"/></pattern>
  <linearGradient id="g-panel" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#cdb8f2"/><stop offset="1" stop-color="#b9a0ea"/></linearGradient>
  <linearGradient id="g-floor" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#c88c58"/><stop offset=".55" stop-color="#d9a06a"/><stop offset="1" stop-color="#e3ad77"/></linearGradient>
  <radialGradient id="g-sun" cx=".5" cy="0" r="1"><stop offset="0" stop-color="#fff6d6" stop-opacity=".55"/><stop offset="1" stop-color="#fff6d6" stop-opacity="0"/></radialGradient>
  <linearGradient id="g-sky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#6ec2ff"/><stop offset="1" stop-color="#c9ecff"/></linearGradient>
  <linearGradient id="g-sky-n" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#141a46"/><stop offset="1" stop-color="#2f3480"/></linearGradient>
  <linearGradient id="g-wood" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#b47a48"/><stop offset="1" stop-color="#8a5630"/></linearGradient>
  <linearGradient id="g-wood-l" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#d9a26c"/><stop offset="1" stop-color="#b47a48"/></linearGradient>
  <linearGradient id="g-curtain" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#ff9ec0"/><stop offset=".35" stop-color="#ffc0d6"/><stop offset=".7" stop-color="#ff98bb"/><stop offset="1" stop-color="#f07aa6"/></linearGradient>
  <linearGradient id="g-blanket" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#8f9cff"/><stop offset="1" stop-color="#5d6ae6"/></linearGradient>
  <linearGradient id="g-sheet" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ffffff"/><stop offset="1" stop-color="#e6e1fb"/></linearGradient>
  <radialGradient id="g-pillow" cx=".4" cy=".35" r=".8"><stop offset="0" stop-color="#fff4c2"/><stop offset="1" stop-color="#ffd257"/></radialGradient>
  <linearGradient id="g-bowl" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#b6a2ff"/><stop offset="1" stop-color="#6a4ee0"/></linearGradient>
  <radialGradient id="g-cookie" cx=".35" cy=".3" r=".9"><stop offset="0" stop-color="#f0b46a"/><stop offset="1" stop-color="#c47c36"/></radialGradient>
  <radialGradient id="g-ball" cx=".35" cy=".3" r=".8"><stop offset="0" stop-color="#ffb3b3"/><stop offset=".6" stop-color="#f05656"/><stop offset="1" stop-color="#c63a3a"/></radialGradient>
  <linearGradient id="g-pad" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#e9f9ff"/><stop offset="1" stop-color="#9fd8f0"/></linearGradient>
  <radialGradient id="g-glow" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="#7fe6ff" stop-opacity=".9"/><stop offset="1" stop-color="#7fe6ff" stop-opacity="0"/></radialGradient>
  <linearGradient id="g-pot" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#f29a6a"/><stop offset=".5" stop-color="#ffb488"/><stop offset="1" stop-color="#d7744a"/></linearGradient>
  <linearGradient id="g-basket" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ffd27a"/><stop offset="1" stop-color="#e09b3a"/></linearGradient>
  <radialGradient id="g-lamp" cx=".5" cy="0" r="1"><stop offset="0" stop-color="#fff3c0" stop-opacity=".75"/><stop offset="1" stop-color="#fff3c0" stop-opacity="0"/></radialGradient>
  <radialGradient id="g-shadow" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="#3a2560" stop-opacity=".32"/><stop offset="1" stop-color="#3a2560" stop-opacity="0"/></radialGradient>
  <filter id="f-soft" x="-20%" y="-20%" width="140%" height="160%"><feDropShadow dx="0" dy="3" stdDeviation="3" flood-color="#3a2560" flood-opacity=".25"/></filter>
</defs>
<g id="room"></g><g id="floorfx"></g><g id="things"></g><g id="front"></g><g id="dark"></g><g id="ui"></g>`;
const L = {room: $('room'), floorfx: $('floorfx'), things: $('things'), front: $('front'), dark: $('dark'), ui: $('ui')};

// ---------------- the still parts of the room ----------------
const state = {night: new Date().getHours() >= 21 || new Date().getHours() < 6, lamp: true, curtains: true, plant: 0, food: 0,
  needs: {hunger: 65, energy: 70, joy: 70}};
function drawRoom() {
  const n = state.night, g = L.room; g.innerHTML = '';
  el('rect', {x: 0, y: 0, width: W, height: WALL + 2, fill: n ? 'url(#g-wall-n)' : 'url(#g-wall)'}, g);
  el('rect', {x: 0, y: 0, width: W, height: WALL, fill: 'url(#p-dots)', opacity: n ? .15 : .9}, g);
  // crown moulding and wainscot
  el('rect', {x: 0, y: 0, width: W, height: 12, fill: n ? '#4a3f86' : '#fff'}, g); el('rect', {x: 0, y: 12, width: W, height: 3, fill: n ? '#3a3170' : '#e4d6fb'}, g);
  el('rect', {x: 0, y: WALL - 120, width: W, height: 120, fill: n ? '#352c66' : 'url(#g-panel)', opacity: n ? 1 : .55}, g);
  for (let x = 14; x < W; x += 64) el('rect', {x, y: WALL - 106, width: 50, height: 88, rx: 6, fill: 'none', stroke: n ? '#4b4190' : '#fff', 'stroke-opacity': n ? 1 : .55, 'stroke-width': 2.5}, g);
  el('rect', {x: 0, y: WALL - 124, width: W, height: 6, rx: 3, fill: n ? '#4b4190' : '#fff'}, g);
  // floor with boards converging to the back
  el('rect', {x: 0, y: WALL, width: W, height: H - WALL, fill: n ? '#5a3d2a' : 'url(#g-floor)'}, g);
  for (let i = -9; i <= 9; i++) el('line', {x1: W / 2 + i * 26, y1: WALL, x2: W / 2 + i * 62, y2: H, stroke: '#7a4a26', 'stroke-opacity': .22, 'stroke-width': 1.4}, g);
  for (const z of [.14, .32, .54, .8]) { const y = yOf(z); for (let i = 0; i < 6; i++) { const x = ((i * 97 + z * 400) % W); el('line', {x1: x, y1: y, x2: x + 18, y2: y, stroke: '#7a4a26', 'stroke-opacity': .18}, g); } }
  el('rect', {x: 0, y: WALL - 6, width: W, height: 9, fill: n ? '#2b2458' : '#fff'}, g); el('rect', {x: 0, y: WALL + 3, width: W, height: 3, fill: '#000', opacity: .08}, g);
  // window with sky, clouds or moon, curtains and a sill
  const wx = 132, wy = 78, ww = 136, wh = 150;
  el('rect', {x: wx - 12, y: wy - 12, width: ww + 24, height: wh + 24, rx: 16, fill: '#fff', filter: 'url(#f-soft)'}, g);
  el('rect', {x: wx, y: wy, width: ww, height: wh, rx: 9, fill: n ? 'url(#g-sky-n)' : 'url(#g-sky)'}, g);
  const sky = el('g', {id: 'sky'}, g);
  if (n) { el('circle', {cx: wx + 98, cy: wy + 38, r: 17, fill: '#fff6c8'}, sky); el('circle', {cx: wx + 90, cy: wy + 32, r: 15, fill: '#1c2352'}, sky);
    for (let i = 0; i < 14; i++) el('circle', {cx: wx + 8 + (i * 53) % (ww - 16), cy: wy + 10 + (i * 37) % (wh - 30), r: i % 3 ? .9 : 1.5, fill: '#fff', class: 'twinkle', style: `animation-delay:${i * .3}s`}, sky); }
  else { el('circle', {cx: wx + 104, cy: wy + 34, r: 16, fill: '#fff2a8', opacity: .9}, sky);
    for (const [x, y, s] of [[20, 52, 1], [86, 104, 1.2], [140, 70, .8]]) { const c = el('g', {class: 'cloud', transform: `translate(${wx + x} ${wy + y}) scale(${s})`}, sky);
      for (const [a, b, r] of [[0, 0, 13], [14, -6, 15], [30, 0, 12], [15, 5, 12]]) el('circle', {cx: a, cy: b, r, fill: '#fff'}, c); } }
  el('rect', {x: wx + ww / 2 - 3, y: wy, width: 6, height: wh, fill: '#fff'}, g); el('rect', {x: wx, y: wy + wh / 2 - 3, width: ww, height: 6, fill: '#fff'}, g);
  el('rect', {x: wx - 22, y: wy + wh + 10, width: ww + 44, height: 12, rx: 5, fill: 'url(#g-wood-l)', filter: 'url(#f-soft)'}, g);
  // a small cactus on the sill
  el('rect', {x: wx + ww - 22, y: wy + wh - 6, width: 18, height: 16, rx: 3, fill: 'url(#g-pot)'}, g);
  el('rect', {x: wx + ww - 17, y: wy + wh - 26, width: 8, height: 22, rx: 4, fill: '#3fbf7f'}, g); el('rect', {x: wx + ww - 23, y: wy + wh - 18, width: 7, height: 4, rx: 2, fill: '#3fbf7f'}, g);
  // curtains (open or closed)
  const cw = state.curtains ? 30 : ww / 2 + 8;
  el('path', {d: `M${wx - 26} ${wy - 22} h${cw + 6} q-8 ${wh / 2} 0 ${wh + 34} h-${cw + 6} z`, fill: 'url(#g-curtain)', filter: 'url(#f-soft)', id: 'curtL'}, g);
  el('path', {d: `M${wx + ww + 26} ${wy - 22} h-${cw + 6} q8 ${wh / 2} 0 ${wh + 34} h${cw + 6} z`, fill: 'url(#g-curtain)', filter: 'url(#f-soft)', id: 'curtR'}, g);
  el('rect', {x: wx - 40, y: wy - 30, width: ww + 80, height: 10, rx: 5, fill: '#e8a0be'}, g);
  // light from the window on the floor
  if (!n && state.curtains) el('path', {d: `M${wx} ${WALL} L${wx + ww} ${WALL} L${wx + ww + 70} ${yOf(.75)} L${wx - 70} ${yOf(.75)} Z`, fill: 'url(#g-sun)'}, g);
  // shelf with books and a picture frame
  el('rect', {x: 300, y: 214, width: 84, height: 8, rx: 4, fill: 'url(#g-wood-l)', filter: 'url(#f-soft)'}, g);
  [['#ff8f5e', 24], ['#4f9ef0', 30], ['#ffd25a', 22], ['#36c08f', 28], ['#ef7aa8', 26]].forEach(([c, h], i) => {
    el('rect', {x: 306 + i * 12, y: 214 - h, width: 10, height: h, rx: 2, fill: c}, g); el('rect', {x: 307 + i * 12, y: 218 - h, width: 8, height: 2, fill: '#fff', opacity: .5}, g); });
  el('circle', {cx: 372, cy: 202, r: 10, fill: '#a98bf5'}, g); el('circle', {cx: 368, cy: 198, r: 3, fill: '#fff', opacity: .7}, g);
  el('rect', {x: 28, y: 130, width: 62, height: 50, rx: 8, fill: '#ffd25a', filter: 'url(#f-soft)'}, g);
  el('rect', {x: 34, y: 136, width: 50, height: 38, rx: 5, fill: n ? '#2c2f72' : '#bfe6ff'}, g);
  el('path', {d: 'M34 174 L52 152 L64 164 L72 156 L84 174 Z', fill: '#36c08f'}, g); el('circle', {cx: 74, cy: 146, r: 5, fill: '#fff3b8'}, g);
  // wall clock showing the real time
  const ck = el('g', {id: 'clock', transform: 'translate(330 120)'}, g);
  el('circle', {r: 24, fill: '#fff', filter: 'url(#f-soft)'}, ck); el('circle', {r: 24, fill: 'none', stroke: '#7a5cf0', 'stroke-width': 4}, ck);
  for (let i = 0; i < 12; i++) el('circle', {cx: Math.sin(i * Math.PI / 6) * 17, cy: -Math.cos(i * Math.PI / 6) * 17, r: i % 3 ? 1 : 2, fill: '#5b4b7a'}, ck);
  el('line', {id: 'hourH', x1: 0, y1: 0, x2: 0, y2: -10, stroke: '#3b2f63', 'stroke-width': 3, 'stroke-linecap': 'round'}, ck);
  el('line', {id: 'minH', x1: 0, y1: 0, x2: 0, y2: -16, stroke: '#3b2f63', 'stroke-width': 2, 'stroke-linecap': 'round'}, ck); el('circle', {r: 2.5, fill: '#ff6b9a'}, ck);
  // rug
  const ry = yOf(.56);
  el('ellipse', {cx: 205, cy: ry, rx: 150, ry: 40, fill: '#f6a8bf'}, g); el('ellipse', {cx: 205, cy: ry, rx: 132, ry: 32, fill: 'none', stroke: '#fff', 'stroke-width': 3, 'stroke-dasharray': '10 7', opacity: .8}, g);
  el('ellipse', {cx: 205, cy: ry, rx: 92, ry: 20, fill: '#ffc6d6'}, g);
  // the pendant lamp
  const lp = el('g', {id: 'lamp', class: 'tap'}, g);
  el('line', {x1: 104, y1: 0, x2: 104, y2: 52, stroke: '#3b2f63', 'stroke-width': 2}, lp);
  el('path', {d: 'M84 74 Q104 40 124 74 Z', fill: '#ffcf5c', filter: 'url(#f-soft)'}, lp); el('ellipse', {cx: 104, cy: 74, rx: 20, ry: 4, fill: state.lamp ? '#fff7c9' : '#d9b54a'}, lp);
  el('path', {d: 'M90 66 Q96 54 104 52', stroke: '#fff', 'stroke-width': 3, fill: 'none', opacity: .6, 'stroke-linecap': 'round'}, lp);
  el('rect', {x: 70, y: 30, width: 70, height: 60, fill: 'transparent'}, lp);
  drawDark();
}
function drawDark() {
  const d = L.dark; d.innerHTML = '';
  const dim = (state.night ? .38 : 0) + (state.lamp ? 0 : .22) + (state.night && state.lamp ? -.14 : 0);
  if (dim > 0) el('rect', {x: 0, y: 0, width: W, height: H, fill: '#0b0726', opacity: clamp(dim, 0, .6), 'pointer-events': 'none'}, d);
  if (state.lamp) el('ellipse', {cx: 104, cy: 250, rx: 170, ry: 260, fill: 'url(#g-lamp)', opacity: state.night ? .8 : .35, 'pointer-events': 'none'}, d);
}

// ---------------- things on the floor (drawn in depth order) ----------------
function shadow(g, w) { el('ellipse', {cx: 0, cy: 0, rx: w, ry: w * .22, fill: 'url(#g-shadow)'}, g); }
const THINGS = {
  bed(g) { shadow(g, 120);
    el('rect', {x: -112, y: -92, width: 20, height: 92, rx: 9, fill: 'url(#g-wood)'}, g);           // headboard
    el('rect', {x: -110, y: -90, width: 6, height: 70, rx: 3, fill: '#d9a26c', opacity: .7}, g);
    el('rect', {x: 92, y: -58, width: 18, height: 58, rx: 8, fill: 'url(#g-wood)'}, g);              // footboard
    el('rect', {x: -100, y: -40, width: 200, height: 26, rx: 10, fill: 'url(#g-wood)'}, g);
    el('rect', {x: -96, y: -14, width: 12, height: 14, rx: 3, fill: '#6e4428'}, g); el('rect', {x: 82, y: -14, width: 12, height: 14, rx: 3, fill: '#6e4428'}, g);
    el('rect', {x: -96, y: -60, width: 190, height: 26, rx: 12, fill: 'url(#g-sheet)'}, g);          // mattress
    el('ellipse', {cx: -64, cy: -64, rx: 30, ry: 15, fill: 'url(#g-pillow)', filter: 'url(#f-soft)'}, g); },
  blanket(g) {   // drawn in front of Yadimo when it sleeps, and folded back when it doesn't
    const on = yad.mode === 'sleep' || yad.mode === 'tuck';
    if (on) { el('path', {d: 'M-40 -68 Q20 -82 96 -66 L96 -36 Q20 -26 -40 -36 Z', fill: 'url(#g-blanket)', filter: 'url(#f-soft)'}, g);
      for (let i = 0; i < 4; i++) el('circle', {cx: -14 + i * 28, cy: -52, r: 3.5, fill: '#fff', opacity: .55}, g);
      el('path', {d: 'M-40 -68 Q-46 -52 -40 -36', stroke: '#c6ccff', 'stroke-width': 6, fill: 'none', 'stroke-linecap': 'round'}, g); }
    else { el('path', {d: 'M30 -66 Q62 -74 96 -66 L96 -38 Q62 -30 30 -38 Z', fill: 'url(#g-blanket)'}, g);
      el('path', {d: 'M30 -66 Q24 -52 30 -38', stroke: '#c6ccff', 'stroke-width': 6, fill: 'none', 'stroke-linecap': 'round'}, g); } },
  bowl(g) { shadow(g, 34);
    el('ellipse', {cx: 0, cy: -2, rx: 40, ry: 10, fill: '#ffd0e0'}, g);                                // mat
    el('path', {d: 'M-30 -22 L30 -22 Q27 0 0 0 Q-27 0 -30 -22 Z', fill: 'url(#g-bowl)'}, g);
    el('ellipse', {cx: 0, cy: -22, rx: 30, ry: 7, fill: '#8f74f5'}, g); el('ellipse', {cx: 0, cy: -21, rx: 25, ry: 5, fill: '#5b40c9'}, g);
    el('path', {d: 'M-24 -16 Q-20 -8 -10 -5', stroke: '#fff', 'stroke-width': 3, fill: 'none', opacity: .5, 'stroke-linecap': 'round'}, g);
    for (let i = 0; i < Math.ceil(state.food); i++) cookie(g, -12 + i * 12, -24 - (i % 2) * 3, 8);
    el('text', {x: 0, y: -8, 'font-size': 8, 'font-weight': 800, fill: '#fff', 'text-anchor': 'middle', opacity: .9}, g).textContent = 'YADIMO'; },
  pad(g) { shadow(g, 44);
    el('ellipse', {cx: 0, cy: -6, rx: 44, ry: 12, fill: '#6b7fa8'}, g); el('ellipse', {cx: 0, cy: -10, rx: 42, ry: 11, fill: 'url(#g-pad)'}, g);
    el('ellipse', {cx: 0, cy: -10, rx: 30, ry: 7, fill: 'none', stroke: yad.mode === 'charge' ? '#36e0ff' : '#7cc9e8', 'stroke-width': 2.5}, g);
    const b = el('g', {transform: 'translate(0 -10)'}, g); el('path', {d: 'M-4 -3 L2 -3 L-1 3 L4 3 L-3 9 L-1 3 L-5 3 Z', transform: 'scale(1.2) translate(0 -4)', fill: '#ffd25a'}, b); },
  plant(g) { const k = 1 + state.plant * .12; shadow(g, 30);
    el('path', {d: 'M-24 -46 L24 -46 L18 0 L-18 0 Z', fill: 'url(#g-pot)'}, g); el('rect', {x: -28, y: -52, width: 56, height: 10, rx: 4, fill: '#e8885f'}, g);
    const lv = el('g', {transform: `translate(0 -50) scale(${k})`}, g);
    for (const [a, l, c] of [[-60, 58, '#2fa86c'], [-25, 74, '#3fbf7f'], [10, 80, '#4fd08e'], [45, 62, '#3fbf7f'], [80, 48, '#2fa86c']]) {
      const r = a * Math.PI / 180; el('path', {d: `M0 0 Q${Math.sin(r) * l * .3 - 10} ${-l * .5} ${Math.sin(r) * l * .9} ${-Math.cos(r) * l} Q${Math.sin(r) * l * .3 + 10} ${-l * .4} 0 0`, fill: c}, lv); }
    if (state.plant >= 2) for (const [x, y] of [[-18, -60], [16, -70], [4, -82]].slice(0, state.plant - 1)) { el('circle', {cx: x, cy: y, r: 7, fill: '#ff8fb6'}, lv); el('circle', {cx: x, cy: y, r: 3, fill: '#ffe08a'}, lv); } },
  basket(g) { shadow(g, 40);
    el('path', {d: 'M-36 -40 L36 -40 L30 0 L-30 0 Z', fill: 'url(#g-basket)'}, g);
    for (let i = -2; i <= 2; i++) el('line', {x1: i * 13, y1: -40, x2: i * 11, y2: 0, stroke: '#c8862f', 'stroke-width': 2}, g);
    for (const y of [-28, -14]) el('line', {x1: -33, y1: y, x2: 33, y2: y, stroke: '#c8862f', 'stroke-width': 2}, g);
    el('rect', {x: -40, y: -46, width: 80, height: 9, rx: 4, fill: '#ffb84d'}, g);
    if (!ball.out) { el('circle', {cx: 8, cy: -50, r: 15, fill: 'url(#g-ball)'}, g); el('path', {d: 'M-7 -50 Q8 -42 23 -50', stroke: '#fff', 'stroke-width': 4, fill: 'none'}, g); }
    el('rect', {x: -24, y: -60, width: 9, height: 22, rx: 3, fill: '#4f9ef0', transform: 'rotate(-14 -20 -50)'}, g); },
};
function cookie(g, x, y, r) { el('circle', {cx: x, cy: y, r, fill: 'url(#g-cookie)'}, g);
  for (const [a, b] of [[-.35, -.25], [.3, -.05], [-.05, .4]]) el('circle', {cx: x + a * r, cy: y + b * r, r: r * .17, fill: '#5a3412'}, g); }

// ---------------- Yadimo ----------------
const yadHolder = el('g', {id: 'yad-holder'});
const yadRot = el('g', {}, yadHolder);
const yadSvg = el('svg', {viewBox: '0 0 600 600', width: 100, height: 100, overflow: 'visible'}, yadRot);
const engine = new Yadimo(yadSvg, {variant: 'robot', color: 'lilac', hat: 'party', glass: 'sun'});
const yad = {x: 200, z: .6, h: 0, vh: 0, mode: 'idle', path: null, then: null, timer: 3, face: 1, lie: 0, t: 0, held: false};
const ball = {out: false, x: 0, z: 0, h: 0, vx: 0, vz: 0, vh: 0, spin: 0};
const drops = [];   // falling cookies, water drops, hearts…

function say(text, secs = 2.6) { bubble.text = text; bubble.t = secs; }
const bubble = {text: '', t: 0};

/** Walk from wherever Yadimo is now to (x, z), then call [then]. */
function walkTo(x, z, then, speed = 120) {
  if (yad.mode === 'sleep') { wake(() => walkTo(x, z, then, speed)); return; }
  yad.path = {x: clamp(x, 26, W - 26), z: clamp(z, .26, .97), speed}; yad.then = then; yad.mode = 'walk';
}
function wake(then) { yad.mode = 'getup'; yad.timer = .7; yad.then = then; engine.setMood('wave'); say('صبح بخیر! ☀️'); }

const ACT = {
  feed() { if (state.food < 1) { for (let i = 0; i < 3; i++) drops.push({kind: 'cookie', x: SPOTS.bowl.x - 10 + i * 10, z: SPOTS.bowl.z, h: 260 + i * 40, vh: 0}); }
    say('غذا! 🍪'); walkTo(SPOTS.bowl.x + 44, SPOTS.bowl.z, () => { yad.mode = 'eat'; yad.timer = 3.2; engine.setMood('love'); }); },
  charge() { say('بریم شارژ! ⚡'); walkTo(SPOTS.pad.x, SPOTS.pad.z + .02, () => { yad.mode = 'charge'; yad.timer = 3; engine.setMood('celebrate'); }); },
  sleep() { if (yad.mode === 'sleep') { wake(); return; } say('خوابم میاد… 🥱'); walkTo(SPOTS.bedSide.x, SPOTS.bedSide.z, () => { yad.mode = 'tuck'; yad.timer = .8; }); },
  play() { if (!ball.out) { refreshThings = true; Object.assign(ball, {out: true, x: SPOTS.basket.x, z: SPOTS.basket.z - .02, h: 50, vx: -140, vz: -.25, vh: 260}); }
    say('توپ! ⚽'); yad.mode = 'chase'; yad.kicks = 0; yad.path = null; },
  pet() { if (yad.mode === 'sleep') { wake(); return; } yad.mode = 'pet'; yad.timer = 2; engine.setMood('love'); state.needs.joy = Math.min(100, state.needs.joy + 12);
    for (let i = 0; i < 4; i++) drops.push({kind: 'heart', x: yad.x + rnd(-30, 30), z: yad.z, h: 120 + rnd(0, 30), vh: 40, life: 1.4}); say('آخ جون! 🥰'); },
  water() { say('آب بدیم! 🌱'); walkTo(SPOTS.plant.x + 52, SPOTS.plant.z + .12, () => { yad.mode = 'water'; yad.timer = 2.6; engine.setMood('celebrate'); }); },
};

// ---------------- each frame ----------------
let last = performance.now();
function frame(now) {
  step(Math.min(.05, (now - last) / 1000)); last = now;
  requestAnimationFrame(frame);
}
/** Moves the whole room on by dt seconds and draws it. */
function step(dt) {
  yad.t += dt;
  const N = state.needs, k = dt * RATE; N.hunger = Math.max(0, N.hunger - k * .5); N.energy = Math.max(0, N.energy - k * .35); N.joy = Math.max(0, N.joy - k * .3);
  // the clock
  const d = new Date(), hh = (d.getHours() % 12 + d.getMinutes() / 60) * 30, mm = d.getMinutes() * 6;
  $('hourH').setAttribute('transform', `rotate(${hh})`); $('minH').setAttribute('transform', `rotate(${mm})`);
  document.querySelectorAll('.cloud').forEach((c, i) => { const m = c.getAttribute('transform').match(/translate\(([-\d.]+) ([-\d.]+)\) scale\(([\d.]+)\)/); if (!m) return;
    let x = +m[1] + dt * (4 + i * 2); if (x > 268 + 10) x = 110; c.setAttribute('transform', `translate(${x} ${m[2]}) scale(${m[3]})`); });

  // behaviour
  const y = yad;
  let walking = false;
  if (y.held) { /* follows the finger, see pointer handlers */ }
  else if (y.mode === 'walk' || (y.mode === 'chase' && ball.out)) {
    const target = y.mode === 'chase' ? {x: ball.x + (ball.x > y.x ? -36 : 36) * sOf(ball.z), z: ball.z, speed: 150} : y.path;
    const dx = target.x - y.x, dz = (target.z - y.z) * 260, dist = Math.hypot(dx, dz);
    if (dist > 3) { const v = Math.min(dist, target.speed * dt * (y.mode === 'chase' ? 1 : 1)); y.x += dx / dist * v; y.z += dz / dist * v / 260; if (Math.abs(dx) > 2) y.face = Math.sign(dx); walking = true; }
    else if (y.mode === 'walk') { y.mode = 'idle'; y.timer = rnd(4, 8); const f = y.then; y.then = null; engine.setMood('idle'); if (f) f(); }
    if (y.mode === 'chase' && Math.abs(ball.x - y.x) < 46 * sOf(y.z) && Math.abs(ball.z - y.z) < .08 && ball.h < 20 && (y.kickT || 0) <= 0) {
      ball.vx = Math.sign(ball.x - y.x || 1) * rnd(170, 260); ball.vz = rnd(-.35, .35); ball.vh = rnd(180, 300); ball.spin = ball.vx / 20;
      y.kickT = .5; y.kicks++; engine.setMood('celebrate'); state.needs.joy = Math.min(100, state.needs.joy + 6);
      if (y.kicks >= 5) { y.mode = 'idle'; y.timer = 2.5; say('خیلی خوش گذشت! 😄'); setTimeout(() => { if (yad.mode === 'idle') ACT_putAway(); }, 2600); }
    }
  } else if (y.mode === 'eat') {
    state.food = Math.max(0, state.food - dt / 3.2 * 3); engine.posY.t = -Math.abs(Math.sin(y.t * 12)) * 16;
    if (Math.random() < dt * 10) drops.push({kind: 'crumb', x: y.x - 30 * y.face, z: y.z, h: 40, vh: 120, vx: rnd(-40, 40), life: .7});
    if ((y.timer -= dt) <= 0) { y.mode = 'idle'; y.timer = 3; state.needs.hunger = 100; engine.setMood('idle'); say('سیرم، مرسی! 😋'); }
  } else if (y.mode === 'charge') {
    if (Math.random() < dt * 14) drops.push({kind: 'ring', x: y.x, z: y.z, h: 0, vh: 90, life: 1});
    if (Math.random() < dt * 20) drops.push({kind: 'spark', x: y.x + rnd(-40, 40), z: y.z, h: rnd(20, 140), vh: 30, life: .6});
    if ((y.timer -= dt) <= 0) { y.mode = 'idle'; y.timer = 3; state.needs.energy = 100; engine.setMood('idle'); say('شارژ شدم! 💯'); }
  } else if (y.mode === 'tuck') {   // climbing into bed
    y.lie = Math.min(1, y.lie + dt / .8); y.x = lerp(SPOTS.bedSide.x, SPOTS.bed.x + 4, y.lie); y.z = lerp(SPOTS.bedSide.z, SPOTS.bed.z, y.lie);
    if (y.lie >= 1) { y.mode = 'sleep'; y.timer = state.night ? 1e9 : rnd(10, 16); engine.setMood('sleep'); say('شب بخیر… 😴', 2); }
  } else if (y.mode === 'sleep') {
    state.needs.energy = Math.min(100, state.needs.energy + dt * 6);
    if (Math.random() < dt * 1.2) drops.push({kind: 'z', x: y.x + 30, z: y.z, h: 70, vh: 30, life: 1.8});
    if (!state.night && (y.timer -= dt) <= 0) wake();
  } else if (y.mode === 'getup') {
    y.lie = Math.max(0, y.lie - dt / .7); y.x = lerp(SPOTS.bedSide.x, SPOTS.bed.x + 4, y.lie); y.z = lerp(SPOTS.bedSide.z, SPOTS.bed.z, y.lie);
    if (y.lie <= 0) { y.mode = 'idle'; y.timer = 3; engine.setMood('idle'); const f = y.then; y.then = null; if (f) f(); }
  } else if (y.mode === 'water') {
    if (Math.random() < dt * 16) drops.push({kind: 'water', x: SPOTS.plant.x + rnd(-14, 18), z: SPOTS.plant.z, h: 110, vh: -30, life: 1});
    if ((y.timer -= dt) <= 0) { y.mode = 'idle'; y.timer = 3; state.plant = Math.min(4, state.plant + 1); refreshThings = true; engine.setMood('love'); say('ببین چه گل قشنگی! 🌸'); }
  } else if (y.mode === 'pet' || y.mode === 'fall') {
    if ((y.timer -= dt) <= 0) { y.mode = 'idle'; y.timer = 3; engine.setMood('idle'); }
  } else if (y.mode === 'idle' && (y.timer -= dt) <= 0) {
    // left alone: look after itself
    const n = state.needs; y.timer = rnd(5, 9);
    if (state.night) ACT.sleep();
    else if (n.hunger < 35) { say('گشنمه… 🍪'); ACT.feed(); }
    else if (n.energy < 30) Math.random() < .5 ? ACT.charge() : ACT.sleep();
    else if (n.joy < 40) ACT.play();
    else { const r = Math.random();
      if (r < .2) { walkTo(SPOTS.window.x + rnd(-30, 30), SPOTS.window.z + .06, () => { engine.setMood('think'); say('چه هوای خوبی! ☁️'); yad.timer = 4; }); }
      else if (r < .3 && state.plant < 4) ACT.water();
      else if (r < .45) { engine.setMood('celebrate'); say('🎵 لا لا لا'); yad.mode = 'pet'; yad.timer = 2.2; }
      else walkTo(rnd(40, 360), rnd(.35, .95)); }
  }
  if (y.kickT) y.kickT -= dt;

  // the ball
  if (ball.out) { ball.vh -= 900 * dt; ball.x += ball.vx * dt; ball.z += ball.vz * dt; ball.h += ball.vh * dt; ball.spin += ball.vx * dt / 12;
    if (ball.h <= 0) { ball.h = 0; ball.vh = Math.abs(ball.vh) > 60 ? -ball.vh * .55 : 0; ball.vx *= .8; ball.vz *= .8; }
    if (ball.x < 20 || ball.x > W - 20) { ball.vx = -ball.vx * .8; ball.x = clamp(ball.x, 20, W - 20); }
    if (ball.z < .28 || ball.z > .97) { ball.vz = -ball.vz; ball.z = clamp(ball.z, .28, .97); }
    if (ball.h === 0) { ball.vx *= 1 - dt * 1.4; ball.vz *= 1 - dt * 1.4; } }
  // falling things
  for (const p of drops) { p.vh = (p.vh || 0) - (p.kind === 'cookie' || p.kind === 'crumb' || p.kind === 'water' ? 700 : 0) * dt; p.h += p.vh * dt; p.x += (p.vx || 0) * dt;
    if (p.life !== undefined) p.life -= dt;
    if (p.kind === 'cookie' && p.h <= (p.stop || 26)) { p.done = true; state.food = Math.min(3, state.food + 1); refreshThings = true; }
    if ((p.kind === 'crumb' || p.kind === 'water') && p.h <= 0) p.done = true; }
  for (let i = drops.length - 1; i >= 0; i--) if (drops[i].done || drops[i].life <= 0) drops.splice(i, 1);

  // character pose
  engine.posY.t = y.mode === 'eat' ? engine.posY.t : walking ? -Math.abs(Math.sin(y.t * 11)) * 40 : y.held ? -30 : 0;
  engine.o.follow = walking || y.mode === 'chase'; engine.pointer.active = engine.o.follow; engine.pointer.x = y.face * .85; engine.pointer.y = .1;
  if (y.mode === 'fall') { y.vh -= 1400 * dt; y.h += y.vh * dt; if (y.h <= 0) { y.h = 0; if (Math.abs(y.vh) > 220) { y.vh = -y.vh * .35; engine.burst('spark', 6); } else y.vh = 0; } }
  engine.update(dt);
  render();
}
let refreshThings = true;
function ACT_putAway() { if (!ball.out) return; walkTo(ball.x, ball.z, () => { ball.out = false; refreshThings = true; say('سر جاش! 🧺'); }); }

// ---------------- drawing the moving parts ----------------
const cache = {};
function thing(name, x, z, extra) {
  let g = cache[name];
  if (!g || refreshThings) { if (g) g.remove(); g = cache[name] = el('g', {class: 'tap', 'data-thing': name}); THINGS[name](g, extra); }
  g.setAttribute('transform', `translate(${x} ${yOf(z)}) scale(${sOf(z)})`); g.dataset.z = z; return g;
}
function render() {
  const y = yad, list = [];
  list.push(thing('plant', SPOTS.plant.x, SPOTS.plant.z));
  list.push(thing('bed', SPOTS.bed.x, SPOTS.bed.z));
  list.push(thing('pad', SPOTS.pad.x, SPOTS.pad.z));
  list.push(thing('bowl', SPOTS.bowl.x, SPOTS.bowl.z));
  list.push(thing('basket', SPOTS.basket.x, SPOTS.basket.z));
  // Yadimo
  const s = sOf(y.z), size = BASE * s;
  yadSvg.setAttribute('width', size); yadSvg.setAttribute('height', size);
  yadSvg.setAttribute('x', -size / 2); yadSvg.setAttribute('y', -size * .9);
  // in bed: lying on the mattress, head on the pillow (the bed's left end), under the blanket
  const sb = sOf(SPOTS.bed.z), lift = y.lie * (60 * sb - .24 * size) + y.h;
  yadHolder.setAttribute('transform', `translate(${y.x} ${yOf(y.z) - lift})`);
  yadRot.setAttribute('transform', `rotate(${-y.lie * 80} 0 ${-size * .42})`);
  yadHolder.dataset.z = y.lie > .5 ? SPOTS.bed.z + .001 : y.z;
  list.push(yadHolder);
  // the blanket goes over Yadimo in bed
  const bl = cache.blanket; if (bl) bl.remove();
  cache.blanket = el('g', {}); THINGS.blanket(cache.blanket); cache.blanket.setAttribute('transform', `translate(${SPOTS.bed.x} ${yOf(SPOTS.bed.z)}) scale(${sOf(SPOTS.bed.z)})`);
  cache.blanket.dataset.z = SPOTS.bed.z + .002; list.push(cache.blanket);
  // the ball
  if (ball.out) { let b = cache.ball; if (!b) { b = cache.ball = el('g', {class: 'tap', 'data-thing': 'ball'}); el('ellipse', {class: 'sh', cx: 0, cy: 0, rx: 16, ry: 4, fill: 'url(#g-shadow)'}, b);
      const bb = el('g', {class: 'bb'}, b); el('circle', {r: 15, fill: 'url(#g-ball)'}, bb); el('path', {d: 'M-15 0 Q0 8 15 0', stroke: '#fff', 'stroke-width': 4, fill: 'none'}, bb); el('circle', {cx: -5, cy: -6, r: 4, fill: '#fff', opacity: .6}, bb); }
    const bs = sOf(ball.z); b.setAttribute('transform', `translate(${ball.x} ${yOf(ball.z)}) scale(${bs})`);
    b.querySelector('.bb').setAttribute('transform', `translate(0 ${-15 - ball.h}) rotate(${ball.spin * 57 % 360})`); b.dataset.z = ball.z; list.push(b); }
  else if (cache.ball) { cache.ball.remove(); cache.ball = null; }
  refreshThings = yad.mode === 'eat' || yad.mode === 'charge';
  list.sort((a, b) => a.dataset.z - b.dataset.z);
  for (const g of list) L.things.appendChild(g);
  // particles
  const f = L.front; f.innerHTML = '';
  for (const p of drops) { const py = yOf(p.z) - p.h, o = p.life !== undefined ? clamp(p.life, 0, 1) : 1;
    if (p.kind === 'cookie') cookie(f, p.x, py, 8 * sOf(p.z));
    else if (p.kind === 'crumb') el('circle', {cx: p.x, cy: py, r: 2.2, fill: '#c47c36', opacity: o}, f);
    else if (p.kind === 'water') el('path', {d: `M${p.x} ${py - 5} q4 6 0 9 q-4 -3 0 -9`, fill: '#5ec8ff', opacity: o}, f);
    else if (p.kind === 'heart') el('path', {d: `M${p.x} ${py + 6} c-12 -8 -12 -18 -4 -18 c3 0 4 3 4 3 c0 0 1 -3 4 -3 c8 0 8 10 -4 18z`, fill: '#ff5c93', opacity: o}, f);
    else if (p.kind === 'spark') el('circle', {cx: p.x, cy: py, r: 2.6, fill: '#fff07a', opacity: o}, f);
    else if (p.kind === 'ring') el('ellipse', {cx: p.x, cy: py - 6, rx: 34 * sOf(p.z), ry: 9 * sOf(p.z), fill: 'none', stroke: '#36e0ff', 'stroke-width': 3, opacity: o * .9}, f);
    else if (p.kind === 'z') { const t = el('text', {x: p.x, y: py, 'font-size': 14 + (1.8 - p.life) * 6, 'font-weight': 800, fill: '#7a62e0', opacity: o}, f); t.textContent = 'z'; } }
  if (yad.mode === 'charge') { const s2 = sOf(y.z); el('ellipse', {cx: y.x, cy: yOf(y.z) - 70 * s2, rx: 70 * s2, ry: 110 * s2, fill: 'url(#g-glow)', opacity: .35}, f);
    const bx = y.x, by = yOf(y.z) - BASE * s2 * 1.02; el('rect', {x: bx - 20, y: by, width: 40, height: 18, rx: 5, fill: '#fff', filter: 'url(#f-soft)'}, f);
    el('rect', {x: bx - 16, y: by + 4, width: 32 * (1 - Math.max(0, yad.timer) / (yad.chargeT || 3)), height: 10, rx: 3, fill: '#36c08f'}, f); el('rect', {x: bx + 20, y: by + 5, width: 4, height: 8, rx: 1, fill: '#fff'}, f); }
  if (yad.mode === 'water') { const s2 = sOf(y.z), cx = y.x - 46 * s2, cy = yOf(y.z) - 80 * s2;
    const can = el('g', {transform: `translate(${cx} ${cy}) rotate(-28) scale(${s2})`}, f);
    el('rect', {x: -16, y: -14, width: 32, height: 26, rx: 6, fill: '#4fb3ff'}, can); el('path', {d: 'M-16 -6 L-38 -18 L-36 -12 L-16 2 Z', fill: '#3a9be8'}, can); el('path', {d: 'M8 -14 Q22 -26 16 0', stroke: '#3a9be8', 'stroke-width': 4, fill: 'none'}, can); }
  // the speech bubble over Yadimo's head
  const u = L.ui; u.innerHTML = '';
  if (bubble.t > 0) { bubble.t -= 1 / 60;
    const s2 = sOf(y.z), bx = clamp(y.x, 70, W - 70), by = yOf(y.z) - lift - BASE * s2 * (y.lie > .5 ? .55 : .98) - 14;
    const g = el('g', {transform: `translate(${bx} ${by})`}, u);
    const r = el('rect', {x: -60, y: -28, width: 120, height: 30, rx: 15, fill: '#fff', filter: 'url(#f-soft)'}, g);
    el('path', {d: `M${clamp(y.x - bx, -40, 40) - 7} 1 L${clamp(y.x - bx, -40, 40) + 7} 1 L${clamp(y.x - bx, -40, 40)} 11 Z`, fill: '#fff'}, g);
    const t = el('text', {x: 0, y: -8, 'font-size': 14, 'font-weight': 800, fill: '#231c47', 'text-anchor': 'middle', direction: 'rtl'}, g); t.textContent = bubble.text;
    const w = Math.min(W - 20, t.getComputedTextLength() + 28); r.setAttribute('x', -w / 2); r.setAttribute('width', w); }
  // needs
  for (const k of ['hunger', 'energy', 'joy']) { const b = $('n-' + k); if (b) b.style.width = state.needs[k].toFixed(0) + '%'; }
}

// ---------------- touch ----------------
function toScene(e) { const p = svg.createSVGPoint(); p.x = e.clientX; p.y = e.clientY; return p.matrixTransform(svg.getScreenCTM().inverse()); }
let press = null;
svg.addEventListener('pointerdown', e => {
  const p = toScene(e), s = sOf(yad.z), size = BASE * s;
  const onYad = Math.abs(p.x - yad.x) < size * .32 && p.y < yOf(yad.z) - yad.h && p.y > yOf(yad.z) - yad.h - size * .9 && yad.lie < .5;
  press = {x: p.x, y: p.y, onYad, moved: false, target: e.target.closest('[data-thing]')?.dataset.thing || (e.target.closest('#lamp') ? 'lamp' : e.target.closest('#curtL,#curtR,#sky') ? 'window' : null)};
  svg.setPointerCapture(e.pointerId);
});
svg.addEventListener('pointermove', e => {
  if (!press) return; const p = toScene(e);
  if (press.onYad && Math.hypot(p.x - press.x, p.y - press.y) > 10) { press.moved = true;
    if (!yad.held) { yad.held = true; yad.mode = 'held'; engine.setMood('surprised' in MOODS ? 'surprised' : 'wave'); say('وای! 😲', 1.4); } }
  if (yad.held) { const z = clamp((p.y + 60 - WALL) / (FLOOR_END - WALL), .3, .97); yad.z = z; yad.x = clamp(p.x, 26, W - 26); yad.h = Math.max(0, yOf(z) - p.y - 60); }
});
svg.addEventListener('pointerup', e => {
  if (!press) return; const p = toScene(e), pr = press; press = null;
  if (yad.held) { yad.held = false; yad.mode = 'fall'; yad.vh = 0; yad.timer = 1.2; engine.setMood('celebrate'); say('دوباره! 😆'); return; }
  if (pr.onYad) { ACT.pet(); return; }
  switch (pr.target) {
    case 'bowl': ACT.feed(); return;
    case 'pad': ACT.charge(); return;
    case 'bed': ACT.sleep(); return;
    case 'basket': ACT.play(); return;
    case 'plant': ACT.water(); return;
    case 'ball': ball.vx = (ball.x - p.x) * 8 + rnd(-60, 60); ball.vh = 280; ball.vz = rnd(-.3, .3); if (yad.mode !== 'chase') { yad.mode = 'chase'; yad.kicks = 2; } return;
    case 'lamp': state.lamp = !state.lamp; drawRoom(); return;
    case 'window': state.curtains = !state.curtains; drawRoom(); say(state.curtains ? 'نور اومد! ☀️' : 'یه کم تاریک‌تر 🌙'); return;
  }
  if (p.y > WALL + 10) walkTo(p.x, (p.y - WALL) / (FLOOR_END - WALL));
});

// ---------------- buttons and options ----------------
document.querySelectorAll('[data-act]').forEach(b => b.onclick = () => ACT[b.dataset.act]());
function setNight(n) { state.night = n; drawRoom(); if (n) ACT.sleep(); else if (yad.mode === 'sleep') wake(); }
if ($('night')) $('night').onclick = () => { setNight(!state.night); $('night').textContent = state.night ? '☀️ روز' : '🌙 شب'; };
document.querySelectorAll('[data-look]').forEach(b => b.onclick = () => { engine.set(JSON.parse(b.dataset.look)); document.querySelectorAll('[data-look]').forEach(x => x.classList.toggle('on', x === b)); });
/** Used by the app: act('feed'…), look(json), night(bool), needs() → JSON, load({needs, plant}). */
window.yadimoGame = {ACT, state, yad, engine, say,
  act(k) { if (ACT[k]) ACT[k](); },
  look(json) { engine.set(typeof json === 'string' ? JSON.parse(json) : json); },
  night(n) { if (!!n !== state.night) setNight(!!n); },
  load(o) { o = typeof o === 'string' ? JSON.parse(o) : o; if (o.needs) Object.assign(state.needs, o.needs); if (o.plant != null) { state.plant = o.plant; refreshThings = true; } },
  save() { return JSON.stringify({needs: state.needs, plant: state.plant}); },
};

// ---------------- pictures for the home-screen widget ----------------
/* The widget is a little window you watch, not a remote control: Yadimo lives its own life there (waters the
   plant, reads, looks outside, plays with its ball, eats, sleeps at night) and talks to itself now and then.
   Tapping it opens the game. A widget can only flip through ready pictures, so the room is one still picture and
   Yadimo is a few short loops of small pictures ("sprites") drawn at about the size it has in the widget; an
   episode is a list of frames saying which sprite goes where, plus what Yadimo says. Walking is a walk loop moved
   a little each frame; the same sprites are used again and again, so a long episode costs little memory. */
const SER = new XMLSerializer();
function svgImage(markup) { return new Promise((ok, bad) => { const i = new Image(); i.onload = () => ok(i); i.onerror = bad; i.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(markup); }); }

/** In the widget's room things sit a little nearer the rug, so more of them are in the picture. */
const WIDGET_SPOTS = {plant: {x: 58, z: .42}, bowl: {x: 92, z: .68}, basket: {x: 336, z: .66}, pad: {x: 272, z: .36}};
const LINE_Z = .52;   // Yadimo walks along this line, so it is always the same size

/** Things Yadimo holds or makes in a loop, drawn in front of it; k goes 0…1 over the loop. */
const PROPS = {
  water(f, k, s) { const cx = yad.x - 40 * s, cy = yOf(yad.z) - 82 * s;
    const can = el('g', {transform: `translate(${cx} ${cy}) rotate(-26) scale(${s})`}, f);
    el('rect', {x: -16, y: -14, width: 32, height: 26, rx: 6, fill: '#4fb3ff'}, can); el('path', {d: 'M-16 -6 L-38 -18 L-36 -12 L-16 2 Z', fill: '#3a9be8'}, can);
    el('path', {d: 'M8 -14 Q22 -26 16 0', stroke: '#3a9be8', 'stroke-width': 4, fill: 'none'}, can);
    for (let i = 0; i < 4; i++) { const p = (k + i / 4) % 1, x = cx - 34 * s - p * 10 * s, y = cy + 4 * s + p * 46 * s;
      el('path', {d: `M${x} ${y - 5 * s} q${4 * s} ${6 * s} 0 ${9 * s} q${-4 * s} ${-3 * s} 0 ${-9 * s}`, fill: '#5ec8ff', opacity: 1 - p * .6}, f); } },
  read(f, k, s) { const x = yad.x + 4 * s, y = yOf(yad.z) - 58 * s, g = el('g', {transform: `translate(${x} ${y}) scale(${s})`}, f);
    el('path', {d: 'M0 -2 L-30 -10 L-30 16 L0 22 Z', fill: '#ff8f5e'}, g); el('path', {d: 'M0 -2 L30 -10 L30 16 L0 22 Z', fill: '#ff7a48'}, g);
    el('path', {d: 'M0 0 L-27 -7 L-27 13 L0 19 Z', fill: '#fff'}, g); el('path', {d: 'M0 0 L27 -7 L27 13 L0 19 Z', fill: '#f4f0ff'}, g);
    for (let i = 0; i < 3; i++) { el('line', {x1: -22, y1: -2 + i * 5, x2: -5, y2: 2 + i * 5, stroke: '#b8aee0', 'stroke-width': 1.4}, g); el('line', {x1: 5, y1: 2 + i * 5, x2: 22, y2: -2 + i * 5, stroke: '#b8aee0', 'stroke-width': 1.4}, g); }
    if (k > .55 && k < .9) { const p = (k - .55) / .35; el('path', {d: `M0 0 L${27 - p * 54} ${-7 - Math.sin(p * Math.PI) * 8} L${27 - p * 54} ${13 - Math.sin(p * Math.PI) * 8} L0 19 Z`, fill: '#fff', stroke: '#e0d8f8'}, g); } },
  eat(f, k, s) { const x = yad.x - 14 * s, y = yOf(yad.z) - 70 * s; cookie(f, x, y, 9 * s);
    for (let i = 0; i < 3; i++) { const p = (k + i / 3) % 1; el('circle', {cx: x - 6 * s + i * 7 * s, cy: y + 6 * s + p * 50 * s, r: 2 * s, fill: '#c47c36', opacity: 1 - p}, f); } },
  sleep(f, k, s) { for (let i = 0; i < 3; i++) { const p = (k + i / 3) % 1; const t = el('text', {x: yad.x + 26 * s + p * 18 * s, y: yOf(yad.z) - 70 * s - p * 60 * s,
    'font-size': (12 + p * 10) * s, 'font-weight': 800, fill: '#7a62e0', opacity: Math.sin(p * Math.PI)}, f); t.textContent = 'z'; } },
};

/** The loops: mood, which way Yadimo looks (-1 left, 1 right, 0 at you), frames, and how they play. */
const SETS = {
  idle: {mood: 'idle', n: 6, pingpong: true, secs: 1.6},
  talk: {mood: 'talk', n: 6, pingpong: true, secs: 1},
  think: {mood: 'think', n: 6, pingpong: true, secs: 1.6},
  walkL: {mood: 'idle', face: -1, n: 6, walk: true, secs: .75},
  walkR: {mood: 'idle', face: 1, n: 6, walk: true, secs: .75},
  water: {mood: 'love', face: -1, n: 6, prop: 'water', secs: .9},
  read: {mood: 'think', n: 8, prop: 'read', secs: 2.4},
  eat: {mood: 'love', face: -1, n: 6, prop: 'eat', chew: true, secs: .75},
  dance: {mood: 'celebrate', n: 8, secs: .98},
  sleep: {mood: 'sleep', n: 8, prop: 'sleep', bed: true, secs: 2.4},
};

/** What Yadimo says to itself; {fa, en}. */
const SAY = {
  hi: ['سلام! کسی اونجاست؟ 👀', 'Hello? Anyone there? 👀'],
  water: ['بریم یه کم به گل آب بدم 🌱', "Let's water my plant 🌱"], flower: ['ببین چه قشنگ شده! 🌸', 'Look how pretty it is! 🌸'],
  sky: ['بیرون چه خبره؟ ☁️', "What's going on outside? ☁️"],
  bored: ['چقدر بیکارم… هیچکی باهام کاری نداره 🥱', "So bored… nobody's around 🥱"], read: ['یه کم مطالعه کنم 📖', 'Time for a little reading 📖'],
  song: ['🎵 لا لا لا', '🎵 la la la'], ball: ['توپم کجاست؟ ⚽', "Where's my ball? ⚽"], goal: ['گل! 😄', 'Goal! 😄'],
  hungry: ['دلم یه کلوچه می‌خواد 🍪', 'I want a cookie 🍪'], yum: ['به به! 😋', 'Yum! 😋'], night: ['خرررر… 😴', 'Zzz… 😴'],
};

window.widgetFrames = {
  /** o: {w, h (dp), density, look, fps, budget, lang}; answers through window.RoomHost:
   *  file(name, png) for every picture, then finished(json) with the episodes. */
  async make(o) {
    const host = window.RoomHost || {file() {}, finished() {}, error(e) { console.error(e); }};
    try {
      o = typeof o === 'string' ? JSON.parse(o) : o;
      const fa = o.lang !== 'en', fps = clamp(o.fps || 8, 4, 12);
      Object.assign(SPOTS, WIDGET_SPOTS); engine.set(o.look || {});
      // the part of the room the widget shows, centred with the walking line at 85% of the height
      const aspect = o.w / o.h; let cw = Math.min(W, o.w * 1.18), ch = cw / aspect;
      // a short widget shows a little more of the room, so Yadimo and what it says always fit
      const need = BASE * sOf(LINE_Z) * 1.6; if (ch < need) { ch = need; cw = ch * aspect; }
      if (cw > W) { cw = W; ch = cw / aspect; }
      if (ch > H) { ch = H; cw = ch * aspect; }
      const feet = yOf(LINE_Z), top = clamp(feet - ch * .85, 0, H - ch), left = clamp(200 - cw / 2, 0, W - cw);
      // at night the window moves to the bed, the same size, so Yadimo asleep is in the middle of the picture
      const view = {day: {left, top}, play: {left, top},
        night: {left: clamp(SPOTS.bed.x - cw / 2, 0, W - cw), top: clamp(yOf(SPOTS.bed.z) - ch * .62, 0, H - ch)}};
      const pxW = Math.round(o.w * o.density), pxH = Math.round(o.h * o.density), scale = pxH / ch;
      const s = sOf(LINE_Z), ys = BASE * s;
      const quiet = () => { Object.assign(yad, {x: 200, z: LINE_Z, lie: 0, h: 0, vh: 0, mode: 'idle', path: null, then: null, timer: 1e9, face: 1, held: false});
        drops.length = 0; bubble.t = 0; refreshThings = true; engine.nextAct = 1e9; };
      const show = (all) => { L.room.style.display = L.dark.style.display = ''; L.ui.style.display = 'none'; for (const g of L.things.children) g.style.display = all || g !== yadHolder ? '' : 'none'; };

      // the still pictures: day with the ball in its basket, day with the ball out, and night
      quiet();
      const backgrounds = {};
      for (const [name, night, out] of [['day', false, false], ['play', false, true], ['night', true, false]]) {
        state.night = night; state.lamp = true; state.curtains = !night; ball.out = out; drawRoom(); refreshThings = true; render(); yadHolder.style.display = 'none'; L.front.innerHTML = '';
        show(true); yadHolder.style.display = 'none';
        if (cache.ball) cache.ball.style.display = 'none';
        const img = await svgImage(SER.serializeToString(svg).replace(/^<svg[^>]*>/, `<svg xmlns="${NS}" viewBox="${view[name].left} ${view[name].top} ${cw} ${ch}" width="${pxW}" height="${pxH}" preserveAspectRatio="xMidYMid slice">`));
        const c = document.createElement('canvas'); c.width = pxW; c.height = pxH; c.getContext('2d').drawImage(img, 0, 0, pxW, pxH);
        host.file('bg_' + name, c.toDataURL('image/png')); backgrounds[name] = 1;
      }
      yadHolder.style.display = ''; ball.out = false; state.night = false; drawRoom();

      // the sprites: a box around Yadimo (wider for the bed), as big as it is in the widget, shrunk to fit memory
      const box = {w: Math.min(cw, ys * 1.3), h: Math.min(ch, ys * 1.3)}, bedBox = {w: Math.min(cw, BASE * sOf(SPOTS.bed.z) * 1.75), h: Math.min(ch, BASE * sOf(SPOTS.bed.z) * 1.15)};
      const most = 44;   // the most sprites one episode uses
      const k2 = Math.min(1, Math.sqrt((o.budget || 9e6) / (most * box.w * box.h * scale * scale * 4)));
      const sprites = [], spriteOf = {};
      for (const [name, set] of Object.entries(SETS)) {
        quiet(); refreshThings = true;
        const bx = set.bed ? bedBox : box;
        if (set.bed) { Object.assign(yad, {x: SPOTS.bed.x + 4, z: SPOTS.bed.z, lie: 1, mode: 'sleep'}); }
        engine.setMood(set.mood); engine.mt = 0;
        engine.o.follow = !!set.face; engine.pointer.active = !!set.face; engine.pointer.x = (set.face || 0) * .85; engine.pointer.y = .1;
        const dt = set.secs / set.n, sub = 6;
        // run one loop first so springs and particles settle into it
        for (let pass = 0; pass < 2; pass++) for (let i = 0; i < set.n; i++) {
          for (let k = 0; k < sub; k++) {
            const ph = (i + k / sub) / set.n;
            engine.posY.t = set.walk ? -Math.abs(Math.sin(ph * Math.PI * 2)) * 36 : set.chew ? -Math.abs(Math.sin(ph * Math.PI * 4)) * 12 : 0;
            engine.update(dt / sub);
          }
          if (!pass) continue;
          render(); L.front.innerHTML = ''; if (set.prop) PROPS[set.prop](L.front, i / set.n, sOf(yad.z));
          for (const g of L.things.children) g.style.display = g === yadHolder || (set.bed && g === cache.blanket) ? '' : 'none';
          L.room.style.display = L.dark.style.display = L.ui.style.display = 'none';
          const fx = yad.x - bx.w / 2, fy = yOf(yad.z) + ys * .08 - bx.h;
          const pw = Math.round(bx.w * scale), ph2 = Math.round(bx.h * scale), ow = Math.max(1, Math.round(pw * k2)), oh = Math.max(1, Math.round(ph2 * k2));
          const img = await svgImage(SER.serializeToString(svg).replace(/^<svg[^>]*>/, `<svg xmlns="${NS}" viewBox="${fx} ${fy} ${bx.w} ${bx.h}" width="${pw}" height="${ph2}">`));
          const c = document.createElement('canvas'); c.width = ow; c.height = oh; const g = c.getContext('2d'); g.imageSmoothingQuality = 'high'; if (set.bed) g.filter = 'brightness(.78) saturate(.9)'; g.drawImage(img, 0, 0, ow, oh);
          spriteOf[name] = spriteOf[name] || []; spriteOf[name].push(sprites.length);
          host.file('s' + sprites.length, c.toDataURL('image/png'));
          // where the sprite sits relative to the spot Yadimo stands on, in widget pixels
          sprites.push({w: pw, h: ph2, dx: Math.round((fx - yad.x) * scale), dy: Math.round((fy - yOf(yad.z)) * scale)});
        }
      }
      // the ball, alone and small
      { const r = 17 * s, pw = Math.round(r * 2 * scale);
        for (let i = 0; i < 4; i++) {
          const m = `<svg xmlns="${NS}" viewBox="${-r} ${-r} ${2 * r} ${2 * r}" width="${pw}" height="${pw}"><defs>${svg.querySelector('#g-ball').outerHTML}</defs><g transform="scale(${s}) rotate(${i * 90})"><circle r="15" fill="url(#g-ball)"/><path d="M-15 0 Q0 8 15 0" stroke="#fff" stroke-width="4" fill="none"/></g></svg>`;
          const img = await svgImage(m), c = document.createElement('canvas'); c.width = c.height = pw; c.getContext('2d').drawImage(img, 0, 0, pw, pw);
          host.file('ball' + i, c.toDataURL('image/png')); }
        sprites.ball = pw; }
      L.room.style.display = L.dark.style.display = L.ui.style.display = ''; for (const g of L.things.children) g.style.display = '';

      // ---------------- the episodes ----------------
      // Yadimo's whole picture always stays inside the widget
      const lo = left + box.w / 2, hi = left + cw - box.w / 2, fit = x => clamp(x, lo, Math.max(lo, hi));
      const seen = x => x > left + 4 && x < left + cw - 4;
      const say = k => (SAY[k] || [k, k])[fa ? 0 : 1];
      function episode(bg) {
        const ep = {bg, frames: []}; let x = 200, t = 0, z = LINE_Z;
        const X = v => Math.round((v - view[bg].left) * scale), Y = v => Math.round((v - view[bg].top) * scale);
        const put = (name, i, words, extra) => { const set = SETS[name], list = spriteOf[name];
          const j = set.pingpong ? [...list, ...list.slice(1, -1).reverse()][i % (list.length * 2 - 2)] : list[i % list.length];
          const sp = sprites[j], fx = set.bed ? SPOTS.bed.x + 4 : x, fz = set.bed ? SPOTS.bed.z : z;
          const fr = {s: j, x: clamp(X(fx) + sp.dx, 0, Math.max(0, pxW - sp.w)), y: clamp(Y(yOf(fz)) + sp.dy, 0, Math.max(0, pxH - sp.h))};
          if (words) { fr.say = words; fr.hx = X(fx); fr.hy = Y(yOf(fz) - (set.bed ? ys * .5 : ys * .98)); }
          Object.assign(fr, extra); ep.frames.push(fr); t++; };
        ep.hold = (name, secs, words, extra) => { const n = Math.round(secs * fps), per = Math.max(1, Math.round(fps * SETS[name].secs / SETS[name].n));
          for (let i = 0; i < n; i++) put(name, Math.floor(i / per), words ? say(words) : null, typeof extra === 'function' ? extra(i, n) : extra); return ep; };
        ep.walk = (to, extra) => { to = fit(to); const dir = to < x ? 'walkL' : 'walkR', step = 70 / fps, per = Math.max(1, Math.round(fps * SETS[dir].secs / SETS[dir].n));
          for (let i = 0; Math.abs(to - x) > step / 2; i++) { x += Math.sign(to - x) * Math.min(step, Math.abs(to - x)); put(dir, Math.floor(i / per), null, typeof extra === 'function' ? extra(x) : extra); } return ep; };
        ep.X = X; ep.Y = Y; ep.at = () => x; ep.go = v => { x = fit(v); return ep; };
        return ep;
      }
      const eps = {};
      const waterAt = SPOTS.plant.x + 58, eatAt = SPOTS.bowl.x + 48;
      { const e = episode('day').hold('idle', 2).hold('talk', 2.4, seen(SPOTS.plant.x) ? 'water' : 'sky');
        if (seen(SPOTS.plant.x)) e.walk(waterAt).hold('water', 5).hold('think', 2.4, 'flower');
        e.walk(200).hold('think', 3.5, 'sky').hold('idle', 3); eps.garden = e.frames; }
      { const e = episode('day').hold('idle', 3).hold('talk', 3.4, 'bored').walk(255).hold('read', 1.6, 'read').hold('read', 6)
          .hold('idle', 1.5).hold('dance', 3, 'song').walk(200).hold('idle', 2); eps.bored = e.frames; }
      { // play: the ball rolls about and Yadimo goes after it and kicks it
        const e = episode('play'); let bx = fit(SPOTS.basket.x - 40), bh = 0, spin = 0;
        const ballAt = () => ({ball: spin % 4, bx: clamp(e.X(bx) - (sprites.ball >> 1), 0, pxW - sprites.ball), by: clamp(e.Y(yOf(LINE_Z) + 4 - bh) - sprites.ball, 0, pxH - sprites.ball)});
        e.hold('talk', 2.2, 'ball', ballAt);
        for (let kick = 0; kick < 4; kick++) {
          const from = bx < 200 ? 1 : -1; e.walk(bx - from * ys * .32, ballAt);
          // kick: the ball flies to the other side and bounces
          const to = fit(200 + from * rnd(60, 120)), n = Math.round(fps * 1.4);
          e.hold('dance', 1.4, kick === 3 ? 'goal' : null, (i) => { const p = i / (n - 1); bx = e.at() + from * ys * .32 + (to - e.at() - from * ys * .32) * Math.min(1, p * 1.3);
            bh = Math.abs(Math.sin(p * Math.PI * 2)) * 60 * (1 - p); spin++; return ballAt(); });
          bh = 0;
        }
        e.walk(200, ballAt).hold('idle', 2, null, ballAt); eps.play = e.frames; }
      { const e = episode('day').hold('idle', 1.5).hold('talk', 2.4, 'hungry');
        if (seen(SPOTS.bowl.x)) e.walk(eatAt).hold('eat', 5, 'yum'); else e.hold('dance', 2, 'yum');
        e.walk(200).hold('idle', 2.5); eps.hungry = e.frames; }
      { const e = episode('night').hold('sleep', 6).hold('sleep', 3, 'night').hold('sleep', 6); eps.night = e.frames; }

      host.finished(JSON.stringify({fps, w: pxW, h: pxH, sprites, ball: sprites.ball, episodes: Object.fromEntries(Object.entries(eps).map(([k, v]) =>
        [k, {bg: k === 'play' ? 'play' : k === 'night' ? 'night' : 'day', frames: v}]))}));
    } catch (e) { host.error(String(e && e.stack || e)); }
  },
};

drawRoom();
if (MODE !== 'widget') { if (MODE === 'demo') say('سلام! این خونهٔ منه 🏠', 3); requestAnimationFrame(frame); }
})();
