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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.postureguard.data.settings.CameraLens
import com.example.postureguard.posture.model.BodySide
import com.example.postureguard.posture.model.FacingDirection
import com.example.postureguard.posture.model.QualityIssue
import com.example.postureguard.ui.components.CameraPermissionGate
import com.example.postureguard.ui.components.CameraPreviewCard
import com.example.postureguard.ui.components.PageScaffold
import com.example.postureguard.ui.components.PgCard
import com.example.postureguard.ui.components.PgChoiceRow
import com.example.postureguard.ui.components.PgDivider
import com.example.postureguard.ui.components.PgInfoRow
import com.example.postureguard.ui.components.PgNote
import com.example.postureguard.ui.components.PgPrimaryButton
import com.example.postureguard.ui.components.PgSectionTitle
import com.example.postureguard.ui.theme.Ink
import com.example.postureguard.ui.theme.InkSoft
import com.example.postureguard.ui.theme.Mist
import com.example.postureguard.ui.theme.Teal
import com.example.postureguard.ui.theme.Warm
import com.example.postureguard.ui.viewmodel.CameraSetupViewModel

@Composable
fun CameraSetupScreen(
    viewModel: CameraSetupViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val probe by viewModel.probe.collectAsStateWithLifecycle()

    PageScaffold(
        title = "摆放手机",
        subtitle = "侧面、稳定，让头、肩和面部完整出现在画面中",
        onBack = onBack,
    ) {
        PgCard {
            PgSectionTitle("使用哪个摄像头")
            PgChoiceRow(
                options = listOf(
                    CameraLens.BACK to CameraLens.BACK.label,
                    CameraLens.FRONT to CameraLens.FRONT.label,
                ),
                selected = settings.cameraLens,
                onSelect = viewModel::setLens,
            )
            Text(
                "前置摄像头预览会镜像显示；切换摄像头会改变画面投影，请重新校准。",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
            )
        }

        PgCard {
            PgSectionTitle("你面向画面的方向")
            PgChoiceRow(
                options = listOf(
                    FacingDirection.FACING_RIGHT to "面向右侧",
                    FacingDirection.FACING_LEFT to "面向左侧",
                ),
                selected = settings.facingDirection,
                onSelect = viewModel::setFacing,
            )
            Text(
                "手机放在身体一侧时，选择你在画面里朝着的方向。若左右侧关键点反复跳动，可以切换后重新校准。",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
            )
        }

        CameraPermissionGate {
            CameraPreviewCard(
                active = true,
                targetFps = settings.targetFps,
                lens = settings.cameraLens,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                onFrame = viewModel::onFrame,
            )
        }

        PgCard {
            PgSectionTitle("画面检查")
            ProbeCheckRow("检测到人体", probe.hasPerson)
            ProbeCheckRow(
                "头、肩在画面内",
                probe.hasPerson && probe.qualityValid,
            )
            ProbeCheckRow("眼睛、嘴部可见", probe.faceVisible)
            ProbeCheckRow(
                "左右侧关键点稳定",
                probe.hasPerson && QualityIssue.SIDE_UNSTABLE !in probe.issues,
            )
            PgDivider()
            PgInfoRow(
                "当前分析侧",
                when (probe.selectedSide) {
                    BodySide.LEFT -> "左侧"
                    BodySide.RIGHT -> "右侧"
                    null -> "等待检测"
                },
            )
            if (probe.mirrored) {
                PgInfoRow("相机", "前置相机 · 预览已镜像")
            } else {
                PgInfoRow("相机", "后置相机 · 未镜像")
            }
            if (probe.issues.isNotEmpty() && probe.hasPerson) {
                Text(
                    text = probe.issues.joinToString(" · ") { it.label },
                    style = MaterialTheme.typography.bodySmall,
                    color = Warm,
                )
            }
            if (!probe.hasPerson) {
                PgNote("请坐到手机侧面，让头部、肩膀和面部进入画面。眼睛、鼻子、耳朵用于判断前倾，眼睛和嘴部可见时才能判断低头；髋部能拍到可额外判断驼背/弯腰（肩髋角度）。")
            }
        }

        PgCard {
            PgSectionTitle("摆放建议")
            TipRow(1, "把手机固定在书桌侧面，用支架或靠墙立稳。")
            TipRow(2, "${if (settings.cameraLens == CameraLens.FRONT) "前置" else "后置"}摄像头朝向自己，画面覆盖头部到肩膀，眼睛和嘴部不要被遮挡。")
            TipRow(3, "避免强背光，也不要让桌面挡住肩膀。髋部在画面内时可额外判断驼背/弯腰。")
            TipRow(4, "监测期间尽量不移动手机；移动后重新校准。")
            TipRow(5, "自然坐下，不要刻意挺胸或仰头。")
        }

        PgPrimaryButton(text = "完成设置", onClick = onBack)

        Text(
            "相机画面只在本机用于姿态关键点分析，不会上传或保存为视频。",
            style = MaterialTheme.typography.labelSmall,
            color = InkSoft,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun ProbeCheckRow(label: String, satisfied: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(if (satisfied) Teal else Mist, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (satisfied) Ink else InkSoft,
            fontWeight = if (satisfied) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun TipRow(number: Int, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(Mist, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = InkSoft,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
