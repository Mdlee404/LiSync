# 歌词与本地

> 项目适配说明（2026-02-04）
> - 歌词获取逻辑：优先读取本地 `lrcPath`，否则缓存，再请求 LiSync（`src/common/lyric.js`）。
> - 解析器：`src/common/lrc-parser.js`，支持多时间标签、offset、二分查找定位。
> - 歌词偏移量：`kv_storage` 键 `lyric_offset_ms`，默认 1000ms。
> - 本地列表与收藏存储在 `internal://files/app_db.json`。
# OMusic 项目详细分析文档
## 歌词滚动与存储管理

---

## 第一部分：歌词滚动功能分析

### 1. 项目概述

**OMusic** 是一个基于小米 Vela 平台的音乐播放器应用，使用 `.ux` 模板语法开发（快应用/鸿蒙开发风格）。主要功能包括在线音乐播放、歌词显示、本地下载管理等。

---

### 2. 歌词数据流架构

```
原始歌词 → lyric_cook.js → cooked格式 → player.ux → UI显示
```

**关键文件：**
- `src/utils/lyric_cook.js` - 歌词解析和格式转换工具
- `src/pages/player/player.ux` - 播放器页面，包含歌词渲染和滚动逻辑

---

### 3. 歌词数据结构详解

#### 3.1 Cooked 格式（v3）

```javascript
{
  v: 3,                          // 版本号
  songId: "123",                 // 歌曲ID
  type: "chinese",               // 歌词类型：chinese | english | japanese | cantonese
  flags: {                       // 标志位
    sgc: false,                  // 是否是SGC歌词
    sfy: false,                  // 是否是SFY歌词
    qfy: false                   // 是否是QFY歌词
  },
  ver: {                         // 版本信息
    lrc: 123456,                 // 原文歌词版本
    tlyric: 123457,              // 翻译歌词版本
    romalrc: 123458              // 罗马音歌词版本
  },
  by: {                          // 贡献者信息
    lyric: { uid: 123, name: "张三", uptime: 123456789 },
    trans: { uid: 456, name: "李四", uptime: 123456790 }
  },
  lines: [                       // 歌词行数组
    { t: 12.5, o: "歌词原文", tr: "翻译", ro: "罗马音" },
    { t: 18.2, o: "第二句歌词", tr: "第二句翻译" }
  ]
}
```

#### 3.2 UI 渲染数据结构

```javascript
// this.lyrics - 原始歌词数据
this.lyrics = [
  {
    time: 12.5,              // 时间戳（秒）
    original: "歌词原文",    // 原文
    translation: "翻译",     // 翻译
    romaji: "罗马音"         // 罗马音
  }
];

// this.lyricLines - UI渲染用（包含状态）
this.lyricLines = [
  {
    id: "lyric-12.5-0",      // 唯一ID
    originalIndex: 0,        // 原始索引
    original: "歌词原文",    // 原文
    extra: "翻译",           // 附加歌词（翻译或罗马音）
    isCurrent: false,        // 是否是当前高亮行
    isSelected: false       // 是否被选中（点击时）
  }
];

// this.fullscreenLines - fullscreen 模式下的可见行
this.fullscreenLines = [
  { id: "lyric-5.0-0", originalIndex: 0, original: "...", ... },
  { id: "lyric-10.0-1", originalIndex: 1, original: "...", ... }
];

// this.lyricGroup0 ~ this.lyricGroup63 - 64个分组用于性能优化
```

---

### 4. 歌词滚动机制详解

#### 4.1 两种渲染模式

**Fullscreen 模式**（默认）
- 固定显示 5 行（圆屏）或 7 行（方屏）
- **不可手动滚动**
- 通过 `buildFullscreenLines()` 动态计算可见行

**All 模式**
- 可滚动的完整歌词列表
- 支持**点击歌词跳转**功能
- 使用 64 个分组优化性能

#### 4.2 Fullscreen 模式核心实现

```javascript
// src/pages/player/player.ux

buildFullscreenLines() {
  const lines = this.lyricLines || [];
  const total = lines.length;
  if (!total) {
    this.fullscreenLines = [];
    return;
  }

  // 1. 获取当前索引：优先用 playerState.currentLyricIndex
  let cur = typeof this.playerState?.currentLyricIndex === "number"
    ? this.playerState.currentLyricIndex
    : -1;

  // 2. 兜底：还没算出 currentLyricIndex 时，用播放时间快速算一个
  if (!(cur >= 0 && cur < total)) {
    const lookaheadTime =
      (this.playerState?.playDuration || 0) +
      (this.settings?.lyricAdvanceTime || 1.5);

    let newIndex = 0;
    let low = 0;
    let high = (this.lyrics ? this.lyrics.length : 0) - 1;
    while (low <= high) {
      const mid = (low + high) >> 1;
      if (this.lyrics[mid].time > lookaheadTime) high = mid - 1;
      else {
        newIndex = mid;
        low = mid + 1;
      }
    }
    cur = newIndex;
  }

  // 3. 判断是否有附加歌词
  const extraText = this.getExtraLyricText(this.lyrics[cur]);
  const hasExtra = !!(extraText && String(extraText).trim());

  // 4. 计算可见行：有 extra → 3；无 extra → 5
  // 上下各多带 1 行不可见，所以总行数分别为 5 / 7
  const visible = hasExtra ? 3 : 5;
  const N = visible + 2;

  const half = N >> 1;
  let start = cur - half;
  let end = start + N - 1;

  // 5. 边界处理
  if (start < 0) {
    start = 0;
    end = Math.min(total - 1, N - 1);
  }
  if (end >= total) {
    end = total - 1;
    start = Math.max(0, end - (N - 1));
  }

  // 6. 切片生成可见行
  this.fullscreenLines = lines.slice(start, end + 1);
},
```

#### 4.3 All 模式性能优化实现

