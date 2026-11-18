import type { Item, Listing } from '../lib/types';
import { filters } from './Sidebar';
import { Icon } from './Icon';
import { ItemCard } from './ItemCard';
export function Library({ data, type, folder, query, sort, setSort, select, selected, open, page, setPage, busy, error, retry }: {
    data: Listing;
    type: string;
    folder: string;
    query: string;
    sort: string;
    setSort: (s: string) => void;
    select: (t: string) => void;
    selected?: string;
    open: (id: string) => void;
    page: number;
    setPage: (p: number) => void;
    busy: boolean;
    error: string;
    retry: () => void;
}) { const grouped = !type && !folder && !query; const recent = grouped ? data.items.filter(i => i.type !== 'MEDIA') : data.items; const media = grouped ? data.items.filter(i => i.type === 'MEDIA') : []; return <section className="library" aria-label="Your library"><header className="library-heading"><div><h1>{folder || (!type ? 'Your library' : '') || filters.find(f => f[0] === type)?.[1] || 'Your library'}</h1><p>{data.totalItems} items{query && ` matching “${query}”`}</p></div><label>Sort: <select aria-label="Sort items" value={sort} onChange={e => setSort(e.target.value)}><option value="updated">Recently edited</option><option value="newest">Newest first</option><option value="title">Title</option></select></label></header><div className="filters" aria-label="Filter content">{filters.map(([t, label, icon]) => <button key={t} className={type === t ? 'active' : ''} onClick={() => select(t)}><Icon name={icon} size={17}/>{t ? label : 'All'}</button>)}</div>{error ? <div className="empty"><h2>Let’s reconnect.</h2><p role="alert">{error}</p><button onClick={retry}>Try again</button></div> : busy ? <div className="skeleton-grid" aria-label="Loading library" aria-busy="true">{[1, 2, 3, 4].map(i => <div key={i}/>)}</div> : <>{grouped && recent.length > 0 && <h2 className="section-heading"><Icon name="bookmark"/>Recently saved</h2>}<div className="item-grid">{recent.map(item => <ItemCard key={item.id} item={item} selected={selected === item.id} onOpen={open}/>)}</div>{media.length > 0 && <><h2 className="section-heading"><Icon name="movie"/>Movies &amp; Anime</h2><div className="media-grid">{media.map(item => <ItemCard key={item.id} item={item} selected={selected === item.id} onOpen={open} poster/>)}</div></>}{!data.items.length && <div className="empty"><Icon name="search" size={32}/><h2>{query ? 'Nothing found.' : 'A little room to begin.'}</h2><p>{query ? 'Try another word or content type.' : 'Your saved captures will appear here.'}</p></div>}</>}{data.totalPages > 1 && <footer className="pagination"><button disabled={page <= 1 || busy} onClick={() => setPage(page - 1)}>Previous</button><span>{page} / {data.totalPages}</span><button disabled={page >= data.totalPages || busy} onClick={() => setPage(page + 1)}>Next</button></footer>}</section>; }
