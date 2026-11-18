// Targeted update of the existing Cobalt Your taste concept and its linked states.
// Run with figma-use, figma-generate-design and figma-generate-library.
const page = await figma.getNodeByIdAsync('77:915');
await figma.setCurrentPageAsync(page);
if (page.children.some(n => n.name === 'Prototype / Other genres / Current')) {
  throw new Error('Others already exists. Inspect the existing update before another construction pass.');
}
const created = new Set(), mutated = new Set(), removed = new Set();
const states = ['Current', 'Ignored', 'Reset'];
const profileIds = { Current: '293:2535', Ignored: '293:2622', Reset: '293:2707' };
const protoIds = { Current: '293:4386', Ignored: '293:4820', Reset: '293:5214' };
const feedbackIds = { Current: '293:4639', Ignored: '293:5071', Reset: '293:5467' };
const scoreIds = { Current: '293:2811', Ignored: '293:2842', Reset: '293:2873' };
const [board, shelf, outcomes, navSource, closeSource, profileSet] = await Promise.all([
  ...['293:2528', '293:2532', '293:3523', '92:2215', '26:2707', '293:2792'].map(id => figma.getNodeByIdAsync(id))
]);
const charts = Object.fromEntries(await Promise.all(states.map(async s => [s, await figma.getNodeByIdAsync(profileIds[s])])));
const prototypes = Object.fromEntries(await Promise.all(states.map(async s => [s, await figma.getNodeByIdAsync(protoIds[s])])));
const scores = Object.fromEntries(await Promise.all(states.map(async s => [s, await figma.getNodeByIdAsync(scoreIds[s])])));
const styleIds = { body: 'S:cbe3138e29f088dd852eee16f3fbae0e4a2f6c4c,', meta: 'S:ef84359f1e29f42596a6e7ada0df55d87b04d849,',
  section: 'S:214a9a9ada5dbb8e84360bd38a243cc68e1e198a,', sheet: 'S:10be63aa6decf3077dc21749353477ab6f61e33d,' };
const variableIds = { surface: 'VariableID:92:2184', text: 'VariableID:92:2185', muted: 'VariableID:92:2186',
  primary: 'VariableID:92:2187', boardInk: 'VariableID:92:2192', s6: 'VariableID:77:951', s8: 'VariableID:77:952', s12: 'VariableID:77:953', s16: 'VariableID:77:954', r24: 'VariableID:77:958' };
const styles = Object.fromEntries(await Promise.all(Object.entries(styleIds).map(async ([k, id]) => [k, await figma.getStyleByIdAsync(id)])));
const tokens = Object.fromEntries(await Promise.all(Object.entries(variableIds).map(async ([k, id]) => [k, await figma.variables.getVariableByIdAsync(id)])));
if ([board, shelf, outcomes, navSource, closeSource, profileSet, ...Object.values(styles), ...Object.values(tokens)].some(n => !n)) throw new Error('A selected Cobalt asset is missing.');
const tasteRoots = [board, shelf, outcomes, ...page.children.filter(n => /^Prototype \/ (Your taste|Why this pick|Your feedback|Ignore rating|Clear Screening|How Your taste|Titles outside)/.test(n.name))];
const all = [...new Map(tasteRoots.flatMap(r => [r, ...r.findAll(() => true)]).map(n => [n.id, n])).values()];
const originalNodeIds = new Set(all.map(n => n.id));
const fontNames = new Map();
for (const t of [...all.filter(n => n.type === 'TEXT'), ...navSource.findAllWithCriteria({ types: ['TEXT'] })]) {
  for (const segment of t.getStyledTextSegments(['fontName'])) fontNames.set(JSON.stringify(segment.fontName), segment.fontName);
}
for (const s of Object.values(styles)) fontNames.set(JSON.stringify(s.fontName), s.fontName);
await Promise.all([...fontNames.values()].map(f => figma.loadFontAsync(f)));

