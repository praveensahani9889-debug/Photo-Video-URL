package com.praveen.studio.photovideo.url.data

import com.praveen.studio.photovideo.url.data.local.SupabaseAccountManager
import com.praveen.studio.photovideo.url.data.model.SupabaseAccount
import com.praveen.studio.photovideo.url.data.model.UploadRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseAccountTest {

    private fun account(id: String, host: String, bucket: String = "media", mb: Long = 1024L, used: Long = 0L) =
        SupabaseAccount(
            id = id,
            projectUrl = "https://$host",
            anonKey = "eyJhbGciOi.payload.sig",
            bucketName = bucket,
            capacityMb = mb,
            usedBytes = used
        )

    @Test
    fun usageNumbersAreCalculated() {
        val a = account("a", "one.supabase.co", mb = 100L, used = 50L * 1024L * 1024L)
        assertEquals(50, a.usedPercent)
        assertEquals(50L * 1024L * 1024L, a.freeBytes)
        assertFalse(a.isFullOrNearly)
        assertTrue(a.wouldOverflow(60L * 1024L * 1024L))
        assertFalse(a.wouldOverflow(10L * 1024L * 1024L))
    }

    @Test
    fun nearlyFullProjectHandsOver() {
        val a = account("a", "one.supabase.co", mb = 100L, used = 99L * 1024L * 1024L)
        assertTrue(a.isFullOrNearly)
        assertTrue(account("b", "two.supabase.co").copy(full = true).isFullOrNearly)
    }

    @Test
    fun sameProjectNeedsSameHostAndBucket() {
        val a = account("a", "one.supabase.co", "media")
        assertTrue(a.sameProject(account("b", "ONE.supabase.co", "Media")))
        assertFalse(a.sameProject(account("c", "one.supabase.co", "other")))
        assertFalse(a.sameProject(account("d", "two.supabase.co", "media")))
    }

    @Test
    fun displayNameFallsBackToTheProjectId() {
        assertEquals("one", account("a", "one.supabase.co").displayName)
        assertEquals("My storage", account("a", "one.supabase.co").copy(label = " My storage ").displayName)
    }

    @Test
    fun recordsAreAssignedToTheirOwnProject() {
        val one = account("one", "one.supabase.co", "media")
        val two = account("two", "two.supabase.co", "media")
        val records = listOf(
            UploadRecord(shortId = "1", supabaseUrl = "https://one.supabase.co/storage/v1/object/public/media/images/a.jpg", fileSize = 100L),
            UploadRecord(shortId = "2", supabaseUrl = "https://two.supabase.co/storage/v1/object/public/media/videos/b.mp4", fileSize = 300L),
            UploadRecord(shortId = "3", supabaseUrl = "https://two.supabase.co/storage/v1/object/public/media/images/c.jpg", fileSize = 50L),
            // free trial photos live in the developer's storage and are not counted
            UploadRecord(shortId = "4", supabaseUrl = "https://dev.supabase.co/x.jpg", fileSize = 999L, isFreeTier = true)
        )
        val sums = SupabaseAccountManager.recordSums(listOf(one, two), records)
        assertEquals(100L, sums["one"])
        assertEquals(350L, sums["two"])
        assertNull(SupabaseAccountManager.accountOfRecord(listOf(one, two), records[3]))
    }
}
