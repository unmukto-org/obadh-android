# KLIPY evaluation — 2026-10-09

## Outcome

An Obadh Android platform and `Obadh-Android` API key were created in the user's KLIPY partner account with explicit API Terms approval. Monetization is **OFF**. The key is **TESTING**, not production-approved. Credentials are outside both repositories, in a local file restricted to the user; no key is committed. Configured local testing APKs now ingest the private key at build time; ordinary CI builds remain unconfigured.

KLIPY's [official Postman workspace](https://www.postman.com/klipy-api/klipy-api-services/overview) advertises a lifetime-free API. Its [developer FAQ](https://klipy.com/developers) says ads are optional and can be disabled per app. [Setup documentation](https://docs.klipy.com/) describes 100 requests/hour in testing and unlimited calls after production approval. The production agreement for Obadh still needs review; this is not a promise that pricing or access cannot change.

The shared public sandbox key in the Postman documentation returned an invalid-key response. The newly created Obadh key works. Four read-only searches (`bangla`, `বাংলা`, `bangla hello`, `gifgari`), eight results each, Bangladesh locale and high content filtering, returned relevant Bangla/GifGari stickers. Measured API round trips on the development machine were 196, 236, 302 and 154 ms. No ad entries were detected. These are small development samples, not mobile performance or safety guarantees. No artwork was downloaded, retained, added to packs or pushed to GitHub.

## Integration constraints

The [API Terms](https://klipy.com/support/api-terms), last updated June 23, 2026, require visible provider/content attribution. Mixing its search results with other providers needs written consent. Content cannot be stored as an offline collection; the stated exception is search-result thumbnails within the user's app. Access is revocable.

Use optional KLIPY GIF and sticker surfaces with provider attribution. Offline sticker collections are excluded at the user's request. No API calls while ordinary text is typed. Only explicit online queries leave the device, with an explanatory opt-in; sensitive/incognito fields disable the online feature. Never send surrounding editor text, dictionary or clipboard data. Native HTTP on an isolated coroutine/executor is enough: no ad SDK, WebView or video player is needed. Bound parallel requests, response/media sizes, decoded dimensions, thumbnail memory and paging; cancel stale searches when text/recipient/lifecycle changes. Handle offline, 429, authorization failure, empty results, provider outage and rejected image insertion visibly.

## Media navigation and UX contract

Emoji, Stickers and GIFs are three distinct destinations and icons: Emoji remains on the bottom-row comma/emoji key; the toolbar uses a folded sticker and the `GIF` wordmark. Do not duplicate Emoji in the toolbar or expanded toolbox. Never reuse the sticker icon for GIFs. Keep their positions stable, native theme contrast and accessibility names explicit. Fit the toolbar to the available width with 48 dp touch targets; use the existing tools surface on narrow/floating keyboards rather than a horizontally scrolling row or tiny targets. The GIF control is enabled only when its destination actually works.

Both online stickers and GIFs share one KLIPY transport, cancellation, filtering, attribution and rich-content delivery implementation. Their destinations start independent search sessions; rotation retains the active query and results. Switching destinations must not send content. Offline sticker packs are removed. Loading preserves cell geometry and scroll position, paging never blocks typing, and only visible previews animate. Respect reduced motion, pause previews on hide and limit simultaneous animations. Confirm compatibility before downloading full selected media; explain unsupported editors without pasting links, replacing text or silently losing composition. Retry is explicit and preserves the user's query. Search Back returns to the original editor without inserting the search text.

Send through Android rich-content MIME negotiation and temporary grants. Selected full media must not become a durable local cache. Before store release, obtain production approval and verify the final ad-free platform configuration. Attribution follows the official guide and accepted insertions register share events. The API response offers GIF/WebP/PNG, but declared format filtering returned additional formats in this sample; the client must validate selected MIME and actual media rather than trusting this parameter. Some PNG first frames are tiny/blank; evaluate representative still frames and animated WebP support before choosing a default.

## Remaining work

Production approval and a production key are still required. The integration is implemented for explicitly configured local testing builds; it is not enabled by a shared public key or approved for production. See [implementation and recents policy](stickers.md). `scripts/evaluate-klipy.py` can repeat the read-only coverage/latency check using `KLIPY_APP_KEY`; it never logs the key and does not store provider artwork.
