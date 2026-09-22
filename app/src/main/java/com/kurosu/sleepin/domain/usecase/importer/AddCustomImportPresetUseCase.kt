package com.kurosu.sleepin.domain.usecase.importer

import com.kurosu.sleepin.domain.model.importer.CustomImportPresetDraft
import com.kurosu.sleepin.domain.model.importer.ImportPresetAddResult
import com.kurosu.sleepin.domain.repository.ImportPresetRepository

/**
 * Validates and stores a preset the user added themselves.
 *
 * This is the only place a custom preset enters the app, so every rule about what one may contain
 * lives here. The storage layer stores what it is given; the form asks for it; neither decides what
 * is acceptable.
 *
 * The rules exist because a stored preset is a promise: the user will pick it later, in a browser,
 * and expect it to work. A blank name makes the list unusable, a missing start URL means the preset
 * opens nothing, and a script larger than a real adapter is a sign that something other than a script
 * was pasted in.
 */
class AddCustomImportPresetUseCase(
    private val repository: ImportPresetRepository
) {

    /**
     * @return [ImportPresetAddResult.Added] with the stored preset, or a rejection/failure carrying a
     *   message the screen can show as-is.
     */
    suspend operator fun invoke(draft: CustomImportPresetDraft): ImportPresetAddResult {
        rejectionMessage(draft)?.let { message -> return ImportPresetAddResult.Rejected(message) }
        return repository.addCustomPreset(draft)
    }

    private fun rejectionMessage(draft: CustomImportPresetDraft): String? {
        val name = draft.name.trim()
        val initial = draft.initial.trim().uppercase()
        val startUrl = draft.startUrl.trim()

        if (name.isEmpty()) return "请填写方案名称。"
        if (name.length > MAX_NAME_LENGTH) return "方案名称最多 $MAX_NAME_LENGTH 个字。"

        if (!INITIAL_PATTERN.matches(initial)) return "首字母应为 A-Z 中的一个字母。"

        if (startUrl.isEmpty()) return "请填写起始网址。"
        if (startUrl.length > MAX_URL_LENGTH) return "起始网址过长。"
        // Required rather than assumed: the scheme decides whether the portal is reached over a secure
        // connection, and silently defaulting it would hide a mistake in what the user pasted.
        if (!startUrl.startsWith("http://") && !startUrl.startsWith("https://")) {
            return "起始网址应以 http:// 或 https:// 开头。"
        }

        if (draft.description.trim().length > MAX_DESCRIPTION_LENGTH) {
            return "方案说明最多 $MAX_DESCRIPTION_LENGTH 个字。"
        }
        if (draft.maintainer.trim().length > MAX_MAINTAINER_LENGTH) {
            return "维护者最多 $MAX_MAINTAINER_LENGTH 个字。"
        }

        if (draft.scriptText.isBlank()) return "请先选择或粘贴脚本内容。"
        if (draft.scriptText.length > MAX_SCRIPT_CHARS) {
            return "脚本过大（上限 ${MAX_SCRIPT_CHARS / 1024} KB），请确认选择的是导入脚本本身。"
        }

        return null
    }

    private companion object {
        const val MAX_NAME_LENGTH = 40
        const val MAX_URL_LENGTH = 500
        const val MAX_DESCRIPTION_LENGTH = 200
        const val MAX_MAINTAINER_LENGTH = 40

        /**
         * Upper bound on a script, in characters.
         *
         * Real adapters are tens of kilobytes; the limit is here to catch a whole page or a file that
         * is not a script at all, not to restrict how an adapter may be written.
         */
        const val MAX_SCRIPT_CHARS = 512 * 1024

        val INITIAL_PATTERN = Regex("^[A-Z]$")
    }
}
