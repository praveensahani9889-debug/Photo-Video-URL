package com.praveen.studio.photovideo.url.data.model

import android.net.Uri

data class IncomingSharedMedia(
    val uris: List<Uri>,
    val detectedCategory: MediaCategory,
    val previewName: String = "",
    val totalCount: Int = uris.size
)
