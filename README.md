# Moment

Moment 是一个 Android 时间管理应用。它通过任务奖励可用时间，并通过无障碍服务和使用情况访问权限监控前台 App，用来限制指定应用的使用。

## 功能概览

- 创建任务，并按完成情况奖励时间。
- 支持手动提交任务和 App 使用时长任务。
- 管理拦截 App 包名列表。
- 统计今日和近 7 天的任务完成与时间变化。
- 每日发放基础时间。
- 每日重置时间可在设置页调整，默认是 12 点。

## 首次使用

安装后进入应用，建议先打开设置页完成这些配置：

1. 设置每日基础时间。
2. 设置每日重置时间，取值为 `0` 到 `23`，表示当天这个整点后进入新的一天。
3. 填写要管理/拦截的 App 包名，每行一个。
4. 点击保存设置。
5. 打开无障碍设置，启用 Moment 服务。
6. 打开使用情况访问设置，给 Moment 授权。

默认拦截列表里包含：

```text
com.tencent.mobileqq
tv.danmaku.bili
```

## 开发环境

推荐使用 Android Studio 打开项目。Android Studio 会管理 Android SDK、Build Tools、Platform Tools 和 Gradle 同步。

当前项目配置：

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

建议环境：

```text
Android Studio
JDK 17 或 Android Studio 自带 JBR
Android SDK Platform 34
Android SDK Build Tools
Android SDK Platform-Tools
```

如果 Android Studio 提示 Gradle JDK 无效，可以在这里设置：

```text
File > Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK
```

选择 Android Studio 自带的 `Embedded JDK` / `jbr` 即可。

## 构建

Android Studio 里可以直接使用：

```text
Build > Make Project
Build > Generate Signed App Bundle / APK
```

也可以用命令行构建。Windows PowerShell 示例：

```powershell
$env:JAVA_HOME = "C:\Tools\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

构建产物位置：

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

## Release 签名

Release 签名通过 Gradle properties 配置：

```text
MOMENT_STORE_FILE
MOMENT_STORE_PASSWORD
MOMENT_KEY_ALIAS
MOMENT_KEY_PASSWORD
```

这些配置通常放在 `gradle.properties` 中。项目 `.gitignore` 已忽略签名相关文件：

```text
*.jks
*.keystore
keystore.properties
gradle.properties
```

如果要覆盖安装手机上的旧 release 版本，必须使用同一个签名 key 构建 release APK。

## 安装到手机

连接手机并开启 USB 调试后，可以检查设备：

```powershell
D:\Android\Sdk\platform-tools\adb.exe devices
```

覆盖安装 release 包：

```powershell
D:\Android\Sdk\platform-tools\adb.exe install -r app\build\outputs\apk\release\app-release.apk
```

`-r` 表示保留数据并覆盖安装。不要先卸载应用，否则本地数据会被删除。

如果手机上已安装的是 release 签名版本，不要直接用 debug 包覆盖。Debug 包签名不同，Android 通常会拒绝安装；如果卸载后再装 debug 包，原数据会丢失。

## 数据与迁移注意事项

应用数据主要包括：

- SharedPreferences：余额、设置、拦截包名、每日重置时间等。
- Room 数据库：任务、完成记录、每日统计。

当前数据库使用：

```kotlin
fallbackToDestructiveMigration()
```

这表示以后如果修改 Room schema 并提升数据库版本，但没有提供 migration，应用可能会清空数据库重建。改设置项或普通逻辑不涉及数据库结构时，不需要升级数据库版本。

更新手机上的应用时，稳妥流程是：

1. 确认没有修改数据库 schema，或已经写好 migration。
2. 构建 release APK。
3. 确认签名 key 与手机已安装版本一致。
4. 使用 `adb install -r` 覆盖安装。
5. 打开应用检查任务、统计、设置是否保留。

## 常见问题

### Git 提示 dubious ownership

如果项目是从旧 Windows 用户迁移过来的，Git 可能提示仓库所有者不安全。可以信任整个代码目录：

```powershell
git config --global --add safe.directory 'D:/Code/*'
```

或只信任当前项目：

```powershell
git config --global --add safe.directory D:/Code/AndroidStudioProjects/Moment
```

### local.properties 要提交吗

不要提交。`local.properties` 是本机 Android SDK 路径，例如：

```text
sdk.dir=D\:\\Android\\Sdk
```

Android Studio 会按当前电脑自动生成。

### 哪些目录可以删除

这些是本地缓存或构建产物，可以删除后重新生成：

```text
.gradle/
.kotlin/
app/build/
.idea/caches/
.idea/workspace.xml
local.properties
```
