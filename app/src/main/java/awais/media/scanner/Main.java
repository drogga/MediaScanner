package awais.media.scanner;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentsContractCompat;
import androidx.documentfile.provider.TreeDocumentFile;
import androidx.multidex.BuildConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import awais.media.scanner.databinding.ActivityMainBinding;
import awais.media.scanner.utils.Filest;
import awais.media.scanner.utils.PermissionUtils;
import awais.media.scanner.utils.SharedPrefsUtils;
import awais.media.scanner.utils.Utils;

public final class Main extends Activity implements CompoundButton.OnCheckedChangeListener, View.OnClickListener, DirectoryChooser.OnFragmentInteractionListener {
    public static int itemsCount;
    private List<File> failedFiles;
    private List<String> scanItemsList = new ArrayList<>(0);
    private ArrayAdapter<String> scanItemsAdapter;
    private ActivityMainBinding mainBinding;
    private Resources resources;
    private String scanRunningText, outOfText;
    private AlertDialog scanRunningDialog;
    private SharedPrefsUtils sharedPrefsUtils;
    private boolean closeWhenFinishedChecked;
    private final DialogInterface.OnClickListener failedDialogButtonListener = (dialog, which) -> {
        if (failedFiles == null) return;
        if (which == DialogInterface.BUTTON_POSITIVE) {
            for (final File failedFile : failedFiles) Utils.runOnSingleItem(this, failedFile);
            Toast.makeText(this, R.string.running_failed_scan, Toast.LENGTH_SHORT).show();
        } else if (which == DialogInterface.BUTTON_NEUTRAL) {
            new AlertDialog.Builder(this)
                    .setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, failedFiles), null)
                    .setTitle(R.string.show_failed_dialog_title).setNeutralButton(R.string.ok, null).show();
        }
    };

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final String storageState = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(storageState) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(storageState)) {
            Utils.sdcardPathFile = Environment.getExternalStorageDirectory();
            Utils.sdcardPath = Utils.sdcardPathFile.getAbsolutePath();
        }

        resources = getResources();
        mainBinding = ActivityMainBinding.inflate(getLayoutInflater());
        sharedPrefsUtils = SharedPrefsUtils.getInstance(this);

        scanItemsList = sharedPrefsUtils.getPathItems();
        if (scanItemsList == null) scanItemsList = new ArrayList<>(0);
        scanItemsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, scanItemsList);

        scanRunningText = resources.getString(R.string.scan_running_text);
        outOfText = resources.getString(R.string.scan_items_left);

        scanRunningDialog = new AlertDialog.Builder(this).setTitle(R.string.scan_running_title).setMessage(scanRunningText).setCancelable(false).create();

        if (PermissionUtils.isPermissionsGranted(this)) doMainStuff();
        else PermissionUtils.requestPermissions(this);
    }

    @Override
    protected void onDestroy() {
        if (mainBinding != null) mainBinding = null;
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults) {
        if (requestCode != 17) super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        else if (PermissionUtils.isPermissionsGranted(this)) doMainStuff();
        else {
            Toast.makeText(this, R.string.permission_not_granted, Toast.LENGTH_SHORT).show();
            finishAffinity();
        }
    }

    @Override
    @SuppressLint("WrongConstant")
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        if (requestCode != 6969) super.onActivityResult(requestCode, resultCode, intent);
        else if (resultCode == RESULT_OK && intent != null) {
            final Uri data = intent.getData();
            final ContentResolver contentResolver = getContentResolver();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && data != null && DocumentsContractCompat.isTreeUri(data)) {
                contentResolver.takePersistableUriPermission(data, intent.getFlags() &
                                                                   (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
                final TreeDocumentFile documentFile = TreeDocumentFile.fromTreeUri(this, data);
                if (documentFile != null && documentFile.isDirectory()) {
                    onSelectDirectory(Filest.getFilePath(this, data));
                }
            }
        }
    }

    @Override
    public void onCheckedChanged(final CompoundButton v, final boolean isChecked) {
        if (v == mainBinding.cbAutoScan) {
            if (sharedPrefsUtils != null) sharedPrefsUtils.setAutoScanFilesChecked(isChecked);
            if (!isChecked) Utils.stopFileObserver(this);
            else if (!Utils.observerRunning) Utils.runFileObserver(this);
        } else if (v == mainBinding.cbWholeDevice) {
            if (sharedPrefsUtils != null) sharedPrefsUtils.setWholeDeviceScanChecked(isChecked);
            mainBinding.lvScanItems.setEnabled(!isChecked);
            mainBinding.lvScanItems.setAlpha(isChecked ? 0.617f : 1);
            mainBinding.btnPickPath.setEnabled(!isChecked);
        } else if (v == mainBinding.cbCloseWhenDone) {
            if (sharedPrefsUtils != null) sharedPrefsUtils.setCloseWhenFinishedChecked(isChecked);
            mainBinding.cbStartScan.setEnabled(!isChecked);
        } else if (v == mainBinding.cbStartScan) {
            if (sharedPrefsUtils != null) sharedPrefsUtils.setScanWhenStartChecked(isChecked);
            mainBinding.cbCloseWhenDone.setEnabled(!isChecked);
        }
    }

    @Override
    public void onClick(@NonNull final View v) {
        final int id = v.getId();

        if (v == mainBinding.btnPickPath || id == R.id.btnPickPath) {
            MediaScanner.showChooser(this);
            return;
        }

        if (v == mainBinding.btnScan || id == R.id.btnScan) {
            boolean ranScan = false;
            if (mainBinding.cbWholeDevice.isChecked() || sharedPrefsUtils != null && sharedPrefsUtils.isWholeDeviceScanChecked()) {
                ranScan = Utils.runWholeScan(this, false);
            } else if (scanItemsList != null) {
                final int filesSize = scanItemsList.size();
                if (filesSize > 0) {
                    final File[] filesToScan = new File[filesSize];
                    for (int i = 0; i < filesSize; i++) filesToScan[i] = new File(scanItemsList.get(i));
                    Utils.smartRun(this, filesToScan);
                    ranScan = true;
                }
            }
            if (ranScan) {
                scanRunningDialog.show();
                scanRunningDialog.setMessage(scanRunningText + itemsCount + ' ' + outOfText + ' ' + itemsCount);
            }
            return;
        }

        if (BuildConfig.DEBUG) Log.d("AWAISKING_APP", "unknown button clicked: " + v);
    }

    @Override
    public void onSelectDirectory(final String selectedPath) {
        if (Utils.isEmptyOrNull(selectedPath)) {
            Toast.makeText(this, R.string.error_adding_dir, Toast.LENGTH_SHORT).show();
            return;
        }

        final String scanPath = new File(selectedPath).getAbsolutePath();
        if (selectedPath.equals(Utils.sdcardPath) || scanPath.equals(new File(Utils.sdcardPath).getAbsolutePath())) {
            if (mainBinding != null) mainBinding.cbWholeDevice.setChecked(true);
            if (sharedPrefsUtils != null) sharedPrefsUtils.setWholeDeviceScanChecked(true);
        } else if (!scanItemsList.contains(scanPath)) {
            scanItemsList.add(scanPath);
            if (scanItemsAdapter != null) scanItemsAdapter.notifyDataSetChanged();
            if (sharedPrefsUtils != null) sharedPrefsUtils.setPathItems(scanItemsList);
        }
    }

    private void doMainStuff() {
        if (sharedPrefsUtils == null) sharedPrefsUtils = SharedPrefsUtils.getInstance(this);

        if (!sharedPrefsUtils.isAndroid11DialogShown()) {
            final AlertDialog alertDialog = new AlertDialog.Builder(this)
                                                    .setTitle(R.string.permission_a11_title)
                                                    .setMessage(R.string.permission_a11_text)
                                                    .setPositiveButton(R.string.ok, null)
                                                    .setCancelable(false)
                                                    .create();
            alertDialog.setOnShowListener(dialog -> {
                final View[] btnOK = {alertDialog.findViewById(android.R.id.button1)};
                if (btnOK[0] == null) btnOK[0] = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (btnOK[0] == null) return;
                btnOK[0].setEnabled(false);
                btnOK[0].postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        btnOK[0].removeCallbacks(this);
                        btnOK[0].setEnabled(true);
                    }
                }, 3000);
            });
            alertDialog.setCancelable(false);
            alertDialog.setCanceledOnTouchOutside(false);
            alertDialog.show();
            if (!BuildConfig.DEBUG) sharedPrefsUtils.setAndroid11DialogShown(true);
        }

        setContentView(mainBinding.getRoot());

        final boolean autoScanChecked = sharedPrefsUtils.isAutoScanFilesChecked();
        final boolean deviceScanChecked = sharedPrefsUtils.isWholeDeviceScanChecked();
        final boolean scanWhenStartChecked = sharedPrefsUtils.isScanWhenStartChecked();
        closeWhenFinishedChecked = sharedPrefsUtils.isCloseWhenFinishedChecked();

        if (autoScanChecked) Utils.runFileObserver(this);

        Utils.filesScanListener = new FilesScanListener() {
            @Override
            public void failedFiles(final Stack<File> failedFilesStack) {
                dismissScanningDialog();

                final int failedSize = failedFilesStack != null ? failedFilesStack.size() : 0;
                if (failedSize > 0) {
                    failedFiles = new ArrayList<>(failedFilesStack);
                    Utils.reset();
                    runOnUiThread(() -> {
                        final AlertDialog alertDialog = new AlertDialog.Builder(Main.this)
                                                                .setTitle(R.string.failed_files_title)
                                                                .setMessage(resources.getQuantityString(R.plurals.failed_files_text, failedSize, failedSize))
                                                                .setNeutralButton(R.string.show_failed_files, failedDialogButtonListener)
                                                                .setPositiveButton(R.string.yes, failedDialogButtonListener)
                                                                .setNegativeButton(R.string.no, null).show();
                        alertDialog.getButton(DialogInterface.BUTTON_NEUTRAL)
                                   .setOnClickListener(v -> failedDialogButtonListener.onClick(alertDialog, DialogInterface.BUTTON_NEUTRAL));
                    });
                }
            }

            @Override
            public void itemsLeft(final int count, final int failedSize) {
                runOnUiThread(() -> {
                    if (scanRunningDialog != null && count > 0)
                        scanRunningDialog.setMessage(scanRunningText + count + ' ' + outOfText + ' ' + itemsCount);
                    else {
                        if (failedSize > 0) Toast.makeText(Main.this, R.string.completed_with_errors, Toast.LENGTH_SHORT).show();
                        else {
                            Toast.makeText(Main.this, R.string.scan_completed, Toast.LENGTH_SHORT).show();
                            if (closeWhenFinishedChecked) finishAffinity();
                        }
                        dismissScanningDialog();
                    }
                });
            }
        };
        Utils.reset();

        mainBinding.cbCloseWhenDone.setEnabled(!scanWhenStartChecked);
        mainBinding.cbStartScan.setEnabled(scanWhenStartChecked || !closeWhenFinishedChecked);

        mainBinding.cbWholeDevice.setChecked(deviceScanChecked);
        mainBinding.cbAutoScan.setChecked(autoScanChecked);
        mainBinding.cbStartScan.setChecked(scanWhenStartChecked);
        mainBinding.cbCloseWhenDone.setChecked(!scanWhenStartChecked && closeWhenFinishedChecked);

        mainBinding.btnPickPath.setEnabled(!deviceScanChecked);
        mainBinding.lvScanItems.setEnabled(!deviceScanChecked);
        mainBinding.lvScanItems.setAlpha(deviceScanChecked ? 0.617f : 1);

        mainBinding.lvScanItems.setAdapter(scanItemsAdapter);
        mainBinding.lvScanItems.setOnItemClickListener((parent, view, position, id) -> new AlertDialog.Builder(this)
                                                                                               .setTitle(R.string.delete_item_title).setMessage(resources.getString(R.string.delete_item_text, scanItemsList.get(position)))
                                                                                               .setIcon(android.R.drawable.stat_sys_warning).setNegativeButton(R.string.no, null)
                                                                                               .setPositiveButton(R.string.yes, (dialog, which) -> {
                                                                                                   scanItemsList.remove(position);
                                                                                                   if (sharedPrefsUtils != null) sharedPrefsUtils.setPathItems(scanItemsList);
                                                                                                   scanItemsAdapter.notifyDataSetChanged();
                                                                                               }).show());

        mainBinding.cbAutoScan.setOnCheckedChangeListener(this);
        mainBinding.cbStartScan.setOnCheckedChangeListener(this);
        mainBinding.cbWholeDevice.setOnCheckedChangeListener(this);
        mainBinding.cbCloseWhenDone.setOnCheckedChangeListener(this);

        mainBinding.btnScan.setOnClickListener(this);
        mainBinding.btnPickPath.setOnClickListener(this);
        if (scanWhenStartChecked) mainBinding.btnScan.performClick();
    }

    private void dismissScanningDialog() {
        if (scanRunningDialog != null && scanRunningDialog.isShowing())
            runOnUiThread(() -> scanRunningDialog.dismiss());
    }
}