// These fictional records now use the runtime's .65 response weight and 60-day half-life.
function profile(state) {
  const records = [
    ...(state === 'Ignored' ? [] : [{ key: 'cold', genres: ['Thriller', 'Crime'], reward: 3.5 / 4.5, amount: 1 }]),
    { key: 'star', genres: ['Science fiction', 'Drama'], reward: 3.5 / 4.5, amount: 1 },
    { key: 'town', genres: ['Drama'], reward: 1.5 / 4.5, amount: 1 },
    { key: 'sunday', genres: ['Comedy'], reward: -1.5 / 4.5, amount: 1 },
    ...(state === 'Reset' ? [] : [{ key: 'late', genres: ['Thriller'], reward: 1, amount: .65 / 2 },
      { key: 'dark', genres: ['Horror'], reward: -1, amount: .65 }]),
    { key: 'road', genres: ['Drama'], reward: .15, amount: .5 }
  ];
  return ['Thriller', 'Crime', 'Science fiction', 'Drama', 'Comedy', 'Horror', 'Adventure'].map(genre => {
    const evidence = records.filter(r => r.genres.includes(genre));
    const amount = evidence.reduce((a, r) => a + r.amount, 0);
    return { genre, count: evidence.length, value: amount ? evidence.reduce((a, r) => a + r.reward * r.amount, 0) / (3 + amount) : null };
  });
}
function ordered(state) {
  return profile(state).sort((a, b) => Number(b.value !== null) - Number(a.value !== null) ||
    Math.abs(b.value ?? 0) - Math.abs(a.value ?? 0) || b.count - a.count || a.genre.toLowerCase().localeCompare(b.genre.toLowerCase()));
}
function score(state) {
  const entries = profile(state);
  const taste = ['Thriller', 'Crime'].reduce((a, g) => a + (entries.find(r => r.genre === g).value ?? 0), 0) * 9;
  const features = [['Base', 20], ['Rediscover', 18], ['Taste', taste], ['Age', Math.log1p(45) * 1.5], ['Repeats', -3], ['Runtime', 3]];
  return { features, total: features.reduce((a, [, v]) => a + v, 0) };
}
const fmt = v => String(Math.round(v * 100) / 100);
function paint(role) { return figma.variables.setBoundVariableForPaint({ type: 'SOLID', color: { r: 0, g: 0, b: 0 } }, 'color', tokens[role]); }
function pad(n, amount) {
  for (const side of ['paddingTop', 'paddingBottom', 'paddingLeft', 'paddingRight']) {
    n[side] = amount; if (tokens['s' + amount]) n.setBoundVariable(side, tokens['s' + amount]);
  }
}
function auto(parent, name, width, gap = 12, background = null, direction = 'VERTICAL') {
  const n = figma.createAutoLayout(direction); created.add(n.id); n.name = name;
  if (parent) parent.appendChild(n);
  n.resize(width, 1); n.layoutSizingHorizontal = 'FIXED'; n.layoutSizingVertical = 'HUG'; n.itemSpacing = gap;
  if (tokens['s' + gap]) n.setBoundVariable('itemSpacing', tokens['s' + gap]);
  n.fills = background ? [paint(background)] : []; return n;
}
async function text(parent, value, role = 'body', width = 416, color = 'text') {
  const n = figma.createText(); created.add(n.id);
  await figma.loadFontAsync(n.fontName); parent.appendChild(n); await n.setTextStyleIdAsync(styles[role].id);
  n.resize(width, 1); n.textAutoResize = 'HEIGHT'; n.layoutSizingHorizontal = 'FIXED'; n.layoutSizingVertical = 'HUG';
  n.characters = value; n.name = value; n.fills = [paint(color)]; return n;
}
const backTemplate = JSON.parse(JSON.stringify(prototypes.Current.findOne(n => n.name === 'Back').reactions[0]));
function route(n, destinationId, navigation = 'NAVIGATE') {
  const r = JSON.parse(JSON.stringify(backTemplate));
  for (const a of [...(r.actions ?? []), ...(r.action ? [r.action] : [])]) { a.destinationId = destinationId; a.navigation = navigation; }
  n.reactions = [r]; mutated.add(n.id);
}
function nav(parent, title, subtitle, destinationId) {
  const n = navSource.createInstance(); parent.appendChild(n); n.name = title;
  n.resize(parent.width - parent.paddingLeft - parent.paddingRight, n.height); n.layoutSizingHorizontal = 'FIXED'; n.layoutSizingVertical = 'HUG';
  n.setProperties({ 'Title#92:9': title, 'Description#92:10': subtitle });
  if (destinationId) route(n, destinationId); created.add(n.id); return n;
}
const addedRoots = [];
const remainingShelf = auto(shelf, 'Other genres · complete evidence vocabulary', 2400, 24, null, 'HORIZONTAL');
const remainingComponents = {};
for (const state of states) {
  const chart = charts[state];
  const rows = chart.children.filter(n => n.type === 'FRAME' && n.name.startsWith('Preference /'));
  const byGenre = new Map(rows.map(r => [r.name.replace('Preference / ', '').replace(' · inspect evidence', ''), r]));
  const entries = ordered(state);
  const remainder = figma.createComponent(); created.add(remainder.id); remainingShelf.appendChild(remainder);
  remainder.name = 'State=' + state; remainder.layoutMode = 'VERTICAL'; remainder.resize(416, 1);
  remainder.primaryAxisSizingMode = 'AUTO'; remainder.counterAxisSizingMode = 'FIXED'; remainder.itemSpacing = 8;
  remainder.setBoundVariable('itemSpacing', tokens.s8); pad(remainder, 16); remainder.cornerRadius = 24;
  remainder.setBoundVariable('cornerRadius', tokens.r24); remainder.fills = [paint('surface')];
  remainder.description = 'Remaining genre tendencies for ' + state + '. Five lead the overview; every remaining genre is inspectable. No combined Others score.';
  const axis = chart.children.find(n => n.name === 'Signed preference scale').clone(); remainder.appendChild(axis); created.add(axis.id);
  for (const [index, entry] of entries.entries()) {
    const row = byGenre.get(entry.genre); if (!row) throw new Error('Missing existing genre row: ' + entry.genre);
    row.paddingTop = row.paddingBottom = 6; row.setBoundVariable('paddingTop', tokens.s6); row.setBoundVariable('paddingBottom', tokens.s6); mutated.add(row.id);
    const plot = row.findOne(n => n.type === 'FRAME' && (n.name.startsWith('Signed weight') || n.name.startsWith('Unknown')));
    if (entry.value !== null) {
      plot.name = 'Signed weight ' + entry.value; mutated.add(plot.id);
      const mark = plot.children.find(n => n.type === 'RECTANGLE' && n.name !== 'Scale −1 to +1' && n.name !== 'Neutral reference');
      const length = Math.abs(entry.value) * 192;
      mark.resize(Math.max(.01, length), 4); mark.x = entry.value < 0 ? 192 - length : 192;
      mark.name = entry.genre + ' · ' + entry.value; mutated.add(mark.id);
    }
    if (index < 5) chart.insertChild(index + 1, row); else remainder.appendChild(row);
  }
  chart.description = 'Five leading absolute tendencies from the complete shared taste profile. Counts are distinct titles; use Others to inspect the remaining genres.';
  chart.layoutSizingVertical = 'HUG'; mutated.add(chart.id); remainingComponents[state] = remainder;
}
profileSet.resizeWithoutConstraints(profileSet.width, Math.max(...states.map(s => charts[s].height))); mutated.add(profileSet.id);
const remainingSet = figma.combineAsVariants(Object.values(remainingComponents), remainingShelf); created.add(remainingSet.id);
remainingSet.name = 'Cobalt / Screening / Insights / Other genres';
remainingSet.children.forEach((c, i) => { c.x = i * 440; c.y = 0; mutated.add(c.id); });
remainingSet.resizeWithoutConstraints(1296, Math.max(...states.map(s => remainingComponents[s].height)));
remainingSet.description = 'Expandable Others content. Retains individual genre weights and evidence across Current, Ignored and Reset states.';

