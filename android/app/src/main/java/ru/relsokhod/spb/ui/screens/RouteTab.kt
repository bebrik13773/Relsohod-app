package ru.relsokhod.spb.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsSubway
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tram
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.relsokhod.spb.live.LiveGeoPoint
import ru.relsokhod.spb.live.LiveTripLiveAlertManager
import ru.relsokhod.spb.live.LiveTripProgressMath
import ru.relsokhod.spb.model.AccentSeed
import ru.relsokhod.spb.model.CityConfig
import ru.relsokhod.spb.model.GeneratedTrip
import ru.relsokhod.spb.model.GenerationOutcome
import ru.relsokhod.spb.model.LandmarkCategory
import ru.relsokhod.spb.model.RouteSegment
import ru.relsokhod.spb.model.SearchParamsState
import ru.relsokhod.spb.model.SegmentKind
import ru.relsokhod.spb.model.ThemeMode
import ru.relsokhod.spb.model.TramStop
import ru.relsokhod.spb.model.TripPhotoMemory
import ru.relsokhod.spb.router.TramRouter
import ru.relsokhod.spb.ui.components.WebTramMap
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private enum class RouteSubView(val label: String) {
    MAP("Карта"),
    STEPS("Шаги"),
    PLACES("Места")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RouteTab(
    cityConfig: CityConfig,
    params: SearchParamsState,
    themeMode: ThemeMode,
    accentSeed: AccentSeed,
    outcome: GenerationOutcome?,
    selectedAlternativeIdx: Int = 0,
    onSelectAlternative: (Int) -> Unit = {},
    mapPickMode: String?,
    isSaved: Boolean,
    isGenerating: Boolean = false,
    photoReminderMinutes: Int,
    onPickStopOnMap: (TramStop) -> Unit,
    onExtendLastSegment: () -> Unit,
    onRegenerate: () -> Unit,
    onEnableFallbackAndRetry: () -> Unit,
    onSaveTrip: (GeneratedTrip) -> Unit,
    onCompleteTrip: (GeneratedTrip, List<TripPhotoMemory>) -> Unit,
    onSpeakText: (String) -> Unit
) {
    val context = LocalContext.current
    var subView by remember { mutableStateOf(if (mapPickMode != null) RouteSubView.MAP else RouteSubView.STEPS) }
    var liveTripOpen by remember { mutableStateOf(false) }

    var onlyWithPhotos by remember { mutableStateOf(true) }
    var selectedCategories by remember { mutableStateOf(emptySet<LandmarkCategory>()) }

    LaunchedEffect(mapPickMode) {
        if (mapPickMode != null) subView = RouteSubView.MAP
    }

    val visibleStops = remember(cityConfig.stops, params.includePlanned2027) {
        if (params.includePlanned2027) cityConfig.stops else cityConfig.stops.filter { !it.planned2027 }
    }
    val visibleStopIds = remember(visibleStops) { visibleStops.map { it.id }.toSet() }
    val visibleRoutes = remember(cityConfig.routes, params.includePlanned2027, visibleStopIds) {
        if (params.includePlanned2027) cityConfig.routes
        else cityConfig.routes.filter { !it.planned2027 && it.stopIds.all { sid -> sid in visibleStopIds } }
    }

    val rootTrip = (outcome as? GenerationOutcome.Ok)?.trip
    val activeTrip = rootTrip?.let { it.alternatives.getOrNull(selectedAlternativeIdx) ?: it }

    Column(modifier = Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            RouteSubView.entries.forEachIndexed { idx, view ->
                val countSuffix = when {
                    view == RouteSubView.STEPS && activeTrip != null -> " (${activeTrip.segments.size})"
                    view == RouteSubView.PLACES && activeTrip != null -> {
                        val c = TramRouter.filterLandmarks(activeTrip.landmarks, selectedCategories, onlyWithPhotos).size
                        " ($c)"
                    }
                    else -> ""
                }
                SegmentedButton(
                    selected = subView == view,
                    onClick = { subView = view },
                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = RouteSubView.entries.size)
                ) {
                    Text(view.label + countSuffix, fontSize = 12.sp, maxLines = 1)
                }
            }
        }

        when (subView) {
            RouteSubView.MAP -> {
                WebTramMap(
                    city = cityConfig.id,
                    themeMode = themeMode,
                    accentSeed = accentSeed,
                    stops = visibleStops,
                    routes = visibleRoutes,
                    trip = activeTrip,
                    showAllNetwork = params.showAllNetwork,
                    includePlanned2027 = params.includePlanned2027,
                    startStopId = params.startStopId,
                    destinationStopId = params.destinationStopId,
                    mapPickMode = mapPickMode,
                    onPickStop = onPickStopOnMap,
                    modifier = Modifier.fillMaxSize()
                )
            }

            RouteSubView.STEPS -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (outcome) {
                        null -> {
                            Text("Нажмите «Подобрать 3 варианта прогулки» во вкладке Поиск.")
                        }
                        is GenerationOutcome.NoService -> {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = outcome.reason,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                        is GenerationOutcome.NeedsFallback -> {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(outcome.reason, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(onClick = onEnableFallbackAndRetry) {
                                        Text("Включить резервный и городской транспорт")
                                    }
                                }
                            }
                        }
                        is GenerationOutcome.Ok -> {
                            val trip = activeTrip ?: outcome.trip
                            val alternatives = outcome.trip.alternatives
                            val yandexUrl = remember(trip) { TramRouter.buildYandexMapsRouteUrl(trip) }
                            val matchedGuides = remember(trip) { TramRouter.matchAudioGuidesForTrip(trip, limit = 3) }
                            val announcements = remember(trip) {
                                TramRouter.buildAnnouncements(trip, cityConfig.announcerIntro)
                            }

                            // Switcher among up to 3 generated variants
                            if (alternatives.size > 1) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    alternatives.forEachIndexed { idx, alt ->
                                        val selected = idx == selectedAlternativeIdx
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                            border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                            modifier = Modifier
                                                .widthIn(min = 148.dp, max = 210.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .clickable { onSelectAlternative(idx) }
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Text(
                                                    text = alt.variantBadge ?: "Вариант ${idx + 1}",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = alt.variantTitle ?: "Маршрут ${idx + 1}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    maxLines = 1
                                                )
                                                val walkTxt = alt.walkDistanceKm?.takeIf { it >= 0.2 }?.let { " · пеш. $it км" }.orEmpty()
                                                val arrTxt = alt.arrivalTimeMinutes?.let { " · к ${TramRouter.formatMinutesToClock(it)}" }.orEmpty()
                                                Text(
                                                    text = "~${alt.totalDurationMinutes} мин$walkTxt$arrTxt",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = if (trip.isLoop) "Кольцо: ${trip.startStop.name.substringBefore("(").trim()}"
                                        else "${trip.startStop.name.substringBefore("(").trim()} → ${trip.endStop.name.substringBefore("(").trim()}",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp
                                    )

                                    val arrClock = TramRouter.formatMinutesToClock(
                                        trip.arrivalTimeMinutes ?: (trip.timeMinutes + trip.totalDurationMinutes)
                                    )
                                    val walkPart = trip.walkDistanceKm?.takeIf { it >= 0.1 }?.let { " · Пешком $it км" }.orEmpty()
                                    Text(
                                        text = "~${trip.totalDurationMinutes} мин · Прибытие ~$arrClock$walkPart · Пересадок: ${trip.transferCount} · Районов: ${trip.districtsVisited.size}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (trip.targetDeadlineMinutes != null) {
                                        val arrival = trip.arrivalTimeMinutes ?: (trip.timeMinutes + trip.totalDurationMinutes)
                                        var reserve = trip.targetDeadlineMinutes - arrival
                                        if (reserve < -720) reserve += 1440
                                        val onTime = reserve >= 0
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (onTime) Color(0xFF059669).copy(alpha = 0.14f) else Color(0xFFD97706).copy(alpha = 0.16f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = if (onTime) {
                                                    "Цель к ${TramRouter.formatMinutesToClock(trip.targetDeadlineMinutes)}: в запасе $reserve мин"
                                                } else {
                                                    "Цель к ${TramRouter.formatMinutesToClock(trip.targetDeadlineMinutes)}: маршрут длиннее на ${-reserve} мин"
                                                },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (onTime) Color(0xFF047857) else Color(0xFFB45309),
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }

                                    // Yandex Maps button + Copy Link
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                runCatching {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(yandexUrl))
                                                    context.startActivity(intent)
                                                }.onFailure {
                                                    Toast.makeText(context, "Не найдено приложение для открытия ссылки", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Rounded.Navigation, contentDescription = null, modifier = Modifier.size(17.dp))
                                            Spacer(modifier = Modifier.size(6.dp))
                                            Text("Яндекс Карты", fontSize = 12.sp)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                cm.setPrimaryClip(ClipData.newPlainText("Yandex Maps Route", yandexUrl))
                                                Toast.makeText(context, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Ссылка", modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    // Action chips: Live Trip, Докрутить +1, Сохранить, Другой, Поделиться
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(onClick = { liveTripOpen = true }) {
                                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.size(4.dp))
                                            Text("В пути", fontSize = 12.sp)
                                        }
                                        FilledTonalButton(enabled = !isGenerating, onClick = onExtendLastSegment) {
                                            Icon(Icons.Rounded.Casino, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.size(4.dp))
                                            Text("Докрутить +1", fontSize = 12.sp)
                                        }
                                        FilledTonalButton(enabled = !isGenerating, onClick = onRegenerate) {
                                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.size(4.dp))
                                            Text("Другой", fontSize = 12.sp)
                                        }
                                        FilledTonalButton(
                                            enabled = !isSaved,
                                            onClick = { onSaveTrip(trip) }
                                        ) {
                                            Icon(
                                                if (isSaved) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.size(4.dp))
                                            Text(if (isSaved) "Сохранено" else "Сохранить", fontSize = 12.sp)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                val linesSummary = trip.segments.mapNotNull { seg ->
                                                    seg.route?.let { TramRouter.getRouteTypeShortLabel(it) }
                                                }.distinct().joinToString(" → ")
                                                val shareText = "Маршрут в Рельсоходе (${cityConfig.name}): ${trip.startStop.name} → ${trip.endStop.name}\nЛинии: $linesSummary\nЯндекс Карты: $yandexUrl"
                                                val send = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                                }
                                                context.startActivity(Intent.createChooser(send, "Поделиться маршрутом"))
                                            }
                                        ) {
                                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.size(4.dp))
                                            Text("Поделиться", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            // Официальные аудиогиды по маршруту + опциональный TTS автоинформатор
                            if (matchedGuides.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Headphones,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = "Официальные аудиогиды",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                            }
                                            if (announcements.isNotEmpty()) {
                                                TextButton(
                                                    onClick = {
                                                        playTramBell()
                                                        onSpeakText(announcements.joinToString(". ") { it.second })
                                                    }
                                                ) {
                                                    Icon(
                                                        Icons.AutoMirrored.Rounded.VolumeUp,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Spacer(modifier = Modifier.size(4.dp))
                                                    Text("Озвучка (TTS)", fontSize = 11.sp)
                                                }
                                            }
                                        }

                                        matchedGuides.forEachIndexed { idx, matched ->
                                            if (idx > 0) {
                                                HorizontalDivider(
                                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                                                )
                                            }
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(
                                                    text = "${matched.matchReason} · ${matched.guide.portalName} · ${matched.guide.durationLabel}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = matched.guide.title,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = matched.guide.description,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                val openGuide = {
                                                    runCatching {
                                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(matched.guide.url))
                                                        context.startActivity(intent)
                                                    }.onFailure {
                                                        Toast.makeText(context, "Не найдено приложение для открытия портала", Toast.LENGTH_SHORT).show()
                                                    }
                                                    Unit
                                                }
                                                if (idx == 0) {
                                                    FilledTonalButton(onClick = openGuide) {
                                                        Icon(Icons.Rounded.Headphones, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.size(6.dp))
                                                        Text("Слушать на официальном портале", fontSize = 12.sp)
                                                        Spacer(modifier = Modifier.size(4.dp))
                                                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    }
                                                } else {
                                                    OutlinedButton(onClick = openGuide) {
                                                        Icon(Icons.Rounded.Headphones, contentDescription = null, modifier = Modifier.size(15.dp))
                                                        Spacer(modifier = Modifier.size(6.dp))
                                                        Text("Открыть гид", fontSize = 12.sp)
                                                        Spacer(modifier = Modifier.size(4.dp))
                                                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(13.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Itinerary segments
                            trip.segments.forEach { seg ->
                                val primary = MaterialTheme.colorScheme.primary
                                val badgeColor = remember(seg.id, seg.kind, seg.route?.colorHex, primary) {
                                    when (seg.kind) {
                                        SegmentKind.TRAM -> runCatching {
                                            Color(android.graphics.Color.parseColor(seg.route?.colorHex ?: "#005A8D"))
                                        }.getOrDefault(primary)
                                        SegmentKind.WALK -> Color(0xFF059669)
                                        SegmentKind.FALLBACK_TRANSIT -> Color(0xFFD97706)
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .defaultMinSize(minWidth = 48.dp, minHeight = 44.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(badgeColor)
                                                .padding(horizontal = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            when {
                                                seg.kind == SegmentKind.WALK -> Icon(
                                                    Icons.AutoMirrored.Rounded.DirectionsWalk,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                seg.kind == SegmentKind.FALLBACK_TRANSIT -> Icon(
                                                    Icons.Rounded.DirectionsBus,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                seg.route?.type == "metro" -> Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Rounded.DirectionsSubway,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text(
                                                        text = seg.route.number,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 13.sp,
                                                        maxLines = 1
                                                    )
                                                }
                                                else -> Text(
                                                    text = seg.route?.number ?: "Т",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 13.sp,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val segTitle = when (seg.kind) {
                                                    SegmentKind.TRAM -> seg.route?.let { TramRouter.getRouteTypeShortLabel(it) } ?: "Транспорт"
                                                    SegmentKind.WALK -> {
                                                        val km = (TramRouter.haversineKm(seg.fromStop.lat, seg.fromStop.lng, seg.toStop.lat, seg.toStop.lng) * 10.0).roundToInt() / 10.0
                                                        if (seg.durationMinutes >= 12) "Пешая прогулка (~$km км)"
                                                        else "Пеший переход"
                                                    }
                                                    SegmentKind.FALLBACK_TRANSIT -> seg.fallbackModeName ?: "Транспорт"
                                                }
                                                Text(
                                                    text = segTitle,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = "${seg.durationMinutes} мин",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            if (seg.route?.planned2027 == true) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFFF59E0B).copy(alpha = 0.18f)
                                                ) {
                                                    Text(
                                                        text = seg.route.plannedLabel ?: "До лета 2027",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFFB45309),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = seg.directionNote,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Посадка: ${seg.fromStop.name}", fontSize = 12.sp)
                                            Text("Выход: ${seg.toStop.name}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            RouteSubView.PLACES -> {
                val allLandmarks = activeTrip?.landmarks.orEmpty()
                val visiblePlaces = remember(allLandmarks, selectedCategories, onlyWithPhotos) {
                    TramRouter.filterLandmarks(allLandmarks, selectedCategories, onlyWithPhotos)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = onlyWithPhotos, onCheckedChange = { onlyWithPhotos = it })
                        Text("Только с фото", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LandmarkCategory.entries.forEach { cat ->
                            val selected = cat in selectedCategories
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedCategories = if (selected) selectedCategories - cat
                                    else selectedCategories + cat
                                },
                                label = { Text(cat.label, fontSize = 12.sp) }
                            )
                        }
                    }

                    if (visiblePlaces.isEmpty()) {
                        Text(
                            text = "Нет точек по выбранным фильтрам вдоль маршрута.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        visiblePlaces.forEach { lm ->
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(lm.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        text = "${lm.category.label} · ${lm.district}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(lm.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val stopsById = remember(cityConfig.stops) { cityConfig.stops.associateBy { it.id } }

    if (liveTripOpen && activeTrip != null) {
        LiveTripDialog(
            trip = activeTrip,
            stopsById = stopsById,
            announcerIntro = cityConfig.announcerIntro,
            photoReminderMinutes = photoReminderMinutes,
            onSpeakText = onSpeakText,
            onComplete = { photos ->
                LiveTripLiveAlertManager.stop(context)
                liveTripOpen = false
                onCompleteTrip(activeTrip, photos)
            },
            onDismiss = {
                LiveTripLiveAlertManager.stop(context)
                liveTripOpen = false
            }
        )
    }
}

private fun segmentColor(seg: RouteSegment, fallback: Color): Color {
    if (seg.kind == SegmentKind.WALK) return Color(0xFF6366F1)
    if (seg.kind == SegmentKind.FALLBACK_TRANSIT) return Color(0xFF16A34A)
    val hex = seg.route?.colorHex ?: return fallback
    return runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)
}

private fun segmentBadgeText(seg: RouteSegment): String {
    if (seg.kind == SegmentKind.WALK) return "Пешком"
    if (seg.kind == SegmentKind.FALLBACK_TRANSIT) return "Автобус"
    return seg.route?.number?.removePrefix("А-") ?: "Т"
}

private fun segmentModeLabel(seg: RouteSegment): String {
    if (seg.kind == SegmentKind.WALK) return if (seg.durationMinutes >= 12) "Пешая прогулка" else "Пеший переход"
    if (seg.kind == SegmentKind.FALLBACK_TRANSIT) return seg.fallbackModeName ?: "Автобус"
    return when (seg.route?.type) {
        "bus" -> "Автобус"
        "trolleybus" -> "Троллейбус"
        "metro" -> "Метро"
        "chizhik" -> "«Чижик»"
        "retro" -> "Ретро-трамвай"
        "circular" -> "Кольцевой трамвай"
        else -> "Трамвай"
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun LiveTripDialog(
    trip: GeneratedTrip,
    stopsById: Map<String, TramStop>,
    announcerIntro: String,
    photoReminderMinutes: Int,
    onSpeakText: (String) -> Unit,
    onComplete: (List<TripPhotoMemory>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var segIdx by remember { mutableIntStateOf(0) }
    val currSeg = trip.segments.getOrElse(segIdx) { trip.segments.first() }
    val nextSeg = trip.segments.getOrNull(segIdx + 1)
    val isLastSeg = segIdx >= trip.segments.lastIndex

    var elapsedSeconds by remember(segIdx) { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(true) }
    var showStepPicker by remember { mutableStateOf(false) }
    var photos by remember { mutableStateOf<List<TripPhotoMemory>>(emptyList()) }
    var elapsedPhotoSec by remember { mutableIntStateOf(0) }
    var showPhotoPrompt by remember { mutableStateOf(false) }
    var userCoords by remember { mutableStateOf<LiveGeoPoint?>(LiveTripLiveAlertManager.latestGeoPoint) }
    var bellFiredForSeg by remember(segIdx) { mutableStateOf(false) }
    var promotedAllowed by remember { mutableStateOf(LiveTripLiveAlertManager.canPostPromotedLiveAlerts(context)) }
    var hasNotificationPermission by remember { mutableStateOf(LiveTripLiveAlertManager.hasNotificationPermission(context)) }
    var hasLocationPermission by remember { mutableStateOf(LiveTripLiveAlertManager.hasLocationPermission(context)) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fine = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = fine || coarse
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Listen to fine/network GPS while dialog is open for smooth real-time progress line
    DisposableEffect(hasLocationPermission) {
        if (!hasLocationPermission) {
            onDispose { }
        } else {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    val point = LiveGeoPoint(
                        lat = location.latitude,
                        lng = location.longitude,
                        accuracyMeters = if (location.hasAccuracy()) location.accuracy else null
                    )
                    userCoords = point
                    LiveTripLiveAlertManager.latestGeoPoint = point
                }
                override fun onProviderDisabled(provider: String) {}
                override fun onProviderEnabled(provider: String) {}
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }
            if (lm != null) {
                runCatching {
                    val lastGps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    val lastNet = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    val bestLast = listOfNotNull(lastGps, lastNet).maxByOrNull { it.time }
                    if (bestLast != null) {
                        val pt = LiveGeoPoint(bestLast.latitude, bestLast.longitude, if (bestLast.hasAccuracy()) bestLast.accuracy else null)
                        userCoords = pt
                        LiveTripLiveAlertManager.latestGeoPoint = pt
                    }
                    if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 4f, listener, Looper.getMainLooper())
                    }
                    if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                        lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3500L, 8f, listener, Looper.getMainLooper())
                    }
                }
            }
            onDispose {
                runCatching { lm?.removeUpdates(listener) }
            }
        }
    }

    val liveProgress = remember(currSeg, stopsById, elapsedSeconds, userCoords) {
        LiveTripProgressMath.computeSegmentLiveProgress(
            segment = currSeg,
            stopsById = stopsById,
            elapsedSeconds = elapsedSeconds,
            userCoords = userCoords
        )
    }

    val animatedProgress by animateFloatAsState(
        targetValue = liveProgress.progressFraction,
        animationSpec = tween(durationMillis = 650),
        label = "live_route_progress"
    )

    val totalRemainingMinutes = remember(trip.segments, segIdx, liveProgress.minutesRemaining) {
        val after = trip.segments.drop(segIdx + 1).sumOf { it.durationMinutes }
        maxOf(liveProgress.minutesRemaining, liveProgress.minutesRemaining + after)
    }

    val arrivalClock = remember(totalRemainingMinutes) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MINUTE, totalRemainingMinutes)
        String.format(Locale.US, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }

    val primaryGuide = remember(trip, currSeg.route?.number) {
        TramRouter.matchAudioGuidesForTrip(
            trip = trip,
            limit = 1,
            preferredRouteNumber = currSeg.route?.number
        ).firstOrNull()
    }
    val announcements = remember(trip) { TramRouter.buildAnnouncements(trip, announcerIntro) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val savedPath = withContext(Dispatchers.IO) { copyPhotoToInternalStorage(context, uri) }
                if (savedPath != null) {
                    val isoNow = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
                    photos = listOf(
                        TripPhotoMemory(
                            id = "photo-${System.currentTimeMillis()}",
                            filePath = savedPath,
                            caption = "${currSeg.fromStop.name.substringBefore("(").trim()} — ${currSeg.route?.let { TramRouter.getRouteTypeShortLabel(it) } ?: "Переход"}",
                            takenAtIso = isoNow
                        )
                    ) + photos
                    showPhotoPrompt = false
                }
            }
        }
    }

    LaunchedEffect(running, segIdx, photoReminderMinutes) {
        val totalSegSec = maxOf(60, currSeg.durationMinutes * 60)
        while (running) {
            delay(1000L)
            if (LiveTripLiveAlertManager.stopRequestedFromNotification) {
                LiveTripLiveAlertManager.stopRequestedFromNotification = false
                onDismiss()
                break
            }
            if (elapsedSeconds < totalSegSec) {
                elapsedSeconds += 1
                if (elapsedSeconds >= totalSegSec && !bellFiredForSeg) {
                    bellFiredForSeg = true
                    playTramBell()
                }
            }
            if (photoReminderMinutes > 0) {
                elapsedPhotoSec += 1
                if (elapsedPhotoSec >= photoReminderMinutes * 60) {
                    elapsedPhotoSec = 0
                    showPhotoPrompt = true
                }
            }
            LiveTripLiveAlertManager.latestGeoPoint?.let { bgGeo ->
                userCoords = bgGeo
            }
            promotedAllowed = LiveTripLiveAlertManager.canPostPromotedLiveAlerts(context)
        }
    }

    // Keep Android 16/17 Live Updates (ColorOS Live Alerts / Realme UI Dynamic Island) synchronized
    LaunchedEffect(currSeg.id, liveProgress.minutesRemaining, liveProgress.stopsRemaining, liveProgress.isGpsLocked, (liveProgress.progressFraction * 40f).roundToInt(), hasNotificationPermission) {
        if (hasNotificationPermission) {
            val snapshot = LiveTripLiveAlertManager.buildSnapshot(currSeg, liveProgress)
            LiveTripLiveAlertManager.startOrUpdate(
                context = context,
                snapshot = snapshot,
                segment = currSeg,
                stopsById = stopsById,
                elapsedSeconds = elapsedSeconds
            )
        }
    }

    val accentColor = segmentColor(currSeg, MaterialTheme.colorScheme.primary)
    val destinationHint = remember(currSeg) {
        currSeg.route?.title?.split("—")?.lastOrNull()?.trim()
            ?: currSeg.toStop.name.substringBefore("(").trim()
    }
    val headerSummaryText = if (currSeg.kind == SegmentKind.WALK) {
        "${liveProgress.minutesRemaining} мин · пешком"
    } else {
        "${liveProgress.minutesRemaining} мин · ${liveProgress.stopsRemaining} ${LiveTripProgressMath.pluralizeStopsRu(liveProgress.stopsRemaining)}"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.56f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 780.dp)
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    // Drag handle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 36.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f))
                        )
                    }

                    // Top Yandex-style header row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            IconButton(
                                onClick = {
                                    playTramBell()
                                    val line = announcements.firstOrNull { it.first.startsWith(currSeg.id) }?.second
                                        ?: announcements.firstOrNull()?.second.orEmpty()
                                    onSpeakText(line)
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "Озвучить")
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = headerSummaryText,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Весь маршрут: ${trip.totalDurationMinutes} мин · $arrivalClock",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Rounded.Close, contentDescription = "Свернуть")
                            }
                        }
                    }

                    // Horizontal progress timeline bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accentColor
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                SegmentModeIconSmall(currSeg, Color.White)
                                Text(
                                    text = segmentBadgeText(currSeg),
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        }

                        val trackBg = MaterialTheme.colorScheme.surfaceVariant
                        val surfaceColor = MaterialTheme.colorScheme.surface
                        Canvas(
                            modifier = Modifier
                                .weight(1f)
                                .height(18.dp)
                        ) {
                            val cy = size.height / 2f
                            val pad = 6.dp.toPx()
                            val usableW = (size.width - pad * 2).coerceAtLeast(1f)
                            drawLine(
                                color = trackBg,
                                start = Offset(pad, cy),
                                end = Offset(pad + usableW, cy),
                                strokeWidth = 5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            val activeEnd = pad + usableW * animatedProgress.coerceIn(0f, 1f)
                            drawLine(
                                color = accentColor,
                                start = Offset(pad, cy),
                                end = Offset(activeEnd, cy),
                                strokeWidth = 5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            val count = liveProgress.stops.size.coerceAtLeast(2)
                            for (i in 0 until count) {
                                val x = pad + usableW * (i.toFloat() / (count - 1).toFloat())
                                val passed = i <= liveProgress.currentStopIndex
                                drawCircle(
                                    color = if (passed) accentColor else surfaceColor,
                                    radius = 4.dp.toPx(),
                                    center = Offset(x, cy)
                                )
                                drawCircle(
                                    color = accentColor,
                                    radius = 4.dp.toPx(),
                                    center = Offset(x, cy),
                                    style = Stroke(width = 1.6.dp.toPx())
                                )
                            }
                            drawCircle(color = Color.White, radius = 6.5.dp.toPx(), center = Offset(activeEnd, cy))
                            drawCircle(color = accentColor, radius = 4.8.dp.toPx(), center = Offset(activeEnd, cy))
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (nextSeg != null) segmentColor(nextSeg, MaterialTheme.colorScheme.secondary)
                            else MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(
                                modifier = Modifier.size(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (nextSeg != null) {
                                    SegmentModeIconSmall(nextSeg, Color.White)
                                } else {
                                    Icon(
                                        Icons.Rounded.Flag,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Exit stop prompt banner
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF2563EB),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    SegmentModeIconSmall(currSeg, Color.White, sizeDp = 20)
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (currSeg.kind == SegmentKind.WALK) "Направляйтесь к остановке" else "Выходите на остановке",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = currSeg.toStop.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Dynamic Island / ColorOS Live Alerts status chip (Android 16/17 API 36+)
                    if (Build.VERSION.SDK_INT >= 36) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (promotedAllowed && hasNotificationPermission) {
                                Color(0xFF16A34A).copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable {
                                    if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        LiveTripLiveAlertManager.openPromotedLiveAlertsSettings(context)
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (promotedAllowed && hasNotificationPermission) {
                                        "Dynamic Island / Live Alerts активен"
                                    } else {
                                        "Включить Dynamic Island (Live Alerts)"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (promotedAllowed && hasNotificationPermission) {
                                        Color(0xFF15803D)
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    }
                                )
                                Text(
                                    text = if (promotedAllowed && hasNotificationPermission) "Вкл" else "Настроить →",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Expanded vertical linear route container + sticky bottom location button
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(min = 240.dp, max = 380.dp)
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Segment card header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = accentColor,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        SegmentModeIconSmall(currSeg, Color.White, sizeDp = 19)
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = segmentBadgeText(currSeg),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Surface(
                                            shape = CircleShape,
                                            color = if (liveProgress.isGpsLocked) {
                                                Color(0xFF16A34A).copy(alpha = 0.16f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            }
                                        ) {
                                            Text(
                                                text = if (liveProgress.isGpsLocked) "GPS · LIVE" else "Авто-ход",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (liveProgress.isGpsLocked) Color(0xFF15803D) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${segmentModeLabel(currSeg)} · В пути ${currSeg.durationMinutes} мин",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "В направлении: «$destinationHint»",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (trip.segments.size > 1) {
                                        Text(
                                            text = if (showStepPicker) "Скрыть шаги" else "Выбрать другой шаг (${segIdx + 1} из ${trip.segments.size})",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .padding(top = 2.dp)
                                                .clickable { showStepPicker = !showStepPicker }
                                        )
                                    }
                                }
                            }

                            if (showStepPicker && trip.segments.size > 1) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 14.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    trip.segments.forEachIndexed { idx, seg ->
                                        val isCurr = idx == segIdx
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isCurr) segmentColor(seg, MaterialTheme.colorScheme.primary) else MaterialTheme.colorScheme.surface,
                                            modifier = Modifier.clickable {
                                                segIdx = idx
                                                showStepPicker = false
                                            }
                                        ) {
                                            Text(
                                                text = "${idx + 1}. ${segmentBadgeText(seg)} (${seg.durationMinutes}м)",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isCurr) Color.White else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Scrollable Vertical Linear Stops List with continuous animated live GPS line
                            val stopsList = liveProgress.stops
                            val trackIdleColor = MaterialTheme.colorScheme.outlineVariant
                            val nodeBgColor = MaterialTheme.colorScheme.surface
                            val intervalCount = (stopsList.size - 1).coerceAtLeast(1)
                            val scaledRiderPos = animatedProgress.coerceIn(0f, 1f) * intervalCount

                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                            ) {
                                itemsIndexed(stopsList, key = { idx, st -> "${st.id}-$idx" }) { idx, stop ->
                                    val isTerminal = idx == 0 || idx == stopsList.lastIndex
                                    val isPassed = idx < liveProgress.currentStopIndex
                                    val isCurrent = idx == liveProgress.currentStopIndex
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Canvas(
                                            modifier = Modifier
                                                .width(28.dp)
                                                .fillMaxHeight()
                                        ) {
                                            val cx = size.width / 2f
                                            val cy = size.height / 2f
                                            val strokeW = 5.dp.toPx()

                                            // Upper half connecting to previous stop
                                            if (idx > 0) {
                                                drawLine(
                                                    color = trackIdleColor,
                                                    start = Offset(cx, 0f),
                                                    end = Offset(cx, cy),
                                                    strokeWidth = strokeW
                                                )
                                                if (scaledRiderPos > idx - 0.5f) {
                                                    val fillEndY = if (scaledRiderPos >= idx.toFloat()) cy else ((scaledRiderPos - (idx - 0.5f)) * 2f) * cy
                                                    drawLine(
                                                        color = accentColor,
                                                        start = Offset(cx, 0f),
                                                        end = Offset(cx, fillEndY.coerceIn(0f, cy)),
                                                        strokeWidth = strokeW
                                                    )
                                                }
                                            }

                                            // Lower half connecting to next stop
                                            if (idx < stopsList.lastIndex) {
                                                drawLine(
                                                    color = trackIdleColor,
                                                    start = Offset(cx, cy),
                                                    end = Offset(cx, size.height),
                                                    strokeWidth = strokeW
                                                )
                                                if (scaledRiderPos > idx.toFloat()) {
                                                    val fillBotFrac = ((scaledRiderPos - idx.toFloat()) / 0.5f).coerceIn(0f, 1f)
                                                    val fillEndY = cy + fillBotFrac * (size.height - cy)
                                                    drawLine(
                                                        color = accentColor,
                                                        start = Offset(cx, cy),
                                                        end = Offset(cx, fillEndY),
                                                        strokeWidth = strokeW
                                                    )
                                                }
                                            }

                                            // Stop circle
                                            val r = if (isTerminal) 5.5.dp.toPx() else 4.2.dp.toPx()
                                            drawCircle(
                                                color = if (isPassed || isTerminal) accentColor else nodeBgColor,
                                                radius = r,
                                                center = Offset(cx, cy)
                                            )
                                            drawCircle(
                                                color = accentColor,
                                                radius = r,
                                                center = Offset(cx, cy),
                                                style = Stroke(width = 1.8.dp.toPx())
                                            )

                                            // Smooth live rider GPS marker inside current interval
                                            val deltaFromCenter = scaledRiderPos - idx.toFloat()
                                            val showRiderInRow = if (idx == stopsList.lastIndex) {
                                                scaledRiderPos >= stopsList.lastIndex - 0.5f
                                            } else {
                                                deltaFromCenter >= -0.5f && deltaFromCenter < 0.5f
                                            }
                                            if (showRiderInRow) {
                                                val markerY = (cy + deltaFromCenter.coerceIn(-0.5f, 0.5f) * size.height)
                                                    .coerceIn(6.dp.toPx(), size.height - 6.dp.toPx())
                                                drawCircle(
                                                    color = accentColor.copy(alpha = 0.28f),
                                                    radius = 10.dp.toPx(),
                                                    center = Offset(cx, markerY)
                                                )
                                                drawCircle(
                                                    color = Color.White,
                                                    radius = 6.5.dp.toPx(),
                                                    center = Offset(cx, markerY)
                                                )
                                                drawCircle(
                                                    color = accentColor,
                                                    radius = 4.6.dp.toPx(),
                                                    center = Offset(cx, markerY)
                                                )
                                            }
                                        }

                                        Text(
                                            text = stop.name,
                                            fontSize = if (isTerminal) 14.sp else 13.sp,
                                            fontWeight = when {
                                                isTerminal -> FontWeight.ExtraBold
                                                isCurrent -> FontWeight.Bold
                                                else -> FontWeight.Normal
                                            },
                                            color = if (isPassed && idx > 0) {
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(start = 6.dp)
                                        )

                                        if (isCurrent && idx < stopsList.lastIndex) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = accentColor.copy(alpha = 0.16f)
                                            ) {
                                                Text(
                                                    text = if (liveProgress.isGpsLocked) "Вы здесь" else "Сейчас",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = accentColor,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                if (showPhotoPrompt || primaryGuide != null) {
                                    item(key = "extras") {
                                        Column(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (showPhotoPrompt) {
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = Color(0xFFF59E0B).copy(alpha = 0.16f)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.size(6.dp))
                                                        Text("Время фото-чекина!", fontSize = 11.sp, modifier = Modifier.weight(1f))
                                                        TextButton(onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                                                            Text("Снять", fontSize = 11.sp)
                                                        }
                                                    }
                                                }
                                            }
                                            if (primaryGuide != null) {
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = MaterialTheme.colorScheme.surface,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            runCatching {
                                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(primaryGuide.guide.url)))
                                                            }
                                                        }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Icon(Icons.Rounded.Headphones, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                                                        Text(
                                                            text = primaryGuide.guide.title,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Sticky Bottom Location Button inside Vertical Linear Route card
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val locBtnLabel = when {
                                    liveProgress.isGpsLocked -> {
                                        val sName = liveProgress.stops.getOrNull(liveProgress.currentStopIndex)?.name?.substringBefore("(")?.trim().orEmpty()
                                        "GPS на линии · у «$sName»"
                                    }
                                    liveProgress.distanceToRouteMeters != null -> {
                                        val km = (liveProgress.distanceToRouteMeters / 100.0).roundToInt() / 10.0
                                        "Где я на линии · до линии $km км"
                                    }
                                    else -> "Где я на линии · показать GPS"
                                }
                                FilledTonalButton(
                                    onClick = {
                                        if (!hasLocationPermission) {
                                            locationPermissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                                )
                                            )
                                        }
                                        scope.launch {
                                            listState.animateScrollToItem(liveProgress.currentStopIndex.coerceIn(0, (stopsList.size - 1).coerceAtLeast(0)))
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text(locBtnLabel, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                OutlinedButton(
                                    onClick = {
                                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    if (photos.isNotEmpty()) {
                                        Spacer(modifier = Modifier.size(4.dp))
                                        Text("${photos.size}", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Bottom action controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = { running = !running },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (running) "Пауза" else "Старт",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (!isLastSeg) {
                            OutlinedButton(
                                onClick = { onComplete(photos) },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Завершить", fontSize = 12.sp)
                            }
                            Button(
                                onClick = { segIdx += 1 },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Следующий шаг", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = { onComplete(photos) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("Завершить поездку", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentModeIconSmall(seg: RouteSegment, tint: Color, sizeDp: Int = 15) {
    val icon = when {
        seg.kind == SegmentKind.WALK -> Icons.AutoMirrored.Rounded.DirectionsWalk
        seg.kind == SegmentKind.FALLBACK_TRANSIT || seg.route?.type == "bus" -> Icons.Rounded.DirectionsBus
        seg.route?.type == "metro" -> Icons.Rounded.DirectionsSubway
        else -> Icons.Rounded.Tram
    }
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(sizeDp.dp))
}

private fun playTramBell() {
    runCatching {
        val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75)
        tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 320)
    }
}

fun copyPhotoToInternalStorage(context: Context, uri: Uri): String? {
    return runCatching {
        val dir = File(context.filesDir, "trip_photos").apply { mkdirs() }
        val file = File(dir, "tram_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        file.absolutePath
    }.getOrNull()
}
