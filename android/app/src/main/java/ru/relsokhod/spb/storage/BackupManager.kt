package ru.relsokhod.spb.storage

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ru.relsokhod.spb.model.AccentSeed
import ru.relsokhod.spb.model.CityConfig
import ru.relsokhod.spb.model.CityId
import ru.relsokhod.spb.model.DurationPreset
import ru.relsokhod.spb.model.GeneratedTrip
import ru.relsokhod.spb.model.Landmark
import ru.relsokhod.spb.model.LandmarkCategory
import ru.relsokhod.spb.model.ModeBalance
import ru.relsokhod.spb.model.RouteSchedule
import ru.relsokhod.spb.model.RouteSegment
import ru.relsokhod.spb.model.RouteThemeFilter
import ru.relsokhod.spb.model.SavedRoute
import ru.relsokhod.spb.model.SearchParamsState
import ru.relsokhod.spb.model.SegmentKind
import ru.relsokhod.spb.model.ThemeMode
import ru.relsokhod.spb.model.TimePlanningMode
import ru.relsokhod.spb.model.TramRoute
import ru.relsokhod.spb.model.TramStop
import ru.relsokhod.spb.model.TripHistoryEntry
import ru.relsokhod.spb.model.TripPhotoMemory
import ru.relsokhod.spb.model.WeatherMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DecodedBackupData(
    val city: CityId,
    val themeMode: ThemeMode,
    val accentSeed: AccentSeed,
    val photoReminderMinutes: Int,
    val spbParams: SearchParamsState,
    val omskParams: SearchParamsState,
    val savedRoutes: List<SavedRoute>,
    val history: List<TripHistoryEntry>,
    val photosRestoredCount: Int
)

sealed interface BackupDecodeResult {
    data class Success(val data: DecodedBackupData) : BackupDecodeResult
    data class Error(val message: String) : BackupDecodeResult
}

object BackupCodec {
    const val FORMAT_ID = "relsokhod-backup"
    const val FORMAT_VERSION = 1
    const val MAX_BACKUP_BYTES = 35L * 1024L * 1024L
    private const val MAX_PHOTOS_PER_IMPORT = 120
    private val VALID_REMINDERS = setOf(0, 15, 30, 45, 60)

