"""Build Cobalt's vector campaign from genuine emulator captures.

SVG is the interchange format for native Figma import. No generated app UI.
Run: python3 design/showcase/build_assets.py
"""
from pathlib import Path
from html import escape
import argparse
import base64
import hashlib
import json
import os
import shutil
import struct
import subprocess
import concurrent.futures

ROOT = Path(__file__).resolve().parent
RAW = ROOT / 'raw'
SOURCE = ROOT / 'source'
EXPORT = ROOT / 'exports'
PREVIEW = ROOT / 'previews'
REPO = ROOT.parents[1]
for directory in (SOURCE, EXPORT, PREVIEW):
    directory.mkdir(exist_ok=True)

# surface, tonal container, foreground, secondary foreground
PALETTES = [
    ('#EEF5EA', '#D6E8CF', '#173D2B', '#47634C'),
    ('#F2EDFA', '#DDD3EE', '#382C52', '#695B7C'),
    ('#FFF0E7', '#F7D4BD', '#57341F', '#84624B'),
    ('#F8F3E8', '#E9DEC4', '#443A28', '#766A55'),
    ('#EEF2FC', '#D6E0F6', '#263B60', '#576B8D'),
    ('#F8EDFA', '#EAD3ED', '#503052', '#7E5C80'),
    ('#FFF7DB', '#EFE0A4', '#4B421F', '#7C7047'),
    ('#FFF0EE', '#F4D0C9', '#593630', '#886159'),
    ('#E8F6F1', '#C5E8DD', '#214E43', '#517A6E'),
    ('#EEF0FC', '#D6DAF3', '#30375F', '#61698C'),
    ('#2C223A', '#493556', '#F6EDFE', '#D3BFDE'),
    ('#F5EFFE', '#E3D4F0', '#45305B', '#78608E'),
    ('#EAF5FA', '#CBE5EF', '#234752', '#577984'),
    ('#FBEFF3', '#EFD4DF', '#553541', '#866574'),
    ('#F0F3E7', '#DAE3C7', '#36482A', '#657653'),
]

PANELS = [
    dict(slug='01-home', label='YOUR PERSONAL ARCHIVE', title=['A home for', 'what you keep.'], body=['Notes, links, images, code and media.', 'One archive to come back to.'], captures=['home-list'], layout='hero'),
    dict(slug='02-grid-and-list', label='TWO WAYS TO BROWSE', title=['Your archive.', 'Your point of view.'], body=['Switch between a compact list', 'and a visual grid.'], captures=['home-list', 'home-grid-final'], layout='duo'),
    dict(slug='03-quick-capture', label='CAPTURE THE MOMENT', title=['Catch it', 'while it’s fresh.'], body=['Start a note. Save a link.', 'Add an image or record a memo.'], captures=['add-options-final', 'image-reader'], layout='capture'),
    dict(slug='04-notes', label='ROOM TO THINK', title=['Make space', 'for a good idea.'], body=['Markdown, checklists and quotes.', 'Ready for your next visit.'], captures=['note-reader'], layout='reader'),
    dict(slug='05-code', label='SMALL THINGS WORTH SAVING', title=['Keep the snippet.', 'Skip the hunt.'], body=['Save useful code beside', 'the rest of your notes.'], captures=['code-reader'], layout='tilt'),
    dict(slug='06-links', label='A LITTLE CONTEXT HELPS', title=['Save the link.', 'Keep the context.'], body=['A preview, a description', 'and the original source.'], captures=['link-detail'], layout='link'),
    dict(slug='07-folders', label='ORGANIZE / 01', title=['A place for', 'every thread.'], body=['Distinct colors. Familiar icons.', 'Folders for the things you care about.'], captures=['folders-final'], layout='connected'),
    dict(slug='08-create-folder', label='ORGANIZE / 02', title=['Give it a color.', 'Make it yours.'], body=['Choose a name and an icon.', 'Pin it when you want it close.'], captures=['add-folder-final'], layout='connected'),
    dict(slug='09-pinned-folder', label='ORGANIZE / 03', title=['Good ideas,', 'within reach.'], body=['Pin a folder. Give your saved', 'thoughts a familiar place.'], captures=['pinned-folder', 'note-reader'], layout='connected'),
    dict(slug='10-search', label='FIND YOUR WAY BACK', title=['Find the thought', 'you nearly forgot.'], body=['Search your saved archive', 'across different content types.'], captures=['search'], layout='search'),
    dict(slug='11-media-hub', label='YOUR MEDIA HUB', title=['A little hub for', 'your next watch.'], body=['Movies, TV shows, anime and manga.', 'A collection beyond your notes.'], captures=['media-hub'], layout='media'),
    dict(slug='12-media-detail', label='TAKE A CLOSER LOOK', title=['More than', 'a title to save.'], body=['Explore the synopsis, rating', 'and watch status.'], captures=['media-detail'], layout='detail'),
    dict(slug='13-settings', label='THE WAY YOU USE COBALT', title=['Your archive.', 'Your preferences.'], body=['Appearance, media providers', 'and the tools behind your capture flow.'], captures=['settings-heatmap'], layout='settings'),
    dict(slug='14-appearance', label='A PERSONAL TOUCH', title=['Make Cobalt', 'feel like you.'], body=['Light or dark. Dynamic color.', 'Frosted blur you can tune.'], captures=['appearance'], layout='appearance'),
    dict(slug='15-storage', label='ON THIS DEVICE', title=['Know what’s', 'in your archive.'], body=['Saved notes, draft recovery,', 'Trash and saved history.'], captures=['storage'], layout='storage'),
]

