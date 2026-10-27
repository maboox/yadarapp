/* Yadimo accessories. Drawn in the same 600 × 600 space as the character. Hats are pinned to the head surface,
   glasses to the face and neck pieces to the body, so they turn with the 2.5D head instead of floating in front. */
const ACC_DEFS = `
  <linearGradient id="acc-purple" x1="0" y1="0" x2=".9" y2="1"><stop stop-color="#b9a3ff"/><stop offset=".45" stop-color="#7c5cf0"/><stop offset="1" stop-color="#3f27a8"/></linearGradient>
  <linearGradient id="acc-pink" x1="0" y1="0" x2=".9" y2="1"><stop stop-color="#ffc2dc"/><stop offset=".5" stop-color="#ff7fae"/><stop offset="1" stop-color="#c93d78"/></linearGradient>
  <linearGradient id="acc-red" x1="0" y1="0" x2=".9" y2="1"><stop stop-color="#ff9a9a"/><stop offset=".5" stop-color="#ef4a5a"/><stop offset="1" stop-color="#a51f35"/></linearGradient>
  <linearGradient id="acc-white" x1="0" y1="0" x2=".7" y2="1"><stop stop-color="#ffffff"/><stop offset=".6" stop-color="#f6f1ff"/><stop offset="1" stop-color="#d9cbf7"/></linearGradient>
  <linearGradient id="acc-dark" x1="0" y1="0" x2=".7" y2="1"><stop stop-color="#5b5486"/><stop offset=".55" stop-color="#2a2450"/><stop offset="1" stop-color="#120e2a"/></linearGradient>
  <linearGradient id="acc-green" x1="0" y1="0" x2=".8" y2="1"><stop stop-color="#b6f5c8"/><stop offset=".5" stop-color="#4fcf86"/><stop offset="1" stop-color="#1f8a52"/></linearGradient>
  <linearGradient id="acc-blue" x1="0" y1="0" x2=".8" y2="1"><stop stop-color="#bfe3ff"/><stop offset=".5" stop-color="#5aa8f5"/><stop offset="1" stop-color="#2463c4"/></linearGradient>
  <linearGradient id="acc-lens" x1="0" y1="0" x2=".3" y2="1"><stop stop-color="#4b4475"/><stop offset="1" stop-color="#0c0920"/></linearGradient>
  <radialGradient id="acc-shine"><stop stop-color="#fff" stop-opacity=".85"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></radialGradient>`;

const star5 = (cx, cy, R, r, rot = -90) => Array.from({length: 10}, (_, i) => {
  const a = (rot + i * 36) * Math.PI / 180, q = i % 2 ? r : R; return `${(cx + Math.cos(a) * q).toFixed(1)},${(cy + Math.sin(a) * q).toFixed(1)}`; }).join(' ');
const heartPath = (x, y, k) => `M${x} ${y + 22 * k} C${x - 44 * k} ${y - 2 * k} ${x - 20 * k} ${y - 40 * k} ${x} ${y - 16 * k} C${x + 20 * k} ${y - 40 * k} ${x + 44 * k} ${y - 2 * k} ${x} ${y + 22 * k}Z`;
const shine = (x, y, rx, ry, r = -25, o = .55) => `<ellipse cx="${x}" cy="${y}" rx="${rx}" ry="${ry}" transform="rotate(${r} ${x} ${y})" fill="white" opacity="${o}"/>`;
const soft = (x, y, rx, ry, o = .22) => `<ellipse cx="${x}" cy="${y}" rx="${rx}" ry="${ry}" fill="#1b1040" opacity="${o}"/>`;

