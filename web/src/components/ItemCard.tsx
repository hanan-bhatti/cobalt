import type { Item } from '../lib/types';
import { date, safeUrl } from '../lib/api';
import { Icon } from './Icon';
export const info: Record<string, [
    string,
    string
]> = { TEXT: ['Note', 'text'], CODE: ['Code', 'code'], LINK: ['Link', 'link'], IMAGE: ['Image', 'image'], VIDEO: ['Video', 'video'], AUDIO: ['Audio', 'voice'], MEDIA: ['Media', 'movie'] };
export function ItemCard({ item, selected, onOpen, poster = false }: {
    item: Item;
    selected: boolean;
    onOpen: (id: string) => void;
    poster?: boolean;
}) { const [label, icon] = info[item.type]; const image = safeUrl(item.thumbnailUrl); return <button className={`item-card ${selected ? 'active' : ''} ${poster ? 'poster' : ''}`} onClick={() => onOpen(item.id)} aria-pressed={selected}>{poster && image && <img className="poster-image" src={image} alt="" loading="lazy"/>}{!poster && <div className="card-top"><span className="badge"><Icon name={icon} size={15}/>{label}</span><time>{date(item.updatedAt)}</time></div>}<h3>{item.title || 'Untitled note'}</h3>{poster ? <div className="poster-meta"><span>{item.watchStatus || 'Saved'}</span><span>{item.releaseYear}</span></div> : <><div className="card-content">{item.type === 'CODE' ? <pre>{item.preview || item.content}</pre> : <p>{(item.preview || item.linkDescription || '').replace(/^[#>*\s-]+/gm, '').replace(/[*_`]/g, '')}</p>}{image && <img className="thumbnail" src={image} alt="" loading="lazy"/>}</div><div className="card-folder"><Icon name="folder" size={15}/>{item.folders[0] || 'Unfiled'}</div></>}</button>; }
