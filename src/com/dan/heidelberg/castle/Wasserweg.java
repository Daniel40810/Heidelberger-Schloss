package com.dan.heidelberg.castle;

import com.dan.heidelberg.core.Mat;
import com.dan.heidelberg.core.MeshBuilder;
import com.dan.heidelberg.core.Sprites;
import com.dan.heidelberg.core.Terrain;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 9, Wasserweg: der ganze Weg des Wassers durch den Hortus, vom Hang bis in den Neckar. Eine Quelle (Brunnenstube) am
 * Hang oberhalb der Oberen Terrasse speist eine offene Hangrinne, die Rinne quert die Obere Terrasse und stürzt über die
 * Stützmauer in einen Trog auf der Hauptterrasse. Von dort verteilen Leitungen (unter der Erde, de Caus nennt Bleirohre) das Wasser
 * an Säulenbrunnen, Achteckiges Becken, Kaskade der Großen Grotte und Rhenusbecken. Der Überlauf des Rhenusbeckens läuft als
 * Rinne über die Koniferenterrasse und fällt über die Westmauer ins Tal; ein Bach nimmt es dort auf und führt es auf dem
 * steilsten Weg zum Neckar. Das war seit Phase 5 offen: der Abfluss fehlte.
 * <p>
 * Auf Wunsch zeigt das Programm den Weg als wandernde Lichtperlen (sichtbare Strecken hell, Leitungen unter dem Boden
 * schwach), und eine Kamerafahrt folgt dem Wasser. Die Lage der Quelle und die Leitungen sind Annahmen des Modells; überliefert ist
 * nur, dass der Garten Wasserkünste hatte und das Wasser vom Hang kam.
 */
public final class Wasserweg {
    private Wasserweg() { }

    /** Perlen an oder aus (Bedienfeld). */
    public static volatile boolean show;

    /** Abschnitte mit Name, Sinn und Geschwindigkeit der Perlen (m/s). */
    public static final String[] SECTION = {"Quelle am Hang", "Hangrinne", "Obere Terrasse", "Mauerfall", "Leitung zum Säulenbrunnen", "Leitung zum Achteckigen Becken",
            "Kaskade der Großen Grotte", "Rhenusbecken", "Abflussrinne", "Fall über die Westmauer", "Abflussbach"};
    public static final String[] INFO = {
            "Brunnenstube: hier tritt die Quelle aus dem Hang (Annahme des Modells).",
            "Offene Rinne den Hang hinab zur Oberen Terrasse.",
            "Die Rinne quert die Obere Terrasse bis zur Stützmauer.",
            "Das Wasser stürzt über die Mauer in den Trog der Hauptterrasse.",
            "Unterirdische Leitung (Bleirohr) vom Trog zum Säulenbrunnen.",
            "Leitung unter der Hauptterrasse zum Achteckigen Becken und zur Großen Grotte.",
            "Drei Stufen in der Großen Grotte; das Wasser läuft zur Tür.",
            "Das Becken mit dem Rhenus vor der Grotte; sein Überlauf speist den Abfluss.",
            "Rinne über die Koniferenterrasse nach Westen.",
            "Der Überlauf fällt über die Westmauer ins Tal.",
            "Der Bach folgt dem steilsten Weg zum Neckar."};
    static final double[] SPEED = {1.0, 2.4, 1.1, 3.5, 1.4, 1.4, 1.2, 0.5, 0.9, 4.0, 1.6};
    /** Abschnitte, die unter der Erde laufen. */
    static final boolean[] HIDDEN = {false, false, false, false, true, true, false, false, false, false, false};

    /** Knoten des Weges: x, y, z, Abschnitt. */
    public static final List<double[]> PATH = new ArrayList<>();
    /** Kreise (x, z, r), die Häuser freihalten. */
    public static final List<double[]> KEEP = new ArrayList<>();
    private static final List<double[]> CUM = new ArrayList<>();
    private static double total;
    private static double[] secStart, secLen;

    /** Lage der Quelle und der Westmauer. */
    public static final double QX = -94, QZ = 172, FALL_X = -132, FALL_Z = 79;

