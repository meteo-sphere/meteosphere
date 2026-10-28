package org.meteosphere.location;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

// Which last known positions the search takes without listening to a provider
public class PositionServiceTest {

    private static final long SECOND_MS = 1000L;
    private static final long NOW = 1_800_000_000_000L;

    // Fresh is under five minutes from now, the clock set back too
    @Test
    public void isFresh() {
        assertTrue(PositionService.isFresh(NOW, NOW));
        assertTrue(PositionService.isFresh(NOW - 299 * SECOND_MS, NOW));
        assertFalse(PositionService.isFresh(NOW - 300 * SECOND_MS, NOW));
        assertTrue(PositionService.isFresh(NOW + 299 * SECOND_MS, NOW));
        assertFalse(PositionService.isFresh(NOW + 300 * SECOND_MS, NOW));
        assertFalse(PositionService.isFresh(0, NOW));
    }

    // Usable is fresh and of a known accuracy under 3 km
    @Test
    public void isUsable() {
        assertTrue(PositionService.isUsable(NOW - 60 * SECOND_MS, true, 25, NOW));
        assertTrue(PositionService.isUsable(NOW - 60 * SECOND_MS, true, 2999, NOW));
        assertFalse(PositionService.isUsable(NOW - 60 * SECOND_MS, true, 3000, NOW));
        assertFalse(PositionService.isUsable(NOW - 60 * SECOND_MS, false, 0, NOW));
        assertFalse(PositionService.isUsable(NOW - 300 * SECOND_MS, true, 25, NOW));
        assertFalse(PositionService.isUsable(NOW + 300 * SECOND_MS, true, 25, NOW));
    }
}
