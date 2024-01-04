package androidx.documentfile.provider;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.File;
import java.util.Objects;

import awais.media.scanner.utils.Filest;

/**
 * Representation of a document backed by either a
 * {@link android.provider.DocumentsProvider} or a raw file on disk. This is a
 * utility class designed to emulate the traditional {@link File} interface. It
 * offers a simplified view of a tree of documents, but it has substantial
 * overhead. For optimal performance and a richer feature set, use the
 * {@link android.provider.DocumentsContract} methods and constants directly.
 * <p>
 * There are several differences between documents and traditional files:
 * <ul>
 * <li>Documents express their display name and MIME type as separate fields,
 * instead of relying on file extensions. Some documents providers may still
 * choose to append extensions to their display names, but that's an
 * implementation detail.
 * <li>A single document may appear as the child of multiple directories, so it
 * doesn't inherently know who its parent is. That is, documents don't have a
 * strong notion of path. You can easily traverse a tree of documents from
 * parent to child, but not from child to parent.
 * <li>Each document has a unique identifier within that provider. This
 * identifier is an <em>opaque</em> implementation detail of the provider, and
 * as such it must not be parsed.
 * </ul>
 * <p>
 * Before using this class, first consider if you really need access to an
 * entire subtree of documents. The principle of least privilege dictates that
 * you should only ask for access to documents you really need. If you only need
 * the user to pick a single file, use {@link Intent#ACTION_OPEN_DOCUMENT} or
 * {@link Intent#ACTION_GET_CONTENT}. If you want to let the user pick multiple
 * files, add {@link Intent#EXTRA_ALLOW_MULTIPLE}. If you only need the user to
 * save a single file, use {@link Intent#ACTION_CREATE_DOCUMENT}. If you use
 * these APIs, you can pass the resulting {@link Intent#getData()} into
 * {fromSingleUri(Context, Uri)} to work with that document.
 * <p>
 * If you really do need full access to an entire subtree of documents, start by
 * launching {@link Intent#ACTION_OPEN_DOCUMENT_TREE} to let the user pick a
 * directory. Then pass the resulting {@link Intent#getData()} into
 * {@link #fromTreeUri(Context, Uri)} to start working with the user selected
 * tree.
 * <p>
 * As you navigate the tree of DocumentFile instances, you can always use
 * {getUri()} to obtain the Uri representing the underlying document for
 * that object, for use with {@link ContentResolver#openInputStream(Uri)}, etc.
 * <p>
 * To simplify your code on devices running
 * {@link android.os.Build.VERSION_CODES#KITKAT} or earlier, you can use
 * {fromFile(File)} which emulates the behavior of a
 * {@link android.provider.DocumentsProvider}.
 *
 * @see android.provider.DocumentsProvider
 * @see android.provider.DocumentsContract
 */
public class TreeDocumentFile {
    private static final String TAG = "DocumentFile";
    private final Context mContext;
    private final Uri mUri;

    TreeDocumentFile(final Context context, final Uri uri) {
        super();
        mContext = context;
        mUri = uri;
    }

    /**
     * Indicates if this file represents a <em>directory</em>.
     *
     * @return {@code true} if this file is a directory, {@code false}
     *         otherwise.
     * @see android.provider.DocumentsContract.Document#MIME_TYPE_DIR
     */
    public boolean isDirectory() {
        return isDirectory(mContext, mUri);
    }

    public static boolean isDirectory(final Context context, final Uri self) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            final String filePath = Filest.getFilePath(context, self);
            return filePath != null && new File(filePath).isDirectory();
        }
        return DocumentsContract.Document.MIME_TYPE_DIR.equals(queryForString(context, self));
    }

    @Nullable
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private static String queryForString(@NonNull final Context context, final Uri self) {
        final ContentResolver resolver = context.getContentResolver();

        try (final Cursor c = resolver.query(self, new String[]{DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
            if (Objects.requireNonNull(c).moveToFirst() && !c.isNull(0)) return c.getString(0);
            return null;
        } catch (final Exception e) {
            Log.w(TAG, "Failed query: " + e);
            return null;
        }
    }

    /**
     * Create a {@link TreeDocumentFile} representing the document tree rooted at
     * the given {@link Uri}. This is only useful on devices running
     * {@link android.os.Build.VERSION_CODES#LOLLIPOP} or later, and will return
     * {@code null} when called on earlier platform versions.
     *
     * @param treeUri the {@link Intent#getData()} from a successful
     *            {@link Intent#ACTION_OPEN_DOCUMENT_TREE} request.
     */
    @Nullable
    public static TreeDocumentFile fromTreeUri(@NonNull final Context context, @NonNull final Uri treeUri) {
        if (Build.VERSION.SDK_INT < 21) return null;

        String documentId = DocumentsContractCompat.getTreeDocumentId(treeUri);
        if (DocumentsContractCompat.isDocumentUri(context, treeUri)) documentId = DocumentsContractCompat.getDocumentId(treeUri);
        if (documentId == null) throw new IllegalArgumentException("Could not get document ID from Uri: " + treeUri);
        final Uri treeDocumentUri = DocumentsContractCompat.buildDocumentUriUsingTree(treeUri, documentId);
        if (treeDocumentUri == null) throw new NullPointerException("Failed to build documentUri from a tree: " + treeUri);
        return new TreeDocumentFile(context, treeDocumentUri);
    }
}