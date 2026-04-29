package com.kurosu.sleepin.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LocalAppWidgetOptions
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.kurosu.sleepin.MainActivity
import com.kurosu.sleepin.SleepInApplication
import kotlinx.coroutines.runBlocking
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.floor
import kotlin.math.min

/**
 * ====== Widget layout tuning knobs ======
 *
 * If you want to manually tune Today widget layout, adjust constants in this block.
 * Each constant affects one dimension rule only, so tuning can be done incrementally.
 */
private const val OUTER_PADDING_DP = 12f
private const val LIST_TOP_SPACING_DP = 10f
private const val HEADER_ESTIMATED_HEIGHT_DP = 24f
private const val STATUS_BLOCK_ESTIMATED_HEIGHT_DP = 24f

/**
 * ====== Course cell tuning knobs (manual debug friendly) ======
 *
 * Layout strategy is intentionally simple:
 * 1) First try one cell per row (single column).
 * 2) Use widget height to compute rows that can be fully displayed.
 * 3) If one column cannot fit all remaining courses, switch to two cells per row.
 *
 * You can tweak the numbers below to tune visual density.
 * Effective per-row height used by the layout calculator is pinned by
 * [COURSE_ROW_FIXED_HEIGHT_DP], so row-count math and render height stay consistent.
 */
private const val COURSE_COLUMN_GAP_DP = 6f
private const val COURSE_ROW_FIXED_HEIGHT_DP = 56f
private const val COURSE_ROW_VERTICAL_PADDING_DP = 6f
private const val COURSE_ROW_HORIZONTAL_PADDING_DP = 12f
private const val COURSE_ROW_STRIPE_HEIGHT_DP = 32f
private const val COURSE_ROW_CORNER_RADIUS_DP = 12f
private const val COURSE_ROW_SPACING_DP = 6f

/**
 * Course typography knobs.
 *
 * - Increase TITLE font to emphasize course name.
 * - Decrease SUBLINE font for more compact information density.
 * - LINE_GAP controls spacing between the two text lines.
 */
private const val COURSE_TITLE_FONT_SP = 16f
private const val COURSE_SUBLINE_FONT_SP = 12f
private const val COURSE_LINE_GAP_DP = 4f

/**
 * Header first-line right slot width for timetable name.
 * Increase when timetable names are often truncated.
 * Decrease to reserve more room for left-side meta text.
 */
private const val HEADER_TIMETABLE_SLOT_DP = 92f
private const val HEADER_TIMETABLE_GAP_DP = 6f
private const val HEADER_LEFT_INSET_DP = 6f
private const val HEADER_RIGHT_INSET_DP = 6f

/**
 * Optional runtime diagnostics for launcher-specific size issues.
 *
 * When true, the widget will log the runtime size and render-plan values to Logcat
 * with tag "TodayWidgetLayout". Keep it false in normal usage.
 */
private const val LOG_LAYOUT_DIAGNOSTICS = false

/**
 * Small tolerance for dp rounding differences from launcher size conversion.
 *
 * Some launchers provide fractional dp values after converting from px.
 * Without a tiny epsilon, edge heights that are visually enough for N rows can be
 * misclassified as "N-1 rows", which may trigger unnecessary two-column layout.
 */
private const val ROW_FIT_EPSILON_DP = 0.5f

/**
 * AndroidManifest receiver entry for the Today widget.
 *
 * Overrides [onAppWidgetOptionsChanged] so that after the default resize update cycle
 * the widget instance receives a targeted full re-composition. Without this override,
 * some launchers reuse stale RemoteViews across size transitions (e.g., 2c/4i → 1c/5i),
 * causing the widget to display old cell counts even when logcat confirms the correct
 * layout spec was computed.
 */
class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        // Force a targeted re-render for this specific widget instance immediately after
        // the default update cycle. Using a blocking call here is acceptable because the
        // snapshot load hits only local Room/DataStore (no network).
        runBlocking {
            try {
                val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
                TodayWidget.update(context, glanceId)
            } catch (_: Exception) {
                // Best-effort supplement; super already triggered the default update path.
            }
        }
    }
}

/**
 * Widget that shows today's classes for the active timetable.
 */