// Reconcile every waterfall and its frozen display totals with the new response policy.
const oldTotals = { Current: 48.0485176502892, Ignored: 45.028676380447926, Reset: 47.24296209473364 };
for (const state of states) {
  const plot = scores[state].children[0], calculation = score(state);
  const bars = plot.children.filter(n => n.type === 'RECTANGLE' && n.name.includes(' / '));
  let running = 0;
  for (const [index, [name, value]] of [...calculation.features, ['Total', calculation.total]].entries()) {
    const bar = bars[index], before = name === 'Total' ? 0 : running, after = name === 'Total' ? value : running + value;
    const top = 151 - Math.max(before, after) * 2.1;
    bar.name = name + ' / ' + value + ' points'; bar.resize(28, Math.max(.01, Math.abs(after - before) * 2.1)); bar.y = top; mutated.add(bar.id);
    const label = plot.children[plot.children.indexOf(bar) + 1];
    label.characters = index === 0 || name === 'Total' ? fmt(value) : (value > 0 ? '+' : '') + fmt(value);
    label.name = label.characters; label.y = top - 20; mutated.add(label.id); running = after;
  }
}
for (const t of all.filter(n => n.type === 'TEXT' && !n.id.startsWith('I'))) {
  let value = t.characters;
  for (const state of states) if (fmt(oldTotals[state]) !== fmt(score(state).total)) value = value.replaceAll(fmt(oldTotals[state]), fmt(score(state).total));
  value = value.replace('current preference weight +0.28', 'current preference weight +' + fmt(profile('Current').find(g => g.genre === 'Thriller').value));
  if (value.startsWith('Recent suggestions help shape a varied shortlist.')) value = 'Genre and purpose variety adjust the shortlist. Higher adjusted points give greater selection weight, without guaranteeing the top title.';
  if (value !== t.characters) { t.characters = value; mutated.add(t.id); }
}

