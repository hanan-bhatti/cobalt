# Fonts & Typography in Cobalt

Cobalt is a local-first, privacy-respecting FOSS Android notes and capture app. All fonts are bundled locally within the application package (`app/src/main/res/font/`). No fonts are downloaded dynamically at runtime, and no network requests or external CDNs (such as Google Play Services or Google Fonts API) are ever used.

All fonts bundled in Cobalt are 100% free and open-source software, licensed under the **SIL Open Font License 1.1 (OFL-1.1)**. Full license texts are included in `app/src/main/assets/licenses/` and displayed within the application's Open Source Licenses screen.

---

## Bundled Font Directory

### 1. Inter
- **Font Name**: Inter
- **Upstream Repository**: [https://github.com/rsms/inter](https://github.com/rsms/inter) (Mirrored at `google/fonts`)
- **Pinned Commit**: `2fe1e8c6b137fb13041a7cfca6fdcd8a910ea4dc`
- **SPDX License Identifier**: `OFL-1.1`
- **Copyright**: `Copyright 2020 The Inter Project Authors (https://github.com/rsms/inter)`
- **Format**: Variable font (`opsz`, `wght`: 100–900)
- **File**: `app/src/main/res/font/inter_variable.ttf`
- **File Size**: 876,576 bytes (~856 KB)
- **SHA-256**: `29160a80ff49ddcab2c97711247e08b1fab27a484a329ce8b813d820dc559031`
- **Supported Scripts**: Latin, Cyrillic, Greek, Vietnamese
- **Reserved Font Name**: None

### 2. Literata
- **Font Name**: Literata
- **Upstream Repository**: [https://github.com/googlefonts/literata](https://github.com/googlefonts/literata)
- **Pinned Commit**: `2fe1e8c6b137fb13041a7cfca6fdcd8a910ea4dc`
- **SPDX License Identifier**: `OFL-1.1`
- **Copyright**: `Copyright 2017 The Literata Project Authors (https://github.com/googlefonts/literata)`
- **Format**: Variable font (`opsz`, `wght`: 200–900)
- **File**: `app/src/main/res/font/literata_variable.ttf`
- **File Size**: 955,132 bytes (~933 KB)
- **SHA-256**: `b41138c9373112f32abb589cc22e8674b06ed4048b0c513be922bdd26f274440`
- **Supported Scripts**: Latin, Cyrillic, Greek, Vietnamese
- **Reserved Font Name**: None

### 3. Source Serif 4
- **Font Name**: Source Serif 4
- **Upstream Repository**: [https://github.com/adobe-fonts/source-serif](https://github.com/adobe-fonts/source-serif)
- **Pinned Commit**: `2fe1e8c6b137fb13041a7cfca6fdcd8a910ea4dc`
- **SPDX License Identifier**: `OFL-1.1`
- **Copyright**: `Copyright 2014 The Source Serif 4 Project Authors (https://github.com/adobe-fonts/source-serif)`
- **Format**: Variable font (`opsz`, `wght`: 200–900)
- **File**: `app/src/main/res/font/source_serif_4_variable.ttf`
- **File Size**: 1,209,508 bytes (~1.18 MB)
- **SHA-256**: `97b2d4da6e3cb494b5a1e66ae176914d852ccabef49e0c02c0df25f3e39aca0b`
- **Supported Scripts**: Latin, Cyrillic, Greek
- **Reserved Font Name**: None

### 4. Atkinson Hyperlegible
- **Font Name**: Atkinson Hyperlegible
- **Upstream Repository**: [https://github.com/googlefonts/atkinson-hyperlegible](https://github.com/googlefonts/atkinson-hyperlegible) / Braille Institute
- **Pinned Commit**: `2fe1e8c6b137fb13041a7cfca6fdcd8a910ea4dc`
- **SPDX License Identifier**: `OFL-1.1`
- **Copyright**: `Copyright 2020 Braille Institute of America, Inc.`
- **Format**: Static font family (Regular 400, Bold 700, Italic 400, Bold Italic 700)
- **Files**:
  - `app/src/main/res/font/atkinson_hyperlegible_regular.ttf` (54,348 bytes, SHA-256: `7fb917c89019896d0b52ee84b7cbb3304c18cb90b19a62f5e32712bd23e97669`)
  - `app/src/main/res/font/atkinson_hyperlegible_bold.ttf` (55,256 bytes, SHA-256: `5a3b0c8cc8ca545155150b4512a1fa248298df121c50d6557e651e61fbdab92f`)
  - `app/src/main/res/font/atkinson_hyperlegible_italic.ttf` (54,892 bytes, SHA-256: `021beda4d3c6edfc78872e436d74009f9a1bcb294331908fe5747c61d3dcc514`)
  - `app/src/main/res/font/atkinson_hyperlegible_bold_italic.ttf` (55,288 bytes, SHA-256: `7a46f1b0ef2f64b61065c1a6b5a11fcb9b019b44062d88ba121a083cc39e86bf`)
- **Total Size**: 219,784 bytes (~215 KB)
- **Supported Scripts**: Latin Core, Latin Extended (designed for low-vision legibility)
- **Reserved Font Name**: None
- **Note on Weights**: As a static font, weights snap between 400 (Regular) and 700 (Bold) without synthetic bold interpolation.

### 5. Lexend
- **Font Name**: Lexend
- **Upstream Repository**: [https://github.com/googlefonts/lexend](https://github.com/googlefonts/lexend)
- **Pinned Commit**: `2fe1e8c6b137fb13041a7cfca6fdcd8a910ea4dc`
- **SPDX License Identifier**: `OFL-1.1`
- **Copyright**: `Copyright 2018 The Lexend Project Authors (https://github.com/googlefonts/lexend)`
- **Format**: Variable font (`wght`: 100–900)
- **File**: `app/src/main/res/font/lexend_variable.ttf`
- **File Size**: 175,756 bytes (~172 KB)
- **SHA-256**: `3add53e641fbc81da64da4bb254285e2831b52b029527bc0714e2b9610832ee6`
- **Supported Scripts**: Latin, Vietnamese
- **Reserved Font Name**: "RevReading Lexend" (preserved unmodified)

### 6. JetBrains Mono
- **Font Name**: JetBrains Mono
- **Upstream Repository**: [https://github.com/JetBrains/JetBrainsMono](https://github.com/JetBrains/JetBrainsMono)
- **Pinned Commit**: `2fe1e8c6b137fb13041a7cfca6fdcd8a910ea4dc`
- **SPDX License Identifier**: `OFL-1.1`
- **Copyright**: `Copyright 2020 The JetBrains Mono Project Authors (https://github.com/JetBrains/JetBrainsMono)`
- **Format**: Variable monospace font (`wght`: 100–800)
- **File**: `app/src/main/res/font/jetbrains_mono_variable.ttf`
- **File Size**: 187,208 bytes (~183 KB)
- **SHA-256**: `48715a42ec242c21e9f02692891e147d022299a52e48d5e413e1a942193ffeda`
- **Supported Scripts**: Latin, Cyrillic, Greek, Symbols, Programming Ligatures
- **Reserved Font Name**: None

---

## Glyph Coverage & System Fallback Notes

The bundled fonts provide broad typography coverage for Latin, Cyrillic, and Greek alphabets:
- **Latin & Latin Extended**: Fully supported by Inter, Literata, Source Serif 4, Atkinson Hyperlegible, Lexend, and JetBrains Mono.
- **Cyrillic**: Supported natively by Inter, Literata, Source Serif 4, and JetBrains Mono.
- **Greek**: Supported natively by Inter, Literata, Source Serif 4, and JetBrains Mono.
- **Right-to-Left (Urdu & Arabic)**: Neither Inter, Literata, Source Serif 4, Atkinson Hyperlegible, Lexend, nor JetBrains Mono contain Arabic/Nastaliq/Urdu glyphs. Characters in Urdu (Perso-Arabic script) and Arabic automatically fall back seamlessly to Android's built-in system fonts (e.g. Noto Sans Arabic / Noto Nastaliq Urdu). The live preview includes an Urdu line to verify that bidirectional text direction and fallback glyphs render correctly alongside Latin fonts.
- **East Asian (CJK: Chinese, Japanese, Korean)**: Automatically falls back to Android platform system CJK fonts (e.g. Noto Sans CJK / DroidSansFallback).

---

## APK Size Impact

All font files are compressed inside the APK by Android's asset packaging tool (`aapt2`). Below are the exact measured byte counts before and after bundling fonts:

| Asset / Artifact | Size (Bytes) | Size (MB) |
|---|---|---|
| Uncompressed Font Assets (9 TTFs) | 3,623,964 B | ~3.46 MB |
| Baseline Debug APK | 33,591,331 B | ~32.03 MB |
| **New Debug APK** | **34,704,819 B** | **~33.09 MB** |
| *Debug APK Delta* | *+1,113,488 B* | *~1.06 MB* |
| Baseline Release APK | 6,162,775 B | ~5.88 MB |
| **New Release APK (Minified/Shrunk)** | **8,062,583 B** | **~7.69 MB** |
| *Release APK Delta* | *+1,899,808 B* | *~1.81 MB* |

---

## How to Update Fonts

To refresh or update the pinned fonts to a newer upstream release:

1. Update the `PINNED_COMMIT` hash in `scripts/fetch_fonts.sh` to the desired upstream git commit.
2. Execute the fetch script:
   ```bash
   ./scripts/fetch_fonts.sh
   ```
3. If hashes have changed upstream, update the expected SHA-256 hashes in `scripts/fetch_fonts.sh` and in `FONTS.md`.
4. Verify the application builds locally with no network access:
   ```bash
   ./gradlew assembleDebug --offline
   ```
5. Commit the updated font files, `scripts/fetch_fonts.sh`, and `FONTS.md`.
