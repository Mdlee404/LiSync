# 主题包标准（Theme Package Spec）v1.0

本文档定义“氦音乐”主题包的统一格式与安装流程，目标是让用户从网络下载一个压缩包并解压后即可使用主题。

> 重要说明  
> 目前 Quick App 运行环境没有官方解压 API。  
> \*\*“解压”必须由手机端/电脑端完成\*\*，再通过传输（例如 LiSync 上传）或同步到手表 `internal://files/themes/{id}/`。  
> 本标准仍以 ZIP 作为分发格式，但解压执行方不在手表端。

---

## 1\. 目标与覆盖范围

主题包需要覆盖以下内容（最低要求）：

* 背景（页面/卡片/面板等）
* 按钮（主要/次要/危险等状态）
* 文字（标题/正文/次级文字）
* 颜色体系（主题色、滑块、强调色等）

可选扩展：

* 图标集（替换 `/assets/icons`）
* 歌词颜色与高亮样式
* 自定义背景图、按钮图、装饰图

---

## 2\. 包格式与目录结构

**分发格式：** `zip`

**解压后目录结构如下：**

```
{themeId}/
  theme.json                # 必需，主题清单
  preview.png               # 可选，主题预览图
  icons/                    # 可选，图标集
    返回.png
    播放.png
    暂停.png
    ...
  images/                   # 可选，背景/装饰图
    bg\_home.png
    bg\_player.png
    ...
  buttons/                  # 可选，按钮皮肤
    primary.png
    primary\_pressed.png
    danger.png
    ...
  checksums.json            # 可选，文件校验
```

**安装后的目标路径：**

```
internal://files/themes/{themeId}/theme.json
internal://files/themes/{themeId}/icons/...
internal://files/themes/{themeId}/images/...
```

---

## 3\. theme.json 规范

### 3.1 顶层结构

```json
{
  "schemaVersion": "1.0",
  "id": "aurora",
  "name": "极光",
  "version": "1.2.0",
  "author": "Mindrift",
  "description": "浅色清爽风",
  "minAppVersion": "1.0.0",
  "minPlatformVersion": 1000,
  "colors": {},
  "text": {},
  "backgrounds": {},
  "buttons": {},
  "lyric": {},
  "icons": {},
  "assets": {}
}
```

### 3.2 必填字段

* `schemaVersion`：主题规范版本（当前 `1.0`）
* `id`：主题唯一标识（小写字母/数字/下划线）
* `name`：显示名称
* `version`：主题版本号（语义化）
* `colors`：主题颜色定义（至少包含 `theme/background/text\_primary/text\_secondary`）

### 3.3 颜色字段（colors）

要求：**必须覆盖当前代码依赖的字段**，避免 UI 取值失败。

```json
"colors": {
  "theme": "#00E5FF",
  "background": "#f0f0f0",
  "text\_primary": "rgba(0,0,0,0.87)",
  "text\_secondary": "rgba(0,0,0,0.6)",
  "slider\_selected": "#00E5FF",
  "slider\_block": "#00E5FF",
  "slider\_unselected": "rgba(0,0,0,0.1)"
}
```

> 以上字段与当前 `theme\_config.js` / `theme\_light.js` / `theme\_dark.js` 兼容。

### 3.4 文字体系（text）

```json
"text": {
  "title": "rgba(0,0,0,0.87)",
  "body": "rgba(0,0,0,0.8)",
  "caption": "rgba(0,0,0,0.6)",
  "danger": "#FF3B30"
}
```

### 3.5 背景（backgrounds）

支持颜色或图片：  
`type = "color"` 或 `"image"`

```json
"backgrounds": {
  "app": { "type": "color", "value": "#f0f0f0" },
  "card": { "type": "color", "value": "rgba(255,255,255,0.1)" },
  "panel": { "type": "image", "value": "images/bg\_panel.png", "objectFit": "cover" }
}
```

### 3.6 按钮（buttons）

```json
"buttons": {
  "primary": {
    "bg": "rgba(0,229,255,0.2)",
    "text": "#00E5FF",
    "border": "rgba(0,229,255,0.3)",
    "image": "buttons/primary.png"
  },
  "danger": {
    "bg": "rgba(255,59,48,0.2)",
    "text": "#FF3B30",
    "image": "buttons/danger.png"
  }
}
```

