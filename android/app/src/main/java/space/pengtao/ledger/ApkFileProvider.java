package space.pengtao.ledger;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 自实现的 FileProvider（无 AndroidX）：对外提供下载好的 APK 文件，
 * 供系统安装器通过 content:// URI 读取。
 */
public class ApkFileProvider extends ContentProvider {

    private static final String AUTHORITY_SUFFIX = ".apkfile";

    public static Uri getUriForFile(Context ctx, File file) {
        return Uri.parse("content://" + ctx.getPackageName() + AUTHORITY_SUFFIX + "/" + file.getName());
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File dir = new File(getContext().getCacheDir(), "apk");
        File file = new File(dir, uri.getLastPathSegment());
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
