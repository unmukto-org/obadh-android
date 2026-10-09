# GIF provider evaluation (9 October 2026)

Voice typing is out of scope. A reaction GIF picker must be optional and must never make typing depend on a network connection. No suitable anonymous, free, open reaction GIF catalog has been verified, so this build does not expose an unfinished GIF button or scrape a provider's website.

* [Google's Tenor notice](https://support.google.com/tenor/answer/10455265?hl=en): the third-party API ended on 30 June 2026. Gboard's continuing access does not grant Obadh access.
* [GIPHY's official API documentation](https://developers.giphy.com/docs/api/): a new beta key allows 100 calls per hour; production requires application review and a pricing discussion. Attribution is mandatory. Media caching needs separate approval, which prevents assuming the bounded local cache a mobile keyboard needs.
* [KLIPY's official documentation](https://docs.klipy.com/) and [terms](https://klipy.com/support/api-terms): registration and an application key are required. The catalog is proprietary and access revocable. Thumbnail caching is allowed; general content storage is not. This could be an optional provider after a production account and exact distribution conditions are verified; it is not an open catalog.

No provider account was created, agreement accepted, third-party key borrowed, or SDK added. A Wikimedia Commons search is not an adequate substitute for a curated reaction GIF service; licensing varies by file and relevance would be poor.

## Integration contract once a provider is chosen

Use a small provider interface in a separately loaded panel, rather than a networking SDK in the keystroke path. Search is explicit, with 300 ms debounce, cancellation and stale-result generation checks. Only visible thumbnail requests run, with bounded parallelism/memory, a permitted bounded disk cache, and explicit data-saver behavior. Cap response bytes, dimensions, animation frames and selected GIF size; enforce HTTPS and validated provider media origins. Handle offline, timeout, rate limit, authentication failure, empty search, pagination, decode errors, cancellation, process death and editor changes. No typed text is sent automatically. Incognito and sensitive fields disable online queries.

Sharing must negotiate `EditorInfoCompat` MIME types and use Android `commitContent` with scoped `FileProvider` URIs and temporary read grants. Check the editor identity again before committing, revoke failed grants and delete partial downloads. An app that cannot accept GIFs gets an explicit supported fallback, never an unexplained no-op. Downloads and decoding run off the IME main thread; hiding the panel cancels unneeded work. Document required attribution and data handling in the app before release.
