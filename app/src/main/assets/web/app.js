// Cobalt Local Web Companion - Client Application
let state = {
  authenticated: false,
  notes: [],
  page: 1,
  totalPages: 1,
  totalItems: 0,
  selectedNoteId: null,
  activeFilterType: '',
  searchQuery: '',
  searchDebounceTimer: null,
  currentNote: null,
  editing: false,
  saving: false,
  noteRequest: 0,
  listRequest: 0,
  heartbeat: null,
  lockoutTimer: null,
  acquiringLock: false
};

document.addEventListener('DOMContentLoaded', () => {
  initTheme();
  checkAuth();
  setupEventListeners();
  setInterval(async () => {
    if (!state.authenticated) return;
    try {
      const result = await api('/api/auth/status');
      if (!result.authenticated) {
        state.authenticated = false;
        setConnection('Locked');
        document.getElementById('auth-modal').style.display = 'flex';
      }
    } catch (_) { setConnection('Disconnected'); }
  }, 15000);
});

// 1. Theme Synchronization (Mirrors phone's Material You / Dark Theme)
async function initTheme() {
  try {
    const res = await fetch('/api/theme');
    if (!res.ok) return;
    const data = await res.json();
    if (!data.colors) return;

    const root = document.documentElement;
    const c = data.colors;

    if (c.primary) root.style.setProperty('--primary-color', c.primary);
    if (c.onPrimary) root.style.setProperty('--on-primary', c.onPrimary);
    if (c.primaryContainer) root.style.setProperty('--primary-container', c.primaryContainer);
    if (c.onPrimaryContainer) root.style.setProperty('--on-primary-container', c.onPrimaryContainer);
    if (c.background) root.style.setProperty('--bg-color', c.background);
    if (c.onBackground) root.style.setProperty('--on-bg-color', c.onBackground);
    if (c.surface) root.style.setProperty('--surface-color', c.surface);
    if (c.onSurface) root.style.setProperty('--on-surface', c.onSurface);
    if (c.surfaceContainer) root.style.setProperty('--surface-container', c.surfaceContainer);
    if (c.surfaceContainerHigh) root.style.setProperty('--surface-container-high', c.surfaceContainerHigh);
    if (c.outline) root.style.setProperty('--outline-color', c.outline);
  } catch (err) {
    console.warn('Could not sync theme with phone, using defaults:', err);
  }
}

// 2. Authentication Flow
async function checkAuth() {
  try {
    const res = await fetch('/api/auth/status');
    const data = await res.json();
    state.authenticated = data.authenticated === true;

    const modal = document.getElementById('auth-modal');
    if (state.authenticated) {
      modal.style.display = 'none';
      setConnection("Phone Connected");
      loadNotes(1);
    } else {
      setConnection('Locked');
      modal.style.display = 'flex';
      document.getElementById('pin-input').focus();
    }
  } catch (err) {
    console.error('Auth status check failed:', err);
    setConnection('Disconnected');
    document.getElementById('auth-modal').style.display = 'flex';
  }
}

async function submitLogin() {
  const pinInput = document.getElementById('pin-input');
  const errorEl = document.getElementById('modal-error');
  const btn = document.getElementById('login-btn');
  if (btn.disabled) return;
  const pin = pinInput.value.trim();

  if (!/^\d{4}$/.test(pin)) {
    errorEl.textContent = 'Please enter a 4-digit PIN.';
    return;
  }

  btn.disabled = true;
  errorEl.textContent = '';

  try {
    const res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ pin })
    });

    const data = await res.json();
    if (res.ok && data.authenticated) {
      state.authenticated = true;
      document.getElementById('auth-modal').style.display = 'none';
      pinInput.value = '';
      setConnection("Phone Connected");
      loadNotes(1);
    } else if (res.status === 429) {
      startLockoutCountdown(data.retryAfterSeconds || 60);
    } else {
      errorEl.textContent = data.message || 'Incorrect PIN.';
      pinInput.value = '';
      pinInput.focus();
    }
  } catch (err) {
    errorEl.textContent = 'Connection error. Ensure your phone and PC are on the same Wi-Fi.';
  } finally {
    btn.disabled = Boolean(state.lockoutTimer);
  }
}

