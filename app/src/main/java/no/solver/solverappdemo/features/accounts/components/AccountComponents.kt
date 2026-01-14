package no.solver.solverappdemo.features.accounts.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.solver.solverappdemo.core.config.AuthEnvironment
import no.solver.solverappdemo.core.config.AuthProvider
import no.solver.solverappdemo.features.auth.models.AuthTokens
import no.solver.solverappdemo.features.auth.models.Session
import no.solver.solverappdemo.features.auth.models.UserInfo

// Extension function for display name
private fun AuthProvider.toDisplayName(): String = when (this) {
    AuthProvider.MICROSOFT -> "Microsoft"
    AuthProvider.VIPPS -> "Vipps"
    AuthProvider.MOBILE -> "Mobile"
}

// Provider colors
private val MicrosoftBlue = Color(0xFF0078D4)
private val MicrosoftBlueLight = Color(0xFF50A0E0)
private val VippsOrange = Color(0xFFFF5B24)
private val VippsOrangeLight = Color(0xFFFF8B60)
private val MobileGreen = Color(0xFF34C759)
private val MobileGreenLight = Color(0xFF6EE090)

@Composable
fun AvatarView(
    initials: String,
    provider: AuthProvider,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val gradient = when (provider) {
        AuthProvider.MICROSOFT -> Brush.linearGradient(
            colors = listOf(MicrosoftBlue, MicrosoftBlueLight)
        )
        AuthProvider.VIPPS -> Brush.linearGradient(
            colors = listOf(VippsOrange, VippsOrangeLight)
        )
        AuthProvider.MOBILE -> Brush.linearGradient(
            colors = listOf(MobileGreen, MobileGreenLight)
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(gradient),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontSize = (size.value * 0.4f).sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ProviderIcon(
    provider: AuthProvider,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val icon: ImageVector = when (provider) {
        AuthProvider.MICROSOFT -> Icons.Filled.AccountCircle
        AuthProvider.VIPPS -> Icons.Filled.AccountCircle // Placeholder, using custom V text below
        AuthProvider.MOBILE -> Icons.Filled.Phone
    }

    val tint = when (provider) {
        AuthProvider.MICROSOFT -> MicrosoftBlue
        AuthProvider.VIPPS -> VippsOrange
        AuthProvider.MOBILE -> MobileGreen
    }

    if (provider == AuthProvider.VIPPS) {
        // Custom "V" text for Vipps
        Text(
            text = "V",
            color = tint,
            fontSize = size.value.sp,
            fontWeight = FontWeight.Bold,
            modifier = modifier
        )
    } else {
        Icon(
            imageVector = icon,
            contentDescription = provider.name,
            tint = tint,
            modifier = modifier.size(size)
        )
    }
}

@Composable
fun AccountRowItem(
    session: Session,
    isSelected: Boolean,
    isEditing: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val userInfo = session.tokens.userInfo

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar (hidden in edit mode)
        if (!isEditing) {
            AvatarView(
                initials = userInfo?.initials ?: "??",
                provider = session.provider,
                size = 40.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        // Name + email column
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = session.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val secondaryText = session.email ?: userInfo?.userName
            if (!secondaryText.isNullOrEmpty()) {
                Text(
                    text = secondaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Checkmark when selected (not in edit mode)
        if (isSelected && !isEditing) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Selected",
                tint = MicrosoftBlue,
                modifier = Modifier.size(24.dp)
            )
        }

        // Chevron in edit mode
        if (isEditing) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun AccountHeaderCard(
    session: Session,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val userInfo = session.tokens.userInfo

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar (60dp)
            AvatarView(
                initials = userInfo?.initials ?: "??",
                provider = session.provider,
                size = 60.dp
            )

            Spacer(modifier = Modifier.width(16.dp))

            // User Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = session.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                session.email?.let { email ->
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Provider badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    ProviderIcon(
                        provider = session.provider,
                        size = 12.dp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = session.provider.toDisplayName(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Chevron
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// Previews

@Preview(showBackground = true)
@Composable
private fun AvatarViewPreview() {
    Row(modifier = Modifier.padding(16.dp)) {
        AvatarView(initials = "JD", provider = AuthProvider.MICROSOFT, size = 60.dp)
        Spacer(modifier = Modifier.width(8.dp))
        AvatarView(initials = "AS", provider = AuthProvider.VIPPS, size = 60.dp)
        Spacer(modifier = Modifier.width(8.dp))
        AvatarView(initials = "MN", provider = AuthProvider.MOBILE, size = 60.dp)
    }
}

@Preview(showBackground = true)
@Composable
private fun ProviderIconPreview() {
    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProviderIcon(provider = AuthProvider.MICROSOFT, size = 16.dp)
        Spacer(modifier = Modifier.width(16.dp))
        ProviderIcon(provider = AuthProvider.VIPPS, size = 16.dp)
        Spacer(modifier = Modifier.width(16.dp))
        ProviderIcon(provider = AuthProvider.MOBILE, size = 16.dp)
    }
}

@Preview(showBackground = true)
@Composable
private fun AccountRowItemPreview() {
    val session = Session(
        id = "1",
        provider = AuthProvider.MICROSOFT,
        environment = AuthEnvironment.SOLVER,
        tokens = AuthTokens(
            accessToken = "token",
            refreshToken = "refresh",
            expiresAtMillis = System.currentTimeMillis() + 3600000,
            userInfo = UserInfo(
                displayName = "John Doe",
                email = "john.doe@company.com",
                givenName = "John",
                familyName = "Doe"
            )
        )
    )

    Column {
        AccountRowItem(
            session = session,
            isSelected = true,
            isEditing = false,
            onClick = {}
        )
        AccountRowItem(
            session = session.copy(id = "2"),
            isSelected = false,
            isEditing = false,
            onClick = {}
        )
        AccountRowItem(
            session = session.copy(id = "3"),
            isSelected = false,
            isEditing = true,
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AccountHeaderCardPreview() {
    val session = Session(
        id = "1",
        provider = AuthProvider.MICROSOFT,
        environment = AuthEnvironment.SOLVER,
        tokens = AuthTokens(
            accessToken = "token",
            refreshToken = "refresh",
            expiresAtMillis = System.currentTimeMillis() + 3600000,
            userInfo = UserInfo(
                displayName = "John Doe",
                email = "john.doe@company.com",
                givenName = "John",
                familyName = "Doe"
            )
        )
    )

    Column(modifier = Modifier.padding(16.dp)) {
        AccountHeaderCard(session = session, onClick = {})
    }
}
