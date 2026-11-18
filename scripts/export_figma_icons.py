"""Export Cobalt's Android vectors and referenced Material icons for Figma.

Original Android resources are kept intact. The manifest bridges clean SVG IDs,
human names, source resources and (after import) Figma component IDs.
"""
from pathlib import Path
import collections
import hashlib
import json
import re
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'design/icons'
ANDROID = '{http://schemas.android.com/apk/res/android}'
INK = '#1B1B1F'

CATEGORIES = {
    'Navigation': 'arrow_right back chevron_down chevron_right chevron_up close home search more_vert sidedock',
    'Actions & clipboard': 'check copy copy_success cut delete download edit export import paste plus redo select_all select_all_bold send share undo',
    'Capture & content': 'checklist code image link markdown movie music ocr quote text video voice',
    'Folders & organization': 'add_folder archive bookmark bookmark_filled filter folder folder_open folder_special grid list outline organization pin unarchive',
    'Settings & storage': 'api_call bar_chart globe history settings storage sync tools',
    'Privacy & security': 'eye eye_off key lock privacy terms vault',
    'Intelligence & reminders': 'ai_brain idea reminder sparkle',
    'People & interests': 'heart school shopping star work',
    'Help & feedback': 'feedback github help info mail survey',
    'Playback': 'pause play stop',
}
LABELS = {
    'add_folder': 'New folder', 'ai_brain': 'AI intelligence', 'api_call': 'API request',
    'back': 'Back', 'bar_chart': 'Usage statistics', 'bookmark_filled': 'Bookmark · filled',
    'copy_success': 'Copy complete', 'eye': 'Show', 'eye_off': 'Hide',
    'folder_special': 'Special folder', 'folder_open': 'Open folder', 'globe': 'Web companion',
    'heart': 'Favorite', 'idea': 'Idea', 'link_external': 'Open external link',
    'more_vert': 'More options', 'movie': 'Movie / show', 'ocr': 'Scan text (OCR)',
    'outline': 'Document outline', 'organization': 'Organization', 'plus': 'Add',
    'school': 'Study', 'select_all': 'Select all · outline', 'select_all_bold': 'Select all · bold',
    'sidedock': 'Side dock', 'terms': 'Terms of use', 'text': 'Text note',
    'voice': 'Voice recording', 'unarchive': 'Restore from archive', 'work': 'Work',
}
FORMAT_LABELS = {
    'h1': 'Heading 1', 'h2': 'Heading 2', 'h3': 'Heading 3', 'code_inline': 'Inline code',
    'code_block': 'Code block', 'list_bullet': 'Bulleted list', 'list_numbered': 'Numbered list',
    'table_column': 'Table column', 'table_row': 'Table row', 'link': 'Insert link',
}
SPECIAL = {
    'ic_capture_camera': ('Capture & content', 'camera', 'Camera capture'),
    'ic_cobalt_logo': ('Brand & artwork', 'logo', 'Cobalt logo'),
    'ic_launcher_background': ('Brand & artwork', 'launcher-background', 'Launcher background'),
    'ic_launcher_foreground': ('Brand & artwork', 'launcher-foreground', 'Launcher foreground'),
    'ic_welcome_blob': ('Brand & artwork', 'welcome-backdrop', 'Welcome backdrop'),
    'ic_dock_close': ('Navigation', 'dock-close', 'Close side dock'),
    'ic_trakt': ('Providers & ratings', 'trakt', 'Trakt'),
    'ic_custom_link_external': ('Navigation', 'external-link', 'Open external link'),
}


def title(value):
    return value.replace('_', ' ').replace('-', ' ').capitalize()


def identify(stem):
    if stem in SPECIAL:
        return SPECIAL[stem]
    if stem.startswith('ic_custom_'):
        key = stem.removeprefix('ic_custom_')
        category = next((c for c, names in CATEGORIES.items() if key in names.split()), None)
        if not category:
            raise ValueError(f'Uncategorized resource: {stem}')
        return category, key.replace('_', '-'), LABELS.get(key, title(key))
    if stem.startswith('ic_format_'):
        key = stem.removeprefix('ic_format_')
        return 'Text formatting', 'format-' + key.replace('_', '-'), FORMAT_LABELS.get(key, title(key))
    if stem.startswith('ic_nav_'):
        key = stem.removeprefix('ic_nav_')
        subject, style = key.rsplit('_', 1)
        return 'Bottom navigation', 'nav-' + key.replace('_', '-'), f'{title(subject)} · {style}'
    if stem.startswith('ic_settings_'):
        key = stem.removeprefix('ic_settings_')
        return 'Settings & storage', 'settings-' + key.replace('_', '-'), title(key)
    if stem.startswith('ic_provider_'):
        key = stem.removeprefix('ic_provider_')
        label = {'apple': 'Apple TV', 'disney': 'Disney+', 'hbo': 'HBO', 'prime': 'Prime Video',
                 'youtube': 'YouTube'}.get(key, title(key))
        return 'Providers & ratings', 'provider-' + key.replace('_', '-'), label
    if stem.startswith('ic_rating_'):
        key = stem.removeprefix('ic_rating_')
        return 'Providers & ratings', 'rating-' + key.replace('_', '-'), {
            'imdb': 'IMDb', 'rotten_tomatoes': 'Rotten Tomatoes', 'popcornmeter': 'Popcornmeter'}[key]
    raise ValueError(f'Uncategorized resource: {stem}')


