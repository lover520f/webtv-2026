package com.fongmi.android.tv.ui.custom;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.ThemePalette;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;

/** Applies focus states to ordinary TV controls without touching imagery or media backgrounds. */
public final class LeanbackTheme {

    private LeanbackTheme() {
    }

    public static void apply(Activity activity) {
        int selected = Setting.getThemeColor();
        if (selected == -1) return;
        int resolved = Setting.getDynamicColor();
        ThemePalette palette = ThemePalette.from(resolved);
        if (resolved != 0) DynamicColors.applyToActivityIfAvailable(activity, new DynamicColorsOptions.Builder().setContentBasedSource(resolved).build());
        theme(activity.findViewById(android.R.id.content), palette);
    }

    private static void theme(View view, ThemePalette palette) {
        if (view == null || view instanceof CustomWallView) return;
        if (view.isFocusable()) applyFocusState(view, palette);
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) theme(group.getChildAt(i), palette);
        }
    }

    private static void applyFocusState(View view, ThemePalette palette) {
        Drawable normal = view.getBackground();
        boolean image = view instanceof ImageView;
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, shape(image ? 0x00000000 : palette.container(), palette.focus()));
        states.addState(new int[]{android.R.attr.state_activated}, shape(image ? 0x00000000 : palette.selected(), palette.selected()));
        states.addState(new int[]{android.R.attr.state_selected}, shape(image ? 0x00000000 : palette.selected(), palette.selected()));
        states.addState(new int[0], normal == null ? shape(0x00000000, 0) : normal);
        view.setBackground(states);
    }

    private static Drawable shape(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(8f);
        if (stroke != 0) drawable.setStroke(3, stroke);
        return drawable;
    }
}
