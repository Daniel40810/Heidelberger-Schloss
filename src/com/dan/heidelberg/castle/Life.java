package com.dan.heidelberg.castle;

import com.dan.heidelberg.core.Animals;
import com.dan.heidelberg.core.Engine3D;
import com.dan.heidelberg.core.Solids;
import com.dan.heidelberg.core.Sprites;
import com.dan.heidelberg.core.Terrain;

import java.util.ArrayList;
import java.util.List;

/**
 * Leben im Schloss (Phase 8): Hofstaat, Wachen, Küfer, Gäste im Saal, Reiter, eine Kutsche mit vier Pferden, ein
 * Fasskarren; dazu Tauben, Schwalben, Krähen, ein Bussard und Fledermäuse. Alles ist eine Funktion der Zeit: Jeder
 * Mensch geht auf einem festen Weg (Kreis, abgerundetes Rechteck, Pendelstrecke), seine Stelle zur Zeit t ergibt sich
 * aus Weglänge und Tempo, ohne gespeicherten Zustand. So sieht jedes Bild (auch ein Standbild in der Prüfung) gleich aus.
 * Wer zu welcher Stunde unterwegs ist, steht in {@link #present}.
 */
public final class Life {
    public volatile boolean enabled = true, riders = true, birds = true, crowd = true;
    /** Phase 9: in der Ruine gibt es keinen Hofstaat, nur die Vögel. */
    public volatile boolean ruin;
    /** Dichte des Hofstaats 0..1. */
    public volatile float density = 0.85f;

    private final Animals an = new Animals();
    private Solids solids;
    private Terrain terrain;

    // ------------------------------------------------------------ Wege

    /** Weg als Polygonzug mit Längen; closed = Rundweg. at() liefert Ort und Blickrichtung. */
    public static final class Path {
        public final double[] x, z, s;
        public final double total;
        public final boolean closed;

        Path(List<double[]> pts, boolean closed) {
            int n = pts.size() + (closed ? 1 : 0);
            x = new double[n]; z = new double[n]; s = new double[n];
            for (int i = 0; i < n; i++) {
                double[] q = pts.get(i % pts.size());
                x[i] = q[0]; z[i] = q[1];
                if (i > 0) s[i] = s[i - 1] + Math.hypot(x[i] - x[i - 1], z[i] - z[i - 1]);
            }
            total = s[n - 1];
            this.closed = closed;
        }

        /** o = {x, z, hx, hz} bei Weglänge d. */
        public void at(double d, double[] o) {
            if (closed) { d %= total; if (d < 0) d += total; } else d = Math.max(0, Math.min(total, d));
            int lo = 0, hi = s.length - 1;
            while (hi - lo > 1) { int m = (lo + hi) >>> 1; if (s[m] <= d) lo = m; else hi = m; }
            double seg = s[lo + 1] - s[lo];
            double u = seg < 1e-9 ? 0 : (d - s[lo]) / seg;
            o[0] = x[lo] + (x[lo + 1] - x[lo]) * u; o[1] = z[lo] + (z[lo + 1] - z[lo]) * u;
            // Richtung über ein kurzes Stück, damit Ecken weich drehen
            double d2 = d + 0.8, d1 = d - 0.8;
            double[] a = pt(d1), b = pt(d2);
            double hx = b[0] - a[0], hz = b[1] - a[1], l = Math.hypot(hx, hz);
            if (l < 1e-9) { hx = x[lo + 1] - x[lo]; hz = z[lo + 1] - z[lo]; l = Math.hypot(hx, hz); if (l < 1e-9) { hx = 1; l = 1; } }
            o[2] = hx / l; o[3] = hz / l;
        }

        private double[] pt(double d) {
            if (closed) { d %= total; if (d < 0) d += total; } else d = Math.max(0, Math.min(total, d));
            int lo = 0, hi = s.length - 1;
            while (hi - lo > 1) { int m = (lo + hi) >>> 1; if (s[m] <= d) lo = m; else hi = m; }
            double seg = s[lo + 1] - s[lo], u = seg < 1e-9 ? 0 : (d - s[lo]) / seg;
            return new double[]{x[lo] + (x[lo + 1] - x[lo]) * u, z[lo] + (z[lo + 1] - z[lo]) * u};
        }
    }

