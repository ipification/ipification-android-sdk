package com.ipification.demoapp.activity

import android.app.Activity
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ipification.demoapp.R
import com.ipification.demoapp.activity.ui.main.MainScreen
import com.ipification.demoapp.activity.ui.main.ProcessScreen
import com.ipification.demoapp.activity.ui.main.ResultScreen
import com.ipification.demoapp.activity.ui.pnv.PnvScreen
import com.ipification.demoapp.activity.ui.main.SmsOtpScreen
import com.ipification.demoapp.model.MainViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import java.net.URLDecoder
import java.net.URLEncoder

// 1. Define App Color Scheme based on your colors.xml
private val AppLightColorScheme = lightColorScheme(
    primary = Color(0xFF0F2CD3),
    onPrimary = Color.White,
    // Other colors will use Material 3 defaults. You can customize them here.
)

// 1. Define App Color Scheme based on your colors.xml
private val AppDarkColorScheme = lightColorScheme(
    primary = Color(0xFF0F2CD3),
    onPrimary = Color.White,
    // Other colors will use Material 3 defaults. You can customize them here.
)


// 2. Define App Typography using your Inter font family from font.xml
private val InterFontFamily = FontFamily(
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_black, FontWeight.Black)
)

private val AppTypography = Typography(
    displayLarge = Typography().displayLarge.copy(fontFamily = InterFontFamily),
    displayMedium = Typography().displayMedium.copy(fontFamily = InterFontFamily),
    displaySmall = Typography().displaySmall.copy(fontFamily = InterFontFamily),
    headlineLarge = Typography().headlineLarge.copy(fontFamily = InterFontFamily),
    headlineMedium = Typography().headlineMedium.copy(fontFamily = InterFontFamily),
    headlineSmall = Typography().headlineSmall.copy(fontFamily = InterFontFamily),
    titleLarge = Typography().titleLarge.copy(fontFamily = InterFontFamily),
    titleMedium = Typography().titleMedium.copy(fontFamily = InterFontFamily),
    titleSmall = Typography().titleSmall.copy(fontFamily = InterFontFamily),
    bodyLarge = Typography().bodyLarge.copy(fontFamily = InterFontFamily),
    bodyMedium = Typography().bodyMedium.copy(fontFamily = InterFontFamily),
    bodySmall = Typography().bodySmall.copy(fontFamily = InterFontFamily),
    labelLarge = Typography().labelLarge.copy(fontFamily = InterFontFamily),
    labelMedium = Typography().labelMedium.copy(fontFamily = InterFontFamily),
    labelSmall = Typography().labelSmall.copy(fontFamily = InterFontFamily)
)

// 3. Define the main Theme Composable
@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    // Check if the system is currently in dark mode
    val isDarkTheme = isSystemInDarkTheme()

    // Select the appropriate color scheme
    val colorScheme = if (isDarkTheme) {
        AppDarkColorScheme
    } else {
        AppLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme, // Use the selected color scheme
        typography = AppTypography,
        content = content
    )
}


class MainActivity : ComponentActivity() { // Changed from AppCompatActivity
    private val mainViewModel: MainViewModel by viewModels()
    private var navController: androidx.navigation.NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Using custom splash screen instead of Android 12+ API to avoid circular masking
        // installSplashScreen()
        super.onCreate(savedInstanceState)

        // Explicitly hide the action bar

