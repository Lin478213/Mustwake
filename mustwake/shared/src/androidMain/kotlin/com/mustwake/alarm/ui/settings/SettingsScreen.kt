package com.mustwake.alarm.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mustwake.alarm.data.AppContainer
import com.mustwake.alarm.data.backup.ImportMode
import com.mustwake.alarm.data.backup.ImportReport
import com.mustwake.alarm.data.settings.AppSettings
import com.mustwake.alarm.ui.design.MwCard
import com.mustwake.alarm.ui.design.MwPrimaryButton
import com.mustwake.alarm.ui.design.MwSecondaryButton
import com.mustwake.alarm.ui.design.MwSectionTitle
import com.mustwake.alarm.ui.design.MwSwitchRow
import com.mustwake.alarm.ui.design.MwText
import com.mustwake.alarm.ui.design.MwTypo
import com.mustwake.alarm.ui.design.mwGlassSupported
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(container: AppContainer, onOpenPermissions: () -> Unit = {}) {
    val settings by container.settingsRepository.settings.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    val glassSupported = mwGlassSupported()
    val context = LocalContext.current
    val alarms by container.alarmRepository.observeAlarms().collectAsState(initial = emptyList())
    var resultText by remember { mutableStateOf<String?>(null) }
    var showReplaceConfirm by remember { mutableStateOf(false) }
    var pendingImportMode by rememberSaveable { mutableStateOf(ImportMode.MERGE) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                resultText = try {
                    val json = container.backupRepository.exportJson()
                    val written = withContext(Dispatchers.IO) { context.writeTextTo(uri, json) }
                    if (written) "已导出备份" else "导出失败：无法写入文件"
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    "导出失败：${e.message ?: "读取数据出错"}"
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                resultText = try {
                    val text = withContext(Dispatchers.IO) { context.readTextFrom(uri) }
                    if (text == null) {
                        "导入失败：无法读取文件或文件过大"
                    } else {
                        formatImportReport(
                            container.backupRepository.importJson(text, pendingImportMode),
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    "导入失败：${e.message ?: "未知错误"}"
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
    ) {
        MwSectionTitle(text = "权限与后台")
        MwCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenPermissions) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    MwText(text = "权限状态", typo = MwTypo.Body)
                    MwText(
                        text = "通知、精确闹钟、全屏通知、自启动",
                        typo = MwTypo.Caption,
                    )
                }
                MwText(text = "›", typo = MwTypo.Title)
            }
        }

        MwSectionTitle(text = "外观", modifier = Modifier.padding(top = 16.dp))
        MwCard(modifier = Modifier.fillMaxWidth()) {
            MwSwitchRow(
                title = "悬浮底栏",
                summary = "主界面使用悬浮导航栏样式",
                checked = settings.floatingBottomBarEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { container.settingsRepository.setFloatingBottomBarEnabled(enabled) }
                },
            )
            MwSwitchRow(
                title = "液态玻璃",
                summary = if (glassSupported) {
                    "半透明模糊表面与高光描边"
                } else {
                    "此设备不支持实时模糊，将使用半透明纯色"
                },
                checked = settings.liquidGlassEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { container.settingsRepository.setLiquidGlassEnabled(enabled) }
                },
            )
        }

        MwSectionTitle(text = "步数", modifier = Modifier.padding(top = 16.dp))
        MwCard(modifier = Modifier.fillMaxWidth()) {
            MwStepperRow(
                title = "默认目标步数",
                value = settings.defaultGoalSteps,
                range = 10..500,
                step = 10,
                suffix = "步",
            ) { scope.launch { container.settingsRepository.setDefaultGoalSteps(it) } }
        }

        MwSectionTitle(text = "稍后提醒", modifier = Modifier.padding(top = 16.dp))
        MwCard(modifier = Modifier.fillMaxWidth()) {
            MwStepperRow(
                title = "间隔",
                value = settings.snoozeMinutes,
                range = 1..15,
                step = 1,
                suffix = "分钟",
            ) { scope.launch { container.settingsRepository.setSnoozeMinutes(it) } }
            MwStepperRow(
                title = "最多次数",
                value = settings.maxSnoozes,
                range = 0..5,
                step = 1,
                suffix = "次",
            ) { scope.launch { container.settingsRepository.setMaxSnoozes(it) } }
        }

        MwSectionTitle(text = "传感器降级", modifier = Modifier.padding(top = 16.dp))
        MwCard(modifier = Modifier.fillMaxWidth()) {
            MwStepperRow(
                title = "摇晃次数",
                value = settings.shakeCount,
                range = 10..100,
                step = 10,
                suffix = "次",
            ) { scope.launch { container.settingsRepository.setShakeCount(it) } }
            MwText(
                text = "计步传感器不可用时，摇晃达到该次数后长按停止。",
                typo = MwTypo.Caption,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        MwSectionTitle(text = "备份与恢复", modifier = Modifier.padding(top = 16.dp))
        MwCard(modifier = Modifier.fillMaxWidth()) {
            MwText(
                text = "备份闹钟与设置为 JSON 文件，不含历史记录与本机权限引导状态。导入会用备份中的设置覆盖当前设置。",
                typo = MwTypo.Caption,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MwSecondaryButton(
                    text = "导出备份",
                    modifier = Modifier.weight(1f),
                    onClick = { exportLauncher.launch(backupFileName()) },
                )
                MwSecondaryButton(
                    text = "合并导入",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        pendingImportMode = ImportMode.MERGE
                        importLauncher.launch(BACKUP_MIME_TYPES)
                    },
                )
            }
            MwSecondaryButton(
                text = "替换导入",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = { showReplaceConfirm = true },
            )
            resultText?.let {
                MwText(text = it, typo = MwTypo.Caption, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }

    if (showReplaceConfirm) {
        Dialog(onDismissRequest = { showReplaceConfirm = false }) {
            MwCard(modifier = Modifier.fillMaxWidth()) {
                MwText(text = "替换导入", typo = MwTypo.Headline)
                MwText(
                    text = "将删除现有 ${alarms.size} 个闹钟，并用备份中的闹钟替换。设置也会被备份覆盖。",
                    typo = MwTypo.Body,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MwSecondaryButton(
                        text = "取消",
                        modifier = Modifier.weight(1f),
                        onClick = { showReplaceConfirm = false },
                    )
                    MwPrimaryButton(
                        text = "确认替换",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showReplaceConfirm = false
                            pendingImportMode = ImportMode.REPLACE
                            importLauncher.launch(BACKUP_MIME_TYPES)
                        },
                    )
                }
            }
        }
    }
}

private val BACKUP_MIME_TYPES =
    arrayOf("application/json", "text/plain", "application/octet-stream")

private fun backupFileName(): String =
    "mustwake-backup-${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.json"

private fun formatImportReport(report: ImportReport): String {
    val summary =
        "导入 ${report.imported} / 跳过非法 ${report.skippedInvalid} / 跳过重复 ${report.skippedDuplicates}"
    return if (report.details.isEmpty()) {
        summary
    } else {
        summary + "\n" + report.details.joinToString("\n")
    }
}

private fun Context.writeTextTo(uri: Uri, text: String): Boolean = try {
    contentResolver.openOutputStream(uri, "wt")?.use { out ->
        out.write(text.toByteArray(Charsets.UTF_8))
    } != null
} catch (_: Exception) {
    false
}

/** Backup files are plain JSON of alarms+settings; anything larger is refused. */
private const val MAX_BACKUP_BYTES = 2L * 1024 * 1024

private fun Context.readTextFrom(uri: Uri): String? = try {
    contentResolver.openInputStream(uri)?.use { stream ->
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        var total = 0L
        while (true) {
            val read = stream.read(chunk)
            if (read < 0) break
            total += read
            if (total > MAX_BACKUP_BYTES) return@use null
            buffer.write(chunk, 0, read)
        }
        buffer.toByteArray().decodeToString()
    }
} catch (_: Exception) {
    null
}

@Composable
private fun MwStepperRow(
    title: String,
    value: Int,
    range: IntRange,
    step: Int,
    suffix: String,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            MwText(text = title, typo = MwTypo.Body)
            MwText(text = "$value $suffix", typo = MwTypo.Caption)
        }
        MwSecondaryButton(
            text = "−",
            modifier = Modifier.width(56.dp),
            enabled = value > range.first,
            onClick = { onChange((value - step).coerceAtLeast(range.first)) },
        )
        MwSecondaryButton(
            text = "+",
            modifier = Modifier.width(56.dp),
            enabled = value < range.last,
            onClick = { onChange((value + step).coerceAtMost(range.last)) },
        )
    }
}
