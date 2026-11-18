# Cobalt companion workspace

React + TypeScript client, bundled with esbuild into Android assets. Node is needed only for development/builds. Android serves the compiled app at `/workspace/`; the original companion remains at `/` during migration.

## Develop

```
cd web
npm ci
npm run dev
```

Open http://127.0.0.1:4173/workspace/ and use demo PIN `1234`. The default development server uses synthetic in-memory captures, plus approved Figma imagery. It does not access the phone database. Restart the dev command after source changes.

For an actual device server, set `COBALT_API_ORIGIN=http://127.0.0.1:18080` before `npm run dev` (ADB port forwarding required). Browser sessions still use the existing server PIN. Test real device integration separately; demo playback is not a real-media verification.

## Build

```
npm run build
```

Type checks and emits `app/src/main/assets/web/workspace/`. Build before packaging Android. Committed generated assets let existing Gradle builds package the frontend without installing Node. Demo imagery stays in `web/fixtures`, outside shipped Android assets.

## Modules

- `src/App.tsx`: workspace orchestration, sessions, tabs and hash navigation.
- `src/components`: Sidebar, Library, ItemCard, Reader and shared Icon.
- `src/lib`: API transport, URL validation and content types.
- `src/styles`: responsive app styles and bundled Android font use.
- `scripts`: local proxy/fixture preview and production bundle.

First-page scope: PIN login/logout, browsing, search, type/folder filters, sort, pagination, split reading, document tabs and browser navigation. Capture and folder management currently link to the existing companion. Editing, advanced Markdown fidelity and expanded media screens are follow-up work; no new write path is introduced here.
