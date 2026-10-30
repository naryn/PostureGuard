# 架构说明

## 1. 总览

PostureGuard 是单模块 Android 应用，分为纯 Kotlin 领域层、平台运行时层和 Compose UI 层。领域层不依赖 Android，可在 JVM 单元测试中直接验证；平台层负责相机、MediaPipe、Room、DataStore、前台服务与提醒；UI 只观察状态并触发控制器操作。

```
CameraX Preview ─────────────► 预览 Surface
        │
        └─ ImageAnalysis (KEEP_ONLY_LATEST, YUV)
                 │  PoseAnalyzer（后台线程，按目标 FPS 抽帧）
                 ▼
        PoseLandmarkerEngine (MediaPipe LIVE_STREAM)
                 │  PoseDetection（33 个关键点 + 延迟）
                 ▼
        PoseCameraView → CameraFrameResult（含预览镜像信息）
                 │
        MonitorController (processor 单线程)
                 ├─ PostureFeaturePipeline  关键点 → RawMetrics + 质量
                 ├─ EMA 滤波                位移/角度
                 ├─ PostureStateMachine     时间制状态 + 迟滞
                 ├─ MonitoringSessionRecorder 时长分类聚合（跨天/跨小时拆分）
                 ├─ CameraDriftDetector     手机位置变化提示
                 ├─ AlertPlanner            重复提醒与严重度
                 └─ StateFlow<MonitorState> ──► Compose 监测页
                 └─ checkpoint ──► persistence 单线程 ──► Room
                 └─ AlertDispatcher ──► ToneGenerator / TTS / Vibrator
```

## 2. 模块划分

| 包 | 职责 | 依赖 |
| --- | --- | --- |
| `core.math` | 角度、中位数、MAD、离群裁剪、坐标变换 | 无 |
| `posture.model` | 关键点、指标、阈值、校准、状态、质量 | 无 |
| `posture.features` | 特征管线、左右侧稳定性、相机漂移检测 | `core`, `posture.model` |
| `posture.filters` | 帧率无关 EMA（标量/角度） | 无 |
| `posture.calibration` | 校准样本收集、稳健统计、失败原因 | `posture.model` |
| `posture.engine` | 时间制状态机与迟滞 | `posture.model` |
| `alert` | 提醒计划（持续异常按间隔重复）与声音/语音/振动分发 | `posture.model`, Android |
| `monitoring` | 会话控制器、会话状态、时长聚合 | 领域层、数据层、提醒、相机 |
| `vision` | MediaPipe 初始化、串行推理封装、模型元数据 | MediaPipe |
| `camera` | CameraX 会话、图像分析、帧数据 | CameraX |
| `data` | Room 实体/DAO/仓库、DataStore 设置 | Room, DataStore |
| `service` | 前台相机服务与通知 | Android |
| `ui` | Compose 页面、组件、ViewModel、导航 | AndroidX, 领域层 |

## 3. 运行时与线程模型

- **主线程**：Compose 渲染、ViewModel 状态收集、控制器生命周期方法。控制器方法内部加锁并快速返回。
- **CameraX 分析线程**：`PoseAnalyzer` 解码 `ImageProxy`，按目标帧率（5/10/15 FPS）跳帧，转换成 `Bitmap` 后提交给引擎。
- **MediaPipe 结果回调线程**：单线程执行器 `pose-result`；`PoseLandmarkerEngine` 用 `AtomicBoolean` 保证同一时刻只有一个推理在途，避免并发调用原生 landmarker。
- **`monitor-frame` 单线程**：`MonitorController` 串行处理帧结果，保证状态机、滤波器和聚合器无并发访问。
- **`monitor-store` 单线程**：所有 Room 写入串行化，检查点与最终写入按提交顺序落库，避免会话写入竞争。
- **`Dispatchers.Default` 应用级协程**：1 秒计时器（更新已监测时长）与设置流收集。
- **`pose-engine-init` 线程**：`PoseEngineHost` 在后台加载模型；UI 销毁时若模型才加载完，会立即关闭，避免泄漏。

**禁止行为**：主线程不做推理、不做数据库读写；不使用无限帧缓存；不使用后台相机保活。

## 4. 生命周期

### 监测页

1. 进入监测页，等待校准 Profile；有 Profile 时自动调用 `MonitorViewModel.ensureStarted(profile, settings)`。
2. `MonitorController.start` 创建会话 ID、状态机、聚合器、提醒计划器，启动计时器，拉起前台服务。
3. CameraX 绑定页面生命周期；离开页面（`ON_STOP`）或按返回键调用 `pause()`：释放相机绑定、暂停状态机计时、保存检查点、停止前台服务。
4. 返回监测页时控制器仍为 `PAUSED`，页面显示「继续监测」，由用户手动恢复，避免隐式重启相机。
5. `stop()` 结束会话、写入 `finalized=true` 的最终记录、切换到 `FINISHED`，页面显示会话摘要。
6. 从摘要页离开后控制器保持 `FINISHED`；下次进入监测页会先 `reset()` 再开启新会话。

