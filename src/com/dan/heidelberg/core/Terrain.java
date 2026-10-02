package com.dan.heidelberg.core;

import java.util.stream.IntStream;

/**
 * Gelände von Heidelberg: das Neckartal am Austritt aus dem Odenwald in die Rheinebene, der
 * Schlossberg mit dem Schloss auf dem Jettenbühl, der Hang hinauf zum Königstuhl, gegenüber der
 * Heiligenberg. Koordinaten in Metern, x nach Osten, z nach Süden; Ursprung im Schlosshof
 * (49,4067° N, 8,7153° O), y = 0 ist der Schlosshof, rund 187 m über dem Meer.
 * <p>
 * Das Gelände folgt wenigen bekannten Höhen und ist sonst parametrisch: Der Neckar liegt bei 107 m,
 * die Altstadt bei 113 m, das Schloss rund 75 bis 80 m darüber, der Königstuhl bei 568 m, der
 * Heiligenberg bei 445 m; beide Berghänge steigen mit dem Abstand vom Talfuß (eine Linie, die dem
 * Fuß des Berges folgt) nach einer glatten Kurve durch diese Höhen, darauf liegt Rauschen für die
 * Rücken und Rinnen. Lage und Höhen der Berge sind genähert, nicht vermessen.
 * <p>
 * Das Netz besteht aus vier Gittern: Schloss (4 m), fein (8 m) über Altstadt und Tal, mittel (48 m)
 * bis 6,7 km, grob (672 m) bis 60 km. {@link #sample} liefert die Höhe genau so, wie die Dreiecke
 * sie zeigen.
 */
public final class Terrain {
    /** Höhe von y = 0 über dem Meer (Schlosshof). */
    public static final double DATUM = 187;
    /** Breite und Länge von Heidelberg für Sonne und Mond (Schlosshof). */
    public static final double LAT = 49.4067, LON = 8.7153;

    // ------------------------------------------------------------ Gitter

    /** Ein Höhengitter mit Löchern (dort liegt ein feineres Gitter). */
    public static final class Grid {
        public final double x0, z0, cell;
        public final int nx, nz;
        final double[][] holes;
        public final float[] h, nxs, nys, nzs;

        Grid(double x0, double x1, double z0, double z1, double cell, double[]... holes) {
            this.x0 = x0; this.z0 = z0; this.cell = cell;
            this.nx = (int) Math.round((x1 - x0) / cell);
            this.nz = (int) Math.round((z1 - z0) / cell);
            this.holes = holes;
            int n = (nx + 1) * (nz + 1);
            h = new float[n]; nxs = new float[n]; nys = new float[n]; nzs = new float[n];
        }

        public double x1() { return x0 + nx * cell; }
        public double z1() { return z0 + nz * cell; }

        boolean contains(double x, double z) { return x >= x0 && x <= x1() && z >= z0 && z <= z1(); }

        public boolean inHole(int i, int j) {
            double cx = x0 + (i + 0.5) * cell, cz = z0 + (j + 0.5) * cell;
            for (double[] o : holes) if (cx > o[0] && cx < o[1] && cz > o[2] && cz < o[3]) return true;
            return false;
        }

        float at(double x, double z) {
            double u = (x - x0) / cell, v = (z - z0) / cell;
            int i = Math.max(0, Math.min(nx - 1, (int) Math.floor(u))), j = Math.max(0, Math.min(nz - 1, (int) Math.floor(v)));
            float fx = (float) Math.max(0, Math.min(1, u - i)), fy = (float) Math.max(0, Math.min(1, v - j));
            int w = nx + 1, a = j * w + i;
            float ha = h[a], hb = h[a + 1], hc = h[a + w + 1], hd = h[a + w];
            return fx >= fy ? ha + (hb - ha) * fx + (hc - hb) * fy : ha + (hc - hd) * fx + (hd - ha) * fy;
        }
    }

    /** Gitterflächen (x0, x1, z0, z1): Schloss, fein, mittel; so gewählt, dass Löcher auf dem gröberen Gitter liegen. */
    static final double[] CASTLE = {-304, 304, -304, 304};
    static final double[] FINE = {-1824, 1824, -1824, 1824};
    static final double[] MIDGRID = {-6720, 6720, -6720, 6720};
    public final Grid castle = new Grid(CASTLE[0], CASTLE[1], CASTLE[2], CASTLE[3], 4);
    public final Grid fine = new Grid(FINE[0], FINE[1], FINE[2], FINE[3], 8, CASTLE);
    public final Grid mid = new Grid(MIDGRID[0], MIDGRID[1], MIDGRID[2], MIDGRID[3], 48, FINE);
    public final Grid far = new Grid(-60480, 60480, -60480, 60480, 672, MIDGRID);

    // ------------------------------------------------------------ Neckar