    fun buildBackupFilename(date: Date = Date()): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return "export-${fmt.format(date)}.relsohod"
    }

    fun isAcceptedBackupFilename(name: String?): Boolean {
        if (name.isNullOrBlank()) return true
        val lower = name.trim().lowercase(Locale.US)
        return lower.endsWith(".relsohod") || lower.endsWith(".relsokhod") || lower.endsWith(".json")
    }

    fun encodeBackupJson(
        city: CityId,
        themeMode: ThemeMode,
        accentSeed: AccentSeed,
        photoReminderMinutes: Int,
        onboardingDone: Boolean,
        spbParams: SearchParamsState,
        omskParams: SearchParamsState,
        savedRoutes: List<SavedRoute>,
        history: List<TripHistoryEntry>,
        exportedAtIso: String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()),
        photoToDataUrl: (TripPhotoMemory) -> String? = { null }
    ): String {
        val activeParams = if (city == CityId.OMSK) omskParams else spbParams
        val root = JSONObject().apply {
            put("format", FORMAT_ID)
            put("version", FORMAT_VERSION)
            put("exportedAt", exportedAtIso)
            put("source", "android")
            put(
                "settings",
                JSONObject().apply {
                    put("city", city.id)
                    put("theme", themeMode.id)
                    put("accentSeed", accentSeed.id)
                    put("photoReminderMinutes", photoReminderMinutes)
                    put("onboardingDone", onboardingDone)
                }
            )
            put(
                "planner",
                JSONObject().apply {
                    put("active", paramsToJson(city, activeParams))
                    put("spb", paramsToJson(CityId.SPB, spbParams))
                    put("omsk", paramsToJson(CityId.OMSK, omskParams))
                }
            )

            val savedArr = JSONArray()
            for (item in savedRoutes) {
                savedArr.put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("customTitle", item.customTitle)
                        put("note", item.note)
                        put("savedAt", item.savedAtIso)
                        put("savedAtIso", item.savedAtIso)
                        put("trip", tripToJson(item.trip))
                    }
                )
            }
            put("savedRoutes", savedArr)

            val histArr = JSONArray()
            for (entry in history) {
                val photosArr = JSONArray()
                for (p in entry.photos) {
                    val dataUrl = photoToDataUrl(p)
                    photosArr.put(
                        JSONObject().apply {
                            put("id", p.id)
                            if (!dataUrl.isNullOrBlank()) {
                                put("dataUrl", dataUrl)
                            }
                            put("filePath", p.filePath)
                            put("caption", p.caption)
                            put("takenAt", p.takenAtIso)
                            put("takenAtIso", p.takenAtIso)
                        }
                    )
                }
                histArr.put(
                    JSONObject().apply {
                        put("id", entry.id)
                        put("city", entry.trip.city.id)
                        put("completedAt", entry.completedAtIso)
                        put("completedAtIso", entry.completedAtIso)
                        put("trip", tripToJson(entry.trip))
                        put("photos", photosArr)
                    }
                )
            }
            put("tripHistory", histArr)
        }
        return root.toString(2)
    }

    fun decodeBackupJson(
        rawText: String,
        cities: Map<CityId, CityConfig> = emptyMap(),
        dataUrlToFilePath: (photoId: String, dataUrl: String) -> String? = { _, _ -> null },
        localFileExists: (String) -> Boolean = { File(it).exists() }
    ): BackupDecodeResult {
        if (rawText.isBlank()) {
            return BackupDecodeResult.Error("Выбранный файл пуст.")
        }
        if (rawText.length > MAX_BACKUP_BYTES) {
            return BackupDecodeResult.Error("Файл слишком большой (превышен лимит 35 МБ).")
        }

        val root = runCatching { JSONObject(rawText) }.getOrNull()
            ?: return BackupDecodeResult.Error("Файл повреждён или не является JSON-копией .relsohod.")

        if (root.optString("format", "") != FORMAT_ID) {
            return BackupDecodeResult.Error("Этот файл не является резервной копией «Рельсохода» (.relsohod).")
        }
        val version = root.optInt("version", 0)
        if (version !in 1..FORMAT_VERSION) {
            return BackupDecodeResult.Error("Неподдерживаемая версия формата (v$version). Обновите приложение.")
        }

        val settingsObj = root.optJSONObject("settings") ?: JSONObject()
        val city = CityId.fromId(settingsObj.optString("city", "spb"))
        val themeMode = ThemeMode.fromId(settingsObj.optString("theme", "light"))
        val accentSeed = AccentSeed.fromId(settingsObj.optString("accentSeed", "neva"))
        val rawReminder = settingsObj.optInt("photoReminderMinutes", 30)
        val photoReminderMinutes = if (rawReminder in VALID_REMINDERS) rawReminder else 30

        val plannerObj = root.optJSONObject("planner") ?: JSONObject()
        val activeObj = plannerObj.optJSONObject("active")
        val spbDefault = cities[CityId.SPB]?.defaultStartStopId ?: "spb_sen"
        val omskDefault = cities[CityId.OMSK]?.defaultStartStopId ?: "omsk_cirk"

        val spbJson = plannerObj.optJSONObject("spb")
            ?: if (city == CityId.SPB) activeObj else null
        val omskJson = plannerObj.optJSONObject("omsk")
            ?: if (city == CityId.OMSK) activeObj else null

        val spbParams = paramsFromJson(spbJson, cities[CityId.SPB], spbDefault)
        val omskParams = paramsFromJson(omskJson, cities[CityId.OMSK], omskDefault)

        val savedArr = root.optJSONArray("savedRoutes") ?: JSONArray()
        val savedRoutes = mutableListOf<SavedRoute>()
        for (i in 0 until savedArr.length()) {
            val item = savedArr.optJSONObject(i) ?: continue
            val tripObj = item.optJSONObject("trip") ?: continue
            val trip = tripFromJson(tripObj, city, cities) ?: continue
            val savedAt = item.optString("savedAt", "").ifBlank {
                item.optString("savedAtIso", "")
            }.ifBlank { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()) }
            val fallbackTitle = "${trip.startStop.name.substringBefore("(").trim()} → ${trip.endStop.name.substringBefore("(").trim()}"
            savedRoutes.add(
                SavedRoute(
                    id = item.optString("id", "").ifBlank { "saved-${savedRoutes.size + 1}" },
                    customTitle = item.optString("customTitle", "").ifBlank { fallbackTitle },
                    note = item.optString("note", ""),
                    savedAtIso = savedAt,
                    trip = trip
                )
            )
        }

        val histArr = root.optJSONArray("tripHistory") ?: JSONArray()
        val history = mutableListOf<TripHistoryEntry>()
        var photosRestored = 0
        for (i in 0 until minOf(histArr.length(), 50)) {
            val item = histArr.optJSONObject(i) ?: continue
            val tripObj = item.optJSONObject("trip") ?: continue
            val trip = tripFromJson(tripObj, city, cities) ?: continue
            val completedAt = item.optString("completedAt", "").ifBlank {
                item.optString("completedAtIso", "")
            }.ifBlank { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()) }

            val photosArr = item.optJSONArray("photos") ?: JSONArray()
            val photos = mutableListOf<TripPhotoMemory>()
            for (pIdx in 0 until photosArr.length()) {
                if (photosRestored >= MAX_PHOTOS_PER_IMPORT) break
                val po = photosArr.optJSONObject(pIdx) ?: continue
                val photoId = po.optString("id", "").ifBlank { "photo-${photosRestored + 1}" }
                val dataUrl = po.optString("dataUrl", "").trim()
                val existingPath = po.optString("filePath", "").trim()

                val resolvedPath = when {
                    dataUrl.isNotBlank() -> dataUrlToFilePath(photoId, dataUrl)
                    existingPath.isNotBlank() && localFileExists(existingPath) -> existingPath
                    else -> null
                } ?: continue

                photosRestored++
                val takenAt = po.optString("takenAt", "").ifBlank {
                    po.optString("takenAtIso", "")
                }.ifBlank { completedAt }

                photos.add(
                    TripPhotoMemory(
                        id = photoId,
                        filePath = resolvedPath,
                        caption = po.optString("caption", "Фото из поездки"),
                        takenAtIso = takenAt
                    )
                )
            }

            history.add(
                TripHistoryEntry(
                    id = item.optString("id", "").ifBlank { "hist-${history.size + 1}" },
                    completedAtIso = completedAt,
                    trip = trip,
                    photos = photos
                )
            )
        }

        return BackupDecodeResult.Success(
            DecodedBackupData(
                city = city,
                themeMode = themeMode,
                accentSeed = accentSeed,
                photoReminderMinutes = photoReminderMinutes,
                spbParams = spbParams,
                omskParams = omskParams,
                savedRoutes = savedRoutes,
                history = history,
                photosRestoredCount = photosRestored
            )
        )
    }

    private fun paramsToJson(city: CityId, state: SearchParamsState): JSONObject = JSONObject().apply {
        put("city", city.id)
        put("startStopId", state.startStopId)
        put("destinationStopId", state.destinationStopId ?: JSONObject.NULL)
        put("destinationAddressQuery", state.destinationAddressQuery)
        put("destinationAddressLabel", state.destinationAddressLabel)
        put("themeFilter", state.themeFilter.id)
        put("durationPreset", state.durationPreset.id)
        put("timeMode", state.timeMode.id)
        put("deadlineMinutes", state.deadlineMinutes)
        put("dayOfWeek", state.dayOfWeek)
        put("timeMinutes", state.timeMinutes)
        put("allowFallbackTransit", state.allowFallbackTransit)
        put("includePlanned2027", state.includePlanned2027)
        put(
            "modeBalance",
            JSONObject().apply {
                put("tram", state.modeBalance.tram.toDouble())
                put("transit", state.modeBalance.transit.toDouble())
                put("walk", state.modeBalance.walk.toDouble())
            }
        )
        put("showAllNetwork", state.showAllNetwork)
        put("loopMode", state.loopMode)
        put("weatherMode", state.weatherMode.id)
        put("preferSpecialFleet", state.preferSpecialFleet)
    }

    private fun paramsFromJson(
        o: JSONObject?,
        cityCfg: CityConfig?,
        defaultStartId: String
    ): SearchParamsState {
        if (o == null) return SearchParamsState(startStopId = defaultStartId)
        val rawStart = o.optString("startStopId", defaultStartId).ifBlank { defaultStartId }
        val validStart = if (cityCfg == null || cityCfg.stops.any { it.id == rawStart }) rawStart else defaultStartId
        val rawDest = if (o.isNull("destinationStopId")) null else o.optString("destinationStopId").ifBlank { null }
        val validDest = if (rawDest != null && (cityCfg == null || cityCfg.stops.any { it.id == rawDest })) rawDest else null

        val mbObj = o.optJSONObject("modeBalance")
        val modeBalance = if (mbObj != null) {
            ModeBalance(
                tram = mbObj.optDouble("tram", 0.72).toFloat(),
                transit = mbObj.optDouble("transit", 0.08).toFloat(),
                walk = mbObj.optDouble("walk", 0.20).toFloat()
            ).normalized()
        } else {
            ModeBalance()
        }

        return SearchParamsState(
            startStopId = validStart,
            destinationStopId = validDest,
            destinationAddressQuery = o.optString("destinationAddressQuery", ""),
            destinationAddressLabel = o.optString("destinationAddressLabel", ""),
            themeFilter = RouteThemeFilter.fromId(o.optString("themeFilter", "any")),
            durationPreset = DurationPreset.fromId(o.optString("durationPreset", "medium")),
            timeMode = TimePlanningMode.fromId(o.optString("timeMode", "duration")),
            deadlineMinutes = o.optInt("deadlineMinutes", 18 * 60).coerceIn(0, 1439),
            dayOfWeek = o.optInt("dayOfWeek", 3).coerceIn(0, 6),
            timeMinutes = o.optInt("timeMinutes", 14 * 60).coerceIn(0, 1439),
            allowFallbackTransit = o.optBoolean("allowFallbackTransit", false),
            includePlanned2027 = o.optBoolean("includePlanned2027", true),
            modeBalance = modeBalance,
            showAllNetwork = o.optBoolean("showAllNetwork", true),
            loopMode = o.optBoolean("loopMode", false),
            weatherMode = WeatherMode.fromId(o.optString("weatherMode", "clear")),
            preferSpecialFleet = o.optBoolean("preferSpecialFleet", false)
        )
    }

    private fun stopToJson(s: TramStop): JSONObject = JSONObject().apply {
        put("id", s.id)
        put("name", s.name)
        put("district", s.district)
        put("lat", s.lat)
        put("lng", s.lng)
        put("stopKind", s.stopKind ?: JSONObject.NULL)
        put("hubId", s.hubId ?: JSONObject.NULL)
        put("isolatedFromMainTram", s.isolatedFromMainTram)
        put("planned2027", s.planned2027)
        put("plannedLabel", s.plannedLabel ?: JSONObject.NULL)
    }

    private fun stopFromJson(o: JSONObject?, cityCfg: CityConfig?): TramStop? {
        if (o == null) return null
        val id = o.optString("id", "").trim()
        val name = o.optString("name", "").trim()
        if (id.isBlank() || name.isBlank()) return null
        val catalog = cityCfg?.stops?.firstOrNull { it.id == id }
        val lat = o.optDouble("lat", catalog?.lat ?: cityCfg?.centerLat ?: 59.93)
        val lng = o.optDouble("lng", catalog?.lng ?: cityCfg?.centerLng ?: 30.33)
        return TramStop(
            id = id,
            name = name,
            district = o.optString("district", catalog?.district ?: ""),
            lat = lat,
            lng = lng,
            stopKind = if (o.isNull("stopKind")) catalog?.stopKind else o.optString("stopKind").ifBlank { catalog?.stopKind },
            hubId = if (o.isNull("hubId")) catalog?.hubId else o.optString("hubId").ifBlank { catalog?.hubId },
            isolatedFromMainTram = o.optBoolean("isolatedFromMainTram", catalog?.isolatedFromMainTram ?: false),
            planned2027 = o.optBoolean("planned2027", catalog?.planned2027 ?: false),
            plannedLabel = if (o.isNull("plannedLabel")) catalog?.plannedLabel else o.optString("plannedLabel").ifBlank { catalog?.plannedLabel }
        )
    }

    private fun routeToJson(r: TramRoute): JSONObject = JSONObject().apply {
        put("id", r.id)
        put("number", r.number)
        put("title", r.title)
        put("color", r.colorHex)
        put("colorHex", r.colorHex)
        put("type", r.type)
        put("themes", JSONArray(r.themes))
        put("stopIds", JSONArray(r.stopIds))
        put("minutesPerHop", r.minutesPerHop)
        put("description", r.description)
        put("planned2027", r.planned2027)
        put("plannedLabel", r.plannedLabel ?: JSONObject.NULL)
        put(
            "schedule",
            JSONObject().apply {
                put("daysOfWeek", JSONArray(r.schedule.daysOfWeek))
                put("startMinutes", r.schedule.startMinutes)
                put("endMinutes", r.schedule.endMinutes)
                put("intervalMinutes", r.schedule.intervalMinutes)
                put("note", r.schedule.label)
                put("label", r.schedule.label)
            }
        )
    }

    private fun routeFromJson(o: JSONObject?, cityCfg: CityConfig?): TramRoute? {
        if (o == null) return null
        val id = o.optString("id", "").trim()
        val number = o.optString("number", "").trim()
        if (id.isBlank() || number.isBlank()) return null
        val catalog = cityCfg?.routes?.firstOrNull { it.id == id }

        val schedObj = o.optJSONObject("schedule")
        val daysArr = schedObj?.optJSONArray("daysOfWeek")
        val days = if (daysArr != null && daysArr.length() > 0) {
            List(daysArr.length()) { daysArr.optInt(it) }
        } else {
            catalog?.schedule?.daysOfWeek ?: listOf(0, 1, 2, 3, 4, 5, 6)
        }

        val themesArr = o.optJSONArray("themes")
        val themes = if (themesArr != null && themesArr.length() > 0) {
            List(themesArr.length()) { themesArr.optString(it) }.filter { it.isNotBlank() }
        } else {
            catalog?.themes ?: listOf("center")
        }

        val stopIdsArr = o.optJSONArray("stopIds")
        val stopIds = if (stopIdsArr != null && stopIdsArr.length() > 0) {
            List(stopIdsArr.length()) { stopIdsArr.optString(it) }.filter { it.isNotBlank() }
        } else {
            catalog?.stopIds ?: emptyList()
        }

        val color = o.optString("colorHex", "").ifBlank {
            o.optString("color", "")
        }.ifBlank { catalog?.colorHex ?: "#0F5E8C" }

        val label = schedObj?.optString("label", "")?.ifBlank {
            schedObj.optString("note", "")
        }?.ifBlank { catalog?.schedule?.label ?: "" } ?: (catalog?.schedule?.label ?: "")

        return TramRoute(
            id = id,
            number = number,
            title = o.optString("title", "").ifBlank { catalog?.title ?: "Маршрут №$number" },
            colorHex = color,
            type = o.optString("type", "").ifBlank { catalog?.type ?: "regular" },
            themes = themes,
            schedule = RouteSchedule(
                daysOfWeek = days,
                startMinutes = schedObj?.optInt("startMinutes", catalog?.schedule?.startMinutes ?: 360)
                    ?: (catalog?.schedule?.startMinutes ?: 360),
                endMinutes = schedObj?.optInt("endMinutes", catalog?.schedule?.endMinutes ?: 1440)
                    ?: (catalog?.schedule?.endMinutes ?: 1440),
                intervalMinutes = schedObj?.optInt("intervalMinutes", catalog?.schedule?.intervalMinutes ?: 12)
                    ?: (catalog?.schedule?.intervalMinutes ?: 12),
                label = label
            ),
            stopIds = stopIds,
            minutesPerHop = o.optInt("minutesPerHop", catalog?.minutesPerHop ?: 3),
            description = o.optString("description", "").ifBlank { catalog?.description ?: "" },
            planned2027 = o.optBoolean("planned2027", catalog?.planned2027 ?: false),
            plannedLabel = if (o.isNull("plannedLabel")) catalog?.plannedLabel else o.optString("plannedLabel").ifBlank { catalog?.plannedLabel }
        )
    }

    private fun tripToJson(t: GeneratedTrip): JSONObject = JSONObject().apply {
        put("id", t.id)
        put("city", t.city.id)
        put("createdAt", t.createdAtIso)
        put("createdAtIso", t.createdAtIso)
        put("startStop", stopToJson(t.startStop))
        put("endStop", stopToJson(t.endStop))
        put("tramLinesUsed", JSONArray(t.tramLinesUsed))
        put("transferCount", t.transferCount)
        put("totalDurationMinutes", t.totalDurationMinutes)
        put("usedFallbackTransit", t.usedFallbackTransit)
        put("isLoop", t.isLoop)
        put("weatherMode", t.weatherMode.id)
        put(
            "modeBalance",
            JSONObject().apply {
                put("tram", t.modeBalance.tram.toDouble())
                put("transit", t.modeBalance.transit.toDouble())
                put("walk", t.modeBalance.walk.toDouble())
            }
        )
        put("districtsVisited", JSONArray(t.districtsVisited))
        put("dayOfWeek", t.dayOfWeek)
        put("timeMinutes", t.timeMinutes)
        if (t.variantTitle != null) put("variantTitle", t.variantTitle)
        if (t.variantBadge != null) put("variantBadge", t.variantBadge)
        if (t.arrivalTimeMinutes != null) put("arrivalTimeMinutes", t.arrivalTimeMinutes)
        if (t.targetDeadlineMinutes != null) put("targetDeadlineMinutes", t.targetDeadlineMinutes)
        if (t.walkDistanceKm != null) put("walkDistanceKm", t.walkDistanceKm)

        val segArr = JSONArray()
        for (seg in t.segments) {
            val kindWire = when (seg.kind) {
                SegmentKind.TRAM -> "tram"
                SegmentKind.WALK -> "walk"
                SegmentKind.FALLBACK_TRANSIT -> "fallback_transit"
            }
            segArr.put(
                JSONObject().apply {
                    put("id", seg.id)
                    put("kind", kindWire)
                    put("route", seg.route?.let { routeToJson(it) } ?: JSONObject.NULL)
                    put("fallbackModeName", seg.fallbackModeName ?: JSONObject.NULL)
                    put("fromStop", stopToJson(seg.fromStop))
                    put("toStop", stopToJson(seg.toStop))
                    put("stopIds", JSONArray(seg.stopIds))
                    put("stopsCount", seg.stopsCount)
                    put("durationMinutes", seg.durationMinutes)
                    put("directionNote", seg.directionNote)
                }
            )
        }
        put("segments", segArr)

        val lmArr = JSONArray()
        for (lm in t.landmarks) {
            lmArr.put(
                JSONObject().apply {
                    put("id", lm.id)
                    put("name", lm.name)
                    put("category", lm.category.id)
                    put("district", lm.district)
                    put("description", lm.description)
                    put("lat", lm.lat)
                    put("lng", lm.lng)
                    put("nearbyStopIds", JSONArray(lm.nearbyStopIds))
                    put("hasPhoto", lm.hasPhoto)
                }
            )
        }
        put("landmarks", lmArr)
    }

    private fun tripFromJson(
        o: JSONObject,
        fallbackCity: CityId,
        cities: Map<CityId, CityConfig>
    ): GeneratedTrip? {
        val rawCity = o.optString("city", "")
        val city = if (rawCity == "omsk" || rawCity == "spb") CityId.fromId(rawCity) else fallbackCity
        val cityCfg = cities[city]

        val startStop = stopFromJson(o.optJSONObject("startStop"), cityCfg) ?: return null
        val endStop = stopFromJson(o.optJSONObject("endStop"), cityCfg) ?: return null

        val segArr = o.optJSONArray("segments") ?: return null
        val segs = mutableListOf<RouteSegment>()
        for (i in 0 until segArr.length()) {
            val so = segArr.optJSONObject(i) ?: continue
            val fromStop = stopFromJson(so.optJSONObject("fromStop"), cityCfg) ?: continue
            val toStop = stopFromJson(so.optJSONObject("toStop"), cityCfg) ?: continue
            val kind = when (so.optString("kind", "tram").lowercase(Locale.US)) {
                "walk" -> SegmentKind.WALK
                "fallback_transit" -> SegmentKind.FALLBACK_TRANSIT
                else -> SegmentKind.TRAM
            }
            val idsArr = so.optJSONArray("stopIds")
            val stopIds = if (idsArr != null && idsArr.length() > 0) {
                List(idsArr.length()) { idsArr.optString(it) }.filter { it.isNotBlank() }
            } else {
                listOf(fromStop.id, toStop.id)
            }
            val route = if (kind == SegmentKind.TRAM && !so.isNull("route")) {
                routeFromJson(so.optJSONObject("route"), cityCfg)
            } else {
                null
            }
            segs.add(
                RouteSegment(
                    id = so.optString("id", "").ifBlank { "seg-${segs.size + 1}" },
                    kind = kind,
                    route = route,
                    fallbackModeName = if (so.isNull("fallbackModeName")) null else so.optString("fallbackModeName").ifBlank { null },
                    fromStop = fromStop,
                    toStop = toStop,
                    stopIds = stopIds,
                    stopsCount = so.optInt("stopsCount", maxOf(0, stopIds.size - 1)),
                    durationMinutes = maxOf(1, so.optInt("durationMinutes", 5)),
                    directionNote = so.optString("directionNote", "")
                )
            )
        }
        if (segs.isEmpty()) return null

        val lmArr = o.optJSONArray("landmarks") ?: JSONArray()
        val lms = mutableListOf<Landmark>()
        for (i in 0 until lmArr.length()) {
            val lo = lmArr.optJSONObject(i) ?: continue
            val id = lo.optString("id", "").trim()
            val name = lo.optString("name", "").trim()
            if (id.isBlank() || name.isBlank()) continue
            val catalogLm = cityCfg?.landmarks?.firstOrNull { it.id == id }
            val nArr = lo.optJSONArray("nearbyStopIds")
            val nearby = if (nArr != null && nArr.length() > 0) {
                List(nArr.length()) { nArr.optString(it) }.filter { it.isNotBlank() }
            } else {
                catalogLm?.nearbyStopIds ?: emptyList()
            }
            val hasPhoto = if (lo.has("hasPhoto")) {
                lo.optBoolean("hasPhoto", false)
            } else {
                !lo.isNull("photoUrl") && lo.optString("photoUrl", "").isNotBlank()
            }
            lms.add(
                Landmark(
                    id = id,
                    name = name,
                    category = LandmarkCategory.fromId(lo.optString("category", catalogLm?.category?.id ?: "architecture")),
                    district = lo.optString("district", catalogLm?.district ?: ""),
                    description = lo.optString("description", catalogLm?.description ?: ""),
                    lat = lo.optDouble("lat", catalogLm?.lat ?: cityCfg?.centerLat ?: 59.93),
                    lng = lo.optDouble("lng", catalogLm?.lng ?: cityCfg?.centerLng ?: 30.33),
                    nearbyStopIds = nearby,
                    hasPhoto = hasPhoto
                )
            )
        }

        val linesArr = o.optJSONArray("tramLinesUsed") ?: JSONArray()
        val distArr = o.optJSONArray("districtsVisited") ?: JSONArray()
        val mbObj = o.optJSONObject("modeBalance")
        val modeBalance = if (mbObj != null) {
            ModeBalance(
                tram = mbObj.optDouble("tram", 0.72).toFloat(),
                transit = mbObj.optDouble("transit", 0.08).toFloat(),
                walk = mbObj.optDouble("walk", 0.20).toFloat()
            ).normalized()
        } else {
            ModeBalance()
        }
        val createdAt = o.optString("createdAt", "").ifBlank {
            o.optString("createdAtIso", "")
        }.ifBlank { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()) }

        return GeneratedTrip(
            id = o.optString("id", "").ifBlank { "trip-${System.currentTimeMillis()}" },
            city = city,
            createdAtIso = createdAt,
            startStop = startStop,
            endStop = endStop,
            segments = segs,
            tramLinesUsed = List(linesArr.length()) { linesArr.optString(it) }.filter { it.isNotBlank() },
            transferCount = maxOf(0, o.optInt("transferCount", 0)),
            totalDurationMinutes = maxOf(1, o.optInt("totalDurationMinutes", 20)),
            usedFallbackTransit = o.optBoolean("usedFallbackTransit", false),
            isLoop = o.optBoolean("isLoop", false),
            weatherMode = WeatherMode.fromId(o.optString("weatherMode", "clear")),
            modeBalance = modeBalance,
            districtsVisited = List(distArr.length()) { distArr.optString(it) }.filter { it.isNotBlank() },
            landmarks = lms,
            dayOfWeek = o.optInt("dayOfWeek", 3),
            timeMinutes = o.optInt("timeMinutes", 14 * 60),
            variantTitle = if (o.isNull("variantTitle")) null else o.optString("variantTitle").ifBlank { null },
            variantBadge = if (o.isNull("variantBadge")) null else o.optString("variantBadge").ifBlank { null },
            arrivalTimeMinutes = if (o.has("arrivalTimeMinutes") && !o.isNull("arrivalTimeMinutes")) o.optInt("arrivalTimeMinutes") else null,
            targetDeadlineMinutes = if (o.has("targetDeadlineMinutes") && !o.isNull("targetDeadlineMinutes")) o.optInt("targetDeadlineMinutes") else null,
            walkDistanceKm = if (o.has("walkDistanceKm") && !o.isNull("walkDistanceKm")) o.optDouble("walkDistanceKm") else null
        )
    }
}

