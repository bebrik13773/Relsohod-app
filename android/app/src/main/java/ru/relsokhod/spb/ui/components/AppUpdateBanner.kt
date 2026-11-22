package ru.relsokhod.spb.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.relsokhod.spb.update.AppUpdateLogic
import ru.relsokhod.spb.update.AvailableRelease
import ru.relsokhod.spb.update.UpdateDownloadState
import java.io.File

@Composable
fun AppUpdateBanner(
    release: AvailableRelease,
    downloadState: UpdateDownloadState,
    onStartDownload: () -> Unit,
    onInstallApk: (File) -> Unit,
    onOpenInBrowser: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sizeLabel = AppUpdateLogic.formatFileSize(release.apkAsset.sizeBytes)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (downloadState is UpdateDownloadState.ReadyToInstall) {
                        Icons.Rounded.CheckCircle
                    } else {
                        Icons.Rounded.SystemUpdate
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Доступно обновление · ${release.tagName}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (sizeLabel.isNotEmpty() && downloadState !is UpdateDownloadState.Downloading) {
                        Text(
                            text = "${release.apkAsset.name} · $sizeLabel",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                        )
                    }
                    if (release.notesSummary.isNotBlank() && downloadState is UpdateDownloadState.Idle) {
                        Text(
                            text = release.notesSummary,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }
                if (downloadState !is UpdateDownloadState.Downloading) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Отложить обновление",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            when (downloadState) {
                is UpdateDownloadState.Idle -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onStartDownload,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Скачать и обновить", fontSize = 12.sp)
                        }
                        OutlinedButton(onClick = { onOpenInBrowser(release.htmlUrl) }) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Релиз", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                is UpdateDownloadState.Downloading -> {
                    val pctText = downloadState.progress?.let { "${(it * 100).toInt()}%" } ?: "..."
                    val loadedText = AppUpdateLogic.formatFileSize(downloadState.downloadedBytes)
                    val totalText = AppUpdateLogic.formatFileSize(downloadState.totalBytes)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (downloadState.progress != null) {
                            LinearProgressIndicator(
                                progress = { downloadState.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Text(
                            text = if (totalText.isNotEmpty()) {
                                "Скачивание $pctText · $loadedText из $totalText"
                            } else {
                                "Скачивание обновления..."
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                is UpdateDownloadState.ReadyToInstall -> {
                    Button(
                        onClick = { onInstallApk(downloadState.apkFile) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Установить ${release.tagName}", fontSize = 12.sp)
                    }
                }

                is UpdateDownloadState.Failed -> {
                    Text(
                        text = downloadState.message,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onStartDownload,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Повторить", fontSize = 12.sp)
                        }
                        OutlinedButton(onClick = { onOpenInBrowser(release.htmlUrl) }) {
                            Text("В браузере", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
