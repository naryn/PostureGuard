# 环境与构建验证记录

记录本次开发所在的实际环境、执行的命令与结果。日期：2026-10-30。

## 1. 主机与工具链

| 项 | 值 |
| --- | --- |
| 主机 | macOS 27.0.0，arm64（Apple Silicon） |
| JDK | OpenJDK 21.0.2（Oracle） |
| Gradle | 8.11.1（仓库内 wrapper） |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 |
| KSP | 2.0.21-1.0.28 |
| compileSdk / targetSdk | 35 |
| minSdk | 29 |
| Build Tools | 34.0.0 / 35.0.0 / 36.0.0（SDK 已安装） |
| Android SDK | `/Users/koko/Library/Android/sdk` |
| 设备 | 模拟器 `Medium_Phone_API_37.0(AVD)`，Android 17 / API 37，arm64-v8a |

`./gradlew --version` 输出 Gradle 8.11.1、Launcher JVM 21.0.2（Gradle 自带 Kotlin 显示为 2.0.20 属于 Gradle 内嵌版本，项目 Kotlin 为 2.0.21）。

## 2. 依赖版本（全部固定）

| 组件 | 版本 |
| --- | --- |
| Compose BOM | 2024.12.01 |
| Navigation Compose | 2.8.5 |
| CameraX | 1.4.1 |
| MediaPipe Tasks Vision | 0.10.21 |
| Room | 2.6.1 |
| DataStore Preferences | 1.1.1 |
| Kotlin Coroutines | 1.9.0 |
| JUnit | 4.13.2 |
| AndroidX Test Core / Runner / Rules | 1.7.0 |
| AndroidX Test Ext JUnit | 1.3.0 |
| Espresso | 3.7.0 |

版本集中在 `gradle/libs.versions.toml`，没有 `latest.release` 或动态版本。

> 说明：Compose UI Test 1.7.6 的 POM 引用了已不存在的 `androidx.test:core:1.6.2`，构建中显式固定 core 1.7.0 解决；设备测试栈同步升级到 1.7.0 / Espresso 3.7.0 以兼容 API 37（旧版 Espresso 在 API 37 上因 `InputManager.getInstance` 反射失败）。

## 3. 构建验证

```bash
$ ./gradlew :app:compileDebugKotlin
BUILD SUCCESSFUL

$ ./gradlew :app:assembleDebug :app:testDebugUnitTest
BUILD SUCCESSFUL

$ ./gradlew :app:lintDebug
BUILD SUCCESSFUL（0 Error，60 Warning）
```

产物：

```text
app/build/outputs/apk/debug/app-debug.apk   119,109,631 字节（约 113.6 MiB，debug，含全部 ABI 的 MediaPipe 原生库与 5.5 MB 模型）
```

安装与启动：

```bash
$ adb install -r app/build/outputs/apk/debug/app-debug.apk
Success

$ adb shell am start -n com.example.postureguard/.MainActivity
Starting: Intent { cmp=com.example.postureguard/.MainActivity }
```

应用启动、相机预览、模型初始化均正常；logcat 中可见 TFLite 使用 XNNPack delegate 加载整图（291/305 节点），说明 bundled 模型在设备上成功初始化。

最终构建产物重装后再次启动冒烟：首页与首次使用引导正常渲染，logcat 无 `AndroidRuntime` 异常。

## 4. 设备测试

```bash
$ ./gradlew :app:connectedDebugAndroidTest
Starting 3 tests on Medium_Phone_API_37.0(AVD) - 17
Finished 3 tests
BUILD SUCCESSFUL
```

详见 [TEST_PLAN.md](TEST_PLAN.md)。

## 5. 模型资源校验

```bash
$ scripts/verify_model_assets.sh
OK: pose_landmarker_lite.task
  size:   5777746 bytes
  sha256: 59929e1d1ee95287735ddd833b19cf4ac46d29bc7afddbbf6753c459690d574a
```

脚本从 `VisionModels.kt` 读取期望 SHA-256，与资产实际哈希比对，并拒绝异常小的文件。

## 6. 环境限制

1. **无真机**：只有 Android 模拟器。不能声称完成真机摄像头验收、真人姿态准确率或功耗验证。
2. **模拟器虚拟场景无真人**：相机预览与「无人」路径可验证，真人分类路径只能由单元测试与合成图片推理间接验证。
3. **未覆盖 API 29–36 实机**：minSdk 29 的兼容性通过编译、Lint 与代码分支审查，未在旧系统真机运行。
4. **网络受限**：部分不存在的测试依赖版本（如 `androidx.test:core:1.6.2`）无法解析，已改用仓库中可用的稳定版本并记录在案。
5. **debug 包体积**：113 MB 主要来自 MediaPipe 多 ABI 原生库；正式发布应开启 ABI 拆分或 App Bundle。
