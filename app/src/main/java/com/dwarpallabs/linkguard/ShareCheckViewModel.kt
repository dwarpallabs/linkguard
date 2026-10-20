package com.dwarpallabs.linkguard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dwarpallabs.linkguard.core.Budget
import com.dwarpallabs.linkguard.core.LinkExtractor
import com.dwarpallabs.linkguard.core.Verdict
import com.dwarpallabs.linkguard.ui.ReportStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * "Check with LinkGuard": checks every link in a message someone shared to LinkGuard.
 *
 * The message is held only in memory, only while this screen exists, and never leaves the phone.
 * Each link it contains is checked exactly as if the user had tapped it (same checks, same
 * network rules), only with more time, because the user is waiting for an answer, not a page.
 */
class ShareCheckViewModel(application: Application) : AndroidViewModel(application) {

    data class Item(val link: String, val verdict: Verdict)

    sealed interface State {
        data object Checking : State
        data object NoLinks : State
        data class Results(val items: List<Item>, val more: Boolean) : State
    }

    private val _state = MutableStateFlow<State>(State.Checking)
    val state: StateFlow<State> = _state

    private val _reports = MutableStateFlow<Map<Int, ReportStatus>>(emptyMap())

    /** Report progress per result, by its position in the list. */
    val reports: StateFlow<Map<Int, ReportStatus>> = _reports

    /** The message being checked; a different one (shared again while this screen is open) starts over. */
    private var current: String? = null
    private var job: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    fun check(text: String) {
        if (text == current) return
        current = text
        job?.cancel()
        _state.value = State.Checking
        _reports.value = emptyMap()
        val app = getApplication<LinkGuardApp>()
        job = viewModelScope.launch {
            val found = withContext(Dispatchers.IO) { LinkExtractor.extract(text, app.data.psl) }
            if (found.links.isEmpty()) {
                _state.value = State.NoLinks
                return@launch
            }
            // Fresh install: give the first allowlist download a moment, so a popular website in the
            // message is not warned about only because the allowlist has not arrived yet.
            if (!app.hasAllowlist()) waitForAllowlist(app)
            val disabled = Prefs(app).disabledChecks
            val (myBrands, managed) = withContext(Dispatchers.IO) { app.myBrands() to app.managed() }
            val io = Dispatchers.IO.limitedParallelism(PARALLEL_CHECKS)
            val items = found.links.map { link ->
                async(io) { Item(link, runInterruptible { app.engine.evaluate(link, BUDGET, disabled, myBrands, managed) }) }

            }.awaitAll()
            _state.value = State.Results(items, found.more)
        }
    }

    /** Sends the website of result [index] to Dwarpal Labs; only because the user tapped Report. */
    fun report(index: Int) {
        val item = (_state.value as? State.Results)?.items?.getOrNull(index) ?: return
        val status = _reports.value[index]
        if (status == ReportStatus.SENDING || status == ReportStatus.SENT) return
        val app = getApplication<LinkGuardApp>()
        val forMessage = current
        _reports.update { it + (index to ReportStatus.SENDING) }
        app.reportScope.launch {
            val website = InterceptViewModel.reportWebsite(item.link, item.verdict, app.data.psl)
            val ok = website != null && Reporter(app).send(website, InterceptViewModel.reportNote(item.verdict))
            // A newer message may have replaced this one meanwhile; its cards are not this report's.
            if (current == forMessage) _reports.update { it + (index to if (ok) ReportStatus.SENT else ReportStatus.FAILED) }
        }

    }

    private suspend fun waitForAllowlist(app: LinkGuardApp) = withContext(Dispatchers.IO) {
        runInterruptible {
            try {
                app.allowlistWarmUp.get(InterceptViewModel.FIRST_RUN_ALLOWLIST_WAIT_MS, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                // Offline or slow: check with what we have.
            }
        }
    }

    companion object {
        /** Per link. The user asked and is waiting for the answer, so network checks get longer than on a tap. */
        val BUDGET = Budget(normalMs = 3_000, shortenerMs = 5_000, lookupMs = 3_000)

        /** Links checked at once: enough to finish ten in a few seconds, few enough for a 2 GB phone. */
        const val PARALLEL_CHECKS = 4
    }
}
