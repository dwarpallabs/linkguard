package com.dwarpallabs.linkguard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dwarpallabs.linkguard.core.Budget
import com.dwarpallabs.linkguard.core.CheckId
import com.dwarpallabs.linkguard.core.Verdict
import com.dwarpallabs.linkguard.ui.ReportStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Decides what happens to one intercepted link. Survives rotation so checks run once.
 * Holds the link only in memory, for as long as the interception screen exists.
 */
class InterceptViewModel(application: Application) : AndroidViewModel(application) {

    sealed interface State {
        /** Deciding. Nothing is drawn, so a fast safe link is invisible. */
        data object Checking : State

        /** Still checking after 300 ms (short link or server lookup): show "Checking link…". */
        data object CheckingSlow : State

        /** Safe: open [url] in the browser without showing anything. */
        data class Forward(val url: String) : State

        data class Warn(val verdict: Verdict) : State
    }

    private val _state = MutableStateFlow<State>(State.Checking)
    val state: StateFlow<State> = _state

    private val _reportStatus = MutableStateFlow(ReportStatus.IDLE)

    /** Progress of the one report the user may send from the warning screen. */
    val reportStatus: StateFlow<ReportStatus> = _reportStatus

    private var current: String? = null
    private var job: Job? = null

    /** Starts checking [raw], unless it is already being checked. */
    fun check(raw: String) {
        if (raw == current) return
        current = raw
        job?.cancel()
        _state.value = State.Checking
        _reportStatus.value = ReportStatus.IDLE
        val app = getApplication<LinkGuardApp>()
        job = viewModelScope.launch {
            val disabled = Prefs(app).disabledChecks
            val budget = Budget.INTERACTIVE
            val (local, isShortener) = withContext(Dispatchers.IO) {
                app.localEngine.evaluate(raw, disabled = disabled) to app.engine.isShortener(raw)
            }
            val slowCard = launch {
                delay(budget.normalMs)
                if (_state.value == State.Checking) _state.value = State.CheckingSlow
            }
            val hardStop = maxOf(if (isShortener) budget.shortenerMs else budget.normalMs, budget.lookupMs) + HARD_STOP_MARGIN_MS
            val full = withTimeoutOrNull(hardStop) {
                withContext(Dispatchers.IO) { runInterruptible { app.engine.evaluate(raw, budget, disabled) } }
            }
            var verdict = full ?: local
            // First run only: no allowlist in memory yet. A warning may be a false positive on a
            // popular site, so give the start-up download a moment and look again. Safe links
            // never wait; they were forwarded above the moment the verdict was silent.
            if (verdict.level != Verdict.Level.SILENT && !app.hasAllowlist()) {
                withContext(Dispatchers.IO) {
                    runInterruptible {
                        try {
                            app.allowlistWarmUp.get(FIRST_RUN_ALLOWLIST_WAIT_MS, TimeUnit.MILLISECONDS)
                        } catch (_: TimeoutException) {
                            // Still downloading or offline: warn on what we know.
                        }
                    }
                }
                if (app.hasAllowlist()) {
                    val recheck = withContext(Dispatchers.IO) { app.localEngine.evaluate(raw, disabled = disabled) }
                    verdict = reconsider(verdict, recheck)
                }
            }
            slowCard.cancel()
            _state.value = decide(raw, verdict)
        }
    }

    /** Sends the warned link to Dwarpal Labs. Called only because the user tapped Report this link. */
    fun report(verdict: Verdict) {
        if (_reportStatus.value == ReportStatus.SENDING || _reportStatus.value == ReportStatus.SENT) return
        val raw = current ?: return
        _reportStatus.value = ReportStatus.SENDING
        val app = getApplication<LinkGuardApp>()
        // On the app's scope, not viewModelScope: tapping Go back must not cancel a report in flight.
        app.reportScope.launch {
            val ok = Reporter(app).send(reportLink(raw, verdict), reportNote(verdict))
            _reportStatus.value = if (ok) ReportStatus.SENT else ReportStatus.FAILED
        }
    }

    private fun decide(raw: String, verdict: Verdict): State {
        if (verdict.level != Verdict.Level.SILENT) return State.Warn(verdict)
        return State.Forward(forwardUrl(raw, verdict))
    }

    companion object {
        /** Grace on top of the engine's own budget before we give up and use the local verdict. */
        private const val HARD_STOP_MARGIN_MS = 150L

        /** How long a warned link waits for the very first allowlist download (once per install). */
        const val FIRST_RUN_ALLOWLIST_WAIT_MS = 3_000L

        /**
         * After the allowlist arrived: the re-check can only clear a warning (the site is allowlisted),
         * never add one, so the original verdict with its network signals is kept otherwise.
         */
        fun reconsider(original: Verdict, recheck: Verdict): Verdict =
            if (recheck.level == Verdict.Level.SILENT) recheck else original

        /**
         * The URL to open: the unwrapped destination if redirects were followed (skipping the
         * shortener), otherwise exactly what the user tapped.
         */
        fun forwardUrl(raw: String, verdict: Verdict): String {
            val followed = verdict.signals.any { it.check == CheckId.UNWRAP && it.reason != "unwrap.hops_0" }
            return if (followed) verdict.finalUrl?.toUrlString() ?: raw else raw
        }

        /** The link to report: the unwrapped destination when it is a web URL, otherwise what the user tapped. */
        fun reportLink(raw: String, verdict: Verdict): String {
            val final = verdict.finalUrl
            return if (final != null && final.isWeb) final.toUrlString() else raw
        }

        /** Which checks fired, as reason codes, so reviewers see why LinkGuard warned. Never user text. */
        fun reportNote(verdict: Verdict): String {
            val reasons = verdict.signals.filter { it.fired }.sortedByDescending { it.score }.map { it.reason }
            return if (reasons.isEmpty()) "" else "LinkGuard warned: " + reasons.joinToString(", ")
        }
    }
}