    /**
     * Lauf des Neckars als Stützpunkte: x, z, Wasserspiegel über y = 0, halbe Breite. Von Neckargemünd
     * im Osten zwischen Königstuhl und Heiligenberg hindurch, an der Altstadt und der Alten Brücke
     * vorbei (um 49,4126° N, 8,7092° O) nach Westen und Nordwesten in die Rheinebene bis zur Mündung
     * in Mannheim. Der Wasserspiegel liegt bei etwa 107 m ü. NN (y = −80), die halbe Breite bei 50 m.
     * Die Linie ist aus Karten genähert, nicht vermessen.
     */
    static final double[][] NODES = {
            {9000, 4600, -79.2, 45}, {7000, 3300, -79.5, 48}, {5200, 1750, -79.6, 50}, {4200, 1000, -79.7, 50},
            {3200, -100, -79.8, 50}, {2400, -440, -79.8, 50}, {1500, -620, -79.9, 50}, {800, -690, -80.0, 50},
            {200, -700, -80.1, 52}, {-440, -650, -80.6, 52}, {-900, -760, -80.8, 52}, {-1500, -1000, -81.0, 52},
            {-2300, -1350, -81.5, 55}, {-3200, -1450, -82.0, 55}, {-5000, -1700, -83.5, 60}, {-8000, -3000, -86.0, 65},
            {-12000, -5000, -90.0, 70}, {-16000, -7200, -94.0, 75}, {-18300, -8540, -96.0, 80}};

    /** Talfuß an der Südseite (Berg liegt östlich und südlich davon) und an der Nordseite (Heiligenberg liegt nördlich und östlich). */
    static final double[][] FOOT_S = {
            {9000, 4680}, {7000, 3380}, {5200, 1810}, {4200, 1060}, {3200, -40}, {2400, -380}, {1500, -560}, {800, -620},
            {200, -480}, {-300, -300}, {-650, 250}, {-900, 900}, {-1400, 2000}, {-1900, 3500}, {-2500, 5500}, {-3500, 9000}};
    static final double[][] FOOT_N = {
            {9000, -700}, {5000, -850}, {3500, -900}, {1500, -1000}, {0, -1100}, {-1000, -1150}, {-1500, -2000},
            {-2400, -2800}, {-3400, -3800}};
    /** Orte: Schlosshof, Königstuhl, Heiligenberg. */
    static final double[] CASTLE_XZ = {0, 0}, KOENIGSTUHL = {782, 961}, HEILIGENBERG = {-655, -2163};

    /** Unterteilter Lauf: Punkte im Abstand von rund 25 m. */
    final float[] rx, rz, re, rh;
    final int rn;

    private final double[] sTab, hTabS, mTabS, sTabN, hTabN, mTabN;

    // ------------------------------------------------------------ geebnete Stellen

    private final java.util.List<double[]> pads = new java.util.ArrayList<>();

    /** Ebnet den Boden um (x, z) im Radius r auf die Höhe level (NaN: die Höhe dort), Übergang über blend Meter. */
    public void pad(double x, double z, double r, double level, double blend) { pads.add(new double[]{x, z, r, level, blend}); }

    private final java.util.List<double[]> rects = new java.util.ArrayList<>();

    /**
     * Ebnet ein Rechteck (x0, z0)–(x1, z1) auf die Höhe level, außerhalb Übergang über blend Meter
     * (Terrassen des Gartens, Platz vor dem Schloss); wird wie {@link #pad} vor {@link #build} gesetzt.
     */
    public void carve(double x0, double z0, double x1, double z1, double level, double blend) {
        rects.add(new double[]{x0, z0, x1, z1, level, blend});
    }

    private final java.util.List<double[]> pits = new java.util.ArrayList<>();

    /**
     * Ausgehobener Raum unter einer Terrasse (Grotte): innerhalb des Rechtecks liegt das Gelände tief, darüber
     * liegt ein Dach aus Netz. Wer höher als roofY steht, steht auf dem Dach in der Höhe surface, wer tiefer steht, im Raum.
     */
    public void pit(double x0, double z0, double x1, double z1, double roofY, double surface) {
        pits.add(new double[]{x0, z0, x1, z1, roofY, surface});
    }

    /** Boden, auf dem etwas in der Höhe eyY an (x, z) steht: das Gelände, oder das Dach eines Raumes darunter. */
    public double stand(double x, double z, double eyY) {
        double h = sample(x, z);
        for (int i = 0; i < pits.size(); i++) {
            double[] p = pits.get(i);
            if (x >= p[0] && x <= p[2] && z >= p[1] && z <= p[3] && eyY > p[4]) return Math.max(h, p[5]);
        }
        return h;
    }

    /** Formt das Gelände nach der Formel und färbt den Boden (etwa Straßen); wird vor {@link #build} gesetzt. */
    public interface Shaper {
        double shape(double x, double z, double h);
        default void paint(float x, float z, float foot, float[] rgb) { }
    }

    public volatile Shaper shaper;

    private final java.util.List<double[]> bumps = new java.util.ArrayList<>();

    /** Hügel oder Mulde: Gauß-Beule der Höhe h und Breite r. */
    public void bump(double x, double z, double r, double h) { bumps.add(new double[]{x, z, r, h}); }

    // ------------------------------------------------------------ Bodenkarte

