package com.plyr.ui.components

import android.Manifest
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.PlanarYUVLuminanceSource
import java.util.concurrent.Executors

@Composable
fun QrScannerDialog(onDismiss: () -> Unit, onQrScanned: (String) -> Unit) {
    val context = LocalContext.current
    var cameraPermissionGranted by remember { mutableStateOf(false) }
    var permissionRequested by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraError by remember { mutableStateOf<String?>(null) }

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
                if (!cameraPermissionGranted) {
                    // No mostrar nada, solo esperar a que el usuario responda la notificación
                } else {
                    var previewView: PreviewView?
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
                                previewView = PreviewView(ctx)
                                previewView.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    try {
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                        }
                                        val imageAnalysis = ImageAnalysis.Builder()
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build()
                                        imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                            val result = scanQrFromImageProxy(imageProxy)
                                            if (result != null) {
                                                onQrScanned(result)
                                                onDismiss()
                                                imageProxy.close()
                                            } else {
                                                imageProxy.close()
                                            }
                                        }
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            imageAnalysis
                                        )
                                    } catch (e: Exception) {
                                        cameraError = "Error iniciando la cámara: ${e.message}"
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                cameraError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                //Spacer(Modifier.height(16.dp))
                //Button(onClick = onDismiss) { Text("Cerrar") }
            }
        }
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
