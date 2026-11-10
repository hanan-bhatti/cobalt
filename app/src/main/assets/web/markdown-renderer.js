/* Safe GFM rendering with Cobalt's native reading and code styles. */
function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
}

function safeUrl(value, local = true) {
  const url = String(value || '');
  if (local && /^\/api\/media\/[A-Za-z0-9_%.-]+(?:\?thumbnail=1)?$/.test(url)) return url;
  try {
    const parsed = new URL(url);
    return ['https:', 'http:', 'mailto:'].includes(parsed.protocol) && !parsed.username && !parsed.password ? parsed.href : null;
  } catch (_) { return null; }
}

function codeBlock(text, language = '') {
  const highlighter = window.cobaltHighlight;
  let highlighted = escapeHtml(text), label = String(language).split(/\s/)[0].toLowerCase();
  // Highlighting a huge fence can be expensive; its source is still displayed in full.
  if (highlighter && text.length <= 50000) {
    try {
      if (label && highlighter.getLanguage(label)) highlighted = highlighter.highlight(text, {language: label, ignoreIllegals: true}).value;
      else if (!label) {
        const result = highlighter.highlightAuto(text, ['kotlin','javascript','typescript','python','java','json','bash','sql']);
        highlighted = result.value; label = result.language || '';
      }
    } catch (_) { /* Preserve readable code if the highlighter fails. */ }
  }
  return `<div class="code-block"><div class="code-heading"><span>${escapeHtml(label || 'Code')}</span><button type="button" class="copy-code quiet" aria-label="Copy code">Copy code</button></div><pre dir="ltr"><code>${highlighted}</code></pre></div>`;
}

function markdownOptions() {
  const renderer = new window.marked.Renderer();
  renderer.html = ({text}) => escapeHtml(text);
  renderer.link = function({href, title, tokens}) {
    const label = this.parser.parseInline(tokens), url = safeUrl(href);
    return url ? `<a href="${escapeHtml(url)}"${title ? ` title="${escapeHtml(title)}"` : ''} target="_blank" rel="noopener noreferrer">${label}</a>` : label;
  };
  renderer.image = ({href, text}) => {
    // Local attachments render directly. External images remain explicit links.
    const local = safeUrl(href);
    return local?.startsWith('/api/media/') ? `<img src="${escapeHtml(local)}" alt="${escapeHtml(text)}" loading="lazy">` :
      local ? `<a class="image-link" href="${escapeHtml(local)}" target="_blank" rel="noopener noreferrer">${escapeHtml(text || 'Open image')} ↗</a>` : escapeHtml(text);
  };
  renderer.code = ({text, lang}) => codeBlock(text, lang);
  renderer.heading = function({depth, tokens}) { return `<h${depth} dir="auto">${this.parser.parseInline(tokens)}</h${depth}>`; };
  renderer.paragraph = function({tokens}) { return `<p dir="auto">${this.parser.parseInline(tokens)}</p>`; };
  renderer.blockquote = function({tokens}) { return `<blockquote dir="auto">${this.parser.parse(tokens)}</blockquote>`; };
  renderer.listitem = function(item) {
    return `<li${item.task ? ` class="task-item${item.checked ? ' task-done' : ''}"` : ''}>${item.task ? `<input type="checkbox" disabled aria-label="${item.checked ? 'Completed' : 'Incomplete'} task"${item.checked ? ' checked' : ''}>` : ''}${this.parser.parse(item.tokens, !!item.loose)}</li>`;
  };
  const table = renderer.table.bind(renderer);
  renderer.table = token => `<div class="table-scroll" tabindex="0" role="region" aria-label="Note table">${table(token)}</div>`;
  return {gfm: true, breaks: false, renderer};
}

function renderMarkdown(source) {
  if (!source) return '<p class="muted">This note is waiting for a thought.</p>';
  return window.marked.parse(String(source), markdownOptions());
}

function renderMarkdownChunks(source) {
  const options = markdownOptions(), tokens = window.marked.lexer(source, options), chunks = [];
  for (let i = 0; i < tokens.length; i += 60) chunks.push(window.marked.parser(tokens.slice(i, i + 60), options));
  return chunks.length ? chunks : [renderMarkdown('')];
}
