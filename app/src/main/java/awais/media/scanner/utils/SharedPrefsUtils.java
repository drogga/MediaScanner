package awais.media.scanner.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SharedPrefsUtils {
    private static final String KEY_CLOSE_WHEN_FINISHED = "finishClose";
    private static final String KEY_SCAN_WHEN_STARTED = "startScan";
    private static final String KEY_A11_DIALOG_SHOWN = "a11ToastShown";
    private static final String KEY_WHOLE_DEVICE = "wholeDevice";
    private static final String KEY_AUTO_SCAN = "autoScan";
    private static final String KEY_PATHS = "filePaths";

    private static SharedPrefsUtils prefsUtilsInstance;

    private final SharedPreferences prefs;

    private SharedPrefsUtils(@NonNull final Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    public static SharedPrefsUtils getInstance(final Context context) {
        if (prefsUtilsInstance == null) prefsUtilsInstance = new SharedPrefsUtils(context);
        return prefsUtilsInstance;
    }

    public boolean isWholeDeviceScanChecked() {
        return prefs == null || prefs.getBoolean(KEY_WHOLE_DEVICE, true);
    }

    public boolean isScanWhenStartChecked() {
        return prefs == null || prefs.getBoolean(KEY_SCAN_WHEN_STARTED, false);
    }

    public boolean isCloseWhenFinishedChecked() {
        return !isScanWhenStartChecked() && (prefs == null || prefs.getBoolean(KEY_CLOSE_WHEN_FINISHED, false));
    }

    public boolean isAutoScanFilesChecked() {
        return prefs == null || prefs.getBoolean(KEY_AUTO_SCAN, false);
    }

    public boolean isAndroid11DialogShown() {
        return prefs == null || prefs.getBoolean(KEY_A11_DIALOG_SHOWN, false);
    }

    public void setWholeDeviceScanChecked(final boolean isChecked) {
        if (prefs != null) prefs.edit().putBoolean(KEY_WHOLE_DEVICE, isChecked).apply();
    }

    public void setAutoScanFilesChecked(final boolean isChecked) {
        if (prefs != null) prefs.edit().putBoolean(KEY_AUTO_SCAN, isChecked).apply();
    }

    public void setScanWhenStartChecked(final boolean isChecked) {
        if (prefs != null) prefs.edit().putBoolean(KEY_SCAN_WHEN_STARTED, isChecked).apply();
    }

    public void setCloseWhenFinishedChecked(final boolean isChecked) {
        if (prefs != null) prefs.edit().putBoolean(KEY_CLOSE_WHEN_FINISHED, isChecked).apply();
    }

    public void setAndroid11DialogShown(final boolean dialogShown) {
        if (prefs != null) prefs.edit().putBoolean(KEY_A11_DIALOG_SHOWN, dialogShown).apply();
    }

    public List<String> getPathItems() {
        ArrayList<String> pathItems = null;
        if (prefs != null && prefs.contains(KEY_PATHS)) {
            final Set<String> paths = prefs.getStringSet(KEY_PATHS, null);
            if (paths != null) pathItems = new ArrayList<>(paths);
        }
        return pathItems;
    }

    public void setPathItems(final List<String> pathItems) {
        if (prefs != null && pathItems != null && !pathItems.isEmpty())
            prefs.edit().putStringSet(KEY_PATHS, new LinkedHashSet<>(pathItems)).apply();
    }
}