```javascript
// src/pages/player/player.ux

distributeLyricsToGroups() {
  const NUM_GROUPS = 64;

  // 1. 初始化所有分组
  for (let i = 0; i < NUM_GROUPS; i++) this[`lyricGroup${i}`] = [];

  const lines = this.lyricLines;
  const total = lines ? lines.length : 0;
  if (!total) {
    this._lyricGroupMeta = [];
    return;
  }

  // 2. 均匀分配歌词到64个组
  const base = Math.floor(total / NUM_GROUPS);
  const remainder = total % NUM_GROUPS;

  this._lyricGroupMeta = new Array(NUM_GROUPS);

  let cursor = 0;
  for (let g = 0; g < NUM_GROUPS; g++) {
    const count = base + (g < remainder ? 1 : 0);
    const start = cursor;
    const end = cursor + count - 1;

    // slice：不修改 lyricLines；只创建 group 的数组"壳"
    this[`lyricGroup${g}`] =
      count > 0 ? lines.slice(cursor, cursor + count) : [];
    cursor += count;

    // 保存元数据，用于快速查找
    this._lyricGroupMeta[g] = { start, end, count };
  },
}

// 根据歌词索引查找所在分组
getLyricGroupIndex(lyricIndex) {
  const meta = this._lyricGroupMeta;
  if (!meta || meta.length !== 64 || lyricIndex < 0) return -1;

  // 二分查找
  let lo = 0, hi = meta.length - 1;
  while (lo <= hi) {
    const mid = (lo + hi) >> 1;
    const m = meta[mid];
    if (!m || m.count <= 0) return -1;

    if (lyricIndex < m.start) hi = mid - 1;
    else if (lyricIndex > m.end) lo = mid + 1;
    else return mid;
  }
  return -1;
}

// 只刷新受影响的分组
refreshLyricGroupsByIndex(indices) {
  if (!indices || !indices.length) return;

  const touched = new Set();
  for (let i = 0; i < indices.length; i++) {
    const g = this.getLyricGroupIndex(indices[i]);
    if (g >= 0) touched.add(g);
  }

  touched.forEach((g) => {
    const key = `lyricGroup${g}`;
    const arr = this[key];
    // 只重建该组数组引用，触发该组 DOM 刷新
    this[key] = arr ? arr.slice() : [];
  });
},
```

---

### 5. 歌词更新逻辑

#### 5.1 核心更新函数

```javascript
// src/pages/player/player.ux

updateLyric() {
  // 1. 检查条件
  if (this.swiperCurrentIndex !== 1) return;
  if (!this.lyrics || this.lyrics.length === 0) return;
  if (!this.lyricLines || this.lyricLines.length !== this.lyrics.length) return;

  // 2. 获取渲染模式
  const mode = (this.settings && this.settings.lyricRenderMode) || "fullscreen";

  // 3. 计算提前时间
  const lookaheadTime =
    this.playerState.playDuration +
    (this.settings.lyricAdvanceTime || 1.5); // 默认提前1.5秒

  const oldIndex = this.playerState.currentLyricIndex;

  // 4. 二分查找当前歌词索引
  let newIndex = 0;
  let low = 0;
  let high = this.lyrics.length - 1;
  while (low <= high) {
    const mid = Math.floor((low + high) / 2);
    if (this.lyrics[mid].time > lookaheadTime) {
      high = mid - 1;
    } else {
      newIndex = mid;
      low = mid + 1;
    }
  }

  // 5. 如果索引没变化，直接返回
  if (newIndex === oldIndex) return;

  // 6. 更新旧行的状态
  if (oldIndex >= 0) {
    this.lyricLines[oldIndex].isCurrent = false;
  }

  // 7. 更新新行的状态
  if (newIndex >= 0) {
    this.lyricLines[newIndex].isCurrent = true;
    // 更新附加歌词
    this.lyricLines[newIndex].extra =
      this.getExtraLyricText(this.lyrics[newIndex]) || null;
  }

  // 8. 保存当前索引
  this.playerState.currentLyricIndex = newIndex;

  // 9. 根据模式渲染
  if (mode === "fullscreen") {
    this.buildFullscreenLines();
    return;
  }

  // 10. all模式：刷新分组并滚动
  this.refreshLyricGroupsByIndex([oldIndex, newIndex]);
  this.$nextTick(() => {
    this.scrollToCurrentLyric();
  });
},
```

#### 5.2 附加歌词获取

```javascript
// src/pages/player/player.ux

getExtraLyricText(lineData) {
  if (!lineData) return "";
  let extraText = "";
  const lyricSettings = this.settings.lyrics || {};

  switch (this.playerState.lyricType) {
    case "japanese":
      if (lyricSettings.japaneseMode === "translation")
        extraText = lineData.translation || "";
      else if (lyricSettings.japaneseMode === "romaji")
        extraText = lineData.romaji || "";
      break;
    case "cantonese":
      if (lyricSettings.cantoneseMode === "romaji")
        extraText = lineData.romaji || "";
      break;
    case "english":
      if (lyricSettings.englishMode === "translation")
        extraText = lineData.translation || "";
      break;
  }
  return extraText;
},
```

---

### 6. 歌词滚动到当前位置

```javascript
// src/pages/player/player.ux

scrollToCurrentLyric(isInitial = false) {
  // 1. 检查条件
  if (
    this.swiperCurrentIndex !== 1 ||
    !this._lyricsScrollView ||
    this.playerState.currentLyricIndex < 0
  ) {
    return;
  }

  // 2. 获取当前歌词行元素
  const lineElement = this.$element(
    `lyric-line-${this.playerState.currentLyricIndex}`
  );
  if (!lineElement) return;

  // 3. 使用回调链避免竞态
  this._lyricsScrollView.getBoundingClientRect({
    success: (scrollRect) => {
      lineElement.getBoundingClientRect({
        success: (lineRect) => {
          // 4. 计算目标位置（居中在40%处）
          const targetCenterY = scrollRect.height * 0.4;
          const lineTopInContainer = lineRect.top - scrollRect.top;
          const scrollOffset =
            lineTopInContainer + lineRect.height / 2 - targetCenterY;

          // 5. 执行滚动
          this._lyricsScrollView.scrollBy({
            top: scrollOffset,
            // 初次进入时无动画，后续切换有动画
            behavior: isInitial ? "instant" : "smooth",
          });
        },
      });
    },
  });
},
```

---

### 7. 歌词点击跳转功能

