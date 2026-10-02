package com.dan.heidelberg.castle;

import com.dan.heidelberg.castle.Arch.Block;
import com.dan.heidelberg.core.Mat;
import com.dan.heidelberg.core.MeshBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Das Heidelberger Schloss um 1619: Prunkbauten voll ausgebildet (Ottheinrichsbau, Friedrichsbau,
 * Gläserner Saalbau, Fassbau), die übrigen Bauten mittel (Englischer Bau, Ruprechtsbau, Frauenzimmerbau,
 * Bibliotheksbau, Ludwigsbau, Torturm, Dicker Turm, Krautturm), Wehrtürme und Mauern einfach, dazu das
 * Pflaster des Hofes und die Brunnenhalle. Koordinaten: Ursprung Schlosshof, x Ost, z Süd (Norden ist −z),
 * Höhe 0 = Hofniveau. Die <b>Lage</b> der Bauten zueinander ist nach Plänen aus dem Gedächtnis geschätzt
 * (Genauigkeit einige Meter), die <b>Maße</b> folgen den Quellen des Werkbuchs, wo sie genannt sind.
 */
public final class Castle {
    private Castle() { }

    /** Schornsteine (x, y, z der Oberkante) für den Rauch in Phase 6. */
    public static final List<double[]> CHIMNEYS = new ArrayList<>();
    /** Benannte Orte: Name, x, y, z, Genauigkeit (G gesichert, E geschätzt). */
    public static final List<Object[]> PLACES = new ArrayList<>();

    /** Firstlinien der Dächer von 1619 (x1, y, z1, x2, y, z2, Höhe) und Turmspitzen (x, y, z, r): hier brennt es 1689. */
    public static final List<double[]> RIDGES = new ArrayList<>(), TOPS = new ArrayList<>();

    static void place(String name, double x, double y, double z, String quality) { if (Ruin.active) return; PLACES.add(new Object[]{name, x, y, z, quality}); }

    /** Eckpunkte der Ringmauer (x, z), gegen den Uhrzeigersinn von Nordwest; konvex. */
    static final double[][] RING = {{-58, -44}, {-10, -52}, {62, -50}, {72, -12}, {70, 50}, {24, 58}, {-36, 56}, {-58, 36}};

    /** 1 im Schloss und im Garten, mit weichem Rand; hier wächst kein Gras und stehen keine Bäume. */
    public static float zone(double x, double z) {
        double d = Math.hypot(x - 8, z - 2);
        double a = 1 - smooth(78, 90, d);
        a = Math.max(a, Hortus.zone(x, z));
        return (float) a;
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    public static void build(MeshBuilder mb, List<float[]> dust) {
        double keep = mb.maxEdge;
        mb.maxEdge = 1e9;
        mb.skyFn = null;
        CHIMNEYS.clear();
        PLACES.clear();
        Heraldry.clear();
        Candles.clear();
        RIDGES.clear();
        TOPS.clear();
        mb.era = MeshBuilder.ERA_ALL;
        courtyard(mb);
        mb.era = MeshBuilder.ERA_1619;
        walls(mb);
        ottheinrichsbau(mb);
        friedrichsbau(mb);
        glaeserner(mb, dust);
        fassbau(mb, dust);
        ruprechtsbau(mb);
        frauenzimmerbau(mb);
        englischerBau(mb);
        bibliothek(mb);
        ludwigsbau(mb);
        torturm(mb);
        tuerme(mb);
        brunnenhalle(mb);
        Heraldry.seal();
        mb.era = MeshBuilder.ERA_ALL;
        mb.maxEdge = keep;
    }

    /** Die Ruinenstufe Ruin.stage: dieselben Bauten ohne Dächer, Glas und Einbauten, mit gebrochener Krone (siehe {@link Ruin}). */
    static void buildRuin(MeshBuilder mb) {
        double keep = mb.maxEdge;
        mb.maxEdge = 1e9;
        mb.skyFn = null;
        int n = RING.length;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double x0 = RING[i][0], z0 = RING[i][1], x1 = RING[j][0], z1 = RING[j][1];
            if (i == 7) {
                Ruin.curtain(mb, x0, z0, -58, 9, -4, 10.5, 2.6, i * 2);
                Ruin.curtain(mb, -58, -5, x1, z1, -4, 10.5, 2.6, i * 2 + 1);
            } else Ruin.curtain(mb, x0, z0, x1, z1, -4, 10.5, 2.6, i * 2);
        }
        ottheinrichsbau(mb);
        friedrichsbau(mb);
        glaeserner(mb, null);
        fassbau(mb, null);
        ruprechtsbau(mb);
        frauenzimmerbau(mb);
        englischerBau(mb);
        bibliothek(mb);
        ludwigsbau(mb);
        torturm(mb);
        Ruin.towers(mb);
        Ruin.brunnenhalle(mb);
        Ruin.courtyardRubble(mb);
        mb.maxEdge = keep;
    }

