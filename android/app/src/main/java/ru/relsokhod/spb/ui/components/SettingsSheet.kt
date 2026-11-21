package ru.relsokhod.spb.ui.components

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.relsokhod.spb.model.AccentSeed
import ru.relsokhod.spb.model.CityConfig
import ru.relsokhod.spb.model.CityId
import ru.relsokhod.spb.model.ThemeMode

private val REMINDER_OPTIONS = listOf(
    0 to "Выкл",
    15 to "15 мин",
    30 to "30 мин",
    45 to "45 мин",
    60 to "1 час"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(
    city: CityId,
    cities: Map<CityId, CityConfig>,
    themeMode: ThemeMode,
    accentSeed: AccentSeed,
    photoReminderMinutes: Int,
    onChangeCity: (CityId) -> Unit,
    onChangeThemeMode: (ThemeMode) -> Unit,
    onChangeAccentSeed: (AccentSeed) -> Unit,
    onChangePhotoReminderMinutes: (Int) -> Unit,
    onRestartOnboarding: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Настройки",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )

            // City selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Rounded.LocationCity, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Город трамвайной сети", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(CityId.SPB, CityId.OMSK).forEach { id ->
                        val cfg = cities[id]
                        val selected = city == id
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onChangeCity(id)
                                },
                            shape = RoundedCornerShape(18.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = cfg?.name ?: id.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${cfg?.routes?.size ?: 0} линий",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // Theme + Material You Accent (ONLY in Settings!)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Rounded.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Тема и акцент Material You", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onChangeThemeMode(mode)
                            },
                            label = { Text(mode.label, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                val accentsAvailable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    AccentSeed.entries
                } else {
                    AccentSeed.entries.filter { it != AccentSeed.SYSTEM }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    accentsAvailable.forEach { seed ->
                        val active = accentSeed == seed && themeMode != ThemeMode.TRAM
                        FilterChip(
                            enabled = themeMode != ThemeMode.TRAM,
                            selected = active,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onChangeAccentSeed(seed)
                            },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color(seed.primaryHex))
                                )
                            },
                            label = { Text(seed.label, fontSize = 12.sp) }
                        )
                    }
                }
                if (themeMode == ThemeMode.TRAM) {
                    Text(
                        text = "Динамические акценты активны в «Светлой» и «Тёмной» темах.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            // Photo Check-in Reminder Interval
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Напоминание сделать фото в поездке", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Text(
                    text = "Во время режима «В пути» приложение напомнит сохранить кадр на память.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    REMINDER_OPTIONS.forEach { (mins, label) ->
                        FilterChip(
                            selected = photoReminderMinutes == mins,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onChangePhotoReminderMinutes(mins)
                            },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }
            }

            HorizontalDivider()

            OutlinedButton(
                onClick = {
                    onDismiss()
                    onRestartOnboarding()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.School, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text("Пройти презентацию (обучение) заново")
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}
