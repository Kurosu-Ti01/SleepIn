package com.kurosu.sleepin.ui.screen.importer.web

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Static checks that keep the bundled demo script and the bridge in step.
 *
 * The script is shipped inside the APK and is the only end-to-end path that needs no portal account,
 * so it is worth catching a mismatch here rather than discovering it on a device.
 */
class BundledDemoScriptContractTest {

    @Test
    fun demoScript_callsExactlyTheBridgeMethodsTheRuntimeProvides() {
        val called = Regex("sleepinBridge(?:Promise)?\\.(\\w+)\\s*\\(")
            .findAll(demoScript())
            .map { match -> match.groupValues[1] }
            .toSet()

        // Both directions of the contract at once: nothing unknown is called, and every method exists
        // to be called — which also makes this the record of what the demo exercises.
        assertEquals(
            setOf("toast", "finish", "alert", "prompt", "choose", "commit"),
            called
        )
    }

    @Test
    fun demoScript_declaresItsValidatorAsAFunction() {
        val names = Regex("validate:\\s*\"(\\w+)\"")
            .findAll(demoScript())
            .map { match -> match.groupValues[1] }
            .toList()

        assertTrue("the demo should exercise prompt validation", names.isNotEmpty())
        names.forEach { name ->
            // The host resolves a validator as `window[name]`, which only a function declaration
            // satisfies: `const name = () => …` would live in the global lexical environment instead
            // and never be found.
            assertTrue(
                "validator '$name' must be declared with `function $name(`",
                demoScript().contains("function $name(")
            )
        }
    }

    @Test
    fun demoScript_statesThePayloadSchemaVersion() {
        assertTrue(demoScript().contains("schema: 1"))
    }

    private fun demoScript(): String {
        val relativePath = "src/main/assets/import-scripts/presets/generic-blank/blank.js"
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
        }
        error("could not locate $relativePath starting from ${File("").absolutePath}")
    }
}
