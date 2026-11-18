// Shared helpers adapted from create-your-taste-figma.js; editable Cobalt components and tokens.
const page = await figma.getNodeByIdAsync('77:915');
await figma.setCurrentPageAsync(page);
const created = new Set(), mutated = new Set(), removed = new Set(), routes = [];
const sourceIds = { status:'142:2085', top:'142:2089', header:'142:2095', nav:'92:2215', button:'92:2222',
  movie:'26:1547', chevron:'26:2680', close:'26:2707', back:'26:2655', info:'26:2162',
  history:'26:3471', search:'26:2793', bookmark:'26:1783', useful:'47:1255', rejected:'47:1319', check:'25:951' };
const styleIds = { board:'S:84dfb7bef8d7ed8ddca9c1884f54783c13d42643,', title:'S:05f1425634395ea4550c67291fb6ad0b0fdeb426,',
  body:'S:cbe3138e29f088dd852eee16f3fbae0e4a2f6c4c,', row:'S:70af0bf50cdb31680884e19363086e2d39424895,',
  section:'S:214a9a9ada5dbb8e84360bd38a243cc68e1e198a,', sheet:'S:10be63aa6decf3077dc21749353477ab6f61e33d,', meta:'S:ef84359f1e29f42596a6e7ada0df55d87b04d849,' };
const variableIds = { background:'VariableID:92:2183', surface:'VariableID:92:2184', text:'VariableID:92:2185', muted:'VariableID:92:2186',
  primary:'VariableID:92:2187', onPrimary:'VariableID:92:2188', selected:'VariableID:92:2189', outline:'VariableID:92:2190',
  boardInk:'VariableID:92:2192', paper:'VariableID:77:918', s6:'VariableID:77:951', s8:'VariableID:77:952', s12:'VariableID:77:953',
  s16:'VariableID:77:954', s18:'VariableID:77:955', s20:'VariableID:77:956', r12:'VariableID:77:957', r24:'VariableID:77:958' };
const [sp,st,vp] = await Promise.all([
  Promise.all(Object.entries(sourceIds).map(async([k,id])=>[k,await figma.getNodeByIdAsync(id)])),
  Promise.all(Object.entries(styleIds).map(async([k,id])=>[k,await figma.getStyleByIdAsync(id)])),
  Promise.all(Object.entries(variableIds).map(async([k,id])=>[k,await figma.variables.getVariableByIdAsync(id)]))]);
const sources=Object.fromEntries(sp), styles=Object.fromEntries(st), tokens=Object.fromEntries(vp);
if([...sp,...st,...vp].some(([,v])=>!v)) throw new Error('A selected Cobalt dependency is missing.');
const fonts = new Map();
for(const s of Object.values(styles)) fonts.set(JSON.stringify(s.fontName),s.fontName);
for(const root of [...Object.values(sources),...await Promise.all(['293:2528','293:2532','293:3523'].map(id=>figma.getNodeByIdAsync(id)))])
  for(const t of root.findAllWithCriteria({types:['TEXT']})) for(const s of t.getStyledTextSegments(['fontName'])) fonts.set(JSON.stringify(s.fontName),s.fontName);
