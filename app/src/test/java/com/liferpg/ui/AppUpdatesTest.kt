package com.liferpg.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class AppUpdatesTest {
    private val valid = """{"versionCode":10,"versionName":"2.1-alpha8","apkUrl":"https://github.com/JotaDev21/remnant-updates/releases/download/2.1-alpha8/REMNANT-2.1-alpha8.apk","sha256":"${"a".repeat(64)}","notes":"Correções"}"""

    @Test fun acceptsExpectedReleaseHostAndChecksum() {
        val release = AppUpdates.parseRelease(valid)
        assertEquals(10, release.versionCode)
        assertEquals("a".repeat(64), release.sha256)
    }

    @Test fun rejectsForeignDownloadAndMalformedChecksum() {
        listOf(
            valid.replace("github.com/JotaDev21/remnant-updates", "example.com/bad"),
            valid.replace("a".repeat(64), "bad"),
        ).forEach { json ->
            try { AppUpdates.parseRelease(json); fail("Invalid update must be rejected") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
