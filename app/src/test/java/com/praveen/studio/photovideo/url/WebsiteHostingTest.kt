package com.praveen.studio.photovideo.url

import com.praveen.studio.photovideo.url.domain.util.HostingRules
import com.praveen.studio.photovideo.url.domain.util.WebsiteBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteHostingTest {

    @Test
    fun hostingName_goodNameIsAccepted() {
        assertNull(HostingRules.problem("mysite", ".com"))
        assertNull(HostingRules.problem("My-Shop-24", ".ivu.com"))
    }

    @Test
    fun hostingName_badOrReservedNamesAreRefused() {
        assertNotNull(HostingRules.problem("", ".com"))
        assertNotNull(HostingRules.problem("ab", ".com"))
        assertNotNull(HostingRules.problem("my site", ".com"))
        assertNotNull(HostingRules.problem("my.site", ".com"))
        assertNotNull(HostingRules.problem("-mysite", ".com"))
        assertNotNull(HostingRules.problem("my--site", ".com"))
        assertNotNull(HostingRules.problem("mysite", ".exe"))
        // names that look like official pages or can fool people
        assertNotNull(HostingRules.problem("app", ".com"))
        assertNotNull(HostingRules.problem("info", ".org"))
        assertNotNull(HostingRules.problem("abuse", ".in"))
        assertNotNull(HostingRules.problem("google", ".com"))
        assertNotNull(HostingRules.problem("my-app", ".com"))
    }

    @Test
    fun hostingKey_hasNoDots_andAddressIsWhatTheVisitorTypes() {
        assertEquals("mysite,com", HostingRules.key("mysite", ".com"))
        assertEquals("mysite,ivu,com", HostingRules.key("MySite", ".ivu.com"))
        assertEquals("mysite.ivu.com", HostingRules.address("MySite", ".ivu.com"))
        assertEquals("https://ivu.gt.tc/mysite.com", HostingRules.url("mysite", ".com"))
        assertTrue(HostingRules.isHostingKey("mysite,com"))
        assertTrue(HostingRules.SUFFIXES.all { it.startsWith(".") })
        assertTrue(HostingRules.SUFFIXES.none { it.contains(',') })
    }

    @Test
    fun websiteFiles_onlySafeNamesAndTypes() {
        assertEquals("css/app.css", WebsiteBuilder.safePath("css/app.css"))
        assertEquals("index.html", WebsiteBuilder.safePath("./index.html"))
        assertNull(WebsiteBuilder.safePath("../secret.js"))
        assertNull(WebsiteBuilder.safePath("/etc/passwd"))
        assertNull(WebsiteBuilder.safePath(".env"))
        assertNull(WebsiteBuilder.safePath("my photo.png"))
        assertNull(WebsiteBuilder.safePath("img/\u00e9.png"))
        assertEquals("image/png", WebsiteBuilder.mimeOf("logo.PNG"))
        assertEquals("text/html", WebsiteBuilder.mimeOf("index.html"))
        assertNull(WebsiteBuilder.mimeOf("setup.exe"))
        assertNull(WebsiteBuilder.mimeOf("run.php"))
        assertNull(WebsiteBuilder.mimeOf("app.apk"))
    }
}