```javascript
// src/pages/player/player.ux

onLyricLineClick(line, originalIndex) {
  const index = originalIndex;

  if (this.selectedLyricIndex !== index) {
    // ========== 首次点击 ==========
    if (this.playerState.isPlaying) {
      audio.pause();
    }

    this.clearSelectionTimeout();
    this.updateLyricSelection(this.selectedLyricIndex, false);
    this.selectedLyricIndex = index;
    this.updateLyricSelection(index, true);

    this.startSelectionTimeout();
    prompt.showToast({ message: `已暂停，再次点击跳转`, duration: 200 });
  } else {
    // ========== 第二次点击 (确认跳转) ==========
    this.clearSelectionTimeout();

    // 清选中框
    if (this.selectedLyricIndex !== -1) {
      const prevSel = this.selectedLyricIndex;
      this.lyricLines[prevSel].isSelected = false;
      this.selectedLyricIndex = -1;
      this.refreshLyricGroupsByIndex([prevSel]);
    }

    // 跳转并播放
    const targetTime = this.lyrics[index].time;
    audio.currentTime = targetTime;
    audio.play();
    prompt.showToast({
      message: `已跳转到 ${this.second2time(targetTime)}`,
      duration: 200,
    });

    // 更新歌词
    this.updateLyric();
    this.$nextTick(() => this.scrollToCurrentLyric(true));
  }
},
```

---

### 8. 歌词解析工具

#### 8.1 parseLyric - 解析 LRC 字符串

```javascript
// src/utils/lyric_cook.js

export function parseLyric(lrcString) {
  const lines = (lrcString || "").split("\n");
  const result = [];

  // 支持多种时间戳格式：
  // [mm:ss] / [mm:ss.xx] / [mm:ss.xxx]
  // [mm:ss:xx] / [mm:ss:xxx] (网易云脏数据)
  const timeRe = /\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\]/g;

  for (const raw of lines) {
    if (!raw) continue;

    // 1) 抓取该行所有时间戳
    timeRe.lastIndex = 0;
    const times = [];
    let m;

    while ((m = timeRe.exec(raw)) !== null) {
      const mm = parseInt(m[1], 10);
      const ss = parseInt(m[2], 10);
      const fracRaw = m[3];

      let ms = 0;
      if (fracRaw != null) {
        // 兼容 1-3 位毫秒
        const frac = String(fracRaw);
        if (frac.length === 1) ms = parseInt(frac, 10) * 100;
        else if (frac.length === 2) ms = parseInt(frac, 10) * 10;
        else ms = parseInt(frac.slice(0, 3).padEnd(3, "0"), 10);
      }

      const t = mm * 60 + ss + ms / 1000;
      times.push(t);
    }

    if (!times.length) continue;

    // 2) 去掉该行所有时间戳后的"正文"
    const text = raw.replace(timeRe, "").trim();

    // 3) 正文为空，跳过
    if (!text) continue;

    // 4) 一行多个时间戳：同一句在多个时间点出现
    for (const t of times) result.push({ time: t, text });
  }

  // 5) 排序
  result.sort((a, b) => a.time - b.time);

  return result.length ? result : [{ time: 0, text: "暂无歌词" }];
}
```

#### 8.2 cookLyricsFromRaw - 原始数据转换为 Cooked 格式

```javascript
// src/utils/lyric_cook.js

export function cookLyricsFromRaw(rawData, songId) {
  const raw = rawData && typeof rawData === "object" ? rawData : {};

  // 1) 解析三种歌词
  const originalArr = raw?.lrc?.lyric ? parseLyric(raw.lrc.lyric) : null;
  const translationArr = raw?.tlyric?.lyric ? parseLyric(raw.tlyric.lyric) : null;
  const romajiArr = raw?.romalrc?.lyric ? parseLyric(raw.romalrc.lyric) : null;

  // 2) 推断 lyricType
  let type = "chinese";
  if (romajiArr && translationArr) type = "japanese";
  else if (romajiArr && !translationArr) type = "cantonese";
  else if (translationArr) type = "english";

  // 3) 对齐 translation/romaji（按 time.toFixed(3) 键）
  const createMap = (arr) => {
    if (!arr || !arr.length) return null;
    const m = new Map();
    for (const it of arr) {
      if (!it || typeof it.time !== "number") continue;
      const key = it.time.toFixed(3);
      const val = (it.text || "").trim();
      if (val) m.set(key, val);
    }
    return m.size ? m : null;
  };

  const transMap = createMap(translationArr);
  const romaMap = createMap(romajiArr);

  // 4) 生成 cooked lines
  let lines = [];
  if (originalArr && originalArr.length) {
    lines = originalArr
      .map((line) => {
        const key = line.time.toFixed(3);
        const o = (line.text || "").trim();

        if (!o) return null;

        const out = { t: line.time, o };

        const tr = transMap ? transMap.get(key) : null;
        const ro = romaMap ? romaMap.get(key) : null;

        if (tr) out.tr = tr;
        if (ro) out.ro = ro;

        return out;
      })
      .filter(Boolean);
  }

  if (!lines.length) {
    lines = [{ t: 0, o: "暂无歌词" }];
  }

  // 5) 返回 cooked 对象
  return {
    v: 3,
    songId: String(songId),
    type,
    flags: {
      sgc: !!raw.sgc,
      sfy: !!raw.sfy,
      qfy: !!raw.qfy,
    },
    ver: {
      lrc: Number(raw?.lrc?.version) || 0,
      tlyric: Number(raw?.tlyric?.version) || 0,
      romalrc: Number(raw?.romalrc?.version) || 0,
    },
    by: {
      lyric: pickLyricUser(raw.lyricUser),
      trans: pickLyricUser(raw.transUser),
    },
    lines,
  };
}
```

---

### 9. 歌词渲染模板

#### 9.1 Fullscreen 模式模板

```html
<!-- src/pages/player/player.ux -->

<div
  if="{{ swiperCurrentIndex === 1 && settings.lyricRenderMode === 'fullscreen' }}"
  class="fullscreen-lyrics"
  onswipe="handleSwipe"
>
  <div
    for="{{(i, line) in fullscreenLines}}"
    tid="line.id"
    class="fs-line lyric-line-wrapper
           {{ line.isCurrent ? 'is-current-wrapper' : '' }}
           {{ line.isSelected ? 'is-selected-wrapper' : '' }}"
  >
    <!-- 主歌词 -->
    <text class="lyric-line {{ line.isCurrent ? 'current-lyric' : 'secondary-lyric' }}">
      {{ line.original }}
    </text>

    <!-- 附加歌词 -->
    <text
      if="{{ line.extra }}"
      class="lyric-line extra-lyric {{ line.isCurrent ? 'extra-current' : 'extra-secondary' }}"
    >
      {{ line.extra }}
    </text>
  </div>
</div>
```

