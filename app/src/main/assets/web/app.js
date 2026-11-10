/* Cobalt desktop companion. All scripts, fonts and icons ship with the app. */
'use strict';
const $ = id => document.getElementById(id);
const icon = name => '<svg aria-hidden="true"><use href="#i-' + name + '"></use></svg>';
const TYPES = [
  {type:'',label:'All items',icon:'home'}, {type:'TEXT',label:'Notes',icon:'text'},
  {type:'CODE',label:'Code',icon:'code'}, {type:'LINK',label:'Links',icon:'link'},
  {type:'ATTACHMENT',label:'Attachments',icon:'image'}, {type:'MEDIA',label:'Media hub',icon:'movie'}
];
const CAPTURES = [{type:'TEXT',label:'Text',icon:'text'},TYPES[2],TYPES[3],{type:'ATTACHMENT',label:'Files',icon:'image'},{type:'MEDIA',label:'Media',icon:'movie'}];
const TYPE_INFO = {TEXT:['Note','text'],CODE:['Code','code'],LINK:['Link','link'],IMAGE:['Image','image'],AUDIO:['Audio','voice'],VIDEO:['Video','video'],MEDIA:['Media','movie']};
/* native-folder-presets:start */
const FOLDER_PRESETS = {"colors":[{"hex":"#FF6B6B","name":"Coral Rose"},{"hex":"#FF8E53","name":"Peach Sun"},{"hex":"#FFA800","name":"Soft Gold"},{"hex":"#4CAF50","name":"Emerald Sage"},{"hex":"#00BFA5","name":"Minty Teal"},{"hex":"#29B6F6","name":"Ice Blue"},{"hex":"#5C6BC0","name":"Royal Indigo"},{"hex":"#BA68C8","name":"Lavender Dream"},{"hex":"#D81B60","name":"Crimson Berry"},{"hex":"#78909C","name":"Slate Silver"},{"hex":"#F06292","name":"Flamingo Pink"},{"hex":"#FF8A65","name":"Terra Cotta"},{"hex":"#FFD54F","name":"Golden Silk"},{"hex":"#AED581","name":"Matcha Green"},{"hex":"#4DB6AC","name":"Ocean Mist"},{"hex":"#4DD0E1","name":"Crystal Cyan"},{"hex":"#7986CB","name":"Twilight Blue"},{"hex":"#9FA8DA","name":"Periwinkle Sky"},{"hex":"#8D6E63","name":"Cocoa Bean"},{"hex":"#BCAAA4","name":"Desert Sand"}],"icons":["folder","star","bookmark","idea","checklist","vault","tools","heart","work","school","code","book","home","shopping","globe","music","movie","image","archive","lock"],"symbols":{"folder":"folder","star":"star","bookmark":"bookmark","idea":"idea","checklist":"checklist","check":"checklist","project":"checklist","vault":"vault","tools":"tools","heart":"heart","work":"work","school":"school","study":"school","code":"code","book":"text","text":"text","document":"text","home":"home","shopping":"shopping","globe":"globe","travel":"globe","music":"music","movie":"movie","image":"image","gallery":"image","archive":"archive","lock":"lock","pin":"pin","link":"link","key":"key","mail":"mail","video":"video","voice":"voice","audio":"voice","ai":"ocr","ocr":"ocr","quote":"quote","import":"import","export":"export"}};
/* native-folder-presets:end */
const state = {
  authenticated:false,notes:[],folders:[],counts:{},page:1,totalPages:1,totalItems:0,
  selectedNoteId:null,activeFilterType:'',activeFolder:'',searchQuery:'',sort:'updated',
  currentNote:null,editing:false,isNew:false,saving:false,acquiringLock:false,
  draftType:'TEXT',draftFolders:[],draftFile:null,draftId:null,preview:false,
  noteRequest:0,listRequest:0,folderRequest:0,heartbeat:null,lockoutTimer:null,
  searchDebounceTimer:null,renderWorker:null,renderSequence:0,renderWaiters:new Map(),
  draftKey:null,recoveredDraft:null,draftTimer:null,draftDatabase:null,draftQueue:Promise.resolve(),draftStorageWarning:false
};
let captureEditor = null;
let folderDraft = {busy:false,isPinned:false};
const modLabel = /Mac|iPhone|iPad/.test(navigator.platform || '') ? '⌘' : 'Ctrl';
const editorText = () => captureEditor ? captureEditor.getText() : $('edit-body').value;
function setEditorText(content, load = false) {
  $('edit-body').value = content || '';
  if (captureEditor) load ? captureEditor.load(content) : captureEditor.setText(content);
}
function editorFocus() { if(captureEditor)captureEditor.focus();else $('edit-body').focus(); }

