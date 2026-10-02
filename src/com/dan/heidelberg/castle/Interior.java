package com.dan.heidelberg.castle;

import com.dan.heidelberg.core.Mat;
import com.dan.heidelberg.core.MeshBuilder;

/**
 * Einrichtung der Innenräume (Phase 8): das Große Fass im Fassbau mit Lager, Leiter und Wappen, Tafeln, Bänke,
 * Baldachin, Spiegel und Kronleuchter im Gläsernen Saalbau, Laternen in den Grotten. Alle Maße sind angenommen; wo
 * die Quellen etwas nennen, steht es im Werkbuch (Fass 1591: etwa 127 000 l, Baumeister Michael Werner aus Landau,
 * Maße nicht überliefert). Gebaut wird in Weltkoordinaten; die Räume sind achsparallel.
 */
final class Interior {
    private Interior() { }

    // ------------------------------------------------------------ Grundformen

    /** Liegendes Fass um die Achse x: Bauch rMax in der Mitte, rEnd an den Böden, Länge L. */
    static void barrelX(MeshBuilder mb, double cx, double cy, double cz, double L, double rMax, double rEnd, int mat, int hoops) {
        final double h = L / 2;
        mb.patch((phi, s, p, n) -> {
            double k = s / h, r = rEnd + (rMax - rEnd) * (1 - k * k), dr = -2 * (rMax - rEnd) * s / (h * h);
            p[0] = cx + s; p[1] = cy + r * Math.cos(phi); p[2] = cz + r * Math.sin(phi);
            n[0] = -dr; n[1] = Math.cos(phi); n[2] = Math.sin(phi);
        }, 0, 2 * Math.PI, 44, -h, h, 16, mat);
        // Böden
        for (int e = -1; e <= 1; e += 2) {
            final int sg = e;
            mb.patch((phi, rho, p, n) -> {
                p[0] = cx + sg * h; p[1] = cy + rho * Math.cos(phi); p[2] = cz + rho * Math.sin(phi);
                n[0] = sg; n[1] = 0; n[2] = 0;
            }, 0, 2 * Math.PI, 44, 0, rEnd, 4, mat);
        }
        // Eisenreifen
        for (int i = 0; i < hoops; i++) {
            double s = -h + 0.30 + (L - 0.60) * i / Math.max(1, hoops - 1);
            double w = 0.11;
            double k0 = (s - w) / h, k1 = (s + w) / h;
            final double ra = rEnd + (rMax - rEnd) * (1 - k0 * k0) + 0.045, rb = rEnd + (rMax - rEnd) * (1 - k1 * k1) + 0.045;
            final double sa = s - w, sb = s + w;
            mb.patch((phi, t, p, n) -> {
                double r = ra + (rb - ra) * t;
                p[0] = cx + sa + (sb - sa) * t; p[1] = cy + r * Math.cos(phi); p[2] = cz + r * Math.sin(phi);
                n[0] = 0; n[1] = Math.cos(phi); n[2] = Math.sin(phi);
            }, 0, 2 * Math.PI, 44, 0, 1, 1, Mat.IRON);
            for (int sd = 0; sd < 2; sd++) {
                final double sx = sd == 0 ? sa : sb, rr = sd == 0 ? ra : rb, dir = sd == 0 ? -1 : 1;
                final double ri = rr - 0.045;
                mb.patch((phi, t, p, n) -> {
                    double r = ri + (rr - ri) * t;
                    p[0] = cx + sx; p[1] = cy + r * Math.cos(phi); p[2] = cz + r * Math.sin(phi);
                    n[0] = dir; n[1] = 0; n[2] = 0;
                }, 0, 2 * Math.PI, 44, 0, 1, 1, Mat.IRON);
            }
        }
    }

