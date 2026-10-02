package com.dan.heidelberg.castle;

import com.dan.heidelberg.castle.Arch.Block;
import com.dan.heidelberg.core.Mat;
import com.dan.heidelberg.core.MeshBuilder;

import java.util.List;
import java.util.TreeSet;
import java.util.function.DoubleUnaryOperator;

/**
 * Das Schloss als Ruine (Phase 9, Zeitschalter). Gebaut wird dreimal mit denselben Bauplänen wie 1619, aber ohne Dächer,
 * Decken, Einbauten und Glas, mit gebrochener Mauerkrone: Stufe 1 nach dem Brand von 1689, Stufe 2 nach der Sprengung
 * von 1693, Stufe 3 nach dem Blitz von 1764. Je Stufe ist jede Mauerkrone tiefer als in der Stufe davor (dieselben
 * Rauschfelder, nur größere Verluste), nur der Notdach-Zustand des Ottheinrichsbaus in Stufe 2 ist Annahme der
 * Rekonstruktion. Lage und Maße sind die von 1619 (siehe {@link Castle}); die Verluste sind <b>Annahmen</b>, nicht
 * Aufmaß: Die Quellen belegen, welche Bauten wann fielen, nicht wie hoch jedes Mauerstück stehen blieb.
 */
public final class Ruin {
    private Ruin() { }

    /** Während des Baus einer Ruinenstufe wahr; die Bauten fragen es ab und lassen Dach, Glas und Einbauten weg. */
    public static boolean active;
    /** Stufe 1 bis 3 (nach 1689, 1693, 1764). */
    public static int stage;

    public static final String[] STAGE_NAMES = {"1619", "nach dem Brand 1689", "nach der Sprengung 1693", "nach dem Blitz 1764"};
    static final int[] ERA = {MeshBuilder.ERA_1619, MeshBuilder.ERA_1689, MeshBuilder.ERA_1693, MeshBuilder.ERA_1764};

    // ------------------------------------------------------------ Rauschen

    static double h1(int seed, int i) {
        int h = seed * 374761393 + i * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / 16777216.0;
    }

    /** Glattes eindimensionales Rauschen 0..1 mit Gitterabstand g Metern. */
    static double n1(int seed, double x, double g) {
        double t = x / g;
        int i = (int) Math.floor(t);
        double f = t - i;
        f = f * f * (3 - 2 * f);
        return h1(seed, i) * (1 - f) + h1(seed, i + 1) * f;
    }