def android_color(value):
    if value == '@android:color/transparent':
        return 'none', 0
    if not value.startswith('#'):
        raise ValueError(f'Unresolved color: {value}')
    h = value[1:]
    if len(h) == 8:
        alpha, h = int(h[:2], 16) / 255, h[2:]
    elif len(h) == 4:
        alpha, h = int(h[0] * 2, 16) / 255, ''.join(c * 2 for c in h[1:])
    else:
        alpha = 1
        if len(h) == 3:
            h = ''.join(c * 2 for c in h)
    return ('none' if alpha == 0 else '#' + h.upper()), alpha


def convert_vector(path):
    vector = ET.parse(path).getroot()
    colors = {android_color(v)[0] for n in vector.iter() for k, v in n.attrib.items()
              if k.endswith('Color') and android_color(v)[1] > 0}
    # Brand palettes remain untouched; tintable single-color resources use dark ink.
    monochrome = len(colors) == 1 and path.stem != 'ic_launcher_background'
    definitions = ET.Element('defs')
    clip_index = 0

    def children(parent, target):
        nonlocal clip_index
        current = target
        for node in parent:
            a = {k.removeprefix(ANDROID): v for k, v in node.attrib.items()}
            if node.tag == 'group':
                px, py = float(a.get('pivotX', 0)), float(a.get('pivotY', 0))
                tx, ty = float(a.get('translateX', 0)), float(a.get('translateY', 0))
                transform = f'translate({tx+px} {ty+py}) rotate({a.get("rotation",0)}) scale({a.get("scaleX",1)} {a.get("scaleY",1)}) translate({-px} {-py})'
                children(node, ET.SubElement(current, 'g', {'transform': transform}))
            elif node.tag == 'clip-path':
                clip_index += 1
                cid = f'clip-{clip_index}'
                clip = ET.SubElement(definitions, 'clipPath', {'id': cid, 'clipPathUnits': 'userSpaceOnUse'})
                ET.SubElement(clip, 'path', {'d': a['pathData'], 'clip-rule': 'evenodd' if a.get('fillType') == 'evenOdd' else 'nonzero'})
                current = ET.SubElement(current, 'g', {'clip-path': f'url(#{cid})'})
            elif node.tag == 'path':
                attrs = {'d': a['pathData']}
                for kind in ['fill', 'stroke']:
                    color, alpha = android_color(a.get(kind + 'Color', '#00000000'))
                    attrs[kind] = INK if monochrome and color != 'none' else color
                    alpha *= float(a.get(kind + 'Alpha', 1))
                    if color != 'none' and alpha != 1:
                        attrs[kind + '-opacity'] = str(alpha)
                for source, dest in [('strokeWidth', 'stroke-width'), ('strokeLineCap', 'stroke-linecap'),
                                     ('strokeLineJoin', 'stroke-linejoin'), ('strokeMiterLimit', 'stroke-miterlimit')]:
                    if source in a:
                        attrs[dest] = a[source]
                if a.get('fillType') == 'evenOdd':
                    attrs['fill-rule'] = 'evenodd'
                ET.SubElement(current, 'path', attrs)
            else:
                raise ValueError(f'Unsupported vector node: {node.tag}')

    w, h = vector.get(ANDROID + 'viewportWidth'), vector.get(ANDROID + 'viewportHeight')
    width = re.sub(r'(dp|dip|px)$', '', vector.get(ANDROID + 'width'))
    height = re.sub(r'(dp|dip|px)$', '', vector.get(ANDROID + 'height'))
    svg = ET.Element('svg', {'xmlns': 'http://www.w3.org/2000/svg', 'width': width, 'height': height, 'viewBox': f'0 0 {w} {h}'})
    content = ET.Element('g')
    children(vector, content)
    if len(definitions):
        svg.append(definitions)
    svg.append(content)
    return ET.tostring(svg, encoding='unicode'), monochrome, [float(width), float(height)]


