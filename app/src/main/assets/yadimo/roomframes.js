/* Turns room scenes into pictures for the home-screen widget. Called from the app:
   roomFrames.make({w, h, cols, rows, density, look, night, fps, seqs: ['idle', 'feed', …], budget})
   and answers through window.RoomHost: background(png), frame(seq, i, png), done(seq, meta), finished(). */
(() => {
  const NSV = 'http://www.w3.org/2000/svg', ser = new XMLSerializer();
  const quiet = {background() {}, frame() {}, done() {}, finished() {}, error(e) { console.error(e); }};
  const host = new Proxy({}, {get: (_, k) => (...a) => (window.RoomHost || quiet)[k](...a)});
  function snapshot(svg) {
    const m = ser.serializeToString(svg).replace(/^<svg[^>]*?style="[^"]*"/, s => s.replace(/style="[^"]*"/, 'width="600" height="600"'));
    return new Promise((ok, bad) => { const img = new Image(); img.onload = () => ok(img); img.onerror = bad;
      img.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(m); });
  }
  async function sequence(scene, svg, kind, o) {
    const sc = scene.script(kind), loop = kind === 'idle' || kind === 'sleep';
    const fps = loop ? o.fps : Math.min(o.fps, 15);
    const n = Math.max(1, Math.round(sc.T * fps));
    const W = Math.round(scene.W * o.density), H = Math.round(scene.H * o.density);
    // warm up: settle the character where the scene starts
    let simT = -1, st = sc.at(0);
    while (simT < 0) { scene.drive(st, simT, 1 / 60); simT += 1 / 60; }
    const frames = [];
    for (let i = 0; i < n; i++) {
      const t = i * sc.T / n;
      while (simT < t) { st = sc.at(Math.max(0, simT)); scene.drive(st, simT, 1 / 60); simT += 1 / 60; }
      st = sc.at(t);
      const img = await snapshot(svg), c = document.createElement('canvas'); c.width = W; c.height = H;
      const g = c.getContext('2d'); g.setTransform(o.density, 0, 0, o.density, 0, 0);
      const p = scene.place(st);
      scene.drawProps(g, st, t, p);
      g.save(); g.translate(p.x, p.y); g.rotate(p.angle * Math.PI / 180); g.drawImage(img, -scene.size / 2, -scene.size / 2, scene.size, scene.size); g.restore();
      frames.push(c);
    }
    // crop to what is drawn in any frame, then shrink if the pictures would not fit the widget's memory
    let x0 = W, y0 = H, x1 = 0, y1 = 0;
    for (const c of frames) { const d = c.getContext('2d').getImageData(0, 0, W, H).data;
      for (let y = 0; y < H; y += 2) for (let x = 0; x < W; x += 2) if (d[(y * W + x) * 4 + 3] > 8) { if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y; } }
    if (x1 < x0) { x0 = 0; y0 = 0; x1 = 1; y1 = 1; }
    x0 = Math.max(0, x0 - 4); y0 = Math.max(0, y0 - 4); x1 = Math.min(W, x1 + 5); y1 = Math.min(H, y1 + 5);
    const bw = x1 - x0, bh = y1 - y0, s = Math.min(1, Math.sqrt(o.budget / (n * bw * bh * 4)));
    const ow = Math.max(1, Math.round(bw * s)), oh = Math.max(1, Math.round(bh * s));
    for (let i = 0; i < n; i++) {
      const c = document.createElement('canvas'); c.width = ow; c.height = oh;
      const g = c.getContext('2d'); g.imageSmoothingQuality = 'high'; g.drawImage(frames[i], x0, y0, bw, bh, 0, 0, ow, oh);
      host.frame(kind, i, c.toDataURL('image/png'));
    }
    host.done(kind, JSON.stringify({count: n, fps, loop, x: x0, y: y0, w: bw, h: bh, scale: ow / bw, seconds: sc.T}));
  }
  window.roomFrames = {
    async make(json) {
      try {
        const o = typeof json === 'string' ? JSON.parse(json) : json;
        NIGHT = !!o.night;
        const svg = document.createElementNS(NSV, 'svg'); svg.setAttribute('viewBox', '0 0 600 600'); svg.setAttribute('xmlns', NSV);
        svg.style.cssText = 'position:fixed;left:0;top:0;width:600px;height:600px;opacity:0'; document.body.append(svg);
        const scene = new Scene(svg, o.cols, o.rows, o.w, o.h, o.look);
        const c = document.createElement('canvas'); c.width = Math.round(o.w * o.density); c.height = Math.round(o.h * o.density);
        const g = c.getContext('2d'); g.setTransform(o.density, 0, 0, o.density, 0, 0); scene.drawRoom(g);
        host.background(c.toDataURL('image/png'));
        for (const k of o.seqs) await sequence(scene, svg, k, o);
        svg.remove();
        host.finished();
      } catch (e) { host.error(String(e && e.stack || e)); }
    },
  };
})();
