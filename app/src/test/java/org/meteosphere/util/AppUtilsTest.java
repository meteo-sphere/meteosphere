package org.meteosphere.util;

import static org.junit.Assert.assertEquals;

import java.util.Locale;
import org.junit.Test;

// The helpers that decide without a device: positions' rounding, date patterns, widgets' shapes and colours' alpha
public class AppUtilsTest {

    // To hundredths, a half up towards the larger number, below zero too
    @Test
    public void roundToHundredths() {
        assertEquals(56.95, AppUtils.roundToHundredths(56.951234), 0);
        assertEquals(12.35, AppUtils.roundToHundredths(12.346), 0);
        assertEquals(-33.87, AppUtils.roundToHundredths(-33.8688), 0);
        assertEquals(-12.34, AppUtils.roundToHundredths(-12.344), 0);
        assertEquals(0, AppUtils.roundToHundredths(0), 0);
        // 12.5 and -12.5 hundredths exactly
        assertEquals(0.13, AppUtils.roundToHundredths(0.125), 0);
        assertEquals(-0.12, AppUtils.roundToHundredths(-0.125), 0);
    }

    // Russian, Ukrainian, Polish and English keep the pattern as it is written
    @Test
    public void datePatternKept() {
        assertEquals("d MMM, HH:mm", AppUtils.datePattern(Locale.forLanguageTag("ru"), "d MMM, HH:mm"));
        assertEquals("EEEE, d MMMM", AppUtils.datePattern(Locale.forLanguageTag("uk-UA"), "EEEE, d MMMM"));
        assertEquals("d MMMM yyyy", AppUtils.datePattern(Locale.forLanguageTag("pl"), "d MMMM yyyy"));
        assertEquals("d MMM, h:mm a", AppUtils.datePattern(Locale.US, "d MMM, h:mm a"));
    }

    // The other languages are asked for the pattern's letters, in their order
    @Test
    public void skeleton() {
        assertEquals("dMMMHHmm", AppUtils.skeleton("d MMM, HH:mm"));
        assertEquals("EEEEdMMMM", AppUtils.skeleton("EEEE, d MMMM"));
        assertEquals("dMMMhmma", AppUtils.skeleton("d MMM, h:mm a"));
    }

    // A widget without a size of its own takes its provider's shape, the fraction of the shape kept and that of the
    // height cut off
    @Test
    public void heightByShape() {
        // Whole numbers would make it 1000 × 0
        assertEquals(440, AppUtils.heightByShape(1000, 250, 110));
        // 443.96
        assertEquals(443, AppUtils.heightByShape(1009, 250, 110));
        assertEquals(1000, AppUtils.heightByShape(1000, 110, 110));
        // Whole numbers would make it 1000 × 1000
        assertEquals(1500, AppUtils.heightByShape(1000, 110, 165));
        assertEquals(0, AppUtils.heightByShape(1000, 0, 110));
    }

    @Test
    public void withAlpha() {
        assertEquals(0x99123456, AppUtils.withAlpha(0xFF123456, 0.6f));
        assertEquals(0x3F123456, AppUtils.withAlpha(0x12123456, 0.25f));
        assertEquals(0x00FFFFFF, AppUtils.withAlpha(0xFFFFFFFF, 0f));
        assertEquals(0xFF000000, AppUtils.withAlpha(0x00000000, 1f));
    }
}