// Three additional page-level prototype destinations, preserving every existing correction link.
let nextY = Math.max(...page.children.filter(n => n.name.startsWith('Prototype /')).map(n => n.y + n.height)) + 160;
let occupied = page.children.filter(n => n.x < 6648 + 3 * 488 && n.x + n.width > 6648 && n.y < nextY + 997 && n.y + n.height > nextY);
while (occupied.length) {
  nextY = Math.max(...occupied.map(n => n.y + n.height)) + 160;
  occupied = page.children.filter(n => n.x < 6648 + 3 * 488 && n.x + n.width > 6648 && n.y < nextY + 997 && n.y + n.height > nextY);
}
const otherPhones = {}, otherPlots = {};
function fillFooter(root) {
  const spacers = root.children.filter(n => n.name === 'Spacing');
  if (spacers.length) { spacers[spacers.length - 1].layoutSizingVertical = 'FILL'; mutated.add(spacers[spacers.length - 1].id); }
}
for (const [index, state] of states.entries()) {
  const root = prototypes[state].clone(); root.name = 'Prototype / Other genres / ' + state;
  root.x = 6648 + index * 488; root.y = nextY; addedRoots.push(root);
  const heading = root.children.find(n => n.name === 'Shared Cobalt expanded settings header');
  heading.children[0].characters = 'Other genres'; heading.children[1].characters = 'Every genre stays part of your taste.';
  mutated.add(heading.children[0].id); mutated.add(heading.children[1].id);
  const content = root.children.find(n => n.name === 'Read-only insights content');
  for (const c of [...content.children]) { for (const n of [c, ...('findAll' in c ? c.findAll(() => true) : [])]) removed.add(n.id); c.remove(); }
  await text(content, 'The overview shows five leading tendencies. Every genre still guides your recommendations; Others has no combined score.');
  const plot = remainingComponents[state].createInstance(); content.appendChild(plot); created.add(plot.id); plot.name = 'Remaining genre tendencies / ' + state;
  await text(content, 'Tap a genre to inspect the feedback behind it.', 'meta', 416, 'muted');
  nav(content, 'See all feedback', 'Inspect and correct the original evidence', feedbackIds[state]);
  route(root.findOne(n => n.name === 'Back'), protoIds[state]); fillFooter(root);
  otherPhones[state] = root; otherPlots[state] = plot;
}
const allOverviewRoots = [...new Map(tasteRoots.flatMap(r => [r, ...r.findAllWithCriteria({ types: ['FRAME'] })])
  .filter(n => n.type === 'FRAME' && n.width === 448 && n.height === 997).map(n => [n.id, n])).values()];
let overviewCount = 0;
for (const root of allOverviewRoots) {
  const content = root.children.find(n => n.name === 'Read-only insights content'); if (!content) continue;
  const plot = content.children.find(n => n.type === 'INSTANCE' && states.some(s => n.mainComponent?.id === profileIds[s]));
  if (!plot) continue;
  const state = states.find(s => plot.mainComponent.id === profileIds[s]);
  const disclosure = nav(content, 'Others', ordered(state).slice(5).length + ' more genres · inspect all', otherPhones[state].id);
  content.insertChild(content.children.indexOf(plot) + 1, disclosure); mutated.add(content.id); fillFooter(root); overviewCount++;
}

