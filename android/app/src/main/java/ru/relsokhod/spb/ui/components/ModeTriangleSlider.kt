package ru.relsokhod.spb.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Tram
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.relsokhod.spb.model.ModeBalance
import kotlin.math.abs
import kotlin.math.roundToInt

data class TrianglePoint(val x: Float, val y: Float)

object TriangleBalanceMath {
    val VERTEX_TRAM = TrianglePoint(0.50f, 0.14f)
    val VERTEX_TRANSIT = TrianglePoint(0.14f, 0.84f)
    val VERTEX_WALK = TrianglePoint(0.86f, 0.84f)

    fun balanceToTrianglePoint(balance: ModeBalance): TrianglePoint {
        val n = balance.normalized()
        return TrianglePoint(
            x = n.tram * VERTEX_TRAM.x + n.transit * VERTEX_TRANSIT.x + n.walk * VERTEX_WALK.x,
            y = n.tram * VERTEX_TRAM.y + n.transit * VERTEX_TRANSIT.y + n.walk * VERTEX_WALK.y
        )
    }

    fun barycentricWeights(px: Float, py: Float): Triple<Float, Float, Float> {
        val a = VERTEX_TRAM
        val b = VERTEX_TRANSIT
        val c = VERTEX_WALK
        val detT = (b.y - c.y) * (a.x - c.x) + (c.x - b.x) * (a.y - c.y)
        val wA = ((b.y - c.y) * (px - c.x) + (c.x - b.x) * (py - c.y)) / detT
        val wB = ((c.y - a.y) * (px - c.x) + (a.x - c.x) * (py - c.y)) / detT
        val wC = 1f - wA - wB
        return Triple(wA, wB, wC)
    }

    fun isPointInTriangle(px: Float, py: Float, edgeTolerance: Float = 0.025f): Boolean {
        val (wA, wB, wC) = barycentricWeights(px, py)
        return wA >= -edgeTolerance && wB >= -edgeTolerance && wC >= -edgeTolerance
    }

    fun trianglePointToBalance(px: Float, py: Float): ModeBalance {
        var (wA, wB, wC) = barycentricWeights(px, py)

        // Project onto probability simplex (w >= 0, sum = 1)
        val sorted = listOf(wA, wB, wC).sortedDescending()
        var tm = 0f
        var sum = 0f
        var k = 0
        for (i in 0 until 3) {
            sum += sorted[i]
            val candidate = (sum - 1f) / (i + 1)
            if (sorted[i] - candidate > 0f) {
                tm = candidate
                k = i + 1
            }
        }
        val threshold = if (k > 0) tm else sorted[0] - 1f
        wA = maxOf(0f, wA - threshold)
        wB = maxOf(0f, wB - threshold)
        wC = maxOf(0f, wC - threshold)

        return ModeBalance(tram = wA, transit = wB, walk = wC).normalized()
    }
}

private data class BalancePreset(val label: String, val value: ModeBalance)

