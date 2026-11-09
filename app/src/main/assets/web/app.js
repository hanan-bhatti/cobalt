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
  searchDebounceTimer: null
};

document.addEventListener('DOMContentLoaded', () => {
  initTheme();
  checkAuth();
  setupEventListeners();
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
      loadNotes(1);
    } else {
      modal.style.display = 'flex';
      document.getElementById('pin-input').focus();
    }
  } catch (err) {
    console.error('Auth status check failed:', err);
    document.getElementById('auth-modal').style.display = 'flex';
  }
}

async function submitLogin() {
  const pinInput = document.getElementById('pin-input');
  const errorEl = document.getElementById('modal-error');
  const btn = document.getElementById('login-btn');
  const pin = pinInput.value.trim();

  if (!pin || pin.length < 4) {
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
      loadNotes(1);
    } else if (res.status === 429) {
      startLockoutCountdown(data.remainingLockoutSeconds || 60);
    } else {
      errorEl.textContent = data.message || 'Incorrect PIN.';
      pinInput.value = '';
      pinInput.focus();
    }
  } catch (err) {
    errorEl.textContent = 'Connection error. Ensure your phone and PC are on the same Wi-Fi.';
  } finally {
    btn.disabled = false;
  }
}

function startLockoutCountdown(seconds) {
  const errorEl = document.getElementById('modal-error');
  const btn = document.getElementById('login-btn');
  const input = document.getElementById('pin-input');

  btn.disabled = true;
  input.disabled = true;
  let remaining = seconds;

  const timer = setInterval(() => {
    errorEl.textContent = `Too many failed attempts. Locked out for ${remaining}s...`;
    remaining--;
    if (remaining < 0) {
      clearInterval(timer);
      btn.disabled = false;
      input.disabled = false;
      errorEl.textContent = 'You may now try again.';
      input.focus();
    }
  }, 1000);
}

async function logout() {
  try {
    await fetch('/api/auth/logout', { method: 'POST' });
  } catch (_) {}
  state.authenticated = false;
  document.getElementById('auth-modal').style.display = 'flex';
  document.getElementById('pin-input').value = '';
}

// 3. Notes Loading & Pagination
async function loadNotes(page = 1) {
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
    const res = await fetch(`/api/notes?${params.toString()}`);
    if (res.status === 401) {
      checkAuth();
      return;
    }
    const data = await res.json();

    state.notes = data.items || [];
    state.page = data.page || 1;
    state.totalPages = data.totalPages || 1;
    state.totalItems = data.totalItems || 0;

    renderNotesList();
    renderPagination();
  } catch (err) {
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
  state.selectedNoteId = id;
  renderNotesList(); // Update active card highlighting

  const readerPane = document.getElementById('reader-pane');
  const emptyState = document.getElementById('empty-state');
  const readerContent = document.getElementById('reader-content');

  emptyState.style.display = 'none';
  readerContent.style.display = 'block';
  document.getElementById('reader-body').innerHTML = '<div style="opacity: 0.6; padding: 32px 0;">Loading note content...</div>';

  try {
    const res = await fetch(`/api/notes/${id}`);
    if (res.status === 401) {
      checkAuth();
      return;
    }
    const note = await res.json();

    document.getElementById('reader-title').textContent = note.title || 'Untitled Note';
    document.getElementById('reader-date').textContent = note.updatedAt
      ? `Last updated ${new Date(note.updatedAt).toLocaleString()}`
      : '';

    const tagsContainer = document.getElementById('reader-tags');
    tagsContainer.innerHTML = '';
    if (Array.isArray(note.folders)) {
      note.folders.forEach(f => {
        const pill = document.createElement('span');
        pill.className = 'tag-pill';
        pill.textContent = f;
        tagsContainer.appendChild(pill);
      });
    }

    const lockAlert = document.getElementById('mobile-lock-alert');
    if (note.isLockedByMobile) {
      lockAlert.style.display = 'flex';
      lockAlert.innerHTML = `
        <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M12 2C9.24 2 7 4.24 7 7v3H6c-1.1 0-2 .9-2 2v8c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2v-8c0-1.1-.9-2-2-2h-1V7c0-2.76-2.24-5-5-5zm3 8H9V7c0-1.66 1.34-3 3-3s3 1.34 3 3v3z"/></svg>
        <span>This note is open in Cobalt on your phone. Modifications must be completed on your device.</span>
      `;
    } else {
      lockAlert.style.display = 'none';
    }

    document.getElementById('reader-body').innerHTML = renderMarkdown(note.content || '');
  } catch (err) {
    document.getElementById('reader-body').innerHTML = '<div style="color: var(--danger-color);">Failed to load note content.</div>';
  }
}