    // ------------------------------------------------------------ Hof und Mauern

    /** Pflaster des Hofes und des Zwingers: Fächer vom Mittelpunkt zu den Mauerecken. */
    static void courtyard(MeshBuilder mb) {
        int c = mb.v(8, 0.04, 2, 0, 1, 0);
        int[] ids = new int[RING.length];
        for (int i = 0; i < RING.length; i++) ids[i] = mb.v(RING[i][0], 0.04, RING[i][1], 0, 1, 0);
        // der Fächer wird fein unterteilt, damit die Pflasterung am Boden sauber liegt: Ringe von 8 m
        for (int i = 0; i < RING.length; i++) {
            int j = (i + 1) % RING.length;
            tessellate(mb, 8, 2, RING[i][0], RING[i][1], RING[j][0], RING[j][1]);
        }
        place("Schlosshof", 0, 0, 0, "G");
    }

    private static void tessellate(MeshBuilder mb, double cx, double cz, double ax, double az, double bx, double bz) {
        int n = 6;
        int[][] g = new int[n + 1][];
        for (int r = 0; r <= n; r++) {
            g[r] = new int[r + 1];
            for (int k = 0; k <= r; k++) {
                double u = r / (double) n, w = r == 0 ? 0 : k / (double) r;
                double x = cx + (ax + (bx - ax) * w - cx) * u, z = cz + (az + (bz - az) * w - cz) * u;
                g[r][k] = mb.v(x, 0.04, z, 0, 1, 0);
            }
        }
        for (int r = 0; r < n; r++) {
            for (int k = 0; k <= r; k++) {
                mb.tri(g[r][k], g[r + 1][k], g[r + 1][k + 1], Mat.SANDSTONE);
                if (k < r) mb.tri(g[r][k], g[r + 1][k + 1], g[r][k + 1], Mat.SANDSTONE);
            }
        }
    }

