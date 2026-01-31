const express = require('express');
const bodyParser = require('body-parser');
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const needle = require('needle');
const crypto = require('crypto');
const zlib = require('zlib');
const multer = require('multer');
const config = require('./config');
const { requireOnlineAccess, sendError } = require('./middleware/auth');
const { rateLimit, rateLimitAdmin } = require('./middleware/rateLimit');
const packageInfo = require('./package.json');

const app = express();
const PORT = config.PORT;
const SCRIPTS_DIR = path.join(__dirname, 'scripts');
const startTime = Date.now();

// --- 核心配置 ---
// 你的 Cloudflare Worker 代理地址
const CFW_PROXY_URL = config.CFW_PROXY_URL;

// --- Multer Config (文件上传) ---
const storage = multer.diskStorage({
    destination: function (req, file, cb) {
        // 建议：实际生产环境最好先存临时目录验证，这里保持原样
        cb(null, SCRIPTS_DIR);
    },
    filename: function (req, file, cb) {
        // 防止路径穿越，保留原文件名
        cb(null, path.basename(file.originalname));
    }
});
const upload = multer({ storage: storage });

// --- Log Capture System (日志系统优化版) ---
const logs = [];
const MAX_LOGS = 500;
const originalConsole = {
    log: console.log,
    error: console.error,
    warn: console.warn,
    info: console.info
};

function captureLog(type, args) {
    const msg = args.map(a => {
        const str = (typeof a === 'object' ? JSON.stringify(a) : String(a));
        // 优化：截断过长的日志，防止大包体导致内存泄漏
        if (str.length > 2000) return str.substring(0, 2000) + '...[truncated]';
        return str;
    }).join(' ');
    
    const timestamp = new Date().toISOString();
    logs.unshift({ time: timestamp, type, msg }); // 添加到头部
    if (logs.length > MAX_LOGS) logs.pop();
    originalConsole[type].apply(console, [`[${timestamp}]`, ...args]);
}

console.log = (...args) => captureLog('log', args);
console.error = (...args) => captureLog('error', args);
console.warn = (...args) => captureLog('warn', args);
console.info = (...args) => captureLog('info', args);

// --- Global Utils ---
const globalPolyfills = {
    btoa: (str) => Buffer.from(str, 'binary').toString('base64'),
    atob: (b64Encoded) => Buffer.from(b64Encoded, 'base64').toString('binary'),
    console: {
        log: (...args) => console.log('[Script]', ...args),
        error: (...args) => console.error('[Script Err]', ...args),
        warn: (...args) => console.warn('[Script Warn]', ...args),
        info: (...args) => console.info('[Script Info]', ...args),
        group: () => {},
        groupEnd: () => {},
    }
};

// --- Source Manager ---
const sourceMap = {}; // { 'kw': [ { scriptId, handler, priority } ] }
const sourceIndex = {}; // { 'kw': 0 } -> Round Robin Index
const loadedScripts = {}; // { scriptId: { context, info } }

// --- Cache System ---
const CACHE_FILE = path.join(__dirname, 'cache.json');
const urlCache = new Map(); // Key: "source:id:quality", Value: { url, provider, expireAt }
const CACHE_DURATION = 3 * 60 * 60 * 1000; // 3小时缓存

// 从文件加载缓存
function loadCacheFromFile() {
    try {
        if (fs.existsSync(CACHE_FILE)) {
            const data = fs.readFileSync(CACHE_FILE, 'utf8');
            const cacheList = JSON.parse(data);
            const now = Date.now();
            
            for (const item of cacheList) {
                if (item.expireAt > now) {
                    urlCache.set(item.key, {
                        data: item.data,
                        provider: item.provider,
                        expireAt: item.expireAt
                    });
                }
            }
            console.log(`[Cache] Loaded ${urlCache.size} valid entries from file`);
        }
    } catch (e) {
        console.error('[Cache] Failed to load from file:', e.message);
    }
}

