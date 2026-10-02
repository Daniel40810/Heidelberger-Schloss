package com.dan.heidelberg.camera;

import com.dan.heidelberg.core.Terrain;

/**
 * Feste Blickpunkte auf Schloss, Altstadt und Tal. Jeder Blickpunkt ist ein Drehpunkt (x, z und eine
 * Höhe über dem Boden) mit Richtung, Neigung und Abstand für die Orbit-Kamera. Die Lagen der Orte
 * sind aus Karten gelesen und auf einige zehn Meter genau; sie werden mit der Geländekorrektur in
 * Phase 11 noch einmal geprüft.
 */
public final class Viewpoint {
    public final String name;
    public final double x, z, up, yawDeg, pitchDeg, dist;
    /** Steht im Raum unter einer Terrasse (Grotte), nicht auf ihrem Dach. */
    public final boolean inside;

    private Viewpoint(String name, double x, double z, double up, double yawDeg, double pitchDeg, double dist, boolean inside) {
        this.name = name; this.x = x; this.z = z; this.up = up; this.yawDeg = yawDeg; this.pitchDeg = pitchDeg; this.dist = dist; this.inside = inside;
    }

    private Viewpoint(String name, double x, double z, double up, double yawDeg, double pitchDeg, double dist) {
        this(name, x, z, up, yawDeg, pitchDeg, dist, false);
    }

    private static Viewpoint inside(String name, double x, double z, double up, double yawDeg, double pitchDeg, double dist) {
        return new Viewpoint(name, x, z, up, yawDeg, pitchDeg, dist, true);
    }

    public static volatile Viewpoint[] ALL = {
            new Viewpoint("Übersicht über das Tal", -250, -250, 20, 226, 15, 2300),
            new Viewpoint("Schloss vom Neckar", 0, 0, 14, 212, 9, 430),
            new Viewpoint("Schlosshof", 0, 0, 6, 160, 14, 95),
            new Viewpoint("Alte Brücke", -440, -650, 4, 180, 9, 230),
            new Viewpoint("Marktplatz und Heiliggeistkirche", -545, -318, 22, 150, 24, 170),
            new Viewpoint("Königstuhl", -200, -300, 0, 38, 14, 1700),
            new Viewpoint("Heiligenberg", -100, -200, 20, 194, 6, 2300),
            new Viewpoint("Neckartal von Osten", -300, -520, 10, 95, 7, 1500),
            // Phase 3 und 4: Schloss und Hortus. Lagen der Gebäude zueinander sind geschätzt (siehe Werkbuch).
            new Viewpoint("Hof zum Friedrichsbau", -0.4, 6.9, 3.78, 320, -9.5, 12),
            new Viewpoint("Hof zum Ottheinrichsbau", 12.0, 3.2, 3.56, 42, -8.4, 12),
            new Viewpoint("Schloss von oben", -167.2, 203.2, 130.97, 320, 28.4, 40),
            new Viewpoint("Hortus Hauptterrasse", -23.8, 113.9, 3.19, 78, -1.1, 10),
            new Viewpoint("Große Grotte", -70.0, 76.0, 1.29, 180, 5.2, 10),
            new Viewpoint("Hortus von oben", -3.8, 164.9, 59.91, 43, 30.1, 40),
            new Viewpoint("Gläserner Saalbau innen", 6.5, -26.7, 2.08, 236, -3.6, 6),
            // Phase 5: Brunnen
            new Viewpoint("Achteckbecken", -75.9, 106.2, 2.74, 306, -6.0, 10),
            new Viewpoint("Säulenbrunnen", -116.8, 112.4, 5.65, 41, -22.4, 8),
            new Viewpoint("Rhenusbecken", -70.0, 78.8, 2.60, 193, -0.0, 9),
            Viewpoint.inside("Kaskade der Großen Grotte", -70.0, 93.0, 1.37, 180, 3.1, 6),
            // Phase 6: Dampf und Licht
            new Viewpoint("Schloss über dem Nebelmeer", 0, 0, 10, 235, 4.2, 540),
            new Viewpoint("Schlosshof bei Nacht", 25, 0, 3, 280.3, -1.3, 56),
            new Viewpoint("Hortus bei Nacht", -80, 102, 2.4, 51.3, -1.0, 38),
            new Viewpoint("Altstadt im Morgenrauch", -470, -320, 15, 258.7, 27, 55),
            new Viewpoint("Regenbogen über dem Schlossberg", -30, 60, 30, 270, 2, 30),
            new Viewpoint("Abendrot über der Rheinebene", -49.4, 97.3, 5.5, 62.1, -3.6, 40),
            // Phase 8: Räume und Leben
            new Viewpoint("Das Große Fass im Fassbau", 43.4, 17.0, 3.3, 270, -12, 7.0),
            new Viewpoint("Saal bei Kerzenlicht", 8.0, -30.0, 2.6, 0, -4, 6.5),
            new Viewpoint("Hof mit Hofstaat", 10.0, 2.0, 1.5, 300, -3, 16),
    };

    /** Nummer des Blickpunkts mit diesem Namen (0, wenn es ihn nicht gibt). */
    public static int index(String name) {
        for (int i = 0; i < ALL.length; i++) if (ALL[i].name.equals(name)) return i;
        return 0;
    }
    public static volatile String[] NAMES;
    /** Die eingebauten Blickpunkte; die Datenbank (HEI_VIEW) darf ihre Werte ersetzen, nicht ihre Zahl und Namen. */
    public static final Viewpoint[] BUILTIN = ALL.clone();
    static {
        NAMES = namen(ALL);
    }

    private static String[] namen(Viewpoint[] a) {
        String[] n = new String[a.length];
        for (int i = 0; i < a.length; i++) n[i] = a[i].name;
        return n;
    }

    /** Ein Blickpunkt aus Werten, zum Beispiel aus der Datenbank. */
    public static Viewpoint of(String name, double x, double z, double up, double yawDeg, double pitchDeg, double dist, boolean inside) {
        return new Viewpoint(name, x, z, up, yawDeg, pitchDeg, dist, inside);
    }

    /** Ersetzt die Blickpunkte; vor dem Bau des Bedienfelds aufrufen. Zahl und Namen müssen die der eingebauten sein. */
    public static synchronized boolean ersetze(Viewpoint[] neu) {
        if (neu == null || neu.length != BUILTIN.length) return false;
        for (int i = 0; i < neu.length; i++) if (neu[i] == null || !neu[i].name.equals(BUILTIN[i].name)) return false;
        ALL = neu;
        NAMES = namen(neu);
        return true;
    }

    /** Pose für {@link CameraController#setPose}: Drehpunkt x, y, z, Gier, Nick, Abstand. */
    public double[] pose(Terrain t) {
        return new double[]{x, t.stand(x, z, inside ? -1e9 : 1e9) + up, z, yawDeg, pitchDeg, dist};
    }

    /** Wie {@link #pose(Terrain)}, aber der Drehpunkt liegt nicht in einer Wand oder einem Stamm. */
    public double[] pose(Terrain t, com.dan.heidelberg.core.Solids so) {
        double[] p = pose(t);
        if (so != null) {
            double[] pv = {p[0], p[1], p[2]};
            if (so.push(pv, CameraController.R_PIVOT)) { p[0] = pv[0]; p[1] = pv[1]; p[2] = pv[2]; }
        }
        return p;
    }
}
