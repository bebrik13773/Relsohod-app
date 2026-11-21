package ru.relsokhod.spb.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.relsokhod.spb.model.CityId
import ru.relsokhod.spb.model.GeneratedTrip
import ru.relsokhod.spb.model.SegmentKind
import ru.relsokhod.spb.model.TramRoute
import ru.relsokhod.spb.model.TramStop
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Generates a smooth Catmull-Rom cubic Bezier Path instead of sharp jagged polygonal corners.
 */
private fun buildSmoothCatmullRomPath(points: List<Offset>, tension: Float = 0.28f): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    if (points.size == 1) return path
    if (points.size == 2) {
        path.lineTo(points[1].x, points[1].y)
        return path
    }
    for (i in 0 until points.size - 1) {
        val p0 = points[maxOf(0, i - 1)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[minOf(points.size - 1, i + 2)]

        val cp1x = p1.x + (p2.x - p0.x) * tension
        val cp1y = p1.y + (p2.y - p0.y) * tension
        val cp2x = p2.x - (p3.x - p1.x) * tension
        val cp2y = p2.y - (p3.y - p1.y) * tension

        path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
    }
    return path
}

/**
 * Offsets a polyline perpendicular to its direction so parallel tram routes on the same street
 * display neatly side-by-side instead of tangling.
 */
private fun offsetPolylinePoints(points: List<Offset>, offsetPx: Float): List<Offset> {
    if (abs(offsetPx) < 0.05f || points.size < 2) return points
    return points.mapIndexed { idx, pt ->
        val prev = points[maxOf(0, idx - 1)]
        val next = points[minOf(points.size - 1, idx + 1)]
        val dx = next.x - prev.x
        val dy = next.y - prev.y
        val len = hypot(dx, dy).takeIf { it > 0.001f } ?: 1f
        Offset(pt.x + (-dy / len) * offsetPx, pt.y + (dx / len) * offsetPx)
    }
}

private fun computeRouteLaneOffsets(
    routes: List<TramRoute>,
    laneSpacingPx: Float = 3.6f
): Map<String, Float> {
    val edgeUsers = HashMap<String, MutableList<String>>()
    for (r in routes) {
        for (i in 0 until r.stopIds.size - 1) {
            val a = r.stopIds[i]
            val b = r.stopIds[i + 1]
            val key = if (a < b) "$a|$b" else "$b|$a"
            val list = edgeUsers.getOrPut(key) { mutableListOf() }
            if (r.id !in list) list.add(r.id)
        }
    }
    val out = HashMap<String, Float>()
    for (r in routes) {
        var maxSlot = 0
        var bestOffset = 0f
        for (i in 0 until r.stopIds.size - 1) {
            val a = r.stopIds[i]
            val b = r.stopIds[i + 1]
            val key = if (a < b) "$a|$b" else "$b|$a"
            val users = edgeUsers[key].orEmpty()
            if (users.size > maxSlot) {
                maxSlot = users.size
                val idx = users.indexOf(r.id)
                bestOffset = (idx - (users.size - 1) / 2f) * laneSpacingPx
            }
        }
        out[r.id] = bestOffset
    }
    return out
}

