// Append to figma-common.js. Reconcile only the changed insight component family.
const shelf=await figma.getNodeByIdAsync('293:2532');
if(shelf.children.some(n=>n.name==='Current design · compact insight cards'))throw new Error('Compact components already exist; read the ledger before retrying.');
const board=await figma.getNodeByIdAsync('293:2528');board.placeholder=true;mutated.add(board.id);
function profile(state){const records=[...(state==='Ignored'?[]:[{g:['Thriller','Crime'],r:3.5/4.5,a:1}]),
  {g:['Science fiction','Drama'],r:3.5/4.5,a:1},{g:['Drama'],r:1.5/4.5,a:1},{g:['Comedy'],r:-1.5/4.5,a:1},
  ...(state==='Reset'?[]:[{g:['Thriller'],r:1,a:.325},{g:['Horror'],r:-1,a:.65}]),{g:['Drama'],r:.15,a:.5}];
  return ['Thriller','Crime','Science fiction','Drama','Comedy','Horror','Adventure'].map(label=>{const e=records.filter(r=>r.g.includes(label)),mass=e.reduce((s,r)=>s+r.a,0);return{label,count:e.length,value:mass?e.reduce((s,r)=>s+r.r*r.a,0)/(3+mass):null};})
    .sort((a,b)=>Number(b.value!==null)-Number(a.value!==null)||Math.abs(b.value??0)-Math.abs(a.value??0)||b.count-a.count||a.label.toLowerCase().localeCompare(b.label.toLowerCase()));}
async function genreRow(parent,e){const row=auto(parent,'Preference / '+e.label,416,'VERTICAL',12);padding(row,20);row.paddingTop=row.paddingBottom=16;row.setBoundVariable('paddingTop',tokens.s16);row.setBoundVariable('paddingBottom',tokens.s16);
  const labels=auto(row,'Genre, tendency and evidence count',376,'HORIZONTAL',10);labels.counterAxisAlignItems='CENTER';const copy=auto(labels,'Genre hierarchy',272,'VERTICAL',5);
  await text(copy,e.label,'row',272);await text(copy,e.value===null?'No feedback yet':Math.abs(e.value)<.000001?'Balanced feedback':e.value>0?'More often':'Less often','body',272,'muted');
  await text(labels,e.count===0?'No evidence':e.count+' '+(e.count===1?'title':'titles'),'meta',66,'muted');icon(labels,'chevron',18,'muted');
  const plot=record(figma.createFrame());row.appendChild(plot);plot.name='Signed preference bar · '+e.label;plot.resize(376,8);plot.fills=[];plot.clipsContent=false;
  const track=record(figma.createRectangle());plot.appendChild(track);track.resize(376,8);track.cornerRadius=4;track.fills=[paint('outline',.35)];track.name='Preference scale −1 to +1';
  if(e.value!==null&&e.value!==0){const mark=record(figma.createRectangle());plot.appendChild(mark);const length=Math.abs(e.value)*188;mark.resize(Math.max(.01,length),8);mark.x=e.value<0?188-length:188;mark.cornerRadius=4;mark.fills=[paint('primary')];mark.name='Learned weight '+e.value;}
  const zero=record(figma.createRectangle());plot.appendChild(zero);zero.resize(1,8);zero.x=187.5;zero.fills=[paint('muted')];zero.name='Neutral reference';return row;}
const profileIds={Current:'293:2535',Ignored:'293:2622',Reset:'293:2707'},otherIds={Current:'322:4936',Ignored:'322:4941',Reset:'322:4946'};
for(const state of ['Current','Ignored','Reset'])for(const [ids,remaining] of [[profileIds,false],[otherIds,true]]){
  const n=await figma.getNodeByIdAsync(ids[state]);clear(n);padding(n,0);n.itemSpacing=0;n.layoutSizingVertical='HUG';n.fills=[paint('surface')];rounded(n);
  const entries=remaining?profile(state).slice(5):profile(state).slice(0,5);
  for(const [i,e] of entries.entries()){await genreRow(n,e);if(i<entries.length-1)divider(n,416);}
  n.description=remaining?'The remaining individual genres. Others has no aggregate score.':'Five leading genres by absolute preference strength; each opens its evidence.';mutated.add(n.id);}
