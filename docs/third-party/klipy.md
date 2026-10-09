# KLIPY optional provider and brand assets

Obadh is independently developed by Unmukto. Its Bangla engine, keyboard integration and application work remain Obadh's own. Online GIF/sticker content is provided by KLIPY; content, creator and brand rights remain with their respective owners. The app's source license does not relicense this provider content or its trademarks.

The provider is subject to the [KLIPY API Terms](https://klipy.com/support/api-terms) and [Privacy Policy](https://klipy.com/support/privacy-policy), both reviewed with a June 23, 2026 update. No KLIPY SDK, ad SDK or bundled GIF/sticker collection is included. The partner platform currently has monetization disabled and a testing key; production approval is pending.

The two unmodified PNG attribution marks in `app/src/main/res/drawable-nodpi/klipy_powered_{black,white}.png` come from KLIPY's official **Logos for GIF Picker Search Bar** bundle, linked from its API attribution instructions:

- [Official attribution folder](https://drive.google.com/drive/folders/1ix5_5221kgbJHPqhCxwPsqHlHexhQP2w)
- [Picker logo PNG folder](https://drive.google.com/drive/folders/1BURIVolMGEMkMZAntGgri6v0h12V0ZwB)
- [Attribution guide](https://docs.google.com/presentation/d/13iOD1u8ANIFcdJqg-A3dj7J7_yR1Mlb1v47Ce_g9Pbw/edit)

Black mark file ID: `1gdB3FaWWyTjWgx8O9pFIkFmNJIwW7RAH`. White mark file ID: `1hUTLcwMgMjiob6GF1BVYH5P6lzLTYQUq`. Retrieved October 9, 2026. The app scales these marks for the native search UI and picks a contrasting variant; it does not redraw the logo. The mandatory search placeholder is **Search KLIPY**. Creator/source metadata, when present, is visible with the item and available in full in its long-press details.

Artwork returned by the API is not redistributed through the Obadh repository. Only bounded search-result thumbnails may be cached in the user's application; original animations are freshly retrieved for each send and only held transiently for Android URI delivery. Offline sticker packs from other sources are excluded.
