package com.dan.heidelberg.effects;

/**
 * Klima in Heidelberg, genähert nach den Normalwerten 1991–2020 des Deutschen Wetterdienstes für die
 * Rheinebene (mittlere Höchst- und Tiefstwerte je Monat, aus dem Gedächtnis gerundet; jährlich rund
 * 11 °C, 745 mm). Daraus:
 * <ul>
 * <li>die Lufttemperatur zu Tag und Stunde: Tiefstwert um 6:30, Höchstwert um 15:30, dazwischen
 * ein Kosinusverlauf;</li>
 * <li>wie gut man Dampf sieht: kalte Luft kann wenig Wasser halten, der Dampf kondensiert zu
 * dichten Wolken; an warmen Nachmittagen verdunstet er schnell;</li>
 * <li>die Schneedecke: ein einfaches Tagesmodell über die Monatsmittel. Unter 0 °C fällt Schnee und
 * bleibt liegen, darüber schmilzt er nach Gradtagen. So liegt Schnee von November bis in den Mai.</li>
 * </ul>
 */
public final class Climate {
    /** Mittlere Höchst- und Tiefstwerte in °C, Januar bis Dezember. */
    public static final double[] HI_C = {5.5, 7.4, 12.1, 17.3, 21.4, 25.0, 27.2, 26.8, 21.9, 15.4, 9.4, 6.0};
    public static final double[] LO_C = {-0.3, 0.0, 2.8, 6.0, 10.2, 13.6, 15.5, 15.0, 11.2, 7.2, 3.2, 0.7};
    static final int[] MID = {15, 45, 74, 105, 135, 166, 196, 227, 258, 288, 319, 349};
    private static final double[] SNOW = new double[366];

    static { snowTable(); }

    private static void snowTable() {
        // Schneedecke: zwei Jahre durchrechnen, damit der Winter über den Jahreswechsel eingeschwungen ist
        double pack = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (int d = 1; d <= 365; d++) {
                double mean = (hi(d) + lo(d)) / 2;
                if (mean < 0.5) pack += 0.5;               // Schneetage (im Rheintal selten und kurz)
                else pack -= mean * 2.4;                  // Schmelze nach Gradtagen
                if (lo(d) > 2) pack -= 3;                 // milde Nächte: schneller weg
                pack = Math.max(0, Math.min(120, pack));
                SNOW[d] = pack;
            }
        }
    }

    private Climate() { }

    private static double f2c(double c) { return c; }

    /** Normalwert (Höchst- oder Tiefstwert) für Tag d, zwischen den Monatsmitten linear. */
    private static double interp(double[] tab, int d) {
        int m = 0;
        while (m < 11 && d > MID[m + 1]) m++;
        int a = d < MID[0] ? 11 : m, b = d < MID[0] ? 0 : (m + 1) % 12;
        double da = MID[a], db = MID[b];
        if (d < MID[0]) da -= 365;
        if (b == 0 && d >= MID[11]) db += 365;
        double t = (d - da) / (db - da);
        return f2c(tab[a] + (tab[b] - tab[a]) * t);
    }

    public static double hi(int d) { return interp(HI_C, d); }
    public static double lo(int d) { return interp(LO_C, d); }

    /** Lufttemperatur in °C am Tag d zur Stunde h (Uhrzeit). */
    public static double air(int d, double h) {
        double lo = lo(d), hi = hi(d);
        double u;
        if (h >= 6.5 && h <= 15.5) u = (1 - Math.cos(Math.PI * (h - 6.5) / 9)) / 2;
        else {
            double s = h > 15.5 ? h - 15.5 : h + 24 - 15.5;          // 0..15 Stunden nach dem Höchstwert
            u = (1 + Math.cos(Math.PI * s / 15)) / 2;
        }
        return lo + (hi - lo) * u;
    }

    /** Sichtbarkeit von Dampf und Nebel 0,15..1: je kälter, desto dichter. */
    public static double steam(double t) {
        return Math.max(0.15, Math.min(1, (22 - t) / 28));
    }

    /** Schneedecke 0..1 am Tag d. */
    public static double snow(int d) {
        return Math.max(0, Math.min(1, SNOW[Math.max(1, Math.min(365, d))] / 15));
    }
}
