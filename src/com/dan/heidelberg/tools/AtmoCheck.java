package com.dan.heidelberg.tools;

import com.dan.heidelberg.castle.Fountains;
import com.dan.heidelberg.castle.Smoke;
import com.dan.heidelberg.core.Camera;
import com.dan.heidelberg.core.Engine3D;
import com.dan.heidelberg.core.Scene;
import com.dan.heidelberg.core.Thermal;
import com.dan.heidelberg.effects.Climate;
import com.dan.heidelberg.effects.DayNightCycle;
import com.dan.heidelberg.effects.ParticleSystem;
import com.dan.heidelberg.effects.ValleyFog;
import com.dan.heidelberg.world.Heidelberg;
import com.dan.heidelberg.world.World;

/**
 * Prüft Phase 6 in Zahlen: Wann gibt der Nebel das Schloss frei (Sichtbarkeit des Hofes im Verlauf des Vormittags),
 * wie viel kosten Talnebel, Fackeln und Regenschauer je Bild, wie viele Rauchteilchen laufen.
 */
public final class AtmoCheck {
    static World w;
    static Engine3D r;
    static ParticleSystem ps = new ParticleSystem();
    static ValleyFog vf = new ValleyFog();
    static Smoke sm = new Smoke();

    static void sky(int day, double hour, boolean fogForced) {
        DayNightCycle cyc = new DayNightCycle();
        cyc.set(day, hour);
        Thermal.setDay(day);
        Thermal.snow = (float) Climate.snow(day);
        Thermal.ambient = (float) Math.max(0, Climate.air(day, hour) + 4);
        r.day = day; r.hour = hour; r.sidereal = cyc.siderealDeg;
        r.setSky(cyc, 0.12);
        vf.forced = fogForced;
        vf.update(day, hour, cyc.elevationDeg, 0, 0, 3);
        r.fogTop = vf.top; r.fogAmt = vf.amount; r.fogBurn = vf.burn;
        w.grove.setSeason(day, Thermal.snow);
        r.leaves = w.grove.quads;
    }

    static double[] time(Camera c, int frames) {
        double best = 1e9, sum = 0;
        for (int i = 0; i < frames; i++) {
            long t0 = System.nanoTime();
            r.render(c, i * 0.1, 0.1);
            double ms = (System.nanoTime() - t0) / 1e6;
            best = Math.min(best, ms); sum += ms;
        }
        return new double[]{best, sum / frames};
    }

    static Camera cam(double x, double up, double z, double tx, double ty, double tz, boolean inside) {
        Camera c = new Camera();
        c.ex = x; c.ez = z;
        c.ey = w.scene.terrain.stand(x, z, inside ? -1e9 : 1e9) + up;
        c.lookAt(tx, ty, tz);
        return c;
    }

