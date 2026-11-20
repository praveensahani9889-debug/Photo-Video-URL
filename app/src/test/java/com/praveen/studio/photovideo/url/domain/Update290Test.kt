package com.praveen.studio.photovideo.url.domain

import com.praveen.studio.photovideo.url.data.model.SupabaseAccount
import com.praveen.studio.photovideo.url.domain.util.LinkRules
import com.praveen.studio.photovideo.url.domain.util.LinkSlugRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Version 2.9.0: reserved website names, fixed custom names, one entry per Supabase project. */
class Update290Test {

    @Test
    fun websitePagesAndSystemFoldersCannotBeCustomNames() {
        listOf("features", "disclaimer", "how-it-works", "thumb", "config", "cache", "errors", "web_photo", "paytm", "bullshit")
            .forEach { assertNotNull("should be blocked: $it", LinkSlugRules.problem(it)) }
    }

    @Test
    fun everydayNamesAreStillFree() {
        listOf("holiday-photos", "family-pics", "wedding-2026", "resume-final", "classic-car", "hello-world")
            .forEach { assertTrue("should be free: $it", LinkSlugRules.problem(it) == null) }
    }

    @Test
    fun onlyAutomaticIdsCanBeReplacedByACustomName() {
        assertTrue(LinkRules.isTypedId("image_AbC1234"))
        assertFalse(LinkRules.isTypedId("my-trip"))
    }

    @Test
    fun sameSupabaseProjectOrKeyIsNotAddedTwice() {
        val a = SupabaseAccount(id = "a", projectUrl = "https://abcd.supabase.co", anonKey = "key-one", bucketName = "media")
        val sameProjectOtherBucket = SupabaseAccount(id = "b", projectUrl = "https://abcd.supabase.co/", anonKey = "key-two", bucketName = "photos")
        val sameKeyOtherUrl = SupabaseAccount(id = "c", projectUrl = "https://efgh.supabase.co", anonKey = "key-one")
        val different = SupabaseAccount(id = "d", projectUrl = "https://zzzz.supabase.co", anonKey = "key-nine")

        assertTrue(sameProjectOtherBucket.isDuplicateOf(a))
        assertTrue(sameKeyOtherUrl.isDuplicateOf(a))
        assertFalse(different.isDuplicateOf(a))
        assertFalse(a.isDuplicateOf(a)) // editing an entry is not a duplicate of itself
    }
}
