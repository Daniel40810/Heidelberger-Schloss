package com.dan.heidelberg.tools;

import com.dan.heidelberg.camera.CameraController;
import com.dan.heidelberg.castle.Zeit;
import com.dan.heidelberg.core.Camera;
import com.dan.heidelberg.core.Engine3D;
import com.dan.heidelberg.core.Scene;
import com.dan.heidelberg.core.Solids;
import com.dan.heidelberg.core.Terrain;
import com.dan.heidelberg.core.Thermal;
import com.dan.heidelberg.effects.Climate;
import com.dan.heidelberg.effects.DayNightCycle;
import com.dan.heidelberg.effects.ParticleSystem;
import com.dan.heidelberg.effects.Weather;
import com.dan.heidelberg.world.Heidelberg;
import com.dan.heidelberg.world.World;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Spielt den Zeitraffer der Zerstörung ohne Fenster ab (25 Schritte/s) wie die Bildschleife: Zeitstufen, Feuer, Rauch,
 * Sprengungen, Wetter, Kamerafahrt. Prüft in jedem Schritt, ob das Auge in einem festen Körper steht oder durch eine Fläche
 * springt, und schreibt Standbilder zu den gewünschten Zeiten. Aufruf: ZeitCheck [Ausgabeordner [Zeiten,mit,Komma [Rate]]].
 * Mit Rate 1 wird zusätzlich jedes Bild gerechnet und die Bildzeit je Szene ausgegeben.
 */
