# 项目审查记录

> 项目适配说明（2026-02-04）
> - 本文件为历史审查记录，未必反映当前代码状态。
> - 本轮已完成代码与文档对齐，新增主题传输与文档归档，详见 `docs/README.md`。

## 0\. 2026-02-06 复查增补（代码现状）

- 版本号已同步为 `1.5-换芯`（`versionCode: 2`），见 `src/manifest.json`。
- 新增主题传输中转页 `pages/theme_download`，与 `src/common/lisync.js`、`src/common/theme_transfer.js` 协作完成主题下发/落盘/应用。
- 主题传输链路对大文件采用更快的响应路径（`theme.file.finish` 立即回执，避免发送端超时取消）。
- 背景图样式统一使用 `background-image: <uri>`（不使用 `url(...)` 包裹），`background-repeat` 未启用；页面内容滚动通过 `scroll` 容器承载，背景层绝对定位保持不滚动。

### 0.1 运行态总览（基于当前代码）

- 入口页：`/pages/init`；设计宽度 480；设备类型 `watch`（见 `src/manifest.json`）。
- 互联通道：使用 `@system.interconnect` 与手机端 LiSync 连接，完成搜索/歌词/播放地址获取与主题传输。
- 主题：内置 light/dark + 自定义主题包（本地/手机端传输）；主题快照常驻 `theme_snapshot`。
- 订阅：设备端进行 license 校验（HMAC-SHA256 + Device ID 绑定），订阅页提供二维码与激活。

### 0.2 关键数据流（精简）

1. **启动**：`init.ux` 读取协议状态 → 跳转 `agreement/home`，并在连接后发送 `WATCH_READY`。
2. **播放**：`app.ux` 维护播放队列与模式；页面触发 `playMusic` → `@system.audio`。
3. **搜索/解析**：`api.js` 通过 `lisync.search/getMusicUrl/getLyric` 请求手机端。
4. **主题传输**：手机端 `theme.*` 消息 → `lisync.js` → `theme_transfer.js` 写盘 → `app.ux` 应用主题。

### 0.3 近期变更点（与旧审查差异）

- **主题传输**：新增中转页与文件落盘流程，支持大文件分片写入与立即回执。
- **背景图**：统一 `background-image: <uri>`，背景层绝对定位，内容层按需滚动。
- **日志**：LiSync 内置日志历史与上传（`log.upload`）。

### 0.4 全量文件审查清单（代码文件）

| 文件 | 作用 | 关键点 |
| --- | --- | --- |
| `src/manifest.json` | 应用清单 | 版本 `1.5-换芯`，入口 `pages/init`，声明 interconnect/audio/request 等能力 |
| `src/app.ux` | 全局状态与播放核心 | 主题快照/验证、License 校验、Lisync 初始化、播放队列/模式/重试 |
| `src/common/api.js` | 搜索/解析 API 封装 | 默认走 Lisync；直连模式开关 `WATCH_DIRECT_ENABLED` 关闭 |
| `src/common/auth_headers.js` | License Header | 统一返回 `X-License` |
| `src/common/base64.js` | Base64 编解码 | 同时提供 `base64ToArrayBuffer` |
| `src/common/config.js` | 配置 | 仍包含 `0.3 Alpha` 版本字段（与 manifest 不同） |
| `src/common/db.js` | 本地 DB | JSON 文件 + 操作队列，保存协议/本地歌曲/收藏/License |
| `src/common/download.js` | 下载管理 | 并行下载音频+歌词，支持取消与落盘 |
| `src/common/kv_storage.js` | KV 存储 | 主题相关键独立文件存储；带刷新日志 |
| `src/common/license.js` | 订阅校验 | HMAC-SHA256、Device ID 绑定、过期校验、结果缓存 |
| `src/common/lisync.js` | 互联通道 | interconnect 收发、搜索/歌词/URL 请求、主题传输、日志上传 |
| `src/common/lrc-parser.js` | 歌词解析 | 兼容多时间标签、offset、返回对象与数组转换 |
| `src/common/lyric.js` | 歌词读取 | 本地优先 + KV 缓存 + 远程拉取 |
| `src/common/screen-util.js` | 屏幕适配 | 圆/方/胶囊屏判定与缩放工具 |
| `src/common/sha256.js` | Hash | 纯 JS SHA256，用于主题校验 |
| `src/common/theme_config.js` | 主题归一化 | 统一缺省字段、背景/按钮路径解析 |
| `src/common/theme_manager.js` | 主题管理 | 本地主题列表、校验（checksums）、应用/删除 |
| `src/common/theme_transfer.js` | 主题传输落盘 | 分片写入、进度统计、完成后写快照 |
| `src/common/utils.js` | 工具 | 时间格式化、短震动、简单防抖 |
| `src/components/InputMethod/InputMethod.ux` | 自定义输入法 | 圆/方/胶囊屏，多键盘（QWERTY/T9），候选与拼音逻辑 |
| `src/components/InputMethod/assets/dic.js` | 词库 | 中文词库数据 |
| `src/components/InputMethod/assets/dicUtil.js` | 词库工具 | 拼音/候选处理辅助 |
| `src/pages/init/init.ux` | 启动页 | 协议判断、Lisync 初始化与 `WATCH_READY` |
| `src/pages/agreement/agreement.ux` | 协议页 | 长文本协议 + 同意入口 |
| `src/pages/home/home.ux` | 首页 | 导航栅格 + 迷你播放器 + 订阅提示 |
| `src/pages/search/search.ux` | 搜索页 | 自定义输入法、列表、下载入口 |
| `src/pages/search/search_old.ux` | 旧搜索页 | UTF-16LE 旧实现，未在 manifest 路由中启用 |
| `src/pages/local/local.ux` | 本地页 | 本地歌曲列表 + 二次确认删除 |
| `src/pages/library/library.ux` | 收藏页 | 收藏列表 + 二次确认删除 |
| `src/pages/player/player.ux` | 播放页 | 歌曲播放/进度/歌词/音量/播放模式 |
| `src/pages/settings/settings.ux` | 设置页 | 数据源、平台、歌词开关/偏移、定时停止、清理 |
| `src/pages/theme/theme.ux` | 主题页 | 内置/本地主题切换与删除，日志上报 |
| `src/pages/theme_download/theme_download.ux` | 主题接收页 | 主题传输中转与进度展示 |
| `src/pages/download/download.ux` | 下载页 | 下载进度与状态提示 |
| `src/pages/subscription/subscription.ux` | 订阅页 | 设备 ID、二维码、激活与清理 |
| `src/pages/about/about.ux` | 关于页 | 版本信息与调试日志面板 |
| `src/assets/config/theme_light.js` | 内置主题 | light 主题参数 |
| `src/assets/config/theme_dark.js` | 内置主题 | dark 主题参数 |
| `src/assets/themes/test_theme/theme.json` | 测试主题 | 示例主题包（含背景图与按钮资源） |
| `src/i18n/defaults.json` | i18n | 默认占位，未见实用入口 |
| `src/i18n/zh-CN.json` | i18n | 中文占位 |
| `src/i18n/en.json` | i18n | 英文占位 |
| `src/config-watch.json` | 设备配置 | 当前为空对象 |
# 氦音乐 (HeMusic) 项目完整概览

## 📋 目录

