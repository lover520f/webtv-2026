package com.fongmi.android.tv.setting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ThemeColorPolicyTest {

    @Test
    public void migratesOnlyUsersWithoutAnExplicitSelection() {
        assertTrue(ThemeColorPolicy.shouldMigrate(false, 0));
        assertFalse(ThemeColorPolicy.shouldMigrate(true, 0));
        assertFalse(ThemeColorPolicy.shouldMigrate(false, ThemeColorPolicy.VERSION));
    }

    @Test
    public void preservesEveryExplicitThemeMode() {
        assertEquals(-1, ThemeColorPolicy.resolve(true, -1));
        assertEquals(0, ThemeColorPolicy.resolve(true, 0));
        assertEquals(0xFF6750A4, ThemeColorPolicy.resolve(true, 0xFF6750A4));
    }

    @Test
    public void defaultsUnselectedUsersToGreen() {
        assertEquals(0xFF43A047, ThemeColorPolicy.resolve(false, -1));
    }
}