object AndroidBackupStorage {
    private val DATA_URL_REGEX = Regex("""^data:image/(png|jpeg|jpg|webp|gif);base64,([A-Za-z0-9+/=\s]+)$""", RegexOption.IGNORE_CASE)
    private const val MAX_PHOTO_FILE_BYTES = 6L * 1024L * 1024L

    fun encodePhotoFileToDataUrl(filePath: String): String? {
        val file = File(filePath)
        if (!file.exists() || !file.isFile || file.length() > MAX_PHOTO_FILE_BYTES) return null
        return runCatching {
            val bytes = file.readBytes()
            val ext = file.extension.lowercase(Locale.US)
            val mime = when (ext) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                else -> "image/jpeg"
            }
            "data:$mime;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
        }.getOrNull()
    }

    fun saveDataUrlToInternalPhotoFile(context: Context, photoId: String, dataUrl: String): String? {
        val match = DATA_URL_REGEX.matchEntire(dataUrl.trim()) ?: return null
        val subtype = match.groupValues[1].lowercase(Locale.US)
        val b64Payload = match.groupValues[2]
        if (b64Payload.length > 10 * 1024 * 1024) return null

        return runCatching {
            val bytes = Base64.decode(b64Payload, Base64.DEFAULT)
            if (bytes.isEmpty() || bytes.size > 8 * 1024 * 1024) return null
            val dir = File(context.filesDir, "trip_photos").apply { if (!exists()) mkdirs() }
            val ext = when (subtype) {
                "png" -> "png"
                "webp" -> "webp"
                "gif" -> "gif"
                else -> "jpg"
            }
            val safeId = photoId.replace(Regex("""[^A-Za-z0-9_-]"""), "_").take(36).ifBlank { "photo" }
            val outFile = File(dir, "restored_${safeId}_${System.currentTimeMillis()}.$ext")
            outFile.writeBytes(bytes)
            outFile.absolutePath
        }.getOrNull()
    }

    suspend fun writeBackupToUri(context: Context, uri: Uri, jsonText: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(jsonText.toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: return@withContext false
                true
            }.getOrDefault(false)
        }

    suspend fun readBackupTextFromUri(context: Context, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(16 * 1024)
                    val out = ByteArrayOutputStream()
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        total += read
                        if (total > BackupCodec.MAX_BACKUP_BYTES) {
                            return@withContext null
                        }
                        out.write(buffer, 0, read)
                    }
                    out.toString(Charsets.UTF_8.name())
                }
            }.getOrNull()
        }
}
