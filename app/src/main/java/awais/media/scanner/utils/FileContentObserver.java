package awais.media.scanner.utils;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;

import androidx.annotation.NonNull;

import java.io.File;

abstract class FileContentObserver extends ContentObserver {
    private static final String[] PROJECTION = new String[]{"_data"};
    private final ContentResolver contentResolver;

    FileContentObserver(final Handler handler, @NonNull final ContentResolver contentResolver) {
        super(handler);
        this.contentResolver = contentResolver;
    }

    @Override
    public void onChange(final boolean selfChange, final Uri uri) {
        // Log.d("AWAISKING_APP", "onChange: " + uri);

        try (final Cursor cursor = contentResolver.query(uri, PROJECTION, null, null, null)) {
            if (cursor != null && cursor.moveToLast()) {
                final String path = cursor.getString(0);
                if (!Utils.isEmptyOrNull(path)) onPath(new File(path));
            }
        }
    }

    protected abstract void onPath(final File file);
}