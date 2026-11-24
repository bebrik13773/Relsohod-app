package ru.relsokhod.spb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.relsokhod.spb.data.NetworkRepository
import ru.relsokhod.spb.model.CityId
import ru.relsokhod.spb.model.DurationPreset
import ru.relsokhod.spb.model.GenerationOutcome
import ru.relsokhod.spb.model.LandmarkCategory
import ru.relsokhod.spb.model.ModeBalance
import ru.relsokhod.spb.model.RouteThemeFilter
import ru.relsokhod.spb.model.SavedRoute
import ru.relsokhod.spb.model.SegmentKind
import ru.relsokhod.spb.model.TimePlanningMode
import ru.relsokhod.spb.model.WeatherMode
import ru.relsokhod.spb.router.TramRouter
import ru.relsokhod.spb.ui.components.TriangleBalanceMath
import java.io.File
import kotlin.math.abs

class TramRouterTest {

    private fun loadCities() = listOf(
        File("src/main/assets/tram_network.json"),
        File("app/src/main/assets/tram_network.json")
    ).first { it.exists() }.inputStream().use { NetworkRepository.parseJson(it) }

    @Test
    fun loadsBothCitiesFromAssets() {
        val cities = loadCities()
        val spb = cities[CityId.SPB]
        val omsk = cities[CityId.OMSK]
        assertNotNull(spb)
        assertNotNull(omsk)
        assertTrue(spb!!.stops.size >= 120)
        assertTrue(spb.routes.size >= 45)
        assertTrue(omsk!!.stops.size >= 40)
        assertTrue(omsk.routes.size >= 14)

        val spbNumbers = spb.routes.map { it.number }.toSet()
        assertTrue(listOf("М1", "М2", "М3", "М4", "М5", "М6", "2", "22").all { it in spbNumbers })
        assertTrue(spb.routes.first { it.number == "22" }.planned2027)
        assertTrue(!spb.routes.first { it.number == "2" }.planned2027)

        val omskNumbers = omsk.routes.map { it.number }.toSet()
        assertTrue(listOf("Тр-67", "2К", "8К").all { it in omskNumbers })
        assertTrue(omsk.routes.first { it.number == "2К" }.planned2027)
        assertTrue(omsk.routes.first { it.number == "8К" }.planned2027)
    }

    @Test
    fun tramAScheduleOnlyWeekdays() {
        val cities = loadCities()
        val spb = cities.getValue(CityId.SPB)
        val tramA = spb.routes.first { it.id == "tram_A" }
        assertTrue(TramRouter.isRouteActiveAt(tramA, dayOfWeek = 3, timeMinutes = 14 * 60))
        assertTrue(!TramRouter.isRouteActiveAt(tramA, dayOfWeek = 0, timeMinutes = 14 * 60))
    }

    @Test
    fun omskRoutingAndYandexMapsUrlFormat() {
        val cities = loadCities()
        val omsk = cities.getValue(CityId.OMSK)
        val outcome = TramRouter.generateTramAdventure(
            city = CityId.OMSK,
            stops = omsk.stops,
            routes = omsk.routes,
            landmarks = omsk.landmarks,
            startStopId = omsk.defaultStartStopId,
            durationPreset = DurationPreset.MEDIUM,
            dayOfWeek = 3,
            timeMinutes = 14 * 60,
            seed = 77L
        )
        assertTrue("Expected Ok outcome for Omsk, got $outcome", outcome is GenerationOutcome.Ok)
        val trip = (outcome as GenerationOutcome.Ok).trip
        val url = TramRouter.buildYandexMapsRouteUrl(trip)
        assertTrue("Yandex URL mismatch: $url", url.startsWith("https://yandex.ru/maps?rtext="))
        assertTrue("Yandex URL missing &rtt=mt: $url", url.endsWith("&rtt=mt"))
        assertTrue("Yandex URL should contain literal ~ separator: $url", url.contains("~"))
        assertTrue("Yandex URL should contain %2C separator: $url", url.contains("%2C"))
    }