    static Path circle(double cx, double cz, double r, boolean ccw) {
        List<double[]> p = new ArrayList<>();
        int n = 72;
        for (int i = 0; i < n; i++) { double a = (ccw ? 1 : -1) * i * 2 * Math.PI / n; p.add(new double[]{cx + r * Math.cos(a), cz + r * Math.sin(a)}); }
        return new Path(p, true);
    }

    /** Abgerundetes Rechteck, im Uhrzeigersinn (von oben, x nach rechts, z nach unten) oder dagegen. */
    static Path roundRect(double x0, double z0, double x1, double z1, double r, boolean cw) {
        List<double[]> p = new ArrayList<>();
        double[][] cs = {{x0 + r, z0 + r, 180}, {x1 - r, z0 + r, 270}, {x1 - r, z1 - r, 0}, {x0 + r, z1 - r, 90}};
        for (double[] c : cs) {
            for (int i = 0; i <= 8; i++) {
                double a = Math.toRadians(c[2] + 90.0 * i / 8);
                p.add(new double[]{c[0] + r * Math.cos(a), c[1] + r * Math.sin(a)});
            }
        }
        if (!cw) java.util.Collections.reverse(p);
        return new Path(p, true);
    }

    /** Offener Weg über Stützpunkte, Ecken mit einem Bogen vom Halbmesser rad verrundet. */
    static Path rounded(double[][] pts, double rad) {
        List<double[]> out = new ArrayList<>();
        out.add(pts[0]);
        for (int i = 1; i < pts.length - 1; i++) {
            double[] pr = pts[i - 1], v = pts[i], nx = pts[i + 1];
            double l1 = Math.hypot(v[0] - pr[0], v[1] - pr[1]), l2 = Math.hypot(nx[0] - v[0], nx[1] - v[1]);
            double d = Math.min(rad, Math.min(l1, l2) * 0.5);
            double[] a = {v[0] + (pr[0] - v[0]) / l1 * d, v[1] + (pr[1] - v[1]) / l1 * d};
            double[] b = {v[0] + (nx[0] - v[0]) / l2 * d, v[1] + (nx[1] - v[1]) / l2 * d};
            for (int k = 0; k <= 10; k++) {
                double u = k / 10.0, w = 1 - u;
                out.add(new double[]{w * w * a[0] + 2 * w * u * v[0] + u * u * b[0], w * w * a[1] + 2 * w * u * v[1] + u * u * b[1]});
            }
        }
        out.add(pts[pts.length - 1]);
        return new Path(out, false);
    }

    static Path line(double x0, double z0, double x1, double z1) {
        List<double[]> p = new ArrayList<>();
        p.add(new double[]{x0, z0}); p.add(new double[]{x1, z1});
        return new Path(p, false);
    }

    // ------------------------------------------------------------ Mitwirkende

    /** Gruppen für Tageszeit und Dichte. */
    static final int STROLL = 0, GUARD = 1, COOP = 2, GUEST = 3, RIDE = 4, COACH = 5, CART = 6, COOP_IN = 7;

    public static final class Actor {
        public byte kind;
        public int cls;
        public Path path;
        public double s0, speed;
        public boolean ping, rev;
        public double fx, fz, fhx, fhz;      // fester Stand
        public boolean stand;
        float[] c, p;
        float scale = 1, variant, thresh, gaitMax = 1;
        double y;                     // letzte Bodenhöhe
        double stride = 1.5;
    }

    private final List<Actor> actors = new ArrayList<>();
    /** Kutsche und Karren: Wege, Haltepunkte. */
    private Path coachPath, cartPath;
    private double coachStop, cartStop;
    private static final double COACH_V = 2.7, COACH_WAIT = 32, COACH_PERIOD = 300, CART_V = 1.5, CART_WAIT = 14, CART_PERIOD = 210;