public final class ZeitCheck {
    public static void main(String[] a) throws Exception {
        File out = new File(a.length > 0 ? a[0] : "shotsz");
        out.mkdirs();
        double[] times = a.length > 1 && !a[1].isEmpty() ? java.util.Arrays.stream(a[1].split(",")).mapToDouble(Double::parseDouble).toArray() : new double[0];
        boolean timing = a.length > 2 && a[2].equals("1");
        World w = Heidelberg.build(false);
        Scene sc0 = w.scene;
        Terrain ter = sc0.terrain;
        Engine3D r = new Engine3D(sc0, 4096);
        r.riverFlow = w.neckar.flow;
        r.riverSurface = w.neckar.surface;
        ParticleSystem ps = new ParticleSystem();
        r.particles = ps;
        com.dan.heidelberg.core.Wetness.Set wets = com.dan.heidelberg.castle.Fountains.wetness(ter);
        r.wetness = wets;
        int W = Integer.getInteger("w", 960), H = Integer.getInteger("h", 540);
        r.setSize(W, H);
        final CameraController c = new CameraController(ter);
        c.setSolids(w.solids);
        Camera cam = new Camera();
        c.setPose(com.dan.heidelberg.camera.Viewpoint.ALL[0].pose(ter, w.solids), true);
        c.update(0.04, cam);
        final Weather weather = new Weather();
        weather.mode = Weather.CLEAR;
        final DayNightCycle cyc = new DayNightCycle();
        final int[] st = {172, 0, 0};    // Tag, Stufe, Wetter-Änderung
        final double[] hr = {16.3};
        final boolean[] dirty = {true};
        final int[] stage = {0};
        final Solids[] cur = {w.solids};
        final StringBuilder log = new StringBuilder();
        final Zeit z = new Zeit();
        final Zeit.Hooks hooks = new Zeit.Hooks() {
            @Override public CameraController camera() { return c; }
            @Override public Terrain terrain() { return ter; }
            @Override public void stage(int s) {
                Scene sc = w.stageScene(s);
                r.setScene(sc);
                cur[0] = w.stageSolids(s);
                c.setSolids(cur[0]);
                w.grove.attach(sc.mesh);
                stage[0] = s;
                dirty[0] = true;
                r.dust = s == 0 ? w.dust : new float[0][];
                log.append(String.format("  t=%.1f s: Stufe %d%n", z.time(), s));
            }
            @Override public void sun(int d, double h) { st[0] = d; hr[0] = h; dirty[0] = true; }
            @Override public void weather(int m) { weather.mode = m; }
            @Override public void strike(double x, double y, double zz) { weather.strike(ter, x, y, zz); log.append(String.format("  t=%.1f s: Blitz bei %.0f/%.0f/%.0f%n", z.time(), x, y, zz)); }
            @Override public void toast(String s, long ms) { log.append("  [").append(s).append("]\n"); }
            @Override public int day() { return st[0]; }
            @Override public double hour() { return hr[0]; }
            @Override public int weatherMode() { return weather.mode; }
        };
        z.start(hooks);
        double t = 0, dt = 0.04;
        int frames = 0, inside = 0, crossed = 0, under = 0, nextShot = 0;
        double worst = 9, minGround = 1e9, maxPs = 0;
        double[] prev = null;
        int maxBurning = 0, maxSprites = 0, maxLights = 0;
        double sumMs = 0;
        int msN = 0;
        double[] segMs = new double[6];
        int[] segN = new int[6];
        java.util.Arrays.sort(times);
        while (z.active() && t < 200) {
            // Umgebung
            Thermal.setDay(st[0]);
            Thermal.snow = (float) Climate.snow(st[0]);
            Thermal.ambient = (float) Math.max(0, Climate.air(st[0], hr[0]) + 4);
            if (weather.step(dt, st[0], hr[0], 12, cam.ex, cam.ez)) weather.makeBolt(ter);
            Sky_overcast(weather);
            weather.emit(ps, ter, cam.ex, cam.ey, cam.ez, (float) dt, 3f, 2f);
            ps.step((float) dt, 3f, 2f, ter, wets);
            r.flash = weather.flash;
            r.bolt = weather.bolt;
            r.sprites.clear();
            r.torches = new float[0]; r.torchN = 0;
            z.update(dt, r, ps, cam.ex, cam.ey, cam.ez);
            if (!z.active()) break;
            c.update(dt, cam);
            t += dt;
            frames++;
            maxBurning = Math.max(maxBurning, z.burning);
            maxSprites = Math.max(maxSprites, r.sprites.n);
            maxLights = Math.max(maxLights, r.torchN);
            maxPs = Math.max(maxPs, ps.n);
            Solids so = cur[0];
            double cl = so.clearance(cam.ex, cam.ey, cam.ez, 1.0, null);
            double gr = cam.ey - ter.stand(cam.ex, cam.ez, cam.ey);
            worst = Math.min(worst, cl);
            minGround = Math.min(minGround, gr);
            boolean viol = false;
            if (cl < 0.2) { inside++; viol = true; }
            if (gr < 0.9) { under++; viol = true; }
            if (prev != null) {
                double ch = Math.sqrt(sq(cam.ex - prev[0]) + sq(cam.ey - prev[1]) + sq(cam.ez - prev[2]));
                if (ch > 1e-6 && so.ray(prev[0], prev[1], prev[2], (cam.ex - prev[0]) / ch, (cam.ey - prev[1]) / ch, (cam.ez - prev[2]) / ch, ch) >= 0) { crossed++; viol = true; }
                if (ch > 40) { log.append(String.format("  Sprung %.1f m bei %.1f s%n", ch, z.time())); viol = true; }
            }
            if (viol && inside + under + crossed < 6) log.append(String.format("  Verstoß bei %.1f s: Auge %.1f/%.1f/%.1f, Abstand %.2f, Boden %.2f%n", z.time(), cam.ex, cam.ey, cam.ez, cl, gr));
            prev = new double[]{cam.ex, cam.ey, cam.ez};
            boolean shot = nextShot < times.length && z.time() >= times[nextShot];
            if (shot || (timing && frames % 5 == 0)) {
                if (dirty[0]) {
                    cyc.set(st[0], hr[0]);
                    r.setSky(cyc, 0.14);
                    r.day = st[0]; r.hour = hr[0]; r.sidereal = cyc.siderealDeg;
                    dirty[0] = false;
                }
                cyc.set(st[0], hr[0]);
                r.day = st[0]; r.hour = hr[0]; r.sidereal = cyc.siderealDeg;
                long t0 = System.nanoTime();
                BufferedImage img = r.render(cam, z.time(), dt);
                double ms = (System.nanoTime() - t0) / 1e6;
                int seg = Math.min(5, (int) (z.time() / 20));
                segMs[seg] += ms; segN[seg]++;
                if (shot) {
                    BufferedImage o = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = o.createGraphics();
                    g.drawImage(img, 0, 0, null);
                    float fb = z.fadeBlack(), fw = z.fadeWhite();
                    if (fb > 0.004f) { g.setColor(new Color(0, 0, 0, Math.min(255, (int) (255 * fb)))); g.fillRect(0, 0, o.getWidth(), o.getHeight()); }
                    if (fw > 0.004f) { g.setColor(new Color(255, 244, 226, Math.min(255, (int) (255 * fw)))); g.fillRect(0, 0, o.getWidth(), o.getHeight()); }
                    g.dispose();
                    ImageIO.write(o, "png", new File(out, String.format("z%05.1f.png", times[nextShot])));
                    System.out.printf("Bild t=%.1f s  Stufe %d  Brände %d  Flecken %d  Lichter %d  Teilchen %d  %.0f ms%n", z.time(), stage[0], z.burning, r.sprites.n, r.torchN, ps.n, ms);
                    nextShot++;
                }
            }
        }
        System.out.printf("%nZeitraffer: %.1f s, %,d Schritte, kleinster Abstand zu Körpern %.2f m, Boden %.2f m, im Körper %d, Schritte durch Flächen %d, unter dem Boden %d%n",
                t, frames, worst, minGround, inside, crossed, under);
        System.out.printf("Höchstwerte: %d Brandherde, %d Flecken, %d Lichter, %.0f Teilchen%n", maxBurning, maxSprites, maxLights, maxPs);
        if (timing) {
            for (int i = 0; i < 6; i++) if (segN[i] > 0) System.out.printf("  %3d bis %3d s: %.0f ms je Bild (%d Bilder)%n", i * 20, i * 20 + 20, segMs[i] / segN[i], segN[i]);
        }
        System.out.println(log);
    }

    static void Sky_overcast(Weather wx) { com.dan.heidelberg.effects.Sky.overcastNext = wx.overcast; }

    static double sq(double x) { return x * x; }
}