### 3.7 歌词样式（lyric）

```json
"lyric": {
  "active": "#00E5FF",
  "normal": "#000000",
  "active\_bg": "rgba(0,229,255,0.2)"
}
```

### 3.8 图标（icons）

```json
"icons": {
  "dark\_mode": false,
  "path": "icons"
}
```

**图标文件必须包含以下名称（建议全量覆盖）：**

```
首页.png  音量.png  音乐.png  闹钟.png  返回.png
订阅.png  警告.png  菜单.png  暂停.png  播放.png
搜索.png  喜欢.png  加载.png  加.png  删除.png
减.png    关于.png  光盘.png  不喜欢.png 下载.png
下一曲.png 上一曲.png logo.png
```

> 当前 UI 通过 `iconPath + "/xxx.png"` 拼接，未做单个文件回退；  
> \*\*因此图标集应完整提供，否则会出现缺图。\*\*

### 3.9 资源路径（assets）

```json
"assets": {
  "base": ".",
  "images": "images",
  "buttons": "buttons"
}
```

**路径规则：**

* 所有路径均为相对路径，禁止 `..`
* 安装后解析为：`internal://files/themes/{id}/{path}`

---

## 4\. 安装流程标准

1. 从服务器下载 `theme.zip`
2. 在手机/电脑端解压
3. 校验 `theme.json` 与 `checksums.json`
4. 将解压后的目录同步到手表：

```
   internal://files/themes/{themeId}/
   ```

5. 设置当前主题：

   * `current\_theme\_id = {themeId}`
   * 写入 `theme\_snapshot`

---

## 5\. 校验与安全

### 5.1 checksums.json（可选）

```json
{
  "theme.json": "sha256:...",
  "icons/返回.png": "sha256:...",
  "images/bg\_home.png": "sha256:..."
}
```

### 5.2 文件大小建议

* 单图建议 `< 200KB`
* 总包建议 `< 1MB`（避免内存/存储压力）
* 超过 200KB 的背景图建议提前压缩

---

## 6\. 兼容与降级策略

当字段缺失时，建议使用默认主题兜底：

* colors 为空 → 默认 `theme\_light`
* icons 缺失 → `/assets/icons`
* backgrounds 缺失 → 走纯色背景

（当前代码未实现逐项兜底，主题包应尽量完整）

---

## 7\. 示例主题包

```json
{
  "schemaVersion": "1.0",
  "id": "aurora",
  "name": "极光",
  "version": "1.0.0",
  "author": "Mindrift",
  "colors": {
    "theme": "#00E5FF",
    "background": "#f0f0f0",
    "text\_primary": "rgba(0,0,0,0.87)",
    "text\_secondary": "rgba(0,0,0,0.6)",
    "slider\_selected": "#00E5FF",
    "slider\_block": "#00E5FF",
    "slider\_unselected": "rgba(0,0,0,0.1)"
  },
  "text": {
    "title": "rgba(0,0,0,0.87)",
    "body": "rgba(0,0,0,0.8)",
    "caption": "rgba(0,0,0,0.6)",
    "danger": "#FF3B30"
  },
  "backgrounds": {
    "app": { "type": "color", "value": "#f0f0f0" },
    "card": { "type": "color", "value": "rgba(255,255,255,0.1)" }
  },
  "buttons": {
    "primary": { "bg": "rgba(0,229,255,0.2)", "text": "#00E5FF" },
    "danger": { "bg": "rgba(255,59,48,0.2)", "text": "#FF3B30" }
  },
  "lyric": {
    "active": "#00E5FF",
    "normal": "#000000",
    "active\_bg": "rgba(0,229,255,0.2)"
  },
  "icons": {
    "dark\_mode": false,
    "path": "icons"
  },
  "assets": {
    "base": ".",
    "images": "images",
    "buttons": "buttons"
  }
}
```

---

## 8\. 后续扩展建议

后续可扩展字段（不破坏兼容）：

* `sounds`：按钮点击音效
* `fonts`：自定义字体（需平台支持）
* `animations`：背景动效/关键帧
