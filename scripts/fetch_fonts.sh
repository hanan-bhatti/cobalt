#!/usr/bin/env bash
#
# Fetch official FOSS fonts (SIL OFL 1.1) and verify SHA-256 checksums.
# Fonts: Inter, Literata, Source Serif 4, Atkinson Hyperlegible, Lexend, JetBrains Mono
# Pinned commit from google/fonts upstream: 2fe1e8c6b137fb13041a7cfca6fdcd8a910ea4dc
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
FONT_DIR="${ROOT_DIR}/app/src/main/res/font"
LICENSE_DIR="${ROOT_DIR}/app/src/main/assets/licenses"

PINNED_COMMIT="2fe1e8c6b137fb13041a7cfca6fdcd8a910ea4dc"
BASE_URL="https://raw.githubusercontent.com/google/fonts/${PINNED_COMMIT}/ofl"

mkdir -p "${FONT_DIR}" "${LICENSE_DIR}"

declare -A FONTS=(
  ["inter_variable.ttf"]="29160a80ff49ddcab2c97711247e08b1fab27a484a329ce8b813d820dc559031|${BASE_URL}/inter/Inter%5Bopsz%2Cwght%5D.ttf"
  ["literata_variable.ttf"]="b41138c9373112f32abb589cc22e8674b06ed4048b0c513be922bdd26f274440|${BASE_URL}/literata/Literata%5Bopsz%2Cwght%5D.ttf"
  ["source_serif_4_variable.ttf"]="97b2d4da6e3cb494b5a1e66ae176914d852ccabef49e0c02c0df25f3e39aca0b|${BASE_URL}/sourceserif4/SourceSerif4%5Bopsz%2Cwght%5D.ttf"
  ["atkinson_hyperlegible_regular.ttf"]="7fb917c89019896d0b52ee84b7cbb3304c18cb90b19a62f5e32712bd23e97669|${BASE_URL}/atkinsonhyperlegible/AtkinsonHyperlegible-Regular.ttf"
  ["atkinson_hyperlegible_bold.ttf"]="5a3b0c8cc8ca545155150b4512a1fa248298df121c50d6557e651e61fbdab92f|${BASE_URL}/atkinsonhyperlegible/AtkinsonHyperlegible-Bold.ttf"
  ["atkinson_hyperlegible_italic.ttf"]="021beda4d3c6edfc78872e436d74009f9a1bcb294331908fe5747c61d3dcc514|${BASE_URL}/atkinsonhyperlegible/AtkinsonHyperlegible-Italic.ttf"
  ["atkinson_hyperlegible_bold_italic.ttf"]="7a46f1b0ef2f64b61065c1a6b5a11fcb9b019b44062d88ba121a083cc39e86bf|${BASE_URL}/atkinsonhyperlegible/AtkinsonHyperlegible-BoldItalic.ttf"
  ["lexend_variable.ttf"]="3add53e641fbc81da64da4bb254285e2831b52b029527bc0714e2b9610832ee6|${BASE_URL}/lexend/Lexend%5Bwght%5D.ttf"
  ["jetbrains_mono_variable.ttf"]="48715a42ec242c21e9f02692891e147d022299a52e48d5e413e1a942193ffeda|${BASE_URL}/jetbrainsmono/JetBrainsMono%5Bwght%5D.ttf"
)

declare -A LICENSES=(
  ["inter_license.txt"]="5b9321a4298cfeb6b34354164a1c3afc3db114569984c502b9b35d988fd58c57|${BASE_URL}/inter/OFL.txt"
  ["literata_license.txt"]="8742963604cd89dc81437811a850018fc03b2bfad686d7422c8235967c87614e|${BASE_URL}/literata/OFL.txt"
  ["source_serif_4_license.txt"]="5f94c3fd3a23131a417ab5a0c8452de57e70c3cfb9f604d88241f7065ebf9fd9|${BASE_URL}/sourceserif4/OFL.txt"
  ["atkinson_hyperlegible_license.txt"]="f32d22b3908fcad2c86a74000614ec22e6a7f66ea7e867e616026a27aebdc143|${BASE_URL}/atkinsonhyperlegible/OFL.txt"
  ["lexend_license.txt"]="5da8505887d0fa7fe963445fd58852707fda34adfeb65af25c99d152bab285bd|${BASE_URL}/lexend/OFL.txt"
  ["jetbrains_mono_license.txt"]="b2fe5e8987594e9ffd1d2ca52a2f5d73eb8335243893c5d6254b5ad69269591d|${BASE_URL}/jetbrainsmono/OFL.txt"
)

verify_and_save() {
  local target_file="$1"
  local expected_sha="$2"
  local url="$3"

  if [ -f "${target_file}" ]; then
    local current_sha
    current_sha=$(sha256sum "${target_file}" | awk '{print $1}')
    if [ "${current_sha}" = "${expected_sha}" ]; then
      echo "OK (already verified): $(basename "${target_file}")"
      return 0
    fi
  fi

  echo "Downloading $(basename "${target_file}")..."
  curl --fail --show-error --location --silent --output "${target_file}.tmp" "${url}"

  local actual_sha
  actual_sha=$(sha256sum "${target_file}.tmp" | awk '{print $1}')

  if [ "${actual_sha}" != "${expected_sha}" ]; then
    echo "ERROR: Checksum mismatch for $(basename "${target_file}")!" >&2
    echo "Expected: ${expected_sha}" >&2
    echo "Actual:   ${actual_sha}" >&2
    rm -f "${target_file}.tmp"
    return 1
  fi

  mv "${target_file}.tmp" "${target_file}"
  echo "VERIFIED: $(basename "${target_file}")"
}

echo "=== Fetching and verifying font files ==="
for file in "${!FONTS[@]}"; do
  val="${FONTS[$file]}"
  sha="${val%%|*}"
  url="${val##*|}"
  verify_and_save "${FONT_DIR}/${file}" "${sha}" "${url}"
done

echo "=== Fetching and verifying license files ==="
for file in "${!LICENSES[@]}"; do
  val="${LICENSES[$file]}"
  sha="${val%%|*}"
  url="${val##*|}"
  verify_and_save "${LICENSE_DIR}/${file}" "${sha}" "${url}"
done

echo "All fonts and licenses fetched and SHA-256 verified successfully."