    private Raster rCastle, rFine, rMid;

    static final class Raster {
        final double x0, z0, cell;
        final int nx, nz;
        final float[] dist, fx, fz, town, forest;
        float[] edge;

        Raster(double x0, double x1, double z0, double z1, double cell) {
            this.x0 = x0; this.z0 = z0; this.cell = cell;
            nx = (int) Math.round((x1 - x0) / cell) + 1;
            nz = (int) Math.round((z1 - z0) / cell) + 1;
            int n = nx * nz;
            dist = new float[n]; fx = new float[n]; fz = new float[n]; town = new float[n]; forest = new float[n];
        }

        boolean contains(double x, double z) {
            return x >= x0 && z >= z0 && x < x0 + (nx - 1) * cell && z < z0 + (nz - 1) * cell;
        }
    }

    /** Bebautes Land als Ellipsen: Mitte x, z, Halbachsen a, b, Drehung (Grad): Altstadt, Neuenheim, Bergheim, Schlierbach. */
    static final double[][] TOWN = {
            {-400, -340, 620, 250, -26}, {-960, -1010, 300, 160, -20}, {-1250, 20, 260, 160, 0}, {900, -470, 260, 60, -5}};

    /** Wird nicht mehr gebraucht; bleibt für die Schnittstelle der Engine. */
    public volatile Thermal thermal;

    public Terrain() {
        java.util.List<float[]> pts = new java.util.ArrayList<>();
        int n = NODES.length;
        for (int k = 0; k < n - 1; k++) {
            double[] p0 = NODES[Math.max(0, k - 1)], p1 = NODES[k], p2 = NODES[k + 1], p3 = NODES[Math.min(n - 1, k + 2)];
            double len = Math.hypot(p2[0] - p1[0], p2[1] - p1[1]);
            int steps = Math.max(1, (int) Math.ceil(len / 25));
            for (int s = 0; s < steps; s++) {
                double t = s / (double) steps;
                float[] q = new float[4];
                for (int c = 0; c < 2; c++) q[c] = (float) catmull(p0[c], p1[c], p2[c], p3[c], t);
                q[2] = (float) (p1[2] + (p2[2] - p1[2]) * t);
                q[3] = (float) (p1[3] + (p2[3] - p1[3]) * t);
                pts.add(q);
            }
        }
        pts.add(new float[]{(float) NODES[n - 1][0], (float) NODES[n - 1][1], (float) NODES[n - 1][2], (float) NODES[n - 1][3]});
        rn = pts.size();
        rx = new float[rn]; rz = new float[rn]; re = new float[rn]; rh = new float[rn];
        for (int i = 0; i < rn; i++) { float[] q = pts.get(i); rx[i] = q[0]; rz[i] = q[1]; re[i] = q[2]; rh[i] = q[3]; }
        // Mäander: leichte Schwingung seitlich, nahe der Altstadt schwächer
        float[] ox = new float[rn], oz = new float[rn];
        double sArc = 0;
        for (int i = 0; i < rn; i++) {
            int a = Math.max(0, i - 1), b = Math.min(rn - 1, i + 1);
            double dx = rx[b] - rx[a], dz = rz[b] - rz[a], l = Math.hypot(dx, dz);
            if (i > 0) sArc += Math.hypot(rx[i] - rx[i - 1], rz[i] - rz[i - 1]);
            double amp = 38 * smoothD(900, 3000, Math.hypot(rx[i] + 300, rz[i] + 700)) + 6;
            double w = (Noise.fbm((float) (sArc / 420), 0.5f, 31.1f, 3) - 0.5) * 2 * amp;
            ox[i] = (float) (-dz / l * w); oz[i] = (float) (dx / l * w);
        }
        for (int i = 0; i < rn; i++) { rx[i] += ox[i]; rz[i] += oz[i]; }

        // Höhenkurven der Bergflanken: durch (0, 0), Schlosshof, Königstuhl, Hochfläche
        double sdC = sdist(FOOT_S, CASTLE_XZ[0], CASTLE_XZ[1], -1), sdK = sdist(FOOT_S, KOENIGSTUHL[0], KOENIGSTUHL[1], -1);
        double hC = 0 - PLAIN, hK = 568 - DATUM - PLAIN;
        sTab = new double[]{0, sdC * 0.5, sdC, (sdC + sdK) / 2, sdK, sdK + 1200, sdK + 4200, sdK + 12000};
        hTabS = new double[]{0, hC * 0.30, hC, hC + (hK - hC) * 0.52, hK, hK + 50, hK - 30, hK - 80};
        mTabS = pchip(sTab, hTabS);
        double sdH = sdist(FOOT_N, HEILIGENBERG[0], HEILIGENBERG[1], 1);
        double hH = 440 - DATUM - PLAIN;
        sTabN = new double[]{0, 160, sdH * 0.55, sdH, sdH + 700, sdH + 3500, sdH + 12000};
        hTabN = new double[]{0, 48, hH * 0.5, hH, hH + 14, hH + 5, hH - 30};
        mTabN = pchip(sTabN, hTabN);
        // das Schloss steht auf einem Absatz am Berg
        pad(0, 0, 95, 0, 150);
        // Gipfel nach den Karten: Königstuhl 567,8 m ü. NN; die Rauheit des Geländes drückt ihn sonst um knapp 20 m
        bump(KOENIGSTUHL[0], KOENIGSTUHL[1], 260, 18);
        bump(HEILIGENBERG[0], HEILIGENBERG[1], 220, 18);   // Heiligenberg 439,9 m ü. NN
    }

