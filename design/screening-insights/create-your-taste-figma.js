// Editable Figma concept. Run with figma-use and figma-generate-design.
// The profile is illustrative; arithmetic follows the current Cobalt genre formula.
const page = await figma.getNodeByIdAsync('77:915');
await figma.setCurrentPageAsync(page);
const boardName = 'Cobalt / Screening / Your taste · Transparency concept';
if (page.children.some(n => n.name === boardName)) throw new Error('Your taste board already exists; inspect it before another construction pass.');

const sourceIds = {
  status: '142:2085', top: '142:2089', header: '142:2095',
  setup: '142:2003', review: '142:2084',
  nav: '92:2215', button: '92:2222', section: '142:1969', info: '145:2599',
  movie: '26:1547', chevron: '26:2680', close: '26:2707', history: '26:3471',
  useful: '47:1255', rejected: '47:1319', check: '25:951'
};
const styleIds = {
  board: 'S:84dfb7bef8d7ed8ddca9c1884f54783c13d42643,',
  title: 'S:05f1425634395ea4550c67291fb6ad0b0fdeb426,',
  body: 'S:cbe3138e29f088dd852eee16f3fbae0e4a2f6c4c,',
  row: 'S:70af0bf50cdb31680884e19363086e2d39424895,',
  section: 'S:214a9a9ada5dbb8e84360bd38a243cc68e1e198a,',
  sheet: 'S:10be63aa6decf3077dc21749353477ab6f61e33d,',
  meta: 'S:ef84359f1e29f42596a6e7ada0df55d87b04d849,'
};
const variableIds = {
  background: 'VariableID:92:2183', surface: 'VariableID:92:2184',
  text: 'VariableID:92:2185', muted: 'VariableID:92:2186',
  primary: 'VariableID:92:2187', onPrimary: 'VariableID:92:2188',
  selected: 'VariableID:92:2189', outline: 'VariableID:92:2190',
  boardInk: 'VariableID:92:2192', paper: 'VariableID:77:918',
  s6: 'VariableID:77:951', s8: 'VariableID:77:952', s12: 'VariableID:77:953',
  s16: 'VariableID:77:954', s18: 'VariableID:77:955', s20: 'VariableID:77:956',
  r12: 'VariableID:77:957', r24: 'VariableID:77:958'
};
const [sourcePairs, stylePairs, variablePairs] = await Promise.all([
  Promise.all(Object.entries(sourceIds).map(async ([k, id]) => [k, await figma.getNodeByIdAsync(id)])),
  Promise.all(Object.entries(styleIds).map(async ([k, id]) => [k, await figma.getStyleByIdAsync(id)])),
  Promise.all(Object.entries(variableIds).map(async ([k, id]) => [k, await figma.variables.getVariableByIdAsync(id)]))
]);
const sources = Object.fromEntries(sourcePairs), styles = Object.fromEntries(stylePairs), tokens = Object.fromEntries(variablePairs);
if ([...sourcePairs, ...stylePairs, ...variablePairs].some(([, n]) => !n)) throw new Error('A selected Cobalt asset is unavailable.');
const fontNames = new Map();
for (const style of Object.values(styles)) if (style.type === 'TEXT') fontNames.set(JSON.stringify(style.fontName), style.fontName);
for (const source of Object.values(sources)) for (const text of source.findAllWithCriteria({ types: ['TEXT'] }))
  for (const segment of text.getStyledTextSegments(['fontName'])) fontNames.set(JSON.stringify(segment.fontName), segment.fontName);
await Promise.all([...fontNames.values()].map(font => figma.loadFontAsync(font)));

