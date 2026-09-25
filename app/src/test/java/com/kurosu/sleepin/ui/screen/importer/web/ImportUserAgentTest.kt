package com.kurosu.sleepin.ui.screen.importer.web

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the User-Agent selector used by the import WebView. */
class ImportUserAgentTest {

    private val mobileUserAgent =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36"

    @Test
    fun mobileMode_keepsTheWebViewsDefaultIdentity() {
        assertEquals(
            mobileUserAgent,
            userAgentForMode(mobileUserAgent, ImportUserAgentMode.MOBILE)
        )
    }

    @Test
    fun desktopModeKeepsTheWebViewChromiumVersion() {
        assertEquals(
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            userAgentForMode(mobileUserAgent, ImportUserAgentMode.DESKTOP)
        )
    }

    @Test
    fun desktopModeKeepsTheOriginalWhenItCannotIdentifyTheChromiumVersion() {
        val unusual = "CustomBrowser on Android"

        assertEquals(unusual, userAgentForMode(unusual, ImportUserAgentMode.DESKTOP))
    }
}