def rect(x,y,w,h,color,r=0,extra=''):
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{color}" {extra}/>'

def text(value,x,y,size,color,weight=400,spacing=0,anchor='start'):
    return f'<text x="{x}" y="{y}" font-family="Inter" font-size="{size}" font-weight="{weight}" letter-spacing="{spacing}" text-anchor="{anchor}" fill="{color}">{escape(value)}</text>'

IMAGES = {}
def image(name):
    if name not in IMAGES:
        path = RAW / f'{name}.png'
        if not path.is_file():
            raise FileNotFoundError(f'Missing genuine capture: {path}')
        IMAGES[name] = 'data:image/png;base64,' + base64.b64encode(path.read_bytes()).decode()
    return IMAGES[name]

PHONE_COUNT = 0
def phone(name,x,y,w=620,angle=0,tint='#334A3D'):
    global PHONE_COUNT
    PHONE_COUNT += 1
    uid = f'phone-{PHONE_COUNT}'
    bezel = w * .025
    sw = w - 2*bezel
    sh = sw * 2992 / 1344
    h = sh + 2*bezel
    cx,cy=x+w/2,y+h/2
    result = f'<g id="{uid}" transform="rotate({angle} {cx} {cy})">'
    result += rect(x-5,y+15,w+10,h+9,tint,w*.105,'opacity="0.13"')
    result += rect(x,y,w,h,tint,w*.105)
    result += rect(x+3,y+3,w-6,h-6,'#19251F',w*.1,'stroke="#8B9A90" stroke-width="1.5"')
    result += rect(x+w,y+h*.14,4,h*.055,tint,2)
    result += rect(x+w,y+h*.23,4,h*.075,tint,2)
    result += f'<defs><clipPath id="{uid}-clip">{rect(x+bezel,y+bezel,sw,sh,"white",w*.078)}</clipPath></defs>'
    result += f'<image id="capture-{escape(name)}" x="{x+bezel}" y="{y+bezel}" width="{sw}" height="{sh}" clip-path="url(#{uid}-clip)" xlink:href="{image(name)}"/>'
    return result + '</g>'

def svg(content,w=1080,h=1920,viewbox=None):
    return f'<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="{w}" height="{h}" viewBox="{viewbox or f"0 0 {w} {h}"}">{content}</svg>'

def header(panel,palette,index):
    bg,container,ink,muted=palette
    result=text('Cobalt',76,113,38,ink,600,-1)
    result+=text(panel['label'],1004,106,17,muted,500,2,'end')
    max_chars=max(map(len,panel['title']))
    size=86 if max_chars < 18 else 77
    for line,value in enumerate(panel['title']):
        result+=text(value,76,251+line*97,size,ink,600,-3)
    for line,value in enumerate(panel['body']):
        result+=text(value,80,413+line*41,28,muted)
    return result

