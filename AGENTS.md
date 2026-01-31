# AGENTS.md - LiSynchronization 项目上下文文档

本文档为 AI 代理提供 LiSynchronization 项目的全面上下文信息，帮助快速理解项目结构、开发流程和核心功能。

---

## 项目概述

**LiSynchronization** 是一个 Android 原生应用，主要功能是作为落雪音乐的音源中转服务，并兼容小米穿戴设备。该应用允许手表设备通过手机应用获取各音乐平台的播放链接。

### 核心功能

- **音源中转服务**：通过 QuickJS JavaScript 引擎动态执行音源脚本，支持多个音乐平台
- **小米穿戴集成**：与小米手表设备通信，提供音乐播放链接获取服务
- **脚本管理**：支持本地导入和 URL 下载音源脚本
- **智能缓存**：4小时双重缓存（内存+文件），提升响应速度
- **负载均衡**：支持指定脚本和轮询负载均衡两种路由模式

### 项目架构

```
小米手表设备
    ↓ JSON 请求
XiaomiWearableManager (消息收发)
    ↓
RequestProxy (缓存检查 + 路由)
    ↓
ScriptManager (脚本管理)
    ↓
ScriptContext + LxNativeImpl (QuickJS 执行)
    ↓
HttpClient (网络请求)
    ↓
返回响应
```

---

## 技术栈

### Android 应用 (LiSynchronization)

| 组件 | 技术/库 | 版本 |
|------|---------|------|
| 开发语言 | Java | 1.8 |
| JavaScript 引擎 | QuickJS (quickjs-android) | 2.4.0 |
| 网络请求 | OkHttp | 4.12.0 |
| JSON 处理 | Gson | 2.10.1 |
| 缓存存储 | Room | 2.6.1 |
| UI 组件 | Material Design | 1.13.0 |
| 小米穿戴 SDK | xms-wearable-lib | 1.4 |
| 生命周期组件 | lifecycle-common-java8 | 2.8.7 |

### React Native 应用 (lx-music-mobile - 参考项目)

| 组件 | 技术/库 | 版本 |
|------|---------|------|
| 框架 | React Native | 0.73.11 |
| 应用版本 | lx-music-mobile | 1.8.0 |
| 导航 | react-native-navigation | 7.39.2 |
| 音频播放 | react-native-track-player | - |

---

## 项目结构

```
F:\Project\LiSynchronization\
├── app/                           # Android 应用主模块
│   ├── build.gradle               # Android 构建配置
│   ├── proguard-rules.pro         # ProGuard 规则
│   ├── libs/                      # 第三方库
│   │   └── xms-wearable-lib_1.4_release.aar  # 小米穿戴 SDK
│   └── src/main/
│       ├── AndroidManifest.xml    # 应用清单
│       ├── assets/                # 资源文件
│       ├── java/mindrift/app/lisynchronization/
│       │   ├── App.java           # Application 入口
│       │   ├── ui/
│       │   │   └── MainActivity.java  # 主 Activity
│       │   ├── wearable/
│       │   │   └── XiaomiWearableManager.java  # 小米穿戴管理
│       │   ├── core/
│       │   │   ├── cache/         # 缓存管理
│       │   │   │   ├── CacheManager.java
│       │   │   │   └── CacheEntry.java
│       │   │   ├── engine/        # 执行引擎
│       │   │   │   ├── LxNativeInterface.java
│       │   │   │   ├── LxNativeImpl.java
│       │   │   │   └── ScriptContext.java
│       │   │   ├── network/       # 网络请求
│       │   │   │   ├── HttpClient.java
│       │   │   │   ├── HttpUtils.java
│       │   │   │   └── NetworkConfig.java
│       │   │   ├── proxy/         # 请求代理
│       │   │   │   ├── RequestProxy.java
│       │   │   │   └── ScriptHandler.java
│       │   │   └── script/        # 脚本管理
│       │   │       ├── ScriptManager.java
│       │   │       ├── ScriptContext.java
│       │   │       ├── ScriptInfo.java
│       │   │       ├── ScriptMeta.java
│       │   │       └── SourceInfo.java
│       │   ├── model/             # 数据模型
│       │   │   └── ResolveRequest.java
│       │   └── utils/             # 工具类
│       │       ├── Logger.java
│       │       ├── CryptoUtils.java
│       │       └── AppLogBuffer.java
│       └── res/                   # Android 资源
├── lx-music-mobile/               # React Native 音乐播放器（参考）
│   ├── package.json               # NPM 配置和脚本
│   ├── src/                       # 源代码
│   ├── android/                   # Android 平台配置
│   │   ├── build.gradle
│   │   └── app/build.gradle
│   └── ios/                       # iOS 平台配置
├── gradle/                        # Gradle 配置
│   └── libs.versions.toml         # 版本目录
├── plan.md                        # 项目详细实施计划
├── server_able_run.js             # Node.js 中间件服务器（参考）
├── 小米穿戴第三方APP能力开放接口文档_1.4.txt  # SDK 文档
└── settings.gradle                # Gradle 项目设置
```

