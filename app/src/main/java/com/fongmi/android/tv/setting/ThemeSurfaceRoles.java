package com.fongmi.android.tv.setting;

/** Pure color policy shared by Android surface application and unit tests. */
public final class ThemeSurfaceRoles {

    public static final int FALLBACK_SURFACE = 0xFF202124;

    private ThemeSurfaceRoles() {
    }

    public static boolean isNeutralSurface(int color) {
        int rgb = color & 0x00FFFFFF;
        return color == 0xFF000000 || color == 0xFFFFFFFF || rgb == 0x747474 || rgb == 0xF3F5F7 || rgb == 0xDADCE0;
    }
}
