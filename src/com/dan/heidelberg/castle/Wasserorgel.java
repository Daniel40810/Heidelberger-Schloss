package com.dan.heidelberg.castle;

import com.dan.heidelberg.core.Mat;
import com.dan.heidelberg.core.MeshBuilder;

import java.util.Random;

/**
 * Phase 9, Wasserorgel: im Wassermechanik-Raum hinter der Großen Grotte steht ein Pfeifenwerk, das Wasser mit Luft versorgt (Wasserkessel),
 * eine Stiftwalze steuert die Töne, ein Zahnradwerk treibt sie, und vier Uhrwerkvögel drehen sich auf Sockeln und trillern.
 * Solche Wasserorgeln, Walzen und Singvögel beschreibt Salomon de Caus in „Les raisons des forces mouvantes“ (1615); dass genau
 * diese Maschine im Heidelberger Garten stand, ist eine Annahme der Rekonstruktion. Die Walze, die Zahnräder und die Vögel sind
 * Drehkörper des Netzes, sie drehen sich also wirklich (Zeit des Bildrechners); der Ton entsteht im Programm ({@link Klang}).
 * <p>
 * Der Klang: zwei Stimmen (Melodie, Bordun) aus Obertönen mit Atem, Wasserrauschen, Zahnradticken und Vogeltriller. Die Melodie
 * ist eine eigene Komposition im Modus D-dorisch (Renaissance-Art), kein überliefertes Stück.
 */
public final class Wasserorgel {
    private Wasserorgel() { }

    /** Mitte des Raumes (x, y, z) für Abstand und Fahrt. */
    public static final double X = Hortus.GROSS_X, Y = Hortus.KL + 0.05, Z = Hortus.M[1] + 1.4 + 14 + 4.5;
    /** Ton der Orgel an oder aus (Bedienfeld "Wasserorgel"). */
    public static volatile boolean on = true;

