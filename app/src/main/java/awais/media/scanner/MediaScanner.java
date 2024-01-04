package awais.media.scanner;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.multidex.MultiDexApplication;

public final class MediaScanner extends MultiDexApplication {
    @Override
    public void onCreate() {
        super.onCreate();
    }

    public static void showChooser(@NonNull final Main main) {
        final StorageManager sm = (StorageManager) main.getSystemService(Context.STORAGE_SERVICE);

        final Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) intent = sm.getPrimaryStorageVolume().createOpenDocumentTreeIntent();
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).putExtra("android.provider.extra.INITIAL_URI",
                                                                           DocumentsContract.buildRootUri("com.android.externalstorage.documents", "primary"));
        else intent = null;

        if (intent != null) intent.putExtra("android.provider.extra.SHOW_ADVANCED", true)
                                  .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && intent != null)
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        if (intent != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) main.startActivityForResult(intent, 6969);
        else DirectoryChooser.showChooser(main);
    }
}