// 保存缓存到文件
function saveCacheToFile() {
    try {
        const cacheList = [];
        for (const [key, val] of urlCache.entries()) {
            cacheList.push({
                key,
                data: val.data,
                provider: val.provider,
                expireAt: val.expireAt
            });
        }
        fs.writeFileSync(CACHE_FILE, JSON.stringify(cacheList, null, 2));
        console.log(`[Cache] Saved ${cacheList.length} entries to file`);
    } catch (e) {
        console.error('[Cache] Failed to save to file:', e.message);
    }
}

// 定期保存 (每5分钟)
setInterval(saveCacheToFile, 5 * 60 * 1000);

// 定期清理 (每10分钟)
setInterval(() => {
    const now = Date.now();
    let cleaned = 0;
    for (const [key, value] of urlCache.entries()) {
        if (value.expireAt <= now) {
            urlCache.delete(key);
            cleaned++;
        }
    }
    if (cleaned > 0) {
        console.log(`[Cache] Cleaned ${cleaned} expired entries.`);
        saveCacheToFile();
    }
}, 10 * 60 * 1000);

function loadScripts() {
    console.log(`Scanning scripts in ${SCRIPTS_DIR}...`);
    Object.keys(sourceMap).forEach(k => delete sourceMap[k]);
    Object.keys(sourceIndex).forEach(k => delete sourceIndex[k]);
    Object.keys(loadedScripts).forEach(k => delete loadedScripts[k]);

    if (!fs.existsSync(SCRIPTS_DIR)) {
        console.error("Scripts directory not found!");
        // 如果目录不存在，尝试创建
        try { fs.mkdirSync(SCRIPTS_DIR); } catch(e) {}
        return;
    }

    const files = fs.readdirSync(SCRIPTS_DIR).filter(f => f.endsWith('.js'));
    files.forEach(file => {
        loadScript(path.join(SCRIPTS_DIR, file), file);
    });
}

function loadScript(filePath, fileName) {
    try {
        const content = fs.readFileSync(filePath, 'utf8');
        const scriptId = fileName;

        console.log(`Loading ${fileName}...`);
        
        const context = vm.createContext({
            ...globalPolyfills,
            window: {},
            setTimeout, clearTimeout, setInterval, clearInterval,
            Buffer,
            URL, 
            URLSearchParams,
            lx: createLxObject(scriptId, fileName, content)
        });
        
        context.window = context;
        context.global = context;
        context.globalThis = context;
        context.self = context;

        try {
            vm.runInContext(content, context, { filename: fileName });
            loadedScripts[scriptId] = { context, fileName };
        } catch (e) {
            console.error(`  -> Execution Error in ${fileName}: ${e.message}`);
        }

    } catch (e) {
        console.error(`  -> Read Error for ${fileName}: ${e.message}`);
    }
}

