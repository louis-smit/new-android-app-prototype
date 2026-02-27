package no.solver.solverappdemo.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import no.solver.solverappdemo.BuildConfig

/**
 * Displays the app version and build number in a subtle text format.
 * Example output: "v1.0 (13)"
 */
@Composable
fun AppVersionText(
    modifier: Modifier = Modifier
) {
    Text(
        text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}
