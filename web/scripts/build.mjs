import {build} from 'esbuild';import{mkdir,cp,writeFile}from'node:fs/promises';
const out=new URL('../../app/src/main/assets/web/workspace/',import.meta.url);
await mkdir(out,{recursive:true});await cp(new URL('../public/',import.meta.url),out,{recursive:true});
await build({entryPoints:[new URL('../src/main.tsx',import.meta.url).pathname],bundle:true,external:['/fonts/*','/workspace/fonts/*'],minify:true,format:'esm',jsx:'automatic',outfile:new URL('app.js',out).pathname});
await writeFile(new URL('index.html',out),'<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Cobalt · Your library</title><link rel="stylesheet" href="/workspace/app.css"></head><body><div id="root"></div><script type="module" src="/workspace/app.js"></script></body></html>');
console.log('Built Android workspace assets');