    // ------------------------------------------------------------ Aufbau

    public static void build(MeshBuilder mb, Terrain t) {
        PATH.clear(); KEEP.clear();
        double keepEdge = mb.maxEdge;
        mb.maxEdge = 1e9;
        mb.skyFn = null;
        final double ML = Hortus.ML, KL = Hortus.KL, UL = Hortus.UL;
        // 0 Quelle: Brunnenstube mit Rundbogen und Löwenmaske
        double qy = t.stand(QX, QZ, 1e9);
        quelle(mb, qy);
        add(0, QX, qy + 0.2, QZ - 1.6);
        // 1 Hangrinne: vom Hang zur Oberen Terrasse (Gelände folgend)
        List<double[]> p = new ArrayList<>();
        for (double z = QZ - 2.4; z > 129.6; z -= 4) p.add(new double[]{QX, z});
        p.add(new double[]{QX, 129.6});
        strip(mb, t, p, 1.5, 1.5, 0.14, true, 0, true);
        for (double[] q : p) add(1, q[0], t.stand(q[0], q[1], 1e9) + 0.16, q[1]);
        // 2 Obere Terrasse, Gefälle sacht zur Mauer
        add(2, QX, UL + 0.1, 129.0);
        add(2, QX, UL + 0.1, 125.0);
        add(2, QX, UL + 0.1, 121.0);
        flat(mb, QX, 129.6, QX, 120.8, 1.5, UL + 0.05, true);
        // 3 Mauerfall (Strahlen in Fountains) und Trog auf der Hauptterrasse
        trog(mb, QX, 117.7);
        add(3, QX, UL, 120.2);
        add(3, QX, ML + 1.2, 119.3);
        add(3, QX, ML + 0.7, 118.0);
        // 4 Leitung zum Säulenbrunnen (unter der Terrasse), 5 Leitung zum Achteckbecken und zur Grotte
        double py = ML - 0.9;
        add(4, QX - 4, py, 116.0); add(4, -106, py, 113.0); add(4, -118, py, 111.0);
        add(5, -112, py, 108.5); add(5, -98, py, 106.0); add(5, -84, py, 103.5); add(5, -74, py, 102.5);
        add(5, -70, py, 101.0); add(5, -70, KL + 2.2, 99.8);
        // 6 Kaskade der Großen Grotte: drei Stufen nach Norden zur Tür
        double gz1 = Hortus.M[1] + 1.4 + 14;
        add(6, Hortus.GROSS_X, KL + 1.7, gz1 - 0.6);
        add(6, Hortus.GROSS_X, KL + 1.2, gz1 - 2.8);
        add(6, Hortus.GROSS_X, KL + 0.7, gz1 - 4.2);
        add(6, Hortus.GROSS_X, KL + 0.5, Hortus.M[1] + 6.5);
        add(6, Hortus.GROSS_X, KL + 0.45, Hortus.M[1] + 1.0);
        // 7 Rhenusbecken
        double rz = Hortus.M[1] - 5;
        add(7, Hortus.GROSS_X, KL + 0.5, Hortus.M[1] - 3.0);
        add(7, Hortus.GROSS_X - 3, KL + 0.5, rz);
        add(7, Hortus.GROSS_X - 7.2, KL + 0.45, FALL_Z);
        // 8 Abflussrinne nach Westen über die Koniferenterrasse
        List<double[]> r = new ArrayList<>();
        for (double x = Hortus.GROSS_X - 8; x > FALL_X + 0.4; x -= 4) r.add(new double[]{x, FALL_Z});
        r.add(new double[]{FALL_X + 0.4, FALL_Z});
        flatPoly(mb, r, 0.9, KL + 0.06, true);
        for (double[] q : r) add(8, q[0], KL + 0.1, q[1]);
        // 9 Fall über die Westmauer: Mündung als Wasserspeier
        double gy = t.stand(FALL_X - 4, FALL_Z, 1e9);
        spout(mb, FALL_X, KL, FALL_Z);
        add(9, FALL_X - 0.4, KL + 0.1, FALL_Z);
        add(9, FALL_X - 1.6, (KL + gy) / 2 + 2, FALL_Z);
        add(9, FALL_X - 3.8, gy + 0.3, FALL_Z);
        // 10 Bach: steilster Abstieg mit Zug zum Fluss
        bach(mb, t, FALL_X - 4.5, FALL_Z);
        total();
        mb.maxEdge = keepEdge;
    }

