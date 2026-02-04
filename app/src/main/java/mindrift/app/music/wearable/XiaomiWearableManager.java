package mindrift.app.music.wearable;

import android.content.Context;
import android.net.Uri;
import android.os.SystemClock;
import androidx.documentfile.provider.DocumentFile;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.xiaomi.xms.wearable.Wearable;
import com.xiaomi.xms.wearable.auth.AuthApi;
import com.xiaomi.xms.wearable.auth.Permission;
import com.xiaomi.xms.wearable.message.MessageApi;
import com.xiaomi.xms.wearable.message.OnMessageReceivedListener;
import com.xiaomi.xms.wearable.node.DataItem;
import com.xiaomi.xms.wearable.node.DataSubscribeResult;
import com.xiaomi.xms.wearable.node.Node;
import com.xiaomi.xms.wearable.node.NodeApi;
import com.xiaomi.xms.wearable.node.OnDataChangedListener;
import com.xiaomi.xms.wearable.service.OnServiceConnectionListener;
import com.xiaomi.xms.wearable.service.ServiceApi;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import mindrift.app.music.core.lyric.LyricService;
import mindrift.app.music.core.script.ScriptManager;
import mindrift.app.music.core.search.SearchService;
import mindrift.app.music.core.proxy.RequestProxy;
import mindrift.app.music.model.ResolveRequest;
import mindrift.app.music.utils.Logger;
import mindrift.app.music.utils.PlatformUtils;

public class XiaomiWearableManager {
    private static final String ACTION_CAPABILITIES = "capabilities";
    private static final String ACTION_GET_CAPABILITIES = "getCapabilities";
    private static final String ACTION_CAPABILITIES_UPDATE = "capabilitiesUpdate";
    private static final String ACTION_WATCH_READY = "WATCH_READY";
    private static final String ACTION_WATCH_READY_ACK = "WATCH_READY_ACK";
    private static final String ACTION_SEARCH = "search";
    private static final String ACTION_LYRIC = "lyric";
    private static final String ACTION_GET_LYRIC = "getLyric";
    private static final String ACTION_UPLOAD_START = "upload.start";
    private static final String ACTION_UPLOAD_CHUNK = "upload.chunk";
    private static final String ACTION_UPLOAD_FINISH = "upload.finish";
    private static final String ACTION_UPLOAD_ACK = "upload.ack";
    private static final String ACTION_UPLOAD_RESULT = "upload.result";
    private static final long MAX_UPLOAD_SIZE = 5 * 1024 * 1024L;
    private static final int UPLOAD_CHUNK_SIZE = 8 * 1024;
    private static final long UPLOAD_TIMEOUT_MS = 30000L;
    private static final Permission[] REQUIRED_PERMISSIONS = new Permission[]{
            Permission.DEVICE_MANAGER,
            Permission.NOTIFY
    };
    private final Context context;
    private final RequestProxy requestProxy;
    private final ScriptManager scriptManager;
    private final SearchService searchService = new SearchService();
    private final LyricService lyricService = new LyricService();
    private final NodeApi nodeApi;
    private final MessageApi messageApi;
    private final AuthApi authApi;
    private final ServiceApi serviceApi;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService uploadExecutor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService uploadScheduler = Executors.newSingleThreadScheduledExecutor();
    private final Gson gson = new Gson();
    private String currentNodeId;
    private String currentNodeName;
    private String listenerNodeId;
    private String subscribedNodeId;
    private String attachingNodeId;
    private Boolean connectedStatus;
    private volatile boolean hasDevicePermission = false;
    private volatile boolean hasNotifyPermission = false;
    private volatile boolean serviceConnected = false;
    private boolean messageListenerAttached = false;
    private boolean started = false;
    private volatile boolean refreshingNodes = false;
    private volatile boolean detailQueryDisabled = false;
    private volatile boolean attachingListener = false;
    private volatile boolean queryInProgress = false;
    private volatile long lastDetailQueryFailedAt = 0L;
    private volatile boolean optionalSubscribeDisabled = false;
    private volatile long lastRefreshAt = 0L;
    private static final long MIN_REFRESH_INTERVAL_MS = 3000L;
    private ScheduledFuture<?> reconnectFuture;
    private final Map<String, UploadSession> uploadSessions = new ConcurrentHashMap<>();
    private static final String DEFAULT_WEAR_APP_URI = "/pages/init";

    private final OnServiceConnectionListener serviceConnectionListener = new OnServiceConnectionListener() {
        @Override
        public void onServiceConnected() {
            serviceConnected = true;
            Logger.info("Wearable service connected");
            refreshNodes();
        }

        @Override
        public void onServiceDisconnected() {
            serviceConnected = false;
            Logger.warn("Wearable service disconnected");
            resetNodeState();
        }
    };

    private final OnMessageReceivedListener messageListener = new OnMessageReceivedListener() {
        @Override
        public void onMessageReceived(String nodeId, byte[] message) {
            handleIncomingMessage(nodeId, message);
        }
    };