#### 9.2 All 模式模板

```html
<!-- src/pages/player/player.ux -->

<scroll
    show="{{ swiperCurrentIndex === 1 && settings.lyricRenderMode === 'all' }}"
    id="lyricsScrollView"
    class="lyrics-scroll-view"
    scroll-y="true"
    onswipe="handleSwipe"
>
    <div class="lyrics-list-padding-top"></div>

    <!-- 64个分组优化渲染 -->
    <div for="{{(index, line) in lyricGroup0}}" tid="line.id" id="lyric-line-{{line.originalIndex}}"
         class="lyric-line-wrapper {{line.isCurrent ? 'is-current-wrapper' : ''}} {{line.isSelected ? 'is-selected-wrapper' : ''}}"
         onclick="onLyricLineClick(line, line.originalIndex)">
        <text class="lyric-line {{line.isCurrent ? 'current-lyric' : 'secondary-lyric'}}">{{ line.original }}</text>
        <text if="{{line.isCurrent && line.extra}}" class="lyric-line extra-lyric">{{ line.extra }}</text>
    </div>

    <!-- lyricGroup1 ~ lyricGroup63 省略 -->

    <div class="lyrics-list-padding-bottom"></div>
</scroll>
```

---

### 10. 设置项配置

```javascript
// src/pages/player/player.ux

settings: {
  // 渲染模式：fullscreen（固定行） | all（可滚动）
  lyricRenderMode: "fullscreen",

  // 歌词提前时间（秒），用于更流畅的显示
  lyricAdvanceTime: 1.5,

  // 歌词显示设置
  lyrics: {
    // 日语模式：translation（显示翻译） | romaji（显示罗马音）
    japaneseMode: "translation",
    // 粤语模式：romaji（显示罗马音）
    cantoneseMode: "romaji",
    // 英语模式：translation（显示翻译）
    englishMode: "translation"
  }
}
```

---

### 11. 技术亮点总结

1. **性能优化**
   - 使用 64 个分组避免一次性渲染大量 DOM
   - 只刷新受影响的分组，减少不必要的重渲染

2. **多语言支持**
   - 支持原文、翻译、罗马音三种歌词类型
   - 智能推断歌词类型（chinese/english/japanese/cantonese）

3. **提前滚动**
   - `lyricAdvanceTime: 1.5` 提前 1.5 秒更新歌词，让显示更流畅

4. **智能分组刷新**
   - 通过 `refreshLyricGroupsByIndex` 只刷新变化的分组

5. **双击跳转**
   - 先选中确认再跳转，避免误操作
   - 首次点击暂停并高亮，第二次点击确认跳转

6. **两种渲染模式**
   - Fullscreen：固定行数，自动居中
   - All：完整可滚动列表

---

## 第二部分：存储管理页面分析

### 1. 页面概述

**存储管理页面**（`src/pages/storage/storage.ux`）用于管理和查看本地下载的歌曲、歌词以及存储空间使用情况。

---

### 2. 数据结构

#### 2.1 存储相关常量

```javascript
// src/pages/storage/storage.ux

const DIR_MUSIC = "internal://files/music/";
const DIR_LYRICS = "internal://files/lyrics/";
const FILE_DOWNLOADED_SONGS = "internal://files/downloaded_songs.json";
const API_SONG_DETAIL = "https://163api.qijieya.cn/song/detail?ids=";
```

#### 2.2 页面状态数据

```javascript
// src/pages/storage/storage.ux

export default {
  private: {
    // 存储统计
    totalStorage: 0,           // 总存储空间（字节）
    availableStorage: 0,       // 可用存储空间（字节）
    musicSize: 0,              // 音乐文件总大小（字节）
    lyricsSize: 0,             // 歌词文件总大小（字节）

    // 歌曲列表
    fullSongList: [],          // 完整歌曲列表
    displaySongList: [],       // 当前页显示的歌曲列表

    // 分页设置
    PAGE_SIZE: 10,             // 每页显示数量
    isLoading: true,           // 是否正在加载
    totalPages: 1,             // 总页数
    currentPage: 1,            // 当前页码

    // UI 状态
    currentTime: "00:00",      // 顶部时间显示
    hasUnknownSongs: false,    // 是否有未知歌曲（需要修复）

    // 批量删除模式
    isDeleteMode: false,       // 是否处于批量删除模式

    // 跳页键盘
    showPageKeypad: false,     // 是否显示跳页键盘
    pageInput: "",             // 跳页输入框内容
  },

  computed: {
    // 已使用存储空间
    usedStorage() {
      return this.totalStorage - this.availableStorage;
    },
    // 使用百分比
    usedPercent() {
      return this.totalStorage > 0
        ? Math.round((this.usedStorage / this.totalStorage) * 100)
        : 0;
    },
    // 格式化后的文本
    totalStorageText() {
      return this.formatSize(this.totalStorage);
    },
    availableStorageText() {
      return this.formatSize(this.availableStorage);
    },
    usedStorageText() {
      return this.formatSize(this.usedStorage);
    },
    musicSizeText() {
      return this.formatSize(this.musicSize);
    },
    lyricsSizeText() {
      return this.formatSize(this.lyricsSize);
    },
  },
}
```

#### 2.3 歌曲项数据结构

```javascript
{
  id: "123456",                    // 歌曲ID
  name: "歌曲名称",                 // 歌曲名称
  artists: "歌手名",               // 歌手
  duration: 240,                   // 时长（秒）
  isUnknown: false,                // 是否是未知歌曲
  localUri: "internal://files/music/123456.mp3",  // 本地音乐文件URI
  localLyricUri: "internal://files/lyrics/123456.json",  // 本地歌词文件URI
  musicFileSize: 5242880,          // 音乐文件大小（字节）
  lyricFileSize: 2048,             // 歌词文件大小（字节）
  displayIndex: 1                  // 显示序号（每页重新计算）
}
```

---

### 3. 核心功能实现

