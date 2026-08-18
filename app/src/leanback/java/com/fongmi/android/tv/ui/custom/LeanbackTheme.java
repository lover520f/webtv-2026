package com.fongmi.android.tv.ui.custom;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.widget.ImageViewCompat;
import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.ThemePalette;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Applies a seed-driven state palette while keeping the player/window backgrounds untouched. */
public final class LeanbackTheme {

    private static final Set<View> THEMED = Collections.newSetFromMap(new WeakHashMap<>());

    private LeanbackTheme() {
    }

    public static void apply(Activity activity) {
        ThemePalette palette = ThemePalette.from(Setting.getDynamicColor());
        DynamicColors.applyToActivityIfAvailable(activity, new DynamicColorsOptions.Builder().setContentBasedSource(palette.seed()).build());
        View root = activity.findViewById(android.R.id.content);
        theme(root, palette);
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> theme(root, palette));
    }

    private static void theme(View view, ThemePalette palette) {
        if (view instanceof ImageView) {
            ImageViewCompat.setImageTintList((ImageView) view, android.content.res.ColorStateList.valueOf(palette.onAccent()));
        } else if (view instanceof TextView) {
            ((TextView) view).setTextColor(palette.onAccent());
        }
        if (view.isFocusable() && THEMED.add(view)) {
            Drawable normal = view.getBackground();
            StateListDrawable states = new StateListDrawable();
            states.addState(new int[]{android.R.attr.state_focused}, shape(palette.container(), palette.focus(), false));
            states.addState(new int[]{android.R.attr.state_activated}, shape(palette.selected(), 0, false));
            states.addState(new int[]{android.R.attr.state_selected}, shape(palette.selected(), 0, false));
            states.addState(new int[0], normal == null ? shape(palette.container(), 0, false) : normal);
            view.setBackground(states);
        }
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) theme(group.getChildAt(i), palette);
    }

    private static Drawable shape(int fill, int stroke, boolean borderOnly) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(borderOnly ? 0x00000000 : fill);
        drawable.setCornerRadius(8f);
        if (stroke != 0) drawable.setStroke(3, stroke);
        return drawable;
    }
}
