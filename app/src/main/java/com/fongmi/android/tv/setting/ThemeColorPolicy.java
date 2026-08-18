package com.fongmi.android.tv.setting;

public final class ThemeColorPolicy {

    public static final int VERSION = 593;
    public static final int DEFAULT_GREEN = 0xFF43A047;

    private ThemeColorPolicy() {
    }

    public static boolean shouldMigrate(boolean hasExplicitSelection, int appliedVersion) {
        return !hasExplicitSelection && appliedVersion < VERSION;
    }

    public static int resolve(boolean hasExplicitSelection, int selectedColor) {
        return hasExplicitSelection ? selectedColor : DEFAULT_GREEN;
    }
}
