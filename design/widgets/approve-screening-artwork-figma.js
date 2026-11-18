/**
 * validateCreation
 *
 * Verifies that a set of nodes exist and match expected structural properties.
 * Designed to run immediately after a creation script to catch partial failures
 * before proceeding to the next build phase.
 *
 * Each check specifies a node ID and any combination of expected properties.
 * A check passes when all specified expectations are met; it fails (with a
 * reason string) as soon as any expectation is violated.
 *
 * @param {Array<{
 *   nodeId: string,
 *   expectedChildCount?: number,
 *   expectedName?: string,
 *   expectedType?: NodeType
 * }>} checks
 *   - `nodeId`: The Figma node ID to look up via figma.getNodeById.
 *   - `expectedChildCount`: If set, the node must have exactly this many direct children.
 *     Applies to any node with a `children` property (frames, component sets, etc.).
 *   - `expectedName`: If set, the node's `.name` must exactly match this string.
 *   - `expectedType`: If set, the node's `.type` must exactly match this string.
 * @returns {{
 *   passed: string[],
 *   failed: Array<{nodeId: string, reason: string}>
 * }}
 *   `passed`: Array of nodeIds that passed all checks.
 *   `failed`: Array of objects with the nodeId and a human-readable reason string.
 */
async function validateCreation(checks) {
  const passed = []
  const failed = []

  for (const check of checks) {
    const node = await figma.getNodeByIdAsync(check.nodeId)

    // Node must exist
    if (!node) {
      failed.push({
        nodeId: check.nodeId,
        reason: `Node not found. It may not have been created, or was deleted.`,
      })
      continue
    }

    const reasons = []

    // Type check
    if (check.expectedType !== undefined && node.type !== check.expectedType) {
      reasons.push(`type is "${node.type}", expected "${check.expectedType}"`)
    }

    // Name check
    if (check.expectedName !== undefined && node.name !== check.expectedName) {
      reasons.push(`name is "${node.name}", expected "${check.expectedName}"`)
    }

    // Child count check
    if (check.expectedChildCount !== undefined) {
      if (!('children' in node)) {
        reasons.push(
          `node type "${node.type}" does not have children, but expectedChildCount=${check.expectedChildCount} was specified`,
        )
      } else {
        const actualCount = node.children.length
        if (actualCount !== check.expectedChildCount) {
          reasons.push(`has ${actualCount} children, expected ${check.expectedChildCount}`)
        }
      }
    }

    if (reasons.length > 0) {
      failed.push({
        nodeId: check.nodeId,
        reason: reasons.join('; '),
      })
    } else {
      passed.push(check.nodeId)
    }
  }

  return { passed, failed }
}

