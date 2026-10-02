package com.dan.heidelberg.castle;

import com.dan.heidelberg.core.Mesh;
import com.dan.heidelberg.core.Schnitt;

/**
 * Phase 9, Schnitt durch Terrassen und Bogenbau: die zwei Ebenen, die das Bedienfeld anbietet. Der Rechenweg der Füllung
 * läuft beim ersten Einschalten einer Ebene (rund eine halbe Sekunde) und wird gemerkt.
 */
public final class Schnitte {
    private Schnitte() { }

    public static final String[] NAMES = {
            "Längsschnitt x = −70 m: Terrassen, Bogenbau, Große Grotte, Wassermechanik",
            "Querschnitt z = 95 m: Große und Kleine Grotte, Bogenbau-Westmauer"};
    /** Kamerapose je Ebene: Drehpunkt x, y, z, Blickrichtung (Grad), Neigung, Abstand. */
    public static final double[][] POSE = {
            {-70, 0.5, 96, -90, 6, 58},
            {-75, 0.5, 95, 0, 6, 52}};
    private static final com.dan.heidelberg.core.Solids[] solidsCache = new com.dan.heidelberg.core.Solids[2];

    /** Kollisionsflächen ohne die weggenommene Seite. */
    public static synchronized com.dan.heidelberg.core.Solids solids(Mesh m, int i) {
        Schnitt.Cap cap = get(m, i);
        if (solidsCache[i] == null || solidsCacheFor != m) {
            if (solidsCacheFor != m) java.util.Arrays.fill(solidsCache, null);
            solidsCacheFor = m;
            solidsCache[i] = new com.dan.heidelberg.core.Solids(m, cap);
        }
        return solidsCache[i];
    }
    private static Mesh solidsCacheFor;

    private static final Schnitt.Cap[] cache = new Schnitt.Cap[2];
    private static Mesh cachedFor;

    public static synchronized Schnitt.Cap get(Mesh m, int i) {
        if (cachedFor != m) { java.util.Arrays.fill(cache, null); cachedFor = m; }
        if (cache[i] == null) {
            long t0 = System.nanoTime();
            cache[i] = i == 0
                    ? Schnitt.build(m, NAMES[0], 0, -1, -70, -100, 320, -24)
                    : Schnitt.build(m, NAMES[1], 2, +1, 95, -200, 60, -24);
            System.out.printf("Schnitt %d: %,d Dreiecke der Füllung, %.0f ms%n", i, cache[i].n, (System.nanoTime() - t0) / 1e6);
        }
        return cache[i];
    }
}
