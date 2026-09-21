package com.kurosu.sleepin.ui.screen.importer.web

import android.webkit.JavascriptInterface
import java.lang.reflect.Modifier
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the bridge wire format.
 *
 * The interesting cases are the hostile ones: this is the only place where what a web page sends turns
 * into something the app acts on, and everything reaching `evaluateJavascript` is assembled here.
 */
class SleepInBridgeProtocolTest {

    private val json = Json

    @Test
    fun decode_readsAnAlertRequest() {
        val result = decode(
            """{ "op": "alert", "callId": "c1", "data": { "title": "提醒", "message": "内容", "confirmText": "好" } }"""
        )

        val request = result as BridgeDecodeResult.Decoded
        assertEquals(
            ImportWebRequest.Alert(callId = "c1", title = "提醒", message = "内容", confirmText = "好"),
            request.request
        )
    }

    @Test
    fun decode_fillsInMissingAlertFields() {
        val result = decode("""{ "op": "alert", "callId": "c1", "data": {} }""")

        val alert = (result as BridgeDecodeResult.Decoded).request as ImportWebRequest.Alert
        assertEquals("提示", alert.title)
        assertEquals("", alert.message)
        assertEquals("确定", alert.confirmText)
    }

    @Test
    fun decode_readsAPromptAndItsValidator() {
        val result = decode(
            """{ "op": "prompt", "callId": "c2", "data": { "title": "学年", "hint": "提示", "initialValue": "2024", "validate": "validateYear" } }"""
        )

        val prompt = (result as BridgeDecodeResult.Decoded).request as ImportWebRequest.Prompt
        assertEquals("c2", prompt.callId)
        assertEquals("2024", prompt.initialValue)
        assertEquals("validateYear", prompt.validateName)
    }

    @Test
    fun decode_treatsABlankValidatorAsNoValidation() {
        val result = decode("""{ "op": "prompt", "callId": "c2", "data": { "validate": "   " } }""")

        val prompt = (result as BridgeDecodeResult.Decoded).request as ImportWebRequest.Prompt
        assertNull(prompt.validateName)
    }

    @Test
    fun decode_clampsAChoiceIndexThatIsOutOfRange() {
        val below = decode("""{ "op": "choose", "callId": "c3", "data": { "options": ["a", "b"], "defaultIndex": -5 } }""")
        val above = decode("""{ "op": "choose", "callId": "c3", "data": { "options": ["a", "b"], "defaultIndex": 9 } }""")

        assertEquals(0, ((below as BridgeDecodeResult.Decoded).request as ImportWebRequest.Choose).defaultIndex)
        assertEquals(1, ((above as BridgeDecodeResult.Decoded).request as ImportWebRequest.Choose).defaultIndex)
    }

    @Test
    fun decode_rejectsAChoiceWithoutUsableOptions() {
        val empty = decode("""{ "op": "choose", "callId": "c3", "data": { "options": [] } }""")
        val blanked = decode("""{ "op": "choose", "callId": "c3", "data": { "options": ["  ", " "] } }""")

        assertTrue(empty is BridgeDecodeResult.Rejected)
        assertTrue(blanked is BridgeDecodeResult.Rejected)
    }

    @Test
    fun decode_dropsBlankOptionLabels() {
        val result = decode(
            """{ "op": "choose", "callId": "c3", "data": { "options": ["甲", "  ", " 乙 "], "defaultIndex": 2 } }"""
        )

        val choose = (result as BridgeDecodeResult.Decoded).request as ImportWebRequest.Choose
        assertEquals(listOf("甲", "乙"), choose.options)
        // Clamped against the surviving options, so the index the script receives still points at the
        // one it asked for.
        assertEquals(1, choose.defaultIndex)
    }

    @Test
    fun decode_rejectsAChoiceWithTooManyOptions() {
        val options = (1..SleepInBridgeProtocol.MAX_CHOOSE_OPTIONS + 1).joinToString(",") { index -> "\"$index\"" }

        val result = decode("""{ "op": "choose", "callId": "c3", "data": { "options": [$options] } }""")

        assertTrue(result is BridgeDecodeResult.Rejected)
    }

    @Test
    fun decode_keepsTheCommitPayloadIntact() {
        val data = """{ "schema": 1, "courses": [ { "name": "高等数学", "sessions": [ { "day": 1 } ] } ] }"""

        val result = decode("""{ "op": "commit", "callId": "c4", "data": $data }""")

        val commit = (result as BridgeDecodeResult.Decoded).request as ImportWebRequest.Commit
        // The payload is handed on as JSON text so it goes through the same validation as any other
        // import; re-encoding must not lose or rename anything in it.
        assertEquals(json.parseToJsonElement(data), json.parseToJsonElement(commit.payloadJson))
    }

