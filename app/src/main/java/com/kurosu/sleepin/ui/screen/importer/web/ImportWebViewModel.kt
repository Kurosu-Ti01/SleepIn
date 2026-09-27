package com.kurosu.sleepin.ui.screen.importer.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kurosu.sleepin.domain.model.formatPeriodNumbers
import com.kurosu.sleepin.domain.repository.TimetableImportResult
import com.kurosu.sleepin.domain.usecase.importer.BuildImportPreviewUseCase
import com.kurosu.sleepin.domain.usecase.importer.CommitImportUseCase
import com.kurosu.sleepin.domain.usecase.importer.GetImportPresetsUseCase
import com.kurosu.sleepin.domain.usecase.importer.ImportParseResult
import com.kurosu.sleepin.domain.usecase.importer.ImportPreview
import com.kurosu.sleepin.domain.usecase.importer.ImportPreviewResult
import com.kurosu.sleepin.domain.usecase.importer.ImportScheduleSource
import com.kurosu.sleepin.domain.usecase.importer.ImportSubmission
import com.kurosu.sleepin.domain.usecase.importer.ImportTarget
import com.kurosu.sleepin.domain.usecase.importer.ParseImportSubmissionUseCase
import com.kurosu.sleepin.domain.usecase.importer.ReadImportScriptUseCase
import com.kurosu.sleepin.domain.usecase.schedule.GetSchedulesUseCase
import com.kurosu.sleepin.domain.usecase.timetable.GetTimetablesUseCase
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One schedule the imported timetable can be linked to.
 *
 * Defined here rather than shared with the timetable editor because the two screens only need an id
 * and a label; coupling them through a common model would say more than the data does.
 */
data class ImportScheduleOptionUi(
    val id: Long,
    val name: String
)

/**
 * One existing timetable the import may overwrite.
 *
 * Defined here for the same reason as [ImportScheduleOptionUi]: the screen needs an id and a label,
 * and sharing the timetable list's model would couple two screens that otherwise do not know about
 * each other.
 */
data class ImportTimetableOptionUi(
    val id: Long,
    val name: String,
    /**
     * Schedule the timetable currently uses.
     *
     * Carried so that choosing this target can default to the class times it already has, instead of
     * whatever schedule happens to be first in the list. Replacing a timetable's courses is not a
     * request to change when its classes happen.
     */
    val scheduleId: Long
)

/**
 * A payload that is valid and is waiting for the user to confirm it.
 *
 * The submission is kept in the state rather than written immediately so the confirmation dialog can
 * describe exactly what will be stored, and so cancelling really is a no-op. The description itself
 * comes from [preview], which was resolved by the same rules the write will follow.
 *
 * [target] and [submission] are kept alongside the preview because the user can still switch where
 * the class times come from, and rebuilding the summary needs both.
 */
data class ImportPendingCommit(
    val callId: String,
    val submission: ImportSubmission,
    val target: ImportTarget,
    val preview: ImportPreview
)

/**
 * Result shown as a dialog once the write succeeded.
 *
 * A dialog rather than a snackbar because this screen closes on success, which would take a snackbar
 * with it. [scheduleIdToFix] is carried so the user can go straight to the schedule that is missing the
 * imported periods, instead of having to find it in the schedule list afterwards.
 */
data class ImportSuccessNotice(
    val message: String,
    val scheduleIdToFix: Long?
)

/**
 * Everything the import page renders.
 */