    /** Höhe der Rheinebene bei der Altstadt (113 m ü. NN). */
    static final double PLAIN = 113 - DATUM;

    // ------------------------------------------------------------ Hilfen

    /** Abstand zu einem Linienzug, positiv auf der Seite des Berges (massif: Vorzeichen des Kreuzprodukts dort). */
    static double sdist(double[][] poly, double x, double z, int massif) {
        double best = Double.MAX_VALUE, sg = 1;
        for (int i = 0; i + 1 < poly.length; i++) {
            double ax = poly[i][0], az = poly[i][1], dx = poly[i + 1][0] - ax, dz = poly[i + 1][1] - az;
            double l2 = dx * dx + dz * dz, t = ((x - ax) * dx + (z - az) * dz) / l2;
            t = t < 0 ? 0 : (t > 1 ? 1 : t);
            double qx = ax + dx * t - x, qz = az + dz * t - z, d2 = qx * qx + qz * qz;
            if (d2 < best) {
                best = d2;
                double cr = dx * (z - az) - dz * (x - ax);
                sg = (cr >= 0 ? 1 : -1) == massif ? 1 : -1;
            }
        }
        return Math.sqrt(best) * sg;
    }

    /** Steigungen für den monotonen kubischen Hermite-Verlauf (Fritsch–Carlson). */
    static double[] pchip(double[] x, double[] y) {
        int n = x.length;
        double[] d = new double[n - 1], m = new double[n];
        for (int i = 0; i < n - 1; i++) d[i] = (y[i + 1] - y[i]) / (x[i + 1] - x[i]);
        m[0] = d[0]; m[n - 1] = d[n - 2];
        for (int i = 1; i < n - 1; i++) m[i] = d[i - 1] * d[i] <= 0 ? 0 : 2 * d[i - 1] * d[i] / (d[i - 1] + d[i]);
        return m;
    }

    static double hermite(double[] x, double[] y, double[] m, double s) {
        if (s <= x[0]) return y[0] + m[0] * Math.min(0, s - x[0]) * 0;
        int n = x.length;
        if (s >= x[n - 1]) return y[n - 1];
        int i = 0;
        while (s > x[i + 1]) i++;
        double h = x[i + 1] - x[i], t = (s - x[i]) / h, t2 = t * t, t3 = t2 * t;
        return (2 * t3 - 3 * t2 + 1) * y[i] + (t3 - 2 * t2 + t) * h * m[i] + (-2 * t3 + 3 * t2) * y[i + 1] + (t3 - t2) * h * m[i + 1];
    }

