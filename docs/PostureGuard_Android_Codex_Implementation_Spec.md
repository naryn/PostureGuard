# PostureGuard — Android 侧面坐姿检测 App 工程实施规范

> 文档版本：1.0.0  
> 项目代号：PostureGuard  
> 目标平台：Android（第一阶段）  
> 开发方式：Codex CLI / Codex Desktop 可直接执行  
> 产品形态：手机固定在桌面侧面，使用手机摄像头独立监测用户坐姿  
> 核心目标：检测头部前倾、低头、埋头、躯干前倾/驼背，并在异常持续时提醒  
> 隐私原则：默认完全本地处理，不上传视频、不保存原始视频

---

# 0. 给 Codex 的执行说明

你是本项目的 Android 技术负责人和实施工程师。请严格按照本文档建立可运行、可测试、可验收的 Android 工程，不要只输出设计说明或伪代码。

## 0.1 执行要求

1. 先检查当前仓库、Android Studio、JDK、Gradle、SDK 和运行设备环境，再开始编码。
2. 如果仓库为空，初始化项目；如果已有项目，先理解现有结构，不得无理由覆盖。
3. 先实现端到端最小闭环：摄像头预览 → 姿态推理 → 关键点绘制 → 指标计算 → 异常状态 → 本地提醒。
4. 每完成一个阶段都执行构建、静态检查和对应测试。
5. 不允许用假数据冒充真实摄像头检测结果。没有模型或模型加载失败时，必须明确显示“检测不可用”。
6. 不允许在主线程执行模型推理、图像转换或数据库批量写入。
7. 不允许无限缓存相机帧；必须丢弃过期帧，优先处理最新帧。
8. 不允许将摄像头帧、面部图像或人体关键点上传云端。
9. 所有阈值必须配置化，并标明哪些是初始工程参数、哪些需要真实用户测试标定；不得将工程阈值宣传为医学标准。
10. 不得宣称本产品能够诊断颈椎疾病、脊柱疾病或医学意义上的姿势异常。
11. 第三方依赖必须使用稳定版本并锁定版本号。禁止在正式构建中使用 `latest.release`。
12. 如果某项 Android 系统限制导致需求无法实现，必须在文档和 UI 中明确说明，不得用不可靠的后台保活技巧绕过系统限制。
13. 每个关键模块提供单元测试或集成测试；所有角度、坐标转换、异常状态逻辑都必须有可重复测试。
14. 完成后输出 `IMPLEMENTATION_STATUS.md`，记录已实现、未实现、测试结果、设备兼容性和已知问题。

## 0.2 交付优先级

- P0：摄像头实时采集、Pose Landmarker、关键点显示、基础姿态指标、状态机、提醒。
- P1：首次安装引导、摄像头位置检查、个人校准、监测控制、历史统计。
- P2：Face Landmarker 增强、详细趋势、更多设备性能优化、数据导出。
- 不在第一版范围：云端账号、社交、多人检测、医学诊断、云端视频分析、AI 聊天、订阅支付。

---

# 1. 产品定义

## 1.1 产品定位

PostureGuard 是一款 Android 本地视觉坐姿辅助应用。用户把手机固定在书桌侧面，手机后置摄像头拍摄用户侧面，应用识别人体关键点并计算姿势变化。当头部相对肩膀持续前移、头部持续向下、头部前移与低头同时出现、躯干持续前倾时，应用通过界面、声音、震动和通知提醒用户调整。

## 1.2 目标用户和场景

- 长时间使用电脑的办公人员。
- 长时间阅读、写作、学习的学生。
- 希望改善日常坐姿习惯的普通用户。

## 1.3 核心使用流程

1. 用户安装 App 并授予摄像头权限。
2. App 引导用户把手机固定在桌面侧面。
3. App 检查人体关键部位是否进入画面。
4. 用户采用自然、舒适的坐姿完成 10–20 秒校准。
5. App 保存个人基准参数。
6. 用户点击“开始监测”。
7. App 实时识别姿态并计算异常程度。
8. 异常持续超过配置时间后提醒。
9. 用户恢复自然坐姿，异常状态解除。
10. 用户查看当天和历史监测统计。

## 1.4 第一版检测范围

| 类型 | 说明 | 优先级 |
|---|---|---|
| 头部前倾 | 耳朵相对肩膀向身体前方移动 | P0 |
| 低头 | 头部相对个人基准姿态向下旋转/倾斜 | P0 |
| 埋头 | 头部前移与低头同时持续出现 | P0 |
| 躯干前倾 | 肩膀相对髋部向前移动或躯干角度持续变化 | P0 |
| 驼背趋势 | 肩部位置、躯干角度和头部补偿姿势的组合趋势 | P1 |
| 离开座位 | 关键点长时间丢失或人体离开检测区域 | P1 |
| 视线方向 | 是否持续看向桌面/手机 | P2，不能仅凭人体 Pose 判断 |

重要边界：单目侧面摄像头只能对可见姿态进行估计。它不能可靠测量脊柱内部形态，也不能仅凭“看向桌面”认定用户低头。短暂看键盘、喝水、转头等动作必须经过时间窗口与多指标判断，避免频繁误报。

---

# 2. 技术选型与工程约束

## 2.1 技术栈

| 层 | 选型 | 说明 |
|---|---|---|
| 语言 | Kotlin | Android 主语言 |
| UI | Jetpack Compose + Material 3 | 页面与状态展示 |
| 摄像头 | CameraX | Preview + ImageAnalysis |
| 姿态识别 | MediaPipe Tasks Vision / Pose Landmarker | 人体关键点 |
| 面部姿态增强 | MediaPipe Face Landmarker | 可选增强，不作为唯一依赖 |
| 图像格式 | CameraX YUV_420_888 | 尽量避免不必要的全帧 Bitmap 转换 |
| 并发 | Kotlin Coroutines / Executor | 推理与 UI 分离 |
| 本地数据库 | Room | 监测会话、统计、异常事件 |
| 设置 | DataStore Preferences | 用户设置、校准配置索引 |
| 通知 | NotificationCompat | 前台监测通知与异常通知 |
| 语音 | Android TextToSpeech | 本地语音提醒 |
| 震动 | VibratorManager / Vibrator | 设备兼容封装 |
| 测试 | JUnit、AndroidX Test、Compose UI Test | 单元与 UI 测试 |

