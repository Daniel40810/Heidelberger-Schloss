package com.dan.heidelberg.tools;

import com.dan.heidelberg.core.Terrain;
import com.dan.heidelberg.world.Heidelberg;
import com.dan.heidelberg.world.World;

/**
 * Abgleich mit Karten: Lage und Höhe wichtiger Punkte im Modell gegen die Angaben aus Wikipedia,
 * OpenStreetMap und Mapcarta. Gibt je Punkt die Abweichung in Metern aus. Aufruf: Abgleich
 */
public final class Abgleich {
    private Abgleich() { }

    /** Name, Breite, Länge, Höhe ü. NN (NaN = unbekannt), Modell-x, Modell-z, Quelle. */
    private static final Object[][] P = {
            {"Königstuhl (Gipfel)", 49.39806, 8.72611, 567.8, Heidelberg.KOENIGSTUHL[0], Heidelberg.KOENIGSTUHL[1], "Wikipedia"},
            {"Heiligenberg (Gipfel)", 49.42615, 8.70626, 439.9, Heidelberg.HEILIGENBERG[0], Heidelberg.HEILIGENBERG[1], "Mapcarta, Wikipedia"},
            {"Alte Brücke", 49.4126, 8.7092, Double.NaN, Heidelberg.BRIDGE_X, Double.NaN, "OpenStreetMap"},
            {"Heiliggeistkirche", 49.4098, 8.7076, Double.NaN, Heidelberg.CHURCH[0], Heidelberg.CHURCH[1], "OpenStreetMap"},
    };

    public static void main(String[] a) {
        World w = Heidelberg.build(false);
        Terrain t = w.scene.terrain;
        double mLat = 111200, mLon = 111320 * Math.cos(Math.toRadians(Terrain.LAT));
        System.out.println("Punkt | Lage-Abweichung (m) | Modell-Höhe ü. NN | Soll | Quelle");
        for (Object[] p : P) {
            double rx = ((Double) p[2] - Terrain.LON) * mLon, rz = -((Double) p[1] - Terrain.LAT) * mLat;
            double mx = (Double) p[4], mz = Double.isNaN((Double) p[5]) ? rz : (Double) p[5];
            double off = Math.hypot(rx - mx, rz - mz);
            double h = Terrain.DATUM + t.sample(mx, mz);
            double hs = Terrain.DATUM + t.sample(rx, rz);
            System.out.printf(java.util.Locale.GERMANY, "%s | %.0f m (Soll x %.0f, z %.0f; Modell x %.0f, z %.0f) | %.0f m (am Soll-Ort %.0f m) | %s | %s%n",
                    p[0], off, rx, rz, mx, mz, h, hs, Double.isNaN((Double) p[3]) ? "-" : String.format("%.0f m", (Double) p[3]), p[6]);
        }
        System.out.printf(java.util.Locale.GERMANY, "Schlosshof: %.0f m ü. NN, Neckar: %.0f m ü. NN%n", Terrain.DATUM + t.sample(0, 0), Terrain.DATUM + t.sample(-440, -650) + 0);
    }
}
