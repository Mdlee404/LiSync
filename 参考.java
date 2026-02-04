package mindrift.app.cheatnote;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.xiaomi.xms.wearable.Wearable;
import com.xiaomi.xms.wearable.auth.Permission;
import com.xiaomi.xms.wearable.node.Node;
import com.xiaomi.xms.wearable.node.NodeApi;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private EditText etTitle;
    private EditText etNote;
    private Button btnSend;

    private String connectedNodeId = null;
    private static final String TAG = "CheatNote";

    private ProgressDialog loadingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        Window w = getWindow();
        w.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        setContentView(R.layout.activity_main);

        etTitle = findViewById(R.id.et_title);
        etNote = findViewById(R.id.et_note);
        btnSend = findViewById(R.id.btn_send);

        btnSend.setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            String content = etNote.getText().toString().trim();

            if (TextUtils.isEmpty(title) || TextUtils.isEmpty(content)) {
                Toast.makeText(this, "标题和内容不能为空", Toast.LENGTH_SHORT).show();
                return;
            }

            if (connectedNodeId == null) {
                // 如果未连接，点击发送时弹出特定提示
                showConnectErrorDialog();
                // 同时也尝试后台重连
                checkConnection(false);
                return;
            }

            sendNoteToWatch(title, content);
        });

        // 启动时自动搜索设备，不弹窗干扰
        checkConnection(false);
    }

    /**
     * 发送笔记核心逻辑
     */
    private void sendNoteToWatch(String title, String content) {
        if (connectedNodeId == null) return;

        showLoading("正在发送...");

        try {
            JSONObject json = new JSONObject();
            json.put("action", "ADD");
            json.put("id", String.valueOf(System.currentTimeMillis()));
            json.put("title", title);
            json.put("content", content);

            Wearable.getMessageApi(getApplicationContext())
                    .sendMessage(connectedNodeId, json.toString().getBytes())
                    .addOnSuccessListener(result -> {
                        hideLoading();
                        Toast.makeText(MainActivity.this, "✅ 发送成功", Toast.LENGTH_SHORT).show();
                        etTitle.setText("");
                        etNote.setText("");
                    })
                    .addOnFailureListener(e -> {
                        hideLoading();
                        // 发送失败处理：提示检查状态 + 尝试唤醒
                        showSendErrorDialog();
                        // 尝试重新唤醒手环应用（自愈逻辑）
                        launchWatchApp();
                    });

        } catch (JSONException e) {
            hideLoading();
            e.printStackTrace();
        }
    }

    private void checkConnection(boolean isUserAction) {
        NodeApi api = Wearable.getNodeApi(getApplicationContext());
        api.getConnectedNodes().addOnSuccessListener(nodes -> {
            if (nodes != null && !nodes.isEmpty()) {
                Node device = nodes.get(0);
                connectedNodeId = device.id;

                // 连接成功，静默唤醒手环应用并申请权限
                launchWatchApp();
                Wearable.getAuthApi(getApplicationContext()).requestPermission(connectedNodeId, Permission.DEVICE_MANAGER);

                if (isUserAction) {
                    Toast.makeText(MainActivity.this, "已连接: " + device.name, Toast.LENGTH_SHORT).show();
                }
            } else {
                // 连接失败
                if (isUserAction) {
                    showConnectErrorDialog();
                }
            }
        }).addOnFailureListener(e -> {
            if (isUserAction) {
                showConnectErrorDialog();
            }
        });
    }

    // 封装唤醒逻辑
    private void launchWatchApp() {
        if (connectedNodeId != null) {
            Wearable.getNodeApi(getApplicationContext()).launchWearApp(connectedNodeId, "/pages/index");
        }
    }

    // 【修改点】连接失败提示框
    private void showConnectErrorDialog() {
        new AlertDialog.Builder(this)
                .setTitle("设备未连接")
                .setMessage("请连接设备并到「小米运动健康」App点击同步。")
                .setPositiveButton("已同步，重试", (d, w) -> {
                    checkConnection(true);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 【修改点】推送失败提示框
    private void showSendErrorDialog() {
        new AlertDialog.Builder(this)
                .setTitle("推送失败")
                .setMessage("请检查：\n1. 设备蓝牙是否连接正常\n2. 手环应用是否在前台运行\n\n系统已尝试重新唤醒手环应用。")
                .setPositiveButton("重试", (d, w) -> {
                    // 用户点击重试时，再次触发发送按钮的逻辑
                    btnSend.performClick();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showLoading(String msg) {
        if (loadingDialog == null) {
            loadingDialog = new ProgressDialog(this);
            loadingDialog.setCancelable(false);
        }
        loadingDialog.setMessage(msg);
        if (!loadingDialog.isShowing()) loadingDialog.show();
    }

    private void hideLoading() {
        if (loadingDialog != null) loadingDialog.dismiss();
    }
}