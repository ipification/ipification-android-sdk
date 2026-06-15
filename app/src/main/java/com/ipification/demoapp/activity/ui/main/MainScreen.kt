package com.ipification.demoapp.activity.ui.main

import android.annotation.SuppressLint
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.ipification.demoapp.R
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import androidx.annotation.RequiresApi
import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.ipification.demoapp.BuildConfig
import com.ipification.demoapp.activity.AppTheme
import com.ipification.mobile.sdk.ip.IPEnvironment

// Note: Assuming these models have been moved to a 'model' package as per your latest code
import com.ipification.demoapp.model.MainScreenNavigation
import com.ipification.demoapp.model.MainViewModel
import com.ipification.demoapp.util.Util
import com.ipification.mobile.sdk.ip.AuthChannel
import com.ipification.mobile.sdk.ip.IPConfiguration
import com.ipification.mobile.sdk.ip.utils.PhoneNumberHelper

private const val MAIN_SCREEN_TAG = "MainScreen"

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    navigateToPnv: () -> Unit,
    navigateToProcess: (userFlow: String, loginHint: String?) -> Unit,
    navigateToFlowWithUserFlow: (userFlow: String) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val phonePerms = remember { PhoneNumberHelper.getRuntimeRequiredPermissions() }

    val googleHintLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val phoneNumber = result.data?.let { data ->
            runCatching { Identity.getSignInClient(context).getPhoneNumberFromIntent(data) }.getOrNull()
        }
        Log.d(
            MAIN_SCREEN_TAG,
            "googleHintLauncher resultCode=${result.resultCode}, hasData=${result.data != null}, hasPhoneNumber=${!phoneNumber.isNullOrBlank()}"
        )
        if (!phoneNumber.isNullOrBlank()) {
            Log.d(MAIN_SCREEN_TAG, "googleHintLauncher received phone number; starting automatic PNV verify")
            viewModel.onAutomaticPnvVerifyClicked(phoneNumber)
        } else {
            Log.d(MAIN_SCREEN_TAG, "googleHintLauncher returned no phone number; falling back to PNV screen")
            viewModel.onAutomaticPnvVerifyUnavailable()
        }
    }

    fun launchGooglePhoneHint() {
        Log.d(MAIN_SCREEN_TAG, "launchGooglePhoneHint requested")
        val request = GetPhoneNumberHintIntentRequest.builder().build()
        Identity.getSignInClient(context)
            .getPhoneNumberHintIntent(request)
            .addOnSuccessListener { pendingIntent ->
                Log.d(MAIN_SCREEN_TAG, "launchGooglePhoneHint got pending intent; launching Google hint UI")
                googleHintLauncher.launch(IntentSenderRequest.Builder(pendingIntent).build())
            }
            .addOnFailureListener { error ->
                val apiException = error as? ApiException
                val errorMessage = apiException?.message ?: error.message ?: "Unknown error"
                if (apiException != null) {
                    Log.w(
                        MAIN_SCREEN_TAG,
                        "launchGooglePhoneHint failed statusCode=${apiException.statusCode}, message=$errorMessage",
                        error
                    )
                } else {
                    Log.w(
                        MAIN_SCREEN_TAG,
                        "launchGooglePhoneHint failed error=${error.javaClass.simpleName}, message=$errorMessage",
                        error
                    )
                }
                Toast.makeText(context, "Google phone hint failed: $errorMessage", Toast.LENGTH_LONG).show()
                viewModel.onAutomaticPnvVerifyUnavailable()
            }
    }

    fun fetchSimNumberThenVerify() {
        Log.d(MAIN_SCREEN_TAG, "fetchSimNumberThenVerify requested")
        PhoneNumberHelper.fetchPhoneNumberNow(context, AuthChannel.IP) { numbers ->
            Log.d(
                MAIN_SCREEN_TAG,
                "fetchSimNumberThenVerify result count=${numbers.size}, usableCount=${numbers.count { it.first.isNotBlank() }}"
            )
            when {
                numbers.size == 1 && numbers.first().first.isNotBlank() -> {
                    val phoneNumber = numbers.first()
                    Log.d(MAIN_SCREEN_TAG, "fetchSimNumberThenVerify using single fetched SIM number")
                    viewModel.onAutomaticPnvVerifyClicked(phoneNumber.first, phoneNumber.second)
                }
                else -> {
                    Log.d(MAIN_SCREEN_TAG, "fetchSimNumberThenVerify could not use SIM number; launching Google hint")
                    launchGooglePhoneHint()
                }
            }
        }
    }

    fun hasPhoneNumberPermission(): Boolean {
        val hasReadPhoneState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val hasReadPhoneNumbers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_NUMBERS
            ) == PackageManager.PERMISSION_GRANTED
            } else {
                false
            }

        Log.d(
            MAIN_SCREEN_TAG,
            "hasPhoneNumberPermission READ_PHONE_STATE=$hasReadPhoneState, READ_PHONE_NUMBERS=$hasReadPhoneNumbers"
        )
        return hasReadPhoneState || hasReadPhoneNumbers
    }

    val phonePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = phonePerms.all { permission -> grants[permission] == true }
        val grantSummary = phonePerms.joinToString { permission ->
            "$permission=${grants[permission]}"
        }
        Log.d(MAIN_SCREEN_TAG, "phonePermLauncher result granted=$granted, grants=[$grantSummary]")
        if (granted) {
            Log.d(MAIN_SCREEN_TAG, "phonePermLauncher permissions granted; fetching SIM number")
            fetchSimNumberThenVerify()
        } else {
            Log.d(MAIN_SCREEN_TAG, "phonePermLauncher permissions not granted; launching Google hint")
            launchGooglePhoneHint()
        }
    }

    fun startAutomaticPnvVerify() {
        val notGranted = phonePerms.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        val hasPhoneNumberPermission = hasPhoneNumberPermission()
        Log.d(
            MAIN_SCREEN_TAG,
            "startAutomaticPnvVerify phonePerms=${phonePerms.joinToString()}, notGranted=${notGranted.joinToString()}, hasPhoneNumberPermission=$hasPhoneNumberPermission"
        )
        if (notGranted.isNotEmpty() && !hasPhoneNumberPermission) {
            Log.d(MAIN_SCREEN_TAG, "startAutomaticPnvVerify requesting missing phone permissions")
            phonePermLauncher.launch(notGranted.toTypedArray())
        } else {
            Log.d(MAIN_SCREEN_TAG, "startAutomaticPnvVerify permissions already available; fetching SIM number")
            fetchSimNumberThenVerify()
        }
    }

    // Handle navigation events
    LaunchedEffect(state.navigation) {

        when (val navigation = state.navigation) {
            is MainScreenNavigation.PNV -> {
                navigateToPnv()
                viewModel.onNavigationHandled()
            }
            is MainScreenNavigation.Process -> {
                navigateToProcess(navigation.userFlow, navigation.loginHint)
                viewModel.onNavigationHandled()
            }
            is MainScreenNavigation.Idle -> { /* Do nothing */ }
        }
    }
    LaunchedEffect(Unit) {
        val config = IPConfiguration.getInstance()
        config.debug = true
//        config.enableBindProcess = true
        context.let {
            val dialCode = Util.getSystemDialCode(context)
            if (dialCode == "62" || dialCode == "+62") {
                // flip to CUSTOM_URL automatically for +62 / 62 - INDO
                viewModel.onEnvironmentChanged(IPEnvironment.CUSTOM_URL)
            }
        }
    }

    // Show Info Dialog
    if (state.showInfoDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onDismissInfoDialog() },
            title = { Text("Configuration Info") },
            text = { Text(state.infoDialogMessage) },
            confirmButton = {
                Button(onClick = { viewModel.onDismissInfoDialog() }) {
                    Text("OK")
                }
            }
        )
    }

    MainScreenContent(
        selectedEnvironment = state.selectedEnvironment,
        selectedServerId = state.selectedServerId,
        onEnvironmentChange = { viewModel.onEnvironmentChanged(it) },
        onServerChanged = { viewModel.onServerChanged(it) },
        onInfoClick = { viewModel.onInfoClicked() },
        onPnvClick = { viewModel.onPnvClicked() },
        onAutomaticPnvVerifyClick = { startAutomaticPnvVerify() },
        onQuickLoginClick = { viewModel.onQuickLoginClicked() },
        onTS43QuickLoginClick = { viewModel.onTS43QuickLoginClicked() },
        onAnonymousLoginClick = { viewModel.onAnonymousLoginClicked() },
        onPrivacyPolicyClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.ipification.com/legal"))
            context.startActivity(intent)
        },
        isLoadingConfig = state.isLoadingConfig,
        configError = state.configError,
        availableClients = state.availableClients,
        availableAuthServers = state.availableAuthServers,
        onFlowClick = { userFlow -> viewModel.onFlowClicked(userFlow) },
        onReloadClick = { viewModel.reloadConfig() }
    )
}