    // Farben (linear): Pfälzer Blau, Weiß, Karmesin, Grün, Ocker, Schwarz, Braun
    private static final float[] BLUE = {0.012f, 0.045f, 0.26f}, WHITE = {0.45f, 0.45f, 0.43f}, CRIM = {0.30f, 0.018f, 0.014f},
            GREEN = {0.02f, 0.11f, 0.035f}, OCHRE = {0.38f, 0.22f, 0.04f}, BLACK = {0.022f, 0.02f, 0.02f}, BROWN = {0.10f, 0.055f, 0.03f},
            PLUM = {0.11f, 0.02f, 0.10f}, GOLD = {0.55f, 0.37f, 0.07f}, GREY = {0.10f, 0.10f, 0.11f}, SKY = {0.04f, 0.14f, 0.30f},
            RUST = {0.25f, 0.07f, 0.03f};
    private static final float[][] JACKETS = {BLUE, CRIM, GREEN, BLACK, PLUM, BROWN, OCHRE, GREY};
    private static final float[][] DRESSES = {CRIM, BLUE, PLUM, GREEN, OCHRE, SKY, GOLD, WHITE};
    private static final float[][] COATS = {{0.14f, 0.055f, 0.025f}, {0.022f, 0.02f, 0.02f}, {0.36f, 0.36f, 0.37f}, {0.23f, 0.10f, 0.04f}, {0.075f, 0.04f, 0.025f}};

