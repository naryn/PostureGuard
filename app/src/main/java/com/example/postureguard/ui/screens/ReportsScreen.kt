package com.example.postureguard.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.postureguard.data.repository.DailySummary
import com.example.postureguard.data.repository.HourlySummary
import com.example.postureguard.ui.components.PageScaffold
import com.example.postureguard.ui.components.PgBarRow
import com.example.postureguard.ui.components.PgCard
import com.example.postureguard.ui.components.PgDivider
import com.example.postureguard.ui.components.PgEmptyState
import com.example.postureguard.ui.components.PgMetricTile
import com.example.postureguard.ui.components.PgNote
import com.example.postureguard.ui.components.PgSectionTitle
import com.example.postureguard.ui.theme.Alert
import com.example.postureguard.ui.theme.Hairline
import com.example.postureguard.ui.theme.Ink
import com.example.postureguard.ui.theme.InkSoft
import com.example.postureguard.ui.theme.Lavender
import com.example.postureguard.ui.theme.Mist
import com.example.postureguard.ui.theme.Teal
import com.example.postureguard.ui.theme.TealDeep
import com.example.postureguard.ui.theme.Warm
import com.example.postureguard.ui.util.formatCompactDuration
import com.example.postureguard.ui.util.formatDateWithWeekday
import com.example.postureguard.ui.viewmodel.ReportsViewModel
import java.time.LocalDate

@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel,
    onBack: () -> Unit,
) {
    val today by viewModel.todaySummary.collectAsStateWithLifecycle()
    val hours by viewModel.todayHours.collectAsStateWithLifecycle()
    val week by viewModel.lastSevenDays.collectAsStateWithLifecycle()

    PageScaffold(
        title = "统计报告",
        subtitle = "只统计检测质量有效的时段，不把离开画面算作正常",
        onBack = onBack,
    ) {
        PgCard {
            Text(
                formatDateWithWeekday(viewModel.todayDate),
                style = MaterialTheme.typography.titleSmall,
                color = InkSoft,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PgMetricTile(
                    label = "监测时长",
                    value = formatCompactDuration(today.monitoredDurationMs),
                    unit = "",
                    accent = Ink,
                    modifier = Modifier.weight(1.1f),
                )
                PgMetricTile(
                    label = "正常观察",
                    value = formatCompactDuration(today.normalDurationMs),
                    unit = "",
                    accent = Teal,
                    modifier = Modifier.weight(1.1f),
                )
                PgMetricTile(
                    label = "异常累计",
                    value = formatCompactDuration(today.abnormalDurationMs),
                    unit = "",
                    accent = Alert,
                    modifier = Modifier.weight(1.1f),
                )
            }
            PgDivider()
            PgMetricCellRow(
                label = "异常事件",
                value = "${today.eventCount} 次",
            )
            PgMetricCellRow(
                label = "有效检测时长",
                value = formatCompactDuration(today.validDurationMs),
            )
            PgMetricCellRow(
                label = "低质量 / 离开画面",
                value = formatCompactDuration(today.lowConfidenceDurationMs),
            )
        }

        PgCard {
            PgSectionTitle("今日姿态分布")
            val maxValue = maxOf(
                today.normalDurationMs,
                today.forwardHeadDurationMs,
                today.headDownDurationMs,
                today.headDownForwardDurationMs,
                today.torsoLeanDurationMs,
                today.lowConfidenceDurationMs,
                1L,
            )
            if (today.monitoredDurationMs == 0L) {
                PgEmptyState("今天还没有监测记录", "完成一次监测后，这里会显示各姿态类别的累计时长。")
            } else {
                PgBarRow("正常", today.normalDurationMs, maxValue, Teal)
                PgBarRow("头部前倾", today.forwardHeadDurationMs, maxValue, Warm)
                PgBarRow("低头", today.headDownDurationMs, maxValue, Lavender)
                PgBarRow("埋头（前倾 + 低头）", today.headDownForwardDurationMs, maxValue, Alert)
                PgBarRow("驼背/弯腰", today.torsoLeanDurationMs, maxValue, TealDeep)
                PgBarRow("低质量 / 离开画面", today.lowConfidenceDurationMs, maxValue, InkSoft)
            }
            PgNote("低质量、暂停与离开画面的时间单独统计，不会计入正常姿势。")
        }

        PgCard {
            PgSectionTitle("每小时分布")
            HourlyChart(hours)
            PgNote("条形为当小时监测时长，颜色区分正常、异常与低质量时段。")
        }

        PgCard {
            PgSectionTitle("最近 7 天")
            WeekTrend(week, viewModel.todayDate)
        }

        Text(
            "统计仅用于日常自我观察，阈值是工程默认值，不构成医学诊断或治疗建议。",
            style = MaterialTheme.typography.labelSmall,
            color = InkSoft,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun PgMetricCellRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = Ink,
        )
    }
}

