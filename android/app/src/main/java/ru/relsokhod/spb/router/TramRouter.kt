package ru.relsokhod.spb.router

import ru.relsokhod.spb.model.CityId
import ru.relsokhod.spb.model.DurationPreset
import ru.relsokhod.spb.model.GeneratedTrip
import ru.relsokhod.spb.model.GenerationOutcome
import ru.relsokhod.spb.model.Landmark
import ru.relsokhod.spb.model.LandmarkCategory
import ru.relsokhod.spb.model.MatchedAudioGuide
import ru.relsokhod.spb.model.ModeBalance
import ru.relsokhod.spb.model.OfficialAudioGuide
import ru.relsokhod.spb.model.PassportBadge
import ru.relsokhod.spb.model.RouteSegment
import ru.relsokhod.spb.model.RouteThemeFilter
import ru.relsokhod.spb.model.SavedRoute
import ru.relsokhod.spb.model.SavedRoutesStats
import ru.relsokhod.spb.model.SegmentKind
import ru.relsokhod.spb.model.TimePlanningMode
import ru.relsokhod.spb.model.TramRoute
import ru.relsokhod.spb.model.TramStop
import ru.relsokhod.spb.model.TripHistoryEntry
import ru.relsokhod.spb.model.WeatherMode
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.PriorityQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

object TramRouter {

    val DAY_NAMES_SHORT = listOf("Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")

    fun formatMinutesToClock(totalMinutes: Int): String {
        val norm = ((totalMinutes % 1440) + 1440) % 1440
        val h = norm / 60
        val m = norm % 60
        return String.format(Locale.US, "%02d:%02d", h, m)
    }

    fun computeDeadlineBudgetMinutes(startMinutes: Int, deadlineMinutes: Int): Int {
        var diff = deadlineMinutes - startMinutes
        if (diff <= 0) diff += 1440
        return diff.coerceIn(20, 480)
    }

    fun isCityTransitRoute(route: TramRoute?): Boolean =
        route?.type == "metro" || route?.type == "trolleybus" || route?.type == "bus"

    fun getRouteTypeShortLabel(route: TramRoute): String = when (route.type) {
        "metro" -> "Метро ${route.number}"
        "trolleybus" -> "Троллейбус № ${route.number.removePrefix("Тр-")}"
        "bus" -> "Автобус № ${route.number.removePrefix("А-")}"
        "retro" -> "Ретро-трамвай ${route.number}"
        "chizhik" -> "«Чижик» № ${route.number}"
        "circular" -> "Кольцевой трамвай № ${route.number}"
        else -> "Трамвай № ${route.number}"
    }

    fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = (lat2 - lat1) * PI / 180.0
        val dLng = (lng2 - lng1) * PI / 180.0
        val sinLat = sin(dLat / 2.0)
        val sinLng = sin(dLng / 2.0)
        val h = sinLat * sinLat + cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) * sinLng * sinLng
        return 2.0 * r * atan2(sqrt(h), sqrt(1.0 - h))
    }

    fun isRouteActiveAt(route: TramRoute, dayOfWeek: Int, timeMinutes: Int): Boolean {
        val s = route.schedule
        if (dayOfWeek in s.daysOfWeek && timeMinutes in s.startMinutes..s.endMinutes) {
            return true
        }
        if (s.endMinutes >= 1440 && timeMinutes <= s.endMinutes - 1440) {
            val prevDay = (dayOfWeek + 6) % 7
            if (prevDay in s.daysOfWeek) return true
        }
        return false
    }

    fun findNearestStops(
        lat: Double,
        lng: Double,
        stops: List<TramStop>,
        limit: Int = 3,
        includeIsolated: Boolean = false
    ): List<Pair<TramStop, Double>> {
        return stops
            .filter { includeIsolated || !it.isolatedFromMainTram }
            .map { it to haversineKm(lat, lng, it.lat, it.lng) }
            .sortedBy { it.second }
            .take(limit)
    }

    private const val ROAD_WINDING_SURFACE = 1.18
    private const val ROAD_WINDING_METRO = 1.12

    fun getCommercialSpeedKmh(route: TramRoute, city: CityId = CityId.SPB): Double = when (route.type) {
        "metro" -> 34.0
        "chizhik" -> 21.5
        "bus" -> if (city == CityId.OMSK) 17.2 else 14.8
        "trolleybus" -> if (city == CityId.OMSK) 16.2 else 13.4
        else -> if (city == CityId.OMSK) 15.5 else 14.2
    }

    fun estimateRealisticHopMinutes(
        route: TramRoute,
        distKm: Double,
        city: CityId = CityId.SPB,
        weatherMode: WeatherMode = WeatherMode.CLEAR
    ): Double {
        val winding = if (route.type == "metro") ROAD_WINDING_METRO else ROAD_WINDING_SURFACE
        val speedKmh = getCommercialSpeedKmh(route, city)
        val weatherMult = when {
            route.type == "metro" || weatherMode == WeatherMode.CLEAR -> 1.0
            weatherMode == WeatherMode.RAIN -> 1.12
            else -> 1.15
        }
        val dwellMins = if (route.type == "metro") 0.55 else 0.48
        val effectiveKm = maxOf(0.22, distKm * winding)
        val travelMins = (effectiveKm / speedKmh) * 60.0 * weatherMult + dwellMins
        return maxOf(1.2, travelMins)
    }

    fun estimateRealisticWalkMinutes(
        distKm: Double,
        weatherMode: WeatherMode = WeatherMode.CLEAR
    ): Double {
        val walkSpeedKmh = if (weatherMode == WeatherMode.CLEAR) 4.2 else 3.7
        val effectiveKm = distKm * ROAD_WINDING_SURFACE
        return maxOf(2.0, (effectiveKm / walkSpeedKmh) * 60.0)
    }

    fun estimateTransitWaitMinutes(
        route: TramRoute?,
        isInitialBoarding: Boolean,
        weatherMode: WeatherMode = WeatherMode.CLEAR
    ): Int {
        val weatherExtra = if (weatherMode == WeatherMode.CLEAR) 0 else 1
        if (route == null) {
            return (if (isInitialBoarding) 4 else 5) + weatherExtra
        }
        if (route.type == "metro") {
            return if (isInitialBoarding) 2 else 3
        }
        val factor = if (isInitialBoarding) 0.35 else 0.40
        val base = (route.schedule.intervalMinutes * factor).roundToInt()
        val minWait = if (isInitialBoarding) 2 else 3
        val maxWait = if (isInitialBoarding) 8 else 10
        return (base + weatherExtra).coerceIn(minWait, maxWait)
    }

    private data class GraphEdge(
        val toStopId: String,
        val kind: SegmentKind,
        val route: TramRoute? = null,
        val fallbackModeName: String? = null,
        val durationMinutes: Double,
        val distanceKm: Double = 0.0
    )

    private data class RawStep(
        val fromStopId: String,
        val toStopId: String,
        val kind: SegmentKind,
        val route: TramRoute? = null,
        val fallbackModeName: String? = null,
        val durationMinutes: Double,
        val distanceKm: Double = 0.0
    )

    private fun buildAdjacencyGraph(
        city: CityId,
        stops: List<TramStop>,
        routes: List<TramRoute>,
        dayOfWeek: Int,
        timeMinutes: Int,
        allowFallbackTransit: Boolean,
        maxWalkKm: Double,
        allowCityTransit: Boolean,
        weatherMode: WeatherMode = WeatherMode.CLEAR
    ): Pair<Map<String, MutableList<GraphEdge>>, List<TramRoute>> {
        val edges = HashMap<String, MutableList<GraphEdge>>()
        val stopMap = stops.associateBy { it.id }
        stops.forEach { edges[it.id] = mutableListOf() }

        val activeRoutes = routes.filter { isRouteActiveAt(it, dayOfWeek, timeMinutes) }
        val directTramPairs = HashSet<String>()

        for (route in activeRoutes) {
            val seq = route.stopIds
            if (route.type == "circular") {
                for (i in 0 until seq.size - 1) {
                    val a = seq[i]
                    val b = seq[i + 1]
                    val sa = stopMap[a] ?: continue
                    val sb = stopMap[b] ?: continue
                    val dist = haversineKm(sa.lat, sa.lng, sb.lat, sb.lng)
                    val hopMins = estimateRealisticHopMinutes(route, dist, city, weatherMode)
                    edges[a]?.add(GraphEdge(b, SegmentKind.TRAM, route, null, hopMins, dist))
                    directTramPairs.add("$a|$b")
                    directTramPairs.add("$b|$a")
                }
            } else {
                for (i in 0 until seq.size - 1) {
                    val a = seq[i]
                    val b = seq[i + 1]
                    val sa = stopMap[a] ?: continue
                    val sb = stopMap[b] ?: continue
                    val dist = haversineKm(sa.lat, sa.lng, sb.lat, sb.lng)
                    val hopMins = estimateRealisticHopMinutes(route, dist, city, weatherMode)
                    edges[a]?.add(GraphEdge(b, SegmentKind.TRAM, route, null, hopMins, dist))
                    edges[b]?.add(GraphEdge(a, SegmentKind.TRAM, route, null, hopMins, dist))
                    directTramPairs.add("$a|$b")
                    directTramPairs.add("$b|$a")
                }
            }
        }

        // Pedestrian transfers (sameHub) + sparse top-8 scenic walk links within maxWalkKm
        val mainStops = stops.filter { !it.isolatedFromMainTram }
        val addedWalkPairs = HashSet<String>()
        fun pushWalkPair(a: TramStop, b: TramStop, distKm: Double) {
            val key = if (a.id < b.id) "${a.id}|${b.id}" else "${b.id}|${a.id}"
            if (!addedWalkPairs.add(key)) return
            val mins = estimateRealisticWalkMinutes(distKm, weatherMode)
            edges[a.id]?.add(GraphEdge(b.id, SegmentKind.WALK, durationMinutes = mins, distanceKm = distKm))
            edges[b.id]?.add(GraphEdge(a.id, SegmentKind.WALK, durationMinutes = mins, distanceKm = distKm))
        }

        for (i in mainStops.indices) {
            val a = mainStops[i]
            val nearby = ArrayList<Pair<TramStop, Double>>()
            for (j in mainStops.indices) {
                if (i == j) continue
                val b = mainStops[j]
                val distKm = haversineKm(a.lat, a.lng, b.lat, b.lng)
                val sameHub = !a.hubId.isNullOrBlank() && a.hubId == b.hubId
                if (sameHub) {
                    pushWalkPair(a, b, distKm)
                } else if (distKm <= maxWalkKm) {
                    nearby.add(b to distKm)
                }
            }
            nearby.sortBy { it.second }
            for ((b, distKm) in nearby.take(8)) {
                pushWalkPair(a, b, distKm)
            }
        }

        // City-wide non-tram transit (metro / bus / trolleybus) connecting distant districts
        if (allowCityTransit) {
            val transitLabel = if (city == CityId.OMSK) {
                "Автобус / троллейбус (городская связка)"
            } else {
                "Метро / автобус (межрайонная связка)"
            }
            val speedKmh = if (city == CityId.OMSK) 16.8 else 15.0
            val weatherMult = if (weatherMode == WeatherMode.CLEAR) 1.0 else 1.12
            for (a in mainStops) {
                val candidates = ArrayList<Pair<TramStop, Double>>()
                for (b in mainStops) {
                    if (a.id == b.id || a.district == b.district) continue
                    if ("${a.id}|${b.id}" in directTramPairs) continue
                    val distKm = haversineKm(a.lat, a.lng, b.lat, b.lng)
                    if (distKm in 2.2..9.5) {
                        candidates.add(b to distKm)
                    }
                }
                candidates.sortBy { abs(it.second - 4.5) }
                for ((b, distKm) in candidates.take(3)) {
                    val transitMins = maxOf(
                        9.0,
                        ((distKm * ROAD_WINDING_SURFACE) / speedKmh) * 60.0 * weatherMult + 4.0
                    )
                    edges[a.id]?.add(
                        GraphEdge(b.id, SegmentKind.FALLBACK_TRANSIT, null, transitLabel, transitMins, distKm)
                    )
                }
            }
        }

        // Fallback transit bridges ONLY connect isolated tram-less stops when enabled
        if (allowFallbackTransit) {
            val weatherMult = if (weatherMode == WeatherMode.CLEAR) 1.0 else 1.12
            for (iso in stops.filter { it.isolatedFromMainTram }) {
                val closest = findNearestStops(iso.lat, iso.lng, mainStops, 2, false)
                for ((bridge, distKm) in closest) {
                    val speedKmh = if (distKm > 5.0) 24.0 else 16.5
                    val mins = maxOf(
                        9.0,
                        ((distKm * ROAD_WINDING_SURFACE) / speedKmh) * 60.0 * weatherMult + 5.0
                    )
                    val label = if (distKm > 5.0) "Автобус / пригородный экспресс" else "Автобус / метро (стыковка)"
                    edges[bridge.id]?.add(GraphEdge(iso.id, SegmentKind.FALLBACK_TRANSIT, null, label, mins, distKm))
                    edges[iso.id]?.add(GraphEdge(bridge.id, SegmentKind.FALLBACK_TRANSIT, null, label, mins, distKm))
                }
            }
        }

        return edges to activeRoutes
    }

    private fun collapseSteps(steps: List<RawStep>, stopMap: Map<String, TramStop>): List<RouteSegment> {
        val out = mutableListOf<RouteSegment>()
        val exactMinutes = mutableListOf<Double>()
        for (step in steps) {
            val prev = out.lastOrNull()
            val fromStop = stopMap[step.fromStopId] ?: continue
            val toStop = stopMap[step.toStopId] ?: continue

            if (prev != null &&
                prev.kind == SegmentKind.TRAM &&
                step.kind == SegmentKind.TRAM &&
                prev.route?.id == step.route?.id &&
                prev.toStop.id == step.fromStopId
            ) {
                val nextExact = exactMinutes.last() + step.durationMinutes
                exactMinutes[exactMinutes.lastIndex] = nextExact
                out[out.lastIndex] = prev.copy(
                    toStop = toStop,
                    stopIds = prev.stopIds + step.toStopId,
                    stopsCount = prev.stopsCount + 1,
                    durationMinutes = maxOf(2, nextExact.roundToInt())
                )
            } else if (prev != null &&
                prev.kind == SegmentKind.WALK &&
                step.kind == SegmentKind.WALK &&
                prev.toStop.id == step.fromStopId
            ) {
                val nextExact = exactMinutes.last() + step.durationMinutes
                exactMinutes[exactMinutes.lastIndex] = nextExact
                val nextDuration = maxOf(3, (nextExact + 1.0).roundToInt())
                out[out.lastIndex] = prev.copy(
                    toStop = toStop,
                    stopIds = prev.stopIds + step.toStopId,
                    stopsCount = prev.stopsCount + 1,
                    durationMinutes = nextDuration,
                    directionNote = if (nextDuration >= 12) {
                        "Пешая прогулка между остановками и районами"
                    } else {
                        "Пешая пересадка между остановками"
                    }
                )
            } else {
                val note = when (step.kind) {
                    SegmentKind.TRAM -> when (step.route?.type) {
                        "metro" -> "Метро · ${step.route.title}"
                        "trolleybus" -> "Троллейбус · ${step.route.title}"
                        "bus" -> "Автобус · ${step.route.title}"
                        "circular" -> "По кольцевому маршруту (по часовой стрелке)"
                        else -> "В направлении: ${step.route?.title.orEmpty()}"
                    }
                    SegmentKind.WALK ->
                        if (step.durationMinutes >= 12.0) "Пешая прогулка по городу"
                        else "Пешая пересадка между остановками"
                    SegmentKind.FALLBACK_TRANSIT ->
                        step.fallbackModeName ?: "Городской транспорт"
                }
                val initialMin = when (step.kind) {
                    SegmentKind.WALK -> maxOf(3, (step.durationMinutes + 1.0).roundToInt())
                    SegmentKind.FALLBACK_TRANSIT -> maxOf(8, step.durationMinutes.roundToInt())
                    SegmentKind.TRAM -> maxOf(2, step.durationMinutes.roundToInt())
                }
                exactMinutes.add(step.durationMinutes)
                out.add(
                    RouteSegment(
                        id = "seg-${out.size + 1}-${step.route?.id ?: step.kind.name}",
                        kind = step.kind,
                        route = step.route,
                        fallbackModeName = step.fallbackModeName,
                        fromStop = fromStop,
                        toStop = toStop,
                        stopIds = listOf(step.fromStopId, step.toStopId),
                        stopsCount = 1,
                        durationMinutes = initialMin,
                        directionNote = note
                    )
                )
            }
        }
        return out
    }

    private fun findPathToDestination(
        startStopId: String,
        destinationStopId: String,
        edgesByStop: Map<String, List<GraphEdge>>,
        theme: RouteThemeFilter,
        balance: ModeBalance,
        allowFallbackTransit: Boolean,
        avoidRouteIds: Set<String> = emptySet(),
        preferVariety: Boolean = false
    ): List<RawStep>? {
        data class Node(
            val stopId: String,
            val lastRouteId: String?,
            val cost: Double,
            val steps: List<RawStep>
        ) : Comparable<Node> {
            override fun compareTo(other: Node): Int = cost.compareTo(other.cost)
        }

        val bestCost = HashMap<String, Double>()
        val pq = PriorityQueue<Node>()
        pq.add(Node(startStopId, null, 0.0, emptyList()))
        bestCost["$startStopId::init"] = 0.0

        val tramMult = 1.45 - balance.tram * 0.75
        val walkMult = 1.85 - balance.walk * 1.35
        val walkPenalty = maxOf(0.0, (1.0 - balance.walk * 1.7) * 8.0)
        val cityTransitActive = allowFallbackTransit || balance.transit >= 0.20f
        val transitMult = 1.65 - balance.transit * 0.95

        while (pq.isNotEmpty()) {
            val curr = pq.poll()!!
            if (curr.stopId == destinationStopId && curr.steps.isNotEmpty()) {
                return curr.steps
            }
            val neighbors = edgesByStop[curr.stopId].orEmpty()
            for (edge in neighbors) {
                if (curr.steps.isNotEmpty() && edge.toStopId == curr.steps.last().fromStopId) continue

                var edgeCost = edge.durationMinutes.toDouble()
                val routeId = edge.route?.id
                when (edge.kind) {
                    SegmentKind.TRAM -> {
                        if (isCityTransitRoute(edge.route)) {
                            val transitFactor = maxOf(0.75, 1.45 - balance.transit * 0.85)
                            edgeCost *= transitFactor
                        } else {
                            edgeCost *= tramMult
                        }
                        if (routeId != null && routeId in avoidRouteIds) {
                            edgeCost = edgeCost * 3.4 + 18.0
                        }
                        if (curr.lastRouteId != null && curr.lastRouteId != routeId) {
                            edgeCost += if (preferVariety) 2.5 else 6.5
                        }
                        if (theme != RouteThemeFilter.ANY && edge.route?.themes?.contains(theme.id) == true) {
                            edgeCost *= 0.80
                        }
                    }
                    SegmentKind.WALK -> {
                        edgeCost = edge.durationMinutes * walkMult + walkPenalty
                    }
                    SegmentKind.FALLBACK_TRANSIT -> {
                        edgeCost = when {
                            !cityTransitActive -> edgeCost + 5000.0
                            balance.transit < 0.22f -> edge.durationMinutes * 1.8 + 40.0
                            else -> edge.durationMinutes * transitMult + 5.0
                        }
                    }
                }

                val nextCost = curr.cost + edgeCost
                val stateKey = "${edge.toStopId}::${routeId ?: edge.kind.name}"
                val prevBest = bestCost[stateKey]
                if (prevBest != null && prevBest <= nextCost) continue
                bestCost[stateKey] = nextCost

                pq.add(
                    Node(
                        stopId = edge.toStopId,
                        lastRouteId = routeId,
                        cost = nextCost,
                        steps = curr.steps + RawStep(
                            fromStopId = curr.stopId,
                            toStopId = edge.toStopId,
                            kind = edge.kind,
                            route = edge.route,
                            fallbackModeName = edge.fallbackModeName,
                            durationMinutes = edge.durationMinutes,
                            distanceKm = edge.distanceKm
                        )
                    )
                )
            }
        }
        return null
    }

    private fun computeTripModeShares(steps: List<RawStep>): ModeBalance {
        var tramMins = 0f
        var transitMins = 0f
        var walkMins = 0f
            for (s in steps) {
            when (s.kind) {
                SegmentKind.TRAM -> {
                    if (isCityTransitRoute(s.route)) transitMins += s.durationMinutes.toFloat()
                    else tramMins += s.durationMinutes.toFloat()
                }
                SegmentKind.WALK -> walkMins += s.durationMinutes.toFloat()
                SegmentKind.FALLBACK_TRANSIT -> transitMins += s.durationMinutes.toFloat()
            }
        }
        val total = tramMins + transitMins + walkMins
        if (total <= 0f) return ModeBalance(1f, 0f, 0f)
        return ModeBalance(
            tram = tramMins / total,
            transit = transitMins / total,
            walk = walkMins / total
        )
    }

    private data class CandidateWalk(
        val steps: List<RawStep>,
        val score: Double,
        val duration: Int,
        val walkRatio: Float,
        val distinctLines: Int,
        val signature: String
    )

    private fun getStepsSignature(steps: List<RawStep>): String {
        val segKeys = mutableListOf<String>()
        for (s in steps) {
            val key = if (s.kind == SegmentKind.TRAM) (s.route?.id ?: "tram") else s.kind.name
            if (segKeys.lastOrNull() != key) {
                segKeys.add(key)
            }
        }
        val endId = steps.lastOrNull()?.toStopId ?: ""
        return "${segKeys.joinToString(">")}|$endId"
    }

    private fun durationRange(preset: DurationPreset): Pair<Int, Int> = when (preset) {
        DurationPreset.SHORT -> 16 to 44
        DurationPreset.MEDIUM -> 42 to 86
        DurationPreset.LONG -> 80 to 180
        DurationPreset.ANY -> 22 to 165
    }

    private fun runAdventureRollouts(
        startStop: TramStop,
        stopMap: Map<String, TramStop>,
        edgesByStop: Map<String, List<GraphEdge>>,
        minMins: Int,
        maxMins: Int,
        theme: RouteThemeFilter,
        balance: ModeBalance,
        allowCityTransit: Boolean,
        allowFallbackTransit: Boolean,
        effectiveIsolatedFallback: Boolean,
        loopMode: Boolean,
        weatherMode: WeatherMode,
        preferSpecialFleet: Boolean,
        rand: Random,
        attempts: Int = 150
    ): List<CandidateWalk> {
        val candidates = mutableListOf<CandidateWalk>()
        val maxHops = when {
            maxMins <= 45 -> 10
            maxMins <= 95 -> 17
            else -> 26
        }

        for (attempt in 0 until attempts) {
            val steps = mutableListOf<RawStep>()
            val visited = HashMap<String, Int>().apply { put(startStop.id, 1) }
            val usedRouteIds = HashSet<String>()
            var currId = startStop.id
            var currRouteId: String? = null
            var totalDuration = 0.0
            var transfers = 0
            val exploreMode = attempt % 4

            for (hop in 0 until maxHops) {
                val neighbors = edgesByStop[currId].orEmpty().filter { e ->
                    if (e.kind != SegmentKind.FALLBACK_TRANSIT) true
                    else if (!allowCityTransit && !effectiveIsolatedFallback) false
                    else {
                        val target = stopMap[e.toStopId]
                        !(target?.isolatedFromMainTram == true && !effectiveIsolatedFallback)
                    }
                }
                if (neighbors.isEmpty()) break

                val currStopObj = stopMap[currId] ?: startStop
                val currDistFromStart = haversineKm(startStop.lat, startStop.lng, currStopObj.lat, currStopObj.lng)
                val currShares = computeTripModeShares(steps)

                val weighted = neighbors.map { edge ->
                    var w = 1.0
                    val nextStop = stopMap[edge.toStopId]
                    val visitCount = visited[edge.toStopId] ?: 0

                    if (steps.isNotEmpty() && edge.toStopId == steps.last().fromStopId) w *= 0.004

                    val recentStops = steps.takeLast(3).map { it.fromStopId }
                    val completingCircular = edge.route?.type == "circular" && edge.toStopId == startStop.id && steps.size >= 4
                    val completingWalkLoop = loopMode && edge.toStopId == startStop.id && steps.size >= 4

                    if (completingWalkLoop) {
                        w *= 18.0
                    } else if (visitCount > 0 && !completingCircular) {
                        w *= if (edge.toStopId in recentStops) 0.008 else 0.04
                    }

                    val nextDistFromStart = if (nextStop != null) {
                        haversineKm(startStop.lat, startStop.lng, nextStop.lat, nextStop.lng)
                    } else currDistFromStart

                    if (!loopMode || totalDuration < minMins * 0.55) {
                        if (nextDistFromStart > currDistFromStart + 0.15) w *= 1.45
                    } else if (loopMode && totalDuration >= minMins * 0.55) {
                        if (nextDistFromStart < currDistFromStart) w *= 2.1
                    }

                    val tramDeficit = maxOf(0.05, (balance.tram - currShares.tram + 0.35).toDouble())
                    val walkDeficit = maxOf(0.04, (balance.walk - currShares.walk + 0.25).toDouble())
                    val transitDeficit = maxOf(0.02, (balance.transit - currShares.transit + 0.22).toDouble())

                    when (edge.kind) {
                        SegmentKind.TRAM -> {
                            val isTransitVeh = isCityTransitRoute(edge.route)
                            if (isTransitVeh) {
                                val transitBoost = if (exploreMode == 3) 1.8 else 1.0
                                w *= (0.28 + balance.transit * 2.8) * (1.0 + transitDeficit) * transitBoost
                            } else {
                                w *= (0.4 + balance.tram * 2.8) * (1.0 + tramDeficit)
                            }
                            if (currRouteId != null && edge.route?.id == currRouteId) {
                                w *= if (exploreMode == 1) 2.3 else 4.1
                            } else if (currRouteId != null && edge.route?.id != currRouteId) {
                                w *= if (edge.route != null && edge.route.id in usedRouteIds) {
                                    0.12
                                } else {
                                    when {
                                        transfers >= 4 -> 0.32
                                        exploreMode == 1 -> 1.45
                                        else -> 1.05
                                    }
                                }
                            }
                            if (theme != RouteThemeFilter.ANY && edge.route?.themes?.contains(theme.id) == true) {
                                w *= 3.8
                            }
                            if (preferSpecialFleet && edge.route != null &&
                                (edge.route.type == "retro" || edge.route.type == "chizhik" || edge.route.type == "circular")
                            ) {
                                w *= 4.6
                            }
                            if (weatherMode == WeatherMode.RAIN || weatherMode == WeatherMode.FROST) {
                                if ((edge.route?.schedule?.intervalMinutes ?: 99) <= 11) w *= 1.7
                            } else if (weatherMode == WeatherMode.CLEAR &&
                                edge.route?.themes?.any { it == "parks" || it == "maritime" } == true
                            ) {
                                w *= 1.35
                            }
                        }
                        SegmentKind.WALK -> {
                            val prevWasWalk = steps.isNotEmpty() && steps.last().kind == SegmentKind.WALK
                            val walkMultMode = if (exploreMode == 2) 1.45 else 1.0
                            val walkBase = if (balance.walk < 0.12f) {
                                0.22
                            } else {
                                (0.4 + balance.walk * 4.4) * (1.0 + walkDeficit * 2.2) * walkMultMode
                            }
                            w *= walkBase
                            if (edge.distanceKm >= 0.65 && (balance.walk >= 0.22f || exploreMode == 2)) w *= 2.4
                            if (prevWasWalk && balance.walk < 0.55f) w *= 0.35
                            if (weatherMode == WeatherMode.RAIN || weatherMode == WeatherMode.FROST) {
                                w *= if (balance.walk >= 0.50f) 0.65 else 0.25
                            }
                        }
                        SegmentKind.FALLBACK_TRANSIT -> {
                            val prevWasTransit = steps.isNotEmpty() && steps.last().kind == SegmentKind.FALLBACK_TRANSIT
                            val transitBase = if (balance.transit < 0.18f) {
                                0.04
                            } else {
                                (0.35 + balance.transit * 4.4) * (1.0 + transitDeficit * 2.3)
                            }
                            w *= transitBase
                            if (prevWasTransit && balance.transit < 0.60f) w *= 0.20
                        }
                    }
                    edge to w
                }

                val totalW = weighted.sumOf { it.second }
                if (totalW <= 0.0) break

                var pick = rand.nextDouble() * totalW
                var chosen = weighted.first().first
                for ((edge, weight) in weighted) {
                    pick -= weight
                    if (pick <= 0.0) {
                        chosen = edge
                        break
                    }
                }

                var legWait = 0
                if (chosen.kind != SegmentKind.WALK) {
                    val isFirstTransit = steps.none { it.kind != SegmentKind.WALK }
                    val isNewRoute = isFirstTransit ||
                        chosen.kind == SegmentKind.FALLBACK_TRANSIT ||
                        (chosen.kind == SegmentKind.TRAM && currRouteId != null && chosen.route?.id != currRouteId)
                    if (isNewRoute) {
                        legWait = estimateTransitWaitMinutes(chosen.route, isFirstTransit, weatherMode)
                    }
                }

                if (steps.size >= 2 && totalDuration + chosen.durationMinutes + legWait > maxMins + 6.0) {
                    break
                }

                if (chosen.kind == SegmentKind.TRAM && currRouteId != null && chosen.route?.id != currRouteId) {
                    transfers++
                }
                steps.add(
                    RawStep(
                        fromStopId = currId,
                        toStopId = chosen.toStopId,
                        kind = chosen.kind,
                        route = chosen.route,
                        fallbackModeName = chosen.fallbackModeName,
                        durationMinutes = chosen.durationMinutes,
                        distanceKm = chosen.distanceKm
                    )
                )
                totalDuration += chosen.durationMinutes + legWait
                if (chosen.kind == SegmentKind.TRAM && chosen.route != null) {
                    currRouteId = chosen.route.id
                    usedRouteIds.add(chosen.route.id)
                }
                currId = chosen.toStopId
                visited[currId] = (visited[currId] ?: 0) + 1

                if (loopMode && currId == startStop.id && steps.size >= 4) break
                if (totalDuration >= maxMins) break
                if (!loopMode && totalDuration >= minMins && rand.nextDouble() < 0.26 && steps.size >= 3) break
            }

            if (loopMode && steps.size >= 2 && steps.last().toStopId != startStop.id) {
                val back = findPathToDestination(
                    startStopId = steps.last().toStopId,
                    destinationStopId = startStop.id,
                    edgesByStop = edgesByStop,
                    theme = theme,
                    balance = balance,
                    allowFallbackTransit = allowFallbackTransit
                )
                if (back != null && back.size in 1..8) {
                    val valid = allowCityTransit || back.none { it.kind == SegmentKind.FALLBACK_TRANSIT }
                    if (valid) {
                        for (bs in back) {
                            steps.add(bs)
                            totalDuration += bs.durationMinutes
                        }
                    }
                }
            }

            while (steps.size > 2 && steps.last().kind == SegmentKind.WALK && balance.walk < 0.55f) {
                totalDuration -= steps.removeAt(steps.lastIndex).durationMinutes
            }
            if (steps.size < 2) continue

            val segs = collapseSteps(steps, stopMap)
            val tramSegs = segs.filter { it.kind == SegmentKind.TRAM }
            val walkSegs = segs.filter { it.kind == SegmentKind.WALK }
            val transitSegs = segs.filter { it.kind == SegmentKind.FALLBACK_TRANSIT }
            if (tramSegs.isEmpty() && balance.tram >= 0.45f) continue

            val actualShares = computeTripModeShares(steps)
            val shareError = abs(actualShares.tram - balance.tram) +
                abs(actualShares.transit - balance.transit) +
                abs(actualShares.walk - balance.walk)
            val modeFitScore = maxOf(0.0, 1.0 - shareError * 0.5) * 145.0

            val districts = segs.flatMap { listOf(it.fromStop.district, it.toStop.district) }.toSet()
            val distinctTramRoutes = tramSegs.mapNotNull { it.route?.id }.toSet().size
            val themeMatches = tramSegs.count { theme == RouteThemeFilter.ANY || it.route?.themes?.contains(theme.id) == true }
            val maxSpreadKm = segs.maxOfOrNull {
                haversineKm(startStop.lat, startStop.lng, it.toStop.lat, it.toStop.lng)
            } ?: 0.0

            var score = modeFitScore +
                districts.size * 15.0 +
                distinctTramRoutes * 18.0 +
                themeMatches * 14.0 +
                minOf(8.0, maxSpreadKm) * 5.0

            if (balance.walk >= 0.28f && walkSegs.isNotEmpty()) score += 36.0
            if (balance.transit >= 0.25f && (transitSegs.isNotEmpty() || actualShares.transit >= 0.20f)) score += 36.0

            val singleHopTrams = tramSegs.count { it.stopsCount <= 1 }
            score -= singleHopTrams * 8.0
            if (tramSegs.size > distinctTramRoutes) {
                score -= (tramSegs.size - distinctTramRoutes) * 20.0
            }

            if (loopMode) {
                if (segs.last().toStop.id == startStop.id) score += 190.0
                if (distinctTramRoutes >= 2) score += 45.0
            }
            if (preferSpecialFleet) {
                score += tramSegs.count { it.route?.type == "retro" || it.route?.type == "chizhik" } * 35.0
            }
            val transitLegs = segs.filter { it.kind == SegmentKind.TRAM || it.kind == SegmentKind.FALLBACK_TRANSIT }
            val assembledWait = transitLegs.mapIndexed { idx, seg ->
                estimateTransitWaitMinutes(seg.route, idx == 0, weatherMode)
            }.sum()
            val assembledDuration = segs.sumOf { it.durationMinutes } + assembledWait

            score += when {
                assembledDuration in minMins..maxMins -> 45.0
                assembledDuration > maxMins -> -(assembledDuration - maxMins) * 3.5
                else -> -abs(assembledDuration - (minMins + maxMins) / 2.0) * 0.65
            }
            candidates.add(
                CandidateWalk(
                    steps = steps,
                    score = score,
                    duration = assembledDuration,
                    walkRatio = actualShares.walk,
                    distinctLines = distinctTramRoutes,
                    signature = getStepsSignature(steps)
                )
            )
        }
        return candidates.sortedByDescending { it.score }
    }

    fun generateTramAdventure(
        city: CityId,
        stops: List<TramStop>,
        routes: List<TramRoute>,
        landmarks: List<Landmark>,
        startStopId: String,
        destinationStopId: String? = null,
        theme: RouteThemeFilter = RouteThemeFilter.ANY,
        durationPreset: DurationPreset = DurationPreset.MEDIUM,
        timeMode: TimePlanningMode = TimePlanningMode.DURATION,
        deadlineMinutes: Int = 18 * 60,
        includePlanned2027: Boolean = true,
        dayOfWeek: Int = 3,
        timeMinutes: Int = 14 * 60,
        allowFallbackTransit: Boolean = false,
        modeBalance: ModeBalance = ModeBalance(),
        loopMode: Boolean = false,
        weatherMode: WeatherMode = WeatherMode.CLEAR,
        preferSpecialFleet: Boolean = false,
        generateAlternatives: Boolean = true,
        seed: Long? = null
    ): GenerationOutcome {
        val visibleStops = if (includePlanned2027) stops else stops.filter { !it.planned2027 }
        val visibleStopIds = visibleStops.map { it.id }.toSet()
        val visibleRoutes = if (includePlanned2027) {
            routes
        } else {
            routes.filter { !it.planned2027 && it.stopIds.all { sid -> sid in visibleStopIds } }
        }

        val balance = modeBalance.normalized()
        val stopMap = visibleStops.associateBy { it.id }
        val startStop = stopMap[startStopId]
            ?: return GenerationOutcome.NoService("Выбранная стартовая остановка не найдена (возможно, она относится к линии 2027 года).")

        val targetDeadlineMinutes = if (timeMode == TimePlanningMode.DEADLINE) {
            ((deadlineMinutes % 1440) + 1440) % 1440
        } else null
        val deadlineBudget = targetDeadlineMinutes?.let { computeDeadlineBudgetMinutes(timeMinutes, it) }

        val maxWalkKm = when {
            balance.walk >= 0.35f || (deadlineBudget != null && deadlineBudget >= 110) -> 2.8
            balance.walk >= 0.16f -> 1.65
            else -> 0.72
        }
        val allowCityTransit = balance.transit >= 0.20f
        val effectiveIsolatedFallback = allowFallbackTransit || balance.transit >= 0.28f

        val (edgesByStop, activeRoutes) = buildAdjacencyGraph(
            city = city,
            stops = visibleStops,
            routes = visibleRoutes,
            dayOfWeek = dayOfWeek,
            timeMinutes = timeMinutes,
            allowFallbackTransit = effectiveIsolatedFallback,
            maxWalkKm = maxWalkKm,
            allowCityTransit = allowCityTransit,
            weatherMode = weatherMode
        )

        if (activeRoutes.isEmpty() && balance.walk < 0.65f) {
            return GenerationOutcome.NoService(
                "В выбранное время (${formatMinutesToClock(timeMinutes)}) транспорт завершил движение. Выберите дневное время или сдвиньте треугольник к «Пешком»."
            )
        }

        if (!destinationStopId.isNullOrBlank() && destinationStopId != startStopId && !loopMode) {
            val destStop = stopMap[destinationStopId]
                ?: return GenerationOutcome.NoService("Конечная остановка не найдена.")
            val baseSteps = findPathToDestination(
                startStopId = startStopId,
                destinationStopId = destinationStopId,
                edgesByStop = edgesByStop,
                theme = theme,
                balance = balance,
                allowFallbackTransit = allowFallbackTransit
            )
            if (baseSteps == null) {
                val nearest = findNearestStops(destStop.lat, destStop.lng, visibleStops, 1, false).firstOrNull()?.first
                return GenerationOutcome.NeedsFallback(
                    "До точки «${destStop.name}» нельзя добраться только на рельсах и пешком. Включите резервный транспорт или сдвиньте треугольник в сторону «Транспорт».",
                    nearest
                )
            }

            fun buildBaseTrip(steps: List<RawStep>, variantTitle: String, variantBadge: String): GeneratedTrip {
                val segs = collapseSteps(steps, stopMap)
                return assembleTrip(
                    city = city,
                    segments = segs,
                    allLandmarks = landmarks,
                    stopMap = stopMap,
                    dayOfWeek = dayOfWeek,
                    timeMinutes = timeMinutes,
                    seed = seed,
                    isLoop = false,
                    weatherMode = weatherMode,
                    modeBalance = balance,
                    variantTitle = variantTitle,
                    variantBadge = variantBadge,
                    targetDeadlineMinutes = targetDeadlineMinutes
                )
            }

            val altTrips = mutableListOf<GeneratedTrip>()
            val seenSignatures = HashSet<String>()
            fun pushAlt(steps: List<RawStep>?, variantTitle: String, variantBadge: String) {
                if (steps.isNullOrEmpty()) return
                val sig = getStepsSignature(steps)
                if (!seenSignatures.add(sig)) return
                altTrips.add(buildBaseTrip(steps, variantTitle, variantBadge))
            }

            val baseTrip = buildBaseTrip(baseSteps, "Оптимальный путь", "Прямой")

            if (generateAlternatives) {
                if (!destStop.isolatedFromMainTram) {
                    val adjacentStops = visibleStops
                        .filter { !it.isolatedFromMainTram && it.id != destStop.id && it.id != startStop.id }
                        .map { it to haversineKm(it.lat, it.lng, destStop.lat, destStop.lng) }
                        .filter { it.second in 0.85..3.4 }
                        .sortedBy { abs(it.second - 1.75) }

                    for ((candStop, distKm) in adjacentStops.take(6)) {
                        val toNeighbor = findPathToDestination(
                            startStopId = startStopId,
                            destinationStopId = candStop.id,
                            edgesByStop = edgesByStop,
                            theme = theme,
                            balance = balance.copy(walk = minOf(balance.walk, 0.25f)).normalized(),
                            allowFallbackTransit = allowFallbackTransit,
                            preferVariety = true
                        )
                        if (!toNeighbor.isNullOrEmpty()) {
                            val walkMins = maxOf(10.0, estimateRealisticWalkMinutes(distKm, weatherMode) + 1.0)
                            val combined = toNeighbor + RawStep(
                                fromStopId = candStop.id,
                                toStopId = destStop.id,
                                kind = SegmentKind.WALK,
                                durationMinutes = walkMins,
                                distanceKm = distKm
                            )
                            if (getStepsSignature(combined) != getStepsSignature(baseSteps)) {
                                val kmLabel = (distKm * 10.0).roundToInt() / 10.0
                                pushAlt(combined, "Выход на соседней + пешком", "+$kmLabel км пешком")
                                break
                            }
                        }
                    }
                }

                val baseRouteIds = baseSteps.mapNotNull { it.route?.id }.toSet()
                if (baseRouteIds.isNotEmpty()) {
                    val variedSteps = findPathToDestination(
                        startStopId = startStopId,
                        destinationStopId = destinationStopId,
                        edgesByStop = edgesByStop,
                        theme = theme,
                        balance = ModeBalance(0.45f, maxOf(0.30f, balance.transit), 0.25f).normalized(),
                        allowFallbackTransit = allowFallbackTransit,
                        avoidRouteIds = baseRouteIds,
                        preferVariety = true
                    )
                    pushAlt(variedSteps, "Разными линиями", "Без скуки")
                }

                if (deadlineBudget != null &&
                    baseTrip.totalDurationMinutes < deadlineBudget * 0.65 &&
                    !destStop.isolatedFromMainTram
                ) {
                    val detourMax = maxOf(25, deadlineBudget - baseTrip.totalDurationMinutes - 14)
                    val detourMin = maxOf(18, (detourMax * 0.55).roundToInt())
                    val detourRand = Random(seed ?: 42L)
                    val detours = runAdventureRollouts(
                        startStop = startStop,
                        stopMap = stopMap,
                        edgesByStop = edgesByStop,
                        minMins = detourMin,
                        maxMins = detourMax,
                        theme = theme,
                        balance = balance,
                        allowCityTransit = allowCityTransit,
                        allowFallbackTransit = allowFallbackTransit,
                        effectiveIsolatedFallback = effectiveIsolatedFallback,
                        loopMode = false,
                        weatherMode = weatherMode,
                        preferSpecialFleet = preferSpecialFleet,
                        rand = detourRand,
                        attempts = 45
                    )
                    for (d in detours.take(6)) {
                        val tailStopId = d.steps.last().toStopId
                        if (tailStopId == destinationStopId) continue
                        val tailToDest = findPathToDestination(
                            startStopId = tailStopId,
                            destinationStopId = destinationStopId,
                            edgesByStop = edgesByStop,
                            theme = theme,
                            balance = balance,
                            allowFallbackTransit = allowFallbackTransit
                        )
                        if (!tailToDest.isNullOrEmpty()) {
                            val fullSteps = d.steps + tailToDest
                            val totalEst = fullSteps.sumOf { it.durationMinutes }.roundToInt() + 10
                            if (totalEst <= deadlineBudget - 4) {
                                pushAlt(
                                    fullSteps,
                                    "Большая прогулка к ${formatMinutesToClock(targetDeadlineMinutes!!)}",
                                    "По времени"
                                )
                                break
                            }
                        }
                    }
                }
            }

            if (seenSignatures.add(getStepsSignature(baseSteps))) {
                altTrips.add(0, baseTrip)
            } else if (altTrips.isEmpty()) {
                altTrips.add(baseTrip)
            }

            val trimmedAlts = altTrips.take(3).map { it.copy(alternatives = emptyList()) }
            val primary = trimmedAlts.first()
            return GenerationOutcome.Ok(primary.copy(alternatives = trimmedAlts))
        }

        val startEdges = edgesByStop[startStopId].orEmpty()
        if (startEdges.isEmpty()) {
            return GenerationOutcome.NoService(
                "От остановки «${startStop.name}» в ${formatMinutesToClock(timeMinutes)} (${DAY_NAMES_SHORT[dayOfWeek]}) нет работающих рейсов. Попробуйте другую остановку или дневное время."
            )
        }

        val rand = if (seed != null) Random(seed) else Random.Default
        var (minMins, maxMins) = durationRange(durationPreset)
        if (deadlineBudget != null) {
            val buffer = minOf(26, maxOf(10, (deadlineBudget * 0.14).roundToInt()))
            maxMins = maxOf(24, deadlineBudget - buffer)
            minMins = maxOf(16, (deadlineBudget * 0.62).roundToInt())
        }

        val candidates = runAdventureRollouts(
            startStop = startStop,
            stopMap = stopMap,
            edgesByStop = edgesByStop,
            minMins = minMins,
            maxMins = maxMins,
            theme = theme,
            balance = balance,
            allowCityTransit = allowCityTransit,
            allowFallbackTransit = allowFallbackTransit,
            effectiveIsolatedFallback = effectiveIsolatedFallback,
            loopMode = loopMode,
            weatherMode = weatherMode,
            preferSpecialFleet = preferSpecialFleet,
            rand = rand,
            attempts = 150
        )

        if (candidates.isEmpty()) {
            return GenerationOutcome.NoService(
                "Не удалось построить маршрут из этой точки в выбранное время. Попробуйте сменить остановку или фильтр времени."
            )
        }

        val eligiblePool = if (deadlineBudget != null) {
            candidates.filter { it.duration <= deadlineBudget - 2 }.ifEmpty { candidates }
        } else {
            candidates
        }

        val pool = eligiblePool.take(minOf(3, eligiblePool.size))
        val pickedPrimary = pool[rand.nextInt(pool.size)]

        val selected = mutableListOf(
            Triple(
                pickedPrimary,
                if (targetDeadlineMinutes != null) "К ${formatMinutesToClock(targetDeadlineMinutes)}" else "Баланс по параметрам",
                "Рекомендуемый"
            )
        )
        val usedSignatures = HashSet<String>().apply { add(pickedPrimary.signature) }

        if (generateAlternatives) {
            val walkCand = candidates
                .filter { it.signature !in usedSignatures }
                .sortedWith(compareByDescending<CandidateWalk> { it.walkRatio }.thenByDescending { it.score })
                .firstOrNull()
            if (walkCand != null) {
                usedSignatures.add(walkCand.signature)
                selected.add(Triple(walkCand, "Больше пешком", "Прогулочный"))
            }

            val diverseCand = candidates
                .filter { it.signature !in usedSignatures }
                .sortedWith(compareByDescending<CandidateWalk> { it.distinctLines }.thenByDescending { it.score })
                .firstOrNull()
            if (diverseCand != null) {
                usedSignatures.add(diverseCand.signature)
                selected.add(Triple(diverseCand, "Разными линиями", "Разнообразие"))
            }
        }

        val builtAlts = selected.take(3).map { (cand, title, badge) ->
            val segs = collapseSteps(cand.steps, stopMap)
            assembleTrip(
                city = city,
                segments = segs,
                allLandmarks = landmarks,
                stopMap = stopMap,
                dayOfWeek = dayOfWeek,
                timeMinutes = timeMinutes,
                seed = seed,
                isLoop = loopMode && segs.last().toStop.id == startStopId,
                weatherMode = weatherMode,
                modeBalance = balance,
                variantTitle = title,
                variantBadge = badge,
                targetDeadlineMinutes = targetDeadlineMinutes
            ).copy(alternatives = emptyList())
        }

        val primaryTrip = builtAlts.first()
        return GenerationOutcome.Ok(primaryTrip.copy(alternatives = builtAlts))
    }

    fun extendTripLastSegment(
        trip: GeneratedTrip,
        stops: List<TramStop>,
        routes: List<TramRoute>,
        landmarks: List<Landmark>,
        allowFallbackTransit: Boolean,
        includePlanned2027: Boolean = true,
        weatherMode: WeatherMode,
        preferSpecialFleet: Boolean,
        seed: Long? = null
    ): GeneratedTrip? {
        val visibleStops = if (includePlanned2027) stops else stops.filter { !it.planned2027 }
        val visibleStopIds = visibleStops.map { it.id }.toSet()
        val visibleRoutes = if (includePlanned2027) {
            routes
        } else {
            routes.filter { !it.planned2027 && it.stopIds.all { sid -> sid in visibleStopIds } }
        }

        val balance = trip.modeBalance.normalized()
        val stopMap = visibleStops.associateBy { it.id }
        val allowCityTransit = balance.transit >= 0.20f
        val (edgesByStop, _) = buildAdjacencyGraph(
            city = trip.city,
            stops = visibleStops,
            routes = visibleRoutes,
            dayOfWeek = trip.dayOfWeek,
            timeMinutes = trip.timeMinutes,
            allowFallbackTransit = allowFallbackTransit,
            maxWalkKm = if (balance.walk >= 0.35f) 2.2 else 1.1,
            allowCityTransit = allowCityTransit,
            weatherMode = weatherMode
        )
        val rand = if (seed != null) Random(seed) else Random.Default
        val lastRouteId = trip.segments.lastOrNull()?.route?.id
        val visited = trip.segments.flatMap { it.stopIds }.toMutableSet()

        var currId = trip.endStop.id
        val addedSteps = mutableListOf<RawStep>()
        var chosenRouteId: String? = null
        val targetHops = 2 + rand.nextInt(3)

        for (hop in 0 until targetHops) {
            val rawNeighbors = edgesByStop[currId].orEmpty().filter {
                allowCityTransit || allowFallbackTransit || it.kind != SegmentKind.FALLBACK_TRANSIT
            }
            if (rawNeighbors.isEmpty()) break

            val preferred = rawNeighbors.filter { edge ->
                (hop == 0 && edge.kind == SegmentKind.TRAM && edge.route?.id != lastRouteId) ||
                    (hop > 0 && chosenRouteId != null && edge.route?.id == chosenRouteId)
            }
            val pool = when {
                preferred.isNotEmpty() -> preferred
                else -> rawNeighbors.filter { it.kind == SegmentKind.TRAM && it.toStopId !in visited }
            }.ifEmpty { rawNeighbors }

            val chosen = pool.maxByOrNull { edge ->
                var s = 0.0
                if (edge.toStopId !in visited) s += 10.0
                if (edge.kind == SegmentKind.TRAM && edge.route?.id != lastRouteId) s += 8.0
                if (edge.kind == SegmentKind.WALK && balance.walk >= 0.35f) s += 5.0
                if (preferSpecialFleet && edge.route != null && edge.route.type != "regular") s += 6.0
                if ((weatherMode == WeatherMode.RAIN || weatherMode == WeatherMode.FROST) && edge.kind == SegmentKind.WALK) s -= 6.0
                s + rand.nextDouble() * 3.0
            } ?: break

            if (chosen.kind == SegmentKind.TRAM && chosenRouteId == null) {
                chosenRouteId = chosen.route?.id
            }
            addedSteps.add(
                RawStep(
                    fromStopId = currId,
                    toStopId = chosen.toStopId,
                    kind = chosen.kind,
                    route = chosen.route,
                    fallbackModeName = chosen.fallbackModeName,
                    durationMinutes = chosen.durationMinutes,
                    distanceKm = chosen.distanceKm
                )
            )
            visited.add(chosen.toStopId)
            currId = chosen.toStopId
        }

        while (addedSteps.isNotEmpty() && addedSteps.last().kind == SegmentKind.WALK && balance.walk < 0.55f) {
            addedSteps.removeAt(addedSteps.lastIndex)
        }
        if (addedSteps.isEmpty()) return null

        val extra = collapseSteps(addedSteps, stopMap)
        val combined = trip.segments + extra
        val isLoopClosed = combined.last().toStop.id == combined.first().fromStop.id
        return assembleTrip(
            city = trip.city,
            segments = combined,
            allLandmarks = landmarks,
            stopMap = stopMap,
            dayOfWeek = trip.dayOfWeek,
            timeMinutes = trip.timeMinutes,
            seed = seed,
            isLoop = isLoopClosed,
            weatherMode = weatherMode,
            modeBalance = balance,
            variantTitle = trip.variantTitle,
            variantBadge = trip.variantBadge,
            targetDeadlineMinutes = trip.targetDeadlineMinutes
        )
    }

    private fun assembleTrip(
        city: CityId,
        segments: List<RouteSegment>,
        allLandmarks: List<Landmark>,
        stopMap: Map<String, TramStop>,
        dayOfWeek: Int,
        timeMinutes: Int,
        seed: Long?,
        isLoop: Boolean,
        weatherMode: WeatherMode,
        modeBalance: ModeBalance,
        variantTitle: String? = null,
        variantBadge: String? = null,
        targetDeadlineMinutes: Int? = null
    ): GeneratedTrip {
        val normalized = segments.mapIndexed { idx, seg ->
            seg.copy(id = "seg-${idx + 1}-${seg.route?.id ?: seg.kind.name}")
        }
        val tramSegs = normalized.filter { it.kind == SegmentKind.TRAM }
        val transitLegs = normalized.filter { it.kind == SegmentKind.TRAM || it.kind == SegmentKind.FALLBACK_TRANSIT }
        val transfers = maxOf(0, normalized.size - 1)
        val boardingAndTransferWait = transitLegs.mapIndexed { idx, seg ->
            estimateTransitWaitMinutes(seg.route, idx == 0, weatherMode)
        }.sum()
        val totalDuration = normalized.sumOf { it.durationMinutes } + boardingAndTransferWait

        var walkKmRaw = 0.0
        for (seg in normalized) {
            if (seg.kind != SegmentKind.WALK) continue
            if (seg.stopIds.size >= 2) {
                for (i in 0 until seg.stopIds.size - 1) {
                    val a = stopMap[seg.stopIds[i]]
                    val b = stopMap[seg.stopIds[i + 1]]
                    walkKmRaw += if (a != null && b != null) haversineKm(a.lat, a.lng, b.lat, b.lng)
                    else haversineKm(seg.fromStop.lat, seg.fromStop.lng, seg.toStop.lat, seg.toStop.lng)
                }
            } else {
                walkKmRaw += haversineKm(seg.fromStop.lat, seg.fromStop.lng, seg.toStop.lat, seg.toStop.lng)
            }
        }
        val walkDistanceKm = (walkKmRaw * 10.0).roundToInt() / 10.0

        val stopIds = normalized.flatMap { it.stopIds }.toSet()
        val districts = normalized.flatMap { listOf(it.fromStop.district, it.toStop.district) }.distinct()
        val matchedLandmarks = allLandmarks.filter { lm -> lm.nearbyStopIds.any { it in stopIds } }
        val linesUsed = tramSegs.mapNotNull { it.route?.number }.distinct()
        val isoNow = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
        val arrivalTimeMinutes = (((timeMinutes + totalDuration) % 1440) + 1440) % 1440

        return GeneratedTrip(
            id = "trip-${seed ?: System.currentTimeMillis()}-${Random.nextInt(100, 999)}",
            city = city,
            createdAtIso = isoNow,
            startStop = normalized.first().fromStop,
            endStop = normalized.last().toStop,
            segments = normalized,
            tramLinesUsed = linesUsed,
            transferCount = transfers,
            totalDurationMinutes = totalDuration,
            usedFallbackTransit = normalized.any { it.kind == SegmentKind.FALLBACK_TRANSIT },
            isLoop = isLoop,
            weatherMode = weatherMode,
            modeBalance = modeBalance,
            districtsVisited = districts,
            landmarks = matchedLandmarks,
            dayOfWeek = dayOfWeek,
            timeMinutes = timeMinutes,
            variantTitle = variantTitle,
            variantBadge = variantBadge,
            arrivalTimeMinutes = arrivalTimeMinutes,
            targetDeadlineMinutes = targetDeadlineMinutes,
            walkDistanceKm = walkDistanceKm
        )
    }

    fun filterLandmarks(
        landmarks: List<Landmark>,
        selectedCategories: Set<LandmarkCategory>,
        onlyWithPhotos: Boolean
    ): List<Landmark> {
        return landmarks.filter { lm ->
            (!onlyWithPhotos || lm.hasPhoto) &&
                (selectedCategories.isEmpty() || lm.category in selectedCategories)
        }
    }

    fun buildYandexMapsRouteUrl(trip: GeneratedTrip): String {
        val waypoints = mutableListOf(trip.startStop)
        for (seg in trip.segments) {
            if (waypoints.last().id != seg.toStop.id) {
                waypoints.add(seg.toStop)
            }
        }
        val rtext = waypoints.joinToString("~") {
            String.format(Locale.US, "%.6f,%.6f", it.lat, it.lng)
        }
        val encoded = URLEncoder.encode(rtext, "UTF-8").replace("%7E", "~")
        return "https://yandex.ru/maps?rtext=$encoded&rtt=mt"
    }

    fun buildAnnouncements(trip: GeneratedTrip, announcerIntro: String): List<Pair<String, String>> {
        val lines = mutableListOf<Pair<String, String>>()
        val startClean = trip.startStop.name.substringBefore("(").trim()
        lines.add(
            "intro" to "$announcerIntro Начальная остановка — «$startClean». Будьте внимательны, оплачивайте проезд при входе."
        )
        trip.segments.forEachIndexed { idx, seg ->
            val fromClean = seg.fromStop.name.substringBefore("(").trim()
            val toClean = seg.toStop.name.substringBefore("(").trim()
            val text = when (seg.kind) {
                SegmentKind.TRAM -> {
                    val lineNum = seg.route?.number.orEmpty()
                    val endSuffix = if (idx == trip.segments.lastIndex) {
                        "Конечная. Уважаемые пассажиры, не забывайте свои вещи в салоне."
                    } else {
                        "Остановка «$toClean» — пересадка на следующий участок маршрута."
                    }
                    when (seg.route?.type) {
                        "metro" -> "Линия метро $lineNum: от станции «$fromClean» до станции «$toClean». $endSuffix"
                        "trolleybus" -> "Троллейбус номер ${lineNum.removePrefix("Тр-")} следует от остановки «$fromClean» до остановки «$toClean». $endSuffix"
                        "bus" -> "Автобус номер ${lineNum.removePrefix("А-")} следует от остановки «$fromClean» до остановки «$toClean». $endSuffix"
                        else -> "Трамвай номер $lineNum следует от остановки «$fromClean» до остановки «$toClean». $endSuffix"
                    }
                }
                SegmentKind.WALK ->
                    "Переход к следующей остановке «$toClean» пешком, ориентировочно ${seg.durationMinutes} мин."
                SegmentKind.FALLBACK_TRANSIT ->
                    "На данном участке воспользуйтесь резервным транспортом до остановки «$toClean»."
            }
            lines.add("${seg.id}-$idx" to text)
        }
        return lines
    }

    fun evaluatePassportBadges(
        history: List<TripHistoryEntry>,
        savedRoutes: List<SavedRoute>
    ): List<PassportBadge> {
        val uniqueLines = history.flatMap { it.trip.tramLinesUsed }.toSet().size
        val loopTrips = history.count { it.trip.isLoop }
        val totalPhotos = history.sumOf { it.photos.size }
        val hasOmsk = history.any { it.trip.city == CityId.OMSK }
        val hasSpb = history.any { it.trip.city == CityId.SPB }

        return listOf(
            PassportBadge(
                id = "first_ride",
                icon = "🎫",
                title = "Первый билет",
                description = "Завершите свою первую поездку в режиме «В пути»",
                unlocked = history.isNotEmpty(),
                progressText = "${minOf(1, history.size)}/1"
            ),
            PassportBadge(
                id = "line_collector",
                icon = "🚋",
                title = "Коллекционер линий",
                description = "Проедьте минимум по 5 разным трамвайным маршрутам",
                unlocked = uniqueLines >= 5,
                progressText = "${minOf(5, uniqueLines)}/5"
            ),
            PassportBadge(
                id = "ring_master",
                icon = "🔁",
                title = "Властелин кольца",
                description = "Завершите круговой маршрут со стартом и финишем в одной точке",
                unlocked = loopTrips >= 1,
                progressText = "${minOf(1, loopTrips)}/1"
            ),
            PassportBadge(
                id = "photo_chronicler",
                icon = "📸",
                title = "Трамвайный фотограф",
                description = "Добавьте хотя бы 3 снимка в историю поездок",
                unlocked = totalPhotos >= 3,
                progressText = "${minOf(3, totalPhotos)}/3"
            ),
            PassportBadge(
                id = "two_capitals",
                icon = "🧭",
                title = "Два города — одни рельсы",
                description = "Совершите поездки и в Санкт-Петербурге, и в Омске",
                unlocked = hasSpb && hasOmsk,
                progressText = "${(if (hasSpb) 1 else 0) + (if (hasOmsk) 1 else 0)}/2"
            ),
            PassportBadge(
                id = "curator",
                icon = "⭐",
                title = "Хранитель маршрутов",
                description = "Сохраните 3 любимых маршрута в Избранное",
                unlocked = savedRoutes.size >= 3,
                progressText = "${minOf(3, savedRoutes.size)}/3"
            )
        )
    }

    fun calculateTripDistanceKm(
        trip: GeneratedTrip,
        stopsById: Map<String, TramStop>
    ): Double {
        var km = 0.0
        for (seg in trip.segments) {
            if (seg.stopIds.size >= 2) {
                var segKm = 0.0
                var resolvedAll = true
                for (i in 0 until seg.stopIds.size - 1) {
                    val a = stopsById[seg.stopIds[i]]
                    val b = stopsById[seg.stopIds[i + 1]]
                    if (a != null && b != null) {
                        segKm += haversineKm(a.lat, a.lng, b.lat, b.lng)
                    } else {
                        resolvedAll = false
                        break
                    }
                }
                km += if (resolvedAll) segKm else haversineKm(
                    seg.fromStop.lat, seg.fromStop.lng, seg.toStop.lat, seg.toStop.lng
                )
            } else {
                km += haversineKm(seg.fromStop.lat, seg.fromStop.lng, seg.toStop.lat, seg.toStop.lng)
            }
        }
        return (km * 10.0).roundToInt() / 10.0
    }

    fun computeSavedRoutesStats(
        savedRoutes: List<SavedRoute>,
        stopsById: Map<String, TramStop>,
        spbTotalDistrictsCount: Int
    ): SavedRoutesStats {
        var totalDist = 0.0
        var totalDuration = 0
        var spbRoutesCount = 0
        val spbDistricts = LinkedHashSet<String>()

        for (item in savedRoutes) {
            val trip = item.trip
            totalDist += calculateTripDistanceKm(trip, stopsById)
            totalDuration += trip.totalDurationMinutes
            if (trip.city == CityId.SPB) {
                spbRoutesCount += 1
                trip.districtsVisited.filter { it.isNotBlank() }.forEach { spbDistricts.add(it) }
            }
        }

        return SavedRoutesStats(
            totalRoutesCount = savedRoutes.size,
            totalDistanceKm = (totalDist * 10.0).roundToInt() / 10.0,
            totalDurationMinutes = totalDuration,
            spbRoutesCount = spbRoutesCount,
            spbDistrictsCount = spbDistricts.size,
            spbDistrictsTotal = spbTotalDistrictsCount,
            spbDistrictNames = spbDistricts.sorted()
        )
    }

    val OFFICIAL_AUDIO_GUIDES: List<OfficialAudioGuide> = listOf(
        OfficialAudioGuide(
            id = "spb-get-route-3",
            city = CityId.SPB,
            title = "Видовой трамвайный маршрут № 3",
            portalName = "izi.TRAVEL · СПб ГУП «Горэлектротранс»",
            url = "https://izi.travel/ru/3f47-vidovoy-tramvaynyy-marshrut-no3/ru",
            durationLabel = "~45 мин",
            description = "Официальный аудиогид Горэлектротранса по трамваю № 3: площадь Репина, Семимостье, Сенная, Марсово поле, Троицкий мост и Финляндский вокзал.",
            matchRouteNumbers = listOf("3"),
            matchDistricts = listOf("Адмиралтейский", "Центральный", "Петроградская сторона", "Петроградский")
        ),
        OfficialAudioGuide(
            id = "spb-get-museum-channel",
            city = CityId.SPB,
            title = "Экскурсии «Петербургский Горэлектротранс» и ретро-трамвай Т1",
            portalName = "izi.TRAVEL · Музей ГЭТ СПб",
            url = "https://izi.travel/ru/0721-peterburgskiy-gorelektrotrans/ru",
            durationLabel = "Подборка",
            description = "Фирменные аудиоэкскурсии перевозчика об истории петербургского трамвая, маршруте «Первый туристический» (Т1) и трамвайных мостах через Неву.",
            matchRouteNumbers = listOf("Т1", "6", "40", "23", "1"),
            matchDistricts = listOf("Васильевский остров", "Петроградская сторона", "Центральный"),
            isCityOverview = true
        ),
        OfficialAudioGuide(
            id = "spb-get-museum-site",
            city = CityId.SPB,
            title = "Музей городского электрического транспорта и ретро-рейс Т1",
            portalName = "Официальный сайт Музея ГЭТ",
            url = "https://getmuseum.ru/",
            durationLabel = "Расписание и гид",
            description = "Официальная страница Экспозиционно-выставочного комплекса ГЭТ на Среднем проспекте В.О., 77: трассы туристического ретро-трамвая Т1 и история вагонов.",
            matchRouteNumbers = listOf("Т1"),
            matchDistricts = listOf("Васильевский остров")
        ),
        OfficialAudioGuide(
            id = "spb-kgiop-52-60",
            city = CityId.SPB,
            title = "Краеведческие аудиогиды по трамваям № 52 и № 60",
            portalName = "izi.TRAVEL · КГИОП СПб",
            url = "https://izi.travel/ru/6f29-mezhregionalnyy-festival-socialnykh-proektov-kgiop-missiya-sokhranit/ru",
            durationLabel = "~40 мин",
            description = "Аудиомаршруты по юго-западу Петербурга (к Северной верфи, Сосновой Поляне и ЛЭМЗ), озвученные актрисой Анастасией Мельниковой в проекте КГИОП.",
            matchRouteNumbers = listOf("52", "60", "36", "41"),
            matchDistricts = listOf("Кировский", "Красносельский", "Петродворцовый")
        ),
        OfficialAudioGuide(
            id = "spb-visit-kolomna",
            city = CityId.SPB,
            title = "Очарование Коломны",
            portalName = "Visit Petersburg · Комитет по туризму СПб",
            url = "https://visit-petersburg.ru/route/ocharovanie-kolomny/",
            durationLabel = "~50 мин",
            description = "Официальный городской аудиомаршрут вдоль Крюкова канала, Никольского собора, Семимостья и площади Тургенева рядом с линией трамваев № 3 и № 16.",
            matchRouteNumbers = listOf("3", "16", "41"),
            matchDistricts = listOf("Адмиралтейский")
        ),
        OfficialAudioGuide(
            id = "spb-visit-vasilevsky",
            city = CityId.SPB,
            title = "Васильевский. Остров памяти и славы",
            portalName = "Visit Petersburg · Комитет по туризму СПб",
            url = "https://visit-petersburg.ru/route/vasilevskiy-ostrov-pamyati-i-slavy/",
            durationLabel = "~55 мин",
            description = "Официальный аудиомаршрут по линиям Васильевского острова, Стрелке, Среднему проспекту и набережным Невы вдоль трамваев № 1, № 6, № 40 и Т1.",
            matchRouteNumbers = listOf("1", "6", "40", "Т1"),
            matchDistricts = listOf("Васильевский остров")
        ),
        OfficialAudioGuide(
            id = "spb-visit-nevsky",
            city = CityId.SPB,
            title = "Храмы Невского проспекта и исторический центр",
            portalName = "Visit Petersburg · Комитет по туризму СПб",
            url = "https://visit-petersburg.ru/route/khramy-nevskogo-prospekta/",
            durationLabel = "~45 мин",
            description = "Официальный аудиопутеводитель Visit Petersburg по парадному центру, Литейному, Садовой и окрестностям центральных трамвайных линий.",
            matchRouteNumbers = listOf("3", "16", "А", "Т1"),
            matchDistricts = listOf("Центральный", "Адмиралтейский")
        ),
        OfficialAudioGuide(
            id = "spb-visit-new-geography",
            city = CityId.SPB,
            title = "Новая туристская география Петербурга XXI века",
            portalName = "Visit Petersburg · Комитет по туризму СПб",
            url = "https://visit-petersburg.ru/route/audiomarshrut-novaya-turistskaya-geografiya-10-dostoprimechatelnostey-sankt-peterburga-xxi-veka/",
            durationLabel = "~60 мин",
            description = "Официальный аудиомаршрут по современным кварталам, набережным Приморского, Невского и Красногвардейского районов и сети «Чижик».",
            matchRouteNumbers = listOf("48", "19", "7", "8", "63", "64", "23"),
            matchDistricts = listOf(
                "Приморский", "Невский", "Красногвардейский",
                "Выборгский", "Калининский", "Московский", "Фрунзенский"
            )
        ),
        OfficialAudioGuide(
            id = "spb-visit-portal",
            city = CityId.SPB,
            title = "Официальный туристский портал Санкт-Петербурга",
            portalName = "Visit Petersburg",
            url = "https://visit-petersburg.ru/",
            durationLabel = "Каталог гидов",
            description = "Официальная подборка городских аудиоэкскурсий, подкастов и маршрутов по всем районам Петербурга.",
            matchRouteNumbers = emptyList(),
            matchDistricts = emptyList(),
            isCityOverview = true
        ),
        OfficialAudioGuide(
            id = "omsk-tic-welcome",
            city = CityId.OMSK,
            title = "Добро пожаловать в Омск!",
            portalName = "izi.TRAVEL · ТИЦ Омской области",
            url = "https://izi.travel/ru/09f1-dobro-pozhalovat-v-omsk/ru",
            durationLabel = "~50 мин",
            description = "Официальный обзорный аудиогид Туристского информационного центра Омской области: Омская крепость, Любинский проспект, площадь Бухгольца и набережная Иртыша.",
            matchRouteNumbers = listOf("1", "2", "4", "7", "8", "9"),
            matchDistricts = listOf("Центральный", "Ленинский", "Кировский"),
            isCityOverview = true
        ),
        OfficialAudioGuide(
            id = "omsk-next-stop",
            city = CityId.OMSK,
            title = "Следующая остановка — Омск из окна электротранспорта",
            portalName = "izi.TRAVEL · Культурный проект Омска",
            url = "https://izi.travel/ru/c234-sleduyuschaya-ostanovka/ru",
            durationLabel = "~45 мин",
            description = "Аудиоэкскурсия о районах и магистралях Омска из окна городского электротранспорта — отличное сопровождение для прогулки на омском трамвае.",
            matchRouteNumbers = listOf("4", "7", "8", "2", "9"),
            matchDistricts = listOf("Центральный", "Октябрьский", "Советский", "Ленинский", "Амурский посёлок"),
            isCityOverview = true
        ),
        OfficialAudioGuide(
            id = "omsk-dostoevsky-walk",
            city = CityId.OMSK,
            title = "Прогулка с Ф. М. Достоевским по Омску",
            portalName = "izi.TRAVEL · Литературный Омск",
            url = "https://izi.travel/ru/bd19-progulka-s-f-m-dostoevskim/ru",
            durationLabel = "~40 мин",
            description = "Аудиомаршрут по историческому центру, Воскресенскому военному собору, Омской крепости и дому коменданта — вдоль центральных трамвайных узлов.",
            matchRouteNumbers = listOf("1", "4", "7", "8"),
            matchDistricts = listOf("Центральный")
        ),
        OfficialAudioGuide(
            id = "omsk-dostoevsky-fortress",
            city = CityId.OMSK,
            title = "Достоевский. Я здесь сидел",
            portalName = "izi.TRAVEL · Омская крепость",
            url = "https://izi.travel/ru/7fc4-dostoevskiy-ya-zdes-sidel/ru",
            durationLabel = "~35 мин",
            description = "Исторический аудиогид о годах писателя в Омске, Тарских и Тобольских воротах и памятниках центра города.",
            matchRouteNumbers = listOf("4", "7", "8"),
            matchDistricts = listOf("Центральный", "Октябрьский")
        )
    )

    fun matchAudioGuidesForTrip(
        trip: GeneratedTrip,
        limit: Int = 3,
        preferredRouteNumber: String? = null
    ): List<MatchedAudioGuide> {
        val usedLines = trip.tramLinesUsed.map { it.trim().uppercase(Locale.ROOT) }.toSet()
        val preferredToken = preferredRouteNumber?.trim()?.uppercase(Locale.ROOT)
        val visitedDistricts = trip.districtsVisited.map { it.trim() }.toSet()
        val landmarkIds = trip.landmarks.map { it.id }.toSet()

        return OFFICIAL_AUDIO_GUIDES
            .filter { it.city == trip.city }
            .map { guide ->
                val matchedLines = guide.matchRouteNumbers.filter {
                    it.trim().uppercase(Locale.ROOT) in usedLines
                }
                val matchesPreferredLine = preferredToken != null &&
                    guide.matchRouteNumbers.any { it.trim().uppercase(Locale.ROOT) == preferredToken }
                val matchedDistricts = guide.matchDistricts.filter { it.trim() in visitedDistricts }
                val matchedLandmarks = guide.matchLandmarkIds.filter { it in landmarkIds }

                var score = (if (matchesPreferredLine) 600 + (if (guide.matchRouteNumbers.size == 1) 250 else 0) else 0) +
                    matchedLines.size * 100 +
                    matchedDistricts.size * 35 +
                    matchedLandmarks.size * 20
                if (guide.isCityOverview) score += 12

                val reason = when {
                    matchesPreferredLine && !preferredRouteNumber.isNullOrBlank() ->
                        "Сейчас по шагу: трамвай № $preferredRouteNumber"
                    matchedLines.isNotEmpty() ->
                        "По вашему маршруту: трамвай № ${matchedLines.joinToString(", № ")}"
                    matchedDistricts.isNotEmpty() ->
                        "Район маршрута: ${matchedDistricts.take(2).joinToString(", ")}"
                    trip.city == CityId.OMSK ->
                        "Официальный обзорный аудиогид по Омску"
                    else ->
                        "Официальный городской аудиопортал СПб"
                }

                MatchedAudioGuide(
                    guide = guide,
                    matchReason = reason,
                    matchScore = score
                )
            }
            .sortedWith(
                compareByDescending<MatchedAudioGuide> { it.matchScore }
                    .thenBy { it.guide.title }
            )
            .take(limit.coerceAtLeast(1))
    }
}