// 5. Lightweight Built-in Markdown Engine
function renderMarkdown(md) {
  if (!md) return '<p style="opacity: 0.5;">Empty note</p>';

  // Pre-process code blocks to prevent inner formatting
  const codeBlocks = [];
  let html = md.replace(/```([a-zA-Z0-9_-]*)\n([\s\S]*?)```/g, (match, lang, code) => {
    const idx = codeBlocks.length;
    codeBlocks.push(`<pre><code class="language-${lang}">${escapeHtml(code.trim())}</code></pre>`);
    return `%%CODEBLOCK_${idx}%%`;
  });

  // Process headers
  html = html
    .replace(/^#### (.*$)/gim, '<h4>$1</h4>')
    .replace(/^### (.*$)/gim, '<h3>$1</h3>')
    .replace(/^## (.*$)/gim, '<h2>$1</h2>')
    .replace(/^# (.*$)/gim, '<h1>$1</h1>');

  // Blockquotes
  html = html.replace(/^\> (.*$)/gim, '<blockquote>$1</blockquote>');

  // Horizontal Rule
  html = html.replace(/^---$/gim, '<hr style="border: none; border-top: 1px solid var(--outline-color); margin: 20px 0;">');

  // Inline formatting
  html = html
    .replace(/\*\*(.*?)\*\*/gim, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/gim, '<em>$1</em>')
    .replace(/`([^`]+)`/gim, '<code>$1</code>');

  // Links and Images
  html = html.replace(/!\[(.*?)\]\((.*?)\)/gim, '<img alt="$1" src="$2" style="max-width: 100%; border-radius: 8px; margin: 12px 0;">');
  html = html.replace(/\[(.*?)\]\((.*?)\)/gim, '<a href="$2" target="_blank" rel="noopener" style="color: var(--primary-color); text-decoration: underline;">$1</a>');

  // Unordered Lists
  html = html.replace(/^\s*-\s+(.*$)/gim, '<li>$1</li>');
  html = html.replace(/(<li>[\s\S]*?<\/li>)/gim, '<ul>$1</ul>');

  // Paragraphs
  html = html.split('\n\n').map(p => {
    if (p.startsWith('<h') || p.startsWith('<pre') || p.startsWith('<ul') || p.startsWith('<blockquote') || p.startsWith('<hr')) {
      return p;
    }
    return `<p>${p.replace(/\n/g, '<br>')}</p>`;
  }).join('');

  // Restore code blocks
  codeBlocks.forEach((block, idx) => {
    html = html.replace(`%%CODEBLOCK_${idx}%%`, block);
  });

  return html;
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

  document.getElementById('copy-note-btn').addEventListener('click', () => {
    const title = document.getElementById('reader-title').textContent;
    const body = document.getElementById('reader-body').innerText;
    const textToCopy = `${title}\n\n${body}`;
    navigator.clipboard.writeText(textToCopy).then(() => {
      const btn = document.getElementById('copy-note-btn');
      const originalText = btn.innerHTML;
      btn.innerHTML = '<span>Copied!</span>';
      setTimeout(() => btn.innerHTML = originalText, 1500);
    });
  });
}