@SuppressLint("MissingPermission", "HardwareIds")
private fun getActiveDataSimPhoneNumber(context: android.content.Context): String? {
    val hasReadPhoneState = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_PHONE_STATE
    ) == PackageManager.PERMISSION_GRANTED

    val hasReadPhoneNumbers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_NUMBERS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        false
    }

    if (!hasReadPhoneState && !hasReadPhoneNumbers) {
        return null
    }

    return try {
        val telephonyManager = context.getSystemService(android.content.Context.TELEPHONY_SERVICE) as TelephonyManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            getActiveDataSubscriptionPhoneNumber(context, telephonyManager)
        } else {
            @Suppress("DEPRECATION")
            telephonyManager.line1Number
        }?.takeIf { it.isNotBlank() && it != "Unknown" }
    } catch (e: Exception) {
        null
    }
}

@SuppressLint("MissingPermission", "HardwareIds")
private fun getActiveDataSubscriptionPhoneNumber(
    context: android.content.Context,
    telephonyManager: TelephonyManager
): String? {
    val subscriptionManager = context.getSystemService(android.content.Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
    val activeDataSubId = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> SubscriptionManager.getActiveDataSubscriptionId()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> SubscriptionManager.getDefaultDataSubscriptionId()
        else -> SubscriptionManager.INVALID_SUBSCRIPTION_ID
    }

    if (activeDataSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
        val phoneNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && subscriptionManager != null) {
            subscriptionManager.getPhoneNumber(activeDataSubId)
        } else {
            @Suppress("DEPRECATION")
            telephonyManager.createForSubscriptionId(activeDataSubId).line1Number
        }

        if (!phoneNumber.isNullOrBlank() && phoneNumber != "Unknown") {
            return phoneNumber
        }
    }

    @Suppress("DEPRECATION")
    return telephonyManager.line1Number
}

