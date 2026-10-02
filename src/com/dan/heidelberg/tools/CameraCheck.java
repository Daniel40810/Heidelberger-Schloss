package com.dan.heidelberg.tools;

import com.dan.heidelberg.camera.CameraController;
import com.dan.heidelberg.camera.Viewpoint;
import com.dan.heidelberg.core.Camera;
import com.dan.heidelberg.core.Solids;
import com.dan.heidelberg.world.Heidelberg;
import com.dan.heidelberg.world.World;

/**
 * Prüft die Kamera (Phase 7) ohne Fenster: Gehen die Blickpunkte, der freie Orbit und die Fahrten durch Mauern, Gelände
 * oder Bäume? Zahlen: kleinster Abstand zu festen Körpern und zum Boden, Zahl der Eingriffe des Schutzes.
 */
public final class CameraCheck {
    public static void main(String[] a) {
        long t0 = System.nanoTime();
        World w = Heidelberg.build(false);
        Solids so = w.solids;
        System.out.printf("Aufbau %.1f s, feste Körper: %,d Dreiecke%n", (System.nanoTime() - t0) / 1e9, so.triangles);

        // 1) Blickpunkte: Auge und Drehpunkt frei? (ohne Schutz)
        System.out.println("\nBlickpunkte (Auge und Drehpunkt dürfen nicht in festen Körpern liegen):");
        CameraController cc = new CameraController(w.scene.terrain);
        int bad = 0;
        for (int i = 0; i < Viewpoint.ALL.length; i++) {
            Viewpoint v = Viewpoint.ALL[i];
            double[] pose = v.pose(w.scene.terrain, so);
            double[] e = cc.eyeOf(pose);
            { double[] ee = e.clone(); if (so.push(ee, CameraController.R_EYE)) e = ee; }
            double ce = so.clearance(e[0], e[1], e[2], 3, null), cp = so.clearance(pose[0], pose[1], pose[2], 3, null);
            boolean flag = ce < CameraController.R_EYE - 0.02 || cp < CameraController.R_PIVOT - 0.02;
            boolean seesThrough = !so.segmentFree(e[0], e[1], e[2], pose[0], pose[1], pose[2], 0.05);
            if (flag) bad++;
            System.out.printf("%2d %-34s Abstand Auge %.2f  Drehpunkt %.2f  Blick durch Körper: %s%s%n", i, v.name, ce, cp, seesThrough ? "ja" : "nein", flag ? "   <-- im Körper" : "");
        }
        System.out.println("Blickpunkte mit Auge oder Drehpunkt im Körper: " + bad);

        // 2) Flüge zwischen allen Blickpunkten (jeder zu jedem): hindernisfrei?
        boolean ridesOnly = a.length > 0 && a[0].equals("rides");
        int n = ridesOnly ? 0 : Viewpoint.ALL.length, routes = 0, badRoutes = 0, direct = 0;
        double minS = 99, minG = 1e9, longest = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                double[] pa = Viewpoint.ALL[i].pose(w.scene.terrain, so), pb = Viewpoint.ALL[j].pose(w.scene.terrain, so);
                double[] ea = cc.eyeOf(pa), eb = cc.eyeOf(pb);
                so.push(ea, CameraController.R_EYE); so.push(eb, CameraController.R_EYE);
                com.dan.heidelberg.camera.Shot sh = com.dan.heidelberg.camera.Router.route(so, w.scene.terrain, ea, new double[]{pa[0], pa[1], pa[2]}, eb, new double[]{pb[0], pb[1], pb[2]}, "Flug");
                Result r = check(sh, so, w.scene.terrain);
                routes++;
                minS = Math.min(minS, r.minSolid); minG = Math.min(minG, r.minGround); longest = Math.max(longest, sh.duration()); if (sh.duration() > 14) System.out.printf("  langer Flug %d → %d: %.1f s%n", i, j, sh.duration());
                if (!r.ok()) {
                    badRoutes++;
                    if (badRoutes <= 12) System.out.printf("  Flug %d → %d (%s → %s): %d Verstöße, erster bei %.2f s (%s) an %.0f/%.0f/%.0f%n", i, j, Viewpoint.ALL[i].name, Viewpoint.ALL[j].name, r.violations, r.firstT, r.what, r.firstAt[0], r.firstAt[1], r.firstAt[2]);
                }
            }
        }
        System.out.printf("Flüge zwischen Blickpunkten: %d, mit Verstoß %d, kleinster Abstand zu Körpern %.2f m, zum Boden %.1f m, längster Flug %.1f s%n", routes, badRoutes, minS, minG, longest);

        // 2b) Fahrten
        System.out.println("\nFahrten:");
        int rb = 0;
        for (com.dan.heidelberg.camera.Rides.Ride rd : com.dan.heidelberg.camera.Rides.build(w.scene.terrain, so)) {
            Result r = check(rd.shot, so, w.scene.terrain);
            if (!r.ok()) rb++;
            System.out.printf("  %-44s %5.1f s %6.0f m  kleinster Abstand Körper %.2f m, Boden %.1f m, Verstöße %d%s%n", rd.name, rd.shot.duration(), r.length, r.minSolid, r.minGround, r.violations,
                    r.ok() ? "" : String.format("  erster bei %.2f s (%s) an %.1f/%.1f/%.1f", r.firstT, r.what, r.firstAt[0], r.firstAt[1], r.firstAt[2]));
        }
        System.out.println("Fahrten mit Verstoß: " + rb);
        if (a.length > 0 && a[0].equals("rides")) return;

        // 3) Zufälliger Orbit: 6000 Bilder mit Tasten und Mausbewegung; zählt, ob das Auge je im Körper steht
        java.util.Random rnd = new java.util.Random(7);
        CameraController c = new CameraController(w.scene.terrain);
        c.setSolids(so);
        Camera cam = new Camera();
        int inside = 0, frames = 0, underground = 0, crossed = 0;
        double worst = 9;
        long tt = System.nanoTime();
        double[] last = new double[3];
        String vname = "";
        for (int s = 0; s < 40; s++) {
            Viewpoint v = Viewpoint.ALL[rnd.nextInt(Viewpoint.ALL.length)];
            vname = v.name;
            c.setPose(v.pose(w.scene.terrain), true);
            c.update(1 / 30.0, cam);
            last[0] = cam.ex; last[1] = cam.ey; last[2] = cam.ez;
            for (int k = 0; k < 150; k++) {
                if (k % 25 == 0) {
                    c.releaseKeys();
                    c.setKey(rnd.nextInt(4), true);
                    if (rnd.nextBoolean()) c.setKey(CameraController.K_SHIFT, true);
                }
                if (k % 10 == 0) c.drag(rnd.nextGaussian() * 25, rnd.nextGaussian() * 8, false);
                if (k % 40 == 0) c.wheel(rnd.nextGaussian() * 3);
                c.update(1 / 30.0, cam);
                frames++;
                double cl = so.clearance(cam.ex, cam.ey, cam.ez, 1.0, null);
                worst = Math.min(worst, cl);
                if (cl < 0.25) { inside++; if (inside <= 4) System.out.printf("  im Körper: Lauf %d Bild %d, Abstand %.2f an %.1f/%.1f/%.1f%n", s, k, cl, cam.ex, cam.ey, cam.ez); }
                double chord = Math.sqrt(sq(cam.ex - last[0]) + sq(cam.ey - last[1]) + sq(cam.ez - last[2]));
                if (chord > 1e-6 && so.ray(last[0], last[1], last[2], (cam.ex - last[0]) / chord, (cam.ey - last[1]) / chord, (cam.ez - last[2]) / chord, chord) >= 0) { crossed++; if (crossed <= 4) { double[] cu = c.current(), po = c.pose(); System.out.printf("  Schritt durch Körper: Lauf %d Bild %d, von %.1f/%.1f/%.1f nach %.1f/%.1f/%.1f; Drehpunkt %.1f/%.1f/%.1f Ziel %.1f/%.1f/%.1f Abstand %.1f Gier %.0f Nick %.0f; Blickpunkt %s%n", s, k, last[0], last[1], last[2], cam.ex, cam.ey, cam.ez, cu[3], cu[4], cu[5], po[0], po[1], po[2], po[5], po[3], po[4], vname); } }
                last[0] = cam.ex; last[1] = cam.ey; last[2] = cam.ez;
                if (cam.ey < w.scene.terrain.stand(cam.ex, cam.ez, cam.ey) + 0.3) underground++;
            }
        }
        System.out.printf("%nFreier Orbit: %,d Bilder in %.0f ms, kleinster Abstand zu festen Körpern %.2f m, Bilder im Körper %d, Schritte durch Körper %d, unter dem Boden %d, Eingriffe %d%n",
                frames, (System.nanoTime() - tt) / 1e6, worst, inside, crossed, underground, c.guardHits);
    }

    /** Ergebnis der Prüfung einer Fahrt. */
    public static final class Result {
        public double minSolid = 99, minGround = 1e9, length;
        public int violations, samples;
        public double firstT = -1;
        public double[] firstAt;
        public String what = "";
        public boolean ok() { return violations == 0; }
    }

    /** Läuft eine Fahrt in 0,04-s-Schritten ab (25 Bilder/s) und misst Abstand zu festen Körpern und zum Boden. */
    public static Result check(com.dan.heidelberg.camera.Shot sh, Solids so, com.dan.heidelberg.core.Terrain t) {
        Result r = new Result();
        double[] o = new double[7], prev = null;
        double dur = sh.duration();
        for (double tt = 0; tt <= dur + 1e-9; tt += 0.04) {
            sh.pose(Math.min(tt, dur), o);
            double cl = so.clearance(o[0], o[1], o[2], 2.0, null);
            double gr = o[1] - t.stand(o[0], o[2], o[1]);
            r.samples++;
            r.minSolid = Math.min(r.minSolid, cl);
            r.minGround = Math.min(r.minGround, gr);
            if (prev != null) r.length += Math.sqrt(sq(o[0] - prev[0]) + sq(o[1] - prev[1]) + sq(o[2] - prev[2]));
            // Schritt zwischen zwei Bildern: durch einen Körper hindurch?
            boolean seg = prev != null && !so.segmentFree(prev[0], prev[1], prev[2], o[0], o[1], o[2], 0.05);
            String why = null;
            if (cl < 0.25) why = String.format("Körper %.2f m", cl);
            else if (gr < 1.0) why = String.format("Boden %.2f m", gr);
            else if (seg) why = "Strecke durch Körper";
            if (why != null) {
                r.violations++;
                if (r.firstT < 0) { r.firstT = tt; r.firstAt = new double[]{o[0], o[1], o[2]}; r.what = why; }
            }
            prev = o.clone();
        }
        return r;
    }

    static double sq(double x) { return x * x; }
}
