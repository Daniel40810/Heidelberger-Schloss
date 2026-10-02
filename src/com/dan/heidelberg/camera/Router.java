package com.dan.heidelberg.camera;

import com.dan.heidelberg.core.Solids;
import com.dan.heidelberg.core.Terrain;

import java.util.ArrayList;
import java.util.List;

/**
 * Plant einen Flug von einer Kameralage zur nächsten, der nicht durch Mauern, Bäume oder Gelände geht.
 * Reihenfolge der Versuche: die gerade Strecke; schräg auf, in sicherer Höhe quer und schräg hinab; und wenn Auge
 * oder Ziel in einem Raum oder Hof eingeschlossen sind, erst ein Weg ins Freie (Suche im Raster mit 1,2 m). Jeder
 * Versuch wird als fertige Kurve in 0,04-s-Schritten geprüft, erst dann gilt er.
 */
public final class Router {
    private Router() { }

    /** Mindesthöhe des Auges über dem Gelände auf dem Flug (m). */
    public static final double CLEAR_GROUND = 2.0;
    static final double R = CameraController.R_EYE + 0.1;

    /** Kleinster Abstand des Auges zu festen Körpern auf einem Flug (Nahebene 0,2 m + Rand). */
    public static final double VALID_R = 0.3;
    static final boolean DEBUG = Boolean.getBoolean("heidelberg.routedebug");

    static double len(double[] a, double[] b) { return Math.sqrt(sq(a[0] - b[0]) + sq(a[1] - b[1]) + sq(a[2] - b[2])); }

    static double sq(double x) { return x * x; }

    /** Dauer eines Fluges über die Weglänge s (m): kurz für Nahes, höchstens 8 s für Fernes. */
    public static double duration(double s) { return Math.min(8, 1.6 + 0.9 * Math.log(1 + s / 30)); }

    /** Ist die Kurve in kleinen Schritten frei von festen Körpern und Gelände? */
    public static boolean valid(Solids so, Terrain t, Shot s) {
        double[] a = new double[7], b = new double[7];
        double dur = s.duration();
        s.pose(0, a);
        for (double tt = 0.04; ; tt += 0.04) {
            double u = Math.min(tt, dur);
            s.pose(u, b);
            if (t != null && b[1] < t.stand(b[0], b[2], b[1]) + CLEAR_GROUND - 0.5) { if (DEBUG) System.out.printf("    Boden bei t=%.2f (%.0f/%.0f/%.0f)%n", tt, b[0], b[1], b[2]); return false; }
            if (so != null && !so.segmentFree(a[0], a[1], a[2], b[0], b[1], b[2], VALID_R)) { if (DEBUG) System.out.printf("    Körper bei t=%.2f (%.1f/%.1f/%.1f)%n", tt, b[0], b[1], b[2]); return false; }
            if (u >= dur) return true;
            System.arraycopy(b, 0, a, 0, 7);
        }
    }

    /** Pfad aus Eckpunkten mit gleitendem Blickziel zu einem Shot machen. */
    static CameraPath path(List<double[]> w, double[] p0, double[] p1, String name) {
        double total = 0;
        double[] seg = new double[w.size() - 1];
        for (int i = 0; i < seg.length; i++) { seg[i] = len(w.get(i), w.get(i + 1)); total += seg[i]; }
        double T = duration(total);
        double[] wt = new double[seg.length];
        double wsum = 0;
        for (int i = 0; i < seg.length; i++) { wt[i] = Math.pow(Math.max(seg[i], 1), 0.85); wsum += wt[i]; }
        // kurze Stücke (Türen, Höfe) höchstens 30 m/s, lange nach dem Anteil an der Gesamtzeit
        double[] dt = new double[seg.length];
        for (int i = 0; i < seg.length; i++) dt[i] = Math.max(T * wt[i] / wsum, seg[i] < 80 ? seg[i] / 30.0 : 0);
        CameraPath cp = new CameraPath().named(name);
        double acc = 0, tt = 0;
        for (int i = 0; i < w.size(); i++) {
            double u = total < 1e-9 ? 0 : acc / total;
            if (i > 0) tt += dt[i - 1];
            double[] q = w.get(i);
            cp.add(tt, q[0], q[1], q[2], p0[0] + (p1[0] - p0[0]) * u, p0[1] + (p1[1] - p0[1]) * u, p0[2] + (p1[2] - p0[2]) * u);
            if (i < seg.length) acc += seg[i];
        }
        return cp;
    }