    static void build(MeshBuilder mb) {
        MeshBuilder.SkyFn keepSky = mb.skyFn;
        int keepEra = mb.era;
        mb.skyFn = (x, y, z, nx, ny, nz) -> 0.18f;
        mb.era = MeshBuilder.ERA_1619;
        final double yF = Y, xm = X;
        final double zb = Z + 2.9;   // Rückwand des Raumes
        // --- Gehäuse: Eichenschrank mit drei Pfeifenfeldern
        mb.box(xm - 3.8, yF, zb - 1.3, xm + 3.8, yF + 1.2, zb - 0.05, Mat.OAK, true);
        mb.box(xm - 3.9, yF + 1.2, zb - 1.4, xm + 3.9, yF + 1.3, zb, Mat.GOLD, true);
        for (int s = -1; s <= 1; s += 2) {
            // Türme links und rechts: Pfeifenhaus mit Schnitzwerk
            mb.box(xm + s * 3.55 - 0.18, yF + 1.3, zb - 1.3, xm + s * 3.55 + 0.18, yF + 3.2, zb - 0.05, Mat.OAK, true);
            mb.box(xm + s * 3.55 - 0.3, yF + 3.2, zb - 1.35, xm + s * 3.55 + 0.3, yF + 3.35, zb, Mat.GOLD, true);
        }
        // --- Pfeifen: 13 sichtbare Prospektpfeifen, zwei hohe Türme, niedrige Felder dazwischen
        double[] hs = {1.55, 1.85, 2.15, 2.5, 1.45, 1.2, 1.0, 1.2, 1.45, 2.5, 2.15, 1.85, 1.55};
        double y0 = yF + 1.3;
        for (int i = 0; i < hs.length; i++) {
            double x = xm - 3.0 + i * 0.5, h = hs[i], r = 0.07 + 0.012 * h;
            mb.cylinder(x, zb - 0.75, y0, y0 + h, r, r * 0.92, 10, Mat.GOLD, true);
            // Labium (Mundschlitz) und Fuß
            mb.box(x - r * 0.8, y0 + 0.22 * h, zb - 0.75 - r - 0.03, x + r * 0.8, y0 + 0.22 * h + 0.09, zb - 0.75 - r + 0.01, Mat.CHAR, true);
            mb.cylinder(x, zb - 0.75, y0 - 0.2, y0, r * 0.45, r, 8, Mat.GOLD, false);
        }
        // Kranzleisten über den Feldern
        mb.box(xm - 3.2, yF + 3.1, zb - 1.0, xm - 1.2, yF + 3.2, zb - 0.5, Mat.GOLD, true);
        mb.box(xm + 1.2, yF + 3.1, zb - 1.0, xm + 3.2, yF + 3.2, zb - 0.5, Mat.GOLD, true);
        // --- Wasserkessel (der Wind kommt vom Wasser): Fass aus Eisenbändern, Zuleitung zum Schrank
        mb.cylinder(xm - 2.6, zb - 2.4, yF, yF + 1.5, 0.55, 0.5, 14, Mat.OAK, true);
        for (double yy : new double[]{0.25, 0.75, 1.25}) mb.cylinder(xm - 2.6, zb - 2.4, yF + yy - 0.03, yF + yy + 0.03, 0.57, 0.57, 14, Mat.IRON, false);
        mb.cylinder(xm - 2.6, zb - 1.9, yF + 1.0, yF + 1.12, 0.07, 0.07, 6, Mat.IRON, true);
        mb.box(xm - 2.66, yF + 1.0, zb - 1.9, xm - 2.54, yF + 1.12, zb - 1.3, Mat.IRON, true);
        // --- Stiftwalze auf dem Steintisch (Tisch bei z = zb - 4.0), dreht sich um die x-Achse
        double ty = yF + 1.05, zt = zb - 3.2;
        double wy = ty + 0.30;
        int sid = mb.addSpinner(xm, wy, zt, 1, 0, 0, 0.9);
        mb.spinId = sid;
        mb.patch((u, v, p, n) -> {
            p[0] = xm - 1.7 + 3.4 * v; p[1] = wy + 0.22 * Math.sin(u); p[2] = zt + 0.22 * Math.cos(u);
            n[0] = 0; n[1] = Math.sin(u); n[2] = Math.cos(u);
        }, 0, 2 * Math.PI, 16, 0, 1, 6, Mat.OAK);
        Random rnd = new Random(1615);
        for (int i = 0; i < 70; i++) {
            double a = rnd.nextDouble() * 6.283, x = xm - 1.6 + 3.2 * rnd.nextDouble();
            double py = wy + 0.22 * Math.sin(a), pz = zt + 0.22 * Math.cos(a);
            mb.box(x - 0.012, py - 0.012 + 0.03 * Math.sin(a), pz - 0.012 + 0.03 * Math.cos(a), x + 0.012, py + 0.012 + 0.03 * Math.sin(a), pz + 0.012 + 0.03 * Math.cos(a), Mat.IRON, true);
        }
        mb.spinId = -1;
        // Lager der Walze
        mb.box(xm - 1.85, ty, zt - 0.1, xm - 1.75, wy + 0.08, zt + 0.1, Mat.IRON, true);
        mb.box(xm + 1.75, ty, zt - 0.1, xm + 1.85, wy + 0.08, zt + 0.1, Mat.IRON, true);
        // --- Zahnradwerk an der Ostwand: drei Räder, gegenläufig
        double gx = xm + 3.9;
        gear(mb, gx, yF + 2.2, zb - 3.8, 0.95, 20, 0.5);
        gear(mb, gx, yF + 2.2, zb - 3.8 + 0.95 + 0.47, 0.47, 10, -1.01);
        gear(mb, gx, yF + 2.2 + 0.95 + 0.34, zb - 3.8, 0.34, 8, -1.4);
        // Gestell: Leiste hinter den Rädern
        mb.box(gx + 0.05, yF + 0.9, zb - 5.0, gx + 0.2, yF + 4.1, zb - 1.7, Mat.OAK, true);
        // --- vier Uhrwerkvögel auf Sockeln
        double[][] bp = {{xm - 3.4, zb - 5.3}, {xm - 3.4, zb - 2.6}, {xm + 3.4, zb - 5.3}, {xm + 3.4, zb - 2.0}};
        for (int i = 0; i < bp.length; i++) bird(mb, bp[i][0], yF, bp[i][1], i % 2 == 0 ? 0.9 : -0.8);
        // --- Licht: Kerzen am Gehäuse
        for (int s = -1; s <= 1; s += 2) {
            Candles.candle(mb, xm + s * 3.55, yF + 1.34, zb - 0.9, Candles.GROTTE);
            mb.cylinder(xm + s * 3.55, zb - 0.9, yF + 1.3, yF + 1.34, 0.06, 0.05, 8, Mat.GOLD, true);
        }
        Candles.light(xm - 2.6, yF + 2.4, zb - 1.6, 2.6f, 9f, Candles.GROTTE);
        Candles.light(xm + 2.6, yF + 2.4, zb - 1.6, 2.6f, 9f, Candles.GROTTE);
        Castle.place("Wasserorgel (Große Grotte)", xm, yF + 1.5, zb - 1.0, "E");
        mb.era = keepEra;
        mb.skyFn = keepSky;
    }

