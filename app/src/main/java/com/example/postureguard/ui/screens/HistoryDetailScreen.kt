package com.example.postureguard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.postureguard.data.entity.PostureEventEntity
import com.example.postureguard.data.entity.SessionEntity
import com.example.postureguard.posture.model.ConditionSeverity
import com.example.postureguard.posture.model.PostureCondition
import com.example.postureguard.ui.components.PageScaffold
import com.example.postureguard.ui.components.PgBarRow
import com.example.postureguard.ui.components.PgCard
import com.example.postureguard.ui.components.PgDivider
import com.example.postureguard.ui.components.PgEmptyState
import com.example.postureguard.ui.components.PgInfoRow
import com.example.postureguard.ui.components.PgMetricTile
import com.example.postureguard.ui.components.PgNote
import com.example.postureguard.ui.components.PgPill
import com.example.postureguard.ui.components.PgSectionTitle
import com.example.postureguard.ui.theme.Alert
import com.example.postureguard.ui.theme.Ink
import com.example.postureguard.ui.theme.InkSoft
import com.example.postureguard.ui.theme.Lavender
import com.example.postureguard.ui.theme.Mist
import com.example.postureguard.ui.theme.Teal
import com.example.postureguard.ui.theme.TealDeep
import com.example.postureguard.ui.theme.Warm
import com.example.postureguard.ui.util.formatClock
import com.example.postureguard.ui.util.formatCompactDuration
import com.example.postureguard.ui.util.formatDateWithWeekday
import com.example.postureguard.ui.util.formatDecimal
import com.example.postureguard.ui.util.severityLabel
import com.example.postureguard.ui.viewmodel.HistoryDetailViewModel
import java.time.Instant
import java.time.ZoneId

@Composable
fun HistoryDetailScreen(
    viewModel: HistoryDetailViewModel,
    onBack: () -> Unit,
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()

    PageScaffold(
        title = "监测详情",
        subtitle = "按会话查看姿态分布与事件时间段",
        onBack = onBack,
    ) {
        val s = session
        if (s == null) {
            PgCard {
                PgEmptyState("记录不存在", "这条记录可能已被删除。")
            }
            return@PageScaffold
        }
        SessionOverview(s = s, eventCount = events.size)
        SessionDistribution(s)
        EventsCard(events)
        PgNote("事件记录包含时间段与姿态指标峰值，不包含视频、截图或原始画面。")
    }
}

@Composable
private fun SessionOverview(s: SessionEntity, eventCount: Int) {
    val date = Instant.ofEpochMilli(s.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
    val abnormal = s.forwardHeadDurationMs + s.headDownDurationMs +
        s.headDownForwardDurationMs + s.torsoLeanDurationMs
    val monitored = (s.totalDurationMs - s.pausedDurationMs).coerceAtLeast(0)
    PgCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    formatDateWithWeekday(date),
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                )
                Text(
                    buildString {
                        append(formatClock(s.startedAt))
                        s.endedAt?.let { append(" – ").append(formatClock(it)) }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft,
                )
            }
            PgPill(
                text = if (s.finalized) "已结束" else "未正常结束",
                containerColor = Mist,
                contentColor = InkSoft,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PgMetricTile(
                label = "监测时长",
                value = formatCompactDuration(monitored),
                unit = "",
                accent = Ink,
                modifier = Modifier.weight(1.1f),
            )
            PgMetricTile(
                label = "正常观察",
                value = formatCompactDuration(s.normalDurationMs),
                unit = "",
                accent = Teal,
                modifier = Modifier.weight(1.1f),
            )
            PgMetricTile(
                label = "异常累计",
                value = formatCompactDuration(abnormal),
                unit = "",
                accent = Alert,
                modifier = Modifier.weight(1.1f),
            )
        }
        PgDivider()
        PgInfoRow("异常事件", "$eventCount 次")
        PgInfoRow("有效检测时长", formatCompactDuration(s.validDurationMs))
        if (s.pausedDurationMs > 0) {
            PgInfoRow("暂停时长", formatCompactDuration(s.pausedDurationMs))
        }
    }
}

@Composable
private fun SessionDistribution(s: SessionEntity) {
    val maxValue = maxOf(
        s.normalDurationMs,
        s.forwardHeadDurationMs,
        s.headDownDurationMs,
        s.headDownForwardDurationMs,
        s.torsoLeanDurationMs,
        s.lowConfidenceDurationMs,
        1L,
    )
    PgCard {
        PgSectionTitle("姿态分布")
        PgBarRow("正常", s.normalDurationMs, maxValue, Teal)
        PgBarRow("头部前倾", s.forwardHeadDurationMs, maxValue, Warm)
        PgBarRow("低头", s.headDownDurationMs, maxValue, Lavender)
        PgBarRow("埋头（前倾 + 低头）", s.headDownForwardDurationMs, maxValue, Alert)
        PgBarRow("躯干前倾", s.torsoLeanDurationMs, maxValue, TealDeep)
        PgBarRow("低质量 / 离开画面", s.lowConfidenceDurationMs, maxValue, InkSoft)
    }
}

@Composable
private fun EventsCard(events: List<PostureEventEntity>) {
    PgCard {
        PgSectionTitle("事件时间段")
        if (events.isEmpty()) {
            PgEmptyState("没有持续异常事件", "本次监测中的异常都比较短暂，未达到确认阈值。")
        } else {
            events.forEachIndexed { index, event ->
                EventRow(event)
                if (index != events.lastIndex) PgDivider()
            }
        }
    }
}

@Composable
private fun EventRow(event: PostureEventEntity) {
    val condition = runCatching { PostureCondition.valueOf(event.type) }.getOrNull()
    val severity = runCatching { ConditionSeverity.valueOf(event.severity) }
        .getOrDefault(ConditionSeverity.MILD)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                condition?.label ?: "姿态异常",
                style = MaterialTheme.typography.bodyLarge,
                color = Ink,
            )
            Text(
                "${formatClock(event.startedAt)} – ${formatClock(event.endedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "持续 ${formatCompactDuration(event.durationMs)} · ${severityLabel(severity)}",
                style = MaterialTheme.typography.bodySmall,
                color = if (severity == ConditionSeverity.SEVERE) Alert else Warm,
            )
            val peaks = buildList {
                event.peakForwardDelta?.let { add("前移 ${formatDecimal(it, 2)}") }
                event.peakHeadDownDelta?.let { add("低头 ${formatDecimal(it, 1)}°") }
                event.peakTorsoLeanDelta?.let { add("躯干 ${formatDecimal(it, 1)}°") }
            }
            if (peaks.isNotEmpty()) {
                Text(
                    "峰值 " + peaks.joinToString(" / "),
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSoft,
                )
            }
        }
    }
}
