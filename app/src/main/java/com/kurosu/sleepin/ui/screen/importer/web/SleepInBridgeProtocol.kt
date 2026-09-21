package com.kurosu.sleepin.ui.screen.importer.web

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A request an import script made through the bridge, as decoded and validated.
 *
 * The page supplies nothing but text and numbers, and every field is defaulted or clamped while
 * decoding, so the dialog host can render a request without checking it again.
 */
sealed interface ImportWebRequest {

    /** Requests the page is waiting on; settling the promise is the host's job. */
    sealed interface Pending : ImportWebRequest {
        val callId: String
    }

    /**
     * Requests that are answered through a modal dialog.
     *
     * Grouped so the dialog host can render the head of its queue with an exhaustive `when`, and so
     * the type itself documents that [Commit] is confirmed elsewhere.
     */
    sealed interface Dialog : Pending

    /** Shows a message with one confirmation button. Resolves `true`, or `false` if dismissed. */
    data class Alert(
        override val callId: String,
        val title: String,
        val message: String,
        val confirmText: String
    ) : Dialog

    /**
     * Asks the user for a line of text.
     *
     * [validateName] names a function in the page that decides whether the input is acceptable; a
     * null value means the script did not ask for validation.
     */
    data class Prompt(
        override val callId: String,
        val title: String,
        val hint: String,
        val initialValue: String,
        val validateName: String?
    ) : Dialog

    /** Offers a single choice. [defaultIndex] is already within [options]' bounds. */
    data class Choose(
        override val callId: String,
        val title: String,
        val options: List<String>,
        val defaultIndex: Int
    ) : Dialog

    /** Hands over an import payload for confirmation and persistence. */
    data class Commit(
        override val callId: String,
        val payloadJson: String
    ) : Pending

    /** Fire-and-forget status line. */
    data class Toast(
        override val callId: String,
        val message: String
    ) : Pending

    /** The script is done and the page can be closed. */
    data class Finish(override val callId: String) : Pending
}

/**
 * Outcome of decoding one bridge message.
 */
sealed interface BridgeDecodeResult {

    data class Decoded(val request: ImportWebRequest) : BridgeDecodeResult

    /**
     * The message cannot be honoured.
     *
     * [callId] is non-null only when it was well formed: rejecting a request means embedding the id in
     * a script, so an unusable id leaves the page's promise pending rather than producing a script
     * that could not be trusted.
     */
    data class Rejected(val callId: String?, val message: String) : BridgeDecodeResult
}

/**
 * Wire format of the JavaScript bridge, and the rules that keep it safe.
 *
 * The WebView exposes one native method to whatever page the user is looking at, so the host side of
 * the bridge is an attack surface rather than a private channel. Everything that reaches
 * `evaluateJavascript` is therefore assembled here from validated pieces: a call id matching a fixed
 * character set, and a result encoded as JSON. A page never supplies script text of its own.
 *
 * Deliberately free of Android types so the framing rules can be tested on the JVM without a WebView.
 */
object SleepInBridgeProtocol {

    /** Name the page sees for the native object. */
    const val HOST_INTERFACE = "SleepInNative"

    /** Method the runtime calls for every request. */
    const val HOST_METHOD = "postMessage"

    /** Global the runtime defines; also the marker that it is already installed. */
    const val READY_FLAG = "__sleepinBridgeReady"

    /** Global the runtime defines to settle a waiting promise. */
    const val RESOLVE_FUNCTION = "__sleepinBridgeResolve"

    /** Global the runtime defines so the host can call a script's validation function by name. */
    const val VALIDATE_FUNCTION = "__sleepinBridgeValidate"

    /** Global scripts use for the calls that have no result. */
    const val BRIDGE_GLOBAL = "sleepinBridge"

    /** Global scripts use for the calls that return a promise. */
    const val PROMISE_GLOBAL = "sleepinBridgePromise"

