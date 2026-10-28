package org.meteosphere.astro;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import org.junit.Test;

public class AstroIOTest {

    // Each value read back as it was written
    @Test
    public void roundTrip() throws Exception {
        Sun.Day sun = Sun.day(1790899200000L, 120, 52.37, 4.90);
        Moon.Day moon = Moon.day(1790899200000L, 120, 52.37, 4.90);
        Moon.Phase phase = Moon.nextPhase(1790899200000L);
        Moon.Apsis apsis = Moon.nextApsis(1790899200000L);
        Eclipse.Local local = new Eclipse.Local(true, 1, 2, 3, 0.25);
        float[] floats = {1.5f, -2f, 0f};
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        AstroIO.write(out, sun);
        AstroIO.write(out, moon);
        AstroIO.write(out, phase);
        AstroIO.write(out, apsis);
        AstroIO.write(out, local);
        AstroIO.write(out, floats);
        out.flush();
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        Sun.Day sunBack = AstroIO.readSunDay(in);
        assertEquals(sun.rise, sunBack.rise);
        assertEquals(sun.set, sunBack.set);
        assertEquals(sun.dawn, sunBack.dawn);
        assertEquals(sun.dusk, sunBack.dusk);
        assertEquals(sun.noon, sunBack.noon);
        assertEquals(sun.noonAltitude, sunBack.noonAltitude, 0);
        assertEquals(sun.dayLength, sunBack.dayLength);
        assertEquals(sun.polarDay, sunBack.polarDay);
        assertEquals(sun.polarNight, sunBack.polarNight);
        Moon.Day moonBack = AstroIO.readMoonDay(in);
        assertEquals(moon.phase, moonBack.phase);
        assertEquals(moon.phaseTime, moonBack.phaseTime);
        assertEquals(moon.age, moonBack.age, 0);
        assertEquals(moon.illumination, moonBack.illumination, 0);
        assertEquals(moon.rise, moonBack.rise);
        assertEquals(moon.set, moonBack.set);
        assertEquals(moon.alwaysUp, moonBack.alwaysUp);
        Moon.Phase phaseBack = AstroIO.readPhase(in);
        assertEquals(phase.phase, phaseBack.phase);
        assertEquals(phase.time, phaseBack.time);
        Moon.Apsis apsisBack = AstroIO.readApsis(in);
        assertEquals(apsis.perigee, apsisBack.perigee);
        assertEquals(apsis.time, apsisBack.time);
        assertEquals(apsis.distanceKm, apsisBack.distanceKm, 0);
        Eclipse.Local localBack = AstroIO.readLocal(in);
        assertEquals(local.visible, localBack.visible);
        assertEquals(local.start, localBack.start);
        assertEquals(local.max, localBack.max);
        assertEquals(local.end, localBack.end);
        assertEquals(local.obscuration, localBack.obscuration, 0);
        assertArrayEquals(floats, AstroIO.readFloats(in), 0);
    }
}