    @Test
    fun decode_rejectsACommitWithoutPayload() {
        val missing = decode("""{ "op": "commit", "callId": "c4" }""")
        val nulled = decode("""{ "op": "commit", "callId": "c4", "data": null }""")

        assertTrue(missing is BridgeDecodeResult.Rejected)
        assertTrue(nulled is BridgeDecodeResult.Rejected)
    }

    @Test
    fun decode_readsTheFireAndForgetCalls() {
        val toast = decode("""{ "op": "toast", "callId": "c5", "data": { "message": "正在读取" } }""")
        val finish = decode("""{ "op": "finish", "callId": "c6", "data": {} }""")

        assertEquals(
            ImportWebRequest.Toast(callId = "c5", message = "正在读取"),
            (toast as BridgeDecodeResult.Decoded).request
        )
        assertEquals(
            ImportWebRequest.Finish(callId = "c6"),
            (finish as BridgeDecodeResult.Decoded).request
        )
    }

    @Test
    fun decode_rejectsAnUnknownOperationButKeepsTheCallId() {
        val result = decode("""{ "op": "eval", "callId": "c7", "data": {} }""")

        // The id is well formed, so the page can be told why its request failed instead of hanging.
        assertEquals("c7", (result as BridgeDecodeResult.Rejected).callId)
    }

    @Test
    fun decode_rejectsACallIdThatCouldEscapeItsStringLiteral() {
        val result = decode("""{ "op": "alert", "callId": "c1');alert(1);//", "data": {} }""")

        // Nothing can be settled without a usable id, so the reason has to reach the user instead of
        // being embedded in a script.
        val rejected = result as BridgeDecodeResult.Rejected
        assertNull(rejected.callId)
    }

    @Test
    fun decode_rejectsAnAsyncRequestWithoutACallId() {
        val result = decode("""{ "op": "alert", "data": {} }""")

        assertNull((result as BridgeDecodeResult.Rejected).callId)
    }

    @Test
    fun decode_rejectsMalformedJson() {
        val result = decode("{ not json")

        assertTrue(result is BridgeDecodeResult.Rejected)
    }

    @Test
    fun decode_rejectsAPayloadOfTheWrongShape() {
        val result = decode("""{ "op": "alert", "callId": "c1", "data": "just text" }""")

        assertTrue(result is BridgeDecodeResult.Rejected)
    }

    @Test
    fun isValidCallId_acceptsWhatTheRuntimeGenerates() {
        assertTrue(SleepInBridgeProtocol.isValidCallId("c1"))
        assertTrue(SleepInBridgeProtocol.isValidCallId("call-42_x"))
    }

    @Test
    fun isValidCallId_rejectsAnythingElse() {
        assertFalse(SleepInBridgeProtocol.isValidCallId(""))
        assertFalse(SleepInBridgeProtocol.isValidCallId("c1'"))
        assertFalse(SleepInBridgeProtocol.isValidCallId("c 1"))
        assertFalse(SleepInBridgeProtocol.isValidCallId("a".repeat(65)))
    }

    @Test
    fun buildResolveScript_producesAStatementWithTheResultAsALiteral() {
        assertEquals(
            "__sleepinBridgeResolve(\"c1\", true, \"已导入\")",
            SleepInBridgeProtocol.buildResolveScript(
                callId = "c1",
                ok = true,
                resultLiteral = SleepInBridgeProtocol.stringResultLiteral("已导入")
            )
        )
        assertEquals(
            "__sleepinBridgeResolve(\"c1\", true, null)",
            SleepInBridgeProtocol.buildResolveScript(callId = "c1", ok = true, resultLiteral = null)
        )
        assertEquals(
            "__sleepinBridgeResolve(\"c1\", true, true)",
            SleepInBridgeProtocol.buildResolveScript(
                callId = "c1",
                ok = true,
                resultLiteral = SleepInBridgeProtocol.booleanLiteral(true)
            )
        )
    }

    @Test
    fun buildResolveScript_escapesALineSeparatorThatWouldBreakParsing() {
        val script = SleepInBridgeProtocol.buildResolveScript(
            callId = "c1",
            ok = false,
            resultLiteral = SleepInBridgeProtocol.stringResultLiteral("a\u2028b")
        )

        // Raw U+2028 is only legal in JavaScript source from ES2019, which the WebView on Android 8
        // predates, so it has to be emitted as an escape.
        assertTrue(script.contains("\\u2028"))
        assertFalse(script.contains("\u2028"))
    }