object TodayWidget : GlanceAppWidget() {
    /**
     * Exact mode makes LocalSize.current reflect the runtime widget size after user resize.
     * Without this, some launchers may keep size in a single baseline bucket, causing
     * under-estimated width and narrow two-column cards clustered on the left.
     */
    override val sizeMode: SizeMode = SizeMode.Exact

    /**
     * Cached snapshot to avoid expensive Room/DataStore re-reads on every pixel-change
     * resize event. Null whenever the next [provideGlance] should load fresh data.
     *
     * Cache lifetime is intentionally short — the snapshot is discarded after one
     * non-resize update or whenever the widget process is killed. This avoids stale
     * data without any explicit invalidation signaling.
     */
    private var cachedSnapshot: WidgetSnapshot? = null
    private var cachedSnapshotTimestamp: Long = 0L
    private const val SNAPSHOT_CACHE_TTL_MS = 3000L

    /**
     * Glance entrypoint invoked by launcher/host when this widget needs rendering.
     *
     * Data flow:
     * - Reads app-level dependencies from [SleepInApplication].
     * - Builds one [WidgetSnapshot] via [WidgetCourseSnapshotProvider].
     * - Passes immutable snapshot data to Compose-style widget content.
     *
     * During rapid resize the snapshot is cached for up to [SNAPSHOT_CACHE_TTL_MS]
     * to prevent overlapping Room/DataStore reads that can cause ANR-like freezes.
     */
    override suspend fun provideGlance(context: android.content.Context, id: GlanceId) {
        val app = context.applicationContext as SleepInApplication
        val now = System.currentTimeMillis()
        val snapshot = if (cachedSnapshot != null &&
            (now - cachedSnapshotTimestamp) < SNAPSHOT_CACHE_TTL_MS
        ) {
            cachedSnapshot!!
        } else {
            val fresh = WidgetCourseSnapshotProvider(app).load()
            cachedSnapshot = fresh
            cachedSnapshotTimestamp = now
            fresh
        }

        provideContent {
            TodayWidgetContent(snapshot, id.toString())
        }
    }
}

/**
 * Stateless renderer for the Today widget.
 */
