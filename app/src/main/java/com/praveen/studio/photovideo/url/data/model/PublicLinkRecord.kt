package com.praveen.studio.photovideo.url.data.model

/** A public_links record as the website reads it (same field names and fallbacks as the PHP code). */
data class PublicLinkRecord(
    val url: String,
    val type: String?,
    val mime: String,
    val fileName: String,
    val storagePath: String,
    val active: Boolean
)

sealed interface PublicLinkLookup {
    data class Found(val record: PublicLinkRecord) : PublicLinkLookup
    data object Missing : PublicLinkLookup
    data object Error : PublicLinkLookup
}
