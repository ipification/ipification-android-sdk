package com.ipification.demoapp.activity.ui.pnv

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arpitkatiyarprojects.countrypicker.enums.CountryListDisplayType
import com.arpitkatiyarprojects.countrypicker.models.CountriesListDialogDisplayProperties
import com.arpitkatiyarprojects.countrypicker.models.CountryDetails
import com.arpitkatiyarprojects.countrypicker.models.CountryPickerColors
import com.arpitkatiyarprojects.countrypicker.models.SelectedCountryDisplayProperties
import com.arpitkatiyarprojects.countrypicker.utils.CountryPickerDefault
import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest
import com.google.android.gms.auth.api.identity.Identity
import com.ipification.demoapp.activity.AppTheme
import com.ipification.demoapp.model.PnvNavigation
import com.ipification.demoapp.model.PnvViewModel
import com.ipification.demoapp.model.PnvState
import com.ipification.demoapp.util.Util
import com.ipification.mobile.sdk.ip.AuthChannel
import com.ipification.mobile.sdk.ip.IPEnvironment
import com.ipification.mobile.sdk.ip.utils.PhoneNumberHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PnvScreen(
  environment: IPEnvironment,
  viewModel: PnvViewModel = viewModel(factory = PnvViewModel.provideFactory(environment, LocalContext.current)),
  onNavigateToProcess: (userFlow: String, loginHint: String) -> Unit,
  onBack: () -> Unit
) {
  val state by viewModel.state.collectAsState()
  val context = LocalContext.current
  val phonePerms = remember { PhoneNumberHelper.getRuntimeRequiredPermissions() }

  // State for phone number selection bottom sheet
  var showPhoneNumberSheet by remember { mutableStateOf(false) }
  var availablePhoneNumbers by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

  // Handle phone numbers fetched from device
  val handlePhoneNumbers: (List<Pair<String, String>>) -> Unit = { numbers ->
    when {
      numbers.size > 1 -> {
        // Multiple numbers - show bottom sheet for selection
        availablePhoneNumbers = numbers
        showPhoneNumberSheet = true
      }
      numbers.size == 1 -> {
        // Single number - use it directly
        val firstNumber = numbers.first()
        if (!firstNumber.first.isNullOrBlank()) {
          viewModel.onPhoneNumberFromHint(firstNumber.first, firstNumber.second)
        } else {
          val dial = Util.getSystemDialCode(context)
          viewModel.onCountryCodeFromHint(dial)
        }
      }
      else -> {
        // No numbers - fallback to country code
        val dial = Util.getSystemDialCode(context)
        viewModel.onCountryCodeFromHint(dial)
      }
    }
  }

  val permLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { grants ->
    val granted = phonePerms.all { grants[it] == true }
    if (granted) {
      PhoneNumberHelper.fetchPhoneNumberNow(context, AuthChannel.TS43) { numbers ->
        handlePhoneNumbers(numbers)
      }
    }
  }

  LaunchedEffect(Unit) {
    if (!viewModel.hasRequestedPhoneNumbers && state.phoneNumber.isEmpty()) {
      viewModel.markPhoneNumbersRequested()
      val notGranted = phonePerms.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
      if (notGranted.isNotEmpty()) {
        permLauncher.launch(notGranted.toTypedArray())
      } else if(environment != IPEnvironment.SANDBOX){
        PhoneNumberHelper.fetchPhoneNumberNow(context, AuthChannel.TS43) { numbers ->
          handlePhoneNumbers(numbers)
        }
      }
    }
  }

  // Handle navigation
  LaunchedEffect(state.navigation) {
    if (state.navigation is PnvNavigation.ToProcess) {
      val nav = state.navigation as PnvNavigation.ToProcess
      onNavigateToProcess(nav.userFlow, nav.loginHint)
      viewModel.onNavigationHandled()
    }
  }


  // Trigger phone number hint request
  LaunchedEffect(environment) {
    if (environment == IPEnvironment.PRODUCTION || environment == IPEnvironment.CUSTOM_URL) {
//      requestPermsThenFetch()
    }
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
    PnvScreenContent(
      modifier = Modifier.padding(paddingValues),
      state = state,
      onPhoneNumberChange = viewModel::onPhoneNumberChanged,
      onCountryChange = viewModel::onCountryChanged,
      onVerify = { viewModel.onVerifyClicked(false) },
      onVerifyPlus = { viewModel.onVerifyClicked(true) },
      onTS43Verify = { viewModel.onTS43VerifyClicked() },
      onSmsVerify = { viewModel.onSmsVerifyClicked() },
      onMultiChannelVerify = { viewModel.onMultiChannelVerifyClicked() },
      onClearSavedNumber = { viewModel.clearSavedPhoneNumber() }
    )
  }
  
  // Phone Number Selection Bottom Sheet
  if (showPhoneNumberSheet) {
    ModalBottomSheet(
      onDismissRequest = { showPhoneNumberSheet = false },
      containerColor = MaterialTheme.colorScheme.surface
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp)
          .padding(bottom = 32.dp)
      ) {
        Text(
          text = "Select Phone Number",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(bottom = 20.dp)
        )

        availablePhoneNumbers.forEachIndexed { index, (phoneNumber, countryCode) ->
          Surface(
            modifier = Modifier
              .fillMaxWidth(),
            onClick = {
              viewModel.onPhoneNumberFromHint(phoneNumber, countryCode)
              showPhoneNumberSheet = false
            },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(40.dp)
              ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                  Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = "Phone",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                  )
                }
              }
              Spacer(modifier = Modifier.width(14.dp))
              Column {
                Text(
                  text = phoneNumber,
                  style = MaterialTheme.typography.bodyLarge,
                  fontWeight = FontWeight.SemiBold
                )
                if (countryCode.isNotBlank()) {
                  Text(
                    text = countryCode.uppercase(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }
          if (index < availablePhoneNumbers.size - 1) {
            Spacer(modifier = Modifier.height(10.dp))
          }
        }
      }
    }
  }
}
@Composable
fun CountryPicker(
  modifier: Modifier = Modifier,
  defaultPaddingValues: PaddingValues = PaddingValues(4.dp, 6.dp),
  selectedCountryDisplayProperties: SelectedCountryDisplayProperties = SelectedCountryDisplayProperties(),
  countriesListDialogDisplayProperties: CountriesListDialogDisplayProperties = CountriesListDialogDisplayProperties(),
  defaultCountryCode: String? = null,
  countriesList: List<String>? = null,
  countryListDisplayType: CountryListDisplayType = CountryListDisplayType.Dialog,
  countryPickerColors: CountryPickerColors = CountryPickerDefault.colors(),
  isPickerEnabled: Boolean = true,
  onCountrySelected: (country: CountryDetails) -> Unit
) {
}
@Composable
fun PnvScreenContent(
  modifier: Modifier = Modifier,
  state: PnvState,
  onPhoneNumberChange: (String) -> Unit,
  onCountryChange: (countryCode: String) -> Unit,
  onVerify: () -> Unit,
  onVerifyPlus: () -> Unit,
  onTS43Verify: () -> Unit,
  onSmsVerify: () -> Unit,
  onMultiChannelVerify: () -> Unit,
  onClearSavedNumber: () -> Unit = {}
) {
  val phoneFieldFocusRequester = remember { FocusRequester() }
  val focusManager = LocalFocusManager.current
  val scrollState = rememberScrollState()

//  var showPicker by remember { mutableStateOf(true) }
  val context = LocalContext.current

  // When we get a country, notify & dismiss
//  if (showPicker) {
//    CountryPicker(
//      defaultCountryCode = state.countryCode,
//      countriesListDialogDisplayProperties = CountriesListDialogDisplayProperties(
////        displayType = CountryListDisplayType.Dialog
//      ),
//      countryPickerColors = CountryPickerDefault.colors(),
//      onCountrySelected = { countryDetails ->
//        onCountryChange(countryDetails.countryCode)
//        showPicker = false
//      }
//    )
//  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .imePadding()
      .navigationBarsPadding()
      .verticalScroll(scrollState)
      .padding(start = 25.dp, end = 25.dp)
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null // no ripple effect
      ) {
        focusManager.clearFocus()
      },

    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = "Phone Number Verification",
      style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(40.dp))

    Text(
      text = "Please enter your phone number to continue",
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(10.dp))

    // — country code + phone number row —
    Row(
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Country code
      Box(
        modifier = Modifier
          .weight(0.3f)
          .height(64.dp)         // this gives the Row a 56dp height for this slot
      ) {
        OutlinedTextField(
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          value = state.countryCode,
          onValueChange = onCountryChange,
          singleLine = true,     // <-- lock it to one line
          label = { Text("Code") },
//          readOnly = true,
          isError = state.error != null && state.errorType == "code",
          modifier = Modifier
            .fillMaxSize()       // fill the 56dp-high Box
        )
        // catch every tap
//        Box(
//          modifier = Modifier
//            .matchParentSize()
//            .clickable { showPicker = true }
//        )
      }

      Spacer(modifier = Modifier.width(8.dp))

      // Phone number
      OutlinedTextField(
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        value = state.phoneNumber,
        onValueChange = onPhoneNumberChange,
        singleLine = true,       // <-- lock it to one line
        label = { Text("Phone Number") },
        isError = state.error != null && state.errorType == "number",
        modifier = Modifier
          .weight(0.7f)
//          .height(56.dp)         // exact same height
          .focusRequester(phoneFieldFocusRequester),

        )
    }

    if (state.hasSavedPhoneNumber) {
      Text(
        text = "Clear & enter new number",
        style = MaterialTheme.typography.labelSmall.copy(
          textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
          .align(Alignment.End)
          .padding(top = 4.dp)
          .clickable { onClearSavedNumber() }
      )
    }

    state.error?.let {
      Text(
        text = it,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 4.dp)
      )
    }

    // push buttons down a little
    Spacer(modifier = Modifier.height(40.dp))

    // Get available PNV flows from API config
    val availableClients = com.ipification.demoapp.manager.ConfigManager.getAllClients()
    val hasPnvIp = availableClients.any { it.userFlow == "pvn_ip" }
    val hasPnvIpPlus = availableClients.any { it.userFlow == "pvn_ip_plus" }
    val hasPnvSim = availableClients.any { it.userFlow == "pvn_sim" || it.userFlow.contains("ts43") }
    val hasPnvSms = availableClients.any { it.userFlow == "pvn_sms" }
    val hasPnvMultiChannel = hasPnvIp && (hasPnvSim || hasPnvSms)

    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // Verify and Verify+ buttons - conditionally shown based on API config
      if (hasPnvIp || hasPnvIpPlus) {
        // Show buttons in a row if both are enabled, otherwise full width
        if (hasPnvIp && hasPnvIpPlus) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Button(
              onClick = {
                focusManager.clearFocus()
                onVerify()
              },
              modifier = Modifier
                .weight(1f)
                .height(50.dp),
              shape = RoundedCornerShape(4.dp),
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
              Text("Verify", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            }
            
            Button(
              onClick = {
                focusManager.clearFocus()
                onVerifyPlus()
              },
              modifier = Modifier
                .weight(1f)
                .height(50.dp),
              shape = RoundedCornerShape(4.dp),
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
              Text("Verify+", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            }
          }
        } else {
          // Single button - full width
          if (hasPnvIp) {
            Button(
              onClick = {
                focusManager.clearFocus()
                onVerify()
              },
              modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
              shape = RoundedCornerShape(4.dp),
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
              Text("Verify", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            }
          }
          
          if (hasPnvIpPlus) {
            Button(
              onClick = {
                focusManager.clearFocus()
                onVerifyPlus()
              },
              modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
              shape = RoundedCornerShape(4.dp),
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
              Text("Verify+", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            }
          }
        }
        Spacer(modifier = Modifier.height(10.dp))
      }
      // TS43/SIM Verify button - conditionally shown based on API config
      if (hasPnvSim) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.Center
        ) {
          Button(
            onClick = {
              focusManager.clearFocus()
              onTS43Verify()
            },
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp),
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = Color(0xFF4CAF50), // Material green 500
              contentColor = Color.White          // text/icon color
            )
          ) {
            Text(
              "TS43 Verify",
              style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
            )
          }
        }
        Spacer(modifier = Modifier.height(10.dp))
      }
      
      // SMS Verify button - conditionally shown based on API config
      if (hasPnvSms) {
        Button(
          onClick = {
            focusManager.clearFocus()
            onSmsVerify()
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
          shape = RoundedCornerShape(4.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFFF9800), // Material orange 500
            contentColor = Color.White
          )
        ) {
          Text(
            "SMS Verify",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
          )
        }
        Spacer(modifier = Modifier.height(10.dp))
      }

      if (hasPnvMultiChannel) {
        Button(
          onClick = {
            focusManager.clearFocus()
            onMultiChannelVerify()
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
          shape = RoundedCornerShape(4.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF673AB7),
            contentColor = Color.White
          )
        ) {
          Text(
            "Multi Channel Verify",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
          )
        }
        Spacer(modifier = Modifier.height(10.dp))
      }
    }

    Spacer(modifier = Modifier.height(24.dp))

    PrivacyPolicyText()
    Spacer(modifier = Modifier.height(50.dp))
  }
