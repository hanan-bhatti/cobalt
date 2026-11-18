// Authoring snapshot from this Figma batch. See README.md before reuse.
(async function(start,count,tag){
const st=load("remainingBatchState"),items=load("remainingTasteCloneSpecs").slice(start,start+count);
const code="const ITEMS="+JSON.stringify(items)+";\nconst CONFIG="+JSON.stringify({parentId:st.boards.widgets,...(st.boards.taste?{laneId:st.boards.taste}:{})})+";\n"+
"const STYLES="+JSON.stringify({caption:st.styleMap["label-large"].id,title:st.styleMap["headline-medium"].id,body:st.styleMap["body-large"].id})+";\n"+
String.raw`await figma.setCurrentPageAsync(await figma.getNodeByIdAsync('369:76'));
const fonts=new Map(),sources=await Promise.all(ITEMS.map(i=>figma.getNodeByIdAsync(i.sourceId)));
for(const n of sources)for(const t of n.query('TEXT').toArray())for(const s of t.getStyledTextSegments(['fontName']))fonts.set(JSON.stringify(s.fontName),s.fontName);await Promise.all([...fonts.values()].map(f=>figma.loadFontAsync(f)));
const ink=$fig.getVar('VariableID:146:953'),muted=$fig.getVar('VariableID:146:954');
let lane=CONFIG.laneId?$fig.get(CONFIG.laneId):$fig.autoLayout({name:'Screening · Approved taste subpages',width:2400,layoutMode:'VERTICAL',itemSpacing:24,fills:[]});
if(!CONFIG.laneId){$fig.get(CONFIG.parentId).append(lane);lane.append($fig.text({characters:'Screening · Approved taste subpages',width:2400,textStyle:$fig.getStyle(STYLES.title),fills:[{type:'SOLID',color:ink}]}),$fig.text({characters:'Reused approved Your taste, review-pick, feedback and evidence views. Example titles and scoring records remain illustrative.',width:2400,textStyle:$fig.getStyle(STYLES.body),fills:[{type:'SOLID',color:muted}]}));}
const row=$fig.autoLayout({name:'Approved subpages',width:2400,layoutMode:'HORIZONTAL',itemSpacing:24,counterAxisAlignItems:'MIN',fills:[]});lane.append(row);const clones=[];
for(let i=0;i<ITEMS.length;i++){const item=ITEMS[i],wrap=$fig.autoLayout({name:item.title,width:448,layoutMode:'VERTICAL',itemSpacing:12,fills:[]});row.append(wrap);wrap.append($fig.text({characters:item.title,width:448,textStyle:$fig.getStyle(STYLES.caption),fills:[{type:'SOLID',color:muted}]}));const n=$fig.clone(item.sourceId,{name:item.key});wrap.append(n);clones.push(n);}
await $fig.done();
const screens={},links=[],created=[];
for(let i=0;i<ITEMS.length;i++){const item=ITEMS[i],n=clones[i].node,old=sources[i],oldChildren=old.query('*').toArray(),newChildren=n.query('*').toArray();screens[item.key]={id:n.id,sourceId:item.sourceId,title:item.title,kind:'page',theme:'Dark',width:448,height:997,reusedApproved:true};created.push(n.id,...n.query('*').values(['id']).map(v=>v.id));for(let k=0;k<oldChildren.length;k++){const o=oldChildren[k],c=newChildren[k];if(!c||!('reactions'in o))continue;for(const r of o.reactions){const a=r.action||r.actions?.[0];if(a?.destinationId)links.push({id:c.id,targetSourceId:a.destinationId,sourceNavigation:a.navigation});}}}
const result={laneId:lane.node.id,screens,links,createdNodeIds:created,mutatedNodeIds:[CONFIG.parentId,lane.node.id]};if(JSON.stringify(result).length>47000)throw new Error('Approved clone batch exceeds output limit');return result;`;
store(tag+"Script",code);
const r=await tools.mcp__figma__use_figma({fileKey:"dJOugcd2V82nQUA1MjyINI",sessionId:"c92f457e-fac4-4dfb-a4d0-8293e8b176ca",description:"Include approved Screening subpages · "+items.map(i=>i.title).join(", ").slice(0,145),skillNames:"figma-use,figma-generate-design",code});store(tag+"Result",r);if(r.isError){text(r);return false;}const d=JSON.parse(r.content.find(c=>c.type==="text").text);store(tag+"Data",d);Object.assign(st.screens,d.screens);st.boards.taste=d.laneId;st.approvedLinks??=[];st.approvedLinks.push(...d.links);st.createdNodeIds.push(...d.createdNodeIds);store("remainingBatchState",st);text({cloned:items.length,keys:Object.keys(d.screens),nodes:d.createdNodeIds.length});return true;
})
