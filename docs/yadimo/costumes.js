/* «تست»: costumes and items inspired by well-known characters. For private testing only; they are not shipped in the
   store build and need legal clearance before any public release.
   A costume can fill several layers: back (cape behind the body), mask (painted on the head, clipped to its shape),
   chest (emblem or collar), head (in the hat layer) and face (on the face plane, above the mask). */
const T_DEFS = `
  <linearGradient id="acc-black" x1="0" y1="0" x2=".8" y2="1"><stop stop-color="#55506e"/><stop offset=".5" stop-color="#26223c"/><stop offset="1" stop-color="#0d0b18"/></linearGradient>
  <linearGradient id="acc-silver" x1="0" y1="0" x2=".8" y2="1"><stop stop-color="#ffffff"/><stop offset=".45" stop-color="#cfd5e6"/><stop offset="1" stop-color="#7b84a3"/></linearGradient>
  <linearGradient id="acc-straw" x1="0" y1="0" x2=".8" y2="1"><stop stop-color="#fff0b8"/><stop offset=".5" stop-color="#f0cc6a"/><stop offset="1" stop-color="#b98a2e"/></linearGradient>
  <linearGradient id="acc-ice" x1="0" y1="0" x2=".8" y2="1"><stop stop-color="#ffffff"/><stop offset=".5" stop-color="#e3f4ff"/><stop offset="1" stop-color="#9fcbe8"/></linearGradient>
  <linearGradient id="acc-gray" x1="0" y1="0" x2=".8" y2="1"><stop stop-color="#d9dcea"/><stop offset=".5" stop-color="#8d93ad"/><stop offset="1" stop-color="#4c516b"/></linearGradient>
  <linearGradient id="acc-joker" x1="0" y1="0" x2=".8" y2="1"><stop stop-color="#b9f5a6"/><stop offset=".5" stop-color="#4ccf5e"/><stop offset="1" stop-color="#1c7a33"/></linearGradient>
  <linearGradient id="acc-navy" x1="0" y1="0" x2=".8" y2="1"><stop stop-color="#6f8fe0"/><stop offset=".5" stop-color="#2e4fa8"/><stop offset="1" stop-color="#16285e"/></linearGradient>
  <linearGradient id="acc-saber" x1="0" y1="0" x2="1" y2="0"><stop stop-color="#7ef0ff" stop-opacity="0"/><stop offset=".5" stop-color="#7ef0ff"/><stop offset="1" stop-color="#7ef0ff" stop-opacity="0"/></linearGradient>`;

const mirrorX = d => d.replace(/(-?\d+\.?\d*)\s+(-?\d+\.?\d*)/g, (m, x, y) => `${600 - Number(x)} ${y}`);
const braidChain = (x0, y0, x1, y1, c1, c2, tie) => {
  let o = ''; const n = 9;
  for (let i = 0; i <= n; i++) { const k = i / n, x = x0 + (x1 - x0) * k + (i % 2 ? 7 : -7), y = y0 + (y1 - y0) * k;
    o += `<ellipse cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" rx="${(22 - k * 6).toFixed(1)}" ry="${(17 - k * 4).toFixed(1)}" fill="${i % 2 ? c1 : c2}"/>`; }
  return o + `<circle cx="${x1}" cy="${y1 + 18}" r="12" fill="${tie}"/>`;
};
const cape = (robot, fill, scallop) => {
  const d = robot ? 'M240 346 Q196 420 188 474 Q300 494 412 474 Q404 420 360 346Z'
    : (scallop ? 'M168 300 Q96 420 66 520 Q100 548 132 530 Q170 560 214 538 Q256 566 300 544 Q344 566 386 538 Q430 560 468 530 Q500 548 534 520 Q504 420 432 300Z'
               : 'M168 300 Q96 420 70 522 Q300 566 530 522 Q504 420 432 300Z');
  return `<path d="${d}" fill="${fill}"/><path d="M190 340 Q140 430 120 500M410 340 Q460 430 480 500" stroke="#fff" stroke-opacity=".14" stroke-width="9" fill="none" stroke-linecap="round"/>`;
};
const chestAt = robot => robot ? [300, 404] : [300, 392];