    private final OnDataChangedListener statusListener = new OnDataChangedListener() {
        @Override
        public void onDataChanged(String nodeId, DataItem dataItem, DataSubscribeResult data) {
            if (dataItem == null || data == null) return;
            int type = dataItem.getType();
            if (type == DataItem.ITEM_CONNECTION.getType()) {
                int status = data.getConnectedStatus();
                if (status == DataSubscribeResult.RESULT_CONNECTION_CONNECTED) {
                    updateConnectedStatus(true);
                    Logger.info("Wearable connected: " + nodeId);
                } else if (status == DataSubscribeResult.RESULT_CONNECTION_DISCONNECTED) {
                    updateConnectedStatus(false);
                    Logger.warn("Wearable disconnected: " + nodeId);
                    resetNodeState();
                    scheduleReconnect(2000);
                }
                return;
            }
            if (type == DataItem.ITEM_CHARGING.getType()) {
                int status = data.getChargingStatus();
                if (status == DataSubscribeResult.RESULT_CHARGING_START) {
                    Logger.info("Wearable charging started");
                } else if (status == DataSubscribeResult.RESULT_CHARGING_FINISH) {
                    Logger.info("Wearable charging finished");
                } else if (status == DataSubscribeResult.RESULT_CHARGING_QUIT) {
                    Logger.info("Wearable charging stopped");
                }
                return;
            }
            if (type == DataItem.ITEM_WEARING.getType()) {
                int status = data.getWearingStatus();
                if (status == DataSubscribeResult.RESULT_WEARING_ON) {
                    Logger.info("Wearable wearing on");
                } else if (status == DataSubscribeResult.RESULT_WEARING_OFF) {
                    Logger.info("Wearable wearing off");
                }
                return;
            }
            if (type == DataItem.ITEM_SLEEP.getType()) {
                int status = data.getSleepStatus();
                if (status == DataSubscribeResult.RESULT_SLEEP_IN) {
                    Logger.info("Wearable sleep in");
                } else if (status == DataSubscribeResult.RESULT_SLEEP_OUT) {
                    Logger.info("Wearable sleep out");
                }
            }
        }
    };

    public XiaomiWearableManager(Context context, RequestProxy requestProxy, ScriptManager scriptManager) {
        this.context = context.getApplicationContext();
        this.requestProxy = requestProxy;
        this.scriptManager = scriptManager;
        this.nodeApi = Wearable.getNodeApi(this.context);
        this.messageApi = Wearable.getMessageApi(this.context);
        this.authApi = Wearable.getAuthApi(this.context);
        this.serviceApi = Wearable.getServiceApi(this.context);
    }

    public void start() {
        if (started) return;
        started = true;
        Logger.info("Wearable manager start");
        serviceApi.registerServiceConnectionListener(serviceConnectionListener);
        Logger.info("Wearable service listener registered");
        if (!isWearableAppInstalled()) {
            Logger.warn("Wearable app not installed, skip wearable init");
            return;
        }
        refreshNodes();
    }

    public void stop() {
        if (!started) return;
        started = false;
        Logger.info("Wearable manager stop");
        serviceApi.unregisterServiceConnectionListener(serviceConnectionListener);
        Logger.info("Wearable service listener unregistered");
        resetNodeState();
        if (reconnectFuture != null) {
            reconnectFuture.cancel(false);
            reconnectFuture = null;
        }
        for (UploadSession session : uploadSessions.values()) {
            if (session.timeoutFuture != null) {
                session.timeoutFuture.cancel(false);
            }
        }
        uploadSessions.clear();
        searchService.shutdown();
        lyricService.shutdown();
        executor.shutdownNow();
        uploadExecutor.shutdownNow();
        uploadScheduler.shutdownNow();
    }

    public void refreshNodes() {
        if (!started) return;
        if (!isWearableAppInstalled()) {
            Logger.warn("Wearable app not installed, skip node refresh");
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastRefreshAt < MIN_REFRESH_INTERVAL_MS) {
            Logger.debug("Skip node refresh: too frequent");
            return;
        }
        if (refreshingNodes) {
            Logger.debug("Skip node refresh: already refreshing");
            return;
        }
        lastRefreshAt = now;
        refreshingNodes = true;
        Logger.info("Query connected wearable nodes");
        nodeApi.getConnectedNodes()
                .addOnSuccessListener(nodes -> {
                    refreshingNodes = false;
                    if (nodes == null || nodes.isEmpty()) {
                        Logger.warn("No connected wearable nodes");
                        resetNodeState();
                        scheduleReconnect(3000);
                        return;
                    }
                    Logger.info("Connected nodes: " + nodes.size());
                    Node node = nodes.get(0);
                    updateCurrentNode(node);
                })
                .addOnFailureListener(e -> {
                    refreshingNodes = false;
                    Logger.error("Get connected nodes failed: " + e.getMessage(), e);
                });
    }

    private void updateCurrentNode(Node node) {
        if (node == null) return;
        String nodeId = node.id;
        if (nodeId == null || nodeId.isEmpty()) return;
        if (currentNodeId != null && !currentNodeId.equals(nodeId)) {
            resetNodeState();
        }
        synchronized (this) {
            currentNodeId = nodeId;
            currentNodeName = node.name;
        }
        Logger.info("Active node: " + nodeId + " (" + node.name + ")");
        if (messageListenerAttached && nodeId.equals(listenerNodeId)) {
            Logger.debug("Node already attached, skip permission check");
            return;
        }
        ensurePermission(nodeId);
    }

