// Four editable Screening concepts. Run with figma-use + figma-generate-design.
const page = await figma.getNodeByIdAsync('77:915');
await figma.setCurrentPageAsync(page);
figma.skipInvisibleInstanceChildren = false;
const boardName = 'Cobalt / Screening / Four launcher redesign concepts';
if (page.children.some(n => n.name === boardName)) throw new Error('Concept board exists; edit its returned IDs.');
const styles = new Map((await figma.getLocalTextStylesAsync()).map(s => [s.name, s]));
const launcher = await figma.getNodeByIdAsync('77:1837');
const source = await figma.getNodeByIdAsync('77:1215');
await Promise.all([
  ...[...styles.values()].filter(s => s.name.startsWith('Cobalt / Widgets')).map(s => figma.loadFontAsync(s.fontName)),
  ...launcher.findAllWithCriteria({types:['TEXT']}).flatMap(t => t.getStyledTextSegments(['fontName']).map(s => figma.loadFontAsync(s.fontName)))
]);
const vars = new Map();
for (const suffix of ['2183','2184','2185','2186','2187','2188','2189','2190','2191','2192']) {
  const v = await figma.variables.getVariableByIdAsync('VariableID:92:' + suffix);
  vars.set(v.name.split('/')[1], v);
}
const icons = new Map();
for (const id of ['26:2767','26:3548','47:1255','26:3471']) icons.set(id, await figma.getNodeByIdAsync(id));
function paint(role, consumer, opacity=1) {
  const v = vars.get(role), c = v.resolveForConsumer(consumer || source).value;
  return figma.variables.setBoundVariableForPaint({type:'SOLID',color:{r:c.r,g:c.g,b:c.b},opacity},'color',v);
}
function frame(parent,name,width,gap=0,role=null,direction='VERTICAL') {
  const n = figma.createAutoLayout(direction); n.name=name;
  if(parent) parent.appendChild(n);
  n.resize(width,1); n.layoutSizingHorizontal='FIXED'; n.layoutSizingVertical='HUG';
  n.itemSpacing=gap; n.fills=role?[paint(role,n)]:[]; n.clipsContent=false;
  return n;
}
function text(parent,value,style='Metadata',role='text',width=304) {
  const t=figma.createText(); t.name=value;
  t.textStyleId=styles.get('Cobalt / Widgets / '+style).id; t.characters=value;
  parent.appendChild(t); t.resize(width,1); t.textAutoResize='HEIGHT'; t.fills=[paint(role,t)];
  return t;
}
function fixed(n,w,h) { n.resize(w,h); n.layoutSizingHorizontal='FIXED'; n.layoutSizingVertical='FIXED'; return n; }
function tintIcon(n,role='primary') {
  for(const v of n.findAllWithCriteria({types:['VECTOR']})) {
    if(v.isMask) continue;
    for(const field of ['fills','strokes']) if(Array.isArray(v[field])) {
      v[field]=v[field].map(p => p.type==='SOLID' && !(p.color.r>.95 && p.color.g>.95 && p.color.b>.95) ? paint(role,v,p.opacity===undefined?1:p.opacity) : p);
    }
  }
}
function icon(parent,id,size=20,role='primary') {
  const n=icons.get(id).createInstance(); parent.appendChild(n); n.resize(size,size); tintIcon(n,role); return n;
}
const right=Math.max(...page.children.map(n=>n.x+n.width));
const board=frame(page,boardName,1664,24,'board');
board.x=right+200; board.y=160; board.paddingTop=36;board.paddingBottom=32;board.paddingLeft=40;board.paddingRight=40;
text(board,'Screening · four new directions','Editorial / 27-34 Medium','boardInk',1584);
text(board,'A wider card, calmer hierarchy, and actions that earn their space. Four editable concepts in the same Android launcher.','Review / Note','boardInk',1584);
const lanes=frame(board,'Compare 01–04',1584,48,null,'HORIZONTAL');
lanes.counterAxisAlignItems='MIN';
const shelf=frame(page,'Screening concepts · editable main components',1664,24,'board');
shelf.x=board.x;shelf.y=1480;shelf.paddingLeft=40;shelf.paddingRight=40;shelf.paddingTop=24;shelf.paddingBottom=28;
text(shelf,'Main components · each launcher uses an instance','Review / Behavior','boardInk',1584);
const controls=frame(shelf,'Reusable controls',1584,16,null,'HORIZONTAL');
const iconControl=figma.createComponent();controls.appendChild(iconControl);
iconControl.name='Cobalt / Screening concepts / Compact icon action';iconControl.description='48 dp target. Change pick or open feedback. Reuses a canonical Cobalt icon.';
iconControl.layoutMode='HORIZONTAL';fixed(iconControl,48,48);iconControl.primaryAxisAlignItems='CENTER';iconControl.counterAxisAlignItems='CENTER';iconControl.cornerRadius=24;iconControl.fills=[paint('surface',iconControl)];
const controlGlyph=icon(iconControl,'26:3548',20,'text');
const iconSwap=iconControl.addComponentProperty('Icon','INSTANCE_SWAP','26:3548');
controlGlyph.componentPropertyReferences={mainComponent:iconSwap};
const inlineAction=figma.createComponent();controls.appendChild(inlineAction);
inlineAction.name='Cobalt / Screening concepts / Inline feedback action';inlineAction.description='Plain 48 dp feedback action. Label and canonical icon are instance properties.';
inlineAction.layoutMode='HORIZONTAL';fixed(inlineAction,144,48);inlineAction.itemSpacing=8;inlineAction.primaryAxisAlignItems='CENTER';inlineAction.counterAxisAlignItems='CENTER';inlineAction.fills=[];
const feedbackIcon=icon(inlineAction,'47:1255',18,'primary');
const feedbackSwap=inlineAction.addComponentProperty('Icon','INSTANCE_SWAP','47:1255');feedbackIcon.componentPropertyReferences={mainComponent:feedbackSwap};
const feedbackLabel=text(inlineAction,'Good pick','Action','primary',76);feedbackLabel.textAutoResize='WIDTH_AND_HEIGHT';
const feedbackText=inlineAction.addComponentProperty('Label','TEXT','Good pick');feedbackLabel.componentPropertyReferences={characters:feedbackText};
const mastersRow=frame(shelf,'Four widget main components',1584,48,null,'HORIZONTAL');
mastersRow.counterAxisAlignItems='MIN';
function master(name,height) {
  const n=figma.createComponent();mastersRow.appendChild(n);n.name='Cobalt / Screening concepts / '+name;
  n.layoutMode='VERTICAL';fixed(n,336,height);n.paddingLeft=16;n.paddingRight=16;n.paddingTop=16;n.paddingBottom=16;n.itemSpacing=8;n.fills=[paint('surface',n)];n.cornerRadius=28;n.clipsContent=true;
  return n;
}
function compact(parent,name,id,filled=true) {
  const n=iconControl.createInstance();parent.appendChild(n);n.name=name;n.setProperties({[iconSwap]:id});tintIcon(n,'text');if(!filled)n.fills=[];return n;
}
function header(parent,action=null) {
  const n=frame(parent,'Screening header',304,0,null,'HORIZONTAL');fixed(n,304,48);n.counterAxisAlignItems='CENTER';n.primaryAxisAlignItems='SPACE_BETWEEN';
  text(n,'SCREENING','Eyebrow','primary',240);
  if(action) compact(n,action.name,action.id,action.filled);
  return n;
}
function art(parent,name,w,h) {
  const n=figma.createFrame();parent.appendChild(n);n.name=name;n.resize(w,h);n.fills=JSON.parse(JSON.stringify(source.fills));n.cornerRadius=14;n.clipsContent=true;return n;
}
// 01 — artwork is the card; no separate navigation bar.
const cinematic=master('01 / Artwork',264);cinematic.description='The artwork/title region opens the saved title. The 48 dp circular change-pick control cycles locally and is hidden with one candidate. Fill launcher width; crop artwork as dimensions change.';
cinematic.fills=JSON.parse(JSON.stringify(source.fills));cinematic.primaryAxisAlignItems='SPACE_BETWEEN';
const shade=figma.createRectangle();cinematic.appendChild(shade);shade.name='Editable contrast gradient';shade.layoutPositioning='ABSOLUTE';shade.resize(336,264);shade.x=0;shade.y=0;shade.constraints={horizontal:'STRETCH',vertical:'STRETCH'};
const dark=vars.get('background').resolveForConsumer(cinematic).value;
shade.fills=[{type:'GRADIENT_LINEAR',gradientTransform:[[0,1,0],[-1,0,1]],gradientStops:[{position:0,color:{r:dark.r,g:dark.g,b:dark.b,a:.42}},{position:.38,color:{r:dark.r,g:dark.g,b:dark.b,a:.06}},{position:.72,color:{r:dark.r,g:dark.g,b:dark.b,a:.68}},{position:1,color:{r:dark.r,g:dark.g,b:dark.b,a:.97}}]}];
header(cinematic,{name:'Change pick · 48 dp',id:'26:3548',filled:true});
const cinCopy=frame(cinematic,'Tappable saved title',304,4);
text(cinCopy,'Your Name.','Editorial / 27-34 Medium','text');
text(cinCopy,'2016 · Anime film · 1h 46m','Metadata','text');
// 02 — cover and title, with a single feedback menu.
const cover=master('02 / Cover shelf',224);cover.description='The cover/title opens the saved title. More opens the existing feedback sheet. Metadata survives absent artwork; remove the cover and give text the full row width.';
header(cover,{name:'Pick feedback · 48 dp',id:'26:2767',filled:false});
const coverBody=frame(cover,'Tappable cover and title',304,16,null,'HORIZONTAL');fixed(coverBody,304,128);coverBody.counterAxisAlignItems='CENTER';
art(coverBody,'Your Name. / cropped cover artwork',100,128);
const coverCopy=frame(coverBody,'Title and metadata',188,6);
text(coverCopy,'Your Name.','Editorial / 25-30 Regular','text',188);
text(coverCopy,'Anime film','Metadata','primary',188);
text(coverCopy,'2016 · 1h 46m','Metadata','muted',188);
// 03 — complete even without artwork; feedback replaces navigation buttons.
const quiet=master('03 / Quiet pick',200);quiet.description='Text-first option works for movies, shows, anime and manga without a missing-artwork placeholder. Tap title for details; Good pick and Later save existing feedback outcomes.';
quiet.primaryAxisAlignItems='SPACE_BETWEEN';
const quietCopy=frame(quiet,'Tappable saved title',304,8);
text(quietCopy,'SCREENING','Eyebrow','primary');
const quietTitle=frame(quietCopy,'Title and metadata',304,4);
text(quietTitle,'Your Name.','Editorial / 27-34 Medium','text');
text(quietTitle,'Anime film · 2016 · 1h 46m','Metadata','muted');
const feedback=frame(quiet,'Feedback actions',304,2);
const rule=figma.createRectangle();feedback.appendChild(rule);rule.name='Subtle divider';rule.resize(304,1);rule.fills=[paint('outline',rule,.24)];
const actionRow=frame(feedback,'Good pick / Later',304,16,null,'HORIZONTAL');fixed(actionRow,304,48);
const good=inlineAction.createInstance();actionRow.appendChild(good);good.name='Good pick · 48 dp';
const later=inlineAction.createInstance();actionRow.appendChild(later);later.name='Maybe later · 48 dp';later.setProperties({[feedbackText]:'Later',[feedbackSwap]:'26:3471'});tintIcon(later,'primary');
// 04 — alternatives are visible, so there is no cycling button.
const shortlist=master('04 / Shortlist',280);shortlist.description='Three independently tappable saved-title rows. The first is the current pick; alternatives are immediately visible. Smaller sizes show two rows. Manga uses format metadata, never fabricated chapter progress.';
shortlist.paddingTop=12;shortlist.paddingBottom=12;shortlist.itemSpacing=4;
const listHeader=frame(shortlist,'Screening / count',304,0,null,'HORIZONTAL');fixed(listHeader,304,32);listHeader.counterAxisAlignItems='CENTER';listHeader.primaryAxisAlignItems='SPACE_BETWEEN';
text(listHeader,'SCREENING','Eyebrow','primary',240);text(listHeader,'3 picks','Caption','muted',40);
const listRow=figma.createComponent();controls.appendChild(listRow);listRow.name='Cobalt / Screening concepts / Shortlist title row';listRow.description='One 72 dp saved-title target with editable sequence, title and format metadata.';
listRow.layoutMode='HORIZONTAL';fixed(listRow,304,72);listRow.itemSpacing=12;listRow.paddingLeft=12;listRow.paddingRight=12;listRow.counterAxisAlignItems='CENTER';listRow.cornerRadius=16;listRow.fills=[];
const number=text(listRow,'01','Caption','primary',22);const numberProp=listRow.addComponentProperty('Number','TEXT','01');number.componentPropertyReferences={characters:numberProp};
const rowCopy=frame(listRow,'Saved title',246,2);
const rowTitle=text(rowCopy,'Your Name.','Editorial / 23-30 Regular','text',246);const titleProp=listRow.addComponentProperty('Title','TEXT','Your Name.');rowTitle.componentPropertyReferences={characters:titleProp};
const rowMeta=text(rowCopy,'Anime film · 1h 46m','Caption','muted',246);const metaProp=listRow.addComponentProperty('Metadata','TEXT','Anime film · 1h 46m');rowMeta.componentPropertyReferences={characters:metaProp};
for(const [i,title,meta] of [['01','Your Name.','Anime film · 1h 46m'],['02','Perfect Days','Movie · 2h 4m'],['03','Look Back','Manga · One-shot']]) {
  const n=listRow.createInstance();shortlist.appendChild(n);n.name=title+' / open saved title';n.setProperties({[numberProp]:i,[titleProp]:title,[metaProp]:meta});if(i==='01')n.fills=[paint('selected',n,.65)];
}
const concepts=[
  {id:'01',name:'Artwork',tagline:'Let the image do the work.',master:cinematic,behavior:'Tap the artwork or title to open it. A small change-pick control sits in the corner.',resize:'Uses the full host width; artwork crops instead of centering a fixed bitmap.'},
  {id:'02',name:'Cover shelf',tagline:'A saved title, quietly presented.',master:cover,behavior:'Tap the cover or title to open it. More opens the three-option feedback sheet.',resize:'Text stays readable when art is missing; the cover can collapse on smaller sizes.'},
  {id:'03',name:'Quiet pick',tagline:'Less decoration. Useful feedback.',master:quiet,behavior:'Tap the title to open it. Good pick and Later respond directly to this recommendation.',resize:'Works without artwork across all four media types. No oversized placeholder.'},
  {id:'04',name:'Shortlist',tagline:'See the alternatives immediately.',master:shortlist,behavior:'Tap any of three saved titles. The current pick is lightly highlighted; no Next button.',resize:'Shows two rows when shorter. Artwork and media-category icons are optional.'}
];
const phones=[];
for(const c of concepts) {
  const lane=frame(lanes,c.id+' / '+c.name,360,10);
  text(lane,c.id+'  '+c.name,'Editorial / 23-30 Regular','boardInk',360);
  text(lane,c.tagline,'Review / Note','boardInk',360);
  const phone=launcher.clone();lane.appendChild(phone);phone.name=c.name+' / Android launcher';
  const positions=phone.children.map(n=>({n,x:n.x,y:n.y}));phone.layoutMode='NONE';for(const p of positions){p.n.x=p.x;p.n.y=p.y;}
  const grid=phone.children.find(n=>n.name==='Widget grid / 4 × 3');grid.remove();
  const widget=c.master.createInstance();phone.appendChild(widget);widget.name=c.name+' / live concept instance';widget.x=12;widget.y=160;
  // Keep the Android dock at the bottom, identical across all four launchers.
  for(const [name,y] of [['Launcher apps',568],['Launcher search slot',665],['Android gesture area',717]]) phone.children.find(n=>n.name===name).y=y;
  text(lane,c.behavior,'Review / Behavior','boardInk',360);
  text(lane,c.resize,'Review / Note','boardInk',360);
  phones.push({concept:c.id,launcherId:phone.id,widgetInstanceId:widget.id,masterId:c.master.id,width:widget.width,height:widget.height,sideInset:12});
}
text(board,'Width rule for every option: fill the space Android gives the widget. Keep launcher-owned outer spacing; add no aspect-ratio padding inside.','Review / Note','boardInk',1584);
shelf.y=board.y+board.height+120;
figma.viewport.scrollAndZoomIntoView([board]);
const nodes=[board,...board.findAll(()=>true),shelf,...shelf.findAll(()=>true)];
const counts={};for(const n of nodes)counts[n.type]=(counts[n.type]||0)+1;
const images=nodes.filter(n=>Array.isArray(n.fills)&&n.fills.some(p=>p.type==='IMAGE')).map(n=>({id:n.id,name:n.name,type:n.type,width:n.width,height:n.height}));
const fonts=[...new Set(nodes.filter(n=>n.type==='TEXT').flatMap(t=>t.getStyledTextSegments(['fontName']).map(s=>s.fontName.family)))];
if(fonts.some(f=>!['Source Serif 4','Roboto'].includes(f)))throw new Error('Unexpected product font: '+fonts.join(', '));
return {boardId:board.id,shelfId:shelf.id,boardBounds:{x:board.x,y:board.y,width:board.width,height:board.height},concepts:phones,createdNodeIds:nodes.map(n=>n.id),mutatedNodeIds:[page.id],descendantCounts:counts,imageNodes:images,fontFamilies:fonts,canonicalIcons:[...icons.keys()],editable:true};
