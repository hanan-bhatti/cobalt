// Authoring snapshot from this Figma batch. See README.md before reuse.
(async function runBatch(moduleName,start,count,laneKey,tag,copy={}){
const st=load("remainingBatchState"),m=load("remainingModules").find(m=>m.name===moduleName),specs=m.screens.slice(start,start+count);
const parents=new Set(specs.map(s=>s.parent).filter(Boolean));
for(const theme of["Dark","Light"]){const match=Object.entries(st.screens).find(([k,v])=>v.theme===theme);if(match)parents.add(match[0]);}
const slim={boardId:st.boardId,assetBoardId:st.assetBoardId,iconMap:st.iconMap,roleIds:st.roleIds,styleMap:Object.fromEntries(Object.entries(st.styleMap).map(([k,v])=>[k,{id:v.id}])),screens:Object.fromEntries(Object.entries(st.screens).filter(([k])=>parents.has(k)).map(([k,v])=>[k,{id:v.id,theme:v.theme}]))};
const pre="const CONFIG="+JSON.stringify({name:m.name,description:m.description,...(st.boards[laneKey]?{laneId:st.boards[laneKey]}:{})})+";\nconst SCREENS="+JSON.stringify(specs)+";\nconst STATE="+JSON.stringify(slim)+";\nconst COPY="+JSON.stringify(copy)+";\n";
const args={fileKey:"dJOugcd2V82nQUA1MjyINI",sessionId:"c92f457e-fac4-4dfb-a4d0-8293e8b176ca",skillNames:"figma-use,figma-generate-design"};
const code=pre+load("remainingComposeEnginePlanBuild");if(code.length>50000)throw new Error("Batch code exceeds limit "+code.length);store(tag+"Script",code);
const r=await tools.mcp__figma__use_figma({...args,description:"Compose "+moduleName+" · "+specs.map(s=>s.caption||s.title).join(", ").slice(0,160),code});store(tag+"Result",r);if(r.isError){text(r);return false;}const d=JSON.parse(r.content.find(c=>c.type==="text").text);store(tag+"Data",d);Object.assign(st.screens,d.screens);st.links.push(...d.links);st.createdNodeIds.push(...d.createdNodeIds);st.boards[laneKey]=d.laneId;store("remainingBatchState",st);
const postCode=pre+"const POST="+JSON.stringify(d.post)+";\n"+load("remainingComposeEnginePlanPost");if(postCode.length>50000)throw new Error("Post code exceeds limit "+postCode.length);store(tag+"PostScript",postCode);
const p=await tools.mcp__figma__use_figma({...args,description:"Finish native controls and Cobalt typography · "+moduleName,code:postCode});store(tag+"PostResult",p);if(p.isError){text(p);return false;}const pd=JSON.parse(p.content.find(c=>c.type==="text").text);st.links.push(...pd.links);store("remainingBatchState",st);
const z=await tools.mcp__figma__use_figma({...args,description:"Finalize scrolling and sheet geometry · "+moduleName,code:"const POST="+JSON.stringify(d.post)+";\n"+load("remainingComposeEnginePlanSize")});store(tag+"SizeResult",z);if(z.isError){text(z);return false;}
text({module:moduleName,views:Object.keys(d.screens),laneId:d.laneId,nodes:d.createdNodeIds.length});return true;
})
