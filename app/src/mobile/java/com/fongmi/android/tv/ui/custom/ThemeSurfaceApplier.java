package com.fongmi.android.tv.ui.custom;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import androidx.media3.ui.PlayerView;

import com.google.android.material.R;
import com.google.android.material.color.MaterialColors;

/** Applies theme roles to ordinary screens; media surfaces and imagery are deliberately untouched. */
public final class ThemeSurfaceApplier {
    private ThemeSurfaceApplier() {}

    public static void apply(View root) {
        if (root != null) theme(root, true);
    }

    private static void theme(View view, boolean root) {
        if (view instanceof ImageView || view instanceof PlayerView || view instanceof CustomWallView) return;
        if (root || view instanceof ViewGroup && !(view instanceof RecyclerView)) {
            view.setBackgroundColor(MaterialColors.getColor(view, R.attr.colorSurface));
        } else if (view.getBackground() instanceof ColorDrawable) {
            int color = ((ColorDrawable) view.getBackground()).getColor();
            if (isNeutral(color)) view.setBackgroundColor(MaterialColors.getColor(view, R.attr.colorSurfaceContainer));
        }
        if (view instanceof TextView text && isNeutral(text.getCurrentTextColor())) {
            text.setTextColor(MaterialColors.getColor(text, R.attr.colorOnSurface));
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) theme(group.getChildAt(i), false);
        }
    }

    private static boolean isNeutral(int color) {
        int rgb = color & 0x00FFFFFF;
        return color == Color.BLACK || color == Color.WHITE || rgb == 0x747474 || rgb == 0xF3F5F7 || rgb == 0xDADCE0;
    }
}
