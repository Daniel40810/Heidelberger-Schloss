package com.dan.heidelberg.tools;

import com.dan.heidelberg.castle.Klang;
import com.dan.heidelberg.castle.Wasserorgel;
import java.io.File;

/** Schreibt Orgel, Knall und Donner als WAV und meldet Spitzenwert, Effektivwert und den Grundton (Nulldurchgänge). */
public final class KlangCheck {
    public static void main(String[] a) throws Exception {
        File o = new File(a.length > 0 ? a[0] : "wav"); o.mkdirs();
        float[] org = Wasserorgel.render(30);
        Klang.writeWav(new File(o, "orgel.wav"), org);
        Klang.writeWav(new File(o, "knall.wav"), Klang.boomClip());
        Klang.writeWav(new File(o, "donner.wav"), Klang.thunderClip());
        stat("Orgel", org); stat("Knall", Klang.boomClip()); stat("Donner", Klang.thunderClip());
        // Grundton je Sekunde der Orgel (Autokorrelation 80..900 Hz)
        for (int s = 0; s < 30; s += 2) {
            int n = 4096, off = s * Klang.SR; double best = 0; int bl = 0;
            for (int lag = Klang.SR / 900; lag < Klang.SR / 80; lag++) {
                double c = 0; for (int i = 0; i < n; i++) c += org[off + i] * org[off + i + lag];
                if (c > best) { best = c; bl = lag; }
            }
            System.out.printf("  %2d s: %.0f Hz%n", s, (double) Klang.SR / bl);
        }
    }
    static void stat(String n, float[] x) {
        double pk = 0, sq = 0; for (float v : x) { pk = Math.max(pk, Math.abs(v)); sq += v * v; }
        System.out.printf("%s: %.1f s, Spitze %.2f, Effektiv %.3f%n", n, x.length / (double) Klang.SR, pk, Math.sqrt(sq / x.length));
    }
}
