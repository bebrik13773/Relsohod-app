package ru.relsokhod.spb.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import ru.relsokhod.spb.model.CityConfig
import ru.relsokhod.spb.model.CityDetectionResult
import ru.relsokhod.spb.model.CityId
import ru.relsokhod.spb.model.Landmark
import ru.relsokhod.spb.model.LandmarkCategory
import ru.relsokhod.spb.model.RouteSchedule
import ru.relsokhod.spb.model.TramRoute
import ru.relsokhod.spb.model.TramStop
import ru.relsokhod.spb.router.TramRouter
import java.io.InputStream

object NetworkRepository {

    fun loadFromAssets(context: Context): Map<CityId, CityConfig> {
        return context.assets.open("tram_network.json").use { parseJson(it) }
    }

    fun parseJson(stream: InputStream): Map<CityId, CityConfig> {
        val raw = stream.bufferedReader(Charsets.UTF_8).readText()
        val root = JSONObject(raw)
        val citiesArr = root.getJSONArray("cities")
        val map = LinkedHashMap<CityId, CityConfig>()
        for (i in 0 until citiesArr.length()) {
            val c = citiesArr.getJSONObject(i)
            val id = CityId.fromId(c.getString("id"))
            val center = c.getJSONObject("center")
            val stops = parseStops(c.getJSONArray("stops"))
            val routes = parseRoutes(c.getJSONArray("routes"))
            val landmarks = parseLandmarks(c.getJSONArray("landmarks"))
            map[id] = CityConfig(
                id = id,
                name = c.getString("name"),
                shortName = c.getString("shortName"),
                genitiveName = c.getString("genitiveName"),
                tagline = c.getString("tagline"),
                centerLat = center.getDouble("lat"),
                centerLng = center.getDouble("lng"),
                defaultStartStopId = c.getString("defaultStartStopId"),
                radiusKm = c.getDouble("radiusKm"),
                stops = stops,
                routes = routes,
                landmarks = landmarks,
                announcerIntro = c.getString("announcerIntro")
            )
        }
        return map
    }

    private fun parseStops(arr: JSONArray): List<TramStop> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            add(
                TramStop(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    district = o.getString("district"),
                    lat = o.getDouble("lat"),
                    lng = o.getDouble("lng"),
                    hubId = if (o.isNull("hubId")) null else o.optString("hubId").ifBlank { null },
                    isolatedFromMainTram = o.optBoolean("isolatedFromMainTram", false)
                )
            )
        }
    }

    private fun parseRoutes(arr: JSONArray): List<TramRoute> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val sched = o.getJSONObject("schedule")
            val daysArr = sched.getJSONArray("daysOfWeek")
            val days = List(daysArr.length()) { idx -> daysArr.getInt(idx) }
            val themesArr = o.getJSONArray("themes")
            val themes = List(themesArr.length()) { idx -> themesArr.getString(idx) }
            val stopsArr = o.getJSONArray("stopIds")
            val stopIds = List(stopsArr.length()) { idx -> stopsArr.getString(idx) }
            add(
                TramRoute(
                    id = o.getString("id"),
                    number = o.getString("number"),
                    title = o.getString("title"),
                    colorHex = o.getString("color"),
                    type = o.getString("type"),
                    themes = themes,
                    schedule = RouteSchedule(
                        daysOfWeek = days,
                        startMinutes = sched.getInt("startMinutes"),
                        endMinutes = sched.getInt("endMinutes"),
                        intervalMinutes = sched.getInt("intervalMinutes"),
                        label = sched.getString("label")
                    ),
                    stopIds = stopIds,
                    minutesPerHop = o.getInt("minutesPerHop"),
                    description = o.getString("description")
                )
            )
        }
    }

    private fun parseLandmarks(arr: JSONArray): List<Landmark> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val nearbyArr = o.getJSONArray("nearbyStopIds")
            val nearbyIds = List(nearbyArr.length()) { idx -> nearbyArr.getString(idx) }
            val photoUrl = if (o.isNull("photoUrl")) null else o.optString("photoUrl").ifBlank { null }
            add(
                Landmark(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    category = LandmarkCategory.fromId(o.getString("category")),
                    district = o.getString("district"),
                    description = o.getString("description"),
                    lat = o.getDouble("lat"),
                    lng = o.getDouble("lng"),
                    nearbyStopIds = nearbyIds,
                    hasPhoto = photoUrl != null
                )
            )
        }
    }

    fun detectCityFromCoords(
        lat: Double,
        lng: Double,
        cities: Map<CityId, CityConfig>
    ): CityDetectionResult {
        val spb = cities[CityId.SPB]
        val omsk = cities[CityId.OMSK]
        val dSpb = if (spb != null) TramRouter.haversineKm(lat, lng, spb.centerLat, spb.centerLng) else 9999.0
        val dOmsk = if (omsk != null) TramRouter.haversineKm(lat, lng, omsk.centerLat, omsk.centerLng) else 9999.0

        val detected = when {
            spb != null && dSpb <= spb.radiusKm -> CityId.SPB
            omsk != null && dOmsk <= omsk.radiusKm -> CityId.OMSK
            else -> null
        }
        val nearest = if (dSpb <= dOmsk) CityId.SPB else CityId.OMSK
        return CityDetectionResult(
            detected = detected,
            nearestSupportedCity = nearest,
            distanceToSpbKm = dSpb,
            distanceToOmskKm = dOmsk
        )
    }
}
