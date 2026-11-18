// Append after figma-common.js and figma-screen-helpers.js.
// Finish the seven-screen review board and preserve retired concepts.
const board=await figma.getNodeByIdAsync('293:2528');
const row=await figma.getNodeByIdAsync('293:2531');
const archiveWidgets=await figma.getNodeByIdAsync('309:5180');
const copyText=async(id,value)=>{const n=await figma.getNodeByIdAsync(id);for(const s of n.getStyledTextSegments(['fontName']))await figma.loadFontAsync(s.fontName);n.characters=value;n.name=value;mutated.add(n.id);return n;};
board.name='Cobalt / Screening / Your taste · Current Android design';
board.resize(3456,board.height);board.layoutSizingHorizontal='FIXED';board.layoutSizingVertical='HUG';mutated.add(board.id);
row.name='Seven current views · overview, reasons, feedback, evidence, first use, Others and search';
row.resize(3376,row.height);row.layoutSizingHorizontal='FIXED';row.layoutSizingVertical='HUG';row.itemSpacing=40;mutated.add(row.id);
await copyText('293:2529','Your taste · current Android design');
await copyText('293:2530','Cobalt settings typography, clear sections and compact rows. Scores, sources and corrections open when you need them.');
for(const id of ['293:2529','293:2530']){const n=await figma.getNodeByIdAsync(id);n.resize(3376,n.height);n.textAutoResize='HEIGHT';mutated.add(n.id);}
const copy=[
 ['01 · Your taste','Five leading genres. Explore the rest in Others.'],
 ['02 · Why this pick?','Reasons first. Open ranking points on demand.'],
 ['03 · Your feedback','Compact reactions. Source and controls open on tap.'],
 ['04 · A genre’s feedback','See the titles shaping one genre.'],
 ['05 · Your first feedback','One starting action and a useful saved pick.'],
 ['06 · Other genres','Every remaining genre stays accessible.']
];
for(const [index,column] of row.children.slice(0,6).entries()){
  column.name=copy[index][0];mutated.add(column.id);
  const labels=column.children.filter(n=>n.type==='TEXT');
  await copyText(labels[0].id,copy[index][0]);
  await copyText(labels[labels.length-1].id,copy[index][1]);
}
for(const [state,id] of Object.entries(ids.search)){
  const phoneRoot=await figma.getNodeByIdAsync(id);phoneRoot.name='Prototype / Saved titles / '+state;mutated.add(phoneRoot.id);
  const content=phoneRoot.findOne(n=>n.name==='Scrollable screen sections');
  content.itemSpacing=16;mutated.add(content.id);
  if(content.children[0]?.type==='TEXT'&&content.children[1]?.type==='TEXT'){
    const heading=auto(null,'Search results heading',416,'VERTICAL',8);
    padding(heading,8);const first=content.children[0],second=content.children[1];content.insertChild(0,heading);
    for(const n of [first,second]){heading.appendChild(n);n.resize(400,n.height);n.textAutoResize='HEIGHT';mutated.add(n.id);}
  }
  const field=phoneRoot.findOne(n=>n.name==='Search saved titles · filled rounded field');
  if(field){rounded(field,16);mutated.add(field.id);}
  const filtersRow=phoneRoot.findOne(n=>n.name==='Title filters');
  clear(filtersRow);
  for(const [label,width] of [['All',48],['Ready to suggest',146],['Not suggested',132]]){
    const chip=auto(filtersRow,label,width,'HORIZONTAL');
    chip.paddingLeft=chip.paddingRight=12;chip.paddingTop=chip.paddingBottom=6;chip.cornerRadius=8;
    chip.fills=label==='All'?[paint('selected')]:[];
    chip.strokes=label==='All'?[]:[paint('outline',.75)];chip.strokeWeight=1;
    await text(chip,label,'row',width-24,label==='All'?'primary':'muted');
  }
}
let searchColumn=row.children.find(n=>n.name==='07 · Saved titles');
if(!searchColumn){
  searchColumn=auto(row,'07 · Saved titles',448,'VERTICAL',12);
  await text(searchColumn,'07 · Saved titles','section',448,'boardInk');
  const preview=record((await figma.getNodeByIdAsync(ids.search.Current)).clone());
  searchColumn.appendChild(preview);preview.name='Saved titles / Current';preview.layoutSizingHorizontal='FIXED';preview.layoutSizingVertical='FIXED';
  await text(searchColumn,'Search, filter and inspect a saved movie or show.','body',448,'boardInk');
}
// Keep the old correction concepts and dialogs together in a separate archive.
let archive=page.children.find(n=>n.name==='Archive · superseded insight correction concepts');
const shift=board.x+board.width+160-archiveWidgets.x;
if(shift>0){
  archiveWidgets.x+=shift;mutated.add(archiveWidgets.id);
  for(const n of [...page.children]){
    if(n.id!==archiveWidgets.id&&n.x>=13056&&(/^Prototype \/ (Details|Others|All feedback|Learning history|Hidden titles) \//.test(n.name))){n.x+=shift;mutated.add(n.id);}
  }
}
if(!archive){
  archive=auto(page,'Archive · superseded insight correction concepts',2480,'VERTICAL',24,'paper');
  padding(archive,40);archive.x=archiveWidgets.x;archive.y=archiveWidgets.y+archiveWidgets.height+160;
  await text(archive,'Archive · dense correction concepts','board',2400,'boardInk');
  const old=await figma.getNodeByIdAsync('293:3488');await currentFonts(old);archive.appendChild(old);old.name='Retired confirmation and explanation examples';mutated.add(old.id);
  const dialogs=auto(archive,'Retired modal examples',2400,'HORIZONTAL',24);
  for(const id of ['293:5917','293:5935']){const n=await figma.getNodeByIdAsync(id);await currentFonts(n);dialogs.appendChild(n);n.layoutSizingHorizontal=n.layoutSizingVertical='FIXED';mutated.add(n.id);}
}
await copyText('293:3487','Details when you need them');
if(!board.children.some(n=>n.name==='Current correction behavior')){
  const guide=auto(board,'Current correction behavior',3376,'HORIZONTAL',40);
  for(const [title,detail] of [
    ['Ignore feedback','Takes effect immediately, with Undo. Your saved rating stays.'],
    ['Reset feedback','Requires confirmation. Saved ratings and watch progress stay.'],
    ['Five + Others','The overview stays tidy. Every genre still learns separately.']
  ]){
    const card=auto(guide,title,448,'VERTICAL',8);
    await text(card,title,'section',448,'boardInk');
    await text(card,detail,'body',448,'boardInk');
  }
}
await copyText('293:3686','Updated ranking points · 44.62');
await copyText('293:4126','Updated ranking points · 47.24');
await copyText('293:3525','Illustrative correction outcomes. Opening details preserves a compact overview; scores reflect the remaining genre evidence.');
// Detail screens open as overlays; moving between disclosures replaces the overlay.
const overlays=new Set(page.children.filter(n=>n.name.startsWith('Prototype / Details /')).map(n=>n.id));
const scopeRoots=[board,await figma.getNodeByIdAsync('293:3523'),...page.children.filter(n=>n.name.startsWith('Prototype /')&&n.width===448&&!['293:5963','293:6042'].includes(n.id))];
const changedRoutes=[];
const visited=new Set();
for(const root of scopeRoots)for(const n of [root,...root.findAll(()=>true)]){
  if(visited.has(n.id)||!('reactions'in n))continue;visited.add(n.id);
  const reactions=JSON.parse(JSON.stringify(n.reactions));let changed=false;
  let ancestor=n;while(ancestor.parent&&ancestor.parent.type!=='PAGE')ancestor=ancestor.parent;
  for(const reaction of reactions)for(const a of reaction.actions??[]){
    if(a.type!=='NODE')continue;
    if(overlays.has(a.destinationId)){
      const desired=overlays.has(ancestor.id)?'SWAP':'OVERLAY';
      if(a.navigation!==desired){a.navigation=desired;changed=true;}
    }else if(a.destinationId==='293:5926'&&a.navigation!=='OVERLAY'){a.navigation='OVERLAY';changed=true;}
  }
  if(changed){await n.setReactionsAsync(reactions);mutated.add(n.id);changedRoutes.push(n.id);}
}
const fontIssues=[];
const phoneRoots=[...row.children.map(n=>n.children.find(c=>c.type==='FRAME'&&c.width===448)),...scopeRoots.filter(n=>n.parent.type==='PAGE'&&n.width===448)].filter(Boolean);
for(const root of phoneRoots)for(const n of root.findAllWithCriteria({types:['TEXT']})){
  let parent=n.parent,isSystem=false;while(parent&&parent.id!==root.id){if(parent.name==='System status'){isSystem=true;break;}parent=parent.parent;}
  if(isSystem)continue;
  for(const s of n.getStyledTextSegments(['fontName']))if(!['Noto Serif','Roboto'].includes(s.fontName.family))fontIssues.push({id:n.id,name:n.name,font:s.fontName});
}
board.placeholder=false;mutated.add(board.id);
return await finish({boardId:board.id,searchPreviewId:searchColumn.children.find(n=>n.type==='FRAME').id,columns:row.children.length,
  bounds:board.absoluteBoundingBox,archiveId:archive.id,archiveBounds:archive.absoluteBoundingBox,shift,changedRoutes,fontIssues});