---

## 构建和运行

### Android 应用 (LiSynchronization)

#### 前置要求

- JDK 1.8 或更高版本
- Android SDK API 34
- Android SDK Build Tools

#### 构建命令

从项目根目录 `F:\Project\LiSynchronization` 执行：

```powershell
# 清理构建
gradlew.bat clean

# 构建 Debug APK
gradlew.bat assembleDebug

# 构建 Release APK
gradlew.bat assembleRelease

# 安装 Debug APK 到连接的设备
gradlew.bat installDebug

# 安装 Release APK 到连接的设备
gradlew.bat installRelease

# 运行单元测试
gradlew.bat test

# 运行 Android 仪器测试
gradlew.bat connectedAndroidTest
```

#### 输出位置

- Debug APK: `app/build/outputs/apk/debug/`
- Release APK: `app/build/outputs/apk/release/`

#### 应用配置

- **Application ID**: `mindrift.app.lisynchronization`
- **Compile SDK**: 34
- **Min SDK**: 28 (Android 9.0)
- **Target SDK**: 34 (Android 14)
- **Java Version**: 1.8

#### 特殊配置

1. **小米 SDK**: AAR 文件位于 `app/libs/xms-wearable-lib_1.4_release.aar`
2. **Room Schema**: 生成的数据库架构存储在 `app/schemas/`
3. **ProGuard 规则**:
   ```proguard
   -dontwarn com.xiaomi.xms.**
   -dontwarn app.cash.quickjs.**
   ```

### React Native 应用 (lx-music-mobile)

#### 前置要求

- Node.js >= 18
- npm >= 8.5.2
- React Native 开发环境

#### 构建命令

```powershell
# 导航到 React Native 项目目录
cd F:\Project\LiSynchronization\lx-music-mobile

# 首次安装依赖
npm install

# 开发模式运行
npm run dev

# 构建 Release APK
npm run pack:android

# 构建 Debug APK
npm run pack:android:debug

# 清理 Gradle 构建
npm run clear

# 完全清理（包括 git，保留密钥库）
npm run clear:full

# 启动 Metro Bundler
npm start

# 重置缓存启动 Metro
npm run sc

# 运行 ESLint
npm run lint

# 自动修复 ESLint 问题
npm run lint:fix

# 打包 JavaScript (Android)
npm run bundle-android
```

#### 可用脚本

| 命令 | 描述 |
|------|------|
| `npm run dev` | 以开发模式运行 Android |
| `npm run ios` | 以开发模式运行 iOS |
| `npm start` | 启动 Metro bundler |
| `npm run sc` | 启动 Metro 并重置缓存 |
| `npm run lint` | 运行 ESLint |
| `npm run lint:fix` | 自动修复 ESLint 问题 |
| `npm run pack:android` | 构建 Release APK |
| `npm run pack:android:debug` | 构建 Debug APK |

#### 输出位置

- APK: `android/app/build/outputs/apk/`
- Universal: `lx-music-mobile-v{version}-universal.apk`
- Per-arch: `lx-music-mobile-v{version}-{abi}.apk`
  - `armeabi-v7a`
  - `x86`
  - `arm64-v8a`
  - `x86_64`

#### Release 构建注意事项

1. 需要在 `android/` 目录中创建 `keystore.properties` 文件
2. ProGuard 在 Release 构建中启用
3. Hermes 和 JSC 都支持

---

## 开发规范

### 代码风格

- **Java**: 遵循 Android 官方代码风格指南
- **JavaScript/TypeScript**: 遵循 React Native 社区规范
- **文件命名**: PascalCase 用于类文件，camelCase 用于方法和变量

### 包结构

Android 应用采用模块化包结构：

