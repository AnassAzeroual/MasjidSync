package com.mosquetracker.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                content()
            },
        )
    }
}

@Composable
fun KeyValueRow(
    label: String,
    value: String,
    leadingIcon: ImageVector? = null,
    leadingPainter: Painter? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            leadingIcon != null -> Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            leadingPainter != null -> Icon(leadingPainter, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        if (leadingIcon != null || leadingPainter != null) Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )
    }
}

/**
 * A problem the user can fix with one tap: a missing permission, detection switched off while
 * inside a zone, an armed action that cannot run.
 */
@Composable
fun AlertBanner(
    title: String,
    message: String,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryText: String? = null,
    onSecondary: () -> Unit = {},
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                val colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)
                if (secondaryText != null) {
                    TextButton(onClick = onSecondary, colors = colors) { Text(secondaryText) }
                }
                TextButton(onClick = onAction, colors = colors) { Text(actionText) }
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
