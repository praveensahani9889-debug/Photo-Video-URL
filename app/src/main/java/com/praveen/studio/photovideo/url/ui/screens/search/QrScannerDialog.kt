package com.praveen.studio.photovideo.url.ui.screens.search

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.praveen.studio.photovideo.url.ui.theme.CyanPrimary
import java.util.concurrent.Executors

/**
 * Full screen QR scanner: asks for the camera permission, shows the camera with a scan frame, and returns the text of
 * the first QR code that is read. Uses CameraX (camera) and ML Kit barcode scanning (reading) - both from Google.
 * The camera picture stays on the phone: it is only used to read the code and is never saved or uploaded.
 */
@Composable
fun QrScannerDialog(onResult: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current

    fun cameraGranted() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    var hasPermission by remember { mutableStateOf(cameraGranted()) }
    var asked by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        asked = true
    }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        hasPermission = cameraGranted()
    }

    // the permission question appears as soon as the scanner opens
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("qr_scanner"),
            color = Color.Black
        ) {
            if (hasPermission) {
                ScannerContent(onResult = onResult, onClose = onDismiss)
            } else {
                CameraPermissionContent(
                    deniedBefore = asked,
                    onAllow = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onOpenSettings = {
                        try {
                            settingsLauncher.launch(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            )
                        } catch (_: Exception) {
                        }
                    },
                    onClose = onDismiss
                )
            }
        }
    }
}

@Composable
private fun CameraPermissionContent(
    deniedBefore: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.QrCodeScanner,
            contentDescription = null,
            tint = CyanPrimary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "Camera access needed",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "To scan a QR code the app needs to use the camera. The picture is only used on your phone to read " +
                "the code. It is never saved or uploaded.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.75f),
            textAlign = TextAlign.Center
        )
        if (deniedBefore) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "The permission is off. Turn on \"Camera\" in the app settings.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFFB4AB),
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = if (deniedBefore) onOpenSettings else onAllow,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("qr_allow_camera")
        ) {
            Text(if (deniedBefore) "Open app settings" else "Allow camera")
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onClose) { Text("Cancel", color = Color.White) }
    }
}

@Composable
private fun ScannerContent(onResult: (String) -> Unit, onClose: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val latestOnResult by rememberUpdatedState(onResult)
    var torchOn by remember { mutableStateOf(false) }
    var hasTorch by remember { mutableStateOf(false) }
    var cameraFailed by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!cameraFailed) {
            CameraPreview(
                torchOn = torchOn,
                onTorchAvailable = { hasTorch = it },
                onError = { cameraFailed = true },
                onCode = { text ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    latestOnResult(text)
                }
            )
            ScannerOverlay()
        } else {
            Text(
                text = "The camera could not be started.",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // top bar
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close scanner", tint = Color.White)
            }
            Text(
                text = "Scan QR code",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
        }

        // bottom: hint and torch
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Point the camera at a QR code",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
            if (hasTorch) {
                Spacer(modifier = Modifier.height(18.dp))
                IconButton(
                    onClick = { torchOn = !torchOn },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(if (torchOn) CyanPrimary else Color.White.copy(alpha = 0.18f))
                        .testTag("qr_torch")
                ) {
                    Icon(
                        imageVector = if (torchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                        contentDescription = if (torchOn) "Turn the light off" else "Turn the light on",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

/** Dark layer with a clear rounded window, accent corners and a moving scan line. */
@Composable
private fun ScannerOverlay() {
    val transition = rememberInfiniteTransition(label = "scan line")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "scan line position"
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        val frame = 260.dp.toPx()
        val left = (size.width - frame) / 2f
        val top = (size.height - frame) / 2f - 40.dp.toPx()
        val radius = 24.dp.toPx()
        val arm = 40.dp.toPx()
        val strokeWidth = 4.dp.toPx()
        val accent = CyanPrimary

        drawRect(Color.Black.copy(alpha = 0.62f))
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(frame, frame),
            cornerRadius = CornerRadius(radius),
            blendMode = BlendMode.Clear
        )

        // accent corners: a quarter circle and two short straight parts for each corner
        val arcSize = Size(radius * 2, radius * 2)
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        val right = left + frame
        val bottom = top + frame

        // top left
        drawArc(accent, 180f, 90f, false, Offset(left, top), arcSize, style = stroke)
        drawLine(accent, Offset(left, top + radius), Offset(left, top + arm), strokeWidth, StrokeCap.Round)
        drawLine(accent, Offset(left + radius, top), Offset(left + arm, top), strokeWidth, StrokeCap.Round)
        // top right
        drawArc(accent, 270f, 90f, false, Offset(right - radius * 2, top), arcSize, style = stroke)
        drawLine(accent, Offset(right - radius, top), Offset(right - arm, top), strokeWidth, StrokeCap.Round)
        drawLine(accent, Offset(right, top + radius), Offset(right, top + arm), strokeWidth, StrokeCap.Round)
        // bottom right
        drawArc(accent, 0f, 90f, false, Offset(right - radius * 2, bottom - radius * 2), arcSize, style = stroke)
        drawLine(accent, Offset(right, bottom - radius), Offset(right, bottom - arm), strokeWidth, StrokeCap.Round)
        drawLine(accent, Offset(right - radius, bottom), Offset(right - arm, bottom), strokeWidth, StrokeCap.Round)
        // bottom left
        drawArc(accent, 90f, 90f, false, Offset(left, bottom - radius * 2), arcSize, style = stroke)
        drawLine(accent, Offset(left + radius, bottom), Offset(left + arm, bottom), strokeWidth, StrokeCap.Round)
        drawLine(accent, Offset(left, bottom - radius), Offset(left, bottom - arm), strokeWidth, StrokeCap.Round)

        // moving scan line
        val lineY = top + 14.dp.toPx() + (frame - 28.dp.toPx()) * progress
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, accent, Color.Transparent),
                startX = left,
                endX = right
            ),
            start = Offset(left + 16.dp.toPx(), lineY),
            end = Offset(right - 16.dp.toPx(), lineY),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun CameraPreview(
    torchOn: Boolean,
    onTorchAvailable: (Boolean) -> Unit,
    onError: () -> Unit,
    onCode: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnCode by rememberUpdatedState(onCode)
    val latestOnTorch by rememberUpdatedState(onTorchAvailable)
    val latestOnError by rememberUpdatedState(onError)

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var camera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val analyzer = QrAnalyzer { text -> latestOnCode(text) }
        val providerFuture = ProcessCameraProvider.getInstance(context)

        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor, analyzer)

                provider.unbindAll()
                val bound = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
                camera = bound
                latestOnTorch(bound.cameraInfo.hasFlashUnit())
            } catch (_: Exception) {
                latestOnError()
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            try {
                providerFuture.get().unbindAll()
            } catch (_: Exception) {
            }
            executor.shutdown()
            analyzer.close()
        }
    }

    LaunchedEffect(torchOn, camera) {
        try {
            camera?.cameraControl?.enableTorch(torchOn)
        } catch (_: Exception) {
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

/** Reads QR codes from the camera pictures (ML Kit). Reports the first code only once. */
@Suppress("UnsafeOptInUsageError")
private class QrAnalyzer(private val onFound: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    )

    @Volatile
    private var finished = false

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || finished) {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val text = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                if (text != null && !finished) {
                    finished = true
                    onFound(text)
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    fun close() {
        try {
            scanner.close()
        } catch (_: Exception) {
        }
    }
}
