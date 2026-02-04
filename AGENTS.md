# AGENTS.md - LiSynchronization 项目上下文文档

本文档为 AI 代理提供 LiSynchronization（又称 **LiSync**）项目的上下文，便于快速理解结构、核心逻辑与开发流程。

---

## 项目概述

**LiSynchronization** 是一个 Android 原生应用，作为落雪音乐音源脚本的 **手机端中转服务**，并与 **小米穿戴/小米健康**配套使用。手表端（Vela 快应用）通过手机端请求各平台播放链接、搜索与歌词数据。

### 核心功能

* **音源脚本执行**：QuickJS 运行自定义脚本，解析 `musicUrl`（脚本声明的 source/quality）。
* **小米穿戴集成**：MessageApi 通信、权限申请（DEVICE\_MANAGER/NOTIFY）、握手与能力推送。
* **设备状态监测**：连接/充电/佩戴/睡眠状态查询与订阅（NodeApi）。
* **脚本管理**：本地导入 / URL 下载 / 编辑 / 重命名 / 删除。
* **缓存**：

  * 播放链接：4 小时（内存 + `cache.json`）。
  * 搜索与歌词：5 分钟 LRU（内存，最多 100 条）。

* **能力更新推送**：脚本变更后推送 `capabilitiesUpdate` 给手表。
* **本地音乐上传**：手机端分片上传音频到手表（最大 5MB，8KB 分片）。
* **日志缓冲**：AppLogBuffer 记录最近 500 行，Settings 可查看/复制。

### 核心数据流（简化）

```
手表快应用 (Vela)
  ↓ JSON + \_requestId
XiaomiWearableManager
  ├─ action=search  -> SearchService -> HttpClient
  ├─ action=lyric/getLyric -> LyricService  -> HttpClient
  ├─ action=WATCH\_READY/capabilities/getCapabilities -> ScriptManager.getCapabilitiesSummary()
  ├─ action=upload.\* -> UploadSession (start/chunk/finish/ack/result)
  └─ 其他请求       -> RequestProxy
       ├─ CacheManager (cache.json)
       └─ ScriptManager -> ScriptContext + LxNativeImpl (QuickJS)
            -> HttpClient
```

---

## 技术栈

### Android 应用 (LiSync)

|组件|技术/库|版本|
|-|-|-|
|开发语言|Java|1.8|
|构建插件|Android Gradle Plugin|8.13.2|
|JavaScript 引擎|QuickJS Wrapper (`wang.harlon.quickjs`)|2.4.0|
|网络请求|OkHttp + logging-interceptor|4.12.0|
|JSON 处理|Gson|2.10.1|
|缓存存储|Room (仅 Java runtime/processor)|2.6.1|
|UI 组件|Material Design|1.13.0|
|UI 基础|AppCompat|1.7.1|
|文档访问|DocumentFile|1.0.1|
|生命周期组件|lifecycle-common-java8|2.8.7|
|小米穿戴 SDK|xms-wearable-lib|1.4|

### React Native 项目（参考/上游）

`lx-music-mobile/` 为完整上游仓库，包含 `lx-music-desktop/` 子项目，仅作为脚本/协议参考，不参与 LiSync 构建。

---

## 项目结构（关键路径）

```
F:\\Project\\LiSynchronization\\
├── app/                              # Android 主模块
│   ├── build.gradle
│   ├── proguard-rules.pro
│   ├── libs/xms-wearable-lib\_1.4\_release.aar
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/script/user-api-preload.js
│       ├── java/mindrift/app/music/
│       │   ├── App.java
│       │   ├── ui/
│       │   │   ├── AgreementActivity.java
│       │   │   ├── MainActivity.java
│       │   │   └── SettingsActivity.java
│       │   ├── wearable/XiaomiWearableManager.java
│       │   ├── core/
│       │   │   ├── cache/CacheManager.java, CacheEntry.java
│       │   │   ├── engine/LxNativeImpl.java, LxNativeInterface.java
│       │   │   ├── lyric/LyricService.java
│       │   │   ├── network/HttpClient.java, HttpUtils.java, NetworkConfig.java
│       │   │   ├── proxy/RequestProxy.java, ScriptHandler.java
│       │   │   ├── script/ScriptManager.java, ScriptContext.java,
│       │   │   │           ScriptInfo.java, ScriptMeta.java, SourceInfo.java
│       │   │   └── search/SearchService.java
│       │   ├── model/ResolveRequest.java
│       │   └── utils/Logger.java, CryptoUtils.java, AppLogBuffer.java
│       └── res/
├── docs/
│   ├── LiSync\_Protocol\_Spec.md
│   ├── vela\_quickapp\_integration.md
│   └── custom\_source.md
├── gradle/libs.versions.toml
├── sign/                             # 快应用签名证书（debug/release）
├── lisync.jks / keystore.properties  # Android 签名
├── server\_able\_run.js                # Node 中转服务参考
├── interconnect测试.pdf / interconnect测试.txt
└── 小米穿戴第三方APP能力开放接口文档\_1.4.\*
```

