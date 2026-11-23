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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import ru.relsokhod.spb.BuildConfig
import ru.relsokhod.spb.model.AccentSeed
import ru.relsokhod.spb.model.CityConfig
import ru.relsokhod.spb.model.CityId
import ru.relsokhod.spb.model.ThemeMode
import ru.relsokhod.spb.storage.BackupCodec
import ru.relsokhod.spb.update.AppUpdateLogic
import ru.relsokhod.spb.update.AvailableRelease
import ru.relsokhod.spb.update.SignatureCompatibility
import ru.relsokhod.spb.update.UpdateCheckResult
import ru.relsokhod.spb.update.UpdateDownloadState
import java.io.File

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
    isCheckingUpdates: Boolean,
    updateCheckResult: UpdateCheckResult?,
    updateDownloadState: UpdateDownloadState,
    onCheckUpdates: () -> Unit,
    onStartUpdateDownload: (AvailableRelease) -> Unit,
    onInstallUpdateApk: (File) -> Unit,
    onExportAndUninstall: (File, AvailableRelease) -> Unit,
    onOpenReleaseUrl: (String) -> Unit,
    onShowWhatsNew: () -> Unit,
    backupFeedback: String?,
    backupFeedbackIsError: Boolean,
    backupSavedForUpdate: Boolean = false,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
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

            // In-app updates from GitHub Releases
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Обновления приложения", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                val buildSuffix = if (BuildConfig.CI_RUN_NUMBER > 0) {
                    " · сборка #${BuildConfig.CI_RUN_NUMBER}"
                } else {
                    ""
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Версия: ${BuildConfig.VERSION_NAME}$buildSuffix",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            onDismiss()
                            onShowWhatsNew()
                        }
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("Что нового", fontSize = 11.sp)
                    }
                }

                when {
                    isCheckingUpdates -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Проверяем релизы на GitHub...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    updateCheckResult is UpdateCheckResult.UpToDate -> {
                        Text(
                            text = updateCheckResult.latestTag?.let {
                                "Установлена актуальная версия (последний релиз: $it)."
                            } ?: "Установлена актуальная версия.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    updateCheckResult is UpdateCheckResult.Error -> {
                        Text(
                            text = updateCheckResult.message,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    updateCheckResult is UpdateCheckResult.UpdateAvailable -> {
                        val rel = updateCheckResult.release
                        val sizeLabel = AppUpdateLogic.formatFileSize(rel.apkAsset.sizeBytes)
                        val blocks = AppUpdateLogic.parseChangelogBlocks(rel.notesSummary)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Доступна версия ${rel.tagName}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (sizeLabel.isNotEmpty()) {
                                    Text(
                                        text = "${rel.apkAsset.name} · $sizeLabel",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                                blocks.forEach { block ->
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = block.title,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        block.items.forEach { item ->
                                            Text(
                                                text = "• $item",
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.92f)
                                            )
                                        }
                                    }
                                }

                                when (updateDownloadState) {
                                    is UpdateDownloadState.Idle -> {
                                        Button(
                                            onClick = { onStartUpdateDownload(rel) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.size(6.dp))
                                            Text("Скачать и установить APK", fontSize = 12.sp)
                                        }
                                    }

                                    is UpdateDownloadState.Downloading -> {
                                        val progressVal = updateDownloadState.progress
                                        val pct = progressVal?.let { "${(it * 100).toInt()}%" } ?: "..."
                                        if (progressVal != null) {
                                            LinearProgressIndicator(
                                                progress = { progressVal },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        } else {
                                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                        }
                                        Text(
                                            text = "Загрузка APK: $pct",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }

                                    is UpdateDownloadState.ReadyToInstall -> {
                                        val compat = updateDownloadState.signatureCompatibility
                                        if (compat == SignatureCompatibility.DIFFERENT_SIGNATURE ||
                                            compat == SignatureCompatibility.PACKAGE_MISMATCH ||
                                            compat == SignatureCompatibility.DOWNGRADE_CONFLICT
                                        ) {
                                            val conflictHint = when (compat) {
                                                SignatureCompatibility.PACKAGE_MISMATCH ->
                                                    "Скачанный APK относится к другому варианту пакета (release/debug). Сначала сохраните копию .relsohod, затем выгрузите APK в Загрузки и удалите текущую сборку."
                                                SignatureCompatibility.DOWNGRADE_CONFLICT ->
                                                    "Установленная сборка имеет более высокий build-номер. Сохраните копию .relsohod, выгрузите APK в Загрузки и переустановите один раз."
                                                else ->
                                                    "Старая сборка подписана другим ключом — прямая установка выдаст «Обновление конфликтует с другим пакетом». Сначала сохраните копию .relsohod, выгрузите APK в Загрузки и удалите старую версию один раз; дальнейшие обновления будут ставиться поверх."
                                            }
                                            Text(
                                                text = conflictHint,
                                                fontSize = 11.sp,
                                                color = Color(0xFFB45309)
                                            )
                                            if (backupSavedForUpdate) {
                                                OutlinedButton(
                                                    onClick = onExportBackup,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                                                    Spacer(modifier = Modifier.size(6.dp))
                                                    Text("Бэкап .relsohod сохранён", fontSize = 12.sp)
                                                }
                                            } else {
                                                FilledTonalButton(
                                                    onClick = onExportBackup,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                                                    Spacer(modifier = Modifier.size(6.dp))
                                                    Text("Сделать бэкап (.relsohod)", fontSize = 12.sp)
                                                }
                                            }
                                            Button(
                                                onClick = { onExportAndUninstall(updateDownloadState.apkFile, rel) },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Сохранить в Загрузки и удалить старую", fontSize = 12.sp)
                                            }
                                        } else {
                                            Button(
                                                onClick = { onInstallUpdateApk(updateDownloadState.apkFile) },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.size(6.dp))
                                                Text("Установить скачанный APK", fontSize = 12.sp)
                                            }
                                        }
                                    }

                                    is UpdateDownloadState.Failed -> {
                                        Text(
                                            text = updateDownloadState.message,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Button(
                                            onClick = { onStartUpdateDownload(rel) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Повторить загрузку", fontSize = 12.sp)
                                        }
                                    }
                                }

                                OutlinedButton(
                                    onClick = { onOpenReleaseUrl(rel.htmlUrl) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text("Открыть релиз на GitHub", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    else -> Unit
                }

                OutlinedButton(
                    enabled = !isCheckingUpdates && updateDownloadState !is UpdateDownloadState.Downloading,
                    onClick = onCheckUpdates,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Проверить обновления")
                }
            }

            HorizontalDivider()

            // Backup Export / Import (.relsohod)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Резервная копия и перенос (.relsohod)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Text(
                    text = "Единый формат для Android и браузера: сохраняет избранное, паспорт с фото и настройки в ${BackupCodec.buildBackupFilename()}.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onExportBackup,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Экспорт", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onImportBackup,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Восстановить", fontSize = 12.sp)
                    }
                }
                if (!backupFeedback.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (backupFeedbackIsError) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = backupFeedback,
                            fontSize = 11.sp,
                            color = if (backupFeedbackIsError) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.padding(10.dp)
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
