package awais.media.scanner.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import awais.media.scanner.BuildConfig;
import awais.media.scanner.FilesScanListener;
import awais.media.scanner.Main;
import awais.media.scanner.R;

public final class Utils {
    private static int filesSize = -1;
    private static FileContentObserver fileContentObserver;
    private static ContentResolver contentResolver;
    private static Handler observerHandler;
    private static HandlerThread handlerThread;
    public static boolean observerRunning;
    public static String sdcardPath = '/' + "sdcard";
    public static File sdcardPathFile = new File(sdcardPath);
    public static FilesScanListener filesScanListener;
    private final static List<File> observerFilesList = new ArrayList<>();
    private final static Intent JB_INTENT = new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.fromFile(Environment.getExternalStorageDirectory()));
    private final static Intent KK_INTENT = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
    private final static Stack<File> failedFilesStack = new Stack<>();
    private final static MediaScannerConnection.OnScanCompletedListener scanCompletedListener = (path, uri) -> {
        if (!isEmptyOrNull(path) && isEmptyOrNull(uri)) failedFilesStack.push(new File(path));

        filesSize = filesSize - 1;

        final int failedSize = failedFilesStack.size();
        filesScanListener.itemsLeft(filesSize, failedSize);

        if (filesSize <= 0 && filesScanListener != null && failedSize > 0) {
            filesScanListener.failedFiles(failedFilesStack);
            filesSize = -1;
        }
    };

    public static void smartRun(final Context context, final File... files) {
        reset();
        if (files != null && files.length > 0) {
            filesSize = files.length;
            Main.itemsCount = filesSize;
            final String[] filesToScan = new String[filesSize];
            for (int i = 0; i < filesSize; i++) filesToScan[i] = files[i].getAbsolutePath();
            MediaScannerConnection.scanFile(context, filesToScan, null, scanCompletedListener);
        }
    }

    public static void reset() {
        filesSize = -1;
        failedFilesStack.clear();
    }

    public static void runOnSingleItem(final Context context, final File file) {
        if (file == null) return;
        if (file.isFile()) runOnSingleFile(context, file);
        else if (file.isDirectory()) runOnSingleDirectory(context, file);
    }

    private static void runOnSingleFile(final Context context, final File file) {
        if (file == null) return;
        context.sendBroadcast(Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT
                              ? JB_INTENT : KK_INTENT.setData(Uri.fromFile(file.getAbsoluteFile())));
    }

    private static void runOnSingleDirectory(final Context context, final File file) {
        if (file == null || !file.isDirectory()) return;
        final File[] files = file.listFiles();
        if (files != null) for (final File listFile : files) runOnSingleFile(context, listFile);
    }

    public static void stopFileObserver(final Context context) {
        if (contentResolver == null) contentResolver = context.getContentResolver();
        if (fileContentObserver != null) {
            contentResolver.unregisterContentObserver(fileContentObserver);
            observerRunning = false;
        }
        observerHandler = null;
        if (handlerThread != null && handlerThread.quit()) observerRunning = false;
    }

    public static void runFileObserver(final Context context) {
        if (observerRunning) return;
        observerRunning = true;

        if (contentResolver == null) contentResolver = context.getContentResolver();

        if (observerHandler == null) {
            synchronized (observerFilesList) {
                if (handlerThread != null) {
                    try {
                        handlerThread.quit();
                    } catch (final Exception e) {
                        // ignore
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        try {
                            handlerThread.quitSafely();
                        } catch (final Exception ex) {
                            // ignore
                        }
                    }
                    handlerThread = null;
                }
                handlerThread = new HandlerThread("content_observer");
                handlerThread.start();
                observerHandler = new Handler(handlerThread.getLooper());
            }
        }

        fileContentObserver = new FileContentObserver(observerHandler, contentResolver) {
            @Override
            protected void onPath(final File file) {
                if (BuildConfig.DEBUG) Log.d("AWAISKING_APP", "observer_file: " + file);

                boolean found = false;
                if (observerFilesList.contains(file)) found = true;
                else for (final File file1 : observerFilesList)
                    if (file.getAbsolutePath().equals(file1.getAbsolutePath())) found = true;

                if (!found) observerFilesList.add(file);

                for (int i = 0; i < observerFilesList.size(); i++) {
                    final File obserableFile = observerFilesList.get(i);
                    if (obserableFile == null || !obserableFile.exists())
                        observerFilesList.remove(obserableFile);
                }
                final String[] observablePaths = new String[observerFilesList.size()];
                for (int i = 0; i < observablePaths.length; i++) observablePaths[i] = observerFilesList.get(i).getAbsolutePath();
                MediaScannerConnection.scanFile(context, observablePaths, null, null);
            }
        };

        contentResolver.registerContentObserver(MediaStore.Files.getContentUri("internal"), true, fileContentObserver);
        contentResolver.registerContentObserver(MediaStore.Files.getContentUri("external"), true, fileContentObserver);
        contentResolver.registerContentObserver(MediaStore.Files.getContentUri("external_primary"), true, fileContentObserver);
    }

    public static boolean runWholeScan(final Context context, boolean ranScan) {
        File file = Environment.getExternalStorageDirectory();
        if (file == null || !file.exists()) file = new File(Utils.sdcardPath);
        if (!file.exists()) Toast.makeText(context, R.string.failed_whole_scan, Toast.LENGTH_SHORT).show();
        else {
            ranScan = true;
            File[] files = {file};
            if (file.isDirectory()) {
                final File[] listFiles = file.listFiles();
                if (listFiles != null && listFiles.length > 0) {
                    files = new File[listFiles.length + 1];
                    int lastIndex = 0;
                    for (final File listFile : listFiles)
                        if (!listFile.isHidden() && listFile.getName().charAt(0) != '.')
                            files[lastIndex++] = listFile;
                    files[lastIndex] = file;
                    final File[] newFiles = new File[lastIndex];
                    System.arraycopy(files, 0, newFiles, 0, lastIndex);
                    files = newFiles;
                }
            }
            Utils.smartRun(context, files);
        }
        return ranScan;
    }

    @SuppressWarnings({"ConstantConditions", "StringEqualsEmptyString", "SizeReplaceableByIsEmpty"})
    public static boolean isEmptyOrNull(final Object object) {
        if (object == null) return true;
        String str = null;

        if (object instanceof String) str = (String) object;
        else if (object instanceof Uri) str = String.valueOf(object);

        if (str == null || "".equals(str) || str.length() < 1 || str.isEmpty()) return true;
        str = str.trim();
        return str == null || "".equals(str) || str.length() < 1 || str.isEmpty();
    }
}