const styleWrites = [], reactions = [], phones = [], customComponents = [], chartChecks = [];
const inheritedWrites = new Set();
const roots = [];
const startX = Math.max(...page.children.map(n => n.x + n.width)) + 160;
function paint(role, opacity = 1) {
  return figma.variables.setBoundVariableForPaint({ type: 'SOLID', color: { r: 0, g: 0, b: 0 }, opacity }, 'color', tokens[role]);
}
function gap(frame, value) {
  frame.itemSpacing = value;
  const token = tokens['s' + value]; if (token) frame.setBoundVariable('itemSpacing', token);
}
function pad(frame, value) {
  frame.paddingLeft = frame.paddingRight = frame.paddingTop = frame.paddingBottom = value;
  const token = tokens['s' + value]; if (token) for (const side of ['paddingLeft', 'paddingRight', 'paddingTop', 'paddingBottom']) frame.setBoundVariable(side, token);
}
function radius(frame, value = 24) {
  frame.cornerRadius = value;
  if (tokens['r' + value]) frame.setBoundVariable('cornerRadius', tokens['r' + value]);
}
function auto(parent, name, width, direction = 'VERTICAL', spacing = 0, fill = null) {
  const frame = figma.createAutoLayout(direction); frame.name = name;
  if (parent) parent.appendChild(frame);
  frame.resize(width, 1); frame.layoutSizingHorizontal = 'FIXED'; frame.layoutSizingVertical = 'HUG';
  frame.primaryAxisAlignItems = 'MIN'; frame.counterAxisAlignItems = 'MIN';
  gap(frame, spacing); frame.fills = fill ? [paint(fill)] : [];
  return frame;
}
function text(parent, value, role = 'body', width = 384, color = 'text', align = 'LEFT', name = value) {
  const node = figma.createText(); parent.appendChild(node); node.name = name;
  const style = styles[role]; node.fontName = style.fontName; node.fontSize = style.fontSize;
  node.lineHeight = style.lineHeight; node.letterSpacing = style.letterSpacing;
  node.resize(width, 1); node.textAutoResize = 'HEIGHT';
  node.layoutSizingHorizontal = 'FIXED'; node.textAlignHorizontal = align;
  node.characters = value; node.fills = [paint(color)];
  styleWrites.push(node.setTextStyleIdAsync(style.id).then(() => {
    const horizontal = node.layoutSizingHorizontal;
    node.resize(node.width, 1); node.textAutoResize = 'HEIGHT';
    if ('layoutMode' in node.parent && node.parent.layoutMode !== 'NONE') {
      node.layoutSizingHorizontal = horizontal === 'FILL' ? 'FILL' : 'FIXED';
      node.layoutSizingVertical = 'HUG';
    }
  }));
  return node;
}
function spacer(parent, height, width = 416, fill = false) {
  const node = auto(parent, 'Spacing', width); node.resize(width, height); node.layoutSizingVertical = 'FIXED';
  if (fill) node.layoutSizingVertical = 'FILL'; return node;
}
function recolorIcon(instance, source, color = 'primary') {
  const sourceNodes = new Map(source.findAll(() => true).map(node => [node.id, node]));
  for (const node of [instance, ...instance.findAll(() => true)]) {
    const original = node === instance ? source : sourceNodes.get(node.id.split(';').pop());
    if (!original) continue;
    for (const property of ['fills', 'strokes']) if (property in node && property in original && Array.isArray(original[property])) {
      node[property] = original[property].map(p => p.type === 'SOLID' ? figma.variables.setBoundVariableForPaint({ ...p }, 'color', tokens[color]) : p);
      if (node.id.startsWith('I')) inheritedWrites.add(node.id);
    }
  }
}
function icon(parent, source, size = 24, color = 'primary') {
  const instance = sources[source].createInstance(); parent.appendChild(instance); instance.resize(size, size);
  recolorIcon(instance, sources[source], color);
  return instance;
}
function fitStatus(status) {
  if (!status || status.children.length < 3) return;
  const last = status.children[status.children.length - 1], filler = status.children[1];
  const overflow = last.x + last.width - status.width;
  if (overflow > 0.5) {
    const vertical = filler.layoutSizingVertical;
    filler.resize(filler.width - overflow, filler.height);
    filler.layoutSizingHorizontal = 'FIXED'; filler.layoutSizingVertical = vertical;
  }
}
function section(parent, title, description) {
  const instance = sources.section.createInstance(); parent.appendChild(instance);
  instance.setProperties({ 'Title#142:0': title, 'Description#142:1': description }); return instance;
}
function nav(parent, title, description, width = 416) {
  const instance = sources.nav.createInstance(); parent.appendChild(instance);
  instance.resize(width, 76); instance.setProperties({ 'Title#92:9': title, 'Description#92:10': description }); return instance;
}
function button(parent, label, width = 416) {
  const instance = sources.button.createInstance(); parent.appendChild(instance); instance.resize(width, 48);
  instance.setProperties({ 'Label#92:11': label }); return instance;
}
function component(parent, name, width, direction = 'VERTICAL', spacing = 0, fill = 'surface') {
  const node = figma.createComponent(); parent.appendChild(node); node.name = name; node.layoutMode = direction;
  node.resize(width, 1);
  node.primaryAxisSizingMode = direction === 'VERTICAL' ? 'AUTO' : 'FIXED';
  node.counterAxisSizingMode = direction === 'VERTICAL' ? 'FIXED' : 'AUTO';
  gap(node, spacing); node.fills = fill ? [paint(fill)] : []; customComponents.push(node); return node;
}
function bindTextProperty(owner, node, name) {
  const key = owner.addComponentProperty(name, 'TEXT', node.characters); node.componentPropertyReferences = { characters: key }; return key;
}
function route(node, destination, navigation = 'NAVIGATE') {
  reactions.push({ node, destination, navigation }); return node;
}
function closeRoute(node) { reactions.push({ node, close: true }); }
function profile(state) {
  const evidence = [
    ...(state === 'Ignored' ? [] : [{ id: 'cold-case', genres: ['Thriller', 'Crime'], reward: 3.5 / 4.5, weight: 1 }]),
    { id: 'star-road', genres: ['Science fiction', 'Drama'], reward: 3.5 / 4.5, weight: 1 },
    { id: 'small-town', genres: ['Drama'], reward: 1.5 / 4.5, weight: 1 },
    { id: 'sunday', genres: ['Comedy'], reward: -1.5 / 4.5, weight: 1 },
    ...(state === 'Reset' ? [] : [
      { id: 'late-shift', genres: ['Thriller'], reward: 1, weight: 0.5 },
      { id: 'dark-house', genres: ['Horror'], reward: -1, weight: 1 }
    ]),
    { id: 'long-road', genres: ['Drama'], reward: 0.15, weight: 0.5 }
  ];
  return ['Thriller', 'Crime', 'Science fiction', 'Drama', 'Comedy', 'Horror', 'Adventure'].map(genre => {
    const items = evidence.filter(e => e.genres.includes(genre));
    const mass = items.reduce((sum, e) => sum + e.weight, 0);
    return { genre, count: items.length, value: items.length ? items.reduce((sum, e) => sum + e.reward * e.weight, 0) / (3 + mass) : null };
  });
}
function score(state) {
  const prefs = profile(state);
  const taste = ['Thriller', 'Crime'].reduce((sum, genre) => sum + (prefs.find(p => p.genre === genre).value ?? 0), 0) / 2 * 18;
  const movements = [['Rediscover', 18], ['Taste', taste], ['Age', Math.log1p(45) * 1.5], ['Repeats', -3], ['Runtime', 3]];
  return { movements, total: 20 + movements.reduce((sum, [, v]) => sum + v, 0) };
}
const fmt = n => String(Math.round(n * 100) / 100);
const board = auto(null, boardName, 2480, 'VERTICAL', 24, 'paper'); roots.push(board);
board.x = startX; board.y = 160; pad(board, 40); board.placeholder = true;
text(board, 'Your taste · see what shapes your picks', 'board', 2400, 'boardInk');
text(board, 'Built from Cobalt’s existing Android settings system. Illustrative titles and feedback; scores follow the current genre formula.', 'body', 2400, 'boardInk');
const flows = auto(board, 'Five views · overview, points, feedback, evidence and first use', 2400, 'HORIZONTAL', 40);
const shelf = auto(null, 'Your taste · editable main components', 2480, 'VERTICAL', 24, 'paper'); roots.push(shelf);
shelf.x = startX; shelf.y = 2100; pad(shelf, 40);
text(shelf, 'Reusable insight components', 'board', 2400, 'boardInk');
const profileShelf = auto(shelf, 'Taste profile states', 2400, 'HORIZONTAL', 24);

