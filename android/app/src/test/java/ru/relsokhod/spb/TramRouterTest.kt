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
import ru.relsokhod.spb.model.RouteThemeFilter
import ru.relsokhod.spb.model.WeatherMode
import ru.relsokhod.spb.router.TramRouter
import java.io.File

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
        assertTrue(spb!!.stops.size >= 60)
        assertTrue(spb.routes.size >= 25)
        assertTrue(omsk!!.stops.size >= 25)
        assertTrue(omsk.routes.size >= 6)
    }

    @Test
    fun tramAScheduleOnlyWeekdays() {
        val cities = loadCities()
        val spb = cities.getValue(CityId.SPB)
        val tramA = spb.routes.first { it.id == "spb_a" }
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
}