## 2.2 Android 版本与权限

建议 `minSdk = 29`（Android 10），`targetSdk` 使用项目创建时 Google Play/Android 官方要求的当前目标版本，并在实施记录中说明。

需要的权限按实际功能最小化申请：

- `CAMERA`：相机预览和姿态检测。
- `FOREGROUND_SERVICE`：前台监测服务。
- `FOREGROUND_SERVICE_CAMERA`：目标 Android 版本要求时声明 camera 类型前台服务。
- `POST_NOTIFICATIONS`：Android 13+ 通知权限。
- `VIBRATE`：震动提醒。
- TTS 使用系统服务，不额外申请网络权限。

隐私要求：
- 第一版不申请麦克风、位置、通讯录、存储媒体等无关权限。
- 第一版原则上不需要 `INTERNET` 权限；若依赖库或后续功能确实需要，必须说明用途并重新评估。
- 仅在用户主动开启监测、且应用处于允许启动相机前台服务的状态下启动监测。
- Android 对后台启动摄像头有系统限制。不能承诺 App 被强制停止、相机权限撤销、系统回收或手机重启后仍可无感自动开启摄像头。
- 提供明确的监测状态和持续通知；用户可以随时停止。
- 屏幕关闭、锁屏、切换其他应用时的相机可用性受设备和 Android 版本限制。第一版以 App 监测页面处于前台、屏幕保持点亮为稳定主路径；可提供“监测时保持屏幕常亮”设置，并提示耗电。

## 2.3 依赖版本管理

Codex 实施时：
1. 查阅 MediaPipe Android 官方文档及 CameraX 官方文档。
2. 选择兼容的稳定版本，锁定精确版本号。
3. 记录版本、发布日期/文档来源、最低 Android 要求和模型文件版本。
4. 不使用 `latest.release`。
5. 将模型文件纳入明确的 assets 管理流程，记录 SHA-256 和来源许可。
6. 如果官方依赖在当前 Android Gradle Plugin / Kotlin 环境无法稳定构建，先建立最小验证工程，不得直接在主工程中堆叠临时补丁。

官方参考：
- Pose Landmarker Android：https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker/android
- Pose Landmarker 概览：https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker
- Face Landmarker Android：https://developers.google.com/edge/mediapipe/solutions/vision/face_landmarker/android
- CameraX Image Analysis：https://developer.android.com/media/camera/camerax/analyze

---

# 3. 总体架构

```text
┌─────────────────────────────────────────────────────────┐
│                    Android Application                  │
├─────────────────────────────────────────────────────────┤
│ UI Layer - Jetpack Compose                               │
│ Home / Live Monitor / Calibration / Settings / Reports   │
├─────────────────────────────────────────────────────────┤
│ Application Layer                                        │
│ MonitorController / CalibrationUseCase / ReportUseCase   │
├─────────────────────────────────────────────────────────┤
│ Domain Layer                                             │
│ PostureEngine / FeatureExtractor / PostureStateMachine   │
│ PersonalBaseline / AlertPolicy / QualityGate             │
├─────────────────────────────────────────────────────────┤
│ Data & Platform Layer                                    │
│ CameraX / MediaPipe Pose / Optional Face Landmarker       │
│ Room / DataStore / Notification / TTS / Vibrator          │
└─────────────────────────────────────────────────────────┘
```

## 3.1 模块边界

### Camera Module
负责摄像头生命周期、镜头选择、帧方向、帧时间戳、预览与分析流。

### Vision Module
负责 MediaPipe 初始化、模型加载、姿态推理、关键点结构映射、置信度输出。

### Posture Domain Module
不依赖 Android UI 和 CameraX。只接收归一化关键点与时间戳，输出姿态指标和判定结果。

### Calibration Module
采集用户自然舒适姿态，过滤低质量样本，计算个人基准和阈值建议。

### Alert Module
根据状态机结果决定是否提醒，控制提醒冷却、提醒方式和重复频率。

### Persistence Module
保存设置、基准、监测会话、统计聚合与异常事件。默认不保存图片和视频。

---

# 4. 推荐工程目录

```text
posture-guard/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/
│       │   │   ├── pose_landmarker_lite.task
│       │   │   └── face_landmarker.task       # P2，可选
│       │   ├── java/com/example/postureguard/
│       │   │   ├── app/
│       │   │   ├── core/
│       │   │   │   ├── common/
│       │   │   │   ├── math/
│       │   │   │   └── permissions/
│       │   │   ├── camera/
│       │   │   │   ├── CameraController.kt
│       │   │   │   ├── CameraAnalyzer.kt
│       │   │   │   ├── FrameTransform.kt
│       │   │   │   └── CameraState.kt
│       │   │   ├── vision/
│       │   │   │   ├── PoseLandmarkerProvider.kt
│       │   │   │   ├── PoseResultMapper.kt
│       │   │   │   ├── FaceLandmarkerProvider.kt
│       │   │   │   └── VisionModels.kt
│       │   │   ├── posture/
│       │   │   │   ├── model/
│       │   │   │   ├── calibration/
│       │   │   │   ├── features/
│       │   │   │   ├── filters/
│       │   │   │   ├── engine/
│       │   │   │   └── state/
│       │   │   ├── alert/
│       │   │   ├── monitoring/
│       │   │   ├── data/
│       │   │   │   ├── db/
│       │   │   │   ├── dao/
│       │   │   │   ├── entity/
│       │   │   │   └── repository/
│       │   │   ├── service/
│       │   │   ├── ui/
│       │   │   │   ├── navigation/
│       │   │   │   ├── theme/
│       │   │   │   ├── home/
│       │   │   │   ├── monitor/
│       │   │   │   ├── calibration/
│       │   │   │   ├── settings/
│       │   │   │   └── reports/
│       │   │   └── MainActivity.kt
│       │   └── res/
│       ├── test/
│       └── androidTest/
├── docs/
│   ├── ARCHITECTURE.md
│   ├── POSTURE_ALGORITHM.md
│   ├── CAMERA_SETUP.md
│   ├── PRIVACY.md
│   └── TEST_PLAN.md
├── scripts/
│   └── verify_model_assets.sh
├── IMPLEMENTATION_STATUS.md
└── README.md
```

