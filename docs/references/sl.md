# 氦音乐 (HeMusic) - 搜索与歌词逻辑分析

## 1. 搜索逻辑

### 1.1 搜索平台支持
氦音乐支持三个主流音乐平台的搜索：
- 腾讯音乐 (tx)
- 网易云音乐 (wy) 
- 酷狗音乐 (kg)

### 1.2 搜索实现
搜索功能实现在 `src/common/api.js` 文件中：

```javascript
// 获取用户选择的搜索平台
async function getSearchPlatform() {
  return new Promise((resolve) => {
    storage.get({
      key: 'search_platform',
      default: 'tx',
      success: (data) => resolve(data),
      fail: () => resolve('tx')
    });
  });
}

// 主搜索方法
async search(keyword, page = 1) {
  const cacheKey = `search_${keyword}_page${page}`;
  const cached = cache.url.get(cacheKey);

  // 如果缓存存在且未过期，直接返回缓存结果
  if (cached && (Date.now() - cached.timestamp) < URL_CACHE_EXPIRY) {
    return cached.results;
  }

  // 获取用户选择的搜索平台
  const platform = await getSearchPlatform();
  let results = [];
  let total = 0;

  try {
    switch (platform) {
      case 'tx':
        ({ results, total } = await searchTencent(keyword, page));
        break;
      case 'wy':
        ({ results, total } = await searchNetease(keyword, page));
        break;
      case 'kg':
        ({ results, total } = await searchKugou(keyword, page));
        break;
      default:
        ({ results, total } = await searchTencent(keyword, page));
    }

    // 缓存搜索结果
    cache.url.set(cacheKey, {
      results: { results, total, pageSize: SEARCH_PAGE_SIZE },
      timestamp: Date.now()
    });

    return { results, total, pageSize: SEARCH_PAGE_SIZE };
  } catch (e) {
    console.error('搜索失败:', e);
    return { results: [], total: 0, pageSize: SEARCH_PAGE_SIZE };
  }
}
```

### 1.3 各平台搜索实现

