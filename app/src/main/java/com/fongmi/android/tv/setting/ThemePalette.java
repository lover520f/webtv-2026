package com.fongmi.android.tv.setting;

/** Pure Java TV palette generation; intentionally independent of Android color APIs. */
public final class ThemePalette {

    public static final int DEFAULT_SEED = ThemeColorPolicy.DEFAULT_GREEN;
    private static final int DARK_SURFACE = 0xFF202124;

    private final int seed;
    private final int focus;
    private final int container;
    private final int selected;
    private final int onAccent;

    private ThemePalette(int seed) {
        this.seed = opaque(seed);
        this.focus = ensureContrast(this.seed, 0xFF000000, 4.5);
        this.container = blend(this.focus, DARK_SURFACE, 0.62f);
        this.selected = blend(this.focus, 0xFFFFFFFF, 0.22f);
        this.onAccent = contrast(0xFFFFFFFF, focus) >= 4.5 ? 0xFFFFFFFF : 0xFF000000;
    }

    public static ThemePalette from(int seed) {
        return new ThemePalette(seed == 0 || seed == -1 ? DEFAULT_SEED : seed);
    }

    public int seed() { return seed; }
    public int focus() { return focus; }
    public int container() { return container; }
    public int selected() { return selected; }
    public int onAccent() { return onAccent; }

    public static double contrast(int foreground, int background) {
        double light = luminance(foreground) + 0.05;
        double dark = luminance(background) + 0.05;
        return Math.max(light, dark) / Math.min(light, dark);
    }

    private static int ensureContrast(int color, int background, double minimum) {
        int result = opaque(color);
        if (contrast(result, background) >= minimum) return result;
        for (int i = 1; i <= 20; i++) {
            result = blend(color, 0xFFFFFFFF, i / 20f);
            if (contrast(result, background) >= minimum) return result;
        }
        return 0xFFFFFFFF;
    }

    private static double luminance(int color) {
        double r = channel((color >> 16) & 0xFF);
        double g = channel((color >> 8) & 0xFF);
        double b = channel(color & 0xFF);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int value) {
        double channel = value / 255.0;
        return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    private static int blend(int from, int to, float amount) {
        float inverse = 1f - amount;
        int r = Math.round(((from >> 16) & 0xFF) * inverse + ((to >> 16) & 0xFF) * amount);
        int g = Math.round(((from >> 8) & 0xFF) * inverse + ((to >> 8) & 0xFF) * amount);
        int b = Math.round((from & 0xFF) * inverse + (to & 0xFF) * amount);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int opaque(int color) {
        return 0xFF000000 | color & 0x00FFFFFF;
    }
}
