import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';
import assert from 'node:assert/strict';
import {webcrypto} from 'node:crypto';
const asset = name => readFileSync(new URL('../../app/src/main/assets/web/'+name,import.meta.url),'utf8');
function fixture() {
  const elements = new Map();
  const element = id => {
    if (!elements.has(id)) elements.set(id,{value:'',textContent:'',innerHTML:'',disabled:false,hidden:false,open:false,dataset:{},style:{},children:[],
      classList:{toggle(){},add(){},remove(){}},focus(){},replaceChildren(){},append(){},setAttribute(){},getAttribute(){return '';},querySelector(){return element('queried');},querySelectorAll(){return [];},showModal(){this.open=true;},close(){this.open=false;},addEventListener(){},removeEventListener(){}});
    return elements.get(id);
  };
  const context = vm.createContext({console,URL,TextEncoder,TextDecoder,URLSearchParams,AbortController,crypto:webcrypto,navigator:{},document:{addEventListener(){},getElementById:element,createElement:()=>element('created'),querySelector:()=>element('workspace'),querySelectorAll:()=>[]},setInterval:()=>42,clearInterval(){},setTimeout,clearTimeout});
  context.window=context;
  ['markdown.js','highlight.js','markdown-renderer.js','app.js'].forEach(name=>vm.runInContext(asset(name),context));
  const run = code => vm.runInContext(code,context);
  run('toast=()=>{}; renderNote=async()=>{}; loadNotes=async()=>{}; loadFolders=async()=>{}; confirmDiscard=async()=>false;');
  return {context,element,run};
}
test('HTML and MDX remain inert text',()=>{
  const {run}=fixture(),html=run('renderMarkdown("# <script>alert(1)</script>\\n\\n<img src=x onerror=alert(1)>\\n\\n<Component onClick={evil} />")');
  assert.ok(html.includes('&lt;script&gt;')); assert.ok(!html.includes('<script>')&&!html.includes('<img ')); assert.ok(html.includes('&lt;Component'));
});
test('unsafe URLs are rejected and remote images never embed automatically',()=>{
  const {run}=fixture(),html=run('renderMarkdown("[click](javascript:alert%281%29)\\n\\n![remote](https://example.com/track)\\n\\n![local](/api/media/image-id)\\n\\n[x](data:text/html,evil)")');
  assert.ok(!html.includes('href="javascript:')&&!html.includes('href="data:')&&!html.includes('src="https://')); assert.ok(html.includes('src="/api/media/image-id"'));
});
test('GFM renders nested lists, reference links, checklists and aligned tables',()=>{
  const {run}=fixture(),html=run('renderMarkdown("1. First\\n   - Nested **bold**\\n2. Second\\n\\n- [x] Done\\n- [ ] Next\\n\\n| A | B |\\n| :--- | ---: |\\n| one | اردو |\\n\\n[Source][ref]\\n\\n[ref]: https://example.com")');
  assert.ok(html.includes('<ol>')&&html.includes('<ul>')&&html.includes('<strong>bold</strong>')); assert.ok(html.includes('task-done')&&html.includes('type="checkbox"')); assert.ok(html.includes('align="right"')&&html.includes('اردو'));assert.ok(html.includes('href="https://example.com/"'));
});
test('fenced code receives syntax spans and preserves indentation and Unicode',()=>{
  const {run}=fixture(),html=run('renderMarkdown("~~~kotlin\\n  val title = \\"café اردو\\"\\n~~~")');
  assert.ok(html.includes('hljs-keyword'));assert.ok(html.includes('  <span'));assert.ok(html.includes('café اردو'));assert.ok(html.includes('Copy code'));
});
test('large Markdown chunks preserve block structure and reference definitions',()=>{
  const {run}=fixture(),chunks=run('renderMarkdownChunks(("# Section\\n\\nParagraph [source][ref].\\n\\n").repeat(80)+"[ref]: https://example.com")');
  assert.ok(chunks.length>1);assert.ok(chunks.every(chunk=>chunk.includes('href="https://example.com/"')));
});
test('failed saves retain the draft and re-enable controls',async()=>{
  const {context,element,run}=fixture();run('state.editing=true;state.currentNote={id:"a",title:"Old",content:"Old",updatedAt:1,contentHash:"base",folders:[]}');
  element('edit-title').value='New';element('edit-body').value='Unsaved اردو';
  context.fetch=async()=>({ok:false,status:409,text:async()=>'{"message":"Conflict"}'});
  await run('saveNote({preventDefault(){}})');assert.equal(element('edit-body').value,'Unsaved اردو');assert.ok(element('edit-message').textContent.includes('Conflict'));assert.equal(run('state.editing'),true);assert.equal(run('state.saving'),false);
});
test('declining discard retains the editor and its lease',async()=>{
  const {element,run}=fixture();run('state.editing=true;state.currentNote={id:"a",title:"Old",content:"Old",folders:[]}');element('edit-title').value='Old';element('edit-body').value='Changed';
  assert.equal(await run('leaveEditor()'),false);assert.equal(run('state.editing'),true);
});
test('lockout disables the PIN and submit button',()=>{
  const {element,run}=fixture();run('startLockoutCountdown(600)');assert.equal(element('login-btn').disabled,true);assert.equal(element('pin-input').disabled,true);assert.equal(run('state.lockoutTimer'),42);
});
test('oversize Unicode draft never issues a write',async()=>{
  const {context,element,run}=fixture();let writes=0;context.fetch=async()=>{writes++;throw new Error('Unexpected request');};run('state.editing=true;state.currentNote={id:"a",content:""}');element('edit-body').value='界'.repeat(1666667);
  await run('saveNote()');assert.equal(writes,0);assert.ok(element('edit-message').textContent.includes('5 MB'));
});
test('title-only edits preserve original CRLF bytes and selected folders',async()=>{
  const {context,element,run}=fixture();let payload;run('state.editing=true;state.currentNote={id:"a",title:"Old",content:"one\\r\\ntwo\\r\\n",updatedAt:1,contentHash:"base",folders:[]};state.draftFolders=["Ideas"]');
  element('edit-title').value='New';element('edit-body').value='one\ntwo\n';
  context.fetch=async(url,options)=>{if(options.method==='PUT')payload=JSON.parse(options.body);return {ok:true,status:200,text:async()=>JSON.stringify({id:'a',title:'New',content:'one\r\ntwo\r\n',type:'TEXT',folders:['Ideas'],updatedAt:2})};};
  await run('saveNote()');assert.equal(payload.content,'one\r\ntwo\r\n');assert.deepEqual(payload.folders,['Ideas']);
});
test('new captures reuse their identifier on retry',async()=>{
  const {context,element,run}=fixture();const ids=[];
  run('state.editing=true;state.isNew=true;state.draftId="fixed-id";state.draftType="TEXT"');element('edit-title').value='Draft';element('edit-body').value='Keep me';
  context.fetch=async(url,options)=>{ids.push(JSON.parse(options.body).id);return {ok:false,status:503,text:async()=>'{"message":"Unavailable"}'};};
  await run('saveNote()');await run('saveNote()');assert.deepEqual(ids,['fixed-id','fixed-id']);assert.equal(element('edit-body').value,'Keep me');
});
test('pending save or lock acquisition blocks navigation',async()=>{
  const {run}=fixture();run('state.acquiringLock=true');assert.equal(await run('leaveEditor()'),false);run('state.acquiringLock=false;state.saving=true');assert.equal(await run('leaveEditor()'),false);
});