function startLockoutCountdown(seconds) {
  const errorEl = document.getElementById('modal-error');
  const btn = document.getElementById('login-btn');
  const input = document.getElementById('pin-input');

  btn.disabled = true;
  input.disabled = true;
  let remaining = seconds;

  clearInterval(state.lockoutTimer);
  state.lockoutTimer = setInterval(() => {
    errorEl.textContent = `Too many failed attempts. Locked out for ${remaining}s...`;
    remaining--;
    if (remaining < 0) {
      clearInterval(state.lockoutTimer);
      state.lockoutTimer = null;
      btn.disabled = false;
      input.disabled = false;
      errorEl.textContent = 'You may now try again.';
      input.focus();
    }
  }, 1000);
}

async function logout() {
  if (!await leaveEditor()) return;
  try { await api('/api/auth/logout', { method: 'POST', body: '{}' }); } catch (_) {}
  state.authenticated = false;
  state.notes = [];
  state.currentNote = null;
  state.selectedNoteId = null;
  state.noteRequest++;
  state.listRequest++;
  document.getElementById('notes-list').replaceChildren();
  document.getElementById('reader-body').replaceChildren();
  document.getElementById('reader-content').style.display = 'none';
  document.getElementById('empty-state').style.display = 'flex';
  document.getElementById('auth-modal').style.display = 'flex';
  document.getElementById('pin-input').value = '';
  setConnection('Locked');
}

function setConnection(text) { document.getElementById('connection-status').textContent = text; }
async function api(path, options = {}) {
  const res = await fetch(path, { credentials: 'same-origin', ...options,
    headers: { 'Content-Type': 'application/json', ...options.headers } });
  const text = await res.text();
  let data;
  try { data = JSON.parse(text); } catch (_) { data = { message: text }; }
  if (!res.ok) {
    if (res.status === 401) {
      state.authenticated = false;
      setConnection('Locked');
      document.getElementById('auth-modal').style.display = 'flex';
    }
    const error = new Error(data.message || `Request failed (${res.status})`);
    error.status = res.status;
    throw error;
  }
  setConnection(state.authenticated ? 'Phone Connected' : 'Locked');
  return data;
}

// 3. Notes Loading & Pagination
async function loadNotes(page = 1) {
  const requestId = ++state.listRequest;
  state.page = page;
  const listEl = document.getElementById('notes-list');
  listEl.innerHTML = '<div style="padding: 24px; text-align: center; opacity: 0.6;">Loading notes...</div>';

  const params = new URLSearchParams({
    page: state.page,
    limit: 25
  });
  if (state.searchQuery) params.set('q', state.searchQuery);
  if (state.activeFilterType) params.set('type', state.activeFilterType);

  try {
    const data = await api(`/api/notes?${params.toString()}`);
    if (requestId !== state.listRequest) return;

    state.notes = data.items || [];
    state.page = data.page || 1;
    state.totalPages = data.totalPages || 1;
    state.totalItems = data.totalItems || 0;

    renderNotesList();
    renderPagination();
  } catch (err) {
    if (requestId !== state.listRequest) return;
    setConnection(err.status === 401 ? 'Locked' : 'Connection error');
    listEl.innerHTML = '<div style="padding: 24px; text-align: center; color: var(--danger-color);">Failed to load notes. Check connection.</div>';
  }
}

function renderNotesList() {
  const listEl = document.getElementById('notes-list');
  if (state.notes.length === 0) {
    listEl.innerHTML = '<div style="padding: 32px; text-align: center; opacity: 0.6;">No notes found.</div>';
    return;
  }

  listEl.innerHTML = '';
  state.notes.forEach(note => {
    const card = document.createElement('div');
    card.tabIndex = 0;
    card.setAttribute('role', 'button');
    card.addEventListener('keydown', e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); openNote(note.id); } });
    card.className = `note-card ${note.id === state.selectedNoteId ? 'active' : ''}`;
    card.onclick = () => openNote(note.id);

    const lockBadgeHtml = note.isLockedByMobile
      ? `<span class="lock-badge"><svg viewBox="0 0 24 24" width="12" height="12" fill="currentColor"><path d="M12 2C9.24 2 7 4.24 7 7v3H6c-1.1 0-2 .9-2 2v8c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2v-8c0-1.1-.9-2-2-2h-1V7c0-2.76-2.24-5-5-5zm3 8H9V7c0-1.66 1.34-3 3-3s3 1.34 3 3v3z"/></svg>Phone Editing</span>`
      : '';

    const dateStr = note.updatedAt ? new Date(note.updatedAt).toLocaleDateString() : '';

    card.innerHTML = `
      <div class="note-card-title">
        <span>${escapeHtml(note.title)}</span>
        ${lockBadgeHtml}
      </div>
      <div class="note-card-preview">${escapeHtml(note.preview || '')}</div>
      <div class="note-card-meta">
        <span>${escapeHtml(note.type || 'NOTE')}</span>
        <span>${dateStr}</span>
      </div>
    `;
    listEl.appendChild(card);
  });
}