```
mindrift.app.lisynchronization/
├── ui/           # UI 相关组件
├── wearable/     # 小米穿戴集成
├── core/         # 核心功能
│   ├── cache/    # 缓存
│   ├── engine/   # 执行引擎
│   ├── network/  # 网络请求
│   ├── proxy/    # 请求代理
│   └── script/   # 脚本管理
├── model/        # 数据模型
└── utils/        # 工具类
```

### 命名约定

- **类名**: PascalCase (例如: `ScriptManager`)
- **方法名**: camelCase (例如: `getScriptById`)
- **常量**: UPPER_SNAKE_CASE (例如: `CACHE_EXPIRY_HOURS`)
- **私有成员**: camelCase 前缀 `m` (例如: `mContext`)

### 版本控制

- 主分支: `main`
- 提交信息: 清晰、简洁、描述变更原因
- 分支策略: 功能分支 (feature/)、修复分支 (fix/)

### 测试

- **单元测试**: 使用 JUnit
- **Android 测试**: 使用 AndroidJUnit4
- **测试命令**:
  ```powershell
  # Android 应用
  gradlew.bat test
  gradlew.bat connectedAndroidTest

  # React Native 应用
  npm run lint
  ```

### 构建差异

| 特性 | Debug | Release |
|------|-------|---------|
| **Android 应用** |
| Minification | 禁用 | 启用 |
| ProGuard | 不应用 | 应用 |
| Signing | Debug keystore | Release keystore |
| **React Native 应用** |
| Metro Bundler | 实时重载 | 打包 JS |
| ProGuard | 禁用 | 启用 |
| APK Splitting | 是 | 是 |
| Universal APK | 生成 | 生成 |

---

## 核心模块

### XiaomiWearableManager

**路径**: `app/src/main/java/mindrift/app/lisynchronization/wearable/XiaomiWearableManager.java`

**职责**: 负责与小米穿戴设备通信

**功能**:
- 监听设备连接状态
- 接收来自手表的 JSON 请求
- 发送响应回手表
- 管理权限申请

**关键 API**:
```java
// 监听消息
messageApi.addListener(nodeId, messageListener);
// 发送消息
messageApi.sendMessage(nodeId, data);
```

### RequestProxy

**路径**: `app/src/main/java/mindrift/app/lisynchronization/core/proxy/RequestProxy.java`

**职责**: 核心请求处理模块

**功能**:
- 缓存检查（4小时过期）
- 路由策略（指定脚本 vs 轮询负载均衡）
- 调用脚本执行器

**路由逻辑**:
```java
if (request.getTargetScriptId() != null && !request.getTargetScriptId().isEmpty()) {
    // 指定模式
    provider = scriptManager.getHandlerById(source, targetScriptId);
} else {
    // 轮询模式
    provider = scriptManager.getNextHandler(source);
}
```

### ScriptManager

**路径**: `app/src/main/java/mindrift/app/lisynchronization/core/script/ScriptManager.java`

**职责**: 脚本管理模块

**功能**:
- 从本地文件或 URL 加载音源脚本
- 解析脚本元数据
- 注册脚本支持的音源
- 轮询索引管理

### ScriptContext

**路径**: `app/src/main/java/mindrift/app/lisynchronization/core/script/ScriptContext.java`

**职责**: QuickJS JavaScript 执行环境

**功能**:
- 初始化 JS 上下文
- 注入 native API
- 异步结果等待机制
- 脚本生命周期管理

### LxNativeImpl

**路径**: `app/src/main/java/mindrift/app/lisynchronization/core/engine/LxNativeImpl.java`

**职责**: LxObject 原生实现

**功能**:
- 提供 lx.request() 网络请求
- 加密工具（AES/RSA/MD5）
- Buffer 操作
- zlib 压缩/解压

### CacheManager

**路径**: `app/src/main/java/mindrift/app/lisynchronization/core/cache/CacheManager.java`

**职责**: 缓存管理模块

**功能**:
- 内存 + 文件持久化
- 4小时过期时间
- 定期清理过期缓存

### HttpClient

**路径**: `app/src/main/java/mindrift/app/lisynchronization/core/network/HttpClient.java`

**职责**: 网络请求封装

**功能**:
- 基于 OkHttp
- 支持 GET/POST/PUT/DELETE
- 支持 body/form/formData
- 同步/异步请求

---

## 支持的音源平台

根据代码分析，支持以下音乐平台：

| 平台代码 | 平台名称 |
|---------|---------|
| tx | 腾讯音乐 |
| wy | 网易云音乐 |
| kg | 酷狗音乐 |
| kw | 酷我音乐 |
| mg | 咪咕音乐 |

### 支持的操作类型

