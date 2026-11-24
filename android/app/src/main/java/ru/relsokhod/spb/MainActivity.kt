package ru.relsokhod.spb

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tram
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.relsokhod.spb.data.NetworkRepository
import ru.relsokhod.spb.model.AccentSeed
import ru.relsokhod.spb.model.CityConfig
import ru.relsokhod.spb.model.CityDetectionResult
import ru.relsokhod.spb.model.CityId
import ru.relsokhod.spb.model.GenerationOutcome
import ru.relsokhod.spb.model.SavedRoute
import ru.relsokhod.spb.model.SearchParamsState
import ru.relsokhod.spb.model.ThemeMode
import ru.relsokhod.spb.model.TripHistoryEntry
import ru.relsokhod.spb.router.TramRouter
import ru.relsokhod.spb.storage.AndroidBackupStorage
import ru.relsokhod.spb.storage.AppPrefs
import ru.relsokhod.spb.storage.BackupCodec
import ru.relsokhod.spb.storage.BackupDecodeResult
import ru.relsokhod.spb.ui.components.AppUpdateBanner
import ru.relsokhod.spb.ui.components.SettingsSheet
import ru.relsokhod.spb.ui.components.StartupSplash
import ru.relsokhod.spb.ui.components.WhatsNewDialog
import ru.relsokhod.spb.ui.screens.FavoritesTab
import ru.relsokhod.spb.ui.screens.LinesTab
import ru.relsokhod.spb.ui.screens.OnboardingScreen
import ru.relsokhod.spb.ui.screens.RouteTab
import ru.relsokhod.spb.ui.screens.SearchTab
import ru.relsokhod.spb.ui.theme.RelsokhodTheme
import ru.relsokhod.spb.update.AppUpdateLogic
import ru.relsokhod.spb.update.AppUpdateManager
import ru.relsokhod.spb.update.AvailableRelease
import ru.relsokhod.spb.update.SignatureCompatibility
import ru.relsokhod.spb.update.UpdateCheckResult
import ru.relsokhod.spb.update.UpdateDownloadState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class BottomTab(val label: String) {
    SEARCH("Поиск"),
    ROUTE("Маршрут"),
    FAVORITES("Избранное"),
    LINES("Линии")
}