    static double sm(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    // ------------------------------------------------------------ Verluste

    /** Verlust der Mauerkrone je Stufe (1..3) und Wandseite (0 Hof, 1 rechts, 2 hinten, 3 links), Bruchteil der Wandhöhe. */
    private static double[][] drops(String key) {
        switch (key) {
            case "ott": return new double[][]{{0.02, 0.04, 0.05, 0.04}, {0, 0, 0, 0}, {0.05, 0.22, 0.32, 0.22}};
            case "fri": return new double[][]{{0.02, 0.05, 0.06, 0.05}, {0.03, 0.12, 0.25, 0.12}, {0.07, 0.20, 0.38, 0.20}};
            case "gla": return new double[][]{{0.04, 0.08, 0.08, 0.08}, {0.25, 0.30, 0.45, 0.30}, {0.38, 0.50, 0.68, 0.50}};
            case "fas": return new double[][]{{0.03, 0.05, 0.06, 0.05}, {0.10, 0.20, 0.30, 0.20}, {0.20, 0.36, 0.50, 0.36}};
            case "rup": return new double[][]{{0.02, 0.04, 0.05, 0.04}, {0.05, 0.10, 0.15, 0.10}, {0.10, 0.20, 0.28, 0.20}};
            case "frz": return new double[][]{{0.05, 0.08, 0.10, 0.08}, {0.30, 0.40, 0.55, 0.40}, {0.45, 0.60, 0.74, 0.60}};
            case "eng": return new double[][]{{0.05, 0.08, 0.10, 0.08}, {0.35, 0.45, 0.60, 0.45}, {0.50, 0.65, 0.78, 0.65}};
            case "bib": return new double[][]{{0.05, 0.08, 0.10, 0.08}, {0.40, 0.48, 0.60, 0.48}, {0.55, 0.68, 0.80, 0.68}};
            case "lud": return new double[][]{{0.05, 0.08, 0.10, 0.08}, {0.45, 0.52, 0.64, 0.52}, {0.60, 0.72, 0.84, 0.72}};
            case "tor": return new double[][]{{0.02, 0.03, 0.03, 0.03}, {0.03, 0.05, 0.05, 0.05}, {0.05, 0.10, 0.10, 0.10}};
            default: return new double[][]{{0.05, 0.08, 0.1, 0.08}, {0.3, 0.4, 0.5, 0.4}, {0.5, 0.6, 0.7, 0.6}};
        }
    }

    /** Wie wahrscheinlich ein Mauerdurchbruch in Wand k ist. */
    private static double breachChance(String key, int k) {
        switch (key) {
            case "ott": case "fri": return k == 0 ? 0 : 0.45;
            case "gla": return k == 0 ? 0.8 : 0.9;
            case "frz": case "eng": case "bib": case "lud": return 0.9;
            case "fas": return 0.6;
            case "rup": return 0.3;
            default: return 0.2;
        }
    }

    /** Der Ottheinrichsbau trägt in Stufe 2 ein schlichtes Notdach (Annahme), das der Blitz 1764 verbrennt. */
    static boolean keepRoof(Block b) { return stage == 2 && keyOf(b).equals("ott"); }

    static String keyOf(Block b) {
        if (near(b, -17, -29)) return "ott";
        if (near(b, 31, -31)) return "fri";
        if (near(b, 8, -27)) return "gla";
        if (near(b, 43, 19)) return "fas";
        if (near(b, 41, -9)) return "rup";
        if (near(b, 13.3, 30.35)) return "frz";
        if (near(b, -23, 31)) return "eng";
        if (near(b, -39, -13)) return "bib";
        if (near(b, -39, 14)) return "lud";
        if (near(b, -52, 2)) return "tor";
        return "?";
    }

    private static boolean near(Block b, double x, double z) { return Math.abs(b.cx - x) < 0.5 && Math.abs(b.cz - z) < 0.5; }

    /** Höhe der Mauerkrone der Wandseite k an der Stelle u (absolute Höhe). */
    static double top(Block b, String key, int k, double u) {
        double H = b.y1 - b.y0;
        double D = drops(key)[stage - 1][k];
        int seed = key.hashCode() * 31 + k * 7 + 3;
        double hi = b.y1;
        if (D > 0) {
            double nl = n1(seed, u, 5.5), nh = n1(seed + 101, u, 1.35);
            hi = b.y1 - H * (D * (0.30 + 0.70 * nl) + (0.03 + 0.10 * D) * nh);
        }
        // Durchbrüche: kommen in Stufe 2 oder 3 und werden mit der Zeit tiefer
        double L = b.len(k);
        double chance = breachChance(key, k);
        int sb = key.hashCode() * 17 + k * 131 + 9;
        int cnt = h1(sb, 1) < chance ? 1 + (h1(sb, 2) < chance * 0.6 ? 1 : 0) : 0;
        for (int i = 0; i < cnt; i++) {
            int appear = h1(sb, 10 + i) < 0.5 ? 2 : 3;
            if (stage < appear) continue;
            double depth = stage == appear ? 0.6 : 1.0;
            double c = L * (0.15 + 0.70 * h1(sb, 20 + i)), half = 2.2 + 4.5 * h1(sb, 30 + i), f = 0.08 + 0.35 * h1(sb, 40 + i);
            double d = Math.abs(u - c) / half;
            double bump = 1 - sm(0.45, 1.0, d);
            hi = Math.min(hi, b.y1 - H * (1 - f) * depth * bump);
        }
        return Math.max(b.y0 + 0.4, hi);
    }

    /** Breiten und Mitten der Durchbrüche der Wand k (für Schuttkegel davor): {Mitte, halbe Breite, Tiefe 0..1}. */
    static double[][] breaches(String key, int k, double L) {
        double chance = breachChance(key, k);
        int sb = key.hashCode() * 17 + k * 131 + 9;
        int cnt = h1(sb, 1) < chance ? 1 + (h1(sb, 2) < chance * 0.6 ? 1 : 0) : 0;
        double[][] r = new double[cnt][];
        for (int i = 0; i < cnt; i++) {
            int appear = h1(sb, 10 + i) < 0.5 ? 2 : 3;
            double depth = stage < appear ? 0 : (stage == appear ? 0.6 : 1.0);
            r[i] = new double[]{L * (0.15 + 0.70 * h1(sb, 20 + i)), 2.2 + 4.5 * h1(sb, 30 + i), depth};
        }
        return r;
    }

    // ------------------------------------------------------------ Mauern mit gebrochener Krone

    static int wallMat() { return stage == 3 ? Mat.SANDSTONE : Mat.SOOT; }

    /**
     * Wand der Länge L im Wandrahmen (Außenfläche z = 0, Innenfläche z = −T, u nach rechts, y nach oben) mit gebrochener Krone
     * top(u). Öffnungen, die die Krone anschneidet, werden zu Scharten bis zur Brüstung; unversehrte bekommen Laibungen
     * und Gewände, aber kein Glas.
     */
    static void wall(MeshBuilder mb, double L, double yb, double T, List<Arch.Op> ops, DoubleUnaryOperator top, int frame) {
        double keep = mb.maxEdge;
        mb.maxEdge = 1e9;
        final int out = wallMat(), in = Mat.SOOT, cap = stage == 1 ? Mat.SOOT : Mat.RUBBLE;
        TreeSet<Double> us = new TreeSet<>();
        us.add(0.0); us.add(L);
        int n = Math.max(1, (int) Math.ceil(L / 1.15));
        for (int i = 1; i < n; i++) us.add(L * i / n);
        for (Arch.Op o : ops) {
            if (o.u0 > 0 && o.u0 < L) us.add(o.u0);
            if (o.u1 > 0 && o.u1 < L) us.add(o.u1);
        }
        java.util.ArrayList<Double> ual = new java.util.ArrayList<>();
        for (double u : us) if (ual.isEmpty() || u - ual.get(ual.size() - 1) > 0.04) ual.add(u);
        if (ual.get(ual.size() - 1) < L - 0.04) ual.add(L); else ual.set(ual.size() - 1, L);
        int m = ual.size() - 1;
        double[] h = new double[m];
        for (int i = 0; i < m; i++) h[i] = top.applyAsDouble((ual.get(i) + ual.get(i + 1)) / 2);
        boolean[] intact = new boolean[ops.size()];
        for (int oi = 0; oi < ops.size(); oi++) {
            Arch.Op o = ops.get(oi);
            boolean ok = true;
            for (int i = 0; i < m; i++) {
                double cu = (ual.get(i) + ual.get(i + 1)) / 2;
                if (cu > o.u0 && cu < o.u1 && h[i] < o.y1 + 0.05) ok = false;
            }
            intact[oi] = ok;
            if (!ok) {
                for (int i = 0; i < m; i++) {
                    double cu = (ual.get(i) + ual.get(i + 1)) / 2;
                    if (cu > o.u0 && cu < o.u1 && h[i] > o.y0) h[i] = o.y0;
                }
            }
        }
        for (int i = 0; i < m; i++) {
            double u0 = ual.get(i), u1 = ual.get(i + 1), hh = h[i], cu = (u0 + u1) / 2;
            TreeSet<Double> ys = new TreeSet<>();
            ys.add(yb); ys.add(hh);
            for (int oi = 0; oi < ops.size(); oi++) {
                Arch.Op o = ops.get(oi);
                if (!intact[oi] || cu <= o.u0 || cu >= o.u1) continue;
                if (o.y0 > yb && o.y0 < hh) ys.add(o.y0);
                if (o.y1 > yb && o.y1 < hh) ys.add(o.y1);
            }
            Double[] ya = ys.toArray(new Double[0]);
            for (int j = 0; j + 1 < ya.length; j++) {
                double cy = (ya[j] + ya[j + 1]) / 2;
                boolean inOp = false;
                for (int oi = 0; oi < ops.size() && !inOp; oi++) {
                    Arch.Op o = ops.get(oi);
                    if (intact[oi] && cu > o.u0 && cu < o.u1 && cy > o.y0 && cy < o.y1) inOp = true;
                }
                if (inOp) continue;
                mb.quad(Arch.P(u0, ya[j], 0), Arch.P(u1, ya[j], 0), Arch.P(u1, ya[j + 1], 0), Arch.P(u0, ya[j + 1], 0), 0, 0, 1, out);
                mb.quad(Arch.P(u0, ya[j], -T), Arch.P(u1, ya[j], -T), Arch.P(u1, ya[j + 1], -T), Arch.P(u0, ya[j + 1], -T), 0, 0, -1, in);
            }
            mb.quad(Arch.P(u0, hh, 0), Arch.P(u1, hh, 0), Arch.P(u1, hh, -T), Arch.P(u0, hh, -T), 0, 1, 0, cap);
        }
        // Stufen zwischen den Spalten und die Stirnseiten
        for (int i = 0; i <= m; i++) {
            double u = ual.get(i);
            if (i == 0) mb.quad(Arch.P(u, yb, 0), Arch.P(u, yb, -T), Arch.P(u, h[0], -T), Arch.P(u, h[0], 0), -1, 0, 0, in);
            else if (i == m) mb.quad(Arch.P(u, yb, 0), Arch.P(u, yb, -T), Arch.P(u, h[m - 1], -T), Arch.P(u, h[m - 1], 0), 1, 0, 0, in);
            else if (h[i - 1] > h[i] + 0.01) mb.quad(Arch.P(u, h[i], 0), Arch.P(u, h[i], -T), Arch.P(u, h[i - 1], -T), Arch.P(u, h[i - 1], 0), 1, 0, 0, in);
            else if (h[i] > h[i - 1] + 0.01) mb.quad(Arch.P(u, h[i - 1], 0), Arch.P(u, h[i - 1], -T), Arch.P(u, h[i], -T), Arch.P(u, h[i], 0), -1, 0, 0, in);
        }
        // Laibungen und Gewände der unversehrten Öffnungen
        for (int oi = 0; oi < ops.size(); oi++) {
            if (!intact[oi]) continue;
            Arch.Op o = ops.get(oi);
            double sp = o.spring(), depth = T;
            boolean ground = o.kind >= 2;
            mb.quad(Arch.P(o.u0, o.y0, 0), Arch.P(o.u0, o.y0, -depth), Arch.P(o.u0, sp, -depth), Arch.P(o.u0, sp, 0), 1, 0, 0, in);
            mb.quad(Arch.P(o.u1, o.y0, 0), Arch.P(o.u1, o.y0, -depth), Arch.P(o.u1, sp, -depth), Arch.P(o.u1, sp, 0), -1, 0, 0, in);
            if (!ground || o.y0 > yb + 0.01) mb.quad(Arch.P(o.u0, o.y0, 0), Arch.P(o.u1, o.y0, 0), Arch.P(o.u1, o.y0, -depth), Arch.P(o.u0, o.y0, -depth), 0, 1, 0, in);
            if (o.kind == 3) {
                final double uc = (o.u0 + o.u1) / 2, r = (o.u1 - o.u0) / 2;
                mb.patch((th, z, p, nn) -> {
                    p[0] = uc + r * Math.cos(th); p[1] = sp + r * Math.sin(th); p[2] = z;
                    nn[0] = -Math.cos(th); nn[1] = -Math.sin(th); nn[2] = 0;
                }, 0, Math.PI, 14, -depth, 0, 1, in);
                Arch.capStrips(mb, o, 0, 1, out);
                Arch.capStrips(mb, o, -T, -1, in);
            } else {
                mb.quad(Arch.P(o.u0, o.y1, 0), Arch.P(o.u1, o.y1, 0), Arch.P(o.u1, o.y1, -depth), Arch.P(o.u0, o.y1, -depth), 0, -1, 0, in);
            }
            if (o.kind <= 1) {
                double f = 0.22;
                mb.box(o.u0 - f, o.y0 - 0.14, 0, o.u1 + f, o.y0, 0.14, frame, false);
                mb.box(o.u0 - f, o.y0, 0, o.u0, o.y1 + f, 0.07, frame, false);
                mb.box(o.u1, o.y0, 0, o.u1 + f, o.y1 + f, 0.07, frame, false);
                mb.box(o.u0 - f, o.y1, 0, o.u1 + f, o.y1 + f, 0.1, frame, false);
            } else if (o.kind == 2) {
                double f = 0.25;
                mb.box(o.u0 - f, o.y0, 0, o.u0, o.y1 + f, 0.1, frame, false);
                mb.box(o.u1, o.y0, 0, o.u1 + f, o.y1 + f, 0.1, frame, false);
                mb.box(o.u0 - f, o.y1, 0, o.u1 + f, o.y1 + f, 0.14, frame, false);
            }
        }
        mb.maxEdge = keep;
    }

    /** Baut einen Baukörper als Ruine: vier Wände mit gebrochener Krone, Gesimse nur, wo die Wand noch steht, Schutt innen und vor den Durchbrüchen. */
    static int block(MeshBuilder mb, Block b) {
        String key = keyOf(b);
        int v0 = mb.vertexCount();
        double T = Math.max(b.T, b.hollow ? b.T : 1.6);
        double yb = b.y0 - b.foot;
        double[][] start = {{-b.W / 2, b.D / 2}, {b.W / 2, b.D / 2}, {b.W / 2, -b.D / 2}, {-b.W / 2, -b.D / 2}};
        double[][] dir = {{1, 0}, {0, -1}, {-1, 0}, {0, 1}};
        double[][] nrm = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
        double keepEdge = mb.maxEdge;
        mb.maxEdge = 1e9;
        for (int k = 0; k < 4; k++) {
            int vf = mb.vertexCount();
            final int kk = k;
            wall(mb, b.len(k), yb, T, b.f[k].ops, u -> top(b, key, kk, u), b.frame == Mat.STATUE ? Mat.SOOT : b.frame);
            // Gesimse und Eckquader, soweit die Krone sie trägt
            for (double[] r : b.f[k].ribs) {
                int segs = Math.max(1, (int) Math.ceil((r[1] - r[0]) / 1.15));
                for (int s = 0; s < segs; s++) {
                    double ua = r[0] + (r[1] - r[0]) * s / segs, ub = r[0] + (r[1] - r[0]) * (s + 1) / segs;
                    double ht = Math.min(top(b, key, k, Math.max(0, Math.min(b.len(k), ua))), Math.min(top(b, key, k, Math.max(0, Math.min(b.len(k), ub))),
                            top(b, key, k, Math.max(0, Math.min(b.len(k), (ua + ub) / 2)))));
                    double y1 = Math.min(r[3], ht - 0.05);
                    if (y1 - r[2] < 0.15) continue;
                    Arch.rib(mb, ua, ub, r[2], y1, r[4], Mat.SOOT);
                }
            }
            mb.transform(vf, k * Math.PI / 2, start[k][0], 0, start[k][1]);
        }
        // Schutt: Haufen im Raum und vor den Durchbrüchen (im Körperrahmen)
        int rs = key.hashCode() * 13 + stage;
        double iw = b.W - 2 * T, id = b.D - 2 * T;
        if (iw > 1 && id > 1) {
            int cnt = (int) Math.max(2, Math.min(9, iw * id / 55)) + stage;
            for (int i = 0; i < cnt; i++) {
                double px = -iw / 2 + iw * h1(rs, 60 + i), pz = -id / 2 + id * h1(rs, 80 + i);
                double r = 0.9 + 1.7 * h1(rs, 100 + i);
                mound(mb, px, pz, b.y0 + 0.02, r, 0.35 + 0.8 * h1(rs, 120 + i) + 0.2 * stage, r * (0.8 + 0.5 * h1(rs, 140 + i)), rs + i);
            }
            // Boden: Schutt und Erde über die ganze Fläche (flach)
            mb.rectH(-iw / 2, -id / 2, iw / 2, id / 2, b.y0 + 0.08, true, Mat.RUBBLE);
        }
        for (int k = 0; k < 4; k++) {
            double[][] br = breaches(key, k, b.len(k));
            for (int i = 0; i < br.length; i++) {
                if (br[i][2] <= 0) continue;
                double u = br[i][0], r = Math.min(4.2, br[i][1] * 0.85) * (0.6 + 0.4 * br[i][2]);
                double bx = start[k][0] + dir[k][0] * u + nrm[k][0] * (r * 0.7 + 0.4), bz = start[k][1] + dir[k][1] * u + nrm[k][1] * (r * 0.7 + 0.4);
                mound(mb, bx, bz, b.y0 - 0.1, r * 1.5, 1.2 + 1.8 * br[i][2], r, rs + 300 + 10 * k + i);
            }
        }
        mb.maxEdge = keepEdge;
        mb.transform(v0, b.yaw, b.cx, 0, b.cz);
        return v0;
    }

    /** Schuttkegel: unregelmäßige Halbkugel aus Brocken, Fuß auf Höhe y. */
    static void mound(MeshBuilder mb, double cx, double cz, double y, double rx, double ry, double rz, int seed) {
        final double a0 = h1(seed, 1) * 6.28;
        mb.patch((u, v, p, n) -> {
            double cv = Math.cos(v), sv = Math.sin(v);
            double wob = 1 + 0.30 * (n1(seed, u * 2.2, 1.0) - 0.5) + 0.15 * (n1(seed + 7, u * 6.0 + v * 3, 1.0) - 0.5);
            double ca = Math.cos(u + a0), sa = Math.sin(u + a0);
            p[0] = cx + rx * wob * cv * ca; p[1] = y + ry * wob * sv * (0.85 + 0.3 * n1(seed + 3, u * 3, 1.0)); p[2] = cz + rz * wob * cv * sa;
            n[0] = cv * ca / rx; n[1] = Math.max(0.15, sv) / ry; n[2] = cv * sa / rz;
        }, 0, 2 * Math.PI, 10, 0, Math.PI / 2, 3, Mat.RUBBLE);
    }

    // ------------------------------------------------------------ Ringmauer

    /** Mauerstück von (x0,z0) nach (x1,z1): Kasten je 1,8 m mit eigener Höhe; Zinnen nur in Stufe 1 und nur zum Teil. */
    static void curtain(MeshBuilder mb, double x0, double z0, double x1, double z1, double yb, double yt, double t, int idx) {
        double keep = mb.maxEdge;
        mb.maxEdge = 1e9;
        double dx = x1 - x0, dz = z1 - z0, l = Math.hypot(dx, dz);
        double yaw = Math.atan2(dx, dz);
        double cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
        int v0 = mb.vertexCount();
        double[] drop = {0, 0.06, 0.28, 0.46};
        double H = yt - 0.0;
        int n = Math.max(1, (int) Math.ceil(l / 1.8));
        int seed = 4000 + idx * 17;
        // Durchbrüche der Ringmauer
        int cnt = h1(seed, 1) < 0.85 ? 1 + (h1(seed, 2) < 0.5 ? 1 : 0) : 0;
        int mat = wallMat();
        int cap = stage == 1 ? Mat.SOOT : Mat.RUBBLE;
        for (int i = 0; i < n; i++) {
            double za = -l / 2 + l * i / n, zb = -l / 2 + l * (i + 1) / n, zc = (za + zb) / 2 + l / 2;
            double h = yt - H * drop[stage] * (0.30 + 0.70 * n1(seed, zc, 6.0)) - H * (0.02 + 0.08 * drop[stage]) * n1(seed + 5, zc, 1.4);
            for (int q = 0; q < cnt; q++) {
                int appear = h1(seed, 10 + q) < 0.5 ? 2 : 3;
                if (stage < appear) continue;
                double depth = stage == appear ? 0.65 : 1.0;
                double c = l * (0.2 + 0.6 * h1(seed, 20 + q)), half = 3 + 6 * h1(seed, 30 + q), f = 0.12 + 0.3 * h1(seed, 40 + q);
                double bump = 1 - sm(0.45, 1.0, Math.abs(zc - c) / half);
                h = Math.min(h, yt - H * (1 - f) * depth * bump);
            }
            h = Math.max(h, 0.8);
            mb.box(-t / 2, yb, za, t / 2, h, zb, mat, false);
            mb.quad(Arch.P(-t / 2, h, za), Arch.P(t / 2, h, za), Arch.P(t / 2, h, zb), Arch.P(-t / 2, h, zb), 0, 1, 0, cap);
            // Zinnen: in Stufe 1 noch gut die Hälfte, danach keine
            if (stage == 1 && h >= yt - 0.4 && h1(seed + 9, i) < 0.55 && i % 1 == 0) {
                double zc2 = (za + zb) / 2;
                mb.box(-t / 2 - 0.15, h, zc2 - 0.55, t / 2 + 0.15, h + 1.3, zc2 + 0.55, Mat.SOOT, true);
            }
        }
        mb.transform(v0, yaw, cx, 0, cz);
        mb.maxEdge = keep;
    }

    // ------------------------------------------------------------ Türme

    /**
     * Hohler Turm aus Sektoren: außen Radius r, innen r − wall, Kronenhöhe je Sektor aus h(θ). Boden bei yFloor.
     */
    static void ringTower(MeshBuilder mb, double cx, double cz, double r, double wall, double yb, double yFloor, int sectors, DoubleUnaryOperator h) {
        double keep = mb.maxEdge;
        mb.maxEdge = 1e9;
        double ri = Math.max(0.5, r - wall);
        double[] hh = new double[sectors];
        for (int s = 0; s < sectors; s++) hh[s] = Math.max(yFloor + 0.3, h.applyAsDouble((s + 0.5) * 2 * Math.PI / sectors));
        final int out = wallMat(), in = Mat.SOOT, cap = stage == 1 ? Mat.SOOT : Mat.RUBBLE;
        for (int s = 0; s < sectors; s++) {
            double t0 = s * 2 * Math.PI / sectors, t1 = (s + 1) * 2 * Math.PI / sectors, top = hh[s];
            int rings = Math.max(1, (int) Math.ceil((top - yb) / 5.0));
            final double ft = top;
            mb.patch((u, v, p, n) -> {
                p[0] = cx + r * Math.cos(u); p[1] = yb + (ft - yb) * v; p[2] = cz + r * Math.sin(u);
                n[0] = Math.cos(u); n[1] = 0; n[2] = Math.sin(u);
            }, t0, t1, 1, 0, 1, rings, out);
            mb.patch((u, v, p, n) -> {
                p[0] = cx + ri * Math.cos(u); p[1] = yb + (ft - yb) * v; p[2] = cz + ri * Math.sin(u);
                n[0] = -Math.cos(u); n[1] = 0; n[2] = -Math.sin(u);
            }, t0, t1, 1, 0, 1, rings, in);
            mb.patch((u, v, p, n) -> {
                double rr = ri + (r - ri) * v;
                p[0] = cx + rr * Math.cos(u); p[1] = ft; p[2] = cz + rr * Math.sin(u);
                n[0] = 0; n[1] = 1; n[2] = 0;
            }, t0, t1, 1, 0, 1, 1, cap);
            // Stufe zum nächsten Sektor: Schnittfläche in Richtung des tieferen
            int nx = (s + 1) % sectors;
            double a = t1, lo = Math.min(top, hh[nx]), hi = Math.max(top, hh[nx]);
            if (hi - lo > 0.02) {
                double tx = -Math.sin(a), tz = Math.cos(a);
                double sgn = top > hh[nx] ? 1 : -1;
                mb.quad(new double[]{cx + ri * Math.cos(a), lo, cz + ri * Math.sin(a)}, new double[]{cx + r * Math.cos(a), lo, cz + r * Math.sin(a)},
                        new double[]{cx + r * Math.cos(a), hi, cz + r * Math.sin(a)}, new double[]{cx + ri * Math.cos(a), hi, cz + ri * Math.sin(a)},
                        tx * sgn, 0, tz * sgn, in);
            }
        }
        // Boden: Schutt
        mb.patch((u, v, p, n) -> {
            p[0] = cx + ri * v * Math.cos(u); p[1] = yFloor + 0.35 * v * (n1(77, u * 2.0 + cx, 1.0) - 0.3); p[2] = cz + ri * v * Math.sin(u);
            n[0] = 0; n[1] = 1; n[2] = 0;
        }, 0, 2 * Math.PI, 14, 0, 1, 2, Mat.RUBBLE);
        mb.maxEdge = keep;
    }

    /** Kronenhöhe eines runden Turms: Verlust d (Bruchteil) mit Rauschen; ein Sektor um outDeg herum bricht bis zu out (Bruchteil) ein. */
    static double towerTop(int seed, double th, double yb, double yt, double d, double outAngle, double outDrop, double outHalf) {
        double H = yt - yb;
        double nl = n1(seed, th * 6.0, 1.0), nh = n1(seed + 9, th * 17.0, 1.0);
        double drop = d * (0.3 + 0.7 * nl) + (0.02 + 0.08 * d) * nh;
        if (outDrop > 0) {
            double da = Math.abs(Math.atan2(Math.sin(th - outAngle), Math.cos(th - outAngle)));
            double k = 1 - sm(outHalf * 0.55, outHalf, da);
            drop = drop * (1 - k) + Math.max(drop, outDrop) * k;
        }
        return yt - H * Math.min(0.97, drop);
    }

    static void towers(MeshBuilder mb) {
        double[] dr = {0, 0.14, 0.28, 0.46};
        int s = stage;
        // Glockenturm, Apothekerturm, vier Wehrtürme
        double[][] small = {{-58, -44, 4.6, 24}, {-10, -52, 3.6, 16}, {72, -12, 3.6, 16}, {24, 58, 3.6, 16}, {-36, 56, 3.6, 16}, {-58, 36, 4.2, 20}};
        for (int i = 0; i < small.length; i++) {
            final double[] t = small[i];
            final int sd = 700 + i * 13;
            double oa = h1(sd, 5) * 6.28;
            double od = s >= 2 ? (s == 2 ? 0.45 : 0.7) * (0.5 + 0.5 * h1(sd, 6)) : 0;
            ringTower(mb, t[0], t[1], t[2], Math.min(2.2, t[2] * 0.5), -4, t[3] * 0.5, 20, th -> towerTop(sd, th, -4, t[3], dr[s], oa, od, 1.0));
        }
        // Dicker Turm: 1693 zur Hälfte weggesprengt (die Seite nach außen)
        {
            final double tx = 60, tz = -44;
            double in = Math.atan2(2 - tz, 8 - tx), out = in + Math.PI;
            double od = s == 1 ? 0 : (s == 2 ? 0.72 : 0.80);
            ringTower(mb, tx, tz, 12.5, 6.2, -6, 12, 36, th -> towerTop(811, th, -6, 34, dr[s] * 0.8, out, od, 1.45));
            // herabgestürzte Mauerbrocken vor dem Turm
            if (s >= 2) for (int i = 0; i < 5; i++) {
                double a = out + (h1(815, i) - 0.5) * 1.6, d = 14 + 9 * h1(816, i);
                mound(mb, tx + d * Math.cos(a), tz + d * Math.sin(a), -2.5 + 2.0 * h1(819, i), 3.5 + 3 * h1(817, i), 3.5 + 2.5 * h1(818, i), 3.5 + 3 * h1(820, i), 830 + i);
            }
        }
        // Krautturm, der Gesprengte Turm: die Außenhälfte stürzt in den Graben, die Innenhälfte bleibt stehen
        {
            final double tx = 68, tz = 48;
            double in = Math.atan2(2 - tz, 8 - tx), out = in + Math.PI;
            double od = s == 1 ? 0 : (s == 2 ? 0.90 : 0.92);
            ringTower(mb, tx, tz, 8.0, 3.9, -5, 8, 32, th -> towerTop(901, th, -5, 27, dr[s] * 0.6, out, od, 1.62));
            if (s >= 2) for (int i = 0; i < 6; i++) {
                double a = out + (h1(905, i) - 0.5) * 1.8, d = 9 + 8 * h1(906, i);
                mound(mb, tx + d * Math.cos(a), tz + d * Math.sin(a), -3 + 1.5 * h1(909, i), 3 + 2.5 * h1(907, i), 3.0 + 2.5 * h1(908, i), 3 + 2.5 * h1(910, i), 930 + i);
            }
        }
    }

    // ------------------------------------------------------------ Brunnenhalle und Hof

    static void brunnenhalle(MeshBuilder mb) {
        double cx = -21, cz = 5;
        int s = stage;
        for (int i = 0; i < 4; i++) {
            double x = cx + (i % 2 == 0 ? -2.6 : 2.6), z = cz + (i < 2 ? -2.6 : 2.6);
            // Stufe 2 und 3: zwei Säulen sind gebrochen
            double top = 5.4;
            if (s >= 2 && (i == 1 || i == 2)) top = i == 1 ? 3.1 : 2.2;
            if (top < 5.4) {
                Arch.column(mb, x, z, 0.04, top, 0.42, Mat.MARBLE);
                mb.cylinder(x, z, top - 0.01, top + 0.0, 0.4, 0.4, 12, Mat.MARBLE, true);
            } else Arch.column(mb, x, z, 0.04, top, 0.42, Mat.MARBLE);
        }
        if (s == 1) mb.box(cx - 3.6, 5.4, cz - 3.6, cx + 3.6, 5.9, cz + 3.6, Mat.SOOT, true);
        else {
            // Gebälk liegt in Stücken am Boden
            for (int i = 0; i < 4; i++) {
                double a = h1(1500, i) * 6.28, d = 1.5 + 3.5 * h1(1501, i);
                int v0 = mb.vertexCount();
                mb.box(-1.4, 0, -0.35, 1.4, 0.55, 0.35, Mat.SOOT, true);
                mb.transform(v0, h1(1502, i) * 6.28, cx + d * Math.cos(a), 0.0, cz + d * Math.sin(a));
            }
        }
        Arch.roundBasin(mb, cx, cz, 0.0, 1.0, 0.45, 1.0, 0.35, 20, Mat.STATUE, Mat.BASIN);
    }

    /** Schutthaufen im Hof und an den Mauern, mit der Stufe mehr. */
    static void courtyardRubble(MeshBuilder mb) {
        int n = new int[]{0, 7, 20, 36}[stage];
        for (int i = 0; i < n; i++) {
            double x = -56 + 94 * h1(2000, i * 2 + stage * 0), z = -21 + 43 * h1(2001, i * 2);
            if (x < -18 && z > -8 && z < 9) continue;            // die Torgasse bleibt frei
            if (Math.hypot(x + 21, z - 5) < 7) continue;         // Brunnenhalle
            if (Math.hypot(x - 9, z + 24) < 3) continue;
            double r = 0.8 + 1.8 * h1(2002, i) * (stage == 3 ? 1.3 : 1);
            mound(mb, x, z, 0.0, r * 1.2, 0.3 + 0.5 * h1(2003, i) + 0.1 * stage, r, 2100 + i);
        }
    }

    /** Alle Ruinenstufen der Reihe nach; jede Stufe steht in ihrer Zeit-Maske. Liefert die Eckenzahl am Ende jeder Stufe. */
    public static int[] buildAll(MeshBuilder mb, List<float[]> dust) {
        int[] ends = new int[4];
        for (int s = 1; s <= 3; s++) {
            stage = s;
            active = true;
            mb.era = ERA[s];
            Castle.buildRuin(mb);
            mb.era = MeshBuilder.ERA_ALL;
            active = false;
            ends[s] = mb.vertexCount();
        }
        return ends;
    }
}