#### 3.1 存储扫描与加载

```javascript
// src/pages/storage/storage.ux

async loadAndScanStorage() {
  // 1. 防止重复加载
  if (this.isLoading && this.fullSongList.length > 0) return;
  this.isLoading = true;
  this.fullSongList = [];
  this.hasUnknownSongs = false;

  try {
    // 2. 并行获取所有数据
    const [
      totalStorageData,
      availableStorageData,
      downloadedMeta,
      musicFilesData,
      lyricFilesData
    ] = await Promise.all([
      // 获取总存储空间
      fileService._promisify(device.getTotalStorage)
        .catch(() => ({ totalStorage: 0 })),
      // 获取可用存储空间
      fileService._promisify(device.getAvailableStorage)
        .catch(() => ({ availableStorage: 0 })),
      // 读取下载歌曲元数据
      fileService.readJson(FILE_DOWNLOADED_SONGS, {}),
      // 列出音乐文件
      fileService._promisify(file.list, { uri: DIR_MUSIC })
        .catch(() => ({ fileList: [] })),
      // 列出歌词文件
      fileService._promisify(file.list, { uri: DIR_LYRICS })
        .catch(() => ({ fileList: [] }))
    ]);

    // 3. 更新存储统计
    this.totalStorage = totalStorageData.totalStorage;
    this.availableStorage = availableStorageData.availableStorage;

    // 4. 构建文件映射表
    const musicFiles = musicFilesData.fileList || [];
    const lyricFiles = lyricFilesData.fileList || [];

    const musicFileMap = new Map(
      musicFiles.map(f => [
        f.uri.split('/').pop().replace('.mp3', ''),
        { uri: f.uri, size: f.length }
      ])
    );

    const lyricFileMap = new Map(
      lyricFiles.map(f => [
        f.uri.split('/').pop().replace('.json', ''),
        { uri: f.uri, size: f.length }
      ])
    );

    // 5. 遍历音乐文件，构建歌曲列表
    let newFullSongList = [];
    let updatedDownloadedMeta = {};
    let totalMusicSize = 0;
    let totalLyricsSize = 0;
    let unknownCount = 0;

    for (const [songId, musicFileInfo] of musicFileMap.entries()) {
      const meta = downloadedMeta[songId];
      const lyricFileInfo = lyricFileMap.get(songId);

      if (meta) {
        // 已知歌曲：使用元数据
        newFullSongList.push({
          ...meta,
          isUnknown: false,
          musicFileSize: musicFileInfo.size,
          lyricFileSize: lyricFileInfo ? lyricFileInfo.size : 0
        });
        updatedDownloadedMeta[songId] = meta;
      } else {
        // 未知歌曲：使用文件信息
        unknownCount++;
        newFullSongList.push({
          id: songId,
          name: '未知歌曲',
          artists: `ID: ${songId}`,
          isUnknown: true,
          localUri: musicFileInfo.uri,
          localLyricUri: lyricFileInfo ? lyricFileInfo.uri : null,
          musicFileSize: musicFileInfo.size,
          lyricFileSize: lyricFileInfo ? lyricFileInfo.size : 0
        });
      }

      totalMusicSize += musicFileInfo.size;
      if (lyricFileInfo) totalLyricsSize += lyricFileInfo.size;
    }

    // 6. 如果元数据有变化，更新保存
    if (Object.keys(downloadedMeta).length !==
        Object.keys(updatedDownloadedMeta).length) {
      fileService.writeJson(FILE_DOWNLOADED_SONGS, updatedDownloadedMeta);
    }

    // 7. 更新页面状态
    this.fullSongList = newFullSongList;
    this.musicSize = totalMusicSize;
    this.lyricsSize = totalLyricsSize;
    this.hasUnknownSongs = unknownCount > 0;
    this.totalPages = Math.ceil(this.fullSongList.length / this.PAGE_SIZE) || 1;
    this.loadPage(1);

  } catch (e) {
    console.error("加载和扫描存储时出错:", e);
    prompt.showToast({ message: `加载失败: ${e.message}` });
  } finally {
    this.isLoading = false;
  }
}
```

#### 3.2 分页加载

```javascript
// src/pages/storage/storage.ux

loadPage(pageNumber) {
  // 1. 验证页码
  if (pageNumber < 1 || (pageNumber > this.totalPages && this.totalPages > 0))
    return;

  // 2. 计算起止索引
  const startIndex = (pageNumber - 1) * this.PAGE_SIZE;
  const endIndex = startIndex + this.PAGE_SIZE;

  // 3. 切片并添加显示序号
  this.displaySongList = this.fullSongList
    .slice(startIndex, endIndex)
    .map((song, index) => ({
      ...song,
      displayIndex: startIndex + index + 1,
    }));

  // 4. 更新当前页
  this.currentPage = pageNumber;

  // 5. 滚动到顶部
  this.$nextTick(() => {
    this.$element("songListScroll").scrollTo({ top: 1 });
  });
}

// 上一页（循环）
loadPrevPage() {
  if (this.totalPages <= 1) return;
  const prev = this.currentPage <= 1 ? this.totalPages : this.currentPage - 1;
  this.loadPage(prev);
}

// 下一页（循环）
loadNextPage() {
  if (this.totalPages <= 1) return;
  const next = this.currentPage >= this.totalPages ? 1 : this.currentPage + 1;
  this.loadPage(next);
}
```

#### 3.3 删除歌曲