包名先使用 `com.example.postureguard`，在正式发布前由项目负责人确定最终 applicationId。

---

# 5. 摄像头安装与画面质量检测

## 5.1 使用方式

- 使用手机后置摄像头。
- 手机横屏或竖屏均可，但第一版优先支持竖屏预览，内部统一转换坐标。
- 手机固定在用户身体正侧面，目标视角约为侧面 70°–90°。
- 手机距离用户建议先从 1.2–2.0 米测试，不作为硬性物理标准。
- 画面至少包含头部、耳朵、肩膀、髋部。
- 若要检测躯干和坐姿，可尽量包含膝盖。
- 摄像头高度以能够清晰拍到头部、肩膀和髋部为准，不应要求镜头必须与某个固定身体部位完全等高。
- 保持手机固定，摄像头发生明显移动时要求重新校准。

## 5.2 画面质量门禁

开始校准前检查：
- 是否检测到一个主要人体。
- 鼻子、至少一侧耳朵、肩膀、髋部关键点是否可见。
- 关键点 visibility / presence 是否达到质量要求。
- 人体是否过小或超出画面。
- 是否严重逆光或模糊（第一版可以先用人工提示，不强制做复杂图像质量模型）。
- 用户是否处于可识别的侧面方向。

UI 状态：
- `CAMERA_PERMISSION_REQUIRED`
- `CAMERA_INITIALIZING`
- `PERSON_NOT_FOUND`
- `BODY_PARTS_OUT_OF_FRAME`
- `LOW_LANDMARK_CONFIDENCE`
- `CAMERA_MOVED_RECALIBRATION_REQUIRED`
- `READY_FOR_CALIBRATION`
- `MONITORING`

## 5.3 摄像头流配置

- CameraX Preview + ImageAnalysis。
- 初始分析分辨率建议 640×480 或 720×480；以设备实测为准。
- 目标分析频率 10–15 FPS，性能不足时允许自动降到 5–10 FPS。
- UI 预览可以使用设备支持的 720p 或更高分辨率，但推理不必使用完整预览分辨率。
- `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST`。
- 每帧处理完毕必须调用 `ImageProxy.close()`，包括异常路径。
- 推理在单独 Executor 上串行处理；不得并发向同一个 Landmarker 实例提交不可控帧。
- 正确处理 `rotationDegrees`、镜像和裁剪区域。
- 维护原始帧坐标、模型输入坐标、预览坐标之间的显式转换函数。
- 不允许在多个模块中重复实现坐标翻转。

---

# 6. 姿态识别模型与关键点

## 6.1 Pose Landmarker

第一版使用 MediaPipe Pose Landmarker Lite 作为起始模型，先验证侧面关键点质量，再根据实测决定是否升级 Full。

重点关键点：
- `NOSE`
- `LEFT_EAR` / `RIGHT_EAR`
- `LEFT_EYE` / `RIGHT_EYE`（如结果可用）
- `LEFT_SHOULDER` / `RIGHT_SHOULDER`
- `LEFT_HIP` / `RIGHT_HIP`
- `LEFT_KNEE` / `RIGHT_KNEE`

侧面模式应自动选择可见性更高的一侧作为分析侧：
1. 比较左右耳朵 visibility/presence。
2. 比较左右肩膀 visibility/presence。
3. 比较左右髋部 visibility/presence。
4. 结合用户在引导页选择的朝向（面向画面左侧/右侧）。
5. 如果左右侧可见性频繁切换，标记质量不稳定，不直接产生强提醒。

不得简单假设 `LEFT` 一定是屏幕左侧。人体左右是被检测对象自身的解剖左右，镜像预览可能改变屏幕显示方向。

## 6.2 Face Landmarker（增强能力）

P2 阶段可启用 Face Landmarker：
- 使用面部 landmarks 和 transformation matrix 估算头部姿态变化。
- 只在人体头部区域质量足够时运行。
- 对接近纯侧脸、遮挡、眼镜反光、低光等情况进行失效处理。
- 将头部俯仰估计作为增强信号，而不是唯一低头判据。
- 若面部姿态不可用，Pose 几何规则仍能继续工作。

第一版可先不同时运行两个模型。应先测量 Pose 单模型的性能、准确率和功耗，再决定是否启用 Face Landmarker。

---

# 7. 姿态算法规范

这是项目核心。必须把算法做成纯 Kotlin 可测试模块，不能把姿态判断散落在 Compose、Camera Analyzer 或 ViewModel 中。

## 7.1 坐标统一

输入关键点统一为归一化坐标：
- `x`：左到右，范围通常为 0–1。
- `y`：上到下，范围通常为 0–1。
- `z`：模型估计深度，不能直接当作真实物理距离。
- `visibility` / `presence`：关键点质量分数，按实际 API 输出映射。

先统一旋转、镜像、方向与用户朝向。定义身体前方的符号：
- 用户面向画面右侧时，画面右侧为前方。
- 用户面向画面左侧时，画面左侧为前方。
- 所有前移指标都转换为“数值越大越向身体前方”。

禁止直接将屏幕 x 增大等同于身体前方。

## 7.2 关键几何量

