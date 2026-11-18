// Refinements of the user's edited Artwork concept. Editable Figma layers and prototype toggles.
await figma.setCurrentPageAsync(await figma.getNodeByIdAsync('77:915'));
figma.skipInvisibleInstanceChildren=false;
const page=figma.currentPage;
const name='Cobalt / Screening / Artwork, feedback & honest progress';
if(page.children.some(n=>n.name===name))throw new Error('Refinement exists; edit returned IDs.');
const original=await figma.getNodeByIdAsync('217:1880');
const phoneSource=await figma.getNodeByIdAsync('217:1853');
const artworkAsset=await figma.getNodeByIdAsync('222:2082');
const styles=new Map((await figma.getLocalTextStylesAsync()).map(s=>[s.name,s]));
await Promise.all([...styles.values()].filter(s=>s.name.startsWith('Cobalt / Widgets')).map(s=>figma.loadFontAsync(s.fontName)));
await Promise.all(phoneSource.findAllWithCriteria({types:['TEXT']}).flatMap(t=>t.getStyledTextSegments(['fontName']).map(s=>figma.loadFontAsync(s.fontName))));
const vars=new Map();
for(const suffix of ['2183','2184','2185','2186','2187','2188','2189','2190','2191','2192']){const v=await figma.variables.getVariableByIdAsync('VariableID:92:'+suffix);vars.set(v.name.split('/')[1],v);}
const up=await figma.getNodeByIdAsync('47:1255'),down=await figma.getNodeByIdAsync('47:1319');
function paint(role,n,opacity=1){const v=vars.get(role),c=v.resolveForConsumer(n||original).value;return figma.variables.setBoundVariableForPaint({type:'SOLID',color:{r:c.r,g:c.g,b:c.b},opacity},'color',v);}
function fixed(n,w,h){n.resize(w,h);n.layoutSizingHorizontal='FIXED';n.layoutSizingVertical='FIXED';return n;}
function frame(parent,name,w,gap=0,role=null,direction='VERTICAL'){const n=figma.createAutoLayout(direction);n.name=name;if(parent)parent.appendChild(n);n.resize(w,1);n.layoutSizingHorizontal='FIXED';n.layoutSizingVertical='HUG';n.itemSpacing=gap;n.fills=role?[paint(role,n)]:[];n.clipsContent=false;return n;}
function text(parent,value,style='Metadata',role='text',width=304){const t=figma.createText();t.name=value;t.textStyleId=styles.get('Cobalt / Widgets / '+style).id;t.characters=value;parent.appendChild(t);t.resize(width,1);t.textAutoResize='HEIGHT';t.fills=[paint(role,t)];return t;}
function tint(n,role){for(const v of n.findAllWithCriteria({types:['VECTOR']})){if(v.isMask)continue;for(const key of ['fills','strokes'])if(Array.isArray(v[key]))v[key]=v[key].map(p=>p.type==='SOLID'&&!(p.color.r>.95&&p.color.g>.95&&p.color.b>.95)?paint(role,v):p);}}
const right=Math.max(...page.children.filter(n=>n.id!==artworkAsset.id).map(n=>n.x+n.width));
const board=frame(page,name,1256,22,'board');board.x=right+200;board.y=160;board.paddingLeft=40;board.paddingRight=40;board.paddingTop=36;board.paddingBottom=36;
text(board,'Your artwork, with a useful second layer.','Editorial / 27-34 Medium','boardInk',1176);
text(board,'Keep the image open. Put reversible thumbs near the caption. Show progress only when it means something.','Review / Note','boardInk',1176);
const shelf=frame(page,'Screening / Feedback states & refinement main components',1256,24,'board');shelf.x=board.x;shelf.y=1600;shelf.paddingLeft=40;shelf.paddingRight=40;shelf.paddingTop=28;shelf.paddingBottom=28;
text(shelf,'Reusable feedback · neutral, liked, not for me','Editorial / 23-30 Regular','boardInk',1176);
const variantsHost=figma.createFrame();shelf.appendChild(variantsHost);variantsHost.name='Feedback variant grid';variantsHost.resize(484,232);variantsHost.fills=[];
const variants=[],targets=new Map();
for(const layout of ['Compact','Labelled'])for(const [i,state] of ['Neutral','Liked','Disliked'].entries()){
  const c=figma.createComponent();variantsHost.appendChild(c);c.name='Layout='+layout+', State='+state;c.description='Mutually exclusive recommendation preference. Tap selected thumb to clear; tap opposite thumb to replace. Does not mark watched or create a star rating.';
  c.layoutMode='HORIZONTAL';c.itemSpacing=layout==='Compact'?4:8;c.counterAxisAlignItems='CENTER';
  fixed(c,layout==='Compact'?108:304,layout==='Compact'?56:48);c.x=layout==='Compact'?0:156;c.y=i*80;
  c.paddingLeft=layout==='Compact'?4:0;c.paddingRight=c.paddingLeft;c.paddingTop=c.paddingLeft;c.paddingBottom=c.paddingLeft;c.cornerRadius=28;
  c.fills=layout==='Compact'?[paint('surface',c,.98)]:[];
  if(layout==='Compact'){c.strokes=[paint('outline',c,.35)];c.strokeWeight=1;}
  const buttons=[];
  for(const [which,label,iconMaster] of [['up','Like',up],['down','Not for me',down]]){
    const selected=which==='up'?state==='Liked':state==='Disliked';
    const b=frame(c,label+' / 48 dp target',layout==='Compact'?48:148,8,null,'HORIZONTAL');fixed(b,layout==='Compact'?48:148,48);b.primaryAxisAlignItems='CENTER';b.counterAxisAlignItems='CENTER';b.cornerRadius=24;b.fills=selected?[paint(layout==='Compact'?'primary':'selected',b)]:[];
    const glyph=iconMaster.createInstance();b.appendChild(glyph);glyph.resize(layout==='Compact'?22:18,layout==='Compact'?22:18);tint(glyph,selected&&layout==='Compact'?'onPrimary':'primary');
    if(layout==='Labelled'){const t=text(b,label,'Action','primary',96);t.textAutoResize='WIDTH_AND_HEIGHT';}
    buttons.push({node:b,which});
  }
  variants.push(c);targets.set(layout+':'+state,{component:c,buttons});
}
const feedbackSet=figma.combineAsVariants(variants,variantsHost);feedbackSet.name='Cobalt / Screening / Recommendation preference';feedbackSet.description='Two layouts × three reversible states. Prototype transitions stay within this component set.';
feedbackSet.x=0;feedbackSet.y=0;
for(const [key,value] of targets){const [layout,state]=key.split(':');for(const b of value.buttons){const next=b.which==='up'?(state==='Liked'?'Neutral':'Liked'):(state==='Disliked'?'Neutral':'Disliked');await b.node.setReactionsAsync([{trigger:{type:'ON_CLICK'},actions:[{type:'NODE',destinationId:targets.get(layout+':'+next).component.id,navigation:'CHANGE_TO',transition:{type:'DISSOLVE',easing:{type:'EASE_OUT'},duration:.15}}]}]);}}
const masters=frame(shelf,'Three refinement main components',1176,56,null,'HORIZONTAL');masters.counterAxisAlignItems='MIN';
function main(name,h,image=true){const n=figma.createComponent();masters.appendChild(n);n.name='Cobalt / Screening artwork / '+name;n.layoutMode='VERTICAL';fixed(n,336,h);n.paddingTop=16;n.paddingBottom=16;n.paddingLeft=16;n.paddingRight=16;n.itemSpacing=8;n.primaryAxisAlignItems='MAX';n.cornerRadius=28;n.clipsContent=true;n.fills=image?JSON.parse(JSON.stringify(original.fills)):[paint('surface',n)];return n;}
function overlay(parent,h,sourceFill=null){const r=figma.createRectangle();parent.appendChild(r);r.name='Editable image contrast';r.layoutPositioning='ABSOLUTE';r.resize(336,h);r.x=0;r.y=0;r.constraints={horizontal:'STRETCH',vertical:'STRETCH'};
  if(sourceFill)r.fills=JSON.parse(JSON.stringify(sourceFill));else{const c=vars.get('surface').resolveForConsumer(parent).value;r.fills=[{type:'GRADIENT_LINEAR',gradientTransform:[[0,1,0],[-1,0,1]],gradientStops:[{position:0,color:{r:c.r,g:c.g,b:c.b,a:0}},{position:.55,color:{r:c.r,g:c.g,b:c.b,a:.08}},{position:1,color:{r:c.r,g:c.g,b:c.b,a:1}}]}];}return r;}
