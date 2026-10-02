package com.dan.heidelberg.world;

import com.dan.heidelberg.core.Animals;
import com.dan.heidelberg.core.Terrain;
import com.dan.road.Ground;
import com.dan.road.Network;
import com.dan.road.Roads;
import com.dan.road.Way;
import com.dan.road.WayType;

/**
 * Wege um Heidelberg mit dem Wege-Paket ({@link com.dan.road}), im Zustand von 1619: gestampfte Kies-
 * und Erdwege, keine Asphaltstraßen und kein Verkehr (Kutschen und Reiter folgen in Phase 8).
 * <ul>
 *   <li>die Uferwege am Neckar, an beiden Ufern vom Karlstor bis Neuenheim und Bergheim;</li>
 *   <li>die Hauptgasse der Altstadt vom Karlstor zum Bergheimer Tor;</li>
 *   <li>der Burgweg vom Kornmarkt in Kehren hinauf zum Torturm im Westen des Schlosses;</li>
 *   <li>der Weg am Hang des Heiligenbergs (heute Philosophenweg);</li>
 *   <li>der Weg vom Schloss hinauf zum Königstuhl.</li>
 * </ul>
 * Die Linien sind nach Karten genähert, nicht vermessen. Das Gelände wird unter den Wegen geebnet,
 * daneben Damm und Einschnitt; aus der Ferne malt die Bodenfarbe den Weg.
 */
public final class Roadways implements Terrain.Shaper {
    public final Network net = new Network();
    public Roads roads;
    public final Way southBank, northBank, hauptgasse, burgweg, hangweg, koenigstuhlweg;
    private final Terrain t;

    /** Hauptgasse (x, z): vom Karlstor nach Westen zum Bergheimer Tor. */
    static final double[] HAUPT = {330, -470, 100, -420, -140, -380, -380, -345, -560, -320, -760, -250, -930, -120, -1080, 60};
    /** Burgweg: vom Kornmarkt in Kehren hinauf zum Schlosstor. */
    static final double[] BURG = {-335, -290, -280, -250, -215, -215, -170, -190, -200, -150, -150, -120, -118, -92, -96, -58, -82, -26, -70, 2};
    /** Weg am Südhang des Heiligenbergs. */
    static final double[] HANG = {-1100, -1175, -700, -1195, -300, -1205, 100, -1215, 450, -1195};
    /** Weg hinauf zum Königstuhl (Molkenkur), beginnt über dem Hortus. */
    static final double[] KOENIG = {150, 190, 230, 300, 330, 390, 400, 470, 490, 560, 560, 650, 650, 760, 740, 860, 782, 955};

    Roadways(Terrain t) {
        this.t = t;
        WayType lane = WayType.track();
        lane.name = "Landstraße";
        lane.laneWidth = 4.6f; lane.verge = 0.8f; lane.thickness = 0.08f; lane.maxGrade = 0.12f; lane.smoothing = 30;
        lane.surface = new float[]{0.17f, 0.15f, 0.12f}; lane.worn = new float[]{0.22f, 0.19f, 0.15f};
        WayType gasse = lane.copy();
        gasse.name = "Gasse"; gasse.laneWidth = 6.0f; gasse.verge = 0.3f; gasse.surface = new float[]{0.14f, 0.13f, 0.115f};
        WayType steil = WayType.track();
        steil.name = "Burgweg"; steil.laneWidth = 3.4f; steil.maxGrade = 0.24f; steil.smoothing = 14;
        steil.surface = new float[]{0.18f, 0.145f, 0.11f}; steil.worn = new float[]{0.25f, 0.2f, 0.15f};
        southBank = net.add(new Way(lane, false, bank(t, 1800, -1500, +1)));
        northBank = net.add(new Way(lane.copy(), false, bank(t, 700, -1500, -1)));
        hauptgasse = net.add(new Way(gasse, false, HAUPT));
        burgweg = net.add(new Way(steil, false, BURG));
        hangweg = net.add(new Way(WayType.path(), false, HANG));
        koenigstuhlweg = net.add(new Way(WayType.path(), false, KOENIG));
        double[] o = new double[6];
        net.build(new Ground() {
            @Override public float height(double x, double z) { return (float) t.exact(x, z); }
            @Override public float water(double x, double z) {
                t.nearest(x, z, o);
                return o[0] < o[2] + 1 ? (float) o[1] : Float.NaN;
            }
        });
        t.shaper = this;
    }