设：
- `E`：可见侧耳朵坐标。
- `N`：鼻尖坐标。
- `S`：可见侧肩膀坐标。
- `H`：可见侧髋部坐标。
- `K`：可见侧膝盖坐标。

躯干尺度：
`torsoScale = distance(S, H)`

如果 `torsoScale` 太小、关键点质量不足或接近零，本帧无效。

### A. 头部相对前移（Forward Head）

计算身体前方方向上的耳朵—肩膀投影距离：

`forwardOffset = dot(E - S, forwardAxis) / torsoScale`

以个人校准基准 `baseline.forwardOffset` 为参照：

`forwardHeadDelta = forwardOffset - baseline.forwardOffset`

含义：正值越大，头部相对肩膀越向身体前方移动。

注意：
- 使用归一化躯干尺度，减少距离和体型变化影响。
- 不单独用固定像素距离判断。
- 头部前倾要有关键点质量门禁。
- 用户转头或摄像头方向改变时，该指标可能失真。

### B. 头部向下（Head Down）

第一版基础方案使用头部向量与个人基准比较：
- 头部向量可先使用 `N - E`。
- 计算其相对水平轴的有符号角度。
- 统一坐标系后，计算当前角度相对基准的变化量。
- 不将鼻尖 y 坐标直接当作低头角度。
- 不将 Face Landmarker 缺失时的结果伪装成精确 Pitch。

公式实现必须处理角度跨越 ±180° 的情况，使用 wrap/shortest-angle 差值。

如果启用 Face Landmarker：
- 优先使用官方输出的 facial transformation matrix / 头部姿态估计。
- 在真实设备上标定 pitch 符号和坐标约定。
- 只在面部检测质量达标时融合。
- 将 Pose 头部向量与 Face pitch 做置信度加权或规则融合。
- 当二者冲突明显时降低结论置信度，不产生强提醒。

### C. 躯干前倾（Torso Lean）

使用肩膀到髋部向量：
`torsoVector = S - H`

计算其相对竖直方向的有符号角度 `torsoAngle`。

与个人基准比较：
`torsoLeanDelta = shortestAngleDelta(torsoAngle, baseline.torsoAngle)`

只判断相对个人基准的持续变化，不把统一绝对角度作为医学标准。

### D. 埋头（Head Down + Forward）

埋头属于复合状态，不是一个单独关键点：
- `forwardHeadDelta` 超过用户配置的前移阈值；
- `headDownDelta` 超过低头阈值；
- 两项关键指标同时成立并持续达到时间要求；
- 关键点质量稳定。

若仅头部前移，不判为埋头。
若仅短暂低头，不判为埋头。
若质量不足，状态应为 UNKNOWN/LOW_CONFIDENCE。

### E. 驼背趋势（Slouch）

第一版不宣称识别医学意义上的驼背。定义为“躯干前倾/肩部前移的姿态趋势”：
- 躯干角度相对基准持续变化；
- 肩膀相对髋部的前移趋势；
- 可结合头部前移作为辅助证据。
多个信号持续出现才判为躯干姿态异常。

## 7.3 个人校准流程

校准时长：建议 15 秒，允许 10–20 秒配置。

步骤：
1. UI 显示摄像头预览和人体关键点。
2. 检查头部、耳朵、肩膀、髋部质量。
3. 提示用户以舒适、自然、不刻意挺胸或仰头的坐姿坐好。
4. 前 2 秒作为稳定时间，不计入样本。
5. 采集剩余有效帧的特征。
6. 丢弃低置信度、明显动作帧和离群值。
7. 使用中位数或截尾均值计算基准。
8. 计算样本离散程度（MAD / IQR），评估基准可靠性。
9. 如果有效帧比例不足或姿态波动过大，提示重新校准。
10. 保存校准数据、摄像头方向、分析侧、模型版本和时间。

基准至少包括：
- `forwardOffsetMedian`
- `headAngleMedian`
- `torsoAngleMedian`
- `torsoScaleMedian`
- `landmarkQualityMedian`
- `forwardOffsetMad`
- `headAngleMad`
- `torsoAngleMad`
- `cameraFacingDirection`
- `selectedBodySide`
- `modelVersion`
- `calibratedAt`

校准不是医学上的“正确姿势认证”，而是用户本人舒适坐姿的个性化参考。允许用户重新校准和删除校准数据。

## 7.4 阈值策略

阈值不写死在算法中。建立 `PostureThresholdConfig`：
- `forwardHeadMild`
- `forwardHeadSevere`
- `headDownMild`
- `headDownSevere`
- `torsoLeanMild`
- `torsoLeanSevere`
- `mildHoldDurationMs`
- `severeHoldDurationMs`
- `recoveryHoldDurationMs`
- `alertCooldownMs`
- `lowConfidenceTimeoutMs`

初始工程阈值需要在真实设备上通过录制测试数据标定。不要在未测试前把任何数值称为科学或医学阈值。

阈值应考虑个人校准离散程度：
- 基准噪声越大，不能简单把阈值设得越敏感。
- 设置最小安全阈值，避免轻微模型抖动触发。
- 提供“灵敏 / 均衡 / 宽松”预设，但预设必须映射到清晰可调参数。
- 高级设置可开放具体参数，默认用户不必理解角度公式。

## 7.5 时序平滑

至少实现：
- EMA 平滑。
- 有效关键点质量门禁。
- 时间窗口投票或连续帧比例判断。
- 异常进入/退出迟滞（hysteresis）。

示例：
- 每个特征先做 EMA。
- 最近 N 秒内，异常帧比例达到设定比例才进入异常状态。
- 恢复时使用不同阈值或更长的恢复时间，避免状态反复跳变。
- 低置信度时暂停累计异常时间，不能把丢失关键点算成不良姿势。

EMA：
`smoothed = alpha * current + (1 - alpha) * previous`

