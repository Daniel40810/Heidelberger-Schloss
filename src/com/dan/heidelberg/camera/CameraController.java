package com.dan.heidelberg.camera;

import com.dan.heidelberg.core.Camera;
import com.dan.heidelberg.core.Solids;
import com.dan.heidelberg.core.Terrain;

/**
 * Kamerasteuerung: Die Kamera kreist um einen Drehpunkt (Orbit mit Zoom). Maus dreht und
 * verschiebt, das Rad zoomt von der Totale bis nah heran, W A S D schieben den Drehpunkt
 * über das Gelände, Q und E heben und senken ihn. Alle Werte gleiten weich nach
 * (Ease-out), damit keine Bewegung ruckt. Aus Semiramis übernommen; neu ist der Boden: Kamera und
 * Drehpunkt bleiben über dem Gelände, das hier nicht eben ist. Mit {@link #adopt} übernimmt die
 * Steuerung die Kamera dort, wo eine Fahrt sie gelassen hat (Regie ab Phase 6).
 */
public final class CameraController {
    public static final int K_W = 0, K_A = 1, K_S = 2, K_D = 3, K_Q = 4, K_E = 5, K_SHIFT = 6;

    /** Übersicht: aus Nordwesten über das Neckartal auf Altstadt und Schloss. */
    static final double HOME_X = -250, HOME_Z = -250, HOME_YAW = Math.toRadians(226), HOME_PITCH = Math.toRadians(15), HOME_DIST = 2300;
    public static final double MAX_DIST = 14000;

    private final Terrain terrain;
    /** Feste Körper (Mauern, Dächer, Bäume): Kamera und Drehpunkt gehen nicht hindurch (ab Phase 7); null = aus. */
    private volatile Solids solids;
    /** Zahl der Eingriffe des Kollisionsschutzes seit dem Start (für die Prüfung). */
    public volatile int guardHits;
    public static boolean DEBUG = Boolean.getBoolean("heidelberg.camdebug");
    public static final double R_EYE = 0.5, R_PIVOT = 0.4;
    /** Das Auge, wie es wirklich steht (gleitet an Hindernissen entlang); eyeValid = false: wieder aufs Soll setzen. */
    private final double[] eye = new double[3];
    private boolean eyeValid;
    private final double[] lastFinal = new double[3];
    private boolean lastFinalValid;
    // Fahrt (Shot) und ihr Ende
    private Shot ride;
    private double rideT, rideSpeed = 1;
    private Runnable rideDone;
    private boolean adoptPending;
    /** Läuft gerade eine Fahrt? */
    public volatile boolean riding;
    /** Name und Zeit der laufenden Fahrt (für die Anzeige), Eingriffe des Schutzes während der Fahrt. */
    public volatile String rideName = "";
    public volatile double rideFrac;
    public volatile int rideHits;
    /** Dauer des Anflugs zum Anfang der Fahrt (bei Tempo 1) und Fahrtzeit seit dem Start; zählt die Abbrüche durch den Benutzer. */
    public volatile double rideLead, rideTime;
    public volatile int rideCancels;
    private double lastFov = CameraPath.FOV;
    private final double[] pose7 = new double[7];

    // Ziel- und aktuelle Werte (weich nachgeführt)
    private double gtx = HOME_X, gty = 0, gtz = HOME_Z, gyaw = HOME_YAW, gpitch = HOME_PITCH, gdist = HOME_DIST;
    private double tx = gtx, ty = gty, tz = gtz, yaw = gyaw - 0.6, pitch = gpitch + 0.3, dist = 6500;

    public void setSolids(Solids s) { solids = s; eyeValid = false; }

    public CameraController(Terrain t) {
        terrain = t;
        gty = ty = t.sample(HOME_X, HOME_Z) + 20;
    }
    private final boolean[] keys = new boolean[7];

    public volatile boolean autoOrbit;

    public synchronized void setKey(int k, boolean down) { if (k >= 0 && k < keys.length) keys[k] = down; }

    public synchronized void releaseKeys() { java.util.Arrays.fill(keys, false); }

