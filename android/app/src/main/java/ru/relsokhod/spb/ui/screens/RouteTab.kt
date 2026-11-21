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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VolumeUp
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
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
    mapPickMode: String?,
    isSaved: Boolean,
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

    val activeTrip = (outcome as? GenerationOutcome.Ok)?.trip

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
                    stops = cityConfig.stops,
                    routes = cityConfig.routes,
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
                            Text("Нажмите «Подобрать маршрут» во вкладке Поиск.")
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
                                        Text("Включить резервный транспорт")
                                    }
                                }
                            }
                        }
                        is GenerationOutcome.Ok -> {
                            val trip = outcome.trip
                            val yandexUrl = remember(trip) { TramRouter.buildYandexMapsRouteUrl(trip) }
                            val announcements = remember(trip) {
                                TramRouter.buildAnnouncements(trip, cityConfig.announcerIntro)
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
                                    Text(
                                        text = "~${trip.totalDurationMinutes} мин · Пересадок: ${trip.transferCount} · Районов: ${trip.districtsVisited.size} · ${TramRouter.DAY_NAMES_SHORT[trip.dayOfWeek]}, ${TramRouter.formatMinutesToClock(trip.timeMinutes)}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

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
                                        FilledTonalButton(onClick = onExtendLastSegment) {
                                            Icon(Icons.Rounded.Casino, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.size(4.dp))
                                            Text("Докрутить +1", fontSize = 12.sp)
                                        }
                                        FilledTonalButton(onClick = onRegenerate) {
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
                                                val shareText = "Маршрут в Рельсоходе (${cityConfig.name}): ${trip.startStop.name} → ${trip.endStop.name}\nТрамваи: ${trip.tramLinesUsed.joinToString(" → ")}\nЯндекс Карты: $yandexUrl"
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

                            // Автоинформатор card
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Автоинформатор", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        FilledTonalButton(
                                            onClick = {
                                                playTramBell()
                                                onSpeakText(announcements.joinToString(". ") { it.second })
                                            }
                                        ) {
                                            Icon(Icons.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.size(4.dp))
                                            Text("Диктовка", fontSize = 12.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "«${announcements.firstOrNull()?.second.orEmpty()}»",
                                        fontSize = 12.sp,
                                        fontStyle = FontStyle.Italic
                                    )
                                }
                            }

                            // Itinerary segments
                            trip.segments.forEach { seg ->
                                val badgeColor = when (seg.kind) {
                                    SegmentKind.TRAM -> runCatching {
                                        Color(android.graphics.Color.parseColor(seg.route?.colorHex ?: "#005A8D"))
                                    }.getOrDefault(MaterialTheme.colorScheme.primary)
                                    SegmentKind.WALK -> Color(0xFF059669)
                                    SegmentKind.FALLBACK_TRANSIT -> Color(0xFFD97706)
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
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(badgeColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = when (seg.kind) {
                                                    SegmentKind.TRAM -> seg.route?.number ?: "Т"
                                                    SegmentKind.WALK -> "🚶"
                                                    SegmentKind.FALLBACK_TRANSIT -> "🚌"
                                                },
                                                color = Color.White,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 14.sp
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = when (seg.kind) {
                                                        SegmentKind.TRAM -> "Трамвай № ${seg.route?.number}"
                                                        SegmentKind.WALK ->
                                                            if (seg.durationMinutes >= 12) "Пешая прогулка"
                                                            else "Пеший переход"
                                                        SegmentKind.FALLBACK_TRANSIT -> seg.fallbackModeName ?: "Транспорт"
                                                    },
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                                Text(
                                                    text = "${seg.durationMinutes} мин",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
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
    var segIdx by remember { mutableIntStateOf(0) }
    val currSeg = trip.segments[segIdx]
    var secondsLeft by remember(segIdx) { mutableIntStateOf(currSeg.durationMinutes * 60) }
    var running by remember { mutableStateOf(true) }
    var photos by remember { mutableStateOf<List<TripPhotoMemory>>(emptyList()) }
    var elapsedPhotoSec by remember { mutableIntStateOf(0) }
    var showPhotoPrompt by remember { mutableStateOf(false) }

    val announcements = remember(trip) { TramRouter.buildAnnouncements(trip, announcerIntro) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val savedPath = copyPhotoToInternalStorage(context, uri)
            if (savedPath != null) {
                val isoNow = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
                photos = listOf(
                    TripPhotoMemory(
                        id = "photo-${System.currentTimeMillis()}",
                        filePath = savedPath,
                        caption = "${currSeg.fromStop.name.substringBefore("(").trim()} — ${currSeg.route?.let { "№ ${it.number}" } ?: "Пересадка"}",
                        takenAtIso = isoNow
                    )
                ) + photos
                showPhotoPrompt = false
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
                            TextButton(onClick = { imagePicker.launch("image/*") }) { Text("Снять") }
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
                        Icon(Icons.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("Автоинформатор", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = { imagePicker.launch("image/*") },
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
