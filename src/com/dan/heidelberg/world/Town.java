package com.dan.heidelberg.world;

import com.dan.heidelberg.core.Mat;
import com.dan.heidelberg.core.MeshBuilder;
import com.dan.heidelberg.core.Terrain;

/**
 * Die Stadt in einfachen Blöcken: Häuser mit Satteldach, die Heiliggeistkirche am Marktplatz, die
 * Alte Brücke mit dem Brückentor und ein Platzhalter für das Schloss (bis Phase 3). Alles aus
 * Kästen und Dächern, parametrisch; die Häuser stehen auf einem lockeren Raster, ausgerichtet am
 * Fluss, und lassen Wege und Ufer frei. Lage und Maße sind genähert.
 */
final class Town {
    private Town() { }

    // ------------------------------------------------------------ Bauteile

    /** Kasten mit Drehung yaw um (cx, cz), Halbbreiten hw (quer) und hd (längs), von y0 bis y1. */
    static void obox(MeshBuilder mb, double cx, double cz, double yaw, double hw, double hd, double y0, double y1, int m) {
        double c = Math.cos(yaw), s = Math.sin(yaw);
        double[][] k = new double[4][];
        double[][] q = {{-hw, -hd}, {hw, -hd}, {hw, hd}, {-hw, hd}};
        for (int i = 0; i < 4; i++) k[i] = new double[]{cx + q[i][0] * c - q[i][1] * s, cz + q[i][0] * s + q[i][1] * c};
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            double ex = k[j][0] - k[i][0], ez = k[j][1] - k[i][1], l = Math.hypot(ex, ez);
            double nx = ez / l, nz = -ex / l;
            mb.quad(new double[]{k[i][0], y0, k[i][1]}, new double[]{k[j][0], y0, k[j][1]}, new double[]{k[j][0], y1, k[j][1]},
                    new double[]{k[i][0], y1, k[i][1]}, nx, 0, nz, m);
        }
    }

    /** Satteldach über dem Rechteck: Traufe auf y, First rise darüber, First längs (Richtung yaw + 90°). */
    static void gable(MeshBuilder mb, double cx, double cz, double yaw, double hw, double hd, double y, double rise, double over, int roof, int wall) {
        double c = Math.cos(yaw), s = Math.sin(yaw);
        double w = hw + over, d = hd + over;
        double[] a0 = pt(cx, cz, c, s, -w, -d, y), a1 = pt(cx, cz, c, s, -w, d, y), b0 = pt(cx, cz, c, s, w, -d, y), b1 = pt(cx, cz, c, s, w, d, y);
        double[] r0 = pt(cx, cz, c, s, 0, -d, y + rise), r1 = pt(cx, cz, c, s, 0, d, y + rise);
        double sl = Math.hypot(w, rise);
        // Dachflächen: Normale zeigt nach außen und oben
        double nxl = -c * rise / sl, nzl = -s * rise / sl, nxr = c * rise / sl, nzr = s * rise / sl, ny = w / sl;
        mb.quad(a0, a1, r1, r0, nxl, ny, nzl, roof);
        mb.quad(b1, b0, r0, r1, nxr, ny, nzr, roof);
        // Giebel
        double fx = -s, fz = c;
        double[] g0 = pt(cx, cz, c, s, -hw, -hd, y), g1 = pt(cx, cz, c, s, hw, -hd, y), g2 = pt(cx, cz, c, s, -hw, hd, y), g3 = pt(cx, cz, c, s, hw, hd, y);
        double[] ra = pt(cx, cz, c, s, 0, -hd, y + rise * hw / w), rb = pt(cx, cz, c, s, 0, hd, y + rise * hw / w);
        mb.quad(g1, g0, ra, ra, -fx, 0, -fz, wall);
        mb.quad(g2, g3, rb, rb, fx, 0, fz, wall);
    }

    private static double[] pt(double cx, double cz, double c, double s, double u, double v, double y) {
        return new double[]{cx + u * c - v * s, y, cz + u * s + v * c};
    }

    /** Pyramidendach über dem Quadrat (Halbseite h) von Höhe y bis y + rise. */
    static void pyramid(MeshBuilder mb, double cx, double cz, double yaw, double h, double y, double rise, double over, int m) {
        double c = Math.cos(yaw), s = Math.sin(yaw), w = h + over;
        double[] top = {cx, y + rise, cz};
        double[][] k = {pt(cx, cz, c, s, -w, -w, y), pt(cx, cz, c, s, w, -w, y), pt(cx, cz, c, s, w, w, y), pt(cx, cz, c, s, -w, w, y)};
        double sl = Math.hypot(w, rise);
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            double mx = (k[i][0] + k[j][0]) / 2 - cx, mz = (k[i][2] + k[j][2]) / 2 - cz, l = Math.hypot(mx, mz);
            mb.quad(k[i], k[j], top, top, mx / l * rise / sl, w / sl, mz / l * rise / sl, m);
        }
    }

    // ------------------------------------------------------------ Häuser

    /** Schornsteine der Häuser (x, y der Oberkante, z) für den Rauch. */
    public static final java.util.List<double[]> CHIMNEYS = new java.util.ArrayList<>();

    /** Leuchtfenster in den Längswänden: zwei Reihen je Seite, höchstens drei Geschosse. */
    static void windows(MeshBuilder mb, double px, double pz, double yaw, double hw, double hd, double base, double eave, double floors) {
        double c = Math.cos(yaw), s = Math.sin(yaw);
        int n = Math.max(1, Math.min(3, (int) Math.floor((2 * hd - 1.6) / 3.3)));
        int fl = (int) Math.min(3, Math.floor(floors));
        for (int side = -1; side <= 1; side += 2) {
            double u = side * (hw + 0.03), nx = side * c, nz = side * s;
            for (int i = 0; i < n; i++) {
                double v = -hd + (i + 0.5) * (2 * hd) / n;
                for (int f = 0; f < fl; f++) {
                    double y0 = base + 1.1 + 3.2 * f, y1 = y0 + 1.55;
                    if (y1 > eave - 0.4) break;
                    double[] a = {px + u * c - (v - 0.55) * s, y0, pz + u * s + (v - 0.55) * c}, b = {px + u * c - (v + 0.55) * s, y0, pz + u * s + (v + 0.55) * c};
                    double[] cc = {b[0], y1, b[2]}, d = {a[0], y1, a[2]};
                    mb.quad(a, b, cc, d, nx, 0, nz, Mat.LIGHT);
                }
            }
        }
    }

    /** Häuser auf einem lockeren Raster; liefert die Anzahl. */
    static int houses(MeshBuilder mb, Terrain t, Roadways rw, java.util.Random rnd, double[][] keepFree) {
        double keepEdge = mb.maxEdge;
        mb.maxEdge = 1e9;
        mb.swayFn = null; mb.swayValue = 0;
        float[] gm = new float[5];
        int n = 0;
        double step = 15;
        CHIMNEYS.clear();
        java.util.Random chim = new java.util.Random(404);
        for (double z = -1800; z < 1800; z += step) {
            for (double x = -1800; x < 1800; x += step) {
                double px = x + (rnd.nextDouble() - 0.5) * 4, pz = z + (rnd.nextDouble() - 0.5) * 4;
                t.ground((float) px, (float) pz, gm);
                float bank = gm[0], tn = gm[3];
                if (tn < 0.55f || bank < 28) continue;
                if (rnd.nextDouble() > 0.55 + 0.45 * tn) continue;
                if (rw != null && rw.clearance(px, pz) < 7) continue;
                boolean skip = false;
                for (double[] f : keepFree) if (Math.hypot(px - f[0], pz - f[1]) < f[2]) { skip = true; break; }
                if (skip) continue;
                double yaw = Math.atan2(gm[2], gm[1]) + (rnd.nextDouble() < 0.5 ? 0 : Math.PI / 2);
                double hw = 4 + 2.2 * rnd.nextDouble(), hd = 5 + 3.5 * rnd.nextDouble();
                double c = Math.cos(yaw), s = Math.sin(yaw);
                double lo = 1e9, hi = -1e9;
                for (double[] q : new double[][]{{-hw, -hd}, {hw, -hd}, {hw, hd}, {-hw, hd}, {0, 0}}) {
                    double h = t.sample(px + q[0] * c - q[1] * s, pz + q[0] * s + q[1] * c);
                    lo = Math.min(lo, h); hi = Math.max(hi, h);
                }
                if (hi - lo > 4.5) continue;
                // Altstadt hoch (drei Geschosse), Rand niedrig
                double floors = 2 + (rnd.nextDouble() < 0.35 + 0.4 * tn ? 1 : 0) + (rnd.nextDouble() < 0.2 ? 1 : 0);
                double wall = 3.2 * floors + 0.6;
                double eave = hi + wall;
                obox(mb, px, pz, yaw, hw, hd, lo - 1.2, eave, Mat.WALL);
                double rise = (hw + 0.5) * (0.85 + 0.5 * rnd.nextDouble());
                gable(mb, px, pz, yaw, hw, hd, eave, rise, 0.5, Mat.ROOF, Mat.WALL);
                windows(mb, px, pz, yaw, hw, hd, hi, eave, floors);
                if (chim.nextDouble() < 0.3) {
                    double v = hd * 0.55 * (chim.nextBoolean() ? 1 : -1);
                    double qx = px - v * s, qz = pz + v * c, top = eave + rise + 1.5;
                    obox(mb, qx, qz, yaw, 0.45, 0.45, eave + rise * 0.3, top, Mat.WALL);
                    CHIMNEYS.add(new double[]{qx, top + 0.1, qz});
                }
                n++;
            }
        }
        mb.maxEdge = keepEdge;
        return n;
    }

    // ------------------------------------------------------------ Heiliggeistkirche

    /** Die Kirche am Marktplatz: Langhaus mit hohem Dach, Westturm; Mitte (x, z), Längsachse nach Osten. */
    static void church(MeshBuilder mb, Terrain t, double cx, double cz) {
        double keep = mb.maxEdge;
        mb.maxEdge = 1e9;
        double base = t.sample(cx, cz) - 1.0;
        double yaw = Math.PI / 2;                       // Länge (v) nach Osten
        obox(mb, cx, cz, yaw, 10.5, 23, base, base + 19, Mat.SANDSTONE);
        gable(mb, cx, cz, yaw, 10.5, 23, base + 19, 13, 0.8, Mat.ROOF, Mat.SANDSTONE);
        // Chor
        obox(mb, cx + 31, cz, yaw, 7, 9, base, base + 15, Mat.SANDSTONE);
        gable(mb, cx + 31, cz, yaw, 7, 9, base + 15, 9, 0.6, Mat.ROOF, Mat.SANDSTONE);
        // Turm im Westen
        obox(mb, cx - 29, cz, 0, 6.5, 6.5, base, base + 44, Mat.SANDSTONE);
        pyramid(mb, cx - 29, cz, 0, 6.5, base + 44, 16, 0.8, Mat.ROOF);
        mb.maxEdge = keep;
    }

    // ------------------------------------------------------------ Alte Brücke

    /**
     * Die Alte Brücke wie um 1619 (Merian): ein Holzsteg auf acht steinernen Pfeilern mit Dach, am
     * Südufer das Brückentor mit zwei Türmen, am Nordufer ein Turm. Mitte bei (x, ·); die Achse steht
     * quer zum Fluss. Liefert die Lage der Mitte und die Richtung (zum Nordufer).
     */
    static double[] bridge(MeshBuilder mb, Terrain t, double atX) {
        double keep = mb.maxEdge;
        mb.maxEdge = 1e9;
        int k = 1;
        for (int i = 1; i + 1 < t.riverPoints(); i++) if (Math.abs(t.riverX(i) - atX) < Math.abs(t.riverX(k) - atX)) k = i;
        double cx = t.riverX(k), cz = t.riverZ(k), hf = t.riverHalf(k), ws = t.riverLevel(k);
        double tx = t.riverX(k + 1) - t.riverX(k - 1), tz = t.riverZ(k + 1) - t.riverZ(k - 1), l = Math.hypot(tx, tz);
        tx /= l; tz /= l;
        double nx = -tz, nz = tx;                        // zum Nordufer (Fluss fließt nach Westen)
        if (nz > 0) { nx = -nx; nz = -nz; }
        double yaw = Math.atan2(-nx, nz);                // v-Achse des Kastens liegt entlang (nx, nz)
        double half = hf + 36;                           // halbe Länge der Brücke
        double deck = ws + 6.8;
        // Pfeiler: acht im Wasser, gleichmäßig verteilt
        for (int p = 0; p < 8; p++) {
            double u = -hf * 0.88 + (p + 0.5) * (hf * 1.76) / 8;
            double px = cx + nx * u, pz = cz + nz * u;
            obox(mb, px, pz, yaw, 3.6, 2.2, ws - 4, deck, Mat.SANDSTONE);
            // Eisbrecher stromauf (nach Osten): ein niedrigerer Vorbau
            double ux = Math.cos(yaw), uz = Math.sin(yaw);
            if (ux < 0) { ux = -ux; uz = -uz; }
            obox(mb, px + ux * 4.6, pz + uz * 4.6, yaw, 1.3, 1.2, ws - 4, ws + 4.2, Mat.SANDSTONE);
        }
        // Landpfeiler und Fahrbahn: durchgehende Bohlen über die ganze Länge
        obox(mb, cx, cz, yaw, 3.4, half, deck, deck + 0.7, Mat.WOOD);
        // Dach über dem Steg: Holzgerüst, Satteldach quer zur Achse
        obox(mb, cx, cz, yaw, 3.5, half * 0.84, deck + 0.7, deck + 3.6, Mat.WOOD);
        gable(mb, cx, cz, yaw, 3.5, half * 0.84, deck + 3.6, 2.3, 0.7, Mat.ROOF, Mat.WOOD);
        // Brückentor am Südufer: zwei Türme beidseits der Durchfahrt, darüber ein Bau
        double sx = cx - nx * (half - 4), sz = cz - nz * (half - 4);
        double gy = Math.max(t.sample(sx, sz), deck) - 0.5;
        for (int sd = -1; sd <= 1; sd += 2) {
            double ox = sx + (-nz) * 6.0 * sd, oz = sz + nx * 6.0 * sd;
            obox(mb, ox, oz, yaw, 4.2, 4.2, gy - 1, gy + 24, Mat.SANDSTONE);
            pyramid(mb, ox, oz, yaw, 4.2, gy + 24, 12, 0.7, Mat.ROOF);
        }
        obox(mb, sx, sz, yaw, 3.6, 4.2, gy + 7, gy + 17, Mat.SANDSTONE);
        // Turm am Nordufer
        double nxp = cx + nx * (half - 6), nzp = cz + nz * (half - 6);
        double ny = Math.max(t.sample(nxp, nzp), deck) - 0.5;
        obox(mb, nxp, nzp, yaw, 4.6, 4.6, ny - 1, ny + 18, Mat.SANDSTONE);
        pyramid(mb, nxp, nzp, yaw, 4.6, ny + 18, 10, 0.7, Mat.ROOF);
        mb.maxEdge = keep;
        return new double[]{cx, cz, nx, nz};
    }

    // ------------------------------------------------------------ Schloss (Platzhalter)

    /**
     * Ein Platzhalter für das Schloss, bis Phase 3 die Bauten einzeln baut: vier Flügel aus rotem
     * Sandstein um einen Hof von etwa 55 × 40 m auf dem Schlosshof, dazu zwei Türme. Die Maße sind
     * schematisch, nicht die der Bauten.
     */
    static void castlePlaceholder(MeshBuilder mb, Terrain t) {
        double keep = mb.maxEdge;
        mb.maxEdge = 1e9;
        double y0 = -1.5;
        double[][] wings = {{0, -34, 40, 8, 26}, {0, 34, 40, 8, 18}, {-34, 0, 8, 26, 22}, {34, 0, 8, 26, 22}};
        for (double[] w : wings) {
            obox(mb, w[0], w[1], 0, w[2], w[3], y0, w[4], Mat.SANDSTONE);
            boolean alongX = w[2] > w[3];
            gable(mb, w[0], w[1], alongX ? Math.PI / 2 : 0, alongX ? w[3] : w[2], alongX ? w[2] : w[3], w[4], 8, 0.6, Mat.ROOF, Mat.SANDSTONE);
        }
        obox(mb, -44, 40, 0, 5, 5, y0, 34, Mat.SANDSTONE);
        pyramid(mb, -44, 40, 0, 5, 34, 11, 0.6, Mat.ROOF);
        obox(mb, 46, -42, 0, 9, 9, y0, 20, Mat.SANDSTONE);
        mb.maxEdge = keep;
    }
}