/* Hats: anchor (300, 196) = the line where a hat sits on the dome. */
const HATS = {
  none:   {n: 'بدون', svg: () => ''},
  party:  {n: 'کلاه جشن', svg: () => `${soft(300, 200, 70, 13)}<g transform="rotate(9 300 196)">
      <path d="M234 196 L304 30 L370 196 Q302 214 234 196Z" fill="url(#acc-purple)"/>
      <clipPath id="acc-party-clip"><path d="M234 196 L304 30 L370 196 Q302 214 234 196Z"/></clipPath>
      <g clip-path="url(#acc-party-clip)" stroke="url(#gold)" stroke-width="15" stroke-linecap="round"><path d="M220 160 L390 132"/><path d="M220 104 L390 76"/></g>
      <circle cx="270" cy="182" r="6" fill="#ff8fb4"/><circle cx="318" cy="120" r="6" fill="#6fe0b2"/><circle cx="292" cy="80" r="5" fill="#7cc0ff"/>
      ${shine(266, 130, 8, 34, 22, .4)}<circle cx="304" cy="28" r="24" fill="url(#gold)"/>${shine(296, 18, 8, 6, 0, .9)}</g>`},
  cap:    {n: 'کلاه کپ', svg: () => `${soft(300, 206, 112, 14)}
      <path d="M186 204 C178 92 422 92 414 204 Q300 222 186 204Z" fill="url(#acc-purple)"/>
      <path d="M300 104 C366 104 410 146 414 204 Q356 214 300 216Z" fill="url(#gold)"/>
      <path d="M300 104 V216" stroke="#3f27a8" stroke-width="3" opacity=".45"/>
      <path d="M388 196 Q486 176 512 212 Q456 230 384 216Z" fill="url(#acc-purple)"/>${shine(462, 196, 30, 6, -10, .35)}
      <circle cx="300" cy="104" r="12" fill="url(#gold)"/>${shine(236, 140, 14, 30, 30, .45)}`},
  crown:  {n: 'تاج', svg: () => `${soft(300, 204, 92, 12)}
      <path d="M210 202 L198 112 L252 158 L300 84 L348 158 L402 112 L390 202 Q300 220 210 202Z" fill="url(#gold)"/>
      <path d="M214 188 Q300 204 386 188" stroke="#b07a00" stroke-width="4" fill="none" opacity=".45"/>
      ${[[198, 108, '#ff5c8a'], [300, 80, '#5aa8f5'], [402, 108, '#4fcf86'], [300, 184, '#ff5c8a']].map(([x, y, c]) => `<circle cx="${x}" cy="${y}" r="13" fill="${c}"/>${shine(x - 4, y - 4, 4, 3, 0, .9)}`).join('')}
      ${shine(232, 160, 6, 22, 15, .5)}`},
  beanie: {n: 'کلاه بافتنی', svg: () => `${soft(300, 214, 120, 14)}
      <path d="M182 210 C172 80 428 80 418 210Z" fill="url(#acc-purple)"/>
      ${[214, 256, 300, 344, 386].map(x => `<path d="M${x} 200 Q${x + (x - 300) * .1} 140 ${x + (x - 300) * .2} 102" stroke="#fff" stroke-opacity=".14" stroke-width="10" fill="none" stroke-linecap="round"/>`).join('')}
      <path d="M174 196 Q300 164 426 196 L424 240 Q300 210 176 240Z" fill="url(#gold)"/>
      ${[200, 244, 288, 332, 376, 410].map(x => `<path d="M${x} 194 V232" stroke="#b07a00" stroke-width="5" opacity=".25"/>`).join('')}
      <circle cx="300" cy="80" r="34" fill="url(#acc-white)"/>${shine(288, 66, 12, 8, 0, .9)}`},
  phones: {n: 'هدفون', svg: () => `<path d="M168 292 C146 66 454 66 432 292" fill="none" stroke="url(#acc-purple)" stroke-width="20" stroke-linecap="round"/>
      <path d="M196 150 C240 100 330 92 392 126" fill="none" stroke="#fff" stroke-opacity=".4" stroke-width="5" stroke-linecap="round"/>
      ${[164, 436].map(x => `<ellipse cx="${x}" cy="296" rx="32" ry="54" fill="url(#acc-purple)"/><ellipse cx="${x}" cy="296" rx="18" ry="34" fill="url(#gold)"/>${shine(x - 10, 274, 6, 14, 0, .5)}`).join('')}`},
  bow:    {n: 'پاپیون', svg: () => `<g transform="translate(226 178) rotate(-22)">
      <path d="M0 0 Q-50 -62 -76 -14 Q-60 40 0 0Z" fill="url(#acc-pink)"/><path d="M0 0 Q50 -62 76 -14 Q60 40 0 0Z" fill="url(#acc-pink)"/>
      ${shine(-46, -20, 10, 18, 40, .5)}${shine(40, -24, 8, 14, -40, .45)}<circle r="18" fill="url(#acc-pink)"/>${shine(-5, -6, 6, 4, 0, .8)}</g>`},
  wizard: {n: 'جادوگر', svg: () => `${soft(300, 206, 118, 16)}
      <path d="M210 196 C226 132 252 66 320 -4 C334 -16 356 -10 362 6 C352 16 344 24 342 40 C350 98 370 150 392 196Z" fill="url(#acc-purple)"/>
      <path d="M342 40 C350 98 370 150 392 196 L346 196 C350 140 346 92 330 10Z" fill="#241270" opacity=".3"/>
      <path d="M362 6 C392 2 412 26 404 58 C394 40 380 32 358 36Z" fill="url(#acc-purple)"/>
      <ellipse cx="300" cy="198" rx="150" ry="30" fill="url(#acc-purple)"/><ellipse cx="300" cy="192" rx="146" ry="20" fill="#fff" opacity=".1"/>
      <path d="M212 180 Q300 214 388 180 L392 202 Q300 238 208 202Z" fill="url(#gold)"/>
      <rect x="282" y="190" width="36" height="28" rx="7" fill="#ffe27a"/><rect x="292" y="198" width="16" height="12" rx="3" fill="#6a4fd8"/>
      <polygon points="${star5(276, 104, 16, 7)}" fill="url(#gold)"/><polygon points="${star5(326, 142, 11, 5)}" fill="url(#gold)"/><polygon points="${star5(250, 158, 9, 4)}" fill="url(#gold)"/>
      <path d="M308 50 A18 18 0 1 0 324 76 A14 14 0 1 1 308 50Z" fill="url(#gold)"/>${shine(246, 128, 8, 40, 25, .35)}`},
  grad:   {n: 'فارغ‌التحصیلی', svg: () => `<path d="M224 176 Q300 220 376 176 L376 214 Q300 256 224 214Z" fill="url(#acc-dark)"/>
      <path d="M128 150 L300 98 L472 150 L300 202Z" fill="url(#acc-dark)"/><path d="M300 202 L472 150 L472 160 L300 214 L128 160 L128 150Z" fill="#0c0920" opacity=".6"/>
      ${shine(228, 132, 50, 8, -17, .2)}<circle cx="300" cy="150" r="9" fill="url(#gold)"/>
      <path d="M300 150 Q420 152 452 168 L456 236" fill="none" stroke="url(#gold)" stroke-width="6" stroke-linecap="round"/>
      <path d="M442 230 L470 230 L474 272 L438 272Z" fill="url(#gold)"/>`},
  chef:   {n: 'کلاه آشپز', svg: () => `${soft(300, 214, 104, 12)}
      ${[[214, 120, 44], [386, 120, 44], [256, 82, 52], [344, 82, 52], [300, 62, 50]].map(([x, y, r]) => `<circle cx="${x}" cy="${y}" r="${r}" fill="url(#acc-white)"/>`).join('')}
      <path d="M200 162 H400 V206 Q300 224 200 206Z" fill="url(#acc-white)"/>
      <path d="M244 168 V208M300 170 V214M356 168 V208" stroke="#d8cbf5" stroke-width="5"/>${shine(268, 56, 16, 10, -20, .8)}`},
  flowers:{n: 'تاج گل', svg: () => {
      const leaf = (x, y, r) => `<ellipse cx="${x}" cy="${y}" rx="24" ry="10" transform="rotate(${r} ${x} ${y})" fill="url(#acc-green)"/>`;
      const fl = (x, y, c) => [0, 72, 144, 216, 288].map(a => `<circle cx="${(x + Math.cos(a * Math.PI / 180) * 12).toFixed(1)}" cy="${(y + Math.sin(a * Math.PI / 180) * 12).toFixed(1)}" r="11" fill="${c}"/>`).join('') + `<circle cx="${x}" cy="${y}" r="8" fill="url(#gold)"/>`;
      return `<path d="M176 214 Q300 96 424 214" fill="none" stroke="#2f9a5e" stroke-width="10" stroke-linecap="round"/>` +
        leaf(196, 186, -60) + leaf(226, 150, -40) + leaf(266, 128, -14) + leaf(334, 128, 14) + leaf(374, 150, 40) + leaf(404, 186, 60) +
        fl(214, 170, '#ff9ec7') + fl(300, 120, '#fff3a0') + fl(386, 170, '#ff9ec7') + fl(256, 136, '#ffffff') + fl(344, 136, '#c9b8ff'); }},
  night:  {n: 'کلاه خواب', svg: () => `<path d="M184 214 C170 90 330 52 420 116 C470 150 470 192 462 246 C436 180 380 166 300 168 C250 168 210 182 184 214Z" fill="url(#acc-blue)"/>
      <path d="M180 212 Q300 162 422 198 L416 232 Q300 192 186 246Z" fill="url(#acc-white)"/>
      <circle cx="462" cy="250" r="26" fill="url(#acc-white)"/>${shine(452, 240, 8, 6, 0, .9)}
      <polygon points="${star5(318, 106, 12, 5)}" fill="url(#gold)"/><polygon points="${star5(378, 132, 9, 4)}" fill="url(#gold)"/>${shine(240, 130, 10, 34, 40, .35)}`},
};

