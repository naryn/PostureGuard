package com.example.postureguard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.example.postureguard.ui.components.PageScaffold
import com.example.postureguard.ui.components.PgCard
import com.example.postureguard.ui.components.PgDivider
import com.example.postureguard.ui.components.PgInfoRow
import com.example.postureguard.ui.components.PgNote
import com.example.postureguard.ui.components.PgSectionTitle
import com.example.postureguard.ui.theme.Ink
import com.example.postureguard.ui.theme.InkSoft
import com.example.postureguard.ui.theme.MintPale
import com.example.postureguard.ui.theme.TealDeep
import com.example.postureguard.vision.VisionModelInfo

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    PageScaffold(
        title = "隐私说明",
        subtitle = "摄像头画面只在本机分析，不保存、不上传",
        onBack = onBack,
    ) {
        PgCard {
            PgSectionTitle("画面如何处理")
            Text(
                "相机画面以只保留最新一帧的方式送入本机 MediaPipe 姿态模型。" +
                    "分析得到的肩、耳、髋等关键点只用于计算角度和时长，" +
                    "处理完立即丢弃，不写入相册、文件或任何网络请求。",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
            )
            PgNote(
                text = "应用未申请网络权限，因此无法向外发送画面或关键点数据。",
                containerColor = MintPale,
                contentColor = TealDeep,
            )
        }

        PgCard {
            PgSectionTitle("本机保存的内容")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("· 个人校准的参考角度与波动范围（中位数 / MAD）", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                Text("· 每次监测的总时长、正常 / 异常 / 低置信度时长分段", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                Text("· 异常事件的开始、结束时间与峰值偏差", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                Text("· 提醒开关、灵敏度等设置项", style = MaterialTheme.typography.bodySmall, color = InkSoft)
            }
            PgDivider()
            Text(
                "以上内容仅存于应用私有数据库，用于生成统计与历史记录。不包含任何图像、视频或原始关键点序列。",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
            )
        }

        PgCard {
            PgSectionTitle("权限用途")
            PgInfoRow("相机", "仅在监测、校准和摆放引导页前台使用")
            PgDivider()
            PgInfoRow("通知", "监测时显示前台服务状态")
            PgDivider()
            PgInfoRow("振动", "姿态提醒（可关闭）")
            PgNote("离开监测页或锁屏后相机会释放，不存在后台偷偷采集。")
        }

        PgCard {
            PgSectionTitle("你的控制权")
            Text(
                "在「设置 → 数据与隐私」中可以随时清空历史记录或删除校准基准。" +
                    "卸载应用会一并删除本机数据库与设置。",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
            )
        }

        PgCard {
            PgSectionTitle("本地模型")
            PgInfoRow("模型", VisionModelInfo.MODEL_NAME)
            PgInfoRow("版本", VisionModelInfo.MODEL_VERSION)
            PgNote("模型文件随安装包分发，可校验 SHA-256：${VisionModelInfo.MODEL_SHA256}")
        }

        Text(
            text = "PostureGuard 不提供医学诊断。阈值均为工程默认值，可按个人情况调整。",
            style = MaterialTheme.typography.labelSmall,
            color = InkSoft,
        )
    }
}
