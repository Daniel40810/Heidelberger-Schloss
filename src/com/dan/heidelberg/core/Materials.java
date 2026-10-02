package com.dan.heidelberg.core;

/**
 * Prozedurale Materialien: Grundfarbe (linear) aus Weltposition und Normale, dazu Glanz.
 * Feine Zeichnung wird nur gerechnet, solange ein Pixel kleiner ist als sie; in der Ferne
 * gehen die Flächen in einen gemittelten Farbton über, damit nichts flimmert. Keine Bilddateien.
 */
public final class Materials {
    public static final float[] SPEC = new float[Mat.COUNT];
    public static final float[] SHIN = new float[Mat.COUNT];
    /** Durchscheinen im Gegenlicht (Nadeln). */
    public static final float[] TRANS = new float[Mat.COUNT];

    static {
        SPEC[Mat.SINTER] = 0.04f; SHIN[Mat.SINTER] = 24;
        SPEC[Mat.WALL] = 0.02f; SHIN[Mat.WALL] = 12;
        SPEC[Mat.ROOF] = 0.05f; SHIN[Mat.ROOF] = 22;
        SPEC[Mat.SANDSTONE] = 0.025f; SHIN[Mat.SANDSTONE] = 14;
        SPEC[Mat.MARK] = 0.06f; SHIN[Mat.MARK] = 30;
        SPEC[Mat.ROCK] = 0.02f; SHIN[Mat.ROCK] = 16;
        SPEC[Mat.RIM] = 0.10f; SHIN[Mat.RIM] = 40;
        SPEC[Mat.CONE] = 0.05f; SHIN[Mat.CONE] = 30;
        SPEC[Mat.NEEDLES] = 0.03f; SHIN[Mat.NEEDLES] = 14;
        TRANS[Mat.NEEDLES] = 0.18f;
        SPEC[Mat.SPRUCE] = 0.03f; SHIN[Mat.SPRUCE] = 14;
        TRANS[Mat.SPRUCE] = 0.12f;
        SPEC[Mat.LEAVES] = 0.05f; SHIN[Mat.LEAVES] = 20;
        TRANS[Mat.LEAVES] = 0.35f;
        SPEC[Mat.MARBLE] = 0.16f; SHIN[Mat.MARBLE] = 260;
        SPEC[Mat.GLASS] = 0.55f; SHIN[Mat.GLASS] = 400;
        SPEC[Mat.LIGHT] = 0.3f; SHIN[Mat.LIGHT] = 200;
        SPEC[Mat.STATUE] = 0.03f; SHIN[Mat.STATUE] = 18;
        SPEC[Mat.HERALD] = 0.07f; SHIN[Mat.HERALD] = 40;
        SPEC[Mat.GRAVEL] = 0.02f; SHIN[Mat.GRAVEL] = 10;
        SPEC[Mat.HEDGE] = 0.03f; SHIN[Mat.HEDGE] = 14;
        TRANS[Mat.HEDGE] = 0.14f;
        SPEC[Mat.PLASTER] = 0.02f; SHIN[Mat.PLASTER] = 12;
        SPEC[Mat.PLANNED] = 0.025f; SHIN[Mat.PLANNED] = 14;
        SPEC[Mat.ORANGE] = 0.06f; SHIN[Mat.ORANGE] = 24;
        SPEC[Mat.LAWN] = 0.02f; SHIN[Mat.LAWN] = 10;
        TRANS[Mat.LAWN] = 0.05f;
        TRANS[Mat.ORANGE] = 0.2f;
        SPEC[Mat.CLOTH] = 0.01f; SHIN[Mat.CLOTH] = 8;
        SPEC[Mat.OAK] = 0.05f; SHIN[Mat.OAK] = 22;
        SPEC[Mat.IRON] = 0.35f; SHIN[Mat.IRON] = 90;
        SPEC[Mat.WAX] = 0.10f; SHIN[Mat.WAX] = 30;
        SPEC[Mat.MIRROR] = 0.80f; SHIN[Mat.MIRROR] = 500;
        SPEC[Mat.GOLD] = 0.55f; SHIN[Mat.GOLD] = 140;
        SPEC[Mat.LINEN] = 0.01f; SHIN[Mat.LINEN] = 8;
        SPEC[Mat.SOOT] = 0.02f; SHIN[Mat.SOOT] = 12;
        SPEC[Mat.RUBBLE] = 0.015f; SHIN[Mat.RUBBLE] = 10;
        SPEC[Mat.CHAR] = 0.03f; SHIN[Mat.CHAR] = 16;
    }

    /**
     * Farben des Espenlaubs nach der Jahreszeit (linear, 3 je Ton, Töne von 0 bis 1 gleichmäßig
     * verteilt); gesetzt von {@link com.dan.heidelberg.world.Grove}. Der Ton kommt aus dem Ort: ganze Haine
     * (in der Natur ein Klon aus einer Wurzel) färben sich gemeinsam, darin jedes Büschel etwas anders.
     */
    public static volatile float[] leafLut = {0.08f, 0.22f, 0.04f, 0.08f, 0.22f, 0.04f};

    /** Achse der liegenden Fässer (Phase 8): Dauben und Reifen sind nach der Normale um diese Achse gezeichnet. */
    public static final int BARREL_AXIS_X = 0;

    /** Nur geplante, nie gebaute Teile des Gartens blau getönt zeigen (Schalter im Bedienfeld). */
    public static volatile boolean tintPlanned = true;

