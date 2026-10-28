/* The room screen in the app: the same room as the widget, live at full speed. Driven from the app through
   window.room: act('feed' | 'play' | 'charge' | 'pet' | 'sleep'), night(bool), look(json). */
(() => {
  const bg = document.getElementById('bg'), fx = document.getElementById('fx'), svg = document.getElementById('yad'), bubble = document.getElementById('bubble');
  const host = window.RoomHost || {acted() {}};
  let scene, kind = 'idle', start = 0, t = 0, look = {}, sayT = 0;
  const LINES = {feed: 'ممم! خوشمزه بود 😋', play: 'بزن بریم! ⚽', charge: 'شارژ شدم! ⚡', pet: 'آخ جون! 🥰', bed: 'شب بخیر… 😴', wake: 'صبح بخیر! ☀️'};
  function size() {
    const W = innerWidth, H = innerHeight, d = Math.min(3, devicePixelRatio || 1);
    const rows = H > W * 1.25 ? 6 : H > W * .8 ? 4 : 2;
    scene = new Scene(svg, 4, rows, W, H, look);
    for (const c of [bg, fx]) { c.width = W * d; c.height = H * d; c.style.width = W + 'px'; c.style.height = H + 'px'; c.getContext('2d').setTransform(d, 0, 0, d, 0, 0); }
    svg.style.width = svg.style.height = scene.size + 'px';
    bg.getContext('2d').clearRect(0, 0, W, H); scene.drawRoom(bg.getContext('2d'));
    play(NIGHT ? 'sleep' : 'idle');
  }
  function play(k) { kind = k; start = t; script = scene.script(k); }
  let script;
  function say(text) { bubble.textContent = text; bubble.classList.add('on'); sayT = 2.6; }
  window.room = {
    act(k) {
      if (k === 'sleep') { if (kind === 'sleep' || kind === 'bed') { play('wake'); say(LINES.wake); } else { play('bed'); say(LINES.bed); } return; }
      if (kind === 'sleep') { play('wake'); return; }
      play(k); if (LINES[k]) say(LINES[k]);
    },
    night(b) { NIGHT = !!b; bg.getContext('2d').clearRect(0, 0, scene.W, scene.H); scene.drawRoom(bg.getContext('2d')); play(NIGHT ? 'bed' : 'wake'); },
    look(json) { look = typeof json === 'string' ? JSON.parse(json) : json; scene.y.set(look); },
    say,
  };
  fx.addEventListener('click', () => { window.room.act('pet'); host.acted('pet'); });
  let last = performance.now(), idleFor = 0;
  function frame(now) {
    const dt = Math.max(0, Math.min(.05, (now - last) / 1000)); last = now; t += dt;
    let u = t - start;
    if (u >= script.T) {   // what comes after a scene
      if (kind === 'bed') play('sleep'); else if (kind === 'sleep' || kind === 'idle') start = t; else play('idle');
      u = t - start;
    }
    if (kind === 'idle' && (idleFor += dt) > 8) { idleFor = 0; if (Math.random() < .7) play('stroll'); }
    if (kind !== 'idle') idleFor = 0;
    const st = script.at(Math.min(u, script.T));
    scene.drive(st, t, dt);
    const p = scene.place(st);
    svg.style.transform = `translate(${(p.x - scene.size / 2).toFixed(1)}px, ${(p.y - scene.size / 2).toFixed(1)}px) rotate(${p.angle.toFixed(1)}deg)`;
    const g = fx.getContext('2d'); g.clearRect(0, 0, scene.W, scene.H); scene.drawProps(g, st, t, p);
    if (sayT > 0 && (sayT -= dt) <= 0) bubble.classList.remove('on');
    requestAnimationFrame(frame);
  }
  addEventListener('resize', size);
  size(); requestAnimationFrame(frame);
})();