    /**
     * Most requests that may wait for an answer at once.
     *
     * The bridge is reachable from any page the user browses to, so a page calling in a loop would
     * otherwise pile up dialogs without limit. Requests past the cap are rejected, which gives the
     * script an error to report instead of growing a backlog the user has to dismiss by hand.
     */
    const val MAX_PENDING_REQUESTS = 32

    /** Most options one choice dialog will offer. A real portal needs a handful. */
    const val MAX_CHOOSE_OPTIONS = 32

    /**
     * Largest raw message the bridge will consider.
     *
     * A page can post anything, and a message is parsed into a JSON tree before any of its fields are
     * validated, so the size has to be bounded before parsing rather than after. The payload limit in
     * the import parser is applied later and is deliberately smaller.
     */
    const val MAX_RAW_MESSAGE_CHARS = 4 * 1024 * 1024

    private const val MAX_CALL_ID_LENGTH = 64
    private val CALL_ID_PATTERN = Regex("^[A-Za-z0-9_-]{1,$MAX_CALL_ID_LENGTH}$")

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Whether [callId] may be embedded in a script.
     *
     * Our runtime only ever produces ids matching this, so a value that fails is either a bug or an
     * attempt to break out of the string literal.
     */
    fun isValidCallId(callId: String): Boolean = CALL_ID_PATTERN.matches(callId)

    /**
     * Decodes one message from the page.
     *
     * Every message is treated as untrusted: the operation must be one the runtime defines, an
     * asynchronous one must carry a usable call id, and each payload is decoded into its own shape
     * before anything reaches the user.
     */
    fun decode(raw: String): BridgeDecodeResult {
        if (raw.length > MAX_RAW_MESSAGE_CHARS) {
            return BridgeDecodeResult.Rejected(null, "桥消息过大，已忽略。")
        }

        val envelope = try {
            json.decodeFromString(BridgeEnvelopeDto.serializer(), raw)
        } catch (error: SerializationException) {
            return BridgeDecodeResult.Rejected(
                callId = null,
                message = "桥消息不是合法 JSON：${error.message ?: "解析失败"}"
            )
        }

        val callId = envelope.callId?.takeIf { isValidCallId(it) }
        val opName = envelope.op.orEmpty()
        val op = OP_BY_NAME[opName]
            ?: return BridgeDecodeResult.Rejected(callId, "不支持的桥操作：$opName")

        return when (op) {
            BridgeOp.TOAST -> withCallId(callId, opName) { id ->
                decoded(
                    ImportWebRequest.Toast(
                        callId = id,
                        message = envelope.data.textField("message").orEmpty()
                    )
                )
            }

            BridgeOp.FINISH -> withCallId(callId, opName) { id ->
                decoded(ImportWebRequest.Finish(callId = id))
            }

            BridgeOp.ALERT -> withCallId(callId, opName) { id ->
                val dto = envelope.data.decodeWith(AlertRequestDto.serializer())
                    ?: return@withCallId reject(callId, "$opName 的数据不是对象")
                decoded(
                    ImportWebRequest.Alert(
                        callId = id,
                        title = dto.title.orEmpty().ifBlank { "提示" },
                        message = dto.message.orEmpty(),
                        confirmText = dto.confirmText.orEmpty().ifBlank { "确定" }
                    )
                )
            }

            BridgeOp.PROMPT -> withCallId(callId, opName) { id ->
                val dto = envelope.data.decodeWith(PromptRequestDto.serializer())
                    ?: return@withCallId reject(callId, "$opName 的数据不是对象")
                decoded(
                    ImportWebRequest.Prompt(
                        callId = id,
                        title = dto.title.orEmpty().ifBlank { "输入" },
                        hint = dto.hint.orEmpty(),
                        initialValue = dto.initialValue.orEmpty(),
                        validateName = dto.validate?.trim()?.takeUnless { name -> name.isEmpty() }
                    )
                )
            }

            BridgeOp.CHOOSE -> withCallId(callId, opName) { id ->
                val dto = envelope.data.decodeWith(ChooseRequestDto.serializer())
                    ?: return@withCallId reject(callId, "$opName 的数据不是对象")
                val options = dto.options.orEmpty()
                    .map { option -> option.trim() }
                    // A blank label renders as an unselectable empty row and shifts the index the script
                    // gets back, so it is dropped rather than offered.
                    .filter { option -> option.isNotEmpty() }
                if (options.isEmpty()) return@withCallId reject(callId, "choose 没有提供任何选项")
                if (options.size > MAX_CHOOSE_OPTIONS) {
                    return@withCallId reject(callId, "choose 的选项超过 $MAX_CHOOSE_OPTIONS 个")
                }
                decoded(
                    ImportWebRequest.Choose(
                        callId = id,
                        title = dto.title.orEmpty().ifBlank { "请选择" },
                        options = options,
                        // Clamped rather than rejected: an out-of-range default is a cosmetic mistake
                        // in the script, not a reason to fail the whole import.
                        defaultIndex = (dto.defaultIndex ?: 0).coerceIn(0, options.lastIndex)
                    )
                )
            }

            BridgeOp.COMMIT -> withCallId(callId, opName) { id ->
                val payload = envelope.data
                if (payload == null || payload is JsonNull) {
                    return@withCallId reject(callId, "导入数据为空")
                }
                decoded(
                    ImportWebRequest.Commit(
                        callId = id,
                        payloadJson = json.encodeToString(JsonElement.serializer(), payload)
                    )
                )
            }
        }
    }

