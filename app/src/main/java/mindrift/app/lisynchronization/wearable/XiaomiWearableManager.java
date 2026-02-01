package mindrift.app.lisynchronization.wearable;

import android.content.Context;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import mindrift.app.lisynchronization.core.lyric.LyricService;
import mindrift.app.lisynchronization.core.script.ScriptManager;
import mindrift.app.lisynchronization.core.search.SearchService;
import mindrift.app.lisynchronization.core.proxy.RequestProxy;
import mindrift.app.lisynchronization.model.ResolveRequest;
import mindrift.app.lisynchronization.utils.Logger;

public class XiaomiWearableManager {
    private static final String ACTION_CAPABILITIES = "capabilities";
    private static final String ACTION_GET_CAPABILITIES = "getCapabilities";
    private static final String ACTION_CAPABILITIES_UPDATE = "capabilitiesUpdate";
    private static final String ACTION_SEARCH = "search";
    private static final String ACTION_LYRIC = "lyric";
    private static final String ACTION_GET_LYRIC = "getLyric";
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
    private final Gson gson = new Gson();
    private String currentNodeId;
    private String currentNodeName;
    private String listenerNodeId;
    private String subscribedNodeId;
    private Boolean connectedStatus;
    private volatile boolean serviceConnected = false;
    private boolean messageListenerAttached = false;
    private boolean started = false;

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
        serviceApi.registerServiceConnectionListener(serviceConnectionListener);
        if (!isWearableAppInstalled()) {
            Logger.warn("Wearable app not installed, skip wearable init");
            return;
        }
        refreshNodes();
    }

    public void stop() {
        if (!started) return;
        started = false;
        serviceApi.unregisterServiceConnectionListener(serviceConnectionListener);
        resetNodeState();
    }

    public void refreshNodes() {
        if (!isWearableAppInstalled()) {
            Logger.warn("Wearable app not installed, skip node refresh");
            return;
        }
        nodeApi.getConnectedNodes()
                .addOnSuccessListener(nodes -> {
                    if (nodes == null || nodes.isEmpty()) {
                        Logger.warn("No connected wearable nodes");
                        resetNodeState();
                        return;
                    }
                    Node node = nodes.get(0);
                    updateCurrentNode(node);
                })
                .addOnFailureListener(e -> Logger.error("Get connected nodes failed: " + e.getMessage(), e));
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
        ensurePermission(nodeId);
    }

    private void ensurePermission(String nodeId) {
        authApi.checkPermissions(nodeId, REQUIRED_PERMISSIONS)
                .addOnSuccessListener(results -> {
                    boolean deviceGranted = results != null && results.length > 0 && results[0];
                    boolean notifyGranted = results != null && results.length > 1 && results[1];
                    if (!deviceGranted || !notifyGranted) {
                        requestPermission(nodeId);
                        return;
                    }
                    attachMessageListener(nodeId);
                })
                .addOnFailureListener(e -> Logger.error("Permission check failed: " + e.getMessage(), e));
    }

    private void requestPermission(String nodeId) {
        authApi.requestPermission(nodeId, REQUIRED_PERMISSIONS)
                .addOnSuccessListener(perms -> attachMessageListener(nodeId))
                .addOnFailureListener(e -> Logger.error("Permission request failed: " + e.getMessage(), e));
    }

    private void attachMessageListener(String nodeId) {
        if (nodeId == null || nodeId.isEmpty()) return;
        if (messageListenerAttached && nodeId.equals(listenerNodeId)) return;
        nodeApi.isWearAppInstalled(nodeId)
                .addOnSuccessListener(installed -> {
                    if (!Boolean.TRUE.equals(installed)) {
                        Logger.warn("Wearable app not installed on node: " + nodeId);
                        return;
                    }
                    messageApi.addListener(nodeId, messageListener)
                            .addOnSuccessListener(v -> {
                                messageListenerAttached = true;
                                listenerNodeId = nodeId;
                                Logger.info("Message listener added for node: " + nodeId);
                                queryDeviceStatus(nodeId);
                                subscribeStatus(nodeId);
                                sendCapabilities(nodeId, true);
                            })
                            .addOnFailureListener(e -> Logger.error("Add message listener failed: " + e.getMessage(), e));
                })
                .addOnFailureListener(e -> Logger.error("Check wear app failed: " + e.getMessage(), e));
    }

    private void handleIncomingMessage(String nodeId, byte[] message) {
        executor.execute(() -> {
            String payload = new String(message, StandardCharsets.UTF_8);
            Logger.info("Received message from wearable: " + payload);
            JsonObject json = null;
            try {
                json = gson.fromJson(payload, JsonObject.class);
            } catch (Exception ignored) {
            }
            if (isCapabilitiesRequest(json)) {
                sendCapabilities(nodeId, false);
                return;
            }
            String action = getString(json, "action");
            if (ACTION_SEARCH.equalsIgnoreCase(action)) {
                handleSearch(nodeId, json);
                return;
            }
            if (ACTION_LYRIC.equalsIgnoreCase(action) || ACTION_GET_LYRIC.equalsIgnoreCase(action)) {
                handleLyric(nodeId, json);
                return;
            }
            ResolveRequest request;
            try {
                request = gson.fromJson(payload, ResolveRequest.class);
            } catch (Exception e) {
                sendMessage(nodeId, gson.toJson(new ErrorResponse("Invalid request", null)));
                return;
            }
            requestProxy.resolve(request, new RequestProxy.ResolveCallback() {
                @Override
                public void onSuccess(String responseJson) {
                    sendMessage(nodeId, responseJson);
                }

                @Override
                public void onFailure(Exception e) {
                    sendMessage(nodeId, gson.toJson(new ErrorResponse(e.getMessage(), request)));
                }
            });
        });
    }

    private void sendMessage(String nodeId, String json) {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        messageApi.sendMessage(nodeId, data)
                .addOnSuccessListener(result -> Logger.info("Response sent to wearable"))
                .addOnFailureListener(e -> Logger.error("Send message failed: " + e.getMessage(), e));
    }

    private void sendCapabilities(String nodeId, boolean update) {
        if (nodeId == null || nodeId.isEmpty()) return;
        Map<String, Object> payload = buildCapabilitiesPayload(update ? ACTION_CAPABILITIES_UPDATE : ACTION_CAPABILITIES);
        sendMessage(nodeId, gson.toJson(payload));
    }

    private Map<String, Object> buildCapabilitiesPayload(String action) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", action);
        payload.put("code", 0);
        payload.put("message", "ok");
        Map<String, Object> data = scriptManager == null ? new HashMap<>() : scriptManager.getCapabilitiesSummary();
        payload.put("data", data);
        return payload;
    }

    public void notifyCapabilitiesChanged() {
        if (listenerNodeId != null) {
            sendCapabilities(listenerNodeId, true);
        }
    }

    private void handleSearch(String nodeId, JsonObject json) {
        String keyword = getString(json, "keyword");
        String platform = getString(json, "platform");
        int page = getInt(json, "page", 1);
        int pageSize = getInt(json, "pageSize", 20);
        if (keyword == null || keyword.trim().isEmpty()) {
            sendMessage(nodeId, gson.toJson(buildErrorPayload(ACTION_SEARCH, "Keyword missing")));
            return;
        }
        SearchService.SearchResult result = searchService.search(platform, keyword, page, pageSize);
        if (result == null) {
            sendMessage(nodeId, gson.toJson(buildErrorPayload(ACTION_SEARCH, "Search failed")));
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("platform", result.platform);
        data.put("page", result.page);
        data.put("pageSize", result.pageSize);
        data.put("total", result.total);
        data.put("results", result.results);
        sendMessage(nodeId, gson.toJson(buildSuccessPayload(ACTION_SEARCH, data, buildInfo("search", result.platform, keyword, result.page, result.pageSize))));
    }

    private void handleLyric(String nodeId, JsonObject json) {
        String platform = getString(json, "platform");
        String id = getString(json, "id");
        if (id == null || id.isEmpty()) {
            id = getString(json, "songid");
        }
        if (platform == null || platform.trim().isEmpty()) {
            sendMessage(nodeId, gson.toJson(buildErrorPayload(ACTION_LYRIC, "Platform missing")));
            return;
        }
        if (id == null || id.trim().isEmpty()) {
            sendMessage(nodeId, gson.toJson(buildErrorPayload(ACTION_LYRIC, "SongId missing")));
            return;
        }
        LyricService.LyricResult result = lyricService.getLyric(platform, id);
        Map<String, Object> data = new HashMap<>();
        data.put("lyric", result.lyric);
        data.put("tlyric", result.tlyric);
        data.put("rlyric", result.rlyric);
        data.put("lxlyric", result.lxlyric);
        sendMessage(nodeId, gson.toJson(buildSuccessPayload(ACTION_LYRIC, data, buildInfo("lyric", platform, id, null, null))));
    }

    private Map<String, Object> buildSuccessPayload(String action, Map<String, Object> data, Map<String, Object> info) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", action);
        payload.put("code", 0);
        payload.put("message", "ok");
        payload.put("data", data);
        if (info != null) {
            payload.put("info", info);
        }
        return payload;
    }

    private Map<String, Object> buildErrorPayload(String action, String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", action);
        payload.put("code", -1);
        payload.put("message", message == null ? "error" : message);
        Map<String, Object> error = new HashMap<>();
        error.put("message", payload.get("message"));
        payload.put("error", error);
        return payload;
    }

    private Map<String, Object> buildInfo(String action, String platform, String keywordOrId, Integer page, Integer pageSize) {
        Map<String, Object> info = new HashMap<>();
        info.put("action", action);
        if (platform != null) info.put("platform", platform);
        if (keywordOrId != null) {
            if ("search".equals(action)) info.put("keyword", keywordOrId);
            else info.put("songId", keywordOrId);
        }
        if (page != null) info.put("page", page);
        if (pageSize != null) info.put("pageSize", pageSize);
        return info;
    }

    private void queryDeviceStatus(String nodeId) {
        nodeApi.query(nodeId, DataItem.ITEM_CONNECTION)
                .addOnSuccessListener(result -> {
                    updateConnectedStatus(result.isConnected);
                    Logger.info("Wearable connection: " + result.isConnected);
                })
                .addOnFailureListener(e -> Logger.error("Query connection status failed: " + e.getMessage(), e));
        nodeApi.query(nodeId, DataItem.ITEM_BATTERY)
                .addOnSuccessListener(result -> Logger.info("Wearable battery: " + result.battery))
                .addOnFailureListener(e -> Logger.error("Query battery status failed: " + e.getMessage(), e));
        nodeApi.query(nodeId, DataItem.ITEM_CHARGING)
                .addOnSuccessListener(result -> Logger.info("Wearable charging: " + result.isCharging))
                .addOnFailureListener(e -> Logger.error("Query charging status failed: " + e.getMessage(), e));
        nodeApi.query(nodeId, DataItem.ITEM_WEARING)
                .addOnSuccessListener(result -> Logger.info("Wearable wearing: " + result.isWearing))
                .addOnFailureListener(e -> Logger.error("Query wearing status failed: " + e.getMessage(), e));
        nodeApi.query(nodeId, DataItem.ITEM_SLEEP)
                .addOnSuccessListener(result -> Logger.info("Wearable sleeping: " + result.isSleeping))
                .addOnFailureListener(e -> Logger.error("Query sleep status failed: " + e.getMessage(), e));
    }

    private void subscribeStatus(String nodeId) {
        if (nodeId == null || nodeId.isEmpty()) return;
        if (nodeId.equals(subscribedNodeId)) return;
        subscribedNodeId = nodeId;
        subscribeItem(nodeId, DataItem.ITEM_CONNECTION, "connection");
        subscribeItem(nodeId, DataItem.ITEM_CHARGING, "charging");
        subscribeItem(nodeId, DataItem.ITEM_WEARING, "wearing");
        subscribeItem(nodeId, DataItem.ITEM_SLEEP, "sleep");
    }

    private void subscribeItem(String nodeId, DataItem item, String label) {
        nodeApi.subscribe(nodeId, item, statusListener)
                .addOnSuccessListener(v -> Logger.info("Subscribe " + label + " status ok"))
                .addOnFailureListener(e -> Logger.error("Subscribe " + label + " status failed: " + e.getMessage(), e));
    }

    private void unsubscribeStatus(String nodeId) {
        if (nodeId == null || nodeId.isEmpty()) return;
        nodeApi.unsubscribe(nodeId, DataItem.ITEM_CONNECTION)
                .addOnFailureListener(e -> Logger.error("Unsubscribe connection failed: " + e.getMessage(), e));
        nodeApi.unsubscribe(nodeId, DataItem.ITEM_CHARGING)
                .addOnFailureListener(e -> Logger.error("Unsubscribe charging failed: " + e.getMessage(), e));
        nodeApi.unsubscribe(nodeId, DataItem.ITEM_WEARING)
                .addOnFailureListener(e -> Logger.error("Unsubscribe wearing failed: " + e.getMessage(), e));
        nodeApi.unsubscribe(nodeId, DataItem.ITEM_SLEEP)
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

    private boolean isCapabilityAction(String value) {
        if (value == null) return false;
        return ACTION_GET_CAPABILITIES.equalsIgnoreCase(value)
                || ACTION_CAPABILITIES.equalsIgnoreCase(value);
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
        final Object info;
        final Object error;

        ErrorResponse(String message, ResolveRequest request) {
            this.code = -1;
            this.message = message == null ? "Unknown error" : message;
            Map<String, Object> info = new HashMap<>();
            if (request != null) {
                info.put("platform", request.getSource());
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
}






