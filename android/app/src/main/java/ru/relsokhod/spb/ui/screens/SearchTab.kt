package ru.relsokhod.spb.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.relsokhod.spb.model.CityConfig
import ru.relsokhod.spb.model.CityId
import ru.relsokhod.spb.model.DurationPreset
import ru.relsokhod.spb.model.RouteThemeFilter
import ru.relsokhod.spb.model.SearchParamsState
import ru.relsokhod.spb.model.TramStop
import ru.relsokhod.spb.model.WeatherMode
import ru.relsokhod.spb.router.TramRouter
import ru.relsokhod.spb.ui.components.ModeTriangleSlider

private val TIME_SLOTS = listOf(
    15, 3 * 60, 6 * 60 + 30, 8 * 60, 9 * 60, 10 * 60, 11 * 60,
    12 * 60, 13 * 60, 14 * 60, 15 * 60, 16 * 60, 17 * 60, 18 * 60,
    19 * 60, 20 * 60, 21 * 60, 22 * 60, 23 * 60
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchTab(
    cityConfig: CityConfig,
    params: SearchParamsState,
    scrollState: ScrollState,
    geoStatusMessage: String?,
    isGenerating: Boolean = false,
    onUpdateParams: (SearchParamsState) -> Unit,
    onDetectLocation: () -> Unit,
    onPickOnMap: (String) -> Unit,
    onGenerate: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val tramStops = remember(cityConfig.stops) { cityConfig.stops.filter { !it.isolatedFromMainTram } }
    val destStops = remember(cityConfig.stops, params.startStopId) {
        cityConfig.stops.filter { it.id != params.startStopId }
    }
    val stopMap = remember(cityConfig.stops) { cityConfig.stops.associateBy { it.id } }

    var startPickerOpen by remember { mutableStateOf(false) }
    var destPickerOpen by remember { mutableStateOf(false) }
    var dayMenuExpanded by remember { mutableStateOf(false) }
    var timeMenuExpanded by remember { mutableStateOf(false) }
    var addressNotFound by remember { mutableStateOf(false) }

    val startStop = stopMap[params.startStopId] ?: tramStops.firstOrNull()
    val destStop = params.destinationStopId?.let { stopMap[it] }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Куда поехать на трамвае",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold
        )

        // Start & Destination card
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Отправление", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = onDetectLocation) {
                            Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("Рядом", fontSize = 12.sp)
                        }
                        TextButton(onClick = { onPickOnMap("start") }) {
                            Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("На схеме", fontSize = 12.sp)
                        }
                    }
                }

                OutlinedButton(
                    onClick = { startPickerOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = startStop?.let { "${it.name} — ${it.district}" } ?: "Выбрать остановку",
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        fontSize = 13.sp
                    )
                    Icon(Icons.Rounded.ExpandMore, contentDescription = null)
                }

                if (!geoStatusMessage.isNullOrBlank()) {
                    Text(
                        text = geoStatusMessage,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                HorizontalDivider()

                // Destination address + stop picker + reset to random
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Конечный адрес или остановка", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (params.destinationStopId != null || params.destinationAddressLabel.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    addressNotFound = false
                                    onUpdateParams(
                                        params.copy(
                                            destinationStopId = null,
                                            destinationAddressQuery = "",
                                            destinationAddressLabel = ""
                                        )
                                    )
                                }
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.size(2.dp))
                                Text("Случайный", fontSize = 12.sp)
                            }
                        }
                        TextButton(onClick = { onPickOnMap("destination") }) {
                            Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("На схеме", fontSize = 12.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = params.destinationAddressQuery,
                        onValueChange = {
                            addressNotFound = false
                            onUpdateParams(params.copy(destinationAddressQuery = it))
                        },
                        placeholder = {
                            Text(
                                text = if (cityConfig.id == CityId.OMSK) "Например: Цирк, Вокзал..."
                                else "Например: Сенная, Чижик...",
                                fontSize = 12.sp
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    FilledTonalButton(
                        onClick = {
                            val q = params.destinationAddressQuery.trim().lowercase()
                            if (q.isEmpty()) return@FilledTonalButton
                            val stopMatch = cityConfig.stops.firstOrNull {
                                it.id != params.startStopId &&
                                    (it.name.lowercase().contains(q) || it.district.lowercase().contains(q))
                            }
                            val lmMatch = if (stopMatch == null) {
                                cityConfig.landmarks.firstOrNull {
                                    it.name.lowercase().contains(q) || it.description.lowercase().contains(q)
                                }
                            } else null
                            val resolved: TramStop? = stopMatch ?: lmMatch?.let { lm ->
                                TramRouter.findNearestStops(lm.lat, lm.lng, cityConfig.stops, 1, true).firstOrNull()?.first
                            }
                            if (resolved != null) {
                                addressNotFound = false
                                onUpdateParams(
                                    params.copy(
                                        destinationStopId = resolved.id,
                                        destinationAddressLabel = resolved.name,
                                        loopMode = false
                                    )
                                )
                            } else {
                                addressNotFound = true
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = "Найти")
                    }
                }

                if (addressNotFound) {
                    Text(
                        text = "Точка не найдена — выберите остановку из списка ниже.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                OutlinedButton(
                    onClick = { destPickerOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = destStop?.let { "${it.name} — ${it.district}" } ?: "Случайная точка в городе",
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        fontSize = 13.sp
                    )
                    Icon(Icons.Rounded.ExpandMore, contentDescription = null)
                }
            }
        }

        // Mood + Weather + Duration + Schedule card
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModeTriangleSlider(
                    value = params.modeBalance,
                    onChange = { onUpdateParams(params.copy(modeBalance = it)) }
                )

                HorizontalDivider()

                Text("Настроение маршрута", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RouteThemeFilter.entries.forEach { tf ->
                        val label = when {
                            cityConfig.id == CityId.OMSK && tf == RouteThemeFilter.CHIZHIK -> "Рабочие окраины"
                            cityConfig.id == CityId.OMSK && tf == RouteThemeFilter.MARITIME -> "Левобережье и вокзал"
                            else -> tf.label
                        }
                        FilterChip(
                            selected = params.themeFilter == tf,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onUpdateParams(params.copy(themeFilter = tf))
                            },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                HorizontalDivider()

                Text("Погода и пересадки", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WeatherMode.entries.forEach { wm ->
                        val icon = when (wm) {
                            WeatherMode.CLEAR -> Icons.Rounded.WbSunny
                            WeatherMode.RAIN -> Icons.Rounded.CloudQueue
                            WeatherMode.FROST -> Icons.Rounded.AcUnit
                        }
                        FilterChip(
                            selected = params.weatherMode == wm,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onUpdateParams(params.copy(weatherMode = wm))
                            },
                            leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text(wm.label, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                HorizontalDivider()

                Text("Время в пути", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DurationPreset.entries.forEach { dp ->
                        FilterChip(
                            selected = params.durationPreset == dp,
                            onClick = { onUpdateParams(params.copy(durationPreset = dp)) },
                            label = { Text(dp.label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text("График (день и время)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { dayMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(TramRouter.DAY_NAMES_SHORT[params.dayOfWeek], modifier = Modifier.weight(1f))
                            Icon(Icons.Rounded.ExpandMore, contentDescription = null)
                        }
                        DropdownMenu(expanded = dayMenuExpanded, onDismissRequest = { dayMenuExpanded = false }) {
                            TramRouter.DAY_NAMES_SHORT.forEachIndexed { idx, dName ->
                                DropdownMenuItem(
                                    text = { Text(dName) },
                                    onClick = {
                                        dayMenuExpanded = false
                                        onUpdateParams(params.copy(dayOfWeek = idx))
                                    }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { timeMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(TramRouter.formatMinutesToClock(params.timeMinutes), modifier = Modifier.weight(1f))
                            Icon(Icons.Rounded.ExpandMore, contentDescription = null)
                        }
                        DropdownMenu(expanded = timeMenuExpanded, onDismissRequest = { timeMenuExpanded = false }) {
                            TIME_SLOTS.forEach { slot ->
                                DropdownMenuItem(
                                    text = { Text(TramRouter.formatMinutesToClock(slot)) },
                                    onClick = {
                                        timeMenuExpanded = false
                                        onUpdateParams(params.copy(timeMinutes = slot))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Toggles card (NO Material You Accent Picker here — lives in Settings!)
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                ToggleRow(
                    checked = params.loopMode,
                    onCheckedChange = { checked ->
                        onUpdateParams(
                            params.copy(
                                loopMode = checked,
                                destinationStopId = if (checked) null else params.destinationStopId
                            )
                        )
                    },
                    title = "Круговая прогулка (старт = финиш)",
                    subtitle = "Кольцевой маршрут вернёт вас на исходную остановку другим путём."
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                ToggleRow(
                    checked = params.preferSpecialFleet,
                    onCheckedChange = { onUpdateParams(params.copy(preferSpecialFleet = it)) },
                    title = "Редкий борт",
                    subtitle = "Приоритет ретро-вагонам, кольцевым маршрутам и «Чижикам»."
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                ToggleRow(
                    checked = params.allowFallbackTransit,
                    onCheckedChange = { onUpdateParams(params.copy(allowFallbackTransit = it)) },
                    title = "Резервный транспорт при необходимости",
                    subtitle = "Подключится только там, где нет трамвая и пешком далеко."
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                ToggleRow(
                    checked = params.showAllNetwork,
                    onCheckedChange = { onUpdateParams(params.copy(showAllNetwork = it)) },
                    title = "Показывать всю сеть трамваев ${cityConfig.shortName} на схеме",
                    subtitle = null
                )
            }
        }

        Button(
            enabled = !isGenerating,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onGenerate()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            if (isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(
                    if (params.loopMode) Icons.Rounded.Repeat else Icons.Rounded.Shuffle,
                    contentDescription = null
                )
            }
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = when {
                    isGenerating -> "Подбираем маршрут..."
                    params.loopMode -> "Построить круговой маршрут"
                    params.destinationStopId != null -> "Построить трамвайный маршрут"
                    else -> "Подобрать случайный маршрут"
                },
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }

    if (startPickerOpen) {
        StopPickerDialog(
            title = "Остановка отправления",
            stops = tramStops,
            allowRandom = false,
            onSelectRandom = {},
            onSelectStop = { stop ->
                startPickerOpen = false
                onUpdateParams(params.copy(startStopId = stop.id))
            },
            onDismiss = { startPickerOpen = false }
        )
    }

    if (destPickerOpen) {
        StopPickerDialog(
            title = "Конечная остановка",
            stops = destStops,
            allowRandom = true,
            onSelectRandom = {
                destPickerOpen = false
                onUpdateParams(
                    params.copy(
                        destinationStopId = null,
                        destinationAddressLabel = "",
                        destinationAddressQuery = ""
                    )
                )
            },
            onSelectStop = { stop ->
                destPickerOpen = false
                onUpdateParams(
                    params.copy(
                        destinationStopId = stop.id,
                        destinationAddressLabel = stop.name,
                        loopMode = false
                    )
                )
            },
            onDismiss = { destPickerOpen = false }
        )
    }
}

@Composable
private fun StopPickerDialog(
    title: String,
    stops: List<TramStop>,
    allowRandom: Boolean,
    onSelectRandom: () -> Unit,
    onSelectStop: (TramStop) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(stops, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) stops
        else stops.filter { it.name.lowercase().contains(q) || it.district.lowercase().contains(q) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Поиск по остановке или району...", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                ) {
                    if (allowRandom) {
                        item(key = "__random__") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectRandom() }
                                    .padding(vertical = 10.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Случайная точка в городе",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 13.sp
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                    items(filtered, key = { it.id }) { stop ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectStop(stop) }
                                .padding(vertical = 10.dp, horizontal = 6.dp)
                        ) {
                            Text(
                                text = if (stop.isolatedFromMainTram) "${stop.name} (резерв. транспорт)" else stop.name,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = stop.district,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

@Composable
private fun ToggleRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    subtitle: String?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
