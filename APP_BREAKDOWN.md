# MockLoc 定位助手 - 完整代码拆解与 Bug 分析

## 1. 项目概览

| 属性 | 值 |
|------|------|
| 应用名称 | 定位助手 (MockLoc) |
| 包名 | `com.mockloc.assistant` |
| Target SDK | 36 (Android 15) |
| Min SDK | 29 (Android 10) |
| AGP | 8.12.3 |
| Kotlin | 2.2.20 |
| Compose BOM | 2025.09.00 |
| osmdroid | 6.1.20 |
| 架构 | MVVM + Compose + DataStore + Foreground Service |
| GitHub | https://github.com/qingjiuban/mockloc |

---

## 2. 目录结构

```
app/src/main/
├── AndroidManifest.xml
├── java/com/mockloc/assistant/
│   ├── MainActivity.kt
│   ├── MockLocApplication.kt
│   ├── core/
│   │   └── GeoUtils.kt
│   ├── data/
│   │   ├── map/MapTileSources.kt
│   │   ├── model/Models.kt, HistoryEntry.kt
│   │   ├── prefs/AppRepository.kt
│   │   └── search/PlaceSearchService.kt, OfflinePlaceIndex.kt
│   ├── mock/
│   │   ├── MockLocationEngine.kt
│   │   ├── MockPrerequisites.kt
│   │   ├── MockSessionState.kt
│   │   ├── MockBootReceiver.kt
│   │   ├── RestoreNotifier.kt
│   │   └── service/MockLocationService.kt
│   └── ui/
│       ├── navigation/AppNavigation.kt
│       ├── home/HomeScreen.kt, HomeViewModel.kt
│       ├── map/MapPickerScreen.kt
│       ├── places/PlacesScreen.kt
│       ├── settings/SettingsScreen.kt
│       ├── guide/GuideScreen.kt
│       ├── state/HomeUiState.kt
│       ├── components/Common.kt
│       └── theme/Theme.kt
└── res/
    ├── xml/network_security_config.xml
    ├── values/strings.xml, colors.xml, themes.xml
    └── drawable/*.xml
```

---

## 3. 核心模块分析

### 3.1 MockLocationEngine (`mock/MockLocationEngine.kt`)

**职责：** 通过 `LocationManager.addTestProvider` 注册模拟 GPS/Network provider，周期性注入坐标。

**关键实现：**
- 使用 SDK 31+ 的 `ProviderProperties.Builder` API
- 回退到 deprecated 9参数 `addTestProvider` 用于旧版本
- 注入后通过 `getLastKnownLocation` 回读验证
- 验证条件：`readBack.isMock == true` 且距离 < 2m

**已知问题：**
1. **`buildLocation()` 中的 `setMock(true)` 被 `runCatching` 包裹** - 该 API 在 SDK 31+ 已废弃，静默失败不会设置 mock 标记。已改为以坐标距离为主要验证依据。
2. **`stop()` 中 `setTestProviderEnabled(provider, false)` 可能在未注册的 provider 上调用** - 已用 `runCatching` 保护
3. **回读验证曾过严 (v1.0.2 及以前)** - 要求 `readBack.isMock == true` 且距离 < 2m，但部分 OEM ROM 不给注入位置打 mock 标记，导致误报「授权失效」。v1.0.3 改为距离优先、isMock 辅助判断。

### 3.2 MockLocationService (`mock/service/MockLocationService.kt`)

**职责：** 前台服务，周期性调用 `engine.inject()` 注入坐标。

**关键实现：**
- `START_STICKY` + WakeLock (6小时)
- 连续失败3次后降级为 Error 状态
- `foregroundServiceType="location"`

**已知问题：**
1. **`beginMocking()` 中 `delay(settings.intervalMillis)` 在注入循环中** - 如果 `inject()` 抛出未捕获异常，`delay` 后的代码不会执行，但 `SupervisorJob` 会吞掉异常导致注入静默停止
2. **`startForegroundSafely()` 使用 `try` 但没有处理 `ForegroundServiceStartNotAllowedException`** - Android 12+ 后台启动 FGS 受限，虽然有 `try` 但只打了 Log 没有降级策略
3. **`onStartCommand` 中 `beginMocking` 在 `scope.launch` 中执行** - 如果服务在 `beginMocking` 完成前被 `stopSelf`，`currentPoint` 可能为 null 导致后续注入空指针
4. **WakeLock 只 acquire 一次但 release 多次** - `releaseWakeLock()` 有 `runCatching` 保护不会 crash，但逻辑不严谨