    @Test
    fun extendLastSegmentGeneratesUniqueSegmentIds() {
        val cities = loadCities()
        val spb = cities.getValue(CityId.SPB)
        val initial = TramRouter.generateTramAdventure(
            city = CityId.SPB,
            stops = spb.stops,
            routes = spb.routes,
            landmarks = spb.landmarks,
            startStopId = spb.defaultStartStopId,
            theme = RouteThemeFilter.HISTORIC,
            durationPreset = DurationPreset.SHORT,
            dayOfWeek = 3,
            timeMinutes = 13 * 60,
            seed = 101L
        ) as GenerationOutcome.Ok

        val extended = TramRouter.extendTripLastSegment(
            trip = initial.trip,
            stops = spb.stops,
            routes = spb.routes,
            landmarks = spb.landmarks,
            allowFallbackTransit = false,
            weatherMode = WeatherMode.CLEAR,
            preferSpecialFleet = false,
            seed = 202L
        )
        assertNotNull(extended)
        val ids = extended!!.segments.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun detectsSpbOmskAndOtherCityFromCoords() {
        val cities = loadCities()
        assertEquals(CityId.SPB, NetworkRepository.detectCityFromCoords(59.934, 30.335, cities).detected)
        assertEquals(CityId.OMSK, NetworkRepository.detectCityFromCoords(54.991, 73.368, cities).detected)
        assertNull(NetworkRepository.detectCityFromCoords(55.7558, 37.6173, cities).detected)
    }

    @Test
    fun groceryCategoryPresentInLandmarks() {
        val cities = loadCities()
        val spbGroceries = cities.getValue(CityId.SPB).landmarks.filter { it.category == LandmarkCategory.GROCERY }
        val omskGroceries = cities.getValue(CityId.OMSK).landmarks.filter { it.category == LandmarkCategory.GROCERY }
        assertTrue(spbGroceries.isNotEmpty())
        assertTrue(omskGroceries.isNotEmpty())
    }

    @Test
    fun triangleBalanceBarycentricRoundTripAndClamping() {
        val target = ModeBalance(tram = 0.5f, transit = 0.2f, walk = 0.3f)
        val pt = TriangleBalanceMath.balanceToTrianglePoint(target)
        val restored = TriangleBalanceMath.trianglePointToBalance(pt.x, pt.y)
        assertEquals(target.tram, restored.tram, 0.02f)
        assertEquals(target.transit, restored.transit, 0.02f)
        assertEquals(target.walk, restored.walk, 0.02f)

        val clamped = TriangleBalanceMath.trianglePointToBalance(1.8f, 1.8f)
        assertEquals(1.0f, clamped.tram + clamped.transit + clamped.walk, 0.001f)
        assertTrue(clamped.walk > 0.7f)
    }

    @Test
    fun highWalkAndTransitBalancesProduceMultimodalTrips() {
        val cities = loadCities()
        val spb = cities.getValue(CityId.SPB)

        val walkOutcome = TramRouter.generateTramAdventure(
            city = CityId.SPB,
            stops = spb.stops,
            routes = spb.routes,
            landmarks = spb.landmarks,
            startStopId = spb.defaultStartStopId,
            durationPreset = DurationPreset.MEDIUM,
            dayOfWeek = 3,
            timeMinutes = 14 * 60,
            modeBalance = ModeBalance(tram = 0.25f, transit = 0.05f, walk = 0.70f),
            seed = 909L
        )
        assertTrue(walkOutcome is GenerationOutcome.Ok)
        val walkTrip = (walkOutcome as GenerationOutcome.Ok).trip
        assertTrue(walkTrip.segments.any { it.kind == SegmentKind.WALK })

        val transitOutcome = TramRouter.generateTramAdventure(
            city = CityId.SPB,
            stops = spb.stops,
            routes = spb.routes,
            landmarks = spb.landmarks,
            startStopId = spb.defaultStartStopId,
            durationPreset = DurationPreset.MEDIUM,
            dayOfWeek = 3,
            timeMinutes = 14 * 60,
            modeBalance = ModeBalance(tram = 0.14f, transit = 0.44f, walk = 0.42f),
            seed = 808L
        )
        assertTrue(transitOutcome is GenerationOutcome.Ok)
        val transitTrip = (transitOutcome as GenerationOutcome.Ok).trip
        assertTrue(
            transitTrip.segments.any {
                it.kind == SegmentKind.FALLBACK_TRANSIT || TramRouter.isCityTransitRoute(it.route)
            }
        )
    }

    @Test
    fun generatesThreeAlternativesAndRespectsDeadlineAndPlannedFilter() {
        val cities = loadCities()
        val spb = cities.getValue(CityId.SPB)

        val destOutcome = TramRouter.generateTramAdventure(
            city = CityId.SPB,
            stops = spb.stops,
            routes = spb.routes,
            landmarks = spb.landmarks,
            startStopId = "m_kupchino",
            destinationStopId = "metro_pl_vosstaniya",
            timeMode = TimePlanningMode.DEADLINE,
            deadlineMinutes = 18 * 60,
            includePlanned2027 = false,
            dayOfWeek = 3,
            timeMinutes = 15 * 60,
            modeBalance = ModeBalance(tram = 0.20f, transit = 0.45f, walk = 0.35f),
            seed = 515L
        )
        assertTrue(destOutcome is GenerationOutcome.Ok)
        val trip = (destOutcome as GenerationOutcome.Ok).trip
        assertEquals(3, trip.alternatives.size)
        assertEquals(18 * 60, trip.targetDeadlineMinutes)
        assertNotNull(trip.arrivalTimeMinutes)
        assertTrue(trip.arrivalTimeMinutes!! <= 18 * 60)
        assertTrue(trip.alternatives.flatMap { it.segments }.none { it.route?.planned2027 == true })
        assertTrue(
            trip.alternatives.any { alt ->
                alt.variantTitle?.contains("соседней", ignoreCase = true) == true ||
                    alt.segments.any { it.kind == SegmentKind.WALK }
            }
        )

        val randomOutcome = TramRouter.generateTramAdventure(
            city = CityId.SPB,
            stops = spb.stops,
            routes = spb.routes,
            landmarks = spb.landmarks,
            startStopId = spb.defaultStartStopId,
            timeMode = TimePlanningMode.DEADLINE,
            deadlineMinutes = 18 * 60,
            dayOfWeek = 3,
            timeMinutes = 15 * 60 + 30,
            modeBalance = ModeBalance(tram = 0.34f, transit = 0.33f, walk = 0.33f),
            seed = 777L
        )
        assertTrue(randomOutcome is GenerationOutcome.Ok)
        val rTrip = (randomOutcome as GenerationOutcome.Ok).trip
        assertEquals(3, rTrip.alternatives.size)
        assertTrue(rTrip.arrivalTimeMinutes!! <= 18 * 60 + 10)
    }

    @Test
    fun savedRoutesStatsAggregatesDistanceAndSpbDistrictsOnly() {
        val cities = loadCities()
        val spb = cities.getValue(CityId.SPB)
        val omsk = cities.getValue(CityId.OMSK)

        val spbOutcome = TramRouter.generateTramAdventure(
            city = CityId.SPB,
            stops = spb.stops,
            routes = spb.routes,
            landmarks = spb.landmarks,
            startStopId = spb.defaultStartStopId,
            durationPreset = DurationPreset.MEDIUM,
            dayOfWeek = 3,
            timeMinutes = 14 * 60,
            seed = 123L
        )
        val omskOutcome = TramRouter.generateTramAdventure(
            city = CityId.OMSK,
            stops = omsk.stops,
            routes = omsk.routes,
            landmarks = omsk.landmarks,
            startStopId = omsk.defaultStartStopId,
            durationPreset = DurationPreset.SHORT,
            dayOfWeek = 3,
            timeMinutes = 14 * 60,
            seed = 456L
        )
        assertTrue(spbOutcome is GenerationOutcome.Ok)
        assertTrue(omskOutcome is GenerationOutcome.Ok)

        val spbTrip = (spbOutcome as GenerationOutcome.Ok).trip
        val omskTrip = (omskOutcome as GenerationOutcome.Ok).trip
        val stopsById = (spb.stops + omsk.stops).associateBy { it.id }
        val spbTotalDistricts = spb.stops.map { it.district }.toSet().size

        val spbDist = TramRouter.calculateTripDistanceKm(spbTrip, stopsById)
        val omskDist = TramRouter.calculateTripDistanceKm(omskTrip, stopsById)
        assertTrue(spbDist > 0.0)
        assertTrue(omskDist > 0.0)

        val saved = listOf(
            SavedRoute(
                id = "fav-1",
                customTitle = "СПб маршрут",
                note = "",
                savedAtIso = "2026-11-21T00:00:00Z",
                trip = spbTrip
            ),
            SavedRoute(
                id = "fav-2",
                customTitle = "Омск маршрут",
                note = "",
                savedAtIso = "2026-11-21T00:00:00Z",
                trip = omskTrip
            )
        )

        val stats = TramRouter.computeSavedRoutesStats(saved, stopsById, spbTotalDistricts)
        assertEquals(2, stats.totalRoutesCount)
        assertEquals(1, stats.spbRoutesCount)
        assertTrue(abs(stats.totalDistanceKm - (spbDist + omskDist)) < 0.25)
        assertEquals(spbTrip.districtsVisited.toSet().size, stats.spbDistrictsCount)
        assertEquals(spbTotalDistricts, stats.spbDistrictsTotal)
        assertTrue(stats.spbDistrictNames.all { it in spbTrip.districtsVisited })
    }

    @Test
    fun officialAudioGuidesMatchSpbLinesAndOmskCity() {
        val cities = loadCities()
        val spb = cities.getValue(CityId.SPB)
        val omsk = cities.getValue(CityId.OMSK)

        assertTrue(TramRouter.OFFICIAL_AUDIO_GUIDES.all { it.url.startsWith("https://") })

        val spbOutcome = TramRouter.generateTramAdventure(
            city = CityId.SPB,
            stops = spb.stops,
            routes = spb.routes,
            landmarks = spb.landmarks,
            startStopId = spb.defaultStartStopId,
            durationPreset = DurationPreset.MEDIUM,
            dayOfWeek = 3,
            timeMinutes = 14 * 60,
            seed = 123L
        ) as GenerationOutcome.Ok
        val spbGuides = TramRouter.matchAudioGuidesForTrip(spbOutcome.trip, limit = 3)
        assertEquals(3, spbGuides.size)
        assertTrue(spbGuides.all { it.guide.city == CityId.SPB })

        val t1Trip = spbOutcome.trip.copy(
            tramLinesUsed = listOf("3"),
            districtsVisited = listOf("Адмиралтейский", "Центральный")
        )
        val route3Guides = TramRouter.matchAudioGuidesForTrip(t1Trip, limit = 3)
        assertEquals("spb-get-route-3", route3Guides.first().guide.id)
        assertTrue(route3Guides.first().matchReason.contains("№ 3"))

        val omskOutcome = TramRouter.generateTramAdventure(
            city = CityId.OMSK,
            stops = omsk.stops,
            routes = omsk.routes,
            landmarks = omsk.landmarks,
            startStopId = omsk.defaultStartStopId,
            durationPreset = DurationPreset.SHORT,
            dayOfWeek = 3,
            timeMinutes = 14 * 60,
            seed = 77L
        ) as GenerationOutcome.Ok
        val omskGuides = TramRouter.matchAudioGuidesForTrip(omskOutcome.trip, limit = 3)
        assertEquals(3, omskGuides.size)
        assertTrue(omskGuides.all { it.guide.city == CityId.OMSK })

        val multiLineTrip = spbOutcome.trip.copy(
            tramLinesUsed = listOf("3", "52", "60"),
            districtsVisited = listOf("Адмиралтейский", "Кировский")
        )
        val step3Guide = TramRouter.matchAudioGuidesForTrip(multiLineTrip, limit = 1, preferredRouteNumber = "3").first()
        assertEquals("spb-get-route-3", step3Guide.guide.id)
        assertTrue(step3Guide.matchReason.contains("№ 3"))
    }
}


