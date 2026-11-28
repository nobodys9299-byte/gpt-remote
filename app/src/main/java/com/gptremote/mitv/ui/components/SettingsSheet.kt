package com.gptremote.mitv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.gptremote.mitv.haptic.HapticStrength
import com.gptremote.mitv.ui.theme.AppAccentTheme
import com.gptremote.mitv.ui.theme.ConnectedGreen
import com.gptremote.mitv.ui.theme.LocalAccentSoft
import com.gptremote.mitv.ui.theme.MiOrange
import com.gptremote.mitv.ui.theme.RemoteBorder
import com.gptremote.mitv.ui.theme.RemoteSurface
import com.gptremote.mitv.ui.theme.RemoteSurfaceElevated
import com.gptremote.mitv.ui.theme.TextPrimary
import com.gptremote.mitv.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    hapticStrength: HapticStrength,
    soundEnabled: Boolean,
    accentTheme: AppAccentTheme,
    onSelectHapticStrength: (HapticStrength) -> Unit,
    onToggleSound: (Boolean) -> Unit,
    onSelectAccentTheme: (AppAccentTheme) -> Unit,
    onScanQrCode: (() -> Unit)? = null,
    phoneQrPayload: String? = null,
    onRequestHostQr: (() -> Unit)? = null,
    googleHomeSignedIn: Boolean = false,
    googleHomeStatus: String = "Not connected",
    googleHomeAccount: String? = null,
    googleHomeDevicesCount: Int = 0,
    onSignInGoogleHome: (() -> Unit)? = null,
    onRefreshGoogleHome: (() -> Unit)? = null,
    onDisconnectGoogleHome: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var showMyQrDialog by remember { mutableStateOf(false) }

    if (showMyQrDialog) {
        Dialog(onDismissRequest = { showMyQrDialog = false }) {
            Surface(
                color = RemoteSurfaceElevated,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, RemoteBorder)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("My Phone QR Code", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showMyQrDialog = false }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = TextPrimary)
                        }
                    }
                    val bmp = remember(phoneQrPayload) {
                        phoneQrPayload?.takeIf { it.isNotBlank() }?.let { generateQrBitmap(it, 440) }
                    }
                    if (bmp != null) {
                        Box(
                            modifier = Modifier
                                .size(210.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .padding(10.dp)
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Phone Share QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Text(
                            text = "Scan from another GPT Remote phone to connect & send files",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    } else {
                        Text(
                            text = "Connect to Wi-Fi or turn on Hotspot so this phone has a local IP for QR pairing.",
                            color = Color(0xFFFBBF24),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = RemoteSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Settings",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            if (onScanQrCode != null || onRequestHostQr != null || phoneQrPayload != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (onScanQrCode != null) {
                        Button(
                            onClick = {
                                onDismiss()
                                onScanQrCode()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MiOrange),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan QR Code", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    FilledTonalButton(
                        onClick = {
                            onRequestHostQr?.invoke()
                            showMyQrDialog = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Show QR Code", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // Vibration Haptic Strength
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(RemoteSurfaceElevated)
                    .border(1.dp, RemoteBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Vibration Haptic Feedback",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Stronger pulse on D-Pad slide, volume rockers, clicks, and buttons",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HapticStrength.entries.forEach { level ->
                        val chipSelected = hapticStrength == level
                        FilterChip(
                            selected = chipSelected,
                            onClick = { onSelectHapticStrength(level) },
                            label = { Text(level.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MiOrange.copy(alpha = 0.25f),
                                selectedLabelColor = TextPrimary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = chipSelected,
                                borderColor = RemoteBorder,
                                selectedBorderColor = MiOrange
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Google Home & Smart Lights Sign In
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(RemoteSurfaceElevated)
                    .border(1.dp, RemoteBorder, RoundedCornerShape(16.dp))
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
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (googleHomeSignedIn) ConnectedGreen.copy(alpha = 0.16f) else MiOrange.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lightbulb,
                                contentDescription = null,
                                tint = if (googleHomeSignedIn) ConnectedGreen else MiOrange,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Google Home & Smart Lights",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (googleHomeSignedIn) {
                                    googleHomeAccount?.takeIf { it.isNotBlank() } ?: "Google Account Connected"
                                } else {
                                    "Connect Wipro Next, Philips Hue & Smart Bulbs"
                                },
                                color = if (googleHomeSignedIn) ConnectedGreen else TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (googleHomeSignedIn) ConnectedGreen.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (googleHomeSignedIn) "Connected" else "Not Signed In",
                            color = if (googleHomeSignedIn) ConnectedGreen else TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (googleHomeSignedIn) {
                    Text(
                        text = "$googleHomeStatus • $googleHomeDevicesCount light(s) synced with app",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { onRefreshGoogleHome?.invoke() },
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync Lights", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = { onDisconnectGoogleHome?.invoke() },
                            modifier = Modifier.weight(1f).height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.2f), contentColor = Color(0xFFFCA5A5)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Disconnect", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    Text(
                        text = "Sign in to grant permission so GPT Remote can control all bulbs connected to your Google Home without third-party bridges.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )

                    Button(
                        onClick = { onSignInGoogleHome?.invoke() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MiOrange),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign In with Google Home", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Button Click Sound
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(RemoteSurfaceElevated)
                    .border(1.dp, RemoteBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Button Click Sound",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Play soft remote click audio on key press",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = soundEnabled,
                    onCheckedChange = onToggleSound,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MiOrange,
                        checkedBorderColor = MiOrange,
                        uncheckedThumbColor = Color(0xFFE2E8F0),
                        uncheckedTrackColor = Color(0xFF334155),
                        uncheckedBorderColor = Color(0xFF64748B)
                    )
                )
            }

            // App Theme Color
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(RemoteSurfaceElevated)
                    .border(1.dp, RemoteBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "App Accent Color",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Theme: ${accentTheme.label}",
                    color = LocalAccentSoft.current,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AppAccentTheme.entries.forEach { theme ->
                        val selected = theme == accentTheme
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Surface(
                                onClick = { onSelectAccentTheme(theme) },
                                shape = CircleShape,
                                color = theme.primary,
                                border = BorderStroke(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) Color.White else RemoteBorder
                                ),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = theme.label,
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = when (theme) {
                                    AppAccentTheme.ORANGE -> "Orange"
                                    AppAccentTheme.BLUE -> "Blue"
                                    AppAccentTheme.EMERALD -> "Emerald"
                                    AppAccentTheme.PURPLE -> "Purple"
                                    AppAccentTheme.CRIMSON -> "Crimson"
                                },
                                color = if (selected) LocalAccentSoft.current else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
