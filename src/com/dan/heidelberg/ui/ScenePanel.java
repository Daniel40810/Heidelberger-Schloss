package com.dan.heidelberg.ui;

import com.dan.heidelberg.camera.CameraController;
import com.dan.heidelberg.camera.Director;
import com.dan.heidelberg.camera.Rides;
import com.dan.heidelberg.camera.Viewpoint;
import com.dan.heidelberg.core.Camera;
import com.dan.heidelberg.core.Engine3D;
import com.dan.heidelberg.core.Scene;
import com.dan.heidelberg.core.Terrain;
import com.dan.heidelberg.core.Thermal;
import com.dan.heidelberg.effects.Climate;
import com.dan.heidelberg.effects.DayNightCycle;
import com.dan.heidelberg.effects.LightingEngine;
import com.dan.heidelberg.effects.ParticleSystem;
import com.dan.heidelberg.effects.Sky;
import com.dan.heidelberg.effects.Weather;
import com.dan.heidelberg.world.Heidelberg;
import com.dan.heidelberg.world.World;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Zeigt die Szene, nimmt Maus und Tastatur entgegen und treibt die Bildschleife. Gerechnet wird auf
 * einem eigenen Thread mit Delta-Time; die Oberfläche zeigt immer das zuletzt fertige Bild. Das Licht
 * (Sonne, Schatten, Himmel) rechnet ein zweiter Thread im Hintergrund, damit die Uhr nicht ruckt.
 * Aufbau nach Geyser; schlank gehalten, die Phasen 2 bis 11 setzen ihre Funktionen auf diese Schleife.
 */
public final class ScenePanel extends JPanel {
    /** Farben der Oberfläche: Sandsteinweiß, Dunstgrau, Abendgold, Neckarblau. */
    static final Color INK = new Color(236, 232, 224), MUTED = new Color(182, 184, 184), GOLD = new Color(232, 188, 98),
            WATER = new Color(104, 164, 206);
    static final String[] STEPS = {"Gelände, Fluss und Wald", "Sonne und Schatten"};

    private volatile Engine3D engine;
    private volatile CameraController ctl;
    private volatile Scene scene;
    private volatile World world;
    private final ParticleSystem ps = new ParticleSystem();
    /** Brunnen und Kaskaden des Hortus (Phase 5), Nässe um sie. */
    private final com.dan.heidelberg.castle.Fountains fountains = new com.dan.heidelberg.castle.Fountains();
    private final com.dan.heidelberg.effects.ValleyFog valleyFog = new com.dan.heidelberg.effects.ValleyFog();
    private final com.dan.heidelberg.castle.Smoke smoke = new com.dan.heidelberg.castle.Smoke();
    private volatile boolean showerOn;
    private com.dan.heidelberg.core.Wetness.Set wets = new com.dan.heidelberg.core.Wetness.Set();
    private double psAcc;
    private final Weather weather = new Weather();
    /** Phase 9: Zeitstufe (0 = 1619, 1 bis 3 = Ruine nach 1689, 1693, 1764) und Zeitraffer der Zerstörung. */
    private final com.dan.heidelberg.castle.Zeit zeit = new com.dan.heidelberg.castle.Zeit();
    private volatile int stageNow;
    private final Object lightLock = new Object();
    private Consumer<Integer> stageListener = i -> { };
    private Consumer<Boolean> zeitListener = b -> { };
    private final Camera cam = new Camera();
    private final DayNightCycle cycle = new DayNightCycle();
    private final ConcurrentLinkedQueue<Runnable> cmds = new ConcurrentLinkedQueue<>();

    private volatile BufferedImage shown;
    private volatile String loading = "Das Neckartal wird geformt …";
    private volatile int step = 1;
    private javax.swing.Timer pulse;
    private volatile int day = DayNightCycle.today();
    private volatile double hour = 16.0;
    private volatile boolean sunDirty = true;
    private volatile double haze = 0.12, timelapse = 0, wind = 0.35, airTemp = 12;
    private volatile int[] pickRequest;
    private volatile double fps, buildMs, shadowMs, renderMs, loopMs;
    private volatile boolean running = true;
    private volatile int viewpoint;
    private double litHour = -1, litOvercast = -1;
    private int lastMinute = -1;

    private Consumer<String> status = s -> { };
    private Consumer<double[]> timeListener = v -> { };
    private Runnable onReady = () -> { };
    private Consumer<Boolean> orbitListener = b -> { };
    private Consumer<Integer> weatherListener = m -> { };
    private Consumer<Integer> viewListener = m -> { };
    private int lastX, lastY;
    // Fahrten und Drehbuch (Phase 7)
    private volatile List<Rides.Ride> rides = new java.util.ArrayList<>();
    private volatile Director director;
    private volatile boolean showTafeln = true, freeCam;
    private volatile int rideSel;
    private volatile double rideSpeedUi = 1;
    private Consumer<String[][]> ridesListener = x -> { };
    private volatile String[][] ridesNames;

    public ScenePanel() {
        weather.mode = Weather.CLEAR;
        // Prüfhilfen: Tag, Uhrzeit, Nebeltag, Schauer vorgeben
        if (Integer.getInteger("heidelberg.day") != null) day = Integer.getInteger("heidelberg.day");
        if (System.getProperty("heidelberg.hour") != null) hour = Double.parseDouble(System.getProperty("heidelberg.hour"));
        valleyFog.forced = Boolean.getBoolean("heidelberg.fog");
        showerOn = Boolean.getBoolean("heidelberg.shower");
        setBackground(new Color(10, 16, 20));
        setFocusable(true);
        setDoubleBuffered(true);
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                lastX = e.getX(); lastY = e.getY();
            }

            @Override public void mouseMoved(MouseEvent e) { mouseSeen(); }

            @Override public void mouseDragged(MouseEvent e) {
                mouseSeen();
                CameraController c = ctl;
                if (c == null) return;
                boolean pan = SwingUtilities.isRightMouseButton(e) || SwingUtilities.isMiddleMouseButton(e) || e.isShiftDown();
                c.drag(e.getX() - lastX, e.getY() - lastY, pan);
                lastX = e.getX(); lastY = e.getY();
            }

