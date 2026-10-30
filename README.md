# PostureGuard

PostureGuard 是一个完全本地运行的 Android 侧坐姿提醒应用：把手机固定在书桌侧面，用后置摄像头观察头、肩、髋的相对位置，检测**头部前倾、低头、埋头（低头 + 前移）和躯干前倾**，按时间阈值给出声音、语音或振动提醒。

> 定位是日常坐姿自我观察工具，不是医疗器械，也不提供诊断、治疗或矫正方案。所有阈值都是工程默认值。

**当前状态**：软件功能已实现，构建、61 个单元测试、3 个设备测试与 Lint 均通过；**真机摄像头验收与真人准确率尚未完成**，详见 [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md) 与 [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md)。

## 主要特性

- **只在本机处理**：CameraX 画面直接送入本机 MediaPipe Pose Landmarker，不保存图片/视频，也不上传。应用未申请网络权限。
- **不伪造检测**：模型未加载、关键点质量不足或画面中没有人时，界面显示「检测不可用 / 未检测到人体 / 检测质量不足」，绝不会当作正常姿势累计。
- **个人校准**：以本人自然坐姿建立中位数 / MAD 基准，阈值按基准噪声自动抬高，减少个体差异。
- **时间制状态机**：轻度异常需持续 8 秒、明显异常 3 秒后确认，恢复也需 4 秒，避免抖动误报；提醒有 2 分钟冷却。
- **诚实的统计口径**：只有质量有效的时段才计入「正常 / 异常」，低置信度、暂停、离开画面单独统计，不混入正常时长。
- **前台运行、离开即暂停**：监测依赖监测页前台 + 屏幕点亮；返回或锁屏时释放相机并暂停，不尝试后台保活。
- **本机历史**：Room 保存会话、每日/每小时聚合和异常事件（仅时间与指标），可按会话回溯异常时段。
- **可配置**：灵敏度、提醒方式、异常持续时间、冷却时间、分析帧率、朝向、屏幕常亮。

## 页面

| 页面 | 说明 |
| --- | --- |
| 首页 | 今日概览、校准状态、开始监测、各功能入口 |
| 摆放手机 | 朝向选择、实时画面检查、摆放要点 |
| 个人校准 | 15 秒采集（前 2 秒预热），显示实时质量与进度，生成个人基准 |
| 姿态监测 | 相机预览、关键点叠加、当前状态与三项变化量、暂停/继续/结束 |
| 设置 | 监测、提醒、灵敏度、朝向、数据与隐私、关于 |
| 统计报告 | 今日汇总、姿态分布、每小时分布、最近 7 天趋势 |
| 历史记录 | 会话列表、单次会话详情与异常事件 |
| 隐私说明 / 关于 | 数据边界、模型版本与 SHA-256、阈值免责声明 |

## 环境要求

- JDK 21
- Android SDK 35（compileSdk / targetSdk 35），minSdk 29
- Gradle 8.11.1（使用仓库内 wrapper）
- Android 10（API 29）及以上真机或模拟器，需后置摄像头

依赖版本固定在 `gradle/libs.versions.toml`，不使用动态版本。

## 构建与运行

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.postureguard/.MainActivity
```

首次使用：

1. 阅读引导 → 「去摆放手机」，授予相机权限。
2. 选择你面向画面的方向（面向右侧 / 面向左侧）。
3. 完成约 15 秒校准；有效样本比例建议 ≥ 70%。
4. 回到首页点击「开始监测」，保持监测页在前台。

## 测试

```bash
./gradlew :app:testDebugUnitTest          # 61 个纯 JVM 单元测试
./gradlew :app:connectedDebugAndroidTest  # 3 个设备测试（模型 + UI 生命周期）
./gradlew :app:lintDebug                  # Android Lint
scripts/verify_model_assets.sh            # 校验模型 SHA-256
```

单元测试覆盖几何、特征管线、状态机、校准、提醒计划、会话聚合、漂移检测；设备测试在模拟器上跑通真实 MediaPipe 模型（合成侧视图片）和监测页「未检测到人体 → 暂停 → 继续 → 结束」流程。详见 [docs/TEST_PLAN.md](docs/TEST_PLAN.md)。

**尚未完成真机摄像头验收**：当前只有 macOS 上的模拟器环境，模拟器虚拟场景不含真人，因此「真人坐姿分类准确率」未验证。详见 [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md)。

## 隐私

画面仅在监测/校准/摆放页前台经 CameraX 送入本机模型，处理完即丢弃；数据库只保存校准基准、时长聚合与事件时间/指标，不含图像或原始关键点。详见 [docs/PRIVACY.md](docs/PRIVACY.md)。

## 阈值与免责声明

`PostureThresholdConfig` 中的前倾（0.10 / 0.22 躯干单位）、低头（10° / 22°）、躯干前倾（8° / 18°）、持续与冷却时间均为工程默认值，不是医学标准。它们描述的是**相对个人校准基准的变化量**。PostureGuard 不构成医学诊断或治疗建议。

## 目录结构

```
app/src/main/java/com/example/postureguard/
  alert/          提醒计划与分发（声音/语音/振动）
  camera/         CameraX 会话、分析器与帧模型
  core/math/      几何、坐标变换
  data/           Room 实体/DAO/仓库、DataStore 设置
  monitoring/     会话控制器、状态模型、时长聚合
  posture/        特征管线、滤波、校准、状态机、领域模型
  service/        前台相机服务与通知
  ui/             Compose 页面、组件、ViewModel、导航
  vision/         MediaPipe 模型封装
app/src/main/assets/pose_landmarker_lite.task
app/src/test/     纯 JVM 单元测试
app/src/androidTest/ 设备测试与合成侧视测试图
docs/             架构、算法、摆放、隐私、测试、环境验证
scripts/          模型校验脚本
```

## 文档

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — 分层、数据流、线程与生命周期
- [docs/POSTURE_ALGORITHM.md](docs/POSTURE_ALGORITHM.md) — 指标定义、校准、阈值、状态机
- [docs/CAMERA_SETUP.md](docs/CAMERA_SETUP.md) — 摆放、朝向、常见问题
- [docs/PRIVACY.md](docs/PRIVACY.md) — 数据边界与权限
- [docs/TEST_PLAN.md](docs/TEST_PLAN.md) — 测试覆盖与已验证/未验证范围
- [docs/ENVIRONMENT_VALIDATION.md](docs/ENVIRONMENT_VALIDATION.md) — 工具链与构建验证记录
- [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md) — 需求对照与已知限制
- [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md) — 项目总结：架构决策、验证结果、偏离与风险
