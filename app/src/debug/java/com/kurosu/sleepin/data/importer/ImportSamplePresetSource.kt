package com.kurosu.sleepin.data.importer

import com.kurosu.sleepin.domain.model.importer.ImportPreset
import com.kurosu.sleepin.domain.model.importer.ImportPresetGroup
import com.kurosu.sleepin.domain.model.importer.ImportPresetSourceKind

/**
 * Sample preset that exists only in debug builds.
 *
 * The import flow normally needs a real academic portal, a login and a timetable to exercise anything
 * end to end. That makes it impractical to check the parts that have nothing to do with a particular
 * school: the setup step, the bridge, the confirmation dialog and its "which class times" choice, the
 * out-of-range diagnostics, and the write itself.
 *
 * This source hands the app a preset whose script talks to the bridge and immediately produces a
 * payload, so the entire chain from the preset list to the success dialog runs without a network. It
 * offers a few datasets on purpose: "no class times" and "alternating weeks with an out-of-range
 * period" are the two cases the app has to handle differently, and neither is easy to find in a real
 * portal on demand.
 *
 * The script is a string rather than an asset so the whole feature — including the payloads — lives in
 * the debug source set and cannot be packaged into a release build.
 */
internal class ImportSamplePresetSource : ImportPresetSource {

    override val kind: ImportPresetSourceKind = ImportPresetSourceKind.DEBUG

    private val sample = ImportPreset(
        id = SAMPLE_PRESET_ID,
        name = "模拟导入（仅调试版）",
        group = ImportPresetGroup.GENERAL,
        initial = "M",
        // No start URL: the import page opens a blank document, which is all this script needs, and
        // the address bar stays available exactly as it is for a preset without a login page.
        startUrl = null,
        scriptPath = scriptPathFor(SAMPLE_PRESET_ID),
        description = "不访问任何网站，直接产出一份样例课表，用于测试导入全流程。",
        maintainer = "SleepIn",
        source = ImportPresetSourceKind.DEBUG
    )

    override suspend fun presets(): List<ImportPreset> = listOf(sample)

    override suspend fun readScript(scriptPath: String): String? =
        SAMPLE_SCRIPT.takeIf { scriptPath == sample.scriptPath }

    private companion object {
        /**
         * Id of the sample preset. Deliberately not a plausible school id, so it cannot collide with
         * a preset a user or the index contributes.
         */
        const val SAMPLE_PRESET_ID = "debug-import-sample"

        val SAMPLE_SCRIPT: String = """
            // 模拟导入脚本（仅调试版）。不访问任何网站，直接产出样例数据。
            //
            // 它走的是和真脚本完全相同的那条路：应用注入运行流程 → 脚本调桥 → 应用校验载荷 →
            // 确认弹窗 → 落库。所以在这里跑通，就说明导入链路本身没问题，剩下的只是取数。
            (async () => {
              const bridge = window.sleepinBridge;
              const promise = window.sleepinBridgePromise;
              if (!bridge || !promise) {
                console.log('这个脚本需要运行在应用内（没有桥时无法测试导入）');
                return;
              }

              const datasets = [
                '标准样例（含作息时间）',
                '不提供作息时间',
                '单双周 + 越界节次'
              ];
              const picked = await promise.choose({
                title: '模拟导入',
                options: datasets,
                defaultIndex: 0
              });
              if (picked === null || picked === undefined) {
                bridge.toast('已取消');
                return;
              }

              // 三个数据集对应三种需要人工确认的行为：正常、没有作息时间（只能选已有作息表）、
              // 有单双周与越界节次（应当被提示而不是被丢掉）。
              const slides = (from, to) => {
                const weeks = [];
                for (let week = from; week <= to; week += 1) weeks.push(week);
                return weeks;
              };
              const oddWeeks = slides(1, 16).filter((week) => week % 2 === 1);
              const evenWeeks = slides(1, 16).filter((week) => week % 2 === 0);
              const periods = [
                { period: 1, start: '08:00', end: '08:45' },
                { period: 2, start: '08:55', end: '09:40' },
                { period: 3, start: '10:00', end: '10:45' },
                { period: 4, start: '10:55', end: '11:40' },
                { period: 5, start: '14:00', end: '14:45' },
                { period: 6, start: '14:55', end: '15:40' }
              ];

              const standard = {
                schema: 1,
                timetable: { name: '模拟学期', totalWeeks: 18, startDate: '2025-09-01' },
                periods: periods,
                courses: [
                  {
                    name: '模拟课程 A',
                    teacher: '张老师',
                    sessions: [{ day: 1, from: 1, to: 2, location: 'A101', weeks: slides(1, 16) }]
                  },
                  {
                    name: '模拟课程 B',
                    teacher: '李老师',
                    sessions: [{ day: 3, from: 3, to: 4, location: 'B202', weeks: slides(1, 8) }]
                  },
                  {
                    name: '模拟课程 C',
                    teacher: null,
                    sessions: [
                      { day: 5, from: 5, to: 6, location: null, weeks: slides(1, 16) },
                      { day: 2, from: 5, to: 6, location: 'C303', weeks: slides(1, 16) }
                    ]
                  }
                ]
              };

              let payload;
              let note;
              if (picked === 0) {
                payload = standard;
                note = '会带上 6 条作息时间，确认弹窗里应当可以二选一；默认用方案提供的。';
              } else if (picked === 1) {
                const withoutTimes = JSON.parse(JSON.stringify(standard));
                delete withoutTimes.periods;
                payload = withoutTimes;
                note = '不带作息时间：确认弹窗里不应出现单选，只能使用已有作息表。';
              } else {
                payload = JSON.parse(JSON.stringify(standard));
                payload.courses[0].sessions = [
                  { day: 1, from: 1, to: 2, location: 'A101', weeks: oddWeeks }
                ];
                payload.courses[1].sessions = [
                  { day: 1, from: 1, to: 2, location: 'A101', weeks: evenWeeks }
                ];
                payload.courses[2].sessions = [
                  { day: 4, from: 30, to: 31, location: 'D404', weeks: slides(1, 4) }
                ];
                note = '第 30-31 节超出作息表，应当被提示「未定义」并可以跳去补全作息表。';
              }

              const sessionCount = payload.courses.reduce(
                (sum, course) => sum + course.sessions.length,
                0
              );
              const confirmed = await promise.alert({
                title: '模拟数据',
                message: note + '\n\n' + payload.courses.length + ' 门课程、' + sessionCount + ' 条课时。',
                confirmText: '导入'
              });
              if (confirmed !== true) {
                bridge.toast('已取消');
                return;
              }

              try {
                const committed = (await promise.commit(payload)) === true;
                bridge.toast(committed ? '模拟导入完成' : '导入未完成');
                if (committed) bridge.finish();
              } catch (error) {
                bridge.toast('导入失败：' + error);
              }
            })();
        """.trimIndent()
    }
}
