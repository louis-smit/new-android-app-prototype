package no.solver.solverappdemo.core.deeplink

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import no.solver.solverappdemo.MainActivity
import no.solver.solverappdemo.ui.navigation.DeepLinkConfirmationSheetContent
import no.solver.solverappdemo.ui.theme.SolverAppTheme

/**
 * Transparent overlay activity for confirming QR/NFC deep link commands.
 * Shows a bottom sheet over whatever the user was doing.
 * Used for external deep links (NFC taps, links from other apps).
 * In-app QR scanning uses the same confirmation sheet via DeepLinkViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@AndroidEntryPoint
class DeepLinkConfirmationActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CONFIRMED = "confirmed_deep_link"
    }

    private val viewModel: DeepLinkConfirmationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val uri = intent?.data
        if (uri == null) {
            finish()
            return
        }

        viewModel.resolve(uri)

        setContent {
            SolverAppTheme {
                val state by viewModel.uiState.collectAsState()
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

                ModalBottomSheet(
                    onDismissRequest = { finish() },
                    sheetState = sheetState
                ) {
                    DeepLinkConfirmationSheetContent(
                        state = state,
                        onConfirm = { confirmAndProceed() },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }

    private fun confirmAndProceed() {
        val uri = intent?.data ?: return
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = uri
            putExtra(EXTRA_CONFIRMED, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(mainIntent)
        finish()
    }
}