    public synchronized void drag(double dx, double dy, boolean pan) {
        cancelRide();
        if (pan) {
            double k = gdist * 0.0016;
            double sx = Math.cos(gyaw), sz = -Math.sin(gyaw);
            double fx = -Math.sin(gyaw), fz = -Math.cos(gyaw);
            gtx += (-dx * sx + dy * fx) * k;
            gtz += (-dx * sz + dy * fz) * k;
        } else {
            gyaw -= dx * 0.006;
            gpitch = clamp(gpitch + dy * 0.0045, Math.toRadians(-35), Math.toRadians(89));
        }
    }

    public synchronized void wheel(double notches) {
        cancelRide();
        gdist = clamp(gdist * Math.pow(1.13, notches), 3, MAX_DIST);
    }

    /** Doppelklick: neuer Drehpunkt, etwas näher heran. */
    public synchronized void focus(double[] p) {
        if (p == null) return;
        cancelRide();
        gtx = p[0]; gty = p[1] + 1; gtz = p[2];
        gdist = Math.max(12, gdist * 0.6);
    }

    /** Zurück zur Übersicht über das Tal. */
    public synchronized void goOverview() {
        glideTo(new double[]{HOME_X, terrain.sample(HOME_X, HOME_Z) + 20, HOME_Z, Math.toDegrees(HOME_YAW), Math.toDegrees(HOME_PITCH), HOME_DIST});
    }

    /** Gleitet zu einem Punkt: Drehpunkt dort, Blick aus Richtung yawDeg (NaN: Richtung bleibt), Neigung und Abstand. */
    public synchronized void flyTo(double x, double y, double z, double yawDeg, double pitchDeg, double d) {
        glideTo(new double[]{x, y, z, Double.isNaN(yawDeg) ? Math.toDegrees(gyaw) : yawDeg, pitchDeg, d});
    }

    private double nearestYaw(double target) {
        double d = target - gyaw;
        d -= Math.round(d / (2 * Math.PI)) * 2 * Math.PI;
        return gyaw + d;
    }

    public synchronized double distance() { return dist; }

    /** Zielpose zum Merken: Drehpunkt x, y, z, Gier und Nick in Grad, Abstand. */
    public synchronized double[] pose() {
        return new double[]{gtx, gty, gtz, Math.toDegrees(gyaw), Math.toDegrees(gpitch), gdist};
    }

    /** Auge und Drehpunkt, wie sie jetzt stehen: ex, ey, ez, tx, ty, tz. */
    public synchronized double[] current() {
        double cp = Math.cos(pitch);
        double ex = eyeValid ? eye[0] : tx + dist * cp * Math.sin(yaw), ey = eyeValid ? eye[1] : ty + dist * Math.sin(pitch), ez = eyeValid ? eye[2] : tz + dist * cp * Math.cos(yaw);
        return new double[]{ex, ey, ez, tx, ty, tz};
    }

    /** Das Auge, das zu einer Pose (Drehpunkt, Gier, Nick in Grad, Abstand) gehört, mit Bodenabstand. */
    public double[] eyeOf(double[] p) {
        double yw = Math.toRadians(p[3]), pt = Math.toRadians(clamp(p[4], -35, 89)), d = clamp(p[5], 3, MAX_DIST), cp = Math.cos(pt);
        double ex = p[0] + d * cp * Math.sin(yw), ey = p[1] + d * Math.sin(pt), ez = p[2] + d * cp * Math.cos(yw);
        double ground = terrain.stand(ex, ez, p[1]) + 1.7;
        return new double[]{ex, Math.max(ey, ground), ez};
    }

    /** Setzt die Zielpose; mit jump sofort, sonst gleitet die Kamera hin (als hindernisfreier Flug). */
    public synchronized void setPose(double[] p, boolean jump) {
        if (!jump) { glideTo(p); return; }
        cancelRide();
        adoptPending = false;
        gtx = p[0]; gty = p[1]; gtz = p[2];
        gyaw = Math.toRadians(p[3]);
        gpitch = clamp(Math.toRadians(p[4]), Math.toRadians(-35), Math.toRadians(89));
        gdist = clamp(p[5], 3, MAX_DIST);
        tx = gtx; ty = gty; tz = gtz; yaw = gyaw; pitch = gpitch; dist = gdist;
        eyeValid = false; lastFinalValid = false;
    }