    /** Das Gelände liefert Bodenkarte und Quellen; gesetzt, sobald die Szene steht. */
    public static volatile Terrain terrain;

    private Materials() { }

    private static float smooth(float a, float b, float x) {
        float t = (x - a) / (b - a);
        t = t < 0 ? 0 : (t > 1 ? 1 : t);
        return t * t * (3 - 2 * t);
    }

    /** Normale mit Rauschen verkippen (Perlen, Nadelbüschel). */
    private static void bump(float[] n, float x, float y, float z, float amp, float freq) {
        float a = Noise.tex(x * freq + z * 0.7f, y * freq) - 0.5f, b = Noise.tex(z * freq + 17.3f, y * freq + x * 0.6f) - 0.5f;
        float c = Noise.tex(y * freq + 5.5f, x * freq + 9.1f) - 0.5f;
        n[0] += a * amp * 2; n[1] += c * amp * 2; n[2] += b * amp * 2;
        float l = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
        n[0] /= l; n[1] /= l; n[2] /= l;
    }

    /**
     * Grundfarbe am Punkt nach o, Normale n darf verkippt werden; liefert die Verschattung 0..1
     * für das Umgebungslicht. foot = Pixelgröße in Metern; gm ist ein Hilfspuffer (5 Werte).
     */
    public static float surface(int m, float x, float y, float z, float[] n, float foot, float[] gm, float[] o) {
        float fineVis = 1 - smooth(0.05f, 0.4f, foot);
        switch (m) {
            case Mat.TERRAIN: {
                Terrain t = terrain;
                if (t == null) { o[0] = 0.2f; o[1] = 0.18f; o[2] = 0.1f; return 1; }
                t.albedo(x, y, z, n[0], n[1], n[2], foot, gm, o);
                return 1;
            }
            case Mat.SINTER: {
                float a = Noise.tex(x * 0.8f + z * 0.3f, y * 1.4f), b = Noise.tex(x * 0.13f, z * 0.13f + y * 0.2f);
                float k = 0.82f + 0.3f * (a - 0.5f) * (1 - smooth(0.1f, 0.6f, foot)) + 0.12f * (b - 0.5f);
                o[0] = 0.53f * k; o[1] = 0.51f * k; o[2] = 0.46f * k;
                return 1;
            }
            case Mat.RIM: {
                // Geyserit-Perlen: rundliche Knollen, zum Wasser hin heller und feucht
                if (fineVis > 0) bump(n, x, y, z, 0.35f * fineVis, 3.2f);
                float a = Noise.tex(x * 3.1f + y, z * 3.1f);
                float k = 0.85f + 0.25f * (a - 0.5f) * fineVis;
                o[0] = 0.56f * k; o[1] = 0.54f * k; o[2] = 0.50f * k;
                tint(x, z, o);
                return 0.85f + 0.15f * a;
            }
            case Mat.CONE: {
                // Sinterkegel: waagrechte Wachstumslagen, perlige Oberfläche, braune Läufe vom Abfluss
                if (fineVis > 0) bump(n, x, y, z, 0.28f * fineVis, 2.1f);
                float band = Noise.tex(y * 2.6f + Noise.tex(x * 0.4f, z * 0.4f) * 1.5f, 0.37f);
                float streak = smooth(0.55f, 0.75f, Noise.tex(x * 0.9f + z * 0.9f, y * 0.12f + 3.3f));
                float k = 0.78f + 0.3f * (band - 0.5f);
                float r = 0.54f * k, g = 0.51f * k, b = 0.46f * k;
                float low = 1 - smooth(0.2f, 2.5f, y - baseY(x, z));
                float br = streak * (0.35f + 0.65f * low);
                r += (0.36f - r) * br; g += (0.22f - g) * br; b += (0.12f - b) * br;
                o[0] = r; o[1] = g; o[2] = b;
                tint(x, z, o);
                float cav = Noise.tex(x * 1.3f + 7, y * 1.3f + z * 0.2f);
                return 0.75f + 0.25f * cav;
            }
            case Mat.WOOD: {
                float gr = Noise.tex(x * 0.4f + z * 0.4f, y * 6f);
                float k = 0.8f + 0.35f * (gr - 0.5f);
                o[0] = 0.20f * k; o[1] = 0.13f * k; o[2] = 0.075f * k;
                return 1;
            }
            case Mat.BOARD: {
                // verwitterte Bretter: graubraun, Maserung, dunkle Fugen quer (alle 0,15 m) nur nah
                float gr = Noise.tex(x * 0.9f + z * 0.2f, z * 0.9f + y * 5f);
                float k = 0.8f + 0.3f * (gr - 0.5f);
                float r = 0.24f * k, g = 0.19f * k, b = 0.14f * k;
                if (fineVis > 0 && n[1] > 0.7f) {
                    float u = (x * 0.7071f + z * 0.7071f) / 0.15f;
                    float f = u - (float) Math.floor(u);
                    if (f < 0.1f) { float d = 1 - 0.55f * fineVis; r *= d; g *= d; b *= d; }
                }
                o[0] = r; o[1] = g; o[2] = b;
                // festgetretener Schnee auf den Brettern (nicht an heißen Stellen)
                if (Thermal.snow > 0.01f && n[1] > 0.7f) {
                    float c = Thermal.snow * 0.8f * (0.7f + 0.3f * gr) * warmFree(x, z);
                    o[0] += (0.66f - o[0]) * c; o[1] += (0.68f - o[1]) * c; o[2] += (0.72f - o[2]) * c;
                }
                return 1;
            }
            case Mat.MARK: {
                float band = y - (float) Math.floor(y / 0.3f) * 0.3f;
                if (band < 0.08f) { o[0] = 0.75f; o[1] = 0.74f; o[2] = 0.70f; }
                else { o[0] = 0.80f; o[1] = 0.17f; o[2] = 0.025f; }
                return 1;
            }
            case Mat.ROCK: {
                float a = Noise.tex(x * 0.3f + y * 0.2f, z * 0.3f);
                float k = 0.85f + 0.3f * (a - 0.5f);
                o[0] = 0.28f * k; o[1] = 0.235f * k; o[2] = 0.21f * k;
                return 1;
            }
            case Mat.BARK: {
                float a = Noise.tex(x * 3 + z * 3, y * 0.8f);
                float k = 0.8f + 0.35f * (a - 0.5f);
                o[0] = 0.085f * k; o[1] = 0.066f * k; o[2] = 0.052f * k;
                return 0.8f;
            }
            case Mat.NEEDLES: {
                // Drehkiefer: dunkles Blaugrün, je Baum etwas anders, Büschel als verkippte Normalen
                bump(n, x, y, z, 0.45f, 1.3f);
                float tree = Noise.tex(x * 0.21f + 11, z * 0.21f + 5);
                float a = Noise.tex(x * 1.9f + y * 0.7f, z * 1.9f + y * 0.5f);
                float k = 0.75f + 0.5f * (a - 0.5f);
                o[0] = (0.030f + 0.012f * tree) * k; o[1] = (0.058f + 0.02f * tree) * k; o[2] = (0.030f + 0.006f * tree) * k;
                frost(x, z, n[1], a, 0.75f, o);
                // Tiefe in der Krone: innen dunkler
                return 0.55f + 0.45f * a;
            }
            case Mat.LEAVES: {
                bump(n, x, y, z, 0.35f, 2.2f);
                float clone = Noise.tex(x * 0.035f + 3.1f, z * 0.035f + 7.7f);
                float a = Noise.tex(x * 2.3f + y * 0.9f, z * 2.3f + y * 0.7f);
                float tone = Math.max(0, Math.min(1, 1.6f * (clone - 0.5f) + 0.5f + 0.5f * (a - 0.5f)));
                float[] lut = leafLut;
                int nt = lut.length / 3;
                float f = tone * (nt - 1);
                int i0 = Math.min(nt - 2, (int) f);
                float w = f - i0;
                float k = 0.8f + 0.4f * (a - 0.5f);
                for (int c = 0; c < 3; c++) o[c] = (lut[3 * i0 + c] + (lut[3 * i0 + 3 + c] - lut[3 * i0 + c]) * w) * k;
                frost(x, z, n[1], a, 0.8f, o);
                return 0.6f + 0.4f * a;
            }
            case Mat.WHITEBARK: {
                // Espe: kalkweiß mit Grünstich, dunkle Narben unter den Ästen
                float a = Noise.tex(x * 4 + z * 4, y * 1.2f);
                float scar = Noise.tex((x + z) * 9, y * 5.5f);
                float k = 0.85f + 0.3f * (a - 0.5f);
                if (scar > 0.78f) { o[0] = 0.05f; o[1] = 0.05f; o[2] = 0.04f; }
                else { o[0] = 0.42f * k; o[1] = 0.45f * k; o[2] = 0.36f * k; }
                frost(x, z, n[1], a, 0.7f, o);
                return 0.9f;
            }
            case Mat.SPRUCE: {
                // Fichte und Tanne: dunkel, blaugrün, dichte Büschel
                bump(n, x, y, z, 0.4f, 1.6f);
                float tree = Noise.tex(x * 0.21f + 3, z * 0.21f + 9);
                float a = Noise.tex(x * 2.1f + y * 0.7f, z * 2.1f + y * 0.5f);
                float k = 0.72f + 0.5f * (a - 0.5f);
                o[0] = (0.020f + 0.008f * tree) * k; o[1] = (0.046f + 0.014f * tree) * k; o[2] = (0.036f + 0.012f * tree) * k;
                frost(x, z, n[1], a, 0.85f, o);
                return 0.5f + 0.45f * a;
            }
            case Mat.SNAG: {
                // abgestorbene Kiefer: silbergrau, unten weiß von aufgesogener Kieselsäure
                float a = Noise.tex(x * 2 + z * 2, y * 0.9f);
                float k = 0.85f + 0.3f * (a - 0.5f);
                float r = 0.34f * k, g = 0.32f * k, b = 0.29f * k;
                float white = 1 - smooth(0.45f, 0.75f, y - baseY(x, z));
                r += (0.66f - r) * white; g += (0.65f - g) * white; b += (0.61f - b) * white;
                o[0] = r; o[1] = g; o[2] = b;
                frost(x, z, n[1], a, 1, o);
                return 1;
            }
            case Mat.WALL: {
                // Putz in warmen Tönen, je Haus anders (Ton aus dem Block, in dem es steht)
                float h = Noise.tex(Math.round(x / 14f) * 3.7f + 11f, Math.round(z / 14f) * 3.1f + 5f);
                float a = Noise.tex(x * 0.9f + y * 0.4f, z * 0.9f + y * 0.6f);
                float k = 0.88f + 0.2f * (a - 0.5f) * fineVis;
                float ochre = smooth(0.25f, 0.5f, h), rose = smooth(0.55f, 0.8f, h);
                float r = 0.42f + 0.10f * ochre + 0.06f * rose, g = 0.37f + 0.03f * ochre - 0.07f * rose, b = 0.28f - 0.06f * ochre - 0.03f * rose;
                // Sockel und Feuchte unten dunkler
                float low = 1 - smooth(0.4f, 2.8f, y - baseY(x, z));
                k *= 1 - 0.25f * low;
                o[0] = r * k; o[1] = g * k; o[2] = b * k;
                frost(x, z, n[1], a, 0.5f, o);
                return 0.9f;
            }
            case Mat.ROOF: {
                // Biberschwanz-Ziegel: rotbraune Reihen, nah mit Fugen
                float h = Noise.tex(Math.round(x / 14f) * 2.3f + 3f, Math.round(z / 14f) * 4.1f + 9f);
                float a = Noise.tex(x * 1.7f + z * 0.5f, y * 1.4f + z * 1.3f);
                float k = 0.8f + 0.35f * (a - 0.5f);
                float row = (y * 3.6f + x * 0.2f) - (float) Math.floor(y * 3.6f + x * 0.2f);
                if (fineVis > 0 && row < 0.12f) k *= 1 - 0.35f * fineVis;
                float dark = smooth(0.6f, 0.9f, h);
                o[0] = (0.30f - 0.11f * dark) * k; o[1] = (0.095f + 0.03f * dark) * k; o[2] = (0.06f + 0.035f * dark) * k;
                // Altstadt: nicht alle Dächer frisch und rot; verwitterte braune, einzelne graue (Schiefer, Holzschindeln)
                float town = smooth(200f, 280f, (float) Math.hypot(x, z));
                if (town > 0) {
                    float h2 = Noise.tex(Math.round(x / 11f) * 5.7f + 1f, Math.round(z / 11f) * 3.3f + 7f);
                    float slate = smooth(0.80f, 0.88f, h2) * town;
                    float brown = smooth(0.42f, 0.58f, h2) * (1 - smooth(0.78f, 0.84f, h2)) * 0.75f * town;
                    o[0] += (0.20f * k - o[0]) * brown; o[1] += (0.115f * k - o[1]) * brown; o[2] += (0.07f * k - o[2]) * brown;
                    o[0] += (0.105f * k - o[0]) * slate; o[1] += (0.11f * k - o[1]) * slate; o[2] += (0.12f * k - o[2]) * slate;
                }
                frost(x, z, n[1], a, 1, o);
                return 0.95f;
            }
            case Mat.SANDSTONE:
            case Mat.PLANNED: {
                float amb = sandstone(m, x, y, z, n, fineVis, o);
                if (m == Mat.PLANNED && tintPlanned) {
                    // geplant, nie gebaut: kühles Blaugrau über dem Stein
                    float lum = 0.3f * o[0] + 0.55f * o[1] + 0.15f * o[2];
                    o[0] += (lum * 0.78f + 0.012f - o[0]) * 0.6f; o[1] += (lum * 0.98f + 0.016f - o[1]) * 0.6f; o[2] += (lum * 1.28f + 0.03f - o[2]) * 0.6f;
                }
                return amb;
            }
            case Mat.MARBLE: {
                // Marmor: warmes Weiß, graue Adern aus Rauschen in Grat-Form, Politur (Glanz im Spiegelpass)
                float a = Noise.tex(x * 0.7f + y * 0.3f, z * 0.7f + y * 0.5f);
                float w = Noise.tex(x * 1.9f + a * 2.4f, z * 1.9f + y * 1.7f - a * 1.8f);
                float vein = 1 - Math.abs(2 * w - 1);
                vein = vein * vein * vein * vein * vein * vein;
                float fine = Noise.tex(x * 6.1f + z * 3.3f, y * 5.7f + z * 2.1f);
                float v2 = 1 - Math.abs(2 * fine - 1);
                v2 = v2 * v2 * v2 * v2 * v2 * v2 * v2 * v2 * (1 - smooth(0.4f, 1.5f, foot) * 0.8f);
                float k = 0.92f + 0.12f * (a - 0.5f);
                float d = Math.min(0.75f, 0.55f * vein + 0.35f * v2);
                float r = 0.66f * k, g = 0.63f * k, b = 0.585f * k;
                o[0] = r * (1 - d) + 0.22f * d; o[1] = g * (1 - d) + 0.21f * d; o[2] = b * (1 - d) + 0.21f * d;
                return 1;
            }
            case Mat.LIGHT: {
                o[0] = 0.05f; o[1] = 0.04f; o[2] = 0.032f;
                return 1;
            }
            case Mat.GLASS: {
                o[0] = 0.014f; o[1] = 0.022f; o[2] = 0.026f;
                return 1;
            }
            case Mat.HERALD: {
                float amb = com.dan.heidelberg.castle.Heraldry.shade(x, y, z, n, foot, o);
                if (amb >= 0) return amb;
                return surface(Mat.STATUE, x, y, z, n, foot, gm, o);
            }
            case Mat.STATUE: {
                if (fineVis > 0) bump(n, x, y, z, 0.2f * fineVis, 7f);
                float a = Noise.tex(x * 3.3f + z * 2.1f, y * 3.1f);
                float k = 0.85f + 0.3f * (a - 0.5f);
                o[0] = 0.56f * k; o[1] = 0.46f * k; o[2] = 0.35f * k;
                frost(x, z, n[1], a, 0.5f, o);
                return 0.9f;
            }
            case Mat.GRAVEL: {
                // Kies: Körnung nah, in den Knotenfeldern farbige Kiesel nach Zelle (Rot, Weiß, Blau, Gelb)
                float cell = 0.07f;
                int ci = (int) Math.floor(x / cell), cj = (int) Math.floor(z / cell);
                int hs = Noise.hash(ci, cj, 31);
                float gr = 0.78f + 0.32f * ((hs & 255) / 255f);
                float r = 0.40f, g = 0.34f, b = 0.26f;
                float zone = Noise.tex(x * 0.05f + 8.1f, z * 0.05f + 2.7f);
                if (zone > 0.5f) {
                    int pick = (hs >> 9) & 3;
                    if (pick == 0) { r = 0.55f; g = 0.20f; b = 0.12f; }
                    else if (pick == 1) { r = 0.62f; g = 0.60f; b = 0.55f; }
                    else if (pick == 2) { r = 0.22f; g = 0.30f; b = 0.46f; }
                    else { r = 0.58f; g = 0.46f; b = 0.18f; }
                    float mix = smooth(0.5f, 0.56f, zone) * 0.38f * fineVis;
                    r = 0.40f + (r - 0.40f) * mix; g = 0.34f + (g - 0.34f) * mix; b = 0.26f + (b - 0.26f) * mix;
                }
                float far = smooth(0.1f, 0.6f, foot);
                gr = gr * (1 - far) + far;
                o[0] = r * gr; o[1] = g * gr; o[2] = b * gr;
                if (fineVis > 0) bump(n, x, y, z, 0.18f * fineVis, 14f);
                return 1;
            }
            case Mat.HEDGE: {
                bump(n, x, y, z, 0.4f, 2.4f);
                float a = Noise.tex(x * 3.1f + y * 1.1f, z * 3.1f + y * 0.9f);
                float k = 0.7f + 0.55f * (a - 0.5f);
                float herb = Noise.tex(Math.round(x / 1.3f) * 1.7f + 4f, Math.round(z / 1.3f) * 2.3f + 9f);
                float lav = smooth(0.6f, 0.85f, herb);
                o[0] = (0.028f + 0.05f * lav) * k; o[1] = (0.075f + 0.012f * lav) * k; o[2] = (0.026f + 0.07f * lav) * k;
                frost(x, z, n[1], a, 0.8f, o);
                return 0.5f + 0.5f * a;
            }
            case Mat.ORANGE: {
                bump(n, x, y, z, 0.35f, 3f);
                float a = Noise.tex(x * 3.3f + y * 1.3f, z * 3.3f + y * 0.7f);
                float k = 0.7f + 0.5f * (a - 0.5f);
                o[0] = 0.025f * k; o[1] = 0.075f * k; o[2] = 0.022f * k;
                float fr = Noise.tex(x * 6f + 13f, z * 6f + y * 2.3f);
                if (fr > 0.80f) { float f2 = smooth(0.80f, 0.84f, fr); o[0] += (0.75f - o[0]) * f2; o[1] += (0.30f - o[1]) * f2; o[2] += (0.02f - o[2]) * f2; }
                return 0.5f + 0.5f * a;
            }
            case Mat.LAWN: {
                // gemähter Rasen: Mähstreifen quer, feines Rauschen nah, trockene Flecken
                float stripe = ((int) Math.floor(x / 2.2f + z * 0.0f) & 1) == 0 ? 1.0f : 0.84f;
                float a = Noise.tex(x * 0.35f + 2f, z * 0.35f + 7f), b = Noise.tex(x * 6.5f + z * 3.1f, z * 6.1f) - 0.5f;
                float dry = smooth(0.62f, 0.85f, Noise.tex(x * 0.05f + 5f, z * 0.05f + 1f));
                float k = stripe * (0.9f + 0.2f * (a - 0.5f)) + 0.12f * b * fineVis;
                o[0] = (0.050f + 0.05f * dry) * k; o[1] = (0.150f - 0.02f * dry) * k; o[2] = (0.035f + 0.005f * dry) * k;
                if (fineVis > 0) bump(n, x, y, z, 0.12f * fineVis, 12f);
                frost(x, z, n[1], a, 0.9f, o);
                return 1;
            }
            case Mat.PLASTER: {
                float a = Noise.tex(x * 0.6f + y * 0.4f, z * 0.6f + y * 0.5f), b = Noise.tex(x * 2.7f + z, y * 2.2f + z * 1.3f);
                float k = 0.9f + 0.16f * (a - 0.5f) + 0.1f * (b - 0.5f) * fineVis;
                o[0] = 0.62f * k; o[1] = 0.57f * k; o[2] = 0.47f * k;
                float low = 1 - smooth(0.3f, 2.0f, y - baseY(x, z));
                float k2 = 1 - 0.18f * low;
                o[0] *= k2; o[1] *= k2; o[2] *= k2;
                return 0.9f;
            }
            case Mat.SOOT: {
                // Ruine: Sandstein vom Brand gerötet und berußt; Ruß läuft in Streifen unter Fenstern und Kanten, oben bewächst Moos
                float amb = sandstone(Mat.SANDSTONE, x, y, z, n, fineVis, o);
                float s1 = Noise.tex(x * 0.21f + z * 0.19f, y * 0.12f);
                float s2 = Noise.tex(x * 1.1f + z * 1.0f, y * 0.16f + 3f);
                float soot = smooth(0.38f, 0.72f, 0.6f * s1 + 0.4f * s2);
                float fire = smooth(0.55f, 0.85f, Noise.tex(x * 0.09f + 7f, z * 0.09f + y * 0.07f));
                // vom Feuer geröteter Stein
                o[0] += (o[0] * 1.35f + 0.03f - o[0]) * fire * 0.7f; o[1] *= 1 - 0.25f * fire; o[2] *= 1 - 0.3f * fire;
                float k = 1 - 0.78f * soot;
                float lum = 0.3f * o[0] + 0.55f * o[1] + 0.15f * o[2];
                o[0] = (o[0] * 0.5f + lum * 0.5f) * k; o[1] = (o[1] * 0.5f + lum * 0.5f) * k; o[2] = (o[2] * 0.5f + lum * 0.5f) * k * 0.95f;
                float moss = smooth(0.6f, 0.82f, Noise.tex(x * 0.7f + 2f, z * 0.7f + y * 0.9f)) * (0.25f + 0.75f * smooth(0.2f, 0.9f, n[1]));
                if (moss > 0) { o[0] += (0.045f - o[0]) * moss * 0.7f; o[1] += (0.11f - o[1]) * moss * 0.7f; o[2] += (0.03f - o[2]) * moss * 0.7f; }
                return amb;
            }
            case Mat.RUBBLE: {
                // Schutt: Brocken in Zellen von 0,45 m, Sandstein, Ruß, Asche; dazwischen Erde und Gras
                float cs = 0.45f;
                int ix = (int) Math.floor(x / cs), iz = (int) Math.floor(z / cs), iy = (int) Math.floor(y / cs);
                int hs = Noise.hash(ix, iz + iy * 31, 53);
                float tone = 0.55f + 0.7f * ((hs & 255) / 255f);
                int kind = (hs >> 8) & 3;
                float a = Noise.tex(x * 1.7f + z * 0.9f, y * 1.3f + z), b = Noise.tex(x * 5.3f + y, z * 5.1f);
                float k = tone * (0.85f + 0.3f * (a - 0.5f) + 0.2f * (b - 0.5f) * fineVis);
                if (kind == 0) { o[0] = 0.36f * k; o[1] = 0.18f * k; o[2] = 0.125f * k; }        // Sandstein
                else if (kind == 1) { o[0] = 0.15f * k; o[1] = 0.13f * k; o[2] = 0.115f * k; }  // Ruß
                else if (kind == 2) { o[0] = 0.26f * k; o[1] = 0.235f * k; o[2] = 0.21f * k; }  // Asche, Kalk
                else { o[0] = 0.30f * k; o[1] = 0.15f * k; o[2] = 0.10f * k; }
                float grass = smooth(0.5f, 0.72f, Noise.tex(x * 0.5f + 8f, z * 0.5f)) * smooth(0.3f, 0.8f, n[1]);
                if (grass > 0) { o[0] += (0.06f - o[0]) * grass * 0.8f; o[1] += (0.13f - o[1]) * grass * 0.8f; o[2] += (0.035f - o[2]) * grass * 0.8f; }
                if (fineVis > 0) bump(n, x, y, z, 0.35f * fineVis, 5f);
                return 0.8f;
            }
            case Mat.SCHNITT_STEIN: {
                // Schnittfläche durch Mauerwerk: warmer Grundton mit Schraffur unter 45°, Fugen als dunkle Linien
                float d = (x + z + y) * 1.6f;
                float hatch = smooth(0.80f, 0.92f, Math.abs((d - (float) Math.floor(d)) - 0.5f) * 2f);
                float a = Noise.tex(x * 0.8f + z * 0.8f, y * 0.8f);
                float k = 0.85f + 0.3f * (a - 0.5f);
                o[0] = (0.52f - 0.30f * hatch) * k; o[1] = (0.34f - 0.20f * hatch) * k; o[2] = (0.24f - 0.14f * hatch) * k;
                return 0.9f;
            }
            case Mat.SCHNITT_ERDE: {
                // Schnittfläche durch Erdreich und Fels: braune Körnung, helle und dunkle Steinchen
                float a = Noise.tex(x * 3.1f + z * 3.1f, y * 3.1f), b = Noise.tex(x * 11f + z * 11f + 4f, y * 11f);
                float k = 0.75f + 0.5f * (a - 0.5f) + 0.25f * (b - 0.5f);
                o[0] = 0.27f * k; o[1] = 0.17f * k; o[2] = 0.10f * k;
                if (b > 0.74f) { o[0] += 0.12f; o[1] += 0.10f; o[2] += 0.08f; }
                return 0.9f;
            }
            case Mat.CHAR: {
                // verkohltes Holz: Schwarz mit Rissen in Faserrichtung (y) und seltenem Glimmen
                float a = Noise.tex(x * 0.6f + z * 0.5f, y * 3.1f), b = Noise.tex(x * 4f + z * 3f, y * 0.8f);
                float crack = smooth(0.62f, 0.8f, b);
                float k = 0.7f + 0.5f * (a - 0.5f);
                o[0] = (0.032f + 0.02f * crack) * k; o[1] = 0.026f * k; o[2] = 0.024f * k;
                return 0.8f;
            }
            case Mat.CLOTH: {
                // Wandbehang, Teppich, Baldachin: Ton nach Stoffbahn (Zelle von 2,4 m), Webmuster nah, goldene Borte
                int ci = (int) Math.floor((x + z) / 2.4f), cj = (int) Math.floor(y / 3.2f);
                int hs = Noise.hash(ci, cj, 77);
                int pick = (hs >> 4) & 3;
                float[][] pal = {{0.30f, 0.025f, 0.03f}, {0.025f, 0.05f, 0.22f}, {0.03f, 0.13f, 0.05f}, {0.28f, 0.17f, 0.02f}};
                float weave = 0.9f + 0.1f * (float) Math.sin(x * 90f + z * 90f) * (float) Math.sin(y * 90f) * fineVis;
                float a = Noise.tex(x * 0.8f + y * 0.5f, z * 0.8f + y * 0.9f);
                float k = weave * (0.85f + 0.3f * (a - 0.5f));
                o[0] = pal[pick][0] * k; o[1] = pal[pick][1] * k; o[2] = pal[pick][2] * k;
                float fr = (x + z) * 0.9f + y * 0.0f;
                float bord = 1 - smooth(0.04f, 0.09f, Math.abs(((y * 0.8f) % 1f) - 0.5f) - 0.40f);
                if (bord > 0 && fineVis > 0) { o[0] += (0.45f - o[0]) * bord * 0.7f; o[1] += (0.30f - o[1]) * bord * 0.7f; o[2] += (0.06f - o[2]) * bord * 0.7f; }
                return 0.85f;
            }
            case Mat.LINEN: {
                float weave = 0.93f + 0.07f * (float) Math.sin(x * 120f) * (float) Math.sin(z * 120f + y * 120f) * fineVis;
                float a = Noise.tex(x * 1.1f, z * 1.1f + y);
                float k = weave * (0.94f + 0.12f * (a - 0.5f));
                o[0] = 0.62f * k; o[1] = 0.60f * k; o[2] = 0.54f * k;
                return 0.95f;
            }
            case Mat.OAK: {
                // Fassdauben: Streifen um die Achse (aus der Normale), jede Daube etwas anders, Maserung entlang der Achse
                float ang = BARREL_AXIS_X == 0 ? (float) Math.atan2(n[1], n[2]) : (float) Math.atan2(n[2], n[0]);
                float u = ang * (34f / 6.2831853f);
                int ui = (int) Math.floor(u);
                float fu = u - ui;
                int hs = Noise.hash(ui, 5, 41);
                float tone = 0.85f + 0.3f * ((hs & 255) / 255f);
                float grain = Noise.tex(x * 0.35f + ui * 3.7f, y * 5f + z * 0.3f + ui * 1.3f);
                float k = tone * (0.85f + 0.3f * (grain - 0.5f));
                if (fineVis > 0 && (fu < 0.045f || fu > 0.955f)) k *= 1 - 0.5f * fineVis;
                o[0] = 0.30f * k; o[1] = 0.185f * k; o[2] = 0.085f * k;
                return 0.9f;
            }
            case Mat.IRON: {
                float a = Noise.tex(x * 2.3f + y, z * 2.3f + y * 1.7f);
                float rust = smooth(0.62f, 0.85f, Noise.tex(x * 0.7f + 9f, y * 0.9f + z * 0.7f));
                float k = 0.8f + 0.4f * (a - 0.5f);
                o[0] = (0.055f + 0.10f * rust) * k; o[1] = (0.055f + 0.04f * rust) * k; o[2] = (0.06f + 0.0f * rust) * k;
                return 0.9f;
            }
            case Mat.WAX: {
                float a = Noise.tex(x * 9f, y * 9f + z * 9f);
                o[0] = 0.78f * (0.95f + 0.1f * a); o[1] = 0.70f * (0.95f + 0.1f * a); o[2] = 0.50f * (0.95f + 0.1f * a);
                return 1;
            }
            case Mat.MIRROR: {
                // venezianisches Spiegelglas: dunkel, leicht grünlich; das Spiegelbild kommt im Spiegelpass
                o[0] = 0.10f; o[1] = 0.115f; o[2] = 0.11f;
                return 1;
            }
            case Mat.GOLD: {
                float a = Noise.tex(x * 6f + y * 3f, z * 6f);
                float k = 0.85f + 0.3f * (a - 0.5f);
                o[0] = 0.62f * k; o[1] = 0.40f * k; o[2] = 0.08f * k;
                return 0.9f;
            }
            default:
                o[0] = 0.3f; o[1] = 0.3f; o[2] = 0.3f;
                return 1;
        }
    }

