package com.example.postureguard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.postureguard.monitoring.MonitorPhase
import com.example.postureguard.ui.components.PageScaffold
import com.example.postureguard.ui.components.PgCard
import com.example.postureguard.ui.components.PgDivider
import com.example.postureguard.ui.components.PgMetricTile
import com.example.postureguard.ui.components.PgNote
import com.example.postureguard.ui.components.PgNotice
import com.example.postureguard.ui.components.PgPill
import com.example.postureguard.ui.components.PgPrimaryButton
import com.example.postureguard.ui.theme.Ink
import com.example.postureguard.ui.theme.InkSoft
import com.example.postureguard.ui.theme.Mint
import com.example.postureguard.ui.theme.MintPale
import com.example.postureguard.ui.theme.Teal
import com.example.postureguard.ui.theme.TealDeep
import com.example.postureguard.ui.theme.TealPale
import com.example.postureguard.ui.theme.Warm
import com.example.postureguard.ui.util.facingLabel
import com.example.postureguard.ui.util.formatCompactDuration
import com.example.postureguard.ui.util.formatDateLabel
import com.example.postureguard.ui.util.formatPercent
import com.example.postureguard.ui.viewmodel.HomeViewModel
import java.time.Instant
import java.time.ZoneId

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onStartMonitoring: () -> Unit,
    onOpenMonitor: () -> Unit,
    onCameraSetup: () -> Unit,
    onCalibration: () -> Unit,
    onSettings: () -> Unit,
    onReports: () -> Unit,
    onHistory: () -> Unit,
    onPrivacy: () -> Unit,
) {
    val summary by viewModel.todaySummary.collectAsStateWithLifecycle()
    val calibration by viewModel.calibration.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val phase by viewModel.monitorPhase.collectAsStateWithLifecycle()
    var onboardingDismissed by rememberSaveable { mutableStateOf(false) }
    val showOnboarding = settings != null && settings?.onboardingCompleted == false && !onboardingDismissed

    if (showOnboarding) {
        AlertDialog(
            onDismissRequest = {
                onboardingDismissed = true
                viewModel.completeOnboarding()
            },
            title = { Text("开始使用 PostureGuard") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. 把手机固定在书桌侧面，后置摄像头朝向自己。", style = MaterialTheme.typography.bodyMedium)
                    Text("2. 用一个自然、舒适的坐姿完成约 15 秒校准。", style = MaterialTheme.typography.bodyMedium)
                    Text("3. 开始监测后，画面只在本机分析，不会上传。", style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onboardingDismissed = true
                    viewModel.completeOnboarding()
                    onCameraSetup()
                }) {
                    Text("去摆放手机")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onboardingDismissed = true
                    viewModel.completeOnboarding()
                }) {
                    Text("稍后")
                }
            },
        )
    }

    PageScaffold(
        title = "PostureGuard",
        subtitle = "侧坐姿提醒 · 画面只在本机处理",
    ) {
        if (phase == MonitorPhase.RUNNING || phase == MonitorPhase.PAUSED) {
            PgNotice(
                title = if (phase == MonitorPhase.RUNNING) "监测进行中" else "监测已暂停",
                detail = if (phase == MonitorPhase.RUNNING) {
                    "相机只在你停留在监测页时运行，返回即可继续。"
                } else {
                    "相机已释放。回到监测页可继续本次记录。"
                },
                containerColor = TealPale,
                contentColor = TealDeep,
                actionLabel = "回到监测",
                onAction = onOpenMonitor,
            )
        }

        PgCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("今日概览", style = MaterialTheme.typography.titleSmall, color = InkSoft)
                PgPill("本地分析", MintPale, TealDeep)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = formatCompactDuration(summary.normalDurationMs),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = Teal,
                )
                Text(
                    "正常姿势观察时长",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PgMetricTile(
                    label = "异常累计",
                    value = formatCompactDuration(summary.abnormalDurationMs),
                    unit = "",
                    accent = Warm,
                    modifier = Modifier.weight(1.15f),
                )
                PgMetricTile(
                    label = "异常次数",
                    value = summary.eventCount.toString(),
                    unit = "次",
                    accent = Ink,
                    modifier = Modifier.weight(0.85f),
                )
                PgMetricTile(
                    label = "监测时长",
                    value = formatCompactDuration(summary.monitoredDurationMs),
                    unit = "",
                    accent = Ink,
                    modifier = Modifier.weight(1.15f),
                )
            }
            PgDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onReports),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("查看完整统计", style = MaterialTheme.typography.bodyMedium, color = TealDeep)
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = TealDeep,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        PgCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(if (calibration != null) Teal else Warm, CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("个人校准", style = MaterialTheme.typography.titleSmall, color = Ink)
                    if (calibration == null) {
                        Text("尚未校准，监测前需要一次基准采集", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                    } else {
                        val date = Instant.ofEpochMilli(calibration!!.createdAt)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        Text(
                            "${formatDateLabel(date)} · ${facingLabel(calibration!!.facingDirection)} · ${formatPercent(calibration!!.validSampleRatio)} 有效样本",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSoft,
                        )
                    }
                }
                Text(
                    if (calibration == null) "去校准" else "重新校准",
                    modifier = Modifier.clickable(onClick = onCalibration),
                    style = MaterialTheme.typography.labelLarge,
                    color = TealDeep,
                )
            }
            PgNote("校准记录的是你本人自然坐姿的参考值，不是医学标准的「正确姿势」。")
        }

        PgPrimaryButton(
            text = if (calibration == null) "先完成校准" else "开始监测",
            onClick = if (calibration == null) onCalibration else onStartMonitoring,
        )

        PgCard {
            HomeLinkRow(
                icon = Icons.Rounded.CameraAlt,
                title = "摆放手机",
                subtitle = "侧面、稳定、头肩髋都在画面内",
                onClick = onCameraSetup,
            )
            PgDivider()
            HomeLinkRow(
                icon = Icons.Rounded.BarChart,
                title = "统计报告",
                subtitle = "今日分布与最近 7 天趋势",
                onClick = onReports,
            )
            PgDivider()
            HomeLinkRow(
                icon = Icons.Rounded.History,
                title = "历史记录",
                subtitle = "按会话查看异常时段",
                onClick = onHistory,
            )
            PgDivider()
            HomeLinkRow(
                icon = Icons.Rounded.Settings,
                title = "设置",
                subtitle = "提醒、灵敏度、隐私与数据",
                onClick = onSettings,
            )
            PgDivider()
            HomeLinkRow(
                icon = Icons.Rounded.Lock,
                title = "隐私说明",
                subtitle = "本地处理，不上传画面",
                onClick = onPrivacy,
            )
        }

        Text(
            text = "PostureGuard 用于日常坐姿自我观察，不提供医学诊断或治疗建议。",
            style = MaterialTheme.typography.labelSmall,
            color = InkSoft,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun HomeLinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(MintPale, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = TealDeep, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Ink)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = InkSoft)
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = Color(0xFFB4C4C7),
            modifier = Modifier.size(18.dp),
        )
    }
}
