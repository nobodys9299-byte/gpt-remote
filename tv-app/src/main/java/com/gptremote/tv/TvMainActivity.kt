package com.gptremote.tv

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TvMainActivity : ComponentActivity() {

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.startForegroundService(this, Intent(this, TvCompanionService::class.java))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF090D16),
                    surface = Color(0xFF111A2E),
                    primary = Color(0xFF22D3EE),
                )
            ) {
                val uiState by TvCompanionState.uiState.collectAsState()
                TvDashboardScreen(
                    uiState = uiState,
                    onOpenFile = ::openReceivedFile,
                )
            }
        }
    }

    private fun openReceivedFile(item: TvReceivedFile) {
        val file = File(item.absolutePath)
        if (!file.exists()) {
            Toast.makeText(this, "File not found on TV storage", Toast.LENGTH_SHORT).show()
            return
        }
        val lower = file.name.lowercase(Locale.US)
        if (lower.endsWith(".apk") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                Toast.makeText(
                    this,
                    "Allow GPT Remote TV to install apps, then click the APK again",
                    Toast.LENGTH_LONG,
                ).show()
                runCatching {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:$packageName")
                        }
                    )
                }
                return
            }
        }

        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val ext = file.extension.lowercase(Locale.US)
        val mime = when {
            ext == "apk" -> "application/vnd.android.package-archive"
            ext.isNotBlank() -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
            else -> "*/*"
        }
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            startActivity(viewIntent)
        }.onFailure {
            val fallback = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching {
                startActivity(Intent.createChooser(fallback, "Open ${file.name}"))
            }.onFailure {
                Toast.makeText(this, "No TV app installed to open ${file.name}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
private fun TvDashboardScreen(
    uiState: TvCompanionUiState,
    onOpenFile: (TvReceivedFile) -> Unit,
) {
    var showQr by remember { mutableStateOf(true) }
    var castDismissedAt by remember { mutableLongStateOf(0L) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val isCastingLive = uiState.castFrame != null &&
        (nowMs - uiState.castLastAtMs) < 5000L &&
        uiState.castLastAtMs > castDismissedAt

    if (isCastingLive && uiState.castFrame != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            Image(
                bitmap = uiState.castFrame.asImageBitmap(),
                contentDescription = "Live Phone Screen Cast",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Surface(
                onClick = { castDismissedAt = uiState.castLastAtMs },
                color = Color(0xCC0F172A),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF22D3EE)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.Cast, contentDescription = null, tint = Color(0xFF22D3EE))
                    Text(
                        "Phone Cast Live • OK to minimize",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        return
    }

    val qrBitmap = remember(uiState.qrPayload, showQr) {
        if (showQr && uiState.qrPayload.isNotBlank()) generateTvQrBitmap(uiState.qrPayload, 440) else null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF090D16), Color(0xFF0F172A), Color(0xFF0B1120)),
                )
            )
            .padding(28.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Left panel: Identity, Wi-Fi IP, Pair Code, QR Code
            Column(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    color = Color(0xFF131D33),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF06B6D4), Color(0xFF2563EB)),
                                    )
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Tv,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "GPT Remote TV",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                uiState.deviceName,
                                color = Color(0xFF94A3B8),
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Surface(
                    color = Color(0xFF131D33),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Wifi,
                                contentDescription = null,
                                tint = Color(0xFF22D3EE),
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                if (uiState.localIps.isEmpty()) "Waiting for Wi-Fi IP..."
                                else "Wi-Fi IP: ${uiState.localIps.joinToString(", ")}",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                            )
                        }
                        Text(
                            "Pair Code: ${uiState.pairCode}  •  HTTP ${TvCompanionService.HTTP_PORT}",
                            color = Color(0xFF38BDF8),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        if (uiState.connectedPhone != null) {
                            Text(
                                "Last Phone: ${uiState.connectedPhone}",
                                color = Color(0xFF4ADE80),
                                fontSize = 13.sp,
                            )
                        }
                    }
                }

                Surface(
                    color = Color(0xFF131D33),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Button(
                            onClick = { showQr = !showQr },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1E293B),
                                contentColor = Color(0xFF22D3EE),
                            ),
                        ) {
                            Icon(Icons.Rounded.QrCode2, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (showQr) "Hide TV QR Code" else "Show TV QR Code")
                        }

                        if (qrBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .size(190.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White)
                                    .padding(10.dp),
                            ) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "Scan from GPT Remote Phone App",
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Text(
                                "Scan in Phone App -> Smart TV -> File Share / Cast",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                            )
                        } else {
                            Text(
                                "QR Hidden • Phone on same Wi-Fi auto-discovers this TV.",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }

            // Right panel: Transfer Progress + Speed + Received Files & APKs (D-Pad Click to Open / Install)
            Column(
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val tp = uiState.transferProgress
                if (tp != null) {
                    val pct = if (tp.totalBytes > 0L) {
                        (tp.bytesDone.toFloat() / tp.totalBytes.toFloat()).coerceIn(0f, 1f)
                    } else 1f
                    Surface(
                        color = Color(0xFF132238),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, Color(0xFF22D3EE).copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    if (tp.isComplete) "Received ${tp.fileName}" else "Receiving ${tp.fileName}",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "${TvCompanionState.formatBytes(tp.bytesPerSec)}/s • ${(pct * 100).toInt()}%",
                                    color = Color(0xFF22D3EE),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            LinearProgressIndicator(
                                progress = { pct },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(99.dp)),
                                color = Color(0xFF22D3EE),
                                trackColor = Color(0xFF1E293B),
                            )
                        }
                    }
                }

                Surface(
                    color = Color(0xFF131D33),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Received Files & APKs (${uiState.receivedFiles.size})",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Press OK / Click to Open or Install",
                                color = Color(0xFF38BDF8),
                                fontSize = 13.sp,
                            )
                        }

                        if (uiState.receivedFiles.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.FolderOpen,
                                        contentDescription = null,
                                        tint = Color(0xFF475569),
                                        modifier = Modifier.size(48.dp),
                                    )
                                    Text(
                                        "No files received yet",
                                        color = Color(0xFFCBD5E1),
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 16.sp,
                                    )
                                    Text(
                                        "Send APKs, movies, or photos from GPT Remote on your phone",
                                        color = Color(0xFF64748B),
                                        fontSize = 13.sp,
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(uiState.receivedFiles, key = { it.absolutePath }) { file ->
                                    TvFileRow(item = file, onClick = { onOpenFile(file) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvFileRow(
    item: TvReceivedFile,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val lower = item.name.lowercase(Locale.US)
    val icon = when {
        lower.endsWith(".apk") -> Icons.Rounded.Android
        lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".mov") -> Icons.Rounded.VideoFile
        lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") -> Icons.Rounded.Image
        else -> Icons.Rounded.Description
    }
    val dateStr = remember(item.timestampMs) {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(item.timestampMs))
    }

    Surface(
        onClick = onClick,
        color = if (focused) Color(0xFF1A2D4A) else Color(0xFF0E1627),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (focused) 2.dp else 1.dp,
            color = if (focused) Color(0xFF22D3EE) else Color(0xFF1E293B),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (lower.endsWith(".apk")) Color(0xFF4ADE80) else Color(0xFF22D3EE),
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${TvCompanionState.formatBytes(item.sizeBytes)} • $dateStr",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                )
            }
            Text(
                if (lower.endsWith(".apk")) "INSTALL" else "OPEN",
                color = Color(0xFF22D3EE),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        }
    }
}

private fun generateTvQrBitmap(payload: String, sizePx: Int): Bitmap? {
    return runCatching {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            val offset = y * sizePx
            for (x in 0 until sizePx) {
                pixels[offset + x] = if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE
            }
        }
        Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
    }.getOrNull()
}