        // This status bar logic can remain as is
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            window.statusBarColor = AndroidColor.WHITE
        }

        // Set the content of the activity to be a Compose screen, wrapped in our custom theme
        setContent {
            AppTheme {
                AppNavigation(
                    mainViewModel = mainViewModel,
                    onNavigationReady = { controller -> navController = controller }
                )
            }
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Handle IM Service activity result
        com.ipification.mobile.sdk.im.IMService.onActivityResult(requestCode, resultCode, data)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@Composable
fun AppNavigation(
    mainViewModel: MainViewModel,
    onNavigationReady: (androidx.navigation.NavHostController) -> Unit = {}
) {
    // NavController handles navigation between your composables
    val navController = rememberNavController()
    val uiState by mainViewModel.state.collectAsState()

    // Notify when navigation is ready
    androidx.compose.runtime.LaunchedEffect(navController) {
        onNavigationReady(navController)
    }

    // NavHost is the container for your navigation graph
    NavHost(navController = navController, startDestination = "main") {
        // Main screen route
        composable("main") {
            MainScreen(
                viewModel = mainViewModel,
                navigateToPnv = {
                    navController.navigate("pnv")
                },
                navigateToProcess = { userFlow, loginHint ->
                    val encodedUserFlow = URLEncoder.encode(userFlow, Charsets.UTF_8.name())
                    val encodedLoginHint = URLEncoder.encode(loginHint.orEmpty(), Charsets.UTF_8.name())
                    navController.navigate("process/$encodedUserFlow?loginHint=$encodedLoginHint")
                },
            )
        }

        composable("pnv") {
            PnvScreen(
                environment         = uiState.selectedEnvironment,
                onBack = { navController.popBackStack() },
                onNavigateToProcess = { userFlow, loginHint ->
                    val encodedUserFlow = URLEncoder.encode(userFlow, Charsets.UTF_8.name())
                    val encodedLoginHint = URLEncoder.encode(loginHint, Charsets.UTF_8.name())
                    navController.navigate("process/$encodedUserFlow?loginHint=$encodedLoginHint")
                }
            )
        }

        composable(
            route = "process/{userFlow}?loginHint={loginHint}",
            arguments = listOf(
                navArgument("userFlow") { type = NavType.StringType },
                navArgument("loginHint") {
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) { backStackEntry ->
            val userFlow = URLDecoder.decode(backStackEntry.arguments?.getString("userFlow") ?: "", Charsets.UTF_8.name())
            val loginHint = backStackEntry.arguments?.getString("loginHint")
                ?.takeIf { it.isNotEmpty() }
                ?.let { URLDecoder.decode(it, Charsets.UTF_8.name()) }
            ProcessScreen(
                userFlow = userFlow,
                loginHint = loginHint,
                onResult = { response, error, isTs43 ->
                    val encodedResponse =
                        URLEncoder.encode(response ?: "", Charsets.UTF_8.name())
                    val encodedError =
                        URLEncoder.encode(error ?: "", Charsets.UTF_8.name())

                    navController.navigate("result?response=$encodedResponse&error=$encodedError&isTS43=$isTs43") {
                        popUpTo(backStackEntry.destination.id) {
                            inclusive = true
                        }
                    }
                },
                onSmsOtp = { phoneNumber, authReqId, nonce, clientId, serverId ->
                    val encodedPhone = URLEncoder.encode(phoneNumber, Charsets.UTF_8.name())
                    val encodedAuthReqId = URLEncoder.encode(authReqId, Charsets.UTF_8.name())
                    val encodedNonce = URLEncoder.encode(nonce, Charsets.UTF_8.name())
                    val encodedClientId = URLEncoder.encode(clientId, Charsets.UTF_8.name())
                    val encodedServerId = URLEncoder.encode(serverId, Charsets.UTF_8.name())
                    navController.navigate("smsOtp/$encodedPhone/$encodedAuthReqId/$encodedNonce/$encodedClientId/$encodedServerId") {
                        popUpTo(backStackEntry.destination.id) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        // SMS OTP screen route
        composable(
            route = "smsOtp/{phoneNumber}/{authReqId}/{nonce}/{clientId}/{serverId}",
            arguments = listOf(
                navArgument("phoneNumber") { type = NavType.StringType },
                navArgument("authReqId") { type = NavType.StringType },
                navArgument("nonce") { type = NavType.StringType },
                navArgument("clientId") { type = NavType.StringType },
                navArgument("serverId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val phoneNumber = URLDecoder.decode(backStackEntry.arguments?.getString("phoneNumber") ?: "", Charsets.UTF_8.name())
            val authReqId = URLDecoder.decode(backStackEntry.arguments?.getString("authReqId") ?: "", Charsets.UTF_8.name())
            val nonce = URLDecoder.decode(backStackEntry.arguments?.getString("nonce") ?: "", Charsets.UTF_8.name())
            val clientId = URLDecoder.decode(backStackEntry.arguments?.getString("clientId") ?: "", Charsets.UTF_8.name())
            val serverId = URLDecoder.decode(backStackEntry.arguments?.getString("serverId") ?: "", Charsets.UTF_8.name())

            val smsViewModel: com.ipification.demoapp.model.ProcessViewModel = viewModel()
            val smsActivity = androidx.compose.ui.platform.LocalContext.current as android.app.Activity

            SmsOtpScreen(
                phoneNumber = phoneNumber,
                isLoading = smsViewModel.state.collectAsState().value.isLoading,
                onVerify = { otpCode ->
                    smsViewModel.submitSmsOtp(smsActivity, otpCode, authReqId, clientId, nonce, serverId)
                },
                onResend = {
                    val client = com.ipification.demoapp.manager.ConfigManager.getClientByUserFlow("pvn_sms")
                    smsViewModel.resendSmsOtp(smsActivity, phoneNumber, clientId, client?.scope ?: "openid ip:phone_verify", serverId)
                },
                onBack = { navController.popBackStack() }
            )

            // Handle navigation from SMS OTP screen
            val smsState by smsViewModel.state.collectAsState()
            androidx.compose.runtime.LaunchedEffect(smsState.navigation) {
                when (val nav = smsState.navigation) {
                    is com.ipification.demoapp.model.ProcessNavigation.ToResult -> {
                        val encodedResponse = URLEncoder.encode(nav.response.orEmpty(), Charsets.UTF_8.name())
                        val encodedError = URLEncoder.encode(nav.error.orEmpty(), Charsets.UTF_8.name())
                        navController.navigate("result?response=$encodedResponse&error=$encodedError&isTS43=false") {
                            popUpTo("main") { inclusive = false }
                        }
                        smsViewModel.onNavigationHandled()
                    }
                    is com.ipification.demoapp.model.ProcessNavigation.ToSmsOtp -> {
                        // Resend navigates to new OTP screen with updated auth data
                        val encodedPhone = URLEncoder.encode(nav.phoneNumber, Charsets.UTF_8.name())
                        val encodedAuthReqId = URLEncoder.encode(nav.authReqId, Charsets.UTF_8.name())
                        val encodedNonce = URLEncoder.encode(nav.nonce, Charsets.UTF_8.name())
                        val encodedClientId = URLEncoder.encode(nav.clientId, Charsets.UTF_8.name())
                        val encodedServerId = URLEncoder.encode(nav.serverId, Charsets.UTF_8.name())
                        navController.navigate("smsOtp/$encodedPhone/$encodedAuthReqId/$encodedNonce/$encodedClientId/$encodedServerId") {
                            popUpTo("smsOtp/{phoneNumber}/{authReqId}/{nonce}/{clientId}/{serverId}") { inclusive = true }
                        }
                        smsViewModel.onNavigationHandled()
                    }
                    else -> {}
                }
            }
        }

        composable(
            route = "result?response={response}&error={error}&isTS43={isTS43}",
            arguments = listOf(
                navArgument("response") { type = NavType.StringType; nullable = true },
                navArgument("error") { type = NavType.StringType; nullable = true },
                navArgument("isTS43") { type = NavType.BoolType; nullable = false }
            )
        ) { backStackEntry ->
            val response = backStackEntry.arguments?.getString("response")?.let {
                URLDecoder.decode(it, Charsets.UTF_8.name())
            }
            val error = backStackEntry.arguments?.getString("error")?.let {
                URLDecoder.decode(it, Charsets.UTF_8.name())
            }
            val isTS43 = backStackEntry.arguments?.getBoolean("isTS43")
            ResultScreen(
                response = response,
                errorMessage = error,
                isTS43 = isTS43 ?: false,
                onClose = { navController.popBackStack() }
            )
        }

    }
}


// These extension functions are still useful and can be kept as they are.
fun Activity.hideKeyboard() {
    if (currentFocus != null) {
        val inputMethodManager: InputMethodManager =
            getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(currentFocus!!.windowToken, 0)
    }
}

fun View.hideKeyboard() {
    val inputMethodManager: InputMethodManager =
        context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow(windowToken, 0)
}
