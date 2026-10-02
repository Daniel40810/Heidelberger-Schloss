package com.dan.heidelberg.castle;

import com.dan.heidelberg.core.Mat;
import com.dan.heidelberg.core.MeshBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Wappen der Kurpfalz an den Fassaden (Phase 7). Das Bild entsteht im Materialprogramm {@link Mat#HERALD} aus
 * Flächenformeln, nicht aus Dreiecken: geviertes Wappen mit dem Pfälzer Löwen (gold auf Schwarz) und den
 * bayerischen Rauten (Silber und Blau), im Herzschild der Reichsapfel (Gold auf Rot), darüber der Kurhut.
 * Es bleibt scharf, wie nah die Kamera auch kommt, und wirft durch die Verkippung der Flächennormalen ein Relief.
 * Der Löwe ist aus Kapseln und Kreisen zusammengesetzt, also <b>stilisiert</b>, nicht nach einer Vorlage gezeichnet.
 * Die Platten sind kleine Quader (Mat.HERALD) in einem Rahmen aus Stein.
 */
public final class Heraldry {
    private Heraldry() { }

    /** Mittelpunkt des Schildes (x, y, z), Blickrichtung nach außen (nx, nz), Rechtsvektor (rx, rz), Breite s. */
    public static final List<double[]> SHIELDS = new ArrayList<>();
    private static volatile double[][] snap = new double[0][];

    /** Wappen in Räumen (Phase 8): kommen beim Siegeln hinter die Wappen an den Außenwänden, damit deren Nummern bleiben. */
    private static final List<double[]> INNER = new ArrayList<>();
    public static void clear() { SHIELDS.clear(); INNER.clear(); snap = new double[0][]; }

    public static void seal() { SHIELDS.addAll(INNER); INNER.clear(); snap = SHIELDS.toArray(new double[0][]); }

    /**
     * Platte mit Wappen an der Seite k des Baukörpers b: Schildmitte bei u (längs der Wand) und Höhe y über dem Boden,
     * Schildbreite s. Zeichnet Platte und Rahmen und trägt das Schild in die Liste ein.
     */
    public static void plaque(MeshBuilder mb, Arch.Block b, int k, double u, double y, double s) {
        double keep = mb.maxEdge;
        mb.maxEdge = 1e9;
        double pw = 0.78 * s, lo = 0.88 * s, hi = 1.03 * s, d = 0.40;
        b.onFace(mb, k, 0, () -> {
            mb.box(u - pw, y - lo, 0, u + pw, y + hi, d, Mat.HERALD, false);
            // Rahmen aus Stein: vier Leisten
            double f = 0.07 * s, e = d + 0.07;
            mb.box(u - pw - f, y - lo - f, 0, u + pw + f, y - lo, e, Mat.STATUE, true);
            mb.box(u - pw - f, y + hi, 0, u + pw + f, y + hi + f, e, Mat.STATUE, true);
            mb.box(u - pw - f, y - lo, 0, u - pw, y + hi, e, Mat.STATUE, true);
            mb.box(u + pw, y - lo, 0, u + pw + f, y + hi, e, Mat.STATUE, true);
        });
        double[] w = b.faceWorld(k, u, d);
        SHIELDS.add(new double[]{w[0], y, w[1], w[2], w[3], w[3], -w[2], s});
        mb.maxEdge = keep;
    }

    /**
     * Platte mit Wappen frei im Raum (Phase 8): Rückseite bei (x, z), Vorderseite d = 0,40 m davor in Richtung (nx, nz),
     * die eine Achse des Raumes ist (±1, 0) oder (0, ±1); y ist die Mitte des Schildes, s die Schildbreite.
     */
    public static void plaqueAt(MeshBuilder mb, double x, double y, double z, double nx, double nz, double s) {
        plaqueAt(mb, x, y, z, nx, nz, s, 0.40);
    }

    public static void plaqueAt(MeshBuilder mb, double x, double y, double z, double nx, double nz, double s, double d) {
        double keep = mb.maxEdge;
        mb.maxEdge = 1e9;
        double pw = 0.78 * s, lo = 0.88 * s, hi = 1.03 * s, f = 0.07 * s, e = d + 0.04;
        double sx = Math.signum(nx), sz = Math.signum(nz);
        if (Math.abs(nx) > 0.5) {
            double a = x, b = x + sx * d, c = x + sx * e;
            mb.box(Math.min(a, b), y - lo, z - pw, Math.max(a, b), y + hi, z + pw, Mat.HERALD, false);
            mb.box(Math.min(a, c), y - lo - f, z - pw - f, Math.max(a, c), y - lo, z + pw + f, Mat.STATUE, true);
            mb.box(Math.min(a, c), y + hi, z - pw - f, Math.max(a, c), y + hi + f, z + pw + f, Mat.STATUE, true);
            mb.box(Math.min(a, c), y - lo, z - pw - f, Math.max(a, c), y + hi, z - pw, Mat.STATUE, true);
            mb.box(Math.min(a, c), y - lo, z + pw, Math.max(a, c), y + hi, z + pw + f, Mat.STATUE, true);
            INNER.add(new double[]{x + sx * d, y, z, sx, 0, 0, -sx, s});
        } else {
            double a = z, b = z + sz * d, c = z + sz * e;
            mb.box(x - pw, y - lo, Math.min(a, b), x + pw, y + hi, Math.max(a, b), Mat.HERALD, false);
            mb.box(x - pw - f, y - lo - f, Math.min(a, c), x + pw + f, y - lo, Math.max(a, c), Mat.STATUE, true);
            mb.box(x - pw - f, y + hi, Math.min(a, c), x + pw + f, y + hi + f, Math.max(a, c), Mat.STATUE, true);
            mb.box(x - pw - f, y - lo, Math.min(a, c), x - pw, y + hi, Math.max(a, c), Mat.STATUE, true);
            mb.box(x + pw, y - lo, Math.min(a, c), x + pw + f, y + hi, Math.max(a, c), Mat.STATUE, true);
            INNER.add(new double[]{x, y, z + sz * d, 0, sz, sz, 0, s});
        }
        mb.maxEdge = keep;
    }

    // ------------------------------------------------------------ Farben (linear)

    private static final float[] SCHWARZ = {0.014f, 0.013f, 0.012f}, GOLD = {0.60f, 0.40f, 0.075f}, ROT = {0.30f, 0.018f, 0.014f},
            SILBER = {0.52f, 0.53f, 0.55f}, BLAU = {0.012f, 0.045f, 0.26f}, HERMELIN = {0.62f, 0.62f, 0.60f};

    private static float smooth(float a, float b, float x) {
        float t = (x - a) / (b - a);
        t = t < 0 ? 0 : (t > 1 ? 1 : t);
        return t * t * (3 - 2 * t);
    }

    // ------------------------------------------------------------ Formen (Maße in Schildbreiten)

    /** Abstand zu einer Strecke ab minus Radius, Radius wechselt linear. */
    private static float seg(float px, float py, float ax, float ay, float bx, float by, float ra, float rb) {
        float abx = bx - ax, aby = by - ay, apx = px - ax, apy = py - ay;
        float h = (apx * abx + apy * aby) / (abx * abx + aby * aby);
        h = h < 0 ? 0 : (h > 1 ? 1 : h);
        float dx = apx - abx * h, dy = apy - aby * h;
        return (float) Math.sqrt(dx * dx + dy * dy) - (ra + (rb - ra) * h);
    }

    private static float circ(float px, float py, float cx, float cy, float r) {
        float dx = px - cx, dy = py - cy;
        return (float) Math.sqrt(dx * dx + dy * dy) - r;
    }

    private static float smin(float a, float b, float k) {
        float h = Math.max(k - Math.abs(a - b), 0) / k;
        return Math.min(a, b) - h * h * k * 0.25f;
    }

    /** Löwe nach links aufgerichtet in Einheiten des Löwenkastens (Höhe 1, Mitte 0): Abstand zur Umrisslinie; claw = Abstand zu Zunge und Krallen. */
    private static float lion(float x, float y, float[] extra) {
        float d = seg(x, y, -0.06f, 0.12f, 0.08f, -0.12f, 0.15f, 0.11f);                    // Rumpf
        d = smin(d, circ(x, y, 0.10f, -0.22f, 0.12f), 0.05f);                                // Keule
        // Mähne mit Zotteln
        float mx = x + 0.06f, my = y - 0.30f;
        float ang = (float) Math.atan2(my, mx);
        d = smin(d, (float) Math.sqrt(mx * mx + my * my) - 0.15f * (1 + 0.10f * (float) Math.sin(9 * ang)), 0.05f);
        d = smin(d, circ(x, y, -0.14f, 0.33f, 0.10f), 0.04f);                                // Kopf
        d = smin(d, seg(x, y, -0.20f, 0.30f, -0.30f, 0.28f, 0.055f, 0.045f), 0.03f);       // Schnauze
        d = smin(d, circ(x, y, -0.10f, 0.435f, 0.032f), 0.02f);                              // Ohr
        d = smin(d, seg(x, y, -0.10f, 0.18f, -0.24f, 0.26f, 0.05f, 0.04f), 0.03f);          // Vorderbein hoch
        d = smin(d, seg(x, y, -0.24f, 0.26f, -0.34f, 0.16f, 0.04f, 0.036f), 0.03f);
        d = smin(d, seg(x, y, -0.06f, 0.10f, -0.22f, 0.08f, 0.05f, 0.04f), 0.03f);          // Vorderbein tief
        d = smin(d, seg(x, y, -0.22f, 0.08f, -0.32f, -0.02f, 0.04f, 0.036f), 0.03f);
        d = smin(d, seg(x, y, 0.08f, -0.18f, -0.06f, -0.30f, 0.07f, 0.05f), 0.04f);         // Hinterbein vorn
        d = smin(d, seg(x, y, -0.06f, -0.30f, -0.14f, -0.44f, 0.05f, 0.045f), 0.03f);
        d = smin(d, seg(x, y, 0.14f, -0.22f, 0.16f, -0.36f, 0.06f, 0.05f), 0.04f);          // Hinterbein hinten
        d = smin(d, seg(x, y, 0.16f, -0.36f, 0.10f, -0.46f, 0.05f, 0.045f), 0.03f);
        // Schwanz über den Rücken
        float t = seg(x, y, 0.12f, -0.10f, 0.26f, -0.04f, 0.04f, 0.035f);
        t = Math.min(t, seg(x, y, 0.26f, -0.04f, 0.31f, 0.10f, 0.035f, 0.033f));
        t = Math.min(t, seg(x, y, 0.31f, 0.10f, 0.24f, 0.24f, 0.033f, 0.04f));
        t = Math.min(t, circ(x, y, 0.22f, 0.30f, 0.065f));
        t = Math.min(t, circ(x, y, 0.28f, 0.26f, 0.04f));
        d = smin(d, t, 0.03f);
        if (extra != null) {
            float c = seg(x, y, -0.30f, 0.26f, -0.37f, 0.20f, 0.02f, 0.016f);               // Zunge
            c = Math.min(c, circ(x, y, -0.34f, 0.16f, 0.028f));                               // Krallen
            c = Math.min(c, circ(x, y, -0.32f, -0.02f, 0.028f));
            c = Math.min(c, circ(x, y, -0.14f, -0.44f, 0.03f));
            c = Math.min(c, circ(x, y, 0.10f, -0.46f, 0.03f));
            extra[0] = c;
        }
        return d;
    }

    private static final float VC = -0.04f;                 // Schnittpunkt der Viertel (Höhe)
    private static final float LS = 0.52f;                  // Löwenkasten in Schildbreiten
    private static final float[] LION_TL = {-0.245f, 0.215f}, LION_BR = {0.245f, -0.31f};

    /** Abstand zum Schild (negativ innen): oben flach, unten halbe Ellipse. */
    private static float shield(float u, float v) {
        float dx = Math.abs(u) - 0.5f;
        if (v >= -0.1f) return Math.max(dx, v - 0.5f);
        float a = u / 0.5f, b = (v + 0.1f) / 0.6f;
        return ((float) Math.sqrt(a * a + b * b) - 1f) * 0.5f;
    }

    /** Abstand zum Löwen des Viertels, in dem (u, v) liegt (Schildbreiten); Zunge und Krallen in extra[0]. */
    private static float lionAt(float u, float v, float[] extra) {
        float cx, cy;
        if (u < 0) { cx = LION_TL[0]; cy = LION_TL[1]; } else { cx = LION_BR[0]; cy = LION_BR[1]; }
        if (u < 0 != v > VC) return 9f;                       // Rautenviertel
        float d = lion((u - cx) / LS, (v - cy) / LS, extra) * LS;
        if (extra != null) extra[0] *= LS;
        return d;
    }

    /** Höhe des Reliefs (0 flach, 1 hoch) an (u, v) für die Normale: Löwe, Rand, Herzschild, Hut. */
    private static float relief(float u, float v, float w) {
        float h = 0;
        float sd = shield(u, v);
        h = Math.max(h, 0.55f * smooth(w, -w, Math.abs(sd + 0.025f) - 0.02f));            // Bord
        float ld = lionAt(u, v, null);
        if (ld < 8) h = Math.max(h, smooth(w, -w, ld) * 0.9f);
        float hd = circ(u, v, 0f, VC, 0.13f);
        h = Math.max(h, 0.7f * smooth(w, -w, hd));
        return h;
    }

    /**
     * Farbe und Normale am Punkt (x, y, z) mit Pixelgröße foot; liefert den Faktor fürs Umgebungslicht oder −1,
     * wenn der Punkt zu keinem Wappen gehört (dann zeichnet {@link com.dan.heidelberg.core.Materials} Stein).
     */
    public static float shade(float x, float y, float z, float[] n, float foot, float[] o) {
        double[][] sh = snap;
        for (double[] s : sh) {
            // nur die Vorderseite der Platte
            if (n[0] * s[3] + n[2] * s[4] < 0.6f) continue;
            float sc = (float) s[7];
            float u = ((float) (x - s[0]) * (float) s[5] + (float) (z - s[2]) * (float) s[6]) / sc;
            float v = (y - (float) s[1]) / sc;
            if (Math.abs(u) > 0.8f || v < -0.9f || v > 1.06f) continue;
            return paint(u, v, foot / sc, n, (float) s[5], (float) s[6], sc, o);
        }
        return -1;
    }

    private static float paint(float u, float v, float w, float[] n, float rx, float rz, float sc, float[] o) {
        w = Math.max(w * 1.1f, 0.0015f);
        float[] ex = new float[1];
        // Grund: Stein des Täfelchens
        float stone = 0.30f;
        float r = 0.50f * stone / 0.30f * 0.75f, g = 0.41f * stone / 0.30f * 0.75f, b = 0.31f * stone / 0.30f * 0.75f;
        float sd = shield(u, v);
        float cov = smooth(w, -w, sd);
        float[] c = new float[3];
        if (cov > 0) {
            // Viertel
            boolean left = u < 0, top = v > VC;
            if (left == top) {
                // Pfälzer Löwe: gold auf Schwarz
                c[0] = SCHWARZ[0]; c[1] = SCHWARZ[1]; c[2] = SCHWARZ[2];
                float ld = lionAt(u, v, ex);
                float lc = smooth(w, -w, ld);
                if (lc > 0) {
                    // Fell: feine Riefen, nur nah zu sehen
                    float fur = 0.86f + 0.14f * (float) Math.sin((u * 1.0f - v * 0.8f) * 260f) * (1 - smooth(0.004f, 0.02f, w));
                    c[0] += (GOLD[0] * fur - c[0]) * lc; c[1] += (GOLD[1] * fur - c[1]) * lc; c[2] += (GOLD[2] * fur - c[2]) * lc;
                    float rc = smooth(w, -w, ex[0]) * lc;
                    c[0] += (ROT[0] - c[0]) * rc; c[1] += (ROT[1] - c[1]) * rc; c[2] += (ROT[2] - c[2]) * rc;
                }
            } else {
                // Bayerische Rauten: schräges Gitter, Silber und Blau im Wechsel
                float L = 0.118f;
                float a = (u + v) / (2 * L), bb = (u - v) / (2 * L);
                int par = (((int) Math.floor(a)) + ((int) Math.floor(bb))) & 1;
                float[] col = par == 0 ? BLAU : SILBER;
                c[0] = col[0]; c[1] = col[1]; c[2] = col[2];
            }
            // Herzschild: Reichsapfel, Gold auf Rot
            float hd = circ(u, v, 0f, VC, 0.13f);
            float hc = smooth(w, -w, hd);
            if (hc > 0) {
                float[] hcl = {ROT[0], ROT[1], ROT[2]};
                float oc = smooth(w, -w, circ(u, v, 0f, VC - 0.005f, 0.058f));
                float band = smooth(w, -w, Math.abs(circ(u, v, 0f, VC - 0.005f, 0.0f) ) - 0.058f) * 0;
                float cross = Math.max(smooth(w, -w, Math.max(Math.abs(u) - 0.010f, Math.abs(v - (VC + 0.083f)) - 0.030f)),
                        smooth(w, -w, Math.max(Math.abs(u) - 0.030f, Math.abs(v - (VC + 0.075f)) - 0.009f)));
                float gk = Math.max(oc, cross);
                hcl[0] += (GOLD[0] - hcl[0]) * gk; hcl[1] += (GOLD[1] - hcl[1]) * gk; hcl[2] += (GOLD[2] - hcl[2]) * gk;
                c[0] += (hcl[0] - c[0]) * hc; c[1] += (hcl[1] - c[1]) * hc; c[2] += (hcl[2] - c[2]) * hc;
            }
            // Viertelungslinien und Rand in Gold
            float line = Math.max(smooth(w, -w, Math.abs(u) - 0.008f) * (v > VC - 0.0f || true ? 1 : 0), smooth(w, -w, Math.abs(v - VC) - 0.008f));
            float rim = smooth(w, -w, Math.abs(sd + 0.022f) - 0.014f);
            float gl = Math.max(line * 0.0f, rim);
            c[0] += (GOLD[0] - c[0]) * gl; c[1] += (GOLD[1] - c[1]) * gl; c[2] += (GOLD[2] - c[2]) * gl;
            float dl = smooth(w, -w, Math.abs(u) - 0.006f) * smooth(0.13f, 0.16f, (float) Math.hypot(u, v - VC))
                    + smooth(w, -w, Math.abs(v - VC) - 0.006f) * smooth(0.13f, 0.16f, (float) Math.hypot(u, v - VC));
            dl = Math.min(1, dl) * 0.8f;
            c[0] += (GOLD[0] * 0.7f - c[0]) * dl; c[1] += (GOLD[1] * 0.7f - c[1]) * dl; c[2] += (GOLD[2] * 0.7f - c[2]) * dl;
            r += (c[0] - r) * cov; g += (c[1] - g) * cov; b += (c[2] - b) * cov;
        }
        // Kurhut: roter Dom, Hermelinstulpe, Goldkreuz
        {
            float dome = (float) Math.sqrt(sq(u / 0.34f) + sq((v - 0.62f) / 0.22f)) - 1f;
            float dc = Math.min(smooth(w * 2, -w * 2, dome) * (v >= 0.60f ? 1 : 0), 1);
            float band = smooth(w, -w, Math.max(Math.abs(u) - 0.36f, Math.max(0.51f - v, v - 0.645f) ));
            if (dc > 0) { r += (ROT[0] - r) * dc; g += (ROT[1] - g) * dc; b += (ROT[2] - b) * dc;
                // zwei Goldspangen über den Dom
                float sp = Math.max(smooth(w, -w, Math.abs(Math.abs(u) - 0.17f * (1 - sq((v - 0.62f) / 0.22f) * 0.5f)) - 0.012f), 0) * dc;
                r += (GOLD[0] - r) * sp; g += (GOLD[1] - g) * sp; b += (GOLD[2] - b) * sp; }
            if (band > 0) {
                float spot = 1 - smooth(0.010f, 0.02f, (float) Math.hypot(((u + 10) % 0.085f) - 0.0425f, ((v * 1.0f + 10) % 0.07f) - 0.035f));
                r += (HERMELIN[0] * (1 - 0.85f * spot) - r) * band; g += (HERMELIN[1] * (1 - 0.85f * spot) - g) * band; b += (HERMELIN[2] * (1 - 0.85f * spot) - b) * band;
            }
            float orb = smooth(w, -w, circ(u, v, 0f, 0.865f, 0.030f));
            float cr = Math.max(smooth(w, -w, Math.max(Math.abs(u) - 0.010f, Math.abs(v - 0.935f) - 0.045f)),
                    smooth(w, -w, Math.max(Math.abs(u) - 0.038f, Math.abs(v - 0.945f) - 0.011f)));
            float gk = Math.max(orb, cr);
            r += (GOLD[0] - r) * gk; g += (GOLD[1] - g) * gk; b += (GOLD[2] - b) * gk;
        }
        o[0] = r; o[1] = g; o[2] = b;
        // Relief: Normale nach dem Gefälle der Höhe kippen
        if (w < 0.03f) {
            float e = Math.max(w * 0.7f, 0.0025f);
            float h0 = relief(u, v, w), hu = relief(u + e, v, w), hv = relief(u, v + e, w);
            float gu = (hu - h0) / e, gv = (hv - h0) / e;
            float k = 0.020f * (1 - smooth(0.004f, 0.03f, w)) ;
            n[0] -= gu * k * rx; n[2] -= gu * k * rz; n[1] -= gv * k;
            float l = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
            n[0] /= l; n[1] /= l; n[2] /= l;
        }
        return 0.95f;
    }

    private static float sq(float x) { return x * x; }
}