function createLxObject(scriptId, fileName, rawScript) {
    const EVENT_NAMES = {
        request: 'request',
        inited: 'inited',
        updateAlert: 'updateAlert',
    };
    const eventNames = Object.values(EVENT_NAMES);
    
    const state = {
        inited: false,
        events: {
            request: null, 
        },
        info: null 
    };

    return {
        EVENT_NAMES,
        version: '2.8.0', 
        env: 'desktop',
        currentScriptInfo: {
            name: fileName,
            description: 'Loaded by LX Middleware (Proxy Enabled)',
            version: '1.0.0',
            author: 'Unknown',
            homepage: '',
            rawScript: rawScript,
        },

        on(eventName, handler) {
            if (!eventNames.includes(eventName)) return Promise.reject(new Error('The event is not supported: ' + eventName));
            switch (eventName) {
                case EVENT_NAMES.request:
                    state.events.request = handler;
                    break;
                default: 
                    return Promise.reject(new Error('The event is not supported: ' + eventName));
            }
            return Promise.resolve();
        },

        send(eventName, data) {
            return new Promise((resolve, reject) => {
                if (!eventNames.includes(eventName)) return reject(new Error('The event is not supported: ' + eventName));
                switch (eventName) {
                    case EVENT_NAMES.inited:
                        if (state.inited) return reject(new Error('Script is inited'));
                        state.inited = true;
                        handleInited(scriptId, state, data);
                        resolve();
                        break;
                    case EVENT_NAMES.updateAlert:
                        console.log(`[${fileName}] Update Alert:`, data);
                        resolve();
                        break;
                    default:
                        reject(new Error('Unknown event name: ' + eventName));
                }
            });
        },

        // --- 核心修改：Request 强制走 CFW 代理 ---
        request(url, { method = 'get', timeout, headers, body, form, formData }, callback) {
            
            // 1. 构造 headers
            // 确保 User-Agent 存在，否则某些平台会拒绝
            if (!headers) headers = {};
            const hasUA = Object.keys(headers).some(k => k.toLowerCase() === 'user-agent');
            if (!hasUA) headers['User-Agent'] = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) lx-music-desktop/2.8.0 Chrome/114.0.5735.289 Electron/25.9.8 Safari/537.36';

            // 2. 构造代理 URL
            // 将原始 URL 作为参数传递给 CFW
            // 注意：encodeURIComponent 是必须的，否则参数会错乱
            const proxyUrl = `${CFW_PROXY_URL}?u=${encodeURIComponent(url)}`;
            
            // 如果你想在 CFW 加密码验证，可以在这里加 headers['x-proxy-auth'] = 'your_password'

            let options = {
                headers, // 这些 headers 会被 CFW 转发给目标服务器 (Referer, Cookie 等)
                follow_max: 5,
                open_timeout: 10000,
                read_timeout: 30000
            };

            if (timeout && typeof timeout === 'number') {
                options.response_timeout = timeout;
                options.read_timeout = timeout;
            }

            // 处理 Body 数据
            let data;
            if (body) {
                data = body;
            } else if (form) {
                data = form;
                options.json = false;
            } else if (formData) {
                data = formData;
                options.json = false;
            }

            // 3. 发起请求 (请求的是 proxyUrl)
            const req = needle.request(method, proxyUrl, data, options, (err, resp, respBody) => {
                try {
                    if (err) {
                        callback(err, null, null);
                    }
                    else {
                        // CFW 应该返回原始的 Body
                        let finalBody = respBody;
                        callback(null, {
                            statusCode: resp.statusCode,
                            statusMessage: resp.statusMessage,
                            headers: resp.headers,
                            bytes: resp.bytes,
                            raw: resp.raw, 
                            body: finalBody,
                        }, finalBody);
                    }
                }
                catch (err) {
                     console.error(`[${fileName}] Req Callback Error: ${err.message}`);
                }
            });

            return () => {
                if (req && !req.aborted) req.abort();
            };
        },

        utils: {
            crypto: {
                aesEncrypt(buffer, mode, key, iv) {
                    const cipher = crypto.createCipheriv(mode, key, iv);
                    return Buffer.concat([cipher.update(buffer), cipher.final()]);
                },
                rsaEncrypt(buffer, key) {
                    buffer = Buffer.isBuffer(buffer) ? buffer : Buffer.from(buffer);
                    const targetLength = 128; 
                    if (buffer.length < targetLength) {
                        const padding = Buffer.alloc(targetLength - buffer.length);
                        buffer = Buffer.concat([padding, buffer]);
                    }
                    return crypto.publicEncrypt({ key, padding: crypto.constants.RSA_NO_PADDING }, buffer);
                },
                randomBytes(size) {
                    return crypto.randomBytes(size);
                },
                md5(str) {
                    return crypto.createHash('md5').update(str).digest('hex');
                },
            },
            buffer: {
                from(...args) {
                    return Buffer.from(...args);
                },
                bufToString(buf, format) {
                    return Buffer.from(buf, 'binary').toString(format);
                },
            },
            zlib: {
                inflate(buf) {
                    return new Promise((resolve, reject) => {
                        zlib.inflate(buf, (err, data) => {
                            if (err) reject(new Error(err.message));
                            else resolve(data);
                        });
                    });
                },
                deflate(data) {
                    return new Promise((resolve, reject) => {
                        zlib.deflate(data, (err, buf) => {
                            if (err) reject(new Error(err.message));
                            else resolve(buf);
                        });
                    });
                },
            },
        },
    };
}

