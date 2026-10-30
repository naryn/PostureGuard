package com.example.postureguard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.postureguard.posture.calibration.CalibrationResult
import com.example.postureguard.posture.model.BodySide
import com.example.postureguard.posture.model.CalibrationProfile
import com.example.postureguard.ui.components.CameraPermissionGate
import com.example.postureguard.ui.components.CameraPreviewCard
import com.example.postureguard.ui.components.PageScaffold
import com.example.postureguard.ui.components.PgCard
import com.example.postureguard.ui.components.PgDivider
import com.example.postureguard.ui.components.PgInfoRow
import com.example.postureguard.ui.components.PgNote
import com.example.postureguard.ui.components.PgNotice
import com.example.postureguard.ui.components.PgPrimaryButton
import com.example.postureguard.ui.components.PgQuietButton
import com.example.postureguard.ui.components.PgSectionTitle
import com.example.postureguard.ui.theme.Alert
import com.example.postureguard.ui.theme.AlertPale
import com.example.postureguard.ui.theme.Hairline
import com.example.postureguard.ui.theme.Ink
import com.example.postureguard.ui.theme.InkSoft
import com.example.postureguard.ui.theme.Mist
import com.example.postureguard.ui.theme.Teal
import com.example.postureguard.ui.theme.TealPale
import com.example.postureguard.ui.theme.Warm
import com.example.postureguard.ui.theme.WarmPale
import com.example.postureguard.ui.util.facingLabel
import com.example.postureguard.ui.util.formatDecimal
import com.example.postureguard.ui.util.formatPercent
import com.example.postureguard.ui.viewmodel.CalibrationViewModel
import kotlinx.coroutines.delay

@Composable
fun CalibrationScreen(
    viewModel: CalibrationViewModel,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val existingProfile by viewModel.existingProfile.collectAsStateWithLifecycle()

    LaunchedEffect(state.running) {
        while (state.running) {
            delay(300)
            viewModel.tick()
        }
    }

    PageScaffold(
        title = "个人校准",
        subtitle = "用你自然、舒适的坐姿建立个人参考",
        onBack = onBack,
    ) {
        if (state.result == null) {
            CameraPermissionGate {
                CameraPreviewCard(
                    active = true,
                    targetFps = 10,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(270.dp),
                    onFrame = viewModel::onFrame,
                )
            }
            PgCard {
                LiveQualityRow(state.liveQualityValid, state.liveSide)
                PgDivider()
                PgInfoRow("面向方向", facingLabel(state.facing))
                PgInfoRow("预热时间", "前 2 秒，不计入样本")
                PgNote("以自然舒适的坐姿坐好，不要刻意挺胸或仰头。保持约 15 秒不动。")
                if (existingProfile != null) {
                    PgNote(
                        text = "重新校准会替换当前个人基准。",
                        containerColor = WarmPale,
                        contentColor = Warm,
                    )
                }
            }
        }

        when (val result = state.result) {
            null -> ProgressCard(
                progress = state.progress,
                validRatio = state.validRatio,
                validSamples = state.validSamples,
                inWarmup = state.inWarmup,
                running = state.running,
                canStart = state.liveQualityValid && state.liveSide != null,
                onStart = viewModel::start,
                onCancel = viewModel::retry,
            )

            is CalibrationResult.Failure -> {
                PgNotice(
                    title = "本次校准未通过",
                    detail = result.reason.message + "（有效样本 ${formatPercent(result.validRatio)}）",
                    containerColor = AlertPale,
                    contentColor = Alert,
                )
                PgPrimaryButton(text = "重新校准", onClick = viewModel::retry)
            }

            is CalibrationResult.Success -> SuccessCard(
                result = result,
                saved = state.saved,
                saveError = state.saveError,
                onRetrySave = viewModel::retrySave,
                onRetryCalibration = viewModel::retry,
                onDone = onDone,
            )
        }
    }
}

@Composable
private fun LiveQualityRow(valid: Boolean, side: BodySide?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(if (valid) Teal else Warm, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (valid) "关键点有效" else "等待完整人体",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
            )
            Text(
                "分析侧：${when (side) {
                    BodySide.LEFT -> "左侧"
                    BodySide.RIGHT -> "右侧"
                    null -> "尚未检测"
                }}",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
            )
        }
    }
}