### 应用级

`PostureGuardApp.onCreate` 构建 `AppContainer`，并在应用协程中执行 `finalizeAbandonedSessions()`：把上次进程被杀时仍未结束的会话按 `startedAt + totalDurationMs` 标记为已结束，避免历史列表出现永远「未完成」的记录。

### 前台服务

`MonitoringService` 只负责「让监测状态可见、可从通知停止」：`ACTION_START` 调用 `ServiceCompat.startForeground(..., FOREGROUND_SERVICE_TYPE_CAMERA)`；`ACTION_STOP` 通知控制器 `stop()` 并结束服务。服务不持有相机，也不在页面不可见时继续推理，因此不存在后台偷偷采集。

## 5. 两个时钟

| 时钟 | 来源 | 用途 |
| --- | --- | --- |
| 单调时钟 | `SystemClock.elapsedRealtime()` / CameraX 帧时间戳 | 状态机持续时长、EMA 时间间隔、漂移检测、帧间隔 |
| 墙钟 | `System.currentTimeMillis()` | 会话起止、跨天/跨小时拆分、事件时间、提醒时间戳 |

分开是为了避免用户改系统时间或 NTP 校时导致持续时长跳变；同时保证历史记录使用真实日期。

## 6. 状态与数据流

`MonitorController` 暴露 `StateFlow<MonitorState>`，包含：

- `phase`：IDLE / RUNNING / PAUSED / FINISHED
- `sessionId`、`startedAtMs`、`elapsedMs`
- `snapshot`：当前姿态状态、严重度、delta、质量、持续时长、是否恢复中
- `lastAlert` / `lastAlertAtMs`、`cameraMoved`、`errorMessage`
- `effectiveFps`、`inferenceLatencyMs`、`droppedFrames`
- `calibrationProfileId`

UI 只读取该状态；所有帧处理结果在同一把锁内更新，提醒分发在锁外执行，避免在持锁期间做 I/O 或音频操作。

## 7. 持久化

Room 数据库 `posture_guard.db`，version 1：

| 表 | 内容 |
| --- | --- |
| `calibration_profiles` | 个人基准：中位数、MAD、质量、朝向、侧别、模型版本、是否激活 |
| `monitoring_sessions` | 每次监测：开始/结束、总时长、有效/正常/各类异常/低质量/暂停时长、校准 ID、是否完成 |
| `session_days` | 会话按天拆分后的聚合（主键 sessionId + date） |
| `session_hours` | 会话按小时拆分后的聚合（主键 sessionId + date + hour） |
| `posture_events` | 异常事件：类型、开始/结束、时长、严重度、峰值 delta |

写入策略：

- 运行中每 20 秒写一次检查点（`finalized=false`）。
- 每次检查点先删除该会话已有的 day/hour 行再插入新聚合，保证不会重复累计。
- 结束时写入 `finalized=true` 的最终快照，并追加尚未落库的事件。
- 日报/小时报直接对 `session_days` / `session_hours` 求和，不重复扫描会话明细。
- 应用启动时完成遗留会话；设置页提供「删除全部历史统计」「删除校准数据」。

设置使用 DataStore Preferences（`posture_guard_settings`），字段包括灵敏度、提醒开关、分析帧率、朝向、重复提醒间隔/持续时间、屏幕常亮、引导完成标记。

## 8. 依赖注入

采用手写容器，避免引入额外框架：

- `PostureGuardApp` 创建 `AppContainer`（数据库、仓库、设置、提醒分发器、`MonitorController`）。
- `AppContainer` 是应用级单例；`MonitorController` 也是应用级，保证页面重建不丢会话。
- 页面通过 `rememberAppContainer()` 获取容器，用 `simpleViewModelFactory` 创建 ViewModel。
- `AppContainer.shutdown()` 仅在测试/进程退出路径使用。

## 9. 导航

`PostureGuardRoot` 使用 Navigation Compose，路由：

`home`、`camera-setup`、`calibration`、`monitor`、`settings`、`reports`、`history`、`history/{sessionId}`、`privacy`、`about`。

校准完成后返回上一页；监测页的「结束并重新校准」会把监测路由从回退栈移除，避免回到已结束会话。监测页在 `FINISHED` 时不自动重置，防止摘要页被新会话覆盖。

## 10. 错误与降级

- 相机权限被拒绝：显示权限说明与「打开系统设置」，不启动分析。
- 相机中断：暂停会话并显示重试；不会继续累计时间。
- 模型加载失败：`PoseEngineHost` 状态为 `Unavailable`，监测页显示不可用提示，不产生虚假状态。
- 画面无人/质量不足：状态机冻结计时，统计归入「低质量 / 离开画面」，UI 明确显示不可用原因。
- 校准失败：区分样本不足、姿势不稳、左右侧不稳定、时间不足，并允许重试。
