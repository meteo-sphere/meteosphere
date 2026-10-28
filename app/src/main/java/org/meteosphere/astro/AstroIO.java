package org.meteosphere.astro;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Writes the computed days, phases, apsides and eclipse views to a stream and reads them back, for a cache */
public final class AstroIO {

    private AstroIO() {
    }

    public static void write(DataOutputStream out, Sun.Day day) throws IOException {
        out.writeLong(day.rise);
        out.writeLong(day.set);
        out.writeLong(day.dawn);
        out.writeLong(day.dusk);
        out.writeLong(day.noon);
        out.writeDouble(day.noonAltitude);
        out.writeLong(day.dayLength);
        out.writeBoolean(day.polarDay);
        out.writeBoolean(day.polarNight);
    }

    public static Sun.Day readSunDay(DataInputStream in) throws IOException {
        return new Sun.Day(in.readLong(), in.readLong(), in.readLong(), in.readLong(), in.readLong(), in.readDouble(),
                in.readLong(), in.readBoolean(), in.readBoolean());
    }

    public static void write(DataOutputStream out, Moon.Day day) throws IOException {
        out.writeInt(day.phase);
        out.writeLong(day.phaseTime);
        out.writeDouble(day.age);
        out.writeDouble(day.illumination);
        out.writeLong(day.rise);
        out.writeLong(day.set);
        out.writeBoolean(day.alwaysUp);
    }

    public static Moon.Day readMoonDay(DataInputStream in) throws IOException {
        return new Moon.Day(in.readInt(), in.readLong(), in.readDouble(), in.readDouble(), in.readLong(), in.readLong(), in.readBoolean());
    }

    public static void write(DataOutputStream out, Moon.Phase phase) throws IOException {
        out.writeInt(phase.phase);
        out.writeLong(phase.time);
    }

    public static Moon.Phase readPhase(DataInputStream in) throws IOException {
        return new Moon.Phase(in.readInt(), in.readLong());
    }

    public static void write(DataOutputStream out, Moon.Apsis apsis) throws IOException {
        out.writeBoolean(apsis.perigee);
        out.writeLong(apsis.time);
        out.writeDouble(apsis.distanceKm);
    }

    public static Moon.Apsis readApsis(DataInputStream in) throws IOException {
        return new Moon.Apsis(in.readBoolean(), in.readLong(), in.readDouble());
    }

    public static void write(DataOutputStream out, Eclipse.Local local) throws IOException {
        out.writeBoolean(local.visible);
        out.writeLong(local.start);
        out.writeLong(local.max);
        out.writeLong(local.end);
        out.writeDouble(local.obscuration);
    }

    public static Eclipse.Local readLocal(DataInputStream in) throws IOException {
        return new Eclipse.Local(in.readBoolean(), in.readLong(), in.readLong(), in.readLong(), in.readDouble());
    }

    public static void write(DataOutputStream out, float[] values) throws IOException {
        out.writeInt(values.length);
        for (float value : values) {
            out.writeFloat(value);
        }
    }

    public static float[] readFloats(DataInputStream in) throws IOException {
        float[] values = new float[in.readInt()];
        for (int i = 0; i < values.length; i++) {
            values[i] = in.readFloat();
        }
        return values;
    }
}
