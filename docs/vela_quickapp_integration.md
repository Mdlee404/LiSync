# Vela 快应用接入 LiSync 指南

本文档说明 Vela 快应用（手表端）如何与 LiSync 手机端通信获取播放链接。

## 1. 前置条件
1. 手机安装并打开 **LiSync**（保持前台或后台常驻）。
2. 手机安装 **小米穿戴** 或 **小米健康**，并完成手表绑定。
3. 手表端安装 Vela 快应用（第三方 App）。
4. 在小米穿戴 App 内为 LiSync 授权 **设备管理** 权限（首次连接会自动弹出）。

## 1.1 包名与签名一致性（必做）
**interconnect 通信前提**：快应用与安卓端三方应用的 **包名** 和 **签名** 必须一致。

- 快应用 `manifest.json` 的 `package` 必须与安卓端包名一致：`mindrift.app.music`
- 快应用签名需要使用安卓端签名（`lisync.jks`）

### 从 `lisync.jks` 提取签名（推荐流程）
1) JKS 转 P12（会提示输入密码）
```bash
keytool -importkeystore -srckeystore lisync.jks -destkeystore lisync.p12 -srcstoretype jks -deststoretype pkcs12
```

2) P12 转 PEM（会提示输入 p12 密码）
```bash
openssl pkcs12 -nodes -in lisync.p12 -out lisync.pem
```

3) 拆分私钥与证书  
- 将 `-----BEGIN PRIVATE KEY-----` 到 `-----END PRIVATE KEY-----` 保存为 `private.pem`  
- 将 `-----BEGIN CERTIFICATE-----` 到 `-----END CERTIFICATE-----` 保存为 `certificate.pem`

4) 放到快应用目录  
```
/sign/debug/private.pem
/sign/debug/certificate.pem
/sign/release/private.pem
/sign/release/certificate.pem
```

### 在线签名生成工具
如果本机没有 OpenSSL，可使用官方在线签名生成工具（本地 WASM，文件不会上传）：
1) 上传 `lisync.p12` 并输入密码  
2) 点击“生成签名”  
3) 下载 `private.pem` 和 `certificate.pem`  

### 真机测试建议
安装新包前，先用包名卸载旧包，避免桌面残留导致覆盖失败。

## 2. 通信方式与编码
- 通过小米穿戴 SDK 的 **MessageApi** 进行应用间消息通信。
- 消息体使用 **UTF-8** 编码的 JSON 字符串。

> 说明：手机端已实现消息监听与权限申请，手表端只需按约定格式发送 JSON。

**重要：所有请求都要带 `_requestId`，响应必须原样回传 `_requestId`。**

## 2.1 手表就绪握手（WATCH_READY）
手表端连接成功后会发送 `WATCH_READY`，手机端会回 ACK 并推送能力集。

**手表 -> 手机**
```json
{
  "action": "WATCH_READY",
  "_requestId": "req_17273849123_1"
}
```

**手机 -> 手表**
```json
{
  "action": "WATCH_READY_ACK",
  "code": 0,
  "message": "ok",
  "_requestId": "req_17273849123_1"
}
```
随后手机端会主动推送一次 `capabilitiesUpdate`。

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

## 13. 上传音乐文件（手机 -> 手表）
手机端可主动推送音频文件到手表端保存（路径由手表端决定）。

**开始**
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

**分片**
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

**结束**
```json
{
  "action": "upload.finish",
  "_requestId": "upload_17273849123_5",
  "fileId": "file_1700000000000",
  "total": 128,
  "md5": "e10adc3949ba59abbe56e057f20f883e"
}
```

**手表确认**
```json
{
  "action": "upload.result",
  "_requestId": "upload_17273849123_5",
  "fileId": "file_1700000000000",
  "ok": true,
  "path": "/storage/vela/music/demo.mp3"
}
```

**限制**
- 单文件最大 5MB
- 分片大小 8KB