    /**
     * Übernimmt die Kamera an Ort und Stelle: Drehpunkt im Abstand d vor dem Auge, Richtung und
     * Abstand so, dass das nächste Bild genau dasselbe zeigt. Kein Sprung, kein Nachgleiten.
     */
    public synchronized void adopt(Camera c, double d) {
        d = clamp(d, 3, MAX_DIST);
        c.update();
        tx = gtx = c.ex + c.fx * d;
        ty = gty = c.ey + c.fy * d;
        tz = gtz = c.ez + c.fz * d;
        yaw = gyaw = c.yaw;
        pitch = gpitch = -c.pitch;
        dist = gdist = d;
        eye[0] = c.ex; eye[1] = c.ey; eye[2] = c.ez;
        eyeValid = true; lastFinalValid = false;
    }

    // ------------------------------------------------------------ Fahrten

    /** Spielt eine Fahrt ab (Tempo 1 = Sollzeit); done läuft am Ende, aber nicht, wenn die Fahrt abgebrochen wird. */
    public synchronized void play(Shot s, double speed, Runnable done) {
        ride = s; rideT = 0; rideSpeed = speed; rideDone = done; adoptPending = false;
        riding = true; rideName = s.name(); rideFrac = 0; rideHits = 0; rideLead = 0; rideTime = 0;
        rideCancels++;
    }

    public synchronized void setRideSpeed(double v) { rideSpeed = v; }

    /**
     * Spielt eine Fahrt ab; vorher fliegt die Kamera um alle Hindernisse herum von ihrem jetzigen Standort an den Anfang
     * der Fahrt (Bildwinkel gleitet mit). rideLead gibt die Dauer dieses Anflugs an.
     */
    public synchronized void playRide(Shot s, double speed, Runnable done) {
        double[] st = new double[7];
        s.pose(0, st);
        double[] cur = current();
        double[] e1 = {st[0], st[1], st[2]};
        double dist = Math.sqrt(sq(cur[0] - e1[0]) + sq(cur[1] - e1[1]) + sq(cur[2] - e1[2]));
        double[] t0 = {cur[3], cur[4], cur[5]}, t1 = {st[3], st[4], st[5]};
        double dtar = Math.sqrt(sq(t0[0] - t1[0]) + sq(t0[1] - t1[1]) + sq(t0[2] - t1[2]));
        final double fov1 = Double.isNaN(st[6]) ? CameraPath.FOV : st[6], fov0 = lastFov;
        if (dist < 0.3 && dtar < 0.5 && Math.abs(fov1 - fov0) < 0.01) {
            play(s, speed, done);
            rideLead = 0;
            return;
        }
        if (solids != null) solids.push(e1, R_EYE);
        final Shot lead = Router.route(solids, terrain, new double[]{cur[0], cur[1], cur[2]}, t0, e1, t1, "Anflug");
        final double ld = lead.duration();
        Shot lf = new Shot() {
            @Override public String name() { return "Anflug"; }
            @Override public double duration() { return ld; }
            @Override public void pose(double t, double[] o) {
                lead.pose(t, o);
                double u = Rides.sm(t / Math.max(1e-6, ld));
                o[6] = fov0 + (fov1 - fov0) * u;
            }
        };
        play(Shots.chain(s.name(), lf, s), speed, done);
        rideLead = ld;
    }

    /** Bricht die laufende Fahrt ab; die Steuerung übernimmt die Kamera, wo sie steht. */
    public synchronized void cancelRide() {
        if (ride == null) return;
        ride = null; rideDone = null; riding = false; adoptPending = true;
        rideCancels++;
    }

