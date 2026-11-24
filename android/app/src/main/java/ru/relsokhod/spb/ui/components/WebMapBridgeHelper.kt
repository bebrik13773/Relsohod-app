package ru.relsokhod.spb.ui.components

import org.json.JSONArray
import org.json.JSONObject
import ru.relsokhod.spb.model.AccentSeed
import ru.relsokhod.spb.model.CityId
import ru.relsokhod.spb.model.GeneratedTrip
import ru.relsokhod.spb.model.SegmentKind
import ru.relsokhod.spb.model.ThemeMode
import java.net.URLEncoder

/**
 * Pure JVM helper for building the fullscreen `?mapOnly=1` URL and serializing
 * state payloads for `window.__relsokhodSetMapState(...)` in WebView.
 */
object WebMapBridgeHelper {

    const val LOCAL_WEB_MAP_HOST = "appassets.androidplatform.net"
    const val LOCAL_WEB_MAP_URL = "https://$LOCAL_WEB_MAP_HOST/assets/webmap/embed-map.html"
    private const val LOCAL_ASSET_URL_PREFIX = "/assets/webmap/"

    fun buildMapOnlyUrl(
        baseUrl: String,
        city: CityId,
        themeMode: ThemeMode,
        accentSeed: AccentSeed,
        showAllNetwork: Boolean,
        includePlanned2027: Boolean,
        startStopId: String?,
        destinationStopId: String?,
        mapPickMode: String?
    ): String {
        val cleanBase = baseUrl.trim().trimEnd('/')
        val params = mutableListOf(
            "mapOnly" to "1",
            "city" to city.id,
            "theme" to themeMode.id,
            "accent" to accentSeed.id,
            "showAll" to if (showAllNetwork) "1" else "0",
            "includePlanned2027" to if (includePlanned2027) "1" else "0"
        )
        if (!startStopId.isNullOrBlank()) {
            params += "startStopId" to startStopId.trim()
        }
        if (!destinationStopId.isNullOrBlank()) {
            params += "destinationStopId" to destinationStopId.trim()
        }
        if (mapPickMode == "start" || mapPickMode == "destination") {
            params += "mapPickMode" to mapPickMode
        }

        val query = params.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        val separator = if (
            cleanBase.endsWith(".html", ignoreCase = true) ||
            cleanBase.endsWith(".htm", ignoreCase = true)
        ) {
            "?"
        } else {
            "/?"
        }
        return "$cleanBase$separator$query"
    }

    fun resolveLocalAssetPath(uriPath: String?): String? {
        val path = uriPath?.trim().orEmpty()
        if (path == "/assets/webmap" || path == LOCAL_ASSET_URL_PREFIX) {
            return "webmap/embed-map.html"
        }
        if (!path.startsWith(LOCAL_ASSET_URL_PREFIX)) return null
        val relative = path.removePrefix("/").trimEnd('/')
        if (relative.isBlank() || relative.contains("..") || relative.contains('\\')) {
            return null
        }
        return relative.removePrefix("assets/")
    }

    fun guessMimeType(assetPath: String): String {
        return when (assetPath.substringAfterLast('.', "").lowercase()) {
            "html", "htm" -> "text/html"
            "js", "mjs" -> "application/javascript"
            "css" -> "text/css"
            "json" -> "application/json"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            else -> "application/octet-stream"
        }
    }

    fun serializeMapStateJson(
        city: CityId,
        themeMode: ThemeMode,
        accentSeed: AccentSeed,
        showAllNetwork: Boolean,
        includePlanned2027: Boolean,
        startStopId: String?,
        destinationStopId: String?,
        mapPickMode: String?,
        trip: GeneratedTrip?
    ): String {
        val root = JSONObject()
        root.put("city", city.id)
        root.put("theme", themeMode.id)
        root.put("accentSeed", accentSeed.id)
        root.put("showAllNetwork", showAllNetwork)
        root.put("includePlanned2027", includePlanned2027)
        root.put("startStopId", startStopId ?: JSONObject.NULL)
        root.put("destinationStopId", destinationStopId ?: JSONObject.NULL)
        root.put(
            "mapPickMode",
            if (mapPickMode == "start" || mapPickMode == "destination") mapPickMode else JSONObject.NULL
        )

        if (trip == null) {
            root.put("trip", JSONObject.NULL)
        } else {
            val tripJson = JSONObject()
            tripJson.put("id", trip.id)
            tripJson.put("city", trip.city.id)
            tripJson.put("createdAtIso", trip.createdAtIso)
            tripJson.put("startStopId", trip.startStop.id)
            tripJson.put("endStopId", trip.endStop.id)
            tripJson.put("isLoop", trip.isLoop)
            tripJson.put("totalDurationMinutes", trip.totalDurationMinutes)
            tripJson.put("transferCount", trip.transferCount)
            tripJson.put("usedFallbackTransit", trip.usedFallbackTransit)
            tripJson.put("weatherMode", trip.weatherMode.id)
            tripJson.put("dayOfWeek", trip.dayOfWeek)
            tripJson.put("timeMinutes", trip.timeMinutes)
            trip.variantTitle?.let { tripJson.put("variantTitle", it) }
            trip.variantBadge?.let { tripJson.put("variantBadge", it) }
            trip.arrivalTimeMinutes?.let { tripJson.put("arrivalTimeMinutes", it) }
            trip.targetDeadlineMinutes?.let { tripJson.put("targetDeadlineMinutes", it) }
            trip.walkDistanceKm?.let { tripJson.put("walkDistanceKm", it) }

            val landmarkIds = JSONArray()
            trip.landmarks.forEach { landmarkIds.put(it.id) }
            tripJson.put("landmarkIds", landmarkIds)

            val segsArr = JSONArray()
            trip.segments.forEach { seg ->
                val segObj = JSONObject()
                segObj.put("id", seg.id)
                segObj.put(
                    "kind",
                    when (seg.kind) {
                        SegmentKind.TRAM -> "tram"
                        SegmentKind.WALK -> "walk"
                        SegmentKind.FALLBACK_TRANSIT -> "fallback_transit"
                    }
                )
                segObj.put("routeId", seg.route?.id ?: JSONObject.NULL)
                segObj.put("routeNumber", seg.route?.number ?: JSONObject.NULL)
                segObj.put("fallbackModeName", seg.fallbackModeName ?: JSONObject.NULL)
                segObj.put("fromStopId", seg.fromStop.id)
                segObj.put("toStopId", seg.toStop.id)
                segObj.put("stopsCount", seg.stopsCount)
                segObj.put("durationMinutes", seg.durationMinutes)
                segObj.put("directionNote", seg.directionNote)

                val stopIdsArr = JSONArray()
                seg.stopIds.forEach { stopIdsArr.put(it) }
                segObj.put("stopIds", stopIdsArr)

                segsArr.put(segObj)
            }
            tripJson.put("segments", segsArr)
            root.put("trip", tripJson)
        }

        return root.toString()
    }

    fun buildEvaluateJsCall(stateJson: String): String {
        return "window.__relsokhodSetMapState && window.__relsokhodSetMapState($stateJson);"
    }
}