function renderPagination() {
  document.getElementById('page-indicator').textContent = `Page ${state.page} of ${state.totalPages}`;
  document.getElementById('prev-page-btn').disabled = state.page <= 1;
  document.getElementById('next-page-btn').disabled = state.page >= state.totalPages;
}

// 4. Note Detail & Markdown Rendering
async function openNote(id) {
  if (!await leaveEditor()) return;
  const requestId = ++state.noteRequest;
  state.selectedNoteId = id;
  state.currentNote = null;
  renderNotesList();
  document.getElementById('reader-pane').classList.add('active-mobile');
  document.getElementById('empty-state').style.display = 'none';
  document.getElementById('reader-content').style.display = 'block';
  document.getElementById('edit-note-btn').disabled = true;
  document.getElementById('copy-note-btn').disabled = true;
  document.getElementById('reader-body').textContent = 'Loading note…';
  try {
    const note = await api(`/api/notes/${encodeURIComponent(id)}`);
    if (requestId !== state.noteRequest) return;
    state.currentNote = note;
    renderNote(note);
  } catch (err) {
    if (requestId !== state.noteRequest) return;
    document.getElementById('reader-body').textContent = err.message || 'Could not load note.';
  }
}

function renderNote(note) {
  document.getElementById('reader-title').textContent = note.title || 'Untitled Note';
  document.getElementById('reader-date').textContent = `Last updated ${new Date(note.updatedAt).toLocaleString()}`;
  const tags = document.getElementById('reader-tags');
  tags.replaceChildren();
  (note.folders || []).forEach(folder => { const pill = document.createElement('span'); pill.className = 'tag-pill'; pill.textContent = folder; tags.append(pill); });
  const lock = document.getElementById('mobile-lock-alert');
  lock.style.display = note.isLockedByMobile || note.isLockedByOtherWeb ? 'flex' : 'none';
  lock.textContent = note.isLockedByMobile ? 'Your phone is editing this note. You can read it here.' : 'Another browser is editing this note.';
  document.getElementById('edit-note-btn').disabled = !note.editable || note.isLockedByMobile || note.isLockedByOtherWeb;
  document.getElementById('copy-note-btn').disabled = false;
  const body = document.getElementById('reader-body');
  if (note.type === 'CODE' || note.content.length > 200000) {
    const pre = document.createElement('div'); pre.className = 'plain-note'; pre.textContent = note.content; body.replaceChildren(pre);
  } else { body.innerHTML = renderMarkdown(note.content || ''); }
  if (note.mediaUrl) {
    const element = document.createElement(note.type === 'IMAGE' ? 'img' : note.type === 'VIDEO' ? 'video' : 'audio');
    element.src = note.mediaUrl; element.controls = true; element.alt = note.title; body.prepend(element);
    element.addEventListener('error', () => { const error = document.createElement('p'); error.textContent = 'Attachment unavailable here. Open it on your phone.'; element.replaceWith(error); });
  }
}

async function beginEdit() {
  if (!state.currentNote || state.editing || state.acquiringLock) return;
  const id = state.currentNote.id;
  state.acquiringLock = true;
  document.getElementById('edit-note-btn').disabled = true;
  try {
    const base = await api(`/api/notes/${encodeURIComponent(id)}/lock`, { method: 'POST', body: '{}' });
    state.currentNote = base;
    state.editing = true;
    document.getElementById('edit-title').value = base.title;
    document.getElementById('edit-body').value = base.content;
    document.getElementById('note-editor').hidden = false;
    document.getElementById('reader-body').hidden = true;
    document.getElementById('edit-note-btn').disabled = true;
    document.getElementById('edit-message').textContent = 'Changes stay here until you save.';
    document.getElementById('edit-body').focus();
    state.heartbeat = setInterval(async () => {
      try { await api(`/api/notes/${encodeURIComponent(id)}/lock`, { method: 'PUT', body: '{}' }); }
      catch (err) { document.getElementById('edit-message').textContent = `${err.message} Copy your draft before leaving.`; }
    }, 20000);
  } catch (err) { document.getElementById('mobile-lock-alert').style.display = 'flex'; document.getElementById('mobile-lock-alert').textContent = err.message; document.getElementById('edit-note-btn').disabled = false; }
  finally { state.acquiringLock = false; }
}