`alpha` 必须可配置，并按采样时间差调整或采用固定采样间隔，避免帧率变化导致滤波特性变化。

---

# 8. 姿态状态机

## 8.1 状态定义

```kotlin
enum class PostureState {
    NOT_READY,
    NORMAL,
    SUSPECTED_FORWARD_HEAD,
    FORWARD_HEAD,
    SUSPECTED_HEAD_DOWN,
    HEAD_DOWN,
    SUSPECTED_HEAD_DOWN_FORWARD,
    HEAD_DOWN_FORWARD,
    SUSPECTED_TORSO_LEAN,
    TORSO_LEAN,
    LOW_CONFIDENCE,
    PERSON_NOT_FOUND,
    PAUSED
}
```

实现时可以用更合理的状态模型，但必须保证 UI 能明确展示当前状态、主要原因和质量情况。

## 8.2 状态转换原则

- `NORMAL` → 轻微异常：进入 `SUSPECTED_*`，开始累计时间。
- 持续满足条件：进入正式异常状态。
- 严重异常：允许使用更短持续时间，但仍需关键点质量有效。
- 多种异常同时存在：按照复合状态优先级展示，例如埋头高于单独前倾。
- 指标恢复：先进入恢复观察，不立即清除。
- 恢复稳定达到配置时间：回到 `NORMAL`。
- 关键点丢失：进入 `LOW_CONFIDENCE` 或 `PERSON_NOT_FOUND`，停止异常累计。
- 用户暂停：进入 `PAUSED`，停止推理或停止相机分析。

## 8.3 异常优先级

建议：
1. `HEAD_DOWN_FORWARD`：埋头（复合异常）
2. `HEAD_DOWN`：低头
3. `FORWARD_HEAD`：头部前倾
4. `TORSO_LEAN`：躯干前倾
5. `NORMAL`

可以同时保存多个指标，但 UI 主提示只展示一个最主要提醒，避免连续弹出多条消息。

---

# 9. 提醒系统

## 9.1 提醒类型

- UI 状态变化。
- 轻提示音。
- Android TTS 语音。
- 震动。
- 本地通知（适用时）。

文案示例：
- “注意抬头，放松颈部。”
- “头部有些前倾，试着回到舒适坐姿。”
- “身体持续前倾，可以调整一下坐姿。”
- “已经保持较久，起来活动一下。”

避免命令式、羞辱性或制造焦虑的文案。

## 9.2 提醒规则

- 异常刚出现：仅 UI 状态变化，不立即打断。
- 轻度异常持续达到阈值：轻提示。
- 严重异常持续达到阈值：可立即提示，但需要冷却控制。
- 同一种异常进入冷却期后不重复提醒。
- 状态恢复后重置对应异常计时。
- 监测暂停、关键点丢失、用户离开画面时不发送姿势异常提醒。
- 用户可以关闭语音、声音、震动，单独设置提醒间隔。

## 9.3 AlertPolicy 配置

```kotlin
data class AlertPolicy(
    val soundEnabled: Boolean,
    val speechEnabled: Boolean,
    val vibrationEnabled: Boolean,
    val mildHoldDurationMs: Long,
    val severeHoldDurationMs: Long,
    val cooldownMs: Long,
    val recoveryHoldDurationMs: Long
)
```

---

# 10. 页面与交互设计

视觉方向：
- 浅色系为主。
- 淡蓝、浅青为主色，少量薄荷绿或薰衣草紫辅助。
- 简洁、清晰、工具型产品，不要过度医疗化。
- 重要状态使用清晰的文字和图标，不能只依赖颜色。
- 页面适配常见 Android 手机尺寸和字体缩放。

## 10.1 首页

展示：
- 产品名称和一句说明。
- 今日监测概览。
- 当前校准状态。
- 开始监测按钮。
- 摄像头位置引导入口。
- 今日正常时间、异常次数、异常累计时长。
- 设置入口。

未完成校准时，“开始监测”应引导先校准。

## 10.2 实时监测页

这是最重要的页面。

布局：
1. 顶部：监测状态、运行时长、暂停/结束。
2. 中间：摄像头预览。
3. 预览覆盖层：人体关键点、骨骼连线、耳朵/肩膀/髋部标记。
4. 侧边或下方：头部前移、低头变化、躯干倾角。
5. 主状态卡：正常 / 头部前倾 / 低头 / 埋头 / 躯干前倾 / 检测质量不足。
6. 底部：提醒开关、重新校准、结束监测。

必须在画面中明确说明：
- 预览是否镜像。
- 检测是否有效。
- 当前是否正在本地处理。
- 关键点质量不足时不要显示“正常”。

## 10.3 摄像头位置引导页

用图文说明：
- 手机固定在桌面侧面。
- 后置摄像头朝向用户。
- 头、肩、髋必须在画面中。
- 保持手机稳定。
- 避免强背光。
- 选择“面向左侧”或“面向右侧”。
- 提供预览画面与关键点可见状态。

## 10.4 坐姿校准页

- 展示实时预览。
- 显示当前有效关键点。
- 提示“以自然舒适的坐姿坐好，不要刻意挺胸或仰头”。
- 进度条 0–100%。
- 显示有效样本比例。
- 校准成功后显示基准质量：良好 / 需要重试。
- 允许取消和重试。
- 校准完成后提供“查看基准指标”，但普通用户无需手动设置复杂角度。

## 10.5 设置页

设置分组：
- 监测：分析帧率（自动/省电/流畅）、预览分辨率、监测时保持屏幕常亮。
- 提醒：声音、语音、震动、异常持续时间、提醒冷却时间。
- 灵敏度：宽松、均衡、灵敏。
- 隐私：本地处理说明、删除历史、删除校准数据。
- 关于：模型版本、App 版本、开源许可。

## 10.6 统计页

展示：
- 今日监测时长。
- 正常姿势观察时长。
- 前倾累计时长。
- 低头累计时长。
- 埋头累计时长。
- 躯干前倾累计时长。
- 异常事件次数。
- 每小时分布。
- 最近 7 天趋势。