@Composable
private fun ProgressCard(
    progress: Float,
    validRatio: Double,
    validSamples: Int,
    inWarmup: Boolean,
    running: Boolean,
    canStart: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    PgCard {
        PgSectionTitle("校准进度")
        Text(
            "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Teal,
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = Teal,
            trackColor = TealPale,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                if (running) "有效样本 ${formatPercent(validRatio)} · $validSamples 帧" else "有效样本比例会在校准中实时显示",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
            )
            if (inWarmup && running) {
                Text("预热中", style = MaterialTheme.typography.labelSmall, color = Warm)
            }
        }
        if (running) {
            PgQuietButton(text = "取消校准", onClick = onCancel)
        } else {
            PgPrimaryButton(
                text = "开始 15 秒校准",
                onClick = onStart,
                enabled = canStart,
            )
            if (!canStart) {
                Text(
                    "请先让头、肩、髋完整出现在画面中，关键点稳定后即可开始。",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft,
                )
            }
        }
    }
}

@Composable
private fun SuccessCard(
    result: CalibrationResult.Success,
    saved: Boolean,
    saveError: String?,
    onRetrySave: () -> Unit,
    onRetryCalibration: () -> Unit,
    onDone: () -> Unit,
) {
    var showDetails by remember(result) { mutableStateOf(false) }
    val grade = when {
        result.validRatio >= 0.85 -> "良好"
        result.validRatio >= 0.70 -> "一般"
        else -> "需要留意"
    }
    val gradeColor = if (result.validRatio >= 0.70) Teal else Warm

    PgCard {
        PgNotice(
            title = if (saved) "校准已保存" else "校准完成，正在保存",
            detail = "基准质量：$grade · 有效样本 ${formatPercent(result.validRatio)}",
            containerColor = if (saved) TealPale else Mist,
            contentColor = if (saved) Teal else InkSoft,
        )
        if (saveError != null) {
            PgNotice(
                title = "保存失败",
                detail = saveError,
                containerColor = AlertPale,
                contentColor = Alert,
            )
        }
        PgInfoRow("基准质量", grade, gradeColor)
        PgInfoRow("有效样本", formatPercent(result.validRatio))
        TextButton(onClick = { showDetails = !showDetails }) {
            Text(if (showDetails) "收起基准指标" else "查看基准指标", color = Teal)
        }
        if (showDetails) {
            ProfileDetails(result.profile)
        }
        if (saved) {
            PgPrimaryButton(text = "完成", onClick = onDone)
            PgQuietButton(text = "重新校准", onClick = onRetryCalibration)
        } else if (saveError != null) {
            PgPrimaryButton(text = "重试保存", onClick = onRetrySave)
            PgQuietButton(text = "重新校准", onClick = onRetryCalibration)
        }
        PgNote("个人基准只反映你本次的舒适坐姿，不是医学诊断标准。睡眠、座椅高度变化后可以重新校准。")
    }
}

@Composable
private fun ProfileDetails(profile: CalibrationProfile) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PgDivider()
        PgInfoRow("耳肩前移中位数", formatDecimal(profile.forwardOffsetMedian))
        PgInfoRow("头部角度中位数", "${formatDecimal(profile.headAngleMedianDeg, 1)}°")
        PgInfoRow("躯干角度中位数", "${formatDecimal(profile.torsoAngleMedianDeg, 1)}°")
        PgInfoRow("前移波动 (MAD)", formatDecimal(profile.forwardOffsetMad, 3))
        PgInfoRow("头部波动 (MAD)", "${formatDecimal(profile.headAngleMadDeg, 1)}°")
        PgInfoRow("躯干波动 (MAD)", "${formatDecimal(profile.torsoAngleMadDeg, 1)}°")
        PgInfoRow("关键点质量", formatPercent(profile.qualityMedian))
        Text(
            "这些数值用于调整提醒灵敏度，不是健康评分。",
            style = MaterialTheme.typography.labelSmall,
            color = InkSoft,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
