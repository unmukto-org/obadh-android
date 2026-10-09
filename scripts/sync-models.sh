#!/usr/bin/env bash
# Copy the engine artifacts the keyboard needs from the iOS repo's bundle so both
# platforms ship byte-identical models. The engine's loanword release is pinned
# independently so CI cannot silently reuse the iOS repository's older artifact.
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
src="${OBADH_MODELS_SRC:-$here/../obadh-ios/Resources/ObadhModels}"
dst="$here/app/src/main/assets/ObadhModels"
mkdir -p "$dst/autocorrect" "$dst/autosuggest" "$dst/emoji"
cp "$src/autocorrect/bn.fst" "$dst/autocorrect/"
# obadh_engine 0.9.5, tracked release artifact (93 KB). Reuse matching local data;
# download only this file when iOS has not yet committed its engine update.
loanword_commit=3888a0b5c6809fccc0478c182b2efd5de2f8d709
loanword_sha=2d92cda223dfa405ff9f1e8e3f72051cf99be510b94fee5702395f0dfd20bf4a
sha256() { python3 -c 'import hashlib,sys; print(hashlib.sha256(open(sys.argv[1], "rb").read()).hexdigest())' "$1"; }
loanword_src="$src/autocorrect/en_bn_loanwords.fst"
loanword_dst="$dst/autocorrect/en_bn_loanwords.fst"
if [ -f "$loanword_src" ] && [ "$(sha256 "$loanword_src")" = "$loanword_sha" ]; then
  cp "$loanword_src" "$loanword_dst"
elif [ -f "$loanword_dst" ] && [ "$(sha256 "$loanword_dst")" = "$loanword_sha" ]; then
  echo "reusing verified engine 0.9.5 loanword model"
else
  loanword_tmp="$(mktemp "$dst/autocorrect/.loanwords.XXXXXX")"
  trap 'rm -f "$loanword_tmp"' EXIT
  curl --fail --location --silent --show-error --max-time 60 --retry 2 \
    "https://raw.githubusercontent.com/unmukto-org/obadh_engine/$loanword_commit/docs/assets/autocorrect/en_bn_loanwords.fst" \
    -o "$loanword_tmp"
  [ "$(sha256 "$loanword_tmp")" = "$loanword_sha" ] || { echo "loanword release checksum mismatch" >&2; exit 1; }
  mv "$loanword_tmp" "$loanword_dst"
  trap - EXIT
fi
cp "$src/autosuggest/autosuggest-ngram-c64.bin" "$dst/autosuggest/"
cp "$src/emoji/emoji.bin" "$src/emoji/emoji-bn.bin" "$src/emoji/emoji-bn-search.bin" "$dst/emoji/"
echo "models synced to $dst"
