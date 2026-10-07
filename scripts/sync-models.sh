#!/usr/bin/env bash
# Copy the engine artifacts the keyboard needs from the iOS repo's bundle so both
# platforms ship byte-identical models (pinned by engine fingerprints in tests).
# Emoji indexes are not bundled yet (emoji is a later phase on Android).
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
src="${OBADH_MODELS_SRC:-$here/../obadh-ios/Resources/ObadhModels}"
dst="$here/app/src/main/assets/ObadhModels"
mkdir -p "$dst/autocorrect" "$dst/autosuggest"
cp "$src/autocorrect/bn.fst" "$src/autocorrect/en_bn_loanwords.fst" "$dst/autocorrect/"
cp "$src/autosuggest/autosuggest-ngram-c64.bin" "$dst/autosuggest/"
echo "models synced to $dst"