            @Override public void mouseClicked(MouseEvent e) {
                if (lupe.active() && SwingUtilities.isLeftMouseButton(e)) {
                    if (lupe.hit(e.getX(), e.getY())) lupe.click(e.getX(), e.getY(), System.nanoTime() / 1e9);
                    else if (e.getClickCount() == 1) lupeRequest = new int[]{e.getX(), e.getY()};
                    return;
                }
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) pickRequest = new int[]{e.getX(), e.getY()};
            }

            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                CameraController c = ctl;
                if (c != null) c.wheel(e.getPreciseWheelRotation());
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        addMouseWheelListener(ma);
        setFocusTraversalKeysEnabled(false);
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { key(e, true); }
            @Override public void keyReleased(KeyEvent e) { key(e, false); }
        });
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                CameraController c = ctl;
                if (c != null) c.releaseKeys();
            }
        });
    }

    private void key(KeyEvent e, boolean down) {
        CameraController c = ctl;
        if (c == null) return;
        int k = -1;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W: case KeyEvent.VK_UP: k = CameraController.K_W; break;
            case KeyEvent.VK_S: case KeyEvent.VK_DOWN: k = CameraController.K_S; break;
            case KeyEvent.VK_A: case KeyEvent.VK_LEFT: k = CameraController.K_A; break;
            case KeyEvent.VK_D: case KeyEvent.VK_RIGHT: k = CameraController.K_D; break;
            case KeyEvent.VK_Q: case KeyEvent.VK_PAGE_DOWN: k = CameraController.K_Q; break;
            case KeyEvent.VK_E: case KeyEvent.VK_PAGE_UP: k = CameraController.K_E; break;
            case KeyEvent.VK_SHIFT: k = CameraController.K_SHIFT; break;
            default:
        }
        if (k >= 0) { c.setKey(k, down); return; }
        if (!down) return;
        int kc = e.getKeyCode();
        if (kc >= KeyEvent.VK_1 && kc <= KeyEvent.VK_9) { goViewpoint(kc - KeyEvent.VK_1); return; }
        if (kc >= KeyEvent.VK_NUMPAD1 && kc <= KeyEvent.VK_NUMPAD9) { goViewpoint(kc - KeyEvent.VK_NUMPAD1); return; }
        switch (kc) {
            case KeyEvent.VK_ESCAPE: if (help) help = false; else if (zeit.active()) toggleZeit(); else if (scriptActive() || c.riding) stopRide(); else if (cinema) setCinema(false); return;
            case KeyEvent.VK_B: toggleScript(); return;
            case KeyEvent.VK_Z: toggleZeit(); return;
            case KeyEvent.VK_L: setLupe(!lupe.active()); return;
            case KeyEvent.VK_X: setSchnitt((schnittNow + 1) % 3); return;
            case KeyEvent.VK_N: nextScene(); return;
            case KeyEvent.VK_V: nextRide(); return;
            case KeyEvent.VK_R: case KeyEvent.VK_0: case KeyEvent.VK_NUMPAD0: goViewpoint(0); return;
            case KeyEvent.VK_G: goViewpoint((viewpoint + 1) % Viewpoint.ALL.length); return;
            case KeyEvent.VK_Y: setWeather((weather.mode + 1) % Weather.MODES.length); return;
            case KeyEvent.VK_SPACE: {
                if (zeit.active()) { zeit.pause(!zeit.paused()); return; }
                if (scriptActive()) { pauseScript(); return; }
                boolean on = !c.autoOrbit;
                c.autoOrbit = on;
                SwingUtilities.invokeLater(() -> orbitListener.accept(on));
                return;
            }
            case KeyEvent.VK_PLUS: case KeyEvent.VK_ADD: case KeyEvent.VK_EQUALS: shiftHour(0.5); return;
            case KeyEvent.VK_MINUS: case KeyEvent.VK_SUBTRACT: shiftHour(-0.5); return;
            case KeyEvent.VK_P: requestStill(); return;
            case KeyEvent.VK_K: case KeyEvent.VK_F11: setCinema(!cinema); return;
            case KeyEvent.VK_F1: case KeyEvent.VK_H: help = !help; return;
            default:
        }
    }

    // ------------------------------------------------------------ Schnittstelle zum Bedienfeld

    public void setStatusListener(Consumer<String> s) { status = s; }
    public void setTimeListener(Consumer<double[]> l) { timeListener = l; }
    public void setOnReady(Runnable r) { onReady = r; }
    public void setOrbitListener(Consumer<Boolean> l) { orbitListener = l; }
    public void setWeatherListener(Consumer<Integer> l) { weatherListener = l; }
    public void setViewListener(Consumer<Integer> l) { viewListener = l; }

    /** Meldet dem Bedienfeld die Namen der Fahrten und Szenen, sobald die Welt steht (oder gleich, wenn sie schon steht). */
    public void setRidesListener(Consumer<String[][]> l) {
        ridesListener = l;
        String[][] n = ridesNames;
        if (n != null) SwingUtilities.invokeLater(() -> l.accept(n));
    }

    public void playRide(int i) {
        cmds.add(() -> {
            CameraController c = ctl;
            List<Rides.Ride> rl = rides;
            if (c == null || i < 0 || i >= rl.size()) return;
            Director d = director;
            if (d != null && d.active()) d.stop();
            Rides.Ride r = rl.get(i);
            rideSel = i;
            freeCam = true;
            c.playRide(r.shot, rideSpeedUi, null);
            com.dan.heidelberg.db.Dienst.ereignis("FAHRT", r.name, null);
            showToast(r.name, 2600);
        });
    }

    /** Nächste Fahrt der Liste (Taste V). */
    public void nextRide() {
        List<Rides.Ride> rl = rides;
        if (rl.isEmpty()) return;
        int i = (rideSel + 1) % rl.size();
        playRide(i);
        SwingUtilities.invokeLater(() -> rideListener.accept(i));
    }
    private Consumer<Integer> rideListener = i -> { };
    public void setRideListener(Consumer<Integer> l) { rideListener = l; }

    /** Hält Fahrt oder Drehbuch an; die Kamera bleibt dort stehen und gehört wieder dem Benutzer. */
    public void stopRide() {
        cmds.add(() -> {
            CameraController c = ctl;
            Director d = director;
            if (d != null && d.active()) d.stop();
            else if (c != null) c.cancelRide();
        });
    }

    public void setRideSpeed(double v) {
        rideSpeedUi = v;
        cmds.add(() -> {
            CameraController c = ctl;
            Director d = director;
            if (c != null && c.riding && (d == null || !d.active())) c.setRideSpeed(v);
        });
    }

    /** Drehbuch ab Szene i starten. */
    public void startScript(int from) {
        cmds.add(() -> {
            Director d = director;
            if (d == null) return;
            if (ctl != null) ctl.autoOrbit = false;
            SwingUtilities.invokeLater(() -> orbitListener.accept(false));
            freeCam = true;
            d.start(from);
            com.dan.heidelberg.db.Dienst.ereignis("DREHBUCH", "Szene " + (from + 1), null);
        });
    }

    private void startScriptNow(int from) {
        Director d = director;
        if (d == null) return;
        if (ctl != null) ctl.autoOrbit = false;
        d.start(from);
    }

    public void toggleScript() {
        Director d = director;
        if (d != null && d.active()) stopRide(); else startScript(0);
    }

    public void pauseScript() {
        cmds.add(() -> { Director d = director; if (d != null && d.active()) d.pause(!d.paused()); });
    }

    public void nextScene() { cmds.add(() -> { Director d = director; if (d != null && d.active()) d.next(); }); }

    public void previousScene() { cmds.add(() -> { Director d = director; if (d != null && d.active()) d.previous(); }); }

    public void setTafeln(boolean on) { showTafeln = on; }

    public boolean scriptActive() { Director d = director; return d != null && d.active(); }

    private final Director.Stage stage = new Director.Stage() {
        @Override public CameraController camera() { return ctl; }
        @Override public void setSunTime(int d, double h) { ScenePanel.this.setSunTime(d, h); }
        @Override public void setWeather(int m) { ScenePanel.this.setWeatherQuiet(m); }
        @Override public void setValleyFog(boolean f, double sc) { ScenePanel.this.setValleyFog(f, sc); }
        @Override public void setTorches(boolean on) { ScenePanel.this.setTorches(on); }
        @Override public void setHaze(double h) { ScenePanel.this.setHaze(h); }
        @Override public void setShower(boolean on) { showerOn = on; }
        @Override public void setTimelapse(double h) { timelapse = h; }
        @Override public void setSnowCover(double c) { snowPack = c; }
        @Override public void toast(String t, long ms) { showToast(t, ms); }
        @Override public int day() { return day; }
        @Override public double hour() { return hour; }
        @Override public double haze() { return haze; }
        @Override public boolean fogForced() { return valleyFog.forced; }
        @Override public int weatherMode() { return weather.mode; }
        @Override public boolean torches() { World w = world; return w == null || w.torches.enabled; }
    };

    private final com.dan.heidelberg.castle.Zeit.Hooks zeitHooks = new com.dan.heidelberg.castle.Zeit.Hooks() {
        @Override public CameraController camera() { return ctl; }
        @Override public Terrain terrain() { return scene.terrain; }
        @Override public void stage(int s) { applyStage(s); }
        @Override public void sun(int d, double hr) { setSunTime(d, hr); }
        @Override public void weather(int m) { setWeatherQuiet(m); }
        @Override public void strike(double x, double y, double z) { weather.strike(scene.terrain, x, y, z); }
        @Override public void toast(String t, long ms) { showToast(t, ms); }
        @Override public int day() { return day; }
        @Override public double hour() { return hour; }
        @Override public int weatherMode() { return weather.mode; }
    };

    private volatile int schnittNow;
    private final com.dan.heidelberg.lupe.Lupe lupe = new com.dan.heidelberg.lupe.Lupe();
    private volatile int[] lupeRequest;
    private Consumer<Boolean> lupeListener = b -> { };
    public void setLupeListener(Consumer<Boolean> l) { lupeListener = l; }
    /** Stein-Lupe an oder aus: Klick auf eine Fläche zeigt den Stoff und das Atommodell. */
    public void setLupe(boolean on) {
        lupe.setActive(on);
        if (on) showToast("Stein-Lupe: auf eine Fläche klicken", 2600);
        SwingUtilities.invokeLater(() -> lupeListener.accept(on));
    }
    public boolean lupeActive() { return lupe.active(); }
    private Consumer<Integer> cutListener = i -> { };
    public void setCutListener(Consumer<Integer> l) { cutListener = l; }

    /** Schnitt durch Terrassen und Bogenbau: 0 aus, 1 und 2 die beiden Ebenen. Nur in der Zeitstufe 1619; die Kamera springt zur Pose der Ebene. */
    public void setSchnitt(int i) {
        cmds.add(() -> {
            World wd = world;
            Engine3D r = engine;
            CameraController c = ctl;
            if (wd == null || r == null || c == null) return;
            if (i <= 0) { clearCut(); return; }
            if (zeit.active()) { zeit.stop(true); SwingUtilities.invokeLater(() -> zeitListener.accept(false)); }
            if (stageNow != 0) applyStage(0);
            com.dan.heidelberg.core.Mesh m = wd.stageScene(0).mesh;
            com.dan.heidelberg.core.Schnitt.Cap cap = com.dan.heidelberg.castle.Schnitte.get(m, i - 1);
            synchronized (lightLock) { r.setCut(cap); sunDirty = true; }
            c.setSolids(com.dan.heidelberg.castle.Schnitte.solids(m, i - 1));
            Director d = director;
            if (d != null && d.active()) d.stop();
            c.autoOrbit = false;
            SwingUtilities.invokeLater(() -> orbitListener.accept(false));
            freeCam = true;
            c.setPose(com.dan.heidelberg.castle.Schnitte.POSE[i - 1], true);
            schnittNow = i;
            SwingUtilities.invokeLater(() -> cutListener.accept(i));
            showToast("Schnitt: " + com.dan.heidelberg.castle.Schnitte.NAMES[i - 1], 3200);
            com.dan.heidelberg.db.Dienst.ereignis("SCHNITT", com.dan.heidelberg.castle.Schnitte.NAMES[i - 1], null);
        });
    }

    /** Schnitt abschalten (nur im Bildthread). */
    private void clearCut() {
        Engine3D r = engine;
        World wd = world;
        if (schnittNow == 0 || r == null) return;
        synchronized (lightLock) { r.setCut(null); sunDirty = true; }
        if (wd != null && ctl != null) ctl.setSolids(wd.solids);
        schnittNow = 0;
        SwingUtilities.invokeLater(() -> cutListener.accept(0));
    }
    public int schnitt() { return schnittNow; }

    private static void Wasserweg_show() { com.dan.heidelberg.castle.Wasserweg.show = true; }
    public void setWasserweg(boolean on) { com.dan.heidelberg.castle.Wasserweg.show = on; }
    public void setWasserorgel(boolean on) { com.dan.heidelberg.castle.Wasserorgel.on = on; }
    public void setKlang(boolean on) { com.dan.heidelberg.castle.Klang.enabled = on; }
    public void setStageListener(Consumer<Integer> l) { stageListener = l; }
    public void setZeitListener(Consumer<Boolean> l) { zeitListener = l; }
    public int stage() { return stageNow; }
    public boolean zeitActive() { return zeit.active(); }

    /** Zeitstufe von außen schalten (Bedienfeld): 0 = 1619, 1 = nach 1689, 2 = nach 1693, 3 = nach 1764. Bricht einen Zeitraffer ab. */
    public void setStage(int s) {
        cmds.add(() -> {
            if (zeit.active()) { zeit.stop(true); SwingUtilities.invokeLater(() -> zeitListener.accept(false)); }
            applyStage(s);
            showToast(com.dan.heidelberg.castle.Ruin.STAGE_NAMES[stageNow], 2400);
            com.dan.heidelberg.db.Dienst.ereignis("STUFE", com.dan.heidelberg.castle.Ruin.STAGE_NAMES[stageNow], null);
        });
    }

    /** Zeitraffer der Zerstörung starten oder beenden (Taste Z). */
    public void toggleZeit() {
        cmds.add(() -> {
            com.dan.heidelberg.db.Dienst.ereignis("ZEITRAFFER", zeit.active() ? "Ende" : "Start", null);
            if (zeit.active()) { zeit.stop(true); SwingUtilities.invokeLater(() -> zeitListener.accept(false)); return; }
            World w = world;
            if (w == null || w.stages() < 4) { showToast("Die Zeitstufen fehlen im Prüfstand", 2500); return; }
            Director d = director;
            if (d != null && d.active()) d.stop();
            clearCut();
            if (ctl != null) ctl.autoOrbit = false;
            SwingUtilities.invokeLater(() -> { orbitListener.accept(false); zeitListener.accept(true); });
            freeCam = true;
            zeit.start(zeitHooks);
        });
    }

    /** Netz, Kollisionsflächen und alles, was am Netz hängt, auf eine Zeitstufe umstellen. Nur im Bildthread. */
    private void applyStage(int s) {
        World wd = world;
        Engine3D r = engine;
        CameraController c = ctl;
        if (wd == null || r == null || c == null || s < 0 || s >= wd.stages() || (s == stageNow && r.mesh() == wd.stageScene(s).mesh)) return;
        if (schnittNow != 0 && s != 0) clearCut();
        long t0 = System.nanoTime();
        Scene sc = wd.stageScene(s);
        synchronized (lightLock) {
            r.setScene(sc);
            cycle.set(day, hour);
            r.setSky(cycle, Math.min(1, haze + 0.3 * weather.overcast));
            litHour = hour;
            sunDirty = false;
        }
        scene = sc;
        wd.grove.attach(sc.mesh);
        wd.solids = wd.stageSolids(s);
        c.setSolids(wd.solids);
        wd.life.attach(wd.solids, sc.terrain);
        boolean ruin = s > 0;
        wd.torches.ruin = ruin;
        wd.life.ruin = ruin;
        smoke.ruin = ruin;
        r.dust = ruin ? new float[0][] : wd.dust;
        r.torches = new float[0]; r.torchN = 0;
        stageNow = s;
        SwingUtilities.invokeLater(() -> stageListener.accept(s));
        System.out.printf(Locale.ROOT, "Zeitstufe %d (%s): %.0f ms%n", s, com.dan.heidelberg.castle.Ruin.STAGE_NAMES[s], (System.nanoTime() - t0) / 1e6);
    }

    /** Wetter setzen und das Bedienfeld nachführen, ohne Hinweis auf dem Bild. */
    private void setWeatherQuiet(int mode) {
        weather.mode = mode;
        sunDirty = true;
        SwingUtilities.invokeLater(() -> weatherListener.accept(mode));
    }
    private static boolean fountainsHereOff() { return com.dan.heidelberg.world.Heidelberg.PROBE; }
    public boolean ready() { return ctl != null; }

    private volatile double fpsSum, msSum;
    private volatile long fpsN;

    /** Mittel der Sitzung: Bilder je Sekunde, Millisekunden je Bild, belegter Speicher in MB (für HEI_SESSION). */
    public double[] leistung() {
        long n = fpsN;
        Runtime rt = Runtime.getRuntime();
        return new double[]{n == 0 ? 0 : fpsSum / n, n == 0 ? 0 : msSum / n, (rt.totalMemory() - rt.freeMemory()) >> 20};
    }

    /** Zielpose der Kamera zum Merken: Drehpunkt x, y, z, Gier, Nick in Grad, Abstand; null, solange die Szene baut. */
    public double[] cameraPose() {
        CameraController c = ctl;
        return c == null ? null : c.pose();
    }

    /** Setzt die Kamera sofort auf eine gemerkte Pose (im Bildthread, nach allen schon gestellten Befehlen). */
    public void restoreCamera(double[] p, int vp) {
        viewpoint = Math.max(0, Math.min(Viewpoint.ALL.length - 1, vp));
        cmds.add(() -> {
            CameraController c = ctl;
            if (c == null) return;
            c.setPose(p, true);
            // Steht die Kamera noch auf dem Blickpunkt, bleibt es sein Name; sonst heißt sie „Freie Kamera“
            double[] v = Viewpoint.ALL[viewpoint].pose(scene.terrain, world == null ? null : world.solids);
            double dy = Math.abs(((p[3] - v[3]) % 360 + 540) % 360 - 180);
            freeCam = Math.abs(p[0] - v[0]) > 0.5 || Math.abs(p[1] - v[1]) > 0.5 || Math.abs(p[2] - v[2]) > 0.5 || dy > 1 || Math.abs(p[4] - v[4]) > 1 || Math.abs(p[5] - v[5]) > 1;
        });
    }
    public int day() { return day; }
    public double hour() { return hour; }

    public void goViewpoint(int i) {
        viewpoint = i;
        freeCam = false;
        cmds.add(() -> {
            CameraController c = ctl;
            if (c == null) return;
            c.setPose(Viewpoint.ALL[i].pose(scene.terrain, world == null ? null : world.solids), false);
        });
        showToast(Viewpoint.NAMES[i], 2200);
        com.dan.heidelberg.db.Dienst.ereignis("BLICKPUNKT", Viewpoint.NAMES[i], null);
        SwingUtilities.invokeLater(() -> viewListener.accept(i));
    }

    public void setOrbit(boolean on) {
        CameraController c = ctl;
        if (c != null) c.autoOrbit = on;
    }

    public void setSunTime(int day, double hour) { this.day = day; this.hour = hour; sunDirty = true; litHour = hour; }
    public void setHaze(double h) { haze = h; sunDirty = true; }
    public void setFountains(boolean on) { fountains.enabled = on; }
    /** Phase 8: Hofstaat, Dichte, Reiter und Wagen, Vögel, Kerzen in den Räumen. */
    public void setCrowd(boolean on) { World wd = world; if (wd != null) wd.life.crowd = on; }
    public void setCrowdDensity(double d) { World wd = world; if (wd != null) wd.life.density = (float) d; }
    public void setRiders(boolean on) { World wd = world; if (wd != null) wd.life.riders = on; }
    public void setBirds(boolean on) { World wd = world; if (wd != null) wd.life.birds = on; }
    public void setCandles(boolean on) { World wd = world; if (wd != null) wd.torches.candlesEnabled = on; }
    public void setFountainDensity(double d) { fountains.density = (float) d; }
    /** Talnebel: Nebeltag erzwingen und Stärke (Faktor). */
    public void setValleyFog(boolean forced, double scale) { valleyFog.forced = forced; valleyFog.scale = scale; }
    public void setSmoke(boolean on) { smoke.enabled = on; }
    /** Schneedecke von Hand setzen (0..1), etwa für die Stimmung „Schneefall“. */
    public void setSnowCover(double c) { snowPack = c; }
    public void setTorches(boolean on) {
        World wd = world; if (wd != null) wd.torches.enabled = on;
        Engine3D r = engine; if (r != null) r.lamps = on;
    }
    public void setHazeFromMood(double h) { setHaze(h); }
    /** Regenschauer vor der Sonne, damit im Gegenpunkt der Regenbogen steht (nur bei Sonne unter 42°). */
    public void setShower(boolean on) {
        showerOn = on;
        if (on && cycle.elevationDeg > 41) showToast("Die Sonne steht zu hoch für einen Regenbogen (unter 42° nötig)", 4000);
    }
    public void setTimelapse(double hoursPerSecond) { timelapse = hoursPerSecond; }
    public void setWind(double w) { wind = w; }

    public void setEffects(boolean rays, boolean bloom) {
        fxRays = rays; fxBloom = bloom;
        Engine3D r = engine;
        if (r != null) { r.rays = rays; r.bloom = bloom; }
    }

    public void setFog(double f) {
        fog = f;
        Engine3D r = engine;
        if (r != null) r.fogScale = f;
    }

    public void setWeather(int mode) {
        weather.mode = mode;
        sunDirty = true;
        showToast("Wetter: " + Weather.MODES[mode], 2000);
        SwingUtilities.invokeLater(() -> weatherListener.accept(mode));
    }

    public void setScale(double s) { scale = s; autoQuality = false; }
    public void setAutoQuality() { autoQuality = true; }
    public void setStyle(int i) { Engine3D.setStyle(i); sunDirty = true; }

    private void shiftHour(double dh) {
        double h = ((hour + dh) % 24 + 24) % 24;
        setSunTime(day, h);
        int d = day;
        SwingUtilities.invokeLater(() -> timeListener.accept(new double[]{d, h}));
    }

    private boolean fxRays = true, fxBloom = true;
    private double fog = 1;

    // ------------------------------------------------------------ Bildschleife

    /** Startet den Aufbau und die Bildschleife auf einem eigenen Thread. */
    public void start() {
        Thread t = new Thread(this::loop, "Heidelberg-Render");
        t.setDaemon(true);
        t.start();
    }

    public void stop() { running = false; }

    private void climate() {
        Thermal.setDay(day);
        double t = Climate.air(day, hour);
        airTemp = t;
        Thermal.ambient = (float) Math.max(0, t + 4);
        Thermal.snow = (float) Math.max(Climate.snow(day), snowPack);
        Thermal.rime = (float) Math.max(0, Math.min(1, (-2 - t) / 10));
    }

    private void loop() {
        Engine3D r;
        CameraController c;
        World wd;
        try {
            long t0 = System.nanoTime();
            wd = Heidelberg.build();
            Scene sc = wd.scene;
            world = wd;
            buildMs = (System.nanoTime() - t0) / 1e6;
            step = 2;
            loading = "Die Sonne steht über dem Schlossberg …";
            repaint();
            r = new Engine3D(sc, 4096);
            r.riverFlow = wd.neckar.flow;
            r.riverSurface = wd.neckar.surface;
            r.particles = ps;
            wets = com.dan.heidelberg.castle.Fountains.wetness(sc.terrain);
            r.wetness = wets;
            r.dust = wd.dust;
            smoke.attach(com.dan.heidelberg.castle.Castle.CHIMNEYS, wd.townChimneys);
            cycle.set(day, hour);
            long s0 = System.nanoTime();
            r.setSky(cycle, haze);
            shadowMs = (System.nanoTime() - s0) / 1e6;
            sunDirty = false;
            litHour = hour;
            c = new CameraController(sc.terrain);
            c.setSolids(wd.solids);
            rides = Rides.build(sc.terrain, wd.solids);
            director = new Director(stage, Director.scenes(sc.terrain, wd.solids));
            {
                String[] rn = new String[rides.size()], sn = new String[director.scenes().size()];
                for (int i = 0; i < rn.length; i++) rn[i] = rides.get(i).name;
                for (int i = 0; i < sn.length; i++) sn[i] = (i + 1) + "  " + director.scenes().get(i).name;
                String[][] names = {rn, sn};
                ridesNames = names;
                SwingUtilities.invokeLater(() -> ridesListener.accept(names));
            }
            viewpoint = Math.max(0, Math.min(Viewpoint.ALL.length - 1, Integer.getInteger("heidelberg.view", 0)));
            // Prüfhilfen: -Dheidelberg.script=Szene (ab 0) startet das Drehbuch, -Dheidelberg.ride=Nummer eine Fahrt
            final Integer autoScript = Integer.getInteger("heidelberg.script"), autoRide = Integer.getInteger("heidelberg.ride");
            if (autoScript != null) cmds.add(() -> startScriptNow(autoScript));
            else if (autoRide != null) playRide(autoRide);
            // Prüfhilfen der Phase 9: -Dheidelberg.zeit=true (Zeitraffer), .schnitt=1|2, .lupe=true, .stufe=0..3, .wasserweg=true
            if (Boolean.getBoolean("heidelberg.zeit")) toggleZeit();
            if (Integer.getInteger("heidelberg.stufe") != null) setStage(Integer.getInteger("heidelberg.stufe"));
            if (Integer.getInteger("heidelberg.schnitt") != null) setSchnitt(Integer.getInteger("heidelberg.schnitt"));
            if (Boolean.getBoolean("heidelberg.lupe")) lupe.setActive(true);
            if (Integer.getInteger("heidelberg.style") != null) setStyle(Integer.getInteger("heidelberg.style"));
            if (Boolean.getBoolean("heidelberg.cinema")) setCinema(true);
            if (Boolean.getBoolean("heidelberg.wasserweg")) Wasserweg_show();
            c.setPose(Viewpoint.ALL[viewpoint].pose(sc.terrain), true);
            scene = sc;
            engine = r; ctl = c;
            r.rays = fxRays; r.bloom = fxBloom; r.fogScale = fog;
            loading = null;
            SwingUtilities.invokeLater(onReady);
        } catch (Throwable ex) {
            loading = "Fehler beim Aufbau: " + ex;
            com.dan.heidelberg.db.Dienst.ereignis("FEHLER", "Aufbau", String.valueOf(ex));
            repaint();
            ex.printStackTrace();
            return;
        }
        Thread lw = new Thread(this::lightLoop, "Heidelberg-Licht");
        lw.setDaemon(true);
        lw.setPriority(Thread.NORM_PRIORITY - 1);
        lw.start();
        long last = System.nanoTime();
        double t = 0, fpsAcc = 0;
        int frames = 0;
        long lastStatus = 0;
        while (running) {
            long now = System.nanoTime();
            double realDt = Math.min(0.5, (now - last) / 1e9);
            double dt = Math.min(0.1, realDt);
            last = now;
            t += dt;
            // Die Uhr läuft in Echtzeit, dazu der Zeitraffer
            double tl = timelapse;
            DayNightCycle dc0 = new DayNightCycle();
            dc0.set(day, hour);
            dc0.advance(realDt / 3600.0 + dt * tl);
            day = dc0.day(); hour = dc0.hour();
            if (tl > 0 || Math.abs(hour - litHour) > 1 / 60.0) { sunDirty = true; litHour = hour; }
            int minute = (int) (hour * 60);
            if (minute != lastMinute) {
                lastMinute = minute;
                double[] v = {day, hour};
                SwingUtilities.invokeLater(() -> timeListener.accept(v));
            }
            climate();
            float wnd = (float) wind;
            // Wetter: Bewölkung ins Licht, Regen und Schnee um die Kamera, Blitz
            if (weather.step(dt, day, hour, airTemp, cam.ex, cam.ez)) {
                weather.makeBolt(scene.terrain);
                double dd = Math.hypot(weather.boltX - cam.ex, weather.boltZ - cam.ez);
                com.dan.heidelberg.castle.Klang.thunderIn(dd / 340.0, Math.min(1, dd / 3000.0));
            }
            Sky.overcastNext = weather.overcast;
            // Schneedecke: wächst im Schneefall, schmilzt über 0,5 °C mit der Zeit (auch im Zeitraffer)
            double dhSim = realDt / 3600.0 + dt * tl;
            if (weather.snow > 0.15f) snowPack = Math.min(1, snowPack + dt / 90.0 * weather.snow);
            else if (airTemp > 0.5) snowPack = Math.max(0, snowPack - dhSim * (0.05 + 0.03 * airTemp));
            if (Math.abs(weather.overcast - litOvercast) > 0.03) { litOvercast = weather.overcast; sunDirty = true; }
            weather.emit(ps, scene.terrain, cam.ex, cam.ey, cam.ez, (float) dt, (float) (0.8 * wnd * 9), (float) (0.6 * wnd * 9));
            rainWet = Math.max(0, Math.min(1, rainWet + (weather.rain > 0.15 ? dt / 90 * weather.rain : -dt / 900)));
            r.rainWet = (float) (0.8 * rainWet);
            // Teilchen in festen Schritten (Brunnen und Wetter): erst aus den Düsen, dann rechnen
            psAcc = Math.min(0.12, psAcc + dt);
            boolean fountainsHere = !com.dan.heidelberg.world.Heidelberg.PROBE;
            while (psAcc >= com.dan.heidelberg.castle.Fountains.STEP) {
                psAcc -= com.dan.heidelberg.castle.Fountains.STEP;
                if (fountainsHere && stageNow == 0) fountains.emit(ps, cam.ex, cam.ez);
                ps.step(com.dan.heidelberg.castle.Fountains.STEP, (float) (0.8 * wnd * 9), (float) (0.6 * wnd * 9), scene.terrain, wets);
            }
            wets.step((float) dt, (float) Math.max(0, Math.min(1, cycle.elevationDeg / 40)));
            r.ripples = fountains.ripples(t - fountains.clock());
            // Dampf und Licht: Talnebel, Gischtwolken, Regenschauer, Rauch, Fackeln und Leuchtfenster
            float windMsX = (float) (0.8 * wnd * 9), windMsZ = (float) (0.6 * wnd * 9);
            valleyFog.update(day, hour, cycle.elevationDeg, weather.overcast, weather.rain, wnd * 9);
            r.fogTop = valleyFog.top; r.fogAmt = valleyFog.amount; r.fogBurn = valleyFog.burn;
            r.sprays = fountainsHere && stageNow == 0 ? fountains.sprays(cam.ex, cam.ez, windMsX, windMsZ) : new float[0][];
            if (showerOn) {
                double sx = -cycle.dir[0], sz = -cycle.dir[2], sl = Math.max(1e-6, Math.hypot(sx, sz));
                r.shower = new float[]{(float) (cam.ex + sx / sl * 1250), (float) (cam.ez + sz / sl * 1250), 800f, 0.0032f, 620f};
            } else r.shower = new float[5];
            if (fountainsHere) {
                smoke.emit(ps, dt, cam.ex, cam.ez, airTemp);
                wd.torches.update(r, ps, t, dt, cam.ex, cam.ey, cam.ez, r.night());
                wd.life.update(r, t, (int) day, hour, weather.rain, cam.ex, cam.ey, cam.ez);
                if (stageNow == 0) com.dan.heidelberg.castle.Wasserweg.beads(r.sprites, t, cam.ex, cam.ey, cam.ez);
            }
            // Wasserorgel: hörbar im Raum und in der Großen Grotte, leiser mit dem Abstand; sonst stumm und ohne Rechenaufwand
            {
                double ox = cam.ex - com.dan.heidelberg.castle.Wasserorgel.X, oz = cam.ez - com.dan.heidelberg.castle.Wasserorgel.Z;
                double od = Math.sqrt(ox * ox + oz * oz + Math.pow(cam.ey - com.dan.heidelberg.castle.Wasserorgel.Y, 2));
                boolean in = Math.abs(ox) < 9 && cam.ez > 83 && cam.ez < 108 && cam.ey < 3.5;
                if (com.dan.heidelberg.castle.Wasserorgel.on && com.dan.heidelberg.castle.Klang.enabled && stageNow == 0 && (in || od < 58) && !fountainsHereOff())
                    com.dan.heidelberg.castle.Wasserorgel.listen(od, in);
                else com.dan.heidelberg.castle.Wasserorgel.silence();
            }
            r.flash = weather.flash;
            r.bolt = weather.bolt;
            r.wind = wnd;
            // Wald, Wiese, Straßen, Fluss: Jahreszeit, Wind, Wetter
            wd.grove.setSeason(day, Thermal.snow);
            wd.grove.update(dt, wnd, r.windX, r.windZ, cam.ex, cam.ez, scene.terrain);
            r.leaves = wd.grove.quads;
            wd.sward.update((float) t, day, Thermal.snow, wnd, r.windX, r.windZ, null, cam.ex, cam.ey, cam.ez);
            r.foliage = wd.sward.meadow.batch;
            wd.roadways.update((float) t, dt, day, weather.rain, Thermal.snow, com.dan.heidelberg.world.Roadways.dark(cycle.elevationDeg),
                    wnd, r.windX, r.windZ, null, 1, cam.ex, cam.ey, cam.ez);
            r.roads = wd.roadways.roads;
            wd.neckar.update(dt, wnd, r.windX, r.windZ, cam.ex, cam.ez);
            r.floats = wd.neckar.quads;

            int pw = Math.max(1, getWidth()), ph = Math.max(1, getHeight());
            int[] pr = pickRequest;
            if (pr != null && r.width() > 0) {
                pickRequest = null;
                c.focus(r.pick((int) (pr[0] * r.width() / (double) pw), bandY(pr[1], r.height(), pw, ph)));
            }
            int[] lr = lupeRequest;
            if (lr != null && r.width() > 0) {
                lupeRequest = null;
                int mat = r.pickMat((int) (lr[0] * r.width() / (double) pw), bandY(lr[1], r.height(), pw, ph));
                com.dan.heidelberg.lupe.Stoffe.Stoff st = com.dan.heidelberg.lupe.Stoffe.fuer(mat);
                if (st != null) lupe.show(st, lr[0], lr[1], System.nanoTime() / 1e9);
                else { lupe.clear(); showToast("Hier gibt es nichts zu untersuchen (Himmel oder Licht)", 2200); }
            }
            for (Runnable cmd; (cmd = cmds.poll()) != null; ) {
                if (c.autoOrbit) { c.autoOrbit = false; SwingUtilities.invokeLater(() -> orbitListener.accept(false)); }
                cmd.run();
            }
            Director dr = director;
            if (dr != null) dr.update(dt);
            if (zeit.active()) {
                zeit.update(dt, r, ps, cam.ex, cam.ey, cam.ez);
                if (!zeit.active()) SwingUtilities.invokeLater(() -> zeitListener.accept(false));
            }
            c.update(dt, cam);

            // Qualität: fest oder automatisch (30 Bilder/s); im Stillstand mit voller Auflösung
            cam.update();
            boolean moved = Math.abs(cam.ex - lastCam[0]) + Math.abs(cam.ey - lastCam[1]) + Math.abs(cam.ez - lastCam[2])
                    + Math.abs(cam.fx - lastCam[3]) + Math.abs(cam.fy - lastCam[4]) + Math.abs(cam.fz - lastCam[5]) > 1e-4;
            lastCam[0] = cam.ex; lastCam[1] = cam.ey; lastCam[2] = cam.ez; lastCam[3] = cam.fx; lastCam[4] = cam.fy; lastCam[5] = cam.fz;
            stillFor = moved ? 0 : stillFor + realDt;
            double sc = scale;
            if (autoQuality) {
                boolean still = stillFor > 0.7;
                boolean cold = still && r.cacheHit < 0.5;
                if (lastWasAuto && still == lastWasSharp && !cold) {
                    if (still) {
                        if (loopMs > 1000 / 30.5) autoStill = Math.max(0.6, autoStill * 0.96);
                        else if (loopMs < 1000 / 40.0) autoStill = Math.min(1.0, autoStill * 1.03);
                    } else {
                        if (loopMs > 1000 / 31.0) autoScale = Math.max(0.42, autoScale * 0.96);
                        else if (loopMs < 1000 / 46.0) autoScale = Math.min(1.0, autoScale * 1.03);
                    }
                }
                sc = Math.round((still ? autoStill : autoScale) * 25) / 25.0;
                lastWasSharp = still;
            }
            lastWasAuto = autoQuality;
            int bandH = cinema ? Math.min(ph, (int) Math.round(pw / 2.39)) : ph;
            r.cropY = bandH / (double) ph;
            int w = Math.max(64, (int) (pw * sc)), h = Math.max(48, (int) (bandH * sc));
            r.setSize(w, h);
            r.day = day; r.hour = hour; r.sidereal = cycle.siderealDeg;
            long r0 = System.nanoTime();
            shown = r.render(cam, t, dt);
            renderMs = (System.nanoTime() - r0) / 1e6;
            if (BENCH) bench(r, w, h, (r0 - now) / 1e6);
            if (stillRequest) { stillRequest = false; saveStill(r); r.setSize(w, h); }
            repaint();
            frames++;
            totalFrames++;
            if (DUMP != null && totalFrames == DUMP_AT) {
                try { javax.imageio.ImageIO.write(shown, "png", new java.io.File(DUMP)); } catch (java.io.IOException ex) { ex.printStackTrace(); }
                System.out.printf("Bild %d: %d x %d, Belichtung %.3f%n", totalFrames, shown.getWidth(), shown.getHeight(), r.exposure());
            }
            fpsAcc += realDt;
            if (fpsAcc > 0.5) {
                fps = frames / fpsAcc; frames = 0; fpsAcc = 0;
                if (fps > 0) { fpsSum += fps; msSum += renderMs; fpsN++; }
            }
            if (now - lastStatus > 250_000_000L) {
                lastStatus = now;
                Runtime rt = Runtime.getRuntime();
                long mb = (rt.totalMemory() - rt.freeMemory()) >> 20;
                double camH = Terrain.DATUM + cam.ey;
                String s = String.format(Locale.GERMANY, "%s  ·  Kamera %,.0f m ü. NN  ·  %,d Dreiecke, %,d im Bild  ·  %,d Teilchen  ·  %d × %d%s  ·  %.0f Bilder/s, %.0f ms je Bild%s  ·  Aufbau %.1f s  ·  %d MB",
                        skyText(), camH, scene.triangles(), r.drawnTris, r.drawnParticles,
                        w, h, autoQuality ? " (Auto)" : "", fps, renderMs,
                        r.cacheHit > 0.05 ? String.format(Locale.GERMANY, ", Licht zu %.0f %% behalten", r.cacheHit * 100) : "",
                        buildMs / 1000, mb);
                SwingUtilities.invokeLater(() -> status.accept(s));
            }
            long spent = System.nanoTime() - now;
            loopMs = spent / 1e6;
            long wait = Math.max(1, (16_000_000L - spent) / 1_000_000L);
            try { Thread.sleep(wait); } catch (InterruptedException e) { return; }
        }
    }

    /** Prüfhilfe: -Dheidelberg.bench=true gibt alle 240 Bilder die mittleren Zeiten der Teilschritte aus. */
    private static final boolean BENCH = Boolean.getBoolean("heidelberg.bench");
    private final double[] bAcc = new double[16];
    private int bN;

    private void bench(Engine3D r, int w, int h, double preMs) {
        double[] v = {preMs, renderMs, r.msSetup, r.msRaster, r.msShade, r.msPost, r.msFx, r.msRays, r.msParticles, r.msBloom, w * h, r.drawnTris, r.msPre, r.msTransform, r.msSetupTri, r.msBin};
        for (int i = 0; i < v.length; i++) bAcc[i] += v[i];
        if (++bN < 240) return;
        System.out.printf(Locale.ROOT, "BENCH %dx%d Dreiecke %.0f | Simulation %.1f | Bild %.1f = Aufbau %.1f + Raster %.1f + Licht %.1f + Nachbild %.1f (davon Effekte %.1f: Strahlen %.1f, Teilchen %.1f, Bloom %.1f) | Aufbau: Tabellen %.1f, Ecken %.1f, Dreiecke %.1f, Streifen %.1f%n",
                w, h, bAcc[11] / bN, bAcc[0] / bN, bAcc[1] / bN, bAcc[2] / bN, bAcc[3] / bN, bAcc[4] / bN, bAcc[5] / bN, bAcc[6] / bN, bAcc[7] / bN, bAcc[8] / bN, bAcc[9] / bN, bAcc[12] / bN, bAcc[13] / bN, bAcc[14] / bN, bAcc[15] / bN);
        java.util.Arrays.fill(bAcc, 0);
        bN = 0;
    }

    private double rainWet;
    private volatile double snowPack;
    private int totalFrames;
    /** Prüfhilfe: -Dheidelberg.dump=datei.png schreibt das Bild Nr. heidelberg.dumpAt (Standard 150) und gibt die Belichtung aus. */
    private static final String DUMP = System.getProperty("heidelberg.dump");
    private static final int DUMP_AT = Integer.getInteger("heidelberg.dumpAt", 150);

    /** Sonnenstand oder Mond als Text, immer aus dem Stand des Himmels. */
    private String skyText() {
        String zone = DayNightCycle.zone(day, hour);
        return cycle.elevationDeg > -4
                ? String.format(Locale.GERMANY, "Sonne %s %s, Höhe %.0f°, Richtung %.0f°", DayNightCycle.timeLabel(hour), zone, cycle.elevationDeg, cycle.azimuthDeg)
                : String.format(Locale.GERMANY, "%s %s, Mond %.0f° hoch, %s", DayNightCycle.timeLabel(hour), zone, cycle.moonElevationDeg, cycle.moonLabel());
    }

    private void lightLoop() {
        DayNightCycle dc = new DayNightCycle();
        while (running) {
            Engine3D r = engine;
            if (r == null || !sunDirty) { sleep(8); continue; }
            synchronized (lightLock) {
                LightingEngine spare = r.takeSpare();
                if (spare == null) { sleep(4); continue; }
                sunDirty = false;
                dc.set(day, hour);
                long t0 = System.nanoTime();
                spare.compute(r.mesh(), dc.dir, dc.moonDir, dc.moonLit, Math.min(1, haze + 0.3 * weather.overcast));
                shadowMs = (System.nanoTime() - t0) / 1e6;
                cycle.set(dc.day(), dc.hour());
                r.offer(spare);
            }
            if (DUMP != null) System.out.printf("Licht: Tag %d, %.2f h, Sonne %.1f°, %.0f ms, Dunst %.2f%n", dc.day(), dc.hour(), dc.elevationDeg, shadowMs, haze);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // ------------------------------------------------------------ Qualität, Standbild, Kinomodus

    private final double[] lastCam = new double[6];
    private double stillFor, autoScale = 0.8, autoStill = 1.0;
    private boolean lastWasAuto, lastWasSharp;
    private volatile double scale = Math.max(0.1, Math.min(1, Double.parseDouble(System.getProperty("heidelberg.scale", "1"))));
    private volatile boolean autoQuality = Double.parseDouble(System.getProperty("heidelberg.scale", "0")) <= 0, stillRequest, cinema, help;
    private volatile String toast;
    private volatile long toastUntil;
    private Consumer<Boolean> cinemaListener = b -> { };

    public void setCinemaListener(Consumer<Boolean> l) { cinemaListener = l; }

    /** Kinomodus: Bedienfeld und Statuszeile verschwinden, Breitbild-Balken, keine Hinweise. */
    public void setCinema(boolean on) {
        if (on && !cinema) {
            cinemaSince = System.currentTimeMillis();
            CameraController c = ctl;
            if (c != null && !c.autoOrbit) {
                c.autoOrbit = true;
                SwingUtilities.invokeLater(() -> orbitListener.accept(true));
            }
        }
        cinema = on;
        Engine3D.VIGNETTE = on ? 0.5f : 0;
        mouseSeen();
        SwingUtilities.invokeLater(() -> cinemaListener.accept(on));
    }

    private volatile long cinemaSince, lastMouse = System.currentTimeMillis();
    private boolean cursorHidden;
    private static final java.awt.Cursor BLANK = java.awt.Toolkit.getDefaultToolkit().createCustomCursor(
            new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), new java.awt.Point(0, 0), "leer");

    private void mouseSeen() {
        lastMouse = System.currentTimeMillis();
        if (cursorHidden) { cursorHidden = false; setCursor(java.awt.Cursor.getDefaultCursor()); }
    }

    private void cinemaCursor() {
        boolean hide = cinema && System.currentTimeMillis() - lastMouse > 2000;
        if (hide != cursorHidden) { cursorHidden = hide; setCursor(hide ? BLANK : java.awt.Cursor.getDefaultCursor()); }
    }

    private void cinemaTitle(Graphics2D g) {
        double t = (System.currentTimeMillis() - cinemaSince) / 1000.0;
        if (t > 6) return;
        float a = (float) Math.max(0, Math.min(1, Math.min(t / 1.2, (6 - t) / 1.5)));
        if (a <= 0.01f) return;
        int W = getWidth(), H = getHeight();
        int bar = (int) Math.max(0, (H - W / 2.39) / 2);
        java.awt.Composite old = g.getComposite();
        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, a));
        String t1 = "HEIDELBERGER SCHLOSS", t2 = Viewpoint.NAMES[viewpoint] + " · " + DayNightCycle.dateLabel(day) + " " + DayNightCycle.YEAR;
        g.setFont(new Font("SansSerif", Font.BOLD, 36));
        int y = H - bar - 70;
        g.setColor(new Color(0, 0, 0, 90));
        g.drawString(t1, 42, y + 2);
        g.setColor(INK);
        g.drawString(t1, 40, y);
        g.setFont(new Font("SansSerif", Font.PLAIN, 16));
        g.setColor(GOLD);
        g.drawString(t2, 42, y + 28);
        g.setComposite(old);
    }

    public void requestStill() { stillRequest = true; showToast("Standbild wird gerechnet …", 30000); com.dan.heidelberg.db.Dienst.ereignis("STANDBILD", null, null); }

    private void showToast(String s, long ms) { toast = s; toastUntil = System.currentTimeMillis() + ms; }

    private int bandY(int my, int rh, int pw, int ph) {
        int band = cinema ? Math.min(ph, (int) Math.round(pw / 2.39)) : ph;
        int top = (ph - band) / 2;
        return (int) Math.max(0, Math.min(rh - 1, (my - top) * rh / (double) band));
    }

    /** Standbild doppelt so groß (höchstens 3840 Pixel breit) unter Bilder/Heidelberg. */
    private void saveStill(Engine3D r) {
        try {
            int pw = Math.max(1, getWidth()), ph = Math.max(1, getHeight());
            double k = Math.min(2.0, 3840.0 / pw);
            int w = (int) (pw * k), h = (int) (ph * k);
            double crop = r.cropY;
            r.cropY = 1;
            r.setSize(w, h);
            r.resetExposure();
            r.render(cam, 0, 0);
            BufferedImage img = r.render(cam, 0, 0);
            r.resetExposure();
            r.cropY = crop;
            if (cinema) {
                int ch = Math.min(h, (int) Math.round(w / 2.39));
                BufferedImage c = new BufferedImage(w, ch, BufferedImage.TYPE_INT_RGB);
                Graphics2D cg = c.createGraphics();
                cg.drawImage(img, 0, -(h - ch) / 2, null);
                cg.dispose();
                img = c;
                h = ch;
            }
            java.io.File dir = new java.io.File(System.getProperty("user.home"), "Pictures");
            dir = dir.isDirectory() ? new java.io.File(dir, "Heidelberg") : new java.io.File("standbilder");
            dir.mkdirs();
            String name = "Heidelberg_" + java.time.LocalDateTime.now().withNano(0).toString().replace(':', '-').replace('T', '_') + ".png";
            java.io.File f = new java.io.File(dir, name);
            javax.imageio.ImageIO.write(img, "png", f);
            showToast("Standbild gespeichert: " + f.getAbsolutePath() + "  (" + w + " × " + h + ")", 6000);
        } catch (Exception e) {
            showToast("Standbild nicht gespeichert: " + e.getMessage(), 6000);
        }
    }

    // ------------------------------------------------------------ Zeichnen

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        cinemaCursor();
        Graphics2D g = (Graphics2D) g0.create();
        int W = getWidth(), H = getHeight();
        BufferedImage img = shown;
        String l = loading;
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (img != null && l == null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            int band = cinema && img.getWidth() > 1.9 * img.getHeight() ? Math.min(H, (int) Math.round(W / 2.39)) : H;
            g.drawImage(img, 0, (H - band) / 2, W, band, null);
            float fb = zeit.fadeBlack(), fw = zeit.fadeWhite();
            if (fb > 0.004f) { g.setColor(new Color(0, 0, 0, Math.min(255, (int) (255 * fb)))); g.fillRect(0, 0, W, H); }
            if (fw > 0.004f) { g.setColor(new Color(255, 244, 226, Math.min(255, (int) (255 * fw)))); g.fillRect(0, 0, W, H); }
            if (cinema) {
                int bar = (int) Math.max(0, (H - W / 2.39) / 2);
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, W, bar);
                g.fillRect(0, H - bar, W, bar);
                cinemaTitle(g);
                cardHud(g, W, H, H - (int) Math.max(0, (H - W / 2.39) / 2) - 28);
            } else {
                hud(g);
                cardHud(g, W, H, H - 56);
            }
            lupe.paint(g, W, H, System.nanoTime() / 1e9);
            if (help) helpHud(g);
            if (toast != null && System.currentTimeMillis() < toastUntil) {
                g.setFont(new Font("SansSerif", Font.PLAIN, 13));
                int tw = g.getFontMetrics().stringWidth(toast);
                g.setColor(new Color(0, 0, 0, 170));
                g.fillRoundRect((W - tw) / 2 - 16, H / 2 - 20, tw + 32, 36, 10, 10);
                g.setColor(INK);
                g.drawString(toast, (W - tw) / 2, H / 2 + 3);
            }
        } else {
            startScreen(g, W, H, l);
        }
        g.dispose();
    }

    /** Startbildschirm: Titel, Schritt, Balken; dahinter aufsteigender Nebel überm Neckar. */
    private void startScreen(Graphics2D g, int W, int H, String l) {
        g.setPaint(new GradientPaint(0, 0, new Color(22, 36, 56), 0, H, new Color(8, 10, 14)));
        g.fillRect(0, 0, W, H);
        long ms = System.currentTimeMillis();
        for (int i = 0; i < 14; i++) {
            double ph = ((ms / 1000.0) * 0.06 + i / 14.0) % 1;
            int r = (int) (60 + 180 * ph);
            int cx = W / 2 + (int) (Math.sin(i * 1.7 + ph * 3) * 90 * ph + (i - 7) * 60);
            int cy = (int) (H * 0.74 - ph * H * 0.3);
            g.setColor(new Color(220, 226, 232, (int) (26 * (1 - ph))));
            g.fillOval(cx - r, cy - r / 3, 2 * r, 2 * r / 3);
        }
        g.setColor(INK);
        g.setFont(new Font("SansSerif", Font.BOLD, 40));
        String title = "HEIDELBERGER SCHLOSS";
        g.drawString(title, (W - g.getFontMetrics().stringWidth(title)) / 2, H / 2 - 14);
        g.setFont(new Font("SansSerif", Font.PLAIN, 15));
        g.setColor(GOLD);
        String sub = "Rekonstruktion in 3D · Jettenbühl, Neckartal";
        g.drawString(sub, (W - g.getFontMetrics().stringWidth(sub)) / 2, H / 2 + 12);
        g.setColor(MUTED);
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        String s = l == null ? "" : l;
        g.drawString(s, (W - g.getFontMetrics().stringWidth(s)) / 2, H / 2 + 40);
        if (l != null && !l.startsWith("Fehler")) {
            int n = STEPS.length, cw = 190, bx = (W - n * cw) / 2, by = H / 2 + 64, cur = step;
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            for (int i = 0; i < n; i++) {
                int x = bx + i * cw;
                boolean done = i + 1 < cur, now = i + 1 == cur;
                g.setColor(new Color(255, 255, 255, 40));
                g.fillRoundRect(x + 6, by, cw - 12, 5, 5, 5);
                if (done || now) {
                    g.setColor(done ? WATER : new Color(104, 164, 206, 150));
                    int fw = done ? cw - 12 : (int) ((cw - 12) * (0.35 + 0.3 * Math.sin(ms / 300.0)));
                    g.fillRoundRect(x + 6, by, fw, 5, 5, 5);
                }
                String t = (i + 1) + "  " + STEPS[i];
                g.setColor(done ? INK : now ? WATER : new Color(120, 128, 132));
                g.drawString(t, x + (cw - g.getFontMetrics().stringWidth(t)) / 2, by + 24);
            }
            javax.swing.Timer tm = pulse;
            if (tm == null) {
                pulse = tm = new javax.swing.Timer(60, e -> { if (loading == null) ((javax.swing.Timer) e.getSource()).stop(); repaint(); });
                tm.start();
            }
        }
    }

    private void hud(Graphics2D g) {
        CameraController cr = ctl;
        Director dd = director;
        String where = zeit.active() ? "Zeitraffer der Zerstörung" : dd != null && dd.active() ? dd.scenes().get(dd.sceneIndex()).name : cr != null && cr.riding ? cr.rideName : freeCam ? "Freie Kamera" : Viewpoint.NAMES[viewpoint];
        String place = "HEIDELBERGER SCHLOSS  ·  " + where.toUpperCase(Locale.ROOT);
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        int w = g.getFontMetrics().stringWidth(place);
        g.setColor(new Color(0, 0, 0, 90));
        g.fillRoundRect(14, 14, w + 28, 38, 10, 10);
        g.setColor(INK);
        g.drawString(place, 28, 40);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        String sub = DayNightCycle.dateLabel(day) + " " + (zeit.active() ? zeit.year() : String.valueOf(DayNightCycle.YEAR)) + (stageNow > 0 && !zeit.active() ? " (Zeitstufe " + com.dan.heidelberg.castle.Ruin.STAGE_NAMES[stageNow] + ")" : "") + "  ·  " + DayNightCycle.timeLabel(hour) + " "
                + DayNightCycle.zone(day, hour) + "  ·  " + weather.label + String.format(Locale.GERMANY, ", %.0f °C", airTemp)
                + "  ·  49,41° N  8,72° O";
        int sw = g.getFontMetrics().stringWidth(sub);
        g.setColor(new Color(0, 0, 0, 80));
        g.fillRoundRect(14, 58, sw + 24, 24, 8, 8);
        g.setColor(GOLD);
        g.drawString(sub, 26, 75);
        String sun = cycle.elevationDeg > -4
                ? String.format(Locale.GERMANY, "Sonne  Höhe %.0f°  ·  Richtung %.0f°", cycle.elevationDeg, cycle.azimuthDeg)
                : String.format(Locale.GERMANY, "Mond  Höhe %.0f°  ·  %s", cycle.moonElevationDeg, cycle.moonLabel());
        int uw = g.getFontMetrics().stringWidth(sun);
        g.setColor(new Color(0, 0, 0, 80));
        g.fillRoundRect(14, 86, uw + 24, 24, 8, 8);
        g.setColor(INK);
        g.drawString(sun, 26, 103);
        {
            CameraController c0 = ctl;
            Director d0 = director;
            String rl = null;
            if (schnittNow > 0) rl = "Schnitt " + com.dan.heidelberg.castle.Schnitte.NAMES[schnittNow - 1] + "  ·  Maus: Ansicht drehen · X: nächste Ebene";
            else if (zeit.active()) rl = zeit.status() + (zeit.paused() ? "  ·  Pause" : "") + "  ·  Leertaste: Pause · Esc oder Z: beenden";
            else if (d0 != null && d0.active()) rl = d0.status() + (d0.paused() ? "  ·  Pause" : "") + "  ·  Leertaste: Pause · N: nächste Szene · Esc: beenden";
            else if (c0 != null && c0.riding) rl = "Fahrt: " + c0.rideName + String.format(Locale.GERMANY, "  ·  %.0f %%", c0.rideFrac * 100) + "  ·  Esc oder Maus: beenden";
            if (rl == null && com.dan.heidelberg.castle.Wasserweg.show && stageNow == 0) {
                String stn = com.dan.heidelberg.castle.Wasserweg.station(cam.ex, cam.ey, cam.ez);
                if (stn != null) rl = "Wasserweg  ·  " + stn;
            }
            if (rl != null) {
                int rw = g.getFontMetrics().stringWidth(rl);
                g.setColor(new Color(0, 0, 0, 130));
                g.fillRoundRect(14, 114, rw + 24, 24, 8, 8);
                g.setColor(new Color(150, 204, 238));
                g.drawString(rl, 26, 131);
            }
        }
        String hint = "Ziehen: drehen · Rechts ziehen: verschieben · Rad: Zoom · Doppelklick: Drehpunkt · 1–9, G: Blickpunkte · + −: Uhrzeit · F1: Tasten";
        int hw = g.getFontMetrics().stringWidth(hint);
        g.setColor(new Color(0, 0, 0, 80));
        g.fillRoundRect(14, getHeight() - 36, hw + 20, 24, 8, 8);
        g.setColor(INK);
        g.drawString(hint, 24, getHeight() - 19);
    }

    /** Tafel des Drehbuchs unten links: Kopf, Text, Quelle. bottom ist die Unterkante. */
    private void cardHud(Graphics2D g, int W, int H, int bottom) {
        Director d = director;
        if (!showTafeln) return;
        Director.Card cd = zeit.active() ? zeit.card() : d == null ? null : d.card();
        if (cd == null || cd.alpha <= 0.01f) return;
        int bw = Math.min(680, Math.max(300, W / 2));
        Font fk = new Font("SansSerif", Font.BOLD, 19), ft = new Font("SansSerif", Font.PLAIN, 14), fq = new Font("SansSerif", Font.ITALIC, 11);
        java.util.List<String> lines = new java.util.ArrayList<>();
        g.setFont(ft);
        java.awt.FontMetrics fm = g.getFontMetrics();
        StringBuilder cur = new StringBuilder();
        for (String w : cd.text.split(" ")) {
            if (cur.length() > 0 && fm.stringWidth(cur + " " + w) > bw - 40) { lines.add(cur.toString()); cur.setLength(0); }
            if (cur.length() > 0) cur.append(' ');
            cur.append(w);
        }
        if (cur.length() > 0) lines.add(cur.toString());
        int lh = 20, bh = 18 + 26 + lines.size() * lh + 8 + 20 + 10;
        int x = 28, y = bottom - bh;
        java.awt.Composite old = g.getComposite();
        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, cd.alpha));
        g.setColor(new Color(6, 10, 14, 196));
        g.fillRoundRect(x, y, bw, bh, 12, 12);
        g.setColor(GOLD);
        g.fillRect(x, y + 12, 3, bh - 24);
        g.setFont(fk);
        g.setColor(GOLD);
        g.drawString(cd.kopf, x + 20, y + 32);
        g.setFont(ft);
        g.setColor(INK);
        int ty = y + 32 + 24;
        for (String ln : lines) { g.drawString(ln, x + 20, ty); ty += lh; }
        g.setFont(fq);
        g.setColor(MUTED);
        g.drawString("Quelle: " + cd.quelle, x + 20, ty + 6);
        g.setComposite(old);
    }

    private void helpHud(Graphics2D g) {
        String[][] rows = {
                {"KAMERA", null},
                {"Maus ziehen", "drehen"}, {"rechts ziehen", "verschieben"}, {"Mausrad", "Zoom"},
                {"Doppelklick", "neuer Drehpunkt"}, {"W A S D, Pfeile", "Drehpunkt bewegen"}, {"Q E", "tiefer, höher"},
                {"Umschalt", "schneller"}, {"Leertaste", "Rundflug (Orbit)"}, {"0 oder R", "Übersicht"},
                {"1 bis 9", "Blickpunkte"}, {"G", "nächster Blickpunkt"},
                {"V", "nächste Fahrt"}, {"B", "Drehbuch an, aus"}, {"N", "nächste Szene"}, {"Leertaste", "Pause im Drehbuch"},
                {"SONNE", null},
                {"+ und −", "½ Stunde vor, zurück"}, {"Y", "Wetter wechseln"},
                {"BILD", null},
                {"P", "Standbild speichern"}, {"K oder F11", "Kinomodus"}, {"F1 oder H", "diese Übersicht"}, {"Esc", "Fahrt beenden, schließen"}};
        int W = getWidth(), H = getHeight();
        int perCol = (rows.length + 1) / 2;
        int bw = 640, bh = 56 + perCol * 20 + 30;
        int x = (W - bw) / 2, y = Math.max(20, (H - bh) / 2);
        g.setColor(new Color(8, 14, 18, 215));
        g.fillRoundRect(x, y, bw, bh, 14, 14);
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        g.setColor(INK);
        g.drawString("Tasten", x + 24, y + 34);
        for (int i = 0; i < rows.length; i++) {
            int cx = x + 24 + (i / perCol) * (bw / 2), cy = y + 62 + (i % perCol) * 20;
            if (rows[i][1] == null) {
                g.setFont(new Font("SansSerif", Font.BOLD, 11));
                g.setColor(GOLD);
                g.drawString(rows[i][0], cx, cy);
            } else {
                g.setFont(new Font("SansSerif", Font.BOLD, 12));
                g.setColor(INK);
                g.drawString(rows[i][0], cx, cy);
                g.setFont(new Font("SansSerif", Font.PLAIN, 12));
                g.setColor(MUTED);
                g.drawString(rows[i][1], cx + 112, cy);
            }
        }
        g.setFont(new Font("SansSerif", Font.ITALIC, 11));
        g.setColor(MUTED);
        g.drawString("Farbstil und Qualität stehen im Bedienfeld unter BILD.", x + 24, y + bh - 16);
    }
}