```javascript
// src/pages/storage/storage.ux

// 右侧按钮点击处理
handleDeleteClick(item) {
  if (!item) return;
  if (this.isDeleteMode) {
    // 批量模式：直接删除
    this.deleteSongDirect(item);
  } else {
    // 普通模式：弹出确认框
    this.deleteSong(item);
  }
}

// 切换批量删除模式
toggleDeleteMode() {
  if (this.fullSongList.length === 0) return;
  this.isDeleteMode = !this.isDeleteMode;
  prompt.showToast({
    message: this.isDeleteMode
      ? "进入批量删除模式，删除无弹窗确认"
      : "已退出批量删除",
  });
}

// 普通模式删除（弹确认）
deleteSong(songToDelete) {
  var that = this;
  prompt.showDialog({
    title: "删除歌曲",
    message: `您确定要删除《${songToDelete.name}》吗？`,
    buttons: [{ text: "取消" }, { text: "确定", color: "#FF453A" }],
    success: function () {
      that.removeFromListAndPaging(songToDelete.id);
      prompt.showToast({ message: "已移除" });
      that.persistDeletion(songToDelete.id);
    },
    cancel: function () {
      prompt.showToast({ message: "操作已取消" });
    },
  });
}

// 批量模式删除（不弹窗）
deleteSongDirect(songToDelete) {
  this.removeFromListAndPaging(songToDelete.id);
  prompt.showToast({ message: "已移除" });
  this.persistDeletion(songToDelete.id);
}

// 从列表中移除（内存操作）
removeFromListAndPaging(songId) {
  // 1) 从 fullSongList 删除
  const fullIndex = this.fullSongList.findIndex((s) => s.id === songId);
  if (fullIndex > -1) this.fullSongList.splice(fullIndex, 1);

  // 2) 从当前页 displaySongList 删除
  const displayIndex = this.displaySongList.findIndex((s) => s.id === songId);
  if (displayIndex > -1) this.displaySongList.splice(displayIndex, 1);

  // 3) 更新总页数
  this.totalPages = Math.ceil(this.fullSongList.length / this.PAGE_SIZE) || 1;

  // 4) 如果当前页空了，重新加载
  if (this.displaySongList.length === 0 && this.fullSongList.length > 0) {
    if (this.currentPage > this.totalPages)
      this.currentPage = this.totalPages;

    const startIndex = (this.currentPage - 1) * this.PAGE_SIZE;
    const endIndex = startIndex + this.PAGE_SIZE;
    this.displaySongList = this.fullSongList.slice(startIndex, endIndex);
  }

  // 5) 重新计算显示序号
  const startIndex = (this.currentPage - 1) * this.PAGE_SIZE;
  this.displaySongList = this.displaySongList.map((song, idx) => ({
    ...song,
    displayIndex: startIndex + idx + 1,
  }));
}

// 持久化删除（文件操作）
persistDeletion: async function (songId) {
  try {
    // 1) 删除音乐文件
    await fileService.delete(`${DIR_MUSIC}${songId}.mp3`);
    // 2) 删除歌词文件
    await fileService.delete(`${DIR_LYRICS}${songId}.json`);

    // 3) 从元数据中删除
    const downloaded = await fileService.readJson(FILE_DOWNLOADED_SONGS, {});
    if (downloaded[String(songId)]) {
      delete downloaded[String(songId)];
      await fileService.writeJson(FILE_DOWNLOADED_SONGS, downloaded);
    }

    // 4) 刷新存储统计
    try {
      const totalStorageData = await fileService
        ._promisify(device.getTotalStorage)
        .catch(() => ({ totalStorage: this.totalStorage }));
      const availableStorageData = await fileService
        ._promisify(device.getAvailableStorage)
        .catch(() => ({ availableStorage: this.availableStorage }));
      this.totalStorage = totalStorageData.totalStorage;
      this.availableStorage = availableStorageData.availableStorage;
    } catch (e) {}
  } catch (error) {
    console.error("持久化删除失败:", error);
    prompt.showToast({ message: "删除文件时发生错误" });
  }
}
```

#### 3.4 修复未知歌曲

```javascript
// src/pages/storage/storage.ux

fixAllUnknownSongs() {
  // 1) 筛选未知歌曲ID
  const unknownSongIds = this.fullSongList
    .filter(song => song.isUnknown)
    .map(song => song.id);

  if (unknownSongIds.length === 0) {
    prompt.showToast({ message: '没有需要修复的歌曲' });
    return;
  }

  prompt.showToast({
    message: `正在批量修复 ${unknownSongIds.length} 首歌曲...`
  });

  const idsString = unknownSongIds.join(',');

  // 2) 批量获取歌曲详情
  fetch.fetch({
    url: `${API_SONG_DETAIL}${idsString}`,
    responseType: 'text',
    success: (response) => {
      try {
        const data = JSON.parse(response.data);
        const songDetails = data?.songs;

        if (!songDetails || songDetails.length === 0)
          throw new Error("API未返回有效的歌曲信息");

        // 3) 异步处理修复
        (async () => {
          const downloaded = await fileService.readJson(
            FILE_DOWNLOADED_SONGS, {}
          );
          let fixedCount = 0;

          songDetails.forEach(songDetail => {
            const originalSong = this.fullSongList.find(
              s => s.id == songDetail.id
            );
            if (originalSong) {
              downloaded[String(songDetail.id)] = {
                id: songDetail.id,
                name: songDetail.name,
                artists: songDetail.ar.map(a => a.name).join(' / '),
                duration: Math.floor(songDetail.dt / 1000)
              };
              fixedCount++;
            }
          });

          if (fixedCount > 0) {
            const success = await fileService.writeJson(
              FILE_DOWNLOADED_SONGS, downloaded
            );
            prompt.showToast({
              message: success
                ? `成功修复 ${fixedCount} 首歌曲！`
                : '保存修复信息失败'
            });
            this.loadAndScanStorage();
          } else {
            prompt.showToast({
              message: '未能从返回数据中匹配到可修复的歌曲'
            });
          }
        })();

      } catch (e) {
        console.error("批量修复失败:", e);
        prompt.showToast({ message: '修复失败，请检查网络或API' });
      }
    },
    fail: () => prompt.showToast({ message: '网络请求失败' })
  });
}
```

#### 3.5 跳页键盘

```javascript
// src/pages/storage/storage.ux

// 打开键盘
openPageKeypad() {
  if (this.totalPages <= 1) return;
  this.showPageKeypad = true;
  this.pageInput = String(this.currentPage);
}

// 关闭键盘
closePageKeypad() {
  this.showPageKeypad = false;
  this.pageInput = "";
}

// 处理按键
handlePageKey(key) {
  if (key === "退格") {
    this.pageInput = (this.pageInput || "").slice(0, -1);
    return;
  }
  const next = (this.pageInput || "") + key;
  if (next.length > 3) return;
  // 去除前导零
  this.pageInput = next.replace(/^0+(\d)/, "$1");
}

// 确认跳转
confirmJumpPage() {
  const p = parseInt(this.pageInput, 10);
  if (!p || isNaN(p)) {
    prompt.showToast({ message: "请输入页码" });
    return;
  }
  let target = p;
  if (target < 1) target = 1;
  if (target > this.totalPages) target = this.totalPages;

  this.closePageKeypad();
  this.loadPage(target);
}

// 空操作（用于阻止事件冒泡）
noop() {}
```

