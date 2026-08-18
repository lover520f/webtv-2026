package com.fongmi.android.tv.setting;

public final class ThemeColorPolicy {

    public static final int VERSION = 593;
    public static final int DEFAULT_GREEN = 0xFF43A047;

    private ThemeColorPolicy() {
    }

    /** v5.9.3: migrate only when the theme_color preference key has never existed. */
    public static boolean shouldMigrate(boolean hasKey, int appliedVersion) {
        return !hasKey && appliedVersion < VERSION;
    }

    public static int resolve(boolean hasKey, int selectedColor) {
        return hasKey ? selectedColor : DEFAULT_GREEN;
    }
}