data class ImportWebUiState(
    val isLoading: Boolean = true,
    val presetName: String = "",
    val presetDescription: String = "",
    /** Page the preset starts at; null means the user types an address themselves. */
    val startUrl: String? = null,
    /** The preset's script could not be read, so the import cannot run at all. */
    val scriptUnavailable: Boolean = false,
    val scheduleOptions: List<ImportScheduleOptionUi> = emptyList(),
    val selectedScheduleId: Long? = null,
    /** Timetables this import may overwrite. Empty means the user can only create a new one. */
    val timetableOptions: List<ImportTimetableOptionUi> = emptyList(),
    /** Target timetable chosen by the user; null means "create a new timetable". */
    val selectedTimetableId: Long? = null,
    /** Whether the user has confirmed the schedule and the WebView may be shown. */
    val isStarted: Boolean = false,
    val pageTitle: String = "",
    val pageUrl: String = "",
    /** Download progress, or null when nothing is loading. */
    val progress: Int? = null,
    val loadError: String? = null,
    /** A script has already run on the current document; running it again needs a reload first. */
    val hasInjectedScript: Boolean = false,
    val isCommitting: Boolean = false,
    /**
     * A commit payload is being validated.
     *
     * Held in the state rather than a plain field because it has to change before the validation
     * coroutine starts, so a second commit arriving in the same frame is refused instead of replacing
     * the first one's confirmation.
     */
    val isPreparingCommit: Boolean = false,
    /** Head of the dialog queue, or null when the script is not waiting for an answer. */
    val dialog: ImportWebRequest.Dialog? = null,
    val pendingCommit: ImportPendingCommit? = null,
    /** Shown as a dialog because the page closes on success, which would take a snackbar with it. */
    val successNotice: ImportSuccessNotice? = null,
    val message: String? = null
) {
    /** Whether the import button should do something. */
    val canRunScript: Boolean
        get() = isStarted &&
            !scriptUnavailable &&
            !isCommitting &&
            !isPreparingCommit &&
            pendingCommit == null
}

/**
 * One-shot effects of the import page.
 */
sealed interface ImportWebEvent {
    /**
     * Leave the import page. [message] is shown by the caller, which outlives this screen.
     *
     * [openScheduleId] asks the caller to open that schedule's editor after leaving, which is how the
     * "fix my schedule" answer to a period mismatch continues past this screen: the import is already
     * stored, and the only remaining task is outside it.
     */
    data class Finished(val message: String?, val openScheduleId: Long? = null) : ImportWebEvent
}

/**
 * What the import page should be showing.
 *
 * A function of the state rather than a condition written inline in the screen, because the case that
 * matters is easy to get wrong and produces a blank page rather than an error: a WebView is created once,
 * so it must not exist until the preset has been read. Creating it earlier gives it whatever the state
 * said at that moment — for a preset with a start URL, an empty document that never becomes the portal.
 */
enum class ImportWebStage {
    /** The preset is still being read; nothing about the page can be decided yet. */
    Loading,

    /** The user still has to confirm which schedule the result should use. */
    Preparation,

    /** The preset is usable, so the page it points at can be opened. */
    Running
}

/**
 * Decides what the import page shows.
 *
 * [ImportWebUiState.scriptUnavailable] sends the user back to preparation rather than to a WebView: a
 * preset whose script cannot be read has nothing to run, and opening its page would only offer a button
 * that refuses to do anything.
 */
internal fun ImportWebUiState.stage(): ImportWebStage = when {
    isLoading -> ImportWebStage.Loading
    isStarted && !scriptUnavailable -> ImportWebStage.Running
    else -> ImportWebStage.Preparation
}

/**
 * How the user answered a dialog.
 *
 * Cancelling resolves differently per request — an alert resolves `false`, a prompt or a choice
 * resolves `null` — so the answer stays abstract and [ImportWebViewModel] applies the right value.
 */
sealed interface ImportDialogAnswer {
    data object Confirmed : ImportDialogAnswer
    data object Cancelled : ImportDialogAnswer
    data class Text(val text: String) : ImportDialogAnswer
    data class Option(val index: Int) : ImportDialogAnswer
}

/**
 * Drives the WebView import page and the host side of the JavaScript bridge.
 *
 * The script is treated as untrusted input even though the user chose it: it can ask for dialogs and
 * hand over a payload, but it cannot write anything. Persistence only happens after the payload has
 * been validated and the user has confirmed a summary of it, which is also why the commit promise is
 * settled last — a script that reports success is reporting something that already happened.
 */
