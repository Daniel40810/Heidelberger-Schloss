package com.dan.heidelberg.tools;

import com.dan.heidelberg.core.Camera;
import com.dan.heidelberg.core.Engine3D;
import com.dan.heidelberg.core.Scene;
import com.dan.heidelberg.core.Thermal;
import com.dan.heidelberg.effects.Climate;
import com.dan.heidelberg.effects.DayNightCycle;
import com.dan.heidelberg.effects.ParticleSystem;
import com.dan.heidelberg.world.Heidelberg;
import com.dan.heidelberg.world.World;

import javax.imageio.ImageIO;
import java.io.File;

/**
 * Standbilder der Phase 9 ohne Fenster: Aufruf P9Render Ausgabeordner [Filter]. Jede Zeile der Tabelle ist eine Zeitstufe mit
 * Tag, Stunde und Kamera; Zusatzschalter folgen in den Schlüsselwörtern (fire, ...).
 */
public final class P9Render {
    public static void main(String[] a) throws Exception {
        File out = new File(a.length > 0 ? a[0] : "shots9");
        out.mkdirs();
        String only = a.length > 1 ? a[1] : null;
        World w = Heidelberg.build(false);
        Scene sc = w.scene;
        Engine3D r = new Engine3D(sc, 4096);
        r.riverFlow = w.neckar.flow;
        r.riverSurface = w.neckar.surface;
        ParticleSystem pss = new ParticleSystem();
        r.particles = pss;
        com.dan.heidelberg.core.Wetness.Set wets = com.dan.heidelberg.castle.Fountains.wetness(sc.terrain);
        r.wetness = wets;
        int W = Integer.getInteger("w", 1280), H = Integer.getInteger("h", 720);
        r.setSize(W, H);
        Object[][] shots = Shots9.table();
        for (Object[] s : shots) {
            String name = (String) s[0];
            if (only != null && !name.startsWith(only)) continue;
            int stage = (Integer) s[1];
            int day = (Integer) s[2];
            double hour = (Double) s[3];
            Scene scn = w.stageScene(stage);
            r.setScene(scn);
            r.setCut(name.startsWith("sn") ? com.dan.heidelberg.castle.Schnitte.get(scn.mesh, name.charAt(2) - '0') : null);
            r.dust = stage == 0 ? w.dust : new float[0][];
            DayNightCycle cyc = new DayNightCycle();
            cyc.set(day, hour);
            Thermal.setDay(day);
            Thermal.snow = (float) Climate.snow(day);
            Thermal.ambient = (float) Math.max(0, Climate.air(day, hour) + 4);
            r.day = day; r.hour = hour; r.sidereal = cyc.siderealDeg;
            r.setSky(cyc, 0.14);
            w.grove.setSeason(day, Thermal.snow);
            r.leaves = w.grove.quads;
            Camera c = new Camera();
            c.ex = (Double) s[4]; c.ez = (Double) s[6];
            c.ey = (Boolean) s[10] ? (Double) s[5] : sc.terrain.stand(c.ex, c.ez, 1e9) + (Double) s[5];
            c.fovY = Math.toRadians((Double) s[11]);
            c.lookAt((Double) s[7], (Double) s[8], (Double) s[9]);
            r.resetExposure();
            r.shower = new float[5];
            r.sprays = new float[0][];
            pss.clear(); r.sprites.clear();
            r.ripples = new float[0][];
            r.torches = new float[0]; r.torchN = 0;
            java.awt.image.BufferedImage img = null;
            com.dan.heidelberg.castle.Fountains fo = new com.dan.heidelberg.castle.Fountains();
            if (Boolean.getBoolean("falls") && stage == 0) {
                for (int i = 0; i < 360; i++) {
                    fo.emit(pss, c.ex, c.ez);
                    pss.step(com.dan.heidelberg.castle.Fountains.STEP, 3f, 2f, sc.terrain, wets);
                }
                r.sprays = fo.sprays(c.ex, c.ez, 0.1f, 0.1f);
            }
            com.dan.heidelberg.castle.Wasserweg.show = Boolean.getBoolean("beads");
            for (int i = 0; i < 6; i++) {
                w.sward.update(i * 0.1f, day, Thermal.snow, 0.35, r.windX, r.windZ, null, c.ex, c.ey, c.ez);
                r.foliage = w.sward.meadow.batch;
                w.roadways.update(i * 0.1f, 0.1, day, 0, Thermal.snow, 0, 0.35f, r.windX, r.windZ, null, 1, c.ex, c.ey, c.ez);
                r.roads = w.roadways.roads;
                w.neckar.update(0.1, 0.35, r.windX, r.windZ, c.ex, c.ez);
                r.floats = w.neckar.quads;
                r.sprites.clear();
                com.dan.heidelberg.castle.Wasserweg.beads(r.sprites, 2.0 + i * 0.1, c.ex, c.ey, c.ez);
                if (Boolean.getBoolean("falls") && stage == 0) {
                    for (int q = 0; q < 6; q++) { fo.emit(pss, c.ex, c.ez); pss.step(com.dan.heidelberg.castle.Fountains.STEP, 3f, 2f, sc.terrain, wets); }
                    r.sprays = fo.sprays(c.ex, c.ez, 0.1f, 0.1f);
                    r.ripples = fo.ripples(i * 0.1 - fo.clock());
                }
                long t0 = System.nanoTime();
                img = r.render(c, i * 0.1, 0.1);
                if (i == 5) System.out.printf("%s (Stufe %d): %.0f ms%n", name, stage, (System.nanoTime() - t0) / 1e6);
            }
            if (name.startsWith("lu")) {
                int mat = r.pickMat(W / 2, H / 2);
                com.dan.heidelberg.lupe.Stoffe.Stoff st = com.dan.heidelberg.lupe.Stoffe.fuer(mat);
                System.out.println("Lupe " + name + ": Mat " + mat + " -> " + (st == null ? "nichts" : st.titel));
                if (st != null) {
                    java.awt.image.BufferedImage o = new java.awt.image.BufferedImage(img.getWidth(), img.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
                    java.awt.Graphics2D g2 = o.createGraphics();
                    g2.drawImage(img, 0, 0, null);
                    com.dan.heidelberg.lupe.Lupe lu = new com.dan.heidelberg.lupe.Lupe();
                    lu.setActive(true);
                    lu.show(st, W / 2, H / 2, 0);
                    lu.select(Integer.getInteger("sel", 0), 3.0);
                    lu.paint(g2, W, H, 4.2);
                    g2.dispose();
                    img = o;
                }
            }
            ImageIO.write(img, "png", new File(out, name + ".png"));
        }
    }
}
