package com.kurosu.sleepin.ui.screen.importer.web

import android.os.Handler
import android.webkit.JavascriptInterface

/**
 * Receives bridge messages from the page.
 *
 * This object is the only native surface a web page can reach, so it does nothing but forward the raw
 * text: decoding, validation and every user-visible consequence happen above it, where they can be
 * tested and where a page cannot influence them.
 *
 * The exposed method name must stay in step with [SleepInBridgeProtocol.HOST_METHOD] and the object is
 * registered under [SleepInBridgeProtocol.HOST_INTERFACE].
 */
class SleepInBridgeHost(
    private val mainHandler: Handler,
    private val onMessage: (String) -> Unit
) {

    /**
     * Called by the page's runtime for every request.
     *
     * `@JavascriptInterface` methods run on the WebView's JavaScript thread, while everything they
     * lead to — dialogs, database writes, `evaluateJavascript` — is main-thread work. The hop happens
     * here rather than in each caller so it cannot be forgotten.
     */
    @JavascriptInterface
    fun postMessage(message: String?) {
        // A page may pass a non-string; there is nothing to act on in that case.
        val payload = message ?: return
        mainHandler.post { onMessage(payload) }
    }
}

/**
 * The host-to-page direction of the bridge.
 *
 * The ViewModel drives everything the bridge does, but a WebView is a view: letting the ViewModel hold
 * one would tie it to a lifecycle it does not own and make the request flow untestable. The screen
 * implements this over its WebView and attaches it for as long as the page is on screen.
 */
interface ImportWebViewHandle {

    /**
     * Runs [script] in the page.
     *
     * [onResult] receives the value the script evaluated to, serialized as JSON, and is only ever
     * called when the page is still alive.
     */
    fun evaluate(script: String, onResult: ((String?) -> Unit)? = null)

    /** Reloads the page, which also resets its globals so a script can be injected again. */
    fun reload()
}