    /** Zahnrad mit Achse in x-Richtung, Mitte (cx, cy, cz), Radius R, Zähne, Winkelgeschwindigkeit. */
    static void gear(MeshBuilder mb, double cx, double cy, double cz, double R, int teeth, double omega) {
        int id = mb.addSpinner(cx, cy, cz, 1, 0, 0, omega);
        mb.spinId = id;
        double th = 0.07;
        int nu = teeth * 4;
        for (int side = -1; side <= 1; side += 2) {
            final int sd = side;
            mb.patch((u, v, p, n) -> {
                double rt = R * (1 + 0.09 * tooth(u, teeth)) * Math.max(0.14, v);
                p[0] = cx + sd * th / 2; p[1] = cy + rt * Math.cos(u); p[2] = cz + rt * Math.sin(u);
                n[0] = sd; n[1] = 0; n[2] = 0;
            }, 0, 2 * Math.PI, nu, 0.14, 1.0, 3, Mat.IRON);
        }
        mb.patch((u, v, p, n) -> {
            double rt = R * (1 + 0.09 * tooth(u, teeth));
            p[0] = cx + (v - 0.5) * th; p[1] = cy + rt * Math.cos(u); p[2] = cz + rt * Math.sin(u);
            n[0] = 0; n[1] = Math.cos(u); n[2] = Math.sin(u);
        }, 0, 2 * Math.PI, nu, 0, 1, 1, Mat.IRON);
        // Nabe und Speichen
        mb.patch((u, v, p, n) -> {
            p[0] = cx + (v - 0.5) * 0.16; p[1] = cy + R * 0.14 * Math.cos(u); p[2] = cz + R * 0.14 * Math.sin(u);
            n[0] = 0; n[1] = Math.cos(u); n[2] = Math.sin(u);
        }, 0, 2 * Math.PI, 10, 0, 1, 1, Mat.GOLD);
        mb.spinId = -1;
    }

    static double tooth(double u, int teeth) {
        double f = (u * teeth / (2 * Math.PI)) % 1.0;
        return f < 0.5 ? 1 : -1;
    }

    /** Uhrwerkvogel auf einem Sockel, dreht sich um die senkrechte Achse. */
    static void bird(MeshBuilder mb, double x, double y, double z, double omega) {
        mb.cylinder(x, z, y, y + 1.05, 0.17, 0.12, 10, Mat.STATUE, true);
        mb.cylinder(x, z, y + 1.05, y + 1.1, 0.2, 0.2, 10, Mat.GOLD, true);
        int id = mb.addSpinner(x, y, z, 0, 1, 0, omega);
        mb.spinId = id;
        double by = y + 1.28;
        mb.ellipsoid(x, by, z, 0.19, 0.11, 0.09, 10, 6, Mat.GOLD);                  // Körper (Länge in x)
        mb.ellipsoid(x + 0.21, by + 0.11, z, 0.075, 0.075, 0.07, 8, 5, Mat.GOLD);    // Kopf
        mb.box(x + 0.27, by + 0.095, z - 0.015, x + 0.36, by + 0.125, z + 0.015, Mat.IRON, true);   // Schnabel
        mb.box(x - 0.40, by + 0.0, z - 0.04, x - 0.17, by + 0.04, z + 0.04, Mat.GOLD, true);        // Schwanz
        for (int s = -1; s <= 1; s += 2) {   // Flügel, leicht gehoben
            mb.quad(new double[]{x - 0.1, by + 0.07, z + s * 0.07}, new double[]{x + 0.1, by + 0.07, z + s * 0.07},
                    new double[]{x + 0.02, by + 0.2, z + s * 0.3}, new double[]{x - 0.18, by + 0.2, z + s * 0.3}, 0, 1, 0, Mat.GOLD);
        }
        mb.spinId = -1;
    }

    // ------------------------------------------------------------ Klang

    private static final double[] MEL = {
            62, 1, 64, 1, 65, 1, 67, 1, 69, 2, 67, 1, 65, 1, 64, 2, 62, 1, 65, 1, 64, 1, 62, 1, 60, 2, 62, 2,
            69, 1, 72, 1, 71, 1, 69, 1, 67, 2, 65, 1, 67, 1, 69, 2, 67, 1, 65, 1, 64, 1, 62, 1, 62, 4};

    /** Die Quelle der Wasserorgel für den Mischer. gain wird je Bild gesetzt (Abstand, Raum). */
    public static final class Voice implements Klang.Source {
        public volatile float gain;
        private long n;
        private int note;
        private double noteLeft;
        private final double[] ph = new double[16];
        private double lp, flow, birdT = 4, birdPhase;
        private final Random rnd = new Random(1615);
        private final double bpm = 76;
        private float g;
        private double curF, prevF;
        private double xfade = 1;

        @Override public boolean alive() { return true; }

        static double hz(double m) { return 440.0 * Math.pow(2, (m - 69) / 12.0); }

