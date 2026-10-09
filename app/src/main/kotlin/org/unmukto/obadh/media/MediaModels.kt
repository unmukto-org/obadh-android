// SPDX-License-Identifier: GPL-3.0-only
package org.unmukto.obadh.media

enum class MediaKind(val path: String, val title: String, val searchHint: String) {
    STICKERS("stickers", "Stickers", "Search stickers"), GIFS("gifs", "GIFs", "Search GIFs")
}

data class MediaFile(val url: String, val mime: String, val width: Int, val height: Int, val bytes: Int)
data class MediaItem(val slug: String, val title: String, val attribution: String,
    val preview: MediaFile, val files: List<MediaFile>)
data class MediaPage(val items: List<MediaItem>, val hasNext: Boolean)

/** Recents retain references and permitted thumbnails, never an original animation. */
data class RecentMedia(val kind: MediaKind, val slug: String, val title: String,
    val attribution: String, val preview: MediaFile)

enum class MediaFailure(val message: String) {
    OFFLINE("Couldn't connect. Check your connection and retry."),
    RATE_LIMIT("KLIPY's request limit was reached. Please try again later."),
    AUTH("Online media is temporarily unavailable. Please try again later."),
    SERVER("KLIPY is unavailable right now. Please retry."),
    INVALID("This item isn't available in a supported format. Try another one."),
    TOO_LARGE("This animation is too large to send. Choose a smaller one."),
    BUSY("Media is busy. Please retry."),
}
class MediaException(val failure: MediaFailure) : Exception(failure.message)
