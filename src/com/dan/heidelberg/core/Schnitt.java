package com.dan.heidelberg.core;

import java.util.Arrays;

/**
 * Phase 9, Schnitt: Eine senkrechte Schnittebene (x = konst. oder z = konst.) nimmt alles auf einer Seite weg, und die
 * Schnittfläche wird ausgefüllt. Die Füllung entsteht aus dem Netz selbst: In Spalten von 20 cm legt das Programm einen
 * senkrechten Strahl durch die Ebene, sammelt alle Dreiecke, die sie dort schneiden, und füllt von jeder Fläche, die nach
 * oben zeigt (hier beginnt Masse), bis zur nächsten, die nach unten zeigt (hier endet sie). So bleiben Räume, Gänge und
 * Türöffnungen leer, Mauern, Gewölbe und Erdreich voll. Das gilt, solange die Netze Innenflächen nach innen zeigen; offene,
 * einseitige Flächen (Dächer ohne Unterseite) füllen sich bis zur Sohle durch. Der Rechenweg läuft einmal beim Einschalten.
 */
public final class Schnitt {
    private Schnitt() { }

    /** Eine fertige Schnittfläche samt Ebene. */
    public static final class Cap {
        public final String name;
        /** Achse der Ebene: 0 = x, 2 = z; val = Lage; side = +1 nimmt Koordinaten > val weg, −1 solche < val. */
        public final int axis, side;
        public final double val;
        /** Ebene in Weltkoordinaten: weggenommen wird n·p > c. */
        public final double[] plane;
        /** Dreiecke der Füllung: 9 Werte je Dreieck (x y z je Ecke), Material je Dreieck, Normale der Fläche. */
        public final float[] tri;
        public final byte[] mat;
        public final int n;
        public final float[] nrm;
        Cap(String name, int axis, int side, double val, float[] tri, byte[] mat, int n) {
            this.name = name; this.axis = axis; this.side = side; this.val = val;
            this.tri = tri; this.mat = mat; this.n = n;
            double[] pl = new double[4];
            pl[axis] = side;
            pl[3] = side * val;
            this.plane = pl;
            nrm = new float[]{axis == 0 ? side : 0, 0, axis == 2 ? side : 0};
        }
        public boolean removed(double x, double y, double z) { return plane[0] * x + plane[2] * z > plane[3]; }
    }

    private static boolean solid(int m) {
        return !(Mat.water(m) || m == Mat.GLASS || m == Mat.LIGHT || m == Mat.MIRROR || m == Mat.ANIMAL || m == Mat.HEDGE);
    }

    private static boolean earth(int m) { return m == Mat.TERRAIN || m == Mat.LAWN || m == Mat.GRAVEL; }