const page=await figma.getNodeByIdAsync('77:915');await figma.setCurrentPageAsync(page);
const reviewName='Cobalt / Screening / Approved Artwork A · Current widget';
const assetsName='Screening / Approved card · Main components';
const archiveName='Cobalt / Screening / Concepts archive · Retired directions';
if(page.children.some(n=>[reviewName,assetsName,archiveName].includes(n.name)))throw new Error('Approval boards already exist; inspect before another pass.');
const created=new Set(),mutated=new Set(),styleWrites=[];
const fontMap=new Map();for(const t of page.findAllWithCriteria({types:['TEXT']}))for(const s of t.getStyledTextSegments(['fontName']))fontMap.set(JSON.stringify(s.fontName),s.fontName);
const styleIds={heading:'S:84dfb7bef8d7ed8ddca9c1884f54783c13d42643,',body:'S:cbe3138e29f088dd852eee16f3fbae0e4a2f6c4c,',section:'S:214a9a9ada5dbb8e84360bd38a243cc68e1e198a,',meta:'S:ef84359f1e29f42596a6e7ada0df55d87b04d849,'};
const styles=Object.fromEntries(await Promise.all(Object.entries(styleIds).map(async([k,id])=>[k,await figma.getStyleByIdAsync(id)])));
for(const s of Object.values(styles))fontMap.set(JSON.stringify(s.fontName),s.fontName);
await Promise.all([...fontMap.values()].map(f=>figma.loadFontAsync(f)));
const [paper,ink,primary,s16,s20]=await Promise.all(['VariableID:92:2191','VariableID:92:2192','VariableID:92:2187','VariableID:77:954','VariableID:77:956'].map(id=>figma.variables.getVariableByIdAsync(id)));
function paint(variable){return figma.variables.setBoundVariableForPaint({type:'SOLID',color:{r:0,g:0,b:0}},'color',variable);}
function auto(parent,name,width,direction='VERTICAL',spacing=20,background=false){const f=figma.createAutoLayout(direction);created.add(f.id);f.name=name;if(parent)parent.appendChild(f);f.resize(width,1);f.layoutSizingHorizontal='FIXED';f.layoutSizingVertical='HUG';f.itemSpacing=spacing;if(spacing===20)f.setBoundVariable('itemSpacing',s20);if(spacing===16)f.setBoundVariable('itemSpacing',s16);f.fills=background?[paint(paper)]:[];f.counterAxisAlignItems='MIN';return f;}
function label(parent,value,role='body',width=336){const t=figma.createText();created.add(t.id);parent.appendChild(t);t.name=value;t.fontName=styles[role].fontName;t.characters=value;t.fills=[paint(ink)];styleWrites.push(t.setTextStyleIdAsync(styles[role].id).then(()=>{t.resize(width,1);t.textAutoResize='HEIGHT';t.layoutSizingHorizontal='FIXED';t.layoutSizingVertical='HUG';}));return t;}
function board(name,width,x,y){const f=auto(null,name,width,'VERTICAL',20,true);f.x=x;f.y=y;f.paddingLeft=f.paddingRight=40;f.paddingTop=f.paddingBottom=36;f.placeholder=true;return f;}
function move(n,parent){const old=n.parent;parent.appendChild(n);mutated.add(n.id);mutated.add(old.id);mutated.add(parent.id);}
function editText(n,value){n.characters=value;n.textAutoResize='HEIGHT';if('layoutMode'in n.parent&&n.parent.layoutMode!=='NONE')n.layoutSizingVertical='HUG';mutated.add(n.id);}
async function fitStatusRow(row){
const overflow=Math.max(0,...row.children.map(n=>n.x+n.width+row.paddingRight-row.width));
if(overflow<=0.1)return;
const spacer=row.children[1];if(spacer.type!=='FRAME'||spacer.width<=overflow)throw new Error('Unexpected status structure: '+row.id);
const vertical=spacer.layoutSizingVertical;spacer.resize(spacer.width-overflow,spacer.height);spacer.layoutSizingHorizontal='FIXED';spacer.layoutSizingVertical=vertical;mutated.add(spacer.id);mutated.add(row.id);
}
const chosen=await figma.getNodeByIdAsync('224:2210');
chosen.name='Cobalt / Widgets / Screening Card · Approved Artwork A';
chosen.description='Current chosen Android Screening Card. Full artwork with a floating 108 × 56 dp thumbs pill; each choice has a 48 dp target. Use Title and Metadata for content. Show progress only with real tracked playback; hide it for unknown or unstarted titles.';
const title=await figma.getNodeByIdAsync('224:2214'),metadata=await figma.getNodeByIdAsync('224:2215');
const titleKey=chosen.addComponentProperty('Title','TEXT',title.characters),metadataKey=chosen.addComponentProperty('Metadata','TEXT',metadata.characters);
title.componentPropertyReferences={characters:titleKey};metadata.componentPropertyReferences={characters:metadataKey};
for(const t of [title,metadata]){t.resize(180,t===title?34:16);t.textAutoResize='NONE';t.textTruncation='ENDING';t.maxLines=1;t.layoutSizingVertical='FIXED';mutated.add(t.id);}
chosen.editComponentProperty('Show progress#227:0',{defaultValue:false});mutated.add(chosen.id);
const progress=await figma.getNodeByIdAsync('227:2516'),edge=await figma.getNodeByIdAsync('227:2517');
progress.name='Cobalt / Screening / Optional tracked-playback edge';progress.description='Optional 3 dp playback edge, inset 28 dp to match ScreeningRenderer. The 46% amount is illustrative. No track, percentage or progress caption on the card.';
edge.resize((336-56)*0.46,3);edge.x=28;edge.name='Tracked playback / illustrative 46%';mutated.add(progress.id);mutated.add(edge.id);
const sourceMap=new Map([chosen,...chosen.findAll(()=>true)].map(n=>[n.id.startsWith('I')?n.id.slice(1):n.id,n]));
async function resetCard(instance){
for(const field of ['cornerRadius','topLeftRadius','topRightRadius','bottomLeftRadius','bottomRightRadius'])instance.setBoundVariable(field,null);
instance.resize(336,264);instance.cornerRadius=28;instance.clipsContent=true;
for(const field of ['paddingLeft','paddingRight','paddingTop','paddingBottom','itemSpacing','primaryAxisAlignItems','counterAxisAlignItems'])instance[field]=chosen[field];
instance.primaryAxisSizingMode='FIXED';instance.counterAxisSizingMode='FIXED';instance.layoutSizingHorizontal='FIXED';instance.layoutSizingVertical='FIXED';
for(const n of [instance,...instance.findAll(()=>true)]){
const src=n===instance?chosen:sourceMap.get(n.id.split(';').slice(1).join(';'));if(!src)continue;
for(const field of ['fills','strokes'])if(field in n&&field in src&&Array.isArray(src[field])){n[field]=src[field].map(p=>({...p}));mutated.add(n.id);}
if(n.type==='TEXT'&&src.type==='TEXT'){await n.setTextStyleIdAsync(src.textStyleId);n.resize(src.width,src.height);n.textAutoResize='NONE';n.textTruncation='ENDING';n.maxLines=1;mutated.add(n.id);}
}
instance.setProperties({[titleKey]:'Your Name.',[metadataKey]:'Anime film · 1h 46m','Show progress#227:0':false});mutated.add(instance.id);
}
const activeIds=["142:2020","142:2101","142:2164","142:2235","145:2643","293:5976","293:6055"],updated=[];
for(const id of activeIds){const n=await figma.getNodeByIdAsync(id);if(n.type!=='INSTANCE'||n.mainComponent.id!=='77:1214')throw new Error('Active old-card target changed: '+id);n.swapComponent(chosen);n.name='Screening Card / Approved full artwork';await resetCard(n);updated.push({id:n.id,main:n.mainComponent.id,w:n.width,h:n.height});}
const gallery=await figma.getNodeByIdAsync('99:2091'),galleryPurpose=await figma.getNodeByIdAsync('99:2102'),galleryStatus=await figma.getNodeByIdAsync('99:2103');
editText(galleryPurpose,'Full artwork, floating thumbs · 4 × 3');editText(galleryStatus,'Available');galleryStatus.fills=[paint(primary)];
editText(await figma.getNodeByIdAsync('99:2044'),'Choose a widget to preview and personalize. Pocket Folio and Screening Card are ready. More widgets are coming next.');
for(const id of ['145:2601','I145:2652;145:2601'])editText(await figma.getNodeByIdAsync(id),'These choices apply to this card only. Tap the artwork to open the saved title. Use a thumb to record a preference; tap it again to clear.');
editText(await figma.getNodeByIdAsync('142:2356'),'Empty source: “No saved manga yet” + “Open Media”. One eligible title: keep the available pick visible. Feedback saved: close the sheet, show a brief confirmation, then choose the next eligible pick. Widget thumbs: tap again to clear. Manga: “Your next read”, “Update reading progress”, chapters / volumes only when known.');
gallery.name='Screening Card / available · Approved Artwork A';await gallery.setReactionsAsync([{trigger:{type:'ON_CLICK'},actions:[{type:'NODE',destinationId:'293:5963',navigation:'NAVIGATE',transition:null}]}]);mutated.add(gallery.id);
const setup=await figma.getNodeByIdAsync('142:1965');setup.name='Cobalt / Screening / Approved setup, sources & feedback';mutated.add(setup.id);
const setupHeading=setup.children.find(n=>n.type==='TEXT');if(setupHeading)editText(setupHeading,'Screening Card · the chosen artwork design');
const review=board(reviewName,1256,3368,2240);
label(review,'Screening Card · chosen design','heading',1176);
label(review,'Full artwork, floating thumbs and an optional thin progress edge. This is the current widget design.','body',1176);
const showcase=auto(review,'Chosen launcher and current card states',1176,'HORIZONTAL',48);
const launcherColumn=await figma.getNodeByIdAsync('224:2288');move(launcherColumn,showcase);launcherColumn.name='Chosen Screening Card / Android launcher';
editText(await figma.getNodeByIdAsync('224:2289'),'Chosen · full artwork');editText(await figma.getNodeByIdAsync('224:2290'),'Current Screening Card');
editText(await figma.getNodeByIdAsync('224:2345'),'Floating 48 dp thumbs. Tap the artwork to open the saved title.');
editText(await figma.getNodeByIdAsync('224:2346'),'A thin bottom edge appears only for recorded playback progress.');
const launcherCard=await figma.getNodeByIdAsync('224:2322');launcherCard.name='Screening Card / approved launcher preview';launcherCard.setProperties({'Show progress#227:0':false});mutated.add(launcherCard.id);
const states=auto(showcase,'Reversible feedback and honest progress',720,'VERTICAL',24);
const stateInstances=[];let stateRow=null;
for(const [index,spec]of [{label:'Neutral · no playback progress',state:'Neutral',show:false,note:'Both choices stay available.'},{label:'Liked · reversible feedback',state:'Liked',show:false,note:'The selected thumb fills in.'},{label:'Not for me · reversible feedback',state:'Disliked',show:false,note:'The card remains visible after a vote.'},{label:'Recorded playback · thin edge',state:'Neutral',show:true,note:'Illustrative 46% playback. No percentage on the card.'}].entries()){
if(index%2===0)stateRow=auto(states,'Card states / row '+(index/2+1),720,'HORIZONTAL',48);
const column=auto(stateRow,spec.label,336,'VERTICAL',12);label(column,spec.label,'section');
const instance=chosen.createInstance();created.add(instance.id);column.appendChild(instance);instance.setProperties({'Show progress#227:0':spec.show});
const feedback=instance.findAllWithCriteria({types:['INSTANCE']}).find(n=>n.mainComponent?.parent?.id==='224:2196');feedback.setProperties({Layout:'Compact',State:spec.state});mutated.add(feedback.id);
label(column,spec.note,'meta');stateInstances.push({id:instance.id,state:spec.state,progress:spec.show});
}
label(review,'The card always uses the same layout. Feedback and real playback data change its state. All sample content here is illustrative.','body',1176);
const assets=board(assetsName,1256,4784,2240);
label(assets,'Approved Screening components','heading',1176);
label(assets,'Edit this Artwork A master. The other card directions are preserved in the concepts archive.','body',1176);
const assetRow=auto(assets,'Current card and reusable feedback',1176,'HORIZONTAL',48);
const masterColumn=auto(assetRow,'Approved card master and playback edge',336,'VERTICAL',16);label(masterColumn,'Screening Card · approved master','section');move(chosen,masterColumn);label(masterColumn,'Default: no playback edge. Title and metadata stay on one line.','meta');label(masterColumn,'Optional tracked-playback edge','section');move(progress,masterColumn);
const feedbackColumn=auto(assetRow,'Reusable preference controls',484,'VERTICAL',16);label(feedbackColumn,'Feedback controls','section',484);move(await figma.getNodeByIdAsync('224:2087'),feedbackColumn);label(feedbackColumn,'Compact variants are used in the widget. Labelled variants remain available for app controls.','body',484);
const feedbackSet=await figma.getNodeByIdAsync('224:2196');feedbackSet.name='Cobalt / Screening / Reversible preference controls';feedbackSet.description='Compact: current widget thumbs. Labelled: app feedback controls. Neutral, Liked and Disliked states remain reversible; prototype transitions stay within the set.';mutated.add(feedbackSet.id);
const archiveX=Math.max(...page.children.filter(n=>![review.id,assets.id].includes(n.id)).map(n=>n.x+n.width))+160;
const archive=board(archiveName,3120,archiveX,160);
label(archive,'Screening Card · concepts archive','heading',3040);
label(archive,'Earlier directions are kept here for reference. The full-artwork card with floating thumbs is the chosen design.','body',3040);
const archiveRow=auto(archive,'Archived launcher directions and refinements',3040,'HORIZONTAL',80);
const older=auto(archiveRow,'Four earlier launcher concepts',1664,'VERTICAL',32);const refinements=auto(archiveRow,'Unselected artwork refinements',1256,'VERTICAL',32);
const legacyArea=auto(older,'Early Screening Card direction',1664,'VERTICAL',16);label(legacyArea,'Early poster-and-details card · retired','section',1664);
const legacyRow=auto(legacyArea,'Original launcher and retired master',1664,'HORIZONTAL',48);
const initial=await figma.getNodeByIdAsync('77:1833'),retired=await figma.getNodeByIdAsync('77:1214');move(initial,legacyRow);move(retired,legacyRow);initial.name='Archived / Screening Card / initial launcher concept';retired.name='Archived / Screening Card / poster + details';retired.description='Archived initial card with separate poster and details/actions. Superseded by the approved full-artwork card at 224:2210. Kept only as a historical concept.';mutated.add(retired.id);
for(const id of ['217:1729','217:1733']){const n=await figma.getNodeByIdAsync(id);move(n,older);n.name='Archived / '+n.name;mutated.add(n.id);}
for(const id of ['224:2082','224:2085']){const n=await figma.getNodeByIdAsync(id);move(n,refinements);n.name='Archived / '+n.name;mutated.add(n.id);}
editText(await figma.getNodeByIdAsync('224:2083'),'Archived · artwork alternatives');editText(await figma.getNodeByIdAsync('224:2084'),'The caption rail and episode-caption directions were not chosen. Current Artwork A is in the approved Screening board.');
editText(await figma.getNodeByIdAsync('224:2086'),'Archived refinement components');
for(const id of ['217:1753','217:1772','217:1790','217:1826','224:2233','224:2258','227:2509']){const n=await figma.getNodeByIdAsync(id);n.name='Archived / '+n.name;n.description='Archived alternative or earlier snapshot. Use the approved Artwork A card at 224:2210 for current Screening screens.';mutated.add(n.id);}
const archivedLiveIds=['77:1851','217:1880','217:1926','217:1971','217:2033','224:2381','224:2442'];
for(const id of archivedLiveIds){const n=await figma.getNodeByIdAsync(id);n.name='Archived preview / '+n.name.replace(/live concept instance|live refinement/g,'earlier direction');mutated.add(n.id);}
const otherBoard=await figma.getNodeByIdAsync('77:1320');otherBoard.name='Cobalt / Android widgets / Other concept directions';mutated.add(otherBoard.id);
editText(await figma.getNodeByIdAsync('77:1324'),'Four other home-screen concepts. The chosen Screening Card is in the approved Screening board.');editText(await figma.getNodeByIdAsync('77:1325'),'OTHER ANDROID WIDGETS\nCONCEPTS / 01–03, 05');
for(const id of ['92:2228','142:2004','142:2085','142:2152','142:2223','145:2631'])await fitStatusRow(await figma.getNodeByIdAsync(id));
const morningStatusSpacer=await figma.getNodeByIdAsync('142:2291');if(morningStatusSpacer)await fitStatusRow(morningStatusSpacer.parent);
await Promise.all(styleWrites);for(const n of [review,assets,archive])n.placeholder=false;
const checks=await validateCreation([{nodeId:chosen.id,expectedType:'COMPONENT',expectedName:'Cobalt / Widgets / Screening Card · Approved Artwork A'},{nodeId:feedbackSet.id,expectedType:'COMPONENT_SET',expectedChildCount:6},{nodeId:review.id,expectedType:'FRAME',expectedName:reviewName},{nodeId:assets.id,expectedType:'FRAME',expectedName:assetsName},{nodeId:archive.id,expectedType:'FRAME',expectedName:archiveName}]);
function insideArchive(n){let x=n;while(x&&x.type!=='PAGE'){if(x.id===archive.id)return true;x=x.parent;}return false;}
const legacyUses=page.findAllWithCriteria({types:['INSTANCE']}).filter(n=>n.mainComponent?.id==='77:1214').map(n=>({id:n.id,archived:insideArchive(n)}));
const currentCards=page.findAllWithCriteria({types:['INSTANCE']}).filter(n=>n.mainComponent?.id===chosen.id&&!insideArchive(n)).map(n=>({id:n.id,w:n.width,h:n.height,hasArtwork:Array.isArray(n.fills)&&n.fills.some(p=>p.type==='IMAGE')}));
figma.viewport.scrollAndZoomIntoView([review]);
return{createdNodeIds:[...created],mutatedNodeIds:[...mutated],reviewId:review.id,assetsId:assets.id,archiveId:archive.id,chosenCardId:chosen.id,properties:{title:titleKey,metadata:metadataKey,progress:'Show progress#227:0'},updatedInstances:updated,stateInstances,legacyUses,currentCards,boards:[review,assets,archive].map(n=>({id:n.id,name:n.name,x:n.x,y:n.y,w:n.width,h:n.height})),checks};