注意：统计中的“正常时长”应仅累计检测质量有效且状态正常的时间。低置信度、暂停、离开画面时间应单独统计，不能计为正常。

## 10.7 历史详情页

- 按日期查看监测会话。
- 查看每种异常持续时长。
- 查看事件时间段。
- 不保存视频作为事件证据。
- 支持删除单条会话或全部历史数据。

---

# 11. 监测生命周期与 Android 前台服务

## 11.1 主路径

第一版推荐监测页作为主运行页面：
- 用户打开 App。
- 进入实时监测页。
- 用户主动点击开始。
- 在允许的生命周期状态下启动相机和监测。
- 显示持续通知。
- 用户暂停或结束时释放相机与推理资源。

如果使用 Camera 前台服务：
- 按目标 Android SDK 的要求声明服务类型和权限。
- 只从允许的可见 UI 状态启动。
- 正确处理 Android 版本差异。
- 在通知中提供返回 App / 停止监测操作。
- 不承诺应用被系统杀死后可自动恢复相机。
- 用户主动结束、权限撤销或系统停止服务时清理资源。

## 11.2 屏幕常亮

提供可选“监测期间保持屏幕常亮”：
- 默认关闭或在首次监测时询问。
- 开启后使用窗口 keepScreenOn。
- 结束监测时恢复。
- 明确提示持续亮屏会增加耗电和发热。

## 11.3 生命周期清理

必须在以下情况安全释放：
- 用户点击结束。
- Activity/Service 生命周期结束。
- 摄像头权限撤销。
- 摄像头被其他应用占用。
- Landmarker 初始化失败。
- 模型加载失败。
- 手机进入不支持的相机状态。
- App 崩溃恢复或服务被系统停止。

`ImageProxy.close()` 必须在 finally 路径执行。

---

# 12. 性能与功耗设计

## 12.1 第一版目标（验收目标，不是未经测试的承诺）

| 指标 | 目标 |
|---|---|
| 姿态分析帧率 | 10 FPS 起步，目标 10–15 FPS |
| UI 预览 | 设备支持范围内流畅显示 |
| 单帧推理 | 记录 P50/P95，不先假定固定数值 |
| 内存 | 连续监测 30 分钟无持续增长 |
| 稳定性 | 连续运行 60 分钟无崩溃 |
| 温控 | 记录设备温度/热状态，不持续热降频 |
| 相机队列 | 不积压旧帧 |
| 视频存储 | 默认 0 |
| 网络上传 | 默认 0 |

## 12.2 性能优化顺序

1. 先用 Pose Lite 和 640×480 分析流建立基准。
2. 使用 `KEEP_ONLY_LATEST`。
3. 控制推理频率，不必每个摄像头帧都推理。
4. 先处理 Pose 单模型。
5. 只有 Pose 头部姿态不足以区分低头时，再评估 Face Landmarker。
6. 通过设备实测比较 Lite / Full。
7. 检查 YUV 到模型输入的转换成本。
8. 避免每帧创建大量临时 Bitmap、Canvas、Path 和对象。
9. 关键点 UI 只在新推理结果到达时更新。
10. 根据热状态动态降低推理帧率。

## 12.3 性能日志

只记录非图像数据：
- timestamp
- inferenceLatencyMs
- frameIntervalMs
- validLandmarkRatio
- droppedFrameCount
- modelName
- deviceModel
- thermalStatus（系统允许时）
- appVersion

默认不记录任何原始图像。

---

# 13. 数据模型

## 13.1 CalibrationProfile

```kotlin
data class CalibrationProfile(
    val id: String,
    val createdAt: Long,
    val cameraFacingDirection: String,
    val selectedBodySide: String,
    val forwardOffsetMedian: Float,
    val headAngleMedian: Float,
    val torsoAngleMedian: Float,
    val torsoScaleMedian: Float,
    val forwardOffsetMad: Float,
    val headAngleMad: Float,
    val torsoAngleMad: Float,
    val qualityMedian: Float,
    val modelVersion: String,
    val isActive: Boolean
)
```

## 13.2 MonitoringSession

```kotlin
data class MonitoringSession(
    val id: String,
    val startedAt: Long,
    val endedAt: Long?,
    val totalDurationMs: Long,
    val validDurationMs: Long,
    val normalDurationMs: Long,
    val lowConfidenceDurationMs: Long,
    val pausedDurationMs: Long,
    val calibrationProfileId: String
)
```

## 13.3 PostureEvent

```kotlin
data class PostureEvent(
    val id: String,
    val sessionId: String,
    val type: String,
    val startedAt: Long,
    val endedAt: Long?,
    val durationMs: Long,
    val severity: String,
    val peakForwardDelta: Float?,
    val peakHeadDownDelta: Float?,
    val peakTorsoLeanDelta: Float?
)
```

## 13.4 DailySummary

```kotlin
data class DailySummary(
    val date: String,
    val monitoredDurationMs: Long,
    val validDurationMs: Long,
    val normalDurationMs: Long,
    val forwardHeadDurationMs: Long,
    val headDownDurationMs: Long,
    val headDownForwardDurationMs: Long,
    val torsoLeanDurationMs: Long,
    val lowConfidenceDurationMs: Long,
    val eventCount: Int
)
```

统计规则：
- 事件时间不能重复累计。
- 复合埋头事件可以作为单独类别展示，但总异常时长计算要定义互斥口径，避免重复加总。
- 所有时间使用同一时区规则，数据库建议保存 epoch millis，展示层按本地时区格式化。
- 监测跨午夜时，日报聚合必须正确拆分到对应日期。

---

# 14. 隐私与安全