def background(palette,variant):
    bg,c,ink,m=palette
    result=rect(0,0,1080,1920,bg)
    if variant in ('hero','media','storage'):
        result+=f'<circle cx="540" cy="1280" r="650" fill="{c}"/>'
    elif variant in ('duo','appearance'):
        result+=rect(-110,790,1340,1100,c,350)
    elif variant in ('tilt','capture'):
        result+=f'<path d="M0 1010 Q540 660 1080 1000 L1080 1920 H0Z" fill="{c}"/>'
    elif variant in ('reader','detail'):
        result+=rect(150,615,780,1390,c,390)
    elif variant=='search':
        result+=f'<circle cx="180" cy="1250" r="620" fill="{c}"/>'
    else:
        result+=f'<path d="M0 1250 Q600 750 1080 1080 V1920 H0Z" fill="{c}"/>'
    return result

def compose(panel,palette,index):
    result=background(palette,panel['layout'])+header(panel,palette,index)
    names=panel['captures']; mode=panel['layout']; tint=palette[2]
    if mode=='hero':result+=phone(names[0],230,535,620,tint=tint)
    elif mode=='duo':
        result+=phone(names[0],-30,755,530,-5,tint)
        result+=phone(names[1],500,600,570,4,tint)
    elif mode=='capture':
        result+=phone(names[1],-130,830,520,-8,tint)
        result+=phone(names[0],380,605,650,5,tint)
    elif mode=='reader':result+=phone(names[0],220,555,640,-2,tint)
    elif mode=='tilt':result+=phone(names[0],170,605,740,6,tint)
    elif mode=='link':result+=phone(names[0],170,575,690,-4,tint)
    elif mode=='search':
        result+=rect(80,560,285,78,palette[1],39)
        result+=text('project',222,612,37,palette[2],500,-1,'middle')
        result+=rect(82,742,916,890,palette[2],42,'opacity="0.10"')
        result+=rect(90,730,900,870,'#F7FAF5',36)
        result+='<defs><clipPath id="search-excerpt">'+rect(90,730,900,870,'white',36)+'</clipPath></defs>'
        result+=f'<image x="90" y="730" width="900" height="{900*2992/1344}" clip-path="url(#search-excerpt)" xlink:href="{image(names[0])}"/>'
        result+=text('Images + code, found together.',90,1720,28,palette[3])
    elif mode=='media':result+=phone(names[0],205,550,670,-3,tint)
    elif mode=='detail':result+=phone(names[0],225,560,640,3,tint)
    elif mode=='settings':result+=phone(names[0],130,600,710,-4,tint)
    elif mode=='appearance':result+=phone(names[0],300,570,680,4,tint)
    elif mode=='storage':result+=phone(names[0],205,565,670,0,tint)
    return svg(result)

def connected_panels():
    # One composition, exported into three adjacent panels. Devices and the
    # broad tonal ribbon continue over panel boundaries without stretching UI.
    content=''
    for i in range(3):
        p=PALETTES[i+6]
        content+=rect(i*1080,0,1080,1920,p[0])
        content+=f'<defs><clipPath id="panel-{i}">{rect(i*1080,0,1080,1920,"white")}</clipPath></defs>'
        content+=f'<g clip-path="url(#panel-{i})"><path d="M-300 1370 C600 700 1450 1600 2100 1080 S3100 1050 3540 1450 L3540 2100 H-300Z" fill="{p[1]}"/></g>'
        content+=f'<g transform="translate({i*1080},0)">{header(PANELS[i+6],p,i+6)}</g>'
    content+=phone('folders-final',425,605,720,-3,PALETTES[6][2])
    content+=phone('add-folder-final',1455,560,650,2,PALETTES[7][2])
    content+=phone('pinned-folder',2240,850,530,-5,PALETTES[8][2])
    content+=phone('note-reader',2670,595,580,4,PALETTES[8][2])
    (SOURCE/'07-09-organize-panorama.svg').write_text(svg(content,3240,1920))
    for i in range(3):
        # Outer clipping keeps each file exactly one store panel wide.
        bounded=f'<defs><clipPath id="export-bound">{rect(i*1080,0,1080,1920,"white")}</clipPath></defs><g clip-path="url(#export-bound)">{content}</g>'
        (SOURCE/f'{PANELS[i+6]["slug"]}.svg').write_text(svg(bounded,1080,1920,f'{i*1080} 0 1080 1920'))