    /** Wege und Mitwirkende anlegen; so wird alles gleich, wann immer man es aufbaut. */
    public Life() {
        java.util.Random rnd = new java.util.Random(1619);
        int ji = 0, di = 0;
        // --- Hofstaat auf Ringen um die Hofmitte (8, 2): Paare, einzelne Herren, Dienerinnen
        Path r1 = circle(8, 2, 5.5, true), r2 = circle(8, 2, 9.5, false), r3 = circle(8, 2, 12.8, true);
        double[][] ringSpec = {
                // ring, Anteil des Rings, Tempo, Paar?
                {2, 0.00, 0.85, 1}, {2, 0.50, 0.80, 1}, {3, 0.15, 0.95, 1}, {3, 0.50, 0.90, 1}, {3, 0.82, 1.0, 0},
                {1, 0.10, 0.75, 1}, {1, 0.60, 0.70, 0}, {2, 0.28, 1.05, 0}, {2, 0.76, 0.95, 0}
        };
        int idx = 0;
        for (double[] rs : ringSpec) {
            Path pth = rs[0] == 1 ? r1 : rs[0] == 2 ? r2 : r3;
            double sp = rs[2];
            boolean pair = rs[3] > 0;
            if (pair) {
                actors.add(walker(Animals.MAN, STROLL, pth, rs[1] * pth.total, sp, JACKETS[ji++ % 8], JACKETS[(ji + 3) % 8], rnd, idx++));
                actors.add(walker(Animals.LADY, STROLL, pth, rs[1] * pth.total - 1.1, sp, DRESSES[di++ % 8], DRESSES[di % 8], rnd, idx++));
            } else {
                boolean lady = (idx & 1) == 1;
                actors.add(walker(lady ? Animals.LADY : Animals.MAN, STROLL, pth, rs[1] * pth.total, sp,
                        lady ? DRESSES[di++ % 8] : JACKETS[ji++ % 8], lady ? DRESSES[di % 8] : JACKETS[(ji + 3) % 8], rnd, idx++));
            }
        }
        // --- Brunnenhalle: Ring um die Halle
        Path rh = circle(-21, 5, 7.2, true);
        for (int k = 0; k < 3; k++) actors.add(walker(k == 1 ? Animals.LADY : Animals.MAN, STROLL, rh, k * rh.total / 3, 0.7, k == 1 ? DRESSES[2] : JACKETS[k + 1], k == 1 ? DRESSES[4] : JACKETS[k + 4], rnd, idx++));
        // --- Hofweg rundum: Diener und zwei Paare
        Path rl = roundRect(-31, -17.2, 31, 16.3, 5, true);
        Path rl2 = roundRect(-31, -17.2, 31, 16.3, 5, false);
        actors.add(walker(Animals.MAN, STROLL, rl, 30, 1.25, BROWN, BLACK, rnd, idx++));
        actors.add(walker(Animals.MAN, STROLL, rl2, 90, 1.3, GREY, BROWN, rnd, idx++));
        actors.add(walker(Animals.LADY, STROLL, rl, 130, 0.9, DRESSES[5], DRESSES[0], rnd, idx++));
        actors.add(walker(Animals.MAN, STROLL, rl, 131.2, 0.9, BLUE, BLACK, rnd, idx++));
        // --- Wachen
        actors.add(stander(Animals.GUARD, GUARD, -46.6, 0.7, 0, 1, BLUE, WHITE));
        actors.add(stander(Animals.GUARD, GUARD, -46.6, 3.5, 0, -1, BLUE, WHITE));
        actors.add(stander(Animals.GUARD, GUARD, -72, 0.9, 0, 1, BLUE, WHITE));
        actors.add(stander(Animals.GUARD, GUARD, -72, 3.1, 0, -1, BLUE, WHITE));
        actors.add(stander(Animals.GUARD, GUARD, 6.3, -20.5, 0, 1, BLUE, WHITE));
        actors.add(stander(Animals.GUARD, GUARD, 9.7, -20.5, 0, 1, BLUE, WHITE));
        Actor pg1 = walker(Animals.GUARD, GUARD, line(-26, -19.3, 30, -19.3), 0, 0.9, BLUE, WHITE, rnd, 0);
        pg1.ping = true; pg1.s0 = 20; actors.add(pg1);
        Actor pg2 = walker(Animals.GUARD, GUARD, line(-26, -19.3, 30, -19.3), 0, 0.9, BLUE, WHITE, rnd, 0);
        pg2.ping = true; pg2.s0 = 20 + 56; actors.add(pg2);
        // --- Küfer: zwei vor der Tür des Fassbaus, drei drinnen
        actors.add(stander(Animals.COOPER, COOP, 32.6, 15.4, -1, 0.3, BROWN, BROWN));
        actors.add(stander(Animals.COOPER, COOP, 31.8, 18.0, -0.6, -0.5, OCHRE, BROWN));
        actors.add(stander(Animals.COOPER, COOP_IN, 44.5, 11.8, 0, 1, BROWN, BROWN));
        actors.add(stander(Animals.COOPER, COOP_IN, 40.6, 25.0, 0, 1, OCHRE, BROWN));
        actors.add(stander(Animals.COOPER, COOP_IN, 37.4, 11.0, 0.5, 1, BROWN, BROWN));
        // --- Saalgäste am Abend
        actors.add(stander(Animals.MAN, GUEST, 5.9, -25.4, 1, 0, BLUE, BLACK));
        actors.add(stander(Animals.LADY, GUEST, 6.7, -25.4, -1, 0, DRESSES[0], DRESSES[0]));
        actors.add(stander(Animals.LADY, GUEST, 9.4, -27.6, 1, 0, DRESSES[3], DRESSES[2]));
        actors.add(stander(Animals.MAN, GUEST, 10.3, -27.6, -1, 0, CRIM, BLACK));
        actors.add(stander(Animals.MAN, GUEST, 6.6, -23.9, 0.3, -1, PLUM, BLACK));
        actors.add(stander(Animals.LADY, GUEST, 9.6, -23.8, -0.4, -1, DRESSES[5], DRESSES[1]));
        Actor g1 = walker(Animals.MAN, GUEST, line(5.7, -28.2, 5.7, -25.8), 0, 0.4, GREEN, BLACK, rnd, 0);
        g1.ping = true; actors.add(g1);
        Actor g2 = walker(Animals.LADY, GUEST, line(10.4, -25.8, 10.4, -28.2), 0, 0.4, DRESSES[6], DRESSES[4], rnd, 0);
        g2.ping = true; actors.add(g2);
        Actor lord = stander(Animals.LORD, GUEST, 6.5, -30.3, 1, 0.3, BLUE, BLACK);
        lord.scale = 1.04f; actors.add(lord);
        // --- Reiter auf dem Hofweg
        Path rr = roundRect(-31, -17.2, 31, 16.3, 5, true);
        float[][] jk = {BLUE, RUST, GREEN};
        for (int k = 0; k < 3; k++) {
            Actor a = new Actor();
            a.kind = Animals.RIDER; a.cls = RIDE; a.path = rr; a.s0 = k * rr.total / 3 + 12; a.speed = 3.0 + 0.2 * k; a.stride = 2.4;
            a.c = COATS[(k * 2 + 1) % 5]; a.p = jk[k]; a.scale = 1; a.variant = k == 1 ? 1 : 0; a.thresh = 0.15f + 0.1f * k;
            actors.add(a);
        }
        // --- Kutsche: von der Torbrücke durch das Tor, vor den Saalbau, einmal um den Hof und zurück
        coachPath = rounded(new double[][]{{-112, 2}, {-34, 2}, {-27, -1.8}, {0, -2.2}, {1.8, -15.4}, {10, -15.4}, {26, -15.0}, {28.5, -8}, {26, -2.2}, {-8, -2.2}, {-27, -1.8}, {-34, 2}, {-112, 2}}, 5.5);
        coachStop = nearest(coachPath, 5.0, -15.4);
        // --- Karren mit Fässern: durch das Tor, über den Hof zum Fassbau, dann eine Runde über den Hof zurück
        cartPath = rounded(new double[][]{{-112, 2}, {-31, 2}, {-29.5, 12.8}, {30, 12.8}, {31.5, 2}, {31, -8.5}, {-10, -8.5}, {-27, -1.8}, {-34, 2}, {-112, 2}}, 4.0);
        cartStop = nearest(cartPath, 26, 12.8);
        for (int i = 0; i < actors.size(); i++) actors.get(i).thresh = actors.get(i).thresh > 0 ? actors.get(i).thresh : (float) (0.02 + 0.98 * ((i * 0.6180339887) % 1.0));
    }