// Each remaining genre opens its own editable evidence panel, including missing-evidence states.
for (const [stateIndex, state] of states.entries()) {
  for (const [genreIndex, genre] of ordered(state).slice(5).entries()) {
    const panel = auto(null, 'Prototype / Others / ' + state + ' / ' + genre.genre + ' evidence', 448, 12, 'surface');
    panel.x = 6648 + stateIndex * 488; panel.y = nextY + 1157 + genreIndex * 600; pad(panel, 24);
    panel.cornerRadius = 24; panel.setBoundVariable('cornerRadius', tokens.r24); addedRoots.push(panel);
    const head = auto(panel, 'Genre heading and close', 400, 8, null, 'HORIZONTAL'); head.counterAxisAlignItems = 'CENTER';
    await text(head, genre.genre, 'sheet', 344);
    const close = auto(head, 'Close / 48 dp target', 48, 0, null, 'HORIZONTAL'); close.resize(48, 48); close.layoutSizingVertical = 'FIXED';
    close.primaryAxisAlignItems = close.counterAxisAlignItems = 'CENTER';
    const closeIcon = closeSource.createInstance(); close.appendChild(closeIcon); closeIcon.resize(24, 24); created.add(closeIcon.id); route(close, otherPhones[state].id);
    if (genre.value === null) {
      await text(panel, 'No feedback yet. This genre remains in your full taste profile.', 'body', 400);
      if (genre.genre === 'Crime') await text(panel, 'Cold Case · saved rating 9/10\nDate not recorded · excluded from learning. Your saved rating is kept.', 'body', 400);
      else if (genre.genre === 'Horror') await text(panel, 'Screening responses were cleared. Saved titles still keep this genre available for future feedback.', 'body', 400);
      else await text(panel, 'No personal rating or Screening response contributes to this genre yet.', 'meta', 400, 'muted');
    } else {
      await text(panel, (genre.value > 0 ? 'More often' : 'Less often') + ' · ' + genre.count + ' contributing title', 'body', 400);
      await text(panel, 'Learned weight ' + (genre.value > 0 ? '+' : '') + fmt(genre.value) + '. This is recommendation influence, not certainty.', 'meta', 400, 'muted');
      await text(panel, 'Sunday · your rating 4/10\nSaved personal rating · Date not recorded', 'body', 400);
    }
    nav(panel, 'Review all feedback', 'Correct an influence while keeping saved facts', feedbackIds[state]);
    const target = otherPlots[state].findOne(n => n.type === 'FRAME' && n.name.startsWith('Preference / ' + genre.genre));
    route(target, panel.id, 'OVERLAY');
  }
}

// Show the expanded destination beside the original five concept views.
const flow = board.children.find(n => n.name.startsWith('Five views'));
const column = auto(flow, '06 · Other genres', 448, 12); addedRoots.push(column);
await text(column, '06 · Other genres', 'section', 448, 'boardInk');
const preview = otherPhones.Current.clone(); column.appendChild(preview); preview.name = 'Other genres / Current'; addedRoots.push(preview);
await text(column, 'Five leading tendencies keep the overview clear. Every other genre and its evidence stays accessible.', 'body', 448, 'boardInk');
flow.name = 'Six views · overview, points, feedback, evidence, first use and Others'; flow.resize(2888, flow.height); flow.layoutSizingVertical = 'HUG'; mutated.add(flow.id);
board.resize(2968, board.height); board.layoutSizingVertical = 'HUG'; mutated.add(board.id); mutated.add(shelf.id);
const conceptCaption = await figma.getNodeByIdAsync('293:2530');
conceptCaption.characters = 'Cobalt’s existing settings design. Illustrative local-taste-v2 evidence: five leading genres, expandable Others and linked corrections.'; mutated.add(conceptCaption.id);
const firstNote = flow.children[0].children.find(n => n.type === 'TEXT' && n.characters.startsWith('One shared profile.'));
if (firstNote) { firstNote.characters = 'One shared profile. Five leading genres stay visible; Others keeps every remaining genre and its evidence accessible.'; mutated.add(firstNote.id); }

for (const r of [remainingShelf, remainingSet, ...addedRoots]) for (const n of [r, ...r.findAll(() => true)]) if (!originalNodeIds.has(n.id)) created.add(n.id);
const mainNodes = [board, ...board.findAll(() => true)], fonts = new Set();
for (const n of mainNodes.filter(n => n.type === 'TEXT')) for (const s of n.getStyledTextSegments(['fontName'])) fonts.add(s.fontName.family);
if ([...fonts].some(f => !['Noto Serif', 'Roboto'].includes(f))) throw new Error('Unexpected font in the concept: ' + [...fonts].join(', '));
const types = {}; for (const n of mainNodes) types[n.type] = (types[n.type] ?? 0) + 1;
const images = mainNodes.filter(n => 'fills' in n && Array.isArray(n.fills) && n.fills.some(p => p.type === 'IMAGE'));
if (images.length) throw new Error('The insight screens must remain editable, without flattened UI images.');
const validation = states.map(state => ({ state, overviewGenres: ordered(state).slice(0, 5).map(g => g.genre),
  otherGenres: ordered(state).slice(5).map(g => g.genre), profile: profile(state), score: score(state),
  overviewComponentId: charts[state].id, othersComponentId: remainingComponents[state].id, prototypeId: otherPhones[state].id }));
return { success: true, createdNodeIds: [...created], mutatedNodeIds: [...mutated], removedNodeIds: [...removed],
  overviewCount, remainingSetId: remainingSet.id, mainPreviewId: preview.id, boardId: board.id,
  fonts: [...fonts], editableTypes: types, imageFilledNodes: [], validation };