def banner():
    bg,c,ink,muted=PALETTES[0]
    result=rect(0,0,2560,1280,bg)
    result+=f'<circle cx="2090" cy="790" r="1010" fill="{c}"/>'
    result+=text('Cobalt',112,172,65,ink,600,-2)
    result+=text('Your ideas.',105,445,145,ink,600,-7)
    result+=text('All together.',105,610,145,ink,600,-7)
    result+=text('A personal archive for notes, links,',115,742,39,muted)
    result+=text('images, code and media.',115,803,39,muted)
    result+=rect(115,930,218,62,'#D6E8CF',31)
    result+=text('LOCAL NOTES',224,970,21,ink,500,2,'middle')
    result+=rect(351,930,224,62,'#D6E8CF',31)
    result+=text('OPEN SOURCE',463,970,21,ink,500,2,'middle')
    result+=rect(593,930,180,62,'#D6E8CF',31)
    result+=text('ANDROID',683,970,21,ink,500,2,'middle')
    result+=phone('folders-final',1260,430,475,-10,ink)
    result+=phone('media-hub',2060,305,480,11,ink)
    result+=phone('home-list',1650,105,525,-2,ink)
    (SOURCE/'readme-banner.svg').write_text(svg(result,2560,1280))

def render(path):
    env=os.environ.copy()
    env['FONTCONFIG_FILE']=str(ROOT/'fontconfig.xml')
    out=EXPORT/f'{path.stem}.png'
    subprocess.run(['magick','-background','none',str(path),'-alpha','off','-depth','8',
                    'PNG24:'+str(out)],env=env,check=True,capture_output=True)
    return out

def png_size(path):
    with path.open('rb') as image:
        header = image.read(24)
    if header[:8] != b'\x89PNG\r\n\x1a\n':
        raise ValueError(f'Invalid PNG: {path}')
    return struct.unpack('>II', header[16:24])

def publication_files():
    manifest = json.loads((ROOT / 'manifest.json').read_text())
    if [p['slug'] for p in manifest] != [p['slug'] for p in PANELS]:
        raise ValueError('The manifest and campaign panel order differ.')
    copies = {}
    for panel in manifest:
        filename = f'{panel["slug"]}.png'
        source = EXPORT / filename
        if png_size(source) != (1080, 1920):
            raise ValueError(f'Expected a 1080 × 1920 panel: {source}')
        for capture, digest in panel['raw_sha256'].items():
            if hashlib.sha256((RAW / f'{capture}.png').read_bytes()).hexdigest() != digest:
                raise ValueError(f'Capture changed; rebuild before publishing: {capture}')
        for folder in ('assets/screenshots', 'docs/assets/screenshots',
                       'fastlane/metadata/android/en-US/images/phoneScreenshots'):
            copies[REPO / folder / filename] = source
    if png_size(EXPORT / 'readme-banner.png') != (2560, 1280):
        raise ValueError('Expected a 2560 × 1280 banner.')
    if png_size(EXPORT / '07-09-organize-panorama.png') != (3240, 1920):
        raise ValueError('Expected a 3240 × 1920 folder panorama.')
    for folder in ('assets', 'docs/assets'):
        copies[REPO / folder / 'banner.png'] = EXPORT / 'readme-banner.png'
    copies[REPO / 'assets/screenshots/07-09-organize-panorama.png'] = EXPORT / '07-09-organize-panorama.png'
    copies[REPO / 'assets/screenshots/all-15-preview.png'] = PREVIEW / 'all-15.png'
    return copies