const profileComponents = {};
for (const state of ['Current', 'Ignored', 'Reset']) {
  const chart = component(profileShelf, 'State=' + state, 416, 'VERTICAL', 5); pad(chart, 16); radius(chart);
  const axis = auto(chart, 'Signed preference scale', 384, 'HORIZONTAL');
  text(axis, 'Less often', 'meta', 144, 'muted'); text(axis, 'Neutral', 'meta', 96, 'muted', 'CENTER'); text(axis, 'More often', 'meta', 144, 'muted', 'RIGHT');
  for (const item of profile(state)) {
    const row = auto(chart, 'Preference / ' + item.genre, 384, 'VERTICAL', 4);
    const label = auto(row, 'Genre and supporting titles', 384, 'HORIZONTAL', 0);
    text(label, item.genre, 'row', 202); text(label, item.count ? item.count + ' ' + (item.count === 1 ? 'title' : 'titles') : 'No feedback yet', 'meta', 162, 'muted', 'RIGHT');
    icon(label, 'chevron', 16, 'muted');
    const plot = figma.createFrame(); row.appendChild(plot); plot.name = item.value === null ? 'Unknown · no numeric estimate' : 'Signed weight ' + item.value;
    plot.resize(384, 6); plot.fills = []; plot.clipsContent = false;
    const track = figma.createRectangle(); plot.appendChild(track); track.name = 'Scale −1 to +1'; track.resize(384, 4); track.y = 1; track.cornerRadius = 2; track.fills = [paint('outline', 0.22)];
    if (item.value !== null) {
      const zero = figma.createRectangle(); plot.appendChild(zero); zero.name = 'Neutral reference'; zero.resize(1, 8); zero.x = 191.5; zero.y = -1; zero.fills = [paint('muted', 0.55)];
      const mark = figma.createRectangle(); plot.appendChild(mark); mark.name = item.genre + ' · ' + item.value;
      const length = Math.abs(item.value) * 192; mark.resize(Math.max(length, 0.01), 4); mark.x = item.value < 0 ? 192 - length : 192; mark.y = 1; mark.cornerRadius = 2; mark.fills = [paint('primary')];
      chartChecks.push({ state, genre: item.genre, value: item.value, length, center: 192 });
    }
    spacer(row, 3, 384); if (item.genre === 'Thriller') row.name = 'Preference / Thriller · inspect evidence';
  }
  profileComponents[state] = chart;
}
const profileSet = figma.combineAsVariants(Object.values(profileComponents), profileShelf);
profileSet.name = 'Cobalt / Screening / Insights / Taste profile';
profileSet.children.forEach((n, i) => { n.x = i * 440; n.y = 0; });
profileSet.resizeWithoutConstraints(1296, Math.max(...profileSet.children.map(n => n.height))); customComponents.push(profileSet);

const evidenceShelf = auto(shelf, 'Evidence and correction primitives', 2400, 'HORIZONTAL', 24);
const evidenceComponent = component(evidenceShelf, 'Cobalt / Screening / Insights / Evidence row', 416, 'HORIZONTAL', 12, null);
pad(evidenceComponent, 16);
evidenceComponent.setBoundVariable('paddingTop', tokens.s8); evidenceComponent.setBoundVariable('paddingBottom', tokens.s8);
evidenceComponent.counterAxisAlignItems = 'CENTER';
const evidenceIcon = icon(evidenceComponent, 'movie', 24);
const evidenceIconProperty = evidenceComponent.addComponentProperty('Icon', 'INSTANCE_SWAP', sources.movie.id);
evidenceIcon.componentPropertyReferences = { mainComponent: evidenceIconProperty };
const evidenceCopy = auto(evidenceComponent, 'Evidence copy', 318, 'VERTICAL', 2); evidenceCopy.layoutSizingHorizontal = 'FILL';
const evidenceTitle = text(evidenceCopy, 'Cold Case · rated 9/10', 'row', 318); evidenceTitle.layoutSizingHorizontal = 'FILL';
const evidenceSource = text(evidenceCopy, 'Date not recorded · Saved rating', 'meta', 318, 'muted'); evidenceSource.layoutSizingHorizontal = 'FILL';
const evidenceEffect = text(evidenceCopy, 'More influence for Thriller and Crime', 'meta', 318, 'primary'); evidenceEffect.layoutSizingHorizontal = 'FILL';
icon(evidenceComponent, 'chevron', 18, 'muted');
const evidenceProps = {
  title: bindTextProperty(evidenceComponent, evidenceTitle, 'Title'),
  source: bindTextProperty(evidenceComponent, evidenceSource, 'Source'),
  effect: bindTextProperty(evidenceComponent, evidenceEffect, 'Current influence')
};
function evidence(parent, title, source, effect, iconName = 'movie', width = 416) {
  const instance = evidenceComponent.createInstance(); parent.appendChild(instance); instance.resize(width, instance.height);
  instance.primaryAxisSizingMode = 'FIXED'; instance.counterAxisSizingMode = 'AUTO'; instance.layoutSizingVertical = 'HUG';
  instance.setProperties({ [evidenceProps.title]: title, [evidenceProps.source]: source, [evidenceProps.effect]: effect, [evidenceIconProperty]: sources[iconName].id });
  recolorIcon(instance.children[0], sources[iconName]); return instance;
}

