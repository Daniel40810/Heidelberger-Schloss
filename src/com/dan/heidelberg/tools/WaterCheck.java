package com.dan.heidelberg.tools;

import com.dan.heidelberg.castle.Fountains;
import com.dan.heidelberg.core.Camera;
import com.dan.heidelberg.core.Engine3D;
import com.dan.heidelberg.core.Scene;
import com.dan.heidelberg.core.Wetness;
import com.dan.heidelberg.effects.DayNightCycle;
import com.dan.heidelberg.effects.ParticleSystem;
import com.dan.heidelberg.world.Heidelberg;
import com.dan.heidelberg.world.World;

/**
 * Prüft Phase 5: Höhe jeder senkrechten Fontäne gegen den Sollwert und die Kosten der Tropfen im Bild.
 * Aufruf: WaterCheck  (Bericht auf der Konsole).
 */
public final class WaterCheck {
    public static void main(String[] a) throws Exception {
        World w = Heidelberg.build(false);
        Scene sc = w.scene;
        Fountains fo = new Fountains();
        System.out.print(fo.report());

        // 1. Höhe: 12 s ohne Wind, größte Höhe der Tropfen nahe jeder senkrechten Düse
        ParticleSystem ps = new ParticleSystem();
        Wetness.Set wets = Fountains.wetness(sc.terrain);
        double[][] nozzles = {{-70, 102, 4.95, 5.5}, {-118, 111, 8.0, 1.6}, {-70, 91.9, -1.62, 1.8}};
        double[] maxY = new double[nozzles.length];
        java.util.Arrays.fill(maxY, -1e9);
        for (int i = 0; i < 12 * 60; i++) {
            fo.emit(ps, -70, 90);
            ps.step(Fountains.STEP, 0, 0, sc.terrain, wets);
            for (int k = 0; k < ps.n; k++) {
                if (ps.kind[k] != ParticleSystem.DROP) continue;
                for (int q = 0; q < nozzles.length; q++) {
                    if (Math.abs(ps.x[k] - nozzles[q][0]) < 0.6 && Math.abs(ps.z[k] - nozzles[q][1]) < 0.6 && ps.y[k] > nozzles[q][2] + 0.3)
                        maxY[q] = Math.max(maxY[q], ps.y[k]);
                }
            }
            if (i == 6 * 60) System.out.printf("nach 6 s: %d Teilchen (Tropfen+Spritzer+Gischt)%n", ps.n);
        }
        String[] nm = {"Achteck Mittelstrahl", "Säulenbrunnen Sprudel", "Grottenbecken Springbrunnen"};
        for (int q = 0; q < nozzles.length; q++)
            System.out.printf("%s: Soll %.2f m, gemessen %.2f m (Abweichung %.0f cm)%n", nm[q], nozzles[q][3], maxY[q] - nozzles[q][2], 100 * (maxY[q] - nozzles[q][2] - nozzles[q][3]));
        // mit Wind 4 m/s
        ps.clear();
        double my = -1e9;
        for (int i = 0; i < 8 * 60; i++) {
            fo.emit(ps, -70, 90);
            ps.step(Fountains.STEP, 4, 2, sc.terrain, wets);
            for (int k = 0; k < ps.n; k++) if (ps.kind[k] == ParticleSystem.DROP && ps.y[k] > my && Math.hypot(ps.x[k] + 70, ps.z[k] - 102) < 4) my = ps.y[k];
        }
        System.out.printf("Achteck Mittelstrahl bei Wind 4 m/s: Scheitel %.2f m über der Düse%n", my - 4.95);

        // 2. Kosten: Bild mit Fontänen, Dichte 1 und 8
        Engine3D r = new Engine3D(sc, 4096);
        r.riverFlow = w.neckar.flow; r.riverSurface = w.neckar.surface; r.dust = w.dust;
        r.particles = ps; r.wetness = wets;
        r.setSize(1280, 720);
        DayNightCycle cyc = new DayNightCycle();
        cyc.set(172, 16.5);
        r.day = 172; r.hour = 16.5; r.sidereal = cyc.siderealDeg;
        r.setSky(cyc, 0.12);
        Camera c = new Camera();
        c.ex = -84; c.ez = 112; c.ey = sc.terrain.sample(c.ex, c.ez) + 2.6;
        c.lookAt(-70, 7, 102);
        for (float dens : new float[]{0f, 1f, 4f, 8f}) {
            ps.clear();
            fo.enabled = dens > 0;
            fo.density = Math.max(dens, 0.05f);
            for (int i = 0; i < 10 * 60; i++) { fo.emit(ps, -70, 90); ps.step(Fountains.STEP, 0, 0, sc.terrain, wets); }
            r.ripples = fo.ripples(0 - fo.clock());
            double best = 1e9, sum = 0;
            int n = ps.n;
            for (int i = 0; i < 6; i++) {
                long t0 = System.nanoTime();
                r.render(c, i * 0.1, 0.1);
                double ms = (System.nanoTime() - t0) / 1e6;
                if (i >= 2) { best = Math.min(best, ms); sum += ms; }
            }
            long s0 = System.nanoTime();
            for (int i = 0; i < 60; i++) { fo.emit(ps, -70, 90); ps.step(Fountains.STEP, 0, 0, sc.terrain, wets); }
            double stepMs = (System.nanoTime() - s0) / 1e6 / 60;
            System.out.printf("Dichte %.0f: %d Teilchen, Bild %.0f ms (bestes), %.0f ms (Mittel), gezeichnet %d; Rechenschritt %.2f ms%n", dens, n, best, sum / 4, r.drawnParticles, stepMs);
        }
    }
}
