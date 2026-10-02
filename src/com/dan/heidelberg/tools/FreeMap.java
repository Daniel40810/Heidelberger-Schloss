package com.dan.heidelberg.tools;

import com.dan.heidelberg.core.Solids;
import com.dan.heidelberg.world.Heidelberg;
import com.dan.heidelberg.world.World;

/** Wegekarte: wo kann ein Mensch (Radius 0,4 m, Höhe 0,3…1,7 m) auf dem Hof stehen? */
public final class FreeMap {
    public static void main(String[] a) {
        World w = Heidelberg.build(false);
        Solids so = w.solids;
        double x0 = Double.parseDouble(a[0]), x1 = Double.parseDouble(a[1]), z0 = Double.parseDouble(a[2]), z1 = Double.parseDouble(a[3]), st = Double.parseDouble(a[4]);
        StringBuilder hd = new StringBuilder("      ");
        for (double x = x0; x <= x1; x += st) hd.append(((int) Math.abs(x) % 10 == 0) ? String.valueOf((int) Math.abs(x) / 10 % 10) : " ");
        System.out.println(hd);
        for (double z = z0; z <= z1; z += st) {
            StringBuilder sb = new StringBuilder(String.format("%5.0f ", z));
            for (double x = x0; x <= x1; x += st) {
                double t = so.ray(x, 1.8, z, 0, -1, 0, 8), y = t < 0 ? -99 : 1.8 - t;
                boolean bad = t < 0;
                for (double h : new double[]{0.55, 1.0, 1.55}) if (!bad && so.clearance(x, y + h, z, 0.45, null) < 0.44) bad = true;
                char c = t < 0 ? '?' : (bad ? '#' : '.');
                if (!bad && Math.abs(y) > 0.15) c = (char) ('a' + Math.min(20, (int) Math.abs(y * 2)));
                sb.append(c);
            }
            System.out.println(sb);
        }
    }
}
