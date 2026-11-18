// Targeted repair for the existing Your taste concept in the approved Cobalt file.
// The construction file is for a new board; this file updates the known created nodes.
// Use figma-use and figma-generate-design with the existing session.
const page=await figma.getNodeByIdAsync('77:915');await figma.setCurrentPageAsync(page);
const rootIds=["293:2528","293:2532","293:3523","293:4386","293:4543","293:4639","293:4782","293:4820","293:4975","293:5071","293:5214","293:5371","293:5467","293:5580","293:5917","293:5926","293:5935","293:5942","293:5949","293:5956"];
const roots=await Promise.all(rootIds.map(id=>figma.getNodeByIdAsync(id)));
const heading=page.children.find(n=>n.name==='Your taste · playable prototype frames');if(heading)roots.push(heading);
const all=[...new Map(roots.flatMap(root=>[root,...root.findAll(()=>true)]).map(n=>[n.id,n])).values()];
const fonts=new Map();for(const t of all.filter(n=>n.type==='TEXT'))for(const s of t.getStyledTextSegments(['fontName']))fonts.set(JSON.stringify(s.fontName),s.fontName);
await Promise.all([...fonts.values()].map(f=>figma.loadFontAsync(f)));
const mutated=new Set();let textChanges=0;let paintChanges=0;
const [primary,space8]=await Promise.all([figma.variables.getVariableByIdAsync('VariableID:92:2187'),figma.variables.getVariableByIdAsync('VariableID:77:952')]);
const evidence=await figma.getNodeByIdAsync('293:2794');
evidence.resize(416,76);evidence.primaryAxisSizingMode='FIXED';evidence.counterAxisSizingMode='AUTO';evidence.layoutSizingHorizontal='FIXED';evidence.layoutSizingVertical='HUG';
evidence.setBoundVariable('paddingTop',space8);evidence.setBoundVariable('paddingBottom',space8);mutated.add(evidence.id);
function fixText(t){
const horizontal=t.layoutSizingHorizontal;const width=t.width;
if(t.characters==='Rediscover'){t.characters='Saved';t.name='Saved';}
t.resize(width,1);t.textAutoResize='HEIGHT';
if('layoutMode'in t.parent&&t.parent.layoutMode!=='NONE'){
t.layoutSizingHorizontal=horizontal==='FILL'?'FILL':'FIXED';t.layoutSizingVertical='HUG';
}
mutated.add(t.id);textChanges++;
}
for(const t of all.filter(n=>n.type==='TEXT'&&n.id.startsWith('293:'))){
if(t.textAutoResize==='NONE'||t.characters==='Rediscover')fixText(t);
}
for(const t of all.filter(n=>n.type==='TEXT'&&n.characters.startsWith('Rediscover: saved 45 days ago.'))){t.characters="Base: every eligible title starts with 20 points.\nSaved: 45 days old and not marked watched.\nAge: time since saving. Taste: your genre feedback.\nRepeats: suggested twice. Runtime: under 100 minutes.";t.name='Plain-language explanation of every score contribution';fixText(t);}
const rows=all.filter(n=>n.type==='INSTANCE'&&n.mainComponent?.id==='293:2794');
for(const row of rows){
row.resize(row.width,76);row.primaryAxisSizingMode='FIXED';row.counterAxisSizingMode='AUTO';row.layoutSizingVertical='HUG';mutated.add(row.id);
for(const t of row.findAllWithCriteria({types:['TEXT']}))if(t.textAutoResize==='NONE')fixText(t);
const lead=row.children[0];if(lead.type!=='INSTANCE')throw new Error('Unexpected evidence icon structure');
const source=lead.mainComponent;const sourceMap=new Map(source.findAll(()=>true).map(n=>[n.id,n]));
for(const n of [lead,...lead.findAll(()=>true)]){
const src=n===lead?source:sourceMap.get(n.id.split(';').pop());if(!src)continue;
let changed=false;for(const prop of ['fills','strokes'])if(prop in n&&prop in src&&Array.isArray(src[prop])){
n[prop]=src[prop].map(p=>p.type==='SOLID'?figma.variables.setBoundVariableForPaint({...p},'color',primary):p);changed=true;
}
if(changed){mutated.add(n.id);paintChanges++;}
}
}
const sheets=all.filter(n=>n.type==='FRAME'&&n.name==='Thriller / evidence bottom sheet');
for(const sheet of sheets){sheet.x=0;sheet.y=997-sheet.height;mutated.add(sheet.id);}

const statusRoots=[...roots,...await Promise.all(['293:5963','293:6042'].map(id=>figma.getNodeByIdAsync(id)))];
const statuses=[...new Map(statusRoots.flatMap(r=>r.findAll(n=>n.type==='FRAME'&&n.name==='System status')).map(n=>[n.id,n])).values()];
for(const status of statuses){const last=status.children[status.children.length-1],filler=status.children[1],overflow=last.x+last.width-status.width;if(overflow>.5){const vertical=filler.layoutSizingVertical;filler.resize(filler.width-overflow,filler.height);filler.layoutSizingHorizontal='FIXED';filler.layoutSizingVertical=vertical;mutated.add(filler.id);}}
const phones=all.filter(n=>n.type==='FRAME'&&n.width===448&&n.height===997&&n.children.some(c=>c.name==='Read-only insights content'));
const bounds=phones.map(n=>{const c=n.children.find(c=>c.name==='Read-only insights content');return{id:n.id,name:n.name,y:c.y,height:c.height,bottom:c.y+c.height};});
const rowBounds=rows.map(n=>({id:n.id,w:n.width,h:n.height,copyHeight:n.children[1].height,copyTop:n.children[1].y,copyBottom:n.children[1].y+n.children[1].height}));
const result={success:true,mutatedNodeIds:[...mutated],textChanges,evidenceRows:rows.length,paintChanges,phoneBounds:bounds,rowBounds,sheets:sheets.map(n=>({id:n.id,y:n.y,h:n.height,bottom:n.y+n.height}))};
if(JSON.stringify(result).length>47000)throw new Error('Mutation report exceeds safe return size');
return result;

