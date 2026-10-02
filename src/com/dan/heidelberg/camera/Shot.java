package com.dan.heidelberg.camera;

/**
 * Eine Kameraeinstellung über die Zeit: Augpunkt, Blickziel und Bildwinkel zu jedem Zeitpunkt. Fahrten,
 * Übergänge zwischen Blickpunkten und der Rundumblick sind Shots; die Steuerung spielt sie ab.
 */
public interface Shot {
    String name();

    /** Dauer in Sekunden bei Tempo 1. */
    double duration();

    /** Pose zur Zeit t in out[0..6]: Auge x, y, z, Ziel x, y, z, Bildwinkel (Bogenmaß; NaN = unverändert). */
    void pose(double t, double[] out);
}