    private static void add(int sec, double x, double y, double z) { PATH.add(new double[]{x, y, z, sec}); }

    private static void total() {
        CUM.clear();
        double s = 0;
        secStart = new double[SECTION.length]; secLen = new double[SECTION.length];
        java.util.Arrays.fill(secStart, -1);
        for (int i = 0; i < PATH.size(); i++) {
            double[] a = PATH.get(i);
            if (i > 0) { double[] b = PATH.get(i - 1); s += Math.sqrt(sq(a[0] - b[0]) + sq(a[1] - b[1]) + sq(a[2] - b[2])); }
            CUM.add(new double[]{s});
            int sec = (int) a[3];
            if (secStart[sec] < 0) secStart[sec] = s;
            secLen[sec] = s - secStart[sec];
        }
        total = s;
    }

    static double sq(double x) { return x * x; }

    /** Weglänge, bei der der Abschnitt sec beginnt. */
    public static double sectionStart(int sec) { return secStart[sec]; }

    /** Gesamtlänge des Weges in Metern. */
    public static double length() { return total; }

    /** Punkt bei Weglänge s (0..length): x, y, z, Abschnitt. */
    public static void at(double s, double[] o) {
        s = Math.max(0, Math.min(total, s));
        int lo = 0, hi = PATH.size() - 1;
        while (hi - lo > 1) {
            int m = (lo + hi) >> 1;
            if (CUM.get(m)[0] <= s) lo = m; else hi = m;
        }
        double a = CUM.get(lo)[0], b = CUM.get(hi)[0], u = b > a ? (s - a) / (b - a) : 0;
        double[] p = PATH.get(lo), q = PATH.get(hi);
        for (int i = 0; i < 3; i++) o[i] = p[i] + (q[i] - p[i]) * u;
        o[3] = u < 0.5 ? p[3] : q[3];
    }

    // ------------------------------------------------------------ Geometrie

    /** Wasserband entlang eines Linienzugs (x, z), dem Gelände folgend; Rand aus Sandstein. */
    static void strip(MeshBuilder mb, Terrain t, List<double[]> pts, double w0, double w1, double lift, boolean rim, int unused, boolean channel) {
        List<double[]> s = resample(pts, 3.0);
        int n = s.size();
        double len = 0;
        for (int i = 1; i < n; i++) len += Math.hypot(s.get(i)[0] - s.get(i - 1)[0], s.get(i)[1] - s.get(i - 1)[1]);
        double run = 0;
        double[] prevL = null, prevR = null, prevLo = null, prevRo = null;
        for (int i = 0; i < n; i++) {
            double[] c = s.get(i);
            double[] a = s.get(Math.max(0, i - 1)), b = s.get(Math.min(n - 1, i + 1));
            double dx = b[0] - a[0], dz = b[1] - a[1], l = Math.hypot(dx, dz);
            if (l < 1e-6) { dx = 0; dz = -1; l = 1; }
            double nx = -dz / l, nz = dx / l;
            if (i > 0) run += Math.hypot(c[0] - s.get(i - 1)[0], c[1] - s.get(i - 1)[1]);
            double w = (w0 + (w1 - w0) * (len > 0 ? run / len : 0)) / 2;
            double[] L = {c[0] + nx * w, 0, c[1] + nz * w}, R = {c[0] - nx * w, 0, c[1] - nz * w};
            L[1] = t.stand(L[0], L[2], 1e9) + lift; R[1] = t.stand(R[0], R[2], 1e9) + lift;
            double m = Math.min(L[1], R[1]);
            if (channel) { L[1] = R[1] = (L[1] + R[1]) / 2; }
            double ro = w + 0.32;
            double[] Lo = {c[0] + nx * ro, t.stand(c[0] + nx * ro, c[1] + nz * ro, 1e9) + lift + 0.2, c[1] + nz * ro};
            double[] Ro = {c[0] - nx * ro, t.stand(c[0] - nx * ro, c[1] - nz * ro, 1e9) + lift + 0.2, c[1] - nz * ro};
            if (channel) { Lo[1] = Ro[1] = Math.max(L[1] + 0.2, (Lo[1] + Ro[1]) / 2); }
            if (prevL != null) {
                mb.quad(prevL, prevR, R, L, 0, 1, 0, Mat.BASIN);
                if (rim) {
                    mb.quad(prevLo, prevL, L, Lo, 0, 1, 0, Mat.SANDSTONE);
                    mb.quad(prevR, prevRo, Ro, R, 0, 1, 0, Mat.SANDSTONE);
                    mb.quad(new double[]{prevL[0], prevL[1] + 0.02, prevL[2]}, new double[]{prevLo[0], prevLo[1], prevLo[2]}, Lo, new double[]{L[0], L[1] + 0.02, L[2]}, 0, 1, 0, Mat.SANDSTONE);
                }
            }
            prevL = L; prevR = R; prevLo = Lo; prevRo = Ro;
        }
    }

