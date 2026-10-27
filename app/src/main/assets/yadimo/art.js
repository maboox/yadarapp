/* Yadimo vector artwork. Every highlight, contour and reflection is a
   native, independently editable SVG shape. No raster textures. */
function mascotArt(s) {
  const c=s.bodyColor,a=s.accentColor,robot=s.variant==='robot';
  const light=shade(c,45), pale=shade(c,70), dark=shade(c,-37);
  const bellPath='M164 293 C159 209 219 148 293 147 C368 145 428 204 431 286 C433 338 446 376 473 407 C484 423 464 437 439 442 Q300 474 161 442 C136 437 116 423 127 407 C154 373 168 338 164 293Z';
  const robotPath='M159 275 C158 201 215 144 292 144 C369 139 435 200 439 274 C446 323 420 345 356 353 C314 360 268 355 222 344 C181 333 157 315 159 275Z';
  const star=(x,y,size,fill='url(#gold)',id='')=>`<path ${id?`id="${id}"`:''} d="M0 -30 Q3 -30 5 -23 L12 -9 L27 -3 Q34 0 27 4 L12 10 L5 25 Q1 33 -3 26 L-11 10 L-27 4 Q-34 1 -27 -3 L-11 -10 L-4 -26 Q-2 -30 0 -30Z" transform="translate(${x} ${y}) scale(${size})" fill="${fill}"/>`;
  const glint=(x,y,rx=11,ry=6,angle=25)=>`<ellipse cx="${x}" cy="${y}" rx="${rx}" ry="${ry}" transform="rotate(${angle} ${x} ${y})" fill="white" opacity=".56"/>`;
  return `<defs>
    <radialGradient id="shell" cx=".35" cy=".13" r=".93"><stop stop-color="${shade(c,55)}"/><stop offset=".3" stop-color="${shade(c,23)}"/><stop offset=".65" stop-color="${shade(c,-4)}"/><stop offset="1" stop-color="${shade(c,-47)}"/></radialGradient>
    <linearGradient id="shell-side" x1="0" y1="0" x2="1" y2=".7"><stop stop-color="${c}" stop-opacity="0"/><stop offset="1" stop-color="${shade(c,-48)}" stop-opacity=".65"/></linearGradient>
    <linearGradient id="shell-depth" x1="0" y1="0" x2="1" y2="1"><stop stop-color="${shade(c,8)}"/><stop offset=".45" stop-color="${shade(c,-24)}"/><stop offset="1" stop-color="${shade(c,-65)}"/></linearGradient>
    <radialGradient id="limb" cx=".35" cy=".12" r="1"><stop stop-color="${shade(c,42)}"/><stop offset=".4" stop-color="${shade(c,5)}"/><stop offset=".8" stop-color="${shade(c,-28)}"/><stop offset="1" stop-color="${shade(c,-48)}"/></radialGradient>
    <linearGradient id="rim" x1="0" y1="0" x2=".4" y2="1"><stop stop-color="${pale}"/><stop offset=".6" stop-color="${light}"/><stop offset="1" stop-color="${shade(c,15)}"/></linearGradient>
    <linearGradient id="inside" x1="0" y1="0" x2=".15" y2="1"><stop stop-color="${shade(c,-55)}"/><stop offset=".55" stop-color="${shade(c,-34)}"/><stop offset="1" stop-color="${shade(c,-13)}"/></linearGradient>
    <radialGradient id="gold" cx=".33" cy=".23" r=".85"><stop stop-color="#fff1a6"/><stop offset=".43" stop-color="${shade(a,12)}"/><stop offset=".8" stop-color="${a}"/><stop offset="1" stop-color="${shade(a,-33)}"/></radialGradient>
    <linearGradient id="gold-stem" x2="1" y2="0"><stop stop-color="${shade(a,-37)}"/><stop offset="1" stop-color="${a}"/></linearGradient>
    <radialGradient id="faceLight" cx=".5" cy=".3" r=".8"><stop offset=".3" stop-color="#fffefd"/><stop offset=".76" stop-color="#fffaff"/><stop offset="1" stop-color="#f1e7ff"/></radialGradient>
    <linearGradient id="face-rim" x2=".2" y2="1"><stop stop-color="#eddbff"/><stop offset="1" stop-color="${shade(c,58)}"/></linearGradient>
    <radialGradient id="cheek"><stop stop-color="${s.cheekColor}" stop-opacity=".86"/><stop offset=".4" stop-color="${s.cheekColor}" stop-opacity=".48"/><stop offset="1" stop-color="${s.cheekColor}" stop-opacity="0"/></radialGradient>
    <radialGradient id="eye" cx=".5" cy=".2" r=".85"><stop stop-color="#241047"/><stop offset=".65" stop-color="#352066"/><stop offset="1" stop-color="#6d42a5"/></radialGradient>
    <linearGradient id="mouth-fill" x2=".2" y2="1"><stop stop-color="#9b265f"/><stop offset="1" stop-color="#d7447f"/></linearGradient>
    <radialGradient id="ground"><stop stop-color="${shade(c,-25)}" stop-opacity=".34"/><stop offset=".6" stop-color="${c}" stop-opacity=".14"/><stop offset="1" stop-color="${c}" stop-opacity="0"/></radialGradient>
    <linearGradient id="porcelain" x2=".7" y2="1"><stop stop-color="#ffffff"/><stop offset=".6" stop-color="#f8f2ff"/><stop offset="1" stop-color="#d8c3ff"/></linearGradient>
    <radialGradient id="sculpt-light"><stop stop-color="#fff7ff" stop-opacity=".37"/><stop offset=".6" stop-color="#fff7ff" stop-opacity=".1"/><stop offset="1" stop-color="#fff7ff" stop-opacity="0"/></radialGradient>
    <radialGradient id="sculpt-shadow"><stop stop-color="${shade(c,-65)}" stop-opacity=".32"/><stop offset="1" stop-color="${shade(c,-65)}" stop-opacity="0"/></radialGradient>
    <radialGradient id="ceramic-glow"><stop stop-color="#ffffff" stop-opacity=".7"/><stop offset=".5" stop-color="#fff5ff" stop-opacity=".22"/><stop offset="1" stop-color="#fff5ff" stop-opacity="0"/></radialGradient>
    <radialGradient id="face-inset"><stop stop-color="${shade(c,-65)}" stop-opacity=".35"/><stop offset="1" stop-color="${shade(c,-65)}" stop-opacity="0"/></radialGradient>
    <clipPath id="shell-clip"><path d="${robot?robotPath:bellPath}"/></clipPath>
  </defs>
  <ellipse id="ground-shadow" cx="300" cy="540" rx="177" ry="24" fill="url(#ground)"/>
  <g id="position"><g id="character"><g id="design-angle" transform="rotate(-11 300 300)">
  ${s.sparkles?`<g id="sparkles" fill="url(#gold)">${star(robot?112:448,robot?368:160,.83)}${robot?star(128,424,.38):''}<path d="M139 185 L127 155 M115 202 L89 181" fill="none" stroke="${shade(a,12)}" stroke-width="12" stroke-linecap="round"/>${robot?'':`<path d="M105 393 Q89 365 103 341" fill="none" stroke="${shade(a,18)}" stroke-width="8" stroke-linecap="round"/>`}</g>`:''}
  ${robot?robotStructure():bellStructure()}
  <g id="face" ${robot?'transform="translate(0 -3)"':''}>
    <ellipse id="face-contact-shadow" cx="302" cy="337" rx="105" ry="21" fill="url(#face-inset)" opacity=".75"/>
    <path id="face-border" d="M184 278 C183 234 232 198 294 198 C355 198 405 233 412 279 C420 322 377 347 300 347 C226 347 183 325 184 278Z" fill="url(#face-rim)"/>
    <path id="face-porcelain" d="M195 283 C194 242 238 213 295 213 C351 213 394 243 401 282 C409 319 369 339 300 339 C235 339 194 320 195 283Z" fill="url(#faceLight)"/>
    <g id="brows" stroke="${shade(c,-25)}" stroke-width="5" stroke-linecap="round" fill="none"><path id="brow-left" d="M242 241 Q248 235 255 239"/><path id="brow-right" d="M342 238 Q349 235 355 241"/></g>
    <g id="cheeks" ${s.blush?'':'visibility="hidden"'}><g id="cheek-left"><ellipse cx="222" cy="304" rx="27" ry="25" fill="url(#cheek)"/><circle cx="220" cy="300" r="2.3" fill="white" opacity=".95"/></g><g id="cheek-right"><ellipse cx="378" cy="304" rx="27" ry="25" fill="url(#cheek)"/><circle cx="380" cy="300" r="2.3" fill="white" opacity=".95"/></g></g>
    <g id="eyes" transform="translate(${Number(s.gaze)} 0)">${eyes()}</g>
    <g id="mouth">${mouth()}</g>
  </g>
  </g></g></g>`;

  function bellStructure(){return `
    <g id="arm-right"><path d="M401 304 C437 286 492 282 513 310 C542 346 508 371 475 358 C447 350 419 329 401 304Z" fill="url(#limb)"/>${glint(496,310,12,8)}</g>
    <path id="shell-back" d="${bellPath}" fill="url(#shell-depth)"/>
    <path id="bell-body" d="${bellPath}" fill="url(#shell)"/>
    ${sculpt()}
    <path id="shell-left-light" d="M165 294 C160 214 218 153 289 150 C214 182 189 235 188 304 C185 349 165 388 146 412 L130 416 C148 380 168 338 165 294Z" fill="${shade(c,17)}" opacity=".72"/>
    <g id="shell-glint">${glint(354,174,17,7,30)}</g>
    <g id="bell-rim"><ellipse cx="300" cy="428" rx="178" ry="36" fill="url(#rim)"/><ellipse cx="300" cy="433" rx="159" ry="24" fill="url(#inside)"/><path d="M127 425 C171 387 410 385 474 422" fill="none" stroke="${shade(c,75)}" stroke-width="4" stroke-linecap="round" opacity=".55"/><path d="M147 445 Q300 484 453 445" fill="none" stroke="${shade(c,48)}" stroke-width="3" opacity=".7"/></g>
    <g id="clapper"><circle cx="300" cy="495" r="23" fill="url(#gold)"/><ellipse cx="307" cy="485" rx="7" ry="8" fill="#fffef3" transform="rotate(25 307 485)"/></g>
    <g id="arm-left"><path d="M185 311 C162 296 126 289 109 306 C89 326 105 351 136 355 C160 359 182 350 185 331Z" fill="url(#limb)"/><path d="M109 335 Q135 354 173 335 Q165 355 137 355 Q116 353 109 335" fill="${shade(c,-28)}" opacity=".26"/>${glint(133,308,12,7,8)}</g>
    <g id="antenna"><g id="antenna-base"><ellipse cx="300" cy="149" rx="17" ry="4" fill="${dark}" opacity=".26"/><path d="M288 148 L293 126 Q300 118 307 126 L313 148 Q301 153 288 148" fill="url(#limb)"/></g><g id="antenna-orb"><circle cx="301" cy="108" r="28" fill="url(#gold)"/><ellipse cx="309" cy="95" rx="10" ry="8" fill="#fffef4" transform="rotate(18 309 95)"/></g></g>`}

  function robotStructure(){return `
    <g id="thruster"><ellipse cx="300" cy="481" rx="61" ry="16" fill="none" stroke="#fffdf2" stroke-width="9" transform="rotate(20 300 481)"/><path d="M257 462 Q301 443 342 462" fill="none" stroke="#fffdf5" stroke-width="6" stroke-linecap="round" opacity=".9"/></g>
    <g id="torso"><path d="M257 340 C234 361 221 399 229 422 C235 451 344 459 363 427 C378 402 362 362 344 343Z" fill="url(#shell)"/><path d="M233 423 Q299 408 362 426 Q353 451 299 450 Q251 451 233 423" fill="url(#rim)"/><ellipse cx="299" cy="440" rx="43" ry="15" fill="url(#inside)"/><ellipse cx="299" cy="447" rx="28" ry="7" fill="${light}"/></g>
    <g id="arm-left"><path d="M234 362 C216 350 190 351 180 369 C171 388 185 410 207 409 C229 408 244 380 234 362Z" fill="url(#porcelain)"/><ellipse cx="184" cy="386" rx="30" ry="28" fill="url(#limb)"/>${glint(176,374,10,6,-25)}</g>
    <g id="arm-right"><path d="M366 362 C384 350 410 351 420 369 C429 388 415 410 393 409 C371 408 356 380 366 362Z" fill="url(#porcelain)"/><ellipse cx="416" cy="386" rx="30" ry="28" fill="url(#limb)"/>${glint(410,374,10,6,-25)}</g>
    <path id="shell-back" d="${robotPath}" fill="url(#shell-depth)"/>
    <path id="robot-head" d="${robotPath}" fill="url(#shell)"/>
    ${sculpt()}
    <path id="head-highlight" d="M167 245 C180 184 233 148 291 146 C351 140 407 182 424 228 C390 185 351 167 297 163 C240 159 203 188 167 245Z" fill="${light}" opacity=".8"/>
    <g id="shell-glint">${glint(365,174,18,7,35)}</g>
    <g id="robot-ears"><g id="ear-left"><ellipse cx="167" cy="275" rx="25" ry="43" fill="url(#shell-depth)"/><ellipse cx="157" cy="275" rx="31" ry="56" fill="url(#inside)"/><ellipse cx="146" cy="274" rx="28" ry="52" fill="url(#rim)"/><g id="ear-left-cap"><ellipse cx="132" cy="275" rx="26" ry="47" fill="url(#porcelain)"/><ellipse cx="126" cy="276" rx="19" ry="34" fill="url(#shell)"/><ellipse cx="124" cy="276" rx="11" ry="21" fill="url(#porcelain)"/><ellipse cx="121" cy="273" rx="7" ry="14" fill="white"/></g></g><g id="ear-right"><ellipse cx="435" cy="275" rx="25" ry="43" fill="url(#shell-depth)"/><ellipse cx="442" cy="275" rx="27" ry="51" fill="url(#inside)"/><g id="ear-right-cap"><ellipse cx="455" cy="274" rx="23" ry="43" fill="url(#porcelain)"/><ellipse cx="460" cy="274" rx="16" ry="35" fill="url(#shell)"/></g></g></g>
    <g id="antenna"><g id="antenna-base"><ellipse cx="300" cy="145" rx="17" ry="4" fill="${dark}" opacity=".25"/><path d="M288 145 L293 123 Q300 116 307 123 L313 145 Q301 151 288 145" fill="url(#limb)"/></g><g id="antenna-orb"><circle cx="301" cy="104" r="29" fill="url(#gold)"/><ellipse cx="309" cy="91" rx="10" ry="8" fill="#fffef4" transform="rotate(18 309 91)"/></g></g>`}

  function sculpt(){return '<g id="sculpted-light" clip-path="url(#shell-clip)"><ellipse cx="256" cy="190" rx="163" ry="122" fill="url(#sculpt-light)"/><ellipse cx="406" cy="302" rx="66" ry="175" fill="url(#sculpt-shadow)"/><ellipse cx="195" cy="379" rx="42" ry="82" fill="url(#sculpt-shadow)" opacity=".65"/><ellipse cx="248" cy="202" rx="105" ry="80" fill="url(#ceramic-glow)" opacity=".7"/><ellipse cx="309" cy="390" rx="130" ry="65" fill="url(#sculpt-shadow)" opacity=".48"/></g>'}
  function eyes(){const e=s.expression;const wrap=(x,content)=>`<g id="eye-${x<300?'left':'right'}">${content}</g>`;
    const eye=x=>wrap(x,`<ellipse cx="${x}" cy="275" rx="18" ry="25" fill="url(#eye)"/><ellipse cx="${x-1}" cy="263" rx="6.5" ry="8" fill="#fffefd" transform="rotate(-20 ${x-1} 263)"/>${star(x-2,288,.22,'#ffe187')}<ellipse cx="${x+10}" cy="279" rx="2.5" ry="3.5" fill="#b895f8" opacity=".4"/>`);
    if(e==='sleepy')return [252,348].map(x=>wrap(x,`<path d="M${x-15} 276 Q${x} 292 ${x+15} 277" fill="none" stroke="#30205e" stroke-width="7" stroke-linecap="round"/>`)).join('');
    if(e==='wink')return wrap(252,'<path d="M236 276 Q251 263 267 279" fill="none" stroke="#30205e" stroke-width="9" stroke-linecap="round"/>')+eye(348);
    if(e==='love')return [252,348].map(x=>wrap(x,`<path d="M${x} 296 C${x-40} 273 ${x-15} 248 ${x} 268 C${x+15} 248 ${x+40} 273 ${x} 296Z" fill="#ef72a8"/>${glint(x-10,269,4,6,-15)}`)).join('');
    if(e==='focused')return wrap(252,'<path d="M238 270 L266 279" fill="none" stroke="#30205e" stroke-width="8" stroke-linecap="round"/>')+wrap(348,'<path d="M334 279 L362 270" fill="none" stroke="#30205e" stroke-width="8" stroke-linecap="round"/>');
    return eye(252)+eye(348);
  }
  function mouth(){if(s.expression==='surprised')return '<ellipse cx="300" cy="311" rx="12" ry="16" fill="url(#mouth-fill)"/><ellipse cx="300" cy="319" rx="7" ry="5" fill="#ff8db5"/>';
    if(['sleepy','wink','focused'].includes(s.expression))return '<path d="M287 309 Q300 322 313 309" fill="none" stroke="#30205e" stroke-width="5" stroke-linecap="round"/>';
    return '<path d="M279 302 Q299 309 320 302 C324 326 313 339 300 339 C286 339 277 321 279 302Z" fill="url(#mouth-fill)"/><path d="M283 324 C292 316 306 318 316 326 Q310 339 300 339 Q289 338 283 324Z" fill="#ff8caf"/>';
  }
}