    static void walls(MeshBuilder mb) {
        int n = RING.length;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double x0 = RING[i][0], z0 = RING[i][1], x1 = RING[j][0], z1 = RING[j][1];
            if (i == 7) {
                // Westmauer von Südwest nach Nordwest, unterbrochen vom Torturm (z −5 bis 9)
                Arch.curtain(mb, x0, z0, -58, 9, -4, 10.5, 2.6, Mat.SANDSTONE, true);
                Arch.curtain(mb, -58, -5, x1, z1, -4, 10.5, 2.6, Mat.SANDSTONE, true);
            } else Arch.curtain(mb, x0, z0, x1, z1, -4, 10.5, 2.6, Mat.SANDSTONE, true);
        }
    }

    static void tuerme(MeshBuilder mb) {
        // Wehrtürme einfach: runde Türme an den Ecken der Ringmauer
        Arch.tower(mb, -58, -44, 4.6, -4, 24, 11, Mat.SANDSTONE, Mat.ROOF, 5);       // Glockenturm
        place("Glockenturm", -58, 24, -44, "E");
        Arch.tower(mb, -10, -52, 3.6, -4, 16, 7, Mat.SANDSTONE, Mat.ROOF, 4);
        Arch.tower(mb, 72, -12, 3.6, -4, 16, 7, Mat.SANDSTONE, Mat.ROOF, 4);
        Arch.tower(mb, 24, 58, 3.6, -4, 16, 7, Mat.SANDSTONE, Mat.ROOF, 4);
        Arch.tower(mb, -36, 56, 3.6, -4, 16, 7, Mat.SANDSTONE, Mat.ROOF, 4);
        Arch.tower(mb, -58, 36, 4.2, -4, 20, 9, Mat.SANDSTONE, Mat.ROOF, 5);          // Apothekerturm
        place("Apothekerturm", -58, 20, 36, "E");
        // Dicker Turm: 40 m hoch, 7 m Mauerstärke (Quelle: Werkbuch); Lage am Nordostende geschätzt
        Arch.tower(mb, 60, -44, 12.5, -6, 34, 9, Mat.SANDSTONE, Mat.ROOF, 12);
        place("Dicker Turm", 60, 34, -44, "E");
        // Krautturm (Gesprengter Turm), 1619 noch unversehrt mit Kegeldach; 1693 gesprengt
        Arch.tower(mb, 68, 48, 8.0, -5, 27, 13, Mat.SANDSTONE, Mat.ROOF, 8);
        place("Krautturm", 68, 27, 48, "E");
    }

    // ------------------------------------------------------------ Fassadenhilfen

    /** Fensterachsen mit Portal: n Achsen, Reihen bei sills (über y0), Portal in der Achse portal der untersten Reihe (−1: keins). */
    static void facade(Block b, int k, int n, double margin, double w, double h, double[] sills, boolean glass, int portal, double pw, double ph, boolean arch) {
        double L = b.len(k), span = L - 2 * margin;
        for (int r = 0; r < sills.length; r++) {
            for (int i = 0; i < n; i++) {
                double uc = margin + span * (i + 0.5) / n;
                if (r == 0 && i == portal) {
                    b.f[k].ops.add(arch ? Arch.arch(uc, b.y0 + 0.05, pw, ph) : Arch.door(uc, b.y0 + 0.05, pw, ph));
                    continue;
                }
                b.f[k].ops.add(Arch.win(uc, b.y0 + sills[r], w, h, glass));
            }
        }
    }

    /** Gesimse, Gurtbänder und Eckquader der Seite k. */
    static void dress(Block b, int k, double[] bands, boolean corners) {
        double L = b.len(k);
        List<double[]> ribs = new ArrayList<>();
        ribs.add(new double[]{-0.3, L + 0.3, b.y1 - 0.55, b.y1, 0.5});
        ribs.add(new double[]{-0.15, L + 0.15, b.y1 - 0.9, b.y1 - 0.55, 0.28});
        for (double y : bands) ribs.add(new double[]{-0.1, L + 0.1, b.y0 + y, b.y0 + y + 0.32, 0.2});
        if (corners) {
            ribs.add(new double[]{-0.15, 0.95, b.y0 - 2, b.y1 - 0.9, 0.12});
            ribs.add(new double[]{L - 0.95, L + 0.15, b.y0 - 2, b.y1 - 0.9, 0.12});
        }
        b.f[k].ribs = ribs.toArray(new double[0][]);
    }

    /** Zwerchgiebel (Renaissance-Giebel) auf der Seite k bei u: Kasten mit Fenster und gestuftem Giebel mit Kugelaufsatz. */
    static void zwerch(MeshBuilder mb, Block b, int k, double uc, double w, double yBase, double hBox, boolean glass) {
        if (Ruin.active) return;
        b.onFace(mb, k, uc - w / 2, () -> {
            double depth = 3.4;
            List<Arch.Op> ops = new ArrayList<>();
            ops.add(Arch.win(w / 2, yBase + 0.9, 1.5, Math.max(1.8, hBox - 3.0), glass));
            Arch.wall(mb, w, yBase, yBase + hBox, 0.9, ops, Mat.SANDSTONE, Mat.STATUE, false);
            // Seitenwangen und Dach-Platte
            mb.box(0, yBase, -depth, 0.01, yBase + hBox, 0, Mat.SANDSTONE, false);
            mb.box(w - 0.01, yBase, -depth, w, yBase + hBox, 0, Mat.SANDSTONE, false);
            mb.rectH(0, -depth, w, 0, yBase + hBox, true, Mat.ROOF);
            // gestufter Giebel: vier Stufen, je schmaler, mit Gesims
            double sw = w, y = yBase + hBox;
            for (int i = 0; i < 4; i++) {
                double inset = (w - sw) / 2;
                double sh = 1.15 - 0.1 * i;
                mb.box(inset - 0.1, y, -0.35, w - inset + 0.1, y + 0.22, 0.12, Mat.STATUE, true);
                mb.box(inset, y + 0.22, -0.3, w - inset, y + sh, 0.0, Mat.SANDSTONE, true);
                y += sh;
                sw -= w * 0.19;
            }
            // Kugel und Spitze
            mb.cylinder(w / 2, -0.15, y, y + 0.7, 0.14, 0.1, 8, Mat.STATUE, true);
            mb.ellipsoid(w / 2, y + 0.95, -0.15, 0.24, 0.24, 0.24, 10, 6, Mat.STATUE);
        });
    }

    static void chimney(MeshBuilder mb, Block b, double u, double v, double yTop) {
        if (Ruin.active) return;
        double[] w = b.world(u, v);
        double sx = 1.1;
        mb.box(w[0] - sx / 2, yTop - 5.5, w[1] - sx / 2, w[0] + sx / 2, yTop, w[1] + sx / 2, Mat.SANDSTONE, false);
        mb.box(w[0] - sx * 0.62, yTop, w[1] - sx * 0.62, w[0] + sx * 0.62, yTop + 0.3, w[1] + sx * 0.62, Mat.STATUE, true);
        CHIMNEYS.add(new double[]{w[0], yTop + 0.3, w[1]});
    }

    /** Standbilder in Nischen auf Reihe y (über y0), an den Grenzen der Achsen und an den Ecken. */
    static void statues(MeshBuilder mb, Block b, int k, int n, double margin, double yRow, double h) {
        double L = b.len(k), span = L - 2 * margin;
        b.onFace(mb, k, 0, () -> {
            List<Double> us = new ArrayList<>();
            us.add(1.55);
            for (int i = 1; i < n; i++) us.add(margin + span * i / n);
            us.add(L - 1.55);
            for (double u : us) {
                // Nische (dunkler Grund) und Sockel
                mb.box(u - 0.55, b.y0 + yRow - 0.1, 0, u + 0.55, b.y0 + yRow + 0.15, 0.4, Mat.STATUE, true);
                Arch.statue(mb, u, b.y0 + yRow + 0.15, 0.2, h, 0, Mat.STATUE);
            }
        });
    }

    // ------------------------------------------------------------ Prunkbauten

    /** Ottheinrichsbau (ab 1556): dreigeschossig, Portal mit Standbildern, drei Zwerchgiebel zum Hof. */
    static void ottheinrichsbau(MeshBuilder mb) {
        Block b = new Block(-17, -29, 0, 30, 14, 0, 17.8);
        double[] sills = {1.8, 7.0, 12.0};
        facade(b, 0, 7, 3.0, 1.8, 3.0, sills, true, 3, 3.0, 4.6, true);
        dress(b, 0, new double[]{5.6, 10.6}, true);
        facade(b, 2, 7, 3.0, 1.6, 2.8, sills, true, -1, 0, 0, false);
        dress(b, 2, new double[]{5.6, 10.6}, true);
        facade(b, 1, 3, 2.5, 1.5, 2.8, new double[]{1.8, 7.0, 12.0}, true, -1, 0, 0, false);
        facade(b, 3, 3, 2.5, 1.5, 2.8, new double[]{1.8, 7.0, 12.0}, true, -1, 0, 0, false);
        b.build(mb);
        b.roof(mb, 9.5, 0.7, false, Mat.ROOF, Mat.SANDSTONE);
        // Portal in der Mitte der Hofseite: Säulen, Gebälk, Standbilder in Nischen
        double uc = 3.0 + 24.0 * 3.5 / 7;
        b.onFace(mb, 0, uc - 2.4, () -> {
            for (int i = 0; i < 2; i++) {
                double x = 0.1 + i * 4.6;
                Arch.column(mb, x, 0.45, 0.05, 6.2, 0.26, Mat.STATUE);
            }
            mb.box(-0.3, 6.2, 0, 5.1, 6.9, 0.8, Mat.STATUE, true);
            mb.box(0.1, 6.9, 0, 4.7, 8.5, 0.4, Mat.SANDSTONE, true);
            Arch.statue(mb, 1.2, 7.0, 0.3, 2.0, 0, Mat.STATUE);
            Arch.statue(mb, 2.4, 7.0, 0.3, 2.3, 0, Mat.STATUE);
            Arch.statue(mb, 3.6, 7.0, 0.3, 2.0, 0, Mat.STATUE);
        });
        for (double u : new double[]{5.0, 15.0, 25.0}) zwerch(mb, b, 0, u, 5.2, 17.0, 3.8, true);
        chimney(mb, b, -8, -3, 28.5);
        chimney(mb, b, 6, -3, 28.5);
        if (!Ruin.active) Heraldry.plaque(mb, b, 0, 15.0, 10.2, 1.0);
        place("Ottheinrichsbau", -17, 9, -29, "E");
    }

    /** Friedrichsbau (1601–1607): vier Geschosse, 16 Standbilder der Pfälzer Ahnen an der Hofseite. */
    static void friedrichsbau(MeshBuilder mb) {
        Block b = new Block(31, -31, 0, 30, 16, 0, 22.5);
        double[] sills = {1.8, 7.3, 12.8, 18.0};
        facade(b, 0, 7, 3.0, 1.7, 3.0, sills, true, 3, 2.8, 4.4, false);
        dress(b, 0, new double[]{5.7, 11.2, 16.7}, true);
        facade(b, 2, 7, 3.0, 1.7, 3.0, sills, true, -1, 0, 0, false);
        dress(b, 2, new double[]{5.7, 11.2, 16.7}, true);
        facade(b, 1, 3, 3.0, 1.6, 2.9, sills, true, -1, 0, 0, false);
        facade(b, 3, 3, 3.0, 1.6, 2.9, sills, true, -1, 0, 0, false);
        dress(b, 1, new double[]{5.7, 11.2, 16.7}, true);
        dress(b, 3, new double[]{5.7, 11.2, 16.7}, true);
        b.build(mb);
        b.roof(mb, 12.0, 0.9, true, Mat.ROOF, Mat.SANDSTONE);
        // 16 Standbilder in zwei Reihen (zweites und drittes Geschoss)
        statues(mb, b, 0, 7, 3.0, 7.0, 2.6);
        statues(mb, b, 0, 7, 3.0, 12.5, 2.6);
        for (double u : new double[]{8.5, 21.5}) zwerch(mb, b, 0, u, 5.4, 22.0, 4.0, true);
        for (double u : new double[]{8.5, 21.5}) zwerch(mb, b, 2, u, 5.4, 22.0, 4.0, true);
        chimney(mb, b, -6, 0, 32.5);
        chimney(mb, b, 6, 0, 32.5);
        if (!Ruin.active) Heraldry.plaque(mb, b, 0, 15.0, 6.0, 1.05);
        place("Friedrichsbau", 31, 11, -31, "E");
    }

    /** Gläserner Saalbau: begehbarer Saal mit hohen Glasfenstern zum Hof, Marmorboden. */
    static void glaeserner(MeshBuilder mb, List<float[]> dust) {
        Block b = new Block(8, -27, 0, 18, 11, 0, 14.5);
        b.hollow = true;
        b.T = 1.4;
        facade(b, 0, 5, 1.8, 2.1, 7.2, new double[]{1.8}, true, 2, 2.8, 4.4, true);
        facade(b, 0, 5, 1.8, 2.1, 2.2, new double[]{10.8}, true, -1, 0, 0, false);
        facade(b, 2, 5, 1.8, 1.8, 3.0, new double[]{6.0}, true, -1, 0, 0, false);
        dress(b, 0, new double[]{9.9}, true);
        dress(b, 2, new double[]{9.9}, true);
        b.slabs.add(new double[]{0.05, Mat.MARBLE});
        b.build(mb);
        b.roof(mb, 7.0, 0.8, false, Mat.ROOF, Mat.SANDSTONE);
        if (Ruin.active) return;
        // Innen: Säulen aus Marmor, die das Gebälk tragen
        mb.skyFn = (x, y, z, nx, ny, nz) -> 0.22f;
        // vier Säulen je Reihe: die Mittelachse zur Tür bleibt frei (so kommt man hinein, und die Kamera auch)
        for (int i = 0; i < 2; i++) for (int j = 0; j < 4; j++) {
            double[] w = b.world(-6.3 + j * 4.2, -2.5 + i * 5.0);
            Arch.column(mb, w[0], w[1], 0.05, 9.5, 0.4, Mat.MARBLE);
        }
        mb.skyFn = null;
        double[] c0 = b.world(-9 + b.T, -5.5 + b.T), c1 = b.world(9 - b.T, 5.5 - b.T);
        dust.add(new float[]{(float) Math.min(c0[0], c1[0]), 0.05f, (float) Math.min(c0[1], c1[1]), (float) Math.max(c0[0], c1[0]), 14.0f, (float) Math.max(c0[1], c1[1]), 0.028f});
        Interior.saal(mb);
        chimney(mb, b, 4, 0, 24.5);
        place("Gläserner Saalbau", 8, 7, -27, "E");
    }

    /** Fassbau (1589–1592): Halle für das Große Fass; Tür und Glasfenster zum Hof. */
    static void fassbau(MeshBuilder mb, List<float[]> dust) {
        Block b = new Block(43, 19, -Math.PI / 2, 22, 18, 0, 11.5);
        b.hollow = true;
        b.T = 1.5;
        facade(b, 0, 4, 2.2, 1.7, 4.4, new double[]{3.6}, true, 1, 3.4, 5.0, true);
        facade(b, 2, 4, 2.2, 1.7, 4.4, new double[]{3.6}, true, -1, 0, 0, false);
        facade(b, 1, 3, 2.5, 1.5, 3.8, new double[]{3.8}, true, -1, 0, 0, false);
        facade(b, 3, 3, 2.5, 1.5, 3.8, new double[]{3.8}, true, -1, 0, 0, false);
        dress(b, 0, new double[0], true);
        b.slabs.add(new double[]{0.05, Mat.SANDSTONE});
        b.build(mb);
        b.roof(mb, 7.5, 0.8, false, Mat.ROOF, Mat.SANDSTONE);
        if (Ruin.active) return;
        double[] c0 = b.world(-11 + b.T, -9 + b.T), c1 = b.world(11 - b.T, 9 - b.T);
        dust.add(new float[]{(float) Math.min(c0[0], c1[0]), 0.05f, (float) Math.min(c0[1], c1[1]), (float) Math.max(c0[0], c1[0]), 11.0f, (float) Math.max(c0[1], c1[1]), 0.010f});
        Interior.fass(mb);
        place("Fassbau", 43, 6, 19, "E");
    }

    // ------------------------------------------------------------ übrige Bauten (mittel)

    static void ruprechtsbau(MeshBuilder mb) {
        Block b = new Block(41, -9, -Math.PI / 2, 24, 14, 0, 15.0);
        facade(b, 0, 4, 2.6, 1.5, 2.6, new double[]{1.8, 6.6, 10.8}, true, 1, 2.4, 3.6, true);
        dress(b, 0, new double[]{5.2, 9.7}, true);
        facade(b, 2, 4, 2.6, 1.4, 2.4, new double[]{1.8, 6.6, 10.8}, true, -1, 0, 0, false);
        b.build(mb);
        b.roof(mb, 8.5, 0.7, false, Mat.ROOF, Mat.SANDSTONE);
        chimney(mb, b, 0, 0, 25.0);
        place("Ruprechtsbau", 41, 8, -9, "E");
    }

    static void frauenzimmerbau(MeshBuilder mb) {
        // Hofstube 34,65 m × 16,70 m (Quelle: Werkbuch)
        Block b = new Block(13.3, 30.35, Math.PI, 34.65, 16.7, 0, 13.5);
        facade(b, 0, 8, 2.4, 1.5, 2.6, new double[]{2.0, 6.6, 10.4}, true, 4, 2.6, 3.8, true);
        dress(b, 0, new double[]{5.3, 9.2}, true);
        facade(b, 2, 8, 2.4, 1.4, 2.4, new double[]{2.0, 6.6, 10.4}, true, -1, 0, 0, false);
        b.build(mb);
        b.roof(mb, 7.5, 0.7, false, Mat.ROOF, Mat.SANDSTONE);
        chimney(mb, b, -8, 0, 23.0);
        chimney(mb, b, 8, 0, 23.0);
        place("Frauenzimmerbau", 13, 7, 30, "E");
    }

    static void englischerBau(MeshBuilder mb) {
        Block b = new Block(-23, 31, Math.PI, 34, 14, 0, 14.0);
        facade(b, 0, 7, 2.4, 1.6, 3.0, new double[]{1.9, 7.2}, true, 3, 2.6, 3.8, false);
        dress(b, 0, new double[]{5.9}, true);
        facade(b, 2, 7, 2.4, 1.6, 3.0, new double[]{1.9, 7.2}, true, -1, 0, 0, false);
        b.build(mb);
        b.roof(mb, 5.5, 0.8, true, Mat.ROOF, Mat.SANDSTONE);
        place("Englischer Bau", -23, 7, 31, "E");
    }

    static void bibliothek(MeshBuilder mb) {
        Block b = new Block(-39, -13, Math.PI / 2, 18, 12, 0, 14.5);
        facade(b, 0, 4, 2.2, 1.5, 2.8, new double[]{1.7, 6.8, 10.8}, true, -1, 0, 0, false);
        dress(b, 0, new double[]{5.4, 9.9}, true);
        facade(b, 2, 4, 2.2, 1.4, 2.6, new double[]{1.7, 6.8, 10.8}, true, -1, 0, 0, false);
        b.build(mb);
        b.roof(mb, 7.0, 0.7, false, Mat.ROOF, Mat.SANDSTONE);
        place("Bibliotheksbau", -39, 7, -13, "E");
    }

    static void ludwigsbau(MeshBuilder mb) {
        Block b = new Block(-39, 14, Math.PI / 2, 16, 12, 0, 12.5);
        facade(b, 0, 4, 2.2, 1.5, 2.7, new double[]{1.8, 6.6}, true, 1, 2.2, 3.4, true);
        dress(b, 0, new double[]{5.2}, true);
        facade(b, 2, 4, 2.2, 1.4, 2.5, new double[]{1.8, 6.6}, true, -1, 0, 0, false);
        b.build(mb);
        b.roof(mb, 6.5, 0.7, false, Mat.ROOF, Mat.SANDSTONE);
        place("Ludwigsbau", -39, 6, 14, "E");
    }

    /** Torturm (1531–1541), 52 m mit Dach: Torhalle im Erdgeschoss, darüber der Turm mit Pyramidendach. */
    static void torturm(MeshBuilder mb) {
        Block lo = new Block(-52, 2, Math.PI / 2, 10, 10, 0, 8.2);
        lo.hollow = true;
        lo.T = 2.2;
        lo.f[0].ops.add(Arch.arch(5, 0.05, 4.6, 5.8));
        lo.f[2].ops.add(Arch.arch(5, 0.05, 4.6, 5.8));
        lo.slabs.add(new double[]{0.05, Mat.SANDSTONE});
        lo.build(mb);
        Block up = new Block(-52, 2, Math.PI / 2, 10, 10, 8.2, 38.0);
        up.foot = 0;
        facade(up, 0, 1, 3.0, 1.4, 2.2, new double[]{6, 14, 22}, false, -1, 0, 0, false);
        facade(up, 2, 1, 3.0, 1.4, 2.2, new double[]{6, 14, 22}, false, -1, 0, 0, false);
        facade(up, 1, 1, 3.0, 1.2, 2.0, new double[]{10, 20}, false, -1, 0, 0, false);
        facade(up, 3, 1, 3.0, 1.2, 2.0, new double[]{10, 20}, false, -1, 0, 0, false);
        dress(up, 0, new double[]{8, 16, 24}, true);
        dress(up, 2, new double[]{8, 16, 24}, true);
        dress(up, 1, new double[]{8, 16, 24}, true);
        dress(up, 3, new double[]{8, 16, 24}, true);
        up.build(mb);
        // Dach: Pyramide (die Barockhaube ist von 1716, die Dachform von 1619 ist nachzuschlagen)
        up.roof(mb, 14.0, 0.9, true, Mat.ROOF, Mat.SANDSTONE);
        if (Ruin.active) return;
        Heraldry.plaque(mb, up, 2, 5.0, 19.4, 0.9);
        place("Torturm", -52, 52, 2, "E");
        torbruecke(mb);
    }

    /**
     * Brücke über den Graben vor dem Torturm: Fahrbahn auf vier Pfeilern, Brüstungen zu beiden Seiten. Die Form ist
     * angenommen (Merian zeigt eine Zugangsbrücke, keine Maße); sie trägt die Kamera beim Rundgang durchs Tor.
     */
    static void torbruecke(MeshBuilder mb) {
        int keepEra = mb.era;
        mb.era = MeshBuilder.ERA_ALL;
        torbrueckeBau(mb);
        mb.era = keepEra;
    }

    private static void torbrueckeBau(MeshBuilder mb) {
        double x0 = -100, x1 = -57.2, z0 = -0.6, z1 = 4.6;
        mb.box(x0, -1.4, z0, x1, 0.0, z1, Mat.SANDSTONE, true);
        mb.box(x0, 0.0, z0, x1, 1.0, z0 + 0.5, Mat.SANDSTONE, true);
        mb.box(x0, 0.0, z1 - 0.5, x1, 1.0, z1, Mat.SANDSTONE, true);
        for (double px : new double[]{-93, -83, -73, -64}) mb.box(px - 1.0, -16, z0 + 0.4, px + 1.0, -1.4, z1 - 0.4, Mat.SANDSTONE, true);
        place("Torbrücke", -78, 0, 2, "M");
    }

    /** Brunnenhalle mit dem Ziehbrunnen (16 m tief, seit 1508), vier Säulen als Monolithen und ein Dach. */
    static void brunnenhalle(MeshBuilder mb) {
        double cx = -21, cz = 5;
        for (int i = 0; i < 4; i++) {
            double x = cx + (i % 2 == 0 ? -2.6 : 2.6), z = cz + (i < 2 ? -2.6 : 2.6);
            Arch.column(mb, x, z, 0.04, 5.4, 0.42, Mat.MARBLE);
        }
        mb.box(cx - 3.6, 5.4, cz - 3.6, cx + 3.6, 5.9, cz + 3.6, Mat.SANDSTONE, true);
        int v0 = mb.vertexCount();
        Arch.gableRoof(mb, 7.4, 7.4, 5.9, 2.8, 0.5, true, Mat.ROOF, Mat.SANDSTONE);
        mb.transform(v0, 0, cx, 0, cz);
        // Brunnenkranz
        Arch.roundBasin(mb, cx, cz, 0.0, 1.0, 0.45, 1.0, 0.35, 20, Mat.STATUE, Mat.BASIN);
        place("Brunnenhalle", cx, 3, cz, "E");
    }
}
