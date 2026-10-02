package com.dan.heidelberg.world;

import com.dan.heidelberg.core.Animals;
import com.dan.heidelberg.core.Terrain;
import com.dan.heidelberg.core.Thermal;
import com.dan.ground.Biome;
import com.dan.ground.GNoise;
import com.dan.ground.Meadow;
import com.dan.ground.Site;

/**
 * Die Bodendecke um Heidelberg mit dem Boden-Paket ({@link com.dan.ground}): mitteleuropäische
 * Blumenwiese mit Rispengras, Margeriten, Mohn, Kornblumen und Löwenzahn, dazu Kies und Steine, offene
 * Erde. Die Regeln kommen aus der Bodenkarte:
 * <ul>
 *   <li>Auf bebautem Boden und im Wasser wächst nichts.</li>
 *   <li>Am Ufer Seggen, Kies und Steine.</li>
 *   <li>Im Wald nur wenig Gras; an steilen Hängen Geröll aus Sandstein.</li>
 *   <li>Auf Wegen nichts, am Rand Kies.</li>
 * </ul>
 * Wind, Jahreszeit und Schnee kommen aus der Szene.
 */
public final class Sward implements Site {
    public final Meadow meadow;
    private final Terrain terrain;
    private final Roadways roads;

    public Sward(Terrain t, Roadways rw) {
        terrain = t; roads = rw;
        meadow = new Meadow(Biome.europe(), this);
        meadow.radius = 26;
        meadow.near = 7;
        meadow.seed = 1619;
    }

    @Override public float height(double x, double z) { return terrain.sample(x, z); }

    @Override public void cover(double x, double z, float[] out) {
        float[] gm = GM.get();
        terrain.ground((float) x, (float) z, gm);
        float bank = gm[0], town = gm[3], forest = gm[4];
        float fx = (float) x, fz = (float) z;
        // Hang
        float h = terrain.sample(x, z), hx = terrain.sample(x + 1.5, z) - h, hz = terrain.sample(x, z + 1.5) - h;
        float slope = (float) Math.sqrt(hx * hx + hz * hz) / 1.5f;
        float rocky = smooth(0.5f, 0.95f, slope);
        float wet = bank < 0.2f ? 0 : 1;                                     // im Wasser wächst nichts
        float shore = 1 - smooth(1.5f, 7, bank);
        // Straßen und Wege: auf der Fahrbahn nichts, am Rand Kies
        float road = roads == null ? 30 : roads.clearance(x, z);
        float verge = 1 - smooth(-0.2f, 1.2f, road);
        float walk = smooth(-1.0f, 0.6f, road);
        float zone = com.dan.heidelberg.castle.Castle.zone(x, z);
        float live = wet * walk * (1 - smooth(0.05f, 0.5f, town)) * (1 - smooth(0.0f, 0.2f, zone));
        float n1 = GNoise.value(fx * 0.07f, fz * 0.07f), n2 = GNoise.value(fx * 0.045f + 9, fz * 0.045f + 3);
        out[0] = live * (0.6f + 0.4f * n1) * (1 - 0.7f * forest) * (1 - rocky);
        out[1] = live * (0.15f + Math.max(0, n2 * 1.6f - 0.5f)) * (1 - forest) * (1 - rocky) * (1 - shore);
        out[2] = wet * Math.max(walk * Math.min(1, rocky + shore * 0.9f + 0.05f), 0.5f * verge * smooth(-1.0f, -0.2f, road)) * (1 - 0.8f * town);
        out[3] = wet * walk * Math.min(1, 0.05f + 0.1f * shore) * (1 - town);
        out[4] = Math.max(1 - smooth(2, 18, bank), 0.3f * forest);
        float zk = 1 - smooth(0.0f, 0.2f, zone);
        out[2] *= zk; out[3] *= zk; out[4] *= zk;
    }

    private static final ThreadLocal<float[]> GM = ThreadLocal.withInitial(() -> new float[5]);

    static float smooth(float a, float b, float x) {
        float t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /**
     * Ein Zeitschritt: Jahreszeit, Wind (0..1, 1 ≈ 9 m/s, Richtung wx, wz), wer das Gras niedertritt,
     * und die Kamera. Hoch über dem Boden (Luftbild) gibt es keine Bodendecke.
     */
    public void update(float t, int day, float snow, double wind, double wx, double wz, Animals an, double camX, double camY, double camZ) {
        meadow.setSeason(day, snow);
        meadow.wind.speed = (float) (wind * 9);
        meadow.wind.direction = (float) Math.toDegrees(Math.atan2(wz, wx));
        int n = an == null ? 0 : an.n;
        if (meadow.pushers.length < 4 * n) meadow.pushers = new float[4 * n + 16];
        int k = 0;
        for (int i = 0; i < n; i++) {
            if (Math.abs(an.x[i] - camX) > 40 || Math.abs(an.z[i] - camZ) > 40) continue;
            boolean person = an.kind[i] == Animals.PERSON, bison = an.kind[i] == Animals.BISON || an.kind[i] == Animals.BISON_CALF;
            meadow.pushers[4 * k] = an.x[i]; meadow.pushers[4 * k + 1] = an.z[i];
            meadow.pushers[4 * k + 2] = (person ? 0.45f : bison ? 1.3f : 0.9f) * an.scale[i];
            meadow.pushers[4 * k + 3] = person ? 0.8f : 1;
            k++;
        }
        meadow.pusherCount = k;
        float above = (float) (camY - terrain.sample(camX, camZ));
        if (above > 45) { meadow.batch.nt = 0; meadow.batch.nv = 0; return; }
        meadow.update(t, camX, camZ);
    }
}