- `musicUrl`: 获取音乐播放链接
- `lyric`: 获取歌词
- `pic`: 获取封面图片

---

## 数据流

### 请求消息格式

```json
{
  "platform": "tx",
  "songid": "123456",
  "quality": "320k",
  "action": "musicUrl",
  "nocache": false,
  "targetScriptId": "user_script_v1.js"
}
```

**字段说明**:
- `platform`: 音乐平台代码
- `songid`: 歌曲ID
- `quality`: 音质（如: 128k, 320k, flac）
- `action`: 操作类型（musicUrl/lyric/pic）
- `nocache`: 是否跳过缓存
- `targetScriptId`: 指定脚本ID（可选）

### 响应消息格式

```json
{
  "data": "https://example.com/music.mp3",
  "provider": "user_script_v1.js",
  "url": "https://example.com/music.mp3"
}
```

**字段说明**:
- `data`: 返回的数据（URL/歌词/图片）
- `provider`: 提供服务的脚本ID
- `url`: 链接地址

---

## 关键配置文件

| 文件路径 | 作用 |
|---------|------|
| `settings.gradle` | Gradle 项目设置，定义模块 |
| `gradle/libs.versions.toml` | 版本目录，管理依赖版本 |
| `app/build.gradle` | Android 应用构建配置，依赖管理 |
| `app/src/main/AndroidManifest.xml` | 应用清单，权限声明 |
| `app/proguard-rules.pro` | ProGuard 混淆规则 |
| `plan.md` | 项目详细实施计划文档 |
| `小米穿戴第三方APP能力开放接口文档_1.4.txt` | 小米穿戴 SDK API 文档 |
| `server_able_run.js` | Node.js 版本中间件服务器（参考实现） |

---

## 小米穿戴 SDK 集成

### 主要 API

- **NodeApi**: 设备管理（连接、断开）
- **MessageApi**: 消息通信（发送、接收）
- **AuthApi**: 权限管理

### 所需权限

```xml
<uses-permission android:name="com.xiaomi.xms.permission.DEVICE_MANAGER" />
```

### 兼容性

- 支持 Android R (API 30) 及以上版本
- 小米穿戴 SDK 版本 1.4

### 测试流程

1. 安装手表端快应用
2. 安装手机端应用
3. 权限申请
4. 消息收发测试

---

## 依赖管理

### Android 应用核心依赖

```gradle
// QuickJS JavaScript 引擎
implementation 'wang.harlon.quickjs:wrapper-android:2.4.0'

// 网络请求
implementation 'com.squareup.okhttp3:okhttp:4.12.0'

// JSON 处理
implementation 'com.google.code.gson:gson:2.10.1'

// 数据库/缓存
implementation 'androidx.room:room-runtime:2.6.1'

// 生命周期
implementation 'androidx.lifecycle:lifecycle-common-java8:2.8.7'

// 小米穿戴 SDK
implementation fileTree(dir: "libs", include: ["*.jar", "*.aar"])
```

### React Native 应用关键依赖

```json
{
  "react-native": "0.73.11",
  "react-native-navigation": "7.39.2",
  "react-native-track-player": "..."
}
```

---

## 注意事项

1. **两个独立应用**: `app/` 和 `lx-music-mobile/` 是两个完全独立的应用，需要分别构建
2. **QuickJS 内存管理**: 执行 JavaScript 脚本时注意内存泄漏
3. **缓存策略**: 默认缓存时间为4小时，可在 `CacheManager` 中调整
4. **负载均衡**: 轮询模式下，脚本按照注册顺序循环执行
5. **权限申请**: 小米穿戴功能需要用户授权
6. **签名配置**: Release 构建需要配置签名密钥

---

## 相关文档

- `plan.md` - 项目详细实施计划
- `小米穿戴第三方APP能力开放接口文档_1.4.txt` - SDK API 文档
- `interconnect测试.txt` - 互联互通测试文档
- `AGENTS.md` - 本文档

---

## 快速开始

### 构建 Android 应用

```powershell
cd F:\Project\LiSynchronization
gradlew.bat clean
gradlew.bat assembleDebug
```

### 构建 React Native 应用

```powershell
cd F:\Project\LiSynchronization\lx-music-mobile
npm install
npm run pack:android
```

### 安装到设备

```powershell
# Android 应用
gradlew.bat installDebug

# React Native 应用
cd lx-music-mobile
npm run dev
```

---

**最后更新**: 2026-01-31
**维护者**: LiSynchronization Team