    /** Waagrechter Ring (Torus) mit Mittelpunktsradius R und Schlauchradius a. */
    static void torusH(MeshBuilder mb, double cx, double cy, double cz, double R, double a, int segPhi, int segTheta, int mat) {
        mb.patch((phi, th, p, n) -> {
            double cp = Math.cos(phi), sp = Math.sin(phi), ct = Math.cos(th), st = Math.sin(th);
            p[0] = cx + (R + a * ct) * cp; p[1] = cy + a * st; p[2] = cz + (R + a * ct) * sp;
            n[0] = ct * cp; n[1] = st; n[2] = ct * sp;
        }, 0, 2 * Math.PI, segPhi, 0, 2 * Math.PI, segTheta, mat);
    }

    static void box(MeshBuilder mb, double x0, double y0, double z0, double x1, double y1, double z1, int mat) {
        mb.box(Math.min(x0, x1), y0, Math.min(z0, z1), Math.max(x0, x1), y1, Math.max(z0, z1), mat, true);
    }

    /** Dünner Stab zwischen zwei Punkten als vierkantiger Quader entlang der Achse mit der größten Ausdehnung (für Leiter und Streben). */
    static void post(MeshBuilder mb, double x, double z, double y0, double y1, double w, int mat) {
        mb.box(x - w, y0, z - w, x + w, y1, z + w, mat, true);
    }

    // ------------------------------------------------------------ Fassbau

