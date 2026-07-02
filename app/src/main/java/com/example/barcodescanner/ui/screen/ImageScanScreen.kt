package com.example.barcodescanner.ui.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageScanScreen(
    onBackClick: () -> Unit,
    onResultFound: (String, String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selection by remember { mutableStateOf<CropSelection?>(null) }
    var imageBoxSize by remember { mutableStateOf(IntSize.Zero) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bitmap = loadBitmapFromUri(context, it)
            selectedBitmap = bitmap
            selection = null
            errorMessage = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Image") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val bitmap = selectedBitmap
            if (bitmap != null) {
                Text(
                    text = "Drag on the image to select the barcode area, then scan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { imageBoxSize = it }
                            .pointerInput(bitmap) {
                                detectDragGestures(
                                    onDragStart = { start ->
                                        selection = CropSelection(start.x, start.y, start.x, start.y)
                                        errorMessage = null
                                    },
                                    onDrag = { change, _ ->
                                        selection = selection?.copy(
                                            endX = change.position.x,
                                            endY = change.position.y
                                        )
                                    }
                                )
                            }
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Selected image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            selection?.normalized()?.let { rect ->
                                drawRect(
                                    color = Color.Yellow,
                                    topLeft = Offset(rect.left, rect.top),
                                    size = Size(rect.width, rect.height),
                                    style = Stroke(width = 4.dp.toPx())
                                )
                                drawRect(
                                    color = Color.Yellow.copy(alpha = 0.15f),
                                    topLeft = Offset(rect.left, rect.top),
                                    size = Size(rect.width, rect.height)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isAnalyzing) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Analyzing image...")
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                selectedBitmap = null
                                selection = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear")
                        }
                        OutlinedButton(
                            onClick = { selection = null },
                            modifier = Modifier.weight(1f),
                            enabled = selection != null
                        ) {
                            Text("Reset Box")
                        }
                        Button(
                            onClick = {
                                isAnalyzing = true
                                scope.launch {
                                    val imageToScan = cropBitmap(bitmap, selection, imageBoxSize) ?: bitmap
                                    analyzeImage(context, imageToScan) { barcodes ->
                                        isAnalyzing = false
                                        if (barcodes.isNotEmpty()) {
                                            val barcode = barcodes.first()
                                            onResultFound(
                                                barcode.rawValue ?: "",
                                                getFormatName(barcode.format),
                                                getValueTypeName(barcode.valueType)
                                            )
                                        } else {
                                            errorMessage = if (selection == null) {
                                                "No barcode found. Try dragging a box around the barcode."
                                            } else {
                                                "No barcode found in the selected area. Try a larger box."
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Scan")
                        }
                    }
                }

                errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Card(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Tap to select an image",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private data class CropSelection(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float
) {
    val left: Float get() = minOf(startX, endX)
    val top: Float get() = minOf(startY, endY)
    val right: Float get() = maxOf(startX, endX)
    val bottom: Float get() = maxOf(startY, endY)
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun normalized(): CropSelection = CropSelection(left, top, right, bottom)
}

private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        }
    } catch (e: Exception) {
        null
    }
}

private fun cropBitmap(
    bitmap: Bitmap,
    selection: CropSelection?,
    boxSize: IntSize
): Bitmap? {
    val rect = selection?.normalized() ?: return null
    if (rect.width < 20f || rect.height < 20f || boxSize.width <= 0 || boxSize.height <= 0) return null

    val bitmapAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
    val boxAspect = boxSize.width.toFloat() / boxSize.height.toFloat()

    val imageWidth: Float
    val imageHeight: Float
    val imageLeft: Float
    val imageTop: Float

    if (boxAspect > bitmapAspect) {
        imageHeight = boxSize.height.toFloat()
        imageWidth = imageHeight * bitmapAspect
        imageLeft = (boxSize.width - imageWidth) / 2f
        imageTop = 0f
    } else {
        imageWidth = boxSize.width.toFloat()
        imageHeight = imageWidth / bitmapAspect
        imageLeft = 0f
        imageTop = (boxSize.height - imageHeight) / 2f
    }

    val cropLeftInView = rect.left.coerceIn(imageLeft, imageLeft + imageWidth)
    val cropTopInView = rect.top.coerceIn(imageTop, imageTop + imageHeight)
    val cropRightInView = rect.right.coerceIn(imageLeft, imageLeft + imageWidth)
    val cropBottomInView = rect.bottom.coerceIn(imageTop, imageTop + imageHeight)

    if (cropRightInView - cropLeftInView < 20f || cropBottomInView - cropTopInView < 20f) return null

    val left = (((cropLeftInView - imageLeft) / imageWidth) * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
    val top = (((cropTopInView - imageTop) / imageHeight) * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
    val right = (((cropRightInView - imageLeft) / imageWidth) * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
    val bottom = (((cropBottomInView - imageTop) / imageHeight) * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)

    return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
}

private suspend fun analyzeImage(
    context: Context,
    bitmap: Bitmap,
    onResult: (List<Barcode>) -> Unit
) {
    val image = InputImage.fromBitmap(bitmap, 0)
    val scanner = BarcodeScanning.getClient()

    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            onResult(barcodes)
        }
        .addOnFailureListener {
            onResult(emptyList())
        }
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