@Composable
fun TramMapCanvas(
    city: CityId,
    stops: List<TramStop>,
    routes: List<TramRoute>,
    trip: GeneratedTrip?,
    showAllNetwork: Boolean,
    mapPickMode: String?, // "start" | "destination" | null
    onPickStop: (TramStop) -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember(city) { mutableFloatStateOf(1f) }
    var pan by remember(city) { mutableStateOf(Offset.Zero) }

    val stopMap = remember(stops) { stops.associateBy { it.id } }
    val laneOffsets = remember(routes) { computeRouteLaneOffsets(routes, 3.6f) }
    val bounds = remember(stops) {
        if (stops.isEmpty()) {
            doubleArrayOf(59.8, 60.05, 30.1, 30.55)
        } else {
            val minLat = stops.minOf { it.lat } - 0.012
            val maxLat = stops.maxOf { it.lat } + 0.012
            val minLng = stops.minOf { it.lng } - 0.02
            val maxLng = stops.maxOf { it.lng } + 0.02
            doubleArrayOf(minLat, maxLat, minLng, maxLng)
        }
    }

    val surfaceBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val waterColor = Color(0xFF7CB9E8).copy(alpha = 0.30f)
    val mutedRailColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
    val stopDotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .background(surfaceBg)
            .pointerInput(city) {
                detectTransformGestures { _, panChange, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.85f, 4.5f)
                    pan += panChange
                }
            }
            .pointerInput(stops, scale, pan, mapPickMode) {
                detectTapGestures { tapOffset ->
                    if (stops.isEmpty()) return@detectTapGestures
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val pad = 36f
                    val minLat = bounds[0]
                    val maxLat = bounds[1]
                    val minLng = bounds[2]
                    val maxLng = bounds[3]

                    var nearest: TramStop? = null
                    var bestDist = 42f
                    for (s in stops) {
                        val baseX = pad + ((s.lng - minLng) / (maxLng - minLng)).toFloat() * (w - pad * 2)
                        val baseY = pad + (1f - ((s.lat - minLat) / (maxLat - minLat)).toFloat()) * (h - pad * 2)
                        val sx = (baseX - w / 2f) * scale + w / 2f + pan.x
                        val sy = (baseY - h / 2f) * scale + h / 2f + pan.y
                        val d = hypot(tapOffset.x - sx, tapOffset.y - sy)
                        if (d < bestDist) {
                            bestDist = d
                            nearest = s
                        }
                    }
                    if (nearest != null && mapPickMode != null) {
                        onPickStop(nearest)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val pad = 36f
            val minLat = bounds[0]
            val maxLat = bounds[1]
            val minLng = bounds[2]
            val maxLng = bounds[3]

            fun toScreen(lat: Double, lng: Double): Offset {
                val baseX = pad + ((lng - minLng) / (maxLng - minLng)).toFloat() * (w - pad * 2)
                val baseY = pad + (1f - ((lat - minLat) / (maxLat - minLat)).toFloat()) * (h - pad * 2)
                return Offset(
                    x = (baseX - w / 2f) * scale + w / 2f + pan.x,
                    y = (baseY - h / 2f) * scale + h / 2f + pan.y
                )
            }

            // Smooth river contour (Neva for SPb, Irtysh/Om for Omsk)
            val riverPts = if (city == CityId.SPB) {
                listOf(
                    toScreen(59.946, 30.22),
                    toScreen(59.942, 30.30),
                    toScreen(59.949, 30.35),
                    toScreen(59.935, 30.40),
                    toScreen(59.895, 30.45),
                    toScreen(59.845, 30.50)
                )
            } else {
                listOf(
                    toScreen(55.035, 73.28),
                    toScreen(54.995, 73.34),
                    toScreen(54.970, 73.36),
                    toScreen(54.930, 73.39)
                )
            }
            drawPath(
                path = buildSmoothCatmullRomPath(riverPts, 0.34f),
                color = waterColor,
                style = Stroke(
                    width = 24f * scale.coerceAtMost(2f),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // All tram network lines background with symmetric corridor lane separation
            if (showAllNetwork) {
                for (route in routes) {
                    val rawPts = route.stopIds.mapNotNull { stopMap[it] }.map { toScreen(it.lat, it.lng) }
                    if (rawPts.size >= 2) {
                        val laneOffset = laneOffsets[route.id] ?: 0f
                        val pts = offsetPolylinePoints(rawPts, laneOffset)
                        drawPath(
                            path = buildSmoothCatmullRomPath(pts, 0.25f),
                            color = mutedRailColor,
                            style = Stroke(
                                width = 3.6f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
                for (stop in stops) {
                    val pt = toScreen(stop.lat, stop.lng)
                    drawCircle(color = stopDotColor, radius = 4f, center = pt)
                }
            }

            // Highlighted active trip segments with smooth cubic Bezier curves + casing
            if (trip != null) {
                for (seg in trip.segments) {
                    val rawPts = seg.stopIds.mapNotNull { stopMap[it] }.map { toScreen(it.lat, it.lng) }
                    if (rawPts.size < 2) continue
                    val laneOffset = if (seg.kind == SegmentKind.TRAM && seg.route != null) {
                        laneOffsets[seg.route.id] ?: 0f
                    } else 0f
                    val pts = offsetPolylinePoints(rawPts, laneOffset)
                    val smoothPath = buildSmoothCatmullRomPath(pts, 0.28f)

                    val segColor = when (seg.kind) {
                        SegmentKind.TRAM -> runCatching {
                            Color(android.graphics.Color.parseColor(seg.route?.colorHex ?: "#005A8D"))
                        }.getOrDefault(primaryColor)
                        SegmentKind.WALK -> Color(0xFF059669)
                        SegmentKind.FALLBACK_TRANSIT -> Color(0xFFD97706)
                    }
                    val dash = if (seg.kind == SegmentKind.TRAM) null
                    else PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)

                    // Subtle surface casing underneath active segment
                    drawPath(
                        path = smoothPath,
                        color = Color.White.copy(alpha = 0.88f),
                        style = Stroke(width = 14f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    drawPath(
                        path = smoothPath,
                        color = segColor,
                        style = Stroke(
                            width = if (seg.kind == SegmentKind.TRAM) 8.5f else 6.5f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                            pathEffect = dash
                        )
                    )
                    drawCircle(color = Color.White, radius = 9f, center = pts.first())
                    drawCircle(color = segColor, radius = 6f, center = pts.first())
                }

                val startPt = toScreen(trip.startStop.lat, trip.startStop.lng)
                val endPt = toScreen(trip.endStop.lat, trip.endStop.lng)
                drawCircle(color = Color.White, radius = 14f, center = startPt)
                drawCircle(color = Color(0xFF16A34A), radius = 10f, center = startPt)
                drawCircle(color = Color.White, radius = 14f, center = endPt)
                drawCircle(color = Color(0xFFDC2626), radius = 10f, center = endPt)
            }
        }

        if (mapPickMode != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(12.dp)
            ) {
                Text(
                    text = if (mapPickMode == "start") "Нажмите на остановку старта на схеме"
                    else "Нажмите на остановку финиша на схеме",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        FilledTonalIconButton(
            onClick = {
                scale = 1f
                pan = Offset.Zero
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
        ) {
            Icon(Icons.Rounded.MyLocation, contentDescription = "Сбросить масштаб карты")
        }
    }
}