    /**
     * Das Große Fass von 1591 (Johann Casimir, Michael Werner aus Landau, rund 127 000 l). Maße angenommen: Das Fass von
     * 1751 misst 7 m × 8,5 m bei 228 000 l; verkleinert auf 127 000 l ergeben sich etwa 5,1 m Bauch und 6,7 m Länge. Es
     * liegt mit dem Boden zur Tür, die Achse in der Türachse (z = 16,8).
     */
    static void fass(MeshBuilder mb) {
        MeshBuilder.SkyFn keep = mb.skyFn;
        mb.skyFn = (x, y, z, nx, ny, nz) -> 0.22f;
        final double cx = 43.4, cz = 16.8, cy = 3.35, L = 6.7, rMax = 2.55, rEnd = 2.20, h = L / 2;
        barrelX(mb, cx, cy, cz, L, rMax, rEnd, Mat.OAK, 9);
        // Lager: zwei Sandsteinsockel quer unter dem Fass, daneben Keile aus Eichenholz
        for (double sx : new double[]{cx - 2.25, cx + 2.25}) {
            box(mb, sx - 0.55, 0.0, cz - 2.45, sx + 0.55, 0.78, cz + 2.45, Mat.SANDSTONE);
            for (int sd = -1; sd <= 1; sd += 2) {
                box(mb, sx - 0.45, 0.78, cz + sd * 1.55 - 0.3, sx + 0.45, 1.25, cz + sd * 1.95 + 0.3, Mat.WOOD);
            }
        }
        // Wappen der Pfalz auf dem Boden zur Tür (Annahme: Johann Casimir führte das Wappen der Kurpfalz)
        Heraldry.plaqueAt(mb, cx - h, cy + 0.15, cz, -1, 0, 1.05, 0.06);
        // Zapfhahn und Spundloch
        box(mb, cx - h - 0.12, cy - 1.75, cz - 0.07, cx - h, cy - 1.68, cz + 0.07, Mat.IRON);
        mb.cylinder(cx - h - 0.35, cz, cy - 1.78, cy - 1.65, 0.06, 0.06, 8, Mat.GOLD, true);
        mb.cylinder(cx, cz, cy + rMax - 0.02, cy + rMax + 0.09, 0.22, 0.20, 12, Mat.IRON, true);
        // Leiter an der Nordflanke des Fasses angelehnt (Annahme): steil, berührt den Bauch etwa auf Höhe der Achse
        double lx = cx + 1.0;
        double zb = cz - 3.77, zt = cz - 2.2, yt = 4.8;
        double dz = zt - zb, nl = Math.hypot(dz, yt);
        for (int sd = -1; sd <= 1; sd += 2) {
            double lxx = lx + sd * 0.28;
            double[] n1 = {0, -dz / nl, yt / nl};
            mb.quad(new double[]{lxx - 0.04, 0.0, zb}, new double[]{lxx + 0.04, 0.0, zb}, new double[]{lxx + 0.04, yt, zt}, new double[]{lxx - 0.04, yt, zt}, n1[0], n1[1], n1[2], Mat.WOOD);
            mb.quad(new double[]{lxx - 0.04, 0.0, zb - 0.07}, new double[]{lxx + 0.04, 0.0, zb - 0.07}, new double[]{lxx + 0.04, yt, zt - 0.07}, new double[]{lxx - 0.04, yt, zt - 0.07}, -n1[0], -n1[1], -n1[2], Mat.WOOD);
            mb.quad(new double[]{lxx - 0.04, 0.0, zb}, new double[]{lxx - 0.04, yt, zt}, new double[]{lxx - 0.04, yt, zt - 0.07}, new double[]{lxx - 0.04, 0.0, zb - 0.07}, -1, 0, 0, Mat.WOOD);
            mb.quad(new double[]{lxx + 0.04, 0.0, zb}, new double[]{lxx + 0.04, yt, zt}, new double[]{lxx + 0.04, yt, zt - 0.07}, new double[]{lxx + 0.04, 0.0, zb - 0.07}, 1, 0, 0, Mat.WOOD);
        }
        for (int i = 1; i <= 12; i++) {
            double t = i / 13.0;
            double y = yt * t, z = zb + dz * t - 0.035;
            mb.box(lx - 0.28, y - 0.025, z - 0.03, lx + 0.28, y + 0.025, z + 0.03, Mat.WOOD, true);
        }
        // Kleine Fässer, liegend auf einem Gestell an der Nordwand: unten drei, oben zwei
        for (int i = 0; i < 3; i++) barrelX(mb, 38.5, 0.55, 11.0 + i * 1.12, 1.25, 0.52, 0.44, Mat.OAK, 3);
        for (int i = 0; i < 2; i++) barrelX(mb, 38.5, 1.52, 11.56 + i * 1.12, 1.25, 0.52, 0.44, Mat.OAK, 3);
        box(mb, 37.7, 0.0, 10.3, 39.3, 0.08, 13.7, Mat.WOOD);
        // Werkbank des Küfers an der Südwand mit Zubern
        box(mb, 37.0, 0.0, 26.3, 41.4, 0.88, 27.5, Mat.WOOD);
        box(mb, 36.9, 0.88, 26.2, 41.5, 0.95, 27.6, Mat.WOOD);
        for (int i = 0; i < 3; i++) mb.cylinder(46.0 + i * 1.3, 27.0, 0.05, 0.75, 0.46, 0.40, 14, Mat.OAK, false);
        // Kerzenständer neben der Eingangsachse und Wandleuchter (immer an: das Tageslicht reicht nicht bis hierher)
        for (double zz : new double[]{cz - 3.4, cz + 3.4}) tripod(mb, 38.0, zz, 3, Candles.FASS);
        Candles.light(38.0, 1.6, cz - 3.4, 2.6f, 9f, Candles.FASS);
        Candles.light(38.0, 1.6, cz + 3.4, 2.6f, 9f, Candles.FASS);
        for (double x : new double[]{38.5, 47.0}) {
            sconce(mb, x, 3.3, 9.5, 0, 1, Candles.FASS);
            sconce(mb, x, 3.3, 28.5, 0, -1, Candles.FASS);
            Candles.light(x, 3.4, 10.2, 2.8f, 10f, Candles.FASS);
            Candles.light(x, 3.4, 27.8, 2.8f, 10f, Candles.FASS);
        }
        Candles.light(cx, cy + 3.8, cz, 2.2f, 14f, Candles.FASS);
        mb.skyFn = keep;
    }

