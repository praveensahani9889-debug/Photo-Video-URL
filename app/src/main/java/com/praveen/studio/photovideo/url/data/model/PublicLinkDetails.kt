package com.praveen.studio.photovideo.url.data.model

data class PublicLinkDetails(
    val shortId: String = "",
    val shortUrl: String = "https://ivu.gt.tc/$shortId",
    val supabaseUrl: String = "",
    val type: String = "",
    val mimeType: String = "",
    val active: Boolean = true,
    val createdAt: Long = 0L,
    val isResolved: Boolean = supabaseUrl.isNotBlank()
)
