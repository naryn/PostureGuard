package com.example.postureguard.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.postureguard.BuildConfig
import com.example.postureguard.data.settings.AnalysisFrameRate
import com.example.postureguard.posture.model.FacingDirection
import com.example.postureguard.posture.model.SensitivityPreset
import com.example.postureguard.ui.components.PageScaffold
import com.example.postureguard.ui.components.PgCard
import com.example.postureguard.ui.components.PgChoiceRow
import com.example.postureguard.ui.components.PgDivider
import com.example.postureguard.ui.components.PgInfoRow
import com.example.postureguard.ui.components.PgNote
import com.example.postureguard.ui.components.PgNotice
import com.example.postureguard.ui.components.PgSectionTitle
import com.example.postureguard.ui.components.PgSwitchRow
import com.example.postureguard.ui.theme.Alert
import com.example.postureguard.ui.theme.AlertPale
import com.example.postureguard.ui.theme.Ink
import com.example.postureguard.ui.theme.InkSoft
import com.example.postureguard.ui.theme.TealDeep
import com.example.postureguard.ui.util.facingLabel
import com.example.postureguard.ui.viewmodel.SettingsViewModel
import com.example.postureguard.vision.VisionModelInfo

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onHistory: () -> Unit,
    onPrivacy: () -> Unit,
    onAbout: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val hasCalibration by viewModel.hasCalibration.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var confirmDeleteHistory by remember { mutableStateOf(false) }
    var confirmDeleteCalibration by remember { mutableStateOf(false) }

    if (confirmDeleteHistory) {
        ConfirmDeleteDialog(
            title = "删除全部历史统计？",
            detail = "会删除所有监测会话、每日/每小时统计与姿态事件。校准数据会保留。此操作不可撤销。",
            onConfirm = {
                confirmDeleteHistory = false
                viewModel.deleteHistory()
            },
            onDismiss = { confirmDeleteHistory = false },
        )
    }
    if (confirmDeleteCalibration) {
        ConfirmDeleteDialog(
            title = "删除校准数据？",
            detail = "删除后需要重新校准才能开始监测。历史统计不会自动删除。此操作不可撤销。",
            onConfirm = {
                confirmDeleteCalibration = false
                viewModel.deleteCalibration()
            },
            onDismiss = { confirmDeleteCalibration = false },
        )
    }

    PageScaffold(title = "设置", subtitle = "所有阈值均为工程默认值，可随个人情况调整", onBack = onBack) {
        if (message != null) {
            PgNotice(
                title = "操作结果",
                detail = message!!,
                containerColor = AlertPale,
                contentColor = Alert,
                actionLabel = "关闭",
                onAction = viewModel::clearMessage,
            )
        }

        PgCard {
            PgSectionTitle("监测")
            Text("分析帧率", style = MaterialTheme.typography.bodyMedium, color = Ink)
            PgChoiceRow(
                options = AnalysisFrameRate.entries.map { it to it.label },
                selected = settings.analysisFrameRate,
                onSelect = viewModel::updateFrameRate,
            )
            PgSwitchRow(
                title = "监测时保持屏幕常亮",
                description = "仅在监测页生效，会增加耗电",
                checked = settings.keepScreenOn,
                onCheckedChange = viewModel::updateKeepScreenOn,
            )
            PgDivider()
            PgInfoRow("分析分辨率", "640 × 480（自动 4:3）")
            Text(
                "相机由系统在可用分辨率中选择最接近 4:3 的档位；预览与推理使用同一帧。",
                style = MaterialTheme.typography.labelSmall,
                color = InkSoft,
            )
        }

        PgCard {
            PgSectionTitle("提醒")
            PgSwitchRow(
                title = "声音",
                checked = settings.soundEnabled,
                onCheckedChange = viewModel::updateSoundEnabled,
            )
            PgSwitchRow(
                title = "语音",
                description = "使用系统中文语音引擎",
                checked = settings.speechEnabled,
                onCheckedChange = viewModel::updateSpeechEnabled,
            )
            PgSwitchRow(
                title = "震动",
                checked = settings.vibrationEnabled,
                onCheckedChange = viewModel::updateVibrationEnabled,
            )
            PgDivider()
            Text("异常持续时间", style = MaterialTheme.typography.bodyMedium, color = Ink)
            Text(
                "轻度异常需要持续这么久才确认；明显异常会更快确认。",
                style = MaterialTheme.typography.labelSmall,
                color = InkSoft,
            )
            PgChoiceRow(
                options = listOf(
                    5_000L to "5 秒",
                    8_000L to "8 秒",
                    12_000L to "12 秒",
                ),
                selected = settings.mildHoldDurationMs,
                onSelect = viewModel::updateMildHoldDurationMs,
            )
            PgDivider()
            Text("提醒冷却时间", style = MaterialTheme.typography.bodyMedium, color = Ink)
            Text(
                "同一类异常在冷却期内不会重复提醒。",
                style = MaterialTheme.typography.labelSmall,
                color = InkSoft,
            )
            PgChoiceRow(
                options = listOf(
                    60_000L to "1 分钟",
                    120_000L to "2 分钟",
                    300_000L to "5 分钟",
                ),
                selected = settings.alertCooldownMs,
                onSelect = viewModel::updateAlertCooldownMs,
            )
            PgNote("这些时长是工程默认值，不是医学建议；请根据自己和环境调整。")
        }

        PgCard {
            PgSectionTitle("灵敏度")
            PgChoiceRow(
                options = SensitivityPreset.entries.map { it to it.displayName },
                selected = settings.sensitivity,
                onSelect = viewModel::updateSensitivity,
            )
            Text(
                "灵敏会更容易触发提醒；宽松需要更明显的变化。校准噪声会自动抬高最小阈值。",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
            )
        }

        PgCard {
            PgSectionTitle("摄像头")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "朝向",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    facingLabel(settings.facingDirection),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft,
                )
            }
            PgChoiceRow(
                options = listOf(
                    FacingDirection.FACING_RIGHT to "面向右侧",
                    FacingDirection.FACING_LEFT to "面向左侧",
                ),
                selected = settings.facingDirection,
                onSelect = viewModel::updateFacingDirection,
            )
            PgNote("改变朝向后，之前的方向基准不再匹配，请重新校准。")
        }

        PgCard {
            PgSectionTitle("数据与隐私")
            SettingsLinkRow("隐私说明", "了解本地处理与数据边界", onPrivacy)
            PgDivider()
            SettingsLinkRow("历史记录", "查看已保存的监测会话", onHistory)
            PgDivider()
            SettingsLinkRow(
                title = "删除全部历史统计",
                subtitle = "会话、事件与每日/每小时统计",
                onClick = { confirmDeleteHistory = true },
                danger = true,
            )
            PgDivider()
            SettingsLinkRow(
                title = "删除校准数据",
                subtitle = if (hasCalibration) "删除后需要重新校准" else "当前没有校准数据",
                onClick = { if (hasCalibration) confirmDeleteCalibration = true },
                danger = hasCalibration,
                enabled = hasCalibration,
            )
            PgNote("所有数据保存在本机 Room 数据库与 DataStore 中，不提供云同步，也不会自动上传。")
        }

        PgCard {
            PgSectionTitle("关于")
            PgInfoRow("App 版本", BuildConfig.VERSION_NAME)
            PgInfoRow("姿态模型", VisionModelInfo.MODEL_VERSION)
            PgDivider()
            SettingsLinkRow("关于与开源许可", "模型来源、版本与使用限制", onAbout)
        }

        Text(
            "PostureGuard 不是医疗器械，不提供诊断或治疗建议。",
            style = MaterialTheme.typography.labelSmall,
            color = InkSoft,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun SettingsLinkRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = when {
                !enabled -> InkSoft.copy(alpha = 0.5f)
                danger -> Alert
                else -> Ink
            },
        )
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = InkSoft)
    }
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    detail: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(detail) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确认删除", color = Alert)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TealDeep)
            }
        },
    )
}
