package com.dan.heidelberg.castle;

import com.dan.heidelberg.core.Engine3D;
import com.dan.heidelberg.core.Mat;
import com.dan.heidelberg.core.MeshBuilder;
import com.dan.heidelberg.core.Sprites;
import com.dan.heidelberg.core.Terrain;
import com.dan.heidelberg.effects.ParticleSystem;

import java.util.ArrayList;
import java.util.List;

/**
 * Fackelständer im Hof, um das Achteckbecken und in der Großen Grotte. Bei Nacht brennen sie: eine Flamme
 * (leuchtender Fleck mit Flackern), warmes Licht auf den Wänden in der Nähe (Punktlicht im Schattierer,
 * ohne Schatten) und ein dünner Rauchfaden. Tagsüber bleiben nur die Pfosten.
 */
public final class Torches {
    public volatile boolean enabled = true;
    /** Phase 9: in der Ruine brennen weder Fackeln noch Kerzen. */
    public volatile boolean ruin;
    /** Ort der Flammen (x, y, z). */
    public final List<double[]> flames = new ArrayList<>();
    private double smokeAcc;

    /** Pfosten bauen und die Orte der Flammen festlegen. */
    public void build(MeshBuilder mb, Terrain t) {
        flames.clear();
        // Hof: sechs Ständer um die Mitte
        for (int k = 0; k < 6; k++) {
            double a = Math.toRadians(30 + 60 * k);
            post(mb, 8 + 16 * Math.cos(a), 2 + 16 * Math.sin(a), 0.04, false, t);
        }
        // Achteckbecken: acht Ständer im Kreis
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(22.5 + 45 * k);
            post(mb, -70 + 10.5 * Math.cos(a), 102 + 10.5 * Math.sin(a), Double.NaN, false, t);
        }
        // Große Grotte: zwei Ständer vor der Kaskade
        post(mb, -76.5, 90, Double.NaN, true, t);
        post(mb, -63.5, 90, Double.NaN, true, t);
        // Säulenbrunnen
        post(mb, -112, 118, Double.NaN, false, t);
        post(mb, -124, 118, Double.NaN, false, t);
    }

    private void post(MeshBuilder mb, double x, double z, double baseY, boolean inside, Terrain t) {
        double y0 = Double.isNaN(baseY) ? t.stand(x, z, inside ? -1e9 : 1e9) : baseY;
        double h = 1.9, w = 0.06;
        mb.box(x - w, y0, z - w, x + w, y0 + h, z + w, Mat.WOOD, false);
        // Schale oben
        mb.box(x - 0.14, y0 + h, z - 0.14, x + 0.14, y0 + h + 0.09, z + 0.14, Mat.WOOD, true);
        flames.add(new double[]{x, y0 + h + 0.2, z});
    }

    /** Kerzen in den Räumen (Phase 8) an oder aus. */
    public volatile boolean candlesEnabled = true;
    /** Höchstzahl der Lichter, die der Bildrechner je Pixel prüft. */
    static final int MAX_LIGHTS = 28;

    /**
     * Pro Bild: Lichter und Flammen für die Engine setzen. night 0..1 (1 = dunkel), time in Sekunden,
     * Kamera zur Auswahl der Nähe. Füllt r.torches, r.sprites und streut Rauchfäden. Seit Phase 8 gehören auch die
     * Kerzen der Räume dazu: der Saal brennt, wenn es dunkel wird, Fass und Grotten immer. Von allen Lichtern kommen
     * nur die nächsten {@link #MAX_LIGHTS} in den Bildrechner.
     */
    public void update(Engine3D r, ParticleSystem ps, double time, double dt, double camX, double camY, double camZ, float night) {
        r.sprites.clear();
        float on = enabled && !ruin ? Math.max(0, Math.min(1, (night - 0.3f) / 0.4f)) : 0;
        float[] gOn = new float[Candles.GROUPS];
        if (candlesEnabled && !ruin) {
            gOn[Candles.SAAL] = Math.max(0, Math.min(1, (night - 0.12f) / 0.3f));
            gOn[Candles.FASS] = 1; gOn[Candles.GROTTE] = 1;
        }
        // Kandidaten für die Lichtliste: x, y, z, r, g, b, Reichweite, Abstand²
        int nc = 0;
        float[] cand = new float[(flames.size() + Candles.LIGHTS.size()) * 8];
        smokeAcc += dt;
        boolean puff = smokeAcc > 0.35;
        if (puff) smokeAcc = 0;
        for (int i = 0; i < flames.size(); i++) {
            if (on <= 0.01f) break;
            double[] f = flames.get(i);
            double d2 = (f[0] - camX) * (f[0] - camX) + (f[1] - camY) * (f[1] - camY) + (f[2] - camZ) * (f[2] - camZ);
            double d = Math.sqrt(d2);
            if (d > 900) continue;
            double ph = i * 1.7;
            float fl = (float) (0.82 + 0.1 * Math.sin(time * 11 + ph) * Math.sin(time * 6.3 + 2 * ph) + 0.08 * Math.sin(time * 23 + ph * 3));
            // Flamme: großer weicher Schein und heller Kern
            r.sprites.add(Sprites.GLOW, f[0], f[1] + 0.12, f[2], 0.55, 2.6 * fl, 1.05 * fl, 0.30 * fl, 0.55 * on);
            r.sprites.add(Sprites.GLOW, f[0], f[1] + 0.12, f[2], 0.15, 6.0 * fl, 3.6 * fl, 1.2 * fl, 0.9 * on);
            if (d < 160) {
                float k = 7.0f * fl * on;
                int o = nc++ * 8;
                cand[o] = (float) f[0]; cand[o + 1] = (float) f[1]; cand[o + 2] = (float) f[2];
                cand[o + 3] = k; cand[o + 4] = k * 0.50f; cand[o + 5] = k * 0.18f; cand[o + 6] = 17f; cand[o + 7] = (float) d2;
            }
            if (puff && d < 120 && ps.n < ParticleSystem.CAP - 6000) {
                int q = ps.spawn(ParticleSystem.SMOKE, (float) f[0], (float) f[1] + 0.2f, (float) f[2], 0, 0.8f, 0, 0.08f, 4.5f, 0.16f * on, -1e9f, -1);
                if (q >= 0) ps.grow[q] = 0.14f;
            }
        }
        // Kerzen: kleine Flammen und Lichter der Räume
        List<Candles.Flame> cf = Candles.FLAMES;
        for (int i = 0; i < cf.size(); i++) {
            Candles.Flame f = cf.get(i);
            float g = gOn[f.group];
            if (g <= 0.01f) continue;
            double dx = f.x - camX, dy = f.y - camY, dz = f.z - camZ;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > 70 * 70) continue;
            double ph = i * 2.3;
            float fl = (float) (0.84 + 0.1 * Math.sin(time * 9 + ph) * Math.sin(time * 5.1 + 1.7 * ph) + 0.06 * Math.sin(time * 19 + ph * 2));
            r.sprites.add(Sprites.GLOW, f.x, f.y + 0.03, f.z, 0.24, 1.9 * fl, 0.80 * fl, 0.24 * fl, 0.50 * g);
            r.sprites.add(Sprites.GLOW, f.x, f.y + 0.03, f.z, 0.06, 5.0 * fl, 3.0 * fl, 1.0 * fl, 0.95 * g);
        }
        List<Candles.Light> cl = Candles.LIGHTS;
        for (int i = 0; i < cl.size(); i++) {
            Candles.Light l = cl.get(i);
            float g = gOn[l.group];
            if (g <= 0.01f) continue;
            double dx = l.x - camX, dy = l.y - camY, dz = l.z - camZ;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > 110 * 110) continue;
            float fl = (float) (0.9 + 0.07 * Math.sin(time * 7.3 + i * 1.9) * Math.sin(time * 3.7 + i));
            float k = g * fl;
            int o = nc++ * 8;
            cand[o] = (float) l.x; cand[o + 1] = (float) l.y; cand[o + 2] = (float) l.z;
            cand[o + 3] = l.r * k; cand[o + 4] = l.g * k; cand[o + 5] = l.b * k; cand[o + 6] = l.radius; cand[o + 7] = (float) d2;
        }
        // die nächsten Lichter auswählen (Auswahl durch Tausch, nc ist klein)
        int n = Math.min(nc, MAX_LIGHTS);
        for (int i = 0; i < n; i++) {
            int best = i;
            for (int j = i + 1; j < nc; j++) if (cand[j * 8 + 7] < cand[best * 8 + 7]) best = j;
            if (best != i) for (int q = 0; q < 8; q++) { float t = cand[i * 8 + q]; cand[i * 8 + q] = cand[best * 8 + q]; cand[best * 8 + q] = t; }
        }
        float[] tl = new float[n * 7];
        for (int i = 0; i < n; i++) System.arraycopy(cand, i * 8, tl, i * 7, 7);
        r.torches = tl;
        r.torchN = n;
    }
}
