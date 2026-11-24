package ru.relsokhod.spb.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsSubway
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tram
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.relsokhod.spb.model.CityConfig
import ru.relsokhod.spb.model.GeneratedTrip
import ru.relsokhod.spb.model.GenerationOutcome
import ru.relsokhod.spb.model.LandmarkCategory
import ru.relsokhod.spb.model.SearchParamsState
import ru.relsokhod.spb.model.SegmentKind
import ru.relsokhod.spb.model.TramStop
import ru.relsokhod.spb.model.TripPhotoMemory
import ru.relsokhod.spb.router.TramRouter
import ru.relsokhod.spb.ui.components.TramMapCanvas
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private enum class RouteSubView(val label: String) {
    MAP("Схема"),
    STEPS("Шаги"),
    PLACES("Места")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RouteTab(
    cityConfig: CityConfig,
    params: SearchParamsState,
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
                TramMapCanvas(
                    city = cityConfig.id,
                    stops = visibleStops,
                    routes = visibleRoutes,
                    trip = activeTrip,
                    showAllNetwork = params.showAllNetwork,
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

    if (liveTripOpen && activeTrip != null) {
        LiveTripDialog(
            trip = activeTrip,
            announcerIntro = cityConfig.announcerIntro,
            photoReminderMinutes = photoReminderMinutes,
            onSpeakText = onSpeakText,
            onComplete = { photos ->
                liveTripOpen = false
                onCompleteTrip(activeTrip, photos)
            },
            onDismiss = { liveTripOpen = false }
        )
    }
}

@Composable
private fun LiveTripDialog(
    trip: GeneratedTrip,
    announcerIntro: String,
    photoReminderMinutes: Int,
    onSpeakText: (String) -> Unit,
    onComplete: (List<TripPhotoMemory>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var segIdx by remember { mutableIntStateOf(0) }
    val currSeg = trip.segments[segIdx]
    var secondsLeft by remember(segIdx) { mutableIntStateOf(currSeg.durationMinutes * 60) }
    var running by remember { mutableStateOf(true) }
    var photos by remember { mutableStateOf<List<TripPhotoMemory>>(emptyList()) }
    var elapsedPhotoSec by remember { mutableIntStateOf(0) }
    var showPhotoPrompt by remember { mutableStateOf(false) }

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
        while (running) {
            delay(1000L)
            if (secondsLeft > 0) {
                secondsLeft -= 1
                if (secondsLeft == 0) playTramBell()
            }
            if (photoReminderMinutes > 0) {
                elapsedPhotoSec += 1
                if (elapsedPhotoSec >= photoReminderMinutes * 60) {
                    elapsedPhotoSec = 0
                    showPhotoPrompt = true
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "В пути · Шаг ${segIdx + 1} из ${trip.segments.size}",
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = String.format(Locale.US, "%02d:%02d", secondsLeft / 60, secondsLeft % 60),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text("Выход: ${currSeg.toStop.name}", fontSize = 12.sp)
                        }
                        FilledTonalButton(onClick = { running = !running }) {
                            Icon(
                                if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = null
                            )
                        }
                    }
                }

                if (showPhotoPrompt) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = Color(0xFFD97706))
                            Text("Время фото-чекина на память!", fontSize = 12.sp, modifier = Modifier.weight(1f))
                            TextButton(
                                onClick = {
                                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                            ) { Text("Снять") }
                        }
                    }
                }

                if (primaryGuide != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${primaryGuide.matchReason} · ${primaryGuide.guide.portalName}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = primaryGuide.guide.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            FilledTonalButton(
                                onClick = {
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(primaryGuide.guide.url))
                                        context.startActivity(intent)
                                    }.onFailure {
                                        Toast.makeText(context, "Не найдено приложение для открытия портала", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.Headphones, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("Открыть официальный аудиогид", fontSize = 12.sp)
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            playTramBell()
                            val line = announcements.firstOrNull { it.first.startsWith(currSeg.id) }?.second
                                ?: announcements.firstOrNull()?.second.orEmpty()
                            onSpeakText(line)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("Озвучка (TTS)", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("Фото (${photos.size})", fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            if (segIdx < trip.segments.lastIndex) {
                Button(onClick = { segIdx += 1 }) { Text("Следующий шаг") }
            } else {
                Button(onClick = { onComplete(photos) }) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.size(4.dp))
                    Text("Завершить")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { onComplete(photos) }) { Text("В Паспорт") }
        }
    )
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
