package com.dan.heidelberg.camera;

import com.dan.heidelberg.castle.Heraldry;
import com.dan.heidelberg.castle.Hortus;
import com.dan.heidelberg.castle.Wasserweg;
import com.dan.heidelberg.core.Solids;
import com.dan.heidelberg.core.Terrain;

import java.util.ArrayList;
import java.util.List;

/**
 * Die fertigen Kamerafahrten: Rundumblick, Anflug, Orbit um das Schloss, Überflug des Hortus, Rundgang durch Tor
 * und Hof, Zoom bis zu den Wappen. Jede Fahrt ist ein {@link Shot}; die Lagen sind gegen Gelände und feste Körper
 * geprüft (Werkzeug CameraCheck). Wer sie abspielt, lässt die Kamera zuerst vom jetzigen Standort an den Anfang fliegen.
 */
public final class Rides {
    private Rides() { }

    /** Eine Fahrt mit Namen und einem Satz zur Beschreibung. */
    public static final class Ride {
        public final String name, info;
        public final Shot shot;
        Ride(String name, String info, Shot shot) { this.name = name; this.info = info; this.shot = shot; }
        @Override public String toString() { return name; }
    }

    /** Wappen in der Reihenfolge, wie das Schloss sie anlegt. */
    static final String[] SHIELD_NAMES = {"Ottheinrichsbau", "Friedrichsbau", "Torturm"};

    public static List<Ride> build(Terrain t, Solids so) {
        List<Ride> l = new ArrayList<>();
        l.add(new Ride("Rundumblick im Schlosshof", "Ein voller Kreis aus dem Hof, die Fassaden ziehen vorbei.", pan(t, so, "Hof zum Friedrichsbau", 54, -2, 0, "Rundumblick im Schlosshof")));
        l.add(new Ride("Rundumblick vom Hortus", "Ein voller Kreis von der Hauptterrasse: Schloss, Garten, Rheinebene.", pan(t, so, "Hortus Hauptterrasse", 54, 1, 0, "Rundumblick vom Hortus")));
        l.add(new Ride("Rundumblick vom Königstuhl", "Ein voller Kreis vom Gipfel, 45 m über dem Boden, damit die Baumkronen den Blick freigeben: Tal, Stadt, Schloss, Ebene.", pan(t, so, "Königstuhl", 60, -4, 45, "Rundumblick vom Königstuhl")));
        l.add(new Ride("Anflug auf das Schloss", "Vom Neckar über den Torturm hinab in den Hof.", anflug(t, "Anflug auf das Schloss")));
        l.add(new Ride("Orbit um das Schloss", "Ein hoher Kreis um die ganze Anlage.", orbit(t, "Orbit um das Schloss")));
        l.add(new Ride("Überflug des Hortus", "Tief über die Terrassen, an Becken und Säulenbrunnen vorbei.", hortus(t, "Überflug des Hortus")));
        l.add(new Ride("Vom Hof in den Fassbau", "Ohne Schnitt vom Hof durch die Tür des Fassbaus, am Großen Fass entlang und um das Fass herum.", fassbau(t, "Vom Hof in den Fassbau")));
        l.add(new Ride("Rundgang durch Tor und Hof", "Zu Fuß von der Torbrücke durch den Torturm, über den Hof bis in den Gläsernen Saalbau.", rundgang(t, "Rundgang durch Tor und Hof")));
        l.add(new Ride("Dem Wasser folgen", "Von der Quelle am Hang über die Terrassen, durch Leitung und Grotte, über die Westmauer und den Bach bis zum Neckar (mit Perlen).", wasserweg(t, so, "Dem Wasser folgen")));
        for (int i = 0; i < Heraldry.SHIELDS.size() && i < SHIELD_NAMES.length; i++) {
            l.add(new Ride("Zoom zum Wappen am " + SHIELD_NAMES[i], "Aus dem Hof (oder von der Brücke) bis vor das kurpfälzische Wappen.", zoomShield(Heraldry.SHIELDS.get(i), "Zoom zum Wappen am " + SHIELD_NAMES[i])));
        }
        return l;
    }

