import {build} from 'esbuild';
import {readFileSync,writeFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
const assets = fileURLToPath(new URL('../../app/src/main/assets/web/',import.meta.url));
await build({entryPoints:[fileURLToPath(new URL('editor-entry.js',import.meta.url))],bundle:true,minify:true,target:'es2022',outfile:assets+'editor.js',banner:{js:'/* CodeMirror 6 and Lezer · MIT · licenses in VENDOR-LICENSES.txt */'}});
await build({stdin:{contents:'import {marked} from "marked"; window.marked=marked;',resolveDir:fileURLToPath(new URL('.',import.meta.url))},bundle:true,minify:true,outfile:assets+'markdown.js',banner:{js:'/* Marked 15.0.12 · MIT · license in VENDOR-LICENSES.txt */'}});
const languages = ['javascript','typescript','kotlin','java','python','bash','json','css','xml','sql','cpp','markdown'];
const imports = languages.map((name,index) => 'import l'+index+' from "highlight.js/lib/languages/'+name+'";').join('\n');
const register = languages.map((name,index) => 'hljs.registerLanguage("'+name+'",l'+index+');').join('\n');
await build({stdin:{contents:'import hljs from "highlight.js/lib/core";\n'+imports+'\n'+register+'\nwindow.cobaltHighlight=hljs;',resolveDir:fileURLToPath(new URL('.',import.meta.url))},bundle:true,minify:true,outfile:assets+'highlight.js',banner:{js:'/* Highlight.js 11.11.1 · BSD-3-Clause · license in VENDOR-LICENSES.txt */'}});
const packages=['marked','highlight.js','@codemirror/state','@codemirror/view','@codemirror/commands','@codemirror/language','@codemirror/lang-markdown','@codemirror/search','@codemirror/lang-javascript','@codemirror/lang-python','@codemirror/lang-json','@codemirror/legacy-modes','@lezer/highlight','@lezer/common','@lezer/lr','@lezer/markdown','@lezer/javascript','@lezer/python','@lezer/json','style-mod','w3c-keyname','crelt','@marijn/find-cluster-break'];
const licenses = packages.map(name => name+'\n\n'+readFileSync(new URL('node_modules/'+name+'/' + (name === 'marked' ? 'LICENSE.md' : 'LICENSE'),import.meta.url),'utf8')).join('\n\n');
writeFileSync(assets+'VENDOR-LICENSES.txt',licenses);
// Preserve the phone's exact filled outline paths, including even-odd cutouts.
execFileSync('python3',[fileURLToPath(new URL('icons.py',import.meta.url))],{stdio:'inherit'});
