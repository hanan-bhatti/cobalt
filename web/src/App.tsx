import { useEffect, useState } from 'react';
import { api } from './lib/api';
import type { Folder, Listing } from './lib/types';
import { Sidebar } from './components/Sidebar';
import { Library } from './components/Library';
import { Reader } from './components/Reader';
import { Icon } from './components/Icon';
interface Tab {
    id: string;
    title: string;
}
const initialRoute = () => { try {
    return decodeURIComponent(location.hash.replace(/^#\/?/, ''));
}
catch {
    return '';
} };
export default function App() {
    const [authenticated, setAuthenticated] = useState(false), [checking, setChecking] = useState(true), [pin, setPin] = useState(''), [authError, setAuthError] = useState(''), [loginBusy, setLoginBusy] = useState(false);
    const [type, setType] = useState(''), [folder, setFolder] = useState(''), [query, setQuery] = useState(''), [sort, setSort] = useState('updated'), [page, setPage] = useState(1);
    const [data, setData] = useState<Listing>({ items: [], totalItems: 0, totalPages: 1, page: 1 }), [folders, setFolders] = useState<Folder[]>([]), [busy, setBusy] = useState(false), [error, setError] = useState(''), [revision, setRevision] = useState(0);
    const [selectedTitle, setSelectedTitle] = useState('');
    const [split, setSplit] = useState<string>(), [tabs, setTabs] = useState<Tab[]>(() => initialRoute() ? [{ id: initialRoute(), title: 'Capture' }] : []), [active, setActive] = useState(initialRoute), [menu, setMenu] = useState(false);
    useEffect(() => { const unauthorized = () => { setAuthenticated(false); setData({ items: [], totalItems: 0, totalPages: 1, page: 1 }); setSplit(undefined); setTabs([]); setActive(''); setFolders([]); }; window.addEventListener('cobalt:unauthorized', unauthorized); api<{
        authenticated: boolean;
    }>('/auth/status').then(r => setAuthenticated(r.authenticated)).catch(() => setAuthError('Could not reach Cobalt. Enable Web Companion on your phone.')).finally(() => setChecking(false)); return () => window.removeEventListener('cobalt:unauthorized', unauthorized); }, []);
    useEffect(() => { const change = () => setActive(initialRoute()); window.addEventListener('hashchange', change); return () => window.removeEventListener('hashchange', change); }, []);
    useEffect(() => { if (!authenticated)
        return; const controller = new AbortController(); api<{
        folders: Folder[];
    }>('/folders', {}, controller.signal).then(r => setFolders(r.folders)).catch(e => { if (e.name !== 'AbortError')
        setError(e.message); }); return () => controller.abort(); }, [authenticated, revision]);
    useEffect(() => { if (!authenticated)
        return; const controller = new AbortController(); setBusy(true); setError(''); const timer = setTimeout(() => { const params = new URLSearchParams({ page: String(page), limit: '25', sort }); if (type)
        params.set('type', type); if (folder)
        params.set('folder', folder); if (query)
        params.set('q', query); api<Listing>('/notes?' + params, {}, controller.signal).then(setData).catch(e => { if (e.name !== 'AbortError')
        setError(e.message); }).finally(() => { if (!controller.signal.aborted)
        setBusy(false); }); }, 180); return () => { clearTimeout(timer); controller.abort(); }; }, [authenticated, type, folder, query, sort, page, revision]);
    const navigate = (id: string) => { location.hash = id ? '/' + encodeURIComponent(id) : '/'; setActive(id); };
    const select = (t: string, f = '') => { setType(t); setFolder(f); setPage(1); navigate(''); };
    const open = (id: string) => { setSelectedTitle(''); setSplit(id); navigate(''); };
    const promote = () => { if (!split)
        return; const title = selectedTitle || data.items.find(i => i.id === split)?.title || 'Capture'; setTabs(previous => previous.some(t => t.id === split) ? previous : [...previous, { id: split, title }]); navigate(split); setSplit(undefined); };
    const closeTab = (id: string) => { setTabs(previous => previous.filter(t => t.id !== id)); if (active === id)
        navigate(''); };
    async function login(event: React.FormEvent) { event.preventDefault(); setLoginBusy(true); setAuthError(''); try {
        await api('/auth/login', { method: 'POST', body: JSON.stringify({ pin }) });
        setPin('');
        setAuthenticated(true);
    }
    catch (e) {
        setAuthError(e instanceof Error ? e.message : 'Unable to sign in.');
    }
    finally {
        setLoginBusy(false);
    } }
    async function logout() { try {
        await api('/auth/logout', { method: 'POST', body: '{}' });
        window.dispatchEvent(new Event('cobalt:unauthorized'));
        setMenu(false);
    }
    catch (e) {
        setError(e instanceof Error ? e.message : 'Could not disconnect.');
    } }
    useEffect(() => { const key = (e: KeyboardEvent) => { if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        document.querySelector<HTMLInputElement>('.search input')?.focus();
    } if (e.key === 'Escape') {
        setMenu(false);
        setSplit(undefined);
    } }; window.addEventListener('keydown', key); return () => window.removeEventListener('keydown', key); }, []);
    const selected = active || split;
    return <div className="app"><Sidebar type={type} folder={folder} folders={folders} select={select}/><main className="main"><header className="topbar"><label className="search"><Icon name="search"/><input aria-label="Search your library" placeholder="Search your library…" type="search" value={query} onChange={e => { setQuery(e.target.value); setPage(1); navigate(''); }}/><kbd>Ctrl K</kbd></label><div className="device"><button aria-expanded={menu} onClick={() => setMenu(!menu)}><i className={authenticated ? 'online' : ''}/>{authenticated ? 'Device' : 'Disconnected'}<Icon name="down" size={15}/></button>{menu && <div className="device-menu"><p>{authenticated ? 'Connected to your Cobalt archive' : 'Connect using the PIN on your phone'}</p><button onClick={() => { setRevision(n => n + 1); setMenu(false); }}><Icon name="refresh"/>Refresh library</button>{authenticated && <button onClick={logout}>Disconnect</button>}<a href="/">Open current companion</a></div>}</div></header><nav className="tabs" aria-label="Workspace tabs"><button className={!active ? 'active' : ''} onClick={() => navigate('')}><Icon name="grid" size={17}/>Library</button>{tabs.map(tab => <div key={tab.id} className={'workspace-tab ' + (active === tab.id ? 'active' : '')}><button onClick={() => navigate(tab.id)}><Icon name="text" size={16}/><span className="truncate">{tab.title}</span></button><button aria-label={'Close ' + tab.title} onClick={() => closeTab(tab.id)}><Icon name="close" size={15}/></button></div>)}</nav><div className={'content ' + (selected ? 'split' : '') + (active ? ' dedicated' : '')}>{!active && <Library data={data} type={type} folder={folder} query={query} sort={sort} setSort={s => { setSort(s); setPage(1); }} select={t => select(t, folder)} selected={split} open={open} page={page} setPage={setPage} busy={busy} error={error} retry={() => setRevision(n => n + 1)}/>}{authenticated && selected && <Reader onTitle={setSelectedTitle} key={selected} id={selected} dedicated={!!active} promote={promote} close={() => active ? navigate('') : setSplit(undefined)}/>}</div></main>{!authenticated && <div className="auth-overlay"><form className="auth" onSubmit={login}><Icon name="logo" size={36}/><h1>Your archive, within reach.</h1><p>Enter the PIN shown in Cobalt’s Web Companion settings.</p><label htmlFor="pin">Connection PIN</label><input id="pin" autoFocus inputMode="numeric" autoComplete="off" value={pin} onChange={e => setPin(e.target.value)} required/><p className="error" role="alert">{authError}</p><button className="primary" disabled={loginBusy || checking}>{checking ? 'Checking connection…' : loginBusy ? 'Connecting…' : 'Open your library'}</button><small>Your captures stay on your phone.</small></form></div>}</div>;
}