    /** Eiserner Dreifuß mit Kerzen, Höhe 1,35 m, an der Stelle (x, z) auf dem Boden (y = 0,05). */
    static void tripod(MeshBuilder mb, double x, double z, int n, int group) {
        for (int i = 0; i < 3; i++) {
            double a = i * 2 * Math.PI / 3;
            post(mb, x + 0.14 * Math.cos(a), z + 0.14 * Math.sin(a), 0.05, 1.3, 0.018, Mat.IRON);
        }
        mb.cylinder(x, z, 1.30, 1.34, 0.22, 0.22, 10, Mat.IRON, true);
        for (int i = 0; i < n; i++) {
            double a = i * 2 * Math.PI / n;
            Candles.candle(mb, x + 0.12 * Math.cos(a), 1.34, z + 0.12 * Math.sin(a), group);
        }
    }

    /** Wandleuchter: Eisenwinkel an der Wand bei (x, y, z), Wandnormale (nx, nz), eine Kerze auf der Schale. */
    static void sconce(MeshBuilder mb, double x, double y, double z, double nx, double nz, int group) {
        double ox = x + nx * 0.22, oz = z + nz * 0.22;
        box(mb, x - 0.03 + nx * 0.02, y - 0.25, z - 0.03 + nz * 0.02, x + 0.03 + nx * 0.12, y + 0.02, z + 0.03 + nz * 0.12, Mat.IRON);
        mb.cylinder(ox, oz, y - 0.02, y + 0.01, 0.10, 0.10, 8, Mat.IRON, true);
        Candles.candle(mb, ox, y + 0.01, oz, group);
    }

    // ------------------------------------------------------------ Gläserner Saalbau

