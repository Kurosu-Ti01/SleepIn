package com.kurosu.sleepin.update

import com.kurosu.sleepin.domain.model.AppReleaseInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Debug-only switch that makes the update dialog render a sample release.
 *
 * Verifying the dialog otherwise requires a GitHub release newer than the installed `VERSION_NAME`
 * plus a cleared `dismissedUpdateVersion`, which also wipes local data. This simulator instead holds
 * the payload in memory:
 *
 * - Nothing is written to settings and nothing is downloaded, so a real update check running in the
 *   background cannot overwrite the state under the developer's feet.
 * - The state lives for one process lifetime; killing the app clears it.
 * - The settings switch and the state subscription that feeds the dialog are both inside
 *   `BuildConfig.DEBUG` branches, so a release build never shows the switch and never receives a
 *   simulated release.
 */
object DebugUpdateSimulator {

    private val _simulatedRelease = MutableStateFlow<AppReleaseInfo?>(null)

    /** Current simulated release, `null` when the simulator is off. */
    val simulatedRelease: StateFlow<AppReleaseInfo?> = _simulatedRelease.asStateFlow()

    /**
     * Turns the simulator on or off; switching off closes the dialog on the next recomposition.
     */
    fun setEnabled(enabled: Boolean) {
        _simulatedRelease.value = if (enabled) SAMPLE_RELEASE else null
    }

    private val SAMPLE_RELEASE_NOTES = """
        <details open="True">
          <summary><b>简体中文</b></summary>

        ## ✨ Highlights

        - **从教务系统直接导入课表**：在应用内的浏览器里登录学校教务系统，用一份导入方案把课程读出来，不用手抄，也不用先导出 CSV。
        - **方案由社区维护并自动更新**：默认每 7 天在后台检查一次，可在设置里关掉，或改成 1/3/14/30 天。

        ## 🚀 Features

        - 新增教务系统导入的载荷契约与落库：字段校验（`schema 1`）、课程与课时上限、单事务写入与课程的整表替换。 953983b @Kurosu-Ti01
        - 新增方案源与索引解析：方案 id 必须是小写 slug，脚本路径必须落在方案自己的目录里，越界与编码形式一律拒绝。 0c436eb @Kurosu-Ti01
        - 新增导入页面：脚本在 `WebView` 里执行，通过 JS 桥请求确认框、输入框与选项并交回载荷。 a843640 @Kurosu-Ti01
        - 支持自己写方案：填写名称、学校、首字母与起始网址，脚本可从文件选择或从剪贴板粘贴，保存后还能再次编辑或删除。 32f3c2e @Kurosu-Ti01

        ## 🐛 Fixes

        **课表**

        - 修复周视图最后一节课的格子被底部导航遮住的问题。 0034a63 @Kurosu-Ti01
        - 修复覆盖导入时作息表未跟随目标课表的问题。 1fbc9d1 @Kurosu-Ti01

        ## 🛠 Build

        - 发布流程改用 universal APK 作为默认下载包。 8ec5cf1 @Kurosu-Ti01

        </details>

        <details>
          <summary><b>English</b></summary>

        ## ✨ Highlights

        - **Import the timetable straight from the school system**: log in inside the in-app browser and read the courses out with one import preset.

        ## 🚀 Features

        - Added the import payload contract and persistence. 953983b @Kurosu-Ti01

        </details>
    """.trimIndent()

    /**
     * Sample payload that exercises every shape the real release template produces: bilingual
     * `<details>` blocks, `## emoji` categories, `**模块名**` sub headings, inline `**bold**`/
     * `` `code` ``, trailing `hash @author` metadata, and enough entries to scroll.
     *
     * Declared after [SAMPLE_RELEASE_NOTES] because object properties initialize in declaration
     * order.
     */
    private val SAMPLE_RELEASE = AppReleaseInfo(
        versionTag = "v9.9.9-debug",
        releaseName = "Debug 模拟版本",
        releaseNotes = SAMPLE_RELEASE_NOTES,
        // Blank on purpose: the simulated dialog must never reach DownloadManager.
        apkDownloadUrl = "",
        releasePageUrl = "https://github.com/Kurosu-Ti01/SleepIn/releases"
    )
}
