package com.example.postureguard.ui.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
private val dateFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1_000).coerceAtLeast(0)
    if (totalSeconds == 0L) return "0 分钟"
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours} 小时 ${minutes} 分"
        minutes > 0 -> "${minutes} 分 ${seconds} 秒"
        else -> "${seconds} 秒"
    }
}

fun formatCompactDuration(ms: Long): String {
    val totalMinutes = ms / 60_000
    if (totalMinutes < 1) return "${(ms / 1_000).coerceAtLeast(0)} 秒"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours} 小时 ${minutes} 分" else "${minutes} 分"
}

fun formatClock(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(timeFormatter)

fun formatDateLabel(date: LocalDate): String = date.format(dateFormatter)

fun formatDateWithWeekday(date: LocalDate): String {
    val weekday = when (date.dayOfWeek.value) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        else -> "周日"
    }
    return "${date.format(dateFormatter)} $weekday"
}

fun formatPercent(ratio: Double): String = "${(ratio * 100).toInt().coerceIn(0, 100)}%"

fun formatElapsed(ms: Long): String {
    val totalSeconds = (ms / 1_000).coerceAtLeast(0)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.CHINA, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.CHINA, "%02d:%02d", minutes, seconds)
    }
}

fun formatDecimal(value: Double, digits: Int = 2): String =
    String.format(Locale.CHINA, "%.${digits}f", value)
