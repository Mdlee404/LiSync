package mindrift.app.lisynchronization.wearable;

import android.content.Context;
import com.google.gson.Gson;
import com.xiaomi.xms.wearable.Wearable;
import com.xiaomi.xms.wearable.auth.AuthApi;
import com.xiaomi.xms.wearable.auth.Permission;
import com.xiaomi.xms.wearable.message.MessageApi;
import com.xiaomi.xms.wearable.message.OnMessageReceivedListener;
import com.xiaomi.xms.wearable.node.Node;
import com.xiaomi.xms.wearable.node.NodeApi;
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
    private final Context context;
    private final RequestProxy requestProxy;
    private final NodeApi nodeApi;
    private final MessageApi messageApi;
    private final AuthApi authApi;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Gson gson = new Gson();
    private String currentNodeId;
    private boolean started = false;

    private final OnMessageReceivedListener messageListener = new OnMessageReceivedListener() {
        @Override
        public void onMessageReceived(String nodeId, byte[] message) {
            handleIncomingMessage(nodeId, message);
        }
    };

    public XiaomiWearableManager(Context context, RequestProxy requestProxy) {
        this.context = context.getApplicationContext();
        this.requestProxy = requestProxy;
        this.nodeApi = Wearable.getNodeApi(this.context);
        this.messageApi = Wearable.getMessageApi(this.context);
        this.authApi = Wearable.getAuthApi(this.context);
    }

    public void start() {
        if (started) return;
        started = true;
        if (!isWearableAppInstalled()) {
            Logger.warn("Wearable app not installed, skip wearable init");
            return;
        }
        refreshNodes();
    }

    public void stop() {
        if (!started) return;
        started = false;
        if (currentNodeId != null) {
            messageApi.removeListener(currentNodeId);
        }
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
                        return;
                    }
                    Node node = nodes.get(0);
                    currentNodeId = node.id;
                    ensurePermission(currentNodeId);
                })
                .addOnFailureListener(e -> Logger.error("Get connected nodes failed: " + e.getMessage(), e));
    }

    private void ensurePermission(String nodeId) {
        authApi.checkPermission(nodeId, Permission.DEVICE_MANAGER)
                .addOnSuccessListener(granted -> {
                    if (Boolean.TRUE.equals(granted)) {
                        attachMessageListener(nodeId);
                    } else {
                        requestPermission(nodeId);
                    }
                })
                .addOnFailureListener(e -> Logger.error("Permission check failed: " + e.getMessage(), e));
    }

    private void requestPermission(String nodeId) {
        authApi.requestPermission(nodeId, Permission.DEVICE_MANAGER)
                .addOnSuccessListener(perms -> attachMessageListener(nodeId))
                .addOnFailureListener(e -> Logger.error("Permission request failed: " + e.getMessage(), e));
    }

    private void attachMessageListener(String nodeId) {
        messageApi.addListener(nodeId, messageListener)
                .addOnSuccessListener(v -> Logger.info("Message listener added for node: " + nodeId))
                .addOnFailureListener(e -> Logger.error("Add message listener failed: " + e.getMessage(), e));
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

    private boolean isWearableAppInstalled() {
        return isPackageInstalled("com.xiaomi.wearable") || isPackageInstalled("com.mi.health");
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






