// Editable Figma proposal. Run with figma-use + figma-generate-design.
const page = await figma.getNodeByIdAsync('77:915');
await figma.setCurrentPageAsync(page);
figma.skipInvisibleInstanceChildren = false;
const boardName = 'Cobalt / Screening / Setup, sources & feedback · Review';
if (page.children.some(n => n.name === boardName)) throw new Error('Proposal already exists; edit it by its returned IDs.');
const styles = new Map((await figma.getLocalTextStylesAsync()).map(s => [s.name, s]));
const relevant = [...styles.values()].filter(s => s.name.startsWith('Cobalt / Settings') || s.name.startsWith('Cobalt / Widgets'));
await Promise.all(relevant.map(s => figma.loadFontAsync(s.fontName)));
const navMaster = await figma.getNodeByIdAsync('92:2215');
const selectorMaster = await figma.getNodeByIdAsync('92:2209');
const buttonMaster = await figma.getNodeByIdAsync('92:2222');
const widgetMaster = await figma.getNodeByIdAsync('77:1214');
const statusMaster = await figma.getNodeByIdAsync('92:2228');
for (const node of [navMaster,selectorMaster,buttonMaster,widgetMaster,statusMaster]) {
  await Promise.all(node.findAllWithCriteria({types:['TEXT']}).flatMap(t => t.getStyledTextSegments(['fontName']).map(s => figma.loadFontAsync(s.fontName))));
}
const vars = new Map();
for (const id of ['2183','2184','2185','2186','2187','2188','2189','2190','2191','2192']) {
  const v = await figma.variables.getVariableByIdAsync('VariableID:92:' + id);
  vars.set(v.name.split('/')[1], v);
}
function paint(role) {
  const v = vars.get(role);
  return figma.variables.setBoundVariableForPaint({type:'SOLID', color:{r:0,g:0,b:0}}, 'color', v);
}
function box(parent, name, width=416, gap=14, role=null, direction='VERTICAL') {
  const f = figma.createAutoLayout(direction, {name, itemSpacing:gap});
  f.resize(width, 1); f.layoutSizingHorizontal='FIXED'; f.layoutSizingVertical='HUG';
  f.fills = role ? [paint(role)] : []; f.clipsContent=false;
  if(parent) parent.appendChild(f);
  return f;
}
function text(parent, value, style='Body', role='text', width=416) {
  const t=figma.createText(); t.name=value.split('\n')[0];
  t.textStyleId=styles.get('Cobalt / Settings / '+style).id;
  t.characters=value; t.fills=[paint(role)]; t.resize(width,1); t.textAutoResize='HEIGHT';
  parent.appendChild(t); return t;
}
function icon(parent, id, size=24, role='text') {
  const master=iconMasters.get(id); const n=master.createInstance(); parent.appendChild(n); n.resize(size,size);
  for(const child of n.findAll(()=>true)) {
    if(Array.isArray(child.fills)) child.fills=child.fills.map(p=>p.type==='SOLID'?paint(role):p);
    if(Array.isArray(child.strokes)) child.strokes=child.strokes.map(p=>p.type==='SOLID'?paint(role):p);
  }
  return n;
}
const iconMasters=new Map();
for(const id of ['26:2655','26:3509','26:2707','26:1547','26:2293','26:3471','26:2086','26:2267','47:1255','47:1319','25:951','26:2162']) {
  iconMasters.set(id,await figma.getNodeByIdAsync(id));
}
const right = Math.max(...page.children.map(n=>n.x+n.width));
const board=box(null,boardName,2480,24,'board'); board.paddingTop=40;board.paddingBottom=40;board.paddingLeft=40;board.paddingRight=40;
board.x=right+200;board.y=160;
text(board,'Screening · a clearer place for every action','Review title','boardInk',2400);
text(board,'Home Screen Widgets → Screening Card. Choose what appears, review one pick, or open its feedback sheet. Morning questions are optional.','Body','boardInk',2400);
const lanes=box(board,'Five views · two pages, two sheets, one optional subpage',2400,32,null,'HORIZONTAL');
const screens={}, targets={}, notes=[];
const localComponents=[];
function screen(key, label, title, subtitle) {
  const lane=box(lanes,label,448,12);text(lane,label,'Section','boardInk',448);
  const s=figma.createFrame();s.name=key;s.resize(448,997);s.cornerRadius=28;s.fills=[paint('background')];s.clipsContent=true;lane.appendChild(s);
  const status=statusMaster.clone();s.appendChild(status);status.x=16;status.y=18;
  const top=box(s,'Top bar',416,12,null,'HORIZONTAL');top.x=16;top.y=62;top.resize(416,48);top.layoutSizingVertical='FIXED';top.counterAxisAlignItems='CENTER';
  const back=box(top,'Back',48,0,null,'HORIZONTAL');back.resize(48,48);back.layoutSizingVertical='FIXED';back.primaryAxisAlignItems='CENTER';back.counterAxisAlignItems='CENTER';icon(back,'26:2655');
  const head=box(s,'Existing Cobalt expanded header',408,3);head.x=20;head.y=116;
  text(head,title,'Expanded header','text',408);text(head,subtitle,'Body','muted',408);
  const content=box(s,'Screen content',416,12);content.x=16;content.y=196;
  const handle=figma.createRectangle();handle.name='System gesture handle';handle.resize(108,4);handle.cornerRadius=2;handle.fills=[paint('text')];s.appendChild(handle);handle.x=170;handle.y=977;
  screens[key]=s;targets[key]={back,content,head,top};return {s,content,lane};
}
function button(parent,label,name=label) {
  const n=buttonMaster.createInstance();n.name=name;parent.appendChild(n);n.setProperties({'Label#92:11':label});
  for(const t of n.findAllWithCriteria({types:['TEXT']})) {t.textAutoResize='WIDTH_AND_HEIGHT';}
  return n;
}
function nav(parent,title,description) {
  const n=navMaster.createInstance();parent.appendChild(n);n.name=title;
  n.setProperties({'Title#92:9':title,'Description#92:10':description});return n;
}
function card(parent) {
  const n=widgetMaster.createInstance();parent.appendChild(n);n.name='Saved title / Your Name.';
  n.fills=[paint('surface')];
  for(const child of n.findAll(()=>true)) {
    if(child.type==='TEXT') child.fills=[paint(/2016/.test(child.characters)?'muted':'text')];
    if(child.name==='Tonight label') child.fills=[paint('surface')];
    if(child.name==='View details') child.fills=[paint('primary')];
    if(child.name==='Next pick') child.fills=[paint('selected')];
  }
  const detail=n.findAllWithCriteria({types:['INSTANCE']}).find(c=>c.name==='View details');
  if(detail) for(const t of detail.findAllWithCriteria({types:['TEXT']})) t.fills=[paint('onPrimary')];
  return n;
}
function preview(parent) {
  const f=box(parent,'Live widget preview',416,0);f.counterAxisAlignItems='CENTER';
  card(f);return f;
}
// Local components correspond to the shared settings header and plain sheet rows.
const sectionMaster=figma.createComponent();sectionMaster.name='Cobalt / Screening / Settings subsection header';
sectionMaster.layoutMode='VERTICAL';sectionMaster.itemSpacing=2;sectionMaster.resize(416,1);sectionMaster.layoutSizingVertical='HUG';sectionMaster.fills=[];
sectionMaster.paddingLeft=12;sectionMaster.paddingRight=12;sectionMaster.paddingTop=8;sectionMaster.paddingBottom=4;
const sectionLine=box(sectionMaster,'Heading and information',392,8,null,'HORIZONTAL');sectionLine.counterAxisAlignItems='CENTER';
const sectionTitle=text(sectionLine,'Customize','Section','primary',360);icon(sectionLine,'26:2162',24,'primary');
const sectionDescription=text(sectionMaster,'Choose what this card recommends.','Body','muted',392);
const sectionTitleKey=sectionMaster.addComponentProperty('Title','TEXT','Customize');sectionTitle.componentPropertyReferences={characters:sectionTitleKey};
const sectionDescriptionKey=sectionMaster.addComponentProperty('Description','TEXT','Choose what this card recommends.');sectionDescription.componentPropertyReferences={characters:sectionDescriptionKey};
localComponents.push(sectionMaster);
function section(parent,title,description) {const n=sectionMaster.createInstance();parent.appendChild(n);n.setProperties({[sectionTitleKey]:title,[sectionDescriptionKey]:description});return n;}
const sourceMaster=figma.createComponent();sourceMaster.name='Cobalt / Screening / Source option';sourceMaster.layoutMode='HORIZONTAL';sourceMaster.itemSpacing=12;sourceMaster.counterAxisAlignItems='CENTER';sourceMaster.resize(416,1);sourceMaster.layoutSizingVertical='HUG';sourceMaster.fills=[];sourceMaster.paddingTop=10;sourceMaster.paddingBottom=10;
const radioSlot=box(sourceMaster,'Radio',22,0,null,'HORIZONTAL');radioSlot.resize(22,22);radioSlot.layoutSizingVertical='FIXED';
const radio=figma.createEllipse();radio.name='Source ring';radio.resize(22,22);radio.fills=[];radio.strokes=[paint('outline')];radio.strokeWeight=2;radioSlot.appendChild(radio);
const dot=figma.createEllipse();dot.name='Selected indicator';dot.resize(12,12);dot.fills=[paint('primary')];radioSlot.appendChild(dot);dot.layoutPositioning='ABSOLUTE';dot.x=5;dot.y=5;dot.visible=false;
const sourceCopy=box(sourceMaster,'Source description',382,2);const sourceTitle=text(sourceCopy,'Movies','Row title','text',382);const sourceDescription=text(sourceCopy,'Saved films','Body','muted',382);
const sourceTitleKey=sourceMaster.addComponentProperty('Title','TEXT','Movies');sourceTitle.componentPropertyReferences={characters:sourceTitleKey};
const sourceDescriptionKey=sourceMaster.addComponentProperty('Description','TEXT','Saved films');sourceDescription.componentPropertyReferences={characters:sourceDescriptionKey};localComponents.push(sourceMaster);
const actionMaster=figma.createComponent();actionMaster.name='Cobalt / Screening / Feedback action';actionMaster.layoutMode='HORIZONTAL';actionMaster.itemSpacing=12;actionMaster.counterAxisAlignItems='CENTER';actionMaster.resize(416,1);actionMaster.layoutSizingVertical='HUG';actionMaster.fills=[];actionMaster.paddingTop=12;actionMaster.paddingBottom=12;
const actionIcon=icon(actionMaster,'47:1255',24,'primary');const actionCopy=box(actionMaster,'Action copy',380,3);const actionTitle=text(actionCopy,'Good pick','Row title','text',380);const actionDescription=text(actionCopy,'Show me more like this.','Body','muted',380);
const actionTitleKey=actionMaster.addComponentProperty('Title','TEXT','Good pick');actionTitle.componentPropertyReferences={characters:actionTitleKey};
const actionDescriptionKey=actionMaster.addComponentProperty('Description','TEXT','Show me more like this.');actionDescription.componentPropertyReferences={characters:actionDescriptionKey};localComponents.push(actionMaster);
const setup=screen('Screening Card / setup','01 · Widget setup','Screening Card','Your next watch. Or your next read.');
preview(setup.content);
section(setup.content,'Customize','Choose what this card recommends.');
const source=selectorMaster.createInstance();source.name='Recommend / opens source picker';setup.content.appendChild(source);
source.setProperties({'Title#92:6':'Recommend','Description#92:7':'Choose the titles this card can suggest','Value#92:8':'Everything'});
targets.source=source;

