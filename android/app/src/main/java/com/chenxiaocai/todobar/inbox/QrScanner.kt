package com.chenxiaocai.todobar.inbox

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable fun QrScanner(onResult: (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val finished = remember { AtomicBoolean(false) }
    DisposableEffect(Unit) { onDispose { executor.shutdown(); ProcessCameraProvider.getInstance(context).get().unbindAll() } }
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { ctx ->
            val view = PreviewView(ctx)
            ProcessCameraProvider.getInstance(ctx).addListener({
                val provider = ProcessCameraProvider.getInstance(ctx).get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { image ->
                    try {
                        val plane = image.planes[0]
                        val buffer = plane.buffer
                        val bytes = ByteArray(image.width * image.height)
                        if (plane.rowStride == image.width && plane.pixelStride == 1) {
                            buffer.get(bytes, 0, bytes.size)
                        } else {
                            val row = ByteArray(plane.rowStride)
                            for (y in 0 until image.height) {
                                val count = minOf(plane.rowStride, buffer.remaining())
                                buffer.get(row, 0, count)
                                for (x in 0 until image.width) bytes[y * image.width + x] = row[x * plane.pixelStride]
                            }
                        }
                        val source = PlanarYUVLuminanceSource(bytes, image.width, image.height, 0, 0, image.width, image.height, false)
                        val value = runCatching { MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source))).text }.getOrNull()
                        if (value != null && finished.compareAndSet(false, true)) ContextCompat.getMainExecutor(ctx).execute { onResult(value) }
                    } finally { image.close() }
                }
                provider.unbindAll(); provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }, ContextCompat.getMainExecutor(ctx))
            view
        }, modifier = Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.BottomCenter).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("将 Mac 上的二维码放入画面"); Spacer(Modifier.height(8.dp)); Button(onClick = onCancel) { Text("取消") }
        }
    }
}