    /** Flug zu einer Pose (Drehpunkt, Gier, Nick, Abstand), um Mauern, Gelände und Bäume herum. */
    public synchronized void glideTo(double[] p) {
        double[] cur = current();
        double[] dest = new double[]{p[0], p[1], p[2], p[3], p[4], p[5]};
        // das Ziel mit kürzestem Drehweg
        double yw = Math.toDegrees(nearestYaw(Math.toRadians(p[3])));
        dest[3] = yw;
        Solids sl = solids;
        if (sl != null) {
            // Ziel nicht in einer Wand: Drehpunkt und Auge hinausschieben
            double[] pv = {dest[0], dest[1], dest[2]};
            if (sl.push(pv, R_PIVOT)) { dest[0] = pv[0]; dest[1] = pv[1]; dest[2] = pv[2]; }
        }
        double[] e1 = eyeOf(dest);
        if (sl != null) sl.push(e1, R_EYE);
        Shot s = Router.route(solids, terrain, new double[]{cur[0], cur[1], cur[2]}, new double[]{cur[3], cur[4], cur[5]},
                e1, new double[]{dest[0], dest[1], dest[2]}, "Flug");
        final double[] fin = dest;
        play(s, 1, () -> { synchronized (CameraController.this) { setPose(fin, true); } });
    }

    private void updateRide(double dt, Camera cam) {
        Shot s = ride;
        rideT += dt * rideSpeed;
        rideTime = rideT;
        double dur = Math.max(1e-6, s.duration());
        boolean end = rideT >= dur;
        s.pose(Math.min(rideT, dur), pose7);
        rideFrac = Math.min(1, rideT / dur);
        double ex = pose7[0], ey = pose7[1], ez = pose7[2];
        Solids so = solids;
        double ground = terrain.stand(ex, ez, ey) + 1.2;
        if (ey < ground) { ey = ground; rideHits++; }
        if (so != null) {
            double[] e = {ex, ey, ez};
            if (so.push(e, R_EYE)) { rideHits++; ex = e[0]; ey = e[1]; ez = e[2]; }
        }
        cam.ex = ex; cam.ey = ey; cam.ez = ez;
        cam.fovY = Double.isNaN(pose7[6]) ? CameraPath.FOV : pose7[6];
        lastFov = cam.fovY;
        cam.lookAt(pose7[3], pose7[4], pose7[5]);
        if (end) {
            Runnable d = rideDone;
            ride = null; rideDone = null; riding = false;
            double len = Math.sqrt(sq(pose7[3] - cam.ex) + sq(pose7[4] - cam.ey) + sq(pose7[5] - cam.ez));
            adopt(cam, Math.max(3, len));
            if (d != null) d.run();
        }
    }

    private static double sq(double x) { return x * x; }

