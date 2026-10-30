package com.example.postureguard.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.postureguard.BuildConfig
import com.example.postureguard.ui.components.PageScaffold
import com.example.postureguard.ui.components.PgCard
import com.example.postureguard.ui.components.PgDivider
import com.example.postureguard.ui.components.PgInfoRow
import com.example.postureguard.ui.components.PgNote
import com.example.postureguard.ui.components.PgSectionTitle
import com.example.postureguard.ui.theme.InkSoft
import com.example.postureguard.vision.VisionModelInfo

@Composable
fun AboutScreen(onBack: () -> Unit) {
    PageScaffold(
        title = "关于 PostureGuard",
        subtitle = "侧视坐姿提醒 · 完全本地运行",
        onBack = onBack,
    ) {
        PgCard {
            PgSectionTitle("应用")
            PgInfoRow("版本", BuildConfig.VERSION_NAME)
            PgInfoRow("包名", BuildConfig.APPLICATION_ID)
            Text(
                "通过手机后置摄像头从侧面观察坐姿，提示前倾、低头、埋头和驼背/弯腰。" +
                    "定位为日常自我观察工具，不替代医疗或康复评估。",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
            )
        }

        PgCard {
            PgSectionTitle("姿态模型")
            PgInfoRow("模型", VisionModelInfo.MODEL_NAME)
            PgInfoRow("版本", VisionModelInfo.MODEL_VERSION)
            PgInfoRow("推理", "MediaPipe Tasks · LIVE_STREAM")
            PgDivider()
            Text(
                "SHA-256\n${VisionModelInfo.MODEL_SHA256}",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
            )
            Text(
                "来源\n${VisionModelInfo.SOURCE_URL}",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
            )
            PgNote("MediaPipe Tasks Vision 及该模型以 Apache License 2.0 分发。")
        }

        PgCard {
            PgSectionTitle("阈值说明")
            Text(
                "前倾、低头、肩髋角度的默认阈值均为工程默认值，用于相对你个人校准基准的变化量，" +
                    "并非医学标准。灵敏度和提醒节奏可在设置中调整。",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
            )
            PgNote("低置信度或检测不到人时会显示「检测不可用」，不会当作正常姿势。")
        }

        Text(
            text = "PostureGuard 不提供诊断、治疗或矫正方案。如持续疼痛或不适，请咨询专业医生。",
            style = MaterialTheme.typography.labelSmall,
            color = InkSoft,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