#### 3.6 文件大小格式化

```javascript
// src/pages/storage/storage.ux

formatSize(bytes, forceUnit = null) {
  if (bytes < 0 || isNaN(bytes)) bytes = 0;
  if (bytes === 0) return `0 ${forceUnit || 'B'}`;

  const k = 1024;
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let i;

  if (forceUnit && units.includes(forceUnit)) {
    i = units.indexOf(forceUnit);
  } else {
    i = Math.floor(Math.log(bytes) / Math.log(k));
  }

  const value = parseFloat((bytes / Math.pow(k, i)).toFixed(1));
  return `${value} ${units[i]}`;
}
```

---

### 4. 文件服务工具

```javascript
// src/pages/storage/storage.ux

const fileService = {
  // Promise 封装
  _promisify(fn, options) {
    return new Promise((resolve, reject) => {
      fn({
        ...options,
        success: resolve,
        fail: (data, code) => reject(
          new Error(`Error code ${code}: ${data}`)
        )
      });
    });
  },

  // 读取 JSON 文件
  async readJson(uri, defaultValue = null) {
    try {
      const data = await this._promisify(file.readText, { uri });
      return JSON.parse(data.text || '{}');
    } catch (error) {
      if (error.code !== 301)
        console.error(`[FileService] readJson 失败: ${uri}`, error);
      return defaultValue;
    }
  },

  // 写入 JSON 文件
  async writeJson(uri, data) {
    try {
      await this._promisify(file.writeText, {
        uri,
        text: JSON.stringify(data), // 去掉 null,2，降低内存峰值
      });
      return true;
    } catch (error) {
      console.error(`[FileService] writeJson 失败: ${uri}`, error);
      return false;
    }
  },

  // 删除文件
  async delete(uri) {
    try {
      await this._promisify(file.delete, { uri });
      return true;
    } catch (error) {
      if (error.code !== 301)
        console.error(`[FileService] delete 失败: ${uri}`, error);
      return false;
    }
  },

  // 确保目录存在
  async ensureDirExists(uri) {
    try {
      await this._promisify(file.mkdir, { uri });
    } catch (error) {
      // 忽略目录已存在的错误
    }
  }
};
```

---

### 5. 页面模板结构

#### 5.1 整体布局

```html
<!-- src/pages/storage/storage.ux -->

<template>
  <div class="page-container">
    <!-- 1. 固定的顶部栏 -->
    <div class="header" onclick="goBack">
      <text class="time-display">{{ currentTime }}</text>
      <text class="title">‹存储管理</text>
    </div>

    <!-- 2. 独立滚动的内容区 -->
    <scroll id="songListScroll" class="scroll-wrapper"
            scroll-y="true" bounces="true">

      <!-- 存储概览 -->
      <div class="storage-overview">
        <!-- 存储文本 -->
        <text class="storage-text">
          {{ usedStorageText }} / {{ totalStorageText }}
        </text>
        <!-- 进度条 -->
        <progress class="storage-progress" type="horizontal"
                  percent="{{ usedPercent }}"></progress>

        <!-- 详细信息 -->
        <div class="storage-details">
          <div class="detail-item">
            <div class="color-box music-color"></div>
            <text class="detail-text">歌曲: {{ musicSizeText }}</text>
          </div>
          <div class="detail-item">
            <div class="color-box lyrics-color"></div>
            <text class="detail-text">歌词: {{ lyricsSizeText }}</text>
          </div>
          <div class="detail-item">
            <div class="color-box free-color"></div>
            <text class="detail-text">可用: {{ availableStorageText }}</text>
          </div>
        </div>

        <!-- 修复未知歌曲按钮 -->
        <div class="header-action-btn-wrapper"
             if="{{ hasUnknownSongs }}"
             onclick="fixAllUnknownSongs">
          <image class="header-action-btn-icon"
                 src="/common/icon/fix.png"></image>
          <text class="header-action-btn-text">修复未知歌曲</text>
        </div>

        <!-- 批量删除按钮 -->
        <div class="batch-delete-pill"
             if="{{ fullSongList.length > 0 }}"
             onclick="toggleDeleteMode">
          <image class="batch-delete-icon"
                 src="{{ isDeleteMode ? '/common/icon/check.png'
                                  : '/common/icon/delete.png' }}"></image>
          <text class="batch-delete-text">
            {{ isDeleteMode ? "完成删除" : "批量删除" }}
          </text>
        </div>
      </div>

      <!-- 歌曲列表 -->
      <div class="list-content">
        <div for="{{ (index, item) in displaySongList }}"
             class="song-item">
          <!-- 序号 -->
          <div class="item-prefix">
            <text class="item-index">{{ item.displayIndex }}</text>
          </div>

          <!-- 歌曲信息 -->
          <div class="song-info">
            <text class="song-title {{ item.isUnknown ? 'unknown-title' : '' }}">
              {{ item.name }}
            </text>
            <text class="song-artist">{{ item.artists }}</text>
            <div class="file-size-details">
              <text class="size-tag music">
                {{ formatSize(item.musicFileSize, "MB") }}
              </text>
              <text class="size-tag lyric">
                {{ formatSize(item.lyricFileSize, "KB") }}
              </text>
            </div>
          </div>

          <!-- 删除按钮 -->
          <div class="action-btn-wrapper"
               onclick="handleDeleteClick(item)">
            <image class="action-btn"
                   src="/common/icon/cancel.png"></image>
          </div>
        </div>
      </div>

      <!-- 加载/空状态提示 -->
      <text class="loading-tip" if="{{ isLoading }}">
        正在计算存储...
      </text>
      <text class="loading-tip"
            if="{{ !isLoading && fullSongList.length === 0 }}">
        没有已下载的歌曲
      </text>
    </scroll>

    <!-- 底部翻页栏 -->
    <div class="pagination-footer"
         if="{{ !isLoading && fullSongList.length > PAGE_SIZE }}">
      <text class="item-value-btn" onclick="loadPrevPage">上一页</text>
      <text class="pagination-text" onclick="openPageKeypad">
        {{ currentPage }} / {{ totalPages }}
      </text>
      <text class="item-value-btn" onclick="loadNextPage">下一页</text>
    </div>

    <!-- 跳页键盘 -->
    <div class="pagekey-overlay"
         if="{{ showPageKeypad }}"
         onclick="closePageKeypad">
      <div class="pagekey-panel" onclick="noop">
        <!-- 显示区 -->
        <div class="pagekey-display-wrapper">
          <text class="pagekey-display">{{ pageInput || "" }}</text>
        </div>

        <!-- 数字键盘 -->
        <div class="pagekey-keyboard">
          <div class="pagekey-row">
            <text class="pagekey-key" onclick="handlePageKey('1')">1</text>
            <text class="pagekey-key" onclick="handlePageKey('2')">2</text>
            <text class="pagekey-key" onclick="handlePageKey('3')">3</text>
          </div>
          <div class="pagekey-row">
            <text class="pagekey-key" onclick="handlePageKey('4')">4</text>
            <text class="pagekey-key" onclick="handlePageKey('5')">5</text>
            <text class="pagekey-key" onclick="handlePageKey('6')">6</text>
          </div>
          <div class="pagekey-row">
            <text class="pagekey-key" onclick="handlePageKey('7')">7</text>
            <text class="pagekey-key" onclick="handlePageKey('8')">8</text>
            <text class="pagekey-key" onclick="handlePageKey('9')">9</text>
          </div>
          <div class="pagekey-row">
            <text class="pagekey-key" onclick="closePageKeypad">取消</text>
            <text class="pagekey-key" onclick="handlePageKey('0')">0</text>
            <text class="pagekey-key" onclick="handlePageKey('退格')">退格</text>
          </div>
        </div>

        <!-- 确认按钮 -->
        <text class="pagekey-confirm" onclick="confirmJumpPage">跳转</text>
      </div>
    </div>
  </div>
</template>
```