await Promise.all([...fonts.values()].map(f=>figma.loadFontAsync(f)));
await figma.loadFontAsync({family:'Inter',style:'Regular'});
const existingBack=(await figma.getNodeByIdAsync('293:4386')).findOne(n=>n.name==='Back');
const backTemplate=JSON.parse(JSON.stringify(existingBack?.reactions?.find(r=>(r.actions??[]).some(a=>a.type==='NODE'))??{
  trigger:{type:'ON_CLICK'},actions:[{type:'NODE',destinationId:'293:4386',navigation:'NAVIGATE',transition:null}]
}));
function record(n){for(const x of [n,...('findAll'in n?n.findAll(()=>true):[])])created.add(x.id);return n;}
function clear(n){for(const c of [...n.children]){for(const x of [c,...('findAll'in c?c.findAll(()=>true):[])])removed.add(x.id);c.remove();}mutated.add(n.id);}
function paint(role,opacity=1){return {...figma.variables.setBoundVariableForPaint({type:'SOLID',color:{r:0,g:0,b:0},opacity},'color',tokens[role]),opacity};}
function padding(n,value){for(const k of ['paddingLeft','paddingRight','paddingTop','paddingBottom']){n.setBoundVariable(k,null);n[k]=value;if(tokens['s'+value])n.setBoundVariable(k,tokens['s'+value]);}}
function rounded(n,value=24){n.cornerRadius=value;if(tokens['r'+value])n.setBoundVariable('cornerRadius',tokens['r'+value]);}
function auto(parent,name,width,direction='VERTICAL',gap=0,fill=null){const n=record(figma.createAutoLayout(direction));if(parent)parent.appendChild(n);n.name=name;n.resize(width,1);n.layoutSizingHorizontal='FIXED';n.layoutSizingVertical='HUG';n.itemSpacing=gap;if(tokens['s'+gap])n.setBoundVariable('itemSpacing',tokens['s'+gap]);n.fills=fill?[paint(fill)]:[];return n;}
async function text(parent,value,role='body',width=416,color='text',name=value){const n=record(figma.createText());parent.appendChild(n);await n.setTextStyleIdAsync(styles[role].id);n.resize(width,1);n.textAutoResize='HEIGHT';n.layoutSizingHorizontal='FIXED';n.layoutSizingVertical='HUG';n.characters=value;n.name=name;n.fills=[paint(color)];return n;}
function icon(parent,key,size=22,color='primary'){const src=sources[key],n=record(src.createInstance());parent.appendChild(n);n.resize(size,size);const map=new Map(src.findAll(()=>true).map(x=>[x.id,x]));for(const x of [n,...n.findAll(()=>true)]){const original=x===n?src:map.get(x.id.split(';').pop());if(!original)continue;for(const k of ['fills','strokes'])if(k in x&&Array.isArray(original[k]))x[k]=original[k].map(p=>p.type==='SOLID'?{...paint(color,p.opacity??1),visible:p.visible??true}:p);}return n;}
function poster(parent,width=48,height=68){const n=auto(parent,'Saved-title thumbnail',width);n.resize(width,height);n.layoutSizingVertical='FIXED';n.fills=[paint('outline',.16)];rounded(n,12);n.primaryAxisAlignItems=n.counterAxisAlignItems='CENTER';icon(n,'movie');return n;}
function group(parent,name,width=416){const n=auto(parent,name,width,'VERTICAL',0,'surface');rounded(n);return n;}
function divider(parent,width){const n=record(figma.createRectangle());parent.appendChild(n);n.name='Quiet group divider';n.resize(width,1);n.fills=[paint('outline',.22)];return n;}
function route(n,id,navigation='NAVIGATE'){if(id)routes.push({n,id,navigation});return n;}
function button(parent,label,width=416,tonal=true){const n=record(sources.button.createInstance());parent.appendChild(n);n.resize(width,48);n.setProperties({'Label#92:11':label});n.name=label;if(tonal){n.fills=[paint('selected')];for(const t of n.findAllWithCriteria({types:['TEXT']}))t.fills=[paint('primary')];}return n;}
async function nav(parent,title,subtitle,id=null,key='chevron',badge=null,navigation='NAVIGATE'){
  const width=parent.width-parent.paddingLeft-parent.paddingRight;
  const n=auto(parent,title,width,'HORIZONTAL',14,'surface');padding(n,16);rounded(n);n.counterAxisAlignItems='CENTER';
  const lead=auto(n,'Leading icon',40);lead.resize(40,40);lead.layoutSizingVertical='FIXED';lead.primaryAxisAlignItems=lead.counterAxisAlignItems='CENTER';icon(lead,key);
  const row=record(sources.nav.createInstance());n.appendChild(row);
  const rowWidth=width-32-54-(badge?80:0);row.resize(rowWidth,76);padding(row,0);row.fills=[];
  row.setProperties({'Title#92:9':title,'Description#92:10':subtitle});row.layoutSizingVertical='HUG';
  const copy=row.children.find(c=>c.type==='FRAME'&&c.name==='Navigation copy');
  const chevron=row.children.find(c=>c.type==='INSTANCE');
  const copyWidth=rowWidth-(badge?0:32);copy.resize(copyWidth,copy.height);copy.layoutSizingHorizontal='FILL';
  for(const t of copy.findAllWithCriteria({types:['TEXT']})){t.resize(copyWidth,t.height);t.textAutoResize='HEIGHT';t.layoutSizingHorizontal='FILL';}
  if(badge){if(chevron)chevron.visible=false;const count=auto(n,'Count badge',32,'HORIZONTAL');count.resize(32,32);count.layoutSizingVertical='FIXED';count.cornerRadius=16;count.fills=[paint('selected')];count.primaryAxisAlignItems=count.counterAxisAlignItems='CENTER';const value=await text(count,badge,'meta',24,'muted');value.textAlignHorizontal='CENTER';icon(n,'chevron',20,'muted');}
  route(n,id,navigation);return n;
}
async function badge(parent,label){const n=auto(parent,label,Math.max(58,label.length*6.7+20),'HORIZONTAL',0);n.paddingLeft=n.paddingRight=10;n.paddingTop=n.paddingBottom=5;n.cornerRadius=30;n.fills=[paint('outline',.16)];await text(n,label,'meta',n.width-20,'primary');return n;}
async function keyValue(parent,label,value,width=400){const n=auto(parent,label,width,'HORIZONTAL',18);await text(n,label,'body',(width-18)/2,'muted');await text(n,value,'body',(width-18)/2);return n;}
async function finish(extra={}){for(const r of routes){if(r.n.removed)continue;const copy=JSON.parse(JSON.stringify(backTemplate));for(const a of [...(copy.actions??[]),...(copy.action?[copy.action]:[])]){a.destinationId=r.id;a.navigation=r.navigation;}await r.n.setReactionsAsync([copy]);mutated.add(r.n.id);}return {success:true,createdNodeIds:[...created],mutatedNodeIds:[...mutated],removedNodeIds:[...removed],...extra};}