---

## 构建与运行

### Android 应用 (LiSync)

在项目根目录执行：

```powershell
gradlew.bat clean
gradlew.bat assembleDebug
gradlew.bat assembleRelease
gradlew.bat installDebug
gradlew.bat installRelease
gradlew.bat test
gradlew.bat connectedAndroidTest
```

#### 输出位置

* Debug APK: `app/build/outputs/apk/debug/`
* Release APK: `app/build/outputs/apk/release/`

#### 应用配置

* **Application ID / Namespace**: `mindrift.app.music`
* **VersionCode / VersionName**: `1` / `1.0`
* **Compile SDK**: 34
* **Min SDK**: 28
* **Target SDK**: 34

#### 构建差异（实际配置）

|特性|Debug|Release|
|-|-|-|
|Minify|禁用|禁用（`minifyEnabled false`）|
|ProGuard|不应用|使用 `proguard-android-optimize.txt` + `proguard-rules.pro`|
|Signing|若存在 keystore 则用 Release 签名|Release 签名|

---

## 关键模块说明

### XiaomiWearableManager

**路径**: `app/src/main/java/mindrift/app/music/wearable/XiaomiWearableManager.java`

**职责**: 与小米穿戴/小米健康通信的核心管理器。

**关键点**:

* 服务连接监听、节点刷新（3s 节流）与状态订阅/查询（连接/充电/佩戴/睡眠）。
* 权限申请：`Permission.DEVICE\_MANAGER` 与 `Permission.NOTIFY`（AuthApi），通过后挂载 MessageApi 监听。
* 启动手表端快应用：`/pages/init`（`launchWearApp`）。
* 处理消息动作：

  * `getCapabilities` / `capabilities` / `capabilitiesUpdate`（支持 action/type/cmd）
  * `WATCH\_READY` -> `WATCH\_READY\_ACK`
  * `search` / `lyric` / `getLyric`（走内置 SearchService / LyricService）
  * 其他 JSON 请求 -> `RequestProxy`（脚本解析）
  * `upload.start` / `upload.chunk` / `upload.finish` + `upload.ack` / `upload.result`（本地音乐上传，8KB 分片，30s 超时）

* **严格回传 `\_requestId`**（手表端依赖）。

### RequestProxy

**路径**: `app/src/main/java/mindrift/app/music/core/proxy/RequestProxy.java`

**职责**: 解析请求、缓存与脚本路由。

**行为**:

* `action` 默认 `musicUrl`，`quality` 默认 `128k`。
* `targetScriptId` 指定脚本优先；否则轮询可用脚本并逐个尝试。
* 若脚本不支持音质，降级到 `128k`。
* 请求超时：4s（`REQUEST\_TIMEOUT\_MS`）。
* 返回统一结构：`code/message/data/provider/info/url`（当响应含 `url` 时 `data` 为 `url`）。

### ScriptManager

**路径**: `app/src/main/java/mindrift/app/music/core/script/ScriptManager.java`

**职责**: 脚本生命周期与能力管理。

**行为**:

* 脚本目录：`Context.getFilesDir()/scripts`。
* 预加载脚本：`assets/script/user-api-preload.js`（定义 `globalThis.lx` API）。
* 解析脚本头注释（`@name/@version/...`），注册 `SourceInfo` 能力。
* 导入/编辑/重命名/删除脚本后 `loadScripts()`。
* `dispatchRequest` -> QuickJS 异步结果等待。
* `getCapabilitiesSummary()` 仅汇总 `type=music` 且支持 `musicUrl` 的平台能力，排序优先 `tx/wy/kg/kw/mg/local`。

### ScriptContext + LxNativeImpl

**路径**:

* `app/src/main/java/mindrift/app/music/core/script/ScriptContext.java`
* `app/src/main/java/mindrift/app/music/core/engine/LxNativeImpl.java`

**职责**: QuickJS 执行与 JS/Native 桥接。

