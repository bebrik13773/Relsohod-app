package ru.relsokhod.spb.live

import ru.relsokhod.spb.model.RouteSegment
import ru.relsokhod.spb.model.TramStop
import ru.relsokhod.spb.router.TramRouter
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

data class LiveGeoPoint(
    val lat: Double,
    val lng: Double,
    val accuracyMeters: Float? = null
)

data class PolylineProjectionResult(
    val progressFraction: Float,
    val currentStopIndex: Int,
    val distanceToRouteMeters: Int
)

data class SegmentLiveProgress(
    val progressFraction: Float,
    val currentStopIndex: Int,
    val nextStopIndex: Int,
    val stopsRemaining: Int,
    val minutesRemaining: Int,
    val isGpsLocked: Boolean,
    val distanceToRouteMeters: Int?,
    val accuracyMeters: Int?,
    val stops: List<TramStop>
)

object LiveTripProgressMath {

    fun pluralizeStopsRu(count: Int): String {
        val n = kotlin.math.abs(count)
        val mod100 = n % 100
        val mod10 = n % 10
        if (mod100 in 11..14) return "остановок"
        return when (mod10) {
            1 -> "остановка"
            2, 3, 4 -> "остановки"
            else -> "остановок"
        }
    }

    fun resolveSegmentStops(
        segment: RouteSegment,
        stopsById: Map<String, TramStop>
    ): List<TramStop> {
        val resolved = mutableListOf<TramStop>()
        for (id in segment.stopIds) {
            val found = stopsById[id]
            if (found != null && resolved.lastOrNull()?.id != found.id) {
                resolved.add(found)
            }
        }
        if (resolved.isEmpty()) {
            resolved.add(segment.fromStop)
            if (segment.toStop.id != segment.fromStop.id) {
                resolved.add(segment.toStop)
            }
        } else if (resolved.size == 1 && segment.toStop.id != resolved[0].id) {
            resolved.add(segment.toStop)
        }
        return resolved
    }

    fun projectPointOntoStopsPolyline(
        coords: LiveGeoPoint,
        stops: List<TramStop>
    ): PolylineProjectionResult? {
        if (stops.isEmpty()) return null
        if (stops.size == 1) {
            val distM = (TramRouter.haversineKm(coords.lat, coords.lng, stops[0].lat, stops[0].lng) * 1000.0).roundToInt()
            return PolylineProjectionResult(0f, 0, distM)
        }

        val cumDistKm = DoubleArray(stops.size)
        for (i in 0 until stops.size - 1) {
            cumDistKm[i + 1] = cumDistKm[i] + TramRouter.haversineKm(
                stops[i].lat, stops[i].lng,
                stops[i + 1].lat, stops[i + 1].lng
            )
        }
        val totalDistKm = cumDistKm.last()
        if (totalDistKm <= 0.005) {
            val distM = (TramRouter.haversineKm(coords.lat, coords.lng, stops[0].lat, stops[0].lng) * 1000.0).roundToInt()
            return PolylineProjectionResult(0f, 0, distM)
        }

        val refLatRad = coords.lat * PI / 180.0
        val kmPerDegLat = 111.32
        val kmPerDegLng = 111.32 * maxOf(0.15, cos(refLatRad))

        var bestDistKm = Double.POSITIVE_INFINITY
        var bestProjectedDistKm = 0.0

        for (i in 0 until stops.size - 1) {
            val ax = (stops[i].lng - coords.lng) * kmPerDegLng
            val ay = (stops[i].lat - coords.lat) * kmPerDegLat
            val bx = (stops[i + 1].lng - coords.lng) * kmPerDegLng
            val by = (stops[i + 1].lat - coords.lat) * kmPerDegLat
            val abX = bx - ax
            val abY = by - ay
            val abLenSq = abX * abX + abY * abY
            val t = if (abLenSq <= 1e-10) 0.0 else ((-ax * abX + -ay * abY) / abLenSq).coerceIn(0.0, 1.0)
            val projX = ax + t * abX
            val projY = ay + t * abY
            val distKm = hypot(projX, projY)
            if (distKm < bestDistKm) {
                bestDistKm = distKm
                val segLen = cumDistKm[i + 1] - cumDistKm[i]
                bestProjectedDistKm = cumDistKm[i] + t * segLen
            }
        }

        val fraction = (bestProjectedDistKm / totalDistKm).toFloat().coerceIn(0f, 1f)
        var currentStopIdx = 0
        for (i in stops.indices) {
            if (cumDistKm[i] <= bestProjectedDistKm + 0.045) {
                currentStopIdx = i
            }
        }
        if (fraction >= 0.985f) {
            currentStopIdx = stops.lastIndex
        } else if (currentStopIdx >= stops.lastIndex) {
            currentStopIdx = (stops.lastIndex - 1).coerceAtLeast(0)
        }

        return PolylineProjectionResult(
            progressFraction = fraction,
            currentStopIndex = currentStopIdx,
            distanceToRouteMeters = (bestDistKm * 1000.0).roundToInt()
        )
    }

