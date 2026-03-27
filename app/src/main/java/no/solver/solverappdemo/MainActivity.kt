package no.solver.solverappdemo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import no.solver.solverappdemo.core.deeplink.DeepLinkConfirmationActivity
import no.solver.solverappdemo.core.deeplink.DeepLinkParser
import no.solver.solverappdemo.core.deeplink.DeepLinkViewModel
import no.solver.solverappdemo.features.objects.payment.StripePaymentHandler
import no.solver.solverappdemo.features.objects.result.ActionResultCenter
import no.solver.solverappdemo.ui.navigation.AppNavHost
import no.solver.solverappdemo.ui.theme.SolverAppTheme
import javax.inject.Inject

/**
 * Main activity for the SolverApp.
 * 
 * Handles deep links for:
 * - QR/NFC commands: solverapp://qr/{command}/{tag}
 * - Universal Links: https://solver.no/qr/{command}/{tag}
 * 
 * Note: Vipps OAuth is handled via AppAuth's RedirectUriReceiverActivity,
 * which automatically captures the OAuth redirect and returns the result
 * to the calling activity via startActivityForResult/ActivityResultLauncher.
 * 
 * This means we don't need to manually handle deep links for Vipps OAuth here -
 * the LoginScreen's ActivityResultLauncher handles the callback.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    @Inject
    lateinit var stripePaymentHandler: StripePaymentHandler

    @Inject
    lateinit var actionResultCenter: ActionResultCenter

    // Deep link handler ViewModel - shared with Compose via hiltViewModel()
    private val deepLinkViewModel: DeepLinkViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize Stripe PaymentSheet - must be done in onCreate before presenting
        stripePaymentHandler.initialize(this)
        
        // Handle deep link from cold start
        handleDeepLinkIntent(intent)
        
        setContent {
            SolverAppTheme {
                AppNavHost(
                    deepLinkViewModel = deepLinkViewModel,
                    actionResultCenter = actionResultCenter
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle deep link when app is already running (warm start)
        handleDeepLinkIntent(intent)
    }

    /**
     * Process incoming intent for QR/NFC deep links.
     * Delegates to DeepLinkViewModel for execution.
     */
    private fun handleDeepLinkIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        val isQrCommandDeepLink = DeepLinkParser.isQrCommandDeepLink(uri)
        val isPaymentCallbackDeepLink = DeepLinkParser.isPaymentCallbackDeepLink(uri)

        // Vipps OAuth is handled by AppAuth's RedirectUriReceiverActivity.
        if (isQrCommandDeepLink || isPaymentCallbackDeepLink) {
            val confirmed = intent.getBooleanExtra(
                DeepLinkConfirmationActivity.EXTRA_CONFIRMED, false
            )
            Log.i(TAG, "📲 Processing deep link: $uri (confirmed=$confirmed)")
            deepLinkViewModel.handle(uri, confirmed = confirmed)
        }
    }
}
