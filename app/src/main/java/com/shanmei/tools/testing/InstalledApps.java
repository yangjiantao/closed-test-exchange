package com.shanmei.tools.testing;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

final class InstalledApps {
    record App(String packageName, String name, Drawable icon, long firstInstalled, String versionName) {}

    static List<App> scan(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        var apps = new LinkedHashMap<String, App>();
        for (ResolveInfo activity : pm.queryIntentActivities(launcher, 0)) {
            ApplicationInfo info = activity.activityInfo.applicationInfo;
            String packageName = info.packageName;
            if (packageName.equals(context.getPackageName()) || apps.containsKey(packageName)
                    || pm.getLaunchIntentForPackage(packageName) == null) {
                continue;
            }
            App app = find(context, packageName);
            if (app != null) apps.put(packageName, app);
        }
        Collator collator = Collator.getInstance();
        var sorted = new ArrayList<>(apps.values());
        sorted.sort(Comparator.comparingLong(App::firstInstalled).reversed()
                .thenComparing(App::name, collator)
                .thenComparing(App::packageName));
        return sorted;
    }

    static App find(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo installed = pm.getPackageInfo(packageName, 0);
            ApplicationInfo info = installed.applicationInfo;
            if (info == null) return null;
            return new App(packageName, info.loadLabel(pm).toString(), info.loadIcon(pm),
                    installed.firstInstallTime, installed.versionName);
        } catch (PackageManager.NameNotFoundException ignored) {
            return null;
        }
    }

    static boolean openAndRecord(Context context, String packageName) {
        if (!open(context, packageName)) return false;
        TestStore.get(context).recordOpen(packageName, System.currentTimeMillis());
        return true;
    }

    static boolean open(Context context, String packageName) {
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (launch == null) return false;
        try {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException | SecurityException ignored) {
            return false;
        }
    }

    private InstalledApps() {}
}
