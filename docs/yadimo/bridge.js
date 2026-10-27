/* App side of Yadimo: one character filling the WebView, driven from Kotlin through window.yadimo. */
const svgEl = document.getElementById('y');
const Y = new Yadimo(svgEl, {follow: false});
let lastConfig = '', pokeTimer = 0;
window.yadimo = {
  config(json) { if (json === lastConfig) return; lastConfig = json; try { Y.set(JSON.parse(json)); } catch (e) {} },
  mood(m) { if (MOODS[m] && m !== Y.mood) Y.setMood(m); },
  replay(m) { if (MOODS[m]) Y.setMood(m); },
  poke() { const prev = Y.mood === 'celebrate' ? 'idle' : Y.mood; Y.setMood('celebrate'); Y.burst('conf', 10);
    clearTimeout(pokeTimer); pokeTimer = setTimeout(() => { if (Y.mood === 'celebrate') Y.setMood(prev); }, 1500); },
  // peeking in from the right edge of its box, or sliding back out of it
  peek(inside, instant) { Y.posX.t = inside ? 90 : 520; if (instant) { Y.posX.x = Y.posX.t; Y.posX.v = 0; } },
  catalog() {
    const pick = o => Object.entries(o).map(([id, v]) => ({id, n: typeof v === 'string' ? v : v.n}));
    return JSON.stringify({
      variants: pick(VARIANTS), faces: pick(FACES), hats: pick(HATS), glasses: pick(GLASSES), neck: pick(NECK), items: pick(ITEMS),
      colors: Object.entries(COLORS).map(([id, v]) => ({id, n: v.n, c: v.body})),
      costumes: pick(TEST.COSTUMES).filter(x => x.id !== 'none'), testItems: pick(TEST.TITEMS)});
  },
};
let last = performance.now();
function frame(now) {
  const dt = Math.min(.033, (now - last) / 1000); last = now;
  if (!document.hidden) Y.update(dt);
  requestAnimationFrame(frame);
}
requestAnimationFrame(frame);
