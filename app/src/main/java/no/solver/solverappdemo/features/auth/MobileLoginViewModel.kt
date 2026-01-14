package no.solver.solverappdemo.features.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import no.solver.solverappdemo.core.config.AuthEnvironment
import no.solver.solverappdemo.core.config.AuthProvider
import no.solver.solverappdemo.features.auth.services.MobileAuthException
import no.solver.solverappdemo.features.auth.services.MobileAuthService
import no.solver.solverappdemo.features.auth.services.MobileConfirmationInput
import no.solver.solverappdemo.features.auth.services.MobileRegistrationInput
import no.solver.solverappdemo.features.auth.services.RegisteredMobileUser
import no.solver.solverappdemo.features.auth.services.SessionManager
import no.solver.solverappdemo.data.repositories.IconRepository
import javax.inject.Inject

sealed class MobileLoginUiState {
    data object InputPhone : MobileLoginUiState()
    data object Loading : MobileLoginUiState()
    data object InputPin : MobileLoginUiState()
    data object Success : MobileLoginUiState()
    data class Error(val message: String) : MobileLoginUiState()
}

@HiltViewModel
class MobileLoginViewModel @Inject constructor(
    private val mobileAuthService: MobileAuthService,
    private val sessionManager: SessionManager,
    private val iconRepository: IconRepository
) : ViewModel() {

    companion object {
        private const val TAG = "MobileLoginViewModel"
    }

    private val _uiState = MutableStateFlow<MobileLoginUiState>(MobileLoginUiState.InputPhone)
    val uiState: StateFlow<MobileLoginUiState> = _uiState.asStateFlow()

    // Input fields
    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _mobileNumber = MutableStateFlow("")
    val mobileNumber: StateFlow<String> = _mobileNumber.asStateFlow()

    private val _countryCode = MutableStateFlow("+47") // Default to Norway
    val countryCode: StateFlow<String> = _countryCode.asStateFlow()

    private val _pin = MutableStateFlow("")
    val pin: StateFlow<String> = _pin.asStateFlow()

    // Registered user info (set after registration, used for confirmation)
    private var registeredUser: RegisteredMobileUser? = null

    // Full mobile number (country code + number)
    val fullMobileNumber: StateFlow<String> = combine(_countryCode, _mobileNumber) { code, number ->
        "$code$number"
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "+47"
    )

    // Validation - reactive StateFlows so UI recomposes
    val isRegisterValid: StateFlow<Boolean> = combine(_displayName, _mobileNumber) { name, number ->
        name.isNotBlank() && number.length >= 8
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val isPinValid: StateFlow<Boolean> = _pin.map { it.length >= 6 }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    fun setDisplayName(value: String) {
        _displayName.value = value
    }

    fun setMobileNumber(value: String) {
        _mobileNumber.value = cleanPhoneNumber(value)
    }

    fun setCountryCode(value: String) {
        _countryCode.value = value
    }

    fun setPin(value: String) {
        _pin.value = value
    }

    /**
     * Clean phone number by removing formatting and country codes.
     * Matches iOS MobileAuthViewModel.cleanPhoneNumber behavior.
     */
    private fun cleanPhoneNumber(input: String): String {
        var cleaned = input

        // Remove common formatting characters
        cleaned = cleaned.replace(" ", "")
        cleaned = cleaned.replace("-", "")
        cleaned = cleaned.replace("(", "")
        cleaned = cleaned.replace(")", "")
        cleaned = cleaned.replace(".", "")

        // If it starts with the current country code, remove it
        if (cleaned.startsWith(_countryCode.value)) {
            cleaned = cleaned.removePrefix(_countryCode.value)
        }

        // If it still starts with +, remove country code digits
        if (cleaned.startsWith("+")) {
            cleaned = cleaned.drop(1)
            // Remove up to 4 country code digits
            var digitsRemoved = 0
            while (digitsRemoved < 4 && cleaned.firstOrNull()?.isDigit() == true) {
                cleaned = cleaned.drop(1)
                digitsRemoved++
            }
        }

        // Keep only digits
        cleaned = cleaned.filter { it.isDigit() }

        return cleaned
    }

    /**
     * Step 1: Register mobile user (triggers SMS)
     */
    fun register() {
        if (!isRegisterValid.value) return

        viewModelScope.launch {
            _uiState.value = MobileLoginUiState.Loading

            try {
                val environment = sessionManager.getAuthEnvironment()
                mobileAuthService.initialize(environment)

                val input = MobileRegistrationInput(
                    displayName = _displayName.value,
                    mobileNumber = "${_countryCode.value}${_mobileNumber.value}"
                )

                Log.d(TAG, "Registering mobile user: ${input.displayName}, ${input.mobileNumber}")

                registeredUser = mobileAuthService.registerMobileUser(input)

                Log.d(TAG, "Registration successful, awaiting PIN")
                _uiState.value = MobileLoginUiState.InputPin

            } catch (e: MobileAuthException) {
                Log.e(TAG, "Registration failed: ${e.message}", e)
                _uiState.value = MobileLoginUiState.Error(e.message ?: "Registration failed")
            } catch (e: Exception) {
                Log.e(TAG, "Registration failed: ${e.message}", e)
                _uiState.value = MobileLoginUiState.Error("Registration failed. Please try again.")
            }
        }
    }

    /**
     * Step 2: Confirm PIN and complete login
     */
    fun confirm() {
        val user = registeredUser
        if (user == null || !isPinValid.value) return

        viewModelScope.launch {
            _uiState.value = MobileLoginUiState.Loading

            try {
                val environment = sessionManager.getAuthEnvironment()
                mobileAuthService.initialize(environment)

                val input = MobileConfirmationInput(
                    sid = user.sid,
                    password = user.password,
                    pin = _pin.value
                )

                Log.d(TAG, "Confirming PIN for user")

                val tokens = mobileAuthService.confirmMobileUser(input)

                // Create session
                sessionManager.createSession(
                    provider = AuthProvider.MOBILE,
                    environment = environment,
                    tokens = tokens
                )

                Log.i(TAG, "✅ Mobile login successful")
                prefetchIcons()
                _uiState.value = MobileLoginUiState.Success

            } catch (e: MobileAuthException) {
                Log.e(TAG, "Confirmation failed: ${e.message}", e)
                _uiState.value = MobileLoginUiState.Error(e.message ?: "Invalid PIN")
            } catch (e: Exception) {
                Log.e(TAG, "Confirmation failed: ${e.message}", e)
                _uiState.value = MobileLoginUiState.Error("Confirmation failed. Please try again.")
            }
        }
    }

    /**
     * Go back from PIN to phone input
     */
    fun goBack() {
        _pin.value = ""
        registeredUser = null
        _uiState.value = MobileLoginUiState.InputPhone
    }

    /**
     * Reset the entire flow
     */
    fun reset() {
        _displayName.value = ""
        _mobileNumber.value = ""
        _pin.value = ""
        registeredUser = null
        _uiState.value = MobileLoginUiState.InputPhone
    }

    /**
     * Resend the SMS verification code
     */
    fun resendCode() {
        if (!isRegisterValid.value) return

        _pin.value = ""

        viewModelScope.launch {
            _uiState.value = MobileLoginUiState.Loading

            try {
                val environment = sessionManager.getAuthEnvironment()
                mobileAuthService.initialize(environment)

                val input = MobileRegistrationInput(
                    displayName = _displayName.value,
                    mobileNumber = "${_countryCode.value}${_mobileNumber.value}"
                )

                Log.d(TAG, "Resending code to: ${input.mobileNumber}")

                registeredUser = mobileAuthService.registerMobileUser(input)

                Log.d(TAG, "Code resent successfully")
                _uiState.value = MobileLoginUiState.InputPin

            } catch (e: MobileAuthException) {
                Log.e(TAG, "Resend failed: ${e.message}", e)
                _uiState.value = MobileLoginUiState.Error(e.message ?: "Failed to resend code")
            } catch (e: Exception) {
                Log.e(TAG, "Resend failed: ${e.message}", e)
                _uiState.value = MobileLoginUiState.Error("Failed to resend code. Please try again.")
            }
        }
    }

    /**
     * Pre-fetch all object type icons in the background for smooth scrolling.
     */
    private fun prefetchIcons() {
        viewModelScope.launch {
            try {
                iconRepository.prefetchIcons()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to prefetch icons", e)
            }
        }
    }
}
