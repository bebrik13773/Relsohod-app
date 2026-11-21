package ru.relsokhod.spb.router

import ru.relsokhod.spb.model.CityId
import ru.relsokhod.spb.model.DurationPreset
import ru.relsokhod.spb.model.GeneratedTrip
import ru.relsokhod.spb.model.GenerationOutcome
import ru.relsokhod.spb.model.Landmark
import ru.relsokhod.spb.model.LandmarkCategory
import ru.relsokhod.spb.model.PassportBadge
import ru.relsokhod.spb.model.RouteSegment
import ru.relsokhod.spb.model.RouteThemeFilter
import ru.relsokhod.spb.model.SavedRoute
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
        val durationMinutes: Int
    )

    private data class RawStep(
        val fromStopId: String,
        val toStopId: String,
        val kind: SegmentKind,
        val route: TramRoute? = null,
        val fallbackModeName: String? = null,
        val durationMinutes: Int
    )

    private fun buildAdjacencyGraph(
        stops: List<TramStop>,
        routes: List<TramRoute>,
        dayOfWeek: Int,
        timeMinutes: Int,
        allowFallbackTransit: Boolean
    ): Pair<Map<String, MutableList<GraphEdge>>, List<TramRoute>> {
        val edges = HashMap<String, MutableList<GraphEdge>>()
        stops.forEach { edges[it.id] = mutableListOf() }

        val activeRoutes = routes.filter { isRouteActiveAt(it, dayOfWeek, timeMinutes) }
        for (route in activeRoutes) {
            val seq = route.stopIds
            if (route.type == "circular") {
                for (i in 0 until seq.size - 1) {
                    val a = seq[i]
                    val b = seq[i + 1]
                    if (edges.containsKey(a) && edges.containsKey(b)) {
                        edges[a]!!.add(GraphEdge(b, SegmentKind.TRAM, route, null, route.minutesPerHop))
                    }
                }
            } else {
                for (i in 0 until seq.size - 1) {
                    val a = seq[i]
                    val b = seq[i + 1]
                    if (edges.containsKey(a) && edges.containsKey(b)) {
                        edges[a]!!.add(GraphEdge(b, SegmentKind.TRAM, route, null, route.minutesPerHop))
                        edges[b]!!.add(GraphEdge(a, SegmentKind.TRAM, route, null, route.minutesPerHop))
                    }
                }
            }
        }

        for (i in stops.indices) {
            for (j in i + 1 until stops.size) {
                val a = stops[i]
                val b = stops[j]
                if (a.isolatedFromMainTram || b.isolatedFromMainTram) continue
                val sameHub = !a.hubId.isNullOrBlank() && a.hubId == b.hubId
                val distKm = haversineKm(a.lat, a.lng, b.lat, b.lng)
                if (sameHub || distKm <= 0.65) {
                    val mins = maxOf(3, (distKm * 13.0).roundToInt())
                    edges[a.id]!!.add(GraphEdge(b.id, SegmentKind.WALK, durationMinutes = mins))
                    edges[b.id]!!.add(GraphEdge(a.id, SegmentKind.WALK, durationMinutes = mins))
                }
            }
        }

        if (allowFallbackTransit) {
            val mainStops = stops.filter { !it.isolatedFromMainTram }
            for (iso in stops.filter { it.isolatedFromMainTram }) {
                val closest = findNearestStops(iso.lat, iso.lng, mainStops, 2, false)
                for ((bridge, distKm) in closest) {
                    val mins = maxOf(8, (distKm * 2.8 + 5.0).roundToInt())
                    val label = if (distKm > 5.0) "Автобус / пригородный экспресс" else "Автобус / метро (стыковка)"
                    edges[bridge.id]!!.add(GraphEdge(iso.id, SegmentKind.FALLBACK_TRANSIT, null, label, mins))
                    edges[iso.id]!!.add(GraphEdge(bridge.id, SegmentKind.FALLBACK_TRANSIT, null, label, mins))
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
            } else {
                val isCircular = step.route?.type == "circular"
                val note = when (step.kind) {
                    SegmentKind.TRAM ->
                        if (isCircular) "По кольцевому маршруту (по часовой стрелке)"
                        else "В направлении: ${step.route?.title.orEmpty()}"
                    SegmentKind.WALK -> "Пешая пересадка между остановками"
                    SegmentKind.FALLBACK_TRANSIT ->
                        "${step.fallbackModeName ?: "Резервный транспорт"} — там, где нет трамвая"
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
        theme: RouteThemeFilter
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
                        if (curr.lastRouteId != null && curr.lastRouteId != routeId) edgeCost += 7.0
                        if (theme != RouteThemeFilter.ANY && edge.route?.themes?.contains(theme.id) == true) {
                            edgeCost *= 0.82
                        }
                    }
                    SegmentKind.WALK -> edgeCost += 8.0
                    SegmentKind.FALLBACK_TRANSIT -> edgeCost += 5000.0
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
                            durationMinutes = edge.durationMinutes
                        )
                    )
                )
            }
        }
        return null
    }

    private fun durationRange(preset: DurationPreset): Pair<Int, Int> = when (preset) {
        DurationPreset.SHORT -> 12 to 42
        DurationPreset.MEDIUM -> 38 to 82
        DurationPreset.LONG -> 75 to 190
        DurationPreset.ANY -> 15 to 180
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
        loopMode: Boolean = false,
        weatherMode: WeatherMode = WeatherMode.CLEAR,
        preferSpecialFleet: Boolean = false,
        seed: Long? = null
    ): GenerationOutcome {
        val stopMap = stops.associateBy { it.id }
        val startStop = stopMap[startStopId]
            ?: return GenerationOutcome.NoService("Выбранная стартовая остановка не найдена.")

        val (edgesByStop, activeRoutes) = buildAdjacencyGraph(
            stops, routes, dayOfWeek, timeMinutes, allowFallbackTransit
        )

        if (activeRoutes.isEmpty()) {
            return GenerationOutcome.NoService(
                "В выбранное время (${formatMinutesToClock(timeMinutes)}) трамваи уже завершили движение. Выберите дневное или вечернее время."
            )
        }

        if (!destinationStopId.isNullOrBlank() && destinationStopId != startStopId && !loopMode) {
            val destStop = stopMap[destinationStopId]
                ?: return GenerationOutcome.NoService("Конечная остановка не найдена.")
            val steps = findPathToDestination(startStopId, destinationStopId, edgesByStop, theme)
            if (steps == null) {
                val nearest = findNearestStops(destStop.lat, destStop.lng, stops, 1, false).firstOrNull()?.first
                return GenerationOutcome.NeedsFallback(
                    "До точки «${destStop.name}» нельзя добраться только на трамвае и пешком. Включите резервный транспорт для стыковки.",
                    nearest
                )
            }
            val segments = collapseSteps(steps, stopMap)
            return GenerationOutcome.Ok(
                assembleTrip(city, segments, landmarks, dayOfWeek, timeMinutes, seed, false, weatherMode)
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
            DurationPreset.SHORT -> 7
            DurationPreset.MEDIUM -> 14
            else -> 22
        }

        data class Candidate(val steps: List<RawStep>, val score: Double)
        val candidates = mutableListOf<Candidate>()

        repeat(100) {
            val steps = mutableListOf<RawStep>()
            val visited = HashMap<String, Int>().apply { put(startStopId, 1) }
            var currId = startStopId
            var currRouteId: String? = null
            var totalDuration = 0
            var transfers = 0

            for (hop in 0 until maxHops) {
                val neighbors = edgesByStop[currId].orEmpty().filter { it.kind != SegmentKind.FALLBACK_TRANSIT }
                if (neighbors.isEmpty()) break

                val weighted = neighbors.map { edge ->
                    var w = 1.0
                    val visitCount = visited[edge.toStopId] ?: 0
                    if (steps.isNotEmpty() && edge.toStopId == steps.last().fromStopId) w *= 0.01

                    val completingCircular = edge.route?.type == "circular" && edge.toStopId == startStopId && steps.size >= 4
                    val completingWalkLoop = loopMode && edge.toStopId == startStopId && steps.size >= 3

                    if (completingWalkLoop) {
                        w *= 14.0
                    } else if (visitCount > 0 && !completingCircular) {
                        w *= 0.06
                    }

                    if (edge.kind == SegmentKind.TRAM) {
                        if (currRouteId != null && edge.route?.id == currRouteId) {
                            w *= 3.2
                        } else if (currRouteId != null && edge.route?.id != currRouteId) {
                            w *= if (transfers >= 3) 0.25 else 1.15
                        }
                        if (theme != RouteThemeFilter.ANY && edge.route?.themes?.contains(theme.id) == true) {
                            w *= 4.5
                        }
                        if (preferSpecialFleet && edge.route != null && edge.route.type != "regular") {
                            w *= 5.0
                        }
                        if (weatherMode == WeatherMode.RAIN || weatherMode == WeatherMode.FROST) {
                            if ((edge.route?.schedule?.intervalMinutes ?: 99) <= 11) w *= 1.8
                        } else if (edge.route?.themes?.any { it == "parks" || it == "maritime" } == true) {
                            w *= 1.45
                        }
                    } else {
                        w *= if (weatherMode == WeatherMode.RAIN || weatherMode == WeatherMode.FROST) 0.2 else 0.55
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
                steps.add(RawStep(currId, chosen.toStopId, chosen.kind, chosen.route, null, chosen.durationMinutes))
                totalDuration += chosen.durationMinutes
                if (chosen.kind == SegmentKind.TRAM) currRouteId = chosen.route?.id ?: currRouteId
                currId = chosen.toStopId
                visited[currId] = (visited[currId] ?: 0) + 1

                if (loopMode && currId == startStopId && steps.size >= 3) break
                if (totalDuration >= maxMins) break
                if (!loopMode && totalDuration >= minMins && rand.nextDouble() < 0.28 && steps.size >= 3) break
            }

            if (loopMode && steps.size >= 2 && steps.last().toStopId != startStopId) {
                val back = findPathToDestination(steps.last().toStopId, startStopId, edgesByStop, theme)
                if (back != null && back.size in 1..8 && back.none { it.kind == SegmentKind.FALLBACK_TRANSIT }) {
                    for (bs in back) {
                        steps.add(bs)
                        totalDuration += bs.durationMinutes
                    }
                }
            }

            while (steps.size > 1 && steps.last().kind == SegmentKind.WALK) {
                totalDuration -= steps.removeAt(steps.lastIndex).durationMinutes
            }
            if (steps.size < 2) return@repeat

            val segs = collapseSteps(steps, stopMap)
            val tramSegs = segs.filter { it.kind == SegmentKind.TRAM }
            if (tramSegs.isEmpty()) return@repeat

            val districts = segs.flatMap { listOf(it.fromStop.district, it.toStop.district) }.toSet()
            val themeMatches = tramSegs.count { theme == RouteThemeFilter.ANY || it.route?.themes?.contains(theme.id) == true }
            var score = districts.size * 12.0 + tramSegs.size * 6.0 + themeMatches * 15.0
            if (loopMode) {
                if (segs.last().toStop.id == startStopId) score += 180.0
                if (tramSegs.mapNotNull { it.route?.id }.toSet().size >= 2) score += 45.0
            }
            if (preferSpecialFleet) {
                score += tramSegs.count { it.route != null && it.route.type != "regular" } * 35.0
            }
            score += if (totalDuration in minMins..maxMins) 30.0 else -abs(totalDuration - (minMins + maxMins) / 2.0) * 0.5
            candidates.add(Candidate(steps, score))
        }

        if (candidates.isEmpty()) {
            return GenerationOutcome.NoService(
                "Не удалось построить маршрут из этой точки в выбранное время. Попробуйте сменить остановку или фильтр времени."
            )
        }

        val sorted = candidates.sortedByDescending { it.score }
        val pool = sorted.take(minOf(4, sorted.size))
        val picked = pool[rand.nextInt(pool.size)]
        val segments = collapseSteps(picked.steps, stopMap)
        val isLoopClosed = loopMode && segments.last().toStop.id == startStopId

        return GenerationOutcome.Ok(
            assembleTrip(city, segments, landmarks, dayOfWeek, timeMinutes, seed, isLoopClosed, weatherMode)
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
        val stopMap = stops.associateBy { it.id }
        val (edgesByStop, _) = buildAdjacencyGraph(
            stops, routes, trip.dayOfWeek, trip.timeMinutes, allowFallbackTransit
        )
        val rand = if (seed != null) Random(seed) else Random.Default
        val lastRouteId = trip.segments.lastOrNull()?.route?.id
        val visited = trip.segments.flatMap { it.stopIds }.toMutableSet()

        var currId = trip.endStop.id
        val addedSteps = mutableListOf<RawStep>()
        var chosenRouteId: String? = null
        val targetHops = 2 + rand.nextInt(3)

        for (hop in 0 until targetHops) {
            val rawNeighbors = edgesByStop[currId].orEmpty().filter { it.kind != SegmentKind.FALLBACK_TRANSIT }
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
                if (preferSpecialFleet && edge.route != null && edge.route.type != "regular") s += 6.0
                if ((weatherMode == WeatherMode.RAIN || weatherMode == WeatherMode.FROST) && edge.kind == SegmentKind.WALK) s -= 6.0
                s + rand.nextDouble() * 3.0
            } ?: break

            if (chosen.kind == SegmentKind.TRAM && chosenRouteId == null) {
                chosenRouteId = chosen.route?.id
            }
            addedSteps.add(RawStep(currId, chosen.toStopId, chosen.kind, chosen.route, null, chosen.durationMinutes))
            visited.add(chosen.toStopId)
            currId = chosen.toStopId
        }

        while (addedSteps.isNotEmpty() && addedSteps.last().kind == SegmentKind.WALK) {
            addedSteps.removeAt(addedSteps.lastIndex)
        }
        if (addedSteps.isEmpty()) return null

        val extra = collapseSteps(addedSteps, stopMap)
        val combined = trip.segments + extra
        val isLoopClosed = combined.last().toStop.id == combined.first().fromStop.id
        return assembleTrip(trip.city, combined, landmarks, trip.dayOfWeek, trip.timeMinutes, seed, isLoopClosed, weatherMode)
    }

    private fun assembleTrip(
        city: CityId,
        segments: List<RouteSegment>,
        allLandmarks: List<Landmark>,
        dayOfWeek: Int,
        timeMinutes: Int,
        seed: Long?,
        isLoop: Boolean,
        weatherMode: WeatherMode
    ): GeneratedTrip {
        val normalized = segments.mapIndexed { idx, seg ->
            seg.copy(id = "seg-${idx + 1}-${seg.route?.id ?: seg.kind.name}")
        }
        val tramSegs = normalized.filter { it.kind == SegmentKind.TRAM }
        val transfers = maxOf(0, tramSegs.size - 1)
        val totalDuration = normalized.sumOf { it.durationMinutes } + transfers * 5

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
}