function handleInited(scriptId, state, data) {
    if (!data || !data.sources) return;
    state.info = data;
    const sources = Object.keys(data.sources);

    console.log(`  -> ${scriptId} inited. Supports: ${sources.join(', ')}`);

    sources.forEach(source => {
        if (!sourceMap[source]) sourceMap[source] = [];
        sourceMap[source].push({
            scriptId,
            handler: state.events.request,
            qualitys: data.sources[source].qualitys,
            actions: data.sources[source].actions || []
        });
    });
}

// --- API Routes ---

app.use(bodyParser.json());
const PUBLIC_DIR = path.join(__dirname, 'public');
console.log(`[Server] Serving static files from: ${PUBLIC_DIR}`);
app.use(express.static(PUBLIC_DIR)); 

// Public health/version endpoints
app.get('/health', (req, res) => {
    res.json({ status: 'ok', uptime: Math.floor((Date.now() - startTime) / 1000) });
});

app.get('/version', (req, res) => {
    res.json({ name: packageInfo.name, version: packageInfo.version });
});

// --- Authentication Middleware ---
const ADMIN_PASSWORD = config.ADMIN_PASSWORD;
const ADMIN_ALLOWED_IPS = config.ADMIN_ALLOWED_IPS
    .split(',')
    .map(ip => ip.trim())
    .filter(Boolean);

const normalizeIp = (ip) => {
    if (!ip) return '';
    return ip.startsWith('::ffff:') ? ip.slice(7) : ip;
};

const isPrivateIp = (rawIp) => {
    const ip = normalizeIp(rawIp);
    if (ip === '127.0.0.1' || ip === '::1') return true;
    const parts = ip.split('.').map(n => Number.parseInt(n, 10));
    if (parts.length !== 4 || parts.some(n => Number.isNaN(n))) return false;
    if (parts[0] === 10) return true;
    if (parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31) return true;
    if (parts[0] === 192 && parts[1] === 168) return true;
    return false;
};

const adminAccess = (req, res, next) => {
    if (config.ADMIN_ALLOW_ALL) return next();
    const forwarded = req.headers['x-forwarded-for'];
    const ip = normalizeIp(forwarded ? forwarded.split(',')[0].trim() : req.ip);
    if (ADMIN_ALLOWED_IPS.includes(ip)) return next();
    if (config.ADMIN_ALLOW_PRIVATE_ONLY && isPrivateIp(ip)) return next();
    return sendError(req, res, 403, 'Forbidden');
};

const authMiddleware = (req, res, next) => {
    const auth = req.headers['x-auth'];
    if (auth === ADMIN_PASSWORD) {
        next();
    } else {
        sendError(req, res, 401, 'Unauthorized');
    }
};

if (config.ADMIN_ENABLED) {
    // Protect all /admin routes
    app.use('/admin', rateLimitAdmin, adminAccess, authMiddleware);

    // Admin APIs
    app.get('/admin/stats', (req, res) => {
        const uptime = Math.floor((Date.now() - startTime) / 1000);
        const memory = process.memoryUsage();
        res.json({
            uptime: `${Math.floor(uptime / 3600)}h ${Math.floor((uptime % 3600) / 60)}m ${uptime % 60}s`,
            cacheSize: urlCache.size,
            logCount: logs.length,
            memory: `${Math.round(memory.rss / 1024 / 1024)} MB`,
            rotation: sourceIndex // Expose rotation state
        });
    });

    app.get('/admin/logs', (req, res) => {
        res.json(logs);
    });

    app.get('/admin/cache', (req, res) => {
        const list = [];
        for (const [key, val] of urlCache.entries()) {
            const urlVal = (typeof val.data === 'string') ? val.data : JSON.stringify(val.data);
            list.push({ 
                key, 
                ...val, 
                url: urlVal, // For display in frontend
                ttl: Math.max(0, Math.floor((val.expireAt - Date.now()) / 1000)) 
            });
        }
        res.json(list);
    });

    app.delete('/admin/cache', (req, res) => {
        const size = urlCache.size;
        urlCache.clear();
        // 删除缓存文件
        try {
            if (fs.existsSync(CACHE_FILE)) {
                fs.unlinkSync(CACHE_FILE);
            }
        } catch (e) {}
        console.log(`[Admin] Cache cleared (removed ${size} entries).`);
        res.json({ success: true, count: size });
    });

    app.delete('/admin/cache/:key', (req, res) => {
        let key = req.params.key;
        if (!urlCache.has(key)) {
            try {
                const decoded = Buffer.from(key, 'base64').toString('utf8');
                if (urlCache.has(decoded)) key = decoded;
            } catch(e) {}
        }
        
        if (urlCache.has(key)) {
            urlCache.delete(key);
            saveCacheToFile(); // 保存到文件
            console.log(`[Admin] Deleted cache key: ${key}`);
            res.json({ success: true });
        } else {
            sendError(req, res, 404, 'Key not found');
        }
    });

    // 随机播放已缓存的音乐（管理端）
    app.get('/admin/cache/random', (req, res) => {
        const entry = getRandomCachedMusic();
        if (!entry) {
            return sendError(req, res, 404, 'No cached music found');
        }
        res.json(entry);
    });
} else {
    console.log('[Admin] Disabled all /admin routes.');
}

