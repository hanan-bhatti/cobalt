// Prepend `const extraIconAssets = ...` from assets.json for the six existing Android vectors.
const page = await figma.getNodeByIdAsync('369:76');
await figma.setCurrentPageAsync(page);
const created = [], mutated = [], removed = [];
const keep = n => { created.push(n.id); return n; };
const deep = n => { created.push(n.id, ...n.findAll(() => true).map(c => c.id)); return n; };
// remove() changes this subtree as one operation; keep the operation's root ID.
const erase = n => { removed.push(n.id); n.remove(); };
const roleIds = { surface:'VariableID:146:969', high:'VariableID:146:967', container:'VariableID:146:966', primary:'VariableID:146:934', primaryContainer:'VariableID:146:935', onPrimaryContainer:'VariableID:146:929', ink:'VariableID:146:953', muted:'VariableID:146:954', outline:'VariableID:146:957' };
const roles = {};
await Promise.all(Object.entries(roleIds).map(async ([key,id]) => { roles[key] = await figma.variables.getVariableByIdAsync(id); }));
const ids = ['487:20779','487:20668','487:20822','487:21210','487:21596','487:21923','436:4379','436:4327','436:4344','453:10243','436:4304','436:4305'];
const sources = await Promise.all(ids.map(id => figma.getNodeByIdAsync(id)));
if (sources.some(n => !n)) throw new Error('A required source frame is missing');
const [board,iconBoard,darkMain,lightMain,darkAnimation,lightAnimation,sliderSource,onSwitch,offSwitch,appearancePrototype,appearanceReview,appearanceSheets] = sources;
const [chipSet,buttonSet] = await Promise.all([
  figma.importComponentSetByKeyAsync('a3c83c534d78babd0613f4f14c72ac25ebae90cf'),
  figma.importComponentSetByKeyAsync('c94bd76a8dbf75d7885f3015ad8474d898fca25b')
]);
const variant = (set,parts) => {
  const n = set.children.find(n => parts.every(p => n.name.includes(p)));
  if (!n) throw new Error('Required native component variant is missing: '+parts.join(', '));
  return n;
};
const selectedChip = variant(chipSet,['Style=Outlined','Configuration=Label & leading icon','State=Enabled','Selected=True','Show trailing icon=False']);
const unselectedChip = variant(chipSet,['Style=Outlined','Configuration=Label only','State=Enabled','Selected=False','Show trailing icon=False']);
const textButton = variant(buttonSet,['Type=Round','Size=Small','State=Enabled']);
const fonts = new Map();
for (const root of [...sources,chipSet,buttonSet]) for (const t of root.findAllWithCriteria({types:['TEXT']})) {
  for (const segment of t.getStyledTextSegments(['fontName'])) fonts.set(segment.fontName.family+'/'+segment.fontName.style,segment.fontName);
}
const available = await figma.listAvailableFontsAsync();
for (const style of ['Regular','Medium']) {
  const font = {family:'Roboto',style};
  if (!available.some(f => f.fontName.family===font.family && f.fontName.style===font.style)) throw new Error('Product font is unavailable');
  fonts.set(font.family+'/'+font.style,font);
}
await Promise.all([...fonts.values()].map(font => figma.loadFontAsync(font)));
const paint = (role,opacity=1) => figma.variables.setBoundVariableForPaint({type:'SOLID',color:{r:.5,g:.5,b:.5},opacity},'color',roles[role]);
function col(parent,name,width,gap=0) {
  const n=keep(figma.createAutoLayout('VERTICAL')); n.name=name; n.resize(width,10);
  n.layoutSizingHorizontal='FIXED'; n.layoutSizingVertical='HUG'; n.fills=[]; n.itemSpacing=gap;
  parent.appendChild(n); return n;
}
function row(parent,name,width,gap=8) {
  const n=keep(figma.createAutoLayout('HORIZONTAL')); n.name=name; n.resize(width,10);
  n.layoutSizingHorizontal='FIXED'; n.layoutSizingVertical='HUG'; n.fills=[]; n.itemSpacing=gap; n.counterAxisAlignItems='CENTER';
  parent.appendChild(n); return n;
}
function text(parent,value,width,size=14,role='ink',weight='Regular',center=false) {
  const n=keep(figma.createText()); n.name=value; n.fontName={family:'Roboto',style:weight};
  n.characters=value; n.fontSize=size; n.lineHeight={unit:'PIXELS',value:size<=12?16:size<=14?20:24};
  n.textAutoResize='HEIGHT'; n.resize(width,10); n.fills=[paint(role)]; if(center)n.textAlignHorizontal='CENTER';
  parent.appendChild(n); n.layoutSizingVertical='HUG'; return n;
}
function line(parent,width,height=7,role='muted',opacity=.25) {
  const n=keep(figma.createRectangle()); n.name='Skeleton line'; n.resize(width,height); n.cornerRadius=height/2; n.fills=[paint(role,opacity)]; parent.appendChild(n); return n;
}
function spacer(parent,height) { const n=col(parent,'Spacing',1); n.resize(1,height); n.layoutSizingVertical='FIXED'; return n; }
const masters = {};
for(const n of iconBoard.findAllWithCriteria({types:['COMPONENT']})) masters[n.name.split(' / ').at(-1)] = n;
let extraRow = iconBoard.children.find(n => n.name==='Additional existing Android vectors');
if(!extraRow) extraRow=row(iconBoard,'Additional existing Android vectors',752,16);
for(const [kind,asset] of Object.entries(extraIconAssets)) {
  if(masters[kind]) continue;
  const n=keep(figma.createComponent()); n.name='Cobalt / Side dock / Source icon / '+kind; n.description='Existing Android drawable: '+asset.source; n.resize(24,24); n.fills=[];
  const glyph=deep(figma.createNodeFromSvg(asset.svg)); n.appendChild(glyph); extraRow.appendChild(n); masters[kind]=n;
}
function icon(parent,kind,role='primary',size=24) {
  const source=masters[kind]; if(!source)throw new Error('Missing source icon '+kind);
  const n=deep(source.createInstance()); n.name='Cobalt source icon · '+kind; n.rescale(size/24); parent.appendChild(n);
  for(const v of n.findAllWithCriteria({types:['VECTOR','BOOLEAN_OPERATION']})) {
    if(v.fills.length)v.fills=[paint(role)]; if(v.strokes.length)v.strokes=[paint(role)];
  }
  return n;
}
function connected(n,index,count) {
  n.cornerRadius=24; n.topLeftRadius=n.topRightRadius=index===0?24:4; n.bottomLeftRadius=n.bottomRightRadius=index===count-1?24:4;
}
function nativeSlider(parent,title,value,ratio,index,count,kind=null,description=null,endpoints=null) {
  const n=col(parent,title,408); n.fills=[paint('surface')]; n.paddingLeft=n.paddingRight=16; n.paddingTop=n.paddingBottom=10; connected(n,index,count);
  const heading=row(n,'Slider heading',376,8);
  if(kind)icon(heading,kind,'muted',22);
  const t=text(heading,title,kind?278:308,16,'ink','Medium'); t.layoutSizingHorizontal='FILL';
  const valueText=text(heading,value,60,13,'primary','Medium'); valueText.textAlignHorizontal='RIGHT';
  if(description)text(n,description,376,13,'muted');
  const slider=row(n,title+' · Native Material slider',376,6); slider.resize(376,48); slider.layoutSizingVertical='FIXED';
  const activeWidth=Math.max(1,Math.min(359,360*ratio));
  for(const [name,width,height] of [['Active track',activeWidth,16],['Handle',4,44],['Inactive track',360-activeWidth,16]]) {
    const source=sliderSource.children.find(c=>c.name===name); const part=deep(source.clone()); slider.appendChild(part); part.resize(width,height);
  }
  if(endpoints) { const r=row(n,'Slider range',376); r.primaryAxisAlignItems='SPACE_BETWEEN'; text(r,endpoints[0],100,12,'muted'); const end=text(r,endpoints[1],100,12,'muted');end.textAlignHorizontal='RIGHT'; }
  return n;
}
function nativeButton(parent,label,kind=null) {
  const n=deep(textButton.createInstance()); parent.appendChild(n);
  const props=n.componentProperties; const labelKey=Object.keys(props).find(k=>k.startsWith('Label text#')); const showKey=Object.keys(props).find(k=>k.startsWith('Show icon#')); const iconKey=Object.keys(props).find(k=>k.startsWith('Icon#'));
  n.setProperties({[labelKey]:label,[showKey]:!!kind,...(kind?{[iconKey]:masters[kind].id}:{})});
  n.resize(kind?112:104,48);n.layoutSizingHorizontal=n.layoutSizingVertical='FIXED';n.cornerRadius=24;
  for(const child of n.children) { if('resize'in child){child.resize(n.width,48); if('layoutSizingVertical'in child)child.layoutSizingVertical='FILL';} }
  for(const t of n.findAllWithCriteria({types:['TEXT']}))t.fills=[paint('primary')];
  if(kind) for(const v of n.findAllWithCriteria({types:['VECTOR','BOOLEAN_OPERATION']})) {if(v.fills.length)v.fills=[paint('primary')];if(v.strokes.length)v.strokes=[paint('primary')];}
  return n;
}
function dockStage(parent) {
  const stage=col(parent,'Transparent motion stage · 376 × 556',376); stage.paddingLeft=stage.paddingRight=stage.paddingTop=stage.paddingBottom=48;
  const dock=col(stage,'Actual dock geometry · 280 × 460',280); dock.resize(280,460); dock.layoutSizingVertical='FIXED'; dock.paddingLeft=dock.paddingRight=dock.paddingTop=dock.paddingBottom=14; dock.fills=[paint('high')]; dock.cornerRadius=24; dock.clipsContent=true; dock.strokes=[paint('outline',.65)];dock.strokeWeight=1;
  const head=row(dock,'Dock header',252);head.primaryAxisAlignItems='SPACE_BETWEEN';
  const badge=row(head,'Side dock title skeleton',131,6);badge.fills=[paint('primaryContainer',.7)];badge.cornerRadius=24;badge.paddingLeft=badge.paddingRight=10;badge.paddingTop=badge.paddingBottom=5;icon(badge,'sidedock','primary',15);line(badge,90,7,'onPrimaryContainer',.6);
  const close=row(head,'Close icon area',28);close.resize(28,28);close.layoutSizingVertical='FIXED';close.primaryAxisAlignItems='CENTER';icon(close,'close','muted',16);
  spacer(dock,8);line(dock,48,6);spacer(dock,4);
  const list=col(dock,'Recent capture skeletons',252,6);list.resize(252,265);list.layoutSizingVertical='FIXED';list.clipsContent=true;
  for(let i=0;i<6;i++){
    const item=row(list,'Capture skeleton',252,9);item.fills=[paint('container')];item.cornerRadius=13;item.paddingLeft=item.paddingRight=9;item.paddingTop=item.paddingBottom=8;
    const glyph=row(item,'Capture type',34);glyph.resize(34,34);glyph.layoutSizingVertical='FIXED';glyph.primaryAxisAlignItems='CENTER';glyph.fills=[paint('primary',.15)];glyph.cornerRadius=9;icon(glyph,['text','link','image','code','voice','movie'][i],'primary',16);
    const copy=col(item,'Capture text skeleton',191,5);line(copy,172,7);line(copy,124,5);
  }
  spacer(dock,10);
  const input=row(dock,'Capture input skeleton',252,8);input.resize(252,52);input.layoutSizingVertical='FIXED';input.paddingLeft=input.paddingRight=10;input.fills=[paint('container')];input.cornerRadius=14;icon(input,'text','primary',18);line(input,170,7);const send=row(input,'Send icon area',28);send.resize(28,28);send.layoutSizingVertical='FIXED';send.primaryAxisAlignItems='CENTER';send.fills=[paint('primaryContainer')];send.cornerRadius=14;icon(send,'send','onPrimaryContainer',14);
  spacer(dock,10);
  const actions=row(dock,'Quick capture actions',252);actions.primaryAxisAlignItems='SPACE_BETWEEN';
  for(const kind of ['ocr','text','link','movie','home']){
    const action=col(actions,'Quick action skeleton',42,3);action.paddingLeft=action.paddingRight=2;action.paddingTop=action.paddingBottom=2;action.counterAxisAlignItems='CENTER';
    const glyph=row(action,'Action icon area',38);glyph.resize(38,38);glyph.layoutSizingVertical='FIXED';glyph.primaryAxisAlignItems='CENTER';glyph.cornerRadius=13;glyph.fills=[paint(kind==='text'?'primaryContainer':'container')];icon(glyph,kind,kind==='text'?'primary':'muted',18);line(action,22,4);
  }
  stage.resize(376,556);stage.layoutSizingVertical='FIXED';stage.rescale(264/556);return stage;
}
function preview(parent,preset,description) {
  const n=col(parent,'Centered frameless dock preview',408,8);n.counterAxisAlignItems='CENTER';
  dockStage(n);text(n,preset,408,16,'ink','Medium',true);
  const descriptionSlot=col(n,'Preset description · Two-line space',360);descriptionSlot.resize(360,40);descriptionSlot.layoutSizingVertical='FIXED';
  text(descriptionSlot,description,360,14,'muted','Regular',true);
  const actions=row(n,'Preview actions',408);actions.primaryAxisAlignItems='CENTER';nativeButton(actions,'Replay','replay');nativeButton(actions,'Open dock');return n;
}
function presets(parent,preset) {
  const n=col(parent,'Motion preset · Round pills',408,8);const heading=row(n,'Preset heading',408);heading.paddingLeft=16;text(heading,'Motion preset',376,13,'muted','Medium');
  const choices=['Smooth','Fluid','Bouncy','Snappy','Instant'];
  for(const group of [choices.slice(0,3),choices.slice(3)]){
    const r=row(n,'Preset pills',408,8);
    for(const label of group){
      const selected=label===preset;const chip=deep((selected?selectedChip:unselectedChip).createInstance());r.appendChild(chip);chip.name='Motion preset · '+label;
      const props=chip.componentProperties; const key=Object.keys(props).find(k=>k.startsWith('Label text#'));const iconKey=Object.keys(props).find(k=>k.startsWith('Leading icon#'));
      chip.setProperties({[key]:label,...(selected?{[iconKey]:masters.check.id}:{})});chip.resize((408-16)/3,48);chip.layoutSizingHorizontal=chip.layoutSizingVertical='FIXED';chip.cornerRadius=24;chip.fills=[paint(selected?'primaryContainer':'surface')];chip.strokes=[];
      for(const c of chip.children){if(c.type==='FRAME'){c.resize(chip.width,48);c.layoutSizingVertical='FILL';c.cornerRadius=24;}}
      for(const t of chip.findAllWithCriteria({types:['TEXT']}))t.fills=[paint(selected?'onPrimaryContainer':'ink')];
      for(const v of chip.findAllWithCriteria({types:['VECTOR','BOOLEAN_OPERATION']})){if(v.fills.length)v.fills=[paint('onPrimaryContainer')];if(v.strokes.length)v.strokes=[paint('onPrimaryContainer')];}
    }
  }
  return n;
}
// Main screen: preserve the one-tap edge choice and replace the four navigation rows with inline sliders.
for(const child of [...darkMain.children])if(child.name==='Dock preview')erase(child);
const handle=darkMain.children.find(n=>n.name==='Handle');const controls=handle.children.find(n=>n.name==='Handle controls');
for(const child of [...controls.children])if(child.name!=='Screen edge · Inline choice')erase(child);
nativeSlider(controls,'Position','40%',.4,1,5,'position',null,['Top','Bottom']);
nativeSlider(controls,'Height','100 dp',(100-50)/150,2,5,'height',null,['Short','Tall']);
nativeSlider(controls,'Width','6 dp',(6-5)/25,3,5,'width',null,['Thin','Wide']);
nativeSlider(controls,'Opacity','70%',(.7-.1)/.9,4,5,'eye',null,['Faint','Solid']);
mutated.push(darkMain.id,controls.id);
const oldLightAccess=lightMain.children.find(n=>n.name==='Access & motion').children.find(n=>n.name==='Access controls');
const offRow=deep(oldLightAccess.children.find(n=>n.name==='Use Side dock').clone());
for(const child of [...lightMain.children])erase(child);
lightMain.appendChild(offRow);connected(offRow,0,1);lightMain.layoutSizingVertical='HUG';mutated.push(lightMain.id);
// Animation: a transparent, centered stage, all text/actions underneath, then rounded native pills and sliders.
for(const [body,preset,description,strength,damping] of [[darkAnimation,'Smooth','A steady glide with no rebound.',430,1],[lightAnimation,'Custom','Uses your spring strength and damping.',510,.79]]){
  for(const child of [...body.children])erase(child);
  preview(body,preset,description);presets(body,preset);
  const springs=col(body,'Spring controls',408,4);const heading=row(springs,'Spring controls heading',408);heading.paddingLeft=16;text(heading,'Spring controls',376,13,'muted','Medium');
  nativeSlider(springs,'Spring strength',String(strength),(strength-120)/780,0,2,null,strength<500?'Moderate spring strength.':'Firm spring, quicker motion.');
  nativeSlider(springs,'Bounce damping',damping.toFixed(2),(damping-.45)/.55,1,2,null,damping===1?'No spring overshoot.':'A small rebound before settling.');
  body.layoutSizingVertical='HUG';mutated.push(body.id);
}
// Add a review state for the missing Android permission, without an enable switch or handle controls.
const views=board.children.find(n=>n.name==='Side dock review views');
let permissionWrap=views.children.find(n=>n.name==='Side dock · Permission required');
if(permissionWrap)erase(permissionWrap);
permissionWrap=col(views,'Side dock · Permission required',448,12);text(permissionWrap,'Side dock · Permission required',448,14,'muted','Medium');
const screen=deep((await figma.getNodeByIdAsync('487:20785')).clone());screen.name='Side dock · Permission required';permissionWrap.appendChild(screen);
const permissionBody=screen.findAllWithCriteria({types:['FRAME']}).find(n=>n.name==='Settings content');for(const child of [...permissionBody.children])erase(child);
const permission=row(permissionBody,'Allow display over other apps',408,12);permission.paddingLeft=permission.paddingRight=16;permission.paddingTop=permission.paddingBottom=14;permission.fills=[paint('surface')];connected(permission,0,1);
const badge=col(permission,'Permission icon badge',40);badge.resize(40,40);badge.layoutSizingVertical='FIXED';badge.paddingLeft=badge.paddingRight=badge.paddingTop=badge.paddingBottom=8;badge.cornerRadius=20;badge.fills=[paint('primaryContainer')];icon(badge,'lock','onPrimaryContainer');
const copy=col(permission,'Permission copy',284,2);copy.layoutSizingHorizontal='FILL';text(copy,'Allow display over other apps',284,16,'ink','Medium');text(copy,'Android permission needed to show the handle.',284,14,'muted');icon(permission,'chevron','muted',20);
permissionBody.layoutSizingVertical='HUG';views.resize(5*448+4*32,views.height);board.resize(views.width+48,board.height);mutated.push(views.id,board.id);
// Appearance v2 pages and their sheet backgrounds now begin directly with settings.
const appearanceChanges=[];
for(const root of [appearancePrototype,appearanceReview,appearanceSheets]){
  const bodies=root.findAllWithCriteria({types:['FRAME']}).filter(n=>n.name==='Appearance content');
  for(const body of bodies){
    const targets=body.children.filter(n=>n.name==='Home & side dock preview'||n.name==='Immediate apply guidance');
    if(!targets.length)continue;
    for(const n of targets)erase(n);
    body.layoutSizingVertical='HUG';mutated.push(body.id);appearanceChanges.push({bodyId:body.id,screenId:body.parent.parent.id});
  }
}
return {createdNodeIds:[...new Set(created)],mutatedNodeIds:[...new Set(mutated)],removedNodeIds:[...new Set(removed)],screens:{main:'487:20785',off:'487:21173',animation:'487:21559',custom:'487:21886',permission:screen.id},appearanceChanges,previewGeometry:{card:[280,460],stage:[376,556],scale:264/556},editable:true};
