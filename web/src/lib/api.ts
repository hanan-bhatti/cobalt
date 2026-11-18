export class ApiError extends Error {
    constructor(message: string, public status: number) { super(message); }
}
export async function api<T>(path: string, options: RequestInit = {}, signal?: AbortSignal): Promise<T> {
    const response = await fetch('/api' + path, { credentials: 'same-origin', ...options, signal, headers: { 'Content-Type': 'application/json', ...options.headers } });
    const data = await response.json();
    if (!response.ok) {
        if (response.status === 401)
            window.dispatchEvent(new Event('cobalt:unauthorized'));
        throw new ApiError(data.message || 'Could not complete the request.', response.status);
    }
    return data;
}
export function safeUrl(value?: string) { if (!value)
    return undefined; try {
    const url = new URL(value, location.origin);
    return ['http:', 'https:'].includes(url.protocol) ? url.href : undefined;
}
catch {
    return undefined;
} }
export const date = (value: number) => new Date(value).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