targets.review=nav(setup.content,'Review a pick','Give feedback on one saved title');
targets.morning=nav(setup.content,'Morning check-in','Off · an optional question about your picks');
button(setup.content,'Add to home screen');
text(setup.content,'These choices apply to this card only.','Body','muted');
notes.push('Setup stays under Home Screen Widgets. Media & Providers contains only search, metadata and sync.');

const pick=screen('Screening / review pick','02 · Review one recommendation','Your next watch','A pick from the titles you have saved.');
const pickCard=preview(pick.content);targets.pickCard=pickCard;
section(pick.content,'Your feedback','Help improve the next recommendation.');
targets.feedback=button(pick.content,'What do you think?');
targets.progress=nav(pick.content,'Update watch progress','Open this title’s existing details');

notes.push('One title, one feedback action. Small headings and information icons match Cobalt settings.');

function sheetScreen(key,label,title,subtitle,base) {
  const lane=box(lanes,label,448,12);text(lane,label,'Section','boardInk',448);
  const s=base.clone();s.name=key;lane.appendChild(s);screens[key]=s;
  const dim=figma.createRectangle();dim.name='Modal scrim';dim.resize(448,997);dim.fills=[{type:'SOLID',color:{r:0,g:0,b:0},opacity:.64}];s.appendChild(dim);dim.x=0;dim.y=0;
  const sheet=box(s,title,448,16,'surface');sheet.cornerRadius=28;sheet.paddingTop=12;sheet.paddingBottom=28;sheet.paddingLeft=16;sheet.paddingRight=16;sheet.x=0;sheet.y=410;
  const grip=figma.createRectangle();grip.name='Bottom sheet handle';grip.resize(32,4);grip.cornerRadius=2;grip.fills=[paint('outline')];sheet.appendChild(grip);grip.layoutAlign='CENTER';
  const header=box(sheet,'Sheet header',416,12,null,'HORIZONTAL');header.counterAxisAlignItems='CENTER';
  text(header,title,'Sheet header','text',368);const close=box(header,'Close',36,0,null,'HORIZONTAL');close.resize(36,36);close.layoutSizingVertical='FIXED';close.counterAxisAlignItems='CENTER';close.primaryAxisAlignItems='CENTER';icon(close,'26:2707',20);
  text(sheet,subtitle,'Body','muted');targets[key]={close,sheet};return {s,lane,sheet};
}
function plainAction(parent,title,description,iconId) {
  const row=actionMaster.createInstance();parent.appendChild(row);row.name=title;row.setProperties({[actionTitleKey]:title,[actionDescriptionKey]:description});
  const glyph=row.findAllWithCriteria({types:['INSTANCE']}).find(n=>n.name===actionIcon.name);glyph.swapComponent(iconMasters.get(iconId));
  return row;
}
const sources=sheetScreen('Screening / sources sheet','03 · Choose a source','Recommend','Applies to this card only.',setup.s);
const options=box(sources.sheet,'Source options',416,0);
const optionRows=[];
for(const [title,description,selected] of [['Everything','Movies, TV shows, anime and manga',true],['Movies','Saved films',false],['TV shows','Live-action series',false],['Anime','Anime series and films',false],['Manga','Your saved reading list',false]]) {
  const row=sourceMaster.createInstance();options.appendChild(row);row.name=title;row.setProperties({[sourceTitleKey]:title,[sourceDescriptionKey]:description});
  row.findOne(n=>n.name==='Selected indicator').visible=selected;
  row.findOne(n=>n.name==='Source ring').strokes=[paint(selected?'primary':'outline')];optionRows.push(row);
}