// 公开随机播放接口（客户端用）
app.get('/random', rateLimit, requireOnlineAccess, (req, res) => {
    const entry = getRandomCachedMusic();
    if (!entry) {
        return sendError(res, 404, 'No cached music found');
    }
    // 返回简单格式，直接重定向或返回 mp3 url
    res.redirect(entry.url);
});

function getRandomCachedMusic() {
    if (urlCache.size === 0) return null;

    const musicCacheEntries = [];
    for (const [key, val] of urlCache.entries()) {
        if (key.includes(':musicUrl:') && typeof val.data === 'string' && val.data.startsWith('http')) {
            musicCacheEntries.push({
                key,
                url: val.data,
                ttl: Math.max(0, Math.floor((val.expireAt - Date.now()) / 1000))
            });
        }
    }

    if (musicCacheEntries.length === 0) return null;
    return musicCacheEntries[Math.floor(Math.random() * musicCacheEntries.length)];
}

// Script Management APIs
app.get('/admin/scripts', (req, res) => {
    const scripts = [];
    for (const [id, data] of Object.entries(loadedScripts)) {
        scripts.push({
            id: id,
            name: data.info?.currentScriptInfo?.name || id,
            version: data.info?.currentScriptInfo?.version || 'Unknown',
            author: data.info?.currentScriptInfo?.author || 'Unknown'
        });
    }
    res.json(scripts);
});

app.post('/admin/scripts', upload.single('file'), (req, res) => {
    if (!req.file) return sendError(res, 400, 'No file uploaded');
    console.log(`[Admin] Uploaded script: ${req.file.filename}`);
    loadScripts(); // Reload all scripts
    res.json({ success: true });
});

app.delete('/admin/scripts/:filename', (req, res) => {
    const filename = req.params.filename;
    const filePath = path.join(SCRIPTS_DIR, filename);
    
    // Safety check: ensure filename is just a filename, not a path
    if (filename.includes('/') || filename.includes('\\')) {
        return sendError(res, 400, 'Invalid filename');
    }

    if (fs.existsSync(filePath)) {
        fs.unlinkSync(filePath);
        console.log(`[Admin] Deleted script: ${filename}`);
        loadScripts(); // Reload
        res.json({ success: true });
    } else {
        sendError(res, 404, 'File not found');
    }
});

app.get('/sources', rateLimit, requireOnlineAccess, (req, res) => {
    const result = {};
    Object.keys(sourceMap).forEach(s => {
        result[s] = sourceMap[s].length;
    });
    res.json({ sources: result });
});