### 3.3 MockPrerequisites (`mock/MockPrerequisites.kt`)

**职责：** 检测模拟定位前置条件（权限、开发者选项、mock app 授权）。

**关键实现：**
- `AppOpsManager.unsafeCheckOpNoThrow(OPSTR_MOCK_LOCATION)` 检查 mock 授权
- 厂商 ROM 识别 + 差异化引导步骤

**已知问题：**
1. **没有运行时权限请求逻辑** - 只检查权限是否已授予，从不主动调用 `requestPermissions()`。用户首次使用时，如果拒绝权限，app 只能显示"请先授予定位权限"但无法引导用户重新授权
2. **`selectedMockApp()` 读取 `Settings.Secure.getString("mock_location")`** - 这个 key 在 Android 10+ 已不返回应用包名，方法基本无效
3. **`developerOptionsEnabled()` 依赖 `DEVELOPMENT_SETTINGS_ENABLED`** - 部分 OEM ROM 此值永远返回 0，虽然有 `adb_enabled` 兜底但两者都可能为 0 导致误判

### 3.4 AppRepository (`data/prefs/AppRepository.kt`)

**职责：** DataStore 持久化层，管理设置、地点、历史、会话快照。

**已知问题：**
1. **`settings` Flow 定义了两遍** - 第62-76行和第120-141行有重复的 `settings` Flow 定义，后者覆盖前者，但前者已定义了完整的映射逻辑，后者又重复了一遍
2. **`altitude` 存储为 `floatPreferencesKey` 但读取时 `.toDouble()`** - float 精度丢失（float 只有 7 位有效数字），如果海拔 > 10000m 会出现精度问题
3. **`saveSession` 和 `setLastPoint` 都写 `Keys.lastPoint` + `Keys.session`** - 功能重叠

### 3.5 MapTileSources (`data/map/MapTileSources.kt`)

**职责：** 配置 osmdroid 瓦片源和初始化。

**已知问题：**
1. **OSM 瓦片服务器在国内不可用** - 已修复，默认改为 CARTO Light (Fastly CDN)
2. **`initialize()` 中 `config.load()` 可能抛出异常** - 已有 try-catch 但日志级别为 Error，不影响功能
3. **没有瓦片加载失败的回调机制** - osmdroid 瓦片加载失败时没有 UI 提示，用户只看到空白网格

### 3.6 MapPickerScreen (`ui/map/MapPickerScreen.kt`)

**职责：** 地图选点界面，包含搜索和地图交互。

**已知问题：**
1. **`update` 回调中 `map.postDelayed(500ms)` 每次 recomposition 都执行** - 没有取消之前的 delayed post，导致多次回调更新 `selectedLat/selectedLng`，可能与 scroll 事件冲突
2. **`mapRef` 通过 `mutableStateOf` 存储 MapView 引用** - 每次 recomposition 都会触发 update 回调，造成不必要的坐标更新
3. **搜索结果列表覆盖在地图上方** - 但 `showResults = false` 只在搜索按钮点击时重置，用户点击搜索结果的 `onClick` 里设了 `showResults = false`，但如果用户手动清空搜索框 `query` 时 `showResults = false` 但 `searchResults` 也被 `clear()` 了（第105行），这是正确的
4. **没有处理 `LocalLifecycleOwner` 的 import 但未使用** - 第53行导入了 `LocalLifecycleOwner` 但代码中未使用

### 3.7 PlaceSearchService (`data/search/PlaceSearchService.kt`)

**职责：** Nominatim API 在线搜索 + 离线城市库兜底。

**已知问题：**
1. **Nominatim API 在国内可能被墙** - 有离线兜底，但用户可能不知道搜索结果是离线的
2. **`searchRemote()` 使用 `HttpURLConnection` 而非 OkHttp** - 没有连接池，没有重试，每次搜索新建 TCP 连接
3. **超时设置为 6 秒** - 在弱网环境下可能不够
4. **`PlaceResult.dedupeKey()` 用 3 位小数精度去重** - 约 111m 精度，两个不同地点如果距离 < 111m 会被错误去重

### 3.8 HomeViewModel (`ui/home/HomeViewModel.kt`)

**职责：** 首页 ViewModel，管理 UI 状态和模拟控制。

