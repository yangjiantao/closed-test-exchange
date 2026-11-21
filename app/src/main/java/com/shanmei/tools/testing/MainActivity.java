package com.shanmei.tools.testing;

import static com.shanmei.tools.testing.TestStore.GOAL_DAYS;
import static com.shanmei.tools.testing.Ui.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public final class MainActivity extends Activity {
    private static final int OVERLAY_PERMISSION = 10;
    private static final int CREATE_REPORT = 11;
    private static final int APP_COLUMNS = 4;

    private enum Page {
        APPS(R.string.app_list), TESTS(R.string.test_checklist);

        final int title;
        Page(int title) { this.title = title; }
    }

    private enum Sort {
        PACKAGE(R.string.sort_package), REMAINING(R.string.sort_remaining), TODAY(R.string.sort_pending);
        final int title;
        Sort(int title) { this.title = title; }
    }

    private final ExecutorService reports = Executors.newSingleThreadExecutor();
    private final ExecutorService playChecks = Executors.newFixedThreadPool(2);
    private final Handler playCheckEvents = new Handler(Looper.getMainLooper());

    private Page page = Page.APPS;
    private Sort sort = Sort.PACKAGE;
    private List<InstalledApps.App> catalog = java.util.Collections.emptyList();
    private TestStore store;
    private ScrollView scroll;
    private TextView playCheckStatus;
    private Future<?> playCheck;
    private AlertDialog playWarning;
    private int playCheckId;
    private String pendingReport;
    private boolean autoPermissionRequested;
    private boolean exporting;

    @Override
    public void onCreate(Bundle previous) {
        super.onCreate(previous);
        store = TestStore.get(this);
        if (previous != null) {
            page = switch (previous.getString("page", Page.APPS.name())) {
                case "TESTS", "REPORT" -> Page.TESTS;
                default -> Page.APPS;
            };
            sort = Sort.valueOf(previous.getString("sort", Sort.PACKAGE.name()));
            autoPermissionRequested = previous.getBoolean("autoPermissionRequested");
            pendingReport = previous.getString("pendingReport");
        }
        configureInsets();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (page == Page.APPS) catalog = InstalledApps.scan(this);
        render(false);
        if (autoPermissionRequested && Settings.canDrawOverlays(this)) {
            autoPermissionRequested = false;
            startAutoTest();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("page", page.name());
        state.putString("sort", sort.name());
        state.putBoolean("autoPermissionRequested", autoPermissionRequested);
        state.putString("pendingReport", pendingReport);
    }

    @Override
    protected void onPause() {
        cancelPlayCheck();
        if (playWarning != null) playWarning.dismiss();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        reports.shutdown();
        playChecks.shutdownNow();
        super.onDestroy();
    }

    private void configureInsets() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().setNavigationBarContrastEnforced(false);
        } else {
            getWindow().setStatusBarColor(PAPER);
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                getWindow().setNavigationBarColor(WHITE);
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            } else {
                getWindow().setNavigationBarColor(DARK);
            }
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    private void navigate(Page target) {
        if (page == target) return;
        cancelPlayCheck();
        page = target;
        if (page == Page.APPS) catalog = InstalledApps.scan(this);
        render(false);
    }

    private void render(boolean keepPosition) {
        int previousOffset = keepPosition && scroll != null ? scroll.getScrollY() : 0;
        playCheckStatus = null;
        LinearLayout root = column(this);
        root.setBackgroundColor(PAPER);
        root.setFocusableInTouchMode(true);
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                var bars = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                        | android.view.WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }
        setContentView(root);
        root.requestFocus();

        LinearLayout header = row(this);
        pad(header, 14, 11, 14, 9);
        var mark = new ImageView(this);
        mark.setImageResource(R.drawable.app_icon);
        mark.setBackground(rounded(this, DARK, 9));
        mark.setClipToOutline(true);
        header.addView(mark, new LinearLayout.LayoutParams(dp(this, 27), dp(this, 27)));
        TextView brand = text(this, getString(R.string.app_name), 16, INK, true);
        pad(brand, 8, 0, 0, 0);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(text(this, date(this, System.currentTimeMillis()), 12, MUTED, false));
        root.addView(header);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout body = column(this);
        pad(body, 14, 5, 14, 16);
        scroll.addView(body);
        boolean reportAvailable = false;
        switch (page) {
            case APPS -> renderApps(body);
            case TESTS -> reportAvailable = renderTests(body);
        }

        if (page == Page.TESTS) {
            var actions = column(this);
            actions.setBackgroundColor(WHITE);
            pad(actions, 14, 7, 14, 2);
            TextView report = button(this, getString(R.string.report_open), true);
            report.setEnabled(reportAvailable);
            if (!reportAvailable) report.setAlpha(0.48f);
            report.setOnClickListener(view -> createReport());
            actions.addView(report, new LinearLayout.LayoutParams(-1, -2));
            root.addView(actions);
        }

        var navigation = row(this);
        navigation.setBackgroundColor(WHITE);
        pad(navigation, 8, 6, 8, 6);
        for (Page target : Page.values()) {
            boolean active = target == page;
            TextView tab = button(this, getString(target.title), active ? PALE : WHITE, active ? GREEN : MUTED);
            tab.setSelected(active);
            tab.setOnClickListener(view -> navigate(target));
            var params = new LinearLayout.LayoutParams(0, -2, 1);
            params.setMargins(dp(this, 4), 0, dp(this, 4), 0);
            navigation.addView(tab, params);
        }
        root.addView(navigation);
        if (previousOffset > 0) scroll.post(() -> scroll.scrollTo(0, previousOffset));
    }

    private void renderApps(LinearLayout body) {
        Set<String> managed = store.entries().stream().map(TestStore.Entry::packageName)
                .collect(Collectors.toSet());
        managed.retainAll(catalog.stream().map(InstalledApps.App::packageName).collect(Collectors.toSet()));
        body.addView(text(this, getString(R.string.app_list), 25, INK, true));
        var counts = row(this);
        counts.addView(text(this, getString(R.string.installed_count, catalog.size()), 12, MUTED, false));
        TextView selectedCount = text(this, getString(R.string.selected_count, managed.size()), 12, GREEN, true);
        pad(selectedCount, 10, 0, 0, 0);
        selectedCount.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        counts.addView(selectedCount);
        append(body, counts, 3);
        if (catalog.isEmpty()) {
            empty(body, getString(R.string.no_launchable_apps));
            return;
        }

        var tiles = new ArrayList<View>();
        for (InstalledApps.App app : catalog) {
            String packageName = app.packageName();
            var card = new FrameLayout(this);
            LinearLayout content = appTile(app, 11);
            content.setBackground(null);
            card.addView(content, new FrameLayout.LayoutParams(-1, -2));
            var check = text(this, "✓", 12, WHITE, true);
            check.setGravity(Gravity.CENTER);
            check.setBackground(rounded(this, GREEN, 20));
            check.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            var mark = new FrameLayout.LayoutParams(dp(this, 17), dp(this, 17), Gravity.TOP | Gravity.END);
            mark.topMargin = dp(this, 5);
            mark.setMarginEnd(dp(this, 5));
            card.addView(check, mark);
            card.setContentDescription(app.name());
            card.setFocusable(true);
            card.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setClassName(CompoundButton.class.getName());
                    info.setCheckable(true);
                    info.setChecked(host.isSelected());
                }
            });
            showSelection(card, check, managed.contains(packageName));
            card.setOnClickListener(view -> {
                boolean selected = !managed.contains(packageName);
                store.setManaged(packageName, selected);
                if (selected) managed.add(packageName);
                else managed.remove(packageName);
                showSelection(card, check, selected);
                selectedCount.setText(getString(R.string.selected_count, managed.size()));
            });
            tiles.add(card);
        }
        appendGrid(body, tiles, APP_COLUMNS);
    }

    private void showSelection(FrameLayout tile, TextView check, boolean selected) {
        var background = rounded(this, selected ? 0xffedf5e9 : WHITE, 12);
        background.setStroke(dp(this, 1), selected ? 0xff92b5a1 : LINE);
        tile.setBackground(new RippleDrawable(ColorStateList.valueOf(0x1a145d4e), background, null));
        tile.setSelected(selected);
        check.setVisibility(selected ? View.VISIBLE : View.GONE);
    }

    private boolean renderTests(LinearLayout body) {
        List<TestStore.Entry> tests = currentTests();
        long now = System.currentTimeMillis();
        pageHeading(body, getString(R.string.test_checklist),
                getResources().getQuantityString(R.plurals.tracked_count, tests.size(), tests.size()));

        if (tests.isEmpty()) {
            empty(body, getString(R.string.empty_checklist));
            return false;
        }

        LinearLayout toolbar = row(this);
        toolbar.setBackground(rounded(this, WHITE, 13));
        pad(toolbar, 10, 6, 8, 6);
        var tally = column(this);
        long opened = tests.stream().filter(entry -> entry.openedOn(now)).count();
        tally.addView(text(this, getString(R.string.checked_in_today), 11, MUTED, false));
        tally.addView(text(this, getString(R.string.check_in_count, opened, tests.size()), 19, INK, true));
        toolbar.addView(tally, new LinearLayout.LayoutParams(0, -2, 1));
        TextView sortButton = button(this, getString(R.string.sort), WHITE, GREEN);
        sortButton.setContentDescription(getString(R.string.sort_description, getString(sort.title)));
        sortButton.setOnClickListener(view -> chooseSort());
        toolbar.addView(sortButton);
        int sequenceLabel = AutoTestService.running ? R.string.sequence_running : R.string.open_in_order;
        addAction(toolbar, getString(sequenceLabel), true, this::requestAutoTest);
        append(body, toolbar, 10);
        playCheckStatus = text(this, getString(R.string.play_checking), 12, MUTED, false);
        playCheckStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        playCheckStatus.setVisibility(playCheck == null ? View.GONE : View.VISIBLE);
        append(body, playCheckStatus, 5);
        renderTestGrid(body, tests, now);
        return true;
    }

    private void renderTestGrid(LinearLayout body, List<TestStore.Entry> tests, long now) {
        var tiles = new ArrayList<View>();
        for (TestStore.Entry entry : tests) {
            InstalledApps.App app = InstalledApps.find(this, entry.packageName());
            if (app == null) continue;
            LinearLayout tile = progressTile(app, entry, now);
            tile.setOnClickListener(view -> openTest(app));
            tile.setFocusable(true);
            tile.setOnLongClickListener(view -> {
                confirmRemove(app);
                return true;
            });
            tiles.add(tile);
        }
        appendGrid(body, tiles, APP_COLUMNS);
    }

    private List<TestStore.Entry> currentTests() {
        List<TestStore.Entry> entries = store.entries();
        var installed = new ArrayList<TestStore.Entry>();
        for (TestStore.Entry entry : entries) {
            if (InstalledApps.find(this, entry.packageName()) != null) installed.add(entry);
        }
        long now = System.currentTimeMillis();
        Comparator<TestStore.Entry> comparator = switch (sort) {
            case PACKAGE -> Comparator.comparing(TestStore.Entry::packageName);
            case REMAINING -> Comparator.comparingInt(
                    (TestStore.Entry entry) -> Math.max(0, GOAL_DAYS - entry.days())).reversed();
            case TODAY -> Comparator.comparing((TestStore.Entry entry) -> entry.openedOn(now))
                    .thenComparing(TestStore.Entry::packageName);
        };
        installed.sort(comparator);
        return installed;
    }

    private void openTest(InstalledApps.App app) {
        checkGooglePlay(() -> {
            if (!InstalledApps.openAndRecord(this, app.packageName())) {
                toast(getString(R.string.open_app_failed, app.name()));
            }
        });
    }

    private void chooseSort() {
        Sort[] options = Sort.values();
        String[] choices = new String[options.length];
        for (int i = 0; i < options.length; i++) choices[i] = getString(options[i].title);
        new AlertDialog.Builder(this).setTitle(R.string.sort_checklist)
                .setSingleChoiceItems(choices, sort.ordinal(), (dialog, selection) -> {
                    sort = options[selection];
                    dialog.dismiss();
                    render(false);
                }).setNegativeButton(R.string.cancel, null).show();
    }

    private void confirmRemove(InstalledApps.App app) {
        new AlertDialog.Builder(this).setTitle(R.string.remove_app_title)
                .setMessage(getString(R.string.remove_app_message, app.name()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.remove_app, (dialog, selection) -> {
                    store.setManaged(app.packageName(), false);
                    render(true);
                }).show();
    }

    private void requestAutoTest() {
        if (playCheck != null) return;
        if (AutoTestService.running) {
            toast(getString(R.string.sequence_already_running));
            return;
        }
        if (Settings.canDrawOverlays(this)) {
            startAutoTest();
            return;
        }
        int seconds = AutoTestService.STEP_SECONDS;
        new AlertDialog.Builder(this).setTitle(R.string.overlay_permission_title)
                .setMessage(getResources().getQuantityString(R.plurals.overlay_permission_message, seconds, seconds))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.open_settings, (dialog, selection) -> {
                    try {
                        autoPermissionRequested = true;
                        startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName())), OVERLAY_PERMISSION);
                    } catch (ActivityNotFoundException exception) {
                        autoPermissionRequested = false;
                        toast(getString(R.string.overlay_permission_required, getString(R.string.app_name)));
                    }
                }).show();
    }

    private void startAutoTest() {
        if (AutoTestService.running) return;
        var queue = new ArrayList<String>();
        for (TestStore.Entry entry : currentTests()) queue.add(entry.packageName());
        if (queue.isEmpty()) {
            toast(getString(R.string.add_apps_first));
            return;
        }
        checkGooglePlay(() -> {
            if (!AutoTestService.running) {
                startService(new Intent(this, AutoTestService.class).putStringArrayListExtra(AutoTestService.QUEUE, queue));
            }
        });
    }

    private void checkGooglePlay(Runnable launch) {
        if (playCheck != null) return;
        int id = ++playCheckId;
        playCheck = playChecks.submit(() -> {
            boolean reachable = GooglePlayConnection.isReachable();
            playCheckEvents.post(() -> finishPlayCheck(id, reachable, launch));
        });
        if (playCheckStatus != null) playCheckStatus.setVisibility(View.VISIBLE);
        playCheckEvents.postDelayed(() -> finishPlayCheck(id, false, launch), GooglePlayConnection.CHECK_TIMEOUT_MS);
    }

    private void finishPlayCheck(int id, boolean reachable, Runnable launch) {
        if (playCheck == null || id != playCheckId) return;
        cancelPlayCheck();
        if (reachable) {
            launch.run();
            return;
        }
        playWarning = new AlertDialog.Builder(this)
                .setTitle(R.string.play_unreachable)
                .setMessage(R.string.play_retry_message)
                .setNeutralButton(R.string.play_continue_anyway, (dialog, choice) -> launch.run())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.play_retry, (dialog, choice) -> checkGooglePlay(launch))
                .create();
        playWarning.setOnDismissListener(dialog -> {
            if (playWarning == dialog) playWarning = null;
        });
        playWarning.show();
        TextView retry = playWarning.getButton(AlertDialog.BUTTON_POSITIVE);
        retry.setTypeface(retry.getTypeface(), Typeface.BOLD);
    }

    private void cancelPlayCheck() {
        if (playCheck == null) return;
        playCheck.cancel(true);
        playCheck = null;
        playCheckEvents.removeCallbacksAndMessages(null);
        if (playCheckStatus != null) playCheckStatus.setVisibility(View.GONE);
    }

    private List<ReportImage.Item> reportItems() {
        var items = new ArrayList<ReportImage.Item>();
        for (TestStore.Entry entry : currentTests()) {
            InstalledApps.App app = InstalledApps.find(this, entry.packageName());
            if (app != null) items.add(new ReportImage.Item(app, entry));
        }
        return items;
    }

    private void saveReport(String filename) {
        if (Build.VERSION.SDK_INT >= 29) {
            saveReport(filename, null);
        } else {
            pendingReport = filename;
            Intent request = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE).setType("image/png")
                    .putExtra(Intent.EXTRA_TITLE, filename);
            startActivityForResult(request, CREATE_REPORT);
        }
    }

    @Override
    protected void onActivityResult(int request, int resultCode, Intent data) {
        super.onActivityResult(request, resultCode, data);
        if (request == OVERLAY_PERMISSION && !Settings.canDrawOverlays(this)) {
            autoPermissionRequested = false;
        }
        if (request == CREATE_REPORT) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null
                    && pendingReport != null) {
                saveReport(pendingReport, data.getData());
            }
            pendingReport = null;
        }
    }

    private void createReport() {
        if (exporting) return;
        List<ReportImage.Item> items = reportItems();
        if (items.isEmpty()) {
            toast(getString(R.string.add_apps_first));
            return;
        }
        exporting = true;
        long now = System.currentTimeMillis();
        int columns = reportColumns();
        String filename = ReportImage.name(now);
        reports.submit(() -> {
            Bitmap image = null;
            Bitmap thumbnail = null;
            boolean generated = false;
            try {
                image = ReportImage.render(this, items, now, columns);
                ReportImage.cache(this, image, filename);
                float scale = Math.min(0.5f, 6000f / image.getHeight());
                thumbnail = Bitmap.createScaledBitmap(image,
                        Math.max(1, Math.round(image.getWidth() * scale)),
                        Math.max(1, Math.round(image.getHeight() * scale)), true);
                generated = true;
            } catch (java.io.IOException | RuntimeException exception) {
                android.util.Log.w("ClosedTestExchange", "Report generation failed", exception);
            } finally {
                if (image != null) image.recycle();
            }

            boolean success = generated;
            Bitmap previewImage = thumbnail;
            runOnUiThread(() -> {
                exporting = false;
                if (isDestroyed() || isFinishing()) return;
                if (!success) toast(getString(R.string.report_create_failed));
                else showReportPreview(filename, previewImage);
            });
        });
    }

    private void showReportPreview(String filename, Bitmap preview) {
        var image = new ImageView(this);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImageBitmap(preview);
        image.setContentDescription(getString(R.string.report_image));
        var reportScroll = new ScrollView(this);
        reportScroll.setBackgroundColor(PAPER);
        pad(reportScroll, 10, 8, 10, 8);
        reportScroll.addView(image, new ScrollView.LayoutParams(-1, -2));
        new AlertDialog.Builder(this)
                .setTitle(R.string.report_preview)
                .setView(reportScroll)
                .setNegativeButton(R.string.close, null)
                .setNeutralButton(R.string.save_png, (dialog, choice) -> saveReport(filename))
                .setPositiveButton(R.string.share, (dialog, choice) ->
                        shareReport(ReportImage.sharedUri(this, filename)))
                .show();
    }

    private void saveReport(String filename, Uri target) {
        reports.submit(() -> {
            boolean saved = false;
            try {
                if (target == null) ReportImage.saveToPictures(this, filename);
                else ReportImage.copyTo(this, filename, target);
                saved = true;
            } catch (java.io.IOException | RuntimeException exception) {
                android.util.Log.w("ClosedTestExchange", "Report save failed", exception);
            }
            boolean success = saved;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                if (!success) toast(getString(R.string.report_save_failed));
                else toast(target == null ? getString(R.string.report_saved_to_pictures, ReportImage.ALBUM)
                        : getString(R.string.report_saved));
            });
        });
    }

    private void shareReport(Uri uri) {
        Intent send = new Intent(Intent.ACTION_SEND).setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(ClipData.newUri(getContentResolver(), getString(R.string.report_title), uri));
        startActivity(Intent.createChooser(send, getString(R.string.share_report)));
    }

    private void pageHeading(LinearLayout parent, String title, String subtitle) {
        parent.addView(text(this, title, 25, INK, true));
        append(parent, text(this, subtitle, 12, MUTED, false), 3);
    }

    private LinearLayout appTile(InstalledApps.App app, int nameSize) {
        var tile = column(this);
        tile.setBackground(rounded(this, WHITE, 12));
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setMinimumHeight(dp(this, 83));
        pad(tile, 4, 9, 4, 7);
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon());
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(this, 32), dp(this, 32)));
        TextView name = text(this, app.name(), nameSize, INK, true);
        name.setGravity(Gravity.CENTER);
        name.setMinLines(2);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        append(tile, name, 3);
        return tile;
    }

    private LinearLayout progressTile(InstalledApps.App app, TestStore.Entry entry, long now) {
        LinearLayout tile = appTile(app, 11);
        boolean opened = entry.openedOn(now);
        String status = getString(opened ? R.string.checked_in : R.string.pending_check_in);
        TextView tag = text(this, status, 11, opened ? GREEN : AMBER, true);
        tag.setGravity(Gravity.CENTER);
        tag.setBackground(rounded(this, opened ? PALE : PALE_AMBER, 6));
        pad(tag, 4, 2, 4, 2);
        tile.addView(tag);
        String days = getResources().getQuantityString(R.plurals.day_progress, GOAL_DAYS, entry.days(), GOAL_DAYS);
        TextView progress = text(this, days, 11, INK, true);
        progress.setGravity(Gravity.CENTER);
        append(tile, progress, 3);
        return tile;
    }

    private int reportColumns() {
        var config = getResources().getConfiguration();
        return config.screenWidthDp < 340 || config.fontScale > 1.3f ? 2 : 3;
    }

    private void appendGrid(LinearLayout parent, List<View> tiles, int columns) {
        for (int offset = 0; offset < tiles.size(); offset += columns) {
            var line = row(this);
            line.setBaselineAligned(false);
            line.setGravity(Gravity.TOP);
            for (int column = 0; column < columns; column++) {
                int index = offset + column;
                var cell = new LinearLayout.LayoutParams(0, -1, 1);
                if (column > 0) cell.setMarginStart(dp(this, 7));
                line.addView(index < tiles.size() ? tiles.get(index) : new Space(this), cell);
            }
            append(parent, line, 7);
        }
    }

    private void addAction(LinearLayout row, String label, boolean primary, Runnable action) {
        TextView control = button(this, label, primary);
        control.setOnClickListener(view -> action.run());
        var params = new LinearLayout.LayoutParams(-2, -2);
        if (row.getChildCount() > 0) params.leftMargin = dp(this, 8);
        row.addView(control, params);
    }

    private void append(LinearLayout parent, View view, int marginTop) {
        var params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(this, marginTop);
        parent.addView(view, params);
    }

    private void empty(LinearLayout parent, String message) {
        TextView text = text(this, message, 16, MUTED, false);
        text.setGravity(Gravity.CENTER);
        text.setBackground(rounded(this, WHITE, 16));
        pad(text, 16, 25, 16, 25);
        append(parent, text, 14);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
