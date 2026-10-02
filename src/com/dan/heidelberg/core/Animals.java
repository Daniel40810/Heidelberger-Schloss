package com.dan.heidelberg.core;

/**
 * Tiere für den Bildrechner: Bison und Wapiti als Schattenrisse in einer senkrechten Ebene entlang
 * der Laufrichtung, die sich so weit zur Kamera dreht, dass sie nie ganz zur Linie wird. Die Beine
 * schwingen im Schritt. Wer sie bewegt ({@link com.dan.heidelberg.world.Fauna}), füllt die Felder vor
 * jedem Bild; der Bildrechner liest sie nur.
 * <p>
 * Die Umrisse sind in Metern gezeichnet (x nach vorn, y nach oben, Boden bei 0).
 */
public final class Animals {
    public static final byte BISON = 0, ELK_COW = 1, ELK_BULL = 2, BISON_CALF = 3, PERSON = 4;
    /** Phase 8, Leben im Schloss: Herr, Dame, Wache mit Hellebarde, Küfer, Kurfürst; Pferd, Reiter, Kutsche, Fasskarren. */
    public static final byte MAN = 5, LADY = 6, GUARD = 7, COOPER = 8, LORD = 9, HORSE = 10, RIDER = 11, COACH = 12, CART = 13;
    static final int CAP = 320;

    public int n;
    public final float[] x = new float[CAP], y = new float[CAP], z = new float[CAP], hx = new float[CAP], hz = new float[CAP];
    /** Schrittphase (Bogenmaß), Gangstärke 0 (steht, grast) bis 1 (geht), Kopf gesenkt 0..1, Größe. */
    public final float[] step = new float[CAP], gait = new float[CAP], graze = new float[CAP], scale = new float[CAP];
    public final byte[] kind = new byte[CAP];
    /** Umgebungslicht 1 = im Freien, kleiner in Räumen (die Kerzen leuchten dann dazu); Spielart (Hut, Kleid …). */
    public final float[] amb = new float[CAP], variant = new float[CAP];
    /** Besucher: Farbe der Jacke (linear) und der Hose. */
    public final float[] cr = new float[CAP], cg = new float[CAP], cb = new float[CAP], pr = new float[CAP], pg = new float[CAP], pb = new float[CAP];

    /** Körper: Umriss als Punktfolge (x, y) und Farbe (linear, Albedo). */
    static final float[] BISON_BODY = {
            -1.45f, 1.05f, -1.40f, 1.38f, -1.10f, 1.52f, -0.50f, 1.60f, 0.05f, 1.78f, 0.35f, 1.86f, 0.62f, 1.80f,
            0.95f, 1.55f, 1.20f, 1.28f, 1.38f, 1.02f, 1.47f, 0.70f, 1.42f, 0.52f, 1.22f, 0.46f, 1.05f, 0.30f,
            0.82f, 0.40f, 0.62f, 0.55f, 0.20f, 0.66f, -0.55f, 0.72f, -1.05f, 0.80f, -1.35f, 0.88f};
    /** Mähne und Kopf dunkler, über den Körper gelegt. */
    static final float[] BISON_MANE = {0.05f, 1.78f, 0.35f, 1.86f, 0.62f, 1.80f, 0.95f, 1.55f, 1.20f, 1.28f, 1.38f, 1.02f,
            1.47f, 0.70f, 1.42f, 0.52f, 1.22f, 0.46f, 1.05f, 0.30f, 0.82f, 0.40f, 0.62f, 0.55f, 0.25f, 0.70f, -0.05f, 1.10f};
    static final float[] ELK_BODY = {
            -1.08f, 1.30f, -1.00f, 1.43f, -0.55f, 1.47f, 0.00f, 1.45f, 0.40f, 1.52f, 0.62f, 1.50f, 0.80f, 1.30f,
            0.74f, 1.08f, 0.62f, 0.98f, 0.20f, 0.95f, -0.30f, 0.97f, -0.70f, 1.02f, -1.02f, 1.08f};
    /** Hals und Kopf mit Ohr, um den Widerrist drehbar (Grasen). */
    static final float[] ELK_NECK = {0.35f, 1.50f, 0.62f, 1.72f, 0.92f, 2.00f, 0.96f, 2.28f, 1.02f, 2.14f, 1.20f, 2.10f,
            1.55f, 1.88f, 1.50f, 1.78f, 1.22f, 1.82f, 0.98f, 1.64f, 0.80f, 1.36f, 0.62f, 1.16f};
    static final float[] ELK_RUMP = {-1.08f, 1.10f, -1.07f, 1.40f, -0.82f, 1.45f, -0.72f, 1.22f, -0.86f, 1.04f};
    /** Geweih des Bullen: Stange und Enden als Linien (x0, y0, x1, y1), vom Kopf aus. */
    static final float[] ANTLER = {1.12f, 2.15f, 0.70f, 2.85f, 0.70f, 2.85f, 0.25f, 3.20f, 1.05f, 2.30f, 1.40f, 2.55f,
            0.92f, 2.52f, 1.22f, 2.85f, 0.80f, 2.70f, 1.00f, 3.05f, 0.55f, 2.98f, 0.62f, 3.30f};

