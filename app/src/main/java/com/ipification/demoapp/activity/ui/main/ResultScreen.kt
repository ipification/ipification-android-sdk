package com.ipification.demoapp.activity.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ipification.demoapp.BuildConfig
import com.ipification.demoapp.activity.AppTheme
import com.ipification.demoapp.model.ResultState
import com.ipification.demoapp.model.ResultViewModel
import com.ipification.mobile.sdk.ip.IPConfiguration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    response: String?,
    errorMessage: String?,
    isTS43: Boolean,
    onClose: () -> Unit,
    viewModel: ResultViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.processResult(response, errorMessage, isTS43)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { paddingValues ->
        ResultScreenContent(
            modifier = Modifier.padding(paddingValues),
            state = state
        )
    }
}

@Composable
fun ResultScreenContent(
    modifier: Modifier = Modifier,
    state: ResultState
) {
    val clientId = remember { IPConfiguration.getInstance().CLIENT_ID }
    val currentState = remember { IPConfiguration.getInstance().currentState }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally

    ) {
        Text(
            text = state.formattedResponse,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.weight(1f))

        // Show client id and state at bottom left
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "client id: $clientId",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "state: $currentState",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(0.dp))

        // Version label
        Text(
            text = "v${BuildConfig.VERSION_NAME}${BuildConfig.FLAVOR_SUFFIX}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.End)
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun ResultScreenPreview() {
    AppTheme {
        ResultScreenContent(
            state = ResultState(
                formattedResponse = """
                {
                    "sub": "123456789",
                    "phone_number_verified": true,
                    "phone_number": "+1234567890"
                }
                """.trimIndent()
            )
        )
    }
}
