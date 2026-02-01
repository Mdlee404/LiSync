package mindrift.app.lisynchronization.wearable;

import android.content.Context;
import com.google.gson.Gson;
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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import mindrift.app.lisynchronization.core.proxy.RequestProxy;
import mindrift.app.lisynchronization.model.ResolveRequest;
import mindrift.app.lisynchronization.utils.Logger;

public class XiaomiWearableManager {
    private static final Permission[] REQUIRED_PERMISSIONS = new Permission[]{
            Permission.DEVICE_MANAGER,
            Permission.NOTIFY
    };
    private final Context context;
    private final RequestProxy requestProxy;
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

    public XiaomiWearableManager(Context context, RequestProxy requestProxy) {
        this.context = context.getApplicationContext();
        this.requestProxy = requestProxy;
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
                            })
                            .addOnFailureListener(e -> Logger.error("Add message listener failed: " + e.getMessage(), e));
                })
                .addOnFailureListener(e -> Logger.error("Check wear app failed: " + e.getMessage(), e));
    }

    private void handleIncomingMessage(String nodeId, byte[] message) {
        executor.execute(() -> {
            String payload = new String(message, StandardCharsets.UTF_8);
            Logger.info("Received message from wearable: " + payload);
            ResolveRequest request = gson.fromJson(payload, ResolveRequest.class);
            requestProxy.resolve(request, new RequestProxy.ResolveCallback() {
                @Override
                public void onSuccess(String responseJson) {
                    sendMessage(nodeId, responseJson);
                }

                @Override
                public void onFailure(Exception e) {
                    sendMessage(nodeId, gson.toJson(new ErrorResponse(e.getMessage())));
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
        final Object error;
        ErrorResponse(String message) {
            this.error = new ErrorDetail(message == null ? "Unknown error" : message);
        }
    }

    private static class ErrorDetail {
        final String message;
        ErrorDetail(String message) {
            this.message = message;
        }
    }
}