function toast(message, kind = 'success', action) {
  const node = document.createElement('div'); node.className = 'toast ' + kind;
  node.innerHTML = icon(kind === 'error' ? 'info' : 'check');
  const text = document.createElement('span'); text.textContent = message; node.append(text);
  if (action) {
    const button = document.createElement('button'); button.textContent = action.label; button.className = 'toast-action';
    button.onclick = async () => { await action.run(); node.remove(); }; node.append(button);
  }
  const close = document.createElement('button'); close.innerHTML = icon('close'); close.setAttribute('aria-label','Dismiss notification'); close.onclick = () => node.remove(); node.append(close);
  $('toasts').append(node);
  while ($('toasts').children.length > 2) $('toasts').firstElementChild.remove();
  const timer = setTimeout(() => node.remove(), kind === 'error' || action ? 12000 : 4500);
  node.addEventListener('mouseenter',() => clearTimeout(timer),{once:true});
}
function setConnection(label, status = 'connected') { $('connection-status').textContent = label; $('connection').dataset.status = status; }
function showDialog(id) { if (!$(id).open) $(id).showModal(); }
function hideDialog(id) { if ($(id).open) $(id).close(); }
function locked() { state.authenticated = false; setConnection('Archive locked','locked'); showDialog('auth-modal'); }
async function api(path, options = {}) {
  const controller = new AbortController(), timer = setTimeout(() => controller.abort(),30000);
  try {
    const res = await fetch(path,{credentials:'same-origin',...options,signal:controller.signal,headers:{'Content-Type':'application/json',...options.headers}});
    const source = await res.text(); let data;
    try { data = JSON.parse(source); } catch (_) { data = {message:source}; }
    if (!res.ok) {
      if (res.status === 401 && path !== '/api/auth/login') locked();
      const error = new Error(data.message || 'Request failed (' + res.status + ').'); error.status = res.status; error.retryAfter = data.retryAfterSeconds; throw error;
    }
    if (state.authenticated) setConnection('Phone connected');
    return data;
  } catch (error) {
    if (!error.status) { setConnection('Disconnected','disconnected'); throw new Error(error.name === 'AbortError' ? 'The phone took too long to respond. Your draft is still here.' : 'Could not reach your phone. Check the connection and try again.'); }
    throw error;
  } finally { clearTimeout(timer); }
}
async function initTheme() {
  try {
    const data = await api('/api/theme'), root = document.documentElement;
    for (const [key,value] of Object.entries(data.colors || {})) if (/^#[0-9a-f]{6}$/i.test(value)) root.style.setProperty('--' + key.replace(/[A-Z]/g,m => '-' + m.toLowerCase()),value);
    root.dataset.theme = data.isDark ? 'dark' : 'light'; root.style.colorScheme = data.isDark ? 'dark' : 'light';
    const prefs = data.typography || {}, fonts = {inter:'"Cobalt Inter"',literata:'"Cobalt Literata"',source_serif_4:'"Cobalt Source Serif"',lexend:'"Cobalt Lexend"',atkinson_hyperlegible:'"Cobalt Atkinson"',system:'system-ui'};
    if (fonts[prefs.textFont]) root.style.setProperty('--body-font',fonts[prefs.textFont] + ',system-ui,sans-serif');
    if (prefs.applyToAppUi && fonts[prefs.textFont]) root.style.setProperty('--ui-font',fonts[prefs.textFont] + ',system-ui,sans-serif');
    root.style.setProperty('--code-font',prefs.codeFont === 'jetbrains_mono' ? '"Cobalt Mono",monospace' : 'ui-monospace,monospace');
    [['fontSize','font-size',12,24,'px'],['fontWeight','font-weight',300,700,''],['lineHeight','line-height',1.2,2,''],['letterSpacing','letter-spacing',-.02,.1,'em'],['paragraphSpacing','paragraph-spacing',0,16,'px']].forEach(([key,css,min,max,unit]) => { if (Number.isFinite(prefs[key])) root.style.setProperty('--' + css,Math.max(min,Math.min(max,prefs[key])) + unit); });
  } catch (_) { /* Defaults keep the login page usable when the phone is offline. */ }
}
async function checkAuth() {
  try {
    const data = await api('/api/auth/status'); if (!data.authenticated) return locked();
    state.authenticated = true; hideDialog('auth-modal'); setConnection('Phone connected');
    await Promise.allSettled([loadNotes(1),loadFolders()]);
    checkDraftRecovery();
  } catch (_) { locked(); setConnection('Disconnected','disconnected'); $('modal-error').textContent = 'Could not reach your phone. Keep Web Companion enabled and try again.'; }
}
async function submitLogin(event) {
  event?.preventDefault(); if ($('login-btn').disabled) return;
  const pin = $('pin-input').value.trim();
  if (!/^\d{4}$/.test(pin)) { $('modal-error').textContent = 'Enter the four-digit PIN from your phone.'; return; }
  $('login-btn').disabled = true; $('modal-error').textContent = '';
  try {
    const data = await api('/api/auth/login',{method:'POST',body:JSON.stringify({pin})});
    if (!data.authenticated) throw new Error('Could not unlock the archive.');
    state.authenticated = true; hideDialog('auth-modal'); $('pin-input').value = ''; setConnection('Phone connected'); toast('Your archive is connected.');
    await Promise.allSettled([loadNotes(state.page),loadFolders()]);
    if (state.editing && !state.isNew) await renewDraftLock();
    if (!state.editing) checkDraftRecovery();
  } catch (error) {
    if (error.status === 429) startLockoutCountdown(error.retryAfter || 60);
    else { $('modal-error').textContent = error.message; $('pin-input').value = ''; $('pin-input').focus(); }
  } finally { $('login-btn').disabled = !!state.lockoutTimer; }
}
function startLockoutCountdown(seconds) {
  clearInterval(state.lockoutTimer); const until = Date.now() + seconds * 1000;
  $('login-btn').disabled = true; $('pin-input').disabled = true;
  const tick = () => {
    const remaining = Math.max(0,Math.ceil((until-Date.now())/1000));
    $('modal-error').textContent = 'Too many attempts. Try again in ' + remaining + ' seconds, or rotate the PIN on your phone.';
    if (!remaining) { clearInterval(state.lockoutTimer); state.lockoutTimer = null; $('login-btn').disabled = false; $('pin-input').disabled = false; $('modal-error').textContent = 'You can try again now.'; $('pin-input').focus(); }
  };
  state.lockoutTimer = setInterval(tick,1000); tick();
}
async function logout() {
  if (!await leaveEditor()) return;
  try { await api('/api/auth/logout',{method:'POST',body:'{}'}); } catch (error) { toast(error.message,'error'); return; }
  state.notes = []; state.folders = []; state.counts = {}; state.currentNote = null; state.selectedNoteId = null;
  state.noteRequest++; state.listRequest++; state.folderRequest++; $('reader-body').dataset.render = 'locked';
  state.searchQuery = ''; $('search-input').value = ''; $('notes-list').replaceChildren(); $('reader-body').replaceChildren();
  showEmpty(); renderNavigation(); locked();
}
function folderIcon(folder) {
  const aliases = {lightbulb:'idea',lightbulb_outline:'idea',build:'tools',auto_awesome:'sparkle'};
  const name = (folder?.icon || '').replace(/^ic_custom_/,'');
  return FOLDER_PRESETS.symbols[name] || (document.getElementById('i-' + name) ? name : aliases[name] || 'folder');
}
function colorFolder(node, folder) { if (/^#[0-9a-f]{6,8}$/i.test(folder?.color || '')) node.style.setProperty('--folder-color',folder.color.slice(0,7)); }
function renderNavigation() {
  $('type-nav').replaceChildren();
  TYPES.forEach(info => {
    const button = document.createElement('button'); button.className = 'nav-item' + (!state.activeFolder && state.activeFilterType === info.type ? ' active' : '');
    button.setAttribute('aria-current',button.classList.contains('active') ? 'page' : 'false');
    button.innerHTML = icon(info.icon) + '<span>' + info.label + '</span><small>' + (state.counts[info.type || 'ALL'] ?? '—') + '</small>';
    button.onclick = () => selectCollection(info.type,''); $('type-nav').append(button);
  });
  $('folder-nav').replaceChildren();
  state.folders.forEach(folder => {
    const button = document.createElement('button'); button.className = 'nav-item folder-nav-item' + (state.activeFolder === folder.name ? ' active' : '');
    button.innerHTML = icon(folderIcon(folder)); colorFolder(button,folder);
    const label = document.createElement('span'); label.textContent = folder.name; button.append(label);
    const count = document.createElement('small'); count.textContent = folder.count; button.append(count);
    button.setAttribute('aria-current',state.activeFolder === folder.name ? 'page' : 'false');
    button.onclick = () => selectCollection('',folder.name); $('folder-nav').append(button);
  });
}
async function loadFolders() {
  const request = ++state.folderRequest;
  try {
    const data = await api('/api/folders'); if (request !== state.folderRequest) return;
    state.folders = data.folders || []; state.counts = data.counts || {}; renderNavigation(); if (state.editing) renderFolderPicker();
  } catch (error) { if (request === state.folderRequest) toast('Folders could not load. ' + error.message,'error'); }
}
async function selectCollection(type, folder) {
  if (window.innerWidth <= 1000 && !await backToLibrary()) return;
  state.activeFilterType = type; state.activeFolder = folder; renderNavigation(); await loadNotes(1);
}
async function loadNotes(page = 1) {
  const request = ++state.listRequest;
  $('collection-title').textContent = state.searchQuery ? 'Search results' : state.activeFolder || TYPES.find(t => t.type === state.activeFilterType)?.label || 'All items';
  $('collection-context').textContent = state.searchQuery ? 'FIND YOUR WAY BACK' : state.activeFolder ? 'IN YOUR FOLDER' : 'YOUR ARCHIVE';
  $('item-count').textContent = 'Loading your archive…'; $('notes-list').setAttribute('aria-busy','true');
  $('notes-list').innerHTML = '<div class="list-loading">' + [1,2,3,4].map(() => '<div class="skeleton"></div>').join('') + '</div>';
  const params = new URLSearchParams({page,limit:25,sort:state.sort});
  if (state.searchQuery) params.set('q',state.searchQuery);
  if (state.activeFilterType) params.set('type',state.activeFilterType);
  if (state.activeFolder) params.set('folder',state.activeFolder);
  try {
    const data = await api('/api/notes?' + params); if (request !== state.listRequest) return;
    state.notes = data.items || []; state.page = data.page || 1; state.totalPages = data.totalPages || 1; state.totalItems = data.totalItems || 0;
    renderNotesList(); renderPagination(); $('item-count').textContent = state.totalItems + (state.totalItems === 1 ? ' saved item' : ' saved items');
  } catch (error) {
    if (request !== state.listRequest) return;
    $('item-count').textContent = 'Connection unavailable';
    $('notes-list').innerHTML = '<div class="list-empty">' + icon('info') + '<h3>Let’s reconnect.</h3><p></p><button class="quiet">Try again</button></div>';
    $('notes-list').querySelector('p').textContent = error.message; $('notes-list').querySelector('button').onclick = () => loadNotes(page);
  } finally { if (request === state.listRequest) $('notes-list').removeAttribute('aria-busy'); }
}
function renderNotesList() {
  const list = $('notes-list'); list.replaceChildren();
  if (!state.notes.length) {
    list.innerHTML = '<div class="list-empty">' + icon(state.searchQuery ? 'search' : 'archive') + '<h3>' + (state.searchQuery ? 'Nothing here yet.' : 'A little room to begin.') + '</h3><p>' + (state.searchQuery ? 'Try another word or a different view.' : 'Capture an idea, link or file. Keep it close.') + '</p><button class="quiet">' + (state.searchQuery ? 'Clear search' : 'New capture') + '</button></div>';
    list.querySelector('button').onclick = () => { if (state.searchQuery) { state.searchQuery = ''; $('search-input').value = ''; loadNotes(1); } else newCapture(); }; return;
  }
  state.notes.forEach(note => {
    const info = TYPE_INFO[note.type] || ['Item','archive'], card = document.createElement('button');
    card.className = 'note-card' + (note.id === state.selectedNoteId ? ' active' : ''); card.dataset.id = note.id; card.setAttribute('aria-pressed',note.id === state.selectedNoteId);
    card.innerHTML = '<div class="card-top"><span class="type-label type-' + escapeHtml(note.type) + '">' + icon(info[1]) + info[0] + '</span><time>' + dateLabel(note.updatedAt) + '</time></div><h3 dir="auto"></h3><p class="note-card-preview" dir="auto"></p><div class="card-bottom"></div>';
    card.querySelector('h3').textContent = note.title || 'Untitled note'; card.querySelector('p').textContent = cleanPreview(note.preview || '');
    const bottom = card.querySelector('.card-bottom');
    if (note.folders?.length) { const folder = document.createElement('span'); folder.innerHTML = icon('folder'); folder.append(document.createTextNode(note.folders[0])); bottom.append(folder); }
    if (note.isLockedByMobile) { const lock = document.createElement('span'); lock.className = 'lock-badge'; lock.innerHTML = icon('lock') + 'Phone editing'; bottom.append(lock); }
    const thumbnail = safeUrl(note.thumbnailUrl);
    if (thumbnail && note.type !== 'CODE') { const img = document.createElement('img'); img.className = 'card-thumbnail'; img.src = thumbnail; img.alt = ''; img.loading = 'lazy'; img.referrerPolicy = 'no-referrer'; img.onerror = () => img.remove(); card.append(img); }
    card.onclick = () => openNote(note.id); list.append(card);
  });
}
function cleanPreview(text) { return String(text).replace(/!\[([^\]]*)\]\([^)]+\)/g,'$1').replace(/\[([^\]]+)\]\([^)]+\)/g,'$1').replace(/^[#>*+\s-]+/gm,'').replace(/[*_~\u0060]/g,'').replace(/\s+/g,' ').slice(0,200); }
function dateLabel(timestamp) { const date = new Date(timestamp); return Number.isFinite(date.getTime()) ? date.toLocaleDateString(undefined,{month:'short',day:'numeric'}) : ''; }
function renderPagination() { $('page-indicator').textContent = state.page + ' / ' + state.totalPages; $('prev-page-btn').disabled = state.page <= 1; $('next-page-btn').disabled = state.page >= state.totalPages; }
function showReading() { document.querySelector('.workspace').classList.add('reading'); $('empty-state').hidden = true; }
function showEmpty() { $('empty-state').hidden = false; $('reader-content').hidden = true; $('note-editor').hidden = true; $('reader-actions').hidden = true; $('reader-breadcrumb').textContent = 'A little space for your thoughts'; }
async function backToLibrary() { if (!await leaveEditor()) return false; document.querySelector('.workspace').classList.remove('reading'); return true; }
async function openNote(id) {
  if (!await leaveEditor()) return;
  const request = ++state.noteRequest; state.selectedNoteId = id; state.currentNote = null; renderNotesList(); showReading();
  $('reader-content').hidden = false; $('reader-actions').hidden = false;
  $('reader-title').textContent = 'Opening…'; $('reader-date').textContent = ''; $('reader-tags').replaceChildren();
  $('reader-body').dataset.render = 'loading-' + request; $('reader-body').textContent = 'Loading your capture…';
  $('edit-note-btn').disabled = true; $('copy-note-btn').disabled = true; $('download-note-btn').disabled = true;
  try {
    const note = await api('/api/notes/' + encodeURIComponent(id)); if (request !== state.noteRequest) return;
    state.currentNote = note; await renderNote(note); if (request === state.noteRequest) $('reader-scroll').scrollTop = 0;
  } catch (error) { if (request !== state.noteRequest) return; $('reader-title').textContent = 'Couldn’t open this capture.'; $('reader-body').textContent = error.message; toast(error.message,'error'); }
}
function renderTags(target, names, selectable = false) {
  target.replaceChildren();
  names.forEach(name => {
    const folder = state.folders.find(f => f.name === name), chip = document.createElement(selectable ? 'button' : 'span');
    chip.className = 'chip'; chip.innerHTML = icon(folderIcon(folder)); chip.append(document.createTextNode(name)); colorFolder(chip,folder);
    if (selectable) {
      chip.type = 'button'; const selected = state.draftFolders.includes(name); chip.classList.toggle('active',selected); chip.setAttribute('aria-pressed',selected);
      chip.onclick = () => { state.draftFolders = selected ? state.draftFolders.filter(f => f !== name) : [...state.draftFolders,name]; renderFolderPicker(); scheduleDraft(); };
    }
    target.append(chip);
  });
}
async function markdownIn(target, content) {
  const ticket = String(++state.renderSequence); target.dataset.render = ticket;
  if (content.length <= 120000 || !window.Worker) {
    try { target.innerHTML = renderMarkdown(content); } catch (_) { target.textContent = content; toast('Markdown could not render. Showing the original text.','error'); } return;
  }
  target.textContent = 'Preparing your note…';
  try {
    if (!state.renderWorker) {
      state.renderWorker = new Worker('/markdown-worker.js');
      state.renderWorker.onmessage = ({data}) => { const waiter = state.renderWaiters.get(data.id); state.renderWaiters.delete(data.id); waiter?.(data); };
      state.renderWorker.onerror = () => { state.renderWaiters.forEach(resolve => resolve({error:'Markdown rendering is unavailable.'})); state.renderWaiters.clear(); state.renderWorker?.terminate(); state.renderWorker = null; };
    }
    const result = await new Promise(resolve => { state.renderWaiters.set(ticket,resolve); state.renderWorker.postMessage({id:ticket,content}); });
    if (target.dataset.render !== ticket) return; if (result.error) throw new Error(result.error);
    target.replaceChildren(); let page = 0; const more = document.createElement('button'); more.className = 'quiet load-more'; more.textContent = 'Continue reading';
    const append = () => { more.remove(); const template = document.createElement('template'); template.innerHTML = result.chunks[page++]; target.append(template.content); if (page < result.chunks.length) target.append(more); };
    more.onclick = append; append();
  } catch (error) { if (target.dataset.render === ticket) { target.textContent = content; toast(error.message,'error'); } }
}
async function renderNote(note) {
  $('reader-content').hidden = false; $('reader-body').hidden = false; $('note-editor').hidden = true; $('reader-actions').hidden = false;
  $('reader-title').textContent = note.title || 'Untitled note'; const info = TYPE_INFO[note.type] || ['Capture','archive'];
  $('reader-breadcrumb').textContent = info[0] + ' / ' + (note.folders?.[0] || 'Your archive');
  $('reader-date').innerHTML = '<time>' + escapeHtml(new Date(note.updatedAt).toLocaleDateString(undefined,{year:'numeric',month:'long',day:'numeric'})) + '</time><span>·</span>' + icon(info[1]) + escapeHtml(info[0]);
  renderTags($('reader-tags'),note.folders || []);
  const conflict = note.isLockedByMobile || note.isLockedByOtherWeb; $('mobile-lock-alert').hidden = !conflict;
  $('mobile-lock-alert').textContent = note.isLockedByMobile ? 'Your phone is editing this note. You can keep reading here.' : 'Another browser is editing this note.';
  $('edit-note-btn').disabled = !note.editable || conflict; $('copy-note-btn').disabled = false; $('download-note-btn').disabled = false;
  const body = $('reader-body'); body.dataset.render = 'note-' + note.id; body.replaceChildren();
  if (note.type === 'LINK') { renderLink(note,body); return; }
  if (note.type === 'MEDIA') { renderMedia(note,body); return; }
  if (note.type === 'CODE') body.innerHTML = codeBlock(note.content || ''); else await markdownIn(body,note.content || '');
  if (state.currentNote?.id !== note.id || state.editing) return;
  if (safeUrl(note.mediaUrl)?.startsWith('/api/media/')) {
    const media = document.createElement(note.type === 'IMAGE' ? 'img' : note.type === 'VIDEO' ? 'video' : 'audio');
    media.className = 'attachment-view'; media.src = note.mediaUrl; media.controls = true; media.alt = note.title; media.preload = 'metadata';
    media.onerror = () => { const error = document.createElement('div'); error.className = 'attachment-error'; error.textContent = 'This attachment isn’t available here. Open it on your phone, or try again after reconnecting.'; media.replaceWith(error); };
    body.prepend(media);
  }
}
function sourceCard(url, title, subtitle) {
  const card = document.createElement('div'); card.className = 'source-card'; card.innerHTML = icon('globe') + '<div><strong></strong><p></p></div>';
  card.querySelector('strong').textContent = title; card.querySelector('p').textContent = subtitle || url;
  const safe = safeUrl(url,false);
  if (safe) { const link = document.createElement('a'); link.href = safe; link.target = '_blank'; link.rel = 'noopener noreferrer'; link.textContent = 'Open link'; link.append(document.createRange().createContextualFragment(icon('link_external'))); card.append(link); }
  return card;
}
function renderLink(note, body) {
  const url = safeUrl(note.thumbnailUrl);
  if (url) { const img = document.createElement('img'); img.className = 'link-cover'; img.src = url; img.alt = ''; img.referrerPolicy = 'no-referrer'; img.onerror = () => img.remove(); body.append(img); }
  const section = document.createElement('section'); section.className = 'detail-section'; section.innerHTML = '<p class="eyebrow">A LITTLE CONTEXT</p><p dir="auto"></p>'; section.lastElementChild.textContent = note.linkDescription || 'A link worth coming back to.'; body.append(section);
  body.append(sourceCard(note.content,note.linkTitle || note.title,note.content));
}
function renderMedia(note, body) {
  const layout = document.createElement('div'); layout.className = 'media-layout'; const image = safeUrl(note.thumbnailUrl);
  if (image) { const img = document.createElement('img'); img.className = 'media-poster'; img.src = image; img.alt = note.title; img.referrerPolicy = 'no-referrer'; img.onerror = () => img.remove(); layout.append(img); }
  const summary = document.createElement('div'); summary.className = 'media-summary';
  const kind = document.createElement('p'); kind.className = 'eyebrow'; kind.textContent = {movie:'MOVIE',tv:'TV SHOW',anime:'ANIME',manga:'MANGA'}[note.mediaType] || 'MEDIA';
  const heading = document.createElement('h2'); heading.textContent = note.title; summary.append(kind,heading);
  const facts = document.createElement('div'); facts.className = 'facts';
  [note.releaseYear,note.rating != null ? note.rating + ' / 10' : null,note.runtime ? note.runtime + ' min' : null,note.episodeCount ? note.episodeCount + ' episodes' : null,note.chapterCount ? note.chapterCount + ' chapters' : null,note.watchStatus].filter(Boolean).forEach(value => { const pill = document.createElement('span'); pill.className = 'fact'; pill.textContent = value; facts.append(pill); });
  summary.append(facts); const tagline = document.createElement('p'); tagline.textContent = note.tagline || ''; summary.append(tagline); layout.append(summary); body.append(layout);
  [['GENRES',note.genres],['WATCH ON',note.watchProviders]].forEach(([label,values]) => {
    if (!values?.length) return; const section = document.createElement('section'); section.className = 'detail-section'; section.innerHTML = '<p class="eyebrow">' + label + '</p><div class="facts"></div>';
    values.forEach(value => { const fact = document.createElement('span'); fact.className = 'fact'; fact.textContent = value; section.lastElementChild.append(fact); }); body.append(section);
  });
  if (note.content) { const section = document.createElement('section'); section.className = 'detail-section'; section.innerHTML = '<p class="eyebrow">STORY</p>'; const text = document.createElement('div'); section.append(text); body.append(section); markdownIn(text,note.content); }
  if (note.sourceUrl) body.append(sourceCard(note.sourceUrl,note.source || 'Original source'));
  if (note.trailerUrl) body.append(sourceCard(note.trailerUrl,'Watch the trailer'));
}
function editorMessage(message, error = false) { $('edit-message').textContent = message; $('edit-message').classList.toggle('error',error); }
function openDraftDatabase() {
  if (!window.indexedDB) return Promise.reject(new Error('Draft storage unavailable'));
  if (!state.draftDatabase) state.draftDatabase = new Promise((resolve,reject) => {
    const request = indexedDB.open('cobalt-capture-drafts',1);
    request.onupgradeneeded = () => request.result.createObjectStore('drafts',{keyPath:'key'});
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
  return state.draftDatabase;
}
async function draftOperation(operation, value) {
  const database = await openDraftDatabase();
  return new Promise((resolve,reject) => {
    const tx = database.transaction('drafts',operation==='all'?'readonly':'readwrite'), store=tx.objectStore('drafts');
    const request = operation==='all'?store.getAll():operation==='put'?store.put(value):store.delete(value);
    let result; request.onsuccess = () => {result=request.result;};
    tx.oncomplete = () => resolve(result); tx.onerror = () => reject(tx.error); tx.onabort = () => reject(tx.error);
  });
}
function draftSnapshot() {
  if (!state.editing || !state.draftKey || state.draftType==='ATTACHMENT') return null;
  return {key:state.draftKey,isNew:state.isNew,draftId:state.draftId,type:state.draftType,
    title:$('edit-title').value,content:editorText(),folders:[...state.draftFolders],
    description:$('link-description').value,mediaType:$('media-kind').value,releaseYear:$('release-year').value,
    original:state.isNew?null:state.currentNote,updatedAt:Date.now()};
}
function scheduleDraft() {
  if (!state.editing || state.saving) return;
  clearTimeout(state.draftTimer); $('draft-status').textContent = 'Unsaved changes';
  state.draftTimer = setTimeout(persistDraft,350);
}
async function persistDraft() {
  clearTimeout(state.draftTimer);
  const snapshot=draftSnapshot();if(!snapshot)return;
  const dirty=editorDirty(),key=snapshot.key;
  state.draftQueue=state.draftQueue.catch(()=>{}).then(()=>draftOperation(dirty?'put':'delete',dirty?snapshot:key));
  try {
    await state.draftQueue;
    if(state.editing&&state.draftKey===key)$('draft-status').textContent=dirty?'Draft saved in this browser':'';
  } catch (_) {
    $('draft-status').textContent='Draft recovery unavailable';
    if(!state.draftStorageWarning){state.draftStorageWarning=true;toast('Browser draft storage is unavailable. Keep this tab open or download your draft.','error');}
  }
}
async function clearDraft(key=state.draftKey) {
  clearTimeout(state.draftTimer);if(!key)return;
  state.draftQueue=state.draftQueue.catch(()=>{}).then(()=>draftOperation('delete',key));
  try {await state.draftQueue;} catch (_) { /* A stale recovery is still safe to review on the next visit. */ }
}
async function checkDraftRecovery() {
  if(state.editing)return;
  try {
    await state.draftQueue.catch(()=>{});
    const drafts=await draftOperation('all');
    if(state.editing)return;
    state.recoveredDraft=drafts.filter(d=>typeof d.content==='string'&&typeof d.title==='string').sort((a,b)=>b.updatedAt-a.updatedAt)[0] || null;
    $('draft-recovery').hidden=!state.recoveredDraft;
    if(state.recoveredDraft)$('recovery-description').textContent=(state.recoveredDraft.title || 'Untitled capture')+' · saved in this browser';
  } catch (_) { $('draft-recovery').hidden=true; }
}
async function resumeDraft() {
  const draft=state.recoveredDraft;if(!draft||state.saving||state.acquiringLock)return;
  if(!await leaveEditor())return;
  state.acquiringLock=true;$('resume-draft-btn').disabled=true;
  let base=null,changed=false;
  try {
    if(!draft.isNew&&draft.original){
      try {
        const latest=await api('/api/notes/'+encodeURIComponent(draft.original.id)+'/lock',{method:'POST',body:'{}'});
        if(latest.updatedAt===draft.original.updatedAt&&latest.contentHash===draft.original.contentHash)base=latest;
        else {
          changed=true;
          await api('/api/notes/'+encodeURIComponent(latest.id)+'/lock',{method:'DELETE',body:'{}'});
        }
      } catch(error){if(![404,409].includes(error.status))throw error;changed=true;}
    }
    state.noteRequest++;state.currentNote=base;state.selectedNoteId=base?.id || null;
    state.editing=true;state.isNew=!base;state.draftType=draft.type || 'TEXT';
    state.draftId=base?null:!changed&&draft.draftId?draft.draftId:newDraftId();
    state.draftKey=base?base.id:state.draftId;state.draftFolders=draft.folders || [];
    $('link-description').value=draft.description || '';$('media-kind').value=draft.mediaType || 'movie';$('release-year').value=draft.releaseYear || '';
    setupEditor(draft.title,draft.content);showReading();if(base)startHeartbeat();
    editorMessage(changed?'The saved note changed. This draft will be saved as a new capture.':'Recovered your draft. Continue when you’re ready.');
    if(draft.key!==state.draftKey){await persistDraft();await clearDraft(draft.key);}
    state.recoveredDraft=null;$('draft-recovery').hidden=true;
  } catch(error){toast(error.message,'error');}
  finally{state.acquiringLock=false;$('resume-draft-btn').disabled=false;}
}
function newDraftId() {
  return crypto.randomUUID?.() || 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g,c=>{const n=crypto.getRandomValues(new Uint8Array(1))[0]&15;return(c==='x'?n:(n&3)|8).toString(16);});
}
function updateFormatToolbar() {
  document.querySelectorAll('[data-format]').forEach(button=>{
    const kind=button.dataset.format;
    button.disabled=state.saving || !state.editing || state.preview || (kind==='undo'&&!captureEditor?.canUndo) || (kind==='redo'&&!captureEditor?.canRedo);
    button.setAttribute('aria-pressed',!!captureEditor?.active(kind));
    const shortcuts={bold:modLabel+' B',italic:modLabel+' I',strike:modLabel+' Shift X',undo:modLabel+' Z',redo:modLabel+' Shift Z',link:modLabel+' Shift K'};
    button.title=button.getAttribute('aria-label')+(shortcuts[kind]?' ('+shortcuts[kind]+')':'');
  });
  $('table-context').hidden=state.preview || !captureEditor?.tableContext();
}
function showLinkDialog() {
  if(!captureEditor||state.saving||state.preview)return;
  const selection=captureEditor.selection;
  $('link-label').value=editorText().slice(selection.from,selection.to);$('link-url').value='';$('link-error').textContent='';
  showDialog('link-modal');$('link-url').focus();
}
function startHeartbeat() {
  clearInterval(state.heartbeat);
  state.heartbeat = setInterval(async () => {
    if (!state.editing || state.isNew || !state.authenticated) return;
    try { await api('/api/notes/' + encodeURIComponent(state.currentNote.id) + '/lock',{method:'PUT',body:'{}'}); }
    catch (error) { editorMessage(error.message + ' Your draft is still here.',true); }
  },20000);
}
async function renewDraftLock() {
  try {
    const fresh = await api('/api/notes/' + encodeURIComponent(state.currentNote.id) + '/lock',{method:'POST',body:'{}'});
    if (fresh.updatedAt !== state.currentNote.updatedAt || fresh.contentHash !== state.currentNote.contentHash) editorMessage('The saved note changed. Download your draft before reopening this note.',true);
    else { startHeartbeat(); editorMessage('Reconnected. Your draft is ready to save.'); }
  } catch (error) { editorMessage(error.message,true); }
}
async function beginEdit() {
  if (!state.currentNote?.editable || state.editing || state.acquiringLock) return;
  state.acquiringLock = true; $('edit-note-btn').disabled = true;
  try {
    const base = await api('/api/notes/' + encodeURIComponent(state.currentNote.id) + '/lock',{method:'POST',body:'{}'});
    state.currentNote = base; state.editing = true; state.isNew = false; state.draftType = base.type; state.draftFolders = [...(base.folders || [])]; state.draftFile = null;
    state.draftKey=base.id;
    setupEditor(base.title,base.content); startHeartbeat(); editorMessage('Changes stay here until you save.');
  } catch (error) { toast(error.message,'error'); $('edit-note-btn').disabled = false; }
  finally { state.acquiringLock = false; }
}
async function newCapture(type = 'TEXT') {
  if (!state.authenticated) return locked(); if (!await leaveEditor()) return;
  state.noteRequest++; $('reader-body').dataset.render = 'new'; state.selectedNoteId = null; state.currentNote = null; state.editing = true; state.isNew = true;
  state.draftType = type; state.draftId = newDraftId();state.draftKey=state.draftId;
  state.draftFolders = state.activeFolder ? [state.activeFolder] : []; state.draftFile = null;
  $('link-description').value = ''; $('release-year').value = ''; $('media-kind').value = 'movie'; $('attachment-input').value = '';
  setupEditor('',''); editorMessage('Ready when you are.'); renderNotesList(); showReading();
}
function setupEditor(title, content) {
  $('empty-state').hidden = true; $('reader-content').hidden = true; $('note-editor').hidden = false; $('reader-actions').hidden = true;
  $('reader-breadcrumb').textContent = state.isNew ? 'New capture' : 'Editing / ' + (state.currentNote.title || 'Untitled note');
  $('edit-title').value = title || '';setEditorText(content,true);
  document.querySelector('.workspace').classList.add('capturing');$('reader-scroll').classList.add('editing');
  $('focus-editor-btn').hidden=false;$('draft-recovery').hidden=true;$('draft-status').textContent='';
  $('save-note-btn').querySelector('kbd').textContent=modLabel+' S';
  state.preview = false;renderCaptureTypes();renderEditorType();renderFolderPicker();updateDraftCount();updateFormatToolbar();
  $('capture-scroll').scrollTop=0;$('edit-title').focus();
}
function renderCaptureTypes() {
  $('capture-types').hidden=false; $('capture-types').replaceChildren();
  CAPTURES.forEach(info => {
    const button = document.createElement('button'); button.type = 'button'; button.className = 'capture-type' + (state.draftType === info.type ? ' active' : ''); button.setAttribute('aria-pressed',state.draftType === info.type); button.innerHTML = icon(info.icon) + info.label;
    button.onclick = () => { state.draftType = info.type; state.preview = false; renderCaptureTypes(); renderEditorType();scheduleDraft(); }; $('capture-types').append(button);
    button.disabled=!state.isNew || state.saving;
  });
}
function renderEditorType() {
  const type = state.draftType, attachment = type === 'ATTACHMENT', rich = ['TEXT','CODE','MEDIA'].includes(type);
  $('note-editor').dataset.type = type; $('capture-options').hidden = type !== 'MEDIA'; $('upload-fields').hidden = !attachment;
  $('editor-format').hidden = !rich;$('edit-body').hidden=!!captureEditor || attachment || state.preview;
  $('capture-editor-host').hidden=attachment || state.preview;$('edit-preview').hidden = !state.preview || !rich; $('link-description-field').hidden = type !== 'LINK';
  $('body-label').textContent = type === 'LINK' ? 'Link URL' : type === 'CODE' ? 'Code content' : 'Note content in Markdown';
  captureEditor?.setMode(type);
  $('preview-btn').setAttribute('aria-pressed',state.preview);$('preview-btn').querySelector('span').textContent=state.preview?'Edit':'Preview';
  $('file-label').textContent = state.draftFile ? state.draftFile.name : 'Choose a file or drop it here';
  updateFormatToolbar();
}
function renderFolderPicker() {
  renderTags($('editor-folders'),[...new Set([...state.folders.map(f => f.name),...state.draftFolders])],true);
  if (!$('editor-folders').children.length) $('editor-folders').innerHTML = '<span class="muted field-help">Your first folder starts with “New folder”.</span>';
}
function editorDirty() {
  if (!state.editing) return false;
  if (state.isNew) return !!($('edit-title').value || editorText() || $('link-description').value || state.draftFile || $('release-year').value || state.draftFolders.length);
  const base = state.currentNote;
  return $('edit-title').value !== base.title || editorText() !== (base.content || '').replace(/\r\n?/g,'\n') || JSON.stringify([...state.draftFolders].sort()) !== JSON.stringify([...(base.folders || [])].sort());
}
function confirmDiscard() {
  return new Promise(resolve => {
    const dialog = $('discard-modal'); showDialog('discard-modal');
    const finish = value => { hideDialog('discard-modal'); $('keep-draft-btn').onclick = null; $('discard-draft-btn').onclick = null; dialog.removeEventListener('cancel',cancel); resolve(value); };
    const cancel = event => { event.preventDefault(); finish(false); };
    $('keep-draft-btn').onclick = () => finish(false); $('discard-draft-btn').onclick = () => finish(true); dialog.addEventListener('cancel',cancel); $('keep-draft-btn').focus();
  });
}
async function leaveEditor(saved = false) {
  if (state.saving || state.acquiringLock || $('discard-modal').open) return false;
  if (!state.editing) return true;
  if (!saved && editorDirty() && !await confirmDiscard()) return false;
  await clearDraft();
  clearInterval(state.heartbeat); state.heartbeat = null;
  if (!state.isNew && state.currentNote) { try { await api('/api/notes/' + encodeURIComponent(state.currentNote.id) + '/lock',{method:'DELETE',body:'{}'}); } catch (_) { /* Abandoned locks expire on the phone. */ } }
  state.editing = false; state.isNew = false; state.preview = false; state.draftFile = null;
  state.draftKey=null;
  document.querySelector('.workspace').classList.remove('capturing','capture-focus');$('reader-scroll').classList.remove('editing');
  $('focus-editor-btn').hidden=true;$('focus-editor-btn').setAttribute('aria-pressed','false');
  $('note-editor').hidden = true; $('edit-title').value = '';setEditorText('',true);$('edit-preview').dataset.render = 'closed'; $('edit-preview').replaceChildren();
  if (state.currentNote) await renderNote(state.currentNote); else showEmpty(); return true;
}
function editorBusy(busy) {
  $('note-editor').querySelectorAll('input,textarea,select,button').forEach(node => node.disabled = busy);
  captureEditor?.setReadOnly(busy);if(!busy){renderCaptureTypes();updateFormatToolbar();}
}
async function saveNote(event) {
  event?.preventDefault(); if (!state.editing || state.saving) return;
  const base = state.currentNote, type = state.draftType, title = $('edit-title').value.trim(), edited = editorText();
  const content = !state.isNew && edited === (base.content || '').replace(/\r\n?/g,'\n') ? base.content : edited;
  if (new TextEncoder().encode(content).length > 5000000) { editorMessage('The web editor supports up to 5 MB. Your draft is still here.',true); return; }
  if (type === 'LINK' && !/^https?:/i.test(safeUrl(content.trim(),false) || '')) { editorMessage('Enter a complete http or https link.',true);editorFocus();return; }
  if (type === 'MEDIA' && !title) { editorMessage('Give this media entry a title.',true); $('edit-title').focus(); return; }
  if (type === 'ATTACHMENT' && !state.draftFile) { editorMessage('Choose a file to upload.',true); return; }
  state.saving = true; editorBusy(true); editorMessage(type === 'ATTACHMENT' ? 'Uploading to your phone…' : 'Saving to your phone…');
  try {
    let saved;
    if (state.isNew && type === 'ATTACHMENT') {
      const file = state.draftFile, params = new URLSearchParams({id:state.draftId,title:title || file.name,folders:JSON.stringify(state.draftFolders)});
      const mime = file.type === 'audio/x-wav' ? 'audio/wav' : file.type || fileMime(file.name);
      saved = await api('/api/attachments?' + params,{method:'POST',headers:{'Content-Type':mime},body:file});
    } else {
      const payload = {title,content:type === 'LINK' ? content.trim() : content,folders:state.draftFolders};
      if (state.isNew) Object.assign(payload,{id:state.draftId,type,description:$('link-description').value,mediaType:$('media-kind').value,releaseYear:$('release-year').value});
      else Object.assign(payload,{updatedAt:base.updatedAt,contentHash:base.contentHash});
      saved = await api(state.isNew ? '/api/notes' : '/api/notes/' + encodeURIComponent(base.id),{method:state.isNew ? 'POST' : 'PUT',body:JSON.stringify(payload)});
    }
    state.saving = false; state.currentNote = saved; state.selectedNoteId = saved.id;
    await leaveEditor(true); toast('Saved to your phone.'); await Promise.allSettled([loadNotes(1),loadFolders()]);
  } catch (error) { editorMessage(error.message + ' Your unsaved draft is still here.',true);await persistDraft();toast(error.message,'error'); }
  finally { state.saving = false; editorBusy(false); }
}
function updateDraftCount() {
  const text=editorText(),words=text.trim()?text.trim().split(/\s+/u).length:0;
  $('draft-count').textContent=words.toLocaleString()+(words===1?' word':' words');
}
function fileMime(name) { return {jpg:'image/jpeg',jpeg:'image/jpeg',png:'image/png',webp:'image/webp',gif:'image/gif',mp4:'video/mp4',webm:'video/webm',mp3:'audio/mpeg',m4a:'audio/mp4',wav:'audio/wav',ogg:'audio/ogg'}[name.split('.').pop().toLowerCase()] || ''; }
function chooseFile(file) {
  if (!file) return; const mime = file.type || fileMime(file.name);
  if (!['image/jpeg','image/png','image/webp','image/gif','video/mp4','video/webm','audio/mpeg','audio/mp4','audio/wav','audio/x-wav','audio/ogg','audio/webm'].includes(mime)) { toast('Choose a supported image, audio or video file.','error'); return; }
  if (!file.size || file.size > 10485760) { toast(file.size ? 'Choose a file smaller than 10 MB.' : 'This file is empty.','error'); return; }
  state.draftFile = file; if (!$('edit-title').value) $('edit-title').value = file.name; renderEditorType(); editorMessage('Ready to upload · ' + (file.size/1048576).toFixed(2) + ' MB');
}
function folderDuplicate(name) {return !!name && state.folders.some(folder=>folder.name.trim().toLowerCase()===name.toLowerCase());}
function updateFolderDialog(clearError=false) {
  const name=$('folder-name').value.trim(), duplicate=folderDuplicate(name);
  if(clearError)folderDraft.error='';
  const error=duplicate?'A folder with this name already exists':folderDraft.error || '';
  $('folder-error').textContent=error;$('folder-error').hidden=!error;
  $('folder-name').setAttribute('aria-invalid',!!error);
  $('folder-save-btn').disabled=folderDraft.busy || !name || duplicate;
  $('folder-save-btn').textContent=folderDraft.busy?'Creating…':'Create';
  $('folder-form').setAttribute('aria-busy',folderDraft.busy);
  $('folder-pin-toggle').setAttribute('aria-checked',folderDraft.isPinned);
  $('folder-preview-pin').hidden=!folderDraft.isPinned;
  $('folder-preview').classList.toggle('pinned',folderDraft.isPinned);
  $('folder-preview').style.setProperty('--folder-color',$('folder-color').value);
  $('folder-preview-name').textContent=name || 'New Folder';
  $('folder-preview-icon').innerHTML=icon(folderIcon({icon:$('folder-icon').value}));
  ['color','icon'].forEach(kind=>{
    document.querySelectorAll('#folder-'+kind+'-options [role=radio]').forEach(button=>{
      const selected=button.dataset.value===$('folder-'+kind).value;
      button.setAttribute('aria-checked',selected);button.tabIndex=selected?0:-1;
    });
  });
}
function renderFolderPresets() {
  ['color','icon'].forEach(kind=>{
    const row=$('folder-'+kind+'-options');row.replaceChildren();
    (kind==='color'?FOLDER_PRESETS.colors:FOLDER_PRESETS.icons).forEach(value=>{
      const button=document.createElement('button');button.type='button';button.setAttribute('role','radio');
      button.className='folder-'+kind+'-option';button.dataset.value=kind==='color'?value.hex:value;
      button.title=kind==='color'?value.name:value.charAt(0).toUpperCase()+value.slice(1);
      button.setAttribute('aria-label',button.title);
      if(kind==='color'){button.style.setProperty('--swatch-color',value.hex);button.innerHTML=icon('check');}
      else button.innerHTML=icon(folderIcon({icon:value}));
      button.onclick=()=>{$('folder-'+kind).value=button.dataset.value;updateFolderDialog(true);};
      button.onkeydown=event=>{
        if(!['ArrowLeft','ArrowRight','Home','End'].includes(event.key))return;
        event.preventDefault();const buttons=[...row.children],index=buttons.indexOf(button);
        const next=buttons[event.key==='Home'?0:event.key==='End'?buttons.length-1:(index+(event.key==='ArrowRight'?1:-1)+buttons.length)%buttons.length];
        next.click();next.focus();next.scrollIntoView({block:'nearest',inline:'nearest'});
      };
      row.append(button);
    });
  });
}
function showFolderDialog() {
  if(!state.authenticated)return locked();if(folderDraft.busy||state.saving)return;
  folderDraft={busy:false,isPinned:false,error:''};$('folder-name').value='';
  $('folder-color').value=FOLDER_PRESETS.colors[0].hex;$('folder-icon').value=FOLDER_PRESETS.icons[0];
  renderFolderPresets();updateFolderDialog();showDialog('folder-modal');
  ['color','icon'].forEach(kind=>$('folder-'+kind+'-options').scrollLeft=0);
  $('folder-name').focus();
}
async function createFolder(event) {
  event.preventDefault(); if(folderDraft.busy)return; const name=$('folder-name').value.trim();
  if(!name||name.length>40||/[\u0000-\u001f\u007f]/.test(name)||folderDuplicate(name)){
    folderDraft.error=folderDuplicate(name)?'A folder with this name already exists':'Use a folder name between 1 and 40 characters.';
    updateFolderDialog();$('folder-name').focus();return;
  }
  const captureKey=state.draftKey,payload={name,color:$('folder-color').value,icon:$('folder-icon').value,isPinned:folderDraft.isPinned};
  folderDraft.busy=true;folderDraft.error='';updateFolderDialog();
  $('folder-form').querySelectorAll('input,button').forEach(node=>node.disabled=true);
  try {
    await api('/api/folders',{method:'POST',body:JSON.stringify(payload)});hideDialog('folder-modal');
    if(state.editing&&state.draftKey===captureKey&&!state.draftFolders.includes(name)){state.draftFolders.push(name);scheduleDraft();}
    await loadFolders(); toast('Folder created.');
  } catch(error){folderDraft.error=error.message;}
  finally{folderDraft.busy=false;$('folder-form').querySelectorAll('input,button').forEach(node=>node.disabled=false);updateFolderDialog();}
}
async function copyText(text) {
  if (navigator.clipboard && window.isSecureContext) await navigator.clipboard.writeText(text);
  else {
    const previous = document.activeElement, area = document.createElement('textarea'); area.value = text; area.style.cssText = 'position:fixed;left:-10000px;top:0'; document.body.append(area); area.select();
    const copied = document.execCommand('copy'); area.remove(); previous?.focus(); if (!copied) throw new Error('Copy is unavailable. Select the text or download this capture.');
  }
}
async function copyNote() {
  if (!state.currentNote && !state.editing) return;
  try { await copyText(state.editing ? editorText() : state.currentNote.content || ''); toast('Copied to clipboard.'); } catch (error) { toast(error.message,'error'); }
}
function downloadNote() {
  const note = state.currentNote, content = state.editing ? editorText() : note?.content || '', title = state.editing ? $('edit-title').value : note?.title || 'Untitled note';
  const link = document.createElement('a'), url = URL.createObjectURL(new Blob([content],{type:'text/plain;charset=utf-8'}));
  link.href = url; link.download = (title.replace(/[<>:"/\\|?*\u0000-\u001f]/g,'_').slice(0,100) || 'Untitled note') + ((state.editing ? state.draftType : note?.type) === 'CODE' ? '.txt' : '.md');
  link.click(); setTimeout(() => URL.revokeObjectURL(url),1000); toast('Download ready.');
}
function formatText(kind) {
  if(state.saving||state.preview||!state.editing)return;
  if(captureEditor){
    if(kind==='link')return showLinkDialog();
    if(kind==='table'){showDialog('table-modal');return;}
    captureEditor.format(kind);updateFormatToolbar();return;
  }
  const area = $('edit-body'); if (area.hidden || area.disabled) return;
  const start = area.selectionStart,end = area.selectionEnd,selected = area.value.slice(start,end), tick = String.fromCharCode(96);
  const wraps = {bold:['**','**','bold text'],italic:['_','_','italic text'],link:['[','](https://example.com)','link text'],code:[tick.repeat(3) + '\n','\n' + tick.repeat(3),'code']};
  let insert,selectionStart,selectionEnd;
  if (wraps[kind]) { const [before,after,fallback] = wraps[kind]; insert = before + (selected || fallback) + after; selectionStart = start + before.length; selectionEnd = selectionStart + (selected || fallback).length; }
  else { const prefix = {heading:'## ',quote:'> ',checklist:'- [ ] '}[kind] || ''; insert = (start && area.value[start-1] !== '\n' ? '\n' : '') + prefix + (selected || 'Your text'); selectionStart = start + insert.length; selectionEnd = selectionStart; }
  area.focus(); area.setSelectionRange(start,end); if (!document.execCommand('insertText',false,insert)) area.setRangeText(insert,start,end,'end'); area.setSelectionRange(selectionStart,selectionEnd); updateDraftCount();
}
async function togglePreview() {
  if (!state.editing || state.saving) return; state.preview = !state.preview; renderEditorType();
  if (state.preview) { if (state.draftType === 'CODE') $('edit-preview').innerHTML = codeBlock(editorText()); else await markdownIn($('edit-preview'),editorText()); }
  else { $('edit-preview').dataset.render = 'hidden';editorFocus(); }
}
function showShortcuts() {
  const rows = [['Find in your archive',modLabel + ' K'],['New note','N'],['Edit selected note','E'],['Save capture',modLabel + ' S / Enter'],['Undo / redo',modLabel + ' Z / Shift Z'],['Bold / italic',modLabel + ' B / I'],['Strikethrough',modLabel + ' Shift X'],['Insert link',modLabel + ' Shift K'],['Preview Markdown',modLabel + ' Shift P'],['Find in this note',modLabel + ' F'],['Indent / outdent',modLabel + ' ] / ['],['Move through toolbar','Tab / arrow keys'],['Return to library / cancel','Esc'],['Show shortcuts','?']];
  $('shortcut-list').innerHTML = rows.map(([label,key]) => '<div class="shortcut-row"><span>' + label + '</span><kbd>' + key + '</kbd></div>').join(''); showDialog('shortcuts-modal');
}
function keydown(event) {
  const key = event.key.toLowerCase(),mod = event.ctrlKey || event.metaKey,input = event.target.matches('input,textarea,select,[contenteditable="true"]');
  if (document.querySelector('dialog[open]')) return;
  if(event.defaultPrevented)return;
  if (mod && key === 'k' && !event.shiftKey) { event.preventDefault(); $('search-input').focus(); $('search-input').select(); return; }
  if (mod && (key === 's' || key==='enter')) { event.preventDefault(); if (state.editing) saveNote(); return; }
  if (state.editing && mod && event.shiftKey && key === 'p') { event.preventDefault(); togglePreview(); return; }
  if (state.editing && (event.target === $('edit-body') || event.target.closest('.cm-editor'))) {
    if (mod && ['b','i'].includes(key)) { event.preventDefault(); formatText(key === 'b' ? 'bold' : 'italic'); return; }
    if (mod && event.shiftKey && key === 'k') { event.preventDefault(); formatText('link'); return; }
  }
  if (key === 'escape') { event.preventDefault(); if (state.editing) leaveEditor(); else backToLibrary(); return; }
  if (input || mod || event.altKey) return;
  if (event.key === '?') { event.preventDefault(); showShortcuts(); }
  else if (key === 'n') { event.preventDefault(); newCapture(event.shiftKey ? 'LINK' : 'TEXT'); }
  else if (key === 'e') { event.preventDefault(); beginEdit(); }
  else if (['j','k'].includes(key) && !state.editing) {
    event.preventDefault(); const current = state.notes.findIndex(n => n.id === state.selectedNoteId), next = Math.max(0,Math.min(state.notes.length-1,current + (key === 'j' ? 1 : -1)));
    if (state.notes[next]) openNote(state.notes[next].id);
  }
}
function setupEventListeners() {
  $('login-form').addEventListener('submit',submitLogin); $('auth-modal').addEventListener('cancel',event => event.preventDefault()); $('logout-btn').onclick = logout;
  $('search-input').oninput = event => { clearTimeout(state.searchDebounceTimer); const value = event.target.value.trim(); state.searchDebounceTimer = setTimeout(() => { state.searchQuery = value; loadNotes(1); },250); };
  $('sort-select').onchange = event => { state.sort = event.target.value; loadNotes(1); };
  $('refresh-btn').onclick = () => Promise.allSettled([loadNotes(state.page),loadFolders(),initTheme()]);
  $('prev-page-btn').onclick = () => { if (state.page > 1) loadNotes(state.page-1); }; $('next-page-btn').onclick = () => { if (state.page < state.totalPages) loadNotes(state.page+1); };
  $('back-notes-btn').onclick = backToLibrary; $('edit-note-btn').onclick = beginEdit; $('cancel-edit-btn').onclick = () => leaveEditor();
  $('new-note-btn').onclick = () => newCapture(); $('empty-create-btn').onclick = () => newCapture();
  $('note-editor').addEventListener('submit',saveNote); $('copy-note-btn').onclick = copyNote; $('download-note-btn').onclick = downloadNote; $('draft-download-btn').onclick = downloadNote;
  $('edit-body').oninput = updateDraftCount; $('preview-btn').onclick = togglePreview;
  $('edit-title').addEventListener('input',scheduleDraft);
  $('edit-title').addEventListener('keydown',event=>{if(event.key==='Enter'&&!event.ctrlKey&&!event.metaKey){event.preventDefault();editorFocus();}});
  ['link-description','media-kind','release-year'].forEach(id=>$(id).addEventListener('input',scheduleDraft));
  $('focus-editor-btn').onclick=()=>{
    const focused=document.querySelector('.workspace').classList.toggle('capture-focus');
    $('focus-editor-btn').setAttribute('aria-pressed',focused);$('focus-editor-btn').setAttribute('aria-label',focused?'Show library':'Hide library');$('focus-editor-btn').title=focused?'Show library':'Hide library';
  };
  document.querySelectorAll('[data-format]').forEach(button => { button.onclick = () => formatText(button.dataset.format); });
  document.querySelectorAll('[data-format]').forEach(button=>{
    button.addEventListener('mousedown',event=>event.preventDefault());
    button.addEventListener('keydown',event=>{
      if(!['ArrowLeft','ArrowRight','Home','End'].includes(event.key))return;
      event.preventDefault();const buttons=[...document.querySelectorAll('[data-format]')].filter(b=>!b.disabled),index=buttons.indexOf(button);
      buttons[event.key==='Home'?0:event.key==='End'?buttons.length-1:(index+(event.key==='ArrowRight'?1:-1)+buttons.length)%buttons.length]?.focus();
    });
  });
  document.querySelectorAll('[data-table-action]').forEach(button=>button.onclick=()=>{captureEditor?.editTable(button.dataset.tableAction);updateFormatToolbar();});
  $('link-form').addEventListener('submit',event=>{
    event.preventDefault();const url=safeUrl($('link-url').value.trim(),false);
    if(!url){$('link-error').textContent='Enter an http, https or mailto link.';return;}
    const label=$('link-label').value;hideDialog('link-modal');captureEditor?.insertLink(label,url);updateFormatToolbar();
  });
  $('table-form').addEventListener('submit',event=>{
    event.preventDefault();const columns=Number($('table-columns').value),rows=Number($('table-rows').value);
    if(!Number.isInteger(columns)||columns<1||columns>10||!Number.isInteger(rows)||rows<1||rows>30)return;
    hideDialog('table-modal');captureEditor?.table(columns,rows);updateFormatToolbar();
  });
  $('resume-draft-btn').onclick=resumeDraft;
  $('dismiss-draft-btn').onclick=async()=>{if(!state.recoveredDraft)return;if(!await confirmDiscard())return;await clearDraft(state.recoveredDraft.key);checkDraftRecovery();};
  $('shortcuts-btn').onclick = showShortcuts; document.querySelectorAll('[data-close]').forEach(button => { button.onclick = () => hideDialog(button.dataset.close); });
  $('new-folder-btn').onclick = showFolderDialog; $('editor-new-folder').onclick = showFolderDialog; $('folder-form').addEventListener('submit',createFolder);
  $('folder-name').addEventListener('input',()=>updateFolderDialog(true));
  $('folder-pin-toggle').onclick=()=>{folderDraft.isPinned=!folderDraft.isPinned;updateFolderDialog(true);};
  $('folder-preview-customize').onclick=()=>{$('folder-color-options').querySelector('[aria-checked=true]')?.focus();};
  $('folder-modal').addEventListener('cancel',event=>{if(folderDraft.busy)event.preventDefault();});
  $('folder-modal').addEventListener('click',event=>{
    if(event.target!==$('folder-modal')||folderDraft.busy)return;
    const bounds=$('folder-modal').getBoundingClientRect();
    if(event.clientX<bounds.left||event.clientX>bounds.right||event.clientY<bounds.top||event.clientY>bounds.bottom)hideDialog('folder-modal');
  });
  $('attachment-input').onchange = event => chooseFile(event.target.files[0]);
  $('import-text-btn').onclick = () => $('text-import-input').click();
  $('text-import-input').onchange = async event => {
    const file = event.target.files[0]; if (!file || state.saving) return;
    if (file.size > 5000000) { toast('Text imports support up to 5 MB. Split this file into smaller notes.','error'); return; }
    try {
      const content = new TextDecoder('utf-8',{fatal:true}).decode(await file.arrayBuffer());
      if (state.saving || !state.editing) return;
      if (editorText() && !await confirmDiscard()) return;
      if(captureEditor)captureEditor.insert(content,0,editorText().length);else setEditorText(content);
      if (!$('edit-title').value) $('edit-title').value = file.name.replace(/\.[^.]+$/,'');
      if(state.isNew)state.draftType='TEXT';state.preview=false;renderCaptureTypes();renderEditorType();updateDraftCount();scheduleDraft();toast('Text imported. Save when you’re ready.');
    } catch (_) { toast('This file is not valid UTF-8 text. Export it as UTF-8 and try again.','error'); }
    finally { event.target.value = ''; }
  };
  const zone = $('dropzone');
  ['dragenter','dragover'].forEach(type => zone.addEventListener(type,event => { event.preventDefault(); zone.classList.add('dragging'); }));
  ['dragleave','drop'].forEach(type => zone.addEventListener(type,event => { event.preventDefault(); zone.classList.remove('dragging'); }));
  zone.addEventListener('drop',event => { if (!state.saving) chooseFile(event.dataTransfer.files[0]); });
  document.addEventListener('click',async event => {
    const button = event.target.closest('.copy-code'); if (!button) return;
    try { await copyText(button.closest('.code-block').querySelector('code').textContent); toast('Code copied.'); } catch (error) { toast(error.message,'error'); }
  });
  document.addEventListener('keydown',keydown);
  window.addEventListener('beforeunload',event => { if (editorDirty() || state.saving) { event.preventDefault(); event.returnValue = ''; } });
  document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='hidden')persistDraft();});
  window.addEventListener('pagehide',persistDraft);
  window.addEventListener('offline',() => setConnection('Disconnected','disconnected'));
  window.addEventListener('online',() => { if (state.authenticated) loadNotes(state.page); });
}
document.addEventListener('DOMContentLoaded',() => {
  if(window.CobaltEditor)captureEditor=new window.CobaltEditor($('capture-editor-host'),content=>{
    $('edit-body').value=content;updateDraftCount();scheduleDraft();
  },action=>{if(action==='link')showLinkDialog();else updateFormatToolbar();});
  $('search-key').textContent = modLabel + ' K'; renderNavigation(); setupEventListeners(); initTheme(); checkAuth();
  setInterval(async () => { if (!state.authenticated) return; try { const data = await api('/api/auth/status'); if (!data.authenticated) locked(); } catch (_) { setConnection('Disconnected','disconnected'); } },30000);
});
