package ru.relsokhod.spb

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.relsokhod.spb.model.AccentSeed
import ru.relsokhod.spb.model.CityId
import ru.relsokhod.spb.model.GeneratedTrip
import ru.relsokhod.spb.model.RouteSchedule
import ru.relsokhod.spb.model.RouteSegment
import ru.relsokhod.spb.model.SegmentKind
import ru.relsokhod.spb.model.ThemeMode
import ru.relsokhod.spb.model.TramRoute
import ru.relsokhod.spb.model.TramStop
import ru.relsokhod.spb.model.WeatherMode
import ru.relsokhod.spb.ui.components.WebMapBridgeHelper

class WebMapBridgeHelperTest {

    @Test
    fun buildMapOnlyUrl_encodesQueryParamsCleanly() {
        val url = WebMapBridgeHelper.buildMapOnlyUrl(
            baseUrl = "https://example.run.app/",
            city = CityId.OMSK,
            themeMode = ThemeMode.DARK,
            accentSeed = AccentSeed.SAGE,
            showAllNetwork = false,
            includePlanned2027 = true,
            startStopId = "omsk_cirk",
            destinationStopId = "omsk_kot",
            mapPickMode = "destination"
        )

        assertTrue(url.startsWith("https://example.run.app/?"))
        assertTrue(url.contains("mapOnly=1"))
        assertTrue(url.contains("city=omsk"))
        assertTrue(url.contains("theme=dark"))
        assertTrue(url.contains("accent=sage"))
        assertTrue(url.contains("showAll=0"))
        assertTrue(url.contains("includePlanned2027=1"))
        assertTrue(url.contains("startStopId=omsk_cirk"))
        assertTrue(url.contains("destinationStopId=omsk_kot"))
        assertTrue(url.contains("mapPickMode=destination"))
    }

    @Test
    fun buildMapOnlyUrl_handlesLocalHtmlFileWithoutTrailingSlash() {
        val url = WebMapBridgeHelper.buildMapOnlyUrl(
            baseUrl = WebMapBridgeHelper.LOCAL_WEB_MAP_URL,
            city = CityId.SPB,
            themeMode = ThemeMode.LIGHT,
            accentSeed = AccentSeed.NEVA,
            showAllNetwork = true,
            includePlanned2027 = false,
            startStopId = null,
            destinationStopId = null,
            mapPickMode = null
        )

        assertTrue(
            url.startsWith("https://appassets.androidplatform.net/assets/webmap/embed-map.html?mapOnly=1")
        )
        assertFalse(url.contains(".html/?"))
    }

    @Test
    fun resolveLocalAssetPath_validatesPrefixAndPreventsTraversal() {
        assertEquals(
            "webmap/embed-map.html",
            WebMapBridgeHelper.resolveLocalAssetPath("/assets/webmap/embed-map.html")
        )
        assertEquals(
            "webmap/assets/index-123.js",
            WebMapBridgeHelper.resolveLocalAssetPath("/assets/webmap/assets/index-123.js")
        )
        assertEquals(
            "webmap/embed-map.html",
            WebMapBridgeHelper.resolveLocalAssetPath("/assets/webmap/")
        )
        assertEquals(null, WebMapBridgeHelper.resolveLocalAssetPath("/assets/webmap/../secret.txt"))
        assertEquals(null, WebMapBridgeHelper.resolveLocalAssetPath("/other/path.js"))
        assertEquals("application/javascript", WebMapBridgeHelper.guessMimeType("webmap/assets/app.js"))
        assertEquals("text/css", WebMapBridgeHelper.guessMimeType("webmap/assets/app.css"))
        assertEquals("text/html", WebMapBridgeHelper.guessMimeType("webmap/embed-map.html"))
    }

    @Test
    fun serializeMapStateJson_includesTripSegmentsAndNullPickMode() {
        val s1 = TramStop("s1", "Старт", "Центр", 59.93, 30.31)
        val s2 = TramStop("s2", "Финиш", "Невский", 59.91, 30.39)
        val route = TramRoute(
            id = "spb_3",
            number = "3",
            title = "Маршрут 3",
            colorHex = "#7C1D26",
            type = "regular",
            themes = listOf("center"),
            schedule = RouteSchedule(listOf(1, 2, 3), 360, 1380, 10, "Ежедневно"),
            stopIds = listOf("s1", "s2"),
            minutesPerHop = 4,
            description = ""
        )
        val trip = GeneratedTrip(
            id = "trip-1",
            city = CityId.SPB,
            createdAtIso = "2026-11-24T00:00:00Z",
            startStop = s1,
            endStop = s2,
            segments = listOf(
                RouteSegment("seg-1", SegmentKind.TRAM, route, null, s1, s2, listOf("s1", "s2"), 1, 14, "Трамвай 3"),
                RouteSegment("seg-2", SegmentKind.WALK, null, null, s2, s1, listOf("s2", "s1"), 1, 9, "Пешком")
            ),
            tramLinesUsed = listOf("3"),
            transferCount = 1,
            totalDurationMinutes = 23,
            usedFallbackTransit = false,
            isLoop = false,
            weatherMode = WeatherMode.CLEAR,
            districtsVisited = listOf("Центр", "Невский"),
            landmarks = emptyList(),
            dayOfWeek = 2,
            timeMinutes = 600
        )

        val jsonStr = WebMapBridgeHelper.serializeMapStateJson(
            city = CityId.SPB,
            themeMode = ThemeMode.TRAM,
            accentSeed = AccentSeed.NEVA,
            showAllNetwork = true,
            includePlanned2027 = false,
            startStopId = "s1",
            destinationStopId = null,
            mapPickMode = null,
            trip = trip
        )

        val parsed = JSONObject(jsonStr)
        assertEquals("spb", parsed.getString("city"))
        assertEquals("tram", parsed.getString("theme"))
        assertFalse(parsed.getBoolean("includePlanned2027"))
        assertTrue(parsed.isNull("mapPickMode"))

        val tripObj = parsed.getJSONObject("trip")
        assertEquals("trip-1", tripObj.getString("id"))
        assertEquals(2, tripObj.getJSONArray("segments").length())
        assertEquals("tram", tripObj.getJSONArray("segments").getJSONObject(0).getString("kind"))
        assertEquals("walk", tripObj.getJSONArray("segments").getJSONObject(1).getString("kind"))

        val jsCall = WebMapBridgeHelper.buildEvaluateJsCall(jsonStr)
        assertTrue(jsCall.contains("__relsokhodSetMapState("))
    }
}
