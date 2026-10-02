package com.dan.heidelberg.effects;

import com.dan.heidelberg.core.Mesh;
import com.dan.heidelberg.core.ShadowMap;

/**
 * Alles, was vom Stand von Sonne und Mond abhängt: Himmel und drei Schattenkarten: eine nahe über dem Schloss (0,12 m), eine feine über
 * Schloss, Altstadt und Neckar (0,7 m je Kartenpunkt) und eine weite über Tal und Berge (knapp 7 m), die
 * die langen Schatten der Hänge am Abend fängt. Zwei solcher Objekte wechseln sich ab, damit ein
 * Hintergrund-Thread das nächste berechnen kann, während das aktuelle gezeichnet wird (Zeitraffer).
 * Aus Semiramis übernommen.
 */
public final class LightingEngine {
    /** Mitte und halbe Kantenlängen der beiden Karten in Metern. */
    public static final double FCX = -250, FCY = -20, FCZ = -350, FINE_HALF = 1400;
    /** Mitte der feinen Karte (verschiebbar, etwa zu einem anderen Ort). */
    public static volatile double centerX = FCX, centerZ = FCZ;
    public static final double WCX = -400, WCY = 60, WCZ = -500, WIDE_HALF = 14000;

    public final Sky sky = new Sky();
    /** Nahkarte über Schloss und Hortus: rund 0,12 m je Kartenpunkt, damit schmale Fenster Lichtschächte werfen. */
    public static final double CCX = 40, CCY = 10, CCZ = 40, CLOSE_HALF = 190;
    public final ShadowMap close, fine, wide;
    public final double[] sun = new double[3];

    public LightingEngine(int shadowSize) {
        close = new ShadowMap(shadowSize * 3 / 4);
        fine = new ShadowMap(shadowSize);
        wide = new ShadowMap(shadowSize);
    }

    /** Himmel und Schatten zu Sonne und Mond; nachts werfen die Dinge Mondschatten. */
    public void compute(Mesh m, double[] sunDir, double[] moonDir, double moonLit, double haze) {
        sky.update(sunDir, moonDir, moonLit, haze);
        double[] dir = sky.sun;
        sun[0] = dir[0]; sun[1] = dir[1]; sun[2] = dir[2];
        boolean lightUp = sky.sunR + sky.sunG + sky.sunB > 1e-4 && dir[1] > -0.02;
        if (lightUp && m != null && m.nt > 0) {
            close.render(m, dir, CCX, CCY, CCZ, CLOSE_HALF);
            fine.render(m, dir, centerX, FCY, centerZ, FINE_HALF);
            wide.render(m, dir, WCX, WCY, WCZ, WIDE_HALF);
        } else {
            close.valid = false;
            fine.valid = false;
            wide.valid = false;
        }
    }

    /** Sonnenanteil 0..1; soft = false nimmt einen einzelnen gefilterten Abgriff (für Fernes). */
    public float lit(double x, double y, double z, double slope, boolean soft) {
        float v = close.litOrMiss(x, y, z, slope, soft, 0.9);
        if (v >= 0) return v;
        v = fine.litOrMiss(x, y, z, slope, soft, 0.55);
        if (v >= 0) return v;
        // Die weite Karte ist grob: immer weich und breiter gefiltert, sonst werden lange Schatten treppig
        v = wide.litOrMiss(x, y, z, slope, true, 1.4);
        return v >= 0 ? v : 1;
    }
}