@Composable
private fun TodayWidgetContent(snapshot: WidgetSnapshot, glanceIdTag: String) {
    val launchAppAction = actionStartActivity<MainActivity>()
    // Glance provides the current widget runtime size (in dp) after user resize.
    // This is the single source of truth for responsive layout decisions.
    val widgetSize = LocalSize.current
    val widgetOptions = LocalAppWidgetOptions.current
    val palette = (snapshot as? WidgetSnapshot.Content)?.palette
        ?: WidgetPalette(
            surface = 0xFFFFFFFF.toInt(),
            surfaceSubtle = 0xFFF6F8FC.toInt(),
            onSurface = 0xFF202124.toInt(),
            onSurfaceMuted = 0xFF6B7280.toInt(),
            primary = 0xFF4A90D9.toInt(),
            outline = 0xFFE5E7EB.toInt(),
            onAccent = 0xFFFFFFFF.toInt()
        )

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(singleColor(palette.surface))
            .padding(OUTER_PADDING_DP.dp)
            .clickable(launchAppAction),
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.Start
    ) {
        when (snapshot) {
            WidgetSnapshot.NoActiveTimetable -> {
                Text(
                    text = "Today",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        color = singleColor(palette.onSurface)
                    )
                )
                Spacer(modifier = GlanceModifier.height(8.dp))
                Text(text = "No active timetable", style = TextStyle(color = singleColor(palette.onSurfaceMuted)))
                Text(text = "Open SleepIn to choose one", style = TextStyle(color = singleColor(palette.onSurfaceMuted)))
            }

            is WidgetSnapshot.Content -> {
                val remainingCourses = filterRemainingCourses(snapshot.todayCourses)
                // Approximate inner width for text strategy (e.g., compact/full header wording).
                val contentWidthDp = (widgetSize.width.value - OUTER_PADDING_DP * 2).coerceAtLeast(0f)
                // Core responsive decision:
                // - how many columns (1 vs 2)
                // - how many rows per column
                // - compact vs regular row metrics
                val layoutSpec = calculateTodayWidgetLayoutSpec(
                    heightDp = widgetSize.height.value,
                    hasSemesterStatus = snapshot.semesterStatusLabel != null,
                    remainingCourseCount = remainingCourses.size
                )
                val renderPlan = resolveTodayCourseRenderPlan(
                    courseCount = remainingCourses.size,
                    layoutSpec = layoutSpec
                )

                if (LOG_LAYOUT_DIAGNOSTICS) {
                    val minWidth = widgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, -1)
                    val minHeight = widgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, -1)
                    val maxWidth = widgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, -1)
                    val maxHeight = widgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, -1)
                    val sizeBucketCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        widgetOptions.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)?.size ?: 0
                    } else {
                        0
                    }
                    Log.d(
                        "TodayWidgetLayout",
                        "id=$glanceIdTag, " +
                            "size=${widgetSize.width.value}x${widgetSize.height.value}, " +
                            "opts=min(${minWidth}x${minHeight}),max(${maxWidth}x${maxHeight}),buckets=${sizeBucketCount}, " +
                            "remaining=${remainingCourses.size}, " +
                            "status=${snapshot.semesterStatusLabel != null}, " +
                            "spec=${layoutSpec.columns}c/${layoutSpec.rowsPerColumn}r, " +
                            "render=${renderPlan.columns}c/${renderPlan.maxVisibleItems}i"
                    )
                }

                HeaderBlock(
                    snapshot = snapshot,
                    remainingCount = remainingCourses.size,
                    contentWidthDp = contentWidthDp
                )

                snapshot.semesterStatusLabel?.let { status ->
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = status,
                        style = TextStyle(
                            fontStyle = FontStyle.Italic,
                            color = singleColor(snapshot.palette.onSurfaceMuted)
                        ),
                        maxLines = 1
                    )
                }

                Spacer(modifier = GlanceModifier.height(layoutSpec.listTopSpacingDp.dp))

                if (remainingCourses.isEmpty()) {
                    TodayEmptyStateCard(
                        state = resolveTodayEmptyState(
                            todayCourses = snapshot.todayCourses,
                            remainingCourses = remainingCourses
                        ),
                        palette = snapshot.palette
                    )
                } else {
                    TodayCourseList(
                        courses = remainingCourses,
                        palette = snapshot.palette,
                        layoutSpec = layoutSpec,
                        contentWidthDp = contentWidthDp,
                        glanceIdTag = glanceIdTag
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderBlock(
    snapshot: WidgetSnapshot.Content,
    remainingCount: Int,
    contentWidthDp: Float
) {
    // Header right-slot width scales with current widget width:
    // narrow widget => smaller slot to prevent left meta from becoming "...".
    // If you want timetable names to keep more visible chars, increase each branch value.
    val timetableSlotDp = when {
        contentWidthDp < 190f -> 64f
        contentWidthDp in 190f..<240f -> 76f
        else -> HEADER_TIMETABLE_SLOT_DP
    }
    val reserveForTimetableDp = timetableSlotDp + HEADER_TIMETABLE_GAP_DP
    val metaText = "${snapshot.dateLabel} ${snapshot.weekdayLabel} · 第${snapshot.currentWeek}周 · 余${remainingCount}节"

    Box(modifier = GlanceModifier.fillMaxWidth()) {
        // Left meta line reserves a fixed right slot so the timetable name can be truly right-aligned.
        Text(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(
                    start = HEADER_LEFT_INSET_DP.dp,
                    end = (reserveForTimetableDp + HEADER_RIGHT_INSET_DP).dp
                ),
            text = metaText,
            style = TextStyle(
                color = singleColor(snapshot.palette.onSurface),
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )

        // This row pins timetable text to the far-right edge regardless of widget width.
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(end = HEADER_RIGHT_INSET_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.End
        ) {
            Text(
                modifier = GlanceModifier.width(timetableSlotDp.dp),
                text = snapshot.timetableName,
                style = TextStyle(
                    color = singleColor(snapshot.palette.onSurfaceMuted),
                    textAlign = TextAlign.End
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TodayCourseList(
    courses: List<WidgetSessionItem>,
    palette: WidgetPalette,
    layoutSpec: TodayWidgetLayoutSpec,
    contentWidthDp: Float,
    glanceIdTag: String
) {
    val renderPlan = resolveTodayCourseRenderPlan(
        courseCount = courses.size,
        layoutSpec = layoutSpec
    )
    val visible = if (renderPlan.maxVisibleItems > 0) {
        courses.take(renderPlan.maxVisibleItems)
    } else {
        emptyList()
    }
    if (LOG_LAYOUT_DIAGNOSTICS) {
        val renderedRows = if (renderPlan.columns == 2) {
            (visible.size + 1) / 2
        } else {
            visible.size
        }
        Log.d(
            "TodayWidgetLayout",
            "id=$glanceIdTag, " +
                "list courses=${courses.size}, visible=${visible.size}, " +
                "render=${renderPlan.columns}c/${renderPlan.maxVisibleItems}i, " +
                "rows=${renderedRows}"
        )
    }
    // ▸▸▸ CRITICAL: wrap the entire course area in a dedicated Column ▸▸▸
    //
    // Without this wrapper, the parent Column (TodayWidgetContent) receives a
    // variable number of direct children whose count and type differ between
    // 1-column and 2-column modes (Row+Spacer pairs vs TodayCourseRow+Spacer
    // sequences). Glance's RemoteViews diffing handles child-update but can
    // fail to ADD new children after a mode switch, causing exactly 4 items
    // to display when 5 are computed.
    //
    // By wrapping the list in its own Column, the parent always sees a single
    // child here, and the wrapper Column rebuilds its entire subtree when the
    // render plan changes columns or visible count.
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        if (renderPlan.columns == 2) {
            // Keep both columns within current widget width; do not enforce a hard min width here.
            // A hard lower bound can push the second column outside viewport on narrow widgets.
            val columnWidthDp = ((contentWidthDp - COURSE_COLUMN_GAP_DP) / 2f).coerceAtLeast(0f).dp
            val rows = visible.chunked(2)
            rows.forEachIndexed { rowIndex, rowItems ->
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TodayCourseRow(
                        item = rowItems.first(),
                        palette = palette,
                        rowMetrics = layoutSpec.rowMetrics,
                        modifier = GlanceModifier.width(columnWidthDp)
                    )
                    Spacer(modifier = GlanceModifier.width(COURSE_COLUMN_GAP_DP.dp))
                    if (rowItems.size == 2) {
                        TodayCourseRow(
                            item = rowItems[1],
                            palette = palette,
                            rowMetrics = layoutSpec.rowMetrics,
                            modifier = GlanceModifier.width(columnWidthDp)
                        )
                    } else {
                        Spacer(modifier = GlanceModifier.width(columnWidthDp))
                    }
                }
                if (rowIndex < rows.lastIndex) {
                    Spacer(modifier = GlanceModifier.height(layoutSpec.rowMetrics.rowSpacingDp.dp))
                }
            }
        } else {
            visible.forEachIndexed { index, item ->
                TodayCourseRow(
                    item = item,
                    palette = palette,
                    rowMetrics = layoutSpec.rowMetrics,
                    modifier = GlanceModifier.fillMaxWidth()
                )
                if (index < visible.lastIndex) {
                    Spacer(modifier = GlanceModifier.height(layoutSpec.rowMetrics.rowSpacingDp.dp))
                }
            }
        }
    }
}

/**
 * Converts layout spec into a render-safe plan.
 *
 * Guardrail:
 * if layoutSpec says single-column but rows cannot cover all courses, we fall back to two-column
 * rendering for this frame. This keeps behavior aligned with product rule:
 * single column only when it can show all remaining courses.
 */
internal fun resolveTodayCourseRenderPlan(
    courseCount: Int,
    layoutSpec: TodayWidgetLayoutSpec
): TodayCourseRenderPlan {
    if (courseCount <= 0 || layoutSpec.rowsPerColumn <= 0) {
        return TodayCourseRenderPlan(columns = 1, maxVisibleItems = 0)
    }

    val forceTwoColumns = layoutSpec.columns == 1 && layoutSpec.rowsPerColumn < courseCount
    val columns = if (forceTwoColumns) 2 else layoutSpec.columns.coerceIn(1, 2)
    val maxVisibleItems = when (columns) {
        1 -> min(courseCount, layoutSpec.rowsPerColumn)
        else -> min(courseCount, layoutSpec.rowsPerColumn * 2)
    }

    return TodayCourseRenderPlan(
        columns = columns,
        maxVisibleItems = maxVisibleItems
    )
}

/**
 * Two-line course row that adapts density by widget height.
 */
@Composable
private fun TodayCourseRow(
    item: WidgetSessionItem,
    palette: WidgetPalette,
    rowMetrics: TodayRowMetrics,
    modifier: GlanceModifier = GlanceModifier
) {
    Row(
        modifier = modifier
            .height(rowMetrics.fixedRowHeightDp.dp)
            .background(singleColor(palette.surfaceSubtle))
            .cornerRadius(rowMetrics.cornerRadiusDp.dp)
            .padding(
                horizontal = rowMetrics.horizontalPaddingDp.dp,
                vertical = rowMetrics.verticalPaddingDp.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The colored stripe carries course identity while keeping the card body clean.
        Box(
            modifier = GlanceModifier
                .width(3.dp)
                .height(rowMetrics.stripeHeightDp.dp)
                .background(singleColor(item.courseColor))
                .cornerRadius(2.dp)
        ) {}
        Spacer(modifier = GlanceModifier.width(7.dp))

        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = item.courseName,
                style = TextStyle(
                    color = singleColor(palette.onSurface),
                    fontWeight = FontWeight.Bold,
                    fontSize = COURSE_TITLE_FONT_SP.sp
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(COURSE_LINE_GAP_DP.dp))
            Text(
                text = formatCourseSubline(item),
                style = TextStyle(
                    color = singleColor(palette.onSurfaceMuted),
                    fontSize = COURSE_SUBLINE_FONT_SP.sp
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TodayEmptyStateCard(
    state: TodayEmptyState,
    palette: WidgetPalette
) {
    val title = when (state) {
        TodayEmptyState.NO_COURSE -> "今天没有课哦~"
        TodayEmptyState.FINISHED -> "今天课上完了哦~"
    }
    val subtitle = when (state) {
        TodayEmptyState.NO_COURSE -> "好好享受今天吧ヾ(≧▽≦*)o"
        TodayEmptyState.FINISHED -> "辛苦啦，好好休息吧q(≧▽≦q)"
    }
    val icon = when (state) {
        TodayEmptyState.NO_COURSE -> "🌤"
        TodayEmptyState.FINISHED -> "✅"
    }

    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(singleColor(withSubtleAlpha(palette.primary)))
            .cornerRadius(12.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column {
            Text(
                text = "$icon $title",
                style = TextStyle(
                    color = singleColor(palette.onSurface),
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = subtitle,
                style = TextStyle(color = singleColor(palette.onSurfaceMuted)),
                maxLines = 1
            )
        }
    }
}

/**
 * Build a compact secondary line that always keeps time and location in a single visual unit.
 */
private fun formatCourseSubline(item: WidgetSessionItem): String {
    val timeRange = when {
        item.startTimeLabel != null && item.endTimeLabel != null -> {
            "${item.startTimeLabel} - ${item.endTimeLabel}"
        }

        else -> "P${item.startPeriod}-${item.endPeriod}"
    }
    val location = item.location?.trim()?.takeIf { it.isNotBlank() }
    return if (location == null) {
        timeRange
    } else {
        "$timeRange  $location"
    }
}

/**
 * Hide sessions that have already ended; sessions with unknown time metadata stay visible.
 */
private fun filterRemainingCourses(
    courses: List<WidgetSessionItem>,
    now: LocalTime = LocalTime.now()
): List<WidgetSessionItem> {
    return courses.filter { item ->
        val end = item.endTimeLabel
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalTime.parse(it, TIME_FORMATTER) }.getOrNull() }

        // Keep course visible when no end-time can be resolved from schedule settings.
        end == null || !end.isBefore(now)
    }
}

internal fun resolveTodayEmptyState(
    todayCourses: List<WidgetSessionItem>,
    remainingCourses: List<WidgetSessionItem>
): TodayEmptyState {
    return if (todayCourses.isNotEmpty() && remainingCourses.isEmpty()) {
        TodayEmptyState.FINISHED
    } else {
        TodayEmptyState.NO_COURSE
    }
}

internal fun calculateTodayWidgetLayoutSpec(
    heightDp: Float,
    hasSemesterStatus: Boolean,
    remainingCourseCount: Int
): TodayWidgetLayoutSpec {
    val rowMetrics = TodayRowMetrics(
        fixedRowHeightDp = COURSE_ROW_FIXED_HEIGHT_DP,
        horizontalPaddingDp = COURSE_ROW_HORIZONTAL_PADDING_DP,
        verticalPaddingDp = COURSE_ROW_VERTICAL_PADDING_DP,
        stripeHeightDp = COURSE_ROW_STRIPE_HEIGHT_DP,
        cornerRadiusDp = COURSE_ROW_CORNER_RADIUS_DP,
        rowSpacingDp = COURSE_ROW_SPACING_DP
    )
    // Step 2: use a fixed row height to avoid clipping caused by text-height estimation drift.
    val rowHeightDp = rowMetrics.fixedRowHeightDp

    // Step 3: reserve non-list blocks (outer paddings + header + optional semester status).
    val reservedHeightDp = OUTER_PADDING_DP * 2 +
        HEADER_ESTIMATED_HEIGHT_DP +
        (if (hasSemesterStatus) STATUS_BLOCK_ESTIMATED_HEIGHT_DP else 0f) +
        LIST_TOP_SPACING_DP
    val availableListHeightDp = (heightDp - reservedHeightDp).coerceAtLeast(0f)

    // Step 4: compute rows that can fit by height using strict full-row math.
    val rowsByHeight = if (remainingCourseCount == 0) {
        0
    } else {
        floor((availableListHeightDp + rowMetrics.rowSpacingDp) / (rowHeightDp + rowMetrics.rowSpacingDp))
            .toInt()
            .coerceAtLeast(0)
    }

    // Step 5: explicitly check whether one column can hold every remaining course.
    // This prevents false truncation around fractional dp boundaries.
    val requiredSingleColumnHeightDp = if (remainingCourseCount <= 0) {
        0f
    } else {
        remainingCourseCount * rowHeightDp + (remainingCourseCount - 1) * rowMetrics.rowSpacingDp
    }
    val singleColumnFitsAll = remainingCourseCount == 0 ||
        availableListHeightDp + ROW_FIT_EPSILON_DP >= requiredSingleColumnHeightDp

    // Step 5:
    // - default is 1 cell per row (single column),
    // - if single column cannot fit all remaining courses, switch to 2 cells per row.
    val useTwoColumns = !singleColumnFitsAll && rowsByHeight > 0
    val rowsPerColumn = when {
        remainingCourseCount == 0 -> 0
        singleColumnFitsAll -> remainingCourseCount
        else -> rowsByHeight
    }

    return TodayWidgetLayoutSpec(
        rowMetrics = rowMetrics,
        listTopSpacingDp = LIST_TOP_SPACING_DP,
        columns = if (useTwoColumns) 2 else 1,
        rowsPerColumn = rowsPerColumn
    )
}

internal data class TodayWidgetLayoutSpec(
    val rowMetrics: TodayRowMetrics,
    val listTopSpacingDp: Float,
    val columns: Int,
    val rowsPerColumn: Int
) {
    val maxVisibleItems: Int get() = columns * rowsPerColumn
}

internal data class TodayCourseRenderPlan(
    val columns: Int,
    val maxVisibleItems: Int
)

/**
 * Row metrics are centralized so manual tuning stays in one place.
 *
 * Practical tuning guidance:
 * - Increase [fixedRowHeightDp] => fewer rows, larger cards.
 * - Decrease them => more rows, denser cards.
 * - Increase [stripeHeightDp] => stronger color marker emphasis.
 */
internal data class TodayRowMetrics(
    val fixedRowHeightDp: Float,
    val horizontalPaddingDp: Float,
    val verticalPaddingDp: Float,
    val stripeHeightDp: Float,
    val cornerRadiusDp: Float,
    val rowSpacingDp: Float
)

internal enum class TodayEmptyState {
    NO_COURSE,
    FINISHED
}

private const val SUBTLE_SURFACE_ALPHA = 0.10f

/**
 * Applies the default subtle alpha used by the empty-state card background.
 */
private fun withSubtleAlpha(argb: Int): Int {
    val normalizedAlpha = (SUBTLE_SURFACE_ALPHA * 255).toInt()
    return (argb and 0x00FFFFFF) or (normalizedAlpha shl 24)
}

private fun singleColor(argb: Int): ColorProvider {
    val color = Color(argb)
    return ColorProvider(color)
}

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