    /**
     * Script that installs the runtime, or does nothing when it is already installed.
     *
     * Re-injected after every page load because navigating resets the page's globals, while the user
     * may log in and browse for a while before running the import.
     */
    val RUNTIME_SCRIPT: String = """
        (function () {
          if (window.$READY_FLAG) { return; }
          window.$READY_FLAG = true;

          var sequence = 0;
          var pending = {};

          function call(op, data) {
            return new Promise(function (resolve, reject) {
              sequence += 1;
              var callId = 'c' + sequence;
              pending[callId] = { resolve: resolve, reject: reject };
              try {
                $HOST_INTERFACE.$HOST_METHOD(JSON.stringify({ op: op, callId: callId, data: data }));
              } catch (error) {
                delete pending[callId];
                reject('与应用的连接不可用：' + error);
              }
            });
          }

          window.$RESOLVE_FUNCTION = function (callId, ok, result) {
            var entry = pending[callId];
            if (!entry) { return; }
            delete pending[callId];
            if (ok) { entry.resolve(result); } else { entry.reject(result); }
          };

          window.$VALIDATE_FUNCTION = function (name, input) {
            var validator = window[name];
            if (typeof validator !== 'function') { return '校验函数 ' + name + ' 不存在'; }
            try {
              var message = validator(input);
              if (message === null || message === undefined || message === '') { return null; }
              return String(message);
            } catch (error) {
              return '校验函数执行出错：' + error;
            }
          };

          window.$BRIDGE_GLOBAL = {
            toast: function (message) { call('toast', { message: String(message) }); },
            finish: function () { call('finish', {}); }
          };

          window.$PROMISE_GLOBAL = {
            alert: function (options) { return call('alert', options || {}); },
            prompt: function (options) { return call('prompt', options || {}); },
            choose: function (options) { return call('choose', options || {}); },
            commit: function (payload) { return call('commit', payload || {}); }
          };
        })();
    """.trimIndent()

    /**
     * Script that settles the promise the page is waiting on.
     *
     * [resultLiteral] must already be a JavaScript value literal — pass JSON, or null for "no value".
     */
    fun buildResolveScript(callId: String, ok: Boolean, resultLiteral: String?): String =
        "$RESOLVE_FUNCTION(${stringLiteral(callId)}, $ok, ${resultLiteral ?: "null"})"
            .escapeLineSeparators()

