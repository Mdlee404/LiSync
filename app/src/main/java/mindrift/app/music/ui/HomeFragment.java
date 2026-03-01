package mindrift.app.music.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import java.text.DateFormat;
import java.util.Date;
import mindrift.app.music.App;
import mindrift.app.music.R;
import mindrift.app.music.wearable.XiaomiWearableManager;

public class HomeFragment extends Fragment {
    private XiaomiWearableManager wearableManager;
    private TextView serviceStatusText;
    private TextView lastUpdatedText;
    private TextView deviceStatusText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        App app = (App) requireActivity().getApplication();
        wearableManager = app.getWearableManager();

        serviceStatusText = view.findViewById(R.id.home_text_service_status);
        lastUpdatedText = view.findViewById(R.id.home_text_last_updated);
        deviceStatusText = view.findViewById(R.id.home_text_device_status);

        view.findViewById(R.id.home_button_refresh).setOnClickListener(v -> {
            if (wearableManager != null) {
                wearableManager.refreshNodes();
            }
            refreshData();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        serviceStatusText = null;
        lastUpdatedText = null;
        deviceStatusText = null;
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
}
