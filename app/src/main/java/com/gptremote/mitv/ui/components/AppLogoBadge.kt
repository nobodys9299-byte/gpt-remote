package com.gptremote.mitv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gptremote.mitv.model.LaunchableApp

/**
 * Renders original-style branded vector/canvas icons for streaming & PC apps.
 */
@Composable
fun AppLogoBadge(app: LaunchableApp, modifier: Modifier = Modifier) {
    when (app.id) {
        "youtube" -> Box(
            modifier = modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFF0000)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(18.dp)) {
                val p = Path().apply {
                    moveTo(size.width * 0.2f, size.height * 0.12f)
                    lineTo(size.width * 0.9f, size.height * 0.5f)
                    lineTo(size.width * 0.2f, size.height * 0.88f)
                    close()
                }
                drawPath(p, Color.White)
            }
        }

        "netflix" -> Box(
            modifier = modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0A0A0A)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(22.dp)) {
                val w = size.width
                val h = size.height
                drawRect(Color(0xFFB91C1C), topLeft = Offset(w * 0.16f, h * 0.12f), size = androidx.compose.ui.geometry.Size(w * 0.18f, h * 0.76f))
                drawRect(Color(0xFFB91C1C), topLeft = Offset(w * 0.66f, h * 0.12f), size = androidx.compose.ui.geometry.Size(w * 0.18f, h * 0.76f))
                val ribbon = Path().apply {
                    moveTo(w * 0.16f, h * 0.12f)
                    lineTo(w * 0.34f, h * 0.12f)
                    lineTo(w * 0.84f, h * 0.88f)
                    lineTo(w * 0.66f, h * 0.88f)
                    close()
                }
                drawPath(ribbon, Color(0xFFE50914))
            }
        }

        "prime" -> Box(
            modifier = modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF002D4C)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(26.dp)) {
                val w = size.width
                val h = size.height
                val smile = Path().apply {
                    moveTo(w * 0.16f, h * 0.62f)
                    quadraticTo(w * 0.5f, h * 0.86f, w * 0.84f, h * 0.58f)
                }
                drawPath(smile, Color(0xFF00A8E1), style = Stroke(width = 3.2f, cap = StrokeCap.Round))
                val arrow = Path().apply {
                    moveTo(w * 0.74f, h * 0.52f)
                    lineTo(w * 0.88f, h * 0.57f)
                    lineTo(w * 0.80f, h * 0.72f)
                }
                drawPath(arrow, Color(0xFF00A8E1), style = Stroke(width = 2.8f, cap = StrokeCap.Round))
            }
            Text("prime", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
        }

        "hotstar" -> Box(
            modifier = modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF091B4D), Color(0xFF0E4FA8))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("★", color = Color(0xFF38BDF8), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        "spotify" -> Box(
            modifier = modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1DB954)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(22.dp)) {
                val w = size.width
                val h = size.height
                for ((yRatio, amp, stroke) in listOf(
                    Triple(0.32f, 0.12f, 3.2f),
                    Triple(0.52f, 0.10f, 2.7f),
                    Triple(0.72f, 0.08f, 2.2f)
                )) {
                    val wave = Path().apply {
                        moveTo(w * 0.16f, h * yRatio)
                        quadraticTo(w * 0.52f, h * (yRatio - amp), w * 0.84f, h * (yRatio + 0.06f))
                    }
                    drawPath(wave, Color(0xFF0B0E14), style = Stroke(width = stroke, cap = StrokeCap.Round))
                }
            }
        }

        "mxplayer" -> Box(
            modifier = modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF1E40AF), Color(0xFF2563EB))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(20.dp)) {
                drawCircle(Color.White.copy(alpha = 0.25f))
                val p = Path().apply {
                    moveTo(size.width * 0.36f, size.height * 0.26f)
                    lineTo(size.width * 0.76f, size.height * 0.50f)
                    lineTo(size.width * 0.36f, size.height * 0.74f)
                    close()
                }
                drawPath(p, Color.White)
            }
        }

        "playstore" -> Box(
            modifier = modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF111827)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(22.dp)) {
                val w = size.width
                val h = size.height
                val green = Path().apply {
                    moveTo(w * 0.14f, h * 0.10f)
                    lineTo(w * 0.60f, h * 0.50f)
                    lineTo(w * 0.14f, h * 0.90f)
                    close()
                }
                drawPath(green, Color(0xFF34D399))
                val blue = Path().apply {
                    moveTo(w * 0.14f, h * 0.10f)
                    lineTo(w * 0.88f, h * 0.50f)
                    lineTo(w * 0.60f, h * 0.50f)
                    close()
                }
                drawPath(blue, Color(0xFF38BDF8))
                val orange = Path().apply {
                    moveTo(w * 0.60f, h * 0.50f)
                    lineTo(w * 0.88f, h * 0.50f)
                    lineTo(w * 0.14f, h * 0.90f)
                    close()
                }
                drawPath(orange, Color(0xFFFBBF24))
            }
        }

        "sonyliv" -> Box(
            modifier = modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF4F46E5), Color(0xFFEC4899))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("LIV", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        }

        "zee5" -> Box(
            modifier = modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF6D28D9), Color(0xFF9333EA))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("Z5", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }

        "jiocinema" -> Box(
            modifier = modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFDB2777), Color(0xFF9D174D))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("Jio", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }

        else -> {
            val vector = when (app.id) {
                "browser" -> Icons.Rounded.Language
                "files" -> Icons.Rounded.Folder
                "calc" -> Icons.Rounded.Calculate
                "terminal" -> Icons.Rounded.Terminal
                "taskmgr" -> Icons.Rounded.Speed
                "settings" -> Icons.Rounded.Settings
                else -> null
            }
            Box(
                modifier = modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(app.badgeColor),
                contentAlignment = Alignment.Center
            ) {
                if (vector != null) {
                    Icon(
                        imageVector = vector,
                        contentDescription = app.name,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text(
                        text = app.shortBadge,
                        color = Color.White,
                        fontSize = if (app.shortBadge.length >= 3) 11.sp else 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}