#### 腾讯音乐搜索
```javascript
// 腾讯音乐搜索
async function searchTencent(keyword, page = 1) {
  return new Promise((resolve) => {
    fetch.fetch({
      url: `https://c.y.qq.com/soso/fcgi-bin/client_search_cp?aggr=1&cr=1&p=${page}&n=${SEARCH_PAGE_SIZE}&w=${encodeURIComponent(keyword)}&format=json`,
      header: { 'Referer': 'https://y.qq.com/' },
      success: (res) => {
        const data = safeParseData(res ? res.data : null);
        const list =
          data?.data?.song?.list ||
          data?.song?.list ||
          [];
        if (list && list.length > 0) {
          const results = list.map(song => ({
            source: 'tx',
            id: song.songmid,
            title: song.songname,
            artist: song.singer ? song.singer.map(s => s.name).join(' / ') : ''
          }));
          const total =
            data?.data?.song?.totalnum ||
            data?.data?.song?.total ||
            data?.song?.totalnum ||
            data?.song?.total ||
            0;
          resolve({ results, total });
        } else {
          resolve({ results: [], total: 0 });
        }
      },
      fail: (err) => {
        console.error('腾讯音乐搜索请求失败:', err);
        resolve({ results: [], total: 0 });
      }
    });
  });
}
```

#### 网易云音乐搜索
```javascript
// 网易云音乐搜索
async function searchNetease(keyword, page = 1) {
  return new Promise((resolve) => {
    const offset = (Math.max(1, page) - 1) * SEARCH_PAGE_SIZE;
    fetch.fetch({
      url: `http://iwenwiki.com:3000/search?keywords=${encodeURIComponent(keyword)}&limit=${SEARCH_PAGE_SIZE}&offset=${offset}`,
      success: (res) => {
        const data = safeParseData(res ? res.data : null);
        const list = data?.result?.songs || [];
        if (list && list.length > 0) {
          const results = list.map(song => ({
            source: 'wy',
            id: song.id,
            title: song.name,
            artist: song.artists ? song.artists.map(a => a.name).join(' / ') : ''
          }));
          const total = data?.result?.songCount || 0;
          resolve({ results, total });
        } else {
          resolve({ results: [], total: 0 });
        }
      },
      fail: (err) => {
        console.error('网易云音乐搜索请求失败:', err);
        resolve({ results: [], total: 0 });
      }
    });
  });
}
```

#### 酷狗音乐搜索
```javascript
// 酷狗音乐搜索
async function searchKugou(keyword, page = 1) {
  return new Promise((resolve) => {
    fetch.fetch({
      url: `http://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword=${encodeURIComponent(keyword)}&page=${Math.max(1, page)}&pagesize=${SEARCH_PAGE_SIZE}`,
      header: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
        'Referer': 'http://kugou.com/'
      },
      success: (res) => {
        const data = safeParseData(res ? res.data : null);
        const list = data?.data?.info || [];
        if (list && list.length > 0) {
          const results = list.map(song => ({
            source: 'kg',
            id: song.hash,
            title: song.songname,
            artist: song.singername
          }));
          const total = data?.data?.total || 0;
          resolve({ results, total });
        } else {
          resolve({ results: [], total: 0 });
        }
      },
      fail: (err) => {
        console.error('酷狗音乐搜索请求失败:', err);
        resolve({ results: [], total: 0 });
      }
    });
  });
}
```

### 1.4 搜索页面 UI 逻辑
搜索页面实现了自定义输入法，针对穿戴设备优化：

```javascript
// 搜索执行逻辑
async doSearch() {
  if (this.isLoading || !this.keyword) return;
  vibrateShort(); this.hideKeyboard();
  this.isLoading = true; this.hasSearched = true; this.resultList = [];
  this.currentPage = 1; // 固定仅一页
  try {
    const response = await api.search(this.keyword, this.currentPage);
    const results = response && response.results ? response.results : [];
    if (results && results.length > 0) {
        this.resultList = results.map(item => ({ ...item, isDownloaded: !!this.localMap[`${item.source}_${item.id}`] }));
    }
  } catch (e) { prompt.showToast({ message: '搜索超时' }); } finally { this.isLoading = false; }
}
```

### 1.5 搜索逻辑完善版（建议实现）
为提升命中率与稳定性，建议在现有逻辑基础上补充如下策略：

1. **关键词规范化**
   - 去首尾空格、连续空格合并、全角转半角、大小写归一。
2. **平台优先级 + 回退**
   - 默认使用用户选择平台；若结果为空或请求失败，按 `tx -> wy -> kg` 顺序回退。
3. **并发搜索（可选）**
   - 若用户开启“多平台搜索”，可并发请求多平台，合并去重结果。
4. **结果去重与排序**
   - 去重键：`source + id`；排序优先级：本地已下载 > 选择平台 > 其他平台。
5. **缓存键拆分**
   - 缓存 key 建议包含平台：`search_${platform}_${keyword}_page${page}`，避免跨平台污染。
6. **节流与超时**
   - 每次搜索间隔最少 300ms；单次请求超时 4~6s，失败不阻塞 UI。

**完善版搜索伪代码**
```javascript
async function searchEnhanced(keyword, page = 1) {
  const kw = normalize(keyword)
  const platform = await getSearchPlatform()
  const platforms = [platform, 'tx', 'wy', 'kg'].filter(unique)

  for (const p of platforms) {
    const cacheKey = `search_${p}_${kw}_page${page}`
    const cached = cache.url.get(cacheKey)
    if (cached && !isExpired(cached)) return cached.results

    const resp = await safeSearchByPlatform(p, kw, page)
    if (resp && resp.results && resp.results.length) {
      cache.url.set(cacheKey, { results: resp, timestamp: Date.now() })
      return resp
    }
  }
  return { results: [], total: 0, pageSize: SEARCH_PAGE_SIZE }
}
```

## 2. 歌词逻辑

### 2.1 歌词获取流程
歌词获取遵循以下优先级：
1. 本地歌词文件 (lrcPath)
2. 缓存歌词
3. 远程歌词 API
4. 直连各平台歌词接口

### 2.2 歌词获取实现
歌词获取实现在 `src/common/lyric.js` 文件中：

```javascript
export default {
  async getLyric(song) {
    if (!song || !song.id || !song.source) return ""

    if (song.lrcPath) {
      const lrcText = await readFile(song.lrcPath)
      if (lrcText) {
        setCachedLyric(song, lrcText)
        return lrcText
      }
    }

    const cached = await getCachedLyric(song)
    if (cached) return cached

    const remote = await api.getLyric(song.source, song.id)
    if (remote) {
      setCachedLyric(song, remote)
      return remote
    }

    return ""
  }
}
```

### 2.3 远程歌词获取
远程歌词获取实现在 `src/common/api.js` 文件中：

```javascript
async function getLyricDirect(source, id) {
  console.debug(`[API] Trying direct lyric fetch for ${source}:${id}`);
  return new Promise((resolve) => {
    if (source === 'tx') {
      // 腾讯音乐直连
      const url = `https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=${id}&format=json&nobase64=1`;
      fetch.fetch({
        url: url,
        header: {
          'Referer': 'https://y.qq.com/portal/player.html',
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
        },
        success: (res) => {
          try {
            // 腾讯返回的是 JSONP 格式或者不标准的 JSON，需要处理
            let dataStr = typeof res.data === 'string' ? res.data : JSON.stringify(res.data);
            // 有时候返回 MusicJsonCallback(...)
            if (dataStr.startsWith('MusicJsonCallback(')) {
              dataStr = dataStr.slice(18, -1);
            }
            const data = JSON.parse(dataStr);
            if (data && data.lyric) {
              console.debug('[API] Direct lyric fetch success (tx)');
              // 解码 HTML 实体
              let lyric = data.lyric.replace(/&#(\d+);/g, (match, dec) => String.fromCharCode(dec));
              resolve(lyric);
            } else {
              resolve('');
            }
          } catch (e) {
            console.error('[API] Direct lyric parse failed:', e);
            resolve('');
          }
        },
        fail: () => resolve('')
      });
    } else if (source === 'wy') {
      // 网易云直连
      const url = `http://iwenwiki.com:3000/lyric?id=${id}`;
      fetch.fetch({
        url: url,
        success: (res) => {
          try {
            const data = typeof res.data === 'string' ? JSON.parse(res.data) : res.data;
            if (data && data.lrc && data.lrc.lyric) {
              console.debug('[API] Direct lyric fetch success (wy)');
              resolve(data.lrc.lyric);
            } else {
              resolve('');
            }
          } catch (e) {
            resolve('');
          }
        },
        fail: () => resolve('')
      });
    } else if (source === 'kg') {
      const header = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
      };
      const searchUrl = `http://krcs.kugou.com/search?ver=1&man=yes&client=mobi&keyword=&duration=&hash=${id}&album_audio_id=`;
      fetchJson(searchUrl, header).then((searchBody) => {
        if (!searchBody || !searchBody.candidates || searchBody.candidates.length === 0) {
          resolve('');
          return;
        }
        const candidate = searchBody.candidates[0];
        const lyricId = candidate.id;
        const accesskey = candidate.accesskey;
        if (!lyricId || !accesskey) {
          resolve('');
          return;
        }

        const downloadUrl = `http://lyrics.kugou.com/download?ver=1&client=pc&id=${lyricId}&accesskey=${accesskey}&fmt=lrc&charset=utf8`;
        fetchJson(downloadUrl, header).then((downloadBody) => {
          if (!downloadBody || !downloadBody.content) {
            resolve('');
            return;
          }
          const lrcText = base64Decode(downloadBody.content);
          resolve(lrcText || '');
        });
      });
    } else {
      // 其他源暂不支持直连
      resolve('');
    }
  });
}
```

### 2.4 歌词解析器
歌词解析器实现在 `src/common/lrc-parser.js` 文件中，支持LRC格式解析：

```javascript
/**
 * 解析时间字符串为秒数
 * 支持格式：00:00.00, 00:00, 00:00:00.00
 * @param {string} timeStr 时间字符串
 * @returns {number|null} 时间戳（秒），解析失败返回null
 */