/* Glasses: in the face plane (eyes at 252 and 348, y 275). */
const GLASSES = {
  none:  {n: 'بدون', svg: () => ''},
  round: {n: 'گرد', svg: () => `<g fill="#fff" fill-opacity=".2" stroke="#3b2a7a" stroke-width="7"><circle cx="252" cy="275" r="36"/><circle cx="348" cy="275" r="36"/></g>
      <path d="M288 272 Q300 260 312 272M216 268 L190 258M384 268 L410 258" fill="none" stroke="#3b2a7a" stroke-width="7" stroke-linecap="round"/>
      <path d="M230 254 Q240 244 254 243" stroke="#fff" stroke-width="5" fill="none" stroke-linecap="round" opacity=".85"/>`},
  sun:   {n: 'آفتابی', svg: () => `${[252, 348].map(x => `<path d="M${x - 44} 252 H${x + 44} V272 Q${x + 44} 310 ${x} 310 Q${x - 44} 310 ${x - 44} 272Z" fill="url(#acc-lens)"/>${shine(x - 18, 264, 14, 5, -10, .5)}`).join('')}
      <path d="M296 258 H304M208 256 L188 250M392 256 L412 250" stroke="#0c0920" stroke-width="8" stroke-linecap="round"/>`},
  star:  {n: 'ستاره‌ای', svg: () => `<polygon points="${star5(252, 278, 46, 21)}" fill="url(#gold)"/><polygon points="${star5(348, 278, 46, 21)}" fill="url(#gold)"/>
      <path d="M286 274 H314" stroke="#b07a00" stroke-width="6"/>${shine(236, 258, 6, 10, 20, .7)}${shine(332, 258, 6, 10, 20, .7)}`},
  heart: {n: 'قلبی', svg: () => `<path d="${heartPath(252, 280, 1.15)}" fill="url(#acc-pink)" fill-opacity=".92"/><path d="${heartPath(348, 280, 1.15)}" fill="url(#acc-pink)" fill-opacity=".92"/>
      <path d="M282 268 Q300 258 318 268" stroke="#c93d78" stroke-width="6" fill="none"/>${shine(232, 262, 6, 9, 30, .8)}${shine(328, 262, 6, 9, 30, .8)}`},
};

