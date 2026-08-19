package com.fongmi.android.tv.setting;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ThemeContrastStrategyTest {

    @Test
    public void dialogPairKeepsReadableForegrounds() {
        assertTrue(contrastRatio(0xFFFFFFFF, 0x146C43) >= 4.5);
        assertTrue(contrastRatio(0xFF202124, 0xFFFFFFFF) >= 4.5);
        assertTrue(contrastRatio(0xFF5F6368, 0xFFFFFFFF) >= 4.5);
    }

    private static double contrastRatio(int foreground, int background) {
        double foregroundLuminance = luminance(foreground);
        double backgroundLuminance = luminance(background);
        double lighter = Math.max(foregroundLuminance, backgroundLuminance);
        double darker = Math.min(foregroundLuminance, backgroundLuminance);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double luminance(int color) {
        return 0.2126 * channel(color >> 16) + 0.7152 * channel(color >> 8) + 0.0722 * channel(color);
    }

    private static double channel(int value) {
        double normalized = (value & 0xFF) / 255.0;
        return normalized <= 0.03928 ? normalized / 12.92 : Math.pow((normalized + 0.055) / 1.055, 2.4);
    }
}