function parseTimeString(timeStr) {
  if (!timeStr || typeof timeStr !== 'string') {
    return null;
  }

  try {
    const parts = timeStr.split(':');
    if (parts.length < 2 || parts.length > 3) {
      return null;
    }

    let totalSeconds = 0;

    if (parts.length === 2) {
      // MM:SS.ss
      const min = parseFloat(parts[0]);
      const sec = parseFloat(parts[1]);
      if (isNaN(min) || isNaN(sec)) return null;
      totalSeconds = min * 60 + sec;
    } else if (parts.length === 3) {
      // HH:MM:SS.ss
      const hour = parseFloat(parts[0]);
      const min = parseFloat(parts[1]);
      const sec = parseFloat(parts[2]);
      if (isNaN(hour) || isNaN(min) || isNaN(sec)) return null;
      totalSeconds = hour * 3600 + min * 60 + sec;
    }

    // 检查是否为有效时间
    if (totalSeconds < 0 || !isFinite(totalSeconds)) {
      console.warn('[lrc-parser] Invalid timestamp:', timeStr);
      return null;
    }

    return totalSeconds;
  } catch (e) {
    console.error('[lrc-parser] Failed to parse time string:', timeStr, e);
    return null;
  }
}

export default function parseLrc(lrcText) {
  if (!lrcText || typeof lrcText !== 'string') {
    console.error('[lrc-parser] Invalid input: expected string');
    return {};
  }

  try {
    // 核心修复：使用正则拆分，兼容 \r\n, \r, \n
    const lines = lrcText.split(/\r\n|\r|\n/);
    const result = {};
    let offsetSeconds = 0;

    // 过滤掉歌曲信息行的关键词
    const metadataKeywords = ['词：', '曲：', '编曲：', 'ti:', 'ar:', 'al:', 'by:'];

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      if (!line) continue;
      const offsetMatch = line.match(/^\s*\[offset:([+-]?\d+)\]/i);
      if (offsetMatch) {
        const offsetMs = parseInt(offsetMatch[1], 10);
        if (!isNaN(offsetMs)) offsetSeconds = offsetMs / 1000;
        continue;
      }

      // 解析一行歌词
      const lyrics = parseLyricLine(line, metadataKeywords);

      // 将解析结果添加到结果对象
      for (const lyric of lyrics) {
        let timestamp = lyric.timestamp + offsetSeconds;
        if (!isFinite(timestamp)) continue;
        if (timestamp < 0) timestamp = 0;
        const rounded = Math.round(timestamp * 1000) / 1000; // 保留三位小数
        const timestampKey = rounded.toString();
        // 如果同一时间戳有多行歌词，只保留第一行
        if (!result[timestampKey]) {
          result[timestampKey] = lyric.text;
        }
      }
    }

    return result;
  } catch (e) {
    console.error('[lrc-parser] Failed to parse lyrics:', e);
    return {};
  }
}
```

### 2.5 歌词同步显示
播放器页面实现了歌词同步显示功能：

```javascript
// 更新歌词显示
updateLyric() {
  // 如果暂停中，不更新歌词
  if (!this.isPlaying) {
    return;
  }

  if (this.lyricsAll.length === 0) {
    return;
  }

  if (this.isDragging) {
    return;
  }

  let now = audio.currentTime + this.lyricOffsetSec;
  if (!isFinite(now)) {
    now = this.currentTime;
  }
  if (now < 0) {
    now = 0;
  }

  let newIndex = -1;

  // 使用二分查找找到当前歌词
  let left = 0;
  let right = this.lyricsAll.length - 1;

  while (left <= right) {
    const mid = Math.floor((left + right) / 2);
    if (this.lyricsAll[mid].time <= now) {
      newIndex = mid;
      left = mid + 1;
    } else {
      right = mid - 1;
    }
  }

  if (newIndex < 0) {
    return;
  }

  // 只在歌词行变化时更新
  if (newIndex !== this.activeIndex) {
    this.activeIndex = newIndex;
    this.activeLine = newIndex;
    this.scrollLyricToActive();
  }
}
```

### 2.6 歌词获取逻辑完善版（建议实现）
为了提高歌词命中率与稳定性，建议对现有流程做如下增强：

1. **优先级改为：本地 > 缓存 > 平台直连 > 平台兜底搜索**
   - 平台直连失败时，尝试“歌词搜索接口”获取候选再下载。
2. **统一返回结构**
   - 统一为 `{ lyric, tlyric, rlyric, lxlyric }`，便于后续展示与逐字解析。
3. **兼容歌词解码**
   - 处理 JSONP、HTML 实体、Base64、gzip 压缩返回。
4. **平台兜底搜索（建议）**
   - 腾讯：songmid 搜索失败时，以 `keyword + artist` 二次搜索取首条。
   - 酷狗：hash 对应候选为空时，尝试 `keyword + duration` 搜索。
5. **缓存 key 优化**
   - `lyric_${source}_${id}`，避免跨源污染。

**完善版歌词伪代码**
```javascript
async function getLyricEnhanced(song) {
  if (!song || !song.id || !song.source) return emptyLyric()

  // 1) 本地
  if (song.lrcPath) {
    const lrc = await readFile(song.lrcPath)
    if (lrc) return normalizeLyric(lrc)
  }

  // 2) 缓存
  const cached = await getCachedLyric(song)
  if (cached) return cached

  // 3) 平台直连
  let lyric = await getLyricDirect(song.source, song.id)
  if (lyric) return setCachedLyric(song, normalizeLyric(lyric))

  // 4) 平台兜底搜索 + 下载
  lyric = await searchAndFetchLyric(song)
  if (lyric) return setCachedLyric(song, normalizeLyric(lyric))

  return emptyLyric()
}
```

**建议的统一结构**
```javascript
function normalizeLyric(raw) {
  return {
    lyric: raw || '',
    tlyric: null,
    rlyric: null,
    lxlyric: null
  }
}
```

## 3. 缓存机制

### 3.1 LRU缓存实现
项目使用LRU缓存来优化性能：

```javascript
class LRUCache {
  constructor(maxSize = 100) {
    this.maxSize = maxSize;
    this.cache = new Map();
  }

