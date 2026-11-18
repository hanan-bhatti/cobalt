// Authoring snapshot from this Figma batch. See README.md before reuse.
// Prefixed at execution with CONFIG, SCREENS, STATE and COPY from the source inventory.
const page = await figma.getNodeByIdAsync('369:76');
await figma.setCurrentPageAsync(page);
const created = new Set(), mutated = new Set(), removed = new Set(), styleWrites = [], links = [];
const keep = n => { created.add(n.id); return n; };
const deep = n => { created.add(n.id); for (const c of n.findAll(() => true)) created.add(c.id); return n; };
const drop = n => { removed.add(n.id); if ('findAll' in n) for (const c of n.findAll(() => true)) removed.add(c.id); n.remove(); };
const board = await figma.getNodeByIdAsync(STATE.boardId);
const parentIds = [...new Set(SCREENS.map(s => s.parent).filter(Boolean))];
const parentFrames = Object.fromEntries(await Promise.all(parentIds.filter(k => STATE.screens[k]).map(async k => [k, await figma.getNodeByIdAsync(STATE.screens[k].id)])));
const roleVars = Object.fromEntries(await Promise.all(Object.entries(STATE.roleIds).map(async ([k,id]) => [k,await figma.variables.getVariableByIdAsync(id)])));
const allVars = await figma.variables.getLocalVariablesAsync();
const space = n => allVars.find(v => v.id.startsWith('VariableID:148:') && v.name === 'space/'+n);
const styleRoles = ['headline-medium','headline-small','title-large','title-medium','body-large','body-medium','body-small','label-large'];
const styles = Object.fromEntries(await Promise.all(styleRoles.map(async k => [k,await figma.getStyleByIdAsync(STATE.styleMap[k].id)])));
const [header, gesture, switchOn, switchOff, sliderMaster] = await Promise.all(['447:7688','436:5004','436:4327','436:4344','436:4379'].map(id => figma.getNodeByIdAsync(id)));
const allBlocks = SCREENS.flatMap(s => [...(s.blocks || []),...(s.footer || [])]);
const nativeKeys = {button:'bc43537f1f7aec7e0c159679f9a721e7aab9f100',textButton:'c94bd76a8dbf75d7885f3015ad8474d898fca25b',radio:'aa0134adaa6451053bf3b6e00a2adcb99f92ba1e',checkbox:'b0c54da23fcaf1f2d07092703407af5c3c656e26',field:'a54980c1c99c62912ab39e9e30fe5c267c98edb4',sheet:'a3ecc23623bce0c4cf795c2c2efb64323cdbce44',dialog:'8171ce19b4b8b7a873e91c719b7fb0fdc8a23794',time:'0ba85df1bc8fb6d58c9a573af638933019323c4d',tabs:'c4816e9ba31977046bf2fdeb5c45bfb7ea217ae5',chip:'a3c83c534d78babd0613f4f14c72ac25ebae90cf',progress:'2f02f075b410db543e8e9770cb399c14c583110e'};
const needs = new Set(['button','textButton']);
for (const b of allBlocks) {
  if (b.type === 'options') needs.add('radio');
  if (b.type === 'consent') needs.add('checkbox');
  if (b.type === 'field') needs.add('field');
  if (b.type === 'time') needs.add('time');
  if (b.type === 'tabs') needs.add('tabs');
  if (b.type === 'chips') needs.add('chip');
  if (b.type === 'progress') needs.add('progress');
}
if (SCREENS.some(s => s.kind === 'sheet')) needs.add('sheet');
if (SCREENS.some(s => s.kind === 'dialog')) needs.add('dialog');
const native = Object.fromEntries(await Promise.all([...needs].map(async k => [k,await figma.importComponentSetByKeyAsync(nativeKeys[k])])));
const widgetKeys = {folio:'3ec156b7638907a30302271e40535f0516ff6417',screening:'f289495d025a113e826736b525a1ece8b07ab297'};
const widgetTypes = [...new Set(allBlocks.flatMap(b=>b.type==='widget'?[b.widget]:b.type==='widgetGrid'?b.items.map(i=>i.widget):[]))];
const widgets = Object.fromEntries(await Promise.all(widgetTypes.map(async k => [k,await figma.importComponentByKeyAsync(widgetKeys[k])])));
const icons = Object.fromEntries(await Promise.all(Object.entries(STATE.iconMap).map(async ([k,id]) => [k,await figma.getNodeByIdAsync(id)])));
await figma.listAvailableFontsAsync();
const fontNames = new Map();
for (const s of Object.values(styles)) fontNames.set(JSON.stringify(s.fontName),s.fontName);
for (const node of [header,gesture,...Object.values(parentFrames),...Object.values(native).map(s => s.defaultVariant),...Object.values(widgets)]) {
  for (const t of node.findAllWithCriteria({types:['TEXT']})) for (const seg of t.getStyledTextSegments(['fontName'])) fontNames.set(JSON.stringify(seg.fontName),seg.fontName);
}
await Promise.all([...fontNames.values()].map(f => figma.loadFontAsync(f)));
let currentFrame = null;
function paint(role='ink',opacity=1) { const v=roleVars[role] || roleVars.ink,c=v.resolveForConsumer(currentFrame || board).value;return figma.variables.setBoundVariableForPaint({type:'SOLID',color:{r:c.r,g:c.g,b:c.b},opacity},'color',v); }
function readable(role,background='bg',minimum=4.5){const sample=k=>(roleVars[k]||roleVars.ink).resolveForConsumer(currentFrame||board).value,lum=c=>[c.r,c.g,c.b].map(v=>v<=.04045?v/12.92:((v+.055)/1.055)**2.4).reduce((s,v,i)=>s+v*[.2126,.7152,.0722][i],0),bg=lum(sample(background)),ratio=k=>{const fg=lum(sample(k));return(Math.max(bg,fg)+.05)/(Math.min(bg,fg)+.05);};return ratio(role)>=minimum?role:['onPrimaryContainer','onSelected','onPrimary','ink'].find(k=>ratio(k)>=minimum)||'ink';}
function gap(n,key,value) { const v=space(value); if(v) n.setBoundVariable(key,v); else n[key]=value; }
function col(name,w,g=0,fill=null,pad=0) {
  const n=keep(figma.createAutoLayout('VERTICAL')); n.name=name;n.resize(w,10);n.layoutSizingHorizontal='FIXED';n.layoutSizingVertical='HUG';gap(n,'itemSpacing',g);n.fills=fill?[paint(fill)]:[];
  for(const key of ['paddingTop','paddingBottom','paddingLeft','paddingRight']) gap(n,key,pad); return n;
}
function row(name,w,g=12) {const n=keep(figma.createAutoLayout('HORIZONTAL'));n.name=name;n.resize(w,10);n.layoutSizingHorizontal='FIXED';n.layoutSizingVertical='HUG';gap(n,'itemSpacing',g);n.counterAxisAlignItems='CENTER';n.fills=[];return n;}
function txt(par,value,w,role='body-large',color='ink') {
  const s=styles[role],t=keep(figma.createText());t.name=String(value).slice(0,100);t.fontName=s.fontName;t.fontSize=s.fontSize;t.lineHeight=s.lineHeight;t.characters=String(value);t.textAutoResize='HEIGHT';t.resize(w,10);t.fills=[paint(readable(color))];par.appendChild(t);t.layoutSizingVertical='HUG';t.layoutSizingHorizontal='FILL';
  styleWrites.push(t.setTextStyleIdAsync(s.id));return t;
}
function themeInk(node,fg='ink',background='bg',minimum=4.5) {
  fg=readable(fg,background,minimum);
  for(const n of [node,...node.findAll(()=>true)]) {
    if(n.type==='TEXT') n.fills=[paint(fg)];
    else if(n.type==='VECTOR'||n.type==='BOOLEAN_OPERATION'||n.type==='ELLIPSE') {
      if('fills'in n&&Array.isArray(n.fills)&&n.fills.some(p=>p.type==='SOLID')) n.fills=[paint(fg)];
      if('strokes'in n&&Array.isArray(n.strokes)&&n.strokes.length) n.strokes=[paint(fg)];
    }
  }
}
function icon(par,name,color='primary',size=24,badge=false,boxRole='primaryContainer') {
  const source=icons[name];if(!source)throw new Error('Missing source icon: '+name);
  let dest=par;
  if(badge){dest=col(name+' · Tonal icon',40,0,boxRole,8);dest.resize(40,40);dest.layoutSizingVertical='FIXED';dest.cornerRadius=20;par.appendChild(dest);color='onPrimaryContainer';}
  const n=deep(source.createInstance());n.name='Iconsax · '+name;n.rescale(size/source.width);dest.appendChild(n);themeInk(n,color,badge?boxRole:'bg',3);return n;
}
function register(n,target) {if(target)links.push({id:n.id,target});return n;}
function prop(inst,start) {return Object.keys(inst.componentProperties).find(k=>k.startsWith(start));}
function control(type,par,w,props={}) {
  const n=keep(native[type].defaultVariant.createInstance());par.appendChild(n);n.setProperties(props);if(w)n.resize(w,n.height);deep(n);return n;
}
function button(par,label,target,kind='tonal',w=par.width,color=null) {
  const type=kind==='text'?'textButton':'button';const n=control(type,par,w,{Type:'Round',Size:'Small',State:'Enabled'});const props={[prop(n,'Label text#')]:label,[prop(n,'Show icon#')]:false};n.setProperties(props);n.name=label;n.resize(w,48);n.layoutSizingVertical='FIXED';n.cornerRadius=24;
  for(const f of n.findAll(x=>x.type==='FRAME'&&/content|container|state-layer/i.test(x.name))){f.resize(w,48);f.layoutSizingHorizontal=f.layoutSizingVertical='FILL';f.primaryAxisAlignItems=f.counterAxisAlignItems='CENTER';f.cornerRadius=24;if(kind!=='text'&&f.name!=='State-layer')f.fills=[paint(color==='error'?'error-container':kind==='primary'?'primary':'selected')];}
  themeInk(n,color==='error'?(kind==='text'?'error':'on-error-container'):kind==='primary'?'onPrimary':kind==='text'?'primary':'onSelected',kind==='text'?'bg':color==='error'?'error-container':kind==='primary'?'primary':'selected');deep(n);return register(n,target);
}
function setting(par,b,w,index=0,count=1) {
  const n=row(b.title,w,12);n.fills=[paint('surface')];n.paddingLeft=n.paddingRight=16;n.paddingTop=n.paddingBottom=12;n.minHeight=64;n.topLeftRadius=n.topRightRadius=index===0?24:4;n.bottomLeftRadius=n.bottomRightRadius=index===count-1?24:4;par.appendChild(n);
  if(b.icon)icon(n,b.icon,b.destructive?'error':b.iconColor||'primary',24,true,b.color||'primaryContainer');
  const trail=b.on!==undefined?64:b.value?Math.min(128,Math.max(64,b.value.length*8))+32:32;
  const cw=w-32-(b.icon?52:0)-trail;const copy=col('Setting text',cw,2);n.appendChild(copy);copy.layoutSizingHorizontal='FILL';txt(copy,b.title,cw,'title-large',b.destructive?'error':'ink');if(b.sub)txt(copy,b.sub,cw,'body-medium','muted');
  if(b.on!==undefined){const sw=deep((b.on?switchOn:switchOff).createInstance());n.appendChild(sw);register(sw,b.target);}
  else {if(b.value){const v=col('Current value',Math.max(64,trail-32),0);n.appendChild(v);txt(v,b.value,v.width,'label-large',b.destructive?'error':'primary');}if(b.target)icon(n,b.external?'external':'chevron','muted',20);}
  if(b.disabled)n.opacity=.55;return register(n,b.target);
}
function divider(par,w){const n=keep(figma.createRectangle());n.name='Section divider';n.resize(w,1);n.fills=[paint('outline',.4)];par.appendChild(n);return n;}
function field(par,b,w) {
  const box=col(b.label+' · Field and hint',w,8);box.paddingTop=8;box.clipsContent=false;par.appendChild(box);
  const n=control('field',box,w,{Style:'Outlined',State:b.error?'Error':'Enabled','Text configurations':b.value?'Input text':'Placeholder text','Leading icon':'False','Trailing icon':b.secret||b.trailing?'True':'False'});
  const props={[prop(n,'Label text#')]:b.label,[prop(n,'Input text#')]:b.value||'',[prop(n,'Placeholder text#')]:b.placeholder||'',[prop(n,'Supporting text#')]:b.error||b.help||'',[prop(n,'Show supporting text#')]:false};n.setProperties(props);n.name=b.label+' · Material text field';
  const h=b.multiline?(b.height||144):56;n.resize(w,h);n.layoutSizingVertical='FIXED';n.layoutSizingHorizontal='FILL';
  for(const f of n.findAllWithCriteria({types:['FRAME']})){
    if(f.name==='Text field'){f.layoutSizingVertical='FILL';f.cornerRadius=12;f.fills=[paint('bg')];if(f.strokes.length)f.strokes=[paint(b.error?'error':'outline')];}
    if(f.name==='State-layer'){f.layoutSizingHorizontal=f.layoutSizingVertical='FILL';f.paddingTop=f.paddingBottom=4;f.paddingLeft=16;f.paddingRight=b.multiline?16:0;}
    if(f.name==='Content'){f.layoutSizingVertical='FILL';f.primaryAxisAlignItems=b.multiline?'MIN':'CENTER';f.paddingTop=b.multiline?8:0;}
    if(f.name==='Label text container')f.fills=[paint('bg')];
  }
  for(const t of n.findAllWithCriteria({types:['TEXT']})) {t.fills=[paint(t.name==='Input text'?'ink':b.error&&/support/i.test(t.name)?'error':'muted')];if(b.multiline&&t.name==='Input text'){t.textAutoResize='HEIGHT';t.resize(w-32,10);t.layoutSizingVertical='HUG';t.textTruncation='DISABLED';}}
  if(b.secret||b.trailing){const slot=n.findAllWithCriteria({types:['INSTANCE']}).find(x=>/trailing/i.test(x.name));if(slot){slot.swapComponent(icons[b.trailing||'eyeOff']);slot.name=b.trailing?'Remove '+b.label:'Show API key';register(slot,b.trailingTarget);}}
  deep(n);if(b.error||b.help)txt(box,b.error||b.help,w,'body-medium',b.error?'error':'muted');return box;
}
function options(par,b,w){const group=col(b.title||'Choices',w,4);par.appendChild(group);for(const [i,o]of b.options.entries()){const n=row(o.title,w,12);n.fills=[paint(o.selected?'selected':'surface')];n.paddingLeft=n.paddingRight=16;n.paddingTop=n.paddingBottom=12;n.cornerRadius=16;n.minHeight=64;group.appendChild(n);if(o.icon)icon(n,o.icon,o.color||'primary',24);const cw=w-80-(o.icon?36:0),c=col('Choice description',cw,2);n.appendChild(c);c.layoutSizingHorizontal='FILL';txt(c,o.title,cw,'title-large',o.selected?'onSelected':'ink');if(o.sub)txt(c,o.sub,cw,'body-medium',o.selected?'onSelected':'muted');const r=control('radio',n,24,{Selected:o.selected?'True':'False',State:'Enabled'});themeInk(r,o.selected?'primary':'muted');register(n,o.target||b.target);}return group;}
function chips(par,b,w){const flow=row(b.title||'Choices',w,8);flow.layoutWrap='WRAP';flow.counterAxisSpacing=8;par.appendChild(flow);for(const o of b.options){const item=typeof o==='string'?{title:o}:o;const n=control('chip',flow,null,{Style:'Outlined',Configuration:'Label only',State:'Enabled',Selected:item.selected?'True':'False','Show trailing icon':'False'});n.setProperties({[prop(n,'Label text#')]:item.title});n.name=item.title;const cw=Math.max(64,Math.min(w,item.title.length*7+32+(item.selected?24:0)));n.resize(cw,48);n.layoutSizingHorizontal=n.layoutSizingVertical='FIXED';n.cornerRadius=24;n.fills=[paint(item.selected?'selected':'high')];n.strokes=[];for(const c of n.findAll(x=>x.type==='FRAME'&&/container|content|state-layer/i.test(x.name))){c.resize(cw,48);c.layoutSizingHorizontal=c.layoutSizingVertical='FILL';c.cornerRadius=24;c.strokes=[];c.fills=[];}themeInk(n,item.selected?'onSelected':'ink');deep(n);register(n,item.target||b.target);}return flow;}
function stats(par,b,w){const table=col(b.title||'Recorded figures',w,0);par.appendChild(table);for(const [label,value]of b.rows){const n=row(label,w,12);n.minHeight=40;table.appendChild(n);const vw=b.valueWidth||100,t=txt(n,label,w-vw-12,'body-large','muted');t.layoutSizingHorizontal='FILL';const v=txt(n,value,vw,'title-large');v.layoutSizingHorizontal='FIXED';v.textAlignHorizontal='RIGHT';}return table;}
function chart(par,b,w){const wrap=col(b.title,w,12);par.appendChild(wrap);txt(wrap,b.title,w,'title-large');const bars=row('Daily values',w,8);bars.counterAxisAlignItems='MAX';wrap.appendChild(bars);const max=Math.max(...b.values);for(let i=0;i<b.values.length;i++){const c=col(b.labels[i],(w-48)/7,8);c.counterAxisAlignItems='CENTER';bars.appendChild(c);const r=keep(figma.createRectangle());r.name=b.values[i]+' tokens';r.resize(18,Math.max(6,118*b.values[i]/max));r.cornerRadius=9;r.fills=[paint('primary',i===b.selected?1:.55)];c.appendChild(r);const t=txt(c,b.labels[i],c.width,'body-small','muted');t.textAlignHorizontal='CENTER';}if(b.note)txt(wrap,b.note,w,'body-medium','muted');return wrap;}
function slider(par,b,w){const box=col(b.title,w,8);par.appendChild(box);const h=row('Slider label',w,8);box.appendChild(h);const t=txt(h,b.title,w-72,'title-large');t.layoutSizingHorizontal='FILL';const v=txt(h,b.value||'Not selected',96,'label-large','primary');v.layoutSizingHorizontal='FIXED';const n=deep(sliderMaster.createInstance());box.appendChild(n);n.resize(w,48);n.layoutSizingVertical='FIXED';if(b.ends){const e=row('Scale labels',w,12);box.appendChild(e);const a=txt(e,b.ends[0],w/2-6,'body-small','muted'),z=txt(e,b.ends[1],w/2-6,'body-small','muted');z.textAlignHorizontal='RIGHT';}if(b.sub)txt(box,b.sub,w,'body-medium','muted');return box;}
function widget(par,b,w){const box=col('Actual widget proportions',w,12);box.counterAxisAlignItems='CENTER';par.appendChild(box);const n=deep(widgets[b.widget].createInstance());n.name=b.widget==='folio'?'Pocket Folio · Approved editable widget':'Screening Card · Approved Artwork A';box.appendChild(n);n.rescale(Math.min(336,w)/n.width);if(b.state==='empty'){if(b.widget==='folio'){for(const c of n.findAllWithCriteria({types:['FRAME']}))if(c.name.startsWith('Saved item /'))c.visible=false;for(const t of n.findAllWithCriteria({types:['TEXT']})){if(t.characters==='Ideas')t.characters='Choose a folder';if(t.characters==='02')t.characters='0';if(t.characters==='Your little collection of good things.')t.characters='Choose a folder to get started.';}}else{for(const c of [n,...n.findAll(()=>true)])if('fills'in c&&Array.isArray(c.fills)&&c.fills.some(f=>f.type==='IMAGE'))c.fills=[paint('high')];for(const t of n.findAllWithCriteria({types:['TEXT']})){if(t.characters==='Your Name.')t.characters='No picks yet';if(t.characters==='Anime film · 1h 46m')t.characters='Save a title in Media.';}}}if(b.caption){const t=txt(box,b.caption,w,'body-medium','muted');t.textAlignHorizontal='CENTER';}return box;}

