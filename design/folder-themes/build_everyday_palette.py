"""Build the Cobalt Everyday 20 proposal and audit its displayed sRGB tokens.

No app or Figma colors are changed. Perceptual distances describe this candidate
set, not a universal recognition threshold or an accessibility certification.
"""

from __future__ import annotations

import csv
import html
import itertools
import json
import math
import re
import shutil
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent

# name, family, everyday cue, suggested association, example folder,
# light L/C, dark L/C, OKLCH hue. These are deliberate individual choices:
# the theme pairs share hue, while their lightness/chroma retain separation.
CHOICES = [
    ("Cherry", "Warm", "Ripe cherries", "Attention", "Priorities", .550, .180, .690, .195, 20),
    ("Coral", "Warm", "Coral and peach sunsets", "Warmth", "People", .633, .118, .840, .090, 40),
    ("Tangerine", "Warm", "Orange citrus peel", "Energy", "Projects", .570, .150, .770, .185, 58),
    ("Marigold", "Warm", "Golden marigold petals", "Confidence", "Plans", .530, .110, .730, .145, 86),
    ("Sunflower", "Warm", "Sunflower petals", "Optimism", "Ideas", .625, .140, .900, .140, 105),
    ("Lime", "Nature", "Fresh lime peel", "Freshness", "Habits", .550, .140, .830, .195, 126),
    ("Leaf", "Nature", "New green leaves", "Growth", "Health", .600, .140, .730, .170, 151),
    ("Forest", "Nature", "Evergreen trees", "Stability", "Home", .420, .074, .620, .075, 159),
    ("Teal", "Nature", "Deep coastal water", "Balance", "Wellbeing", .520, .095, .810, .135, 183),
    ("Cyan", "Nature", "Clear glacial water", "Clarity", "Learning", .600, .095, .880, .095, 215),
    ("Sky", "Cool", "Open blue sky", "Openness", "Travel", .625, .093, .810, .085, 260),
    ("Cobalt", "Cool", "Cobalt blue ceramics", "Focus", "Work", .460, .190, .640, .210, 264),
    ("Violet", "Cool", "Violet flowers", "Imagination", "Creativity", .545, .180, .715, .180, 300),
    ("Orchid", "Cool", "Orchid petals", "Expression", "Inspiration", .600, .182, .795, .170, 332),
    ("Plum", "Cool", "Dark plum skin", "Reflection", "Research", .440, .110, .620, .117, 326),
    ("Rose", "Earth & personal", "Soft rose petals", "Care", "Personal", .620, .095, .900, .065, 358),
    ("Clay", "Earth & personal", "Fired terracotta", "Making", "Craft", .475, .104, .675, .107, 48),
    ("Cocoa", "Earth & personal", "Roasted cocoa beans", "Comfort", "Journal", .390, .048, .750, .050, 64),
    ("Sand", "Earth & personal", "Warm beach sand", "Ease", "Memories", .620, .050, .880, .065, 86),
    ("Slate", "Earth & personal", "Blue-gray stone", "Order", "Admin", .525, .038, .670, .045, 245),
]

SOURCES = [
    {
        "title": "Jonauskaite et al. — Universal Patterns in Color-Emotion Associations",
        "url": "https://doi.org/10.1177/0956797620948810",
        "use": "Emotional associations show broad similarities and cultural variation. The proposed individual meanings are design suggestions, not measured psychological effects.",
    },
    {
        "title": "IBM Carbon — Color palettes",
        "url": "https://carbondesignsystem.com/data-visualization/color-palettes/",
        "use": "Categorical colors should differentiate discrete items; inspect the sequence and neighboring colors together. This is an original palette, not an IBM palette.",
    },
    {
        "title": "Material Color Utilities",
        "url": "https://github.com/material-foundation/material-color-utilities",
        "use": "Use perceptual color models and explicit color roles. This proposal uses OKLCH, not HCT or wallpaper-derived dynamic color.",
    },
    {
        "title": "W3C — Understanding Use of Color",
        "url": "https://www.w3.org/WAI/WCAG22/Understanding/use-of-color.html",
        "use": "Folder names and icons must also communicate identity; color is an additional cue.",
    },
]


def parse(hex_color):
    return [int(hex_color[i:i + 2], 16) for i in (1, 3, 5)]


def hex_rgb(rgb):
    return "#" + "".join(f"{min(255, max(0, math.floor(v + .5))):02X}" for v in rgb)


def linear(channel):
    value = channel / 255
    return value / 12.92 if value <= .04045 else ((value + .055) / 1.055) ** 2.4