  get(key) {
    if (!this.cache.has(key)) {
      return undefined;
    }
    // 重新设置以更新访问顺序（移到最后）
    const value = this.cache.get(key);
    this.cache.delete(key);
    this.cache.set(key, value);
    return value;
  }

  set(key, value) {
    // 如果已存在，先删除旧的
    if (this.cache.has(key)) {
      this.cache.delete(key);
    }
    // 如果超过大小限制，删除最旧的（第一个）
    else if (this.cache.size >= this.maxSize) {
      const firstKey = this.cache.keys().next().value;
      this.cache.delete(firstKey);
    }
    // 添加新的
    this.cache.set(key, value);
  }

  clear() {
    this.cache.clear();
  }

  get size() {
    return this.cache.size;
  }
}

// 缓存机制：减少重复网络请求，限制大小为100条
const cache = {
  url: new LRUCache(100),
  lyric: new LRUCache(100)
};
```

### 3.2 缓存过期时间
- URL缓存过期时间：5分钟 (`URL_CACHE_EXPIRY = 5 * 60 * 1000`)
- 歌词缓存过期时间：5分钟 (`LYRIC_CACHE_EXPIRY = 5 * 60 * 1000`)

## 4. 播放控制与歌词联动
播放器页面将搜索结果、播放控制和歌词显示整合在一起：

```javascript
// 播放在线音乐
async playOnline(item) {
  vibrateShort();
  if (item.isDownloaded) {
      const list = await db.getList();
      const localSong = list.find(s => s.id === item.id && s.source === item.source);
      if (localSong) {
          this.$app.$def.playMusic(localSong, [localSong]);
          router.push({ uri: '/pages/player' });
          return;
      }
  }

  const appDef = (this.$app && this.$app.$def) ? this.$app.$def : null;
  if (!appDef) return;

  if (appDef.data.isProcessing) {
      prompt.showToast({ message: '请等待当前任务完成' });
      return;
  }

  appDef.data.isProcessing = true;
  prompt.showToast({ message: '解析地址...' });

  try {
      const url = await api.getMusicUrl(item.source, item.id);
      if (url) {
          appDef.playMusic({ ...item, url: url }, [{...item, url: url}]);
          router.push({ uri: '/pages/player' });
      } else {
          prompt.showToast({ message: '解析失败' });
      }
  } finally {
      appDef.data.isProcessing = false;
  }
}
```