/* Neck pieces: anchor (300, 362) on the bell, (300, 346) on the bot. */
const NECK = {
  none:   {n: 'بدون', svg: () => ''},
  bowtie: {n: 'پاپیون گردن', svg: () => `<path d="M300 362 L248 334 Q236 362 248 390Z" fill="url(#acc-red)"/><path d="M300 362 L352 334 Q364 362 352 390Z" fill="url(#acc-red)"/>
      <ellipse cx="300" cy="362" rx="14" ry="15" fill="url(#acc-red)"/>${shine(256, 350, 5, 10, 20, .45)}`},
  scarf:  {n: 'شال', svg: () => `<path d="M170 350 Q300 394 430 350 L436 384 Q300 432 164 384Z" fill="url(#gold)"/>
      ${[204, 252, 300, 348, 396].map(x => `<path d="M${x} ${364 + Math.abs(300 - x) * -.06} v32" stroke="#b07a00" stroke-width="6" opacity=".25" stroke-linecap="round"/>`).join('')}
      <path d="M352 392 L392 386 L404 470 L360 476Z" fill="url(#gold)"/>`},
  medal:  {n: 'مدال', svg: () => `<path d="M256 344 L288 404 M344 344 L312 404" stroke="url(#acc-red)" stroke-width="16" stroke-linecap="round"/>
      <circle cx="300" cy="418" r="30" fill="url(#gold)"/><polygon points="${star5(300, 418, 17, 8)}" fill="#fff" opacity=".9"/>`},
  tie:    {n: 'کراوات', svg: () => `<path d="M284 350 H316 L310 368 L326 438 L300 462 L274 438 L290 368Z" fill="url(#acc-blue)"/>
      <path d="M288 354 H312 L308 368 H292Z" fill="#2463c4"/><path d="M292 400 L318 390M288 424 L320 412" stroke="#fff" stroke-opacity=".35" stroke-width="5"/>`},
};

