package org.meteosphere.astro;

import java.util.Collection;

/** Which moon phase to announce next; QuietHours says when. Plain Java, so it is tested without Android. */
public final class MoonPhaseAlerts {

    private MoonPhaseAlerts() {
    }

    /** The next of the given principal phases after the instant, null if there are none */
    public static Moon.Phase nextPhase(long utcMillis, Collection<Integer> phases) {
        if (phases.isEmpty()) {
            return null;
        }
        Moon.Phase phase = Moon.nextPhase(utcMillis);
        while (!phases.contains(phase.phase)) {
            phase = Moon.nextPhase(phase.time + 60000);
        }
        return phase;
    }
}
