// Run per correction state, after the three detail ledgers have been merged into sheets.
Object.assign(sheets,detailMap);
const previews={Current:{overview:'293:2915',why:'293:3076',feedback:'293:3169'},Ignored:{overview:'293:3529',why:'293:3687',feedback:'293:3779'},Reset:{overview:'293:3967',why:'293:4127',feedback:'293:4219'}};
const built=[];
async function finishPhone(n){const viewport=n.children.find(c=>c.name==='Read-only insights content');if(viewport){const occupied=n.children.filter(c=>c.visible&&c.id!==viewport.id&&c.layoutPositioning!=='ABSOLUTE').reduce((sum,c)=>sum+c.height,0);viewport.resize(416,n.height-n.paddingTop-n.paddingBottom-occupied);viewport.layoutSizingVertical='FIXED';mutated.add(viewport.id);}return n;}
async function build(n,type,state,empty=false){let content;
  if(type==='search'){content=await searchPhone(n,state);}
  else{const title=type==='overview'?'Your taste':type==='why'?'Why this pick?':type==='feedback'?'Your feedback':'Other genres';const subtitle=type==='overview'?'A little feedback. Better picks for you.':type==='why'?'The reasons behind this suggestion.':type==='feedback'?'What shapes your next pick.':'Every genre stays part of your taste.';
    content=await phone(n,title,subtitle,type==='overview'?null:ids.overview[state]);
    if(type==='overview')await overview(content,state,empty);else if(type==='why')await why(content,state);else if(type==='feedback')await feedback(content,state);else{const chart=record((await figma.getNodeByIdAsync(otherMasterIds[state])).createInstance());content.appendChild(chart);for(const row of chart.findAll(x=>x.type==='FRAME'&&x.name.startsWith('Preference / ')))route(row,sheets.genre[state][row.name.replace('Preference / ','')],'OVERLAY');}}
  await finishPhone(n);built.push({id:n.id,type,state,contentHeight:content.height});return n;}
if(mainMode==='state')for(const state of mainStates){for(const type of ['overview','why','feedback','others','search'])await build(await figma.getNodeByIdAsync(ids[type][state]),type,state);
  for(const type of ['overview','why','feedback'])await build(await figma.getNodeByIdAsync(previews[state][type]),type,state);
  if(state==='Current'){const k=Object.keys(masters.score.componentPropertyDefinitions).find(k=>k.startsWith('Points#'));masters.score.editComponentProperty(k,{defaultValue:points(calculation('Current').raw)});mutated.add(masters.score.id);}}
if(mainMode==='extras'){
  await build(await figma.getNodeByIdAsync('322:5644'),'others','Current');for(const id of ['293:3454','293:4782'])await build(await figma.getNodeByIdAsync(id),'overview','Current',true);
  for(const id of ['293:3358','293:5580']){const n=await figma.getNodeByIdAsync(id);await build(n,'overview','Current');const overlay=record((await figma.getNodeByIdAsync(sheets.genre.Current.Thriller)).clone());n.appendChild(overlay);overlay.name='Thriller · compact evidence overlay';overlay.layoutPositioning='ABSOLUTE';overlay.x=0;overlay.y=0;await overlay.setReactionsAsync([]);mutated.add(overlay.id);route(overlay.findOne(x=>x.name==='Close details'),ids.overview.Current);}
}
return await finish({built,mainMode,mainStates});