    /** Weglänge des Punktes auf dem Weg, der (x, z) am nächsten liegt (erster Treffer, wenn der Weg sich kreuzt). */
    private static double nearest(Path p, double x, double z) {
        double best = 0, bd = 1e9;
        double[] q = new double[4];
        for (double d = 0; d < p.total; d += 0.25) { p.at(d, q); double dd = Math.hypot(q[0] - x, q[1] - z); if (dd < bd) { bd = dd; best = d; } }
        return best;
    }

    private Actor walker(byte kind, int cls, Path pth, double s0, double speed, float[] c, float[] p, java.util.Random rnd, int idx) {
        Actor a = new Actor();
        a.kind = kind; a.cls = cls; a.path = pth; a.s0 = s0; a.speed = speed; a.c = c; a.p = p;
        a.scale = 0.96f + rnd.nextFloat() * 0.1f; a.variant = rnd.nextFloat();
        a.stride = kind == Animals.LADY ? 1.1 : 1.5;
        return a;
    }

    private Actor stander(byte kind, int cls, double x, double z, double hx, double hz, float[] c, float[] p) {
        Actor a = new Actor();
        a.kind = kind; a.cls = cls; a.stand = true; a.fx = x; a.fz = z;
        double l = Math.hypot(hx, hz);
        a.fhx = hx / l; a.fhz = hz / l; a.c = c; a.p = p; a.scale = 1; a.variant = ((int) (x * 7 + z * 3)) % 2;
        return a;
    }

    /** Boden unter den Füßen: Gelände oder fester Boden (Hofpflaster, Brücke, Saal), je nachdem was höher ist. */
    public void attach(Solids so, Terrain t) {
        this.solids = so; this.terrain = t;
        for (Actor a : actors) a.y = a.stand ? ground(a.fx, a.fz, interiorRef(a.fx, a.fz)) : 0;
    }

    private static double interiorRef(double x, double z) {
        return 0.6;
    }

    /** Bodenhöhe für die Prüfung. */
    public double groundAt(double x, double z, double yRef) { return ground(x, z, yRef); }

    private double ground(double x, double z, double yRef) {
        double h = terrain == null ? 0 : terrain.stand(x, z, -1e9);
        if (solids == null) return h;
        double t = solids.ray(x, yRef + 1.6, z, 0, -1, 0, 6);
        if (t >= 0) {
            double g = yRef + 1.6 - t;
            if (g > h - 0.05) return g;
        }
        return h;
    }

    // ------------------------------------------------------------ Tageszeit

    /** Ist die Gruppe zu dieser Stunde da (Stunde 0..24)? rain: Regen 0..1. */
    static boolean present(int cls, double hour, float rain, int day) {
        switch (cls) {
            case STROLL: return hour >= 7.5 && hour < 20.5 && rain < 0.5;
            case GUARD: return true;
            case COOP: return hour >= 6.5 && hour < 18.5;
            case COOP_IN: return hour >= 6.5 && hour < 19.0;
            case GUEST: return hour >= 17.2 || hour < 0.8;
            case RIDE: return hour >= 8.5 && hour < 18 && rain < 0.4;
            case COACH: return hour >= 8.0 && hour < 18.5 && rain < 0.7;
            case CART: return hour >= 6.5 && hour < 18.5 && rain < 0.7;
            default: return true;
        }
    }

