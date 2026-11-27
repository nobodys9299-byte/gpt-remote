package com.gptremote.mitv.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.util.Size
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.gptremote.mitv.ui.theme.MiOrange
import com.gptremote.mitv.ui.theme.RemoteBg
import com.gptremote.mitv.ui.theme.RemoteSurfaceElevated
import com.gptremote.mitv.ui.theme.TextPrimary
import com.gptremote.mitv.ui.theme.TextSecondary
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

fun generateQrBitmap(content: String, sizePx: Int = 520): Bitmap? {
    return runCatching {
        val hints = mapOf(
            com.google.zxing.EncodeHintType.MARGIN to 2
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            val offset = y * sizePx
            for (x in 0 until sizePx) {
                pixels[offset + x] = if (matrix.get(x, y)) 0xFF0B0E14.toInt() else AndroidColor.WHITE
            }
        }
        bmp.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
        bmp
    }.getOrNull()
}

@Composable
fun QrScannerDialog(
    title: String = "Scan QR Code",
    subtitle: String = "Point your camera at the QR code shown on PC or another phone",
    onScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val scannedOnce = remember { AtomicBoolean(false) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(torchEnabled, boundCamera) {
        runCatching { boundCamera?.cameraControl?.enableTorch(torchEnabled) }
    }

    DisposableEffect(hasCameraPermission) {
        var provider: ProcessCameraProvider? = null
        if (hasCameraPermission) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                runCatching {
                    val camProvider = future.get()
                    provider = camProvider
                    val preview = Preview.Builder().build().apply {
                        setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val reader = QRCodeReader()
                    val hints = mapOf(
                        DecodeHintType.TRY_HARDER to true,
                        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                        DecodeHintType.ALSO_INVERTED to true
                    )

                    @Suppress("DEPRECATION")
                    val analysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(analyzerExecutor) { image ->
                        try {
                            if (scannedOnce.get()) return@setAnalyzer
                            val plane = image.planes[0]
                            val w = image.width
                            val h = image.height
                            val rowStride = plane.rowStride
                            val pixelStride = plane.pixelStride
                            val buf = plane.buffer
                            val packed = ByteArray(w * h)
                            if (pixelStride == 1 && rowStride == w && buf.remaining() >= w * h) {
                                buf.get(packed, 0, w * h)
                            } else {
                                val rowBuf = ByteArray(rowStride)
                                for (row in 0 until h) {
                                    val copyLen = minOf(rowStride, buf.remaining())
                                    if (copyLen <= 0) break
                                    buf.get(rowBuf, 0, copyLen)
                                    val outOff = row * w
                                    if (pixelStride == 1) {
                                        System.arraycopy(rowBuf, 0, packed, outOff, minOf(w, copyLen))
                                    } else {
                                        var col = 0
                                        var idx = 0
                                        while (col < w && idx < copyLen) {
                                            packed[outOff + col] = rowBuf[idx]
                                            col++
                                            idx += pixelStride
                                        }
                                    }
                                }
                            }

                            val fullSrc = PlanarYUVLuminanceSource(packed, w, h, 0, 0, w, h, false)
                            val cropW = (w * 0.72f).toInt().coerceAtLeast(160).coerceAtMost(w)
                            val cropH = (h * 0.72f).toInt().coerceAtLeast(160).coerceAtMost(h)
                            val cropLeft = (w - cropW) / 2
                            val cropTop = (h - cropH) / 2
                            val centerSrc = PlanarYUVLuminanceSource(
                                packed, w, h, cropLeft, cropTop, cropW, cropH, false
                            )

                            fun tryDecode(source: LuminanceSource): String? {
                                val direct = runCatching {
                                    reader.decode(BinaryBitmap(HybridBinarizer(source)), hints)
                                }.getOrNull()?.text
                                if (!direct.isNullOrBlank()) return direct
                                reader.reset()
                                val global = runCatching {
                                    reader.decode(BinaryBitmap(GlobalHistogramBinarizer(source)), hints)
                                }.getOrNull()?.text
                                if (!global.isNullOrBlank()) return global
                                reader.reset()
                                return runCatching {
                                    reader.decode(BinaryBitmap(HybridBinarizer(source.invert())), hints)
                                }.getOrNull()?.text
                            }

                            val text = tryDecode(centerSrc)
                                ?: tryDecode(fullSrc)
                                ?: runCatching {
                                    // Manual 90-degree Y-plane rotation avoids ZXing's UnsupportedOperationException
                                    val rotW = cropH
                                    val rotH = cropW
                                    val rotated = ByteArray(rotW * rotH)
                                    for (row in 0 until cropH) {
                                        val srcRow = (cropTop + row) * w + cropLeft
                                        val dstCol = cropH - 1 - row
                                        for (col in 0 until cropW) {
                                            rotated[col * rotW + dstCol] = packed[srcRow + col]
                                        }
                                    }
                                    val rotSrc = PlanarYUVLuminanceSource(
                                        rotated, rotW, rotH, 0, 0, rotW, rotH, false
                                    )
                                    tryDecode(rotSrc)
                                }.getOrNull()

                            if (!text.isNullOrBlank() && scannedOnce.compareAndSet(false, true)) {
                                ContextCompat.getMainExecutor(context).execute {
                                    onScanned(text)
                                }
                            }
                        } catch (_: Throwable) {
                            // Never crash the analyzer thread on malformed/transient YUV frames
                        } finally {
                            runCatching { reader.reset() }
                            runCatching { image.close() }
                        }
                    }

                    camProvider.unbindAll()
                    boundCamera = camProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                }
            }, ContextCompat.getMainExecutor(context))
        }
        onDispose {
            boundCamera = null
            runCatching { provider?.unbindAll() }
            analyzerExecutor.shutdownNow()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(RemoteBg)
                .statusBarsPadding()
        ) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QrCodeScanner,
                        contentDescription = null,
                        tint = MiOrange,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = "Camera Permission Needed",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Allow Camera access to scan QR codes from your PC or another phone.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { permLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = MiOrange)
                    ) {
                        Text("Allow Camera")
                    }
                }
            }

            // Top bar overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hasCameraPermission) {
                        FilledTonalIconButton(
                            onClick = { torchEnabled = !torchEnabled },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (torchEnabled) MiOrange else RemoteSurfaceElevated,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = if (torchEnabled) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                                contentDescription = "Toggle Flashlight"
                            )
                        }
                    }
                    FilledTonalIconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = RemoteSurfaceElevated,
                            contentColor = TextPrimary
                        )
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close Scanner")
                    }
                }
            }

            // Center targeting frame
            if (hasCameraPermission) {
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(24.dp))
                        .border(3.dp, MiOrange, RoundedCornerShape(24.dp))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Align QR code inside the frame",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(1.dp))
            }
        }
    }
}