@Composable
private fun HourlyChart(hours: List<HourlySummary>) {
    val byHour = hours.associateBy { it.hour }
    val maxTotal = (0..23)
        .maxOf { byHour[it]?.monitoredDurationMs ?: 0L }
        .coerceAtLeast(1L)
    val chartHeight = 110.dp

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            for (hour in 0..23) {
                val item = byHour[hour]
                val normal = item?.normalDurationMs ?: 0L
                val abnormal = item?.abnormalDurationMs ?: 0L
                val low = item?.lowConfidenceDurationMs ?: 0L
                val total = normal + abnormal + low
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    if (total == 0L) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(Hairline),
                        )
                    } else {
                        val scale = chartHeight.value / maxTotal
                        if (abnormal > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((abnormal * scale).dp.coerceAtLeast(1.dp))
                                    .background(Alert),
                            )
                        }
                        if (low > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((low * scale).dp.coerceAtLeast(1.dp))
                                    .background(InkSoft.copy(alpha = 0.35f)),
                            )
                        }
                        if (normal > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((normal * scale).dp.coerceAtLeast(1.dp))
                                    .background(Teal),
                            )
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("0 时", "6 时", "12 时", "18 时", "23 时").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = InkSoft)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ChartLegend("正常", Teal)
            ChartLegend("异常", Alert)
            ChartLegend("低质量", InkSoft.copy(alpha = 0.35f))
        }
    }
}

@Composable
private fun ChartLegend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = InkSoft)
    }
}

@Composable
private fun WeekTrend(days: List<DailySummary>, today: LocalDate) {
    val filled = (6 downTo 0).map { offset ->
        val date = today.minusDays(offset.toLong())
        days.find { it.date == date } ?: DailySummary.empty(date)
    }
    val maxMonitored = filled.maxOf { it.monitoredDurationMs }.coerceAtLeast(1L)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        filled.forEach { day ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.width(66.dp)) {
                    Text(
                        text = if (day.date == today) "今天" else "${day.date.monthValue}/${day.date.dayOfMonth}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (day.date == today) TealDeep else Ink,
                    )
                    Text(
                        text = formatCompactDuration(day.monitoredDurationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = InkSoft,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SegmentedBar(
                        normal = day.normalDurationMs.toFloat(),
                        abnormal = day.abnormalDurationMs.toFloat(),
                        low = day.lowConfidenceDurationMs.toFloat(),
                        max = maxMonitored.toFloat(),
                    )
                    Text(
                        "异常 ${formatCompactDuration(day.abnormalDurationMs)} · 事件 ${day.eventCount} 次",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkSoft,
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentedBar(normal: Float, abnormal: Float, low: Float, max: Float) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp),
    ) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(
            color = Mist,
            size = size,
            cornerRadius = radius,
        )
        val safeMax = if (max <= 0f) 1f else max
        var cursor = 0f
        fun segment(value: Float, color: Color) {
            if (value <= 0f) return
            val width = (size.width * (value / safeMax)).coerceAtMost(size.width - cursor)
            if (width > 0f) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(cursor, 0f),
                    size = Size(width, size.height),
                    cornerRadius = radius,
                )
                cursor += width
            }
        }
        segment(normal, Teal)
        segment(abnormal, Alert)
        segment(low, InkSoft.copy(alpha = 0.35f))
    }
}
