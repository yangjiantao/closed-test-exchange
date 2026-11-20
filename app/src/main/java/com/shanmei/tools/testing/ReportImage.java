package com.shanmei.tools.testing;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class ReportImage {
    private static final int WIDTH = 1080;
    private static final int MARGIN = 42;
    private static final int GAP = 20;
    private static final int GRID_TOP = 396;
    private static final int TILE_HEIGHT = 254;

    record Item(InstalledApps.App app, TestStore.Entry entry) {}

    private record Summary(int total, int opened) {
        static Summary from(List<Item> items, long now) {
            int opened = 0;
            for (Item item : items) {
                if (item.entry().openedOn(now)) opened++;
            }
            return new Summary(items.size(), opened);
        }

        int pending() {
            return total - opened;
        }
    }

    static String name(long now) {
        String time = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ROOT).format(new Date(now));
        return "Testing_" + time + ".png";
    }

    static File sharedFile(Context context, String name) {
        return new File(new File(context.getCacheDir(), "reports"), name);
    }

    static void cache(Context context, Bitmap image, String name) throws IOException {
        File file = sharedFile(context, name);
        File directory = file.getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create report directory");
        try (var output = new FileOutputStream(file)) {
            if (!image.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IOException("Cannot write PNG");
            }
        } catch (IOException failure) {
            file.delete();
            throw failure;
        }
    }

    static Uri sharedUri(Context context, String name) {
        return new Uri.Builder().scheme("content").authority(context.getPackageName() + ".reports")
                .appendPath(name).build();
    }

    static void saveToPictures(Context context, String name) throws IOException {
        var resolver = context.getContentResolver();
        var values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Testing");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (imageUri == null) throw new IOException("Cannot create media entry");
        try {
            copyTo(context, name, imageUri);
            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            if (resolver.update(imageUri, values, null, null) != 1) {
                throw new IOException("Cannot publish media entry");
            }
        } catch (IOException | RuntimeException failure) {
            resolver.delete(imageUri, null, null);
            throw failure;
        }
    }

    static void copyTo(Context context, String name, Uri target) throws IOException {
        try (var source = new FileInputStream(sharedFile(context, name));
             OutputStream output = context.getContentResolver().openOutputStream(target)) {
            if (output == null) throw new IOException("Cannot open report destination");
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = source.read(buffer)) != -1) output.write(buffer, 0, count);
        }
    }

    static Bitmap render(Context context, List<Item> items, long now, int columns) {
        int rows = (items.size() + columns - 1) / columns;
        int height = GRID_TOP + rows * (TILE_HEIGHT + GAP) - GAP + MARGIN;
        var image = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888);
        var canvas = new Canvas(image);
        var paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        canvas.drawColor(Ui.PAPER);
        drawHeader(context, canvas, paint, TestStore.day(now), Summary.from(items, now));

        int tileWidth = (WIDTH - 2 * MARGIN - (columns - 1) * GAP) / columns;
        for (int i = 0; i < items.size(); i++) {
            int x = MARGIN + (i % columns) * (tileWidth + GAP);
            int y = GRID_TOP + (i / columns) * (TILE_HEIGHT + GAP);
            drawItem(canvas, paint, items.get(i), now, x, y, tileWidth);
        }
        return image;
    }

    private static void drawHeader(Context context, Canvas canvas, TextPaint paint, String date, Summary summary) {
        drawIcon(canvas, context.getDrawable(R.drawable.app_icon), MARGIN, 36, 58);
        draw(canvas, paint, context.getString(R.string.app_name), 118, 77, 34, Ui.INK, true);
        draw(canvas, paint, "测试报告", MARGIN, 167, 68, Ui.INK, true);
        draw(canvas, paint, date + " · 本机记录", MARGIN, 221, 34, Ui.MUTED, false);

        fill(canvas, paint, MARGIN, 252, WIDTH - MARGIN, 370, 28, Ui.DARK);
        draw(canvas, paint, "今日已打开", 68, 289, 28, Ui.WHITE, false);
        draw(canvas, paint, summary.opened() + " / " + summary.total(), 68, 344, 55, Ui.MINT, true);
        paint.setTextAlign(Paint.Align.RIGHT);
        draw(canvas, paint, "待打开 " + summary.pending() + " 个", WIDTH - 70, 320, 33, Ui.WHITE, false);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private static void drawItem(Canvas canvas, TextPaint paint, Item item, long now, int x, int y, int width) {
        int center = x + width / 2;
        fill(canvas, paint, x, y, x + width, y + TILE_HEIGHT, 26, Ui.WHITE);
        drawIcon(canvas, item.app().icon(), center - 35, y + 19, 70);
        drawName(canvas, paint, item.app().name(), x + 13, y + 99, width - 26);

        boolean opened = item.entry().openedOn(now);
        int color = opened ? Ui.GREEN : Ui.AMBER;
        String status = item.entry().todayStatus(now);
        fill(canvas, paint, center - 91, y + 173, center + 91, y + 212, 10,
                opened ? Ui.PALE : Ui.PALE_AMBER);
        paint.setTextAlign(Paint.Align.CENTER);
        draw(canvas, paint, status, center, y + 202, 29, color, true);
        draw(canvas, paint, "累计 " + item.entry().days() + " 天", center, y + 240, 30, Ui.GREEN, true);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private static void drawIcon(Canvas canvas, Drawable icon, int x, int y, int size) {
        if (icon == null) return;
        var original = icon.copyBounds();
        icon.setBounds(x, y, x + size, y + size);
        icon.draw(canvas);
        icon.setBounds(original);
    }

    private static void drawName(Canvas canvas, TextPaint paint, String name, int x, int y, int width) {
        var namePaint = new TextPaint(paint);
        namePaint.setColor(Ui.INK);
        namePaint.setTypeface(Typeface.DEFAULT_BOLD);
        namePaint.setTextSize(34);
        var layout = StaticLayout.Builder.obtain(name, 0, name.length(), namePaint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build();
        canvas.save();
        canvas.translate(x, y);
        layout.draw(canvas);
        canvas.restore();
    }

    private static void fill(Canvas canvas, Paint paint, float left, float top, float right,
                             float bottom, int radius, int color) {
        paint.setColor(color);
        canvas.drawRoundRect(new RectF(left, top, right, bottom), radius, radius, paint);
    }

    private static void draw(Canvas canvas, TextPaint paint, String value, int x, int y, int size,
                             int color, boolean bold) {
        paint.setColor(color);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextSize(size);
        canvas.drawText(value, x, y, paint);
    }

    private ReportImage() {}
}