    private void ensurePermission(String nodeId) {
        if (nodeId == null || nodeId.isEmpty()) return;
        if (messageListenerAttached && nodeId.equals(listenerNodeId)) return;
        if (attachingListener && nodeId.equals(attachingNodeId)) {
            Logger.debug("Skip permission check: attaching listener");
            return;
        }
        Logger.info("Check permissions for node: " + nodeId);
        authApi.checkPermissions(nodeId, REQUIRED_PERMISSIONS)
                .addOnSuccessListener(results -> {
                    boolean deviceGranted = results != null && results.length > 0 && Boolean.TRUE.equals(results[0]);
                    boolean notifyGranted = results != null && results.length > 1 && Boolean.TRUE.equals(results[1]);
                    hasDevicePermission = deviceGranted;
                    hasNotifyPermission = notifyGranted;
                    Logger.info("Permission check result: DEVICE_MANAGER=" + hasDevicePermission + ", NOTIFY=" + hasNotifyPermission);
                    if (!hasDevicePermission || !hasNotifyPermission) {
                        requestPermission(nodeId);
                        return;
                    }
                    attachMessageListener(nodeId);
                })
                .addOnFailureListener(e -> Logger.error("Permission check failed: " + e.getMessage(), e));
    }

    private void requestPermission(String nodeId) {
        Logger.info("Request permissions for node: " + nodeId);
        authApi.requestPermission(nodeId, REQUIRED_PERMISSIONS)
                .addOnSuccessListener(perms -> {
                    hasDevicePermission = hasPermission(perms, Permission.DEVICE_MANAGER);
                    hasNotifyPermission = hasPermission(perms, Permission.NOTIFY);
                    Logger.info("Permission request success: DEVICE_MANAGER=" + hasDevicePermission + ", NOTIFY=" + hasNotifyPermission);
                    if (hasDevicePermission) {
                        attachMessageListener(nodeId);
                    } else {
                        Logger.warn("Device permission missing, skip attach listener");
                    }
                })
                .addOnFailureListener(e -> Logger.error("Permission request failed: " + e.getMessage(), e));
    }

    private void attachMessageListener(String nodeId) {
        if (nodeId == null || nodeId.isEmpty()) return;
        if (messageListenerAttached && nodeId.equals(listenerNodeId)) return;
        if (attachingListener && nodeId.equals(attachingNodeId)) return;
        attachingListener = true;
        attachingNodeId = nodeId;
        Logger.info("Check wear app installed on node: " + nodeId);
        nodeApi.isWearAppInstalled(nodeId)
                .addOnSuccessListener(installed -> {
                    if (!Boolean.TRUE.equals(installed)) {
                        Logger.warn("Wearable app not installed on node: " + nodeId);
                        attachingListener = false;
                        attachingNodeId = null;
                        return;
                    }
                    Logger.info("Wear app installed, add message listener: " + nodeId);
                    messageApi.addListener(nodeId, messageListener)
                            .addOnSuccessListener(v -> {
                                messageListenerAttached = true;
                                listenerNodeId = nodeId;
                                Logger.info("Message listener added for node: " + nodeId);
                                launchWearApp(nodeId);
                                if (hasDevicePermission) {
                                    queryDeviceStatus(nodeId);
                                    subscribeStatus(nodeId);
                                } else {
                                    Logger.warn("Skip device status query: missing permission");
                                }
                                sendCapabilities(nodeId, true, null);
                                attachingListener = false;
                                attachingNodeId = null;
                            })
                            .addOnFailureListener(e -> {
                                attachingListener = false;
                                attachingNodeId = null;
                                Logger.error("Add message listener failed: " + e.getMessage(), e);
                            });
                })
                .addOnFailureListener(e -> {
                    attachingListener = false;
                    attachingNodeId = null;
                    Logger.error("Check wear app failed: " + e.getMessage(), e);
                });
    }

    private void handleIncomingMessage(String nodeId, byte[] message) {
        if (executor.isShutdown()) {
            Logger.warn("Message ignored: executor shutdown");
            return;
        }
        executor.execute(() -> {
            String payload = new String(message, StandardCharsets.UTF_8);
            Logger.info("Received message from node " + nodeId + " bytes=" + payload.length());
            JsonObject json = null;
            try {
                json = gson.fromJson(payload, JsonObject.class);
            } catch (Exception e) {
                Logger.warn("Parse message failed: " + e.getMessage());
            }
            String requestId = getString(json, "_requestId");
            String action = getString(json, "action");
            if ((action != null && !action.isEmpty()) || (requestId != null && !requestId.isEmpty())) {
                Logger.info("Message action=" + action + " requestId=" + requestId);
            }
            if (isCapabilitiesRequest(json)) {
                sendCapabilities(nodeId, false, requestId);
                return;
            }
            if (ACTION_WATCH_READY.equalsIgnoreCase(action)) {
                handleWatchReady(nodeId, requestId);
                return;
            }
            if (isUploadAction(action)) {
                handleUploadResponse(action, json, requestId);
                return;
            }
            if (ACTION_SEARCH.equalsIgnoreCase(action)) {
                handleSearch(nodeId, json, requestId);
                return;
            }
            if (ACTION_LYRIC.equalsIgnoreCase(action) || ACTION_GET_LYRIC.equalsIgnoreCase(action)) {
                handleLyric(nodeId, json, requestId);
                return;
            }
            ResolveRequest request;
            try {
                request = gson.fromJson(payload, ResolveRequest.class);
            } catch (Exception e) {
                sendMessage(nodeId, gson.toJson(new ErrorResponse("Invalid request", null, requestId)));
                return;
            }
            requestProxy.resolve(request, new RequestProxy.ResolveCallback() {
                @Override
                public void onSuccess(String responseJson) {
                    sendMessage(nodeId, withRequestId(responseJson, requestId));
                }

                @Override
                public void onFailure(Exception e) {
                    sendMessage(nodeId, gson.toJson(new ErrorResponse(e.getMessage(), request, requestId)));
                }
            });
        });
    }

