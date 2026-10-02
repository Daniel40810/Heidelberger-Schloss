package com.dan.heidelberg.effects;

/**
 * Talnebel im Neckartal: Strahlungsnebel, wie er an klaren, windstillen Nächten im Herbst und Winter in der
 * Rheinebene und im Tal entsteht. Wie oft es Nebeltage gibt, ist ein Modell (Wahrscheinlichkeit je Monat, jeden
 * Tag gleich gewürfelt). Der Nebel baut sich am Abend auf, ist kurz nach Sonnenaufgang am dichtesten und wird
 * von der Sonne aufgezehrt: je höher sie steigt, desto tiefer sinkt die Bank und desto größer werden die Lücken.
 * Im Winter steht die Sonne zu flach, dann hält er sich bis zum Nachmittag.
 */
public final class ValleyFog {
    /** Wahrscheinlichkeit eines Nebeltags je Monat (Januar bis Dezember). */
    public static final double[] P = {0.40, 0.30, 0.28, 0.18, 0.12, 0.07, 0.06, 0.10, 0.35, 0.60, 0.55, 0.45};
    /** Dichte je Meter im dichtesten Teil bei „100 %“. */
    static final double PEAK = 0.0068;

    /** Ergebnis: Obergrenze (Weltkoordinate y, Hof = 0), Dichte je Meter, Abbrand 0..1, ob heute ein Nebeltag ist. */
    public float top, amount, burn;
    public boolean fogDay;
    /** Von Hand: Nebeltag erzwingen, Stärke (Faktor, 1 = Vorgabe). */
    public volatile boolean forced;
    public volatile double scale = 1;

    private static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** Ist an diesem Tag Nebel? */
    public static boolean isFogDay(int day) {
        int mo = Math.min(11, (int) ((day - 1) / 30.5));
        return new java.util.Random(day * 7919L + 13).nextDouble() < P[mo];
    }

    /**
     * Zustand zu Tag, Uhrzeit, Sonnenhöhe (Grad), Bewölkung, Regen und Wind (m/s).
     */
    public void update(int day, double hour, double sunElevDeg, double overcast, double rain, double windMs) {
        fogDay = forced || isFogDay(day);
        java.util.Random r = new java.util.Random(day * 104729L + 5);
        r.nextDouble();
        // Obergrenze über dem Hof: von knapp unter den Dächern bis über die Türme
        top = forced ? 22f : (float) (8 + 30 * r.nextDouble());
        double hh = hour < 12 ? hour + 24 : hour;
        double presence = smooth(19.5, 28, hh);
        double wind = 1 - 0.55 * smooth(3.5, 8, windMs);
        double wet = 1 - 0.6 * smooth(0.3, 0.8, rain);
        amount = (float) (fogDay ? PEAK * scale * presence * wind * wet : 0);
        // Die Sonne zehrt den Nebel auf; unter Hochnebel (Bewölkung) kaum
        burn = (float) (smooth(3, 26, sunElevDeg) * (1 - 0.55 * overcast));
    }
}