const scoreShelf = auto(shelf, 'Exact score chart states', 2400, 'HORIZONTAL', 24);
const scoreComponents = {};
for (const state of ['Current', 'Ignored', 'Reset']) {
  const chart = component(scoreShelf, 'State=' + state, 384, 'VERTICAL', 0, null);
  const plot = figma.createFrame(); chart.appendChild(plot); plot.name = 'Raw points · before variety and selection'; plot.resize(384, 190); plot.fills = []; plot.clipsContent = false;
  const data = score(state); let running = 20;
  const steps = [{ label: 'Base', value: 20, lower: 0, upper: 20 }, ...data.movements.map(([label, value]) => { const before = running; running += value; return { label, value, lower: Math.min(before, running), upper: Math.max(before, running) }; }), { label: 'Total', value: data.total, lower: 0, upper: data.total }];
  const baseline = 151, scale = 2.1, left = 28, stride = 49, barWidth = 28;
  for (const tick of [0, 20, 40, 60]) {
    const line = figma.createRectangle(); plot.appendChild(line); line.name = tick + ' point reference'; line.resize(356, 1); line.x = 28; line.y = baseline - tick * scale; line.fills = [paint('outline', 0.15)];
    const label = text(plot, String(tick), 'meta', 24, 'muted', 'RIGHT'); label.x = 0; label.y = line.y - 8;
  }
  steps.forEach((step, index) => {
    const mark = figma.createRectangle(); plot.appendChild(mark); mark.name = step.label + ' / ' + step.value + ' points';
    mark.resize(barWidth, Math.max((step.upper - step.lower) * scale, 0.01)); mark.x = left + index * stride; mark.y = baseline - step.upper * scale;
    mark.cornerRadius = 3; mark.fills = [paint(step.value < 0 ? 'muted' : 'primary', index === 0 ? 0.45 : 1)];
    const val = text(plot, (index > 0 && index < steps.length - 1 && step.value >= 0 ? '+' : '') + fmt(step.value), 'meta', 48, 'text', 'CENTER'); val.x = mark.x - 10; val.y = Math.max(0, mark.y - 20);
    const label = text(plot, step.label === 'Rediscover' ? 'Saved' : step.label, 'meta', 48, 'muted', 'CENTER'); label.x = mark.x - 10; label.y = 161;
  });
  scoreComponents[state] = chart;
  chartChecks.push({ state, base: 20, movements: data.movements, total: data.total, ending: running });
}
const scoreSet = figma.combineAsVariants(Object.values(scoreComponents), scoreShelf);
scoreSet.name = 'Cobalt / Screening / Insights / Score waterfall';
scoreSet.children.forEach((n, i) => { n.x = i * 408; n.y = 0; });
scoreSet.resizeWithoutConstraints(1200, 190); customComponents.push(scoreSet);

const confirmation = component(evidenceShelf, 'Cobalt / Screening / Insights / Correction dialog', 384, 'VERTICAL', 12); pad(confirmation, 24); radius(confirmation);
const modalTitle = text(confirmation, 'Ignore this rating?', 'sheet', 336);
const modalBody = text(confirmation, 'Keep the saved 9/10 rating, but remove its influence from recommendations. You can undo this.', 'body', 336, 'muted');
const modalActions = auto(confirmation, 'Dialog actions', 336, 'HORIZONTAL', 16); modalActions.primaryAxisAlignItems = 'MAX'; modalActions.counterAxisAlignItems = 'CENTER';
const cancelAction = auto(modalActions, 'Cancel / 48 dp target', 88, 'HORIZONTAL', 0); cancelAction.resize(88, 48); cancelAction.layoutSizingVertical = 'FIXED'; cancelAction.primaryAxisAlignItems = 'CENTER'; cancelAction.counterAxisAlignItems = 'CENTER'; text(cancelAction, 'Cancel', 'row', 80, 'primary', 'CENTER');
const confirmAction = auto(modalActions, 'Confirm / 48 dp target', 144, 'HORIZONTAL', 0); confirmAction.resize(144, 48); confirmAction.layoutSizingVertical = 'FIXED'; confirmAction.primaryAxisAlignItems = 'CENTER'; confirmAction.counterAxisAlignItems = 'CENTER'; text(confirmAction, 'Ignore rating', 'row', 136, 'primary', 'CENTER');
const modalProps = {
  title: bindTextProperty(confirmation, modalTitle, 'Title'),
  body: bindTextProperty(confirmation, modalBody, 'Body'),
  action: bindTextProperty(confirmation, confirmAction.children[0], 'Action')
};