    /** Mittlere Helligkeit des Bildes in einem Rechteck (Anteile 0..1). */
    static double lum(java.awt.image.BufferedImage img, double fx0, double fy0, double fx1, double fy1) {
        int x0 = (int) (fx0 * img.getWidth()), x1 = (int) (fx1 * img.getWidth()), y0 = (int) (fy0 * img.getHeight()), y1 = (int) (fy1 * img.getHeight());
        double s = 0; int n = 0;
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
            int p = img.getRGB(x, y);
            s += 0.3 * ((p >> 16) & 255) + 0.59 * ((p >> 8) & 255) + 0.11 * (p & 255); n++;
        }
        return s / n;
    }

    /**
     * Sichtbarkeit des Schlosses im Nebel: Kontrast des Hofes (Rechteck um das Schloss) zu einem Bild ohne Nebel
     * derselben Zeit, 1 = so klar wie ohne Nebel.
     */
    static double clarity(Camera c, int day, double hour) {
        sky(day, hour, true);
        java.awt.image.BufferedImage a = null;
        for (int i = 0; i < 3; i++) a = r.render(c, i * 0.1, 0.1);
        double[] ca = contrast(a);
        sky(day, hour, false);
        java.awt.image.BufferedImage b = null;
        for (int i = 0; i < 3; i++) b = r.render(c, i * 0.1, 0.1);
        double[] cb = contrast(b);
        return ca[0] / Math.max(1e-6, cb[0]);
    }

    /** Standardabweichung der Helligkeit im Rechteck um das Schloss (Projektion des Hofes ± 150 × 70 Pixel). */
    static double[] contrast(java.awt.image.BufferedImage img) {
        double[] p = r.project(0, 15, 0);
        int cx = p == null ? img.getWidth() / 2 : (int) p[0], cy = p == null ? img.getHeight() / 2 : (int) p[1];
        int x0 = Math.max(0, cx - 150), x1 = Math.min(img.getWidth(), cx + 150), y0 = Math.max(0, cy - 70), y1 = Math.min(img.getHeight(), cy + 70);
        double s = 0, s2 = 0; int n = 0;
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
            int q = img.getRGB(x, y);
            double l = 0.3 * ((q >> 16) & 255) + 0.59 * ((q >> 8) & 255) + 0.11 * (q & 255);
            s += l; s2 += l * l; n++;
        }
        double m = s / n;
        return new double[]{Math.sqrt(Math.max(0, s2 / n - m * m)), m};
    }

    public static void main(String[] a) throws Exception {
        w = Heidelberg.build(false);
        Scene sc = w.scene;
        r = new Engine3D(sc, 4096);
        r.riverFlow = w.neckar.flow;
        r.riverSurface = w.neckar.surface;
        r.particles = ps;
        r.wetness = Fountains.wetness(sc.terrain);
        r.dust = w.dust;
        r.setSize(1280, 720);
        sm.attach(com.dan.heidelberg.castle.Castle.CHIMNEYS, w.townChimneys);
        System.out.printf("Schornsteine: %d im Schloss, %d in der Stadt%n", com.dan.heidelberg.castle.Castle.CHIMNEYS.size(), w.townChimneys.size());

        // 1. Klarheit des Schlosses im Verlauf des Vormittags (Tag 285, Blick aus dem Tal)
        Camera cv = cam(-450, 20, -330, 0, 10, 0, false);
        Camera cvTop = cam(-430, 125, -300, 0, 10, 0, false);
        System.out.println("Nebel am 12. Oktober, Blick vom Talboden hinauf zum Schloss (Kontrast des Schlosses gegenüber klarer Luft, 100 % = frei):");
        for (double h : new double[]{7.0, 7.6, 8.0, 8.4, 8.8, 9.2, 9.6, 10.0, 10.5, 11.0}) {
            DayNightCycle c = new DayNightCycle(); c.set(285, h);
            System.out.printf("  %4.1f Uhr, Sonne %5.1f°: Schloss zu %3.0f %% sichtbar%n", h, c.elevationDeg, 100 * Math.min(1, clarity(cv, 285, h)));
        }

        // 2. Kosten
        System.out.println("Bildzeit (1280 × 720, 2 Kerne), je 6 Bilder: beste / mittlere ms");
        sky(285, 8.4, false);
        double[] t0 = time(cvTop, 6);
        sky(285, 8.4, true);
        double[] t1 = time(cvTop, 6);
        System.out.printf("  Tal, 8,4 Uhr ohne Nebel: %.0f / %.0f;  mit Talnebel: %.0f / %.0f%n", t0[0], t0[1], t1[0], t1[1]);

        Camera ch = cam(-30, 1.7, 10, 25, 3, 0, false);
        sky(285, 19.8, false);
        w.torches.enabled = false; r.torchN = 0; r.lamps = false;
        double[] n0 = time(ch, 6);
        w.torches.enabled = true; r.lamps = true;
        for (int i = 0; i < 3; i++) { w.torches.update(r, ps, i * 0.1, 0.1, ch.ex, ch.ey, ch.ez, r.night()); r.render(ch, i * 0.1, 0.1); }
        w.torches.update(r, ps, 0.5, 0.1, ch.ex, ch.ey, ch.ez, r.night());
        double[] n1 = time(ch, 6);
        System.out.printf("  Hof 19,8 Uhr ohne Fackeln: %.0f / %.0f;  mit %d Fackellichtern und Fensterlicht: %.0f / %.0f%n", n0[0], n0[1], r.torchN, n1[0], n1[1]);

        Camera cb = cam(-60, 30, 60, 800, 120, 100, false);
        sky(150, 18.9, false);
        r.shower = new float[5];
        double[] s0 = time(cb, 6);
        DayNightCycle c = new DayNightCycle(); c.set(150, 18.9);
        double sx = -c.dir[0], sz = -c.dir[2], sl = Math.hypot(sx, sz);
        r.shower = new float[]{(float) (cb.ex + sx / sl * 1250), (float) (cb.ez + sz / sl * 1250), 800f, 0.0032f, 620f};
        double[] s1 = time(cb, 6);
        System.out.printf("  Blick nach Osten ohne Schauer: %.0f / %.0f;  mit Regenschauer und Regenbogen: %.0f / %.0f%n", s0[0], s0[1], s1[0], s1[1]);

        // 3. Rauch
        ps.clear();
        for (int i = 0; i < 25 * 60; i++) { sm.emit(ps, 1 / 60.0, 0, 0, 5); ps.step(Fountains.STEP, 0.4f, 0.2f, sc.terrain, r.wetness); }
        System.out.printf("Rauch nach 25 s bei 5 °C im Schlosshof: %d Teilchen%n", ps.n);
        ps.clear();
        for (int i = 0; i < 25 * 60; i++) { sm.emit(ps, 1 / 60.0, -470, -320, 5); ps.step(Fountains.STEP, 0.4f, 0.2f, sc.terrain, r.wetness); }
        System.out.printf("Rauch nach 25 s bei 5 °C über der Altstadt (70 nächste Schornsteine): %d Teilchen%n", ps.n);
        System.out.println("Wahrscheinlichkeit eines Nebeltags: Oktober " + ValleyFog.P[9] + ", Januar " + ValleyFog.P[0] + ", Juli " + ValleyFog.P[6]);
        int cnt = 0;
        for (int d = 1; d <= 365; d++) if (ValleyFog.isFogDay(d)) cnt++;
        System.out.println("Nebeltage im Modelljahr: " + cnt + " von 365");
    }
}