notes.push('Anime is distinct from TV shows. Manga is a reading category; chapter and volume counts never become episode counts.');

const feedback=sheetScreen('Screening / feedback sheet','04 · Feedback in context','What do you think?','About Your Name. · Anime film',pick.s);
const responses=box(feedback.sheet,'Three clear recommendation responses',416,0);
const good=plainAction(responses,'Good pick','Show me more picks like this.','47:1255');
const no=plainAction(responses,'Not interested','Hide this title from future picks.','47:1319');
const later=plainAction(responses,'Maybe later','Skip this title for 7 days.','26:3471');


notes.push('Feedback is a short bottom sheet. Watching or reading progress remains in the existing title details. Notification taps open this same sheet.');

const morning=screen('Screening / morning check-in','05 · Optional morning questions','Morning check-in','One gentle question, when you want it.');
const notification=box(morning.content,'Example notification',416,10,'surface');notification.cornerRadius=24;notification.paddingTop=16;notification.paddingBottom=16;notification.paddingLeft=16;notification.paddingRight=16;
const from=box(notification,'Notification source',384,8,null,'HORIZONTAL');icon(from,'26:1547',20,'primary');text(from,'Cobalt · Screening','Section','muted',356);
text(notification,'How was this pick?','Row title','text',384);text(notification,'Your Name. · Tap to give feedback or update progress.','Body','muted',384);
const toggle=nav(morning.content,'Morning questions','Off · enable when you want a check-in');
const times=box(morning.content,'Schedule · disabled until enabled',416,2);
const time=nav(times,'Morning time','09:00 · local time');time.opacity=.5;
const weekends=nav(times,'Include weekends','Off');weekends.opacity=.5;
text(morning.content,'At most once a day. Android may deliver a little later.','Body','muted');
text(morning.content,'Unanswered questions reduce how often we ask.','Body','muted');
targets.reset=nav(morning.content,'Reset recommendation learning','Your saved titles and progress are kept');
notes.push('Morning check-in is a subpage of Screening Card. Notifications start off; permissions and schedule appear here only.');

