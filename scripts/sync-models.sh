#!/usr/bin/env bash
# Copy the engine artifacts the keyboard needs from the iOS repo's bundle so both
# platforms ship byte-identical models (pinned by engine fingerprints in tests).
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
src="${OBADH_MODELS_SRC:-$here/../obadh-ios/Resources/ObadhModels}"
dst="$here/app/src/main/assets/ObadhModels"
mkdir -p "$dst/autocorrect" "$dst/autosuggest" "$dst/emoji"
cp "$src/autocorrect/bn.fst" "$src/autocorrect/en_bn_loanwords.fst" "$dst/autocorrect/"
cp "$src/autosuggest/autosuggest-ngram-c64.bin" "$dst/autosuggest/"
cp "$src/emoji/emoji.bin" "$src/emoji/emoji-bn.bin" "$src/emoji/emoji-bn-search.bin" "$dst/emoji/"
echo "models synced to $dst"
