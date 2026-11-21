package com.shanmei.tools.testing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import androidx.test.platform.app.InstrumentationRegistry;

import java.io.InputStream;
import java.util.Collections;
import java.util.Locale;

import org.junit.Test;

public class ReportImageTest {
    @Test
    public void reportCanBeRenderedAndReadFromShareUriInBothLanguages() throws Exception {
        for (Locale locale : new Locale[]{Locale.SIMPLIFIED_CHINESE, Locale.US}) {
            Context context = localizedContext(locale);
            long now = System.currentTimeMillis();
            String name = writeReport(context, now);
            try {
                Uri uri = ReportImage.sharedUri(context, name);
                assertEquals("image/png", context.getContentResolver().getType(uri));
                assertPng(context, uri);
            } finally {
                ReportImage.sharedFile(context, name).delete();
            }
        }
    }

    @Test
    public void reportCanBeSavedToPictures() throws Exception {
        assumeTrue(Build.VERSION.SDK_INT >= 29);
        Context context = localizedContext(Locale.US);
        String name = writeReport(context, System.currentTimeMillis());
        ContentResolver resolver = context.getContentResolver();
        Uri images = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        Uri saved = null;
        try {
            ReportImage.saveToPictures(context, name);
            try (var rows = resolver.query(images, new String[]{MediaStore.Images.Media._ID},
                    MediaStore.Images.Media.DISPLAY_NAME + " = ?", new String[]{name}, null)) {
                assertNotNull(rows);
                assertTrue(rows.moveToFirst());
                saved = ContentUris.withAppendedId(images, rows.getLong(0));
            }
            assertPng(context, saved);
        } finally {
            if (saved != null) resolver.delete(saved, null, null);
            ReportImage.sharedFile(context, name).delete();
        }
    }

    private Context localizedContext(Locale locale) {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration configuration = new Configuration(app.getResources().getConfiguration());
        configuration.setLocale(locale);
        return app.createConfigurationContext(configuration);
    }

    private String writeReport(Context context, long now) throws Exception {
        String name = ReportImage.name(now);
        var app = new InstalledApps.App("example.checkin", "Sample app", context.getDrawable(R.drawable.app_icon), now);
        var entry = new TestStore.Entry(app.packageName(), 1, now);
        Bitmap report = ReportImage.render(context, Collections.singletonList(new ReportImage.Item(app, entry)), now, 3);
        try {
            ReportImage.cache(context, report, name);
        } finally {
            report.recycle();
        }
        return name;
    }

    private void assertPng(Context context, Uri uri) throws Exception {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            assertNotNull(bitmap);
            try {
                assertEquals(1080, bitmap.getWidth());
                assertTrue(bitmap.getHeight() > 0);
            } finally {
                bitmap.recycle();
            }
        }
    }
}
