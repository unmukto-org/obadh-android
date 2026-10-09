# GIFs, stickers and local recents

Obadh's optional online GIFs and stickers use KLIPY. Offline sticker packs, their bundled images, local artwork search and pack downloads have been removed. The separate [obadh-stickers repository](https://github.com/unmukto-org/obadh-stickers) remains as historical artwork work; Android no longer ships or downloads its collections. Upgrade cleanup removes previously installed packs, old share files and sticker history without touching the swipe-library download.

Emoji remains on the bottom-row comma/emoji key. The toolbar has separate folded-sticker and GIF wordmark controls. Both use one app-owned implementation, isolated from the Bangla C ABI and upstream Latin typing pipeline. Upstream sees only optional media capability and navigation members in `ObadhExtension`; no KLIPY transport, credentials or cache is part of the HeliBoard overlay. Secondary toolbar controls move into the existing tools surface on narrow keyboards, preserving 48 dp targets.

## Privacy and navigation

The first explicit media action asks the user to enable online GIFs and stickers. The user can disable them in **GIFs & stickers** settings. Explicit search terms and accepted-share events go to KLIPY with a random installation identifier; normal typing, recipient package/field information, surrounding text, clipboard and dictionary data are never included. There is no advertising SDK or advertising identifier. The partner platform has monetization disabled, and ad entries are excluded.

The search editor is a private translucent activity with the keyboard's active colors. Searches debounce for 600 ms, cancel stale requests and support English and Bangla. The header has one compact search field and a recents clock toggle; typing searches the provider, tapping the selected clock returns to trending. When references exist, the picker opens cached recents first, avoiding an unnecessary network request. No separate text-pill row or extra media menu is needed. GIF tiles preserve artwork aspect ratios in a rounded native masonry grid; sticker tiles use a regular grid. Titles remain available to accessibility and long-press details. Creator/source credits are displayed when supplied. The placeholder reads **Search KLIPY** and the picker displays the official provider attribution mark. Loading, empty results, retry, cancel, offline, rate limiting, authorization failure and server failure have explicit states.

A session belongs to the original editor and expires after ten minutes. Configuration changes retain query/results/scroll state in process memory. Closing the picker restores the original editor without pasting search text. Leaving the activity cancels work; process death invalidates the session. Passwords, incognito and the private search editor disable media controls. Only recipients advertising animated GIF/WebP-compatible MIME types enable sending. The host decides whether a sticker is displayed as a sticker or an image attachment; Obadh does not paste a URL or substitute a still image.

## Recents and storage

Recent references are saved only after the recipient accepts the animation. Each category holds at most 20 references, deduplicated and ordered by most recent use. These contain the slug, title, attribution and preview metadata, not the full media or search query. The thumbnail cache holds at most 64 files / 6 MiB, with a 7-day idle age; recently sent previews take priority over disposable search previews. It contains only provider thumbnails up to 256 px / 384 KiB, checked before decoding. **Clear local recents & thumbnails** removes both and cancels outstanding cache-warming work. Disabling online media cancels its background requests. This does not delete provider records.

Cached thumbnails can remain visible offline; sending still requires a fresh provider request. A recent selection resolves its current metadata and downloads the chosen animation again. [KLIPY's API terms](https://klipy.com/support/api-terms) allow caching search-result thumbnails but prohibit retaining content as an offline collection. Originals never enter the thumbnail cache or private files. Android content delivery uses a temporary in-memory buffer, limited to 16 MiB total and expiring after 90 seconds, accessed through a non-exported provider with scoped read grants. Buffer lifetime is for recipient delivery, never reuse from recents.

## Bounds and responsiveness

HTTP has three low-priority workers, a bounded queue, cancellation/disconnect and connect/read timeouts. Idle workers are released. JSON parsing, media validation, file IO and image decoding run away from the input thread. There is no WebView, video player or additional image/network SDK. JSON is capped at 1 MiB, selected files at 8 MiB, canvas dimensions at 1024 px and animation frames at 300 (120 for thumbnails). GIF/WebP containers, frame bounds, HTTPS host and actual format are checked, rather than trusting format filtering. Only `static.klipy.com` image URLs are accepted; redirects are disabled.

Two decoder slots service recycled thumbnails. At most twelve visible previews animate; hidden/detached views stop and cancel their jobs. Reduced motion uses still GIF previews. Native ImageDecoder handles animation on Android 9+, with single-frame native Skia previews on Android 8/8.1 to limit memory; sends retain their animation. Paging is explicit on scrolling and capped at 120 loaded items. Selected downloads show progress and can be canceled. Explicit WebP recipients prefer compact WebP; wildcard recipients prefer broadly supported GIF. Literal Bangla/English composition is committed safely before media insertion.

## Build configuration and attribution

No key is stored in Git. An unconfigured build hides media toolbar controls and explains availability in settings. CI can compile and test without a provider key. A key is provided using `-PklipyKeyFile=/private/path` or `KLIPY_APP_KEY`, with an explicit `-PklipyTesting` or `-PklipyProduction` declaration. Never print credential-bearing request URLs or upload a configured testing APK to public CI artifacts.

For local emulator verification:

```sh
source scripts/dev-env.sh
./gradlew :app:assembleDebug -PklipyTesting -PklipyKeyFile="$HOME/.config/obadh/klipy-test-key"
# Optimized, non-debuggable local test build; not store signing or production approval:
./gradlew :app:assembleRelease -PdebugSign -PklipyTesting -PklipyKeyFile="$HOME/.config/obadh/klipy-test-key"
```

The existing Obadh platform/key is **TESTING**, monetization **OFF**. Production approval and a production key are still required. A testing release requires local debug signing. See [provider evaluation](klipy-evaluation.md) and [brand asset notice](third-party/klipy.md). Obadh and Obadh Engine remain the primary About content, followed by foundation and optional-provider acknowledgments.

`MediaSafetyTest` verifies malformed/truncated containers, frame/canvas escape, MIME negotiation, host validation, payload budgets and cancellation. `scripts/test-native-media.py` uses real native touches and a debug-only receiving editor. Production excludes all debug probe components. Emulator verification is useful evidence, not certification of every receiving app, physical device or provider service state.
