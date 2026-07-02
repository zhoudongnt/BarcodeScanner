package com.example.barcodescanner.ui.screen

import android.Manifest
import android.content.Context
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.example.barcodescanner.ui.viewmodel.ScanViewModel
import com.example.barcodescanner.ui.viewmodel.ScanState
import com.example.barcodescanner.utils.OcrAnalyzer
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    viewModel: ScanViewModel,
    onBackClick: () -> Unit,
    onResultFound: (String, String, String) -> Unit
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        viewModel.setScanning()
    }

    LaunchedEffect(viewModel.scanState.value) {
        if (viewModel.scanState.value is ScanState.Success) {
            val result = (viewModel.scanState.value as ScanState.Success).result
            onResultFound(result.text, "OCR", "Text")
            viewModel.resetScan()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            cameraPermissionState.status.isGranted -> {
                CameraPreviewWithOcr(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )

                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.TopStart)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                val torchEnabled by viewModel.torchEnabled.collectAsState()
                IconButton(
                    onClick = { viewModel.toggleTorch() },
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Toggle flash",
                        tint = Color.White
                    )
                }
            }
            cameraPermissionState.status.shouldShowRationale -> {
                PermissionRationale(
                    onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
                )
            }
            else -> {
                PermissionRequest(
                    onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
                )
            }
        }
    }
}

@Composable
private fun CameraPreviewWithOcr(
    viewModel: ScanViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val torchEnabled by viewModel.torchEnabled.collectAsState()
    val detectedText by viewModel.lastDetectedText.collectAsState()

    val density = LocalDensity.current
    val boxWidth = 300.dp
    val boxHeight = 120.dp
    val boxWidthPx = with(density) { boxWidth.toPx() }
    val boxHeightPx = with(density) { boxHeight.toPx() }

    var boxOffset by remember { mutableStateOf(Offset.Zero) }
    var parentSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    val analyzer = remember { OcrAnalyzer(viewModel) }

    Box(modifier = modifier.onSizeChanged { parentSize = it }) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { previewView ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor, analyzer)
                        }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                        camera.cameraControl.enableTorch(torchEnabled)
                    } catch (e: Exception) {
                        viewModel.setError("Camera initialization failed")
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        )

        if (parentSize.width > 0 && parentSize.height > 0) {
            LaunchedEffect(parentSize) {
                if (boxOffset == Offset.Zero) {
                    boxOffset = Offset(
                        (parentSize.width - boxWidthPx) / 2f,
                        (parentSize.height - boxHeightPx) / 2f
                    )
                }
            }

            DisposableEffect(boxOffset, parentSize) {
                viewModel.selectionRect = android.graphics.Rect(
                    boxOffset.x.toInt(),
                    boxOffset.y.toInt(),
                    (boxOffset.x + boxWidthPx).toInt(),
                    (boxOffset.y + boxHeightPx).toInt()
                )
                onDispose { }
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    color = Color.Cyan.copy(alpha = 0.15f),
                    topLeft = Offset(boxOffset.x, boxOffset.y),
                    size = Size(boxWidthPx, boxHeightPx)
                )
                drawRect(
                    color = Color.Cyan,
                    topLeft = Offset(boxOffset.x, boxOffset.y),
                    size = Size(boxWidthPx, boxHeightPx),
                    style = Stroke(width = 3.dp.toPx())
                )
                val cornerSize = 16.dp.toPx()
                val strokeW = 4.dp.toPx()
                val left = boxOffset.x
                val top = boxOffset.y
                val right = boxOffset.x + boxWidthPx
                val bottom = boxOffset.y + boxHeightPx
                // 左上
                drawLine(Color.Cyan, Offset(left, top), Offset(left + cornerSize, top), strokeWidth = strokeW)
                drawLine(Color.Cyan, Offset(left, top), Offset(left, top + cornerSize), strokeWidth = strokeW)
                // 右上
                drawLine(Color.Cyan, Offset(right, top), Offset(right - cornerSize, top), strokeWidth = strokeW)
                drawLine(Color.Cyan, Offset(right, top), Offset(right, top + cornerSize), strokeWidth = strokeW)
                // 左下
                drawLine(Color.Cyan, Offset(left, bottom), Offset(left + cornerSize, bottom), strokeWidth = strokeW)
                drawLine(Color.Cyan, Offset(left, bottom), Offset(left, bottom - cornerSize), strokeWidth = strokeW)
                // 右下
                drawLine(Color.Cyan, Offset(right, bottom), Offset(right - cornerSize, bottom), strokeWidth = strokeW)
                drawLine(Color.Cyan, Offset(right, bottom), Offset(right, bottom - cornerSize), strokeWidth = strokeW)
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(boxOffset.x.toInt(), boxOffset.y.toInt()) }
                    .size(boxWidth, boxHeight)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            val newX = (boxOffset.x + dragAmount.x).coerceIn(0f, parentSize.width - boxWidthPx)
                            val newY = (boxOffset.y + dragAmount.y).coerceIn(0f, parentSize.height - boxHeightPx)
                            boxOffset = Offset(newX, newY)
                            change.consume()
                        }
                    }
            )

            if (detectedText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 120.dp, start = 24.dp, end = 24.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = detectedText,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            Text(
                text = "Align text inside the box",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun PermissionRationale(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Camera Permission Required",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "This app needs camera access to scan text. Please grant the permission.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequestPermission) {
            Text("Grant Permission")
        }
    }
}

@Composable
private fun PermissionRequest(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Camera Permission Needed",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Please allow camera access to scan text.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequestPermission) {
            Text("Request Permission")
        }
    }
}
