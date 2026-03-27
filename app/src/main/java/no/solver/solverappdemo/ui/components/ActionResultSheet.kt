package no.solver.solverappdemo.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import no.solver.solverappdemo.features.objects.result.ActionResultCenter
import no.solver.solverappdemo.features.objects.result.ActionResultPresentation
import no.solver.solverappdemo.features.objects.result.ActionResultState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionResultSheetHost(
    center: ActionResultCenter,
    modifier: Modifier = Modifier
) {
    val current by center.current.collectAsState()

    current?.let { result ->
        val canDismiss = result.state != ActionResultState.PROCESSING
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        BackHandler(enabled = !canDismiss) {}

        ModalBottomSheet(
            onDismissRequest = {
                if (canDismiss) {
                    center.dismissCurrent()
                }
            },
            sheetState = sheetState,
            modifier = modifier
        ) {
            ActionResultSheetContent(
                result = result,
                onDismiss = { center.dismissCurrent() }
            )
        }
    }
}

@Composable
fun ActionResultSheetContent(
    result: ActionResultPresentation,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = when (result.state) {
        ActionResultState.PROCESSING -> MaterialTheme.colorScheme.primary
        ActionResultState.SUCCESS -> Color(0xFF2E7D32)
        ActionResultState.FAILURE -> MaterialTheme.colorScheme.error
        ActionResultState.CANCELLED -> Color(0xFFED6C02)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = result.kind.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        if (result.state == ActionResultState.PROCESSING) {
            CircularProgressIndicator(
                modifier = Modifier.size(56.dp),
                color = tint,
                strokeWidth = 4.dp
            )
        } else {
            Icon(
                imageVector = when (result.state) {
                    ActionResultState.SUCCESS -> Icons.Default.CheckCircle
                    ActionResultState.FAILURE -> Icons.Default.Error
                    ActionResultState.CANCELLED -> Icons.Default.Close
                    ActionResultState.PROCESSING -> Icons.Default.CheckCircle
                },
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(62.dp)
            )
        }

        Text(
            text = result.title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )

        Text(
            text = result.message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        result.timestampText?.let { timestamp ->
            Text(
                text = timestamp,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (result.state != ActionResultState.PROCESSING) {
            result.secondaryActionTitle?.takeIf { !it.isBlank() }?.let { secondaryActionTitle ->
                result.secondaryAction?.let { secondaryAction ->
                    TextButton(onClick = secondaryAction) {
                        Text(secondaryActionTitle)
                    }
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Done")
            }
        }
    }
}