const COSTUMES = {
  none: {n: 'بدون'},
  bat: {n: 'خفاش‌نما',
    back: r => cape(r, 'url(#acc-black)', true),
    mask: () => `<path d="M0 30 H600 V238 Q470 250 404 246 L344 258 Q300 296 256 258 L196 246 Q130 250 0 238Z" fill="url(#acc-black)"/><ellipse cx="250" cy="150" rx="70" ry="30" fill="#fff" opacity=".08"/>`,
    head: () => `<path d="M216 178 L194 64 L266 150Z" fill="url(#acc-black)"/><path d="M384 178 L406 64 L334 150Z" fill="url(#acc-black)"/>`,
    chest: r => { const [x, y] = chestAt(r); return `<ellipse cx="${x}" cy="${y}" rx="52" ry="30" fill="url(#gold)"/><path d="M${x - 44} ${y - 2} Q${x - 22} ${y - 26} ${x - 10} ${y - 10} L${x} ${y - 24} L${x + 10} ${y - 10} Q${x + 22} ${y - 26} ${x + 44} ${y - 2} Q${x + 24} ${y + 4} ${x + 16} ${y + 18} Q${x + 8} ${y + 6} ${x} ${y + 20} Q${x - 8} ${y + 6} ${x - 16} ${y + 18} Q${x - 24} ${y + 4} ${x - 44} ${y - 2}Z" fill="#14111f"/>`; }},
  plumber: {n: 'کلاه قرمز و سبیل',
    head: () => `<path d="M186 206 C178 94 422 94 414 206 Q300 224 186 206Z" fill="url(#acc-red)"/><path d="M176 202 Q300 236 424 202 L432 226 Q300 270 168 226Z" fill="#b81f34"/>
      <circle cx="300" cy="148" r="32" fill="url(#acc-white)"/><path d="M284 148 l12 12 l22 -24" fill="none" stroke="#ef4a5a" stroke-width="10" stroke-linecap="round" stroke-linejoin="round"/>${shine(232, 140, 12, 28, 30, .4)}`,
    face: () => `<path d="M300 300 Q270 278 232 300 Q258 330 300 316 Q342 330 368 300 Q330 278 300 300Z" fill="#3a2418"/><path d="M252 298 Q268 290 284 296" stroke="#fff" stroke-opacity=".25" stroke-width="5" fill="none" stroke-linecap="round"/>`},
  spider: {n: 'تارعنکبوتی',
    mask: () => { let w = '';
      for (let i = 0; i < 12; i++) { const a = i * Math.PI / 6 + .2; w += `<path d="M300 272 L${(300 + Math.cos(a) * 340).toFixed(0)} ${(272 + Math.sin(a) * 340).toFixed(0)}"/>`; }
      for (const k of [1, 2, 3]) w += `<ellipse cx="300" cy="272" rx="${k * 62}" ry="${k * 46}"/>`;
      return `<rect x="0" y="30" width="600" height="352" fill="url(#acc-red)"/><g stroke="#7a1018" stroke-width="3" fill="none" opacity=".55">${w}</g><ellipse cx="250" cy="160" rx="80" ry="34" fill="#fff" opacity=".12"/>`; },
    face: () => `<path d="M222 282 Q230 234 286 252 Q298 288 262 306 Q224 306 222 282Z" fill="url(#acc-white)" stroke="#111" stroke-width="8" stroke-linejoin="round"/>
      <path d="M378 282 Q370 234 314 252 Q302 288 338 306 Q376 306 378 282Z" fill="url(#acc-white)" stroke="#111" stroke-width="8" stroke-linejoin="round"/>`},
  iron: {n: 'آهنی',
    mask: () => `<rect x="0" y="30" width="600" height="352" fill="url(#acc-red)"/><ellipse cx="250" cy="160" rx="80" ry="34" fill="#fff" opacity=".16"/>`,
    face: () => `<path d="M196 232 Q300 200 404 232 L396 318 Q300 366 204 318Z" fill="url(#gold)"/><path d="M214 244 Q300 220 386 244 L380 300 Q300 340 220 300Z" fill="#fff" opacity=".12"/>
      <path d="M226 266 L284 276 L280 292 L228 282Z" fill="#c9f6ff" stroke="#2a1f5e" stroke-width="3"/><path d="M374 266 L316 276 L320 292 L372 282Z" fill="#c9f6ff" stroke="#2a1f5e" stroke-width="3"/>
      <path d="M262 326 Q300 338 338 326M300 214 V258" stroke="#8a5a00" stroke-width="4" fill="none" stroke-linecap="round" opacity=".6"/>`},
  straw: {n: 'کلاه حصیری',
    head: () => `${soft(300, 200, 150, 16)}<ellipse cx="300" cy="194" rx="176" ry="36" fill="url(#acc-straw)"/><path d="M210 194 C202 102 398 102 390 194Z" fill="url(#acc-straw)"/>
      <path d="M211 168 Q300 196 389 168 L390 194 Q300 224 210 194Z" fill="url(#acc-red)"/><path d="M140 196 Q300 236 460 196M240 132 Q300 150 360 132" stroke="#b98a2e" stroke-width="3" fill="none" opacity=".55"/>${shine(250, 130, 12, 26, 30, .45)}`,
    face: () => `<path d="M234 304 L246 334M228 314 H246M231 324 H249" stroke="#7a3b2e" stroke-width="4" stroke-linecap="round" fill="none"/>`},
  ninja: {n: 'سربند نینجا',
    head: () => `<path d="M166 222 Q300 196 434 222 L434 260 Q300 234 166 260Z" fill="url(#acc-navy)"/><rect x="258" y="210" width="84" height="44" rx="10" fill="url(#acc-silver)"/>
      <path d="M300 232 m-14 0 a14 14 0 0 1 28 0 a9 9 0 0 1 -18 0 a4 4 0 0 1 8 0" fill="none" stroke="#2e3a6a" stroke-width="4" stroke-linecap="round"/>
      <path d="M428 236 L478 212 L468 246 L498 272 L434 264Z" fill="url(#acc-navy)"/>`,
    face: () => `<g stroke="#4a3a6a" stroke-width="4" stroke-linecap="round" opacity=".75"><path d="M218 300 L190 292M218 312 L188 312M218 324 L190 332"/><path d="M382 300 L410 292M382 312 L412 312M382 324 L410 332"/></g>`},
  ice: {n: 'موی یخی و بافت',
    head: () => `<path d="M168 270 C148 160 228 106 300 106 C380 106 454 160 434 272 C418 216 384 196 342 214 C306 180 236 194 200 238Z" fill="url(#acc-ice)"/>
      ${braidChain(184, 262, 152, 452, '#e3f4ff', '#bfe0f5', '#6ec6ff')}<path d="M248 140 L262 110 L276 132 L300 92 L324 132 L338 110 L352 140Z" fill="url(#acc-ice)" stroke="#9fcbe8" stroke-width="3"/>${shine(232, 150, 14, 34, 35, .6)}`},
  thor: {n: 'کلاه‌خود بالدار',
    back: r => cape(r, 'url(#acc-red)', false),
    head: () => `<path d="M188 214 C180 98 420 98 412 214 Q300 232 188 214Z" fill="url(#acc-silver)"/><path d="M190 196 Q300 222 410 196" stroke="#8b94ac" stroke-width="6" fill="none"/>
      <path d="M196 168 Q120 150 82 76 Q150 108 176 124 Q118 118 102 176 Q160 160 192 196Z" fill="url(#acc-white)"/><path d="${mirrorX('M196 168 Q120 150 82 76 Q150 108 176 124 Q118 118 102 176 Q160 160 192 196Z')}" fill="url(#acc-white)"/>${shine(240, 140, 12, 30, 30, .7)}`},
  elf: {n: 'کلاه سبز بلند',
    head: () => `<path d="M188 214 C176 98 340 62 432 138 C482 180 502 250 492 322 C462 252 422 222 382 212 Q300 198 188 214Z" fill="url(#acc-green)"/>
      <path d="M190 212 Q290 196 382 210" stroke="#fff" stroke-opacity=".3" stroke-width="7" fill="none" stroke-linecap="round"/><path d="M262 120 Q340 106 410 150" stroke="#1f8a52" stroke-width="5" fill="none" opacity=".5"/>`},
  graywiz: {n: 'جادوگر پیر',
    head: () => `${soft(300, 206, 118, 16)}<path d="M210 196 C226 132 252 66 320 -4 C334 -16 356 -10 362 6 C352 16 344 24 342 40 C350 98 370 150 392 196Z" fill="url(#acc-gray)"/>
      <ellipse cx="300" cy="198" rx="150" ry="30" fill="url(#acc-gray)"/><path d="M212 180 Q300 214 388 180 L392 202 Q300 238 208 202Z" fill="#5d6380"/>${shine(246, 128, 8, 40, 25, .35)}`,
    face: () => `<path d="M196 300 Q202 388 244 436 Q300 506 356 436 Q398 388 404 300 Q382 326 346 322 Q322 312 300 318 Q278 312 254 322 Q218 326 196 300Z" fill="url(#acc-white)"/>
      <path d="M244 378 Q300 418 356 378M234 410 Q300 460 366 410" stroke="#d8cdf2" stroke-width="6" fill="none" stroke-linecap="round"/>`},
  joker: {n: 'موی سبز',
    head: () => `<path d="M174 264 C158 168 220 108 300 110 C380 106 442 168 426 264 C406 216 384 228 366 206 C346 232 328 208 300 224 C274 208 256 232 236 206 C216 228 194 216 174 264Z" fill="url(#acc-joker)"/>
      <path d="M252 118 Q236 70 272 50 Q264 84 288 100M346 112 Q366 66 330 44 Q344 80 318 98" stroke="url(#acc-joker)" stroke-width="13" fill="none" stroke-linecap="round"/>`,
    face: () => `<path d="M236 300 Q300 364 364 300 Q300 336 236 300Z" fill="url(#acc-red)"/><path d="M236 300 Q222 290 214 266M364 300 Q378 290 386 266" stroke="#ef4a5a" stroke-width="8" fill="none" stroke-linecap="round"/>`},
  wed: {n: 'موی مشکی و بافت',
    mask: r => r ? '' : `<rect x="0" y="352" width="600" height="200" fill="url(#acc-black)"/>`,
    head: () => `<path d="M172 264 C162 160 228 114 300 114 C372 114 438 160 428 264 L412 214 Q360 232 300 226 Q240 232 188 214Z" fill="url(#acc-black)"/>
      ${braidChain(176, 262, 150, 440, '#3a3556', '#26223c', '#fff')}${braidChain(424, 262, 450, 440, '#3a3556', '#26223c', '#fff')}${shine(236, 150, 14, 30, 35, .25)}`,
    chest: r => { const [x, y] = chestAt(r); return `<path d="M${x - 62} ${y - 38} L${x} ${y + 18} L${x + 62} ${y - 38} L${x + 34} ${y - 48} L${x} ${y - 14} L${x - 34} ${y - 48}Z" fill="url(#acc-white)"/>`; }},
  saiyan: {n: 'موی سیخی',
    head: () => `<path d="M182 232 L156 118 L222 172 L214 64 L270 150 L300 34 L330 150 L386 64 L378 172 L444 118 L418 232 Q300 196 182 232Z" fill="url(#acc-black)"/>
      <path d="M240 130 L254 108M300 96 V66M360 130 L346 108" stroke="#fff" stroke-opacity=".25" stroke-width="7" stroke-linecap="round"/>`},
  hero: {n: 'ابرقهرمان',
    back: r => cape(r, 'url(#acc-red)', false),
    head: () => `<path d="M186 216 C180 118 420 118 414 216 C384 182 330 178 300 192 C270 178 216 182 186 216Z" fill="url(#acc-black)"/>
      <path d="M298 188 c-26 10 -20 46 12 38 c20 -8 16 -32 -4 -30" fill="none" stroke="url(#acc-black)" stroke-width="12" stroke-linecap="round"/>`,
    chest: r => { const [x, y] = chestAt(r); return `<path d="M${x - 48} ${y - 34} H${x + 48} L${x + 36} ${y + 14} L${x} ${y + 44} L${x - 36} ${y + 14}Z" fill="url(#gold)"/>
      <path d="M${x - 36} ${y - 26} H${x + 36} L${x + 27} ${y + 10} L${x} ${y + 33} L${x - 27} ${y + 10}Z" fill="url(#acc-red)"/><polygon points="${star5(x, y - 2, 20, 9)}" fill="#fff"/>`; }},
  pirate: {n: 'ناخدا',
    head: () => `${soft(300, 206, 130, 14)}<path d="M146 212 Q172 110 300 100 Q428 110 454 212 Q382 180 300 184 Q218 180 146 212Z" fill="url(#acc-black)"/>
      <path d="M146 212 Q218 180 300 184 Q382 180 454 212 L446 228 Q380 198 300 202 Q220 198 154 228Z" fill="url(#gold)"/>
      <circle cx="300" cy="146" r="22" fill="url(#acc-white)"/><rect x="288" y="160" width="24" height="14" rx="5" fill="url(#acc-white)"/><circle cx="292" cy="144" r="5" fill="#26223c"/><circle cx="308" cy="144" r="5" fill="#26223c"/>`,
    face: () => `<path d="M206 236 L282 258M318 258 L420 224" stroke="#1b1830" stroke-width="6" fill="none" stroke-linecap="round"/><ellipse cx="252" cy="272" rx="32" ry="30" fill="url(#acc-black)"/>${shine(240, 260, 8, 5, -20, .3)}`},
};