    /** Buntsandstein: Quader in versetzten Lagen (0,59 m hoch, 1,1 m lang), Fugen, Töne je Stein, Moos unten. */
    private static float sandstone(int m, float x, float y, float z, float[] n, float fineVis, float[] o) {
        if (Math.abs(n[1]) > 0.85f) { paving(x, y, z, fineVis, o); return 1; }
        float a = Noise.tex(x * 0.5f + y * 0.2f, z * 0.5f + y * 0.3f), b = Noise.tex(x * 2.4f + z * 1.1f, y * 2.2f);
        float wall = 1 - smooth(0.6f, 0.85f, Math.abs(n[1]));
        float cy = y * 1.7f;
        int ci = (int) Math.floor(cy);
        float fy = cy - ci;
        float u = (x * Math.abs(n[2]) + z * Math.abs(n[0])) / 1.1f + (ci & 1) * 0.5f + (Noise.hash(ci, 3, 9) & 255) / 255f;
        int ui = (int) Math.floor(u);
        float fu = u - ui;
        int hs = Noise.hash(ci, ui, 5);
        float tone = 1 + 0.13f * (((hs & 255) / 255f) - 0.5f) * 2 * wall;
        float k = (0.82f + 0.2f * (a - 0.5f) + 0.2f * (b - 0.5f) * fineVis) * tone;
        if (fineVis > 0 && wall > 0.1f) {
            float j = Math.min(Math.min(fy, 1 - fy) / 0.045f, Math.min(fu, 1 - fu) / 0.026f);
            if (j < 1) k *= 1 - 0.38f * (1 - j) * fineVis * wall;
        } else if (fineVis > 0 && fy < 0.06f) k *= 1 - 0.3f * fineVis;
        float bleach = smooth(0.55f, 0.8f, Noise.tex(x * 0.08f + 4f, z * 0.08f + y * 0.1f));
        float r = 0.40f + 0.06f * bleach, g = 0.20f + 0.07f * bleach, bl = 0.145f + 0.06f * bleach;
        float low = 1 - smooth(0.2f, 2.5f, y - baseY(x, z));
        o[0] = r * k * (1 - 0.3f * low); o[1] = (g + 0.04f * low) * k; o[2] = bl * k * (1 - 0.2f * low);
        frost(x, z, n[1], a, 0.5f, o);
        return 0.85f + 0.15f * b;
    }