//  LaunchedEffect(Unit) {
//    phoneFieldFocusRequester.requestFocus()
//  }

}


@Composable
fun PrivacyPolicyText(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val annotatedString = buildAnnotatedString {
    append("By continuing this verification you are\nagreeing to the ")
    pushStringAnnotation(tag = "URL", annotation = "https://www.ipification.com/legal")
    withStyle(style = SpanStyle(textDecoration = TextDecoration.Underline)) {
      append("Privacy policy")
    }
    pop()
  }

  ClickableText(
    text = annotatedString,
    style = MaterialTheme.typography.bodySmall.copy(
      color = Color(0xFF999999),
      textAlign = TextAlign.Center
    ),
    modifier = modifier.fillMaxWidth(),
    onClick = { offset ->
      annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
        .firstOrNull()?.let { annotation ->
          val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
          context.startActivity(intent)
        }
    }
  )
}


@Preview(showBackground = true)
@Composable
private fun PnvScreenPreview() {
  AppTheme {
    PnvScreenContent(
      state = PnvState(phoneNumber = "123456789", error = null),
      onPhoneNumberChange = {},
      onCountryChange = { _ -> },
      onVerify = {},
      onVerifyPlus = {},
      onTS43Verify = {},
      onSmsVerify = {},
      onMultiChannelVerify = {}
    )
  }
}