    /** Umgebungslicht: in Fassbau und Saal gedämpft (die Kerzen kommen dazu). */
    static float ambAt(double x, double z) {
        if (x > 35.0 && x < 51 && z > 9 && z < 29) return 0.3f;
        if (x > 0.2 && x < 15.8 && z > -31.5 && z < -22.5) return 0.32f;
        return 1;
    }

    // ------------------------------------------------------------ Schritt

    private final double[] o = new double[4], o2 = new double[4];

    /** Pro Bild: Figuren und Vögel für Zeit t (Sekunden), Tag 0..364, Stunde 0..24 füllen. */
    public void update(Engine3D r, double t, int day, double hour, float rain, double camX, double camY, double camZ) {
        an.clear();
        if (!enabled) { r.animals = an; return; }
        if (ruin) { r.animals = an; if (birds) birds(r.sprites, t, day, hour, camX, camY, camZ); return; }
        float dens = crowd ? density : 0;
        for (int i = 0; i < actors.size(); i++) {
            Actor a = actors.get(i);
            if (a.cls == RIDE && !riders) continue;
            if (a.cls == STROLL || a.cls == COOP || a.cls == COOP_IN || a.cls == GUEST || a.cls == RIDE) {
                if (a.thresh > dens) continue;
            }
            if (!present(a.cls, hour, rain, day)) continue;
            place(a, t, camX, camZ);
        }
        if (dens > 0.1f && riders) {
            if (present(COACH, hour, rain, day)) coach(t, camX, camZ);
            if (present(CART, hour, rain, day)) cart(t, camX, camZ);
        }
        r.animals = an;
        if (birds) birds(r.sprites, t, day, hour, camX, camY, camZ);
    }

    private void place(Actor a, double t, double camX, double camZ) {
        double x, z, hx, hz, d;
        if (a.stand) { x = a.fx; z = a.fz; hx = a.fhx; hz = a.fhz; d = 0; }
        else {
            d = a.s0 + a.speed * t;
            double L = a.path.total;
            if (a.ping) {
                double m = d % (2 * L);
                boolean back = m >= L;
                a.path.at(back ? 2 * L - m : m, o);
                x = o[0]; z = o[1]; hx = back ? -o[2] : o[2]; hz = back ? -o[3] : o[3];
            } else {
                a.path.at(d, o);
                x = o[0]; z = o[1]; hx = o[2]; hz = o[3];
            }
        }
        double dx = x - camX, dz = z - camZ;
        if (dx * dx + dz * dz > 450 * 450) return;
        a.y = ground(x, z, a.y);
        int i = an.add(a.kind, x, a.y, z, hx, hz, a.scale);
        if (i < 0) return;
        an.cr[i] = a.c[0]; an.cg[i] = a.c[1]; an.cb[i] = a.c[2];
        an.pr[i] = a.p[0]; an.pg[i] = a.p[1]; an.pb[i] = a.p[2];
        an.step[i] = (float) (d * 2 * Math.PI / a.stride);
        an.gait[i] = a.stand ? 0 : (float) Math.min(1, a.speed / 1.3);
        an.variant[i] = a.variant;
        an.amb[i] = ambAt(x, z);
    }

    /** Ort der Kutsche entlang des Weges zur Zeit t: Weglänge des Wagens oder −1, wenn gerade nicht unterwegs. */
    private static double trainS(double t, double stop, double total, double v, double wait, double period) {
        double u = t % period;
        double t1 = stop / v;
        if (u < t1) return u * v;
        if (u < t1 + wait) return stop;
        double s = stop + (u - t1 - wait) * v;
        return s <= total ? s : -1;
    }

    private void coach(double t, double camX, double camZ) {
        // Der Wagen fährt, bis auch das letzte Pferd (6 m voraus) den Weg verlassen hat: Weglänge reicht bis total − 7
        double s = trainS(t, coachStop, coachPath.total - 7, COACH_V, COACH_WAIT, COACH_PERIOD);
        if (s < 0) return;
        double moving = Math.abs(s - coachStop) < 1e-6 ? 0 : 1;
        // Anfahren/Bremsen weich: nahe am Halt langsamer wäre schöner; hier konstant
        double travelled = s;
        train(coachPath, s, travelled, moving, Animals.COACH, new double[]{3.6, 3.95, 6.0, 6.35}, camX, camZ);
    }

