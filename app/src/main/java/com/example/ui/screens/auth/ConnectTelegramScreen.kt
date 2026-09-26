package com.example.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CountryCode
import com.example.data.model.CountryRepository
import com.example.data.model.TelegramAuthState
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.theme.KawachAccent
import com.example.ui.theme.KawachPrimary
import com.example.ui.theme.KawachPrimaryDark
import com.example.ui.theme.KawachSuccess
import com.example.ui.viewmodel.KawachViewModel

@Composable
fun ConnectTelegramScreen(
    viewModel: KawachViewModel,
    onConnected: () -> Unit
) {
    val authState by viewModel.authState.collectAsState()
    val selectedCountry by viewModel.selectedCountry.collectAsState()
    val phoneInput by viewModel.phoneNumberInput.collectAsState()
    val otpInput by viewModel.otpInput.collectAsState()
    val passwordInput by viewModel.passwordInput.collectAsState()

    var showCountryDialog by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    if (authState is TelegramAuthState.Authenticated) {
        onConnected()
    }

    if (showCountryDialog) {
        CountryPickerDialog(
            countries = CountryRepository.COUNTRIES,
            onDismiss = { showCountryDialog = false },
            onSelect = { country ->
                viewModel.setSelectedCountry(country)
                showCountryDialog = false
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo / Header
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(KawachPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = KawachPrimary,
                    modifier = Modifier.size(42.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Kawach Cloud",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Connect your Telegram account to use your own Saved Messages as cloud storage",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = KawachPrimary.copy(alpha = 0.35f)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    when (val state = authState) {
                        is TelegramAuthState.Uninitialized,
                        is TelegramAuthState.Initializing -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = KawachPrimary)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Connecting to Telegram network...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        is TelegramAuthState.WaitingPhoneNumber,
                        is TelegramAuthState.SendingPhoneNumber,
                        is TelegramAuthState.Error -> {
                            val isSending = state is TelegramAuthState.SendingPhoneNumber

                            Text(
                                text = "Enter Phone Number",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Country Selector Button
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showCountryDialog = true }
                                    .testTag("country_selector_button"),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = selectedCountry.flagEmoji, fontSize = 20.sp)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "${selectedCountry.countryName} (${selectedCountry.dialCode})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select country"
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Phone Number Input
                            OutlinedTextField(
                                value = phoneInput,
                                onValueChange = { viewModel.setPhoneNumberInput(it) },
                                label = { Text("Phone Number") },
                                prefix = {
                                    Text(
                                        text = "${selectedCountry.dialCode} ",
                                        fontWeight = FontWeight.Bold,
                                        color = KawachPrimary
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Phone, contentDescription = null, tint = KawachPrimary)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Phone,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { viewModel.sendPhoneNumber() }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("phone_number_input")
                            )

                            if (state is TelegramAuthState.Error) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            GlassButton(
                                text = "Send Telegram OTP",
                                onClick = { viewModel.sendPhoneNumber() },
                                isLoading = isSending,
                                enabled = phoneInput.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                                tag = "send_otp_button"
                            )
                        }

                        is TelegramAuthState.WaitingCode,
                        is TelegramAuthState.VerifyingCode -> {
                            val isVerifying = state is TelegramAuthState.VerifyingCode
                            val waitingCodeState = state as? TelegramAuthState.WaitingCode

                            Text(
                                text = "Enter Telegram OTP",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "A login code was sent to your Telegram app on ${waitingCodeState?.phoneNumber ?: ""}. Enter the code below.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = otpInput,
                                onValueChange = { viewModel.setOtpInput(it) },
                                label = { Text("Telegram OTP Code") },
                                leadingIcon = {
                                    Icon(Icons.Default.Key, contentDescription = null, tint = KawachPrimary)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { viewModel.sendOtp() }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("otp_input_field")
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            GlassButton(
                                text = "Verify Code",
                                onClick = { viewModel.sendOtp() },
                                isLoading = isVerifying,
                                enabled = otpInput.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                                tag = "verify_otp_button"
                            )
                        }

                        is TelegramAuthState.WaitingPassword,
                        is TelegramAuthState.VerifyingPassword -> {
                            val isVerifying = state is TelegramAuthState.VerifyingPassword
                            val pwdState = state as? TelegramAuthState.WaitingPassword

                            Text(
                                text = "Two-Step Verification",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Your Telegram account is protected by a Two-Step Verification cloud password. Enter your password to continue.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (!pwdState?.hint.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Hint: ${pwdState?.hint}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KawachAccent
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { viewModel.setPasswordInput(it) },
                                label = { Text("2FA Password") },
                                leadingIcon = {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = KawachPrimary)
                                },
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                        )
                                    }
                                },
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { viewModel.send2faPassword() }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("2fa_password_input")
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            GlassButton(
                                text = "Verify 2FA Password",
                                onClick = { viewModel.send2faPassword() },
                                isLoading = isVerifying,
                                enabled = passwordInput.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                                tag = "verify_2fa_button"
                            )
                        }

                        is TelegramAuthState.Authenticated -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = KawachSuccess,
                                    modifier = Modifier.size(52.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Connected Successfully!",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = KawachSuccess
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = state.user.displayName,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        is TelegramAuthState.LoggingOut -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = KawachPrimary)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Logging out...")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Each user connects to their own Telegram account. Kawach Cloud never sees your personal chats and only manages files in your Saved Messages.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

@Composable
fun CountryPickerDialog(
    countries: List<CountryCode>,
    onDismiss: () -> Unit,
    onSelect: (CountryCode) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredCountries = remember(searchQuery) {
        if (searchQuery.isBlank()) countries
        else countries.filter {
            it.countryName.contains(searchQuery.trim(), ignoreCase = true) ||
                    it.dialCode.contains(searchQuery.trim())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Select Country", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search country or code...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("country_search_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(filteredCountries) { country ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(country) }
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = country.flagEmoji, fontSize = 22.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = country.countryName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Text(
                                    text = country.dialCode,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = KawachPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ApiCredentialsDialog(
    initialApiId: String,
    initialApiHash: String,
    onDismiss: () -> Unit,
    onSave: (apiId: String, apiHash: String) -> Unit
) {
    var apiIdInput by remember { mutableStateOf(initialApiId) }
    var apiHashInput by remember { mutableStateOf(initialApiHash) }
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Key, contentDescription = null, tint = KawachPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Telegram API Credentials", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "To connect to Telegram Saved Messages without restrictions, enter your free API credentials:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("How to get free credentials (1 minute):", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("1. Open my.telegram.org in your browser", fontSize = 12.sp)
                        Text("2. Log in with your phone number", fontSize = 12.sp)
                        Text("3. Tap 'API development tools'", fontSize = 12.sp)
                        Text("4. Copy App api_id and App api_hash", fontSize = 12.sp)

                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                try {
                                    uriHandler.openUri("https://my.telegram.org")
                                } catch (e: Exception) {}
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open my.telegram.org", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = apiIdInput,
                    onValueChange = { apiIdInput = it.filter { char -> char.isDigit() } },
                    label = { Text("App api_id (Number)") },
                    placeholder = { Text("e.g. 24967394") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = apiHashInput,
                    onValueChange = { apiHashInput = it.trim() },
                    label = { Text("App api_hash (32-char hex)") },
                    placeholder = { Text("e.g. 8da85b0d5bfe...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(apiIdInput.trim(), apiHashInput.trim())
                    onDismiss()
                },
                enabled = apiIdInput.isNotBlank() && apiHashInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = KawachPrimary)
            ) {
                Text("Save & Reconnect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