**能力**:

* `\_\_lx\_native\_\_` 桥接 `request/response/init/showUpdateAlert/cancelRequest` 与 `setTimeout`。
* `lx.request` / `requestSync` 封装 OkHttp 网络请求。
* 提供 AES/RSA/MD5、Buffer、Base64、zlib（inflate/deflate）等工具方法。
* 兼容补丁：对特定 API（`api.music.lerd.dpdns.org`）补充 `source/quality/songid` 字段。

### CacheManager

**路径**: `app/src/main/java/mindrift/app/music/core/cache/CacheManager.java`

**职责**: 播放链接缓存（内存 + 文件）。

**特性**:

* 默认 4 小时过期，写入即保存。
* 文件缓存：`filesDir/cache.json`。
* 定时清理/保存（10 分钟清理，5 分钟持久化）。

### SearchService / LyricService

**路径**:

* `core/search/SearchService.java`
* `core/lyric/LyricService.java`

**特性**:

* 平台：`tx` / `wy` / `kg`（内置直连接口），search 支持平台回退（指定平台 -> tx -> wy -> kg）。
* 5 分钟 LRU 缓存（最多 100 条）。

### HttpClient

**路径**: `app/src/main/java/mindrift/app/music/core/network/HttpClient.java`

**特性**:

* 支持 `GET/POST/PUT/DELETE` 与 body/form/formData（可单次覆盖 timeout）。
* 默认 UA：`lx-music-android/1.0`（见 `NetworkConfig`）。
* 默认超时：15s。

### UI

* **AgreementActivity**：启动协议页，未同意则不进入主界面。
* **MainActivity**：脚本管理 + 设备状态/刷新 + 上传音乐。
* **SettingsActivity**：缓存/日志查看与复制、脚本能力展示、请求测试（musicUrl/search/lyric）。

---

## 支持的平台与操作

### 脚本能力（由脚本声明）

常见平台代码：`tx` / `wy` / `kg` / `kw` / `mg` / `local`。  
脚本可声明支持的 `actions` 与 `qualitys`，`local` 支持 `musicUrl/lyric/pic`（手表端能力推送仅聚合 `musicUrl`）。

### 内置能力

* 搜索：`tx / wy / kg`
* 歌词：`tx / wy / kg`

---

## 请求与响应（关键字段）

### 通用请求字段（ResolveRequest）

* `source` 或 `platform`：平台代码。
* `songid` / `id` / `songId` / `songID`：歌曲 ID。
* `musicInfo.songmid` / `musicInfo.hash`：歌曲 ID（优先级高于 `songid`）。
* `action`：默认 `musicUrl`。
* `quality`：默认 `128k`。
* `nocache`：跳过缓存。
* `targetScriptId`：指定脚本（可选）。

### 手表通信关键点

* **请求必须携带 `\_requestId`**，手机端响应必须原样回传。
* 详细协议见：`docs/LiSync\_Protocol\_Spec.md` 与 `docs/vela\_quickapp\_integration.md`。

---

## 关键配置与运行时目录

* **明文 HTTP**：`app/src/main/res/xml/network\_security\_config.xml` 允许明文流量。
* **脚本目录**：`filesDir/scripts`
* **缓存文件**：`filesDir/cache.json`
* **Room schema**：`app/schemas/`

---

## 开发规范

* Java：遵循 Android 官方代码风格。
* 命名：

  * 类名 PascalCase
  * 方法/变量 camelCase
  * 常量 UPPER\_SNAKE\_CASE
  * 私有成员前缀 `m`

* 包结构（核心）：`mindrift.app.music.\*`

---

## 相关文档

* `docs/LiSync\_Protocol\_Spec.md` - 通信协议规范
* `docs/vela\_quickapp\_integration.md` - 手表快应用接入指南
* `docs/custom\_source.md` - 自定义音源脚本说明
* `GEMINI.md` - 项目生态与参考说明
* `plan.md` - 项目实施计划
* `sl.md` - 搜索/歌词逻辑参考
* `interconnect测试.pdf` / `interconnect测试.txt` - 互联互通测试
* `小米穿戴第三方APP能力开放接入文档.*` - SDK 接入文档
* `小米穿戴第三方APP能力开放接口文档\_1.4.\*` - SDK 文档

---

## 快速开始

```powershell
cd F:\\Project\\LiSynchronization
gradlew.bat assembleDebug
```

---

**最后更新**: 2026-02-04  
**维护者**: Mindrift Team
