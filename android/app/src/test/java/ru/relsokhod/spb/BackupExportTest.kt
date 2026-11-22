package ru.relsokhod.spb

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.relsokhod.spb.model.AccentSeed
import ru.relsokhod.spb.model.CityId
import ru.relsokhod.spb.model.DurationPreset
import ru.relsokhod.spb.model.GeneratedTrip
import ru.relsokhod.spb.model.ModeBalance
import ru.relsokhod.spb.model.RouteSchedule
import ru.relsokhod.spb.model.RouteSegment
import ru.relsokhod.spb.model.RouteThemeFilter
import ru.relsokhod.spb.model.SavedRoute
import ru.relsokhod.spb.model.SearchParamsState
import ru.relsokhod.spb.model.SegmentKind
import ru.relsokhod.spb.model.ThemeMode
import ru.relsokhod.spb.model.TramRoute
import ru.relsokhod.spb.model.TramStop
import ru.relsokhod.spb.model.TripHistoryEntry
import ru.relsokhod.spb.model.TripPhotoMemory
import ru.relsokhod.spb.model.WeatherMode
import ru.relsokhod.spb.storage.BackupCodec
import ru.relsokhod.spb.storage.BackupDecodeResult
import java.util.Calendar

class BackupExportTest {

    @Test
    fun buildsDatedRelsohodFilename() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 22, 10, 0, 0)
        }
        assertEquals("export-2026-11-22.relsohod", BackupCodec.buildBackupFilename(cal.time))
        assertTrue(BackupCodec.isAcceptedBackupFilename("export-2026-11-22.relsohod"))
        assertTrue(BackupCodec.isAcceptedBackupFilename("export-2026-11-22.relsokhod"))
        assertFalse(BackupCodec.isAcceptedBackupFilename("archive.zip"))
    }

    @Test
    fun roundTripsAndroidBackupAndDecodesWebFormat() {
        val stopA = TramStop("spb_sen", "Сенная площадь", "Адмиралтейский", 59.927, 30.320)
        val stopB = TramStop("spb_sad", "Садовая", "Адмиралтейский", 59.926, 30.317)
        val route = TramRoute(
            id = "spb_3",
            number = "3",
            title = "Площадь Репина — Финляндский вокзал",
            colorHex = "#0F5E8C",
            type = "regular",
            themes = listOf("center"),
            schedule = RouteSchedule(listOf(0, 1, 2, 3, 4, 5, 6), 360, 1440, 10, "06:00–24:00"),
            stopIds = listOf("spb_sen", "spb_sad"),
            minutesPerHop = 3,
            description = ""
        )
        val trip = GeneratedTrip(
            id = "trip-1",
            city = CityId.SPB,
            createdAtIso = "2026-11-22T10:00:00Z",
            startStop = stopA,
            endStop = stopB,
            segments = listOf(
                RouteSegment(
                    id = "seg-1",
                    kind = SegmentKind.TRAM,
                    route = route,
                    fallbackModeName = null,
                    fromStop = stopA,
                    toStop = stopB,
                    stopIds = listOf("spb_sen", "spb_sad"),
                    stopsCount = 1,
                    durationMinutes = 4,
                    directionNote = "К центру"
                )
            ),
            tramLinesUsed = listOf("3"),
            transferCount = 0,
            totalDurationMinutes = 4,
            usedFallbackTransit = false,
            isLoop = false,
            weatherMode = WeatherMode.RAIN,
            modeBalance = ModeBalance(0.7f, 0.1f, 0.2f),
            districtsVisited = listOf("Адмиралтейский"),
            landmarks = emptyList(),
            dayOfWeek = 3,
            timeMinutes = 840
        )

        val raw = BackupCodec.encodeBackupJson(
            city = CityId.OMSK,
            themeMode = ThemeMode.DARK,
            accentSeed = AccentSeed.SAGE,
            photoReminderMinutes = 45,
            onboardingDone = true,
            spbParams = SearchParamsState(startStopId = "spb_sen", loopMode = true),
            omskParams = SearchParamsState(
                startStopId = "omsk_cirk",
                themeFilter = RouteThemeFilter.PARKS,
                durationPreset = DurationPreset.LONG,
                weatherMode = WeatherMode.FROST
            ),
            savedRoutes = listOf(SavedRoute("s-1", "По центру", "Заметка", "2026-11-22T11:00:00Z", trip)),
            history = listOf(
                TripHistoryEntry(
                    id = "h-1",
                    completedAtIso = "2026-11-22T12:00:00Z",
                    trip = trip,
                    photos = listOf(TripPhotoMemory("p-1", "/tmp/p1.jpg", "Мост", "2026-11-22T11:50:00Z"))
                )
            ),
            photoToDataUrl = { "data:image/jpeg;base64,/9j/4AAQ" }
        )

        val res = BackupCodec.decodeBackupJson(
            rawText = raw,
            dataUrlToFilePath = { id, url ->
                assertTrue(url.startsWith("data:image/"))
                "/restored/$id.jpg"
            }
        )
        assertTrue(res is BackupDecodeResult.Success)
        val ok = (res as BackupDecodeResult.Success).data
        assertEquals(CityId.OMSK, ok.city)
        assertEquals(ThemeMode.DARK, ok.themeMode)
        assertEquals(AccentSeed.SAGE, ok.accentSeed)
        assertEquals(45, ok.photoReminderMinutes)
        assertTrue(ok.spbParams.loopMode)
        assertEquals(WeatherMode.FROST, ok.omskParams.weatherMode)
        assertEquals(1, ok.savedRoutes.size)
        assertEquals(1, ok.history.size)
        assertEquals("/restored/p-1.jpg", ok.history[0].photos[0].filePath)
        assertEquals(1, ok.photosRestoredCount)
    }

    @Test
    fun decodesWebExportedBackupAndRejectsWrongFormat() {
        val webJson = JSONObject().apply {
            put("format", BackupCodec.FORMAT_ID)
            put("version", 1)
            put("source", "web")
            put(
                "settings",
                JSONObject().apply {
                    put("city", "spb")
                    put("theme", "tram")
                    put("accentSeed", "terracotta")
                    put("photoReminderMinutes", 15)
                }
            )
            put(
                "planner",
                JSONObject().apply {
                    put(
                        "active",
                        JSONObject().apply {
                            put("startStopId", "spb_sen")
                            put("loopMode", true)
                        }
                    )
                }
            )
            put("savedRoutes", JSONArray())
            put("tripHistory", JSONArray())
        }.toString()

        val webRes = BackupCodec.decodeBackupJson(webJson)
        assertTrue(webRes is BackupDecodeResult.Success)
        val decoded = (webRes as BackupDecodeResult.Success).data
        assertEquals(ThemeMode.TRAM, decoded.themeMode)
        assertEquals(AccentSeed.TERRACOTTA, decoded.accentSeed)
        assertEquals(15, decoded.photoReminderMinutes)
        assertTrue(decoded.spbParams.loopMode)

        val badRes = BackupCodec.decodeBackupJson("""{"format":"unknown","version":1}""")
        assertTrue(badRes is BackupDecodeResult.Error)
    }
}
