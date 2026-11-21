package ru.relsokhod.spb.model

enum class CityId(val id: String) {
    SPB("spb"),
    OMSK("omsk");

    companion object {
        fun fromId(raw: String?): CityId = entries.firstOrNull { it.id == raw } ?: SPB
    }
}

enum class ThemeMode(val id: String, val label: String) {
    LIGHT("light", "Светлая"),
    DARK("dark", "Тёмная"),
    TRAM("tram", "Трамвай");

    companion object {
        fun fromId(raw: String?): ThemeMode = entries.firstOrNull { it.id == raw } ?: LIGHT
    }
}

enum class AccentSeed(val id: String, val label: String, val primaryHex: Long, val containerHex: Long) {
    SYSTEM("system", "Системный Material You", 0xFF005A8D, 0xFFD2E4FF),
    NEVA("neva", "Нева (по умолчанию)", 0xFF005A8D, 0xFFD2E4FF),
    SAGE("sage", "Шалфей", 0xFF2D6A4F, 0xFFB7E4C7),
    TERRACOTTA("terracotta", "Терракота", 0xFF9C4221, 0xFFFFDBCF),
    INDIGO("indigo", "Индиго", 0xFF3F51B5, 0xFFDEE0FF),
    AMBER("amber", "Янтарь", 0xFF8C5D00, 0xFFFFDEA6);

    companion object {
        fun fromId(raw: String?): AccentSeed = entries.firstOrNull { it.id == raw } ?: NEVA
    }
}

enum class WeatherMode(val id: String, val label: String) {
    CLEAR("clear", "Ясно"),
    RAIN("rain", "Дождь"),
    FROST("frost", "Мороз");

    companion object {
        fun fromId(raw: String?): WeatherMode = entries.firstOrNull { it.id == raw } ?: CLEAR
    }
}

enum class DurationPreset(val id: String, val label: String) {
    ANY("any", "Любая"),
    SHORT("short", "~30 мин"),
    MEDIUM("medium", "~1 ч"),
    LONG("long", "~2+ ч");

    companion object {
        fun fromId(raw: String?): DurationPreset = entries.firstOrNull { it.id == raw } ?: MEDIUM
    }
}

enum class RouteThemeFilter(val id: String, val label: String) {
    ANY("any", "Полный рандом"),
    HISTORIC("historic", "Центр и история"),
    PARKS("parks", "Парки и набережные"),
    CHIZHIK("chizhik", "Окраины и Чижики"),
    MARITIME("maritime", "Вокзалы и простор");

    companion object {
        fun fromId(raw: String?): RouteThemeFilter = entries.firstOrNull { it.id == raw } ?: ANY
    }
}

enum class LandmarkCategory(val id: String, val label: String) {
    ARCHITECTURE("architecture", "Архитектура"),
    PARK("park", "Парки"),
    QUIRKY("quirky", "Необычное"),
    CAFE("cafe", "Кофейни"),
    GROCERY("grocery", "Пятёрочка / Перекрёсток");

    companion object {
        fun fromId(raw: String?): LandmarkCategory =
            entries.firstOrNull { it.id == raw } ?: ARCHITECTURE
    }
}

data class TramStop(
    val id: String,
    val name: String,
    val district: String,
    val lat: Double,
    val lng: Double,
    val hubId: String? = null,
    val isolatedFromMainTram: Boolean = false
)

data class RouteSchedule(
    val daysOfWeek: List<Int>,
    val startMinutes: Int,
    val endMinutes: Int,
    val intervalMinutes: Int,
    val label: String
)

data class TramRoute(
    val id: String,
    val number: String,
    val title: String,
    val colorHex: String,
    val type: String, // "regular" | "circular" | "chizhik" | "retro"
    val themes: List<String>,
    val schedule: RouteSchedule,
    val stopIds: List<String>,
    val minutesPerHop: Int,
    val description: String
)

