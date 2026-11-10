from pathlib import Path
import re
import json
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[2]
drawables = root / 'app/src/main/res/drawable'
ns = '{http://schemas.android.com/apk/res/android}'
symbols = []
for path in sorted(drawables.glob('ic_custom_*.xml')) + sorted(drawables.glob('ic_format_*.xml')) + [drawables / 'ic_cobalt_logo.xml']:
    vector = ET.parse(path).getroot()
    name = path.stem.replace('ic_custom_', '').replace('ic_cobalt_logo', 'logo').replace('ic_format_', 'format_')
    def convert(parent):
        parts = []
        for node in parent:
            if node.tag == 'group':
                x, y = float(node.get(ns + 'pivotX', '0')), float(node.get(ns + 'pivotY', '0'))
                tx, ty = float(node.get(ns + 'translateX', '0')), float(node.get(ns + 'translateY', '0'))
                rotation = node.get(ns + 'rotation', '0')
                sx, sy = node.get(ns + 'scaleX', '1'), node.get(ns + 'scaleY', '1')
                transform = f'translate({tx+x} {ty+y}) rotate({rotation}) scale({sx} {sy}) translate({-x} {-y})'
                parts.append('<g transform="' + transform + '">' + convert(node) + '</g>')
            elif node.tag == 'path':
                fill = node.get(ns + 'fillColor', '#00000000')
                attributes = {'d': node.get(ns + 'pathData', ''), 'fill': 'none' if fill in ('#00000000', '@android:color/transparent') else 'currentColor'}
                if node.get(ns + 'fillType') == 'evenOdd':
                    attributes['fill-rule'] = 'evenodd'
                if node.get(ns + 'strokeColor'):
                    attributes.update({'stroke': 'currentColor', 'stroke-width': node.get(ns + 'strokeWidth', '1.5'), 'stroke-linecap': node.get(ns + 'strokeLineCap', 'butt'), 'stroke-linejoin': node.get(ns + 'strokeLineJoin', 'miter')})
                for source, target in [('fillAlpha', 'fill-opacity'), ('strokeAlpha', 'stroke-opacity')]:
                    if node.get(ns + source):
                        attributes[target] = node.get(ns + source)
                parts.append('<path ' + ' '.join(key + '="' + value + '"' for key, value in attributes.items()) + '/>')
        return ''.join(parts)
    parts = [convert(vector)]
    symbols.append('<symbol id="i-' + name + '" viewBox="0 0 ' + vector.get(ns + 'viewportWidth', '24') + ' ' + vector.get(ns + 'viewportHeight', '24') + '">' + ''.join(parts) + '</symbol>')
html_path = root / 'app/src/main/assets/web/index.html'
html = html_path.read_text()
sprite = '<svg xmlns="http://www.w3.org/2000/svg" aria-hidden="true" style="display:none">' + ''.join(symbols) + '</svg>'
html = re.sub(r'<svg xmlns="http://www.w3.org/2000/svg" aria-hidden="true" style="display:none">.*?</svg>', lambda _: sprite, html, count=1)
html_path.write_text(html)

# Read the owning Android catalog so web choices keep the exact order and vectors.
native = (root / 'app/src/main/kotlin/com/example/ui/screens/FoldersScreen.kt').read_text()
color_source = native.split('val folderPresetColors = listOf(', 1)[1].split('\n)', 1)[0]
icon_source = native.split('val folderPresetIcons = listOf(', 1)[1].split('\n)', 1)[0]
symbols = {}
for names, symbol in re.findall(r'((?:"[^"]+"(?:,\s*)?)+)\s*-> R.drawable.ic_custom_(\w+)', native):
    for name in re.findall(r'"([^"]+)"', names):
        symbols[name] = symbol
presets = {
    'colors': [{'hex': color, 'name': name} for color, name in re.findall(r'"(#[0-9A-Fa-f]{6})" to "([^"]+)"', color_source)],
    'icons': re.findall(r'"([^"]+)"', icon_source),
    'symbols': symbols,
}
assert len(presets['colors']) == 20 and len(presets['icons']) == 20
app_path = root / 'app/src/main/assets/web/app.js'
app = app_path.read_text()
app, count = re.subn(r'/\* native-folder-presets:start \*/.*?/\* native-folder-presets:end \*/',
    lambda _: '/* native-folder-presets:start */\nconst FOLDER_PRESETS = ' + json.dumps(presets, separators=(',', ':')) + ';\n/* native-folder-presets:end */', app, count=1, flags=re.S)
assert count == 1
app_path.write_text(app)
