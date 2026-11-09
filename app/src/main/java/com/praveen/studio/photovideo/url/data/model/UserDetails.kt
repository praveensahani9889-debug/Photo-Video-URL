package com.praveen.studio.photovideo.url.data.model

data class UserDetails(
    var name: String = "",
    var totalPhotosUpload: Long = 0L,
    var totalVideosUpload: Long = 0L,
    var totalDocumentsUpload: Long = 0L,
    var totalFilesUpload: Long = 0L,
    var totalApksUpload: Long = 0L,
    var supabaseApi: String = "",
    var supabaseKey: String = "",
    var premium: Boolean = false,
    var freeTierUploadsUsed: Long = 0L,
    var freeTierMax: Long = 3L
) {
    val totalAllUploads: Long
        get() = totalPhotosUpload + totalVideosUpload + totalDocumentsUpload + totalFilesUpload + totalApksUpload

    val hasFreeUploadsLeft: Boolean
        get() = freeTierUploadsUsed < freeTierMax

    val freeUploadsRemaining: Int
        get() = (freeTierMax - freeTierUploadsUsed).coerceAtLeast(0L).toInt()
}