    @Test
    fun buildValidateScript_escapesTheInput() {
        val script = SleepInBridgeProtocol.buildValidateScript("validateYear", "he said \"hi\"\n")

        assertTrue(script.startsWith("__sleepinBridgeValidate(\"validateYear\","))
        assertTrue(script.contains("\\\"hi\\\""))
        assertFalse(script.contains("\n"))
    }

    @Test
    fun parseValidateResult_readsWhatThePageReturned() {
        // `evaluateJavascript` serializes the return value as JSON.
        assertEquals("请输入四位数字", SleepInBridgeProtocol.parseValidateResult("\"请输入四位数字\""))
        assertNull(SleepInBridgeProtocol.parseValidateResult("null"))
        assertNull(SleepInBridgeProtocol.parseValidateResult("\"\""))
        assertNull(SleepInBridgeProtocol.parseValidateResult(""))
        assertNull(SleepInBridgeProtocol.parseValidateResult(null))
        assertNull(SleepInBridgeProtocol.parseValidateResult("undefined"))
        // Anything unexpected is still reported rather than silently accepted.
        assertEquals("oops", SleepInBridgeProtocol.parseValidateResult("oops"))
        assertEquals("123", SleepInBridgeProtocol.parseValidateResult("123"))
        assertEquals("{}", SleepInBridgeProtocol.parseValidateResult("{}"))
    }

    @Test
    fun decode_refusesAMessageTooLargeToParseSafely() {
        val oversized = "x".repeat(SleepInBridgeProtocol.MAX_RAW_MESSAGE_CHARS + 1)

        assertTrue(SleepInBridgeProtocol.decode(oversized) is BridgeDecodeResult.Rejected)
    }

    @Test
    fun decoder_recognisesEveryOperationTheRuntimeSends() {
        // The runtime and the decoder are two halves of one contract, written in two languages, with
        // nothing but these strings tying them together.
        val messages = mapOf(
            "alert" to """{ "title": "t", "message": "m" }""",
            "prompt" to """{ "title": "t" }""",
            "choose" to """{ "options": ["a"] }""",
            "commit" to """{ "schema": 1 }""",
            "toast" to """{ "message": "m" }""",
            "finish" to "{}"
        )

        messages.forEach { (op, data) ->
            val result = decode("""{ "op": "$op", "callId": "c1", "data": $data }""")
            assertTrue("decoder rejected '$op': $result", result is BridgeDecodeResult.Decoded)
            assertTrue(
                "runtime never sends '$op'",
                SleepInBridgeProtocol.RUNTIME_SCRIPT.contains("call('$op'")
            )
        }
    }

    @Test
    fun hostInterface_exposesTheMethodTheRuntimeCalls() {
        val method = SleepInBridgeHost::class.java
            .getDeclaredMethod(SleepInBridgeProtocol.HOST_METHOD, String::class.java)

        // A method without the annotation is invisible to JavaScript, and a renamed one breaks the
        // bridge silently: the script's promise would simply never settle.
        assertTrue(method.isAnnotationPresent(JavascriptInterface::class.java))
        assertTrue(Modifier.isPublic(method.modifiers))
    }

    @Test
    fun runtimeScript_installsBothGlobalObjectsOverTheNativeMethod() {
        val script = SleepInBridgeProtocol.RUNTIME_SCRIPT

        assertTrue(script.contains("window.${SleepInBridgeProtocol.READY_FLAG}"))
        assertTrue(script.contains("window.${SleepInBridgeProtocol.BRIDGE_GLOBAL}"))
        assertTrue(script.contains("window.${SleepInBridgeProtocol.PROMISE_GLOBAL}"))
        assertTrue(script.contains("window.${SleepInBridgeProtocol.RESOLVE_FUNCTION}"))
        assertTrue(script.contains("window.${SleepInBridgeProtocol.VALIDATE_FUNCTION}"))
        assertTrue(
            script.contains("${SleepInBridgeProtocol.HOST_INTERFACE}.${SleepInBridgeProtocol.HOST_METHOD}")
        )
    }

    @Test
    fun runtimeScript_neverUsesTemplateLiterals() {
        // The runtime is a Kotlin string, so a `$` in the JavaScript would be read as an interpolation
        // and either fail to compile or silently produce the wrong script.
        assertFalse(SleepInBridgeProtocol.RUNTIME_SCRIPT.contains("$"))
    }

    private fun decode(raw: String): BridgeDecodeResult = SleepInBridgeProtocol.decode(raw)
}
