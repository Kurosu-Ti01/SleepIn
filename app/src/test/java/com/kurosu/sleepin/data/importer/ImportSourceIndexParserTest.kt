package com.kurosu.sleepin.data.importer

import com.kurosu.sleepin.domain.model.importer.ImportPresetAssemblyMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportSourceIndexParserTest {

    private val parser = ImportSourceIndexParser()
    private val hash = "a".repeat(64)

    @Test
    fun parse_acceptsFamilyOverrideAndRewriteDeclarations() {
        val result = parser.parse(
            """
            {
              "schemaVersion":2,
              "indexVersion":4,
              "runner":{"path":"lib/runner.js","sha256":"$hash"},
              "families":[{"id":"sample-family","metadata":{"path":"families/sample-family/family.json","sha256":"$hash"},"adapter":{"path":"families/sample-family/adapter.js","sha256":"$hash"}}],
              "schools":[{"id":"school-sample","name":"示例学校"}],
              "presets":[
                {"id":"sample-family-preset","name":"复用","schoolId":"school-sample","group":"UNDERGRADUATE","initial":"S","assembly":"family","family":"sample-family","config":{"mode":"a"},"description":"说明","maintainer":"tester"},
                {"id":"sample-override-preset","name":"覆盖","group":"GENERAL","initial":"O","assembly":"override","family":"sample-family","override":{"path":"presets/sample-override/override.js","sha256":"$hash"},"description":"说明","maintainer":"tester"},
                {"id":"sample-rewrite-preset","name":"重写","group":"GENERAL","initial":"R","assembly":"rewrite","rewrite":{"path":"presets/sample-rewrite/script.js","sha256":"$hash"},"description":"说明","maintainer":"tester"}
              ]
            }
            """.trimIndent()
        ).getOrThrow()

        assertEquals(4, result.indexVersion)
        assertEquals(3, result.presets.size)
        assertEquals(ImportPresetAssemblyMode.FAMILY, result.presets[0].assembly)
        assertEquals(ImportPresetAssemblyMode.OVERRIDE, result.presets[1].assembly)
        assertEquals(ImportPresetAssemblyMode.REWRITE, result.presets[2].assembly)
        assertEquals("示例学校", result.presets[0].schoolName)
    }

    @Test
    fun parse_rejectsUnsafeSourcePath() {
        val result = parser.parse(
            """
            {"schemaVersion":2,"indexVersion":1,
             "runner":{"path":"../runner.js","sha256":"$hash"},"families":[],"presets":[]}
            """.trimIndent()
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun parse_rejectsRewriteWithoutRewriteFile() {
        val result = parser.parse(
            """
            {"schemaVersion":2,"indexVersion":1,
             "runner":{"path":"runner.js","sha256":"$hash"},"families":[],
             "presets":[{"id":"rewrite-preset","name":"重写","group":"GENERAL","initial":"R","assembly":"rewrite","description":"说明","maintainer":"tester"}]}
            """.trimIndent()
        )

        assertTrue(result.isFailure)
    }
}