app.post('/resolve', rateLimit, requireOnlineAccess, async (req, res) => {
    const { source, musicInfo, quality, nocache } = req.body;
    const action = req.body.action || 'musicUrl'; // Default to musicUrl
    const songId = musicInfo.songmid || musicInfo.hash;
    
    // Cache Key includes Action now
    const cacheKey = `${source}:${songId}:${action}:${quality || ''}`;
    
    console.log(`[API] Resolve ${action} @ ${source} - ${songId}`);

    // 1. Check Cache (unless nocache is requested)
    if (!nocache) {
        const cached = urlCache.get(cacheKey);
        if (cached) {
            if (cached.expireAt > Date.now()) {
                console.log(`  -> Cache Hit! (${cached.provider})`);
                const jsonResp = { data: cached.data, provider: cached.provider };
                if (action === 'musicUrl' && typeof cached.data === 'string') {
                    jsonResp.url = cached.data;
                }
                return res.json(jsonResp); 
            } else {
                urlCache.delete(cacheKey); // Expired
            }
        }
    } else {
        console.log('  -> Cache skipped (nocache=true)');
    }

    if (!sourceMap[source] || sourceMap[source].length === 0) {
        return sendError(res, 404, 'Source not supported');
    }

    const handlers = sourceMap[source];
    const count = handlers.length;

    console.log(`[Balancer] Source: ${source}, Handlers: [${handlers.map(h => h.scriptId).join(', ')}], Total: ${count}`);

    // Round-Robin Logic
    let currentIndex = sourceIndex[source] || 0;
    sourceIndex[source] = (currentIndex + 1) % count;
    
    const orderedHandlers = handlers.slice(currentIndex).concat(handlers.slice(0, currentIndex));

    let lastError = null;
    
    for (const provider of orderedHandlers) {
        if (!provider.handler) continue;

        console.log(`  Trying script: ${provider.scriptId} (via Proxy)...`);
        
        try {
            // 4-Second Timeout Logic
            const timeoutPromise = new Promise((_, reject) => {
                setTimeout(() => reject(new Error('Timeout (4s)')), 4000);
            });

            const requestInfo = { musicInfo };
            if (action === 'musicUrl') {
                requestInfo.type = quality || '128k';
            }

            const handlerPromise = provider.handler({
                action: action,
                source: source,
                info: requestInfo
            });

            // Race: Handler vs Timeout
            const response = await Promise.race([handlerPromise, timeoutPromise]);

            console.log(`  -> Success!`);
            
            // 2. Save to Cache
            const resultData = response; 

            urlCache.set(cacheKey, {
                data: resultData,
                provider: provider.scriptId,
                expireAt: Date.now() + CACHE_DURATION
            });
            saveCacheToFile(); // 保存到文件

            // Return result
            const jsonResp = { data: resultData };
            if (action === 'musicUrl' && typeof resultData === 'string') {
                jsonResp.url = resultData;
            }
            return res.json(jsonResp);

        } catch (e) {
            const isTimeout = e.message === 'Timeout (4s)';
            console.error(`  -> Failed (${isTimeout ? 'TIMEOUT' : 'ERROR'}): ${e.message || e}`);
            lastError = e.message || e;
        }
    }

    sendError(res, 500, 'All providers failed');
});

// --- Search Endpoint REMOVED ---
// /search 接口已移除，请在客户端直接实现搜索

// Catch-all route for SPA (Serve index.html for unknown paths)
app.use((req, res) => {
    // Avoid intercepting API calls if they fell through
    if (req.path.startsWith('/resolve') || req.path.startsWith('/sources') || req.path.startsWith('/admin')) {
        return sendError(res, 404, 'API Endpoint Not Found');
    }
    // 如果你有前端页面，这里可以返回，否则返回404
    if (fs.existsSync(path.join(PUBLIC_DIR, 'index.html'))) {
        res.sendFile(path.join(PUBLIC_DIR, 'index.html'), (err) => {
            if (err) res.status(500).send("Server Error: Unable to load UI.");
        });
    } else {
        res.status(404).send("Hydrogen Dispatch Server Running. (UI not found)");
    }
});

app.listen(PORT, () => {
    loadCacheFromFile(); // 从文件加载缓存
    console.log(`LX Music Middleware (Proxy Core) running on http://127.0.0.1:${PORT}`);
    console.log(`[Config] Proxy Enabled: ${CFW_PROXY_URL}`);
    loadScripts();
});
