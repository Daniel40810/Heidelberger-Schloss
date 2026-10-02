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
 * Rechnet einzelne Bilder ohne Fenster (für Prüfungen und die Statusbilder der Phasen). Aufruf:
 * StillRender Ausgabeordner [Tag Stunde] ; ohne Angaben die Standardreihe.
 */
public final class StillRender {
    /** Prüft den Regenbogen: Farbe entlang der Senkrechten durch den Gegenpunkt der Sonne, 34° bis 56°. */
    static void bowCheck(Engine3D r, DayNightCycle cyc, Camera c, java.awt.image.BufferedImage img) {
        double ax = -cyc.dir[0], ay = -cyc.dir[1], az = -cyc.dir[2];
        double hl = Math.hypot(ax, az), hx = ax / hl, hz = az / hl, ea = Math.asin(ay);
        double bestRB = -1e9, bestBR = -1e9, thRB = 0, thBR = 0, bestL = -1;
        double thL = 0;
        System.out.println("Gegenpunkt der Sonne: Höhe " + String.format("%.1f", Math.toDegrees(ea)) + "°, Richtung " + String.format("%.0f", (Math.toDegrees(Math.atan2(ax, -az)) + 360) % 360) + "°");
        for (double th = 34; th <= 56; th += 0.5) {
            double el = ea + Math.toRadians(th);
            double dx = hx * Math.cos(el), dy = Math.sin(el), dz = hz * Math.cos(el);
            double[] p = r.project(c.ex + dx * 3000, c.ey + dy * 3000, c.ez + dz * 3000);
            if (p == null) continue;
            int px = (int) p[0], py = (int) p[1];
            if (px < 2 || py < 2 || px >= img.getWidth() - 2 || py >= img.getHeight() - 2) continue;
            double R = 0, G = 0, B = 0;
            for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) {
                int rgb = img.getRGB(px + i, py + j);
                R += (rgb >> 16) & 255; G += (rgb >> 8) & 255; B += rgb & 255;
            }
            R /= 9; G /= 9; B /= 9;
            double lum = 0.3 * R + 0.59 * G + 0.11 * B;
            System.out.printf("  %.1f°: R %.0f G %.0f B %.0f%n", th, R, G, B);
            if (R - B > bestRB) { bestRB = R - B; thRB = th; }
            if (th < 45 && B - R > bestBR) { bestBR = B - R; thBR = th; }
            if (lum > bestL) { bestL = lum; thL = th; }
        }
        System.out.printf("Hauptbogen: rötester Punkt bei %.1f° (Soll 42,2°), bläulichster bei %.1f° (Soll 40,8°)%n", thRB, thBR);
    }

    public static void main(String[] a) throws Exception {
        File out = new File(a.length > 0 ? a[0] : "docs/bilder");
        out.mkdirs();
        boolean probe = a.length > 1 && a[1].startsWith("probe");
        java.util.Set<String> castleOnly = java.util.Set.of("schloss_hof", "schloss_hof2", "schloss_tal", "schloss_luft", "hortus_haupt", "hortus_grotte", "hortus_luft", "grotte_innen", "grotte_drin", "grotte_tor", "schloss_innen", "wasser_achteck", "wasser_achteck_nah", "wasser_saeule", "wasser_kaskade", "wasser_rhenus", "wasser_klein", "wasser_luft", "neckar_nah", "nebel_a", "nebel_hof", "fackel_hof", "fackel_hortus", "fackel_tal", "rauch_tal", "regen_bogen", "gischt_bogen", "abend_hof", "abend_tal", "morgen_a", "abend_west", "schnee_luft", "wappen_friedrich", "wappen_friedrich_nah", "wappen_otto", "wappen_torturm", "wappen_torturm_hof");
        World w = Heidelberg.build(probe);
        Scene sc = w.scene;
        Engine3D r = new Engine3D(sc, 4096);
        r.riverFlow = w.neckar.flow;
        r.riverSurface = w.neckar.surface;
        ParticleSystem pss = new ParticleSystem();
        com.dan.heidelberg.effects.ValleyFog vf = new com.dan.heidelberg.effects.ValleyFog();
        com.dan.heidelberg.castle.Smoke sm = new com.dan.heidelberg.castle.Smoke();
        r.particles = pss;
        com.dan.heidelberg.castle.Fountains fo = new com.dan.heidelberg.castle.Fountains();
        com.dan.heidelberg.core.Wetness.Set wets = com.dan.heidelberg.castle.Fountains.wetness(sc.terrain);
        r.wetness = wets;
        r.dust = w.dust;
        int W = 1280, H = 720;
        r.setSize(W, H);
        String only = a.length > 1 ? a[1] : null;
        Object[][] shots = {
                // Name, Tag, Stunde, Augpunkt x, y über Boden (NaN = Höhe), z, Ziel x, Ziel y, Ziel z
                {"uebersicht_mittag", 172, 13.5, -1500.0, 330.0, -1450.0, 0.0, 0.0, 0.0},
                {"schloss_morgen", 172, 7.2, -1100.0, 150.0, -1500.0, 0.0, 10.0, 0.0},
                {"tal_abend", 172, 20.5, 900.0, 250.0, -1300.0, -500.0, -60.0, -400.0},
                {"altstadt_herbst", 285, 16.5, -700.0, 110.0, -900.0, -480.0, -50.0, -320.0},
                {"koenigstuhl", 120, 15.0, 700.0, 320.0, 700.0, -300.0, -40.0, -300.0},
                {"nacht", 172, 23.5, -1200.0, 260.0, -1300.0, 0.0, 0.0, 0.0},
                {"schloss_hof", 172, 17.0, -8.0, 1.8, 16.0, 31.0, 12.0, -31.0},
                {"schloss_hof2", 172, 14.0, 20.0, 1.8, 12.0, -17.0, 10.0, -29.0},
                {"schloss_tal", 172, 18.0, -620.0, 60.0, -420.0, 0.0, 14.0, 0.0},
                {"schloss_luft", 172, 17.5, -190.0, 150.0, 230.0, -20.0, 8.0, 30.0},
                {"hortus_haupt", 172, 16.5, -14.0, 3.0, 116.0, -90.0, 4.5, 100.0},
                {"hortus_grotte", 172, 16.5, -70.0, 2.2, 66.0, -70.0, 0.0, 90.0},
                {"grotte_innen", 172, 16.5, -70.0, 2.0, 92.0, -70.0, -0.5, 108.0},
                {"grotte_drin", 172, 16.5, -70.0, 1.6, 86.2, -70.0, 0.8, 99.0},
                {"grotte_tor", 172, 16.5, -73.0, 1.6, 79.0, -70.0, 0.6, 92.0},
                {"wasser_achteck", 172, 16.5, -84.0, 2.6, 112.0, -70.0, 7.0, 102.0},
                {"wasser_achteck_nah", 172, 16.5, -62.0, 2.2, 112.0, -70.0, 6.0, 102.0},
                {"wasser_saeule", 172, 16.5, -112.0, 1.8, 118.0, -118.0, 6.5, 111.0},
                {"wasser_kaskade", 172, 16.5, -70.0, 1.6, 87.0, -70.0, -0.9, 97.0},
                {"wasser_rhenus", 172, 16.5, -72.0, 2.0, 70.0, -70.0, -0.3, 79.0},
                {"wasser_klein", 172, 16.5, -106.0, 1.8, 87.0, -106.0, -1.0, 94.0},
                {"neckar_nah", 285, 14.0, -438.0, 6.5, -605.0, -441.6, -80.6, -612.4},
                {"wasser_luft", 172, 16.5, -40.0, 45.0, 150.0, -75.0, 5.0, 100.0},
                // Phase 6: Dampf und Licht (Tag 285 = 12. Oktober)
                {"nebel_a", 285, 7.0, -430.0, 125.0, -300.0, 0.0, 10.0, 0.0},
                {"morgen_a", 285, 10.0, -430.0, 125.0, -300.0, 0.0, 10.0, 0.0},
                {"nebel_hof", 285, 7.3, -150.0, 28.0, 150.0, 20.0, 12.0, -20.0},
                {"fackel_hof", 285, 19.6, -30.0, 1.7, 10.0, 25.0, 3.0, 0.0},
                {"fackel_hortus", 285, 19.6, -50.0, 2.5, 126.0, -80.0, 3.5, 102.0},
                {"fackel_tal", 285, 19.7, -620.0, 60.0, -420.0, 0.0, 14.0, 0.0},
                {"rauch_tal", 335, 8.2, -520.0, 40.0, -330.0, -470.0, -75.0, -320.0},
                {"regen_bogen", 150, 18.9, -60.0, 30.0, 60.0, 800.0, 120.0, 100.0},
                {"gischt_bogen", 150, 18.9, -62.0, 2.0, 112.0, -75.0, 3.0, 100.0},
                {"abend_hof", 172, 20.6, 20.0, 1.8, 12.0, -30.0, 12.0, -20.0},
                {"schnee_luft", 20, 11.0, -190.0, 150.0, 230.0, -20.0, 8.0, 30.0},
                {"abend_west", 172, 20.9, -14.0, 3.0, 116.0, -800.0, 45.0, -300.0},
                {"abend_tal", 172, 20.9, -620.0, 60.0, -420.0, 0.0, 14.0, 0.0},
                {"schloss_innen", 285, 13.3, 1.5, 1.7, -30.0, 12.0, 2.5, -23.0},
                {"hortus_luft", 172, 17.0, 20.0, 80.0, 190.0, -70.0, 4.0, 95.0},
                {"wappen_friedrich", 172, 14.0, 24.0, 5.5, 4.0, 31.0, 6.0, -22.6},
                {"wappen_friedrich_nah", 172, 14.0, 31.0, 6.3, -12.5, 31.0, 6.0, -22.6},
                {"wappen_otto", 172, 15.0, -17.0, 10.2, -13.5, -17.0, 10.2, -21.6},
                {"wappen_torturm", 172, 16.0, -68.0, 19.4, 2.0, -57.4, 19.4, 2.0},
                {"wappen_torturm_hof", 172, 16.0, -30.0, 19.4, 2.0, -47.0, 19.4, 2.0},
                // Phase 8: Räume und Leben (Tag 285 = 12. Oktober)
                {"p8_fass_hof", 285, 13.0, 20.0, 1.7, 16.8, 34.0, 3.0, 16.8},
                {"p8_fass_tuer", 285, 13.0, 36.4, 1.7, 16.8, 43.0, 3.0, 16.8},
                {"p8_fass_seite", 285, 13.0, 38.5, 1.8, 26.5, 43.0, 3.3, 16.8},
                {"p8_fass_kopf", 285, 13.0, 37.0, 1.7, 16.8, 40.0, 3.3, 16.8},
                {"p8_fass_leiter", 285, 13.0, 37.5, 1.8, 12.0, 43.4, 3.2, 14.5},
                {"p8_saal_tuer", 285, 13.0, 8.0, 1.7, -23.6, 8.0, 2.8, -31.0},
                {"p8_saal_abend", 285, 19.7, 8.0, 1.7, -23.6, 8.0, 2.8, -31.0},
                {"p8_saal_tisch", 285, 19.7, 1.4, 1.7, -23.6, 12.0, 1.2, -27.0},
                {"p8_saal_spiegel", 285, 19.7, 13.5, 1.7, -27.0, 0.4, 3.0, -27.0},
                {"p8_saal_thron", 285, 13.0, 8.0, 1.7, -27.0, 8.0, 2.4, -31.0},
                {"p8_grotte", 285, 13.0, -70.0, -0.4, 89.0, -70.0, 0.4, 98.0},
                {"p8_hof_leben", 285, 13.0, 14.0, 1.7, 6.0, 8.0, 1.5, -10.0},
                {"p8_hof_leben2", 285, 13.0, 26.0, 1.7, 10.0, 4.0, 1.5, 0.0},
                {"p8_wache_tor", 285, 13.0, -38.0, 1.7, 2.0, -50.0, 1.5, 2.0},
                {"p8_kutsche", 285, 13.0, -10.0, 1.7, -8.0, 4.0, 1.5, -15.4},
                {"p8_karren", 285, 13.0, 20.0, 1.7, 5.0, 26.0, 1.2, 12.8},
                {"p8_fass_kueferei", 285, 13.0, 36.4, 1.7, 16.8, 41.0, 1.2, 24.5},
                {"p8_saal_gaeste", 285, 20.3, 12.5, 1.7, -23.4, 6.5, 1.5, -27.0},
                {"p8_voegel", 192, 18.0, -5.0, 1.7, 6.0, -33.0, 30.0, 4.0},
                // Prüfstand: Lichtschacht im Saal, Becken mit Spiegelung, Blick von außen
                {"probe_saal", 285, 13.3, 6.5, 1.8, -10.5, -4.0, 3.2, -1.0},
                {"probe_saal2", 285, 13.3, -6.5, 1.7, -10.0, 5.0, 0.5, -1.5},
                {"probe_becken", 172, 13.5, 3.0, 6.0, 2.0, -0.5, 0.2, 14.0},
                {"probe_aussen", 172, 13.5, 24.0, 4.0, 22.0, 0.0, 3.0, -2.0},
        };
        if ("fassfahrt".equals(only)) {
            java.util.List<Object[]> l = new java.util.ArrayList<>();
            for (com.dan.heidelberg.camera.Rides.Ride rd : com.dan.heidelberg.camera.Rides.build(sc.terrain, w.solids)) {
                if (!rd.name.equals("Vom Hof in den Fassbau")) continue;
                double[] fr = {0.02, 0.14, 0.26, 0.36, 0.44, 0.55, 0.68, 0.82, 0.93};
                for (int k = 0; k < fr.length; k++) {
                    double[] o = new double[7];
                    rd.shot.pose(rd.shot.duration() * fr[k], o);
                    l.add(new Object[]{"fahrt_" + k, 285, 13.0, o[0], o[1], o[2], o[3], o[4], o[5], o[6]});
                }
            }
            shots = l.toArray(new Object[0][]);
        }
        if ("rides".equals(only)) {
            java.util.List<Object[]> l = new java.util.ArrayList<>();
            int ri = 0;
            for (com.dan.heidelberg.camera.Rides.Ride rd : com.dan.heidelberg.camera.Rides.build(sc.terrain, w.solids)) {
                double[] fr = {0.12, 0.5, 0.94};
                for (int k = 0; k < fr.length; k++) {
                    double[] o = new double[7];
                    rd.shot.pose(rd.shot.duration() * fr[k], o);
                    l.add(new Object[]{"ride_" + ri + "_" + k, 172, 15.0, o[0], o[1], o[2], o[3], o[4], o[5], o[6]});
                }
                ri++;
            }
            shots = l.toArray(new Object[0][]);
        }
        if ("script".equals(only)) {
            java.util.List<Object[]> l = new java.util.ArrayList<>();
            int si = 1;
            for (com.dan.heidelberg.camera.Director.Scene sn : com.dan.heidelberg.camera.Director.scenes(sc.terrain, w.solids)) {
                double[] o = new double[7];
                sn.shot.pose(sn.shot.duration() * 0.5, o);
                l.add(new Object[]{String.format("szene_%02d", si++), com.dan.heidelberg.camera.Director.DAY, sn.h0 + (sn.h1 - sn.h0) * 0.5, o[0], o[1], o[2], o[3], o[4], o[5], o[6]});
            }
            shots = l.toArray(new Object[0][]);
        }
        for (Object[] s : shots) {
            String name = (String) s[0];
            if (only != null && !name.equals(only) && !(only.equals("probe") && name.startsWith("probe_")) && !(only.equals("castle") && castleOnly.contains(name)) && !(only.equals("rides") && name.startsWith("ride_")) && !(only.equals("fassfahrt") && name.startsWith("fahrt_")) && !(only.equals("p8") && name.startsWith("p8_")) && !(only.equals("script") && name.startsWith("szene_"))) continue;
            if (only == null && (name.startsWith("probe_") || name.startsWith("p8_") || castleOnly.contains(name))) continue;
            int day = a.length > 3 ? Integer.parseInt(a[2]) : (Integer) s[1];
            double hour = a.length > 3 ? Double.parseDouble(a[3]) : (Double) s[2];
            DayNightCycle cyc = new DayNightCycle();
            cyc.set(day, hour);
            Thermal.setDay(day);
            Thermal.snow = (float) Climate.snow(day);
            Thermal.ambient = (float) Math.max(0, Climate.air(day, hour) + 4);
            r.day = day; r.hour = hour; r.sidereal = cyc.siderealDeg;
            r.setSky(cyc, name.startsWith("abend_") || name.equals("szene_09") ? 0.3 : 0.12);
            if (name.startsWith("schnee_")) Thermal.snow = 0.8f;
            w.grove.setSeason(day, Thermal.snow);
            r.leaves = w.grove.quads;
            Camera c = new Camera();
            c.ex = (Double) s[3]; c.ez = (Double) s[5];
            boolean inside = name.equals("wasser_kaskade") || name.equals("wasser_klein") || name.equals("grotte_drin") || name.equals("grotte_tor");
            c.ey = name.startsWith("ride_") || name.startsWith("szene_") ? (Double) s[4] : sc.terrain.stand(c.ex, c.ez, inside ? -1e9 : 1e9) + (Double) s[4];
            if (s.length > 9) c.fovY = (Double) s[9];
            c.lookAt((Double) s[6], (Double) s[7], (Double) s[8]);
            r.resetExposure();
            if (probe) {
                // Tropfen im Becken: Ringwellen verschiedenen Alters
                r.ripples = new float[][]{{-1.5f, 14.4f, -0.8f, 0.05f}, {1.2f, 13.5f, -2.5f, 0.05f}, {3.0f, 14.8f, -1.5f, 0.04f}, {0f, 13.8f, -0.2f, 0.06f}};
            }
            boolean water = name.startsWith("wasser_") || name.equals("gischt_bogen");
            vf.forced = name.startsWith("nebel_") || (name.startsWith("szene_") && name.compareTo("szene_05") < 0);
            vf.update(day, hour, cyc.elevationDeg, 0, 0, 3.0);
            r.fogTop = vf.top; r.fogAmt = vf.amount; r.fogBurn = vf.burn;
            boolean shower = name.startsWith("regen_");
            if (shower) {
                double sx = -cyc.dir[0], sz = -cyc.dir[2], sl = Math.hypot(sx, sz);
                r.shower = new float[]{(float) (c.ex + sx / sl * 1250), (float) (c.ez + sz / sl * 1250), 800f, 0.0032f, 620f};
            } else r.shower = new float[5];
            sm.attach(com.dan.heidelberg.castle.Castle.CHIMNEYS, w.townChimneys);
            boolean smokeOn = name.startsWith("rauch_") || name.startsWith("nebel_") || name.startsWith("fackel_") || name.startsWith("abend_") || (name.startsWith("szene_") && name.compareTo("szene_04") < 0);
            r.sprays = new float[0][];
            if (name.startsWith("neckar_")) for (int q = 0; q < 150; q++) w.neckar.update(0.1, 0.35, r.windX, r.windZ, (Double) s[3], (Double) s[5]);
            pss.clear(); r.sprites.clear();
            r.ripples = new float[0][];
            fo.enabled = water;
            if (water) {
                for (int i = 0; i < 10 * 60; i++) { fo.emit(pss, c.ex, c.ez); pss.step(com.dan.heidelberg.castle.Fountains.STEP, 0.4f, 0.2f, sc.terrain, wets); if (i % 12 == 0) wets.step(0.2f, 0.7f); }
            }
            if (smokeOn) {
                for (int i = 0; i < 20 * 60; i++) {
                    sm.emit(pss, 1 / 60.0, c.ex, c.ez, Climate.air(day, hour));
                    pss.step(com.dan.heidelberg.castle.Fountains.STEP, 0.4f, 0.2f, sc.terrain, wets);
                }
            }
            java.awt.image.BufferedImage img = null;
            for (int i = 0; i < 6; i++) {
                if (water) r.sprays = fo.sprays(c.ex, c.ez, 0.4f, 0.2f);
                if (smokeOn) { sm.emit(pss, 0.1, c.ex, c.ez, Climate.air(day, hour)); for (int q = 0; q < 6; q++) pss.step(com.dan.heidelberg.castle.Fountains.STEP, 0.4f, 0.2f, sc.terrain, wets); }
                w.torches.update(r, pss, i * 0.1, 0.1, c.ex, c.ey, c.ez, r.night());
                w.life.enabled = !Boolean.getBoolean("life.off");
                w.life.update(r, (name.contains("kutsche") ? w.life.tCoachStop() : name.contains("karren") ? w.life.tCartStop() : 40) + i * 0.1, day, hour, 0, c.ex, c.ey, c.ez);
                w.sward.update(i * 0.1f, day, Thermal.snow, 0.35, r.windX, r.windZ, null, c.ex, c.ey, c.ez);
                r.foliage = w.sward.meadow.batch;
                w.roadways.update(i * 0.1f, 0.1, day, 0, Thermal.snow, 0, 0.35f, r.windX, r.windZ, null, 1, c.ex, c.ey, c.ez);
                r.roads = w.roadways.roads;
                w.neckar.update(0.1, 0.35, r.windX, r.windZ, c.ex, c.ez);
                r.floats = w.neckar.quads;
                if (water) {
                    for (int q = 0; q < 6; q++) { fo.emit(pss, c.ex, c.ez); pss.step(com.dan.heidelberg.castle.Fountains.STEP, 0.4f, 0.2f, sc.terrain, wets); }
                    r.ripples = fo.ripples(i * 0.1 - fo.clock());
                }
                long t0 = System.nanoTime();
                img = r.render(c, i * 0.1, 0.1);
                System.out.printf("%s #%d: %.0f ms%n", name, i, (System.nanoTime() - t0) / 1e6);
            }
            ImageIO.write(img, "png", new File(out, name + ".png"));
            if (shower) bowCheck(r, cyc, c, img);
            System.out.printf("%s: Sonne Höhe %.1f°, Richtung %.0f°%n", name, cyc.elevationDeg, cyc.azimuthDeg);
        }
    }
}
