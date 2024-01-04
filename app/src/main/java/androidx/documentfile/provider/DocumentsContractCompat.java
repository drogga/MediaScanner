package androidx.documentfile.provider;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsProvider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public final class DocumentsContractCompat {
    private static final String PATH_TREE = "tree";

    /**
     * Checks if the given URI represents a {@link Document} backed by a
     * {@link DocumentsProvider}.
     *
     * @see DocumentsContract#isDocumentUri(Context, Uri)
     */
    public static boolean isDocumentUri(@NonNull final Context context, @Nullable final Uri uri) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && DocumentsContract.isDocumentUri(context, uri);
    }

    /**
     * Checks if the given URI represents a {@link Document} tree.
     *
     * @see DocumentsContract#isTreeUri(Uri)
     */
    public static boolean isTreeUri(@NonNull final Uri uri) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            // While "tree" Uris were added in 21, the check was only (publicly) added in 24
            final List<String> paths = uri.getPathSegments();
            return (paths.size() >= 2 && PATH_TREE.equals(paths.get(0)));
        }
        return DocumentsContract.isTreeUri(uri);
    }

    /**
     * Extract the {@link Document#COLUMN_DOCUMENT_ID} from the given URI.
     *
     * @see DocumentsContract#getDocumentId(Uri)
     */
    @Nullable
    public static String getDocumentId(@NonNull final Uri documentUri) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ? DocumentsContract.getDocumentId(documentUri) : null;
    }

    /**
     * Extract the via {@link Document#COLUMN_DOCUMENT_ID} from the given URI.
     *
     * @see DocumentsContract#getTreeDocumentId(Uri)
     */
    public static String getTreeDocumentId(@NonNull final Uri documentUri) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? DocumentsContract.getTreeDocumentId(documentUri) : null;
    }

    /**
     * Build URI representing the target {@link Document#COLUMN_DOCUMENT_ID} in
     * a document provider. When queried, a provider will return a single row
     * with columns defined by {@link Document}.
     */
    @Nullable
    public static Uri buildDocumentUriUsingTree(@NonNull final Uri treeUri, @NonNull final String documentId) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId) : null;
    }
}