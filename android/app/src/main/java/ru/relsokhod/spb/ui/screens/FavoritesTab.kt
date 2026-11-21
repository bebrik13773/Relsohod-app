package ru.relsokhod.spb.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.relsokhod.spb.model.CityConfig
import ru.relsokhod.spb.model.CityId
import ru.relsokhod.spb.model.GeneratedTrip
import ru.relsokhod.spb.model.SavedRoute
import ru.relsokhod.spb.model.TripHistoryEntry
import ru.relsokhod.spb.model.TripPhotoMemory
import ru.relsokhod.spb.router.TramRouter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class FavoritesSubTab(val label: String) {
    SAVED("Сохранённые"),
    PASSPORT("Паспорт и фото")
}

private fun decodeSampledBitmap(path: String, reqWidth: Int = 256, reqHeight: Int = 256): Bitmap? {
    val file = File(path)
    if (!file.exists()) return null
    return runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        var inSampleSize = 1
        while (opts.outHeight / inSampleSize > reqHeight * 2 || opts.outWidth / inSampleSize > reqWidth * 2) {
            inSampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
        BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
    }.getOrNull()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FavoritesTab(
    allCities: Map<CityId, CityConfig>,
    savedRoutes: List<SavedRoute>,
    history: List<TripHistoryEntry>,
    onOpenTrip: (GeneratedTrip) -> Unit,
    onDeleteSaved: (String) -> Unit,
    onAddPhotoToHistory: (String, TripPhotoMemory) -> Unit,
    onDeleteHistory: (String) -> Unit
) {
    val stopsById = remember(allCities) {
        allCities.values.flatMap { it.stops }.associateBy { it.id }
    }
    val spbDistrictsTotal = remember(allCities) {
        allCities[CityId.SPB]?.stops?.map { it.district }?.toSet()?.size ?: 15
    }
    val summaryStats = remember(savedRoutes, stopsById, spbDistrictsTotal) {
        TramRouter.computeSavedRoutesStats(savedRoutes, stopsById, spbDistrictsTotal)
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var subTab by remember { mutableStateOf(FavoritesSubTab.SAVED) }
    var pendingEntryId by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val entryId = pendingEntryId ?: return@rememberLauncherForActivityResult
        pendingEntryId = null
        if (uri != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) { copyPhotoToInternalStorage(context, uri) }
                if (path != null) {
                    val isoNow = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
                    onAddPhotoToHistory(
                        entryId,
                        TripPhotoMemory(
                            id = "hist-${System.currentTimeMillis()}",
                            filePath = path,
                            caption = "Фото из поездки",
                            takenAtIso = isoNow
                        )
                    )
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            FavoritesSubTab.entries.forEachIndexed { idx, tab ->
                val count = if (tab == FavoritesSubTab.SAVED) savedRoutes.size else history.size
                SegmentedButton(
                    selected = subTab == tab,
                    onClick = { subTab = tab },
                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = FavoritesSubTab.entries.size)
                ) {
                    Text("${tab.label} ($count)", fontSize = 12.sp)
                }
            }
        }

        if (subTab == FavoritesSubTab.SAVED) {
            if (savedRoutes.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Rounded.Bookmark, contentDescription = null, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("В избранном пока пусто", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        text = "Соберите маршрут и нажмите «Сохранить».",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val coverage = if (summaryStats.spbDistrictsTotal > 0) {
                    (summaryStats.spbDistrictsCount.toFloat() / summaryStats.spbDistrictsTotal).coerceIn(0f, 1f)
                } else 0f

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "__summary__") {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "Пройденная дистанция",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = String.format(Locale.US, "%.1f км", summaryStats.totalDistanceKm),
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = "Маршрутов: ${summaryStats.totalRoutesCount} · ~${summaryStats.totalDurationMinutes} мин",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "Районы СПб",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "${summaryStats.spbDistrictsCount} из ${summaryStats.spbDistrictsTotal}",
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            LinearProgressIndicator(
                                                progress = { coverage },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(6.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                            )
                                        }
                                    }
                                }

                                if (summaryStats.spbDistrictNames.isNotEmpty()) {
                                    Text(
                                        text = "Районы СПб: ${summaryStats.spbDistrictNames.joinToString(", ")}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    items(savedRoutes, key = { it.id }) { saved ->
                        val tripDistKm = remember(saved.trip.id, stopsById) {
                            TramRouter.calculateTripDistanceKm(saved.trip, stopsById)
                        }
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(saved.customTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { onDeleteSaved(saved.id) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Удалить")
                                    }
                                }
                                Text(
                                    text = "~${saved.trip.totalDurationMinutes} мин · ${String.format(Locale.US, "%.1f", tripDistKm)} км · Трамваи: ${saved.trip.tramLinesUsed.joinToString(" → ")}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                FilledTonalButton(onClick = { onOpenTrip(saved.trip) }) {
                                    Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text("Открыть маршрут", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(12.dp)) }
                }
            }
        } else {
            val badges = remember(history, savedRoutes) {
                TramRouter.evaluatePassportBadges(history, savedRoutes)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "__badges__") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Паспорт рельсохода", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            badges.forEach { badge ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (badge.unlocked) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.fillMaxWidth(0.48f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(badge.icon, fontSize = 20.sp)
                                            Text(badge.progressText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(badge.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(badge.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("История поездок с фото (${history.size})", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    }
                }

                if (history.isEmpty()) {
                    item(key = "__empty_history__") {
                        Text(
                            text = "Запустите режим «В пути» на маршруте и нажмите «Завершить» — поездки и фото появятся здесь.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(history, key = { it.id }) { entry ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${entry.trip.startStop.name.substringBefore("(").trim()} → ${entry.trip.endStop.name.substringBefore("(").trim()}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "${entry.trip.tramLinesUsed.joinToString(" → ")} · ~${entry.trip.totalDurationMinutes} мин",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { onDeleteHistory(entry.id) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Удалить")
                                    }
                                }

                                if (entry.photos.isNotEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        entry.photos.take(3).forEach { p ->
                                            val bmp by produceState<Bitmap?>(initialValue = null, p.filePath) {
                                                value = withContext(Dispatchers.IO) { decodeSampledBitmap(p.filePath) }
                                            }
                                            if (bmp != null) {
                                                Image(
                                                    bitmap = bmp!!.asImageBitmap(),
                                                    contentDescription = p.caption,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(84.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            pendingEntryId = entry.id
                                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        }
                                    ) {
                                        Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.size(4.dp))
                                        Text("Добавить фото", fontSize = 12.sp)
                                    }
                                    FilledTonalButton(onClick = { onOpenTrip(entry.trip) }) {
                                        Text("На схему", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(12.dp)) }
            }
        }
    }
}
