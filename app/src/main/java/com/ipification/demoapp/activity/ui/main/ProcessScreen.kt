package com.ipification.demoapp.activity.ui.main
import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ipification.demoapp.activity.AppTheme
import com.ipification.demoapp.model.ProcessNavigation
import com.ipification.demoapp.model.ProcessState
import com.ipification.demoapp.model.ProcessViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessScreen(
    userFlow: String,
    loginHint: String?,
    onBack: () -> Unit,
    onResult: (response: String?, error: String?, isTS43: Boolean) -> Unit,
    onSmsOtp: (phoneNumber: String, authReqId: String, nonce: String, clientId: String, serverId: String) -> Unit = { _, _, _, _, _ -> },
    viewModel: ProcessViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val activity = context as Activity

    // Start the authentication flow when the screen is first displayed
    LaunchedEffect(Unit) {
        viewModel.startAuthenticationWithUserFlow(activity, userFlow, loginHint)
    }
    // Handle navigation
    LaunchedEffect(state.navigation) {
        when (val nav = state.navigation) {
            is ProcessNavigation.ToResult -> {
                onResult(nav.response, nav.error, nav.isTS43)
                viewModel.onNavigationHandled()
            }
            is ProcessNavigation.ToSmsOtp -> {
                onSmsOtp(nav.phoneNumber, nav.authReqId, nav.nonce, nav.clientId, nav.serverId)
                viewModel.onNavigationHandled()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Authenticating...") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.isLoading) {
                    Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        ProcessScreenContent(
            modifier = Modifier.padding(paddingValues),
            state = state
        )
    }
}

@Composable
fun ProcessScreenContent(
    modifier: Modifier = Modifier,
    state: ProcessState
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (state.isLoading) {
            CircularProgressIndicator()
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = state.message,
                    color = if (state.isError) MaterialTheme.colorScheme.error else Color.Unspecified,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProcessScreenPreview() {
    AppTheme {
        ProcessScreenContent(
            state = ProcessState(isLoading = false, message = "Authentication Success!")
        )
    }
}