    private void train(Path pth, double s, double travelled, double moving, byte bodyKind, double[] horseOff, double camX, double camZ) {
        // Wagen
        pth.at(s, o);
        double bx = o[0], bz = o[1];
        if ((bx - camX) * (bx - camX) + (bz - camZ) * (bz - camZ) > 450 * 450) return;
        double wy = ground(bx, bz, 0);
        int i = an.add(bodyKind, bx, wy, bz, o[2], o[3], 1);
        if (i >= 0) {
            float[] cc = bodyKind == Animals.COACH ? BLUE : OCHRE;
            an.cr[i] = cc[0]; an.cg[i] = cc[1]; an.cb[i] = cc[2];
            float[] pp = bodyKind == Animals.COACH ? GOLD : BROWN;
            an.pr[i] = bodyKind == Animals.COACH ? 0.55f : BROWN[0]; an.pg[i] = bodyKind == Animals.COACH ? 0.37f : BROWN[1]; an.pb[i] = bodyKind == Animals.COACH ? 0.07f : BROWN[2];
            an.step[i] = (float) (travelled / (bodyKind == Animals.COACH ? 0.62 : 0.55));
            an.gait[i] = (float) moving;
            an.amb[i] = 1;
        }
        for (int k = 0; k < horseOff.length; k++) {
            pth.at(s + horseOff[k], o2);
            double hy = ground(o2[0], o2[1], wy);
            int h = an.add(Animals.HORSE, o2[0], hy, o2[1], o2[2], o2[3], bodyKind == Animals.COACH ? 1.02 : 1.0);
            if (h < 0) continue;
            float[] cc = bodyKind == Animals.COACH ? COATS[k % 2 == 0 ? 2 : 1] : COATS[3 + (k & 1)];
            if (bodyKind == Animals.COACH && k >= 2) cc = COATS[k % 2 == 0 ? 2 : 1];
            an.cr[h] = cc[0]; an.cg[h] = cc[1]; an.cb[h] = cc[2];
            an.step[h] = (float) ((travelled + horseOff[k]) * 2 * Math.PI / 2.3) + (k & 1) * 0.5f;
            an.gait[h] = (float) moving;
            an.amb[h] = 1;
        }
    }

    private void cart(double t, double camX, double camZ) {
        double s = trainS(t, cartStop, cartPath.total - 4, CART_V, CART_WAIT, CART_PERIOD);
        if (s < 0) return;
        double moving = Math.abs(s - cartStop) < 1e-6 ? 0 : 1;
        train(cartPath, s, s, moving, Animals.CART, new double[]{2.9}, camX, camZ);
    }

    /** Zeit, zu der die Kutsche (Karren) gerade am Halt steht, für Standbilder. */
    public double tCoachStop() { return coachStop / COACH_V + 6; }
    public double tCartStop() { return cartStop / CART_V + 4; }

    /** Position des Gespanns für die Prüfung: Weg, Weglänge. */
    public Path coachPath() { return coachPath; }
    public Path cartPath() { return cartPath; }
    public List<Actor> actors() { return actors; }

    // ------------------------------------------------------------ Vögel