function faq(par,b,w){const list=col('Help questions',w,4);par.appendChild(list);for(const [i,q]of b.questions.entries()){const box=col(q.title,w,12,'surface',16);box.topLeftRadius=box.topRightRadius=i===0?24:4;box.bottomLeftRadius=box.bottomRightRadius=i===b.questions.length-1?24:4;list.appendChild(box);const h=row(q.title,w-32,12);h.minHeight=32;box.appendChild(h);const title=txt(h,q.title,w-64,'title-large');title.layoutSizingHorizontal='FILL';const arrow=icon(h,'chevron','muted',20);if(q.answer)arrow.rotation=-90;register(h,q.target||b.target);if(q.answer){txt(box,q.answer,w-32,'body-large','muted');const actions=row('Answer actions',w-32,8);box.appendChild(actions);for(const label of ['Helpful','Not helpful','Copy answer'])button(actions,label,q.actionTarget||q.target,'text',(w-48)/3);}}return list;}
function widgetGrid(par,b,w){const grid=row('Available widgets',w,16);grid.counterAxisAlignItems='MIN';par.appendChild(grid);for(const item of b.items){const cell=col(item.title,(w-16)/2,12);grid.appendChild(cell);widget(cell,{widget:item.widget},cell.width);txt(cell,item.title,cell.width,'title-large');if(item.sub)txt(cell,item.sub,cell.width,'body-medium','muted');button(cell,item.label||'Set up widget',item.target,'tonal',cell.width);}return grid;}