COMMANDS = {
    'moveTo': 'M', 'moveToRelative': 'm', 'lineTo': 'L', 'lineToRelative': 'l',
    'horizontalLineTo': 'H', 'horizontalLineToRelative': 'h',
    'verticalLineTo': 'V', 'verticalLineToRelative': 'v',
    'curveTo': 'C', 'curveToRelative': 'c', 'reflectiveCurveTo': 'S', 'reflectiveCurveToRelative': 's',
    'quadTo': 'Q', 'quadToRelative': 'q', 'reflectiveQuadTo': 'T', 'reflectiveQuadToRelative': 't',
    'close': 'Z',
}


def material_svg(source):
    blocks = re.findall(r'materialPath(?:\s*\(([^)]*)\))?\s*\{([^}]+)\}', source, re.S)
    if not blocks:
        raise ValueError('No Material paths')
    paths = []
    for options, body in blocks:
        if options.strip():
            raise ValueError(f'Unparsed Material path options: {options}')
        commands = []
        for line in body.strip().splitlines():
            m = re.fullmatch(r'\s*(\w+)\(([^)]*)\)\s*', line)
            if not m or m[1] not in COMMANDS:
                raise ValueError(f'Unparsed Material command: {line}')
            args = m[2].replace('f', '').strip()
            if args and not re.fullmatch(r'[-\d.\s,]+', args):
                raise ValueError(f'Unparsed Material arguments: {args}')
            commands.append(COMMANDS[m[1]] + args)
        paths.append('<path fill="' + INK + '" d="' + ' '.join(commands) + '"/>')
    return '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">' + ''.join(paths) + '</svg>'


