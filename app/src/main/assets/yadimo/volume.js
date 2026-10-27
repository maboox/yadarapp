/* Analytic volume projection into editable SVG. A shared head frame projects
   the shell, ceramic panel, features, ear sockets and antenna. The volume is
   expressed as paths, not a loaded mesh or a WebGL scene. */
const clamp=(n,a,b)=>Math.max(a,Math.min(b,n));
const radians=d=>d*Math.PI/180;
const rounded=n=>Number(n.toFixed(4));
function rigMatrix(x,y,a,b,c,d,dx=0,dy=0){return `matrix(${[a,b,c,d,x+dx-a*x-c*y,y+dy-b*x-d*y].map(rounded).join(' ')})`}
function springStep(current,velocity,target,dt,omega=11){
  const delta=current-target,temp=(velocity+omega*delta)*dt,decay=Math.exp(-omega*dt);
  return [target+(delta+temp)*decay,(velocity-omega*temp)*decay];
}
function headPoint(x,y,yaw,pitch,roll=0,depth=0){
  const u=x-300,v=y-330,z=52+depth;
  const horizontal=u*Math.cos(yaw)+z*Math.sin(yaw),turnedZ=-u*Math.sin(yaw)+z*Math.cos(yaw);
  const vertical=v*Math.cos(pitch)-turnedZ*Math.sin(pitch),viewZ=v*Math.sin(pitch)+turnedZ*Math.cos(pitch);
  const perspective=clamp(1/(1-(viewZ-z)/1050),.86,1.14);
  return [300+(horizontal*Math.cos(roll)-vertical*Math.sin(roll))*perspective,330+(horizontal*Math.sin(roll)+vertical*Math.cos(roll))*perspective];
}
function surfacePoint(x,y,yaw,pitch,roll=0){
  const u=x-300,v=y-270,depth=7-2*u*u/16000-v*v/16000;
  return headPoint(x,y,yaw,pitch,roll,depth);
}
function matrixFor(map,x,y){
  const [px,py]=map(x,y),e=.05,[xx,xy]=map(x+e,y),[yx,yy]=map(x,y+e);
  return rigMatrix(x,y,(xx-px)/e,(xy-py)/e,(yx-px)/e,(yy-py)/e,px-x,py-y);
}
function surfaceMatrix(x,y,yaw,pitch,roll=0){return matrixFor((a,b)=>surfacePoint(a,b,yaw,pitch,roll),x,y)}
function tokensFor(node){return node.getAttribute('d').match(/[a-z]|[-+]?(?:\d*\.?\d+)(?:e[-+]?\d+)?/gi)}
function warpPathTokens(tokens,map){
  const out=[];
  for(let i=0;i<tokens.length;i++){
    if(/^[a-z]$/i.test(tokens[i]))out.push(tokens[i]);
    else{const [x,y]=map(Number(tokens[i]),Number(tokens[++i]));out.push(rounded(x),rounded(y))}
  }
  return out.join(' ');
}
function bellBend(x,y,yaw,pitch,roll=0,depth=0){
  pitch=clamp(pitch,-radians(18),radians(18));roll=clamp(roll,-radians(8),radians(8));
  if(y<=350)return headPoint(x,y,yaw,pitch,roll,depth);
  if(y>=435)return [x,y];
  // A monotone neck-to-skirt transition avoids a folded silhouette when the
  // head looks down. The complete face stays in the rigid upper region.
  const length=85,t=(y-350)/length,p=headPoint(x,350,yaw,pitch,roll,depth),q=headPoint(x,350.01,yaw,pitch,roll,depth),gap=435-p[1];
  const hermite=(a,b,m0,m1)=>((2*t-3)*t*t+1)*a+((t-2)*t+1)*t*m0+(-2*t+3)*t*t*b+(t-1)*t*t*m1;
  return [hermite(p[0],x,(q[0]-p[0])/.01*length,0),hermite(p[1],435,clamp((q[1]-p[1])/.01*length,0,gap*1.5),Math.min(length,gap*1.5))];
}
class VectorRig {
  constructor(svg,prefix=''){this.svg=svg;this.P=prefix;this.current={headYaw:0,headPitch:0,headRoll:0,bodyYaw:0,bodyPitch:0,bodyRoll:0};this.velocity={...this.current}}
  bind(robot){
    this.robot=robot;this.nodes={};const find=id=>this.nodes[id]=this.svg.querySelector('#'+this.P+id);
    const root=find('design-angle'),children=[...root.children].filter(n=>n.id!=='sparkles');
    this.body=document.createElementNS('http://www.w3.org/2000/svg','g');this.body.id=this.P+'body-rig';root.append(this.body);this.body.append(...children);
    if(robot){
      this.head=document.createElementNS('http://www.w3.org/2000/svg','g');this.head.id=this.P+'head-rig';this.body.insertBefore(this.head,find('shell-back'));
      const neck=document.createElementNS('http://www.w3.org/2000/svg','ellipse');neck.id=this.P+'neck-joint';
      for(const [k,v] of Object.entries({cx:300,cy:348,rx:37,ry:19,fill:'url(#'+this.P+'limb)'}))neck.setAttribute(k,v);
      this.body.insertBefore(neck,this.head);
      // Sockets stay behind the front shell; their collars are occluded by it.
      for(const id of ['shell-back','robot-ears','robot-head','sculpted-light','head-highlight','shell-glint','antenna','face'])this.head.append(find(id));
    }else this.head=null;
    for(const id of ['face','eyes','eye-left','eye-right','cheek-left','cheek-right','mouth','face-contact-shadow','arm-left','arm-right','antenna','antenna-base','antenna-orb','bell-rim','clapper','torso','thruster','ear-left','ear-right','ear-left-cap','ear-right-cap','shell','faceLight','shell-glint','shell-left-light','ground-shadow','shell-back'])find(id);
    // Keep a stable occlusion order. Hands can pass behind a head, never pop
    // across its face when a turn crosses a sign threshold.
    if(!robot){this.body.prepend(find('arm-right'));this.body.insertBefore(find('arm-left'),find('antenna'));this.body.append(find('face'))}
    this.facePaths=['face-border','face-porcelain','brow-left','brow-right'].map(id=>{const node=find(id);return {node,tokens:tokensFor(node)}});
    this.shellPaths=(robot?['robot-head','head-highlight']:['bell-body','shell-left-light']).map(id=>{const node=find(id);return {node,tokens:tokensFor(node)}});
    this.backTokens=tokensFor(find('shell-back'));this.clip=find('shell-clip').querySelector('path');
    this.lights=[...this.svg.querySelectorAll('#'+this.P+'sculpted-light ellipse')].map(node=>({node,x:Number(node.getAttribute('cx')),y:Number(node.getAttribute('cy'))}));
  }
  step(target,dt,snap=false){
    for(const key of Object.keys(this.current)){
      if(snap){this.current[key]=target[key];this.velocity[key]=0}
      else [this.current[key],this.velocity[key]]=springStep(this.current[key],this.velocity[key],target[key],dt,key.startsWith('body')?8.5:11);
    }
  }
  apply(s,t,pose,blink,gazeOffset=0,ex={}){
    const n=this.nodes,h=this.current,by=radians(h.bodyYaw),bp=radians(h.bodyPitch);
    const yaw=radians(clamp(h.headYaw+h.bodyYaw*.48,-40,40)),pitch=radians(clamp(h.headPitch+h.bodyPitch*.3,this.robot?-28:-18,this.robot?28:18)),roll=radians(clamp(h.headRoll,this.robot?-18:-8,this.robot?18:8));
    const turn=Math.sin(by),nod=Math.sin(bp),headTurn=Math.sin(yaw);
    this.body.setAttribute('transform',rigMatrix(300,340,Math.cos(by*.3),-turn*.025,turn*nod*.06,Math.cos(bp*.35)));
    const shellMap=(x,y)=>this.robot?headPoint(x,y,yaw,pitch,roll):bellBend(x,y,yaw,pitch,roll);
    const backMap=(x,y)=>this.robot?headPoint(x,y,yaw,pitch,roll,-42):bellBend(x,y,yaw,pitch,roll,-42);
    const faceMap=(x,y)=>surfacePoint(x,y,yaw,pitch,roll);
    n.face.removeAttribute('transform');
    for(const {node,tokens} of this.shellPaths)node.setAttribute('d',warpPathTokens(tokens,shellMap));
    n['shell-back'].setAttribute('d',warpPathTokens(this.backTokens,backMap));
    this.clip.setAttribute('d',n[this.robot?'robot-head':'bell-body'].getAttribute('d'));
    for(const {node,tokens} of this.facePaths)node.setAttribute('d',warpPathTokens(tokens,faceMap));
    for(const [id,x,y] of [['eye-left',252,275],['eye-right',348,275],['cheek-left',222,304],['cheek-right',378,304],['mouth',300,315],['face-contact-shadow',302,337]]){
      let transform=matrixFor(faceMap,x,y);
      if(id.startsWith('eye-'))transform+=` translate(${x} ${y}) scale(1 ${blink?.08:1}) translate(${-x} ${-y})`;
      n[id].setAttribute('transform',transform);
    }
    const gaze=Number(s.gaze)+gazeOffset;
    n.eyes.setAttribute('transform',`translate(${rounded(gaze*Math.cos(yaw))} ${rounded(gaze*Math.sin(roll)+(ex.eyeUp||0))})`);
    if(ex.mouth!==undefined&&ex.mouth!==1)n.mouth.setAttribute('transform',n.mouth.getAttribute('transform')+` translate(300 304) scale(1 ${rounded(ex.mouth)}) translate(-300 -304)`);
    if(n['bell-rim'])n['bell-rim'].setAttribute('transform',rigMatrix(300,428,1,turn*.015,turn*nod*.08,1+nod*.72,0,-nod*3));
    if(n.clapper)n.clapper.setAttribute('transform',`translate(${rounded(turn*8+(ex.clap||0))} ${rounded(nod*5+Math.sin(t*2.2)*2-Math.abs(ex.clap||0)*.25)})`);
    if(n.torso)n.torso.setAttribute('transform',rigMatrix(300,394,Math.cos(by*.8),0,turn*nod*.07,1,turn*7,-nod*4));
    if(n.thruster)n.thruster.setAttribute('transform',rigMatrix(300,481,1,0,turn*.12,1+nod*.25,-turn*4,0));
    for(const [id,cx,cy,angle] of [['arm-left',this.robot?226:182,this.robot?375:322,pose[0]],['arm-right',this.robot?374:411,this.robot?375:320,pose[1]]]){
      const attach=this.robot?rigMatrix(cx,cy,1,0,0,1,turn*7,-nod*4):matrixFor(shellMap,cx,cy);
      n[id].setAttribute('transform',`${attach} rotate(${rounded(angle)} ${cx} ${cy})`);
      n[id].removeAttribute('opacity');
    }
    if(this.robot){
      n['ear-left'].setAttribute('transform',matrixFor(shellMap,150,275));n['ear-right'].setAttribute('transform',matrixFor(shellMap,448,275));
      n['ear-left-cap'].setAttribute('transform',rigMatrix(132,275,1+headTurn*.32,0,0,1));
      n['ear-right-cap'].setAttribute('transform',rigMatrix(455,275,1-headTurn*.32,0,0,1));
    }
    const wobble=clamp(-this.velocity.headYaw*.025+Math.sin(t*3)*.7,-4,4);
    n['antenna-base'].setAttribute('transform',`${matrixFor(shellMap,300,149)} rotate(${rounded(wobble)} 300 149)`);
    const orbY=this.robot?104:108,w=radians(wobble),orbX=300+Math.cos(w)-Math.sin(w)*(orbY-149),orbLocalY=149+Math.sin(w)+Math.cos(w)*(orbY-149),orbCenter=shellMap(orbX,orbLocalY);
    // A sphere keeps its round silhouette as the supporting stem turns.
    n['antenna-orb'].setAttribute('transform',`translate(${rounded(orbCenter[0]-301)} ${rounded(orbCenter[1]-orbY)})`);
    n['shell-glint'].setAttribute('transform',matrixFor(shellMap,this.robot?365:354,174));
    n.shell.setAttribute('cx',String(rounded(.32-headTurn*.09)));n.shell.setAttribute('cy',String(rounded(.12+Math.sin(pitch)*.05)));
    n.faceLight.setAttribute('cx',String(rounded(.44-headTurn*.13)));
    if(n['shell-left-light'])n['shell-left-light'].setAttribute('opacity',String(rounded(.5+headTurn*.14)));
    for(const light of this.lights)light.node.setAttribute('transform',matrixFor(shellMap,light.x,light.y));
    this.maps={shellMap,faceMap,yaw,pitch,roll,turn,nod};
    this.svg.setAttribute('data-head-yaw',h.headYaw.toFixed(2));this.svg.setAttribute('data-head-pitch',h.headPitch.toFixed(2));this.svg.setAttribute('data-body-yaw',h.bodyYaw.toFixed(2));
    const center=headPoint(300,270,yaw,pitch,roll),panel=faceMap(300,270);
    this.svg.setAttribute('data-panel-offset',Math.hypot(panel[0]-center[0],panel[1]-center[1]).toFixed(3));
  }
}
