package ru.relsokhod.spb.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun StartupSplash(
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    val scaleIn by animateFloatAsState(
        targetValue = if (entered) 1f else 0.88f,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "splashScale"
    )
    val progressFill by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "splashProgress"
    )

    val infinite = rememberInfiniteTransition(label = "nevaSplashMotion")
    val wavePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )
    val tramBob by infinite.animateFloat(
        initialValue = -2.2f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tramBob"
    )
    val headlightPulse by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "headlightPulse"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF072B47),
                        Color(0xFF0F5E8C),
                        Color(0xFF0D7A6F)
                    )
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSkip
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .graphicsLayer {
                    scaleX = scaleIn
                    scaleY = scaleIn
                }
        ) {
            Box(
                modifier = Modifier
                    .size(136.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(116.dp)) {
                    val s = size.minDimension / 512f
                    val bobPx = tramBob * 2.4f * s

                    // Subtle halo glow behind the tram
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF5EEAD4).copy(alpha = 0.24f),
                                Color.Transparent
                            ),
                            center = Offset(256f * s, 250f * s),
                            radius = 220f * s
                        ),
                        center = Offset(256f * s, 250f * s),
                        radius = 220f * s
                    )

                    // Catenary overhead wire
                    val wirePath = Path().apply {
                        moveTo(110f * s, 118f * s)
                        quadraticTo(256f * s, 102f * s, 402f * s, 118f * s)
                    }
                    drawPath(
                        path = wirePath,
                        color = Color(0xFF7DD3FC).copy(alpha = 0.65f),
                        style = Stroke(width = 7f * s, cap = StrokeCap.Round)
                    )

                    // Twin converging perspective rails
                    drawLine(
                        color = Color(0xFFE0F2FE).copy(alpha = 0.9f),
                        start = Offset(204f * s, 344f * s),
                        end = Offset(154f * s, 418f * s),
                        strokeWidth = 14f * s,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color(0xFFE0F2FE).copy(alpha = 0.9f),
                        start = Offset(308f * s, 344f * s),
                        end = Offset(358f * s, 418f * s),
                        strokeWidth = 14f * s,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color(0xFFBAE6FD).copy(alpha = 0.72f),
                        start = Offset(182f * s, 372f * s),
                        end = Offset(330f * s, 372f * s),
                        strokeWidth = 10f * s,
                        cap = StrokeCap.Round
                    )

                    // Animated Neva wave crest
                    val wavePath = Path().apply {
                        val baseY = 408f * s
                        moveTo(96f * s, baseY + sin(wavePhase) * 8f * s)
                        var x = 96f
                        while (x <= 416f) {
                            val t = (x - 96f) / 320f * 2f * PI.toFloat()
                            val y = baseY + sin(t * 1.4f + wavePhase) * 11f * s
                            lineTo(x * s, y)
                            x += 16f
                        }
                    }
                    drawPath(
                        path = wavePath,
                        color = Color(0xFF5EEAD4),
                        style = Stroke(width = 14f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    // Pantograph + Tram cabin with subtle suspension bob
                    drawLine(
                        color = Color.White,
                        start = Offset(206f * s, 124f * s + bobPx * 0.4f),
                        end = Offset(306f * s, 124f * s + bobPx * 0.4f),
                        strokeWidth = 14f * s,
                        cap = StrokeCap.Round
                    )
                    val panto = Path().apply {
                        moveTo(224f * s, 124f * s + bobPx * 0.4f)
                        lineTo(256f * s, 156f * s + bobPx)
                        lineTo(288f * s, 124f * s + bobPx * 0.4f)
                    }
                    drawPath(
                        path = panto,
                        color = Color(0xFFE0F2FE),
                        style = Stroke(width = 12f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    drawRoundRect(
                        color = Color(0xFFBAE6FD),
                        topLeft = Offset(220f * s, 150f * s + bobPx),
                        size = Size(72f * s, 16f * s),
                        cornerRadius = CornerRadius(8f * s, 8f * s)
                    )
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(162f * s, 162f * s + bobPx),
                        size = Size(188f * s, 178f * s),
                        cornerRadius = CornerRadius(44f * s, 44f * s)
                    )
                    // Turquoise LED route header bar
                    drawRoundRect(
                        color = Color(0xFF14B8A6),
                        topLeft = Offset(212f * s, 176f * s + bobPx),
                        size = Size(88f * s, 11f * s),
                        cornerRadius = CornerRadius(5.5f * s, 5.5f * s)
                    )
                    // Windshield
                    drawRoundRect(
                        color = Color(0xFF0A3D5E),
                        topLeft = Offset(184f * s, 198f * s + bobPx),
                        size = Size(144f * s, 72f * s),
                        cornerRadius = CornerRadius(20f * s, 20f * s)
                    )
                    // Headlights
                    drawCircle(
                        color = Color(0xFF0F5E8C),
                        radius = 15f * s,
                        center = Offset(204f * s, 304f * s + bobPx)
                    )
                    drawCircle(
                        color = Color(0xFF7DD3FC).copy(alpha = headlightPulse),
                        radius = 8f * s,
                        center = Offset(204f * s, 304f * s + bobPx)
                    )
                    drawCircle(
                        color = Color(0xFF0F5E8C),
                        radius = 15f * s,
                        center = Offset(308f * s, 304f * s + bobPx)
                    )
                    drawCircle(
                        color = Color(0xFF7DD3FC).copy(alpha = headlightPulse),
                        radius = 8f * s,
                        center = Offset(308f * s, 304f * s + bobPx)
                    )
                    // Bumper grille
                    drawRoundRect(
                        color = Color(0xFF94A3B8),
                        topLeft = Offset(234f * s, 298f * s + bobPx),
                        size = Size(44f * s, 12f * s),
                        cornerRadius = CornerRadius(6f * s, 6f * s)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Рельсоход",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Трамвайные прогулки · СПб и Омск",
                color = Color(0xFFBAE6FD),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Smooth Neva progress track
            Box(
                modifier = Modifier
                    .width(164.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.18f))
            ) {
                Box(
                    modifier = Modifier
                        .width((164f * progressFill).dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF7DD3FC), Color(0xFF5EEAD4))
                            )
                        )
                )
            }
        }
    }
}