    /** Folgt dem Wasserweg: im Hang und am Bach dem Wasser nach, im Hortus von Hand geführt (Leitungen und Grotte sind nur durch Fahrt über der Terrasse und durch die Tür zu zeigen). */
    static Shot wasserweg(Terrain t, Solids so, String name) {
        CameraPath p = new CameraPath().named(name);
        double L = Wasserweg.length();
        double[] o = new double[4], q = new double[4], pv = new double[4];
        double tt = 0, s = 0;
        double s4 = Wasserweg.sectionStart(4), s8 = Wasserweg.sectionStart(8);
        // 1) Quelle bis Mauerfall: dem Wasser nach
        while (s < s4) {
            Wasserweg.at(s, o);
            Wasserweg.at(Math.min(L, s + 22), q);
            Wasserweg.at(Math.max(0, s - 16), pv);
            double ex = pv[0], ez = pv[2];
            p.add(tt, ex, t.stand(ex, ez, 1e9) + 6.5, ez, q[0], q[1], q[2]);
            double ds = 18;
            tt += ds / 5.0;
            s = Math.min(s4, s + ds);
        }
        // 2) Hortus von Hand: Trog, Säulenbrunnen, Achteckiges Becken, Grotte, Rhenusbecken
        double[][] k = {
                // Auge x, y, z; Ziel x, y, z; Sekunden bis zur Pose
                {-96, 9.0, 108, -100, 3.5, 112, 5},
                {-108, 8.2, 104, -118, 6.0, 111, 5},
                {-98, 9.5, 102, -82, 4.0, 102, 5},
                {-86, 9.0, 98, -70, 5.0, 102, 4},
                {-84, 9.5, 90, -72, 2.0, 84, 4},
                {-76, 9.0, 70, -70, -0.5, 84, 5},
                {-70, 1.5, 74, -70, 0.3, 90, 4},
                {-68, 0.6, 88, -70, -0.4, 97, 5},
                {-68, 0.6, 93, -70, -0.4, 99, 3},
                {-68, 0.6, 89, -70, -0.8, 80, 5},
                {-69, 1.5, 78, -77, -1.8, 79, 5},
                {-82, 3.8, 79, -100, -1.8, 79, 4}};
        for (double[] a : k) {
            tt += a[6];
            p.add(tt, a[0], a[1], a[2], a[3], a[4], a[5]);
        }
        // 3) Abflussrinne, Fall und Bach: dem Wasser nach
        s = s8;
        tt += 4;
        boolean first = false;
        while (true) {
            Wasserweg.at(s, o);
            Wasserweg.at(Math.min(L, s + 24), q);
            Wasserweg.at(Math.max(s8, s - 16), pv);
            double sec = o[3];
            double ex = pv[0], ez = pv[2];
            double h = sec >= 10 ? 30 : 5.5;
            p.add(tt, ex, t.stand(ex, ez, 1e9) + h, ez, q[0], q[1], q[2]);
            if (s >= L) break;
            double v = sec <= 9 ? 5.0 : 14.0, ds = sec <= 9 ? 12 : 22;
            tt += ds / v + (first ? 3 : 0);
            first = false;
            s = Math.min(L, s + ds);
        }
        return p;
    }

    // ------------------------------------------------------------ Bausteine