    /**
     * Einrichtung des Saals (angenommen; belegt ist nur der Name: ein Saal mit venezianischem Spiegelglas): Teppich,
     * Podest mit Thronsessel und Baldachin unter dem Wappen an der Nordwand, zwei Tafeln mit Bänken, Spiegel mit
     * Goldrahmen an den Seitenwänden, drei Kronleuchter. Innenmaße x 0,4…15,6, z −31,1…−22,9; Tür in der Mitte der Südseite.
     */
    static void saal(MeshBuilder mb) {
        MeshBuilder.SkyFn keep = mb.skyFn;
        mb.skyFn = (x, y, z, nx, ny, nz) -> 0.22f;
        final double x0 = 0.4, x1 = 15.6, zN = -31.1, zS = -22.9, f = 0.05;
        // Teppich in der Türachse bis zum Podest
        mb.box(7.0, f, -29.0, 9.0, f + 0.02, zS, Mat.CLOTH, false);
        // Podest und Stufe
        box(mb, 4.4, f, zN, 11.6, f + 0.45, -29.3, Mat.MARBLE);
        box(mb, 5.4, f, -29.3, 10.6, f + 0.22, -28.8, Mat.MARBLE);
        // Thronsessel mit hoher Lehne
        box(mb, 7.35, f + 0.45, -30.7, 8.65, f + 1.0, -29.9, Mat.OAK);
        box(mb, 7.35, f + 1.0, -30.75, 8.65, f + 2.3, -30.45, Mat.OAK);
        box(mb, 7.45, f + 1.0, -30.5, 8.55, f + 1.12, -29.95, Mat.CLOTH);
        box(mb, 7.25, f + 1.0, -30.7, 7.38, f + 1.35, -29.95, Mat.OAK);
        box(mb, 8.62, f + 1.0, -30.7, 8.75, f + 1.35, -29.95, Mat.OAK);
        mb.ellipsoid(7.5, f + 2.4, -30.6, 0.1, 0.1, 0.1, 8, 5, Mat.GOLD);
        mb.ellipsoid(8.5, f + 2.4, -30.6, 0.1, 0.1, 0.1, 8, 5, Mat.GOLD);
        // Baldachin: zwei vergoldete Pfosten, Querbalken mit Stoff, Vorhänge neben dem Wappen
        for (double px : new double[]{5.0, 11.0}) mb.cylinder(px, -29.5, f + 0.45, 5.6, 0.10, 0.09, 8, Mat.GOLD, true);
        box(mb, 4.9, 5.0, zN, 11.1, 5.7, -29.4, Mat.CLOTH);
        box(mb, 4.9, 5.7, zN, 11.1, 5.8, -29.4, Mat.GOLD);
        box(mb, 4.6, f + 0.45, zN, 6.6, 5.0, zN + 0.12, Mat.CLOTH);
        box(mb, 9.4, f + 0.45, zN, 11.4, 5.0, zN + 0.12, Mat.CLOTH);
        // Wappen der Pfalz an der Wand hinter dem Thron
        Heraldry.plaqueAt(mb, 8.0, 3.6, zN, 0, 1, 1.5);
        // Tafeln mit Leinen, Bänke an den Längsseiten
        for (double tx : new double[]{3.8, 12.2}) {
            double za = -28.4, zb = -23.6;
            box(mb, tx - 0.55, 0.88, za, tx + 0.55, 0.95, zb, Mat.OAK);
            box(mb, tx - 0.62, 0.62, za - 0.07, tx + 0.62, 0.96, zb + 0.07, Mat.LINEN);
            for (double zz : new double[]{za + 0.4, zb - 0.4}) box(mb, tx - 0.45, f, zz - 0.05, tx + 0.45, 0.88, zz + 0.05, Mat.OAK);
            for (int sd = -1; sd <= 1; sd += 2) {
                double bx = tx + sd * 1.05;
                box(mb, bx - 0.2, 0.44, za + 0.1, bx + 0.2, 0.50, zb - 0.1, Mat.OAK);
                for (double zz : new double[]{za + 0.5, zb - 0.5}) box(mb, bx - 0.15, f, zz - 0.05, bx + 0.15, 0.44, zz + 0.05, Mat.OAK);
            }
            // Leuchter und Zinn auf der Tafel
            for (double zz : new double[]{-27.2, -25.0}) candelabra(mb, tx, 0.95, zz, 3, Candles.SAAL);
            Candles.light(tx, 1.7, -26.0, 2.6f, 8f, Candles.SAAL);
            for (double zz = -27.8; zz < -23.8; zz += 0.8) {
                mb.cylinder(tx - 0.30, zz, 0.95, 1.00, 0.12, 0.12, 8, Mat.IRON, true);
                mb.cylinder(tx + 0.30, zz + 0.4, 0.95, 1.00, 0.12, 0.12, 8, Mat.IRON, true);
            }
        }
        // Spiegel an den Seitenwänden: venezianisches Glas im Goldrahmen
        for (int sd = 0; sd < 2; sd++) {
            double wx = sd == 0 ? x0 : x1, dir = sd == 0 ? 1 : -1;
            for (double zc : new double[]{-29.3, -27.0, -24.7}) {
                double hw = 0.8, y0 = 1.7, y1 = 4.7;
                double xa = wx, xb = wx + dir * 0.04, xf = wx + dir * 0.09;
                box(mb, xa, y0, zc - hw, xb, y1, zc + hw, Mat.MIRROR);
                box(mb, xa, y0 - 0.1, zc - hw - 0.1, xf, y0, zc + hw + 0.1, Mat.GOLD);
                box(mb, xa, y1, zc - hw - 0.1, xf, y1 + 0.1, zc + hw + 0.1, Mat.GOLD);
                box(mb, xa, y0, zc - hw - 0.1, xf, y1, zc - hw, Mat.GOLD);
                box(mb, xa, y0, zc + hw, xf, y1, zc + hw + 0.1, Mat.GOLD);
            }
        }
        // Kronleuchter
        for (double zc : new double[]{-24.2, -27.0, -29.8}) chandelier(mb, 8.0, 7.6, zc, 12, Candles.SAAL);
        mb.skyFn = keep;
    }

