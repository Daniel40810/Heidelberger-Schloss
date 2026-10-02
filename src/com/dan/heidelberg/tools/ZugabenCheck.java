package com.dan.heidelberg.tools;

import com.dan.heidelberg.castle.Schnitte;
import com.dan.heidelberg.castle.Wasserorgel;
import com.dan.heidelberg.castle.Wasserweg;
import com.dan.heidelberg.core.Camera;
import com.dan.heidelberg.core.Engine3D;
import com.dan.heidelberg.core.Scene;
import com.dan.heidelberg.core.Thermal;
import com.dan.heidelberg.effects.Climate;
import com.dan.heidelberg.effects.DayNightCycle;
import com.dan.heidelberg.effects.ParticleSystem;
import com.dan.heidelberg.world.Heidelberg;
import com.dan.heidelberg.world.World;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/** Misst, was jede Zugabe der Phase 9 kostet: Bildzeit mit und ohne Wasserweg und Schnitt, Lupe, Orgelklang. Aufruf ohne Argumente. */
public final class ZugabenCheck {
    public static void main(String[] a) throws Exception {
        World w = Heidelberg.build(false);
        Scene sc = w.scene;
        Engine3D r = new Engine3D(sc, 4096);
        r.riverFlow = w.neckar.flow;
        r.riverSurface = w.neckar.surface;
        ParticleSystem pss = new ParticleSystem();
        r.particles = pss;
        r.wetness = com.dan.heidelberg.castle.Fountains.wetness(sc.terrain);
        int W = Integer.getInteger("w", 1280), H = Integer.getInteger("h", 720);
        r.setSize(W, H);
        DayNightCycle cyc = new DayNightCycle();
        cyc.set(172, 15.5);
        Thermal.setDay(172); Thermal.snow = 0; Thermal.ambient = 22;
        r.day = 172; r.hour = 15.5; r.sidereal = cyc.siderealDeg;
        r.setSky(cyc, 0.14);
        w.grove.setSeason(172, 0);
        r.leaves = w.grove.quads;
        double[][] views = {
                {20, 3.0, 8, -17, 8, -29},          // Hof
                {-40, 38, 190, -92, 3, 120},        // Hortus von oben
                {-134, 7, 96, -70, 0.5, 96}};       // Blick auf die Schnittebene 1
        String[] names = {"Hof", "Hortus von oben", "Westblick (Ebene 1)"};
        for (int vi = 0; vi < views.length; vi++) {
            double[] v = views[vi];
            Camera c = new Camera();
            c.ex = v[0]; c.ey = vi == 2 ? v[1] : (vi == 0 ? sc.terrain.stand(v[0], v[2], 1e9) + v[1] : v[1]); c.ez = v[2];
            c.fovY = Math.toRadians(60);
            c.lookAt(v[3], v[4], v[5]);
            System.out.println("Ansicht: " + names[vi]);
            Wasserweg.show = false;
            r.setCut(null);
            System.out.printf("  Zugaben aus:               %6.0f ms%n", measure(r, w, c, false));
            Wasserweg.show = true;
            System.out.printf("  Wasserweg-Perlen an:       %6.0f ms%n", measure(r, w, c, true));
            Wasserweg.show = false;
            r.setCut(Schnitte.get(sc.mesh, 0));
            System.out.printf("  Schnitt Ebene 1 an:        %6.0f ms%n", measure(r, w, c, false));
            r.setCut(Schnitte.get(sc.mesh, 1));
            System.out.printf("  Schnitt Ebene 2 an:        %6.0f ms%n", measure(r, w, c, false));
            r.setCut(null);
        }
        // Lupe
        com.dan.heidelberg.lupe.Lupe lu = new com.dan.heidelberg.lupe.Lupe();
        lu.setActive(true);
        lu.show(com.dan.heidelberg.lupe.Stoffe.fuer(24), 600, 300, 0);
        BufferedImage bi = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bi.createGraphics();
        for (int i = 0; i < 20; i++) lu.paint(g, W, H, i * 0.04);
        long t0 = System.nanoTime();
        for (int i = 0; i < 100; i++) lu.paint(g, W, H, 1 + i * 0.04);
        System.out.printf("Stein-Lupe zeichnen:         %6.2f ms je Bild (nur wenn eingeschaltet und angeklickt)%n", (System.nanoTime() - t0) / 1e8);
        // Orgelklang: Rechenzeit je Sekunde Ton
        Wasserorgel.render(2);
        t0 = System.nanoTime();
        Wasserorgel.render(20);
        System.out.printf("Wasserorgel synthetisieren:  %6.1f ms je Sekunde Ton (auf dem Klangthread, nur in Hörweite)%n", (System.nanoTime() - t0) / 1e6 / 20);
    }

    static double measure(Engine3D r, World w, Camera c, boolean beads) {
        double[] ms = new double[14];
        for (int i = 0; i < ms.length + 4; i++) {
            r.sprites.clear();
            if (beads) Wasserweg.beads(r.sprites, 2 + i * 0.1, c.ex, c.ey, c.ez);
            long t0 = System.nanoTime();
            r.render(c, i * 0.1, 0.1);
            if (i >= 4) ms[i - 4] = (System.nanoTime() - t0) / 1e6;
        }
        Arrays.sort(ms);
        return ms[ms.length / 2];
    }
}
