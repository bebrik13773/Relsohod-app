package ru.relsokhod.spb

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AltRoute
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tram
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import ru.relsokhod.spb.data.NetworkRepository
import ru.relsokhod.spb.model.CityConfig
import ru.relsokhod.spb.model.CityDetectionResult
import ru.relsokhod.spb.model.CityId
import ru.relsokhod.spb.model.GenerationOutcome
import ru.relsokhod.spb.model.SavedRoute
import ru.relsokhod.spb.model.SearchParamsState
import ru.relsokhod.spb.model.TripHistoryEntry
import ru.relsokhod.spb.router.TramRouter
import ru.relsokhod.spb.storage.AppPrefs
import ru.relsokhod.spb.ui.components.SettingsSheet
import ru.relsokhod.spb.ui.screens.FavoritesTab
import ru.relsokhod.spb.ui.screens.LinesTab
import ru.relsokhod.spb.ui.screens.OnboardingScreen
import ru.relsokhod.spb.ui.screens.RouteTab
import ru.relsokhod.spb.ui.screens.SearchTab
import ru.relsokhod.spb.ui.theme.RelsokhodTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class BottomTab(val label: String) {
    SEARCH("Поиск"),
    ROUTE("Маршрут"),
    FAVORITES("Избранное"),
    LINES("Линии")
}

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        tts = TextToSpeech(this, this)

        val cities = NetworkRepository.loadFromAssets(applicationContext)
        val prefs = AppPrefs(applicationContext)

        setContent {
            RelsokhodNativeApp(
                cities = cities,
                prefs = prefs,
                activity = this,
                onSpeakText = { text -> speakRussian(text) }
            )
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val res = tts?.setLanguage(Locale("ru", "RU"))
            ttsReady = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    private fun speakRussian(text: String) {
        if (ttsReady && text.isNotBlank()) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "relsokhod_announcer")
        } else {
            Toast.makeText(this, "Голосовой синтез RU не настроен на устройстве", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RelsokhodNativeApp(
    cities: Map<CityId, CityConfig>,
    prefs: AppPrefs,
    activity: ComponentActivity,
    onSpeakText: (String) -> Unit
) {
    var onboardingDone by remember { mutableStateOf(prefs.isOnboardingDone()) }
    var selectedCity by remember { mutableStateOf(prefs.getCity()) }
    var themeMode by remember { mutableStateOf(prefs.getThemeMode()) }
    var accentSeed by remember { mutableStateOf(prefs.getAccentSeed()) }
    var photoReminderMinutes by remember { mutableStateOf(prefs.getPhotoReminderMinutes()) }

    val spbDefault = cities[CityId.SPB]?.defaultStartStopId ?: "spb_sen"
    val omskDefault = cities[CityId.OMSK]?.defaultStartStopId ?: "omsk_cirk"

    var spbParams by remember { mutableStateOf(prefs.getSearchParams(CityId.SPB, spbDefault)) }
    var omskParams by remember { mutableStateOf(prefs.getSearchParams(CityId.OMSK, omskDefault)) }

    val spbScroll = rememberScrollState()
    val omskScroll = rememberScrollState()

    var activeTab by remember { mutableStateOf(BottomTab.SEARCH) }
    var mapPickMode by remember { mutableStateOf<String?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }

    var savedRoutes by remember { mutableStateOf(prefs.loadSavedRoutes()) }
    var history by remember { mutableStateOf(prefs.loadHistory()) }

    var geoStatusMessage by remember { mutableStateOf<String?>(null) }
    var cityDetection by remember { mutableStateOf<CityDetectionResult?>(null) }
    var mismatchDismissed by remember { mutableStateOf(false) }

    val currentCityConfig = cities[selectedCity] ?: cities.values.first()
    val currentParams = if (selectedCity == CityId.SPB) spbParams else omskParams
    val currentScroll = if (selectedCity == CityId.SPB) spbScroll else omskScroll

    fun updateCurrentParams(next: SearchParamsState) {
        if (selectedCity == CityId.SPB) {
            spbParams = next
            prefs.saveSearchParams(CityId.SPB, next)
        } else {
            omskParams = next
            prefs.saveSearchParams(CityId.OMSK, next)
        }
    }

    fun generateTripFor(
        cityCfg: CityConfig,
        p: SearchParamsState,
        seed: Long? = null
    ): GenerationOutcome {
        return TramRouter.generateTramAdventure(
            city = cityCfg.id,
            stops = cityCfg.stops,
            routes = cityCfg.routes,
            landmarks = cityCfg.landmarks,
            startStopId = p.startStopId,
            destinationStopId = p.destinationStopId,
            theme = p.themeFilter,
            durationPreset = p.durationPreset,
            dayOfWeek = p.dayOfWeek,
            timeMinutes = p.timeMinutes,
            allowFallbackTransit = p.allowFallbackTransit,
            loopMode = p.loopMode,
            weatherMode = p.weatherMode,
            preferSpecialFleet = p.preferSpecialFleet,
            seed = seed
        )
    }

    var outcome by remember {
        mutableStateOf<GenerationOutcome?>(
            generateTripFor(currentCityConfig, currentParams, seed = 42L)
        )
    }

    var pendingApplyNearestStart by remember { mutableStateOf(false) }

    fun handleLocationSuccess(lat: Double, lng: Double, applyNearestStart: Boolean) {
        val detection = NetworkRepository.detectCityFromCoords(lat, lng, cities)
        cityDetection = detection
        mismatchDismissed = false

        val cfg = cities[selectedCity] ?: currentCityConfig
        if (detection.detected != null && detection.detected != selectedCity) {
            val detectedName = cities[detection.detected]?.name.orEmpty()
            geoStatusMessage = "По координатам вы в городе $detectedName, а выбран ${cfg.name}."
            return
        }
        if (detection.detected == null) {
            geoStatusMessage = "Вы находитесь за пределами СПб и Омска — показана сеть выбранного города (${cfg.name})."
            return
        }

        if (applyNearestStart) {
            val nearest = TramRouter.findNearestStops(lat, lng, cfg.stops, limit = 1, includeIsolated = false).firstOrNull()
            if (nearest != null) {
                val distMeters = (nearest.second * 1000).toInt()
                updateCurrentParams(currentParams.copy(startStopId = nearest.first.id))
                geoStatusMessage = "Ближайшая остановка: «${nearest.first.name}» (~$distMeters м)"
            }
        } else {
            geoStatusMessage = "Местоположение подтверждено (${cfg.shortName})."
        }
    }

    fun requestSingleLocation(applyNearestStart: Boolean) {
        geoStatusMessage = "Определяем координаты..."
        fetchCurrentLocation(
            context = activity,
            onSuccess = { loc -> handleLocationSuccess(loc.latitude, loc.longitude, applyNearestStart) },
            onFailure = { reason -> geoStatusMessage = reason }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
            (grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
        if (granted) {
            requestSingleLocation(pendingApplyNearestStart)
        } else {
            geoStatusMessage = "Доступ к геолокации отклонён — выберите остановку вручную."
        }
    }

    fun triggerLocationFlow(applyNearestStart: Boolean) {
        pendingApplyNearestStart = applyNearestStart
        if (hasLocationPermission(activity)) {
            requestSingleLocation(applyNearestStart)
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Silent mismatch check on entry when permission already granted
    LaunchedEffect(Unit) {
        if (hasLocationPermission(activity)) {
            fetchCurrentLocation(
                context = activity,
                onSuccess = { loc -> handleLocationSuccess(loc.latitude, loc.longitude, applyNearestStart = false) },
                onFailure = {}
            )
        }
    }

    fun switchCity(newCity: CityId) {
        selectedCity = newCity
        prefs.setCity(newCity)
        mismatchDismissed = true
        val newCfg = cities[newCity] ?: return
        val newParams = if (newCity == CityId.SPB) spbParams else omskParams
        outcome = generateTripFor(newCfg, newParams, seed = System.currentTimeMillis())
    }

    RelsokhodTheme(themeMode = themeMode, accentSeed = accentSeed) {
        if (!onboardingDone) {
            OnboardingScreen(
                selectedCity = selectedCity,
                cities = cities,
                geoStatusMessage = geoStatusMessage,
                cityDetection = cityDetection,
                onSelectCity = { switchCity(it) },
                onRequestLocation = { triggerLocationFlow(applyNearestStart = true) },
                onComplete = {
                    onboardingDone = true
                    prefs.setOnboardingDone(true)
                }
            )
            return@RelsokhodTheme
        }

        val activeTrip = (outcome as? GenerationOutcome.Ok)?.trip
        val isTripSaved = activeTrip != null && savedRoutes.any { it.trip.id == activeTrip.id }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Tram,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "Рельсоход",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = currentCityConfig.shortName,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { settingsOpen = true }) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Настройки")
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = activeTab == BottomTab.SEARCH,
                        onClick = { activeTab = BottomTab.SEARCH },
                        icon = { Icon(Icons.Rounded.Explore, contentDescription = null) },
                        label = { Text("Поиск") }
                    )
                    NavigationBarItem(
                        selected = activeTab == BottomTab.ROUTE,
                        onClick = { activeTab = BottomTab.ROUTE },
                        icon = { Icon(Icons.Rounded.AltRoute, contentDescription = null) },
                        label = { Text("Маршрут") }
                    )
                    NavigationBarItem(
                        selected = activeTab == BottomTab.FAVORITES,
                        onClick = { activeTab = BottomTab.FAVORITES },
                        icon = { Icon(Icons.Rounded.Bookmark, contentDescription = null) },
                        label = { Text("Избранное") }
                    )
                    NavigationBarItem(
                        selected = activeTab == BottomTab.LINES,
                        onClick = { activeTab = BottomTab.LINES },
                        icon = { Icon(Icons.Rounded.FormatListBulleted, contentDescription = null) },
                        label = { Text("Линии ${currentCityConfig.shortName}") }
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (!mismatchDismissed && cityDetection != null && cityDetection?.detected != selectedCity) {
                    CityMismatchBanner(
                        selectedCity = currentCityConfig,
                        detection = cityDetection!!,
                        cities = cities,
                        onSwitchCity = { switchCity(it) },
                        onDismiss = { mismatchDismissed = true }
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (activeTab) {
                        BottomTab.SEARCH -> {
                            SearchTab(
                                cityConfig = currentCityConfig,
                                params = currentParams,
                                scrollState = currentScroll,
                                geoStatusMessage = geoStatusMessage,
                                onUpdateParams = { updateCurrentParams(it) },
                                onDetectLocation = { triggerLocationFlow(applyNearestStart = true) },
                                onPickOnMap = { mode ->
                                    mapPickMode = mode
                                    activeTab = BottomTab.ROUTE
                                },
                                onGenerate = {
                                    outcome = generateTripFor(currentCityConfig, currentParams)
                                    activeTab = BottomTab.ROUTE
                                }
                            )
                        }

                        BottomTab.ROUTE -> {
                            RouteTab(
                                cityConfig = currentCityConfig,
                                params = currentParams,
                                outcome = outcome,
                                mapPickMode = mapPickMode,
                                isSaved = isTripSaved,
                                photoReminderMinutes = photoReminderMinutes,
                                onPickStopOnMap = { stop ->
                                    if (mapPickMode == "start") {
                                        updateCurrentParams(currentParams.copy(startStopId = stop.id))
                                    } else if (mapPickMode == "destination") {
                                        updateCurrentParams(
                                            currentParams.copy(
                                                destinationStopId = stop.id,
                                                destinationAddressLabel = stop.name,
                                                loopMode = false
                                            )
                                        )
                                    }
                                    mapPickMode = null
                                    activeTab = BottomTab.SEARCH
                                },
                                onExtendLastSegment = {
                                    val trip = activeTrip ?: return@RouteTab
                                    val extended = TramRouter.extendTripLastSegment(
                                        trip = trip,
                                        stops = currentCityConfig.stops,
                                        routes = currentCityConfig.routes,
                                        landmarks = currentCityConfig.landmarks,
                                        allowFallbackTransit = currentParams.allowFallbackTransit,
                                        weatherMode = currentParams.weatherMode,
                                        preferSpecialFleet = currentParams.preferSpecialFleet
                                    )
                                    if (extended != null) {
                                        outcome = GenerationOutcome.Ok(extended)
                                    } else {
                                        Toast.makeText(activity, "Дальше от этой конечной линии не продолжаются", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onRegenerate = {
                                    outcome = generateTripFor(currentCityConfig, currentParams)
                                },
                                onEnableFallbackAndRetry = {
                                    val next = currentParams.copy(allowFallbackTransit = true)
                                    updateCurrentParams(next)
                                    outcome = generateTripFor(currentCityConfig, next)
                                },
                                onSaveTrip = { trip ->
                                    if (savedRoutes.none { it.trip.id == trip.id }) {
                                        val isoNow = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
                                        val nextSaved = listOf(
                                            SavedRoute(
                                                id = "saved-${System.currentTimeMillis()}",
                                                customTitle = "${trip.startStop.name.substringBefore("(").trim()} → ${trip.endStop.name.substringBefore("(").trim()}",
                                                note = "",
                                                savedAtIso = isoNow,
                                                trip = trip
                                            )
                                        ) + savedRoutes
                                        savedRoutes = nextSaved
                                        prefs.persistSavedRoutes(nextSaved)
                                        Toast.makeText(activity, "Маршрут сохранён в Избранное", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onCompleteTrip = { trip, photos ->
                                    val isoNow = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
                                    val entry = TripHistoryEntry(
                                        id = "hist-${System.currentTimeMillis()}",
                                        completedAtIso = isoNow,
                                        trip = trip,
                                        photos = photos
                                    )
                                    val nextHistory = listOf(entry) + history
                                    history = nextHistory
                                    prefs.persistHistory(nextHistory)
                                    activeTab = BottomTab.FAVORITES
                                    Toast.makeText(activity, "Поездка добавлена в Паспорт!", Toast.LENGTH_SHORT).show()
                                },
                                onSpeakText = onSpeakText
                            )
                        }

                        BottomTab.FAVORITES -> {
                            FavoritesTab(
                                savedRoutes = savedRoutes,
                                history = history,
                                onOpenTrip = { trip ->
                                    if (trip.city != selectedCity) {
                                        selectedCity = trip.city
                                        prefs.setCity(trip.city)
                                    }
                                    outcome = GenerationOutcome.Ok(trip)
                                    activeTab = BottomTab.ROUTE
                                },
                                onDeleteSaved = { id ->
                                    val next = savedRoutes.filterNot { it.id == id }
                                    savedRoutes = next
                                    prefs.persistSavedRoutes(next)
                                },
                                onAddPhotoToHistory = { entryId, photo ->
                                    val next = history.map { e ->
                                        if (e.id == entryId) e.copy(photos = listOf(photo) + e.photos) else e
                                    }
                                    history = next
                                    prefs.persistHistory(next)
                                },
                                onDeleteHistory = { id ->
                                    val next = history.filterNot { it.id == id }
                                    history = next
                                    prefs.persistHistory(next)
                                }
                            )
                        }

                        BottomTab.LINES -> {
                            LinesTab(cityConfig = currentCityConfig)
                        }
                    }
                }
            }
        }

        if (settingsOpen) {
            SettingsSheet(
                city = selectedCity,
                cities = cities,
                themeMode = themeMode,
                accentSeed = accentSeed,
                photoReminderMinutes = photoReminderMinutes,
                onChangeCity = { switchCity(it) },
                onChangeThemeMode = {
                    themeMode = it
                    prefs.setThemeMode(it)
                },
                onChangeAccentSeed = {
                    accentSeed = it
                    prefs.setAccentSeed(it)
                },
                onChangePhotoReminderMinutes = {
                    photoReminderMinutes = it
                    prefs.setPhotoReminderMinutes(it)
                },
                onRestartOnboarding = {
                    onboardingDone = false
                    prefs.setOnboardingDone(false)
                },
                onDismiss = { settingsOpen = false }
            )
        }
    }
}

@Composable
private fun CityMismatchBanner(
    selectedCity: CityConfig,
    detection: CityDetectionResult,
    cities: Map<CityId, CityConfig>,
    onSwitchCity: (CityId) -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF59E0B).copy(alpha = 0.16f),
        border = BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = Color(0xFFD97706))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (detection.detected == null) {
                        "Вы за пределами СПб и Омска — активна сеть ${selectedCity.name}."
                    } else {
                        "Вы сейчас в ${cities[detection.detected]?.genitiveName ?: ""}, а выбран ${selectedCity.name}."
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (detection.detected != null) {
                    Spacer(modifier = Modifier.size(6.dp))
                    Button(onClick = { onSwitchCity(detection.detected) }) {
                        Text("Переключиться на ${cities[detection.detected]?.shortName}", fontSize = 11.sp)
                    }
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = "Закрыть")
            }
        }
    }
}

private fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

@SuppressLint("MissingPermission")
private fun fetchCurrentLocation(
    context: Context,
    onSuccess: (Location) -> Unit,
    onFailure: (String) -> Unit
) {
    if (!hasLocationPermission(context)) {
        onFailure("Нет разрешения на геолокацию.")
        return
    }
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    if (lm == null) {
        onFailure("Сервис геолокации недоступен.")
        return
    }

    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
    val bestCached = providers.mapNotNull { p ->
        runCatching { lm.getLastKnownLocation(p) }.getOrNull()
    }.maxByOrNull { it.time }

    val now = System.currentTimeMillis()
    if (bestCached != null && now - bestCached.time < 5 * 60 * 1000L) {
        onSuccess(bestCached)
        return
    }

    val activeProvider = when {
        runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) ->
            LocationManager.NETWORK_PROVIDER
        runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) ->
            LocationManager.GPS_PROVIDER
        else -> null
    }

    if (activeProvider == null) {
        if (bestCached != null) onSuccess(bestCached)
        else onFailure("Включите геолокацию в настройках телефона.")
        return
    }

    val handler = Handler(Looper.getMainLooper())
    var completed = false
    lateinit var listener: LocationListener
    val timeoutRunnable = Runnable {
        if (!completed) {
            completed = true
            runCatching { lm.removeUpdates(listener) }
            if (bestCached != null) onSuccess(bestCached)
            else onFailure("Не удалось получить GPS-координаты за 8 сек.")
        }
    }

    listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!completed) {
                completed = true
                handler.removeCallbacks(timeoutRunnable)
                runCatching { lm.removeUpdates(this) }
                onSuccess(location)
            }
        }

        override fun onProviderDisabled(provider: String) {}
        override fun onProviderEnabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    runCatching {
        lm.requestLocationUpdates(activeProvider, 0L, 0f, listener, Looper.getMainLooper())
        handler.postDelayed(timeoutRunnable, 8000L)
    }.onFailure {
        if (bestCached != null) onSuccess(bestCached)
        else onFailure("Ошибка доступа к геолокации.")
    }
}
