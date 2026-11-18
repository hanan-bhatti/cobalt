// Prepend the source SVG map from assets.json before executing.
const page = await figma.getNodeByIdAsync('369:76');
await figma.setCurrentPageAsync(page);
const boards = await Promise.all(['466:20299', '472:20467'].map(id => figma.getNodeByIdAsync(id)));
const previews = boards.flatMap(n => n.findAll(c => c.type === 'FRAME' && c.name === 'Folder picker · Static example'));
const recency = boards.flatMap(n => n.findAll(c => c.type === 'TEXT' && ['Recent saves', 'Folders used recently can appear higher.'].includes(c.characters)));
const texts = [...previews.flatMap(n => n.findAllWithCriteria({ types: ['TEXT'] })), ...recency];
await Promise.all(texts.flatMap(t => t.getStyledTextSegments(['fontName']).map(s => figma.loadFontAsync(s.fontName))));
const variableIds = { Work: 'VariableID:146:1014', Ideas: 'VariableID:146:990', Reading: 'VariableID:146:996', WorkBox: 'VariableID:146:1017', WorkSurface: 'VariableID:146:1015', WorkBorder: 'VariableID:146:1016', IdeasBox: 'VariableID:146:993', ReadingBox: 'VariableID:146:999', ink: 'VariableID:146:953' };
const vars = {};
for (const [name, id] of Object.entries(variableIds)) vars[name] = await figma.variables.getVariableByIdAsync(id);
const created = [];
const mutated = [];
const removed = [];
function paint(name, opacity = 1) {
  return figma.variables.setBoundVariableForPaint({ type: 'SOLID', color: { r: .5, g: .5, b: .5 }, opacity }, 'color', vars[name]);
}
const kinds = { Work: 'work', Ideas: 'idea', Reading: 'text' };
for (const preview of previews) {
  for (const r of preview.children.filter(n => n.type === 'FRAME' && n.name.startsWith('Example folder · '))) {
    const name = r.name.replace('Example folder · ', '');
    const oldIcon = r.children.find(n => n.name === 'Cobalt icon · folder');
    if (!oldIcon) throw new Error('Preview already changed; inspect before retrying: ' + r.id);
    removed.push(oldIcon.id, ...oldIcon.findAll(() => true).map(c => c.id)); oldIcon.remove();
    const badge = figma.createAutoLayout('VERTICAL'); created.push(badge.id);
    badge.name = 'Folder icon box · ' + name; badge.resize(32, 32); badge.layoutSizingHorizontal = 'FIXED'; badge.layoutSizingVertical = 'FIXED';
    badge.paddingTop = 8; badge.paddingBottom = 8; badge.paddingLeft = 8; badge.paddingRight = 8; badge.cornerRadius = 10;
    badge.fills = [paint(name + 'Box')]; r.insertChild(0, badge);
    const glyph = figma.createNodeFromSvg(svg[kinds[name]]); created.push(glyph.id, ...glyph.findAll(() => true).map(c => c.id));
    glyph.name = 'Cobalt folder icon · ' + kinds[name]; glyph.resize(16, 16); badge.appendChild(glyph);
    for (const v of glyph.findAll(n => n.type === 'VECTOR' || n.type === 'BOOLEAN_OPERATION')) {
      if ('fills' in v && v.fills.length) v.fills = [paint(name)];
      if ('strokes' in v && v.strokes.length) v.strokes = [paint(name)];
    }
    r.paddingTop = 4; r.paddingBottom = 4; r.itemSpacing = 12; mutated.push(r.id);
    const label = r.children.find(n => n.type === 'TEXT' && n.characters === name);
    label.layoutSizingHorizontal = 'FILL'; mutated.push(label.id);
    const suggested = r.children.find(n => n.type === 'FRAME' && n.name === 'Suggested badge');
    if (suggested) {
      r.fills = [paint('WorkSurface')]; r.strokes = [paint('WorkBorder')]; suggested.fills = [paint('WorkSurface')]; mutated.push(suggested.id);
      for (const t of suggested.findAllWithCriteria({ types: ['TEXT'] })) { t.fills = [paint('ink')]; mutated.push(t.id); }
    }
  }
}
for (const t of recency) {
  t.characters = t.characters === 'Recent saves' ? 'Recent captures' : 'Folders with recently captured items can appear higher.';
  t.name = t.characters; mutated.push(t.id);
}
return { boardIds: boards.map(n => n.id), previewCount: previews.length, createdNodeIds: created, mutatedNodeIds: mutated, removedNodeIds: removed };
