package mindrift.app.music.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import java.text.DateFormat;
import java.util.Date;
import mindrift.app.music.App;
import mindrift.app.music.R;
import mindrift.app.music.utils.Logger;
import mindrift.app.music.wearable.XiaomiWearableManager;

public class HomeFragment extends Fragment {
    private static final String RENEW_LICENSE_BASE_URL =
            "https://ifdian.net/order/create?product_type=1&product_type=0&plan_id=920d48ecfb7411f0afe95254001e7c00&sku=%5B%7B%22sku_id%22%3A%2292151a54fb7411f0bb485254001e7c00%22,%22count%22%3A1%7D%5D&viokrz_ex=0&month=0&remark=";
    private XiaomiWearableManager wearableManager;
    private TextView serviceStatusText;
    private TextView lastUpdatedText;
    private TextView deviceStatusText;
    private TextView licenseTitleText;
    private TextView licenseStatusText;
    private TextView licenseDeviceIdText;
    private Button renewLicenseButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Logger.info("HomeFragment onViewCreated");
        App app = (App) requireActivity().getApplication();
        wearableManager = app.getWearableManager();

        serviceStatusText = view.findViewById(R.id.home_text_service_status);
        lastUpdatedText = view.findViewById(R.id.home_text_last_updated);
        deviceStatusText = view.findViewById(R.id.home_text_device_status);
        licenseTitleText = view.findViewById(R.id.home_text_license_title);
        licenseStatusText = view.findViewById(R.id.home_text_license_status);
        licenseDeviceIdText = view.findViewById(R.id.home_text_license_device_id);
        renewLicenseButton = view.findViewById(R.id.home_button_renew_license);
        
        Logger.info("HomeFragment licenseStatusText=" + (licenseStatusText != null ? "found" : "null"));

        wearableManager.setLicenseExpiryListener((expireAt, title, message) -> {
            Logger.info("HomeFragment received license expiry: " + title + " - " + message);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() ->
                    applyLicenseStatus(expireAt, title, message, wearableManager.getPendingLicenseDeviceId()));
        });

        view.findViewById(R.id.home_button_refresh).setOnClickListener(v -> {
            if (wearableManager != null) {
                wearableManager.refreshNodes();
            }
            refreshData();
        });
        view.findViewById(R.id.home_button_renew_license).setOnClickListener(v -> openRenewLicensePage());
        if (licenseDeviceIdText != null) {
            licenseDeviceIdText.setOnClickListener(v -> copyLicenseDeviceId());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (wearableManager != null) {
            wearableManager.setLicenseExpiryListener(null);
        }
        serviceStatusText = null;
        lastUpdatedText = null;
        deviceStatusText = null;
        licenseTitleText = null;
        licenseStatusText = null;
        licenseDeviceIdText = null;
        renewLicenseButton = null;
    }

    private void refreshData() {
        if (!isAdded()) return;
        if (lastUpdatedText != null) {
            String updated = getString(R.string.updated_at, DateFormat.getDateTimeInstance().format(new Date()));
            lastUpdatedText.setText(updated);
        }
        if (serviceStatusText != null) {
            serviceStatusText.setText(getString(R.string.server_active));
        }
        updateDeviceStatus();
        updateLicenseStatus();
    }

    private void updateDeviceStatus() {
        if (wearableManager == null || deviceStatusText == null) return;
        boolean wearableInstalled = wearableManager.isWearableAppInstalledForUi();
        boolean serviceConnected = wearableManager.isServiceConnected();
        String nodeId = wearableManager.getCurrentNodeId();
        String nodeName = wearableManager.getCurrentNodeName();
        Boolean connected = wearableManager.getConnectedStatus();
        String status;
        if (!wearableInstalled) {
            status = getString(R.string.device_status_no_app);
        } else if (!serviceConnected) {
            status = getString(R.string.device_status_service_disconnected);
        } else if (nodeId == null || nodeId.isEmpty()) {
            status = getString(R.string.device_status_no_device);
        } else {
            String name = (nodeName == null || nodeName.trim().isEmpty()) ? nodeId : nodeName;
            if (connected == null || connected) {
                status = getString(R.string.device_status_connected, name);
            } else {
                status = getString(R.string.device_status_disconnected, name);
            }
        }
        deviceStatusText.setText(status);
    }

    private void updateLicenseStatus() {
        if (wearableManager == null) return;
        long expireAt = wearableManager.getPendingLicenseExpireAt();
        String title = wearableManager.getPendingLicenseTitle();
        String message = wearableManager.getPendingLicenseMessage();
        String deviceId = wearableManager.getPendingLicenseDeviceId();
        if (expireAt > 0 && message != null && !message.trim().isEmpty()) {
            applyLicenseStatus(expireAt, title, message, deviceId);
            return;
        }
        if (licenseTitleText != null) {
            licenseTitleText.setText(getString(R.string.home_license_title_default));
        }
        if (licenseStatusText != null) {
            licenseStatusText.setText(getString(R.string.license_status_loading));
            licenseStatusText.setTextColor(android.graphics.Color.parseColor("#2196F3"));
        }
        applyLicenseDeviceId(deviceId);
    }

    private void applyLicenseStatus(long expireAt, String title, String message, String deviceId) {
        if (licenseTitleText == null || licenseStatusText == null || !isAdded()) return;
        boolean expired = expireAt > 0 && expireAt <= System.currentTimeMillis();
        licenseTitleText.setText(title == null || title.trim().isEmpty()
                ? getString(R.string.home_license_title_default)
                : title);
        licenseStatusText.setText(getString(
                expired ? R.string.home_license_expired_at : R.string.home_license_expire_at,
                message
        ));
        licenseStatusText.setTextColor(expired
                ? android.graphics.Color.RED
                : android.graphics.Color.parseColor("#2196F3"));
        applyLicenseDeviceId(deviceId);
        Logger.info("HomeFragment license status updated: title=" + title + " message=" + message);
    }

    private void applyLicenseDeviceId(String deviceId) {
        String safeDeviceId = deviceId == null ? "" : deviceId.trim();
        if (licenseDeviceIdText != null) {
            licenseDeviceIdText.setText(safeDeviceId.isEmpty()
                    ? getString(R.string.home_license_device_id_unknown)
                    : getString(R.string.home_license_device_id_value, safeDeviceId));
            licenseDeviceIdText.setEnabled(!safeDeviceId.isEmpty());
        }
        if (renewLicenseButton != null) {
            renewLicenseButton.setEnabled(!safeDeviceId.isEmpty());
        }
    }

    private void copyLicenseDeviceId() {
        if (!isAdded() || wearableManager == null) return;
        String deviceId = wearableManager.getPendingLicenseDeviceId();
        if (deviceId == null || deviceId.trim().isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("deviceId", deviceId));
        Toast.makeText(requireContext(), getString(R.string.license_device_id_copied), Toast.LENGTH_SHORT).show();
    }

    private void openRenewLicensePage() {
        if (!isAdded()) return;
        String deviceId = wearableManager == null ? null : wearableManager.getPendingLicenseDeviceId();
        String url = RENEW_LICENSE_BASE_URL + Uri.encode(deviceId == null ? "" : deviceId);
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Logger.warn("Open renew license page failed: " + e.getMessage());
            Toast.makeText(requireContext(), getString(R.string.renew_license_open_failed), Toast.LENGTH_SHORT).show();
        }
    }
}
