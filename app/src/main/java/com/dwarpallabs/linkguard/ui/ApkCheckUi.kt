package com.dwarpallabs.linkguard.ui

import com.dwarpallabs.linkguard.ApkInspector

/** State of "Check what this app can do" on an app-download warning (roadmap B3). */
sealed interface ApkCheckUi {
    data object Idle : ApkCheckUi
    data class Running(val done: Long, val total: Long?) : ApkCheckUi
    data class Done(val report: ApkInspector.Report) : ApkCheckUi
    data object TooBig : ApkCheckUi
    data object NotAnApp : ApkCheckUi
    data object Failed : ApkCheckUi
}
