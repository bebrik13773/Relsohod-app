package ru.relsokhod.spb.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import ru.relsokhod.spb.model.CityConfig
import ru.relsokhod.spb.router.TramRouter

private enum class LineTypeFilter(val label: String) {
    ALL("Все виды"),
    TRAM("Трамваи"),
    METRO("Метро"),
    TROLLEYBUS("Троллейбусы")
}

private enum class LineStatusFilter(val label: String) {
    ALL("Все"),
    ACTIVE("Действующие"),
    PLANNED_2027("До лета 2027")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LinesTab(cityConfig: CityConfig) {
    val fallbackPrimary = MaterialTheme.colorScheme.primary
    var typeFilter by remember(cityConfig.id) { mutableStateOf(LineTypeFilter.ALL) }
    var statusFilter by remember(cityConfig.id) { mutableStateOf(LineStatusFilter.ALL) }

    val hasMetro = remember(cityConfig.routes) { cityConfig.routes.any { it.type == "metro" } }
    val hasTrolleybus = remember(cityConfig.routes) { cityConfig.routes.any { it.type == "trolleybus" } }
    val hasPlanned = remember(cityConfig.routes) { cityConfig.routes.any { it.planned2027 } }

    val filteredRoutes = remember(cityConfig.routes, typeFilter, statusFilter) {
        cityConfig.routes.filter { route ->
            val typeOk = when (typeFilter) {
                LineTypeFilter.ALL -> true
                LineTypeFilter.METRO -> route.type == "metro"
                LineTypeFilter.TROLLEYBUS -> route.type == "trolleybus"
                LineTypeFilter.TRAM -> route.type != "metro" && route.type != "trolleybus"
            }
            val statusOk = when (statusFilter) {
                LineStatusFilter.ALL -> true
                LineStatusFilter.ACTIVE -> !route.planned2027
                LineStatusFilter.PLANNED_2027 -> route.planned2027
            }
            typeOk && statusOk
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Справочник линий (${cityConfig.name})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Трамваи, метро, троллейбусы и пусковые участки до лета 2027 года (${filteredRoutes.size} из ${cityConfig.routes.size}).",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = typeFilter == LineTypeFilter.ALL,
                    onClick = { typeFilter = LineTypeFilter.ALL },
                    shape = RoundedCornerShape(50),
                    label = { Text(LineTypeFilter.ALL.label, fontSize = 11.sp) }
                )
                FilterChip(
                    selected = typeFilter == LineTypeFilter.TRAM,
                    onClick = { typeFilter = LineTypeFilter.TRAM },
                    shape = RoundedCornerShape(50),
                    label = { Text(LineTypeFilter.TRAM.label, fontSize = 11.sp) }
                )
                if (hasMetro) {
                    FilterChip(
                        selected = typeFilter == LineTypeFilter.METRO,
                        onClick = { typeFilter = LineTypeFilter.METRO },
                        shape = RoundedCornerShape(50),
                        label = { Text(LineTypeFilter.METRO.label, fontSize = 11.sp) }
                    )
                }
                if (hasTrolleybus) {
                    FilterChip(
                        selected = typeFilter == LineTypeFilter.TROLLEYBUS,
                        onClick = { typeFilter = LineTypeFilter.TROLLEYBUS },
                        shape = RoundedCornerShape(50),
                        label = { Text(LineTypeFilter.TROLLEYBUS.label, fontSize = 11.sp) }
                    )
                }
            }

            if (hasPlanned) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LineStatusFilter.entries.forEach { sf ->
                        FilterChip(
                            selected = statusFilter == sf,
                            onClick = { statusFilter = sf },
                            shape = RoundedCornerShape(50),
                            label = { Text(sf.label, fontSize = 11.sp) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }

        items(filteredRoutes, key = { it.id }) { route ->
            val lineColor = remember(route.id, route.colorHex, fallbackPrimary) {
                runCatching {
                    Color(android.graphics.Color.parseColor(route.colorHex))
                }.getOrDefault(fallbackPrimary)
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 52.dp, minHeight = 46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(lineColor)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = route.number,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = TramRouter.getRouteTypeShortLabel(route),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (route.planned2027) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFF59E0B).copy(alpha = 0.18f)
                                ) {
                                    Text(
                                        text = route.plannedLabel ?: "До лета 2027",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFB45309),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(route.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = "${route.schedule.label} · Интервал ~${route.schedule.intervalMinutes} мин",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = route.description,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(14.dp)) }
    }
}
