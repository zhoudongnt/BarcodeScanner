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
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.TextRecognition
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

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

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
            scale = 1f
            offset = Offset.Zero
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (scale > 1f) {
                                scale -= 0.5f
                                scale = scale.coerceIn(1f, 5f)
                            }
                        },
                        enabled = scale > 1f,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("${scale}x", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.width(16.dp))
                    IconButton(
                        onClick = {
                            if (scale < 5f) {
                                scale += 0.5f
                                scale = scale.coerceIn(1f, 5f)
                            }
                        },
                        enabled = scale < 5f,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Drag on image to select area, then tap Scan.",
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
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Selected image",
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                ),
                            contentScale = ContentScale.Fit,
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
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
                                    val imageToScan = cropBitmap(bitmap, selection, imageBoxSize, scale, offset) ?: bitmap
                                    analyzeOcr(context, imageToScan) { text, confidence ->
                                        isAnalyzing = false
                                        if (text.isNotBlank()) {
                                            val confidenceStr = confidence?.let {
                                                " (${(it * 100).toInt()}% confidence)"
                                            } ?: ""
                                            onResultFound(
                                                "$text$confidenceStr",
                                                "OCR",
                                                "Text"
                                            )
                                        } else {
                                            errorMessage = "No text found in the selected area."
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
    boxSize: IntSize,
    scale: Float,
    offset: Offset
): Bitmap? {
    val rect = selection?.normalized() ?: return null
    val bmp = computeImageBounds(bitmap, boxSize)

    val center = Offset(boxSize.width.toFloat()/2, boxSize.height.toFloat()/2)
    val invScale = 1/scale
    val invOffset = Offset(-offset.x, -offset.y)

    fun transformPoint(p: Offset): Offset {
        val t1 = p + invOffset
        val fromCenter = t1 - center
        val unscaled = fromCenter * invScale
        return unscaled + center
    }

    val leftTrans = transformPoint(Offset(rect.left, 0f)).x
    val rightTrans = transformPoint(Offset(rect.right, 0f)).x
    val topTrans = transformPoint(Offset(0f, rect.top)).y
    val bottomTrans = transformPoint(Offset(0f, rect.bottom)).y

    val cropLeft = minOf(leftTrans, rightTrans).coerceIn(bmp.left, bmp.right)
    val cropTop = minOf(topTrans, bottomTrans).coerceIn(bmp.top, bmp.bottom)
    val cropRight = maxOf(leftTrans, rightTrans).coerceIn(bmp.left, bmp.right)
    val cropBottom = maxOf(topTrans, bottomTrans).coerceIn(bmp.top, bmp.bottom)

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

private suspend fun analyzeOcr(
    context: Context,
    bitmap: Bitmap,
    onResult: (String, Float?) -> Unit
) {
    val options = TextRecognizerOptions.Builder().build()
    val recognizer = TextRecognition.getClient(options)

    val rotations = listOf(0, 90, 180, 270)
    for (rotation in rotations) {
        val rotated = if (rotation == 0) bitmap else rotateBitmap(bitmap, rotation)
        val image = InputImage.fromBitmap(rotated, 0)

        try {
            val result = recognizer.process(image).await()
            if (result.text.isNotBlank()) {
                val confidences = mutableListOf<Float>()
                for (block in result.textBlocks) {
                    block.lines.forEach { line ->
                        line.elements.forEach { element ->
                            element.confidence?.let { confidences.add(it) }
                        }
                    }
                }
                val avgConfidence = if (confidences.isNotEmpty()) {
                    confidences.average().toFloat()
                } else {
                    null
                }
                onResult(result.text, avgConfidence)
                return
            }
        } catch (_: Exception) {
            // Ignore errors
        }
    }

    onResult("", null)
}

private fun rotateBitmap(bmp: Bitmap, degrees: Int): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(degrees.toFloat())
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
}
