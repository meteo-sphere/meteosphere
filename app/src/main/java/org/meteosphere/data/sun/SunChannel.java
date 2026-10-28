package org.meteosphere.data.sun;

/**
 * What the Sun can be seen in: an instrument's channel as Helioviewer names it, and how much sky a whole picture of
 * it spans, in arc seconds. The disc's channels span AIA's own field, so the Sun stays the same size between them.
 */
public enum SunChannel {
    CORONA("aia193", 11, "SDO,AIA,AIA,193", "193 Å"),
    LOOPS("aia171", 10, "SDO,AIA,AIA,171", "171 Å"),
    PROMINENCES("aia304", 13, "SDO,AIA,AIA,304", "304 Å"),
    FLARES("aia131", 9, "SDO,AIA,AIA,131", "131 Å"),
    SPOTS("hmic", 18, "SDO,HMI,HMI,continuum", "HMI"),
    MAGNETIC("hmim", 19, "SDO,HMI,HMI,magnetogram", "HMI"),
    EJECTIONS("lasco3", 5, "SOHO,LASCO,C3,white-light", "LASCO C3");

    // AIA's 4096 pixels of 0.6": the disc with its corona
    private static final double DISC_FIELD = 2457.6;
    // SOHO's C3 coronagraph out to some eight solar radii
    private static final double CORONAGRAPH_FIELD = 15360;
    // AIA's pixel, the finest a part of a disc's picture is asked at
    private static final double DISC_FINEST = 0.6;

    /** The name of its pictures on the disk */
    public final String key;
    /** Helioviewer's id of the source, for its frames' times */
    public final int sourceId;
    /** Helioviewer's layer: observatory, instrument, detector, measurement */
    public final String layer;
    /** The wavelength or the instrument, shown with the name */
    public final String label;

    SunChannel(String key, int sourceId, String layer, String label) {
        this.key = key;
        this.sourceId = sourceId;
        this.layer = layer;
        this.label = label;
    }

    /** Whether it shows the disc, which can be cut from its sky and looked at closer; the coronagraph covers it */
    public boolean hasDisc() {
        return this != EJECTIONS;
    }

    /** The sky a whole picture spans, arc seconds across */
    public double field() {
        return hasDisc() ? DISC_FIELD : CORONAGRAPH_FIELD;
    }

    /** The finest scale a part of the picture is worth asking at, arc seconds a pixel */
    public double finest() {
        return hasDisc() ? DISC_FINEST : CORONAGRAPH_FIELD / 1024;
    }
}
