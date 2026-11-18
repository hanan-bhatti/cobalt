import { Icon } from './Icon';
import type { Folder } from '../lib/types';
export const filters = [['', 'Library', 'grid'], ['TEXT', 'Notes', 'text'], ['CODE', 'Code', 'code'], ['LINK', 'Links', 'link'], ['ATTACHMENT', 'Files', 'folder'], ['MEDIA', 'Media', 'movie']];
export function Sidebar({ type, folder, folders, select }: {
    type: string;
    folder: string;
    folders: Folder[];
    select: (type: string, folder?: string) => void;
}) { return <aside className="sidebar"><a className="brand" href="#" onClick={e => { e.preventDefault(); select(''); }}><Icon name="logo" size={28}/>Cobalt</a><a className="capture primary" href="/" title="Open capture in the current companion"><Icon name="plus"/>New capture</a><nav aria-label="Library">{filters.map(([key, label, icon]) => <button key={key} className={type === key && !folder ? 'selected' : ''} onClick={() => select(key)}><Icon name={icon}/>{label}</button>)}</nav><div className="collection-label">Collections<a href="/" aria-label="Manage collections in the current companion"><Icon name="plus" size={18}/></a></div><nav aria-label="Collections">{folders.map(f => <button key={f.name} className={folder === f.name ? 'selected' : ''} onClick={() => select('', f.name)}><span style={{ color: f.color }}><Icon name="folder"/></span><span className="truncate">{f.name}</span></button>)}</nav></aside>; }
