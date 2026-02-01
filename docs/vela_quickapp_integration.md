# Vela 快应用接入 LiSync 指南

本文档说明 Vela 快应用（手表端）如何与 LiSync 手机端通信获取播放链接。

## 1. 前置条件
1. 手机安装并打开 **LiSync**（保持前台或后台常驻）。
2. 手机安装 **小米穿戴** 或 **小米健康**，并完成手表绑定。
3. 手表端安装 Vela 快应用（第三方 App）。
4. 在小米穿戴 App 内为 LiSync 授权 **设备管理** 权限（首次连接会自动弹出）。

## 2. 通信方式与编码
- 通过小米穿戴 SDK 的 **MessageApi** 进行应用间消息通信。
- 消息体使用 **UTF-8** 编码的 JSON 字符串。

> 说明：手机端已实现消息监听与权限申请，手表端只需按约定格式发送 JSON。

**重要：所有请求都要带 `_requestId`，响应必须原样回传 `_requestId`。**

## 3. 获取支持平台/音质（手表 -> 手机）
**请求示例**
```json
{
  "action": "getCapabilities",
  "_requestId": "req_17273849123_1"
}
```

**响应示例**
```json
{
  "action": "capabilities",
  "code": 0,
  "message": "ok",
  "_requestId": "req_17273849123_1",
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

> 说明：`qualities` 为空数组表示“不限”。

## 4. 搜索（手表 -> 手机）
**请求示例**
```json
{
  "action": "search",
  "platform": "tx",
  "keyword": "周杰伦",
  "page": 1,
  "pageSize": 20,
  "_requestId": "req_17273849123_2"
}
```

**字段说明**
- `platform`：可选，不传则按 `tx -> wy -> kg` 顺序回退搜索
- `keyword`：搜索关键词（必填）
- `page`：页码（默认 1）
- `pageSize`：每页条数（默认 20）

**响应示例**
```json
{
  "action": "search",
  "code": 0,
  "message": "ok",
  "_requestId": "req_17273849123_2",
  "data": {
    "platform": "tx",
    "page": 1,
    "pageSize": 20,
    "total": 1000,
    "results": [
      { "source": "tx", "id": "001X0PDf0W4lBq", "title": "歌名", "artist": "歌手" }
    ]
  },
  "info": { "action": "search", "platform": "tx", "keyword": "周杰伦", "page": 1, "pageSize": 20 }
}
```

## 5. 获取歌词（手表 -> 手机）
**请求示例**
```json
{
  "action": "lyric",
  "platform": "tx",
  "id": "001X0PDf0W4lBq",
  "_requestId": "req_17273849123_3"
}
```

**字段说明**
- `platform`：平台代码（必填）
- `id`：歌曲 ID（必填，也支持 `songid`）

**响应示例**
```json
{
  "action": "lyric",
  "code": 0,
  "message": "ok",
  "_requestId": "req_17273849123_3",
  "data": {
    "lyric": "[00:00.00]...",
    "tlyric": null,
    "rlyric": null,
    "lxlyric": null
  },
  "info": { "action": "lyric", "platform": "tx", "songId": "001X0PDf0W4lBq" }
}
```

## 6. 获取播放链接（手表 -> 手机）
**请求示例**
```json
{
  "platform": "tx",
  "quality": "128k",
  "id": "001X0PDf0W4lBq",
  "nocache": false,
  "_requestId": "req_17273849123_4"
}
```

**字段说明**
- `platform`：平台代码，取值 `tx / wy / kg / kw / mg / local`
- `quality`：音质，取值 `128k / 320k / flac / flac24bit`（具体以脚本能力为准）
- `id`：歌曲 ID（也支持 `songid`）
- `nocache`：是否跳过缓存（`true` 表示强制重新请求）

> 注意：**不要指定脚本**（不传 `targetScriptId`），手机端会自动选择可用音源。

## 7. 响应数据格式（手机 -> 手表）
**成功响应**
```json
{
  "code": 0,
  "message": "ok",
  "_requestId": "req_17273849123_4",
  "data": "https://example.com/music.mp3",
  "url": "https://example.com/music.mp3",
  "provider": "lx-music-source-paid-1769840511495.js",
  "info": {
    "platform": "tx",
    "action": "musicUrl",
    "quality": "128k",
    "songId": "001X0PDf0W4lBq",
    "provider": "lx-music-source-paid-1769840511495.js"
  }
}
```

**失败响应**
```json
{
  "code": -1,
  "message": "error message",
  "_requestId": "req_17273849123_4",
  "error": {
    "message": "error message"
  }
}
```

## 8. 平台与音质约定
- 平台代码：
  - `tx` 腾讯音乐
  - `wy` 网易云音乐
  - `kg` 酷狗音乐
  - `kw` 酷我音乐
  - `mg` 咪咕音乐
  - `local` 本地音乐
- 音质：`128k / 320k / flac / flac24bit`
  - 以脚本 `sources[x].qualitys` 为准

## 9. 缓存策略
- LiSync 默认 **4 小时缓存**。
- 请求中 `nocache = true` 可强制跳过缓存。

## 10. 常见问题排查
1. **一直返回未连接设备**
   - 检查手表是否与手机正常连接
   - 确认小米穿戴/小米健康已登录并授权
2. **无响应或超时**
   - 确保 LiSync 在后台未被系统清理
   - 检查脚本是否已导入且支持该平台
3. **返回错误消息**
   - 检查 `source/quality/songid` 是否符合脚本能力

## 11. 最小调用示例（伪代码）
```js
const payload = {
  platform: 'tx',
  quality: '128k',
  id: '001X0PDf0W4lBq',
  nocache: false,
  _requestId: 'req_' + Date.now(),
};
// 通过 Vela 侧消息通道发送 UTF-8 JSON
sendMessage(JSON.stringify(payload));
```

## 12. 平台能力更新推送（手机 -> 手表）
当手机端音源变更时，会主动推送更新：
```json
{
  "action": "capabilitiesUpdate",
  "code": 0,
  "message": "ok",
  "data": { "platforms": [...], "qualities": {...}, "actions": ["musicUrl"] }
}
```