const TITEMS = {
  hammer: {n: 'چکش', svg: (x, y) => `<rect x="${x - 7}" y="${y - 66}" width="14" height="86" rx="7" fill="#8a5a2a"/><rect x="${x - 46}" y="${y - 112}" width="92" height="54" rx="12" fill="url(#acc-silver)"/>
      <path d="M${x - 24} ${y - 104} V${y - 66}M${x + 24} ${y - 104} V${y - 66}" stroke="#7b84a3" stroke-width="5"/>${shine(x - 22, y - 100, 16, 5, 0, .7)}`},
  shield: {n: 'سپر گرد', svg: (x, y) => `<circle cx="${x + 6}" cy="${y - 54}" r="58" fill="url(#acc-red)"/><circle cx="${x + 6}" cy="${y - 54}" r="44" fill="url(#acc-white)"/><circle cx="${x + 6}" cy="${y - 54}" r="32" fill="url(#acc-red)"/>
      <circle cx="${x + 6}" cy="${y - 54}" r="21" fill="url(#acc-navy)"/><polygon points="${star5(x + 6, y - 54, 17, 7)}" fill="#fff"/>${shine(x - 22, y - 86, 10, 18, 40, .5)}`},
  ball: {n: 'توپ قرمز و سفید', svg: (x, y) => `<circle cx="${x}" cy="${y - 42}" r="38" fill="url(#acc-white)"/><path d="M${x - 38} ${y - 42} A38 38 0 0 1 ${x + 38} ${y - 42}Z" fill="url(#acc-red)"/>
      <path d="M${x - 38} ${y - 42} H${x + 38}" stroke="#1b1830" stroke-width="7"/><circle cx="${x}" cy="${y - 42}" r="12" fill="url(#acc-white)" stroke="#1b1830" stroke-width="6"/>${shine(x - 14, y - 64, 8, 6, -20, .8)}`},
  saber: {n: 'شمشیر نورانی', svg: (x, y) => `<rect x="${x - 20}" y="${y - 200}" width="40" height="160" rx="20" fill="url(#acc-saber)" opacity=".7"/><rect x="${x - 7}" y="${y - 196}" width="14" height="154" rx="7" fill="#e8fdff"/>
      <rect x="${x - 11}" y="${y - 48}" width="22" height="66" rx="5" fill="url(#acc-silver)"/><path d="M${x - 11} ${y - 30} H${x + 11}M${x - 11} ${y - 14} H${x + 11}" stroke="#4a5068" stroke-width="4"/>`},
  staff: {n: 'عصای جادو', svg: (x, y) => `<path d="M${x - 4} ${y + 30} L${x + 10} ${y - 170}" stroke="#7a4a22" stroke-width="12" stroke-linecap="round"/>
      <circle cx="${x + 12}" cy="${y - 186}" r="22" fill="url(#acc-blue)"/>${shine(x + 4, y - 194, 7, 5, -20, .9)}<circle cx="${x + 12}" cy="${y - 186}" r="40" fill="url(#acc-shine)" opacity=".5"/>`},
};