    /** Pflaster: Platten von 0,9 × 0,6 m in versetzten Reihen, Fugen nah, Töne je Platte, grauer als die Mauern. */
    private static void paving(float x, float y, float z, float fineVis, float[] o) {
        float rz = z / 0.6f;
        int ri = (int) Math.floor(rz);
        float fz = rz - ri;
        float ux = x / 0.9f + (ri & 1) * 0.5f;
        int ui = (int) Math.floor(ux);
        float fx = ux - ui;
        int hs = Noise.hash(ri, ui, 17);
        float tone = 0.88f + 0.24f * ((hs & 255) / 255f);
        float a = Noise.tex(x * 0.4f + 3f, z * 0.4f + 1f), b = Noise.tex(x * 3.1f + z * 1.7f, z * 3.3f - x);
        float k = tone * (0.9f + 0.2f * (a - 0.5f) + 0.15f * (b - 0.5f) * fineVis);
        if (fineVis > 0) {
            float j = Math.min(Math.min(fz, 1 - fz) / 0.05f, Math.min(fx, 1 - fx) / 0.035f);
            if (j < 1) k *= 1 - 0.45f * (1 - j) * fineVis;
        }
        float grey = 0.35f;
        float r = 0.33f, g = 0.235f, bl = 0.19f;
        float lum = 0.3f * r + 0.59f * g + 0.11f * bl;
        o[0] = (r + (lum - r) * grey) * k; o[1] = (g + (lum - g) * grey) * k; o[2] = (bl + (lum - bl) * grey) * k;
        frost(x, z, 1f, a, 0.5f, o);
    }

