package no.solver.solverapp.features.objects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import no.solver.solverapp.R
import no.solver.solverapp.data.models.ObjectStatusColor
import no.solver.solverapp.data.models.OnlineState
import no.solver.solverapp.data.models.SolverObject
import no.solver.solverapp.ui.theme.SolverAppTheme

@Composable
fun ObjectRow(
    solverObject: SolverObject,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ObjectIcon(
                objectTypeId = solverObject.objectTypeId,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = solverObject.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (solverObject.latitude != null && solverObject.longitude != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_location),
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${String.format("%.4f", solverObject.latitude)}, ${String.format("%.4f", solverObject.longitude)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (solverObject.onlineState != OnlineState.NOT_APPLICABLE) {
                    OnlineIndicator(onlineState = solverObject.onlineState)
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(start = 60.dp))
    }
}

@Composable
private fun ObjectIcon(
    objectTypeId: Int,
    modifier: Modifier = Modifier
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_cube),
        contentDescription = null,
        modifier = modifier,
        tint = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun OnlineIndicator(
    onlineState: OnlineState,
    modifier: Modifier = Modifier
) {
    val color = when (onlineState) {
        OnlineState.ONLINE -> Color(0xFF4CAF50) // Green
        OnlineState.OFFLINE -> Color(0xFFF44336) // Red
        OnlineState.UNKNOWN -> Color.Gray
        OnlineState.NOT_APPLICABLE -> Color.Gray
    }

    Icon(
        painter = painterResource(id = R.drawable.ic_globe),
        contentDescription = when (onlineState) {
            OnlineState.ONLINE -> "Online"
            OnlineState.OFFLINE -> "Offline"
            else -> "Status unknown"
        },
        modifier = modifier.size(20.dp),
        tint = color
    )
}

@Preview(showBackground = true)
@Composable
private fun ObjectRowPreview() {
    SolverAppTheme {
        ObjectRow(
            solverObject = SolverObject(
                id = 1,
                name = "Meeting Room A",
                objectTypeId = 1,
                status = "Available",
                latitude = 59.9139,
                longitude = 10.7522,
                active = true,
                online = true,
                state = 1
            ),
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ObjectRowOfflinePreview() {
    SolverAppTheme {
        ObjectRow(
            solverObject = SolverObject(
                id = 2,
                name = "Storage Unit B with a very long name that should truncate",
                objectTypeId = 2,
                status = "Locked",
                active = true,
                online = false,
                state = 2
            ),
            onClick = {}
        )
    }
}
