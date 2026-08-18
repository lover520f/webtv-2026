package com.fongmi.android.tv.ui.custom;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;

import com.fongmi.android.tv.setting.ThemeSurfaceRoles;
import com.google.android.material.R;

/** Themes only an ordinary page root. Child drawables, media surfaces and wallpaper are never traversed. */
public final class ThemeSurfaceApplier {

    private ThemeSurfaceApplier() {
    }

    public static void apply(View root) {
        if (root == null || root instanceof CustomWallView || !canReplace(root.getBackground())) return;
        root.setBackgroundColor(resolve(root, R.attr.colorSurface, ThemeSurfaceRoles.FALLBACK_SURFACE));
    }

    private static boolean canReplace(Drawable background) {
        if (background == null) return true;
        if (!(background instanceof ColorDrawable color)) return false;
        return color.getColor() == Color.TRANSPARENT || ThemeSurfaceRoles.isNeutralSurface(color.getColor());
    }

    private static int resolve(View view, int attr, int fallback) {
        TypedValue value = new TypedValue();
        return view.getContext().getTheme().resolveAttribute(attr, value, true) && value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT ? value.data : fallback;
    }
}
