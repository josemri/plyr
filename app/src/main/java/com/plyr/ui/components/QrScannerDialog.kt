package com.plyr.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.PlanarYUVLuminanceSource
import com.plyr.model.ScanResult
import com.plyr.utils.Translations
import com.plyr.utils.UrlParser
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Composable
fun QrScannerDialog(onDismiss: () -> Unit, onQrScanned: (ScanResult?) -> Unit) {
    val context = LocalContext.current
    var cameraPermissionGranted by remember { mutableStateOf(false) }
    var permissionRequested by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraError by remember { mutableStateOf<String?>(null) }
    val scannerExecutor = remember { Executors.newSingleThreadExecutor() }
    val cameraProviderRef = remember { AtomicReference<ProcessCameraProvider?>(null) }
    val scanHandled = remember { AtomicBoolean(false) }

    DisposableEffect(lifecycleOwner, context) {
        onDispose {
            // B11: teardown real — desligar la cámara y cerrar el hilo del analizador
            cameraProviderRef.get()?.unbindAll()
            scannerExecutor.shutdown()
        }
    }

    // Solicitar permiso de cámara al abrir el escáner
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraPermissionGranted = granted
        permissionRequested = true
    }
    LaunchedEffect(Unit) {
        val permission = Manifest.permission.CAMERA
        cameraPermissionGranted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (!cameraPermissionGranted && !permissionRequested) {
            launcher.launch(permission)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.padding(24.dp)) {
            Column {
                //Text("Escanea un código QR", style = MaterialTheme.typography.titleMedium)
                //Spacer(Modifier.height(16.dp))
                QrScannerBody(
                    cameraPermissionGranted = cameraPermissionGranted,
                    permissionRequested = permissionRequested,
                    onRetry = { launcher.launch(Manifest.permission.CAMERA) },
                    onDismiss = onDismiss,
                    lifecycleOwner = lifecycleOwner,
                    scannerExecutor = scannerExecutor,
                    cameraProviderRef = cameraProviderRef,
                    scanHandled = scanHandled,
                    onCameraError = { cameraError = it },
                    onQrScanned = onQrScanned,
                )
                cameraError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                //Spacer(Modifier.height(16.dp))
                //Button(onClick = onDismiss) { Text("Cerrar") }
            }
        }
    }
}

@Composable
private fun QrScannerBody(
    cameraPermissionGranted: Boolean,
    permissionRequested: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    lifecycleOwner: LifecycleOwner,
    scannerExecutor: ExecutorService,
    cameraProviderRef: AtomicReference<ProcessCameraProvider?>,
    scanHandled: AtomicBoolean,
    onCameraError: (String) -> Unit,
    onQrScanned: (ScanResult?) -> Unit,
) {
    if (!cameraPermissionGranted) {
        if (permissionRequested) {
            CameraPermissionDenied(onRetry, onDismiss)
        }
    } else {
        CameraPreviewBox(
            lifecycleOwner = lifecycleOwner,
            scannerExecutor = scannerExecutor,
            session = QrScannerSession(
                cameraProviderRef = cameraProviderRef,
                scanHandled = scanHandled,
                onCameraError = onCameraError,
                onQrScanned = onQrScanned,
                onDismiss = onDismiss,
            ),
        )
    }
}

private data class QrScannerSession(
    val cameraProviderRef: AtomicReference<ProcessCameraProvider?>,
    val scanHandled: AtomicBoolean,
    val onCameraError: (String) -> Unit,
    val onQrScanned: (ScanResult?) -> Unit,
    val onDismiss: () -> Unit,
)

// El permiso se rechazó: mostrar mensaje y botones en vez
// de un modal mudo (B47)
@Composable
private fun CameraPermissionDenied(onRetry: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            Translations.get(context, "permission_denied"),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text(Translations.get(context, "retry"))
        }
        TextButton(onClick = onDismiss) {
            Text(Translations.get(context, "close"))
        }
    }
}

@Composable
private fun CameraPreviewBox(
    lifecycleOwner: LifecycleOwner,
    scannerExecutor: ExecutorService,
    session: QrScannerSession,
) {
    Box(
        modifier = Modifier
            .size(300.dp)
            .aspectRatio(1f)
            .graphicsLayer {
                clip = true
                shape = RoundedCornerShape(24.dp)
            }
    ) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                previewView.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                bindCameraUseCases(ctx, previewView, lifecycleOwner, scannerExecutor, session)
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun bindCameraUseCases(
    ctx: Context,
    previewView: PreviewView,
    lifecycleOwner: LifecycleOwner,
    scannerExecutor: ExecutorService,
    session: QrScannerSession,
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
    cameraProviderFuture.addListener({
        try {
            val cameraProvider = cameraProviderFuture.get()
            session.cameraProviderRef.set(cameraProvider)
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            imageAnalysis.setAnalyzer(scannerExecutor) { imageProxy ->
                handleQrFrame(imageProxy, ctx, session)
            }
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )
        } catch (e: Exception) {
            session.onCameraError("Error iniciando la cámara: ${e.message}")
        }
    }, ContextCompat.getMainExecutor(ctx))
}

private fun handleQrFrame(
    imageProxy: ImageProxy,
    context: Context,
    session: QrScannerSession,
) {
    val qrText = scanQrFromImageProxy(imageProxy)
    if (qrText != null && session.scanHandled.compareAndSet(false, true)) {
        val result = UrlParser.parseScanText(qrText)
        ContextCompat.getMainExecutor(context).execute {
            session.onQrScanned(result)
            session.onDismiss()
        }
        imageProxy.close()
    } else {
        imageProxy.close()
    }
}

fun scanQrFromImageProxy(imageProxy: ImageProxy): String? {
    return try {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val width = imageProxy.width
        val height = imageProxy.height
        val source = PlanarYUVLuminanceSource(
            bytes, width, height, 0, 0, width, height, false
        )
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val result = MultiFormatReader().decode(bitmap)
        result.text
    } catch (_: Exception) {
        null
    }
}
