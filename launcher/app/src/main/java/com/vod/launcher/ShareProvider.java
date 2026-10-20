package com.vod.launcher;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Minimal provider that exposes files from the game save/base dir for sharing. Path = /<absolute path>. */
public class ShareProvider extends ContentProvider {
    public static final String AUTH = "com.vod.launcher.fileprovider";

    public static Uri uriFor(File f) {
        return new Uri.Builder().scheme("content").authority(AUTH).encodedPath(Uri.encode(f.getAbsolutePath(), "/")).build();
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        File f = new File(uri.getPath());
        try {
            String p = f.getCanonicalPath();
            String a = getContext().getFilesDir().getParentFile().getCanonicalPath();
            File ext = getContext().getExternalFilesDir(null);
            String b = ext != null ? ext.getCanonicalPath() : a;
            if (!(p.startsWith(a) || p.startsWith(b))) throw new FileNotFoundException("outside sandbox");
        } catch (java.io.IOException e) { throw new FileNotFoundException(e.getMessage()); }
        return f;
    }

    @Override public boolean onCreate() { return true; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri, String[] projection, String sel, String[] args, String sort) {
        try {
            File f = resolve(uri);
            MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
            c.addRow(new Object[]{f.getName(), f.length()});
            return c;
        } catch (FileNotFoundException e) { return null; }
    }
    @Override public String getType(Uri uri) { return "application/octet-stream"; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