    static List<double[]> resample(List<double[]> pts, double step) {
        List<double[]> o = new ArrayList<>();
        o.add(pts.get(0));
        for (int i = 1; i < pts.size(); i++) {
            double[] a = pts.get(i - 1), b = pts.get(i);
            double d = Math.hypot(b[0] - a[0], b[1] - a[1]);
            int k = Math.max(1, (int) Math.ceil(d / step));
            for (int j = 1; j <= k; j++) o.add(new double[]{a[0] + (b[0] - a[0]) * j / k, a[1] + (b[1] - a[1]) * j / k});
        }
        return o;
    }

    /** Ebene Rinne mit Rand (auf Terrassen): Wasser und zwei Randsteine. */
    static void flat(MeshBuilder mb, double x0, double z0, double x1, double z1, double w, double y, boolean rim) {
        List<double[]> p = new ArrayList<>();
        p.add(new double[]{x0, z0}); p.add(new double[]{x1, z1});
        flatPoly(mb, p, w, y, rim);
    }

    static void flatPoly(MeshBuilder mb, List<double[]> pts, double w, double y, boolean rim) {
        for (int i = 0; i + 1 < pts.size(); i++) {
            double ax = pts.get(i)[0], az = pts.get(i)[1], bx = pts.get(i + 1)[0], bz = pts.get(i + 1)[1];
            double dx = bx - ax, dz = bz - az, l = Math.hypot(dx, dz);
            if (l < 1e-6) continue;
            double nx = -dz / l * w / 2, nz = dx / l * w / 2;
            mb.quad(new double[]{ax + nx, y, az + nz}, new double[]{bx + nx, y, bz + nz}, new double[]{bx - nx, y, bz - nz}, new double[]{ax - nx, y, az - nz}, 0, 1, 0, Mat.BASIN);
            if (rim) {
                double mx = nx * 1.0 + (nx / (w / 2)) * 0.3, mz = nz * 1.0 + (nz / (w / 2)) * 0.3;
                // Randsteine als flache Kästen: links und rechts
                for (int sgn = -1; sgn <= 1; sgn += 2) {
                    double ox = sgn * (nx + nx / (w / 2) * 0.16), oz = sgn * (nz + nz / (w / 2) * 0.16);
                    double x0 = Math.min(ax + ox, bx + ox) - 0.16, x1 = Math.max(ax + ox, bx + ox) + 0.16;
                    double z0 = Math.min(az + oz, bz + oz) - 0.16, z1 = Math.max(az + oz, bz + oz) + 0.16;
                    if (Math.abs(dx) > Math.abs(dz)) { z0 = az + oz - 0.16; z1 = az + oz + 0.16; x0 = Math.min(ax, bx); x1 = Math.max(ax, bx); }
                    else { x0 = ax + ox - 0.16; x1 = ax + ox + 0.16; z0 = Math.min(az, bz); z1 = Math.max(az, bz); }
                    mb.box(x0, y - 0.04, z0, x1, y + 0.2, z1, Mat.SANDSTONE, true);
                }
            }
        }
    }

