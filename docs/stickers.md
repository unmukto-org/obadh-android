# Stickers

Obadh owns the picker and input policy. HeliBoard sees only two optional extension members (`stickersSupported`, `showStickers`) and one host transaction (`prepareObadhContentInput`). This preserves literal composition, cancels stale candidate work and resets editing caches before Android rich-content insertion. Upstream code does not depend on the sticker catalog or downloader.

## Artwork and distribution

[obadh-stickers](https://github.com/unmukto-org/obadh-stickers) is a separate public repository. Blobfox (23) and BunHD (35) are included. Additional Volpeon reactions and eight searchable OpenMoji categories are optional. Volpeon artwork is Apache-2.0; OpenMoji graphics and metadata remain CC BY-SA 4.0. Complete license texts are available offline from About. Artist notices, modifications, source URLs, source hashes and per-artwork OpenMoji authors are preserved in the collection repository. Obadh/Unmukto and Obadh Engine remain the primary About content, followed by acknowledgments.

The app pins an immutable Git revision, archive sizes/hashes, image hashes and local keyword index. Downloaded art is not executable. The app never trusts a mutable remote catalog or arbitrary import. Rebuild/version artwork in the collection repository, review licenses, then refresh the app's local catalog/bundled assets with the new revision. No runtime API key or catalog server is needed. Animated sources become first-frame static PNG stickers, preserving transparency; no animations run while typing.

## Input and performance

The picker is nonfocusable, overlays the keyboard body and retains the original input connection. Tabs, recent and favorites are local. Landscape combines search and pack tabs into one row. Explicit search uses a private transient editor with native Bangla/English typing; queries are debounced and matched against names/keywords on a separate worker, never sent to the receiving app or a network service. Only downloaded packs produce sendable results; the local index can indicate additional matching packs.

Insertion negotiates `image/png`, `image/*` or `*/*`, uses scoped FileProvider URIs with temporary Android read grants, and checks input session/editor identity after file IO. Password fields block stickers. Unsupported editors can browse with disabled image cells and a clear explanation; rejected commits retain the picker and show feedback. Incognito stores neither recent stickers nor new favorites. Search selections return only to the original editor, within a bounded time, and cannot be delivered to another app. Actual receiving apps decide whether a PNG appears as a sticker or an image attachment.

No model/typing executor owns sticker work. Catalog/search/files use an independent worker; only visible thumbnails use two low-priority workers, a bounded queue and 4 MiB LRU bitmap cache. Cells are recycled; stale/detached views are checked before decoding/delivery. Hiding/rotating invalidates the panel and pending insertion. Trim-memory drops thumbnails. Prepared immutable shared images are limited to 256 files, expire after 72 hours, and never expose other private directories.

## Background downloads

Explicit user downloads use unique WorkManager jobs with connected-network constraints, progress, cancel, retry/backoff and process-death recovery. Files are private. Verification checks pinned archive size/SHA-256, exact archive membership, duplicate entries, traversal, expanded/per-file limits, PNG signatures/dimensions/animation chunks and image hashes. Installation is atomic after verification; cancellation cannot publish partial packs. Pack removal frees downloaded files and leaves the bundled packs intact. No network, archive IO or bitmap decode runs on the IME input thread.

## Validation

`StickerSafetyTest` covers MIME negotiation, path identifiers, PNG bounds/truncation/animation rejection, bounded reads, good/bad/incomplete archives, zip traversal, size bombs, cancellation and English/Bangla/roman searches. `scripts/test-native-stickers.py` exercises real framework rich-content receiving/rejection, actual picker/search touches, composition and sensitive-field behavior. The receiving fixture is debug-only. Production contains no testing receiver.
