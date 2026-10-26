// Turns the mascot parts in docs/mascot-lab.html into small SVG layer files for the app.
// Usage: node tools/export-mascot.js   (writes app/src/main/assets/mascot and app/src/testparts/assets/mascot_test)
const fs = require('fs'), vm = require('vm'), path = require('path');
const root = path.join(__dirname, '..');
const html = fs.readFileSync(path.join(root, 'docs/mascot-lab.html'), 'utf8');
const script = html.match(/<script>([\s\S]*?)<\/script>/)[1];
const code = script.slice(0, script.indexOf('/* ============================ حرکت'));
const A = vm.runInNewContext(code + `;({PAL,FACES,HEADS,GLASSES,ITEMS,OUTFITS,THEADS,TGLASSES,TITEMS,TOUTFITS,LAYER,defsSvg,VIEWBOX,BULB})`, {});

const mainDir = path.join(root, 'app/src/main/assets/mascot');
const testDir = path.join(root, 'app/src/testparts/assets/mascot_test');
for (const d of [mainDir, testDir]) { fs.rmSync(d, { recursive: true, force: true }); fs.mkdirSync(d, { recursive: true }); }

const vb = A.VIEWBOX.split(' ').map(Number);
const svg = (inner, p, box = A.VIEWBOX) => {
  const b = box.split(' ').map(Number);
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${box}" width="${b[2]}" height="${b[3]}">${A.defsSvg(p, '')}${inner}</svg>\n`;
};
const lav = A.PAL.lav;
const write = (dir, name, text) => fs.writeFileSync(path.join(dir, name), text);
const ITEM_BOX = '-70 -110 140 140';

// palette-dependent layers
for (const [id, p] of Object.entries(A.PAL)) {
  write(mainDir, `body_${id}.svg`, svg(A.LAYER.body(p, '') + A.LAYER.belly(p, ''), p));
  write(mainDir, `handle_${id}.svg`, svg(A.LAYER.handle(p, ''), p));
  write(mainDir, `legs_${id}.svg`, svg(A.LAYER.legs(p, ''), p));
  write(mainDir, `armL_${id}.svg`, svg(A.LAYER.arm(p, '', 76, 'armL'), p));
  write(mainDir, `armR_${id}.svg`, svg(A.LAYER.arm(p, '', 224, 'armR'), p));
}
// effects
write(mainDir, 'fx_bulb.svg', svg(A.BULB(''), lav, '-50 -50 100 100'));
const wave = d => `<path fill="none" stroke="#6a4fd8" stroke-width="5" stroke-linecap="round" d="${d}"/>`;
write(mainDir, 'fx_waves.svg', svg(['M30 120 Q8 160 30 200', 'M10 106 Q-22 160 10 214', 'M270 120 Q292 160 270 200', 'M290 106 Q322 160 290 214'].map(wave).join(''), lav));

// faces: eyes and mouth are separate layers so the app can blink and talk
const catalog = { palettes: Object.entries(A.PAL).map(([id, p]) => ({ id, n: p.n, sw: p.sw })), faces: [], heads: [], glasses: [], items: [], outfits: [] };
const eyesRe = /<g class="eyes"[^>]*>[\s\S]*?<\/g>/g, mouthRe = /<g class="mouth">[\s\S]*?<\/g>/g;
for (const [id, f] of Object.entries(A.FACES)) {
  const s = f.svg();
  const eyes = s.match(eyesRe) || [], mouth = s.match(mouthRe) || [];
  const rest = s.replace(eyesRe, '').replace(mouthRe, '');
  const cy = eyes.length ? +eyes[0].match(/data-cy="([\d.]+)"/)[1] : 158;
  write(mainDir, `face_${id}_rest.svg`, svg(rest, lav));
  if (eyes.length) write(mainDir, `face_${id}_eyes.svg`, svg(eyes.join(''), lav));
  if (mouth.length) write(mainDir, `face_${id}_mouth.svg`, svg(mouth.join(''), lav));
  catalog.faces.push({ id, n: f.n, cy, eyes: eyes.length > 0, mouth: mouth.length > 0 });
}

const parts = (dir, cat, heads, glasses, items, outfits, test) => {
  const add = (list, id, n) => list.push(test ? { id, n, test: true } : { id, n });
  for (const [id, h] of Object.entries(heads)) { if (id !== 'none') write(dir, `head_${id}.svg`, svg(h.svg(''), lav)); add(cat.heads, id, h.n); }
  for (const [id, g] of Object.entries(glasses)) { if (id !== 'none') write(dir, `glasses_${id}.svg`, svg(g.svg(''), lav)); add(cat.glasses, id, g.n); }
  for (const [id, it] of Object.entries(items)) { if (id !== 'none') write(dir, `item_${id}.svg`, svg(it.svg(''), lav, ITEM_BOX)); add(cat.items, id, it.n); }
  for (const [id, o] of Object.entries(outfits)) {
    const back = o.back(''), front = o.front('');
    if (back) write(dir, `out_${id}_back.svg`, svg(back, lav));
    if (front) write(dir, `out_${id}_front.svg`, svg(front, lav));
    cat.outfits.push(Object.assign({ id, n: o.n, back: !!back, front: !!front }, test ? { test: true } : {}));
  }
};
parts(mainDir, catalog, A.HEADS, A.GLASSES, A.ITEMS, A.OUTFITS, false);
const tcat = { heads: [], glasses: [], items: [], outfits: [] };
parts(testDir, tcat, A.THEADS, A.TGLASSES, A.TITEMS, A.TOUTFITS, true);
write(mainDir, 'catalog.json', JSON.stringify(catalog, null, 1));
write(testDir, 'catalog_test.json', JSON.stringify(tcat, null, 1));
const count = d => fs.readdirSync(d).length;
console.log('main assets:', count(mainDir), 'test assets:', count(testDir));
