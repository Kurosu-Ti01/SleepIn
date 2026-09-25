package com.kurosu.sleepin.ui.screen.importer.web

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Page events the import screen reacts to.
 *
 * A WebView reports navigation on several callbacks that all arrive on the main thread; funnelling
 * them through one listener keeps the screen from having to define two anonymous clients inline.
 */
interface ImportPageListener {

    /** A new document started loading. Any script injected into the previous one is gone. */
    fun onPageStarted(url: String)

    /** The document finished loading, so the runtime can be installed. */
    fun onPageFinished(url: String)

    fun onTitleChanged(title: String)

    fun onProgressChanged(progress: Int)

    /** The main frame could not be loaded at all. */
    fun onPageLoadFailed(message: String)
}

/**
 * Builds and tears down the WebView that hosts an import script.
 *
 * The configuration is what a school portal needs to work at all: JavaScript and DOM storage for the
 * site's own front end, a desktop-like viewport so the timetable table is not squeezed into a phone
 * layout, and mixed content allowed because portals still embed plain HTTP resources. Third-party
 * cookies are accepted because campus single-sign-on usually sets them from another host.
 */
object ImportWebViewFactory {

    /**
     * Creates a WebView for [initialUrl] with the bridge installed.
     *
     * @param userAgent the identity to use on the initial request. The caller derives it from the
     *   WebView's default value so switching profiles never has to guess a Chromium version.
     * @param host the object pages see as [SleepInBridgeProtocol.HOST_INTERFACE].
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun create(
        context: Context,
        initialUrl: String,
        userAgent: String,
        host: SleepInBridgeHost,
        listener: ImportPageListener
    ): WebView {
        val webView = WebView(context)

        // Lets a debug build be inspected from chrome://inspect, which is the only practical way to see
        // what a preset's script actually did. In a release build pages stay opaque.
        if (isDebuggable(context)) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            userAgentString = userAgent
            // localStorage and sessionStorage, which portals use to keep a login across page loads.
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            // Pages that open links in a new window would otherwise be dropped silently; loading them
            // in place keeps the user inside the one WebView the import runs in.
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.addJavascriptInterface(host, SleepInBridgeProtocol.HOST_INTERFACE)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                listener.onPageStarted(url.orEmpty())
            }

            override fun onPageFinished(view: WebView, url: String?) {
                listener.onPageFinished(url.orEmpty())
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                // Sub-resource failures are normal on portal pages (trackers, dead images), so only a
                // failed main frame is worth telling the user about.
                if (!request.isForMainFrame) return
                listener.onPageLoadFailed("页面加载失败：${error.description}")
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                listener.onProgressChanged(newProgress)
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                title?.let { received -> listener.onTitleChanged(received) }
            }
        }

        // Navigation is intentionally not overridden: leaving every URL to the WebView keeps the user
        // in the same view, which is where the import button and the bridge live.
        webView.loadUrl(initialUrl)
        return webView
    }

    /**
     * Releases [webView] in the order that avoids delivering callbacks into a dead view.
     *
     * Cookies and the disk cache are deliberately left alone: the user is expected to come back to the
     * same portal, and clearing them would mean logging in again on every import.
     */
    fun destroy(webView: WebView) {
        webView.stopLoading()
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.clearHistory()
        webView.clearCache(false)
        webView.clearFormData()
        webView.clearSslPreferences()
        webView.destroy()
    }

    private fun isDebuggable(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
