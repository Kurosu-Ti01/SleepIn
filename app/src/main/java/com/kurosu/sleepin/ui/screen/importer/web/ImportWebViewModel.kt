package com.kurosu.sleepin.ui.screen.importer.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kurosu.sleepin.domain.repository.TimetableImportResult
import com.kurosu.sleepin.domain.usecase.importer.CommitImportUseCase
import com.kurosu.sleepin.domain.usecase.importer.GetImportPresetsUseCase
import com.kurosu.sleepin.domain.usecase.importer.ImportParseResult
import com.kurosu.sleepin.domain.usecase.importer.ImportSubmission
import com.kurosu.sleepin.domain.usecase.importer.ImportTarget
import com.kurosu.sleepin.domain.usecase.importer.ParseImportSubmissionUseCase
import com.kurosu.sleepin.domain.usecase.importer.ReadImportScriptUseCase
import com.kurosu.sleepin.domain.usecase.schedule.GetSchedulesUseCase
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
 * A payload that is valid and is waiting for the user to confirm it.
 *
 * The submission is kept in the state rather than written immediately so the confirmation dialog can
 * describe exactly what will be stored, and so cancelling really is a no-op.
 *
 * [targetLabel] and [scheduleLabel] are resolved when the request arrives because that is the last
 * moment the user's choices are known to be the ones the write will use.
 */
data class ImportPendingCommit(
    val callId: String,
    val submission: ImportSubmission,
    val targetLabel: String,
    val scheduleLabel: String
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
    val successNotice: String? = null,
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
    /** Leave the import page. [message] is shown by the caller, which outlives this screen. */
    data class Finished(val message: String?) : ImportWebEvent
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
    private val timetableId: Long?,
    initialScheduleId: Long?,
    private val getImportPresetsUseCase: GetImportPresetsUseCase,
    private val readImportScriptUseCase: ReadImportScriptUseCase,
    private val getSchedulesUseCase: GetSchedulesUseCase,
    private val parseImportSubmissionUseCase: ParseImportSubmissionUseCase,
    private val commitImportUseCase: CommitImportUseCase
) : ViewModel() {

    /** Schedule chosen by the caller, or null when this page still has to ask for one. */
    private val chosenScheduleId: Long? = initialScheduleId?.takeIf { id -> id > 0L }

    private val _uiState = MutableStateFlow(
        ImportWebUiState(
            selectedScheduleId = chosenScheduleId,
            // A caller that already knows the schedule — the new-timetable screen does, because picking
            // one is part of filling in that form — goes straight to the page instead of being asked
            // again.
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

    /** Acknowledges the success notice and leaves the page. */
    fun consumeSuccessNotice() {
        val notice = _uiState.value.successNotice ?: return
        _uiState.update { it.copy(successNotice = null) }
        viewModelScope.launch { _events.emit(ImportWebEvent.Finished(notice)) }
    }

    fun onScheduleChange(scheduleId: Long) {
        _uiState.update { it.copy(selectedScheduleId = scheduleId) }
    }

    /**
     * Leaves the preparation step and shows the WebView.
     *
     * The schedule is required up front because the import writes a reference to it and an academic
     * portal reports period numbers without class times; the app will not invent them.
     */
    fun onStartImport() {
        val state = _uiState.value
        if (state.selectedScheduleId == null) {
            emitMessage("请先选择作息表。")
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
        val scheduleId = _uiState.value.selectedScheduleId
        if (scheduleId == null) {
            _uiState.update { it.copy(pendingCommit = null) }
            failCommit(pending, "请先选择作息表。")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(pendingCommit = null, isCommitting = true) }
            val target = timetableId
                ?.let { id -> ImportTarget.ExistingTimetable(id) }
                ?: ImportTarget.NewTimetable

            when (val result = commitImportUseCase(pending.submission, target, scheduleId)) {
                is TimetableImportResult.Imported -> {
                    _uiState.update {
                        it.copy(isCommitting = false, successNotice = importSummary(result))
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
     * Validates a payload and asks the user to confirm it.
     *
     * Rejecting here — rather than showing a dialog for an empty or unusable payload — means the script
     * gets a precise message it can display, which is the only feedback channel a preset author has.
     */
    private fun onCommitRequested(request: ImportWebRequest.Commit) {
        val state = _uiState.value
        if (state.pendingCommit != null || state.isPreparingCommit || state.isCommitting) {
            reject(request.callId, "上一次导入还没有完成，请先处理它。")
            return
        }
        _uiState.update { it.copy(isPreparingCommit = true) }

        viewModelScope.launch {
            when (val parsed = parseImportSubmissionUseCase(request.payloadJson)) {
                is ImportParseResult.Rejected -> {
                    _uiState.update { it.copy(isPreparingCommit = false) }
                    reject(request.callId, parsed.message)
                }

                is ImportParseResult.Accepted -> {
                    if (parsed.submission.courses.isEmpty()) {
                        _uiState.update { it.copy(isPreparingCommit = false) }
                        reject(request.callId, "导入数据里没有任何课程，已取消导入。")
                        return@launch
                    }
                    val current = _uiState.value
                    _uiState.update {
                        it.copy(
                            isPreparingCommit = false,
                            pendingCommit = ImportPendingCommit(
                                callId = request.callId,
                                submission = parsed.submission,
                                targetLabel = if (timetableId == null) "新建课程表" else "覆盖现有课程表",
                                scheduleLabel = current.scheduleOptions
                                    .firstOrNull { option -> option.id == current.selectedScheduleId }
                                    ?.name
                                    .orEmpty()
                            )
                        )
                    }
                }
            }
        }
    }

    private fun failCommit(pending: ImportPendingCommit, message: String) {
        _uiState.update { it.copy(isCommitting = false) }
        reject(pending.callId, message)
        emitMessage(message)
    }

    private fun importSummary(result: TimetableImportResult.Imported): String = buildString {
        append("已导入 ")
        append(result.courseCount)
        append(" 门课程、")
        append(result.sessionCount)
        append(" 条课时。")
        if (timetableId == null) append("新课表已设为当前课表。")
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
            timetableId: Long?,
            scheduleId: Long?,
            getImportPresetsUseCase: GetImportPresetsUseCase,
            readImportScriptUseCase: ReadImportScriptUseCase,
            getSchedulesUseCase: GetSchedulesUseCase,
            parseImportSubmissionUseCase: ParseImportSubmissionUseCase,
            commitImportUseCase: CommitImportUseCase
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return ImportWebViewModel(
                        presetId = presetId,
                        timetableId = timetableId,
                        initialScheduleId = scheduleId,
                        getImportPresetsUseCase = getImportPresetsUseCase,
                        readImportScriptUseCase = readImportScriptUseCase,
                        getSchedulesUseCase = getSchedulesUseCase,
                        parseImportSubmissionUseCase = parseImportSubmissionUseCase,
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
