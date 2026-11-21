package ru.relsokhod.spb.router

import ru.relsokhod.spb.model.CityId
import ru.relsokhod.spb.model.DurationPreset
import ru.relsokhod.spb.model.GeneratedTrip
import ru.relsokhod.spb.model.GenerationOutcome
import ru.relsokhod.spb.model.Landmark
import ru.relsokhod.spb.model.LandmarkCategory
import ru.relsokhod.spb.model.ModeBalance
import ru.relsokhod.spb.model.PassportBadge
import ru.relsokhod.spb.model.RouteSegment
import ru.relsokhod.spb.model.RouteThemeFilter
import ru.relsokhod.spb.model.SavedRoute
import ru.relsokhod.spb.model.SavedRoutesStats
import ru.relsokhod.spb.model.SegmentKind
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

    private data class GraphEdge(
        val toStopId: String,
        val kind: SegmentKind,
        val route: TramRoute? = null,
        val fallbackModeName: String? = null,
        val durationMinutes: Int,
        val distanceKm: Double = 0.0
    )

    private data class RawStep(
        val fromStopId: String,
        val toStopId: String,
        val kind: SegmentKind,
        val route: TramRoute? = null,
        val fallbackModeName: String? = null,
        val durationMinutes: Int,
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
        allowCityTransit: Boolean
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
                    edges[a]?.add(GraphEdge(b, SegmentKind.TRAM, route, null, route.minutesPerHop, dist))
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
                    edges[a]?.add(GraphEdge(b, SegmentKind.TRAM, route, null, route.minutesPerHop, dist))
                    edges[b]?.add(GraphEdge(a, SegmentKind.TRAM, route, null, route.minutesPerHop, dist))
                    directTramPairs.add("$a|$b")
                    directTramPairs.add("$b|$a")
                }
            }
        }

        // Pedestrian transfers + scenic walking corridors (scalable by walk preference)
        for (i in stops.indices) {
            for (j in i + 1 until stops.size) {
                val a = stops[i]
                val b = stops[j]
                if (a.isolatedFromMainTram || b.isolatedFromMainTram) continue
                val sameHub = !a.hubId.isNullOrBlank() && a.hubId == b.hubId
                val distKm = haversineKm(a.lat, a.lng, b.lat, b.lng)
                if (sameHub || distKm <= maxWalkKm) {
                    val mins = maxOf(3, (distKm * 12.5).roundToInt())
                    edges[a.id]?.add(GraphEdge(b.id, SegmentKind.WALK, durationMinutes = mins, distanceKm = distKm))
                    edges[b.id]?.add(GraphEdge(a.id, SegmentKind.WALK, durationMinutes = mins, distanceKm = distKm))
                }
            }
        }

        // City-wide non-tram transit (metro / bus / trolleybus) connecting distant districts
        if (allowCityTransit) {
            val mainStops = stops.filter { !it.isolatedFromMainTram }
            val transitLabel = if (city == CityId.OMSK) {
                "Автобус / троллейбус (городская связка)"
            } else {
                "Метро / автобус (межрайонная связка)"
            }
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
                    val transitMins = maxOf(7, (distKm * 2.4 + 4.0).roundToInt())
                    edges[a.id]?.add(
                        GraphEdge(b.id, SegmentKind.FALLBACK_TRANSIT, null, transitLabel, transitMins, distKm)
                    )
                }
            }
        }

        // Fallback transit bridges ONLY connect isolated tram-less stops when enabled
        if (allowFallbackTransit) {
            val mainStops = stops.filter { !it.isolatedFromMainTram }
            for (iso in stops.filter { it.isolatedFromMainTram }) {
                val closest = findNearestStops(iso.lat, iso.lng, mainStops, 2, false)
                for ((bridge, distKm) in closest) {
                    val mins = maxOf(8, (distKm * 2.8 + 5.0).roundToInt())
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
                out[out.lastIndex] = prev.copy(
                    toStop = toStop,
                    stopIds = prev.stopIds + step.toStopId,
                    stopsCount = prev.stopsCount + 1,
                    durationMinutes = prev.durationMinutes + step.durationMinutes
                )
            } else if (prev != null &&
                prev.kind == SegmentKind.WALK &&
                step.kind == SegmentKind.WALK &&
                prev.toStop.id == step.fromStopId
            ) {
                val nextDuration = prev.durationMinutes + step.durationMinutes
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
                val isCircular = step.route?.type == "circular"
                val note = when (step.kind) {
                    SegmentKind.TRAM ->
                        if (isCircular) "По кольцевому маршруту (по часовой стрелке)"
                        else "В направлении: ${step.route?.title.orEmpty()}"
                    SegmentKind.WALK ->
                        if (step.durationMinutes >= 12) "Пешая прогулка по городу"
                        else "Пешая пересадка между остановками"
                    SegmentKind.FALLBACK_TRANSIT ->
                        step.fallbackModeName ?: "Городской транспорт"
                }
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
                        durationMinutes = step.durationMinutes,
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
        allowFallbackTransit: Boolean
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
        val cityTransitActive = allowFallbackTransit || balance.transit >= 0.14f
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
                        edgeCost *= tramMult
                        if (curr.lastRouteId != null && curr.lastRouteId != routeId) edgeCost += 6.5
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
                SegmentKind.TRAM -> tramMins += s.durationMinutes
                SegmentKind.WALK -> walkMins += s.durationMinutes
                SegmentKind.FALLBACK_TRANSIT -> transitMins += s.durationMinutes
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

    private fun durationRange(preset: DurationPreset): Pair<Int, Int> = when (preset) {
        DurationPreset.SHORT -> 16 to 44
        DurationPreset.MEDIUM -> 42 to 86
        DurationPreset.LONG -> 80 to 180
        DurationPreset.ANY -> 22 to 165
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
        dayOfWeek: Int = 3,
        timeMinutes: Int = 14 * 60,
        allowFallbackTransit: Boolean = false,
        modeBalance: ModeBalance = ModeBalance(),
        loopMode: Boolean = false,
        weatherMode: WeatherMode = WeatherMode.CLEAR,
        preferSpecialFleet: Boolean = false,
        seed: Long? = null
    ): GenerationOutcome {
        val balance = modeBalance.normalized()
        val stopMap = stops.associateBy { it.id }
        val startStop = stopMap[startStopId]
            ?: return GenerationOutcome.NoService("Выбранная стартовая остановка не найдена.")

        val maxWalkKm = when {
            balance.walk >= 0.35f -> 2.6
            balance.walk >= 0.16f -> 1.55
            else -> 0.72
        }
        val allowCityTransit = balance.transit >= 0.14f || allowFallbackTransit
        val effectiveIsolatedFallback = allowFallbackTransit || balance.transit >= 0.28f

        val (edgesByStop, activeRoutes) = buildAdjacencyGraph(
            city = city,
            stops = stops,
            routes = routes,
            dayOfWeek = dayOfWeek,
            timeMinutes = timeMinutes,
            allowFallbackTransit = effectiveIsolatedFallback,
            maxWalkKm = maxWalkKm,
            allowCityTransit = allowCityTransit
        )

        if (activeRoutes.isEmpty() && balance.walk < 0.65f) {
            return GenerationOutcome.NoService(
                "В выбранное время (${formatMinutesToClock(timeMinutes)}) трамваи уже завершили движение. Выберите дневное время или сдвиньте треугольник к «Пешком»."
            )
        }

        if (!destinationStopId.isNullOrBlank() && destinationStopId != startStopId && !loopMode) {
            val destStop = stopMap[destinationStopId]
                ?: return GenerationOutcome.NoService("Конечная остановка не найдена.")
            val steps = findPathToDestination(
                startStopId = startStopId,
                destinationStopId = destinationStopId,
                edgesByStop = edgesByStop,
                theme = theme,
                balance = balance,
                allowFallbackTransit = allowFallbackTransit
            )
            if (steps == null) {
                val nearest = findNearestStops(destStop.lat, destStop.lng, stops, 1, false).firstOrNull()?.first
                return GenerationOutcome.NeedsFallback(
                    "До точки «${destStop.name}» нельзя добраться только на трамвае и пешком. Включите резервный транспорт или сдвиньте треугольник в сторону «Транспорт».",
                    nearest
                )
            }
            val segments = collapseSteps(steps, stopMap)
            return GenerationOutcome.Ok(
                assembleTrip(city, segments, landmarks, dayOfWeek, timeMinutes, seed, false, weatherMode, balance)
            )
        }

        val startEdges = edgesByStop[startStopId].orEmpty()
        if (startEdges.isEmpty()) {
            return GenerationOutcome.NoService(
                "От остановки «${startStop.name}» в ${formatMinutesToClock(timeMinutes)} (${DAY_NAMES_SHORT[dayOfWeek]}) нет работающих рейсов."
            )
        }

        val rand = if (seed != null) Random(seed) else Random.Default
        val (minMins, maxMins) = durationRange(durationPreset)
        val maxHops = when (durationPreset) {
            DurationPreset.SHORT -> 9
            DurationPreset.MEDIUM -> 16
            else -> 24
        }

        data class Candidate(val steps: List<RawStep>, val score: Double)
        val candidates = mutableListOf<Candidate>()

        for (attempt in 0 until 160) {
            val steps = mutableListOf<RawStep>()
            val visited = HashMap<String, Int>().apply { put(startStopId, 1) }
            val usedRouteIds = HashSet<String>()
            var currId = startStopId
            var currRouteId: String? = null
            var totalDuration = 0
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
                    val completingCircular = edge.route?.type == "circular" && edge.toStopId == startStopId && steps.size >= 4
                    val completingWalkLoop = loopMode && edge.toStopId == startStopId && steps.size >= 4

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
                    val transitDeficit = maxOf(0.02, (balance.transit - currShares.transit + 0.20).toDouble())

                    when (edge.kind) {
                        SegmentKind.TRAM -> {
                            w *= (0.4 + balance.tram * 2.8) * (1.0 + tramDeficit)
                            if (currRouteId != null && edge.route?.id == currRouteId) {
                                w *= if (exploreMode == 1) 2.6 else 4.2
                            } else if (currRouteId != null && edge.route?.id != currRouteId) {
                                w *= if (edge.route != null && edge.route.id in usedRouteIds) {
                                    0.12
                                } else {
                                    if (transfers >= 3) 0.30 else 1.05
                                }
                            }
                            if (theme != RouteThemeFilter.ANY && edge.route?.themes?.contains(theme.id) == true) {
                                w *= 3.8
                            }
                            if (preferSpecialFleet && edge.route != null && edge.route.type != "regular") {
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
                            val walkBase = if (balance.walk < 0.12f) {
                                0.22
                            } else {
                                (0.4 + balance.walk * 4.4) * (1.0 + walkDeficit * 2.2)
                            }
                            w *= walkBase
                            if (edge.distanceKm >= 0.65 && balance.walk >= 0.25f) w *= 2.4
                            if (prevWasWalk && balance.walk < 0.55f) w *= 0.35
                            if (weatherMode == WeatherMode.RAIN || weatherMode == WeatherMode.FROST) {
                                w *= if (balance.walk >= 0.50f) 0.65 else 0.25
                            }
                        }
                        SegmentKind.FALLBACK_TRANSIT -> {
                            val prevWasTransit = steps.isNotEmpty() && steps.last().kind == SegmentKind.FALLBACK_TRANSIT
                            val transitBase = if (balance.transit < 0.14f) {
                                0.05
                            } else {
                                (0.3 + balance.transit * 4.2) * (1.0 + transitDeficit * 2.2)
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
                totalDuration += chosen.durationMinutes
                if (chosen.kind == SegmentKind.TRAM && chosen.route != null) {
                    currRouteId = chosen.route.id
                    usedRouteIds.add(chosen.route.id)
                }
                currId = chosen.toStopId
                visited[currId] = (visited[currId] ?: 0) + 1

                if (loopMode && currId == startStopId && steps.size >= 4) break
                if (totalDuration >= maxMins) break
                if (!loopMode && totalDuration >= minMins && rand.nextDouble() < 0.26 && steps.size >= 3) break
            }

            if (loopMode && steps.size >= 2 && steps.last().toStopId != startStopId) {
                val back = findPathToDestination(
                    startStopId = steps.last().toStopId,
                    destinationStopId = startStopId,
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
            if (balance.transit >= 0.25f && transitSegs.isNotEmpty()) score += 36.0

            val singleHopTrams = tramSegs.count { it.stopsCount <= 1 }
            score -= singleHopTrams * 8.0
            if (tramSegs.size > distinctTramRoutes) {
                score -= (tramSegs.size - distinctTramRoutes) * 20.0
            }

            if (loopMode) {
                if (segs.last().toStop.id == startStopId) score += 190.0
                if (distinctTramRoutes >= 2) score += 45.0
            }
            if (preferSpecialFleet) {
                score += tramSegs.count { it.route != null && it.route.type != "regular" } * 35.0
            }
            score += if (totalDuration in minMins..maxMins) {
                35.0
            } else {
                -abs(totalDuration - (minMins + maxMins) / 2.0) * 0.65
            }
            candidates.add(Candidate(steps, score))
        }

        if (candidates.isEmpty()) {
            return GenerationOutcome.NoService(
                "Не удалось построить маршрут из этой точки в выбранное время. Попробуйте сменить остановку или фильтр времени."
            )
        }

        val sorted = candidates.sortedByDescending { it.score }
        val pool = sorted.take(minOf(3, sorted.size))
        val picked = pool[rand.nextInt(pool.size)]
        val segments = collapseSteps(picked.steps, stopMap)
        val isLoopClosed = loopMode && segments.last().toStop.id == startStopId

        return GenerationOutcome.Ok(
            assembleTrip(city, segments, landmarks, dayOfWeek, timeMinutes, seed, isLoopClosed, weatherMode, balance)
        )
    }

    fun extendTripLastSegment(
        trip: GeneratedTrip,
        stops: List<TramStop>,
        routes: List<TramRoute>,
        landmarks: List<Landmark>,
        allowFallbackTransit: Boolean,
        weatherMode: WeatherMode,
        preferSpecialFleet: Boolean,
        seed: Long? = null
    ): GeneratedTrip? {
        val balance = trip.modeBalance.normalized()
        val stopMap = stops.associateBy { it.id }
        val allowCityTransit = balance.transit >= 0.14f || allowFallbackTransit
        val (edgesByStop, _) = buildAdjacencyGraph(
            city = trip.city,
            stops = stops,
            routes = routes,
            dayOfWeek = trip.dayOfWeek,
            timeMinutes = trip.timeMinutes,
            allowFallbackTransit = allowFallbackTransit,
            maxWalkKm = if (balance.walk >= 0.35f) 2.2 else 1.1,
            allowCityTransit = allowCityTransit
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
                allowCityTransit || it.kind != SegmentKind.FALLBACK_TRANSIT
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
        return assembleTrip(trip.city, combined, landmarks, trip.dayOfWeek, trip.timeMinutes, seed, isLoopClosed, weatherMode, balance)
    }

    private fun assembleTrip(
        city: CityId,
        segments: List<RouteSegment>,
        allLandmarks: List<Landmark>,
        dayOfWeek: Int,
        timeMinutes: Int,
        seed: Long?,
        isLoop: Boolean,
        weatherMode: WeatherMode,
        modeBalance: ModeBalance
    ): GeneratedTrip {
        val normalized = segments.mapIndexed { idx, seg ->
            seg.copy(id = "seg-${idx + 1}-${seg.route?.id ?: seg.kind.name}")
        }
        val tramSegs = normalized.filter { it.kind == SegmentKind.TRAM }
        val transfers = maxOf(0, normalized.size - 1)
        val tramWait = maxOf(0, tramSegs.size - 1) * 5
        val totalDuration = normalized.sumOf { it.durationMinutes } + tramWait

        val stopIds = normalized.flatMap { it.stopIds }.toSet()
        val districts = normalized.flatMap { listOf(it.fromStop.district, it.toStop.district) }.distinct()
        val matchedLandmarks = allLandmarks.filter { lm -> lm.nearbyStopIds.any { it in stopIds } }
        val linesUsed = tramSegs.mapNotNull { it.route?.number }.distinct()
        val isoNow = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())

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
            timeMinutes = timeMinutes
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
                    "Трамвай номер $lineNum следует от остановки «$fromClean» до остановки «$toClean». $endSuffix"
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
}
