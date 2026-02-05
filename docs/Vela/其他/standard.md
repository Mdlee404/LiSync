# 规范与约定

> 项目适配说明（2026-02-04）
> - 格式化：`prettier`（`.prettierrc.js`），`eslint`/`stylelint` 按项目配置执行。
> - 构建工具：`aiot-toolkit`，designWidth = 480（见 `src/manifest.json`）。
# HeMusic 第三方扩展接入规范（音乐解析）

> 这是**音乐解析（/resolve）**的完整技术规范，不包含主题相关内容。
> 本规范用于“自定义数据源”接入，目标是让客户端能稳定获取可播放直链 URL。

---

## 0. 范围与目标
- **范围**：仅规范“音乐解析”接口（`/resolve`）。
- **不包含**：主题、歌词、封面等业务（客户端歌词走本地直连，不需要第三方提供）。
- **目标**：第三方服务只需返回可播放的 `http/https` 直链地址。

---

## 1. 关键术语与变量
### 1.1 `{base}` 是什么？
`{base}` 指**自定义源的根地址（Base URL）**，由用户在“设置 → 数据源”中输入并保存。

要求：
- 必须以 `http://` 或 `https://` 开头
- 末尾**不能带 `/`**（客户端会去掉尾部斜杠）

示例：
- ✅ `https://api.example.com`
- ✅ `http://music.example.com`
- ❌ `https://api.example.com/`（末尾 `/` 会被去掉）

### 1.2 `{base}/resolve` 是什么？
客户端会把 `{base}` 与固定路径 `/resolve` 拼接，请求音乐解析接口：
```
{base}/resolve
```
例如 `{base} = https://api.example.com`：
```
https://api.example.com/resolve
```

---

## 2. 请求方式（非常明确）
- **协议**：HTTP/HTTPS
- **方法**：**POST**
- **不是**：WebSocket / GET / PUT / 其他

**因此，必须实现：**
```
POST {base}/resolve
```

---

## 3. 客户端解析流程
1. 先查内存 LRU 缓存（默认 5 分钟）。
2. 未命中则请求 `{base}/resolve`。
3. 解析响应，提取 `url`。
4. 校验 `url` 是否为 `http/https` 且**不包含 `/resolve`**。
5. 校验通过则返回 URL，并写入 LRU 缓存。
6. 校验失败或请求失败返回空字符串（播放会提示“解析失败”）。

### 3.1 缓存机制
- **缓存对象**：仅内存 LRU，不落盘。
- **缓存键**：`${source}_${id}`。
- **过期时间**：`5 分钟`（`config.api.cache.urlExpiry`）。
- **最大容量**：100 条（LRU 淘汰最旧）。

### 3.2 失败处理
- 请求失败/解析失败 → 返回空字符串。
- 由上层提示：`解析失败`。
- **解析接口没有自动重试**（请确保稳定性）。

---

## 4. 请求规范（必须遵守）
### 4.1 请求头
客户端固定发送：
```
Content-Type: application/json
```

附加鉴权头：
- 当前实现**无 JWT**。
- `X-License` 由 `getLicenseHeadersForUrl` 统一注入（不区分域名）。
- 代码中未发送 `X-Device-Id`。

> 结论：**第三方自定义域名也会收到 `X-License`。**

### 4.2 请求体（JSON）
```json
{
  "action": "musicUrl",
  "source": "tx|wy|kg",
  "quality": "128k",
  "musicInfo": { "songmid": "xxx" }
}
```

字段解释：
- `action`：固定为 `musicUrl`。
- `source`：音乐平台标识：
  - `tx`：QQ 音乐
  - `wy`：网易云音乐
  - `kg`：酷狗音乐
- `quality`：固定 `128k`（来自 `config.api.audioQuality`）。
- `musicInfo`：
  - `tx/wy` 使用 `{ "songmid": "..." }`
  - `kg` 使用 `{ "hash": "..." }`

**注意**：
- `tx/wy` 的 `songmid` 和 `kg` 的 `hash` 都是搜索结果中返回的 `id`。
- 客户端不会额外传其它字段（如歌名/歌手）。

---

## 5. 响应规范（必须遵守）
客户端支持 3 种响应格式（任选其一）：

### 格式 A
```json
{ "data": { "url": "https://..." } }
```

### 格式 B
```json
{ "url": "https://..." }
```

### 格式 C（纯字符串）
```
https://...
```

### URL 校验规则（强制）
- 必须以 `http://` 或 `https://` 开头
- **不可包含 `/resolve` 字段**（防止死循环）
- 必须是可直接播放的媒体直链（`.mp3/.aac/.m4a` 等）

---

## 6. 典型示例
### 6.1 请求示例
**Base URL**：`https://api.example.com`

**请求地址**：
```
https://api.example.com/resolve
```

**请求体**：
```json
{
  "action": "musicUrl",
  "source": "tx",
  "quality": "128k",
  "musicInfo": { "songmid": "001CG3wA3QkuJS" }
}
```

### 6.2 响应示例
```json
{ "data": { "url": "https://cdn.example.com/music/001CG3wA3QkuJS.mp3" } }
```

---

## 7. 兼容性与实现建议
### 7.1 兼容策略
- 客户端只关心最终 `url`，其它字段会忽略。
- 服务端可以扩展附加字段，但不要依赖客户端读取。

### 7.2 性能与稳定性建议
- 保证响应时间可控（推荐 < 2s）。
- 返回直链尽量使用稳定 CDN。
- 如果需要跳转，请确保音频播放器能够跟随重定向。

---

## 8. 与“内置测试源”的关系
- 未开启自定义源时，客户端默认请求 `config.api.musicApiBaseUrl`。
- 开启自定义源后，**只影响解析接口**，不会携带 JWT。

---

## 9. 常见错误与排查
### 9.1 解析失败（客户端提示“解析失败”）可能原因
- 返回 URL 不是 `http/https`
- 返回的是 `/resolve` 自身
- JSON 格式错误或不可解析
- 服务端超时或网络错误

### 9.2 无法播放
- URL 是需要鉴权或短效 token，过期太快
- 返回的 URL 是 HTML/中转页

---

## 10. 版本适配
- 当前版本只支持 `action = musicUrl` + `quality = 128k`。
- 若未来扩展高音质或其他 action，需要客户端同步支持。