1. 摄像头只用于实时姿态检测。
2. 默认不保存图片或视频。
3. 默认不上传人体关键点。
4. 默认不请求网络权限。
5. 校准数据只保存在本机。
6. 用户可删除校准数据和全部统计。
7. UI 明确说明摄像头正在工作。
8. 相机权限被撤销后立即停止监测。
9. 日志不得输出原始图像、面部网格或可识别个人的内容。
10. 不做身份识别、人脸比对或用户追踪。
11. 提供隐私说明页面。
12. 不把姿势评分描述为健康诊断结果。

---

# 15. 测试与验收计划

## 15.1 单元测试

必须覆盖：

### 坐标与几何
- 左向/右向身体前方轴转换。
- 镜像前后坐标一致性。
- 旋转 0/90/180/270 度坐标转换。
- 向量长度、点积、角度计算。
- 角度跨 ±180° 的差值。
- 躯干尺度为 0 或极小时的保护。
- 关键点缺失和低置信度处理。

### 校准
- 正常稳定样本可以生成基准。
- 大量离群样本被过滤。
- 有效样本不足时失败。
- 基准离散度过大时提示重试。
- 左右朝向和身体侧选择正确。
- 更换模型版本时基准兼容性策略明确。

### 姿态引擎
- 头部前移触发前倾候选。
- 低头角度变化触发低头候选。
- 前移 + 低头同时出现触发埋头候选。
- 单独短暂低头不触发持续异常。
- 关键点质量不足不触发异常。
- 身体离开画面不被判为正常。
- 恢复状态具备迟滞。
- 不同帧率下持续时间判断一致。

### 提醒
- 持续时间达到阈值才提醒。
- 冷却期内不重复提醒。
- 恢复后重新计时。
- 暂停和低置信度不提醒。
- 用户关闭某提醒渠道后不调用该渠道。

## 15.2 集成测试

- 真机摄像头权限申请。
- CameraX Preview 正常。
- ImageAnalysis 正常。
- Pose 模型正常加载。
- 模型加载失败显示错误。
- 旋转与镜像显示正确。
- 预览骨骼位置与实际人体对齐。
- 监测开始/暂停/恢复/结束。
- 前台通知正确显示。
- 结束后摄像头指示状态消失。
- 进程重建后不错误恢复相机。
- Room 数据保存和日报聚合正确。

## 15.3 场景测试矩阵

至少准备以下真人测试场景，每种场景记录视频供开发测试（测试素材必须由测试人员知情同意；素材仅在本地测试环境使用，发布版本不保留）：

| 场景 | 期望结果 |
|---|---|
| 自然舒适坐姿 | 稳定正常 |
| 头部缓慢前移 | 识别头部前倾 |
| 头部明显向下 | 识别低头 |
| 前移并低头 | 识别埋头 |
| 整个躯干前倾 | 识别躯干前倾 |
| 短暂看键盘 | 不应频繁提醒 |
| 短暂喝水 | 不应频繁提醒 |
| 转头看旁边 | 低置信度或短暂忽略 |
| 手托下巴 | 不应轻易判为埋头 |
| 伸手拿东西 | 不应永久改变个人基准 |
| 离开座位 | 显示未检测到人体 |
| 侧脸被遮挡 | 降低置信度 |
| 摄像头轻微移动 | 提示检查/重新校准 |
| 暗光 | 降低置信度，不错误判正常 |
| 不同身高和体型 | 通过个人校准适配 |

## 15.4 验收标准

P0 验收：
- 真机安装运行。
- 摄像头画面正常。
- 可看到关键点和骨骼覆盖层。
- 关键点坐标与预览画面准确对齐。
- 能输出头部前移、低头、躯干倾角等指标。
- 能通过个人校准生成基准。
- 能区分正常、前倾、低头、埋头、躯干前倾。
- 有持续时间判断和提醒冷却。
- 关键点丢失时显示检测质量不足，而不是正常。
- 默认不保存视频、不上传图像。
- 连续运行 30 分钟无崩溃，无明显内存持续增长。

P1 验收：
- 六个页面完成。
- Room 统计正常。
- 历史报告正常。
- 提醒设置生效。
- 前台通知与生命周期处理符合 Android 限制。

P2 验收：
- Face Landmarker 可选增强。
- 对比 Pose-only 与 Pose+Face 的准确率、延迟、温度和耗电。
- 只有增强效果经过真实测试后才默认启用。

---

# 16. 开发阶段与任务拆解

## Phase 0：环境和依赖验证

任务：
- 检查 Android SDK、JDK、Gradle、AGP。
- 创建最小 Kotlin 工程。
- 验证 CameraX。
- 验证 MediaPipe Pose 模型加载。
- 在目标 Android 真机上完成一张图片和实时视频推理。
- 记录模型大小、加载时间、单帧延迟。
- 输出 `docs/ENVIRONMENT_VALIDATION.md`。

交付：能启动的最小验证 App。

## Phase 1：摄像头与关键点 Demo

任务：
- CameraX Preview。
- ImageAnalysis。
- YUV/Bitmap 输入转换。
- 旋转、镜像和坐标映射。
- Pose Landmarker LIVE_STREAM。
- 骨骼关键点覆盖层。
- 关键点质量提示。
- 性能日志。

交付：实时摄像头姿态识别 Demo。

## Phase 2：姿态几何引擎

任务：
- 统一坐标系统。
- 身体前方方向。
- 耳朵—肩膀前移量。
- 头部向量角度。
- 肩膀—髋部躯干角度。
- EMA。
- 质量门禁。
- 单元测试。

交付：纯 Kotlin PostureEngine。

## Phase 3：个性化校准与状态机

任务：
- 校准引导。
- 有效帧采样。
- 中位数/MAD。
- 阈值配置。
- 异常持续时间。
- 复合埋头状态。
- 恢复迟滞。
- 状态机测试。

交付：可在真机上区分主要姿势的检测引擎。

## Phase 4：提醒和监测生命周期

任务：
- 声音提醒。
- TTS。
- 震动。
- 提醒冷却。
- 监测开始/暂停/结束。
- 前台通知。
- Android 生命周期和权限处理。
- 屏幕常亮设置。

