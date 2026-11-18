import { useEffect, useState } from 'react';
import { marked } from 'marked';
import type { Item } from '../lib/types';
import { api, date, safeUrl } from '../lib/api';
import { Icon } from './Icon';
// Render parsed Markdown as text: no HTML from saved content enters the DOM.
function Markdown({ body, title }: {
    body: string; title: string;
}) { return <>{marked.lexer(body).map((token, index) => { switch (token.type) {
    case 'heading': if(index===0 && token.text.trim()===title.trim())return null; return <h2 key={index}>{token.text}</h2>;
    case 'paragraph': return <p key={index}>{token.text}</p>;
    case 'code': return <pre key={index}><code>{token.text}</code></pre>;
    case 'blockquote': return <blockquote key={index}>{token.text}</blockquote>;
    case 'list': return <ul key={index}>{token.items.map((item: {
        task?: boolean;
        checked?: boolean;
        text: string;
    }, j: number) => <li key={j}>{item.task && <input type="checkbox" checked={!!item.checked} readOnly aria-label={item.text}/>} {item.text}</li>)}</ul>;
    case 'space': return null;
    default: return <p key={index}>{'text' in token ? String(token.text) : token.raw}</p>;
} })}</>; }
export function Reader({ id, promote, close, dedicated, onTitle }: {
    id: string;
    promote: () => void;
    close: () => void;
    dedicated: boolean;
    onTitle: (title: string) => void;
}) { const [item, setItem] = useState<Item>(); const [error, setError] = useState(''); useEffect(() => { const controller = new AbortController(); setItem(undefined); setError(''); api<Item>('/notes/' + encodeURIComponent(id), {}, controller.signal).then(note => {setItem(note); onTitle(note.title)}).catch(e => { if (e.name !== 'AbortError')
    setError(e.message); }); return () => controller.abort(); }, [id, onTitle]); return <section className="reader" aria-label="Content preview"><header className="reader-toolbar"><span className="badge"><Icon name="text" size={16}/>Preview</span><div>{!dedicated && <button disabled={!item} onClick={promote}><Icon name="external" size={17}/>Open in new tab</button>}<button onClick={close} aria-label="Close preview"><Icon name="close" size={19}/></button></div></header>{error ? <div className="empty" role="alert">{error}</div> : !item ? <div className="empty" role="status">Opening capture…</div> : <><article className="document"><h1>{item.title}</h1><div className="document-meta">{item.folders.join(' · ')} · {date(item.timestamp)}</div>{item.isLockedByMobile && <p className="notice">This note is being edited on your phone.</p>}{item.type === 'TEXT' ? <Markdown body={item.content || ''} title={item.title}/> : item.type === 'CODE' ? <pre><code>{item.content}</code></pre> : item.type === 'LINK' ? <>{safeUrl(item.thumbnailUrl) && <img className="reader-image" src={safeUrl(item.thumbnailUrl)} alt=""/>}<p>{item.linkDescription}</p>{safeUrl(item.content) && <a className="source-link" href={safeUrl(item.content)} target="_blank" rel="noreferrer">Open original <Icon name="external" size={16}/></a>}</> : item.type === 'IMAGE' ? <><img className="reader-image" src={safeUrl(item.mediaUrl)} alt={item.title}/><p>{item.content}</p></> : item.type === 'VIDEO' ? <><video controls src={safeUrl(item.mediaUrl)}/><p>{item.content}</p></> : item.type === 'AUDIO' ? <><audio controls src={safeUrl(item.mediaUrl)}/><p>{item.content}</p></> : <>{safeUrl(item.thumbnailUrl) && <img className="reader-image" src={safeUrl(item.thumbnailUrl)} alt=""/>}<p className="badge">{[item.mediaType, item.releaseYear, item.watchStatus].filter(Boolean).join(' · ')}</p><p>{item.content}</p><p>{item.genres?.join(' · ')}</p></>}</article><footer className="reader-footer"><Icon name="check" size={15}/>Saved · {date(item.updatedAt)}</footer></>}</section>; }