    static double sm(double x) { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); }

    /** Auge zu einer Blickpunktpose (Drehpunkt, Gier, Nick in Grad, Abstand), wie die Steuerung es setzt. */
    public static double[] eyeOf(Terrain t, double[] p) {
        double yw = Math.toRadians(p[3]), pt = Math.toRadians(Math.max(-35, Math.min(89, p[4]))), d = Math.max(3, p[5]), cp = Math.cos(pt);
        double ex = p[0] + d * cp * Math.sin(yw), ey = p[1] + d * Math.sin(pt), ez = p[2] + d * cp * Math.cos(yw);
        return new double[]{ex, Math.max(ey, t.stand(ex, ez, p[1]) + 1.7), ez};
    }

    /** Anfangs- und Endpose einer Fahrt: Auge, Ziel, Bildwinkel. */
    public static double[] startOf(Shot s) { double[] o = new double[7]; s.pose(0, o); return o; }

    /** Voller Kreis um den Blickpunkt-Standort: das Auge steht still, das Ziel läuft einmal herum (Uhrzeigersinn). */
    static Shot pan(Terrain t, Solids so, String vpName, double seconds, double pitchDeg, double lift, String name) {
        Viewpoint v = Viewpoint.ALL[Viewpoint.index(vpName)];
        double[] p = v.pose(t, so);
        double[] e = eyeOf(t, p);
        e[1] += lift;
        if (so != null) so.push(e, CameraController.R_EYE);
        final double[] eye = e;
        final double yaw0 = Math.atan2(p[0] - e[0], p[2] - e[2]);
        final double tp = Math.tan(Math.toRadians(pitchDeg));
        final double fov = Math.toRadians(62);
        return new Shot() {
            @Override public String name() { return name; }
            @Override public double duration() { return seconds; }
            @Override public void pose(double tt, double[] o) {
                double u = sm(tt / seconds);
                double a = yaw0 - 2 * Math.PI * u;
                o[0] = eye[0]; o[1] = eye[1]; o[2] = eye[2];
                o[3] = eye[0] + 100 * Math.sin(a); o[4] = eye[1] + 100 * tp; o[5] = eye[2] + 100 * Math.cos(a);
                o[6] = fov;
            }
        };
    }

    /**
     * Langsamer Bogen um den Drehpunkt eines Blickpunkts: Gier, Nick (Grad) und Abstand gleiten von der ersten zur zweiten
     * Einstellung. Das Auge bleibt über dem Gelände.
     */
    public static Shot arc(Terrain t, Solids so, String vpName, double yaw0, double pitch0, double dist0,
                           double yaw1, double pitch1, double dist1, double seconds, String name) {
        Viewpoint v = Viewpoint.ALL[Viewpoint.index(vpName)];
        final double[] p = v.pose(t, so);
        return new Shot() {
            @Override public String name() { return name; }
            @Override public double duration() { return seconds; }
            @Override public void pose(double tt, double[] o) {
                double u = sm(tt / seconds);
                double yw = Math.toRadians(yaw0 + (yaw1 - yaw0) * u), pt = Math.toRadians(pitch0 + (pitch1 - pitch0) * u);
                double d = Math.exp(Math.log(dist0) + (Math.log(dist1) - Math.log(dist0)) * u), cp = Math.cos(pt);
                double ex = p[0] + d * cp * Math.sin(yw), ey = p[1] + d * Math.sin(pt), ez = p[2] + d * cp * Math.cos(yw);
                o[0] = ex; o[1] = Math.max(ey, t.stand(ex, ez, p[1]) + 1.7); o[2] = ez;
                o[3] = p[0]; o[4] = p[1]; o[5] = p[2];
                o[6] = CameraPath.FOV;
            }
        };
    }

    /** Das Auge steht still an einem Ort, der Blick schwenkt von Richtung yaw0 (Grad, Blickrichtung) nach yaw1, Nick von p0 nach p1. */
    public static Shot look(double ex, double ey, double ez, double yaw0, double yaw1, double p0, double p1, double fovDeg, double seconds, String name) {
        return new Shot() {
            @Override public String name() { return name; }
            @Override public double duration() { return seconds; }
            @Override public void pose(double tt, double[] o) {
                double u = sm(tt / seconds);
                double a = Math.toRadians(yaw0 + (yaw1 - yaw0) * u), pt = Math.toRadians(p0 + (p1 - p0) * u);
                o[0] = ex; o[1] = ey; o[2] = ez;
                o[3] = ex + 100 * Math.sin(a); o[4] = ey + 100 * Math.tan(pt); o[5] = ez + 100 * Math.cos(a);
                o[6] = Math.toRadians(fovDeg);
            }
        };
    }

    /** Vom Neckar über den Torturm hinab in den Hof. */
    public static Shot anflug(Terrain t, String name) {
        Viewpoint v = Viewpoint.ALL[Viewpoint.index("Schloss vom Neckar")];
        double[] e0 = eyeOf(t, v.pose(t));
        CameraPath p = new CameraPath().named(name);
        p.add(0, e0[0], e0[1], e0[2], 0, 14, 0);
        p.then(9, -150, 80, -140, -30, 24, -8);
        p.then(8, -122, 74, 2, -52, 30, 2);
        p.then(7, -62, 70, 4, -10, 12, -6);
        p.then(6, -12, 44, 18, 31, 7, -22.6);
        p.then(6, 12, 13, 20, 31, 7, -22.6);
        return p;
    }

    /** Hoher Kreis um die ganze Anlage. */
    public static Shot orbit(Terrain t, String name) {
        CameraPath p = new CameraPath().named(name);
        int n = 24;
        double secs = 70, a0 = Math.toRadians(205);
        for (int k = 0; k <= n; k++) {
            double a = a0 - 2 * Math.PI * k / n;
            double r = 150 + 10 * Math.sin(2 * a);
            double y = 66 + 12 * Math.sin(a * 1.0 + 1);
            p.add(secs * k / n, 8 + r * Math.sin(a), y, 2 + r * Math.cos(a), 8, 16, 2);
        }
        return p;
    }

    /** Tief über die Terrassen des Hortus, an Becken und Säulenbrunnen vorbei. */
    public static Shot hortus(Terrain t, String name) {
        double[][] k = {
                {-8, 134, 6}, {-34, 122, 5.5}, {-62, 120, 5.5}, {-86, 120, 6}, {-108, 122, 6.5}, {-124, 126, 7}};
        double[][] tg = {
                {-45, 3, 112}, {-70, 3, 108}, {-90, 4, 108}, {-112, 5, 110}, {-118, 6, 111}, {-118, 6, 111}};
        CameraPath p = new CameraPath().named(name);
        double tt = 0;
        for (int i = 0; i < k.length; i++) {
            double y = t.stand(k[i][0], k[i][1], 1e9) + k[i][2];
            if (i > 0) tt += Math.max(4.0, Math.hypot(k[i][0] - k[i - 1][0], k[i][1] - k[i - 1][1]) / 5.0);
            p.add(tt, k[i][0], y, k[i][1], tg[i][0], tg[i][1], tg[i][2]);
        }
        return p;
    }

    /** Zu Fuß: Torbrücke, Torturm, Hof, Wappen, Gläserner Saalbau. */
    public static Shot rundgang(Terrain t, String name) {
        final double Y = 1.75;
        double[][] k = {
                // Auge x, z; Ziel x, y, z
                {-97, 2, -62, 16, 2},
                {-82, 2, -57.4, 19.4, 2},
                {-68, 2, -52, 8, 2},
                {-57, 2, -30, 3, 2},
                {-47, 2, -20, 4, 2},
                {-35, -3, 31, 6.0, -22.6},
                {-14, -4, 31, 6.0, -22.6},
                {4, -5, 31, 6.0, -22.6},
                {7, -8, -17, 10.2, -21.6},
                {6.6, -15, 8, 3.5, -30},
                {8, -24, 8, 3.0, -30},
                {8, -27, 14, 4.5, -30},
                {8, -27, 8, 3.0, -12},
        };
        CameraPath p = new CameraPath().named(name);
        double tt = 0;
        for (int i = 0; i < k.length; i++) {
            if (i > 0) tt += Math.max(2.5, Math.hypot(k[i][0] - k[i - 1][0], k[i][1] - k[i - 1][1]) / 2.8);
            p.add(tt, k[i][0], Y, k[i][1], k[i][2], k[i][3], k[i][4]);
        }
        return p;
    }

    /** Zu Fuß vom Hof über die Schwelle des Fassbaus, vor das Fass und einmal um das Fass herum. */
    public static Shot fassbau(Terrain t, String name) {
        final double Y = 1.75;
        double[][] k = {
                // Auge x, z; Ziel x, y, z
                {10, 2, 30, 3.0, 16.8},
                {20, 6.5, 34, 2.2, 16.8},
                {28, 13.5, 34.5, 2.0, 16.8},
                {32.0, 16.8, 38, 2.6, 16.8},
                {35.0, 16.8, 40, 3.2, 16.8},
                {37.0, 17.4, 40.05, 3.3, 16.8},
                {37.2, 21.8, 43.4, 3.0, 19},
                {41.0, 24.0, 43.4, 3.0, 16.8},
                {46.5, 23.5, 43.4, 3.4, 16.8},
                {49.0, 17.0, 43.4, 3.4, 16.8},
                {47.0, 11.0, 40.0, 2.8, 16.8},
                {41.0, 11.8, 34.0, 2.4, 16.8},
        };
        CameraPath p = new CameraPath().named(name);
        double tt = 0;
        for (int i = 0; i < k.length; i++) {
            if (i > 0) tt += Math.max(2.5, Math.hypot(k[i][0] - k[i - 1][0], k[i][1] - k[i - 1][1]) / 2.6);
            p.add(tt, k[i][0], Y, k[i][1], k[i][2], k[i][3], k[i][4]);
        }
        return p;
    }

    /** Aus der Ferne dicht vor das Wappen; der Bildwinkel wird enger. s = {x, y, z, nx, nz, rx, rz, Breite}. */
    static Shot zoomShield(double[] s, String name) {
        double cx = s[0], cy = s[1], cz = s[2], nx = s[3], nz = s[4];
        CameraPath p = new CameraPath().named(name);
        double[] dist = {40, 22, 11, 5.2}, up = {7, 4, 1.4, 0};
        double[] fov = {55, 50, 38, 22};
        double[] dt = {0, 5.5, 5.5, 5.5};
        double tt = 0;
        for (int i = 0; i < dist.length; i++) {
            tt += dt[i];
            p.add(tt, cx + nx * dist[i], cy + up[i], cz + nz * dist[i], cx + nx * 0.8, cy + up[i] * 0.3, cz + nz * 0.8, Math.toRadians(fov[i]));
        }
        return p;
    }
}
