package no.solver.solverappdemo.features.objects.payment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import no.solver.solverappdemo.data.models.PaymentMethod
import no.solver.solverappdemo.data.models.PaymentStatus
import no.solver.solverappdemo.ui.theme.SolverAppTheme
import javax.inject.Inject

/**
 * State for payment callback polling.
 */
sealed class PaymentCallbackState {
    data object Polling : PaymentCallbackState()
    data object Success : PaymentCallbackState()
    data class Cancelled(val reason: String) : PaymentCallbackState()
    data class Failure(val reason: String) : PaymentCallbackState()
}

/**
 * ViewModel for payment callback screen.
 */
@HiltViewModel
class PaymentCallbackViewModel @Inject constructor(
    private val paymentStatusPoller: PaymentStatusPoller,
    private val paymentStorage: PaymentStorage,
    private val subscriptionStorage: SubscriptionStorage
) : ViewModel() {

    private val _state = MutableStateFlow<PaymentCallbackState>(PaymentCallbackState.Polling)
    val state: StateFlow<PaymentCallbackState> = _state.asStateFlow()

    val statusTitle: String
        get() = when (_state.value) {
            is PaymentCallbackState.Polling -> "Checking Payment"
            is PaymentCallbackState.Success -> "Success"
            is PaymentCallbackState.Cancelled -> "Cancelled"
            is PaymentCallbackState.Failure -> "Payment Failed"
        }

    val statusMessage: String
        get() = when (val state = _state.value) {
            is PaymentCallbackState.Polling -> "Please wait while we verify your payment..."
            is PaymentCallbackState.Success -> "Your payment was successful"
            is PaymentCallbackState.Cancelled -> state.reason
            is PaymentCallbackState.Failure -> "Payment failed: ${state.reason}"
        }

    /**
     * Start polling for payment status.
     */
    fun startPolling(method: PaymentMethod, reference: String) {
        viewModelScope.launch {
            // Check if this is a recurring subscription payment
            val isRecurring = subscriptionStorage.getPendingSubscription()
                ?.option
                ?.subscriptionType
                ?.isRecurring ?: false

            val status = paymentStatusPoller.pollStatus(
                method = method,
                reference = reference,
                isRecurringSubscription = isRecurring
            )

            _state.value = when {
                status.isSuccess -> PaymentCallbackState.Success
                status == PaymentStatus.STOPPED || status == PaymentStatus.CANCELLED -> {
                    PaymentCallbackState.Cancelled(status.displayName)
                }
                else -> PaymentCallbackState.Failure(status.displayName)
            }
        }
    }

    /**
     * Clear pending payment/subscription data.
     */
    fun clearPendingData() {
        paymentStorage.clearPendingPayment()
        subscriptionStorage.clearPendingSubscription()
    }
}

/**
 * Screen shown after returning from external payment (Vipps/Card).
 * Matches iOS PaymentCallbackView.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentCallbackScreen(
    method: PaymentMethod,
    reference: String,
    onDone: () -> Unit,
    viewModel: PaymentCallbackViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(method, reference) {
        viewModel.startPolling(method, reference)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payment Status") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Status Icon
            when (state) {
                is PaymentCallbackState.Polling -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(80.dp),
                        strokeWidth = 4.dp
                    )
                }
                is PaymentCallbackState.Success -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        modifier = Modifier.size(80.dp),
                        tint = Color(0xFF4CAF50) // Green
                    )
                }
                is PaymentCallbackState.Cancelled -> {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancelled",
                        modifier = Modifier.size(80.dp),
                        tint = Color(0xFFFF9800) // Orange
                    )
                }
                is PaymentCallbackState.Failure -> {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Failed",
                        modifier = Modifier.size(80.dp),
                        tint = Color(0xFFF44336) // Red
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Status Title
            Text(
                text = viewModel.statusTitle,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Status Message
            Text(
                text = viewModel.statusMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Done button (only when not polling)
            if (state !is PaymentCallbackState.Polling) {
                Button(
                    onClick = {
                        viewModel.clearPendingData()
                        onDone()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Done",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

// MARK: - Previews

@Preview(showBackground = true)
@Composable
private fun PaymentCallbackScreenPollingPreview() {
    SolverAppTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(80.dp),
                strokeWidth = 4.dp
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Checking Payment",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Please wait while we verify your payment...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PaymentCallbackScreenSuccessPreview() {
    SolverAppTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Success",
                modifier = Modifier.size(80.dp),
                tint = Color(0xFF4CAF50)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Success",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Your payment was successful",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(48.dp))
            Button(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "Done", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
