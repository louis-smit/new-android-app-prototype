package no.solver.solverappdemo.features.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import no.solver.solverappdemo.BuildConfig
import no.solver.solverappdemo.features.auth.models.Session

enum class MoreItem(
    val label: String,
    val icon: ImageVector
) {
    PAYMENTS("Payments", Icons.Default.Star),
    VISIT("Visit", Icons.Default.AccountCircle),
    LOGS("Logs", Icons.Default.DateRange),
    DEBUG("Debug", Icons.Default.Build),
    DANALOCK_DEMO("Danalock Demo", Icons.Default.Lock),
    MASTERLOCK_DEMO("Masterlock Demo", Icons.Default.Lock);

    companion object {
        val productionItems = listOf(PAYMENTS, VISIT, LOGS)
        val debugItems = listOf(DEBUG, DANALOCK_DEMO, MASTERLOCK_DEMO)

        val visibleItems: List<MoreItem>
            get() = if (BuildConfig.DEBUG) entries.toList() else productionItems
    }
}

@Composable
fun MoreScreen(
    onSignOut: () -> Unit,
    onNavigateToItem: (MoreItem) -> Unit = {},
    viewModel: MoreViewModel = hiltViewModel()
) {
    val currentSession by viewModel.currentSession.collectAsState()
    val isSigningOut by viewModel.isSigningOut.collectAsState()
    var showSignOutConfirmation by remember { mutableStateOf(false) }

    if (showSignOutConfirmation) {
        SignOutConfirmationDialog(
            onConfirm = {
                showSignOutConfirmation = false
                viewModel.signOut()
                onSignOut()
            },
            onDismiss = { showSignOutConfirmation = false }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = WindowInsets.statusBars.asPaddingValues()
    ) {
        // Profile Section
        currentSession?.let { session ->
            item {
                MoreProfileSection(session = session)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }

        // Features Header
        item {
            Text(
                text = "FEATURES",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Navigation Items
        items(MoreItem.visibleItems) { item ->
            MoreItemRow(
                item = item,
                onClick = { onNavigateToItem(item) }
            )
        }

        // Sign Out Section
        if (currentSession != null) {
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SignOutButton(
                    isSigningOut = isSigningOut,
                    onClick = { showSignOutConfirmation = true }
                )
            }
        }
    }
}

@Composable
private fun MoreProfileSection(
    session: Session,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = session.displayName,
                style = MaterialTheme.typography.titleMedium
            )

            session.email?.let { email ->
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = session.provider.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MoreItemRow(
    item: MoreItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = { Text(item.label) },
        leadingContent = {
            Icon(
                imageVector = item.icon,
                contentDescription = null
            )
        },
        modifier = modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun SignOutButton(
    isSigningOut: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = {
            Text(
                text = "Sign Out",
                color = MaterialTheme.colorScheme.error
            )
        },
        leadingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        trailingContent = {
            if (isSigningOut) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        },
        modifier = modifier.clickable(enabled = !isSigningOut, onClick = onClick)
    )
}

@Composable
private fun SignOutConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign Out") },
        text = { Text("Are you sure you want to sign out of this account?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "Sign Out",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