    private void sendMessage(String nodeId, String json) {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        Logger.info("Send message to node " + nodeId + ", bytes=" + data.length);
        messageApi.sendMessage(nodeId, data)
                .addOnSuccessListener(result -> Logger.info("Response sent to node " + nodeId))
                .addOnFailureListener(e -> Logger.error("Send message failed: " + e.getMessage(), e));
    }

    private void sendCapabilities(String nodeId, boolean update, String requestId) {
        if (nodeId == null || nodeId.isEmpty()) return;
        Map<String, Object> payload = buildCapabilitiesPayload(update ? ACTION_CAPABILITIES_UPDATE : ACTION_CAPABILITIES, requestId);
        Logger.info("Send capabilities to node: " + nodeId + (update ? " (update)" : ""));
        sendMessage(nodeId, gson.toJson(payload));
    }

    private Map<String, Object> buildCapabilitiesPayload(String action, String requestId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", action);
        payload.put("code", 0);
        payload.put("message", "ok");
        if (requestId != null && !requestId.isEmpty()) {
            payload.put("_requestId", requestId);
        }
        Map<String, Object> data = scriptManager == null ? new HashMap<>() : scriptManager.getCapabilitiesSummary();
        payload.put("data", data);
        return payload;
    }

    public void notifyCapabilitiesChanged() {
        if (listenerNodeId != null) {
            Logger.info("Capabilities changed, notify node: " + listenerNodeId);
            sendCapabilities(listenerNodeId, true, null);
        }
    }