function flowColumn(parent, label, note) {
  const column = auto(parent, label, 448, 'VERTICAL', 12);
  text(column, label, 'section', 448, 'boardInk');
  return { column, note };
}
function phone(parent, title, subtitle, name) {
  const root = auto(parent, name, 448, 'VERTICAL', 0, 'background'); root.resize(448, 997); root.layoutSizingVertical = 'FIXED';
  root.clipsContent = true; root.cornerRadius = 28; pad(root, 16); root.paddingTop = 18; root.setBoundVariable('paddingTop', tokens.s18);
  const status = sources.status.clone(); root.appendChild(status);
  fitStatus(status);
  spacer(root, 20);
  const top = sources.top.clone(); root.appendChild(top);
  spacer(root, 6);
  const heading = sources.header.clone(); root.appendChild(heading); heading.name = 'Shared Cobalt expanded settings header'; root.counterAxisAlignItems = 'CENTER';
  const headingTexts = heading.findAllWithCriteria({ types: ['TEXT'] }); headingTexts[0].characters = title; headingTexts[1].characters = subtitle;
  spacer(root, 16);
  const content = auto(root, 'Read-only insights content', 416, 'VERTICAL', 12);
  spacer(root, 1, 416, true);
  const gesture = figma.createRectangle(); root.appendChild(gesture); gesture.name = 'System gesture handle'; gesture.resize(108, 4); gesture.cornerRadius = 2; gesture.fills = [paint('text')];
  phones.push({ root, content, top, heading }); return { root, content, back: top.findAllWithCriteria({ types: ['FRAME'] }).find(n => n.name === 'Back') ?? top };
}
const overviewByState = {}, whyByState = {};
function buildOverview(parent, state = 'Current') {
  const view = phone(parent, 'Your taste', 'How your feedback shapes your picks.', 'Your taste / ' + state);
  const count = state === 'Ignored' ? 6 : state === 'Reset' ? 5 : 7;
  text(view.content, 'Shared across saved media · ' + count + ' contributing titles', 'meta', 416, 'muted');
  const heading = section(view.content, 'What your feedback suggests', 'The bars show tendencies, not certainty.');
  const plot = profileComponents[state].createInstance(); view.content.appendChild(plot);
  const why = nav(view.content, 'Midnight Signal · Why this pick?', 'Saved 45 days ago · Thriller / Crime');
  const recent = auto(view.content, 'Recent learning', 416, 'VERTICAL', 0);
  text(recent, 'Recent learning', 'section', 416);
  const action = state === 'Ignored'
    ? evidence(recent, 'Cold Case · rating ignored', 'Just now · Recommendation correction', 'Saved rating unchanged · tap to undo')
    : state === 'Reset'
      ? evidence(recent, 'Screening feedback cleared', 'Just now · Recommendation correction', 'Saved ratings still guide your picks', 'history')
      : evidence(recent, 'Cold Case · rated 9/10', 'Date not recorded · Saved rating', 'More influence for Thriller and Crime');
  const history = nav(view.content, 'See all feedback', state === 'Current' ? 'Inspect or correct what Cobalt has learned' : 'Review your remaining influences');
  overviewByState[state] = { ...view, heading, plot, why, action, history }; return overviewByState[state];
}
function buildWhy(parent, state = 'Current') {
  const view = phone(parent, 'Why this pick?', 'Midnight Signal · from your saved titles.', 'Why this pick / ' + state);
  const intro = auto(view.content, 'Selected title', 416, 'HORIZONTAL', 12, 'surface'); pad(intro, 16); radius(intro); intro.counterAxisAlignItems = 'CENTER';
  icon(intro, 'movie', 32); const copy = auto(intro, 'Title and real inputs', 340, 'VERTICAL', 2); text(copy, 'Midnight Signal', 'row', 340); text(copy, 'Thriller / Crime · 1h 38m · not marked watched', 'meta', 340, 'muted');
  const detail = auto(view.content, 'Raw ranking points', 416, 'VERTICAL', 8, 'surface'); pad(detail, 16); radius(detail);
  const headline = auto(detail, 'Points and unit', 384, 'HORIZONTAL', 12); headline.counterAxisAlignItems = 'CENTER';
  text(headline, fmt(score(state).total), 'title', 125, 'primary'); text(headline, 'Ranking points\nBefore variety and selection', 'meta', 247, 'muted');
  const chart = scoreComponents[state].createInstance(); detail.appendChild(chart);
  text(detail, "Base: every eligible title starts with 20 points.\nSaved: 45 days old and not marked watched.\nAge: time since saving. Taste: your genre feedback.\nRepeats: suggested twice. Runtime: under 100 minutes.", 'body', 384, 'text', 'LEFT', 'Plain-language explanation of every score contribution');
  text(detail, 'These points compare titles. They are not a percentage chance of enjoyment.', 'meta', 384, 'muted');
  section(view.content, 'Variety and the final choice', 'The raw score is only one part of choosing.');
  const selection = auto(view.content, 'How a shortlist becomes a pick', 416, 'VERTICAL', 8, 'surface'); pad(selection, 16); radius(selection);
  text(selection, 'Recent suggestions help shape a varied shortlist. Higher scores get more weight; they do not guarantee selection.', 'body', 384);
  text(selection, 'A valid daily pick can stay in place when you refresh.', 'meta', 384, 'muted');
  const find = nav(view.content, 'Why is another title missing?', 'Inspect its status, source or exclusions');
  whyByState[state] = { ...view, find }; return whyByState[state];
}

const first = flowColumn(flows, '01 · Your taste', 'One shared profile. Unknown genres stay unknown; counts show the evidence behind each tendency.');
const overview = buildOverview(first.column); text(first.column, first.note, 'body', 448, 'boardInk');
const second = flowColumn(flows, '02 · Why this pick?', 'The exact raw total, its contributions, and a separate explanation of variety and final selection.');
const why = buildWhy(second.column); text(second.column, second.note, 'body', 448, 'boardInk');
const third = flowColumn(flows, '03 · Your feedback', 'The source and available date stay visible. Correct recommendation influence without changing archive facts.');
const history = phone(third.column, 'Your feedback', 'Changes you can inspect and revisit.', 'Your feedback / Current');
text(history.content, 'Retained influences · 7 contributing titles', 'meta', 416, 'muted');
const historyGroup = auto(history.content, 'Connected feedback rows', 416, 'VERTICAL', 0, 'surface'); radius(historyGroup);
const records = [
  ['Dark House · Not interested', 'Today · Review response', 'Hidden from picks · less Horror influence', 'rejected'],
  ['Late Shift · Good pick', '15 Sep · Review response', 'More Thriller influence · fades over time', 'useful'],
  ['Cold Case · rated 9/10', 'Date not recorded · Saved rating', 'More influence for Thriller and Crime', 'movie'],
  ['Star Road · rated 9/10', 'Date not recorded · Saved rating', 'More Science fiction and Drama influence', 'movie'],
  ['Small Town · rated 7/10', 'Date not recorded · Saved rating', 'More influence for Drama', 'movie'],
  ['Sunday · rated 4/10', 'Date not recorded · Saved rating', 'Less influence for Comedy', 'movie'],
  ['Long Road · completed', 'Date not recorded · Saved status', 'Weak evidence · completion is not liking', 'check']
];
const recordInstances = records.map(record => evidence(historyGroup, ...record));
const resetNav = nav(history.content, 'Reset recommendation feedback', 'Keep saved ratings and actual watch progress');
text(history.content, 'An undated rating stays undated. Only recorded events get a date.', 'meta', 416, 'muted');
text(third.column, third.note, 'body', 448, 'boardInk');

