/* Yadimo's isometric room for the home-screen widget idea. Pure SVG strings, so the same drawing can later be
   rendered into widget images. A room is an N×N tile floor with two back walls; furniture is placed on tiles. */
const Room = (() => {
  const TW = 64, TH = 32, WALL = 120;                 // tile width/height on screen, wall height
  // grid (gx, gy, z) -> screen; the far corner of the floor is at (0, 0)
  const P = (gx, gy, z = 0) => [(gx - gy) * TW / 2, (gx + gy) * TH / 2 - z];
  const pts = a => a.map(p => p[0].toFixed(1) + ',' + p[1].toFixed(1)).join(' ');
  const poly = (a, fill, extra = '') => `<polygon points="${pts(a)}" fill="${fill}" ${extra}/>`;
  // a box standing on the floor: x, y in tiles, w along x, d along y, h in pixels
  function box(x, y, w, d, h, top, left, right, z = 0) {
    const t = [P(x, y, z + h), P(x + w, y, z + h), P(x + w, y + d, z + h), P(x, y + d, z + h)];
    const l = [P(x, y + d, z + h), P(x + w, y + d, z + h), P(x + w, y + d, z), P(x, y + d, z)];
    const r = [P(x + w, y, z + h), P(x + w, y + d, z + h), P(x + w, y + d, z), P(x + w, y, z)];
    return poly(l, left) + poly(r, right) + poly(t, top);
  }
  const shade = (hex, k) => { const n = parseInt(hex.slice(1), 16), f = c => Math.max(0, Math.min(255, Math.round(c * k)));
    return '#' + [n >> 16, (n >> 8) & 255, n & 255].map(f).map(c => c.toString(16).padStart(2, '0')).join(''); };
  const cube = (x, y, w, d, h, c, z = 0) => box(x, y, w, d, h, shade(c, 1.12), shade(c, .82), shade(c, .66), z);

  // ---- furniture (each returns SVG; s = sort key so nearer things draw later) ----
  const F = {
    rug: (x, y) => ({s: -1, svg: poly([P(x, y), P(x + 3, y), P(x + 3, y + 2), P(x, y + 2)], '#f6b8c9', 'opacity=".9"') +
      poly([P(x + .3, y + .3), P(x + 2.7, y + .3), P(x + 2.7, y + 1.7), P(x + .3, y + 1.7)], 'none', 'stroke="#fff" stroke-width="3" stroke-dasharray="8 6" opacity=".7"')}),
    bed: (x, y) => ({s: x + y, svg: cube(x, y, 2, 1.2, 22, '#8a6a4f') + cube(x + .1, y + .1, 1.8, 1, 14, '#f4f1ff', 22) +
      cube(x + .15, y + .15, .5, .9, 10, '#ffd96a', 36) + cube(x + .75, y + .1, 1.15, 1, 6, '#7d8cf0', 36)}),
    lamp: (x, y) => ({s: x + y, svg: cube(x + .35, y + .35, .3, .3, 8, '#5b4b7a') + `<rect x="${P(x + .5, y + .5)[0] - 2}" y="${P(x + .5, y + .5, 70)[1]}" width="4" height="62" fill="#5b4b7a"/>` +
      `<path d="M${P(x + .5, y + .5, 92)[0] - 20} ${P(x + .5, y + .5, 70)[1]} L${P(x + .5, y + .5, 92)[0] - 11} ${P(x + .5, y + .5, 96)[1]} L${P(x + .5, y + .5, 92)[0] + 11} ${P(x + .5, y + .5, 96)[1]} L${P(x + .5, y + .5, 92)[0] + 20} ${P(x + .5, y + .5, 70)[1]}Z" fill="#ffcf5c"/>` +
      `<ellipse cx="${P(x + .5, y + .5)[0]}" cy="${P(x + .5, y + .5, 66)[1]}" rx="34" ry="20" fill="#fff3b0" opacity=".25"/>`}),
    plant: (x, y) => { const c = P(x + .5, y + .5, 40); return {s: x + y, svg: cube(x + .25, y + .25, .5, .5, 26, '#d9774f') +
      `<circle cx="${c[0]}" cy="${c[1]}" r="20" fill="#3fbf7f"/><circle cx="${c[0] - 14}" cy="${c[1] + 6}" r="13" fill="#2fa86c"/><circle cx="${c[0] + 13}" cy="${c[1] + 4}" r="14" fill="#4fd08e"/>`}; },
    shelf: (x, y) => ({s: x + y, svg: cube(x, y, 1.6, .5, 96, '#a07850') +
      [24, 50, 76].map((z, i) => cube(x + .1, y + .05, 1.4, .4, 3, '#7b5a3a', z) +
        [0, 1, 2, 3].map(k => cube(x + .15 + k * .33, y + .1, .22, .3, 18 - (k + i) % 3 * 3, ['#ff8f5e', '#4f9ef0', '#ffd25a', '#36c08f'][(k + i) % 4], z + 3)).join('')).join('')}),
    desk: (x, y) => ({s: x + y, svg: cube(x, y, 1.6, .8, 36, '#c9a27a') + cube(x + .5, y + .15, .7, .1, 30, '#2b2f4a', 36) +
      cube(x + .52, y + .17, .66, .06, 26, '#7fd3ff', 38)}),
    sofa: (x, y) => ({s: x + y, svg: cube(x, y, 2, .9, 20, '#ef7aa8') + cube(x, y, 2, .3, 44, '#e2689a') +
      cube(x, y, .3, .9, 32, '#e2689a') + cube(x + 1.7, y, .3, .9, 32, '#e2689a')}),
    ball: (x, y) => { const c = P(x + .5, y + .5, 16); return {s: x + y + 1, svg: `<ellipse cx="${c[0]}" cy="${P(x + .5, y + .5)[1]}" rx="15" ry="6" fill="#000" opacity=".15"/>` +
      `<circle cx="${c[0]}" cy="${c[1]}" r="16" fill="#ff6b6b"/><path d="M${c[0] - 16} ${c[1]} Q${c[0]} ${c[1] + 9} ${c[0] + 16} ${c[1]}" stroke="#fff" stroke-width="4" fill="none"/>`}; },
    bowl: (x, y) => { const c = P(x + .5, y + .5); return {s: x + y + 1, svg: `<ellipse cx="${c[0]}" cy="${c[1] - 6}" rx="20" ry="9" fill="#7a5cf0"/><ellipse cx="${c[0]}" cy="${c[1] - 10}" rx="15" ry="6" fill="#c8a06a"/>` +
      `<circle cx="${c[0] - 5}" cy="${c[1] - 12}" r="3" fill="#8a5a2a"/><circle cx="${c[0] + 5}" cy="${c[1] - 11}" r="3" fill="#8a5a2a"/>`}; },
    tv: (x, y) => ({s: x + y, svg: cube(x, y, 1.6, .5, 30, '#6b5a8a') + cube(x + .2, y + .15, 1.2, .1, 46, '#1e1b2e', 30) + cube(x + .23, y + .17, 1.14, .06, 40, '#3b3466', 33)}),
    toybox: (x, y) => ({s: x + y, svg: cube(x, y, .8, .7, 26, '#ffd25a') + cube(x, y, .8, .7, 4, '#ff8f5e', 26)}),
  };

  // things hung on the walls: on the left wall (along x, y = 0) or the right wall (along y, x = 0)
  function windowL(x, w, sky) {
    const a = [P(x, 0, 92), P(x + w, 0, 92), P(x + w, 0, 36), P(x, 0, 36)];
    const m = [P(x + w / 2, 0, 92), P(x + w / 2, 0, 36)];
    return poly(a, '#fff') + poly([P(x + .08, 0, 88), P(x + w - .08, 0, 88), P(x + w - .08, 0, 40), P(x + .08, 0, 40)], sky) +
      `<line x1="${m[0][0]}" y1="${m[0][1]}" x2="${m[1][0]}" y2="${m[1][1]}" stroke="#fff" stroke-width="4"/>`;
  }
  function frameR(y, w) {
    return poly([P(0, y, 96), P(0, y + w, 96), P(0, y + w, 50), P(0, y, 50)], '#ffd25a') +
      poly([P(0, y + .1, 90), P(0, y + w - .1, 90), P(0, y + w - .1, 56), P(0, y + .1, 56)], '#7a5cf0') +
      `<circle cx="${P(0, y + w / 2, 75)[0]}" cy="${P(0, y + w / 2, 75)[1]}" r="7" fill="#ffd25a"/>`;
  }
  function clockR(y) { const c = P(0, y, 100); return `<ellipse cx="${c[0]}" cy="${c[1]}" rx="13" ry="16" fill="#fff" stroke="#5b4b7a" stroke-width="3"/>` +
    `<path d="M${c[0]} ${c[1]} l0 -9 M${c[0]} ${c[1]} l6 3" stroke="#5b4b7a" stroke-width="2.5"/>`; }

  /** Builds a room of n×n tiles with the given furniture. Returns {svg, viewBox, toScreen(gx, gy)}. */
  function build(n, items, opt = {}) {
    const night = !!opt.night;
    const wallL = night ? '#3b3470' : '#efe7ff', wallR = night ? '#2f2960' : '#e2d6ff', floorA = night ? '#5a4a7a' : '#e9c9a0', floorB = night ? '#51426f' : '#dfbb8f';
    const sky = night ? '#22305e' : '#9fd8ff';
    let s = '';
    // walls
    s += poly([P(0, 0, WALL), P(n, 0, WALL), P(n, 0), P(0, 0)], wallL);
    s += poly([P(0, 0, WALL), P(0, n, WALL), P(0, n), P(0, 0)], wallR);
    s += poly([P(0, 0, WALL), P(n, 0, WALL), P(n, 0, WALL - 8), P(0, 0, WALL - 8)], shade(wallL, .93));
    s += poly([P(0, 0, WALL), P(0, n, WALL), P(0, n, WALL - 8), P(0, 0, WALL - 8)], shade(wallR, .93));
    // floor boards
    for (let i = 0; i < n; i++) for (let j = 0; j < n; j++) s += poly([P(i, j), P(i + 1, j), P(i + 1, j + 1), P(i, j + 1)], (i + j) % 2 ? floorA : floorB);
    // skirting
    s += poly([P(0, 0, 8), P(n, 0, 8), P(n, 0), P(0, 0)], shade(wallL, .8));
    s += poly([P(0, 0, 8), P(0, n, 8), P(0, n), P(0, 0)], shade(wallR, .8));
    for (const w of (opt.walls || [])) s += w(sky);
    const placed = items.map(([k, x, y]) => F[k](x, y)).sort((a, b) => a.s - b.s);
    s += placed.map(p => p.svg).join('');
    if (night) s += poly([P(0, 0, WALL), P(n, 0, WALL), P(n, n), P(0, n)], '#0b0820', 'opacity=".18"');
    const minX = P(0, n)[0] - 6, maxX = P(n, 0)[0] + 6, minY = P(0, 0, WALL + 14)[1], maxY = P(n, n)[1] + 6;
    return {svg: s, viewBox: [minX, minY, maxX - minX, maxY - minY], toScreen: (gx, gy) => P(gx, gy)};
  }

  /** Rooms for each widget size; bigger widgets get more furniture. */
  const SIZES = {
    s22: {n: 3, items: [], walls: []},
    s42: {n: 4, items: [['rug', .6, 1.6], ['bed', 2, .1], ['lamp', 0.1, .1]], walls: [s => windowL(.6, 1.2, s)]},
    s43: {n: 5, items: [['rug', 1, 1.8], ['bed', 2.8, .1], ['lamp', .1, .1], ['plant', .1, 3.8], ['shelf', .1, 1.4], ['bowl', 3.6, 3.6]],
      walls: [s => windowL(1, 1.3, s), () => frameR(3.1, .9)]},
    s44: {n: 6, items: [['rug', 1.4, 2.2], ['bed', 3.8, .1], ['lamp', .1, .1], ['plant', .1, 4.9], ['shelf', .1, 1.6], ['desk', 1.5, .1],
      ['bowl', 5, 5], ['ball', 3.5, 4], ['toybox', 5.1, 2.3]], walls: [s => windowL(3.9, 1.4, s), () => frameR(3.6, .9), () => clockR(1.1)]},
    full: {n: 8, items: [['rug', 2.2, 3], ['bed', 5.6, .1], ['lamp', .1, .1], ['plant', .1, 6.9], ['plant', 7, .1], ['shelf', .1, 1.6],
      ['desk', 2.6, .1], ['sofa', .1, 4.4], ['tv', 4.6, 6.1], ['bowl', 6.8, 6.6], ['ball', 4.5, 4.5], ['toybox', 6.9, 3.2]],
      walls: [s => windowL(5.4, 1.6, s), s => windowL(.9, 1.2, s), () => frameR(3.4, 1), () => clockR(6.4)]},
  };

  return {build, SIZES, P};
})();
