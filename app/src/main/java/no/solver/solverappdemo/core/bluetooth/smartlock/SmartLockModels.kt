package no.solver.solverappdemo.core.bluetooth.smartlock

import androidx.compose.ui.graphics.Color
import java.util.Date

/**
 * Unified lock state across all lock brands
 */
enum class SmartLockState {
    LOCKED,
    UNLOCKED,
    UNKNOWN;

    val iconName: String
        get() = when (this) {
            LOCKED -> "lock"
            UNLOCKED -> "lock_open"
            UNKNOWN -> "lock_clock"
        }

    val color: Color
        get() = when (this) {
            LOCKED -> Color(0xFFF44336) // Red
            UNLOCKED -> Color(0xFF4CAF50) // Green
            UNKNOWN -> Color(0xFF9E9E9E) // Gray
        }

    val displayText: String
        get() = when (this) {
            LOCKED -> "Locked"
            UNLOCKED -> "Unlocked"
            UNKNOWN -> "Unknown"
        }
}

/**
 * Supported lock brands
 */
enum class SmartLockBrand {
    DANALOCK,
    MASTERLOCK;

    val displayName: String
        get() = when (this) {
            DANALOCK -> "Danalock"
            MASTERLOCK -> "Masterlock"
        }

    companion object {
        /**
         * Detect lock brand from objectTypeId
         */
        fun fromObjectTypeId(objectTypeId: Int): SmartLockBrand? {
            return when (objectTypeId) {
                4 -> DANALOCK
                10 -> MASTERLOCK
                else -> null
            }
        }
    }
}

/**
 * Complete status snapshot of a smart lock
 */
data class SmartLockStatus(
    val state: SmartLockState,
    val batteryLevel: Int?,
    val inRange: Boolean,
    val hasValidTokens: Boolean,
    val tokensExpireAt: Date?
) {
    companion object {
        val UNKNOWN = SmartLockStatus(
            state = SmartLockState.UNKNOWN,
            batteryLevel = null,
            inRange = false,
            hasValidTokens = false,
            tokensExpireAt = null
        )
    }
}

/**
 * Brand-agnostic token storage
 */
data class SmartLockTokens(
    val brand: SmartLockBrand,
    val deviceIdentifier: String,
    val authData: ByteArray,
    val additionalData: ByteArray?,
    val cachedAt: Date,
    val expiresAt: Date
) {
    val isExpired: Boolean
        get() = Date().after(expiresAt)

    val remainingValiditySeconds: Long?
        get() {
            val remaining = (expiresAt.time - Date().time) / 1000
            return if (remaining > 0) remaining else null
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SmartLockTokens
        return brand == other.brand &&
                deviceIdentifier == other.deviceIdentifier &&
                authData.contentEquals(other.authData)
    }

    override fun hashCode(): Int {
        var result = brand.hashCode()
        result = 31 * result + deviceIdentifier.hashCode()
        result = 31 * result + authData.contentHashCode()
        return result
    }
}

/**
 * What features a lock brand supports
 */
data class SmartLockCapabilities(
    val supportsBatteryReading: Boolean,
    val supportsRangeDetection: Boolean,
    val supportsStatePolling: Boolean,
    val supportsManualStatusCheck: Boolean,
    val supportsLock: Boolean,
    val supportsUnlock: Boolean
) {
    companion object {
        val DANALOCK = SmartLockCapabilities(
            supportsBatteryReading = true,
            supportsRangeDetection = true,
            supportsStatePolling = true,
            supportsManualStatusCheck = false, // Uses continuous polling instead
            supportsLock = true,
            supportsUnlock = true
        )

        val MASTERLOCK = SmartLockCapabilities(
            supportsBatteryReading = false,
            supportsRangeDetection = true,
            supportsStatePolling = true, // Polls by periodically connecting to read state
            supportsManualStatusCheck = true, // Also supports manual status check button
            supportsLock = false,
            supportsUnlock = true
        )
    }
}

/**
 * Smart lock errors
 */
sealed class SmartLockError : Exception() {
    data class UnsupportedOperation(val operation: String) : SmartLockError() {
        override val message: String = "Operation '$operation' not supported by this lock"
    }

    data object MissingTokens : SmartLockError() {
        private fun readResolve(): Any = MissingTokens
        override val message: String = "Authentication tokens not available"
    }

    data object InvalidTokenFormat : SmartLockError() {
        private fun readResolve(): Any = InvalidTokenFormat
        override val message: String = "Invalid token format"
    }

    data object DeviceNotFound : SmartLockError() {
        private fun readResolve(): Any = DeviceNotFound
        override val message: String = "Lock device not found or out of range"
    }

    data object ConnectionTimeout : SmartLockError() {
        private fun readResolve(): Any = ConnectionTimeout
        override val message: String = "Connection to device timed out"
    }

    data object ConnectionFailed : SmartLockError() {
        private fun readResolve(): Any = ConnectionFailed
        override val message: String = "Failed to establish BLE connection"
    }

    data class OperationFailed(val reason: String) : SmartLockError() {
        override val message: String = "Operation failed: $reason"
    }

    data object NotASmartLock : SmartLockError() {
        private fun readResolve(): Any = NotASmartLock
        override val message: String = "This object is not a smart lock"
    }

    data object BluetoothDisabled : SmartLockError() {
        private fun readResolve(): Any = BluetoothDisabled
        override val message: String = "Bluetooth is disabled"
    }

    data object PermissionDenied : SmartLockError() {
        private fun readResolve(): Any = PermissionDenied
        override val message: String = "Bluetooth permission denied"
    }
}
