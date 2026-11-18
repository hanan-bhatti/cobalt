export type ItemType = 'TEXT' | 'CODE' | 'LINK' | 'IMAGE' | 'VIDEO' | 'AUDIO' | 'MEDIA';
export interface Item {
    id: string;
    type: ItemType;
    title: string;
    preview?: string;
    content?: string;
    folders: string[];
    timestamp: number;
    updatedAt: number;
    thumbnailUrl?: string;
    mediaUrl?: string;
    linkDescription?: string;
    linkTitle?: string;
    watchStatus?: string;
    releaseYear?: string;
    rating?: number;
    runtime?: number;
    genres?: string[];
    mediaType?: string;
    editable?: boolean;
    isLockedByMobile?: boolean;
}
export interface Folder {
    name: string;
    color?: string;
    icon?: string;
    count: number;
}
export interface Listing {
    items: Item[];
    totalItems: number;
    totalPages: number;
    page: number;
}