    /** Mensch, von der Seite: Rumpf mit Jacke, Kopf; Beine eigens (Hüfte bei 0,9 m). */
    static final float[] PERSON_BODY = {-0.14f, 0.88f, -0.16f, 1.20f, -0.13f, 1.42f, -0.05f, 1.48f, 0.08f, 1.47f, 0.14f, 1.38f, 0.15f, 1.15f, 0.12f, 0.88f};
    static final float[] PERSON_HEAD = {-0.07f, 1.50f, -0.09f, 1.60f, -0.06f, 1.70f, 0.02f, 1.73f, 0.09f, 1.68f, 0.10f, 1.58f, 0.06f, 1.50f};

    /** Beine: Hüft-x, Hüfthöhe, Länge bis zum Huf, Phasenversatz. */
    static final float[][] BISON_LEGS = {{0.75f, 0.70f, 0.70f, 0}, {0.55f, 0.70f, 0.70f, 3.14f}, {-0.95f, 0.85f, 0.85f, 1.57f}, {-1.15f, 0.85f, 0.85f, 4.71f}};
    static final float[][] ELK_LEGS = {{0.70f, 1.05f, 1.05f, 0}, {0.52f, 1.05f, 1.05f, 3.14f}, {-0.75f, 1.05f, 1.05f, 1.57f}, {-0.92f, 1.05f, 1.05f, 4.71f}};


    // ------------------------------------------------------------ Phase 8: Menschen und Pferde (Meter, x nach vorn)

    /** Wams mit Gänsebauch, Rumpf von der Hüfte zum Hals. */
    static final float[] MAN_DOUBLET = {-0.15f, 0.88f, -0.17f, 1.20f, -0.14f, 1.42f, -0.06f, 1.50f, 0.09f, 1.49f, 0.16f, 1.40f, 0.20f, 1.20f, 0.14f, 0.88f};
    /** Kürbishose. */
    static final float[] MAN_TRUNK = {-0.18f, 0.93f, -0.20f, 0.70f, 0.0f, 0.60f, 0.21f, 0.70f, 0.19f, 0.93f};
    static final float[] RUFF = {-0.10f, 1.50f, -0.13f, 1.545f, -0.04f, 1.585f, 0.09f, 1.58f, 0.15f, 1.53f, 0.12f, 1.49f, 0.0f, 1.475f};
    static final float[] HAT_CROWN = {-0.075f, 1.735f, -0.065f, 1.92f, 0.075f, 1.92f, 0.085f, 1.735f};
    static final float[] HAT_BRIM = {-0.19f, 1.735f, -0.16f, 1.765f, 0.21f, 1.765f, 0.23f, 1.735f};
    static final float[] HAT_PLUME = {-0.07f, 1.90f, -0.20f, 1.98f, -0.34f, 1.86f, -0.18f, 1.92f};
    static final float[] SKULLCAP = {-0.085f, 1.70f, -0.07f, 1.80f, 0.04f, 1.82f, 0.095f, 1.72f};
    static final float[] MORION = {-0.115f, 1.69f, -0.11f, 1.79f, -0.02f, 1.855f, 0.10f, 1.80f, 0.115f, 1.70f, 0.20f, 1.715f, -0.18f, 1.715f};
    static final float[] MORION_COMB = {-0.10f, 1.80f, -0.02f, 1.92f, 0.07f, 1.81f};
    static final float[] BREASTPLATE = {-0.13f, 0.98f, -0.16f, 1.22f, -0.13f, 1.42f, 0.09f, 1.45f, 0.17f, 1.36f, 0.22f, 1.18f, 0.12f, 0.98f};
    static final float[] APRON = {0.08f, 1.30f, 0.22f, 1.12f, 0.25f, 0.55f, 0.04f, 0.50f};
    static final float[] CAPE = {-0.14f, 1.46f, -0.33f, 0.72f, -0.10f, 0.66f, 0.0f, 1.40f};
    static final float[] HALBERD_BLADE = {0.0f, 2.02f, 0.30f, 2.05f, 0.27f, 2.27f, 0.045f, 2.30f, 0.0f, 2.46f};
    /** Dame: Spanischer Reifrock, Mieder, Kragen, Haube. */
    static final float[] LADY_BODICE = {-0.11f, 1.04f, -0.13f, 1.30f, -0.10f, 1.46f, 0.0f, 1.50f, 0.10f, 1.46f, 0.14f, 1.30f, 0.09f, 1.04f};
    static final float[] LADY_COLLAR = {-0.17f, 1.48f, -0.24f, 1.72f, -0.08f, 1.64f, 0.0f, 1.56f, 0.10f, 1.50f};
    static final float[] LADY_HEAD = {-0.07f, 1.53f, -0.09f, 1.62f, -0.06f, 1.71f, 0.02f, 1.74f, 0.09f, 1.69f, 0.10f, 1.60f, 0.06f, 1.53f};
    static final float[] LADY_HAIR = {-0.11f, 1.58f, -0.12f, 1.72f, -0.03f, 1.79f, 0.07f, 1.76f, 0.02f, 1.70f, -0.05f, 1.66f, -0.06f, 1.57f};
    /** Reiter sitzt: Oberschenkel und Stiefel zur Seite des Pferdes. */
    static final float[] SEATED_LEG = {-0.06f, 1.62f, 0.40f, 1.50f, 0.50f, 1.04f, 0.38f, 1.02f, 0.30f, 1.36f, -0.12f, 1.46f};