    /** Tischleuchter: Fuß, Schaft, n Kerzen auf Armen. */
    static void candelabra(MeshBuilder mb, double x, double y, double z, int n, int group) {
        mb.cylinder(x, z, y, y + 0.04, 0.09, 0.08, 8, Mat.GOLD, true);
        mb.cylinder(x, z, y + 0.04, y + 0.38, 0.03, 0.02, 8, Mat.GOLD, true);
        for (int i = 0; i < n; i++) {
            double a = i * 2 * Math.PI / n + 0.5;
            double cx = x + 0.13 * Math.cos(a), cz = z + 0.13 * Math.sin(a);
            box(mb, Math.min(x, cx) - 0.01, y + 0.30, Math.min(z, cz) - 0.01, Math.max(x, cx) + 0.01, y + 0.32, Math.max(z, cz) + 0.01, Mat.GOLD);
            Candles.candle(mb, cx, y + 0.32, cz, group);
        }
    }

    /** Kronleuchter: Kette von der Decke (y = 14,3), Eisenring mit n Kerzen, Goldkugel darunter, Licht für die Wände. */
    static void chandelier(MeshBuilder mb, double x, double y, double z, int n, int group) {
        torusH(mb, x, y, z, 0.95, 0.045, 24, 6, Mat.IRON);
        torusH(mb, x, y + 0.5, z, 0.55, 0.035, 18, 5, Mat.IRON);
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2;
            box(mb, x + 0.5 * Math.cos(a) - 0.015, y, z + 0.5 * Math.sin(a) - 0.015, x + 0.5 * Math.cos(a) + 0.015, y + 0.5, z + 0.5 * Math.sin(a) + 0.015, Mat.IRON);
        }
        box(mb, x - 0.02, y + 0.5, z - 0.02, x + 0.02, 14.3, z + 0.02, Mat.IRON);
        mb.ellipsoid(x, y + 0.5, z, 0.14, 0.14, 0.14, 10, 6, Mat.GOLD);
        mb.ellipsoid(x, y - 0.35, z, 0.12, 0.2, 0.12, 8, 6, Mat.GOLD);
        box(mb, x - 0.015, y - 0.2, z - 0.015, x + 0.015, y, z + 0.015, Mat.IRON);
        for (int i = 0; i < n; i++) {
            double a = i * 2 * Math.PI / n;
            Candles.candle(mb, x + 0.95 * Math.cos(a), y + 0.045, z + 0.95 * Math.sin(a), group);
        }
        Candles.light(x, y + 0.4, z, 3.0f, 15f, group);
    }

    // ------------------------------------------------------------ Grotten

    /** Laternen in der Großen und Kleinen Grotte (immer an); x0/z0: Mitte und vordere Innenkante, yFloor: Boden. */
    static void grotten(MeshBuilder mb, double grossX, double kleinX, double zf, double yFloor) {
        MeshBuilder.SkyFn keep = mb.skyFn;
        mb.skyFn = (x, y, z, nx, ny, nz) -> 0.18f;
        double y = yFloor + 2.5;
        for (double zz : new double[]{zf + 2.5, zf + 8.0}) {
            sconce(mb, grossX - 7.6, y, zz, 1, 0, Candles.GROTTE);
            sconce(mb, grossX + 7.6, y, zz, -1, 0, Candles.GROTTE);
            Candles.light(grossX - 7.0, y + 0.3, zz, 2.4f, 9f, Candles.GROTTE);
            Candles.light(grossX + 7.0, y + 0.3, zz, 2.4f, 9f, Candles.GROTTE);
        }
        // zweiter Raum: Kerzen auf dem Steintisch
        double gz1 = zf + 14;
        candelabra(mb, grossX, yFloor + 1.05, gz1 + 4.4, 3, Candles.GROTTE);
        Candles.light(grossX, yFloor + 2.0, gz1 + 4.4, 2.6f, 9f, Candles.GROTTE);
        // Kleine Grotte
        sconce(mb, kleinX - 5.6, y, zf + 4.5, 1, 0, Candles.GROTTE);
        sconce(mb, kleinX + 5.6, y, zf + 4.5, -1, 0, Candles.GROTTE);
        Candles.light(kleinX - 5.0, y + 0.3, zf + 4.5, 2.4f, 8f, Candles.GROTTE);
        Candles.light(kleinX + 5.0, y + 0.3, zf + 4.5, 2.4f, 8f, Candles.GROTTE);
        mb.skyFn = keep;
    }
}
