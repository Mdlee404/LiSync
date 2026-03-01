# LiSync 通信协议规范 (Vela 快应用 <-> Android)

本文档为 Android 客户端开发人员提供详细的通信协议规范，用于开发与 **HeMusic-Lite** (Vela 快应用) 配套的 **LiSync** 安卓伴侣应用。

## 1. 通信基础

### 1.1 传输层
*   **API**: Xiaomi Wear SDK (MessageApi) / `@system.interconnect` (Vela)
*   **数据格式**: JSON 字符串 (UTF-8)
*   **通信模型**: 异步请求/响应 (Request-Response) 及 服务端推送 (Push)

### 1.2 关键机制：Request ID (`_requestId`)
**非常重要**：快应用端使用 `_requestId` 来追踪异步请求。
*   **Android 端接收到请求时**：必须解析并保存请求中的 `_requestId` 字段。
*   **Android 端发送响应时**：必须在响应 JSON 的根对象中原样带回该 `_requestId` 字段。
*   **后果**：如果响应中缺少 `_requestId`，快应用将无法匹配回调，导致请求超时。

---

## 2. 接口定义

### 2.0 Watch Ready (Handshake)
手表端连接成功后会主动发送 `WATCH_READY`，手机端需回 ACK 并推送能力集。

#### 请求 (Watch -> Phone)
```json
{
  "action": "WATCH_READY",
  "version": "1.5",
  "_requestId": "req_17273849123_1"
}
```

`version` 为手表端版本号（建议必传，字符串）。Android 端也兼容以下等价写法：

```json
{
  "action": "WATCH_READY",
  "watchVersion": "1.5",
  "_requestId": "req_17273849123_1"
}
```

或：

```json
{
  "action": "WATCH_READY",
  "watch": { "version": "1.5" },
  "_requestId": "req_17273849123_1"
}
```

#### 响应 (Phone -> Watch)
```json
{
  "action": "WATCH_READY_ACK",
  "code": 0,
  "message": "ok",
  "_requestId": "req_17273849123_1"
}
```

随后手机端应主动推送一次 `capabilitiesUpdate`，以刷新手表端缓存。

---

### 2.0.1 版本检查 (Version Check)
手表端可主动请求 `version.check`，由手机端统一返回手机/手表版本与更新说明。

#### 请求 (Watch -> Phone)
```json
{
  "action": "version.check",
  "_requestId": "req_17273849123_9",
  "version": "1.5"
}
```

`version` 可选，也兼容 `watchVersion` 或 `watch.version`。

#### 响应 (Phone -> Watch)
```json
{
  "action": "version.check.result",
  "code": 0,
  "_requestId": "req_17273849123_9",
  "data": {
    "watch": {
      "version": "1.5",
      "update_notes": ["..."]
    },
    "phone": {
      "version": "1.0.0",
      "update_notes": ["..."],
      "download_url": "https://..."
    }
  }
}
```

---

### 2.1 获取平台能力 (Capabilities)
快应用启动时会调用此接口，查询 Android 端支持的音乐平台和音质。

#### 请求 (Watch -> Phone)
```json
{
  "action": "getCapabilities",
  "_requestId": "req_17273849123_1"
}
```

#### 响应 (Phone -> Watch)
```json
{
  "action": "capabilities",
  "code": 0,
  "message": "ok",
  "_requestId": "req_17273849123_1",  // 必须回传
  "data": {
    "platforms": ["tx", "wy", "kg", "kw", "mg", "local"],
    "qualities": {
      "tx": ["128k", "320k", "flac", "flac24bit"],
      "wy": ["128k", "320k"],
      "local": []
    },
    "actions": ["musicUrl"]
  }
}
```
*   `qualities`: 键为平台代码，值为支持的音质列表。空数组表示不限制。

---

### 2.2 搜索歌曲 (Search)

#### 请求 (Watch -> Phone)
```json
{
  "action": "search",
  "platform": "tx",        // 可选，指定搜索源
  "keyword": "周杰伦",
  "page": 1,
  "pageSize": 20,
  "_requestId": "req_17273849123_2"
}
```

#### 响应 (Phone -> Watch)
```json
{
  "action": "search",
  "code": 0,
  "_requestId": "req_17273849123_2", // 必须回传
  "data": {
    "platform": "tx",
    "page": 1,
    "pageSize": 20,
    "total": 100,
    "results": [
      {
        "source": "tx",
        "id": "001X0PDf0W4lBq",
        "title": "七里香",
        "artist": "周杰伦",
        "album": "七里香",
        "duration": 299,      // 秒 (可选)
        "picUrl": "http://..." // 封面图 (可选)
      }
    ]
  }
}
```

---

### 2.3 获取播放链接 (Get Music URL)
这是最核心的接口，用于解析真实的音频流地址。

#### 请求 (Watch -> Phone)
注意：此请求可能不包含 `action` 字段，需根据字段特征或默认为 URL 请求。

```json
{
  "platform": "tx",
  "id": "001X0PDf0W4lBq", // 歌曲唯一ID
  "quality": "128k",      // 请求音质: 128k/320k/flac/flac24bit
  "targetScriptId": "your-script.js", // 可选，指定优先音源脚本
  "nocache": false,       // true 表示强制刷新，忽略缓存
  "_requestId": "req_17273849123_3"
}
```

`targetScriptId` 也兼容以下别名：`scriptId` / `provider` / `target_script_id` / `targetScript`。

