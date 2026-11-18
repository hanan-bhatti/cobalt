// Prepend `const svg = <map of role to SVG text from assets.json>;` when executing.
const page = await figma.getNodeByIdAsync('369:76');
await figma.setCurrentPageAsync(page);
if (page.children.some(n => n.name === 'Notes & folders · Bottom-sheet states')) {
  throw new Error('Sheet board already exists; inspect it before another write.');
}
const created = [];
const removed = [];
const keep = n => { created.push(n.id); return n; };
const deep = n => { created.push(n.id, ...n.findAll(() => true).map(c => c.id)); return n; };
const discard = n => { removed.push(n.id, ...('findAll' in n ? n.findAll(() => true).map(c => c.id) : [])); n.remove(); };
const mainBoard = await figma.getNodeByIdAsync('466:20299');
const dark = await figma.getNodeByIdAsync('466:20305');
const light = await figma.getNodeByIdAsync('466:20420');
const handleTemplate = await figma.getNodeByIdAsync('447:10046');
const titleTemplate = await figma.getNodeByIdAsync('447:9712');
const gestureTemplate = await figma.getNodeByIdAsync('447:10048');
const heatmap = await figma.getNodeByIdAsync('403:2393');
const categories = await figma.getNodeByIdAsync('403:2401');
const roles = {
  bg: 'VariableID:146:940', surface: 'VariableID:146:969', primary: 'VariableID:146:934',
  primaryContainer: 'VariableID:146:935', onPrimaryContainer: 'VariableID:146:929',
  ink: 'VariableID:146:953', muted: 'VariableID:146:954'
};
const vars = {};
await Promise.all(Object.entries(roles).map(async ([name, id]) => { vars[name] = await figma.variables.getVariableByIdAsync(id); }));
const fonts = [dark, light, titleTemplate].flatMap(n => n.findAllWithCriteria({ types: ['TEXT'] })
  .flatMap(t => t.getStyledTextSegments(['fontName']).map(s => s.fontName)));