    fun computeSegmentLiveProgress(
        segment: RouteSegment,
        stopsById: Map<String, TramStop>,
        elapsedSeconds: Int,
        userCoords: LiveGeoPoint?,
        corridorMaxMeters: Int = 1500
    ): SegmentLiveProgress {
        val stops = resolveSegmentStops(segment, stopsById)
        val totalSeconds = maxOf(60, segment.durationMinutes * 60)
        val timerProgress = (elapsedSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
        val intervalsCount = maxOf(1, stops.size - 1)

        val gpsProj = userCoords?.let { projectPointOntoStopsPolyline(it, stops) }

        if (gpsProj != null && gpsProj.distanceToRouteMeters <= corridorMaxMeters) {
            val stopIdx = gpsProj.currentStopIndex
            val nextIdx = minOf(stops.lastIndex, stopIdx + 1)
            val remainingStops = if (gpsProj.progressFraction >= 0.985f) 0 else maxOf(1, stops.lastIndex - stopIdx)
            val remainingMins = if (gpsProj.progressFraction >= 0.985f) 0 else maxOf(
                1,
                ceil((1f - gpsProj.progressFraction) * segment.durationMinutes).toInt()
            )
            return SegmentLiveProgress(
                progressFraction = gpsProj.progressFraction,
                currentStopIndex = stopIdx,
                nextStopIndex = nextIdx,
                stopsRemaining = remainingStops,
                minutesRemaining = remainingMins,
                isGpsLocked = true,
                distanceToRouteMeters = gpsProj.distanceToRouteMeters,
                accuracyMeters = userCoords.accuracyMeters?.roundToInt(),
                stops = stops
            )
        }

        val rawIdx = (timerProgress * intervalsCount).toInt()
        val currentIdx = if (timerProgress >= 0.995f) {
            stops.lastIndex
        } else {
            rawIdx.coerceIn(0, (stops.lastIndex - 1).coerceAtLeast(0))
        }
        val nextIdx = minOf(stops.lastIndex, currentIdx + 1)
        val remainingStops = if (timerProgress >= 0.995f) 0 else maxOf(1, stops.lastIndex - currentIdx)
        val remainingMins = if (timerProgress >= 0.995f) 0 else maxOf(
            1,
            ceil((totalSeconds - elapsedSeconds).toDouble() / 60.0).toInt()
        )

        return SegmentLiveProgress(
            progressFraction = timerProgress,
            currentStopIndex = currentIdx,
            nextStopIndex = nextIdx,
            stopsRemaining = remainingStops,
            minutesRemaining = remainingMins,
            isGpsLocked = false,
            distanceToRouteMeters = gpsProj?.distanceToRouteMeters,
            accuracyMeters = userCoords?.accuracyMeters?.roundToInt(),
            stops = stops
        )
    }
}