/* Hand items, drawn around the hand (hx, hy) and turned with the arm. */
const ITEMS = {
  none:    {n: 'بدون', svg: () => ''},
  wand:    {n: 'ستاره', svg: (x, y) => `<path d="M${x - 6} ${y + 10} L${x + 22} ${y - 70}" stroke="url(#gold)" stroke-width="9" stroke-linecap="round"/>
      <polygon points="${star5(x + 26, y - 86, 34, 14)}" fill="url(#gold)"/>${shine(x + 14, y - 98, 6, 9, 20, .8)}`},
  cup:     {n: 'قهوه', svg: (x, y) => `<path d="M${x - 30} ${y - 64} H${x + 30} L${x + 22} ${y - 8} Q${x + 20} ${y + 4} ${x + 6} ${y + 4} H${x - 6} Q${x - 20} ${y + 4} ${x - 22} ${y - 8}Z" fill="url(#acc-white)"/>
      <path d="M${x + 30} ${y - 52} Q${x + 58} ${y - 52} ${x + 54} ${y - 30} Q${x + 50} ${y - 12} ${x + 24} ${y - 16}" fill="none" stroke="url(#acc-white)" stroke-width="9"/>
      <ellipse cx="${x}" cy="${y - 62}" rx="26" ry="6" fill="#7a4a2a"/><path d="M${x - 8} ${y - 78} q-8 -16 4 -28M${x + 10} ${y - 78} q-8 -16 4 -28" stroke="#b8acd6" stroke-width="5" fill="none" stroke-linecap="round" opacity=".7"/>`},
  phone:   {n: 'گوشی', svg: (x, y) => `<rect x="${x - 26}" y="${y - 94}" width="52" height="92" rx="14" fill="url(#acc-purple)"/><rect x="${x - 19}" y="${y - 84}" width="38" height="70" rx="6" fill="#f3efff"/>
      <path d="M${x - 9} ${y - 50} l7 8 l14 -18" fill="none" stroke="#6a4fd8" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>`},
  balloon: {n: 'بادکنک', svg: (x, y) => `<path d="M${x} ${y} Q${x - 14} ${y - 40} ${x + 6} ${y - 76}" fill="none" stroke="#8a80b8" stroke-width="3"/>
      <ellipse cx="${x + 12}" cy="${y - 128}" rx="40" ry="50" fill="url(#acc-pink)"/><path d="M${x + 12} ${y - 78} l-8 10 h16Z" fill="#c93d78"/>${shine(x - 4, y - 150, 9, 16, 25, .7)}`},
  flag:    {n: 'پرچم', svg: (x, y) => `<path d="M${x} ${y + 14} V${y - 150}" stroke="#8a5a2a" stroke-width="8" stroke-linecap="round"/>
      <path d="M${x + 4} ${y - 150} Q${x + 44} ${y - 166} ${x + 84} ${y - 146} Q${x + 56} ${y - 130} ${x + 84} ${y - 110} Q${x + 44} ${y - 92} ${x + 4} ${y - 112}Z" fill="url(#acc-purple)"/>
      <polygon points="${star5(x + 38, y - 130, 13, 6)}" fill="url(#gold)"/><circle cx="${x}" cy="${y - 154}" r="9" fill="url(#gold)"/>`},
};