    /** Brunnenstube am Hang: Sockel, Bogenöffnung, Löwenmaske und ein kleines Becken. */
    static void quelle(MeshBuilder mb, double y) {
        double x = QX, z = QZ;
        mb.box(x - 1.9, y - 0.6, z - 0.9, x + 1.9, y + 2.3, z + 1.6, Mat.SANDSTONE, true);
        mb.box(x - 2.1, y + 2.3, z - 1.1, x + 2.1, y + 2.6, z + 1.8, Mat.STATUE, true);
        mb.box(x - 0.7, y + 0.3, z - 1.0, x + 0.7, y + 1.5, z - 0.88, Mat.ROCK, true);
        mb.ellipsoid(x, y + 1.9, z - 1.0, 0.38, 0.38, 0.14, 10, 6, Mat.STATUE);
        mb.box(x - 1.1, y - 0.2, z - 2.4, x + 1.1, y + 0.18, z - 0.9, Mat.SANDSTONE, true);
        mb.rectH(x - 0.9, z - 2.2, x + 0.9, z - 0.95, y + 0.14, true, Mat.BASIN);
        Castle.place("Brunnenstube (Quelle des Wasserwegs)", x, y + 1, z, "M");
    }

    /** Trog am Fuß der Stützmauer, Wasser fällt vom Mauerfall hinein. */
    static void trog(MeshBuilder mb, double x, double z) {
        final double ML = Hortus.ML;
        mb.box(x - 2.4, ML, z - 2.6, x + 2.4, ML + 0.7, z - 2.2, Mat.SANDSTONE, true);
        mb.box(x - 2.4, ML, z + 2.2, x + 2.4, ML + 0.7, z + 2.6, Mat.SANDSTONE, true);
        mb.box(x - 2.4, ML, z - 2.2, x - 2.0, ML + 0.7, z + 2.2, Mat.SANDSTONE, true);
        mb.box(x + 2.0, ML, z - 2.2, x + 2.4, ML + 0.7, z + 2.2, Mat.SANDSTONE, true);
        mb.rectH(x - 2.0, z - 2.2, x + 2.0, z + 2.2, ML + 0.55, true, Mat.BASIN);
        // Wasserspeier oben an der Mauer: Löwenkopf, Schlund zum Trog
        mb.box(x - 0.5, Hortus.UL - 0.9, 119.2, x + 0.5, Hortus.UL - 0.1, 120.0, Mat.STATUE, true);
        Castle.place("Mauerfall und Trog (Wasserweg)", x, ML + 0.6, z, "M");
    }

    /** Wasserspeier in der Westmauer der Koniferenterrasse. */
    static void spout(MeshBuilder mb, double x, double ky, double z) {
        mb.box(x - 0.9, ky - 1.0, z - 0.8, x + 0.1, ky + 0.1, z + 0.8, Mat.STATUE, true);
        mb.ellipsoid(x - 0.5, ky - 0.4, z, 0.42, 0.42, 0.5, 10, 6, Mat.STATUE);
        Castle.place("Abfluss: Wasserspeier an der Westmauer", x, ky, z, "M");
    }

    // ------------------------------------------------------------ Bach

