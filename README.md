# Moment

Moment 是一款 Android 时间管理应用。它把“可以使用分心 App 的时间”设计成一种余额：完成任务获得时间，使用被管理的 App 消耗时间，余额耗尽后通过无障碍服务进行提醒和拦截。

## 主要功能

### 时间余额

应用首页会显示当前剩余时间。完成任务会增加余额，使用被管理的 App 会扣减余额。余额不足时，Moment 会尝试拦截已配置的应用。

### 每日基础时间

Moment 可以每天自动发放一段基础时间。基础时间可在设置页调整，适合保留少量弹性使用空间。

### 自定义每日重置时间

每日重置时间默认为 12 点，也可以在设置页改为 `0` 到 `23` 之间的任意整点。到达该整点后，Moment 会把当前逻辑日期切换到新的一天，并按设置发放基础时间。

### 任务系统

Moment 支持两类任务：

- 提交任务：完成后提交文字或图片材料，手动领取奖励时间。
- 时长任务：配置目标 App 包名和目标时长，使用该 App 达到要求后自动结算奖励。

任务可以设置为一次性任务或循环任务。循环任务完成后会保留，并继续累计下一轮进度。

### 完成记录

应用会保存已完成任务的记录，包括任务名称、任务类型、奖励时间、完成时间、提交内容和图片。完成记录页支持查看详情和按条件筛选。

### 统计概览

统计页展示当前余额、今日获得时间、今日消耗时间、今日净变化、今日完成任务数、近 7 天完成数、连续完成天数、任务类型占比和贡献最高的任务。

### 被管理 App 列表

设置页可以维护要管理的 App 包名，每行一个。默认包含：

```text
com.tencent.mobileqq
tv.danmaku.bili
```

## 权限说明

Moment 需要以下权限才能完整工作：

- 无障碍服务：用于识别当前界面，并在余额不足时引导离开被管理 App。
- 使用情况访问权限：用于更可靠地统计前台 App 使用情况和时长任务进度。

这些权限需要用户在系统设置中手动授予。

## 使用方法

1. 安装并打开 Moment。
2. 进入设置页，配置每日基础时间和每日重置时间。
3. 在拦截 App 包名列表中填写需要管理的应用包名。
4. 点击保存设置。
5. 按页面按钮打开无障碍设置，启用 Moment 服务。
6. 按页面按钮打开使用情况访问设置，授予 Moment 权限。
7. 创建任务，完成任务后获得可用时间。

## 构建环境

推荐使用 Android Studio 打开并构建项目。

项目配置：

```text
Application ID: com.example.moment
minSdk: 29
targetSdk: 34
compileSdk: 34
Android Gradle Plugin: 8.13.1
Gradle Wrapper: 8.13
Kotlin: 2.0.21
Room: 2.6.1
```

建议安装：

```text
Android Studio
JDK 17 或 Android Studio 自带 JBR
Android SDK Platform 34
Android SDK Build Tools
Android SDK Platform-Tools
```

## 构建 APK

在 Android Studio 中可以直接使用：

```text
Build > Make Project
Build > Generate Signed App Bundle / APK
```

也可以使用命令行：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

构建产物：

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

## Release 签名

Release 构建会读取以下 Gradle properties：

```text
MOMENT_STORE_FILE
MOMENT_STORE_PASSWORD
MOMENT_KEY_ALIAS
MOMENT_KEY_PASSWORD
```

如果这些配置存在，`assembleRelease` 会生成已签名的 release APK；如果不存在，则需要通过 Android Studio 或其他方式配置签名。

## 安装到设备

连接 Android 设备并开启 USB 调试后，可以使用：

```powershell
adb install -r app/build/outputs/apk/release/app-release.apk
```

`-r` 表示覆盖安装并保留应用数据。更新已安装版本时，需要使用与旧版本一致的签名证书。

## 技术栈

- Kotlin
- AndroidX AppCompat
- Material Components
- Room
- KSP
- Markwon
- Coil
