package com.fongmi.android.tv.setting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ThemePaletteTest {

    @Test
    public void arbitrarySeedsProduceTvSafeFocus() {
        int[] seeds = {0xFF000000, 0xFFFFFFFF, 0xFFFF0000, 0xFF0000FF, 0xFF123456, 0xFF808080};
        for (int seed : seeds) {
            ThemePalette palette = ThemePalette.from(seed);
            assertTrue(ThemePalette.contrast(palette.focus(), 0xFF000000) >= 4.5);
            assertTrue(ThemePalette.contrast(palette.onAccent(), palette.focus()) >= 4.5);
        }
    }

    @Test
    public void invalidSeedFallsBackToDefault() {
        assertEquals(ThemePalette.DEFAULT_SEED, ThemePalette.from(0).seed());
        assertEquals(ThemePalette.DEFAULT_SEED, ThemePalette.from(-1).seed());
    }
}
