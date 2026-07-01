package com.example.barcodescanner.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScanResult(
    val rawValue: String = "",
    val format: String = "",
    val valueType: String = ""
)

sealed class ScanState {
    object Idle : ScanState()
    object Scanning : ScanState()
    data class Success(val result: ScanResult) : ScanState()
    data class Error(val message: String) : ScanState()
}

class ScanViewModel : ViewModel() {

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _torchEnabled = MutableStateFlow(false)
    val torchEnabled: StateFlow<Boolean> = _torchEnabled.asStateFlow()

    fun onBarcodeDetected(barcodes: List<Barcode>) {
        if (barcodes.isNotEmpty() && _scanState.value !is ScanState.Success) {
            val barcode = barcodes.first()
            val result = ScanResult(
                rawValue = barcode.rawValue ?: "",
                format = getFormatName(barcode.format),
                valueType = getValueTypeName(barcode.valueType)
            )
            _scanState.value = ScanState.Success(result)
        }
    }

    fun toggleTorch() {
        _torchEnabled.value = !_torchEnabled.value
    }

    fun resetScan() {
        _scanState.value = ScanState.Scanning
    }

    fun setScanning() {
        _scanState.value = ScanState.Scanning
    }

    fun setError(message: String) {
        _scanState.value = ScanState.Error(message)
    }

    private fun getFormatName(format: Int): String {
        return when (format) {
            Barcode.FORMAT_QR_CODE -> "QR Code"
            Barcode.FORMAT_EAN_13 -> "EAN-13"
            Barcode.FORMAT_EAN_8 -> "EAN-8"
            Barcode.FORMAT_UPC_A -> "UPC-A"
            Barcode.FORMAT_UPC_E -> "UPC-E"
            Barcode.FORMAT_CODE_128 -> "Code 128"
            Barcode.FORMAT_CODE_39 -> "Code 39"
            Barcode.FORMAT_CODE_93 -> "Code 93"
            Barcode.FORMAT_CODABAR -> "Codabar"
            Barcode.FORMAT_ITF -> "ITF"
            Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
            Barcode.FORMAT_PDF417 -> "PDF417"
            Barcode.FORMAT_AZTEC -> "Aztec"
            else -> "Unknown"
        }
    }

    private fun getValueTypeName(valueType: Int): String {
        return when (valueType) {
            Barcode.TYPE_TEXT -> "Text"
            Barcode.TYPE_URL -> "URL"
            Barcode.TYPE_EMAIL -> "Email"
            Barcode.TYPE_PHONE -> "Phone"
            Barcode.TYPE_SMS -> "SMS"
            Barcode.TYPE_WIFI -> "WiFi"
            Barcode.TYPE_GEO -> "Location"
            Barcode.TYPE_CONTACT_INFO -> "Contact"
            Barcode.TYPE_CALENDAR_EVENT -> "Calendar Event"
            Barcode.TYPE_DRIVER_LICENSE -> "Driver License"
            else -> "Unknown"
        }
    }
}
