# 主题传输协议（Android -> Vela）

用于安卓端通过 LiSync 向手表端下发主题文件。手表端会保存到：

```
internal://files/themes/{themeId}/
```

> 注意：手表端仅保存文件，不自动应用主题。应用主题需在手表端“主题管理”页手动选择。

---

## 一、基本流程（推荐）

1. **打开主题接收页（可选）**
2. **初始化主题（创建目录）**
3. **逐文件发送（base64 分片）**
4. **发送完成指令**

> LiSync 侧导入提示  
> - 支持直接选择 `theme.zip`，会自动解压到私有目录后按本协议发送。  
> - ZIP 内需满足：根目录或单一子目录包含 `theme.json`。  
> - `themeId` 会在发送前统一转为小写并校验。

---

## 二、消息定义

> 重要：除 `theme.file.chunk` 外，其余请求 **必须带 `_requestId`**，手表端会用同一 `_requestId` 返回结果。

### 1) 打开主题接收页（可选）

**Phone -> Watch**
```json
{
  "action": "theme.open",
  "_requestId": "theme_open_1",
  "themeId": "aurora"
}
```

**Watch -> Phone**
```json
{
  "action": "theme.open.result",
  "_requestId": "theme_open_1",
  "ok": true
}
```

---

### 2) 初始化主题（创建目录）

**Phone -> Watch**
```json
{
  "action": "theme.init",
  "_requestId": "theme_init_1",
  "themeId": "aurora",
  "totalFiles": 12,
  "totalChunks": 240,
  "totalBytes": 983040,
  "clean": true
}
```

**说明**
- `themeId`：主题 ID（小写字母/数字/下划线/中划线，最长 32）
- `themeId` 会被手表端统一转为小写（建议 Android 端直接小写）
- `clean`：是否删除已存在目录（默认 `true`）
- `totalFiles/totalChunks/totalBytes`：用于进度显示，可选，但建议填写

**Watch -> Phone**
```json
{
  "action": "theme.init.result",
  "_requestId": "theme_init_1",
  "ok": true,
  "themeId": "aurora"
}
```

---

### 3) 文件发送（分片）

#### 3.1 开始某个文件

**Phone -> Watch**
```json
{
  "action": "theme.file.start",
  "_requestId": "file_start_1",
  "fileId": "f1",
  "themeId": "aurora",
  "path": "theme.json",
  "size": 2048,
  "totalChunks": 1
}
```

**Watch -> Phone**
```json
{
  "action": "theme.file.start.result",
  "_requestId": "file_start_1",
  "ok": true,
  "path": "theme.json",
  "fileId": "f1"
}
```

**说明**
- `fileId` 可选；若未提供，手表端用 `path` 作为标识
- `path` 必须是相对路径（见“路径规则”）

#### 3.2 发送分片

**Phone -> Watch**
```json
{
  "action": "theme.file.chunk",
  "fileId": "f1",
  "themeId": "aurora",
  "path": "theme.json",
  "index": 1,
  "total": 1,
  "data": "BASE64..."
}
```

**说明**
- `data`：base64 内容（二进制）
- `index/total`：仅用于进度统计
- 手表端不会校验 `index/total` 顺序，建议 Android 端按顺序发送
- 默认不回 ACK（如果需要，可加 `needAck: true` 且必须带 `_requestId`）

#### 3.3 结束某个文件

**Phone -> Watch**
```json
{
  "action": "theme.file.finish",
  "_requestId": "file_finish_1",
  "fileId": "f1",
  "themeId": "aurora",
  "path": "theme.json"
}
```

**Watch -> Phone**
```json
{
  "action": "theme.file.finish.result",
  "_requestId": "file_finish_1",
  "ok": true,
  "path": "theme.json",
  "fileId": "f1"
}
```

---

### 4) 完成传输

**Phone -> Watch**
```json
{
  "action": "theme.finish",
  "_requestId": "theme_finish_1",
  "themeId": "aurora"
}
```

**Watch -> Phone**
```json
{
  "action": "theme.finish.result",
  "_requestId": "theme_finish_1",
  "ok": true,
  "themeId": "aurora"
}
```

---

### 5) 取消传输（可选）

**Phone -> Watch**
```json
{
  "action": "theme.cancel",
  "_requestId": "theme_cancel_1",
  "clean": true
}
```

**Watch -> Phone**
```json
{
  "action": "theme.cancel.result",
  "_requestId": "theme_cancel_1",
  "ok": true
}
```

---

## 三、路径规则（必须遵守）

- `path` 为相对路径，如 `theme.json`、`icons/返回.png`
- 禁止 `..`、禁止绝对路径、禁止 `internal://` 前缀
- 分隔符统一用 `/`
- 反斜杠会被自动转换为 `/`（但仍建议 Android 端直接使用 `/`）

---

## 四、数据与约束（强制/建议）

**强制**
- 必须先 `theme.init` 再发送任何文件
- 每个文件必须按顺序：`theme.file.start` → 多个 `theme.file.chunk` → `theme.file.finish`
- base64 必须为标准 Base64（非 URL-safe），数据需是 UTF-8/二进制原始字节

**建议**
- 分片大小建议 **8KB**（base64 后约 10.7KB）
- 单文件建议 < 200KB，总体建议 < 1MB
- 进度显示优先级：`totalBytes` > `totalChunks` > `totalFiles`

---

## 五、失败返回与异常

以下情况会返回 `ok: false`（并带 `message`）：
- `themeId` 不合法
- 目录创建失败
- `path` 非法（含 `..` 或绝对路径）
- `theme.json` 缺失或无法解析（`theme.finish`）

出现失败后，建议：
1) 调用 `theme.cancel`（`clean: true`）清理残留  
2) 重新执行完整流程

---

## 六、最小示例流程

1. `theme.open`
2. `theme.init`
3. `theme.file.start`（theme.json）
4. `theme.file.chunk`（一片）
5. `theme.file.finish`
6. 依次发送 icons/images 等文件
7. `theme.finish`

---

## 七、主题结构参考

主题结构与字段要求请参考：`docs/theme/theme.md`

> 提示：`theme.finish` 仅检查 `theme.json` 是否存在且可解析；  
> 主题字段完整性会在手表端切换主题时校验，若不符合 `docs/theme/theme.md` 会回退到默认主题。
