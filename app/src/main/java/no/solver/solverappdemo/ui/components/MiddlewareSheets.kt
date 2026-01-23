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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import no.solver.solverappdemo.R
import no.solver.solverappdemo.features.objects.middleware.PaymentContext
import no.solver.solverappdemo.features.objects.middleware.SubscriptionContext
import no.solver.solverappdemo.ui.theme.SolverAppTheme

/**
 * Sheet content for selecting a payment method.
 * Matches iOS PaymentMethodSheet.
 */
@Composable
fun PaymentMethodSheetContent(
    context: PaymentContext,
    onSelectMethod: (String) -> Unit,
    onDismiss: () -> Unit,
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
            text = "Pay ${context.price} to execute ${context.command}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Payment method buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Vipps
            PaymentMethodButton(
                name = "Vipps",
                iconResId = R.drawable.ic_vipps,
                onClick = { onSelectMethod("vipps") }
            )
            
            // Card
            PaymentMethodButton(
                name = "Card",
                iconResId = R.drawable.ic_payment,
                onClick = { onSelectMethod("card") }
            )
            
            // Stripe (if available)
            PaymentMethodButton(
                name = "Stripe",
                iconResId = R.drawable.ic_payment,
                onClick = { onSelectMethod("stripe") }
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Cancel button
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }
    }
}

@Composable
private fun PaymentMethodButton(
    name: String,
    iconResId: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = ButtonDefaults.outlinedButtonBorder(enabled = true)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = name,
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
 * Matches iOS subscription selection sheet.
 */
@Composable
fun SubscriptionSheetContent(
    context: SubscriptionContext,
    onSelectSubscription: (String) -> Unit,
    onDismiss: () -> Unit,
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
            text = "Subscription Required",
            style = MaterialTheme.typography.titleLarge
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "A subscription is required to use this feature",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Subscription options
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // TODO: Display actual subscription options from context
            // For now, show a placeholder
            SubscriptionOptionButton(
                title = "Monthly Subscription",
                description = "Unlimited access for 30 days",
                onClick = { onSelectSubscription("monthly") }
            )
            
            SubscriptionOptionButton(
                title = "Annual Subscription",
                description = "Unlimited access for 1 year (save 20%)",
                onClick = { onSelectSubscription("annual") }
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Cancel button
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }
    }
}

@Composable
private fun SubscriptionOptionButton(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                price = "50 NOK",
                command = "unlock",
                objectId = 123,
                vendingTransId = null
            ),
            onSelectMethod = {},
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SubscriptionSheetContentPreview() {
    SolverAppTheme {
        SubscriptionSheetContent(
            context = SubscriptionContext(
                command = "unlock",
                objectId = 123
            ),
            onSelectSubscription = {},
            onDismiss = {}
        )
    }
}