    /**
     * Baut die Füllung für die Ebene. u0..u1: Bereich entlang der Ebene (bei x = konst. das z, bei z = konst. das x),
     * yBot: Sohle des Schnittblocks.
     */
    public static Cap build(Mesh m, String name, int axis, int side, double val, double u0, double u1, double yBot) {
        final double step = 0.2;
        final int nc = (int) Math.ceil((u1 - u0) / step);
        final int ua = axis == 0 ? 2 : 0;      // Achse entlang der Ebene
        float[][] hy = new float[nc][];
        byte[][] hs = new byte[nc][];
        int[] cnt = new int[nc];
        final float[] P = m.pos;
        final int[] I = m.idx;
        double[] pu = new double[4], py = new double[4];
        for (int t = 0; t < m.nt; t++) {
            if (m.grp[t] == 1) continue;
            int mt = m.mat[t];
            if (!solid(mt)) continue;
            float ny = m.fn[3 * t + 1];
            if (Math.abs(ny) < 0.02f) continue;
            int a = I[3 * t], b = I[3 * t + 1], c = I[3 * t + 2];
            double da = P[3 * a + axis] - val, db = P[3 * b + axis] - val, dc = P[3 * c + axis] - val;
            double mn = Math.min(da, Math.min(db, dc)), mx = Math.max(da, Math.max(db, dc));
            if (mn > 0 || mx < 0 || (mn == 0 && mx == 0)) continue;
            // Schnittpunkte der Kanten mit der Ebene
            int np = 0;
            int[] vs = {a, b, c};
            double[] ds = {da, db, dc};
            for (int i = 0; i < 3; i++) {
                int j = (i + 1) % 3;
                if (ds[i] == 0) { pu[np] = P[3 * vs[i] + ua]; py[np] = P[3 * vs[i] + 1]; np++; }
                else if (ds[i] * ds[j] < 0) {
                    double k = ds[i] / (ds[i] - ds[j]);
                    pu[np] = P[3 * vs[i] + ua] + (P[3 * vs[j] + ua] - P[3 * vs[i] + ua]) * k;
                    py[np] = P[3 * vs[i] + 1] + (P[3 * vs[j] + 1] - P[3 * vs[i] + 1]) * k;
                    np++;
                }
                if (np >= 4) break;
            }
            if (np < 2) continue;
            int lo = 0, hi = 0;
            for (int i = 1; i < np; i++) { if (pu[i] < pu[lo]) lo = i; if (pu[i] > pu[hi]) hi = i; }
            double ux = pu[lo], uy = py[lo], vx = pu[hi], vy = py[hi];
            if (vx - ux < 1e-9) continue;
            int c0 = (int) Math.ceil((ux - u0) / step - 0.5), c1 = (int) Math.floor((vx - u0) / step - 0.5);
            c0 = Math.max(0, c0); c1 = Math.min(nc - 1, c1);
            for (int ci = c0; ci <= c1; ci++) {
                double uc = u0 + (ci + 0.5) * step;
                double y = uy + (vy - uy) * (uc - ux) / (vx - ux);
                int k = cnt[ci];
                if (hy[ci] == null) { hy[ci] = new float[8]; hs[ci] = new byte[8]; }
                else if (k == hy[ci].length) { hy[ci] = Arrays.copyOf(hy[ci], k * 2); hs[ci] = Arrays.copyOf(hs[ci], k * 2); }
                hy[ci][k] = (float) y;
                // Vorzeichen und Material in einem Byte: Material + 64 bei Fläche nach oben
                hs[ci][k] = (byte) (mt | (ny > 0 ? 64 : 0));
                cnt[ci] = k + 1;
            }
        }
        float[] tri = new float[9 * 4096];
        byte[] tm = new byte[4096];
        int n = 0;
        Integer[] ord = null;
        for (int ci = 0; ci < nc; ci++) {
            int k = cnt[ci];
            if (k == 0) continue;
            // nach y absteigend sortieren (Einfügesortierung)
            float[] ys = hy[ci];
            byte[] ss = hs[ci];
            for (int i = 1; i < k; i++) {
                float y = ys[i]; byte s = ss[i];
                int j = i - 1;
                while (j >= 0 && ys[j] < y) { ys[j + 1] = ys[j]; ss[j + 1] = ss[j]; j--; }
                ys[j + 1] = y; ss[j + 1] = s;
            }
            boolean in = false;
            float top = 0;
            int topMat = 0;
            double u = u0 + ci * step, uN = u + step;
            for (int i = 0; i <= k; i++) {
                float yEnd;
                if (i == k) { if (!in) break; yEnd = (float) yBot; }
                else {
                    boolean up = (ss[i] & 64) != 0;
                    if (!in && up) { in = true; top = ys[i]; topMat = ss[i] & 63; }
                    if (in && !up && ys[i] < top) { yEnd = ys[i]; in = false; }
                    else continue;
                }
                if (top - yEnd < 0.03f) continue;
                if (n + 2 > tm.length) { tri = Arrays.copyOf(tri, tri.length * 2); tm = Arrays.copyOf(tm, tm.length * 2); }
                byte mat = (byte) (earth(topMat) || yEnd == (float) yBot ? Mat.SCHNITT_ERDE : Mat.SCHNITT_STEIN);
                if (yEnd == (float) yBot && !earth(topMat)) mat = (byte) Mat.SCHNITT_STEIN;
                int o = 9 * n;
                // zwei Dreiecke je Rechteck
                double[][] q = {{u, top}, {uN, top}, {uN, yEnd}, {u, yEnd}};
                int[][] tt = {{0, 1, 2}, {0, 2, 3}};
                for (int[] tr : tt) {
                    for (int v : tr) {
                        tri[o++] = axis == 0 ? (float) val : (float) q[v][0];
                        tri[o++] = (float) q[v][1];
                        tri[o++] = axis == 0 ? (float) q[v][0] : (float) val;
                    }
                }
                tm[n] = mat; tm[n + 1] = mat;
                n += 2;
            }
        }
        return new Cap(name, axis, side, val, tri, tm, n);
    }
}
