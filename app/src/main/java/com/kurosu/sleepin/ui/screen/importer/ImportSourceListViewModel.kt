package com.kurosu.sleepin.ui.screen.importer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kurosu.sleepin.domain.model.importer.BUILT_IN_IMPORT_SOURCE_URL
import com.kurosu.sleepin.domain.model.importer.ImportSourceImportResult
import com.kurosu.sleepin.domain.model.importer.ImportLocalSourceDraft
import com.kurosu.sleepin.domain.model.importer.ImportSourceDraft
import com.kurosu.sleepin.domain.model.importer.LOCAL_SOURCE_KEY_PREFIX
import com.kurosu.sleepin.domain.usecase.importer.ChangeImportSourceUrlUseCase
import com.kurosu.sleepin.domain.usecase.importer.ForgetImportSourceUseCase
import com.kurosu.sleepin.domain.usecase.importer.GetRegisteredImportSourcesUseCase
import com.kurosu.sleepin.domain.usecase.importer.ImportSourceUseCase
import com.kurosu.sleepin.domain.usecase.importer.RenameImportSourceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The label a source row shows when the user has not named it.
 *
 * Derived rather than a fixed word, because "自建源" tells the user nothing when they have two of them.
 * A link is named by its host; a directory by its last path segment, which is usually the folder the user
 * copied the source into.
 */
internal fun defaultImportSourceLabel(sourceKey: String): String {
    if (sourceKey.startsWith(LOCAL_SOURCE_KEY_PREFIX)) {
        // SAF encodes the document id's separators ("primary%3ASleepIn"), so they are decoded before the
        // last segment is taken; otherwise every directory would be named after the volume.
        val decoded = sourceKey.removePrefix(LOCAL_SOURCE_KEY_PREFIX)
            .replace("%2F", "/", ignoreCase = true)
            .replace("%3A", ":", ignoreCase = true)
        val segment = decoded.substringAfterLast('/').substringAfterLast(':')
        return segment.ifBlank { "本地目录" }
    }

    // Only the host: the full path is shown underneath, and a host is what distinguishes two mirrors.
    // java.net.URI rather than android.net.Uri so this stays testable on the JVM.
    val host = runCatching { java.net.URI(sourceKey).host }.getOrNull()
    return host?.takeIf { it.isNotBlank() } ?: sourceKey
}

/**
 * The row title for one source.
 *
 * The built-in source is always called the built-in source: it has a fixed address the user cannot change,
 * so a name of their own would only be a second thing that could disagree with reality.
 */
internal fun importSourceLabel(sourceKey: String, displayName: String?): String {
    if (sourceKey == BUILT_IN_IMPORT_SOURCE_URL) return "内置源"
    val named = displayName?.trim()
    if (!named.isNullOrEmpty()) return named
    return defaultImportSourceLabel(sourceKey)
}

/** One row of the source list. */
data class ImportSourceRowUi(
    val sourceKey: String,
    /** User-given name, or null to fall back to [defaultImportSourceLabel]. */
    val displayName: String?,
    val indexVersion: Int,
    val presetCount: Int,
    /**
     * The source the app refreshes on its own and whose address is built in.
     *
     * It cannot be renamed, re-pointed or deleted: every one of those is a way to break the one source the
     * app is guaranteed to have, and there would be no "restore default" to undo it with.
     */
    val isManaged: Boolean,
    /** A directory on the device rather than a link. */
    val isLocal: Boolean
) {
    /** What the row shows: the user's name if they gave one, otherwise something derived. */
    val title: String get() = importSourceLabel(sourceKey, displayName)
}

/** The address editor, open for exactly one source. */
data class ImportSourceEditorUi(
    val sourceKey: String,
    val urlText: String,
    val isSaving: Boolean = false,
    val error: String? = null
)

/** The rename editor, open for exactly one source. */
data class ImportSourceRenameUi(
    val sourceKey: String,
    val nameText: String,
    val isSaving: Boolean = false
)

/** The "add a source from a link" dialog. */
data class ImportSourceAddRemoteUi(
    val urlText: String = "",
    /** Whether a cached family may be reused; see [ImportSourceDraft]. */
    val reuseLocalFamilies: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null
)

/** The "add a source from a directory" confirmation, shown once a directory was picked. */
data class ImportSourceAddLocalUi(
    val rootUri: String,
    val reuseLocalFamilies: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null
)

/** State of the source list page. */
data class ImportSourceListUiState(
    val isLoading: Boolean = true,
    val sources: List<ImportSourceRowUi> = emptyList(),
    val editor: ImportSourceEditorUi? = null,
    /** The row being renamed, if the rename dialog is open. */
    val rename: ImportSourceRenameUi? = null,
    /** The add-from-link dialog, when it is open. */
    val addRemote: ImportSourceAddRemoteUi? = null,
    /** The add-from-directory confirmation, when a directory has been picked. */
    val addLocal: ImportSourceAddLocalUi? = null,
    /** The row the user asked to delete, waiting for confirmation. */
    val pendingDelete: ImportSourceRowUi? = null,
    val message: String? = null
)

