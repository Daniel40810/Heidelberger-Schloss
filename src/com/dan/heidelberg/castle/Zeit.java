package com.dan.heidelberg.castle;

import com.dan.heidelberg.camera.CameraController;
import com.dan.heidelberg.camera.CameraPath;
import com.dan.heidelberg.camera.Director;
import com.dan.heidelberg.core.Engine3D;
import com.dan.heidelberg.core.Sprites;
import com.dan.heidelberg.core.Terrain;
import com.dan.heidelberg.effects.ParticleSystem;
import com.dan.heidelberg.effects.Weather;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Phase 9, Zeitraffer der Zerstörung: Das Schloss von 1619 über die Brandlegung vom 2. März 1689, die Sprengungen vom
 * 6. September 1693 und den Blitzschlag vom 24. Juni 1764 bis zur Ruine. Der Ablauf ist ein Drehbuch mit Zeitmarken: Er
 * schaltet Zeitstufe, Tageszeit und Wetter, legt Feuer, lässt Rauch steigen und Türme bersten und führt die Kamera.
 * <p>
 * Das Feuer entsteht aus den Dachfirsten und Turmspitzen von 1619 ({@link Castle#RIDGES}, {@link Castle#TOPS}). Jeder Punkt
 * zündet, wenn ihn die Flammenfront erreicht (Abstand vom Ausbruchsort durch Brandgeschwindigkeit), brennt eine Weile und
 * verlöscht. Jede Flamme ist ein Satz leuchtender Flecken, dazu steigt dunkler Rauch, und die nächsten Brände werfen
 * Licht auf die Mauern (Punktlichter). Wie schnell sich Feuer ausbreitet, ist ein Modell; die Daten stehen in den Quellen.
 */
public final class Zeit {
    /** Was der Zeitraffer an der Szene schalten darf (aufgerufen aus der Bildschleife). */
    public interface Hooks {
        CameraController camera();
        Terrain terrain();
        /** Zeitstufe wechseln (0 = 1619, 1 = nach 1689, 2 = nach 1693, 3 = nach 1764). */
        void stage(int s);
        void sun(int day, double hour);
        void weather(int mode);
        void strike(double x, double y, double z);
        void toast(String s, long ms);
        int day();
        double hour();
        int weatherMode();
    }

    /** Ein Brandherd: Ort, Größe, Zündzeit, Brenndauer. */
    static final class Site {
        double x, y, z, ig, dur, drop;
        float sc;
        /** Dach, das bis zur Stufe 3 steht (Notdach des Ottheinrichsbaus): kein Absinken in den Innenraum. */
        boolean keepRoof;
    }

    /** Ein Lichtblitz (Sprengung): Ort, Beginn, Dauer, Größe. */
    static final class Flare {
        double x, y, z, t0, dur, size;
        float r, g, b;
    }

    private static final class Ev {
        final double t;
        final Runnable run;
        Ev(double t, Runnable run) { this.t = t; this.run = run; }
    }

    private static final class Tafel {
        final double from, to;
        final String kopf, text, quelle;
        Tafel(double from, double to, String kopf, String text, String quelle) { this.from = from; this.to = to; this.kopf = kopf; this.text = text; this.quelle = quelle; }
    }

    /** Länge des Zeitraffers in Sekunden. */
    public static final double LENGTH = 104;
    /** Daten der vier Schauplätze (Tag im Jahr): 2. März 1689, 6. September 1693, 24. Juni 1764. */
    static final int D_1689 = 61, D_1693 = 249, D_1764 = 176;

    private final List<Site> sites = new ArrayList<>();
    private final List<Flare> flares = new ArrayList<>();
    private final List<Ev> events = new ArrayList<>();
    private final List<Tafel> tafeln = new ArrayList<>();
    private final Random rnd = new Random(1689);
    private Hooks h;
    private ParticleSystem psNow;
    private volatile boolean active, paused, finished;
    private double t;
    private int nextEv;
    private volatile Director.Card card;
    private volatile String status = "";
    private boolean lowered;
    private double smokeAcc;
    private double[][] fadeKeys, whiteKeys;
    private int sDay, sWeather;
    private double sHour;
    private int cancels;
    private int lastStage = -1;
    private final List<double[]> lapses = new ArrayList<>();
    private double lastSun = -9;
    private String year = "1619";
    /** Für Prüfungen: Zahl der Brandherde, die gerade brennen, und der Flammenflecken im Bild. */
    public volatile int burning, flameSprites;

    public boolean active() { return active; }
    public boolean paused() { return paused; }
    public double time() { return t; }
    public Director.Card card() { return active ? card : null; }
    public String status() { return active ? status : ""; }
    public int stageNow() { return lastStage; }
    /** Jahr der gerade gezeigten Zeit (für die Datumszeile). */
    public String year() { return year; }

    /** Abdunkeln (Schwarz) und Aufblitzen (Weiß) als Deckkraft 0..1. */
    public float fadeBlack() { return active ? (float) ramp(fadeKeys, t) : 0; }
    public float fadeWhite() { return active ? (float) ramp(whiteKeys, t) : 0; }

    // ------------------------------------------------------------ Ablauf

    /** Zeitraffer von vorn starten. */
    public void start(Hooks hooks) {
        h = hooks;
        sDay = h.day(); sHour = h.hour(); sWeather = h.weatherMode();
        sites.clear(); flares.clear(); events.clear(); tafeln.clear(); blasts.clear(); dusts.clear(); swapAt = -1; lapses.clear(); lastSun = -9;
        t = 0; nextEv = 0; lowered = false; finished = false; paused = false; smokeAcc = 0;
        script();
        active = true;
        year = "1619";
        h.weather(Weather.CLEAR);
        h.sun(172, 16.3);
        h.stage(0);
        lastStage = 0;
        CameraController c = h.camera();
        c.setRideSpeed(1);
        c.playRide(path(h.terrain()), 1.0, () -> finished = true);
        cancels = c.rideCancels;
        status = "Zeitraffer · 1619";
        h.toast("Zeitraffer: von 1619 bis zur Ruine", 2600);
    }

    /** Beendet den Zeitraffer; bei Abbruch kehren Tageszeit und Wetter von vorher zurück, die Zeitstufe bleibt. */
    public void stop(boolean cancelCamera) {
        if (!active) return;
        active = false;
        paused = false;
        card = null;
        status = "";
        sites.clear(); flares.clear();
        CameraController c = h.camera();
        if (c != null) { c.setRideSpeed(1); if (cancelCamera) c.cancelRide(); }
        h.weather(cancelCamera ? sWeather : Weather.CLEAR);
        if (cancelCamera) h.sun(sDay, sHour);
    }

    public void pause(boolean p) {
        if (!active) return;
        paused = p;
        CameraController c = h.camera();
        if (c != null) c.setRideSpeed(p ? 0 : 1);
    }

    private static double smooth(double a, double b, double x) {
        double u = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return u * u * (3 - 2 * u);
    }

    private static double ramp(double[][] k, double t) {
        if (k == null || k.length == 0) return 0;
        if (t <= k[0][0]) return k[0][1];
        for (int i = 1; i < k.length; i++) {
            if (t <= k[i][0]) {
                double u = (t - k[i - 1][0]) / Math.max(1e-6, k[i][0] - k[i - 1][0]);
                return k[i - 1][1] + (k[i][1] - k[i - 1][1]) * u;
            }
        }
        return k[k.length - 1][1];
    }

    private void ev(double at, Runnable r) { events.add(new Ev(at, r)); }

    private void card(double from, double to, String kopf, String text, String quelle) { tafeln.add(new Tafel(from, to, kopf, text, quelle)); }

    /** Das Drehbuch. */
    private void script() {
        final String WIKI = "Wikipedia: Heidelberger Schloss";
        // 0 bis 10: 1619
        card(1.5, 9.0, "Um 1619", "Das Schloss unter Kurfürst Friedrich V. mit dem Hortus Palatinus: so steht es in der Rekonstruktion. Von hier an läuft die Zeit vorwärts.", "Rekonstruktion (Modell)");
        // Schnitt 1: Abend des 2. März 1689
        ev(10.3, () -> { year = "1689"; h.sun(D_1689, 16.1); h.weather(Weather.CLOUDY); h.stage(0); lastStage = 0; status = "Zeitraffer · 2. März 1689"; });
        card(11.6, 19.5, "2. März 1689 · Brandlegung", "Beim Abzug aus der Stadt stecken französische Truppen unter Mélac das Schloss und die Stadt an vielen Ecken zugleich in Brand.", WIKI);
        ev(12.5, () -> brand1689(12.5));
        fadeKeys = new double[][]{{0, 0}, {9.2, 0}, {10.1, 1}, {10.6, 1}, {11.4, 0}, {28.0, 0}, {28.9, 1}, {29.5, 1}, {30.4, 0}, {42.0, 0}, {42.9, 1}, {43.4, 1}, {44.3, 0},
                {66.0, 0}, {66.9, 1}, {67.4, 1}, {68.3, 0}, {86.2, 0}, {87.0, 1}, {87.6, 1}, {88.6, 0}, {99.5, 0}, {100.4, 1}, {100.9, 1}, {101.8, 0}};
        // Schnitt 2: die Hitze der Nacht, das Haus ist ausgebrannt
        ev(29.1, () -> { h.sun(D_1689, 17.7); h.stage(1); lastStage = 1; lowered = true; status = "Zeitraffer · nach 1689"; });
        card(31.0, 40.0, "Nach dem Brand", "Die Mauern stehen noch, Dächer, Decken und Einbauten sind verbrannt. Der Wiederaufbau bleibt Stückwerk.", WIKI);
        // Schnitt 3: 6. September 1693
        ev(43.1, () -> { clearSmoke(); year = "1693"; sites.clear(); lowered = false; h.sun(D_1693, 15.4); h.weather(Weather.CLEAR); h.stage(1); lastStage = 1; status = "Zeitraffer · 6. September 1693"; });
        card(44.6, 55.0, "6. September 1693 · Sprengung", "Französische Truppen sprengen mit Minen die Türme und Mauern, die 1689 stehen geblieben waren.", WIKI);
        ev(49.0, () -> sprengung(49.0));
        whiteKeys = new double[][]{{0, 0}, {49.0, 0}, {49.12, 1}, {49.4, 0.55}, {50.6, 0}};
        card(57.0, 65.0, "Nach 1693", "Türme und Wälle sind gefallen. Ein Teil des Ottheinrichsbaus trägt im Modell noch ein Notdach, die übrigen Bauten stehen als Ruine.", "Modellannahme; Quelle: " + WIKI);
        // Schnitt 4: Nacht auf den 25. Juni 1764
        ev(67.1, () -> { clearSmoke(); year = "1764"; sites.clear(); lowered = true; h.sun(D_1764, 22.3); h.weather(Weather.STORM); h.stage(2); lastStage = 2; status = "Zeitraffer · 24. Juni 1764"; });
        card(68.8, 78.0, "24. Juni 1764 · Blitzschlag", "Ein Gewitter zieht auf. Der Blitz schlägt zweimal ein: Der Gläserne Saalbau und der Glockenturm brennen, das Feuer greift auf die Palastbauten über.", "schloss-heidelberg.de, Dossier „Doppelter Blitzeinschlag“");
        ev(71.0, () -> { h.strike(8, 19.5, -27); Klang.thunder(0.4); brand1764(71.1, 8, -27, 2.4, true); });
        ev(75.0, () -> { h.strike(-58, 26, -44); Klang.thunder(0.6); brand1764(75.1, -58, -44, 2.4, false); });
        ev(87.1, () -> { h.sun(D_1764 + 1, 5.2); h.weather(Weather.CLOUDY); h.stage(3); lastStage = 3; status = "Zeitraffer · nach 1764"; });
        card(89.5, 98.0, "Seit 1764", "Der Brand zerstört die Bauten bis auf die Außenmauern. Kurfürst Karl Theodor gibt die Wiederherstellung auf und verlegt 1777 seine Residenz nach München.", WIKI);
        ev(100.6, () -> { clearSmoke(); year = "heute"; sites.clear(); lowered = false; h.sun(172, 17.3); h.weather(Weather.CLEAR); h.stage(3); lastStage = 3; status = "Zeitraffer · heute"; });
        card(102.0, 103.8, "Heute", "Die Ruine über Heidelberg.", "Rekonstruktion (Modell)");
        // Die Uhr läuft im Zeitraffer weiter: Sonnenuntergang über dem Brand, Abend der Sprengung, Gewitternacht, Morgen danach
        lapses.add(new double[]{10.3, 28.9, D_1689, 16.1, 17.7});
        lapses.add(new double[]{29.1, 42.5, D_1689, 17.7, 18.4});
        lapses.add(new double[]{43.1, 66.5, D_1693, 15.4, 17.6});
        lapses.add(new double[]{67.1, 86.8, D_1764, 22.3, 23.4});
        lapses.add(new double[]{87.1, 99.8, D_1764 + 1, 5.0, 6.6});
        events.sort((a, b) -> Double.compare(a.t, b.t));
    }

    /** Die Kamerafahrt durch alle Zeiten, 104 s. */
    private CameraPath path(Terrain ter) {
        CameraPath p = new CameraPath().named("Zeitraffer der Zerstörung");
        double[][] k = {
                // t, Auge x, Höhe über Grund, Auge z, Ziel x, y, z
                {0, -330, 135, 380, 0, 15, 0},
                {10, -210, 80, 230, 0, 14, 0},
                {10.8, -150, 55, 175, 0, 18, 0},
                {19, -60, 50, 255, 0, 18, 0},
                {28, 80, 55, 190, 5, 18, 0},
                {36, 190, 55, 60, 0, 15, 0},
                {43, 185, 40, -90, 20, 15, 0},
                {49, 175, 55, 10, 25, 14, 0},
                {58, 130, 50, 120, 20, 12, 0},
                {66, 40, 55, 150, 5, 10, -20},
                {70, 30, 42, 125, 5, 10, -22},
                {80, -25, 48, 100, 0, 12, -25},
                {86.5, -60, 60, 70, 0, 12, -20},
                {89, -120, 50, 90, 0, 10, -10},
                {98, -170, 65, 200, -10, 10, 5},
                {104, -200, 80, 235, -20, 8, 30}};
        for (double[] q : k) {
            double y = ter.stand(q[1], q[3], 1e9) + q[2];
            p.add(q[0], q[1], y, q[3], q[4], q[5], q[6]);
        }
        return p;
    }

    // ------------------------------------------------------------ Brände

    private static boolean inBox(double[] r, double x0, double x1, double z0, double z1) {
        double mx = (r[0] + r[3]) / 2, mz = (r[2] + r[5]) / 2;
        return mx >= x0 && mx <= x1 && mz >= z0 && mz <= z1;
    }

    /** Feuer entlang aller Firste und auf den Turmspitzen, Ausbruchsorte über das Schloss verteilt. */
    private void brand1689(double t0) {
        double[][] origins = {{41, -9}, {13, 30}, {-52, 2}, {-39, -13}, {43, 19}, {-23, 31}, {8, -27}};
        sites.clear();
        for (double[] r : Castle.RIDGES) {
            double len = Math.hypot(r[3] - r[0], r[5] - r[2]);
            int n = Math.max(1, (int) Math.ceil(len / 2.6));
            for (int i = 0; i <= n; i++) {
                double u = i / (double) n;
                addSite(r[0] + (r[3] - r[0]) * u, r[1] - 0.25 * r[6], r[2] + (r[5] - r[2]) * u, origins, t0, 3.4, 2.6, 24, 30, 1, 11);
            }
        }
        for (double[] tp : Castle.TOPS) addSite(tp[0], tp[1] + 1, tp[2], origins, t0 + 2, 3.4, 3.6, 22, 26, 2, 5);
    }

    /** Blitz auf Saalbau und Glockenturm: das Feuer wandert über die Nordbauten und die Türme. */
    private void brand1764(double t0, double ox, double oz, double speed, boolean first) {
        double[][] origin = {{ox, oz}};
        for (double[] r : Castle.RIDGES) {
            if (!inBox(r, -48, 55, -48, -8) && !inBox(r, -50, -30, 5, 25)) continue;
            double len = Math.hypot(r[3] - r[0], r[5] - r[2]);
            int n = Math.max(1, (int) Math.ceil(len / 2.6));
            boolean notdach = inBox(r, -34, -1, -44, -14);
            for (int i = 0; i <= n; i++) {
                double u = i / (double) n;
                double x = r[0] + (r[3] - r[0]) * u, z = r[2] + (r[5] - r[2]) * u;
                Site s = addSite(x, r[1] - 0.25 * r[6], z, origin, t0, speed, 2.6, 22, 26, 5, 6);
                s.keepRoof = notdach;
            }
        }
        for (double[] tp : Castle.TOPS) if (Math.hypot(tp[0] - ox, tp[2] - oz) < 40) addSite(tp[0], tp[1] + 1, tp[2], origin, t0, speed, 3.6, 24, 26, 2, 5);
    }

    private Site addSite(double x, double y, double z, double[][] origins, double t0, double speed, double scale, double durMin, double durRange,
                         double dropMin, double dropRange) {
        double d = 1e9;
        for (double[] o : origins) d = Math.min(d, Math.hypot(x - o[0], z - o[1]));
        Site s = new Site();
        s.x = x + (rnd.nextDouble() - 0.5) * 1.2; s.z = z + (rnd.nextDouble() - 0.5) * 1.2; s.y = y;
        s.ig = t0 + d / speed + rnd.nextDouble() * 1.4;
        s.dur = durMin + rnd.nextDouble() * durRange * 0.5;
        s.sc = (float) (scale * (0.8 + 0.6 * rnd.nextDouble()));
        s.drop = dropMin + rnd.nextDouble() * dropRange;
        sites.add(s);
        return s;
    }

    private void clearSmoke() { if (psNow != null) psNow.clear(); }

    private boolean lowFlame(Site s) { return s.keepRoof ? lastStage >= 3 : lowered; }

    private double inten(Site s, double now) {
        double u = now - s.ig;
        if (u < 0) return 0;
        return smooth(0, 3.5, u) * (1 - smooth(s.dur - 7, s.dur, u));
    }

    // ------------------------------------------------------------ Sprengung

    private void sprengung(double t0) {
        double[][] bl = {{60, 22, -44, 1.7}, {68, 17, 48, 1.5}, {-58, 15, -44, 1.3}, {-58, 12, 36, 1.2}, {-52, 22, 2, 1.0}, {62, 6, 4, 0.9}};
        double[] dt = {0, 0.35, 0.7, 1.0, 1.4, 1.8};
        swapAt = t0 + 0.08;
        for (int i = 0; i < bl.length; i++) blasts.add(new double[]{t0 + dt[i], bl[i][0], bl[i][1], bl[i][2], bl[i][3]});
    }

    private final List<double[]> blasts = new ArrayList<>();
    /** Staubsäulen der Sprengungen: Beginn, x, y, z, Größe. */
    private final List<double[]> dusts = new ArrayList<>();
    /** Zeitpunkt, an dem das Netz der Sprengung eingeschaltet wird (unter dem weißen Blitz). */
    private double swapAt = -1;

    private void blast(ParticleSystem ps, double x, double y, double z, double size) {
        Flare f = new Flare();
        f.x = x; f.y = y; f.z = z; f.t0 = t; f.dur = 1.1; f.size = 16 * size; f.r = 6; f.g = 3.6f; f.b = 1.4f;
        flares.add(f);
        if (ps == null) return;
        int nm = (int) (70 * size), nsm = (int) (60 * size);
        dusts.add(new double[]{t, x, y - 6, z, size});
        for (int i = 0; i < nm && ps.n < ParticleSystem.CAP - 8000; i++) {
            double a = rnd.nextDouble() * 6.2832, up = 10 + rnd.nextDouble() * 24, hs = (4 + rnd.nextDouble() * 15) * size;
            ps.spawn(ParticleSystem.MUD, (float) x, (float) y, (float) z, (float) (Math.cos(a) * hs), (float) up, (float) (Math.sin(a) * hs),
                    (float) (0.5 + rnd.nextDouble() * 1.1), 9, 1f, -1e9f, -1);
        }
        for (int i = 0; i < nsm && ps.n < ParticleSystem.CAP - 6000; i++) {
            double a = rnd.nextDouble() * 6.2832, hs = (0.5 + rnd.nextDouble() * 5.5) * size, rr = Math.sqrt(rnd.nextDouble());
            int q = ps.spawn(ParticleSystem.SMOKE, (float) (x + Math.cos(a) * rr * 4 * size), (float) (y - 8 + rnd.nextDouble() * 12), (float) (z + Math.sin(a) * rr * 4 * size),
                    (float) (Math.cos(a) * hs), (float) (3 + rnd.nextDouble() * 10), (float) (Math.sin(a) * hs),
                    (float) ((0.9 + rnd.nextDouble() * 2.1) * size), 18 + rnd.nextFloat() * 10, 0.34f, -1e9f, -1);
            if (q >= 0) ps.grow[q] = (float) ((0.35 + 0.4 * rnd.nextDouble()) * size);
        }
        Klang.boom(0);
    }

    // ------------------------------------------------------------ Bildschleife

    /**
     * Einmal je Bild, nach {@code Torches.update} und {@code Life.update}: Zeitmarken abarbeiten, Flammen und Funkenlichter in
     * die Engine setzen, Rauch ausstoßen.
     */
    public void update(double dt, Engine3D r, ParticleSystem ps, double cx, double cy, double cz) {
        if (!active) return;
        CameraController c = h.camera();
        if (c.rideCancels != cancels) { stop(false); h.toast("Zeitraffer beendet", 2500); return; }
        if (finished) { stop(false); h.toast("Ende des Zeitraffers", 4000); return; }
        if (!paused) t += dt;
        psNow = ps;
        while (nextEv < events.size() && events.get(nextEv).t <= t) events.get(nextEv++).run.run();
        if (swapAt > 0 && t >= swapAt) { swapAt = -1; h.stage(2); lastStage = 2; }
        for (int i = blasts.size() - 1; i >= 0; i--) {
            double[] b = blasts.get(i);
            if (t >= b[0]) { blast(ps, b[1], b[2], b[3], b[4]); blasts.remove(i); }
        }
        Director.Card cd = null;
        for (Tafel f : tafeln) {
            if (t >= f.from && t <= f.to) {
                float a = (float) Math.max(0, Math.min(1, Math.min((t - f.from) / 0.8, (f.to - t) / 0.8)));
                cd = new Director.Card(f.kopf, f.text, f.quelle, a);
                break;
            }
        }
        card = cd;
        if (!paused && t - lastSun >= 0.25) {
            for (double[] l : lapses) {
                if (t >= l[0] && t <= l[1]) { lastSun = t; h.sun((int) l[2], l[3] + (l[4] - l[3]) * (t - l[0]) / (l[1] - l[0])); break; }
            }
        }
        fire(dt, r, ps, cx, cy, cz);
    }

    private void fire(double dt, Engine3D r, ParticleSystem ps, double cx, double cy, double cz) {
        Sprites sp = r.sprites;
        int nb = 0, nspr = 0;
        // Lichtblitze der Sprengungen
        for (int i = flares.size() - 1; i >= 0; i--) {
            Flare f = flares.get(i);
            double u = (t - f.t0) / f.dur;
            if (u >= 1) { flares.remove(i); continue; }
            double k = Math.exp(-3.2 * u) * (1 - u);
            sp.add(Sprites.GLOW, f.x, f.y, f.z, f.size * (0.5 + 0.8 * u), f.r * k, f.g * k, f.b * k, 0.9 * k);
            sp.add(Sprites.GLOW, f.x, f.y, f.z, f.size * 0.25, 8 * k, 6 * k, 3.5 * k, 0.95 * k);
        }
        if (ps != null && !dusts.isEmpty() && !paused) {
            for (int i = dusts.size() - 1; i >= 0; i--) {
                double[] d = dusts.get(i);
                double age = t - d[0];
                if (age > 16) { dusts.remove(i); continue; }
                double rate = 55 * d[4] * Math.exp(-age / 5.5);
                double pr = rate * dt;
                int k = (int) pr;
                if (ps.rand() < pr - k) k++;
                for (int q = 0; q < k && ps.n < ParticleSystem.CAP - 6000; q++) {
                    double a = rnd.nextDouble() * 6.2832, rr = Math.sqrt(rnd.nextDouble()) * 3.5 * d[4];
                    int j = ps.spawn(ParticleSystem.SMOKE, (float) (d[1] + Math.cos(a) * rr), (float) (d[2] + rnd.nextDouble() * 6), (float) (d[3] + Math.sin(a) * rr),
                            (float) (Math.cos(a) * 1.4), (float) (5 + rnd.nextDouble() * 8), (float) (Math.sin(a) * 1.4),
                            (float) ((1.3 + rnd.nextDouble() * 2.0) * d[4]), 15 + rnd.nextFloat() * 9, 0.30f, -1e9f, -1);
                    if (j >= 0) ps.grow[j] = (float) ((0.3 + 0.3 * rnd.nextDouble()) * d[4]);
                }
            }
        }
        if (sites.isEmpty()) { burning = 0; flameSprites = 0; return; }
        smokeAcc += dt;
        boolean smokeNow = smokeAcc > 0.12;
        double sdt = smokeAcc;
        if (smokeNow) smokeAcc = 0;
        // Lichter: Brände in 12-m-Zellen zusammenfassen, die stärksten und nächsten bis zu zehn
        java.util.HashMap<Long, double[]> cell = new java.util.HashMap<>();
        double tt = t;
        for (Site s : sites) {
            double in = inten(s, tt);
            if (in <= 0.01) continue;
            nb++;
            double y = s.y - (s.keepRoof ? (lastStage >= 3 ? s.drop : 0) : (lowered ? s.drop : 0));
            double dx = s.x - cx, dy = y - cy, dz = s.z - cz;
            double d2 = dx * dx + dy * dy + dz * dz;
            double fl = 0.85 + 0.15 * Math.sin(tt * 11 + s.x * 3.1 + s.z * 1.7) * Math.sin(tt * 5.3 + s.x);
            if (d2 < 900.0 * 900.0) {
                double sz = s.sc * in * fl;
                double sway = 0.5 * s.sc * Math.sin(tt * 6.5 + s.z * 1.3 + s.x);
                // Flammenfuß (breit, rot), Körper, Mitte und Spitze (klein, gelb), die Spitze flackert im Wind
                double hz = lowFlame(s) ? 1.3 : 1.0;
                sp.add(Sprites.GLOW, s.x, y + 0.4 * sz, s.z, (lowFlame(s) ? 0.85 : 1.15) * sz, 2.4 * fl, 0.70 * fl, 0.14 * fl, 0.80 * in);
                sp.add(Sprites.GLOW, s.x, y + 1.3 * sz, s.z, 0.80 * sz, 7.0 * fl, 3.2 * fl, 0.8 * fl, 0.95 * in);
                sp.add(Sprites.GLOW, s.x + 0.3 * sway, y + 2.5 * sz * hz, s.z, 0.62 * sz, 5.0 * fl, 1.9 * fl, 0.36 * fl, 0.85 * in);
                sp.add(Sprites.GLOW, s.x + 0.7 * sway, y + 3.8 * sz * hz, s.z + 0.2 * sway, 0.46 * sz, 3.2 * fl, 1.0 * fl, 0.2 * fl, 0.7 * in);
                sp.add(Sprites.GLOW, s.x + sway, y + 5.0 * sz * hz, s.z + 0.3 * sway, 0.30 * sz, 1.8 * fl, 0.5 * fl, 0.1 * fl, 0.5 * in);
                nspr += 5;
            }
            if (d2 < 260.0 * 260.0) {
                long key = ((long) Math.floor(s.x / 12) << 32) ^ (long) Math.floor(s.z / 12) ^ ((long) Math.floor(y / 12) << 20);
                double[] a = cell.get(key);
                if (a == null) { a = new double[5]; a[4] = 1e18; cell.put(key, a); }
                a[0] += s.x * in; a[1] += y * in; a[2] += s.z * in; a[3] += in; a[4] = Math.min(a[4], d2);
            }
            if (smokeNow && ps != null && ps.n < ParticleSystem.CAP - 7000 && in > 0.25) {
                // Rauch: schwer und dunkel, steigt über dem Dach und treibt mit dem Wind
                double thin = d2 > 400.0 * 400.0 ? 0.35 : 1.0;
                double pr = 0.42 * in * sdt * s.sc * thin;
                int k = (int) pr;
                if (ps.rand() < pr - k) k++;
                for (int q = 0; q < k; q++) {
                    int i = ps.spawn(ParticleSystem.SMOKE, (float) (s.x + (ps.rand() - 0.5) * 1.5), (float) (y + 1.2 * s.sc), (float) (s.z + (ps.rand() - 0.5) * 1.5),
                            0, 3.2f, 0, 1.1f * s.sc, 20 + 9 * ps.rand(), 0.7f, -1e9f, -1);
                    if (i >= 0) ps.grow[i] = 0.62f * s.sc;
                }
            }
        }
        burning = nb;
        flameSprites = nspr;
        // Punktlichter: bis zu zehn, nach Stärke durch Abstand geordnet
        List<double[]> ls = new ArrayList<>(cell.values());
        ls.sort((a, b) -> Double.compare(b[3] / (30 + Math.sqrt(Math.max(1, b[4]))), a[3] / (30 + Math.sqrt(Math.max(1, a[4])))));
        int n = Math.min(10, ls.size());
        if (n > 0) {
            float[] old = r.torches;
            int on = r.torchN;
            float[] nt = new float[(on + n) * 7];
            System.arraycopy(old, 0, nt, 0, on * 7);
            for (int i = 0; i < n; i++) {
                double[] a = ls.get(i);
                float w = (float) Math.min(3.2, a[3]);
                float k = (float) (24.0 * Math.pow(w, 0.7));
                int o = (on + i) * 7;
                nt[o] = (float) (a[0] / a[3]); nt[o + 1] = (float) (a[1] / a[3]) + 1.5f; nt[o + 2] = (float) (a[2] / a[3]);
                nt[o + 3] = k; nt[o + 4] = k * 0.42f; nt[o + 5] = k * 0.12f; nt[o + 6] = 48f + 14f * w;
            }
            r.torches = nt;
            r.torchN = on + n;
        }
    }
}
