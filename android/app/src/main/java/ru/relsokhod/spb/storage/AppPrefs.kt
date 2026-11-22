package ru.relsokhod.spb.storage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import ru.relsokhod.spb.model.AccentSeed
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
import ru.relsokhod.spb.model.TramRoute
import ru.relsokhod.spb.model.TramStop
import ru.relsokhod.spb.model.TripHistoryEntry
import ru.relsokhod.spb.model.TripPhotoMemory
import ru.relsokhod.spb.model.WeatherMode

class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("relsokhod_native_prefs", Context.MODE_PRIVATE)

    fun isOnboardingDone(): Boolean = prefs.getBoolean("onboarding_done", false)
    fun setOnboardingDone(done: Boolean) = prefs.edit().putBoolean("onboarding_done", done).apply()

    fun getCity(): CityId = CityId.fromId(prefs.getString("city", "spb"))
    fun setCity(city: CityId) = prefs.edit().putString("city", city.id).apply()

    fun getThemeMode(): ThemeMode = ThemeMode.fromId(prefs.getString("theme_mode", "light"))
    fun setThemeMode(mode: ThemeMode) = prefs.edit().putString("theme_mode", mode.id).apply()

    fun getAccentSeed(): AccentSeed = AccentSeed.fromId(prefs.getString("accent_seed", "neva"))
    fun setAccentSeed(seed: AccentSeed) = prefs.edit().putString("accent_seed", seed.id).apply()

    fun getPhotoReminderMinutes(): Int = prefs.getInt("photo_reminder_mins", 30)
    fun setPhotoReminderMinutes(mins: Int) = prefs.edit().putInt("photo_reminder_mins", mins).apply()

    fun getDismissedUpdateTag(): String? = prefs.getString("dismissed_update_tag", null)
    fun setDismissedUpdateTag(tag: String?) {
        prefs.edit().apply {
            if (tag == null) remove("dismissed_update_tag")
            else putString("dismissed_update_tag", tag)
        }.apply()
    }

    fun getLastSeenVersionCode(): Int = prefs.getInt("last_seen_version_code", 0)
    fun setLastSeenVersionCode(code: Int) = prefs.edit().putInt("last_seen_version_code", code).apply()

    fun getPendingUpdateNotes(): Pair<String, String>? {
        val tag = prefs.getString("pending_update_tag", null) ?: return null
        val notes = prefs.getString("pending_update_notes", null) ?: return null
        return tag to notes
    }

    fun setPendingUpdateNotes(tag: String, notes: String) {
        prefs.edit()
            .putString("pending_update_tag", tag)
            .putString("pending_update_notes", notes)
            .apply()
    }

    fun clearPendingUpdateNotes() {
        prefs.edit()
            .remove("pending_update_tag")
            .remove("pending_update_notes")
            .apply()
    }

    fun getSearchParams(city: CityId, defaultStartId: String): SearchParamsState {
        val raw = prefs.getString("search_params_${city.id}", null)
            ?: return SearchParamsState(startStopId = defaultStartId)
        return try {
            val o = JSONObject(raw)
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
            SearchParamsState(
                startStopId = o.optString("startStopId", defaultStartId).ifBlank { defaultStartId },
                destinationStopId = if (o.isNull("destinationStopId")) null else o.optString("destinationStopId").ifBlank { null },
                destinationAddressQuery = o.optString("destinationAddressQuery", ""),
                destinationAddressLabel = o.optString("destinationAddressLabel", ""),
                themeFilter = RouteThemeFilter.fromId(o.optString("themeFilter", "any")),
                durationPreset = DurationPreset.fromId(o.optString("durationPreset", "medium")),
                dayOfWeek = o.optInt("dayOfWeek", 3),
                timeMinutes = o.optInt("timeMinutes", 14 * 60),
                allowFallbackTransit = o.optBoolean("allowFallbackTransit", false),
                modeBalance = modeBalance,
                showAllNetwork = o.optBoolean("showAllNetwork", true),
                loopMode = o.optBoolean("loopMode", false),
                weatherMode = WeatherMode.fromId(o.optString("weatherMode", "clear")),
                preferSpecialFleet = o.optBoolean("preferSpecialFleet", false)
            )
        } catch (_: Exception) {
            SearchParamsState(startStopId = defaultStartId)
        }
    }

    fun saveSearchParams(city: CityId, state: SearchParamsState) {
        val o = JSONObject().apply {
            put("startStopId", state.startStopId)
            put("destinationStopId", state.destinationStopId ?: JSONObject.NULL)
            put("destinationAddressQuery", state.destinationAddressQuery)
            put("destinationAddressLabel", state.destinationAddressLabel)
            put("themeFilter", state.themeFilter.id)
            put("durationPreset", state.durationPreset.id)
            put("dayOfWeek", state.dayOfWeek)
            put("timeMinutes", state.timeMinutes)
            put("allowFallbackTransit", state.allowFallbackTransit)
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
        prefs.edit().putString("search_params_${city.id}", o.toString()).apply()
    }

    fun loadSavedRoutes(): List<SavedRoute> {
        val raw = prefs.getString("saved_routes", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { idx ->
                val o = arr.getJSONObject(idx)
                SavedRoute(
                    id = o.getString("id"),
                    customTitle = o.getString("customTitle"),
                    note = o.optString("note", ""),
                    savedAtIso = o.getString("savedAtIso"),
                    trip = tripFromJson(o.getJSONObject("trip"))
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun persistSavedRoutes(items: List<SavedRoute>) {
        val arr = JSONArray()
        for (item in items) {
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("customTitle", item.customTitle)
                    put("note", item.note)
                    put("savedAtIso", item.savedAtIso)
                    put("trip", tripToJson(item.trip))
                }
            )
        }
        prefs.edit().putString("saved_routes", arr.toString()).apply()
    }

    fun loadHistory(): List<TripHistoryEntry> {
        val raw = prefs.getString("trip_history", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { idx ->
                val o = arr.getJSONObject(idx)
                val photosArr = o.getJSONArray("photos")
                val photos = List(photosArr.length()) { pIdx ->
                    val po = photosArr.getJSONObject(pIdx)
                    TripPhotoMemory(
                        id = po.getString("id"),
                        filePath = po.getString("filePath"),
                        caption = po.optString("caption", ""),
                        takenAtIso = po.getString("takenAtIso")
                    )
                }
                TripHistoryEntry(
                    id = o.getString("id"),
                    completedAtIso = o.getString("completedAtIso"),
                    trip = tripFromJson(o.getJSONObject("trip")),
                    photos = photos
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun persistHistory(items: List<TripHistoryEntry>) {
        val arr = JSONArray()
        for (item in items) {
            val photosArr = JSONArray()
            for (p in item.photos) {
                photosArr.put(
                    JSONObject().apply {
                        put("id", p.id)
                        put("filePath", p.filePath)
                        put("caption", p.caption)
                        put("takenAtIso", p.takenAtIso)
                    }
                )
            }
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("completedAtIso", item.completedAtIso)
                    put("trip", tripToJson(item.trip))
                    put("photos", photosArr)
                }
            )
        }
        prefs.edit().putString("trip_history", arr.toString()).apply()
    }

    private fun stopToJson(s: TramStop): JSONObject = JSONObject().apply {
        put("id", s.id)
        put("name", s.name)
        put("district", s.district)
        put("lat", s.lat)
        put("lng", s.lng)
        put("hubId", s.hubId ?: JSONObject.NULL)
        put("isolatedFromMainTram", s.isolatedFromMainTram)
    }

    private fun stopFromJson(o: JSONObject): TramStop = TramStop(
        id = o.getString("id"),
        name = o.getString("name"),
        district = o.getString("district"),
        lat = o.getDouble("lat"),
        lng = o.getDouble("lng"),
        hubId = if (o.isNull("hubId")) null else o.optString("hubId").ifBlank { null },
        isolatedFromMainTram = o.optBoolean("isolatedFromMainTram", false)
    )

    private fun routeToJson(r: TramRoute): JSONObject = JSONObject().apply {
        put("id", r.id)
        put("number", r.number)
        put("title", r.title)
        put("colorHex", r.colorHex)
        put("type", r.type)
        put("themes", JSONArray(r.themes))
        put("stopIds", JSONArray(r.stopIds))
        put("minutesPerHop", r.minutesPerHop)
        put("description", r.description)
        put(
            "schedule",
            JSONObject().apply {
                put("daysOfWeek", JSONArray(r.schedule.daysOfWeek))
                put("startMinutes", r.schedule.startMinutes)
                put("endMinutes", r.schedule.endMinutes)
                put("intervalMinutes", r.schedule.intervalMinutes)
                put("label", r.schedule.label)
            }
        )
    }

    private fun routeFromJson(o: JSONObject): TramRoute {
        val sched = o.getJSONObject("schedule")
        val dArr = sched.getJSONArray("daysOfWeek")
        val tArr = o.getJSONArray("themes")
        val sArr = o.getJSONArray("stopIds")
        return TramRoute(
            id = o.getString("id"),
            number = o.getString("number"),
            title = o.getString("title"),
            colorHex = o.getString("colorHex"),
            type = o.getString("type"),
            themes = List(tArr.length()) { tArr.getString(it) },
            schedule = RouteSchedule(
                daysOfWeek = List(dArr.length()) { dArr.getInt(it) },
                startMinutes = sched.getInt("startMinutes"),
                endMinutes = sched.getInt("endMinutes"),
                intervalMinutes = sched.getInt("intervalMinutes"),
                label = sched.getString("label")
            ),
            stopIds = List(sArr.length()) { sArr.getString(it) },
            minutesPerHop = o.getInt("minutesPerHop"),
            description = o.getString("description")
        )
    }

    private fun tripToJson(t: GeneratedTrip): JSONObject = JSONObject().apply {
        put("id", t.id)
        put("city", t.city.id)
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

        val segArr = JSONArray()
        for (seg in t.segments) {
            segArr.put(
                JSONObject().apply {
                    put("id", seg.id)
                    put("kind", seg.kind.name)
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

    private fun tripFromJson(o: JSONObject): GeneratedTrip {
        val segArr = o.getJSONArray("segments")
        val segs = List(segArr.length()) { i ->
            val so = segArr.getJSONObject(i)
            val idsArr = so.getJSONArray("stopIds")
            RouteSegment(
                id = so.getString("id"),
                kind = SegmentKind.valueOf(so.getString("kind")),
                route = if (so.isNull("route")) null else routeFromJson(so.getJSONObject("route")),
                fallbackModeName = if (so.isNull("fallbackModeName")) null else so.optString("fallbackModeName"),
                fromStop = stopFromJson(so.getJSONObject("fromStop")),
                toStop = stopFromJson(so.getJSONObject("toStop")),
                stopIds = List(idsArr.length()) { idsArr.getString(it) },
                stopsCount = so.getInt("stopsCount"),
                durationMinutes = so.getInt("durationMinutes"),
                directionNote = so.getString("directionNote")
            )
        }

        val lmArr = o.getJSONArray("landmarks")
        val lms = List(lmArr.length()) { i ->
            val lo = lmArr.getJSONObject(i)
            val nArr = lo.getJSONArray("nearbyStopIds")
            Landmark(
                id = lo.getString("id"),
                name = lo.getString("name"),
                category = LandmarkCategory.fromId(lo.getString("category")),
                district = lo.getString("district"),
                description = lo.getString("description"),
                lat = lo.getDouble("lat"),
                lng = lo.getDouble("lng"),
                nearbyStopIds = List(nArr.length()) { nArr.getString(it) },
                hasPhoto = lo.optBoolean("hasPhoto", false)
            )
        }

        val linesArr = o.getJSONArray("tramLinesUsed")
        val distArr = o.getJSONArray("districtsVisited")
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
        return GeneratedTrip(
            id = o.getString("id"),
            city = CityId.fromId(o.getString("city")),
            createdAtIso = o.getString("createdAtIso"),
            startStop = stopFromJson(o.getJSONObject("startStop")),
            endStop = stopFromJson(o.getJSONObject("endStop")),
            segments = segs,
            tramLinesUsed = List(linesArr.length()) { linesArr.getString(it) },
            transferCount = o.getInt("transferCount"),
            totalDurationMinutes = o.getInt("totalDurationMinutes"),
            usedFallbackTransit = o.optBoolean("usedFallbackTransit", false),
            isLoop = o.optBoolean("isLoop", false),
            weatherMode = WeatherMode.fromId(o.optString("weatherMode", "clear")),
            modeBalance = modeBalance,
            districtsVisited = List(distArr.length()) { distArr.getString(it) },
            landmarks = lms,
            dayOfWeek = o.getInt("dayOfWeek"),
            timeMinutes = o.getInt("timeMinutes")
        )
    }
}