def lab(rgb):
    r, g, b = map(linear, rgb)
    l = (.4122214708 * r + .5363325363 * g + .0514459929 * b) ** (1 / 3)
    m = (.2119034982 * r + .6806995451 * g + .1073969566 * b) ** (1 / 3)
    s = (.0883024619 * r + .2817188376 * g + .6299787005 * b) ** (1 / 3)
    return [.2104542553 * l + .7936177850 * m - .0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + .4505937099 * s,
            .0259040371 * l + .7827717662 * m - .8086757660 * s]


def color(L, C, hue):
    hue = math.radians(hue)
    for _ in range(120):
        a, b = C * math.cos(hue), C * math.sin(hue)
        l = (L + .3963377774 * a + .2158037573 * b) ** 3
        m = (L - .1055613458 * a - .0638541728 * b) ** 3
        s = (L - .0894841775 * a - 1.291485548 * b) ** 3
        rgb = [4.0767416621 * l - 3.3077115913 * m + .2309699292 * s,
               -1.2684380046 * l + 2.6097574011 * m - .3413193965 * s,
               -.0041960863 * l - .7034186147 * m + 1.707614701 * s]
        if all(0 <= v <= 1 for v in rgb):
            return hex_rgb([255 * (12.92 * v if v <= .0031308 else 1.055 * v ** (1 / 2.4) - .055) for v in rgb])
        C *= .97
    raise ValueError("Cannot gamut-map the selected color")


def mix(front, back, opacity):
    return hex_rgb([a * opacity + b * (1 - opacity) for a, b in zip(parse(front), parse(back))])


def luminance(hex_color):
    return sum(v * weight for v, weight in zip(map(linear, parse(hex_color)), [.2126, .7152, .0722]))


def contrast(front, back):
    a, b = luminance(front), luminance(back)
    return (max(a, b) + .05) / (min(a, b) + .05)


def roles(hue, chroma, accent, theme, pinned=False):
    if theme == "light":
        return {
            "background": "#FDFBF9", "accent": accent,
            "card": color(.966 if pinned else .984, min(chroma * .16, .016 if pinned else .009), hue),
            "iconBox": color(.955, min(chroma * .32, .030), hue),
            "border": color(.855 if pinned else .895, min(chroma * .27, .032), hue),
            "label": "#171717", "count": "#6C615B", "control": "#75675F",
        }
    return {
        "background": "#1A120E", "accent": accent,
        "card": color(.277 if pinned else .250, min(chroma * .28, .037), hue),
        "iconBox": color(.303, min(chroma * .45, .060), hue),
        "border": color(.425 if pinned else .378, min(chroma * .40, .052), hue),
        "label": "#F0DFD8", "count": "#CDBEB6", "control": "#B9AAA2",
    }


def pair_audit(palette, theme):
    data = [(p["name"], lab(parse(p[theme]))) for p in palette]
    pairs = sorted([
        {"pair": [a[0], b[0]], "deltaEOKx100": round(math.dist(a[1], b[1]) * 100, 3)}
        for a, b in itertools.combinations(data, 2)
    ], key=lambda row: row["deltaEOKx100"])
    return {"minimum": pairs[0], "closestFive": pairs[:5]}


def original_roles(accent, theme, pinned):
    light = theme == "light"
    bg, text = ("#FDFBF9", "#171717") if light else ("#1A120E", "#F0DFD8")
    card = mix(accent, bg, (.12 if light else .22) if pinned else (.08 if light else .16))
    return {
        "background": bg, "accent": accent, "card": card,
        "iconBox": mix(accent, card, .15 if light else .25),
        "border": mix(accent, card, (.40 if light else .55) if pinned else (.20 if light else .35)),
        "label": text, "count": mix(text if pinned else accent, card, .60 if pinned else .80),
        "control": mix(accent if pinned else ("#775847" if light else "#E6BEAB"), card, .60),
    }


