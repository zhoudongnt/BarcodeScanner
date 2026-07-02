package com.example.barcodescanner.ui.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.roundToInt

private enum class DragMode { NONE, CREATE, MOVE }

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
    var dragMode by remember { mutableStateOf(DragMode.NONE) }

    val currentSelection by rememberUpdatedState(selection)
    val currentDragMode by rememberUpdatedState(dragMode)
    val currentBitmap by rememberUpdatedState(selectedBitmap)

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
                    text = "Drag inside the box to move it, or drag outside to draw a new box.",
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
                            .pointerInput(currentBitmap) {
                                detectDragGestures(
                                    onDragStart = { start ->
                                        val sel = currentSelection?.normalized()
                                        if (sel != null &&
                                            start.x >= sel.left - 24f &&
                                            start.x <= sel.right + 24f &&
                                            start.y >= sel.top - 24f &&
                                            start.y <= sel.bottom + 24f
                                        ) {
                                            dragMode = DragMode.MOVE
                                        } else {
                                            dragMode = DragMode.CREATE
                                            selection = CropSelection(start.x, start.y, start.x, start.y)
                                        }
                                        errorMessage = null
                                    },
                                    onDrag = { change, dragAmount ->
                                        when (currentDragMode) {
                                            DragMode.MOVE -> {
                                                val sel = currentSelection ?: return@detectDragGestures
                                                selection = CropSelection(
                                                    startX = sel.startX + dragAmount.x,
                                                    startY = sel.startY + dragAmount.y,
                                                    endX = sel.endX + dragAmount.x,
                                                    endY = sel.endY + dragAmount.y
                                                )
                                            }
                                            DragMode.CREATE -> {
                                                selection = selection?.copy(
                                                    endX = change.position.x,
                                                    endY = change.position.y
                                                )
                                            }
                                            DragMode.NONE -> {}
                                        }
                                        change.consume()
                                    },
                                    onDragEnd = {
                                        dragMode = DragMode.NONE
                                    },
                                    onDragCancel = {
                                        dragMode = DragMode.NONE
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
                                val strokeW = 4.dp.toPx()
                                drawRect(
                                    color = Color.Cyan,
                                    topLeft = Offset(rect.left, rect.top),
                                    size = Size(rect.width, rect.height),
                                    style = Stroke(width = strokeW)
                                )
                                drawRect(
                                    color = Color.Cyan.copy(alpha = 0.12f),
                                    topLeft = Offset(rect.left, rect.top),
                                    size = Size(rect.width, rect.height)
                                )
                                val corner = 12.dp.toPx()
                                drawCircle(Color.Cyan, corner, Offset(rect.left, rect.top))
                                drawCircle(Color.Cyan, corner, Offset(rect.right, rect.top))
                                drawCircle(Color.Cyan, corner, Offset(rect.left, rect.bottom))
                                drawCircle(Color.Cyan, corner, Offset(rect.right, rect.bottom))
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

private fun computeImageBounds(
    bitmap: Bitmap,
    boxSize: IntSize
): ImageBounds {
    val bitmapAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
    val boxAspect = boxSize.width.toFloat() / boxSize.height.toFloat()

    return if (boxAspect > bitmapAspect) {
        val h = boxSize.height.toFloat()
        val w = h * bitmapAspect
        ImageBounds(
            left = (boxSize.width - w) / 2f,
            top = 0f,
            right = (boxSize.width + w) / 2f,
            bottom = h
        )
    } else {
        val w = boxSize.width.toFloat()
        val h = w / bitmapAspect
        ImageBounds(
            left = 0f,
            top = (boxSize.height - h) / 2f,
            right = w,
            bottom = (boxSize.height + h) / 2f
        )
    }
}

private data class ImageBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

private fun cropBitmap(
    bitmap: Bitmap,
    selection: CropSelection?,
    boxSize: IntSize
): Bitmap? {
    val rect = selection?.normalized() ?: return null
    val bmp = computeImageBounds(bitmap, boxSize)
    if (rect.width < 20f || rect.height < 20f) return null

    val cropLeft = rect.left.coerceIn(bmp.left, bmp.right)
    val cropTop = rect.top.coerceIn(bmp.top, bmp.bottom)
    val cropRight = rect.right.coerceIn(bmp.left, bmp.right)
    val cropBottom = rect.bottom.coerceIn(bmp.top, bmp.bottom)

    if (cropRight - cropLeft < 20f || cropBottom - cropTop < 20f) return null

    val iw = bmp.right - bmp.left
    val ih = bmp.bottom - bmp.top
    if (iw <= 0f || ih <= 0f) return null

    val px = (((cropLeft - bmp.left) / iw) * bitmap.width).roundToInt().coerceIn(0, bitmap.width)
    val py = (((cropTop - bmp.top) / ih) * bitmap.height).roundToInt().coerceIn(0, bitmap.height)
    val pw = (((cropRight - bmp.left) / iw) * bitmap.width).roundToInt().coerceIn(0, bitmap.width - px)
    val ph = (((cropBottom - bmp.top) / ih) * bitmap.height).roundToInt().coerceIn(0, bitmap.height - py)

    if (pw < 4 || ph < 4) return null

    return Bitmap.createBitmap(bitmap, px, py, pw, ph)
}

private suspend fun analyzeImage(
    context: Context,
    bitmap: Bitmap,
    onResult: (List<Barcode>) -> Unit
) {
    val options = com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_ALL_FORMATS,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_CODE_39,
            Barcode.FORMAT_ITF,
            Barcode.FORMAT_CODABAR
        )
        .build()

    val scanner = BarcodeScanning.getClient(options)

    val rotations = listOf(0, 90, 180, 270)
    for (rotation in rotations) {
        val rotated = if (rotation == 0) bitmap else rotateBitmap(bitmap, rotation)
        val image = InputImage.fromBitmap(rotated, 0)

        try {
            val barcodes = scanner.process(image).await()
            if (barcodes.isNotEmpty()) {
                onResult(barcodes)
                return
            }
        } catch (_: Exception) {
            // Ignore errors
        }
    }

    onResult(emptyList())
}

private fun rotateBitmap(bmp: Bitmap, degrees: Int): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(degrees.toFloat())
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
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