class ImportWebViewModel(
    private val presetId: String,
    initialScheduleId: Long?,
    private val getImportPresetsUseCase: GetImportPresetsUseCase,
    private val readImportScriptUseCase: ReadImportScriptUseCase,
    private val getSchedulesUseCase: GetSchedulesUseCase,
    private val getTimetablesUseCase: GetTimetablesUseCase,
    private val parseImportSubmissionUseCase: ParseImportSubmissionUseCase,
    private val buildImportPreviewUseCase: BuildImportPreviewUseCase,
    private val commitImportUseCase: CommitImportUseCase
) : ViewModel() {

    /** Schedule chosen by the caller, or null when this page still has to ask for one. */
    private val chosenScheduleId: Long? = initialScheduleId?.takeIf { id -> id > 0L }

    private val _uiState = MutableStateFlow(
        ImportWebUiState(
            selectedScheduleId = chosenScheduleId,
            // A caller that already knows the schedule — the new-timetable screen does, because picking
            // one is part of filling in that form — goes straight to the page instead of being asked
            // again. The target is not decided here: choosing between creating a timetable and
            // overwriting one belongs to the page itself.
            isStarted = chosenScheduleId != null
        )
    )
    val uiState: StateFlow<ImportWebUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ImportWebEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ImportWebEvent> = _events.asSharedFlow()

    /** Requests waiting to be answered, in the order the script made them. */
    private val dialogQueue = ArrayDeque<ImportWebRequest.Dialog>()

    private var pageHandle: ImportWebViewHandle? = null
    private var presetScript: String? = null

    /**
     * URL that was reloaded so an already-run script can run again.
     *
     * The script is injected only when that same document arrives. If the user navigated somewhere
     * else while the reload was in flight, running the import on a page they did not choose would be a
     * surprise — and the script's own dialogs would appear over the wrong page.
     */
    private var reloadingForRerun: String? = null

    init {
        observeSchedules()
        observeTimetables()
        loadPreset()
    }

    /** Registers the page the ViewModel talks to. Called by the screen when the WebView exists. */
    fun attachPage(handle: ImportWebViewHandle) {
        pageHandle = handle
    }

    /** Forgets the page. Called before the WebView is destroyed so nothing is called into a dead view. */
    fun detachPage() {
        pageHandle = null
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /** Acknowledges the success notice and leaves the page, optionally opening a schedule to fix. */
    fun consumeSuccessNotice(openScheduleId: Long? = null) {
        val notice = _uiState.value.successNotice ?: return
        _uiState.update { it.copy(successNotice = null) }
        viewModelScope.launch { _events.emit(ImportWebEvent.Finished(notice.message, openScheduleId)) }
    }

    fun onScheduleChange(scheduleId: Long) {
        _uiState.update { it.copy(selectedScheduleId = scheduleId) }
    }

    /** Chooses "create a new timetable" as the import target. */
    fun onCreateTargetSelected() {
        _uiState.update { it.copy(selectedTimetableId = null) }
    }

    /**
     * Switches the target to "overwrite an existing timetable".
     *
     * Keeps whichever timetable was chosen before and falls back to the first one only when there is no
     * usable choice left, so toggling the target back and forth does not silently move the selection.
     * The schedule follows the timetable, because overwriting one is not a request to change when its
     * classes happen; the user can still pick a different schedule afterwards.
     */
    fun onOverwriteTargetRequested() {
        _uiState.update { state ->
            val option = chooseOverwriteTarget(state.timetableOptions, state.selectedTimetableId)
                ?: return@update state
            state.copy(selectedTimetableId = option.id, selectedScheduleId = option.scheduleId)
        }
    }

    /** Chooses an existing timetable the import should replace the courses of. */
    fun onOverwriteTargetSelected(timetableId: Long) {
        _uiState.update { state ->
            val option = state.timetableOptions.firstOrNull { it.id == timetableId }
                ?: return@update state
            state.copy(selectedTimetableId = option.id, selectedScheduleId = option.scheduleId)
        }
    }

    /**
     * Leaves the preparation step and shows the WebView.
     *
     * Both the target and the schedule are required up front because the write needs them: the
     * schedule because an academic portal reports period numbers without class times and the app will
     * not invent them, the target because an import replaces every course of the timetable it writes
     * to.
     */
    fun onStartImport() {
        val state = _uiState.value
        if (state.selectedScheduleId == null) {
            emitMessage("请先选择作息表。")
            return
        }
        if (state.selectedTimetableId != null &&
            state.timetableOptions.none { option -> option.id == state.selectedTimetableId }
        ) {
            emitMessage("要覆盖的课程表不存在，可能已被删除。")
            return
        }
        if (state.scriptUnavailable) {
            emitMessage("该方案的脚本无法读取，请重新选择方案。")
            return
        }
        _uiState.update { it.copy(isStarted = true) }
    }

    /**
     * Runs the preset's script in the page.
     *
     * A document can only be handed the same script once: a top-level `const` in the script would make
     * the second evaluation fail before anything ran. Re-running therefore reloads the page first, which
     * resets the page's globals, and injects once the new document is ready. Cookies are untouched, so
     * the user stays logged in.
     */
    fun onRunScriptClick() {
        val handle = pageHandle ?: return
        if (presetScript == null) {
            emitMessage("该方案的脚本无法读取，请重新选择方案。")
            return
        }
        if (_uiState.value.hasInjectedScript) {
            reloadingForRerun = _uiState.value.pageUrl
            _uiState.update { it.copy(progress = 0) }
            handle.reload()
            return
        }
        injectScript()
    }

    /** Reloads the page from the menu; the runtime is reinstalled automatically. */
    fun onReloadClick() {
        _uiState.update { it.copy(progress = 0, loadError = null) }
        pageHandle?.reload()
    }

    fun onPageStarted(url: String) {
        // A navigation abandons whatever the previous document was waiting on. Leaving those requests
        // queued would make the user answer questions the page no longer has, and the promises they
        // belong to died with the document.
        dialogQueue.clear()
        _uiState.update {
            it.copy(
                progress = 0,
                loadError = null,
                pageUrl = url,
                hasInjectedScript = false,
                dialog = null
            )
        }
    }

    /**
     * Installs the runtime, then runs a script whose reload has just finished.
     *
     * The runtime is reinstalled on every page because navigating away discards it, while the user is
     * expected to log in and open their timetable before pressing the import button.
     */
    fun onPageFinished(url: String) {
        _uiState.update { it.copy(progress = null, pageUrl = url) }
        pageHandle?.evaluate(SleepInBridgeProtocol.RUNTIME_SCRIPT)

        val pendingRerun = reloadingForRerun
        reloadingForRerun = null
        if (pendingRerun != null) {
            if (pendingRerun == url) injectScript() else emitMessage("页面已跳转，请重新点击「执行导入」。")
        }
    }

    fun onTitleChanged(title: String) {
        _uiState.update { it.copy(pageTitle = title) }
    }

    fun onProgressChanged(progress: Int) {
        _uiState.update { it.copy(progress = progress.takeIf { value -> value < 100 }) }
    }

    fun onPageLoadFailed(message: String) {
        reloadingForRerun = null
        _uiState.update { it.copy(progress = null, loadError = message) }
    }

    /**
     * Handles one raw message from the page.
     *
     * A rejected message is reported back to the script so its `catch` can explain the failure, except
     * when the call id itself was unusable — there is then no promise to settle, and the reason is
     * surfaced to the user instead of being dropped.
     */
    fun onBridgeMessage(raw: String) {
        when (val result = SleepInBridgeProtocol.decode(raw)) {
            is BridgeDecodeResult.Decoded -> handleRequest(result.request)
            is BridgeDecodeResult.Rejected -> {
                val callId = result.callId
                if (callId == null) emitMessage(result.message) else reject(callId, result.message)
            }
        }
    }

    /**
     * Answers the dialog currently on screen.
     *
     * The answer is tied to [callId] so that answering the same dialog twice — a clickable row and the
     * control inside it both firing, for instance — cannot advance the queue and answer the next
     * request, which belongs to a script that is still waiting its turn.
     */
    fun onDialogAnswered(callId: String, answer: ImportDialogAnswer) {
        val request = _uiState.value.dialog
        if (request == null || request.callId != callId) return

        when (answer) {
            ImportDialogAnswer.Confirmed -> resolveTrue(callId)
            // An alert resolves `false` when dismissed, while a prompt or a choice resolves `null`.
            ImportDialogAnswer.Cancelled -> if (request is ImportWebRequest.Alert) {
                resolve(callId, SleepInBridgeProtocol.booleanLiteral(false))
            } else {
                resolve(callId, null)
            }

            is ImportDialogAnswer.Text -> resolveText(callId, answer.text)
            is ImportDialogAnswer.Option -> resolve(callId, answer.index.toString())
        }

        // Published only after the promise is settled, so the next dialog is never on screen while the
        // request before it is still unanswered.
        dialogQueue.removeFirstOrNull()
        publishNextDialog()
    }

    /**
     * Runs the page's validation function for a prompt.
     *
     * The result comes back through the evaluation callback rather than as a bridge message, so the
     * script does not have to know that validation crosses the boundary at all. A page that never
     * answers reports a timeout instead of silently accepting the input.
     */
    suspend fun validatePromptInput(validateName: String, input: String): String? {
        val handle = pageHandle ?: return "页面已关闭，无法校验输入。"

        // The outcome is wrapped so that "the script accepted the input" (a null message) stays
        // distinguishable from "the page never answered": withTimeoutOrNull reports both as null, and
        // treating acceptance as a timeout would leave the dialog unable to be completed at all.
        val outcome = withTimeoutOrNull(VALIDATE_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation: CancellableContinuation<PromptValidationOutcome> ->
                handle.evaluate(SleepInBridgeProtocol.buildValidateScript(validateName, input)) { raw ->
                    if (continuation.isActive) {
                        continuation.resume(
                            PromptValidationOutcome(SleepInBridgeProtocol.parseValidateResult(raw))
                        )
                    }
                }
            }
        }
        return if (outcome == null) "校验超时，请重试。" else outcome.message
    }

    /** Writes the confirmed payload. */
    fun confirmCommit() {
        val pending = _uiState.value.pendingCommit ?: return
        // Cleared before the coroutine starts, so a second tap in the same frame cannot start a second
        // write for the same confirmation.
        _uiState.update { it.copy(pendingCommit = null, isCommitting = true) }

        viewModelScope.launch {
            val preview = pending.preview

            when (
                val result = commitImportUseCase(
                    submission = pending.submission,
                    target = preview.target,
                    scheduleSource = preview.scheduleSource
                )
            ) {
                is TimetableImportResult.Imported -> {
                    _uiState.update {
                        it.copy(
                            isCommitting = false,
                            successNotice = buildSuccessNotice(result, preview)
                        )
                    }
                    resolveTrue(pending.callId)
                }

                TimetableImportResult.ScheduleNotFound ->
                    failCommit(pending, "所选作息表不存在，请重新选择作息表后再导入。")

                TimetableImportResult.TimetableNotFound ->
                    failCommit(pending, "目标课程表不存在，可能已被删除。")

                is TimetableImportResult.Rejected -> failCommit(pending, result.message)
            }
        }
    }

    /** Cancels the confirmation without writing anything. */
    fun dismissCommit() {
        val pending = _uiState.value.pendingCommit ?: return
        _uiState.update { it.copy(pendingCommit = null) }
        // Rejected but not announced: the user made this choice on purpose, and the script's own error
        // handling already reports it.
        reject(pending.callId, "已取消导入。")
    }

    private fun injectScript() {
        val script = presetScript ?: return
        val handle = pageHandle ?: return
        // The runtime is installed again here rather than relying on the page-load callback: a document
        // that never finishes loading would otherwise leave the script with nothing to call. Installing
        // it is idempotent, so doing it twice costs nothing.
        handle.evaluate(SleepInBridgeProtocol.RUNTIME_SCRIPT + "\n" + script)
        _uiState.update { it.copy(hasInjectedScript = true) }
    }

    private fun handleRequest(request: ImportWebRequest) {
        when (request) {
            is ImportWebRequest.Alert -> enqueueDialog(request)
            is ImportWebRequest.Prompt -> enqueueDialog(request)
            is ImportWebRequest.Choose -> enqueueDialog(request)
            is ImportWebRequest.Commit -> onCommitRequested(request)
            is ImportWebRequest.Toast -> {
                resolveTrue(request.callId)
                emitMessage(request.message)
            }

            is ImportWebRequest.Finish -> {
                resolveTrue(request.callId)
                // The success notice is a dialog precisely so that leaving happens after it is read.
                if (_uiState.value.successNotice == null) {
                    viewModelScope.launch { _events.emit(ImportWebEvent.Finished(null)) }
                }
            }
        }
    }

    private fun enqueueDialog(request: ImportWebRequest.Dialog) {
        if (dialogQueue.size >= SleepInBridgeProtocol.MAX_PENDING_REQUESTS) {
            reject(request.callId, "请求过多，已忽略。请重新执行导入。")
            return
        }
        dialogQueue.addLast(request)
        publishNextDialog()
    }

    private fun publishNextDialog() {
        _uiState.update { it.copy(dialog = dialogQueue.firstOrNull()) }
    }

    /**
     * Validates a payload, describes the resulting import and asks the user to confirm it.
     *
     * Rejecting here — rather than showing a dialog for an empty or unusable payload — means the script
     * gets a precise message it can display, which is the only feedback channel a preset author has.
     * The summary is built by [BuildImportPreviewUseCase] from the same resolved target and schedule the
     * write will use, so a target that was deleted while the user was logged in is refused now instead
     * of after they confirm.
     */
    private fun onCommitRequested(request: ImportWebRequest.Commit) {
        val state = _uiState.value
        if (state.pendingCommit != null || state.isPreparingCommit || state.isCommitting) {
            reject(request.callId, "上一次导入还没有完成，请先处理它。")
            return
        }
        _uiState.update { it.copy(isPreparingCommit = true) }

        viewModelScope.launch {
            val submission = when (val parsed = parseImportSubmissionUseCase(request.payloadJson)) {
                is ImportParseResult.Rejected -> {
                    // Not announced to the user: the script is still running and reports this itself.
                    _uiState.update { it.copy(isPreparingCommit = false) }
                    reject(request.callId, parsed.message)
                    return@launch
                }

                is ImportParseResult.Accepted -> parsed.submission
            }
            if (submission.courses.isEmpty()) {
                _uiState.update { it.copy(isPreparingCommit = false) }
                reject(request.callId, "导入数据里没有任何课程，已取消导入。")
                return@launch
            }

            val current = _uiState.value
            val scheduleId = current.selectedScheduleId
            if (scheduleId == null) {
                failPrepare(request.callId, "请先选择作息表。")
                return@launch
            }

            val target = current.selectedTimetableId
                ?.let { id -> ImportTarget.ExistingTimetable(id) }
                ?: ImportTarget.NewTimetable

            // Default to the times the script read, when it read any: the portal's own class times are
            // closer to the truth than whatever template happens to be selected, and the user can
            // still switch to an existing one in the dialog below.
            val scheduleSource = if (submission.periods.isEmpty()) {
                ImportScheduleSource.Existing(scheduleId)
            } else {
                ImportScheduleSource.FromPayload
            }

            when (val preview = buildImportPreviewUseCase(submission, target, scheduleSource)) {
                is ImportPreviewResult.Ready -> _uiState.update {
                    it.copy(
                        isPreparingCommit = false,
                        pendingCommit = ImportPendingCommit(
                            callId = request.callId,
                            submission = submission,
                            target = target,
                            preview = preview.preview
                        )
                    )
                }

                ImportPreviewResult.ScheduleNotFound ->
                    failPrepare(request.callId, "所选作息表不存在，请重新选择作息表后再导入。")

                ImportPreviewResult.TimetableNotFound ->
                    failPrepare(request.callId, "目标课程表不存在，可能已被删除。")

                is ImportPreviewResult.Rejected -> failPrepare(request.callId, preview.message)
            }
        }
    }

    /**
     * Switches where the imported class times come from, and rebuilds the summary.
     *
     * Rebuilding rather than patching the preview is what keeps one rule in one place: the same use
     * case that produced the confirmed numbers produces them again for the other source, so the
     * dialog cannot end up describing a combination that was never resolvable.
     */
    fun onScheduleSourceSelected(scheduleSource: ImportScheduleSource) {
        val pending = _uiState.value.pendingCommit ?: return
        if (pending.preview.scheduleSource == scheduleSource) return
        // Guarded like a first preparation: a second tap while the summary is being rebuilt would
        // otherwise race its own result.
        if (_uiState.value.isPreparingCommit) return
        _uiState.update { it.copy(isPreparingCommit = true) }

        viewModelScope.launch {
            when (val preview = buildImportPreviewUseCase(pending.submission, pending.target, scheduleSource)) {
                is ImportPreviewResult.Ready -> _uiState.update { state ->
                    // The confirmation may have been cancelled while this ran.
                    val currentPending = state.pendingCommit ?: return@update state
                    state.copy(
                        isPreparingCommit = false,
                        pendingCommit = currentPending.copy(preview = preview.preview)
                    )
                }

                ImportPreviewResult.ScheduleNotFound ->
                    failPrepare(pending.callId, "所选作息表不存在，请重新选择作息表后再导入。")

                ImportPreviewResult.TimetableNotFound ->
                    failPrepare(pending.callId, "目标课程表不存在，可能已被删除。")

                is ImportPreviewResult.Rejected -> failPrepare(pending.callId, preview.message)
            }
        }
    }

    private fun failCommit(pending: ImportPendingCommit, message: String) {
        _uiState.update { it.copy(isCommitting = false) }
        reject(pending.callId, message)
        emitMessage(message)
    }

    /**
     * Refuses a payload before the user is asked to confirm it.
     *
     * Reported both to the script and to the user: unlike a malformed payload, which the script is
     * expected to explain, this happens to a payload that was acceptable when the script built it.
     */
    private fun failPrepare(callId: String, message: String) {
        _uiState.update { it.copy(isPreparingCommit = false) }
        reject(callId, message)
        emitMessage(message)
    }

    /**
     * Builds the success dialog's text and, when the payload outran the schedule, the way to fix it.
     *
     * The import has already happened at this point, so the only useful thing left to offer is the
     * schedule editor: the courses exist, they simply have no class time until the missing periods are
     * filled in.
     */
    private fun buildSuccessNotice(
        result: TimetableImportResult.Imported,
        preview: ImportPreview
    ): ImportSuccessNotice {
        val missingPeriods = preview.diagnostics.periodsMissingFromSchedule
        val message = buildString {
            append("已导入 ")
            append(result.courseCount)
            append(" 门课程、")
            append(result.sessionCount)
            append(" 条课时。")
            if (preview.target is ImportTarget.NewTimetable) append("新课表已设为当前课表。")
            // Worth saying explicitly: the user asked for the portal's times, and a new template in
            // their schedule list is the evidence that it happened.
            if (preview.scheduleSource == ImportScheduleSource.FromPayload) {
                append("\n\n已按方案提供的时间新建了一个作息表。")
            }
            if (missingPeriods.isNotEmpty()) {
                append("\n\n第 ")
                append(formatPeriodNumbers(missingPeriods))
                append(" 节未在所选作息表中定义，这些课时暂时没有上课时间。")
            }
        }
        return ImportSuccessNotice(
            message = message,
            // Prefer the schedule the write actually left behind: an import that created a template
            // from the portal's times only learns its id once the write ran.
            scheduleIdToFix = (result.scheduleId.takeIf { it > 0L } ?: preview.scheduleId)
                .takeIf { preview.diagnostics.hasScheduleMismatch }
        )
    }

    private fun loadPreset() {
        viewModelScope.launch {
            val preset = getImportPresetsUseCase().firstOrNull { candidate -> candidate.id == presetId }
            if (preset == null) {
                _uiState.update {
                    it.copy(isLoading = false, scriptUnavailable = true)
                }
                emitMessage("找不到该导入方案，可能已被删除。")
                return@launch
            }

            val script = readImportScriptUseCase(preset)
            presetScript = script
            _uiState.update {
                it.copy(
                    isLoading = false,
                    presetName = preset.name,
                    presetDescription = preset.description,
                    startUrl = preset.startUrl,
                    scriptUnavailable = script == null
                )
            }
            if (script == null) emitMessage("该方案的脚本无法读取，请重新选择方案。")
        }
    }

    /**
     * Keeps the schedule picker in step with the database and always has a valid selection, so the
     * import cannot be started against a schedule that was deleted in the meantime.
     */
    private fun observeSchedules() {
        viewModelScope.launch {
            getSchedulesUseCase().collect { schedules ->
                _uiState.update { state ->
                    val options = schedules.map { schedule ->
                        ImportScheduleOptionUi(id = schedule.id, name = schedule.name)
                    }
                    val selected = state.selectedScheduleId
                        ?.takeIf { id -> options.any { option -> option.id == id } }
                    state.copy(
                        scheduleOptions = options,
                        selectedScheduleId = selected ?: options.firstOrNull()?.id
                    )
                }
            }
        }
    }

    /**
     * Streams the timetables an import could overwrite.
     *
     * A chosen target that disappears clears the choice instead of being kept, so the confirmation
     * cannot promise to overwrite something the user can no longer see; falling back to "new timetable"
     * is the safe direction, because that writes nothing over an existing one.
     */
    private fun observeTimetables() {
        viewModelScope.launch {
            getTimetablesUseCase().collect { timetables ->
                _uiState.update { state ->
                    val options = timetables.map { timetable ->
                        ImportTimetableOptionUi(
                            id = timetable.id,
                            name = timetable.name,
                            scheduleId = timetable.scheduleId
                        )
                    }
                    state.copy(
                        timetableOptions = options,
                        selectedTimetableId = state.selectedTimetableId
                            ?.takeIf { id -> options.any { option -> option.id == id } }
                    )
                }
            }
        }
    }

    private fun resolve(callId: String, literal: String? = null) {
        pageHandle?.evaluate(SleepInBridgeProtocol.buildResolveScript(callId, ok = true, literal))
    }

    private fun resolveTrue(callId: String) {
        resolve(callId, SleepInBridgeProtocol.booleanLiteral(true))
    }

    private fun resolveText(callId: String, text: String) {
        resolve(callId, SleepInBridgeProtocol.stringResultLiteral(text))
    }

    private fun reject(callId: String, message: String) {
        pageHandle?.evaluate(
            SleepInBridgeProtocol.buildResolveScript(
                callId = callId,
                ok = false,
                resultLiteral = SleepInBridgeProtocol.stringResultLiteral(message)
            )
        )
    }

    private fun emitMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    companion object {
        /** How long a script's validation function may take before the dialog reports a timeout. */
        private const val VALIDATE_TIMEOUT_MS = 5_000L

        /** Manual factory mirrors the current non-Hilt architecture used across the app. */
        fun factory(
            presetId: String,
            scheduleId: Long?,
            getImportPresetsUseCase: GetImportPresetsUseCase,
            readImportScriptUseCase: ReadImportScriptUseCase,
            getSchedulesUseCase: GetSchedulesUseCase,
            getTimetablesUseCase: GetTimetablesUseCase,
            parseImportSubmissionUseCase: ParseImportSubmissionUseCase,
            buildImportPreviewUseCase: BuildImportPreviewUseCase,
            commitImportUseCase: CommitImportUseCase
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return ImportWebViewModel(
                        presetId = presetId,
                        initialScheduleId = scheduleId,
                        getImportPresetsUseCase = getImportPresetsUseCase,
                        readImportScriptUseCase = readImportScriptUseCase,
                        getSchedulesUseCase = getSchedulesUseCase,
                        getTimetablesUseCase = getTimetablesUseCase,
                        parseImportSubmissionUseCase = parseImportSubmissionUseCase,
                        buildImportPreviewUseCase = buildImportPreviewUseCase,
                        commitImportUseCase = commitImportUseCase
                    ) as T
                }
            }
    }
}

/**
 * Outcome of asking the page to validate a prompt input.
 *
 * Wrapped because an accepted input has a null message while `withTimeoutOrNull` also reports "no
 * answer" as null; the wrapper is what keeps accepted distinguishable from abandoned.
 */
private data class PromptValidationOutcome(val message: String?)