    /**
     * Schnee auf den nach oben weisenden Flächen (Nadeln, Äste) und Raureif rundum in der Nähe
     * heißer Quellen, wenn die Luft friert: die „Geisterbäume“ der Geysirbecken im Winter.
     */
    private static void frost(float x, float z, float ny, float a, float amt, float[] o) {
        float snow = Thermal.snow, rime = Thermal.rime;
        if (snow < 0.01f && rime < 0.01f) return;
        Terrain t = terrain;
        float c = snow * smooth(0.45f, 0.9f, ny + (a - 0.5f) * 0.5f) * 0.6f;
        if (rime > 0.01f && t != null && t.thermal != null) {
            float r = rime * t.thermal.near(x, z, 90) * (0.6f + 0.4f * a);
            c = Math.max(c, r);
        }
        c = Math.min(1, c * amt);
        if (c <= 0) return;
        o[0] += (0.74f - o[0]) * c; o[1] += (0.77f - o[1]) * c; o[2] += (0.82f - o[2]) * c;
    }

    /** 1, wo der Boden kalt ist; 0 über warmem Abfluss. */
    private static float warmFree(float x, float z) {
        Terrain t = terrain;
        if (t == null || t.thermal == null) return 1;
        float tq = t.thermal.temp(x, z);
        return 1 - (float) smooth(Thermal.ambient + 3, Thermal.ambient + 12, tq);
    }

    private static float baseY(float x, float z) {
        Terrain t = terrain;
        return t == null ? 0 : t.sample(x, z);
    }

    /** Warmes Wasser färbt auch Kegel und Ränder: Matten nach der Temperatur, halb so stark. */
    private static void tint(float x, float z, float[] o) {
        Terrain t = terrain;
        if (t == null || t.thermal == null) return;
        float r = o[0], g = o[1], b = o[2];
        t.thermal.overlay(x, z, 1, o);
        o[0] = r + (o[0] - r) * 0.5f; o[1] = g + (o[1] - g) * 0.5f; o[2] = b + (o[2] - b) * 0.5f;
    }
}
