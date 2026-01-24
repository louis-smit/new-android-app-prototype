package no.solver.solverappdemo.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import no.solver.solverappdemo.R
import no.solver.solverappdemo.data.models.AvailablePaymentMethods
import no.solver.solverappdemo.data.models.PaymentMethod
import no.solver.solverappdemo.data.models.SubscriptionOption
import no.solver.solverappdemo.features.objects.middleware.PaymentContext
import no.solver.solverappdemo.features.objects.middleware.SubscriptionContext
import no.solver.solverappdemo.ui.theme.SolverAppTheme

/**
 * Sheet content for selecting a payment method.
 * Matches iOS PaymentMethodSheet with dynamic methods.
 */
@Composable
fun PaymentMethodSheetContent(
    context: PaymentContext,
    availableMethods: AvailablePaymentMethods,
    onSelectMethod: (PaymentMethod) -> Unit,
    onDismiss: () -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header icon
        Icon(
            painter = painterResource(id = R.drawable.ic_payment),
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Payment Required",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = context.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.padding(vertical = 24.dp)
            )
        } else {
            // Payment method buttons - only show available methods
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                availableMethods.methods.forEach { method ->
                    PaymentMethodButton(
                        method = method,
                        onClick = { onSelectMethod(method) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Cancel button
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text("Cancel")
        }
    }
}

@Composable
private fun PaymentMethodButton(
    method: PaymentMethod,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = ButtonDefaults.outlinedButtonBorder(enabled = true),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Use custom icon for Vipps, system icons for others
            when (method) {
                PaymentMethod.VIPPS -> {
                    Image(
                        painter = painterResource(id = R.drawable.ic_vipps),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                }
                PaymentMethod.CARD -> {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_payment),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                PaymentMethod.STRIPE -> {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_payment),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = Color(0xFF635BFF) // Stripe purple
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = method.displayName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Sheet content for selecting a subscription option.
 * Matches iOS SubscriptionOptionsSheet.
 */
@Composable
fun SubscriptionOptionsSheetContent(
    context: SubscriptionContext,
    onSelectOption: (SubscriptionOption) -> Unit,
    onDismiss: () -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header icon
        Icon(
            painter = painterResource(id = R.drawable.ic_subscription),
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Choose subscription",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = context.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.padding(vertical = 24.dp)
            )
        } else {
            // Subscription options
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(context.subscriptionOptions) { option ->
                    SubscriptionOptionButton(
                        option = option,
                        onClick = { onSelectOption(option) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Cancel button
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text("Cancel")
        }
    }
}

@Composable
private fun SubscriptionOptionButton(
    option: SubscriptionOption,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = ButtonDefaults.outlinedButtonBorder(enabled = true),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = option.displayTitle,
                    style = MaterialTheme.typography.titleMedium
                )

                option.subscriptionType?.let { type ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = type.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = option.displayPrice,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Sheet content for selecting payment method for a subscription.
 * Similar to PaymentMethodSheetContent but for subscriptions.
 */
@Composable
fun SubscriptionPaymentMethodSheetContent(
    subscription: SubscriptionOption,
    availableMethods: AvailablePaymentMethods,
    onSelectMethod: (PaymentMethod) -> Unit,
    onDismiss: () -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header icon
        Icon(
            painter = painterResource(id = R.drawable.ic_payment),
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Select Payment Method",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Pay ${subscription.displayPrice} for ${subscription.displayTitle}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.padding(vertical = 24.dp)
            )
        } else {
            // Payment method buttons - only show available methods
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                availableMethods.methods.forEach { method ->
                    PaymentMethodButton(
                        method = method,
                        onClick = { onSelectMethod(method) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Cancel button
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text("Cancel")
        }
    }
}

// MARK: - Previews

@Preview(showBackground = true)
@Composable
private fun PaymentMethodSheetContentPreview() {
    SolverAppTheme {
        PaymentMethodSheetContent(
            context = PaymentContext(
                price = "50",
                command = "unlock",
                objectId = 123,
                vendingTransId = null
            ),
            availableMethods = AvailablePaymentMethods(
                hasVipps = true,
                hasCard = true,
                hasStripe = true
            ),
            onSelectMethod = {},
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SubscriptionOptionsSheetContentPreview() {
    SolverAppTheme {
        SubscriptionOptionsSheetContent(
            context = SubscriptionContext(
                command = "unlock",
                objectId = 123,
                subscriptionOptions = listOf(
                    SubscriptionOption(
                        objectSubscriptionId = 1,
                        subscriptionTypeId = 1,
                        description = "Monthly Access",
                        amount = 99.0
                    ),
                    SubscriptionOption(
                        objectSubscriptionId = 2,
                        subscriptionTypeId = 3,
                        description = "Recurring Monthly",
                        amount = 89.0
                    )
                )
            ),
            onSelectOption = {},
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SubscriptionPaymentMethodSheetContentPreview() {
    SolverAppTheme {
        SubscriptionPaymentMethodSheetContent(
            subscription = SubscriptionOption(
                objectSubscriptionId = 1,
                subscriptionTypeId = 1,
                description = "Monthly Access",
                amount = 99.0
            ),
            availableMethods = AvailablePaymentMethods(
                hasVipps = true,
                hasCard = true,
                hasStripe = false
            ),
            onSelectMethod = {},
            onDismiss = {}
        )
    }
}