function feedback(parent,layout,state='Neutral'){const n=targets.get(layout+':'+state).component.createInstance();parent.appendChild(n);n.name='Like / Not for me · reversible';return n;}
function captionRow(parent,title,meta,titleStyle='Editorial / 27-34 Medium'){
  const row=frame(parent,'Caption and reachable feedback',304,16,null,'HORIZONTAL');fixed(row,304,56);row.counterAxisAlignItems='CENTER';
  const copy=frame(row,'Open this saved title',180,4);text(copy,title,titleStyle,'text',180);text(copy,meta,'Caption','text',180);feedback(row,'Compact');return row;
}
const shadeFill=original.children.find(n=>n.type==='RECTANGLE').fills;
const floating=main('A / Floating feedback',264);floating.description='Recommended refinement of the user edit. Keeps unlabelled artwork, short caption, two 48 dp thumbs beside it. Feedback remains visible and reversible; opening the image/title enters details.';
overlay(floating,264,shadeFill);captionRow(floating,'Your Name.','Anime film · 1h 46m');
const rail=main('B / Caption rail',288);rail.description='Larger labelled feedback treatment. Like means interest in this recommendation; Not for me means exclude this title from future recommendations. Title/image open details.';
overlay(rail,288,shadeFill);const railCopy=frame(rail,'Tappable saved title',304,4);text(railCopy,'Your Name.','Editorial / 27-34 Medium');text(railCopy,'Anime film · 1h 46m','Metadata','text');
const divider=figma.createRectangle();rail.appendChild(divider);divider.name='Caption rail divider';divider.resize(304,1);divider.fills=[paint('outline',divider,.3)];feedback(rail,'Labelled');
const progress=main('C / Continue with progress',296,false);progress.description='Optional state when tracked episode progress is valid. Mock example: 12 of 26 watched, not playback percentage. Progress is a readout, not a seek slider. Hide when counts are unknown or inconsistent.';
const art=figma.createFrame();progress.appendChild(art);art.name='Cowboy Bebop / discrete promotional art';art.layoutPositioning='ABSOLUTE';art.resize(336,188);art.x=0;art.y=0;art.fills=JSON.parse(JSON.stringify(artworkAsset.fills));
overlay(progress,188);
const progressCaption=frame(progress,'Continue caption and episode readout',304,10);
captionRow(progressCaption,'Cowboy Bebop','Anime · Continue watching','Editorial / 25-30 Regular');
const count=frame(progressCaption,'Actual episode progress',304,6);text(count,'12 of 26 episodes watched','Metadata','muted');
const track=figma.createFrame();count.appendChild(track);track.name='Watched / released episodes · 12/26';track.resize(304,4);track.cornerRadius=2;track.fills=[paint('outline',track,.3)];
const fill=figma.createRectangle();track.appendChild(fill);fill.name='Progress · 46.15 percent';fill.resize(304*12/26,4);fill.cornerRadius=2;fill.fills=[paint('primary',fill)];
const lanes=frame(board,'A–C / artwork refinements',1176,48,null,'HORIZONTAL');lanes.counterAxisAlignItems='MIN';
const info=[
  {name:'A  Floating feedback',tag:'Recommended · the image stays first',main:floating,note:'Two easy 48 dp targets beside the caption. A selected thumb fills in; tap it again to undo.',reason:'Shorter metadata keeps the title legible. No header or change-pick control added back.'},
  {name:'B  Caption rail',tag:'Clear labels · easier to learn',main:rail,note:'Like and Not for me sit in a quiet lower rail. Useful when icon-only controls feel ambiguous.',reason:'Uses a little more height, with the same artwork and direct title entry.'},
  {name:'C  Continue',tag:'Optional · episode progress only',main:progress,note:'A thin bar and a real count answer how far you are. The example uses mock viewing progress.',reason:'Only appears with reliable TV/anime counts. No movie playback or manga percentage inferred.'}
];
const phones=[];
for(const c of info){const lane=frame(lanes,c.name,360,10);text(lane,c.name,'Editorial / 23-30 Regular','boardInk',360);text(lane,c.tag,'Review / Note','boardInk',360);
  const phone=phoneSource.clone();lane.appendChild(phone);phone.name=c.name+' / Android launcher';
  const old=phone.children.find(n=>n.type==='INSTANCE'&&n.name==='Artwork / live concept instance');old.remove();
  const placed=c.main.createInstance();phone.appendChild(placed);placed.x=12;placed.y=160;placed.name=c.name+' / live refinement';
  text(lane,c.note,'Review / Behavior','boardInk',360);text(lane,c.reason,'Review / Note','boardInk',360);
  phones.push({launcherId:phone.id,widgetInstanceId:placed.id,mainComponentId:c.main.id,width:placed.width,height:placed.height});
}
text(board,'Feedback is a preference, not proof of watching. Keep the card visible after a vote so users can change their mind.','Review / Behavior','boardInk',1176);
const stateRow=frame(board,'Feedback states',1176,28,null,'HORIZONTAL');
for(const [state,label] of [['Neutral','No preference'],['Liked','Liked · tap again to clear'],['Disliked','Not for me · tap again to clear']]){const cell=frame(stateRow,label,360,8);feedback(cell,'Compact',state);text(cell,label,'Review / Note','boardInk',360);}
text(board,'Research direction: a personal poster, a useful shortcut, and reliable state. A small daily ritual earns space better than a miniature dashboard.','Review / Note','boardInk',1176);
shelf.y=board.y+board.height+120;
const assetSlot=frame(shelf,'Discrete artwork source · Sunrise promotional visual',1176,8);text(assetSlot,'Artwork source for Continue example · viewing counts are mock data','Review / Note','boardInk',1176);assetSlot.appendChild(artworkAsset);artworkAsset.name='Cowboy Bebop / source crop · Sunrise';artworkAsset.resize(400,116);
figma.viewport.scrollAndZoomIntoView([board]);
const nodes=[board,...board.findAll(()=>true),shelf,...shelf.findAll(()=>true)];const counts={};for(const n of nodes)counts[n.type]=(counts[n.type]||0)+1;
const fonts=[...new Set(nodes.filter(n=>n.type==='TEXT').flatMap(t=>t.getStyledTextSegments(['fontName']).map(s=>s.fontName.family)))];
if(fonts.some(f=>!['Roboto','Source Serif 4'].includes(f)))throw new Error('Unexpected font family');
const images=nodes.filter(n=>Array.isArray(n.fills)&&n.fills.some(p=>p.type==='IMAGE')).map(n=>({id:n.id,name:n.name,type:n.type,width:n.width,height:n.height}));
return {boardId:board.id,shelfId:shelf.id,feedbackComponentSetId:feedbackSet.id,concepts:phones,createdNodeIds:nodes.filter(n=>n.id!==artworkAsset.id).map(n=>n.id),mutatedNodeIds:[page.id,artworkAsset.id],nodeCounts:counts,imageNodes:images,fontFamilies:fonts,prototypeTransitions:12,originalUserEditId:original.id,preservedUserEdit:true};
