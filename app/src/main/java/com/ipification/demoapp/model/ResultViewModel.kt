package com.ipification.demoapp.model

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

data class ResultState(
    val formattedResponse: String = "",
    val isTS43Flow: Boolean = false
)

class ResultViewModel : ViewModel() {

    private val _state = MutableStateFlow(ResultState())
    val state = _state.asStateFlow()

    fun processResult(response: String?, errorMessage: String?, isTS43: Boolean) {
        val formatted = formatResponse(response, errorMessage)
        _state.update { it.copy(formattedResponse = formatted, isTS43Flow = isTS43) }
    }

    private fun formatResponse(response: String?, errorMessage: String?): String {
        return try {
            JSONObject(response ?: "{}")
                .toString(4)
                .replace("\\/", "/")
        } catch (e: Exception) {
            errorMessage ?: response ?: "An unknown error occurred."
        }
    }
}
