package com.kurosu.sleepin.ui.screen.importer.web

/** User-Agent profile the import WebView will present to an academic portal. */
enum class ImportUserAgentMode {
    /** WebView's default Android identity, best for portals with a mobile-specific front end. */
    MOBILE,

    /** A desktop-shaped identity, useful when a portal hides the timetable in its mobile layout. */
    DESKTOP
}

/**
 * Makes a desktop-shaped User-Agent from the WebView's default User-Agent.
 *
 * Keeping the Chromium version from the running WebView is more accurate than embedding a fixed
 * desktop UA that ages every time Android updates its WebView. A User-Agent alone does not make a
 * phone a desktop browser: a portal can also inspect viewport size, touch support, or other features,
 * so this is a compatibility switch, not an identity or security boundary. It must not be presented as
 * a way to bypass authentication or access rules.
 */
internal fun userAgentForMode(mobileUserAgent: String, mode: ImportUserAgentMode): String = when (mode) {
    ImportUserAgentMode.MOBILE -> mobileUserAgent
    ImportUserAgentMode.DESKTOP -> desktopUserAgent(mobileUserAgent)
}

/**
 * Produces a Linux desktop-shaped UA while retaining the Chrome version from the current WebView.
 *
 * If the WebView does not identify its Chromium version in the usual way, keep its native UA instead
 * of inventing one: presenting an unknown browser version is less useful than staying in mobile mode.
 */
internal fun desktopUserAgent(mobileUserAgent: String): String {
    val chromeVersion = Regex("Chrome/([0-9.]+)")
        .find(mobileUserAgent)
        ?.groupValues
        ?.getOrNull(1)
        ?: return mobileUserAgent

    return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/$chromeVersion Safari/537.36"
}