#### 5.2 样式关键部分

```css
/* src/pages/storage/storage.ux */

/* 存储概览卡片 */
.storage-overview {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin: 10px 0 20px 0;
  padding: 15px;
  background-color: #2e323c;
  border-radius: 20px;
  width: 360px;
}

/* 进度条 */
.storage-progress {
  width: 100%;
  height: 12px;
  stroke-width: 12px;
  color: #bac3ff;
  background-color: #555555;
}

/* 歌曲项 */
.song-item {
  padding: 0 10px 0 0;
  width: 400px;
  height: 120px;
  margin: 5px;
  background-color: #2e323c;
  border-radius: 60px;
  justify-content: flex-start;
  align-items: center;
}

/* 批量删除胶囊 */
.batch-delete-pill {
  width: 240px;
  height: 56px;
  margin-top: 12px;
  background-color: #bac3ff;
  border-radius: 28px;
  flex-direction: row;
  justify-content: center;
  align-items: center;
}

/* 跳页键盘面板 */
.pagekey-panel {
  width: 280px;
  height: 300px;
  background-color: rgba(25, 24, 33);
  border-radius: 28px;
  flex-direction: column;
  align-items: center;
  justify-content: space-between;
  padding: 14px 0 12px 0;
  margin-bottom: 20px;
}

/* 键盘按键 */
.pagekey-key {
  width: 72px;
  height: 44px;
  background-color: rgba(255, 255, 255, 0.12);
  border-radius: 22px;
  color: #fff;
  font-size: 24px;
  font-weight: bold;
  text-align: center;
  line-height: 44px;
}

/* 方屏适配 */
@media (shape: rect) {
  .pagekey-panel {
    width: 320px;
    height: 340px;
    border-radius: 32px;
  }
  .pagekey-key {
    width: 86px;
    height: 52px;
    border-radius: 26px;
    font-size: 28px;
    line-height: 52px;
  }
}
```

---

### 6. 技术亮点总结

1. **高效存储扫描**
   - 使用 `Promise.all` 并行获取所有数据
   - 构建文件映射表，快速匹配音乐和歌词文件

2. **智能分页系统**
   - 循环翻页（最后一页下一页回到第一页）
   - 自定义数字键盘跳页
   - 删除后自动补页

3. **批量删除模式**
   - 切换模式后删除无需确认
   - 提供视觉反馈

4. **未知歌曲修复**
   - 批量查询API获取歌曲信息
   - 自动更新元数据

5. **内存优化**
   - JSON写入时去除格式化，降低内存峰值
   - 只更新变化的数据

6. **响应式布局**
   - 支持圆屏和方屏
   - 使用媒体查询适配不同屏幕

---

## 第三部分：整体架构总结

### 1. 文件结构

```
OMusic/
├── src/
│   ├── pages/
│   │   ├── player/
│   │   │   └── player.ux          # 播放器页面（歌词滚动）
│   │   └── storage/
│   │       └── storage.ux         # 存储管理页面
│   ├── utils/
│   │   └── lyric_cook.js          # 歌词解析工具
│   └── services/
│       └── api.js                 # API服务
```

### 2. 数据流向

```
歌词系统：
API → lyric_cook.js → cooked格式 → player.ux → UI显示

存储系统：
文件系统 → storage.ux → 扫描统计 → UI显示
```

### 3. 核心模块对比

| 特性 | 歌词滚动 | 存储管理 |
|------|---------|---------|
| 主要功能 | 显示和滚动歌词 | 管理本地文件 |
| 数据来源 | 网络API | 本地文件系统 |
| 渲染模式 | fullscreen/all | 分页列表 |
| 性能优化 | 64分组渲染 | Promise并行 |
| 交互方式 | 点击跳转 | 批量删除 |
| 文件操作 | 读取歌词 | 删除文件 |

---

## 附录：完整代码文件路径

1. **歌词滚动核心文件**
   - `src/utils/lyric_cook.js` - 歌词解析和转换
   - `src/pages/player/player.ux` - 播放器页面

2. **存储管理核心文件**
   - `src/pages/storage/storage.ux` - 存储管理页面

3. **文档-全.md**
   - 包含小米 Vela 平台系统接口文档
   - 上传、下载、传感器、地理位置等API

---

*文档生成日期：2026年2月3日*