    /**
     * Uferweg: Punkte im Abstand von der Mittellinie des Neckars (halbe Breite + 24 m) auf der Seite
     * side (+1 Süden, −1 Norden), von x = xFrom bis x = xTo, etwa alle 60 m.
     */
    private static double[] bank(Terrain t, double xFrom, double xTo, int side) {
        java.util.List<Double> out = new java.util.ArrayList<>();
        double last = Double.NaN, lastZ = 0;
        for (int i = 1; i + 1 < t.riverPoints(); i++) {
            double x = t.riverX(i);
            if (x > xFrom || x < xTo) continue;
            double dx = t.riverX(i + 1) - t.riverX(i - 1), dz = t.riverZ(i + 1) - t.riverZ(i - 1), l = Math.hypot(dx, dz);
            double nx = side * dz / l, nz = -side * dx / l;      // nach Süden bzw. Norden (Fluss fließt nach Westen)
            double off = t.riverHalf(i) + 26;
            double px = x + nx * off, pz = t.riverZ(i) + nz * off;
            if (!Double.isNaN(last) && Math.hypot(px - last, pz - lastZ) < 55) continue;
            out.add(px); out.add(pz);
            last = px; lastZ = pz;
        }
        double[] a = new double[out.size()];
        for (int i = 0; i < a.length; i++) a[i] = out.get(i);
        return a;
    }

    /** Nach dem Bau des Geländes: Geometrie auf dem sichtbaren Boden; kein Verkehr. */
    void attach() {
        roads = new Roads(net, (x, z) -> t.sample(x, z), 1619);
        roads.radius = 1500;
        for (Way w : new Way[]{southBank, northBank, hauptgasse, burgweg, hangweg, koenigstuhlweg}) {
            roads.traffic.flow[w.index] = 0;
            roads.traffic.slowFlow[w.index] = 0;
        }
        roads.traffic.rates();
        roads.traffic.populate();
    }

    // ------------------------------------------------------------ Gelände

    @Override public double shape(double x, double z, double h) { return net.shape(x, z, h); }

    private static final ThreadLocal<Network.Hit> HIT = ThreadLocal.withInitial(Network.Hit::new);

    @Override public void paint(float x, float z, float foot, float[] rgb) {
        Network.Hit h = HIT.get();
        if (!net.locate(x, z, h) || h.cover <= 0.001f) return;
        WayType ty = h.way.type;
        float[] c = ty.paving == WayType.Paving.ASPHALT ? ty.worn : ty.surface;
        float k = h.cover * (ty.kind == WayType.Kind.PATH ? 0.7f : 0.95f);
        float snow = com.dan.heidelberg.core.Thermal.snow;
        if (snow > 0.01f) k *= 1 - Math.min(1, snow * 2);
        float r = c[0] * 0.9f, g = c[1] * 0.9f, b = c[2] * 0.9f;
        rgb[0] += (r - rgb[0]) * k; rgb[1] += (g - rgb[1]) * k; rgb[2] += (b - rgb[2]) * k;
    }

    /** Wie weit (x, z) befestigt ist (für Gras und Bäume). */
    public float cover(double x, double z) { return net.cover(x, z); }

    /** Abstand von (x, z) zum Rand des nächsten Weges (m), höchstens 30; negativ darauf. */
    public float clearance(double x, double z) {
        Network.Hit h = HIT.get();
        if (!net.locate(x, z, h)) return 30;
        return (float) (Math.abs(h.u) - h.way.type.half());
    }

    // ------------------------------------------------------------ Schritt

    /** Ein Zeitschritt: Wetter (Regen 0..1, Schnee 0..1, Dunkelheit 0..1), Wind und Kamera. */
    public void update(float time, double dt, int day, float rain, float snow, float dark, float wind, double wx, double wz,
                       Animals animals, double speed, double camX, double camY, double camZ) {
        Roads r = roads;
        if (r == null) return;
        var w = r.weather;
        w.rain = rain; w.snow = snow; w.dark = dark;
        w.windSpeed = wind * 9;
        w.windDirection = (float) Math.toDegrees(Math.atan2(wz, wx));
        w.timeScale = (float) speed;
        w.poles = false;
        r.update(time, (float) (dt * Math.min(speed, 4)), camX, camY, camZ);
    }

    /** Dunkelheit 0..1 aus der Sonnenhöhe (Grad): ab etwa 3° über dem Horizont bis 7° darunter. */
    public static float dark(double elevDeg) {
        double u = Math.max(0, Math.min(1, (elevDeg + 7) / 10));
        return (float) (1 - u * u * (3 - 2 * u));
    }
}
