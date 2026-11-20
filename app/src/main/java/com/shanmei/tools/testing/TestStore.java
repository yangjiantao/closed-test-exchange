package com.shanmei.tools.testing;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class TestStore extends SQLiteOpenHelper {
    static final int GOAL_DAYS = 14;

    record Entry(String packageName, int days, long lastOpened) {
        boolean openedOn(long date) {
            return lastOpened > 0 && day(lastOpened).equals(day(date));
        }

        String progressLabel() {
            return days + " / " + GOAL_DAYS + " 天";
        }

        String todayStatus(long date) {
            return openedOn(date) ? "今日已打开" : "今日待打开";
        }
    }

    private static TestStore instance;

    static synchronized TestStore get(Context context) {
        if (instance == null) instance = new TestStore(context.getApplicationContext());
        return instance;
    }

    private TestStore(Context context) {
        super(context, "app_data.db", null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("""
                CREATE TABLE app_data (
                    packageName TEXT PRIMARY KEY NOT NULL,
                    usedDays INTEGER NOT NULL DEFAULT 0,
                    lastUsedTime INTEGER NOT NULL DEFAULT 0,
                    released INTEGER NOT NULL DEFAULT 0,
                    lastCheckReleaseTime INTEGER NOT NULL DEFAULT 0,
                    managed INTEGER NOT NULL DEFAULT 1
                )
                """);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 1 && newVersion == 2) {
            db.execSQL("ALTER TABLE app_data ADD COLUMN managed INTEGER NOT NULL DEFAULT 1");
        } else {
            throw new IllegalStateException("Missing migration from " + oldVersion + " to " + newVersion);
        }
    }

    List<Entry> entries() {
        var entries = new ArrayList<Entry>();
        try (Cursor rows = getReadableDatabase().query(
                "app_data", null, "managed = 1", null, null, null, "usedDays DESC, packageName ASC")) {
            while (rows.moveToNext()) entries.add(read(rows));
        }
        return entries;
    }

    void setManaged(String packageName, boolean managed) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("managed", managed ? 1 : 0);
        String[] key = {packageName};
        db.beginTransaction();
        try {
            int updated = db.update("app_data", values, "packageName = ?", key);
            if (managed && updated == 0) {
                values.put("packageName", packageName);
                db.insertOrThrow("app_data", null, values);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    void recordOpen(String packageName, long time) {
        SQLiteDatabase db = getWritableDatabase();
        String[] key = {packageName};
        db.beginTransaction();
        try (Cursor rows = db.query("app_data", null, "packageName = ? AND managed = 1",
                key, null, null, null)) {
            if (rows.moveToFirst()) {
                Entry entry = read(rows);
                ContentValues values = new ContentValues();
                values.put("lastUsedTime", time);
                if (!entry.openedOn(time)) values.put("usedDays", entry.days() + 1);
                db.update("app_data", values, "packageName = ?", key);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static Entry read(Cursor rows) {
        return new Entry(rows.getString(0), rows.getInt(1), rows.getLong(2));
    }

    static String day(long timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date(timestamp));
    }
}
