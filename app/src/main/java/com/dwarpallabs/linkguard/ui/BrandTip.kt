package com.dwarpallabs.linkguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dwarpallabs.linkguard.R
import com.dwarpallabs.linkguard.core.Brand
import com.dwarpallabs.linkguard.ui.InterstitialModel.Reason
import com.dwarpallabs.linkguard.ui.components.PrimaryButton
import com.dwarpallabs.linkguard.ui.components.Status
import com.dwarpallabs.linkguard.ui.components.StatusBadge

/** The tip after Go back on a fake-brand warning (roadmap B6): the brand and its real website, if known. */
data class BrandTip(val brand: String, val site: String?) {
    companion object {
        /**
         * A tip for warnings about a brand being faked: look-alike websites, odd addresses using a brand's
         * name, and UPI requests using a brand's name. The real website comes from LinkGuard's brand
         * list: its first full domain (suffix-only entries like ".sbi" are skipped), or none.
         */
        fun of(reason: Reason, brands: List<Brand>): BrandTip? {
            val name = when (reason) {
                is Reason.BrandLookalike -> reason.brand
                is Reason.WeirdHost -> reason.brand
                is Reason.Upi -> reason.brand
                else -> null
            } ?: return null
            val brand = brands.firstOrNull { it.name == name }
            return BrandTip(name, brand?.officialDomains?.firstOrNull { !it.startsWith(".") })
        }
    }
}

/** Calm, one screen: what the user did, and how to reach the real brand next time. Done closes it. */
@Composable
fun BrandTipScreen(tip: BrandTip, onDone: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(horizontal = 24.dp, vertical = 32.dp).testTag("brand_tip"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusBadge(Status.SAFE)
            Text(stringResource(R.string.brand_tip_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                if (tip.site != null) stringResource(R.string.brand_tip_body, tip.brand, tip.site)
                else stringResource(R.string.brand_tip_body_no_site, tip.brand),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag("brand_tip_body"),
            )
            Spacer(Modifier.height(8.dp))
            PrimaryButton(stringResource(R.string.brand_tip_done), onDone, Modifier.testTag("brand_tip_done"))
        }
    }
}
