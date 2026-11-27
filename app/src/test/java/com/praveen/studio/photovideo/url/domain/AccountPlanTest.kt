package com.praveen.studio.photovideo.url.domain

import com.praveen.studio.photovideo.url.data.model.AccountInfo
import com.praveen.studio.photovideo.url.data.model.AccountType
import com.praveen.studio.photovideo.url.data.model.MediaCategory
import com.praveen.studio.photovideo.url.data.model.PlanLimits
import com.praveen.studio.photovideo.url.data.model.Plans
import com.praveen.studio.photovideo.url.domain.util.HostingRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountPlanTest {

    @Test
    fun accountType_parsesWhatTheOwnerTypesInTheConsole() {
        assertEquals(AccountType.FREE, AccountType.parse(null))
        assertEquals(AccountType.FREE, AccountType.parse("something else"))
        assertEquals(AccountType.PREMIUM_49, AccountType.parse("Premium 49"))
        assertEquals(AccountType.PREMIUM_399, AccountType.parse("premium_399"))
        assertEquals(AccountType.ADMIN, AccountType.parse(" Admin "))
        assertEquals(AccountType.MODERATOR, AccountType.parse("moderator"))
        assertEquals(AccountType.FRIEND, AccountType.parse("friends"))
    }

    @Test
    fun freePlan_hasTheAgreedLimits() {
        val p = Plans.FREE
        assertEquals(3, p.dailyLimit(MediaCategory.IMAGES))
        assertEquals(2, p.dailyLimit(MediaCategory.VIDEOS))
        assertEquals(1, p.dailyLimit(MediaCategory.DOCUMENTS))
        assertEquals(1, p.dailyLimit(MediaCategory.APKS))
        assertEquals(1, p.dailyLimit(MediaCategory.FILES))
        assertEquals(5, p.dailyLimit(MediaCategory.TEXT))
        assertEquals(36, p.websiteEveryHours)
        assertEquals(1, p.maxSupabase)
        assertFalse(p.advancedOptions)
        assertFalse(p.hostingEndings)
        assertTrue(p.showAds)
    }

    @Test
    fun premium49_hasTheAgreedLimits() {
        val p = Plans.PREMIUM_49
        assertEquals(10, p.dailyLimit(MediaCategory.IMAGES))
        assertEquals(5, p.dailyLimit(MediaCategory.VIDEOS))
        assertEquals(3, p.dailyLimit(MediaCategory.DOCUMENTS))
        assertEquals(2, p.dailyLimit(MediaCategory.APKS))
        assertEquals(2, p.dailyLimit(MediaCategory.FILES))
        assertEquals(10, p.dailyLimit(MediaCategory.TEXT))
        assertEquals(12, p.websiteEveryHours)
        assertEquals(2, p.maxSupabase)
        assertTrue(p.advancedOptions)
        assertTrue(p.hostingEndings)
        assertFalse(p.customEndings)
        assertTrue(p.showAds)
    }

    @Test
    fun premium399_isUnlimitedWithoutAds() {
        val p = Plans.PREMIUM_399
        MediaCategory.entries.forEach { assertEquals(PlanLimits.UNLIMITED, p.dailyLimit(it)) }
        assertEquals(6, p.websiteEveryHours)
        assertEquals(5, p.maxSupabase)
        assertTrue(p.customEndings)
        assertFalse(p.showAds)
        assertTrue(p.premiumSupport)
        assertTrue(p.earlyAccess)
    }

    @Test
    fun teamAccounts_getEverythingOf399PlusUnlimitedProjects() {
        listOf(AccountType.FRIEND, AccountType.MODERATOR, AccountType.ADMIN).forEach { type ->
            val p = Plans.forType(type)
            assertTrue(p.unlimitedSupabase)
            assertEquals(0, p.websiteEveryHours)
            assertFalse(p.showAds)
            assertTrue(p.customEndings)
        }
    }

    @Test
    fun expiredPaidPlan_countsAsFree_butTeamNeverExpires() {
        val past = System.currentTimeMillis() - 1_000L
        assertEquals(AccountType.FREE, AccountInfo(AccountType.PREMIUM_49, "x", past).effectiveType)
        assertEquals(AccountType.PREMIUM_399, AccountInfo(AccountType.PREMIUM_399, "x", 0L).effectiveType)
        assertEquals(AccountType.ADMIN, AccountInfo(AccountType.ADMIN, "", past).effectiveType)
    }

    @Test
    fun customEnding_rulesAreStrict() {
        assertTrue(HostingRules.isValidCustomSuffix(".xyz9"))
        assertFalse(HostingRules.isValidCustomSuffix(".php"))
        assertFalse(HostingRules.isValidCustomSuffix(".json"))
        assertFalse(HostingRules.isValidCustomSuffix(".a"))
        assertFalse(HostingRules.isValidCustomSuffix(".toolongending1"))
        assertFalse(HostingRules.isValidCustomSuffix("xyz"))
        assertEquals(".abc", HostingRules.cleanCustomSuffix(" A.b-C "))
        // an own ending is only accepted when the plan allows it
        assertNotNull(HostingRules.problem("mysite", ".zzz"))
        assertNull(HostingRules.problem("mysite", ".zzz", emptyList(), allowCustomEnding = true))
    }
}
