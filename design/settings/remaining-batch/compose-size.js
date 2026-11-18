// Authoring snapshot from this Figma batch. See README.md before reuse.
await figma.setCurrentPageAsync(await figma.getNodeByIdAsync('369:76'));
const headers=POST.headers.map(({id,...h})=>({...h,n:$fig.get(id)}));
const sheets=POST.sheets.map(({sheetId,bodyId,...v})=>({...v,s:$fig.get(sheetId),body:$fig.get(bodyId)}));
const dialogs=POST.dialogs.map(({id,...d})=>({...d,n:$fig.get(id)}));
for(const h of headers){const n=h.n.node;if(!h.spec.compact){const hb=n.query('FRAME[name="Header body"]').first(),copy=n.query('FRAME[name="Page title & subtitle"]').first();if(hb&&copy){const bh=Math.max(160,copy.y+copy.height+24);$fig.get(hb.id).set({height:bh,width:h.W});h.n.set({height:bh+53});}}}
for(const v of sheets){const n=v.s.node,max=v.H-112;if(n.height>max){const bh=Math.max(160,max-(n.height-v.body.node.height));v.body.set({height:bh,layoutSizingVertical:'FIXED',clipsContent:true,overflowDirection:'VERTICAL'});v.s.set({height:max,layoutSizingVertical:'FIXED'});}v.s.set({y:v.H-Math.min(n.height,max)});}
for(const d of dialogs)d.n.set({x:(d.W-d.n.node.width)/2,y:(d.H-d.n.node.height)/2});
await $fig.done();
return {mutatedNodeIds:[...POST.headers.map(h=>h.id),...POST.sheets.map(s=>s.sheetId),...POST.dialogs.map(d=>d.id)]};
