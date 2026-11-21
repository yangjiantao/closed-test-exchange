package com.shanmei.tools.testing;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Date;

final class Ui {
    static final int PAPER = 0xfff4f6f1;
    static final int WHITE = 0xffffffff;
    static final int INK = 0xff172820;
    static final int MUTED = 0xff54655d;
    static final int LINE = 0xffdce4da;
    static final int GREEN = 0xff145d4e;
    static final int DARK = 0xff123a33;
    static final int MINT = 0xffd8f578;
    static final int PALE = 0xffe4efe6;
    static final int AMBER = 0xff815121;
    static final int PALE_AMBER = 0xffffefd9;

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static String date(Context context, long time) {
        return DateFormat.getMediumDateFormat(context).format(new Date(time));
    }

    static void pad(View view, int left, int top, int right, int bottom) {
        Context c = view.getContext();
        view.setPadding(dp(c, left), dp(c, top), dp(c, right), dp(c, bottom));
    }

    static LinearLayout column(Context context) {
        var result = new LinearLayout(context);
        result.setOrientation(LinearLayout.VERTICAL);
        return result;
    }

    static LinearLayout row(Context context) {
        var result = new LinearLayout(context);
        result.setGravity(Gravity.CENTER_VERTICAL);
        return result;
    }

    static TextView text(Context context, String text, int sp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(sp);
        if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }

    static GradientDrawable rounded(Context c, int color, int radius) {
        var shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(c, radius));
        return shape;
    }

    static TextView button(Context c, String text, boolean primary) {
        return button(c, text, primary ? GREEN : PALE, primary ? WHITE : GREEN);
    }

    static TextView button(Context c, String text, int background, int foreground) {
        TextView button = text(c, text, 14, foreground, true);
        ColorStateList tint = ColorStateList.valueOf(0x2209362d);
        button.setBackground(new RippleDrawable(tint, rounded(c, background, 13), null));
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(c, 46));
        pad(button, 14, 7, 14, 7);
        button.setFocusable(true);
        button.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(
                    View view, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(view, info);
                info.setClassName(android.widget.Button.class.getName());
            }
        });
        return button;
    }

    static ProgressBar progress(Context c, int max, int value, int color) {
        ProgressBar bar = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(max);
        bar.setProgress(value);
        bar.setProgressTintList(ColorStateList.valueOf(color));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(LINE));
        return bar;
    }

    private Ui() {}
}
