package {{PACKAGE}};

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * FileProvider ringan tanpa dependensi AndroidX. Hanya membagikan file di bawah cacheDir
 * (dipakai untuk hasil foto kamera pada upload file).
 *
 * URI: content://<package>.fileprovider/cache/<path relatif>
 */
public class WebifyFileProvider extends ContentProvider {

    private static final String ROOT = "cache";

    public static String authority(Context ctx) {
        return ctx.getPackageName() + ".fileprovider";
    }

    public static Uri getUriForFile(Context ctx, File file) throws IOException {
        String base = ctx.getCacheDir().getCanonicalPath();
        String path = file.getCanonicalPath();
        if (!path.startsWith(base + File.separator)) {
            throw new IllegalArgumentException("File di luar cacheDir: " + path);
        }
        String rel = path.substring(base.length() + 1);
        return new Uri.Builder().scheme("content").authority(authority(ctx))
                .appendPath(ROOT).appendEncodedPath(Uri.encode(rel, "/")).build();
    }

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context, info);
        if (info.exported) throw new SecurityException("WebifyFileProvider tidak boleh exported");
        if (!info.grantUriPermissions) throw new SecurityException("WebifyFileProvider wajib grantUriPermissions");
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    private File fileFor(Uri uri) throws FileNotFoundException {
        String path = uri.getEncodedPath();
        if (path == null) throw new FileNotFoundException("URI kosong");
        String prefix = "/" + ROOT + "/";
        if (!path.startsWith(prefix)) throw new FileNotFoundException("Root tidak dikenal: " + uri);
        String rel = Uri.decode(path.substring(prefix.length()));
        try {
            File base = getContext().getCacheDir().getCanonicalFile();
            File f = new File(base, rel).getCanonicalFile();
            if (!f.getPath().startsWith(base.getPath() + File.separator)) {
                throw new SecurityException("Path traversal ditolak");
            }
            return f;
        } catch (IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = fileFor(uri);
        int pfdMode = ParcelFileDescriptor.parseMode(mode);
        if ((pfdMode & ParcelFileDescriptor.MODE_CREATE) != 0) {
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
        }
        return ParcelFileDescriptor.open(f, pfdMode);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File f;
        try { f = fileFor(uri); } catch (FileNotFoundException e) { return null; }
        String[] cols = projection != null ? projection : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor c = new MatrixCursor(cols, 1);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = f.getName();
            else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.length();
        }
        c.addRow(row);
        return c;
    }

    @Override
    public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name != null) {
            int dot = name.lastIndexOf('.');
            if (dot >= 0) {
                String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(dot + 1).toLowerCase());
                if (mime != null) return mime;
            }
        }
        return "application/octet-stream";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("insert tidak didukung");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        try { return fileFor(uri).delete() ? 1 : 0; } catch (FileNotFoundException e) { return 0; }
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("update tidak didukung");
    }
}
