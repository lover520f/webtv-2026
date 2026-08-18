package com.fongmi.android.tv.setting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ThemeSurfaceRolesTest {

    @Test
    public void fallbackSurfaceIsOpaqueAndMediaSafeDark() {
        assertEquals(0xFF202124, ThemeSurfaceRoles.FALLBACK_SURFACE);
        assertEquals(0xFF000000, ThemeSurfaceRoles.FALLBACK_SURFACE & 0xFF000000);
    }

    @Test
    public void onlyKnownFlatNeutralSurfacesAreReplaceable() {
        assertTrue(ThemeSurfaceRoles.isNeutralSurface(0xFF000000));
        assertTrue(ThemeSurfaceRoles.isNeutralSurface(0xFFFFFFFF));
        assertTrue(ThemeSurfaceRoles.isNeutralSurface(0xFF747474));
        assertTrue(ThemeSurfaceRoles.isNeutralSurface(0xFFF3F5F7));
        assertTrue(ThemeSurfaceRoles.isNeutralSurface(0xFFDADCE0));
        assertFalse(ThemeSurfaceRoles.isNeutralSurface(0xFF43A047));
        assertFalse(ThemeSurfaceRoles.isNeutralSurface(0x00000000));
    }
}