await Promise.all(styleWrites.splice(0));
const fourth = flowColumn(flows, '04 · Evidence for a genre', 'Tap a tendency to inspect its sources. From a rating, open the correction dialog or review all feedback.');
const evidencePhone = overview.root.clone(); fourth.column.appendChild(evidencePhone); evidencePhone.name = 'Your taste / Thriller evidence sheet';
const scrim = figma.createRectangle(); evidencePhone.appendChild(scrim); scrim.name = 'Contextual sheet scrim'; scrim.layoutPositioning = 'ABSOLUTE'; scrim.resize(448, 997); scrim.x = scrim.y = 0; scrim.fills = [{ type: 'SOLID', color: { r: 0, g: 0, b: 0 }, opacity: 0.65 }];
const sheet = auto(evidencePhone, 'Thriller / evidence bottom sheet', 448, 'VERTICAL', 12, 'surface'); sheet.layoutPositioning = 'ABSOLUTE'; pad(sheet, 24); radius(sheet); sheet.bottomLeftRadius = sheet.bottomRightRadius = 0;
const sheetHead = auto(sheet, 'Sheet heading and close', 400, 'HORIZONTAL', 8); sheetHead.counterAxisAlignItems = 'CENTER'; text(sheetHead, 'Thriller', 'sheet', 344);
const sheetClose = auto(sheetHead, 'Close / 48 dp target', 48, 'HORIZONTAL'); sheetClose.resize(48, 48); sheetClose.layoutSizingVertical = 'FIXED'; sheetClose.primaryAxisAlignItems = 'CENTER'; sheetClose.counterAxisAlignItems = 'CENTER'; icon(sheetClose, 'close', 24);
text(sheet, 'Your feedback suggests more Thriller picks.', 'body', 400);
text(sheet, '2 titles · current preference weight +0.28', 'meta', 400, 'primary');
const sheetRating = evidence(sheet, 'Cold Case · your rating 9/10', 'Date not recorded · Saved rating', 'Stronger positive evidence', 'movie', 400);
evidence(sheet, 'Late Shift · you said Good pick', '15 Sep · Review response', 'Positive evidence with time decay', 'useful', 400);
text(sheet, 'The weight describes recommendation influence, not certainty about your taste.', 'meta', 400, 'muted');
const sheetReview = nav(sheet, 'Review this feedback', 'Correct or exclude a source of influence', 400);
spacer(sheet, 8, 400); const gestureRow = auto(sheet, 'Centered system gesture', 400, 'HORIZONTAL'); gestureRow.primaryAxisAlignItems = 'CENTER';
const sheetGesture = figma.createRectangle(); gestureRow.appendChild(sheetGesture); sheetGesture.name = 'System gesture handle'; sheetGesture.resize(108, 4); sheetGesture.fills = [paint('text')];
text(fourth.column, fourth.note, 'body', 448, 'boardInk');

const fifth = flowColumn(flows, '05 · Before the first feedback', 'No invented neutral profile or accuracy meter. Existing saved-title rules can still provide a useful pick.');
const empty = phone(fifth.column, 'Your taste', 'How your feedback shapes your picks.', 'Your taste / No evidence');
const emptyBody = auto(empty.content, 'A clear first-use state', 416, 'VERTICAL', 20); emptyBody.counterAxisAlignItems = 'CENTER'; spacer(emptyBody, 96);
icon(emptyBody, 'movie', 48);
text(emptyBody, 'No feedback yet', 'sheet', 384, 'text', 'CENTER');
text(emptyBody, 'Rate a saved title or respond to a Screening suggestion. Cobalt will show the evidence here.', 'body', 352, 'muted', 'CENTER');
const firstRating = button(emptyBody, 'Review a saved title', 352);
text(emptyBody, 'Until then, picks use saved titles and watch status. Opening a title does not mean you liked it.', 'meta', 352, 'muted', 'CENTER');
text(fifth.column, fifth.note, 'body', 448, 'boardInk');

text(board, 'Corrections keep you in control', 'sheet', 2400, 'boardInk');
const corrections = auto(board, 'Rating exclusion, reset scope and explanation', 2400, 'HORIZONTAL', 40);
const ignoreColumn = auto(corrections, 'Ignore a rating · preserve the saved rating', 448, 'VERTICAL', 12); text(ignoreColumn, 'Ignore one influence', 'section', 448, 'boardInk');
const ignoreDialogFrame = auto(ignoreColumn, 'Ignore rating / Modal', 384);
const ignoreDialog = confirmation.createInstance(); ignoreDialogFrame.appendChild(ignoreDialog);
const resetColumn = auto(corrections, 'Reset · preserve archive facts', 448, 'VERTICAL', 12); text(resetColumn, 'Reset only recommendation feedback', 'section', 448, 'boardInk');
const resetDialogFrame = auto(resetColumn, 'Clear Screening feedback / Modal', 384);
const resetDialog = confirmation.createInstance(); resetDialogFrame.appendChild(resetDialog);
resetDialog.setProperties({ [modalProps.title]: 'Clear Screening feedback?', [modalProps.body]: 'Clear Screening reactions and question responses? Saved ratings and watch progress stay. Saved ratings will still guide your picks.', [modalProps.action]: 'Clear feedback' });
const aboutColumn = auto(corrections, 'Existing information dialog', 448, 'VERTICAL', 12); text(aboutColumn, 'The existing information pattern', 'section', 448, 'boardInk');
const aboutFrame = auto(aboutColumn, 'How Your taste works / Modal', 384);
const about = sources.info.createInstance(); aboutFrame.appendChild(about);
about.setProperties({ 'Description#145:0': 'Ratings and Screening feedback shape your picks. Completion is weak evidence. Ranking points are not a chance-of-enjoyment percentage.' });
const aboutTitle = about.findAllWithCriteria({ types: ['TEXT'] }).find(n => n.characters === 'Customize'); aboutTitle.characters = 'How Your taste works'; if (aboutTitle.id.startsWith('I')) inheritedWrites.add(aboutTitle.id);
const rules = auto(corrections, 'Rules that keep the screen honest', 800, 'VERTICAL', 12);
text(rules, 'A correction changes recommendation influence, not Trakt history or your saved rating. A reset clears Screening feedback, then rebuilds taste from remaining saved facts.', 'body', 800, 'boardInk');
text(rules, 'The prototype uses illustrative evidence. Native implementation follows the shared explanation API and the feedback/selection fixes in the research plan.', 'body', 800, 'boardInk');

