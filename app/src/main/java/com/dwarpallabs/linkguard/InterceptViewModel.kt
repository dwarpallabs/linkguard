package com.dwarpallabs.linkguard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dwarpallabs.linkguard.core.Budget
import com.dwarpallabs.linkguard.core.CheckId
import com.dwarpallabs.linkguard.core.NormalisedUrl
import com.dwarpallabs.linkguard.core.PublicSuffixList

import com.dwarpallabs.linkguard.core.Verdict
import com.dwarpallabs.linkguard.ui.ApkCheckUi
import com.dwarpallabs.linkguard.ui.ReportStatus
import kotlinx.coroutines.isActive

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

    private val _apkCheck = MutableStateFlow<ApkCheckUi>(ApkCheckUi.Idle)

    /** "Check what this app can do" on an app-download warning (roadmap B3). */
    val apkCheck: StateFlow<ApkCheckUi> = _apkCheck
    private var apkJob: Job? = null

    /** Starts checking [raw], unless it is already being checked. */
    fun check(raw: String) {
        if (raw == current) return
        current = raw
        job?.cancel()
        _state.value = State.Checking
        _reportStatus.value = ReportStatus.IDLE
        apkJob?.cancel()
        _apkCheck.value = ApkCheckUi.Idle

        val app = getApplication<LinkGuardApp>()
        job = viewModelScope.launch {
            val disabled = Prefs(app).disabledChecks
            val budget = Budget.INTERACTIVE
            val managed = withContext(Dispatchers.IO) { app.managed() }
            val (myBrands, local, isShortener) = withContext(Dispatchers.IO) {
                val mine = app.myBrands()
                Triple(mine, app.localEngine.evaluate(raw, disabled = disabled, myBrands = mine, managed = managed), app.engine.isShortener(raw))
            }
            val slowCard = launch {
                delay(budget.normalMs)
                if (_state.value == State.Checking) _state.value = State.CheckingSlow
            }
            val hardStop = maxOf(if (isShortener) budget.shortenerMs else budget.normalMs, budget.lookupMs) + HARD_STOP_MARGIN_MS
            val full = withTimeoutOrNull(hardStop) {
                withContext(Dispatchers.IO) { runInterruptible { app.engine.evaluate(raw, budget, disabled, myBrands, managed) } }
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
                    val recheck = withContext(Dispatchers.IO) { app.localEngine.evaluate(raw, disabled = disabled, myBrands = myBrands, managed = managed) }
                    verdict = reconsider(verdict, recheck)
                }
            }
            slowCard.cancel()
            // A website the user said was fine skips the soft warning (never the strong one, never APK or UPI).
            val trusted = trustSilences(verdict) &&
                withContext(Dispatchers.IO) { reportDomain(raw, verdict, app.data.psl)?.let(app.memory::isTrusted) == true }
            val decision = decide(raw, verdict, trusted)
            _state.value = decision
            if (decision is State.Forward) learnBrand(app, verdict)

        }
    }

    /**
     * Sends the warned website to Dwarpal Labs. Called only because the user tapped Report this link
     * and then Report. Only the website is sent, never the path, query or fragment of the link.
     */
    fun report(verdict: Verdict) {
        if (_reportStatus.value == ReportStatus.SENDING || _reportStatus.value == ReportStatus.SENT) return
        val raw = current ?: return
        _reportStatus.value = ReportStatus.SENDING
        val app = getApplication<LinkGuardApp>()
        // On the app's scope, not viewModelScope: tapping Go back must not cancel a report in flight.
        app.reportScope.launch {
            val website = reportWebsite(raw, verdict, app.data.psl)
            val ok = website != null && Reporter(app).send(website, reportNote(verdict))
            _reportStatus.value = if (ok) ReportStatus.SENT else ReportStatus.FAILED
        }
    }

    /**
     * The user tapped "Check what this app can do": download the app file from the link (the only
     * time LinkGuard contacts a link's destination, and only on this tap), look inside, delete it.
     * Leaving the screen or tapping Stop cancels it.
     */
    fun checkApk(verdict: Verdict) {
        if (_apkCheck.value is ApkCheckUi.Running) return
        val raw = current ?: return
        val url = forwardUrl(raw, verdict)
        val app = getApplication<LinkGuardApp>()
        _apkCheck.value = ApkCheckUi.Running(0, null)
        apkJob = viewModelScope.launch(Dispatchers.IO) {
            var shown = 0L
            val result = ApkInspector.check(
                url, app.cacheDir, app.packageManager, app.data.brands,
                onProgress = { done, total ->
                    if (done - shown >= PROGRESS_STEP || done == total) {
                        shown = done
                        _apkCheck.value = ApkCheckUi.Running(done, total)
                    }
                },
                isCancelled = { !isActive },
            )
            if (!isActive) return@launch
            _apkCheck.value = when (result) {
                is ApkInspector.Result.Ok -> ApkCheckUi.Done(result.report)
                ApkInspector.Result.TooBig -> ApkCheckUi.TooBig
                ApkInspector.Result.NotAnApp -> ApkCheckUi.NotAnApp
                ApkInspector.Result.Failed -> ApkCheckUi.Failed
            }
        }
    }

    fun stopApkCheck() {
        apkJob?.cancel()
        _apkCheck.value = ApkCheckUi.Idle
    }

    /**
     * The user tapped Open anyway: remember the website's name (never the link) so the next app
     * open can ask "Was that link a scam?". Replaces any earlier question.
     *
     * Synchronous on purpose: the screen finishes right after, and the process may go with it. It
     * is cheap, because the bundled data is already loaded (the verdict needed it) and the write is
     * one small SharedPreferences apply, which Android flushes when the activity stops.
     */
    fun rememberOpenedAnyway(verdict: Verdict) {
        val raw = current ?: return
        val app = getApplication<LinkGuardApp>()
        val domain = reportDomain(raw, verdict, app.data.psl) ?: return
        AfterOpenStore.of(app).remember(domain, firedReasons(verdict))
    }


    private fun decide(raw: String, verdict: Verdict, trusted: Boolean): State {
        if (verdict.level != Verdict.Level.SILENT && !trusted) return State.Warn(verdict)
        return State.Forward(forwardUrl(raw, verdict))
    }

    /**
     * After a link was forwarded: if it was a brand's real website, count one open (only the brand,
     * as an HMAC; never the link). Runs on the app's scope after the forward, so it never delays a
     * safe link and still finishes when this screen is gone.
     */

    private fun learnBrand(app: LinkGuardApp, verdict: Verdict) {
        val url = verdict.finalUrl ?: return
        app.reportScope.launch {
            val brand = app.engine.ownerBrand(url) ?: return@launch
            if (app.memory.countOpen(brand.name)) app.forgetMyBrandsCache()
        }
    }

    companion object {
        /** Progress updates at most every 256 KB, so a big download doesn't redraw the screen constantly. */
        private const val PROGRESS_STEP = 256 * 1024L

        /** Warnings a "trusted by me" website still always gets. */
        private val NEVER_TRUSTED = setOf(CheckId.APK_DOWNLOAD, CheckId.UPI_INTENT)

        /** True if trusting the website may silence this warning: soft only, and no APK or UPI signal. */
        fun trustSilences(verdict: Verdict): Boolean =
            verdict.level == Verdict.Level.SOFT && verdict.signals.none { it.fired && it.check in NEVER_TRUSTED }

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

        /**
         * The website a warning is about: the unwrapped destination when it is a web URL, otherwise
         * what the user tapped. Its registrable domain, else its host; never an IP address.
         */
        fun reportDomain(raw: String, verdict: Verdict, psl: PublicSuffixList): String? =
            Reporter.reportDomain(warnedUrl(raw, verdict, psl))

        /**
         * The link a report sends: the website only, never the path, query, fragment, port or user
         * info. An IP address is sent as itself; the server keeps such reports but never counts them.
         */
        fun reportWebsite(raw: String, verdict: Verdict, psl: PublicSuffixList): String? {
            val url = warnedUrl(raw, verdict, psl)
            val website = Reporter.reportDomain(url)
                ?: url?.host?.takeIf { url.isWeb && it.isNotEmpty() }?.let { if (':' in it && !it.startsWith("[")) "[$it]" else it }
            return website?.let(Reporter::websiteLink)
        }

        private fun warnedUrl(raw: String, verdict: Verdict, psl: PublicSuffixList): NormalisedUrl? {
            val final = verdict.finalUrl
            return if (final != null && final.isWeb) final else NormalisedUrl.parse(raw, psl)
        }

        /**
         * Reason codes of the checks that fired, strongest first. Never user text: codes are fixed
         * tokens, and any code that is not a plain token (one carries a UPI link's host) is dropped.
         */
        fun firedReasons(verdict: Verdict): List<String> =
            verdict.signals.filter { it.fired }.sortedByDescending { it.score }.map { it.reason }
                .filter(PendingQuestion::isValidReason)


        /** Which checks fired, as reason codes, so reviewers see why LinkGuard warned. Never user text. */
        fun reportNote(verdict: Verdict): String = reportNote(firedReasons(verdict))

        fun reportNote(reasons: List<String>): String {
            val safe = reasons.filter(PendingQuestion::isValidReason)
            return if (safe.isEmpty()) "" else "LinkGuard warned: " + safe.joinToString(", ")
        }


    }
}
