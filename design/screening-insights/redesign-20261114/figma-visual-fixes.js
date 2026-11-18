// Append after figma-common.js. Repair only current insights and their masters.
const board=await figma.getNodeByIdAsync('293:2528');
const rootIds=['293:3523','331:5295','331:5520','331:5813','331:5999','293:2535','293:2622','293:2707','322:4936','322:4941','322:4946'];
const roots=[board,...await Promise.all(rootIds.map(id=>figma.getNodeByIdAsync(id))),...page.children.filter(n=>n.name.startsWith('Prototype /')&&n.width===448&&!['293:5963','293:6042'].includes(n.id))];
const all=[],seen=new Set();
for(const root of roots.filter(Boolean))for(const n of [root,...root.findAll(()=>true)])if(!seen.has(n.id)){seen.add(n.id);all.push(n);}
const iconMasters=new Map(),fixedIcons=[],fixedRows=[];
for(const n of all){
  if(n.type==='INSTANCE'){
    const m=await n.getMainComponentAsync();
    if(m?.name.startsWith('Cobalt / Icons /')&&Array.isArray(m.fills)&&Array.isArray(n.fills)){
      const original=m.fills;
      if(original.length===n.fills.length&&original.some(f=>f.visible===false)){
        n.fills=n.fills.map((f,i)=>({...f,visible:original[i].visible??true}));
        mutated.add(n.id);fixedIcons.push(n.id);
      }
    }
  }
  if(n.type==='FRAME'&&Array.isArray(n.fills)&&n.width<=100&&n.height<=140){
    const next=n.fills.map(f=>f.type==='SOLID'&&f.boundVariables?.color?.id===tokens.outline.id?{...f,opacity:.16}:f);
    if(JSON.stringify(next)!==JSON.stringify(n.fills)){n.fills=next;mutated.add(n.id);}
  }
  if(n.name==='Quiet group divider'&&Array.isArray(n.fills)){
    n.fills=n.fills.map(f=>({...f,opacity:.22}));mutated.add(n.id);
  }
}
for(const n of all){
  if(n.type!=='FRAME'||n.layoutMode!=='HORIZONTAL')continue;
  const body=n.children.find(c=>c.type==='INSTANCE'&&c.name==='Cobalt / Settings / Folio navigation');
  const lead=n.children.find(c=>c.type==='FRAME'&&c.name==='Leading icon');
  if(!body||!lead)continue;
  const width=n.parent.width-(n.parent.paddingLeft??0)-(n.parent.paddingRight??0);
  const number=n.children.find(c=>c.type==='TEXT');
  let outerChevron=n.children.find(c=>c.type==='INSTANCE'&&c.id!==body.id);
  let countBox=n.children.find(c=>c.name==='Count badge');
  if(number&&!countBox){
    countBox=auto(n,'Count badge',32,'HORIZONTAL');countBox.resize(32,32);countBox.layoutSizingVertical='FIXED';countBox.cornerRadius=16;countBox.fills=[paint('selected')];
    countBox.primaryAxisAlignItems=countBox.counterAxisAlignItems='CENTER';countBox.appendChild(number);number.resize(24,number.height);number.textAlignHorizontal='CENTER';mutated.add(number.id);
    if(!outerChevron)outerChevron=icon(n,'chevron',20,'muted');
    n.insertChild(2,countBox);n.insertChild(3,outerChevron);
  }
  n.resize(width,76);n.layoutSizingHorizontal='FIXED';n.layoutSizingVertical='HUG';padding(n,16);n.itemSpacing=14;n.counterAxisAlignItems='CENTER';rounded(n);
  lead.resize(40,40);lead.layoutSizingHorizontal=lead.layoutSizingVertical='FIXED';lead.fills=[];mutated.add(lead.id);
  const bodyWidth=width-32-40-14-(countBox?32+14+20+14:0);
  body.resize(bodyWidth,41);body.layoutSizingHorizontal='FIXED';body.layoutSizingVertical='HUG';padding(body,0);body.fills=[];body.itemSpacing=14;
  const copy=body.children.find(c=>c.type==='FRAME'&&c.name==='Navigation copy');
  const chevron=body.children.find(c=>c.type==='INSTANCE');
  if(chevron){const chevronId=chevron.id;if(!countBox)chevron.resize(18,18);chevron.visible=!countBox;mutated.add(chevronId);}
  const copyWidth=bodyWidth-(countBox?0:32);
  copy.resize(copyWidth,copy.height);copy.layoutSizingHorizontal='FILL';copy.layoutSizingVertical='HUG';mutated.add(copy.id);
  for(const t of copy.findAllWithCriteria({types:['TEXT']})){
    for(const s of t.getStyledTextSegments(['fontName']))await figma.loadFontAsync(s.fontName);
    t.resize(copyWidth,t.height);t.textAutoResize='HEIGHT';t.layoutSizingHorizontal='FILL';t.layoutSizingVertical='HUG';mutated.add(t.id);
  }
  mutated.add(n.id);mutated.add(body.id);fixedRows.push({id:n.id,width:n.width,height:n.height});
}
for(const state of ['Current','Ignored','Reset']){
  const root=await figma.getNodeByIdAsync({Current:'293:5942',Ignored:'293:5949',Reset:'293:5956'}[state]);root.cornerRadius=28;mutated.add(root.id);
}
const preview=await figma.getNodeByIdAsync('345:7875');preview.cornerRadius=28;mutated.add(preview.id);
return await finish({fixedIconCount:fixedIcons.length,fixedRows});