function editorDirty() {
  return state.editing && (document.getElementById('edit-title').value !== state.currentNote.title || document.getElementById('edit-body').value !== state.currentNote.content.replace(/\r\n?/g, '\n'));
}
async function leaveEditor(saved = false) {
  if (state.saving || state.acquiringLock) return false;
  if (!state.editing) return true;
  if (!saved && editorDirty() && !window.confirm('Discard your unsaved changes?')) return false;
  clearInterval(state.heartbeat);
  try { await api(`/api/notes/${encodeURIComponent(state.currentNote.id)}/lock`, { method: 'DELETE', body: '{}' }); } catch (_) {}
  state.editing = false;
  document.getElementById('note-editor').hidden = true;
  document.getElementById('reader-body').hidden = false;
  document.getElementById('edit-title').value = '';
  document.getElementById('edit-body').value = '';
  if (state.currentNote) renderNote(state.currentNote);
  return true;
}
async function saveNote(event) {
  event.preventDefault();
  if (!state.editing || state.saving) return;
  const base = state.currentNote;
  const edited = document.getElementById('edit-body').value;
  const content = edited === base.content.replace(/\r\n?/g, '\n') ? base.content : edited;
  const message = document.getElementById('edit-message');
  if (new TextEncoder().encode(content).length > 5000000) { message.textContent = 'The web editor supports up to 5 MB. Your draft is still here.'; return; }
  state.saving = true;
  document.getElementById('save-note-btn').disabled = true;
  document.getElementById('cancel-edit-btn').disabled = true;
  document.getElementById('edit-title').disabled = true;
  document.getElementById('edit-body').disabled = true;
  message.textContent = 'Saving to your phone…';
  try {
    const saved = await api(`/api/notes/${encodeURIComponent(base.id)}`, { method: 'PUT', body: JSON.stringify({ title: document.getElementById('edit-title').value, content, updatedAt: base.updatedAt, contentHash: base.contentHash }) });
    state.saving = false;
    state.currentNote = saved;
    await leaveEditor(true);
    document.getElementById('reader-date').textContent = 'Saved to your phone';
    loadNotes(state.page);
  } catch (err) { message.textContent = `${err.message} Your unsaved text is still here.`; }
  finally { state.saving = false; document.getElementById('save-note-btn').disabled = false; document.getElementById('cancel-edit-btn').disabled = false; document.getElementById('edit-title').disabled = false; document.getElementById('edit-body').disabled = false; }
}

