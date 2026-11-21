package com.shanmei.tools.testing;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public final class AutoTestService extends Service {
    static final String QUEUE = "packages";
    static final int STEP_SECONDS = 15;
    private static final long STEP_MS = STEP_SECONDS * 1000L;
    static boolean running;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ArrayList<String> queue;
    private int index;
    private int opened;
    private long deadline;

    private WindowManager windows;
    private LinearLayout overlay;
    private TextView progressText;
    private TextView appName;
    private ProgressBar progress;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (running) return START_NOT_STICKY;
        queue = intent == null ? null : intent.getStringArrayListExtra(QUEUE);
        if (queue == null || queue.isEmpty() || !Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            showOverlay();
        } catch (RuntimeException exception) {
            Toast.makeText(this, R.string.overlay_failed, Toast.LENGTH_SHORT).show();
            stopSelf();
            return START_NOT_STICKY;
        }

        running = true;
        overlay.post(this::nextApp);
        return START_NOT_STICKY;
    }

    private void showOverlay() {
        windows = getSystemService(WindowManager.class);
        overlay = Ui.column(this);
        overlay.setBackground(Ui.rounded(this, Ui.DARK, 18));
        overlay.setElevation(Ui.dp(this, 10));
        Ui.pad(overlay, 16, 10, 12, 12);

        var top = Ui.row(this);
        progressText = Ui.text(this, "", 14, Ui.MINT, true);
        top.addView(progressText, new LinearLayout.LayoutParams(0, -2, 1));
        TextView stop = Ui.button(this, getString(R.string.stop), false);
        stop.setOnClickListener(view -> finish(false));
        top.addView(stop);
        overlay.addView(top);

        appName = Ui.text(this, "", 15, Ui.WHITE, true);
        appName.setSingleLine(true);
        appName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        overlay.addView(appName);
        progress = Ui.progress(this, 1000, 0, Ui.MINT);
        overlay.addView(progress, new LinearLayout.LayoutParams(-1, Ui.dp(this, 8)));

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        var params = new WindowManager.LayoutParams(Ui.dp(this, 242), -2, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = Ui.dp(this, 14);
        params.y = Ui.dp(this, 46);
        windows.addView(overlay, params);
    }

    private void nextApp() {
        if (!running) return;
        if (index == queue.size()) {
            finish(true);
            return;
        }
        String packageName = queue.get(index++);
        InstalledApps.App app = InstalledApps.find(this, packageName);
        appName.setText(app == null ? packageName : app.name());
        if (InstalledApps.openAndRecord(this, packageName)) {
            opened++;
        } else {
            appName.setText(getString(R.string.sequence_app_failed, appName.getText()));
        }
        deadline = SystemClock.uptimeMillis() + STEP_MS;
        updateProgress();
    }

    private void updateProgress() {
        long remaining = Math.max(0, deadline - SystemClock.uptimeMillis());
        progressText.setText(getString(R.string.sequence_progress, index, queue.size(), (remaining + 999) / 1000));
        progress.setProgress((int) ((STEP_MS - remaining) * 1000 / STEP_MS));
        if (remaining > 0) {
            handler.postDelayed(this::updateProgress, Math.min(remaining, 200));
        } else {
            nextApp();
        }
    }

    private void finish(boolean complete) {
        handler.removeCallbacksAndMessages(null);
        running = false;
        if (complete) {
            int total = queue.size();
            String message = getResources().getQuantityString(R.plurals.sequence_complete, total, opened, total);
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
        try {
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } finally {
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        if (overlay != null && overlay.isAttachedToWindow()) windows.removeViewImmediate(overlay);
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopSelf();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