const outcomes = auto(null, 'Cobalt / Screening / Your taste · Correction outcomes', 2968, 'VERTICAL', 24, 'paper'); roots.push(outcomes); outcomes.x = startX; outcomes.y = 3900; pad(outcomes, 40);
text(outcomes, 'Corrections change the evidence, not the archive', 'board', 2888, 'boardInk');
text(outcomes, 'Live prototype destinations. Unknown genres, exact scores and active feedback update with the remaining illustrative evidence.', 'body', 2888, 'boardInk');
const outcomeFlow = auto(outcomes, 'After correction states', 2888, 'HORIZONTAL', 40);
const historyByState = { Current: history };
function buildHistoryAfterCorrection(parent, state) {
  const view = phone(parent, 'Your feedback', 'Changes you can inspect and revisit.', 'Your feedback / ' + state);
  text(view.content, 'Retained records · ' + (state === 'Ignored' ? 6 : 5) + ' active contributing titles', 'meta', 416, 'muted');
  const group = auto(view.content, 'Connected feedback rows', 416, 'VERTICAL', 0, 'surface'); radius(group);
  const remaining = state === 'Reset' ? records.slice(2) : records;
  const rows = remaining.map(record => {
    if (state === 'Ignored' && record[0].startsWith('Cold Case')) return evidence(group, 'Cold Case · rating ignored', 'Saved rating date not recorded', 'Saved 9/10 remains · excluded from taste');
    return evidence(group, ...record);
  });
  const reset = nav(view.content, 'Reset recommendation feedback', 'Keep saved ratings and actual watch progress');
  text(view.content, state === 'Ignored' ? 'Ignored sources remain inspectable. They do not count as active taste evidence.' : 'Screening responses were cleared. Your saved ratings and completion facts remain.', 'meta', 416, 'muted');
  historyByState[state] = { ...view, reset, rows }; return historyByState[state];
}
for (const state of ['Ignored', 'Reset']) {
  const overviewColumn = auto(outcomeFlow, 'Overview / ' + state, 448, 'VERTICAL', 12); text(overviewColumn, state === 'Ignored' ? 'Rating ignored · 6 titles remain' : 'Feedback reset · 5 titles remain', 'section', 448, 'boardInk'); buildOverview(overviewColumn, state);
  const whyColumn = auto(outcomeFlow, 'Score / ' + state, 448, 'VERTICAL', 12); text(whyColumn, 'Updated raw points · ' + fmt(score(state).total), 'section', 448, 'boardInk'); buildWhy(whyColumn, state);
  const historyColumn = auto(outcomeFlow, 'Feedback / ' + state, 448, 'VERTICAL', 12); text(historyColumn, 'Active feedback after correction', 'section', 448, 'boardInk'); buildHistoryAfterCorrection(historyColumn, state);
}

for (const state of ['Current', 'Ignored', 'Reset']) {
  const view = overviewByState[state]; route(view.why, whyByState[state].root); route(view.history, historyByState[state].root);
  route(view.back, '142:2003'); route(whyByState[state].back, view.root);
  if (state === 'Current') {
    const thriller = view.plot.findAllWithCriteria({ types: ['FRAME'] }).find(n => n.name === 'Preference / Thriller · inspect evidence'); route(thriller, evidencePhone);
    route(view.action, ignoreDialogFrame, 'OVERLAY');
  } else if (state === 'Ignored') route(view.action, overview.root);
  else route(view.action, aboutFrame, 'OVERLAY');
  const infoButton = view.heading.findAllWithCriteria({ types: ['INSTANCE'] }).find(n => n.name.includes('Info')); if (infoButton) route(infoButton, aboutFrame, 'OVERLAY');
}
route(history.back, overview.root); route(recordInstances[2], ignoreDialogFrame, 'OVERLAY'); route(resetNav, resetDialogFrame, 'OVERLAY');
for (const state of ['Ignored', 'Reset']) {
  route(historyByState[state].back, overviewByState[state].root); route(historyByState[state].reset, resetDialogFrame, 'OVERLAY');
  if (state === 'Ignored') route(historyByState[state].rows[2], overview.root);
}
route(sheetRating, ignoreDialogFrame, 'OVERLAY'); route(sheetReview, history.root); route(sheetClose, overview.root); route(scrim, overview.root);
route(empty.back, '142:2003'); route(firstRating, '142:2084');
for (const modal of [ignoreDialog, resetDialog]) {
  const cancel = modal.findAllWithCriteria({ types: ['FRAME'] }).find(n => n.name === 'Cancel / 48 dp target'); closeRoute(cancel);
  const confirm = modal.findAllWithCriteria({ types: ['FRAME'] }).find(n => n.name === 'Confirm / 48 dp target'); route(confirm, overviewByState[modal === ignoreDialog ? 'Ignored' : 'Reset'].root);
}
const aboutAction = about.findAllWithCriteria({ types: ['FRAME'] }).find(n => n.name === 'Dialog action'); closeRoute(aboutAction);

const eligibilityShelf = auto(shelf, 'Eligibility explanations', 2400, 'HORIZONTAL', 24);
for (const state of ['Current', 'Ignored', 'Reset']) {
  const frame = auto(eligibilityShelf, 'Titles outside the shortlist / ' + state, 384);
  const dialog = sources.info.createInstance(); frame.appendChild(dialog);
  dialog.setProperties({ 'Description#145:0': state === 'Reset'
    ? 'Long Road is completed. Unavailable titles and titles outside the selected source are also excluded. Dark House can return after Screening feedback is cleared.'
    : 'Dark House is hidden by Not interested. Long Road is completed. Unavailable titles and titles outside the selected source are also excluded.' });
  const title = dialog.findAllWithCriteria({ types: ['TEXT'] }).find(n => n.characters === 'Customize'); title.characters = 'Why titles are missing'; if (title.id.startsWith('I')) inheritedWrites.add(title.id);
  route(whyByState[state].find, frame, 'OVERLAY');
  closeRoute(dialog.findAllWithCriteria({ types: ['FRAME'] }).find(n => n.name === 'Dialog action'));
  if (state === 'Current') route(recordInstances[0], frame, 'OVERLAY');
}