/**
 * Backs the list of preset sources.
 *
 * Its own screen rather than a section of settings: every row carries actions of its own, one of them
 * destructive, and the addresses are long enough to need the width.
 */
class ImportSourceListViewModel(
    private val importSourceUseCase: ImportSourceUseCase,
    private val getRegisteredImportSourcesUseCase: GetRegisteredImportSourcesUseCase,
    private val changeImportSourceUrlUseCase: ChangeImportSourceUrlUseCase,
    private val forgetImportSourceUseCase: ForgetImportSourceUseCase,
    private val renameImportSourceUseCase: RenameImportSourceUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportSourceListUiState())
    val uiState: StateFlow<ImportSourceListUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun onEditRequested(row: ImportSourceRowUi) {
        // The built-in source has no editor: its address is the one thing the app must be able to rely on.
        if (row.isManaged) return
        _uiState.update {
            it.copy(editor = ImportSourceEditorUi(sourceKey = row.sourceKey, urlText = row.sourceKey))
        }
    }

    fun onUrlChange(text: String) {
        _uiState.update { state ->
            state.copy(editor = state.editor?.copy(urlText = text, error = null))
        }
    }

    fun dismissEditor() {
        _uiState.update { it.copy(editor = null) }
    }

    /**
     * Imports the typed address and, only if that works, forgets the one it replaces.
     *
     * The editor stays open on failure so a typo can be corrected instead of typed from scratch.
     */
    fun confirmEditor() {
        val editor = _uiState.value.editor ?: return
        if (editor.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(editor = editor.copy(isSaving = true, error = null)) }
            val result = changeImportSourceUrlUseCase(
                previousSourceKey = editor.sourceKey,
                newIndexUrl = editor.urlText
            )

            when (result) {
                is ImportSourceImportResult.Rejected -> _uiState.update {
                    it.copy(editor = editor.copy(isSaving = false, error = result.message))
                }

                is ImportSourceImportResult.Failed -> _uiState.update {
                    it.copy(editor = editor.copy(isSaving = false, error = result.message))
                }

                is ImportSourceImportResult.Imported,
                is ImportSourceImportResult.Unchanged -> {
                    _uiState.update {
                        it.copy(editor = null, message = "已更新方案源：${editor.urlText.trim()}")
                    }
                    reload()
                }
            }
        }
    }

    fun onDeleteRequested(row: ImportSourceRowUi) {
        _uiState.update { it.copy(pendingDelete = row) }
    }

    /** Opens the rename dialog, prefilled with the current name or the derived label. */
    fun onRenameRequested(row: ImportSourceRowUi) {
        // Named built-ins would be a second thing that can disagree with the fixed address.
        if (row.isManaged) return
        _uiState.update {
            it.copy(rename = ImportSourceRenameUi(sourceKey = row.sourceKey, nameText = row.displayName.orEmpty()))
        }
    }

    fun onRenameChange(text: String) {
        _uiState.update { state -> state.copy(rename = state.rename?.copy(nameText = text)) }
    }

    fun dismissRename() {
        _uiState.update { it.copy(rename = null) }
    }

    /**
     * Stores the typed name.
     *
     * An empty field means "use the derived label again", not "cancel" — the dialog says so, and the
     * repository treats blank as clearing.
     */
    fun confirmRename() {
        val rename = _uiState.value.rename ?: return
        if (rename.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(rename = rename.copy(isSaving = true)) }
            val saved = renameImportSourceUseCase(rename.sourceKey, rename.nameText)
            _uiState.update {
                it.copy(rename = null, message = if (saved) "已重命名" else "重命名失败，请重试。")
            }
            reload()
        }
    }

    fun dismissDelete() {
        _uiState.update { it.copy(pendingDelete = null) }
    }

    fun confirmDelete() {
        val row = _uiState.value.pendingDelete ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(pendingDelete = null) }
            val removed = forgetImportSourceUseCase(row.sourceKey)
            _uiState.update {
                it.copy(message = if (removed) "已删除方案源" else "删除失败，请重试。")
            }
            reload()
        }
    }

    // ---------------------------------------------------------------- 添加源

    fun onAddRemoteRequested() {
        _uiState.update { it.copy(addRemote = ImportSourceAddRemoteUi()) }
    }

    fun onAddUrlChange(text: String) {
        _uiState.update { state -> state.copy(addRemote = state.addRemote?.copy(urlText = text, error = null)) }
    }

    fun onAddReuseLocalFamiliesChange(reuse: Boolean) {
        _uiState.update { state ->
            state.copy(
                addRemote = state.addRemote?.copy(reuseLocalFamilies = reuse),
                addLocal = state.addLocal?.copy(reuseLocalFamilies = reuse)
            )
        }
    }

    fun dismissAddRemote() {
        _uiState.update { it.copy(addRemote = null) }
    }

    /**
     * Imports the typed address as a new source.
     *
     * The dialog stays open on failure so a typo can be corrected; a source that is already registered
     * comes back as "no change" rather than as an error, and the list is re-read either way.
     */
    fun confirmAddRemote() {
        val add = _uiState.value.addRemote ?: return
        if (add.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(addRemote = add.copy(isSaving = true, error = null)) }
            val result = importSourceUseCase(
                ImportSourceDraft(
                    indexUrl = add.urlText.trim(),
                    reuseLocalFamilies = add.reuseLocalFamilies
                )
            )
            when (result) {
                is ImportSourceImportResult.Rejected -> _uiState.update {
                    it.copy(addRemote = add.copy(isSaving = false, error = result.message))
                }

                is ImportSourceImportResult.Failed -> _uiState.update {
                    it.copy(addRemote = add.copy(isSaving = false, error = result.message))
                }

                is ImportSourceImportResult.Imported -> {
                    _uiState.update {
                        it.copy(addRemote = null, message = "已添加方案源，${result.presetCount} 个方案")
                    }
                    reload()
                }

                is ImportSourceImportResult.Unchanged -> {
                    _uiState.update {
                        it.copy(addRemote = null, message = "这个源已在列表里，内容没有变化")
                    }
                    reload()
                }
            }
        }
    }

    /** Opens the confirmation for a directory the caller just picked. */
    fun onLocalDirectoryPicked(rootUri: String) {
        _uiState.update { it.copy(addLocal = ImportSourceAddLocalUi(rootUri = rootUri)) }
    }

    fun dismissAddLocal() {
        _uiState.update { it.copy(addLocal = null) }
    }

    fun confirmAddLocal() {
        val add = _uiState.value.addLocal ?: return
        if (add.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(addLocal = add.copy(isSaving = true, error = null)) }
            val result = importSourceUseCase.importFromDirectory(
                ImportLocalSourceDraft(rootUri = add.rootUri, reuseLocalFamilies = add.reuseLocalFamilies)
            )
            when (result) {
                is ImportSourceImportResult.Rejected -> _uiState.update {
                    it.copy(addLocal = add.copy(isSaving = false, error = result.message))
                }

                is ImportSourceImportResult.Failed -> _uiState.update {
                    it.copy(addLocal = add.copy(isSaving = false, error = result.message))
                }

                is ImportSourceImportResult.Imported -> {
                    _uiState.update {
                        it.copy(addLocal = null, message = "已添加本地源，${result.presetCount} 个方案")
                    }
                    reload()
                }

                is ImportSourceImportResult.Unchanged -> {
                    _uiState.update {
                        it.copy(addLocal = null, message = "这个目录已在列表里，内容没有变化")
                    }
                    reload()
                }
            }
        }
    }

    /**
     * Re-reads the inventory, with the built-in source first.
     *
     * The built-in row is built from the constant rather than from the inventory, so it is listed even
     * before anything has been imported. It is read-only, so there is nothing to keep in sync with settings.
     */
    private fun reload() {
        viewModelScope.launch {
            val registered = getRegisteredImportSourcesUseCase()

            val rows = buildList {
                val managed = registered.firstOrNull { source -> source.sourceKey == BUILT_IN_IMPORT_SOURCE_URL }
                add(
                    ImportSourceRowUi(
                        sourceKey = BUILT_IN_IMPORT_SOURCE_URL,
                        displayName = null,
                        indexVersion = managed?.indexVersion ?: 0,
                        presetCount = managed?.presetCount ?: 0,
                        isManaged = true,
                        isLocal = false
                    )
                )
                registered
                    .filter { source -> source.sourceKey != BUILT_IN_IMPORT_SOURCE_URL }
                    .forEach { source ->
                        add(
                            ImportSourceRowUi(
                                sourceKey = source.sourceKey,
                                displayName = source.displayName,
                                indexVersion = source.indexVersion,
                                presetCount = source.presetCount,
                                isManaged = false,
                                isLocal = source.isLocal
                            )
                        )
                    }
            }

            _uiState.update { it.copy(isLoading = false, sources = rows) }
        }
    }

    companion object {
        fun factory(
            importSourceUseCase: ImportSourceUseCase,
            getRegisteredImportSourcesUseCase: GetRegisteredImportSourcesUseCase,
            changeImportSourceUrlUseCase: ChangeImportSourceUrlUseCase,
            forgetImportSourceUseCase: ForgetImportSourceUseCase,
            renameImportSourceUseCase: RenameImportSourceUseCase
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ImportSourceListViewModel(
                    importSourceUseCase = importSourceUseCase,
                    getRegisteredImportSourcesUseCase = getRegisteredImportSourcesUseCase,
                    changeImportSourceUrlUseCase = changeImportSourceUrlUseCase,
                    forgetImportSourceUseCase = forgetImportSourceUseCase,
                    renameImportSourceUseCase = renameImportSourceUseCase
                ) as T
            }
        }
    }
}
