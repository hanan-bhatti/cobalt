// Large documents parse off the UI thread; complete Markdown blocks stay together.
self.window = self;
importScripts('/markdown.js', '/highlight.js', '/markdown-renderer.js');
self.onmessage = ({data}) => {
  try { self.postMessage({id: data.id, chunks: renderMarkdownChunks(data.content)}); }
  catch (_) { self.postMessage({id: data.id, error: 'Could not render this note. Its original text is available in the editor or download.'}); }
};