#### 响应 (Phone -> Watch) - 成功
```json
{
  "code": 0,
  "message": "ok",
  "_requestId": "req_17273849123_3", // 必须回传
  "data": "https://server.com/stream.mp3?token=xyz", // 方式1: 直接字符串
  "url": "https://server.com/stream.mp3?token=xyz",  // 方式2: 放在 url 字段 (兼容)
  "provider": "lx-source-script-v1.js", // (可选) 提供源信息
  "info": {
     "songId": "001X0PDf0W4lBq",
     "quality": "128k"
  }
}
```
*   快应用解析优先级: `data.url` > `data` (if string) > `url`。建议统一放在 `data` 或 `url` 字段。

#### 响应 (Phone -> Watch) - 失败
```json
{
  "code": -1,
  "message": "Copyright restricted",
  "_requestId": "req_17273849123_3",
  "error": {
    "message": "Copyright restricted",
    "type": "copyright"
  }
}
```

---

### 2.4 获取歌词 (Lyric)

#### 请求 (Watch -> Phone)
```json
{
  "action": "lyric",
  "platform": "tx",
  "id": "001X0PDf0W4lBq",
  "_requestId": "req_17273849123_4"
}
```

#### 响应 (Phone -> Watch)
```json
{
  "action": "lyric",
  "code": 0,
  "_requestId": "req_17273849123_4",
  "data": {
    "lyric": "[00:00.00] 歌词内容...", // 原版歌词
    "tlyric": "[00:00.00] Translation...", // 翻译歌词 (可选)
    "rlyric": null, // 罗马音 (可选)
    "lxlyric": null // LX 格式 (可选)
  }
}
```

---

### 2.5 能力更新推送 (Push Notification)
当 Android 端配置发生变更（如导入了新脚本、开启了新平台），应主动推送此消息。此消息不需要 `_requestId`。

#### 推送 (Phone -> Watch)
```json
{
  "action": "capabilitiesUpdate",
  "code": 0,
  "data": {
    "platforms": ["tx", "wy", "kg", "kw"],
    "qualities": {
      "tx": ["128k", "320k", "flac"]
    },
    "actions": ["musicUrl"]
  }
}
```

---

### 2.6 上传音乐文件（Phone -> Watch）
用于手机端将本地音频文件推送到手表端保存。手表端决定保存路径。

#### 开始（Phone -> Watch）
```json
{
  "action": "upload.start",
  "_requestId": "upload_17273849123_5",
  "fileId": "file_1700000000000",
  "name": "demo.mp3",
  "size": 1048576,
  "mime": "audio/mpeg",
  "chunkSize": 8192,
  "total": 128,
  "md5": "e10adc3949ba59abbe56e057f20f883e"
}
```

#### 分片（Phone -> Watch）
```json
{
  "action": "upload.chunk",
  "_requestId": "upload_17273849123_5",
  "fileId": "file_1700000000000",
  "index": 1,
  "total": 128,
  "data": "BASE64..."
}
```

#### 结束（Phone -> Watch）
```json
{
  "action": "upload.finish",
  "_requestId": "upload_17273849123_5",
  "fileId": "file_1700000000000",
  "total": 128,
  "md5": "e10adc3949ba59abbe56e057f20f883e"
}
```

#### 手表确认（Watch -> Phone）
```json
{
  "action": "upload.result",
  "_requestId": "upload_17273849123_5",
  "fileId": "file_1700000000000",
  "ok": true,
  "path": "/storage/vela/music/demo.mp3"
}
```

**限制**：
- 单文件最大 **5MB**
- 分片大小 **8KB**

---

### 2.7 主题传输（Phone -> Watch）
主题传输（`theme.open` / `theme.init` / `theme.file.*` / `theme.finish` / `theme.cancel`）请参考 `docs/protocols/Theme_Transfer_Protocol.md`。

---

### 2.8 云端源仓库索引（预留）
Android 端已预留云端音源仓库入口，索引建议使用以下格式：

```json
{
  "repoName": "LiSync Cloud Sources",
  "scripts": [
    {
      "name": "示例音源.js",
      "url": "https://example.com/sources/demo.js",
      "description": "支持 tx/wy/kg"
    }
  ]
}
```

兼容字段：
- 顶层列表：`scripts` / `items` / `list`
- 脚本下载地址：`url` / `scriptUrl` / `downloadUrl` / `raw`
- 脚本名：`name` / `fileName` / `id`
- 描述：`description` / `desc` / `note`

---

## 3. 平台代码对照表

| 代码 | 平台名称 | 备注 |
| :--- | :--- | :--- |
| `tx` | 腾讯音乐 (QQ Music) | |
| `wy` | 网易云音乐 | |
| `kg` | 酷狗音乐 | |
| `kw` | 酷我音乐 | |
| `mg` | 咪咕音乐 | |
| `local`| 本地/NAS | 需 App 内部实现 |

## 4. 常见问题 (FAQ)

1.  **为什么手表端一直显示“加载中”？**
    *   请检查 Android 端返回的 JSON 中是否包含了请求时的 `_requestId`。
    *   检查 `code` 是否为 `0`。

2.  **关于缓存**
    *   建议 Android 端对真实播放链接进行缓存（如 4 小时），以减少源站请求压力。
    *   若收到 `nocache: true`，请务必重新获取最新链接。

3.  **大图与流量**
    *   搜索结果尽量不返回过大的图片 URL，或者使用缩略图，以加快蓝牙传输速度。