// Raw HTML is text, links have an allowlist, and only authenticated local media is embedded.
function inlineMarkdown(text) {
  const pattern = /(`[^`]+`|!?\[[^\]\n]*\]\([^\s)]*\)|\*\*[^*\n]+\*\*|\*[^*\n]+\*)/g;
  let out = '', offset = 0;
  for (const match of text.matchAll(pattern)) {
    out += escapeHtml(text.slice(offset, match.index));
    const token = match[0];
    if (token.startsWith('`')) out += `<code>${escapeHtml(token.slice(1, -1))}</code>`;
    else if (token.startsWith('**')) out += `<strong>${escapeHtml(token.slice(2, -2))}</strong>`;
    else if (token.startsWith('*')) out += `<em>${escapeHtml(token.slice(1, -1))}</em>`;
    else {
      const parts = token.match(/^(!?)\[([^\]]*)\]\(([^)]*)\)$/);
      const [, image, label, url] = parts;
      const local = /^\/api\/media\/[A-Za-z0-9_%.-]+$/.test(url);
      if (image && local) out += `<img alt="${escapeHtml(label)}" src="${escapeHtml(url)}">`;
      else if (/^https?:\/\//i.test(url) || local) out += `<a href="${escapeHtml(url)}" target="_blank" rel="noopener noreferrer">${escapeHtml(label)}</a>`;
      else out += escapeHtml(token);
    }
    offset = match.index + token.length;
  }
  return out + escapeHtml(text.slice(offset));
}
function renderMarkdown(md) {
  if (!md) return '<p>Empty note</p>';
  const lines = md.split(/\r?\n/), blocks = [];
  let code = null, paragraph = [], list = [];
  const flush = () => {
    if (paragraph.length) blocks.push(`<p>${paragraph.map(inlineMarkdown).join('<br>')}</p>`);
    if (list.length) blocks.push(`<ul>${list.map(t => `<li>${inlineMarkdown(t)}</li>`).join('')}</ul>`);
    paragraph = []; list = [];
  };
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (line.startsWith('```')) { flush(); if (code !== null) { blocks.push(`<pre><code>${escapeHtml(code.join('\n'))}</code></pre>`); code = null; } else code = []; continue; }
    if (code !== null) { code.push(line); continue; }
    if (!line.trim()) { flush(); continue; }
    const heading = line.match(/^(#{1,6}) (.*)$/);
    if (heading) { flush(); blocks.push(`<h${heading[1].length}>${inlineMarkdown(heading[2])}</h${heading[1].length}>`); continue; }
    if (line.startsWith('> ')) { flush(); blocks.push(`<blockquote>${inlineMarkdown(line.slice(2))}</blockquote>`); continue; }
    if (/^[-*] /.test(line)) { if (paragraph.length) flush(); list.push(line.slice(2)); continue; }
    if (/^---+$/.test(line)) { flush(); blocks.push('<hr>'); continue; }
    if (line.includes('|') && i + 1 < lines.length && /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$/.test(lines[i + 1])) {
      flush(); const cells = row => row.replace(/^\s*\||\|\s*$/g, '').split('|').map(c => inlineMarkdown(c.trim()));
      let table = `<table><thead><tr>${cells(line).map(c => `<th>${c}</th>`).join('')}</tr></thead><tbody>`; i++;
      while (i + 1 < lines.length && lines[i + 1].includes('|')) table += `<tr>${cells(lines[++i]).map(c => `<td>${c}</td>`).join('')}</tr>`;
      blocks.push(table + '</tbody></table>'); continue;
    }
    if (list.length) flush(); paragraph.push(line);
  }
  flush(); if (code !== null) blocks.push(`<pre><code>${escapeHtml(code.join('\n'))}</code></pre>`);
  return blocks.join('');
}

function escapeHtml(str) {
  return (str || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

// 6. Event Listeners
function setupEventListeners() {
  document.getElementById('search-input').addEventListener('input', (e) => {
    clearTimeout(state.searchDebounceTimer);
    state.searchDebounceTimer = setTimeout(() => {
      state.searchQuery = e.target.value.trim();
      loadNotes(1);
    }, 300);
  });

  document.querySelectorAll('.filter-chip').forEach(chip => {
    chip.addEventListener('click', () => {
      document.querySelectorAll('.filter-chip').forEach(c => c.classList.remove('active'));
      chip.classList.add('active');
      state.activeFilterType = chip.getAttribute('data-type') || '';
      loadNotes(1);
    });
  });

  document.getElementById('prev-page-btn').addEventListener('click', () => {
    if (state.page > 1) loadNotes(state.page - 1);
  });

  document.getElementById('next-page-btn').addEventListener('click', () => {
    if (state.page < state.totalPages) loadNotes(state.page + 1);
  });

  document.getElementById('login-btn').addEventListener('click', submitLogin);
  document.getElementById('pin-input').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') submitLogin();
  });

  document.getElementById('logout-btn').addEventListener('click', logout);

  document.getElementById('back-notes-btn').addEventListener('click', async () => {
    if (await leaveEditor()) document.getElementById('reader-pane').classList.remove('active-mobile');
  });
  document.getElementById('edit-note-btn').addEventListener('click', beginEdit);
  document.getElementById('cancel-edit-btn').addEventListener('click', () => leaveEditor());
  document.getElementById('note-editor').addEventListener('submit', saveNote);
  window.addEventListener('beforeunload', event => { if (editorDirty() || state.saving) { event.preventDefault(); event.returnValue = ''; } });
  document.getElementById('copy-note-btn').addEventListener('click', async () => {
    if (!state.currentNote) return;
    const text = `${state.currentNote.title}\n\n${state.editing ? document.getElementById('edit-body').value : state.currentNote.content}`;
    try {
      if (navigator.clipboard && window.isSecureContext) await navigator.clipboard.writeText(text);
      else {
        const input = document.createElement('textarea'); input.value = text; input.style.position = 'fixed'; input.style.opacity = '0'; document.body.append(input); input.select();
        const copied = document.execCommand('copy'); input.remove(); if (!copied) throw new Error('Copy failed');
      }
      document.getElementById('copy-note-btn').textContent = 'Copied!';
      setTimeout(() => document.getElementById('copy-note-btn').textContent = 'Copy Text', 1500);
    } catch (_) { document.getElementById('copy-note-btn').textContent = 'Select text to copy'; }
  });
}
