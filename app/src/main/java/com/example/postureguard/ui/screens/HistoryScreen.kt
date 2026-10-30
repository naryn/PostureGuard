package com.example.postureguard.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.postureguard.data.entity.SessionEntity
import com.example.postureguard.ui.components.PageScaffold
import com.example.postureguard.ui.components.PgCard
import com.example.postureguard.ui.components.PgDivider
import com.example.postureguard.ui.components.PgEmptyState
import com.example.postureguard.ui.components.PgPill
import com.example.postureguard.ui.components.PgSectionTitle
import com.example.postureguard.ui.theme.Ink
import com.example.postureguard.ui.theme.InkSoft
import com.example.postureguard.ui.theme.Mist
import com.example.postureguard.ui.util.formatClock
import com.example.postureguard.ui.util.formatCompactDuration
import com.example.postureguard.ui.util.formatDateWithWeekday
import com.example.postureguard.ui.viewmodel.HistoryViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    val grouped = sessions
        .groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
        .toSortedMap(compareByDescending<LocalDate> { it })

    PageScaffold(
        title = "历史记录",
        subtitle = "每次监测的时长与异常分布",
        onBack = onBack,
    ) {
        if (sessions.isEmpty()) {
            PgCard {
                PgEmptyState("还没有监测记录", "完成一次监测后，可在这里按日期查看会话与事件时间段。")
            }
        } else {
            grouped.forEach { (date, daySessions) ->
                PgSectionTitle(formatDateWithWeekday(date))
                PgCard {
                    daySessions.forEachIndexed { index, session ->
                        SessionRow(session = session, onClick = { onOpenSession(session.id) })
                        if (index != daySessions.lastIndex) PgDivider()
                    }
                }
            }
        }
        Text(
            "只保存时长、指标与事件时间段，不保存视频或截图。",
            style = MaterialTheme.typography.labelSmall,
            color = InkSoft,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun SessionRow(session: SessionEntity, onClick: () -> Unit) {
    val abnormal = session.forwardHeadDurationMs + session.headDownDurationMs +
        session.headDownForwardDurationMs + session.torsoLeanDurationMs
    val end = session.endedAt
        ?: (session.startedAt + session.totalDurationMs).coerceAtLeast(session.startedAt)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${formatClock(session.startedAt)} – ${formatClock(end)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ink,
                )
                Spacer(Modifier.size(8.dp))
                if (!session.finalized) {
                    PgPill("未正常结束", Mist, InkSoft)
                }
            }
            Text(
                "监测 ${formatCompactDuration(session.totalDurationMs - session.pausedDurationMs)} · 异常 ${formatCompactDuration(abnormal)}",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
            )
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = InkSoft.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp),
        )
    }
}