**已知问题：**
1. **`combine(9个Flow)` 性能问题** - 9 路 combine 每次任一 Flow 发射都会重新计算，可能导致频繁 recomposition
2. **`refreshRealLocation()` 调用 `RealLocationReader.lastKnown()`** - 在 `Dispatchers.IO` 上执行但没有处理 `SecurityException`（如果用户拒绝权限）
3. **`startMocking()` 检查 `pre.locationPermission` 但不在检查前自动请求权限** - 用户需要手动去系统设置授权
4. **`toggleMocking()` 没有防抖** - 快速点击可能导致重复启动/停止

### 3.9 MockBootReceiver (`mock/MockBootReceiver.kt`)

**职责：** 开机/应用更新后恢复模拟会话。

**已知问题：**
1. **`goAsync()` 的 `pending.finish()` 在 finally 中** - 如果 coroutine 被取消（例如进程被杀），`finish()` 可能不会被调用
2. **`CoroutineScope(SupervisorJob() + Dispatchers.IO)` 每次创建新 scope** - 如果 `onReceive` 被多次调用，多个 coroutine 可能并发执行

---

## 4. 对比模拟定位实现指南 - 缺失项

根据 Android 模拟定位应用实现指南逐项对照：

### 4.1 权限声明 (Manifest)

| 要求 | 当前状态 | 问题 |
|------|---------|------|
| `ACCESS_FINE_LOCATION` | 已声明 | OK |
| `ACCESS_MOCK_LOCATION` | **未声明** | Android 6.0+ 已废弃此权限，改为 AppOps 授权。不声明通常不影响功能，但部分 OEM ROM 可能检查此声明 |
| `ACCESS_COARSE_LOCATION` | 已声明 | OK |
| `INTERNET` | 已声明 | OK |
| `FOREGROUND_SERVICE` | 已声明 | OK |
| `FOREGROUND_SERVICE_LOCATION` | 已声明 | OK |
| `POST_NOTIFICATIONS` | 已声明 | OK (Android 13+ 需要) |
| `RECEIVE_BOOT_COMPLETED` | 已声明 | OK |
| `WAKE_LOCK` | 已声明 | OK |

### 4.2 核心代码逻辑

| 要求 | 当前状态 | 问题 |
|------|---------|------|
| `LocationManager.addTestProvider` | 已实现 | SDK 31+ 使用新 API，但 **SDK 29-30 的 fallback 缺失** |
| `setTestProviderEnabled(true)` | 已实现 | OK |
| `setTestProviderLocation` | 已实现 | OK |
| `Location.setMock(true)` | 已实现但被 `runCatching` 包裹 | SDK 31+ 已移除此 API，静默失败。**这可能导致系统不标记位置为 mock** |
| 持续注入循环 | 前台服务中实现 | OK，但有异常处理不足的问题 |
| 回读验证 | `getLastKnownLocation` + 距离检查 | OK |

### 4.3 运行时权限请求

| 要求 | 当前状态 | 问题 |
|------|---------|------|
| 运行时动态权限申请 | **完全缺失** | `MockPrerequisites` 只检查不请求。`HomeScreen` 没有调用 `requestPermissions()` 或 `ActivityResultContracts.RequestPermission`。用户必须在系统设置中手动授权 |

### 4.4 签名

| 要求 | 当前状态 | 问题 |
|------|---------|------|
| Debug 签名 | 当前使用 debug 签名 | 部分 OEM ROM 可能对 debug 签名的 mock 应用有限制 |
| Release 签名 | 已生成 keystore 但未使用 | OK |

---

## 5. 已确认 Bug 列表

### P0 - 严重

1. **MapTileSources 瓦片加载失败 (v1.0.0-v1.0.1)**
   - 原因：User-Agent 拼接错误 + OSM 服务器国内不可达
   - 修复：v1.0.2 改用 CARTO CDN 作为默认瓦片源
   - 状态：已修复

2. **SessionSnapshot 缺少 @Serializable (v1.0.0)**
   - 原因：DataStore 序列化时找不到注解
   - 修复：v1.0.1 添加 `@Serializable` 注解
   - 状态：已修复

3. **SDK 29-30 设备 addTestProvider 缺失 fallback**
   - 原因：`MockLocationEngine.addProvider()` 只有 `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` 分支
   - 影响：Android 10-11 设备无法注册 test provider
   - 状态：**未修复**

### P1 - 高优先级

4. **缺少运行时权限请求**
   - 影响：用户拒绝权限后无法重新引导授权
   - 状态：**未修复**

