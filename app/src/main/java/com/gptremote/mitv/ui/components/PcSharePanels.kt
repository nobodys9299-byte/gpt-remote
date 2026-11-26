package com.gptremote.mitv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gptremote.mitv.model.ConnectionStatus
import com.gptremote.mitv.model.RemoteUiState
import com.gptremote.mitv.model.TransportMode
import com.gptremote.mitv.ui.theme.ConnectedGreen
import com.gptremote.mitv.ui.theme.MiOrange
import com.gptremote.mitv.ui.theme.RemoteBorder
import com.gptremote.mitv.ui.theme.RemoteSurface
import com.gptremote.mitv.ui.theme.RemoteSurfaceElevated
import com.gptremote.mitv.ui.theme.TextPrimary
import com.gptremote.mitv.ui.theme.TextSecondary
import com.gptremote.mitv.ui.theme.WifiBlue
import com.gptremote.mitv.wifi.SharedPcFile

@Composable
fun PcScreenSharePanel(
    uiState: RemoteUiState,
    onStartPcMirror: () -> Unit,
    onStopPcMirror: () -> Unit,
    onTapMirroredScreen: (nx: Float, ny: Float) -> Unit,
    onStartPhoneCast: () -> Unit,
    onStopPhoneCast: () -> Unit,
    onOpenConnect: () -> Unit
) {
    val isWifiPc = (uiState.status as? ConnectionStatus.Connected)?.device?.transport == TransportMode.WIFI_PC

    DisposableEffect(Unit) {
        onDispose {
            onStopPcMirror()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!isWifiPc) {
            WifiPcRequiredBanner(onOpenConnect = onOpenConnect)
        }

        // 1. View & Touch-Control PC Screen on Phone
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(RemoteSurfaceElevated)
                .border(1.dp, RemoteBorder, RoundedCornerShape(20.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.DesktopWindows, contentDescription = null, tint = WifiBlue)
                    Column {
                        Text("PC Screen on Phone", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Tap anywhere on preview to click PC", color = TextSecondary, fontSize = 11.sp)
                    }
                }
                if (uiState.isMirroringPc) {
                    OutlinedButton(onClick = onStopPcMirror, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Rounded.StopCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop", fontSize = 12.sp)
                    }
                } else {
                    Button(
                        onClick = onStartPcMirror,
                        enabled = isWifiPc,
                        colors = ButtonDefaults.buttonColors(containerColor = MiOrange),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("View PC", fontSize = 12.sp)
                    }
                }
            }

            val bmp = uiState.pcScreenBitmap
            if (bmp != null) {
                val aspect = (bmp.width.toFloat() / bmp.height.coerceAtLeast(1)).coerceIn(0.6f, 2.5f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspect)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black)
                        .border(1.dp, ConnectedGreen.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        .pointerInput(bmp.width, bmp.height) {
                            detectTapGestures { offset ->
                                val nx = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                val ny = (offset.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                onTapMirroredScreen(nx, ny)
                            }
                        }
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Live PC Screen",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(RemoteSurface)
                        .border(1.dp, RemoteBorder, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (uiState.isMirroringPc) {
                            "Loading PC screen stream..."
                        } else {
                            "Tap 'View PC' to mirror your computer screen here"
                        },
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 2. Cast Phone Screen to PC Window
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(RemoteSurfaceElevated)
                .border(1.dp, RemoteBorder, RoundedCornerShape(20.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (uiState.isCastingPhoneScreen) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
                        contentDescription = null,
                        tint = if (uiState.isCastingPhoneScreen) ConnectedGreen else MiOrange
                    )
                    Column {
                        Text("Phone Screen to PC", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (uiState.isCastingPhoneScreen) "Casting live to GPT Remote PC" else "Mirror phone display on PC",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
                if (uiState.isCastingPhoneScreen) {
                    OutlinedButton(onClick = onStopPhoneCast, shape = RoundedCornerShape(12.dp)) {
                        Text("Stop Cast", fontSize = 12.sp, color = Color(0xFFF87171))
                    }
                } else {
                    FilledTonalButton(
                        onClick = onStartPhoneCast,
                        enabled = isWifiPc,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cast Screen", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun PcFileSharePanel(
    uiState: RemoteUiState,
    onRefreshFiles: () -> Unit,
    onPickFileToSend: () -> Unit,
    onDownloadFile: (SharedPcFile) -> Unit,
    onOpenConnect: () -> Unit
) {
    val isWifiPc = (uiState.status as? ConnectionStatus.Connected)?.device?.transport == TransportMode.WIFI_PC

    LaunchedEffect(isWifiPc) {
        if (isWifiPc) {
            onRefreshFiles()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!isWifiPc) {
            WifiPcRequiredBanner(onOpenConnect = onOpenConnect)
        }

        Button(
            onClick = onPickFileToSend,
            enabled = isWifiPc && !uiState.isTransferringFile,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MiOrange),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (uiState.isTransferringFile) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
            } else {
                Icon(Icons.Rounded.CloudUpload, contentDescription = null)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (uiState.isTransferringFile) "Transferring..." else "Send File from Phone to PC",
                fontWeight = FontWeight.SemiBold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("PC Shared Files", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Files in PC Downloads/GptRemote", color = TextSecondary, fontSize = 11.sp)
            }
            IconButton(onClick = onRefreshFiles, enabled = isWifiPc) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh PC Files", tint = WifiBlue)
            }
        }

        if (uiState.sharedPcFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(RemoteSurfaceElevated)
                    .border(1.dp, RemoteBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = if (isWifiPc) {
                        "No files in PC Downloads/GptRemote yet. Add files in GPT Remote PC or send from phone above."
                    } else {
                        "Connect via Wi-Fi PC to browse and exchange files."
                    },
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                uiState.sharedPcFiles.forEach { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(RemoteSurfaceElevated)
                            .border(1.dp, RemoteBorder, RoundedCornerShape(14.dp))
                            .clickable(enabled = !uiState.isTransferringFile) { onDownloadFile(file) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, contentDescription = null, tint = WifiBlue)
                            Column {
                                Text(
                                    text = file.name,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatFileSize(file.sizeBytes),
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Rounded.CloudDownload,
                            contentDescription = "Download ${file.name}",
                            tint = ConnectedGreen
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WifiPcRequiredBanner(onOpenConnect: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RemoteSurfaceElevated)
            .border(1.dp, WifiBlue.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Connect GPT Remote PC (Wi-Fi)", color = WifiBlue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = "Screen sharing and file transfer use the GPT Remote PC Windows app over Wi-Fi.",
            color = TextSecondary,
            fontSize = 12.sp
        )
        Button(
            onClick = onOpenConnect,
            colors = ButtonDefaults.buttonColors(containerColor = WifiBlue),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Connect Wi-Fi PC", color = Color(0xFF062033), fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024f * 1024f))
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}
