package com.praveen.studio.photovideo.url.data.model

data class UserDetails(
    var name: String = "Praveen",
    var totalPhotosUpload: Int = 0,
    var totalVideosUpload: Int = 0,
    var totalDocumentsUpload: Int = 0,
    var totalFilesUpload: Int = 0,
    var totalApksUpload: Int = 0,
    var supabaseApi: String = "",
    var supabaseKey: String = "",
    var premium: Boolean = false,
    var freeTierUploadsUsed: Int = 0,
    var freeTierMax: Int = 3
) {
    val totalAllUploads: Int
        get() = totalPhotosUpload + totalVideosUpload + totalDocumentsUpload + totalFilesUpload + totalApksUpload

    val hasFreeUploadsLeft: Boolean
        get() = freeTierUploadsUsed < freeTierMax

    val freeUploadsRemaining: Int
        get() = (freeTierMax - freeTierUploadsUsed).coerceAtLeast(0)
}