1. [项目概述](#1-项目概述)
2. [技术架构详解](#2-技术架构详解)
3. [页面模块详解](#3-页面模块详解)
4. [核心模块详解](#4-核心模块详解)
5. [应用核心功能](#5-应用核心功能)
6. [技术亮点](#6-技术亮点)
7. [项目特色功能](#7-项目特色功能)
8. [潜在问题和改进建议](#8-潜在问题和改进建议)
9. [总结](#9-总结)

---

## 1\. 项目概述

### 1.1 基本信息

|项目属性|详情|
|-|-|
|**项目名称**|氦音乐 (HeMusic)|
|**包名**|`mindrift.app.music`|
|**版本**|1.5-换芯 (versionCode: 2)|
|**目标平台**|小米 Vela OS 快应用|
|**设备类型**|智能手表/手环|
|**最低平台版本**|1000|
|**设计基准宽度**|480px|
|**开发语言**|JavaScript (Quick App UX)|

### 1.2 项目简介

氦音乐是一款专为小米智能穿戴设备设计的音乐播放器应用，采用订阅制商业模式。应用支持多平台音乐搜索、在线播放、本地下载、歌词同步显示、主题定制等核心功能，并提供了完善的许可证验证机制。

### 1.3 核心特性

* 🎵 **多平台音乐支持**：通过 Lisync 调用手机端脚本/服务提供的源（平台随手机端配置变化）
* 📥 **离线下载**：支持音频和歌词文件下载
* 🎤 **歌词同步**：实时歌词显示，支持偏移校准
* 🎨 **主题系统**：内置亮色/暗色主题，支持自定义主题下载
* 📦 **主题传输**：手机端通过互联通道下发主题包到设备
* 🔒 **许可证验证**：HMAC-SHA256签名验证，设备绑定
* 📱 **穿戴设备优化**：自定义输入法，触觉反馈，手势支持
* ⚙️ **自定义数据源**：用户可配置自己的API服务器

---

## 2\. 技术架构详解

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                         应用层 (Pages)                           │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐       │
│  │   Init   │──│ Agreement│──│   Home   │──│  Player  │       │
│  │  初始化   │  │   协议    │  │   首页   │  │  播放器  │       │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘       │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐       │
│  │  Search  │  │  Local   │  │ Library  │  │ Settings │       │
│  │   搜索    │  │  本地    │  │  收藏    │  │  设置    │       │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘       │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                     │
│  │  Theme   │  │Download  │  │Subscription│                    │
│  │   主题    │  │  下载    │  │   订阅     │                    │
│  └──────────┘  └──────────┘  └──────────┘                     │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                    应用核心 (app.ux)                            │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  播放控制系统            │   │
│  │  播放列表管理                             │   │
│  │  播放模式切换              │   │
│  │  定时停止功能                       │   │
│  │  错误处理和重试                       │   │
│  │  预加载机制                         │   │
│  │  主题系统                           │   │
│  │  许可证系统                       │   │
│  │  音频事件处理                       │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                   公共模块层 (src/common/)                      │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐           │
│  │   API    │ │    DB    │ │ Download │ │   Theme  │           │
│  │  接口封装 │ │  数据库   │ │  下载管理 │ │  主题管理 │           │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘           │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐           │
│  │  Lyric   │ │  License │ │  Config  │                      │
│  │  歌词处理 │ │  许可证   │ │  配置管理 │                      │
│  └──────────┘ └──────────┘ └──────────┘                      │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐           │
│  │ lrc-parse│ │  Storage │ │   Utils  │ │Screen-Util│          │
│  │  歌词解析 │ │  KV存储  │ │  工具函数 │ │ 屏幕适配  │           │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘           │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│               系统API层 (@system.\*)                            │
│  Audio | Fetch | Request | File | Router | Crypto | Vibrator  │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                  小米 Vela OS 系统层                            │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 数据流图

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  用户操作   │────▶│  页面组件   │────▶│  公共模块   │
│  (触控/手势)│     │  (事件处理) │     │  (业务逻辑) │
└─────────────┘     └─────────────┘     └─────────────┘
                                                │
                                                ▼
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   UI更新    │◀────│  状态同步   │◀────│  API/DB操作 │
│ (主题/歌词) │     │  ($app.$def)│     │  (数据存储) │
└─────────────┘     └─────────────┘     └─────────────┘
                                                │
                                                ▼
                                        ┌─────────────┐
                                        │  系统 API   │
                                        │ (网络/文件) │
                                        └─────────────┘
```

### 2.3 核心设计模式

#### 2.3.1 LRU缓存模式

**位置**: `src/common/api.js`

```javascript
class LRUCache {
  constructor(capacity = 100) {
    this.capacity = capacity;
    this.cache = new Map();
  }

  get(key) {
    if (!this.cache.has(key)) return null;
    const value = this.cache.get(key);
    this.cache.delete(key); // 删除后重新插入，更新访问顺序
    this.cache.set(key, value);
    return value;
  }

  set(key, value) {
    if (this.cache.has(key)) {
      this.cache.delete(key);
    } else if (this.cache.size >= this.capacity) {
      // 删除最久未使用的项
      const firstKey = this.cache.keys().next().value;
      this.cache.delete(firstKey);
    }
    this.cache.set(key, value);
  }
}
```

**优势**:

* 限制缓存大小，防止内存泄漏
* O(1)时间复杂度的读写操作
* 自动淘汰最久未使用的数据

#### 2.3.2 队列模式

**位置**: `src/common/db.js`

```javascript
class DBManager {
  constructor() {
    this.operationQueue = \[];
    this.isProcessing = false;
  }

  async enqueueOperation(operation) {
    return new Promise((resolve, reject) => {
      this.operationQueue.push({ operation, resolve, reject });
      this.processQueue();
    });
  }

  async processQueue() {
    if (this.isProcessing || this.operationQueue.length === 0) return;

    this.isProcessing = true;
    const { operation, resolve, reject } = this.operationQueue.shift();

    try {
      const result = await operation();
      resolve(result);
    } catch (error) {
      reject(error);
    } finally {
      this.isProcessing = false;
      this.processQueue(); // 处理下一个操作
    }
  }
}
```

**优势**:

* 防止并发写入导致的数据冲突
* 保证数据一致性
* 支持事务回滚

#### 2.3.3 策略模式

**位置**: `src/common/auth\_headers.js`

```javascript
export async function getAuthHeadersForUrl(url) {
  if (!isOwnedUrl(url)) return {};

  const isCustomEnabled = await isCustomSourceEnabled();
  const licenseHeader = await getLicenseHeadersForUrl(url);

  if (isCustomEnabled) {
    // 策略1：仅使用许可证头
    return licenseHeader;
  } else {
    // 策略2：当前项目无 JWT，仅使用许可证头
    return { ...licenseHeader };
  }
}
```

**优势**:

* 根据URL所有权和配置动态选择认证策略
* 易于扩展新的认证方式
* 逻辑清晰，易于维护

### 2.4 模块依赖关系图

```
                    ┌──────────────┐
                    │   app.ux     │
                    │  (应用核心)   │
                    └──────┬───────┘
                           │
           ┌───────────────┼───────────────┐
           ▼               ▼               ▼
    ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
    │   Pages     │ │ theme\_config│ │     db      │
    │  (页面层)   │ │  (主题配置) │ │  (数据库)   │
    └──────┬──────┘ └──────┬──────┘ └──────┬──────┘
           │               │               │
           ▼               ▼               ▼
    ┌────────────────────────────────────────────┐
    │            公共模块层                        │
    │  api.js │ download.js │ lyric.js │
    │  │ theme\_manager │ license │ storage │ utils│
    │  │ lrc-parser │ auth\_headers │ custom\_source │
    │  │ config │ base64 │ host │ screen-util    │
    └────────────────────────────────────────────┘
                           │
                           ▼
                    ┌──────────────┐
                    │ @system APIs │
                    └──────────────┘
```

---

## 3\. 页面模块详解

### 3.1 初始化页面 (Init)

**文件位置**: `src/pages/init/init.ux`

**功能描述**:
应用启动时的入口页面，负责检查用户协议状态并引导用户进入相应页面。

**核心逻辑**:

```javascript
async onShow() {
  // 检查用户是否已同意协议
  const hasAccepted = await db.getAgreementStatus();

  if (hasAccepted) {
    // 已同意，跳转到首页
    router.replace({ uri: '/pages/home' });
  } else {
    // 未同意，跳转到协议页面
    router.replace({ uri: '/pages/agreement' });
  }
}
```

**用户体验**:

* 显示应用logo和版本信息
* 显示加载动画
* 快速跳转，无需用户交互

---

### 3.2 协议页面 (Agreement)

**文件位置**: `src/pages/agreement/agreement.ux`

**功能描述**:
显示完整的用户协议和免责声明，用户必须同意后才能使用应用。

**协议内容**:

1. **免责声明与合规声明**

   * 开发者不对使用本应用造成的任何损失负责
   * 本应用仅提供音乐播放服务，不提供音乐内容
   * 用户应遵守当地法律法规

2. **用户服务协议**

   * 用户的权利和义务
   * 服务的范围和限制
   * 知识产权声明

3. **订阅、计费与退款政策**

   * 订阅制的收费模式
   * 自动续费说明
   * 退款条件和流程

4. **隐私政策与数据保护**

   * 收集的数据类型
   * 数据使用目的
   * 数据保护措施

**交互逻辑**:

```javascript
async onAgree() {
  // 保存协议同意状态
  await db.setAgreementStatus(true);
  // 跳转到首页
  router.replace({ uri: '/pages/home' });
}

onDisagree() {
  // 退出应用
  router.back();
}
```

---

### 3.3 首页 (Home)

**文件位置**: `src/pages/home/home.ux`

**功能描述**:
应用的主页面，提供快捷导航到各个功能模块，并显示迷你播放器。

**页面布局**:

```
┌─────────────────────────┐
│      HeMusic            │
│    氦音乐 v1.0          │
├─────────────────────────┤
│                         │
│  ┌──────────┐  ┌──────┐│
│  │   搜索   │  │ 本地 ││
│  └──────────┘  └──────┘│
│                         │
│  ┌──────────┐  ┌──────┐│
│  │   收藏   │  │ 订阅 ││
│  └──────────┘  └──────┘│
│                         │
│  ┌──────────┐  ┌──────┐│
│  │   设置   │  │ 关于 ││
│  └──────────┘  └──────┘│
│                         │
│  ┌───────────────────┐ │
│  │   迷你播放器       │ │
│  │  播放中 - 歌曲名   │ │
│  └───────────────────┘ │
└─────────────────────────┘
```

**核心功能**:

1. **快捷导航**: 提供搜索、本地、收藏、订阅、设置、关于六个功能入口
2. **迷你播放器**: 显示当前播放歌曲，点击跳转到播放器页面
3. **主题应用**: 使用主题快照快速加载，避免闪烁
4. **许可证检查**: 启动时检查许可证状态

**关键代码**:

```javascript
async onShow() {
  // 应用主题快照
  this.applyThemeSnapshot();

  // 检查许可证状态
  const licenseStatus = this.$app.$def.data.licenseStatus;
  if (!licenseStatus || !licenseStatus.valid) {
    this.showLicenseWarning();
  }

  // 更新播放器信息
  this.updateMiniPlayer();
}

applyThemeSnapshot() {
  const appDef = this.$app \&\& this.$app.$def;
  if (appDef \&\& appDef.data.themeSnapshot) {
    const snapshot = appDef.data.themeSnapshot;
    this.themeColors = snapshot.colors;
    this.themeIcons = snapshot.icons;
  }
}

goPage(uri) {
  vibrateShort(); // 震动反馈
  router.push({ uri: uri });
}
```

---

### 3.4 播放器页面 (Player)

**文件位置**: `src/pages/player/player.ux`

**功能描述**:
核心播放控制页面，提供完整的音乐播放功能和歌词显示。

**页面布局**:

```
┌─────────────────────────┐
│ ◀  返回                 │
├─────────────────────────┤
│                         │
│      \[专辑封面]         │
│                         │
│      歌曲标题           │
│      歌手名称           │
│                         │
│   \[进度条]  01:23/03:45│
│                         │
│   ⏮️  ⏯️  ⏭️           │
│                         │
│   \[歌词区域]            │
│   歌词第一行            │
│   当前歌词高亮          │
│   歌词第三行            │
│                         │
│   🎵 🔄 ❤️ ⏱️ ⬇️        │
└─────────────────────────┘
```

**核心功能**:

1. **播放控制**

   * 播放/暂停
   * 上一曲/下一曲
   * 进度条拖动
   * 音量调节

2. **播放模式**

   * 顺序播放 (sequential)
   * 随机播放 (shuffle)
   * 单曲循环 (loop)

3. **歌词显示**

   * 实时同步
   * 平滑滚动
   * 偏移校准
   * 字体大小调节

4. **其他功能**

   * 收藏歌曲
   * 定时停止
   * 下载歌曲
   * 歌词显示开关

**歌词同步机制**:

```javascript
// 使用二分查找快速定位当前歌词
updateLyricDisplay() {
  const currentTime = audio.currentTime;
  const lyricIndex = getCurrentLyricIndex(this.lyrics, currentTime);

  if (lyricIndex !== this.currentLyricIndex) {
    this.currentLyricIndex = lyricIndex;
    const currentLyric = this.lyrics\[lyricIndex];

    // 平滑滚动到当前歌词
    this.scrollToLyric(lyricIndex);

    // 高亮当前歌词
    this.lyricHighlightIndex = lyricIndex;
  }
}

// 滚动到指定歌词行
scrollToLyric(index) {
  const scrollAmount = index \* this.lyricLineHeight;
  this.lyricScrollTop = scrollAmount - this.lyricContainerHeight / 2;
}
```

**状态同步**:

```javascript
updateUI() {
  const app = (this.$app \&\& this.$app.$def) ? this.$app.$def.data : null;
  if (!app) return;

  // 同步播放状态
  this.isPlaying = app.isPlaying;

  // 同步播放模式
  this.playModeText = this.getPlayModeText(app.playMode);

  // 同步当前歌曲
  if (app.currentSong \&\& app.currentSong.id !== this.currentSongId) {
    this.syncState();
    this.currentSongId = app.currentSong.id;
  }
}
```

**播放模式切换**:

```javascript
togglePlayMode() {
  this.$app.$def.togglePlayMode();
  this.updateUI();
}

getPlayModeText(mode) {
  const modeNames = {
    'sequential': '顺序',
    'shuffle': '随机',
    'loop': '循环'
  };
  return modeNames\[mode] || '顺序';
}
```

---

### 3.5 搜索页面 (Search)

**文件位置**: `src/pages/search/search.ux`

**功能描述**:
搜索音乐，支持多平台搜索，在线播放和下载。

**页面布局**:

```
┌─────────────────────────┐
│ ◀  搜索                │
├─────────────────────────┤
│  \[搜索框]               │
│  \[平台选择] \[搜索]      │
├─────────────────────────┤
│  搜索结果 (1/5)         │
│                         │
│  1. 歌曲名 - 歌手       │
│     \[播放] \[下载]       │
│                         │
│  2. 歌曲名 - 歌手 ⬇️   │
│     \[播放] \[下载]       │
│                         │
│  3. 歌曲名 - 歌手       │
│     \[播放] \[下载]       │
│                         │
│  \[加载更多]             │
└─────────────────────────┘
```

**核心功能**:

1. **自定义输入法**

   * 集成InputMethod组件
   * 支持拼音输入
   * 支持数字和符号输入

2. **多平台搜索（经 Lisync 手机端脚本）**

   * QQ音乐 (tx)
   * 网易云音乐 (wy)
   * 酷狗音乐 (kg)
   * 平台枚举由设置页固定配置，实际数据由手机端脚本提供

3. **搜索结果**

   * 分页显示
   * 本地歌曲标记
   * 在线播放
   * 下载功能

**搜索实现**:

```javascript
async doSearch() {
  if (!this.keyword.trim()) {
    prompt.showToast({ message: '请输入搜索关键词' });
    return;
  }

  this.isLoading = true;
  this.resultList = \[];

  try {
    // 调用API搜索
    const results = await api.search(this.keyword, this.currentPage);

    // 标记本地已下载的歌曲
    const localMap = await this.getLocalSongMap();

    this.resultList = results.map(item => ({
      ...item,
      isDownloaded: !!localMap\[`${item.source}\_${item.id}`]
    }));

    this.totalResults = results.length > 0 ? '...' : '0';
  } catch (error) {
    console.error('搜索失败:', error);
    prompt.showToast({ message: '搜索失败，请重试' });
  } finally {
    this.isLoading = false;
  }
}

async getLocalSongMap() {
  const localSongs = await db.getList();
  const map = {};
  localSongs.forEach(song => {
    map\[`${song.source}\_${song.id}`] = song;
  });
  return map;
}
```

**播放和下载**:

```javascript
async playOnline(item) {
  vibrateShort();

  // 获取音乐URL
  this.loadingSong = item;
  const url = await api.getMusicUrl(item.source, item.id);

  if (!url) {
    prompt.showToast({ message: '无法获取播放地址' });
    this.loadingSong = null;
    return;
  }

  // 添加到播放列表并播放
  const song = {
    id: item.id,
    source: item.source,
    title: item.title,
    artist: item.artist,
    url: url
  };

  this.$app.$def.playMusic(song);
  router.push({ uri: '/pages/player' });
  this.loadingSong = null;
}

downloadSong(item) {
  vibrateShort();

  // 检查是否已下载
  if (item.isDownloaded) {
    prompt.showToast({ message: '歌曲已在本地' });
    return;
  }

  // 跳转到下载页面
  router.push({
    uri: '/pages/download',
    params: {
      song: JSON.stringify(item)
    }
  });
}
```

---

### 3.6 本地音乐页面 (Local)

**文件位置**: `src/pages/local/local.ux`

**功能描述**:
显示已下载的音乐列表，支持播放和删除。

**页面布局**:

```
┌─────────────────────────┐
│ ◀  本地音乐             │
├─────────────────────────┤
│  已下载: 12首           │
│  总大小: 45.6 MB        │
├─────────────────────────┤
│  1. 歌曲名 - 歌手       │
│     3.2 MB             │
│     \[播放] \[删除]       │
│                         │
│  2. 歌曲名 - 歌手       │
│     4.1 MB             │
│     \[播放] \[删除]       │
│                         │
│  ...                    │
└─────────────────────────┘
```

**核心功能**:

1. **显示本地歌曲**

   * 歌曲信息
   * 文件大小
   * 下载时间

2. **播放本地歌曲**

   * 直接播放
   * 添加到播放列表

3. **删除本地歌曲**

   * 双击确认
   * 删除音频和歌词文件
   * 更新数据库

**双击确认机制**:

```javascript
deleteSong(item) {
  // 第一次点击
  if (!this.pendingDeleteItem) {
    this.pendingDeleteItem = item;
    prompt.showToast({ message: '再次点击确认删除' });
    return;
  }

  // 第二次点击，确认删除
  if (this.pendingDeleteItem.id === item.id) {
    this.doDelete(item);
  } else {
    // 取消之前的删除请求，开始新的
    this.pendingDeleteItem = item;
    prompt.showToast({ message: '再次点击确认删除' });
  }
}

async doDelete(item) {
  try {
    // 从数据库删除
    await db.remove(item.id, item.source);

    // 删除音频文件
    if (item.path) {
      await file.delete({ uri: item.path });
    }

    // 删除歌词文件
    if (item.lrcPath) {
      await file.delete({ uri: item.lrcPath });
    }

    // 刷新列表
    await this.loadLocalSongs();

    prompt.showToast({ message: '删除成功' });
  } catch (error) {
    console.error('删除失败:', error);
    prompt.showToast({ message: '删除失败' });
  } finally {
    this.pendingDeleteItem = null;
  }
}
```

**加载本地歌曲**:

```javascript
async loadLocalSongs() {
  this.isLoading = true;

  try {
    const songs = await db.getList();

    // 格式化文件大小
    this.songList = songs.map(song => ({
      ...song,
      sizeFormatted: this.formatSize(song.size)
    }));

    // 获取统计信息
    const stats = await db.getStorageStats();
    this.totalCount = stats.totalSongs;
    this.totalSize = stats.totalSizeFormatted;
  } catch (error) {
    console.error('加载本地歌曲失败:', error);
  } finally {
    this.isLoading = false;
  }
}

formatSize(bytes) {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 \* 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / (1024 \* 1024)).toFixed(1) + ' MB';
}
```

**手势支持**:

```javascript
onPageTouchStart(e) {
  this.touchStartX = e.touches\[0].offsetX;
  this.touchStartTime = Date.now();
}

onPageTouchEnd(e) {
  const endX = e.changedTouches\[0].offsetX;
  const deltaX = endX - this.touchStartX;
  const deltaTime = Date.now() - this.touchStartTime;

  // 右滑返回
  if (deltaX > 100 \&\& deltaTime < 500) {
    this.goBack();
  }
}
```

---

### 3.7 收藏页面 (Library)

**文件位置**: `src/pages/library/library.ux`

**功能描述**:
显示用户收藏的歌曲列表，支持播放和取消收藏。

**核心特点**:

* 与本地下载分离，纯逻辑收藏
* 存储歌曲元数据而非文件路径
* 支持在线和本地歌曲的收藏

**加载收藏列表**:

```javascript
async loadFavorites() {
  this.isLoading = true;

  try {
    const favorites = await db.getFavorites();

    // 获取本地歌曲映射
    const localSongs = await db.getList();
    const localMap = {};
    localSongs.forEach(song => {
      localMap\[`${song.source}\_${song.id}`] = true;
    });

    // 标记本地歌曲
    this.favoritesList = favorites.map(item => ({
      ...item,
      isDownloaded: !!localMap\[`${item.source}\_${item.id}`]
    }));
  } catch (error) {
    console.error('加载收藏失败:', error);
  } finally {
    this.isLoading = false;
  }
}
```

**取消收藏**:

```javascript
async removeFavorite(item) {
  try {
    await db.removeFavorite(item.id, item.source);

    // 从列表中移除
    this.favoritesList = this.favoritesList.filter(
      fav => fav.id !== item.id || fav.source !== item.source
    );

    prompt.showToast({ message: '已取消收藏' });
  } catch (error) {
    console.error('取消收藏失败:', error);
    prompt.showToast({ message: '操作失败' });
  }
}
```

---

### 3.8 设置页面 (Settings)

**文件位置**: `src/pages/settings/settings.ux`

**功能描述**:
提供各种应用设置选项。

**设置项**:

1. **搜索平台**

   * QQ音乐
   * 网易云音乐
   * 酷狗音乐

2. **自定义数据源**

   * 仅配置 Base URL（无显式开关）
   * 存储键：`music_api_base`

3. **歌词设置**

   * 歌词显示开关
   * 歌词时间校准

4. **播放设置**

   * 定时停止
   * 默认播放模式

5. **主题管理**

   * 内置主题
   * 自定义主题

6. **订阅管理**

   * 查看订阅状态
   * 激活订阅

7. **数据管理**

   * 清空配置（保留本地音乐）
   * 清空所有下载

**自定义源配置（当前实现）**:

```javascript
applyCustomSource() {
  const input = this.customSourceInput.trim();

  // 验证URL格式
  const normalized = validateCustomSourceUrl(input);
  if (!normalized) {
    prompt.showToast({ message: '请输入有效的 http(s) 地址' });
    return;
  }

  // 保存配置
  storage.set({ key: 'custom\_source\_url', value: normalized });
  storage.set({ key: 'custom\_source\_enabled', value: 'true' });

  // 清除缓存
  api.clearCache();

  this.customSourceEnabled = true;
  this.customSourceUrl = normalized;

  prompt.showToast({ message: '自定义源已启用' });
}

disableCustomSource() {
  storage.set({ key: 'custom\_source\_enabled', value: 'false' });

  this.customSourceEnabled = false;

  prompt.showToast({ message: '已恢复默认数据源' });
}
```

**歌词时间校准**:

```javascript
async applyLyricOffset() {
  const offset = parseInt(this.lyricOffsetInput);

  if (isNaN(offset) || offset < -10000 || offset > 10000) {
    prompt.showToast({ message: '请输入 -10000 到 10000 之间的数值' });
    return;
  }

  storage.set({ key: 'lyric\_offset', value: offset.toString() });
  this.lyricOffset = offset;

  prompt.showToast({ message: `歌词偏移已设置为 ${offset}ms` });
}
```

**清空配置**:

```javascript
async clearConfig() {
  // 第一次点击
  if (!this.pendingClear) {
    this.pendingClear = true;
    prompt.showToast({ message: '再次点击确认清空' });
    return;
  }

  try {
    // 清空配置，保留本地音乐
    await db.clearConfigKeepMusic();

    // 清除主题快照
    storage.set({ key: 'theme\_snapshot', value: null });

    prompt.showToast({ message: '配置已清空，重启应用生效' });
  } catch (error) {
    console.error('清空配置失败:', error);
    prompt.showToast({ message: '操作失败' });
  } finally {
    this.pendingClear = false;
  }
}
```

---

### 3.9 主题管理页面 (Theme)

**文件位置**: `src/pages/theme/theme.ux`

**功能描述**:
管理应用主题，支持内置主题和自定义主题。

**主题类型**:

1. **内置主题**

   * 极光 (light)
   * 暗夜 (dark)

2. **自定义主题**

   * 从服务器下载
   * 本地存储
   * 可删除

**主题切换流程**:

```javascript
async applyBuiltIn(id) {
  this.isSwitching = true;

  // 保存主题选择
  this.saveThemeChoice(id);

  // 等待主题就绪
  const appDef = (this.$app \&\& this.$app.$def);
  if (appDef \&\& typeof appDef.loadThemeFile === 'function') {
    await Promise.resolve(appDef.loadThemeFile(id));
  }

  // 应用主题
  this.applyThemeWhenReady();
  this.isSwitching = false;
}

applyThemeWhenReady() {
  const appDef = (this.$app \&\& this.$app.$def);
  if (!appDef) return;

  // 等待主题就绪
  appDef.onThemeReady(() => {
    const snapshot = appDef.data.themeSnapshot;
    if (snapshot) {
      this.themeColors = snapshot.colors;
      this.themeIcons = snapshot.icons;
      this.currentThemeId = this.savedThemeId;
    }
  });
}

saveThemeChoice(id) {
  storage.set({ key: 'current\_theme\_id', value: id });
  this.savedThemeId = id;
}
```

**下载自定义主题**:

```javascript
async downloadTheme(themeId) {
  this.isDownloading = true;

  try {
    const result = await themeManager.downloadTheme(themeId);

    if (result.success) {
      await this.loadLocalThemes();
      prompt.showToast({ message: '主题下载成功' });
    } else {
      prompt.showToast({ message: result.message || '下载失败' });
    }
  } catch (error) {
    console.error('主题下载失败:', error);
    prompt.showToast({ message: '下载失败' });
  } finally {
    this.isDownloading = false;
  }
}
```

**删除自定义主题**:

```javascript
async deleteTheme(theme) {
  try {
    const success = await themeManager.deleteTheme(theme.id);

    if (success) {
      // 刷新主题列表
      await this.loadLocalThemes();

      // 如果删除的是当前主题，切换到默认主题
      if (this.currentThemeId === theme.id) {
        await this.applyBuiltIn('light');
      }

      prompt.showToast({ message: '主题已删除' });
    } else {
      prompt.showToast({ message: '删除失败' });
    }
  } catch (error) {
    console.error('删除主题失败:', error);
    prompt.showToast({ message: '操作失败' });
  }
}
```

**当前实现说明**:

- 手表端 `theme_manager.downloadTheme` 需要 `api.getThemeInfo`；目前 watch 端默认返回“暂不支持下载”，主题主要通过手机端传输（`theme_download` 中转页）。

### 3.9.1 主题传输中转页 (ThemeDownload)

**文件位置**: `src/pages/theme_download/theme_download.ux`

**功能描述**:
与手机端互联消息配合，作为主题传输的中转与状态展示页面；在收到 `theme.open` 时由 `src/common/lisync.js` 导航进入。

**关键流程**:

1. `theme.open` → 打开中转页并提示等待发送
2. `theme.init` → 初始化传输状态与目录
3. `theme.file.start/chunk/finish` → 分片写入 `internal://files/themes/<id>/`
4. `theme.finish` → 读取并应用 `theme.json`，写入 `theme_snapshot` 与 `current_theme_id`

**注意事项**:

- 大文件传输需快速回执（避免发送端超时取消）
- 背景图样式使用 `<uri>` 直传（不使用 `url(...)` 包裹）

---

### 3.10 下载页面 (Download)

**文件位置**: `src/pages/download/download.ux`

**功能描述**:
显示歌曲下载进度，支持音频和歌词并行下载。

**下载流程**:

```javascript
async onShow() {
  // 获取歌曲信息
  const params = router.getParams();
  if (params \&\& params.song) {
    try {
      this.song = JSON.parse(params.song);
      await this.startDownload();
    } catch (error) {
      console.error('解析歌曲信息失败:', error);
      prompt.showToast({ message: '参数错误' });
      this.goBack();
    }
  } else {
    this.goBack();
  }
}

async startDownload() {
  this.statusText = '正在解析地址...';
  this.progress = 0;

  try {
    // 检查是否已下载
    const exists = await db.exists(this.song.id, this.song.source);
    if (exists) {
      this.statusText = '歌曲已在本地';
      setTimeout(() => this.goBack(), 1500);
      return;
    }

    // 获取音乐URL
    const url = await api.getMusicUrl(this.song.source, this.song.id);
    if (!url) {
      this.statusText = '无法获取下载地址';
      return;
    }

    this.statusText = '正在下载...';

    // 开始下载
    await download.start(
      this.song,
      () => {
        // 成功回调
        this.statusText = '下载成功';
        this.progress = 100;
        setTimeout(() => this.goBack(), 1500);
      },
      (percent) => {
        // 进度回调
        this.progress = percent;
        this.statusText = `下载中... ${percent}%`;
      },
      (error) => {
        // 错误回调
        this.statusText = '下载失败';
        console.error('下载失败:', error);
      }
    );
  } catch (error) {
    console.error('下载失败:', error);
    this.statusText = '下载失败';
  }
}
```

---

### 3.11 订阅页面 (Subscription)

**文件位置**: `src/pages/subscription/subscription.ux`

**功能描述**:
管理应用订阅，显示设备ID和订阅状态。

**核心功能**:

1. **显示设备ID**

   * 用于订阅激活
   * 二维码展示

2. **订阅状态检查**

   * 检查许可证有效性
   * 显示过期时间

3. **清除订阅**

   * 移除许可证
   * 需要重新激活

**订阅检查**:

```javascript
async checkSubscription() {
  this.isChecking = true;

  try {
    const license = await db.getLicense();
    if (!license) {
      this.subscriptionStatus = '未订阅';
      return;
    }

    const result = await validateLicense(license);

    if (result.valid) {
      this.subscriptionStatus = '已订阅';
      this.expireAt = new Date(result.expireAt \* 1000).toLocaleDateString();

      // 更新应用状态
      const appDef = this.$app \&\& this.$app.$def;
      if (appDef) {
        appDef.data.licenseStatus = result;
        appDef.data.licenseRaw = license;
      }
    } else {
      this.subscriptionStatus = '订阅无效';
      this.statusText = this.getErrorMessage(result.reason);
    }
  } catch (error) {
    console.error('检查订阅失败:', error);
    this.subscriptionStatus = '检查失败';
  } finally {
    this.isChecking = false;
  }
}

getErrorMessage(reason) {
  const messages = {
    'DEVICE\_MISMATCH': '设备不匹配',
    'SIGNATURE\_INVALID': '许可证校验失败',
    'LICENSE\_EXPIRED': '订阅已过期',
    'LICENSE\_INVALID': '许可证无效'
  };
  return messages\[reason] || '未知错误';
}
```

**清除订阅**:

```javascript
async clearSubscription() {
  // 第一次点击
  if (!this.pendingClear) {
    this.pendingClear = true;
    prompt.showToast({ message: '再次点击确认清除' });
    return;
  }

  try {
    // 清除许可证
    await db.setLicense(null);

    // 清除应用状态
    const appDef = this.$app \&\& this.$app.$def;
    if (appDef) {
      appDef.data.licenseStatus = null;
      appDef.data.licenseRaw = null;
    }

    this.subscriptionStatus = '未订阅';
    this.expireAt = '';

    prompt.showToast({ message: '订阅已清除' });
  } catch (error) {
    console.error('清除订阅失败:', error);
    prompt.showToast({ message: '操作失败' });
  } finally {
    this.pendingClear = false;
  }
}
```

---

### 3.12 关于页面 (About)

**文件位置**: `src/pages/about/about.ux`

**功能描述**:
显示应用信息和版权声明。

**页面内容**:

* 应用logo
* 应用名称：氦音乐
* 版本信息：v1.0
* 应用描述
* 免责声明
* 版权信息

---

## 4\. 核心模块详解

### 4.1 API模块 (api.js)

**文件位置**: `src/common/api.js`

**功能描述**:
音乐API客户端，提供多平台搜索、音乐URL获取、歌词获取等功能。

**核心特性**:

1. **LRU缓存**

   * URL缓存：100条，5分钟过期
   * 歌词缓存：100条，5分钟过期

2. **多平台支持**

   * QQ音乐 (tx)
   * 网易云音乐 (wy)
   * 酷狗音乐 (kg)

3. **直连歌词获取**

   * 每个平台特定的歌词API
   * Base64解码支持

**关键API**:

```javascript
// 搜索音乐
search(keyword, page = 1) → Promise<Array<SearchResult>>

// 获取音乐URL
getMusicUrl(source, id) → Promise<string>

// 获取歌词
getLyric(source, id) → Promise<string>

// 获取主题信息
getThemeInfo(themeId) → Promise<Object>

// 清除缓存
clearCache()
```

**数据结构**:

```javascript
// SearchResult
{
  source: 'tx' | 'wy' | 'kg',
  id: string,
  title: string,
  artist: string
}
```

**搜索实现**:

```javascript
async search(keyword, page = 1) {
  const cacheKey = `search\_${keyword}\_${page}`;

  // 检查缓存
  const cached = this.cache.search.get(cacheKey);
  if (cached \&\& Date.now() - cached.timestamp < this.config.cache.searchExpiry) {
    return cached.results;
  }

  // 调用API
  const results = await this.fetchSearchResults(keyword, page);

  // 缓存结果
  this.cache.search.set(cacheKey, {
    results,
    timestamp: Date.now()
  });

  return results;
}
```

---

### 4.2 数据库模块 (db.js)

**文件位置**: `src/common/db.js`

**功能描述**:
结构化数据库管理器，提供数据持久化功能。

**存储位置**: `internal://files/app\_db.json`

**核心特性**:

1. **操作队列**

   * 串行化写入操作
   * 防止并发冲突
   * 自动重试

2. **内存缓存**

   * 减少文件I/O
   * 提升性能

3. **事务保护**

   * 失败回滚
   * 数据一致性

**数据结构**:

```javascript
// Song
{
  id: string,
  source: string,
  title: string,
  artist: string,
  path: string,
  lrcPath: string,
  size: number,
  downloadTime: number
}

// Favorite
{
  id: string,
  source: string,
  title: string,
  artist: string,
  addedAt: number
}

// Database Schema
{
  agreementAccepted: boolean,
  musicList: Array<Song>,
  favorites: Array<Favorite>,
  license: Object | null
}
```

**关键API**:

```javascript
// 协议管理
getAgreementStatus() → Promise<boolean>
setAgreementStatus(status) → Promise<boolean>

// 音乐库管理
getList() → Promise<Array<Song>>
add(song) → Promise<boolean>
remove(id, source) → Promise<Array<Song>>
exists(id, source) → Promise<Song | null>

// 收藏管理
getFavorites() → Promise<Array<Favorite>>
addFavorite(song) → Promise<boolean>
removeFavorite(id, source) → Promise<boolean>
isFavorite(id, source) → Promise<boolean>

// 存储管理
getStorageStats() → Promise<Object>
clearAllDownloads() → Promise<boolean>
clearAll() → Promise<boolean>
clearConfigKeepMusic() → Promise<boolean>

// 许可证管理
getLicense() → Promise<Object | null>
setLicense(license) → Promise<boolean>
```

---

### 4.3 下载模块 (download.js)

**文件位置**: `src/common/download.js`

**功能描述**:
处理音频和歌词下载，支持进度报告和取消。

**存储位置**: `internal://files/music/`

**核心特性**:

1. **并行下载**

   * 音频和歌词同时下载
   * Promise.all实现

2. **进度报告**

   * 实时回调
   * 百分比显示

3. **取消支持**

   * 中断下载
   * 清理临时文件

4. **重试机制**

   * 失败自动重试
   * 最小header重试

**下载流程**:

```javascript
async start(songObj, onSuccess, onProgress, onError) {
  // 检查是否已下载
  const exists = await db.exists(songObj.id, songObj.source);
  if (exists) {
    onSuccess?.();
    return;
  }

  try {
    // 获取音乐URL
    const url = await api.getMusicUrl(songObj.source, songObj.id);
    if (!url) {
      onError?.('无法获取下载地址');
      return;
    }

    // 确保目录存在
    await this.ensureMusicDir();

    // 并行下载音频和歌词
    const \[audioPath, lyricPath] = await Promise.all(\[
      this.downloadAudio(url, songObj, onProgress),
      this.downloadLyric(songObj)
    ]);

    // 保存到数据库
    const savedSong = {
      ...songObj,
      path: audioPath,
      lrcPath: lyricPath,
      downloadTime: Date.now()
    };

    await db.add(savedSong);
    onSuccess?.();

  } catch (error) {
    console.error('下载失败:', error);
    onError?.(error.message);
  }
}
```

**文件命名规则**:

```
音频: {source}\_{id}.mp3
歌词: {source}\_{id}.lrc

示例:
tx\_123456789.mp3
wy\_987654321.lrc
```

---

### 4.4 歌词模块 (lyric.js)

**文件位置**: `src/common/lyric.js`

**功能描述**:
统一歌词获取接口，支持多层缓存。

**缓存策略**:

1. **本地文件** (优先级最高)
2. **KV存储** (中间层)
3. **远程API** (最后获取)

**实现逻辑**:

```javascript
async getLyric(song) {
  // 1. 检查本地文件
  if (song.lrcPath) {
    try {
      const lyric = await this.readLocalLyric(song.lrcPath);
      if (lyric) {
        // 缓存到KV存储
        this.cacheToKV(song, lyric);
        return lyric;
      }
    } catch (error) {
      console.error('读取本地歌词失败:', error);
    }
  }

  // 2. 检查KV存储缓存
  const cached = await this.getFromKVCache(song);
  if (cached) {
    return cached;
  }

  // 3. 从远程API获取
  try {
    const lyric = await api.getLyric(song.source, song.id);
    if (lyric) {
      // 缓存到KV存储
      this.cacheToKV(song, lyric);
      return lyric;
    }
  } catch (error) {
    console.error('获取远程歌词失败:', error);
  }

  return '';
}

async cacheToKV(song, lyric) {
  const cacheKey = `lyric\_cache\_${song.source}\_${song.id}`;
  storage.set({
    key: cacheKey,
    value: lyric
  });
}
```

---

### 4.5 歌词解析器 (lrc-parser.js)

**文件位置**: `src/common/lrc-parser.js`

**功能描述**:
解析LRC格式歌词，支持多种时间格式和偏移。

**支持的时间格式**:

1. `MM:SS.ss` (分钟:秒.毫秒)
2. `HH:MM:SS.ss` (小时:分钟:秒.毫秒)

**核心算法**:

```javascript
function parseLrc(lrcText) {
  const lines = lrcText.split(/\\r\\n|\\r|\\n/);
  const result = {};
  let offsetSeconds = 0;

  for (const line of lines) {
    // 解析offset
    const offsetMatch = line.match(/^\\s\*\\\[offset:(\[+-]?\\d+)\\]/i);
    if (offsetMatch) {
      offsetSeconds = parseInt(offsetMatch\[1], 10) / 1000;
      continue;
    }

    // 解析歌词行
    const timeTags = line.match(/\\\[(\\d{2}):(\\d{2})(?:\\.(\\d{2,3}))?\\]/g);
    const lyricText = line.replace(/\\\[.\*?\\]/g, '').trim();

    if (timeTags \&\& lyricText) {
      for (const tag of timeTags) {
        const match = tag.match(/\\\[(\\d{2}):(\\d{2})(?:\\.(\\d{2,3}))?\\]/);
        if (match) {
          let minutes = parseInt(match\[1], 10);
          let seconds = parseInt(match\[2], 10);
          let milliseconds = match\[3] ? parseInt(match\[3].padEnd(3, '0'), 10) : 0;

          // 支持小时格式
          if (match\[0].includes(':') \&\& match\[0].split(':').length === 3) {
            const parts = match\[0].split(':');
            minutes = parseInt(parts\[1], 10);
            seconds = parseInt(parts\[2], 10);
          }

          let timestamp = minutes \* 60 + seconds + milliseconds / 1000;
          timestamp += offsetSeconds;

          const rounded = Math.round(timestamp \* 1000) / 1000;
          result\[rounded.toString()] = lyricText;
        }
      }
    }
  }

  return result;
}

// 二分查找当前歌词
function getCurrentLyricIndex(lyricObj, currentTime) {
  const sortedLyrics = convertToArray(lyricObj);
  const timestamps = sortedLyrics.map(item => item.time);

  let left = 0;
  let right = timestamps.length - 1;
  let currentIndex = -1;

  while (left <= right) {
    const mid = Math.floor((left + right) / 2);
    if (timestamps\[mid] <= currentTime) {
      currentIndex = mid;
      left = mid + 1;
    } else {
      right = mid - 1;
    }
  }

  return currentIndex;
}
```

**时间复杂度**: O(log n) 使用二分查找

---

### 4.6 许可证模块 (license.js)

**文件位置**: `src/common/license.js`

**功能描述**:
许可证验证和管理，使用HMAC-SHA256签名。

**许可证结构**:

```javascript
{
  payload: {
    device\_id: string,    // 设备ID
    plan\_id: string,      // 订阅计划ID
    expire\_at: number,    // 过期时间（Unix时间戳）
    issued\_at: number     // 签发时间（Unix时间戳）
  },
  signature: string       // HMAC-SHA256签名
}
```

**验证流程**:

```javascript
async validateLicense(licenseObject) {
  const payload = licenseObject.payload;
  const signature = licenseObject.signature;

  // 1. 验证payload结构
  if (!payload || !signature) {
    return { valid: false, reason: 'LICENSE\_INVALID' };
  }

  const requiredFields = \['device\_id', 'plan\_id', 'expire\_at', 'issued\_at'];
  for (const field of requiredFields) {
    if (!(field in payload)) {
      return { valid: false, reason: 'LICENSE\_INVALID' };
    }
  }

  // 2. 验证设备ID
  const deviceId = await getDeviceId();
  if (deviceId !== payload.device\_id) {
    return { valid: false, reason: 'DEVICE\_MISMATCH' };
  }

  // 3. 验证签名
  const payloadString = JSON.stringify(payload);
  const digest = await hmacSha256(payloadString, config.license.secret);

  if (digest !== signature) {
    return { valid: false, reason: 'SIGNATURE\_INVALID' };
  }

  // 4. 验证过期时间
  const now = Math.floor(Date.now() / 1000);
  if (now > payload.expire\_at) {
    return { valid: false, reason: 'LICENSE\_EXPIRED' };
  }

  return { valid: true, expireAt: payload.expire\_at };
}
```

**HMAC-SHA256实现**:

```javascript
async function hmacSha256(message, secret) {
  return new Promise((resolve, reject) => {
    crypto.hmac({
      algo: 'SHA256',
      message: message,
      secret: secret,
      success: (data) => {
        resolve(data.data);
      },
      fail: (error) => {
        reject(error);
      }
    });
  });
}
```

---

### 4.7 JWT模块

**说明**:
当前项目无 `jwt.js`，仅使用 `license` 相关逻辑。

**令牌生命周期**:

```
1. 检查存储的令牌
2. 如果令牌存在且未过期（>60秒），直接返回
3. 如果令牌存在但即将过期，刷新令牌
4. 如果没有令牌或刷新失败，请求新令牌
5. 存储令牌、过期时间和权限
```

**关键API**:

```javascript
// 获取当前令牌（自动刷新）
getToken() → Promise<string>

// 获取设备ID
getDeviceId() → Promise<string>

// 获取Authorization头
getAuthHeader() → Promise<{Authorization: string} | null>

// 清除存储的令牌
clear() → Promise<void>
```

**实现逻辑**:

```javascript
async getToken() {
  // 检查缓存
  const cachedToken = this.cachedToken;
  if (cachedToken \&\& cachedToken.expiresAt > Date.now() + 60000) {
    return cachedToken.token;
  }

  // 检查存储
  const storedToken = await this.getStoredToken();
  if (storedToken) {
    const { token, expiresAt } = storedToken;

    // 即将过期，刷新
    if (expiresAt <= Date.now() + 60000) {
      return await this.refreshToken();
    }

    // 仍然有效
    this.cachedToken = { token, expiresAt };
    return token;
  }

  // 请求新令牌
  return await this.requestNewToken();
}

async requestNewToken() {
  const deviceId = await getDeviceId();

  return new Promise((resolve, reject) => {
    fetch.fetch({
      url: `${config.api.musicApiBaseUrl}/api/auth/token`,
      method: 'POST',
      data: JSON.stringify({ device\_id: deviceId }),
      success: async (res) => {
        const data = JSON.parse(res.data);
        const token = data.token;
        const expiresAt = Date.now() + data.expires\_in \* 1000;
        const permission = data.permission;

        // 存储令牌
        await this.storeToken(token, expiresAt, permission);

        // 缓存
        this.cachedToken = { token, expiresAt };
        this.cachedPermission = permission;

        resolve(token);
      },
      fail: (error) => {
        reject(error);
      }
    });
  });
}
```

---

### 4.8 主题管理模块 (theme\_manager.js)

**文件位置**: `src/common/theme\_manager.js`

**功能描述**:
主题下载、安装、应用和删除。

**存储位置**: `internal://files/themes/{themeId}/`

**主题结构**:

```javascript
{
  name: string,
  colors: {
    background: string,
    text\_primary: string,
    text\_secondary: string,
    theme: string,
    slider\_selected: string,
    slider\_block: string,
    slider\_unselected: string
  },
  lyric: {
    active: string,
    normal: string,
    active\_bg: string
  },
  icons: {
    dark\_mode: boolean,
    path: string
  }
}
```

**关键API**:

```javascript
// 初始化主题目录
init() → Promise<void>

// 下载并安装主题
downloadTheme(themeId) → Promise<{success, message}>

// 应用本地主题
applyTheme(themeId) → Promise<boolean>

// 列出已安装主题
getLocalThemes() → Promise<Array<ThemeInfo>>

// 删除主题
deleteTheme(themeId) → Promise<boolean>
```

**下载主题**:

```javascript
async downloadTheme(themeId) {
  // 检查是否已存在
  const localThemes = await this.getLocalThemes();
  if (localThemes.find(t => t.id === themeId)) {
    return { success: false, message: '主题已存在' };
  }

  // 获取主题信息
  const themeInfo = await api.getThemeInfo(themeId);
  if (!themeInfo) {
    return { success: false, message: '获取主题信息失败' };
  }

  // 创建主题目录
  const themeDir = `internal://files/themes/${themeId}`;
  await file.mkdir({ uri: themeDir });

  // 下载主题配置
  const configPath = `${themeDir}/theme.json`;
  await this.downloadFile(themeInfo.configUrl, configPath);

  // 下载主题资源（如果有）
  if (themeInfo.resources) {
    for (const resource of themeInfo.resources) {
      const resourcePath = `${themeDir}/${resource.name}`;
      await this.downloadFile(resource.url, resourcePath);
    }
  }

  return { success: true, message: '下载成功' };
}
```

**删除主题**:

```javascript
async deleteTheme(themeId) {
  const themeDir = `internal://files/themes/${themeId}`;

  // 递归删除目录
  await this.deleteDirectory(themeDir);

  return true;
}

async deleteDirectory(dirUri) {
  return new Promise((resolve, reject) => {
    file.list({
      uri: dirUri,
      success: async (res) => {
        const files = res.fileList || \[];

        // 删除所有文件
        for (const file of files) {
          const filePath = `${dirUri}/${file}`;
          if (file.endsWith('/')) {
            // 子目录，递归删除
            await this.deleteDirectory(filePath);
          } else {
            // 文件，直接删除
            await file.delete({ uri: filePath });
          }
        }

        // 删除目录本身
        await file.delete({ uri: dirUri });
        resolve();
      },
      fail: (error) => {
        reject(error);
      }
    });
  });
}
```

---

### 4.9 KV存储模块 (kv\_storage.js)

**文件位置**: `src/common/kv\_storage.js`

**功能描述**:
键值存储包装器，替代@system.storage。

**存储位置**: `internal://files/kv\_storage.json`

**核心特性**:

1. **懒加载**

   * 首次访问时才加载文件
   * 减少启动时间

2. **保存链**

   * 顺序保存
   * 防止并发写入

3. **内存缓存**

   * 快速读取
   * 减少I/O

**实现逻辑**:

```javascript
let data = {};
let loadPromise = null;
let saveChain = Promise.resolve();

function load() {
  if (loadPromise) return loadPromise;

  loadPromise = new Promise((resolve, reject) => {
    file.readText({
      uri: 'internal://files/kv\_storage.json',
      success: (res) => {
        try {
          data = JSON.parse(res.text);
          resolve();
        } catch (e) {
          data = {};
          resolve();
        }
      },
      fail: () => {
        data = {};
        resolve();
      }
    });
  });

  return loadPromise;
}

function save() {
  saveChain = saveChain.then(() => {
    return new Promise((resolve, reject) => {
      file.writeText({
        uri: 'internal://files/kv\_storage.json',
        text: JSON.stringify(data),
        success: () => resolve(),
        fail: (error) => reject(error)
      });
    });
  }).catch(() => {
    // 忽略保存错误
  });

  return saveChain;
}

export default {
  get({ key, default: defaultValue, success, fail }) {
    load().then(() => {
      const value = data\[key] !== undefined ? data\[key] : defaultValue;
      success?.(value);
    }).catch(fail);
  },

  set({ key, value, success, fail }) {
    load().then(() => {
      data\[key] = value;
      save().then(success).catch(fail);
    }).catch(fail);
  },

  delete({ key, success, fail }) {
    load().then(() => {
      delete data\[key];
      save().then(success).catch(fail);
    }).catch(fail);
  },

  clear({ success, fail }) {
    load().then(() => {
      data = {};
      save().then(success).catch(fail);
    }).catch(fail);
  }
};
```

---

### 4.10 配置模块 (config.js)

**文件位置**: `src/common/config.js`

**功能描述**:
应用配置管理中心。

**配置结构**:

```javascript
export default {
  // 环境配置
  ENV: 'production',

  // API配置
  api: {
    musicApiBaseUrl: 'http://music.mindrift.cn',
    cache: {
      urlExpiry: 5 \* 60 \* 1000,      // 5分钟
      lyricExpiry: 5 \* 60 \* 1000     // 5分钟
    },
    audioQuality: '128k',
    retry: {
      maxAttempts: 3,
      delay: 1000
    }
  },

  // 应用配置
  app: {
    version: '0.3 Alpha',
    versionCode: 2,
    paths: {
      db: 'internal://files/app\_db.json',
      musicDir: 'internal://files/music/',
      lyricDir: 'internal://files/music/'
    },
    screen: {
      designWidth: 480,
      circleScreen: { width: 466, height: 466 },
      rectScreen: { width: 432, height: 514 }
    }
  },

  // 许可证配置
  license: {
    secret: '2ih3ugcuy8hjckashy238'
  }
};
```

---

### 4.11 工具函数模块 (utils.js)

**文件位置**: `src/common/utils.js`

**功能描述**:
通用工具函数集合。

**核心函数**:

```javascript
// 时间格式化
export function formatTime(seconds) {
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m < 10 ? '0' + m : m}:${s < 10 ? '0' + s : s}`;
}

// 震动反馈
export function vibrateShort() {
  try {
    vibrator.vibrate({
      mode: 'short',
      success: () => {},
      fail: () => {}
    });
  } catch (e) {
    // 忽略震动错误
  }
}

// 防抖函数
export function debounce(fn, delay) {
  let timer = null;
  return function() {
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => {
      fn.apply(this, arguments);
    }, delay);
  };
}
```

---

### 4.12 屏幕适配模块 (screen-util.js)

**文件位置**: `src/common/screen-util.js`

**功能描述**:
响应式设计工具，支持不同屏幕类型。

**屏幕类型**:

```javascript
const SCREEN\_TYPES = {
  CIRCLE: 'circle',  // 466x466 (宽高比 ~1:1)
  RECT: 'rect',      // 432x514 (高度 > 宽度 \* 1.15)
  PILL: 'pill'       // 432x480 (其他情况)
};
```

**核心API**:

```javascript
// 获取屏幕信息（缓存）
getScreenInfo() → Promise<{width, height, type, config, scale}>

// 检测屏幕类型
detectScreenType(width, height) → string

// 缩放值
scale(value) → number
scaleAsync(value) → Promise<number>

// 根据屏幕类型获取值
getValueByScreenType({circle, rect, pill}, defaultValue) → Promise<number>

// 响应式计算
responsiveWidth(percentage) → Promise<number>
responsiveHeight(percentage) → Promise<number>

// 屏幕类型检查
isCircleScreen() → Promise<boolean>
isRectScreen() → Promise<boolean>
isPillScreen() → Promise<boolean>

// 圆形屏幕安全区域
getSafeArea() → Promise<{left, right, top, bottom, radius}>

// 清除缓存
refreshScreenInfo() → void

// 获取屏幕配置
getScreenConfig() → Object
```

**屏幕类型检测**:

```javascript
function detectScreenType(width, height) {
  const aspectRatio = width / height;

  // 圆形屏幕
  if (aspectRatio > 0.95 \&\& aspectRatio < 1.05) {
    return SCREEN\_TYPES.CIRCLE;
  }

  // 方形屏幕
  if (height > width \* 1.15) {
    return SCREEN\_TYPES.RECT;
  }

  // 胶囊形屏幕
  return SCREEN\_TYPES.PILL;
}
```

**缩放计算**:

```javascript
async scale(value) {
  const screenInfo = await getScreenInfo();
  const scaledValue = Math.round(value \* screenInfo.scale);
  return scaledValue;
}

async responsiveWidth(percentage) {
  const screenInfo = await getScreenInfo();
  return Math.round(screenInfo.width \* percentage / 100);
}
```

---

### 4.13 认证头模块 (auth\_headers.js)

**文件位置**: `src/common/auth\_headers.js`

**功能描述**:
生成认证请求头。

**实现逻辑**:

```javascript
export async function getAuthHeadersForUrl(url) {
  // 只对拥有的URL添加认证头
  if (!isOwnedUrl(url)) {
    return {};
  }

  const licenseHeader = await getLicenseHeadersForUrl(url);
  const isCustomEnabled = await isCustomSourceEnabled();

  // 自定义源仅使用许可证头
  if (isCustomEnabled) {
    return licenseHeader;
  }

  // 默认仅使用许可证头
  return { ...licenseHeader };
}

export async function getLicenseHeadersForUrl(url) {
  if (!isOwnedUrl(url)) {
    return {};
  }

  const licenseHeader = await getLicenseHeader();
  return licenseHeader;
}
```

---

### 4.14 自定义源模块 (custom\_source.js)

**文件位置**: `src/common/custom\_source.js`

**功能描述**:
自定义API数据源配置。

**核心API**:

```javascript
// 检查是否启用自定义源
isCustomSourceEnabled() → Promise<boolean>

// 获取自定义源URL
getCustomSourceUrl() → Promise<string>

// 获取当前API基础URL
getApiBaseUrl() → Promise<string>

// 验证自定义源URL
validateCustomSourceUrl(url) → string
```

**实现逻辑**:

```javascript
async isCustomSourceEnabled() {
  return new Promise((resolve) => {
    storage.get({
      key: 'custom\_source\_enabled',
      success: (value) => {
        resolve(value === 'true');
      },
      fail: () => {
        resolve(false);
      }
    });
  });
}

async getCustomSourceUrl() {
  return new Promise((resolve) => {
    storage.get({
      key: 'custom\_source\_url',
      success: (value) => {
        resolve(value || '');
      },
      fail: () => {
        resolve('');
      }
    });
  });
}

async getApiBaseUrl() {
  const enabled = await isCustomSourceEnabled();
  if (enabled) {
    const url = await getCustomSourceUrl();
    if (url) {
      return url;
    }
  }
  return config.api.musicApiBaseUrl;
}

function validateCustomSourceUrl(url) {
  if (!url || typeof url !== 'string') {
    return null;
  }

  const trimmed = url.trim();

  // 必须以http://或https://开头
  if (!trimmed.startsWith('http://') \&\& !trimmed.startsWith('https://')) {
    return null;
  }

  // 移除末尾斜杠
  let normalized = trimmed;
  while (normalized.endsWith('/')) {
    normalized = normalized.slice(0, -1);
  }

  return normalized;
}
```

---

### 4.15 主题配置模块 (theme\_config.js)

**文件位置**: `src/common/theme\_config.js`

**功能描述**:
运行时主题配置管理。

**默认主题**:

```javascript
const defaultConfig = {
  colors: {
    theme: '#00E5FF',
    text\_primary: 'rgba(0, 0, 0, 0.67)',
    text\_secondary: 'rgba(0, 0, 0, 0.5)',
    slider\_selected: '#00E5FF',
    slider\_block: '#00E5FF',
    slider\_unselected: 'rgba(255, 255, 255, 0.3)',
    background: '#f0f0f0'
  },
  lyric: {
    active: '#00E5FF',
    normal: '#000000',
    active\_bg: 'rgba(0, 229, 255, 0.3)'
  },
  icons: {
    dark\_mode: false,
    path: '/assets/icons'
  }
};
```

**核心API**:

```javascript
// 获取当前主题配置
get() → Object

// 更新主题配置（深度合并）
set(newConfig) → void
```

**深度合并实现**:

```javascript
function deepMerge(target, source) {
  const result = { ...target };

  for (const key in source) {
    if (source.hasOwnProperty(key)) {
      const sourceValue = source\[key];
      const targetValue = target\[key];

      if (typeof sourceValue === 'object' \&\& sourceValue !== null \&\&
          typeof targetValue === 'object' \&\& targetValue !== null \&\&
          !Array.isArray(sourceValue)) {
        // 对象递归合并
        result\[key] = deepMerge(targetValue, sourceValue);
      } else {
        // 基本类型或数组，直接覆盖
        result\[key] = sourceValue;
      }
    }
  }

  return result;
}

export default {
  get() {
    return currentConfig;
  },

  set(newConfig) {
    currentConfig = deepMerge(currentConfig, newConfig);
  }
};
```

---

### 4.16 URL所有权模块 (host.js)

**文件位置**: `src/common/host.js`

**功能描述**:
检查URL是否属于拥有的域名。

**拥有的域名**:

```
mindrift.cn
music.mindrift.cn
```

**实现逻辑**:

```javascript
function getHost(url) {
  if (!url || typeof url !== 'string') {
    return '';
  }

  try {
    // 移除协议
    let stripped = url;
    if (stripped.startsWith('http://')) {
      stripped = stripped.slice(7);
    } else if (stripped.startsWith('https://')) {
      stripped = stripped.slice(8);
    }

    // 移除用户名密码
    const atIndex = stripped.indexOf('@');
    if (atIndex > 0) {
      stripped = stripped.slice(atIndex + 1);
    }

    // 移除端口和路径
    const slashIndex = stripped.indexOf('/');
    if (slashIndex > 0) {
      stripped = stripped.slice(0, slashIndex);
    }

    const colonIndex = stripped.indexOf(':');
    if (colonIndex > 0) {
      stripped = stripped.slice(0, colonIndex);
    }

    return stripped.toLowerCase();
  } catch (e) {
    return '';
  }
}

function isOwnedUrl(url) {
  const host = getHost(url);
  if (!host) return false;

  const ownedHosts = \['mindrift.cn', 'music.mindrift.cn'];

  return ownedHosts.includes(host);
}

export { isOwnedUrl };
```

---

### 4.17 Base64解码模块 (base64.js)

**文件位置**: `src/common/base64.js`

**功能描述**:
纯JavaScript Base64解码器，用于解码酷狗歌词。

**实现逻辑**:

```javascript
const CHARS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';

function base64Decode(input) {
  // 移除非法字符
  const sanitized = input.replace(/\[^A-Za-z0-9+/=]/g, '');

  let output = '';
  let buffer = 0;
  let bits = 0;

  for (let i = 0; i < sanitized.length; i++) {
    const char = sanitized\[i];
    const index = CHARS.indexOf(char);

    if (index === -1) continue;

    buffer = (buffer << 6) | index;
    bits += 6;

    if (bits >= 8) {
      const byte = (buffer >> (bits - 8)) \& 0xFF;
      output += String.fromCharCode(byte);
      bits -= 8;
    }
  }

  // UTF-8解码
  try {
    return decodeURIComponent(escape(output));
  } catch (e) {
    return output;
  }
}

export default base64Decode;
```

---

## 5\. 应用核心功能

### 5.1 播放控制系统

**位置**: `src/app.ux`

**核心状态**:

```javascript
data: {
  playlist: \[],              // 播放列表
  currentIndex: -1,          // 当前索引
  currentSong: null,         // 当前歌曲
  isPlaying: false,          // 播放状态
  playMode: 'sequential',    // 播放模式
  shuffleOrder: \[],          // 随机顺序
  shuffleCursor: 0,          // 随机游标
  sleepTimer: null,          // 定时器
  sleepTimeLeft: 0,          // 剩余时间
  errorRetryCount: 0,        // 重试次数
  maxRetryCount: 3           // 最大重试次数
}
```

**播放音乐**:

```javascript
async playMusic(song, list) {
  // 许可证验证
  const allowed = await this.ensureLicenseValid();
  if (!allowed) return;

  // 更新播放列表
  if (list) this.data.playlist = list;

  // 查找或添加歌曲
  const idx = this.data.playlist.findIndex(
    (item) => item.id === song.id \&\& item.source === song.source
  );
  this.data.currentIndex = idx > -1 ? idx : this.data.playlist.push(song) - 1;
  this.data.currentSong = song;

  // 准备音频源
  let src = song.path || song.url;
  if (!src) return;

  // 停止当前播放
  audio.stop();
  audio.src = src;
  audio.play();

  // 预加载下一首
  this.preloadNext();
}
```

**预加载下一首**:

```javascript
async preloadNext() {
  if (this.data.playlist.length === 0) return;

  const nextIndex = (this.data.currentIndex + 1) % this.data.playlist.length;
  const nextSong = this.data.playlist\[nextIndex];

  // 本地文件无需预加载
  if (nextSong.path) return;

  // 已有URL无需预加载
  if (nextSong.url) return;

  // 预加载URL
  try {
    const api = require('./common/api').default;
    const url = await api.getMusicUrl(nextSong.source, nextSong.id);
    if (url) {
      nextSong.url = url;
    }
  } catch (e) {
    console.error('预加载失败:', e);
  }
}
```

**切换播放模式**:

```javascript
togglePlayMode() {
  const modes = \['sequential', 'shuffle', 'loop'];
  const currentIndex = modes.indexOf(this.data.playMode);
  this.data.playMode = modes\[(currentIndex + 1) % modes.length];

  // 随机模式初始化顺序
  if (this.data.playMode === 'shuffle') {
    this.resetShuffleOrder(false);
  }

  // 提示用户
  const modeNames = {
    'sequential': '顺序播放',
    'shuffle': '随机播放',
    'loop': '单曲循环'
  };

  prompt.showToast({
    message: modeNames\[this.data.playMode]
  });
}
```

**随机播放算法 (Fisher-Yates洗牌)**:

```javascript
resetShuffleOrder(startFromHead) {
  const size = this.data.playlist.length;
  const order = \[];

  // 初始化顺序数组
  for (let i = 0; i < size; i++) {
    order.push(i);
  }

  // Fisher-Yates洗牌
  for (let i = order.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() \* (i + 1));
    const tmp = order\[i];
    order\[i] = order\[j];
    order\[j] = tmp;
  }

  // 确保当前歌曲不在第一位
  if (order\[0] === this.data.currentIndex \&\& order.length > 1) {
    const tmp = order\[0];
    order\[0] = order\[1];
    order\[1] = tmp;
  }

  this.data.shuffleOrder = order;

  // 设置游标
  if (startFromHead) {
    this.data.shuffleCursor = 0;
  } else {
    const currentPos = order.indexOf(this.data.currentIndex);
    this.data.shuffleCursor = currentPos >= 0 ? currentPos + 1 : 0;
  }
}

getNextShuffleIndex() {
  const size = this.data.playlist.length;
  if (size <= 1) return this.data.currentIndex;

  // 初始化顺序
  if (!this.data.shuffleOrder || this.data.shuffleOrder.length !== size) {
    this.resetShuffleOrder(false);
  }

  // 循环播放
  if (this.data.shuffleCursor >= this.data.shuffleOrder.length) {
    this.resetShuffleOrder(true);
  }

  const nextIndex = this.data.shuffleOrder\[this.data.shuffleCursor];
  this.data.shuffleCursor += 1;

  return nextIndex;
}
```

**错误处理**:

```javascript
handleAudioError(error) {
  const errorType = this.classifyError(error);
  let errorMessage = '';
  let shouldRetry = false;

  // 分类错误
  switch (errorType) {
    case 'network':
      errorMessage = '网络错误，播放失败';
      shouldRetry = true;
      break;
    case 'file\_not\_found':
      errorMessage = '音频文件不存在';
      shouldRetry = false;
      break;
    case 'decode':
      errorMessage = '音频解码失败';
      shouldRetry = true;
      break;
    case 'permission':
      errorMessage = '没有播放权限';
      shouldRetry = false;
      break;
    default:
      errorMessage = '播放失败，请重试';
      shouldRetry = true;
  }

  // 显示错误提示
  prompt.showToast({ message: errorMessage });

  // 重试或跳过
  if (shouldRetry \&\& this.data.errorRetryCount < this.data.maxRetryCount) {
    this.data.errorRetryCount++;
    console.debug(`尝试重试播放 (${this.data.errorRetryCount}/${this.data.maxRetryCount})`);

    setTimeout(() => {
      if (this.data.currentSong) {
        this.playMusic(this.data.currentSong);
      }
    }, this.data.appConfig.api.retry.delay);
  } else if (this.data.errorRetryCount >= this.data.maxRetryCount) {
    console.debug('超过最大重试次数，跳过当前歌曲');
    this.data.errorRetryCount = 0;
    setTimeout(() => {
      this.next();
    }, 1000);
  }
}
```

**定时停止**:

```javascript
setSleepTimer(minutes) {
  // 清除现有定时器
  if (this.data.sleepTimer) {
    clearTimeout(this.data.sleepTimer);
    this.data.sleepTimer = null;
  }
  if (this.data.sleepTimerInterval) {
    clearInterval(this.data.sleepTimerInterval);
    this.data.sleepTimerInterval = null;
  }

  if (minutes > 0) {
    this.data.sleepTimeLeft = minutes \* 60 \* 1000;

    // 停止定时器
    this.data.sleepTimer = setTimeout(() => {
      audio.stop();
      this.data.isPlaying = false;
      this.data.sleepTimeLeft = 0;
      if (this.data.sleepTimerInterval) {
        clearInterval(this.data.sleepTimerInterval);
      }
    }, minutes \* 60 \* 1000);

    // 倒计时更新
    this.data.sleepTimerInterval = setInterval(() => {
      this.data.sleepTimeLeft -= 1000;
      if (this.data.sleepTimeLeft <= 0) {
        clearInterval(this.data.sleepTimerInterval);
      }
    }, 1000);
  } else {
    this.data.sleepTimeLeft = 0;
  }
}
```

---

### 5.2 主题系统

**核心特性**:

1. **主题快照**

   * 快速加载避免闪烁
   * 持久化存储

2. **主题就绪回调**

   * 确保主题加载完成
   * 防止竞态条件

3. **深度合并**

   * 保留未修改的配置
   * 支持部分更新

**主题初始化**:

```javascript
initTheme() {
  storage.get({
    key: 'current\_theme\_id',
    success: (id) => {
      const themeId = id || 'light';
      console.debug(`\[App] 初始化主题: ${themeId}`);
      this.loadThemeFile(themeId);
    },
    fail: () => {
      this.loadThemeFile('light');
    }
  });
}

initThemeSnapshot() {
  storage.get({
    key: "theme\_snapshot",
    success: (snapshot) => {
      if (snapshot) {
        this.data.themeSnapshot = snapshot;
        const patch = { colors: snapshot.colors };
        if (snapshot.lyric) patch.lyric = snapshot.lyric;
        if (snapshot.icons) patch.icons = snapshot.icons;
        themeConfig.set(patch);
      }
    }
  });
}
```

**加载主题文件**:

```javascript
loadThemeFile(themeId) {
  return new Promise((resolve) => {
    // 内置主题直接加载
    if (themeId === 'light') {
      themeConfig.set(themeLight);
      this.persistThemeSnapshot();
      this.setThemeReady();
      resolve(true);
      return;
    }

    if (themeId === 'dark') {
      themeConfig.set(themeDark);
      this.persistThemeSnapshot();
      this.setThemeReady();
      resolve(true);
      return;
    }

    // 自定义主题读取文件
    const uri = `internal://files/themes/${themeId}/theme.json`;
    file.readText({
      uri: uri,
      success: (data) => {
        try {
          const config = JSON.parse(data.text);
          themeConfig.set(config);
          this.persistThemeSnapshot();
          this.setThemeReady();
          resolve(true);
        } catch (e) {
          console.error(`\[App] 主题 ${themeId} 解析失败:`, e);
          themeConfig.set(themeLight);
          this.persistThemeSnapshot();
          this.setThemeReady();
          resolve(false);
        }
      },
      fail: () => {
        console.error(`\[App] 主题文件读取失败: ${uri}`);
        themeConfig.set(themeLight);
        this.persistThemeSnapshot();
        this.setThemeReady();
        resolve(false);
      }
    });
  });
}
```

**主题快照**:

```javascript
buildThemeSnapshot() {
  const theme = themeConfig.get();
  return {
    colors: {
      background: theme.colors.background,
      text\_primary: theme.colors.text\_primary,
      text\_secondary: theme.colors.text\_secondary,
      theme: theme.colors.theme
    },
    lyric: theme.lyric || {},
    icons: theme.icons || {}
  };
}

persistThemeSnapshot() {
  const snapshot = this.buildThemeSnapshot();
  this.data.themeSnapshot = snapshot;
  storage.set({
    key: "theme\_snapshot",
    value: snapshot
  });
}
```

**主题就绪回调**:

```javascript
onThemeReady(callback) {
  if (this.data.themeReady) {
    if (callback) callback();
    return;
  }

  if (!this.\_themeReadyCallbacks) {
    this.\_themeReadyCallbacks = \[];
  }

  if (callback) this.\_themeReadyCallbacks.push(callback);
}

setThemeReady() {
  if (this.data.themeReady) return;

  this.data.themeReady = true;
  const callbacks = this.\_themeReadyCallbacks || \[];
  this.\_themeReadyCallbacks = \[];

  callbacks.forEach((cb) => {
    try {
      cb();
    } catch (e) {
      console.error('\[App] themeReady callback error:', e);
    }
  });
}
```

---

### 5.3 许可证系统

**验证流程**:

```javascript
async initLicense() {
  try {
    // 获取存储的许可证
    const license = await db.getLicense();
    if (!license) {
      this.data.licenseStatus = { valid: false, reason: "LICENSE\_INVALID" };
      this.data.licenseRaw = null;
      this.data.licenseReady = true;
      return;
    }

    this.data.licenseRaw = license;

    // 验证许可证
    const result = await validateLicense(license);
    this.data.licenseStatus = result;
    this.data.licenseReady = true;

  } catch (error) {
    console.error("\[App] initLicense failed:", error);
    this.data.licenseStatus = { valid: false, reason: "LICENSE\_INVALID" };
    this.data.licenseRaw = null;
    this.data.licenseReady = true;
  }
}

async ensureLicenseValid() {
  // 等待许可证初始化
  if (!this.data.licenseReady) {
    await this.initLicense();
  }

  // 重新验证（如果已存储）
  if (this.data.licenseRaw) {
    this.data.licenseStatus = await validateLicense(this.data.licenseRaw);
  }

  // 检查有效性
  const status = this.data.licenseStatus;
  if (status \&\& status.valid) return true;

  this.showLicenseError(status);
  return false;
}

showLicenseError(status) {
  let message = "未订阅或授权无效，请订阅后使用";
  const reason = status?.reason;

  if (reason === "DEVICE\_MISMATCH") {
    message = "设备不匹配，无法播放";
  } else if (reason === "SIGNATURE\_INVALID") {
    message = "License 校验失败，请重新订阅";
  } else if (reason === "LICENSE\_EXPIRED") {
    message = "订阅已过期，请续费";
  }

  prompt.showToast({ message });
}
```

---

## 6\. 技术亮点

### 6.1 性能优化

#### 6.1.1 LRU缓存

* **实现**: 自定义LRU缓存类
* **优势**: 限制缓存大小，防止内存泄漏
* **应用**: URL缓存、歌词缓存、搜索结果缓存

#### 6.1.2 操作队列

* **实现**: 数据库操作队列
* **优势**: 防止并发写入冲突
* **应用**: 所有数据库写操作

#### 6.1.3 歌词二分查找

* **实现**: O(log n)时间复杂度的查找算法
* **优势**: 快速定位当前歌词
* **应用**: 歌词同步显示

#### 6.1.4 预加载机制

* **实现**: 提前加载下一首歌曲URL
* **优势**: 提升播放连续性
* **应用**: 播放控制系统

#### 6.1.5 主题快照

* **实现**: 主题配置持久化
* **优势**: 快速加载，避免闪烁
* **应用**: 主题系统

---

### 6.2 安全性

#### 6.2.1 许可证验证

* **技术**: HMAC-SHA256签名
* **保护**: 防止许可证伪造
* **绑定**: 设备ID绑定

#### 6.2.2 设备绑定

* **实现**: 设备ID验证
* **保护**: 防止许可证滥用
* **检查**: 每次播放前验证

#### 6.2.3 过期检查

* **实现**: Unix时间戳比较
* **保护**: 防止永久使用
* **检查**: 每次播放前验证

#### 6.2.4 URL验证

* **实现**: 自定义源URL验证
* **保护**: 防止恶意URL
* **检查**: 格式和协议验证

---

### 6.3 可扩展性

#### 6.3.1 主题系统

* **设计**: 动态主题加载
* **扩展**: 支持自定义主题
* **配置**: JSON格式主题配置

#### 6.3.2 多平台支持

* **设计**: 统一API接口
* **扩展**: 易于添加新平台
* **实现**: 平台特定处理

#### 6.3.3 自定义数据源

* **设计**: 用户可配置API
* **扩展**: 支持私有部署
* **切换**: 运行时动态切换

#### 6.3.4 模块化设计

* **设计**: 清晰的职责分离
* **扩展**: 独立开发和测试
* **复用**: 高度可复用的模块

---

### 6.4 用户体验

#### 6.4.1 双击确认

* **设计**: 防止误操作
* **应用**: 删除、清空等危险操作
* **提示**: Toast消息提示

#### 6.4.2 震动反馈

* **设计**: 触觉反馈
* **应用**: 按钮点击、页面切换
* **库**: @system.vibrator

#### 6.4.3 进度显示

* **设计**: 实时反馈
* **应用**: 下载、搜索等耗时操作
* **展示**: 百分比 + 文本描述

#### 6.4.4 主题切换

* **设计**: 满足个性化需求
* **应用**: 整个应用UI
* **效果**: 无缝切换，无需重启

#### 6.4.5 歌词校准

* **设计**: 解决歌词不同步
* **应用**: 歌词显示
* **精度**: 毫秒级调整

---

## 7\. 项目特色功能

### 7.1 穿戴设备优化的输入法

**文件位置**: `src/components/InputMethod/InputMethod.ux`

**支持的屏幕类型**:

* 圆形屏幕 (62mm)
* 方形屏幕 (67mm)
* 胶囊形屏幕 (66mm)

**键盘类型**:

* QWERTY全键盘
* T9九键

**输入模式**:

* 中文输入（拼音候选）
* 英文输入（大小写切换）
* 数字符号输入

**核心特性**:

1. **拼音联想**: 智能候选词推荐
2. **候选词显示**: 多行候选词展示
3. **退格删除**: 支持连续删除
4. **空格输入**: 快速空格输入
5. **震动反馈**: 可选的触觉反馈

---

### 7.2 实时歌词同步

**技术实现**:

* **解析**: LRC格式解析器
* **查找**: 二分查找算法
* **同步**: 实时时间戳匹配
* **滚动**: 平滑滚动到当前行

**功能特性**:

1. **多种时间格式**: 支持MM:SS.ss和HH:MM:SS.ss
2. **偏移校准**: 支持全局时间偏移
3. **多时间标签**: 支持同一行多个时间标签
4. **元数据过滤**: 自动过滤歌曲信息行

---

### 7.3 多主题支持

**内置主题**:

* 极光 (light): 亮色主题
* 暗夜 (dark): 暗色主题

**自定义主题**:

* 服务器下载
* 本地存储
* 动态应用

**主题配置**:

```json
{
  "name": "主题名称",
  "colors": {
    "background": "#ffffff",
    "text\_primary": "#000000",
    "text\_secondary": "rgba(0,0,0,0.5)",
    "theme": "#00E5FF"
  },
  "lyric": {
    "active": "#00E5FF",
    "normal": "#000000"
  },
  "icons": {
    "dark\_mode": false,
    "path": "/assets/icons"
  }
}
```

---

### 7.4 订阅制商业模式

**许可证验证**:

* HMAC-SHA256签名
* 设备ID绑定
* 过期时间检查

**订阅流程**:

1. 用户获取设备ID
2. 扫描二维码或访问订阅页面
3. 完成支付
4. 系统生成许可证
5. 用户激活许可证

**许可证结构**:

```json
{
  "payload": {
    "device\_id": "设备ID",
    "plan\_id": "订阅计划ID",
    "expire\_at": 1735689600,
    "issued\_at": 1704153600
  },
  "signature": "HMAC-SHA256签名"
}
```

---

### 7.5 自定义数据源

**功能**:

* 用户可配置自己的API服务器
* 支持http和https协议
* 运行时动态切换

**配置方式**:

1. 在设置页面输入API地址
2. 启用自定义源
3. 应用自动使用新API

**验证机制**:

* URL格式验证
* 协议验证
* 规范化处理

---

## 8\. 潜在问题和改进建议

### 8.1 内存管理

#### 问题

1. **定时器清理**: 部分页面可能存在定时器未清理的问题
2. **缓存大小**: LRU缓存大小可能需要根据设备内存调整
3. **大列表渲染**: 本地音乐列表较长时可能影响性能

#### 建议

1. **定时器清理**: 在页面onHide时清理所有定时器
2. **缓存监控**: 添加内存使用监控，动态调整缓存大小
3. **虚拟列表**: 实现虚拟滚动，只渲染可见项

---

### 8.2 错误处理

#### 问题

1. **网络错误**: 部分网络请求缺少详细的错误处理
2. **文件操作**: 文件读写失败时的回退机制不完善
3. **许可证缓存**: 缓存失效时需要重新验证

#### 建议

1. **统一错误处理**: 实现全局错误处理中间件
2. **重试机制**: 添加指数退避重试策略
3. **缓存失效**: 添加缓存失效检测和自动刷新

---

### 8.3 代码质量

#### 问题

1. **测试覆盖**: 缺少单元测试和集成测试
2. **代码注释**: 部分复杂逻辑缺少详细注释
3. **命名规范**: 部分变量命名不够清晰

#### 建议

1. **添加测试**: 使用Jest或Mocha添加测试
2. **完善注释**: 添加JSDoc注释
3. **统一命名**: 遵循一致的命名规范

---

### 8.4 功能完善

#### 问题

1. **国际化**: 当前国际化功能不完整
2. **播放历史**: 缺少播放历史记录功能
3. **歌单管理**: 缺少自定义歌单功能
4. **均衡器**: 缺少音效调节功能

#### 建议

1. **完善国际化**: 添加完整的语言支持
2. **播放历史**: 记录和显示播放历史
3. **歌单管理**: 支持创建和管理自定义歌单
4. **均衡器**: 添加音效调节功能

---

### 8.5 用户体验

#### 问题

1. **加载提示**: 部分操作缺少加载提示
2. **空状态**: 某些页面缺少空状态展示
3. **错误提示**: 错误提示信息不够友好

#### 建议

1. **加载提示**: 添加统一的加载组件
2. **空状态**: 设计友好的空状态页面
3. **错误提示**: 优化错误提示文案

---

## 9\. 总结

氦音乐是一个功能完善的智能穿戴设备音乐播放器应用，展现了良好的工程实践和用户体验设计。

### 项目优势

1. **完整的音乐播放功能**

   * 在线播放、本地播放、下载功能
   * 歌词同步显示
   * 多种播放模式

2. **精心设计的UI/UX**

   * 主题系统支持
   * 手势交互
   * 触觉反馈

3. **健壮的架构设计**

   * 模块化设计
   * 缓存机制
   * 队列模式

4. **完善的许可证系统**

   * HMAC-SHA256验证
   * 设备绑定
   * 过期检查

5. **灵活的扩展机制**

   * 主题系统
   * 自定义数据源
   * 多平台支持

### 技术栈

* **框架**: 小米 Vela OS 快应用
* **语言**: JavaScript (UX)
* **存储**: JSON文件 + KV存储
* **网络**: Fetch API
* **音频**: @system.audio

### 开发实践

* **设计模式**: LRU缓存、队列模式、策略模式
* **性能优化**: 缓存、预加载、二分查找
* **安全性**: 许可证验证、设备绑定
* **可扩展性**: 模块化设计、主题系统

### 适用场景

该项目特别适合：

* 智能手表/手环音乐播放器开发
* 快应用学习和参考
* 订阅制应用架构设计
* 穿戴设备UI/UX设计

该项目是一个值得学习和参考的快应用开发案例，展现了良好的工程实践和用户体验设计。


