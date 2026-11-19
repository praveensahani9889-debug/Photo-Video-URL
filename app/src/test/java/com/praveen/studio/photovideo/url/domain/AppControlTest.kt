package com.praveen.studio.photovideo.url.domain

import com.praveen.studio.photovideo.url.data.model.AppControl
import com.praveen.studio.photovideo.url.domain.util.VersionUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppControlTest {

    @Test
    fun newerVersionIsDetected() {
        assertTrue(VersionUtils.isNewer("2.5.0", "2.4.2"))
        assertTrue(VersionUtils.isNewer("2.4.10", "2.4.9"))
        assertTrue(VersionUtils.isNewer("3", "2.9.9"))
    }

    @Test
    fun sameOrOlderVersionIsNotAnUpdate() {
        assertFalse(VersionUtils.isNewer("2.4.2", "2.4.2"))
        assertFalse(VersionUtils.isNewer("2.4.1", "2.4.2"))
        assertFalse(VersionUtils.isNewer("v2.4.2", "2.4.2"))
    }

    @Test
    fun blankOrBrokenVersionNeverTriggersAnUpdate() {
        assertFalse(VersionUtils.isNewer("", "2.4.2"))
        assertFalse(VersionUtils.isNewer("abc", "2.4.2"))
    }

    @Test
    fun demoLimitIsZeroWhenTheDemoIsOff() {
        assertEquals(0L, AppControl(demoEnabled = false, demoImageLimit = 5L).effectiveDemoLimit)
        assertEquals(5L, AppControl(demoEnabled = true, demoImageLimit = 5L).effectiveDemoLimit)
        assertEquals(3L, AppControl().effectiveDemoLimit)
    }

    @Test
    fun defaultsKeepTheAppOpen() {
        val control = AppControl()
        assertFalse(control.maintenance)
        assertFalse(control.newUpdate)
    }

    @Test
    fun networkLimitCanBeSwitchedOff() {
        assertEquals(30L, AppControl().effectiveIpLimit)
        assertEquals(null, AppControl(ipLimitEnabled = false).effectiveIpLimit)
        assertEquals(10L, AppControl(ipFreeLimit = 10L).effectiveIpLimit)
    }

    @Test
    fun supabaseProjectLimitStaysInRange() {
        assertEquals(5, AppControl().effectiveMaxAccounts)
        assertEquals(1, AppControl(maxSupabaseAccounts = 0).effectiveMaxAccounts)
        assertEquals(10, AppControl(maxSupabaseAccounts = 99).effectiveMaxAccounts)
    }
}