private val BALANCE_PRESETS = listOf(
    BalancePreset("Больше трамвая", ModeBalance(tram = 0.86f, transit = 0.04f, walk = 0.10f)),
    BalancePreset("Трамвай + прогулка", ModeBalance(tram = 0.50f, transit = 0.05f, walk = 0.45f)),
    BalancePreset("Метро + пешком", ModeBalance(tram = 0.14f, transit = 0.44f, walk = 0.42f)),
    BalancePreset("Баланс 3 в 1", ModeBalance(tram = 0.34f, transit = 0.33f, walk = 0.33f)),
    BalancePreset("Макс. пешком", ModeBalance(tram = 0.22f, transit = 0.06f, walk = 0.72f))
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModeTriangleSlider(
    value: ModeBalance,
    onChange: (ModeBalance) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var isDragging by remember { mutableStateOf(false) }
    var dragBalance by remember { mutableStateOf<ModeBalance?>(null) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val currentOnChange by rememberUpdatedState(onChange)

    val displayBalance = dragBalance ?: value

    val pctTram = (displayBalance.tram * 100f).roundToInt()
    val pctTransit = (displayBalance.transit * 100f).roundToInt()
    val pctWalk = maxOf(0, 100 - pctTram - pctTransit)

    val primaryColor = MaterialTheme.colorScheme.primary
    val transitColor = Color(0xFFD97706)
    val walkColor = Color(0xFF059669)
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val medianColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) }

    val cachedGeometry = remember(boxSize, primaryColor) {
        if (boxSize.width <= 0 || boxSize.height <= 0) null
        else {
            val w = boxSize.width.toFloat()
            val h = boxSize.height.toFloat()
            val vTram = Offset(TriangleBalanceMath.VERTEX_TRAM.x * w, TriangleBalanceMath.VERTEX_TRAM.y * h)
            val vTransit = Offset(TriangleBalanceMath.VERTEX_TRANSIT.x * w, TriangleBalanceMath.VERTEX_TRANSIT.y * h)
            val vWalk = Offset(TriangleBalanceMath.VERTEX_WALK.x * w, TriangleBalanceMath.VERTEX_WALK.y * h)
            val path = Path().apply {
                moveTo(vTram.x, vTram.y)
                lineTo(vTransit.x, vTransit.y)
                lineTo(vWalk.x, vWalk.y)
                close()
            }
            val brush = Brush.linearGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.16f),
                    transitColor.copy(alpha = 0.11f),
                    walkColor.copy(alpha = 0.15f)
                ),
                start = vTram,
                end = vWalk
            )
            Triple(Triple(vTram, vTransit, vWalk), path, brush)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Баланс маршрута", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(
                text = "Двигайте точку внутри",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Live 3-mode percentage tiles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Tram,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "Трамвай",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        "$pctTram%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.DirectionsBus,
                            contentDescription = null,
                            tint = transitColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text("Метро / тр.", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("$pctTransit%", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.DirectionsWalk,
                            contentDescription = null,
                            tint = walkColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text("Пешком", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("$pctWalk%", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        // Interactive barycentric triangle surface
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(216.dp)
                        .onSizeChanged { boxSize = it }
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val h = size.height.toFloat().coerceAtLeast(1f)
                                    val nx = down.position.x / w
                                    val ny = down.position.y / h
                                    // Ignore taps outside the triangle shape so corners don't accidentally move the balance
                                    if (!TriangleBalanceMath.isPointInTriangle(nx, ny)) {
                                        return@awaitEachGesture
                                    }
                                    down.consume()
                                    isDragging = true
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    var latest = TriangleBalanceMath.trianglePointToBalance(nx, ny)
                                    dragBalance = latest
                                    val activeId = down.id
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == activeId }
                                            ?: event.changes.firstOrNull()
                                        if (change == null || !change.pressed) break
                                        change.consume()
                                        latest = TriangleBalanceMath.trianglePointToBalance(
                                            change.position.x / w,
                                            change.position.y / h
                                        )
                                        dragBalance = latest
                                    }
                                    isDragging = false
                                    dragBalance = null
                                    currentOnChange(latest)
                                }
                            }
                    ) {
                        val geom = cachedGeometry ?: return@Canvas
                        val (vertices, triPath, triBrush) = geom
                        val (vTram, vTransit, vWalk) = vertices

                        val thumbNorm = TriangleBalanceMath.balanceToTrianglePoint(displayBalance)
                        val thumb = Offset(thumbNorm.x * size.width, thumbNorm.y * size.height)

                        drawPath(path = triPath, brush = triBrush)
                        drawPath(
                            path = triPath,
                            color = outlineColor,
                            style = Stroke(width = 4f, join = StrokeJoin.Round, cap = StrokeCap.Round)
                        )

                        drawLine(
                            color = medianColor,
                            start = vTram,
                            end = Offset((vTransit.x + vWalk.x) / 2f, (vTransit.y + vWalk.y) / 2f),
                            strokeWidth = 2.5f,
                            pathEffect = dashEffect
                        )
                        drawLine(
                            color = medianColor,
                            start = vTransit,
                            end = Offset((vTram.x + vWalk.x) / 2f, (vTram.y + vWalk.y) / 2f),
                            strokeWidth = 2.5f,
                            pathEffect = dashEffect
                        )
                        drawLine(
                            color = medianColor,
                            start = vWalk,
                            end = Offset((vTram.x + vTransit.x) / 2f, (vTram.y + vTransit.y) / 2f),
                            strokeWidth = 2.5f,
                            pathEffect = dashEffect
                        )

                        val tensionColor = primaryColor.copy(alpha = 0.38f)
                        drawLine(tensionColor, start = thumb, end = vTram, strokeWidth = 3f)
                        drawLine(tensionColor, start = thumb, end = vTransit, strokeWidth = 3f)
                        drawLine(tensionColor, start = thumb, end = vWalk, strokeWidth = 3f)

                        drawCircle(Color.White, radius = 14f, center = vTram)
                        drawCircle(primaryColor, radius = 10f, center = vTram)

                        drawCircle(Color.White, radius = 14f, center = vTransit)
                        drawCircle(transitColor, radius = 10f, center = vTransit)

                        drawCircle(Color.White, radius = 14f, center = vWalk)
                        drawCircle(walkColor, radius = 10f, center = vWalk)

                        drawCircle(primaryColor.copy(alpha = 0.22f), radius = if (isDragging) 34f else 28f, center = thumb)
                        drawCircle(Color.White, radius = if (isDragging) 21f else 18f, center = thumb)
                        drawCircle(primaryColor, radius = if (isDragging) 16f else 13f, center = thumb)
                    }

                    Text(
                        text = "Трамваи",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 2.dp)
                    )
                    Text(
                        text = "Метро / гор. тр.",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 12.dp, bottom = 4.dp)
                    )
                    Text(
                        text = "Пешком",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = 4.dp)
                    )
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BALANCE_PRESETS.forEach { preset ->
                        val isClose =
                            abs(displayBalance.tram - preset.value.tram) < 0.06f &&
                                abs(displayBalance.transit - preset.value.transit) < 0.06f &&
                                abs(displayBalance.walk - preset.value.walk) < 0.06f
                        FilterChip(
                            selected = isClose,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                dragBalance = null
                                onChange(preset.value)
                            },
                            shape = RoundedCornerShape(50),
                            label = { Text(preset.label, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
    }
}
