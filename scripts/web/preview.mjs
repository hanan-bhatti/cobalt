// Isolated, synthetic UI fixture. Never shipped or used by the Android server.
import http from 'node:http';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
const assets = new URL('../../app/src/main/assets/web/',import.meta.url);
const now = Date.now(), tick = String.fromCharCode(96);
const noteBody = '# A quieter kind of productivity\nMake room for the things worth keeping.\n\n## This week\n- [x] Gather the ideas scattered across apps\n- [ ] Read one saved article slowly\n- [ ] Make something small, just for fun\n\n> A useful archive gives an idea somewhere to grow.\n\n## A little ritual\nCapture first. Organize when you have a moment.\nReturn when you need it.\n\n## Useful snippets\n'+tick.repeat(3)+'kotlin\nfun keepWhatMatters(ideas: List<String>) =\n    ideas.filter { it.isNotBlank() }\n        .distinct()\n        .sorted()\n'+tick.repeat(3)+'\n\n| Thought | Next step |\n| :--- | ---: |\n| Better mornings | Start small |\n| A little more focus | Make room |\n\n1. Save something useful\n   - Add it to a folder\n   - Come back to it later\n2. Keep the source\n\nRead [Material Design](https://m3.material.io).\n\n<script>window.untrusted=true</script>';
export async function startPreview(port = 0) {
  let authenticated = false;
  const folders = [{name:'Ideas',color:'#b87516',icon:'idea'},{name:'Projects',color:'#6675c7',icon:'code'},{name:'Reading',color:'#278f96',icon:'bookmark'},{name:'Movies & Anime',color:'#ba4c7b',icon:'movie'}];
  const notes = [
    ['a','TEXT','A quieter kind of productivity',noteBody,['Ideas']],
    ['b','CODE','A tiny Kotlin helper','fun keepWhatMatters(ideas: List<String>) =\n    ideas.filter { it.isNotBlank() }\n        .distinct()\n        .sorted()\n\n// Small tools, clear intentions.\n// Keep a useful snippet for the next project.',['Projects']],
    ['c','LINK','Material Design','https://m3.material.io',['Reading']],
    ['d','TEXT','Weekend in the mountains','# Weekend in the mountains\nWalking slowly. Keeping the little things.\n\n- A trail through the pines\n- Warm tea after the rain\n- Nothing on the agenda',['Ideas']],
    ['e','MEDIA','The Walking Dead','Sheriff’s deputy Rick Grimes awakens from a coma to find a post-apocalyptic world.',['Movies & Anime']],
    ['f','IMAGE','A softer palette','',['Projects']]
  ].map(([id,type,title,content,assigned],i) => ({id,type,title,content,folders:assigned,updatedAt:now-i*86400000,timestamp:now-i*86400000,contentHash:'base-'+id,editable:['TEXT','CODE'].includes(type),preview:content.slice(0,200),linkDescription:type==='LINK' ? 'Material Design 3 — Google’s open-source design system, patterns, and components for expressive interfaces.' : null,mediaType:type==='MEDIA' ? 'tv' : null,watchStatus:'Plan to Watch',releaseYear:'2010',rating:8.1,genres:['Drama','Sci-Fi & Fantasy']}));
  const rows = new Map(notes.map(n => [n.id,n]));
  const image = Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="600" height="300"><rect width="600" height="300" fill="#ece5f6"/><rect x="70" y="65" width="130" height="170" rx="60" fill="#c7b5e9"/><rect x="235" y="65" width="130" height="170" rx="60" fill="#cfe6c6"/><rect x="400" y="65" width="130" height="170" rx="60" fill="#f2c4aa"/></svg>');
  const server = http.createServer(async (req,res) => {
    const url = new URL(req.url,'http://localhost');
    const send = (code,body,headers={}) => {res.writeHead(code,{'Content-Type':'application/json','Cache-Control':'no-store',...headers});res.end(typeof body==='string' || Buffer.isBuffer(body) ? body : JSON.stringify(body));};
    if (url.pathname === '/api/theme') return send(200,{isDark:false,colors:{primary:'#30684e',onPrimary:'#ffffff',primaryContainer:'#dcebe0',onPrimaryContainer:'#204b35',background:'#f6f9f4',onBackground:'#2b352e',surface:'#f8fbf6',onSurface:'#2b352e',surfaceVariant:'#dfe8de',onSurfaceVariant:'#637168',surfaceContainer:'#eaf1e8',surfaceContainerHigh:'#e2ebe0',outline:'#78867a',outlineVariant:'#d6e0d4'},typography:{textFont:'inter',codeFont:'jetbrains_mono',fontSize:16,fontWeight:400,lineHeight:1.5,paragraphSpacing:8}});
    if (url.pathname === '/api/auth/status') return send(200,{authenticated});
    if (url.pathname === '/api/auth/logout') {authenticated=false;return send(200,{authenticated:false});}
    const buffers = []; for await (const chunk of req) buffers.push(chunk); const bytes = Buffer.concat(buffers);
    const body = req.headers['content-type']?.startsWith('application/json') && bytes.length ? JSON.parse(bytes) : {};
    if (url.pathname === '/api/auth/login') { if (body.pin !== '1234') return send(401,{message:'Incorrect PIN.'}); authenticated=true;return send(200,{authenticated:true});}
    if (url.pathname.startsWith('/api/') && !authenticated) return send(401,{message:'Authentication required.'});
    if (url.pathname === '/api/folders') {
      if (req.method === 'POST') { if (folders.some(f=>f.name.toLowerCase()===body.name.trim().toLowerCase())) return send(409,{message:'A folder with this name already exists.'}); folders.push({name:body.name,color:body.color || '#FF6B6B',icon:body.icon || 'folder',isPinned:body.isPinned || false});return send(201,{name:body.name}); }
      const items = [...rows.values()], counts = {ALL:items.length};
      ['TEXT','CODE','LINK','IMAGE','AUDIO','VIDEO','MEDIA'].forEach(type=>counts[type]=items.filter(n=>n.type===type).length);counts.ATTACHMENT=items.filter(n=>['IMAGE','VIDEO','AUDIO'].includes(n.type)).length;
      return send(200,{folders:folders.map(f=>({...f,count:items.filter(n=>n.folders.includes(f.name)).length})),counts});
    }
    if (url.pathname === '/api/notes' && req.method === 'GET') {
      let items=[...rows.values()].filter(n=>(!url.searchParams.get('folder') || n.folders.includes(url.searchParams.get('folder'))) && (!url.searchParams.get('q') || (n.title+' '+n.content).toLowerCase().includes(url.searchParams.get('q').toLowerCase())) && (!url.searchParams.get('type') || n.type===url.searchParams.get('type') || url.searchParams.get('type')==='ATTACHMENT' && ['IMAGE','AUDIO','VIDEO'].includes(n.type)));
      const sort=url.searchParams.get('sort');items.sort(sort==='title' ? (a,b)=>a.title.localeCompare(b.title) : (a,b)=>b.updatedAt-a.updatedAt);
      const page=Number(url.searchParams.get('page')||1),limit=25,totalItems=items.length;
      return send(200,{items:items.slice((page-1)*limit,page*limit).map(n=>({...n,thumbnailUrl:n.type==='IMAGE' ? '/api/media/f' : null})),page,totalPages:Math.max(1,Math.ceil(totalItems/limit)),totalItems});
    }
    if (url.pathname === '/api/notes' && req.method === 'POST') {const saved={...body,updatedAt:Date.now(),timestamp:Date.now(),editable:['TEXT','CODE'].includes(body.type),contentHash:'new',preview:body.content.slice(0,200),linkDescription:body.description};rows.set(saved.id,saved);return send(201,saved);}
    if (url.pathname === '/api/attachments') {const id=url.searchParams.get('id'),saved={id,title:url.searchParams.get('title'),type:req.headers['content-type'].startsWith('image/')?'IMAGE':req.headers['content-type'].startsWith('audio/')?'AUDIO':'VIDEO',content:'',folders:JSON.parse(url.searchParams.get('folders')||'[]'),updatedAt:Date.now(),mediaUrl:'/api/media/'+id,preview:'Uploaded file'};rows.set(id,saved);return send(201,saved);}
    if (url.pathname.startsWith('/api/media/')) return send(200,image,{'Content-Type':'image/svg+xml'});
    if (url.pathname.startsWith('/api/notes/')) {
      const [,id,lock]=url.pathname.match(/^\/api\/notes\/([^/]+)(?:\/(lock))?$/)||[];
      const note=rows.get(id);if(!note)return send(404,{message:'Note not found.'});
      if(lock)return send(200,req.method==='POST'?note:{renewed:true});
      if(req.method==='PUT'){Object.assign(note,body,{updatedAt:Date.now(),contentHash:'changed'});note.preview=note.content.slice(0,200);}
      return send(200,{...note,mediaUrl:note.type==='IMAGE'?'/api/media/'+id:note.mediaUrl});
    }
    try {
      const path=url.pathname==='/'?'index.html':url.pathname.slice(1);
      const fonts={inter:'inter_variable',literata:'literata_variable',source_serif_4:'source_serif_4_variable',lexend:'lexend_variable',atkinson_hyperlegible:'atkinson_hyperlegible_regular',jetbrains_mono:'jetbrains_mono_variable'};
      if(path.startsWith('fonts/'))return send(200,readFileSync(new URL('../../app/src/main/res/font/'+fonts[path.slice(6,-4)]+'.ttf',import.meta.url)),{'Content-Type':'font/ttf'});
      if(!['index.html','style.css','capture.css','editor.js','app.js','markdown.js','highlight.js','markdown-renderer.js','markdown-worker.js'].includes(path))return send(404,'Missing');
      return send(200,readFileSync(new URL(path,assets)),{'Content-Type':path.endsWith('.css')?'text/css':path.endsWith('.js')?'application/javascript':'text/html'});
    } catch (_) {return send(404,'Missing');}
  });
  await new Promise(resolve=>server.listen(port,'127.0.0.1',resolve));
  return {server,origin:'http://127.0.0.1:'+server.address().port,rows};
}
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const {origin} = await startPreview(18081);console.log('Cobalt synthetic UI preview: '+origin+' · test PIN 1234');
}
