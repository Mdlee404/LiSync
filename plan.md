# LiSynchronization Android APP 详细实施计划 (Java 修订版)

## 项目概述

**项目名称**：LiSynchronization
**项目类型**：Android 应用
**包名**：mindrift.app.lisynchronization
**开发目标**：开发一个 Android 应用，作为落雪音乐音源的中转服务，兼容小米穿戴设备。核心聚焦于高效的消息转发与脚本执行，剥离非必要的设备状态监控与通知功能。

---

## 目录

1. [项目背景与需求分析](#1-项目背景与需求分析)
2. [技术选型](#2-技术选型)
3. [系统架构设计](#3-系统架构设计)
4. [核心模块设计](#4-核心模块设计)
5. [数据流设计](#5-数据流设计)
6. [详细实施步骤](#6-详细实施步骤)

---

## 1. 项目背景与需求分析

### 1.1 业务背景
将落雪音乐的音源中转能力移植到 Android 平台，通过小米穿戴 SDK 与手表设备集成，让手表设备能够通过手机 APP 获取各音乐平台的播放链接。

### 1.2 核心需求

#### 1.2.1 音源中转服务
*   **输入**：接收手表设备发起的请求
*   **输出**：返回播放链接或数据

#### 1.2.2 JavaScript 运行环境
*   提供与落雪音乐桌面版完全兼容的 JavaScript 运行环境 (QuickJS)。
*   支持执行用户导入的音源脚本。
*   **保留弹窗能力**：支持脚本触发的 `updateAlert` 事件，在手机端进行提示。

#### 1.2.3 脚本管理
*   本地导入与 URL 下载。
*   **指定音源支持**：请求中可携带指定脚本 ID，系统将优先使用该脚本，跳过负载均衡。

#### 1.2.4 小米穿戴集成 (精简版)
*   **仅保留**：设备连接监听、应用间消息通信 (MessageApi)。
*   **移除**：设备状态订阅 (电量/佩戴/睡眠)、系统通知推送 (NotifyApi)、固件/APP安装引导。

#### 1.2.5 网络请求
*   直接请求，不使用代理。
*   支持完整的 HTTP 选项。

#### 1.2.6 缓存系统
*   内存 + 持久化双重缓存。
*   **过期时间**：**4 小时**。

#### 1.2.7 负载均衡
*   默认：Round-Robin 轮询机制。
*   例外：当请求指定了 `targetScriptId` 时，直接使用指定脚本。

---

## 2. 技术选型 (已确立)

*   **语言**：Java (JDK 1.8)
*   **JS 引擎**：QuickJS (quickjs-android)
*   **网络**：OkHttp 4.12.0
*   **JSON**：Gson 2.10.1
*   **异步处理**：ExecutorService / CompletableFuture (Android 24+)
*   **SDK**：小米穿戴 SDK (仅 MessageApi, NodeApi, AuthApi)

---

## 3. 系统架构设计

### 3.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        小米手表                                  │
└────────────────────────────┬────────────────────────────────────┘
                             │ JSON 请求 (含可选 targetScriptId)
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                  XiaomiWearableManager                          │
│  - 监听连接 (NodeApi)                                           │
│  - 接收消息 (MessageApi)                                        │
│  - 发送响应 (MessageApi)                                        │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    RequestProxy (核心)                           │
│  1. 检查缓存 (4小时过期)                                         │
│  2. 路由策略:                                                    │
│     IF targetScriptId -> 定向分发                                │
│     ELSE -> 负载均衡 (Round-Robin)                               │
│  3. 调用脚本执行器                                               │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                   ScriptExecutor (QuickJS)                       │
│  - 执行 lx.request()                                             │
│  - 处理 lx.send('updateAlert') -> UI Toast/Dialog                │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 模块划分

*   **wearable/**: 仅包含 `XiaomiWearableManager` (负责连接和消息) 和 `XiaomiMessageHandler`。
*   **core/proxy/**: `RequestProxy` 实现带优先级的路由逻辑。
*   **core/cache/**: `CacheManager` 配置 4 小时过期。

---

## 4. 核心模块设计

### 4.1 RequestProxy (路由策略更新 - Java)

```java
public class RequestProxy {
    private ScriptManager scriptManager;
    private CacheManager cacheManager;

    public void resolve(ResolveRequest request, ResolveCallback callback) {
        // 1. 检查缓存 (略)

        // 2. 选择音源处理器
        ScriptHandler provider;
        if (request.getTargetScriptId() != null && !request.getTargetScriptId().isEmpty()) {
            // A. 指定模式
            provider = scriptManager.getHandlerById(request.getPlatform(), request.getTargetScriptId());
        } else {
            // B. 轮询模式
            provider = scriptManager.getNextHandler(request.getPlatform());
        }

        if (provider == null) {
            callback.onFailure(new Exception("No provider found"));
            return;
        }

        // 3. 执行 (略)
    }
}
```

### 4.2 LxObject (弹窗支持 - Java)

```java
public class LxSendCallback {
    private ScriptContext context;

    public Promise call(String eventName, Object data) {
        return Promise.resolve(null).then(result -> {
            switch (eventName) {
                case "inited":
                    // 初始化逻辑
                    break;
                case "updateAlert":
                    // 保留弹窗逻辑
                    Logger.info("[Alert] " + context.getFileName() + ": " + data);
                    UiNotifier.showToast("脚本通知: " + data);
                    break;
            }
            return null;
        });
    }
}
```

### 4.3 缓存配置

```java
public class CacheManager {
    private static final long CACHE_DURATION = 4 * 60 * 60 * 1000L; // 4小时
    // ...
}
```

---

## 5. 数据流设计

### 5.1 请求消息格式

```json
{
  "platform": "tx",
  "songid": "123456",
  "quality": "320k",
  "action": "musicUrl",
  "nocache": false,
  "targetScriptId": "user_script_v1.js"  // [新增] 可选，指定脚本文件名
}
```

---

## 6. 详细实施步骤 (精简后)

1.  **基础配置** (Done): Gradle 依赖配置。
2.  **核心模型**: 创建 `ResolveRequest`, `ResolveResponse` 等 Java 类。
3.  **工具类**: 实现 `CryptoUtils`, `Logger` 等。
4.  **脚本引擎**: 集成 QuickJS，实现 `LxObject` (含弹窗支持)。
5.  **脚本管理**: 实现脚本加载、解析、指定查找与轮询逻辑。
6.  **网络与缓存**: 实现 OkHttp 封装与 4 小时缓存机制。
7.  **穿戴集成**: 实现 `MessageApi` 收发，**跳过**所有状态订阅与通知代码。
8.  **主流程联调**: 串联各个模块，验证“收到消息 -> 路由 -> 执行 -> 返回”。