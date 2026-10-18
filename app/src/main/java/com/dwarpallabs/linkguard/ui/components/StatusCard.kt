package com.dwarpallabs.linkguard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dwarpallabs.linkguard.ui.theme.LinkguardTheme

/**
 * Answers "Is LinkGuard protecting me?" on Home. On: safe tint with the tick badge. Off: caution tint
 * with the "!" badge, and [content] holds the explanation and the one action that turns it on.
 * Text stays ink; the tint and badge carry the state, never coloured text.
 */
@Composable
fun StatusCard(on: Boolean, title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    val status = LinkguardTheme.status
    Column(
        modifier
            .fillMaxWidth()
            .background(if (on) status.safeTint else status.cautionTint, RoundedCornerShape(12.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.semantics(mergeDescendants = true) {},
        ) {
            StatusBadge(
                if (on) Status.SAFE else Status.CAUTION,
                container = MaterialTheme.colorScheme.background,
            )
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        content()
    }
}
