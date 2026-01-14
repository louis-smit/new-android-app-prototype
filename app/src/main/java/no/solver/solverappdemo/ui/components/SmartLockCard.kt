package no.solver.solverappdemo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import no.solver.solverappdemo.R
import no.solver.solverappdemo.core.bluetooth.smartlock.SmartLockBrand
import no.solver.solverappdemo.core.bluetooth.smartlock.SmartLockCapabilities
import no.solver.solverappdemo.core.bluetooth.smartlock.SmartLockState
import no.solver.solverappdemo.core.bluetooth.smartlock.SmartLockStatus
import no.solver.solverappdemo.features.objects.detail.CachedOperation
import no.solver.solverappdemo.ui.theme.SolverAppTheme
import java.util.Date

@Composable
fun SmartLockCard(
    lockBrand: SmartLockBrand,
    lockStatus: SmartLockStatus,
    capabilities: SmartLockCapabilities,
    operation: CachedOperation,
    debugResult: String?,
    onUnlock: () -> Unit,
    onLock: () -> Unit,
    onCheckStatus: () -> Unit,
    onGetKeys: () -> Unit,
    onClearKeys: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAnyExecuting = operation != CachedOperation.NONE
    var showDebugButtons by remember { mutableStateOf(false) }
    var debugTapCount by remember { mutableIntStateOf(0) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Lock brand icon + name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = painterResource(
                        id = when (lockBrand) {
                            SmartLockBrand.DANALOCK -> R.drawable.ic_lock
                            SmartLockBrand.MASTERLOCK -> R.drawable.ic_lock
                        }
                    ),
                    contentDescription = null,
                    tint = when (lockBrand) {
                        SmartLockBrand.DANALOCK -> Color(0xFF2196F3) // Blue
                        SmartLockBrand.MASTERLOCK -> Color(0xFFFF9800) // Orange
                    },
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = lockBrand.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            HorizontalDivider()

            // Status row: lock state + battery + range
            if (lockStatus.hasValidTokens) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Lock state
                    if (lockStatus.state != SmartLockState.UNKNOWN) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = when (lockStatus.state) {
                                        SmartLockState.LOCKED -> R.drawable.ic_lock
                                        SmartLockState.UNLOCKED -> R.drawable.ic_lock_open
                                        SmartLockState.UNKNOWN -> R.drawable.ic_lock
                                    }
                                ),
                                contentDescription = null,
                                tint = lockStatus.state.color,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = lockStatus.state.displayText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = lockStatus.state.color
                            )
                        }
                    }

                    // Battery (only if supported)
                    if (capabilities.supportsBatteryReading && lockStatus.batteryLevel != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_battery),
                                contentDescription = null,
                                tint = batteryColor(lockStatus.batteryLevel),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${lockStatus.batteryLevel}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = batteryColor(lockStatus.batteryLevel)
                            )
                        }
                    }

                    // Range (only if supported)
                    if (capabilities.supportsRangeDetection) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_bluetooth),
                                contentDescription = null,
                                tint = if (lockStatus.inRange) Color(0xFF4CAF50) else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (lockStatus.inRange) "In range" else "Out of range",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (lockStatus.inRange) Color(0xFF4CAF50) else Color.Gray
                            )
                        }
                    }
                }

                // Token info
                lockStatus.tokensExpireAt?.let { expiresAt ->
                    val remainingMinutes = ((expiresAt.time - Date().time) / 60000).coerceAtLeast(0)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_check_circle),
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Keys cached successfully",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Valid for $remainingMinutes minutes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "Keys not loaded",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Unlock button
                if (capabilities.supportsUnlock) {
                    Button(
                        onClick = onUnlock,
                        enabled = !isAnyExecuting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50) // Green
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (operation == CachedOperation.UNLOCKING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_lock_open),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Unlock")
                    }
                }

                // Lock button (only if supported)
                if (capabilities.supportsLock) {
                    Button(
                        onClick = onLock,
                        enabled = !isAnyExecuting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF44336) // Red
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (operation == CachedOperation.LOCKING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_lock),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Lock")
                    }
                }

                // Check status button (for Masterlock)
                if (capabilities.supportsManualStatusCheck) {
                    Button(
                        onClick = onCheckStatus,
                        enabled = !isAnyExecuting,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (operation == CachedOperation.CHECKING_STATUS) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Status")
                    }
                }
            }

            // Debug result section (tap 3x to reveal hidden buttons)
            if (debugResult != null) {
                HorizontalDivider()
                Text(
                    text = debugResult,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            debugTapCount++
                            if (debugTapCount >= 3) {
                                showDebugButtons = !showDebugButtons
                                debugTapCount = 0
                            }
                        }
                )
            }

            // Hidden debug buttons (Get Keys / Clear Keys)
            if (showDebugButtons) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onGetKeys,
                        enabled = !isAnyExecuting,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (operation == CachedOperation.FETCHING_KEYS) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_lock),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Get keys")
                    }

                    Button(
                        onClick = onClearKeys,
                        enabled = !isAnyExecuting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF9800) // Orange
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_refresh),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear keys")
                    }
                }
            }
        }
    }
}

@Composable
private fun batteryColor(level: Int): Color {
    return when {
        level < 20 -> Color(0xFFF44336) // Red
        level < 50 -> Color(0xFFFF9800) // Orange
        else -> Color(0xFF4CAF50) // Green
    }
}

@Preview(showBackground = true)
@Composable
private fun SmartLockCardDanalockPreview() {
    SolverAppTheme {
        SmartLockCard(
            lockBrand = SmartLockBrand.DANALOCK,
            lockStatus = SmartLockStatus(
                state = SmartLockState.LOCKED,
                batteryLevel = 77,
                inRange = true,
                hasValidTokens = true,
                tokensExpireAt = Date(System.currentTimeMillis() + 86_400_000)
            ),
            capabilities = SmartLockCapabilities.DANALOCK,
            operation = CachedOperation.NONE,
            debugResult = "✅ Keys cached successfully\nDevice: e5:c8:3a:89:b6:f9\nValid for 1439 minutes",
            onUnlock = {},
            onLock = {},
            onCheckStatus = {},
            onGetKeys = {},
            onClearKeys = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SmartLockCardMasterlockPreview() {
    SolverAppTheme {
        SmartLockCard(
            lockBrand = SmartLockBrand.MASTERLOCK,
            lockStatus = SmartLockStatus(
                state = SmartLockState.UNKNOWN,
                batteryLevel = null,
                inRange = true,
                hasValidTokens = true,
                tokensExpireAt = null
            ),
            capabilities = SmartLockCapabilities.MASTERLOCK,
            operation = CachedOperation.NONE,
            debugResult = null,
            onUnlock = {},
            onLock = {},
            onCheckStatus = {},
            onGetKeys = {},
            onClearKeys = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SmartLockCardNoTokensPreview() {
    SolverAppTheme {
        SmartLockCard(
            lockBrand = SmartLockBrand.DANALOCK,
            lockStatus = SmartLockStatus.UNKNOWN,
            capabilities = SmartLockCapabilities.DANALOCK,
            operation = CachedOperation.NONE,
            debugResult = null,
            onUnlock = {},
            onLock = {},
            onCheckStatus = {},
            onGetKeys = {},
            onClearKeys = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}
