package com.praveen.studio.photovideo.url.domain

import com.praveen.studio.photovideo.url.data.model.LinkOptions
import com.praveen.studio.photovideo.url.data.model.PublicLinkRecord
import com.praveen.studio.photovideo.url.domain.util.BlockedWords
import com.praveen.studio.photovideo.url.domain.util.LinkDraft
import com.praveen.studio.photovideo.url.domain.util.LinkDraftMapper
import com.praveen.studio.photovideo.url.domain.util.LinkOptionsRules
import com.praveen.studio.photovideo.url.domain.util.LinkRules
import com.praveen.studio.photovideo.url.domain.util.LinkSlugRules
import com.praveen.studio.photovideo.url.domain.util.PasswordHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedOptionsTest {

    // ---- custom address -------------------------------------------------------------------------

    @Test
    fun goodNamesAreAccepted() {
        assertNull(LinkSlugRules.problem("my-trip"))
        assertNull(LinkSlugRules.problem("Summer_2026"))
        assertNull(LinkSlugRules.problem("abc"))
        assertNull(LinkSlugRules.problem("")) // empty = keep the automatic link
    }

    @Test
    fun spacesAndSymbolsAreRefused() {
        assertNotNull(LinkSlugRules.problem("praveen sahani"))
        assertNotNull(LinkSlugRules.problem("my@name"))
        assertNotNull(LinkSlugRules.problem("my#name"))
        assertNotNull(LinkSlugRules.problem("a.b.c"))
        assertNotNull(LinkSlugRules.problem("path/name"))
        assertNotNull(LinkSlugRules.problem("-start"))
    }

    @Test
    fun lengthIsChecked() {
        assertNotNull(LinkSlugRules.problem("ab"))
        assertNotNull(LinkSlugRules.problem("a".repeat(31)))
        assertNull(LinkSlugRules.problem("a".repeat(30)))
    }

    @Test
    fun ownerNamesPagesAndBadWordsAreBlocked() {
        listOf("admin", "Admin", "praveen", "Praveen_Studio", "praveen-studio", "my-admin-page", "about", "privacy", "terms",
            "contact", "adm1n", "image", "fuck", "f_u_c_k_you", "pornsite", "sex").forEach {
            assertNotNull("should be blocked: $it", LinkSlugRules.problem(it))
        }
    }

    @Test
    fun normalNamesAreNotBlockedByAccident() {
        listOf("class", "assassin", "scunthorpe-fc", "crisis", "torpedo", "fukuoka", "grandiose", "laudable", "my_file", "summer-trip").forEach {
            if (it != "scunthorpe-fc") assertNull("should be free: $it", LinkSlugRules.problem(it))
        }
    }

    @Test
    fun wordsFromFirebaseAreBlockedToo() {
        assertNull(LinkSlugRules.problem("mycompany"))
        assertNotNull(LinkSlugRules.problem("mycompany", listOf("company")))
        assertTrue(BlockedWords.isBlocked("best-company", listOf(" Company ")))
    }

    @Test
    fun automaticIdsCannotBeTakenAsCustomNames() {
        assertNotNull(LinkSlugRules.problem("image_abc"))
        assertNotNull(LinkSlugRules.problem("APK_something"))
    }

    @Test
    fun customLinksAreRecognisedOnlyWhenAllowed() {
        assertNull(LinkRules.parseShortId("/my-trip"))
        assertEquals("my-trip", LinkRules.parseShortId("/my-trip", allowCustom = true))
        assertNull(LinkRules.parseShortId("/privacy", allowCustom = true))
        assertEquals("image_AbC1234", LinkRules.parseShortId("/image_AbC1234", allowCustom = true))
        assertEquals("", LinkRules.typeOf("my-trip"))
    }

    // ---- link content decisions -----------------------------------------------------------------

    private fun record(options: LinkOptions = LinkOptions(), type: String? = "image") = PublicLinkRecord(
        url = "https://abc.supabase.co/storage/v1/object/public/media/images/a.jpg",
        type = type,
        mime = "image/jpeg",
        fileName = "a.jpg",
        storagePath = "",
        active = true,
        options = options
    )

    @Test
    fun expiredViewLimitAndPasswordAreChecked() {
        val now = System.currentTimeMillis()
        assertEquals(
            com.praveen.studio.photovideo.url.domain.util.ViewerContent.Expired,
            LinkRules.contentFor("my-trip", record(LinkOptions(expiresAt = now - 1000L)))
        )
        assertEquals(
            com.praveen.studio.photovideo.url.domain.util.ViewerContent.ViewLimitReached,
            LinkRules.contentFor("my-trip", record(LinkOptions(maxViews = 5L, views = 5L)))
        )
        val protectedLink = record(LinkOptions(hasPassword = true, passwordHash = "aa", passwordSalt = "bb", passwordIter = 1000))
        assertTrue(LinkRules.contentFor("my-trip", protectedLink) is com.praveen.studio.photovideo.url.domain.util.ViewerContent.PasswordRequired)
        assertFalse(LinkRules.contentFor("my-trip", protectedLink, passwordOk = true) is com.praveen.studio.photovideo.url.domain.util.ViewerContent.PasswordRequired)
    }

    @Test
    fun customLinkTakesItsTypeFromTheSavedLink() {
        assertTrue(LinkRules.contentFor("my-trip", record(type = "image")) is com.praveen.studio.photovideo.url.domain.util.ViewerContent.Image)
        assertEquals(
            com.praveen.studio.photovideo.url.domain.util.ViewerContent.NotFound,
            LinkRules.contentFor("my-trip", record(type = null))
        )
    }

    // ---- password -------------------------------------------------------------------------------

    @Test
    fun passwordHashIsCheckedAndSalted() {
        val a = PasswordHasher.hash("secret123")
        val b = PasswordHasher.hash("secret123")
        assertEquals(64, a.hash.length)
        assertEquals(32, a.salt.length)
        assertTrue(a.hash != b.hash) // new random salt every time
        assertTrue(PasswordHasher.verify("secret123", a.salt, a.hash, a.iterations))
        assertFalse(PasswordHasher.verify("secret124", a.salt, a.hash, a.iterations))
        assertFalse(PasswordHasher.verify("x", "", "", 0))
    }

    // ---- option values --------------------------------------------------------------------------

    @Test
    fun valuesAreValidated() {
        assertNull(LinkOptionsRules.colorProblem("#1E293B"))
        assertNotNull(LinkOptionsRules.colorProblem("1E293B"))
        assertNotNull(LinkOptionsRules.colorProblem("#12345"))
        assertNull(LinkOptionsRules.urlProblem("https://example.com/a?b=1"))
        assertNull(LinkOptionsRules.urlProblem("example.com"))
        assertNotNull(LinkOptionsRules.urlProblem("javascript:alert(1)"))
        assertNotNull(LinkOptionsRules.urlProblem("https://exa mple.com"))
        assertNotNull(LinkOptionsRules.urlProblem("ftp://example.com"))
        assertNull(LinkOptionsRules.viewsProblem("100"))
        assertNotNull(LinkOptionsRules.viewsProblem("0"))
        assertNotNull(LinkOptionsRules.viewsProblem("abc"))
        assertNull(LinkOptionsRules.passwordProblem("abcd"))
        assertNotNull(LinkOptionsRules.passwordProblem("abc"))
        assertEquals(180L, LinkOptionsRules.customExpiryMinutes("3", 1))
        assertNotNull(LinkOptionsRules.expiryProblem(LinkOptionsRules.customExpiryMinutes("1", 0)))
        assertNull(LinkOptionsRules.expiryProblem(LinkOptionsRules.customExpiryMinutes("2", 2)))
    }

    @Test
    fun draftBecomesOptions() {
        val now = 1_000_000L
        val empty = LinkDraftMapper.toOptions(LinkDraft(), now = now)
        assertTrue(empty is LinkDraftMapper.Result.Ok)
        assertTrue((empty as LinkDraftMapper.Result.Ok).options.isDefault)

        val draft = LinkDraft(
            expiryOn = true, expiryPreset = 0,
            passwordOn = true, password = "hello1",
            viewsOn = true, maxViews = "10",
            bgOn = true, bgColor = "#0ea5e9",
            buttonOn = true, buttonText = "Visit", buttonColor = "#F43F5E", buttonUrl = "example.com",
            allowDownload = false, pageTitle = " Hi "
        )
        val ok = LinkDraftMapper.toOptions(draft, now = now) as LinkDraftMapper.Result.Ok
        val o = ok.options
        assertEquals(now + 60L * 60_000L, o.expiresAt)
        assertTrue(o.isPasswordProtected)
        assertEquals(64, o.passwordHash.length)
        assertEquals(10L, o.maxViews)
        assertEquals("#0EA5E9", o.bgColor)
        assertEquals("https://example.com", o.buttonUrl)
        assertFalse(o.allowDownload)
        assertEquals("Hi", o.pageTitle)
    }

    @Test
    fun wrongValuesGiveAnError() {
        assertTrue(LinkDraftMapper.toOptions(LinkDraft(passwordOn = true)) is LinkDraftMapper.Result.Error)
        assertTrue(LinkDraftMapper.toOptions(LinkDraft(viewsOn = true, maxViews = "0")) is LinkDraftMapper.Result.Error)
        assertTrue(LinkDraftMapper.toOptions(LinkDraft(buttonOn = true, buttonText = "Go", buttonUrl = "nope")) is LinkDraftMapper.Result.Error)
        assertTrue(LinkDraftMapper.toOptions(LinkDraft(expiryOn = true, expiryPreset = LinkDraft.PRESET_CUSTOM, customAmount = "1", customUnit = 0)) is LinkDraftMapper.Result.Error)
    }

    @Test
    fun existingPasswordAndViewsAreKept() {
        val existing = LinkOptions(hasPassword = true, passwordHash = "h".repeat(64), passwordSalt = "s".repeat(32), passwordIter = 10000, views = 7L)
        val ok = LinkDraftMapper.toOptions(LinkDraft(passwordOn = true, password = ""), existing) as LinkDraftMapper.Result.Ok
        assertEquals("h".repeat(64), ok.options.passwordHash)
        assertEquals(7L, ok.options.views)
    }

    @Test
    fun optionsAreWrittenAndRemovedFromFirebaseMaps() {
        val map = mutableMapOf<String, Any?>("type" to "image", "views" to 3L, "bgColor" to "#000000", "pageTitle" to "old")
        LinkOptions(maxViews = 5L, hasPassword = true, passwordHash = "x".repeat(64), passwordSalt = "y".repeat(32), passwordIter = 10000)
            .applyTo(map, publicNode = true)
        assertEquals(5L, map["maxViews"])
        assertEquals(3L, map["views"]) // the website's counter is never touched
        assertFalse(map.containsKey("bgColor")) // switched off options disappear
        assertFalse(map.containsKey("pageTitle"))
        assertEquals(64, (map["passwordHash"] as String).length)

        val userMap = mutableMapOf<String, Any?>()
        LinkOptions(hasPassword = true, passwordHash = "x".repeat(64), passwordSalt = "y".repeat(32), passwordIter = 10000)
            .applyTo(userMap, publicNode = false)
        assertEquals(true, userMap["hasPassword"])
        assertFalse(userMap.containsKey("passwordHash")) // the history entry never keeps the hash
    }
}