private fun areSamePhoneNumber(first: String, second: String): Boolean {
    val normalizedFirst = first.filter { it.isDigit() }
    val normalizedSecond = second.filter { it.isDigit() }
    return normalizedFirst.isNotBlank() && normalizedSecond.isNotBlank() &&
        (normalizedFirst.endsWith(normalizedSecond) || normalizedSecond.endsWith(normalizedFirst))
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun MainScreenContent(
    selectedEnvironment: IPEnvironment,
    selectedServerId: String? = null,
    onEnvironmentChange: (IPEnvironment) -> Unit,
    onServerChanged: (String) -> Unit = {},
    onInfoClick: () -> Unit,
    onPnvClick: () -> Unit,
    onAutomaticPnvVerifyClick: () -> Unit,
    onQuickLoginClick: () -> Unit,
    onTS43QuickLoginClick:()->Unit,
    onAnonymousLoginClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    isLoadingConfig: Boolean = false,
    configError: String? = null,
    availableClients: List<com.ipification.demoapp.model.config.ClientConfig> = emptyList(),
    availableAuthServers: List<com.ipification.demoapp.model.config.AuthServer> = emptyList(),
    onFlowClick: (String) -> Unit = {},
    onReloadClick: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 25.dp)
        ) {
            // Top section: Logo and Environment Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 70.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Show logo if configured, otherwise show text
//                if (BuildConfig.FLAVOR != "stage" && BuildConfig.FLAVOR != "production") {
//                    BoxWithConstraints {
//                        Image(
//                            painter = painterResource(id = R.drawable.ip_logo_bw),
//                            contentDescription = "App Logo",
//                            contentScale = ContentScale.Fit,
//                            modifier = Modifier
//                                .widthIn(max = maxWidth / 3)
//                                .wrapContentHeight()
//                        )
//                    }
//                } else {
                    Text(
                        text = "IPification\nfeatures\nshowcase",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.onBackground
                    )
//                }

                Row(
                    horizontalArrangement = Arrangement.End,
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .background(MaterialTheme.colorScheme.primary),
                ) {
                    if (availableAuthServers.isNotEmpty()) {
                        // Show dynamic environment toggle based on API auth servers
                        DynamicEnvironmentToggle(
                            selectedServerId = selectedServerId,
                            onServerChanged = onServerChanged,
                            authServers = availableAuthServers
                        )
                    }
//                    else if (!isLoadingConfig && availableClients.isEmpty()) {
//                        // Show static environment toggle only if no dynamic config loaded
//                        EnvironmentToggle(
//                            selected = selectedEnvironment,
//                            onSelectionChanged = onEnvironmentChange
//                        )
//                    }
//                    Spacer(modifier = Modifier.width(8.dp))
//                    InfoButton(onClick = onInfoClick)
                }
            }

            Spacer(modifier = Modifier.height(100.dp))

            // Show loading indicator while config is being loaded
            if (isLoadingConfig) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Loading configuration...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Show error message if config failed to load
            if (configError != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Configuration Error",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = configError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onReloadClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }

            // Static buttons - show based on API config only
            val hasPnvFlows = availableClients.any { it.userFlow.startsWith("pvn_ip") }
            val hasLoginFlows = availableClients.any { it.userFlow == "login_ip" || it.userFlow == "login_ip_plus" }
            val hasTS43Flows = availableClients.any { it.userFlow == "login_sim" || it.userFlow.contains("ts43") }
            val hasAnonymousFlow = availableClients.any { it.userFlow == "anonymous" }
            val hasLoginIMFlow = availableClients.any { it.userFlow == "login_im" }
            
            // Show buttons based only on API config support
            if (!isLoadingConfig) {
                // PNV Button - show if config has PNV flows
                if (hasPnvFlows) {
                    FeatureButton(
                        text = "Phone Number Verification",
                        onClick = onPnvClick,
                        showArrow = true
                    )
                    Spacer(modifier = Modifier.height(18.dp))

                    FeatureButton(
                        text = "Auto Verify Phone Number",
                        onClick = onAutomaticPnvVerifyClick
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // Login Button - show if config has login flows
                if (hasLoginFlows) {
                    FeatureButton(
                        text = "Login - Quick Access",
                        onClick = onQuickLoginClick,
                        containerColor = Color(0xFFFF8A00),
                        contentColor = Color.White
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // Anonymous Button - show if config has anonymous flow
                if (hasAnonymousFlow) {
                    FeatureButton(
                        text = "Anonymous Identity",
                        onClick = onAnonymousLoginClick,
                        containerColor = Color(0xFFFF8A00),
                        contentColor = Color.White
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // TS43 Login Button - show if config has TS43 flows
                if (hasTS43Flows) {
                    TS43FeatureButton(
                        text = "TS43 Login - Quick Access ",
                        onClick = onTS43QuickLoginClick
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // IM Button - show if config has IM flow
                if (hasLoginIMFlow) {
                    IMFeatureButton(
                        text = "IM Verification",
                        onClick = {
                            // Find first IM client and trigger flow
                            val loginIMChannel = availableClients.firstOrNull { it.userFlow.contains("login_im") }
                            if (loginIMChannel != null) {
                                onFlowClick(loginIMChannel.userFlow)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }

            // Spacer to push policy to the bottom
            Spacer(modifier = Modifier.weight(1f))

            // Privacy Policy Text
            PrivacyPolicyText(
                onClick = { onPrivacyPolicyClick() },
                modifier = Modifier.padding(bottom = 22.dp)
            )

            // Version label
            Text(
                text = "v${BuildConfig.VERSION_NAME}${BuildConfig.FLAVOR_SUFFIX}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(bottom = 0.dp)
            )
            Spacer(modifier = Modifier.height(70.dp))
        }
    }
}



// Refactored Toggle with a private helper to reduce repeated code
@Composable
private fun EnvironmentToggleButton(
    text: String,
    isSelected: Boolean,
    shape: Shape,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFCECECE)
    val textColor = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF8C8C8C)

    OutlinedButton(
        onClick = onClick,
        shape = shape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = textColor
        ),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.defaultMinSize(minWidth = 0.dp, minHeight = 0.dp),
        // 2) Give exactly 5.dp horizontal padding around your text:
        contentPadding = PaddingValues(
            horizontal = 2.dp,
            vertical   = 2.dp
        )


    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall)
    }
}

private fun environmentToggleLabel(serverId: String): String {
    val trimmedServerId = serverId.trim()

    return when (trimmedServerId.lowercase()) {
        "global" -> "GL"
        "singapore" -> "SG"
        "indonesia" -> "ID"
        "united_states", "united-states", "united states" -> "US"
        else -> {
            val parts = trimmedServerId
                .split('_', '-', ' ')
                .filter { it.isNotBlank() }
            val label = if (parts.size > 1) {
                parts.joinToString("") { it.first().uppercase() }.take(2)
            } else {
                trimmedServerId.filter { it.isLetterOrDigit() }.take(2).uppercase()
            }

            label.ifBlank { trimmedServerId.take(2).uppercase() }
        }
    }
}

@Composable
fun DynamicEnvironmentToggle(
    selectedServerId: String?,
    onServerChanged: (String) -> Unit,
    authServers: List<com.ipification.demoapp.model.config.AuthServer>
) {
    if (authServers.isEmpty()) return

    Row(
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.wrapContentWidth()
    ) {
        authServers.forEachIndexed { index, server ->
            val shape = when {
                authServers.size == 1 -> RoundedCornerShape(4.dp)
                index == 0 -> RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)
                index == authServers.size - 1 -> RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                else -> RoundedCornerShape(0.dp)
            }
            
            EnvironmentToggleButton(
                text = environmentToggleLabel(server.id),
                isSelected = selectedServerId == server.id,
                shape = shape,
                onClick = { onServerChanged(server.id) }
            )
        }
    }
}

@Composable
fun EnvironmentToggle(
    selected: IPEnvironment,
    onSelectionChanged: (IPEnvironment) -> Unit
) {
    Row(
        modifier = Modifier.width(160.dp)
    ) {
        // Stage Button
        EnvironmentToggleButton(
            text = "Stage",
            isSelected = selected == IPEnvironment.SANDBOX,
            shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp),
            onClick = { onSelectionChanged(IPEnvironment.SANDBOX) }
        )

        // Live button (now a middle button)
        EnvironmentToggleButton(
            text = "Live",
            isSelected = selected == IPEnvironment.PRODUCTION,
            shape = RoundedCornerShape(0.dp), // Middle buttons have square corners
            onClick = { onSelectionChanged(IPEnvironment.PRODUCTION) }
        )
//        Spacer(Modifier.width((-1).dp))

        // ID Button (the new end button)
        EnvironmentToggleButton(
            text = "ID",
            isSelected = selected == IPEnvironment.CUSTOM_URL,
            shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp),
            onClick = { onSelectionChanged(IPEnvironment.CUSTOM_URL) }
        )

    }
}

@Composable
fun InfoButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier       = Modifier.size(32.dp),           // 32dp tap target
        shape          = RoundedCornerShape(16.dp),     // circle
        border         = BorderStroke(1.dp, Color.White),
        colors         = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor   = MaterialTheme.colorScheme.primary
        ),
        contentPadding = PaddingValues(0.dp)             // zero out padding so we can position manually
    ) {
        Box(
            modifier          = Modifier
                .fillMaxSize()
                .padding(top = 0.dp),                    // nudge the icon down a bit from the very edge
            contentAlignment = Alignment.TopCenter        // align icon at top‐center
        ) {
            Icon(
                imageVector        = Icons.Default.Info,
                contentDescription = "Info",
                modifier           = Modifier.size(16.dp)
            )
        }
    }
}
@Composable
fun FeatureButton(
    text: String,
    onClick: () -> Unit,
    showArrow: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(4.dp), // Slightly rounded corners to match screenshot
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
            )
            if (showArrow) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Navigate",
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(24.dp)
                )
            }
        }
    }
}

