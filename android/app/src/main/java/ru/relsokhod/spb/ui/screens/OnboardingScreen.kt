package ru.relsokhod.spb.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Tram
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.relsokhod.spb.model.CityConfig
import ru.relsokhod.spb.model.CityDetectionResult
import ru.relsokhod.spb.model.CityId

@Composable
fun OnboardingScreen(
    selectedCity: CityId,
    cities: Map<CityId, CityConfig>,
    geoStatusMessage: String?,
    cityDetection: CityDetectionResult?,
    onSelectCity: (CityId) -> Unit,
    onRequestLocation: () -> Unit,
    onComplete: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    val mismatch = cityDetection != null && cityDetection.detected != selectedCity

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Tram,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "Рельсоход",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(3) { idx ->
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .size(width = if (step == idx) 24.dp else 8.dp, height = 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (step == idx) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                    }
                }
            }
        },
        bottomBar = {
            Column {
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (step > 0) {
                        OutlinedButton(onClick = { step -= 1 }) { Text("Назад") }
                    } else {
                        TextButton(onClick = onComplete) { Text("Пропустить") }
                    }

                    if (step < 2) {
                        Button(onClick = { step += 1 }) { Text("Далее") }
                    } else {
                        Button(onClick = onComplete) {
                            Icon(Icons.Rounded.Explore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Начать (${cities[selectedCity]?.shortName ?: "СПб"})")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (step) {
                0 -> {
                    Text(
                        text = "Трамвайное путешествие в один клик",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Нативное приложение без WebView: стройте случайные и точные трамвайные цепочки с пересадками по реальному графику.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OnboardingFeatureCard(
                        icon = Icons.Rounded.Tram,
                        title = "Актуальная сеть и расписание",
                        body = "Реальные маршруты СПб и Омска, круговые линии, «Чижики» и интервалы движения."
                    )
                    OnboardingFeatureCard(
                        icon = Icons.Rounded.Navigation,
                        title = "Ведение в Яндекс Картах и автоинформатор",
                        body = "Открытие готовой цепочки в Яндекс Картах, озвучка остановок и живой режим «В пути»."
                    )
                    OnboardingFeatureCard(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "Паспорт, фото-история и точки перекуса",
                        body = "Собирайте штампы, прикрепляйте снимки поездки и находите «Пятёрочки», «Перекрёстки» и кофейни."
                    )
                }

                1 -> {
                    Text(
                        text = "Выберите ваш город",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Доступны Санкт-Петербург и Омск. Поменять город позже можно в Настройках.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    listOf(CityId.SPB, CityId.OMSK).forEach { id ->
                        val cfg = cities[id] ?: return@forEach
                        val active = selectedCity == id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .clickable { onSelectCity(id) },
                            shape = RoundedCornerShape(22.dp),
                            color = if (active) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (active) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Text(cfg.name, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(cfg.tagline, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "${cfg.routes.size} линий · ${cfg.stops.size} остановок",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (active) {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                                }
                            }
                        }
                    }
                }

                else -> {
                    Text(
                        text = "Разрешить доступ к местоположению?",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Геолокация автоматически найдёт ближайшую трамвайную остановку в ${cities[selectedCity]?.genitiveName ?: "выбранном городе"} и предупредит, если вы находитесь в другом городе.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FilledTonalButton(
                        onClick = onRequestLocation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(Icons.Rounded.MyLocation, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Разрешить и определить город", fontWeight = FontWeight.Bold)
                    }

                    if (!geoStatusMessage.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = geoStatusMessage,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }

                    if (mismatch) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFF59E0B).copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.WarningAmber,
                                        contentDescription = null,
                                        tint = Color(0xFFD97706)
                                    )
                                    Text("Обнаружен другой город", fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (cityDetection.detected == null) {
                                        "Вы сейчас за пределами СПб и Омска — приложение продолжит работу для выбранного города (${cities[selectedCity]?.name})."
                                    } else {
                                        "По координатам вы находитесь в городе ${cities[cityDetection.detected]?.name}, а выбран ${cities[selectedCity]?.name}."
                                    },
                                    fontSize = 13.sp
                                )
                                if (cityDetection.detected != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(onClick = { onSelectCity(cityDetection.detected) }) {
                                        Text("Переключиться на ${cities[cityDetection.detected]?.name}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingFeatureCard(
    icon: ImageVector,
    title: String,
    body: String
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(body, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