data class Landmark(
    val id: String,
    val name: String,
    val category: LandmarkCategory,
    val district: String,
    val description: String,
    val lat: Double,
    val lng: Double,
    val nearbyStopIds: List<String>,
    val hasPhoto: Boolean
)

data class CityConfig(
    val id: CityId,
    val name: String,
    val shortName: String,
    val genitiveName: String,
    val tagline: String,
    val centerLat: Double,
    val centerLng: Double,
    val defaultStartStopId: String,
    val radiusKm: Double,
    val stops: List<TramStop>,
    val routes: List<TramRoute>,
    val landmarks: List<Landmark>,
    val announcerIntro: String
)

data class CityDetectionResult(
    val detected: CityId?, // null means "other"
    val nearestSupportedCity: CityId,
    val distanceToSpbKm: Double,
    val distanceToOmskKm: Double
)

enum class SegmentKind { TRAM, WALK, FALLBACK_TRANSIT }

data class RouteSegment(
    val id: String,
    val kind: SegmentKind,
    val route: TramRoute?,
    val fallbackModeName: String?,
    val fromStop: TramStop,
    val toStop: TramStop,
    val stopIds: List<String>,
    val stopsCount: Int,
    val durationMinutes: Int,
    val directionNote: String
)

data class ModeBalance(
    val tram: Float = 0.72f,
    val transit: Float = 0.08f,
    val walk: Float = 0.20f
) {
    fun normalized(): ModeBalance {
        val t = maxOf(0f, tram)
        val tr = maxOf(0f, transit)
        val w = maxOf(0f, walk)
        val sum = t + tr + w
        if (sum <= 0.0001f) return ModeBalance()
        return ModeBalance(tram = t / sum, transit = tr / sum, walk = w / sum)
    }
}

data class GeneratedTrip(
    val id: String,
    val city: CityId,
    val createdAtIso: String,
    val startStop: TramStop,
    val endStop: TramStop,
    val segments: List<RouteSegment>,
    val tramLinesUsed: List<String>,
    val transferCount: Int,
    val totalDurationMinutes: Int,
    val usedFallbackTransit: Boolean,
    val isLoop: Boolean,
    val weatherMode: WeatherMode,
    val modeBalance: ModeBalance = ModeBalance(),
    val districtsVisited: List<String>,
    val landmarks: List<Landmark>,
    val dayOfWeek: Int,
    val timeMinutes: Int
)

sealed class GenerationOutcome {
    data class Ok(val trip: GeneratedTrip) : GenerationOutcome()
    data class NeedsFallback(val reason: String, val nearestTramStop: TramStop?) : GenerationOutcome()
    data class NoService(val reason: String) : GenerationOutcome()
}

data class SavedRoute(
    val id: String,
    val customTitle: String,
    val note: String,
    val savedAtIso: String,
    val trip: GeneratedTrip
)

data class TripPhotoMemory(
    val id: String,
    val filePath: String,
    val caption: String,
    val takenAtIso: String
)

data class TripHistoryEntry(
    val id: String,
    val completedAtIso: String,
    val trip: GeneratedTrip,
    val photos: List<TripPhotoMemory>
)

data class PassportBadge(
    val id: String,
    val icon: String,
    val title: String,
    val description: String,
    val unlocked: Boolean,
    val progressText: String
)

data class SearchParamsState(
    val startStopId: String,
    val destinationStopId: String? = null,
    val destinationAddressQuery: String = "",
    val destinationAddressLabel: String = "",
    val themeFilter: RouteThemeFilter = RouteThemeFilter.ANY,
    val durationPreset: DurationPreset = DurationPreset.MEDIUM,
    val dayOfWeek: Int = 3,
    val timeMinutes: Int = 14 * 60,
    val allowFallbackTransit: Boolean = false,
    val modeBalance: ModeBalance = ModeBalance(),
    val showAllNetwork: Boolean = true,
    val loopMode: Boolean = false,
    val weatherMode: WeatherMode = WeatherMode.CLEAR,
    val preferSpecialFleet: Boolean = false
)
