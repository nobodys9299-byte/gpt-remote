package com.gptremote.mitv.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.PhonelinkSetup
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.gptremote.mitv.haptic.HapticStrength
import com.gptremote.mitv.model.ActiveSessionItem
import com.gptremote.mitv.model.ConnectionStatus
import com.gptremote.mitv.model.ProductType
import com.gptremote.mitv.model.RemoteUiState
import com.gptremote.mitv.ui.components.SettingsSheet
import com.gptremote.mitv.ui.theme.AppAccentTheme
import com.gptremote.mitv.ui.theme.ConnectedGreen
import com.gptremote.mitv.ui.theme.LocalAccentSoft
import com.gptremote.mitv.ui.theme.MiOrange
import com.gptremote.mitv.ui.theme.RemoteBg
import com.gptremote.mitv.ui.theme.RemoteBorder
import com.gptremote.mitv.ui.theme.RemoteSurfaceElevated
import com.gptremote.mitv.ui.theme.TextPrimary
import com.gptremote.mitv.ui.theme.TextSecondary
import com.gptremote.mitv.ui.theme.WifiBlue

@Composable
fun HomeHubScreen(
    uiState: RemoteUiState,
    onSelectProduct: (ProductType) -> Unit,
    onOpenActiveRemote: () -> Unit,
    onDisconnect: () -> Unit,
    onSelectHapticStrength: (HapticStrength) -> Unit,
    onToggleSound: (Boolean) -> Unit,
    onSelectAccentTheme: (AppAccentTheme) -> Unit,
    onClearBanner: () -> Unit,
    onOpenActiveSession: (ActiveSessionItem) -> Unit = { onOpenActiveRemote() },
    onDisconnectSession: (ActiveSessionItem) -> Unit = { onDisconnect() },
    onScanQrCode: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showSettings by remember { mutableStateOf(false) }

    var productOrder by remember { mutableStateOf(loadProductOrder(context)) }
    val gridState = rememberLazyGridState()
    val density = LocalDensity.current
    var gridHeightPx by remember { mutableFloatStateOf(0f) }
    var draggedProduct by remember { mutableStateOf<ProductType?>(null) }
    var pointerLocal by remember { mutableStateOf(Offset.Zero) }
    var touchAnchorInItem by remember { mutableStateOf(Offset.Zero) }
    var draggedItemHalfSize by remember { mutableStateOf(Offset.Zero) }
    var lastSwapMs by remember { mutableLongStateOf(0L) }
    var lastSwappedTarget by remember { mutableStateOf<ProductType?>(null) }
    val sessions = uiState.activeSessions
    val accent = MiOrange
    val accentSoft = LocalAccentSoft.current

    fun trySwapAtPointer(pointer: Offset) {
        val active = draggedProduct ?: return
        val now = System.currentTimeMillis()
        if (now - lastSwapMs < 130L) return

        val dragCenter = pointer - touchAnchorInItem + draggedItemHalfSize
        val hit = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            val other = (info.key as? String)?.let { name ->
                ProductType.entries.firstOrNull { it.name == name }
            } ?: return@firstOrNull false
            if (other == active) return@firstOrNull false
            val w = info.size.width.toFloat()
            val h = info.size.height.toFloat()
            // Inner 62% zone avoids flicker when hovering on borders between cards
            val marginX = w * 0.19f
            val marginY = h * 0.19f
            val left = info.offset.x.toFloat() + marginX
            val top = info.offset.y.toFloat() + marginY
            val right = info.offset.x.toFloat() + w - marginX
            val bottom = info.offset.y.toFloat() + h - marginY
            dragCenter.x in left..right && dragCenter.y in top..bottom
        }
        val target = (hit?.key as? String)?.let { name ->
            ProductType.entries.firstOrNull { it.name == name }
        }
        if (target == null) {
            lastSwappedTarget = null
            return
        }
        if (target == lastSwappedTarget && now - lastSwapMs < 350L) return

        val fromIdx = productOrder.indexOf(active)
        val toIdx = productOrder.indexOf(target)
        if (fromIdx >= 0 && toIdx >= 0 && fromIdx != toIdx) {
            val mutable = productOrder.toMutableList()
            mutable.add(toIdx, mutable.removeAt(fromIdx))
            productOrder = mutable
            lastSwapMs = now
            lastSwappedTarget = target
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    LaunchedEffect(draggedProduct) {
        if (draggedProduct == null) return@LaunchedEffect
        val edgeZonePx = with(density) { 88.dp.toPx() }
        val maxStepPx = with(density) { 18.dp.toPx() }
        while (draggedProduct != null) {
            val h = gridHeightPx
            val py = pointerLocal.y
            if (h > edgeZonePx * 2f) {
                val delta = when {
                    py < edgeZonePx -> {
                        val factor = ((edgeZonePx - py) / edgeZonePx).coerceIn(0.15f, 1f)
                        -maxStepPx * factor
                    }
                    py > h - edgeZonePx -> {
                        val factor = ((py - (h - edgeZonePx)) / edgeZonePx).coerceIn(0.15f, 1f)
                        maxStepPx * factor
                    }
                    else -> 0f
                }
                if (delta != 0f) {
                    val scrolled = gridState.scrollBy(delta)
                    if (scrolled != 0f) {
                        trySwapAtPointer(pointerLocal)
                    }
                }
            }
            delay(16L)
        }
    }

    val statusError = (uiState.status as? ConnectionStatus.Error)?.message
    val bannerMessage = uiState.lastStatusBanner ?: statusError
    LaunchedEffect(bannerMessage) {
        if (!bannerMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(bannerMessage)
            onClearBanner()
        }
    }

    if (showSettings) {
        SettingsSheet(
            hapticStrength = uiState.hapticStrength,
            soundEnabled = uiState.soundEnabled,
            accentTheme = uiState.accentTheme,
            onSelectHapticStrength = onSelectHapticStrength,
            onToggleSound = onToggleSound,
            onSelectAccentTheme = onSelectAccentTheme,
            onDismiss = { showSettings = false }
        )
    }

    Scaffold(
        containerColor = RemoteBg,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Fixed clean top header (never collides or clips under status bar during grid scroll)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "GPT Remote",
                            color = TextPrimary,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.18f))
                                .border(1.dp, accent.copy(alpha = 0.55f), CircleShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "HUB",
                                color = accentSoft,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    Text(
                        text = if (draggedProduct != null) {
                            "Drag to place card anywhere"
                        } else {
                            "Tap a device • Hold & drag to reorder"
                        },
                        color = if (draggedProduct != null) accent else TextSecondary,
                        fontSize = 13.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalIconButton(
                        onClick = onScanQrCode,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = accent.copy(alpha = 0.16f),
                            contentColor = accent
                        )
                    ) {
                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = "Scan QR Code")
                    }
                    FilledTonalIconButton(
                        onClick = { showSettings = true },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = RemoteSurfaceElevated,
                            contentColor = accent
                        )
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                }
            }

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { gridHeightPx = it.height.toFloat() }
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { localPos ->
                                val hitInfo = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                    val product = (info.key as? String)?.let { name ->
                                        ProductType.entries.firstOrNull { it.name == name }
                                    } ?: return@firstOrNull false
                                    val rect = Rect(
                                        offset = Offset(info.offset.x.toFloat(), info.offset.y.toFloat()),
                                        size = Size(info.size.width.toFloat(), info.size.height.toFloat())
                                    )
                                    rect.contains(localPos)
                                }
                                val product = (hitInfo?.key as? String)?.let { name ->
                                    ProductType.entries.firstOrNull { it.name == name }
                                }
                                if (hitInfo != null && product != null) {
                                    draggedProduct = product
                                    pointerLocal = localPos
                                    touchAnchorInItem = localPos - Offset(
                                        hitInfo.offset.x.toFloat(),
                                        hitInfo.offset.y.toFloat()
                                    )
                                    draggedItemHalfSize = Offset(
                                        hitInfo.size.width / 2f,
                                        hitInfo.size.height / 2f
                                    )
                                    lastSwappedTarget = null
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            },
                            onDrag = { change, dragAmount ->
                                if (draggedProduct == null) return@detectDragGesturesAfterLongPress
                                change.consume()
                                pointerLocal += dragAmount
                                trySwapAtPointer(pointerLocal)
                            },
                            onDragEnd = {
                                if (draggedProduct != null) {
                                    draggedProduct = null
                                    lastSwappedTarget = null
                                    saveProductOrder(context, productOrder)
                                }
                            },
                            onDragCancel = {
                                if (draggedProduct != null) {
                                    draggedProduct = null
                                    lastSwappedTarget = null
                                    saveProductOrder(context, productOrder)
                                }
                            }
                        )
                    },
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (sessions.isNotEmpty()) {
                    val primarySession = sessions.first()
                    item(key = "__ACTIVE_SESSION__", span = { GridItemSpan(2) }) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(RemoteSurfaceElevated, Color(0xFF1A2838))
                                    )
                                )
                                .border(1.5.dp, ConnectedGreen.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                                .clickable { onOpenActiveSession(primarySession) }
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (sessions.size == 1) {
                                            "Connected: ${primarySession.title}"
                                        } else {
                                            "Connected (${sessions.size}): ${primarySession.title}"
                                        },
                                        color = ConnectedGreen,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (sessions.size == 1) {
                                            "${primarySession.subtitle} • Tap to open"
                                        } else {
                                            "Tap any active device pill below to switch"
                                        },
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { onOpenActiveSession(primarySession) },
                                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Open")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                OutlinedButton(
                                    onClick = onDisconnect,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Off")
                                }
                            }

                            if (sessions.size > 1) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    sessions.forEach { session ->
                                        Row(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(Color(0xFF101622))
                                                .border(1.dp, accent.copy(alpha = 0.55f), CircleShape)
                                                .clickable { onOpenActiveSession(session) }
                                                .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(ConnectedGreen)
                                            )
                                            Text(
                                                text = "${session.title} • ${session.subtitle}",
                                                color = TextPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .background(RemoteSurfaceElevated)
                                                    .clickable { onDisconnectSession(session) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = "Disconnect ${session.title}",
                                                    tint = TextSecondary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                items(productOrder, key = { it.name }) { product ->
                    val isDragged = draggedProduct == product
                    ProductCard(
                        product = product,
                        isDragged = isDragged,
                        modifier = Modifier
                            .zIndex(if (isDragged) 20f else 0f)
                            .graphicsLayer {
                                if (isDragged) {
                                    val info = gridState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.key == product.name }
                                    if (info != null) {
                                        val delta = pointerLocal - Offset(
                                            info.offset.x.toFloat(),
                                            info.offset.y.toFloat()
                                        ) - touchAnchorInItem
                                        translationX = delta.x
                                        translationY = delta.y
                                    }
                                    scaleX = 1.05f
                                    scaleY = 1.05f
                                    shadowElevation = 18f
                                    alpha = 0.94f
                                }
                            },
                        onClick = { onSelectProduct(product) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductCard(
    product: ProductType,
    isDragged: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val (icon, gradientStart, gradientEnd) = productVisualsGradient(product)
    val activeAccent = MiOrange

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(RemoteSurfaceElevated)
            .border(
                width = if (isDragged) 2.dp else 1.dp,
                color = if (isDragged) activeAccent else RemoteBorder,
                shape = RoundedCornerShape(22.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(gradientStart, gradientEnd)
                    )
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = product.title,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }

        Column {
            Text(
                text = product.title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = product.subtitle,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun productVisuals(product: ProductType): Pair<ImageVector, Color> {
    val (icon, start, _) = productVisualsGradient(product)
    return icon to start
}

@Composable
private fun productVisualsGradient(product: ProductType): Triple<ImageVector, Color, Color> = when (product) {
    ProductType.SMART_TV -> Triple(Icons.Rounded.Tv, Color(0xFFFF6B00), Color(0xFFDC2626))
    ProductType.PC_LAPTOP -> Triple(Icons.Rounded.Laptop, Color(0xFF38BDF8), Color(0xFF2563EB))
    ProductType.AC -> Triple(Icons.Rounded.AcUnit, Color(0xFF60A5FA), Color(0xFF0284C7))
    ProductType.FRIDGE -> Triple(Icons.Rounded.Kitchen, Color(0xFF34D399), Color(0xFF059669))
    ProductType.WASHING_MACHINE -> Triple(Icons.Rounded.LocalLaundryService, Color(0xFFA78BFA), Color(0xFF7C3AED))
    ProductType.SET_TOP_BOX -> Triple(Icons.Rounded.Router, Color(0xFFFBBF24), Color(0xFFD97706))
    ProductType.SOUNDBAR -> Triple(Icons.Rounded.Speaker, Color(0xFFF472B6), Color(0xFFDB2777))
    ProductType.PROJECTOR -> Triple(Icons.Rounded.Videocam, Color(0xFFFB923C), Color(0xFFEA580C))
    ProductType.CEILING_FAN -> Triple(Icons.Rounded.Air, Color(0xFF2DD4BF), Color(0xFF0D9488))
    ProductType.SMART_BULB -> Triple(Icons.Rounded.Lightbulb, Color(0xFFFACC15), Color(0xFFEAB308))
    ProductType.PHONE_TO_PHONE -> Triple(Icons.Rounded.PhonelinkSetup, Color(0xFF22D3EE), Color(0xFF0891B2))
}

private const val PRODUCT_ORDER_PREFS = "gpt_product_order"
private const val KEY_ORDER = "order_csv"

private fun loadProductOrder(context: Context): List<ProductType> {
    val prefs = context.applicationContext.getSharedPreferences(PRODUCT_ORDER_PREFS, Context.MODE_PRIVATE)
    val saved = prefs.getString(KEY_ORDER, null)
    val all = ProductType.entries.toList()
    if (saved.isNullOrBlank()) return all
    val ordered = saved.split(',')
        .mapNotNull { name -> all.firstOrNull { it.name == name } }
        .distinct()
        .toMutableList()
    all.forEach { if (it !in ordered) ordered.add(it) }
    return ordered
}

private fun saveProductOrder(context: Context, order: List<ProductType>) {
    context.applicationContext.getSharedPreferences(PRODUCT_ORDER_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_ORDER, order.joinToString(",") { it.name })
        .apply()
}