    private static double catmull(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    private static double smoothD(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    private static float smooth(float a, float b, float x) {
        float t = (x - a) / (b - a);
        t = t < 0 ? 0 : (t > 1 ? 1 : t);
        return t * t * (3 - 2 * t);
    }

    // ------------------------------------------------------------ Bau

    public void build() {
        fill(castle); fill(fine); fill(mid); fill(far);
        rCastle = new Raster(castle.x0, castle.x1(), castle.z0, castle.z1(), 2);
        rFine = new Raster(fine.x0, fine.x1(), fine.z0, fine.z1(), 4);
        rMid = new Raster(mid.x0, mid.x1(), mid.z0, mid.z1(), 24);
        fillRaster(rCastle);
        fillRaster(rFine);
        fillRaster(rMid);
        edges(rCastle);
        edges(rFine);
    }

    /** Nächster Punkt am Fluss: Abstand, Wasserspiegel, halbe Breite, Fließrichtung x, z; o[5] = Seite (+1 rechts). */
    public void nearest(double x, double z, double[] o) {
        double best = Double.MAX_VALUE, be = 0, bh = 10, bfx = 0, bfz = -1, side = 0;
        for (int i = 0; i + 1 < rn; i++) {
            double ax = rx[i], az = rz[i], dx = rx[i + 1] - ax, dz = rz[i + 1] - az;
            double qa = (x - ax) * (x - ax) + (z - az) * (z - az);
            if (qa > best * 9 && qa > 250000) { i += 6; continue; }
            double l2 = dx * dx + dz * dz;
            double t = ((x - ax) * dx + (z - az) * dz) / l2;
            t = t < 0 ? 0 : (t > 1 ? 1 : t);
            double qx = ax + dx * t - x, qz = az + dz * t - z;
            double d2 = qx * qx + qz * qz;
            if (d2 < best) {
                best = d2;
                be = re[i] + (re[i + 1] - re[i]) * t;
                bh = rh[i] + (rh[i + 1] - rh[i]) * t;
                double l = Math.sqrt(l2);
                bfx = dx / l; bfz = dz / l;
                side = Math.signum(dx * (z - az) - dz * (x - ax));
            }
        }
        o[0] = Math.sqrt(best); o[1] = be; o[2] = bh; o[3] = bfx; o[4] = bfz;
        if (o.length > 5) o[5] = side;
    }

    /** Die Höhenformel an (x, z), ohne Gitter. */
    public double exact(double x, double z) {
        double[] o = new double[6];
        nearest(x, z, o);
        return height(x, z, o);
    }

    private double height(double x, double z, double[] o) {
        double d = o[0], ws = o[1], hf = o[2];
        double plain = PLAIN - 17 * smoothD(-1500, -18000, x);
        double sS = sdist(FOOT_S, x, z, -1), sN = sdist(FOOT_N, x, z, 1);
        double mS = sS > 0 ? hermite(sTab, hTabS, mTabS, sS) : 0;
        double mN = sN > 0 ? hermite(sTabN, hTabN, mTabN, sN) : 0;
        // Rücken und Rinnen auf den Bergen, beim Schloss ruhig
        double dc = Math.hypot(x, z);
        double calm = smoothD(160, 520, dc);
        double rough = (Noise.fbm((float) (x / 520), (float) (z / 520), 4.4f, 4) - 0.5) * 2;
        double ridge = (Noise.fbm((float) (x / 170), (float) (z / 170), 9.9f, 3) - 0.5) * 2;
        double mass = Math.max(smoothD(40, 420, sS), smoothD(40, 420, sN));
        double h = plain + mS + mN + (46 * rough + 9 * ridge) * mass * calm;
        // fernes Gebirge: Pfälzerwald im Westen, Odenwald nach Osten höher
        h += 260 * smoothD(-30000, -46000, x) * (0.5 + 0.8 * Noise.fbm((float) (x / 4000), (float) (z / 4000), 2.2f, 3));
        h += 90 * smoothD(8000, 24000, x) * (Noise.fbm((float) (x / 3500), (float) (z / 3500), 6.1f, 4) - 0.3);
        for (double[] b : bumps) h += b[3] * Math.exp(-((x - b[0]) * (x - b[0]) + (z - b[1]) * (z - b[1])) / (b[2] * b[2]));
        // Flussbett und Ufer: das Wasser steht auf ws, das Ufer steigt auf 55 m zum Land an
        double ramp = smoothD(hf, hf + 55, d);
        h = (ws + 0.5) + (h - (ws + 0.5)) * ramp;
        h -= 2.6 * (1 - smoothD(0.45 * hf, 1.05 * hf, d));
        for (double[] p : pads) {
            double dd = Math.hypot(x - p[0], z - p[1]);
            if (dd >= p[2] + p[4]) continue;
            double lvl = p[3];
            if (lvl != lvl) continue;
            double w = 1 - smoothD(p[2], p[2] + p[4], dd);
            h += (lvl - h) * w;
        }
        for (double[] r : rects) {
            double dx = Math.max(Math.max(r[0] - x, x - r[2]), 0), dz = Math.max(Math.max(r[1] - z, z - r[3]), 0);
            double dd = Math.hypot(dx, dz);
            if (dd >= r[5]) continue;
            double w = 1 - smoothD(0, r[5], dd);
            h += (r[4] - h) * w;
        }
        Shaper sh = shaper;
        if (sh != null) h = sh.shape(x, z, h);
        return h;
    }

    private void fill(Grid g) {
        int w = g.nx + 1;
        IntStream.range(0, g.nz + 1).parallel().forEach(j -> {
            double[] o = new double[6];
            for (int i = 0; i <= g.nx; i++) {
                double x = g.x0 + i * g.cell, z = g.z0 + j * g.cell;
                nearest(x, z, o);
                g.h[j * w + i] = (float) height(x, z, o);
            }
        });
        for (int j = 0; j <= g.nz; j++) {
            for (int i = 0; i <= g.nx; i++) {
                int a = j * w + i;
                double hl = g.h[j * w + Math.max(0, i - 1)], hr = g.h[j * w + Math.min(g.nx, i + 1)];
                double hu = g.h[Math.max(0, j - 1) * w + i], hd = g.h[Math.min(g.nz, j + 1) * w + i];
                double sx = (hr - hl) / ((Math.min(g.nx, i + 1) - Math.max(0, i - 1)) * g.cell);
                double sz = (hd - hu) / ((Math.min(g.nz, j + 1) - Math.max(0, j - 1)) * g.cell);
                double l = Math.sqrt(sx * sx + 1 + sz * sz);
                g.nxs[a] = (float) (-sx / l); g.nys[a] = (float) (1 / l); g.nzs[a] = (float) (-sz / l);
            }
        }
    }

    /** Bebauung 0..1 an (x, z): Ellipsen mit weichem Rand, nur auf dem Land. */
    private static double townAt(double x, double z) {
        double best = 0;
        for (double[] e : TOWN) {
            double a = Math.toRadians(e[4]), ca = Math.cos(a), sa = Math.sin(a);
            double dx = x - e[0], dz = z - e[1];
            double u = (dx * ca + dz * sa) / e[2], v = (-dx * sa + dz * ca) / e[3];
            best = Math.max(best, 1 - smoothD(0.78, 1.0, Math.sqrt(u * u + v * v)));
        }
        return best;
    }

    private void fillRaster(Raster r) {
        IntStream.range(0, r.nz).parallel().forEach(j -> {
            double[] o = new double[6];
            for (int i = 0; i < r.nx; i++) {
                double x = r.x0 + i * r.cell, z = r.z0 + j * r.cell;
                nearest(x, z, o);
                int p = j * r.nx + i;
                double d = o[0], hf = o[2];
                r.dist[p] = (float) (d - hf);
                r.fx[p] = (float) o[3]; r.fz[p] = (float) o[4];
                double sS = sdist(FOOT_S, x, z, -1), sN = sdist(FOOT_N, x, z, 1);
                double hh = height(x, z, o);
                // Stadt: auf dem Land am Talboden, nicht am Berg
                double flatLand = 1 - Math.max(smoothD(20, 90, sS), smoothD(20, 90, sN));
                double tw = townAt(x, z) * flatLand * smoothD(hf + 14, hf + 40, d);
                r.town[p] = (float) tw;
                // Wald: auf den Bergflanken in Flecken (Wiesen und Weinberge dazwischen), nicht beim Schloss
                double nf = Noise.fbm((float) (x / 210), (float) (z / 210), 17.3f, 3);
                double mount = Math.max(smoothD(70, 200, sS), smoothD(70, 200, sN));
                double fo = mount * smoothD(0.16, 0.30, nf + 0.25 * (Noise.fbm((float) (x / 800), (float) (z / 800), 3.1f, 2) - 0.5));
                fo *= smoothD(230, 380, Math.hypot(x, z));
                fo *= 1 - tw;
                r.forest[p] = (float) Math.max(0, Math.min(1, fo));
            }
        });
    }

    private static void edges(Raster r) {
        int nx = r.nx, nz = r.nz;
        float[] e = new float[nx * nz];
        float c = (float) r.cell, cd = c * 1.4142f, big = 1e9f;
        for (int p = 0; p < e.length; p++) e[p] = r.forest[p] > 0.5f ? big : 0;
        for (int j = 0; j < nz; j++) for (int i = 0; i < nx; i++) {
            int p = j * nx + i;
            if (e[p] == 0) continue;
            float v = e[p];
            if (i > 0) v = Math.min(v, e[p - 1] + c);
            if (j > 0) v = Math.min(v, e[p - nx] + c);
            if (i > 0 && j > 0) v = Math.min(v, e[p - nx - 1] + cd);
            if (i < nx - 1 && j > 0) v = Math.min(v, e[p - nx + 1] + cd);
            e[p] = v;
        }
        for (int j = nz - 1; j >= 0; j--) for (int i = nx - 1; i >= 0; i--) {
            int p = j * nx + i;
            if (e[p] == 0) continue;
            float v = e[p];
            if (i < nx - 1) v = Math.min(v, e[p + 1] + c);
            if (j < nz - 1) v = Math.min(v, e[p + nx] + c);
            if (i < nx - 1 && j < nz - 1) v = Math.min(v, e[p + nx + 1] + cd);
            if (i > 0 && j < nz - 1) v = Math.min(v, e[p + nx - 1] + cd);
            e[p] = v;
        }
        r.edge = e;
    }

    // ------------------------------------------------------------ Abfragen zur Laufzeit

    /** Bodenhöhe genau wie im Netz. */
    public float sample(double x, double z) {
        if (castle.contains(x, z)) return castle.at(x, z);
        if (fine.contains(x, z)) return fine.at(x, z);
        if (mid.contains(x, z)) return mid.at(x, z);
        if (far.contains(x, z)) return far.at(x, z);
        return far.h[0];
    }

    /** Himmelssicht 0..1 am Boden (x, z) in Höhe h: je Richtung der höchste Horizont in 30, 90 und 250 m. */
    public float skyView(double x, double z, double h) {
        double sum = 0;
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4 + 0.2, cx = Math.cos(a), cz = Math.sin(a), best = 0;
            for (double r : new double[]{30, 90, 250}) {
                double e = (sample(x + cx * r, z + cz * r) - h) / r;
                if (e > best) best = e;
            }
            sum += best / Math.sqrt(1 + best * best);
        }
        return (float) Math.max(0.35, 1 - sum / 8 * 1.3);
    }

    public float riverLevel(int i) { return re[i]; }
    public int riverPoints() { return rn; }
    public float riverX(int i) { return rx[i]; }
    public float riverZ(int i) { return rz[i]; }
    public float riverHalf(int i) { return rh[i]; }

    private static final float[] NONE = {9999, 0, -1, 0, 1};

    private Raster rasterAt(double x, double z) {
        if (rCastle.contains(x, z)) return rCastle;
        if (rFine.contains(x, z)) return rFine;
        if (rMid.contains(x, z)) return rMid;
        return null;
    }

    /** Bodenkarte: Abstand zum Ufer (negativ im Wasser), Fließrichtung x, z, Bebauung und Wald 0..1, bilinear. */
    public void ground(float x, float z, float[] o) {
        Raster r = rasterAt(x, z);
        if (r == null) { System.arraycopy(NONE, 0, o, 0, 5); return; }
        float u = (float) ((x - r.x0) / r.cell), v = (float) ((z - r.z0) / r.cell);
        int i = Math.min(r.nx - 2, (int) u), j = Math.min(r.nz - 2, (int) v);
        float fu = Math.min(1, u - i), fv = Math.min(1, v - j);
        int p = j * r.nx + i, q = p + r.nx;
        o[0] = bil(r.dist, p, q, fu, fv);
        int nn = fu < 0.5f ? (fv < 0.5f ? p : q) : (fv < 0.5f ? p + 1 : q + 1);
        o[1] = r.fx[nn];
        o[2] = r.fz[nn];
        o[3] = bil(r.town, p, q, fu, fv);
        o[4] = bil(r.forest, p, q, fu, fv);
    }

    /** Abstand zum Waldrand in Metern (nur in den feinen Rastern, sonst −1). */
    public float forestEdge(double x, double z) {
        Raster r = rCastle.contains(x, z) ? rCastle : rFine.contains(x, z) ? rFine : null;
        if (r == null) return -1;
        int i = (int) Math.round((x - r.x0) / r.cell), j = (int) Math.round((z - r.z0) / r.cell);
        i = Math.max(0, Math.min(r.nx - 1, i)); j = Math.max(0, Math.min(r.nz - 1, j));
        return r.edge[j * r.nx + i];
    }

    private static float bil(float[] a, int p, int q, float fu, float fv) {
        float ab = a[p] + (a[p + 1] - a[p]) * fu, cd = a[q] + (a[q + 1] - a[q]) * fu;
        return ab + (cd - ab) * fv;
    }

    /** Nur Abstand zum Ufer (für den Dunst über dem Fluss); 9999 außerhalb der Raster. */
    public float riverDist(float x, float z) {
        Raster r = rasterAt(x, z);
        if (r == null) return 9999;
        float u = (float) ((x - r.x0) / r.cell), v = (float) ((z - r.z0) / r.cell);
        int i = Math.min(r.nx - 2, (int) u), j = Math.min(r.nz - 2, (int) v);
        int p = j * r.nx + i;
        return bil(r.dist, p, p + r.nx, Math.min(1, u - i), Math.min(1, v - j));
    }

    /**
     * Farbe des Bodens (linear) am Punkt mit Normale n; foot ist die Größe eines Pixels in Metern.
     * Frische Wiese, Laubwald als Kronendecke, Stadt aus roten und grauen Dächern und Pflaster, Kies am
     * Ufer, roter Buntsandstein an steilen Hängen.
     */
    public void albedo(float x, float y, float z, float nx, float ny, float nz, float foot, float[] gm, float[] o) {
        ground(x, z, gm);
        float bank = gm[0], tn = gm[3], fo = gm[4];
        float big = Noise.tex(x * 0.011f, z * 0.011f), mid = Noise.tex(x * 0.07f + 3.1f, z * 0.07f + 8.7f);
        float fineVis = 1 - smooth(0.15f, 0.9f, foot);
        float fn = fineVis > 0 ? Noise.tex(x * 0.9f + 17.7f, z * 0.9f + 1.3f) - 0.5f : 0;
        // Wiese: frisches Grün mit trockenen Flecken
        float g = smooth(0.3f, 0.75f, Noise.tex(x * 0.025f + 3.1f, z * 0.025f + 8.7f));
        float r0 = 0.085f + 0.05f * g + 0.03f * (big - 0.5f) + 0.02f * (mid - 0.5f), g0 = 0.15f + 0.02f * g + 0.04f * (big - 0.5f) + 0.02f * (mid - 0.5f),
                b0 = 0.045f + 0.01f * g;
        r0 += fn * 0.04f * fineVis; g0 += fn * 0.05f * fineVis; b0 += fn * 0.02f * fineVis;
        // Fluren: Felder und Wiesen als Flecken von 80 bis 250 m, Weizen gelbgrün, Wiese satt, Brache bräunlich
        float fld = Noise.tex(x * 0.0065f + 21.3f, z * 0.0065f + 4.9f), fld2 = Noise.tex(x * 0.015f + 8.1f, z * 0.015f + 30.2f);
        float wheat = smooth(0.52f, 0.60f, fld), fallow = smooth(0.62f, 0.7f, fld2) * (1 - wheat), lush = smooth(0.40f, 0.30f, fld);
        float flat0 = smooth(0.9f, 0.97f, ny) * smooth(220f, 420f, (float) Math.hypot(x, z));
        r0 += (0.22f - r0) * wheat * 0.55f * flat0; g0 += (0.21f - g0) * wheat * 0.45f * flat0; b0 += (0.075f - b0) * wheat * 0.5f * flat0;
        r0 += (0.15f - r0) * fallow * 0.5f * flat0; g0 += (0.115f - g0) * fallow * 0.5f * flat0; b0 += (0.065f - b0) * fallow * 0.5f * flat0;
        g0 *= 1 + 0.12f * lush; r0 *= 1 - 0.1f * lush;
        // Stadt: Dächer, Höfe und Pflaster von oben
        if (tn > 0.01f) {
            float roof = smooth(0.42f, 0.58f, Noise.tex(x * 0.05f + 7.3f, z * 0.05f));
            float rr = 0.22f - 0.07f * roof, rg = 0.12f + 0.03f * roof, rb = 0.095f + 0.04f * roof;
            float mott = (mid - 0.5f) * 0.08f + (big - 0.5f) * 0.05f;
            rr += mott; rg += mott * 0.8f; rb += mott * 0.7f;
            if (fineVis > 0) {
                float c = Noise.tex(x * 0.7f + 5.1f, z * 0.7f + 9.9f);
                float gap = smooth(0.46f, 0.5f, c) * (1 - smooth(0.5f, 0.54f, c)) * fineVis;
                rr *= 1 - 0.25f * gap; rg *= 1 - 0.25f * gap; rb *= 1 - 0.25f * gap;
            }
            float edge = Math.max(0, Math.min(1, tn * (0.6f + 0.8f * (Noise.tex(x * 0.16f + 2.2f, z * 0.16f + 6.6f) - 0.5f) + 0.4f * tn)));
            r0 += (rr - r0) * edge; g0 += (rg - g0) * edge; b0 += (rb - b0) * edge;
        }
        // Wald: Kronendecke der Buchen und Eichen mit Lichtungen
        if (fo > 0.01f) {
            float bl = Noise.tex(x * 0.05f + 1.7f, z * 0.05f + 4.4f);
            float gap = smooth(0.66f, 0.74f, Noise.tex(x * 0.12f + 5.3f, z * 0.12f + 2.1f)) * 0.5f;
            float oak = smooth(0.55f, 0.7f, Noise.tex(x * 0.013f + 9.9f, z * 0.013f + 4.2f));
            float cr = 0.032f + 0.02f * bl + 0.015f * oak, cg = 0.065f + 0.035f * bl - 0.005f * oak, cb = 0.022f + 0.01f * bl;
            cr += (0.10f - cr) * gap * 0.5f; cg += (0.13f - cg) * gap * 0.5f; cb += (0.05f - cb) * gap * 0.5f;
            r0 += (cr - r0) * fo; g0 += (cg - g0) * fo; b0 += (cb - b0) * fo;
        }
        // Ufer: nasser Kies und dunkle Erde
        float wet = 1 - smooth(0, 6, bank);
        if (wet > 0) {
            float kr = 0.10f + 0.03f * mid, kg = 0.092f + 0.025f * mid, kb = 0.07f + 0.02f * mid;
            r0 += (kr - r0) * wet; g0 += (kg - g0) * wet; b0 += (kb - b0) * wet;
        }
        // Fels: roter Buntsandstein an steilen Hängen
        float rock = 1 - smooth(0.62f, 0.80f, ny);
        // Felsflecken nur dort, wo der Hang offen ist: im Wald verdeckt, sonst in Bändern und Flecken statt als geschlossenes Braun
        rock *= (1 - 0.85f * fo) * smooth(0.22f, 0.55f, Noise.tex(x * 0.03f + 21.7f, z * 0.045f + 5.3f));
        if (rock > 0) {
            float rr = 0.27f + 0.08f * mid, rg = 0.135f + 0.05f * mid, rb = 0.10f + 0.04f * mid;
            r0 += (rr - r0) * rock; g0 += (rg - g0) * rock; b0 += (rb - b0) * rock;
        }
        o[0] = r0; o[1] = g0; o[2] = b0;
        // Schnee auf flachem Boden, am Waldrand fleckig
        float snow = Thermal.snow;
        if (snow > 0.01f) {
            float flat = smooth(0.62f, 0.9f, ny);
            float patch = Noise.tex(x * 0.04f + 13.1f, z * 0.04f + 2.7f);
            float cover = flat * smooth(0.0f, 0.35f, snow + (patch - 0.5f) * 0.5f);
            cover *= 1 - 0.55f * fo;
            cover *= 1 - 0.5f * tn;
            cover *= smooth(1.5f, 4, bank);
            if (cover > 0.001f) {
                float drift = 0.93f + 0.07f * mid + (fineVis > 0 ? fn * 0.04f : 0);
                float sr = 0.80f * drift, sg = 0.82f * drift, sb = 0.86f * drift;
                o[0] += (sr - o[0]) * cover; o[1] += (sg - o[1]) * cover; o[2] += (sb - o[2]) * cover;
            }
        }
        Shaper sh = shaper;
        if (sh != null) sh.paint(x, z, foot, o);
        o[0] = Math.max(0.005f, o[0]); o[1] = Math.max(0.005f, o[1]); o[2] = Math.max(0.005f, o[2]);
    }
}
