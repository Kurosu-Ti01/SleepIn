package com.kurosu.sleepin.domain.usecase.importer

import com.kurosu.sleepin.domain.model.importer.CustomImportPresetDraft

/**
 * The rules a preset the user adds must satisfy, in one place.
 *
 * Shared by adding and by replacing, because the two accept exactly the same thing: a preset is a
 * promise that picking it later will work, and a promise is no weaker for having been made twice.
 */
internal object CustomImportPresetValidation {

    private const val MAX_NAME_LENGTH = 40
    private const val MAX_URL_LENGTH = 500
    private const val MAX_DESCRIPTION_LENGTH = 200
    private const val MAX_MAINTAINER_LENGTH = 40
    private const val MAX_SCHOOL_NAME_LENGTH = 80
    private const val MAX_SCHOOL_CODE_LENGTH = 32

    /**
     * Upper bound on a script, in characters.
     *
     * Real adapters are tens of kilobytes; the limit is here to catch a whole page or a file that is not
     * a script at all, not to restrict how an adapter may be written.
     */
    private const val MAX_SCRIPT_CHARS = 512 * 1024

    private val INITIAL_PATTERN = Regex("^[A-Z]$")
    private val SCHOOL_ID_PATTERN = Regex("^[a-z0-9][a-z0-9-]{1,63}$")

    /**
     * @return the message to show when [draft] cannot be stored, or null when it can.
     */
    fun rejectionMessage(draft: CustomImportPresetDraft): String? {
        val name = draft.name.trim()
        val initial = draft.initial.trim().uppercase()
        val startUrl = draft.startUrl.trim()

        if (name.isEmpty()) return "请填写方案名称。"
        if (name.length > MAX_NAME_LENGTH) return "方案名称最多 $MAX_NAME_LENGTH 个字。"

        if (!INITIAL_PATTERN.matches(initial)) return "首字母应为 A-Z 中的一个字母。"

        val schoolName = draft.schoolName.trim()
        if (schoolName.isEmpty()) return "请选择或填写学校名称。"
        if (schoolName.length > MAX_SCHOOL_NAME_LENGTH) return "学校名称最多 $MAX_SCHOOL_NAME_LENGTH 个字。"
        if (draft.schoolId != null && !SCHOOL_ID_PATTERN.matches(draft.schoolId)) {
            return "学校标识无效，请重新选择学校。"
        }
        if (draft.schoolCode.trim().length > MAX_SCHOOL_CODE_LENGTH) {
            return "学校代码最多 $MAX_SCHOOL_CODE_LENGTH 个字符。"
        }

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
}