5. **Location.setMock(true) 在 SDK 31+ 静默失败**
   - 影响：注入的位置可能不被系统标记为 mock 位置
   - 状态：**未修复**（需要改用 SDK 31+ 的 `Location.isMock` 属性或 `setMock()` 替代方案）

6. **HomeViewModel 9路 combine 性能问题**
   - 影响：频繁 recomposition 导致 UI 卡顿
   - 状态：**未修复**

7. **MapPickerScreen update 回调无限触发**
   - 影响：每次 recomposition 都 `postDelayed(500ms)` 更新坐标
   - 状态：**未修复**

### P2 - 中优先级

8. **PlaceSearchService 使用 HttpURLConnection 无连接池**
   - 影响：每次搜索新建 TCP 连接，慢
   - 状态：**未修复**

9. **SettingsScreen 版本号硬编码为 "1.0.0"**
   - 文件：`ui/settings/SettingsScreen.kt:86`
   - 影响：显示过时版本号
   - 状态：**未修复**

10. **AppRepository 的 settings Flow 重复定义**
    - 影响：代码冗余，可能有意外行为
    - 状态：**未修复**

### P3 - 低优先级

11. **OfflinePlaceIndex 搜索匹配优先级问题**
    - `city.name.contains(query)` 得分 70 高于 `haystack.startsWith(q)` 得分 80，逻辑上 startsWith 应该优先
    - 状态：**未修复**

12. **GeoUtils.gcj02ToWgs84 迭代终止条件过严**
    - `kotlin.math.abs(dLat) < 1e-9` 对于双精度浮点数来说几乎不可能满足
    - 实际靠 `repeat(8)` 的 8 次迭代兜底
    - 状态：**未修复**

---

## 6. 依赖清单

| 依赖 | 版本 | 用途 |
|------|------|------|
| Compose BOM | 2025.09.00 | Material3 UI |
| Navigation Compose | 2.9.5 | 页面导航 |
| DataStore Preferences | 1.1.7 | 持久化 |
| Lifecycle | 2.9.4 | ViewModel + Runtime |
| kotlinx.serialization | 1.9.0 | JSON 序列化 |
| osmdroid | 6.1.20 | 地图瓦片渲染 |
| core-splashscreen | 1.0.1 | 启动屏 |

---

## 7. 构建配置

```kotlin
// app/build.gradle.kts 关键配置
namespace = "com.mockloc.assistant"
compileSdk = 36
minSdk = 29
targetSdk = 36
versionCode = 3
versionName = "1.0.2"

buildTypes {
    debug {
        applicationIdSuffix = ".debug"
        versionNameSuffix = "-debug"
    }
    release {
        isMinifyEnabled = true
        isShrinkResources = true
    }
}
```

---

## 8. 文件清单与行数

| 文件 | 行数 | 职责 |
|------|------|------|
| MockLocationEngine.kt | 208 | 模拟定位核心引擎 |
| MockLocationService.kt | 391 | 前台服务 |
| MockPrerequisites.kt | 161 | 前置条件检测 |
| AppRepository.kt | 228 | DataStore 持久化 |
| HomeViewModel.kt | 260 | 首页 ViewModel |
| HomeScreen.kt | 259 | 首页 UI |
| MapPickerScreen.kt | 313 | 地图选点 UI |
| PlacesScreen.kt | 222 | 常用地点 UI |
| SettingsScreen.kt | 124 | 设置 UI |
| GuideScreen.kt | 176 | 使用指南 UI |
| AppNavigation.kt | 104 | 导航配置 |
| Theme.kt | 146 | Material3 主题 |
| Common.kt | 315 | 通用 UI 组件 |
| GeoUtils.kt | 152 | 地理计算工具 |
| MapTileSources.kt | 141 | 地图瓦片源配置 |
| PlaceSearchService.kt | 114 | 在线/离线搜索 |
| OfflinePlaceIndex.kt | 159 | 离线城市数据库 |
| MockSessionState.kt | 34 | 会话状态定义 |
| MockBootReceiver.kt | 43 | 开机恢复广播 |
| RestoreNotifier.kt | 62 | 恢复通知 |
| HomeUiState.kt | 60 | UI 状态定义 |
| Models.kt | 86 | 数据模型 |
| HistoryEntry.kt | 22 | 历史记录模型 |
| MainActivity.kt | 28 | 入口 Activity |
| MockLocApplication.kt | 11 | Application 初始化 |
| AndroidManifest.xml | 70 | 权限与组件声明 |