    private void handleSearch(String nodeId, JsonObject json, String requestId) {
        String keyword = getString(json, "keyword");
        String platform = getString(json, "platform");
        String normalizedPlatform = PlatformUtils.normalize(platform);
        int page = getInt(json, "page", 1);
        int pageSize = getInt(json, "pageSize", 20);
        Logger.info("Search request: platform=" + platform + ", keyword=" + keyword + ", page=" + page + ", pageSize=" + pageSize);
        if (keyword == null || keyword.trim().isEmpty()) {
            sendMessage(nodeId, gson.toJson(buildErrorPayload(ACTION_SEARCH, "Keyword missing", requestId)));
            return;
        }
        SearchService.SearchResult result = searchService.search(normalizedPlatform, keyword, page, pageSize);
        if (result == null) {
            sendMessage(nodeId, gson.toJson(buildErrorPayload(ACTION_SEARCH, "Search failed", requestId)));
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("platform", result.platform);
        data.put("page", result.page);
        data.put("pageSize", result.pageSize);
        data.put("total", result.total);
        data.put("results", result.results);
        String displayPlatform = PlatformUtils.displayName(normalizedPlatform);
        sendMessage(nodeId, gson.toJson(buildSuccessPayload(ACTION_SEARCH, data, buildInfo("search", displayPlatform, keyword, result.page, result.pageSize), requestId)));
    }

    private void handleLyric(String nodeId, JsonObject json, String requestId) {
        String platform = getString(json, "platform");
        String normalizedPlatform = PlatformUtils.normalize(platform);
        String id = getString(json, "id");
        if (id == null || id.isEmpty()) {
            id = getString(json, "songid");
        }
        Logger.info("Lyric request: platform=" + platform + ", id=" + id);
        if (platform == null || platform.trim().isEmpty()) {
            sendMessage(nodeId, gson.toJson(buildErrorPayload(ACTION_LYRIC, "Platform missing", requestId)));
            return;
        }
        if (id == null || id.trim().isEmpty()) {
            sendMessage(nodeId, gson.toJson(buildErrorPayload(ACTION_LYRIC, "SongId missing", requestId)));
            return;
        }
        LyricService.LyricResult result = lyricService.getLyric(normalizedPlatform, id);
        Map<String, Object> data = new HashMap<>();
        data.put("lyric", result.lyric);
        data.put("tlyric", result.tlyric);
        data.put("rlyric", result.rlyric);
        data.put("lxlyric", result.lxlyric);
        String displayPlatform = PlatformUtils.displayName(normalizedPlatform);
        sendMessage(nodeId, gson.toJson(buildSuccessPayload(ACTION_LYRIC, data, buildInfo("lyric", displayPlatform, id, null, null), requestId)));
    }

    private void handleWatchReady(String nodeId, String requestId) {
        Logger.info("Watch ready received: node=" + nodeId);
        Map<String, Object> ack = new HashMap<>();
        ack.put("action", ACTION_WATCH_READY_ACK);
        ack.put("code", 0);
        ack.put("message", "ok");
        if (requestId != null && !requestId.isEmpty()) {
            ack.put("_requestId", requestId);
        }
        sendMessage(nodeId, gson.toJson(ack));
        sendCapabilities(nodeId, true, null);
    }

    private Map<String, Object> buildSuccessPayload(String action, Map<String, Object> data, Map<String, Object> info, String requestId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", action);
        payload.put("code", 0);
        payload.put("message", "ok");
        payload.put("data", data);
        if (info != null) {
            payload.put("info", info);
        }
        if (requestId != null && !requestId.isEmpty()) {
            payload.put("_requestId", requestId);
        }
        return payload;
    }

    private Map<String, Object> buildErrorPayload(String action, String message, String requestId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", action);
        payload.put("code", -1);
        payload.put("message", message == null ? "error" : message);
        Map<String, Object> error = new HashMap<>();
        error.put("message", payload.get("message"));
        payload.put("error", error);
        if (requestId != null && !requestId.isEmpty()) {
            payload.put("_requestId", requestId);
        }
        return payload;
    }

    private Map<String, Object> buildInfo(String action, String platform, String keywordOrId, Integer page, Integer pageSize) {
        Map<String, Object> info = new HashMap<>();
        info.put("action", action);
        if (platform != null) info.put("platform", PlatformUtils.displayName(platform));
        if (keywordOrId != null) {
            if ("search".equals(action)) info.put("keyword", keywordOrId);
            else info.put("songId", keywordOrId);
        }
        if (page != null) info.put("page", page);
        if (pageSize != null) info.put("pageSize", pageSize);
        return info;
    }

    private void queryDeviceStatus(String nodeId) {
        if (nodeId == null || nodeId.isEmpty()) return;
        if (!hasDevicePermission) {
            Logger.warn("Skip device status query: missing permission");
            return;
        }
        if (queryInProgress) {
            Logger.debug("Skip device status query: in progress");
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (detailQueryDisabled && now - lastDetailQueryFailedAt > 60_000L) {
            detailQueryDisabled = false;
        }
        queryInProgress = true;
        nodeApi.query(nodeId, DataItem.ITEM_CONNECTION)
                .addOnSuccessListener(result -> {
                    updateConnectedStatus(result.isConnected);
                    Logger.info("Wearable connection: " + result.isConnected);
                    queryOptionalStatus(nodeId);
                })
                .addOnFailureListener(e -> {
                    Logger.warn("Query connection status failed: " + e.getMessage());
                    detailQueryDisabled = true;
                    lastDetailQueryFailedAt = SystemClock.elapsedRealtime();
                    finishQuery();
                });
    }

    private void queryOptionalStatus(String nodeId) {
        if (detailQueryDisabled) {
            Logger.debug("Skip optional device status queries");
            finishQuery();
            return;
        }
        queryBattery(nodeId, () -> {
            if (detailQueryDisabled) {
                finishQuery();
                return;
            }
            queryCharging(nodeId, () -> {
                if (detailQueryDisabled) {
                    finishQuery();
                    return;
                }
                queryWearing(nodeId, () -> {
                    if (detailQueryDisabled) {
                        finishQuery();
                        return;
                    }
                    querySleep(nodeId, this::finishQuery);
                });
            });
        });
    }

    private void queryBattery(String nodeId, Runnable next) {
        nodeApi.query(nodeId, DataItem.ITEM_BATTERY)
                .addOnSuccessListener(result -> {
                    Logger.info("Wearable battery: " + result.battery);
                    next.run();
                })
                .addOnFailureListener(e -> {
                    Logger.warn("Query battery status failed: " + e.getMessage());
                    detailQueryDisabled = true;
                    lastDetailQueryFailedAt = SystemClock.elapsedRealtime();
                    next.run();
                });
    }

    private void queryCharging(String nodeId, Runnable next) {
        nodeApi.query(nodeId, DataItem.ITEM_CHARGING)
                .addOnSuccessListener(result -> {
                    Logger.info("Wearable charging: " + result.isCharging);
                    next.run();
                })
                .addOnFailureListener(e -> {
                    Logger.warn("Query charging status failed: " + e.getMessage());
                    detailQueryDisabled = true;
                    lastDetailQueryFailedAt = SystemClock.elapsedRealtime();
                    next.run();
                });
    }

    private void queryWearing(String nodeId, Runnable next) {
        nodeApi.query(nodeId, DataItem.ITEM_WEARING)
                .addOnSuccessListener(result -> {
                    Logger.info("Wearable wearing: " + result.isWearing);
                    next.run();
                })
                .addOnFailureListener(e -> {
                    Logger.warn("Query wearing status failed: " + e.getMessage());
                    detailQueryDisabled = true;
                    lastDetailQueryFailedAt = SystemClock.elapsedRealtime();
                    next.run();
                });
    }

    private void querySleep(String nodeId, Runnable next) {
        nodeApi.query(nodeId, DataItem.ITEM_SLEEP)
                .addOnSuccessListener(result -> {
                    Logger.info("Wearable sleeping: " + result.isSleeping);
                    next.run();
                })
                .addOnFailureListener(e -> {
                    Logger.warn("Query sleep status failed: " + e.getMessage());
                    detailQueryDisabled = true;
                    lastDetailQueryFailedAt = SystemClock.elapsedRealtime();
                    next.run();
                });
    }

    private void finishQuery() {
        queryInProgress = false;
    }

    private void subscribeStatus(String nodeId) {
        if (nodeId == null || nodeId.isEmpty()) return;
        if (nodeId.equals(subscribedNodeId)) return;
        subscribedNodeId = nodeId;
        subscribeItem(nodeId, DataItem.ITEM_CONNECTION, "connection");
        if (optionalSubscribeDisabled) {
            Logger.debug("Skip optional status subscribe");
            return;
        }
        subscribeItem(nodeId, DataItem.ITEM_CHARGING, "charging");
        subscribeItem(nodeId, DataItem.ITEM_WEARING, "wearing");
        subscribeItem(nodeId, DataItem.ITEM_SLEEP, "sleep");
    }

    private void subscribeItem(String nodeId, DataItem item, String label) {
        nodeApi.subscribe(nodeId, item, statusListener)
                .addOnSuccessListener(v -> Logger.info("Subscribe " + label + " status ok"))
                .addOnFailureListener(e -> {
                    Logger.warn("Subscribe " + label + " status failed: " + e.getMessage());
                    if (!"connection".equals(label)) {
                        optionalSubscribeDisabled = true;
                    }
                });
    }

    private void unsubscribeStatus(String nodeId) {
        if (nodeId == null || nodeId.isEmpty()) return;
        nodeApi.unsubscribe(nodeId, DataItem.ITEM_CONNECTION)
                .addOnSuccessListener(v -> Logger.info("Unsubscribe connection ok"))
                .addOnFailureListener(e -> Logger.error("Unsubscribe connection failed: " + e.getMessage(), e));
        nodeApi.unsubscribe(nodeId, DataItem.ITEM_CHARGING)
                .addOnSuccessListener(v -> Logger.info("Unsubscribe charging ok"))
                .addOnFailureListener(e -> Logger.error("Unsubscribe charging failed: " + e.getMessage(), e));
        nodeApi.unsubscribe(nodeId, DataItem.ITEM_WEARING)
                .addOnSuccessListener(v -> Logger.info("Unsubscribe wearing ok"))
                .addOnFailureListener(e -> Logger.error("Unsubscribe wearing failed: " + e.getMessage(), e));
        nodeApi.unsubscribe(nodeId, DataItem.ITEM_SLEEP)
                .addOnSuccessListener(v -> Logger.info("Unsubscribe sleep ok"))
                .addOnFailureListener(e -> Logger.error("Unsubscribe sleep failed: " + e.getMessage(), e));
    }

    private void resetNodeState() {
        if (messageListenerAttached && listenerNodeId != null) {
            messageApi.removeListener(listenerNodeId);
        }
        if (subscribedNodeId != null) {
            unsubscribeStatus(subscribedNodeId);
        }
        messageListenerAttached = false;
        listenerNodeId = null;
        subscribedNodeId = null;
        synchronized (this) {
            currentNodeId = null;
            currentNodeName = null;
            connectedStatus = null;
        }
        hasDevicePermission = false;
        hasNotifyPermission = false;
        attachingListener = false;
        attachingNodeId = null;
        detailQueryDisabled = false;
        queryInProgress = false;
        lastDetailQueryFailedAt = 0L;
        optionalSubscribeDisabled = false;
        Logger.info("Wearable node state cleared");
    }

    private void scheduleReconnect(long delayMs) {
        if (!serviceConnected || !started || uploadScheduler.isShutdown()) return;
        if (reconnectFuture != null && !reconnectFuture.isDone()) return;
        reconnectFuture = uploadScheduler.schedule(this::refreshNodes, delayMs, TimeUnit.MILLISECONDS);
    }

    private void launchWearApp(String nodeId) {
        if (nodeId == null || nodeId.isEmpty()) return;
        nodeApi.launchWearApp(nodeId, DEFAULT_WEAR_APP_URI)
                .addOnSuccessListener(v -> Logger.info("Launch wear app success"))
                .addOnFailureListener(e -> Logger.warn("Launch wear app failed: " + e.getMessage()));
    }

    private boolean isWearableAppInstalled() {
        return isPackageInstalled("com.xiaomi.wearable") || isPackageInstalled("com.mi.health");
    }

    private boolean isCapabilitiesRequest(JsonObject json) {
        if (json == null) return false;
        String action = getString(json, "action");
        String type = getString(json, "type");
        String cmd = getString(json, "cmd");
        return isCapabilityAction(action) || isCapabilityAction(type) || isCapabilityAction(cmd);
    }

    private boolean hasPermission(Permission[] permissions, Permission target) {
        if (permissions == null || target == null) return false;
        for (Permission permission : permissions) {
            if (permission == target) return true;
        }
        return false;
    }

    private boolean isCapabilityAction(String value) {
        if (value == null) return false;
        return ACTION_GET_CAPABILITIES.equalsIgnoreCase(value)
                || ACTION_CAPABILITIES.equalsIgnoreCase(value);
    }

    private boolean isUploadAction(String action) {
        if (action == null) return false;
        return ACTION_UPLOAD_ACK.equalsIgnoreCase(action)
                || ACTION_UPLOAD_RESULT.equalsIgnoreCase(action);
    }

    private String withRequestId(String responseJson, String requestId) {
        if (requestId == null || requestId.isEmpty()) return responseJson;
        if (responseJson == null || responseJson.trim().isEmpty()) {
            return gson.toJson(buildErrorPayload("musicUrl", "Empty response", requestId));
        }
        try {
            JsonObject obj = gson.fromJson(responseJson, JsonObject.class);
            if (obj == null) {
                return gson.toJson(buildErrorPayload("musicUrl", "Invalid response", requestId));
            }
            obj.addProperty("_requestId", requestId);
            return gson.toJson(obj);
        } catch (Exception e) {
            return gson.toJson(buildErrorPayload("musicUrl", "Invalid response", requestId));
        }
    }

    private String getString(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key)) return null;
        try {
            JsonElement el = obj.get(key);
            if (el == null || el.isJsonNull()) return null;
            return el.getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    private int getInt(JsonObject obj, String key, int fallback) {
        if (obj == null || key == null || !obj.has(key)) return fallback;
        try {
            JsonElement el = obj.get(key);
            if (el == null || el.isJsonNull()) return fallback;
            return el.getAsInt();
        } catch (Exception e) {
            return fallback;
        }
    }

    private boolean getBoolean(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key)) return false;
        try {
            JsonElement el = obj.get(key);
            if (el == null || el.isJsonNull()) return false;
            if (el.isJsonPrimitive()) {
                JsonPrimitive primitive = el.getAsJsonPrimitive();
                if (primitive.isBoolean()) return primitive.getAsBoolean();
                if (primitive.isNumber()) return primitive.getAsInt() != 0;
                if (primitive.isString()) {
                    String value = primitive.getAsString();
                    return "1".equals(value) || "true".equalsIgnoreCase(value);
                }
            }
            return el.getAsBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    private void updateConnectedStatus(boolean connected) {
        synchronized (this) {
            connectedStatus = connected;
        }
    }

    public synchronized String getCurrentNodeId() {
        return currentNodeId;
    }

    public synchronized String getCurrentNodeName() {
        return currentNodeName;
    }

    public synchronized Boolean getConnectedStatus() {
        return connectedStatus;
    }

    public boolean isServiceConnected() {
        return serviceConnected;
    }

    public boolean isWearableAppInstalledForUi() {
        return isWearableAppInstalled();
    }

    public interface UploadCallback {
        void onProgress(int percent);
        void onSuccess(String fileId, String message);
        void onFailure(String message);
    }

    public void uploadMusic(Uri uri, UploadCallback callback) {
        if (uploadExecutor.isShutdown()) {
            notifyUploadFailure(callback, "服务已关闭");
            return;
        }
        uploadExecutor.execute(() -> {
            if (!serviceConnected) {
                notifyUploadFailure(callback, "服务未连接");
                return;
            }
            String nodeId = getCurrentNodeId();
            if (nodeId == null || nodeId.isEmpty()) {
                notifyUploadFailure(callback, "未连接设备");
                return;
            }
            DocumentFile doc = DocumentFile.fromSingleUri(context, uri);
            String name = doc != null && doc.getName() != null ? doc.getName() : ("music_" + System.currentTimeMillis());
            long size = doc != null ? doc.length() : -1;
            String mime = context.getContentResolver().getType(uri);
            if (mime == null || mime.trim().isEmpty()) {
                mime = "application/octet-stream";
            }
            byte[] data = readBytes(uri);
            if (data == null || data.length == 0) {
                notifyUploadFailure(callback, "文件过大或读取失败");
                return;
            }
            if (size <= 0) size = data.length;
            if (size > MAX_UPLOAD_SIZE) {
                notifyUploadFailure(callback, "文件过大，最大 5MB");
                return;
            }
            String md5 = md5(data);
            String fileId = "file_" + System.currentTimeMillis();
            int totalChunks = (int) ((data.length + (UPLOAD_CHUNK_SIZE - 1)) / UPLOAD_CHUNK_SIZE);
            String requestId = "upload_" + System.currentTimeMillis();
            UploadSession session = new UploadSession(fileId, callback);
            uploadSessions.put(fileId, session);

            Logger.info("Upload start: " + name + " size=" + size + " chunks=" + totalChunks);
            sendMessage(nodeId, gson.toJson(buildUploadStartPayload(requestId, fileId, name, size, mime, md5, totalChunks)));

            int lastPercent = -1;
            for (int index = 0; index < totalChunks; index++) {
                int start = index * UPLOAD_CHUNK_SIZE;
                int end = Math.min(start + UPLOAD_CHUNK_SIZE, data.length);
                byte[] chunk = Arrays.copyOfRange(data, start, end);
                String base64 = android.util.Base64.encodeToString(chunk, android.util.Base64.NO_WRAP);
                sendMessage(nodeId, gson.toJson(buildUploadChunkPayload(requestId, fileId, index + 1, totalChunks, base64)));
                int percent = (int) Math.round(((index + 1) * 100.0) / totalChunks);
                if (percent != lastPercent && callback != null) {
                    callback.onProgress(percent);
                    lastPercent = percent;
                }
                SystemClock.sleep(8);
            }

            sendMessage(nodeId, gson.toJson(buildUploadFinishPayload(requestId, fileId, totalChunks, md5)));
            Logger.info("Upload finish sent: " + fileId);
            scheduleUploadTimeout(fileId);
        });
    }

    private Map<String, Object> buildUploadStartPayload(String requestId, String fileId, String name, long size,
                                                        String mime, String md5, int totalChunks) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", ACTION_UPLOAD_START);
        payload.put("_requestId", requestId);
        payload.put("fileId", fileId);
        payload.put("name", name);
        payload.put("size", size);
        payload.put("mime", mime);
        payload.put("chunkSize", UPLOAD_CHUNK_SIZE);
        payload.put("total", totalChunks);
        payload.put("md5", md5);
        return payload;
    }

    private Map<String, Object> buildUploadChunkPayload(String requestId, String fileId, int index, int total, String data) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", ACTION_UPLOAD_CHUNK);
        payload.put("_requestId", requestId);
        payload.put("fileId", fileId);
        payload.put("index", index);
        payload.put("total", total);
        payload.put("data", data);
        return payload;
    }