    public synchronized void update(double dt, Camera cam) {
        // Bewegungstasten übernehmen die Kamera auch mitten in einer Fahrt
        if (ride != null && (keys[K_W] || keys[K_A] || keys[K_S] || keys[K_D] || keys[K_Q] || keys[K_E])) cancelRide();
        if (ride != null) { updateRide(dt, cam); return; }
        if (adoptPending) {
            adoptPending = false;
            adopt(cam, Math.max(3, Math.min(dist, 400)));
        }
        if (Math.abs(cam.fovY - CameraPath.FOV) > 1e-4) cam.fovY += (CameraPath.FOV - cam.fovY) * Math.min(1, dt * 4);
        else cam.fovY = CameraPath.FOV;
        lastFov = cam.fovY;
        double k = 1 - Math.exp(-dt * 6);
        if (autoOrbit) gyaw += dt * 0.08;
        // Drehpunkt mit der Tastatur verschieben, Tempo wächst mit dem Abstand
        double sp = (keys[K_SHIFT] ? 3.5 : 1) * Math.max(6, gdist * 0.35) * dt;
        double fx = -Math.sin(gyaw), fz = -Math.cos(gyaw), rx = -fz, rz = fx;
        double mf = (keys[K_W] ? 1 : 0) - (keys[K_S] ? 1 : 0), ms = (keys[K_D] ? 1 : 0) - (keys[K_A] ? 1 : 0);
        double mu = (keys[K_E] ? 1 : 0) - (keys[K_Q] ? 1 : 0);
        if (mf != 0 || ms != 0 || mu != 0) cancelRide();
        gtx += (fx * mf + rx * ms) * sp;
        gtz += (fz * mf + rz * ms) * sp;
        double floor = terrain.stand(gtx, gtz, gty);
        gty = clamp(gty + mu * sp * 0.5, floor + 0.5, floor + 1500);
        double ntx = tx + (gtx - tx) * k, nty = ty + (gty - ty) * k, ntz = tz + (gtz - tz) * k;
        yaw += (gyaw - yaw) * k; pitch += (gpitch - pitch) * k;
        dist = Math.exp(Math.log(dist) + (Math.log(gdist) - Math.log(dist)) * k);
        Solids so = solids;
        if (so != null) {
            // Drehpunkt gleitet an Wänden und Stämmen entlang; das Ziel folgt, damit es nicht in der Wand klebt
            double[] p = {tx, ty, tz};
            if (so.move(p, ntx, nty, ntz, R_PIVOT)) {
                guardHits++;
                gtx += p[0] - ntx; gty += p[1] - nty; gtz += p[2] - ntz;
            }
            tx = p[0]; ty = p[1]; tz = p[2];
        } else { tx = ntx; ty = nty; tz = ntz; }
        double cp = Math.cos(pitch);
        double dx = tx + dist * cp * Math.sin(yaw), dy = ty + dist * Math.sin(pitch), dz = tz + dist * cp * Math.cos(yaw);
        double ground = terrain.stand(dx, dz, ty) + 1.7;
        if (dy < ground) dy = ground;
        if (so != null && eyeValid) {
            double[] pv = eye.clone();
            boolean blocked = so.move(eye, dx, dy, dz, R_EYE);
            if (blocked) guardHits++;
        } else {
            eye[0] = dx; eye[1] = dy; eye[2] = dz;
            if (so != null) so.push(eye, R_EYE);
            eyeValid = true;
        }
        double g2 = terrain.stand(eye[0], eye[2], eye[1]) + 1.7;
        if (eye[1] < g2) eye[1] = g2;
        // nie genau im Drehpunkt stehen
        double ddx = tx - eye[0], ddy = ty - eye[1], ddz = tz - eye[2], dl = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
        if (dl < 0.25) { double q = (0.25 - dl); eye[0] -= (dl < 1e-6 ? 0 : ddx / dl) * q; eye[1] += dl < 1e-6 ? q : -ddy / dl * q; eye[2] -= (dl < 1e-6 ? 0 : ddz / dl) * q; }
        if (so != null) so.push(eye, R_EYE * 0.8);
        if (so != null && lastFinalValid) {
            // Letzte Sicherung: kreuzt der Schritt zwischen zwei Bildern eine Fläche, bleibt das Auge stehen
            double cl = Math.sqrt(sq(eye[0] - lastFinal[0]) + sq(eye[1] - lastFinal[1]) + sq(eye[2] - lastFinal[2]));
            if (cl > 1e-6 && so.ray(lastFinal[0], lastFinal[1], lastFinal[2], (eye[0] - lastFinal[0]) / cl, (eye[1] - lastFinal[1]) / cl, (eye[2] - lastFinal[2]) / cl, cl) >= 0) {
                if (DEBUG) System.out.printf("DEBUG Schritt verworfen: %.2f m%n", cl);
                eye[0] = lastFinal[0]; eye[1] = lastFinal[1]; eye[2] = lastFinal[2];
                guardHits++;
            }
        }
        lastFinal[0] = eye[0]; lastFinal[1] = eye[1]; lastFinal[2] = eye[2]; lastFinalValid = true;
        cam.ex = eye[0]; cam.ey = eye[1]; cam.ez = eye[2];
        cam.lookAt(tx, ty, tz);
    }

    private static double clamp(double v, double a, double b) { return Math.max(a, Math.min(b, v)); }
}
