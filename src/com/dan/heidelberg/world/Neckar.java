package com.dan.heidelberg.world;

import com.dan.forest.LeafFall;
import com.dan.heidelberg.core.LeafQuads;
import com.dan.heidelberg.core.Terrain;
import com.dan.river.Drift;
import com.dan.river.FlowField;
import com.dan.river.RiverPath;
import com.dan.river.WaterSurface;

/**
 * Der Neckar mit dem Fluss-Paket ({@link com.dan.river}): Strömung nach Gefälle, Breite und Kurven,
 * Wellen und Schaumstreifen, die mit dem Wasser ziehen, und Treibgut um die Kamera. Laub, das auf den
 * Fluss fällt, treibt weiter.
 * <p>
 * Der Neckar ist bei Heidelberg gut 100 m breit, staugeregelt und ruhig (um 3 m tief, unter einem
 * halben Meter je Sekunde); Wellen entstehen vor allem durch Wind. Die Tiefe ist angenommen.
 */
public final class Neckar implements LeafFall.Water {
    public final RiverPath path;
    public final FlowField flow;
    public final WaterSurface surface = new WaterSurface();
    public final Drift drift = new Drift(LeafQuads.CAP);
    public final LeafQuads quads = new LeafQuads();
    private final FlowField.Flow f = new FlowField.Flow();
    
    public Neckar(Terrain t) {
        int n = t.riverPoints();
        double[][] nodes = new double[n][];
        for (int i = 0; i < n; i++) nodes[i] = new double[]{t.riverX(i), t.riverZ(i), t.riverLevel(i), t.riverHalf(i)};
        path = new RiverPath(nodes, 5, 14);
        flow = new FlowField(path);
        flow.depth = 3.0f;
        drift.foamDensity = 1.2f;
        drift.foamSize = 0.11f;
        drift.leafDensity = 0.45f;
        drift.twigDensity = 0.25f;
        surface.amplitude = 0.45f;
    }

    // ------------------------------------------------------------ Laub auf dem Wasser

    @Override public float level(float x, float z) {
        synchronized (f) {
            return flow.sample(x, z, f) && f.wet ? f.level : Float.NaN;
        }
    }

    @Override public boolean take(float x, float y, float z, float r, float g, float b, float size) {
        return drift.add(x, z, Drift.LEAF, r, g, b, size) && setY(y);
    }

    private boolean setY(float y) { drift.y[drift.n - 1] = y; return true; }

    // ------------------------------------------------------------ Zeitschritt

    /**
     * Treibgut bewegen und für den Bildrechner bereitlegen; Wind 0..1 (1 ≈ 9 m/s) in Richtung (wx, wz)
     * kräuselt das Wasser. Treibgut gibt es nur, wenn die Kamera nahe am Fluss ist.
     */
    public void update(double dt, double wind, double wx, double wz, double camX, double camZ) {
        surface.wind = (float) (wind * 9);
        surface.windDir = (float) Math.toDegrees(Math.atan2(wz, wx));
        boolean near;
        synchronized (f) {
            RiverPath.Loc L = f.loc;
            near = path.locate(camX, camZ, L) || nearRiver(camX, camZ);
        }
        if (near || drift.n > 0) drift.step((float) dt, flow, camX, camZ, near ? 110 : 1);
        int n = drift.quads(quads.xyz, quads.rgb);
        for (int i = 0; i < n; i++) { quads.nrm[3 * i] = 0; quads.nrm[3 * i + 1] = 1; quads.nrm[3 * i + 2] = 0; }
        quads.n = n;
    }

    /** Liegt der Fluss höchstens 110 m entfernt? (grob über acht Richtungen) */
    private boolean nearRiver(double x, double z) {
        RiverPath.Loc L = new RiverPath.Loc();
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            for (double d = 25; d <= 110; d += 42) if (path.locate(x + Math.cos(a) * d, z + Math.sin(a) * d, L)) return true;
        }
        return false;
    }
}
