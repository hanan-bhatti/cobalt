import {EditorState, EditorSelection, Compartment, Transaction} from '@codemirror/state';
import {EditorView, keymap, placeholder, drawSelection, rectangularSelection, highlightActiveLine} from '@codemirror/view';
import {history, historyKeymap, defaultKeymap, undo, redo, undoDepth, redoDepth, indentMore, indentLess, insertNewlineAndIndent} from '@codemirror/commands';
import {markdown, markdownLanguage} from '@codemirror/lang-markdown';
import {syntaxHighlighting, HighlightStyle, indentUnit, bracketMatching} from '@codemirror/language';
import {searchKeymap, search, highlightSelectionMatches} from '@codemirror/search';
import {tags} from '@lezer/highlight';
import {javascriptLanguage} from '@codemirror/lang-javascript';
import {pythonLanguage} from '@codemirror/lang-python';
import {jsonLanguage} from '@codemirror/lang-json';
import {StreamLanguage, LanguageDescription, LanguageSupport} from '@codemirror/language';
import {kotlin} from '@codemirror/legacy-modes/mode/clike';

const tick = String.fromCharCode(96);
const markdownMode = () => markdown({base:markdownLanguage,codeLanguages:[
  LanguageDescription.of({name:'JavaScript',alias:['js','javascript','typescript','ts'],support:new LanguageSupport(javascriptLanguage)}),
  LanguageDescription.of({name:'Python',alias:['py','python'],support:new LanguageSupport(pythonLanguage)}),
  LanguageDescription.of({name:'JSON',alias:['json'],support:new LanguageSupport(jsonLanguage)}),
  LanguageDescription.of({name:'Kotlin',alias:['kt','kotlin'],support:new LanguageSupport(StreamLanguage.define(kotlin))})
]});
const styles = HighlightStyle.define([
  {tag:tags.heading1,class:'cm-heading-1'}, {tag:tags.heading2,class:'cm-heading-2'},
  {tag:tags.heading3,class:'cm-heading-3'}, {tag:tags.strong,class:'cm-strong'},
  {tag:tags.emphasis,class:'cm-emphasis'}, {tag:tags.strikethrough,class:'cm-strike'},
  {tag:tags.link,class:'cm-link'}, {tag:tags.url,class:'cm-url'},
  {tag:tags.processingInstruction,class:'cm-markup'}, {tag:tags.monospace,class:'cm-monospace'},
  {tag:tags.keyword,class:'hljs-keyword'}, {tag:tags.string,class:'hljs-string'},
  {tag:tags.number,class:'hljs-number'}, {tag:tags.comment,class:'hljs-comment'},
  {tag:tags.typeName,class:'hljs-type'}
]);
class CobaltEditor {
  constructor(parent, onChange, onSelection) {
    this.editable = new Compartment();
    this.mode = new Compartment();
    this.view = new EditorView({
      parent,
      state:EditorState.create({extensions:this.extensions=[
        history(),drawSelection(),rectangularSelection(),bracketMatching(),
        indentUnit.of('  '),EditorView.lineWrapping,
        search({top:true}),highlightSelectionMatches(),
        this.editable.of([EditorView.editable.of(true),EditorState.readOnly.of(false)]),
        this.mode.of(markdownMode()),syntaxHighlighting(styles),
        placeholder('Start writing…'),
        EditorView.contentAttributes.of({'aria-label':'Note content in Markdown',spellcheck:'true',autocapitalize:'sentences'}),
        EditorView.scrollMargins.of(() => ({top:74,bottom:24})),
        keymap.of([
          {key:'Mod-b',run:()=>this.format('bold')},{key:'Mod-i',run:()=>this.format('italic')},
          {key:'Mod-Shift-x',run:()=>this.format('strike')},{key:'Mod-Shift-k',run:()=>{onSelection?.('link');return true;}},
          {key:'Mod-]',run:indentMore},{key:'Mod-[',run:indentLess},
          {key:'Enter',run:insertNewlineAndIndent},
          ...historyKeymap,...searchKeymap,...defaultKeymap
        ]),
        EditorView.updateListener.of(update=>{
          if(update.docChanged) onChange(update.state.doc.toString());
          if(update.selectionSet || update.docChanged) onSelection?.();
        })
      ]})
    });
  }
  getText() { return this.view.state.doc.toString(); }
  setText(text, reset = false) {
    text = String(text || '').replace(/\r\n?/g,'\n');
    if (reset) this.load(text);
    else this.view.dispatch({changes:{from:0,to:this.view.state.doc.length,insert:text},selection:{anchor:0},annotations:Transaction.addToHistory.of(false)});
  }
  load(text) {
    // Recreating the state resets history between notes, while preserving all extensions.
    this.view.setState(EditorState.create({doc:String(text || '').replace(/\r\n?/g,'\n'),extensions:this.extensions}));
  }
  setMode(type) {
    this.view.dispatch({effects:this.mode.reconfigure(type === 'CODE' ? StreamLanguage.define(kotlin) : markdownMode()),
      annotations:Transaction.addToHistory.of(false)});
    this.view.contentDOM.setAttribute('spellcheck',type==='TEXT' || type==='MEDIA' ? 'true' : 'false');
  }
  setReadOnly(value) {this.view.dispatch({effects:this.editable.reconfigure([EditorView.editable.of(!value),EditorState.readOnly.of(value)])});}
  focus() {this.view.focus();}
  undo() {return undo(this.view);}
  redo() {return redo(this.view);}
  get canUndo() {return undoDepth(this.view.state)>0;}
  get canRedo() {return redoDepth(this.view.state)>0;}
  get selection() {return this.view.state.selection.main;}
  insert(insert, from=this.selection.from, to=this.selection.to, selectFrom, selectTo) {
    this.view.dispatch({changes:{from,to,insert},selection:EditorSelection.single(selectFrom ?? from+insert.length,selectTo ?? selectFrom ?? from+insert.length),
      userEvent:'input.format',scrollIntoView:true});
    this.focus();return true;
  }
  format(kind) {
    if(this.view.state.readOnly)return false;
    if(kind==='undo')return this.undo();
    if(kind==='redo')return this.redo();
    const {from,to}=this.selection, doc=this.view.state.doc, selected=doc.sliceString(from,to);
    const delimiters={bold:'**',italic:'_',strike:'~~',inlinecode:tick};
    if(delimiters[kind]){
      const mark=delimiters[kind];
      if(selected.startsWith(mark)&&selected.endsWith(mark)&&selected.length>=mark.length*2)
        return this.insert(selected.slice(mark.length,-mark.length),from,to,from,to-mark.length*2);
      if(from>=mark.length&&doc.sliceString(from-mark.length,from)===mark&&doc.sliceString(to,to+mark.length)===mark)
        return this.insert(selected,from-mark.length,to+mark.length,from-mark.length,to-mark.length);
      const value=selected || {bold:'bold text',italic:'italic text',strike:'text',inlinecode:'code'}[kind];
      return this.insert(mark+value+mark,from,to,from+mark.length,from+mark.length+value.length);
    }
    if(kind==='code'){
      const prefix=from&&doc.sliceString(from-1,from)!=='\n'?'\n':'', value=selected||'code';
      return this.insert(prefix+tick.repeat(3)+'\n'+value+'\n'+tick.repeat(3),from,to,from+prefix.length+4,from+prefix.length+4+value.length);
    }
    const first=doc.lineAt(from), last=doc.lineAt(to>from&&doc.sliceString(to-1,to)==='\n'?to-1:to);
    const source=doc.sliceString(first.from,last.to), lines=source.split('\n');
    const prefix={h1:'# ',h2:'## ',h3:'### ',heading:'## ',quote:'> ',checklist:'- [ ] ',bullet:'- ',numbered:'1. '}[kind];
    if(!prefix)return false;
    let result;
    if(/^h[123]$/.test(kind)||kind==='heading') {
      const level=prefix.trim(), all=lines.every(line=>line.startsWith(prefix));
      result=lines.map(line=>all?line.replace(/^#{1,6}\s+/,''):line.replace(/^#{1,6}\s+/,'').replace(/^/,prefix));
    } else {
      const matcher={quote:/^>\s?/,checklist:/^[-*+] \[[ xX]\] /,bullet:/^[-*+] (?!\[[ xX]\] )/,numbered:/^\d+[.)] /}[kind];
      const all=lines.every(line=>matcher.test(line));
      result=lines.map((line,index)=>all?line.replace(matcher,''):(kind==='numbered'?(index+1)+'. ':prefix)+line.replace(/^[-*+] (?:\[[ xX]\] )?|^\d+[.)] |^> ?/,''));
    }
    const text=result.join('\n');
    return this.insert(text,first.from,last.to,first.from,first.from+text.length);
  }
  insertLink(label, url) {
    const {from,to}=this.selection, text=label || this.view.state.doc.sliceString(from,to) || 'link text';
    return this.insert('['+text.replace(/[\[\]]/g,'\\$&')+']('+url+')',from,to);
  }
  table(columns=2, rows=2) {
    const {from,to}=this.selection, doc=this.view.state.doc;
    const head='| '+Array.from({length:columns},(_,i)=>'Column '+(i+1)).join(' | ')+' |';
    const divider='| '+Array(columns).fill('---').join(' | ')+' |';
    const body=Array.from({length:rows},()=> '| '+Array(columns).fill(' ').join(' | ')+' |').join('\n');
    return this.insert((from&&doc.sliceString(from-1,from)!=='\n'?'\n\n':'')+head+'\n'+divider+'\n'+body+'\n',from,to);
  }
  tableContext() {
    const doc=this.view.state.doc, line=doc.lineAt(this.selection.from);
    if(!line.text.trim().startsWith('|'))return null;
    let start=line.number,end=line.number;
    while(start>1&&doc.line(start-1).text.trim().startsWith('|'))start--;
    while(end<doc.lines&&doc.line(end+1).text.trim().startsWith('|'))end++;
    const rows=[];
    for(let i=start;i<=end;i++)rows.push(doc.line(i).text.trim().replace(/^\||\|$/g,'').split(/(?<!\\)\|/).map(c=>c.trim()));
    if(rows.length<2||!rows[1].every(c=>/^:?-{3,}:?$/.test(c)))return null;
    const before=line.text.slice(0,this.selection.from-line.from);
    return {start,end,rows,row:line.number-start,col:Math.max(0,(before.match(/(?<!\\)\|/g)||[]).length-1)};
  }
  editTable(action) {
    const table=this.tableContext();if(!table)return false;
    const {rows,start,end,row,col}=table, columns=rows[0].length;
    if(action==='row')rows.splice(Math.max(2,row+1),0,Array(columns).fill(''));
    if(action==='column')rows.forEach((cells,index)=>cells.splice(col+1,0,index===1?'---':index===0?'Column':''));
    if(action==='remove-row'){if(row<2||rows.length<=3)return false;rows.splice(row,1);}
    if(action==='remove-column'){if(columns<=1)return false;rows.forEach(cells=>cells.splice(col,1));}
    const text=rows.map(cells=>'| '+cells.join(' | ')+' |').join('\n');
    return this.insert(text,this.view.state.doc.line(start).from,this.view.state.doc.line(end).to);
  }
  active(kind) {
    const {from,to}=this.selection, doc=this.view.state.doc, line=doc.lineAt(from).text;
    if(/^h[123]$/.test(kind))return line.startsWith('#'.repeat(Number(kind[1]))+' ');
    const prefixes={quote:/^> /,checklist:/^[-*+] \[[ xX]\] /,bullet:/^[-*+] (?!\[)/,numbered:/^\d+[.)] /};
    if(prefixes[kind])return prefixes[kind].test(line);
    const mark={bold:'**',italic:'_',strike:'~~',inlinecode:tick}[kind];
    return !!mark&&doc.sliceString(Math.max(0,from-mark.length),from)===mark&&doc.sliceString(to,to+mark.length)===mark;
  }
}
window.CobaltEditor=CobaltEditor;
