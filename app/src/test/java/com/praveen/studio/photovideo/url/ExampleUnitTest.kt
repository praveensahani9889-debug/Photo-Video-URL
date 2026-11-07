package com.praveen.studio.photovideo.url

import com.praveen.studio.photovideo.url.data.model.MediaCategory
import com.praveen.studio.photovideo.url.data.model.SupabaseConfig
import com.praveen.studio.photovideo.url.data.model.UserDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testSupabaseConfigValidation() {
        val emptyConfig = SupabaseConfig()
        assertFalse(emptyConfig.isConfigured)

        val validConfig = SupabaseConfig(
            projectUrl = "https://myproject.supabase.co",
            anonKey = "eyJhbGciOi...",
            bucketName = "media"
        )
        assertTrue(validConfig.isConfigured)
        assertFalse(validConfig.isPlaceholder)
        assertEquals("https://myproject.supabase.co", validConfig.cleanUrl)
    }

    @Test
    fun testUserDetailsCounters() {
        val details = UserDetails(
            name = "Praveen",
            totalPhotosUpload = 2,
            totalVideosUpload = 1,
            freeTierUploadsUsed = 1,
            freeTierMax = 3
        )
        assertEquals(3, details.totalAllUploads)
        assertTrue(details.hasFreeUploadsLeft)
        assertEquals(2, details.freeUploadsRemaining)
    }

    @Test
    fun testMediaCategoryShortIdPrefix() {
        assertEquals("image_", MediaCategory.IMAGES.shortIdPrefix)
        assertEquals("video_", MediaCategory.VIDEOS.shortIdPrefix)
        assertEquals("document_", MediaCategory.DOCUMENTS.shortIdPrefix)
        assertEquals("file_", MediaCategory.FILES.shortIdPrefix)
        assertEquals("apk_", MediaCategory.APKS.shortIdPrefix)
    }

    @Test
    fun testMediaCategoryFromMimeAndExtension() {
        assertEquals(MediaCategory.IMAGES, MediaCategory.fromMimeAndExtension("image/jpeg", "vacation.jpg"))
        assertEquals(MediaCategory.IMAGES, MediaCategory.fromMimeAndExtension(null, "photo.png"))
        assertEquals(MediaCategory.VIDEOS, MediaCategory.fromMimeAndExtension("video/mp4", "clip.mp4"))
        assertEquals(MediaCategory.APKS, MediaCategory.fromMimeAndExtension("application/vnd.android.package-archive", "app.apk"))
        assertEquals(MediaCategory.APKS, MediaCategory.fromMimeAndExtension(null, "game.apk"))
        assertEquals(MediaCategory.DOCUMENTS, MediaCategory.fromMimeAndExtension("application/pdf", "resume.pdf"))
        assertEquals(MediaCategory.DOCUMENTS, MediaCategory.fromMimeAndExtension(null, "notes.docx"))
        assertEquals(MediaCategory.FILES, MediaCategory.fromMimeAndExtension("application/zip", "backup.zip"))
    }

    @Test
    fun testDeepLinkUrlParsing() {
        val path1 = "/image_AaBb123".trim().removePrefix("/")
        assertEquals("image_AaBb123", path1)

        val fragment1 = "#photo_123".trim().removePrefix("#")
        assertEquals("photo_123", fragment1)

        val generatedShortUrl = "https://ivu.gt.tc/$path1"
        assertEquals("https://ivu.gt.tc/image_AaBb123", generatedShortUrl)
    }
}
