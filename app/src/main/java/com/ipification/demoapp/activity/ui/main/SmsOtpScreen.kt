package com.ipification.demoapp.activity.ui.main

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import com.ipification.demoapp.activity.AppTheme
import com.ipification.demoapp.util.Util
import com.ipification.mobile.sdk.ip.utils.IPLogs
import kotlinx.coroutines.delay

private const val OTP_LENGTH = 6
private const val RESEND_TIMEOUT_SECONDS = 60

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsOtpScreen(
    phoneNumber: String,
    isLoading: Boolean = false,
    onVerify: (otpCode: String) -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit
) {
    var otpValue by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var resendTimer by remember { mutableIntStateOf(RESEND_TIMEOUT_SECONDS) }
    var canResend by remember { mutableStateOf(false) }
    var showResendAlert by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val activity = context as? Activity

    // SMS User Consent: handle the consent dialog result
    val smsConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        IPLogs.getInstance().LOG += "[SmsOtpScreen] ${Util.getCurrentDate()} - SMS_CONSENT_RESULT - resultCode: ${result.resultCode}\n"
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val message = result.data?.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE) ?: ""
            IPLogs.getInstance().LOG += "[SmsOtpScreen] ${Util.getCurrentDate()} - SMS_BODY: $message\n"
            // Extract 6-digit OTP code from SMS
            val code = Regex("\\b(\\d{6})\\b").find(message)?.groupValues?.get(1)
            if (code != null) {
                IPLogs.getInstance().LOG += "[SmsOtpScreen] ${Util.getCurrentDate()} - OTP_EXTRACTED: $code\n"
                otpValue = code
                if (!isSubmitting) {
                    IPLogs.getInstance().LOG += "[SmsOtpScreen] ${Util.getCurrentDate()} - AUTO_VERIFY: submitting code $code\n"
                    isSubmitting = true
                    onVerify(code)
                }
            } else {
                IPLogs.getInstance().LOG += "[SmsOtpScreen] ${Util.getCurrentDate()} - OTP_EXTRACT_FAILED: no 6-digit code found\n"
            }
        } else {
            IPLogs.getInstance().LOG += "[SmsOtpScreen] ${Util.getCurrentDate()} - SMS_CONSENT_DECLINED\n"
        }
    }

    // Start SMS User Consent listener
    DisposableEffect(Unit) {
        IPLogs.getInstance().LOG += "[SmsOtpScreen] ${Util.getCurrentDate()} - SMS_CONSENT_INIT - START\n"
        val smsClient = SmsRetriever.getClient(context)
        smsClient.startSmsUserConsent(null)
            .addOnSuccessListener {
                IPLogs.getInstance().LOG += "[SmsOtpScreen] ${Util.getCurrentDate()} - SMS_CONSENT_INIT - SUCCESS\n"
                Log.d("SmsOtpScreen", "SMS User Consent listener started successfully")
            }
            .addOnFailureListener { e ->
                IPLogs.getInstance().LOG += "[SmsOtpScreen] ${Util.getCurrentDate()} - SMS_CONSENT_INIT - FAILED: ${e.message}\n"
                Log.e("SmsOtpScreen", "SMS User Consent listener failed", e)
            }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                IPLogs.getInstance().LOG += "[SmsOtpScreen] ${Util.getCurrentDate()} - SMS_BROADCAST_RECEIVED - action: ${intent?.action}\n"
                if (intent?.action == SmsRetriever.SMS_RETRIEVED_ACTION) {
                    val extras = intent.extras ?: return
                    val smsStatus = extras.get(SmsRetriever.EXTRA_STATUS) as? Status ?: return
                    IPLogs.getInstance().LOG += "[SmsOtpScreen] ${Util.getCurrentDate()} - SMS_STATUS_CODE: ${smsStatus.statusCode}\n"
                    when (smsStatus.statusCode) {
                        CommonStatusCodes.SUCCESS -> {
                            IPLogs.getInstance().LOG += "[SmsOtpScreen] ${Util.getCurrentDate()} - SMS_DETECTED - launching consent dialog\n"
                            val consentIntent = extras.getParcelable<Intent>(SmsRetriever.EXTRA_CONSENT_INTENT)
                            if (consentIntent != null) {
                                try {
                                    smsConsentLauncher.launch(consentIntent)
                                } catch (e: Exception) {
                                    IPLogs.getInstance().LOG += "[SmsOtpScreen] ${Util.getCurrentDate()} - CONSENT_LAUNCH_ERROR: ${e.message}\n"
                                    Log.e("SmsOtpScreen", "Error launching consent intent", e)
                                }
                            }
                        }
                        CommonStatusCodes.TIMEOUT -> {
                            IPLogs.getInstance().LOG += "[SmsOtpScreen] ${Util.getCurrentDate()} - SMS_CONSENT_TIMEOUT\n"
                        }
                    }
                }
            }
        }

        val intentFilter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
        ContextCompat.registerReceiver(context, receiver, intentFilter, ContextCompat.RECEIVER_EXPORTED)
        IPLogs.getInstance().LOG += "[SmsOtpScreen] ${Util.getCurrentDate()} - SMS_RECEIVER_REGISTERED\n"

        onDispose {
            try {
                context.unregisterReceiver(receiver)
                IPLogs.getInstance().LOG += "[SmsOtpScreen] ${Util.getCurrentDate()} - SMS_RECEIVER_UNREGISTERED\n"
            } catch (_: Exception) {}
        }
    }

    // Countdown timer for resend
    LaunchedEffect(resendTimer) {
        if (resendTimer > 0) {
            delay(1000L)
            resendTimer--
        } else {
            canResend = true
        }
    }

    // Auto-focus the input
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "OTP Verification",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Enter the OTP code we sent to",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = phoneNumber,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // OTP digit boxes
            BasicTextField(
                value = otpValue,
                onValueChange = { newValue ->
                    if (newValue.length <= OTP_LENGTH && newValue.all { it.isDigit() }) {
                        otpValue = newValue
                        if (newValue.length == OTP_LENGTH && !isSubmitting) {
                            isSubmitting = true
                            onVerify(newValue)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.focusRequester(focusRequester),
                decorationBox = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(OTP_LENGTH) { index ->
                            val char = otpValue.getOrNull(index)?.toString() ?: ""
                            val isFocused = index == otpValue.length
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .border(
                                        width = if (isFocused) 2.dp else 1.5.dp,
                                        color = if (isFocused) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = char,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 24.sp
                                    ),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Verify button
            Button(
                onClick = {
                    isSubmitting = true
                    onVerify(otpValue)
                },
                enabled = otpValue.length == OTP_LENGTH && !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "Verify",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Resend section
            Text(
                text = "Didn't receive OTP code?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            val minutes = resendTimer / 60
            val seconds = resendTimer % 60
            val resendColor = if (canResend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

            Row(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable(enabled = canResend) { showResendAlert = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Resend",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                    ),
                    color = resendColor
                )
                if (!canResend) {
                    Text(
                        text = " - %02d : %02d".format(minutes, seconds),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = resendColor
                    )
                }
            }
        }
    }

    if (showResendAlert) {
        AlertDialog(
            onDismissRequest = { showResendAlert = false },
            title = { Text("") },
            text = { Text("TBD") },
            confirmButton = {
                TextButton(onClick = { showResendAlert = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SmsOtpScreenPreview() {
    AppTheme {
        SmsOtpScreen(
            phoneNumber = "+32 123456789",
            onVerify = {},
            onResend = {},
            onBack = {}
        )
    }
}