for(const key of ['Screening / sources sheet','Screening / feedback sheet']) {const sheet=targets[key].sheet;sheet.y=997-sheet.height;}
for(let i=0;i<lanes.children.length;i++) text(lanes.children[i],notes[i],'Body','boardInk',448);
const rules=box(board,'Flow and edge states',2400,12);text(rules,'The hierarchy','Sheet header','boardInk',2400);
text(rules,'Home Screen Widgets → Screening Card → Review a pick → Feedback sheet. Recommend opens the source sheet. Morning check-in opens its own subpage. Title details handle all watching or reading progress.','Body','boardInk',2400);
text(rules,'Empty source: “No saved manga yet” + “Open Media”. One eligible title: Next is disabled. Feedback saved: close the sheet, show a brief confirmation, then choose the next eligible pick. Manga: “Your next read”, “Update reading progress”, chapters / volumes only when known.','Body','boardInk',2400);
text(rules,'Proposal for review. The existing movie/show widget is tested; Anime/Manga selection and this revised screen flow require the implementation pass.','Body','boardInk',2400);
const components=box(board,'Editable shared components',2400,24,null,'HORIZONTAL');for(const n of localComponents) components.appendChild(n);
const all=[board,...board.findAll(()=>true)];
return {boardId:board.id,createdNodeIds:all.map(n=>n.id),screens:Object.entries(screens).map(([key,n])=>({id:n.id,name:key,width:n.width,height:n.height})),nodeTypes:all.reduce((counts,n)=>(counts[n.type]=(counts[n.type]||0)+1,counts),{}),fontFamilies:[...new Set(all.filter(n=>n.type==='TEXT').flatMap(n=>n.getStyledTextSegments(['fontName']).map(s=>s.fontName.family)))],bounds:{width:board.width,height:board.height},contentBounds:Object.entries(targets).filter(([key])=>['Screening Card / setup','Screening / review pick','Screening / morning check-in'].includes(key)).map(([key,v])=>({screen:key,y:v.content.y,height:v.content.height,bottom:v.content.y+v.content.height})),imageNodes:all.filter(n=>Array.isArray(n.fills)&&n.fills.some(p=>p.type==='IMAGE')).map(n=>({id:n.id,name:n.name,type:n.type,width:n.width,height:n.height})),usedComponents:[...new Set(all.filter(n=>n.type==='INSTANCE').map(n=>n.mainComponent.id))]};
