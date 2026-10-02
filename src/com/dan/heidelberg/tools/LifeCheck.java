package com.dan.heidelberg.tools;

import com.dan.heidelberg.castle.Life;
import com.dan.heidelberg.core.Solids;
import com.dan.heidelberg.world.Heidelberg;
import com.dan.heidelberg.world.World;

/**
 * Prüft das Leben (Phase 8) ohne Fenster: Gehen Menschen, Pferde und Wagen durch Mauern, Pfosten oder Säulen? Jeder Weg
 * wird alle 0,5 m abgetastet; Leib = Zylinder (Halbmesser 0,35 m für Menschen, 1,0 m für Gespanne) in drei Höhen über dem Boden.
 */
public final class LifeCheck {
    public static void main(String[] a) {
        World w = Heidelberg.build(false);
        Solids so = w.solids;
        Life life = w.life;
        int bad = 0, n = 0;
        for (Life.Actor ac : life.actors()) {
            n++;
            double rad = ac.kind == com.dan.heidelberg.core.Animals.RIDER ? 0.75 : 0.35;
            int v = 0; double[] first = null; double minC = 9;
            double[] o = new double[4];
            int steps = ac.stand ? 1 : (int) (ac.path.total / 0.5) + 1;
            double y = 0;
            for (int k = 0; k < steps; k++) {
                double x, z;
                if (ac.stand) { x = ac.fx; z = ac.fz; }
                else { ac.path.at(k * 0.5, o); x = o[0]; z = o[1]; }
                y = life.groundAt(x, z, y);
                double c = 9;
                for (double h : new double[]{1.0, 1.4, 1.7}) c = Math.min(c, so.clearance(x, y + h, z, 1.5, null));
                minC = Math.min(minC, c);
                if (c < rad) { v++; if (first == null) first = new double[]{x, y, z}; }
            }
            if (v > 0) bad++;
            System.out.printf("%-8s kind %2d cls %d  %s  kleinster Abstand %.2f  Verstöße %d%s%n", ac.stand ? "steht" : (ac.ping ? "pendelt" : "geht"), ac.kind, ac.cls, ac.stand ? String.format("(%.1f, %.1f)", ac.fx, ac.fz) : String.format("Weg %.0f m", ac.path.total), minC, v,
                    first == null ? "" : String.format("   erster bei %.1f / %.1f / %.1f", first[0], first[1], first[2]));
        }
        System.out.println("Mitwirkende: " + n + ", mit Verstoß: " + bad);
        for (int q = 0; q < 2; q++) {
            Life.Path p = q == 0 ? life.coachPath() : life.cartPath();
            double rad = 1.0;
            int v = 0; double[] first = null; double minC = 9, y = 0;
            double[] o = new double[4];
            for (double d = 0; d < p.total; d += 0.5) {
                p.at(d, o);
                y = life.groundAt(o[0], o[1], y);
                double c = 9;
                for (double h : new double[]{1.2, 1.6, 2.0}) c = Math.min(c, so.clearance(o[0], y + h, o[1], 2.5, null));
                minC = Math.min(minC, c);
                if (c < rad) { v++; if (first == null) first = new double[]{o[0], y, o[1], d}; }
            }
            System.out.printf("%s: Weg %.0f m, kleinster Abstand %.2f m, Verstöße %d%s%n", q == 0 ? "Kutsche" : "Karren", p.total, minC, v,
                    first == null ? "" : String.format("   erster bei %.1f / %.1f / %.1f (Weglänge %.0f)", first[0], first[1], first[2], first[3]));
        }
    }
}