        @Override public void mix(float[] out, int cnt) {
            if (!on) { g += (0 - g) * 0.05f; if (g < 0.001f) return; }
            else { g += (gain - g) * 0.02f; if (g < 0.001f) return; }
            double dt = 1.0 / Klang.SR;
            double beat = 60.0 / bpm;
            for (int i = 0; i < cnt; i++) {
                if (noteLeft <= 0) {
                    prevF = curF;
                    curF = hz(MEL[2 * note]);
                    noteLeft = MEL[2 * note + 1] * beat;
                    xfade = 0;
                    note = (note + 1) % (MEL.length / 2);
                    n = 0;
                }
                noteLeft -= dt;
                double t = n * dt;
                n++;
                // Hüllkurve: Wind setzt zügig ein, am Ende kurz absetzen (Stiftwalze lässt die Ventile los)
                double env = Math.min(1, t / 0.045) * Math.min(1, Math.max(0, noteLeft) / 0.05 + 0.05);
                double trem = 1 + 0.025 * Math.sin(2 * Math.PI * 5.3 * (t + note));
                double f = curF;
                double s = 0;
                for (int h = 1; h <= 6; h++) {
                    double a = h == 1 ? 1.0 : h == 2 ? 0.62 : h == 3 ? 0.38 : h == 4 ? 0.22 : h == 5 ? 0.1 : 0.06;
                    ph[h] += 2 * Math.PI * f * h * dt * (1 + 0.0007 * Math.sin(t * 3 + h));
                    s += a * Math.sin(ph[h]);
                }
                s *= 0.16 * env * trem;
                // Bordun: Quinte D3 und A3, leise
                ph[8] += 2 * Math.PI * 146.83 * dt; ph[9] += 2 * Math.PI * 220.0 * dt;
                double dr = 0.05 * (Math.sin(ph[8]) + 0.4 * Math.sin(2 * ph[8]) + 0.7 * Math.sin(ph[9]));
                // Anblasegeräusch am Tonanfang
                double chiff = t < 0.04 ? (rnd.nextDouble() * 2 - 1) * 0.09 * (1 - t / 0.04) : 0;
                // Wasserrauschen aus dem Kessel (tiefgefiltertes Rauschen mit langsamer Welle)
                flow += 0.06 * ((rnd.nextDouble() * 2 - 1) - flow);
                lp += 0.35 * (flow - lp);
                double water = lp * 0.55 * (0.7 + 0.3 * Math.sin(2 * Math.PI * 0.37 * (n * dt + note)));
                // Zahnradticken
                double tickPh = (n * dt + note * 0.31) * 7.0 % 1.0;
                double tick = tickPh < 0.004 ? (rnd.nextDouble() * 2 - 1) * 0.08 : 0;
                // Vogeltriller alle 7 bis 12 s
                birdT -= dt;
                double bird = 0;
                if (birdT < 0) {
                    birdPhase += dt;
                    if (birdPhase < 0.95) {
                        double fb = 3200 + 700 * Math.sin(2 * Math.PI * 12 * birdPhase) + 400 * birdPhase;
                        ph[12] += 2 * Math.PI * fb * dt;
                        double ae = Math.max(0, Math.sin(Math.PI * birdPhase / 0.95)) * (0.5 + 0.5 * Math.sin(2 * Math.PI * 16 * birdPhase));
                        bird = 0.035 * ae * Math.sin(ph[12]);
                    } else { birdT = 7 + 5 * rnd.nextDouble(); birdPhase = 0; }
                }
                float v = (float) ((s + dr + chiff + water * 0.02 + tick + bird) * g);
                out[i] += v * 1.8f;
            }
        }
    }

    private static Voice voice;

    /** Die Orgel anmelden und die Lautstärke je Bild setzen: Abstand zur Raummitte in Metern, drinnen = im Raum oder in der Halle. */
    public static void listen(double dist, boolean inside) {
        if (voice == null) { voice = new Voice(); Klang.add(voice); }
        double g = inside ? 1.0 : Math.max(0, 1 - (dist - 3) / 55.0);
        if (inside) g = Math.max(0.4, 1 - dist / 40.0);
        voice.gain = (float) (g * g * 0.9);
    }

    public static void silence() { if (voice != null) voice.gain = 0; }

    /** Für die Prüfung ohne Soundkarte: n Sekunden Orgel mit gain 1. */
    public static float[] render(double seconds) {
        Voice v = new Voice();
        v.gain = 1;
        int total = (int) (seconds * Klang.SR);
        float[] out = new float[total];
        for (int off = 0; off < total; off += 512) {
            float[] blk = new float[512];
            v.mix(blk, 512);
            System.arraycopy(blk, 0, out, off, Math.min(512, total - off));
        }
        return out;
    }
}