@Composable
fun TS43FeatureButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(4.dp), // Slightly rounded corners to match screenshot
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF4CAF50), // Material green 500
            contentColor = Color.White          // text/icon color
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
        )
    }
}



@Composable
fun IMFeatureButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFE91E63),
            contentColor = Color.White
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
fun DynamicFlowButton(title: String, userFlow: String, onClick: () -> Unit) {
    val buttonColor = when {
        userFlow.contains("pvn") -> Color(0xFF2196F3) // Blue for PNV flows
        userFlow.contains("login") -> Color(0xFF4CAF50) // Green for login flows
        userFlow.contains("anonymous") -> Color(0xFF9C27B0) // Purple for anonymous
        userFlow.contains("kyc") -> Color(0xFFFF5722) // Deep orange for KYC
        userFlow.contains("sim") -> Color(0xFF00BCD4) // Cyan for SIM
        userFlow.contains("im") -> Color(0xFFE91E63) // Pink for IM
        else -> MaterialTheme.colorScheme.primary // Default theme color
    }
    
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonColor,
            contentColor = Color.White
        )
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
fun PrivacyPolicyText(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val annotatedString = buildAnnotatedString {
        append("By continuing this verification you are\nagreeing to the ")
        withStyle(style = SpanStyle(textDecoration = TextDecoration.Underline)) {
            append("Privacy policy")
        }
    }

    ClickableText(
        text = annotatedString,
        style = MaterialTheme.typography.bodySmall.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant, // Using theme color
            textAlign = TextAlign.Center
        ),
        modifier = modifier.fillMaxWidth(),
        onClick = {
            // The entire text is clickable
            onClick()
        }
    )
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    AppTheme {
        MainScreenContent(
            selectedEnvironment = IPEnvironment.PRODUCTION,
            onEnvironmentChange = {},
            onInfoClick = {},
            onPnvClick = {},
            onAutomaticPnvVerifyClick = {},
            onQuickLoginClick = {},
            onTS43QuickLoginClick = {},
            onAnonymousLoginClick = {},
            onPrivacyPolicyClick = {}
        )
    }
}