    public static Shot route(Solids so, Terrain t, double[] e0, double[] p0, double[] e1, double[] p1, String name) {
        if (so == null) return path(java.util.Arrays.asList(e0, e1), p0, p1, name);
        // 1) gerade
        List<double[]> w = new ArrayList<>(java.util.Arrays.asList(e0, e1));
        CameraPath cp = path(w, p0, p1, name);
        if (valid(so, t, cp)) return cp;
        // 2) Wege ins Freie von beiden Enden (leer, wenn das Ende schon frei in den Himmel sieht)
        List<double[]> out0 = escape(so, t, e0), out1 = escape(so, t, e1);
        if (out0 == null) out0 = new ArrayList<>(java.util.Arrays.asList(e0));
        if (out1 == null) out1 = new ArrayList<>(java.util.Arrays.asList(e1));
        double[] x0 = out0.get(out0.size() - 1), x1 = out1.get(out1.size() - 1);
        if (DEBUG) System.out.printf("Router: Ausgang0 %d Punkte (%.1f/%.1f/%.1f), Ausgang1 %d Punkte (%.1f/%.1f/%.1f)%n", out0.size(), x0[0], x0[1], x0[2], out1.size(), x1[0], x1[1], x1[2]);
        List<double[]> best = null;
        search:
        for (double lift : new double[]{0, 25, 45, 80, 130, 200, 300, 450, 700, 1100}) {
            double ay = Math.max(x0[1], x1[1]) + lift;
            double hx = x1[0] - x0[0], hz = x1[2] - x0[2];
            for (double f : lift == 0 ? new double[]{0} : new double[]{0.22, 0.10, 0.0}) {
                List<double[]> c = new ArrayList<>(out0);
                if (lift > 0) {
                    c.add(new double[]{x0[0] + hx * f, ay, x0[2] + hz * f});
                    c.add(new double[]{x1[0] - hx * f, ay, x1[2] - hz * f});
                }
                List<double[]> rev = new ArrayList<>(out1);
                java.util.Collections.reverse(rev);
                c.addAll(rev);
                // doppelte Punkte entfernen
                for (int i = c.size() - 1; i > 0; i--) if (len(c.get(i), c.get(i - 1)) < 0.3) c.remove(i);
                if (c.size() < 2) continue;
                CameraPath cand = path(c, p0, p1, name);
                boolean okc = valid(so, t, cand);
                if (DEBUG) System.out.printf("  Versuch Hub %.0f f %.2f: %d Punkte, %s%n", lift, f, c.size(), okc ? "gültig" : "ungültig");
                if (okc) { best = c; break search; }
            }
        }
        if (best == null) return cp;       // nichts gefunden: gerade; der Schutz der Steuerung hält das Auge aus den Körpern
        return path(best, p0, p1, name);
    }

    // ------------------------------------------------------------ Weg ins Freie

    private static boolean freeAt(Solids so, Terrain t, double x, double y, double z) {
        if (y < t.stand(x, z, y) + 1.5) return false;
        return so.clearance(x, y, z, R, null) >= R;
    }