    private void birds(Sprites sp, double t, int day, double hour, double cx, double cy, double cz) {
        boolean daylight = hour > 5.5 && hour < 20.8;
        if (daylight) {
            // Tauben: ein Schwarm kreist über dem Hof
            for (int i = 0; i < 9; i++) {
                double w = 0.24, a = w * t + i * 0.13 + 0.4 * Math.sin(0.11 * t + i);
                double R = 36 + 4 * Math.sin(i * 1.7 + 0.05 * t);
                double x = 6 + R * Math.cos(a), z = -2 + R * 0.7 * Math.sin(a);
                double y = 30 + 3 * Math.sin(i * 1.3) + 1.2 * Math.sin(0.7 * t + i);
                bird(sp, x, y, z, -Math.sin(a) * R, Math.cos(a) * R * 0.7, 0.9, 0.20, 0.20, 0.21, 0.92, Math.sin(2 * Math.PI * 5.2 * t + i * 2.1), cx, cy, cz);
            }
            // Krähen: größere Kreise, Flattern und Gleiten im Wechsel
            int nCrows = (day < 60 || day > 300) ? 7 : 4;
            for (int i = 0; i < nCrows; i++) {
                double w = -0.14, a = w * t + i * 0.9;
                double R = 58 + 9 * Math.sin(i + 0.03 * t);
                double x = -12 + R * Math.cos(a), z = -4 + R * 0.8 * Math.sin(a);
                double y = 38 + 5 * Math.sin(i * 2.1 + 0.2 * t);
                double glide = 0.5 + 0.5 * Math.sin(0.5 * t + i * 1.3);
                bird(sp, x, y, z, -Math.sin(a) * R * -1, Math.cos(a) * R * 0.8 * -1, 1.3, 0.016, 0.015, 0.016, 0.95, Math.sin(2 * Math.PI * 3.0 * t + i) * (glide > 0.45 ? 1 : 0.15), cx, cy, cz);
            }
            // Mäusebussard: hoch am Hang über dem Schloss, ohne Flügelschlag
            if (hour > 9.5 && hour < 18 && day > 50 && day < 320) {
                double a = 0.06 * t;
                bird(sp, -34 + 140 * Math.cos(a), 150 + 10 * Math.sin(0.1 * t), 40 + 120 * Math.sin(a), -Math.sin(a), Math.cos(a), 1.7, 0.03, 0.022, 0.016, 0.95, 0.12 * Math.sin(0.6 * t), cx, cy, cz);
            }
        }
        // Schwalben (Sommer): schnelle Bögen um Türme und Dächer, am meisten am Abend
        boolean swift = day > 108 && day < 262 && hour > 5.0 && hour < 21.5;
        if (swift) {
            int n = (hour > 16.5 && hour < 21) ? 14 : 8;
            for (int i = 0; i < n; i++) {
                double ph = i * 1.37;
                double x = 4 + 52 * Math.sin(0.29 * t + ph) + 16 * Math.sin(0.71 * t + 2 * ph);
                double z = -3 + 34 * Math.cos(0.23 * t + ph * 0.7) + 8 * Math.sin(0.97 * t + ph);
                double y = 20 + 10 * Math.sin(0.41 * t + ph * 1.9) + 4 * Math.sin(1.3 * t + ph);
                double dt = 0.05;
                double x2 = 4 + 52 * Math.sin(0.29 * (t + dt) + ph) + 16 * Math.sin(0.71 * (t + dt) + 2 * ph);
                double z2 = -3 + 34 * Math.cos(0.23 * (t + dt) + ph * 0.7) + 8 * Math.sin(0.97 * (t + dt) + ph);
                bird(sp, x, y, z, x2 - x, z2 - z, 0.46, 0.014, 0.013, 0.015, 0.95, Math.sin(2 * Math.PI * 4.5 * t + ph), cx, cy, cz);
            }
        }
        // Fledermäuse in der Dämmerung
        boolean dusk = (hour > 19.2 && hour < 22.8) || (hour > 3.8 && hour < 5.6);
        if (dusk && day > 100 && day < 285) {
            for (int i = 0; i < 6; i++) {
                double ph = i * 2.3;
                double x = 6 + 24 * Math.sin(0.9 * t + ph) + 8 * Math.sin(2.1 * t + ph * 1.3);
                double z = -2 + 18 * Math.cos(0.7 * t + ph * 0.8) + 6 * Math.sin(1.9 * t + ph);
                double y = 11 + 3.5 * Math.sin(1.3 * t + ph) + 1.2 * Math.sin(3.7 * t + ph);
                double dt = 0.05;
                double x2 = 6 + 24 * Math.sin(0.9 * (t + dt) + ph) + 8 * Math.sin(2.1 * (t + dt) + ph * 1.3);
                double z2 = -2 + 18 * Math.cos(0.7 * (t + dt) + ph * 0.8) + 6 * Math.sin(1.9 * (t + dt) + ph);
                bird(sp, x, y, z, x2 - x, z2 - z, 0.38, 0.008, 0.008, 0.009, 0.95, Math.sin(2 * Math.PI * 6.0 * t + ph), cx, cy, cz);
            }
        }
    }

    private void bird(Sprites sp, double x, double y, double z, double hx, double hz, double span, double r, double g, double b, double al, double flap,
                      double cx, double cy, double cz) {
        double d2 = (x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz);
        if (d2 > 700 * 700) return;
        double l = Math.hypot(hx, hz);
        if (l < 1e-9) { hx = 1; hz = 0; l = 1; }
        int q = sp.add(Sprites.BIRD, x, y, z, span, r, g, b, al);
        sp.hx[q] = (float) (hx / l); sp.hz[q] = (float) (hz / l); sp.flap[q] = (float) flap;
    }
}
