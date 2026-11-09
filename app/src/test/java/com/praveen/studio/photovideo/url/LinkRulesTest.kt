package com.praveen.studio.photovideo.url

import com.praveen.studio.photovideo.url.data.model.PublicLinkRecord
import com.praveen.studio.photovideo.url.data.model.SupabaseConfig
import com.praveen.studio.photovideo.url.domain.util.LinkRules
import com.praveen.studio.photovideo.url.domain.util.ViewerContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app must treat a link exactly like the website does (see LinkRules). */
class LinkRulesTest {

    private val base = "https://abc.supabase.co/storage/v1/object/public/media"

    private fun record(
        path: String,
        type: String? = null,
        mime: String = "",
        name: String = "",
        active: Boolean = true
    ) = PublicLinkRecord(
        url = "$base/$path",
        type = type,
        mime = mime,
        fileName = name,
        storagePath = "",
        active = active
    )

    @Test
    fun shortIdParsing_acceptsOnlyMediaIds() {
        assertEquals("image_AbC1234", LinkRules.parseShortId("/image_AbC1234/"))
        assertEquals("apk_do0MYU9", LinkRules.parseShortId("apk_do0MYU9"))
        assertNull(LinkRules.parseShortId("/privacy"))
        assertNull(LinkRules.parseShortId("/photo_123"))
        assertNull(LinkRules.parseShortId("/image_ab"))
        assertNull(LinkRules.parseShortId(null))
    }

    @Test
    fun storageUrlValidation_matchesWebsiteRules() {
        assertTrue(LinkRules.isAllowedStorageUrl("$base/images/a.jpg"))
        assertFalse(LinkRules.isAllowedStorageUrl("http://abc.supabase.co/storage/v1/object/public/media/a.jpg"))
        assertFalse(LinkRules.isAllowedStorageUrl("https://evil.example.com/storage/v1/object/public/media/a.jpg"))
        assertFalse(LinkRules.isAllowedStorageUrl("$base/images/a b.jpg"))
        assertFalse(LinkRules.isAllowedStorageUrl("$base/../secret.jpg"))
        assertFalse(LinkRules.isAllowedStorageUrl("https://abc.supabase.co/rest/v1/media"))
    }

    @Test
    fun image_isShown() {
        val c = LinkRules.contentFor("image_AbC1234", record("images/a.jpg", type = "image", mime = "image/jpeg"))
        assertTrue(c is ViewerContent.Image)
    }

    @Test
    fun disabledLink_isReportedAsDisabled() {
        val c = LinkRules.contentFor("image_AbC1234", record("images/a.jpg", active = false))
        assertEquals(ViewerContent.Disabled, c)
    }

    @Test
    fun typeMismatch_isNotFound() {
        val c = LinkRules.contentFor("video_AbC1234", record("images/a.jpg", type = "image"))
        assertEquals(ViewerContent.NotFound, c)
    }

    @Test
    fun videoLinkWithImageMime_isUnsupported() {
        val c = LinkRules.contentFor("video_AbC1234", record("videos/a.png", type = "video", mime = "image/png"))
        assertEquals(ViewerContent.Unsupported, c)
    }

    @Test
    fun pdfDocument_isDisplayed_otherDocumentsDownload() {
        val pdf = LinkRules.contentFor(
            "document_AbC1234",
            record("documents/a.pdf", type = "document", mime = "application/pdf")
        )
        assertTrue(pdf is ViewerContent.Pdf)

        val docx = LinkRules.contentFor(
            "document_AbC1234",
            record("documents/a.docx", type = "document", name = "a.docx")
        )
        assertTrue(docx is ViewerContent.Download)
    }

    @Test
    fun apk_isOfferedAsDownloadWithProperName() {
        val c = LinkRules.contentFor(
            "apk_AbC1234",
            record("apks/apk_AbC1234_App.apk", type = "apk", name = "App_v1.apk")
        ) as ViewerContent.Download
        assertEquals("App_v1.apk", c.name)
        assertTrue(c.downloadUrl.endsWith("?download=App_v1.apk"))
    }

    @Test
    fun supabaseUrl_isNormalisedForTheWebsite() {
        assertEquals("https://abc.supabase.co", SupabaseConfig(projectUrl = "http://abc.supabase.co/rest/v1/").cleanUrl)
        assertEquals("https://abc.supabase.co", SupabaseConfig(projectUrl = "ABC.supabase.co/").cleanUrl)
        assertTrue(SupabaseConfig(projectUrl = "abc.supabase.co", anonKey = "k").isWebCompatible)
        assertFalse(SupabaseConfig(projectUrl = "https://storage.mydomain.com", anonKey = "k").isWebCompatible)
    }
}