def main():
    existing_manifest = OUT / 'manifest.json'
    if existing_manifest.exists() and json.loads(existing_manifest.read_text()).get('version', 1) >= 2:
        raise SystemExit('This library is synced from current Figma vectors. Use scripts/sync_figma_icon_assets.py with a fresh Figma export bundle; Android bootstrap export would overwrite that source of truth.')
    source_files = [p for p in (ROOT / 'app/src/main').rglob('*') if p.is_file() and p.suffix in ['.kt', '.xml', '.html', '.js']]
    sources = {str(p.relative_to(ROOT)): p.read_text(errors='replace') for p in source_files}
    records = []

    def add(category, identity, label, source, svg, mono, size, source_kind, usages):
        slug = re.sub(r'[^a-z0-9]+', '-', category.lower()).strip('-')
        identity = 'cobalt-' + identity
        asset = OUT / 'svg' / slug / (identity + '.svg')
        asset.parent.mkdir(parents=True, exist_ok=True)
        ET.fromstring(svg)
        asset.write_text(svg + '\n')
        records.append({'id': identity, 'name': label, 'category': category,
                        'componentName': f'Cobalt / Icons / {category} / {label}',
                        'source': source, 'sourceKind': source_kind, 'svgFile': str(asset.relative_to(ROOT)),
                        'svgSha256': hashlib.sha256(svg.encode()).hexdigest(),
                        'monochrome': mono, 'nativeSize': size, 'usedIn': usages, 'svg': svg})

    for p in sorted((ROOT / 'app/src/main/res').rglob('*.xml')):
        if ET.parse(p).getroot().tag != 'vector':
            continue
        category, identity, label = identify(p.stem)
        svg, mono, size = convert_vector(p)
        pattern = re.compile(r'(?:R\.drawable\.|@drawable/)' + re.escape(p.stem) + r'\b')
        usages = [name for name, content in sources.items() if pattern.search(content)]
        add(category, identity, label, str(p.relative_to(ROOT)), svg, mono, size, 'Android vector', usages)

    # BOM 2024.09.00 pins Material icons to 1.7.0. Use the cached owning sources.
    version = '1.7.0'
    cache = Path.home() / '.gradle/caches/modules-2/files-2.1/androidx.compose.material'
    jars = [p for p in cache.glob(f'material-icons*-android/{version}/*/*sources.jar') if 'samples' not in p.name]
    archives = [(p, zipfile.ZipFile(p)) for p in jars]
    material_refs = sorted({m for s in sources.values() for m in re.findall(r'Icons\.(?:AutoMirrored\.)?(?:Default|Filled|Outlined|Rounded|Sharp|TwoTone)\.\w+', s)})
    for ref in material_refs:
        parts = ref.split('.')[1:]
        suffix = '/'.join([('filled' if x == 'Default' else x.lower()) for x in parts[:-1]] + [parts[-1] + '.kt'])
        matches = [(p, z, n) for p, z in archives for n in z.namelist() if n.endswith('/' + suffix)]
        if len(matches) != 1:
            raise ValueError(f'Missing/ambiguous Material source: {ref}')
        jar, archive, entry = matches[0]
        style = 'filled' if parts[-2] == 'Default' else parts[-2].lower()
        key = re.sub(r'(?<!^)(?=[A-Z])', '-', parts[-1]).lower()
        mirror = '-auto-mirrored' if 'AutoMirrored' in parts else ''
        label = re.sub(r'(?<!^)(?=[A-Z])', ' ', parts[-1]) + f' · {style}' + (' · auto mirrored' if mirror else '')
        add('Material fallbacks', 'material-' + style + '-' + key + mirror, label,
            f'androidx.compose.material:material-icons:{version}!/{entry}',
            material_svg(archive.read(entry).decode()), True, [24, 24], 'Material dependency',
            [name for name, content in sources.items() if re.search(r'\b' + re.escape(ref) + r'\b', content)])

    for relative, identity, label in [('assets/welcome_illustration.svg', 'welcome-illustration', 'Welcome illustration · SVG source'),
                                       ('docs/favicon.svg', 'website-favicon', 'Website favicon')]:
        svg = (ROOT / relative).read_text()
        element = ET.fromstring(svg)
        size = [float(re.sub(r'[^0-9.]', '', element.get(k, '24'))) for k in ['width', 'height']]
        add('Brand & artwork', identity, label, relative, svg, False, size, 'Standalone SVG', [])

    records.sort(key=lambda r: (r['category'], r['name'], r['id']))
    assert len({r['id'] for r in records}) == len(records)
    assert len({r['componentName'] for r in records}) == len(records)
    prior_path = OUT / 'manifest.json'
    prior = json.loads(prior_path.read_text()) if prior_path.exists() else {}
    prior_icons = {r['id']: r for r in prior.get('icons', [])}
    for record in records:
        for key in ['figma', 'catalogRow', 'catalogPosition']:
            if record['id'] in prior_icons and key in prior_icons[record['id']]:
                record[key] = prior_icons[record['id']][key]
    manifest = {'version': 1, 'total': len(records), 'categories': dict(collections.Counter(r['category'] for r in records)),
                'sourceCounts': dict(collections.Counter(r['sourceKind'] for r in records)),
                'naming': 'Stable kebab-case IDs; readable component names grouped by category.',
                'notes': ['Single-color vectors use neutral ink for visibility and can be tinted in Figma.',
                          'Multicolor branding and artwork keep their original palette.',
                          'All local vector resources are included, even resources with no static app reference.',
                          'Material auto-mirrored assets show their default LTR geometry; mirror horizontally for RTL.',
                          'Showcase screen SVGs are design documents rather than icon assets.'],
                'icons': [{k: v for k, v in r.items() if k != 'svg'} for r in records]}
    if 'figma' in prior:
        manifest['figma'] = prior['figma']
    if 'catalogOrdering' in prior:
        manifest['catalogOrdering'] = prior['catalogOrdering']
    (OUT / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    Path('/tmp/cobalt-figma-icons-payload.json').write_text(json.dumps(records))
    (OUT / 'README.md').write_text('# Cobalt icon library\n\n'
        f'{len(records)} editable SVG assets: 136 Android vectors, 29 referenced Material icons, and 2 standalone SVGs.\n\n'
        'See `manifest.json` for clean IDs, readable names, categories, original resources and usage locations. '
        'The `svg/` folders use the same categories as the Figma catalog. Original Android resources are unchanged.\n\n'
        'Regenerate using `python3 scripts/export_figma_icons.py`. Material exports read cached Apache-2.0 AndroidX '
        'source jars matching the app’s Compose BOM (1.7.0).\n\n'
        'Monochrome paths use neutral ink for editing. Brand palettes, clip paths, group transforms, '
        'fill rules and viewport proportions are preserved. Figma components contain vector paths; '
        'catalog tiles use instances so edits propagate.\n'
        + ('\nFigma catalog: ' + manifest['figma']['url'] + '\n' if 'figma' in manifest else ''))
    print(json.dumps({k: manifest[k] for k in ['total', 'categories', 'sourceCounts']}, indent=2))


if __name__ == '__main__':
    main()