await Promise.all(styleWrites);
sheet.x = 0; sheet.y = 997 - sheet.height;
// Figma navigation requires destinations to be separate page-level frames.
// Keep the review boards composed, and make linked top-level mobile frames for playback.
const prototypeSources = [...phones.map(p => p.root), evidencePhone, ignoreDialogFrame, resetDialogFrame, aboutFrame, ...eligibilityShelf.children, sources.setup, sources.review];
const prototypeSourcesUnique = [...new Map(prototypeSources.map(n => [n.id, n])).values()];
const cloneNodes = new Map(), prototypeFrames = new Map();
const prototypeHeading = auto(null, 'Your taste · playable prototype frames', 1912, 'VERTICAL', 12, 'paper'); roots.push(prototypeHeading);
prototypeHeading.x = startX; prototypeHeading.y = 5400; pad(prototypeHeading, 24);
text(prototypeHeading, 'Your taste · playable mobile flow', 'board', 1864, 'boardInk');
text(prototypeHeading, 'Start at Your taste / Current. The frames below support navigation, dialogs, corrections, undo and updated explanations.', 'body', 1864, 'boardInk');
function pairCloneTree(original, clone) {
  cloneNodes.set(original.id, clone);
  if ('children' in original && 'children' in clone) {
    if (original.children.length !== clone.children.length) throw new Error('Prototype clone hierarchy changed while mapping navigation.');
    original.children.forEach((child, index) => pairCloneTree(child, clone.children[index]));
  }
}
prototypeSourcesUnique.forEach((source, index) => {
  const clone = source.clone(); page.appendChild(clone); clone.name = 'Prototype / ' + source.name;
  fitStatus(clone.children.find(node => node.name === 'System status'));
  clone.x = startX + (index % 4) * 488; clone.y = 5560 + Math.floor(index / 4) * 1100;
  clone.placeholder = false; pairCloneTree(source, clone); prototypeFrames.set(source.id, clone); roots.push(clone);
});
function actionFor(reaction) {
  if (reaction.close) return { type: 'CLOSE' };
  const sourceDestinationId = typeof reaction.destination === 'string' ? reaction.destination : reaction.destination.id;
  const destination = prototypeFrames.get(sourceDestinationId);
  if (!destination) throw new Error('Missing top-level prototype destination: ' + sourceDestinationId);
  return { type: 'NODE', destinationId: destination.id, navigation: reaction.navigation,
    transition: { type: 'DISSOLVE', easing: { type: 'EASE_OUT' }, duration: 0.2 }, resetScrollPosition: true };
}
const navigationWrites = [];
for (const reaction of reactions.filter(r => r.node)) {
  const payload = [{ trigger: { type: 'ON_CLICK' }, actions: [actionFor(reaction)] }];
  navigationWrites.push(reaction.node.setReactionsAsync(payload));
  if (reaction.node.id.startsWith('I')) inheritedWrites.add(reaction.node.id);
  const clone = cloneNodes.get(reaction.node.id); if (clone) { navigationWrites.push(clone.setReactionsAsync(payload)); if (clone.id.startsWith('I')) inheritedWrites.add(clone.id); }
}
// The setup copy provides an entry into the new concept without changing the approved source.
const setupPrototype = prototypeFrames.get(sources.setup.id);
const reviewText = setupPrototype.findAllWithCriteria({ types: ['TEXT'] }).find(n => n.characters === 'Review a pick');
if (reviewText) {
  let entry = reviewText; while (entry && entry.type !== 'INSTANCE' && entry !== setupPrototype) entry = entry.parent;
  if (entry?.type === 'INSTANCE') {
    entry.setProperties({ 'Title#92:9': 'Your taste', 'Description#92:10': 'See what your feedback is teaching Cobalt' });
    navigationWrites.push(entry.setReactionsAsync([{ trigger: { type: 'ON_CLICK' }, actions: [{ type: 'NODE', destinationId: prototypeFrames.get(overview.root.id).id, navigation: 'NAVIGATE', transition: null }] }]));
  }
}
await Promise.all(navigationWrites);
await Promise.all(styleWrites);
page.flowStartingPoints = [...page.flowStartingPoints, { nodeId: prototypeFrames.get(overview.root.id).id, name: 'Your taste · inspect, explain and correct' }];
for (const view of phones) view.root.placeholder = false;
board.placeholder = false;
const failures = phones.filter(p => p.content.height > 761).map(p => ({ id: p.root.id, name: p.root.name, contentHeight: p.content.height }));
const nodes = roots.flatMap(root => [root, ...root.findAll(() => true)]);
const counts = {}; for (const node of nodes) counts[node.type] = (counts[node.type] ?? 0) + 1;
const fontsUsed = [...new Set(nodes.filter(n => n.type === 'TEXT').flatMap(n => n.getStyledTextSegments(['fontName']).map(s => s.fontName.family + ' / ' + s.fontName.style)))];
const imageNodes = nodes.filter(n => 'fills' in n && Array.isArray(n.fills) && n.fills.some(p => p.type === 'IMAGE')).map(n => ({ id: n.id, name: n.name, type: n.type, width: n.width, height: n.height }));
figma.viewport.scrollAndZoomIntoView([board]);
const response = {
  success: failures.length === 0, boardId: board.id, componentShelfId: shelf.id, outcomesId: outcomes.id,
  // I-prefixed IDs are inherited instance proxies, not independently created scene nodes.
  // Return every owned scene node, and every inherited proxy explicitly overridden above.
  createdNodeIds: [...new Set(nodes.filter(n => !n.id.startsWith('I')).map(n => n.id))],
  mutatedNodeIds: [page.id, ...inheritedWrites],
  inheritedInstanceDescendantCount: nodes.filter(n => n.id.startsWith('I')).length,
  screens: { overview: overview.root.id, why: why.root.id, history: history.root.id, evidence: evidencePhone.id, empty: empty.root.id, ignored: overviewByState.Ignored.root.id, reset: overviewByState.Reset.root.id },
  dialogs: { ignore: ignoreDialogFrame.id, reset: resetDialogFrame.id, about: aboutFrame.id },
  prototypeStartingPoint: prototypeFrames.get(overview.root.id).id,
  prototypeFrames: [...prototypeFrames.values()].map(n => ({ id: n.id, name: n.name, width: n.width, height: n.height })),
  phoneBounds: phones.map(p => ({ id: p.root.id, name: p.root.name, width: p.root.width, height: p.root.height, contentHeight: p.content.height })),
  failures, descendantCount: nodes.length, typeCounts: counts, fontsUsed, imageNodes,
  prototypeConnections: navigationWrites.length, reusableComponents: customComponents.map(n => ({ id: n.id, name: n.name, type: n.type })),
  exampleScores: ['Current', 'Ignored', 'Reset'].map(state => ({ state, ...score(state) })), chartChecks
};
if (JSON.stringify(response).length > 47000) throw new Error('Result must be split before returning all affected IDs.');
return response;