private data class BootstrapData(
    val cities: Map<CityId, CityConfig>,
    val onboardingDone: Boolean,
    val selectedCity: CityId,
    val themeMode: ThemeMode,
    val accentSeed: AccentSeed,
    val photoReminderMinutes: Int,
    val spbParams: SearchParamsState,
    val omskParams: SearchParamsState,
    val savedRoutes: List<SavedRoute>,
    val history: List<TripHistoryEntry>,
    val dismissedUpdateTag: String?,
    val lastSeenVersionCode: Int,
    val pendingUpdateNotes: Pair<String, String>?,
    val bundledChangelog: String
)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsInitializing = false
    private var ttsFailed = false
    private var pendingSpeakText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RelsokhodNativeApp(
                appContext = applicationContext,
                activity = this,
                onSpeakText = { text -> speakRussian(text) }
            )
        }
    }

    override fun onInit(status: Int) {
        ttsInitializing = false
        if (status == TextToSpeech.SUCCESS) {
            val res = tts?.setLanguage(Locale.forLanguageTag("ru-RU"))
            ttsReady = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
        } else {
            ttsReady = false
        }
        ttsFailed = !ttsReady

        val pending = pendingSpeakText
        pendingSpeakText = null
        if (ttsReady && !pending.isNullOrBlank()) {
            tts?.speak(pending, TextToSpeech.QUEUE_FLUSH, null, "relsokhod_announcer")
        } else if (!ttsReady && pending != null) {
            runOnUiThread {
                Toast.makeText(this, "Голосовой синтез RU не настроен на устройстве", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun speakRussian(text: String) {
        if (text.isBlank()) return
        if (ttsReady && tts != null) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "relsokhod_announcer")
            return
        }
        if (tts == null && !ttsInitializing && !ttsFailed) {
            pendingSpeakText = text
            ttsInitializing = true
            tts = TextToSpeech(this, this)
            return
        }
        if (ttsInitializing) {
            pendingSpeakText = text
            return
        }
        Toast.makeText(this, "Голосовой синтез RU не настроен на устройстве", Toast.LENGTH_SHORT).show()
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
    appContext: Context,
    activity: ComponentActivity,
    onSpeakText: (String) -> Unit
) {
    val prefs = remember(appContext) { AppPrefs(appContext) }
    val coroutineScope = rememberCoroutineScope()

    var cities by remember { mutableStateOf<Map<CityId, CityConfig>>(emptyMap()) }
    var isBootstrapping by remember { mutableStateOf(true) }
    var showAnimatedSplash by remember { mutableStateOf(true) }
    var isGenerating by remember { mutableStateOf(false) }

    var onboardingDone by remember { mutableStateOf(false) }
    var selectedCity by remember { mutableStateOf(CityId.SPB) }
    var themeMode by remember { mutableStateOf(ThemeMode.LIGHT) }
    var accentSeed by remember { mutableStateOf(AccentSeed.NEVA) }
    var photoReminderMinutes by remember { mutableStateOf(30) }

    var spbParams by remember { mutableStateOf(SearchParamsState(startStopId = "spb_sen")) }
    var omskParams by remember { mutableStateOf(SearchParamsState(startStopId = "omsk_cirk")) }
    var spbParamsDirty by remember { mutableStateOf(false) }
    var omskParamsDirty by remember { mutableStateOf(false) }

    val spbScroll = rememberScrollState()
    val omskScroll = rememberScrollState()

    var activeTab by remember { mutableStateOf(BottomTab.SEARCH) }
    var mapPickMode by remember { mutableStateOf<String?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }

    var savedRoutes by remember { mutableStateOf<List<SavedRoute>>(emptyList()) }
    var history by remember { mutableStateOf<List<TripHistoryEntry>>(emptyList()) }

    var geoStatusMessage by remember { mutableStateOf<String?>(null) }
    var cityDetection by remember { mutableStateOf<CityDetectionResult?>(null) }
    var mismatchDismissed by remember { mutableStateOf(false) }

    var outcome by remember { mutableStateOf<GenerationOutcome?>(null) }
    var generationJob by remember { mutableStateOf<Job?>(null) }

    var dismissedUpdateTag by remember { mutableStateOf<String?>(null) }
    var isCheckingUpdates by remember { mutableStateOf(false) }
    var updateCheckResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var updateDownloadState by remember { mutableStateOf<UpdateDownloadState>(UpdateDownloadState.Idle) }
    var updateDownloadJob by remember { mutableStateOf<Job?>(null) }
    var pendingInstallApk by remember { mutableStateOf<File?>(null) }
    var lastSeenVersionCode by remember { mutableStateOf(BuildConfig.VERSION_CODE) }
    var bundledChangelog by remember { mutableStateOf("") }
    var whatsNewNotes by remember { mutableStateOf("") }
    var whatsNewReleaseTag by remember { mutableStateOf<String?>(null) }
    var whatsNewIsPostUpdate by remember { mutableStateOf(false) }
    var showWhatsNewDialog by remember { mutableStateOf(false) }
    var backupFeedback by remember { mutableStateOf<String?>(null) }
    var backupFeedbackIsError by remember { mutableStateOf(false) }
    var backupRestoredSuccess by remember { mutableStateOf(false) }
    var backupSavedForUpdate by remember { mutableStateOf(false) }
    var reinstallBackupPrompt by remember { mutableStateOf<Pair<File, AvailableRelease>?>(null) }
    var pendingUninstallAfterBackup by remember { mutableStateOf<Pair<File, AvailableRelease>?>(null) }

    fun openReleaseInBrowser(rawUrl: String) {
        if (!AppUpdateLogic.isAllowedGitHubUrl(rawUrl)) return
        runCatching {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(rawUrl)))
        }.onFailure {
            Toast.makeText(activity, "Не найден браузер для открытия GitHub", Toast.LENGTH_SHORT).show()
        }
    }

    fun exportAndUninstall(file: File, release: AvailableRelease) {
        coroutineScope.launch {
            val saved = AppUpdateManager.exportApkToDownloads(
                context = appContext,
                apkFile = file,
                displayName = release.apkAsset.name
            )
            if (saved) {
                Toast.makeText(
                    activity,
                    "APK сохранён в Загрузки (${release.apkAsset.name}). Удалите старую версию и откройте файл.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    activity,
                    "Откройте страницу релиза в браузере, чтобы скачать APK после удаления старой версии.",
                    Toast.LENGTH_LONG
                ).show()
                openReleaseInBrowser(release.htmlUrl)
            }
            runCatching {
                activity.startActivity(AppUpdateManager.buildUninstallCurrentAppIntent(activity))
            }.onFailure {
                Toast.makeText(
                    activity,
                    "Удалите старую версию вручную и установите APK из папки «Загрузки».",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun requestExportAndUninstall(file: File, release: AvailableRelease) {
        if (!backupSavedForUpdate) {
            reinstallBackupPrompt = file to release
        } else {
            exportAndUninstall(file, release)
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri == null) {
            pendingUninstallAfterBackup = null
            return@rememberLauncherForActivityResult
        }
        coroutineScope.launch {
            val jsonText = withContext(Dispatchers.IO) {
                BackupCodec.encodeBackupJson(
                    city = selectedCity,
                    themeMode = themeMode,
                    accentSeed = accentSeed,
                    photoReminderMinutes = photoReminderMinutes,
                    onboardingDone = onboardingDone,
                    spbParams = spbParams,
                    omskParams = omskParams,
                    savedRoutes = savedRoutes,
                    history = history,
                    photoToDataUrl = { photo ->
                        AndroidBackupStorage.encodePhotoFileToDataUrl(photo.filePath)
                    }
                )
            }
            val saved = AndroidBackupStorage.writeBackupToUri(appContext, uri, jsonText)
            if (saved) {
                backupSavedForUpdate = true
                backupFeedbackIsError = false
                backupFeedback = "Резервная копия сохранена (${BackupCodec.buildBackupFilename()})."
                Toast.makeText(activity, "Файл .relsohod сохранён", Toast.LENGTH_SHORT).show()
                val pending = pendingUninstallAfterBackup
                pendingUninstallAfterBackup = null
                if (pending != null) {
                    exportAndUninstall(pending.first, pending.second)
                }
            } else {
                pendingUninstallAfterBackup = null
                backupFeedbackIsError = true
                backupFeedback = "Не удалось записать файл резервной копии."
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val rawText = AndroidBackupStorage.readBackupTextFromUri(appContext, uri)
            if (rawText == null) {
                backupFeedbackIsError = true
                backupRestoredSuccess = false
                backupFeedback = "Не удалось прочитать файл или превышен размер 35 МБ."
                return@launch
            }
            val decodeResult = withContext(Dispatchers.IO) {
                BackupCodec.decodeBackupJson(
                    rawText = rawText,
                    cities = cities,
                    dataUrlToFilePath = { photoId, dataUrl ->
                        AndroidBackupStorage.saveDataUrlToInternalPhotoFile(appContext, photoId, dataUrl)
                    }
                )
            }
            when (decodeResult) {
                is BackupDecodeResult.Error -> {
                    backupFeedbackIsError = true
                    backupRestoredSuccess = false
                    backupFeedback = decodeResult.message
                }
                is BackupDecodeResult.Success -> {
                    val data = decodeResult.data
                    withContext(Dispatchers.IO) {
                        prefs.setCity(data.city)
                        prefs.setThemeMode(data.themeMode)
                        prefs.setAccentSeed(data.accentSeed)
                        prefs.setPhotoReminderMinutes(data.photoReminderMinutes)
                        prefs.saveSearchParams(CityId.SPB, data.spbParams)
                        prefs.saveSearchParams(CityId.OMSK, data.omskParams)
                        prefs.persistSavedRoutes(data.savedRoutes)
                        prefs.persistHistory(data.history)
                    }
                    selectedCity = data.city
                    themeMode = data.themeMode
                    accentSeed = data.accentSeed
                    photoReminderMinutes = data.photoReminderMinutes
                    spbParams = data.spbParams
                    omskParams = data.omskParams
                    spbParamsDirty = false
                    omskParamsDirty = false
                    savedRoutes = data.savedRoutes
                    history = data.history
                    mismatchDismissed = true

                    val restoredCfg = cities[data.city]
                    if (restoredCfg != null) {
                        val restoredParams = if (data.city == CityId.OMSK) data.omskParams else data.spbParams
                        val regen = withContext(Dispatchers.Default) {
                            TramRouter.generateTramAdventure(
                                city = restoredCfg.id,
                                stops = restoredCfg.stops,
                                routes = restoredCfg.routes,
                                landmarks = restoredCfg.landmarks,
                                startStopId = restoredParams.startStopId,
                                destinationStopId = restoredParams.destinationStopId,
                                theme = restoredParams.themeFilter,
                                durationPreset = restoredParams.durationPreset,
                                timeMode = restoredParams.timeMode,
                                deadlineMinutes = restoredParams.deadlineMinutes,
                                includePlanned2027 = restoredParams.includePlanned2027,
                                dayOfWeek = restoredParams.dayOfWeek,
                                timeMinutes = restoredParams.timeMinutes,
                                allowFallbackTransit = restoredParams.allowFallbackTransit,
                                modeBalance = restoredParams.modeBalance,
                                loopMode = restoredParams.loopMode,
                                weatherMode = restoredParams.weatherMode,
                                preferSpecialFleet = restoredParams.preferSpecialFleet
                            )
                        }
                        outcome = regen
                    }

                    backupFeedbackIsError = false
                    backupRestoredSuccess = true
                    val cityShort = restoredCfg?.shortName ?: data.city.id
                    backupFeedback = "Восстановлено ($cityShort): маршрутов — ${data.savedRoutes.size}, поездок — ${data.history.size}, фото — ${data.photosRestoredCount}."
                    Toast.makeText(activity, "Данные из .relsohod восстановлены", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val file = pendingInstallApk
        if (file != null && file.exists() && AppUpdateManager.canRequestPackageInstalls(activity)) {
            pendingInstallApk = null
            if (!AppUpdateManager.launchApkInstaller(activity, file)) {
                Toast.makeText(activity, "Не удалось запустить установщик APK", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun triggerApkInstall(file: File) {
        if (!file.exists()) {
            Toast.makeText(activity, "Файл обновления не найден, скачайте заново", Toast.LENGTH_SHORT).show()
            updateDownloadState = UpdateDownloadState.Idle
            return
        }
        if (AppUpdateManager.canRequestPackageInstalls(activity)) {
            if (!AppUpdateManager.launchApkInstaller(activity, file)) {
                Toast.makeText(activity, "Не удалось открыть установщик APK", Toast.LENGTH_SHORT).show()
            }
        } else {
            pendingInstallApk = file
            Toast.makeText(activity, "Разрешите установку обновлений для Рельсохода", Toast.LENGTH_LONG).show()
            runCatching {
                unknownSourcesLauncher.launch(AppUpdateManager.buildUnknownSourcesSettingsIntent(activity))
            }.onFailure {
                pendingInstallApk = null
                Toast.makeText(activity, "Откройте настройки безопасности и разрешите установку APK", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun checkUpdates(manual: Boolean) {
        if (isCheckingUpdates) return
        coroutineScope.launch {
            isCheckingUpdates = true
            if (manual) {
                dismissedUpdateTag = null
                withContext(Dispatchers.IO) { prefs.setDismissedUpdateTag(null) }
            }
            val result = AppUpdateManager.checkForUpdates(
                repoSlug = BuildConfig.GITHUB_REPO,
                currentVersionName = BuildConfig.VERSION_NAME,
                currentCiRunNumber = BuildConfig.CI_RUN_NUMBER,
                preferDebugApk = BuildConfig.DEBUG
            )
            updateCheckResult = result
            isCheckingUpdates = false
            if (manual && result is UpdateCheckResult.UpToDate) {
                Toast.makeText(activity, "У вас установлена актуальная версия", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun startUpdateDownload(release: AvailableRelease) {
        if (updateDownloadState is UpdateDownloadState.Downloading) return
        updateDownloadJob?.cancel()
        updateDownloadJob = coroutineScope.launch {
            withContext(Dispatchers.IO) {
                if (release.notesSummary.isNotBlank()) {
                    prefs.setPendingUpdateNotes(release.tagName, release.notesSummary)
                }
            }
            updateDownloadState = UpdateDownloadState.Downloading(
                progress = null,
                downloadedBytes = 0L,
                totalBytes = release.apkAsset.sizeBytes
            )
            val result = AppUpdateManager.downloadUpdateApk(
                context = appContext,
                release = release,
                onProgress = { progress, downloaded, total ->
                    coroutineScope.launch {
                        if (updateDownloadState is UpdateDownloadState.Downloading) {
                            updateDownloadState = UpdateDownloadState.Downloading(progress, downloaded, total)
                        }
                    }
                }
            )
            result.onSuccess { apkFile ->
                val sigCompat = withContext(Dispatchers.IO) {
                    AppUpdateManager.checkSignatureCompatibility(appContext, apkFile)
                }
                updateDownloadState = UpdateDownloadState.ReadyToInstall(apkFile, release, sigCompat)
                when (sigCompat) {
                    SignatureCompatibility.DIFFERENT_SIGNATURE -> {
                        Toast.makeText(
                            activity,
                            "Старая версия подписана другим ключом — сохраните APK в Загрузки и удалите её один раз",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    SignatureCompatibility.PACKAGE_MISMATCH -> {
                        Toast.makeText(
                            activity,
                            "Скачанный APK относится к другому варианту пакета (debug/release)",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    SignatureCompatibility.DOWNGRADE_CONFLICT -> {
                        Toast.makeText(
                            activity,
                            "Версия кода скачанного APK ниже уже установленной сборки",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    SignatureCompatibility.COMPATIBLE,
                    SignatureCompatibility.UNKNOWN -> {
                        triggerApkInstall(apkFile)
                    }
                }
            }.onFailure { err ->
                updateDownloadState = UpdateDownloadState.Failed(
                    err.message?.takeIf { it.isNotBlank() } ?: "Не удалось скачать APK"
                )
            }
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
            timeMode = p.timeMode,
            deadlineMinutes = p.deadlineMinutes,
            includePlanned2027 = p.includePlanned2027,
            dayOfWeek = p.dayOfWeek,
            timeMinutes = p.timeMinutes,
            allowFallbackTransit = p.allowFallbackTransit,
            modeBalance = p.modeBalance,
            loopMode = p.loopMode,
            weatherMode = p.weatherMode,
            preferSpecialFleet = p.preferSpecialFleet,
            seed = seed
        )
    }

    fun launchGenerate(
        cityCfg: CityConfig,
        p: SearchParamsState,
        seed: Long? = null,
        switchToRouteAfter: Boolean = false
    ) {
        generationJob?.cancel()
        generationJob = coroutineScope.launch {
            isGenerating = true
            val res = withContext(Dispatchers.Default) {
                generateTripFor(cityCfg, p, seed)
            }
            outcome = res
            isGenerating = false
            if (switchToRouteAfter) {
                activeTab = BottomTab.ROUTE
            }
        }
    }

    val currentCityConfig = cities[selectedCity] ?: cities.values.firstOrNull()
    val currentParams = if (selectedCity == CityId.SPB) spbParams else omskParams
    val currentScroll = if (selectedCity == CityId.SPB) spbScroll else omskScroll

    fun updateCurrentParams(next: SearchParamsState) {
        if (selectedCity == CityId.SPB) {
            spbParams = next
            spbParamsDirty = true
        } else {
            omskParams = next
            omskParamsDirty = true
        }
    }

    // Debounced off-main-thread persistence for search parameters
    LaunchedEffect(spbParams, spbParamsDirty) {
        if (!isBootstrapping && spbParamsDirty) {
            delay(250)
            withContext(Dispatchers.IO) {
                prefs.saveSearchParams(CityId.SPB, spbParams)
            }
            spbParamsDirty = false
        }
    }
    LaunchedEffect(omskParams, omskParamsDirty) {
        if (!isBootstrapping && omskParamsDirty) {
            delay(250)
            withContext(Dispatchers.IO) {
                prefs.saveSearchParams(CityId.OMSK, omskParams)
            }
            omskParamsDirty = false
        }
    }

    var pendingApplyNearestStart by remember { mutableStateOf(false) }
    var pendingAutoSelectCity by remember { mutableStateOf(false) }

    fun handleLocationSuccess(
        lat: Double,
        lng: Double,
        applyNearestStart: Boolean,
        autoSelectCity: Boolean = false
    ) {
        val detection = NetworkRepository.detectCityFromCoords(lat, lng, cities)
        cityDetection = detection
        mismatchDismissed = false

        if (autoSelectCity && detection.detected != null) {
            val detectedCity = detection.detected
            val detectedCfg = cities[detectedCity] ?: return
            selectedCity = detectedCity
            coroutineScope.launch(Dispatchers.IO) {
                prefs.setCity(detectedCity)
            }
            val nearest = TramRouter.findNearestStops(lat, lng, detectedCfg.stops, limit = 1, includeIsolated = false).firstOrNull()
            val baseParams = if (detectedCity == CityId.SPB) spbParams else omskParams
            val updatedParams = if (nearest != null) {
                baseParams.copy(startStopId = nearest.first.id, destinationStopId = null, destinationAddressLabel = "")
            } else {
                baseParams
            }
            if (detectedCity == CityId.SPB) {
                spbParams = updatedParams
                spbParamsDirty = true
            } else {
                omskParams = updatedParams
                omskParamsDirty = true
            }
            launchGenerate(detectedCfg, updatedParams, seed = System.currentTimeMillis())
            geoStatusMessage = if (nearest != null) {
                val distMeters = (nearest.second * 1000).toInt()
                "Город выбран автоматически: ${detectedCfg.name} · ближайшая остановка: «${nearest.first.name}» (~$distMeters м)"
            } else {
                "Город выбран автоматически: ${detectedCfg.name}."
            }
            return
        }

        val cfg = cities[selectedCity] ?: currentCityConfig ?: return
        if (detection.detected != null && detection.detected != selectedCity) {
            val detectedName = cities[detection.detected]?.name.orEmpty()
            geoStatusMessage = "По координатам вы в городе $detectedName, а выбран ${cfg.name}."
            return
        }
        if (detection.detected == null) {
            geoStatusMessage = if (autoSelectCity) {
                "Вы находитесь за пределами СПб и Омска — выберите город вручную на следующем шаге."
            } else {
                "Вы находитесь за пределами СПб и Омска — показана сеть выбранного города (${cfg.name})."
            }
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

    fun requestSingleLocation(applyNearestStart: Boolean, autoSelectCity: Boolean) {
        geoStatusMessage = "Определяем координаты..."
        fetchCurrentLocation(
            context = activity,
            onSuccess = { loc -> handleLocationSuccess(loc.latitude, loc.longitude, applyNearestStart, autoSelectCity) },
            onFailure = { reason -> geoStatusMessage = reason }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
            (grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
        if (granted) {
            requestSingleLocation(pendingApplyNearestStart, pendingAutoSelectCity)
        } else {
            geoStatusMessage = "Доступ к геолокации отклонён — выберите город и остановку вручную."
        }
    }

    fun triggerLocationFlow(applyNearestStart: Boolean, autoSelectCity: Boolean = false) {
        pendingApplyNearestStart = applyNearestStart
        pendingAutoSelectCity = autoSelectCity
        if (hasLocationPermission(activity)) {
            requestSingleLocation(applyNearestStart, autoSelectCity)
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Async bootstrap: load assets & prefs on IO, then generate initial route on Default
    LaunchedEffect(Unit) {
        val data = withContext(Dispatchers.IO) {
            val loadedCities = NetworkRepository.loadFromAssets(appContext)
            val spbDefault = loadedCities[CityId.SPB]?.defaultStartStopId ?: "spb_sen"
            val omskDefault = loadedCities[CityId.OMSK]?.defaultStartStopId ?: "omsk_cirk"
            BootstrapData(
                cities = loadedCities,
                onboardingDone = prefs.isOnboardingDone(),
                selectedCity = prefs.getCity(),
                themeMode = prefs.getThemeMode(),
                accentSeed = prefs.getAccentSeed(),
                photoReminderMinutes = prefs.getPhotoReminderMinutes(),
                spbParams = prefs.getSearchParams(CityId.SPB, spbDefault),
                omskParams = prefs.getSearchParams(CityId.OMSK, omskDefault),
                savedRoutes = prefs.loadSavedRoutes(),
                history = prefs.loadHistory(),
                dismissedUpdateTag = prefs.getDismissedUpdateTag(),
                lastSeenVersionCode = prefs.getLastSeenVersionCode(),
                pendingUpdateNotes = prefs.getPendingUpdateNotes(),
                bundledChangelog = AppUpdateManager.readBundledChangelogSummary(appContext)
            )
        }
        cities = data.cities
        onboardingDone = data.onboardingDone
        selectedCity = data.selectedCity
        themeMode = data.themeMode
        accentSeed = data.accentSeed
        photoReminderMinutes = data.photoReminderMinutes
        spbParams = data.spbParams
        omskParams = data.omskParams
        savedRoutes = data.savedRoutes
        history = data.history
        dismissedUpdateTag = data.dismissedUpdateTag
        lastSeenVersionCode = data.lastSeenVersionCode
        bundledChangelog = data.bundledChangelog
        if (data.onboardingDone && data.lastSeenVersionCode < BuildConfig.VERSION_CODE) {
            whatsNewNotes = data.pendingUpdateNotes?.second?.takeIf { it.isNotBlank() } ?: data.bundledChangelog
            whatsNewReleaseTag = data.pendingUpdateNotes?.first
            whatsNewIsPostUpdate = true
            showWhatsNewDialog = true
        }
        isBootstrapping = false

        val initialCfg = data.cities[data.selectedCity] ?: data.cities.values.firstOrNull()
        val initialParams = if (data.selectedCity == CityId.SPB) data.spbParams else data.omskParams
        if (initialCfg != null) {
            launchGenerate(initialCfg, initialParams, seed = 42L)
        }

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
        mismatchDismissed = true
        coroutineScope.launch(Dispatchers.IO) {
            prefs.setCity(newCity)
        }
        val newCfg = cities[newCity] ?: return
        val newParams = if (newCity == CityId.SPB) spbParams else omskParams
        launchGenerate(newCfg, newParams, seed = System.currentTimeMillis())
    }

    LaunchedEffect(isBootstrapping) {
        if (!isBootstrapping && showAnimatedSplash) {
            delay(1050)
            showAnimatedSplash = false
        }
    }

    LaunchedEffect(isBootstrapping) {
        if (!isBootstrapping && updateCheckResult == null) {
            delay(1200)
            checkUpdates(manual = false)
        }
    }

    RelsokhodTheme(themeMode = themeMode, accentSeed = accentSeed) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!isBootstrapping && currentCityConfig != null) {
                if (!onboardingDone) {
                    OnboardingScreen(
                        selectedCity = selectedCity,
                        cities = cities,
                        geoStatusMessage = geoStatusMessage,
                        cityDetection = cityDetection,
                        backupFeedback = backupFeedback,
                        backupFeedbackIsError = backupFeedbackIsError,
                        backupRestoredSuccess = backupRestoredSuccess,
                        onSelectCity = { switchCity(it) },
                        onRequestLocation = { triggerLocationFlow(applyNearestStart = true, autoSelectCity = true) },
                        onImportBackup = {
                            importBackupLauncher.launch(arrayOf("*/*"))
                        },
                        onComplete = {
                            onboardingDone = true
                            lastSeenVersionCode = BuildConfig.VERSION_CODE
                            backupFeedback = null
                            backupRestoredSuccess = false
                            coroutineScope.launch(Dispatchers.IO) {
                                prefs.setOnboardingDone(true)
                                prefs.setLastSeenVersionCode(BuildConfig.VERSION_CODE)
                                prefs.clearPendingUpdateNotes()
                            }
                        }
                    )
                } else {
                    var selectedAlternativeIdx by remember(outcome) { mutableIntStateOf(0) }
                    val rootTrip = (outcome as? GenerationOutcome.Ok)?.trip
                    val activeTrip = rootTrip?.let { it.alternatives.getOrNull(selectedAlternativeIdx) ?: it }
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
                        icon = { Icon(Icons.AutoMirrored.Rounded.AltRoute, contentDescription = null) },
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
                        icon = { Icon(Icons.AutoMirrored.Rounded.FormatListBulleted, contentDescription = null) },
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
                val availableUpdate = (updateCheckResult as? UpdateCheckResult.UpdateAvailable)?.release
                val showUpdateBanner = availableUpdate != null && (
                    dismissedUpdateTag != availableUpdate.tagName ||
                        updateDownloadState !is UpdateDownloadState.Idle
                    )
                if (showUpdateBanner && availableUpdate != null) {
                    AppUpdateBanner(
                        release = availableUpdate,
                        downloadState = updateDownloadState,
                        onStartDownload = { startUpdateDownload(availableUpdate) },
                        onInstallApk = { file -> triggerApkInstall(file) },
                        onExportAndUninstall = { file, release -> requestExportAndUninstall(file, release) },
                        onExportBackup = {
                            exportBackupLauncher.launch(BackupCodec.buildBackupFilename())
                        },
                        backupSavedForUpdate = backupSavedForUpdate,
                        onOpenInBrowser = { url -> openReleaseInBrowser(url) },
                        onDismiss = {
                            dismissedUpdateTag = availableUpdate.tagName
                            coroutineScope.launch(Dispatchers.IO) {
                                prefs.setDismissedUpdateTag(availableUpdate.tagName)
                            }
                        }
                    )
                }

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
                                isGenerating = isGenerating,
                                onUpdateParams = { updateCurrentParams(it) },
                                onDetectLocation = { triggerLocationFlow(applyNearestStart = true) },
                                onPickOnMap = { mode ->
                                    mapPickMode = mode
                                    activeTab = BottomTab.ROUTE
                                },
                                onGenerate = {
                                    launchGenerate(
                                        cityCfg = currentCityConfig,
                                        p = currentParams,
                                        switchToRouteAfter = true
                                    )
                                }
                            )
                        }

                        BottomTab.ROUTE -> {
                            RouteTab(
                                cityConfig = currentCityConfig,
                                params = currentParams,
                                outcome = outcome,
                                selectedAlternativeIdx = selectedAlternativeIdx,
                                onSelectAlternative = { selectedAlternativeIdx = it },
                                mapPickMode = mapPickMode,
                                isSaved = isTripSaved,
                                isGenerating = isGenerating,
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
                                    val cfg = currentCityConfig
                                    val p = currentParams
                                    generationJob?.cancel()
                                    generationJob = coroutineScope.launch {
                                        isGenerating = true
                                        val extended = withContext(Dispatchers.Default) {
                                            TramRouter.extendTripLastSegment(
                                                trip = trip,
                                                stops = cfg.stops,
                                                routes = cfg.routes,
                                                landmarks = cfg.landmarks,
                                                allowFallbackTransit = p.allowFallbackTransit,
                                                includePlanned2027 = p.includePlanned2027,
                                                weatherMode = p.weatherMode,
                                                preferSpecialFleet = p.preferSpecialFleet
                                            )
                                        }
                                        isGenerating = false
                                        if (extended != null) {
                                            outcome = GenerationOutcome.Ok(extended)
                                        } else {
                                            Toast.makeText(activity, "Дальше от этой конечной линии не продолжаются", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onRegenerate = {
                                    launchGenerate(currentCityConfig, currentParams)
                                },
                                onEnableFallbackAndRetry = {
                                    val next = currentParams.copy(allowFallbackTransit = true)
                                    updateCurrentParams(next)
                                    launchGenerate(currentCityConfig, next)
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
                                        backupSavedForUpdate = false
                                        coroutineScope.launch(Dispatchers.IO) {
                                            prefs.persistSavedRoutes(nextSaved)
                                        }
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
                                    backupSavedForUpdate = false
                                    coroutineScope.launch(Dispatchers.IO) {
                                        prefs.persistHistory(nextHistory)
                                    }
                                    activeTab = BottomTab.FAVORITES
                                    Toast.makeText(activity, "Поездка добавлена в Паспорт!", Toast.LENGTH_SHORT).show()
                                },
                                onSpeakText = onSpeakText
                            )
                        }

                        BottomTab.FAVORITES -> {
                            FavoritesTab(
                                allCities = cities,
                                savedRoutes = savedRoutes,
                                history = history,
                                onOpenTrip = { trip ->
                                    if (trip.city != selectedCity) {
                                        selectedCity = trip.city
                                        coroutineScope.launch(Dispatchers.IO) {
                                            prefs.setCity(trip.city)
                                        }
                                    }
                                    outcome = GenerationOutcome.Ok(trip)
                                    activeTab = BottomTab.ROUTE
                                },
                                onDeleteSaved = { id ->
                                    val next = savedRoutes.filterNot { it.id == id }
                                    savedRoutes = next
                                    coroutineScope.launch(Dispatchers.IO) {
                                        prefs.persistSavedRoutes(next)
                                    }
                                },
                                onAddPhotoToHistory = { entryId, photo ->
                                    val next = history.map { e ->
                                        if (e.id == entryId) e.copy(photos = listOf(photo) + e.photos) else e
                                    }
                                    history = next
                                    coroutineScope.launch(Dispatchers.IO) {
                                        prefs.persistHistory(next)
                                    }
                                },
                                onDeleteHistory = { id ->
                                    val next = history.filterNot { it.id == id }
                                    history = next
                                    coroutineScope.launch(Dispatchers.IO) {
                                        prefs.persistHistory(next)
                                    }
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
                isCheckingUpdates = isCheckingUpdates,
                updateCheckResult = updateCheckResult,
                updateDownloadState = updateDownloadState,
                onCheckUpdates = { checkUpdates(manual = true) },
                onStartUpdateDownload = { release -> startUpdateDownload(release) },
                onInstallUpdateApk = { file -> triggerApkInstall(file) },
                onExportAndUninstall = { file, release -> requestExportAndUninstall(file, release) },
                onOpenReleaseUrl = { url -> openReleaseInBrowser(url) },
                onShowWhatsNew = {
                    whatsNewNotes = bundledChangelog
                    whatsNewReleaseTag = null
                    whatsNewIsPostUpdate = false
                    showWhatsNewDialog = true
                },
                backupFeedback = backupFeedback,
                backupFeedbackIsError = backupFeedbackIsError,
                backupSavedForUpdate = backupSavedForUpdate,
                onExportBackup = {
                    exportBackupLauncher.launch(BackupCodec.buildBackupFilename())
                },
                onImportBackup = {
                    importBackupLauncher.launch(arrayOf("*/*"))
                },
                onChangeCity = { switchCity(it) },
                onChangeThemeMode = {
                    themeMode = it
                    coroutineScope.launch(Dispatchers.IO) { prefs.setThemeMode(it) }
                },
                onChangeAccentSeed = {
                    accentSeed = it
                    coroutineScope.launch(Dispatchers.IO) { prefs.setAccentSeed(it) }
                },
                onChangePhotoReminderMinutes = {
                    photoReminderMinutes = it
                    coroutineScope.launch(Dispatchers.IO) { prefs.setPhotoReminderMinutes(it) }
                },
                onRestartOnboarding = {
                    backupFeedback = null
                    backupRestoredSuccess = false
                    onboardingDone = false
                    coroutineScope.launch(Dispatchers.IO) { prefs.setOnboardingDone(false) }
                },
                onDismiss = { settingsOpen = false }
            )
        }

        if (showWhatsNewDialog) {
            WhatsNewDialog(
                changelogSummary = whatsNewNotes,
                isPostUpdate = whatsNewIsPostUpdate,
                releaseTag = whatsNewReleaseTag,
                onDismiss = {
                    showWhatsNewDialog = false
                    if (lastSeenVersionCode < BuildConfig.VERSION_CODE) {
                        lastSeenVersionCode = BuildConfig.VERSION_CODE
                        coroutineScope.launch(Dispatchers.IO) {
                            prefs.setLastSeenVersionCode(BuildConfig.VERSION_CODE)
                            prefs.clearPendingUpdateNotes()
                        }
                    }
                }
            )
        }

        val promptTarget = reinstallBackupPrompt
        if (promptTarget != null) {
            val compat = (updateDownloadState as? UpdateDownloadState.ReadyToInstall)?.signatureCompatibility
            val reasonLine = when (compat) {
                SignatureCompatibility.PACKAGE_MISMATCH ->
                    "Скачанный APK относится к другому пакету (debug/release)."
                SignatureCompatibility.DOWNGRADE_CONFLICT ->
                    "Для перехода на эту версию нужна чистая переустановка."
                else ->
                    "Удаление старой версии перед переустановкой сотрёт избранные маршруты, паспорт с фото и настройки."
            }
            AlertDialog(
                onDismissRequest = { reinstallBackupPrompt = null },
                title = {
                    Text(
                        text = "Сделать бэкап перед переустановкой?",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "$reasonLine Сохраните файл .relsohod сейчас — после сохранения приложение сразу выгрузит APK и откроет удаление старой версии.",
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    reinstallBackupPrompt = null
                                    exportAndUninstall(promptTarget.first, promptTarget.second)
                                }
                            ) {
                                Text("Удалить без бэкапа", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            pendingUninstallAfterBackup = promptTarget
                            reinstallBackupPrompt = null
                            exportBackupLauncher.launch(BackupCodec.buildBackupFilename())
                        }
                    ) {
                        Text("Сделать бэкап (.relsohod)")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { reinstallBackupPrompt = null }) {
                        Text("Отмена")
                    }
                }
            )
        }
                }
            }

            AnimatedVisibility(
                visible = showAnimatedSplash || isBootstrapping || currentCityConfig == null,
                exit = fadeOut(animationSpec = tween(durationMillis = 360))
            ) {
                StartupSplash(
                    onSkip = {
                        if (!isBootstrapping && currentCityConfig != null) {
                            showAnimatedSplash = false
                        }
                    }
                )
            }
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
