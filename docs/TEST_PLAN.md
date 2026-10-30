# 测试计划与结果

## 1. 测试层级

| 层级 | 内容 | 命令 |
| --- | --- | --- |
| JVM 单元测试 | 纯 Kotlin 领域逻辑（几何、特征、滤波、状态机、校准、提醒、会话聚合、漂移） | `./gradlew :app:testDebugUnitTest` |
| 设备测试 | 真实 MediaPipe 模型 + Compose UI 生命周期 | `./gradlew :app:connectedDebugAndroidTest` |
| 静态检查 | Android Lint | `./gradlew :app:lintDebug` |
| 资源校验 | 模型文件存在且 SHA-256 匹配 | `scripts/verify_model_assets.sh` |

## 2. JVM 单元测试（61 个，全部通过）

| 测试类 | 覆盖点 |
| --- | --- |
| `GeometryTest` | 角度环绕、最短角差、中位数、MAD、离群裁剪、clamp |
| `FrameTransformTest` | 图像/预览坐标变换、镜像、纵横比处理 |
| `PostureFeaturePipelineTest` | 前移/低头/躯干角计算、朝向符号、侧别选择、质量门控、左右不稳定、无人 |
| `PostureStateMachineTest` | 轻度/明显确认时长、恢复迟滞、无人冻结、低质量宽限、埋头组合、优先级 |
| `CalibrationAccumulatorTest` | 预热、有效比例、样本不足、姿势不稳、离群裁剪、成功基准 |
| `AlertPlannerTest` | 确认前不提醒、冷却、按条件独立冷却、正常后清空、通道选择 |
| `MonitoringSessionRecorderTest` | 互斥时长分类、暂停、无人归入低质量、跨小时/跨天拆分、事件记录 |
| `CameraDriftDetectorTest` | 稳定不报警、持续偏移报警、回归清除、帧间隔重置、缺失指标重置 |

```text
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL
测试总数 61，失败 0，错误 0，跳过 0
```

## 3. 设备测试（3 个，全部通过）

运行环境：Medium_Phone_API_37.0(AVD)，arm64-v8a，Android API 37 模拟器。

### 3.1 合成侧视图片推理

`SideProfileModelInstrumentedTest.syntheticSideProfileYieldsUsableMetrics`

- 使用仓库内合成侧视图片 `app/src/androidTest/assets/side_profile_synthetic.jpg`（非真人拍摄，仅作为模型/管线冒烟测试图）。
- 用真实的 `pose_landmarker_lite.task` 在 `IMAGE` 模式推理。
- 断言：检测到人体、特征管线产生指标、侧别确定、躯干尺度合理。

实测输出：

```text
SIDE_PROFILE forward=0.1744 head=0.5734 torso=3.0982 scale=0.3087 side=RIGHT issues=[]
```

### 3.2 空白画面不伪造姿态

`SideProfileModelInstrumentedTest.blankFrameDoesNotFabricateAPose`

- 输入纯色 640×480 图片，断言关键点数量为 0。
- 验证「无人体时不产生虚假检测」的底线。

### 3.3 监测页生命周期与诚实状态

`MonitorFlowInstrumentedTest.monitorShowsHonestNoPersonStateAndLifecycle`

- 通过仓库写入一份校准基准（仅测试数据），授予相机与通知权限。
- 启动 `MainActivity`，从首页点击「开始监测」。
- 模拟器虚拟相机画面中没有人，断言：
  - 进入「监测中」；
  - 显示「未检测到人体」；
  - 不出现「姿态自然」；
  - 「暂停」后出现「继续监测」；
  - 「继续监测」恢复运行；
  - 「结束监测」进入「监测结束」，显示「本次记录已保存在本机」。
- 测试完成后系统会清理应用与测试数据。

```text
Starting 3 tests on Medium_Phone_API_37.0(AVD) - 17
Finished 3 tests
BUILD SUCCESSFUL
```

## 4. 静态检查

`./gradlew :app:lintDebug` 通过，无 Error。剩余 60 条 Warning 均为：

- 依赖存在更新版本（版本被有意固定）；
- 固定竖屏（相机应用的有意设计）；
- `allowBackup` 弃用提示（隐私上主动关闭备份）；
- 自适应图标缺 monochrome（不影响功能）。

## 5. 手工 UI 验证（模拟器）

已在 1080×2400 / Android API 37 模拟器上逐页打开并截图核对：

- 首页：引导弹窗、今日概览、校准卡片、功能入口；
- 摆放手机：朝向切换、相机权限申请、实时预览、检查项「等待检测」（画面无人）；
- 个人校准：实时预览、质量提示、「开始 15 秒校准」在无人时禁用；
- 设置：帧率、提醒、灵敏度、朝向、删除历史/校准确认框、关于入口；
- 统计报告：今日汇总、空状态、每小时分布、最近 7 天；
- 历史记录：空状态；
- 隐私说明、关于：文案、模型版本、SHA-256；
- 相机权限弹窗、返回栈行为；
- 最终 debug APK 重装并启动冒烟：首页正常渲染（含首次使用引导），logcat 无 `AndroidRuntime` 异常。

监测页的运行态由设备测试覆盖（无人 → 暂停 → 继续 → 结束）。

## 6. 未验证与限制（必须知晓）

1. **没有真机摄像头验收**：开发环境只有 macOS + Android 模拟器，模拟器虚拟场景不含真人。真人坐姿的分类准确率、不同体型/服装/光照下的稳定性均未验证。
2. **没有真人校准验收**：校准算法有单元测试与合成图片冒烟，但未用真人 15 秒采集验证。
3. **没有准确率/误报率数据**：工程阈值未经真实数据调优，不能宣称医疗或临床有效性。
4. **API 29–36 未实机覆盖**：只在 API 37 模拟器运行；minSdk 29 的兼容性仅通过编译与 API 分支审查。
5. **长时间运行/耗电/发热**：未做 1 小时以上稳定性与功耗测量。
6. **多设备相机差异**：不同厂商的相机方向、镜像、色彩处理可能导致预览/分析方向差异，需要真机核对。

在真机上完成下列验收前，不应把本项目标记为「生产可用」：

- [ ] 三种光照（顺光/侧光/逆光）下校准成功率；
- [ ] 自然坐姿 10 分钟，误报次数；
- [ ] 明显前倾/低头/埋头/躯干前倾各 2 分钟，检出延迟与漏报；
- [ ] 移动手机后漂移提示与重新校准流程；
- [ ] 锁屏、来电、切换应用后的暂停与恢复；
- [ ] 1 小时耗电与发热。
