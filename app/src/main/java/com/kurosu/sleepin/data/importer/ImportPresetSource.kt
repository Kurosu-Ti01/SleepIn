package com.kurosu.sleepin.data.importer

import android.content.res.AssetManager
import com.kurosu.sleepin.domain.model.importer.ImportPreset
import com.kurosu.sleepin.domain.model.importer.ImportPresetIndexParseResult
import com.kurosu.sleepin.domain.model.importer.ImportPresetSourceKind
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One place import presets can come from.
 *
 * The repository merges sources instead of knowing about each kind, so adding a source — a user's own
 * script folder, a downloaded index — is a matter of constructing it and adding it to the list, with
 * no change to the repository or the screens.
 *
 * Implementations must not throw for "unavailable" conditions: a source that cannot be read simply
 * reports nothing, so one broken source cannot take the whole list down with it.
 */
interface ImportPresetSource {

    val kind: ImportPresetSourceKind

    /** Presets this source currently provides; empty when its index is missing or unreadable. */
    suspend fun presets(): List<ImportPreset>

    /**
     * Text of the script at [scriptPath], or null when it is missing or the path is not acceptable.
     *
     * [scriptPath] comes from a parsed index, so it has already been validated; it is re-checked here
     * because this is the step that actually resolves it to a file.
     */
    suspend fun readScript(scriptPath: String): String?
}

/**
 * Presets packaged inside the APK.
 *
 * A bundled index that fails to parse is a packaging mistake, and the app degrades to "no bundled
 * presets" rather than crashing on launch: the user can still import a script of their own.
 */
class BundledImportPresetSource(
    private val assets: AssetManager,
    private val parser: ImportPresetIndexParser,
    private val indexAssetPath: String = "$ASSET_ROOT/index.json"
) : ImportPresetSource {

    override val kind: ImportPresetSourceKind = ImportPresetSourceKind.BUNDLED

    override suspend fun presets(): List<ImportPreset> = withContext(Dispatchers.IO) {
        val raw = readAsset(indexAssetPath) ?: return@withContext emptyList()
        when (val result = parser.parse(raw, kind)) {
            is ImportPresetIndexParseResult.Parsed -> result.index.presets
            is ImportPresetIndexParseResult.Invalid -> emptyList()
        }
    }

    override suspend fun readScript(scriptPath: String): String? = withContext(Dispatchers.IO) {
        if (!isSafeRelativePath(scriptPath)) return@withContext null
        readAsset("$ASSET_ROOT/$scriptPath")
    }

    private fun readAsset(path: String): String? = try {
        assets.open(path).bufferedReader().use { reader -> reader.readText() }
    } catch (_: IOException) {
        null
    }

    private companion object {
        const val ASSET_ROOT = "import-scripts"
    }
}