def app_icons():
    android = "{http://schemas.android.com/apk/res/android}"
    project = ROOT.parents[1]
    symbols = []
    for name in ["folder", "pin", "settings"]:
        vector = ET.parse(project / f"app/src/main/res/drawable/ic_custom_{name}.xml").getroot()
        paths = []
        for path in vector.iter("path"):
            fill = path.get(android + "fillColor", "#00000000")
            attrs = {
                "d": path.get(android + "pathData"),
                "fill": "none" if fill == "#00000000" else "currentColor",
            }
            if path.get(android + "strokeColor"):
                attrs["stroke"] = "currentColor"
            for source, target in [("strokeWidth", "stroke-width"), ("strokeLineCap", "stroke-linecap"), ("strokeLineJoin", "stroke-linejoin")]:
                if path.get(android + source):
                    attrs[target] = path.get(android + source)
            if path.get(android + "fillType") == "evenOdd":
                attrs["fill-rule"] = "evenodd"
            paths.append("<path " + " ".join(f'{key}="{html.escape(value, quote=True)}"' for key, value in attrs.items()) + "/>")
        symbols.append(f'<symbol id="cobalt-demo-{name}" viewBox="0 0 24 24">' + "".join(paths) + "</symbol>")
    return "\n".join(symbols)


def main():
    palette = []
    for index, (name, family, cue, association, example, light_L, light_C, dark_L, dark_C, hue) in enumerate(CHOICES, 1):
        slug = name.lower()
        light, dark = color(light_L, light_C, hue), color(dark_L, dark_C, hue)
        p = {
            "id": f"F{index:02}", "slug": slug, "token": f"folder/{slug}",
            "name": name, "family": family, "everydayCue": cue,
            "association": association, "exampleFolder": example,
            "light": light, "dark": dark,
            "targets": {"hue": hue, "light": {"L": light_L, "C": light_C}, "dark": {"L": dark_L, "C": dark_C}},
            "roles": {},
        }
        for theme, accent, C in [("light", light, light_C), ("dark", dark, dark_C)]:
            p["roles"][theme] = {
                "list": roles(hue, C, accent, theme),
                "pinned": roles(hue, C, accent, theme, pinned=True),
            }
        palette.append(p)

    source = (ROOT / "folder-theme-comparison.html").read_text()
    rows = re.findall(r"\['([a-z-]+)', '([^']+)', '(#[A-F0-9]{6})', '(#[A-F0-9]{6})', '(#[A-F0-9]{6})'\]", source)
    original = [{"id": f"O{i:02}", "slug": r[0], "token": f"folder/{r[0]}", "name": r[1],
                 "light": r[2], "dark": r[3], "family": "Original palette", "exampleFolder": "Research",
                 "everydayCue": "Original app preset", "association": "Existing choice", "roles": {
                     theme: {layout: original_roles(r[2] if theme == "light" else r[3], theme, layout == "pinned")
                             for layout in ["list", "pinned"]} for theme in ["light", "dark"]
                 }} for i, r in enumerate(rows, 1)]
    if len(original) != 20:
        raise ValueError("Original palette extraction must return 20 colors")

    checks = []
    for p in palette:
        for theme, layouts in p["roles"].items():
            for layout, r in layouts.items():
                check = {"color": p["name"], "theme": theme, "layout": layout,
                         "icon": contrast(r["accent"], r["iconBox"] if layout == "list" else r["card"]),
                         "label": contrast(r["label"], r["card"]),
                         "count": contrast(r["count"], r["card"])}
                if check["icon"] < 3 or min(check["label"], check["count"]) < 4.5:
                    raise ValueError(f"Contrast target failed: {check}")
                checks.append(check)

    audit = {
        "metric": "Euclidean OKLab distance times 100; comparative design diagnostic only",
        "original": {theme: pair_audit(original, theme) for theme in ["light", "dark"]},
        "everyday": {theme: pair_audit(palette, theme) for theme in ["light", "dark"]},
        "contrast": {"states": len(checks), "minimumIcon": min(c["icon"] for c in checks),
                     "minimumLabel": min(c["label"] for c in checks), "minimumCount": min(c["count"] for c in checks)},
        "limitations": "Not user recognition testing. Twenty colors cannot be assumed distinct for every viewer. Labels and icons remain required. Theme-role contrast applies to the authored backgrounds, not all wallpaper-derived themes.",
    }
    manifest = {"name": "Cobalt Everyday 20", "version": 2, "status": "proposal", "colors": palette, "original": original, "sources": SOURCES, "audit": audit}
    (ROOT / "everyday-palette.json").write_text(json.dumps(manifest, indent=2) + "\n")
    (ROOT / "everyday-audit.json").write_text(json.dumps(audit, indent=2) + "\n")
    with (ROOT / "everyday-palette.csv").open("w", newline="") as stream:
        writer = csv.writer(stream)
        writer.writerow(["ID", "Token", "Name", "Group", "Light icon", "Dark icon", "Everyday cue", "Suggested association", "Example folder"])
        for p in palette:
            writer.writerow([p["id"], p["token"], p["name"], p["family"], p["light"], p["dark"], p["everydayCue"], p["association"], p["exampleFolder"]])
    notes = [
        "# Cobalt Everyday 20", "", "Palette proposal · 2026-11-13", "",
        "Twenty folder color choices organized into four groups of five. Familiar objects provide memorable names; each group mixes brightness and saturation so nearby hues retain different identities. Light and dark accents share their hue, with individually selected lightness and chroma.", "",
        "The suggested associations and example folders below are optional design cues. Color-emotion research finds broad shared patterns alongside cultural and language differences; it does not establish a universal meaning for these exact swatches. [Jonauskaite et al., 2020](https://doi.org/10.1177/0956797620948810)", "",
        "| ID | Color | Group | Light icon | Dark icon | Suggested association | Example folder |",
        "| --- | --- | --- | --- | --- | --- | --- |",
    ]
    for p in palette:
        notes.append(f'| {p["id"]} | {p["name"]} | {p["family"]} | `{p["light"]}` | `{p["dark"]}` | {p["association"]} | {p["exampleFolder"]} |')
    notes.extend([
        "", "## Applying the palette", "",
        "Use `folder/<name>` as the stable token identity and select its light/dark role values from `everyday-palette.json`. Each theme includes separate list and pinned card roles for icon accent, card, icon box, border, folder label, item count, and settings icon. Counts remain neutral text. Solid color-picker chips show the accent at full strength; their icon ink is selected independently for contrast.", "",
        "Folder colors are personalization choices, separate from error, warning, and success status tokens. Keep folder names and icons visible: twenty colors are not a substitute for readable identity. [W3C Use of Color](https://www.w3.org/WAI/WCAG22/Understanding/use-of-color.html)", "",
        "The set is reviewed as a categorical palette, using hue, lightness, and chroma differences across the whole set. This follows the differentiation principle in [IBM Carbon's categorical palette guidance](https://carbondesignsystem.com/data-visualization/color-palettes/); these swatches are an original Cobalt proposal. The theme roles follow the separation of color roles and contrast decisions described in [Material Color Utilities](https://github.com/material-foundation/material-color-utilities), using OKLCH for this implementation.", "",
        "## Design checks", "",
        f'The closest accent pair has OKLab distance ×100 of {audit["everyday"]["light"]["minimum"]["deltaEOKx100"]:.2f} in light and {audit["everyday"]["dark"]["minimum"]["deltaEOKx100"]:.2f} in dark. The original set measured {audit["original"]["light"]["minimum"]["deltaEOKx100"]:.2f} and {audit["original"]["dark"]["minimum"]["deltaEOKx100"]:.2f}, respectively. These distances are comparative diagnostics, not recognition rates, WCAG thresholds, or evidence that every viewer can distinguish all twenty colors.', "",
        f'All {len(checks)} color/theme/card combinations meet the 3:1 icon and 4.5:1 label/count targets against their actual opaque sRGB surfaces. Lowest icon contrast: {audit["contrast"]["minimumIcon"]:.2f}:1. Lowest count contrast: {audit["contrast"]["minimumCount"]:.2f}:1. The checks use the authored backgrounds `#FDFBF9` / `#1A120E`; wallpaper-derived backgrounds require their own verification.', "",
        "See `everyday-audit.json` for the nearest pairs, and `previews/everyday-verification.json` for browser verification. User recognition testing is still the right way to validate how quickly people can identify their chosen folders.", "",
    ])
    (ROOT / "EVERYDAY-PALETTE.md").write_text("\n".join(notes))
    template = ROOT / "everyday-palette.template.html"
    if template.exists():
        document = template.read_text()
        document = document.replace("<!--__PALETTE_DATA__-->", json.dumps(manifest, separators=(",", ":")).replace("<", "\\u003c"))
        document = document.replace("<!--__APP_ICONS__-->", app_icons())
        if "<!--__PALETTE_DATA__-->" in document or "<!--__APP_ICONS__-->" in document:
            raise ValueError("The demo still has an unresolved placeholder")
        destination = ROOT / "index.html"
        if destination.exists() and not (ROOT / "original.html").exists():
            shutil.copy2(destination, ROOT / "original.html")
        stage = ROOT / "index.html.new"
        stage.write_text(document)
        stage.replace(destination)
    print(json.dumps({"minimumDistance": {theme: audit["everyday"][theme]["minimum"] for theme in ["light", "dark"]}, "contrast": audit["contrast"]}, indent=2))
    for p in palette:
        print(f'{p["id"]} {p["name"]:12} {p["light"]} / {p["dark"]}')


if __name__ == "__main__":
    main()