    static void bach(MeshBuilder mb, Terrain t, double x0, double z0) {
        double x = x0, z = z0, step = 6;
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[]{x, z});
        double dxp = -1, dzp = 0;
        for (int i = 0; i < 500; i++) {
            double rd = t.riverDist((float) x, (float) z);
            if (rd < 7) break;
            double gx = (t.stand(x + 3, z, 1e9) - t.stand(x - 3, z, 1e9)) / 6, gz = (t.stand(x, z + 3, 1e9) - t.stand(x, z - 3, 1e9)) / 6;
            double g = Math.hypot(gx, gz) + 1e-9;
            double best = 1e18; int bi = 0;
            for (int k = 0; k < t.riverPoints(); k += 2) {
                double d = Math.hypot(t.riverX(k) - x, t.riverZ(k) - z);
                if (d < best) { best = d; bi = k; }
            }
            double bx = t.riverX(bi) - x, bz = t.riverZ(bi) - z, bl = Math.hypot(bx, bz);
            bx /= bl; bz /= bl;
            double wg = Math.min(1, g / 0.10) * 0.7, wr = 1 - wg;
            double dx = -gx / g * wg + bx * wr, dz = -gz / g * wg + bz * wr;
            dx = dx * 0.55 + dxp * 0.45; dz = dz * 0.55 + dzp * 0.45;
            double dl = Math.hypot(dx, dz);
            dx /= dl; dz /= dl;
            dxp = dx; dzp = dz;
            x += dx * step; z += dz * step;
            pts.add(new double[]{x, z});
        }
        // Flussufer: letzter Knoten ganz an die Uferlinie ziehen
        strip(mb, t, pts, 1.3, 3.4, 0.22, false, 0, false);
        for (int i = 0; i < pts.size(); i++) {
            double[] q = pts.get(i);
            add(10, q[0], t.stand(q[0], q[1], 1e9) + 0.25, q[1]);
            if (i % 5 == 0) KEEP.add(new double[]{q[0], q[1], 11});
        }
        double[] qa = pts.get(0), qe = pts.get(pts.size() - 1);
        Castle.place("Abflussbach (Wasserweg)", qa[0], t.stand(qa[0], qa[1], 1e9), qa[1], "M");
        System.out.printf("Wasserweg: Bach mit %d Knoten, endet bei %.0f/%.0f (Fluss %.0f m)%n", pts.size(), qe[0], qe[1], t.riverDist((float) qe[0], (float) qe[1]));
    }

    // ------------------------------------------------------------ Perlen

    /** Namen und Beschreibung des Abschnitts, in dem die Kamera steht (null, wenn weiter als 45 m vom Weg). */
    public static String station(double cx, double cy, double cz) {
        double best = 45 * 45;
        int sec = -1;
        for (int i = 0; i < PATH.size(); i += 1) {
            double[] p = PATH.get(i);
            double d = sq(p[0] - cx) + sq(p[1] - cy) + sq(p[2] - cz);
            if (d < best) { best = d; sec = (int) p[3]; }
        }
        return sec < 0 ? null : SECTION[sec] + ": " + INFO[sec];
    }

    /** Wandernde Perlen entlang des Weges, nur nahe der Kamera. */
    public static void beads(Sprites sp, double time, double cx, double cy, double cz) {
        if (!show || secStart == null) return;
        double[] o = new double[4];
        int cnt = 0;
        for (int sec = 0; sec < SECTION.length && cnt < 900; sec++) {
            if (secStart[sec] < 0 || secLen[sec] < 0.5) continue;
            boolean hid = HIDDEN[sec];
            double spacing = sec == 10 ? 7 : sec == 1 ? 3.2 : 2.4, v = SPEED[sec];
            double L = secLen[sec];
            int n = (int) Math.ceil(L / spacing);
            double ph = (time * v) % spacing;
            for (int k = 0; k <= n; k++) {
                double s = k * spacing + ph;
                if (s > L) s -= spacing * (n + 1);
                if (s < 0 || s > L) continue;
                at(secStart[sec] + s, o);
                double dx = o[0] - cx, dy = o[1] - cy, dz = o[2] - cz;
                double d2 = dx * dx + dy * dy + dz * dz;
                if (d2 > 260 * 260) continue;
                double fl = 0.8 + 0.2 * Math.sin(time * 5 + k * 1.7 + sec);
                double size = (hid ? 0.22 : sec == 10 ? 0.55 : 0.32) * (1 + Math.sqrt(d2) / 90);
                if (hid) sp.add(Sprites.GLOW, o[0], o[1], o[2], size, 0.5 * fl, 0.4 * fl, 1.4 * fl, 0.5);
                else sp.add(Sprites.GLOW, o[0], o[1] + 0.25, o[2], size, 0.2 * fl, 1.5 * fl, 2.6 * fl, 0.9);
                cnt++;
            }
        }
    }
}
