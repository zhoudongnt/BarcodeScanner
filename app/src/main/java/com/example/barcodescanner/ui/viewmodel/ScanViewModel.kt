package com.example.barcodescanner.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OcrResult(
    val text: String = "",
    val confidence: Float? = null
)

sealed class ScanState {
    object Idle : ScanState()
    object Scanning : ScanState()
    data class Success(val result: OcrResult) : ScanState()
    data class Error(val message: String) : ScanState()
}

class ScanViewModel : ViewModel() {

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _torchEnabled = MutableStateFlow(false)
    val torchEnabled: StateFlow<Boolean> = _torchEnabled.asStateFlow()

    private val _lastDetectedText = MutableStateFlow("")
    val lastDetectedText: StateFlow<String> = _lastDetectedText.asStateFlow()

    @Volatile
    var selectionRect: android.graphics.Rect? = null

    fun onTextDetected(text: String, confidence: Float?) {
        if (text.isNotBlank()) {
            _lastDetectedText.value = text
            _scanState.value = ScanState.Success(OcrResult(text, confidence))
        }
    }

    fun toggleTorch() {
        _torchEnabled.value = !_torchEnabled.value
    }

    fun resetScan() {
        _scanState.value = ScanState.Scanning
        _lastDetectedText.value = ""
    }

    fun setScanning() {
        _scanState.value = ScanState.Scanning
    }

    fun setError(message: String) {
        _scanState.value = ScanState.Error(message)
    }
}