    private Map<String, Object> buildUploadFinishPayload(String requestId, String fileId, int total, String md5) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", ACTION_UPLOAD_FINISH);
        payload.put("_requestId", requestId);
        payload.put("fileId", fileId);
        payload.put("total", total);
        payload.put("md5", md5);
        return payload;
    }

    private void handleUploadResponse(String action, JsonObject json, String requestId) {
        if (json == null) return;
        String fileId = getString(json, "fileId");
        if (fileId == null || fileId.isEmpty()) {
            Logger.warn("Upload response missing fileId");
            return;
        }
        UploadSession session = uploadSessions.get(fileId);
        if (ACTION_UPLOAD_ACK.equalsIgnoreCase(action)) {
            int index = getInt(json, "index", -1);
            int total = getInt(json, "total", -1);
            Logger.info("Upload ack: fileId=" + fileId + " index=" + index + "/" + total);
            return;
        }
        if (ACTION_UPLOAD_RESULT.equalsIgnoreCase(action)) {
            boolean ok = getBoolean(json, "ok");
            String message = getString(json, "message");
            String path = getString(json, "path");
            Logger.info("Upload result: fileId=" + fileId + " ok=" + ok + " path=" + path);
            if (session != null && session.timeoutFuture != null) {
                session.timeoutFuture.cancel(false);
            }
            uploadSessions.remove(fileId);
            if (session != null && session.callback != null) {
                if (ok) {
                    session.callback.onSuccess(fileId, path != null ? path : message);
                } else {
                    session.callback.onFailure(message == null ? "上传失败" : message);
                }
            }
        }
    }

    private void scheduleUploadTimeout(String fileId) {
        UploadSession session = uploadSessions.get(fileId);
        if (session == null) return;
        if (uploadScheduler.isShutdown()) {
            Logger.warn("Upload timeout schedule skipped: scheduler shutdown");
            return;
        }
        session.timeoutFuture = uploadScheduler.schedule(() -> {
            UploadSession removed = uploadSessions.remove(fileId);
            if (removed != null && removed.callback != null) {
                removed.callback.onFailure("等待手表确认超时");
            }
        }, UPLOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private void notifyUploadFailure(UploadCallback callback, String message) {
        Logger.warn("Upload failed: " + message);
        if (callback != null) {
            callback.onFailure(message);
        }
    }

    private byte[] readBytes(Uri uri) {
        try (java.io.InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) return null;
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                if (output.size() > MAX_UPLOAD_SIZE) {
                    return null;
                }
            }
            return output.toByteArray();
        } catch (Exception e) {
            Logger.error("Read upload file failed: " + e.getMessage(), e);
            return null;
        }
    }

    private String md5(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            Logger.warn("Upload MD5 failed: " + e.getMessage());
            return "";
        }
    }

    private boolean isPackageInstalled(String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageInfo(packageName, 0);
            return info != null;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        } catch (Exception e) {
            Logger.warn("Package check failed: " + packageName);
            return false;
        }
    }

    private static class ErrorResponse {
        final int code;
        final String message;
        final String _requestId;
        final Object info;
        final Object error;

        ErrorResponse(String message, ResolveRequest request, String requestId) {
            this.code = -1;
            this.message = message == null ? "Unknown error" : message;
            this._requestId = requestId;
            Map<String, Object> info = new HashMap<>();
            if (request != null) {
                info.put("platform", PlatformUtils.displayName(request.getSource()));
                info.put("action", request.getAction() == null ? "musicUrl" : request.getAction());
                info.put("quality", request.getQuality());
                info.put("songId", request.resolveSongId());
            }
            this.info = info.isEmpty() ? null : info;
            this.error = new ErrorDetail(this.message);
        }
    }

    private static class ErrorDetail {
        final String message;
        ErrorDetail(String message) {
            this.message = message;
        }
    }

    private static class UploadSession {
        final String fileId;
        final UploadCallback callback;
        ScheduledFuture<?> timeoutFuture;

        UploadSession(String fileId, UploadCallback callback) {
            this.fileId = fileId;
            this.callback = callback;
        }
    }
}