    /**
     * Sucht von e aus einen Weg zu einem Punkt, von dem es 60 m senkrecht nach oben frei ist. Liefert die vereinfachte
     * Punktfolge (beginnt bei e); null, wenn e selbst schon frei nach oben sieht oder nichts gefunden wird.
     */
    public static List<double[]> escape(Solids so, Terrain t, double[] e) {
        if (so.segmentFree(e[0], e[1], e[2], e[0], e[1] + 60, e[2], R)) return null;
        final double s = 1.2;
        final int RAD = 55, UP = 50, DOWN = 8;                   // Zellen: Reichweite seitlich, hinauf, hinab
        int nx = 2 * RAD + 1, ny = UP + DOWN + 1;
        byte[] state = new byte[nx * nx * ny];                   // 0 unbekannt, 1 frei und besucht, 2 gesperrt
        int[] parent = new int[state.length];
        java.util.Arrays.fill(parent, -1);
        int[] queue = new int[state.length];
        int qh = 0, qt = 0;
        int c0 = RAD + RAD * nx + DOWN * nx * nx;
        double ox = e[0] - RAD * s, oy = e[1] - DOWN * s, oz = e[2] - RAD * s;
        state[c0] = 1;
        queue[qt++] = c0;
        int goal = -1;
        int[] d = {1, -1, nx, -nx, nx * nx, -nx * nx};
        int expanded = 0;
        while (qh < qt && goal < 0 && expanded < 90000) {
            int c = queue[qh++];
            expanded++;
            int ix = c % nx, iz = (c / nx) % nx, iy = c / (nx * nx);
            double x = ox + ix * s, y = oy + iy * s, z = oz + iz * s;
            if (c != c0 && so.segmentFree(x, y, z, x, y + 60, z, R) && y > t.stand(x, z, y) + 1.5) { goal = c; break; }
            for (int k = 0; k < 6; k++) {
                int jx = ix, jz = iz, jy = iy;
                if (k == 0) jx++; else if (k == 1) jx--; else if (k == 2) jz++; else if (k == 3) jz--; else if (k == 4) jy++; else jy--;
                if (jx < 0 || jz < 0 || jy < 0 || jx >= nx || jz >= nx || jy >= ny) continue;
                int j = jx + jz * nx + jy * nx * nx;
                if (state[j] != 0) continue;
                if (freeAt(so, t, ox + jx * s, oy + jy * s, oz + jz * s)) { state[j] = 1; parent[j] = c; queue[qt++] = j; }
                else state[j] = 2;
            }
        }
        if (goal < 0) return null;
        List<double[]> raw = new ArrayList<>();
        for (int c = goal; c >= 0; c = parent[c]) {
            int ix = c % nx, iz = (c / nx) % nx, iy = c / (nx * nx);
            raw.add(new double[]{ox + ix * s, oy + iy * s, oz + iz * s});
        }
        java.util.Collections.reverse(raw);
        raw.set(0, e);
        // Fadenziehen: vom aktuellen Punkt zum am weitesten sichtbaren
        List<double[]> out = new ArrayList<>();
        int i = 0;
        out.add(raw.get(0));
        while (i < raw.size() - 1) {
            int j = raw.size() - 1;
            while (j > i + 1) {
                double[] a = raw.get(i), b = raw.get(j);
                if (so.segmentFree(a[0], a[1], a[2], b[0], b[1], b[2], R)) break;
                j--;
            }
            out.add(raw.get(j));
            i = j;
        }
        // Zwischenpunkte alle 1,5 m, damit die Kurve in Türen und Gassen nicht ausschwingt
        List<double[]> dense = new ArrayList<>();
        for (int k = 0; k < out.size(); k++) {
            dense.add(out.get(k));
            if (k + 1 < out.size()) {
                double[] a = out.get(k), b = out.get(k + 1);
                int n = (int) Math.floor(len(a, b) / 1.5);
                for (int q = 1; q <= n; q++) {
                    double u = q / (n + 1.0);
                    dense.add(new double[]{a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u, a[2] + (b[2] - a[2]) * u});
                }
            }
        }
        return dense;
    }
}
