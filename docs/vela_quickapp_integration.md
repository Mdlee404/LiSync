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

## 3. 请求数据格式（手表 -> 手机）
**JSON 示例**
```json
{
  "source": "tx",
  "action": "musicUrl",
  "quality": "128k",
  "songid": "001X0PDf0W4lBq",
  "nocache": false,
  "targetScriptId": "lx-music-source-paid-1769840511495.js",
  "musicInfo": {
    "songmid": "001X0PDf0W4lBq",
    "hash": "001X0PDf0W4lBq"
  }
}
```

**字段说明**
- `source`：平台代码，取值 `tx / wy / kg / kw / mg / local`
- `action`：操作类型，当前仅支持 `musicUrl`
- `quality`：音质，取值 `128k / 320k / flac / flac24bit`（具体以脚本能力为准）
- `songid`：歌曲 ID（当 `musicInfo` 为空时作为兜底）
- `musicInfo`：音乐信息对象
  - `songmid`：歌曲 ID
  - `hash`：歌曲 hash
- `nocache`：是否跳过缓存（`true` 表示强制重新请求）
- `targetScriptId`：指定脚本 ID（可选，空表示使用轮询负载均衡）

## 4. 响应数据格式（手机 -> 手表）
**成功响应**
```json
{
  "data": "https://example.com/music.mp3",
  "url": "https://example.com/music.mp3",
  "provider": "lx-music-source-paid-1769840511495.js"
}
```
- `data`：响应数据（当前为 URL）
- `url`：与 `data` 一致，便于兼容
- `provider`：提供本次解析的脚本 ID

**失败响应**
```json
{
  "error": {
    "message": "error message"
  }
}
```

## 5. 平台与音质约定
- 平台代码：
  - `tx` 腾讯音乐
  - `wy` 网易云音乐
  - `kg` 酷狗音乐
  - `kw` 酷我音乐
  - `mg` 咪咕音乐
  - `local` 本地音乐
- 音质：`128k / 320k / flac / flac24bit`
  - 以脚本 `sources[x].qualitys` 为准

## 6. 缓存策略
- LiSync 默认 **4 小时缓存**。
- 请求中 `nocache = true` 可强制跳过缓存。

## 7. 常见问题排查
1. **一直返回未连接设备**
   - 检查手表是否与手机正常连接
   - 确认小米穿戴/小米健康已登录并授权
2. **无响应或超时**
   - 确保 LiSync 在后台未被系统清理
   - 检查脚本是否已导入且支持该平台
3. **返回错误消息**
   - 检查 `source/quality/songid` 是否符合脚本能力

## 8. 最小调用示例（伪代码）
```js
const payload = {
  source: 'tx',
  action: 'musicUrl',
  quality: '128k',
  songid: '001X0PDf0W4lBq',
  nocache: false,
};
// 通过 Vela 侧消息通道发送 UTF-8 JSON
sendMessage(JSON.stringify(payload));
```