def publish_assets(check=False):
    copies = publication_files()
    for folder in ('assets/screenshots', 'docs/assets/screenshots',
                   'fastlane/metadata/android/en-US/images/phoneScreenshots'):
        directory = REPO / folder
        if not check:
            directory.mkdir(parents=True, exist_ok=True)
        elif not directory.is_dir():
            raise ValueError(f'Published screenshot directory is missing: {directory}')
        stale = [p for p in directory.iterdir()
                 if p.suffix.lower() in ('.png', '.jpg', '.jpeg', '.webp') and p not in copies]
        if check and stale:
            raise ValueError(f'Obsolete screenshots remain in {directory}')
        for path in stale:
            path.unlink()
    for filename in ('dashboard_home.png', 'floating_edge_panel.png', 'ocr_extracted_links.png',
                     'voice_memo_recording.png', 'folders_screen.png'):
        path = REPO / 'docs/assets' / filename
        if path.exists():
            if check:
                raise ValueError(f'Obsolete website screenshot remains: {path}')
            path.unlink()
    for destination, source in copies.items():
        if check:
            if not destination.is_file() or destination.read_bytes() != source.read_bytes():
                raise ValueError(f'Published asset differs from its export: {destination}')
        else:
            shutil.copyfile(source, destination)
    print(f'{"Verified" if check else "Published"} all {len(PANELS)} panels for README, website and F-Droid, '
          'plus both banners, the panorama and gallery preview.')

def main():
    font_dir=REPO/'app/src/main/res/font'
    (ROOT/'fontconfig.xml').write_text(f'<?xml version="1.0"?><!DOCTYPE fontconfig SYSTEM "fonts.dtd"><fontconfig><include>/etc/fonts/fonts.conf</include><dir>{font_dir}</dir><cachedir>/tmp/cobalt-showcase/font-cache</cachedir></fontconfig>')
    missing=sorted({name for p in PANELS for name in p['captures']} - {p.stem for p in RAW.glob('*.png')})
    if missing:raise SystemExit('Missing captures: '+', '.join(missing))
    for i,p in enumerate(PANELS):
        if p['layout']!='connected':(SOURCE/f'{p["slug"]}.svg').write_text(compose(p,PALETTES[i],i))
    connected_panels();banner()
    manifest=[]
    for i,p in enumerate(PANELS):
        manifest.append({**p,'palette':dict(zip(['surface','container','foreground','secondary'],PALETTES[i])),
                         'size':[1080,1920],
                         'ui':('Unmodified emulator capture shown as a framed results close-up.'
                               if p['layout']=='search' else
                               'Unmodified emulator capture; uniformly scaled and clipped by device bezel.'),
                         'source_package':('com.hanan_bhatti.cobalt.debug' if p['slug']=='13-settings'
                                           else 'com.hanan_bhatti.cobalt'),
                         'raw_sha256':{name:hashlib.sha256((RAW/f'{name}.png').read_bytes()).hexdigest()
                                       for name in p['captures']}})
    (ROOT/'manifest.json').write_text(json.dumps(manifest,indent=2,ensure_ascii=False)+'\n')
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        outputs=list(pool.map(render, sorted(SOURCE.glob('*.svg'))))
    images=[EXPORT/f'{p["slug"]}.png' for p in PANELS]
    subprocess.run(['magick','montage',*[str(p) for p in images],'-thumbnail','270x480','-tile','5x3','-geometry','+12+12','-background','#E6E7E1',str(PREVIEW/'all-15.png')],check=True,capture_output=True)
    subprocess.run(['magick',str(EXPORT/'readme-banner.png'),'-resize','1600x800',str(PREVIEW/'readme-banner.png')],check=True,capture_output=True)
    print(f'Rendered {len(PANELS)} panels, a connected panorama, and README banner.')
    publish_assets()

if __name__=='__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--publish-only', action='store_true', help='Copy existing exports without rendering.')
    mode.add_argument('--check', action='store_true', help='Verify published copies and reject obsolete screenshots.')
    args = parser.parse_args()
    if args.check:
        publish_assets(check=True)
    elif args.publish_only:
        publish_assets()
    else:
        main()
