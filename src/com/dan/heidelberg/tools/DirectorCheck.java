package com.dan.heidelberg.tools;

import com.dan.heidelberg.camera.CameraController;
import com.dan.heidelberg.camera.Director;
import com.dan.heidelberg.core.Camera;
import com.dan.heidelberg.core.Solids;
import com.dan.heidelberg.world.Heidelberg;
import com.dan.heidelberg.world.World;

import java.util.List;

/**
 * Spielt das ganze Drehbuch ohne Fenster ab (25 Bilder/s) und prüft in jedem Bild, ob das Auge in einem festen Körper
 * steht, unter dem Boden oder durch eine Fläche springt, auch auf den Anflügen zwischen den Szenen. Dann bricht ein
 * simulierter Mausruck das Drehbuch ab.
 */
public final class DirectorCheck {
    public static void main(String[] a) {
        World w = Heidelberg.build(false);
        Solids so = w.solids;
        final CameraController c = new CameraController(w.scene.terrain);
        c.setSolids(so);
        Camera cam = new Camera();
        c.setPose(com.dan.heidelberg.camera.Viewpoint.ALL[0].pose(w.scene.terrain, so), true);
        c.update(0.04, cam);
        final double[] st = {0, 12.0, 0, 0, 0};   // Tag, Uhr (Zeit), Wetter, Nebel, Fackeln
        final StringBuilder log = new StringBuilder();
        Director.Stage stage = new Director.Stage() {
            int day = 172; double hour = 12, haze = 0.12; boolean fog, torches = true; int weather = 1;
            @Override public CameraController camera() { return c; }
            @Override public void setSunTime(int d, double h) { day = d; hour = h; }
            @Override public void setWeather(int m) { weather = m; }
            @Override public void setValleyFog(boolean f, double s) { fog = f; }
            @Override public void setTorches(boolean on) { torches = on; }
            @Override public void setHaze(double h) { haze = h; }
            @Override public void setShower(boolean on) { }
            @Override public void setTimelapse(double h) { }
            @Override public void setSnowCover(double s) { }
            @Override public void toast(String s, long ms) { log.append("   [").append(s).append("]\n"); }
            @Override public int day() { return day; }
            @Override public double hour() { return hour; }
            @Override public double haze() { return haze; }
            @Override public boolean fogForced() { return fog; }
            @Override public int weatherMode() { return weather; }
            @Override public boolean torches() { return torches; }
        };
        List<Director.Scene> scenes = Director.scenes(w.scene.terrain, so);
        // 1) jede Szenenfahrt für sich
        System.out.println("Szenenfahrten:");
        int bad = 0;
        for (Director.Scene s : scenes) {
            CameraCheck.Result r = CameraCheck.check(s.shot, so, w.scene.terrain);
            if (!r.ok()) bad++;
            System.out.printf("  %-30s %5.1f s %6.0f m  Abstand Körper %.2f m, Boden %.1f m, Verstöße %d%s%n", s.name, s.shot.duration(), r.length, r.minSolid, r.minGround, r.violations,
                    r.ok() ? "" : String.format("  erster bei %.2f s (%s) an %.1f/%.1f/%.1f", r.firstT, r.what, r.firstAt[0], r.firstAt[1], r.firstAt[2]));
        }
        System.out.println("Szenenfahrten mit Verstoß: " + bad);

        // 2) das ganze Drehbuch, so wie die Bildschleife es spielt
        Director d = new Director(stage, scenes);
        d.start(0);
        double t = 0, dt = 0.04, worst = 9, minGround = 1e9;
        int frames = 0, inside = 0, crossed = 0, under = 0, lastScene = -1, sceneFrames = 0, cards = 0;
        double[] prev = null;
        double tScene = 0;
        int sceneBad = 0;
        double hourMin = 99, hourMax = -1;
        while (d.active() && t < 1200) {
            d.update(dt);
            if (!d.active()) break;
            c.update(dt, cam);
            t += dt;
            frames++;
            if (d.sceneIndex() != lastScene) {
                if (lastScene >= 0) System.out.printf("  Szene %d fertig nach %.1f s, Verstöße %d%n", lastScene + 1, tScene, sceneBad);
                lastScene = d.sceneIndex(); tScene = 0; sceneBad = 0;
            }
            tScene += dt;
            if (d.card() != null) cards++;
            double cl = so.clearance(cam.ex, cam.ey, cam.ez, 1.0, null);
            double gr = cam.ey - w.scene.terrain.stand(cam.ex, cam.ez, cam.ey);
            worst = Math.min(worst, cl);
            minGround = Math.min(minGround, gr);
            boolean viol = false;
            if (cl < 0.2) { inside++; viol = true; }
            if (gr < 0.9) { under++; viol = true; }
            if (prev != null) {
                double ch = Math.sqrt(sq(cam.ex - prev[0]) + sq(cam.ey - prev[1]) + sq(cam.ez - prev[2]));
                if (ch > 1e-6 && so.ray(prev[0], prev[1], prev[2], (cam.ex - prev[0]) / ch, (cam.ey - prev[1]) / ch, (cam.ez - prev[2]) / ch, ch) >= 0) { crossed++; viol = true; }
                if (ch > 40) { System.out.printf("  Sprung %.1f m bei %.1f s in Szene %d%n", ch, t, lastScene + 1); viol = true; }
            }
            if (viol) {
                sceneBad++;
                if (sceneBad <= 3) System.out.printf("    Verstoß Szene %d, %.1f s: Auge %.1f/%.1f/%.1f, Abstand %.2f, Boden %.2f%n", lastScene + 1, tScene, cam.ex, cam.ey, cam.ez, cl, gr);
            }
            prev = new double[]{cam.ex, cam.ey, cam.ez};
        }
        if (lastScene >= 0) System.out.printf("  Szene %d fertig nach %.1f s, Verstöße %d%n", lastScene + 1, tScene, sceneBad);
        System.out.printf("%nDrehbuch: %.0f s, %,d Bilder, kleinster Abstand zu Körpern %.2f m, Boden %.2f m, im Körper %d, Schritte durch Flächen %d, unter dem Boden %d, Bilder mit Tafel %d%n",
                t, frames, worst, minGround, inside, crossed, under, cards);
        System.out.println("Zustand danach: Tag " + stage.day() + ", Uhr " + String.format("%.2f", stage.hour()) + ", Nebel erzwungen " + stage.fogForced() + ", Dunst " + stage.haze());
        System.out.println(log.length() > 0 ? "Meldungen:\n" + log : "");

        // 3) Abbruch durch den Benutzer
        d.start(3);
        for (int i = 0; i < 50; i++) { d.update(dt); c.update(dt, cam); }
        boolean activeBefore = d.active();
        c.drag(10, 0, false);
        d.update(dt);
        c.update(dt, cam);
        System.out.println("Abbruch durch Mausruck: aktiv vorher " + activeBefore + ", danach " + d.active());
        // 4) Überspringen und Pause
        d.start(0);
        for (int i = 0; i < 50; i++) { d.update(dt); c.update(dt, cam); }
        d.next();
        for (int i = 0; i < 25; i++) { d.update(dt); c.update(dt, cam); }
        System.out.println("Nach Überspringen: Szene " + (d.sceneIndex() + 1) + " (" + d.status() + ")");
        d.pause(true);
        double[] p0 = {cam.ex, cam.ey, cam.ez};
        for (int i = 0; i < 50; i++) { d.update(dt); c.update(dt, cam); }
        double moved = Math.sqrt(sq(cam.ex - p0[0]) + sq(cam.ey - p0[1]) + sq(cam.ez - p0[2]));
        System.out.printf("In der Pause bewegt sich die Kamera um %.4f m%n", moved);
        d.pause(false);
        d.stop();
    }

    static double sq(double x) { return x * x; }
}
