package com.kurosu.sleepin.data.importer

import com.kurosu.sleepin.domain.model.importer.ImportPresetAssemblyMode
import com.kurosu.sleepin.domain.model.importer.ImportPresetGroup
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/** One source file referenced by a schema-v2 source index. */
@Serializable
internal data class ImportSourceFileDto(
    val path: String? = null,
    val sha256: String? = null
)

/** Family source files published by a schema-v2 source. */
@Serializable
internal data class ImportSourceFamilyDto(
    val id: String? = null,
    val metadata: ImportSourceFileDto? = null,
    val adapter: ImportSourceFileDto? = null
)

/** One preset declaration that will be assembled locally. */
@Serializable
internal data class ImportSourcePresetDto(
    val id: String? = null,
    val name: String? = null,
    val schoolId: String? = null,
    val group: String? = null,
    val initial: String? = null,
    val startUrl: String? = null,
    val description: String? = null,
    val maintainer: String? = null,
    val assembly: String? = null,
    val family: String? = null,
    val config: JsonObject? = null,
    val override: ImportSourceFileDto? = null,
    val rewrite: ImportSourceFileDto? = null
)

/** Wire shape of a source index that contains sources, not generated scripts. */
@Serializable
internal data class ImportSourceIndexDto(
    val schemaVersion: Int? = null,
    val indexVersion: Int? = null,
    val runner: ImportSourceFileDto? = null,
    val families: List<ImportSourceFamilyDto>? = null,
    val schools: List<ImportPresetSchoolDto>? = null,
    val presets: List<ImportSourcePresetDto>? = null
)

data class ImportSourceFile(
    val path: String,
    val sha256: String
)

data class ImportSourceFamily(
    val id: String,
    val metadata: ImportSourceFile,
    val adapter: ImportSourceFile
)

data class ImportSourcePreset(
    val id: String,
    val name: String,
    val schoolId: String?,
    val schoolName: String?,
    val schoolCode: String?,
    val schoolAliases: List<String>,
    val group: ImportPresetGroup,
    val initial: String,
    val startUrl: String?,
    val description: String,
    val maintainer: String,
    val assembly: ImportPresetAssemblyMode,
    val familyId: String?,
    val config: JsonObject,
    val override: ImportSourceFile?,
    val rewrite: ImportSourceFile?
)

data class ImportSourceIndex(
    val indexVersion: Int,
    val runner: ImportSourceFile,
    val families: List<ImportSourceFamily>,
    val presets: List<ImportSourcePreset>
)

/** Parses and validates schema-v2 source indexes before any source file is downloaded. */
class ImportSourceIndexParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(rawJson: String): Result<ImportSourceIndex> = runCatching {
        val dto = json.decodeFromString(ImportSourceIndexDto.serializer(), rawJson)
        require(dto.schemaVersion == 2) { "源索引 schemaVersion 必须是 2" }
        val indexVersion = dto.indexVersion ?: error("源索引缺少 indexVersion")
        require(indexVersion >= 1) { "源索引 indexVersion 无效" }

        val runner = dto.runner?.toFile("runner") ?: error("源索引缺少 runner")
        val familyDtos = dto.families.orEmpty()
        val familyIds = mutableSetOf<String>()
        val schools = dto.schools.orEmpty().associate { school ->
            val id = school.id?.trim().orEmpty()
            require(isValidPresetId(id)) { "学校 id 无效：$id" }
            id to school
        }
        require(schools.size == dto.schools.orEmpty().size) { "源索引存在重复学校 id" }

        val families = familyDtos.mapIndexed { index, family ->
            val id = family.id?.trim().orEmpty()
            require(isValidPresetId(id)) { "families[$index].id 无效" }
            require(familyIds.add(id)) { "源索引存在重复族 id：$id" }
            ImportSourceFamily(
                id = id,
                metadata = family.metadata?.toFile("families[$index].metadata")
                    ?: error("families[$index] 缺少 metadata"),
                adapter = family.adapter?.toFile("families[$index].adapter")
                    ?: error("families[$index] 缺少 adapter")
            )
        }
        val familySet: Set<String> = families.mapTo(mutableSetOf()) { it.id }

        val presetIds = mutableSetOf<String>()
        val presets = dto.presets.orEmpty().mapIndexed { index, preset ->
            val id = preset.id?.trim().orEmpty()
            require(isValidPresetId(id)) { "presets[$index].id 无效" }
            require(presetIds.add(id)) { "源索引存在重复方案 id：$id" }
            val name = preset.name?.trim().orEmpty()
            require(name.isNotEmpty()) { "presets[$index].name 不能为空" }
            val group = ImportPresetGroup.entries.firstOrNull {
                it.name == preset.group?.trim()?.uppercase()
            } ?: error("presets[$index].group 无效")
            val initial = preset.initial?.trim().orEmpty()
            require(Regex("^[A-Z]$").matches(initial)) { "presets[$index].initial 无效" }
            val schoolId = preset.schoolId?.trim()?.takeIf { it.isNotEmpty() }
            val school = schoolId?.let { id -> schools[id] }
            require(schoolId == null || school != null) { "presets[$index].schoolId 未在 schools 中声明" }
            val assembly = when (preset.assembly?.trim()?.lowercase()) {
                "family" -> ImportPresetAssemblyMode.FAMILY
                "override" -> ImportPresetAssemblyMode.OVERRIDE
                "rewrite" -> ImportPresetAssemblyMode.REWRITE
                else -> error("presets[$index].assembly 必须是 family、override 或 rewrite")
            }
            val familyId = preset.family?.trim()?.takeIf { it.isNotEmpty() }
            require(assembly == ImportPresetAssemblyMode.REWRITE || (familyId != null && familySet.contains(familyId))) {
                "presets[$index] 的 family 不存在"
            }
            val override = preset.override?.toFile("presets[$index].override")
            val rewrite = preset.rewrite?.toFile("presets[$index].rewrite")
            require(assembly != ImportPresetAssemblyMode.OVERRIDE || override != null) {
                "presets[$index] 的 override 形态缺少 override 文件"
            }
            require(assembly != ImportPresetAssemblyMode.REWRITE || rewrite != null) {
                "presets[$index] 的 rewrite 形态缺少 rewrite 文件"
            }
            ImportSourcePreset(
                id = id,
                name = name,
                schoolId = schoolId,
                schoolName = school?.name,
                schoolCode = school?.code,
                schoolAliases = school?.aliases.orEmpty(),
                group = group,
                initial = initial,
                startUrl = preset.startUrl?.trim()?.takeIf { it.isNotEmpty() },
                description = preset.description?.trim().orEmpty(),
                maintainer = preset.maintainer?.trim().orEmpty(),
                assembly = assembly,
                familyId = familyId,
                config = preset.config ?: buildJsonObject {},
                override = override,
                rewrite = rewrite
            )
        }

        ImportSourceIndex(indexVersion = indexVersion, runner = runner, families = families, presets = presets)
    }

    private fun ImportSourceFileDto.toFile(label: String): ImportSourceFile {
        val resolvedPath = path?.trim().orEmpty()
        require(isSafeRelativePath(resolvedPath)) { "$label.path 不是安全相对路径" }
        val hash = sha256?.trim()?.lowercase().orEmpty()
        require(Regex("^[0-9a-f]{64}$").matches(hash)) { "$label.sha256 无效" }
        return ImportSourceFile(path = resolvedPath, sha256 = hash)
    }
}