function markdown(par,doc,w){const raw=COPY[doc]||doc;for(const block of raw.split(/\n\s*\n/)){const cleaned=block.replace(/\[([^\]]+)\]\([^\)]+\)/g,'$1').replace(/\*\*/g,'').replace(/`/g,'').trim();if(!cleaned||cleaned==='---')continue;const heading=/^#{1,3}\s/.test(cleaned)||/^\*\*[^*]+\*\*$/.test(block.trim());txt(par,cleaned.replace(/^#{1,3}\s/,''),w,heading?'title-large':'body-large',heading?'ink':'muted');}}
function timePicker(par,b,w){const n=control('time',par,Math.min(328,w),{Format:'12 hour'});n.fills=[paint('high')];n.setProperties({[prop(n,'Headline#')]:b.label||'Custom time'});const times=n.findAllWithCriteria({types:['TEXT']}).filter(t=>t.name==='Time text');if(times[0])times[0].characters=b.hour||'09';if(times[1])times[1].characters=b.minute||'00';for(const t of n.findAllWithCriteria({types:['TEXT']})){if(t.characters==='OK')t.characters='Set time';t.fills=[paint(readable(t.name==='Time text'?'primary':'ink',t.name==='Time text'?'selected':'high',t.name==='Time text'?3:4.5))];}for(const f of n.findAll(x=>x.type==='FRAME'&&/input|period|container|^AM$|^PM$/i.test(x.name))){if(f.fills.length||f.name==='AM')f.fills=[paint('selected')];if(f.strokes.length)f.strokes=[paint(readable('primary','selected',3))];}for(const i of n.findAllWithCriteria({types:['INSTANCE']})){if(i.name==='.Building Blocks/Period Selector')i.fills=[];if(i.name==='Icon'&&i.width===24)i.swapComponent(icons.clock);}for(const c of n.findAllWithCriteria({types:['LINE','RECTANGLE']}))if(c.width<=2){if(c.fills.length)c.fills=[paint('primary')];if(c.strokes.length)c.strokes=[paint('primary')];}deep(n);register(n,b.target);return n;}
function tabs(par,b,w){const n=control('tabs',par,w,{Configuration:'Fixed',Style:'Primary',Layout:'Label only'});const labels=n.findAllWithCriteria({types:['TEXT']});for(let i=0;i<labels.length;i++){if(i>=b.labels.length){labels[i].parent.visible=false;continue;}labels[i].characters=b.labels[i];labels[i].fills=[paint(i===b.selected?'primary':'muted')];register(labels[i].parent,b.targets?.[i]);}deep(n);return n;}
function blocks(par,list,w){for(const b of list){if(b.type==='group'){const sec=col(b.label,w,8);par.appendChild(sec);txt(sec,b.label,w,'label-large','muted');const g=col(b.label+' · Connected rows',w,4);sec.appendChild(g);for(const [i,r]of b.rows.entries())setting(g,r,w,i,b.rows.length);}
else if(b.type==='row')setting(par,b,w);else if(b.type==='text')txt(par,b.text,w,b.role||'body-large',b.color||'muted');else if(b.type==='heading')txt(par,b.text,w,'title-large');else if(b.type==='button')button(par,b.label,b.target,b.kind||'tonal',w,b.destructive?'error':null);else if(b.type==='field')field(par,b,w);else if(b.type==='options')options(par,b,w);else if(b.type==='chips')chips(par,b,w);else if(b.type==='stats')stats(par,b,w);else if(b.type==='chart')chart(par,b,w);else if(b.type==='slider')slider(par,b,w);else if(b.type==='widget')widget(par,b,w);else if(b.type==='widgetGrid')widgetGrid(par,b,w);else if(b.type==='faq')faq(par,b,w);else if(b.type==='legal')markdown(par,b.doc,w);else if(b.type==='time')timePicker(par,b,w);else if(b.type==='tabs')tabs(par,b,w);else if(b.type==='divider')divider(par,w);
else if(b.type==='progress'){const n=control('progress',par,w,{Type:'Flat',Thickness:'4 dp',Progress:String(b.value||50)});n.name=b.label||'Verification progress';if(b.label)txt(par,b.label,w,'body-medium','muted');}
else if(b.type==='status'){const box=col(b.title,w,8);par.appendChild(box);if(b.icon)icon(box,b.icon,b.error?'error':'primary',32);txt(box,b.title,w,'title-large',b.error?'error':'ink');txt(box,b.sub,w,'body-large','muted');if(b.action)button(box,b.action,b.target,'tonal',w);}
else if(b.type==='consent'){const r=row('Sharing consent',w,12);r.minHeight=48;par.appendChild(r);const n=control('checkbox',r,24,{Type:b.on?'Selected':'Unselected',State:'Enabled'});themeInk(n,b.on?'primary':'muted');txt(r,b.text,w-36,'body-medium','muted');}
}}
let lane=CONFIG.laneId?await figma.getNodeByIdAsync(CONFIG.laneId):null;
if(!lane){lane=col(CONFIG.name,2400,24);board.appendChild(lane);mutated.add(board.id);txt(lane,CONFIG.name,2400,'headline-medium');txt(lane,CONFIG.description,2400,'body-large','muted');}
const screens={},screenRows=[];
for(let i=0;i<SCREENS.length;i++) {
  const spec=SCREENS[i],W=spec.width||448,H=spec.height||997;
  if(i%5===0){const r=row(CONFIG.name+' · Review row '+(Math.floor(i/5)+1),2400,24);r.counterAxisAlignItems='MIN';lane.appendChild(r);screenRows.push(r);}
  const wrap=col(spec.caption||spec.title,W,12);screenRows[screenRows.length-1].appendChild(wrap);txt(wrap,spec.caption||spec.title,W,'label-large','muted');
  let f;
  if(spec.kind==='sheet'||spec.kind==='dialog'){
    const parent=parentFrames[spec.parent]||(screens[spec.parent]&&await figma.getNodeByIdAsync(screens[spec.parent].id));if(!parent)throw new Error('Missing sheet background '+spec.parent);f=deep(parent.clone());wrap.appendChild(f);f.name=spec.key;f.resize(W,H);currentFrame=f;
    const scrim=keep(figma.createRectangle());scrim.name='Modal scrim';scrim.resize(W,H);scrim.fills=[{type:'SOLID',color:{r:0,g:0,b:0},opacity:.48}];f.appendChild(scrim);scrim.layoutPositioning='ABSOLUTE';scrim.x=scrim.y=0;register(scrim,spec.parent);
    if(spec.kind==='sheet'){
      const original=keep(native.sheet.defaultVariant.createInstance());f.appendChild(original);original.setProperties({Modal:'False'});deep(original);const s=deep(original.detachInstance());s.name=spec.title+' · Native bottom sheet';for(const c of [...s.children])drop(c);s.layoutMode='VERTICAL';s.resize(W,10);s.layoutSizingHorizontal='FIXED';s.layoutSizingVertical='HUG';s.itemSpacing=16;s.paddingLeft=s.paddingRight=20;s.paddingTop=12;s.paddingBottom=24;s.topLeftRadius=s.topRightRadius=28;s.bottomLeftRadius=s.bottomRightRadius=0;s.fills=[paint('surface')];s.clipsContent=true;
      const handleRow=row('Material drag handle',W-40,0);handleRow.primaryAxisAlignItems='CENTER';s.appendChild(handleRow);const handle=keep(figma.createRectangle());handle.name='Drag handle';handle.resize(32,4);handle.cornerRadius=2;handle.fills=[paint('muted')];handleRow.appendChild(handle);
      const h=row('Sheet heading',W-40,12);s.appendChild(h);const t=txt(h,spec.title,W-100,'headline-medium');t.layoutSizingHorizontal='FILL';const close=col('Close sheet',48,0);close.resize(48,48);close.layoutSizingVertical='FIXED';close.counterAxisAlignItems=close.primaryAxisAlignItems='CENTER';h.appendChild(close);icon(close,'close','muted',20);register(close,spec.parent);if(spec.subtitle)txt(s,spec.subtitle,W-40,'body-large','muted');const body=col('Sheet choices',W-40,16);s.appendChild(body);blocks(body,spec.blocks,W-40);if(spec.footer?.length){const footer=col('Sheet actions',W-40,8);s.appendChild(footer);blocks(footer,spec.footer,W-40);}
      await Promise.all(styleWrites);const max=H-112;if(s.height>max){const bodyH=Math.max(160,max-(s.height-body.height));body.resize(W-40,bodyH);body.layoutSizingVertical='FIXED';body.clipsContent=true;body.overflowDirection='VERTICAL';s.resize(W,max);s.layoutSizingVertical='FIXED';}
      s.layoutPositioning='ABSOLUTE';s.x=0;s.y=H-s.height;const gestureArea=deep(gesture.clone());s.appendChild(gestureArea);gestureArea.layoutPositioning='ABSOLUTE';gestureArea.resize(W,28);gestureArea.x=0;gestureArea.y=s.height-28;
    } else {
      const n=control('dialog',f,Math.min(360,W-48),{Icon:'False'});n.setProperties({[prop(n,'Title#')]:spec.title,[prop(n,'Supporting text#')]:spec.message||spec.subtitle||''});n.name=spec.title;n.fills=[paint('high')];
      const labels=n.findAllWithCriteria({types:['TEXT']});const actionLabels=labels.filter(t=>t.name==='Label');if(actionLabels[0])actionLabels[0].characters=spec.cancel||'Cancel';if(actionLabels[1])actionLabels[1].characters=spec.confirm||'Confirm';for(const t of labels){t.fills=[paint(readable(t===actionLabels[1]&&spec.destructive?'error':t.name==='Supporting text'?'muted':'ink','high'))];if(t.name==='Headline')styleWrites.push(t.setTextStyleIdAsync(styles['headline-medium'].id));if(t.name==='Supporting text')styleWrites.push(t.setTextStyleIdAsync(styles['body-large'].id));}deep(n);n.layoutPositioning='ABSOLUTE';n.x=(W-n.width)/2;n.y=(H-n.height)/2;if(actionLabels[0])register(actionLabels[0].parent,spec.parent);if(actionLabels[1])register(actionLabels[1].parent,spec.confirmTarget||spec.parent);
    }
  } else {
    f=col(spec.key,W,0,'bg');f.resize(W,H);f.layoutSizingVertical='FIXED';f.cornerRadius=32;f.clipsContent=true;wrap.appendChild(f);f.setExplicitVariableModeForCollection('VariableCollectionId:146:927',spec.theme==='Light'?'146:1':'146:2');f.setExplicitVariableModeForCollection('VariableCollectionId:146:977',spec.theme==='Light'?'146:3':'146:4');
    currentFrame=f;const headerSource=spec.compact?(header.parent.children.find(n=>n.type==='COMPONENT'&&n.name.includes('Compact'))||header):header;const h=deep(headerSource.createInstance());f.appendChild(h);if(W!==448)h.resize(W,h.height);for(const t of h.findAllWithCriteria({types:['TEXT']})){if(t.name==='Page title'||t.name==='Page subtitle'){t.characters=t.name==='Page title'?spec.title:spec.subtitle||'';t.textAutoResize='HEIGHT';t.resize(spec.compact?W-80:W-40,Math.max(1,t.height));t.layoutSizingHorizontal='FILL';t.layoutSizingVertical='HUG';}}if(!spec.compact){const hb=h.findAllWithCriteria({types:['FRAME']}).find(n=>n.name==='Header body'),copy=h.findAllWithCriteria({types:['FRAME']}).find(n=>n.name==='Page title & subtitle');const bh=Math.max(160,copy.y+copy.height+24);hb.resize(W,bh);h.resize(W,bh+53);}const back=h.findAllWithCriteria({types:['FRAME','INSTANCE']}).find(n=>n.name==='Back');if(back)register(back,spec.back||'index');
    const viewport=col('Scrollable settings',W,0);f.appendChild(viewport);viewport.layoutSizingVertical='FILL';viewport.clipsContent=true;viewport.overflowDirection='VERTICAL';const body=col('Settings content',W,20,null,20);body.paddingTop=12;viewport.appendChild(body);blocks(body,spec.blocks,W-40);const g=deep(gesture.clone());f.appendChild(g);g.resize(W,g.height);g.layoutSizingHorizontal='FILL';
  }
  parentFrames[spec.key]=f;screens[spec.key]={id:f.id,title:spec.title,kind:spec.kind||'page',theme:spec.theme||(spec.parent&&(STATE.screens[spec.parent]?.theme||screens[spec.parent]?.theme))||'Dark',parent:spec.parent||null,width:W,height:H};
}
await Promise.all(styleWrites);
mutated.add(lane.id);
const result={laneId:lane.id,createdNodeIds:[...created],mutatedNodeIds:[...mutated],removedNodeIds:[...removed],screens,links,editable:true,nativeControls:[...needs],bounds:{x:lane.x,y:lane.y,width:lane.width,height:lane.height}};
if(JSON.stringify(result).length>47000)throw new Error('Construction return exceeds safe size; split this screen set.');
return result;