    /**
     * Script that runs the page's validation function for a prompt against [input].
     *
     * The return value arrives through `evaluateJavascript`'s result callback, so no extra operation
     * is needed: the function returns either nothing (accepted) or the message to display.
     */
    fun buildValidateScript(validateName: String, input: String): String =
        "$VALIDATE_FUNCTION(${stringLiteral(validateName)}, ${stringLiteral(input)})"
            .escapeLineSeparators()

    /** JSON literal for a boolean result. */
    fun booleanLiteral(value: Boolean): String = if (value) "true" else "false"

    /** JSON literal for a nullable string result. */
    fun stringResultLiteral(value: String?): String =
        if (value == null) "null" else stringLiteral(value)

    /**
     * Reads the message a validation function returned, from the raw text
     * `evaluateJavascript` reports.
     *
     * The callback serializes the JavaScript value as JSON, so a function that returned nothing and a
     * function that returned `null` both arrive as `null`, and a returned message arrives as a quoted
     * JSON string. Anything that is not a JSON string is treated as the message itself, because
     * returning garbage should still tell the user something rather than silently accepting the input.
     */
    fun parseValidateResult(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text == "null" || text == "undefined") return null
        val message = runCatching { json.decodeFromString(String.serializer(), text) }.getOrNull() ?: text
        return message.takeUnless { it.isEmpty() }
    }

    private fun stringLiteral(value: String): String =
        json.encodeToString(String.serializer(), value)

    /**
     * Escapes the two line separators JSON may leave raw.
     *
     * JSON encoders emit U+2028 and U+2029 unescaped. They are legal inside a JSON string but only
     * became legal in JavaScript source with ES2019, and the WebView shipped with Android 8 predates
     * that, so a script containing one would fail to parse.
     */
    private fun String.escapeLineSeparators(): String =
        replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")

    private inline fun withCallId(
        callId: String?,
        opName: String,
        build: (String) -> BridgeDecodeResult
    ): BridgeDecodeResult {
        val id = callId ?: return reject(null, "操作 $opName 缺少合法的 callId")
        return build(id)
    }

    private fun decoded(request: ImportWebRequest): BridgeDecodeResult =
        BridgeDecodeResult.Decoded(request)

    private fun reject(callId: String?, message: String): BridgeDecodeResult.Rejected =
        BridgeDecodeResult.Rejected(callId, message)

    private fun JsonElement?.textField(key: String): String? {
        val element = (this as? JsonObject)?.get(key) ?: return null
        return runCatching { element.jsonPrimitive.content }.getOrNull()
    }

    private fun <T> JsonElement?.decodeWith(serializer: KSerializer<T>): T? {
        if (this == null || this is JsonNull) return null
        return runCatching { json.decodeFromJsonElement(serializer, this) }.getOrNull()
    }

    private enum class BridgeOp(val wireName: String) {
        ALERT("alert"),
        PROMPT("prompt"),
        CHOOSE("choose"),
        COMMIT("commit"),
        TOAST("toast"),
        FINISH("finish")
    }

    private val OP_BY_NAME: Map<String, BridgeOp> =
        BridgeOp.entries.associateBy { op -> op.wireName }
}

/**
 * Envelope every message from the page shares.
 *
 * Fields are nullable so a malformed message is reported as a missing field instead of a decoder
 * exception, and unknown keys are ignored so a newer script can send extra data without an older
 * build refusing it.
 */
@Serializable
internal data class BridgeEnvelopeDto(
    val op: String? = null,
    val callId: String? = null,
    val data: JsonElement? = null
)

@Serializable
internal data class AlertRequestDto(
    val title: String? = null,
    val message: String? = null,
    val confirmText: String? = null
)

@Serializable
internal data class PromptRequestDto(
    val title: String? = null,
    val hint: String? = null,
    val initialValue: String? = null,
    val validate: String? = null
)

@Serializable
internal data class ChooseRequestDto(
    val title: String? = null,
    val options: List<String>? = null,
    val defaultIndex: Int? = null
)