await Promise.all(fonts.map(f => figma.loadFontAsync(f)));
await Promise.all([{ family: 'Roboto', style: 'Regular' }, { family: 'Roboto', style: 'Medium' }].map(f => figma.loadFontAsync(f)));
function paint(role, opacity = 1) {
  return figma.variables.setBoundVariableForPaint({ type: 'SOLID', color: { r: .5, g: .5, b: .5 }, opacity }, 'color', vars[role]);
}
function column(name, width, gap = 0, padding = 0) {
  const n = keep(figma.createAutoLayout('VERTICAL'));
  n.name = name; n.resize(width, 10); n.layoutSizingHorizontal = 'FIXED'; n.layoutSizingVertical = 'HUG';
  n.itemSpacing = gap; n.paddingTop = padding; n.paddingBottom = padding; n.paddingLeft = padding; n.paddingRight = padding;
  n.fills = []; n.clipsContent = false; return n;
}
function row(name, width, gap = 12) {
  const n = keep(figma.createAutoLayout('HORIZONTAL'));
  n.name = name; n.resize(width, 10); n.layoutSizingHorizontal = 'FIXED'; n.layoutSizingVertical = 'HUG';
  n.itemSpacing = gap; n.counterAxisAlignItems = 'CENTER'; n.fills = []; return n;
}
function text(parent, value, width, size = 13, role = 'ink', weight = 'Regular') {
  const n = keep(figma.createText()); n.name = value; n.fontName = { family: 'Roboto', style: weight };
  n.fontSize = size; n.lineHeight = { unit: 'PIXELS', value: size === 16 ? 22 : size === 28 ? 34 : 19 };
  n.characters = value; n.textAutoResize = 'HEIGHT'; n.resize(width, 10); n.fills = [paint(role)];
  parent.appendChild(n); n.layoutSizingVertical = 'HUG'; return n;
}
function icon(parent, kind, role = 'primary', size = 24) {
  const n = deep(kind === 'heatmap' || kind === 'categories'
    ? (kind === 'heatmap' ? heatmap : categories).createInstance()
    : figma.createNodeFromSvg(svg[kind]));
  n.name = 'Cobalt icon · ' + kind; n.resize(size, size); parent.appendChild(n);
  for (const child of n.findAll(c => c.type === 'VECTOR' || c.type === 'BOOLEAN_OPERATION')) {
    if ('fills' in child && child.fills.length) child.fills = [paint(role)];
    if ('strokes' in child && child.strokes.length) child.strokes = [paint(role)];
  }
  return n;
}
function explanation(parent, kind, title, description) {
  const r = row(title, 408, 12); r.counterAxisAlignItems = 'MIN'; parent.appendChild(r);
  const badge = column('Explanation icon', 40, 0, 8); badge.resize(40, 40); badge.layoutSizingVertical = 'FIXED';
  badge.fills = [paint('primaryContainer')]; badge.cornerRadius = 12; r.appendChild(badge); icon(badge, kind, 'onPrimaryContainer');
  const copy = column('Explanation copy', 356, 4); r.appendChild(copy); copy.layoutSizingHorizontal = 'FILL';
  text(copy, title, 356, 16, 'ink', 'Medium'); text(copy, description, 356, 13, 'muted');
}
function choice(parent, title, description, kind, selected) {
  const r = row(title, 408, 12); r.paddingTop = 12; r.paddingBottom = 12; r.paddingLeft = 16; r.paddingRight = 16;
  r.fills = [paint(selected ? 'primaryContainer' : 'surface')]; r.cornerRadius = 20; parent.appendChild(r);
  const fg = selected ? 'onPrimaryContainer' : 'ink'; icon(r, kind, fg);
  const width = selected ? 304 : 340; const copy = column('Choice copy', width, 2); r.appendChild(copy); copy.layoutSizingHorizontal = 'FILL';
  text(copy, title, width, 16, fg, 'Medium'); text(copy, description, width, 13, selected ? fg : 'muted');
  if (selected) icon(r, 'check', fg); return r;
}
function sheetPhone(parent, source, mode, kind, selected = 'heatmap') {
  const phone = deep(source.clone()); phone.name = 'Notes & folders · ' + kind + ' sheet · ' + mode; parent.appendChild(phone);
  const scrim = keep(figma.createRectangle()); scrim.name = 'Modal scrim'; scrim.resize(448, 997);
  scrim.fills = [{ type: 'SOLID', color: { r: 0, g: 0, b: 0 }, opacity: .5 }]; phone.appendChild(scrim);
  scrim.layoutPositioning = 'ABSOLUTE'; scrim.x = 0; scrim.y = 0;
  const sheet = column('Native Settings bottom sheet', 448); sheet.fills = [paint('bg')];
  sheet.topLeftRadius = 28; sheet.topRightRadius = 28; sheet.bottomLeftRadius = 0; sheet.bottomRightRadius = 0; sheet.clipsContent = true;
  phone.appendChild(sheet); sheet.layoutPositioning = 'ABSOLUTE';
  const handle = deep(handleTemplate.clone()); sheet.appendChild(handle); handle.layoutSizingHorizontal = 'FILL';
  const content = column('Sheet content', 448, 8, 20); content.paddingBottom = 0; sheet.appendChild(content);
  const titleRow = deep(titleTemplate.clone()); content.appendChild(titleRow);
  const title = titleRow.findOne(n => n.type === 'TEXT' && n.name === 'Sheet title');
  title.characters = kind === 'Suggestions' ? 'How suggestions work' : 'Archive view'; title.fontSize = 24;
  title.lineHeight = { unit: 'PIXELS', value: 30 }; title.textAutoResize = 'HEIGHT'; title.resize(360, 10);
  title.layoutSizingHorizontal = 'FILL'; title.layoutSizingVertical = 'HUG';
  const close = titleRow.findOne(n => n.type === 'FRAME' && n.name === 'Close sheet');
  for (const n of [...close.children]) discard(n);
  const closeIcon = icon(close, 'close', 'ink'); closeIcon.x = 12; closeIcon.y = 12;
  const description = kind === 'Suggestions'
    ? 'Suggestions help order the folders you see when saving or moving an item.'
    : 'Choose how saved items are summarized in Settings.';
  text(content, description, 408, 13, 'muted');
  const items = column(kind === 'Suggestions' ? 'Folder ranking explanation' : 'Archive choices', 408, kind === 'Suggestions' ? 20 : 8);
  items.paddingTop = 8; items.paddingBottom = 24; content.appendChild(items);
  const optionRows = [];
  if (kind === 'Suggestions') {
    explanation(items, 'folder', 'More saved items', 'Folders with more saved items tend to appear higher.');
    explanation(items, 'history', 'Recent captures', 'Folders with recently captured items can appear higher.');
    explanation(items, 'text', 'Similar content', 'Folder names are compared with the item’s title and text. Item type also influences the order.');
    explanation(items, 'pin', 'Pinned folders', 'Pinned folders stay first. With suggestions off, the remaining folders are sorted alphabetically.');
  } else {
    optionRows.push(choice(items, 'Activity heatmap', 'Saved items by date.', 'heatmap', selected === 'heatmap').id);
    optionRows.push(choice(items, 'Category totals', 'Saved items by type.', 'categories', selected === 'pills').id);
  }
  const gesture = deep(gestureTemplate.clone()); sheet.appendChild(gesture);
  sheet.x = 0; sheet.y = phone.height - sheet.height;
  return { id: phone.id, kind, mode, selected, sheetId: sheet.id, closeId: close.id, optionRows, sheetHeight: sheet.height };
}
const board = column('Notes & folders · Bottom-sheet states', 1504, 16, 24); board.fills = [paint('bg')];
board.x = mainBoard.x; board.y = mainBoard.y + mainBoard.height + 80;
board.setExplicitVariableModeForCollection('VariableCollectionId:146:927', '146:1');
text(board, 'Notes & folders · Bottom sheets', 1400, 28, 'ink', 'Medium');
text(board, 'The explanation and archive choices use the existing native Settings sheet.', 1400, 14, 'muted');
const views = row('Review views', 1456, 32); board.appendChild(views);
const screens = [];
for (const [source, mode, kind, selected, caption] of [
  [dark, 'Dark', 'Suggestions', 'heatmap', 'How suggestions work'],
  [dark, 'Dark', 'Archive', 'heatmap', 'Archive view · Dark'],
  [light, 'Light', 'Archive', 'pills', 'Archive view · Light']
]) {
  const view = column('Review · ' + caption, 448, 12); views.appendChild(view); text(view, caption, 448, 14, 'muted', 'Medium');
  screens.push(sheetPhone(view, source, mode, kind, selected));
}
return { boardId: board.id, pageId: page.id, screens, createdNodeIds: [...new Set(created)], removedNodeIds: removed,
  bounds: { x: board.x, y: board.y, width: board.width, height: board.height } };