交付：可持续使用的本地监测闭环。

## Phase 5：产品 UI 与数据统计

任务：
- 首页。
- 摄像头引导。
- 校准页。
- 实时监测页。
- 设置页。
- 统计页。
- 历史页。
- Room 数据层。
- DataStore 设置层。

交付：完整第一版 Android App。

## Phase 6：真机验证和性能优化

任务：
- 不同品牌 Android 真机测试。
- 不同身高和体型测试。
- 左右朝向测试。
- 不同光线测试。
- 短暂动作误报测试。
- 30–60 分钟稳定性测试。
- 热状态和耗电观察。
- 修复误报与漏报。
- 输出测试报告。

交付：Release APK / AAB、测试报告、已知问题清单。

---

# 17. Codex 每阶段执行约束

每个 Phase 完成后必须：
1. 更新 `IMPLEMENTATION_STATUS.md`。
2. 运行 `./gradlew test`。
3. 运行 `./gradlew lint`。
4. 能运行真机测试时执行对应 instrumentation tests。
5. 输出新增文件清单。
6. 输出构建结果和测试结果。
7. 明确未完成项。
8. 不得把未通过的测试标记为通过。
9. 不得未经确认跳过模型、相机、算法验证，直接开始堆 UI。
10. 如果当前环境没有 Android 真机，必须提供可运行的模拟数据单元测试，但不能声称真机摄像头验收通过。

## 17.1 每阶段 Git 提交建议

- `chore: initialize android project`
- `feat: add camera preview and analyzer`
- `feat: integrate pose landmarker`
- `feat: add posture geometry engine`
- `feat: add calibration and posture state machine`
- `feat: add local posture alerts`
- `feat: add monitoring ui and reports`
- `test: add device posture validation`

---

# 18. README 必须说明

- 项目用途。
- Android Studio 打开方式。
- JDK/SDK 要求。
- 模型文件获取与校验方式。
- 如何运行。
- 如何授权摄像头。
- 如何固定手机。
- 如何校准。
- 如何开始/结束监测。
- 隐私说明。
- 已知限制。
- 如何运行单元测试。
- 如何生成 APK。

---

# 19. 已知技术风险与处理策略

## 风险 1：纯侧脸关键点不稳定
处理：
- 真实设备先测试 Pose Lite。
- 优先选择可见性更好的身体侧。
- 侧脸条件不佳时提示调整手机角度。
- Face Landmarker 只作为增强，不作为唯一判据。

## 风险 2：看键盘被误判为低头
处理：
- 低头需要持续时间。
- 结合头部前移、躯干角度和头部姿态。
- 短时动作不触发强提醒。
- 使用迟滞和冷却。

## 风险 3：摄像头角度变化
处理：
- 保存校准时的身体朝向和几何基准。
- 监测关键点整体关系是否异常突变。
- 怀疑相机移动时显示“请检查手机位置/重新校准”。
- 不要把摄像头移动误判为用户姿势突然变好或变坏。

## 风险 4：不同人体比例
处理：
- 用躯干尺度归一化。
- 使用个人校准。
- 使用关键点置信度。
- 不依赖固定像素距离。

## 风险 5：长时间监测功耗
处理：
- 控制推理帧率。
- 使用 Lite 模型作为起点。
- 动态降帧。
- 默认不同时运行多个重模型。
- 提供监测时保持屏幕常亮开关。
- 记录真实设备功耗和热状态。

## 风险 6：Android 后台摄像头限制
处理：
- 第一版将前台监测页面作为可靠主流程。
- 遵循 Camera Foreground Service 规则。
- 不宣称锁屏、强杀或重启后可以无感持续拍摄。
- 明确显示持续通知和停止入口。

## 风险 7：姿势分数被误解为医学结论
处理：
- 使用“姿势状态/个人基准偏移”表述。
- 不给疾病风险诊断。
- 不将一个分数作为健康评价。
- 允许用户关闭评分，仅使用状态提醒。

---

# 20. 最终交付清单

Codex 完成后必须交付：

- [ ] Android Studio 可打开工程。
- [ ] 可安装 APK。
- [ ] 完整 Kotlin 源码。
- [ ] CameraX 实时预览。
- [ ] MediaPipe Pose 实时关键点。
- [ ] 坐标映射和骨骼覆盖层。
- [ ] 姿态几何算法。
- [ ] 个人坐姿校准。
- [ ] 头部前倾检测。
- [ ] 低头检测。
- [ ] 埋头检测。
- [ ] 躯干前倾检测。
- [ ] 状态机与防抖。
- [ ] 声音、语音、震动提醒。
- [ ] 本地统计和历史。
- [ ] 隐私说明。
- [ ] 单元测试和集成测试。
- [ ] 真机性能测试报告。
- [ ] `IMPLEMENTATION_STATUS.md`。
- [ ] README 和部署说明。

---

# 21. 第一版产品成功标准

第一版不要以“页面数量”作为成功标准，而以真实使用效果作为标准：

1. 手机放在桌面侧面，用户正常坐着时能够稳定识别人体关键点。
2. 用户完成一次校准后，系统能建立个人基准。
3. 用户主动将头部向前移动时，前倾指标能够明显变化。
4. 用户低头时，低头指标能够明显变化。
5. 用户同时前移和低头时，系统能够识别复合埋头状态。
6. 用户短暂看键盘、喝水、转头时，不应频繁触发提醒。
7. 用户离开画面或关键点丢失时，系统明确显示检测不可用。
8. 连续运行时相机、模型、UI 和提醒不会互相阻塞。
9. 监测过程默认完全本地化，不保存原始视频。
10. 真实设备测试结果、误报案例和漏报案例都有记录。

**开发原则：先把侧面坐姿识别做准，再把提醒和统计做完整。不要先做一个漂亮但判断不可靠的 App。**
