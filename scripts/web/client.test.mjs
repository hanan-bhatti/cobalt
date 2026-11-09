import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';
import assert from 'node:assert/strict';
const source = readFileSync(new URL('../../app/src/main/assets/web/app.js', import.meta.url), 'utf8');
function fixture() {
  const elements = new Map();
  const element = id => {
    if (!elements.has(id)) elements.set(id, { value: '', textContent: '', innerHTML: '', disabled: false, hidden: false, style: {}, focus() {}, replaceChildren() {}, append() {} });
    return elements.get(id);
  };
  const context = vm.createContext({ console, TextEncoder, URLSearchParams, document: { addEventListener() {}, getElementById: element, createElement: () => ({style:{}}) }, window: { confirm: () => false }, navigator: {}, setInterval: () => 42, clearInterval() {}, setTimeout, clearTimeout });
  vm.runInContext(source, context);
  return { context, element, run: code => vm.runInContext(code, context) };
}
test('saved HTML and script payloads are rendered as text', () => {
  const { run } = fixture();
  const html = run('renderMarkdown("# <script>alert(1)</script>\\n\\n<img src=x onerror=alert(1)>")');
  assert.ok(html.includes('&lt;script&gt;'));
  assert.ok(!html.includes('<script>') && !html.includes('<img '));
});
test('unsafe links and remote image embeds cannot execute or leak a request', () => {
  const { run } = fixture();
  const html = run('renderMarkdown("[click](javascript:alert%281%29)\\n\\n![remote](https://example.com/track)\\n\\n![local](/api/media/image-id)")');
  assert.ok(!html.includes('href="javascript:'));
  assert.ok(!html.includes('src="https://'));
  assert.ok(html.includes('src="/api/media/image-id"'));
});
test('Markdown retains code indentation, Unicode and tables', () => {
  const { run } = fixture();
  const html = run('renderMarkdown("```js\\n  const x = \\\"🙂\\\";\\n```\\n\\n| A | B |\\n| --- | --- |\\n| one | اردو |")');
  assert.ok(html.includes('  const x = &quot;🙂&quot;;'));
  assert.ok(html.includes('<table>') && html.includes('<td>اردو</td>'));
});
test('failed save retains the unsaved draft and re-enables controls', async () => {
  const { context, element, run } = fixture();
  run('state.editing = true; state.currentNote = {id:"a", title:"Old",content:"Old",updatedAt:1,contentHash:"base"}');
  element('edit-title').value = 'New'; element('edit-body').value = 'Unsaved 🙂';
  context.fetch = async () => ({ ok: false, status: 409, text: async () => '{"message":"Conflict"}' });
  await run('saveNote({preventDefault(){}})');
  assert.equal(element('edit-body').value, 'Unsaved 🙂');
  assert.ok(element('edit-message').textContent.includes('Conflict'));
  assert.equal(element('save-note-btn').disabled, false);
  assert.equal(run('state.editing'), true);
});
test('cancel with a declined discard confirmation keeps the editor open', async () => {
  const { element, run } = fixture();
  run('state.editing = true; state.currentNote={title:"Old",content:"Old"}');
  element('edit-title').value='Old'; element('edit-body').value='Changed';
  assert.equal(await run('leaveEditor()'), false);
  assert.equal(run('state.editing'), true);
});
test('lockout disables both input and button until countdown completes', () => {
  const { element, run } = fixture();
  run('startLockoutCountdown(600)');
  assert.equal(element('login-btn').disabled, true);
  assert.equal(element('pin-input').disabled, true);
  assert.equal(run('state.lockoutTimer'), 42);
});
test('oversize Unicode draft is rejected without issuing a write', async () => {
  const { context, element, run } = fixture(); let writes=0;
  context.fetch=async()=> { writes++; throw new Error('Unexpected request'); };
  run('state.editing=true; state.currentNote={id:"a",content:""}');
  element('edit-body').value='🙂'.repeat(1_250_001);
  await run('saveNote({preventDefault(){}})');
  assert.equal(writes,0); assert.ok(element('edit-message').textContent.includes('5 MB'));
});

test('title-only edit retains original CRLF body bytes', async () => {
  const { context, element, run } = fixture(); let savedPayload;
  run('state.editing=true; state.currentNote={id:"a",title:"Old",content:"one\\r\\ntwo\\r\\n",updatedAt:1,contentHash:"base"}');
  element('edit-title').value='New'; element('edit-body').value='one\r\ntwo\r\n'.replace(/\r\n/g,'\n');
  context.fetch=async (url,options)=> {
    if(options.method==='PUT') savedPayload=JSON.parse(options.body);
    return {ok:true,status:200,text:async()=>JSON.stringify({id:'a',title:'New',content:'one\r\ntwo\r\n',type:'TEXT',editable:true,updatedAt:2,contentHash:'next',items:[]})};
  };
  await run('saveNote({preventDefault(){}})');
  assert.equal(savedPayload.content,'one\r\ntwo\r\n');
});
test('pending lock acquisition blocks leaving the note', async () => {
  const { run }=fixture(); run('state.acquiringLock=true');
  assert.equal(await run('leaveEditor()'),false);
});