for(const id of ['293:2792','322:4951']){const n=await figma.getNodeByIdAsync(id);n.resizeWithoutConstraints(n.width,Math.max(...n.children.map(c=>c.height)));mutated.add(n.id);}
const gallery=auto(shelf,'Current design · compact insight cards',2400,'HORIZONTAL',24);shelf.insertChild(2,gallery);
function component(name,width=416,direction='VERTICAL',gap=0){const n=record(figma.createComponent());gallery.appendChild(n);n.name='Cobalt / Screening / Insights / '+name;n.layoutMode=direction;n.resize(width,1);n.layoutSizingHorizontal='FIXED';n.layoutSizingVertical='HUG';n.itemSpacing=gap;n.fills=[paint('surface')];rounded(n);return n;}
function property(owner,node,name){const key=owner.addComponentProperty(name,'TEXT',node.characters);node.componentPropertyReferences={characters:key};return key;}
const hero=component('Media hero');padding(hero,20);hero.itemSpacing=20;hero.setBoundVariable('itemSpacing',tokens.s20);
const heroHead=auto(hero,'Poster and media title',376,'HORIZONTAL',18);heroHead.counterAxisAlignItems='CENTER';poster(heroHead,76,108);
const heroCopy=auto(heroHead,'Media title hierarchy',282,'VERTICAL',10);
property(hero,await text(heroCopy,'Movie','section',282,'primary'),'Kind');
const mediaStyle=figma.createTextStyle();mediaStyle.name='Cobalt / Screening / Insights / Media title';mediaStyle.fontName=styles.row.fontName;mediaStyle.fontSize=16;mediaStyle.lineHeight={unit:'PIXELS',value:22};mediaStyle.letterSpacing={unit:'PIXELS',value:0};
styles.media=mediaStyle;const heroTitle=await text(heroCopy,'Cold Case','media',282);property(hero,heroTitle,'Title');property(hero,await text(heroCopy,'92 min · Plan to Watch','body',282,'muted'),'Facts');
button(hero,'Open saved title',376);hero.description='Saved-media hero with poster fallback, kind, title, concise facts and one primary action. Matches TasteMediaHero.';
const feedback=component('Compact feedback row',416,'HORIZONTAL',14);padding(feedback,16);feedback.counterAxisAlignItems='CENTER';poster(feedback,42,60);
const feedbackCopy=auto(feedback,'Feedback hierarchy',294,'VERTICAL',6);property(feedback,await text(feedbackCopy,'Cold Case','row',294),'Title');property(feedback,await text(feedbackCopy,'Your rating 9/10','body',294,'muted'),'Feedback');
const status=await text(feedbackCopy,'Ignored','meta',294,'muted');const statusProp=feedback.addComponentProperty('Show status','BOOLEAN',false);status.componentPropertyReferences={visible:statusProp};property(feedback,status,'Status');icon(feedback,'chevron',20,'muted');
feedback.description='Compact feedback entry. Source, date, numerical influence and corrections open in a detail sheet. Matches TasteEvidenceRow.';
const reason=component('Reason row',416,'HORIZONTAL',16);padding(reason,20);reason.counterAxisAlignItems='CENTER';
const reasonIcon=auto(reason,'Reason icon',40);reasonIcon.resize(40,40);reasonIcon.layoutSizingVertical='FIXED';reasonIcon.cornerRadius=20;reasonIcon.fills=[paint('outline',.16)];reasonIcon.primaryAxisAlignItems=reasonIcon.counterAxisAlignItems='CENTER';const ri=icon(reasonIcon,'history');
const ip=reason.addComponentProperty('Icon','INSTANCE_SWAP',sources.history.id);ri.componentPropertyReferences={mainComponent:ip};const reasonCopy=auto(reason,'Reason hierarchy',320,'VERTICAL',6);
property(reason,await text(reasonCopy,'Rediscover a saved title','row',320),'Title');property(reason,await text(reasonCopy,'Saved 30 Sep 2026','body',320,'muted'),'Detail');reason.description='One concise, feature-derived reason. At most three rows appear on Why this pick.';
const scoreCard=component('Score disclosure',416,'HORIZONTAL',14);padding(scoreCard,20);scoreCard.counterAxisAlignItems='CENTER';const sc=auto(scoreCard,'Score disclosure copy',264,'VERTICAL',6);
await text(sc,'Ranking points','row',264);await text(sc,'Score details','body',264,'primary');property(scoreCard,await text(scoreCard,'48.65','sheet',64),'Points');icon(scoreCard,'chevron',20,'muted');scoreCard.description='Relative ranking total. Tap for the full contribution breakdown; never an enjoyment percentage.';
const archive=auto(shelf,'Archive · superseded dense insight concepts',2400,'VERTICAL',24);await text(archive,'Archived concepts · earlier dense components','section',2400,'boardInk');
for(const id of ['293:2793','293:2810','293:4361']){const n=await figma.getNodeByIdAsync(id);archive.appendChild(n);mutated.add(n.id);}
const title=shelf.children.find(n=>n.type==='TEXT');title.characters='Current insight components · Android redesign';mutated.add(title.id);mutated.add(shelf.id);
return await finish({components:{hero:hero.id,feedback:feedback.id,reason:reason.id,score:scoreCard.id},mediaStyleId:mediaStyle.id,archiveId:archive.id,genreMasters:profileIds,otherMasters:otherIds,
  layout:{hero:{w:hero.width,h:hero.height},feedback:{w:feedback.width,h:feedback.height},reason:{w:reason.width,h:reason.height},score:{w:scoreCard.width,h:scoreCard.height}}});
