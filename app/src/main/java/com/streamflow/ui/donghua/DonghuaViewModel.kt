package com.streamflow.ui.donghua

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DonghuaViewModel : ViewModel() {
    private val _detectedStreamUrl = MutableStateFlow<String?>(null)
    val detectedStreamUrl: StateFlow<String?> = _detectedStreamUrl

    fun onStreamDetected(url: String) {
        _detectedStreamUrl.value = url
    }

    fun clearDetectedStream() {
        _detectedStreamUrl.value = null
    }
}
