#!/usr/bin/env python3
"""Disposable Jikan v4 fixtures for emulator tests; no credentials, writes or external requests.

Run: python tools/media-fixture-server.py --port 8701
Then: adb -s emulator-5554 reverse tcp:8701 tcp:8701
Configure Cobalt's Jikan base URL as http://127.0.0.1:8701/v4/.
Restore the user's provider settings when testing finishes.
"""
import argparse
import json
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, parse_qs

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlparse(self.path)
        query = parse_qs(parsed.query).get('q', [''])[0]
        if query == 'CobaltFault429':
            self.send_response(429); self.send_header('Retry-After', '2'); self.end_headers(); return
        if query == 'CobaltFaultJson':
            self.send_response(200); self.end_headers(); self.wfile.write(b'{invalid'); return
        if parsed.path == '/v4/anime':
            data = [{'mal_id': 98765001, 'title': 'Cobalt Fixture Anime', 'title_english': 'Cobalt Fixture Anime',
                     'synopsis': 'Synthetic local Jikan anime fixture.', 'episodes': 12, 'status': 'Finished Airing',
                     'year': 2026, 'score': 8.2, 'genres': [{'name': 'Adventure'}],
                     'url': 'https://myanimelist.net/anime/98765001'}]
        elif parsed.path == '/v4/manga':
            data = [{'mal_id': 98765002, 'title': 'Cobalt Fixture Manga', 'title_english': 'Cobalt Fixture Manga',
                     'synopsis': 'Synthetic local Jikan manga fixture.', 'chapters': 42, 'volumes': 8,
                     'status': 'Publishing', 'score': 8.5, 'genres': [{'name': 'Adventure'}],
                     'url': 'https://myanimelist.net/manga/98765002'}]
        elif parsed.path.endswith('/streaming'):
            data = []
        else:
            self.send_response(404); self.end_headers(); return
        body = json.dumps({'data': data, 'pagination': {'has_next_page': False}}).encode()
        self.send_response(200); self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body))); self.end_headers()
        try: self.wfile.write(body)
        except BrokenPipeError: pass
    def log_message(self, *_):
        pass  # Never log queries, headers or tokens.

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', type=int, default=8701)
    args = parser.parse_args()
    HTTPServer(('127.0.0.1', args.port), Handler).serve_forever()
