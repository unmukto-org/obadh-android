#!/usr/bin/env python3
"""Fail engine/model updates loudly when the bundled derived glide asset is stale."""
import hashlib
import json
from pathlib import Path
import re
import struct

ROOT = Path(__file__).resolve().parent.parent


def check():
    directory = ROOT / 'app/src/main/assets/ObadhGesture'
    metadata = json.loads((directory / 'metadata.json').read_text())
    dependency = (ROOT / 'rust/obadh-jni/Cargo.toml').read_text()
    engine = re.search(r'obadh_engine = \{ version = "=([\d.]+)"', dependency).group(1)
    assert metadata['engine'] == engine and metadata['abi'] == 2, 'Regenerate the glide model for this engine'
    data = (directory / 'main.dict').read_bytes()
    assert len(data) == metadata['bytes'] and 0 < len(data) <= 4 * 1024 * 1024, 'Glide model size mismatch'
    assert hashlib.sha256(data).hexdigest() == metadata['sha256'], 'Glide model checksum mismatch'
    magic, version, flags, header = struct.unpack_from('>IHHI', data)
    assert magic == 0x9bc13afe and version == metadata['format'] == 202 and flags == 0 and 12 < header < len(data)
    assert 0 < metadata['paths'] <= metadata['spellings'] and metadata['words'] > 0
    source = ROOT / 'app/src/main/assets/ObadhModels/autocorrect/bn.fst'
    assert source.is_file(), 'Sync Obadh models before checking derived glide provenance'
    assert hashlib.sha256(source.read_bytes()).hexdigest() == metadata['source_fst_sha256'], 'Source model changed; regenerate Bangla glide'
    print(f'PASS Bangla glide provenance: engine {engine}, {metadata["words"]:,} words, {len(data):,} bytes')


if __name__ == '__main__':
    check()