    /** Pferd: Rumpf, Hals mit Kopf, Mähne, Ohr, Schweif. */
    static final float[] HORSE_BODY = {-1.12f, 1.18f, -1.05f, 1.45f, -0.80f, 1.52f, -0.30f, 1.50f, 0.25f, 1.52f, 0.62f, 1.55f, 0.88f, 1.45f,
            0.98f, 1.22f, 0.92f, 0.98f, 0.55f, 0.88f, 0.0f, 0.86f, -0.45f, 0.88f, -0.85f, 0.95f, -1.12f, 1.02f};
    static final float[] HORSE_NECK = {0.55f, 1.50f, 0.78f, 1.82f, 0.98f, 2.08f, 1.12f, 2.16f, 1.28f, 2.12f, 1.55f, 1.88f, 1.68f, 1.72f,
            1.66f, 1.62f, 1.52f, 1.62f, 1.30f, 1.74f, 1.12f, 1.66f, 0.98f, 1.40f, 0.90f, 1.20f};
    static final float[] HORSE_EAR = {1.10f, 2.14f, 1.09f, 2.31f, 1.19f, 2.15f};
    static final float[] HORSE_MANE = {0.60f, 1.55f, 0.80f, 1.88f, 1.0f, 2.14f, 1.10f, 2.14f, 0.92f, 1.78f, 0.72f, 1.46f};
    static final float[] HORSE_TAIL = {-1.10f, 1.42f, -1.30f, 1.20f, -1.40f, 0.65f, -1.31f, 0.60f, -1.20f, 1.05f, -1.07f, 1.30f};
    /** Hüfte x, y, Beinlänge, Phase, Breite oben. */
    static final float[][] HORSE_LEGS = {{0.78f, 1.0f, 1.0f, 0f, 0.10f}, {0.60f, 1.0f, 1.0f, 3.14f, 0.10f}, {-0.88f, 1.05f, 1.05f, 3.14f, 0.15f}, {-0.70f, 1.05f, 1.05f, 0f, 0.15f}};

    /** Kutschenkasten. */
    static final float[] COACH_BODY = {-1.85f, 0.80f, 1.05f, 0.80f, 1.15f, 1.50f, 0.95f, 2.12f, 0.55f, 2.40f, -1.20f, 2.40f, -1.65f, 2.18f, -1.85f, 1.60f};
    static final float[] COACH_WINDOW = {-1.15f, 1.50f, 0.55f, 1.50f, 0.50f, 2.12f, -1.10f, 2.12f};
    static final float[] COACH_SEAT = {0.98f, 1.40f, 1.85f, 1.40f, 1.85f, 1.52f, 1.10f, 1.58f};
    static final float[] COACH_POLE = {1.0f, 0.93f, 3.55f, 1.08f, 3.55f, 1.15f, 1.0f, 1.01f};
    /** Karren. */
    static final float[] CART_BED = {-1.35f, 0.80f, 1.05f, 0.80f, 1.10f, 1.18f, -1.40f, 1.18f};
    static final float[] CART_POLE = {1.0f, 0.93f, 2.4f, 1.08f, 2.4f, 1.14f, 1.0f, 1.00f};
    static final float[] CART_SEAT = {0.55f, 1.18f, 1.10f, 1.18f, 1.10f, 1.30f, 0.55f, 1.30f};

    public void clear() { n = 0; }

    public int add(byte k, double px, double py, double pz, double hdx, double hdz, double sc) {
        if (n == x.length) return -1;
        int i = n++;
        kind[i] = k; x[i] = (float) px; y[i] = (float) py; z[i] = (float) pz;
        double l = Math.hypot(hdx, hdz);
        hx[i] = (float) (hdx / l); hz[i] = (float) (hdz / l);
        scale[i] = (float) sc; step[i] = 0; gait[i] = 0; graze[i] = 0; amb[i] = 1; variant[i] = 0;
        return i;
    }
}
