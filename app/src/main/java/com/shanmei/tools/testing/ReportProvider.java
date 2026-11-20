package com.shanmei.tools.testing;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

public final class ReportProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) {
        return file(uri) == null ? null : "image/png";
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args,
                                  String sortOrder) {
        File report = file(uri);
        if (report == null) return null;
        String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        var result = new MatrixCursor(columns, 1);
        var row = result.newRow();
        for (String column : columns) {
            row.add(switch (column) {
                case OpenableColumns.DISPLAY_NAME -> report.getName();
                case OpenableColumns.SIZE -> report.length();
                default -> null;
            });
        }
        return result;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File report = file(uri);
        if (!"r".equals(mode) || report == null) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(report, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private File file(Uri uri) {
        String name = uri.getLastPathSegment();
        if (uri.getPathSegments().size() != 1 || name == null
                || !name.matches("Testing_[0-9]{8}_[0-9]{6}_[0-9]{3}\\.png")) return null;
        File file = ReportImage.sharedFile(getContext(), name);
        return file.isFile() ? file : null;
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }
}
